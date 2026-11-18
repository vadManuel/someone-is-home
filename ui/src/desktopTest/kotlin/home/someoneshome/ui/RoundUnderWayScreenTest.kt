package home.someoneshome.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.SavedHome
import home.someoneshome.model.protocol.LobbyBody
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The lobby of a phone seated while a round it is not in runs** (D-273).
 *
 * The house says so on the standing (`waitingForNextRound`) and counts the round: four seats, four
 * names, none of them this phone's. The screen says the round's count as the round's, the one fact
 * and the one way out, and nothing it cannot act on: no settings, no line row, no JOINED.
 */
@OptIn(ExperimentalTestApi::class)
class RoundUnderWayScreenTest {

    /** A house that answers every join with [standing], and nothing else. */
    private class Answering(private val standing: LobbyBody.Standing) : LobbyLink {
        override fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) = Unit
        override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) = onStanding(standing)
        override fun handOver(line: String) = Unit
        override fun setInsiders(chosen: Int?) = Unit
        override fun setVoteWindow(seconds: Int) = Unit
        override fun roundEnded() = Unit
        override fun leave() = Unit
        override fun terminate() = Unit
    }

    private val round = listOf("PRIYA", "MARCUS", "DANI", "ROSE")

    private fun lobbyFor(waiting: Boolean, over: Boolean = false): FlowModel {
        val lobby = LobbyModel(
            MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))),
            Answering(
                LobbyBody.Standing(4, 4, round, insiders = null, round = 2, waitingForNextRound = waiting, roundOver = over),
            ),
        )
        lobby.look()
        lobby.nameResident("ELLIOT")
        lobby.attachTo(lobby.nearby.first())
        return FlowModel(PanelState(screen = ScreenId.Lobby), lobby = lobby, perms = PermsModel.sample())
    }

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
            }
        }
    }

    private fun DesktopComposeUiTest.count(text: String) =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    @Test
    fun aPhoneOutsideTheRunningRoundIsShownTheRoundAndTheWayOut() = runDesktopComposeUiTest(width = 300, height = 650) {
        show(lobbyFor(waiting = true))
        onNodeWithText("4 IN THIS ROUND").assertExists("the round's count is not on the waiting phone")
        onNodeWithText("ROUND IN PROGRESS").assertExists()
        onNodeWithText("YOU JOIN THE NEXT ONE").assertExists()
        onNodeWithText("LEAVE THIS HOUSE").assertExists("the waiting phone has no way out")
        for (name in round) onNodeWithText(name).assertExists("the round's $name is not listed")
        assertEquals(0, count("JOINED"), "the waiting phone still says JOINED")
        assertEquals(0, count("YOUR ONE LINE"), "the waiting phone is asked for a line the next NEW ROUND deletes")
        assertEquals(0, count("INSIDERS"), "the waiting phone draws the lobby's settings")
    }

    /**
     * **The round it waits on has ended, and it no longer says it is in progress** (D-274). The
     * ending is not NEW ROUND, so it still waits — on the host, in the lobby's own words.
     */
    @Test
    fun aPhoneWaitingOnAnEndedRoundIsNotToldItIsInProgress() = runDesktopComposeUiTest(width = 300, height = 650) {
        show(lobbyFor(waiting = true, over = true))
        assertEquals(0, count("ROUND IN PROGRESS"), "the waiting phone says a round is in progress over its ending")
        onNodeWithText("WAITING FOR THE HOST").assertExists("the waiting phone does not say what it waits on")
        onNodeWithText("YOU JOIN THE NEXT ONE").assertExists()
        onNodeWithText("LEAVE THIS HOUSE").assertExists("the waiting phone has no way out")
        assertEquals(0, count("YOUR ONE LINE"), "the waiting phone is asked for a line the next NEW ROUND deletes")
    }

    /** The same standing without the flag is the ordinary lobby, so the flag alone draws the above. */
    @Test
    fun aPhoneInTheRoundIsShownTheOrdinaryLobby() = runDesktopComposeUiTest(width = 300, height = 650) {
        show(lobbyFor(waiting = false))
        onNodeWithText("4 JOINED").assertExists()
        onNodeWithText("YOUR ONE LINE").assertExists()
        assertEquals(0, count("ROUND IN PROGRESS"))
    }
}
