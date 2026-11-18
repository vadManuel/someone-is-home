package home.someoneshome.ui

import home.someoneshome.model.SavedHome

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The home editor opens on nothing on a real phone, and on the bungalow in the harness alone**
 * (D-270, occupancy spec unit O1).
 *
 * The owner wants no fake data on a live phone, and the GDD's walk starts *"with Floor 0
 * (renameable), add more as you go. Nothing fixed"* (gdd:822, EP 4.3). A real phone is the one the
 * app handed a clock; the harness is the one handed none (D-255). Every route into the editor is
 * walked here: a fresh model, MAP A NEW HOME, EDIT THE PLAN with and without a home open, and
 * [Screen]'s own default.
 */
@OptIn(ExperimentalTestApi::class)
class EditorOpeningTest {

    private val now = WallTime(hour = 23, minute = 46, weekday = 2, day = 17, month = 11)

    /** Every room the design's bungalow paints, both storeys. */
    private val bungalowRooms: Set<String> =
        HomeEditorModel.bungalow().plan.floors.flatMap { storey -> storey.rooms.map { it.name } }.toSet()

    /** A real phone: the app hands it its own clock and its own (here empty) saved homes. */
    private fun phone(homes: SavedHomesModel = SavedHomesModel(), screen: ScreenId = ScreenId.Maps) =
        FlowModel(PanelState(screen = screen), homes = homes, wallClock = { now })

    private fun assertEmptyHome(editor: HomeEditorModel, where: String) {
        assertEquals(1, editor.plan.floors.size, "$where: not one storey")
        assertEquals(0, editor.plan.floors.sumOf { it.rooms.size }, "$where: rooms on a home nobody painted")
        assertEquals(0, editor.map.registrations.size, "$where: cards in a home nobody walked")
        assertNull(editor.map.terminal, "$where: a terminal in a home nobody walked")
        assertNull(editor.map.meeting, "$where: a meeting card in a home nobody walked")
        assertNull(editor.held, "$where: a room held in a home with none")
    }

