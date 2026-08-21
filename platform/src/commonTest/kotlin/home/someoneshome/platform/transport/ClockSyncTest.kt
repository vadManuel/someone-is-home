package home.someoneshome.platform.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClockSyncTest {

    @Test
    fun aBurstAtJoinThenSilenceUntilTheResync() {
        val sync = ClockSync()
        val burst = sync.dueProbes(nowMillis = 1_000)
        assertEquals(PROBES_PER_ROUND, burst.size)
        assertEquals(emptyList(), sync.dueProbes(nowMillis = 1_100), "no probes while a round runs")
        burst.forEach { sync.onMark(it, hostMillis = 500_000, nowMillis = 1_200) }
        assertEquals(emptyList(), sync.dueProbes(nowMillis = 20_000), "no probes before the 30 s cadence")
        assertEquals(PROBES_PER_ROUND, sync.dueProbes(nowMillis = 1_000 + RESYNC_MILLIS).size)
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
    fun theFirstFixSetsAndLaterFixesSlew() {
        val sync = ClockSync()
        sync.dueProbes(nowMillis = 0).forEach { sync.onMark(it, hostMillis = 500_010, nowMillis = 20) }
        assertEquals(500_000, sync.appliedOffsetMillis(20), "the first fix is applied whole")

        // A second round claims the offset is 300 ms larger. The applied offset walks there at
        // the bounded rate rather than arriving.
        val t2 = RESYNC_MILLIS
        sync.dueProbes(nowMillis = t2).forEach { sync.onMark(it, hostMillis = t2 + 500_310, nowMillis = t2 + 10) }
        val atFix = sync.appliedOffsetMillis(t2 + 10)!!
        assertTrue(atFix < 500_100, "the new estimate must not be applied as a jump (got $atFix)")
        assertEquals(
            atFix + SLEW_RATE_MILLIS_PER_SECOND,
            sync.appliedOffsetMillis(t2 + 1_010),
            "one second of slewing moves the offset by exactly the rate",
        )
        assertEquals(500_305, sync.appliedOffsetMillis(t2 + 100_000), "the slew stops at the target")
    }

    @Test
    fun theHostTimelineNeverRunsBackward() {
        // The adversarial direction: a later round says the offset was 400 ms too HIGH. A jump
        // would pull the mapped timeline backward past events already scheduled on it; the slew
        // rate being under 1000 ms/s means the timeline slows and never reverses.
        val sync = ClockSync()
        sync.dueProbes(nowMillis = 0).forEach { sync.onMark(it, hostMillis = 500_410, nowMillis = 20) }
        val t2 = RESYNC_MILLIS
        // Sampled BEFORE the correcting round lands, so the fix boundary itself is covered — a
        // pure jump to the new target passed the first version of this test, because every
        // sample was taken after the jump had already happened.
        var previous = sync.hostNowMillis(t2 + 9)!!
        sync.dueProbes(nowMillis = t2).forEach { sync.onMark(it, hostMillis = t2 + 500_005, nowMillis = t2 + 10) }
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
