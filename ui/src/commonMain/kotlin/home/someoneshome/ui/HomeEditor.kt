package home.someoneshome.ui

import home.someoneshome.model.Cell
import home.someoneshome.model.CellRect
import home.someoneshome.model.Floor
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.PaintResult
import home.someoneshome.model.Room
import home.someoneshome.model.RoomKind
import home.someoneshome.model.PlanRoom as PaintedRoom

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor

/**
 * **The home editor's state, and the only mutable thing in `:ui` that is not chrome.**
 *
 * The host paints a plan; this holds it. It sits *beside* [PanelState] for the same reason
 * [FlowModel]'s trail does: `PanelState` is flat and inert, every field of it already decided at
 * the effect boundary, and a plan under a finger is neither flat nor decided.
 *
 * ### It answers no game question, and it never will
 *
 * A [HousePlan] is host-side setup data, drawn in the light, weeks before anybody plays. Nothing
 * here is read during a round: the plan is saved, and in play the authority sends whatever
 * narrower view a screen needs. That is what makes an editor with real mutable state lawful in a
 * module that cannot see `core` — there is no rule here to leak, because there is no rule here.
 *
 * ### The refusals are the model's, not this layer's
 *
 * [HousePlan.paint] refuses politely — overlapping cells, a name already used, a storey that is
 * not there — and [refusal] is where the answer is put so the editor can say it **while the
 * finger is still on the screen**. This class adds no rules of its own; every `no` in it is one
 * of the model's, translated into a line of screen copy. A second opinion about what is legal is
 * a second opinion that will one day disagree.
 *
 * ### Markers and the terminal are fixtures, and are marked as such
 *
 * Dropping markers into cells is story 4.4 and the Terminal designation is 4.5; neither is built
 * yet. What is here is the *shape* of that data — which room holds what — so the rules that
 * depend on it can be real now: **stairs hold nothing** (D-099), so [setKind] to stairs
 * unregisters the room's cards as part of the change rather than in a flow that remembers to,
 * and REVIEW HOME is gated on a terminal existing rather than on a flag somebody sets.
 */
