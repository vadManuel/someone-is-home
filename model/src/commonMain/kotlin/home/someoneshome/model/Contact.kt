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

/**
 * The bytes this hex spells, read off the characters in place (rule 7, D-276): the array is the only
 * allocation. It once cut a two-character substring per byte, which made a seed's sixteen bytes
 * cost seventeen objects every time a token or an upload's filler was computed.
 */
internal fun String.hexBytes(): ByteArray =
    ByteArray(length / 2) { i -> hexByteAt(2 * i).toByte() }

/** The byte the two hex characters at [at] spell, 0 to 255. Allocates nothing. */
internal fun CharSequence.hexByteAt(at: Int): Int = (this[at].digitToInt(16) shl 4) or this[at + 1].digitToInt(16)

internal fun ByteArray.toLowerHex(bytes: Int = size): String = buildString(bytes * 2) {
    for (i in 0 until bytes) {
        val v = this@toLowerHex[i].toInt() and 0xff
        append(HEX[v ushr 4]); append(HEX[v and 0xf])
    }
}

private const val HEX = "0123456789abcdef"

/**
 * **Why a phone's radio went down, in the one distinction D-187 draws: a person's act, or the
 * phone's own** (D-223, the owner's 2026-11-15 ruling).
 *
 * D-187: *a house notice reports detectable deliberate evasion and nothing else; genuine radio loss
 * earns no notice.* So the only thing the house needs to know about an outage is which side of that
 * line it is on, and this is the whole of what a phone says about one.
 *
 * - [Permission]: something this game needs switched off during a round (D-310, D-311): Bluetooth's
 *   or Nearby Interaction's permission withdrawn, or **the Bluetooth radio itself turned off**
 *   (Control Centre, airplane mode), as the radios say, or any grant now reading switched off
 *   (`Permissions.refusedGrants`, read by the uploader every beat). Every one is required to take a
 *   seat (D-161, D-309, D-311), so one missing during a round is something a person did. **Named at
 *   the next meeting in the owner's permission sentence, and after the warning it closes the
 *   house** (`Event.HouseClosedOnPermission`).
 * - [Person]: the phone locked or taken out of the app (D-229).
 *   **Named at the next meeting**, with no warning, and it **never** counts toward closing the
 *   house (the owner, 2026-11-20).
 * - [Device]: the OS resetting the radio, or the hardware not supporting it. **Recorded for the
 *   authority and announced to nobody**: a notice naming a player whose phone failed reads to the
 *   room exactly like a notice naming a cheat (D-187). It shows as disconnection on the monitoring
 *   surfaces, which it does by itself, since a radio that is down is heard by nobody.
 *
 * **The three serial names are the same length**, so the one upward body is the same number of
 * bytes whichever it carries (D-217). `RadioFaultTest` pins it.
 */
@ClientFacing
@kotlinx.serialization.Serializable
enum class RadioCause {
    @kotlinx.serialization.SerialName("permit")
    Permission,

    @kotlinx.serialization.SerialName("person")
    Person,

    @kotlinx.serialization.SerialName("device")
    Device,
}

/**
 * **Two causes for one onset, as one** (D-303, D-310): a permission switched off outranks a person's
 * other act, which outranks the phone's own failure. One beat that holds both a leave and a radio's
 * onset, or both radios going down together, sends one report, and it must not hide the stronger
 * act: a device fault that coincided must not hide an evasion D-187 names, and a leave that
 * coincided must not hide a permission the owner's warning is about. Null is no onset.
 */
fun RadioCause.outranking(other: RadioCause?): RadioCause =
    if (other != null && other.ordinal < ordinal) other else this

/**
 * **What a touch did with an armed phone's Revoke, on that phone's own answer** (the owner's
 * 2026-11-18 ruling, D-290). Carried by `Effect.AbilityFired.touch`.
 *
 * A touch spends an armed Revoke whether or not it lands (`Rules.contact`'s `revokeArms` filter), and
 * until D-290 the armed phone was told only `AbilityFired(cooldownStarted = true)`, the same answer as
 * an Egress fired into a ready clock, so its tile held ARMED until 20 s from the hold over an arm the
 * house had already cleared. The three values are one digit each on the wire, so the frame keeps the
 * touch's one length (D-216, D-219).
 */
enum class ArmedTouch(
    /** The buzz the answer carries: Long when the Revoke landed (D-290), nothing otherwise. */
    val haptic: Haptic?,
) {
    /** Not a touch's answer: a Surge, an Isolate, a Static or an Egress. */
    None(null),

    /**
     * **A touch spent this phone's armed Revoke and it landed on nobody**: the other phone was
     * already Revoked, or the touch was this phone's own. Between two armed phones the one whose
     * Revoke did not land is the one Revoked, so its `Spent` is declined at the boundary and it is
     * sent `RevokeLanded` in its place (D-220). No buzz: the owner ruled the Long for a landed
     * Revoke only.
     */
    Spent(null),

    /** **This phone's armed Revoke landed on the other phone of the touch.** The Long buzz (D-290). */
    Landed(Haptic.Long),
}
