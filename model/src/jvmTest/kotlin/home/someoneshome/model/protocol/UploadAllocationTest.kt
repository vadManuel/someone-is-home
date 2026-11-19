package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Hmac
import home.someoneshome.model.Sighting
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **GUARD (rule 7, D-276): what one upload costs to build and to read, in bytes allocated.**
 *
 * Every phone packs one upload a beat and the host unpacks one per seat a beat, so these two are
 * paid per second per seat for the whole round, against a whole-app budget of about 0.5 MB/s. A
 * simulator round measured the host's unpacking alone at 26 KB an upload before D-276, and a phone's
 * packing at about 75 KB a beat on the JVM: a quarter of the budget spent turning 1 200 characters
 * into 24 slots and back.
 *
 * Measured on the JVM, by the thread's own allocation counter, because that is exact and cheap
 * and Kotlin/Native has nothing like it (D-276). The JVM's objects are not Kotlin/Native's (compact
 * strings, other headers), so the ceilings are **this code's cost with headroom**, not a phone's
 * figure: what they catch is a change of kind, an object per slot or per hex pair coming back.
 * Each ceiling is under a third of what the code before D-276 allocated, which is what was checked
 * by putting that code back.
 */
class UploadAllocationTest {

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Volatile
    private var sink: Any? = null

    /** Bytes [block] allocates per call on this thread, over [times] calls after as many to warm up. */
    private fun perCall(times: Int = 2_000, block: () -> Any?): Long {
        repeat(times) { sink = block() }
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        repeat(times) { sink = block() }
        return (threads.getThreadAllocatedBytes(id) - before) / times
    }

    private val seeds = (0 until 6).map { ContactSeed.of(Hmac.sha256(byteArrayOf(it.toByte()), "seat".encodeToByteArray())) }
    private val window = ContactWindow(117_000L)
    private val heard = (1 until 6).map { Sighting(ContactTokens.of(seeds[it], window), -50 - it, window) }

    private fun assertUnder(ceiling: Long, measured: Long, what: String) =
        assertTrue(measured <= ceiling, "$what allocates $measured bytes, over its ceiling of $ceiling (rule 7, D-276)")

    /** The host, once per seat per beat. 18 KB a call on the JVM before D-276; about 2.6 KB now. */
    @Test
    fun `reading one upload allocates its slots and little else`() {
        val body = SightingUpload.pack(seeds[0], window, 7, heard)
        assertUnder(4_096, perCall { SightingUpload.unpack(body) }, "unpacking one upload")
    }

    /** Every phone, once a beat. About 75 KB before D-276 heard nothing; about 6 to 8 KB now. */
    @Test
    fun `packing one upload allocates the body and little else`() {
        assertUnder(12_288, perCall { SightingUpload.pack(seeds[0], window, 7, emptyList()) }, "packing an upload that heard nothing")
        assertUnder(12_288, perCall { SightingUpload.pack(seeds[0], window, 7, heard) }, "packing an upload that heard five")
    }

    /**
     * Every phone builds an empty slot most beats, and the host reads one per seat per beat. Before
     * D-276 each encoded 384 zero bytes afresh and each read decoded them back: about 2.1 and 1.7 KB.
     */
    @Test
    fun `an empty request slot costs its id to build`() {
        assertUnder(1_280, perCall { RequestSlot.empty("0123456789abcdef") }, "building an empty slot")
    }

    @Test
    fun `an empty request slot costs its id to read`() {
        val slot = RequestSlot.empty("0123456789abcdef")
        assertUnder(256, perCall { RequestSlot.read(slot) }, "reading an empty slot")
    }

