package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **[ScreenGraph] against the screens themselves.**
 *
 * The graph is a transcription, and a transcription is a second copy of something. Two copies
 * agree on the day they are written and never again: someone retargets a button, the graph still
 * says what the button used to do, and every property `FlowTest` proves is proved about a game
 * that is no longer in the repo.
 *
 * So it is not reviewed against the screens, it is *read off* them. Each screen is rendered with
 * a navigator that records rather than navigates, every tap target it publishes is fired, and the
 * screens it asked for are compared with the screens the graph claims. A retargeted button fails
 * here, on the build, naming both.
 *
 * ### Why the semantics action and not a click
 *
 * `performClick` dispatches a pointer event at the centre of a node, so a control sitting under
 * another control is never reached and quietly contributes no edge — the graph would come out
 * *smaller* than the screen and agree with itself. Invoking the click action directly reaches
 * every target regardless of what is drawn on top of it.
 *
 * ### The state axes
 *
 * Both roles, both room types, markers on and off. Role is the one that matters: the springboard
 * is identical for both, and the Insider's egress tile is the single edge in the whole game that
 * one role can walk and the other cannot. Rendering one role would lose it.
 */
class ScreenGraphTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theTranscribedGraphIsWhatTheScreensActuallyDo() {
        val wrong = mutableListOf<String>()

        for (id in ScreenId.entries) {
            val asked = linkedSetOf<ScreenId>()
            for (role in PanelRole.entries) {
                for (roomType in RoomType.entries) {
                    for (markersOn in listOf(false, true)) {
                        runDesktopComposeUiTest(width = 600, height = 1300) {
                            setContent {
                                DeviceCanvas(insets = PanelInsets()) {
                                    Screen(
                                        PanelState(
                                            role = role,
                                            roomType = roomType,
                                            markersOn = markersOn,
                                        ).arrivingAt(id),
                                        PanelActions(nav = { asked += it }),
                                    )
                                }
                            }
                            val targets = onAllNodes(hasClickAction())
                            repeat(targets.fetchSemanticsNodes().size) { i ->
                                targets[i].performSemanticsAction(SemanticsActions.OnClick)
                            }
                        }
                    }
                }
            }
            val claimed = ScreenGraph.exitsOf(id)
            if (asked != claimed) {
                wrong += "$id — the screen reaches $asked, the graph says $claimed"
            }
        }

        wrong.forEach { println("GRAPH  $it") }
        assertEquals(
            emptyList(), wrong.toList(),
            "the screen graph and the screens disagree:" + wrong.joinToString("") { "\n  $it" },
        )
    }
}
