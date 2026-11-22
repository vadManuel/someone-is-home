package home.someoneshome.app

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.ui.Carried
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.NotificationKind
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.SubroutineModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A bench round where somebody finishes each new piece of work** (D-322): an Array Wipe walked on
 * a phone's real model through the house's own card-read path (`RoundAuthority.cardRead`, D-318), a
 * Memory Dump handed over at its target, and a cross-player dependency released by the house's own
 * clock onto the downstream phone as `you're up`.
 *
 * Real: the rules, the draw, the gate, `EmitSchema`'s allowlist, `RoundWire.project`, `FlowModel`.
 * What stands in is the socket, as in [HandOverLoopTest]: the projected message is handed straight
 * to the addressed seat's model.
 */
class StructuredWorkBenchTest {

    private val seats = List(6) { Seat(it) }

    private val inputs = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
        discussionSeconds = Balance.DISCUSSION_SECONDS,
        egressTimerSeconds = Balance.EGRESS_TIMER_SECONDS,
        revokeCooldownSeconds = Balance.REVOKE_COOLDOWN_SECONDS,
    )

    private class Bench(inputs: ArmingInputs) {
        val phones: Map<Seat, FlowModel> = inputs.seats.associateWith { seat ->
            FlowModel(PanelState(screen = ScreenId.Boot), subroutines = SubroutineModel(send = { up(seat, it) }))
        }
        private val pending = mutableListOf<Intent>()

        val house: RoundAuthority = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state -> emit(effect, state) },
            note = {},
        )

        private fun emit(effect: Effect, state: GameState) {
            val message = RoundWire.project(effect)
            for (delivery in EmitSchema.deliveries(effect, state)) phones[delivery.seat]?.houseSaid(message)
        }

        private fun up(seat: Seat, intent: IntentMessage) {
            pending += when (intent) {
                is IntentMessage.HandOverEntry -> Intent.HandOverEntry(seat, intent.marker, intent.entered)
                IntentMessage.StopSubroutine -> Intent.StopSubroutine(seat)
                else -> return
            }
        }

        /** The phone reads [card] and sends it up, both halves in the scan bridge's order. */
        suspend fun read(seat: Seat, card: MarkerId) {
            phones.getValue(seat).scanning(card)
            house.cardRead(seat, card)
            while (pending.isNotEmpty()) house.heard(pending.removeAt(0))
        }

        /** Finish a Subroutine line the way [HandOverLoopTest] does, by intents at its card. */
        suspend fun play(seat: Seat, index: Int) {
            val line = house.state.workOrderFor(seat)!!.entries[index]
            val card = line.anchor!!
            read(seat, card)
            val open = assertNotNull(house.state.openSubroutineFor(seat), "nothing opened at line $index")
            house.heard(Intent.HandOverEntry(seat, card, open.expected))
        }

        /** Finish whatever [index] is chained behind on its own seat's order. */
        suspend fun clear(seat: Seat, index: Int) {
            for (b in house.state.workOrderFor(seat)!!.entries[index].blockedBy) {
                if (house.state.workOrderFor(seat)!!.entries[b].done) continue
                clear(seat, b)
                val line = house.state.workOrderFor(seat)!!.entries[b]
                when (line.work) {
                    is AssignedWork.Subroutine -> play(seat, b)
                    AssignedWork.MemoryDump -> dump(seat, b)
                    AssignedWork.ArrayWipe -> repeat(Balance.ARRAY_WIPE_DISKS) { for (m in line.markers) read(seat, m) }
                }
            }
        }

        suspend fun dump(seat: Seat, index: Int) {
            val (source, target) = house.state.workOrderFor(seat)!!.entries[index].markers
            read(seat, source)
            val pattern = house.state.patternOf(seat, index)!!.elements
            read(seat, target)
            house.heard(Intent.HandOverEntry(seat, target, pattern))
        }
    }

    private fun bench(block: suspend (Bench) -> Unit) = runBlocking { block(Bench(inputs)) }

    private fun GameState.alone(seat: Seat, index: Int): Boolean {
        val line = workOrderFor(seat)!!.entries[index]
        return !line.held && line.blockedBy.all { alone(seat, it) }
    }

    @Test
    fun anArrayWipeIsWalkedOnThePhoneAndNothingElseOpensWhileTheDiskIsInHand() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")
        val state = bench.house.state
        val seat = seats.first { s -> state.alone(s, state.workOrderFor(s)!!.entries.single { it.work == AssignedWork.ArrayWipe }.index) }
        val wipe = state.workOrderFor(seat)!!.entries.single { it.work == AssignedWork.ArrayWipe }
        bench.clear(seat, wipe.index)
        val phone = bench.phones.getValue(seat)
        val (spares, rack, disposal) = wipe.markers

        bench.read(seat, spares)
        assertEquals(Carried("DISK 01", "RACK"), phone.state.carrying, "Spares did not put DISK 01 in this phone's hand")
        assertEquals(ScreenId.Home, phone.state.screen, "a station step landed somewhere other than the widget")

        // While carrying: a Subroutine of this order, actionable, is NOTHING FOR YOU HERE.
        val order = bench.house.state.workOrderFor(seat)!!
        val other = assertNotNull(
            order.entries.firstOrNull { it.work is AssignedWork.Subroutine && order.isActionable(it) },
            "the seed left this seat no Subroutine to try while carrying, so the block is unexercised",
        )
        bench.read(seat, other.anchor!!)
        assertEquals(ScreenId.ScanBad, phone.state.screen, "a Subroutine opened while a disk was in hand")
        assertEquals(Carried("DISK 01", "RACK"), phone.state.carrying, "the disk moved at another card")
        assertNull(bench.house.state.openSubroutineFor(seat)?.takeIf { it.armed }, "the house armed work behind the disk")

        bench.read(seat, rack)
        assertEquals(Carried("DISK 01", "DISPOSAL"), phone.state.carrying)
        bench.read(seat, disposal)
        assertNull(phone.state.carrying, "Disposal left the disk in hand")
        for (station in listOf(spares, rack, disposal)) bench.read(seat, station)

        val line = phone.state.order!!.filterIsInstance<OrderLine.Known>().single { it.index == wipe.index }
        assertTrue(line.done, "four traverses did not strike the circuit off this phone's order")
        assertTrue(bench.house.state.workOrderFor(seat)!!.entries[wipe.index].done)
    }

    @Test
    fun aMemoryDumpIsShownAtItsSourceAndGradedAtItsTarget() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED))
        val state = bench.house.state
        val seat = seats.first { s -> state.alone(s, state.workOrderFor(s)!!.entries.first { it.work == AssignedWork.MemoryDump }.index) }
        val dump = state.workOrderFor(seat)!!.entries.first { it.work == AssignedWork.MemoryDump }
        bench.clear(seat, dump.index)
        val phone = bench.phones.getValue(seat)

        bench.read(seat, dump.markers[0])
        // ⚠️ STOPPED (D-322): no screen draws a Memory Dump, so the phone opens nothing over it.
        assertNull(phone.state.carrying, "a pattern reached the carry, which Files draws (F-015)")
        val pattern = assertNotNull(bench.house.state.patternOf(seat, dump.index)).elements
        bench.read(seat, dump.markers[1])
        val graded = bench.house.emitted.count { it is Effect.SubroutineGraded }
        bench.house.heard(Intent.HandOverEntry(seat, dump.markers[1], pattern))
        assertEquals(graded + 1, bench.house.emitted.count { it is Effect.SubroutineGraded })
        assertTrue(bench.house.state.workOrderFor(seat)!!.entries[dump.index].done, "the Memory Dump did not finish")
        assertTrue(phone.state.order!!.filterIsInstance<OrderLine.Known>().single { it.index == dump.index }.done)
    }

    @Test
    fun aDependencyIsReleasedByTheHousesClockOntoTheDownstreamPhone() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED))
        val state = bench.house.state
        val link = state.links.first { state.workOrderFor(it.up)!!.entries[it.upEntry].work is AssignedWork.Subroutine }
        val downstream = bench.phones.getValue(link.down)
        assertTrue(downstream.state.order!![link.downEntry] is OrderLine.Blocked, "the held line reached the phone named")

        bench.clear(link.up, link.upEntry)
        bench.play(link.up, link.upEntry)
        val due = assertNotNull(bench.house.state.links.single { it.isDown(link.down, link.downEntry) }.dueAt)

        // The house's clock, pumped a step at a time to the deadline and a little past it.
        var clock = HouseClock.started(Tick0)
        val step = 1_000_000_000L / Balance.TICKS_PER_SECOND
        while (clock.now.step <= due.step + Balance.TICKS_PER_SECOND) {
            val turned = clock.turned(step, bench.house.state)
            clock = turned.next
            bench.house.advanceTo(clock.now)
            for (event in turned.due) bench.house.mint(event)
        }
        assertTrue(downstream.state.order!![link.downEntry] is OrderLine.Known, "the released line is still blocked on the phone")
        val text = assertNotNull(downstream.state.lastQuiet, "the downstream phone was not told")
        assertEquals(NotificationKind.Unblocked, text.kind)
        assertEquals("you're up", text.body)
        assertEquals("NUMBER WITHHELD", text.from)
        assertTrue(bench.house.emitted.none { it is Effect.WorkUnblocked && it.seat == link.up }, "the upstream seat was told the unlock happened")
    }

    private companion object {
        val Tick0 = home.someoneshome.model.Tick(0)
    }
}
