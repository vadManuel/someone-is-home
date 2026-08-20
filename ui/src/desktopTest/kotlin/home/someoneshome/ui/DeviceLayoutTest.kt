package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Every screen, against a phone this machine is not.**
 *
 * The 56 screens were designed and reviewed on a desktop window, which has no notch, no rounded
 * corners and no home indicator. On the first phone they ever ran on, ONE strip of screen turned
 * out to have five separate faults — the title under the Dynamic Island, the boot progress under
 * the home indicator, the status row above the pill rather than level with it, its rule crossing
 * the pill, and the end glyphs shaved by the corner radius. Every one was found by a person
 * holding a phone, and not one of them was visible where the screens were built.
 *
 * The other 55 screens have still never been looked at on hardware. This renders them all against
 * simulated insets and asserts nothing lands where the hardware will eat it. It is the guard the
 * desktop preview structurally cannot be.
 *
 * ### It checks three regions, and all three come from the insets themselves
 *
 * No corner radius is hardcoded and no Island width is guessed — Compose reports that a cutout
 * exists and nothing about its size, so any number here would be an invention. The regions are
 * defined by the insets the platform does report:
 *
 * - **the cutout band** — a centred span across the top, where the pill sits
 * - **the home indicator** — the full width of the bottom inset
 * - **the deep corners** — inside the side inset AND the top/bottom inset at once
 */
class DeviceLayoutTest {

    private val out = File("build/layout-faults").apply { mkdirs() }

    /**
     * An iPhone 16 Pro, measured off the device rather than looked up: at density 3.0,
     * `safeDrawing` reports top 62 and bottom 34, and `safeContent` reports 16 each side.
     *
     * Converted to the design's units at the scale a 393pt panel implies.
     */
    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

    /** The pill occupies roughly a third of the width, centred. Generous on purpose. */
    private val cutoutFraction = 0.36f

    /**
     * The bottom rule of the status band is a full-width divider sitting ON the band's boundary.
     * That is deliberate — a divider stopping short of each edge would read as a shorter bar, not
     * a safer one — so it runs through the cutout span AND both top corners by design, and every
     * top check stops just above it.
     *
     * Without this the guard fired on all 112 renders at (0,88), which is the rule and not a
     * fault. A check that fails on everything gets switched off.
     */
    private val ruleAllowance = 3

    /** Half a percent, which absorbs an antialiased edge and nothing that could be read. */
    private val TOLERANCE = 0.005

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun nothingLandsWhereTheHardwareEatsIt() {
        val w = 300
        val h = 650
        val failures = mutableListOf<String>()
        var checked = 0

        for (id in ScreenId.entries) {
            val outBy = when (id) {
                ScreenId.Restrained -> OutBy.Restrained
                ScreenId.Revoked, ScreenId.Ghost2, ScreenId.Ghost3, ScreenId.GhostMeeting ->
                    OutBy.Revoked
                else -> null
            }
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = w * 2, height = h * 2) {
                    setContent {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            Box(Modifier.size((w * 2).dp, (h * 2).dp)) {
                                DeviceCanvas(insets = insets) {
                                    Screen(PanelState(screen = id, role = role, outBy = outBy))
                                }
                            }
                        }
                    }
                    val img = onRoot().captureToImage().toAwtImage()
                    val faults = faultsIn(img, "${id.name}/${role.name}")
                    // A failing render is written out. A guard that reports coordinates and no
                    // picture sends you reasoning about a screen you cannot see.
                    if (faults.isNotEmpty()) {
                        ImageIO.write(
                            img, "png",
                            File(out, "${id.name.lowercase()}-${role.name.lowercase()}.png"),
                        )
                    }
                    failures += faults
                    checked++
                }
            }
        }

        // Every fault to stdout as well as the assertion message: a truncated list sends you
        // fixing the first twelve and guessing at the rest.
        failures.forEach { println("LAYOUT  $it") }

        assertTrue(checked >= ScreenId.entries.size * 2, "only $checked renders — screens missing")
        assertTrue(
            failures.isEmpty(),
            "${failures.size} fault(s) across $checked renders — ink under the device's own hardware:" +
                failures.take(12).joinToString("") { "\n  $it" } +
                if (failures.size > 12) "\n  … and ${failures.size - 12} more" else "",
        )
    }

    /**
     * A region is at fault when it is NOT UNIFORM.
     *
     * The first version asked whether pixels differed from the image's most common colour, and
     * that was wrong in a way worth keeping written down: `ScanMarker` is a light-field screen
     * with a large dark camera viewport, so the viewport is the modal colour and every Bone pixel
     * in the status band counted as ink. The guard reported six faults on five screens that had
     * none, which is how a check gets disbelieved and then deleted.
     *
     * Uniformity is the property that actually matters. Content is variation; a flat fill is not,
     * whatever colour it is — and a flat fill under the pill is not merely tolerable but REQUIRED
     * on the lamp screens, where the amber must reach the edges or the core has emitted light the
     * hardware then swallows.
     *
     * [TOLERANCE] absorbs the antialiased edge of something legitimately adjacent, such as the
     * status rule sitting on the band's boundary.
     */
    private fun faultsIn(img: BufferedImage, label: String): List<String> {
        val out = mutableListOf<String>()

        val top = img.px(insets.top.value)
        val bottom = img.px(insets.bottom.value)
        val side = img.px(insets.side.value)
        val cutoutHalf = (img.width * cutoutFraction / 2).toInt()
        val centre = img.width / 2

        fun scan(name: String, x0: Int, y0: Int, x1: Int, y1: Int) {
            val xs = x0.coerceAtLeast(0) until x1.coerceAtMost(img.width)
            val ys = y0.coerceAtLeast(0) until y1.coerceAtMost(img.height)
            if (xs.isEmpty() || ys.isEmpty()) return

            val counts = HashMap<Int, Int>()
            for (y in ys) for (x in xs) {
                val c = img.getRGB(x, y)
                counts[c] = (counts[c] ?: 0) + 1
            }
            val total = xs.count() * ys.count()
            val fill = counts.maxByOrNull { it.value }!!
            val differing = total - fill.value
            if (differing > total * TOLERANCE) {
                var at = ""
                outer@ for (y in ys) for (x in xs) {
                    if (img.getRGB(x, y) != fill.key) { at = "first at ($x,$y)"; break@outer }
                }
                out += "$label — $name: $differing of $total px differ from the fill, $at"
            }
        }

        scan("under the cutout", centre - cutoutHalf, 0, centre + cutoutHalf, top - ruleAllowance)
        scan("under the home indicator", 0, img.height - bottom, img.width, img.height)
        scan("in the top-left corner", 0, 0, side, top - ruleAllowance)
        scan("in the top-right corner", img.width - side, 0, img.width, top - ruleAllowance)
        scan("in the bottom-left corner", 0, img.height - bottom, side, img.height)
        scan("in the bottom-right corner", img.width - side, img.height - bottom, img.width, img.height)
        return out
    }

    /**
     * Design units to image pixels.
     *
     * The canvas is rendered [DESIGN_WIDTH] units wide, so the ratio is just the image's width
     * over that — no density to guess, and it stays correct if the render size changes.
     */
    private fun BufferedImage.px(designUnits: Float): Int =
        (designUnits * width / DESIGN_WIDTH).toInt()

}
