package home.someoneshome.core

import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RangingCodec
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingStep
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.SpareKey
import home.someoneshome.model.protocol.SightingUpload
import java.lang.management.ManagementFactory
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **GUARD (rule 7, D-300): what the house's ranging path costs per seat per second, in bytes
 * allocated.**
 *
 * The house runs [RangingDesk.answer] for every seat's upload every second: the Bluetooth slots
 * resolved to seats, the report opened, the pairing told, the plan sealed. Eighteen seats, in a
 * steady room (every pair the game needs formed and held, every phone told to keep), each upload a real report
 * from a phone's own codec and the slots a real upload carries. Measured on the JVM, by the
 * thread's own allocation counter, as `UploadAllocationTest` measures `model`'s codecs (D-276).
 *
 * What it should cost is the plan's own string, [RangingWire.PLAN_CHARS] characters, and nothing
 * else: the codec decodes, opens and seals in held arrays, the pairing works in arrays sized at
 * arming, and an idle session's token is copied once, the first time its key is seen.
 */
class RangingAllocationTest {

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun allocated(): Long = threads.getThreadAllocatedBytes(Thread.currentThread().id)

    private val seats = (0 until 18).map { Seat(it) }

    /**
     * A round (every one reads distance, D-308), the middle seat of every group of three Revoked and not yet
     * reported: since D-306 the house pairs only close phones the game needs, and a Revoked seat's
     * close living neighbours are needed for the report, so the room still holds twelve pairs.
     */
    private val state: GameState =
        reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 20261119L, seats = seats, insiders = listOf(Seat(2), Seat(9))),
        ).state.let { s -> s.copy(revoked = seats.filter { it.index % 3 == 1 }) }
    private val window = ContactWindow(117_000L)

    /** A phone's pool: three sessions, the spares it offers and the pairs it holds. */
    private class Phone(val seat: Int) {
        val codec = RangingCodec()
        var made = 0L
        val spares = mutableListOf<RangingSpare>()
        val held = LinkedHashMap<RangingHandle, RangingToken>()

        fun fresh(): RangingSpare {
            made++
            return RangingSpare(SpareKey(seat * 1_000L + made), RangingToken.of(ByteArray(330) { (it + seat + made).toByte() }))
        }

        fun field(seed: home.someoneshome.model.ContactSeed, nonce: ByteArray): String {
            while (spares.size + held.size < RangingWire.STARTING_CAPACITY) spares += fresh()
            return codec.packReport(
                seed, nonce, RangingWire.STARTING_CAPACITY, spares.take(RangingWire.TOKEN_SLOTS),
                held.keys.map { RangingReading(it, 80, 6) },
            )
        }

        fun apply(plan: String, seed: home.someoneshome.model.ContactSeed) {
            val steps = RangingWire.unpackPlan(seed, plan)?.steps ?: error("seat $seat's plan did not open")
            for (s in steps) if (s is RangingStep.Stop) held.remove(s.handle)
            for (s in steps) if (s is RangingStep.Start) {
                val own = spares.firstOrNull { it.key == s.own } ?: continue
                spares.remove(own)
                held[s.handle] = s.peer
            }
        }
    }

    @Test
    fun `the house's ranging path costs one plan's string per seat per beat`() {
        val desk = rangingDeskFor(state)
        val book = resolveContacts(ContactMemory.EMPTY, Tick(0), window, emptyList(), state).memory.book
        val phones = seats.map { Phone(it.index) }
        // Six groups of three, a room apart: everybody hears everybody, and pairs close by.
        fun spot(i: Int) = Pair((i / 3) * 6.0 + (i % 3) * 0.7, (i % 3) * 0.4)
        val sightings = seats.map { me ->
            seats.filter { it != me }.map { other ->
                val (x1, y1) = spot(me.index)
                val (x2, y2) = spot(other.index)
                val d = sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)).coerceAtLeast(0.05)
                Sighting(ContactTokens.of(contactSeedFor(state.seed, other), window), (-30.0 - 20.0 * log10(d / 0.05)).roundToInt().coerceAtLeast(-100), window)
            }.take(17)
        }
        val seeds = seats.map { contactSeedFor(state.seed, it) }
        val nonce = ByteArray(RangingWire.NONCE_BYTES) { 3 }
        var tick = 0L

        // Settle: every phone uploads, takes its plan and acts on it, until nothing changes.
        // Until the pairs have stood unchanged for three beats, every phone holding them (D-302: a
        // pair formed on the last beat of a fixed count is one the measured seconds would see go).
        var last: List<RangingHandle>? = null
        var steady = 0
        var settling = 0
        // A beat is one second, as the phones upload: a pair's second phone lists it two beats after
        // it forms, inside the pairing's three-second grace.
        while (steady < 3 && settling < 60) {
            val start = tick
            for (s in seats) {
                val plan = desk.answer(state, Tick(tick++), s, sightings[s.index], book, phones[s.index].field(seeds[s.index], nonce), true, nonce)!!
                phones[s.index].apply(plan, seeds[s.index])
            }
            tick = start + ContactGate.UPLOAD_TICKS
            settling++
            val handles = desk.pairing.pairs().map { it.handle }
            steady = if (handles == last) steady + 1 else 0
            last = handles
        }
        println("D-302 THE ALLOCATION ROOM SETTLED AFTER $settling BEATS")
        assertTrue(steady >= 3, "the room never settled in $settling beats")
        val pairs = desk.pairing.pairs()
        assertTrue(pairs.size >= 9, "the room settled into only ${pairs.size} pairs")
        for (p in phones) assertEquals(p.held.keys, pairs.filter { it.has(Seat(p.seat)) }.map { it.handle }.toSet(), "seat ${p.seat} is not ranging what the house keeps")

        // The steady second, measured: each seat's upload as its phone would send it.
        val fields = seats.map { phones[it.index].field(seeds[it.index], nonce) }
        val rounds = 200
        var sink: String? = null
        repeat(20) { for (s in seats) sink = desk.answer(state, Tick(tick++), s, sightings[s.index], book, fields[s.index], true, nonce) }
        val before = allocated()
        repeat(rounds) {
            for (s in seats) sink = desk.answer(state, Tick(tick++), s, sightings[s.index], book, fields[s.index], true, nonce)
            tick += 20
        }
        val perUpload = (allocated() - before) / (rounds * seats.size)
        check(sink != null)
        assertEquals(pairs.map { it.handle }, desk.pairing.pairs().map { it.handle }, "the measured seconds changed the pairing, so they were not steady")
        println("D-300 THE HOUSE'S RANGING PATH — $perUpload B per seat per beat (the plan's string is ${RangingWire.PLAN_CHARS} characters)")
        assertTrue(perUpload <= 2_048, "the house's ranging path allocates $perUpload bytes per seat per beat, over its ceiling of 2048 (rule 7, D-300)")
    }

    /**
     * **GUARD (rule 7, D-301, D-308): the house's whole path per seat per beat, the rules read from
     * distance.** The same eighteen-seat room, settled: the report opened first and its distances
     * resolved to seats, the resolver over them with the report's hold, then the pairing and the
     * plan with the slot sealed in, as `RoundAuthority.sight` runs it. Each upload is a fresh packed
     * one, so the resolver takes every one as new, as it would on the air. The middle seat of each
     * group of three is Revoked, so the group's living phones are paired with it (D-306), and the
     * holding variant reads each of them at 20 cm.
     *
     * **The ceilings are D-301's, made absolute and not loosened.** Until D-308 this measured the
     * distance path against the Bluetooth rules' path in the same room (6 477 B on `183f387`) and
     * allowed it 1 024 B more, and holding 1 536 B more: 7 501 B and 8 013 B. With no Bluetooth
     * rules left there is no baseline to subtract, so the ceilings are now 7 168 B and 7 680 B, each
     * under the limit it replaces. Measured on `183f387`: 6 416 B and 6 607 B.
     */
    @Test
    fun `the house's path on distance costs what it did per seat per beat`() {
        fun measure(holding: Boolean): Long {
            val round = reduce(
                GameState.EMPTY,
                Event.RoundArmed(Tick(0), seed = 20261119L, seats = seats, insiders = listOf(Seat(2), Seat(9))),
            ).state.let { s -> s.copy(revoked = seats.filter { it.index % 3 == 1 }) }
            val desk = rangingDeskFor(round)
            val phones = seats.map { Phone(it.index) }
            val seeds = seats.map { contactSeedFor(round.seed, it) }
            fun spot(i: Int) = Pair((i / 3) * 6.0 + (i % 3) * 0.7, (i % 3) * 0.4)
            val slots = seats.map { me ->
                seats.filter { it != me }.map { other ->
                    val (x1, y1) = spot(me.index)
                    val (x2, y2) = spot(other.index)
                    val d = sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)).coerceAtLeast(0.05)
                    Sighting(ContactTokens.of(contactSeedFor(round.seed, other), window), (-30.0 - 20.0 * log10(d / 0.05)).roundToInt().coerceAtLeast(-100), window)
                }.take(17)
            }
            val nonce = ByteArray(RangingWire.NONCE_BYTES) { 3 }
            var memory = ContactMemory.EMPTY
            var tick = 0L
            var counter = 0L
            fun upload(s: Seat): List<Sighting> =
                SightingUpload.unpack(SightingUpload.pack(seeds[s.index], window, counter++, slots[s.index]))!!
            // One beat of the house's path for [s], as `RoundAuthority.sight` runs it.
            fun beat(s: Seat, sightings: List<Sighting>, field: String): String? {
                val at = Tick(tick++)
                val distances = desk.read(s, field)
                val arrival = ContactUpload(s, sightings, distances, onMainView = true)
                val r = resolveContacts(memory, at, window, listOf(arrival), round)
                memory = r.memory
                return desk.answer(round, at, s, sightings, memory.book, r.taken.isNotEmpty(), r.slotOf(s), nonce)
            }
            repeat(12) {
                for (s in seats) {
                    val plan = beat(s, upload(s), phones[s.index].field(seeds[s.index], nonce))!!
                    phones[s.index].apply(plan, seeds[s.index])
                }
                tick += 20
            }
            // When holding, every phone ranging a Revoked seat reads it at 20 cm.
            val fields = seats.map { s ->
                val p = phones[s.index]
                val near = if (holding) desk.pairing.pairs().filter { it.has(s) && (round.isRevoked(it.low) || round.isRevoked(it.high)) }.map { it.handle }.toSet() else emptySet()
                p.codec.packReport(
                    seeds[s.index], nonce, RangingWire.STARTING_CAPACITY, p.spares.take(RangingWire.TOKEN_SLOTS),
                    p.held.keys.map { RangingReading(it, if (it in near) 20 else 80, 6) },
                )
            }
            val rounds = 40
            val prepared = List(rounds + 4) { seats.map { upload(it) } }
            var sink: String? = null
            for (k in 0 until 4) { for (s in seats) sink = beat(s, prepared[k][s.index], fields[s.index]); tick += 20 }
            val before = allocated()
            for (k in 4 until rounds + 4) {
                for (s in seats) sink = beat(s, prepared[k][s.index], fields[s.index])
                tick += 20
            }
            check(sink != null)
            return (allocated() - before) / (rounds * seats.size)
        }
        val path = measure(holding = false)
        val held = measure(holding = true)
        println("D-308 THE HOUSE'S PATH PER SEAT PER BEAT — distance rules $path B · with every pair of a Revoked seat holding a report $held B (+${held - path})")
        assertTrue(path <= 7_168, "the house's path on distance allocates $path bytes per seat per beat, over its ceiling of 7168 (rule 7, D-301, D-308)")
        assertTrue(held <= 7_680, "the house's path holding reports allocates $held bytes per seat per beat, over its ceiling of 7680 (rule 7, D-301, D-308)")
    }
}
