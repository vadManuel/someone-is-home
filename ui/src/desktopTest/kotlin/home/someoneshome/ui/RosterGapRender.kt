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
import home.someoneshome.model.Haptic
import home.someoneshome.model.SavedHome
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundMessage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * **A viewer, not an assertion: the hunt's second round, armed without seat 2** (D-274).
 * Writes `build/screens/gap-*.png` — the living ballot, the couch's ballot, the Surge picker and the
 * couch's ending — so the rows can be looked at against the hunt's sheets.
 */
class RosterGapRender {

    private val out = File("build/screens").apply { mkdirs() }
    private val roster = listOf("KESTREL", "OSPREY", null, "HERON", "FINCH", "PLOVER")

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    private fun render(name: String, model: FlowModel) = runDesktopComposeUiTest(width = 600, height = 1300) {
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.size(600.dp, 1300.dp)) {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(model.state, model.actions(), model.editor, model.homes, model.lobby, model.meeting)
                    }
                }
            }
        }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    @Test
    fun renderTheRoundAroundAMissingSeat() {
        val living = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true), meeting = MeetingModel())
        living.houseSaid(RoundMessage.RosterIssued(roster))
        living.push(ScreenId.Vote)
        render("gap-vote", living)

        val picker = FlowModel(PanelState(screen = ScreenId.Home, role = PanelRole.Insider, houseAttached = true))
        picker.houseSaid(RoundMessage.RosterIssued(roster))
        picker.push(ScreenId.SurgeTargets)
        render("gap-surge", picker)

        val couch = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true), meeting = MeetingModel())
        couch.houseSaid(RoundMessage.RosterIssued(roster))
        couch.houseSaid(RoundMessage.RevokeLanded.Buzzing)
        for (voter in listOf(1, 3, 4, 5)) {
            couch.houseSaid(RoundMessage.VoteSelectionShown(voter = Seat(voter), selection = Seat(1)))
        }
        couch.push(ScreenId.GhostMeeting)
        render("gap-couch-vote", couch)

        // The relaunched guest, waiting on a round that has reached its ending (D-274).
        val waiting = LobbyModel(
            MemoryHomeFinder(listOf(NearbyHome("HUNT HOME", "192.168.1.24", 47747))),
            object : LobbyLink {
                override fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) = Unit
                override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) =
                    onStanding(
                        LobbyBody.Standing(
                            5, 5, roster.filterNotNull(), round = 2, waitingForNextRound = true, roundOver = true,
                        ),
                    )
                override fun handOver(line: String) = Unit
                override fun setInsiders(chosen: Int?) = Unit
                override fun setVoteWindow(seconds: Int) = Unit
                override fun setDiscussion(seconds: Int) = Unit
                override fun setEgressTimer(seconds: Int) = Unit
                override fun setRevokeCooldown(seconds: Int) = Unit
                override fun roundEnded() = Unit
                override fun leave() = Unit
                override fun terminate() = Unit
            },
        ).apply { look(); nameResident("WREN"); attachTo(nearby.first()) }
        render("gap-waiting-over-ending", FlowModel(PanelState(screen = ScreenId.Lobby), lobby = waiting))

        couch.houseSaid(RoundMessage.SystemIntegrityLive(100))
        couch.houseSaid(RoundMessage.RoundEnded(Winner.Insiders, WinRoute.EgressUncontained, Haptic.Short, integrity = 100))
        render("gap-couch-ending", couch)
    }
}
