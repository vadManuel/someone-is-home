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
     * **How many ticks a second is worth. Every duration in this object is expressed through it.**
     *
     * ⚠️ **This is `FixedTimestep.SUGGESTED_STEP_NANOS`'s 20 Hz, adopted — and adopting it is a
     * visible act, which is exactly what that constant is named separately for.** Somebody should
     * confirm it.
     *
     * ### Why it had to be named now
     *
     * Every duration here previously assumed **one tick per second**, silently: [REVOKE_COOLDOWN]
     * was the bare literal `60` for the lobby's `REVOKE COOLDOWN 60S`. Nothing needed finer than a
     * second, so nothing noticed — and at the architecture's own suggested rate that literal was a
     * three-second cooldown.
     *
     * The Sync Pulse cannot be written at one tick per second **at all**. Its tap window has to be
     * a fraction of its beat interval or the grading stops grading (see [SYNC_PULSE_WINDOW]), and
     * at a one-second floor the only interval that leaves room for a window is slower than any
     * heartbeat two people could catch. So the assumption stopped being invisible.
     *
     * Naming it is D-130's move applied to time: the *shape* of each duration is settled, the rate
     * is one number, and moving it moves everything together instead of leaving somebody to
     * re-derive eight constants by hand.
     */
    const val TICKS_PER_SECOND: Long = 20L

    /**
     * **D-132's normal duration for the Revoke, in ticks.** The lobby's own settings row reads
     * `REVOKE COOLDOWN 60S`, which is where this number comes from and where it will move from.
     *
     * The round opens with every Insider ability **already running at half of this**, which closes
     * the opening-Revoke problem structurally rather than by asking players not to.
     *
     * ⚠️ **It was the bare literal `60`, which was only correct at one tick per second** — an
     * assumption nothing stated and nothing checked. It reads through [TICKS_PER_SECOND] now, so
     * `60S` on the lobby row and sixty seconds in the rules are the same fact rather than two that
     * happen to agree. **The number on screen has not changed; the number in ticks has.**
     */
    const val REVOKE_COOLDOWN: Long = 60L * TICKS_PER_SECOND

    /**
     * **The Egress countdown — 120s, and a placeholder for a lobby setting** (`gdd.md:353`, F-009).
     *
     * The design is explicit that this *is not a constant*: it has to cover two Residents reading
     * the node names off a widget, crossing **this particular house**, and pairing — and a
     * one-bedroom apartment and a three-storey house are not the same game. F-009 resolved it as a
     * host setting with a 120s default, and suggested better still that the app **propose** the
     * default from the painted grid, since the longest marker-to-marker path is already computable
     * from the setup walk.
     *
     * **Neither the setting nor the proposal is built.** What is built is the number the round is
     * armed against, in one place, named as the placeholder it is. Playtest owns it, and the lobby
     * owns it after that.
     */
    const val EGRESS_TIMER: Long = 120L * TICKS_PER_SECOND

    /**
     * **One shared Egress cooldown for the whole house, at 180s — a placeholder** (D-132).
     *
     * *Shared with the other Insider*: firing it puts every Insider on the same clock, which is why
     * it is one number on the round rather than a row per seat. The round opens with it **already
     * running at half**, like every other Insider cooldown (D-132), so the opening minute cannot
     * hold an Egress.
     *
     * The duration is playtest's, and the lobby's settings row will own it beside the Revoke's.
     * 180s is chosen as the longest cooldown in the design's own framing and for no measured reason.
     */
    const val EGRESS_COOLDOWN: Long = 180L * TICKS_PER_SECOND

    /** **Four taps on the beat** (`gdd.md:355`, A-11). The design's number, not a placeholder. */
    const val SYNC_PULSE_BEATS: Int = 4

    /**
     * How long after the Egress fires the first beat lands, and how far apart the beats are.
     *
     * The schedule runs from the moment the Egress fired and is **house-wide**: every phone pulses
     * on the same beats, so concurrent pairs forming at different moments are still tapping in
     * unison with each other. Both are playtest's.
     *
     * **A slow beat is the correct direction here rather than a concession.** Two people who cannot
     * speak, in the dark, in different rooms, have to catch the same rhythm — that is a heartbeat,
     * not a metronome, and the design already forbids twitch timing outright.
     */
    const val SYNC_PULSE_LEAD: Long = 2L * TICKS_PER_SECOND
    const val SYNC_PULSE_INTERVAL: Long = 3L * TICKS_PER_SECOND / 2L

    /**
     * **The tap window, and it is generous on purpose** (game-architecture.md:249).
     *
     * *Twitch timing is forbidden, and a generous window absorbs device skew.* This is the one
     * timing number in the game the architecture explicitly refuses to tighten: the players are in
     * the dark, holding the only light in the house, and two phones' clocks agree to about a
     * hundred milliseconds. A tight window would turn a coordination beat into a reflex test and
     * would fail honest pairs for reasons neither of them could see.
     *
     * ⚠️ **It cannot be widened past half of [SYNC_PULSE_INTERVAL], and that is a hard edge rather
     * than a preference.** At half the interval every possible tap is within the window of *some*
     * beat, so the grading stops grading: a player hammering the screen passes, a player who never
     * looked up passes, and the whole Sync Pulse becomes a four-tap button with a delay in front of
     * it. Nothing about that failure is visible — the tests still pass, the pairs still contain,
     * and the beat is decoration. [syncPulseIsGradeable] holds the line.
     */
    const val SYNC_PULSE_WINDOW: Long = 2L * TICKS_PER_SECOND / 5L

    /**
     * **Whether the beat can be missed at all.** See [SYNC_PULSE_WINDOW]'s warning.
     *
     * A function rather than a comment, because the numbers above are playtest's and the person who
     * widens the window to be kind will not be reading this file's KDoc when they do it.
     */
    fun syncPulseIsGradeable(): Boolean = SYNC_PULSE_WINDOW * 2 < SYNC_PULSE_INTERVAL

    /**
     * **What a missed beat costs — the design's 2–3s** (`gdd.md:359`).
     *
     * *Makes stalling meaningfully more expensive and stops spam-retry from being optimal.* Three,
     * the top of the range, because the thing it is priced against is a Insider standing at a node
     * hammering the button, and the cheaper end buys less of that.
     */
    const val SYNC_PULSE_LOCKOUT: Long = 3L * TICKS_PER_SECOND

    /**
     * **How simultaneous *simultaneously* is** (`gdd.md:352`).
     *
     * Two people at two separate markers, at the same time — but the beats they hit are their own,
     * and two phones will not hand over on the same tick. This is how long a graded beat waits for
     * a partner before it goes stale.
     *
     * **One full run of the pulse**, derived rather than typed, which is what makes it mean
     * something: two people who both kept time through overlapping runs always meet, and somebody
     * who tapped and wandered off cannot be paired with by a stranger arriving later. A number
     * chosen by hand would drift out of that relationship the first time the beat moved.
     */
    const val SYNC_PULSE_PAIR_WINDOW: Long = SYNC_PULSE_BEATS * SYNC_PULSE_INTERVAL

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
