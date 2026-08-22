package home.someoneshome.core

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderEntry
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.Tick
import home.someoneshome.model.WorkOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **What a client is allowed to learn about the house it is standing in.**
 *
 * The rules hold the home now — every ordinary card, its glyph and its room — because a work order
 * has to be able to say *where*, and where is the thing a player in a dark house navigates by
 * (E-C2-1). The widening authorised with it was narrow and this file is what holds it there: **a
 * seat may be told the rooms of its own order's lines, and nothing else about the home.**
 *
 * The alternative — the whole marker→room table on every phone — is the map of the round the house
 * has never published, arriving as a convenience. It would tell a Resident where every other
 * player's work could be before anybody had walked anywhere.
 */
class GeographyTest {

    private val seats = (0 until 6).map { Seat(it) }

    /** Twenty cards for six seats, so the home is comfortably wider than any one seat's order. */
    private val home = placed((0 until 20).map { MarkerId("m$it") })

    private fun armed(): Reduction<GameState, Effect> = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = 20260822L, seats = seats, insiders = listOf(Seat(1)),
            markers = home,
        ),
    )

    /**
     * **THE INJECTION: a client never receives geography beyond its own order's rooms.**
     *
     * For every seat, every destination on every line the house issued it must be a card that
     * seat's **own** order names. A build that handed the whole table over — the obvious
     * implementation, and the convenient one — fails here on the first seat.
     *
     * The positive control matters as much: the destinations really arrive, and the set really is
     * smaller than the home. A build that sent nothing would satisfy a subset assertion perfectly.
     */
    @Test
    fun `a seat is told its own order's rooms and no more of the house`() {
        val run = armed()
        val orders = run.effects.filterIsInstance<Effect.WorkOrderIssued>()
        assertEquals(seats.size, orders.size, "one order per seat is the shape arming emits")

        for (issued in orders) {
            val mine = run.state.workOrderFor(issued.seat)!!
                .entries.flatMap { it.markers }.map { it.value }.toSet()
            val told = issued.lines
                .filterIsInstance<OrderLine.Known>()
                .flatMap { it.at }
                .map { it.marker.value }
                .toSet()

            assertTrue(
                told.isNotEmpty(),
                "seat ${issued.seat.index} was told nothing about where its work is; a subset " +
                    "assertion over an empty set proves nothing",
            )
            assertEquals(
                emptySet(), told - mine,
                "seat ${issued.seat.index} was told about cards its own order does not name — " +
                    "that is the house publishing a map of the round",
            )
            assertTrue(
                told.size < home.size,
                "seat ${issued.seat.index} was told about the whole home",
            )
        }
    }

    /**
     * **A blocked line carries nothing, geography included** (D-114, D-146).
     *
     * The room is the newest thing a blocked line could leak and the most plausible: it names no
     * Subroutine, so it reads as harmless — and under cross-player dependencies it points at the
     * card the person **upstream of you** is standing at. The blackout is structural, so this is a
     * type-level guarantee being checked rather than a discipline: `OrderLine.Blocked` has one
     * field, and the assertion is that the house really does produce them.
     */
    @Test
    fun `a blocked line reaches the phone with no room on it`() {
        // One card, so every line after the first is blocked by the one before it (D-123).
        val one = placed(listOf(MarkerId("only")))
        val run = reduce(
            GameState.EMPTY,
            Event.RoundArmed(
                Tick(0), seed = 7L, seats = seats, insiders = listOf(Seat(1)), markers = one,
            ),
        )
        val lines = run.effects.filterIsInstance<Effect.WorkOrderIssued>().first().lines

        assertTrue(
            lines.drop(1).all { it is OrderLine.Blocked },
            "a one-card home did not produce a self-chain, so this proves nothing about blackouts",
        )
        assertTrue(
            lines.first() is OrderLine.Known && (lines.first() as OrderLine.Known).at.isNotEmpty(),
            "the actionable line arrived with no destination on it",
        )
        assertEquals(
            1, lines.count { it is OrderLine.Known },
            "more than the head of the chain was named, so the blackout is not where it was",
        )
    }

    /**
     * **A card the round was not armed with resolves to nothing, visibly.**
     *
     * Fail-closed, and the failure is the one somebody notices: an empty column on every row rather
     * than a room the phone invented. Written against `asLines` directly because the state this
     * describes — an order whose cards are not in the home — is one the draw cannot produce.
     */
    @Test
    fun `a line whose card is not in the home carries no destination`() {
        val order = WorkOrder(
            Seat(0),
            listOf(
                OrderEntry(
                    0, AssignedWork.Subroutine(SubroutineKind.Replay),
                    listOf(MarkerId("nowhere")), emptyList(), done = false,
                ),
            ),
        )
        val line = order.asLines(home).single() as OrderLine.Known
        assertEquals(emptyList(), line.at, "a card the home does not hold was given a room")
    }

    /**
     * **The circuit's three stations ride as its destinations** (D-122, the mix's *1 circuit*).
     *
     * The format has to be able to carry it before the house can ever deal one — and what it
     * carries is the entry's own cards, in draw order, so there is no second list that could
     * disagree with the station draw.
     */
    @Test
    fun `an Array Wipe line carries its three stations in draw order`() {
        val stations = home.take(3).map { it.marker }
        val order = WorkOrder(
            Seat(0),
            listOf(OrderEntry(0, AssignedWork.ArrayWipe, stations, emptyList(), done = false)),
        )
        val line = order.asLines(home).single() as OrderLine.Known

        assertEquals(AssignedWork.ArrayWipe, line.work)
        assertEquals(stations.map { it.value }, line.at.map { it.marker.value })
        assertEquals(
            home.take(3).map { it.room }, line.at.map { it.room },
            "the stations resolved to rooms other than the ones the home holds them in",
        )
    }

    /**
     * **A circuit is not opened by a scan and does not block the card it sits at.**
     *
     * The carried state a circuit leg is graded against is not built (F-014, E7), so the format can
     * hold one and the scan path cannot yet answer one. Stepping over it is the better of the two
     * wrong answers: refusing at the card would turn a station into a blind alley for the real work
     * behind it.
     */
    @Test
    fun `a scan steps over a circuit line and opens the real work behind it`() {
        val card = home.first().marker
        val order = WorkOrder(
            Seat(0),
            listOf(
                OrderEntry(0, AssignedWork.ArrayWipe, listOf(card), emptyList(), done = false),
                OrderEntry(
                    1, AssignedWork.Subroutine(SubroutineKind.Replay),
                    listOf(card), emptyList(), done = false,
                ),
            ),
        )
        assertEquals(1, order.openAt(card)?.index, "the scan did not reach the work behind the circuit")
    }

    /**
     * **The home reaches the rules as recorded input and nothing derives anything from it.**
     *
     * The draw is card-only: two homes with the same cards in different rooms must produce the
     * identical round. If a room name ever reaches a draw, the rules have acquired an opinion about
     * a house they have never been in — and a replay against a recording made in a renamed home
     * would stop matching for reasons nobody could see.
     */
    @Test
    fun `renaming every room changes nothing the house draws`() {
        val renamed = home.map { Destination(it.marker, it.shape, "SOMEWHERE ELSE") }
        fun draw(where: List<Destination>) = reduce(
            GameState.EMPTY,
            Event.RoundArmed(
                Tick(0), seed = 99L, seats = seats, insiders = listOf(Seat(1)), markers = where,
            ),
        ).state

        val a = draw(home)
        val b = draw(renamed)
        assertEquals(a.stations.map { it.value }, b.stations.map { it.value })
        assertEquals(a.activeMarkers.map { it.value }, b.activeMarkers.map { it.value })
        for (seat in seats) {
            assertEquals(
                a.workOrderFor(seat)!!.entries.map { it.work to it.markers.map { m -> m.value } },
                b.workOrderFor(seat)!!.entries.map { it.work to it.markers.map { m -> m.value } },
                "seat ${seat.index}: the draw moved when the rooms were renamed",
            )
        }
    }
}
