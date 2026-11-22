package home.someoneshome.core

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.CircuitStation
import home.someoneshome.model.DiskInHand
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MemoryPattern
import home.someoneshome.model.OrderEntry
import home.someoneshome.model.OrderLine
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.SubroutineStructure
import home.someoneshome.model.Tick
import home.someoneshome.model.WorkOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * **D-322: the work order as the design draws it** — the mix scaled to `K`, the cross-player
 * dependencies, the Array Wipe circuit and Memory Dump, and the carry that ties the last two
 * together. Every test here drives the real rules (`reduce`, `admit`) and reads what they emitted.
 */

private val CARDS = (0 until 16).map { MarkerId("c$it") }
private fun seatsOf(n: Int) = (0 until n).map { Seat(it) }

/** A round of [seats], with [insiders], drawn from [seed] in a home of [cards]. */
private fun armed(
    seats: Int = 6,
    insiders: List<Seat> = listOf(Seat(1)),
    seed: Long = 20261121L,
    cards: List<MarkerId> = CARDS,
) = reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed, seatsOf(seats), insiders, null, placed(cards)))

/** A clock a test advances by hand, one tick per event. */
private class Clock(var t: Long = 1L) {
    fun next(): Tick = Tick(t++)
}

private fun scan(state: GameState, seat: Seat, card: MarkerId, clock: Clock) =
    reduce(state, Event.MarkerScanned(clock.next(), seat, card))

private fun hand(state: GameState, seat: Seat, card: MarkerId, entered: List<Int>, clock: Clock) =
    reduce(state, Event.SubroutineReturned(clock.next(), seat, card, entered))

/** The disk in [seat]'s hand after the last scan, off the house's own answer. */
private fun carried(effects: List<Effect>): DiskInHand? =
    effects.filterIsInstance<Effect.ScanAnswered>().single().carrying

/**
 * **Finish [entry] of [seat]'s order the way a player would**, first finishing whatever of its own
 * order it is chained behind. A Subroutine: scan its card and hand over the answer. A Memory Dump:
 * the source, then the target with the pattern. An Array Wipe: Spares, Rack, Disposal, twice.
 */
private fun finish(start: GameState, seat: Seat, entry: Int, clock: Clock): GameState {
    var state = start
    val line = state.workOrderFor(seat)!!.entries[entry]
    for (b in line.blockedBy) {
        if (!state.workOrderFor(seat)!!.entries[b].done) state = finish(state, seat, b, clock)
    }
    when (line.work) {
        is AssignedWork.Subroutine -> {
            val card = line.anchor!!
            state = scan(state, seat, card, clock).state
            val open = assertNotNull(state.openSubroutineFor(seat), "nothing opened at entry $entry's card")
            assertEquals(entry, open.entry, "the card opened a different line")
            state = hand(state, seat, card, open.expected, clock).state
        }
        AssignedWork.MemoryDump -> {
            state = scan(state, seat, line.markers[0], clock).state
            val pattern = assertNotNull(state.patternOf(seat, entry), "the source held no pattern").elements
            state = scan(state, seat, line.markers[1], clock).state
            state = hand(state, seat, line.markers[1], pattern, clock).state
        }
        AssignedWork.ArrayWipe -> repeat(Balance.ARRAY_WIPE_DISKS) {
            for (station in line.markers) state = scan(state, seat, station, clock).state
        }
    }
    assertTrue(state.workOrderFor(seat)!!.entries[entry].done, "entry $entry of seat ${seat.index} did not finish")
    return state
}

/**
 * Whether this seat can finish [entry] alone: it is not held by another seat's work, and neither is
 * anything of its own order it is chained behind.
 */
private fun GameState.alone(seat: Seat, entry: Int): Boolean {
    val line = workOrderFor(seat)!!.entries[entry]
    return !line.held && line.blockedBy.all { alone(seat, it) }
}

private fun structureOf(entry: OrderEntry): SubroutineStructure = when (val work = entry.work) {
    is AssignedWork.Subroutine -> work.kind.structure
    AssignedWork.MemoryDump -> SubroutineStructure.Long
    AssignedWork.ArrayWipe -> SubroutineStructure.Circuit
}

