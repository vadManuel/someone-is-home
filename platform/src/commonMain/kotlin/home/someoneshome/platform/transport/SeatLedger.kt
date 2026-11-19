package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportRefusal

/**
 * The host's table of who holds which seat — story 0.8's server half, pure and headless.
 *
 * Attribution is by connection: a connection presents nothing and is *granted* a token at join,
 * or presents a stored token and is returned to **that** seat. There is no operation on this
 * class that accepts a seat number from outside, which is what makes "a client never names
 * itself" structural rather than reviewed.
 *
 * ### The property this type exists for
 *
 * **After [lock], a seat can never change hands.** [join] refuses, [release] throws, and the only
 * way back in is the token that already owns the seat. A disconnect does not free anything — the
 * seat waits for its token. Re-deriving identity from the lobby code on resume — the path of
 * least resistance at 1 am — is not a bug this class can have, because no such operation exists.
 *
 * ### What it deliberately is not
 *
 * Not game state, not a `GameState` participant, and no [home.someoneshome.model.Event] is minted
 * here. Which connection holds which seat is a fact about the transport; the rules never learn
 * tokens exist. Refusals follow D-080's shape — a distinct kind, never a quieter success.
 *
 * Not thread-safe: D4 confines it to the transport's own dispatcher.
 */
class SeatLedger(
    seats: List<Seat>,
    /**
     * Mints an unguessable token. Injected because *this* class must be testable in milliseconds,
     * and because the determinism rules ban bare randomness anywhere it could leak into a replay —
     * the app root supplies real randomness; tokens never enter recordings.
     */
    private val mint: () -> SeatToken,
) {

    /** D-080's answer shape: seated or refused, never a seated-with-nothing. */
    sealed interface Admission {
        data class Seated(val seat: Seat, val token: SeatToken) : Admission
        data class Refused(val reason: TransportRefusal) : Admission
    }

    private val free = ArrayDeque(seats)
    private val held = LinkedHashMap<SeatToken, Seat>()

    /** True from [lock] on. Set once, never cleared — arming has no undo. */
    var locked: Boolean = false
        private set

    /**
     * **The seats of the round under way, from LIGHTS OUT to its ending, or null between rounds**
     * (D-287, the owner's ruling 9c: *a round in progress cannot be joined*).
     *
     * [lock]'s reversible sibling, for an evening of many rounds (D-157): [lock] is forever, and the
     * house plays a second round in the same lobby. While this is set, a stranger's [join] is refused
     * [TransportRefusal.RoundLocked], and so is a [resume] whose seat is not one of these: a phone
     * that was not seated at LIGHTS OUT is not in the round, whatever token it holds. A seat that is
     * one of these comes back to it at any time (D-225), which is a rejoin and not a join.
     */
    private var underWay: List<Seat>? = null

    /** LIGHTS OUT: from here until [roundOver], only [seats] are admitted, and only by their tokens. */
    fun roundUnderWay(seats: List<Seat>) {
        underWay = seats.sortedBy { it.index }
    }

    /** The round reached its ending, or NEW ROUND: the door is open again, as it is in a lobby. */
    fun roundOver() {
        underWay = null
    }

    /** A fresh connection gets the next free seat and a newly minted token — or a refusal. */
    fun join(): Admission {
        if (locked || underWay != null) return Admission.Refused(TransportRefusal.RoundLocked)
        val seat = free.removeFirstOrNull() ?: return Admission.Refused(TransportRefusal.NoFreeSeat)
        val token = mint()
        // A colliding mint is a broken mint, and this is host-side, pre-arm, in the light —
        // rule 6 protects players in a round, not a lobby that cannot start. Fail loud.
        require(token !in held) { "the token mint produced a token that is already held" }
        held[token] = seat
        return Admission.Seated(seat, token)
    }

    /**
     * A returning connection gets exactly the seat its token owns — or a refusal.
     *
     * **While a round is under way, only a seat in it** (D-287). A token this ledger never issued is
     * still [TransportRefusal.UnknownToken] first, so the phone starts over as a stranger and is then
     * told the round is locked; a token for a seat outside the round is told so at once.
     */
    fun resume(token: SeatToken): Admission {
        val seat = held[token] ?: return Admission.Refused(TransportRefusal.UnknownToken)
        val round = underWay
        return if (round != null && round.none { it.index == seat.index }) {
            Admission.Refused(TransportRefusal.RoundLocked)
        } else {
            Admission.Seated(seat, token)
        }
    }

    /**
     * **The seat [token] holds, or null** (D-289): how the house learns which seat its own phone
     * took, from the token its own loopback client was handed. A read; it seats nobody.
     */
    fun seatOf(token: SeatToken): Seat? = held[token]

    /**
     * A player leaves the lobby and the seat returns to the pool. **Pre-arm only.** After [lock]
     * this throws rather than refusing quietly: nothing legitimate calls it then, and a release
     * that "just returns false" is a Boolean somebody drops (D-087) on the exact path that
     * rebuilds the attribution hole.
     */
    fun release(token: SeatToken) {
        require(!locked) { "no seat is released after arming — the seat waits for its token" }
        val seat = requireNotNull(held.remove(token)) { "released a token the ledger never issued" }
        free.addLast(seat)
    }

    /** Arming. From here on the ledger only ever answers to tokens it already issued. */
    fun lock() {
        locked = true
    }

    /** The seats currently held, in issue order. The lobby renders from this. */
    fun heldSeats(): List<Seat> = held.values.toList()
}
