package home.someoneshome.platform

import home.someoneshome.model.ContactWindow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **The clock contact windows are cut from is the wall clock, in milliseconds** (D-217).
 *
 * Every phone and the house cut one window index from it, so it has to be the shared epoch rather
 * than the time since this phone booted, and it has to be milliseconds rather than seconds or
 * nanoseconds: off by either, every token is resolved against a window hours or years away.
 */
class WallClockTest {

    @Test
    fun itIsTheEpochInMillisecondsAndNotTheTimeSinceBoot() {
        val now = wallClockMillis()
        // 2026-01-01 and 2100-01-01, in milliseconds since the epoch.
        assertTrue(now in 1_767_225_600_000L..4_102_444_800_000L, "the wall clock read $now")
        val boot = monotonicNanos() / 1_000_000
        assertTrue(now - boot > 1_000_000_000L, "the wall clock reads like the time since boot: $now")
    }

    @Test
    fun aWindowCutFromItIsFifteenSecondsWideAndMovesForward() {
        val a = ContactWindow.containing(wallClockMillis())
        val b = ContactWindow.containing(wallClockMillis() + ContactWindow.MILLIS)
        assertTrue(b.index == a.index + 1 || b.index == a.index + 2, "windows did not step: $a then $b")
    }
}
