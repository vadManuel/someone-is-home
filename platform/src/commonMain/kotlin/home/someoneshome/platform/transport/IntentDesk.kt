package home.someoneshome.platform.transport

import home.someoneshome.model.Intent
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.Asked
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **The intent desk: what a phone asked for, becoming what *that* phone asked for.**
 *
 * One direction only — client to authority — and [EffectWire]'s mirror. Where the wire's job on the
 * way down is to decide *who may see this*, the desk's job on the way up is to decide **who said
 * it**, and it decides that from the connection alone.
 *
 * ### The seat comes off the socket, and there is no argument that could carry another one
 *
 * [arrived] takes the seat from its caller, and its caller is `TransportHost`'s read loop, which
 * holds one seat per connection because the ledger admitted a token to it. The frame contributes
 * nothing: [IntentMessage] has no field an actor could ride in, so *the same bytes arriving on two
 * connections build two different intents*, and that is asserted rather than described.
 *
 * **This is the property the whole channel exists for.** A client that could name its own seat
 * could name somebody else's, and a checked-in seat that never walked to the meeting area — or a
 * vote cast in another player's name — is the one cheat in this game that is remote, undetectable
 * and requires no physical act. Every other advantage available to a dishonest player costs them
 * something a room full of people can see.
 *
 * ### A request delivered twice must not be *acted on* twice
 *
 * D-097 makes intents fire-and-forget — *clients hold no request/response posture* — so there is no
 * ack here and nothing waits for one. The sequence on [RoundBody.Act] is a dedup key: the desk
 * keeps the highest it has seen from each seat and drops anything at or below it. `ArmRevoke`
 * applied twice is two cooldowns spent on one press, and `CheckIn` applied twice would be a phone
 * counted twice against a gate that closes on a number (D-104).
 *
 * **A request *above* the mark is put through even when it skips numbers.** Refusing it would be
 * rule 1's forbidden shape wearing transport clothing: the player pressed a button, the house did
 * nothing, and the silence is the answer. So the skip is reported to the authority and never to the
 * player (rule 6), and the phone's own screen is corrected the ordinary way — [RoundInbox] notices
 * the missing push and asks, and the house re-asserts.
 *
 * ### The dedup window is the connection, and it must be
 *
 * [seated] clears a seat's mark, because a phone whose process failed and resumed its seat begins
 * counting from 1 again — 0.8's whole resume story — and a desk that kept the old mark would
 * silently swallow every request that phone ever made afterwards. **That is the failure this class
 * would otherwise have**: not a leak, but a player pressing controls that reach a house which has
 * already decided it has heard them. `IntentOutbox` resets on the same event at the other end, and
 * an intent from before a re-seating is never resent — see there.
 */
