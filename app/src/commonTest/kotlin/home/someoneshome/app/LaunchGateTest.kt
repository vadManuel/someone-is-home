package home.someoneshome.app

import home.someoneshome.ui.Flow
import home.someoneshome.ui.ScreenGraph
import home.someoneshome.ui.ScreenId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A phone without Ultra Wideband lands on the gate, and nowhere else** (D-292).
 *
 * The routing every variant's root goes through, compiled against whichever variant this build
 * selected; `variantSweep*` runs it in the other two. The roots themselves are composables, and a
 * `when` over [Launch] in each is what this pins the meaning of.
 */
class LaunchGateTest {

    @Test
    fun aPhoneWithoutUwbIsSentToTheGate() {
        assertEquals(Launch.Gate, launchAs(uwb = false), "a phone without UWB was let past the launch gate")
        assertEquals(ScreenId.NoUwb, GATE_PANEL.screen, "the gate's panel is not the gate's screen")
    }

    @Test
    fun aPhoneWithUwbPlays() {
        assertEquals(Launch.Play, launchAs(uwb = true), "a phone with UWB was stopped at the launch gate")
    }

    /** Nothing on the gate goes anywhere: no control, no timer, no action, no swipe back. */
    @Test
    fun theGateHasNoWayForward() {
        assertEquals(emptySet(), ScreenGraph.exitsOf(ScreenId.NoUwb), "the gate publishes a control that goes somewhere")
        assertTrue(ScreenId.NoUwb !in Flow.autoAdvance, "the gate falls through on a timer")
        assertTrue(ScreenId.NoUwb !in Flow.viaActions, "the gate has an action that walks somewhere")
        assertTrue(ScreenId.NoUwb !in Flow.backable, "a swipe steps back off the gate")
    }

    /** And nothing walks onto it: a phone reaches the gate at launch or not at all. */
    @Test
    fun nothingWalksToTheGate() {
        val walkedTo = ScreenId.entries.filter { from ->
            ScreenId.NoUwb in ScreenGraph.exitsOf(from) ||
                Flow.autoAdvance[from]?.to == ScreenId.NoUwb ||
                ScreenId.NoUwb in Flow.viaActions[from].orEmpty()
        }
        assertEquals(emptyList(), walkedTo, "a screen walks to the launch gate")
    }
}