class HomeEditorModel(
    plan: HousePlan,
    floorName: String,
    markers: Map<String, List<MarkerShape>> = emptyMap(),
    terminal: String? = null,
) {

    var plan: HousePlan by mutableStateOf(plan)
        private set

    /** The storey the host has open. Floors are unordered, so this is a name and not an index. */
    var floorName: String by mutableStateOf(floorName)
        private set

    /**
     * The room the panel is about.
     *
     * **Null only when the plan holds no rooms at all.** The room panel always has a subject:
     * opening it with nothing held would draw a name field for a room that does not exist, and
     * the host would type into it. Painting one holds it, deleting one holds another.
     */
    var held: String? by mutableStateOf(plan.floorNamed(floorName)?.rooms?.firstOrNull()?.name)
        private set

    /** The rectangle under the finger, live. Null except during a drag. */
    var drag: CellRect? by mutableStateOf(null)
        private set

    /**
     * The last refusal, in the host's words.
     *
     * Cleared by the next thing the host does, so it describes the gesture they just made rather
     * than accumulating. Rule 6 — errors silent to the player — is about a player mid-round; a
     * host painting a house in a lit room is owed the reason.
     */
    var refusal: String? by mutableStateOf(null)
        private set

    /** Fixture. Room name to the cards registered in it — story 4.4 replaces the contents. */
    var markers: Map<String, List<MarkerShape>> by mutableStateOf(markers)
        private set

    /** Fixture. The one room holding the T card, or none — story 4.5 replaces the contents. */
    var terminal: String? by mutableStateOf(terminal)
        private set

    private var anchor: Cell? = null

    /**
     * The room the current drag started inside, if it started inside one.
     *
     * **This is how an L-shaped room gets made.** A room is a list of strokes and not one rect —
     * that is the design's own second argument for a grid, because real houses have L-shaped
     * rooms and rectangles cannot express them — so the editor needs a gesture that adds a
     * stroke to a room that already exists. Starting the drag *inside* a room is that gesture,
     * and it is unambiguous: a drag from bare grid can only be a new room, and a drag from
     * inside a room can only be that room growing, since a second room there would overlap it.
     */
    private var growing: String? = null

    // ---- What the screens read ----------------------------------------------------------------

    val floor: Floor? get() = plan.floorNamed(floorName)

    val rooms: List<PaintedRoom> get() = floor?.rooms.orEmpty()

    val heldRoom: PaintedRoom? get() = held?.let { plan.roomNamed(it) }

    /** The held room's type, or the type a room gets when there is no room: an ordinary one. */
    val heldKind: RoomKind get() = heldRoom?.kind ?: RoomKind.Room

    val heldName: String get() = heldRoom?.name.orEmpty()

    val heldMarkers: List<MarkerShape> get() = markersIn(heldName)

    fun markersIn(room: String): List<MarkerShape> = markers[room].orEmpty()

    /**
     * Whether a room holds anything a type change would take away.
     *
     * The one question the stairs warning turns on. An empty room becoming stairs costs nothing
     * and must not be interrogated about it; an occupied one costs every card in it.
     */
    fun holdsAnything(room: String): Boolean =
        markersIn(room).isNotEmpty() || terminal == room

    /** **No terminal, no playable home.** The gate on REVIEW HOME, and it is a fact, not a flag. */
    val hasTerminal: Boolean get() = terminal != null

    val floorCount: Int get() = plan.floors.size
    val roomCount: Int get() = plan.rooms.size
    val markerCount: Int get() = markers.values.sumOf { it.size }

    fun roomsOn(floor: String): Int = plan.floorNamed(floor)?.rooms?.size ?: 0

    fun markersOn(floor: String): Int =
        plan.floorNamed(floor)?.rooms?.sumOf { markersIn(it.name).size } ?: 0

    /** The room covering a cell on the open storey, or none — most of a grid is not a room. */
    fun roomAt(cell: Cell): PaintedRoom? = floor?.roomAt(cell)

    // ---- Painting -----------------------------------------------------------------------------

    /**
     * The finger went down. The anchor is one corner; the other one follows it.
     *
     * **Two corners, not a brush.** The design's own instruction on the screen is DRAG TWO
     * CORNERS TO ADD A ROOM, and a stroke is rectangular because a drag is — the room is the
     * union of strokes, which is what lets an L-shaped kitchen be one room with one name.
     */
    fun dragFrom(cell: Cell) {
        anchor = cell
        growing = roomAt(cell)?.name
        drag = spanning(cell, cell)
        refusal = null
    }

    fun dragTo(cell: Cell) {
        val from = anchor ?: return
        drag = spanning(from, cell)
    }

    /**
     * The finger came up. The rectangle becomes a room — a new one, or another stroke of the one
     * the drag started in — or the model says why it cannot.
     *
     * Returns the room's name, or null if it was refused. The caller uses that to decide nothing;
     * [refusal] carries the reason and the editor prints it.
     */
    fun dropDrag(): String? {
        val rect = drag ?: return null
        val grown = growing?.let { plan.roomNamed(it) }
        drag = null
        anchor = null
        growing = null
        val storey = floor ?: run { refusal = noStorey(floorName); return null }
        val room =
            if (grown != null) PaintedRoom(grown.room, grown.strokes + rect)
            else PaintedRoom(Room(freeRoomName()), listOf(rect))
        val painted = plan.paint(storey.name, room)
        return if (accept(painted)) {
            held = room.name
            room.name
        } else {
            null
        }
    }

    /** The finger left the screen without finishing. Nothing was drawn and nothing is refused. */
    fun cancelDrag() {
        drag = null
        anchor = null
        growing = null
    }

    /**
     * Whether the rectangle under the finger lands on cells **another** room already holds.
     *
     * The room being grown is not another room, and a stroke that overlaps the one it is being
     * added to is ordinary — cells are counted once. Colouring that as a collision would tell a
     * host their L-shaped kitchen was illegal at the exact moment it became L-shaped.
     */
    val dragBlocked: Boolean
        get() {
            val rect = drag ?: return false
            return rooms.any { room ->
                room.name != growing && room.strokes.any { it overlaps rect }
            }
        }

    // ---- The room panel ------------------------------------------------------------------------

    /** Open a room in the panel. The plan is what says which rooms there are. */
    fun open(name: String) {
        if (plan.roomNamed(name) == null) return
        held = name
        refusal = null
    }

    /**
     * Name the held room — the field, and the preset chips, which are the same act.
     *
     * A rename is a forget and a repaint, because a room's name is its identity everywhere else:
     * `Room` is a name and a kind, and it is what a card is registered to. The cards and the T
     * card come across with it, or a rename would silently unregister a room's contents.
     */
    fun renameHeld(to: String) {
        val room = heldRoom ?: return
        val name = to.trim().uppercase()
        if (name == room.name) return
        if (name.isEmpty()) {
            refusal = "A ROOM NEEDS A NAME"
            return
        }
        if (plan.roomNamed(name) != null) {
            refusal = alreadyCalled(name)
            return
        }
        val storey = plan.floorOf(room)?.name ?: run { refusal = noStorey(floorName); return }
        val without = plan.forget(room.name)
        val painted = without.paint(storey, PaintedRoom(Room(name, room.kind), room.strokes))
        if (accept(painted)) {
            markers = markers - room.name + (name to markersIn(room.name))
            if (terminal == room.name) terminal = name
            held = name
        }
    }

    /**
     * ROOM or STAIRS.
     *
     * **Stairs hold nothing, and that is enforced here rather than asked for.** D-099 makes a
     * registration into stairs impossible to construct; the editor's half of the same rule is
     * that the cards in a room stop being registered *as part of* the room becoming stairs. A
     * flow that changed the type and then remembered to clear the cards is a flow that will one
     * day not remember.
     *
     * The host is warned first — see [FlowModel.pickRoomType] — but only when there is something
     * to warn about. This is where it actually happens, whichever route reached it.
     */
    fun setKind(kind: RoomKind) {
        val room = heldRoom ?: return
        if (room.kind == kind) return
        val storey = plan.floorOf(room)?.name ?: run { refusal = noStorey(floorName); return }
        val painted = plan.paint(storey, PaintedRoom(Room(room.name, kind), room.strokes))
        if (accept(painted) && kind == RoomKind.Stairs) unregisterAll(room.name)
    }

    /** Forget the held room, its cards with it, and hold whatever is left on this storey. */
    fun deleteHeld() {
        val room = heldRoom ?: return
        plan = plan.forget(room.name)
        unregisterAll(room.name)
        held = floor?.rooms?.firstOrNull()?.name
        refusal = null
    }

    // ---- Floors --------------------------------------------------------------------------------

    /**
     * Add a storey and open it.
     *
     * **Additive and unordered.** The new one is named for how many there are and not for where
     * it sits, because there is no where: floors carry no number, nothing connects them, and the
     * app renders what was drawn. The host renames it.
     */
    fun addFloor(): String {
        val name = freeFloorName()
        plan = plan.withFloor(name)
        floorName = name
        held = null
        refusal = null
        return name
    }

    /** Switch storeys. The panel's subject goes with it — a room belongs to one floor. */
    fun openFloor(name: String) {
        if (plan.floorNamed(name) == null) return
        floorName = name
        held = plan.floorNamed(name)?.rooms?.firstOrNull()?.name
        drag = null
        anchor = null
        refusal = null
    }

    // ---- Internals -----------------------------------------------------------------------------

    /**
     * Take the plan if it was painted; take the reason in the host's words if it was not.
     *
     * **One place, so nothing is swallowed.** Every refusal the model can return is turned into
     * a line here — a `when` with an `else` that shrugs is how a host comes to believe the app
     * ignored them.
     */
    private fun accept(result: PaintResult): Boolean = when (result) {
        is PaintResult.Painted -> {
            plan = result.plan
            refusal = null
            true
        }

        is PaintResult.CellsAlreadyPainted -> {
            refusal = "THAT CROSSES ${result.by.name}"
            false
        }

        is PaintResult.NameAlreadyUsed -> {
            refusal = alreadyCalled(result.by.name)
            false
        }

        is PaintResult.NoSuchFloor -> {
            refusal = noStorey(result.name)
            false
        }
    }

    private fun unregisterAll(room: String) {
        markers = markers - room
        if (terminal == room) terminal = null
    }

    /**
     * A provisional name, and it is provisional on purpose.
     *
     * A painted room has to have a name because a name is what a card is registered to, and it
     * has to be unique across the whole plan for the same reason. ROOM 2 is obviously nobody's
     * word for a room, which is what sends the host to the panel to say what it really is.
     */
    private fun freeRoomName(): String = generateSequence(1) { it + 1 }
        .map { "ROOM $it" }
        .first { plan.roomNamed(it) == null }

    private fun freeFloorName(): String = generateSequence(plan.floors.size + 1) { it + 1 }
        .map { "FLOOR $it" }
        .first { plan.floorNamed(it) == null }

    private fun alreadyCalled(name: String) = "A ROOM IS ALREADY CALLED $name"

    private fun noStorey(name: String) = "NO STOREY CALLED $name"

    companion object {

        /**
         * The editor's viewport, in cells.
         *
         * **A window, not a bound.** [Cell] coordinates are unbounded and negative ones are
         * ordinary — a host who starts painting in the middle of the hall and works outwards must
         * not hit an origin — so this is how much of the grid a phone shows at once, and panning
         * it is a later story. It is the design's own ten-by-twelve grid.
         */
        const val COLS = 10
        const val ROWS = 12

        /**
         * The preset names the room panel offers as chips.
         *
         * Five, because five is what fits on one row at chip size, and these five because they
         * are the rooms every house has. Everything else is typed.
         */
        val PRESETS: List<String> = listOf("KITCHEN", "LIVING", "GARAGE", "BATH 1", "BED 1")

        /**
         * The bungalow — **the same rooms the live map draws, converted rather than restated.**
         *
         * [Plan]'s KDoc makes the promise this keeps: one source of truth for every screen that
         * draws a plan, because a room sitting in one place in the editor and another on the map
         * would be a bug nobody could see from inside the game. So the ground floor is read out
         * of [Plan.rooms] and turned into strokes, rather than typed a second time.
         *
         * The upper storey has no counterpart on the live map — the map is drawn for the room a
         * player is standing in — so it is written here, and it is what the host-setup screens
         * have always claimed: five rooms and four cards.
         */
        fun bungalow(): HomeEditorModel {
            val ground = Floor(
                GROUND,
                Plan.rooms.map { rect ->
                    PaintedRoom(
                        Room(rect.name, if (rect.transit) RoomKind.Stairs else RoomKind.Room),
                        listOf(
                            CellRect(
                                x = rect.c0,
                                y = rect.r0,
                                width = rect.c1 - rect.c0 + 1,
                                height = rect.r1 - rect.r0 + 1,
                            )
                        ),
                    )
                },
            )
            val upper = Floor(
                UPPER,
                listOf(
                    painted("BED 1", RoomKind.Room, 0, 0, 4, 4),
                    painted("BED 2", RoomKind.Room, 5, 0, 5, 4),
                    painted("LANDING", RoomKind.Room, 0, 5, 6, 2),
                    painted("TOP OF STAIRS", RoomKind.Stairs, 6, 5, 4, 2),
                    painted("BATH 1", RoomKind.Room, 0, 8, 10, 3),
                ),
            )
            return HomeEditorModel(
                plan = HousePlan.of(listOf(ground, upper)),
                floorName = GROUND,
                // Five cards on the ground floor and four upstairs — the nine the save screen
                // has always counted. The shapes are drawn from the real roster, because a
                // marker's shape is its whole identity to everyone who is not the app.
                markers = mapOf(
                    "KITCHEN" to shapes("triangle_up"),
                    "LIVING" to shapes("square"),
                    "STUDY" to shapes("diamond"),
                    "GARAGE" to shapes("triangle_up", "ring"),
                    "BED 1" to shapes("crescent"),
                    "BED 2" to shapes("wide_rect"),
                    "LANDING" to shapes("circle"),
                    "BATH 1" to shapes("triangle_down"),
                ),
                terminal = "HALL",
            )
        }

        const val GROUND = "GROUND"
        const val UPPER = "UPPER"

        private fun painted(name: String, kind: RoomKind, x: Int, y: Int, w: Int, h: Int) =
            PaintedRoom(Room(name, kind), listOf(CellRect(x, y, w, h)))

        private fun shapes(vararg ids: String): List<MarkerShape> =
            ids.mapNotNull { MarkerShapes[it] }
    }
}

