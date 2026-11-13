package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The status carrier, and every word that ever goes in it, drawn whole** (D-078, D-204, rule 9).
 *
 * The slot is blank all evening and then says one thing: what happened to you, once something has.
 * `REVOKED` is system power the house lent an Insider; `RESTRAINED` is a physical act the room
 * performed and the house could only ratify. **They are not synonyms and the whole point of the
 * slot is that a person can tell them apart**, which is a claim about a rendered string, not about
 * a `when` branch.
 *
 * ### D-204 retired the concession this file used to hold
 *
 * Beside the Island the carrier had ten characters: RESTRAINED fitted with no margin and
 * UNREGISTERED, the fallback for a missing cause, was drawn as `UNREGISTER…` on purpose. Under the
 * Island the strip is the mockup's, the carrier has the row's middle, and all three words fit. So
 * the guard is now the plain one: **every word the carrier can be given is drawn to its last
 * character**, on the narrowest panel the canvas is drawn at.
 *
 * ### The measure is the last visible character, not `hasVisualOverflow`
 *
 * `getLineEnd(visibleEnd = true)` answers the question being asked: how much of this string a
 * person can actually read. The semantics node reports the whole string even when it is drawn cut.
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
     * Every cause the game can produce, and the missing one. [OutBy] is a closed set, so a new member
     * appears here at compile time.
     */
    @Test
    fun everyWordTheCarrierHoldsIsDrawnWhole() {
        for (cause in OutBy.entries + listOf<OutBy?>(null)) {
            val word = carrierOn(cause)
            assertEquals(
                word.length, charactersDrawn(cause),
                "$word is clipped in the status strip; REVOKED and RESTRAINED are the one " +
                    "distinction rule 9 exists to keep legible",
            )
        }
        assertEquals("UNREGISTERED", carrierOn(null))
    }
}
