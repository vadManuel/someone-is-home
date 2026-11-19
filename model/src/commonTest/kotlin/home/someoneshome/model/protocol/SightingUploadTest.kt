package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Hmac
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **D-212: every upload is the same size, whatever the phone is and whatever it heard.**
 *
 * An observer of the Wi-Fi must not be able to tell an armed phone from an unarmed one, or an
 * Insider's from a Resident's. [SightingUpload.pack] takes no role and no armed state, so the cases
 * below differ only in what each phone would plausibly have heard — which is the only thing that
 * *could* move the size, and is what is measured.
 */
class SightingUploadTest {

    private fun seedOf(i: Int) = ContactSeed.of(Hmac.sha256(byteArrayOf(i.toByte()), "test".encodeToByteArray()))
    private val seeds = (0 until 8).map { Seat(it) to seedOf(it) }
    private val now = ContactWindow(117_000L)

    private fun heard(seat: Int, rssi: Int, window: ContactWindow = now) =
        Sighting(ContactTokens.of(seeds[seat].second, window), rssi, window)

    /** The four phones the ruling names, and the ones that stress the slot widths. */
    private fun cases(): Map<String, RoundBody.Sighted> = mapOf(
        // Touching its target: one reading at contact range, and the room around it.
        "an armed Insider" to SightingUpload.pack(
            seedOf(1), now, 41, listOf(heard(3, -22), heard(3, -25), heard(4, -71), heard(5, -88)),
        ),
        "an unarmed Insider" to SightingUpload.pack(seedOf(1), now, 41, listOf(heard(4, -70), heard(5, -86))),
        "a Resident" to SightingUpload.pack(seedOf(3), now, 41, listOf(heard(1, -23), heard(6, -64))),
        "a phone that heard nothing" to SightingUpload.pack(seedOf(6), now, 41, emptyList()),
        "a phone that heard everybody, twice over" to SightingUpload.pack(
            seedOf(0), now, 9_999_999,
            (1 until 8).flatMap { listOf(heard(it, -40 - it), heard(it, -60, ContactWindow(now.index - 1))) } +
                (0 until 30).map { Sighting(ContactToken(it.toString(16).padStart(32, '0')), -95, now) },
        ),
        "readings at the ends of every range" to SightingUpload.pack(
            seedOf(2), ContactWindow(Long.MAX_VALUE), Long.MAX_VALUE,
            listOf(
                Sighting(ContactToken("f".repeat(32)), 500, ContactWindow(Long.MAX_VALUE)),
                Sighting(ContactToken("0".repeat(32)), -500, ContactWindow(0)),
            ),
        ),
        // D-263: the slot, empty and full, carrying what only an Insider sends and what anybody does.
        "an Insider arming in the slot" to SightingUpload.pack(
            seedOf(1), now, 41, emptyList(), RequestSlot.of("0123456789abcdef", Asked.Act(IntentMessage.ArmRevoke))!!,
        ),
        "an Insider's Egress in the slot" to SightingUpload.pack(seedOf(1), now, 41, emptyList(), RequestSlot.of("fedcba9876543210", Asked.Fire)!!),
        "a Resident's check-in in the slot" to SightingUpload.pack(
            seedOf(3), now, 41, emptyList(), RequestSlot.of("00000000000000aa", Asked.Act(IntentMessage.CheckIn))!!,
        ),
        "an empty slot under a random id" to SightingUpload.pack(seedOf(6), now, 41, emptyList(), RequestSlot.empty("9f9f9f9f9f9f9f9f")),
    )

    /** **The ruling, measured on the bytes a reader of the Wi-Fi would see.** */
    @Test
    fun `every upload frame is byte-identical in length`() {
        val frames = cases().mapValues { (_, body) ->
            TransportWire.encode(TransportFrame.Carry(RoundWire.encode(body)))
        }
        val lengths = frames.mapValues { it.value.encodeToByteArray().size }
        assertEquals(1, lengths.values.distinct().size, "the upload's size says something: $lengths")
        for ((name, body) in cases()) {
            assertEquals(SightingUpload.BODY_CHARS, body.slots.length, name)
        }
    }

