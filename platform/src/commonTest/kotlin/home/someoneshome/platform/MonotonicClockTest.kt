package home.someoneshome.platform

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The clock, not the game. Runs anywhere — this is one of the few things in this project the
 * simulator can honestly verify, because it touches no radio, torch, camera or haptic.
 */
class MonotonicClockTest {

    @Test
    fun `it never goes backwards`() {
        var previous = monotonicNanos()
        repeat(10_000) {
            val now = monotonicNanos()
            assertTrue(now >= previous, "clock went backwards: $previous then $now")
            previous = now
        }
    }

    @Test
    fun `it advances`() {
        val start = monotonicNanos()
        var spin = 0L
        while (monotonicNanos() == start && spin < 100_000_000L) spin++
        assertTrue(monotonicNanos() > start, "the clock did not move in 100M reads")
    }

    /** A plausible magnitude. A clock returning seconds or millis here would be a 1e9 error. */
    @Test
    fun `two readings a spin apart differ by a plausible number of nanoseconds`() {
        val a = monotonicNanos()
        var spin = 0L
        repeat(1_000_000) { spin += it.toLong() }
        val elapsed = monotonicNanos() - a
        assertTrue(spin >= 0)
        assertTrue(elapsed in 1L..10_000_000_000L, "implausible elapsed for a spin loop: $elapsed ns")
    }
}
