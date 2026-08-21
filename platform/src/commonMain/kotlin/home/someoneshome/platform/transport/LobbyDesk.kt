package home.someoneshome.platform.transport

import home.someoneshome.model.InsiderBand
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody

/**
 * **The host's lobby table: who is here, whose line has arrived, and what the host has set.**
 *
 * Sits beside [SeatLedger] and for the same reason — this is transport-side, pre-arm bookkeeping,
 * not the game. No `Event` is minted here, no `Effect` leaves here, nothing in this class survives
 * arming, and the rules never learn it existed. What it owns is the small amount of truth the
 * lobby screen is a picture of.
 *
 * ### The lines are held in memory and written nowhere
 *
 * A player types one line they would rather not explain and hands it over. The screen where they
 * type it promises two things in the host's own words — **seen by the house only**, and **deleted
 * when the round ends** — and this class is where both promises are kept:
 *
 * - There is no store here. Not a store that is currently unused, not a path that is currently
 *   commented out: this class has no way to reach a filesystem, and a `LobbyDesk` that could
 *   persist a line would have to be given something to persist it with, in a diff that says so.
 * - [roundEnded] drops every line, and [standing] — the only thing that goes back down the wire —
 *   is physically incapable of quoting one.
 *
 * [lineOf] is the house's own reader, and the house runs on the host's phone. It exists so the
 * one line can do the only job it has, which is to be quoted back to the player the house picks.
 * **Nothing a host sends to a client may call it**, and nothing can accidentally: the downward
 * body has no text field to put the answer in.
 *
 * ### The Insider setting is clamped here, not where it is drawn
 *
 * D-103's band is a balance envelope and it clamps the **setting**, not only the draw — so the
 * count is pulled inside the band every time it is set *and* every time the seat count moves
 * under it. A host who picks 3 in a twelve-seat home and then watches four people leave does not
 * keep a 3 that the band no longer allows.
 *
 * Not thread-safe: confined to the transport's own dispatcher, exactly as [SeatLedger] is.
 */
class LobbyDesk {

    /** Seats currently in the lobby, in the order they arrived. */
    private val seats = LinkedHashSet<Seat>()

    /**
     * The lines, by seat. **In memory, for the lifetime of one round, and nowhere else.**
     *
     * Keyed by seat rather than by name because the desk has no names: a client never names
     * itself, so attribution here is the seat the ledger granted and nothing a player typed.
     */
    private val lines = HashMap<Seat, String>()

    /**
     * The host's Insider-count setting. **Null is UNKNOWN, and that is the default** (D-103): the
     * house draws the count at arming, locks it, and tells no one until the round ends.
     */
    var insiders: Int? = null
        private set

    /** A connection was seated. Re-clamps the setting, because the band moved with the seat count. */
    fun seated(seat: Seat) {
        seats += seat
        reclamp()
    }

    /**
     * A seat left the lobby.
     *
     * **The line goes with them.** Someone who walks out before the lights go off has not handed
     * the house anything, and a desk that kept the text of a player who is no longer in the round
     * would be holding it past the only moment it was ever for.
     */
    fun left(seat: Seat) {
        seats -= seat
        lines -= seat
        reclamp()
    }

    /**
     * A line arrived from a seat. Handing over twice replaces it — a player who re-typed theirs
     * meant the second one.
     *
     * Refused from a seat that is not in the lobby, loudly. Pre-arm, in a lit room, on the host's
     * own phone: rule 6 protects a player mid-round, not a lobby that cannot start, and a line
     * silently filed under nobody is a player who will be told at arming that they never handed
     * one over.
     */
    fun handedOver(seat: Seat, line: String) {
        require(seat in seats) { "a line arrived from $seat, which is not in this lobby" }
        lines[seat] = line
    }

    /**
     * The house's own reader. See the class KDoc for who may call this and who may not.
     *
     * Null for a seat that has not handed one over — which is a fact about the lobby, not about
     * the player, and is already on everybody's screen as a count.
     */
    fun lineOf(seat: Seat): String? = lines[seat]

    /**
     * The host's setting, pulled inside D-103's band. Null sets UNKNOWN, and stays UNKNOWN.
     *
     * The band is consulted here rather than trusted to the caller because "clamped where it is
     * drawn" is a clamp that holds until the second place it is drawn.
     */
    fun setInsiders(chosen: Int?) {
        insiders = InsiderBand.clamp(seats.size, chosen)
    }

    /** The band the setting is currently allowed to move in, for a control that has to draw it. */
    fun band(): IntRange = InsiderBand.of(seats.size)

    /**
     * **What every phone in the lobby is shown: counts and a setting.**
     *
     * Counted from the seats currently here, so the two numbers cannot contradict each other —
     * a line whose player has left is gone with them, and `linesIn` can never exceed `joined`.
     * The gate below depends on that being true rather than usually true.
     */
    fun standing(): LobbyBody.Standing = LobbyBody.Standing(
        joined = seats.size,
        linesIn = lines.size,
        insiders = insiders,
    )

    /**
     * Whether LIGHTS OUT is allowed to be offered: everybody here, everybody's line in.
     *
     * An empty lobby is not ready. Nobody has handed anything over, and the condition
     * "0 of 0" is true in arithmetic and false in every other sense.
     */
    fun everyLineIn(): Boolean = seats.isNotEmpty() && lines.size == seats.size

    /**
     * **Deleted when the round ends** — the second promise made on the screen where the line was
     * typed, kept in the one place it can be.
     *
     * The seats stay: who was in the round is not a secret, and the round ending is not everybody
     * standing up. Only the text goes.
     */
    fun roundEnded() {
        lines.clear()
    }

    private fun reclamp() {
        insiders = InsiderBand.clamp(seats.size, insiders)
    }
}
