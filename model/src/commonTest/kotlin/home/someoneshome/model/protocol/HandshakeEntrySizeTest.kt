package home.someoneshome.model.protocol

import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.HandshakeTaps
import home.someoneshome.model.MarkerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A Handshake's hand-over is one length, whatever was tapped** (the owner's Q7 ruling, D-296).
 *
 * The entry now carries the timing of the player's taps, and timing is exactly the kind of content
 * whose width could vary: a long gap is more digits than a short one, a three-beat rhythm fewer
 * gaps than a six-beat one. So every entry is `HandshakeTaps.GAPS` five-digit numbers, and this
 * measures the request as it rides the upload's slot (`RequestSlot.bytesOf`), the same bytes for a
 * three-beat rhythm and a six-beat one, a perfect return and a wild one, no tap and a seventh.
 *
 * Role is not an input here at all, and that is the role half of the claim: nothing that builds an
 * entry takes one. `HandshakeTimingTest` in `ui` drives a Resident's phone and an Insider's through
 * the screen's own taps and measures what each put on the wire.
 */
class HandshakeEntrySizeTest {

    private val card = MarkerId("card-hall")

    private fun bytes(entered: List<Int>): Int =
        RequestSlot.bytesOf(Asked.Act(IntentMessage.HandOverEntry(card, entered)))

    private fun stamped(gaps: List<Long>): List<Long> = gaps.runningFold(9_000_000L) { at, gap -> at + gap }

    /** Taps a player could make, and some no player could: every width a number could have taken. */
    private val returns: Map<String, List<Long>> = buildMap {
        put("no tap", emptyList())
        put("one tap", listOf(123L))
        for (beats in HandshakeRhythm.LEAST_BEATS..HandshakeRhythm.MOST_BEATS) {
            put("$beats beats, perfect", stamped(List(beats - 1) { 300L }))
            put("$beats beats, all at once", List(beats) { 5L })
            put("$beats beats, a millisecond apart", stamped(List(beats - 1) { 1L }))
            put("$beats beats, ten seconds apart", stamped(List(beats - 1) { 10_000L }))
            put("$beats beats, an hour apart", stamped(List(beats - 1) { 3_600_000L }))
            put("$beats beats, ragged", stamped(List(beats - 1) { 7L * (it + 1) * (it + 1) * 31L }))
        }
        put("seven taps", stamped(List(HandshakeRhythm.MOST_BEATS) { 400L }))
    }

    @Test
    fun `every Handshake entry is the same bytes long`() {
        val sizes = returns.mapValues { (_, taps) -> HandshakeTaps.of(taps) }
        for ((label, entered) in sizes) {
            assertEquals(HandshakeTaps.GAPS, entered.size, "$label: $entered")
            assertTrue(entered.all { it.toString().length == 5 }, "$label has a number that is not five digits: $entered")
        }
        val lengths = sizes.mapValues { (_, entered) -> bytes(entered) }
        assertEquals(1, lengths.values.toSet().size, "Handshake entries differ in length: $lengths")
        println("D-296 HANDSHAKE ENTRY BYTES IN THE SLOT — ${lengths.values.first()} for all ${lengths.size}")
    }

    /** The reader gives back what the writer wrote, a gap at a time, and no gap as no gap. */
    @Test
    fun `the gaps come back as they were written`() {
        val written = HandshakeTaps.of(stamped(listOf(200L, 900L, 0L, 12_000L)))
        assertEquals(listOf(200, 900, 0, HandshakeTaps.LONGEST, null), HandshakeTaps.gapsIn(written))
    }
}
