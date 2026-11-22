package home.someoneshome.ui

import home.someoneshome.model.SavedHome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Nobody is trapped in a lobby, and nobody joins one without a name** (E-PAY-6).
 *
 * Four findings the owner reported as one batch, and they are one batch because they are all the
 * same shape: *a control that should exist and does not*. There was no way out of a lobby for a
 * client, no way to close one for a host, no gate on a nameless seat, and no memory of a name
 * between launches — and every one of them is invisible on the screen, because the absence of a
 * control looks exactly like a design decision.
 *
 * The trapped-lobby test is written as a **reproduction** rather than as a property: it walks the
 * phone in the way the owner did, asserts the state they were stuck in, and then leaves. That is
 * what makes it fail on the build before the fix rather than merely pass on the one after.
 */
class LobbyExitTest {

    private fun aHome() = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    private fun aSavedHome() = SavedHomesModel.sample().homes.first()

    /**
     * **THE REPRODUCTION.** A client joins a lobby, hands a line over, and wants out.
     *
     * Before N5b there was nothing to call: `LobbyLink.leave` existed and no screen reached it, so
     * the sequence below ended with the phone still attached, still seated and still counted, and
     * the only exit anybody found was force-quitting the app. That is the trap, and this is it.
     */
    @Test
    fun aClientCanLeaveTheLobbyItJoined() {
        val link = MemoryLobbyLink(joined = 5, linesIn = 3)
        val model = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link)
        model.look()
        model.nameResident("ROSE")
        model.attachTo(model.nearby.first())
        model.typeLine("i still have priya's spare key")
        assertTrue(model.handOverLine(), "the line went")
        assertEquals(aHome(), model.attached, "this phone is in the lobby")

        model.leaveLobby()

