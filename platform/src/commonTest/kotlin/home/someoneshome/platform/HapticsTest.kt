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
 * **The buzzes, and the fact that each of them is one definition.**
 *
 * D-135 closes the long haptic to five events; D-160 adds *no* and takes the incoming call out of
 * the five; D-102 makes every kind identical for every player. Between them they say the app has
 * exactly this many patterns, and that is a property a test can hold even before any of them exist
 * to be felt.
 */
class HapticsTest {

    @Test
    fun everyKindIsOnePatternAndTheyAreOneDefinitionEach() {
        assertEquals(
            listOf(HapticStep.Buzz(HouseBuzz.SHORT_MILLIS)), HouseBuzz.script(Haptic.Short),
            "the Short is one buzz of one length and nothing else",
        )
        assertEquals(
            listOf(HapticStep.Buzz(HouseBuzz.LONG_MILLIS)), HouseBuzz.script(Haptic.Long),
            "the Long is one buzz of one length and nothing else",
        )
        assertEquals(
            listOf(
                HapticStep.Buzz(HouseBuzz.SHORT_MILLIS),
                HapticStep.Rest(HouseBuzz.DOUBLE_GAP_MILLIS),
                HapticStep.Buzz(HouseBuzz.SHORT_MILLIS),
            ),
            HouseBuzz.script(Haptic.Double),
            "the Double is two of the Short with one gap and nothing else",
        )
        assertEquals(
            Haptic.entries.size, HouseBuzz.KINDS.distinct().size,
            "${Haptic.entries.size} kinds must be that many distinguishable patterns — ${HouseBuzz.KINDS}",
        )
        assertTrue(
            HouseBuzz.LONG_MILLIS > HouseBuzz.SHORT_MILLIS * 2,
            "the Long is ${HouseBuzz.LONG_MILLIS}ms against a Short of ${HouseBuzz.SHORT_MILLIS}ms " +
                "— through a pocket that is one buzz, and D-135's closed set is built on a " +
                "player being able to tell the two apart without looking",
        )
    }

    /**
     * **The *no* must not be feelable as a slow *yes*, or as a *Long***.
     *
     * D-160 gives the alphabet three words and they are told apart by a hand in a pocket in the
     * dark. Two of those distinctions are arithmetic and can be held here: the gap has to be wide
     * enough to be a gap, and the whole double has to be shorter than the Long — otherwise the
     * refusal a scan window produces reads as one of the five events a player must not miss.
     */
    @Test
    fun theNoIsTwoBuzzesAndNotOneLongOne() {
        val double = HouseBuzz.script(Haptic.Double)
        assertEquals(2, double.count { it is HapticStep.Buzz }, "the *no* is two buzzes: $double")
        assertTrue(
            HouseBuzz.DOUBLE_GAP_MILLIS >= HouseBuzz.SHORT_MILLIS,
            "the gap is ${HouseBuzz.DOUBLE_GAP_MILLIS}ms against buzzes of " +
                "${HouseBuzz.SHORT_MILLIS}ms — a gap narrower than the buzz either side of it is " +
                "felt as one longer buzz, which is the *yes* delivered slowly",
        )
        val total = double.sumOf { if (it is HapticStep.Buzz) it.millis else (it as HapticStep.Rest).millis }
        assertTrue(
            total < HouseBuzz.LONG_MILLIS,
            "the whole *no* runs ${total}ms against a Long of ${HouseBuzz.LONG_MILLIS}ms — a " +
                "refusal that outlasts the Long is a refusal a hurried hand reads as the Egress",
        )
    }

    /**
     * **The call, and the trailing rest that keeps it a pair of buzzes rather than a train.**
     *
     * D-160: LONG–pause–LONG, repeating indefinitely. A pattern whose last step is a buzz loops
     * straight from one ring into the next, and the pairing that says *telephone* is gone — so the
     * rest at the end is not tidiness, it is the shape of the signal.
     */
    @Test
    fun theCallIsTwoLongsAPauseAndAGapBeforeItComesRound() {
        assertEquals(
            listOf(
                HapticStep.Buzz(HouseBuzz.LONG_MILLIS),
                HapticStep.Rest(HouseBuzz.RING_PAUSE_MILLIS),
                HapticStep.Buzz(HouseBuzz.LONG_MILLIS),
                HapticStep.Rest(HouseBuzz.RING_GAP_MILLIS),
            ),
            HouseBuzz.RING,
        )
        assertTrue(
            HouseBuzz.RING.last() is HapticStep.Rest,
            "the ring ends on a buzz, so every repeat runs the two Longs together: ${HouseBuzz.RING}",
        )
        assertTrue(
            HouseBuzz.RING_GAP_MILLIS > HouseBuzz.RING_PAUSE_MILLIS,
            "the gap between two rings (${HouseBuzz.RING_GAP_MILLIS}ms) is not wider than the " +
                "pause inside one (${HouseBuzz.RING_PAUSE_MILLIS}ms) — the pairing is what makes " +
                "it a phone ringing rather than a phone vibrating",
        )
        assertTrue(
            HouseBuzz.RING !in HouseBuzz.KINDS,
            "the ring became a kind — no `buzz` may ask for the one pattern that does not stop",
        )
        assertTrue(HouseBuzz.RING in HouseBuzz.ALL, "the ring is a pattern an event produces")
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
        SilentHaptics.play(HouseBuzz.RING, repeating = true)
        SilentHaptics.stop()
    }

    /**
     * **The recorder holds *until answered* as a state, not as a flag on the last row.**
     *
     * The property D-160 makes is about the phone *now*: it is ringing, and it goes on ringing
     * until something silences it. A recorder that only kept the flag would answer *yes, still
     * ringing* for the rest of the round, and every test built on it would be reading a fact from
     * ten minutes ago.
     */
    @Test
    fun theRecorderKnowsWhenThePhoneStoppedRinging() {
        val motor = RecordingHaptics()
        assertTrue(!motor.ringing, "a motor nobody has touched is ringing")

        motor.play(HouseBuzz.RING, repeating = true)
        assertTrue(motor.ringing, "the call was played and the phone is not ringing")

        motor.stop()
        assertTrue(!motor.ringing, "the phone was answered and it is still ringing")
        assertEquals(1, motor.stopped)

        // The motor replaces rather than queues, so anything the house says next ends the ring
        // too — which is the arrangement D-156 closed, seen from the other side.
        motor.play(HouseBuzz.RING, repeating = true)
        motor.buzz(Haptic.Short)
        assertTrue(!motor.ringing, "a buzz landed on top of the ring and the ring survived it")
        assertEquals(1, motor.stopped, "a replacing buzz was recorded as somebody answering")
    }
}
