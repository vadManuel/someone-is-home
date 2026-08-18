package home.someoneshome.model

/**
 * Simulation time, as an integer count of fixed steps.
 *
 * Not a `Duration`, not an `Instant`, not a wall clock. `core` sees no datetime type at all —
 * the boundary check fails the build if one reaches it — because a clock read inside the rules
 * is an input nobody recorded, and a round that reads the clock cannot replay.
 *
 * A real timestamp is an *input*: it arrives on an [Event], having been sampled at the edge and
 * written into the recording.
 */
value class Tick(val step: Long) : Comparable<Tick> {
    override fun compareTo(other: Tick): Int = step.compareTo(other.step)
    operator fun plus(steps: Long): Tick = Tick(step + steps)
}
