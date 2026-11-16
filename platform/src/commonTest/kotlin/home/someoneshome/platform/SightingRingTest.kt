package home.someoneshome.platform

import home.someoneshome.model.ContactToken
import home.someoneshome.model.protocol.SightingUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The radio's ring: one slot per token, the strongest reading kept, never more than an upload
 * carries** (D-218, rule 7).
 */
class SightingRingTest {

    private fun bytes(last: Int, first: Int = 0xab): ByteArray =
        ByteArray(16) { i -> if (i == 15) last.toByte() else if (i == 0) first.toByte() else i.toByte() }

    private fun hex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun oneTokenHeardManyTimesIsOneReadingAtItsStrongest() {
        val ring = SightingRing()
        val t = bytes(1)
        for (rssi in listOf(-70, -31, -55, -40)) ring.heard(t, rssi)
        val out = ring.drain()
        assertEquals(listOf(RadioReading(ContactToken(hex(t)), -31)), out)
        assertEquals(3L, ring.coalesced)
    }

    /** iOS's 127 is no reading: it never displaces a real one, and a real one displaces it. */
    @Test
    fun aReadingOfNoRssiNeverDisplacesARealOne() {
        val ring = SightingRing()
        val a = bytes(1)
        val b = bytes(2)
        ring.heard(a, -60)
        ring.heard(a, 127)
        ring.heard(b, 127)
        ring.heard(b, -80)
        val c = bytes(3)
        ring.heard(c, 127)
        val out = ring.drain().associate { it.token.hex to it.rssi }
        assertEquals(-60, out[hex(a)], "127 displaced a real reading")
        assertEquals(-80, out[hex(b)], "a real reading did not displace 127")
        assertEquals(127, out[hex(c)], "a token heard only without a reading was not passed on for the house to drop")
    }

    @Test
    fun aDrainEmptiesItAndTheOrderIsFirstHeard() {
        val ring = SightingRing()
        ring.heard(bytes(3), -50)
        ring.heard(bytes(1), -30)
        ring.heard(bytes(2), -90)
        assertEquals(listOf(hex(bytes(3)), hex(bytes(1)), hex(bytes(2))), ring.drain().map { it.token.hex })
        assertEquals(emptyList(), ring.drain())
        assertEquals(0, ring.size)
    }

    /** Tokens that differ only in their last byte, or only in their first, are different phones. */
    @Test
    fun everyByteOfTheTokenCounts() {
        val ring = SightingRing()
        ring.heard(bytes(1), -50)
        ring.heard(bytes(2), -50)
        ring.heard(bytes(1, first = 0xac), -50)
        assertEquals(3, ring.drain().size)
    }

    /**
     * Full, a stronger newcomer takes the weakest's slot and a weaker one is refused, so what the
     * upload gets is the strongest it can carry, which is the end that decides a contact.
     */
    @Test
    fun fullTheWeakestGivesWayToAStrongerNewcomerAndNeverToAWeakerOne() {
        val ring = SightingRing()
        assertEquals(SightingUpload.SLOTS, ring.capacity)
        for (i in 0 until ring.capacity) ring.heard(bytes(i), -60 - i)
        ring.heard(bytes(200), -99)
        assertTrue(ring.drain().none { it.token.hex == hex(bytes(200)) }, "a weaker newcomer displaced a held reading")
        for (i in 0 until ring.capacity) ring.heard(bytes(i), -60 - i)
        ring.heard(bytes(200), -30)
        val out = ring.drain()
        assertEquals(ring.capacity, out.size)
        assertTrue(out.any { it.token.hex == hex(bytes(200)) && it.rssi == -30 }, "the touch was refused by a full ring")
        val weakest = hex(bytes(ring.capacity - 1))
        assertTrue(out.none { it.token.hex == weakest }, "the weakest was kept over a touch")
        assertEquals(hex(bytes(200)), out.last().token.hex, "the newcomer is not last in first-heard order")
        assertEquals(2L, ring.displaced)
    }
}
