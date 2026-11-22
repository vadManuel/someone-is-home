package home.someoneshome.app

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Destination
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.RadioCause
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.RadioReading
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.MemoryHomeFinder
import home.someoneshome.ui.MemoryLobbyLink
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PermsModel
import home.someoneshome.ui.ScreenId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **A NEARBY DEVICES refused after the arming stays in the round and is reported as a switch-off**
 * (D-311, D-326; D-327, the overnight review of 2026-11-22).
 *
 * The panel half: a guest that holds the first of its arming pushes is not let go by the lobby
 * probe's late REFUSED (`ArmedSeatKeptTest`, ui). This is the round half: the same OS, read by the
 * round's uploader as `Permissions.refusedGrants` reads it on a phone, counts the grant off at the
 * round's first beat, once, as a permission (D-311: *a grant already off when the round armed counts
 * too*), so the house names the seat at the first meeting as it names any other switch-off.
 */
class ArmedSeatSwitchOffTest {

    private class QuietRadio : ContactRadio {
        override fun advertise(token: ContactToken) = Unit
        override fun scan(on: Boolean) = Unit
        override fun drain(): List<RadioReading> = emptyList()
        override fun silence() = Unit
        override fun faultBegan(): RadioCause? = null
    }

    @Test
    fun aRefusedProbeAfterTheArmingIsTheRoundsPermission() {
        val rows = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()
        var answer: (() -> Unit)? = null
        val guest = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(
                finder = MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))),
                link = MemoryLobbyLink(joined = 4, linesIn = 4),
            ),
            perms = PermsModel(
                read = { rows.getValue(it) },
                recheck = { then -> answer = { rows[DevicePermission.NearbyDevices] = Grant.Refused; then() } },
            ),
        )
        guest.lobby.nameResident("ROSE")
        guest.lobby.look()
        guest.attachToHome(guest.lobby.nearby.first())
        guest.cameBackToTheForeground()

        // The arming lands one push at a time; the work order is ahead of the opening text.
        guest.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Jam), false, listOf(Destination(MarkerId("c0"), shape = "diamond", room = "STUDY")))),
            ),
        )
        assertNotNull(answer, "the return to the lobby did not probe NEARBY DEVICES").invoke()
        assertNotNull(guest.lobby.attached, "the probe's late REFUSED let an armed seat go")
        guest.houseSaid(RoundMessage.OpeningMessage(Haptic.Long, 0L))
        assertEquals(ScreenId.Armed, guest.state.screen)

        // The round's uploader, reading the same OS one bit a row, as `Permissions.refusedGrants` does.
        val reports = mutableListOf<RoundBody.RadioFaulted>()
        val uploader = ContactUploader(
            QuietRadio(), { ContactWindow(10) }, faulted = { reports += it }, kept = MemorySeat(),
            refusedGrants = { DevicePermission.entries.foldIndexed(0) { i, bits, row -> if (rows.getValue(row) == Grant.Refused) bits or (1 shl i) else bits } },
            send = { },
        )
        uploader.heard(RoundMessage.ContactSeedIssued(ContactSeed("0123456789abcdef0123456789abcdef")))
        repeat(3) { uploader.beat() }
        assertEquals(listOf(RadioCause.Permission), reports.map { it.cause }, "the switch-off was not the round's permission, once")
    }
}
