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
 * **The glass: the design's scan lines, on every screen, carrying nothing — at the strength the
 * ground under them can afford.**
 *
 * Transcribed from the device mockup's `.screen::after` — one row of 32% black in every three,
 * over the whole panel. Worth a test rather than a glance for two reasons that pull opposite ways.
 *
 * It has to be **there on every screen**, because it is the surface rather than any screen's
 * decoration, and a screen that quietly lost it would look like a different device.
 *
 * And it has to be **the same on every screen of a ground**, for the same reason from the other
 * side. Light in this game is state (rule 5): an attenuation that varied with the **role** or the
 * **moment** would be a difference emitted into a dark room, where the one thing a player may never
 * learn from a glance at somebody else's phone is anything at all.
 *
 * ### Why there are two numbers, and why that is not the thing the rule forbids
 *
 * The mockup is amber on black. Over black the band takes 32% of nothing and is invisible; what it
 * does there is lay texture over lit content, which is what it was chosen for. The port drew the
 * same brush over **every ground the app has**, and over a ground that already emits a multiply
 * takes the ink and the ground together, leaving them closer than it found them.
 * [theBandUsedToCostEveryLitGroundHalfItsContrast] is that sentence in numbers, on all four pairs
 * that carry words.
 *
 * So the strength follows the **ground**, which is a function of [PanelState.screen] alone — never
 * the role, never the round, never the moment. Two phones showing the same screen carry identical
 * glass, which is the whole of what the rule protects. Two phones showing different screens already
 * look different, and a lit ground announces itself long before its banding does.
 *
 * ### ⚠️ One rule for every lit ground, because the first pass had two and got one of them wrong
 *
 * The bone screens were fixed first and the lantern was **cleared in the same pass** — on the
 * grounds that black on `Amber.Bright` still measures 6.34:1 under the full band, comfortably past
 * the 4.5:1 small-text floor. That was the wrong instrument: an absolute floor asks whether a
 * palette is legible, and the question the glass raises is what it *costs* a palette somebody
 * already chose. Asked the way bone was asked, the lantern keeps **47%** — worse than bone's 53%,
 * the worst number in the app — and the owner had said so: *"this also applies to dark mode, but
 * only whenever there is text on the amber color background."*
 *
 * So there is one floor here and every lit ground is held to it, bone and amber alike. Two rules
 * are two chances to clear a ground with the kinder one.
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
     * Rendered 300 wide, the old canvas's width, so the glass's 3.6-unit period is three rows with
     * nothing to round and every render here is the one this file was calibrated on.
     */
    private val width = 300
    private val height = 200

    /**
     * **The two grounds' bands, read off the shipped constants rather than restated here.**
     *
     * Black at alpha `a` over a colour leaves `(1 - a)` of every sRGB channel, and [luminance] is a
     * linear combination of those channels — so the ratio a flat surround comes out at *is*
     * `1 - a`, exactly, with nothing to model. Asking [scanLineInk] for it keeps this test a
     * measurement of the app instead of a second copy of its numbers that agrees on the day it is
     * written; [theDarkGroundsBandIsStillTheMockupsOwnNumber] is where the number itself is pinned.
     */
    private fun expected(lit: Boolean): Double = 1.0 - scanLineInk(lit).alpha.toDouble()

    /** sRGB rounding at these levels moves the ratio by about a percent either way. */
    private val slack = 0.02

    /** Whether a screen puts text on a ground that emits. A function of the screen and nothing else. */
    private fun lit(id: ScreenId): Boolean = PanelVals(PanelState(screen = id)).litGround

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
     * **Every screen, both roles: the band its ground calls for, to within sRGB rounding.**
     *
     * The parity assertion, and the one that would matter in a dark house. Every band with a flat
     * surround is measured on all 112 renders, and the *typical* band on each has to be the band
     * its ground carries — so a screen that dimmed its glass, brightened it or dropped it shows up
     * as itself rather than as a number nobody can attribute.
     *
     * **The two grounds are asserted to disagree**, at the bottom, and that is not decoration: a
     * change that quietly collapsed them back to one number would otherwise pass here on whichever
     * number survived, and take every lit ground's legibility with it.
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
    fun everyScreenAndBothRolesCarryTheGlassOfItsGround() {
        val wrong = mutableListOf<String>()
        val tooDark = mutableListOf<String>()
        val byGround = mapOf(false to mutableListOf<Double>(), true to mutableListOf())
        var measurable = 0
        for (id in ScreenId.entries) {
            val lit = lit(id)
            for (role in PanelRole.entries) {
                // A cause on every render: the out-screens read it, and the glass must not care.
                render(PanelState(screen = id, role = role, outBy = OutBy.Revoked)) { img ->
                    val ratios = (3 until img.height - 1 step 3)
                        .mapNotNull { flatRatioAt(img, it) }
                        .sorted()
                    byGround.getValue(lit) += ratios
                    if (ratios.size < 4) {
                        tooDark += "$id/$role"
                        return@render
                    }
                    measurable++
                    val median = ratios[ratios.size / 2]
                    if (kotlin.math.abs(median - expected(lit)) > slack) {
                        wrong += "$id/$role — the typical band is $median, expected " +
                            "${expected(lit)} on ${if (lit) "a lit" else "a black"} ground"
                    }
                }
            }
        }

        val all = byGround.getValue(false) + byGround.getValue(true)
        println(
            "SCANLINES  ${all.size} bands over $measurable renders " +
                "(${byGround.getValue(false).size} black-ground, " +
                "${byGround.getValue(true).size} lit-ground); too dark to read: $tooDark"
        )
        assertTrue(measurable > 40, "only $measurable of 112 renders were lit enough to measure")
        assertTrue(all.size > 500, "only ${all.size} bands measured in total")
        for ((lit, ratios) in byGround) {
            val agreeing = ratios.count { kotlin.math.abs(it - expected(lit)) <= slack }
            assertTrue(
                agreeing > ratios.size * 0.9,
                "only $agreeing of ${ratios.size} bands on ${if (lit) "a lit" else "a black"} " +
                    "ground are its glass — something else is banding",
            )
        }
        assertEquals(emptyList(), wrong, "the glass is not the band its ground carries")

        assertTrue(
            expected(true) - expected(false) > 0.1,
            "the two grounds' bands have collapsed to one number — " +
                "lit is ${expected(true)}, black is ${expected(false)}",
        )
    }

    /**
     * **The black ground's band is the mockup's own number, unrounded and unadjusted.**
     *
     * The structural half of the split, and the half with no room to negotiate. `rgba(0,0,0,.32)`
     * is what the design draws over amber-on-black, that is the ground every screen the game is
     * *played* on has under it, and the legibility fix on the lit grounds is not a licence to touch
     * it. Asserted on the constant rather than on a render because a render can only say what a
     * band *came out* at; this says what was asked for.
     */
    @Test
    fun theDarkGroundsBandIsStillTheMockupsOwnNumber() {
        // Written the way the source writes it, rather than as a float compared to `.alpha`:
        // `Color` keeps its channels as half-floats, so 0.32f comes back out as 0.32156864 and an
        // equality on the number alone fails on the storage rather than on the design.
        assertEquals(
            Color.Black.copy(alpha = 0.32f), scanLineInk(lit = false),
            "the black ground's band is the design's transcription — black at .32 — and may not drift",
        )
        assertEquals(
            Color.Black.copy(alpha = scanLineInk(lit = true).alpha), scanLineInk(lit = true),
            "a lit ground's band is the same black at a different strength, not another colour",
        )
        assertTrue(
            scanLineInk(lit = true).alpha < scanLineInk(lit = false).alpha,
            "a lit ground's band is no lighter than a black one's — the split has inverted",
        )
    }

    /**
     * **The app's answer about each screen's ground, checked against the screen's own pixels.**
     *
     * The set matters as much as the number, because the fault this file exists for was a *missing
     * member* rather than a wrong value: the lantern and the scan fill with amber, `Tally` and
     * `WinInsiders` put their one load-bearing sentence in an amber block, and all four were
     * carrying the black ground's band while showing black text on light.
     *
     * ### ⚠️ It has to ask the render, not the constant, and the first version of this did not
     *
     * This began as a list — `PRE` plus the three inverted screens plus [PanelVals.AMBER_BLOCKS] —
     * compared against [PanelVals.litGround]. Injecting an empty `AMBER_BLOCKS` **passed**, because
     * both sides of the comparison read the same constant. A guard assembled from the thing it is
     * guarding agrees with it by construction and is worth nothing.
     *
     * So the second opinion comes from the pixels. [litTextBand] looks for a band of ground that
     * emits with words standing on it — which is the owner's own phrasing of the fault: *"whenever
     * there is text on the amber color background."* It is the same sentence for bone, for the
     * lantern's amber and for an amber block on an otherwise black panel, and no screen can fake
     * it: a screen of amber-on-black has bright pixels and dark pixels in the same rows too, but
     * the bright ones are the *glyphs* and they are nowhere near a majority.
     */
    @Test
    fun theAppAgreesWithThePixelsAboutWhichGroundsEmit() {
        val disagreed = mutableListOf<String>()
        for (id in ScreenId.entries) {
            // Taller than the rest of this file's renders on purpose: `Tally` and `WinInsiders`
            // carry their amber block at the very bottom of the panel, and a 200-unit window is
            // all header.
            var band = 0
            runDesktopComposeUiTest(width = width, height = 400) {
                setContent {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Box(Modifier.size(width.dp, 400.dp)) {
                            DeviceCanvas(insets = PanelInsets()) {
                                Screen(PanelState(screen = id, outBy = OutBy.Restrained))
                            }
                        }
                    }
                }
                band = litTextBand(onRoot().captureToImage().toAwtImage())
            }
            if ((band > 0) != lit(id)) {
                disagreed += "$id — the app says ${if (lit(id)) "lit" else "black"}, " +
                    "the render's tallest band of lit ground carrying words is $band rows"
            }
        }
        assertEquals(
            emptyList(), disagreed,
            "a screen's ground is not what the app thinks it is — a lit ground carrying the " +
                "black ground's band is the owner's report, back again",
        )
    }

    /**
     * **Is there a band of lit ground on this screen with words standing on it?**
     *
     * Two questions, and both are needed. A **band** is a run of at least [MIN_BAND] consecutive
     * rows that are each more than half brighter than [GROUND_LIT] — a ground that emits, tall
     * enough to put a line of type on. **Words** are what makes the ink count in those rows *vary*
     * down the band: a row through the waist of a line of capitals has a different amount of ink in
     * it than the row above the caps, by a lot.
     *
     * ⚠️ **The variation is the whole discriminator, and it was added because the first two
     * versions failed on `Home`.** The springboard's System Integrity meter is a wide lit bar with
     * unlit segments punched through it: every row of it is majority-bright, every row of it has
     * dark pixels, and the dark pixels are stroke-width. It is indistinguishable from a line of
     * text in any *single* row. What separates them is down the bar rather than across it — **a
     * bar's rows are all identical and a word's are not** — so the test is the spread of ink
     * between the rows of a band, and the meter's spread is zero.
     *
     * Returns the tallest such band, or 0.
     */
    private fun litTextBand(img: BufferedImage): Int {
        val brightRow = BooleanArray(img.height)
        val inkPerRow = IntArray(img.height)
        for (y in 0 until img.height) {
            var bright = 0
            var ink = 0
            for (x in 0 until img.width) {
                val l = luminance(img.getRGB(x, y))
                if (l > GROUND_LIT) bright++ else if (l < INK_DARK) ink++
            }
            brightRow[y] = bright > img.width / 2
            inkPerRow[y] = ink
        }

        var best = 0
        var y = 0
        while (y < img.height) {
            if (!brightRow[y]) { y++; continue }
            var end = y
            while (end + 1 < img.height && brightRow[end + 1]) end++
            val height = end - y + 1
            if (height >= MIN_BAND) {
                val ink = (y..end).map { inkPerRow[it] }
                if (ink.max() - ink.min() > img.width / 20) best = maxOf(best, height)
            }
            y = end + 1
        }
        return best
    }

    /**
     * **What the full band cost every lit ground, measured — and what it costs now.**
     *
     * Contrast is **not** scale-invariant: WCAG's ratio carries a `+0.05` flare term, so
     * multiplying a foreground and its ground by the same factor moves them closer together. The
     * band multiplies. On a black ground that costs nothing, because the ground is already zero and
     * stays zero. On every ground that emits it cost about half:
     *
     * | pair | clear | band at .32 | band at .08 |
     * |---|---|---|---|
     * | `Black` on `Amber.Bright` — the lantern, the scan | 13.57 | **6.34** | 11.27 |
     * | `Black` on `Amber.Mid` — the inverted blocks | 8.91 | **4.40** | 7.51 |
     * | `BoneInk` on `Bone` — body and headings | 6.73 | **3.57** | 5.76 |
     * | `BoneDim` on `Bone` — secondary ink | 4.82 | **2.98** | 4.31 |
     * | `BoneFaint` on `Bone` — the two-line footers | 2.86 | **2.18** | 2.70 |
     *
     * The `.32` column is exact arithmetic on the palette; the `.08` column is what the test reads
     * off a render, which is why it sits a little under it — half-float storage of the alpha.
     *
     * **The floor asserted here is a fraction, not an absolute**, and there are two reasons rather
     * than one. `BoneDim` sits at 4.82 on a clear row, so it is already marginal before any glass
     * touches it — that is the palette's business, and N5b's, which moved `Amber.Caution` off bone
     * for the same kind of reason. And an absolute floor is what *cleared the lantern wrongly*: at
     * 6.34:1 it passes 4.5:1 outright while having lost more of its contrast than anything else in
     * the app. What this file is entitled to hold is what the **glass** may cost — so: no lit pair
     * may lose more than [MAX_BAND_COST] of the contrast it has on a clear row, and the same
     * sentence is applied to amber and to bone.
     *
     * The attenuation is read off a **real render** rather than assumed from the constant, so a
     * change that left the constant alone and put a second dark layer over a lit ground — a
     * vignette, a scrim, a second glass — fails here too.
     */
    @Test
    fun theBandUsedToCostEveryLitGroundHalfItsContrast() {
        // Measured, not assumed: the flattest lit surface in the app, banded, off a render.
        var measured = emptyList<Double>()
        render(PanelState(screen = ScreenId.Boot)) { img ->
            measured = (3 until img.height - 1 step 3).mapNotNull { flatRatioAt(img, it) }.sorted()
        }
        assertTrue(measured.size > 20, "only ${measured.size} readable bands on a lit ground")
        val attenuation = measured[measured.size / 2]
        assertTrue(
            kotlin.math.abs(attenuation - expected(true)) < slack,
            "a lit ground attenuates by $attenuation, not the ${expected(true)} its band asks " +
                "for — something else is darkening it",
        )

        // The lantern's own render, because it is a different ground and the instrument must not
        // be allowed to infer one from the other -- that inference is what went wrong last time.
        var lantern = emptyList<Double>()
        render(PanelState(screen = ScreenId.Lock)) { img ->
            lantern = (3 until img.height - 1 step 3).mapNotNull { flatRatioAt(img, it) }.sorted()
        }
        assertTrue(lantern.size > 20, "only ${lantern.size} readable bands on the lantern")
        assertTrue(
            kotlin.math.abs(lantern[lantern.size / 2] - expected(true)) < slack,
            "the lantern attenuates by ${lantern[lantern.size / 2]}, not the ${expected(true)} a " +
                "lit ground asks for — the amber field is still on the mockup's band",
        )

        val pairs = listOf(
            "Black on Amber.Bright" to (Amber.Black to Amber.Bright),
            "Black on Amber.Mid" to (Amber.Black to Amber.Mid),
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
            println(
                "SCANLINES  %-22s clear %5.2f, band %5.2f, keeps %5.1f%%"
                    .format(name, clear, band, kept * 100)
            )
            if (kept < MAX_BAND_COST) {
                costly += "$name keeps only ${(kept * 100).toInt()}% of its contrast under the band"
            }
        }
        assertEquals(emptyList(), costly, "a lit ground's glass is eating its text")

        val primary = contrast(
            Amber.BoneInk.attenuatedBy(attenuation), Amber.Bone.attenuatedBy(attenuation),
        )
        assertTrue(
            primary >= SMALL_TEXT_FLOOR,
            "the bone ground's own ink reads at $primary under the band, below the " +
                "$SMALL_TEXT_FLOOR small-text floor — this is the owner's report, back again",
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
     *
     * **Same means within half a percent.** It was one percent, and the meeting's restyle showed
     * what that let through: rows inside a 64-unit pixel-font headline whose neighbours happened to
     * agree to 0.7%, read as bands three points off the glass. Measured over every render, every
     * band off its ground's glass on a black ground had a surround between 0.5% and 1%, and all
     * but nine of the ones on it had one under 0.25%.
     */
    private fun flatRatioAt(img: BufferedImage, y: Int): Double? {
        val above = rowMean(img, y - 1)
        val below = rowMean(img, y + 1)
        if (above < 20.0 || kotlin.math.abs(above - below) > above * 0.005) return null
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
         * Set from the measurement rather than aspiration: at the shipped alpha the worst pair —
         * black on `Amber.Bright`, the lantern — keeps 83%, so this is the floor with room
         * under it and not a restatement of the number it was measured at. The full band left that
         * same pair at **47%**, the worst in the app.
         */
        const val MAX_BAND_COST = 0.80

        /** WCAG AA for small text, which is every word on these screens. */
        const val SMALL_TEXT_FLOOR = 4.5

        /**
         * A pixel this bright is ground rather than ink, and this dark is ink rather than ground.
         *
         * The dimmest lit ground in the app is the lantern under a notification that dims the
         * house: the lamp at [NOTIFIED_DIM], about 64, and 59 under the lit band. It carries black
         * words, which is exactly the case the lighter band exists for, so it has to count as lit;
         * at 100 it did not, and the screen only passed while its arriving card was a bright amber
         * block (the mockup made that card black, D-202). The brightest ink that draws a *word* is
         * `BoneDim` at 46, so the two still do not meet. `BoneEdge`, at 128 on bone, draws
         * hairlines rather than words.
         */
        const val GROUND_LIT = 50
        const val INK_DARK = 48

        /**
         * How tall a run of lit rows has to be before it can be carrying a line of type.
         *
         * The app's smallest label is 5.5 design units and this renders one unit to one pixel, so
         * eight rows is a line of the smallest text in the game with a little room around it.
         */
        const val MIN_BAND = 8


    }
}