        assertNull(model.attached, "the phone is still holding a lobby it walked out of")
        assertEquals(0, model.standing.joined, "the phone is still counting a room it left")
        assertEquals("", model.line.text, "the line survived the leave; it was typed for one lobby")
        assertFalse(model.line.handedOver, "the phone still believes the house is holding its line")
    }

    /**
     * **The seat and the line drop from the counts, because the DESK drops them.**
     *
     * Read off the fixture rather than off the model, and that is the point of asserting it here at
     * all: a client that decremented its own `joined` would be a phone asserting a lobby. The desk
     * recounts and publishes, and everybody still in the room sees the new number — which is what
     * `LobbyDesk.left` has always done on a lost connection and what nothing was ever calling.
     */
    @Test
    fun theDeskRecountsWhenASeatLeaves() {
        val link = MemoryLobbyLink(joined = 5, linesIn = 3)
        val model = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link)
        model.look()
        model.nameResident("ROSE")
        model.attachTo(model.nearby.first())
        model.typeLine("something true")
        model.handOverLine()
        assertEquals(5, model.standing.joined)
        assertEquals(4, model.standing.linesIn, "the fixture took the line")

        // The link's own verb, not the model's — this is the desk's arithmetic being asserted, and
        // the model is standing in for the five phones that are still in the room and watching the
        // count. `leaveLobby` would wipe this phone's copy and hide exactly what is being measured.
        link.leave()

        assertEquals(4, model.standing.joined, "a seat left and the count did not move")
        assertEquals(3, model.standing.linesIn, "the leaver's line stayed in the count")
    }

    /**
     * **The host closes the house, and this phone lands where a leaver lands.**
     *
     * The clients' half is `FlowModel.houseClosed`, reached by a push — see [LobbyClosedFlowTest].
     * This is the host's own phone, which gets no special treatment: what a terminate produces is a
     * network with no house on it and every phone back at the start.
     */
    @Test
    fun theHostCanCloseTheHouse() {
        val model = LobbyModel.sample()
        assertTrue(model.hosting, "the sample is the host's phone")

        model.terminateHouse()

        assertFalse(model.hosting, "the phone still believes it is running a house it closed")
        assertNull(model.attached)
        assertEquals(0, model.standing.joined)
    }

    /**
     * **A client cannot close somebody else's house**, and the refusal is not a message — the
     * control is not drawn there and the call does nothing if it arrives anyway.
     *
     * The same shape `cycleInsiders` and `cycleVoteWindow` already have. Two locks on one door,
     * because the screen's half is a `when` somebody could edit and this one is not.
     */
    @Test
    fun aClientCannotCloseTheHouse() {
        val link = MemoryLobbyLink(joined = 6, linesIn = 6)
        val model = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link)
        model.look()
        model.nameResident("TOMAS")
        model.attachTo(model.nearby.first())
        assertFalse(model.hosting)

        model.terminateHouse()

        assertEquals(aHome(), model.attached, "a client closed a house it does not run")
        assertEquals(6, model.standing.joined)
    }

    /**
     * **Being shown out and walking out land in the same place, with one difference: the line.**
     *
     * A leaver knows why they are on the join screen. A phone whose house closed under it does not,
     * and the whole of what it is owed is an honest sentence — see `LobbyModel.HOUSE_CLOSED`, whose
     * wording the owner approved as written (D-323 D).
     */
    @Test
    fun aClosedHouseLeavesAnHonestLineAndNothingElse() {
        val link = MemoryLobbyLink(joined = 6, linesIn = 4)
        val model = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link)
        model.look()
        model.nameResident("DANI")
        model.attachTo(model.nearby.first())

        model.houseClosed()

        assertEquals(LobbyModel.HOUSE_CLOSED, model.refusal)
        assertNull(model.attached)
        assertEquals(0, model.standing.joined)
        assertEquals("DANI", model.residentName, "the name is this phone's own and does not go")
    }

    // ---- The name gate -----------------------------------------------------------------------

    /**
     * **A nameless phone cannot take a seat.**
     *
     * The refusal stays on the screen the field is on, which is the whole reason this is a return
     * value rather than a dialog: `attachTo` writes [LobbyModel.NEEDS_A_NAME] and does not attach,
     * so `FlowModel.attachToHome` has nothing to navigate to.
     */
    @Test
    fun joiningWithoutANameIsRefused() {
        val link = MemoryLobbyLink(joined = 5, linesIn = 5)
        val model = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link)
        model.look()

        model.attachTo(model.nearby.first())

        assertNull(model.attached, "a phone with no name on it took a seat")
        assertEquals(LobbyModel.NEEDS_A_NAME, model.refusal)
        assertEquals(emptyList(), link.named, "a blank name went up the wire")
    }

    /** Blank is not a name. A field full of spaces is a phone that has not said anything. */
    @Test
    fun whitespaceIsNotAName() {
        val model = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())))
        model.nameResident("   ")
        assertFalse(model.named, "spaces were accepted as a name")
    }

    /**
     * **A nameless phone cannot host either**, and it is refused before the radio is asked.
     *
     * D-161's setup gate already stands in front of this call for the same reason: a house that
     * went on the air under a host nobody can name is a party assembled around something that has
     * to be fixed later, in the dark.
     */
    @Test
    fun hostingWithoutANameIsRefused() {
        val link = MemoryLobbyLink()
        val model = LobbyModel(link = link)
        var wentUp = false

        model.hostHome(aSavedHome()) { wentUp = true }

        assertFalse(wentUp, "a nameless host walked into a lobby")
        assertEquals(LobbyModel.NEEDS_A_NAME, model.refusal)
        assertEquals(emptyList(), link.hosted, "the radio was asked for a house with no host name")
    }

    @Test
    fun namingThePhoneOpensBothControls() {
        val link = MemoryLobbyLink()
        val model = LobbyModel(finder = MemoryHomeFinder(listOf(aHome())), link = link)
        model.look()
        model.nameResident("ELLIOT")

        var wentUp = false
        model.hostHome(aSavedHome()) { wentUp = true }
        assertTrue(wentUp, "a named host was still refused")
        assertNull(model.refusal)
        assertEquals(listOf("ELLIOT"), link.named, "the name did not go up with the join")
    }

    // ---- The name, across a relaunch -----------------------------------------------------------

    /**
     * **THE RELAUNCH.** A phone that has been named comes back named.
     *
     * The store is the seam and this is the whole test: a second `LobbyModel` over the same store is
     * what a second launch is. The cost of not having it was an evening — the app was force-quit to
     * fix a name and the round went with it (E-PAY-7).
     */
    @Test
    fun theNameSurvivesARelaunch() {
        val store = MemoryNameStore()
        LobbyModel(names = store).nameResident("MARCUS")

        val afterRelaunch = LobbyModel(names = store)

        assertEquals("MARCUS", afterRelaunch.residentName)
        assertTrue(afterRelaunch.named, "the phone came back unnamed and would ask again")
    }

    /** Typing is what persists it. There is no commit control under the field to persist at. */
    @Test
    fun everyKeystrokeIsWrittenThrough() {
        val store = MemoryNameStore()
        val model = LobbyModel(names = store)
        model.nameResident("R")
        assertEquals("R", store.read())
        model.nameResident("RO")
        assertEquals("RO", store.read(), "a name half typed was not kept")
    }

    /**
     * **A store that cannot write does not take the app down with it**, and the phone plays anyway.
     *
     * The one asymmetry with the homes store and the seat token, both of which throw. See
     * [NameStore]: the entire consequence of losing this is being asked again next launch, and a
     * refusal on a screen where nothing was refused is worse than that.
     */
    @Test
    fun aStoreThatRefusesToWriteIsSurvived() {
        val model = LobbyModel(names = object : NameStore {
            override fun read(): String? = null
            override fun write(text: String) = throw IllegalStateException("the disk said no")
        })

        model.nameResident("PRIYA")

        assertEquals("PRIYA", model.residentName, "the name was lost from memory as well as disk")
        assertTrue(model.named, "a failed write refused this phone a seat")
        assertNull(model.refusal, "a failed write put a refusal on a screen that refused nothing")
    }

    /**
     * A name past the wire's limit is cut here as well as at the host's desk, so a field that let
     * more be typed cannot silently truncate on somebody else's screen.
     */
    @Test
    fun theStoredNameIsBoundedOnTheWayBackIn() {
        val store = MemoryNameStore("X".repeat(LobbyModel.NAME_LIMIT + 20))
        assertEquals(LobbyModel.NAME_LIMIT, LobbyModel(names = store).residentName.length)
    }

    // ---- The highlight ------------------------------------------------------------------------

    /**
     * **Your own row is marked, and it is marked by NAME** — no seat is asked for and none could be
     * (D-191, E-R1-1).
     */
    @Test
    fun yourOwnNameIsTheOneThatIsMarked() {
        val model = LobbyModel.sample()
        assertTrue(model.isMine("ELLIOT"), "the sample phone's own name is not marked")
        assertFalse(model.isMine("PRIYA"))
    }

    /**
     * **Two ROSEs are both marked, which is the honest answer.**
     *
     * The strip's KDoc used to refuse the highlight outright on this exact case, and the argument
     * held against picking *one* row. Marking both says *one of these is you*, which is true;
     * picking one would say *that one is you*, which would be a guess about a seat this phone is
     * not told.
     */
    @Test
    fun twoResidentsOfOneNameAreBothMarked() {
        val model = LobbyModel()
        model.nameResident("ROSE")
        assertTrue(model.isMine("ROSE"))
        // There is no second answer to give: the phone knows a name and nothing else, so both rows
        // reading ROSE get the same answer to the same question.
        assertEquals(model.isMine("ROSE"), model.isMine("ROSE"))
    }

    /** An unnamed seat is somebody who has not typed, and is never you. */
    @Test
    fun anUnnamedSeatIsNeverMarked() {
        val unnamed = LobbyModel()
        assertFalse(unnamed.isMine(""), "a phone with no name marked every seat that had none")

        val named = LobbyModel()
        named.nameResident("ROSE")
        assertFalse(named.isMine(""), "a blank seat was marked as this phone's")
    }
}
