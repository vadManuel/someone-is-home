package home.someoneshome.ui

import home.someoneshome.model.CellRect
import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.RoomKind
import home.someoneshome.model.PlanRoom as PaintedRoom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

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

/**
 * The host's editor grid: a filled cell per square, in slate, on the bone LCD — and the rectangle
 * under the finger, while there is one.
 *
 * **The preview is drawn, not composed.** It changes on every frame of a drag, and a rectangle
 * that recomposed the grid to move would be a layout pass per frame for something that is four
 * numbers. It is deliberately *not* a room yet: the model has not been asked, and it may refuse —
 * [blocked] is the editor's live answer to whether it would, drawn in the one warning colour so
 * the host knows before they lift their finger rather than after.
 */
@Composable
fun EditorPlan(
    cells: List<EditorCell>,
    modifier: Modifier = Modifier,
    cols: Int = HomeEditorModel.COLS,
    rows: Int = HomeEditorModel.ROWS,
    preview: CellRect? = null,
    blocked: Boolean = false,
) {
    Canvas(modifier) {
        val hair = 0.5.dp.toPx()
        cells.forEachIndexed { i, cell ->
            val b = cellBounds(i % cols, i / cols, size.width, size.height, cols, rows)
            cell.fill?.let { drawRect(it, Offset(b.x, b.y), Size(b.w, b.h)) }
            if (cell.hatch) drawHatch(b.x, b.y, b.w, b.h)
            drawRect(cell.border, Offset(b.x, b.y), Size(b.w, b.h), style = Stroke(hair))
        }
        if (preview != null) {
            val a = cellBounds(preview.x, preview.y, size.width, size.height, cols, rows)
            val z = cellBounds(
                preview.x + preview.width - 1, preview.y + preview.height - 1,
                size.width, size.height, cols, rows,
            )
            val ink = if (blocked) Amber.Caution else Amber.SlateInk
            val at = Offset(a.x, a.y)
            val span = Size(z.x + z.w - a.x, z.y + z.h - a.y)
            drawRect(ink.copy(alpha = 0.35f), at, span)
            drawRect(ink, at, span, style = Stroke(2.dp.toPx()))
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

private fun cellBounds(
    col: Int,
    row: Int,
    width: Float,
    height: Float,
    cols: Int = Plan.COLS,
    rows: Int = Plan.ROWS,
): CellBounds {
    val x0 = kotlin.math.round(col * width / cols)
    val x1 = kotlin.math.round((col + 1) * width / cols)
    val y0 = kotlin.math.round(row * height / rows)
    val y1 = kotlin.math.round((row + 1) * height / rows)
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
    // The mockup's `repeating-linear-gradient(45deg, #B3AA96 0 3px, #234A3A 3px 4px)`, per cell as
    // its cells draw it: three of pale to one of slate, measured ACROSS the stripe, running the way
    // CSS's 45deg runs (see [hatch]). The first port stroked 3-wide lines 4 apart along the edge,
    // which is 2.8 across, so the slate never showed and the stairs went flat pale.
    val root2 = kotlin.math.sqrt(2f)
    val period = 4.dp.toPx() * root2
    val slate = 1.dp.toPx() * root2
    val stripes = hatchStripes(w, h, period)
    if (stripes == 0) return
    clipRect(x, y, x + w, y + h) {
        drawRect(Amber.BoneHatch, Offset(x, y), Size(w, h))
        // A stripe is the run where `x - y + h` falls in [k*period + pale, (k+1)*period). A counted
        // walk with one path, never a float stepping until it passes the edge: see [hatchStripes].
        val path = androidx.compose.ui.graphics.Path()
        repeat(stripes) { k ->
            val d = -h + period - slate + k * period
            path.reset()
            path.moveTo(x + d, y); path.lineTo(x + d + slate, y)
            path.lineTo(x + d + slate + h, y + h); path.lineTo(x + d + h, y + h); path.close()
            drawPath(path, Amber.Slate)
        }
    }
}

/**
 * **How many stripes cross a [w] by [h] cell, which is always a finite number** — zero for a cell
 * with no size, a size that is not a number, or a period that does not move.
 *
 * Counted once rather than walked: a drawing loop that steps a float until it passes an edge never
 * ends when the step or the edge is not a real number, and every pass records into the frame. A
 * runaway dotted rule on the self-test screen took a test worker to some 80 GB that way.
 */
internal fun hatchStripes(w: Float, h: Float, period: Float): Int {
    if (!(w.isFinite() && h.isFinite() && period.isFinite()) || w <= 0f || h <= 0f || period <= 0f) return 0
    // Capped as well: no cell on a phone needs ten thousand stripes, and a cell that claims to is
    // a measuring fault this must not turn into ten thousand native paths.
    return (kotlin.math.ceil((w + h) / period).toInt() + 1).coerceAtMost(MAX_STRIPES)
}

private const val MAX_STRIPES = 10_000

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
 * **The live plan as the mockup draws it** (D-202, `kit.py`'s `plan` with the live map's cells):
 * the cells, the room names and the counts, in one place, all from [Ramp].
 *
 * Only room cells are drawn. A room's edge falls where its neighbour is another room or none, in
 * [Ramp.Line] at one unit, and **your own room is outlined in full ink at two units over the
 * [Ramp.Fill] ground** -- the one anchor a count-based map can offer. The stairs are the hard
 * stripe, a [Ramp.Faint] hairline every six units, because nobody can be *in* them.
 *
 * Counts sit at the room's centre for [PlanCounts]' reason: anywhere else would read as a
 * position. **The ink of each count is the caller's** -- it is what carries staleness, and this
 * draws whatever it is handed.
 *
 * It is a sibling of [LivePlan] and its label and count helpers rather than a change to them,
 * because the ghost map still draws through those until its own family is restyled.
 */
@Composable
fun KitLivePlan(counts: List<Plan.RoomCount>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.matchParentSize()) {
            val hair = 1.dp.toPx()
            val bold = 2.dp.toPx()
            // The CSS stripe: 45 degrees, one unit of line in every six measured across it.
            val period = 6.dp.toPx() * kotlin.math.sqrt(2f)
            for (row in 0 until Plan.ROWS) for (col in 0 until Plan.COLS) {
                val room = Plan.roomAt(row, col) ?: continue
                val mine = room.name == Plan.HERE
                val b = cellBounds(col, row, size.width, size.height)
                if (room.transit) {
                    clipRect(b.x, b.y, b.x + b.w, b.y + b.h) {
                        var d = -b.h
                        while (d < b.w) {
                            drawLine(
                                Ramp.Faint, Offset(b.x + d, b.y), Offset(b.x + d + b.h, b.y + b.h),
                                strokeWidth = hair,
                            )
                            d += period
                        }
                    }
                } else if (mine) {
                    drawRect(Ramp.Fill, Offset(b.x, b.y), Size(b.w, b.h))
                }
                val ink = if (mine) Ramp.Ink else Ramp.Line
                val t = if (mine) bold else hair
                if (Plan.roomAt(row - 1, col) !== room) drawRect(ink, Offset(b.x, b.y), Size(b.w, t))
                if (Plan.roomAt(row + 1, col) !== room) drawRect(ink, Offset(b.x, b.y + b.h - t), Size(b.w, t))
                if (Plan.roomAt(row, col - 1) !== room) drawRect(ink, Offset(b.x, b.y), Size(t, b.h))
                if (Plan.roomAt(row, col + 1) !== room) drawRect(ink, Offset(b.x + b.w - t, b.y), Size(t, b.h))
            }
        }
        Plan.rooms.forEach { room ->
            Words(
                room.name,
                Modifier.offset(x = w * (room.c0.toFloat() / Plan.COLS), y = h * (room.r0.toFloat() / Plan.ROWS))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                size = 18, color = if (room.name == Plan.HERE) Ramp.Ink else Ramp.Dim, lineHeight = 1.0,
            )
        }
        counts.forEach { rc ->
            val cx = (rc.room.c0 + rc.room.c1 + 1) / 2f / Plan.COLS
            val cy = (rc.room.r0 + rc.room.r1 + 1) / 2f / Plan.ROWS
            Words(
                rc.count.toString(),
                // The mockup's translate(-50%, -40%): centred across, a little below centre down.
                Modifier.offset(x = w * cx, y = h * cy).layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(p.width, p.height) { p.place(-p.width / 2, -(p.height * 0.4f).roundToInt()) }
                },
                size = 44, color = rc.ink, lineHeight = 1.0,
            )
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
 * Room names and marker chips over the *editor* plan — **read off the painted rooms**.
 *
 * The chip carries a count, or `T` for the terminal. It is drawn inverted because the editor is a
 * light field: on bone, inverted-dark is the emphasis, exactly as inverted-amber is on black.
 *
 * The port carried a hand-tuned list of placements here, several of them nudged off their room's
 * true origin so the text cleared the chip. That list could only ever describe one plan — the
 * design's own bungalow — so a host who painted their own house got six labels in the wrong
 * places, or none. Each label now sits at its room's top-left cell, which is a placement that is
 * right for every plan rather than nudged for one.
 *
 * ### A name is held inside its own room, and ellipsised rather than allowed out
 *
 * The placement was right and the *width* was unbounded, so a long name ran off to the right for
 * as far as it wanted. On the design's bungalow that is only untidy — `KITCHEN` crosses one of its
 * own room's internal gridlines — but the room it starts in is not always four cells wide, and a
 * `CONSERVATORY` painted on one cell would have printed itself across two other people's rooms.
 * A label reaching into a room it does not name is a host reading the wrong name for a place they
 * are about to go and stand in, in the dark, so it is bounded by the room it belongs to.
 *
 * The bound is the **anchor stroke's** width rather than the room's overall extent, because the
 * anchor stroke is the run of cells the label is actually sitting on: an L-shaped room is wider
 * somewhere else, and somewhere else is not where the text is.
 */
@Composable
fun EditorLabels(
    rooms: List<PaintedRoom>,
    modifier: Modifier = Modifier,
    held: String? = null,
    markers: (String) -> Int = { 0 },
    terminal: String? = null,
    meeting: String? = null,
    cols: Int = HomeEditorModel.COLS,
    rows: Int = HomeEditorModel.ROWS,
    size: Int = 18,
) {
    // Mockup pixels (D-202): every caller draws this inside [Mockup].
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        rooms.forEach { room ->
            val anchor = room.strokes.minWithOrNull(compareBy({ it.y }, { it.x })) ?: return@forEach
            val isHeld = room.name == held
            // The label sits on a patch of its own room's fill, as the mockup's `boneRooms` draws
            // it, so a gridline running under the name does not cut through the letters.
            val ground = when {
                isHeld -> Amber.SlateFocusFill
                room.kind == RoomKind.Stairs -> Amber.BoneHatch
                else -> Amber.SlateFill
            }
            val ink = if (isHeld) Amber.BoneChip else Amber.SlateInk
            val count = markers(room.name)
            // What the label has to fit in: the anchor stroke's cells, less the margin that holds
            // it off the room's own border.
            val inset = 4.dp
            val room0 = w * (anchor.width.toFloat() / cols)
            val bound = (room0 - inset * 2).coerceAtLeast(inset)
            Row(
                Modifier
                    .offset(x = w * (anchor.x.toFloat() / cols), y = h * (anchor.y.toFloat() / rows))
                    .padding(inset)
                    .widthIn(max = bound)
                    .background(ground)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Words(
                    room.name, Modifier.weight(1f, fill = false), size = size, color = ink,
                    lineHeight = 1.0, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (count > 0) EditorChip("$count", ink, ground, round = false)
                // The T and the U are their own chips rather than replacing the count: a room can
                // hold cards AND a reserved card — the meeting card in particular is expected to
                // share (D-121) — and a chip that showed one of them would be the editor quietly
                // hiding another from the host who is about to go and find it.
                if (room.name == terminal) EditorChip("T", ink, ground, round = true)
                if (room.name == meeting) EditorChip("U", ink, ground, round = true)
            }
        }
    }
}

@Composable
private fun EditorChip(text: String, ink: Color, chipInk: Color, round: Boolean) {
    Box(
        Modifier.height(18.dp).defaultMinSize(minWidth = 18.dp)
            .background(ink, if (round) CircleShape else RectangleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Words(text, size = 17, color = chipInk, lineHeight = 1.0)
    }
}

/**
 * The terminal's mark: a `T` in a ring.
 *
 * **The T card is never an ordinary marker**, and it is drawn as a circular token in a set of
 * angular ones so that is obvious across a room and on a printed sheet.
 */
@Composable
fun TerminalToken(size: Dp, ink: Color, stroke: Dp = 1.u, textSize: Double = 10.0) {
    ReservedToken("T", size, ink, stroke, textSize)
}

/**
 * The meeting card's mark: a `U` in a ring.
 *
 * **A ring, like the terminal's, because the two are the same kind of thing** (D-121) — a card
 * that is never an ordinary marker, printed with a letter, belonging to one room in the whole
 * home. Drawing the meeting card as a marker glyph would put the one card a whole party walks to
 * in the middle of a sheet of cards one person walks to, looking like any of them.
 *
 * The letter differs and nothing else does, which is also what the paper looks like.
 */
@Composable
fun MeetingToken(size: Dp, ink: Color, stroke: Dp = 1.u, textSize: Double = 10.0) {
    ReservedToken("U", size, ink, stroke, textSize)
}

@Composable
private fun ReservedToken(letter: String, size: Dp, ink: Color, stroke: Dp, textSize: Double) {
    Box(Modifier.size(size).border(stroke, ink, CircleShape), contentAlignment = Alignment.Center) {
        Label(letter, size = textSize, color = ink)
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
