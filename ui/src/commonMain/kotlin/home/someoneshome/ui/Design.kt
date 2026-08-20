package home.someoneshome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import home.someoneshome.ui.generated.resources.Res
import home.someoneshome.ui.generated.resources.Silkscreen_Bold
import home.someoneshome.ui.generated.resources.Silkscreen_Regular
import home.someoneshome.ui.generated.resources.VT323_Regular
import org.jetbrains.compose.resources.Font

/**
 * The amber panel, as a palette.
 *
 * **Four luminance steps, one hue, no second hue.** Emphasis comes from inversion — amber
 * ground, black glyphs — the way a real amber panel does it. Adding a colour here is not a
 * styling choice: a second hue is a channel, and a channel can carry a role.
 *
 * **Dark-field, not light-field.** The rule is to minimise lit pixel *area*, not merely
 * brightness — on OLED a black pixel emits nothing, and the phone is a lantern in a dark house.
 * The bone values below are the deliberate exception: they belong to screens that run while the
 * house lights are still on, and nothing about them has to survive a dark room.
 *
 * Values are the design's own, carried across unchanged so a screen can be diffed against the
 * source by eye. Do not tidy them toward round numbers.
 */
object Amber {
    /** Step 4 — the only full-intensity ink. Headings, live values, the armed glyph. */
    val Bright = Color(0xFFFFC759)

    /** Step 3 — secondary ink, and the accent on anything the player is being shown. */
    val Mid = Color(0xFFE39A22)

    /** Step 2 — body text and inactive controls. */
    val Dim = Color(0xFF8A5C12)

    /** Step 1 — hairlines, borders, spent segments. Barely lit, on purpose. */
    val Faint = Color(0xFF46300B)

    /** Step 0 — an edge that exists structurally but emits almost nothing. */
    val Edge = Color(0xFF241806)

    /** True black. Not "very dark grey" — the pixel is off. */
    val Black = Color(0xFF000000)

    /** Darker than an edge: an unlit cell inside a lit grid. */
    val Well = Color(0xFF0B0703)

    /** The fill for the room you are standing in, on a live plan. */
    val Deep = Color(0xFF150E04)

    // ---- Bone: light-field, pre-game. The house lights are still on for all of these. ----

    /** The bone LCD ground of a device whose perimeter is not armed. */
    val Bone = Color(0xFFA29A86)
    val BoneInk = Color(0xFF14110B)
    val BoneDim = Color(0xFF332E24)
    val BoneEdge = Color(0xFF837B68)
    val BoneFaint = Color(0xFF57503F)
    val BoneDeep = Color(0xFF262117)
    val BonePutty = Color(0xFF8C8470)
    val BonePale = Color(0xFF918975)
    val BoneSoft = Color(0xFFABA28E)
    val BoneChip = Color(0xFFC6BEAC)
    val BoneChipOff = Color(0xFFA79E8C)

    /** The pale stripe in the stairs hatch. */
    val BoneHatch = Color(0xFFB3AA96)

    /**
     * The plan editor's slate green — the one non-amber hue in the whole interface, and it
     * appears only before the perimeter arms. It never reaches a screen played in the dark.
     */
    val Slate = Color(0xFF234A3A)
    val SlateInk = Color(0xFF123328)
    val SlateFill = Color(0xFF8FA294)
    val SlateFocus = Color(0xFF243029)
    val SlateFocusFill = Color(0xFF3E4C44)
    val SlateMute = Color(0xFF332E24)
    val SlateDead = Color(0xFF14110B)

    /** The wash the host's torch throws over the registration viewfinder. */
    val TorchWash = Color(0xFF8FA294).copy(alpha = 0.16f)
}

/**
 * The two faces, both bitmap-derived.
 *
 * Silkscreen carries every label; VT323 carries anything read as a *quantity* — a clock, a
 * count, a countdown. The split is doing work: labels are chrome the player learns once and
 * stops reading, readouts are values that change, and on a screen with no colour to spare that
 * distinction has to be carried by the face.
 *
 * Neither is decorative. Their glyphs land on whole pixels, so substituting a system monospace
 * does not soften the look — it removes the device.
 */
object PanelType {
    val label: FontFamily
        @Composable get() = FontFamily(
            Font(Res.font.Silkscreen_Regular, FontWeight.Normal),
            Font(Res.font.Silkscreen_Bold, FontWeight.Bold),
        )

    val readout: FontFamily
        @Composable get() = FontFamily(Font(Res.font.VT323_Regular))
}

/**
 * The design canvas is 300 units wide, and every dimension in this module is written in those
 * units — a 7px label is `7.0`, a 17px status bar is `17.u`.
 *
 * The whole tree is scaled once at the root by [DeviceCanvas]. Without that, every number would
 * have to be re-derived by hand against a real device size, and a hand-derived number cannot be
 * diffed against the design it came from.
 *
 * Width is pinned; height flows. The design's screens are column layouts with a growing middle,
 * so a taller panel gives the middle more room rather than letterboxing the whole thing.
 */
const val DESIGN_WIDTH: Float = 300f

/** The design canvas height. A reference, not a cap — screens may be taller. */
const val DESIGN_HEIGHT: Float = 400f

/**
 * Design units.
 *
 * Fractional values in the source (`7.5px`, `6.5px`, `5.5px`) stay fractional. Rounding them to
 * whole dp is what turns a deliberately cramped label into an ordinary one.
 */
val Int.u: Dp get() = this.dp
val Double.u: Dp get() = this.dp

/** The scale in force. Almost nothing should read this; it exists for the rare physical check. */
val LocalPanelScale: ProvidableCompositionLocal<Float> = staticCompositionLocalOf { 1f }

/**
 * The panel's height in design units.
 *
 * Screens that fill vertically need it, because the canvas pins width and lets height flow: 400
 * is the reference, not a guarantee, and a screen that hard-codes it will crop or float
 * depending on the handset.
 */
val LocalPanelHeight: ProvidableCompositionLocal<Dp> = staticCompositionLocalOf { DESIGN_HEIGHT.dp }

/**
 * Which of the two roles this device belongs to.
 *
 * **A rendering input, not an answer to a question anyone may ask.** `ui` cannot see `core`, so
 * this arrives already decided; nothing here derives it, and nothing here may render it as a
 * difference in brightness, page count or control position. The design's rule, kept verbatim
 * because it is the entire discipline: *brightness must never encode the role, and neither may
 * a working button.*
 */
enum class PanelRole { Resident, Insider }
