package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.Hmac
import home.someoneshome.model.Sha256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmInline

/*
 * **The UWB half of the beat: what a phone tells the house about its ranging, and the house's plan
 * back** (U6, D-300).
 *
 * Under the owner's ruling U6, Bluetooth finds who is near and UWB measures them. A phone can range
 * only a few peers at once (the limit is unpublished and differs by phone), so the house pairs
 * phones from the Bluetooth sightings it already has and tells each phone, once a second, which
 * peers to range. Two fields carry it, and both ride frames every phone already sends:
 *
 * - **The report**, phone to house, in every upload ([RoundBody.Sighted.ranging]): how many peers
 *   this phone can range now ([RangingReport.capacity]), the discovery tokens of its idle sessions
 *   ([RangingReport.spares]), and, for every pair it is ranging, the nearest distance it read this
 *   second and how many readings it took ([RangingReport.readings]).
 * - **The plan**, house to phone, in every upload's answer ([RoundBody.UploadHeard.plan]): for each
 *   pair, start it on one of this phone's idle sessions against the peer's token, keep it, or stop
 *   it ([RangingStep]).
 *
 * ### One width, always
 *
 * Every field is [RangingWire.MAX_PEERS] slots of each kind, filled or not, so a phone that can range
 * six peers and is ranging six sends exactly as many bytes as a phone with no UWB at all, and a plan
 * that starts six pairs is exactly as long as one that says nothing. Nothing about the sender is an
 * input: [RangingWire.packReport] takes the seat's seed, a nonce and what the radio said; there is no
 * role and no out bit to pass.
 *
 * ### Sealed under the seat's seed, so the filler IS keyed filler
 *
 * A token is an archived `NIDiscoveryToken`, a property list with a recognisable shape, and a pair's
 * handle in the clear would show the Wi-Fi which two sockets are being ranged together, which is who
 * is standing near whom. So each field is sealed: its bytes are XORed with a SHA-256 keystream whose
 * key is an HMAC, under the seat's own contact seed (the seed `ContactSeedIssued` gave that phone
 * alone, and the house derives), of the field's domain byte and a fresh nonce carried in the clear;
 * an eight-byte tag under a key derived from the same seed closes it. An empty slot is zeros before
 * sealing, so on the wire it is the keystream itself, keyed off the seed under its own domain byte:
 * the keyed filler `SightingUpload` pads with, in kind, and to anybody without the seed a full slot
 * and an empty one are the same uniform bytes. Without
 * the seed nobody can say how many tokens, readings or steps a field holds, or which handles recur.
 *
 * The domain bytes (`U`/`u` for a report's keystream and tag, `R`/`r` for a plan's) are apart from
 * the token's `T` and the upload filler's `P`, and the messages differ in length too, so nothing
 * keyed off one seed for one purpose can equal anything keyed off it for another.
 *
 * ### Fail closed
 *
 * [RangingWire.unpackReport] and [RangingWire.unpackPlan] return null for anything that is not
 * exactly one: the wrong width, a character outside URL-safe base64, a tag that does not check
 * (a flipped bit anywhere, or another seat's seed), and, under a good tag, any slot that does not
 * read: a flag or a kind out of range, a capacity past [RangingWire.MAX_PEERS], a token of no bytes
 * or past [RangingWire.TOKEN_BYTES], a pad or an empty slot that is not zeros, a repeated handle or
 * key, a distance with no readings behind it. A field that does not read is *nothing heard*, never
 * a partial one.
 *
 * Encoded as URL-safe base64 with no padding, as the request slot is (D-275): no layer of the wire
 * escapes those characters, so the field's width is its length.
 */

/** **A pair's name for the phones in it**: [RangingWire.HANDLE_BYTES] bytes the house drew, as hex. */
@JvmInline
value class RangingHandle(val hex: String) {
    init {
        require(hex.length == RangingWire.HANDLE_BYTES * 2 && WireHex.isHex(hex)) {
            "a ranging handle is ${RangingWire.HANDLE_BYTES} bytes of lowercase hex"
        }
    }
}

/** **A phone's own name for one of its idle sessions**: [RangingWire.KEY_BYTES] random bytes, as hex. */
@JvmInline
value class SpareKey(val hex: String) {
    init {
        require(hex.length == RangingWire.KEY_BYTES * 2 && WireHex.isHex(hex)) {
            "a spare session's key is ${RangingWire.KEY_BYTES} bytes of lowercase hex"
        }
    }
}

