package home.someoneshome.core

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.ChainLink
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
import home.someoneshome.model.Role
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
    effects.filterIsInstance<Effect.ScanAnswered>().last().carrying

/** BEGIN, on whatever the seat's last scan offered (D-325). */
private fun begin(state: GameState, seat: Seat, clock: Clock) =
    reduce(state, Event.CarryBegun(clock.next(), seat))

/**
 * **A station scanned the way a player walks it** (D-325): the first Spares is briefed, so BEGIN
 * follows it; every other station is the scan alone. The effects of both, in order.
 */
private fun station(state: GameState, seat: Seat, card: MarkerId, clock: Clock): Reduction<GameState, Effect> {
    val scanned = scan(state, seat, card, clock)
    if (!scanned.effects.filterIsInstance<Effect.ScanAnswered>().single().circuit) return scanned
    val begun = begin(scanned.state, seat, clock)
    return Reduction(begun.state, scanned.effects + begun.effects)
}

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
            state = begin(state, seat, clock).state
            val pattern = assertNotNull(state.patternOf(seat, entry), "the source held no pattern").elements
            state = scan(state, seat, line.markers[1], clock).state
            state = hand(state, seat, line.markers[1], pattern, clock).state
        }
        AssignedWork.ArrayWipe -> repeat(Balance.ARRAY_WIPE_DISKS) {
            for (card in line.markers) state = station(state, seat, card, clock).state
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
     * **GUARD (D-325): the switch is on, and the design's mix is dealt.** Memory Dump's screens are
     * built from the owner's approved draft, so [Balance.MEMORY_DUMP_DEALT] is true (it was false
     * from D-323 A), [Balance.orderMix] is [Balance.designMix] for every `K`, and every order of
     * every round, the Insider's fake included, holds the mix's Memory Dumps at two cards.
     */
    @Test
    fun `the design's mix is dealt with a Memory Dump in every order`() {
        assertEquals(true, Balance.MEMORY_DUMP_DEALT, "Memory Dump is not dealt, though its screens are built (D-325)")
        for (k in 0..60) assertEquals(Balance.designMix(k), Balance.orderMix(k), "the order of $k is not the design's mix")
        for (seats in 5..18) {
            for (seed in listOf(1L, 77L, 20261121L, 20261122L)) {
                val insiders = insidersFor(seed, seatsOf(seats), null)
                val state = armed(seats = seats, insiders = insiders, seed = seed).state
                for (order in state.workOrders) {
                    val longs = Balance.orderMix(order.entries.size).getValue(SubroutineStructure.Long)
                    assertTrue(longs >= 1, "an order of ${order.entries.size} deals no long")
                    assertEquals(
                        longs,
                        order.entries.count { it.work == AssignedWork.MemoryDump },
                        "seat ${order.seat.index} of $seats (${state.roleOf(order.seat)}, seed $seed) holds the wrong number of Memory Dumps",
                    )
                    for (dump in order.entries.filter { it.work == AssignedWork.MemoryDump }) {
                        assertEquals(2, dump.markers.distinctBy { it.value }.size, "a Memory Dump at one card")
                    }
                }
            }
        }
    }
}

// =================================================================================================

class CrossPlayerChainTest {

    /** Every lawful round over [seeds] at five to twelve seats, the Insiders as the house draws them. */
    private fun rounds(seeds: LongRange = 1L..30L): List<GameState> = seeds.flatMap { seed ->
        (5..12).map { n -> armed(seats = n, insiders = insidersFor(seed, seatsOf(n), null), seed = seed).state }
    }

    private fun GameState.isInsider(seat: Seat) = roleOf(seat) == Role.Insider

    /** The players each chain links, following a release on to whatever the released line holds. */
    private fun chainLengths(state: GameState): List<Int> {
        val real = state.links.filterNot { it.fake }
        fun length(link: ChainLink, seen: Int): Int {
            val next = real.filter { it.isUp(link.down, link.downEntry) }
            return if (next.isEmpty() || seen > state.seats.size) seen else next.maxOf { length(it, seen + 1) }
        }
        return real.map { length(it, 2) }
    }

