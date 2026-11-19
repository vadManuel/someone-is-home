package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Hmac
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The UWB report and plan on the wire (U6, D-300): one width whatever they carry, sealed so the
 * filler cannot be told from a real value without the seed, and read fail-closed.**
 */
@OptIn(ExperimentalEncodingApi::class)
class RangingWireTest {

    private val seed = ContactSeed("00112233445566778899aabbccddeeff")
    private val other = ContactSeed("ffeeddccbbaa99887766554433221100")

    private var nonces = 0L

    /** A fresh nonce per field, as the phone's secure source would give: here a counter run through a MAC. */
    private fun nonce(): ByteArray = Hmac.sha256(byteArrayOf(9), longBytes(nonces++)).copyOf(RangingWire.NONCE_BYTES)

    private fun longBytes(v: Long) = ByteArray(8) { i -> (v ushr (56 - 8 * i)).toByte() }

    private fun hex(n: Int, bytes: Int) = Hmac.sha256(byteArrayOf(7), longBytes(n.toLong())).copyOf(bytes).let(WireHex::of)

    private fun handle(n: Int) = RangingHandle(hex(n, RangingWire.HANDLE_BYTES))
    private fun key(n: Int) = SpareKey(hex(1_000 + n, RangingWire.KEY_BYTES))

    /**
     * **A token shaped like an archived `NIDiscoveryToken`**: a binary property list's magic, then
     * structure that is mostly low and repeated bytes, [length] long. Real values are this regular,
     * which is why the field is sealed rather than padded.
     */
    private fun token(n: Int, length: Int = 312 + n % 34) = RangingToken.of(
        ByteArray(length) { i ->
            when {
                i < 8 -> "bplist00"[i].code.toByte()
                i % 17 == 0 -> (n + i).toByte()
                else -> (i % 5).toByte()
            }
        },
    )

    private fun reading(n: Int, cm: Int?, count: Int) = RangingReading(handle(n), cm, count)

    /** Every shape a report can take: no UWB, idle, full, out of range numbers, the longest tokens. */
    private val reports: List<RangingReport> = listOf(
        RangingReport.NONE,
        RangingReport(3, (0 until 3).map { RangingSpare(key(it), token(it)) }, emptyList()),
        RangingReport(1, emptyList(), listOf(reading(0, 21, 7))),
        RangingReport(
            6,
            (0 until 6).map { RangingSpare(key(it), token(it, RangingWire.TOKEN_BYTES)) },
            (0 until 6).map { reading(it, -8 + it * 500, it + 1) },
        ),
        RangingReport(6, (0 until 6).map { RangingSpare(key(it), token(it, 1)) }, (0 until 6).map { reading(it, null, 0) }),
        RangingReport(2, listOf(RangingSpare(key(9), token(9))), listOf(reading(3, 32_767, 255), reading(4, -32_768, 1))),
    )

    private fun start(n: Int, length: Int = 312) = RangingStep.Start(handle(n), key(n), token(n, length))

    private val plans: List<RangingPlan> = listOf(
        RangingPlan.EMPTY,
        RangingPlan(listOf(start(0))),
        RangingPlan(listOf(RangingStep.Keep(handle(1)))),
        RangingPlan((0 until 6).map { start(it, RangingWire.TOKEN_BYTES) }),
        RangingPlan((0 until 6).map { RangingStep.Stop(handle(it)) }),
        RangingPlan(listOf(RangingStep.Stop(handle(0)), RangingStep.Keep(handle(1)), start(2, 1))),
    )

    // ---- One width -------------------------------------------------------------------------------

