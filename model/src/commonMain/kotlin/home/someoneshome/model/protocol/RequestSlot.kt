package home.someoneshome.model.protocol

import home.someoneshome.model.ClientFacing
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * **What a phone may ask the house for from inside its upload: a control, or the Egress tile**
 * (D-263, provisional).
 *
 * The two upward requests the round has, [IntentMessage] in [Act] and the Egress tile in [Fire],
 * kept as two members for the reason [RoundBody.Fire] is not an [RoundBody.Act]: one is translated
 * and admitted, the other needs the house to make a draw first. Neither carries a seat, and neither
 * can: the seat comes off the connection the upload arrived on, exactly as it always has.
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed class Asked {

    /** A control the player pressed. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("act")
    data class Act(val intent: IntentMessage) : Asked()

    /** The Egress tile, held down. There is nothing in it, as there is nothing in [RoundBody.Fire]. */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("fire")
    data object Fire : Asked()
}

/**
 * **The request slot: one fixed-width field in every upload, holding the requests that fit, or none**
 * (D-263, D-275, provisional, owner to confirm).
 *
 * ### Why requests stopped travelling as frames of their own
 *
 * `ArmRevoke`, the Egress tile, Surge, Isolate and Static are pressed only by Insiders. Each went up
 * as its own frame, at the moment it was pressed, so anybody who could see the Wi-Fi saw a phone put
 * a frame on the air between its beats, and the phones that did that were the Insiders. Encryption
 * (R7) hides what a frame says, not that it exists, how long it is or when it went. So every request
 * a player can make during a round now rides **inside the upload every phone already sends once a
 * beat at one size** (D-212): most beats the slot is empty and padded to the same width, and a
 * phone with something to ask puts it in the next beat's slot. Every living phone puts the same
 * number of frames of the same length on the air at the same cadence, whatever it is and whatever
 * it asks for.
 *
 * ### As many requests as fit, under one id (D-275)
 *
 * The slot holds a **list** of requests, in the order they were made, and the id names the list. One
 * request a beat queued a burst of Subroutine entries for tens of seconds; a list drains it at the
 * cadence the upload already has. The width does not move: a request that would not fit waits, whole,
 * for the next beat, and the padding makes one request, five and none the same length.
 *
 * ### The id, and why every slot has one
 *
 * [ID_CHARS] lowercase hex characters, drawn fresh at random by the phone (never a counter, whose
 * digits would grow with what the phone had asked for). A list keeps its id, and exactly its members,
 * across every beat it is sent on until the house answers `RoundBody.UploadHeard` with it, and the
 * house puts each id to the rules once (`IntentDesk.slotted`), so every request in it is acted on
 * exactly once however many times a lost beat makes the phone send it. **An empty slot has an id
 * too**, fresh every beat, and the house answers it the same way: the answer down the wire is one
 * frame of one length a beat for every phone, and says nothing about whether the phone asked for
 * anything, or for how much.
 *
 * ### The layout
 *
 * `id` · `length` (four hex characters, the payload's byte count) · `payload` (the list's JSON as
 * UTF-8 bytes, padded with zeros to [PAYLOAD_BYTES], in unpadded URL-safe base64). URL-safe base64 for
 * the reason the slot was hex (D-249, D-258): no layer of the wire escapes a letter, a digit, `-` or
 * `_`, so the slot's width is its length, and it carries half as much again as hex did in the same
 * [CHARS] (D-275). A list whose JSON is longer than [PAYLOAD_BYTES] is not packed: [of] says so with a
 * null, and [fitsAlone] lets the phone refuse a request that could never fit, in its own log (rule 6).
 * The largest request today, a long `HandOverEntry`, is well under it.
 */
@OptIn(ExperimentalEncodingApi::class)
object RequestSlot {

    /** Eight random bytes, as hex: one list's name. */
    const val ID_CHARS: Int = 16

    /** The most JSON one slot's list may be. ⚠️ Provisional: all that [PAYLOAD_CHARS] can carry. */
    const val PAYLOAD_BYTES: Int = 384

