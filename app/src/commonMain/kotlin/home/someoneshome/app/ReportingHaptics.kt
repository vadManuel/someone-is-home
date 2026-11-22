package home.someoneshome.app

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import home.someoneshome.platform.Haptics
import home.someoneshome.ui.OwnMotor

/**
 * **The motor, telling the motion budget when it is playing** (D-327, provisional).
 *
 * The Taptic Engine shakes the phone it is in, and Core Motion's `userAcceleration` reads it, so a
 * player standing still while Handshake, Sniff, Drift or a verdict buzzes would spend their own
 * budget. Every buzz in the app goes through the one motor the app root holds, so this wraps that
 * motor: each [buzz], [play] and [stop] is told to [own] as it is passed on, and the panel's budget
 * ([home.someoneshome.ui.MotionBudget]) takes no sample while [own] says it is running.
 *
 * It changes nothing the motor does: the same call, in the same order, with nothing added and
 * nothing chosen here (D-102, `HouseBuzzer`). It reads no role, and both roles' buzzes are the same
 * scripts. `ui` cannot see `platform`, which is why the join is here.
 */
class ReportingHaptics(private val motor: Haptics, private val own: OwnMotor) : Haptics {

    override fun buzz(haptic: Haptic) {
        own.played(HouseBuzz.script(haptic))
        motor.buzz(haptic)
    }

    override fun play(script: List<HapticStep>, repeating: Boolean) {
        own.played(script, repeating)
        motor.play(script, repeating)
    }

    override fun stop() {
        own.stopped()
        motor.stop()
    }
}