/**
 * **One session's discovery token, archived**: 1 to [RangingWire.TOKEN_BYTES] bytes, as hex. Measured
 * at 312 bytes (iPhone 13 Pro, iOS 18) and 345 (iPhone 16 Pro, iOS 26); a longer one is a device
 * fault for the radio to report, and cannot be built here, so it can never become a short frame.
 */
@JvmInline
value class RangingToken(val hex: String) {
    init {
        require(hex.length % 2 == 0 && hex.length in 2..RangingWire.TOKEN_BYTES * 2 && WireHex.isHex(hex)) {
            "a ranging token is 1 to ${RangingWire.TOKEN_BYTES} bytes of lowercase hex"
        }
    }

    /** The archived token's bytes. */
    fun bytes(): ByteArray = WireHex.bytes(hex)

    companion object {
        fun of(bytes: ByteArray): RangingToken = RangingToken(WireHex.of(bytes))
    }
}

/** **One idle session, offered**: its [key] on this phone, and the [token] a peer ranges it with. */
data class RangingSpare(val key: SpareKey, val token: RangingToken)

/**
 * **One pair this phone is ranging, this second**: the [nearestCm] it read, in centimetres (a touch
 * reads at or below zero, so it is signed), and how many [readings] it took. A pair held with no
 * reading this second has [readings] 0 and no distance: it is still listed, which is how the house
 * knows the phone holds it.
 */
data class RangingReading(val handle: RangingHandle, val nearestCm: Int?, val readings: Int) {
    init {
        require(readings in 0..RangingWire.MOST_READINGS) { "readings in one second are 0 to ${RangingWire.MOST_READINGS}" }
        require((readings == 0) == (nearestCm == null)) { "a distance needs a reading behind it, and a reading a distance" }
    }
}

/** **What one phone said about its ranging in one upload.** */
data class RangingReport(
    val capacity: Int,
    val spares: List<RangingSpare>,
    val readings: List<RangingReading>,
) {
    init {
        require(capacity in 0..RangingWire.MAX_PEERS) { "a capacity is 0 to ${RangingWire.MAX_PEERS}" }
        require(spares.size <= RangingWire.MAX_PEERS && readings.size <= RangingWire.MAX_PEERS) {
            "at most ${RangingWire.MAX_PEERS} spares and ${RangingWire.MAX_PEERS} readings"
        }
        require(spares.map { it.key }.distinct().size == spares.size) { "a spare's key appears twice" }
        require(readings.map { it.handle }.distinct().size == readings.size) { "a pair's reading appears twice" }
    }

    companion object {
        /** A phone with no UWB: no capacity, nothing offered, nothing ranged. */
        val NONE = RangingReport(0, emptyList(), emptyList())
    }
}

/** **One instruction in the house's plan to one phone, about one pair.** */
sealed class RangingStep {
    abstract val handle: RangingHandle

    /** Range [handle] on this phone's idle session [own], against the peer's [peer] token. */
    data class Start(override val handle: RangingHandle, val own: SpareKey, val peer: RangingToken) : RangingStep()

    /** Keep ranging [handle]: unchanged. */
    data class Keep(override val handle: RangingHandle) : RangingStep()

    /** Stop ranging [handle]. */
    data class Stop(override val handle: RangingHandle) : RangingStep()
}

/** **The house's plan to one phone, this second**: at most [RangingWire.MAX_PEERS] steps. */
data class RangingPlan(val steps: List<RangingStep>) {
    init {
        require(steps.size <= RangingWire.MAX_PEERS) { "a plan holds at most ${RangingWire.MAX_PEERS} steps" }
        require(steps.map { it.handle }.distinct().size == steps.size) { "a plan names a pair twice" }
    }

    companion object {
        val EMPTY = RangingPlan(emptyList())
    }
}

/** **The two fields' layout, sealing and reading.** */
@OptIn(ExperimentalEncodingApi::class)
object RangingWire {

    /**
     * ⚠️ **The most peers one phone may range at once, as the wire carries it: 6** (the U6 plan's
     * proposal). Every report and every plan is this many slots of each kind, so it sets both fields'
     * width. One named value so a change is one line; the phones' own limit is what they report.
     */
    const val MAX_PEERS: Int = 6

