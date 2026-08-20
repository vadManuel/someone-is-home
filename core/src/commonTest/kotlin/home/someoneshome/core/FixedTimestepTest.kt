package home.someoneshome.core

import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Story 0.2. The tick is what makes a round reproducible; a frame clock is not. */
class FixedTimestepTest {

    private val STEP = 50_000_000L // 20 Hz
    private fun fresh() = FixedTimestep.of(
        stepNanos = STEP, maxStepsPerPump = 4, maxDebtNanos = 2_000_000_000L,
    )

    /** Runs a whole sequence of frame times and returns the end state plus every step count. */
    private fun drive(start: FixedTimestep, frames: List<Long>): Pair<FixedTimestep, List<Int>> {
        var t = start
        val counts = mutableListOf<Int>()
        for (f in frames) {
            val p = t.pump(f)
            t = p.next
            counts += p.steps
        }
        return t to counts
    }

    @Test
    fun `an exact step produces exactly one step and no carry`() {
        val p = fresh().pump(STEP)
        assertEquals(1, p.steps)
        assertEquals(0L, p.next.carryNanos)
        assertEquals(Tick(1), p.next.now)
    }

    @Test
    fun `time shorter than a step produces no step and is not lost`() {
        val p = fresh().pump(STEP - 1)
        assertEquals(0, p.steps)
        assertEquals(STEP - 1, p.next.carryNanos)
        assertEquals(Tick(0), p.next.now)
        assertEquals(1, p.next.pump(1).steps, "the remainder must complete the step")
    }

    /**
     * **The property the whole story exists for.**
     *
     * Wildly irregular frame times — a thermally throttling phone — must produce exactly the same
     * simulation time as perfectly even ones, given the same total elapsed. If this fails, the
     * simulation is a function of frame pacing and no round can replay.
     */
    @Test
    fun `irregular frames reach the same tick as even ones`() {
        val total = STEP * 600
        val even = List(600) { STEP }
        val jittery = buildList {
            var spent = 0L
            var i = 1L
            // Deliberately lumpy: alternates far below and far above one step.
            while (spent < total) {
                val f = minOf(if (i % 3 == 0L) STEP * 3 + 7 else STEP / 4 + i % 11, total - spent)
                add(f); spent += f; i++
            }
        }
        assertEquals(total, jittery.sum(), "the fixture must spend the same real time")

        val (a, countsA) = drive(fresh(), even)
        val (b, countsB) = drive(fresh(), jittery)
        assertEquals(a.now, b.now, "frame pacing changed simulation time")
        assertEquals(600, countsA.sum())
        assertEquals(countsA.sum(), countsB.sum())
        assertEquals(0L, a.abandonedSteps + b.abandonedSteps)
    }

    /** No float anywhere: a long run must land on an exact tick, not near one. */
    @Test
    fun `a long run accumulates no drift`() {
        val (end, counts) = drive(fresh(), List(20 * 60 * 25) { STEP }) // 25 minutes at 20 Hz
        assertEquals(Tick(30_000), end.now)
        assertEquals(30_000, counts.sum())
        assertEquals(0L, end.carryNanos)
        assertEquals(0L, end.abandonedSteps)
    }

    /** The burst bound defers work; it never drops it. */
    @Test
    fun `a burst is capped per pump and the remainder drains later`() {
        val p = fresh().pump(STEP * 10)
        assertEquals(4, p.steps, "burst not capped")
        assertEquals(0L, p.abandoned, "a burst inside the debt bound must not abandon anything")

        var t = p.next
        var ran = p.steps
        repeat(10) { ran += t.pump(0).let { r -> t = r.next; r.steps } }
        assertEquals(10, ran, "the deferred steps never drained")
        assertEquals(Tick(10), t.now)
    }

    /**
     * The backlog bound is the only lossy thing here, and it counts what it loses.
     *
     * A ten-minute suspension is a backlog no burst limit can drain.
     */
    @Test
    fun `a backlog beyond the debt bound is abandoned and counted`() {
        val p = fresh().pump(600_000_000_000L) // ten minutes
        assertTrue(p.abandoned > 0, "an unbounded backlog was accepted")
        assertEquals(p.abandoned, p.next.abandonedSteps)

        // 10 minutes = 12 000 steps; 2 s of debt = 40 are kept, the rest abandoned.
        assertEquals(12_000L - 40L, p.abandoned)
    }

    /**
     * Abandoned steps advance simulation time rather than falling behind it, so a stall costs
     * evaluated boundaries and not wall-clock fidelity.
     */
    @Test
    fun `abandoned steps still advance simulation time`() {
        val p = fresh().pump(600_000_000_000L)
        assertEquals(p.abandoned + p.steps, p.next.now.step, "simulation time fell behind the wall clock")
    }

    /** A monotonic source should never do this, which is why it is counted rather than trusted. */
    @Test
    fun `a backwards clock reading advances nothing and is counted`() {
        val p = fresh().pump(-STEP * 5)
        assertEquals(0, p.steps)
        assertEquals(Tick(0), p.next.now)
        assertEquals(0L, p.next.carryNanos)
        assertEquals(1L, p.next.backwardsReadings)
    }

    /** Pumping is a pure function of the value: the same input twice gives the same answer. */
    @Test
    fun `pumping is pure`() {
        val t = fresh().pump(STEP * 7).next
        val a = t.pump(STEP * 3)
        val b = t.pump(STEP * 3)
        assertEquals(a.steps, b.steps)
        assertEquals(a.next.now, b.next.now)
        assertEquals(a.next.carryNanos, b.next.carryNanos)
    }

    /** Bounds that cannot work are rejected at construction, not discovered at 3am in a dark house. */
    @Test
    fun `unworkable bounds are refused`() {
        assertFailsWith<IllegalArgumentException> { FixedTimestep.of(0L, 4, 1_000L) }
        assertFailsWith<IllegalArgumentException> { FixedTimestep.of(STEP, 0, STEP) }
        assertFailsWith<IllegalArgumentException> { FixedTimestep.of(STEP, 4, STEP - 1) }
    }

    /** The suggested values are a coherent set, whatever else they are. */
    @Test
    fun `the suggested starting point is self-consistent`() {
        val t = FixedTimestep.of(
            FixedTimestep.SUGGESTED_STEP_NANOS,
            FixedTimestep.SUGGESTED_MAX_STEPS_PER_PUMP,
            FixedTimestep.SUGGESTED_MAX_DEBT_NANOS,
        )
        assertEquals(20L, 1_000_000_000L / t.stepNanos, "SUGGESTED_STEP_NANOS is not 20 Hz")
        assertEquals(1, t.pump(t.stepNanos).steps)
    }
}
