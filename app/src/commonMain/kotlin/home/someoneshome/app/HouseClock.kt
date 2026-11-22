package home.someoneshome.app

import home.someoneshome.core.FixedTimestep
import home.someoneshome.model.Balance
import home.someoneshome.model.Egress
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.Tick

/**
 * **The house's clock, and the five transitions that were waiting on one — E-P3-3.**
 *
 * The architecture puts the tick driver in the app roots *for the reason `core` cannot import
 * coroutines*, and it did not exist. So nothing minted [Event.DiscussionClosed],
 * [Event.VoteWindowClosed], [Event.TallyHalfwayReached], [Event.MeetingClosed] or
 * [Event.EgressExpired] when their moment arrived: **a meeting left alone stopped at whatever phase
 * it reached, and an Egress countdown never reached zero.** Every one of those five is the house's
 * own — minted above the rules, never requestable, and `factOf` explains at length why a phone may
 * not ask for one.
 *
 * ### It is split in two, and the pure half is the one that decides
 *
 * This class holds no coroutine, reads no wall clock and calls nothing. It takes **elapsed
 * nanoseconds** and a [GameState] and returns the events that have come due — which is what lets a
 * test drive a whole meeting on a clock it made up, with no delays and no flake, and what keeps
 * *when does the talk end* out of a `LaunchedEffect` where nobody would find it. [tickHouse] is the
 * other half: a loop that reads a real monotonic clock and pumps this.
 *
 * ### It decides nothing about the game and it must not
 *
 * Every event below fires because a **deadline the house already set** has passed. Nothing here
 * asks whether a meeting should end, who won a vote, or whether an Egress was contained; the rules
 * answer all three when the event reaches them, and the admission gate refuses any of these that
 * arrives out of place. A driver that checked before minting would be the rules living in the app
 * root in their most plausible costume — and it would check *differently*, which is worse than not
 * checking at all.
 *
 * ### Where each deadline comes from, and the one that had to be remembered
 *
 * Four of the five are read straight off state that already exists. [GameState.egress] carries its
 * own [Egress.deadline]; [GameState.voteWindowSeconds] is the ballot's length and
 * [GameState.discussionSeconds] the talk's (D-317), both locked at arming. The one that is not on
 * state is the tally's fifteen, [Balance.TALLY_SECONDS] — moved into `Balance` by this unit for
 * exactly the reason `Balance` exists, so the bar on five phones and the transition that ends it
 * count against one number.
 *
 * **What state does not carry at all is WHEN the current phase opened**, and that is what [since]
 * is. It is held here rather than added to [Meeting] deliberately: a field on the authority's own
 * shape would change what a recording contains, and the moment a phase opened is not a fact about
 * the round — it is a fact about the clock that is running it, which is this object. A driver
 * restarted mid-meeting starts the phase's clock again, which is the fail-closed direction: the
 * room waits longer, nobody is moved on early, and *the meeting ran long* is noticed by six people
 * standing in a room.
 */
