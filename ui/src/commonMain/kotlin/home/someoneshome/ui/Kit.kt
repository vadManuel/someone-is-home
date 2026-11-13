package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * **The redesign's building blocks** (D-202), ported from `tools/design-mockup/kit.py` one block at
 * a time, so a header, a row, a button or a hold is drawn one way everywhere.
 *
 * **Every number in this file is the mockup's own pixel value**, on its 360-wide canvas: a 22px
 * caption is `22`, a 56px target is `56.dp`. That is only true inside [Mockup], which is how a
 * restyled screen gets the mockup's coordinate space while the screens not yet restyled keep the
 * 300-unit space they were drawn in. See [Mockup] for why the two live side by side for a while.
 */

/** The mockup's canvas width. Where [DESIGN_WIDTH] is going, and where [Mockup] already is. */
const val MOCKUP_WIDTH: Float = 360f

/**
 * **A restyled screen, drawn in the mockup's 360-unit space** (D-202).
 *
 * D-202 moves the canvas from 300 to 360 units. Flipping [DESIGN_WIDTH] in one commit would shrink
 * every screen not yet restyled by a sixth, on main, for the whole of the restyle: text that is
 * already too small, made smaller, on a build people playtest. So the restyle goes screen by screen
 * instead, and each restyled screen is wrapped in this, which rescales density by
 * `DESIGN_WIDTH / MOCKUP_WIDTH` so 360 of its units span the panel.
 *
 * **It is a bridge and it ends.** When the last screen is restyled, [DESIGN_WIDTH] becomes 360, the
 * ratio here becomes 1, and this goes. Everything a screen reads about the panel is rescaled with
 * it -- height, insets and scale -- so a restyled screen never mixes the two unit systems.
 */
@Composable
fun Mockup(content: @Composable () -> Unit) {
    val ratio = DESIGN_WIDTH / MOCKUP_WIDTH
    val base = LocalDensity.current
    val insets = LocalPanelInsets.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * ratio, fontScale = 1f),
        LocalPanelScale provides LocalPanelScale.current * ratio,
        LocalPanelHeight provides LocalPanelHeight.current / ratio,
        LocalPanelInsets provides PanelInsets(
            top = insets.top / ratio, bottom = insets.bottom / ratio, side = insets.side / ratio,
        ),
        content = content,
    )
}

/**
 * **One of the redesign's four palettes: what each role on a screen is drawn in** (D-202, D-077).
 *
 * `kit.py`'s `DARK`, `MEDIUM`, `CONCEAL` and `BONE`, field for field. [ink] is the brightest thing
 * allowed on the screen, so a concealment screen is a palette whose ink is `#C08A2E` rather than a
 * screen that remembers to dim each of its parts.
 *
 * [fill], [onFill] and [fillEdge] are the primary block: the one solid block a shift screen may
 * have. On the medium and concealment palettes there is no fill and the block is an outline.
 */
