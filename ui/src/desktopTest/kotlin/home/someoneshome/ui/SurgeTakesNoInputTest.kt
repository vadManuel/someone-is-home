package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.Sound
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-182 — no input from the player at any point of a Surge.**
 *
 * *Player input: None. Fully automatic, start to finish*, across all three phases: 1.5 s at full,
 * 1.0 s black, 2.5 s of the Boot render. The work in the player's hands is LOST. What this file
 * tries is the thing the ruling forbids: **a finger on the glass during the bright and the dark
 * phases**, while the Subroutine the Surge interrupted is still the screen underneath the lamp.
 *
 * Before D-313 both taps reached the screen. The slam is a lamp drawn over the panel, and a lamp
 * draws; it does not hold a finger. So a tap on Drift's lane in the bright phase **handed the
 * entry over** — the work the Surge was meant to eat went to the house to be graded — and STOP NOW
 * in the dark phase sent its report and walked the phone off the Subroutine mid-Surge. Drift
 * because a tap on its lane always hands an entry over, before the buzz or after it (D-165), so
 * the tap is a hand-over whenever in the Surge it lands.
 *
 * Driven with real pointers at the screen rather than through the model, because what is being
 * checked is that nothing a finger can reach does anything: a test that called the actions
 * directly would be asking the model a question the player cannot ask it.
 */
@OptIn(ExperimentalTestApi::class)
class SurgeTakesNoInputTest {

    private val hall = MarkerId("card-hall")

    private val order: List<OrderLine> = listOf(
        OrderLine.Known(
            index = 0,
            work = AssignedWork.Subroutine(SubroutineKind.Drift),
            done = false,
            at = listOf(Destination(hall, shape = "diamond", room = "HALL")),
        ),
    )

    private val surge = RoundMessage.Surged(peak = 100, black = 0, sound = Sound.Surge, haptic = Haptic.Short)

    /** A phone standing at [hall] with the house's Drift open in front of it, and its wire. */
    private fun holdingADrift(wire: MutableList<IntentMessage>): FlowModel {
        val phone = FlowModel(
            PanelState(screen = ScreenId.Scan),
            subroutines = SubroutineModel(send = { wire += it }),
        )
        phone.houseSaid(RoundMessage.WorkOrderIssued(order))
        phone.scanning(hall)
        phone.houseSaid(
            RoundMessage.ScanAnswered(
                SubroutineInstance(
                    entry = 0,
                    subroutine = SubroutineKind.Drift,
                    parameters = SubroutineModel.benchParameters(SubroutineKind.Drift),
                ),
            ),
        )
        phone.actions().beginSubroutine()
        check(phone.state.screen == ScreenId.SubDrift) { "the fixture never opened the Drift" }
        return phone
    }

    /**
     * **The control: outside a Surge the same tap hands the entry over.** Without it, a screen that
     * had stopped taking taps altogether would pass the two tests below.
     */
    @Test
    fun aTapOnTheLaneHandsTheDriftOverWhenNothingIsHappening() = runDesktopComposeUiTest(width = 300, height = 650) {
        val wire = mutableListOf<IntentMessage>()
        val phone = holdingADrift(wire)
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(phone) } }
        mainClock.advanceTimeByFrame()

        onNodeWithTag(DRIFT_LANE).performTouchInput { click(center) }
        mainClock.advanceTimeBy(100L)

        assertEquals(
            1, wire.filterIsInstance<IntentMessage.HandOverEntry>().size,
            "the fixture's tap on the lane handed nothing over, so the Surge tests below prove nothing",
        )
    }

    @Test
    fun aTapDuringTheBrightPhaseHandsNothingOver() = runDesktopComposeUiTest(width = 300, height = 650) {
        val wire = mutableListOf<IntentMessage>()
        val phone = holdingADrift(wire)
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(phone) } }
        mainClock.advanceTimeByFrame()

        phone.houseSaid(surge)
        mainClock.advanceTimeBy(SURGE_PEAK_MS / 3L)
        onNodeWithTag(DRIFT_LANE).performTouchInput { click(center) }
        mainClock.advanceTimeBy(100L)

        assertTrue(
            wire.none { it is IntentMessage.HandOverEntry },
            "a tap in the Surge's bright phase handed the Drift over — the work the Surge eats " +
                "went to the house to be graded (D-182: no input at any point): $wire",
        )
    }

    /**
     * **A gesture already under way when the Surge lands hands nothing over either.**
     *
     * The pane that holds new fingers off the glass cannot cut short a hold that began before it
     * appeared — a pointer already down keeps going to what it first landed on — so a Short whose
     * two seconds run out in the bright phase would still hand its entry over. Driven here as the
     * hold's own ending, which is the call its timer makes.
     */
    @Test
    fun aHoldThatRunsOutDuringTheSurgeHandsNothingOver() {
        val wire = mutableListOf<IntentMessage>()
        val phone = holdingADrift(wire)
        phone.houseSaid(surge)
        phone.actions().tapSubroutine(Subroutine.Short, 2)
        phone.actions().handOverSubroutine(Subroutine.Short)
        phone.actions().tapSubroutine(Subroutine.Drift, 3)
        phone.actions().handOverSubroutine(Subroutine.Drift)

        assertTrue(
            wire.none { it is IntentMessage.HandOverEntry },
            "a gesture that began before the Surge handed work over during it — the work the " +
                "Surge eats reached the house (D-182): $wire",
        )
    }

    @Test
    fun aTapDuringTheDarkPhaseFiresNothing() = runDesktopComposeUiTest(width = 300, height = 650) {
        val wire = mutableListOf<IntentMessage>()
        val phone = holdingADrift(wire)
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(phone) } }
        mainClock.advanceTimeByFrame()

        phone.houseSaid(surge)
        mainClock.advanceTimeBy(SURGE_PEAK_MS + SURGE_BLACK_MS / 2L)
        assertEquals(ScreenId.SubDrift, phone.state.screen, "the fixture is not in the dark phase")

        onNode(hasText("STOP NOW")).performTouchInput { click(center) }
        onNodeWithTag(DRIFT_LANE).performTouchInput { click(center) }
        mainClock.advanceTimeBy(100L)

        assertTrue(
            wire.isEmpty(),
            "a tap in the Surge's dark phase put something on the wire (D-182: no input at any " +
                "point): $wire",
        )
        assertEquals(
            ScreenId.SubDrift, phone.state.screen,
            "a tap in the Surge's dark phase moved the phone",
        )
    }
}
