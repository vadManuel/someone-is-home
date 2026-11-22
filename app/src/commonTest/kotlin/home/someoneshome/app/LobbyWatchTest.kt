package home.someoneshome.app

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.platform.Foreground
import home.someoneshome.platform.NoPermissions
import home.someoneshome.platform.missing
import home.someoneshome.platform.requestStanding
import home.someoneshome.platform.standing
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
import kotlin.test.assertNull

/**
 * **The lobby's two app-root triggers, wired** (D-326): the Bluetooth radio's state callback and the
 * return to the foreground, through [watchTheLobby] onto a real flow seated in a lobby, the seam a
 * [NoPermissions] standing in for the OS. Nothing here calls the lobby's check itself, and there is
 * no poll: a guest let go here was let go by the event.
 */
class LobbyWatchTest {

    /** A foreground the test moves itself. */
    private class Front : Foreground {
        private var back: () -> Unit = {}
        override fun watch(onBackground: () -> Unit, onForeground: () -> Unit): () -> Unit {
            back = onForeground
            return { back = {} }
        }
        fun returns() = back()
    }

    /** A guest seated in a lobby, its rows read through the seam as the app root reads them. */
    private fun aGuest(os: NoPermissions): FlowModel {
        DevicePermission.entries.forEach { os.says(it, Grant.Granted) }
        val home = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)
        val flow = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(home)), link = MemoryLobbyLink(joined = 4, linesIn = 4)),
            perms = PermsModel(read = os::standing, ask = os::requestStanding, reread = os::refresh, recheck = os::recheckNearby),
        )
        flow.lobby.nameResident("ROSE")
        flow.lobby.look()
        flow.attachToHome(flow.lobby.nearby.first())
        assertEquals(ScreenId.Lobby, flow.state.screen, "the fixture never reached a lobby")
        return flow
    }

    /** **GUARD: the radio turned off reaches the lobby's check by its own callback**, and the guest goes. */
    @Test
    fun theRadiosStateCallbackLetsAGuestGo() {
        val os = NoPermissions()
        val guest = aGuest(os)
        val stop = watchTheLobby(os, Front()) { guest }
        os.radioOff = true
        assertEquals(ScreenId.Perms, guest.state.screen, "the radio turned off and the guest stayed in the lobby")
        assertNull(guest.lobby.attached)
        assertEquals(listOf(DevicePermission.Bluetooth), os.missing())
        stop()
    }

    /**
     * **GUARD: a return to the foreground reads the OS again and probes NEARBY DEVICES in the lobby**
     * (D-326): a refusal the probe hears lets the guest go.
     */
    @Test
    fun aReturnToTheForegroundProbesAndLetsAGuestGo() {
        val os = NoPermissions().also { it.probeHears = Grant.Refused }
        val guest = aGuest(os)
        val front = Front()
        val stop = watchTheLobby(os, front) { guest }
        val reads = os.refreshes
        front.returns()
        assertEquals(reads + 1, os.refreshes, "the return did not read the OS again")
        assertEquals(1, os.rechecks, "the return to the lobby did not probe NEARBY DEVICES")
        assertEquals(ScreenId.Perms, guest.state.screen, "NEARBY DEVICES refused and the guest stayed in the lobby")
        stop()
    }

    /** Stopped, neither trigger reaches the flow. */
    @Test
    fun stoppedItHearsNothing() {
        val os = NoPermissions()
        val guest = aGuest(os)
        val front = Front()
        watchTheLobby(os, front) { guest }()
        os.radioOff = true
        front.returns()
        assertEquals(ScreenId.Lobby, guest.state.screen, "a stopped watch still moved the guest")
        assertEquals(0, os.rechecks)
    }
}