/** Everything the draw put in a round, as text, for comparing two rounds. */
private fun drawn(state: GameState): String = state.workOrders.joinToString("\n") { order ->
    "${order.seat.index}: " + order.entries.joinToString(" ") { e ->
        "${e.index}/${e.work}/${e.markers.joinToString("+") { it.value }}/${e.blockedBy}/${e.held}"
    }
} + "\n" + state.links.joinToString(" ") { "${it.up.index}.${it.upEntry}>${it.down.index}.${it.downEntry}:${it.delay}" }

/** The effects [seat]'s phone is delivered, in order. */
private fun deliveredTo(seat: Seat, effects: List<Effect>, state: GameState): List<Effect> =
    effects.filter { e -> EmitSchema.deliveries(e, state).any { it.seat.index == seat.index } }

/**
 * **A Memory Dump put into [seat]'s order by hand** (D-323 A). The mix deals none while
 * [Balance.MEMORY_DUMP_DEALT] is off, so Memory Dump's house half is tested on a line planted here
 * rather than one the draw dealt. The first Subroutine line of the order that nothing holds and
 * nothing of its own is chained before becomes a Memory Dump from its own card to the first card of
 * [cards] the order does not use, so the planted target chains nothing. Nothing here reads a role:
 * two rounds that differ only in who is an Insider get the same line.
 */
private fun GameState.withMemoryDump(seat: Seat, cards: List<MarkerId> = CARDS): GameState {
    val order = workOrderFor(seat)!!
    val line = order.entries.first { it.work is AssignedWork.Subroutine && !it.held && it.blockedBy.isEmpty() }
    val target = cards.first { c -> order.entries.none { it.isAt(c) } }
    val planted = OrderEntry(
        index = line.index,
        work = AssignedWork.MemoryDump,
        markers = listOf(line.anchor!!, target),
        blockedBy = emptyList(),
        done = false,
    )
    return withWorkOrder(WorkOrder(seat, order.entries.map { if (it.index == line.index) planted else it }))
}

// =================================================================================================

class OrderMixTest {

    @Test
    fun `the mix sums to K for every K and keeps every structure within one entry of its share`() {
        val total = Balance.ORDER_MIX_WEIGHTS.sumOf { it.second }
        assertEquals(7, total, "the design's mix is seven parts: 1 circuit 1 long 3 medium 2 short")
        for (k in 0..60) {
            val mix = Balance.designMix(k)
            assertEquals(k, mix.values.sum(), "an order of $k was dealt ${mix.values.sum()}")
            for ((structure, weight) in Balance.ORDER_MIX_WEIGHTS) {
                // |count − K·w/7| < 1, in sevenths so nothing is rounded.
                val off = mix.getValue(structure) * total - k * weight
                assertTrue(off > -total && off < total, "at K=$k $structure is ${mix[structure]}, not within one of $k×$weight/7")
            }
        }
    }

    @Test
    fun `an order of seven is the design's own mix and the K a lobby computes round as stated`() {
        fun mix(k: Int) = Balance.designMix(k).let {
            listOf(it[SubroutineStructure.Circuit], it[SubroutineStructure.Long], it[SubroutineStructure.Medium], it[SubroutineStructure.Short])
        }
        assertEquals(listOf(1, 1, 3, 2), mix(7), "K=7 is not D-005's 1 circuit 1 long 3 medium 2 short")
        assertEquals(listOf(1, 1, 2, 2), mix(6))
        assertEquals(listOf(1, 1, 4, 2), mix(8))
        assertEquals(listOf(1, 1, 4, 3), mix(9))
        // The one tie with these weights is circuit against long, and it goes to the long.
        assertEquals(listOf(1, 2, 4, 3), mix(10))
    }

