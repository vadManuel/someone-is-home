package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A viewer, not an assertion: the PERMISSIONS walk with its NEARBY DEVICES row** (D-303), in the
 * row's states, and the screen CONTINUE reaches now the NameDrop step is gone. Writes
 * `build/screens/perms-*.png` at the mockup's 720×1520 with its camera band, so the row can be laid
 * beside BLUETOOTH and the rest.
 */
class PermsWalkRender {

    private val out = File("build/screens").apply { mkdirs() }

    private fun perms(nearby: Grant?, others: Grant? = Grant.Granted): PermsModel {
        val os = DevicePermission.entries.associateWith { others }.toMutableMap()
        os[DevicePermission.NearbyDevices] = nearby
        return PermsModel(read = { os[it] ?: Grant.Unasked }).also { it.refresh() }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    private fun render(name: String, model: FlowModel, walk: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null) =
        runDesktopComposeUiTest(width = 720, height = 1520) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    DeviceCanvas(insets = PanelInsets(top = 36.dp)) { FlowHost(model) }
                }
            }
            walk?.invoke(this)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }

    private fun at(screen: ScreenId, perms: PermsModel) = FlowModel(PanelState(screen = screen), perms = perms)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renderThePermsWalk() {
        render("perms-fresh", at(ScreenId.Perms, perms(null, others = null)))
        render("perms-nearby-unasked", at(ScreenId.Perms, perms(Grant.Unasked)))
        render("perms-nearby-refused", at(ScreenId.Perms, perms(Grant.Refused)))
        render("perms-granted", at(ScreenId.Perms, perms(Grant.Granted)))
        // The tallest the screen gets: Local Network's caption, a refusal (OPEN SETTINGS) and every
        // other row still owed, so the gate's sentence names five.
        val tallest = DevicePermission.entries.associateWith { Grant.Unasked }.toMutableMap().apply {
            this[DevicePermission.LocalNetwork] = Grant.Asked
            this[DevicePermission.NearbyDevices] = Grant.Refused
        }
        render("perms-tallest", at(ScreenId.Perms, PermsModel(read = { tallest.getValue(it) }).also { it.refresh() }))
        // CONTINUE on a phone that has granted everything: straight to JOIN A HOME, no step between.
        val walked = at(ScreenId.Perms, perms(Grant.Granted))
        render("perms-continue", walked) { onNodeWithText("CONTINUE").performClick() }
        assertEquals(ScreenId.Join, walked.state.screen, "CONTINUE did not reach the join screen")
    }
}
