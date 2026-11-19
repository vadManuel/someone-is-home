package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The launch gate says the owner's words, exactly, and offers nothing to tap** (D-292).
 *
 * The strings are pinned against literals typed out here rather than against the constants they
 * are, so a change to either side is a red test and not a quiet agreement.
 */
@OptIn(ExperimentalTestApi::class)
class NoUwbScreenTest {

    @Test
    fun theWordsAreTheApprovedOnes() {
        assertEquals("THIS PHONE CAN'T PLAY", NO_UWB_HEADLINE)
        assertEquals("Someone's Home needs Ultra Wideband: iPhone 11 or later, not iPhone SE.", NO_UWB_BODY)
    }

    /** What the gate draws is those two lines and no other text. */
    @Test
    fun theGateDrawsTheTwoLinesAndNothingElse() = runDesktopComposeUiTest(width = 300, height = 650) {
        setContent { DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.NoUwb)) } }
        onNodeWithText(NO_UWB_HEADLINE).assertExists()
        onNodeWithText(NO_UWB_BODY).assertExists()
        val drawn = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
            .filter { it.isNotBlank() }
        assertEquals(
            listOf(NO_UWB_HEADLINE, NO_UWB_BODY),
            drawn.filterNot { STATUS_CLOCK.matches(it) },
            "the gate draws words the owner did not approve",
        )
    }

    /** No way forward: nothing to click, type into or scroll. */
    @Test
    fun theGateHasNothingToTap() = runDesktopComposeUiTest(width = 300, height = 650) {
        setContent { DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.NoUwb)) } }
        onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
        onAllNodes(hasSetTextAction(), useUnmergedTree = true).assertCountEquals(0)
        onAllNodes(hasScrollAction(), useUnmergedTree = true).assertCountEquals(0)
    }

    /** A viewer, not an assertion: the gate at a phone's aspect, in `build/screens/no-uwb.png`. */
    @Test
    fun renderTheGate() = runDesktopComposeUiTest(width = 600, height = 1300) {
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.size(600.dp, 1300.dp)) {
                    DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.NoUwb)) }
                }
            }
        }
        val out = File("build/screens").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "no-uwb.png"))
    }

    private companion object {
        /** The status bar's clock, which the device frame draws over every screen, this one included. */
        val STATUS_CLOCK = Regex("""\d{1,2}:\d{2}""")
    }
}