    private const val LENGTH_CHARS = 4

    /** [PAYLOAD_BYTES] in base64: four characters for every three bytes, with nothing left over. */
    private const val PAYLOAD_CHARS = PAYLOAD_BYTES / 3 * 4

    /** The slot's width in characters, always: 532, as it was when it held one request in hex (D-263). */
    const val CHARS: Int = ID_CHARS + LENGTH_CHARS + PAYLOAD_CHARS

    /** `[` and `]` around a list. */
    private const val LIST_BYTES = 2

    /** One `,` between two members. */
    private const val SEPARATOR_BYTES = 1

    private val json = Json
    private val list = ListSerializer(Asked.serializer())
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** An empty slot under [id]: nothing asked, the same width as a full one. */
    fun empty(id: String): String {
        requireId(id)
        return id + "0".repeat(LENGTH_CHARS) + base64.encode(ByteArray(PAYLOAD_BYTES))
    }

    /** [asked], in order, in a slot under [id], or null when their JSON is longer than [PAYLOAD_BYTES]. */
    fun of(id: String, asked: List<Asked>): String? {
        requireId(id)
        val bytes = json.encodeToString(list, asked).encodeToByteArray()
        if (bytes.size > PAYLOAD_BYTES) return null
        val length = bytes.size.toString(16).padStart(LENGTH_CHARS, '0')
        return id + length + base64.encode(bytes.copyOf(PAYLOAD_BYTES))
    }

    /** One request alone in a slot under [id]: [of] with a list of one. */
    fun of(id: String, asked: Asked): String? = of(id, listOf(asked))

    /** **The bytes [asked] takes in a slot's list**, the separator before it not counted. */
    fun bytesOf(asked: Asked): Int = json.encodeToString(Asked.serializer(), asked).encodeToByteArray().size

    /** Whether [members] requests whose [bytesOf] add up to [memberBytes] fit one slot together. */
    fun fits(memberBytes: Int, members: Int): Boolean =
        LIST_BYTES + memberBytes + SEPARATOR_BYTES * (members - 1).coerceAtLeast(0) <= PAYLOAD_BYTES

    /** Whether a request of [bytes] (its [bytesOf]) fits a slot on its own. One that does not, never will. */
    fun fitsAlone(bytes: Int): Boolean = fits(bytes, 1)

    /** What one slot held: its id, and its requests in the order they were made, none for an empty slot. */
    data class Read(val id: String, val asked: List<Asked>)

    /**
     * **One slot, read**, or null for a slot that is not one: the wrong width, an id or length that is
     * not lowercase hex, a payload that is not URL-safe base64, a length past the payload, padding that
     * is not zeros, or JSON that is not a list of requests. Its id is read whatever it holds.
     */
    fun read(slot: String): Read? {
        if (slot.length != CHARS) return null
        val head = slot.substring(0, ID_CHARS + LENGTH_CHARS)
        if (!WireHex.isHex(head)) return null
        val id = head.substring(0, ID_CHARS)
        val length = head.substring(ID_CHARS).toInt(16)
        if (length > PAYLOAD_BYTES) return null
        val payload = slot.substring(ID_CHARS + LENGTH_CHARS)
        if (!payload.all(::isBase64UrlSafe)) return null
        val bytes = try {
            base64.decode(payload)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size != PAYLOAD_BYTES) return null
        for (i in length until PAYLOAD_BYTES) if (bytes[i] != ZERO) return null
        if (length == 0) return Read(id, emptyList())
        val asked = try {
            json.decodeFromString(list, bytes.decodeToString(0, length))
        } catch (_: IllegalArgumentException) {
            return null
        }
        return Read(id, asked)
    }

    /** Whether [id] is one: [ID_CHARS] lowercase hex characters. */
    fun isId(id: String): Boolean = id.length == ID_CHARS && WireHex.isHex(id)

    private const val ZERO: Byte = 0

    private fun isBase64UrlSafe(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'

    private fun requireId(id: String) = require(isId(id)) { "a request's id is $ID_CHARS lowercase hex characters" }
}
