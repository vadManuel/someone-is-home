package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Every drawing loop the setup screens run ends, whatever size it is handed** (D-202).
 *
 * The self-test's dotted leader first walked its rule a dot at a time. A row squeezed to no height
 * by a short panel has dots of no size, so the step was zero, the walk never ended, and every pass
 * recorded another rectangle into the frame: a test worker grew to some 80 GB of native memory
 * before it was stopped. The leader and the plan's stairs hatch now count their marks once, and
 * these hold the counts finite for a zero, negative or non-finite size.
 */
@OptIn(ExperimentalTestApi::class)
class BootScreenTest {

    private val nonsense = listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

    @Test
    fun aLeaderOfNoSizeHoldsNoDots() {
        for (bad in nonsense) {
            assertEquals(0, dotsAcross(width = 200f, dot = bad), "a leader with dots of size $bad drew dots")
            assertEquals(0, dotsAcross(width = bad, dot = 2f), "a leader $bad long drew dots")
        }
    }

    @Test
    fun aLeaderHoldsDotsTheirOwnWidthApart() {
        assertEquals(25, dotsAcross(width = 100f, dot = 2f))
        assertEquals(1, dotsAcross(width = 2f, dot = 2f))
        assertEquals(2, dotsAcross(width = 6f, dot = 2f))
    }

    @Test
    fun aHatchOfNoSizeHoldsNoStripes() {
        for (bad in nonsense) {
            assertEquals(0, hatchStripes(w = bad, h = 30f, period = 5f), "a cell $bad wide was hatched")
            assertEquals(0, hatchStripes(w = 30f, h = bad, period = 5f), "a cell $bad tall was hatched")
            assertEquals(0, hatchStripes(w = 30f, h = 30f, period = bad), "a hatch of period $bad was drawn")
        }
        val huge = hatchStripes(w = 1e9f, h = 1e9f, period = 5f)
        assertTrue(huge <= 10_000, "a cell a billion wide asked for $huge stripes")
    }

    @Test
    fun aCellIsHatchedAcrossItsWholeWidth() {
        // A 30 by 30 cell at a 5-unit period: the stripes have to span x - y from -30 to 30.
        assertTrue(hatchStripes(w = 30f, h = 30f, period = 5f) >= 12)
    }

    /** The case that ran away: the self-test in a panel far too short for its rows. */
    @Test
    fun theSelfTestDrawsInAPanelTooShortForIt() = runDesktopComposeUiTest(width = 300, height = 120) {
        setContent { DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.Boot)) } }
        onNodeWithTag(BOOT_BAR).assertExists()
    }
}
