package home.someoneshome.model.protocol

import kotlinx.serialization.json.Json

/**
 * The one encoder/decoder for [TransportFrame] — the wire format module owns its own bytes.
 *
 * Kept here rather than in the transport wiring so that "what the JSON looks like" has exactly
 * one home, the same home its round-trip test lives in. D8: versioned JSON.
 */
object TransportWire {

    private val json = Json

    fun encode(frame: TransportFrame): String = json.encodeToString(TransportFrame.serializer(), frame)

    /**
     * Null for anything that is not a frame. A socket hands the wiring whatever arrived, and a
     * malformed message must become a fact the shell can report — never an exception on an I/O
     * thread taking the connection down with it (the error rule: silent to the player, loud to
     * the authority).
     */
    fun decodeOrNull(text: String): TransportFrame? = try {
        json.decodeFromString(TransportFrame.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }
}