    /** Every piece of text [screen] draws, through [Screen] as `FlowHost` calls it. */
    private fun drawn(state: PanelState, editor: HomeEditorModel?): List<String> {
        var texts: List<String> = emptyList()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    if (editor == null) Screen(state) else Screen(state, editor = editor)
                }
            }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        }
        return texts
    }

    private fun bungalowWordsIn(texts: List<String>): List<String> =
        texts.filter { text -> bungalowRooms.any { room -> text == room } || HomeEditorModel.BUNGALOW in text }

    // ---- 1 · A real phone ----------------------------------------------------------------------

    /**
     * **GUARD: a new home on a real phone opens empty — one storey, no rooms, no cards — by every
     * route into the editor, and draws none of the bungalow.**
     */
    @Test
    fun aNewHomeOnARealPhoneOpensEmpty() {
        // Before any route: what a host reaching the editor by the picker, or by EDIT THE PLAN with
        // nothing open, would find.
        val fresh = phone()
        assertEquals(false, PanelVals(fresh.state).renderHarness, "a phone with a clock was taken for the harness")
        assertEmptyHome(fresh.editor, "a fresh real phone")
        fresh.editOpenHome()
        assertEmptyHome(fresh.editor, "EDIT THE PLAN with no home open")

        // MAP A NEW HOME, the route the GDD's walk starts on.
        val mapping = phone()
        mapping.mapNewHome()
        assertEmptyHome(mapping.editor, "MAP A NEW HOME")
        assertEquals("HOME 1", mapping.editor.name)
        assertEquals(HomeEditorModel.GROUND, mapping.editor.floorName)

        // And MAP A NEW HOME after a saved home was open: the walk starts over, nothing carried.
        val after = phone(SavedHomesModel(MemoryHomeStore()).apply { save(kept()) })
        after.openSavedHome(KEPT)
        after.editOpenHome()
        after.mapNewHome()
        assertEmptyHome(after.editor, "MAP A NEW HOME after a saved home")

        // On the glass, through the model's own editor and through Screen's default alike.
        val onScreen = PanelState(screen = ScreenId.Editor, wallTime = now)
        for ((how, texts) in listOf(
            "the model's editor" to drawn(mapping.state.copy(screen = ScreenId.Editor), mapping.editor),
            "Screen's default" to drawn(onScreen, editor = null),
        )) {
            assertTrue(texts.any { "0 ROOMS . 0 MARKERS" in it }, "$how did not count an empty storey: $texts")
            assertEquals(emptyList(), bungalowWordsIn(texts), "$how drew the bungalow on a real phone")
        }
    }

    // ---- 2 · The harness -----------------------------------------------------------------------

    /** **GUARD: the render harness — no clock, no house — still opens on the design's bungalow.** */
    @Test
    fun theHarnessStillDrawsTheMockup() {
        val harness = FlowModel(PanelState(screen = ScreenId.Editor))
        assertEquals(true, PanelVals(harness.state).renderHarness)
        assertEquals(HomeEditorModel.BUNGALOW, harness.editor.name, "the harness lost its bungalow")
        assertEquals(HomeEditorModel.bungalow().asSavedHome(), harness.editor.asSavedHome(), "the harness lost its bungalow")
        for ((how, texts) in listOf(
            "the model's editor" to drawn(harness.state, harness.editor),
            "Screen's default" to drawn(PanelState(screen = ScreenId.Editor), editor = null),
        )) {
            assertTrue(texts.any { HomeEditorModel.BUNGALOW in it }, "$how lost the mockup's name: $texts")
            assertTrue(texts.any { "5 ROOMS . 5 MARKERS" in it }, "$how lost the mockup's counts: $texts")
        }
    }

    // ---- 3 · A saved home ----------------------------------------------------------------------

    /**
     * **GUARD: a saved home opens as saved — plan, cards, terminal, meeting card and name — and
     * SAVE HOME writes back exactly what was read**, on a real phone whose editor started empty.
     */
    @Test
    fun aSavedHomeRoundTrips() {
        val store = MemoryHomeStore()
        SavedHomesModel(store).save(kept())

        // Read off the file, the way a phone opening tomorrow does.
        val phone = phone(SavedHomesModel(store), screen = ScreenId.SaveName)
        assertEmptyHome(phone.editor, "the real phone before a home is opened")
        phone.openSavedHome(KEPT)
        phone.editOpenHome()
        assertEquals(kept(), phone.editor.asSavedHome(), "EDIT THE PLAN did not open the home as saved")

        phone.saveHome()
        assertEquals(ScreenId.HomeDetail, phone.state.screen, "the save was refused: ${phone.homes.refusal}")
        assertEquals(listOf(kept()), SavedHomesModel(store).homes, "the file does not hold what was opened")
    }

    // ---- 4 · Saving an empty home --------------------------------------------------------------

    /**
     * **GUARD: an empty home cannot be saved from the editor; REVIEW HOME sends it to the screen
     * that names what it lacks** (D-126: every gate lives at REVIEW; D-127: the one gate is a
     * terminal, a meeting card and eight markers). An empty plan has no room to hold a card, so it
     * is short all three, and the file is left untouched.
     */
    @Test
    fun anEmptyHomeGoesToReviewAndIsNotSaved() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val store = MemoryHomeStore()
        val phone = phone(SavedHomesModel(store))
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(phone.state, phone.actions(), phone.editor, phone.homes, phone.lobby)
            }
        }

        onNodeWithText("MAP A NEW HOME").performClick()
        waitForIdle()
        assertEquals(ScreenId.Editor, phone.state.screen)
        assertEquals(3, phone.editor.review.missing.size)

        onNodeWithText("REVIEW HOME . 3 THINGS MISSING").performClick()
        waitForIdle()
        assertEquals(ScreenId.ReviewNeeds, phone.state.screen, "an empty home went on to SAVE HOME")
        assertTrue(SavedHomesModel(store).isEmpty, "an empty home reached the phone's file")
    }

    private fun kept(): SavedHome = HomeEditorModel.bungalow().asSavedHome(KEPT)

    private companion object {
        const val KEPT = "SOMEWHERE KEPT"
    }
}