    @Test
    fun `every real chain links two plain Residents and no Insider is ever upstream`() {
        for (state in rounds()) {
            val residents = state.seats.filterNot { state.isInsider(it) }
            val real = state.links.filterNot { it.fake }
            for (link in real) {
                assertTrue(!state.isInsider(link.up), "an Insider is upstream of a real chain: ${link.up}")
                assertTrue(!state.isInsider(link.down), "an Insider is downstream of a real chain: ${link.down}")
                assertNotEquals(link.up.index, link.down.index, "a dependency joined a seat to itself")
            }
            assertTrue(state.links.none { state.isInsider(it.up) }, "an Insider's work holds somebody's line")
            for (link in state.links.filter { it.fake }) assertTrue(state.isInsider(link.down), "a fake hold on a Resident")
            assertEquals(residents.map { it.index }, real.map { it.up.index }.sorted(), "a Resident is upstream twice or never")
            assertEquals(residents.map { it.index }, real.map { it.down.index }.sorted(), "a Resident is downstream twice or never")
            for (link in state.links) {
                assertTrue(state.workOrderFor(link.down)!!.entries[link.downEntry].held, "the downstream line is not held")
                val seconds = link.delay / Balance.TICKS_PER_SECOND
                assertTrue(seconds in 20L..120L, "an unlock delay of ${seconds}s is outside gdd.md's 20–120 s")
            }
        }
    }

    @Test
    fun `the length bound holds and a round with no Insider draws no chain`() {
        for (state in rounds()) {
            val bound = Balance.chainBound(state.insiderSeats.size)
            for (length in chainLengths(state)) assertTrue(length <= bound, "a chain of $length players against a bound of $bound")
        }
        val none = armed(seats = 6, insiders = emptyList()).state
        assertEquals(emptyList(), none.links.map { it.up.index }, "a round with a bound of one drew a chain")
        assertTrue(none.workOrders.all { o -> o.entries.none { it.held } }, "a round with no chain locked a line")
    }

    @Test
    fun `an Insider's order is locked in the same number and at the same place a Resident's would be`() {
        for (seed in 1L..30L) {
            val seat = Seat(4)
            val residentRound = armed(seats = 8, insiders = listOf(Seat(1)), seed = seed)
            for (other in listOf(listOf(seat), listOf(seat, Seat(6)))) {
                val insiderRound = armed(seats = 8, insiders = other, seed = seed)
                fun holds(r: Reduction<GameState, Effect>) = r.state.links.filter { it.down == seat }.map { it.downEntry to it.delay }
                assertEquals(holds(residentRound), holds(insiderRound), "seat 4's locked line or its delay moved with its role (seed $seed)")
                assertEquals(1, holds(insiderRound).size, "seat 4 holds ${holds(insiderRound).size} locked lines")
                fun lines(r: Reduction<GameState, Effect>) = r.effects.filterIsInstance<Effect.WorkOrderIssued>().single { it.seat == seat }.lines
                assertEquals(lines(residentRound), lines(insiderRound), "seat 4's order reached its phone differently by role (seed $seed)")
            }
        }
    }

    @Test
    fun `an Insider's locked line unlocks on the same distribution as a Resident's`() {
        val real = mutableListOf<Long>()
        val fake = mutableListOf<Long>()
        for (seed in 1L..300L) {
            val state = armed(seats = 8, insiders = insidersFor(seed, seatsOf(8), 2), seed = seed).state
            for (link in state.links) (if (link.fake) fake else real) += link.delay / Balance.TICKS_PER_SECOND
        }
        for (d in real + fake) assertTrue(d in 20L..120L)
        // One draw makes both: the delay is the downstream seat's whatever its role (the test
        // above), so the two samples differ only by chance and their means sit together.
        val gap = kotlin.math.abs(real.average() - fake.average())
        assertTrue(gap < 8.0, "a fake hold's delay is drawn differently: real ${real.average()} against fake ${fake.average()}")
        assertTrue(fake.size >= 600, "the sample of fake holds is too small: ${fake.size}")
    }

    @Test
    fun `no seat is told anyone is waiting at lights out`() {
        val reduction = armed(seats = 8, insiders = listOf(Seat(1), Seat(5)))
        // The Isolate picker and its blank are one run of one size (D-258); everything else is one kind.
        val kinds = seatsOf(8).map { seat ->
            deliveredTo(seat, reduction.effects, reduction.state).map { it::class.simpleName!!.replace("Blanked", "Offered") }
        }
        for (k in kinds) assertEquals(kinds[0], k, "the arming burst differs between seats")
        assertTrue(kinds[0].none { it == "WorkUnblocked" }, "something like a chain notice rode the burst")
        for (link in reduction.state.links) {
            val issued = reduction.effects.filterIsInstance<Effect.WorkOrderIssued>().single { it.seat == link.down }
            assertIs<OrderLine.Blocked>(issued.lines[link.downEntry], "the held line reached its phone named")
        }
    }

