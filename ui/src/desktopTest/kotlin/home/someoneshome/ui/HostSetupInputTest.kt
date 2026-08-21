package home.someoneshome.ui

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
 * **The two host-setup controls a synthetic click cannot reach.**
 *
 * `ScreenGraphTest` fires click *semantics actions* and `DeviceLayoutTest` measures pixels;
 * between them, a field nothing types into and a hold nothing holds would both pass. These are
 * the same gap `EditorSurfaceTest` was written for, on the two controls that decide whether a
 * home gets a name and whether one gets thrown away.
 *
 * Both drive the real input stack against the real screens, with a real [FlowModel] behind them,
 * so what is being proved is the whole path: finger, screen, actions layer, store.
 */
@OptIn(ExperimentalTestApi::class)
class HostSetupInputTest {

    /** The screen under test, wired to a model the test can then interrogate. */
    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes)
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
}
