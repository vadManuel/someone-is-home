package home.someoneshome.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **The lamp's ground runs to the top edge, with the Island on bare light** (rule 5, D-204).
 *
 * The lantern and the scan are the screens whose ground *is* the light. They draw their own strip
 * under the safe area, and the amber behind it has to reach the top of the panel: a black band
 * beside the Island is light the core authored and the screen did not emit. The device-layout guard
 * cannot see it, because a band of black is as uniform as a band of amber.
 */
@OptIn(ExperimentalTestApi::class)
class LampEdgeTest {

    /** The same notch [DeviceLayoutTest] renders every screen against. */
    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

    @Test
    fun `the lamp screens are lit beside the Island`() {
        for (screen in listOf(ScreenId.Lock, ScreenId.LockNotify, ScreenId.Scan)) {
            var value = 0
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = insets) { Screen(PanelState(screen = screen)) }
                }
                val img = onRoot().captureToImage().toAwtImage()
                // Beside the Island, a quarter of the way in, two rows down: inside the safe area.
                val rgb = img.getRGB(img.width / 4, 2)
                value = maxOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            }
            assertTrue(value > 40, "$screen is black beside the Island (brightest channel $value)")
        }
    }
}