    @Test
    fun `a chain unlocks across players after its delay and not a tick before`() {
        var state = armed(seats = 6).state
        // A downstream line nothing of its own order is chained before, so the release alone names
        // it on the wire (a line still behind its own earlier work is rightly Blocked after it too).
        val link = state.links.first { !it.fake && state.workOrderFor(it.down)!!.entries[it.downEntry].blockedBy.isEmpty() }
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
        assertTrue(after.workOrderFor(link.down)!!.isActionable(after.workOrderFor(link.down)!!.entries[link.downEntry]))

        val twice = admit(after, Event.ChainUnlocked(Tick(due.step + 5), link.down, link.downEntry))
        assertEquals(Admission.Refused(RefusalReason.ChainNotDue), twice, "a released line was released again")
    }

    @Test
    fun `a Revoke strands downstream work and nothing ever releases it`() {
        val armedState = armed(seats = 6).state
        val link = armedState.links.first { !it.fake }
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
    fun `an Insider's work releases nobody`() {
        val insider = Seat(3)
        var state = armed(seats = 6, insiders = listOf(insider)).state
        val clock = Clock()
        for (entry in state.workOrderFor(insider)!!.entries.filter { state.alone(insider, it.index) }) {
            if (!state.workOrderFor(insider)!!.entries[entry.index].done) state = finish(state, insider, entry.index, clock)
        }
        assertTrue(state.links.all { it.dueAt == null }, "an Insider's fake started somebody's delay")
    }

    @Test
    fun `an Insider's fake hold is released off a Resident's completion with a Resident's effects`() {
        fun run(insiders: List<Seat>): Pair<Long, List<Effect>> {
            var state = armed(seats = 6, insiders = insiders).state
            val link = state.links.single { it.down == Seat(3) }
            state = finish(state, link.up, link.upEntry, Clock())
            val started = state.links.single { it.isDown(link.down, link.downEntry) }
            val released = (admit(state, Event.ChainUnlocked(started.dueAt!!, link.down, link.downEntry)) as Admission.Admitted).reduction
            return started.delay to deliveredTo(link.down, released.effects, released.state)
        }
        val asInsider = run(listOf(Seat(3)))
        val asResident = run(listOf(Seat(1)))
        assertEquals(asResident, asInsider, "seat 3's release differed by its role")
        assertTrue(
            asInsider.second.any { it is Effect.WorkUnblocked && it.haptic == HOUSE_TEXT_BUZZ },
            "the fake's `you're up` did not buzz as a house text",
        )
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
            for (card in listOf(spares, rack, disposal)) {
                val step = station(state, seat, card, clock)
                state = step.state
                walk += carried(step.effects)
                assertTrue(step.effects.filterIsInstance<Effect.ScanAnswered>().all { it.opened == null }, "a station opened a screen")
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
        state = station(state, seat, wipe.markers[0], clock).state
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
            for (card in listOf(spares, rack, disposal)) for (s in pair) state = station(state, s, card, clock).state
        }
        for (s in pair) assertTrue(circuitOf(state, s).done, "seat ${s.index} did not finish beside the other")
    }

    @Test
    fun `the first Spares is briefed and BEGIN takes the disk and NOT THIS ONE takes nothing`() {
        val seat = seatsOf(6).first { s -> armed().state.let { st -> st.alone(s, circuitOf(st, s).index) } }
        var (state, clock) = ready(seat)
        val (spares, rack) = circuitOf(state, seat).markers

        // The first Spares: briefed, nothing in hand, no *no*.
        val caught = scan(state, seat, spares, clock)
        val answer = caught.effects.filterIsInstance<Effect.ScanAnswered>().single()
        assertTrue(answer.circuit, "the first Spares was not briefed")
        assertNull(answer.carrying, "the first Spares put a disk in hand before BEGIN")
        assertNull(answer.haptic, "the first Spares said no")
        assertNull(caught.state.diskOf(seat))

        // NOT THIS ONE sends nothing: the next scan, the Rack, finds nothing to swap, and a BEGIN
        // that arrives after it takes nothing.
        val walkedOff = scan(caught.state, seat, rack, clock)
        assertNull(walkedOff.state.diskOf(seat), "NOT THIS ONE took a disk")
        val late = begin(walkedOff.state, seat, clock)
        assertNull(late.state.diskOf(seat), "a BEGIN after another scan took the disk")
        assertTrue(late.effects.none { it is Effect.ScanAnswered }, "a BEGIN that took nothing answered as a step")

        // BEGIN on the briefed Spares takes DISK 01, and answers as a station step.
        val again = scan(late.state, seat, spares, clock)
        assertTrue(again.effects.filterIsInstance<Effect.ScanAnswered>().single().circuit)
        val begun = begin(again.state, seat, clock)
        assertEquals(DiskInHand(1, CircuitStation.Rack), begun.effects.filterIsInstance<Effect.ScanAnswered>().single().carrying)
        assertEquals(listOf(seat, seat), begun.effects.map { (it as? Effect.ScanAnswered)?.seat ?: (it as Effect.WorkOrderIssued).seat })
        state = begun.state

        // The second Spares, after the first disk is delivered, is the scan alone (D-322).
        for (card in circuitOf(state, seat).markers.drop(1)) state = scan(state, seat, card, clock).state
        val second = scan(state, seat, spares, clock).effects.filterIsInstance<Effect.ScanAnswered>().single()
        assertTrue(!second.circuit, "the second Spares was briefed again")
        assertEquals(DiskInHand(2, CircuitStation.Rack), second.carrying)
    }
}

// =================================================================================================

class MemoryDumpTest {

