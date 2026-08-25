package home.someoneshome.ui

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **No seat until the device is ready** — D-161's gate, on the two doors into a lobby.
 *
 * The ruling is not about mDNS and never was: *iOS asks once, ever, on whichever screen first needs
 * the thing*, and in this game the screen that first needs the camera is a player alone in a dark
 * corridor. Warming a permission is half the answer; **the other half is that a warmed permission
 * which was declined leaves a phone that looks like it is working and is not.** E-D2-1 measured
 * exactly that: *Don't Allow* leaves the scan screen lit, instructive, counting down, registering
 * nothing.
 *
 * So this is the gate, from both sides of the party — the host who is about to advertise a home,
 * and the player who is about to tap a network row.
 */
class SetupGateTest {

    /**
     * A model whose permission rows are whatever this test says they are.
     *
     * A **fresh** [LobbyModel] rather than the sample one, and that is not incidental:
     * `LobbyModel.sample()` opens already hosting, because it is the fixture the lobby screen is
     * drawn from. A gate test written against it would assert `hosting` on a phone that was
     * hosting before it asked for anything.
     */
    private fun phone(vararg answers: Pair<DevicePermission, Grant>): FlowModel {
        val states = answers.toMap()
        val perms = PermsModel(read = { states[it] ?: Grant.Unasked })
        perms.refresh()
        return FlowModel(perms = perms, homes = SavedHomesModel.sample(), lobby = LobbyModel())
    }

    /** Every row granted. The phone a host is meant to be standing behind. */
    private fun readyPhone(): FlowModel =
        phone(*DevicePermission.entries.map { it to Grant.Granted }.toTypedArray())

    /**
     * **A host with no grants advertises nothing**, and is told which rows, on the screen they are
     * already on.
     */
    @Test
    fun hostingIsRefusedUntilTheGrantsExist() {
        val model = phone(DevicePermission.Camera to Grant.Refused)
        val home = model.homes.homes.first()

        model.hostHome(home.name)

        assertTrue(!model.lobby.hosting, "a phone that cannot scan went on the air as a house")
        assertEquals(
            ScreenId.Boot, model.state.screen,
            "the refusal walked the host forward anyway — the fall-through this gate exists to stop",
        )
        val refusal = assertNotNull(model.lobby.refusal, "the host was refused with no reason given")
        assertTrue(
            DevicePermission.Camera.label in refusal,
            "the refusal does not name the row the host has to go back and fix: '$refusal'",
        )
    }

    /**
     * **And admits with them.** The other half, and the half that makes the test above mean
     * something: a gate that refused everything would pass the assertions above and ship a game
     * nobody can host.
     */
    @Test
    fun hostingIsAdmittedOnceEveryGrantExists() {
        val model = readyPhone()
        val home = model.homes.homes.first()

        model.hostHome(home.name)

        assertNull(
            model.lobby.refusal,
            "a phone with every grant was refused a home anyway: ${model.lobby.refusal}",
        )
        assertTrue(model.lobby.hosting, "a complete phone did not get its home on the air")
        assertEquals(ScreenId.Lobby, model.state.screen, "the host did not reach the lobby")
    }

    /**
     * **The player's door is the same door.** D-161 is about a seat, not about hosting — *the lobby
     * will not seat a device that cannot do the job, and the host is never handed a party that will
     * fail in the dark.*
     */
    @Test
    fun joiningIsRefusedUntilTheGrantsExist() {
        val model = phone()
        val home = NearbyHome("THE BUNGALOW", "10.0.0.4", 8080)

        model.attachToHome(home)

        assertNull(model.lobby.attached, "a phone with no grants took a seat in somebody's house")
        assertNotNull(model.lobby.refusal, "the player was refused with no reason given")
    }

    /** And joins once the phone can do the job. */
    @Test
    fun joiningIsAdmittedOnceEveryGrantExists() {
        val model = readyPhone()
        val home = NearbyHome("THE BUNGALOW", "10.0.0.4", 8080)

        model.attachToHome(home)

        assertEquals(home, model.lobby.attached, "a complete phone was kept out of the lobby")
    }

    /**
     * **A phone nobody has asked anything is refused**, which is what a cold launch is.
     *
     * The tempting default here is the permissive one — an unread row reading as fine — and it is
     * the one that ships. Stated as its own test because the gate's other cases all arrange an
     * explicit refusal, and *nothing has happened yet* is the state a real first launch is in.
     */
    @Test
    fun theDefaultPhoneHasBeenAskedNothingAndIsRefused() {
        val model = FlowModel(homes = SavedHomesModel.sample(), lobby = LobbyModel())

        model.hostHome(model.homes.homes.first().name)

        assertTrue(
            !model.lobby.hosting,
            "the default FlowModel hosts a home, so every test, bench and screenshot walks " +
                "through a gate that was never shut",
        )
        assertEquals(
            DevicePermission.entries.toList(), model.perms.missing,
            "a FlowModel nobody arranged already believes some permission was answered",
        )
    }
}
