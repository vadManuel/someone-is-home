package home.someoneshome.platform

import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.HouseBuzz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * **The two buzzes, and the fact that there are two.**
 *
 * D-135 closes the long haptic to five events; D-102 makes both kinds identical for every player.
 * Between them they say the app has exactly two patterns, and that is a property a test can hold
 * even before any of the five exist to be felt.
 */
class HapticsTest {

    @Test
    fun thereAreTwoPatternsAndTheyAreOneDefinitionEach() {
        assertEquals(
            listOf(HapticStep.Buzz(HouseBuzz.SHORT_MILLIS)), HouseBuzz.script(Haptic.Short),
            "the Short is one buzz of one length and nothing else",
        )
        assertEquals(
            listOf(HapticStep.Buzz(HouseBuzz.LONG_MILLIS)), HouseBuzz.script(Haptic.Long),
            "the Long is one buzz of one length and nothing else",
        )
        assertEquals(
            Haptic.entries.size, HouseBuzz.ALL.distinct().size,
            "two kinds must be two distinguishable patterns — ${HouseBuzz.ALL}",
        )
        assertTrue(
            HouseBuzz.LONG_MILLIS > HouseBuzz.SHORT_MILLIS * 2,
            "the Long is ${HouseBuzz.LONG_MILLIS}ms against a Short of ${HouseBuzz.SHORT_MILLIS}ms " +
                "— through a pocket that is one buzz, and D-135's closed set is built on a " +
                "player being able to tell the two apart without looking",
        )
    }

    /**
     * **The same list every time, not an equal one.** The whole app has an allocation budget of
     * about 0.5 MB/s and a buzz arrives on the effect thread, which is one of the threads that
     * budget is actually spent on (rule 7). A pattern rebuilt per event is small and pointless.
     */
    @Test
    fun aKindsPatternIsHeldRatherThanRebuilt() {
        Haptic.entries.forEach { kind ->
            assertSame(
                HouseBuzz.script(kind), HouseBuzz.script(kind),
                "$kind's pattern is rebuilt on every ask",
            )
        }
    }

    /** The recorder records what the motor would have done, which is scripts and not labels. */
    @Test
    fun theRecorderKeepsPatternsAndNotKinds() {
        val motor = RecordingHaptics()
        motor.buzz(Haptic.Long)
        motor.buzz(Haptic.Short)
        assertEquals(
            listOf(HouseBuzz.script(Haptic.Long), HouseBuzz.script(Haptic.Short)), motor.played,
        )
        assertEquals(2, motor.count)
        assertNotEquals(motor.played[0], motor.played[1], "the Long recorded as the Short")
    }

    /**
     * A stop is not a play. It was tempting to have [Haptics.stop] clear the recording — and that
     * would have made every count test read one lower than what the room felt.
     */
    @Test
    fun stoppingDoesNotUnhappenWhatAlreadyPlayed() {
        val motor = RecordingHaptics()
        motor.buzz(Haptic.Short)
        motor.stop()
        assertEquals(1, motor.count, "a buzz that already fired was erased by a stop")
    }

    /** The silent motor is silent and is not an error — the desktop harness runs on it. */
    @Test
    fun theSilentMotorRefusesNothing() {
        SilentHaptics.buzz(Haptic.Long)
        SilentHaptics.play(listOf(HapticStep.Buzz(10), HapticStep.Rest(10)))
        SilentHaptics.stop()
    }
}