/**
 * The open storey as cells, ready to draw.
 *
 * **The held room goes DARKER, and that is correct.** The design carries a comment describing the
 * opposite — selection keeps the accent while every other room drops to neutral putty — which was
 * an earlier intention its own code never implemented. Confirmed as the code, not the comment. Do
 * not "fix" this to match a comment that is not in this repo.
 *
 * [focus] is passed rather than assumed to be [HomeEditorModel.held], because the two screens
 * that draw a plan want different answers. The room panel is *about* a room and lights it; the
 * editor is about the whole storey and lights nothing — the panel always holds a room, so an
 * editor that drew the held one would open on a house with a room already picked out, which
 * reads as a selection the host did not make.
 *
 * Row-major over the viewport, so index `i` is column `i % COLS`, row `i / COLS` — the order
 * [EditorPlan] draws in.
 */
fun HomeEditorModel.editorCells(focus: String? = held): List<EditorCell> {
    val storey = floor
    return List(HomeEditorModel.ROWS * HomeEditorModel.COLS) { i ->
        val room = storey?.roomAt(Cell(x = i % HomeEditorModel.COLS, y = i / HomeEditorModel.COLS))
        when {
            room == null -> EditorCell(Amber.BonePutty, null)
            room.name == focus -> EditorCell(Amber.SlateFocus, Amber.SlateFocusFill)
            // Slate underneath, pale stripes on top: the source gradient is 3 units of BoneHatch
            // against 1 of Slate, so the SLATE is the gap colour. Leaving the fill null let the
            // bone ground show through and the stairs went pale.
            room.kind == RoomKind.Stairs -> EditorCell(Amber.Slate, Amber.Slate, hatch = true)
            else -> EditorCell(Amber.Slate, Amber.SlateFill)
        }
    }
}

