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
        (0 until 3).map { RangingSpare(SpareKey("00000000000000a$it"), RangingToken("ab".repeat(345))) },
        (0 until 3).map { RangingReading(RangingHandle("00000000000000b$it"), 40 + it, 6) },
    )
    private val fullPlan = RangingPlan(
        (0 until 3).map { RangingStep.Start(RangingHandle("00000000000000c$it"), SpareKey("00000000000000d$it"), RangingToken("cd".repeat(345))) } +
            (0 until 3).map { RangingStep.Keep(RangingHandle("00000000000000e$it")) },
    )
    private val nonce = ByteArray(RangingWire.NONCE_BYTES) { 5 }

    /**
     * **D-300: the ranging report, packed on every phone a beat and opened by the host per seat a
     * beat.** Sealing is one digest per 32 bytes into one held block, in place in the field's one
     * array; what is left is that array and the base64 string. Measured on the JVM at first landing:
     * 11.9 KB to pack and 18.0 KB to open a report, 11.8 and 17.5 KB a plan (half of it the base64
     * string and the three tokens as hex). The ceilings are that with headroom.
     */
    @Test
    fun `the ranging report costs its bytes to pack and to open`() {
        val packed = perCall(500) { RangingWire.packReport(seeds[0], nonce, fullReport) }
        val field = RangingWire.packReport(seeds[0], nonce, fullReport)
        val opened = perCall(500) { RangingWire.unpackReport(seeds[0], field) }
        println("D-300 RANGING REPORT — pack $packed B, open $opened B")
        assertUnder(16_384, packed, "packing a ranging report")
        assertUnder(24_576, opened, "opening a ranging report")
    }

    /** **D-300: the plan, sealed by the host per seat a beat and opened by every phone a beat.** */
    @Test
    fun `the ranging plan costs its bytes to pack and to open`() {
        val packed = perCall(500) { RangingWire.packPlan(seeds[0], nonce, fullPlan) }
        val field = RangingWire.packPlan(seeds[0], nonce, fullPlan)
        val opened = perCall(500) { RangingWire.unpackPlan(seeds[0], field) }
        println("D-300 RANGING PLAN — pack $packed B, open $opened B")
        assertUnder(16_384, packed, "packing a ranging plan")
        assertUnder(24_576, opened, "opening a ranging plan")
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
