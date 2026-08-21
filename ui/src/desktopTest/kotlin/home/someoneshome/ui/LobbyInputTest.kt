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
 * The first test is the one that matters. This phone holds two pieces of text nobody else may see
 * — the name its owner typed and the line they handed the house — and the lobby is the screen
 * where showing either would feel most natural and be most wrong.
 */
@OptIn(ExperimentalTestApi::class)
class LobbyInputTest {

    private val secret = "i still have priya's spare key"

    private fun modelOn(
        screen: ScreenId,
        joined: Int = 6,
        linesIn: Int = 4,
        hosting: Boolean = true,
    ): Pair<FlowModel, MemoryLobbyLink> {
        val link = MemoryLobbyLink(joined = joined, linesIn = linesIn)
        val lobby = LobbyModel(
            MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))),
            link,
            hosting = hosting,
        )
        lobby.look()
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

    // ---- No names, no lines ------------------------------------------------------------------

    /**
     * **The lobby names nobody, and this phone knows two names it could have used.**
     *
     * The design's lobby shows counts. The model behind this screen is three integers and is
     * incapable of naming another player — but the phone's own resident name and its own one line
     * are right there beside it, and either would be a leak of a different kind on a screen six
     * people are looking over each other's shoulders at in a lit hall.
     */
    @Test
    fun theLobbyNamesNobodyThoughThisPhoneKnowsTwoNames() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val (model, _) = modelOn(ScreenId.Lobby)
            model.lobby.nameResident("ELLIOT")
            model.lobby.typeLine(secret)
            model.handOverLine()
            show(model)

            assertNothingSays("ELLIOT", "the lobby printed the name typed on this phone")
            assertNothingSays(secret, "the lobby printed the one line typed on this phone")
            for (word in secret.split(" ").filter { it.length > 4 }) {
                assertNothingSays(word, "the lobby printed '$word' from the one line")
            }
            // And it is not blank instead: the counts the house sent are on it.
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
            val (model, _) = modelOn(ScreenId.Lobby, joined = 6)
            show(model)

            onNodeWithText("UNKNOWN").assertExists()
            val seen = mutableListOf<String>()
            repeat(6) {
                onNodeWithText("INSIDERS").performClick()
                mainClock.advanceTimeBy(50)
                seen += model.lobby.insidersLabel
            }
            assertEquals(listOf("1", "2", "UNKNOWN", "1", "2", "UNKNOWN"), seen)
            assertFalse("3" in seen, "the control left the band six seats allow")
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
}
