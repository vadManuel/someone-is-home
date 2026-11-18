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

    /**
     * **Whether the house is off the air, or back on it with the gap not yet recorded** (the
     * owner's 2026-11-18 ruling, Q4 option A). See [OffAirMark]. False on a handle that never goes
     * off the air, which is every bench that does not ask.
     */
    fun offAir(): Boolean = false

    /**
     * **The house is back: the gap, as the event that records it, once**, stamped [at] — or null
     * while it is still off the air or was never off it. See [OffAirMark.ended].
     */
    fun backOnAir(at: Tick): Event.OffAirEnded? = null
}

/**
 * **When the house went off the air, and whether it is back** (the owner's 2026-11-18 ruling, Q4
 * option A; D-225, D-230).
 *
 * The host's lock is marked here **synchronously, at the moment it happens**, rather than noticed by
 * the driver: iOS suspends the app with the driver mid-sleep, so the first pump after the return is
 * the first the driver runs at all, and by then the clock has skipped the whole gap. [went] reads
 * the house's tick as it stood; [ended] closes the mark on the first pump after [returned] and hands
 * back the one event that records the gap. The house's ticks on both ends, so a replay moves the
 * Egress by exactly what this recorded, and no clock is read here.
 */
class OffAirMark {
    private var since: Tick? = null
    private var back: Boolean = false

    /** Off the air from [at]. A second call while off changes nothing: the gap began at the first. */
    fun went(at: Tick) {
        if (since == null) {
            since = at
            back = false
        }
    }

    /** Back on the air. The gap is recorded on the driver's next pump. */
    fun returned() {
        if (since != null) back = true
    }

    /** Off the air, or back with the gap not yet recorded. */
    val off: Boolean get() = since != null

    /** The gap, once, when back: [Event.OffAirEnded] from the tick it went off to [at]. */
    fun ended(at: Tick): Event.OffAirEnded? {
        val from = since ?: return null
        if (!back) return null
        since = null
        back = false
        return Event.OffAirEnded(at, from)
    }
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

        clock = pumped(house, clock, elapsed)

        if (clock.abandonedSteps > reported) {
            note("THE HOUSE CLOCK SKIPPED ${clock.abandonedSteps - reported} STEP(S)")
            reported = clock.abandonedSteps
        }
    }
}

/**
 * **One pump of [tickHouse]**: [elapsedNanos] of real time handed to [clock], the house moved to the
 * new tick, and whatever fell due minted, in a fixed order. Its own function so a test can drive the
 * order with a clock it made up, as `HouseClockTest` drives [HouseClock].
 *
 * **The house's return is minted first** (the owner's 2026-11-18 ruling, Q4 option A): an
 * [Event.OffAirEnded] reaches the rules before anything this pump owes, so the Egress it pauses is
 * paused before the clock is asked about it again. [HouseClock.turned] is told the house is off the
 * air for this pump too, and owes no expiry; the next pump judges the moved deadline.
 */
internal suspend fun pumped(house: HouseHandle, clock: HouseClock, elapsedNanos: Long): HouseClock {
    val turned = clock.turned(elapsedNanos, house.state(), offAir = house.offAir())
    val next = turned.next

    // The clock moves whether or not anything fell due. A house whose tick only advanced when
    // it minted something would stamp every request with the moment of the last transition.
    house.advanceTo(next.now)
    house.backOnAir(next.now)?.let { house.mint(it) }
    for (event in turned.due) house.mint(event)
    return next
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
