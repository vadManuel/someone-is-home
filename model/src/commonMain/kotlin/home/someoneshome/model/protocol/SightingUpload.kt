package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Hmac
import home.someoneshome.model.Sighting
import home.someoneshome.model.hexBytes
import home.someoneshome.model.isLowerHex
import home.someoneshome.model.toLowerHex

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
        val kept = heard
            .groupBy { it.token.hex to it.window.index }
            .map { (_, same) -> same.maxWith(compareBy<Sighting> { it.rssi }.thenBy { it.token.hex }) }
            .sortedWith(compareByDescending<Sighting> { it.rssi }.thenBy { it.token.hex }.thenBy { it.window.index })
            .take(SLOTS)
        val filler = (kept.size until SLOTS).map { slot -> filler(seed, window, upload, slot) }
        val slots = (kept + filler).map { slotOf(it) }.sorted()
        return RoundBody.Sighted(slots.joinToString(""), ask)
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
        return (0 until SLOTS).map { i ->
            val slot = text.substring(i * SLOT_CHARS, (i + 1) * SLOT_CHARS)
            Sighting(
                token = ContactToken(slot.substring(0, TOKEN_CHARS)),
                rssi = slot.substring(TOKEN_CHARS, TOKEN_CHARS + RSSI_CHARS).hexBytes()[0].toInt(),
                window = ContactWindow(
                    slot.substring(TOKEN_CHARS + RSSI_CHARS).hexBytes()
                        .fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) },
                ),
            )
        }
    }

    private fun slotOf(s: Sighting): String {
        val rssi = s.rssi.coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
        val window = ByteArray(8) { i -> (s.window.index ushr (56 - 8 * i)).toByte() }
        return s.token.hex + byteArrayOf(rssi).toLowerHex() + window.toLowerHex()
    }

    private fun filler(seed: ContactSeed, window: ContactWindow, upload: Long, slot: Int): Sighting {
        val mac = Hmac.sha256(
            seed.bytes(),
            byteArrayOf(DOMAIN_FILLER) +
                ByteArray(8) { i -> (window.index ushr (56 - 8 * i)).toByte() } +
                ByteArray(8) { i -> (upload ushr (56 - 8 * i)).toByte() } +
                byteArrayOf(slot.toByte()),
        )
        val rssi = FILLER_RSSI_FLOOR + (mac[ContactTokens.TOKEN_BYTES].toInt() and 0xff) % FILLER_RSSI_SPAN
        return Sighting(ContactToken(mac.toLowerHex(ContactTokens.TOKEN_BYTES)), rssi, window)
    }
}
