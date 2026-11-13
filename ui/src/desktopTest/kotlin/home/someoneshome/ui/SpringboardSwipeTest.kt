package home.someoneshome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **The springboard pages by horizontal swipe, identically for both roles** (`gdd.md:561`, D-202).
 *
 * The page swipe is a gesture, so no rendering test can read it off a screen as a click; this is
 * the walk that keeps its [Flow.viaActions] edges honest. Every screen that draws page 1 turns to
 * page 2 on a swipe left, page 2 turns back on a swipe right, and a slipped finger turns nothing.
 */
@OptIn(ExperimentalTestApi::class)
class SpringboardSwipeTest {

    /** Swipes across the middle of the page, from [fromX] to [toX] as fractions of the width. */
    private fun swiped(screen: ScreenId, role: PanelRole, fromX: Float, toX: Float): ScreenId? {
        var went: ScreenId? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = screen, role = role), PanelActions(nav = { went = it }))
                }
            }
            onRoot().performTouchInput {
                val y = height * 0.6f
                swipe(Offset(width * fromX, y), Offset(width * toX, y), durationMillis = 200)
            }
        }
        return went
    }

    private val pageOne = listOf(
        ScreenId.Home, ScreenId.Notify, ScreenId.Quiet, ScreenId.Banner, ScreenId.EgressWidget,
    )

    @Test
    fun `a swipe left on page 1 turns to page 2`() {
        for (role in PanelRole.entries) for (screen in pageOne) {
            assertEquals(ScreenId.Page2, swiped(screen, role, 0.85f, 0.15f), "$role on $screen")
        }
    }

    @Test
    fun `a swipe right on page 2 turns back to page 1`() {
        for (role in PanelRole.entries) {
            assertEquals(ScreenId.Home, swiped(ScreenId.Page2, role, 0.15f, 0.85f), "$role")
        }
    }

    @Test
    fun `a slipped finger and the wrong direction turn nothing`() {
        for (role in PanelRole.entries) {
            assertNull(swiped(ScreenId.Home, role, 0.55f, 0.50f), "$role: a slip turned the page")
            assertNull(swiped(ScreenId.Home, role, 0.15f, 0.85f), "$role: page 1 turned right")
            assertNull(swiped(ScreenId.Page2, role, 0.85f, 0.15f), "$role: page 2 turned left")
        }
    }
}
