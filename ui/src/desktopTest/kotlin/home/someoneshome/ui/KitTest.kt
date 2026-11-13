package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **The restyle's kit holds D-202's numbers, whatever the panel is** (D-202).
 *
 * Three things a restyled screen leans on without checking: inside [Mockup], the panel is 360 of
 * its units wide, so a mockup pixel is a unit; every control the kit draws is a 56-unit target or
 * taller; and a caption cannot be set under the 18px floor.
 */
@OptIn(ExperimentalTestApi::class)
class KitTest {

    @Test
    fun `inside the bridge the panel is the mockup's 360 units, at any phone width`() {
        for (width in listOf(390, 600, 430)) {
            var seen: Dp? = null
            runDesktopComposeUiTest(width = width, height = 900) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Mockup { BoxWithConstraints { seen = maxWidth } }
                    }
                }
                waitForIdle()
            }
            assertEquals(MOCKUP_WIDTH, seen!!.value, 0.01f, "a $width-wide panel is not 360 mockup units")
        }
    }

    @Test
    fun `every control the kit draws is a 56 target or taller`() {
        runDesktopComposeUiTest(width = 360, height = 1400) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Mockup {
                        val p = Palette.DARK
                        Column {
                            KitPrimary("A", p, Modifier.testTag("primary")) {}
                            KitSecondary("B", p, Modifier.testTag("secondary")) {}
                            KitQuiet("C", p, Modifier.testTag("quiet")) {}
                            Box(Modifier.testTag("nav")) { KitNavRow("D", p, onClick = {}) }
                        }
                    }
                }
            }
            // Bounds come back in the node's own density, which inside the bridge is the mockup unit.
            for (tag in listOf("primary", "secondary", "quiet", "nav")) {
                val tall = onNodeWithTag(tag).getBoundsInRoot().let { it.bottom - it.top }
                assertTrue(tall >= 56.dp - 0.5.dp, "$tag is ${tall.value} tall, under D-202's 56")
            }
        }
    }

    @Test
    fun `a bridge inside a bridge is still 360 units`() {
        var seen: Dp? = null
        runDesktopComposeUiTest(width = 600, height = 900) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Mockup { Mockup { BoxWithConstraints { seen = maxWidth } } }
                }
            }
            waitForIdle()
        }
        assertEquals(MOCKUP_WIDTH, seen!!.value, 0.01f, "a nested bridge scaled the panel twice")
    }

    @Test
    fun `a line box is as tall as the mockup's CSS makes it`() {
        runDesktopComposeUiTest(width = 360, height = 900) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Mockup {
                        Column {
                            Box(Modifier.testTag("clock")) { Words("21:04", size = 124, lineHeight = 0.8) }
                            Box(Modifier.testTag("two")) { Words("TURN\nTO", size = 44, lineHeight = 0.9) }
                        }
                    }
                }
            }
            for ((tag, want) in listOf("clock" to 124 * 0.8, "two" to 2 * 44 * 0.9)) {
                val tall = onNodeWithTag(tag).getBoundsInRoot().let { it.bottom - it.top }.value
                assertEquals(want.toFloat(), tall, 1f, "$tag's line box is $tall tall, not the mockup's $want")
            }
        }
    }

    @Test
    fun `a caption under 18 is refused`() {
        assertFailsWith<IllegalArgumentException> {
            runDesktopComposeUiTest {
                setContent { KitCaption("TOO SMALL", Palette.DARK, size = 17) }
            }
        }
    }
}
