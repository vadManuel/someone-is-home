package home.someoneshome.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The phone's own clock, in its own zone, in the forms `ui` words** (D-243).
 *
 * The zone is whatever the machine running the test is set to, so the hour cannot be pinned; what
 * can be is that every field is in its range and that the minute is the wall clock's minute, which
 * no time zone moves (every zone in use is a whole number of quarter hours from UTC).
 */
class LocalWallTimeTest {

    @Test
    fun everyFieldIsInItsRange() {
        val now = localWallTime()
        assertTrue(now.hour in 0..23, "hour $now")
        assertTrue(now.minute in 0..59, "minute $now")
        assertTrue(now.weekday in 1..7, "weekday $now")
        assertTrue(now.day in 1..31, "day $now")
        assertTrue(now.month in 1..12, "month $now")
    }

    @Test
    fun theMinuteIsTheWallClocksMinuteModuloAQuarterHour() {
        // Read either side of the local reading, and retry across a minute boundary.
        repeat(3) {
            val before = wallClockMillis() / 60_000 % 15
            val local = localWallTime().minute % 15
            val after = wallClockMillis() / 60_000 % 15
            if (before == after) {
                assertEquals(before.toInt(), local, "the local minute is not the wall clock's")
                return
            }
        }
    }
}