    private fun dumpOf(state: GameState, seat: Seat): OrderEntry =
        state.workOrderFor(seat)!!.entries.first { it.work == AssignedWork.MemoryDump }

    // The dealt line (D-325): the first seat whose Memory Dump nothing of another seat holds.
    private fun ready(): Triple<GameState, Seat, Clock> {
        val start = armed(seats = 6).state
        val seat = seatsOf(6).first { start.alone(it, dumpOf(start, it).index) }
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
        assertTrue(read.shown && read.length == 4, "the source did not show four elements")
        assertEquals((0 until 4).toList(), read.elements.sorted(), "the pattern is not the four dots, each once")
        assertNull(state.patternOf(seat, dump.index), "the pattern was held before BEGIN (D-325)")
        assertNull(carried(shown.effects), "a pattern rode the carry: Files must never show it (F-015)")

        // BEGIN: held from here, and the order says so (the row reads TO <ROOM>).
        val begun = begin(state, seat, clock)
        state = begun.state
        assertEquals(read.elements, state.patternOf(seat, dump.index)!!.elements, "the house holds a different pattern")
        val held = begun.effects.filterIsInstance<Effect.WorkOrderIssued>().single().lines[dump.index]
        assertEquals(1, assertIs<OrderLine.Known>(held).pattern, "the order does not say the pattern is held")
        assertTrue(begun.effects.none { it is Effect.ScanAnswered }, "BEGIN at the source answered as a scan")

        val asked = scan(state, seat, target, clock)
        state = asked.state
        val entry = MemoryPattern.of(asked.effects.filterIsInstance<Effect.ScanAnswered>().single().opened!!.parameters)
        assertTrue(!entry.shown && entry.elements.isEmpty(), "the target showed the pattern")
        assertEquals(read.length, entry.length)

        val graded = hand(state, seat, target, read.elements, clock)
        assertEquals(true, graded.effects.filterIsInstance<Effect.SubroutineGraded>().single().accepted)
        assertTrue(dumpOf(graded.state, seat).done, "a matched pattern did not finish the line")
        assertNull(graded.state.patternOf(seat, dump.index), "the pattern outlived its hand-over")
        val after = graded.effects.filterIsInstance<Effect.WorkOrderIssued>().single().lines[dump.index]
        assertEquals(0, assertIs<OrderLine.Known>(after).pattern, "the order still says a pattern is held")
    }

    @Test
    fun `NOT THIS ONE at the source holds nothing`() {
        var (state, seat, clock) = ready()
        val (source, target) = dumpOf(state, seat).markers
        state = scan(state, seat, source, clock).state
        // NOT THIS ONE sends nothing; the player walks to the target and scans it.
        val asked = scan(state, seat, target, clock)
        assertNull(asked.effects.filterIsInstance<Effect.ScanAnswered>().single().opened, "the target opened without BEGIN")
        assertNull(asked.state.patternOf(seat, dumpOf(state, seat).index), "NOT THIS ONE held the pattern")
        // A BEGIN arriving after another scan takes nothing either.
        val late = begin(asked.state, seat, clock)
        assertNull(late.state.patternOf(seat, dumpOf(state, seat).index), "a late BEGIN held a pattern")
        val line = late.effects.filterIsInstance<Effect.WorkOrderIssued>().single().lines[dumpOf(state, seat).index]
        assertEquals(0, assertIs<OrderLine.Known>(line).pattern)
    }

