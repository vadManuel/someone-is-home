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
 * THE HOUSE IS CLOSED** (D-310), the notice in every ordering and the lobby's check landing on
 * PERMISSIONS (D-311). Writes `build/screens/permission-*.png` at a phone's 600×1300.
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
                        Screen(model.state, model.actions(), model.editor, model.homes, model.lobby, perms = model.perms)
                    }
                }
            }
        }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    /** A housed phone at the notices, the house having named [left] as leavers and [switched] as switchers (D-311). */
    private fun notice(left: List<Seat>, switched: List<Seat>): FlowModel {
        val model = FlowModel(PanelState(houseAttached = true))
        model.houseSaid(RoundMessage.RosterIssued(listOf("ROSE", "MIA", "JO", "DANI", "SAM")))
        model.houseSaid(RoundMessage.NoticeIssued(left, switchedOff = switched))
        model.push(ScreenId.Notice)
        return model
    }

    @Test
    fun renderTheNoticeAndTheClose() {
        // The notice in every ordering (D-311): only switchers, one and two; only a leaver; both.
        render("permission-notice-switched-one", notice(emptyList(), listOf(Seat(1))))
        render("permission-notice-switched-two", notice(emptyList(), listOf(Seat(1), Seat(2))))
        render("permission-notice-left-the-app", notice(listOf(Seat(4)), emptyList()))
        render("permission-notice-both", notice(listOf(Seat(4)), listOf(Seat(1), Seat(2))))

        // The lobby's check (D-311): a guest whose Bluetooth radio is turned off is let go and lands
        // on PERMISSIONS with the row refused; the host is shown PERMISSIONS with its lobby kept.
        val radioOff: (home.someoneshome.model.DevicePermission) -> home.someoneshome.model.Grant = {
            if (it == home.someoneshome.model.DevicePermission.Bluetooth) home.someoneshome.model.Grant.Refused else home.someoneshome.model.Grant.Granted
        }
        val inLobby = LobbyModel(MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))), Seating)
        inLobby.look()
        inLobby.nameResident("DANI")
        inLobby.attachTo(inLobby.nearby.first())
        val kicked = FlowModel(PanelState(screen = ScreenId.Lobby), lobby = inLobby, perms = PermsModel(read = radioOff))
        kicked.permissionsChecked()
        render("permission-lobby-guest-kicked", kicked)
        val held = FlowModel(PanelState(screen = ScreenId.Lobby), lobby = LobbyModel.sample(), perms = PermsModel(read = radioOff))
        held.permissionsChecked()
        render("permission-lobby-host-held", held)

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
