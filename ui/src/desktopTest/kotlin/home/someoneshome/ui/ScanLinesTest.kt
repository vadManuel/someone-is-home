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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The glass: the design's scan lines, on every screen, carrying nothing.**
 *
 * Transcribed from the device mockup's `.screen::after` — one row of 32% black in every three,
 * over the whole panel. Worth a test rather than a glance for two reasons that pull opposite ways.
 *
 * It has to be **there on every screen**, because it is the surface rather than any screen's
 * decoration, and a screen that quietly lost it would look like a different device.
 *
 * And it has to be **identical on every screen**, for the same reason from the other side. Light
 * in this game is state (rule 5): an attenuation that varied with the screen, the role or the
 * moment would be a difference emitted into a dark room, where the one thing a player may never
 * learn from a glance at somebody else's phone is anything at all.
 *
 * ### How it is measured, and why not by looking for dark rows
 *
 * The first version of this counted rows darker than their neighbours, and counted the screens'
 * own content along with the glass — a border, a bar's top edge and a line of text are all rows
 * that are darker than the row above. It reported a period of `[8, 32, 3, 1, 2, 7, 48, 27, 4]`,
 * which is a picture of a springboard and not of any banding.
 *
 * So this measures the **ratio**, and only where the rows either side of a band are already
 * identical to each other. A flat surround is the definition of "nothing is happening here except
 * the glass", it is available on every screen in the game, and content cannot fake it.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class ScanLinesTest {

    /**
     * Rendered exactly [DESIGN_WIDTH] wide, so one design unit is one image pixel and the period
     * is three rows with nothing to round.
     */
    private val width = DESIGN_WIDTH.toInt()
    private val height = 200

    /** The band's own ratio: 32% black over whatever is under it leaves 68% of it. */
    private val expected = 0.68

    /** sRGB rounding at these levels moves the ratio by about a percent either way. */
    private val slack = 0.02

    private fun render(state: PanelState, block: (BufferedImage) -> Unit) =
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        DeviceCanvas(insets = PanelInsets()) { Screen(state) }
                    }
                }
            }
            block(onRoot().captureToImage().toAwtImage())
        }

    /**
     * **One row in three, at 68% of what is under it, with the first band on row three.**
     *
     * The period AND the phase, off the flattest surface in the app — the self-test screen, which
     * is a light field with almost nothing on it. Every row of it that can carry a band does.
     */
    @Test
    fun theGlassIsOneRowInThree() = render(PanelState(screen = ScreenId.Boot)) { img ->
        val banded = mutableListOf<Int>()
        val untouched = mutableListOf<Int>()
        for (y in 1 until img.height - 1) {
            val ratio = flatRatioAt(img, y) ?: continue
            if (ratio < 1.0 - slack) banded += y else untouched += y
        }

        assertTrue(banded.size > 40, "only ${banded.size} bands down 200 rows — the glass is missing")
        assertEquals(
            emptyList(), banded.filter { it % 3 != 0 },
            "a band landed off the design's three-row period",
        )
        assertEquals(
            emptyList(), untouched.filter { it % 3 == 0 },
            "a row on the period carried no band",
        )
    }

    /**
     * **Every screen, both roles: the same attenuation, to within sRGB rounding.**
     *
     * The parity assertion, and the one that would matter in a dark house. Every band with a flat
     * surround is measured on all 112 renders, and the *typical* band on each has to be the same
     * band — so a screen that dimmed its glass, brightened it or dropped it shows up as itself
     * rather than as a number nobody can attribute.
     *
     * ### Why the typical band and not every band
     *
     * A flat surround says content is not changing across the two rows either side. It does not
     * say nothing is drawn ON the band row, and one thing routinely is: this design's only divider
     * is a one-unit horizontal hairline ([Hairline]), which has identical ground above and below
     * it by construction. A hairline landing on a band row is indistinguishable from darker glass
     * *at that row*, and there are a handful on most screens.
     *
     * They cannot hide a real fault, because a real fault is not local: glass is one brush over
     * the whole panel, so a screen carrying different glass carries it on **every** band, which
     * moves the median. A hairline does not.
     *
     * ### And why some screens cannot be measured at all
     *
     * **A band over black is invisible, and that is the glass working rather than failing.** It
     * takes light away and never adds any, so a nearly black panel — `Armed`, `Reveal` — has
     * nothing for it to take and nothing here to read. Those renders are counted and named rather
     * than passed over in silence, because "the instrument could not see this one" and "this one
     * was fine" are different sentences and only one of them is true.
     *
     * There is no weaker check to fall back on for them either, and it is worth writing down why
     * the obvious one is wrong: *a band row is never brighter than its neighbours* is false. The
     * glass multiplies, so a band drawn across the top of a lit box is still far brighter than the
     * unlit ground above it. That version of this test reported faults on nineteen screens that
     * had none.
     */
    @Test
    fun everyScreenAndBothRolesCarryTheSameGlass() {
        val wrong = mutableListOf<String>()
        val tooDark = mutableListOf<String>()
        val all = mutableListOf<Double>()
        var measurable = 0
        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                // A cause on every render: the out-screens read it, and the glass must not care.
                render(PanelState(screen = id, role = role, outBy = OutBy.Revoked)) { img ->
                    val ratios = (3 until img.height - 1 step 3)
                        .mapNotNull { flatRatioAt(img, it) }
                        .sorted()
                    all += ratios
                    if (ratios.size < 4) {
                        tooDark += "$id/$role"
                        return@render
                    }
                    measurable++
                    val median = ratios[ratios.size / 2]
                    if (kotlin.math.abs(median - expected) > slack) {
                        wrong += "$id/$role — the typical band is $median, expected $expected"
                    }
                }
            }
        }

        println("SCANLINES  ${all.size} bands over $measurable renders; too dark to read: $tooDark")
        assertTrue(measurable > 40, "only $measurable of 112 renders were lit enough to measure")
        assertTrue(all.size > 500, "only ${all.size} bands measured in total")
        val agreeing = all.count { kotlin.math.abs(it - expected) <= slack }
        assertTrue(
            agreeing > all.size * 0.9,
            "only $agreeing of ${all.size} measured bands are the glass — something else is banding",
        )
        assertEquals(emptyList(), wrong, "the glass is not the same on every screen")
    }

    /**
     * **It attenuates and never adds.** A black pixel stays black.
     *
     * The rule that makes this affordable at all: the art direction assumes a black pixel emits
     * nothing, so glass that lifted the ground off black would be light the core never authored,
     * on every screen, all evening — and on a phone being held as a lamp that is not a styling
     * mistake, it is emitted light nobody asked for.
     */
    @Test
    fun theGlassNeverAddsLight() = render(PanelState(screen = ScreenId.Home)) { img ->
        val darkest = (0 until img.height).minOf { y -> luminance(img.getRGB(img.width / 2, y)) }
        assertEquals(0, darkest, "the darkest pixel on a dark-field screen is no longer black")
    }

    /**
     * How much darker row [y] is than the rows either side — but only where those two are already
     * the same as each other and lit enough to measure.
     *
     * Null means "this row tells us nothing": content is changing across it, or there is no light
     * here for a band to take away. Both are ordinary and neither is a fault.
     */
    private fun flatRatioAt(img: BufferedImage, y: Int): Double? {
        val above = rowMean(img, y - 1)
        val below = rowMean(img, y + 1)
        if (above < 20.0 || kotlin.math.abs(above - below) > above * 0.01) return null
        return rowMean(img, y) / above
    }

    private fun rowMean(img: BufferedImage, y: Int): Double =
        (0 until img.width).sumOf { luminance(img.getRGB(it, y)) }.toDouble() / img.width

    private fun luminance(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
