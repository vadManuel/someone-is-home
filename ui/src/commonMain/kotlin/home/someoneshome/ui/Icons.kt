package home.someoneshome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * The springboard icon set, carried across as the design's own SVG path data.
 *
 * **Parsed, not re-drawn.** Re-expressing a path as a sequence of `lineTo` calls is a
 * transcription with no reviewer: a mistyped coordinate produces an icon that is subtly wrong and
 * looks deliberate. Keeping the `d` strings means each icon can be diffed against the source
 * character for character. The design's `<rect>` elements are the one exception — those are
 * rewritten as equivalent path data, because a rect is not path syntax.
 *
 * Every icon is **stroked, never filled**, at 1.4–1.5 units on a 20-unit grid. That is what makes
 * the set read as one family, and it is the dark-field rule doing its work: an outline lights a
 * fraction of the pixels a solid glyph would.
 */
object PanelIcons {
    const val VIEWBOX = 20f

    val Files = listOf("M3 5h5l2 2h7v9H3z")
    val Notes = listOf("M5 3h7l3 3v11H5z", "M7.5 10h5M7.5 13h5")
    val Phone = listOf(
        "M6 4c-1 1-1.4 2.4-1 3.4.7 1.6 1.8 3.2 3.1 4.5s2.9 2.4 4.5 3.1c1 .4 2.4 0 3.4-1l-2.4-2.4" +
            "-1.9.9c-.9-.5-1.7-1.1-2.4-1.8s-1.3-1.5-1.8-2.4l.9-1.9z"
    )
    val Messages = listOf("M3 5h14v10H3z", "M3 5.5l7 5.5 7-5.5")
    val Settings = listOf("M3 6h14M3 10h14M3 14h14")
    val SettingsDots = listOf(Triple(7f, 6f, 1.6f), Triple(12f, 10f, 1.6f), Triple(6f, 14f, 1.6f))
    val Terminal = listOf("M3 4h14v11H3z", "M6 8l2 1.6L6 11.2M10.5 11.5H14")
    val Camera = listOf("M3 6h14v9H3z")
    val CameraLens = listOf(Triple(10f, 10.5f, 2.6f))
    val Work = listOf("M3 4h14v12H3z", "M6 8h8M6 11h5")
    val Scan = listOf("M3 7V3h4M17 7V3h-4M3 13v4h4M17 13v4h-4M6 10h8")
    val Lock = listOf("M5 9h10v7H5z", "M7.5 9V7a2.5 2.5 0 0 1 5 0v2")
}

/**
 * Draws one of [PanelIcons]' path sets, stroked.
 *
 * Paths are parsed once and cached against the path data. Parsing on every recomposition would
 * allocate on a screen that is on camera for the whole round, and this app has a measured
 * whole-app allocation budget to stay inside.
 */
@Composable
fun PanelIcon(
    paths: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 16.u,
    color: Color = Amber.Bright,
    strokeWidth: Float = 1.4f,
    circles: List<Triple<Float, Float, Float>> = emptyList(),
) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / PanelIcons.VIEWBOX
        scale(factor, factor, pivot = Offset.Zero) {
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
            parsed.forEach { drawPath(it, color, style = stroke) }
            circles.forEach { (cx, cy, r) ->
                drawCircle(color, radius = r, center = Offset(cx, cy), style = stroke)
            }
        }
    }
}

/**
 * A springboard tile: a bordered square with an icon in it, and a caption beneath.
 *
 * **The dimmed tile is not a disabled tile that happens to look dim.** Camera is drawn at edge
 * intensity with no tap target at all, because an app that responded and then refused would be a
 * channel — the refusal itself carries information about what the device thinks you may do.
 */
@Composable
fun SpringTile(
    label: String,
    paths: List<String>,
    modifier: Modifier = Modifier,
    circles: List<Triple<Float, Float, Float>> = emptyList(),
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val border = if (enabled) Amber.Dim else Amber.Edge
    val ink = if (enabled) Amber.Bright else Amber.Faint
    val caption = if (enabled) Amber.Mid else Amber.Faint

    Column(
        modifier.then(if (enabled) Modifier.tap(onClick) else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.u),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(30.u).border(1.u, border), contentAlignment = Alignment.Center) {
            PanelIcon(paths, color = ink, circles = circles)
        }
        Label(label, size = 5.5, color = caption, tracking = 0.08)
    }
}
