package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText

/**
 * Establishes the design's coordinate space, so every dimension in this module is the design's
 * own number rather than one re-derived against a device.
 *
 * **The scale is applied by overriding [LocalDensity], not by scaling a `graphicsLayer`.** A
 * scaled layer rasterises its contents at layout size and resamples them up, which is precisely
 * the wrong thing to do to a bitmap-derived face: Silkscreen's whole value is that its glyphs sit
 * on whole pixels, and bilinear resampling turns crisp pixels into grey mush. Overriding density
 * makes the text *lay out and rasterise* at final size instead, so it stays sharp.
 *
 * **`fontScale` is pinned to 1.** This is a fake device OS, not a document: the player's system
 * text-size preference must not reflow a springboard whose parity depends on both roles seeing
 * identical geometry. A Resident whose labels wrapped and an Insider's that did not would be a
 * difference visible across a dark room.
 */
@Composable
fun DeviceCanvas(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val base = LocalDensity.current
        val scale = maxWidth.value / DESIGN_WIDTH
        val scaled = Density(base.density * scale, fontScale = 1f)
        CompositionLocalProvider(
            LocalDensity provides scaled,
            LocalPanelScale provides scale,
            LocalPanelHeight provides with(scaled) { constraints.maxHeight.toDp() },
        ) {
            Box(Modifier.fillMaxSize().background(Amber.Black)) { content() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

/**
 * A Silkscreen label. Every piece of chrome text in the interface is one of these.
 *
 * Sizes are the design's, in design units, and are deliberately tiny — 6 to 9. That is not an
 * accessibility oversight to be corrected later; the reference device is a 2001 organiser and the
 * whole screen is 300 units wide. Scaling it up is a design change, not a fix.
 */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: Double = 7.0,
    color: Color = Amber.Dim,
    tracking: Double = 0.0,
    lineHeight: Double? = null,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = PanelType.label,
            fontSize = size.sp,
            color = color,
            fontWeight = weight,
            letterSpacing = tracking.em,
            lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
            textAlign = align ?: TextAlign.Unspecified,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * A VT323 readout. Clocks, counts, countdowns — anything the eye reads as a *quantity*.
 *
 * The split between this and [Label] is the design's, and it is doing work: labels are chrome the
 * player learns once and stops reading, readouts are values that change. Rendering them in the
 * same face would flatten that distinction on a screen with no colour to spare.
 */
@Composable
fun Readout(
    text: String,
    modifier: Modifier = Modifier,
    size: Double = 11.0,
    color: Color = Amber.Bright,
    tracking: Double = 0.0,
    lineHeight: Double? = null,
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
            lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
            textAlign = align ?: TextAlign.Unspecified,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

// ---------------------------------------------------------------------------------------------
// Rules, fills and frames
// ---------------------------------------------------------------------------------------------

/** A one-unit horizontal hairline. The design's only divider. */
@Composable
fun Hairline(color: Color = Amber.Edge, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.u).background(color))
}

/** A one-unit vertical hairline. */
@Composable
fun VHairline(color: Color = Amber.Edge, modifier: Modifier = Modifier) {
    Box(modifier.width(1.u).fillMaxHeight().background(color))
}

/** A filled block of exact size — the design builds its glyphs and bars out of these. */
@Composable
fun Block(width: Dp, height: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).background(color))
}

/**
 * A bordered region. `border` before `background` so the fill sits inside the stroke, matching
 * CSS's default `content-box` behaviour rather than painting over the line.
 */
@Composable
fun Framed(
    modifier: Modifier = Modifier,
    border: Color = Amber.Edge,
    fill: Color = Color.Transparent,
    strokeWidth: Dp = 1.u,
    padding: Dp = 0.u,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .border(strokeWidth, border)
            .background(fill)
            .padding(padding),
        content = content,
    )
}

/**
 * A segmented bar — the design's only progress form.
 *
 * Discrete segments, never a continuous fill. *Comparing quantities is perception; adding numbers
 * is computation*, and a segmented bar is read at a glance across a room where a percentage is
 * not. It is also the shape that lets the Egress countdown replace System Integrity in place,
 * taking the Residents' only number away without the widget changing size.
 */
@Composable
fun SegmentBar(
    total: Int,
    lit: Int,
    litColor: Color,
    unlitColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 4.u,
    gap: Dp = 1.u,
) {
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (index < lit) litColor else unlitColor)
            )
        }
    }
}

/** A horizontal row with the design's default centre alignment and a unit gap. */
@Composable
fun PanelRow(
    modifier: Modifier = Modifier,
    gap: Dp = 0.u,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(gap),
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier, horizontalArrangement, verticalAlignment, content = content)
}

/** A vertical stack with a unit gap. */
@Composable
fun PanelColumn(
    modifier: Modifier = Modifier,
    gap: Dp = 0.u,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(gap),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement, horizontalAlignment, content = content)
}

/**
 * A hairline on one side only, painted rather than laid out.
 *
 * `Modifier.border` strokes all four sides and takes space on all four. The design uses one-sided
 * rules constantly — a row separator, a panel's top edge — and drawing them instead of bordering
 * keeps the content box exactly where the source put it.
 */
fun Modifier.edgeLine(side: PanelSide, color: Color, weight: Dp = 1.u): Modifier = drawBehind {
    val t = weight.toPx()
    when (side) {
        PanelSide.Top -> drawRect(color, Offset.Zero, Size(size.width, t))
        PanelSide.Bottom -> drawRect(color, Offset(0f, size.height - t), Size(size.width, t))
        PanelSide.Start -> drawRect(color, Offset.Zero, Size(t, size.height))
        PanelSide.End -> drawRect(color, Offset(size.width - t, 0f), Size(t, size.height))
    }
}

enum class PanelSide { Top, Bottom, Start, End }

/** A square of exact size, used for the design's hand-built glyphs. */
@Composable
fun Dot(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color))
}
