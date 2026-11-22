package home.someoneshome.model.protocol

import home.someoneshome.model.Balance
import home.someoneshome.model.ClientFacing
import kotlinx.serialization.json.Json

/**
 * **What the lobby says, in the opaque body of a [TransportFrame.Carry].**
 *
 * The lobby happens *before* the round exists: nothing here is an Event, nothing here is an
 * Effect, and nothing here passes the emit boundary, because there is no `GameState` yet for
 * anything to be redacted out of. It is pre-arm bookkeeping riding the channel story 0.8 already
 * built, which is why it needs no [TRANSPORT_PROTOCOL] bump — the frame layer carries it and
 * still cannot see what it is carrying.
 *
 * ### The direction is the type, and that is the whole guard
 *
 * There are three bodies and each one travels one way only:
 *
 * - [LobbyBody.Handover] goes **client → host**. It carries the one line a player typed. That
 *   text is the player's own, it is theirs to hand over, and the house is the only thing that
 *   ever reads it.
 * - [LobbyBody.Naming] goes **client → host**. It carries what to call this phone's owner.
 * - [LobbyBody.Standing] goes **host → client**. It carries the counts, the host's setting, and
 *   the names.
 * - [LobbyBody.RoundEnded], [LobbyBody.HouseClosed] and [LobbyBody.NameTaken] go **host → client**
 *   and carry nothing: each is a moment, and the whole message is that it arrived.
 *
 * A single body with a nullable `line` would have been the obvious shape and it is exactly the
 * shape rule 3 forbids: a nulled field still exists, and someone makes it non-null later for an
 * unrelated reason. Two upward bodies rather than one widened one keeps that property where it
 * pays: **[LobbyBody.Standing] carries names because D-115 says it does, and it still has nowhere
 * to put a one line** — not a field that is currently blank, no field at all.
 *
 * ### The names arrived by a ruling, in a diff that says so (D-115)
 *
 * This type shipped as three integers, which was the overnight charter's caution while E6-1 was
 * open. D-115 closed it: **the design's lobby shows names**, the host learns them, and clients
 * receive them where the interaction needs them. Names are **round-scoped** — they arrive for the
 * round and they are gone when it ends — and they are held in memory by the host's desk exactly
 * as the one lines are.
 *
 * What the widening does *not* open is occupancy. Knowing who is in the game is not knowing who is
 * in a room, and nothing here touches the latter. Nor does it open the lines: a name says who is
 * standing in the hall, which six people standing in the hall can already see.
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed class LobbyBody {

    /**
     * **Client → host. The one line the house can hold over you.**
     *
     * Seen by the house only, deleted when the round ends — both promised on the screen where it
     * is typed, and both kept above this layer: the host holds it in memory and writes it
     * nowhere, and nothing sends it back down. This body is the single authorised exit from the
     * phone that typed it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("handover")
    data class Handover(val line: String) : LobbyBody() {
        companion object {
            /**
             * **The one place the line's length lives** (D-249), in UTF-16 units as typed.
             *
             * One line, and the field is one line high. It was the lobby screen's alone; it lives
             * here because two more readers need it: the house bounds what it keeps to it (a client
             * is free to send whatever it likes), and `RoundMessage.RoleRevealed` pads every reveal
             * to the longest line this allows, so that an Insider's reveal and a Resident's are
             * one length on the Wi-Fi.
             */
            const val LIMIT: Int = 120
        }
    }

    /**
     * **Client → host. What to call the person holding this phone.**
     *
     * Sent on every seating rather than once: a phone that dropped and resumed comes back to the
     * seat its token owns, and a house that had forgotten the name would list an empty chip for
     * somebody standing in the room. Re-announcing is the cheaper half of that pair.
     *
     * Separate from [Handover] because the two arrive at different moments — a name on the way in,
     * a line whenever its author has thought of one — and a body that carried both would have to
     * be sent half-empty, which is the nullable-field shape rule 3 forbids.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("naming")
    data class Naming(val name: String) : LobbyBody() {
        companion object {
            /**
             * **The one place the name's length lives.**
             *
             * The field is one line on a 300-unit panel and the design's own names are six
             * characters. Past this a name stops being readable across a dark hall, which is the
             * only thing a name in this game is for. Bounded on the *host's* side as well as the
             * typist's: a client is free to send whatever it likes, and a lobby is not the place
             * to find out that one of them did.
             */
            const val LIMIT: Int = 14
        }
    }

    /**
     * **Host → client. How the lobby stands: the counts, the setting, and who is here.**
     *
     * [joined] is how many seats are held, [linesIn] how many of them have handed their line
     * over, and [insiders] the host's setting — **null is UNKNOWN**, which is D-103's default:
     * the house draws the count at arming, locks it, and tells no one until the round ends. A
     * chosen number is public, because the host chose it in a lit room in front of everybody.
     *
     * [names] is one entry per seat in the order the seats were taken, **empty for a phone that
     * has not said what to call its owner**. One entry per seat rather than a list of the names it
     * happens to have, so that `names.size` is `joined` and the list cannot quietly under-report
     * a lobby back to a screen that is counting it.
     *
     * ### [names] and [linesIn] must never be drawn against each other
     *
     * The order here is arrival order and [linesIn] is a count. Pairing the two positionally —
     * the nth name against the nth filled mark — asserts *who* has handed a line over, which is a
     * thing the house never said and this body cannot say. The count is a count; the names are a
     * set of people. See `LobbyScreen`, where that separation is drawn.
     *
     * ### [round] is which round of the evening the house is on (V1, D-267)
     *
     * A count from 1, one more at every NEW ROUND, and the same on every phone. It is a number and
     * not a flag, which is the distinction [RoundEnded]'s note draws: read twice, it says the same
     * thing twice. It is here because [RoundEnded] is sent once and never again: a phone that was off
     * the air at that moment came back to a house on the next round still holding the last one, and
     * the standing is the one body every seating is sent. A phone that reads a higher number than it
     * holds missed the round's end, and ends its own there, before it says anything else. 0 is
     * unmarked, a body from no house.
     *
     * ### While a round runs, the standing is the round's, and [waitingForNextRound] is per phone (D-273)
     *
     * A seat's token returns that seat at any time (D-225), so a phone relaunched after LIGHTS OUT is
     * seated in a house playing a round it is not in. The desk counted it, and its lobby read 5 JOINED
     * beside a round of four. So from arming to NEW ROUND [joined], [linesIn] and [names] are the
     * round's seats, as armed, and the phone the house is speaking to is told whether it is one of
     * them. True only on a phone seated outside the running round; it joins at the next NEW ROUND.
     * It answers one question about the phone it is sent to and names no seat (D-191's shape). Who is
     * in a round is not a secret: the room watched the lights go out.
     *
     * **[roundOver]: the round it waits on has reached its ending** (D-274). The ending is not NEW
     * ROUND, so the phone still waits — but a phone told only that it waits said ROUND IN PROGRESS
     * over a room standing in the lights at the ending. Set on the waiting phone's body from the
     * round's ending to NEW ROUND, and nowhere else; the room watched the lights come on.
     *
     * ### The host's four timed rows ride here too, so a guest draws the host's numbers (D-320)
     *
     * [voteWindowSeconds], [discussionSeconds], [egressTimerSeconds] and [revokeCooldownSeconds] are
     * the host's VOTING, DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN, as the desk holds them. They
     * were the host's phone's alone (D-117, D-317), so a guest's row drew its own phone's number; with
     * the ranges the owner ruled on 2026-11-21 the two would disagree. **Every phone draws these**,
     * the host's included, as every phone draws [insiders]. Each is public: the host sets it in a lit
     * room in front of everybody, and the round's pushes carry the same numbers once it runs. Each
     * defaults to its design number, for a body from no house.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("standing")
    data class Standing(
        val joined: Int,
        val linesIn: Int,
        val names: List<String> = emptyList(),
        val insiders: Int? = null,
        val round: Int = 0,
        val waitingForNextRound: Boolean = false,
        val roundOver: Boolean = false,
        val voteWindowSeconds: Int = Balance.VOTE_WINDOW_SECONDS,
        val discussionSeconds: Int = Balance.DISCUSSION_SECONDS,
        val egressTimerSeconds: Int = Balance.EGRESS_TIMER_SECONDS,
        val revokeCooldownSeconds: Int = Balance.REVOKE_COOLDOWN_SECONDS,
    ) : LobbyBody()

    /**
     * **Host → client. The round is over; come back to the lobby** (D-157, D-116, E-R2-10 /
     * E-P3-5).
     *
     * NEW ROUND was a control the host's phone drew and a call into the desk sitting beside it, and
     * on a client the desk is not there — so the host walked back to the lobby and **four phones
     * stayed on the ending until somebody handed them back.** *What is missing is a push, not a
     * button*, and this is the push.
     *
     * ### It carries nothing, and there is nothing it could carry
     *
     * The whole message is that it arrived. Everything a second round is built from survives it —
     * D-157 keeps the home, the seats and the settings, so an evening does not end after one round
     * with five people re-typing a lobby code in the dark — and everything that does not survive it
     * is dropped on **both** sides independently rather than sent: the desk drops the lines and the
     * names, and each phone drops the line it typed. A body carrying either would be the one text
     * in this game that is promised never to come back down travelling in the direction it is
     * promised never to travel.
     *
     * ### It is the standing's sibling and not a variant of it
     *
     * [Standing] is sent constantly and says how the lobby *is*; this is sent once and says the
     * evening turned over. A flag on the standing would be a moment expressed as a field, arriving
     * on every publish, and the first client to read it twice would wipe a round that had already
     * begun. Two members for two facts, which is this file's rule from the top.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("roundEnded")
    data object RoundEnded : LobbyBody()

    /**
     * **Host → client. The host closed the house; there is no lobby to stand in any more.**
     *
     * [RoundEnded]'s sibling and emphatically not the same fact. That one says *the evening turned
     * over* and everything a second round is built from survives it — the home, the seats, the
     * settings, the connection. This one says the house is going down: the server stops, the
     * advertisement comes off the air, and a phone that kept sitting on the lobby screen would be
     * drawing a count from a desk that no longer exists. **The only honest destination is the join
     * screen.**
     *
     * ### It carries nothing, for [RoundEnded]'s reason and one of its own
     *
     * The whole message is that it arrived. There is also nothing it *could* carry that a client
     * should act on: a reason would be the host's words on five other people's screens, and the
     * host is standing in the room with them.
     *
     * ### Downward only, and that direction is the guard
     *
     * A client able to send this would be one phone closing everybody else's house. The host's own
     * end ignores it on arrival for exactly the reason it ignores [RoundEnded] there.
     *
     * ⚠️ **The line the clients are shown is PROPOSED, not ratified** — see
     * `LobbyModel.HOUSE_CLOSED`. This body carries no words, so a change of wording is a change to
     * one constant in `ui` and never a protocol change.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("houseClosed")
    data object HouseClosed : LobbyBody()

    /**
     * **Host → one client. The name this phone sent is already another seat's here** (D-288, the
     * owner's ruling 9d: refused, case-insensitively, and the person types another).
     *
     * Sent to the one phone whose [Naming] the desk refused, and to no other. **It carries nothing**:
     * the phone knows the name it sent, and the name it collided with is on every lobby screen
     * already, so a field here could only repeat something or name somebody. It is a moment and not
     * a state, so it is a body of its own rather than a flag on [Standing], for [RoundEnded]'s reason.
     *
     * Never sent to a seat in a running round: that seat's name is the round's, fixed at arming, and
     * a phone in the dark is not walked back to JOIN over a lobby's bookkeeping.
     *
     * Downward only. The house ignores one arriving from a phone, as it ignores [RoundEnded].
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("nameTaken")
    data object NameTaken : LobbyBody()
}

/**
 * The one encoder/decoder for [LobbyBody], for the reason [TransportWire] is the one for frames:
 * what the JSON looks like has exactly one home, and its round-trip test lives beside it.
 */
object LobbyWire {

    private val json = Json

    fun encode(body: LobbyBody): String = json.encodeToString(LobbyBody.serializer(), body)

    /**
     * Null for anything that is not a lobby body — including every other thing a `Carry` may
     * hold. A body that does not decode must become a fact the shell can report, never an
     * exception on an I/O thread taking a player's connection down with it.
     */
    fun decodeOrNull(text: String): LobbyBody? = if (foreign.isForeign(text)) {
        // A round body, by its leading type: what the decode below would have thrown over. Every
        // push and every upload's answer reaches here on every phone (D-276).
        null
    } else try {
        json.decodeFromString(LobbyBody.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private val foreign = ForeignBody(LobbyBody.serializer().descriptor)
}
