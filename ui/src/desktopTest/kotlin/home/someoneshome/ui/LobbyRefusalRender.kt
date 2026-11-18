package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.SavedHome
import home.someoneshome.model.protocol.LobbyBody
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * **A viewer, not an assertion: the join screen when the house turned this phone away** (D-287).
 * Writes `build/screens/refusal-*.png` at a phone's 600×1300, so the refusal slot can be looked at
 * with the field, the network rows and HOST A HOME INSTEAD around it.
 */
class LobbyRefusalRender {

    private val out = File("build/screens").apply { mkdirs() }

    /** A house that seats this phone into a six-seat lobby, and nothing else. */
    private object Seating : LobbyLink {
        override fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) = Unit
        override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) =
            onStanding(LobbyBody.Standing(6, 4, listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS", name)))
        override fun handOver(line: String) = Unit
        override fun setInsiders(chosen: Int?) = Unit
        override fun setVoteWindow(seconds: Int) = Unit
        override fun roundEnded() = Unit
        override fun leave() = Unit
        override fun terminate() = Unit
    }

    private fun attached(): FlowModel {
        val lobby = LobbyModel(
            MemoryHomeFinder(
                listOf(
                    NearbyHome("THE BUNGALOW", "192.168.1.24", 47747),
                    NearbyHome("FLAT 6", "192.168.1.9", 47747),
                ),
            ),
            Seating,
        )
        lobby.look()
        lobby.nameResident("ELLIOT")
        lobby.attachTo(lobby.nearby.first())
        return FlowModel(PanelState(screen = ScreenId.Lobby), lobby = lobby, perms = PermsModel.sample())
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    private fun render(name: String, model: FlowModel) = runDesktopComposeUiTest(width = 600, height = 1300) {
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.size(600.dp, 1300.dp)) {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
                    }
                }
            }
        }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    @Test
    fun renderTheJoinScreenAfterARefusal() {
        // Refused at the door while a round runs (D-287).
        val locked = attached()
        locked.roundInProgress()
        render("refusal-round-in-progress", locked)
    }
}
