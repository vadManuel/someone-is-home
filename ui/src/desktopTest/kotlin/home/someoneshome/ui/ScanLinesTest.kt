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
 * **The glass: the design's scan lines, on every screen, carrying nothing — at the strength each
 * field can afford.**
 *
 * Transcribed from the device mockup's `.screen::after` — one row of 32% black in every three,
 * over the whole panel. Worth a test rather than a glance for two reasons that pull opposite ways.
 *
 * It has to be **there on every screen**, because it is the surface rather than any screen's
 * decoration, and a screen that quietly lost it would look like a different device.
 *
 * And it has to be **the same on every screen of a field**, for the same reason from the other
 * side. Light in this game is state (rule 5): an attenuation that varied with the **role** or the
 * **moment** would be a difference emitted into a dark room, where the one thing a player may never
 * learn from a glance at somebody else's phone is anything at all.
 *
 * ### Why there are now two numbers, and why that is not the thing the rule forbids
 *
 * The mockup is amber on black. Over black the band takes 32% of nothing and is invisible; what it
 * does there is lay texture over lit content, which is what it was chosen for. The port drew the
 * same brush over the **bone** field as well — the pre-arming setup surfaces and the Residents'
 * ending, dark ink on a mid-tone ground — where there is no unlit ground to spare and the band
 * attenuates ink and ground together. The owner reported it as *"the scan lines make the text
 * barely legible on light mode"*, and [theBandUsedToCostTheBoneFieldHalfItsContrast] is that
 * sentence in numbers.
 *
 * So the strength follows the **field**, which is a function of [PanelState.screen] alone — never
 * the role, never the round, never the moment. Two phones showing the same screen carry identical
 * glass, which is the whole of what the rule protects. Two phones showing different screens already
 * look different, and the bone field announces itself long before its banding does.
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

    /**
     * **The two fields' bands, read off the shipped constants rather than restated here.**
     *
     * Black at alpha `a` over a colour leaves `(1 - a)` of every sRGB channel, and [luminance] is a
     * linear combination of those channels — so the ratio a flat surround comes out at *is*
     * `1 - a`, exactly, with nothing to model. Asking [scanLineInk] for it keeps this test a
     * measurement of the app instead of a second copy of its numbers that agrees on the day it is
     * written; [theDarkFieldsBandIsStillTheMockupsOwnNumber] is where the number itself is pinned.
     */
    private fun expected(light: Boolean): Double = 1.0 - scanLineInk(light).alpha.toDouble()

    /** sRGB rounding at these levels moves the ratio by about a percent either way. */
    private val slack = 0.02

    /** Whether a screen draws on the bone light field. A function of the screen and nothing else. */
    private fun light(id: ScreenId): Boolean = PanelVals(PanelState(screen = id)).isPre

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
     * **One row in three, with the first band on row three.**
     *
     * The period AND the phase, off the flattest surface in the app — the self-test screen, which
     * is a bone field with almost nothing on it. Every row of it that can carry a band does.
     *
     * The period and the phase are the part of the transcription that both fields share, so this
     * says nothing about strength: a band is any row measurably darker than a flat surround, at
     * either field's alpha.
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
     * **Every screen, both roles: the band its field calls for, to within sRGB rounding.**
     *
     * The parity assertion, and the one that would matter in a dark house. Every band with a flat
     * surround is measured on all 112 renders, and the *typical* band on each has to be the band
     * its field carries — so a screen that dimmed its glass, brightened it or dropped it shows up
     * as itself rather than as a number nobody can attribute.
     *
     * **The two fields are asserted to disagree**, at the bottom, and that is not decoration: a
     * change that quietly collapsed them back to one number would otherwise pass here on whichever
     * number survived, and take the bone field's legibility with it.
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
    fun everyScreenAndBothRolesCarryTheGlassOfItsField() {
        val wrong = mutableListOf<String>()
        val tooDark = mutableListOf<String>()
        val byField = mapOf(false to mutableListOf<Double>(), true to mutableListOf())
        var measurable = 0
        for (id in ScreenId.entries) {
            val light = light(id)
            for (role in PanelRole.entries) {
                // A cause on every render: the out-screens read it, and the glass must not care.
                render(PanelState(screen = id, role = role, outBy = OutBy.Revoked)) { img ->
                    val ratios = (3 until img.height - 1 step 3)
                        .mapNotNull { flatRatioAt(img, it) }
                        .sorted()
                    byField.getValue(light) += ratios
                    if (ratios.size < 4) {
                        tooDark += "$id/$role"
                        return@render
                    }
                    measurable++
                    val median = ratios[ratios.size / 2]
                    if (kotlin.math.abs(median - expected(light)) > slack) {
                        val field = if (light) "bone" else "dark"
                        wrong += "$id/$role — the typical band is $median, " +
                            "expected ${expected(light)} on the $field field"
                    }
                }
            }
        }

        val all = byField.getValue(false) + byField.getValue(true)
        println(
            "SCANLINES  ${all.size} bands over $measurable renders " +
                "(${byField.getValue(false).size} dark, ${byField.getValue(true).size} bone); " +
                "too dark to read: $tooDark"
        )
        assertTrue(measurable > 40, "only $measurable of 112 renders were lit enough to measure")
        assertTrue(all.size > 500, "only ${all.size} bands measured in total")
        for ((light, ratios) in byField) {
            val agreeing = ratios.count { kotlin.math.abs(it - expected(light)) <= slack }
            assertTrue(
                agreeing > ratios.size * 0.9,
                "only $agreeing of ${ratios.size} bands on the " +
                    "${if (light) "bone" else "dark"} field are its glass — something else is banding",
            )
        }
        assertEquals(emptyList(), wrong, "the glass is not the band its field carries")

        assertTrue(
            expected(true) - expected(false) > 0.1,
            "the two fields' bands have collapsed to one number — " +
                "bone is ${expected(true)}, dark is ${expected(false)}",
        )
    }

    /**
     * **The dark field's band is the mockup's own number, unrounded and unadjusted.**
     *
     * The structural half of the split, and the half with no room to negotiate. `rgba(0,0,0,.32)`
     * is what the design draws over amber-on-black, that is the field every in-round screen is
     * played on, and the legibility fix on the other field is not a licence to touch it. Asserted
     * on the constant rather than on a render because a render can only say what a band *came out*
     * at; this says what was asked for.
     */
    @Test
    fun theDarkFieldsBandIsStillTheMockupsOwnNumber() {
        // Written the way the source writes it, rather than as a float compared to `.alpha`:
        // `Color` keeps its channels as half-floats, so 0.32f comes back out as 0.32156864 and an
        // equality on the number alone fails on the storage rather than on the design.
        assertEquals(
            Color.Black.copy(alpha = 0.32f), scanLineInk(light = false),
            "the dark field's band is the design's transcription — black at .32 — and may not drift",
        )
        assertEquals(
            Color.Black.copy(alpha = scanLineInk(light = true).alpha), scanLineInk(light = true),
            "the bone field's band is the same black at a different strength, not another colour",
        )
        assertTrue(
            scanLineInk(light = true).alpha < scanLineInk(light = false).alpha,
            "the bone field's band is no lighter than the dark field's — the split has inverted",
        )
    }

    /**
     * **What the full band cost the bone field, measured — and what it costs now.**
     *
     * The owner's report was *"the scan lines make the text barely legible on light mode"*, and
     * this is the honest version of it. Contrast is **not** scale-invariant: WCAG's ratio carries a
     * `+0.05` flare term, so multiplying a foreground and its ground by the same factor moves them
     * closer together. The band multiplies. On the dark field that costs nothing, because the
     * ground is already zero and stays zero. On bone it costs nearly half:
     *
     * | pair | clear | band at .32 | band at .10 |
     * |---|---|---|---|
     * | `BoneInk` on `Bone` — body and headings | 6.73 | **3.57** | 5.56 |
     * | `BoneDim` on `Bone` — secondary ink | 4.82 | **2.98** | 4.20 |
     * | `BoneFaint` on `Bone` — the two-line footers | 2.86 | **2.18** | 2.65 |
     *
     * 4.5:1 is the small-text floor and every word on those screens is small text, so at .32 one
     * row in three of every glyph was under it — which is what "barely legible" looks like from
     * the inside.
     *
     * **The floor asserted here is a fraction, not an absolute**, and the reason is `BoneDim`: it
     * sits at 4.82 on a clear row, so it is already marginal before any glass touches it. That is
     * the palette's business (and N5b's, which moved `Amber.Caution` off bone for the same kind of
     * reason). What *this* file is entitled to hold is what the **glass** may cost — so: no
     * light-field pair may lose more than [MAX_BAND_COST] of the contrast it has on a clear row,
     * and the primary pair must still clear 4.5:1 outright.
     *
     * The attenuation is read off a **real render** rather than assumed from the constant, so a
     * change that left the constant alone and put a second dark layer over the bone field — a
     * vignette, a scrim, a second glass — fails here too.
     */
    @Test
    fun theBandUsedToCostTheBoneFieldHalfItsContrast() {
        // Measured, not assumed: the flattest bone surface in the app, banded, off a render.
        var measured = emptyList<Double>()
        render(PanelState(screen = ScreenId.Boot)) { img ->
            measured = (3 until img.height - 1 step 3).mapNotNull { flatRatioAt(img, it) }.sorted()
        }
        assertTrue(measured.size > 20, "only ${measured.size} readable bands on the bone field")
        val attenuation = measured[measured.size / 2]
        assertTrue(
            kotlin.math.abs(attenuation - expected(true)) < slack,
            "the bone field attenuates by $attenuation, not the ${expected(true)} its band asks " +
                "for — something else is darkening it",
        )

        val pairs = listOf(
            "BoneInk on Bone" to (Amber.BoneInk to Amber.Bone),
            "BoneDim on Bone" to (Amber.BoneDim to Amber.Bone),
            "BoneFaint on Bone" to (Amber.BoneFaint to Amber.Bone),
        )
        val costly = mutableListOf<String>()
        for ((name, pair) in pairs) {
            val (ink, ground) = pair
            val clear = contrast(ink, ground)
            val band = contrast(ink.attenuatedBy(attenuation), ground.attenuatedBy(attenuation))
            val kept = band / clear
            println("SCANLINES  $name — clear %.2f, band %.2f, keeps %.1f%%".format(clear, band, kept * 100))
            if (kept < MAX_BAND_COST) {
                costly += "$name keeps only ${(kept * 100).toInt()}% of its contrast under the band"
            }
        }
        assertEquals(emptyList(), costly, "the bone field's glass is eating its text")

        val primary = contrast(
            Amber.BoneInk.attenuatedBy(attenuation), Amber.Bone.attenuatedBy(attenuation),
        )
        assertTrue(
            primary >= SMALL_TEXT_FLOOR,
            "the bone field's own ink reads at $primary under the band, below the " +
                "$SMALL_TEXT_FLOOR small-text floor — this is the owner's report, back again",
        )
    }

    /**
     * **The lantern was the other suspect and it was measured and cleared.**
     *
     * `Lock`, `LockNotify` and `Scan` fill the panel with amber and draw black on it, which is the
     * app's other light ground — and the first note written about this fault blamed exactly those
     * three. It was wrong, and the way to keep it wrong is to write the measurement down: black on
     * `Amber.Bright` goes 13.57:1 clear to 6.34:1 under the full band, comfortable at both ends,
     * because the ground starts far enough up that halving it lands nowhere near the ink.
     *
     * So the lantern keeps the design's glass, and this test exists to say that on purpose rather
     * than by omission. It is also the screen a player holds up in a dark room, where the mockup's
     * texture is doing the work it was drawn for.
     */
    @Test
    fun theLanternKeepsTheDesignsGlassBecauseItCanAffordIt() {
        for (id in listOf(ScreenId.Lock, ScreenId.LockNotify, ScreenId.Scan)) {
            assertTrue(!light(id), "$id has been moved onto the bone field")
        }
        val attenuation = expected(light = false)
        val band = contrast(
            Amber.Black.attenuatedBy(attenuation), Amber.Bright.attenuatedBy(attenuation),
        )
        assertTrue(
            band >= SMALL_TEXT_FLOOR,
            "the lantern's inverted text reads at $band under the design's band — it has stopped " +
                "being able to afford it, and the field split needs to grow a third case",
        )
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

    /** This colour with [factor] of it left — what a band row does to whatever is under it. */
    private fun Color.attenuatedBy(factor: Double): Color =
        Color(red * factor.toFloat(), green * factor.toFloat(), blue * factor.toFloat())

    /**
     * WCAG's contrast ratio. The `+0.05` is the flare term, and it is the whole reason a band that
     * scales both sides by the same factor is not free.
     */
    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.04045) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private companion object {
        /**
         * The share of its clear-row contrast a light-field pair must still have under a band.
         *
         * Set from the measurement rather than aspiration: at the shipped alpha the worst pair
         * keeps 82.7%, so this is the floor with a little room under it and not a restatement of
         * the number it was measured at. The full band left the primary pair at 53%.
         */
        const val MAX_BAND_COST = 0.80

        /** WCAG AA for small text, which is every word on these screens. */
        const val SMALL_TEXT_FLOOR = 4.5
    }
}
