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
     * **This is `FixedTimestep.SUGGESTED_STEP_NANOS`'s 20 Hz, adopted — and adopting it is a
     * visible act, which is exactly what that constant is named separately for. Confirmed by the
     * owner on 2026-08-22** (E-L5-1, closed), which is what makes [REVOKE_COOLDOWN]'s sixty seconds
     * sixty seconds rather than an assumption two constants happen to share.
     *
     * It is asserted rather than trusted, and asserted where the *other* clocks are: a Subroutine's
     * parameters carry tenths of a second and its screen counts milliseconds, so three clocks meet
     * in one system and the failure mode of three clocks is two of them being multiplied together.
     * See `SubroutineDrawTest` in `core`.
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

    /**
     * **The Access pool's nominal duration — a placeholder, and it is what D-132 halves**
     * (`gdd.md:306`, D-132).
     *
     * Tier 2 is one clock shared by Surge, Spoof and Isolate ([InsiderAbility.Access]), and each of
     * them restarts it for its **own** weight below. So the pool has no single duration in play —
     * which leaves D-132's *every Insider cooldown begins the round at half its normal duration*
     * with nothing to be half of. This is that number: the pool's nominal, halved at arming, so the
     * round opens with a guaranteed stretch in which no support ability is available either.
     *
     * Between [REVOKE_COOLDOWN]'s sixty and [EGRESS_COOLDOWN]'s hundred and eighty, because Tier 2
     * is *deliberate, never used while running* and the two Tier 1 abilities are the time-critical
     * ones. Playtest owns it, and the lobby's settings row will own it after that.
     */
    const val ACCESS_COOLDOWN: Long = 90L * TICKS_PER_SECOND

    /**
     * **Surge's weight on the pool — the longest in it, and a placeholder** (`gdd.md:342`).
     *
     * *Surge is a commitment, not a tool. Either half alone was a nudge; together it is the loudest,
     * brightest thing that can happen in a silent dark house, and everyone converges on that person
     * immediately.* **Longest cooldown in the Access pool** is the design's own sentence and this is
     * it as a number — it is also what prices the self-surge alibi (`gdd.md:342`), which buys cover
     * at the cost of the whole support kit for a long time.
     *
     * Longer than [ACCESS_COOLDOWN] and shorter than [EGRESS_COOLDOWN]: the pool's longest is not
     * the game's longest, because the Egress is a win condition and this is a noise.
     */
    const val SURGE_COOLDOWN: Long = 150L * TICKS_PER_SECOND

    /**
     * **Isolate's weight on the pool — a placeholder** (`gdd.md:315`).
     *
     * The pool's nominal, unweighted: Isolate is neither the commitment Surge is nor free. Playtest
     * owns the relationship between this, [SURGE_COOLDOWN] and Spoof's, which is unbuilt.
     */
    const val ISOLATE_COOLDOWN: Long = ACCESS_COOLDOWN

    /**
     * **How long a marker stays offline — a placeholder** (`gdd.md:315`, `:621`).
     *
     * *Temporarily* is the whole of what the design says, and the one place it says more is the
     * Array Wipe interaction: Isolating the Disposal marker *does not delay one task — it locks that
     * player out of every task until it clears. **Temporary, so a delay rather than a permanent
     * lock.*** That sentence is what this number has to keep true, so it is deliberately well under
     * a shift: long enough that a carrier has to wait somewhere in the dark, short enough that
     * waiting is the answer.
     *
     * Shorter than [ISOLATE_COOLDOWN], which is the shape that matters more than either value: a
     * duration longer than its own cooldown would let one Insider keep a marker permanently dark,
     * which is the permanent lock the design ruled out.
     */
    const val ISOLATE_DURATION: Long = 45L * TICKS_PER_SECOND

    /**
     * **Whether an Isolation is really temporary.** See [ISOLATE_DURATION].
     *
     * A function rather than a comment, for [syncPulseIsGradeable]'s reason: the numbers above are
     * playtest's, and the person who lengthens the Isolation to make it bite will not be reading
     * this file's KDoc when they do it. At or past parity one Insider can hold a marker dark for the
     * whole round by re-firing, and `gdd.md:621` is explicit that a permanent lock is not the
     * ability.
     */
    fun isolateIsTemporary(): Boolean = ISOLATE_DURATION < ISOLATE_COOLDOWN

    /**
     * **Spoof's weight on the pool — the lightest in it, and a placeholder** (`gdd.md:313`).
     *
     * The third and last weight on the Access pool, and the cheapest of the three on purpose.
     * `gdd.md:1143` rates Spoof the roster's **cut risk**: *"Spoof may be too abstract for the dark.
     * Surge and Revoke have immediate, visceral effects; Spoof is slow, indirect, and asks a Insider
     * to think about the map layer while standing in a dark hallway. It may simply never get
     * used."* — and `gdd.md:1342` turns that into a measurement: **fired at least once per three
     * rounds, or it is cut.**
     *
     * Pricing an ability nobody reaches for at the same weight as one everybody does would be the
     * build putting a thumb on that measurement's scale. It is the lightest weight in the pool so
     * that *nobody used it* is a finding about the ability rather than about its price.
     *
     * ⚠️ **The metric is the point of this number and it is not measurable yet.** See
     * `Transcript.spoofFires`, which is the counting half.
     */
    const val SPOOF_COOLDOWN: Long = 60L * TICKS_PER_SECOND

    /**
     * **How long a phantom occupant lasts — a placeholder** (`gdd.md:313`).
     *
     * The design says nothing at all about this: *false check-in in a room you are not in;
     * manufactures a phantom occupant* is the whole of it, and a check-in is an instant rather than
     * a duration. So a number had to be chosen, and what it is choosing between is a lie that
     * decays like a real sighting and a lie that stands until something contradicts it.
     *
     * It decays. A phantom that outlived its round would make the map's oldest data its most
     * confident, which is the opposite of what a staleness-banded map is for (`gdd.md`'s three
     * bands) — and D-136's *adjacent-room bleed is accepted* only reads as acceptable because a
     * sighting is a recent thing.
     *
     * Longer than [ISOLATE_DURATION] because a lie has to outlast the walk to the room it names,
     * and shorter than [SPOOF_COOLDOWN] for [ISOLATE_DURATION]'s exact reason: at or past parity
     * one Insider could keep a permanent false occupant standing in a room by re-firing, and a
     * permanent phantom is a fixture rather than a check-in.
     */
    const val SPOOF_DURATION: Long = 50L * TICKS_PER_SECOND

    /**
     * **Whether a phantom is really a check-in rather than a fixture.** See [SPOOF_DURATION].
     *
     * [isolateIsTemporary]'s shape and its reason: the numbers above are playtest's, and the person
     * who lengthens a phantom to make it bite will not be reading this file's KDoc when they do it.
     */
    fun phantomIsTemporary(): Boolean = SPOOF_DURATION < SPOOF_COOLDOWN

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

    /**
     * **The ballot's window, in seconds — the design's 45, and the host may move it** (D-117,
     * `gdd.md:412`, `:1006`).
     *
     * The one number in this object that is **not** a placeholder: it is the design's own, and it
     * is here rather than in `ui` because it is now the default a round is armed against
     * ([Event.RoundArmed.voteWindowSeconds]) and the number a client draws when no house has said
     * otherwise. Two copies of forty-five, one in the rules and one on a screen, is the drift
     * `Countdowns` was already caught in once.
     *
     * Seconds and not ticks, deliberately, unlike every duration above it. This one is *displayed*
     * — the host cycles it as `45S` and the couch counts it down — and a screen that had to divide
     * by [TICKS_PER_SECOND] to draw a clock would be a screen holding an opinion about the rate.
     */
    const val VOTE_WINDOW_SECONDS: Int = 45

    /** `M`, the whole bar, fixed at arming and never moved again (`gdd.md:1322`). */
    fun meterTotal(seats: Int): Int = seats * METER_PER_SEAT

    /**
     * **System Integrity as a percentage, and there is nowhere else this arithmetic may live**
     * (D-103, D-153).
     *
     * *Percentage only, never `28/42`.* A total of `seats × METER_PER_SEAT` is public arithmetic,
     * so an absolute value on any wire or any screen hands back the division D-130 retired the
     * Resident operand to close. The wire carries the output of this function and never its inputs.
     *
     * **[remaining], not completed.** The meter counts down: a round opens at 100% and the
     * Residents win at 0 (D-131's first route). Integer division, floored, for [InsiderBand]'s
     * reason — and floored is the right direction for the one value it matters at, since a meter
     * with work still on it must never round up to a clean 0%.
     *
     * A round with no seats has no bar; it reads 0 rather than dividing by nothing.
     */
    fun integrityPercent(remaining: Int, seats: Int): Int {
        val total = meterTotal(seats)
        if (total <= 0) return 0
        return (remaining.coerceIn(0, total) * 100) / total
    }

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
