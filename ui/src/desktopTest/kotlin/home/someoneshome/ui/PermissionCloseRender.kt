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
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.RoundMessage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * **A viewer, not an assertion: the notice with the owner's warning, the notice without it, and
 * THE HOUSE IS CLOSED** (D-310). Writes `build/screens/permission-*.png` at a phone's 600×1300.
 */
class PermissionCloseRender {

    private val out = File("build/screens").apply { mkdirs() }

    /** A house that seats this phone into a four-seat lobby, and nothing else. */
    private object Seating : LobbyLink {
        override fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) = Unit
        override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) =
            onStanding(LobbyBody.Standing(4, 4, listOf("ROSE", "MIA", "JO", name)))
        override fun handOver(line: String) = Unit
        override fun setInsiders(chosen: Int?) = Unit
        override fun setVoteWindow(seconds: Int) = Unit
        override fun roundEnded() = Unit
        override fun leave() = Unit
        override fun terminate() = Unit
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

    /** A housed phone at the notices, the house having named [seats], warned or not. */
    private fun notice(seats: List<Seat>, warned: Boolean): FlowModel {
        val model = FlowModel(PanelState(houseAttached = true))
        model.houseSaid(RoundMessage.RosterIssued(listOf("ROSE", "MIA", "JO", "DANI")))
        model.houseSaid(RoundMessage.NoticeIssued(seats, permissionSwitchedOff = warned))
        model.push(ScreenId.Notice)
        return model
    }

    @Test
    fun renderTheNoticeAndTheClose() {
        render("permission-notice-warned-one", notice(listOf(Seat(1)), warned = true))
        render("permission-notice-warned-two", notice(listOf(Seat(1), Seat(2)), warned = true))
        render("permission-notice-left-the-app", notice(listOf(Seat(1)), warned = false))

        // A guest in a round, closed: JOIN A HOME with the two lines.
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
        lobby.nameResident("DANI")
        lobby.attachTo(lobby.nearby.first())
        val guest = FlowModel(PanelState(screen = ScreenId.Home), lobby = lobby, perms = PermsModel.sample())
        guest.houseSaid(RoundMessage.HouseClosedOnPermission)
        render("permission-closed-guest-join", guest)

        // The host, closed: its own lobby, still hosting, with the two lines.
        val host = FlowModel(PanelState(screen = ScreenId.Home), lobby = LobbyModel.sample(), perms = PermsModel.sample())
        host.houseSaid(RoundMessage.HouseClosedOnPermission)
        render("permission-closed-host-lobby", host)
    }
}
