package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test

/**
 * D-103 (revision 21): SystemIntegrity reaches a panel only as a percentage, because the meter's
 * denominator is `(seats − insiders) × 7` and printing it hands every reader the Insider count
 * by division — a count the host may have hidden. The bar itself is fixed display resolution
 * ([PanelVals.METER_SEGMENTS]); only the percentage varies.
 *
 * These render the three meter-bearing surfaces and read the text off the screen: a reintroduced
 * absolute readout — "28/42", "/32", any slash-total — fails here, which is the regression that
 * revision 19 existed to catch, updated for the sharper rule that replaced it.
 */
@OptIn(ExperimentalTestApi::class)
class MeterDisclosureTest {

    private fun assertNoDenominator(screen: ScreenId, outBy: OutBy? = null) = runDesktopComposeUiTest {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(PanelState(screen = screen, outBy = outBy))
            }
        }
        // The design's segment resolutions, and the true denominators for 6-10 players at 7
        // subroutines each: none may appear as on-screen text, alone or as a "/total" suffix.
        for (total in listOf(32, 28, 35, 42, 49, 56)) {
            onAllNodesWithText("/$total", substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun theSpringboardMeterSpeaksOnlyPercent() {
        runDesktopComposeUiTest {
            setContent {
                DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = ScreenId.Home)) }
            }
            onAllNodesWithText("88%").assertCountEquals(1)
        }
        assertNoDenominator(ScreenId.Home)
    }

    @Test
    fun theOutsideViewSpeaksOnlyPercent() {
        assertNoDenominator(ScreenId.Ghost3, outBy = OutBy.Revoked)
    }
}
