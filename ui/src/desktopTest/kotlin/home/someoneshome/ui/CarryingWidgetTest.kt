package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Carrying takes the work-order widget's place, and says the rule on its face** (D-168).
 *
 * Both halves: while carrying, the widget is the carry and opens Files; not carrying, it is the
 * next Subroutine and opens the list. Both roles, because an Insider's fake order carries the same
 * way and a widget that changed for one role only would be the springboard's parity broken.
 */
@OptIn(ExperimentalTestApi::class)
class CarryingWidgetTest {

    private val disk = Carried(item = "DISK 01", to = "RACK")

    /** Renders page 1, taps [words], and returns where the tap went (null if nowhere). */
    private fun tapOn(words: String, carrying: Carried?, role: PanelRole): ScreenId? {
        var went: ScreenId? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        PanelState(screen = ScreenId.Home, role = role, carrying = carrying),
                        PanelActions(nav = { went = it }),
                    )
                }
            }
            onNodeWithText(words).performClick()
        }
        return went
    }

    @Test
    fun `carrying shows the carry and the rule, and opens Files`() {
        for (role in PanelRole.entries) {
            assertEquals(ScreenId.Files, tapOn("CARRYING", disk, role), "$role: CARRYING did not open Files")
            assertEquals(ScreenId.Files, tapOn("DISK 01", disk, role))
            assertEquals(ScreenId.Files, tapOn("TO RACK", disk, role))
            assertEquals(
                ScreenId.Files,
                tapOn("NOTHING ELSE OPENS UNTIL IT IS DELIVERED", disk, role),
                "$role: the rule is not on the widget's face",
            )
        }
    }

    @Test
    fun `not carrying is the next Subroutine, opening the list`() {
        for (role in PanelRole.entries) {
            assertEquals(ScreenId.Work, tapOn("NEXT SUBROUTINE", null, role))
        }
    }
}
