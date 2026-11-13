package home.someoneshome.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The register screens are one layout, and so are the two removals** (D-202), as the mockup's
 * `s_setup.py` draws them.
 *
 * REGISTER MARKER and its two refusals keep the same header, the same viewfinder at the same
 * height and a fixed action area with the lit button at the foot: a refusal arriving between two
 * cards must not move the frame the host is pointing at a card. REMOVE THE TERMINAL and REMOVE THE
 * MEETING CARD differ only in their words. Read off the rendered screens, so a screen that grew its
 * own spacing fails here rather than in a side-by-side nobody runs.
 */
@OptIn(ExperimentalTestApi::class)
class RegisterFrameParityTest {

    private fun <T> on(screen: ScreenId, read: DesktopComposeUiTest.() -> T): T {
        var out: T? = null
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = screen)) } }
            out = read()
        }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    private class Frame(val viewfinder: DpRect, val lit: DpRect)

    private fun frame(screen: ScreenId, lit: String): Frame = on(screen) {
        Frame(
            viewfinder = onNodeWithTag(REGISTER_VIEWFINDER).getBoundsInRoot(),
            lit = onNodeWithText(lit, substring = true).getBoundsInRoot(),
        )
    }

    @Test
    fun theScanAndItsTwoRefusalsShareOneFrame() {
        val scan = frame(ScreenId.ScanMarker, "DONE SCANNING")
        for ((screen, lit) in listOf(ScreenId.TermTaken to "KEEP IT IN", ScreenId.MeetTaken to "KEEP IT IN")) {
            val refusal = frame(screen, lit)
            assertEquals(scan.viewfinder, refusal.viewfinder, "$screen moves the viewfinder the host is looking at")
            assertEquals(scan.lit.bottom, refusal.lit.bottom, "$screen's lit button is not at the scan's foot")
        }
    }

    @Test
    fun theTwoRemovalsShareOneLayout() {
        fun drawn(screen: ScreenId, at: String) = on(screen) {
            listOf(
                onNodeWithText(at).getBoundsInRoot().top,
                onNodeWithText("REMOVE IT").getBoundsInRoot().top,
                onNodeWithText("KEEP IT").getBoundsInRoot().top,
            )
        }
        assertEquals(
            drawn(ScreenId.TermRemove, "IN HALL"), drawn(ScreenId.MeetRemove, "IN LIVING"),
            "the two removals place their token row or their two buttons differently",
        )
    }
}
