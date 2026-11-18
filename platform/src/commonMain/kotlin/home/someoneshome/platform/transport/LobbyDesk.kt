package home.someoneshome.platform.transport

import home.someoneshome.model.Balance
import home.someoneshome.model.InsiderBand
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.LobbyBody

/**
 * **The host's lobby table: who is here, what they are called, whose line has arrived, and what
 * the host has set.**
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
 * body has no field to put the answer in.
 *
 * ### The names are held the same way, and they are not the lines (D-115)
 *
 * A name goes back down and a line never does, so the two live in two maps and [standing] reads
 * exactly one of them. That is the whole structural difference between them, and it is deliberate
 * that it is a difference of *which map*, not of a flag on one: "the standing quoted a line" then
 * takes writing `lines` where `names` is written, in a diff a reader can see.
 *
 * Names are round-scoped like the lines — [roundEnded] drops both — and a leaver takes their name
 * with them exactly as they take their line.
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
     * Keyed by seat rather than by the name its author gave: a client names its owner and never
     * its seat, so attribution here is the seat the ledger granted and nothing a player typed.
     */
    private val lines = HashMap<Seat, String>()

    /**
     * What each seat asked to be called. **In memory, for the lifetime of one round.**
     *
     * Keyed by seat for [lines]' reason: a client never names its *seat*, only its owner, and the
     * attribution is the one the ledger granted.
     */
    private val names = HashMap<Seat, String>()

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
     * **The line goes with them, and so does the name.** Someone who walks out before the lights
     * go off has not handed the house anything, and a desk that kept the text of a player who is
     * no longer in the round would be holding it past the only moment it was ever for. A name left
     * behind is worse than untidy: it is a person on six lobby screens who is not in the house.
     */
    fun left(seat: Seat) {
        seats -= seat
        lines -= seat
        names -= seat
        reclamp()
    }

    /**
     * **A phone said what to call its owner.** Sent on every seating, so this replaces rather than
     * accumulates — a resume is the same person coming back, not a second one.
     *
     * Trimmed and bounded here as well as where it is typed: a client is free to send whatever it
     * likes, and a lobby screen is not the place to find out that one of them sent a paragraph.
     *
     * Refused from a seat that is not in the lobby, loudly, for [handedOver]'s reason.
     *
     * ### A name another seat here already has is refused, and the answer is false (D-288)
     *
     * The owner's ruling 9d: *the lobby refuses a name already taken, case-insensitively, and the
     * player types another.* Compared as it would be kept (trimmed, bounded) against every **other**
     * seat's name, ignoring case, so a seat re-sending its own name on a resume is never refused by
     * itself. **A blank is never taken** (D-277): it is a seat with no name, it keeps its row as
     * UNNAMED, and two of them are two seats (D-278). A refused name is not kept, and whatever this
     * seat was called before goes with it: the phone is told and walks back to type another, so the
     * row is a blank until it says something else. The house tells the phone; this only answers.
     */
    fun named(seat: Seat, name: String): Boolean {
        require(seat in seats) { "a name arrived from $seat, which is not in this lobby" }
        val kept = name.trim().take(LobbyBody.Naming.LIMIT)
        val taken = kept.isNotEmpty() &&
            names.any { (other, held) -> other != seat && held.equals(kept, ignoreCase = true) }
        if (taken) {
            names -= seat
            return false
        }
        names[seat] = kept
        return true
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
        // Bounded here as well as where it is typed, as a name is (D-249).
        lines[seat] = line.take(LobbyBody.Handover.LIMIT)
    }

    /**
     * The house's own reader. See the class KDoc for who may call this and who may not.
     *
     * Null for a seat that has not handed one over — which is a fact about the lobby, not about
     * the player, and is already on everybody's screen as a count.
     */
    fun lineOf(seat: Seat): String? = lines[seat]

    /**
     * **Every seat's name, in seat order, for the one thing the round needs them for** (D-115).
     *
     * The reveal says *who* the house owned, and *seat 3* is not a person — so the names ride the
     * arming event into the round exactly as the lines do, and this is where they are taken from.
     * One entry per seat in the lobby, **empty string for a phone that never said**, so the list
     * cannot quietly under-report a lobby to whatever is counting it. [standing] builds its own
     * list the same way and for the same reason.
     *
     * ### It is not [lineOf] with a wider return type, and the difference is the point
     *
     * A line goes to the house and comes back to nobody; a name goes down the wire to every phone
     * in the lobby already. So this reads [names] and there is nothing in it that could read
     * [lines] — the two maps stay two maps, and *the arming quoted a line* would take writing
     * `lines` where `names` is written, in a diff a reader can see.
     *
     * Sorted by seat, because the rules hold ordered collections and a hash order here would be an
     * arming event that replayed differently.
     */
    fun namedSeats(): List<SeatNamed> =
        seats.map { SeatNamed(it, names[it].orEmpty()) }.sortedBy { it.seat.index }

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
     * **The host's ballot length, in seconds** (D-117). The design's forty-five until they move it.
     *
     * Held here beside [insiders] because this desk is what a round is armed from, and a setting
     * the arming cannot read is a control that moves a number nothing follows — which is exactly
     * what this one was.
     *
     * **It does not go down the wire and does not need to.** A client draws the ballot's clock from
     * `Effect.MeetingPhaseOpened`, which carries the window the house locked at arming; the lobby
     * row is the host's own screen, on the host's own phone, and `LobbyModel` already refuses the
     * control to anybody else. So there is no [LobbyBody] case for it and adding one would widen
     * the wire for a number that is about to travel by a better road.
     *
     * Unclamped, unlike [insiders]: D-125's sorting rule clamps what players cannot perceive, and
     * a ballot's length is a number the whole room watches run out.
     */
    var voteWindowSeconds: Int = Balance.VOTE_WINDOW_SECONDS
        private set

    /** The host moved the VOTING row. Refused from anywhere but the host's own phone, one layer up. */
    fun setVoteWindow(seconds: Int) {
        voteWindowSeconds = seconds
    }

    /**
     * **What every phone in the lobby is shown: counts, a setting, and the names.**
     *
     * Counted from the seats currently here, so the numbers cannot contradict each other — a line
     * whose player has left is gone with them, `linesIn` can never exceed `joined`, and `names`
     * is one entry per seat because it is *built* from the seats rather than collected. The gate
     * below depends on the first of those being true rather than usually true.
     *
     * **It reads [names] and never [lines].** That is the single line of this class that decides
     * whether a one line goes back down the wire, and it is why the two are separate maps: the
     * bug has to be written, not left in.
     */
    fun standing(): LobbyBody.Standing = LobbyBody.Standing(
        joined = seats.size,
        linesIn = lines.size,
        names = seats.map { names[it].orEmpty() },
        insiders = insiders,
    )

    /**
     * Whether every seat here has handed a line over.
     *
     * An empty lobby is not ready. Nobody has handed anything over, and the condition
     * "0 of 0" is true in arithmetic and false in every other sense.
     */
    fun everyLineIn(): Boolean = seats.isNotEmpty() && lines.size == seats.size

    /**
     * **Whether the house may arm: [MINIMUM_SEATS][Balance.MINIMUM_SEATS] seated, every line in.**
     *
     * The second gate in the game that clamps rather than guides, and it is D-125's sorting rule
     * that says which it should be. A host can see how many people are standing in their hall, so
     * D-127's capacity guidance never blocks — but *four plain Residents and one Insider is the
     * smallest party in which a meeting means anything* (D-128) is a balance consequence nobody in
     * the hall can check, and an unwinnable evening is not something to find out at the end of one.
     *
     * The screen draws the same two conditions off the standing. This is the one the house acts
     * on: a client that decided for itself that the lobby was ready would be a phone arming a
     * round.
     */
    fun readyToArm(): Boolean = seats.size >= Balance.MINIMUM_SEATS && everyLineIn()

    /**
     * **Deleted when the round ends** — the second promise made on the screen where the line was
     * typed, kept in the one place it can be.
     *
     * The seats stay: who was in the round is not a secret, and the round ending is not everybody
     * standing up. Only the text goes — and the names are text, held for one round like everything
     * else on this desk (D-115), so they go with the lines rather than outliving them.
     */
    fun roundEnded() {
        lines.clear()
        names.clear()
    }

    private fun reclamp() {
        insiders = InsiderBand.clamp(seats.size, insiders)
    }
}
