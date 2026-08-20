package home.someoneshome.ui

import androidx.compose.ui.graphics.vector.PathParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The marker shapes are GENERATED, and generated data is exactly what nobody reads.
 *
 * A malformed path string does not fail to compile and does not throw at draw time — it renders
 * nothing. A marker whose shape is invisible is a marker with no name, in a game where the shape
 * is the marker's whole identity, and it would be found by a player in a dark house rather than
 * here. So every path is parsed and measured.
 */
class MarkerShapesTest {

    @Test
    fun everyShapeParsesToSomethingWithArea() {
        val empty = mutableListOf<String>()
        val outside = mutableListOf<String>()

        for (shape in MarkerShapes.all) {
            val path = PathParser().parsePathString(shape.path).toPath()
            val b = path.getBounds()
            if (b.width <= 0f || b.height <= 0f) empty += shape.id
            // Everything must sit inside the 16x16 box, with a hair of tolerance for the
            // traced shapes whose outline rides the boundary.
            if (b.left < -0.1f || b.top < -0.1f ||
                b.right > MarkerShapes.VIEWBOX + 0.1f || b.bottom > MarkerShapes.VIEWBOX + 0.1f
            ) outside += "${shape.id}(${b.left},${b.top},${b.right},${b.bottom})"
        }

        assertTrue(empty.isEmpty(), "shapes that parsed to nothing: $empty")
        assertTrue(outside.isEmpty(), "shapes outside the viewbox: $outside")
    }

    @Test
    fun theRosterIsTheAlphabet() {
        assertEquals(44, MarkerShapes.ALPHABET.length)
        assertEquals(44, MarkerShapes.all.size)
        assertEquals(44, MarkerShapes.all.map { it.id }.toSet().size)
    }

    /**
     * The codec has to agree with `shape-encoder`'s, because the character it produces is what
     * gets printed into a QR code and scanned back months later. A silent reordering here would
     * make every card already on a wall decode to the wrong shape.
     */
    @Test
    fun encodeAndDecodeRoundTrip() {
        MarkerShapes.all.forEachIndexed { i, shape ->
            val ch = MarkerShapes.encode(i)
            assertEquals(shape, MarkerShapes.decode(ch), "round trip failed at $i (${shape.id})")
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
     * `pentagon` and `hexagon` are absent on purpose — the legibility pass upstream cut anything
     * reading as "circle with corners". The device design's own fixture reintroduced both, so
     * this asserts which set won.
     */
    @Test
    fun theShapesTheLegibilityPassRejectedStayRejected() {
        assertEquals(null, MarkerShapes["pentagon"])
        assertEquals(null, MarkerShapes["hexagon"])
    }
}