    @Test
    fun `the ten are classified as the roster rates them and Memory Dump is the long`() {
        assertEquals(
            listOf(
                SubroutineKind.Replay, SubroutineKind.Interrupt, SubroutineKind.ParityCheck,
                SubroutineKind.Sniff, SubroutineKind.Deallocate, SubroutineKind.Short,
            ),
            SubroutineKind.dealtAs(SubroutineStructure.Short),
            "gdd.md's roster table rates these six short",
        )
        assertEquals(
            listOf(SubroutineKind.Drift, SubroutineKind.SignalTrace, SubroutineKind.Jam, SubroutineKind.Handshake),
            SubroutineKind.dealtAs(SubroutineStructure.Medium),
            "gdd.md's roster table rates these four medium",
        )
        assertEquals(listOf(SubroutineKind.MemoryDump), SubroutineKind.dealtAs(SubroutineStructure.Long))
        assertEquals(emptyList(), SubroutineKind.dealtAs(SubroutineStructure.Circuit), "the circuit is not a kind")
    }

    @Test
    fun `every order in a round holds the mix and the Insider's fake holds it too`() {
        for (seats in listOf(5, 6, 8)) {
            val insider = Seat(2)
            val state = armed(seats = seats, insiders = listOf(insider)).state
            val size = Balance.orderSize(seats, null)
            val mix = Balance.orderMix(size)
            for (order in state.workOrders) {
                assertEquals(size, order.entries.size, "seat ${order.seat.index}'s order is not K long")
                val counts = SubroutineStructure.entries.associateWith { s -> order.entries.count { structureOf(it) == s } }
                assertEquals(mix, counts, "seat ${order.seat.index} (${state.roleOf(order.seat)}) does not hold the mix")
                for (e in order.entries) {
                    when (e.work) {
                        AssignedWork.ArrayWipe -> assertEquals(state.stations, e.markers, "a circuit is not at the round's stations")
                        AssignedWork.MemoryDump -> {
                            assertEquals(2, e.markers.size)
                            assertNotEquals(e.markers[0].value, e.markers[1].value, "a Memory Dump's target is its source")
                        }
                        is AssignedWork.Subroutine -> assertEquals(1, e.markers.size)
                    }
                }
            }
            assertTrue(state.workOrders.any { it.seat == insider }, "the Insider was dealt no order")
        }
    }

    /**
     * **GUARD (D-323 A): no Memory Dump is dealt while its screens are unbuilt.** The owner's
     * 2026-11-22 ruling: no phone can open one, so dealt into every order it left every seat a line
     * nobody could finish and the meter out of reach. While [Balance.MEMORY_DUMP_DEALT] is off the
     * long's share is medium, every order still sums to `K`, and no order of any round, the
     * Insider's fake included, holds a Memory Dump.
     */
    @Test
    fun `no dealt order holds a Memory Dump while the switch is off`() {
        assertEquals(false, Balance.MEMORY_DUMP_DEALT, "Memory Dump is dealt: its screens must exist first (D-323)")
        for (k in 0..60) {
            val design = Balance.designMix(k)
            val dealt = Balance.orderMix(k)
            assertEquals(k, dealt.values.sum(), "an order of $k was dealt ${dealt.values.sum()}")
            assertEquals(0, dealt.getValue(SubroutineStructure.Long), "the mix deals a long at K=$k")
            assertEquals(
                design.getValue(SubroutineStructure.Medium) + design.getValue(SubroutineStructure.Long),
                dealt.getValue(SubroutineStructure.Medium),
                "the long's share at K=$k did not go to medium",
            )
            assertEquals(design.getValue(SubroutineStructure.Circuit), dealt.getValue(SubroutineStructure.Circuit))
            assertEquals(design.getValue(SubroutineStructure.Short), dealt.getValue(SubroutineStructure.Short))
        }
        for (seats in 3..18) {
            for (seed in listOf(1L, 77L, 20261121L, 20261122L)) {
                val insiders = listOf(Seat(seats - 1)) + if (seats >= 8) listOf(Seat(1)) else emptyList()
                val state = armed(seats = seats, insiders = insiders, seed = seed).state
                assertEquals(seats, state.workOrders.size, "a seat of $seats was dealt no order")
                for (order in state.workOrders) {
                    assertTrue(
                        order.entries.none { it.work == AssignedWork.MemoryDump },
                        "seat ${order.seat.index} of $seats (${state.roleOf(order.seat)}, seed $seed) was dealt a Memory Dump",
                    )
                }
            }
        }
    }
}

