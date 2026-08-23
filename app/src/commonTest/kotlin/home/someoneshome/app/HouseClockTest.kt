package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.Egress
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The tick driver's deciding half, on a clock this file made up — E-P3-3.**
 *
 * Five authority pushes were waiting on a driver that did not exist, so a meeting left alone
 * stopped at whatever phase it reached and an Egress countdown never reached zero. What is checked
 * here is *when the house mints*, and it is checked with **no coroutine, no delay and no wall
 * clock**: [HouseClock.turned] takes elapsed nanoseconds and gives back the events that fell due,
 * so a ninety-second discussion is one call with a number in it.
 *
 * That split is the point of the driver being two files. [tickHouse] reads a real clock, sleeps and
 * calls three seams, and holds no deadline of its own — so there is nothing in it left to verify by
 * waiting, which is the only way this could otherwise have been tested.
 */
class HouseClockTest {

    private val seats = (0 until 5).map { Seat(it) }

    private fun armed(): GameState = home.someoneshome.core.reduce(
        GameState.EMPTY,
        Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1))),
    ).state

    private fun at(phase: MeetingPhase): GameState = armed().copy(
        meeting = Meeting.called(Seat(0), MeetingTrigger.MeetingCard).copy(phase = phase),
    )

    /** One second of real time, in nanoseconds — the unit every case below counts in. */
    private val second = 1_000_000_000L

    /**
     * Run [seconds] of real time past [clock] one second at a time, collecting what fell due.
     *
     * **A second at a time rather than in one lump**, and that is not decoration: [FixedTimestep]
     * bounds a burst at four steps and abandons a backlog past two seconds, so handing it ninety
     * seconds in one call would abandon most of them and the clock would arrive at the right tick
     * having skipped every boundary on the way. Which is correct behaviour and the wrong test — it
     * would pass while proving nothing about when a deadline is crossed.
     */
    private fun run(clock: HouseClock, seconds: Int, state: () -> GameState): Pair<HouseClock, List<Event>> {
        var held = clock
        val due = mutableListOf<Event>()
        repeat(seconds * Balance.TICKS_PER_SECOND.toInt()) {
            val turned = held.turned(second / Balance.TICKS_PER_SECOND, state())
            held = turned.next
            due += turned.due
        }
        return held to due
    }

    // ---- 1 · The five, each at its own moment ---------------------------------------------------

    /**
     * **The talk ends at ninety seconds and not before** (`gdd.md:412`, [Balance.DISCUSSION_SECONDS]).
     *
     * Both halves, because *fires at all* and *fires when it should* are different failures and the
     * first one hides the second: a driver that minted `DiscussionClosed` on every pump would pass
     * an assertion that only looked for one.
     */
    @Test
    fun `the talk closes at ninety seconds`() {
        val state = at(MeetingPhase.Discussion)
        val (part, early) = run(HouseClock.started(), Balance.DISCUSSION_SECONDS - 1) { state }
        assertEquals(emptyList(), early, "the talk ended before its window did")

        val (_, late) = run(part, 2) { state }
        assertEquals(1, late.count { it is Event.DiscussionClosed }, "the talk did not end, or ended twice")
    }

    /**
     * **The ballot closes on the HOUSE'S window, not on a constant** (D-117).
     *
     * The host cycles the vote window in lobby settings and it rides the arming event; a driver
     * holding its own forty-five would run a ballot the lobby had shortened. Checked at thirty,
     * which is a value the lobby offers and the design's default is not.
     */
    @Test
    fun `the ballot closes on the window the round was armed with`() {
        val state = at(MeetingPhase.Vote).copy(voteWindowSeconds = 30)
        val (part, early) = run(HouseClock.started(), 29) { state }
        assertEquals(emptyList(), early, "a thirty-second ballot closed early")

        val (_, late) = run(part, 2) { state }
        assertEquals(1, late.count { it is Event.VoteWindowClosed }, "the ballot never closed")

        // And the default window is genuinely longer, so the assertion above is about the round's
        // number rather than about a coincidence between two constants.
        assertTrue(Balance.VOTE_WINDOW_SECONDS > 30, "the fixture stopped differing from the default")
    }

    /**
     * **Two moments inside the tally, and the takeover is the first of them** (D-102, D-134's E1-1).
     *
     * *The takeover screen arrives at the halfway mark so the player does not walk away when the
     * countdown ends.* So a driver that only minted `MeetingClosed` would take a seat over at the
     * exact moment the lights went out, which is the thing D-102 moved it away from.
     *
     * **The halfway mark is derived from [Balance.TALLY_SECONDS]**, so this test says *half of the
     * window* rather than seven — a number typed here would go on passing after the window moved.
     */
    @Test
    fun `the takeover lands at halfway and the lights go out at zero`() {
        val restrained = Seat(3)
        var state = at(MeetingPhase.Tally).let {
            it.copy(meeting = it.meeting?.copy(restrainPending = restrained))
        }

        val half = Balance.TALLY_SECONDS / 2
        val (part, first) = run(HouseClock.started(), half + 1) { state }
        assertEquals(
            1, first.count { it is Event.TallyHalfwayReached },
            "the takeover did not land at the halfway mark, or landed on every pump after it",
        )
        assertTrue(first.none { it is Event.MeetingClosed }, "the lights went out at halfway")

        // **The state deliberately does not move here.** In a real round the minted event reaches
        // the rules and the phase changes underneath the driver, which would stop it re-firing all
        // by itself — and that is exactly the thing not to lean on: an event the gate refuses
        // changes nothing, and a driver relying on the rules to shut it up would mint one twenty
        // times a second into a refusal log nobody reads. The clock remembers what it has spent on
        // this phase, and holding the state still is what proves it.
        val (_, rest) = run(part, Balance.TALLY_SECONDS) { state }
        assertEquals(1, rest.count { it is Event.MeetingClosed }, "the meeting never ended")
        assertTrue(rest.none { it is Event.TallyHalfwayReached }, "the takeover fired twice")
    }

    /**
     * **The Egress expires on its own deadline, which is the only one of the five already on state.**
     *
     * [Egress.deadline] is an absolute tick the rules set when it was fired, so this needs no
     * bookkeeping at all — which is why it is the one transition that would have worked with a
     * driver that knew nothing about meetings.
     */
    @Test
    fun `the Egress expires at its deadline`() {
        val fired = Egress.fired(
            Tick(0), EgressType.Beacon, listOf(MarkerId("A"), MarkerId("B")), Balance.EGRESS_TIMER,
        )
        val state = armed().copy(egress = fired)

        val whole = (Balance.EGRESS_TIMER / Balance.TICKS_PER_SECOND).toInt()
        val (part, early) = run(HouseClock.started(), whole - 1) { state }
        assertEquals(emptyList(), early, "the countdown reached zero early")

        val (_, late) = run(part, 2) { state }
        assertTrue(late.any { it is Event.EgressExpired }, "the countdown never reached zero")
    }

    /**
     * **A PAUSED Egress does not expire, and this is the one the driver could most easily get
     * wrong** (D-133).
     *
     * A meeting holds the countdown, and *the deadline moves forward by the length of the meeting
     * and by nothing else* — so during a meeting the deadline is a tick in the past and a driver
     * comparing the clock against it would mint `EgressExpired` on every pump. The gate would then
     * refuse every one of them, silently and correctly, and the only trace would be a refusal log
     * nobody reads.
     */
    @Test
    fun `a paused Egress does not expire`() {
        val fired = Egress.fired(
            Tick(0), EgressType.Beacon, listOf(MarkerId("A"), MarkerId("B")), Balance.EGRESS_TIMER,
        )
        val state = armed().copy(egress = fired.pausedAt(Tick(10)))

        val whole = (Balance.EGRESS_TIMER / Balance.TICKS_PER_SECOND).toInt()
        val (_, due) = run(HouseClock.started(), whole + 5) { state }
        assertTrue(
            due.none { it is Event.EgressExpired },
            "an Egress the party had paused ran its clock out while everybody stood in one room",
        )
    }

    // ---- 2 · What the driver must NOT do --------------------------------------------------------

    /**
     * **The check-in gate has no timeout and must never grow one** (D-104).
     *
     * *Presence over proceed-without* was decided with the stall named: a player whose phone has
     * failed holds the gate until the room resolves it socially, and *a meeting that starts while
     * someone is still walking is a worse game than a meeting that waits.* A timeout here would
     * overturn that ruling quietly, in the dark, to a room still waiting on somebody on the stairs.
     *
     * Run for longer than any other window in the meeting, so *nothing fires* is a statement about
     * the phase rather than about not having waited long enough.
     */
    @Test
    fun `the check-in gate never times out`() {
        val state = at(MeetingPhase.CheckIn)
        val (_, due) = run(HouseClock.started(), Balance.DISCUSSION_SECONDS * 2) { state }
        assertEquals(emptyList(), due, "the check-in gate closed on a clock rather than on a count")
    }

    /** No meeting, no Egress: the clock runs and the house is owed nothing. */
    @Test
    fun `a quiet round produces nothing at all`() {
        val state = armed()
        val (clock, due) = run(HouseClock.started(), 60) { state }
        assertEquals(emptyList(), due, "the driver minted an event into a round with nothing running")
        assertEquals(
            Tick(60L * Balance.TICKS_PER_SECOND), clock.now,
            "sixty seconds of real time did not become sixty seconds of simulation time",
        )
    }

    /**
     * **A phase's clock starts when the driver first SEES it, not when the round began.**
     *
     * The moment a phase opened is not on [GameState] — see [HouseClock]'s note on why it is held
     * by the clock rather than added to the authority's own shape — so this is the property that
     * makes that safe: a discussion opening at T+600 gets its full ninety seconds, rather than
     * ending immediately because the round is already older than the window.
     */
    @Test
    fun `a phase gets its whole window however late in the round it opens`() {
        var state = armed()
        val (aged, _) = run(HouseClock.started(), 600) { state }
        assertTrue(aged.now.step > 600L, "the fixture did not age")

        state = at(MeetingPhase.Discussion)
        val (part, early) = run(aged, Balance.DISCUSSION_SECONDS - 1) { state }
        assertEquals(
            emptyList(), early,
            "the talk ended at once because the ROUND was older than the window, not the phase",
        )
        val (_, late) = run(part, 2) { state }
        assertEquals(1, late.count { it is Event.DiscussionClosed })
    }

    /**
     * **A tally with nobody to restrain takes nobody over, and the lights still go out.**
     *
     * `restrainPending` is null on a Skip, a tie, or a vote nobody won — which is a real outcome
     * and the commonest one D-075 argues for. A driver that minted `TallyHalfwayReached` anyway
     * would put an event through the gate for a takeover with no seat behind it.
     */
    @Test
    fun `a tally that restrained nobody produces no takeover`() {
        val state = at(MeetingPhase.Tally)
        assertNull(state.meeting?.restrainPending, "the fixture restrained somebody")

        val (_, due) = run(HouseClock.started(), Balance.TALLY_SECONDS + 1) { state }
        assertTrue(due.none { it is Event.TallyHalfwayReached }, "a takeover fired with nobody to take over")
        assertEquals(1, due.count { it is Event.MeetingClosed }, "the meeting never ended")
    }

    // ---- 3 · The clock itself -------------------------------------------------------------------

    /**
     * **A stall is abandoned rather than chased, and the count is a fact for the authority.**
     *
     * A phone suspended for ten minutes accumulates a backlog no burst limit can drain, and
     * simulation time **skips** rather than falling behind the wall clock — because a ninety-second
     * discussion has to be ninety seconds to a room standing in the dark. What that costs is that a
     * rule which would have fired inside the skipped window fires at the boundary after it, which
     * is why the skip is counted rather than clamped away.
     */
    @Test
    fun `a long stall skips simulation time and says so`() {
        val state = armed()
        val stalled = HouseClock.started().turned(60 * second, state)
        assertTrue(
            stalled.next.abandonedSteps > 0,
            "a minute-long stall was chased rather than abandoned",
        )
        assertTrue(
            stalled.next.now.step > 0,
            "simulation time fell behind the wall clock instead of skipping",
        )
    }

    /**
     * **The adopted rate and the architecture's suggested step are one number, and they are checked
     * rather than trusted to agree.**
     *
     * `FixedTimestep.of` takes no defaults *because a default rate would quietly become the decided
     * rate*, and the rate is decided — 20 Hz, confirmed by the owner on 2026-08-22 (E-L5-1), which
     * is what makes every duration in `Balance` mean what it says. This is the assertion that fails
     * if one of the two moves without the other.
     */
    @Test
    fun `the adopted tick rate is the architecture's suggested step`() {
        assertEquals(
            1_000_000_000L / Balance.TICKS_PER_SECOND,
            home.someoneshome.core.FixedTimestep.SUGGESTED_STEP_NANOS,
            "the tick rate and the step nanos came apart",
        )
        // And `started` refuses to build a clock when they have — the check is on the constructor
        // rather than only here, so a mismatch fails at the app root and not just in this file.
        HouseClock.started()
    }
}
