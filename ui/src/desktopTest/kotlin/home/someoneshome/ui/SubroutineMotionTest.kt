package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The two Subroutines that move, checked against a clock the test owns.**
 *
 * Interrupt's sweep (D-139) and Drift's dot (D-140) are the only things in this module that change
 * without being touched, and everything difficult about them is a fact about *time*: whether the
 * two roles are looking at the same frame, whether a replay draws the frame it drew before, whether
 * anything on either screen quietly ends after a while, and whether the buzz that asks Drift's
 * question is what decides that an entry may go.
 *
 * ### None of that can be asserted on a clock the test does not control
 *
 * `SubroutineParityTest` renders with the harness advancing time on its own, which for these two
 * means Compose's [androidx.compose.animation.core.InfiniteAnimationPolicy] stops the frames and
 * both screens sit at their opening position. That is a real state and worth comparing — an
 * animated screen that never draws its first frame is a bug all of its own — but it is one frame of
 * a motion that has no end, and every claim below is about the rest of them.
 *
 * So every render here sets `mainClock.autoAdvance = false` and steps the clock itself. That is the
 * FlowHost discipline stated for a screen rather than for a flow: **the milliseconds come from the
 * composition, never from a wall clock**, which is what makes a frame sequence something a test can
 * ask for twice and a recording can ask for again.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class SubroutineMotionTest {

    private val width = 300
    private val height = 650

    /** The two the house draws with a clock. Everything here is asserted about both of them. */
    private val moving = listOf(Subroutine.Interrupt, Subroutine.Drift)

    /**
     * The screen, rendered once at each of [steps] — a list of how far to push the clock before
     * taking each frame, in milliseconds.
     *
     * The first frame is taken before any of them, because a screen's opening position is the one
     * frame that exists whether or not anything is animating.
     */
    private fun frames(
        subroutine: Subroutine,
        steps: List<Long>,
        role: PanelRole = PanelRole.Resident,
        model: SubroutineModel = SubroutineModel(),
    ): List<BufferedImage> {
        val out = mutableListOf<BufferedImage>()
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
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
            mainClock.advanceTimeByFrame()
            out += onRoot().captureToImage().toAwtImage()
            for (millis in steps) {
                mainClock.advanceTimeBy(millis)
                out += onRoot().captureToImage().toAwtImage()
            }
        }
        return out
    }

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

    /** How many pixels on the frame are exactly [rgb]. The echo's own colour is countable. */
    private fun pixels(image: BufferedImage, rgb: Int): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) and 0xFFFFFF == rgb and 0xFFFFFF) count++
            }
        }
        return count
    }

    /** A quarter-second of motion, eleven times: two and a half seconds of either Subroutine. */
    private val sampled = List(11) { 250L }

    /**
     * **The Insider's moving screen is the Resident's moving screen, frame for frame.**
     *
     * Rule 8's claim on the two Subroutines where it is hardest to check by looking: a difference
     * that lasted a hundred milliseconds — a sweep a step behind, a dot that emerged a frame early,
     * a mark that lingered — is invisible in review, invisible in a screenshot, and perfectly
     * visible to somebody standing behind an Insider in a dark corridor.
     *
     * Both roles are driven through the identical clock, so the comparison is of the same instant
     * rather than of two screens that happen to have been sampled at the same rate.
     *
     * Injecting the bug means giving either screen something that reads `vals.insider` — a
     * different speed, a dimmer dot, a band a step wider. All of them fail here, naming the
     * Subroutine and the frame.
     */
    @Test
    fun theMovingScreensAreTheSameScreenForBothRolesAtEveryStepOfTheMotion() {
        val wrong = mutableListOf<String>()
        for (subroutine in moving) {
            val resident = frames(subroutine, sampled, PanelRole.Resident)
            val insider = frames(subroutine, sampled, PanelRole.Insider)
            for (frame in resident.indices) {
                val (count, at) = diff(resident[frame], insider[frame])
                if (count > 0) {
                    wrong += "${subroutine.label} frame $frame — $count pixels differ by role, " +
                        "first at $at"
                }
            }
        }
        wrong.forEach { println("MOTION-FAKE  $it") }
        assertTrue(
            wrong.isEmpty(),
            "a moving Subroutine is not the same screen for both roles:" +
                wrong.joinToString("") { "\n  $it" },
        )
    }

    /**
     * **The same instance renders the same frames, twice — which is what makes a replay a replay.**
     *
     * D-139 and D-140 both rest on it: *the house sends the parameters and the client renders the
     * motion deterministically from them.* Two compositions of the same screen, driven through the
     * identical clock, have to produce the identical sequence of pictures — because the alternative
     * is that the house grades a tap against a picture nobody can reconstruct, and eight phones in
     * a dark house cannot be debugged any other way.
     *
     * Rendered rather than computed, because the arithmetic being deterministic is already asserted
     * in `SubroutineTest` and this is the claim one layer up: nothing between the parameters and
     * the glass — no remembered start, no wall clock, no frame counter that survives a
     * recomposition — has smuggled in a second source of time.
     *
     * **The sequence also has to move**, or two identical piles of identical frames would pass
     * this without either screen ever drawing anything.
     */
    @Test
    fun theSameInstanceRendersTheSameFrameSequenceTwice() {
        for (subroutine in moving) {
            val first = frames(subroutine, sampled)
            val again = frames(subroutine, sampled)
            assertEquals(first.size, again.size)
            for (frame in first.indices) {
                val (count, at) = diff(first[frame], again[frame])
                assertEquals(
                    0, count,
                    "${subroutine.label} frame $frame drew something different the second time " +
                        "through — $count pixels, first at $at. The motion is reading a clock the " +
                        "harness cannot put back.",
                )
            }
            val distinct = first.indices.count { frame ->
                frame == 0 || diff(first[frame - 1], first[frame]).first > 0
            }
            assertTrue(
                distinct > first.size / 2,
                "${subroutine.label} drew ${first.size} frames and only $distinct of them differ " +
                    "from the one before, so this is comparing a screen that is not moving",
            )
        }
    }

    /**
     * **D-139 — the sweep runs forever, and a minute of standing still changes nothing.**
     *
     * *There is no timeout. Hesitation is taxed by exposure, not by a clock* — the screen is MEDIUM
     * and lit and the player is standing at the marker where D-110 keeps them, so every extra pass
     * is another second of being visible to whoever walks in. Nothing on the phone spends that
     * patience for them.
     *
     * Four things a minute later, and each is a different way a timeout could have been written: the
     * entry has not gone, the return line is still empty, the panel is still a control, and the
     * sweep is still moving. A screen that quietly stopped animating would pass the first three.
     *
     * Injecting the bug means giving [InterruptScreen] a ceiling — an `if (elapsed > …)` that hands
     * the entry over, dims the panel, or stops the clock.
     */
    @Test
    fun interruptHasNoTimeoutAndIsStillRunningAMinuteIn() {
        val model = SubroutineModel()
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        PanelState(screen = ScreenId.SubInterrupt),
                        PanelActions(tapSubroutine = model::tap),
                        subroutines = model,
                    )
                }
            }
            mainClock.advanceTimeByFrame()
            val controls = onAllNodes(hasClickAction()).fetchSemanticsNodes().size

            repeat(60) { mainClock.advanceTimeBy(1_000) }

            assertTrue(
                !model.interrupt.touched,
                "the sweep caught itself while nobody was touching the phone",
            )
            assertTrue(
                onAllNodes(hasText("RETURNED . WAITING")).fetchSemanticsNodes().isEmpty(),
                "INTERRUPT returned an entry a minute in, with nothing entered",
            )
            assertEquals(
                controls, onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
                "a minute of waiting took a control off INTERRUPT, so something on it expired",
            )

            val before = onRoot().captureToImage().toAwtImage()
            mainClock.advanceTimeBy(300)
            val after = onRoot().captureToImage().toAwtImage()
            assertTrue(
                diff(before, after).first > 0,
                "the sweep has stopped moving a minute in, so there is a clock on this screen " +
                    "counting something down",
            )
        }
    }

    /**
     * **D-140 — a tap before the buzz is echoed and sends nothing; the one after it sends.**
     *
     * The question is the house's to ask, and until it has been asked there is nothing to answer.
     * But a control that swallowed the tap in silence is indistinguishable, in an unlit room, from
     * a phone that has died — and this is the one Subroutine where the player's eyes are on the
     * screen rather than on the doorway. So the mark moves and the entry stays put.
     *
     * Driven with real pointers at the lane rather than through the model, because the decision
     * being checked lives in the screen: it is the screen that knows whether the house has asked.
     */
    @Test
    fun driftEchoesATapBeforeTheBuzzAndSendsOnlyTheOneAfterIt() {
        val model = SubroutineModel()
        val path = SubroutineModel.DRIFT
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        PanelState(screen = ScreenId.SubDrift),
                        PanelActions(
                            tapSubroutine = model::tap,
                            handOverSubroutine = model::handOver,
                        ),
                        subroutines = model,
                    )
                }
            }
            mainClock.advanceTimeByFrame()

            // Well before the buzz, and the fixture's wait is long enough that this is not a
            // matter of a frame either way.
            val early = path.askAtMillis / 3L
            mainClock.advanceTimeBy(early)
            tapLane(fraction = 0.25f)
            mainClock.advanceTimeBy(100)

            val marked = model.drift.choice
            assertTrue(marked != null, "a tap before the buzz reached nothing at all")
            assertNull(
                model.drift.handedOver,
                "a tap before the buzz went to the house, which is an answer to a question " +
                    "nobody has asked",
            )
            assertTrue(
                onAllNodes(hasText("RETURNED . WAITING")).fetchSemanticsNodes().isEmpty(),
                "DRIFT says it has returned something, and nothing has gone",
            )

            // Past the instant the scan named. The buzz is the house's and this build has no motor
            // to feel it on, so what happens here is the clock reaching it.
            mainClock.advanceTimeBy(path.askAtMillis.toLong())
            tapLane(fraction = 0.75f)
            mainClock.advanceTimeBy(100)

            assertTrue(
                model.drift.handedOver != null,
                "a tap after the buzz did not go, so the answer cannot be given",
            )
            assertEquals(
                model.drift.choice, model.drift.handedOver,
                "what went to the house is not what the screen is showing",
            )
            assertTrue(
                model.drift.handedOver!! > marked,
                "the second tap landed further along the lane and the entry kept the first one",
            )
            assertTrue(
                onAllNodes(hasText("RETURNED . WAITING")).fetchSemanticsNodes().isNotEmpty(),
                "the entry has gone and the screen has not said so",
            )
        }
    }

    /**
     * **Neither screen draws the catch or the tap any differently for landing well.**
     *
     * The word sweep next door forbids a screen *saying* how the player did; this is the same claim
     * about the one thing these two screens have that could say it without a word — **the echo's
     * own light.** A mark drawn brighter for a catch inside the band, or wider for a tap near the
     * dot, is a verdict rendered in the only language this game has, on the two Subroutines where
     * the device holds both halves of the question in order to draw them.
     *
     * Checked by counting the echo's colour: the mark is the same mark, so it is the same number of
     * pixels of [Amber.Bright] wherever it landed. Injecting the bug means one `if` on either screen
     * — inside the band, near the dot — and it fails here naming the two counts.
     */
    @Test
    fun theEchoIsTheSameMarkWhereverItLanded() {
        // The echo's colour, off the palette rather than typed, so this cannot drift from what the
        // screens actually draw.
        val rgb = Amber.Bright.toArgb()

        val sweep = SubroutineModel.INTERRUPT
        val inside = SubroutineModel().apply { interrupt.enter(sweep.bandAt) }
        val outside = SubroutineModel().apply {
            interrupt.enter(sweep.bandFrom - InterruptSweep.BAND_HALF * 2)
        }
        val caughtIn = pixels(frames(Subroutine.Interrupt, emptyList(), model = inside).single(), rgb)
        val caughtOut =
            pixels(frames(Subroutine.Interrupt, emptyList(), model = outside).single(), rgb)
        assertEquals(
            caughtOut, caughtIn,
            "INTERRUPT draws $caughtIn lit pixels for a catch inside the band and $caughtOut for " +
                "one outside it, so the screen is grading the catch",
        )

        val path = SubroutineModel.DRIFT
        val answer = path.at(path.askAtMillis.toLong())!!
        val near = SubroutineModel().apply { drift.choose(answer) }
        val far = SubroutineModel().apply { drift.choose((answer + DriftPath.SPAN / 3) % DriftPath.SPAN) }
        val tappedNear = pixels(frames(Subroutine.Drift, emptyList(), model = near).single(), rgb)
        val tappedFar = pixels(frames(Subroutine.Drift, emptyList(), model = far).single(), rgb)
        assertEquals(
            tappedFar, tappedNear,
            "DRIFT draws $tappedNear lit pixels for a tap on the dot and $tappedFar for one " +
                "nowhere near it, so the screen is grading the answer",
        )
    }

    /**
     * **The mark lands under the finger, which is what keeps what is drawn and what is sent one
     * fact.**
     *
     * Drift's entry is a position, and the position is the offset of the tap over the width of the
     * surface it landed on. So the picture has to be measuring the same rectangle: a canvas inset
     * by even a few units inside its own touch surface would draw the mark somewhere the finger was
     * not — and would send the house the other one of the two. The player then answers the picture,
     * correctly, and is graded on a number they were never shown, which is [DotColumns]' clipped
     * column wearing a different fault.
     *
     * Measured on the lane's own pixels rather than on the whole screen, so the arithmetic is the
     * screen's rather than this test's: the mark's middle, over the lane's width, is where the
     * finger went.
     */
    @Test
    fun theMarkIsDrawnUnderTheFinger() {
        val rgb = Amber.Bright.toArgb()
        for (fraction in listOf(0.2f, 0.5f, 0.8f)) {
            val model = SubroutineModel()
            runDesktopComposeUiTest(width = width, height = height) {
                mainClock.autoAdvance = false
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(
                            PanelState(screen = ScreenId.SubDrift),
                            PanelActions(tapSubroutine = model::tap),
                            subroutines = model,
                        )
                    }
                }
                mainClock.advanceTimeByFrame()
                tapLane(fraction)
                mainClock.advanceTimeBy(100)

                val lane = onNodeWithTag(DRIFT_LANE).captureToImage().toAwtImage()
                val columns = (0 until lane.width).filter { x ->
                    (0 until lane.height).any {
                        lane.getRGB(x, it) and 0xFFFFFF == rgb and 0xFFFFFF
                    }
                }
                assertTrue(columns.isNotEmpty(), "the tap at $fraction drew no mark at all")
                val middle = (columns.first() + columns.last()) / 2.0 / lane.width
                assertTrue(
                    kotlin.math.abs(middle - fraction) < 0.03,
                    "a tap at $fraction of the lane drew its mark at $middle of it — the canvas " +
                        "and the touch surface are not the same rectangle, so what is drawn and " +
                        "what is sent are two different positions",
                )
            }
        }
    }

    /** A tap at [fraction] of the way along the lane, made with a real pointer. */
    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.tapLane(
        fraction: Float,
    ) {
        onNodeWithTag(DRIFT_LANE).performTouchInput {
            click(Offset(width * fraction, height / 2f))
        }
    }
}
