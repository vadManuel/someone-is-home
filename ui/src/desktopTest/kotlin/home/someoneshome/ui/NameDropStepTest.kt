package home.someoneshome.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The NameDrop step: shown once per install, before a seat, and never again unless asked for**
 * (D-248; D-161's place, D-235's skip).
 *
 * Driven through [FlowHost] on the virtual clock, the way the app root drives it, with the OS as a
 * map behind [PermsModel]'s `read` seam and the install's file as a [MemoryNameDropStore] that the
 * test keeps hold of, so "a relaunch" is a second [FlowModel] built over the same store.
 */
@OptIn(ExperimentalTestApi::class)
class NameDropStepTest {

    private val everything get() = DevicePermission.entries.associateWith { Grant.Granted }.toMutableMap()

    /** Launch a phone at [start] over [os] and [store], let the self-test run out, then [then]. */
    private fun launch(
        store: NameDropStore,
        os: MutableMap<DevicePermission, Grant> = everything,
        start: ScreenId = ScreenId.Boot,
        then: ComposeUiTest.(FlowModel) -> Unit = {},
    ): ScreenId {
        var landed = start
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(
                PanelState(screen = start),
                perms = PermsModel(read = { os[it] ?: Grant.Unasked }),
                nameDrop = NameDropModel(store),
            )
            setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }
            mainClock.autoAdvance = false
            mainClock.advanceTimeBy(BOOT_MILLIS)
            waitForIdle()
            then(model)
            frame()
            landed = model.state.screen
        }
        return landed
    }

    /**
     * **GUARD: a fresh install the OS grants everything is shown the step, not the join screen.**
     * D-235's skip goes past PERMISSIONS; it must not go past this too.
     */
    @Test
    fun theSkipDoesNotWalkAFreshInstallPastTheStep() {
        assertEquals(
            ScreenId.NameDrop, launch(MemoryNameDropStore()),
            "a fresh install skipped the NameDrop step on its way to a seat",
        )
    }

    /** **GUARD: a fresh install that had to grant something reaches the step from CONTINUE.** */
    @Test
    fun aFreshInstallReachesTheStepFromContinue() {
        val os = everything.also { it[DevicePermission.Camera] = Grant.Unasked }
        val landed = launch(MemoryNameDropStore(), os) {
            assertEquals(ScreenId.Perms, it.state.screen, "a missing permission did not show PERMISSIONS first")
            os[DevicePermission.Camera] = Grant.Granted
            it.perms.refresh()
            frame()
            onNodeWithText("CONTINUE").performClick()
        }
        assertEquals(ScreenId.NameDrop, landed, "CONTINUE walked a fresh install past the NameDrop step")
    }

    /** **GUARD: DONE is stored, and a relaunch over the same file goes straight to join.** */
    @Test
    fun doneIsRememberedAcrossALaunch() {
        val store = MemoryNameDropStore()
        val first = launch(store) { onNodeWithTag(NAMEDROP_DONE).performClick() }
        assertEquals(ScreenId.Join, first, "DONE did not go on to the join screen")
        assertTrue(store.read(), "DONE was not written to the install's file")
        assertEquals(
            ScreenId.Join, launch(store),
            "a returning install that had confirmed NameDrop was stopped at the step again",
        )
    }

    /** **GUARD: a confirmed install is not stopped by CONTINUE either.** */
    @Test
    fun aConfirmedInstallIsNotStoppedByContinue() {
        val os = everything.also { it[DevicePermission.Bluetooth] = Grant.Unasked }
        val landed = launch(MemoryNameDropStore(off = true), os) {
            os[DevicePermission.Bluetooth] = Grant.Granted
            it.perms.refresh()
            frame()
            onNodeWithText("CONTINUE").performClick()
        }
        assertEquals(ScreenId.Join, landed, "a confirmed install was shown the NameDrop step again")
    }

    /**
     * **The one way back to the step, from a phone that skipped every setup screen** (D-248): the
     * join screen's back arrow reaches PERMISSIONS with nothing on the trail, and its NAMEDROP row
     * opens the step. Pressing nothing there changes nothing that was stored.
     */
    @Test
    fun aConfirmedInstallCanReachTheStepAgainFromPermissions() {
        val store = MemoryNameDropStore(off = true)
        val landed = launch(store) {
            assertEquals(ScreenId.Join, it.state.screen)
            onNodeWithTag(BACK_CHEVRON).performClick()
            frame()
            assertEquals(ScreenId.Perms, it.state.screen, "the join screen's back arrow did not reach PERMISSIONS")
            onNodeWithText("NAMEDROP").performClick()
        }
        assertEquals(ScreenId.NameDrop, landed, "the NAMEDROP row did not open the step")
        assertTrue(store.read())
    }

    /** A fresh install that has not pressed DONE has nothing stored, wherever it went. */
    @Test
    fun nothingIsStoredUntilDone() {
        val store = MemoryNameDropStore()
        launch(store)
        assertFalse(store.read(), "the step stored an answer nobody gave")
    }

    /** The clock is held, so a change reaches the glass only when a frame is run. */
    private fun ComposeUiTest.frame() {
        mainClock.advanceTimeBy(100)
        waitForIdle()
    }

    private companion object {
        /** `Flow.autoAdvance`'s Boot row, read rather than restated, plus a frame. */
        val BOOT_MILLIS: Long = Flow.autoAdvance.getValue(ScreenId.Boot).afterMillis.toLong() + 100L
    }
}
