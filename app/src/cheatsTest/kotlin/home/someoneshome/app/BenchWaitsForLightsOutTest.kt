package home.someoneshome.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The bench waits for LIGHTS OUT however long the room takes, and only then for its work order.**
 *
 * The first one-phone device session (2026-11-16) found the bench giving up sixty seconds after
 * the app launched, before anybody had pressed LIGHTS OUT, so a scripted round ran without the
 * phone it was launched to play. These run [awaitTheWorkOrder] on a clock of their own: every
 * pause moves it forward and nothing really sleeps.
 */
class BenchWaitsForLightsOutTest {

    /** A room that takes [lightsAt] to go dark, and a house that deals the order [orderAfter] later. */
    private class Room(private val lightsAt: Long, private val orderAfter: Long?, private val giveUpAt: Long = Long.MAX_VALUE) {
        var now = 0L
        val notes = mutableListOf<String>()
        val lightsOut: () -> Boolean = { now >= lightsAt }
        val ordered: () -> Boolean = { orderAfter != null && now >= lightsAt + orderAfter }
        val pause: suspend (Long) -> Unit = { step ->
            now += step
            if (now > giveUpAt) throw StillWaiting()
        }
    }

    private class StillWaiting : RuntimeException()

    /** **GUARD: ten minutes of seating and a LIGHTS OUT at the end of it, and the bench still plays.** */
    @Test
    fun aSlowRoomIsWaitedForAndTheOrderIsPlayed() {
        val room = Room(lightsAt = 10 * 60_000L, orderAfter = 2_000L)
        val played = runBlocking { awaitTheWorkOrder(room.lightsOut, room.ordered, { room.notes += it }, room.pause) }
        assertTrue(played, "the bench gave up before the lights went out: ${room.notes}")
        assertTrue(room.notes.none { "NO WORK ORDER" in it }, "the bench said it had no order before the lights went out")
        assertEquals(10, room.notes.count { it.startsWith("STILL WAITING FOR THE LIGHTS TO GO OUT") })
    }

    /** Before the lights go out there is no timeout at all: an hour in, it is still waiting. */
    @Test
    fun beforeTheLightsGoOutThereIsNoTimeout() {
        val room = Room(lightsAt = Long.MAX_VALUE, orderAfter = null, giveUpAt = 60 * 60_000L)
        assertFailsWith<StillWaiting>("the bench stopped waiting for LIGHTS OUT on a clock") {
            runBlocking { awaitTheWorkOrder(room.lightsOut, room.ordered, { room.notes += it }, room.pause) }
        }
        assertTrue(room.notes.none { "NO WORK ORDER" in it })
    }

    /** Once the lights are out the order is owed, and its absence is said out loud after the patience. */
    @Test
    fun afterTheLightsGoOutAMissingOrderIsNamed() {
        val room = Room(lightsAt = 90_000L, orderAfter = null)
        val played = runBlocking { awaitTheWorkOrder(room.lightsOut, room.ordered, { room.notes += it }, room.pause) }
        assertFalse(played)
        assertEquals(90_000L + ORDER_PATIENCE_MILLIS, room.now, "the patience did not start at LIGHTS OUT")
        assertTrue(room.notes.last().startsWith("THE LIGHTS WENT OUT AND NO WORK ORDER ARRIVED"))
    }

    /** An order that is the first thing to arrive ends both halves of the wait at once. */
    @Test
    fun anOrderBeforeTheOpeningIsEnough() {
        var now = 0L
        val played = runBlocking {
            awaitTheWorkOrder(lightsOut = { false }, ordered = { now >= 4_000L }, note = {}, pause = { now += it })
        }
        assertTrue(played)
    }
}
