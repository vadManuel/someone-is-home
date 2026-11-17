package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **A field being typed into is above the keyboard, on a phone's own proportions** (D-235).
 *
 * Found on real iPhones on 2026-11-16: in the lobby's one-line screen the keyboard covered the
 * field and nobody could see what they typed. The desktop has no keyboard, so the keyboard is the
 * inset the phone reports (`PanelInsets.keyboard`, read from `WindowInsets.ime`) passed in by hand,
 * at the height an iPhone 13 Pro's keyboard and its suggestion bar take (336 pt, 310 units).
 *
 * What is asserted is geometry off the semantics tree: the text field's own bounds are on the
 * panel, at least one line of its type tall, and wholly above the keyboard's top edge; and on the
 * one-line screen HAND IT OVER is above it too, so the line can be handed over without closing the
 * keyboard first.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardLayoutTest {

    /** A 13 Pro in design units: 390 by 844 pt is 360 by 779, the Island and home bar, and its keyboard. */
    private val phone = PanelInsets(top = 43.dp, bottom = 31.dp, keyboard = 310.dp)

    /** The panel's scale and the keyboard's top edge, in the root's pixels. */
    private class Geometry(val scale: Float, val keyboardTop: Float)

    private fun onA13Pro(
        screen: ScreenId,
        insets: PanelInsets = phone,
        check: DesktopComposeUiTest.(Geometry) -> Unit,
    ) = runDesktopComposeUiTest(width = 720, height = 1558) {
        val lobby = LobbyModel.sample().also { it.typeLine(LINE) }
        setContent {
            Box(Modifier.fillMaxSize()) {
                DeviceCanvas(insets = insets) { Screen(PanelState(screen = screen), lobby = lobby) }
            }
        }
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        val scale = root.width / DESIGN_WIDTH
        check(Geometry(scale, root.height - insets.keyboard.value * scale))
    }

    private fun SemanticsNodeInteraction.box(): Rect = fetchSemanticsNode().boundsInRoot

    private fun assertAbove(name: String, box: Rect, at: Geometry, minHeight: Float = 1f) {
        assertTrue(box.height >= minHeight, "$name is ${box.height}px tall, under $minHeight: squeezed away ($box)")
        assertTrue(box.top >= 0f, "$name starts above the panel ($box)")
        assertTrue(
            box.bottom <= at.keyboardTop + 0.5f,
            "$name ends at ${box.bottom}px, under the keyboard's top edge at ${at.keyboardTop}px ($box)",
        )
    }

    /** **GUARD: the one line, typed with the keyboard up, is on screen above it, and so is HAND IT OVER.** */
    @Test
    fun theOneLineAndItsButtonAreAboveTheKeyboard() = onA13Pro(ScreenId.Secret) { at ->
        // The words the player typed: a line of 32-unit type is at least 32 units tall.
        assertAbove("the one-line field", onNode(hasSetTextAction()).box(), at, minHeight = 32f * at.scale)
        assertAbove("HAND IT OVER", onNodeWithText("HAND IT OVER").box(), at)
    }

    /** The name on the way in and the home's name are above it too. */
    @Test
    fun theOtherTwoFieldsAreAboveTheKeyboard() {
        for (screen in listOf(ScreenId.Join, ScreenId.SaveName)) {
            onA13Pro(screen) { at ->
                assertAbove("the field on ${screen.name}", onNode(hasSetTextAction()).box(), at, minHeight = 17f * at.scale)
            }
        }
    }

    /** **With the keyboard down the one-line screen is what it was**: the caption and both promises. */
    @Test
    fun withTheKeyboardDownTheOneLineScreenKeepsItsWords() =
        onA13Pro(ScreenId.Secret, insets = phone.copy(keyboard = 0.dp)) {
            onNodeWithText("One line, and make it real.", substring = true).assertExists()
            onNodeWithText("THE HOUSE ONLY").assertExists()
            onNodeWithText("WHEN THE ROUND ENDS").assertExists()
        }

    private companion object {
        const val LINE = "i told everyone the dog ran away but i gave it to my cousin in leeds"
    }
}
