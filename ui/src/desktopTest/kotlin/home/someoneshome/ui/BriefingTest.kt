package home.someoneshome.ui

import home.someoneshome.model.MarkerShapes

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Every Subroutine is briefed at the card, and none of them explains itself inside** (D-188).
 *
 * This file was `SniffBriefingTest` and it asserted the opposite: that Sniff was the one Subroutine
 * briefed before the work opened and the other nine said it on their own screens. E-S1-1 closed
 * that as a *placement* for Sniff alone, because Sniff's screen is black until the answer (D-137)
 * and a sentence drawn there is black ink on black glass. **The ruling inverted the pattern rather
 * than extending the exception**, so the assertions invert with it and the file changed its name to
 * say so.
 *
 * The argument is the light signature. A rating of bright, medium or dark is a promise about how
 * much light you are standing in while you work, and an in-screen instruction line is lit pixels for
 * the whole duration of the task — it makes a dark Subroutine less dark and a bright one longer.
 * Reading happens at the card, where the scan has already lit the player up.
 *
 * ### ⚠️ The copy is a draft and the placement is what is being asserted
 *
 * Nine of the ten lines are build-time drafts in Sniff's register — second person, imperative, what
 * the phone will do and then what you do about it — and they are flagged on `Subroutine.briefing`
 * and in the worklog, not settled. What these tests hold is the structural half: **all ten are
 * briefed at the card, each one only at its own card, and no Subroutine screen draws its own line.**
 */
@OptIn(ExperimentalTestApi::class)
class BriefingTest {

    private fun caught(of: Subroutine) = PanelState(
        screen = ScreenId.ScanCaught,
        current = CurrentSubroutine(
            subroutine = of,
            room = "STUDY",
            marker = MarkerShapes["triangle_up"],
            index = 2,
            total = 6,
            done = 1,
        ),
    )

    /**
     * **All ten have one, and the type is what makes that true.**
     *
     * `briefing` is non-null now, so the interesting assertion is no longer *which of them has one*
     * — the compiler settles that — but that none of them is briefed with an empty string or with
     * somebody else's line. A duplicate would be two Subroutines a player could not tell apart at
     * the last screen before the work opens.
     */
    @Test
    fun everySubroutineIsBriefedAndNoTwoShareALine() {
        for (subroutine in Subroutine.entries) {
            assertTrue(
                subroutine.briefing.isNotBlank(),
                "${subroutine.label} reaches a player with nothing said about it (D-188)",
            )
        }
        assertEquals(
            Subroutine.entries.size,
            Subroutine.entries.map { it.briefing }.distinct().size,
            "two Subroutines are briefed with the same sentence",
        )
        assertEquals(
            LightSignature.Dark, Subroutine.Sniff.light,
            "SNIFF stopped being a dark Subroutine, which is the reason a briefing could not live " +
                "on its own screen and therefore the reason this pattern was found at all",
        )
    }

    /**
     * **The caught scan draws the briefing of the Subroutine the card opened, above BEGIN.**
     *
     * Every one of the ten, rendered, rather than one of them and a claim about the rest: the
     * screen reads a field off the roster, so the failure this catches is the field being read from
     * the wrong row or not being read at all.
     */
    @Test
    fun everyCaughtScanDrawsItsOwnBriefingBeforeBegin() {
        for (subroutine in Subroutine.entries) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                setContent { DeviceCanvas(insets = PanelInsets()) { Screen(caught(subroutine)) } }
                onAllNodesWithText(subroutine.briefing).assertCountEquals(1)
                onAllNodesWithText("BEGIN").assertCountEquals(1)
            }
        }
    }

    /**
     * **And only its own.** A card that drew a second Subroutine's line would be a player reading
     * instructions for work they are not about to do, in the dark, with one attempt.
     */
    @Test
    fun aCaughtScanCarriesNobodyElsesBriefing() {
        for (subroutine in Subroutine.entries) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                setContent { DeviceCanvas(insets = PanelInsets()) { Screen(caught(subroutine)) } }
                for (other in Subroutine.entries - subroutine) {
                    onAllNodesWithText(other.briefing).assertCountEquals(0)
                }
            }
        }
    }

    /**
     * **The nine deletions, asserted as pixels rather than as a diff** (D-188).
     *
     * The screens used to carry their instruction across the top and the ruling strips it: the
     * briefing is read once, at the card, and a screen that also drew it would have made a dark
     * Subroutine less dark for the whole of the task. Sniff's own screen is in this loop for the
     * reason it always was — *fully dark* is a claim about pixels — and it now proves the same thing
     * the other nine do.
     *
     * **Both roles**, because rule 8's fake is the same screen and a line that appeared for one of
     * them would be an alignment tell on a lit screen in a dark room.
     */
    @Test
    fun noSubroutineScreenDrawsItsOwnBriefing() {
        for (subroutine in Subroutine.entries) {
            val screen = subroutine.screen ?: continue
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 300, height = 650) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(PanelState(screen = screen, role = role))
                        }
                    }
                    onAllNodesWithText(subroutine.briefing).assertCountEquals(0)
                }
            }
        }
    }

    /**
     * **The flavour line is gone from every screen in the game** (the owner's 2026-08-24 ruling).
     *
     * `CurrentSubroutine.instruction` carried a fake-technical sentence — *RESYNC THE KEEPALIVE* —
     * naming what one instance of a kind asked for. **Nothing on the wire ever carried it**, so it
     * was the port's own words printed over whatever Subroutine a real scan had opened, or it was
     * absent; the owner deleted the slot rather than commission a carrier, and noted the register
     * as a v2 revisit.
     *
     * The field's deletion is the compiler's half. This is the half that catches it coming back as
     * a literal in a fixture — which is exactly how it got onto four screens the first time — and
     * it sweeps **every screen in the game in both roles**, because the caught scan was where it
     * was drawn and not where a helpful reinstatement would necessarily put it.
     */
    @Test
    fun theDeletedFlavourLineIsOnNoScreenInTheGame() {
        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(PanelState(role = role).arrivingAt(id))
                        }
                    }
                    // Substring, and on the half of the phrase no other copy in the game uses: the
                    // line was drawn across two rendered lines, so the whole sentence is not one
                    // node and a search for it would pass on a screen still drawing it.
                    onAllNodesWithText("KEEPALIVE", substring = true).assertCountEquals(0)
                }
            }
        }
    }
}