    /**
     * **The capacity a phone starts a round at: 3**, the 2021 iPhone 12 report (U6). It drops by one
     * whenever iOS refuses a session (the radio, a later unit), and is reported in every upload.
     */
    const val STARTING_CAPACITY: Int = 3

    /** **Each token slot's room: 384 bytes**, above the 345 measured (unit 2 of the UWB plan). */
    const val TOKEN_BYTES: Int = 384

    const val HANDLE_BYTES: Int = 8
    const val KEY_BYTES: Int = 8
    const val NONCE_BYTES: Int = 8
    const val TAG_BYTES: Int = 8

    /** The most readings one second can carry: one byte. iOS gives about 6.7 a second. */
    const val MOST_READINGS: Int = 255

    private const val LENGTH_BYTES = 2
    private const val CM_BYTES = 2

    /** A spare slot: present, key, token length, token padded. */
    private const val SPARE_BYTES = 1 + KEY_BYTES + LENGTH_BYTES + TOKEN_BYTES

    /** A reading slot: present, handle, nearest centimetres (signed), readings. */
    private const val READING_BYTES = 1 + HANDLE_BYTES + CM_BYTES + 1

    /** A step slot: kind, handle, own key, token length, token padded. */
    private const val STEP_BYTES = 1 + HANDLE_BYTES + KEY_BYTES + LENGTH_BYTES + TOKEN_BYTES

    private const val REPORT_BYTES = 1 + MAX_PEERS * SPARE_BYTES + MAX_PEERS * READING_BYTES
    private const val PLAN_BYTES = MAX_PEERS * STEP_BYTES

    /** A report field's bytes on the wire: nonce, sealed report, tag. */
    const val REPORT_FIELD_BYTES: Int = NONCE_BYTES + REPORT_BYTES + TAG_BYTES

    /** A plan field's bytes on the wire: nonce, sealed plan, tag. */
    const val PLAN_FIELD_BYTES: Int = NONCE_BYTES + PLAN_BYTES + TAG_BYTES

    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** Unpadded base64's length for [bytes] bytes. */
    private fun charsFor(bytes: Int): Int = bytes / 3 * 4 + when (bytes % 3) {
        0 -> 0
        1 -> 2
        else -> 3
    }

    /** **A report field's width in characters, always.** */
    val REPORT_CHARS: Int = charsFor(REPORT_FIELD_BYTES)

    /** **A plan field's width in characters, always.** */
    val PLAN_CHARS: Int = charsFor(PLAN_FIELD_BYTES)

    /**
     * **No report**: what an upload built by anything but a phone's uploader carries (a bench, a
     * test). The full width, all zeros; its tag never checks, so the house reads nothing from it.
     */
    val NO_REPORT: String = base64.encode(ByteArray(REPORT_FIELD_BYTES))

    /** **No plan**: the full width, all zeros, for a test's answer. It never reads on a phone. */
    val NO_PLAN: String = base64.encode(ByteArray(PLAN_FIELD_BYTES))

    private const val KIND_EMPTY = 0
    private const val KIND_START = 1
    private const val KIND_KEEP = 2
    private const val KIND_STOP = 3

    internal const val DOMAIN_REPORT: Byte = 'U'.code.toByte()
    internal const val DOMAIN_REPORT_TAG: Byte = 'u'.code.toByte()
    internal const val DOMAIN_PLAN: Byte = 'R'.code.toByte()
    internal const val DOMAIN_PLAN_TAG: Byte = 'r'.code.toByte()

    /** Whether [text] is a report field's shape: the width, and URL-safe base64. Not whether it reads. */
    fun isReportField(text: String): Boolean = text.length == REPORT_CHARS && text.all(::isBase64UrlSafe)

    /** Whether [text] is a plan field's shape: the width, and URL-safe base64. Not whether it reads. */
    fun isPlanField(text: String): Boolean = text.length == PLAN_CHARS && text.all(::isBase64UrlSafe)

    /**
     * **A plan field of [random] bytes**: what the house answers when it has no plan it can seal for
     * the phone (an upload that did not read, a seat with no seed). The full width and uniform, as a
     * sealed plan is; it never reads on any phone, so it changes nothing there.
     */
    fun blankPlan(random: ByteArray): String {
        require(random.size == PLAN_FIELD_BYTES) { "a blank plan is $PLAN_FIELD_BYTES random bytes" }
        return base64.encode(random)
    }

