package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Where a phone lands when it leaves a lobby, and what it may do next** (E-PAY-6).
 *
 * [LobbyExitTest] holds what the lobby model does; this holds what the *flow* does about it, which
 * is a separate question with a separate way of being wrong. A model that dropped everything
 * correctly and left the phone on the lobby screen would pass every test in that file.
 *
 * The three roads out — a client leaving, a host closing, and a client being closed on — all end on
 * the join screen, and they all end there with **no trail**. That is the part worth a test: `go`
 * keeps the trail and `push` clears it, and a back arrow that walked into a lobby with no house
 * behind it would draw counts from a desk that is gone.
 */
class LobbyClosedFlowTest {

    private fun aHome() = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    /**
     * A phone that has walked in the way a real one does: named, seated, and standing in a lobby it
     * reached by tapping. `perms` is the sample because D-161's gate stands in front of every one of
     * these calls and would otherwise be the refusal every assertion below read.
     */
    private fun inALobby(): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome()))),
            perms = PermsModel.sample(),
        )
        model.lobby.nameResident("ROSE")
        model.lobby.look()
        model.attachToHome(model.lobby.nearby.first())
        assertEquals(ScreenId.Lobby, model.state.screen, "the fixture never reached a lobby")
        return model
    }

    @Test
    fun leavingTheLobbyLandsOnTheJoinScreen() {
        val model = inALobby()
        model.leaveLobby()
        assertEquals(ScreenId.Join, model.state.screen)
    }

    /**
     * **And there is nothing behind it.** The trail ran back through a lobby this phone is no longer
     * in; a back arrow that walked into it would be a screen counting a room the phone has left.
     */
    @Test
    fun leavingTheLobbyLeavesNoTrail() {
        val model = inALobby()
        model.leaveLobby()
        assertFalse(model.canGoBack, "a phone could step back into a lobby it walked out of")
    }

    @Test
    fun closingTheHouseLandsTheHostOnTheJoinScreenWithNoTrail() {
        val model = FlowModel(PanelState(screen = ScreenId.Lobby), lobby = LobbyModel.sample())
        assertTrue(model.lobby.hosting)

        model.terminateHouse()

        assertEquals(ScreenId.Join, model.state.screen)
        assertFalse(model.canGoBack)
        assertFalse(model.lobby.hosting)
    }

    /**
     * **The client's half, reached by a push and never by a control.**
     *
     * This is what the app root calls when `LobbyBody.HouseClosed` arrives — `newRound`'s sibling,
     * and the difference between them is the whole of why there are two bodies on the wire: one
     * walks back to the lobby for a second round, and this one has no lobby to walk to.
     */
    @Test
    fun aClosedHouseWalksAClientToTheJoinScreenWithTheReasonOnIt() {
        val model = inALobby()
        model.houseClosed()

        assertEquals(ScreenId.Join, model.state.screen)
        assertFalse(model.canGoBack)
        assertEquals(LobbyModel.HOUSE_CLOSED, model.lobby.refusal)
        assertNull(model.lobby.attached)
    }

    /**
     * **A refused join does not navigate**, which is the fall-through this unit closed.
     *
     * The join screen used to call `attachToHome` and then `go(Lobby)` on the next line,
     * unconditionally — so a phone that was refused a seat walked into a lobby it had not joined,
     * with the reason printed on the screen it had just left.
     */
    @Test
    fun aNamelessJoinStaysOnTheJoinScreen() {
        // A phone whose setup is done, because D-161's gate stands one step in front of the name
        // gate and would otherwise be the refusal this test reads. See SetupGateTest for the gate.
        //
        // And a FRESH lobby rather than `FlowModel`'s default, which is `LobbyModel.sample()` —
        // already named ELLIOT and already attached. A fixture built on it would be testing a phone
        // that cleared this gate before the test began.
        val model = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome()))),
            perms = PermsModel.sample(),
        )
        model.lobby.look()
        val home = model.lobby.nearby.first()

        model.attachToHome(home)

        assertEquals(ScreenId.Join, model.state.screen, "a refused join walked into a lobby")
        assertEquals(LobbyModel.NEEDS_A_NAME, model.lobby.refusal)
    }

    @Test
    fun aNamedJoinDoesNavigate() {
        val model = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome()))),
            perms = PermsModel.sample(),
        )
        model.lobby.nameResident("ROSE")
        model.lobby.look()

        model.attachToHome(model.lobby.nearby.first())

        assertEquals(ScreenId.Lobby, model.state.screen)
    }
}
