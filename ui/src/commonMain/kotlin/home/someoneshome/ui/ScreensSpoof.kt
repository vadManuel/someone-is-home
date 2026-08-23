package home.someoneshome.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import home.someoneshome.model.FloorView
import home.someoneshome.model.PlanCell
import home.someoneshome.model.RoomOutline
import kotlin.math.floor

/**
 * **SPOOF — the home's plan, and you tap a room on it** (`gdd.md:313`, the owner's targeting
 * ruling).
 *
 * *False check-in in a room you are not in. Manufactures a phantom occupant.*
 *
 * ### Why this is a plan where the other two are lists
 *
 * The ruling is explicit — **Spoof picks from the MAP** — and it is the same trade `ScreensTargets`
 * takes twice, taken once more and priced higher. *The glow of reading the map is the deliberate
 * price*: a list can be scanned down in a second and a plan has to be **oriented to**, which is the
 * point. `gdd.md:1143` is what makes that price honest rather than punitive — Spoof is *slow,
 * indirect, and asks a Insider to think about the map layer while standing in a dark hallway*, and
 * the design chose to watch that in playtest rather than sand it down.
 *
 * So this file holds `ScreensTargets`' whole header by reference and adds nothing to soften it:
 * **nothing here may be quicker than reading the plan.** No room list beside it, no search, no
 * "here" marker, no last-room shortcut. A room list would be the picker the ruling declined.
 *
 * ### There is no YOU ARE HERE and there cannot be one
 *
 * `Plan.HERE` exists on the terminal's fixture map and is deliberately absent here. The house does
 * know where a seat last scanned (D-136) — and drawing it would hand an Insider a live readout of
 * the presence plane on the one screen where they are choosing a lie to tell about it, which is a
 * disclosure nobody ruled and one this ability does not need. **It is also not sent**: the plan on
 * this phone is rooms and squares and cannot carry a position (see `HomePlanView`).
 *
 * ### A tap fires, and the screen says nothing about what happened
 *
 * `ScreensTargets`' rule and it bites hardest here, because a Spoof's whole consequence is invisible
 * — see `Actions.fireSpoof`. **No room highlights after a tap, no tick, no line, and no room drawn
 * differently because it is the one you last claimed.** Each of those is `gdd.md:398`'s forbidden
 * actor-side feedback, and each is the obvious thing to add to a screen that looks like it did
 * nothing. Looking like it did nothing is the ability working.
 *
 * ### It is never drawn for a Resident
 *
 * Not by a check on this page — there is no role branch in this file — but by there being no way
 * here: a Resident's tier-2 tiles take no pointer input at all (D-142).
 */
@Composable
fun SpoofRoomsScreen(vals: PanelVals) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 7.u).padding(top = 7.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            Label(vals.tier2B, size = 13.0, color = Amber.Bright, tracking = 0.18)
            // The house's register, and a line that reads the same over a shoulder. Deliberately
            // not "PICK A ROOM TO PRETEND YOU ARE IN": a picker that narrates the lie is a picker
            // somebody can read across a dark room and know what they are looking at.
            Label("SELECT A ROOM", size = 6.0, color = Amber.Dim, tracking = 0.12)
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.u),
            ) {
                // **Every floor, one under the other, scrolling.** A home has more than one storey
                // and a picker that showed one of them would be an ability that cannot name half a
                // house. Floors are not tabs for the same reason the pickers have no search: a tab
                // is quicker than reading, and reading is the price.
                //
                // **No empty-state copy**, exactly as the two list pickers have none: a round armed
                // with no home draws an empty column, which is the honest picture of one.
                vals.homePlan.floors.forEach { floor ->
                    Label(floor.name, size = 6.0, color = Amber.Edge, tracking = 0.14)
                    SpoofFloor(floor, onPick = { actions.fireSpoof(it) })
                }
            }
            val go = navigator()
            Label(
                "BACK",
                modifier = Modifier.fillMaxWidth().tap { go(ScreenId.Page2) }
                    .padding(vertical = 7.u).testTag(TARGET_BACK),
                size = 6.5, color = Amber.Dim, tracking = 0.14, align = TextAlign.Center,
            )
        }
    }
}

/**
 * One storey: the plan drawn, the names over it, and one gesture for the whole grid.
 *
 * ### One `pointerInput` rather than a tap target per room
 *
 * `EditorSurface`'s arrangement, and it is here for the reason `PlanViews`' header gives for drawing
 * the plan instead of composing it: a ten-by-twelve grid is 120 cells, and a Box per cell is
 * thousands of layout nodes for what is in the end rectangles — against a whole-app allocation
 * budget of roughly 0.5 MB/s measured on hardware in story 1.7. **The tap resolves a coordinate to
 * a cell to a room**, which is one node and no allocation, and it also gives the thing a thumb in
 * the dark actually wants: the whole room is the target, not the four characters of its name.
 *
 * ### Stairs are drawn and are not a target
 *
 * *The map knows only room and stairs, and stairs hold nothing.* Nobody checks in at a staircase, so
 * a phantom in one would be a claim the fiction has no room for. **The refusal is here, on a
 * client-visible path, and it is not rule 1's forbidden shape** — rule 1 governs what the *house*
 * emits in response to a fire, and this is a finger that never became a fire. Nothing is sent, so
 * there is no effect whose absence could say anything, and the stairs cannot become a detector
 * because a hatched square is visibly not a room on every plan in the game.
 *
 * They are drawn rather than cut out because a plan with a hole where the reader's own staircase is
 * is a worse map to orient to in the dark than one with a hatched square in it.
 */
