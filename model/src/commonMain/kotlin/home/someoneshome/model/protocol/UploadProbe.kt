package home.someoneshome.model.protocol

/**
 * **The house's clock, measured on every upload: the probe a phone sends, and the two readings the
 * house answers with** (D-295, the owner's ruling of 2026-11-18 on Q2's containment half).
 *
 * Each upload carries a fresh [CHARS]-character probe ([RoundBody.Sighted.probe]); its
 * [RoundBody.UploadHeard] carries the probe back with the house's monotonic milliseconds at the
 * upload's arrival and at the answer. One fixed width for all three, the value's 64 bits in hex, so
 * no field grows with the house's uptime or with anything a phone did.
 */
object UploadProbe {

    /** Eight bytes as lowercase hex: one probe, or one reading of the house's clock. */
    const val CHARS: Int = 16

    /**
     * **No probe.** What an upload built by anything but a phone's uploader carries (a bench, a
     * test), and what the house echoes for a probe that did not read. A phone never matches it.
     */
    val NONE: String = "0".repeat(CHARS)

    private const val DIGITS = "0123456789abcdef"

    /** Whether [text] is one: [CHARS] lowercase hex characters. */
    fun isProbe(text: String): Boolean = text.length == CHARS && WireHex.isHex(text)

    /** [millis] as [CHARS] hex characters, its 64 bits big-endian, so any clock reading is written. */
    fun hexOf(millis: Long): String {
        val out = CharArray(CHARS)
        for (i in 0 until CHARS) out[i] = DIGITS[((millis ushr (60 - 4 * i)) and 0xf).toInt()]
        return out.concatToString()
    }

    /** [hexOf], read back. [hex] is [isProbe]: the wire's `init` checks it before anything reads it. */
    fun millisOf(hex: String): Long {
        var v = 0L
        for (c in hex) v = (v shl 4) or DIGITS.indexOf(c).toLong()
        return v
    }
}
