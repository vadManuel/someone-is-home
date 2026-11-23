package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A lobby let-go asks the house first** (the owner's ruling, 2026-11-23, D-329: *"report this at
 * the next meeting instead of kicking out a player"*).
 *
 * The phone's half, on the real flow and lobby models, with the house's answer held by the fixture
 * ([MemoryLobbyLink.holdsLetGo]) so a test can stand in the moment between the ask and the answer.
 * The real sockets, the real desk and the real authority are `PermissionCloseLoopbackTest`'s.
 */
class LobbyLetGoTest {

    private fun aHome() = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    private val rows = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()

    private fun aGuest(link: MemoryLobbyLink): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link),
            perms = PermsModel(read = { rows.getValue(it) }),
        )
        model.lobby.nameResident("ROSE")
        model.lobby.look()
        model.attachToHome(model.lobby.nearby.first())
        assertEquals(ScreenId.Lobby, model.state.screen, "the fixture never reached a lobby")
        return model
    }

    private fun aHost(link: MemoryLobbyLink): FlowModel {
        val lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link, hosting = true)
        lobby.look()
        lobby.nameResident("ELLIOT")
        lobby.attachTo(lobby.nearby.first())
        return FlowModel(PanelState(screen = ScreenId.Lobby), lobby = lobby, perms = PermsModel(read = { rows.getValue(it) }))
    }

    private fun held() = MemoryLobbyLink(joined = 4, linesIn = 4).also { it.holdsLetGo = true }

    /**
     * **GUARD: the check asks, and the guest stays where it is until the house answers.** Something
     * off in the lobby sends one ask and moves nothing: the seat is held, the screen is the lobby, the
     * row is not yet rewritten. The house's RELEASED then walks it to PERMISSIONS as D-311 does.
     */
    @Test
    fun theCheckAsksAndMovesNothingUntilTheHouseAnswers() {
        val link = held()
        val guest = aGuest(link)
        rows[DevicePermission.Bluetooth] = Grant.Refused
        guest.permissionsChecked()
        assertEquals(1, link.letGoAsks, "the lobby's check did not ask the house to let the phone go")
        assertEquals(ScreenId.Lobby, guest.state.screen, "the phone left the lobby on its own word, before the house answered")
        assertNotNull(guest.lobby.attached, "the phone let its seat go before the house answered")

        link.answerLetGo(LetGoAnswer.Released)
        assertEquals(ScreenId.Perms, guest.state.screen, "RELEASED did not walk the guest to PERMISSIONS")
        assertNull(guest.lobby.attached, "RELEASED and the guest still holds the lobby")
        assertEquals(Grant.Refused, guest.perms.grant(DevicePermission.Bluetooth), "the row does not show as refused on PERMISSIONS")
    }

    /**
     * **GUARD: KEPT leaves the phone in its seat**, and the arming that follows walks it to ARMED.
     * The house armed the seat before this phone heard any push; the answer came after them.
     */
    @Test
    fun keptLeavesTheSeatAndTheArmingWalksItToArmed() {
        val link = held()
        val guest = aGuest(link)
        rows[DevicePermission.NearbyDevices] = Grant.Refused
        guest.permissionsChecked()
        // The house had armed: its pushes are ahead of its answer on this seat's queue.
        guest.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Jam), false, listOf(Destination(MarkerId("c0"), shape = "diamond", room = "STUDY")))),
            ),
        )
        guest.houseSaid(RoundMessage.OpeningMessage(Haptic.Long, 0L))
        link.answerLetGo(LetGoAnswer.Kept)
        assertEquals(ScreenId.Armed, guest.state.screen, "a kept seat was not walked to ARMED")
        assertNotNull(guest.lobby.attached, "a kept seat was let go")
        // KEPT before any push (a frame still in the air) also leaves the seat.
        val other = held()
        rows[DevicePermission.NearbyDevices] = Grant.Granted
        val early = aGuest(other)
        rows[DevicePermission.NearbyDevices] = Grant.Refused
        early.permissionsChecked()
        other.answerLetGo(LetGoAnswer.Kept)
        assertEquals(ScreenId.Lobby, early.state.screen, "KEPT moved the phone")
        assertNotNull(early.lobby.attached, "KEPT let the seat go")
    }

    /**
     * **GUARD: one ask at a time, asked again after [LET_GO_PATIENCE_MILLIS] by the next trigger,
     * and never a leave without an answer.** A house that does not answer leaves the phone where it
     * is however many triggers come; a late RELEASED still walks it.
     */
    @Test
    fun anUnansweredAskIsAskedAgainOnlyAfterThePatienceAndNeverLeaves() {
        val link = held()
        val guest = aGuest(link)
        rows[DevicePermission.Camera] = Grant.Refused
        guest.permissionsChecked()
        guest.permissionsChecked()
        guest.cameBackToTheForeground()
        assertEquals(1, link.letGoAsks, "a second trigger asked again while the first ask was waiting")
        repeat((LET_GO_PATIENCE_MILLIS / 1_000L).toInt() - 1) { guest.secondPassed() }
        guest.permissionsChecked()
        assertEquals(1, link.letGoAsks, "a trigger before the patience ran out asked again")
        guest.secondPassed()
        guest.permissionsChecked()
        assertEquals(2, link.letGoAsks, "the next trigger after the patience did not ask again")
        repeat(30) { guest.secondPassed(); guest.permissionsChecked() }
        assertEquals(ScreenId.Lobby, guest.state.screen, "the phone left without the house's answer")
        assertNotNull(guest.lobby.attached, "the phone let its seat go without the house's answer")

        link.answerLetGo(LetGoAnswer.Released)
        assertEquals(ScreenId.Perms, guest.state.screen, "a late RELEASED did not walk the guest")
    }

    /**
     * **GUARD (D-327's fast path stays): a phone that holds a push of a round asks nothing.** Its
     * switch-off is the round's from the first push, so the house is not asked at all.
     */
    @Test
    fun aPhoneThatHeardTheRoundAsksNothing() {
        val link = held()
        val guest = aGuest(link)
        guest.houseSaid(RoundMessage.LampSet(40))
        rows[DevicePermission.Motion] = Grant.Refused
        guest.permissionsChecked()
        guest.cameBackToTheForeground()
        assertEquals(0, link.letGoAsks, "a phone holding a round's push asked the lobby's question")
        assertEquals(ScreenId.Lobby, guest.state.screen)
    }

    /**
     * **GUARD: the host asks too, and follows D-311's host rule on RELEASED**: shown PERMISSIONS, its
     * house on the air, its lobby kept; its PROCEED goes back there. On KEPT it stays.
     */
    @Test
    fun theHostRequestsALetGoAndIsShownPermissionsOnReleased() {
        val link = held()
        val host = aHost(link)
        rows[DevicePermission.Bluetooth] = Grant.Refused
        host.permissionsChecked()
        assertEquals(1, link.letGoAsks, "the host's check did not ask its own house")
        assertEquals(ScreenId.Lobby, host.state.screen, "the host moved before its house answered")
        link.answerLetGo(LetGoAnswer.Released)
        assertEquals(ScreenId.Perms, host.state.screen, "RELEASED did not show the host PERMISSIONS")
        assertTrue(host.lobby.hosting, "the host's house went off the air")
        assertNotNull(host.lobby.attached, "the host left its own lobby")
        rows[DevicePermission.Bluetooth] = Grant.Granted
        host.permissionsDone()
        assertEquals(ScreenId.Lobby, host.state.screen, "the host's PROCEED did not go back to its lobby")

        val armedLink = held()
        val arming = aHost(armedLink)
        rows[DevicePermission.Camera] = Grant.Refused
        arming.permissionsChecked()
        armedLink.answerLetGo(LetGoAnswer.Kept)
        assertEquals(ScreenId.Lobby, arming.state.screen, "a host its house kept was moved")
        assertTrue(arming.lobby.hosting)
    }
}
