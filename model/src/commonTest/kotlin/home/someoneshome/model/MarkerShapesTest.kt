package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * The roster and its codec, which are identity rather than drawing.
 *
 * These followed the roster down from `ui`. The one assertion left behind there is the one that
 * needs a path parser — whether a shape renders is a `ui` question; whether a card decodes to the
 * shape printed on it is not.
 */
class MarkerShapesTest {

    @Test
    fun theRosterIsTheAlphabet() {
        assertEquals(44, MarkerShapes.ALPHABET.length)
        assertEquals(44, MarkerShapes.all.size)
        assertEquals(44, MarkerShapes.all.map { it.id }.toSet().size)
    }

    /** SPACE is ambiguous in print, so the alphabet is QR alphanumeric minus it. */
    @Test
    fun theAlphabetExcludesSpace() {
        assertEquals(-1, MarkerShapes.ALPHABET.indexOf(' '))
    }

    /**
     * The codec has to agree with `shape-encoder`'s, because the character it produces is printed
     * into a QR code and scanned back months later. A silent reordering here would make every
     * card already on a wall decode to the wrong shape.
     */
    @Test
    fun encodeAndDecodeRoundTrip() {
        MarkerShapes.all.forEachIndexed { i, shape ->
            assertEquals(shape, MarkerShapes.decode(MarkerShapes.encode(i)), "round trip failed at $i (${shape.id})")
        }
        assertEquals('0', MarkerShapes.encode(0))
        assertEquals("circle", MarkerShapes.decode('0')?.id)
    }

    @Test
    fun aCharacterOutsideTheRosterDecodesToNothing() {
        assertEquals(null, MarkerShapes.decode('!'))
        assertEquals(null, MarkerShapes.decode(' '))
    }

    /**
     * `pentagon` and `hexagon` are absent on purpose (D-070) — the legibility pass upstream cut
     * anything reading as "circle with corners". The device design's own fixture reintroduced
     * both, so this asserts which set won.
     */
    @Test
    fun theShapesTheLegibilityPassRejectedStayRejected() {
        assertEquals(null, MarkerShapes["pentagon"])
        assertEquals(null, MarkerShapes["hexagon"])
    }

    /**
     * Lookup by id works for every shape in the roster.
     *
     * `ui` calls `MarkerShapes["diamond"]` and friends by literal, and a typo there returns null
     * and draws nothing — a marker with no name, in a game where the shape IS the name.
     */
    @Test
    fun everyShapeIsFoundByItsOwnId() {
        for (shape in MarkerShapes.all) assertNotNull(MarkerShapes[shape.id], shape.id)
        for (shape in MarkerShapes.all) assertEquals(shape, MarkerShapes.require(shape.id))
    }

    /** A constant id that does not exist is a typo, and it says so rather than returning null. */
    @Test
    fun requireRefusesAnUnknownId() {
        val failure = assertFailsWith<IllegalArgumentException> { MarkerShapes.require("pentagon") }
        assertTrue(failure.message!!.contains("typo"), failure.message!!)
    }
}
