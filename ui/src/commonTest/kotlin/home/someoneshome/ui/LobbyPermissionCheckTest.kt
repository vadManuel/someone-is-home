package home.someoneshome.ui

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Something this game needs switched off in the lobby** (the owner's 2026-11-21 rulings, D-311):
 * *"If you're in the lobby you get kicked out and land on the permission screen."*
 *
 * The phone's half, on the real flow and lobby models. Each row stands for the OS: a map the test
 * writes, read through the same seam the app reads (`Permissions.standing`, which reads the
 * Bluetooth radio turned off as the BLUETOOTH row refused, so here "the radio off" is that row
 * reading refused, as the app hands it down).
 */
class LobbyPermissionCheckTest {

    private fun aHome() = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    /** The OS's answers, every row granted until a test switches one off. */
    private class Os {
        val rows = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()
        fun off(row: DevicePermission) { rows[row] = Grant.Refused }
        fun on(row: DevicePermission) { rows[row] = Grant.Granted }
    }

    private class Holding(var round: Boolean = false) : LobbyLink by MemoryLobbyLink(joined = 4, linesIn = 4) {
        override fun holdsARoundSeat(): Boolean = round
    }

    /** A guest seated in a lobby, walked in as a real one is. */
    private fun aGuest(os: Os, link: LobbyLink = MemoryLobbyLink(joined = 4, linesIn = 4)): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link),
            perms = PermsModel(read = { os.rows.getValue(it) }),
        )
        model.lobby.nameResident("ROSE")
        model.lobby.look()
        model.attachToHome(model.lobby.nearby.first())
        assertEquals(ScreenId.Lobby, model.state.screen, "the fixture never reached a lobby")
        return model
    }

    /** A host on its own lobby, the house on the air. */
    private fun aHost(os: Os, armed: MutableList<Unit> = mutableListOf()): FlowModel {
        val lobby = LobbyModel.sample()
        val model = FlowModel(
            PanelState(screen = ScreenId.Lobby),
            lobby = lobby,
            perms = PermsModel(read = { os.rows.getValue(it) }),
            onLightsOut = { walk -> armed += Unit; walk() },
        )
        assertTrue(model.lobby.hosting)
        return model
    }

    /**
     * **GUARD: a guest that switches any of them off leaves the lobby and lands on PERMISSIONS**, the
     * row it switched off reading REFUSED there, for every row, the Bluetooth radio's included; with
     * nothing switched off the check moves nothing.
     */
    @Test
    fun aGuestSwitchingAnythingOffLeavesTheLobbyForPermissions() {
        for (row in DevicePermission.entries) {
            val os = Os()
            val guest = aGuest(os)
            guest.permissionsChecked()
            assertEquals(ScreenId.Lobby, guest.state.screen, "$row: a guest with everything on was moved")
            os.off(row)
            guest.permissionsChecked()
            assertEquals(ScreenId.Perms, guest.state.screen, "$row switched off and the guest stayed in the lobby")
            assertNull(guest.lobby.attached, "$row: the guest still holds the lobby it was let go from")
            assertEquals(0, guest.lobby.standing.joined, "$row: the guest still counts a room it left")
            assertEquals(Grant.Refused, guest.perms.grant(row), "$row does not show as refused on PERMISSIONS")
            assertEquals(listOf(row), guest.perms.missing, "PERMISSIONS shows something else owed")
            assertFalse(guest.canGoBack, "$row: the guest could step back into the lobby it was let go from")
            // With everything back on, it can rejoin as usual.
            os.on(row)
            guest.perms.refresh()
            assertTrue(guest.perms.complete, "$row back on and PERMISSIONS still owes something")
            guest.go(ScreenId.Join)
            guest.attachToHome(guest.lobby.nearby.first())
            assertEquals(ScreenId.Lobby, guest.state.screen, "$row back on and the guest could not rejoin")
        }
    }

    /** The one-line screen is the lobby too; a phone not in a lobby, or in a round, is never moved. */
    @Test
    fun theCheckActsOnTheLobbysScreensAndNowhereElse() {
        val os = Os()
        val guest = aGuest(os)
        guest.go(ScreenId.Secret)
        os.off(DevicePermission.Camera)
        guest.permissionsChecked()
        assertEquals(ScreenId.Perms, guest.state.screen, "a guest on the one-line screen was not let go")

        for (screen in listOf(ScreenId.Home, ScreenId.Page2, ScreenId.Discussion, ScreenId.Armed)) {
            val o = Os()
            val other = aGuest(o)
            other.push(screen)
            o.off(DevicePermission.Bluetooth)
            other.permissionsChecked()
            assertEquals(screen, other.state.screen, "a phone on $screen was walked to PERMISSIONS by the lobby's check")
        }
        // A phone that holds no lobby seat is not the lobby's to let go.
        val o = Os()
        val gone = aGuest(o)
        gone.leaveLobby()
        o.off(DevicePermission.Camera)
        gone.permissionsChecked()
        assertEquals(ScreenId.Join, gone.state.screen, "a phone in no lobby was moved by the lobby's check")
    }

    /**
     * **GUARD (the overnight review's finding 2): a phone that holds a seat in a running round is
     * never let go by the lobby's check, and goes back to its seat past the gate.** A relaunched
     * phone with Camera still off falls through to JOIN A HOME, not PERMISSIONS, and its tap on the
     * home is not refused; once the round is over the gate applies again.
     */
    @Test
    fun aPhoneHoldingARoundSeatGoesBackToItPastTheGate() {
        val os = Os().also { it.off(DevicePermission.Camera) }
        val link = Holding(round = true)
        val relaunched = FlowModel(
            PanelState(screen = ScreenId.Boot),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link),
            perms = PermsModel(read = { os.rows.getValue(it) }),
        )
        assertEquals(ScreenId.Join, relaunched.fallThrough(Flow.autoAdvance.getValue(ScreenId.Boot)), "a phone holding a round seat was held at PERMISSIONS")
        relaunched.push(ScreenId.Join)
        relaunched.lobby.nameResident("ROSE")
        relaunched.lobby.look()
        relaunched.attachToHome(relaunched.lobby.nearby.first())
        assertEquals(aHome(), relaunched.lobby.attached, "the gate refused a phone going back to its round seat")
        relaunched.permissionsChecked()
        assertEquals(ScreenId.Lobby, relaunched.state.screen, "the lobby's check let go a phone holding a round seat")

        // The round over: the gate and the check apply again.
        link.round = false
        relaunched.permissionsChecked()
        assertEquals(ScreenId.Perms, relaunched.state.screen, "after the round the phone was not let go")
        assertEquals(ScreenId.Perms, relaunched.fallThrough(Flow.autoAdvance.getValue(ScreenId.Boot)))
    }

    /**
     * **GUARD: the host cannot leave, so it is shown PERMISSIONS, cannot press LIGHTS OUT, and the
     * lobby survives** (D-311, provisional). Back on, its PROCEED goes back to its own lobby.
     */
    @Test
    fun theHostIsShownPermissionsAndTheLobbySurvives() {
        val os = Os()
        val armed = mutableListOf<Unit>()
        val host = aHost(os, armed)
        val joined = host.lobby.standing.joined
        os.off(DevicePermission.Bluetooth)
        host.permissionsChecked()
        assertEquals(ScreenId.Perms, host.state.screen, "the host was not shown PERMISSIONS")
        assertTrue(host.lobby.hosting, "the host's house came off the air")
        assertEquals(joined, host.lobby.standing.joined, "the host's lobby lost its seats")
        assertEquals(Grant.Refused, host.perms.grant(DevicePermission.Bluetooth))
        assertTrue(Flow.viaActions.getValue(ScreenId.Perms).contains(ScreenId.Lobby))
        os.on(DevicePermission.Bluetooth)
        host.perms.refresh()
        assertTrue(host.perms.complete)
        host.go(ScreenId.Lobby)
        assertEquals(ScreenId.Lobby, host.state.screen)
        assertTrue(armed.isEmpty(), "something armed while the host was held")
    }

    /**
     * **GUARD: the same check at lights out keeps the phone, and so the round, out** (D-311): a host
     * holding LIGHTS OUT with something switched off arms nothing and is shown PERMISSIONS.
     */
    @Test
    fun lightsOutWithSomethingSwitchedOffArmsNothing() {
        val os = Os()
        val armed = mutableListOf<Unit>()
        val host = aHost(os, armed)
        os.off(DevicePermission.Motion)
        host.lightsOut()
        assertTrue(armed.isEmpty(), "LIGHTS OUT armed a round on a host missing a grant")
        assertEquals(ScreenId.Perms, host.state.screen, "the host was not shown PERMISSIONS")
        os.on(DevicePermission.Motion)
        host.go(ScreenId.Lobby)
        host.lightsOut()
        assertEquals(1, armed.size, "LIGHTS OUT with everything on did not arm")
        assertEquals(ScreenId.Armed, host.state.screen)
    }

    /**
     * **GUARD (the overnight review's finding 3): between rounds the check runs on arrival** — at
     * NEW ROUND for a guest, and for the host after the house closed, who must not be left on a
     * lobby with LIGHTS OUT and a grant off.
     */
    @Test
    fun betweenRoundsTheCheckRunsOnArrival() {
        val os = Os()
        val guest = aGuest(os)
        guest.push(ScreenId.Home)
        os.off(DevicePermission.Motion)
        guest.newRound()
        assertEquals(ScreenId.Perms, guest.state.screen, "NEW ROUND kept a guest missing a grant in the lobby")
        assertNull(guest.lobby.attached)

        val hostOs = Os()
        val host = aHost(hostOs)
        host.push(ScreenId.Home)
        hostOs.off(DevicePermission.Camera)
        host.houseSaid(RoundMessage.HouseClosedOnPermission)
        assertEquals(ScreenId.Perms, host.state.screen, "the host was left on its lobby after the close with a grant off")
        assertTrue(host.lobby.hosting)
    }
}
