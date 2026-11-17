package home.someoneshome.ui

import home.someoneshome.model.SignalGraph
import home.someoneshome.model.SubroutineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **A tap on a node the house did not deal does nothing** (H3 of the 2026-11-17 hunt, D-252).
 *
 * D-238's graphs can hold seven nodes. The bench tapped node indices 0 to 7, the eighth went onto
 * the route, and `SignalTraceScreen`'s canvas read `wiring.nodes[7]` for it: the 4-seat dry run at
 * seed 20261150 failed with an `ArrayIndexOutOfBoundsException`. A finger cannot tap a node that is
 * not drawn, so the route only ever holds nodes the wiring has, and the canvas never reads past it.
 */
class SignalTraceBoundsTest {

    @Test
    fun aTapOutsideTheGraphLeavesTheRouteAlone() {
        val seed = (0 until 2_000).firstOrNull { SignalGraph.of(listOf(it)).nodes.size == 7 }
        assertNotNull(seed, "no seed under 2000 deals a seven-node graph, so this proves nothing")
        val model = SubroutineModel()
        model.deal(SubroutineKind.SignalTrace, listOf(seed))
        assertEquals(7, model.wiring.nodes.size)

        model.tap(Subroutine.SignalTrace, 7)
        model.tap(Subroutine.SignalTrace, -1)
        assertEquals(emptyList(), model.trace.walked, "a node the house never dealt went onto the route")

        // And a real node still walks, so the guard is not a defunct control.
        model.tap(Subroutine.SignalTrace, 0)
        model.tap(Subroutine.SignalTrace, 7)
        model.tap(Subroutine.SignalTrace, 6)
        assertEquals(listOf(0, 6), model.trace.walked, "the dealt nodes stopped walking")
    }
}
