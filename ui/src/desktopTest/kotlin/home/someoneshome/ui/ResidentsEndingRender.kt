package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **D-323 C, rendered: the Residents' ending is the name tiles alone** (the owner's 2026-11-22
 * ruling). No THE RESIDENTS label over them and no `4 OF 6` beside it; no new copy. Drawn on a
 * housed phone with the house's five Residents and on the port's fixture, at 600 by 1300, to
 * `build/screens/d323-residents-*.png`.
 */
@OptIn(ExperimentalTestApi::class)
class ResidentsEndingRender {

    private val out = File("build/screens").apply { mkdirs() }

    private fun render(name: String, state: PanelState, check: ComposeUiTest.() -> Unit) {
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(600.dp, 1300.dp)) {
                        DeviceCanvas(insets = PanelInsets()) { Screen(state) }
                    }
                }
            }
            waitForIdle()
            check()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(this@ResidentsEndingRender.out, "$name.png"))
        }
    }

    private fun ComposeUiTest.drawn(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theResidentsEndingIsTheNameTilesAlone() {
        val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")
        val housed = PanelState(screen = ScreenId.WinResidents, houseAttached = true, residents = names)
        render("d323-residents-housed", housed) {
            for (n in names) assertTrue(drawn(n), "$n's tile is missing")
            assertTrue(!drawn("THE RESIDENTS"), "the label is still over the tiles")
            assertTrue(drawn("RESIDENTS WIN"))
        }
        render("d323-residents-port", PanelState(screen = ScreenId.WinResidents)) {
            for (n in PanelVals.ENDING_RESIDENTS) assertTrue(drawn(n), "the port's $n is missing")
            assertTrue(!drawn("THE RESIDENTS") && !drawn("4 OF 6"), "the port's label or count is still drawn")
        }
    }
}
