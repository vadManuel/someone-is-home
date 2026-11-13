package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **A banner lies over page 1, and the quiet one covers the meter whole** (D-202, D-118).
 *
 * The page no longer moves for a banner. That is only safe while no banner slices the System
 * Integrity meter on a page that has not dimmed, because half a meter reads as a rendering fault
 * rather than as something behind. A heavy banner arrives over a dimmed page; the quiet one does
 * not, so it is drawn tall enough to cover the widget to its last line.
 */
@OptIn(ExperimentalTestApi::class)
class BannerOverPageTest {

    @Test
    fun `the quiet banner covers the System Integrity widget to its last line`() {
        for (role in PanelRole.entries) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.Quiet, role = role)) }
                }
                val banner = onNodeWithTag(NOTIFICATION_BANNER).getBoundsInRoot()
                val lastLine = onNodeWithText("Frozen until next house meeting").getBoundsInRoot()
                assertTrue(
                    banner.bottom >= lastLine.bottom && banner.top <= lastLine.top,
                    "$role: the quiet banner ends at ${banner.bottom} and the meter's last line at " +
                        "${lastLine.bottom}: the meter is sliced on a page that did not dim",
                )
            }
        }
    }

    @Test
    fun `the page does not move when a banner arrives`() {
        fun lineTop(screen: ScreenId): Float {
            var top = 0f
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent { DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = screen)) } }
                top = onNodeWithText("Frozen until next house meeting").getBoundsInRoot().top.value
            }
            return top
        }
        val still = lineTop(ScreenId.Home)
        for (screen in listOf(ScreenId.Notify, ScreenId.Quiet)) {
            val moved = lineTop(screen)
            assertTrue(moved == still, "$screen pushed page 1 from $still to $moved")
        }
    }
}
