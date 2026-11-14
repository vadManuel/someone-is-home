package home.someoneshome.model

import kotlin.jvm.JvmInline

/*
 * **The contact handshake's shared half: seeds, windows, tokens and sightings** (D-212, D-032/D7).
 *
 * A phone advertises a token that rotates every fifteen seconds and nothing else. The token is an
 * HMAC of that seat's seed and the window index, so the phone that advertises it and the house that
 * inverts it compute the same function from the same two inputs, and nobody without the seed can
 * link one window's token to the next. That is the whole of D7: *resolvable only by the authority*.
 *
 * Everything here is pure and deterministic (rule 4). Nothing reads a clock: the window is an
 * argument, and which clock it is cut from is the wiring's to choose (unit 3).
 *
 * **No role, no seat, no armed bit is an input to any token**, and that is checkable from the
 * signatures alone: [ContactTokens.of] takes a seed and a window. An Insider's phone and a
 * Resident's, armed or not, advertise tokens of one length drawn from one function (D-212).
 */

/**
 * **One seat's contact seed: 16 bytes, as 32 lowercase hex characters.**
 *
 * Issued once, at arming, to the seat it belongs to and to no other phone
 * ([Effect.ContactSeedIssued], D-212). It is the HMAC key for that seat's tokens, so a phone holding
 * somebody else's seed could follow that player through the house by radio all round: the
 * addressing is what keeps it on one socket, and the allowlist row is what keeps it to the living.
 *
 * **May reveal: nothing about role or alignment.** It is drawn from the round's seed and the seat
 * index alone (`core`'s `contactSeedFor`), so every seat's seed has the same shape and the same
 * provenance whichever role the seat holds.
 *
 * Fixed width, so the message carrying it is the same size on every phone.
 */
@ClientFacing
@kotlinx.serialization.Serializable
@JvmInline
value class ContactSeed(val hex: String) {
    init {
        require(hex.length == ContactTokens.TOKEN_BYTES * 2 && hex.isLowerHex()) {
            "a contact seed is ${ContactTokens.TOKEN_BYTES} bytes of lowercase hex"
        }
    }

    /** The key bytes. */
    fun bytes(): ByteArray = hex.hexBytes()

    override fun toString(): String = "ContactSeed(…)"

    companion object {
        /** The first [ContactTokens.TOKEN_BYTES] bytes of [bytes], as a seed. */
        fun of(bytes: ByteArray): ContactSeed {
            require(bytes.size >= ContactTokens.TOKEN_BYTES) { "a seed needs 16 bytes" }
            return ContactSeed(bytes.copyOf(ContactTokens.TOKEN_BYTES).toLowerHex())
        }
    }
}

/**
 * **A fifteen-second slice of the round, by index** (D-032: *rotating ephemeral token, 15 s*).
 *
 * An index rather than a time, for [Tick]'s reason: the derivation never reads a clock. Which clock
 * the index is cut from — the host's monotonic reading through `ClockSync` is the obvious one — is
 * unit 3's to wire, and [containing] is the one cut both ends must use.
 */
@JvmInline
value class ContactWindow(val index: Long) {
    companion object {
        /** D-032. Shorter is free on the host; the bound is radio overhead. */
        const val MILLIS: Long = 15_000L

        /** The window [millis] falls in. Floor division, so a reading of 0 is window 0. */
        fun containing(millis: Long): ContactWindow = ContactWindow(millis.floorDiv(MILLIS))
    }
}

/**
 * **What a phone advertises in one window: 16 bytes of HMAC, as 32 lowercase hex characters.**
 *
 * **May reveal: nothing.** To anybody without the seat's seed it is indistinguishable from noise,
 * and the next window's token cannot be linked to this one. No role, no seat, no armed bit is an
 * input to it (D-212).
 *
 * ⚠️ **The length is provisional for the radio.** Sixteen bytes is a 128-bit service UUID's worth,
 * which is what iOS can advertise in the foreground; unit 4 may need to carry fewer, and if it does
 * the truncation moves into [ContactTokens.of] so both ends keep computing one function.
 */
