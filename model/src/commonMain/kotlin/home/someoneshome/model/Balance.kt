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
     *
     * ---
     *
     * ## ⚠️ TEMPORARILY LOWERED TO 3 — OWNER-ORDERED 2026-08-25 — MUST RETURN TO 5
     *
     * **This is not a balance decision and it does not ship.** The owner has three iPhones
     * physically in hand and wants a round that arms on three phones; the ruled minimum is still
     * **five** (D-128, revision 29's D-158 family). **Put this back to 5 before release**, and when
     * you do, delete `minimumSeatsIsTemporarilyLoweredForDeviceTesting` in `BalanceTest` — that
     * test exists only to make this line impossible to forget.
     *
     * The instruction was *only* the minimum and nothing else, so these consequences are
     * **verified, not adjusted**:
     *
     * - **The review gate stays at eight markers.** It is not derived from this constant and was
     *   explicitly left alone.
     * - **A three-seat party draws exactly one Insider**, by the existing formula and with no edit:
     *   [InsiderBand]'s amendment pins `MINIMUM_SEATS..6` to `1..1`, so lowering the floor extends
     *   the pin down to three rather than opening a band there. Two Insiders in three seats would
     *   be parity at arming; the pin is what stops it.
     * - **The vote at three seats is the thing D-128 refuses**, and that is the known cost of the
     *   evening: one wrong Restrain reaches parity. The round is for exercising three phones, not
     *   for reading balance off.
     */
    const val MINIMUM_SEATS: Int = 3

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
     * **A UWB report's hold: two seconds** (the owner's Q5 ruling, D-301), in milliseconds. Here so
     * that the house's hold (`ContactGate.REPORT_HOLD_TICKS`, read through [TICKS_PER_SECOND]) and the
     * circle the reader's phone fills over it (D-307) are the same number: `ui` cannot see `core`.
     */
    const val REPORT_HOLD_MILLIS: Long = 2_000L

    /**
     * **The REVOKE COOLDOWN row's default, in seconds: the lobby's own `60S`** (D-317).
     *
     * A host's setting since D-317, locked at arming ([Event.RoundArmed.revokeCooldownSeconds]) and
     * read by the rules off [GameState.revokeCooldown], never off this. This is what a round armed
     * without a setting gets, and the only choice the lobby's row offers until the owner rules a
     * range (no ruling sets one: `gdd.md:930`, `:1181`, D-132, D-037).
     */
    const val REVOKE_COOLDOWN_SECONDS: Int = 60

    /**
     * **D-132's normal duration for the Revoke, in ticks — the default only** (D-317). The lobby's
     * REVOKE COOLDOWN row is the host's now, and the round reads the locked
     * [GameState.revokeCooldown]; this is [REVOKE_COOLDOWN_SECONDS] in ticks, for a round armed
     * without a setting.
     *
     * The round opens with every Insider ability **already running at half of this**, which closes
     * the opening-Revoke problem structurally rather than by asking players not to.
     *
     * ⚠️ **It was the bare literal `60`, which was only correct at one tick per second** — an
     * assumption nothing stated and nothing checked. It reads through [TICKS_PER_SECOND] now, so
     * `60S` on the lobby row and sixty seconds in the rules are the same fact rather than two that
     * happen to agree. **The number on screen has not changed; the number in ticks has.**
     */
    const val REVOKE_COOLDOWN: Long = REVOKE_COOLDOWN_SECONDS * TICKS_PER_SECOND

    /**
     * **How long an armed Revoke stays armed: 20 s, or until a meeting is called, whichever comes
     * first** (the owner's 2026-11-18 ruling, which overrides the 45 s of `gdd.md:323`/`:1155` and
     * D-142).
     *
     * Counted by the house from the arm's own tick ([RevokeArm.lapsesAt]) and by the Insider's
     * phone from its own hold, and both end in silence: nothing is sent to anybody, the house stops
     * treating the seat as armed, and the REVOKE tile goes back to the public clock. Shorter than
     * [REVOKE_COOLDOWN], so the cooldown spent at arming always outlives the window it bought.
     */
    const val REVOKE_ARM_WINDOW: Long = 20L * TICKS_PER_SECOND

    /**
     * **The EGRESS TIMER row's default, in seconds: 120** (D-014, F-009, `gdd.md:382`, D-317).
     *
     * A host's setting since D-317, locked at arming ([Event.RoundArmed.egressTimerSeconds]) and read
     * by the rules off [GameState.egressTimer], never off this. D-014 gives the default and no range
     * or step, so this is also the row's only choice until the owner rules one.
     */
    const val EGRESS_TIMER_SECONDS: Int = 120

    /**
     * **The Egress countdown's default, in ticks** (`gdd.md:353`, F-009). Since D-317 the host's
     * setting is built and the round reads the locked [GameState.egressTimer]; this is what a round
     * armed without a setting gets. What follows is the history, and its first sentence still holds.
     *
     * The design is explicit that this *is not a constant*: it has to cover two Residents reading
     * the node names off a widget, crossing **this particular house**, and pairing — and a
     * one-bedroom apartment and a three-storey house are not the same game. F-009 resolved it as a
     * host setting with a 120s default, and suggested better still that the app **propose** the
     * default from the painted grid, since the longest marker-to-marker path is already computable
     * from the setup walk.
     *
     * **The setting is built (D-317); the proposal is not**, because no ruling says how the longest
     * path becomes seconds (D-014 gives the idea and no rate).
     */
    const val EGRESS_TIMER: Long = EGRESS_TIMER_SECONDS * TICKS_PER_SECOND

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
     * Tier 2 is one clock shared by Surge, Static and Isolate ([InsiderAbility.Access]), and each of
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
     * **The three clocks at half: D-132's opening, and D-184's every meeting end.**
     *
     * One arithmetic, named once, because two layers read it: the rules write every seat's clocks
     * off it at LIGHTS OUT and at each meeting's end, and page 2 draws the same halves on every
     * phone (D-239). Both moments are pushed to every phone, so a clock restarted there is a
     * PUBLIC fact; a `/ 2` in each layer would be two copies that agree until somebody moves one.
     */
    const val REVOKE_OPENING: Long = REVOKE_COOLDOWN / 2

    /**
     * **Any cooldown at half: the one arithmetic behind the three openings above** (D-132, D-184).
     * The rules apply it to the Revoke cooldown the round was armed with (D-317), and page 2 to the
     * one the house pushed, so the `/ 2` is still written once.
     */
    fun opening(cooldown: Long): Long = cooldown / 2
    const val ACCESS_OPENING: Long = ACCESS_COOLDOWN / 2
    const val EGRESS_OPENING: Long = EGRESS_COOLDOWN / 2

    /**
     * **A duration in ticks, as the whole seconds a screen counts, rounded up.**
     *
     * Here and not in `ui` for the reason [TICKS_PER_SECOND] is named at all: a screen that divided
     * by the tick rate would hold its own opinion of it. Rounded up so that a screen counting these
     * seconds never reaches the end before the house does.
     */
    fun wholeSeconds(ticks: Long): Int = ((ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND).toInt()

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
     * The pool's nominal, unweighted: Isolate is neither the commitment Surge is nor free. It is
     * also the pool's **lightest** weight, and it did not use to be — see [STATIC_COOLDOWN], which
     * took the slot the old third ability held. Playtest owns the relationship between the three.
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
     * **Static's weight on the pool — heavier than the ability it replaced, and a placeholder**
     * (D-186).
     *
     * D-186 replaced Spoof rather than tuning it, and it named the direction of this number without
     * naming the number: *heavier than old Spoof's — constant raised; playtest tunes it.* Spoof's
     * was the pool's lightest at sixty seconds, priced down so that `gdd.md:1347`'s *nobody used
     * it* would be a finding about the ability rather than about its price. **Static needs no such
     * discount**: it costs nothing to understand, it is fired because you are about to do something,
     * and it announces itself to the whole house.
     *
     * So the pool's three weights are ordered rather than guessed: **Surge, then Static, then
     * Isolate.** A Surge takes one player out of the game for five seconds and is the loudest thing
     * that can happen (D-182); a Static blinds every reader in the house for ten and tells the
     * house it did; an Isolate takes one card dark and nothing anywhere says so. The ordering is
     * the claim this number is making, and the value is playtest's.
     */
    const val STATIC_COOLDOWN: Long = 120L * TICKS_PER_SECOND

    /**
     * **How long the house's monitoring stays jammed — ten seconds, and it is OWNER-SET** (D-186).
     *
     * Not a placeholder, unlike almost everything else in this file. The ruling's table gives the
     * duration as **10s** beside the scope and the exemption, and the length is doing work: it is
     * long enough that a reader at the station loses the window they were reading and short enough
     * that they are still standing there when it clears, which is what makes *you know an Insider
     * did something in the last ten seconds* the sentence the ability is for.
     *
     * Well under [STATIC_COOLDOWN] — see [staticIsAWindow]. At or past parity two Insiders could
     * hold the house's monitoring dark for a whole shift by alternating, and a permanent jam is a
     * broken Terminal rather than an ability.
     */
    const val STATIC_DURATION: Long = 10L * TICKS_PER_SECOND

    /**
     * **Whether a jam is really a window rather than a condition.** See [STATIC_DURATION].
     *
     * [isolateIsTemporary]'s shape and its reason: [STATIC_COOLDOWN] is playtest's, and the person
     * who shortens it to make the ability bite will not be reading this file's KDoc when they do
     * it. **It is deliberately not asked per Insider.** The pool is per-seat and the jam is
     * house-wide, so what this guards is the single-Insider case; two Insiders alternating is a
     * balance question and belongs to the playtest, not to an assertion.
     */
    fun staticIsAWindow(): Boolean = STATIC_DURATION < STATIC_COOLDOWN

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
     * **The pulse's three timings on the house's millisecond clock** (C2), derived from the tick
     * constants above so there is still one number of each.
     *
     * The schedule is graded in milliseconds of the host's monotonic clock and not in ticks because
     * the house's ticks do not keep wall time: the tick driver adds one per arrival on top of its
     * twenty a second, skips forward after the host's phone has been away (D-230), and a bench can
     * skew it. A haptic beat in two hands has to land at one moment of real time, and a phone can
     * measure its offset to the host's clock (C1, ARCH D5); it has no way to know the house's tick.
     */
    const val SYNC_PULSE_LEAD_MILLIS: Long = SYNC_PULSE_LEAD * 1_000L / TICKS_PER_SECOND
    const val SYNC_PULSE_INTERVAL_MILLIS: Long = SYNC_PULSE_INTERVAL * 1_000L / TICKS_PER_SECOND
    const val SYNC_PULSE_WINDOW_MILLIS: Long = SYNC_PULSE_WINDOW * 1_000L / TICKS_PER_SECOND

    /** [syncPulseIsGradeable] for the millisecond timings the house actually grades on. */
    fun syncPulseIsGradeableInMillis(): Boolean = SYNC_PULSE_WINDOW_MILLIS * 2 < SYNC_PULSE_INTERVAL_MILLIS

    /**
     * **What a missed beat costs — 3 s, the tapping phone vibrating for all of it** (D-298).
     *
     * *Makes stalling meaningfully more expensive and stops spam-retry from being optimal.* Three,
     * the top of the range, because the thing it is priced against is an Insider standing at a node
     * hammering the button, and the cheaper end buys less of that.
     */
    const val SYNC_PULSE_LOCKOUT: Long = 3L * TICKS_PER_SECOND

    // **How simultaneous *simultaneously* is** (`gdd.md:352`) is no longer a time: two runs of four
    // pair when they share at least one pulse of the house's grid (the owner's O3 pairing ruling,
    // option B, D-298; `Egress.pairs`). Until then it was `SYNC_PULSE_PAIR_WINDOW`, one full run
    // (6 s) between the two uploads' arrivals.

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

    /**
     * **The talk — 90s, the design's own number and a lobby setting after that** (`gdd.md:412`,
     * `:1005`, `:1122`, A-10). **The DISCUSSION row's default since D-317**: the host's setting
     * is locked at arming ([Event.RoundArmed.discussionSeconds]) and the house closes the talk on
     * [GameState.discussionSeconds]; this is what a round armed without a setting gets, and the
     * row's only choice until the owner rules a range.
     *
     * *The phone shows a timer and one control. Unanimous "Ready to vote" skips ahead.* The skip is
     * a count of phones and ends the talk early; this is the other way it ends, and it is the
     * house's clock.
     *
     * ### It moved here because something finally had to READ it
     *
     * It was a literal in `ui/Countdown.kt`, drawn beside `LIGHTS OUT IN` and the vote's window,
     * and that was honest while nothing anywhere ran a discussion — a screen with a bar on it needs
     * a denominator whether or not a house is attached. **The tick driver is the first thing that
     * has to know when the talk is over**, and a number the driver held privately would be a second
     * copy of ninety: the bar on five phones would drain against one and the transition would fire
     * off the other, and the two would agree until somebody moved one.
     *
     * Seconds and not ticks, for [VOTE_WINDOW_SECONDS]'s reason: it is displayed, and a screen that
     * had to divide by [TICKS_PER_SECOND] to draw a clock would be a screen holding an opinion
     * about the rate.
     */
    const val DISCUSSION_SECONDS: Int = 90

    /**
     * **The result, and the LIGHTS OUT countdown under it — 15s** (D-102, D-134's E1-1).
     *
     * Read off the design's own drawn screen, which is where it has always lived: `LIGHTS OUT IN 9`
     * over a bar with six of fifteen spent. [DISCUSSION_SECONDS]' note applies exactly — it was a
     * literal in `ui/Countdown.kt` until the tick driver needed to know when the meeting was over.
     *
     * **Two moments happen inside it and they are not the same one.** At the halfway mark the
     * Restrained takeover reaches the losing seat (D-102: the screen arrives at halfway *so the
     * player does not walk away when the countdown ends*), and at zero everybody else goes back to
     * the round. The halfway mark is derived rather than typed — see the driver — so a change here
     * moves both together and cannot leave a takeover landing after the lights went out.
     */
    const val TALLY_SECONDS: Int = 15

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

    /**
     * **The design's mix, as proportions of `K`** (D-005, D-129; the owner's 2026-11-21 ruling,
     * D-322): *1 circuit : 1 long : 3 medium : 2 short*, in that order.
     *
     * D-005 wrote the mix for an order of seven. D-129 replaced the fixed seven with `K`, and the
     * owner chose to **scale the proportions** rather than keep seven fixed entries and pad the rest:
     * about 14% circuit, 14% long, 43% medium and 29% short of whatever `K` the lobby's public facts
     * compute. The weights are written here once, in structure order, and [designMix] is the only
     * thing that reads them.
     */
    val ORDER_MIX_WEIGHTS: List<Pair<SubroutineStructure, Int>> = listOf(
        SubroutineStructure.Circuit to 1,
        SubroutineStructure.Long to 1,
        SubroutineStructure.Medium to 3,
        SubroutineStructure.Short to 2,
    )

    /**
     * **Whether the mix deals a Memory Dump: not until its screens are designed** (the owner's
     * 2026-11-22 ruling, D-323 A). ⚠️ **Provisional, owner-pending.**
     *
     * D-322 built Memory Dump's house half and STOPPED its two screens, so no phone can open one.
     * Dealt into every order as D-322 had it, every seat held a line nobody could finish and the
     * meter could not reach 0. **While this is false [orderMix] gives the long's share to medium**;
     * the house half stays built and is tested on lines planted by hand, not by the draw.
     *
     * **One switch, so dealing it is one line**: set this to true when Memory Dump's screens exist,
     * and [orderMix] is [designMix] again. It is not a lobby setting and not a balance value a round
     * locks: it says what the build can play, and no role reads it.
     */
    val MEMORY_DUMP_DEALT: Boolean = false

    /**
     * **How many entries of each structure an order of [size] holds**: [designMix], with the long's
     * share dealt as medium while [MEMORY_DUMP_DEALT] is off (D-323 A). Always sums to [size], and
     * **no role is an input**.
     */
    fun orderMix(size: Int): Map<SubroutineStructure, Int> {
        val mix = designMix(size)
        if (MEMORY_DUMP_DEALT) return mix
        return mix + mapOf(
            SubroutineStructure.Long to 0,
            SubroutineStructure.Medium to mix.getValue(SubroutineStructure.Medium) + mix.getValue(SubroutineStructure.Long),
        )
    }

    /**
     * **The design's mix of an order of [size], Memory Dump included** — D-322's rounding, ⚠️
     * **provisional until the owner confirms it.** What [orderMix] deals once [MEMORY_DUMP_DEALT]
     * is on.
     *
     * **Largest remainder** (Hamilton's method), integer-only, and the answer always sums to [size]:
     *
     * 1. each structure's quota is `size × weight ÷ 7`; every structure first takes the whole part
     *    of its quota;
     * 2. the entries still unassigned go one each to the structures with the **largest fractional
     *    remainders**;
     * 3. **a tie goes to the cheaper structure first** — short, then medium, then long, then circuit.
     *    With these weights the only remainders that can ever tie are the circuit's and the long's,
     *    which share a weight, so the rule in practice is *the long before the circuit*: a circuit is
     *    the most expensive entry in the game (~160 s, three markers, a carry that blocks everything
     *    else), and a tie is not a reason to deal a second one.
     *
     * At `K = 7` it is the design's 1/1/3/2 exactly. At the `K`s a lobby actually computes it reads:
     * 6 → 1/1/2/2, 8 → 1/1/4/2, 9 → 1/1/4/3, 10 → 1/2/4/3.
     *
     * Integer arithmetic for [orderSize]'s reason: a balance number that depends on a rounding mode
     * is a number that differs between two phones. **No role is an input**, so an Insider's fake
     * order has the same shape as every Resident's (D-109, D-129).
     */
    fun designMix(size: Int): Map<SubroutineStructure, Int> {
        if (size <= 0) return SubroutineStructure.entries.associateWith { 0 }
        val total = ORDER_MIX_WEIGHTS.sumOf { it.second }
        val whole = ORDER_MIX_WEIGHTS.associate { (s, w) -> s to (size * w) / total }
        val remainder = ORDER_MIX_WEIGHTS.associate { (s, w) -> s to (size * w) % total }
        val left = size - whole.values.sum()
        // Cheaper first on a tie: the structures in ascending cost, then a stable sort on the
        // remainder, descending. `sortedByDescending` is stable, so the cost order survives a tie.
        val byCost = listOf(
            SubroutineStructure.Short,
            SubroutineStructure.Medium,
            SubroutineStructure.Long,
            SubroutineStructure.Circuit,
        )
        val extra = byCost.sortedByDescending { remainder.getValue(it) }.take(left)
        return SubroutineStructure.entries.associateWith { s ->
            whole.getValue(s) + if (s in extra) 1 else 0
        }
    }

    /**
     * **The random unlock delay on a cross-player dependency, in whole seconds** (`gdd.md:284`,
     * F-013, D-074, D-146): **20–120 s**, held at the original window (D-011).
     *
     * Drawn once per dependency from the round's seed, uniform over the whole seconds of the
     * window, and applied from the moment the upstream entry is completed. ⚠️ The **uniform
     * distribution** is the build's reading (D-322): the design says *random 20–120 s* and *start at
     * the short end and lengthen if playtests show the game is too blind*, and names no shape.
     */
    const val UNLOCK_DELAY_LEAST_SECONDS: Int = 20
    const val UNLOCK_DELAY_MOST_SECONDS: Int = 120

    /**
     * **The longest a dependency chain may be: `L ≤ living Insiders + 1`** (`gdd.md:273`, D-324),
     * counted in the players the chain links.
     *
     * The design's bound, built as written: *tight — one more would generate a Subroutine that
     * becomes literally unstaffable while the game is still running*. Read at arming, where every
     * Insider is living. The draw links Residents in pairs (D-324), so every lawful round, whose
     * band never falls below one Insider, has room for its chains; a hand-built round with no
     * Insider has a bound of one and draws no chain at all.
     */
    fun chainBound(livingInsiders: Int): Int = livingInsiders + 1

    /**
     * **Two damaged disks to an Array Wipe** (`gdd.md:660`): *two damaged disks → four traverses*,
     * Spares to Rack and Rack to Disposal, twice, carrying one disk at a time.
     */
    const val ARRAY_WIPE_DISKS: Int = 2
}