    private val fullReport = RangingReport(
        6,
        (0 until 3).map { RangingSpare(SpareKey(0xa0L + it), RangingToken.ofHex("ab".repeat(345))) },
        (0 until 3).map { RangingReading(RangingHandle(0xb0L + it), 40 + it, 6) },
    )
    private val fullPlan = RangingPlan(
        (0 until 3).map { RangingStep.Start(RangingHandle(0xc0L + it), SpareKey(0xd0L + it), RangingToken.ofHex("cd".repeat(345))) } +
            (0 until 3).map { RangingStep.Keep(RangingHandle(0xe0L + it)) },
    )
    private val nonce = ByteArray(RangingWire.NONCE_BYTES) { 5 }

    /**
     * **D-300: the ranging report, opened by the house for every seat every second, through its held
     * codec.** Decoded into the held field, its tag checked and its keystream run in place, every
     * slot validated, then read through the view: no allocation at all. Measured at first landing:
     * 0 B (the codec before D-300's rule 7 rework, a fresh array per step and the tokens as hex
     * strings, cost 18.0 KB).
     */
    @Test
    fun `the house opens a ranging report and reads it without allocating`() {
        val codec = RangingCodec()
        val field = codec.packReport(seeds[0], nonce, fullReport.capacity, fullReport.spares, fullReport.readings)
        var sum = 0L
        val opened = perCall {
            check(codec.openReport(seeds[0], field))
            for (i in 0 until codec.spareCount) sum += codec.spareKey(i) + if (codec.spareTokenIs(i, fullReport.spares[i].token)) 1 else 0
            for (i in 0 until codec.readingCount) sum += codec.readingHandle(i) + codec.readingCm(i) + codec.readingReadings(i)
            null
        }
        println("D-300 RANGING REPORT — the house opens and reads one: $opened B")
        assertUnder(64, opened, "opening and reading a ranging report")
        check(sum != 0L)
    }

    /**
     * **D-300: the plan, sealed by the house for every seat every second.** Written into the held
     * field, sealed in place and encoded from there: the one allocation is the plan's own string,
     * [RangingWire.PLAN_CHARS] characters. Measured at first landing: 1 744 B (was 11.8 KB).
     */
    @Test
    fun `the house seals a plan for the cost of its string`() {
        val codec = RangingCodec()
        val packed = perCall { codec.packPlan(seeds[0], nonce, fullPlan) }
        println("D-300 RANGING PLAN — the house seals one: $packed B for ${RangingWire.PLAN_CHARS} characters")
        assertUnder(2_048, packed, "sealing a ranging plan")
    }

    /**
     * **D-300: the phone's side, once a beat**: packing its report costs the report's string, and
     * opening its plan nothing until a start hands it a peer's token. Measured at first landing:
     * 1 736 B and 0 B (were 11.9 and 17.5 KB).
     */
    @Test
    fun `a phone packs its report for the cost of its string and opens its plan without allocating`() {
        val codec = RangingCodec()
        val packed = perCall { codec.packReport(seeds[0], nonce, fullReport.capacity, fullReport.spares, fullReport.readings) }
        val plan = codec.packPlan(seeds[0], nonce, RangingPlan(fullPlan.steps.filterIsInstance<RangingStep.Keep>()))
        var sum = 0L
        val opened = perCall {
            check(codec.openPlan(seeds[0], plan))
            for (i in 0 until codec.planSteps) sum += codec.planHandle(i) + codec.planKind(i)
            null
        }
        println("D-300 RANGING, A PHONE — packs its report: $packed B, opens a plan of keeps: $opened B")
        assertUnder(2_048, packed, "packing a ranging report")
        assertUnder(64, opened, "opening a plan")
        check(sum != 0L)
    }

    /** A keyed MAC into the caller's array allocates nothing at all; a seed's bytes are its array. */
    @Test
    fun `a MAC under a held key allocates nothing and a seed's bytes are one array`() {
        val mac = Hmac.Keyed(seeds[1].bytes())
        val message = ByteArray(18)
        val into = ByteArray(32)
        assertUnder(0, perCall { mac.mac(message, into = into) }, "a keyed MAC into a held array")
        assertUnder(64, perCall { seeds[1].bytes() }, "a seed's bytes")
    }
}
