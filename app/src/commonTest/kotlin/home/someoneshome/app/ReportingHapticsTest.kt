package home.someoneshome.app

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.platform.RecordingHaptics
import home.someoneshome.ui.MotorClock
import home.someoneshome.ui.OwnMotor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The app root's motor tells the motion budget when it plays, and plays exactly what it is asked**
 * (D-327). Every buzz in the app goes through the one motor the root holds, so the join is there.
 */
class ReportingHapticsTest {

    private var now = 0L
    private val own = OwnMotor(MotorClock { now })
    private val recorded = RecordingHaptics()
    private val motor = ReportingHaptics(recorded, own)

    @Test
    fun `a buzz is passed on unchanged and the budget hears it until its tail is over`() {
        motor.buzz(Haptic.Short)
        assertEquals(listOf(HouseBuzz.script(Haptic.Short)), recorded.played, "the motor was not asked for the house's buzz")
        assertTrue(own.running(), "the budget did not hear the buzz")
        now = HouseBuzz.SHORT_MILLIS + OwnMotor.TAIL_MILLIS - 1
        assertTrue(own.running(), "the budget stopped hearing the buzz before its tail was over")
        now = HouseBuzz.SHORT_MILLIS + OwnMotor.TAIL_MILLIS
        assertTrue(!own.running(), "the budget still hears a buzz that is over")
    }

    @Test
    fun `a script and the ring are passed on and a stop is heard`() {
        val script = listOf(HapticStep.Buzz(120), HapticStep.Rest(400), HapticStep.Buzz(120))
        motor.play(script)
        assertTrue(own.running())
        motor.play(HouseBuzz.RING, repeating = true)
        assertEquals(listOf(RecordingHaptics.Play(script), RecordingHaptics.Play(HouseBuzz.RING, repeating = true)), recorded.plays)
        assertTrue(recorded.ringing)
        now = 60_000
        assertTrue(own.running(), "the ring was taken as over")
        motor.stop()
        assertEquals(1, recorded.stopped, "the stop did not reach the motor")
        now += OwnMotor.TAIL_MILLIS
        assertTrue(!own.running(), "the budget still hears a ring that was stopped")
    }

    @Test
    fun `each house buzz is heard for its own length and its tail`() {
        // Nothing here reads a role: the verdict a Resident's real work and an Insider's fake are
        // sent is the same `Haptic` (D-102), so the same call, and the same time heard.
        val heard = listOf(Haptic.Short, Haptic.Double, Haptic.Long).map { haptic ->
            val clock = longArrayOf(0L)
            val ownOf = OwnMotor(MotorClock { clock[0] })
            ReportingHaptics(RecordingHaptics(), ownOf).buzz(haptic)
            var ms = 0L
            while (ownOf.running() && ms < 10_000) { ms++; clock[0] = ms }
            haptic to ms
        }
        assertEquals(
            listOf(
                Haptic.Short to HouseBuzz.SHORT_MILLIS + OwnMotor.TAIL_MILLIS,
                Haptic.Double to 2L * HouseBuzz.SHORT_MILLIS + HouseBuzz.DOUBLE_GAP_MILLIS + OwnMotor.TAIL_MILLIS,
                Haptic.Long to HouseBuzz.LONG_MILLIS + OwnMotor.TAIL_MILLIS,
            ),
            heard,
        )
    }
}