    @Test
    fun `the pattern reaches no phone after the source so Files can never show it`() {
        var (state, seat, clock) = ready()
        val dump = dumpOf(state, seat)
        val (source, target) = dump.markers
        val walk = mutableListOf<Effect>()
        for (step in listOf<(GameState) -> Reduction<GameState, Effect>>(
            { scan(it, seat, source, clock) },
            { begin(it, seat, clock) },
            { scan(it, seat, target, clock) },
        )) {
            val r = step(state)
            walk += deliveredTo(seat, r.effects, r.state)
            state = r.state
        }
        val pattern = state.patternOf(seat, dump.index)!!.elements
        val afterTheSource = walk.drop(walk.indexOfFirst { it is Effect.ScanAnswered } + 1)
        for (e in afterTheSource) {
            val text = e.toString()
            assertTrue(e !is Effect.ScanAnswered || e.opened?.parameters?.firstOrNull() != MemoryPattern.SOURCE, "the pattern was shown again: $e")
            assertTrue(e !is Effect.ScanAnswered || e.carrying == null, "a pattern rode the carry Files draws: $e")
            assertTrue(!text.contains(pattern.joinToString(", ")), "the pattern reached the phone after the source: $e")
        }
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
        state = begin(state, seat, clock).state
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
        // Back to the source: a fresh pattern, drawn off the new scan (D-139). Re-scanned until the
        // draw differs from the last one, which a fresh draw does within a few scans.
        var fresh = scan(state, seat, source, clock)
        var shownAgain = MemoryPattern.of(fresh.effects.filterIsInstance<Effect.ScanAnswered>().single().opened!!.parameters).elements
        var tries = 0
        while (shownAgain == pattern && tries++ < 8) {
            fresh = scan(fresh.state, seat, source, clock)
            shownAgain = MemoryPattern.of(fresh.effects.filterIsInstance<Effect.ScanAnswered>().single().opened!!.parameters).elements
        }
        assertNotEquals(pattern, shownAgain, "the source showed the same pattern again after a mismatch")
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
                order.entries.count { it.held },
            )
        }
        val resident = shape(Seat(0))
        for (seat in seatsOf(8)) assertEquals(resident, shape(seat), "seat ${seat.index} (${state.roleOf(seat)}) has a different shape")
    }

    @Test
    fun `swapping who is an Insider changes nothing the house draws`() {
        val a = armed(seats = 8, insiders = listOf(Seat(1))).state
        val b = armed(seats = 8, insiders = listOf(Seat(4))).state
        // The orders, and every seat's locked line with its delay, are the role-free draw's. Who a
        // Resident's line waits on does follow the role, which is the ruling (D-324).
        fun holds(state: GameState) = state.links.map { "${it.down.index}.${it.downEntry}:${it.delay}" }
        assertEquals(drawn(a).lines().dropLast(1), drawn(b).lines().dropLast(1), "the work order followed the role")
        assertEquals(holds(a), holds(b), "a locked line or its delay followed the role")
    }

    @Test
    fun `a seat walks a circuit and a Memory Dump to the same delivered effects in either role`() {
        val seat = Seat(3)
        // The delivered effects are compared scan by scan below; the meter is the one difference.
        // The Memory Dump is the dealt one (D-325), drawn without the role in both rounds.
        val asResident = armed(seats = 6, insiders = listOf(Seat(1))).state
        val asInsider = armed(seats = 6, insiders = listOf(seat)).state
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
                // BEGIN where the scan offered a carry (D-325): the first Spares, a source.
                val answer = ra.effects.filterIsInstance<Effect.ScanAnswered>().single()
                if (answer.circuit || answer.opened?.parameters?.firstOrNull() == MemoryPattern.SOURCE &&
                    answer.opened?.subroutine == SubroutineKind.MemoryDump
                ) {
                    val ba = begin(a, seat, clockA)
                    val bb = begin(b, seat, clockB)
                    assertEquals(deliveredTo(seat, ba.effects, ba.state), deliveredTo(seat, bb.effects, bb.state), "BEGIN answered by role")
                    a = ba.state
                    b = bb.state
                }
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
        // The dealt line (D-325).
        val seat = Seat(0)
        val state = armed(seats = 6).state
        var a = state
        var b = state
        val dump = state.workOrderFor(seat)!!.entries.first { it.work == AssignedWork.MemoryDump }
        val ca = Clock()
        val cb = Clock()
        for (blocker in dump.blockedBy) {
            a = finish(a, seat, blocker, ca)
            b = finish(b, seat, blocker, cb)
        }
        a = begin(scan(a, seat, dump.markers[0], ca).state, seat, ca).state
        b = begin(scan(b, seat, dump.markers[0], cb).state, seat, cb).state
        assertEquals(a.patternOf(seat, dump.index)!!.elements, b.patternOf(seat, dump.index)!!.elements, "one recording showed two patterns")
    }
}
