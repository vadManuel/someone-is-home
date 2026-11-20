package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.ContactSource
import home.someoneshome.model.Hmac
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Presence
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingPlan
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingReport
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingStep
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.SpareKey
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **The house's UWB pairing (U6, D-300, D-306)**: symmetric, never past a phone's capacity,
 * deterministic, slow to churn, dealt handles that say nothing about a seat, and, since D-306, only
 * for close pairs the game needs.
 *
 * Phones here are small models of the radio's pool ([Phone]): they offer one spare per free session,
 * take a Start on the spare it names, drop a pair on a Stop and open a fresh spare in its place, and
 * list every pair they hold in the next report. Bluetooth is the bench curve, −30 dBm at 5 cm and
 * −20 dB a decade, heard by every phone that is not out: on it [RangingGate.NEAR_RSSI_FLOOR] (−68)
 * is about four metres.
 *
 * **The need.** The mechanics (D-300, D-302) are tested with a need that answers yes to every pair
 * ([every]), so that what they pin is the pairing's own arithmetic, as before D-306. The cases under
 * *Only close pairs the game needs* run the house's own need ([RoundRangingNeed]) over a real round.
 */
class RangingPairsTest {

    private companion object {
        val TERMINAL = MarkerId("T")
    }

    private val second = Balance.TICKS_PER_SECOND

    /** How many beats a held pair the house never picked may take to be ranged (D-302). */
    private val displaceBeats = 3

    private fun armed(seats: Int, insiders: List<Int> = listOf(1), seed: Long = 20261119L, source: ContactSource = ContactSource.Rssi): GameState =
        reduce(
            GameState.EMPTY,
            Event.RoundArmed(
                Tick(0), seed = seed, seats = (0 until seats).map { Seat(it) }, insiders = insiders.map { Seat(it) },
                contactSource = source, terminal = TERMINAL, terminalRoom = "ROOM 0",
            ),
        ).state

    /** A round that reads distance (D-301), the only kind in which the game needs a pair (D-306). */
    private fun onDistance(seats: Int, insiders: List<Int> = listOf(1)): GameState = armed(seats, insiders, source = ContactSource.Uwb)

    /** The tick an Insider's Revoke can first be armed: the cooldown starts at half (D-132). */
    private val ready = Balance.REVOKE_COOLDOWN / 2

    /** [state] with [seat]'s Revoke armed at [at], through the rules. */
    private fun withArm(state: GameState, seat: Int, at: Long): GameState =
        reduce(state, Event.RevokeArmed(Tick(at), Seat(seat))).state.also {
            assertTrue(it.armedAt(Seat(seat), Tick(at)), "the fixture: seat $seat's Revoke did not arm")
        }

    /** [state] with [seat] performing at the T card since [since]: a sensor ([sensingRoom]). */
    private fun withSensor(state: GameState, seat: Int, since: Long): GameState =
        state.copy(presence = state.presence.filter { it.seat.index != seat } + Presence(Seat(seat), TERMINAL, open = true, since = Tick(since)))

    /** A need that answers yes to every pair: the mechanics alone, as before D-306. */
    private val every = RangingNeed { _, _, _ -> true }

    private fun bytes(vararg v: Long): ByteArray = Hmac.sha256(byteArrayOf(3), ByteArray(8 * v.size) { i -> (v[i / 8] ushr (56 - 8 * (i % 8))).toByte() })

    private fun hex(b: ByteArray, n: Int) = b.copyOf(n).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    /** A phone's idle-session pool and the pairs it holds, as the radio will keep them. */
    private inner class Phone(val seat: Int, var capacity: Int = 3) {
        private var made = 0L
        val spares = mutableListOf<RangingSpare>()
        val held = LinkedHashMap<RangingHandle, Pair<SpareKey, RangingToken>>()
        var follows = true

        /** A phone that offers one spare more than its capacity leaves room for: modified, or behind. */
        var offersTooMany = false

        private fun fresh(): RangingSpare {
            val b = bytes(seat.toLong(), made++)
            return RangingSpare(SpareKey.ofHex(hex(b, 8)), RangingToken.ofHex(hex(bytes(seat.toLong(), made, 7L), 32).repeat(10)))
        }

        fun report(): RangingReport {
            val room = capacity + if (offersTooMany) 1 else 0
            while (spares.size + held.size < room) spares += fresh()
            while (spares.size + held.size > room && spares.isNotEmpty()) spares.removeAt(spares.lastIndex)
            // At most three idle sessions offered a second (D-300); the rest wait.
            return RangingReport(capacity, spares.take(RangingWire.TOKEN_SLOTS), held.keys.sortedBy { it.hex }.map { RangingReading(it, 50, 6) })
        }

        fun apply(plan: RangingPlan) {
            if (!follows) return
            for (step in plan.steps) when (step) {
                is RangingStep.Stop -> held.remove(step.handle)
                is RangingStep.Start -> {
                    if (step.handle in held) continue
                    val s = spares.firstOrNull { it.key == step.own } ?: continue
                    spares.remove(s)
                    held[step.handle] = step.own to step.peer
                }
                is RangingStep.Keep -> Unit
            }
        }
    }

    private data class Spot(val x: Double, val y: Double)

