package home.someoneshome.platform

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import platform.CoreHaptics.CHHapticEngine
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **The real motor, asked to do things on whatever target is running the test.**
 *
 * Under `./gradlew check` that target is the **Simulator**, which has no Taptic Engine — so what
 * this run certifies is the silent branch, and it says so rather than implying more. *The
 * Simulator cannot run this game*; it can only prove that the absence of hardware is handled like
 * an ordinary Tuesday instead of like an exception.
 *
 * The other branch is certified by the device log, quoted in the worklog, and by a hand on the
 * phone in the morning. Nothing here can replace either.
 */
class CoreHapticsMotorTest {

    private val supported: Boolean = CHHapticEngine.capabilitiesForHardware().supportsHaptics()

    /**
     * **The no-op stays silent, and stays quiet about it to everyone but the log.**
     *
     * Silent as in *nothing happens and nothing throws*. A motor that threw on a target without
     * hardware would take the screen down with it, and rule 5 is unambiguous about what a screen
     * that blanks in a dark house looks like: a revocation. So the failure has to be boring.
     */
    @Test
    fun aTargetWithNoMotorIsSilentAndNeverThrows() {
        val log = mutableListOf<String>()
        val motor = CoreHapticsMotor(log::add)

        Haptic.entries.forEach(motor::buzz)
        motor.play(listOf(HapticStep.Buzz(90), HapticStep.Rest(220), HapticStep.Buzz(90)))
        motor.play(emptyList())
        motor.stop()
        motor.stop()

        if (supported) {
            assertTrue(
                log.none { it.contains("no haptic hardware") },
                "this target reports haptic hardware and the motor called itself silent: $log",
            )
        } else {
            assertTrue(
                log.isNotEmpty() && log.all { it.startsWith("[haptics]") },
                "a target with no motor said nothing to the log, so a phone that will never buzz " +
                    "for the rest of the evening looks exactly like one that is working: $log",
            )
            assertTrue(
                log.count { it.contains("no haptic hardware") } >= Haptic.entries.size,
                "not every buzz on a target with no motor was accounted for: $log",
            )
        }
    }

    /** An empty script is not an error either — it is a scan that asked for nothing. */
    @Test
    fun anEmptyScriptIsRefusedToTheLogAndNotToThePlayer() {
        val log = mutableListOf<String>()
        CoreHapticsMotor(log::add).play(listOf(HapticStep.Rest(100)))
        assertTrue(
            log.any { it.contains("no buzzes in it") || it.contains("no haptic hardware") },
            "a script of nothing but silence passed without a word: $log",
        )
    }
}
