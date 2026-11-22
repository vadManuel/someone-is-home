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

/**
 * **A seat the house has armed is not let go by a late lobby answer** (D-311, D-326; D-327, the
 * overnight review of 2026-11-22).
 *
 * The house's arming pushes land one by one, and the opening text that walks a phone to ARMED is
 * not the first of them (the lamp and the work order are), so an armed phone stands on the lobby
 * for a moment holding a round. A NEARBY DEVICES probe started on a return to the foreground answers
 * up to two seconds later, and the Bluetooth radio's callback can fire at any time: either landing
 * then ran `leaveLobby` and `roundForgotten` on a seat the round now holds. Once a push of the round
 * is in hand, the switch-off is the round's: the seat stays, the row reads REFUSED, and the round's
 * first beat reports it as a permission (`ArmedSeatSwitchOffTest`, app). Both roles alike: the
 * lobby has none, and the role arrives after the first push.
 */
class ArmedSeatKeptTest {

    private fun aHome() = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    /** The OS, and a NEARBY DEVICES probe whose answer the test hands back when it chooses. */
    private class Os {
        val rows = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()
        var answer: (() -> Unit)? = null
        fun perms() = PermsModel(
            read = { rows.getValue(it) },
            recheck = { then -> answer = { rows[DevicePermission.NearbyDevices] = Grant.Refused; then() } },
        )
    }

    private fun aGuest(os: Os, role: PanelRole): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Join, role = role),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = MemoryLobbyLink(joined = 4, linesIn = 4)),
            perms = os.perms(),
        )
        model.lobby.nameResident("ROSE")
        model.lobby.look()
        model.attachToHome(model.lobby.nearby.first())
        assertEquals(ScreenId.Lobby, model.state.screen, "the fixture never reached a lobby")
        return model
    }

    /** The first of the house's arming pushes: the work order, ahead of the opening text. */
    private fun FlowModel.armedButNotYetWalked() = houseSaid(
        RoundMessage.WorkOrderIssued(
            listOf(OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Jam), false, listOf(Destination(MarkerId("c0"), shape = "diamond", room = "STUDY")))),
        ),
    )

    private fun FlowModel.assertKept(why: String) {
        assertEquals(ScreenId.Lobby, state.screen, why)
        assertNotNull(lobby.attached, "$why: the seat was let go")
        houseSaid(RoundMessage.OpeningMessage(Haptic.Long, 0L))
        assertEquals(ScreenId.Armed, state.screen, "$why: the opening text did not walk the kept seat to ARMED")
    }

    @Test
    fun `a REFUSED probe answer arriving after arming leaves the seat in the round`() {
        for (role in PanelRole.entries) {
            val os = Os()
            val guest = aGuest(os, role)
            guest.cameBackToTheForeground()
            val answer = assertNotNull(os.answer, "$role: the return to the lobby did not probe NEARBY DEVICES")
            guest.armedButNotYetWalked()
            answer()
            assertEquals(listOf(DevicePermission.NearbyDevices), guest.perms.switchedOffNow().toList(), "$role: the probe's REFUSED is not what the OS now reads")
            guest.assertKept("$role: a probe's REFUSED landing after the arming let the seat go")
        }
    }

    @Test
    fun `the radio's callback after arming leaves the seat in the round too`() {
        for (role in PanelRole.entries) {
            val os = Os()
            val guest = aGuest(os, role)
            guest.armedButNotYetWalked()
            os.rows[DevicePermission.Bluetooth] = Grant.Refused
            guest.permissionsChecked()
            guest.assertKept("$role: the Bluetooth radio switched off after the arming let the seat go")
        }
    }

    @Test
    fun `before any push of a round the lobby still lets a REFUSED guest go`() {
        val os = Os()
        val guest = aGuest(os, PanelRole.Resident)
        guest.cameBackToTheForeground()
        assertNotNull(os.answer).invoke()
        assertEquals(ScreenId.Perms, guest.state.screen, "a REFUSED probe in the lobby kept the guest (D-326)")
    }
}