// =================================================================================================

class CrossPlayerChainTest {

    @Test
    fun `every seat is upstream of one other and downstream of one other and never of itself`() {
        val state = armed(seats = 8, insiders = listOf(Seat(1), Seat(5))).state
        assertEquals(8, state.links.size, "one link per seat")
        assertEquals((0 until 8).toList(), state.links.map { it.up.index }.sorted(), "a seat is upstream twice or never")
        assertEquals((0 until 8).toList(), state.links.map { it.down.index }.sorted(), "a seat is downstream twice or never")
        for (link in state.links) {
            assertNotEquals(link.up.index, link.down.index, "a dependency joined a seat to itself")
            assertTrue(state.workOrderFor(link.down)!!.entries[link.downEntry].held, "the downstream line is not held")
            val ownDown = state.links.single { it.down == link.up }.downEntry
            assertTrue(link.upEntry < ownDown, "an upstream line is after its own seat's held line, which could deadlock")
            val seconds = link.delay / Balance.TICKS_PER_SECOND
            assertTrue(seconds in 20L..120L, "an unlock delay of ${seconds}s is outside gdd.md's 20–120 s")
        }
    }

    @Test
    fun `a blocked entry reaches its phone as a blocked line and the upstream seat is told someone waits`() {
        val reduction = armed(seats = 6)
        val state = reduction.state
        for (link in state.links) {
            val issued = reduction.effects.filterIsInstance<Effect.WorkOrderIssued>().single { it.seat == link.down }
            assertIs<OrderLine.Blocked>(issued.lines[link.downEntry], "the held line reached its phone named")
            assertEquals(state.workOrderFor(link.down)!!.entries.size, issued.lines.size, "the order shortened")
        }
        val waiting = reduction.effects.filterIsInstance<Effect.UpstreamWaiting>()
        assertEquals(state.links.sortedBy { it.up.index }.map { it.up to it.upEntry }, waiting.map { it.seat to it.entry })
    }

    @Test
    fun `a chain unlocks across players after its delay and not a tick before`() {
        var state = armed(seats = 6).state
        // A downstream line nothing of its own order is chained before, so the release alone names
        // it on the wire (D-323 A's mix moved which lines the seed chains; a line still behind its
        // own earlier work is rightly Blocked after the release too).
        val link = state.links.first {
            state.roleOf(it.up) == home.someoneshome.model.Role.Resident &&
                state.workOrderFor(it.down)!!.entries[it.downEntry].blockedBy.isEmpty()
        }
        val clock = Clock()
        state = finish(state, link.up, link.upEntry, clock)
        val started = state.links.single { it.isDown(link.down, link.downEntry) }
        val due = assertNotNull(started.dueAt, "completing the upstream line did not start the delay")
        assertTrue(due.step > clock.t - 1, "the delay did not start at the completion")

        val early = admit(state, Event.ChainUnlocked(Tick(due.step - 1), link.down, link.downEntry))
        assertEquals(Admission.Refused(RefusalReason.ChainNotDue), early, "the line came free before its delay ran out")

        val released = assertIs<Admission.Admitted>(admit(state, Event.ChainUnlocked(due, link.down, link.downEntry)))
        val after = released.reduction.state
        val effects = released.reduction.effects
        assertEquals(listOf(link.down, link.down), effects.map {
            when (it) { is Effect.WorkOrderIssued -> it.seat; is Effect.WorkUnblocked -> it.seat; else -> Seat(-1) }
        }, "the release spoke to somebody other than the downstream seat: $effects")
        val lines = effects.filterIsInstance<Effect.WorkOrderIssued>().single().lines
        assertIs<OrderLine.Known>(lines[link.downEntry], "the released line is still blocked on the wire")
        assertTrue(after.workOrderFor(link.down)!!.isActionable(after.workOrderFor(link.down)!!.entries[link.downEntry]) ||
            after.workOrderFor(link.down)!!.entries[link.downEntry].blockedBy.isNotEmpty())

        val twice = admit(after, Event.ChainUnlocked(Tick(due.step + 5), link.down, link.downEntry))
        assertEquals(Admission.Refused(RefusalReason.ChainNotDue), twice, "a released line was released again")
    }