    /** Three keys and nothing else: no seat, no sequence, no role, no armed bit. The slot is D-263's. */
    @Test
    fun `the body carries the slots and nothing about who sent it`() {
        for ((name, body) in cases()) {
            val keys = Json.parseToJsonElement(RoundWire.encode(body)).jsonObject.keys
            // The probe is random and fresh every upload (D-295): a number, never a seat or a time.
            assertEquals(setOf("type", "slots", "ask", "probe"), keys, name)
        }
    }

    /** What a phone heard comes back out, strongest reading per token and window. */
    @Test
    fun `the house reads back what the phone heard`() {
        val body = SightingUpload.pack(
            seedOf(1), now, 3, listOf(heard(3, -30), heard(3, -22), heard(4, -71), heard(4, -60, ContactWindow(now.index - 1))),
        )
        val slots = assertNotNull(SightingUpload.unpack(body))
        assertEquals(SightingUpload.SLOTS, slots.size)
        val real = slots.filter { ContactTokens.seatOf(it.token, it.window, seeds) != null }
        assertEquals(
            setOf(
                Triple(Seat(3), -22, now),
                Triple(Seat(4), -71, now),
                Triple(Seat(4), -60, ContactWindow(now.index - 1)),
            ),
            real.map { Triple(ContactTokens.seatOf(it.token, it.window, seeds)!!, it.rssi, it.window) }.toSet(),
        )
        // Round trip of the clamp and of the window's full width.
        val ends = assertNotNull(SightingUpload.unpack(cases().getValue("readings at the ends of every range")))
        assertTrue(ends.any { it.rssi == 127 && it.window == ContactWindow(Long.MAX_VALUE) })
        assertTrue(ends.any { it.rssi == -128 && it.window == ContactWindow(0) })
    }

    /** Filler resolves to no seat, so the house never sights a phone nobody heard. */
    @Test
    fun `padding is nobody`() {
        val empty = assertNotNull(SightingUpload.unpack(cases().getValue("a phone that heard nothing")))
        assertEquals(0, empty.count { ContactTokens.seatOf(it.token, it.window, seeds) != null })
        assertEquals(SightingUpload.SLOTS, empty.map { it.token }.distinct().size, "filler repeated a slot")
    }

    /** Past the slot count the strongest are kept: the end of the list that decides a contact. */
    @Test
    fun `an overfull upload keeps the strongest readings`() {
        val many = (0 until 40).map { Sighting(ContactToken(it.toString(16).padStart(32, '0')), -100 + it, now) }
        val kept = assertNotNull(SightingUpload.unpack(SightingUpload.pack(seedOf(0), now, 1, many)))
        assertEquals(many.sortedByDescending { it.rssi }.take(SightingUpload.SLOTS).toSet(), kept.toSet())
    }

    /** Deterministic: the same inputs pack to the same bytes, and the next upload's filler moves. */
    @Test
    fun `packing is deterministic and the filler moves per upload`() {
        val a = SightingUpload.pack(seedOf(6), now, 41, emptyList())
        assertEquals(a, SightingUpload.pack(seedOf(6), now, 41, emptyList()))
        assertTrue(a != SightingUpload.pack(seedOf(6), now, 42, emptyList()))
    }

