package home.someoneshome.platform.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClockSyncTest {

    /** One upload answered: sent at [sent], the house at [heard] and [answered], back at [received]. */
    private fun ClockSync.upload(sent: Long, heard: Long, answered: Long = heard, received: Long) =
        onSample(sent, heard, answered, received)

    @Test
    fun aBurstAtSeatingAndNoMoreOnThatConnection() {
        // D-295: the uploads refresh the estimate every second, so the 30 s bursts are gone.
        val sync = ClockSync()
        val burst = sync.dueProbes(nowMillis = 1_000)
        assertEquals(PROBES_PER_ROUND, burst.size)
        assertEquals(emptyList(), sync.dueProbes(nowMillis = 1_100), "no probes while a burst runs")
        burst.forEach { sync.onMark(it, hostMillis = 500_000, nowMillis = 1_200) }
        assertEquals(emptyList(), sync.dueProbes(nowMillis = 31_000), "a second burst on one connection")
        assertEquals(emptyList(), sync.dueProbes(nowMillis = 1_800_000), "a burst half an hour later")
        sync.reconnected(nowMillis = 1_800_001)
        assertEquals(PROBES_PER_ROUND, sync.dueProbes(nowMillis = 1_800_002).size, "a new connection bursts at once")
    }

    @Test
    fun theMinimumRttSampleWinsNotTheMean() {
        // Five samples whose offsets disagree wildly; only the least-delayed one is believed,
        // because a delayed packet lies about the offset in proportion to its delay (D5).
        val sync = ClockSync()
        val probes = sync.dueProbes(nowMillis = 0)
        sync.onMark(probes[0], hostMillis = 500_400, nowMillis = 400) // rtt 400 → offset 500 200
        sync.onMark(probes[1], hostMillis = 500_010, nowMillis = 20)  // rtt 20  → offset 500 000
        sync.onMark(probes[2], hostMillis = 501_000, nowMillis = 900) // rtt 900 → offset 500 550
        sync.onMark(probes[3], hostMillis = 500_300, nowMillis = 250) // rtt 250 → offset 500 175
        sync.onMark(probes[4], hostMillis = 500_600, nowMillis = 700) // rtt 700 → offset 500 250
        assertEquals(500_000, sync.appliedOffsetMillis(nowMillis = 1_000), "the rtt-20 sample decides")
        assertEquals(20, sync.lastRttMillis)
    }

    @Test
    fun anUploadIsOneSampleAndTheHousesOwnTimeWithItIsNotRoundTrip() {
        // Sent at 1 000, read by the house at 501 010, answered 200 ms later (its queue and its
        // rules), back at 1 230: 30 ms of network, 200 of house. The offset is 500 000 whichever
        // way the 30 ms split, and the 200 ms is not counted against it.
        val sync = ClockSync()
        sync.upload(sent = 1_000, heard = 501_015, answered = 501_215, received = 1_230)
        assertEquals(500_000, sync.appliedOffsetMillis(1_230))
        assertEquals(30, sync.lastRttMillis)
    }

    @Test
    fun theWindowsLeastDelayedUploadDecidesAndAnOldOneIsForgotten() {
        val sync = ClockSync()
        // A quiet beat (rtt 4) among loud ones (rtt 80 to 300, offsets lying by up to half).
        sync.upload(sent = 0, heard = 500_150, received = 300)        // rtt 300, offset 500 000 ± 150
        sync.upload(sent = 1_000, heard = 501_002, received = 1_004)  // rtt 4, offset 500 000
        var t = 2_000L
        repeat(SAMPLE_WINDOW - 2) {
            sync.upload(sent = t, heard = t + 500_070, received = t + 80) // rtt 80, offset 500 030
            t += 1_000
        }
        assertEquals(4, sync.lastRttMillis, "the quiet beat is in the window and decides")
        assertEquals(500_000, sync.fix!!.targetOffsetMillis)
        // One more loud beat pushes the quiet one out of the last SAMPLE_WINDOW.
        sync.upload(sent = t, heard = t + 500_070, received = t + 80)
        sync.upload(sent = t + 1_000, heard = t + 1_000 + 500_070, received = t + 1_080)
        assertEquals(80, sync.lastRttMillis, "a sample older than the window still decided")
        assertEquals(500_030, sync.fix!!.targetOffsetMillis)
    }

    @Test
    fun aDriftingClockIsFollowedBeatByBeat() {
        // The house's clock runs 100 ppm fast against this phone's: after 30 minutes it is 180 ms
        // further on. Sampled every second with 5 ms of round trip, the applied offset follows it
        // to within a few milliseconds the whole way; a fix taken once at the start would be the
        // whole 180 ms wrong at the end.
        val sync = ClockSync()
        var worst = 0L
        var t = 0L
        while (t <= 1_800_000) {
            val house = { local: Long -> 500_000 + local + local / 10_000 }
            sync.upload(sent = t, heard = house(t + 2), answered = house(t + 3), received = t + 5)
            val error = sync.hostNowMillis(t + 5)!! - house(t + 5)
            if (t > 0) worst = maxOf(worst, kotlin.math.abs(error))
            t += 1_000
        }
        println("CLOCK DRIFT 100 PPM, 30 MIN, AN UPLOAD A SECOND — worst error $worst ms")
        assertTrue(worst <= 3, "the estimate fell $worst ms behind a clock drifting 100 ppm")
    }

    @Test
    fun anImpossibleSampleIsNotBelieved() {
        val sync = ClockSync()
        sync.upload(sent = 1_000, heard = 500_000, answered = 500_100, received = 1_050) // rtt −50
        sync.upload(sent = 1_000, heard = 500_100, answered = 500_000, received = 1_200) // answered before heard
        assertNull(sync.fix, "an impossible sample became a fix")
        assertEquals(2, sync.strayMarks)
    }

    @Test
    fun theFirstFixSetsAndLaterFixesSlew() {
        val sync = ClockSync()
        sync.dueProbes(nowMillis = 0).forEach { sync.onMark(it, hostMillis = 500_010, nowMillis = 20) }
        assertEquals(500_000, sync.appliedOffsetMillis(20), "the first fix is applied whole")

        // A quieter upload claims the offset is 300 ms larger. The applied offset walks there at
        // the bounded rate rather than arriving.
        val t2 = 30_000L
        sync.upload(sent = t2, heard = t2 + 500_305, received = t2 + 10)
        val atFix = sync.appliedOffsetMillis(t2 + 10)!!
        assertTrue(atFix < 500_100, "the new estimate must not be applied as a jump (got $atFix)")
        assertEquals(
            atFix + SLEW_RATE_MILLIS_PER_SECOND,
            sync.appliedOffsetMillis(t2 + 1_010),
            "one second of slewing moves the offset by exactly the rate",
        )
        assertEquals(500_300, sync.appliedOffsetMillis(t2 + 100_000), "the slew stops at the target")
    }

    @Test
    fun theHostTimelineNeverRunsBackward() {
        // The adversarial direction: a later sample says the offset was 400 ms too HIGH. A jump
        // would pull the mapped timeline backward past events already scheduled on it; the slew
        // rate being under 1000 ms/s means the timeline slows and never reverses.
        val sync = ClockSync()
        sync.dueProbes(nowMillis = 0).forEach { sync.onMark(it, hostMillis = 500_410, nowMillis = 20) }
        val t2 = 30_000L
        // Sampled BEFORE the correcting sample lands, so the fix boundary itself is covered — a
        // pure jump to the new target passed the first version of this test, because every
        // sample was taken after the jump had already happened.
        var previous = sync.hostNowMillis(t2 + 9)!!
        sync.upload(sent = t2 + 6, heard = t2 + 500_008, received = t2 + 10)
        var t = t2 + 10
        while (t < t2 + 12_000) {
            val mapped = sync.hostNowMillis(t)!!
            assertTrue(mapped >= previous, "the host timeline ran backward at local $t: $mapped < $previous")
            previous = mapped
            t += 100
        }
        assertEquals(500_000, sync.appliedOffsetMillis(t2 + 100_000), "the correction does land, slowly")
    }

    @Test
    fun beforeAnyFixThereIsNoHostTimeline() {
        val sync = ClockSync()
        assertNull(sync.hostNowMillis(nowMillis = 5_000))
        sync.dueProbes(nowMillis = 5_000)
        assertNull(sync.hostNowMillis(nowMillis = 5_500), "an unanswered round is not a fix")
    }

    @Test
    fun anAbandonedRoundStillCountsItsSamples() {
        val sync = ClockSync()
        val probes = sync.dueProbes(nowMillis = 0)
        sync.onMark(probes[0], hostMillis = 500_050, nowMillis = 100) // the only answer
        assertNull(sync.appliedOffsetMillis(200), "one of five is not yet a finished round")
        assertEquals(emptyList(), sync.dueProbes(nowMillis = ROUND_TIMEOUT_MILLIS + 1))
        assertEquals(500_000, sync.appliedOffsetMillis(ROUND_TIMEOUT_MILLIS + 1), "the partial round's best sample became the fix")
    }

    @Test
    fun strayMarksAreCountedNotCrashedOn() {
        val sync = ClockSync()
        sync.onMark(probe = 99, hostMillis = 1, nowMillis = 1)
        val probes = sync.dueProbes(nowMillis = 10)
        sync.onMark(probes[0], hostMillis = 500_000, nowMillis = 20)
        sync.onMark(probes[0], hostMillis = 500_000, nowMillis = 30) // answered twice
        assertEquals(2, sync.strayMarks)
    }
}
