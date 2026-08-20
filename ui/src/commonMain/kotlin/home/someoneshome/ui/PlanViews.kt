package home.someoneshome.ui

import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Plans and marker glyphs, drawn rather than composed.
 *
 * A ten-by-twelve grid is 120 cells and several screens show one. As 120 `Box`es apiece that is
 * thousands of layout nodes for what is, in the end, rectangles — and this app has a whole-app
 * allocation budget of roughly 0.5 MB/s, measured on hardware in story 1.7. Drawing into a single
 * `Canvas` keeps the plan off the composition entirely.
 */

/**
 * A marker's shape, at the size it is read.
 *
 * **Filled, even-odd, never stroked.** The shapes are defined upstream as regions — a hole is a
 * second contour, which is how `ring` and the three frames work — and they were chosen by
 * measuring how confusable they are *as filled regions*. Stroking them instead would change the
 * thing that was measured.
 */
@Composable
fun MarkerGlyph(shape: MarkerShape, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val path = remember(shape.path) {
        PathParser().parsePathString(shape.path).toPath().apply {
            fillType = PathFillType.EvenOdd
        }
    }
    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / MarkerShapes.VIEWBOX
        scale(factor, factor, pivot = Offset.Zero) { drawPath(path, color) }
    }
}

/** The host's editor grid: a filled cell per square, in slate, on the bone LCD. */
@Composable
fun EditorPlan(cells: List<EditorCell>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val hair = 0.5.dp.toPx()
        cells.forEachIndexed { i, cell ->
            val b = cellBounds(i % Plan.COLS, i / Plan.COLS, size.width, size.height)
            cell.fill?.let { drawRect(it, Offset(b.x, b.y), Size(b.w, b.h)) }
            if (cell.hatch) drawHatch(b.x, b.y, b.w, b.h)
            drawRect(cell.border, Offset(b.x, b.y), Size(b.w, b.h), style = Stroke(hair))
        }
    }
}

/**
 * A cell's bounds, snapped so neighbours share an exact edge.
 *
 * Computing a cell as `index * width / cols` leaves sub-pixel gaps between adjacent fills, and on
 * a dark-field map those gaps read as faint seams *inside* a room — which is exactly what an
 * interior wall would look like. Deriving each edge from the same rounded boundary makes adjacent
 * cells abut precisely.
 */
private class CellBounds(val x: Float, val y: Float, val w: Float, val h: Float)

private fun cellBounds(col: Int, row: Int, width: Float, height: Float): CellBounds {
    val x0 = kotlin.math.round(col * width / Plan.COLS)
    val x1 = kotlin.math.round((col + 1) * width / Plan.COLS)
    val y0 = kotlin.math.round(row * height / Plan.ROWS)
    val y1 = kotlin.math.round((row + 1) * height / Plan.ROWS)
    return CellBounds(x0, y0, x1 - x0, y1 - y0)
}

/**
 * Stairs, drawn as a diagonal hatch.
 *
 * Transit zones are never counted, never carry a Subroutine and never carry a timed route, so
 * they must not read as a room you could be *in*. A hatch says "you pass through here" in a way
 * a fill of any intensity would not.
 */
private fun DrawScope.drawHatch(x: Float, y: Float, w: Float, h: Float) {
    val step = 4.dp.toPx()
    val stroke = 3.dp.toPx()
    clipRect(x, y, x + w, y + h) {
        var d = -h
        while (d < w + h) {
            drawLine(Amber.BoneHatch, Offset(x + d, y + h), Offset(x + d + h, y), strokeWidth = stroke)
            d += step
        }
    }
}

/**
 * A live plan: no fills except your own room and the stairs, and outlines only where a room
 * boundary actually falls.
 *
 * Dark-field discipline. On a lit-field map the whole plan glows; here only the edges do, which
 * is both the right look and the right number of lit pixels for a phone in a dark house.
 */
@Composable
fun LivePlan(cells: List<MapCell>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = 1.dp.toPx()
        cells.forEachIndexed { i, cell ->
            val b = cellBounds(i % Plan.COLS, i / Plan.COLS, size.width, size.height)
            cell.fill?.let { drawRect(it, Offset(b.x, b.y), Size(b.w, b.h)) }
            cell.top?.let { drawRect(it, Offset(b.x, b.y), Size(b.w, t)) }
            cell.bottom?.let { drawRect(it, Offset(b.x, b.y + b.h - t), Size(b.w, t)) }
            cell.start?.let { drawRect(it, Offset(b.x, b.y), Size(t, b.h)) }
            cell.end?.let { drawRect(it, Offset(b.x + b.w - t, b.y), Size(t, b.h)) }
        }
    }
}

/**
 * Room names, at each room's top-left corner.
 *
 * Separate from the counts because the design places them differently and for different reasons:
 * the name anchors the room in the plan, and the count sits at the centre so it cannot be read as
 * a position *within* the room.
 */