@ClientFacing
@kotlinx.serialization.Serializable
@JvmInline
value class ContactToken(val hex: String) {
    init {
        require(hex.length == ContactTokens.TOKEN_BYTES * 2 && hex.isLowerHex()) {
            "a contact token is ${ContactTokens.TOKEN_BYTES} bytes of lowercase hex"
        }
    }
}

/**
 * **One sighting: this phone heard [token] at [rssi] dBm during [window].**
 *
 * A fact, reported (rule 10): the phone heard what it heard, and what that means — whose token, how
 * close, whether anybody touched — is the house's to conclude (unit 2). It names no seat because a
 * phone cannot know one: tokens are resolvable by the authority alone (D7).
 *
 * [window] is the window the *scanning* phone was in when it heard the token. The house allows for
 * the two phones' clocks disagreeing at a window edge; that tolerance is unit 2's.
 *
 * Not `@Serializable`: what goes on the wire is the fixed-size packing in
 * `protocol.SightingUpload`, never this shape.
 */
@ClientFacing
data class Sighting(val token: ContactToken, val rssi: Int, val window: ContactWindow)

/** **The token function and its inverse.** One function, run on the phone and on the host. */
object ContactTokens {

    /** Bytes in a seed and in a token. See [ContactToken] for why the token's is provisional. */
    const val TOKEN_BYTES: Int = 16

    /**
     * **The token [seed] advertises in [window]:** HMAC-SHA-256(seed, `T` ‖ window, big-endian),
     * truncated to [TOKEN_BYTES].
     *
     * The domain byte keeps tokens apart from anything else ever keyed off the same seed — the
     * upload's padding is (see `SightingUpload`), and a padding slot that could equal a real token
     * would be a sighting of a phone nobody saw.
     */
    fun of(seed: ContactSeed, window: ContactWindow): ContactToken =
        ContactToken(Hmac.sha256(seed.bytes(), domain(DOMAIN_TOKEN, window.index)).toLowerHex(TOKEN_BYTES))

    /**
     * **The host's inversion: which seat advertised [token] in [window]**, or null for a token no
     * seat in [seeds] advertised then.
     *
     * Walks [seeds] in seat order and answers the first match, so the answer is deterministic even
     * in the case that cannot happen (two seeds colliding on one window's 128 bits). Exact window
     * only: the tolerance for clocks that disagree at an edge is the resolver's (unit 2), which asks
     * this once per window it allows.
     */
    fun seatOf(token: ContactToken, window: ContactWindow, seeds: List<Pair<Seat, ContactSeed>>): Seat? =
        seeds.sortedBy { it.first.index }.firstOrNull { (_, seed) -> of(seed, window) == token }?.first

    /** One domain byte, then [value] as eight big-endian bytes, then any [extra] bytes. */
    internal fun domain(tag: Byte, value: Long, vararg extra: Byte): ByteArray =
        byteArrayOf(tag) + ByteArray(8) { i -> (value ushr (56 - 8 * i)).toByte() } + extra

    internal const val DOMAIN_TOKEN: Byte = 'T'.code.toByte()
}

internal fun String.isLowerHex(): Boolean = all { it in '0'..'9' || it in 'a'..'f' }

internal fun String.hexBytes(): ByteArray =
    ByteArray(length / 2) { i -> substring(2 * i, 2 * i + 2).toInt(16).toByte() }

internal fun ByteArray.toLowerHex(bytes: Int = size): String = buildString(bytes * 2) {
    for (i in 0 until bytes) {
        val v = this@toLowerHex[i].toInt() and 0xff
        append(HEX[v ushr 4]); append(HEX[v and 0xf])
    }
}

private const val HEX = "0123456789abcdef"
