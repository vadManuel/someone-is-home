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
import home.someoneshome.model.protocol.SpareKey
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
 * steady room (every pair formed and held, every phone told to keep), each upload a real report
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
    private val state: GameState =
        reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 20261119L, seats = seats, insiders = listOf(Seat(2), Seat(9)))).state
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
        repeat(12) {
            for (s in seats) {
                val plan = desk.answer(Tick(tick++), s, sightings[s.index], book, phones[s.index].field(seeds[s.index], nonce), true, nonce)!!
                phones[s.index].apply(plan, seeds[s.index])
            }
            tick += 20
        }
        val pairs = desk.pairing.pairs()
        assertTrue(pairs.size >= 9, "the room settled into only ${pairs.size} pairs")
        for (p in phones) assertEquals(p.held.keys, pairs.filter { it.has(Seat(p.seat)) }.map { it.handle }.toSet(), "seat ${p.seat} is not ranging what the house keeps")

        // The steady second, measured: each seat's upload as its phone would send it.
        val fields = seats.map { phones[it.index].field(seeds[it.index], nonce) }
        val rounds = 200
        var sink: String? = null
        repeat(20) { for (s in seats) sink = desk.answer(Tick(tick++), s, sightings[s.index], book, fields[s.index], true, nonce) }
        val before = allocated()
        repeat(rounds) {
            for (s in seats) sink = desk.answer(Tick(tick++), s, sightings[s.index], book, fields[s.index], true, nonce)
            tick += 20
        }
        val perUpload = (allocated() - before) / (rounds * seats.size)
        check(sink != null)
        assertEquals(pairs.map { it.handle }, desk.pairing.pairs().map { it.handle }, "the measured seconds changed the pairing, so they were not steady")
        println("D-300 THE HOUSE'S RANGING PATH — $perUpload B per seat per beat (the plan's string is ${RangingWire.PLAN_CHARS} characters)")
        assertTrue(perUpload <= 2_048, "the house's ranging path allocates $perUpload bytes per seat per beat, over its ceiling of 2048 (rule 7, D-300)")
    }
}