    /**
     * **D-276 changed how an upload is built and read, and nothing it puts on the wire** (rule 7).
     *
     * Pinned to a computation outside this codebase (Python's `hmac`, the same filler, sorted and
     * joined), and compared, case by case and over a spread of seeds, windows, counters and
     * readings, with the packing and reading exactly as they were written before D-276 ([legacyPack],
     * [legacyUnpack]). A packing that saved its allocations by changing a character fails here.
     */
    @Test
    fun `packing and reading are the text and the readings they were before D-276`() {
        val nothing = SightingUpload.pack(seedOf(6), now, 41, emptyList())
        assertEquals(
            "f2a5a9d66128074753443d3507195a6b2fd8ce96d56fc14e3263687d32222471",
            home.someoneshome.model.Sha256.digest(nothing.slots.encodeToByteArray()).toHexString(),
        )
        val bodies = cases().values.toMutableList()
        for (seat in 0 until 8) for (upload in listOf(0L, 1L, 255L, 256L, 1L shl 40, Long.MAX_VALUE)) {
            val window = ContactWindow(now.index + seat * 7 - 20)
            val heard = (0 until seat * 3).map { i ->
                heard((seat + i) % 8, -20 - 9 * i + (i % 3) * 200 - 150, if (i % 2 == 0) window else ContactWindow(window.index - 1))
            }
            val ask = RequestSlot.empty("0123456789abcd" + seat.toString(16).padStart(2, '0'))
            val body = SightingUpload.pack(seedOf(seat), window, upload, heard, ask)
            assertEquals(legacyPack(seedOf(seat), window, upload, heard, ask), body, "seat $seat, upload $upload")
            bodies += body
        }
        for (body in bodies) assertEquals(legacyUnpack(body), SightingUpload.unpack(body))
    }

    private fun ByteArray.toHexString(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    /** [SightingUpload.pack] as it was written before D-276, kept only to be compared with. */
    private fun legacyPack(seed: ContactSeed, window: ContactWindow, upload: Long, heard: List<Sighting>, ask: String): RoundBody.Sighted {
        fun hex(bytes: ByteArray, n: Int = bytes.size) = bytes.copyOf(n).toHexString()
        fun slotOf(s: Sighting): String {
            val rssi = s.rssi.coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
            return s.token.hex + hex(byteArrayOf(rssi)) + hex(ByteArray(8) { i -> (s.window.index ushr (56 - 8 * i)).toByte() })
        }
        fun filler(slot: Int): Sighting {
            val key = ByteArray(16) { i -> seed.hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
            val mac = Hmac.sha256(
                key,
                byteArrayOf('P'.code.toByte()) +
                    ByteArray(8) { i -> (window.index ushr (56 - 8 * i)).toByte() } +
                    ByteArray(8) { i -> (upload ushr (56 - 8 * i)).toByte() } +
                    byteArrayOf(slot.toByte()),
            )
            val rssi = -100 + (mac[ContactTokens.TOKEN_BYTES].toInt() and 0xff) % 61
            return Sighting(ContactToken(hex(mac, ContactTokens.TOKEN_BYTES)), rssi, window)
        }
        val kept = heard
            .groupBy { it.token.hex to it.window.index }
            .map { (_, same) -> same.maxWith(compareBy<Sighting> { it.rssi }.thenBy { it.token.hex }) }
            .sortedWith(compareByDescending<Sighting> { it.rssi }.thenBy { it.token.hex }.thenBy { it.window.index })
            .take(SightingUpload.SLOTS)
        val filler = (kept.size until SightingUpload.SLOTS).map { filler(it) }
        return RoundBody.Sighted((kept + filler).map { slotOf(it) }.sorted().joinToString(""), ask, UploadProbe.NONE)
    }

    /** [SightingUpload.unpack] as it was written before D-276, kept only to be compared with. */
    private fun legacyUnpack(body: RoundBody.Sighted): List<Sighting> {
        fun bytes(hex: String) = ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
        val chars = 50
        return (0 until SightingUpload.SLOTS).map { i ->
            val slot = body.slots.substring(i * chars, (i + 1) * chars)
            Sighting(
                token = ContactToken(slot.substring(0, 32)),
                rssi = bytes(slot.substring(32, 34))[0].toInt(),
                window = ContactWindow(bytes(slot.substring(34)).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }),
            )
        }
    }

    @Test
    fun `a body that is not a packed upload is unreadable rather than half read`() {
        assertNull(SightingUpload.unpack(RoundBody.Sighted("00", SightingUpload.EMPTY_ASK, UploadProbe.NONE)))
        assertNull(SightingUpload.unpack(RoundBody.Sighted("Z".repeat(SightingUpload.BODY_CHARS), SightingUpload.EMPTY_ASK, UploadProbe.NONE)))
    }
}
