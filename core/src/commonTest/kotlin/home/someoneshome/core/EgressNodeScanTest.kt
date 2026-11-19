package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A node card scanned during an Egress opens no work at the house** (the owner's E2 ruling,
 * D-297), and **a Subroutine already open at a card that became a node is graded as any other**.
 *
 * Every phone routes a scan of one of the running Egress's two cards to the tap control (D-295),
 * whatever the house answered. Before this the house still answered with the seat's work, so the
 * seat held an open instance no phone could reach. Each case is written for a Resident and an
 * Insider, because the card is the only thing the rule may read.
 */
private val SEATS = (0 until 8).map { Seat(it) }
private val INSIDERS = listOf(Seat(1))

/** Twenty cards, so a card holding nothing for a seat exists to compare against (ScanResolutionTest's reason). */
private val MARKERS = (0 until 20).map { MarkerId("m$it") }

private const val PULSE_FROM = 4_200_000L

private fun armed(): GameState = reduce(
    GameState.EMPTY,
    Event.RoundArmed(Tick(0), seed = 20260821L, seats = SEATS, insiders = INSIDERS, markers = placed(MARKERS)),
).state

/** This seat's own first anchor, read off the draw. */
private fun cardFor(state: GameState, seat: Seat): MarkerId =
    state.workOrderFor(seat)!!.entries.first().anchor!!

/** A registered card holding nothing for this seat. */
private fun emptyCardFor(state: GameState, seat: Seat, besides: Set<String> = emptySet()): MarkerId {
    val mine = state.workOrderFor(seat)!!.entries.map { it.anchor!!.value }.toSet()
    return state.activeMarkers.first { it.value !in mine && it.value !in besides }
}

private fun scan(state: GameState, seat: Seat, marker: MarkerId, at: Tick) =
    reduce(state, Event.MarkerScanned(at, seat, marker))

private fun answerOf(effects: List<Effect>): Effect.ScanAnswered =
    effects.filterIsInstance<Effect.ScanAnswered>().single()

/** The Egress fired with [node] as one of its two cards, at the first tick the shared clock allows. */
private fun egressAt(state: GameState, node: MarkerId, other: MarkerId): GameState {
    val fired = reduce(
        state,
        Event.EgressFired(state.egressReadyAt, Seat(1), EgressType.Beacon, listOf(node, other), pulseFrom = PULSE_FROM),
    ).state
    assertTrue(fired.egressRunning, "the fixture's Egress never started")
    return fired
}

/** What the answer is on the socket, at a sequence number a round reaches. */
private fun frame(answer: Effect.ScanAnswered, seq: Long = 4_321L): String =
    TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, RoundWire.project(answer)))))

class EgressNodeScanTest {

    private val roles = listOf(Seat(0) to "Resident", Seat(1) to "Insider")

    @Test
    fun `a node scan opens nothing at the house and answers as an empty card does for either role`() {
        val base = armed()
        val frames = mutableListOf<String>()
        for ((seat, role) in roles) {
            val node = cardFor(base, seat)
            val other = emptyCardFor(base, seat)
            val at = base.egressReadyAt + 1
            val fired = egressAt(base, node, other)

            // The control: the same card, with no Egress, opens this seat's work. Without it the
            // assertion below would pass on a card that never held anything.
            assertNotNull(answerOf(scan(base, seat, node, at).effects).opened, "$role: the fixture's card holds no work")

            val atNode = scan(fired, seat, node, at)
            val empty = scan(fired, seat, emptyCardFor(fired, seat, besides = setOf(other.value)), at)
            assertNull(
                answerOf(atNode.effects).opened,
                "$role: a node scan during an Egress opened a Subroutine the phone cannot reach",
            )
            assertNull(atNode.state.openSubroutineFor(seat)?.armedAt, "$role: the house holds work armed at the node")
            assertEquals(
                empty.effects.map { it::class.simpleName }, atNode.effects.map { it::class.simpleName },
                "$role: a node scan answered in a different shape from an empty card",
            )
            assertEquals(
                frame(answerOf(empty.effects)), frame(answerOf(atNode.effects)),
                "$role: a node scan's answer is not an empty card's on the wire",
            )
            assertEquals(
                listOf(seat), EmitSchema.deliveries(answerOf(atNode.effects), atNode.state).map { it.seat },
                "$role: the node scan's answer reached somebody else",
            )
            // **The scan still places the seat**, which is what containment grades a pair by (D-136).
            val presence = atNode.state.presenceFor(seat)
            assertEquals(node, presence?.at, "$role: the node scan did not place the seat at the node")
            assertEquals(at, presence?.since, "$role: the node scan's placement is not this scan's")
            frames += frame(answerOf(atNode.effects))
        }
        assertEquals(1, frames.toSet().size, "a node scan's answer differs by role: $frames")
        println("D-297 NODE-SCAN ANSWER BYTES AT SEQ 4321, EITHER ROLE, AS AN EMPTY CARD — ${frames.first().length}")
    }

