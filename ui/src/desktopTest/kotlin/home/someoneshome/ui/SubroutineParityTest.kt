package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Rule 8, as something that can fail: every Subroutine ships with its fake, and the fake is the
 * same screen.**
 *
 * An Insider has no assigned Subroutines and no action of theirs advances System Integrity — but
 * their phone opens a Subroutine at a marker on their fake list and runs it: real UI, real
 * progress, real completion, writing nothing. *Ten Subroutines with three forgotten fakes breaks
 * screen parity, and nobody finds out until somebody glances at an Insider's phone in the dark.*
 *
 * The way that stays true is not diligence. It is that there is **no second screen and no role
 * anywhere in the interaction**: `SubroutineModel` holds no `isFake`, `PanelActions` passes no
 * role, and the three screens read neither. So the claim to check is the strongest form of it —
 * given the same input, the two roles render the same pixels — and it is checked at every step of
 * the input rather than only at the end, because a difference that appears halfway through a
 * sequence and closes again is exactly the sort a final-state check would miss.
 *
 * ### Pixels, not semantics
 *
 * A semantics comparison passes on two screens that say the same words in different colours, and
 * colour is the whole language here: four luminance steps of amber, no hue, and a Subroutine
 * screen's brightness is its light signature. A tell in this game is far more likely to be *a
 * shade* than *a word*.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class SubroutineParityTest {

    /** Large enough that the grid and the dots are drawn at real size, not collapsed. */
    private val width = 300
    private val height = 650

    /**
     * One more tap on [subroutine], made through the actions layer rather than on the model, so
     * this drives the path a finger drives.
     */
    private fun drive(model: SubroutineModel, subroutine: Subroutine, step: Int) {
        val actions = PanelActions(
            tapSubroutine = { which, at ->
                when (which) {
                    Subroutine.Handshake -> model.handshake.enter(at)
                    Subroutine.Replay -> model.replay.enter(at)
                    Subroutine.ParityCheck -> model.parity.choose(at)
                    else -> Unit
                }
            },
        )
        actions.tapSubroutine(subroutine, step)
    }

    /** The Subroutine's screen, in [role], after [steps] further taps on a fresh entry. */
    private fun shot(subroutine: Subroutine, role: PanelRole, steps: Int): BufferedImage {
        var image: BufferedImage? = null
        runDesktopComposeUiTest(width = width, height = height) {
            val model = SubroutineModel()
            repeat(steps) { drive(model, subroutine, it) }
            setContent {
                Box(Modifier.fillMaxSize()) {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(
                            PanelState(screen = subroutine.screen!!, role = role),
                            subroutines = model,
                        )
                    }
                }
            }
            image = onRoot().captureToImage().toAwtImage()
        }
        return requireNonNull(image)
    }

    private fun requireNonNull(image: BufferedImage?): BufferedImage =
        image ?: error("the screen rendered nothing at all")

    /** How many pixels differ, and the first place they do. */
    private fun diff(a: BufferedImage, b: BufferedImage): Pair<Int, String?> {
        var count = 0
        var first: String? = null
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    count++
                    if (first == null) first = "($x,$y)"
                }
            }
        }
        return count to first
    }

    /**
     * **The Insider's Subroutine is the Resident's Subroutine, pixel for pixel, at every step.**
     *
     * Injecting the bug this exists for means giving any of the three screens something that reads
     * `vals.insider` — a different intensity on a returned beat, an extra line under the grid, a
     * button that dims a step earlier. All of them are invisible in review and all of them fail
     * here, naming the Subroutine and the step.
     */
    @Test
    fun theInsidersSubroutineIsTheResidentsSubroutinePixelForPixel() {
        val wrong = mutableListOf<String>()

        for (subroutine in Subroutine.built) {
            // Zero taps is the screen a player walks onto; each step after it is one more finger.
            // The last is past the point every entry of the three has gone to the house, so the
            // handed-over state is compared too.
            for (steps in 0..SubroutineModel.HANDSHAKE_BEATS + 1) {
                val resident = shot(subroutine, PanelRole.Resident, steps)
                val insider = shot(subroutine, PanelRole.Insider, steps)
                val (count, at) = diff(resident, insider)
                if (count > 0) {
                    wrong += "${subroutine.label} after $steps tap(s) — $count pixels differ by " +
                        "role, first at $at"
                }
            }
        }

        wrong.forEach { println("FAKE  $it") }
        assertTrue(
            wrong.isEmpty(),
            "a Subroutine is not the same screen for both roles, so somewhere there are two " +
                "Subroutines being kept in step by hand:" + wrong.joinToString("") { "\n  $it" },
        )
    }

    /**
     * **No Subroutine screen ever says whether you were right, in any state, for either role.**
     *
     * The verdict is the house's — the entry goes back as an Intent and the server verifies it
     * (D-042) — and there is nothing on the device that could form an opinion, because
     * [SequenceEntry] and [ChoiceEntry] have no field for the answer to sit in. This is the same
     * claim read off the screens rather than off the types, and it is a sweep for a reason: the way
     * this breaks is somebody adding a helpful line, not somebody rewriting the model.
     *
     * The words are the ones that could only ever be a judgement. *Waiting* is not among them and
     * is on screen deliberately: it is the honest state, and the honest state is what stops
     * somebody filling the silence with a guess.
     */
    @Test
    fun noSubroutineScreenEverSaysWhetherTheEntryWasRight() {
        val verdicts = listOf(
            "CORRECT", "INCORRECT", "WRONG", "MISMATCH", "FAILED", "FAILURE", "PASSED",
            "SUCCESS", "COMPLETE", "ACCEPTED", "REJECTED", "VERIFIED", "TRY AGAIN",
        )
        val wrong = mutableListOf<String>()

        for (subroutine in Subroutine.built) {
            for (steps in 0..SubroutineModel.HANDSHAKE_BEATS + 1) {
                for (role in PanelRole.entries) {
                    runDesktopComposeUiTest(width = width, height = height) {
                        val model = SubroutineModel()
                        repeat(steps) { drive(model, subroutine, it) }
                        setContent {
                            DeviceCanvas(insets = PanelInsets()) {
                                Screen(
                                    PanelState(screen = subroutine.screen!!, role = role),
                                    subroutines = model,
                                )
                            }
                        }
                        for (verdict in verdicts) {
                            val nodes = onAllNodes(hasText(verdict, substring = true))
                            if (nodes.fetchSemanticsNodes().isNotEmpty()) {
                                wrong += "${subroutine.label}/$role after $steps tap(s) says " +
                                    "\"$verdict\""
                            }
                        }
                    }
                }
            }
        }

        wrong.forEach { println("VERDICT  $it") }
        assertTrue(
            wrong.isEmpty(),
            "a Subroutine screen adjudicated its own entry, which is the house's answer and the " +
                "one thing that must not differ between a real Subroutine and a fake:" +
                wrong.joinToString("") { "\n  $it" },
        )
    }

    /**
     * **The parity grid marks the cell the player chose, and never more than one.**
     *
     * Two marks would mean the screen had started marking something other than the choice, and the
     * only other thing on that screen worth marking is the answer. The grid it draws does not
     * contain the answer ([ParityGrid]), so this failing means somebody put it back.
     */
    @Test
    fun theParityGridCarriesExactlyTheOneMarkThePlayerMade() {
        for (steps in 0..2) {
            runDesktopComposeUiTest(width = width, height = height) {
                val model = SubroutineModel()
                repeat(steps) { drive(model, Subroutine.ParityCheck, it) }
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(
                            PanelState(screen = ScreenId.SubParity),
                            subroutines = model,
                        )
                    }
                }
                val marks = onAllNodesWithTag(PARITY_MARK, useUnmergedTree = true)
                    .fetchSemanticsNodes().size
                assertEquals(
                    if (steps == 0) 0 else 1, marks,
                    "after $steps tap(s) the grid carries $marks marks",
                )
            }
        }
    }

    /**
     * **A tap lights the thing you touched, and moves the phone nowhere.**
     *
     * The echo is the whole content of these screens and the one thing `ui` may draw without
     * asking anybody. What it must never be is a step in a route: whether the work landed is the
     * house's answer, so no tap inside a Subroutine navigates — the only control that does is STOP
     * NOW, which is you walking away rather than the phone deciding you are finished.
     */
    @Test
    fun aTapEchoesAndNavigatesNowhere() = runDesktopComposeUiTest(width = width, height = height) {
        val model = SubroutineModel()
        val asked = mutableListOf<ScreenId>()
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(
                    PanelState(screen = ScreenId.SubReplay),
                    PanelActions(
                        nav = { asked += it },
                        tapSubroutine = { _, at -> model.replay.enter(at) },
                    ),
                    subroutines = model,
                )
            }
        }

        // The dots come before STOP NOW in the tree, so the first target is a dot. Fired by
        // index rather than by position, for the reason ScreenGraphTest fires by index: a
        // pointer event at the centre of a node never reaches a control drawn underneath one.
        val targets = onAllNodes(hasClickAction())
        assertTrue(
            targets.fetchSemanticsNodes().size > SubroutineModel.REPLAY_DOTS,
            "the dots are not publishing tap targets, so nothing here is being tested",
        )
        targets[0].performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(0), model.replay.entered, "the tap did not reach the entry")
        assertEquals(emptyList(), asked, "a tap inside a Subroutine moved the phone")
    }
}
