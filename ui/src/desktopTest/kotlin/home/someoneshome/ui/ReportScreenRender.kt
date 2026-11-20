package home.someoneshome.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The report screen, drawn: page 1 and page 2 before it, and the circle at nothing, half and full**
 * (D-307, the owner's 2026-11-20 rulings).
 *
 * Driven through [FlowHost] at 600×1300 with an iPhone 16 Pro's insets, with the composition's own
 * clock as the phone's monotonic clock, so the fill is the one a phone draws: stamped from the
 * slot's held time when the slot lands and read every frame after it (D-243, D-269). Writes
 * `report-page1-<role>.png`, `report-page2-<role>.png`, `report-0-<role>.png`, `report-50-<role>.png`,
 * `report-100-<role>.png` and `report-egress-<role>.png` to `build/screens`, and checks what pixels
 * can:
 *
 * - an Insider's phone and a Resident's are the same pixels at every step;
 * - the report screen is drawn at the springboard's light: its ground is page 1's ground, and every
 *   pixel under the status strip is the ground, [Ramp.Line], [Ramp.Ink] or an edge between them;
 * - the circle at half is lit over about half of its ring and at full over all of it;
 * - an Egress over the report screen dims it, and the circle goes on filling under the alert.
 */
@OptIn(ExperimentalTestApi::class)
class ReportScreenRender {

    private val out = File("build/screens").apply { mkdirs() }

    /** An iPhone 16 Pro's measured insets (`DeviceLayoutTest`). */
    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

    private fun egressOpened() = RoundMessage.EgressOpened(
        type = EgressType.Beacon,
        nodes = listOf(
            Destination(MarkerId("west-card"), "circle", "UTILITY"),
            Destination(MarkerId("east-card"), "square", "LANDING"),
        ),
        remaining = 2_400,
        haptic = Haptic.Long,
        pulseFrom = 86_402_000L,
    )

    private fun DesktopComposeUiTest.shot(): BufferedImage = onRoot().captureToImage().toAwtImage()

    private fun save(name: String, png: BufferedImage) {
        ImageIO.write(png, "png", File(out, "$name.png"))
    }

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    private class Shots(
        val page1: BufferedImage,
        val page2: BufferedImage,
        val empty: BufferedImage,
        val half: BufferedImage,
        val full: BufferedImage,
        val egress: BufferedImage,
        val fills: List<Float>,
        val afterCancel: ScreenId,
    )

    /** A housed phone of [role] on page 1, then page 2, then the report from page 2. */
    private fun walk(role: PanelRole): Shots {
        var shots: Shots? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            mainClock.autoAdvance = false
            val model = FlowModel(
                PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
                monotonicMillis = { mainClock.currentTime },
            )
            setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
            mainClock.advanceTimeByFrame()
            val page1 = shot()
            model.go(ScreenId.Page2)
            mainClock.advanceTimeByFrame()
            val page2 = shot()

            val fills = mutableListOf<Float>()
            model.reportSaid(ReportSlot.Pending(0))
            mainClock.advanceTimeByFrame()
            fills += model.report.fill.floatValue
            val empty = shot()
            // A second on the phone's clock, with the house's next answer landing half way through
            // it: the circle runs between answers and the answer does not step it.
            mainClock.advanceTimeBy(500)
            model.reportSaid(ReportSlot.Pending(480))
            mainClock.advanceTimeBy(500)
            fills += model.report.fill.floatValue
            val half = shot()
            mainClock.advanceTimeBy(1_100)
            fills += model.report.fill.floatValue
            val full = shot()
            shots = Shots(page1, page2, empty, half, full, page1, fills, ScreenId.Report)
        }
        // The Egress over the report screen, and the cancel back to the page, in a scene of their own.
        var egress: BufferedImage? = null
        var after: ScreenId? = null
        val egressFills = mutableListOf<Float>()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            mainClock.autoAdvance = false
            val model = FlowModel(
                PanelState(screen = ScreenId.Page2, role = role, houseAttached = true),
                monotonicMillis = { mainClock.currentTime },
            )
            setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
            mainClock.advanceTimeByFrame()
            model.reportSaid(ReportSlot.Pending(0))
            mainClock.advanceTimeBy(400)
            model.houseSaid(egressOpened())
            mainClock.advanceTimeByFrame()
            egressFills += model.report.fill.floatValue
            mainClock.advanceTimeBy(600)
            egressFills += model.report.fill.floatValue
            assertTrue(model.state.egressAlertUp, "$role: the alert is not over the report screen")
            assertEquals(ScreenId.Report, model.state.screen, "$role: the Egress took the report screen away")
            egress = shot()
            model.reportSaid(ReportSlot.Cancelled)
            mainClock.advanceTimeByFrame()
            after = model.state.screen
        }
        assertTrue(egressFills[1] > egressFills[0] + 0.25f, "$role: the circle stopped under the alert: $egressFills")
        val s = shots!!
        return Shots(s.page1, s.page2, s.empty, s.half, s.full, egress!!, s.fills, after!!)
    }

    /** The y the status strip ends at on this canvas: the insets' top plus the 34 strip, doubled. */
    private val belowStrip = ((45 + 34) * 600 / 360) + 4

    /** How many pixels under the strip are not the ground. */
    private fun lit(image: BufferedImage, ground: Int): Int {
        var n = 0
        for (y in belowStrip until image.height) for (x in 0 until image.width) if (image.getRGB(x, y) != ground) n++
        return n
    }

    /** Whether [rgb] lies on the segment from the ground to [ink] (an antialiased edge of that ink). */
    private fun between(rgb: Int, ground: Int, ink: Int): Boolean {
        fun ch(c: Int, s: Int) = (c shr s) and 0xff
        val t = listOf(16, 8, 0).map { s ->
            val span = ch(ink, s) - ch(ground, s)
            if (span == 0) null else (ch(rgb, s) - ch(ground, s)).toDouble() / span
        }
        val known = t.filterNotNull()
        if (known.isEmpty()) return rgb == ground
        val mean = known.average()
        if (mean < -0.02 || mean > 1.02) return false
        // Five levels of slack a channel: the glass's band and the edge's coverage each round once.
        return listOf(16, 8, 0).all { s -> abs(ch(rgb, s) - (ch(ground, s) + mean * (ch(ink, s) - ch(ground, s)))) <= 5.0 }
    }

    /**
     * Where the track's ink and the fill's ink meet at the top and bottom of a part-filled ring, an
     * edge pixel is all three of black, [line] and [ink] at once: its hue lies between the two inks'
     * (green and blue as fractions of red), and it is no brighter than the fill's ink.
     */
    private fun inTheTriangle(rgb: Int, line: Int, ink: Int): Boolean {
        fun ch(c: Int, s: Int) = (c shr s) and 0xff
        val r = ch(rgb, 16)
        if (r > ch(ink, 16)) return false
        if (r < 24) return ch(rgb, 8) <= r + 2 && ch(rgb, 0) <= r + 2
        val g = ch(rgb, 8).toDouble() / r
        val b = ch(rgb, 0).toDouble() / r
        fun ratio(c: Int, s: Int) = ch(c, s).toDouble() / ch(c, 16)
        val gs = listOf(ratio(line, 8), ratio(ink, 8))
        val bs = listOf(ratio(line, 0), ratio(ink, 0))
        return g in (gs.min() - 0.06)..(gs.max() + 0.06) && b in (bs.min() - 0.06)..(bs.max() + 0.06)
    }

    @Test
    fun renderTheReportScreenFromBothPagesAtNothingHalfAndFull() {
        val resident = walk(PanelRole.Resident)
        val insider = walk(PanelRole.Insider)
        for ((role, s) in listOf("resident" to resident, "insider" to insider)) {
            save("report-page1-$role", s.page1)
            save("report-page2-$role", s.page2)
            save("report-0-$role", s.empty)
            save("report-50-$role", s.half)
            save("report-100-$role", s.full)
            save("report-egress-$role", s.egress)
        }
        println("D-307 REPORT CIRCLE FILLS — resident ${resident.fills}, insider ${insider.fills}")

        // Both roles, the same pixels, at every step of the report.
        for ((name, pair) in listOf(
            "report at nothing" to (resident.empty to insider.empty),
            "report at half" to (resident.half to insider.half),
            "report at full" to (resident.full to insider.full),
            "report under the Egress" to (resident.egress to insider.egress),
        )) assertTrue(same(pair.first, pair.second), "$name: an Insider's report screen is not a Resident's")
        assertEquals(resident.fills, insider.fills)

        // The fill, on the phone's own clock: nothing, a second of two, and full.
        val (zero, half, full) = resident.fills
        assertEquals(0f, zero, "the circle did not start at what the house said was held")
        assertTrue(abs(half - 0.5f) < 0.02f, "a second in, the circle is not half full: $half")
        assertEquals(1f, full, "two seconds in, the circle is not full")

        // The springboard's light: page 1's ground is the report screen's ground, and every pixel
        // under the strip is the ground, the track's ink, the fill's ink, or an edge of one.
        val ground = resident.page1.getRGB(resident.page1.width / 2, resident.page1.height - 8)
        assertEquals(ground, resident.empty.getRGB(resident.empty.width / 2, resident.empty.height - 8), "the report screen's ground is not page 1's")
        val line = Ramp.Line.toArgb()
        val ink = Ramp.Ink.toArgb()
        for ((name, image) in listOf("nothing" to resident.empty, "half" to resident.half, "full" to resident.full)) {
            for (y in belowStrip until image.height) for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                assertTrue(
                    rgb == ground || between(rgb, ground, line) || between(rgb, ground, ink) || between(rgb, line, ink) ||
                        inTheTriangle(rgb, line, ink),
                    "report at $name: a pixel at ($x, $y) is #${Integer.toHexString(rgb)}, none of the springboard's inks",
                )
            }
        }
        // Lit like a ring: the empty one in the track's ink, the half lit over about half of it in the fill's.
        // The fill's ink, at full or under the glass's band (a multiply, so still brighter than the
        // track's ink in red): `Ramp.Ink`'s red is 0xFF and `Ramp.Line`'s 0x8A.
        fun inkPixels(image: BufferedImage) = (belowStrip until image.height).sumOf { y ->
            (0 until image.width).count { x -> (image.getRGB(x, y) shr 16 and 0xff) > 0xA0 }
        }
        val atHalf = inkPixels(resident.half)
        val atFull = inkPixels(resident.full)
        assertEquals(0, inkPixels(resident.empty), "the empty circle drew the fill's ink")
        assertTrue(atFull > 0 && abs(atHalf.toDouble() / atFull - 0.5) < 0.06, "the half circle is not half the full one: $atHalf of $atFull")
        assertTrue(lit(resident.full, ground) < lit(resident.page1, ground), "the report screen emits more than page 1")

        // The Egress: the dim, and the cancel back to the page the report came from.
        assertTrue(lit(resident.egress, ground) > 0)
        assertEquals(ScreenId.Page2, resident.afterCancel, "the cancel did not go back to page 2")
        assertEquals(ScreenId.Page2, insider.afterCancel)
    }
}
