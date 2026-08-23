package home.someoneshome.app

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Tick
import home.someoneshome.platform.monotonicNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

/**
 * **The handle [HouseClock] turns** — the authority, seen by the driver and by nothing else.
 *
 * Three functions rather than the authority itself, and it is [RoundAuthority]'s own reasoning
 * about `inputs` and `emit` applied one layer up: a driver that took the authority could only be
 * checked by playing an evening, and the thing under test here is *when does it mint*, which wants
 * a fixture on both sides. It also keeps `commonMain` clear of `cheats` — the authority is a bench
 * and lives there; the clock that drives it is production machinery and lives here.
 */
interface HouseHandle {

    /** The round as it stands. Read at the top of every pump and never written. */
    fun state(): GameState

    /**
     * **Move the house's clock to [at], and never backwards.**
     *
     * The driver owns simulation time and this is how the authority learns it. Without it the
     * authority would stamp requests off its own event counter while the driver stamped
     * transitions off the simulation clock — two clocks, and the recording could not order them.
     */
    fun advanceTo(at: Tick)

    /** One event the house minted itself, put to the same gate every request goes through. */
    suspend fun mint(event: Event)
}

/**
 * **The tick driver — E-P3-3 closed, and it is a coroutine turning a handle that already existed.**
 *
 * `Balance.TICKS_PER_SECOND` named the rate, `FixedTimestep.pump` held the semantics, and
 * [HouseClock] holds the deadlines. This is the loop: read a monotonic clock, hand the elapsed time
 * to the clock, put whatever fell due to the house. **It runs on the host and nowhere else** — the
 * five events it mints are the authority's own, and a client that ran one would be a phone deciding
 * when the talk was over.
 *
 * ### Everything that decides is in [HouseClock], and that is the point
 *
 * This function reads a wall clock, sleeps, and calls three seams. It holds no deadline, no
 * duration and no branch on the round. Which means the whole of *when does a meeting end* is
 * testable with no coroutine, no delay and no flake — a test hands [HouseClock.turned] the elapsed
 * nanoseconds it likes and reads the events back. **Nothing about the five transitions is verified
 * by waiting**, which is what test-clock discipline buys and why the split is worth two files.
 *
 * ### It sleeps a step and pumps whatever really elapsed
 *
 * `delay` is a floor and not a promise: a thermally-throttled phone, a socket burst or a GC pause
 * make a step take longer than it was asked to. So the loop never assumes it slept the amount it
 * asked for — it measures, and [FixedTimestep] converts real time into whole steps, carries the
 * remainder, bounds the burst and abandons a backlog too large to chase. That is the whole reason
 * the timestep type exists and why this loop is allowed to be four lines.
 *
 * ### Abandoned steps are reported and never swallowed
 *
 * A phone suspended long enough to abandon step boundaries is a host that lost time, and simulation
 * time skips rather than falling behind the wall clock — a ninety-second discussion has to be
 * ninety seconds to a room standing in the dark. What it costs is that a rule which would have
 * fired inside the skipped window fires at the boundary after it. Cooldowns and countdowns tolerate
 * that; [note] is how the authority hears about it anyway, because the symptom of the one rule that
 * would *not* tolerate it is invisible (rule 6).
 */
suspend fun tickHouse(
    house: HouseHandle,
    note: (String) -> Unit = {},
    /**
     * The clock to start from. A parameter so a test can start one anywhere, and so the driver and
     * the round can be started at the same tick rather than at two.
     */
    start: HouseClock = HouseClock.started(),
    /** Monotonic nanoseconds. A parameter for the reason [start] is one — see [tickHouse]'s note. */
    clockNanos: () -> Long = ::monotonicNanos,
) {
    var clock = start
    var last = clockNanos()
    var reported = 0L

    while (currentCoroutineContext().isActive) {
        delay(STEP_MILLIS)

        val now = clockNanos()
        val elapsed = now - last
        last = now

        val turned = clock.turned(elapsed, house.state())
        clock = turned.next

        // The clock moves whether or not anything fell due. A house whose tick only advanced when
        // it minted something would stamp every request with the moment of the last transition.
        house.advanceTo(clock.now)
        for (event in turned.due) house.mint(event)

        if (clock.abandonedSteps > reported) {
            note("THE HOUSE CLOCK SKIPPED ${clock.abandonedSteps - reported} STEP(S)")
            reported = clock.abandonedSteps
        }
    }
}

/**
 * How long the loop asks to sleep between pumps: one step at the adopted rate, 50 ms at 20 Hz.
 *
 * **It is a request and not the step**, which is the distinction the loop is built around: what
 * advances the simulation is the time that really passed, measured after the sleep returns. Asking
 * for a step is simply the cheapest cadence that cannot fall behind — asking for less would burn
 * the allocation budget on pumps that produce nothing, and asking for more would put the house's
 * answer to a countdown further from the moment it came due than a player standing over it would
 * accept.
 */
private const val STEP_MILLIS: Long = 1_000L / 20L