    @Test
    fun `a Revoke strands downstream work and nothing ever releases it`() {
        val armedState = armed(seats = 6).state
        val link = armedState.links.first()
        // The upstream seat is out before it finishes: revoked, as the touch would leave it.
        var state = armedState.copy(revoked = listOf(link.up))
        val clock = Clock()
        val line = state.workOrderFor(link.up)!!.entries[link.upEntry]
        val card = line.anchor!!
        state = scan(state, link.up, card, clock).state
        state = hand(state, link.up, card, listOf(0), clock).state
        assertNull(state.links.single { it.isDown(link.down, link.downEntry) }.dueAt, "an out seat's work started a delay")
        for (later in listOf(20L, 2_400L, 1_000_000L).map { Tick(it * Balance.TICKS_PER_SECOND) }) {
            assertEquals(
                Admission.Refused(RefusalReason.ChainNotDue),
                admit(state, Event.ChainUnlocked(later, link.down, link.downEntry)),
                "a stranded line was released at $later — nothing auto-satisfies (D-146)",
            )
        }
        assertIs<OrderLine.Blocked>(state.workOrderFor(link.down)!!.asLines(state.destinations)[link.downEntry])
    }

    @Test
    fun `an Insider's completion unlocks the downstream seat exactly as a Resident's does`() {
        fun run(insiders: List<Seat>): Pair<Tick?, List<Effect>> {
            var state = armed(seats = 6, insiders = insiders).state
            val link = state.links.first { it.up == Seat(3) }
            state = finish(state, link.up, link.upEntry, Clock())
            val due = state.links.single { it.isDown(link.down, link.downEntry) }.dueAt!!
            val released = (admit(state, Event.ChainUnlocked(due, link.down, link.downEntry)) as Admission.Admitted).reduction
            return due to deliveredTo(link.down, released.effects, released.state)
        }
        val asInsider = run(listOf(Seat(3)))
        val asResident = run(listOf(Seat(1)))
        assertEquals(asResident, asInsider, "the downstream seat could tell its upstream was an Insider")
    }
}

// =================================================================================================

class ArrayWipeTest {

    private fun circuitOf(state: GameState, seat: Seat): OrderEntry =
        state.workOrderFor(seat)!!.entries.single { it.work == AssignedWork.ArrayWipe }

    /** Clear whatever the circuit is chained behind, so the four traverses are the circuit's own. */
    private fun ready(seat: Seat, seed: Long = 20261121L): Pair<GameState, Clock> {
        var state = armed(seats = 6, seed = seed).state
        val clock = Clock()
        val wipe = circuitOf(state, seat)
        for (b in wipe.blockedBy) state = finish(state, seat, b, clock)
        return state to clock
    }

    @Test
    fun `two damaged disks are four traverses and the line is done at the second disposal`() {
        val seat = seatsOf(6).first { s -> armed().state.let { st -> st.alone(s, circuitOf(st, s).index) } }
        var (state, clock) = ready(seat)
        val wipe = circuitOf(state, seat)
        val (spares, rack, disposal) = wipe.markers
        val before = state.systemIntegrity

        val walk = mutableListOf<DiskInHand?>()
        for (disk in 1..2) {
            for (station in listOf(spares, rack, disposal)) {
                val step = scan(state, seat, station, clock)
                state = step.state
                walk += carried(step.effects)
                assertNull(step.effects.filterIsInstance<Effect.ScanAnswered>().single().opened, "a station opened a screen")
            }
            if (disk == 1) assertTrue(!circuitOf(state, seat).done, "one disk finished the circuit")
        }
        assertEquals(
            listOf(
                DiskInHand(1, CircuitStation.Rack), DiskInHand(1, CircuitStation.Disposal), null,
                DiskInHand(2, CircuitStation.Rack), DiskInHand(2, CircuitStation.Disposal), null,
            ),
            walk,
            "the circuit did not walk good to the Rack and damaged to Disposal, one disk at a time",
        )
        assertTrue(circuitOf(state, seat).done, "four traverses did not finish the circuit")
        val banked = if (state.roleOf(seat) == home.someoneshome.model.Role.Resident) 1 else 0
        assertEquals(before - banked, state.systemIntegrity, "the circuit banked the wrong amount")
    }

