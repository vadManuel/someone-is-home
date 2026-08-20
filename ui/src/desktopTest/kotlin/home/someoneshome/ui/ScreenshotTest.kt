package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders every screen to a PNG so they can be looked at.
 *
 * **This is the instrument that makes the rest of the port honest.** Layout in Compose fails at
 * runtime, silently: a `weight` inside a wrap-content parent, an `aspectRatio` fighting a fill, a
 * plan grid measuring to zero. None of it is visible to the compiler, and a screen that renders
 * as an empty black rectangle looks exactly like a screen that is meant to be sparse.
 *
 * Both roles are rendered for every screen, side by side, because parity is the property most
 * easily broken and least easily noticed — and it only exists in comparison.
 *
 * Output lands in `ui/build/screens/`. Not an assertion and not a regression test: there is no
 * golden image to diff against, because there is no prior correct version. It is a viewer.
 */
class ScreenshotTest {

    private val out = File("build/screens").apply { mkdirs() }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderEveryScreen() {
        val w = 300
        val h = 400
        var written = 0

        for (id in ScreenId.entries) {
            // The out-screens are meaningless without a cause: the status bar names what
            // happened to you, and there is no sensible default. Ghost2/Ghost3/GhostMeeting are
            // reached by both routes -- rendered here as revoked, which is the route the design
            // fixture cannot express.
            val outBy = when (id) {
                ScreenId.Restrained -> OutBy.Restrained
                ScreenId.Revoked, ScreenId.Ghost2, ScreenId.Ghost3, ScreenId.GhostMeeting ->
                    OutBy.Revoked
                else -> null
            }
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = w * 2, height = h * 2) {
                    setContent {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            Box(Modifier.size((w * 2).dp, (h * 2).dp)) {
                                DeviceCanvas {
                                    Screen(PanelState(screen = id, role = role, outBy = outBy))
                                }
                            }
                        }
                    }
                    val img = onRoot().captureToImage().toAwtImage()
                    val name = "${id.name.lowercase()}-${role.name.lowercase()}.png"
                    ImageIO.write(img, "png", File(out, name))
                    written++
                }
            }
        }
        println("wrote $written screenshots to ${out.absolutePath}")
    }
}
