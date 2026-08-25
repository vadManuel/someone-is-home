package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The torch reaches something now** — the `ui` half of E-PAY-5's camera finding.
 *
 * `FlowModel.toggleTorch`'s entire body was `state = state.copy(torch = !state.torch)`. It moved a
 * colour value in `Amber` — the wash over the viewfinder and the label beside the button — and
 * reached no hardware at all, on a screen whose whole purpose is reading a printed card in a hall
 * with the lights on. The owner's words were that the torch button reaches no hardware.
 *
 * [LightsOutSeamTest]'s shape exactly, and for the same reason: what is under test is **the report**
 * — the press leaving this layer — because what the LED does about it is `platform`'s and needs a
 * phone. The screen still moves, and it moves first; see `toggleTorch`.
 */
class TorchSeamTest {

    /** Each press is reported, carrying the state the host chose, and the screen agrees with it. */
    @Test
    fun eachPressIsReportedWithTheStateTheHostChose() {
        val asked = mutableListOf<Boolean>()
        val model = FlowModel(onTorch = { asked += it })

        model.toggleTorch()
        model.toggleTorch()
        model.toggleTorch()

        assertEquals(
            listOf(true, false, true), asked,
            "the torch button reached nothing — which is what shipped, with the wash on screen " +
                "changing colour over a lens that never lit",
        )
        assertTrue(model.state.torch, "the screen and the seam disagree about the torch")
    }

    /**
     * **Nothing else on this model touches the torch.**
     *
     * Stated separately because the failure it stands against is not a missing call but an extra
     * one: a seam fired from somewhere other than the host's own press would be a light nobody
     * authored, and the screens swept here include the in-round scan.
     */
    @Test
    fun nothingButThePressReportsATorch() {
        val asked = mutableListOf<Boolean>()
        val model = FlowModel(onTorch = { asked += it })

        model.jump(model.state.arrivingAt(ScreenId.ScanMarker))
        model.jump(model.state.arrivingAt(ScreenId.Scan))
        model.toggleMarkers()

        assertEquals(
            emptyList(), asked,
            "something other than the host's own button lit the torch: walking onto a scan screen " +
                "must never do it, and the in-round one is a phone in a dark house",
        )
    }
}
