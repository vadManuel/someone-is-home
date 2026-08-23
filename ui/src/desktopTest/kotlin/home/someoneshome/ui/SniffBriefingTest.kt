package home.someoneshome.ui

import home.someoneshome.model.MarkerShapes

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Sniff's instruction is read at the card, because Sniff's own screen cannot be read** (E-S1-1,
 * D-137).
 *
 * Every other Subroutine writes what to do across the top of its own screen. Sniff's screen is
 * black — literally, every colour on it, until the player has answered — because it is the roster's
 * only *short dark* and the whole value of that cell is that doing it makes no light. A sentence
 * drawn there is black ink on black glass.
 *
 * So the sentence moves one screen earlier, onto the caught scan, which is the last lit surface
 * before the work opens and the last moment the player is looking at their phone rather than at a
 * doorway. **Above BEGIN**, so it is read on the way to the control rather than found under a
 * thumb.
 *
 * ### ⚠️ The copy is a draft and the placement is what is being asserted
 *
 * The wording is a build-time draft in D-137's own register — *the player answers which group was
 * bigger* — and it is flagged on `Subroutine.briefing`, not settled. What these tests hold is the
 * structural half: the one Subroutine that needs a briefing has one, the nine that say it on their
 * own screens do not, and the caught scan draws whatever it is given.
 */
@OptIn(ExperimentalTestApi::class)
class SniffBriefingTest {

    private fun caught(of: Subroutine) = PanelState(
        screen = ScreenId.ScanCaught,
        current = CurrentSubroutine(
            subroutine = of,
            instruction = "READ THE\nCOUNTER",
            room = "STUDY",
            marker = MarkerShapes["triangle_up"],
            index = 2,
            total = 6,
            done = 1,
        ),
    )

    /**
     * **Sniff has a briefing and it is the only one that does.**
     *
     * The second half is the one that keeps the first honest. A briefing on every card would be a
     * paragraph standing between a player and the work, on the screen the design most wants them to
     * leave quickly — and it would make the surface that exists for the unreadable screen ordinary,
     * which is how a special case stops being noticed.
     */
    @Test
    fun sniffIsTheOneSubroutineBriefedAtTheCard() {
        assertNotNull(
            Subroutine.Sniff.briefing,
            "the one Subroutine whose own screen cannot carry an instruction has no briefing",
        )
        for (other in Subroutine.entries - Subroutine.Sniff) {
            assertNull(
                other.briefing,
                "${other.label} is briefed at the card as well as on its own screen — see " +
                    "Subroutine.briefing for why the ten are not treated alike",
            )
        }
        assertEquals(
            LightSignature.Dark, Subroutine.Sniff.light,
            "SNIFF stopped being a dark Subroutine, which is the entire reason this briefing " +
                "cannot live on its own screen",
        )
    }

    /** The caught scan draws it, above BEGIN, on the way to the work. */
    @Test
    fun theCaughtScanReadsSniffsBriefingBeforeBegin() {
        val briefing = assertNotNull(Subroutine.Sniff.briefing)
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(caught(Subroutine.Sniff)) } }
            onAllNodesWithText(briefing).assertCountEquals(1)
            onAllNodesWithText("BEGIN").assertCountEquals(1)
        }
    }

    /**
     * **A Subroutine that says it on its own screen says nothing here**, so the briefing stays the
     * exception it was built as rather than becoming the shape of the screen.
     */
    @Test
    fun aCaughtScanForAnythingElseCarriesNoBriefing() {
        val briefing = assertNotNull(Subroutine.Sniff.briefing)
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(caught(Subroutine.Interrupt)) } }
            onAllNodesWithText(briefing).assertCountEquals(0)
            onAllNodesWithText("BEGIN").assertCountEquals(1)
        }
    }

    /**
     * **And the dark screen still says nothing a player could read.**
     *
     * The briefing moved because the screen emits nothing; a build that also started drawing it
     * there would have solved the problem twice, and the second copy would be the one that lights
     * up the darkest surface in the game. Asserted as an absence on the screen itself, in both
     * roles, because *fully dark* is a claim about pixels and this is the file that owns the
     * sentence.
     */
    @Test
    fun sniffsOwnScreenDoesNotDrawTheBriefing() {
        val briefing = assertNotNull(Subroutine.Sniff.briefing)
        for (role in PanelRole.entries) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(PanelState(screen = ScreenId.SubSniff, role = role))
                    }
                }
                onAllNodesWithText(briefing).assertCountEquals(0)
            }
        }
    }
}