    /** **[report], sealed under [seed] with [nonce]**: always [REPORT_CHARS] characters. */
    fun packReport(seed: ContactSeed, nonce: ByteArray, report: RangingReport): String {
        // The plain bytes are written straight into the field, after the nonce, and sealed in place.
        val field = ByteArray(REPORT_FIELD_BYTES)
        val o = NONCE_BYTES
        field[o] = report.capacity.toByte()
        for ((i, spare) in report.spares.withIndex()) {
            val at = o + 1 + i * SPARE_BYTES
            field[at] = 1
            putHex(field, at + 1, spare.key.hex)
            putShort(field, at + 1 + KEY_BYTES, spare.token.hex.length / 2)
            putHex(field, at + 1 + KEY_BYTES + LENGTH_BYTES, spare.token.hex)
        }
        val readingsAt = o + 1 + MAX_PEERS * SPARE_BYTES
        for ((i, reading) in report.readings.withIndex()) {
            val at = readingsAt + i * READING_BYTES
            field[at] = 1
            putHex(field, at + 1, reading.handle.hex)
            putShort(field, at + 1 + HANDLE_BYTES, (reading.nearestCm ?: 0).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            field[at + 1 + HANDLE_BYTES + CM_BYTES] = reading.readings.toByte()
        }
        sealInPlace(seed, nonce, DOMAIN_REPORT, DOMAIN_REPORT_TAG, field, REPORT_BYTES)
        return base64.encode(field)
    }

    /** **A report field, opened with [seed]**, or null for anything that is not exactly one. */
    fun unpackReport(seed: ContactSeed, text: String): RangingReport? {
        if (!isReportField(text)) return null
        val f = openInPlace(seed, decode(text) ?: return null, DOMAIN_REPORT, DOMAIN_REPORT_TAG, REPORT_BYTES) ?: return null
        val o = NONCE_BYTES
        val capacity = f[o].toInt() and 0xff
        if (capacity > MAX_PEERS) return null
        val spares = ArrayList<RangingSpare>(MAX_PEERS)
        for (i in 0 until MAX_PEERS) {
            val at = o + 1 + i * SPARE_BYTES
            when (f[at].toInt()) {
                0 -> if (!zeros(f, at, SPARE_BYTES)) return null
                1 -> {
                    val length = getShort(f, at + 1 + KEY_BYTES)
                    val tokenAt = at + 1 + KEY_BYTES + LENGTH_BYTES
                    if (length !in 1..TOKEN_BYTES || !zeros(f, tokenAt + length, TOKEN_BYTES - length)) return null
                    spares += RangingSpare(SpareKey(hexOf(f, at + 1, KEY_BYTES)), RangingToken(hexOf(f, tokenAt, length)))
                }
                else -> return null
            }
        }
        val readings = ArrayList<RangingReading>(MAX_PEERS)
        val readingsAt = o + 1 + MAX_PEERS * SPARE_BYTES
        for (i in 0 until MAX_PEERS) {
            val at = readingsAt + i * READING_BYTES
            when (f[at].toInt()) {
                0 -> if (!zeros(f, at, READING_BYTES)) return null
                1 -> {
                    val cm = getShort(f, at + 1 + HANDLE_BYTES).toShort().toInt()
                    val count = f[at + 1 + HANDLE_BYTES + CM_BYTES].toInt() and 0xff
                    if (count == 0 && cm != 0) return null
                    readings += RangingReading(RangingHandle(hexOf(f, at + 1, HANDLE_BYTES)), if (count == 0) null else cm, count)
                }
                else -> return null
            }
        }
        if (spares.map { it.key }.distinct().size != spares.size) return null
        if (readings.map { it.handle }.distinct().size != readings.size) return null
        return RangingReport(capacity, spares, readings)
    }

    /** **[plan], sealed under the receiving seat's [seed] with [nonce]**: always [PLAN_CHARS] characters. */
    fun packPlan(seed: ContactSeed, nonce: ByteArray, plan: RangingPlan): String {
        val field = ByteArray(PLAN_FIELD_BYTES)
        for ((i, step) in plan.steps.withIndex()) {
            val at = NONCE_BYTES + i * STEP_BYTES
            field[at] = when (step) {
                is RangingStep.Start -> KIND_START
                is RangingStep.Keep -> KIND_KEEP
                is RangingStep.Stop -> KIND_STOP
            }.toByte()
            putHex(field, at + 1, step.handle.hex)
            if (step is RangingStep.Start) {
                putHex(field, at + 1 + HANDLE_BYTES, step.own.hex)
                putShort(field, at + 1 + HANDLE_BYTES + KEY_BYTES, step.peer.hex.length / 2)
                putHex(field, at + 1 + HANDLE_BYTES + KEY_BYTES + LENGTH_BYTES, step.peer.hex)
            }
        }
        sealInPlace(seed, nonce, DOMAIN_PLAN, DOMAIN_PLAN_TAG, field, PLAN_BYTES)
        return base64.encode(field)
    }

    /** **A plan field, opened with [seed]**, or null for anything that is not exactly one. */
    fun unpackPlan(seed: ContactSeed, text: String): RangingPlan? {
        if (!isPlanField(text)) return null
        val f = openInPlace(seed, decode(text) ?: return null, DOMAIN_PLAN, DOMAIN_PLAN_TAG, PLAN_BYTES) ?: return null
        val steps = ArrayList<RangingStep>(MAX_PEERS)
        for (i in 0 until MAX_PEERS) {
            val at = NONCE_BYTES + i * STEP_BYTES
            val kind = f[at].toInt()
            if (kind == KIND_EMPTY) {
                if (!zeros(f, at, STEP_BYTES)) return null
                continue
            }
            if (kind !in KIND_START..KIND_STOP) return null
            val handle = RangingHandle(hexOf(f, at + 1, HANDLE_BYTES))
            val rest = at + 1 + HANDLE_BYTES
            steps += if (kind == KIND_START) {
                val length = getShort(f, rest + KEY_BYTES)
                val tokenAt = rest + KEY_BYTES + LENGTH_BYTES
                if (length !in 1..TOKEN_BYTES || !zeros(f, tokenAt + length, TOKEN_BYTES - length)) return null
                RangingStep.Start(handle, SpareKey(hexOf(f, rest, KEY_BYTES)), RangingToken(hexOf(f, tokenAt, length)))
            } else {
                if (!zeros(f, rest, STEP_BYTES - 1 - HANDLE_BYTES)) return null
                if (kind == KIND_KEEP) RangingStep.Keep(handle) else RangingStep.Stop(handle)
            }
        }
        if (steps.map { it.handle }.distinct().size != steps.size) return null
        return RangingPlan(steps)
    }

    /** **The nonce a field carries in the clear**, or null for a field that is not one's shape. */
    fun nonceOf(text: String): ByteArray? {
        if (!isReportField(text) && !isPlanField(text)) return null
        return decode(text)?.copyOf(NONCE_BYTES)
    }

    private fun decode(text: String): ByteArray? = try {
        base64.decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * **[plain] sealed under [seed]**: [nonce], then [plain] XOR the keystream, then the tag. What
     * the packers do in place; here for the fail-closed tests, which seal what no packer builds.
     */
    internal fun seal(seed: ContactSeed, nonce: ByteArray, domain: Byte, tagDomain: Byte, plain: ByteArray): ByteArray {
        val field = ByteArray(NONCE_BYTES + plain.size + TAG_BYTES)
        plain.copyInto(field, NONCE_BYTES)
        sealInPlace(seed, nonce, domain, tagDomain, field, plain.size)
        return field
    }

    /**
     * **[field] sealed in place**: its first [NONCE_BYTES] become [nonce], the [plainBytes] after
     * them are XORed with the keystream, and the last [TAG_BYTES] become the tag over everything
     * before them. One array for the whole field, written once (rule 7).
     */
    private fun sealInPlace(seed: ContactSeed, nonce: ByteArray, domain: Byte, tagDomain: Byte, field: ByteArray, plainBytes: Int) {
        require(nonce.size == NONCE_BYTES) { "a nonce is $NONCE_BYTES bytes" }
        nonce.copyInto(field)
        val key = seed.bytes()
        xorStream(Hmac.Keyed(key), domain, field, NONCE_BYTES, plainBytes)
        val tag = tagKey(key, tagDomain).mac(field, NONCE_BYTES + plainBytes)
        tag.copyInto(field, NONCE_BYTES + plainBytes, 0, TAG_BYTES)
    }

    /** [field] opened in place under [seed], or null when its length or its tag is wrong. */
    private fun openInPlace(seed: ContactSeed, field: ByteArray, domain: Byte, tagDomain: Byte, plainBytes: Int): ByteArray? {
        if (field.size != NONCE_BYTES + plainBytes + TAG_BYTES) return null
        val key = seed.bytes()
        val expected = tagKey(key, tagDomain).mac(field, NONCE_BYTES + plainBytes)
        var differ = 0
        for (i in 0 until TAG_BYTES) differ = differ or (expected[i].toInt() xor field[NONCE_BYTES + plainBytes + i].toInt())
        if (differ != 0) return null
        xorStream(Hmac.Keyed(key), domain, field, NONCE_BYTES, plainBytes)
        return field
    }

    /**
     * **The tag's own key: HMAC(seed, tagDomain)**, so the tag is one MAC over the field as it lies,
     * with no copy of it behind a domain byte. A different key from the keystream's, so the two
     * cannot be confused.
     */
    private fun tagKey(seedKey: ByteArray, tagDomain: Byte): Hmac.Keyed =
        Hmac.Keyed(Hmac.Keyed(seedKey).mac(byteArrayOf(tagDomain)))

    /**
     * [field]'s [length] bytes from [at] XOR this field's keystream, in place. The nonce is
     * [field]'s first [NONCE_BYTES].
     *
     * One MAC derives the field's stream key, HMAC(seed, domain ‖ nonce); each 32-byte block is then
     * SHA-256(stream key ‖ block index), a fixed-length input under a secret key, so one compression a
     * block where an HMAC per block was four. A field is 77 blocks, sealed or opened four times a
     * second per phone between the phone and the house, and on a debug build in one test process the
     * HMAC-per-block stream was CPU enough to push a phone's beat late (`UplinkFrameTest`, D-300).
     */
    private fun xorStream(mac: Hmac.Keyed, domain: Byte, field: ByteArray, at: Int, length: Int) {
        val derive = ByteArray(1 + NONCE_BYTES)
        derive[0] = domain
        field.copyInto(derive, 1, 0, NONCE_BYTES)
        val message = ByteArray(Sha256.DIGEST_BYTES + 4)
        mac.mac(derive, into = message)
        val engine = Sha256.Engine()
        val block = ByteArray(BLOCK)
        var j = 0
        var i = 0
        while (i < length) {
            for (b in 0 until 4) message[Sha256.DIGEST_BYTES + b] = (j ushr (24 - 8 * b)).toByte()
            engine.reset()
            engine.update(message, 0, message.size)
            engine.finish(block)
            val n = minOf(BLOCK, length - i)
            for (k in 0 until n) field[at + i + k] = (field[at + i + k].toInt() xor block[k].toInt()).toByte()
            i += n
            j++
        }
    }

    private const val BLOCK = 32

    private const val HEX = "0123456789abcdef"

    /** [length] bytes of [from] at [at], as lowercase hex, with no array between. */
    private fun hexOf(from: ByteArray, at: Int, length: Int): String {
        val out = CharArray(length * 2)
        for (i in 0 until length) {
            val v = from[at + i].toInt() and 0xff
            out[2 * i] = HEX[v ushr 4]
            out[2 * i + 1] = HEX[v and 0xf]
        }
        return out.concatToString()
    }

    /** [hex]'s bytes written into [into] at [at], read off the characters in place. */
    private fun putHex(into: ByteArray, at: Int, hex: String) {
        for (i in 0 until hex.length / 2) {
            into[at + i] = ((HEX.indexOf(hex[2 * i]) shl 4) or HEX.indexOf(hex[2 * i + 1])).toByte()
        }
    }

    private fun putShort(into: ByteArray, at: Int, value: Int) {
        into[at] = (value ushr 8).toByte()
        into[at + 1] = value.toByte()
    }

    private fun getShort(from: ByteArray, at: Int): Int =
        ((from[at].toInt() and 0xff) shl 8) or (from[at + 1].toInt() and 0xff)

    private fun zeros(bytes: ByteArray, at: Int, length: Int): Boolean {
        for (i in at until at + length) if (bytes[i].toInt() != 0) return false
        return true
    }

    private fun isBase64UrlSafe(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
}
