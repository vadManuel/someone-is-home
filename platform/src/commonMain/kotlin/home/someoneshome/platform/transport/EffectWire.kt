package home.someoneshome.platform.transport

import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **The effect wire: what the rules emitted, on its way to the phones that may have it.**
 *
 * One direction only — authority to client. Every [Effect] the rules produce comes in here, and
 * what leaves is one [RoundBody.Push] per *permitted* seat, in that seat's own order, down that
 * seat's own socket.
 *
 * ### The allowlist is the only authority, and it is consulted before anything is rendered
 *
 * [emit] has exactly one source of recipients — [EmitSchema.deliveries] — and no way to be handed a
 * ready-made frame. A kind the schema denies produces no [home.someoneshome.model.Delivery], so no
 * sequence number is spent, nothing is retained, and nothing is written: the pipeline is not
 * *checked* against the allowlist, it is **built out of it**. `EffectWireTest`'s first injection is
 * this loop rewritten to walk `state.seats`, which is the shape somebody reaches for when a message
 * "should obviously go to everyone".
 *
 * ### The host's own phone is not a special case
 *
 * There is no local delivery path and there must not be one. The host joins its own house over
 * loopback like every other player (Unit 6), so seat zero's client is a websocket client and its
 * effects travel the same encoder, the same sequence counter and the same socket as everybody
 * else's. **The route no other phone uses is the route nobody tests**, and on this pipeline it
 * would be the route the authority's own screen depends on.
 *
 * ### Order, and what happens when a phone is not there
 *
 * Sequencing and handing to [send] happen under one lock, which is D4's *one ordered queue* made
 * literal: per-seat order is not an emergent property of how fast sockets drain, it is the only
 * order this class can produce. **[send] queues and returns** (`TransportHost.send`, R12, D-244):
 * each seat's connection writes its own queue in order, so this lock is never held across a socket
 * write, and one phone that stopped draining delays no other phone's frames. A seat whose socket is gone still **spends its sequence number and keeps its
 * message** — [onUnreachable] reports the failure to the authority (rule 6: a failed radio must never
 * make a player silently invisible) and the phone collects the message when it asks (see
 * [reassert]). A write that failed is therefore a delay, never a hole.
 */
class EffectWire(
    /**
     * Host → one seat. In production this is `TransportHost::send`; it throws when no connection
     * holds the seat, and that throw is the signal [onUnreachable] reports.
     */
    private val send: suspend (Seat, TransportFrame) -> Unit,
    /**
     * **A seat the house could not reach, and it is loud on purpose.**
     *
     * Rule 6 both ways round: the player is told nothing, and the authority is told immediately.
     * *A failed radio makes a living player invisible on the map, which nobody including them would
     * detect* — so the one thing that may never happen here is a swallowed write.
     */
    private val onUnreachable: (Seat) -> Unit = {},
) {

    /** One seat's stream: where its numbering is, and what it has been sent. */
    private class Stream {
        var seq: Long = 0
        val held = mutableListOf<RoundBody.Push>()
    }

    private val gate = Mutex()
    private val streams = HashMap<Int, Stream>()

    /**
     * **Resolve one effect through the emit boundary and push it to every seat it is permitted to.**
     *
     * The state is the one *after* the event, exactly as the per-client transcript recorder takes
     * it: the round-state axis moves because of the event being processed, and classifying against
     * the state before it would hand a revoked player one last frame of living-client permissions.
     */
    suspend fun emit(effect: Effect, state: GameState) {
        val message = RoundWire.project(effect)
        gate.withLock {
            for (delivery in EmitSchema.deliveries(effect, state)) {
                val stream = streams.getOrPut(delivery.seat.index) { Stream() }
                val push = RoundBody.Push(seq = ++stream.seq, message = message)
                stream.held += push
                write(delivery.seat, push)
            }
        }
    }

    /**
     * **D-097's re-assertion: the house says again what it already said to this seat.**
     *
     * *A refused event is answered with re-assertion, not explanation* — and *one mechanism, two
     * triggers*: a phone that noticed a gap and a phone that has just reconnected are the same
     * request. [held] is the last sequence the client is sure of; everything after it goes out
     * again, in order, and a client that holds nothing gets its whole permitted round.
     *
     * ### It re-states, it does not recompute
     *
     * The pushes are replayed **as they were sent**, not rebuilt against today's state. That is the
     * safe direction and it is the reason this needs no ruling of its own: D-097 left the payload of
     * *the authorized view* deliberately undecided, and a replay invents no payload — every byte
     * here passed [EmitSchema] at the moment it was produced, addressed to this seat, and was
     * already delivered to this phone once. Re-sending it discloses nothing that was not disclosed.
     *
     * A rebuild would be the opposite: it would ask what this seat may see *now*, which is a
     * different question with a different answer once a seat's class has moved, and answering it
     * would be writing the undecided snapshot under another name.
     *
     * ### Safe to receive redundantly
     *
     * The other half of D-097's requirement, and it is kept on the client: [RoundInbox] drops any
     * sequence it already holds, so a re-assertion that overlaps what a phone has is bytes it
     * discards rather than state it applies twice.
     */
    suspend fun reassert(seat: Seat, held: Long) {
        gate.withLock {
            val stream = streams[seat.index] ?: return@withLock
            for (push in stream.held) {
                if (push.seq > held) write(seat, push)
            }
        }
    }

    /** How far this seat's stream has got. For the authority's own reporting, never for permitting. */
    suspend fun sequenceOf(seat: Seat): Long = gate.withLock { streams[seat.index]?.seq ?: 0 }

    /**
     * **The round is over: the retained streams go.**
     *
     * They are a round's worth of one house's messages and they outlive nothing. Called from the
     * same place `LobbyDesk.roundEnded` is called from, so that what the house is holding about an
     * evening ends when the evening does.
     */
    suspend fun roundEnded() {
        gate.withLock { streams.clear() }
    }

    /**
     * One write. Failure is reported and never rethrown: a phone that has gone is one seat's
     * problem, and letting it out of here would take the rest of the house's delivery down with it.
     */
    private suspend fun write(seat: Seat, push: RoundBody.Push) {
        val frame = TransportFrame.Carry(RoundWire.encode(push))
        try {
            send(seat, frame)
        } catch (cancelled: CancellationException) {
            // The house is shutting down, not the phone refusing. Reporting it as an unreachable
            // seat would put a line in the log for every player every time a round is torn down.
            throw cancelled
        } catch (_: Exception) {
            onUnreachable(seat)
        }
    }
}