/** The rectangle two corners span, in either order — a drag runs whichever way the finger went. */
fun spanning(a: Cell, b: Cell): CellRect = CellRect(
    x = minOf(a.x, b.x),
    y = minOf(a.y, b.y),
    width = maxOf(a.x, b.x) - minOf(a.x, b.x) + 1,
    height = maxOf(a.y, b.y) - minOf(a.y, b.y) + 1,
)

/**
 * Whether two strokes share a cell, **without expanding either into cells**.
 *
 * This is asked on every frame of a drag, and `CellRect.cells` builds a list. Sixty of those a
 * second against a whole-app budget of about half a megabyte a second is the wrong direction for
 * an answer four comparisons give.
 */
infix fun CellRect.overlaps(other: CellRect): Boolean =
    x < other.x + other.width && other.x < x + width &&
        y < other.y + other.height && other.y < y + height

/**
 * The editor the host-setup screens draw.
 *
 * Provided by [Screen], so a screen reads the plan the same way it reads its actions, and a test
 * that renders one screen gets its own editor rather than sharing a global one that the previous
 * test painted over.
 */
val LocalEditor: ProvidableCompositionLocal<HomeEditorModel> =
    staticCompositionLocalOf { HomeEditorModel.bungalow() }

/**
 * **The grid under the finger: drag two corners to add a room, tap a room to open it.**
 *
 * The two gestures sit in separate `pointerInput` blocks on purpose. A drag has to wait out touch
 * slop before it is a drag, and a tap has to wait for the finger to come up without having moved;
 * one detector cannot be waiting for both, and combining them by hand is how a slow, deliberate
 * drag from one corner of a room to the other ends up registering as a tap on the corner.
 *
 * ### The anchor is where the finger WENT DOWN, and getting that wrong loses a cell
 *
 * The drag is written out by hand rather than with `detectDragGestures` for one reason:
 * `detectDragGestures` reports the position at which the gesture *became* a drag — after touch
 * slop has been travelled — so the first corner arrives already a cell or two along the drag.
 * The host draws a four-cell room and gets a three-cell one, off by exactly the corner they
 * aimed at, and if that corner was inside a room the drag creates a new room beside it instead
 * of growing it. `awaitFirstDown` is the only position that is the corner the host chose.
 *
 * `EditorSurfaceTest` caught this; nothing else could have. The model's own tests are told which
 * cells the finger crossed, so they were being told the truth about a lie.
 *
 * ### Why the tap navigates through the actions layer rather than naming a screen
 *
 * Every other control in this app says where it goes, and `ScreenGraphTest` reads those straight
 * off the screens. This one cannot: where a tap lands depends on **which room is under it**, and
 * on most of the grid there is no room and it lands nowhere. So the edge is declared in
 * [Flow.viaActions] and walked by a test, the same footnote the room-type chip already carries.
 */
