package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest

import kotlin.test.Test

/**
 * F-005's contradiction, held shut: the meter total is display data from [PanelState], never a
 * number this module knows.
 *
 * The ported design's 32 was an artifact of an earlier player count, and it sat here as a
 * constant contradicting the authority's `(seats − insiders) × 7` — flagged three times before
 * being settled (revision 19). These render a round whose total is a number the design never
 * drew, and read it back off the screen: a hardcoded segment count returns "/32" and fails.
 */
@OptIn(ExperimentalTestApi::class)
class MeterSegmentsTest {

    @Test
    fun theSpringboardMeterReadsItsTotalFromTheState() = runDesktopComposeUiTest {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(PanelState(screen = ScreenId.Home, meterSegments = 42))
            }
        }
        onAllNodesWithText("/42").assertCountEquals(1)
        onAllNodesWithText("/32").assertCountEquals(0)
    }

    @Test
    fun theOutsideViewReadsItsTotalFromTheState() = runDesktopComposeUiTest {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(PanelState(screen = ScreenId.Ghost3, outBy = OutBy.Revoked, meterSegments = 42))
            }
        }
        onAllNodesWithText("21/42").assertCountEquals(1)
        onAllNodesWithText("21/32").assertCountEquals(0)
    }
}