class IntentDesk(
    /**
     * **A seat whose request numbering skipped, and it is loud on purpose.**
     *
     * Rule 6 both ways round: the player is told nothing and the authority is told at once. A
     * client whose frames are being lost on the way up is a player whose controls are doing
     * nothing, which — like a failed radio on the way down — is invisible to everybody including
     * them.
     */
    private val onSkipped: (Seat, Long) -> Unit = { _, _ -> },
) {

    /** What the caller should do with a request. */
    sealed interface Landing {

        /**
         * New. Put it to the house.
         *
         * [intent] is attributed already — it was built by [IntentMessage.attributedTo] from the
         * seat the connection holds, and this is the only construction of one from arriving bytes
         * anywhere in the app.
         */
        data class Put(val intent: Intent) : Landing

        /**
         * Already heard. Drop it — the rules have seen this request once, which is exactly once.
         *
         * **It is silent, and that silence is not rule 1's forbidden shape.** The first copy
         * produced whatever it produced and it went down the seat's own stream; a phone still
         * waiting for that answer is a phone with a hole in its push sequence, which [RoundInbox]
         * notices on its own and asks about. Answering the duplicate would answer it twice.
         */
        data object Duplicate : Landing

        /**
         * **New, and the house has to make a draw before there is anything to put.**
         *
         * [RoundBody.Fire]'s answer, and it is a different [Landing] rather than a [Put] carrying a
         * different intent because the two are different *acts on the authority's part*: a [Put] is
         * translated and admitted, and this one is composed — the house draws the type and the two
         * nodes against a home's shape the rules have never held, and only then is there a fact to
         * admit. Carrying it as a [Put] would need an [Intent] whose `factOf` arm invents a draw,
         * which is the line `factOf` holds and the reason [RoundBody.Fire] is its own frame.
         *
         * [actor] is the connection's seat, arrived at exactly as [Put]'s is.
         */
        data class Draw(val actor: Seat) : Landing
    }

    private val gate = Mutex()

    /** The highest request number heard from each seat on its current connection. */
    private val heard = HashMap<Int, Long>()

    /**
     * **One request off one connection.**
     *
     * [seat] is the connection's and never the frame's. See this class's header for why that
     * sentence is the whole point of the file.
     */
    suspend fun arrived(seat: Seat, act: RoundBody.Act): Landing = gate.withLock {
        val fresh = mark(seat, act.seq) ?: return@withLock Landing.Duplicate
        check(fresh == act.seq)
        Landing.Put(act.intent.attributedTo(seat))
    }

    /**
     * **The Egress tile, off one connection** ([RoundBody.Fire]).
     *
     * [arrived] above with one difference and it is the whole of the difference: there is no frame
     * content to attribute, so what comes back names the seat and nothing else. **The dedup mark is
     * the same mark** — one counter per connection across both members, because what is being
     * deduplicated is this phone's requests rather than this phone's requests of one kind, and an
     * Egress delivered twice spends the house's longest cooldown on one finger.
     */
    suspend fun arrived(seat: Seat, fire: RoundBody.Fire): Landing = gate.withLock {
        mark(seat, fire.seq) ?: return@withLock Landing.Duplicate
        Landing.Draw(seat)
    }

    /**
     * The ids each seat's slotted requests have carried, newest last, at most [SLOTTED_KEPT] a seat
     * (D-263). Kept across connections: a request is sent again on every beat until the house
     * answers it, a new connection included, so the mark has to outlive the socket.
     */
    private val slotted = HashMap<Int, ArrayDeque<String>>()

    /**
     * **The requests out of one upload's slot, off one connection** (D-263, D-275).
     *
     * [arrived]'s two doors in one, with a different dedup key: the slot's [id], which the phone
     * draws at random and keeps, with exactly the same requests under it, for every beat it sends
     * them on until the house answers. **The first time an id is heard from a seat every request
     * under it is put, in the order the slot holds them** ([Landing.Put] for a control,
     * [Landing.Draw] for the Egress tile); every later time the id is one [Landing.Duplicate] and
     * nothing under it is put again. The seat is the connection's, as in [arrived]; [asked] has
     * nowhere to name one.
     *
     * The id mark is not cleared by [seated]. A slot that went up a failing socket is sent again on the
     * next one, under the same id, and has to be recognised there.
     */
    suspend fun slotted(seat: Seat, id: String, asked: List<Asked>): List<Landing> = gate.withLock {
        val seen = slotted.getOrPut(seat.index) { ArrayDeque() }
        if (id in seen) return@withLock listOf(Landing.Duplicate)
        seen.addLast(id)
        while (seen.size > SLOTTED_KEPT) seen.removeFirst()
        asked.map {
            when (it) {
                is Asked.Act -> Landing.Put(it.intent.attributedTo(seat))
                is Asked.Fire -> Landing.Draw(seat)
            }
        }
    }

    /**
     * Move this seat's mark to [seq], or null when [seq] has already been heard.
     *
     * The one place the dedup arithmetic lives, so the two [arrived] doors cannot come to disagree
     * about which requests a connection has made. Called under [gate] by both.
     */
    private fun mark(seat: Seat, seq: Long): Long? {
        val last = heard[seat.index] ?: 0
        if (seq <= last) return null
        if (seq > last + 1) onSkipped(seat, seq - last - 1)
        heard[seat.index] = seq
        return seq
    }

    /**
     * **This seat has a new connection: its numbering starts again.**
     *
     * Called from the same place `LobbyDesk.seated` is, because it is the same fact. See the
     * header — a mark kept across a re-seating turns every later request from that phone into a
     * duplicate, forever and silently.
     */
    suspend fun seated(seat: Seat) {
        gate.withLock { heard.remove(seat.index) }
    }

    private companion object {
        /**
         * Slotted ids remembered per seat. A phone resends the one slot it has out and no other, so
         * the only id it can still send again is that one; this is far past that.
         */
        const val SLOTTED_KEPT = 64
    }

    // There is deliberately no `roundEnded` here, and the absence is the safe direction.
    // EffectWire.roundEnded wipes the retained pushes because they are a round's worth of one
    // house's messages and outlive nothing. A mark is not that: it is a single number that says
    // nothing about anybody, and clearing it at the end of a round would make a stale frame from
    // THAT round -- a duplicate -- arrive at a desk that had forgotten it and be put to the rules
    // of the next one. The numbering belongs to the connection, so a NEW ROUND on the same socket
    // carries on counting, which is what keeps the boundary shut (D-157: the party stays in the
    // lobby between rounds, so the sockets do too).
}

/**
 * **The client's half: this phone's own requests, numbered.**
 *
 * Pure and synchronous, like [RoundInbox] beside it and for the same reason — what a control does
 * to a frame is not I/O and must be testable without any. It holds one number.
 *
 * ### An intent does not survive its connection
 *
 * [reseated] drops the numbering back to nothing, in step with [IntentDesk.seated] at the other
 * end. The consequence is deliberate: **a request made just before a socket failed is never resent.**
 * The player pressed a control and it did nothing, and the phone will say so the only way this
 * design permits — the house re-asserts that seat's authorized view on reconnect (D-097), and the
 * screen shows the round as the house actually holds it rather than as the tap hoped.
 *
 * The alternative is worse in a way that is hard to see and impossible to debug. A resend that
 * crossed a reconnect would arrive at a desk whose mark had just been cleared, so it would be
 * indistinguishable from a fresh request — and `ArmRevoke` re-sent after a dropped socket would
 * spend a second cooldown on a press the player made once.
 */
class IntentOutbox {

    /** How many requests this connection has made. Zero means none, and numbering starts at 1. */
    var sent: Long = 0
        private set

    /** One control, framed. The seat is not here and cannot be — see [IntentMessage]. */
    fun ask(intent: IntentMessage): RoundBody.Act = RoundBody.Act(seq = ++sent, intent = intent)

    /**
     * **The Egress tile, framed** ([RoundBody.Fire]).
     *
     * Numbered off the same counter [ask] uses, in step with [IntentDesk.mark] at the other end: one
     * connection makes one sequence of requests, and a second counter would be a second mark a
     * resend could slip between. There is nothing to pass — the tile is the entire message.
     */
    fun fire(): RoundBody.Fire = RoundBody.Fire(seq = ++sent)

    /** A new connection: this phone has asked this house for nothing yet. */
    fun reseated() {
        sent = 0
    }

    // There is no `roundEnded` here either, and for IntentDesk's reason: the numbering is the
    // connection's rather than the round's, and a NEW ROUND on a socket that never dropped carries
    // on counting. Resetting it here would put this phone back at 1 against a desk still holding
    // its old mark, and every control on it would go quiet.
}
