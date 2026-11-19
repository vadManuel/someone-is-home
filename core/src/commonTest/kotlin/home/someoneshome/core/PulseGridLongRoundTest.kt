package home.someoneshome.core

import home.someoneshome.model.Balance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The house's grid holds late in a long round and on a house that has been up for weeks** (D-295).
 *
 * D-295 makes the phones' estimate of the house's clock current every second, so a tap at minute 29
 * of a round, on a house whose monotonic clock reads billions of milliseconds, reaches the rules as
 * precisely as one at minute 1. What the rules must then do is grade it by the same arithmetic: the
 * pulse nearest the tap, the window either side of it inclusive, and nothing about how far into the
 * round or into the house's uptime the grid is.
 */
class PulseGridLongRoundTest {

    private val interval = Balance.SYNC_PULSE_INTERVAL_MILLIS
    private val window = Balance.SYNC_PULSE_WINDOW_MILLIS

    private fun on(pulseFrom: Long, first: Long, offsets: List<Long>): Boolean =
        onThePulse(pulseFrom, offsets.mapIndexed { k, off -> pulseAt(pulseFrom, first + k) + off })

    @Test
    fun theWindowIsTheSameAtMinuteOneAndMinuteTwentyNineOnAHouseUpForWeeks() {
        // A house up 46 days, an Egress fired, and the taps at its first pulses and at pulses 1 160
        // onward (29 minutes in: the grid does not stop, D-133 pauses the countdown, not the grid).
        for (pulseFrom in listOf(2_000L, 86_402_000L, 4_000_000_000L)) {
            for (first in listOf(0L, 1_160L)) {
                assertTrue(on(pulseFrom, first, listOf(window, -window, 0, window)), "the window's own edge missed at $pulseFrom, pulse $first")
                assertFalse(on(pulseFrom, first, listOf(window + 1, 0, 0, 0)), "one past the window held at $pulseFrom, pulse $first")
                assertFalse(on(pulseFrom, first, listOf(0, 0, 0, -window - 1)), "one before the window held at $pulseFrom, pulse $first")
                assertEquals(first + 2, pulseNear(pulseFrom, pulseAt(pulseFrom, first + 2) + interval / 2 - 1))
            }
        }
    }

    @Test
    fun aPersonAQuarterSecondEitherSideOnAPhoneWithinTwentyFiveMillisecondsHolds() {
        // The drift bench's numbers (ContainmentDriftTest): a person 240 ms off the felt pulse, on a
        // phone whose estimate is within 25 ms of the house, is inside the 400 ms window; the same
        // person on a phone whose offset was taken once at join and has drifted 174 ms (100 ppm over
        // 29 minutes) would be graded on their own felt pulse, which drifted with it, and still hold.
        // What a stale offset costs is unison between two phones (GDD:1291), not the grade.
        val pulseFrom = 86_402_000L
        assertTrue(on(pulseFrom, 1_160, listOf(240 + 25, -240 - 25, 240 + 25, -240 - 25)))
        assertFalse(on(pulseFrom, 1_160, listOf(240 + 174, -240, 240, -240)), "a tap 414 ms off the grid held")
    }
}