    @Test
    fun everyReportIsOneWidthAndEveryUploadCarryingOneIsOneLength() {
        val fields = reports.map { RangingWire.packReport(seed, nonce(), it) }
        assertEquals(setOf(RangingWire.REPORT_CHARS), fields.map { it.length }.toSet(), "a report's width moved with what it carried")
        // The whole upload, through the encoder every phone uses, from a phone that heard nobody and
        // one that heard many, asked for nothing and something, with every report above.
        val window = ContactWindow(4_000L)
        val frames = fields.flatMap { field ->
            listOf(
                SightingUpload.pack(seed, window, 0, emptyList(), ranging = field),
                SightingUpload.pack(other, window, 7, emptyList(), ask = RequestSlot.empty("0123456789abcdef"), probe = "00000000000003e8", ranging = field),
            )
        }.map { TransportWire.encode(TransportFrame.Carry(RoundWire.encode(it))).encodeToByteArray().size }
        assertEquals(1, frames.toSet().size, "uploads differ in length by their ranging: ${frames.toSet()}")
        assertEquals(RangingWire.REPORT_CHARS, RangingWire.NO_REPORT.length)
    }

    @Test
    fun everyPlanIsOneWidthAndEveryAnswerCarryingOneIsOneLength() {
        val fields = plans.map { RangingWire.packPlan(seed, nonce(), it) } + RangingWire.NO_PLAN +
            RangingWire.blankPlan(ByteArray(RangingWire.PLAN_FIELD_BYTES) { it.toByte() })
        assertEquals(setOf(RangingWire.PLAN_CHARS), fields.map { it.length }.toSet(), "a plan's width moved with what it said")
        val answers = fields.map {
            TransportWire.encode(
                TransportFrame.Carry(RoundWire.encode(RoundBody.UploadHeard.of("0123456789abcdef", UploadProbe.NONE, 5L, 6L, it))),
            ).encodeToByteArray().size
        }
        assertEquals(1, answers.toSet().size, "answers differ in length by their plan: ${answers.toSet()}")
    }

    // ---- What goes in comes out --------------------------------------------------------------------

    @Test
    fun aReportAndAPlanComeBackAsTheyWentUnderTheSeatsSeed() {
        for (report in reports) assertEquals(report, RangingWire.unpackReport(seed, RangingWire.packReport(seed, nonce(), report)))
        for (plan in plans) assertEquals(plan, RangingWire.unpackPlan(seed, RangingWire.packPlan(seed, nonce(), plan)))
        // A distance past a signed 16-bit count of centimetres (327 m either way) is clamped, not wrapped.
        val far = RangingReport(1, emptyList(), listOf(reading(5, 99_999, 3), reading(6, -99_999, 3)))
        assertEquals(listOf(32_767, -32_768), RangingWire.unpackReport(seed, RangingWire.packReport(seed, nonce(), far))?.readings?.map { it.nearestCm })
        // A decoded body carries the field as it was sent.
        val sent = SightingUpload.pack(seed, ContactWindow(1), 0, emptyList(), ranging = RangingWire.packReport(seed, nonce(), reports[3]))
        val back = RoundWire.decodeOrNull(RoundWire.encode(sent)) as RoundBody.Sighted
        assertEquals(reports[3], RangingWire.unpackReport(seed, back.ranging))
    }

    // ---- The filler cannot be told from a real value without the seed ----------------------------

    /** The sealed bytes after the clear nonce. */
    private fun sealedOf(field: String) = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(field).let {
        it.copyOfRange(RangingWire.NONCE_BYTES, it.size)
    }

