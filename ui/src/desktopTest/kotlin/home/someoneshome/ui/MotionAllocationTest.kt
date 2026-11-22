package home.someoneshome.ui

import home.someoneshome.model.protocol.RoundMessage
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **GUARD (rule 7, D-321): a motion sample allocates nothing.**
 *
 * The motion budget takes a hundred samples a second for every second anybody stands at a card or
 * at the station, on the main thread, and rule 7 names the motion thread as one of the places the
 * whole-app allocation budget is spent. So the sample path is held to **zero bytes**: the
 * accumulator alone, and the panel's sink the sensor calls ([FlowModel.motionSampled]), still and
 * moving. Measured on the JVM by the thread's own allocation counter, as `RangingAllocationTest`
 * measures `core`'s path. What a sample costs on a phone above this (the sensor's C shim) is the
 * shim's own argument: it hands three doubles and no object.
 *
 * **Each run is 100 000 samples, measured five times; one must be zero and none may reach 4 KB.**
 * While a loop is interpreted and then compiled on the stack, the JVM's own bookkeeping for that
 * lands on this thread, a few hundred bytes now and then: it is the JVM's and not the sample's. A
 * real per-sample allocation is at least sixteen bytes a sample, 1.6 MB a run, in every run, so it
 * cannot hide under either bound.
 *
 * The one write a sample can make is the bar's step ([MotionBar.STEPS] an opening at most), so the
 * moving run through the panel is measured over samples that change no step.
 */
class MotionAllocationTest {

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun allocated(): Long = threads.getThreadAllocatedBytes(Thread.currentThread().id)

    /** Bytes allocated by each of five runs of [block]. */
    private inline fun runs(block: () -> Unit): List<Long> = List(5) {
        val before = allocated()
        block()
        allocated() - before
    }

    private fun assertNothingPerSample(what: String, runs: List<Long>) {
        assertTrue(runs.max() < 4_096, "$what: a run allocated ${runs.max()} bytes, a sample's worth (rule 7): $runs")
        assertEquals(0L, runs.min(), "$what allocated in every run (rule 7): $runs")
    }

    @Test
    fun `the accumulator allocates nothing per sample, still or moving`() {
        val budget = MotionBudget()
        budget.begin(1e9)
        repeat(MotionBudget.CALIBRATION_SAMPLES) { budget.sample(0.004, 0.006, 0.003) }
        assertNothingPerSample("a still sample", runs { repeat(100_000) { budget.sample(0.004, 0.006, 0.003) } })
        assertNothingPerSample("a moving sample", runs { repeat(100_000) { budget.sample(0.12, 0.15, 0.05) } })
        assertTrue(budget.left < 1.0, "the moving run spent nothing, so it measured nothing")
    }

    @Test
    fun `the panel's sink allocates nothing per sample`() {
        val model = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true), send = {})
        model.houseSaid(RoundMessage.TerminalAnswered(reading = true))
        assertEquals(ScreenId.TermLive, model.state.screen)

        // Still: nothing spent, no step moves.
        model.motion.begin(1e9)
        repeat(MotionBudget.CALIBRATION_SAMPLES) { model.motionSampled(0.004, 0.006, 0.003) }
        assertNothingPerSample("a still sample through the panel", runs { repeat(100_000) { model.motionSampled(0.004, 0.006, 0.003) } })

        // Moving, on a budget large enough that 500 000 samples move the bar by less than a step.
        model.motion.begin(1e6)
        repeat(MotionBudget.CALIBRATION_SAMPLES) { model.motionSampled(0.004, 0.006, 0.003) }
        val shown = model.motionBar.left.floatValue
        val moving = runs { repeat(100_000) { model.motionSampled(0.12, 0.15, 0.05) } }
        assertEquals(shown, model.motionBar.left.floatValue, "the bar stepped, so this measured a write")
        assertTrue(model.motion.left < 1.0, "the moving run spent nothing, so it measured nothing")
        assertNothingPerSample("a moving sample through the panel", moving)
        assertEquals(ScreenId.TermLive, model.state.screen)
    }

    /**
     * **And a sample asked of this phone's own motor allocates nothing either** (D-327): the motor's
     * clock is the standard library's, read on every sample while something has played; a repeating
     * script (the ring) keeps it playing for the whole measurement, and one played once and over
     * keeps the clock read on every sample after it.
     */
    @Test
    fun `a sample while this phone's own motor plays allocates nothing`() {
        val motor = OwnMotor()
        val budget = MotionBudget(motor)
        budget.begin(1e9)
        repeat(MotionBudget.CALIBRATION_SAMPLES) { budget.sample(0.004, 0.006, 0.003) }
        motor.played(home.someoneshome.model.HouseBuzz.RING, repeating = true)
        assertTrue(motor.running(), "the arrangement is wrong: the motor is not playing")
        assertNothingPerSample("a sample under the ring", runs { repeat(100_000) { budget.sample(0.12, 0.15, 0.05) } })
        assertEquals(1.0, budget.left, "the ring spent the budget, so this measured the wrong path")
        motor.played(listOf(home.someoneshome.model.HapticStep.Buzz(1)))
        Thread.sleep(OwnMotor.TAIL_MILLIS + 20)
        assertTrue(!motor.running(), "the arrangement is wrong: the buzz is still playing")
        assertNothingPerSample("a sample after a buzz", runs { repeat(100_000) { budget.sample(0.12, 0.15, 0.05) } })
        assertTrue(budget.left < 1.0, "the moving run spent nothing, so it measured nothing")
    }
}
