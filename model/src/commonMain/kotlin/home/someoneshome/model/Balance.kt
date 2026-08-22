package home.someoneshome.model

/**
 * **The numbers a round is armed against, in one place because they are one decision.**
 *
 * Every constant here is a **placeholder that playtest owns** (D-129, D-130, D-132, revision 30's
 * list). What is settled is the *shape* of each formula, and naming the shape apart from the
 * number is exactly what D-130 was for: `7 × residents` was a coefficient with the scaling already
 * implied inside it, so moving one meant re-deriving the other.
 *
 * They live in `model` rather than in the rules because three layers need them and none of them
 * may hold its own opinion: the rules draw the round against them, the lobby's gate reads the
 * minimum party off them, and a test asserting reachability computes both sides from them. Three
 * copies of "five" are three copies that drift.
 *
 * ### Everything here is a PUBLIC fact, and that is load-bearing
 *
 * Not one of these functions takes the drawn Insider count. [orderSize] takes the host's *setting*
 * — a number every phone in the lobby can read, or UNKNOWN — because sizing a work order against
 * the count the house actually drew would let order length divide out the number D-103 spent a
 * whole revision hiding. Same division the percentage-only meter exists to prevent, arriving
 * through the other door.
 */
object Balance {

    /**
     * **D-128 — the minimum party is five seats.** Four plain Residents and one Insider.
     *
     * Below that the vote has nothing to work with: three plain Residents and an Insider is one
     * wrong Restrain away from parity, and the meeting the whole game is built around becomes a
     * formality. This is a **gate and not guidance**, which is the one thing that separates it
     * from D-127's HOSTS UP TO N — a host can see how many people are standing in their hall, but
     * D-125's sorting rule turns on what players cannot perceive, and *this party cannot produce
     * a meeting worth holding* is not visible from the hall.
     */
    const val MINIMUM_SEATS: Int = 5

    /**
     * **D-130 — SystemIntegrity scales with seats, and this coefficient is playtest's.**
     *
     * `M = seats × METER_PER_SEAT`. It retires the Resident operand, and the reason is not
     * tidiness: under D-103 the Insider count can be hidden, and a total of `(seats − insiders) × K`
     * *is* that count, recoverable by anyone who ever sees an absolute meter value. The display
     * rule (percentage only, never `28/42`) closes the panel; this closes the arithmetic behind it.
     */
    const val METER_PER_SEAT: Int = 5

    /**
     * **D-129's `slack` — the one new balance knob, and playtest owns it.**
     *
     * `K = ⌈M ÷ worstCasePlainResidents⌉ + slack`. Without a slack term `K` is exactly the ceiling,
     * so a single entry that is never completed — a Revoked player's orphans, an order whose tail
     * nobody reached — puts the bar out of reach for the rest of the round. One is the smallest
     * number that is not zero, chosen for that reason and for no other.
     *
     * **It moves L1's eight-seat `K` from 7 to 8**, which is a visible change to a number that was
     * deliberately parked where the old placeholder sat. Recorded rather than absorbed: the
     * coefficient and the slack are both playtest's and this is the first build that has to pick
     * one of each.
     */
    const val ORDER_SLACK: Int = 1

    /** **D-122 — Spares, Rack and Disposal.** Three stations, drawn every round, never stored. */
    const val STATIONS: Int = 3

    /**
     * How many ordinary markers the house lights per seat when it draws the round's active set.
     *
     * **A placeholder, and the active set is capped by the home as well** (D-123: markers are
     * capacity, not workload). A home with fewer markers than this asks for is not short of
     * anything — same-card reuse absorbs it, and the player meets the shortage as
     * blocked-by-your-own-work rather than as a refusal.
     */
    const val ACTIVE_MARKERS_PER_SEAT: Int = 2

    /**
     * **D-132's normal duration for the Revoke, in ticks.** The lobby's own settings row reads
     * `REVOKE COOLDOWN 60S`, which is where this number comes from and where it will move from.
     *
     * The round opens with every Insider ability **already running at half of this**, which closes
     * the opening-Revoke problem structurally rather than by asking players not to.
     */
    const val REVOKE_COOLDOWN: Long = 60L

    /** `M`, the whole bar, fixed at arming and never moved again (`gdd.md:1322`). */
    fun meterTotal(seats: Int): Int = seats * METER_PER_SEAT

    /**
     * **The fewest plain Residents the lobby's public facts allow** — D-129's denominator.
     *
     * `seats − bandMax` when the count is UNKNOWN, and `seats − chosen` when the host has picked
     * one, because a picked count is on every phone's settings row. Both are public; neither is
     * the draw.
     *
     * Floored at one so the division below cannot divide by zero for a lobby that has nobody in
     * it. That is arithmetic hygiene rather than a balance statement — [MINIMUM_SEATS] is what
     * stops a round that small being armed at all.
     */
    fun worstCasePlainResidents(seats: Int, chosenInsiders: Int?): Int {
        val insiders = InsiderBand.clamp(seats, chosenInsiders) ?: InsiderBand.of(seats).let {
            if (it.isEmpty()) 0 else it.last
        }
        return maxOf(1, seats - insiders)
    }

    /**
     * **D-129 — `K`, the size of every seat's work order, computed from public facts alone.**
     *
     * `K = ⌈M ÷ worstCasePlainResidents⌉ + slack`. Identical for every seat, and identical for both
     * roles: **the Insider's fake order is drawn from this same rule**, so order length is
     * role-independent by construction rather than by a rule somebody has to keep applying.
     *
     * Ceiling division, integer-only, for [InsiderBand]'s reason — a balance number that depends on
     * a rounding mode is a balance number that differs between two phones.
     */
    fun orderSize(seats: Int, chosenInsiders: Int?): Int {
        val worst = worstCasePlainResidents(seats, chosenInsiders)
        val total = meterTotal(seats)
        return (total + worst - 1) / worst + ORDER_SLACK
    }

    /**
     * **D-123 — the round's active marker set, sized to seats and capped by the home.**
     *
     * *A registered marker is a slot the house may use, not work that must be done.* What is left
     * over sits dark this round and is drawn again next round, which is what stops a home becoming
     * a fact players learn once and keep.
     *
     * At least one, so a home at D-127's floor still has somewhere for the work to be.
     */
    fun activeMarkers(seats: Int, available: Int): Int =
        maxOf(1, minOf(available, seats * ACTIVE_MARKERS_PER_SEAT))
}
