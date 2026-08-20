package home.someoneshome.core

import home.someoneshome.model.Tick

/**
 * The result of one pump. Nothing is discarded silently.
 *
 * [steps] is how many simulation steps the caller must now run. [abandoned] is how many step
 * boundaries were **skipped without ever being evaluated** — always zero in normal operation, and
 * a fact the authority has to hear about when it is not.
 */
data class Pumped(val next: FixedTimestep, val steps: Int, val abandoned: Long)

/**
 * **Story 0.2 — the fixed-timestep tick, decoupled from render.**
 *
 * Frame clocks are variable-rate and throttle thermally, so they cannot drive a replayable
 * simulation (game-architecture.md:241, :364). Simulation time advances in whole [stepNanos]
 * steps, and how long a frame took has no influence on how far the simulation moves.
 *
 * ### Immutable, like the rest of the authority
 *
 * `reduce` is a pure function of state; so is this. The caller holds the value and threads it,
 * which is what makes a tick sequence reproducible on a bench without a device attached.
 *
 * ### Integer arithmetic only
 *
 * No floats anywhere. A float accumulator loses low bits differently depending on the magnitude
 * of the values that passed through it, so two devices that received the same frame times in a
 * different order would drift apart — silently, and only over a long round.
 *
 * ### Two bounds, and they do different jobs
 *
 * [maxStepsPerPump] bounds the **burst**. After a stall, running the whole backlog in one pump
 * blows the frame budget and can start a spiral where catching up costs more than it recovers.
 * Steps over this limit are **not lost** — they stay in [carryNanos] and drain over later pumps.
 *
 * [maxDebtNanos] bounds the **backlog itself**. A phone suspended for ten minutes accumulates a
 * backlog that a burst limit alone can never drain. Steps beyond this are **abandoned**, and
 * abandoning is the only lossy thing this class does.
 *
 * ### Abandoned steps SKIP simulation time rather than falling behind it
 *
 * [now] advances by `steps + abandoned`. The alternative — advancing only by steps actually run —
 * keeps the invariant that every tick was evaluated, and was rejected: it makes simulation time
 * fall permanently behind the wall clock, so a 90-second discussion would run 90 seconds *plus
 * however long the host was suspended*, to a room full of people standing in the dark waiting for
 * it. Wall-clock fidelity is a promise to players; evaluating every step boundary is not.
 *
 * What that costs is real and bounded: a rule that would have fired inside the skipped window
 * fires at the boundary after it instead. Cooldowns and countdowns tolerate that. **A rule that
 * must observe every step boundary would not, which is why [abandonedSteps] is counted and not
 * merely clamped away.**
 *
 * ### The rate is not decided
 *
 * [stepNanos] has no default on purpose. Nothing in the GDD or the architecture fixes a tick rate,
 * and a default here would become that decision by accident. [SUGGESTED_STEP_NANOS] is a starting
 * point, labelled as one.
 *
 * ### This does not gate event admission
 *
 * Events are admitted when they arrive and stamped with the current [now]; they are not queued to
 * a step boundary. Deferring them would put up to one whole step between a phone touch and the
 * lamp going dark, and the lamp must die in the **same frame** as contact — that is the entire
 * mitigation for losing the anonymous revoke. Two events arriving inside one step share a [Tick]
 * value, and their order is carried by the recording's event list, not by the timestamp.
 */
class FixedTimestep private constructor(
    val stepNanos: Long,
    val maxStepsPerPump: Int,
    val maxDebtNanos: Long,
    /** Simulation time. Advances by whole steps, including abandoned ones. */
    val now: Tick,
    /** Real time owed but not yet converted into steps. Always less than one step after a pump. */
    val carryNanos: Long,
    /** Cumulative step boundaries never evaluated. Non-zero is a fact for the authority. */
    val abandonedSteps: Long,
    /** Cumulative pumps whose elapsed time was negative. A monotonic clock should never do this. */
    val backwardsReadings: Long,
) {

    /**
     * Advance by [elapsedNanos] of real time.
     *
     * A negative elapsed is treated as zero and counted. It should be impossible from a monotonic
     * source, which is exactly why it is worth counting rather than trusting: the symptom of a
     * wrong clock source is a simulation that runs backwards, and that is not a symptom anyone
     * would attribute to the clock.
     */
    fun pump(elapsedNanos: Long): Pumped {
        val backwards = if (elapsedNanos < 0) 1L else 0L
        val elapsed = if (elapsedNanos < 0) 0L else elapsedNanos

        var debt = carryNanos + elapsed

        // Abandon in WHOLE steps. Trimming to an exact nanosecond ceiling would leave a fractional
        // remainder that is neither a step nor carry, and the count would stop meaning "step
        // boundaries nobody evaluated".
        var abandoned = 0L
        if (debt > maxDebtNanos) {
            abandoned = (debt - maxDebtNanos) / stepNanos
            debt -= abandoned * stepNanos
        }

        val available = debt / stepNanos
        val steps = if (available > maxStepsPerPump) maxStepsPerPump else available.toInt()
        val carry = debt - steps.toLong() * stepNanos

        return Pumped(
            next = FixedTimestep(
                stepNanos = stepNanos,
                maxStepsPerPump = maxStepsPerPump,
                maxDebtNanos = maxDebtNanos,
                now = now + (steps.toLong() + abandoned),
                carryNanos = carry,
                abandonedSteps = abandonedSteps + abandoned,
                backwardsReadings = backwardsReadings + backwards,
            ),
            steps = steps,
            abandoned = abandoned,
        )
    }

    companion object {
        /**
         * A starting point, **not a decision**. 20 Hz.
         *
         * Nothing in the GDD or the architecture fixes a tick rate. 20 Hz is fine for the timers
         * this game actually has — a 90-second discussion, a 120-second Egress countdown, ability
         * cooldowns — and cheap enough not to compete with BLE and 100 Hz motion for the
         * allocation budget. It is named separately from [of] so that adopting it is a visible act.
         */
        const val SUGGESTED_STEP_NANOS: Long = 50_000_000L

        /** Burst limit. Four steps at 20 Hz is 200 ms of catch-up in one pump. */
        const val SUGGESTED_MAX_STEPS_PER_PUMP: Int = 4

        /** Backlog limit. Two seconds of debt; past that, a stall is abandoned rather than chased. */
        const val SUGGESTED_MAX_DEBT_NANOS: Long = 2_000_000_000L

        /**
         * Every bound is explicit at the call site. No parameter here has a default, because a
         * default rate would quietly become the decided rate.
         */
        fun of(
            stepNanos: Long,
            maxStepsPerPump: Int,
            maxDebtNanos: Long,
            startAt: Tick = Tick(0),
        ): FixedTimestep {
            require(stepNanos > 0) { "a step must have a positive duration, got $stepNanos ns" }
            require(maxStepsPerPump > 0) { "a pump that can run no steps never advances" }
            require(maxDebtNanos >= stepNanos) {
                "maxDebtNanos ($maxDebtNanos) below one step ($stepNanos) abandons every backlog " +
                    "before it can ever be run"
            }
            return FixedTimestep(
                stepNanos = stepNanos,
                maxStepsPerPump = maxStepsPerPump,
                maxDebtNanos = maxDebtNanos,
                now = startAt,
                carryNanos = 0L,
                abandonedSteps = 0L,
                backwardsReadings = 0L,
            )
        }
    }
}
