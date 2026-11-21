package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Notes draws an Insider exactly what it draws a Resident in the same state** (D-312, GDD M8,
 * D-286).
 *
 * Notes is one tap from page 1, and page 2 is the only page allowed to differ by role. The note
 * used to read an Insider-only line on every Insider's phone and a Resident's note on every
 * Resident's, so a glance at anybody's Notes was their role. The note depends on two things, a
 * house being attached and the seed, and both are swept here, through the line and through the
 * rendered screen, words and pixels.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class NotesParityTest {

    private fun state(role: PanelRole, housed: Boolean, seed: Int) =
        PanelState(screen = ScreenId.Notes, role = role, houseAttached = housed, noteSeed = seed)

    /** **GUARD: the line is the same for both roles at every seed, housed or not.** */
    @Test
    fun anInsidersNoteIsAResidentsNoteInEveryState() {
        for (housed in listOf(false, true)) {
            for (seed in -3..20) {
                assertEquals(
                    PanelVals(state(PanelRole.Resident, housed, seed)).noteLine,
                    PanelVals(state(PanelRole.Insider, housed, seed)).noteLine,
                    "Notes told an Insider different words from a Resident (housed=$housed, seed=$seed)",
                )
            }
        }
    }

    /** **GUARD: and the rendered Notes screen is the same text and the same pixels for both roles.** */
    @Test
    fun theNotesScreenRendersTheSameForBothRoles() {
        for (housed in listOf(false, true)) {
            for (seed in 0..8) {
                val resident = render(state(PanelRole.Resident, housed, seed))
                val insider = render(state(PanelRole.Insider, housed, seed))
                assertTrue(resident.first.isNotEmpty(), "Notes drew no words (housed=$housed, seed=$seed)")
                assertEquals(
                    resident.first, insider.first,
                    "Notes drew an Insider different words from a Resident (housed=$housed, seed=$seed)",
                )
                assertTrue(
                    resident.second.contentEquals(insider.second),
                    "Notes drew an Insider different pixels from a Resident (housed=$housed, seed=$seed)",
                )
            }
        }
    }

    /** The words on the glass, and the pixels, of [state]'s Notes screen. */
    private fun render(state: PanelState): Pair<List<String>, IntArray> {
        var texts: List<String> = emptyList()
        var pixels = IntArray(0)
        runDesktopComposeUiTest(width = 360, height = 760) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    DeviceCanvas(insets = PanelInsets()) { Screen(state) }
                }
            }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            val img = onRoot().captureToImage().toAwtImage()
            pixels = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        }
        return texts to pixels
    }
}
