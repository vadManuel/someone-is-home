package home.someoneshome.app

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Seat
import home.someoneshome.model.Role
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.ui.Carried
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.NotificationKind
import home.someoneshome.ui.PanelVals
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
 * a phone's real model through the house's own card-read path (`RoundAuthority.cardRead`, D-318), the
 * design's mix dealt again with a Memory Dump in every order (D-325), and a cross-player dependency released
 * by the house's own clock onto the downstream phone as `you're up`.
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
            FlowModel(
                PanelState(screen = ScreenId.Boot),
                subroutines = SubroutineModel(send = { up(seat, it) }),
                send = { up(seat, it) },
            )
        }
        private val pending = mutableListOf<Intent>()

        val house: RoundAuthority = RoundAuthority(
            inputs = { inputs },
            emit = { effect, state -> emit(effect, state) },
            note = {},
        )

        /** Every message each phone was sent, in order, as it went down the wire. */
        val said = mutableListOf<Pair<Seat, RoundMessage>>()

        private fun emit(effect: Effect, state: GameState) {
            val message = RoundWire.project(effect)
            for (delivery in EmitSchema.deliveries(effect, state)) {
                said += delivery.seat to message
                phones[delivery.seat]?.houseSaid(message)
            }
        }

        private fun up(seat: Seat, intent: IntentMessage) {
            pending += when (intent) {
                is IntentMessage.HandOverEntry -> Intent.HandOverEntry(seat, intent.marker, intent.entered)
                IntentMessage.StopSubroutine -> Intent.StopSubroutine(seat)
                IntentMessage.BeginCarry -> Intent.BeginCarry(seat)
                else -> return
            }
        }

        /** The phone reads [card] and sends it up, both halves in the scan bridge's order. */
        suspend fun read(seat: Seat, card: MarkerId) {
            phones.getValue(seat).scanning(card)
            house.cardRead(seat, card)
            while (pending.isNotEmpty()) house.heard(pending.removeAt(0))
        }

        /** The last scan answer [seat] was sent, as it went down the wire. */
        fun lastAnswer(seat: Seat): RoundMessage = said.last { it.first == seat && it.second is RoundMessage.ScanAnswered }.second

        /** BEGIN on the phone's caught scan, and whatever it sent up heard by the house (D-325). */
        suspend fun begin(seat: Seat) {
            phones.getValue(seat).beginSubroutine()
            while (pending.isNotEmpty()) house.heard(pending.removeAt(0))
        }

        /** STOP NOW or PROCEED on the phone, and the window's report heard by the house before anything else. */
        suspend fun stop(seat: Seat) {
            phones.getValue(seat).stopSubroutine()
            while (pending.isNotEmpty()) house.heard(pending.removeAt(0))
        }

        /** A station read as a player walks it: the first Spares is briefed, so BEGIN follows it (D-325). */
        suspend fun station(seat: Seat, card: MarkerId) {
            read(seat, card)
            if (phones.getValue(seat).state.circuit != null) begin(seat)
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
                    AssignedWork.ArrayWipe -> repeat(Balance.ARRAY_WIPE_DISKS) { for (m in line.markers) station(seat, m) }
                }
            }
        }

        suspend fun dump(seat: Seat, index: Int) {
            val (source, target) = house.state.workOrderFor(seat)!!.entries[index].markers
            read(seat, source)
            begin(seat)
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
        // The first Spares is briefed (D-325): the caught scan, nothing in hand until BEGIN.
        assertEquals(ScreenId.ScanCaught, phone.state.screen, "the first Spares was not briefed")
        assertNull(phone.state.carrying, "the first Spares put a disk in hand before BEGIN")
        bench.begin(seat)
        assertEquals(Carried("DISK 01", "RACK"), phone.state.carrying, "BEGIN at Spares did not put DISK 01 in this phone's hand")
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
        // D-328: the refusal names the Rack's room, CARRYING . GO TO <ROOM>, and still says *no*.
        assertEquals(bench.house.state.destinations.single { it.marker == rack }.room, PanelVals(phone.state).carryingTo, "the refusal does not name the Rack's room")
        assertEquals(Haptic.Double, bench.lastAnswer(seat).haptic(), "the refusal while carrying did not say no (D-320)")

        bench.read(seat, rack)
        assertEquals(Carried("DISK 01", "DISPOSAL"), phone.state.carrying)
        bench.read(seat, disposal)
        assertNull(phone.state.carrying, "Disposal left the disk in hand")
        for (station in listOf(spares, rack, disposal)) bench.read(seat, station)

        val line = phone.state.order!!.filterIsInstance<OrderLine.Known>().single { it.index == wipe.index }
        assertTrue(line.done, "four traverses did not strike the circuit off this phone's order")
        assertTrue(bench.house.state.workOrderFor(seat)!!.entries[wipe.index].done)
    }

    /**
     * **GUARD (D-328): while a Memory Dump pattern is held, every card but its target is refused on
     * the phone as CARRYING . GO TO <the target's room>, with the *no*; the target still opens.** The
     * owner's 2026-11-23 ruling, on a phone's real model through the house's own card-read path. The
     * source is the one other card that answers (D-330), in the test after this one; the strip's
     * padlock shows throughout (D-330).
     */
    @Test
    fun aHeldPatternRefusesEveryOtherCardWithWhereToGo() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")
        val state = bench.house.state
        fun dumpOf(s: Seat) = state.workOrderFor(s)!!.entries.first { it.work == AssignedWork.MemoryDump }
        val seat = seats.first { s -> state.alone(s, dumpOf(s).index) }
        val dump = dumpOf(seat)
        bench.clear(seat, dump.index)
        val phone = bench.phones.getValue(seat)
        val (source, target) = dump.markers

        bench.read(seat, source)
        bench.begin(seat)
        assertNotNull(bench.house.state.patternOf(seat, dump.index), "BEGIN at the source held no pattern")
        phone.stopSubroutine()
        val targetRoom = state.destinations.single { it.marker == target }.room

        // The padlock shows while the pattern is held, as for a disk (D-330).
        assertTrue(PanelVals(phone.state).lockChip, "the strip shows no padlock while a pattern is held")

        // Another card with work on it: refused, naming the target's room.
        val order = bench.house.state.workOrderFor(seat)!!
        val other = assertNotNull(
            order.entries.firstOrNull { it.work is AssignedWork.Subroutine && order.isActionable(it) && it.anchor != target && it.anchor != source },
            "the seed left this seat no Subroutine to try while the pattern is held",
        )
        val card = other.anchor!!
        bench.read(seat, card)
        assertEquals(ScreenId.ScanBad, phone.state.screen, "${card.value} opened while a pattern was held")
        assertEquals(targetRoom, PanelVals(phone.state).carryingTo, "${card.value}'s refusal does not name the target's room")
        assertEquals(Haptic.Double, bench.lastAnswer(seat).haptic(), "${card.value}'s refusal did not say no (D-320)")
        assertNull(phone.state.carrying, "a held pattern reached Files (F-015)")
        assertNotNull(bench.house.state.patternOf(seat, dump.index), "${card.value} let go of the pattern")
        assertTrue(PanelVals(phone.state).lockChip, "the padlock went with the refusal")

        // The target still opens.
        bench.read(seat, target)
        assertEquals(ScreenId.ScanCaught, phone.state.screen, "the target did not open while the pattern was held")
    }

    /**
     * **The source re-scanned while its pattern is held restarts it** (the owner's 2026-11-23
     * ruling, D-330), on the phone through `RoundAuthority.cardRead`: the source's briefing again,
     * the forgotten pattern let go (the padlock goes and the row reads the source's room), BEGIN
     * plays a fresh pattern that differs, the padlock back, and the target takes the fresh one.
     */
    @Test
    fun theSourceReScannedWhileAPatternIsHeldRestartsItWithAFreshOne() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED), "the bench never armed a round")
        val state = bench.house.state
        fun dumpOf(s: Seat) = state.workOrderFor(s)!!.entries.first { it.work == AssignedWork.MemoryDump }
        val seat = seats.first { s -> state.alone(s, dumpOf(s).index) }
        val dump = dumpOf(seat)
        bench.clear(seat, dump.index)
        val phone = bench.phones.getValue(seat)
        val (source, target) = dump.markers

        bench.read(seat, source)
        bench.begin(seat)
        val forgotten = assertNotNull(bench.house.state.patternOf(seat, dump.index), "BEGIN at the source held no pattern").elements
        bench.stop(seat)
        assertTrue(PanelVals(phone.state).lockChip, "no padlock while the pattern is held")

        repeat(6) { attempt ->
            val held = bench.house.state.patternOf(seat, dump.index)!!.elements
            bench.read(seat, source)
            assertEquals(ScreenId.ScanCaught, phone.state.screen, "attempt $attempt: the source was refused while its pattern was held")
            assertEquals(null, bench.lastAnswer(seat).haptic(), "attempt $attempt: the source said *no*")
            assertNull(bench.house.state.patternOf(seat, dump.index), "attempt $attempt: the re-scan kept the forgotten pattern")
            assertTrue(!PanelVals(phone.state).lockChip, "attempt $attempt: the padlock outlived the pattern the re-scan let go")
            bench.begin(seat)
            assertEquals(ScreenId.SubMemory, phone.state.screen, "attempt $attempt: BEGIN did not play the fresh pattern")
            val fresh = assertNotNull(bench.house.state.patternOf(seat, dump.index), "attempt $attempt: BEGIN held nothing").elements
            assertTrue(fresh != held, "attempt $attempt: the fresh pattern is the forgotten one")
            assertTrue(PanelVals(phone.state).lockChip, "attempt $attempt: no padlock for the fresh pattern")
            bench.stop(seat)
        }
        val pattern = bench.house.state.patternOf(seat, dump.index)!!.elements
        assertTrue(forgotten.isNotEmpty())

        // The target opens against the fresh pattern, and the hand-over lets it go: the padlock too.
        bench.read(seat, target)
        assertEquals(ScreenId.ScanCaught, phone.state.screen, "the target did not open")
        bench.house.heard(Intent.HandOverEntry(seat, target, pattern))
        assertTrue(bench.house.state.workOrderFor(seat)!!.entries[dump.index].done, "the fresh pattern was not the one graded")
        assertNull(bench.house.state.patternOf(seat, dump.index))
        assertTrue(!PanelVals(phone.state).lockChip, "the padlock outlived the hand-over")
    }

    /**
     * **Every order on the bench holds the design's Memory Dump, and every phone names it** (D-325;
     * D-323 A kept it off until its screens were built). The Insider's included.
     */
    @Test
    fun everyOrderOnTheBenchHoldsAMemoryDump() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED))
        assertEquals(true, Balance.MEMORY_DUMP_DEALT, "Memory Dump is not dealt, though its screens are built (D-325)")
        val state = bench.house.state
        for (seat in seats) {
            val order = assertNotNull(state.workOrderFor(seat), "seat ${seat.index} was dealt no order")
            val longs = Balance.designMix(order.entries.size).getValue(home.someoneshome.model.SubroutineStructure.Long)
            assertEquals(longs, order.entries.count { it.work == AssignedWork.MemoryDump }, "seat ${seat.index} holds the wrong number of Memory Dumps")
            val lines = assertNotNull(bench.phones.getValue(seat).state.order, "seat ${seat.index}'s phone holds no order")
            assertEquals(
                order.entries.filter { it.work == AssignedWork.MemoryDump }.map { it.index },
                (lines.filterIsInstance<OrderLine.Known>().filter { it.work == AssignedWork.MemoryDump }.map { it.index } +
                    lines.filterIsInstance<OrderLine.Blocked>().map { it.index }.filter { i -> order.entries[i].work == AssignedWork.MemoryDump }
                    ).sorted(),
                "seat ${seat.index}'s phone does not hold its Memory Dump line",
            )
        }
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

    /**
     * **GUARD (D-324): `you're up` buzzes as the house's text does, on both roles.** Every chain on
     * the bench, a Resident's real one and an Insider's fake hold alike, is released by the house's
     * clock, and what reaches the downstream phone buzzes exactly as the opening text did there.
     */
    @Test
    fun youreUpBuzzesAsTheHouseTextOnBothRoles() = bench { bench ->
        assertTrue(bench.house.arm(RoundCheat.SEED))
        val armed = bench.house.state
        val released = mutableSetOf<Role>()
        for (link in armed.links) {
            if (bench.house.state.workOrderFor(link.up)!!.entries[link.upEntry].done) continue
            if (armed.workOrderFor(link.up)!!.entries[link.upEntry].work !is AssignedWork.Subroutine) continue
            bench.clear(link.up, link.upEntry)
            bench.play(link.up, link.upEntry)
        }
        var clock = HouseClock.started(Tick0)
        val step = 1_000_000_000L / Balance.TICKS_PER_SECOND
        val last = bench.house.state.links.mapNotNull { it.dueAt?.step }.maxOrNull()!!
        while (clock.now.step <= last + Balance.TICKS_PER_SECOND) {
            val turned = clock.turned(step, bench.house.state)
            clock = turned.next
            bench.house.advanceTo(clock.now)
            for (event in turned.due) bench.house.mint(event)
        }
        for (seat in seats) {
            val opening = bench.said.single { it.first == seat && it.second is RoundMessage.OpeningMessage }.second
            for ((_, message) in bench.said.filter { it.first == seat && it.second is RoundMessage.WorkUnblocked }) {
                assertEquals(opening.haptic(), message.haptic(), "seat ${seat.index}'s `you're up` buzzed unlike the house's text")
                assertNotNull(message.haptic(), "`you're up` is silent")
                released += armed.roleOf(seat)
            }
        }
        assertEquals(setOf(Role.Resident, Role.Insider), released, "the bench released `you're up` on only $released")
    }

    private companion object {
        val Tick0 = home.someoneshome.model.Tick(0)
    }
}
