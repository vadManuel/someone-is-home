package home.someoneshome.model.protocol

import home.someoneshome.model.ClientFacing
import kotlinx.serialization.json.Json

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
 * **The request slot: one fixed-width field in every upload, holding one request or none** (D-263,
 * provisional, owner to confirm).
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
 * ### The id, and why every slot has one
 *
 * [ID_CHARS] lowercase hex characters, drawn fresh at random by the phone (never a counter, whose
 * digits would grow with what the phone had asked for). A request keeps its id across every beat it
 * is sent on until the house answers `RoundBody.UploadHeard` with it, and the house puts each id to
 * the rules once (`IntentDesk.slotted`), so a request is acted on exactly once however many times a
 * lost beat makes the phone send it. **An empty slot has an id too**, fresh every beat, and the house
 * answers it the same way: the answer down the wire is one frame of one length a beat for every
 * phone, and says nothing about whether the phone asked for anything.
 *
 * ### The layout
 *
 * `id` · `length` (four hex characters, the payload's byte count) · `payload` (the request's JSON as
 * UTF-8 bytes in hex, padded with zeros to [PAYLOAD_BYTES]). Hex because no layer of the wire escapes
 * it, so the slot's width is its length (D-249, D-258). A request whose JSON is longer than
 * [PAYLOAD_BYTES] does not fit and is not packed: [of] says so with a null, and the phone says so in
 * its own log (rule 6). The largest request today, a long `HandOverEntry`, is well under it.
 */
object RequestSlot {

    /** Eight random bytes, as hex: one request's name. */
    const val ID_CHARS: Int = 16

    /** The most JSON one request may be. ⚠️ Provisional: sized with room over today's largest. */
    const val PAYLOAD_BYTES: Int = 256

    private const val LENGTH_CHARS = 4

    /** The slot's width in characters, always. */
    const val CHARS: Int = ID_CHARS + LENGTH_CHARS + PAYLOAD_BYTES * 2

    private val json = Json

    /** An empty slot under [id]: nothing asked, the same width as a full one. */
    fun empty(id: String): String {
        requireId(id)
        return id + "0".repeat(LENGTH_CHARS + PAYLOAD_BYTES * 2)
    }

    /** [asked] in a slot under [id], or null when its JSON is longer than [PAYLOAD_BYTES]. */
    fun of(id: String, asked: Asked): String? {
        requireId(id)
        val bytes = json.encodeToString(Asked.serializer(), asked).encodeToByteArray()
        if (bytes.size > PAYLOAD_BYTES) return null
        val length = bytes.size.toString(16).padStart(LENGTH_CHARS, '0')
        return id + length + WireHex.of(bytes) + "0".repeat((PAYLOAD_BYTES - bytes.size) * 2)
    }

    /** What one slot held: its id, and the request, or null for an empty slot. */
    data class Read(val id: String, val asked: Asked?)

    /**
     * **One slot, read**, or null for a slot that is not one: the wrong width, not lowercase hex,
     * a length past the payload, or a payload that is not a request. Its id is read whatever it holds.
     */
    fun read(slot: String): Read? {
        if (slot.length != CHARS || !WireHex.isHex(slot)) return null
        val id = slot.substring(0, ID_CHARS)
        val length = slot.substring(ID_CHARS, ID_CHARS + LENGTH_CHARS).toInt(16)
        if (length > PAYLOAD_BYTES) return null
        if (length == 0) return Read(id, null)
        val start = ID_CHARS + LENGTH_CHARS
        val text = WireHex.bytes(slot.substring(start, start + length * 2)).decodeToString()
        val asked = try {
            json.decodeFromString(Asked.serializer(), text)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return Read(id, asked)
    }

    /** Whether [id] is one: [ID_CHARS] lowercase hex characters. */
    fun isId(id: String): Boolean = id.length == ID_CHARS && WireHex.isHex(id)

    private fun requireId(id: String) = require(isId(id)) { "a request's id is $ID_CHARS lowercase hex characters" }
}
