package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Hmac
import home.someoneshome.model.Sighting
import home.someoneshome.model.hexByteAt
import home.someoneshome.model.isLowerHex

/**
 * **The sighting upload: one fixed-size message, on one cadence, from every live phone** (D-212).
 *
 * The owner's ruling is that an observer of the Wi-Fi must not be able to tell an armed phone from
 * an unarmed one, or an Insider's from a Resident's, by its uploads. So every upload is the same
 * number of bytes whatever it carries: [SLOTS] fixed-width slots, filled with what the phone heard
 * and padded with filler, sorted so the filler sits nowhere in particular. **A phone that heard
 * nothing sends exactly as many bytes as one that heard everybody.**
 *
 * ### Nothing about the sender is an input
 *
 * [pack] takes the seat's seed, the window, the upload's own counter and what was heard. There is no
 * role, no armed state, no seat and nothing derived from one, so the message *cannot* vary by them:
 * the guarantee is the parameter list, and `SightingUploadTest` checks the encoded frame's length
 * over every case anyway.
 *
 * ### Why the filler is keyed rather than zeros
 *
 * Zero slots would show a reader of the frame how many phones this one heard, and a phone that
 * never scans (a Revoked one, D-008) would send nothing but zeros. Keyed filler — HMAC of the seed
 * under its own domain byte — is indistinguishable from a token to anybody without the seed, and it
 * resolves to no seat, so the house drops it the way it drops a token from a phone that is not in
 * the round.
 *
 * ⚠️ **Length is the guarantee; content is only as private as the transport.** The websocket is
 * plain `ws` today, so a reader on the Wi-Fi can read every frame, not only measure it. Against
 * that reader filler hides a count only roughly (real tokens persist across a window, filler does
 * not). Raised in D-212 as an open question rather than decided here.
 */
object SightingUpload {

    /**
     * Slots per upload. Eighteen seats is the largest party any rule sizes for (D-143), so a phone
     * can hear seventeen others, and a window edge inside one upload can double that briefly; past
     * [SLOTS] the strongest are kept, which is the end that decides a contact.
     */
    const val SLOTS: Int = 24

    /**
     * ⚠️ **Provisional: one upload a second.** Identical on every phone is the ruling; the number is
     * not ruled. Contact-to-dark latency (D-212, ruling 6 deferred to unit 7's measurement) is what
     * will set it, and it is held here as one named value so that measurement changes one line.
     */
    const val CADENCE_MILLIS: Long = 1_000L

    private const val TOKEN_CHARS = ContactTokens.TOKEN_BYTES * 2
    private const val RSSI_CHARS = 2
    private const val WINDOW_CHARS = 16
    private const val SLOT_CHARS = TOKEN_CHARS + RSSI_CHARS + WINDOW_CHARS

    /** The packed body's length in characters, always. */
    const val BODY_CHARS: Int = SLOTS * SLOT_CHARS

    private const val DOMAIN_FILLER: Byte = 'P'.code.toByte()

    /** An empty request slot under an all-zero id: what an upload carries when nothing asks (D-263). */
    val EMPTY_ASK: String = RequestSlot.empty("0".repeat(RequestSlot.ID_CHARS))

    /** The filler's RSSI range, roughly where real readings sit. */
    private const val FILLER_RSSI_FLOOR = -100
    private const val FILLER_RSSI_SPAN = 61

    /**
     * **The upload for [upload], the [upload]th since this phone's round began, in [window].**
     *
     * What was heard is deduplicated per token and window, keeping the strongest reading; past
     * [SLOTS] the strongest are kept. The rest of the slots are filler keyed off [seed], [window],
     * [upload] and the slot, and every slot is then sorted by its own text, so neither position nor
     * order says which are real. RSSI is clamped to a signed byte.
     */
    fun pack(
        seed: ContactSeed,
        window: ContactWindow,
        upload: Long,
        heard: List<Sighting>,
        /**
         * The request slot this upload carries (D-263): a [RequestSlot], always [RequestSlot.CHARS]
         * characters. Empty under an all-zero id when a caller has no requests to carry, which is
         * every caller but the phone's own uploader; the width is the same either way.
         */
        ask: String = EMPTY_ASK,
    ): RoundBody.Sighted {
        require(ask.length == RequestSlot.CHARS) { "a request slot is ${RequestSlot.CHARS} characters" }
        val kept = if (heard.isEmpty()) emptyList() else heard
            .groupBy { it.token.hex to it.window.index }
            .map { (_, same) -> same.maxWith(compareBy<Sighting> { it.rssi }.thenBy { it.token.hex }) }
            .sortedWith(compareByDescending<Sighting> { it.rssi }.thenBy { it.token.hex }.thenBy { it.window.index })
            .take(SLOTS)
        // **Every slot written straight into one array, then sorted by its own text** (rule 7, D-276).
        // The text is the text it always was, slot for slot; what went is an object per slot and per
        // hex pair, and two kilobytes per MAC: one keyed MAC serves the whole filler.
        val text = CharArray(BODY_CHARS)
        for ((slot, s) in kept.withIndex()) writeSlot(text, slot, s.token.hex, s.rssi, s.window.index)
        if (kept.size < SLOTS) {
            val mac = Hmac.Keyed(seed.bytes())
            val message = ByteArray(FILLER_MESSAGE_BYTES)
            message[0] = DOMAIN_FILLER
            putLong(message, 1, window.index)
            putLong(message, 9, upload)
            val tag = ByteArray(MAC_BYTES)
            for (slot in kept.size until SLOTS) {
                message[FILLER_MESSAGE_BYTES - 1] = slot.toByte()
                mac.mac(message, into = tag)
                val rssi = FILLER_RSSI_FLOOR + (tag[ContactTokens.TOKEN_BYTES].toInt() and 0xff) % FILLER_RSSI_SPAN
                writeSlot(text, slot, tag, rssi, window.index)
            }
        }
        return RoundBody.Sighted(sortedSlots(text), ask)
    }

