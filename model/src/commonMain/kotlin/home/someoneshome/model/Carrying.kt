package home.someoneshome.model

/**
 * **One cross-player dependency: an entry of one seat's order holds an entry of another's**
 * (D-146, `gdd.md:262`, D-322, D-324).
 *
 * Authority state, never on any client type. The downstream seat sees a blocked line (D-114) and
 * nothing about who holds it. **The upstream seat is told nothing** (the owner's 2026-11-22 ruling,
 * D-324: *no separate notice; the LOCKED · WAITING UPSTREAM row says it*), and never whether or when
 * the downstream unlocked (`gdd.md:282`).
 *
 * ### Real links join two plain Residents; a fake one is an Insider's locked line (D-324)
 *
 * *Insiders don't have real tasks. They cannot progress any task … an insider cannot be included in
 * the chain* (the owner, 2026-11-22). So when [fake] is false, [up] and [down] are both Residents,
 * always: an Insider is never upstream of anybody's real line and never downstream of one.
 *
 * When [fake] is true the link is **an Insider's fake hold**: [down] is the Insider and [downEntry]
 * the locked line their order shows, so it has the same LOCKED · WAITING UPSTREAM a Resident's has.
 * It is timed off a Resident's real completion ([up], [upEntry] — the Resident before the Insider in
 * the draw's cycle) plus its own drawn [delay], which is exactly *as if an upstream Resident had
 * finished*, because one did. It holds nothing of the Resident's and changes nothing for them; it is
 * the fake list faking a chain (M8), and the release reaches the Insider's phone as a Resident's
 * does. It lives in the house, beside the real links, because the clock, the gate and the release
 * are then the same code for both.
 *
 * ### The delay is drawn at arming and starts when the upstream entry is completed
 *
 * [delay] is in ticks, drawn once from the round's seed inside [Balance.UNLOCK_DELAY_LEAST_SECONDS]
 * to [Balance.UNLOCK_DELAY_MOST_SECONDS], by the same draw for a real link and a fake one. [dueAt]
 * is null until the upstream entry is completed, and then it is the completion's tick plus [delay].
 * The house's clock mints [Event.ChainUnlocked] once it has passed.
 *
 * **Nothing else ever sets [dueAt]** (D-146): a Revoke that takes the upstream seat out before the
 * entry is done strands the downstream line for the rest of the round, real or fake alike, and no
 * repair runs.
 *
 * Not a `data class`, for [OrderEntry]'s reason: the two transitions below are the only ones.
 */
class ChainLink(
    val up: Seat,
    val upEntry: Int,
    val down: Seat,
    val downEntry: Int,
    val delay: Long,
    val dueAt: Tick?,
    /** An Insider's fake hold rather than a chain between two Residents (D-324). */
    val fake: Boolean,
) {
    /** The upstream entry was completed at [at]: the downstream unlocks [delay] ticks later. */
    fun startedAt(at: Tick): ChainLink = ChainLink(up, upEntry, down, downEntry, delay, Tick(at.step + delay), fake)

    fun isUp(seat: Seat, entry: Int): Boolean = up.index == seat.index && upEntry == entry

    fun isDown(seat: Seat, entry: Int): Boolean = down.index == seat.index && downEntry == entry
}

/**
 * **What a seat is carrying between two cards** (F-014, `gdd.md:640`, `gdd.md:652`, D-322).
 *
 * Authority state. *Carry state is a fact about the world, not an unfinished answer*: it persists
 * through a meeting and across shifts (F-014, D-170), and it is cleared only by the step that
 * consumes it.
 */
sealed interface Carry {
    val seat: Seat

    /** The order entry this carry belongs to. */
    val entry: Int

    /**
     * **An Array Wipe disk in hand** (`gdd.md:656`). Good from Spares to the Rack, damaged from the
     * Rack to Disposal. **While one is held, no other scan opens anything** (`gdd.md:664`, F-014).
     *
     * [disk] counts from one, so the second good disk of a circuit is `2` — the `DISK 01` the
     * mockup's widget draws is the first.
     */
    class Disk(override val seat: Seat, override val entry: Int, val disk: Int, val damaged: Boolean) : Carry {
        fun swapped(): Disk = Disk(seat, entry, disk, damaged = true)

        /** What the seat's own phone is told: which disk, and which station it goes to next. */
        fun inHand(): DiskInHand =
            DiskInHand(disk, if (damaged) CircuitStation.Disposal else CircuitStation.Rack)
    }

    /**
     * **A Memory Dump pattern, in the player's head and in the house's ledger** (`gdd.md:642`).
     *
     * The house holds the pattern so that it can grade the target. **No client type carries it**
     * after the source scan that showed it: the phone may not keep it (*only your head is
     * off-network*), and Files never shows it (F-015). A fresh scan of the source replaces it; a
     * hand-over at the target consumes it, matched or not, which is what *a mismatch sends you
     * back to the source* is.
     *
     * ⚠️ It does **not** block other scans (D-322): F-014 says the Array Wipe flag blocks every other
     * scan, and no ruling says a pattern held in memory does. Flagged for the owner.
     */
    class Pattern(override val seat: Seat, override val entry: Int, val elements: List<Int>) : Carry
}

/**
 * **The Array Wipe's three stations, in D-122's draw order** — the order the circuit is walked.
 */
@ClientFacing
@kotlinx.serialization.Serializable
enum class CircuitStation { Spares, Rack, Disposal }

/**
 * **The disk in this phone's hand, as the phone may know it** (D-168, D-322): which disk, and the
 * station it goes to next. Rides [Effect.ScanAnswered], to the scanning seat alone.
 *
 * It names a station rather than a card or a room: the circuit's cards and rooms are on the seat's
 * own work-order line already, and this is the widget's `DISK 01 · TO RACK`.
 */
@ClientFacing
@kotlinx.serialization.Serializable
data class DiskInHand(val disk: Int, val to: CircuitStation)
