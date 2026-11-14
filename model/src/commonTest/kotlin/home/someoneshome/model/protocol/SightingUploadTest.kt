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

    /** Two keys and nothing else: no seat, no sequence, no role, no armed bit. */
    @Test
    fun `the body carries the slots and nothing about who sent it`() {
        for ((name, body) in cases()) {
            val keys = Json.parseToJsonElement(RoundWire.encode(body)).jsonObject.keys
            assertEquals(setOf("type", "slots"), keys, name)
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

    @Test
    fun `a body that is not a packed upload is unreadable rather than half read`() {
        assertNull(SightingUpload.unpack(RoundBody.Sighted("00")))
        assertNull(SightingUpload.unpack(RoundBody.Sighted("Z".repeat(SightingUpload.BODY_CHARS))))
    }
}
