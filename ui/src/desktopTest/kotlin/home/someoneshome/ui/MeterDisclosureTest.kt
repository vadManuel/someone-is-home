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

    /**
     * **THE INJECTION: the endings are not the safe place for a real number** (D-153).
     *
     * Both ending screens drew one — `SYSTEM INTEGRITY 14 / 32` on the Insiders' and `0 / 32` on
     * the Residents' — and both were defects twice over: the `32` was a fossil of the count F-005
     * had already corrected, and the denominator divides out the Insider count whatever it is.
     *
     * **They looked safe and they were not.** The round is over, the reveal has happened, nothing
     * can be acted on — and the app is played *two to three rounds in an evening* (D-157), so a
     * denominator printed at the end of round one is a denominator carried into round two, where
     * it divides out the thing D-103 spent a whole revision hiding. One ending screen would
     * retroactively unhide the Insider count for every round that follows.
     *
     * Reintroducing either row fails here by name. The percentage assertions beside the
     * denominator sweep are what stop the row being *deleted* instead of converted — a screen that
     * says nothing about the meter also has no denominator on it.
     */
    @Test
    fun theEndingsSpeakOnlyPercent() {
        for (ending in listOf(ScreenId.WinInsiders, ScreenId.WinResidents)) {
            assertNoDenominator(ending)
        }
        // The Residents took it by clearing the meter, and the Insiders took it with this much
        // still standing. Read off the screens rather than off PanelVals, because the defect being
        // guarded was a literal in a composable and a test that read the model would not have seen
        // it.
        runDesktopComposeUiTest {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.WinResidents))
                }
            }
            onAllNodesWithText("0%").assertCountEquals(1)
        }
        runDesktopComposeUiTest {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.WinInsiders, role = PanelRole.Insider))
                }
            }
            onAllNodesWithText("43%").assertCountEquals(1)
        }
    }
}
