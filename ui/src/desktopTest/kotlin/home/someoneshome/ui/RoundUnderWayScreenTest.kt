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
        override fun setDiscussion(seconds: Int) = Unit
        override fun setEgressTimer(seconds: Int) = Unit
        override fun setRevokeCooldown(seconds: Int) = Unit
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

    /**
     * **GUARD (D-287): refused at the door while a round runs, the phone stands on JOIN with the
     * waiting screen's own two lines in the refusal slot**, holds no house and no lobby, and is not on
     * a waiting lobby. Typing a name clears the line, as every refusal on that screen is cleared.
     */
    @Test
    fun aPhoneRefusedAtTheDoorStandsOnJoinSayingTheRoundIsInProgress() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = lobbyFor(waiting = false)
        model.roundInProgress()
        assertEquals(ScreenId.Join, model.state.screen, "the refused phone was not walked to the join screen")
        assertEquals(null, model.lobby.attached, "the refused phone still holds the house")
        assertEquals(0, model.lobby.standing.joined, "the refused phone still holds a lobby")
        show(model)
        onNodeWithText("ROUND IN PROGRESS\nYOU JOIN THE NEXT ONE").assertExists("the join screen does not say why the phone is on it")
        onNodeWithText("NETWORKS NEARBY").assertExists()
        assertEquals(0, count("LEAVE THIS HOUSE"), "the refused phone is drawn a lobby's way out")
        model.lobby.nameResident("ELLIOTT")
        waitForIdle()
        assertEquals(0, count("ROUND IN PROGRESS"), "typing did not clear the refusal")
    }

    /**
     * **GUARD (D-288): a refused name walks the phone to JOIN with NAME TAKEN, one line, under the
     * field that still holds it**; typing another clears it.
     */
    @Test
    fun aRefusedNameStandsOnJoinSayingNameTaken() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = lobbyFor(waiting = false)
        model.nameTaken()
        assertEquals(ScreenId.Join, model.state.screen, "the refused phone was not walked to the join screen")
        assertEquals(null, model.lobby.attached, "the refused phone still holds the house")
        show(model)
        onNodeWithText("NAME TAKEN").assertExists("the join screen does not say the name is taken")
        onNodeWithText("ELLIOT").assertExists("the field no longer holds the name to be changed")
        model.lobby.nameResident("ELLIOTT")
        waitForIdle()
        assertEquals(0, count("NAME TAKEN"), "typing another did not clear NAME TAKEN")
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
