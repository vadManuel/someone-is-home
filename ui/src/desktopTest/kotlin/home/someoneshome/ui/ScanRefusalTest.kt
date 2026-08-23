package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test

/**
 * **The two scan refusals, and the words the ruling gave each of them** (D-124).
 *
 * A scan can be refused for two different facts and the ruling gives them two different
 * vocabularies on purpose. A **registered** card with no work for *you* answers **NOTHING FOR YOU
 * HERE** — true, non-committal, and silent about whether the card is somebody else's anchor. The
 * **unregistered** answer is reserved for paper this home has never seen, where the honest reply
 * is that the card is not part of the game.
 *
 * The screen said `NOT YOURS` for three revisions after the ruling named different words, which is
 * E-L4-2. The two said the same thing in English and that is exactly why it survived: a
 * paraphrase of a copy ruling reads correct to everybody except the next ruling.
 *
 * ### Why the two must sound different, and why the frame must not
 *
 * They must **sound** different because the host needs the unregistered case loud during setup and
 * the player needs the other case unremarkable during a round; collapsing them would let a
 * Resident sweeping cards separate *registered but not mine* from *not registered at all*, which is
 * a detector built out of two refusals.
 *
 * They must **look** identical because one of the two is reachable only by a Resident. If the
 * frames differed — a different icon, a different set of controls — an onlooker would read which
 * refusal you got from across a dark room, and one of the two answers is role-bearing. So the
 * words are asserted apart and the frame is asserted together.
 */
@OptIn(ExperimentalTestApi::class)
class ScanRefusalTest {

    private fun refusal(screen: ScreenId, block: SemanticsNodeInteractionsProvider.() -> Unit) =
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = screen)) } }
            block()
        }

    /**
     * **The refused scan says D-124's words**, and the paraphrase it replaced is nowhere on the
     * device.
     *
     * The headline is two lines because it will not sit on one at the size the frame draws it; the
     * assertion carries the break so that a later reflow is a decision somebody makes here rather
     * than one a test tolerates.
     */
    @Test
    fun aRefusedScanSaysNothingForYouHere() = refusal(ScreenId.ScanBad) {
        onAllNodesWithText("NOTHING FOR\nYOU HERE").assertCountEquals(1)
        onAllNodesWithText("NOT YOURS").assertCountEquals(0)
    }

    /**
     * **The unregistered card keeps its own words** — the half of D-124 that makes the other half
     * safe.
     *
     * Asserted here rather than assumed, because the tempting cleanup after the rename is to give
     * both screens one string. That would be the detector: two facts with one answer are
     * separable by nothing, and two facts with two answers are separable only by the person
     * holding the card, which is what the ruling wants.
     */
    @Test
    fun anUnregisteredCardIsToldSoAndIsToldSomethingElse() = refusal(ScreenId.ScanUnknown) {
        onAllNodesWithText("NOT A MARKER").assertCountEquals(1)
        onAllNodesWithText("NOTHING FOR\nYOU HERE").assertCountEquals(0)
    }

    /**
     * **One frame, two sets of words.** Both refusals offer the same two ways out, in the same
     * order, so what an onlooker can read off the screen is the shape of a refusal and never which
     * one it was.
     */
    @Test
    fun bothRefusalsOfferTheSameTwoWaysOut() {
        for (screen in listOf(ScreenId.ScanBad, ScreenId.ScanUnknown)) {
            refusal(screen) {
                onAllNodesWithText("SCAN ANOTHER").assertCountEquals(1)
                onAllNodesWithText("SEE MY SUBROUTINES").assertCountEquals(1)
            }
        }
    }
}