    @Test
    fun `while a disk is in hand no other card opens anything`() {
        val seat = seatsOf(6).first { s -> armed().state.let { st -> st.alone(s, circuitOf(st, s).index) } }
        var (state, clock) = ready(seat)
        val wipe = circuitOf(state, seat)
        state = scan(state, seat, wipe.markers[0], clock).state
        assertNotNull(state.diskOf(seat), "Spares put nothing in hand")

        val order = state.workOrderFor(seat)!!
        val other = order.entries.first { it.work is AssignedWork.Subroutine && order.isActionable(it) }
        val blocked = scan(state, seat, other.anchor!!, clock)
        val answer = blocked.effects.filterIsInstance<Effect.ScanAnswered>().single()
        assertNull(answer.opened, "a Subroutine opened while a disk was in hand")
        assertEquals(DiskInHand(1, CircuitStation.Rack), answer.carrying, "the disk changed at another card")
        assertNull(blocked.state.openSubroutineFor(seat)?.takeIf { it.armed }, "something is armed while a disk is in hand")

        // Spares again, and Disposal with a good disk: neither is this disk's next station.
        for (card in listOf(wipe.markers[0], wipe.markers[2])) {
            val again = scan(blocked.state, seat, card, clock).effects.filterIsInstance<Effect.ScanAnswered>().single()
            assertEquals(DiskInHand(1, CircuitStation.Rack), again.carrying, "a good disk moved at ${card.value}")
        }
    }

    @Test
    fun `two seats share one Rack and both finish their own circuit`() {
        var state = armed(seats = 6).state
        val clock = Clock()
        val pair = seatsOf(6).filter { state.alone(it, circuitOf(state, it).index) }.take(2)
        assertEquals(2, pair.size)
        for (s in pair) for (b in circuitOf(state, s).blockedBy) state = finish(state, s, b, clock)
        val (spares, rack, disposal) = circuitOf(state, pair[0]).markers
        assertEquals(listOf(spares, rack, disposal), circuitOf(state, pair[1]).markers, "two circuits, two Racks")
        // Interleaved at every station: the Rack is a hub, not a queue.
        repeat(2) {
            for (station in listOf(spares, rack, disposal)) for (s in pair) state = scan(state, s, station, clock).state
        }
        for (s in pair) assertTrue(circuitOf(state, s).done, "seat ${s.index} did not finish beside the other")
    }
}

// =================================================================================================

class MemoryDumpTest {

    private fun dumpOf(state: GameState, seat: Seat): OrderEntry =
        state.workOrderFor(seat)!!.entries.first { it.work == AssignedWork.MemoryDump }

    // Planted by hand (D-323 A): the mix deals none while its screens are unbuilt.
    private fun ready(): Triple<GameState, Seat, Clock> {
        val seat = seatsOf(6).first()
        val start = armed(seats = 6).state.withMemoryDump(seat)
        var state = start
        val clock = Clock()
        for (b in dumpOf(state, seat).blockedBy) state = finish(state, seat, b, clock)
        return Triple(state, seat, clock)
    }