    /**
     * **A phone with no UWB, a phone offering three tokens and a phone ranging six pairs send fields
     * that look the same**: uniform bytes, nothing constant from one upload to the next, and no
     * region where the real slots sit that reads differently from where the empty ones sit. Real
     * tokens are property lists, mostly small and repeated bytes; sealed, none of that survives.
     */
    @Test
    fun theFillerAndTheRealValuesLookTheSameWithoutTheSeed() {
        val empty = RangingReport.NONE
        val full = reports[3]
        val rounds = 64
        val emptyFields = List(rounds) { sealedOf(RangingWire.packReport(seed, nonce(), empty)) }
        val fullFields = List(rounds) { sealedOf(RangingWire.packReport(seed, nonce(), full)) }
        for ((name, fields) in listOf("empty" to emptyFields, "full" to fullFields)) {
            val all = fields.flatMap { f -> f.map { it.toInt() and 0xff } }
            val mean = all.average()
            assertTrue(mean in 125.0..130.5, "the $name report's bytes are not uniform: mean $mean")
            val zeros = all.count { it == 0 }.toDouble() / all.size
            assertTrue(zeros < 0.006, "the $name report shows its zeros: ${zeros * 100}% of bytes are 0")
            // No position holds one value across every upload: nothing persists for a reader to count.
            val constant = (0 until fields[0].size).count { i -> fields.all { it[i] == fields[0][i] } }
            assertEquals(0, constant, "the $name report has $constant byte positions that never change")
        }
        // Region by region, where the first token sits, where the last empty slot sits: the same.
        val firstToken = 1 + 1 + RangingWire.KEY_BYTES + 2 until 1 + 1 + RangingWire.KEY_BYTES + 2 + 300
        for (region in listOf(firstToken, 0 until 200)) {
            val e = emptyFields.flatMap { f -> region.map { f[it].toInt() and 0xff } }.average()
            val f = fullFields.flatMap { f -> region.map { f[it].toInt() and 0xff } }.average()
            assertTrue(kotlin.math.abs(e - f) < 4.0, "a real token's region reads $f against the empty slot's $e")
        }
        // And without the seed neither opens: another seat's seed reads nothing from either.
        assertNull(RangingWire.unpackReport(other, RangingWire.packReport(seed, nonce(), full)))
        assertNull(RangingWire.unpackReport(other, RangingWire.packReport(seed, nonce(), empty)))
    }

    @Test
    fun aPlanThatSaysNothingLooksLikeAPlanThatStartsSix() {
        val rounds = 64
        for (plan in listOf(RangingPlan.EMPTY, plans[3])) {
            val fields = List(rounds) { sealedOf(RangingWire.packPlan(seed, nonce(), plan)) }
            val all = fields.flatMap { f -> f.map { it.toInt() and 0xff } }
            assertTrue(all.average() in 125.0..130.5, "the plan $plan reads ${all.average()} on average")
            assertTrue(all.count { it == 0 }.toDouble() / all.size < 0.006, "the plan shows its zeros")
            val constant = (0 until fields[0].size).count { i -> fields.all { it[i] == fields[0][i] } }
            assertEquals(0, constant, "a plan has $constant byte positions that never change")
        }
    }

    // ---- Fail closed --------------------------------------------------------------------------------

    @Test
    fun aFieldThatIsNotExactlyOneReadsAsNothing() {
        val report = RangingWire.packReport(seed, nonce(), reports[3])
        val plan = RangingWire.packPlan(seed, nonce(), plans[3])
        for ((field, read) in listOf<Pair<String, (String) -> Any?>>(
            report to { RangingWire.unpackReport(seed, it) },
            plan to { RangingWire.unpackPlan(seed, it) },
        )) {
            assertNotNull(read(field), "the good field did not read")
            assertNull(read(field.dropLast(1)), "a short field read")
            assertNull(read(field + "A"), "a long field read")
            assertNull(read(field.replaceRange(40, 41, "+")), "a field with a character outside URL-safe base64 read")
            assertNull(read(""), "nothing read")
            // A flipped character anywhere: the nonce, the body, the tag.
            for (at in listOf(0, 5, 11, 200, field.length / 2, field.length - 12, field.length - 2)) {
                val c = field[at]
                val flipped = field.replaceRange(at, at + 1, (if (c == 'A') 'B' else 'A').toString())
                assertNull(read(flipped), "a field with character $at changed still read")
            }
        }
        assertNull(RangingWire.unpackReport(seed, RangingWire.NO_REPORT), "the bench's empty report read as one")
        assertNull(RangingWire.unpackPlan(seed, RangingWire.NO_PLAN), "the test's empty plan read as one")
        assertNull(RangingWire.unpackPlan(seed, report), "a report read as a plan")
        // A report sealed under a plan's domain, and the other way, is the wrong field.
        assertNull(RangingWire.unpackPlan(seed, RangingWire.packReport(seed, nonce(), RangingReport.NONE)))
    }

