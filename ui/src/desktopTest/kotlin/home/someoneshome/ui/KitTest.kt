package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
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
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
                            Box(Modifier.testTag("three")) { Words("A\nB\nC", size = 52, lineHeight = 0.85) }
                        }
                    }
                }
            }
            for ((tag, want) in listOf("clock" to 124 * 0.8, "two" to 2 * 44 * 0.9, "three" to 3 * 52 * 0.85)) {
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

    /**
     * **The hatch lies the way the mockup's CSS lays it** (D-202): `45deg` runs towards the
     * top-right, so the stripes run top-left to bottom-right, `band` is measured across a stripe,
     * and the first stripe starts at the bottom-left corner. The first port drew them the other way
     * up, and nothing but a side-by-side render noticed.
     *
     * Read off the pixels: a step down-right stays on the same stripe; a step of one band
     * up-right crosses onto the other colour most of the time.
     */
    @Test
    fun `the hatch runs top-left to bottom-right, as the mockup's 45deg does`() {
        runDesktopComposeUiTest(width = 240, height = 120) {
            setContent { Box(Modifier.size(240.dp, 120.dp).hatch(Color.White, Color.Black, band = 12.dp)) }
            val img = onRoot().captureToImage().toAwtImage()
            fun lit(x: Int, y: Int) = (img.getRGB(x, y) and 0xFF) > 128
            var along = 0
            var across = 0
            var n = 0
            for (y in 20 until 90 step 3) for (x in 20 until 200 step 3) {
                n++
                if (lit(x, y) != lit(x + 9, y + 9)) along++
                if (lit(x, y) != lit(x + 9, y - 9)) across++
            }
            assertTrue(along < n / 10, "a step down-right left its stripe at $along of $n points: the hatch runs the wrong way")
            assertTrue(across > n / 2, "a band's step up-right stayed on one colour at ${n - across} of $n points")
            assertTrue(lit(1, 118), "the first stripe does not start at the bottom-left corner, as CSS's does")
        }
    }
}