    /**
     * **Every slot of [body], filler included**, or null for a body that is not a packed upload.
     *
     * Filler is returned with the rest because the house cannot tell it from a stranger's token
     * without the sender's seed, and does not need to: it resolves to no seat (unit 2).
     */
    fun unpack(body: RoundBody.Sighted): List<Sighting>? {
        val text = body.slots
        if (text.length != BODY_CHARS || !text.isLowerHex()) return null
        // Read off the body's characters in place (rule 7, D-276): a slot costs its token's text and
        // its `Sighting`, where it once cost eleven substrings and two arrays besides.
        val slots = ArrayList<Sighting>(SLOTS)
        for (i in 0 until SLOTS) {
            val at = i * SLOT_CHARS
            var window = 0L
            for (b in 0 until 8) {
                window = (window shl 8) or text.hexByteAt(at + TOKEN_CHARS + RSSI_CHARS + 2 * b).toLong()
            }
            slots += Sighting(
                token = ContactToken(text.substring(at, at + TOKEN_CHARS)),
                rssi = text.hexByteAt(at + TOKEN_CHARS).toByte().toInt(),
                window = ContactWindow(window),
            )
        }
        return slots
    }

    /** The filler's MAC input: the domain byte, the window, the upload's counter and the slot. */
    private const val FILLER_MESSAGE_BYTES = 1 + 8 + 8 + 1

    private const val MAC_BYTES = 32

    /** Slot [slot] of [text]: [token]'s hex, then [rssi] clamped to a signed byte, then [window], big-endian. */
    private fun writeSlot(text: CharArray, slot: Int, token: String, rssi: Int, window: Long) {
        val at = slot * SLOT_CHARS
        for (i in 0 until TOKEN_CHARS) text[at + i] = token[i]
        writeTail(text, at, rssi, window)
    }

    /** [writeSlot] for a token that is still bytes: the first [ContactTokens.TOKEN_BYTES] of [token]. */
    private fun writeSlot(text: CharArray, slot: Int, token: ByteArray, rssi: Int, window: Long) {
        val at = slot * SLOT_CHARS
        for (i in 0 until ContactTokens.TOKEN_BYTES) writeHex(text, at + 2 * i, token[i].toInt() and 0xff)
        writeTail(text, at, rssi, window)
    }

    private fun writeTail(text: CharArray, at: Int, rssi: Int, window: Long) {
        val clamped = rssi.coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt())
        writeHex(text, at + TOKEN_CHARS, clamped and 0xff)
        for (i in 0 until 8) {
            writeHex(text, at + TOKEN_CHARS + RSSI_CHARS + 2 * i, (window ushr (56 - 8 * i)).toInt() and 0xff)
        }
    }

    private fun writeHex(text: CharArray, at: Int, v: Int) {
        text[at] = HEX[v ushr 4]
        text[at + 1] = HEX[v and 0xf]
    }

    private fun putLong(into: ByteArray, at: Int, value: Long) {
        for (i in 0 until 8) into[at + i] = (value ushr (56 - 8 * i)).toByte()
    }

    /**
     * **[text]'s slots in the order their own text sorts, joined**: what sorting the slots as
     * strings and joining them gave, by an insertion sort over [SLOTS] indices instead of over
     * strings. Two equal slots are equal text, so which of them goes first cannot show.
     */
    private fun sortedSlots(text: CharArray): String {
        val order = IntArray(SLOTS) { it }
        for (i in 1 until SLOTS) {
            val moving = order[i]
            var j = i - 1
            while (j >= 0 && compareSlots(text, order[j], moving) > 0) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = moving
        }
        val out = StringBuilder(BODY_CHARS)
        for (slot in order) out.appendRange(text, slot * SLOT_CHARS, (slot + 1) * SLOT_CHARS)
        return out.toString()
    }

    private fun compareSlots(text: CharArray, a: Int, b: Int): Int {
        val x = a * SLOT_CHARS
        val y = b * SLOT_CHARS
        for (i in 0 until SLOT_CHARS) {
            val c = text[x + i].compareTo(text[y + i])
            if (c != 0) return c
        }
        return 0
    }

    private const val HEX = "0123456789abcdef"
}
