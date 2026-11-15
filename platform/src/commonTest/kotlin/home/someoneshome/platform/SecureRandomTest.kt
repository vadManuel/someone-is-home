package home.someoneshome.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The secure source answers, answers differently every time, and reaches every bit** (D-215).
 *
 * What a test can say about a random source is narrow, and this says only that: it cannot tell a
 * CSPRNG from a well-seeded `kotlin.random.Random`. That half is `seedSourceCordon`'s, which fails
 * the build on any use of `kotlin.random` in `app` or `platform`. What this catches is the other
 * failure, the one a refactor produces: a source that returns the same bytes on every call, a
 * buffer that is never written, or a long assembled from too few bytes.
 */
class SecureRandomTest {

    @Test
    fun itReturnsExactlyTheBytesAskedFor() {
        assertEquals(0, secureRandomBytes(0).size)
        assertEquals(8, secureRandomBytes(8).size)
        assertEquals(33, secureRandomBytes(33).size)
    }

    /** A pinned buffer that was never written is sixteen zeros, every time. */
    @Test
    fun noTwoDrawsInAThousandRepeat() {
        val drawn = List(1_000) { secureRandomBytes(16).joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') } }
        assertEquals(drawn.size, drawn.toSet().size, "the secure source repeated itself")
    }

    /**
     * **Every one of the sixty-four bits of a seed moves.** A long assembled from four bytes, or a
     * byte order that dropped the top half, leaves bits that are always zero, and every round's seed
     * would then sit in a space a phone could search.
     */
    @Test
    fun everyBitOfTheSeedTakesBothValues() {
        val seeds = List(512) { secureRandomLong() }
        val ones = seeds.fold(0L) { acc, s -> acc or s }
        val zeros = seeds.fold(0L) { acc, s -> acc or s.inv() }
        assertEquals(-1L, ones, "a bit of the seed was never 1: ${ones.inv().toULong().toString(2)}")
        assertEquals(-1L, zeros, "a bit of the seed was never 0: ${zeros.inv().toULong().toString(2)}")
        assertTrue(seeds.toSet().size == seeds.size, "two seeds in 512 were equal")
    }
}
