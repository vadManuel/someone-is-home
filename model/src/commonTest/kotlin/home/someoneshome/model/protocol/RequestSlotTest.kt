package home.someoneshome.model.protocol

import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **D-263: the request slot is one width whatever it holds, and reads back what was put in it.**
 *
 * Every request a player can make in a round rides an upload's slot, so the slot is what a reader of
 * the Wi-Fi would measure to tell an Insider's arm from a Resident's check-in from a beat that asked
 * for nothing. Measured here on the encoded frame, through the real `RoundWire` and `TransportWire`.
 */
class RequestSlotTest {

    private val marker = MarkerId("HOME-07")

    /** Every member of [Asked], and through it every member of [IntentMessage], at its widest plausible. */
    private val everyRequest: List<Asked> = listOf(
        Asked.Act(IntentMessage.ArmRevoke),
        Asked.Act(IntentMessage.FireSurge(Seat(17))),
        Asked.Act(IntentMessage.FireIsolate(marker)),
        Asked.Act(IntentMessage.FireStatic),
        Asked.Act(IntentMessage.BeginSubroutine(marker)),
        Asked.Act(IntentMessage.StopSubroutine),
        Asked.Act(IntentMessage.LeaveTerminal),
        Asked.Act(IntentMessage.ReturnSyncPulse(List(8) { 9_999_999_999L - it })),
        Asked.Act(IntentMessage.CallMeeting),
        Asked.Act(IntentMessage.CheckIn),
        Asked.Act(IntentMessage.DeclareReadyToVote),
        Asked.Act(IntentMessage.SelectVote(Seat(17))),
        Asked.Act(IntentMessage.SelectVote(null)),
        Asked.Act(IntentMessage.LockVote),
        Asked.Act(IntentMessage.HandOverEntry(marker, List(24) { 99 - it })),
        Asked.Fire,
    )

