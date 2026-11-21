package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Page 1 has four tiles and no NOTES, and draws the same for both roles** (D-315).
 *
 * The owner removed the Notes app. Its tile, its screen and its words are gone, and the grid closes
 * up the way it did when TERMINAL went (D-199): FILES and MESSAGES on the first row, SETTINGS and
 * CAMERA on the second, each row ending in empty slots, so the grid keeps its shape. Page 1 is one
 * of the pages a glance across a dark room reads, and only page 2 may differ by role (GDD M8, D-286),
 * so both roles' page 1 is rendered at the mockup's 720×1520 with its camera band and compared
 * word for word and pixel for pixel. The renders land in `build/screens/home-phone-*.png`.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PageOneTilesTest {

    private val out = File("build/screens").apply { mkdirs() }

    private val tiles = listOf("FILES", "MESSAGES", "SETTINGS", "CAMERA")

    /** **GUARD: there is no screen named Notes to reach.** */
    @Test
    fun noScreenIsNamedNotes() {
        val named = ScreenId.entries.filter { "note" in it.name.lowercase() }
        assertEquals(emptyList(), named, "a screen named Notes is back (D-315)")
    }

    /** **GUARD: page 1 draws FILES, MESSAGES, SETTINGS and CAMERA, in the grid's order, and no NOTES.** */
    @Test
    fun pageOneHasFourTilesAndNoNotes() {
        for (role in PanelRole.entries) {
            val (texts, bounds) = render(role).let { it.texts to it.bounds }
            assertTrue(texts.none { "NOTES" in it }, "page 1 drew NOTES for a $role (D-315): $texts")
            for (tile in tiles) {
                assertEquals(1, texts.count { it == tile }, "page 1 drew $tile ${texts.count { it == tile }} times for a $role")
            }
            val (files, messages, settings, camera) = tiles.map { bounds.getValue(it) }
            assertEquals(files.top, messages.top, "FILES and MESSAGES are not one row for a $role")
            assertEquals(settings.top, camera.top, "SETTINGS and CAMERA are not one row for a $role")
            assertTrue(settings.top > files.top, "SETTINGS did not sit on the row below FILES for a $role")
            // A label is centred under its tile, so a column is read off the labels' centres.
            assertTrue(messages.center.x > files.center.x, "MESSAGES is not to the right of FILES for a $role")
            assertEquals(files.center.x, settings.center.x, 1f, "the rows do not start in the same column for a $role")
            assertEquals(messages.center.x, camera.center.x, 1f, "MESSAGES and CAMERA are not in one column for a $role")
        }
    }

    /** **GUARD: an Insider's page 1 is a Resident's, the same words and the same pixels.** */
    @Test
    fun pageOneRendersTheSameForBothRoles() {
        val resident = render(PanelRole.Resident)
        val insider = render(PanelRole.Insider)
        assertTrue(resident.texts.isNotEmpty(), "page 1 drew no words")
        assertEquals(resident.texts, insider.texts, "page 1 drew an Insider different words from a Resident")
        assertTrue(resident.pixels.contentEquals(insider.pixels), "page 1 drew an Insider different pixels from a Resident")
    }

    private class Drawn(val texts: List<String>, val bounds: Map<String, Rect>, val pixels: IntArray)

    /** Page 1 for [role] at the mockup's 720×1520 with its camera band, written to `build/screens`. */
    private fun render(role: PanelRole): Drawn {
        var drawn: Drawn? = null
        runDesktopComposeUiTest(width = 720, height = 1520) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    DeviceCanvas(insets = PanelInsets(top = 36.dp)) {
                        Screen(PanelState(screen = ScreenId.Home, role = role))
                    }
                }
            }
            val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
            val texts = nodes.flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            val bounds = nodes.flatMap { node -> node.config[SemanticsProperties.Text].map { it.text to node.boundsInRoot } }
                .filter { it.first in tiles }.toMap()
            val img = onRoot().captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(out, "home-phone-${role.name.lowercase()}.png"))
            drawn = Drawn(texts, bounds, img.getRGB(0, 0, img.width, img.height, null, 0, img.width))
        }
        return drawn!!
    }
}