data class Palette(
    val ink: Color,
    val mid: Color,
    val dim: Color,
    val line: Color,
    val faint: Color,
    val fill: Color,
    val onFill: Color,
    val fillEdge: Color,
) {
    /** Whether this is the lights-on bone palette, which draws a few blocks differently. */
    val bone: Boolean get() = this == BONE

    companion object {
        /** The dark field: full ink, and the one solid amber block. */
        val DARK = Palette(
            ink = Ramp.Ink, mid = Ramp.Mid, dim = Ramp.Dim, line = Ramp.Line, faint = Ramp.Faint,
            fill = Ramp.Ink, onFill = Amber.Black, fillEdge = Ramp.Ink,
        )

        /** A medium screen: the ink drops a step and the primary block becomes an outline. */
        val MEDIUM = DARK.copy(ink = Ramp.Mid, fill = Color.Transparent, onFill = Ramp.Mid, fillEdge = Ramp.Mid)

        /** A concealment screen (D-077): nothing above `#C08A2E`, and no fill at all. */
        val CONCEAL = Palette(
            ink = Ramp.Dim, mid = Ramp.Dim, dim = Ramp.Dim, line = Ramp.Faint, faint = Ramp.Faint,
            fill = Color.Transparent, onFill = Ramp.Dim, fillEdge = Ramp.Faint,
        )

        /** The bone LCD, lights on, before arming and at the Residents' ending. Unchanged values. */
        val BONE = Palette(
            ink = Amber.BoneInk, mid = Amber.BoneInk, dim = Amber.BoneDim, line = Amber.BoneEdge,
            faint = Amber.BoneEdge, fill = Amber.SlateFill, onFill = Amber.SlateInk,
            fillEdge = Amber.Slate,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------------------------

/**
 * **VT323: everything a player reads** (D-202) -- headings, numbers, buttons and reading text.
 *
 * [size] is mockup pixels. Reading text is 22 and up; a caption may be 18 to 20, and a map's room
 * labels 17. Nothing smaller is set in this face.
 */
@Composable
fun Words(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 24,
    color: Color = Ramp.Ink,
    lineHeight: Double = 1.12,
    tracking: Double = 0.0,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = PanelType.readout,
            fontSize = size.sp,
            color = color,
            letterSpacing = tracking.em,
            lineHeight = (size * lineHeight).sp,
            textAlign = align ?: TextAlign.Unspecified,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * **Silkscreen, 12px, and nothing else** (D-202): the small label over a section and the status
 * strip. Chrome a player learns once and then does not read.
 */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = PanelType.label,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            color = color,
            letterSpacing = 0.08.em,
            textAlign = align ?: TextAlign.Unspecified,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Blocks
// ---------------------------------------------------------------------------------------------

/** `kit.py`'s `screen`: the padded column every restyled screen is laid out in. */
@Composable
fun KitScreen(
    padding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** The mockup's left chevron, on its own 24-unit grid. */
private val CHEVRON_LEFT = listOf("M15 4l-8 8 8 8")

/** The mockup's right chevron, trailing a navigation row. */
private val CHEVRON_RIGHT = listOf("M9 4l8 8-8 8")

/**
 * **`kit.py`'s `hdr`: the title, and a way back when there is one.**
 *
 * The chevron is a 56 square hung 16 units into the left margin, so the title sits on the margin
 * while the finger gets the whole target. It is the app's one back arrow, so it carries
 * [BACK_CHEVRON] and [BACK_NAME] exactly as [BackChevron] does, and steps back along the trail when
 * there is one and to [back] when there is not.
 *
 * **With [back] null there is no arrow, trail or no trail**, which is `kit.py`'s `hdr` exactly: the
 * screen decides whether it has a way back. A header that grew an arrow because a trail happened to
 * exist would put one on screens the house pushed you onto (`BackNavigationTest`).
 */
@Composable
fun KitHeader(
    title: String,
    palette: Palette,
    back: ScreenId? = null,
    meta: String? = null,
    titleSize: Int = 32,
) {
    val actions = LocalActions.current
    val trailed = actions.canGoBack()
    val arrow = back != null
    Row(
        Modifier.fillMaxWidth().then(if (arrow) Modifier.bleedStart(16.dp) else Modifier)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (arrow) 8.dp else 12.dp),
    ) {
        if (arrow) {
            Box(
                Modifier.testTag(BACK_CHEVRON)
                    .semantics(mergeDescendants = true) {
                        contentDescription = BACK_NAME
                        role = Role.Button
                    }
                    .size(56.dp)
                    .tap { if (trailed) actions.back() else back?.let(actions.nav) },
                contentAlignment = Alignment.Center,
            ) {
                PanelIcon(CHEVRON_LEFT, size = 22.dp, color = palette.ink, strokeWidth = 2.5f, viewBox = 24f)
            }
        }
        Words(title, Modifier.weight(1f), size = titleSize, color = palette.ink, lineHeight = 1.0)
        meta?.let { Words(it, size = 22, color = palette.dim, lineHeight = 1.0) }
    }
}

/** `kit.py`'s `label`: Silkscreen 12, in the palette's dim. */
@Composable
fun KitLabel(text: String, palette: Palette, modifier: Modifier = Modifier) =
    Tag(text, palette.dim, modifier)

/** `kit.py`'s `cap`: a caption, 22 by default and never under 18 (D-202). */
@Composable
fun KitCaption(
    text: String,
    palette: Palette,
    modifier: Modifier = Modifier,
    size: Int = 22,
    center: Boolean = false,
) {
    require(size >= 18) { "a caption under 18px is below D-202's floor" }
    Words(
        text, modifier, size = size, color = palette.dim, lineHeight = 1.15,
        align = if (center) TextAlign.Center else null,
    )
}

/** `kit.py`'s `body`: reading text, 24 by default, in ink unless told otherwise. */
@Composable
fun KitBody(
    text: String,
    palette: Palette,
    modifier: Modifier = Modifier,
    size: Int = 24,
    color: Color = palette.ink,
    align: TextAlign? = null,
) = Words(text, modifier, size = size, color = color, lineHeight = 1.12, align = align)

/**
 * `kit.py`'s `primary`: **the one solid block** (D-202). 64 tall, filled on the dark palette and an
 * outline everywhere else.
 */
@Composable
fun KitPrimary(
    text: String,
    palette: Palette,
    modifier: Modifier = Modifier,
    size: Int = 30,
    height: Dp = 64.dp,
    onClick: (() -> Unit)?,
) = KitButtonBox(
    text, modifier.height(height).border(1.dp, palette.fillEdge).background(palette.fill),
    palette.onFill, size, 0.06, onClick,
)

/**
 * `kit.py`'s `secondary`: an outline at the line value, 56 tall. [edge] and [edgeWidth] are the
 * overrides a screen passes the way the mockup passes `border:` in `extra`.
 */
@Composable
fun KitSecondary(
    text: String,
    palette: Palette,
    modifier: Modifier = Modifier,
    size: Int = 26,
    height: Dp = 56.dp,
    edge: Color = if (palette.bone) palette.ink else palette.line,
    edgeWidth: Dp = 1.dp,
    onClick: (() -> Unit)?,
) = KitButtonBox(
    text, modifier.height(height).border(edgeWidth, edge), palette.ink, size, 0.04, onClick,
)

/** `kit.py`'s `quiet`: no border, 56 tall, in the palette's mid. */
@Composable
fun KitQuiet(
    text: String,
    palette: Palette,
    modifier: Modifier = Modifier,
    size: Int = 24,
    onClick: (() -> Unit)?,
) = KitButtonBox(
    text, modifier.height(56.dp), if (palette.bone) palette.dim else palette.mid, size, 0.04, onClick,
)

@Composable
private fun KitButtonBox(
    text: String,
    modifier: Modifier,
    ink: Color,
    size: Int,
    tracking: Double,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.tap(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Words(text, size = size, color = ink, tracking = tracking, lineHeight = 1.0, align = TextAlign.Center)
    }
}

/**
 * **`kit.py`'s `hold`: the two-second hold, as the redesign draws it** (D-141).
 *
 * A 2-unit outline, 64 tall, with the bar along its foot filling while a finger is down -- the bar
 * *is* the hold. [lit] draws it as the solid block. The note beneath says what to do, and only
 * while a finger is down says to keep going.
 */
@Composable
fun KitHold(
    text: String,
    palette: Palette,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lit: Boolean = false,
    size: Int = 28,
    note: Boolean = true,
) {
    val hold = rememberHold(onConfirm, enabled)
    val edge = if (lit) palette.fillEdge else if (palette.bone) palette.ink else palette.line
    val ink = if (lit) palette.onFill else palette.ink
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).border(2.dp, edge)
                .background(if (lit) palette.fill else Color.Transparent).then(hold.surface),
            contentAlignment = Alignment.Center,
        ) {
            Words(text, size = size, color = ink, tracking = 0.06, lineHeight = 1.0, align = TextAlign.Center)
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(hold.fraction).height(5.dp)
                    .background(ink)
            )
        }
        if (note) {
            KitCaption(
                if (hold.holding) "KEEP HOLDING" else HOLD_NOTE,
                palette, Modifier.fillMaxWidth(), size = 20, center = true,
            )
        }
    }
}

