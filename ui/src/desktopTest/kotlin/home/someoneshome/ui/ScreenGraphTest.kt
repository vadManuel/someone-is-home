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
 * Both roles, markers on and off, and three plans. Role is the one that matters most: the
 * springboard is identical for both, and the Insider's egress tile is the single edge in the
 * whole game that one role can walk and the other cannot. Rendering one role would lose it.
 *
 * The plans are the second axis, and they exist because the host-setup screens ask the plan
 * questions that change which controls are drawn — is the held room stairs, does this home have
 * a terminal yet. Three named plans rather than a full cross of every editor flag: a cross of
 * things that do not interact costs renders and proves nothing extra, and each of these covers a
 * branch nothing else does.
 *
 * **Nothing here mutates the editor.** The actions passed in wire `nav` and nothing else, so
 * firing DELETE ROOM records the screen it goes to without deleting anything. That matters: the
 * targets are fetched once and fired by index, and a control that removed another control
 * mid-pass would leave this test reading a tree that had moved underneath it.
 */
class ScreenGraphTest {

    /**
     * The plans, each named for the branch it is here to reach.
     *
     * Built fresh per render rather than shared — a `HomeEditorModel` is mutable, and a fixture
     * shared across 672 renders is one bad tap away from being a different house halfway through.
     */
    private val plans: List<Pair<String, () -> HomeEditorModel>> = listOf(
        "an ordinary room, terminal placed" to { HomeEditorModel.bungalow() },
        "stairs held" to { HomeEditorModel.bungalow().apply { open("STAIRS") } },
        "no terminal anywhere" to {
            // Deleting the room the T card is in is the only way to reach a home with no
            // terminal through the editor's own API, which is the point: there is no flag.
            HomeEditorModel.bungalow().apply { open("HALL"); deleteHeld() }
        },
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theTranscribedGraphIsWhatTheScreensActuallyDo() {
        val wrong = mutableListOf<String>()

        for (id in ScreenId.entries) {
            val asked = linkedSetOf<ScreenId>()
            for (role in PanelRole.entries) {
                for ((_, plan) in plans) {
                    for (markersOn in listOf(false, true)) {
                        runDesktopComposeUiTest(width = 600, height = 1300) {
                            setContent {
                                DeviceCanvas(insets = PanelInsets()) {
                                    Screen(
                                        PanelState(role = role, markersOn = markersOn)
                                            .arrivingAt(id),
                                        PanelActions(nav = { asked += it }),
                                        plan(),
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
