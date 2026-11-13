package home.someoneshome.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The motion audit, as something that can fail.**
 *
 * The owner's report was two sentences long and both were about a picture that did not move: *the
 * loading screen's bar literally doesn't move* and *the scanning timeout doesn't countdown nor does
 * its countdown bar move.* Neither was invisible. Both had been on screen for weeks, in front of
 * every instrument this repo has, and every one of them said the screen was fine.
 *
 * ### Why nothing caught it
 *
 * `ScreenshotTest` renders one frame. `DeviceLayoutTest` renders one frame. `SubroutineParityTest`
 * compares two roles at one instant. **A still photograph of a frozen bar and a still photograph of
 * a moving one are the same photograph** — so the entire render harness, which is the only thing
 * that has ever been trusted to say a screen works, is structurally blind to whether it moves.
 * `SubroutineMotionTest` is the exception and it covers exactly two screens.
 *
 * ### What this asks instead
 *
 * The clock is the test's, and every claim is about **two frames**: this rectangle is different from
 * the one before it, or it is deliberately identical. Three things in that order, because each is a
 * different way the fix could be wrong:
 *
 * 1. **It moves.** The bar is not the same picture a second later, and it moves the whole way.
 * 2. **It stops.** A window that ended is still, so a bar that quietly kept filling — or a clock
 *    that ran past zero into negative seconds — fails rather than looking finished.
 * 3. **Nothing else started moving.** The meeting's clocks are the house's, and a housed phone
 *    sitting on one for thirty seconds must draw the identical frame at the end of it. That is
 *    `HousedPhoneTest`'s property asked about the **picture** rather than about the screen id, and
 *    it is what stops a motion audit from being answered by making everything move.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class MotionTest {

    private val width = 300
    private val height = 650

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

    /** How many pixels on the frame are exactly [rgb]. A bar's own fill is countable. */
    private fun pixels(image: BufferedImage, rgb: Int): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) and 0xFFFFFF == rgb and 0xFFFFFF) count++
            }
        }
        return count
    }

    /**
     * **The self-test bar fills across its whole 2.4 seconds** — the owner's first sentence.
     *
     * Measured on the bar's own rectangle rather than on the screen, and by counting the fill's own
     * colour rather than by diffing: a bar that jittered by an antialiased pixel would satisfy *the
     * picture changed* while standing exactly where it stood. What has to be true is that there is
     * **more lit bar** at every step, which is the only thing a progress bar means.
     *
     * Injecting the bug is the state this screen shipped in: two `weight`s, 7 against 3. Every
     * sample then returns the same count and this fails naming both.
     */
    @Test
    fun theSelfTestBarFillsAcrossItsWholeWindow() {
        val window = Flow.autoAdvance.getValue(ScreenId.Boot).afterMillis
        val fill = Amber.BoneDim.toArgb()
        val counts = mutableListOf<Int>()
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.Boot)) }
            }
            mainClock.advanceTimeByFrame()
            counts += pixels(onNodeWithTag(BOOT_BAR).captureToImage().toAwtImage(), fill)
            // Five equal steps across the window, plus one past the end.
            repeat(6) {
                mainClock.advanceTimeBy(window / 5L)
                counts += pixels(onNodeWithTag(BOOT_BAR).captureToImage().toAwtImage(), fill)
            }
        }

        assertEquals(
            0, counts.first(),
            "the self-test bar was already ${counts.first()} pixels full in its opening frame — " +
                "it is drawing a fixture rather than the window it is in",
        )
        for (step in 1 until counts.size - 1) {
            assertTrue(
                counts[step] > counts[step - 1],
                "the self-test bar stood still between step ${step - 1} and $step " +
                    "(${counts[step - 1]} → ${counts[step]} lit pixels): the loading bar does not " +
                    "move, which is the owner's own report. Sequence: $counts",
            )
        }
        assertTrue(
            counts.last() > 0,
            "the self-test bar ended empty after its whole window ran: $counts",
        )
    }

    /**
     * **The scan window really counts its ten seconds down** — the owner's second sentence.
     *
     * The screen used to draw the value twice, a seconds readout over twenty segments, and the
     * report named both. The mockup cut the readout (D-202: screens say little), so the bar is the
     * whole of it now, and this holds the bar to everything the readout was held to: it opens on
     * the whole window, never gains, visits most of the window rather than sitting on a drawn
     * moment, and is empty when the window runs out.
     *
     * D-159's window is a safety device rather than pacing, which is why the last assertion matters
     * as much as the first: a bar that reaches zero is the phone saying *the light is about to fail*,
     * and a bar that stops at six forever is the phone promising six more seconds of light that it
     * has already spent.
     *
     * Injecting the bug: hand `Countdowns.on` the fixture again — drop [PanelVals.window] out of
     * `PanelVals.countdown` — and every sample comes back as the same twelve segments.
     */
    @Test
    fun theScanWindowCountsItsTenSecondsDownOnTheBar() {
        val window = Flow.autoAdvance.getValue(ScreenId.Scan).afterMillis
        val lit = Amber.Black.toArgb()
        val bars = mutableListOf<Int>()
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.Scan)) }
            }
            mainClock.advanceTimeByFrame()

            fun sample(): Int = segmentsLit(onNodeWithTag(SCAN_BAR).captureToImage().toAwtImage(), lit)

            bars += sample()
            // A second at a time, the whole way, and one second past the end.
            repeat(window / 1000 + 1) {
                mainClock.advanceTimeBy(1_000)
                bars += sample()
            }
        }

        assertEquals(
            PanelVals.SCAN_SEGMENTS, bars.first(),
            "the scan opened on ${bars.first()} lit segments rather than its whole window — the " +
                "bar is a drawn moment, not this phone's clock: $bars",
        )
        for (step in 1 until bars.size) {
            assertTrue(
                bars[step] <= bars[step - 1],
                "the scan bar gained ${bars[step] - bars[step - 1]} segments while the clock was " +
                    "running down: $bars",
            )
        }
        assertTrue(
            bars.distinct().size >= window / 1000,
            "the scan bar showed only ${bars.distinct().size} different lengths across a " +
                "${window}ms window — it is not counting, it is sitting on a drawn moment: $bars",
        )
        assertEquals(0, bars.last(), "the scan window ran out and the bar is not empty: $bars")
    }

    /**
     * How many segments of a segment bar are lit: the runs of [lit] along its middle row. A lit
     * segment is solid edge to edge and the gaps between segments are ground, so each lit segment
     * is one run however wide the panel draws it.
     */
    private fun segmentsLit(img: BufferedImage, lit: Int): Int {
        val y = img.height / 2
        var runs = 0
        var inRun = false
        for (x in 0 until img.width) {
            val on = img.getRGB(x, y) == lit
            if (on && !inRun) runs++
            inRun = on
        }
        return runs
    }

    /**
     * **THE CLASS GUARD: every window this phone runs for itself moves, and then stops.**
     *
     * Written over [LocalWindows.screens] rather than over the two screens by name, so a third
     * client-owned window added later is measured the day it is declared and cannot ship frozen. The
     * two halves are the two ways this can be wrong:
     *
     * - **It moves.** More than half the frames across the window differ from the one before them.
     *   A bar that stepped once and stopped passes a two-frame comparison and is still frozen.
     * - **It stops.** A window whose length has run is a still picture. A screen still animating a
     *   second later is running a clock past the moment the thing it was counting down happened,
     *   which on the scan is a lit rectangle in a dark house that nobody is coming back for.
     *
     * Measured on the whole screen, because the claim here is about the screen rather than about one
     * rectangle — and both of these screens have exactly one moving thing on them, so *the screen
     * changed* and *the window moved* are the same sentence. The scan's ring pulse would break that
     * if it were on the same surface; it is not, it sits above the panel and pulses forever, so the
     * still-picture half is asserted on the tagged bar alone.
     */
    @Test
    fun everyWindowThisPhoneRunsForItselfMovesAndThenStops() {
        assertTrue(
            LocalWindows.screens.isNotEmpty(),
            "no screen runs a window of its own; the table emptied and every clock went with it",
        )
        for (screen in LocalWindows.screens) {
            val window = LocalWindows.millisOf(screen)
                ?: throw AssertionError("$screen is named as running its own window and has none")
            val tag = when (screen) {
                ScreenId.Boot -> BOOT_BAR
                ScreenId.Scan -> SCAN_BAR
                else -> throw AssertionError(
                    "$screen runs a window of its own and nothing on it is tagged, so this guard " +
                        "cannot see whether it moves. Tag the element it draws.",
                )
            }
            runDesktopComposeUiTest(width = width, height = height) {
                mainClock.autoAdvance = false
                setContent {
                    DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = screen)) }
                }
                mainClock.advanceTimeByFrame()

                // Ten samples across the window, which is fine grain on 2.4 seconds and coarse on
                // ten — deliberately, since the claim is *it keeps moving* rather than *it moves
                // smoothly*.
                val frames = mutableListOf(onNodeWithTag(tag).captureToImage().toAwtImage())
                repeat(10) {
                    mainClock.advanceTimeBy(window / 10L)
                    frames += onNodeWithTag(tag).captureToImage().toAwtImage()
                }
                val moved = (1 until frames.size).count { diff(frames[it - 1], frames[it]).first > 0 }
                assertTrue(
                    moved > frames.size / 2,
                    "$screen runs a ${window}ms window of its own and only $moved of " +
                        "${frames.size - 1} steps across it drew anything different — the picture " +
                        "is frozen while the clock behind it runs",
                )

                // Past the end, twice over. Nothing may still be moving.
                mainClock.advanceTimeBy(window.toLong())
                val settled = onNodeWithTag(tag).captureToImage().toAwtImage()
                mainClock.advanceTimeBy(window.toLong())
                val later = onNodeWithTag(tag).captureToImage().toAwtImage()
                val (count, at) = diff(settled, later)
                assertEquals(
                    0, count,
                    "$screen was still animating ${window}ms after its own window ended " +
                        "($count pixels, first at $at) — the clock is running past the thing it " +
                        "was counting down to",
                )
            }
        }
    }

    /**
     * **The house's clocks are still the house's: a housed phone on a meeting screen draws the
     * identical frame thirty seconds later.**
     *
     * The audit's own failure mode, and the reason this test is in the same file as the fixes. The
     * cheap way to answer *nothing moves* is to make everything move, and it would have been
     * invisible: a discussion clock ticking down locally looks **more** correct than one that sits
     * still, right up until six phones in a dark house are each certain of a different number and
     * the house ends the talk at a moment none of them drew.
     *
     * This is `HousedPhoneTest`'s property one layer down. That file asks whether the phone changed
     * *screen*; this asks whether it changed *picture*, which is where a local tick would appear
     * first and where no existing test was looking.
     *
     * Thirty seconds is a third of the discussion window and two thirds of the ballot, so a clock
     * running at any believable rate would have moved several segments by the end of it.
     */
    @Test
    fun aHousedMeetingScreenDrawsTheIdenticalFrameThirtySecondsLater() {
        for (screen in listOf(ScreenId.Discussion, ScreenId.Vote, ScreenId.Tally, ScreenId.GhostMeeting)) {
            runDesktopComposeUiTest(width = width, height = height) {
                mainClock.autoAdvance = false
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(
                            PanelState(
                                screen = screen,
                                houseAttached = true,
                                secondsLeft = 30,
                                outBy = if (screen == ScreenId.GhostMeeting) OutBy.Revoked else null,
                            )
                        )
                    }
                }
                mainClock.advanceTimeByFrame()
                val opening = onRoot().captureToImage().toAwtImage()
                mainClock.advanceTimeBy(30_000)
                val later = onRoot().captureToImage().toAwtImage()

                val (count, at) = diff(opening, later)
                assertEquals(
                    0, count,
                    "$screen moved by itself: $count pixels differ thirty seconds on, first at " +
                        "$at. The house owns this clock — a phone that advances it locally is six " +
                        "phones counting differently, each of them certain",
                )
            }
        }
    }

    /**
     * **And the house's clock is drawn, so *still* is not *ignored*.**
     *
     * The other half of *parked is not frozen*. A screen that satisfied the test above by drawing no
     * clock at all, or by drawing the same fixture whatever arrived, would be a worse bug than the
     * one being guarded against — and it is the exact fault `PanelVals.integrityPercent` and the
     * couch's phase were both caught in.
     *
     * Two pushes, one frame apart, differing only in the number: the picture has to differ with it.
     */
    @Test
    fun aMeetingScreenDrawsTheSecondsTheHousePushed() {
        val frames = listOf(80, 20).map { said ->
            var image: BufferedImage? = null
            runDesktopComposeUiTest(width = width, height = height) {
                mainClock.autoAdvance = false
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(
                            PanelState(
                                screen = ScreenId.Discussion,
                                houseAttached = true,
                                secondsLeft = said,
                            )
                        )
                    }
                }
                mainClock.advanceTimeByFrame()
                image = onRoot().captureToImage().toAwtImage()
            }
            image!!
        }
        assertTrue(
            diff(frames[0], frames[1]).first > 0,
            "the house pushed 80 seconds and then 20 and the discussion screen drew the same " +
                "picture both times — the clock is a fixture and the push reaches nothing",
        )
    }

    /**
     * **The two kinds of clock are disjoint, and that is checked rather than trusted.**
     *
     * [LocalWindows.screens] is the list of windows this phone runs; the meeting's are the house's.
     * A screen appearing on both lists would be a phone counting down a window the house is also
     * pushing — and the symptom is not a crash, it is a clock that jumps backwards whenever a push
     * lands, on the screens where a player is being asked to make a decision against it.
     *
     * There is no arrangement of the current tables that could do this. That is the point of writing
     * it down: the day somebody adds a row to make a meeting screen feel more alive, this is what
     * says no.
     */
    @Test
    fun noScreenRunsItsOwnClockAndTheHousesAtOnce() {
        val housesOwn = Flow.housePushes.keys + Countdowns.screens - LocalWindows.screens
        val both = LocalWindows.screens intersect Flow.housePushes.keys
        assertTrue(
            both.isEmpty(),
            "$both run a window of their own AND wait for a house push — whichever arrives second " +
                "moves the clock backwards under the player",
        )
        assertTrue(
            ScreenId.Discussion in housesOwn && ScreenId.Vote in housesOwn,
            "the meeting's own clocks left the house's side of the line, which is the whole " +
                "distinction this audit rests on",
        )
    }
}