    /**
     * **Under a good tag, a slot that does not read sinks the whole field.** Sealed by hand, past the
     * packers, because no packer builds them.
     */
    @Test
    fun aGoodlySealedFieldWithABadSlotReadsAsNothing() {
        val reportBytes = 1 + 6 * (1 + 8 + 2 + 384) + 6 * (1 + 8 + 2 + 1)
        val planBytes = 6 * (1 + 8 + 8 + 2 + 384)
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        fun report(edit: (ByteArray) -> Unit): RangingReport? {
            val plain = ByteArray(reportBytes).also(edit)
            val field = b64.encode(RangingWire.seal(seed, nonce(), RangingWire.DOMAIN_REPORT, RangingWire.DOMAIN_REPORT_TAG, plain))
            assertEquals(RangingWire.REPORT_CHARS, field.length)
            return RangingWire.unpackReport(seed, field)
        }
        fun plan(edit: (ByteArray) -> Unit): RangingPlan? {
            val plain = ByteArray(planBytes).also(edit)
            val field = b64.encode(RangingWire.seal(seed, nonce(), RangingWire.DOMAIN_PLAN, RangingWire.DOMAIN_PLAN_TAG, plain))
            return RangingWire.unpackPlan(seed, field)
        }
        val spare = 1
        val readings = 1 + 6 * (1 + 8 + 2 + 384)
        assertEquals(RangingReport.NONE, report {}, "an all-empty report did not read")
        assertNull(report { it[0] = 7 }, "a capacity of 7 read")
        assertNull(report { it[spare] = 2 }, "a spare flag of 2 read")
        assertNull(report { it[spare + 3] = 1 }, "an empty spare slot with a byte in it read")
        assertNull(report { it[spare] = 1 }, "a spare with a token of no bytes read")
        assertNull(report { it[spare] = 1; it[spare + 9] = 0x01; it[spare + 10] = 0x81.toByte() }, "a spare with a 385-byte token read")
        assertNull(report { it[spare] = 1; it[spare + 10] = 3; it[spare + 11 + 3] = 5 }, "a spare's pad with a byte in it read")
        assertNull(report { it[readings] = 1; it[readings + 10] = 4 }, "a distance with no reading behind it read")
        assertNull(report { it[readings + 12 + 1] = 1 }, "an empty reading slot with a byte in it read")
        assertNull(report {
            it[readings] = 1; it[readings + 11] = 1
            it[readings + 12] = 1; it[readings + 12 + 11] = 1
        }, "two readings of one pair read")
        assertNull(report {
            for (s in listOf(spare, spare + 395)) { it[s] = 1; it[s + 10] = 1; it[s + 11] = 9 }
        }, "two spares under one key read")
        assertEquals(RangingPlan.EMPTY, plan {}, "an all-empty plan did not read")
        assertNull(plan { it[0] = 4 }, "a step of kind 4 read")
        assertNull(plan { it[0] = 2; it[9] = 1 }, "a keep carrying a key read")
        assertNull(plan { it[0] = 1 }, "a start with no token read")
        assertNull(plan { it[0] = 3; it[403] = 3 }, "two steps for one pair read")
        assertNull(plan { it[1] = 1 }, "an empty step with a byte in it read")
    }

    @Test
    fun aTokenTooLongForItsSlotCannotBeBuilt() {
        val long = ByteArray(RangingWire.TOKEN_BYTES + 1)
        assertTrue(runCatching { RangingToken.of(long) }.isFailure, "a 385-byte token was built")
        assertTrue(runCatching { RangingToken.of(ByteArray(0)) }.isFailure, "an empty token was built")
        assertNotEquals(RangingWire.REPORT_CHARS, 0)
    }
}
