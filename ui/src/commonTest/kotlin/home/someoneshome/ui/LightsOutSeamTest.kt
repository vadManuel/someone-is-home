package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **LIGHTS OUT reaches something now** — the `ui` half of E-PAY-1.
 *
 * `FlowModel.lightsOut`'s entire body was `push(ScreenId.Armed)`. It walked the host's own screen
 * and told nobody, which was correct while there was no loop to tell and is the reason five seats
 * once stood in a lobby forever behind a press that landed. The seam is the fix, and this is what
 * holds it: **the press is reported exactly once, and nothing else on this model reports it.**
 *
 * ### What is under test is the ACT, not the screen
 *
 * A round armed on *arriving at* [ScreenId.Armed] would arm on a route that never consulted the
 * gate — the cheat picker walks every `ScreenId` and `Armed` is one of them, which the payoff run
 * recorded as *a jump to ARMED is indistinguishable from a gate that failed*. So the second test
 * puts the phone on that screen by every other route this model has and asserts the seam stayed
 * quiet.
 */
class LightsOutSeamTest {

    /** The press is reported, and the phone walks to ARMED when the house answers that it armed. */
    @Test
    fun theHoldReportsThePressOnce() {
        var pressed = 0
        val model = FlowModel(PanelState(screen = ScreenId.Lobby), onLightsOut = { armed -> pressed++; armed() })

        model.lightsOut()

        assertEquals(1, pressed, "the host held LIGHTS OUT and nothing was told about it")
        assertEquals(
            ScreenId.Armed, model.state.screen,
            "the house armed and the screen did not follow it",
        )
    }

    /**
     * **A press the house refuses leaves the host on its lobby** (D-226).
     *
     * On 2026-11-16 the host's phone locked in the lobby, every seat dropped, and the house said
     * `WILL NOT ARM — 0 SEAT(S)` while the screen said ARMED: the walk happened on the press, and
     * the house was told after. The seam is now handed the walk and the house decides whether it
     * happens. A refusal must leave nothing on the screen but the lobby that is drawing why.
     */
    @Test
    fun aPressTheHouseRefusesNeverShowsArmed() {
        var pressed = 0
        val model = FlowModel(PanelState(screen = ScreenId.Lobby), onLightsOut = { _ -> pressed++ })

        model.lightsOut()

        assertEquals(1, pressed, "the press was not reported")
        assertEquals(ScreenId.Lobby, model.state.screen, "the house refused and the screen said ARMED anyway")
    }

    /**
     * **Arriving on ARMED any other way arms nobody.**
     *
     * [FlowModel.jump] is the cheat picker's route and [FlowModel.housePushed] is the house's —
     * neither is a person holding a button for two seconds, and a round is started by exactly one
     * of the three.
     */
    @Test
    fun landingOnArmedByAnyOtherRouteReportsNothing() {
        var pressed = 0
        val model = FlowModel(onLightsOut = { _ -> pressed++ })

        model.jump(model.state.arrivingAt(ScreenId.Armed))

        assertEquals(ScreenId.Armed, model.state.screen, "the picker did not reach the screen")
        assertTrue(pressed == 0, "a jump to ARMED armed a round the gate was never asked about")
    }
}
