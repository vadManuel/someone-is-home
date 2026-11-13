package home.someoneshome.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The two walk-ins are one layout** (D-202, D-104).
 *
 * At a meeting call the living walk in on [AssembleScreen] and a newly revoked player walks in on
 * [Ghost2Screen], through a room the house has just lit and among people who have not yet been
 * told. If the two screens drew their block, their rule or their count in different places, a
 * glance at a phone across the room would say which kind of player was holding it. Only the words
 * may differ.
 */
@OptIn(ExperimentalTestApi::class)
class WalkInParityTest {

    private class Drawn(val block: DpRect, val count: DpRect, val headlineTop: Float)

    private fun DesktopComposeUiTest.drawn(screen: ScreenId, outBy: OutBy?, headline: String): Drawn {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(PanelState(outBy = outBy).arrivingAt(screen))
            }
        }
        return Drawn(
            block = onNodeWithText("I AM HERE").getBoundsInRoot(),
            count = onNodeWithText("CHECKED IN", substring = true).getBoundsInRoot(),
            headlineTop = onNodeWithText(headline, substring = true).getBoundsInRoot().top.value,
        )
    }

    @Test
    fun theLivingAndTheRevokedWalkInOnTheSameLayout() {
        lateinit var living: Drawn
        lateinit var revoked: Drawn
        runDesktopComposeUiTest(width = 300, height = 650) {
            living = drawn(ScreenId.Assemble, null, "WALK TO THE")
        }
        runDesktopComposeUiTest(width = 300, height = 650) {
            revoked = drawn(ScreenId.Ghost2, OutBy.Revoked, "STAND")
        }
        assertEquals(living.block, revoked.block, "I AM HERE sits differently for the revoked")
        assertEquals(living.count, revoked.count, "the check-in count sits differently for the revoked")
        assertEquals(
            living.headlineTop, revoked.headlineTop,
            "the headline starts at a different height for the revoked",
        )
    }
}