@Composable
private fun SpoofFloor(floor: FloorView, onPick: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * Plan.ROWS / Plan.COLS
        Box(
            Modifier.fillMaxWidth().aspectRatio(Plan.COLS.toFloat() / Plan.ROWS)
                .pointerInput(floor) {
                    detectTapGestures { at ->
                        val cell = cellAt(at, size)
                        val room = floor.rooms.firstOrNull { it.covers(cell) }
                        // A tap on a gap between rooms, or on the stairs, sends nothing. See the
                        // KDoc above for why that is not a refusal on a client-visible path.
                        if (room != null && !room.stairs) onPick(room.room)
                    }
                }
                .testTag("$SPOOF_FLOOR${floor.name}"),
        ) {
            LivePlan(floor.mapCells(), Modifier.fillMaxSize())
            SpoofRoomLabels(floor.rooms, w, h)
        }
    }
}

/**
 * Room names, at each room's top-left cell, bounded by the cells that name sits on.
 *
 * `EditorLabels`' placement and its bound, in the dark field's ink. The port's hand-tuned label
 * list could only ever describe the design's own bungalow; a name at its own room's origin is a
 * placement that is right for every plan a host paints. **Ellipsised rather than allowed out**,
 * because a label reaching into a room it does not name is somebody reading the wrong name for a
 * place they are about to claim to be in.
 *
 * **Not tappable.** The gesture belongs to the whole floor — see [SpoofFloor].
 */
@Composable
private fun SpoofRoomLabels(rooms: List<RoomOutline>, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    rooms.forEach { room ->
        val anchor = room.cells.minWithOrNull(compareBy({ it.y }, { it.x })) ?: return@forEach
        val inset = 3.u
        // The run of cells the label actually sits on, which is what bounds it. A room is wider
        // somewhere else and somewhere else is not where the text is.
        val run = room.cells.count { it.y == anchor.y && it.x >= anchor.x && room.runsTo(anchor, it) }
        Column(
            Modifier
                .offset(
                    x = w * (anchor.x.toFloat() / Plan.COLS),
                    y = h * (anchor.y.toFloat() / Plan.ROWS),
                )
                .padding(start = inset, top = 2.u)
                .width((w * (run.toFloat() / Plan.COLS) - inset).coerceAtLeast(inset)),
        ) {
            Label(
                room.room,
                size = 5.5,
                // Stairs are named in the faint ink the hatch is drawn in, so that a room you can
                // claim and a transit you cannot are distinguishable before your thumb lands.
                color = if (room.stairs) Amber.Edge else Amber.Dim,
                tracking = 0.06,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Whether this room covers [cell]. */
private fun RoomOutline.covers(cell: PlanCell): Boolean = cells.any { it.x == cell.x && it.y == cell.y }

/** Whether the cells from [anchor] to [end] along one row are unbroken — the label's bound. */
private fun RoomOutline.runsTo(anchor: PlanCell, end: PlanCell): Boolean =
    (anchor.x..end.x).all { x -> cells.any { it.x == x && it.y == anchor.y } }

/**
 * The floor as [MapCell]s — `Plan.mapCells()`'s arithmetic, over a plan the house sent rather than
 * over the design's fixture bungalow.
 *
 * **Dark-field discipline: no fills except the stairs, outlines only where a room boundary falls.**
 * On a lit-field map the whole plan glows; here only the edges do, which is both the right look and
 * the right number of lit pixels for a phone in a dark house.
 *
 * The fixture's `HERE` fill has no equivalent and must not acquire one — see [SpoofRoomsScreen].
 */
private fun FloorView.mapCells(): List<MapCell> =
    List(Plan.ROWS * Plan.COLS) { i ->
        val here = PlanCell(x = i % Plan.COLS, y = i / Plan.COLS)
        val room = rooms.firstOrNull { it.covers(here) }

        fun roomAt(x: Int, y: Int): RoomOutline? =
            rooms.firstOrNull { it.covers(PlanCell(x, y)) }

        fun edgeTo(other: RoomOutline?): Color? =
            if (other?.room != room?.room) {
                if (room != null) Amber.Dim else Amber.Deep
            } else {
                null
            }

        MapCell(
            top = edgeTo(roomAt(here.x, here.y - 1)),
            end = edgeTo(roomAt(here.x + 1, here.y)),
            bottom = edgeTo(roomAt(here.x, here.y + 1)),
            start = edgeTo(roomAt(here.x - 1, here.y)),
            fill = if (room?.stairs == true) Amber.Edge else null,
        )
    }

/**
 * A tap's cell. `HomeEditor`'s `cellAt`, which is private to that file — copied rather than shared
 * because the two grids are the same size by coincidence of the editor's own constants, and a
 * shared helper would make that coincidence look like a contract.
 */
private fun cellAt(at: Offset, size: IntSize): PlanCell = PlanCell(
    x = floor(at.x / (size.width.toFloat() / Plan.COLS)).toInt().coerceIn(0, Plan.COLS - 1),
    y = floor(at.y / (size.height.toFloat() / Plan.ROWS)).toInt().coerceIn(0, Plan.ROWS - 1),
)

/** The prefix a floor's plan is tagged with, so a test can tap a coordinate on a named storey. */
const val SPOOF_FLOOR: String = "spoof-floor-"