    @Test
    fun `a node scan opens nothing while the Egress is paused and the card is itself again once it ends`() {
        val base = armed()
        for ((seat, role) in roles) {
            val node = cardFor(base, seat)
            val at = base.egressReadyAt + 1
            val fired = egressAt(base, node, emptyCardFor(base, seat))
            val paused = fired.withEgress(fired.egress!!.pausedAt(at))
            assertNull(
                answerOf(scan(paused, seat, node, at + 1).effects).opened,
                "$role: a node scan during a paused Egress opened work",
            )
            val over = fired.withEgress(null)
            assertNotNull(
                answerOf(scan(over, seat, node, at + 1).effects).opened,
                "$role: the card never came back as work once the Egress was over",
            )
        }
    }

    /**
     * **A Subroutine open at a card before it became a node is graded as any other** (E1, and the
     * third case of the brief). Nothing about the Egress reaches `returned`: the instance was armed
     * at the card by the scan before, and the entry is graded against it.
     */
    @Test
    fun `work open at a card that becomes a node is handed over and graded normally`() {
        val base = armed()
        for ((seat, role) in roles) {
            val node = cardFor(base, seat)
            val opened = scan(base, seat, node, Tick(1)).state
            val open = assertNotNull(opened.openSubroutineFor(seat), "$role: the scan opened no work")
            val fired = egressAt(opened, node, emptyCardFor(opened, seat))
            assertEquals(node, fired.openSubroutineFor(seat)?.armedAt, "$role: the Egress took the open work away")

            val returned = reduce(
                fired,
                Event.SubroutineReturned(opened.egressReadyAt + 1, seat, node, open.expected),
            )
            val verdict = returned.effects.filterIsInstance<Effect.SubroutineGraded>().single()
            assertTrue(verdict.accepted, "$role: a right entry at a card that became a node was not accepted")
            assertTrue(
                returned.state.workOrderFor(seat)!!.entries.first { it.index == open.entry }.done,
                "$role: the accepted entry did not mark its line done",
            )
            val banked = returned.state.systemIntegrity < fired.systemIntegrity
            assertEquals(role == "Resident", banked, "$role: the meter moved the wrong way for the role")
        }
    }

    /** The rule D-110 already holds for any scan that opens nothing: work open elsewhere is spent. */
    @Test
    fun `a node scan from a seat with work open at another card spends it as an empty card would`() {
        val base = armed()
        for ((seat, role) in roles) {
            val elsewhere = cardFor(base, seat)
            val node = emptyCardFor(base, seat)
            val opened = scan(base, seat, elsewhere, Tick(1)).state
            val fired = egressAt(opened, node, emptyCardFor(opened, seat, besides = setOf(node.value)))
            val at = opened.egressReadyAt + 1
            val atNode = scan(fired, seat, node, at).state
            val atEmpty = scan(fired, seat, emptyCardFor(fired, seat, besides = setOf(node.value)), at).state
            fun held(state: GameState) = state.openSubroutineFor(seat)
                ?.let { listOf(it.entry, it.subroutine, it.parameters, it.expected, it.armedAt) }
            assertEquals(
                held(atEmpty), held(atNode),
                "$role: a node scan did something else to work open at another card",
            )
            assertNull(atNode.openSubroutineFor(seat)?.armedAt, "$role: the work at the other card is still armed")
        }
    }
}
