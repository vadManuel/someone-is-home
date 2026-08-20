package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Establishes the design's coordinate space, so every dimension downstream is the design's own
 * number rather than one re-derived against a device.
 *
 * **The scale is applied by overriding [LocalDensity], not by scaling a `graphicsLayer`.** A
 * scaled layer rasterises its contents at layout size and resamples them up, which is exactly
 * the wrong thing to do to a bitmap-derived face — Silkscreen's whole value is glyphs sitting on
 * whole pixels, and bilinear resampling turns crisp pixels into grey mush. Overriding density
 * makes text lay out *and rasterise* at final size instead.
 *
 * **`fontScale` is pinned to 1.** This is a fake device OS, not a document. Parity depends on
 * both roles seeing identical geometry, and a system text-size preference that reflowed one
 * player's springboard and not another's would be a difference visible across a dark room.
 */
@Composable
fun DeviceCanvas(
    modifier: Modifier = Modifier,
    /**
     * What the hardware takes out of the panel. `null` reads the real device.
     *
     * Passed explicitly so a test can render against a phone this machine is not — see
     * [PanelInsets]. Nothing in production should supply it.
     */
    insets: PanelInsets? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val base = LocalDensity.current
        val scale = maxWidth.value / DESIGN_WIDTH
        val scaled = Density(base.density * scale, fontScale = 1f)
        CompositionLocalProvider(
            LocalDensity provides scaled,
            LocalPanelScale provides scale,
            LocalPanelHeight provides with(scaled) { constraints.maxHeight.toDp() },
        ) {
            // Read INSIDE the scaled density, so the platform's points come out as design units
            // — the coordinate space every other number in the panel is in. Converting anywhere
            // else silently mixes two unit systems.
            CompositionLocalProvider(LocalPanelInsets provides (insets ?: platformInsets())) {
                Box(Modifier.fillMaxSize().background(Amber.Black)) { content() }
            }
        }
    }
}

/** The real device's insets, in design units. Zero on the desktop target. */
@Composable
private fun platformInsets(): PanelInsets {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val content = WindowInsets.safeContent.asPaddingValues()
    val layout = LocalLayoutDirection.current
    return PanelInsets(
        top = safe.calculateTopPadding(),
        bottom = safe.calculateBottomPadding(),
        side = maxOf(content.calculateLeftPadding(layout), content.calculateRightPadding(layout)),
    )
}

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

/**
 * A Silkscreen label — every piece of chrome text in the interface.
 *
 * Sizes are the design's, and they are deliberately tiny: 5.5 to 11 on a canvas 300 wide. That
 * is not an accessibility oversight awaiting correction; the reference device is a 2001
 * organiser. Scaling it up is a design change, not a fix.
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
    decoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            textDecoration = decoration,
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

/** A VT323 readout: clocks, counts, countdowns — anything the eye reads as a quantity. */
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

/** A filled block of exact size — the design builds its glyphs and bars out of these. */
@Composable
fun Block(width: Dp, height: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).background(color))
}

/** A square of exact size. */
@Composable
fun Dot(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color))
}

/**
 * A hairline on one side only, painted rather than laid out.
 *
 * `Modifier.border` strokes all four sides and takes space on all four. The design uses
 * one-sided rules constantly — a row separator, a panel's top edge — and drawing them keeps the
 * content box exactly where the source put it.
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

/**
 * A segmented bar — the design's only progress form.
 *
 * Discrete segments, never a continuous fill. *Comparing quantities is perception; adding
 * numbers is computation*, and a segmented bar reads at a glance across a room where a
 * percentage does not. It is also the shape that lets the Egress countdown replace System
 * Integrity **in place**, taking the Residents' only number away without the widget resizing.
 */
@Composable
fun SegmentBar(
    total: Int,
    lit: Int,
    litColor: Color,
    unlitColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 7.u,
    gap: Dp = 1.u,
) {
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(total) { index ->
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (index < lit) litColor else unlitColor)
            )
        }
    }
}

/**
 * A continuous bar, used only where the design uses a percentage width — the ability cooldowns
 * and the shared pool on page 2. Three units tall, so it reads as a hairline gauge rather than
 * as the segmented meter it must never be confused with.
 */
@Composable
fun FillBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = Amber.Edge,
    height: Dp = 3.u,
) {
    Box(modifier.fillMaxWidth().height(height).background(track)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
    }
}

/** A bordered block of text or content. The design's only container. */
@Composable
fun InfoBox(
    modifier: Modifier = Modifier,
    border: Color = Amber.Edge,
    fill: Color = Color.Transparent,
    padding: Dp = 7.u,
    gap: Dp = 0.u,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().border(1.u, border).background(fill).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}
