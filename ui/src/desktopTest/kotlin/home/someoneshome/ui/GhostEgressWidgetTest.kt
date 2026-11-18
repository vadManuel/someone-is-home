package home.someoneshome.ui

import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.protocol.RoundMessage

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **The couch's Egress widget: the real number while there is one, and nothing when there is not.**
 *
 * `Ghost3` is the one surface in the game a player sits in front of for a whole round, so it is the
 * one place a widget cannot borrow *is this happening* from the screen it is drawn on. The living's
 * Egress is a screen the house pushes them to and the screen is the state; the couch's is a block
 * on a screen that was already there.
 *
 * Two things are being held here at once, and they fail in opposite directions. A widget that never
 * empties tells a player who is out that a crisis is running from lights-out onward, in the one
 * place the app promises them the truth. A widget that never fills takes away the one number the
 * design gives them (`gdd.md:1014`) — so both states are asserted, and neither can be reached by
 * deleting the other.
 *
 * **`EGRESS . TRUE` is what this replaces**, and it was two grammars in one shape: TRUE read as an
 * adjective here (the *true* Egress number) and as a **type** on the living's widget one screen
 * over, which draws `EGRESS . BEACON`. One of the two had to go and the type is the one the shape
 * already meant everywhere else.
 */
@OptIn(ExperimentalTestApi::class)
class GhostEgressWidgetTest {

    private fun couch(running: Boolean, type: String? = null) = PanelState(
        screen = ScreenId.Ghost3,
        outBy = OutBy.Revoked,
        egressRunning = running,
        egressType = type,
    )

    /** **While an Egress runs: the house's own type, and the number no living player may see.** */
    @Test
    fun aRunningEgressNamesItsTypeAndItsNumber() {
        val vals = PanelVals(couch(running = true, type = "TETHER"))
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) { Screen(couch(running = true, type = "TETHER")) }
            }
            onAllNodesWithText("EGRESS . TETHER").assertCountEquals(1)
            onAllNodesWithText(vals.egressPercent).assertCountEquals(1)
        }
    }

    /**
     * **With no Egress running the widget says nothing at all** — no percentage, no type, and not
     * the word EGRESS.
     *
     * This is the default state of the screen, which is what makes it the one worth asserting: a
     * phone with no house attached has had no Egress fired at it, and every other block on this
     * screen stands in the port's drawn value while it waits. The Egress widget deliberately does
     * not, because a fixture that drew a live crisis would be the bench asserting something about
     * the house.
     */
    @Test
    fun withNoEgressRunningTheWidgetIsEmpty() {
        val vals = PanelVals(couch(running = true, type = "BEACON"))
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(couch(running = false)) } }
            onAllNodesWithText("EGRESS", substring = true).assertCountEquals(0)
            onAllNodesWithText(vals.egressPercent).assertCountEquals(0)
        }
    }

    /**
     * **TRUE is not an Egress type and no longer reads like one.** The removal is asserted against
     * the type that took its place rather than against a bare absence: while an Egress runs the
     * widget names its type, so a screen that drew nothing at all would fail here too. *(It was
     * asserted against TRUE OCCUPANCY at the foot of the screen, until the mockup cut that line.)*
     */
    @Test
    fun theCouchNoLongerCallsAnEgressTrue() {
        for (running in listOf(true, false)) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(couch(running, type = "BEACON"))
                    }
                }
                onAllNodesWithText("EGRESS . TRUE").assertCountEquals(0)
                onAllNodesWithText("EGRESS . BEACON").assertCountEquals(if (running) 1 else 0)
            }
        }
    }

    /**
     * **The house's word ends the widget, and the type is left standing.**
     *
     * Two facts, two fields, and this is why: containment takes the crisis down and says nothing
     * about which one it was. A build that unset the type here would have one field doing both
     * jobs again, and the first thing to break would be a couch whose widget emptied because the
     * house happened to send the type last.
     */
    @Test
    fun containmentEmptiesTheWidgetAndKeepsWhichEgressItWas() {
        val model = FlowModel(couch(running = false))
        model.houseSaid(RoundMessage.EgressOpened(EgressType.Beacon, emptyList(), 120L, Haptic.Long, pulseFrom = 86_402_000L))
        assertTrue(model.state.egressRunning, "an opened Egress did not reach the couch's widget")

        model.houseSaid(RoundMessage.EgressContained(Haptic.Short))
        assertTrue(
            !model.state.egressRunning,
            "the Egress was contained and the couch is still reading a live percentage",
        )
        assertTrue(
            model.state.egressType == "BEACON",
            "containment forgot which Egress the house had run",
        )
    }
}
