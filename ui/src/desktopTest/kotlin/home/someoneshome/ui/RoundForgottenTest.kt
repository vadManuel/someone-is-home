package home.someoneshome.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.OrderLine
import home.someoneshome.model.WinRoute
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Every way out of a round leaves the panel holding none of it** (D-259), and **the disconnect
 * screen has one** (D-260).
 *
 * The 2026-11-17 hunt's second round drew the first round's OUT REVOKED, METER 96% and work order on
 * its PERIMETER ARMED: `newRound` walked the screen and copied every other field across. The four
 * exits that end a round on this phone are NEW ROUND, the host closing the house, this phone
 * leaving, and the host closing it from its own phone; each is checked against a panel a round has
 * filled. The loopback version, five phones and a real ending, is `RoundBoundaryTest` in `app`.
 */
@OptIn(ExperimentalTestApi::class)
class RoundForgottenTest {

    private val clock = WallTime(hour = 21, minute = 7, weekday = 6, day = 16, month = 8)

    /** A panel a round has written into: out, Insider, dealt, metered, timed, met and ended. */
    private fun played(screen: ScreenId) = PanelState(
        screen = screen,
        houseAttached = true,
        role = PanelRole.Insider,
        revoke = RevokeState.Armed,
        outBy = OutBy.Revoked,
        verdict = SubroutineVerdict.Rejected,
        secondsLeft = 9,
        egressType = "BEACON",
        egressNodes = listOf("KITCHEN", "HALL"),
        egressRunning = true,
        egressHeld = true,
        beatHeld = true,
        revealed = listOf(RevealedInsider("ALBA", "a line")),
        signOff = "GOODNIGHT",
        order = listOf(OrderLine.Blocked(0)),
        tally = MeetingTally("BRUNO", 4, 4),
        caller = "CYRUS",
        voteWindowSeconds = 45,
        integrityPercent = 96,
        seats = 5,
        roster = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO"),
        notice = emptyList(),
        isolateTargets = emptyList(),
        staticJam = StaticJam(10, 7L),
        revokeClock = PublicCooldown.EgressFired,
        accessClock = PublicCooldown.EgressFired,
        egressClock = PublicCooldown.EgressFired,
        carrying = Carried("DISK 01", "RACK"),
        wallTime = clock,
        openedAt = "21:00",
        houseTexts = listOf(HouseText("21:03", "STAY QUIET")),
        endedAt = "21:30",
        endedBy = WinRoute.Parity,
        heldOver = HeldLine("a line"),
        egressesContained = 1,
        residents = listOf("BRUNO"),
        markersOn = true,
        inboxSeed = 5,
        noteSeed = 2,
    )

    private fun flowOn(screen: ScreenId, hosting: Boolean = false): Pair<FlowModel, MemoryLobbyLink> {
        val link = MemoryLobbyLink(joined = 5, linesIn = 5)
        val lobby = LobbyModel(MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))), link, hosting = hosting)
        lobby.look()
        lobby.nameResident("ELLIOT")
        lobby.attachTo(lobby.nearby.first())
        val flow = FlowModel(played(screen), lobby = lobby, perms = PermsModel.sample(), wallClock = { clock })
        // Attached as the app attaches a phone, which takes the design's own texts off the lock
        // screen (D-243), so what is left there is the house's.
        flow.houseAttached(true)
        // A house text on the lock screen, as `MessageDelivered` leaves one.
        flow.houseSaid(RoundMessage.RosterIssued(listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")))
        flow.notifications.houseSent(Notifications.houseText(flow.state.houseTexts.single()))
        flow.meeting.checkIn()
        return flow to link
    }

    /** What a phone that has never played holds, on [screen], with the evening's fields kept. */
    private fun fresh(screen: ScreenId) = PanelState(
        screen = screen, houseAttached = true, wallTime = clock, markersOn = true, inboxSeed = 5, noteSeed = 2,
    )

    private fun assertForgotten(flow: FlowModel, screen: ScreenId, how: String) {
        assertEquals(fresh(screen), flow.state, "$how carried the round across")
        assertEquals(MeetingCounts.UNSAID, flow.meeting.counts, "$how kept the meeting's counts")
        assertEquals(false, flow.meeting.checkedIn, "$how kept the check-in")
        assertEquals(emptyList(), flow.meeting.names, "$how kept the round's roster on the ballot")
        assertNull(flow.subroutines.standingAt, "$how kept the card in hand")
        assertEquals(
            emptyList(), flow.notifications.standing.filter { it.kind == NotificationKind.Text },
            "$how left the round's house text under the clock",
        )
    }

    @Test
    fun newRoundForgetsTheRound() {
        val (flow, _) = flowOn(ScreenId.WinInsiders)
        flow.newRound()
        assertForgotten(flow, ScreenId.Lobby, "NEW ROUND")
        // The house's push answers it again on every phone, the host's included; nothing is left.
        flow.newRound()
        assertForgotten(flow, ScreenId.Lobby, "a second NEW ROUND")
        assertEquals("ELLIOT", flow.lobby.residentName, "NEW ROUND took this phone's name")
        assertEquals("THE BUNGALOW", flow.lobby.attached?.name, "NEW ROUND took this phone out of the home")
    }

    @Test
    fun theHouseClosingForgetsTheRound() {
        val (flow, _) = flowOn(ScreenId.Home)
        flow.houseClosed()
        assertForgotten(flow, ScreenId.Join, "the house closing")
    }

    @Test
    fun theHostClosingItsHouseForgetsTheRound() {
        val (flow, _) = flowOn(ScreenId.WinResidents, hosting = true)
        flow.terminateHouse()
        assertForgotten(flow, ScreenId.Join, "CLOSE THE HOUSE")
    }

    // ---- The disconnect screen's way out (D-260) -------------------------------------------------

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
            }
        }
        mainClock.autoAdvance = false
    }

    /**
     * **LEAVE THIS HOUSE, held for two seconds on RECONNECTING**: the join screen, the round
     * forgotten, the home let go. The seat token is the link's to keep and is kept there
     * (`HouseGoneTest`, where a host that comes back seats this phone in its own chair again).
     */
    @Test
    fun aGuestLeavesTheDisconnectScreenForTheJoinScreen() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (flow, _) = flowOn(ScreenId.Disconnect)
        show(flow)
        onNodeWithText("RECONNECTING").assertExists()
        onNodeWithText("LEAVE THIS HOUSE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(HOLD_MILLIS + 200L)
        assertForgotten(flow, ScreenId.Join, "LEAVE THIS HOUSE on the disconnect screen")
        assertNull(flow.lobby.attached, "the phone left and is still attached to the home")
    }

    /** And most of a hold leaves nothing: a thumb brushing it in the dark costs no seat. */
    @Test
    fun mostOfAHoldLeavesNothing() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (flow, _) = flowOn(ScreenId.Disconnect)
        show(flow)
        onNodeWithText("LEAVE THIS HOUSE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(HOLD_MILLIS - 100L)
        onNodeWithText("LEAVE THIS HOUSE").performTouchInput { up() }
        mainClock.advanceTimeBy(HOLD_MILLIS + 200L)
        assertEquals(ScreenId.Disconnect, flow.state.screen, "letting go early left the house")
        assertEquals("THE BUNGALOW", flow.lobby.attached?.name, "letting go early let go of the home")
    }
}
