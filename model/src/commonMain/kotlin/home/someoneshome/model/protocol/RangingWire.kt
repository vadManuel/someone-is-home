package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.Hmac
import home.someoneshome.model.Sha256
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
 *   this phone can range now ([RangingReport.capacity]), the discovery tokens of up to
 *   [RangingWire.TOKEN_SLOTS] of its idle sessions ([RangingReport.spares]), and, for every pair it
 *   is ranging, the nearest distance it read this second and how many readings it took
 *   ([RangingReport.readings]).
 * - **The plan**, house to phone, in every upload's answer ([RoundBody.UploadHeard.plan]): for each
 *   pair, start it on one of this phone's idle sessions against the peer's token, keep it, or stop
 *   it ([RangingStep]); at most [RangingWire.TOKEN_SLOTS] starts a second.
 *
 * ### One width, always
 *
 * Every field is a fixed number of slots of each kind, filled or not: [RangingWire.TOKEN_SLOTS] token
 * slots and [RangingWire.MAX_PEERS] distance or step slots. A phone that can range six peers and is
 * ranging six sends exactly as many bytes as a phone with no UWB at all, and a plan that starts three
 * pairs is exactly as long as one that says nothing. Nothing about the sender is an input: the
 * packers take the seat's seed, a nonce and what the radio said; there is no role and no out bit.
 *
 * ### Three token slots, not six (D-300, rule 7)
 *
 * Six 384-byte token slots each way every second made the two fields 3 279 and 3 246 characters, and
 * the frames carrying them cost the house about 16 KB per seat per beat to read and write through
 * the JSON wire: about 0.3 MB/s on an 18-seat host before a byte of ranging work, most of rule 7's
 * whole-app 0.5 MB/s. A phone starts a round at capacity 3 (U6), so it never needs more than three
 * new sessions in one second, and a phone that can hold more takes the rest a second later.
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
 * and an empty one are the same uniform bytes. Without the seed nobody can say how many tokens,
 * readings or steps a field holds, or which handles recur.
 *
 * The domain bytes (`U`/`u` for a report's keystream and tag, `R`/`r` for a plan's) are apart from
 * the token's `T` and the upload filler's `P`, so nothing keyed off one seed for one purpose can
 * equal anything keyed off it for another.
 *
 * ### Fail closed
 *
 * A field that is not exactly one reads as nothing: the wrong width, a character outside URL-safe
 * base64 or trailing bits that are not zero, a tag that does not check (a flipped bit anywhere, or
 * another seat's seed), and, under a good tag, any slot that does not read: a flag or a kind out of
 * range, a capacity past [RangingWire.MAX_PEERS], a token of no bytes or past
 * [RangingWire.TOKEN_BYTES], a pad or an empty slot that is not zeros, a repeated handle or key, a
 * distance with no readings behind it, a start whose token slot is missing or shared. A field that
 * does not read is *nothing heard*, never a partial one.
 *
 * ### Allocation (rule 7)
 *
 * The house reads a report and seals a plan for every seat every second, so the codec that does it,
 * [RangingCodec], holds its buffers and its keys: a field is decoded into a held array, opened and
 * read in place, and a plan is written, sealed and encoded in place, so the one allocation a plan
 * costs is its own string. Handles and keys are `Long`s, and a token is copied off the wire only the
 * first time its key is seen. `RangingAllocationTest` (core) and `UploadAllocationTest` (model) hold
 * the ceilings.
 *
 * Encoded as URL-safe base64 with no padding, as the request slot is (D-275): no layer of the wire
 * escapes those characters, so the field's width is its length.
 */

private const val HEX = "0123456789abcdef"

private fun hexOfLong(bits: Long): String {
    val out = CharArray(16)
    for (i in 0 until 16) out[i] = HEX[((bits ushr (60 - 4 * i)) and 0xf).toInt()]
    return out.concatToString()
}

private fun longOfHex(hex: String, what: String): Long {
    require(hex.length == 16 && WireHex.isHex(hex)) { "$what is 8 bytes of lowercase hex" }
    var v = 0L
    for (c in hex) v = (v shl 4) or HEX.indexOf(c).toLong()
    return v
}

/** **A pair's name for the phones in it**: [RangingWire.HANDLE_BYTES] bytes the house drew. */
@JvmInline
value class RangingHandle(val bits: Long) {
    val hex: String get() = hexOfLong(bits)
    override fun toString(): String = "RangingHandle($hex)"

    companion object {
        fun ofHex(hex: String): RangingHandle = RangingHandle(longOfHex(hex, "a ranging handle"))
    }
}

/** **A phone's own name for one of its idle sessions**: [RangingWire.KEY_BYTES] random bytes. */
@JvmInline
value class SpareKey(val bits: Long) {
    val hex: String get() = hexOfLong(bits)
    override fun toString(): String = "SpareKey($hex)"

    companion object {
        fun ofHex(hex: String): SpareKey = SpareKey(longOfHex(hex, "a spare session's key"))
    }
}

/**
 * **One session's discovery token, archived**: 1 to [RangingWire.TOKEN_BYTES] bytes. Measured at 312
 * bytes (iPhone 13 Pro, iOS 18) and 345 (iPhone 16 Pro, iOS 26); a longer one is a device fault for
 * the radio to report, and cannot be built here, so it can never become a short frame. Its bytes
 * are its own copy and never handed out, so two equal tokens are equal by content.
 */
class RangingToken private constructor(private val bytes: ByteArray) {
    val size: Int get() = bytes.size

    /** A copy of the archived token's bytes. */
    fun bytes(): ByteArray = bytes.copyOf()

    /** The token as lowercase hex. Allocates; for logs and tests. */
    val hex: String get() = WireHex.of(bytes)

    internal fun copyInto(into: ByteArray, at: Int) {
        bytes.copyInto(into, at)
    }

    /** Whether [length] bytes of [from] at [at] are this token. Allocates nothing. */
    internal fun isAt(from: ByteArray, at: Int, length: Int): Boolean {
        if (length != bytes.size) return false
        for (i in 0 until length) if (from[at + i] != bytes[i]) return false
        return true
    }

    override fun equals(other: Any?): Boolean = other is RangingToken && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "RangingToken(${bytes.size} bytes)"

    companion object {
        fun of(bytes: ByteArray): RangingToken {
            require(bytes.size in 1..RangingWire.TOKEN_BYTES) { "a ranging token is 1 to ${RangingWire.TOKEN_BYTES} bytes" }
            return RangingToken(bytes.copyOf())
        }

        fun ofHex(hex: String): RangingToken {
            require(hex.length % 2 == 0 && WireHex.isHex(hex)) { "a ranging token is lowercase hex" }
            return of(WireHex.bytes(hex))
        }

        internal fun read(from: ByteArray, at: Int, length: Int): RangingToken = RangingToken(from.copyOfRange(at, at + length))
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

/**
 * **A report, read**: what the house's pairing asks of one. [RangingReport] is one; so is a
 * [RangingCodec] that has just opened a field, which answers from its held buffer.
 */
interface RangingReportView {
    val capacity: Int
    val spareCount: Int
    fun spareKey(i: Int): Long

    /** Whether spare [i]'s token is [token], byte for byte. Allocates nothing. */
    fun spareTokenIs(i: Int, token: RangingToken): Boolean

    /** Spare [i]'s token, as a value of its own. Allocates. */
    fun spareToken(i: Int): RangingToken
    val readingCount: Int
    fun readingHandle(i: Int): Long
    fun readingCm(i: Int): Int
    fun readingReadings(i: Int): Int
}

/** **What one phone said about its ranging in one upload.** */
data class RangingReport(
    override val capacity: Int,
    val spares: List<RangingSpare>,
    val readings: List<RangingReading>,
) : RangingReportView {
    init {
        require(capacity in 0..RangingWire.MAX_PEERS) { "a capacity is 0 to ${RangingWire.MAX_PEERS}" }
        require(spares.size <= RangingWire.TOKEN_SLOTS && readings.size <= RangingWire.MAX_PEERS) {
            "at most ${RangingWire.TOKEN_SLOTS} spares and ${RangingWire.MAX_PEERS} readings"
        }
        require(spares.map { it.key }.distinct().size == spares.size) { "a spare's key appears twice" }
        require(readings.map { it.handle }.distinct().size == readings.size) { "a pair's reading appears twice" }
    }

    override val spareCount: Int get() = spares.size
    override fun spareKey(i: Int): Long = spares[i].key.bits
    override fun spareTokenIs(i: Int, token: RangingToken): Boolean = spares[i].token == token
    override fun spareToken(i: Int): RangingToken = spares[i].token
    override val readingCount: Int get() = readings.size
    override fun readingHandle(i: Int): Long = readings[i].handle.bits
    override fun readingCm(i: Int): Int = readings[i].nearestCm ?: 0
    override fun readingReadings(i: Int): Int = readings[i].readings

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

/**
 * **A plan, to be sealed**: what the codec asks of one. [RangingPlan] is one; so is the house's
 * pairing, which answers from its held arrays.
 */
interface RangingPlanSource {
    val stepCount: Int

    /** [RangingWire.STEP_START], [RangingWire.STEP_KEEP] or [RangingWire.STEP_STOP]. */
    fun stepKind(i: Int): Int
    fun stepHandle(i: Int): Long

    /** A start's own spare key; anything for a keep or a stop. */
    fun stepOwn(i: Int): Long

    /** A start's peer token; null for a keep or a stop. */
    fun stepPeer(i: Int): RangingToken?
}

/**
 * **The house's plan to one phone, this second**: at most [RangingWire.MAX_PEERS] steps, of which at
 * most [RangingWire.TOKEN_SLOTS] start a pair.
 */
data class RangingPlan(val steps: List<RangingStep>) : RangingPlanSource {
    init {
        require(steps.size <= RangingWire.MAX_PEERS) { "a plan holds at most ${RangingWire.MAX_PEERS} steps" }
        require(steps.count { it is RangingStep.Start } <= RangingWire.TOKEN_SLOTS) {
            "a plan starts at most ${RangingWire.TOKEN_SLOTS} pairs"
        }
        require(steps.map { it.handle }.distinct().size == steps.size) { "a plan names a pair twice" }
    }

    override val stepCount: Int get() = steps.size
    override fun stepKind(i: Int): Int = when (steps[i]) {
        is RangingStep.Start -> RangingWire.STEP_START
        is RangingStep.Keep -> RangingWire.STEP_KEEP
        is RangingStep.Stop -> RangingWire.STEP_STOP
    }
    override fun stepHandle(i: Int): Long = steps[i].handle.bits
    override fun stepOwn(i: Int): Long = (steps[i] as? RangingStep.Start)?.own?.bits ?: 0L
    override fun stepPeer(i: Int): RangingToken? = (steps[i] as? RangingStep.Start)?.peer

    companion object {
        val EMPTY = RangingPlan(emptyList())
    }
}

/** **The two fields' layout**, and the convenience forms of the codec for a phone, a bench and a test. */
object RangingWire {

    /**
     * ⚠️ **The most peers one phone may range at once, as the wire carries it: 6** (the U6 plan's
     * proposal). Every report has this many distance slots and every plan this many step slots.
     */
    const val MAX_PEERS: Int = 6

    /**
     * ⚠️ **Token slots each way: 3** (D-300, provisional (overnight), for rule 7). A report offers at
     * most this many idle sessions' tokens, and a plan starts at most this many pairs, each second.
     */
    const val TOKEN_SLOTS: Int = 3

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

    const val STEP_START: Int = 1
    const val STEP_KEEP: Int = 2
    const val STEP_STOP: Int = 3

    internal const val LENGTH_BYTES = 2
    internal const val CM_BYTES = 2

    /** A spare slot: present, key, token length, token padded. */
    internal const val SPARE_BYTES = 1 + KEY_BYTES + LENGTH_BYTES + TOKEN_BYTES

    /** A reading slot: present, handle, nearest centimetres (signed), readings. */
    internal const val READING_BYTES = 1 + HANDLE_BYTES + CM_BYTES + 1

    /** A step slot: kind, handle, own key, the start's token slot. */
    internal const val STEP_BYTES = 1 + HANDLE_BYTES + KEY_BYTES + 1

    /** A plan's token slot: length, token padded. */
    internal const val PLAN_TOKEN_BYTES = LENGTH_BYTES + TOKEN_BYTES

    internal const val REPORT_BYTES = 1 + TOKEN_SLOTS * SPARE_BYTES + MAX_PEERS * READING_BYTES
    internal const val PLAN_BYTES = MAX_PEERS * STEP_BYTES + TOKEN_SLOTS * PLAN_TOKEN_BYTES

    /** A report field's bytes on the wire: nonce, sealed report, tag. */
    const val REPORT_FIELD_BYTES: Int = NONCE_BYTES + REPORT_BYTES + TAG_BYTES

    /** A plan field's bytes on the wire: nonce, sealed plan, tag. */
    const val PLAN_FIELD_BYTES: Int = NONCE_BYTES + PLAN_BYTES + TAG_BYTES

    /** Unpadded base64's length for [bytes] bytes. */
    internal fun charsFor(bytes: Int): Int = bytes / 3 * 4 + when (bytes % 3) {
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
    val NO_REPORT: String = "A".repeat(REPORT_CHARS)

    /** **No plan**: the full width, all zeros, for a test's answer. It never reads on a phone. */
    val NO_PLAN: String = "A".repeat(PLAN_CHARS)

    internal const val DOMAIN_REPORT: Byte = 'U'.code.toByte()
    internal const val DOMAIN_REPORT_TAG: Byte = 'u'.code.toByte()
    internal const val DOMAIN_PLAN: Byte = 'R'.code.toByte()
    internal const val DOMAIN_PLAN_TAG: Byte = 'r'.code.toByte()

    /** Whether [text] is a report field's shape: the width, and URL-safe base64. Not whether it reads. */
    fun isReportField(text: String): Boolean = text.length == REPORT_CHARS && isBase64(text)

    /** Whether [text] is a plan field's shape: the width, and URL-safe base64. Not whether it reads. */
    fun isPlanField(text: String): Boolean = text.length == PLAN_CHARS && isBase64(text)

    private fun isBase64(text: String): Boolean {
        for (i in text.indices) if (Base64Url.valueOf(text[i]) < 0) return false
        return true
    }

    /**
     * **A plan field of [random] bytes**: what the house answers when it has no plan it can seal for
     * the phone (an upload that did not read, a seat with no seed). The full width and uniform, as a
     * sealed plan is; it never reads on any phone, so it changes nothing there.
     */
    fun blankPlan(random: ByteArray): String {
        require(random.size == PLAN_FIELD_BYTES) { "a blank plan is $PLAN_FIELD_BYTES random bytes" }
        return Base64Url.encode(random, random.size)
    }

    /** **[report], sealed under [seed] with [nonce]**: always [REPORT_CHARS] characters. */
    fun packReport(seed: ContactSeed, nonce: ByteArray, report: RangingReport): String =
        RangingCodec().packReport(seed, nonce, report.capacity, report.spares, report.readings)

    /** **A report field, opened with [seed]**, or null for anything that is not exactly one. Allocates; the house uses [RangingCodec]. */
    fun unpackReport(seed: ContactSeed, text: String): RangingReport? =
        RangingCodec().let { if (it.openReport(seed, text)) it.report() else null }

    /** **[plan], sealed under the receiving seat's [seed] with [nonce]**: always [PLAN_CHARS] characters. */
    fun packPlan(seed: ContactSeed, nonce: ByteArray, plan: RangingPlanSource): String =
        RangingCodec().packPlan(seed, nonce, plan)

    /** **A plan field, opened with [seed]**, or null for anything that is not exactly one. */
    fun unpackPlan(seed: ContactSeed, text: String): RangingPlan? =
        RangingCodec().let { if (it.openPlan(seed, text)) it.plan() else null }

    /** **The nonce a field carries in the clear**, or null for a field that is not one's shape. */
    fun nonceOf(text: String): ByteArray? {
        val bytes = when {
            isReportField(text) -> REPORT_FIELD_BYTES
            isPlanField(text) -> PLAN_FIELD_BYTES
            else -> return null
        }
        val out = ByteArray(bytes)
        if (!Base64Url.decodeInto(text, out, bytes)) return null
        return out.copyOf(NONCE_BYTES)
    }

    /** **[plain] sealed under [seed]**, as the packers seal: for the fail-closed tests, which seal what no packer builds. */
    internal fun seal(seed: ContactSeed, nonce: ByteArray, domain: Byte, tagDomain: Byte, plain: ByteArray): ByteArray =
        RangingCodec().sealPlain(seed, nonce, domain, tagDomain, plain)
}

/**
 * **URL-safe base64 without padding, written out** (rule 7): decoded into and encoded from arrays
 * the caller holds, so neither direction allocates more than the string it must return. The
 * standard library's decoder copies the characters into a fresh array first.
 */
internal object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun valueOf(c: Char): Int = when (c) {
        in 'A'..'Z' -> c - 'A'
        in 'a'..'z' -> c - 'a' + 26
        in '0'..'9' -> c - '0' + 52
        '-' -> 62
        '_' -> 63
        else -> -1
    }

    /** [length] bytes of [bytes] as unpadded base64, into [chars] (fresh if not given); returns the string. */
    fun encode(bytes: ByteArray, length: Int, chars: CharArray = CharArray(RangingWire.charsFor(length))): String {
        var o = 0
        var i = 0
        while (i + 3 <= length) {
            val v = ((bytes[i].toInt() and 0xff) shl 16) or ((bytes[i + 1].toInt() and 0xff) shl 8) or (bytes[i + 2].toInt() and 0xff)
            chars[o++] = ALPHABET[v ushr 18]
            chars[o++] = ALPHABET[(v ushr 12) and 63]
            chars[o++] = ALPHABET[(v ushr 6) and 63]
            chars[o++] = ALPHABET[v and 63]
            i += 3
        }
        when (length - i) {
            1 -> {
                val v = (bytes[i].toInt() and 0xff) shl 16
                chars[o++] = ALPHABET[v ushr 18]
                chars[o++] = ALPHABET[(v ushr 12) and 63]
            }
            2 -> {
                val v = ((bytes[i].toInt() and 0xff) shl 16) or ((bytes[i + 1].toInt() and 0xff) shl 8)
                chars[o++] = ALPHABET[v ushr 18]
                chars[o++] = ALPHABET[(v ushr 12) and 63]
                chars[o++] = ALPHABET[(v ushr 6) and 63]
            }
        }
        return chars.concatToString(0, o)
    }

    /**
     * [text] decoded into the first [length] bytes of [into], or false for a character outside the
     * alphabet, a width that is not [length]'s, or trailing bits that are not zero.
     */
    fun decodeInto(text: String, into: ByteArray, length: Int): Boolean {
        if (text.length != RangingWire.charsFor(length)) return false
        var o = 0
        var i = 0
        while (o + 3 <= length) {
            val a = valueOf(text[i]); val b = valueOf(text[i + 1]); val c = valueOf(text[i + 2]); val d = valueOf(text[i + 3])
            if ((a or b or c or d) < 0) return false
            val v = (a shl 18) or (b shl 12) or (c shl 6) or d
            into[o++] = (v ushr 16).toByte()
            into[o++] = (v ushr 8).toByte()
            into[o++] = v.toByte()
            i += 4
        }
        when (length - o) {
            1 -> {
                val a = valueOf(text[i]); val b = valueOf(text[i + 1])
                if ((a or b) < 0 || (b and 0xf) != 0) return false
                into[o] = ((a shl 2) or (b ushr 4)).toByte()
            }
            2 -> {
                val a = valueOf(text[i]); val b = valueOf(text[i + 1]); val c = valueOf(text[i + 2])
                if ((a or b or c) < 0 || (c and 0x3) != 0) return false
                val v = (a shl 18) or (b shl 12) or (c shl 6)
                into[o] = (v ushr 16).toByte()
                into[o + 1] = (v ushr 8).toByte()
            }
        }
        return true
    }
}

/**
 * **The ranging codec, with its buffers and keys held** (rule 7, D-300).
 *
 * One per owner: the house keeps one for every seat's report and plan, a phone one for its own.
 * **Not thread-safe, and never shared**, as [Hmac.Keyed] is not. After [openReport] returns true it
 * answers [RangingReportView] from the field it just opened, until the next call; after [openPlan],
 * the plan accessors. Packing writes the plain bytes into the held field, seals them in place and
 * encodes from there, so a packed field costs the string it returns and nothing else. Each seed's
 * keys are derived once and kept (up to [KEYS_KEPT] seeds).
 */
class RangingCodec : RangingReportView {
    private val field = ByteArray(maxOf(RangingWire.REPORT_FIELD_BYTES, RangingWire.PLAN_FIELD_BYTES))
    private val chars = CharArray(maxOf(RangingWire.REPORT_CHARS, RangingWire.PLAN_CHARS))
    private val derive = ByteArray(1 + RangingWire.NONCE_BYTES)
    private val stream = ByteArray(Sha256.DIGEST_BYTES + 4)
    private val block = ByteArray(Sha256.DIGEST_BYTES)
    private val tagOut = ByteArray(Sha256.DIGEST_BYTES)
    private val engine = Sha256.Engine()

    /** Where each present slot of the opened field starts, in order. */
    private val spareAt = IntArray(RangingWire.TOKEN_SLOTS)
    private val readingAt = IntArray(RangingWire.MAX_PEERS)
    private val stepAt = IntArray(RangingWire.MAX_PEERS)
    private val usedToken = BooleanArray(RangingWire.TOKEN_SLOTS)
    private var spares = 0
    private var readings = 0
    private var steps = 0
    private var reportCapacity = 0

    private class Keys(seed: ByteArray) {
        val stream = Hmac.Keyed(seed)
        val reportTag = Hmac.Keyed(stream.mac(byteArrayOf(RangingWire.DOMAIN_REPORT_TAG)))
        val planTag = Hmac.Keyed(stream.mac(byteArrayOf(RangingWire.DOMAIN_PLAN_TAG)))

        fun tagFor(domain: Byte): Hmac.Keyed = if (domain == RangingWire.DOMAIN_REPORT_TAG) reportTag else planTag
    }

    private val keys = HashMap<String, Keys>()

    private fun keysFor(seed: ContactSeed): Keys {
        keys[seed.hex]?.let { return it }
        if (keys.size >= KEYS_KEPT) keys.clear()
        return Keys(seed.bytes()).also { keys[seed.hex] = it }
    }

    // ---- The report ------------------------------------------------------------------------------

    /** **[spares] and [readings] at [capacity], sealed under [seed] with [nonce]**: always [RangingWire.REPORT_CHARS] characters. */
    fun packReport(seed: ContactSeed, nonce: ByteArray, capacity: Int, spares: List<RangingSpare>, readings: List<RangingReading>): String {
        require(capacity in 0..RangingWire.MAX_PEERS) { "a capacity is 0 to ${RangingWire.MAX_PEERS}" }
        require(spares.size <= RangingWire.TOKEN_SLOTS && readings.size <= RangingWire.MAX_PEERS) { "too many spares or readings for the field" }
        val o = RangingWire.NONCE_BYTES
        field.fill(0, 0, RangingWire.REPORT_FIELD_BYTES)
        field[o] = capacity.toByte()
        for (i in spares.indices) {
            val spare = spares[i]
            val at = o + 1 + i * RangingWire.SPARE_BYTES
            field[at] = 1
            putLong(at + 1, spare.key.bits)
            putShort(at + 1 + RangingWire.KEY_BYTES, spare.token.size)
            spare.token.copyInto(field, at + 1 + RangingWire.KEY_BYTES + RangingWire.LENGTH_BYTES)
        }
        val readingsFrom = o + 1 + RangingWire.TOKEN_SLOTS * RangingWire.SPARE_BYTES
        for (i in readings.indices) {
            val reading = readings[i]
            val at = readingsFrom + i * RangingWire.READING_BYTES
            field[at] = 1
            putLong(at + 1, reading.handle.bits)
            putShort(at + 1 + RangingWire.HANDLE_BYTES, (reading.nearestCm ?: 0).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            field[at + 1 + RangingWire.HANDLE_BYTES + RangingWire.CM_BYTES] = reading.readings.toByte()
        }
        val keys = keysFor(seed)
        sealInPlace(keys.stream, keys.reportTag, RangingWire.DOMAIN_REPORT, nonce, RangingWire.REPORT_BYTES)
        return Base64Url.encode(field, RangingWire.REPORT_FIELD_BYTES, chars)
    }

    /** **Open a report field under [seed]**: true when it is exactly one, and the view then reads it. */
    fun openReport(seed: ContactSeed, text: String): Boolean {
        spares = 0
        readings = 0
        if (!Base64Url.decodeInto(text, field, RangingWire.REPORT_FIELD_BYTES)) return false
        val keys = keysFor(seed)
        if (!openInPlace(keys.stream, keys.reportTag, RangingWire.DOMAIN_REPORT, RangingWire.REPORT_BYTES)) return false
        val o = RangingWire.NONCE_BYTES
        val capacity = field[o].toInt() and 0xff
        if (capacity > RangingWire.MAX_PEERS) return false
        var count = 0
        for (s in 0 until RangingWire.TOKEN_SLOTS) {
            val at = o + 1 + s * RangingWire.SPARE_BYTES
            when (field[at].toInt()) {
                0 -> if (!zeros(at, RangingWire.SPARE_BYTES)) return false
                1 -> {
                    val length = getShort(at + 1 + RangingWire.KEY_BYTES)
                    val tokenAt = at + 1 + RangingWire.KEY_BYTES + RangingWire.LENGTH_BYTES
                    if (length !in 1..RangingWire.TOKEN_BYTES || !zeros(tokenAt + length, RangingWire.TOKEN_BYTES - length)) return false
                    spareAt[count++] = at
                }
                else -> return false
            }
        }
        for (a in 0 until count) for (b in a + 1 until count) if (getLong(spareAt[a] + 1) == getLong(spareAt[b] + 1)) return false
        var read = 0
        val readingsFrom = o + 1 + RangingWire.TOKEN_SLOTS * RangingWire.SPARE_BYTES
        for (s in 0 until RangingWire.MAX_PEERS) {
            val at = readingsFrom + s * RangingWire.READING_BYTES
            when (field[at].toInt()) {
                0 -> if (!zeros(at, RangingWire.READING_BYTES)) return false
                1 -> {
                    val cm = getShort(at + 1 + RangingWire.HANDLE_BYTES)
                    val n = field[at + 1 + RangingWire.HANDLE_BYTES + RangingWire.CM_BYTES].toInt() and 0xff
                    if (n == 0 && cm != 0) return false
                    readingAt[read++] = at
                }
                else -> return false
            }
        }
        for (a in 0 until read) for (b in a + 1 until read) if (getLong(readingAt[a] + 1) == getLong(readingAt[b] + 1)) return false
        reportCapacity = capacity
        spares = count
        readings = read
        return true
    }

    override val capacity: Int get() = reportCapacity
    override val spareCount: Int get() = spares
    override fun spareKey(i: Int): Long = getLong(spareAt[i] + 1)
    override fun spareTokenIs(i: Int, token: RangingToken): Boolean {
        val at = spareAt[i] + 1 + RangingWire.KEY_BYTES
        return token.isAt(field, at + RangingWire.LENGTH_BYTES, getShort(at))
    }
    override fun spareToken(i: Int): RangingToken {
        val at = spareAt[i] + 1 + RangingWire.KEY_BYTES
        return RangingToken.read(field, at + RangingWire.LENGTH_BYTES, getShort(at))
    }
    override val readingCount: Int get() = readings
    override fun readingHandle(i: Int): Long = getLong(readingAt[i] + 1)
    override fun readingCm(i: Int): Int = getShort(readingAt[i] + 1 + RangingWire.HANDLE_BYTES).toShort().toInt()
    override fun readingReadings(i: Int): Int = field[readingAt[i] + 1 + RangingWire.HANDLE_BYTES + RangingWire.CM_BYTES].toInt() and 0xff

    /** The report just opened, as a value of its own. Allocates; for a phone's bench and the tests. */
    fun report(): RangingReport = RangingReport(
        reportCapacity,
        List(spares) { RangingSpare(SpareKey(spareKey(it)), spareToken(it)) },
        List(readings) { i ->
            val n = readingReadings(i)
            RangingReading(RangingHandle(readingHandle(i)), if (n == 0) null else readingCm(i), n)
        },
    )

    // ---- The plan --------------------------------------------------------------------------------

    /** **[plan], sealed under the receiving seat's [seed] with [nonce]**: always [RangingWire.PLAN_CHARS] characters. */
    fun packPlan(seed: ContactSeed, nonce: ByteArray, plan: RangingPlanSource): String {
        val n = plan.stepCount
        require(n <= RangingWire.MAX_PEERS) { "a plan holds at most ${RangingWire.MAX_PEERS} steps" }
        field.fill(0, 0, RangingWire.PLAN_FIELD_BYTES)
        val o = RangingWire.NONCE_BYTES
        val tokensFrom = o + RangingWire.MAX_PEERS * RangingWire.STEP_BYTES
        var tokens = 0
        for (i in 0 until n) {
            val at = o + i * RangingWire.STEP_BYTES
            val kind = plan.stepKind(i)
            require(kind in RangingWire.STEP_START..RangingWire.STEP_STOP) { "a step is a start, a keep or a stop" }
            field[at] = kind.toByte()
            putLong(at + 1, plan.stepHandle(i))
            if (kind == RangingWire.STEP_START) {
                val peer = requireNotNull(plan.stepPeer(i)) { "a start carries the peer's token" }
                require(tokens < RangingWire.TOKEN_SLOTS) { "a plan starts at most ${RangingWire.TOKEN_SLOTS} pairs" }
                putLong(at + 1 + RangingWire.HANDLE_BYTES, plan.stepOwn(i))
                field[at + 1 + RangingWire.HANDLE_BYTES + RangingWire.KEY_BYTES] = tokens.toByte()
                val tokenAt = tokensFrom + tokens * RangingWire.PLAN_TOKEN_BYTES
                putShort(tokenAt, peer.size)
                peer.copyInto(field, tokenAt + RangingWire.LENGTH_BYTES)
                tokens++
            }
        }
        val keys = keysFor(seed)
        sealInPlace(keys.stream, keys.planTag, RangingWire.DOMAIN_PLAN, nonce, RangingWire.PLAN_BYTES)
        return Base64Url.encode(field, RangingWire.PLAN_FIELD_BYTES, chars)
    }

    /** **Open a plan field under [seed]**: true when it is exactly one, and the plan accessors then read it. */
    fun openPlan(seed: ContactSeed, text: String): Boolean {
        steps = 0
        if (!Base64Url.decodeInto(text, field, RangingWire.PLAN_FIELD_BYTES)) return false
        val keys = keysFor(seed)
        if (!openInPlace(keys.stream, keys.planTag, RangingWire.DOMAIN_PLAN, RangingWire.PLAN_BYTES)) return false
        val o = RangingWire.NONCE_BYTES
        val tokensFrom = o + RangingWire.MAX_PEERS * RangingWire.STEP_BYTES
        usedToken.fill(false)
        var count = 0
        for (s in 0 until RangingWire.MAX_PEERS) {
            val at = o + s * RangingWire.STEP_BYTES
            val kind = field[at].toInt()
            val slot = field[at + 1 + RangingWire.HANDLE_BYTES + RangingWire.KEY_BYTES].toInt()
            when (kind) {
                0 -> if (!zeros(at, RangingWire.STEP_BYTES)) return false
                RangingWire.STEP_START -> {
                    if (slot !in 0 until RangingWire.TOKEN_SLOTS || usedToken[slot]) return false
                    usedToken[slot] = true
                    stepAt[count++] = at
                }
                RangingWire.STEP_KEEP, RangingWire.STEP_STOP -> {
                    if (!zeros(at + 1 + RangingWire.HANDLE_BYTES, RangingWire.KEY_BYTES + 1)) return false
                    stepAt[count++] = at
                }
                else -> return false
            }
        }
        for (t in 0 until RangingWire.TOKEN_SLOTS) {
            val at = tokensFrom + t * RangingWire.PLAN_TOKEN_BYTES
            if (!usedToken[t]) {
                if (!zeros(at, RangingWire.PLAN_TOKEN_BYTES)) return false
                continue
            }
            val length = getShort(at)
            if (length !in 1..RangingWire.TOKEN_BYTES || !zeros(at + RangingWire.LENGTH_BYTES + length, RangingWire.TOKEN_BYTES - length)) return false
        }
        for (a in 0 until count) for (b in a + 1 until count) if (getLong(stepAt[a] + 1) == getLong(stepAt[b] + 1)) return false
        steps = count
        return true
    }

    /** Steps in the plan just opened. */
    val planSteps: Int get() = steps
    fun planKind(i: Int): Int = field[stepAt[i]].toInt()
    fun planHandle(i: Int): Long = getLong(stepAt[i] + 1)
    fun planOwn(i: Int): Long = getLong(stepAt[i] + 1 + RangingWire.HANDLE_BYTES)

    /** A start's peer token, as a value of its own. Allocates, once per start a phone is sent. */
    fun planPeer(i: Int): RangingToken {
        val slot = field[stepAt[i] + 1 + RangingWire.HANDLE_BYTES + RangingWire.KEY_BYTES].toInt()
        val at = RangingWire.NONCE_BYTES + RangingWire.MAX_PEERS * RangingWire.STEP_BYTES + slot * RangingWire.PLAN_TOKEN_BYTES
        return RangingToken.read(field, at + RangingWire.LENGTH_BYTES, getShort(at))
    }

    /** The plan just opened, as a value of its own. Allocates; for the tests. */
    fun plan(): RangingPlan = RangingPlan(
        List(steps) { i ->
            val handle = RangingHandle(planHandle(i))
            when (planKind(i)) {
                RangingWire.STEP_START -> RangingStep.Start(handle, SpareKey(planOwn(i)), planPeer(i))
                RangingWire.STEP_KEEP -> RangingStep.Keep(handle)
                else -> RangingStep.Stop(handle)
            }
        },
    )

    // ---- Sealing ---------------------------------------------------------------------------------

    internal fun sealPlain(seed: ContactSeed, nonce: ByteArray, domain: Byte, tagDomain: Byte, plain: ByteArray): ByteArray {
        val keys = keysFor(seed)
        field.fill(0)
        plain.copyInto(field, RangingWire.NONCE_BYTES)
        val tag = if (tagDomain == RangingWire.DOMAIN_REPORT_TAG || tagDomain == RangingWire.DOMAIN_PLAN_TAG) {
            keys.tagFor(tagDomain)
        } else {
            Hmac.Keyed(keys.stream.mac(byteArrayOf(tagDomain)))
        }
        sealInPlace(keys.stream, tag, domain, nonce, plain.size)
        return field.copyOf(RangingWire.NONCE_BYTES + plain.size + RangingWire.TAG_BYTES)
    }

    /**
     * **The field sealed in place**: its first [RangingWire.NONCE_BYTES] become [nonce], the
     * [plainBytes] after them are XORed with the keystream, and the [RangingWire.TAG_BYTES] after
     * those become the tag over everything before them.
     */
    private fun sealInPlace(streamKey: Hmac.Keyed, tag: Hmac.Keyed, domain: Byte, nonce: ByteArray, plainBytes: Int) {
        require(nonce.size == RangingWire.NONCE_BYTES) { "a nonce is ${RangingWire.NONCE_BYTES} bytes" }
        nonce.copyInto(field)
        xorStream(streamKey, domain, plainBytes)
        tag.mac(field, RangingWire.NONCE_BYTES + plainBytes, into = tagOut)
        tagOut.copyInto(field, RangingWire.NONCE_BYTES + plainBytes, 0, RangingWire.TAG_BYTES)
    }

    /** The field opened in place, or false when its tag is wrong. */
    private fun openInPlace(streamKey: Hmac.Keyed, tag: Hmac.Keyed, domain: Byte, plainBytes: Int): Boolean {
        tag.mac(field, RangingWire.NONCE_BYTES + plainBytes, into = tagOut)
        var differ = 0
        for (i in 0 until RangingWire.TAG_BYTES) differ = differ or (tagOut[i].toInt() xor field[RangingWire.NONCE_BYTES + plainBytes + i].toInt())
        if (differ != 0) return false
        xorStream(streamKey, domain, plainBytes)
        return true
    }

    /**
     * The field's [length] bytes after the nonce XOR its keystream, in place. One MAC derives the
     * field's stream key, HMAC(seed, domain ‖ nonce); each 32-byte block is then SHA-256(stream key ‖
     * block index), a fixed-length input under a secret key: one compression a block. An HMAC per
     * block was four, and on a debug build in one test process that was CPU enough to push a phone's
     * beat late (`UplinkFrameTest`, D-300).
     */
    private fun xorStream(streamKey: Hmac.Keyed, domain: Byte, length: Int) {
        derive[0] = domain
        field.copyInto(derive, 1, 0, RangingWire.NONCE_BYTES)
        streamKey.mac(derive, into = stream)
        val at = RangingWire.NONCE_BYTES
        var j = 0
        var i = 0
        while (i < length) {
            for (b in 0 until 4) stream[Sha256.DIGEST_BYTES + b] = (j ushr (24 - 8 * b)).toByte()
            engine.reset()
            engine.update(stream, 0, stream.size)
            engine.finish(block)
            val n = minOf(Sha256.DIGEST_BYTES, length - i)
            for (k in 0 until n) field[at + i + k] = (field[at + i + k].toInt() xor block[k].toInt()).toByte()
            i += n
            j++
        }
    }

    private fun putLong(at: Int, v: Long) {
        for (i in 0 until 8) field[at + i] = (v ushr (56 - 8 * i)).toByte()
    }

    private fun getLong(at: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (field[at + i].toLong() and 0xff)
        return v
    }

    private fun putShort(at: Int, value: Int) {
        field[at] = (value ushr 8).toByte()
        field[at + 1] = value.toByte()
    }

    private fun getShort(at: Int): Int = ((field[at].toInt() and 0xff) shl 8) or (field[at + 1].toInt() and 0xff)

    private fun zeros(at: Int, length: Int): Boolean {
        for (i in at until at + length) if (field[i].toInt() != 0) return false
        return true
    }

    private companion object {
        /** Seeds whose keys are kept: every seat of the largest round, with room. */
        const val KEYS_KEPT = 32
    }
}