    private fun frameOf(ask: String): Int =
        TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.Sighted("0".repeat(SightingUpload.BODY_CHARS), ask))))
            .encodeToByteArray().size

    @Test
    fun `every request and an empty slot are one width and the upload one length`() {
        val slots = everyRequest.associate { it.toString() to assertNotNull(RequestSlot.of("0123456789abcdef", it), "$it did not fit") } +
            ("nothing asked" to RequestSlot.empty("fedcba9876543210"))
        for ((name, slot) in slots) assertEquals(RequestSlot.CHARS, slot.length, name)
        val frames = slots.mapValues { frameOf(it.value) }
        assertEquals(1, frames.values.toSet().size, "the upload's length says what was asked: $frames")
        println("D-263 UPLOAD FRAME BYTES — ${frames.values.first()} for every request and for none")
    }

    @Test
    fun `a slot reads back its id and its request and an empty one reads back nothing`() {
        for (asked in everyRequest) {
            val read = assertNotNull(RequestSlot.read(RequestSlot.of("00112233445566ff", asked)!!))
            assertEquals("00112233445566ff", read.id)
            assertEquals(listOf(asked), read.asked)
        }
        assertEquals(RequestSlot.Read("aaaaaaaaaaaaaaaa", emptyList()), RequestSlot.read(RequestSlot.empty("aaaaaaaaaaaaaaaa")))
    }

    @Test
    fun `a request too long for the slot is not packed`() {
        assertNull(RequestSlot.of("0123456789abcdef", Asked.Act(IntentMessage.HandOverEntry(marker, List(200) { it }))))
    }

    @Test
    fun `a slot that is not one reads as nothing rather than half`() {
        val good = RequestSlot.of("0123456789abcdef", Asked.Act(IntentMessage.CheckIn))!!
        assertNull(RequestSlot.read(good.dropLast(2)), "a short slot was read")
        assertNull(RequestSlot.read(good.uppercase()), "an uppercase slot was read")
        // A length past the payload.
        assertNull(RequestSlot.read(good.substring(0, 16) + "ffff" + good.substring(20)), "a length past the payload was read")
        // A payload that is not a list of requests: the bytes of `{}` with the length saying two.
        val notARequest = "0123456789abcdef" + "0002" + payloadOf("{}".encodeToByteArray())
        assertNull(RequestSlot.read(notARequest), "a payload that is not a list of requests was read")
        // A payload that is not URL-safe base64, and one whose padding is not zeros.
        assertNull(RequestSlot.read(good.dropLast(1) + "+"), "a payload outside the alphabet was read")
        val dirty = "[]".encodeToByteArray().copyOf(RequestSlot.PAYLOAD_BYTES).also { it[RequestSlot.PAYLOAD_BYTES - 1] = 1 }
        assertNull(RequestSlot.read("0123456789abcdef" + "0002" + payloadOf(dirty)), "padding that was not zeros was read")
        // An empty slot is read without decoding (D-276), and only one that is all zeros is empty.
        val empty = RequestSlot.empty("0123456789abcdef")
        assertEquals("0123456789abcdef" + "0000" + payloadOf(ByteArray(0)), empty)
        for (at in listOf(20, 21, 300, RequestSlot.CHARS - 1)) {
            for (c in listOf('B', 'g', '-', '_', '+', '=')) {
                val nearly = empty.substring(0, at) + c + empty.substring(at + 1)
                assertNull(RequestSlot.read(nearly), "an empty slot with '$c' at $at was read")
            }
        }
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun payloadOf(bytes: ByteArray): String =
        kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
            .encode(bytes.copyOf(RequestSlot.PAYLOAD_BYTES))

    /**
     * **D-275: a slot holds as many requests as fit, and is one width however many it holds.** Every
     * prefix of a long run of requests that fits is measured on the encoded frame beside an empty slot
     * and a slot of one, and each reads back exactly its list, in order.
     */
    @Test
    fun `a slot of many requests is the width of one and of none and reads back in order`() {
        val run = everyRequest + everyRequest
        val lists = (0..run.size).map { run.take(it) }.filter { RequestSlot.of("0123456789abcdef", it) != null }
        assertTrue(lists.size > 4, "fewer than four requests fit a slot: ${lists.size - 1}")
        val frames = lists.associate { it.size to frameOf(RequestSlot.of("0123456789abcdef", it)!!) }
        assertEquals(1, frames.values.toSet().size, "the upload's length says how many were asked: $frames")
        for (list in lists) assertEquals(list, RequestSlot.read(RequestSlot.of("00112233445566ff", list)!!)!!.asked)
        assertEquals(frameOf(RequestSlot.empty("fedcba9876543210")), frames.values.first(), "an empty slot is another length")
        assertEquals(532, RequestSlot.CHARS, "the slot's width moved from D-263's")
    }

    /** [RequestSlot.fits] agrees with what [RequestSlot.of] really packs, at every size around the edge. */
    @Test
    fun `fits says exactly what of packs`() {
        for (n in 0..200) {
            val list = List(1 + n / 40) { Asked.Act(IntentMessage.HandOverEntry(marker, List(n) { 7 })) }
            val bytes = list.sumOf { RequestSlot.bytesOf(it) }
            assertEquals(RequestSlot.of("0123456789abcdef", list) != null, RequestSlot.fits(bytes, list.size), "$n")
        }
    }

    @Test
    fun `an id is sixteen lowercase hex characters on the slot and on the answer`() {
        assertFailsWith<IllegalArgumentException> { RequestSlot.empty("0123") }
        assertFailsWith<IllegalArgumentException> { RequestSlot.empty("0123456789ABCDEF") }
        assertFailsWith<IllegalArgumentException> { RoundBody.UploadHeard("xyz") }
        // The answer is one length whatever id it carries.
        val answers = listOf("0000000000000000", "ffffffffffffffff", "0123456789abcdef").map {
            TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.UploadHeard(it)))).encodeToByteArray().size
        }
        assertEquals(1, answers.toSet().size, "the answer's length follows its id: $answers")
    }

    /** No member of [Asked] can name who is asking: the slot's JSON has no field for it. */
    @Test
    fun `nothing in a slot can say who sent it`() {
        val forbidden = listOf("\"actor\"", "\"seat\"", "\"voter\"", "\"caller\"", "\"sender\"", "\"from\"")
        for (asked in everyRequest) {
            val text = kotlinx.serialization.json.Json.encodeToString(Asked.serializer(), asked)
            assertTrue(forbidden.none { it in text }, "$asked carries a field an actor could ride in: $text")
        }
    }
}
