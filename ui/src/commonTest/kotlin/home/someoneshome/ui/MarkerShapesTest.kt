package home.someoneshome.ui

import androidx.compose.ui.graphics.vector.PathParser
import home.someoneshome.model.MarkerShapes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The marker shapes are GENERATED, and generated data is exactly what nobody reads.
 *
 * A malformed path string does not fail to compile and does not throw at draw time — it renders
 * nothing. A marker whose shape is invisible is a marker with no name, in a game where the shape
 * is the marker's whole identity, and it would be found by a player in a dark house rather than
 * here. So every path is parsed and measured.
 *
 * **Only the drawing question is left here.** The roster and its codec moved to `model` with the
 * shapes themselves — whether a card decodes to the shape printed on it is identity, not
 * rendering, and map persistence needs it. `home.someoneshome.model.MarkerShapesTest` holds
 * those. This one needs a path parser and so stays where the parser is.
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

    /**
     * Every id this module names as a literal resolves.
     *
     * `PanelModel` wraps its lookups in `listOfNotNull`, so a typo does not draw a blank marker —
     * it silently makes the list one shorter, and nobody counts a list they did not write. The
     * roster moving to `model` removed the risk of a SECOND roster; it does nothing about a
     * misspelled id, which is what this covers.
     *
     * Brittle by construction: a seventh literal added to `ui` will not appear here on its own,
     * and the `PanelVals` fixtures are not reachable without building a `PanelState`, so this
     * checks the ids rather than the fixtures that use them. `MarkerShapes.require` is the
     * non-brittle fix and is available to any call site that wants a typo to be loud.
     */
    @Test
    fun everyIdNamedInThisModuleResolves() {
        for (id in listOf("arrow_right", "cross", "diamond", "ring", "square_frame", "triangle_up")) {
            assertTrue(MarkerShapes[id] != null, "ui names a shape that is not in the roster: $id")
        }
    }
}