@Composable
fun EditorSurface(
    editor: HomeEditorModel,
    onOpenRoom: (Cell) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .pointerInput(editor) {
                awaitEachGesture {
                    // `requireUnconsumed = false` because the tap detector below is the inner
                    // node and consumes the down before this one is reached. Waiting for an
                    // unconsumed down here means waiting forever, and the whole painting gesture
                    // silently does nothing.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val past = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture

                    editor.dragFrom(cellAt(down.position, size))
                    editor.dragTo(cellAt(past.position, size))
                    val finished = drag(past.id) { editor.dragTo(cellAt(it.position, size)) }
                    if (finished) editor.dropDrag() else editor.cancelDrag()
                }
            }
            .pointerInput(editor) {
                detectTapGestures { onOpenRoom(cellAt(it, size)) }
            }
    ) {
        EditorPlan(
            editor.editorCells(focus = null),
            Modifier.fillMaxSize(),
            preview = editor.drag,
            blocked = editor.dragBlocked,
        )
        EditorLabels(
            editor.rooms,
            Modifier.fillMaxSize(),
            markers = { editor.markersIn(it).size },
            terminal = editor.terminal,
        )
    }
}

/**
 * Where on the grid a finger is, clamped to the viewport.
 *
 * Clamped rather than refused: a drag that runs off the edge of the plan should stop at the edge,
 * not stop tracking — a rectangle that freezes halfway through a drag reads as the app having
 * stopped listening. `floor` rather than a truncating cast, because truncation folds the two
 * cells either side of zero into one.
 */
private fun cellAt(at: Offset, size: IntSize): Cell = Cell(
    x = floor(at.x / (size.width.toFloat() / HomeEditorModel.COLS)).toInt()
        .coerceIn(0, HomeEditorModel.COLS - 1),
    y = floor(at.y / (size.height.toFloat() / HomeEditorModel.ROWS)).toInt()
        .coerceIn(0, HomeEditorModel.ROWS - 1),
)
