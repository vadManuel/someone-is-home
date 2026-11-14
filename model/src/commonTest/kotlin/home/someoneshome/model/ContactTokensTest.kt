package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * **D-212: the token is an HMAC of the seed and the window, the same on the phone and on the host,
 * and only the host can turn it back into a seat.**
 */
class ContactTokensTest {

    /** RFC 4231, test cases 1, 2 and 6 (a key longer than the block). */
    @Test
    fun `the HMAC is RFC 4231's`() {
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            Hmac.sha256(ByteArray(20) { 0x0b }, "Hi There".encodeToByteArray()).toLowerHex(),
        )
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            Hmac.sha256("Jefe".encodeToByteArray(), "what do ya want for nothing?".encodeToByteArray())
                .toLowerHex(),
        )
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            Hmac.sha256(
                ByteArray(131) { 0xaa.toByte() },
                "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray(),
            ).toLowerHex(),
        )
        // And the bare digest across a padding boundary: 55, 56 and 64 bytes.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digest(ByteArray(0)).toLowerHex(),
        )
        val boundaries = mapOf(
            55 to "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            56 to "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            64 to "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
        )
        for ((n, digest) in boundaries) {
            assertEquals(digest, Sha256.digest(ByteArray(n) { 'a'.code.toByte() }).toLowerHex(), "$n bytes")
        }
    }

    private val seed = ContactSeed((0 until 16).joinToString("") { it.toString(16).padStart(2, '0') })

    /**
     * **Deterministic, and pinned to vectors computed outside this codebase** (Python's `hmac`).
     *
     * A function that agreed with itself would pass a determinism test while drifting from the
     * phone's copy; the pinned values are what make the phone and the host provably one function.
     */
    @Test
    fun `a token is the same every time and matches an outside computation`() {
        assertEquals(ContactToken("11aced382e4ffb790e2c2d314c69222d"), ContactTokens.of(seed, ContactWindow(0)))
        assertEquals(ContactToken("4c04eea57ec91d127691158648aacb88"), ContactTokens.of(seed, ContactWindow(1)))
        assertEquals(
            ContactToken("414a9920aa0b3232ddb539c8d876e89a"),
            ContactTokens.of(seed, ContactWindow(123_456_789)),
        )
        assertEquals(
            ContactTokens.of(ContactSeed(seed.hex), ContactWindow(42)),
            ContactTokens.of(seed, ContactWindow(42)),
        )
    }

    /** D-032: a new token every fifteen seconds, and never one repeated. */
    @Test
    fun `tokens rotate every window and never repeat`() {
        val tokens = (0L until 2_000L).map { ContactTokens.of(seed, ContactWindow(it)) }
        assertEquals(tokens.size, tokens.distinct().size, "a token repeated across windows")
        for (i in 1 until tokens.size) {
            check(tokens[i] != tokens[i - 1]) { "window $i kept window ${i - 1}'s token" }
        }
        assertEquals(ContactWindow(0), ContactWindow.containing(14_999))
        assertEquals(ContactWindow(1), ContactWindow.containing(15_000))
        assertEquals(ContactWindow(-1), ContactWindow.containing(-1))
    }

    /** Eighteen seats, a few hundred windows: every token comes back to the seat that sent it. */
    @Test
    fun `the host inverts every token to its seat and nothing else`() {
        val seeds = (0 until 18).map { i ->
            Seat(i) to ContactSeed.of(Hmac.sha256(byteArrayOf(i.toByte()), "seed".encodeToByteArray()))
        }
        for (w in 0L until 300L) {
            val window = ContactWindow(w)
            for ((seat, s) in seeds) {
                assertEquals(seat, ContactTokens.seatOf(ContactTokens.of(s, window), window, seeds), "window $w")
                // The same token in the next window is nobody: tokens do not carry across.
                assertNull(ContactTokens.seatOf(ContactTokens.of(s, window), ContactWindow(w + 1), seeds))
            }
        }
        // Order of the list is not an input: the answer is the same reversed.
        val t = ContactTokens.of(seeds[7].second, ContactWindow(5))
        assertEquals(Seat(7), ContactTokens.seatOf(t, ContactWindow(5), seeds.reversed()))
        assertNull(ContactTokens.seatOf(ContactToken("0".repeat(32)), ContactWindow(5), seeds))
    }

    /** A seed or token of the wrong width cannot be built, so every message carrying one is one size. */
    @Test
    fun `seeds and tokens are one width`() {
        assertFailsWith<IllegalArgumentException> { ContactSeed("abc") }
        assertFailsWith<IllegalArgumentException> { ContactSeed("A".repeat(32)) }
        assertFailsWith<IllegalArgumentException> { ContactToken("0".repeat(30)) }
        assertEquals(32, ContactSeed.of(ByteArray(32)).hex.length)
    }
}