class HouseClock private constructor(
    private val timestep: FixedTimestep,
    /**
     * The phase this clock last saw, when it saw it open, and what it has already minted for it.
     *
     * Null before there is a meeting. One value rather than three fields because they are one fact
     * — *this phase, since then, having done that* — and three fields would be three facts that
     * disagree the first time one is written without the others.
     */
    private val since: Running?,
) {

    /**
     * **A phase, its start, and what has already been minted into it.**
     *
     * [closed] is what stops the driver re-firing. Without it a transition fires on **every pump**
     * once its window has passed, and the reason that is not obvious is that in a real round it
     * self-corrects: the event reaches the rules, the phase moves on, and the next pump is looking
     * at a different window. **The driver would be relying on the rules to stop it**, which holds
     * right up until an event is refused — a `MeetingClosed` arriving at a meeting the gate has
     * already closed is refused `WrongMeetingPhase`, quietly and correctly, and the driver would go
     * on minting one twenty times a second into a refusal log nobody reads.
     *
     * Found by a test that ran a phase past its window on a state that did not move, which is the
     * shape of the failure rather than a contrivance: it is what happens whenever the rules decline
     * what the clock offers.
     *
     * [tookOver] is the same protection for the takeover, which is the one phase with two moments
     * in it — see [dueAt].
     */
    private data class Running(
        val phase: MeetingPhase,
        val from: Tick,
        val closed: Boolean = false,
        val tookOver: Boolean = false,
    )

    /** Simulation time, in ticks. Advances in whole steps, abandoned ones included. */
    val now: Tick get() = timestep.now

    /** Step boundaries never evaluated. Non-zero is a fact for the authority, not for a player. */
    val abandonedSteps: Long get() = timestep.abandonedSteps

    /** What one pump produced: the clock to hold next, and what the house owes the rules. */
    data class Turned(val next: HouseClock, val due: List<Event>)

    /**
     * **Advance by [elapsedNanos] of real time and say what fell due.**
     *
     * [state] is read and never written. The events come back in a fixed order — the meeting's
     * before the Egress's — because two events reduced at one tick are two events the recording
     * cannot order, and the recording is the only debugging instrument this game has.
     *
     * [offAir]: the house is off the air, or back on it with the gap not yet recorded. See the
     * Egress's line below.
     *
     * **At most one meeting transition per pump**, because each of them is what *opens* the phase
     * the next one ends: minting `DiscussionClosed` and `VoteWindowClosed` off one pump would end a
     * ballot that had not been open for a single tick. The Egress's is independent of all of them
     * and rides along in the same list.
     */
    fun turned(elapsedNanos: Long, state: GameState, offAir: Boolean = false): Turned {
        val pumped = timestep.pump(elapsedNanos)
        val at = pumped.next.now

        val phase = state.meeting?.phase
        // The phase's clock starts the first time this driver sees it, which is the pump after the
        // rules opened it. One step of lateness at 20 Hz is fifty milliseconds on a ninety-second
        // talk, and it is late rather than early — nobody is moved on before the house said so.
        val opened = when {
            phase == null -> null
            since?.phase != phase -> Running(phase, at)
            else -> since
        }

        val meetingDue = opened?.let { dueAt(state, it, at) }
        // **Never while the house is off the air, nor on the step it comes back** (the owner's
        // 2026-11-18 ruling, Q4 option A). The Egress is paused for the gap, and the pause is the
        // `OffAirEnded` the driver mints on the return; until the rules have reduced it, the
        // deadline on [state] is the one from before the gap, and an expiry judged against it
        // would hand the round to the Insiders for the host's lock. The next step reads the moved
        // deadline. [offAir] is true from the moment the house went off the air until that event
        // has been minted, so a step the driver ran during the gap is covered too.
        val egressDue = state.egress
            ?.takeIf { !offAir && it.pausedAt == null && at.step >= it.deadline.step }
            ?.let { Event.EgressExpired(at) }

        // **A dependency's release, once its delay has run out** (D-146, `gdd.md:284`, D-322). The
        // delay started when the upstream entry was completed and is on the state ([ChainLink.dueAt]);
        // this reads it, as the Egress's line reads its deadline, and decides nothing. In the links'
        // order, one per link that is due, after the meeting's and the Egress's.
        //
        // ⚠️ **Held while a meeting runs and while the house is off the air** (D-322, provisional):
        // a release during a meeting would put `you're up` on a phone standing in the meeting, and
        // nothing ruled whether it should. It lands on the first step after the lights go out.
        // A round that has ended releases nothing: the gate would refuse it every pump.
        val chainsDue = if (offAir || state.meeting != null || state.ended) emptyList() else chainsDueAt(state, at)

        return Turned(
            next = HouseClock(pumped.next, opened?.spent(meetingDue)),
            due = listOfNotNull(meetingDue, egressDue) + chainsDue,
        )
    }

    /**
     * Every link whose downstream line is still held and whose delay has run out at [at]. Read off
     * the state alone, so a link the rules have released is never minted again.
     */
    private fun chainsDueAt(state: GameState, at: Tick): List<Event> = state.links.mapNotNull { link ->
        val held = state.workOrderFor(link.down)?.entries?.getOrNull(link.downEntry)?.held == true
        val due = link.dueAt?.let { at.step >= it.step } == true
        if (held && due) Event.ChainUnlocked(at, link.down, link.downEntry) else null
    }

    /**
     * This phase, with whatever [minted] was recorded against it.
     *
     * Written here rather than at each `return` inside [dueAt] so that *what was minted* and *what
     * was remembered* cannot come apart: they are read off the same value, once.
     */
    private fun Running.spent(minted: Event?): Running = when (minted) {
        is Event.TallyHalfwayReached -> copy(tookOver = true)
        null -> this
        else -> copy(closed = true)
    }

    /**
     * The meeting transition this phase owes at [at], if its window has run out.
     *
     * **[MeetingPhase.CheckIn] has no row and must never get one** (D-104). The gate closes when
     * every living player and every out player has walked in — *presence over proceed-without*, and
     * the stall was named when it was decided. A timeout here would be this driver quietly
     * overturning that ruling, and it would do it in the dark to a room still waiting for somebody
     * on the stairs.
     */
    private fun dueAt(state: GameState, running: Running, at: Tick): Event? {
        if (running.closed) return null
        val ran = at.step - running.from.step
        return when (running.phase) {
            MeetingPhase.CheckIn -> null

            // The host's DISCUSSION row, locked at arming and carried on the state (D-317) — the
            // ballot's arrangement below, one phase earlier, and never a constant here.
            MeetingPhase.Discussion ->
                Event.DiscussionClosed(at).takeIf { ran >= seconds(state.discussionSeconds) }

            // The host's setting, locked at arming and carried on the state — never a constant
            // here. A driver with its own forty-five would run a ballot the lobby had shortened.
            MeetingPhase.Vote ->
                Event.VoteWindowClosed(at).takeIf { ran >= seconds(state.voteWindowSeconds) }

            // **Two moments in one phase, and the halfway mark is derived rather than typed**
            // (D-102, D-134's E1-1). The takeover reaches the losing seat at halfway so the player
            // does not walk away when the countdown ends; everybody else goes back to the round at
            // zero. Ordered so that a phase which somehow reached both in one pump ends the meeting
            // rather than taking somebody over after the lights went out.
            MeetingPhase.Tally -> {
                val whole = seconds(Balance.TALLY_SECONDS)
                when {
                    ran >= whole -> Event.MeetingClosed(at)
                    // **Once, on the pump that crosses it.** A takeover pushed every pump for seven
                    // seconds is seven long haptics on the one phone in the house that has just
                    // been told it is out — D-135 reserves the long buzz for five things, and this
                    // would make one of them a stutter.
                    //
                    // **And nobody to restrain is a real outcome, not a missing one**: null on a
                    // Skip, a tie, or a vote nobody won, which is the commonest result D-075 argues
                    // for. The lights still go out at zero.
                    ran >= whole / 2 && !running.tookOver &&
                        state.meeting?.restrainPending != null -> Event.TallyHalfwayReached(at)
                    else -> null
                }
            }
        }
    }

    private fun seconds(count: Int): Long = count * Balance.TICKS_PER_SECOND

    companion object {

        /**
         * **A clock at the architecture's suggested rate, adopted** — 20 Hz, and it is
         * [Balance.TICKS_PER_SECOND]'s twenty rather than a second copy of it.
         *
         * `FixedTimestep.of` takes no defaults on purpose, *because a default rate would quietly
         * become the decided rate*. It is decided: the owner confirmed 20 Hz on 2026-08-22
         * (E-L5-1), which is why `Balance` expresses every duration through it. So the step is
         * derived from the adopted rate rather than read from `SUGGESTED_STEP_NANOS`, and the two
         * are checked against each other rather than trusted to agree.
         *
         * The two bounds are the architecture's suggestions taken as they stand: four steps of
         * catch-up in one pump, and two seconds of backlog before a stall is abandoned rather than
         * chased. Neither is a balance value and neither stamps into a recording.
         */
        fun started(at: Tick = Tick(0)): HouseClock {
            val step = NANOS_PER_SECOND / Balance.TICKS_PER_SECOND
            check(step == FixedTimestep.SUGGESTED_STEP_NANOS) {
                "the adopted tick rate (${Balance.TICKS_PER_SECOND} Hz) and the architecture's " +
                    "suggested step ($step ns vs ${FixedTimestep.SUGGESTED_STEP_NANOS} ns) have " +
                    "come apart; one of them moved without the other"
            }
            return HouseClock(
                FixedTimestep.of(
                    stepNanos = step,
                    maxStepsPerPump = FixedTimestep.SUGGESTED_MAX_STEPS_PER_PUMP,
                    maxDebtNanos = FixedTimestep.SUGGESTED_MAX_DEBT_NANOS,
                    startAt = at,
                ),
                since = null,
            )
        }

        private const val NANOS_PER_SECOND: Long = 1_000_000_000L
    }
}
