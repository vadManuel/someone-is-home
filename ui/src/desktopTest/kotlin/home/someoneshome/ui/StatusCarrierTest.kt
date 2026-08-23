package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The status carrier, and the two words that ever really go in it** (D-078, rule 9).
 *
 * The slot is blank all evening and then says one thing: what happened to you, once something has.
 * `REVOKED` is system power the house lent an Insider; `RESTRAINED` is a physical act the room
 * performed and the house could only ratify. **They are not synonyms and the whole point of the
 * slot is that a person can tell them apart** — which is a claim about a rendered string in a
 * fixed-width zone, not about a `when` branch.
 *
 * ### Why this is worth a test rather than a look
 *
 * The status row's left zone is 30% of the panel and holds two glyphs before it holds a word.
 * `UNREGISTERED` does not fit and ellipsizes, which was measured and accepted: it is the fallback
 * for a **missing cause**, chosen because it asserts only that you are out, and `UNREGISTER…` still
 * asserts exactly that.
 *
 * That acceptance is only safe while the two real words fit, and nobody had checked. A screenshot
 * sweep caught the carrier reading `UNREGISTE…` on a bench that had jumped to a ghost screen with
 * no cause on it — a bench artefact, and the thing it showed was that **the field truncates, and
 * nothing anywhere said RESTRAINED was the longest word that would ever go there.**
 *
 * ### ⚠️ Measured: the slot draws exactly ten characters, and RESTRAINED is ten
 *
 * It fits, and it fits with **no margin at all** — one more character in either the word or the
 * tracking and the distinction rule 9 exists to keep legible is drawn as `RESTRAINE…`. The comment
 * on `LEFT_ZONE` said the two fit *with room to spare*; they do not, and this file is where that
 * is now written down as something that fails rather than as something somebody remembered.
 *
 * ### The measure is the last visible character, not `hasVisualOverflow`
 *
 * `hasVisualOverflow` reports true for all three words on this row, including the two that are
 * drawn whole, because the Label's line height exceeds the band it sits in — so it answers a
 * question about the band and not about the word. `getLineEnd(visibleEnd = true)` answers the one
 * being asked: how much of this string a person can actually read.
 */
@OptIn(ExperimentalTestApi::class)
class StatusCarrierTest {

    private fun carrierOn(outBy: OutBy?): String =
        PanelVals(PanelState(screen = ScreenId.Ghost3, outBy = outBy)).carrier

    /**
     * How many characters of the carrier are actually drawn, on the couch's screen, for the cause
     * that puts the word there.
     *
     * Read off the layout rather than off the semantics: the node reports the whole string even
     * when it is drawn as `UNREGISTER…`, so a text assertion is exactly the shape of test that
     * missed this in the first place.
     */
    private fun charactersDrawn(outBy: OutBy?): Int {
        var drawn = 0
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Ghost3, outBy = outBy))
                }
            }
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithText(carrierOn(outBy)).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            drawn = results.first().getLineEnd(0, visibleEnd = true)
        }
        return drawn
    }

    /**
     * **RESTRAINED fits whole, and it is the longest word the carrier will ever be given.**
     *
     * Both halves matter and only together. Rendering it and finding it fits says nothing about the
     * next cause somebody adds; asserting a length says nothing about whether ten characters still
     * fit after a tracking change. So the sweep walks every cause the game can actually produce —
     * [OutBy] is a closed set, so a new member appears here at compile time — and asserts each one
     * is drawn to its last character and is no longer than the word this bound is named for.
     */
    @Test
    fun restrainedFitsAndIsTheLongestWordTheCarrierEverHolds() {
        val longest = carrierOn(OutBy.Restrained)
        assertEquals("RESTRAINED", longest)

        for (cause in OutBy.entries) {
            val word = carrierOn(cause)
            assertTrue(
                word.length <= longest.length,
                "$cause says $word, which is longer than RESTRAINED — the carrier draws ten " +
                    "characters and both ways out of a round have to fit inside them",
            )
            assertEquals(
                word.length, charactersDrawn(cause),
                "$word is clipped in the status row; REVOKED and RESTRAINED are the one " +
                    "distinction rule 9 exists to keep legible",
            )
        }
    }

    /**
     * **UNREGISTERED is the one that loses, deliberately** — asserted so the concession stays a
     * concession, and so the width it loses at is written down.
     *
     * It is the fallback for a cause the phone was never told, it asserts only that you are out,
     * and `UNREGISTER…` still asserts exactly that. **Ten characters is also RESTRAINED's exact
     * length**, which is the reason this assertion is on the number rather than on the fact of
     * truncation: the margin between *the distinction is legible* and *the distinction is clipped*
     * is zero characters wide, and a change that narrows the zone shows up here first.
     *
     * If it ever stops truncating, the left zone has grown — and the last time somebody widened it
     * to make this word fit, its final character went under the display cutout instead, which is a
     * truncation nobody can see.
     */
    @Test
    fun theMissingCauseIsTheWordThatEllipsizes() {
        assertEquals("UNREGISTERED", carrierOn(null))
        assertEquals(
            10, charactersDrawn(null),
            "the carrier's width moved; RESTRAINED is ten characters and has no margin, so " +
                "check both directions — a narrower zone clips it, and a wider one was last " +
                "gained by pushing the word under the display cutout",
        )
    }
}
