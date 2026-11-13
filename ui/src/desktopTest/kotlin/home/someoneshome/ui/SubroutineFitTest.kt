package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.DotColumns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **What the restyled Subroutine screens promise about their own geometry** (D-202).
 *
 * The redesign gave Jam and Deallocate the mockup's numbers -- a 140 ring and a 4-unit step, a
 * 40 × 30 block in a 260 field -- where they used to be derived from the question. Typed numbers can
 * stop agreeing with the question, and on these two screens a disagreement is a *different question*
 * on the glass from the one the house asked. These hold the two agreements that matter.
 */
@OptIn(ExperimentalTestApi::class)
class SubroutineFitTest {

    /**
     * **The press the house is waiting for is the press that puts one ring on the other.**
     *
     * Jam is graded on the net presses cancelling the opening the house drew (E-S2-1). So at that
     * offset the player's ring must be drawn exactly over the fixed one -- not a step beside it -- or
     * the press that visibly closes the gap is refused and the one that is accepted looks wrong.
     * Read as the fixed ring's own value: at the answer none of it shows, because the player's ring
     * covers it; at the opening it does.
     *
     * Injecting the bug means drawing either ring from anything but `opening + offset` -- a fixed
     * ring a step off centre, a step that is not [JAM_STEP] -- and the fixed ring shows at the answer.
     */
    @Test
    fun jamsRingsCoincideAtExactlyTheOffsetTheHouseGrades() {
        val opening = SubroutineModel.JAM.opening
        val apart = fixedRingPixels(SubroutineModel())
        // One press at a time towards the fixed ring, as many as the house opened it by.
        val toward = if (opening < 0) 1 else -1
        val together = fixedRingPixels(
            SubroutineModel().apply { repeat(kotlin.math.abs(opening)) { tap(Subroutine.Jam, toward) } },
        )
        assertTrue(apart > 0, "the fixed ring drew nothing at the opening, so this measures nothing")
        assertEquals(
            0, together,
            "with the presses cancelling the opening of $opening, $together pixels of the fixed ring " +
                "still show: the ring the house will accept is not drawn on the one it is aimed at",
        )
    }

    private fun fixedRingPixels(model: SubroutineModel): Int {
        val rgb = Ramp.Line.toArgb() and 0xFFFFFF
        var count = 0
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent {
                Box(Modifier.fillMaxSize()) {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(PanelState(screen = ScreenId.SubJam), subroutines = model)
                    }
                }
            }
            val rings = onNodeWithTag(JAM_RINGS).captureToImage().toAwtImage()
            for (y in 0 until rings.height) for (x in 0 until rings.width) {
                if (rings.getRGB(x, y) and 0xFFFFFF == rgb) count++
            }
        }
        return count
    }

    /**
     * **Every distribution the reader lets through stands in the field whole.**
     *
     * [DotColumns] accepts up to [DotColumns.MOST_DOTS] in a column and [DotColumns.MOST_COLUMNS]
     * columns. The tallest column has to stand inside [DEAL_FIELD] and the widest row of columns
     * inside the mockup's 320-unit body, or a column is clipped -- and a clipped column is a
     * distribution the player was never shown, graded as if they had been.
     *
     * Injecting the bug: shrink the field below seven blocks, or widen the block, and this names the
     * overrun.
     */
    @Test
    fun theTallestAndWidestDistributionTheReaderAcceptsFitTheScreen() {
        val tallest = DEAL_BLOCK_HEIGHT * DotColumns.MOST_DOTS + DEAL_GAP * (DotColumns.MOST_DOTS - 1)
        assertTrue(
            tallest <= DEAL_FIELD,
            "a column of ${DotColumns.MOST_DOTS} stands $tallest tall in a field of $DEAL_FIELD",
        )
        val widest = DEAL_BLOCK_WIDTH * DotColumns.MOST_COLUMNS + DEAL_COLUMN_GAP * (DotColumns.MOST_COLUMNS - 1)
        val body = (DESIGN_WIDTH - 40f)
        assertTrue(
            widest.value <= body,
            "${DotColumns.MOST_COLUMNS} columns stand $widest wide in a body of $body",
        )
    }
}
