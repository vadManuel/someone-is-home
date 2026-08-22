package home.someoneshome.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The lobby's controls, and the one thing this screen must never do.**
 *
 * `ScreenGraphTest` fires clicks and `DeviceLayoutTest` measures pixels; between them, a field
 * nothing types into and a screen quietly printing a name would both pass. These drive the real
 * input stack against the real screens with a real [FlowModel] behind them.
 *
 * The first two are the ones that matter. This phone holds two pieces of text and the lobby is the
 * screen where drawing either would feel most natural: **one of them belongs there** (D-115 — the
 * design's lobby lists who is here) and **the other must never appear on any screen but the one it
 * was typed on.** The pair is tested together on purpose, because "the lobby draws no text at all"
 * would pass the second one while failing the design.
 */
@OptIn(ExperimentalTestApi::class)
class LobbyInputTest {

    private val secret = "i still have priya's spare key"

    private fun modelOn(
        screen: ScreenId,
        joined: Int = 6,
        linesIn: Int = 4,
        hosting: Boolean = true,
        name: String = "ELLIOT",
    ): Pair<FlowModel, MemoryLobbyLink> {
        val link = MemoryLobbyLink(joined = joined, linesIn = linesIn)
        val lobby = LobbyModel(
            MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))),
            link,
            hosting = hosting,
        )
        lobby.look()
        // Named before attaching, because attaching is what carries the name up.
        lobby.nameResident(name)
        lobby.attachTo(lobby.nearby.first())
        return FlowModel(PanelState(screen = screen), lobby = lobby) to link
    }

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
            }
        }
        mainClock.autoAdvance = false
    }

    private fun DesktopComposeUiTest.assertNothingSays(text: String, why: String) {
        val found = onAllNodes(hasText(text, substring = true, ignoreCase = true))
            .fetchSemanticsNodes().size
        assertEquals(0, found, why)
    }

    // ---- Names yes, lines no -------------------------------------------------------------------

    /**
     * **The lobby lists everybody in it, by name** (D-115).
     *
     * Off the wire, not out of a fixture: the five who were already here came down in the house's
     * standing, and the sixth is this phone's own name, which reached the house by attaching and
     * came back around with the rest.
     */
    @Test
    fun theLobbyListsEverybodyWhoHasJoined() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, _) = modelOn(ScreenId.Lobby)
            show(model)

            for (name in listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS", "ELLIOT")) {
                onNodeWithText(name).assertExists("the lobby did not list $name")
            }
            onNodeWithText("6 JOINED").assertExists()
        }

    /** A phone that said nothing is a seat nobody has spoken for, not a seat that vanished. */
    @Test
    fun aResidentWhoTypedNoNameIsStillInTheRoom() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, _) = modelOn(ScreenId.Lobby, name = "")
            show(model)

            onNodeWithText("UNNAMED").assertExists("an unnamed seat was dropped from the lobby")
            onNodeWithText("6 JOINED").assertExists("and the count no longer agrees with the list")
        }

    /**
     * **The one line appears on no screen but the one it was typed on**, and the lobby is where
     * drawing it would feel most natural now that the lobby draws text again.
     *
     * The names being on this screen is what makes the test worth running: a lobby that printed
     * nothing would pass it while failing the design, so both halves are asserted here — the six
     * names present, the line and every fragment of it absent.
     */
    @Test
    fun theLobbyNeverPrintsTheOneLine() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, _) = modelOn(ScreenId.Lobby)
            model.lobby.typeLine(secret)
            model.handOverLine()
            show(model)

            assertNothingSays(secret, "the lobby printed the one line typed on this phone")
            for (word in secret.split(" ").filter { it.length > 4 }) {
                assertNothingSays(word, "the lobby printed '$word' from the one line")
            }
            // And it is not blank instead: the names and the counts the house sent are on it.
            onNodeWithText("ELLIOT").assertExists()
            onNodeWithText("6 JOINED").assertExists()
            onNodeWithText("5 OF 6 HANDED THEIRS OVER").assertExists()
        }

    // ---- Typing ------------------------------------------------------------------------------

    @Test
    fun theNameOnTheWayInIsRealTyping() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (model, _) = modelOn(ScreenId.Join)
        show(model)

        onNode(hasSetTextAction()).performTextReplacement("priya")
        assertEquals("PRIYA", model.lobby.residentName, "the name field types nothing")
    }

    /** A tap on a row attaches to *that* home, and goes into its lobby. */
    @Test
    fun tappingANearbyHomeAttachesToThatOne() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (model, _) = modelOn(ScreenId.Join)
        show(model)

        onNodeWithText("THE BUNGALOW").performClick()
        assertEquals("THE BUNGALOW", model.lobby.attached?.name)
        assertEquals(ScreenId.Lobby, model.state.screen)
    }

    /**
     * The line is typed as typed. `transform` is identity on this one field: a house name is a
     * label and a confession is a sentence, and the screen that asks somebody for something true
     * must not restyle it while they write it.
     */
    @Test
    fun theLineIsTypedAsWrittenAndGoesToTheHouseOnce() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, link) = modelOn(ScreenId.Secret)
            show(model)

            onNode(hasSetTextAction()).performTextReplacement(secret)
            assertEquals(secret, model.lobby.line.text, "the field shouted what was typed into it")

            onNodeWithText("HAND IT OVER").performClick()
            assertEquals(listOf(secret), link.received, "the house was handed nothing")
            assertEquals(ScreenId.Lobby, model.state.screen, "it did not go back to the lobby")
        }

    /**
     * **A blank line is refused and the screen stays put**, with the reason on it.
     *
     * Walking away would leave the player believing the house holds something it does not — and
     * the lobby's own count would agree with them, which is the version of this bug nobody would
     * find until the house quoted an empty line at somebody.
     */
    @Test
    fun aBlankLineIsRefusedOnTheScreenItWasNotTypedOn() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, link) = modelOn(ScreenId.Secret)
            show(model)

            onNodeWithText("HAND IT OVER").performClick()
            // The clock is held still so the caret's animation cannot outrun the test; a frame
            // has to be spent by hand for the refusal to be drawn.
            mainClock.advanceTimeBy(50)
            assertEquals(ScreenId.Secret, model.state.screen, "a blank line walked away")
            assertEquals(emptyList(), link.received, "a blank line reached the house")
            onNodeWithText("ONE LINE, AND MAKE IT REAL").assertExists()
        }

    // ---- The gate ----------------------------------------------------------------------------

    /**
     * **LIGHTS OUT is present and inert until every line is in**, rather than absent and then
     * appearing: a button that materialised when the last line arrived would move the layout
     * under a host's thumb at the moment they are about to press it.
     */
    @Test
    fun lightsOutIsInertUntilEveryLineIsIn() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (model, _) = modelOn(ScreenId.Lobby, joined = 6, linesIn = 4)
        show(model)

        val armed = onAllNodes(hasClickAction() and hasText("LIGHTS OUT", substring = true))
        assertEquals(0, armed.fetchSemanticsNodes().size, "the gate was open at four of six")
        onNodeWithText("LIGHTS OUT").assertExists()
        assertEquals(ScreenId.Lobby, model.state.screen)
    }

    @Test
    fun lightsOutArmsOnceEveryLineIsIn() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (model, _) = modelOn(ScreenId.Lobby, joined = 6, linesIn = 6)
        show(model)

        onNodeWithText("LIGHTS OUT").performClick()
        assertEquals(ScreenId.Armed, model.state.screen, "the gate was closed with every line in")
    }

    /** The host turns the lights off. A client is told, and gets no button that would do nothing. */
    @Test
    fun aClientIsToldRatherThanGivenTheHostsButton() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, _) = modelOn(ScreenId.Lobby, joined = 6, linesIn = 6, hosting = false)
            show(model)

            assertNothingSays("LIGHTS OUT", "a client was given the host's control")
            onNodeWithText("WAITING FOR THE HOST").assertExists()
        }

    // ---- D-103 at the control ------------------------------------------------------------------

    /**
     * The row walks D-103's band and comes back to UNKNOWN. Six seats admit one or two Insiders
     * and **the band clamps the setting itself**, so there is no tap sequence that reaches three.
     */
    @Test
    fun theInsiderRowWalksTheBandAndNeverLeavesIt() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            // Eight seats, because D-103's amendment pins five and six to a one-member band and a
            // control with one stop on it is not a control this test can walk.
            val (model, _) = modelOn(ScreenId.Lobby, joined = 8)
            show(model)

            onNodeWithText("UNKNOWN").assertExists()
            val seen = mutableListOf<String>()
            repeat(6) {
                onNodeWithText("INSIDERS").performClick()
                mainClock.advanceTimeBy(50)
                seen += model.lobby.insidersLabel
            }
            assertEquals(listOf("1", "2", "UNKNOWN", "1", "2", "UNKNOWN"), seen)
            assertFalse("3" in seen, "the control left the band eight seats allow")
        }

    /** A client's settings are a reading. Tapping the row moves nothing on anybody's phone. */
    @Test
    fun aClientsSettingsRowIsAReadingNotAControl() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, _) = modelOn(ScreenId.Lobby, joined = 6, hosting = false)
            show(model)

            val row = onAllNodes(hasClickAction() and hasText("INSIDERS", substring = true))
            assertTrue(
                row.fetchSemanticsNodes().isEmpty(),
                "a client was given a live control for the host's setting",
            )
            assertEquals("UNKNOWN", model.lobby.insidersLabel)
        }

    // ---- The vote window -----------------------------------------------------------------------

    /**
     * **The row on screen reads 45S**, which is the design's number (`gdd.md:412`).
     *
     * Read off the rendered screen rather than off the model, because the fault this replaces was
     * a screen and a flow table each holding their own 60 — agreeing with each other and with
     * nothing else. A model assertion would not have caught either of them.
     *
     * `45S` is asserted rather than the absence of `60S`: REVOKE COOLDOWN is legitimately 60S, so
     * a substring sweep for the old value would fail on a row that was never wrong.
     */
    @Test
    fun theVotingRowShowsTheDesignsWindow() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (model, _) = modelOn(ScreenId.Lobby, joined = 6)
        show(model)

        onNodeWithText("45S").assertExists()
    }

    /** The host taps it and the row follows, the same way the Insider row does. */
    @Test
    fun theHostCanMoveTheVoteWindow() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (model, _) = modelOn(ScreenId.Lobby, joined = 6)
        show(model)

        val seen = mutableListOf<String>()
        repeat(LobbyModel.VOTE_WINDOWS.size) {
            onNodeWithText("VOTING").performClick()
            mainClock.advanceTimeBy(50)
            seen += model.lobby.voteWindowLabel
        }
        assertEquals(listOf("60S", "90S", "30S", "45S"), seen)
        onNodeWithText("45S").assertExists("the row did not come back to the design's default")
    }

    /** And a client gets a reading, exactly as it does for the count. */
    @Test
    fun aClientGetsNoVoteWindowControl() = runDesktopComposeUiTest(width = 300, height = 650) {
        val (model, _) = modelOn(ScreenId.Lobby, joined = 6, hosting = false)
        show(model)

        val row = onAllNodes(hasClickAction() and hasText("VOTING", substring = true))
        assertTrue(
            row.fetchSemanticsNodes().isEmpty(),
            "a client was given a live control for the host's vote window",
        )
        onNodeWithText("45S").assertExists("a client lost the reading along with the control")
    }
}
