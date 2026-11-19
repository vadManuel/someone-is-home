package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.UploadProbe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Each upload's probe is remembered with when it left, and each answer is one sample** (D-295);
 * and **the felt pulse lands where the house's grid is, through the phone's own offset**.
 */
class UploadClockTest {

    private class Sample(val sent: Long, val heard: Long, val answered: Long, val received: Long)

    @Test
    fun anAnswerIsMatchedToTheProbeItEchoesAndUsedOnce() {
        var now = 10_000L
        var n = 0
        val clock = UploadClock(nowMillis = { now }, draw = { (++n).toString(16).padStart(16, '0') })
        val first = clock.stamp()
        now = 11_000L
        val second = clock.stamp()
        val got = mutableListOf<Sample>()
        // The second upload's answer comes back first: each is matched by its probe, not its order.
        clock.answered(RoundBody.UploadHeard.of("0123456789abcdef", second, 500_000L, 500_020L), 11_090L) { s, h, a, r ->
            got += Sample(s, h, a, r)
        }
        clock.answered(RoundBody.UploadHeard.of("0123456789abcdef", first, 499_010L, 499_011L), 11_100L) { s, h, a, r ->
            got += Sample(s, h, a, r)
        }
        assertEquals(listOf(11_000L, 10_000L), got.map { it.sent }, "an answer was matched to the wrong upload")
        assertEquals(listOf(500_000L, 499_010L), got.map { it.heard })
        assertEquals(listOf(500_020L, 499_011L), got.map { it.answered })
        // A second copy of an answer is not a second sample.
        clock.answered(RoundBody.UploadHeard.of("0123456789abcdef", first, 499_010L, 499_011L), 11_200L) { _, _, _, _ ->
            got += Sample(0, 0, 0, 0)
        }
        assertEquals(2, got.size, "an answer heard twice was taken twice")
    }

    @Test
    fun noProbeAndForgottenProbesAreNeverSamples() {
        val clock = UploadClock(nowMillis = { 0L })
        assertNull(clock.take(UploadProbe.NONE))
        val probe = clock.stamp()
        assertTrue(UploadProbe.isProbe(probe) && probe != UploadProbe.NONE, "a probe was not sixteen hex characters: $probe")
        clock.forget()
        assertNull(clock.take(probe), "a probe from before the round's end was matched")
        // Only the last KEPT are held: an answer later than that is dropped, not misread.
        val old = clock.stamp()
        repeat(UploadClock.KEPT) { clock.stamp() }
        assertNull(clock.take(old), "a probe older than ${UploadClock.KEPT} beats was matched")
    }

    @Test
    fun probesAreFreshEveryBeat() {
        val clock = UploadClock(nowMillis = { 0L })
        val drawn = List(64) { clock.stamp() }
        assertEquals(drawn.size, drawn.toSet().size, "a probe repeated within 64 beats")
    }

    /**
     * **The felt pulse is the house's grid, read through this phone's offset.** A phone 3 s ahead of
     * the house buzzes pulse n when its estimate of the house's clock reaches it; a phone that read
     * its own clock instead would buzz 3 s early.
     */
    @Test
    fun theNextPulseIsOnTheHousesGridAndNeverTwice() {
        val from = 1_000_000L
        val interval = Balance.SYNC_PULSE_INTERVAL_MILLIS
        assertEquals(0L, PulseBuzzer.nextPulse(from, now = from - 2_000))
        assertEquals(0L, PulseBuzzer.nextPulse(from, now = from))
        assertEquals(1L, PulseBuzzer.nextPulse(from, now = from + 1))
        assertEquals(7L, PulseBuzzer.nextPulse(from, now = from + 7 * interval))
        assertEquals(8L, PulseBuzzer.nextPulse(from, now = from + 7 * interval, after = 7L), "a pulse buzzed twice")
        assertEquals(from + 5 * interval, PulseBuzzer.pulseAt(from, 5))
    }
}