/**
 * **The client's half: what this phone holds, and how it notices it is missing something.**
 *
 * Pure and synchronous, like [ClientSession] beside it and for the same reason — the decision about
 * what to do with a frame is not I/O and must be testable without any. It holds one number.
 *
 * ### An out-of-order push is dropped rather than applied
 *
 * Applying push 9 while 7 and 8 are missing leaves whichever fields 7 and 8 carried standing at
 * their old values, under a screen drawn from a newer one — a phone that is *partly* up to date and
 * cannot say which part. Dropping it and asking is the recoverable failure: the house replays 7, 8
 * and 9 in order and the phone converges. **Nothing is guessed and nothing is interpolated.**
 *
 * ### A duplicate is dropped, which is what makes a re-assertion safe
 *
 * D-097 requires the re-assertion to be *safe to re-receive redundantly*. This is where that is
 * kept: any sequence at or below what is already held is discarded before it reaches a screen.
 */
class RoundInbox {

    /** The highest sequence this phone holds, in order, from 1. Zero means it holds nothing. */
    var held: Long = 0
        private set

    /** What the caller should do with a push. */
    sealed interface Landing {

        /** In order and new. Hand it to the one consumer. */
        data class Apply(val message: RoundMessage) : Landing

        /** Already held. Discard it — this is the re-assertion arriving over what the phone has. */
        data object Duplicate : Landing

        /**
         * Something is missing. Ask the house for everything after [held], and **draw nothing** —
         * the screen keeps saying what it last legitimately said, which is G3's rule for the lamp
         * applied to every other field: a phone that has lost the thread does not invent one.
         */
        data class Gap(val held: Long) : Landing
    }

    fun arrived(push: RoundBody.Push): Landing = when {
        push.seq == held + 1 -> {
            held = push.seq
            Landing.Apply(push.message)
        }
        push.seq <= held -> Landing.Duplicate
        else -> Landing.Gap(held)
    }

    /**
     * The socket came back. The phone still holds what it held, so this is the same request a gap
     * makes — D-097's one mechanism, arriving by its other trigger.
     */
    fun askAgain(): RoundBody.Gap = RoundBody.Gap(held)

    /** A new round: this phone holds nothing of it. */
    fun roundEnded() {
        held = 0
    }
}