    @Test
    fun `the source shows the pattern and the target takes it back`() {
        var (state, seat, clock) = ready()
        val dump = dumpOf(state, seat)
        val (source, target) = dump.markers

        val shown = scan(state, seat, source, clock)
        state = shown.state
        val opened = assertNotNull(shown.effects.filterIsInstance<Effect.ScanAnswered>().single().opened)
        assertEquals(SubroutineKind.MemoryDump, opened.subroutine)
        val read = MemoryPattern.of(opened.parameters)
        assertTrue(read.shown && read.length in 4..5, "the source did not show four or five elements")
        assertEquals(read.elements, state.patternOf(seat, dump.index)!!.elements, "the house holds a different pattern")
        assertNull(carried(shown.effects), "a pattern rode the carry: Files must never show it (F-015)")

        val asked = scan(state, seat, target, clock)
        state = asked.state
        val entry = MemoryPattern.of(asked.effects.filterIsInstance<Effect.ScanAnswered>().single().opened!!.parameters)
        assertTrue(!entry.shown && entry.elements.isEmpty(), "the target showed the pattern")
        assertEquals(read.length, entry.length)

        val graded = hand(state, seat, target, read.elements, clock)
        assertEquals(true, graded.effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted)
        assertTrue(dumpOf(graded.state, seat).done, "a matched pattern did not finish the line")
        assertNull(graded.state.patternOf(seat, dump.index), "the pattern outlived its hand-over")
    }

    @Test
    fun `a mismatch says only mismatch and sends the player back to the source`() {
        var (state, seat, clock) = ready()
        val dump = dumpOf(state, seat)
        val (source, target) = dump.markers

        assertNull(
            scan(state, seat, target, clock).effects.filterIsInstance<Effect.ScanAnswered>().single().opened,
            "the target opened before the source was read",
        )
        state = scan(state, seat, source, clock).state
        val pattern = state.patternOf(seat, dump.index)!!.elements
        state = scan(state, seat, target, clock).state
        val wrong = pattern.reversed()
        val graded = hand(state, seat, target, wrong, clock)
        state = graded.state
        assertEquals(listOf(false), graded.effects.filterIsInstance<Effect.SubroutineGraded>().map { it.accepted })
        assertNull(state.patternOf(seat, dump.index), "a mismatch kept the pattern")
        assertTrue(!dumpOf(state, seat).done)

        assertNull(
            scan(state, seat, target, clock).effects.filterIsInstance<Effect.ScanAnswered>().single().opened,
            "the target opened again after a mismatch, without the source",
        )
        val fresh = scan(state, seat, source, clock)
        assertNotNull(fresh.effects.filterIsInstance<Effect.ScanAnswered>().single().opened, "the source did not show a fresh pattern")
        state = finish(fresh.state, seat, dump.index, clock)
        assertTrue(dumpOf(state, seat).done)
    }
}

// =================================================================================================

class OrderParityTest {

    @Test
    fun `the Insider's fake order has the shape every Resident's has`() {
        val reduction = armed(seats = 8, insiders = listOf(Seat(1), Seat(6)))
        val state = reduction.state
        fun shape(seat: Seat): List<Int> {
            val lines = reduction.effects.filterIsInstance<Effect.WorkOrderIssued>().single { it.seat == seat }.lines
            val order = state.workOrderFor(seat)!!
            return listOf(
                lines.size,
                lines.count { it is OrderLine.Blocked } - order.entries.count { !it.held && !order.isActionable(it) },
                order.entries.count { it.markers.size == 3 },
                order.entries.count { it.markers.size == 2 },
                reduction.effects.count { it is Effect.UpstreamWaiting && it.seat == seat },
            )
        }
        val resident = shape(Seat(0))
        for (seat in seatsOf(8)) assertEquals(resident, shape(seat), "seat ${seat.index} (${state.roleOf(seat)}) has a different shape")
    }

    @Test
    fun `swapping who is an Insider changes nothing the house draws`() {
        val a = armed(seats = 8, insiders = listOf(Seat(1))).state
        val b = armed(seats = 8, insiders = listOf(Seat(4))).state
        assertEquals(drawn(a), drawn(b), "the work order or a dependency followed the role")
    }

