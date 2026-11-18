package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The roster is read by seat index, so it holds one slot per seat index** (D-115, D-274).
 *
 * A round need not be armed with seats `0..n-1`: a guest who left and was not back by LIGHTS OUT
 * leaves a hole (the hunt's second round armed with seats 0, 1, 3, 4 and 5). Every phone reads the
 * roster by position — `nameOf(seat) = roster[seat.index]`, and a ballot row or a Surge row turns
 * back into the seat at its position — so a list packed tight over the hole moved every seat after
 * it onto the next person's name, and the room restrained HERON while every screen said FINCH.
 */
class RosterBySeatTest {

    /** Seat 2 is not in this round. */
    private val seats = listOf(Seat(0), Seat(1), Seat(3), Seat(4))
    private val names = seats.map { SeatNamed(it, "NAME-${it.index}") }

    private fun rosters(): List<Effect.RosterIssued> = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = 20261118L, seats = seats, insiders = listOf(Seat(1)),
            markers = placed((0 until 8).map { MarkerId("m$it") }), names = names,
        ),
    ).effects.filterIsInstance<Effect.RosterIssued>()

    @Test
    fun `a seat's name sits at that seat's index and the missing seat holds no name`() {
        val issued = rosters()
        assertEquals(seats.map { it.index }, issued.map { it.seat.index }, "a seat in the round was not sent the roster")
        val roster = issued.first().roster
        for (seat in seats) {
            assertEquals(
                "NAME-${seat.index}", roster.getOrNull(seat.index),
                "seat ${seat.index}'s name is not at index ${seat.index}: $roster",
            )
        }
        assertNull(roster.getOrNull(2), "the seat that is not in the round was handed somebody's name: $roster")
        assertEquals(seats.size, roster.count { it != null }, "the roster does not hold one entry per seat in the round")
    }

    /** One list, the same on every phone: the frame does not vary by seat or by role. */
    @Test
    fun `every phone is sent the same roster`() {
        val issued = rosters()
        assertTrue(issued.all { it.roster == issued.first().roster }, "two phones were sent different rosters")
    }
}
