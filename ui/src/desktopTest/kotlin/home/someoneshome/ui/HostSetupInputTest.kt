package home.someoneshome.ui

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **The host-setup controls a synthetic click cannot reach, or reaches without proving anything.**
 *
 * `ScreenGraphTest` fires click *semantics actions* and `DeviceLayoutTest` measures pixels;
 * between them, a field nothing types into and a hold nothing holds would both pass. So would a
 * button that navigates correctly and changes nothing on the way — which is what every control in
 * host setup was until there was something behind it. These are the same gap `EditorSurfaceTest`
 * was written for.
 *
 * Every one drives the real input stack against the real screens, with a real [FlowModel] behind
 * them, so what is being proved is the whole path: finger, screen, actions layer, map, store.
 */
@OptIn(ExperimentalTestApi::class)
class HostSetupInputTest {

    /**
     * The screen under test, wired to a model the test can then interrogate.
     *
     * The model's own lobby is handed over rather than left to `Screen`'s default. It used to be
     * left, which was harmless while nothing on a host-setup screen read it — and would have
     * silently defeated the capacity-guidance tests, because the rendered screen would have been
     * asking a different lobby than the one the test set up.
     */
    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
            }
        }
        mainClock.autoAdvance = false
    }

    private fun onDelete(homes: SavedHomesModel = SavedHomesModel.sample()) =
        FlowModel(PanelState(screen = ScreenId.Delete), homes = homes)

    // ---- Hold to delete -----------------------------------------------------------------------

    /**
     * **Two seconds of a finger, and the home is gone from the phone as well as from the list.**
     *
     * The store is checked rather than the list, because a delete that updated the screen and not
     * the file is a house that comes back the next time the app opens — which the host would find
     * out about by hosting an evening in a home they thought they had thrown away.
     */
    @Test
    fun aFingerHeldForTwoSecondsDeletesTheHome() = runDesktopComposeUiTest(width = 300, height = 650) {
        val store = MemoryHomeStore()
        val homes = SavedHomesModel(store)
        homes.save(HomeEditorModel.bungalow().asSavedHome())
        val model = onDelete(homes)
        show(model)

        onNodeWithText("HOLD TO DELETE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(HOLD_MILLIS + 200L)

        assertTrue(homes.isEmpty, "the home survived a full hold")
        assertEquals(ScreenId.Maps, model.state.screen, "the host was left on a screen about nothing")
        assertEquals(
            emptyList(), SavedHomesModel(store).homes,
            "the list forgot it and the phone did not",
        )
    }

    /**
     * A tap does not delete a home, and neither does most of a hold.
     *
     * This is the whole reason the control is a hold: a phone in a pocket, a fumble in the dark,
     * or a thumb landing on the wrong half of the screen must not cost somebody fifteen minutes
     * of walking their own house.
     */
    @Test
    fun aTapAndMostOfAHoldBothDeleteNothing() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = onDelete()
        show(model)
        val before = model.homes.homes.size

        // The finger landing, on its own, checked before it is lifted — otherwise a hold that
        // fires the moment it is touched fails on the lift with "no such node", which sends the
        // next person looking at the wrong control.
        onNodeWithText("HOLD TO DELETE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(32)
        assertEquals(before, model.homes.homes.size, "the home went the moment a finger landed on it")

        onNodeWithText("HOLD TO DELETE").performTouchInput { up() }
        mainClock.advanceTimeBy(HOLD_MILLIS + 200L)
        assertEquals(before, model.homes.homes.size, "a tap deleted a home")

        onNodeWithText("HOLD TO DELETE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(HOLD_MILLIS - 400L)
        onNodeWithText("HOLD TO DELETE").performTouchInput { up() }
        mainClock.advanceTimeBy(HOLD_MILLIS + 200L)

        assertEquals(before, model.homes.homes.size, "letting go early deleted a home anyway")
        assertEquals(ScreenId.Delete, model.state.screen)
    }

    /** Let go and the bar goes back to nothing — a hold is not a total, it is a hold. */
    @Test
    fun lettingGoResetsTheHold() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = onDelete()
        show(model)
        val before = model.homes.homes.size

        // Most of a hold, released; then most of another. Together they are past two seconds and
        // must still delete nothing.
        repeat(2) { attempt ->
            onNodeWithText("HOLD TO DELETE").performTouchInput { down(center) }
            mainClock.advanceTimeBy(HOLD_MILLIS - 400L)
            // Checked with the finger still down, so a hold that carried its predecessor over
            // fails here by name rather than on the lift with "no such node".
            assertEquals(
                before, model.homes.homes.size,
                "hold ${attempt + 1} finished off the one before it",
            )
            onNodeWithText("HOLD TO DELETE").performTouchInput { up() }
            mainClock.advanceTimeBy(100)
        }

        assertEquals(before, model.homes.homes.size, "two part-holds added up to a delete")
    }

    // ---- Naming a home ------------------------------------------------------------------------

    /**
     * **The field names the home, and SAVE HOME puts that name on the phone.**
     *
     * The port drew THE BUNGALOW into this field as a picture of one. A host mapping their own
     * house typed nothing, saved nothing, and got a second copy of somebody else's bungalow.
     */
    @Test
    fun whatIsTypedIntoTheFieldIsWhatGetsSaved() = runDesktopComposeUiTest(width = 300, height = 650) {
        val store = MemoryHomeStore()
        val model = FlowModel(
            PanelState(screen = ScreenId.SaveName),
            homes = SavedHomesModel(store),
        )
        show(model)

        onNode(hasSetTextAction()).performTextReplacement("the annexe")
        mainClock.advanceTimeBy(100)
        assertEquals("THE ANNEXE", model.editor.name, "the field did not shout it")

        onNode(hasText("SAVE HOME") and hasClickAction()).performClick()
        mainClock.advanceTimeBy(100)

        assertEquals(listOf("THE ANNEXE"), model.homes.homes.map { it.name })
        assertEquals(ScreenId.HomeDetail, model.state.screen)
        val stored = assertNotNull(store.read())
        assertTrue("H THE ANNEXE" in stored.lines(), "the phone holds a different name: $stored")
    }

    /**
     * A refused save stays on the screen with the reason, which is why SAVE HOME hands its
     * navigation to the actions layer instead of naming a target.
     */
    @Test
    fun aNameAnotherHomeHoldsIsRefusedAndTheHostStaysPut() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val homes = SavedHomesModel.sample()
            homes.closeHome()
            val model = FlowModel(PanelState(screen = ScreenId.SaveName), homes = homes)
            show(model)

            onNode(hasSetTextAction()).performTextReplacement("the lake place")
            mainClock.advanceTimeBy(100)
            onNode(hasText("SAVE HOME") and hasClickAction()).performClick()
            mainClock.advanceTimeBy(100)

            assertEquals(ScreenId.SaveName, model.state.screen, "a refused save walked away anyway")
            assertEquals(3, homes.homes.size, "a fourth home appeared under a name already taken")
            onNodeWithText("A HOME IS ALREADY CALLED THE LAKE PLACE").assertExists()
        }

    /** An empty field is refused before a home with no name can be constructed. */
    @Test
    fun aHomeWithNoNameIsRefused() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = FlowModel(
            PanelState(screen = ScreenId.SaveName),
            homes = SavedHomesModel(MemoryHomeStore()),
        )
        show(model)

        onNode(hasSetTextAction()).performTextReplacement("")
        mainClock.advanceTimeBy(100)
        onNode(hasText("SAVE HOME") and hasClickAction()).performClick()
        mainClock.advanceTimeBy(100)

        assertTrue(model.homes.isEmpty)
        assertEquals("A HOME NEEDS A NAME", model.homes.refusal)
        assertEquals(ScreenId.SaveName, model.state.screen)
    }

    // ---- Registration, through the real screens -----------------------------------------------

    private fun openOn(screen: ScreenId, room: String): FlowModel {
        val model = FlowModel(PanelState(screen = screen))
        model.editor.open(room)
        return model
    }

    /**
     * **TAP A MARKER TO REMOVE IT**, which the screen has said since the day it was drawn and
     * which did nothing until there were cards to remove.
     *
     * The chip is keyed on the card, not on the shape it draws — two cards in one room draw two
     * different shapes, and a control that removed "the ring" rather than "this card" would be
     * removing something by a name the map is deliberately not keyed on.
     */
    @Test
    fun tappingAMarkerChipUnregistersThatCard() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = openOn(ScreenId.MarkerSheet, "GARAGE")
        val before = model.editor.cardsIn("GARAGE")
        assertEquals(2, before.size, "the fixture room no longer holds two cards")
        show(model)

        // The chips come first on the screen; the terminal's own × is the one after them.
        onAllNodes(hasText("×") and hasClickAction())[0].performClick()
        mainClock.advanceTimeBy(100)

        assertEquals(
            before.drop(1).map { it.card.id },
            model.editor.cardsIn("GARAGE").map { it.card.id },
            "the wrong card came off the sheet",
        )
    }

    /**
     * **Every outcome reaches the viewfinder the host is looking at.**
     *
     * Found by injection: deleting the readout from the scan screen altogether broke no test. The
     * whole unit is a card being offered to a map, and the visible half of it is one line in the
     * middle of a black rectangle — a scan that changed the map and said nothing, or was turned
     * away and said nothing, is a host walking out of a room with the wrong idea about what is in
     * it. So each branch is read back off the rendered screen rather than off the model.
     */
    @Test
    fun everyScanOutcomeIsSaidOnTheViewfinder() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = openOn(ScreenId.ScanMarker, "KITCHEN")
        show(model)

        // It landed.
        model.cardScanned(
            CardPayload.encode(MarkerCard(CardPayload.VERSION, MarkerShapes.require("bowtie"), MarkerId("CARD-01")))
        )
        mainClock.advanceTimeBy(100)
        onNodeWithText("KITCHEN . ADDED").assertExists()
        onNodeWithText("CARD-01").assertExists()

        // D-086: a second card carrying a shape the house already has.
        model.cardScanned(
            CardPayload.encode(MarkerCard(CardPayload.VERSION, MarkerShapes.require("ring"), MarkerId("CARD-99")))
        )
        mainClock.advanceTimeBy(100)
        onNodeWithText("THAT SHAPE IS ALREADY IN GARAGE").assertExists()

        // D-071: a fact about a piece of paper, so it may be said plainly.
        model.cardScanned("NOTACARD")
        mainClock.advanceTimeBy(100)
        onNodeWithText("NOT ONE OF OUR CARDS").assertExists()
    }

    /**
     * MOVE THE TERMINAL TO THIS ROOM: the card in the host's hand is put down here, and the room
     * it came from is left without one — which is what the screen said it would do.
     */
    @Test
    fun movingTheTerminalFromTheRefusalScreenRebindsIt() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = openOn(ScreenId.ScanMarker, "KITCHEN")
            val tCard = MarkerCard(CardPayload.VERSION, MarkerShapes.TERMINAL, MarkerId("SEEDT01"))
            model.cardScanned(CardPayload.encode(tCard))
            assertEquals(ScreenId.TermTaken, model.state.screen)
            show(model)

            onNode(hasText("MOVE THE TERMINAL TO KITCHEN") and hasClickAction()).performClick()
            mainClock.advanceTimeBy(100)

            assertEquals("KITCHEN", model.editor.terminal, "the terminal did not move")
            assertEquals(ScreenId.ScanMarker, model.state.screen)
        }

    /** REMOVE IT, and the home cannot be saved again until some room has a terminal. */
    @Test
    fun removingTheTerminalTakesItOffTheHome() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = openOn(ScreenId.TermRemove, "KITCHEN")
        assertEquals("HALL", model.editor.terminal)
        show(model)

        onNode(hasText("REMOVE IT") and hasClickAction()).performClick()
        mainClock.advanceTimeBy(100)

        assertEquals(null, model.editor.terminal, "REMOVE IT navigated and removed nothing")
        assertTrue(!model.editor.hasTerminal)
        assertEquals(ScreenId.MarkerSheet, model.state.screen)
    }

    /** KEEP IT is the same screen's other button and must not be the same act. */
    @Test
    fun keepingTheTerminalLeavesItWhereItIs() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = openOn(ScreenId.TermRemove, "KITCHEN")
        show(model)

        onNode(hasText("KEEP IT") and hasClickAction()).performClick()
        mainClock.advanceTimeBy(100)

        assertEquals("HALL", model.editor.terminal)
        assertEquals(ScreenId.MarkerSheet, model.state.screen)
    }

    // ---- The meeting card, through the real screens ---------------------------------------------

    /**
     * The sheet says where **both** reserved cards are, and it is the only screen that does.
     *
     * A host who cannot see that this home has a meeting card here is a host who scans a second
     * one — which is the exact refusal the row exists to prevent them meeting.
     */
    @Test
    fun theMarkerSheetSaysWhereBothReservedCardsAre() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = openOn(ScreenId.MarkerSheet, "GARAGE")
            show(model)

            onNodeWithText("TERMINAL").assertExists()
            onNodeWithText("IN HALL").assertExists()
            onNodeWithText("MEETING CARD").assertExists()
            onNodeWithText("IN LIVING").assertExists()
        }

    /**
     * The meeting card's own outcomes, read back off the viewfinder rather than off the model.
     *
     * **The token is asserted as well as the line, and that came from an injection that slept.**
     * Deleting the `MeetingToken` from the readout left the line and the id in place and broke no
     * test — so the host would have been shown a scan result with no mark on it at all, on the one
     * screen whose whole job is matching what the app says against the paper in their hand. The
     * `U` is the token's own label and nothing else on this screen carries it.
     */
    @Test
    fun theMeetingCardIsSaidOnTheViewfinderToo() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = openOn(ScreenId.ScanMarker, "KITCHEN")
            model.removeMeeting()
            model.go(ScreenId.ScanMarker)
            show(model)

            model.cardScanned(CardPayload.encode(meetingCard()))
            mainClock.advanceTimeBy(100)

            onNodeWithText("MEETING CARD . KITCHEN").assertExists()
            onNodeWithText("SEEDU01").assertExists()
            onNodeWithText("U").assertExists()
        }

    /**
     * And the terminal's, which had the same gap for the same reason — every test on this screen
     * read the words and none of them read the mark.
     */
    @Test
    fun theTerminalsTokenIsOnTheViewfinderToo() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = openOn(ScreenId.ScanMarker, "KITCHEN")
            model.removeTerminal()
            model.go(ScreenId.ScanMarker)
            show(model)

            model.cardScanned(
                CardPayload.encode(
                    MarkerCard(CardPayload.VERSION, MarkerShapes.TERMINAL, MarkerId("SEEDT01"))
                )
            )
            mainClock.advanceTimeBy(100)

            onNodeWithText("TERMINAL . KITCHEN").assertExists()
            onNodeWithText("T").assertExists()
        }

    @Test
    fun movingTheMeetingCardFromTheRefusalScreenRebindsIt() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = openOn(ScreenId.ScanMarker, "KITCHEN")
            model.cardScanned(CardPayload.encode(meetingCard()))
            assertEquals(ScreenId.MeetTaken, model.state.screen)
            show(model)

            onNodeWithText("This home has one meeting card and it is in").assertExists()
            onNode(hasText("MOVE THE MEETING CARD TO KITCHEN") and hasClickAction()).performClick()
            mainClock.advanceTimeBy(100)

            assertEquals("KITCHEN", model.editor.meeting, "the meeting card did not move")
            assertEquals(ScreenId.ScanMarker, model.state.screen)
        }

    @Test
    fun removingTheMeetingCardTakesItOffTheHome() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = openOn(ScreenId.MeetRemove, "KITCHEN")
            assertEquals("LIVING", model.editor.meeting)
            show(model)

            onNode(hasText("REMOVE IT") and hasClickAction()).performClick()
            mainClock.advanceTimeBy(100)

            assertEquals(null, model.editor.meeting, "REMOVE IT navigated and removed nothing")
            assertEquals(ScreenId.MarkerSheet, model.state.screen)
        }

    @Test
    fun keepingTheMeetingCardLeavesItWhereItIs() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = openOn(ScreenId.MeetRemove, "KITCHEN")
            show(model)

            onNode(hasText("KEEP IT") and hasClickAction()).performClick()
            mainClock.advanceTimeBy(100)

            assertEquals("LIVING", model.editor.meeting)
            assertEquals(ScreenId.MarkerSheet, model.state.screen)
        }

    // ---- The REVIEW gate, through the real screens (D-127) --------------------------------------

    /**
     * **Every missing requirement is named on the screen, not the first one that fails.**
     *
     * This is the U5 lesson applied to a gate: a refusal the host cannot read is a refusal that
     * sends them round the house guessing. Each of the three is read back off the rendered screen,
     * with the other two removed so the assertion cannot be satisfied by a screen that only ever
     * draws the terminal's paragraph.
     */
    @Test
    fun theGateScreenNamesEveryMissingRequirement() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(PanelState(screen = ScreenId.ReviewNeeds))
            model.editor.startNewHome("SOMEWHERE NEW")
            show(model)

            onNodeWithText("THIS HOME NEEDS 3 MORE THINGS").assertExists()
            onNodeWithText("SCAN THE CARD MARKED T").assertExists()
            onNodeWithText("SCAN THE CARD MARKED U").assertExists()
            onNodeWithText("8 MORE MARKERS").assertExists()
            // The number the host is working towards, on the screen that is refusing them.
            onNode(hasText("HOSTS UP TO NOBODY YET", substring = true)).assertExists()
        }

    /** One thing short reads as one thing, in the singular, and draws only that paragraph. */
    @Test
    fun theGateScreenNamesOnlyWhatIsActuallyMissing() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(PanelState(screen = ScreenId.ReviewNeeds))
            model.editor.removeMeeting()
            show(model)

            onNodeWithText("THIS HOME NEEDS ONE MORE THING").assertExists()
            onNodeWithText("SCAN THE CARD MARKED U").assertExists()
            onNodeWithText("SCAN THE CARD MARKED T").assertDoesNotExist()
            onNode(hasText("HOSTS UP TO 6", substring = true)).assertExists()
        }

    /**
     * The button carries the count and the route, and both come off the same verdict.
     *
     * A label saying the home is ready while the tap lands on the refusal screen — or the other
     * way round — is the failure this walks, and it is one a host meets at the end of an hour.
     */
    @Test
    fun reviewHomeGoesWhereItsLabelSays() = runDesktopComposeUiTest(width = 300, height = 650) {
        val passing = FlowModel(PanelState(screen = ScreenId.Editor))
        show(passing)
        onNode(hasText("REVIEW HOME") and hasClickAction()).performClick()
        mainClock.advanceTimeBy(100)
        assertEquals(ScreenId.SaveName, passing.state.screen)
    }

    @Test
    fun reviewHomeOnAShortHomeSaysHowManyThingsAndGoesToTheGate() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val short = FlowModel(PanelState(screen = ScreenId.Editor))
            short.editor.removeTerminal()
            short.editor.removeMeeting()
            show(short)

            onNode(hasText("REVIEW HOME . 2 THINGS MISSING") and hasClickAction()).performClick()
            mainClock.advanceTimeBy(100)
            assertEquals(ScreenId.ReviewNeeds, short.state.screen)
        }

    /** Capacity is on the last screen before the home is kept, and it is not a gate. */
    @Test
    fun theSaveScreenShowsWhatThisHomeHostsUpTo() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(PanelState(screen = ScreenId.SaveName))
            show(model)

            onNodeWithText("HOSTS UP TO").assertExists()
            onNodeWithText("6").assertExists()
            // And it refuses nothing: SAVE HOME is a live control on this screen either way.
            onNode(hasText("SAVE HOME") and hasClickAction()).assertExists()
        }

    @Test
    fun theHomeDetailScreenShowsWhatThatHomeHostsUpTo() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(PanelState(screen = ScreenId.HomeDetail))
            model.openSavedHome(HomeEditorModel.BUNGALOW)
            show(model)

            onNodeWithText("HOSTS UP TO 6").assertExists()
            onNode(hasText("HOST WITH THIS HOME") and hasClickAction()).assertExists()
        }

    // ---- The lobby's capacity guidance (D-125, D-127) --------------------------------------------

    /**
     * **More people than the home is sized for is a line, never a block.**
     *
     * LIGHTS OUT is asserted to still be there — the point of D-127's ruling is that capacity
     * guides and does not gate, and a test that only read the sentence would pass just as well
     * against a build that had quietly started refusing the round.
     */
    @Test
    fun aCrowdedLobbySaysSoAndStillArms() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = hostingWith(joined = 9)
        show(model)

        onNode(hasText("IT WILL BE CROWDED", substring = true)).assertExists()
        onNode(hasText("LIGHTS OUT")).assertExists()
    }

    /** A party that fits draws nothing. A line that only ever says "this is fine" is noise. */
    @Test
    fun aLobbyThatFitsSaysNothingAboutCapacity() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = hostingWith(joined = 6)
            show(model)

            onNode(hasText("IT WILL BE CROWDED", substring = true)).assertDoesNotExist()
        }

    /**
     * The guidance is the host's. A client has never been sent the home's marker count, so a
     * client drawing this line would be drawing a number it guessed.
     */
    @Test
    fun aClientsLobbyDrawsNoCapacityLine() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = FlowModel(
            PanelState(screen = ScreenId.Lobby),
            lobby = LobbyModel(link = MemoryLobbyLink(joined = 9, linesIn = 9)),
        )
        model.lobby.attachTo(NearbyHome("THE BUNGALOW", "10.0.0.2", 1234))
        show(model)

        onNode(hasText("IT WILL BE CROWDED", substring = true)).assertDoesNotExist()
    }

    /** The host's own lobby, with a home open and that many people standing in the hall. */
    private fun hostingWith(joined: Int): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Lobby),
            lobby = LobbyModel(link = MemoryLobbyLink(joined = joined, linesIn = joined)),
        )
        model.openSavedHome(HomeEditorModel.BUNGALOW)
        model.lobby.hostHome(HomeEditorModel.BUNGALOW)
        return model
    }

    private fun meetingCard(id: String = "SEEDU01") =
        MarkerCard(CardPayload.VERSION, MarkerShapes.MEETING, MarkerId(id))
}
