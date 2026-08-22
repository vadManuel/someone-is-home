package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        //
        // **Both spacings, and the spaced one is the form the actual defect had.** The rows D-153
        // was written against read `SYSTEM INTEGRITY 14 / 32`, with spaces, and this sweep only
        // looked for `/32` — so it was pointed straight at the bug it was named for and would have
        // walked past it. It was found by injection: a couch readout rewritten to `21 / 32` failed
        // only the percentage assertion beside this loop, which is a different guard doing this
        // one's job by luck.
        for (total in listOf(32, 28, 35, 42, 49, 56)) {
            for (form in listOf("/$total", "/ $total")) {
                onAllNodesWithText(form, substring = true).assertCountEquals(0)
            }
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

    /**
     * **The couch is not an exception, and it is the audience with the most time to divide**
     * (D-134, D-145, D-153).
     *
     * A player who is out watches Resident progress and the real Egress number live, all round,
     * with nothing else to do — which is exactly why the percentage rule cannot soften for them.
     * The denominator is `(seats − insiders) × 7`; one printed on this screen would hand the reader
     * the Insider count for this round and, since an evening is two to three rounds (D-157), for
     * every round after it. That the reader can act on none of it is D-145's point and not a
     * licence: the correlation they build is theirs, the arithmetic is nobody's.
     *
     * **Both numbers are read against their own bars.** They were literals — `66%` over a bar lit
     * to 21 of 32, and `71%` over one lit to 22 — which is not a rounding slip but two
     * hand-maintained facts about one meter, on the screen where a meter is read most carefully.
     * The same fault, on the same class, that [PanelVals.integrityPercent] was extracted to end.
     */
    @Test
    fun theOutsideViewSpeaksOnlyPercent() {
        assertNoDenominator(ScreenId.Ghost3, outBy = OutBy.Revoked)

        val vals = PanelVals(PanelState(screen = ScreenId.Ghost3, outBy = OutBy.Revoked))
        assertEquals(
            "${vals.outsideLit * 100 / PanelVals.METER_SEGMENTS}%", vals.outsidePercent,
            "Resident progress and the bar under it disagree",
        )
        assertEquals(
            "${vals.egressLit * 100 / PanelVals.METER_SEGMENTS}%", vals.egressPercent,
            "the true Egress number and the bar under it disagree",
        )
        runDesktopComposeUiTest {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Ghost3, outBy = OutBy.Revoked))
                }
            }
            // Read off the screen rather than off the model, because the defect being guarded was
            // a literal in a composable and a test reading the model would not have seen it.
            onAllNodesWithText(vals.outsidePercent).assertCountEquals(1)
            onAllNodesWithText(vals.egressPercent).assertCountEquals(1)
        }
    }

    /**
     * **The couch's meeting screen carries no meter at all, and must not grow one.**
     *
     * It is the one in-play screen whose whole subject is other people's choices, and the natural
     * addition to it — *while you are watching, here is where the Residents are* — would be the
     * meter arriving on a screen nobody swept, beside a live ballot, in front of the reader with
     * the most time to do arithmetic. Swept here so that it fails on arrival rather than on review.
     */
    @Test
    fun theCouchsMeetingScreenPrintsNoTotalEither() {
        assertNoDenominator(ScreenId.GhostMeeting, outBy = OutBy.Revoked)
        assertNoDenominator(ScreenId.Ghost2, outBy = OutBy.Revoked)
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
