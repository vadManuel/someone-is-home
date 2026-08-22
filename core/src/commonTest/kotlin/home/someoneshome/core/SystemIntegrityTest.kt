package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderBand
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The meter total, under D-130: proportional to SEATS, coefficient owned by playtest.**
 *
 * This replaced the assertions for `7 × initial_residents`, which D-130 explicitly retires. The
 * old operand was not merely a different arithmetic — under D-103 the Insider count can be hidden,
 * and a total built out of `seats − insiders` **is** that count, recoverable by division from any
 * absolute meter value that ever escapes. The percentage-only display rule closes the panel; this
 * closes the arithmetic behind it.
 *
 * Every assertion below is written as the property rather than as the number, so playtest can move
 * the coefficient without rewriting the file.
 */
class SystemIntegrityTest {

    private fun armedWith(
        seats: List<Seat>,
        insiders: List<Seat>,
        markers: List<MarkerId> = emptyList(),
    ): GameState =
        reduce(
            GameState.EMPTY,
            Event.RoundArmed(
                Tick(0), seed = 1L, seats = seats, insiders = insiders, markers = markers,
            ),
        ).state

    private fun seatsOf(count: Int) = (0 until count).map { Seat(it) }

    /**
     * **The total is a function of seats alone — the Insider count cannot be divided back out.**
     *
     * The sharp form of D-130, and the one that would have caught the old arithmetic: arm the same
     * home with every lawful Insider count and the bar must not move. If it does, an absolute meter
     * value is a statement about a number D-103 spent a whole revision hiding.
     */
    @Test
    fun `the total reads seats and never the Insider count`() {
        for (count in 5..16) {
            val seats = seatsOf(count)
            val band = InsiderBand.of(count)
            val totals = band.map { armedWith(seats, seatsOf(it)).systemIntegrity }.distinct()
            assertEquals(
                1, totals.size,
                "$count seats: the bar moved with the Insider count across band $band — $totals, " +
                    "so the hidden count is recoverable from the meter by division",
            )
        }
    }

    /** Proportional, not merely insensitive: twice the seats is twice the bar. */
    @Test
    fun `the total scales with seats`() {
        val six = armedWith(seatsOf(6), listOf(Seat(1))).systemIntegrity
        val twelve = armedWith(seatsOf(12), listOf(Seat(1), Seat(2))).systemIntegrity
        assertTrue(six > 0, "a six-seat home has no bar to complete")
        assertEquals(2 * six, twelve, "the total is not proportional to seats")
    }

    /**
     * **The meter must stay reachable, and under D-130 that rests on D-129 rather than on the
     * operand.**
     *
     * Work-order size is `K = ⌈M ÷ worstCasePlainResidents⌉ + slack`, where
     * `worstCasePlainResidents = seats − bandMax` — computed from public lobby facts only, never
     * from the hidden draw. So the Residents who actually exist are never fewer than the number the
     * order was sized against, and `residents × K ≥ M` whatever the house drew.
     *
     * `K` is drawn at arming now, so this reads [Balance.orderSize] rather than re-deriving it —
     * a test that recomputed the formula would agree with itself after the formula moved.
     */
    @Test
    fun `the bar is reachable by the fewest Residents the band allows`() {
        for (count in Balance.MINIMUM_SEATS..16) {
            val seats = seatsOf(count)
            val bandMax = InsiderBand.of(count).last
            val worstCase = count - bandMax
            val total = armedWith(seats, seatsOf(bandMax)).systemIntegrity
            val k = Balance.orderSize(count, chosenInsiders = null)
            assertTrue(worstCase >= 2, "$count seats: the band leaves fewer than two Residents")
            assertTrue(
                worstCase * k >= total,
                "$count seats: $worstCase Residents at $k each cannot reach a bar of $total",
            )
        }
    }

    /**
     * **D-129 — the order is sized off the PUBLIC setting and never off the draw.**
     *
     * The sharp form, and the counterpart of the meter test above: hold the seats still, move the
     * host's visible setting through the whole band, and the length may change with it — that is
     * public. Then hold the setting at UNKNOWN and move what the house actually drew, and the
     * length must not move at all. If it did, order length would divide out the count D-103 spent
     * a whole revision hiding.
     */
    @Test
    fun `the order length reads the setting and never the draw`() {
        val markers = (0 until 8).map { MarkerId("m$it") }
        for (count in Balance.MINIMUM_SEATS..16) {
            val seats = seatsOf(count)
            val lengths = InsiderBand.of(count).map { drawn ->
                reduce(
                    GameState.EMPTY,
                    Event.RoundArmed(
                        Tick(0), seed = 1L, seats = seats, insiders = seatsOf(drawn),
                        chosenInsiders = null, markers = markers,
                    ),
                ).state.workOrders.map { it.entries.size }.distinct()
            }.distinct()
            assertEquals(
                listOf(listOf(Balance.orderSize(count, null))), lengths,
                "$count seats: order length moved with the hidden draw, or differed between " +
                    "seats — either way the count is recoverable by counting a work order",
            )
        }
    }

    /** An unseated Insider is not seated. Only seated players count, and now only as seats. */
    @Test
    fun `an unseated Insider changes nothing`() {
        val seats = seatsOf(6)
        assertEquals(
            armedWith(seats, listOf(Seat(1))).systemIntegrity,
            armedWith(seats, listOf(Seat(1), Seat(99))).systemIntegrity,
        )
    }

    /** The denominator never moves after arming (gdd.md:1322). */
    @Test
    fun `the denominator is fixed at arming`() {
        val seats = seatsOf(8)
        var state = armedWith(seats, listOf(Seat(1), Seat(5)))
        val atArming = state.systemIntegrity
        state = reduce(state, Event.MeetingCalled(Tick(1), Seat(0), MeetingTrigger.MeetingCard)).state
        state = reduce(state, Event.RevokeArmed(Tick(2), Seat(1))).state
        state = reduce(state, Event.ContactMade(Tick(3), Seat(1), Seat(2))).state
        assertEquals(atArming, state.systemIntegrity, "a revocation moved the denominator")
    }

    /**
     * **D-109's one asymmetry, read off the meter: a plain Resident banks, an Insider does not.**
     *
     * `gdd.md:382` — Insiders have no assigned Subroutines and no action an Insider takes ever
     * advances the meter. The fake is real work, graded honestly, counting for nothing.
     */
    @Test
    fun `only a plain Resident's accepted entry moves the meter`() {
        val seats = seatsOf(8)
        val armed = armedWith(seats, listOf(Seat(1)), (0 until 8).map { MarkerId("m$it") })

        // The card and the answer are read off the draw rather than typed in: the house decides
        // where a seat's work is, and a fixture that named a card would be asserting against its
        // own idea of the draw instead of against the draw.
        fun walk(seat: Seat): GameState {
            val entry = armed.workOrderFor(seat)!!.entries.first()
            val scanned = reduce(armed, Event.MarkerScanned(Tick(1), seat, entry.marker)).state
            return reduce(
                scanned,
                Event.SubroutineReturned(Tick(2), seat, entry.marker, entry.expected),
            ).state
        }

        val resident = walk(Seat(0))
        val insider = walk(Seat(1))
        assertEquals(Role.Resident, armed.roleOf(Seat(0)))
        assertEquals(Role.Insider, armed.roleOf(Seat(1)))
        assertEquals(
            armed.systemIntegrity - 1, resident.systemIntegrity,
            "a Resident's accepted entry did not bank",
        )
        assertEquals(
            armed.systemIntegrity, insider.systemIntegrity,
            "an Insider's fake advanced the meter (gdd.md:382)",
        )
    }
}
