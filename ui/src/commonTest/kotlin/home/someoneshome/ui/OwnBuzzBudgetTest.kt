package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **This phone's own buzz does not spend its motion budget** (D-327, provisional; the overnight
 * review of 2026-11-22).
 *
 * The Taptic Engine shakes the phone it is in and `userAcceleration` reads it, under a limit as low
 * as 0.03 g, so a player standing still while Handshake, Sniff, Drift or a verdict buzzed could have
 * their work closed by their own phone. While the motor plays, and for [OwnMotor.TAIL_MILLIS] after,
 * a sample is nothing. The clock here is the test's, moved by hand; the samples are a walk, which
 * is what the buzz looks like to Core Motion at its worst.
 */
class OwnBuzzBudgetTest {

    private var now = 0L
    private val clock = MotorClock { now }

    /** A budget of [allowance], calibrated on a still hand, with this phone's own motor behind it. */
    private fun calibrated(motor: OwnMotor, allowance: Double = MotionAllowance.SHORT): MotionBudget =
        MotionBudget(motor).also { budget ->
            budget.begin(allowance)
            repeat(MotionBudget.CALIBRATION_SAMPLES) { budget.sample(0.004, 0.006, 0.003) }
        }

    private fun MotionBudget.walk(samples: Int) = repeat(samples) { sample(0.12, 0.15, 0.05) }

    @Test
    fun `samples during a buzz do not drain the budget and samples after the tail do`() {
        val motor = OwnMotor(clock)
        val budget = calibrated(motor)
        motor.played(listOf(HapticStep.Buzz(300)))
        for (at in 0L until 300L + OwnMotor.TAIL_MILLIS step 10) {
            now = at
            budget.walk(1)
        }
        assertEquals(1.0, budget.left, "this phone's own buzz spent its budget")
        now = 300L + OwnMotor.TAIL_MILLIS
        budget.walk(5)
        assertTrue(budget.left < 1.0, "a walk after the buzz's tail spent nothing")
    }

    @Test
    fun `a rest longer than the tail is measured`() {
        val motor = OwnMotor(clock)
        val budget = calibrated(motor)
        motor.played(listOf(HapticStep.Buzz(100), HapticStep.Rest(1_000), HapticStep.Buzz(100)))
        now = 100L + OwnMotor.TAIL_MILLIS + 50
        budget.walk(5)
        assertTrue(budget.left < 1.0, "the gap between two buzzes was taken for the motor")
        val after = budget.left
        now = 1_150L
        budget.walk(5)
        assertEquals(after, budget.left, "the second buzz spent the budget")
    }

    @Test
    fun `a buzz stopped early keeps its tail and no more`() {
        val motor = OwnMotor(clock)
        val budget = calibrated(motor)
        motor.played(listOf(HapticStep.Buzz(2_000)))
        now = 100
        motor.stopped()
        now = 100 + OwnMotor.TAIL_MILLIS - 1
        budget.walk(5)
        assertEquals(1.0, budget.left, "the tail of the stopped buzz was measured")
        now = 100 + OwnMotor.TAIL_MILLIS
        budget.walk(5)
        assertTrue(budget.left < 1.0, "a buzz stopped at 100 ms was still taken for the motor at 2 s")
    }

    @Test
    fun `the ring plays until it is stopped`() {
        val motor = OwnMotor(clock)
        val budget = calibrated(motor)
        motor.played(HouseBuzz.RING, repeating = true)
        now = 60_000
        assertTrue(motor.running(), "the ring was taken as over")
        // The ring's long rest is not the motor, even on its tenth time round.
        val length = HouseBuzz.RING.sumOf { (it as? HapticStep.Buzz)?.millis ?: (it as HapticStep.Rest).millis }.toLong()
        now = length * 10 + length - 200
        budget.walk(5)
        assertTrue(budget.left < 1.0, "the ring's rest was taken for the motor")
        motor.stopped()
        now += 10_000
        assertTrue(!motor.running(), "the ring was still taken as playing after its stop")
    }

    @Test
    fun `a buzz during the calibration is not calibrated on`() {
        val motor = OwnMotor(clock)
        val buzzed = MotionBudget(motor)
        val control = MotionBudget()
        buzzed.begin(MotionAllowance.SHORT)
        control.begin(MotionAllowance.SHORT)
        motor.played(listOf(HapticStep.Buzz(500)))
        // The buzz, as Core Motion reads it: a walk's worth, for the whole of what would be the calibration.
        repeat(MotionBudget.CALIBRATION_SAMPLES * 2) { buzzed.sample(0.12, 0.15, 0.05) }
        now = 500L + OwnMotor.TAIL_MILLIS
        repeat(MotionBudget.CALIBRATION_SAMPLES) {
            buzzed.sample(0.004, 0.006, 0.003)
            control.sample(0.004, 0.006, 0.003)
        }
        var buzzedSamples = 0
        while (!buzzed.gone && buzzedSamples < 10_000) { buzzed.walk(1); buzzedSamples++ }
        var controlSamples = 0
        while (!control.gone && controlSamples < 10_000) { control.walk(1); controlSamples++ }
        assertEquals(controlSamples, buzzedSamples, "a buzz during the first half second loosened the limit")
    }

    // ---- Through the panel, for both roles ---------------------------------------------------------

    private val study = MarkerId("card-study")

    /** A housed phone of [role] on Handshake, its own clock behind its motor. */
    private fun atHandshake(role: PanelRole): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            subroutines = SubroutineModel(send = {}),
            motor = OwnMotor(clock),
        )
        val kind = SubroutineKind.Handshake
        model.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(OrderLine.Known(0, AssignedWork.Subroutine(kind), false, listOf(Destination(study, shape = "diamond", room = "STUDY")))),
            ),
        )
        model.scanning(study)
        model.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(0, kind, SubroutineModel.benchParameters(kind))))
        model.actions().beginSubroutine()
        assertEquals(ScreenId.SubHandshake, model.state.screen, "$role: the arrangement is wrong")
        return model
    }

    @Test
    fun `both roles keep their work through their own buzz and lose it to the same walk after`() {
        val counts = PanelRole.entries.map { role ->
            now = 0
            val model = atHandshake(role)
            repeat(MotionBudget.CALIBRATION_SAMPLES) { model.motionSampled(0.004, 0.006, 0.003) }
            // The question, buzzed as the app root buzzes it, and a verdict's Long after it.
            model.motor.played(listOf(HapticStep.Buzz(200), HapticStep.Rest(100), HapticStep.Buzz(200)))
            for (at in 0L until 500L + OwnMotor.TAIL_MILLIS step 10) {
                now = at
                model.motionSampled(0.12, 0.15, 0.05)
            }
            assertEquals(ScreenId.SubHandshake, model.state.screen, "$role: the phone's own buzz closed its Subroutine")
            assertEquals(1.0, model.motion.left, "$role: the phone's own buzz spent its budget")
            now = 10_000
            var n = 0
            while (model.state.screen == ScreenId.SubHandshake && n < 10_000) {
                model.motionSampled(0.12, 0.15, 0.05)
                n++
            }
            assertTrue(model.state.screen != ScreenId.SubHandshake, "$role: a walk after the buzz never spent the budget")
            n
        }
        assertEquals(counts.first(), counts.last(), "the walk that spends the budget differs by role")
    }
}