/**
 * `kit.py`'s `rules`: **hairline lists, not boxed rows** (D-202). A rule across the top, and each
 * row draws its own rule beneath it.
 */
@Composable
fun KitRules(palette: Palette, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().edgeLine(PanelSide.Top, palette.faint, 1.dp), content = content)
}

/** `kit.py`'s `info`: a key and its value, on a hairline. */
@Composable
fun KitInfo(key: String, value: String, palette: Palette, valueColor: Color = palette.ink) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).edgeLine(PanelSide.Bottom, palette.faint, 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Words(key, Modifier.weight(1f), size = 22, color = palette.dim, lineHeight = 1.0)
        Words(value, size = 24, color = valueColor, lineHeight = 1.0)
    }
}

/** `kit.py`'s `navrow`: a 56-tall row on a hairline, with a chevron, that goes somewhere. */
@Composable
fun KitNavRow(
    text: String,
    palette: Palette,
    onClick: () -> Unit,
    size: Int = 26,
    trailing: (@Composable RowScope.() -> Unit)? = {
        PanelIcon(CHEVRON_RIGHT, size = 18.dp, color = palette.ink, strokeWidth = 2.5f, viewBox = 24f)
    },
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).edgeLine(PanelSide.Bottom, palette.faint, 1.dp)
            .tap(onClick).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Words(text, Modifier.weight(1f), size = size, color = palette.ink, lineHeight = 1.0)
        trailing?.invoke(this)
    }
}

/** A box that grows to fill a column, for pushing what follows to the foot. */
@Composable
fun ColumnScope.KitSpacer() = Box(Modifier.weight(1f).fillMaxWidth())

/** A centred column that takes the screen's spare height, which is where a headline sits. */
@Composable
fun ColumnScope.KitMiddle(gap: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * **Widen into the left margin by [by]**, the mockup's `margin-left: -16px` on a flex row: the row
 * starts [by] further left *and* is [by] wider, so its right edge stays on the margin. An `offset`
 * would shift it without widening, and the meta on the right would stop [by] short.
 */
private fun Modifier.bleedStart(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        )
    )
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-extra, 0) }
}
