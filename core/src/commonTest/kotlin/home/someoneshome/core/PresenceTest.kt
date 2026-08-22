package home.someoneshome.core

import home.someoneshome.model.ClientClass
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The presence plane** (D-111, D-136).
 *
 * *The work plane hears nothing; the presence plane hears the window.* Two halves of one ruling,
 * and the tests below are split the same way: what the house records, and what it never says.
 */
private val P_SEATS = (0 until 8).map { Seat(it) }
/** Twenty, so that a card holding nothing for a given seat exists — see ScanResolutionTest. */
private val P_MARKERS = (0 until 20).map { MarkerId("m$it") }

private fun round(): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(
        Tick(0), seed = 20260821L, seats = P_SEATS, insiders = listOf(Seat(1)),
        markers = P_MARKERS,
    ),
).state

private fun anchor(state: GameState, seat: Seat): MarkerId =
    state.workOrderFor(seat)!!.entries.first().marker

class PerformanceWindowTest {

    /** Nobody is anywhere until somebody reads a card. Placement is knowledge (D-136). */
    @Test
    fun `a round opens with nobody placed`() {
        assertEquals(emptyList(), round().presence.map { it.seat })
    }

    /** **The window opens on a scan that opened work, and it opens at that card.** */
    @Test
    fun `a work-opening scan opens the window`() {
        val state = round()
        val seat = Seat(0)
        val after = reduce(state, Event.MarkerScanned(Tick(1), seat, anchor(state, seat)))

        val row = assertNotNull(after.state.presenceFor(seat))
        assertTrue(row.open, "a scan that opened work did not open a window")
        assertEquals(anchor(state, seat), row.at)
        assertEquals(
            listOf(Effect.PresenceChanged(seat, anchor(state, seat), open = true)),
            after.effects.filterIsInstance<Effect.PresenceChanged>(),
        )
    }

    /**
     * **A scan that opened nothing places the player and leaves the window shut.**
     *
     * They are somewhere — they read a card whose place the house knows — and they are not
     * performing. Both facts, in one row, because *a performer's own scan placement is ground
     * truth* (D-136) and it is ground truth about a player who is standing there doing nothing
     * just as much as about one who is working.
     */
    @Test
    fun `a scan that opens nothing places the seat with the window shut`() {
        val state = round()
        val seat = Seat(0)
        val mine = state.workOrderFor(seat)!!.entries.map { it.marker.value }.toSet()
        val empty = state.activeMarkers.first { it.value !in mine }

        val row = assertNotNull(
            reduce(state, Event.MarkerScanned(Tick(1), seat, empty)).state.presenceFor(seat),
        )
        assertFalse(row.open, "a card holding nothing opened a performance window")
        assertEquals(empty, row.at, "the house did not place a player who read a card")
    }

    /** **The hand-over closes it** (D-111) — the house grades what arrived and the window shuts. */
    @Test
    fun `handing an entry over closes the window`() {
        val state = round()
        val seat = Seat(0)
        val card = anchor(state, seat)
        val open = reduce(state, Event.MarkerScanned(Tick(1), seat, card)).state
        assertTrue(open.presenceFor(seat)!!.open)

        val returned = reduce(
            open,
            Event.SubroutineReturned(Tick(2), seat, card, open.openSubroutineFor(seat)!!.expected),
        )
        assertFalse(returned.state.presenceFor(seat)!!.open, "the window survived the hand-over")
        assertEquals(
            listOf(Effect.PresenceChanged(seat, card, open = false)),
            returned.effects.filterIsInstance<Effect.PresenceChanged>(),
        )
    }

    /**
     * **STOP NOW closes it, and spends what the scan armed** (D-110, D-111).
     *
     * *There is no half-returned sequence to resume, so the next scan restarts.* An arming that
     * survived the player walking out of the room would refund D-110's whole cost — standing at
     * the marker, in the dark, where the house asked you to stand — and would let an entry be
     * handed over from anywhere.
     */
    @Test
    fun `stopping closes the window and spends the arming`() {
        val state = round()
        val seat = Seat(0)
        val card = anchor(state, seat)
        val open = reduce(state, Event.MarkerScanned(Tick(1), seat, card)).state
        val asked = open.openSubroutineFor(seat)!!.expected

        val stopped = reduce(open, Event.PerformanceEnded(Tick(2), seat))
        assertFalse(stopped.state.presenceFor(seat)!!.open)
        assertFalse(stopped.state.openSubroutineFor(seat)!!.armed, "the arming outlived the walk")

        // And the entry it armed is no longer accepted from anywhere.
        val late = reduce(stopped.state, Event.SubroutineReturned(Tick(3), seat, card, asked))
        assertFalse(
            late.effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted,
            "an entry was accepted after its performer had walked away",
        )
    }

    /**
     * **Scanning somewhere else is walking away, and it costs the same** (D-111, D-136).
     *
     * A scan is a placement, so a seat armed at one card and then reading another is no longer
     * standing where the house asked. Left armed, the player could carry a live question around the
     * house and hand it over from anywhere — which is D-110's cost, standing at the marker in the
     * dark, refunded by walking off.
     */
    @Test
    fun `scanning another card spends what the first one armed`() {
        val state = round()
        val seat = Seat(0)
        val card = anchor(state, seat)
        val open = reduce(state, Event.MarkerScanned(Tick(1), seat, card)).state
        val asked = open.openSubroutineFor(seat)!!.expected

        val mine = state.workOrderFor(seat)!!.entries.map { it.marker.value }.toSet()
        val elsewhere = state.activeMarkers.first { it.value !in mine }
        val moved = reduce(open, Event.MarkerScanned(Tick(2), seat, elsewhere)).state

        assertEquals(elsewhere, moved.presenceFor(seat)!!.at, "the house did not follow the player")
        assertFalse(moved.openSubroutineFor(seat)!!.armed, "the arming followed the player instead")
        assertFalse(
            reduce(moved, Event.SubroutineReturned(Tick(3), seat, card, asked))
                .effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted,
            "an entry was accepted from a player standing at a different card",
        )
    }