    private fun rssi(a: Spot, b: Spot): Int {
        val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).coerceAtLeast(0.05)
        return (-30.0 - 20.0 * log10(d / 0.05)).roundToInt().coerceAtLeast(-100)
    }

    /** What the pairing holds, read out: its pairs and how many it has formed. */
    private data class Held(val pairs: List<RangingPair>, val formed: Long)

    /**
     * One room: phones at spots, uploads fed one at a time, every plan applied and kept. [byTheGame]
     * asks the house's own need of [state] as it stands on each upload; otherwise [every].
     */
    private inner class Room(var state: GameState, val phones: List<Phone>, val byTheGame: Boolean = false) {
        val pairing = rangingDeskFor(state).pairing
        private val roundNeed = RoundRangingNeed()
        val memory: Held get() = Held(pairing.pairs(), pairing.pairsFormed)
        val spots = HashMap<Int, Spot>()
        val deaf = HashSet<Int>()
        var tick = 0L
        val plans = mutableListOf<Pair<Int, RangingPlan>>()

        fun heardBy(seat: Int): List<Pair<Seat, Int>> {
            if (seat in deaf) return emptyList()
            val here = spots[seat] ?: return emptyList()
            return phones.filter { it.seat != seat }.mapNotNull { other -> spots[other.seat]?.let { Seat(other.seat) to rssi(here, it) } }
        }

        fun up(phone: Phone, report: RangingReport? = phone.report(), taken: Boolean = true): RangingPlan {
            for ((seat, rssi) in heardBy(phone.seat)) pairing.hear(seat.index, rssi)
            roundNeed.state = state
            pairing.arrive(Tick(tick), Seat(phone.seat), report, taken, if (byTheGame) roundNeed else every)
            val plan = pairing.planned()
            phone.apply(plan)
            plans += phone.seat to plan
            return plan
        }

        /** One second: every phone uploads once, in seat order, a tick apart. */
        fun beat() {
            val start = tick
            for (p in phones) {
                up(p)
                tick++
            }
            tick = start + second
        }

        fun pairs(seat: Int) = memory.pairs.filter { it.has(Seat(seat)) }
    }

    // ---- Symmetric -------------------------------------------------------------------------------

    @Test
    fun twoPhonesNearEachOtherArePairedAndEachIsHandedTheOthersSpareToken() {
        val a = Phone(0)
        val b = Phone(2)
        val room = Room(armed(4), listOf(a, b))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[2] = Spot(1.0, 0.0)
        repeat(3) { room.beat() }
        val pair = room.memory.pairs.single()
        assertEquals(listOf(pair.handle), a.held.keys.toList(), "seat 0 does not hold the pair")
        assertEquals(listOf(pair.handle), b.held.keys.toList(), "seat 2 does not hold the pair")
        val (aOwn, aPeer) = a.held.getValue(pair.handle)
        val (bOwn, bPeer) = b.held.getValue(pair.handle)
        assertEquals(pair.lowSpare.key, aOwn)
        assertEquals(pair.highSpare.token, aPeer, "seat 0 was not handed seat 2's token")
        assertEquals(pair.highSpare.key, bOwn)
        assertEquals(pair.lowSpare.token, bPeer, "seat 2 was not handed seat 0's token")
        // Once both hold it, each is told to keep it, and nothing else.
        room.beat()
        val last = room.plans.takeLast(2).associate { it.first to it.second }
        assertEquals(RangingPlan(listOf(RangingStep.Keep(pair.handle))), last.getValue(0))
        assertEquals(RangingPlan(listOf(RangingStep.Keep(pair.handle))), last.getValue(2))
    }

    /** A crowd: twelve phones, mixed capacities, walking for a minute. */
    private fun crowd(capacities: List<Int>, state: GameState = armed(capacities.size), walk: Long = 7L): Room {
        val phones = capacities.mapIndexed { i, c -> Phone(i, c) }
        val room = Room(state, phones)
        var x = walk
        fun next(): Double {
            x = x * 6364136223846793005L + 1442695040888963407L
            return ((x ushr 33).toDouble() / (1L shl 31).toDouble())
        }
        for (p in phones) room.spots[p.seat] = Spot(next() * 8, next() * 6)
        repeat(60) { s ->
            if (s % 5 == 0) for (p in phones) {
                val at = room.spots.getValue(p.seat)
                room.spots[p.seat] = Spot((at.x + next() * 2 - 1).coerceIn(0.0, 8.0), (at.y + next() * 2 - 1).coerceIn(0.0, 6.0))
            }
            if (s == 30) phones[3].capacity = 1
            room.beat()
            for (p in phones) {
                val degree = room.pairs(p.seat).size
                assertTrue(degree <= p.capacity, "at second $s seat ${p.seat} is in $degree pairs with capacity ${p.capacity}")
                assertTrue(p.held.size <= p.capacity || s == 30, "at second $s seat ${p.seat} holds ${p.held.size} with capacity ${p.capacity}")
            }
        }
        return room
    }

    @Test
    fun everyPairIsBothPhonesAndNoPhoneIsInMorePairsThanItsCapacity() {
        val room = crowd(listOf(3, 3, 1, 6, 2, 3, 0, 4, 3, 2, 5, 1))
        assertTrue(room.memory.pairs.size >= 6, "a crowd of twelve formed only ${room.memory.pairs.size} pairs")
        assertTrue(room.pairs(6).isEmpty(), "a phone with no UWB was paired")
        room.beat()
        room.beat()
        for (pair in room.memory.pairs) {
            val low = room.phones[pair.low.index]
            val high = room.phones[pair.high.index]
            assertEquals(pair.highSpare.token, low.held[pair.handle]?.second, "seat ${pair.low.index} is not ranging ${pair.high.index}'s token")
            assertEquals(pair.lowSpare.token, high.held[pair.handle]?.second, "seat ${pair.high.index} is not ranging ${pair.low.index}'s token")
        }
        for (p in room.phones) {
            val inMemory = room.pairs(p.seat).map { it.handle }.toSet()
            assertEquals(inMemory, p.held.keys, "seat ${p.seat} holds pairs the house does not, or the other way")
        }
    }

    // ---- Deterministic ---------------------------------------------------------------------------

    @Test
    fun theSameUploadsGiveTheSamePairsHandlesAndPlans() {
        val one = crowd(listOf(3, 2, 3, 1, 4, 3, 3, 2))
        val two = crowd(listOf(3, 2, 3, 1, 4, 3, 3, 2))
        assertEquals(one.memory, two.memory, "the same uploads paired differently")
        assertEquals(one.plans, two.plans, "the same uploads planned differently")
    }

    // ---- Hysteresis ------------------------------------------------------------------------------

    @Test
    fun aKeptPairIsNotDisplacedByAPeerJustAheadOfItAndGoesWhenItFallsOutOfReach() {
        val a = Phone(0, capacity = 1)
        val b = Phone(1, capacity = 1)
        val c = Phone(2, capacity = 1)
        val d = Phone(3, capacity = 1)
        val e = Phone(4, capacity = 1)
        val room = Room(armed(5), listOf(a, b, c, d, e))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(1.0, 0.0)
        room.spots[2] = Spot(3.0, 0.0)
        room.spots[3] = Spot(7.0, 0.0)
        room.spots[4] = Spot(12.0, 5.0)
        repeat(3) { room.beat() }
        val ab = room.memory.pairs.single { it.low.index == 0 }
        assertEquals(1, ab.high.index, "seat 0 should pair with its nearest, seat 1")
        // C walks in closer to A than B is. A keeps B: B is still within A's top 1 + margin.
        room.spots[2] = Spot(0.5, 0.0)
        repeat(5) { room.beat() }
        assertEquals(ab.handle, room.memory.pairs.single { it.has(Seat(0)) }.handle, "a peer just ahead of the kept one displaced it")
        assertTrue(room.plans.takeLast(5).all { (s, p) -> s != 0 || p.steps == listOf(RangingStep.Keep(ab.handle)) }, "seat 0 was told anything but keep")
        // B walks away, past the margin (three phones nearer to A than B is): the pair goes. B stays
        // inside the near floor (3.5 m, −67 on the bench), so it is the margin that lets it go (D-306).
        room.spots[3] = Spot(0.3, 0.3)
        room.spots[4] = Spot(0.2, -0.4)
        room.spots[1] = Spot(3.5, 0.0)
        repeat(4) { room.beat() }
        assertTrue(room.memory.pairs.none { it.handle == ab.handle }, "the pair outlived falling out of reach")
        assertTrue(room.plans.any { (s, p) -> s == 1 && RangingStep.Stop(ab.handle) in p.steps }, "seat 1 was never told to stop")
    }

    // ---- Displacement: a pair both phones rank first is never starved (D-302) ----------------------

    /**
     * **GUARD (D-302): five phones at the starting capacity of three, one pair the house never
     * picked; the two phones are held together, and within [displaceBeats] beats both range each
     * other.** Five phones at three hold at most seven of the ten pairs, so three are missing; under
     * D-300's rule alone nothing ever falls past *capacity + 2* = rank five of four peers, so the
     * missing pairs stayed missing for the whole round, and a held touch between them never landed.
     *
     * Why three beats: the touch's first upload makes the pair both phones' first, and each full
     * phone gives up its weakest pair (its stop goes out in that answer); on the next beat each phone
     * offers the spare the stop freed, and the pair forms on the second of the two uploads; on the
     * third the first phone is handed its start. Readings follow a beat later on the radio.
     */
    @Test
    fun aPairTheHouseNeverPickedIsPairedWithinThreeBeatsOfItsPhonesBeingHeldTogether() {
        val phones = (0 until 5).map { Phone(it, capacity = RangingWire.STARTING_CAPACITY) }
        val room = Room(armed(5), phones)
        // A loose ring a few metres across, every phone at least a metre from every other: the house
        // pairs as many as it can, seven, and four of the five phones are full.
        val ring = listOf(Spot(0.2, 1.0), Spot(1.4, 1.5), Spot(1.4, 3.2), Spot(3.4, 2.8), Spot(3.3, 0.9))
        ring.forEachIndexed { i, at -> room.spots[i] = at }
        repeat(6) { room.beat() }
        val missing = (0 until 5).flatMap { a -> (a + 1 until 5).map { b -> a to b } }
            .filter { (a, b) -> room.memory.pairs.none { it.has(Seat(a)) && it.has(Seat(b)) } }
        println("D-302 FIVE AT THREE — pairs ${room.memory.pairs.map { it.low.index to it.high.index }}, missing $missing")
        assertEquals(7, room.memory.pairs.size, "five phones at three did not settle into seven pairs")
        assertTrue(
            missing.any { (a, b) -> room.pairs(a).size == 3 && room.pairs(b).size == 3 },
            "no unranged pair is between two full phones: $missing",
        )
        // Left as it is, a missing pair stays missing: the room is still.
        val settled = room.memory.pairs.map { it.handle }
        repeat(20) { room.beat() }
        assertEquals(settled, room.memory.pairs.map { it.handle }, "a still room changed its pairs")

        // Three times, a pair the house is not ranging now with a full phone in it (both full first),
        // never the same one twice: under D-300 alone each of them stayed unranged for good.
        val tried = mutableSetOf<Pair<Int, Int>>()
        repeat(3) {
            val unranged = (0 until 5).flatMap { a -> (a + 1 until 5).map { b -> a to b } }
                .filter { (a, b) -> (a to b) !in tried && room.memory.pairs.none { it.has(Seat(a)) && it.has(Seat(b)) } }
                .filter { (a, b) -> room.pairs(a).size == 3 || room.pairs(b).size == 3 }
            val (x, y) = unranged.firstOrNull { (a, b) -> room.pairs(a).size == 3 && room.pairs(b).size == 3 } ?: unranged.first()
            tried += x to y
            val full = listOf(x, y).filter { room.pairs(it).size == 3 }
            val before = room.memory.pairs.map { it.handle }.toSet()
            // Y is held against X.
            room.spots[y] = Spot(room.spots.getValue(x).x + 0.04, room.spots.getValue(x).y)
            var beats = 0
            while (beats < 30 && !rangingEachOther(room, x, y)) {
                room.beat()
                beats++
                for (p in phones) {
                    assertTrue(room.pairs(p.seat).size <= p.capacity, "seat ${p.seat} is in ${room.pairs(p.seat).size} pairs at capacity ${p.capacity}")
                    assertTrue(p.held.size <= p.capacity, "seat ${p.seat} was told to range ${p.held.size} at capacity ${p.capacity}")
                }
            }
            println("D-302 A HELD PAIR THE HOUSE NEVER PICKED — seats $x and $y (full: $full) ranging each other after $beats beats")
            assertTrue(beats <= displaceBeats, "seats $x and $y, held together, were not ranging each other within $displaceBeats beats (after $beats; pairs ${room.memory.pairs.map { it.low.index to it.high.index }})")
            // Each full phone gave up one pair, no more.
            val gone = before - room.memory.pairs.map { it.handle }.toSet()
            assertTrue(gone.size <= 2, "holding $x and $y together let ${gone.size} pairs go")
            // Back in the ring for the next pair.
            room.spots[y] = ring[y]
            repeat(6) { room.beat() }
        }
    }

    /**
     * **GUARD (D-302): the room a held pair displaced for is kept for it while it waits on a spare.**
     * Seat 0 (capacity 2) ranges two phones a metre off; seat 2 stands nearer than either, but only
     * by 2 to 3 dB, so it displaces neither and waits unpaired. Seat 1 is held against seat 0. Seat 0
     * gives up its weaker pair and its stop frees a spare a beat before seat 1's does; in that beat
     * the free place would go to seat 2, which has a spare and ranks seat 0 first, and seat 1 would
     * wait for another displacement. Held for the pair that displaced for it, the touch is ranged in
     * the same [displaceBeats] beats as anywhere else.
     */
    @Test
    fun theRoomAHeldPairDisplacedForIsKeptForItWhileItWaitsOnASpare() {
        val x = Phone(0, capacity = 2)
        val y = Phone(1, capacity = 1)
        val w = Phone(2, capacity = 1)
        val c = Phone(3, capacity = 1)
        val e = Phone(4, capacity = 1)
        val d = Phone(5, capacity = 1)
        val room = Room(armed(6), listOf(x, y, w, c, e, d))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[3] = Spot(1.0, 0.0)
        room.spots[4] = Spot(0.0, 1.1)
        room.spots[1] = Spot(5.0, 0.0)
        room.spots[5] = Spot(5.5, 0.0)
        room.spots[2] = Spot(12.0, 9.0)
        repeat(4) { room.beat() }
        // Seat 2 walks in, nearer seat 0 than seat 0's pairs, but not by the margin.
        room.spots[2] = Spot(-0.8, 0.0)
        repeat(5) { room.beat() }
        assertEquals(setOf(3, 4), room.pairs(0).map { if (it.low.index == 0) it.high.index else it.low.index }.toSet(), "the fixture: seat 0 should range seats 3 and 4")
        assertTrue(room.pairs(2).isEmpty(), "the fixture: seat 2 should be waiting unpaired")
        room.spots[1] = Spot(0.04, 0.0)
        var beats = 0
        while (beats < 30 && !rangingEachOther(room, 0, 1)) {
            room.beat()
            beats++
        }
        println("D-302 THE ROOM KEPT FOR A HELD PAIR — seats 0 and 1 ranging each other after $beats beats")
        assertTrue(beats <= displaceBeats, "seats 0 and 1, held together, were not ranging each other within $displaceBeats beats (after $beats): the room seat 0 gave up went elsewhere")
    }

    /**
     * **GUARD (D-302): a spare dealt into a pair its phone never took up is dealt again.** A phone
     * that does not act on its plans for twenty seconds (a lost answer, a phone behind) is dealt a
     * pair on its lowest spare, which is let go after the grace, again and again. Under D-300 each
     * such spare stayed dealt for the round while the phone kept offering it idle, so after three of
     * them it could never be paired again; the touch the eight-phone bench held between two such
     * phones never landed. Once it acts on its plans it is ranging its peer within three beats.
     */
    @Test
    fun aSpareDealtIntoAPairItsPhoneNeverTookUpIsDealtAgain() {
        val a = Phone(0)
        val b = Phone(1).also { it.follows = false }
        val room = Room(armed(2), listOf(a, b))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(0.04, 0.0)
        repeat(20) { room.beat() }
        assertTrue(room.memory.formed >= 3, "the fixture: seat 1 should have been dealt its three spares in pairs it never took up, was dealt ${room.memory.formed}")
        b.follows = true
        var beats = 0
        while (beats < 10 && !rangingEachOther(room, 0, 1)) {
            room.beat()
            beats++
        }
        assertTrue(beats <= displaceBeats, "seat 1, acting on its plans again, was not ranging seat 0 within $displaceBeats beats (after $beats): its spares stayed dealt")
    }

    /**
     * **GUARD (D-302): a handed-back spare is dealt again only once its phone's own next report
     * offers it idle.** The start may have reached the phone late, after the house let the pair go;
     * then the session is busy, and only the phone's next report (listing the old pair, not offering
     * the key) says so. Dealt again on the report from before the drop, it would be dealt into a
     * second pair the phone cannot start.
     */
    @Test
    fun aHandedBackSpareWaitsForItsPhonesNextReport() {
        val a = Phone(0)
        val b = Phone(1).also { it.follows = false }
        val room = Room(armed(2), listOf(a, b))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(0.04, 0.0)
        room.beat()
        val first = room.memory.pairs.single()
        // Upload by upload, until the house lets the first pair go.
        var uploads = 0
        while (room.memory.pairs.any { it.handle == first.handle } && uploads < 40) {
            val start = room.tick
            for (p in room.phones) {
                room.up(p)
                room.tick++
                uploads++
                if (room.memory.pairs.none { it.handle == first.handle }) break
            }
            if (room.memory.pairs.any { it.handle == first.handle }) room.tick = start + second
        }
        assertTrue(room.memory.pairs.none { it.handle == first.handle }, "the fixture: the pair seat 1 never took up was never let go")
        assertTrue(
            room.memory.pairs.none { it.highSpare.key == first.highSpare.key },
            "seat 1's spare was dealt again on the upload that let its pair go, before seat 1 said it was still idle",
        )
    }

    private fun rangingEachOther(room: Room, x: Int, y: Int): Boolean {
        val pair = room.memory.pairs.singleOrNull { it.has(Seat(x)) && it.has(Seat(y)) } ?: return false
        return pair.handle in room.phones[x].held && pair.handle in room.phones[y].held
    }

    /**
     * **GUARD (D-302): two candidates nearly tied for a full phone's first place do not trade the pair
     * back and forth.** A phone of capacity one between two others, the two taking turns at being
     * nearer by about 2 dB (0.9 m and 1.1 m), each turn four seconds, longer than a reading ranks a
     * pair. Each in its turn is the phone's first and the phone theirs, but it is never
     * [RangingGate.DISPLACE_MARGIN_DB] stronger than the kept pair, so the first pair formed stays
     * for the minute.
     */
    @Test
    fun twoNearlyTiedCandidatesDoNotTradeAPairBackAndForth() {
        val a = Phone(0, capacity = 1)
        val b = Phone(1, capacity = 1)
        val c = Phone(2, capacity = 1)
        val room = Room(armed(3), listOf(a, b, c))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(-0.9, 0.0)
        room.spots[2] = Spot(1.1, 0.0)
        repeat(3) { room.beat() }
        val first = room.memory.pairs.single().handle
        assertTrue(room.memory.pairs.single().has(Seat(1)), "seat 0 should pair first with its nearest, seat 1")
        val formedBefore = room.memory.formed
        for (turn in 0 until 15) {
            val bNearer = turn % 2 == 1
            room.spots[1] = Spot(if (bNearer) -0.9 else -1.1, 0.0)
            room.spots[2] = Spot(if (bNearer) 1.1 else 0.9, 0.0)
            repeat(4) { room.beat() }
        }
        assertEquals(formedBefore, room.memory.formed, "the pair traded places: ${room.memory.formed - formedBefore} pairs formed in a minute of a near tie")
        assertEquals(listOf(first), room.memory.pairs.map { it.handle }, "seat 0's pair changed in a near tie")
        assertEquals(setOf(first), a.held.keys, "seat 0 is not ranging the pair it began with")
    }

    // ---- A house that goes quiet (D-302) ------------------------------------------------------------

    /**
     * **GUARD (D-302): the house's clock jumping lets no pair go that its phones still hold.** Before,
     * any jump past three seconds aged every reading, report and hold at once, and every pair was let
     * go and formed afresh under new handles, so nobody could touch or report for the beats that
     * took. After the jump the phones list the same pairs, and the house keeps them all, telling each
     * phone only to keep; a pair one phone then stops listing still goes, after the grace.
     */
    @Test
    fun aJumpOfTheHousesClockKeepsEveryPairItsPhonesStillHold() {
        val phones = (0 until 6).map { Phone(it, capacity = RangingWire.STARTING_CAPACITY) }
        val room = Room(armed(6), phones)
        listOf(Spot(0.0, 0.0), Spot(1.0, 0.2), Spot(2.2, 0.0), Spot(0.3, 1.4), Spot(1.6, 1.6), Spot(2.8, 1.2))
            .forEachIndexed { i, at -> room.spots[i] = at }
        repeat(6) { room.beat() }
        val settled = room.memory.pairs.map { it.handle }
        assertTrue(settled.size >= 6, "the fixture paired only ${settled.size}")
        val formed = room.memory.formed
        room.tick += 10 * second
        val planned = room.plans.size
        repeat(3) { room.beat() }
        assertEquals(settled, room.memory.pairs.map { it.handle }, "a jump of the house's clock let pairs go")
        assertEquals(formed, room.memory.formed, "a jump of the house's clock formed pairs afresh")
        assertTrue(
            room.plans.drop(planned).all { (_, p) -> p.steps.all { it is RangingStep.Keep } },
            "after the jump a phone was told something other than keep: ${room.plans.drop(planned).filter { (_, p) -> p.steps.any { it !is RangingStep.Keep } }}",
        )
        // A pair one phone stops listing is still let go, after the grace.
        val lost = room.pairs(0).first()
        phones[0].follows = false
        phones[0].held.remove(lost.handle)
        repeat((RangingGate.HOLD_GRACE_TICKS / second).toInt() + 1) { room.beat() }
        assertTrue(room.memory.pairs.none { it.handle == lost.handle }, "a pair seat 0 stopped listing outlived the grace")
    }

    @Test
    fun aPairAPhoneNeverTakesUpIsLetGoAfterTheGraceAndAHandleNobodyKeepsIsStopped() {
        val a = Phone(0)
        val b = Phone(1).also { it.follows = false }
        val room = Room(armed(2), listOf(a, b))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(0.5, 0.0)
        room.beat()
        room.beat()
        val first = room.memory.pairs.single().handle
        repeat((RangingGate.HOLD_GRACE_TICKS / second).toInt() + 1) { room.beat() }
        assertTrue(room.memory.pairs.none { it.handle == first }, "a pair seat 1 never took up was kept past the grace")
        // Seat 0 held it; the house no longer keeps it, so seat 0 was told to stop it.
        assertTrue(room.plans.any { (s, p) -> s == 0 && RangingStep.Stop(first) in p.steps }, "seat 0 was never told to stop the lost pair")
        // A handle a phone claims that the house never dealt is stopped too.
        val stray = RangingHandle.ofHex("00000000000000aa")
        val plan = room.up(a, RangingReport(3, emptyList(), listOf(RangingReading(stray, 10, 3))))
        assertTrue(RangingStep.Stop(stray) in plan.steps, "a handle the house does not keep was not stopped")
    }

    /**
     * **The house holds a phone to its reported capacity, not to the spares it offers.** A phone at
     * capacity 1, ranging a peer that is no longer its nearest, offers a spare it has no room for
     * (modified, or a report from before its capacity dropped), and a nearer phone walks in.
     */
    @Test
    fun aPhoneOfferingMoreSparesThanItsCapacityIsStillPairedWithinIt() {
        val a = Phone(0, capacity = 1)
        val others = (1..4).map { Phone(it, capacity = 1) }
        val room = Room(armed(5), listOf(a) + others)
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(1.0, 0.0)
        room.spots[2] = Spot(3.0, 0.0)
        room.spots[3] = Spot(7.0, 0.0)
        room.spots[4] = Spot(12.0, 5.0)
        repeat(3) { room.beat() }
        assertEquals(1, room.pairs(0).size, "the fixture paired seat 0 with nobody")
        a.offersTooMany = true
        room.spots[2] = Spot(0.5, 0.0)
        // Upload by upload, not beat by beat: a pair formed past capacity and trimmed by a later
        // upload in the same second has still sent the phone a Start it has no room for.
        repeat(5) {
            val start = room.tick
            for (p in room.phones) {
                room.up(p)
                room.tick++
                assertTrue(room.pairs(0).size <= 1, "seat 0, capacity 1, is in ${room.pairs(0).size} pairs after seat ${p.seat}'s upload")
                assertTrue(a.held.size <= 1, "seat 0, capacity 1, was told to range ${a.held.size} pairs")
            }
            room.tick = start + second
        }
    }

    // ---- Handles ---------------------------------------------------------------------------------

    @Test
    fun handlesAreFreshForEveryFormingAndSayNothingAboutTheSeats() {
        // The first pair of a round gets the same handle whichever two seats form it.
        fun firstHandle(x: Int, y: Int, seed: Long = 20261119L): RangingHandle {
            val p = Phone(x)
            val q = Phone(y)
            val room = Room(armed(6, seed = seed), listOf(p, q))
            room.spots[x] = Spot(0.0, 0.0)
            room.spots[y] = Spot(0.4, 0.0)
            repeat(2) { room.beat() }
            return room.memory.pairs.single().handle
        }
        assertEquals(firstHandle(0, 1), firstHandle(3, 5), "a handle depends on which seats are in the pair")
        assertNotEquals(firstHandle(0, 1), firstHandle(0, 1, seed = 20261120L), "two rounds dealt the same handle")
        // A pair formed, let go and formed again gets a new one; nothing repeats over many formings.
        val handles = (1L..400L).map { rangingHandleFor(20261119L, it) }
        assertEquals(handles.size, handles.toSet().size, "a handle repeated within a round")
        // No byte position is shared across the handles of one seat's pairs: a crowd's handles, by seat.
        val room = crowd(listOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 3))
        var looked = 0
        for (seat in 0 until 10) {
            val mine = room.plans.filter { it.first == seat }.flatMap { (_, p) -> p.steps.map { it.handle } }.distinct()
            if (mine.size < 5) continue
            looked++
            for (i in 0 until 16) assertTrue(mine.map { it.hex[i] }.toSet().size > 1, "every handle seat $seat was dealt shares character $i")
        }
        assertTrue(looked > 0, "no seat in the crowd was dealt five handles, so the check saw nothing")
    }

    // ---- Only close pairs the game needs (D-306) ---------------------------------------------------

    /** Every pair the house keeps, as (low, high) seat numbers. */
    private fun Room.kept(): Set<Pair<Int, Int>> = memory.pairs.map { it.low.index to it.high.index }.toSet()

    /** Whether every pair the house keeps is held by both its phones. */
    private fun Room.allHeld(): Boolean = memory.pairs.all { it.handle in phones[it.low.index].held && it.handle in phones[it.high.index].held }

    /**
     * **GUARD (D-306): a close pair the game does not need is never paired.** Three phones within a
     * metre of each other in a round that reads distance, nobody armed, nobody Revoked, nobody at a
     * card: for ten seconds the house forms nothing and tells every phone nothing. And in a round
     * that reads Bluetooth nothing is needed, an armed Revoke included (D-301: no rule reads a
     * distance there).
     */
    @Test
    fun aClosePairTheGameDoesNotNeedIsNeverPaired() {
        val phones = (0 until 3).map { Phone(it) }
        val room = Room(onDistance(4), phones, byTheGame = true)
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(0.6, 0.0)
        room.spots[2] = Spot(0.3, 0.5)
        room.tick = ready
        repeat(10) { room.beat() }
        assertEquals(emptySet(), room.kept(), "three close phones the game does not need were paired")
        assertEquals(0L, room.memory.formed, "a pair the game does not need was formed")
        assertTrue(room.plans.all { (_, p) -> p.steps.isEmpty() }, "a phone the game does not need was told to range")

        val onBluetooth = Room(withArm(armed(4), 1, ready + 1), (0 until 3).map { Phone(it) }, byTheGame = true)
        onBluetooth.spots.putAll(room.spots)
        onBluetooth.tick = ready
        repeat(10) { onBluetooth.beat() }
        assertEquals(emptySet(), onBluetooth.kept(), "a round that reads Bluetooth paired phones for UWB")
    }

    /**
     * **GUARD (D-306): an armed Revoke pairs the Insider with the close phones it could land on**, and
     * nothing else: the other close phones, with no need of their own, stay unpaired.
     */
    @Test
    fun anArmedRevokePairsTheInsiderWithTheClosePhonesItCouldLandOn() {
        val phones = (0 until 4).map { Phone(it) }
        val room = Room(withArm(onDistance(4, insiders = listOf(1)), 1, ready + 1), phones, byTheGame = true)
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(0.8, 0.0)
        room.spots[2] = Spot(0.4, 0.7)
        room.spots[3] = Spot(1.2, 0.6)
        room.tick = ready + 1
        repeat(4) { room.beat() }
        assertEquals(setOf(0 to 1, 1 to 2, 1 to 3), room.kept(), "the armed Insider is not paired with exactly the close phones its Revoke could land on")
        assertTrue(room.allHeld(), "a pair the house keeps is not held by both its phones")
    }

    /**
     * **GUARD (D-306): a Revoked seat that can still be reported is paired with the close living
     * phones**, and they not with each other. The Revoked phone hears nobody (it advertises and never
     * scans, D-008), and is ranked by the phones that hear it. Once the seat is reported (S4) the
     * need ends: the pairs stay for [RangingGate.NEED_GRACE_TICKS] and then go, every phone told Stop.
     */
    @Test
    fun aRevokedSeatThatCanStillBeReportedIsPairedAndLetGoAfterTheGraceOnceReported() {
        val phones = (0 until 3).map { Phone(it) }
        val room = Room(onDistance(3, insiders = listOf(0)).copy(revoked = listOf(Seat(2))), phones, byTheGame = true)
        room.deaf += 2
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(0.7, 0.0)
        room.spots[2] = Spot(0.3, 0.6)
        repeat(4) { room.beat() }
        assertEquals(setOf(0 to 2, 1 to 2), room.kept(), "the Revoked seat is not paired with exactly the close living phones")
        assertTrue(room.allHeld(), "the Revoked phone is not ranging, so nobody can measure it")
        val handles = room.memory.pairs.map { it.handle }

        // Reported: no hold can start on it again, so the game needs neither pair.
        room.state = room.state.copy(reported = listOf(Seat(2)))
        room.beat()
        assertEquals(handles, room.memory.pairs.map { it.handle }, "a pair whose need ended went before its grace")
        repeat((RangingGate.NEED_GRACE_TICKS / second).toInt() + 1) { room.beat() }
        assertEquals(emptySet(), room.kept(), "a pair whose need ended outlived its grace")
        for (h in handles) {
            assertTrue(room.plans.any { (_, p) -> RangingStep.Stop(h) in p.steps }, "the pair $h was never stopped")
        }
        assertTrue(phones.all { it.held.isEmpty() }, "a phone still ranges a pair the game no longer needs")
    }

    /** **GUARD (D-306): a sensor is paired with the close phones it could place**, and they not with each other. */
    @Test
    fun aSensorIsPairedWithTheClosePhonesItCouldPlace() {
        val phones = (0 until 3).map { Phone(it) }
        val room = Room(withSensor(onDistance(3), 0, since = 1), phones, byTheGame = true)
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(1.2, 0.0)
        room.spots[2] = Spot(0.5, 1.0)
        repeat(4) { room.beat() }
        assertEquals(setOf(0 to 1, 0 to 2), room.kept(), "the sensor is not paired with exactly the close phones it could place")
        assertTrue(room.allHeld(), "a pair the house keeps is not held by both its phones")
    }

    /**
     * **GUARD (D-306): a needed pair below the near floor is not paired**, and is once it walks in.
     * An armed Insider and a phone 4.5 m off (−69 on the bench, under −68): nothing. At 3.5 m (−67):
     * paired.
     */
    @Test
    fun aNeededPairBelowTheNearFloorIsNotPaired() {
        val phones = (0 until 2).map { Phone(it) }
        val room = Room(withArm(onDistance(3, insiders = listOf(1)), 1, ready + 1), phones, byTheGame = true)
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(4.5, 0.0)
        room.tick = ready + 1
        repeat(6) { room.beat() }
        assertEquals(emptySet(), room.kept(), "a needed pair under the near floor was paired")
        room.spots[1] = Spot(3.5, 0.0)
        repeat(3) { room.beat() }
        assertEquals(setOf(0 to 1), room.kept(), "a needed pair inside the near floor was not paired")
    }

    /**
     * **GUARD (D-306): an armed Revoke in a crowd still pairs the Insider, within its capacity.** Ten
     * phones within a metre and a half, every one of capacity three; seat 7 is Revoked, so nine
     * living phones could report it and all want it. The Insider (seat 4) arms: it is paired to its
     * capacity, every pair the house keeps is one the game needs, and no phone is past its capacity,
     * beat by beat.
     */
    @Test
    fun aRevokeArmedInACrowdStillPairsTheInsiderWithinItsCapacity() {
        val phones = (0 until 10).map { Phone(it) }
        val base = onDistance(10, insiders = listOf(4)).copy(revoked = listOf(Seat(7)))
        val room = Room(base, phones, byTheGame = true)
        room.deaf += 7
        for (i in 0 until 10) room.spots[i] = Spot((i % 4) * 0.45, (i / 4) * 0.5)
        room.tick = ready
        repeat(4) { room.beat() }
        room.state = withArm(room.state, 4, room.tick)
        var beats = 0
        while (beats < 10 && (room.pairs(4).size < 3 || !room.allHeld())) {
            room.beat()
            beats++
            for (p in phones) assertTrue(room.pairs(p.seat).size <= p.capacity, "seat ${p.seat} is in ${room.pairs(p.seat).size} pairs at capacity ${p.capacity}")
        }
        println("D-306 AN ARMED REVOKE IN A CROWD — the Insider ranging ${room.pairs(4).size} after $beats beats; pairs ${room.kept()}")
        assertEquals(3, room.pairs(4).size, "the armed Insider was not paired to its capacity in a crowd (pairs ${room.kept()})")
        assertTrue(room.allHeld(), "a pair the house keeps is not held by both its phones")
        assertTrue(room.kept().all { (a, b) -> a == 4 || b == 4 || a == 7 || b == 7 }, "a pair the game does not need was kept: ${room.kept()}")
    }

    /**
     * **The need reads what the rules would act on, not a role** (D-306). Two rounds with the same
     * Revoked seat and different Insiders, nobody armed, pair exactly alike: an unarmed Insider is
     * paired as any Resident is.
     */
    @Test
    fun anUnarmedInsiderIsPairedExactlyAsAResidentIs() {
        val caps = listOf(3, 2, 3, 1, 4, 3, 3, 2)
        fun round(insiders: List<Int>): Room {
            val room = Room(onDistance(caps.size, insiders).copy(revoked = listOf(Seat(5))), caps.mapIndexed { i, c -> Phone(i, c) }, byTheGame = true)
            room.deaf += 5
            for (i in caps.indices) room.spots[i] = Spot((i % 3) * 0.9, (i / 3) * 0.8)
            repeat(6) { room.beat() }
            return room
        }
        val base = round(listOf(1))
        val swapped = round(listOf(4, 6))
        assertTrue(base.memory.pairs.isNotEmpty(), "the fixture paired nobody")
        assertEquals(base.plans, swapped.plans, "the plans moved with who the Insiders are")
        assertEquals(base.memory, swapped.memory, "the pairs moved with who the Insiders are")
    }

    // ---- A second delivery -----------------------------------------------------------------------

    @Test
    fun aSecondDeliveryChangesNothingTheHouseHolds() {
        val a = Phone(0)
        val b = Phone(1)
        val room = Room(armed(2), listOf(a, b))
        room.spots[0] = Spot(0.0, 0.0)
        room.spots[1] = Spot(0.5, 0.0)
        room.beat()
        val before = room.memory
        room.up(b, report = RangingReport(6, emptyList(), emptyList()), taken = false)
        assertEquals(before, room.memory, "an upload not taken moved the pairing")
    }
}
