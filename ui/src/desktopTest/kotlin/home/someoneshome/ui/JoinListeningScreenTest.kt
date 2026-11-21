package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **JOIN A HOME's three captions on the glass, on the screen's own clock** (D-309), and a viewer:
 * each state is written to `build/screens/join-*.png` at the mockup's 720×1520 with its camera band.
 */
@OptIn(ExperimentalTestApi::class)
class JoinListeningScreenTest {

    private val out = File("build/screens").apply { mkdirs() }
    private val bungalow = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    private fun DesktopComposeUiTest.join(finder: ScriptedHomeFinder, settings: () -> Unit = {}) {
        val lobby = LobbyModel(finder = finder, settings = settings)
        lobby.nameResident("ELLIOT")
        val model = FlowModel(PanelState(screen = ScreenId.Join), lobby = lobby, perms = PermsModel.sample())
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                DeviceCanvas(insets = PanelInsets(top = 36.dp)) { FlowHost(model) }
            }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }

    private fun DesktopComposeUiTest.shoot(name: String) {
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    private fun DesktopComposeUiTest.captions(listening: Int, noHome: Int, off: Int, settings: Int) {
        onAllNodesWithText(Listening.Looking.caption).assertCountEquals(listening)
        onAllNodesWithText(Listening.NothingFound.caption).assertCountEquals(noHome)
        onAllNodesWithText(Listening.Off.caption).assertCountEquals(off)
        onAllNodesWithText("OPEN SETTINGS").assertCountEquals(settings)
    }

    /** LISTENING, then NO HOME after ten seconds, then a home that answers later is listed. */
    @Test
    fun listeningThenNoHomeThenAHomeAnswers() = runDesktopComposeUiTest(width = 720, height = 1520) {
        val finder = ScriptedHomeFinder()
        join(finder)
        captions(listening = 1, noHome = 0, off = 0, settings = 0)
        shoot("join-listening")

        mainClock.advanceTimeBy(LobbyModel.NO_HOME_AFTER_MILLIS - 1_000)
        waitForIdle()
        captions(listening = 1, noHome = 0, off = 0, settings = 0)
        mainClock.advanceTimeBy(1_100)
        waitForIdle()
        captions(listening = 0, noHome = 1, off = 0, settings = 0)
        shoot("join-no-home")

        finder.answers(bungalow)
        mainClock.advanceTimeByFrame()
        waitForIdle()
        captions(listening = 0, noHome = 0, off = 0, settings = 0)
        onAllNodesWithText(bungalow.name).assertCountEquals(1)
        shoot("join-home-listed")
    }

    /** A refused search: LOCAL NETWORK IS OFF with OPEN SETTINGS under it, which opens Settings. */
    @Test
    fun aRefusedSearchSaysOffAndOffersSettings() = runDesktopComposeUiTest(width = 720, height = 1520) {
        val finder = ScriptedHomeFinder()
        var opened = 0
        join(finder) { opened++ }
        finder.refuses()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        captions(listening = 0, noHome = 0, off = 1, settings = 1)
        shoot("join-off")

        // The look's own clock runs out and OFF stays: this look was refused.
        mainClock.advanceTimeBy(LobbyModel.NO_HOME_AFTER_MILLIS + 100)
        waitForIdle()
        captions(listening = 0, noHome = 0, off = 1, settings = 1)

        onNodeWithText("OPEN SETTINGS").performClick()
        assertEquals(1, opened, "OPEN SETTINGS under LOCAL NETWORK IS OFF did not open Settings")
    }

    /** Back from Settings with Local Network on: the look again finds the home and OFF is gone. */
    @Test
    fun offClearsWhenTheLookAgainFindsAHome() = runDesktopComposeUiTest(width = 720, height = 1520) {
        val finder = ScriptedHomeFinder()
        var lobby: LobbyModel? = null
        val model = LobbyModel(finder = finder).also { lobby = it }
        model.nameResident("ELLIOT")
        val flow = FlowModel(PanelState(screen = ScreenId.Join), lobby = model, perms = PermsModel.sample())
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(flow) } }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        waitForIdle()
        finder.refuses()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        captions(listening = 0, noHome = 0, off = 1, settings = 1)

        lobby!!.lookAgain()
        finder.answers(bungalow)
        mainClock.advanceTimeByFrame()
        waitForIdle()
        captions(listening = 0, noHome = 0, off = 0, settings = 0)
        onAllNodesWithText(bungalow.name).assertCountEquals(1)
    }
}