    /**
     * **It carries no reason, and one walk away is indistinguishable from three** (D-111).
     *
     * The abandonment record is the behavioural channel that separates a real Subroutine from a
     * fake: somebody who walks away from work that was never going to count, at a rate nobody
     * designed. There is no count to leak because there is no count.
     */
    @Test
    fun `walking away is not counted anywhere`() {
        val state = round()
        val seat = Seat(0)
        val card = anchor(state, seat)

        fun walkAway(times: Int): GameState {
            var s = state
            repeat(times) { i ->
                s = reduce(s, Event.MarkerScanned(Tick(i * 2L + 1), seat, card)).state
                s = reduce(s, Event.PerformanceEnded(Tick(i * 2L + 2), seat)).state
            }
            return s
        }

        val once = walkAway(1).presenceFor(seat)!!
        val thrice = walkAway(3).presenceFor(seat)!!
        assertEquals(once.at, thrice.at)
        assertEquals(once.open, thrice.open)
        assertEquals(
            walkAway(1).workOrderFor(seat)!!.entries.map { it.done },
            walkAway(3).workOrderFor(seat)!!.entries.map { it.done },
            "walking away advanced the order, so abandonment is visible in the work plane",
        )
    }

    /**
     * A report from a seat the house has never placed is quiet and places nobody.
     *
     * The absurd player's case, and the fail-closed direction: closing a window nobody opened
     * cannot invent a location, because the event carries none and *inference never overrides
     * knowledge* (D-136).
     */
    @Test
    fun `a report from a seat that never scanned names no place`() {
        val stopped = reduce(round(), Event.PerformanceEnded(Tick(1), Seat(4)))
        val row = assertNotNull(stopped.state.presenceFor(Seat(4)))
        assertNull(row.at, "closing a window nobody opened invented a location")
        assertFalse(row.open)
    }
}

/**
 * **The house records, never recites** (D-111).
 *
 * Two independent denials, asserted independently, because either alone passes on a build that is
 * wrong the other way.
 */
class PresenceIsNeverRecitedTest {

    /**
     * **The allowlist gives presence no row, so it ships to nobody** (rule 2).
     *
     * The row would be a one-line edit and its consumer — the spectator map's expiry (D-136) — is
     * frozen. Written out in full so that adding `LIVING` fails by name: presence reaching a
     * living seat is the leak the work plane was split in two to close.
     */
    @Test
    fun `presence reaches no client class at all`() {
        assertEquals(emptyList(), EmitSchema.classesFor(EmitSchema.PRESENCE_CHANGED))
        for (role in Role.entries) {
            for (state in RoundState.entries) {
                assertFalse(
                    EmitSchema.permits(EmitSchema.PRESENCE_CHANGED, ClientClass(role, state)),
                    "a presence fact reached $role/$state",
                )
            }
        }
        assertTrue(
            EmitSchema.PRESENCE_CHANGED !in EmitSchema.knownKinds(),
            "presence acquired a row in the allowlist",
        )
    }

    /**
     * **And it is addressed to the performing seat, so a row written by mistake still could not
     * carry seat 3's window to seat 5.**
     *
     * Addressing and the allowlist are separate concerns and neither subsumes the other. This is
     * the half the allowlist cannot see: every living Resident is in one class, so a broadcast
     * presence effect would pass any row somebody wrote.
     */
    @Test
    fun `presence is addressed to the performing seat and to no other`() {
        val state = round()
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.audienceOf(
                Effect.PresenceChanged(Seat(3), P_MARKERS.first(), open = true), state,
            ),
        )
    }

    /**
     * **THE WHOLE-ROUND ASSERTION, and the one that would survive a refactor: over a round in
     * which everybody scans, hands over and walks away, not one delivery to any seat carries
     * anybody's presence.**
     *
     * Read through the emit boundary rather than off the table, because that is where a leak
     * would actually arrive.
     */
    @Test
    fun `no delivery in a whole round carries a presence fact`() {
        var state = round()
        val delivered = mutableListOf<Effect>()
        var t = 1L
        repeat(3) {
            for (seat in P_SEATS) {
                val order = state.workOrderFor(seat)!!
                val card = order.entries.first { order.isActionable(it) }.marker
                for (event in listOf(
                    Event.MarkerScanned(Tick(t++), seat, card),
                    Event.SubroutineReturned(
                        Tick(t++), seat, card,
                        reduce(state, Event.MarkerScanned(Tick(t), seat, card))
                            .state.openSubroutineFor(seat)!!.expected,
                    ),
                    Event.PerformanceEnded(Tick(t++), seat),
                )) {
                    val step = reduce(state, event)
                    state = step.state
                    for (effect in step.effects) {
                        if (EmitSchema.deliveries(effect, state).isNotEmpty()) delivered += effect
                    }
                }
            }
        }

        assertTrue(delivered.isNotEmpty(), "the fixture round delivered nothing at all")
        assertEquals(
            emptyList(),
            delivered.filterIsInstance<Effect.PresenceChanged>(),
            "a presence fact was delivered to a phone",
        )
        assertTrue(
            state.presence.any { it.at != null },
            "the round recorded no presence, so this test proves nothing",
        )
    }
}
