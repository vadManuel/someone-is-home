package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **The caught scan's room, count and light mark stay on the panel, whatever the room is called**
 * (D-256, H10).
 *
 * `<ROOM> . SUBROUTINE n OF 8` with the mark beside it was one line, which fitted the design's
 * GARAGE and nothing a host actually types: on the Simulators SCULLERY pushed the mark off the
 * panel's right edge and FRONT ROOM lost it altogether. Rendered on a phone-shaped canvas with the
 * longest room names a host is likely to type, at the sizes the screen draws them.
 */
@OptIn(ExperimentalTestApi::class)
class CaughtRowTest {

    @Test
    fun theCaughtRowFitsTheRoomItNames() {
        for (room in listOf("SCULLERY", "FRONT ROOM", "MASTER BEDROOM", "DOWNSTAIRS BATHROOM")) {
            val current = PanelVals.CURRENT.copy(room = room, index = 10, total = 10)
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(PanelState(screen = ScreenId.ScanCaught, houseAttached = true, current = current))
                    }
                }
                val panel = onRoot().getBoundsInRoot()
                val mark = onNodeWithTag(markTag(current.light), useUnmergedTree = true).getBoundsInRoot()
                assertTrue(mark.right <= panel.right && mark.left >= panel.left, "$room pushed the light mark off the panel: $mark in $panel")
                assertTrue(mark.right - mark.left > 0.dp, "$room squeezed the light mark to nothing: $mark")
                val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes().flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }
                assertTrue(room in texts, "the room is not drawn whole: $texts")
                assertTrue("SUBROUTINE 10 OF 10" in texts, "the count is not drawn whole: $texts")
            }
        }
    }
}
