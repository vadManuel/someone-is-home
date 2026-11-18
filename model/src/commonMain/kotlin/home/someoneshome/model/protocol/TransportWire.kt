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
    fun decodeOrNull(text: String): TransportFrame? = carryOrNull(text) ?: try {
        json.decodeFromString(TransportFrame.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * **A `Carry` exactly as [encode] writes one, read without the general decoder** (rule 7, D-276),
     * or null for anything else, which the general decoder then reads as it always did.
     *
     * Nearly every frame in a round is a `Carry` whose body is a round body's JSON, so its quotes are
     * escaped, and the general decoder unescaped them through a builder that grew by doubling: about
     * three times the body in garbage, for every upload on the host and every push on a phone.
     *
     * Exact: taken only for `{"type":"carry","body":"…"}` with nothing before or after, whose string
     * escapes nothing but `\"` and `\\` and holds no other `"` and no control character. That text has
     * one reading as JSON, the `Carry` of that string unescaped, which is what is built here. A frame
     * with any other escape, any whitespace, another key or anything else is not decided here.
     *
     * **Into a `CharArray`, not a `StringBuilder`.** The same loop appending to a pre-sized builder
     * measured 60 KB for an 1 808-character upload on Kotlin/Native, five times the general decoder;
     * the array is the body once and its string once, 7.2 KB (D-276).
     */
    private fun carryOrNull(text: String): TransportFrame.Carry? {
        if (!text.startsWith(CARRY_HEAD) || !text.endsWith(CARRY_TAIL)) return null
        val end = text.length - CARRY_TAIL.length
        if (end < CARRY_HEAD.length) return null
        val body = CharArray(end - CARRY_HEAD.length)
        var n = 0
        var i = CARRY_HEAD.length
        while (i < end) {
            var c = text[i]
            if (c == '\\') {
                if (i + 1 >= end) return null
                c = text[i + 1]
                if (c != '"' && c != '\\') return null
                i += 2
            } else {
                if (c == '"' || c < ' ') return null
                i += 1
            }
            body[n] = c
            n += 1
        }
        return TransportFrame.Carry(body.concatToString(0, n))
    }

    private const val CARRY_HEAD = "{\"type\":\"carry\",\"body\":\""
    private const val CARRY_TAIL = "\"}"
}