    @Test
    fun `a seat walks a circuit and a Memory Dump to the same delivered effects in either role`() {
        val seat = Seat(3)
        // The delivered effects are compared scan by scan below; the meter is the one difference.
        // The Memory Dump is planted (D-323 A), by the same role-free hand in both rounds.
        val asResident = armed(seats = 6, insiders = listOf(Seat(1))).state.withMemoryDump(seat)
        val asInsider = armed(seats = 6, insiders = listOf(seat)).state.withMemoryDump(seat)
        val clockA = Clock()
        val clockB = Clock()
        var a = asResident
        var b = asInsider
        val order = a.workOrderFor(seat)!!
        val structured = order.entries.filter { it.work !is AssignedWork.Subroutine && a.alone(seat, it.index) }
        assertTrue(structured.isNotEmpty(), "the seed gave this seat no circuit or Memory Dump it could walk alone")
        for (entry in structured) {
            for (blocker in entry.blockedBy) {
                if (!a.workOrderFor(seat)!!.entries[blocker].done) {
                    a = finish(a, seat, blocker, clockA)
                    b = finish(b, seat, blocker, clockB)
                }
            }
            val cards = when (entry.work) {
                AssignedWork.ArrayWipe -> List(Balance.ARRAY_WIPE_DISKS) { entry.markers }.flatten()
                else -> entry.markers
            }
            for (card in cards) {
                val ra = scan(a, seat, card, clockA)
                val rb = scan(b, seat, card, clockB)
                assertEquals(deliveredTo(seat, ra.effects, ra.state), deliveredTo(seat, rb.effects, rb.state), "a scan answered by role")
                a = ra.state
                b = rb.state
            }
            if (entry.work == AssignedWork.MemoryDump) {
                val pattern = a.openSubroutineFor(seat)!!.expected
                assertEquals(pattern, b.openSubroutineFor(seat)!!.expected, "the two roles were dealt different patterns")
                val ra = hand(a, seat, entry.markers[1], pattern, clockA)
                val rb = hand(b, seat, entry.markers[1], pattern, clockB)
                assertEquals(deliveredTo(seat, ra.effects, ra.state), deliveredTo(seat, rb.effects, rb.state), "a hand-over answered by role")
                a = ra.state
                b = rb.state
            }
            assertTrue(a.workOrderFor(seat)!!.entries[entry.index].done && b.workOrderFor(seat)!!.entries[entry.index].done)
        }
        assertTrue(a.systemIntegrity < b.systemIntegrity, "the Insider's fake banked, or the Resident's did not")
        assertEquals(asInsider.systemIntegrity, b.systemIntegrity, "the Insider's fake wrote to SystemIntegrity")
    }
}

// =================================================================================================

class WorkOrderDeterminismTest {

    @Test
    fun `the same seed draws the same orders and dependencies and another seed does not`() {
        val once = drawn(armed(seats = 8, seed = 77L).state)
        val again = drawn(armed(seats = 8, seed = 77L).state)
        val other = drawn(armed(seats = 8, seed = 78L).state)
        assertEquals(once, again, "one seed drew two rounds")
        assertNotEquals(once, other, "two seeds drew one round")
    }

    @Test
    fun `a Memory Dump's pattern is drawn from the scan and replays from the recording`() {
        // Planted by hand (D-323 A): the mix deals none while its screens are unbuilt.
        val seat = Seat(0)
        val state = armed(seats = 6).state.withMemoryDump(seat)
        var a = state
        var b = state
        val dump = state.workOrderFor(seat)!!.entries.first { it.work == AssignedWork.MemoryDump }
        val ca = Clock()
        val cb = Clock()
        for (blocker in dump.blockedBy) {
            a = finish(a, seat, blocker, ca)
            b = finish(b, seat, blocker, cb)
        }
        a = scan(a, seat, dump.markers[0], ca).state
        b = scan(b, seat, dump.markers[0], cb).state
        assertEquals(a.patternOf(seat, dump.index)!!.elements, b.patternOf(seat, dump.index)!!.elements, "one recording showed two patterns")
    }
}