@Composable
fun PlanRoomLabels(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        Plan.rooms.forEach { room ->
            Box(
                Modifier.offset(
                    x = w * (room.c0.toFloat() / Plan.COLS),
                    y = h * (room.r0.toFloat() / Plan.ROWS),
                ).padding(horizontal = 4.u, vertical = 3.u)
            ) {
                Label(
                    room.name,
                    size = 5.5,
                    color = if (room.name == Plan.HERE) Amber.Bright else Amber.Dim,
                    tracking = 0.06,
                )
            }
        }
    }
}

/**
 * A room's occupancy count, at the room's centre.
 *
 * **Counts, never dots.** A dot implies a trackable individual and no such thing exists in this
 * system: you learn that four people were in the living room, never which four, and a numeral
 * says exactly that and nothing more. Centring it is part of that — a numeral placed anywhere
 * else in the room would start to look like a position.
 */
@Composable
fun PlanCounts(counts: List<Plan.RoomCount>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        counts.forEach { rc ->
            val cx = (rc.room.c0 + rc.room.c1 + 1) / 2f / Plan.COLS
            val cy = (rc.room.r0 + rc.room.r1 + 1) / 2f / Plan.ROWS
            Box(Modifier.offset(x = w * cx - 5.u, y = h * cy - 10.u)) {
                Readout(rc.count.toString(), size = 19.0, color = rc.ink, lineHeight = 1.0)
            }
        }
    }
}

/**
 * Room names and marker chips over the *editor* plan.
 *
 * The chip carries a count, or `T` for the terminal. It is drawn inverted because the editor is a
 * light field: on bone, inverted-dark is the emphasis, exactly as inverted-amber is on black.
 */
@Composable
fun EditorLabels(modifier: Modifier = Modifier, held: String? = null) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        editorLabels.forEach { spec ->
            val isHeld = held != null && spec.name == held
            val ink = if (isHeld) Amber.BoneChip else Amber.BoneInk
            val chipInk = if (isHeld) Amber.SlateFocusFill else Amber.SlateFill
            Column(
                Modifier.offset(
                    x = spec.leftFraction?.let { w * it } ?: spec.leftInset,
                    y = spec.topFraction?.let { h * it } ?: spec.topInset,
                )
            ) {
                Label(spec.name, size = 5.5, color = ink, tracking = 0.06)
                if (spec.chip != null) {
                    Box(
                        Modifier.padding(top = 1.u).let {
                            if (spec.round) it.size(8.u).background(ink, CircleShape)
                            else it.defaultMinSize(minWidth = 8.u).height(8.u).background(ink)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(spec.chip, size = 4.5, color = chipInk)
                    }
                }
            }
        }
    }
}

private class EditorLabelSpec(
    val name: String,
    val leftInset: Dp = 0.u,
    val leftFraction: Float? = null,
    val topInset: Dp = 0.u,
    val topFraction: Float? = null,
    val chip: String? = null,
    val round: Boolean = false,
)

/**
 * Label placements, kept as the design's own mix of pixel insets and percentages.
 *
 * Not derived from [Plan]'s rects on purpose: the design nudged several off their room's true
 * origin so the text clears the marker chip, and re-deriving them would silently undo that.
 */
private val editorLabels = listOf(
    EditorLabelSpec("KITCHEN", leftInset = 3.u, topInset = 2.u, chip = "1"),
    EditorLabelSpec("LIVING", leftFraction = 0.52f, topInset = 2.u, chip = "1"),
    EditorLabelSpec("HALL", leftInset = 3.u, topFraction = 0.34f, chip = "T", round = true),
    EditorLabelSpec("STAIRS", leftFraction = 0.62f, topFraction = 0.34f),
    EditorLabelSpec("STUDY", leftInset = 3.u, topFraction = 0.59f, chip = "1"),
    EditorLabelSpec("GARAGE", leftFraction = 0.52f, topFraction = 0.59f, chip = "2"),
)

/**
 * The terminal's mark: a `T` in a ring.
 *
 * **The T card is never an ordinary marker**, and it is drawn as the one circular token in a set
 * of 44 angular ones so that is obvious across a room and on a printed sheet.
 */
@Composable
fun TerminalToken(size: Dp, ink: Color, stroke: Dp = 1.u, textSize: Double = 10.0) {
    Box(Modifier.size(size).border(stroke, ink, CircleShape), contentAlignment = Alignment.Center) {
        Label("T", size = textSize, color = ink)
    }
}

/** A dashed outline, which the design uses only for "not placed yet". */
fun Modifier.dashedBorder(color: Color, width: Dp = 1.u): Modifier = drawBehind {
    drawRect(
        color = color,
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        ),
    )
}
