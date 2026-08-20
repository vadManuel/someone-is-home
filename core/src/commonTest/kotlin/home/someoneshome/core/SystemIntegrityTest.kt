package home.someoneshome.core

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The SystemIntegrity denominator, which is a win condition (F-005).
 *
 * `7 × initial_residents`, fixed at arming, never moving afterwards. The placeholder 7 is still a
 * placeholder; the *operand* is not a matter of refinement, because counting Insiders makes the
 * meter unreachable and Residents cannot win.
 */
class SystemIntegrityTest {

    private fun armedWith(seats: List<Seat>, insiders: List<Seat>): GameState =
        reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = insiders),
        ).state

    /**
     * **Insiders have no assigned subroutines** (gdd.md:382), so no action an Insider takes ever
     * advances the meter. Counting their seats into the denominator sets a bar higher than the
     * Residents can ever reach — the arithmetic is wrong now, not merely unrefined.
     */
    @Test
    fun `the denominator counts Residents rather than seats`() {
        val seats = (0 until 8).map { Seat(it) }
        val state = armedWith(seats, listOf(Seat(1), Seat(5)))
        assertEquals(6 * 7, state.systemIntegrity, "8 seats, 2 Insiders, so 6 Residents")
    }

    /**
     * The meter must be reachable: every point on it has a Resident subroutine behind it.
     *
     * Stated as the property rather than as the number, so it survives the 7 being replaced.
     */
    @Test
    fun `the meter is reachable by the Residents alone`() {
        for (insiderCount in 1..3) {
            val seats = (0 until 8).map { Seat(it) }
            val insiders = (1..insiderCount).map { Seat(it) }
            val state = armedWith(seats, insiders)
            val residents = seats.size - insiderCount
            val completable = residents * (state.systemIntegrity / residents)
            assertTrue(
                state.systemIntegrity <= completable,
                "$insiderCount Insiders: bar at ${state.systemIntegrity} against $completable " +
                    "completable subroutines — Residents cannot reach zero",
            )
            assertEquals(0, state.systemIntegrity % residents, "the bar must divide by Residents")
        }
    }

    /** An unseated Insider is not a Resident. Only seated players count either way. */
    @Test
    fun `an Insider who is not seated changes nothing`() {
        val seats = (0 until 6).map { Seat(it) }
        assertEquals(
            armedWith(seats, listOf(Seat(1))).systemIntegrity,
            armedWith(seats, listOf(Seat(1), Seat(99))).systemIntegrity,
        )
    }

    /** The denominator never moves after arming (gdd.md:1322). */
    @Test
    fun `the denominator is fixed at arming`() {
        val seats = (0 until 8).map { Seat(it) }
        var state = armedWith(seats, listOf(Seat(1), Seat(5)))
        val atArming = state.systemIntegrity
        state = reduce(state, Event.MeetingCalled(Tick(1), Seat(0))).state
        state = reduce(state, Event.RevokeArmed(Tick(2), Seat(1))).state
        state = reduce(state, Event.ContactMade(Tick(3), Seat(1), Seat(2))).state
        assertEquals(atArming, state.systemIntegrity, "a revocation moved the denominator")
    }
}
