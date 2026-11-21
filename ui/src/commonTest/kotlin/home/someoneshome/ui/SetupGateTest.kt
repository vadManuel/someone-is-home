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
    /**
     * A phone that is ready in **every** sense — every grant given, and named.
     *
     * The name is here rather than in each positive test because it is the same fact as the grants:
     * *the lobby will not seat a device that cannot do the job*, and since E-PAY-6 a device with no
     * name on it is one of them (`LobbyModel.named`). These tests are about D-161's gate, so the
     * fixture has to clear every other gate or the refusal they read would be the wrong one.
     */
    private fun readyPhone(): FlowModel =
        phone(*DevicePermission.entries.map { it to Grant.Granted }.toTypedArray())
            .also { it.lobby.nameResident("ELLIOT") }

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
     * ⚠️ **THE FALSE REFUSAL — a granted phone that never opened the Perms screen** (N6).
     *
     * This is the sweep's negative control written down as a test, and against the build it was
     * found on it fails. `PermsModel.states` opens at `emptyMap()` and `refresh()` had exactly one
     * caller in the whole app — the Perms screen's own `LaunchedEffect`. Anything that reaches a
     * lobby without composing that screen therefore asked the gate a question about a map nobody
     * had filled, and got back **the whole five-row sentence on a phone where every row is
     * granted**. Both benches do exactly that: `flow.go(ScreenId.Join)` at launch, early enough to
     * pre-empt Boot's first frame. No round could be armed on hardware or on a simulator.
     *
     * So the fixture here is deliberately **not** [phone] — no `refresh()`, because a refresh is
     * the thing the real path did not get. What it is instead is the honest arrangement of the 13
     * Pro: an OS that answers GRANTED to every question it is asked, and a screen that never asked.
     *
     * The ruling this asserts is **the gate reads the OS at decision time** (D-161, D-180's
     * family): a refusal must be true and must name a real cause, and one assembled out of a
     * display map that nobody filled is neither.
     */
    @Test
    fun aGrantedPhoneThatNeverOpenedThePermsScreenIsAdmitted() {
        val model = FlowModel(
            // Every row granted at the OS, and `refresh()` deliberately never called.
            perms = PermsModel(read = { Grant.Granted }),
            homes = SavedHomesModel.sample(),
            lobby = LobbyModel(),
        )
        model.lobby.nameResident("ELLIOT")
        // What both benches do at launch, and what makes the Perms screen never compose.
        model.go(ScreenId.Join)

        model.hostHome(model.homes.homes.first().name)

        assertNull(
            model.lobby.refusal,
            "a phone the OS grants everything was refused for rows it does not have: " +
                "${model.lobby.refusal}",
        )
        assertTrue(model.lobby.hosting, "a granted phone could not put its home on the air")
        assertEquals(ScreenId.Lobby, model.state.screen, "the host did not reach the lobby")
    }

    /**
     * **And the refusal it does give names the one row that is really missing, and only it.**
     *
     * The other half of the test above, and the half that stops the fix being *open the gate*. A
     * fresh read that reported an empty `missing` for everything would pass the assertion above
     * and ship a gate with nothing behind it. Same arrangement — no `refresh()` — with one row the
     * OS says no to.
     */
    @Test
    fun theFreshReadStillRefusesTheRowTheOsRefuses() {
        val model = FlowModel(
            perms = PermsModel(
                read = { if (it == DevicePermission.Camera) Grant.Refused else Grant.Granted },
            ),
            homes = SavedHomesModel.sample(),
            lobby = LobbyModel(),
        )
        model.lobby.nameResident("ELLIOT")
        model.go(ScreenId.Join)

        model.hostHome(model.homes.homes.first().name)

        assertTrue(!model.lobby.hosting, "a phone whose camera is refused went on the air")
        val refusal = assertNotNull(model.lobby.refusal, "the host was refused with no reason given")
        assertTrue(
            DevicePermission.Camera.label in refusal,
            "the refusal does not name the row the OS actually refused: '$refusal'",
        )
        val alsoNamed = DevicePermission.entries
            .filter { it != DevicePermission.Camera && it.label in refusal }
        assertEquals(
            emptyList(), alsoNamed,
            "the refusal names rows this phone was granted — the false refusal, one row narrower",
        )
    }

    /**
     * **D-309: a host whose NEARBY DEVICES reads ASKED is refused**, and the refusal names it.
     *
     * ASKED is a probe that heard nothing clear in two seconds (D-303). Seated on it, the phone
     * could not be measured all round (D-308), so it is held at the gate like any missing row. Same
     * path a thumb takes, with no `refresh()`.
     */
    @Test
    fun aHostWhoseNearbyDevicesReadsAskedIsRefused() {
        val model = FlowModel(
            perms = PermsModel(
                read = { if (it == DevicePermission.NearbyDevices) Grant.Asked else Grant.Granted },
            ),
            homes = SavedHomesModel.sample(),
            lobby = LobbyModel(),
        )
        model.lobby.nameResident("ELLIOT")
        model.go(ScreenId.Join)

        model.hostHome(model.homes.homes.first().name)

        assertTrue(!model.lobby.hosting, "a phone whose NEARBY DEVICES reads ASKED went on the air")
        val refusal = assertNotNull(model.lobby.refusal, "the host was refused with no reason given")
        assertTrue(
            DevicePermission.NearbyDevices.label in refusal,
            "the refusal does not name NEARBY DEVICES: '$refusal'",
        )
        assertEquals(
            emptyList(),
            DevicePermission.entries.filter { it != DevicePermission.NearbyDevices && it.label in refusal },
            "the refusal names rows this phone was granted: '$refusal'",
        )
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
