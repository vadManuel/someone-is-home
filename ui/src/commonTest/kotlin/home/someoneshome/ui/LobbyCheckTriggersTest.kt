package home.someoneshome.ui

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.model.Haptic
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The lobby's check runs on events, never on a clock** (the owner's 2026-11-22 rulings, D-326).
 *
 * D-311 read the OS once a second. That poll is gone, so each test here is one of the triggers that
 * remain, driven as the app drives it, with nothing calling the check on a timer: a phone that is
 * let go here is let go by its trigger alone.
 *
 * - the return to the foreground ([FlowModel.cameBackToTheForeground]), which in the lobby also
 *   probes NEARBY DEVICES again;
 * - the Bluetooth radio's state callback (its flow half here; its wiring is `LobbyWatchTest`, app);
 * - the app reopened after iOS ended it (the cold start's gate);
 * - NEW ROUND, and a later round's push;
 * - the host after a close;
 * - a refused Nearby Interaction session: in the lobby the return's probe, here; in a round the
 *   ranging pool's own report (`RangingPoolTest`, `OneReportAGrantTest`).
 *
 * The OS is a map, read through the seam the app reads (`Permissions.standing`), and the probe is
 * `Permissions.recheckNearby`'s shape: it writes what it heard, except that ASKED leaves the row as
 * it read (`afterARecheck`, platform).
 */
class LobbyCheckTriggersTest {

    private fun aHome() = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    /** The OS: every row granted until a test switches one off, and a probe that hears [probeHears]. */
    private class Os {
        val rows = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()
        var probeHears: Grant = Grant.Granted
        var probes = 0
        fun off(row: DevicePermission) { rows[row] = Grant.Refused }
        fun perms() = PermsModel(
            read = { rows.getValue(it) },
            recheck = { then ->
                probes++
                if (probeHears != Grant.Asked) rows[DevicePermission.NearbyDevices] = probeHears
                then()
            },
        )
    }

    private class Holding(var round: Boolean = false) : LobbyLink by MemoryLobbyLink(joined = 4, linesIn = 4) {
        override fun holdsARoundSeat(): Boolean = round
    }

    /** A guest seated in a lobby, walked in as a real one is. */
    private fun aGuest(os: Os, role: PanelRole = PanelRole.Resident, link: LobbyLink = MemoryLobbyLink(joined = 4, linesIn = 4)): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Join, role = role),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link),
            perms = os.perms(),
        )
        model.lobby.nameResident("ROSE")
        model.lobby.look()
        model.attachToHome(model.lobby.nearby.first())
        assertEquals(ScreenId.Lobby, model.state.screen, "the fixture never reached a lobby")
        return model
    }

    /** A host on its own lobby, the house on the air. */
    private fun aHost(os: Os): FlowModel {
        val model = FlowModel(PanelState(screen = ScreenId.Lobby), lobby = LobbyModel.sample(), perms = os.perms())
        assertTrue(model.lobby.hosting)
        return model
    }

    private fun FlowModel.assertLetGo(why: String) {
        assertEquals(ScreenId.Perms, state.screen, why)
        assertNull(lobby.attached, "$why: the guest still holds the lobby it was let go from")
    }

    // ---- The return to the foreground ------------------------------------------------------------

    /**
     * **GUARD: the return to the foreground lets a guest go for any row switched off in Settings**,
     * every row in turn, with no poll behind it.
     */
    @Test
    fun aReturnToTheForegroundLetsAGuestGo() {
        for (row in DevicePermission.entries) {
            val os = Os()
            val guest = aGuest(os)
            guest.cameBackToTheForeground()
            assertEquals(ScreenId.Lobby, guest.state.screen, "$row: a return with everything on moved the guest")
            os.off(row)
            guest.cameBackToTheForeground()
            guest.assertLetGo("$row switched off in Settings and the return kept the guest in the lobby")
            assertEquals(Grant.Refused, guest.perms.grant(row), "$row does not show as refused on PERMISSIONS")
        }
    }

    /**
     * **GUARD (D-326): back in the foreground in the lobby, NEARBY DEVICES is probed again, and a
     * REFUSED answer lets the guest go to PERMISSIONS** like any other lobby switch-off. Nearby
     * Interaction has no status to read: before the probe the row still reads GRANTED, as the seam
     * last heard it, so only the probe can hear the switch-off.
     */
    @Test
    fun aReturnsProbeThatHearsRefusedLetsTheGuestGo() {
        val os = Os().also { it.probeHears = Grant.Refused }
        val guest = aGuest(os)
        assertEquals(Grant.Granted, os.rows.getValue(DevicePermission.NearbyDevices), "the arrangement is wrong")
        guest.cameBackToTheForeground()
        assertEquals(1, os.probes, "the return to the lobby did not probe NEARBY DEVICES (D-326)")
        guest.assertLetGo("NEARBY DEVICES switched off in Settings and the return kept the guest in the lobby")
        assertEquals(Grant.Refused, guest.perms.grant(DevicePermission.NearbyDevices), "NEARBY DEVICES does not show as refused")
        assertEquals(listOf(DevicePermission.NearbyDevices), guest.perms.missing, "PERMISSIONS shows something else owed")
    }

    /** The one-line screen is the lobby too, so it is probed there as well. */
    @Test
    fun theOneLineScreenIsProbedToo() {
        val os = Os().also { it.probeHears = Grant.Refused }
        val guest = aGuest(os)
        guest.go(ScreenId.Secret)
        guest.cameBackToTheForeground()
        guest.assertLetGo("NEARBY DEVICES refused on the one-line screen and the guest stayed")
    }

    /**
     * **GUARD (D-326, provisional): a probe that says only ASKED keeps the phone**, its row as it
     * read. A slow probe is not a switch-off, and a guest is not let go over one.
     */
    @Test
    fun aReturnsProbeThatSaysOnlyAskedKeepsTheGuest() {
        val os = Os().also { it.probeHears = Grant.Asked }
        val guest = aGuest(os)
        guest.cameBackToTheForeground()
        assertEquals(1, os.probes, "the return to the lobby did not probe NEARBY DEVICES")
        assertEquals(ScreenId.Lobby, guest.state.screen, "a probe that said only ASKED let the guest go")
        assertNotNull(guest.lobby.attached, "a probe that said only ASKED took the guest's seat")
        assertEquals(Grant.Granted, os.rows.getValue(DevicePermission.NearbyDevices), "ASKED rewrote the row")
    }

    /**
     * **GUARD: the host's return follows D-311's host choice**: a REFUSED probe shows it PERMISSIONS
     * with the house still on the air and every seat kept, and ASKED keeps it on its lobby.
     */
    @Test
    fun theHostsReturnFollowsTheHostsChoice() {
        val os = Os().also { it.probeHears = Grant.Refused }
        val host = aHost(os)
        val joined = host.lobby.standing.joined
        host.cameBackToTheForeground()
        assertEquals(1, os.probes, "the host's return to its lobby did not probe NEARBY DEVICES")
        assertEquals(ScreenId.Perms, host.state.screen, "NEARBY DEVICES refused and the host was not shown PERMISSIONS")
        assertTrue(host.lobby.hosting, "the host's house came off the air")
        assertEquals(joined, host.lobby.standing.joined, "the host's lobby lost its seats")

        val calm = Os().also { it.probeHears = Grant.Asked }
        val other = aHost(calm)
        other.cameBackToTheForeground()
        assertEquals(ScreenId.Lobby, other.state.screen, "ASKED moved the host off its lobby")
    }

    /**
     * **GUARD (D-309, D-310): in a round nothing is probed**, for an Insider's phone and a
     * Resident's alike, on every screen a round stands on, and on the lobby's own screen while the
     * phone holds a round's seat. The probe hearing REFUSED would move nothing either.
     */
    @Test
    fun noProbeRunsInARound() {
        for (role in PanelRole.entries) {
            for (screen in listOf(ScreenId.Armed, ScreenId.Home, ScreenId.Page2, ScreenId.Discussion)) {
                val os = Os().also { it.probeHears = Grant.Refused }
                val phone = aGuest(os, role)
                phone.push(screen)
                phone.cameBackToTheForeground()
                assertEquals(0, os.probes, "$role: a return on $screen probed NEARBY DEVICES beside the round's sessions")
                assertEquals(screen, phone.state.screen, "$role: a return on $screen moved the phone")
            }
            val os = Os().also { it.probeHears = Grant.Refused }
            val held = aGuest(os, role, link = Holding(round = true))
            held.cameBackToTheForeground()
            assertEquals(0, os.probes, "$role: a phone holding a round's seat was probed")
            assertEquals(ScreenId.Lobby, held.state.screen)
        }
    }

    /** A phone in no lobby is not the lobby's: nothing is probed and nothing moves. */
    @Test
    fun aPhoneInNoLobbyIsNotProbed() {
        val os = Os().also { it.probeHears = Grant.Refused }
        val gone = aGuest(os)
        gone.leaveLobby()
        gone.cameBackToTheForeground()
        assertEquals(0, os.probes)
        assertEquals(ScreenId.Join, gone.state.screen)
    }

    // ---- The Bluetooth radio's state callback ------------------------------------------------------

    /**
     * **GUARD: the radio turned off in Control Centre lets the guest go on the radio's own callback**
     * (`Permissions.watchTheRadio` runs the check, `LobbyWatchTest`), with no return and no poll: the
     * BLUETOOTH row reads refused while the radio is off, and the check reads it.
     */
    @Test
    fun theRadiosCallbackLetsTheGuestGo() {
        val os = Os()
        val guest = aGuest(os)
        os.off(DevicePermission.Bluetooth)
        guest.permissionsChecked()
        guest.assertLetGo("the radio turned off and its callback kept the guest in the lobby")
        assertEquals(0, os.probes, "the radio's callback probed NEARBY DEVICES")
    }

    // ---- The app reopened after iOS ended it -------------------------------------------------------

    /**
     * **GUARD: a phone iOS ended for Camera or Motion meets the gate at its cold start** and lands on
     * PERMISSIONS with the row refused, not on JOIN A HOME; a phone holding a round's seat goes back
     * to it past the gate, and its switch-off is the round's (`ContactUploaderTest`, app).
     */
    @Test
    fun aRelaunchMeetsTheGate() {
        for (row in listOf(DevicePermission.Camera, DevicePermission.Motion)) {
            val os = Os().also { it.off(row) }
            val relaunched = FlowModel(
                PanelState(screen = ScreenId.Boot),
                lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = Holding(round = false)),
                perms = os.perms(),
            )
            assertEquals(ScreenId.Perms, relaunched.fallThrough(Flow.autoAdvance.getValue(ScreenId.Boot)), "$row off and the relaunch skipped PERMISSIONS")
            val seated = FlowModel(
                PanelState(screen = ScreenId.Boot),
                lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = Holding(round = true)),
                perms = os.perms(),
            )
            assertEquals(ScreenId.Join, seated.fallThrough(Flow.autoAdvance.getValue(ScreenId.Boot)), "$row off and a round's seat was held at PERMISSIONS")
        }
    }

    // ---- NEW ROUND, and a later round's push -------------------------------------------------------

    /**
     * **GUARD: NEW ROUND runs the check on arrival in the lobby; a later round's push does not**
     * (D-327). The later round's push is that round's, so the phone holding it was armed into it,
     * and a grant still off is the round's to count on its first beat (D-311), not the lobby's to
     * let go: the seat stays, and the push walks the phone to ARMED.
     */
    @Test
    fun aNewRoundRunsTheCheckOnArrival() {
        val os = Os()
        val guest = aGuest(os)
        guest.push(ScreenId.Home)
        os.off(DevicePermission.NearbyDevices)
        guest.newRound()
        guest.assertLetGo("NEW ROUND kept a guest missing a grant in the lobby")

        val again = Os()
        val behind = aGuest(again)
        behind.push(ScreenId.Home)
        again.off(DevicePermission.Camera)
        behind.houseBeganAnotherRound()
        assertEquals(ScreenId.Lobby, behind.state.screen, "a later round's push let go a seat armed into that round")
        assertNotNull(behind.lobby.attached, "a later round's push let go a seat armed into that round")
        behind.houseSaid(RoundMessage.OpeningMessage(Haptic.Long, 0L))
        assertEquals(ScreenId.Armed, behind.state.screen, "the later round's opening did not walk the kept seat to ARMED")
    }

    // ---- The host after a close ---------------------------------------------------------------------

    /** **GUARD: the host is checked as its lobby comes back after the house closed.** */
    @Test
    fun theHostAfterACloseIsChecked() {
        val os = Os()
        val host = aHost(os)
        host.push(ScreenId.Home)
        os.off(DevicePermission.Bluetooth)
        host.houseSaid(RoundMessage.HouseClosedOnPermission)
        assertEquals(ScreenId.Perms, host.state.screen, "the host was left on its lobby after the close with a grant off")
        assertTrue(host.lobby.hosting, "the host's house came off the air")
    }
}
