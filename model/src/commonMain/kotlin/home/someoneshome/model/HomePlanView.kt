package home.someoneshome.model

/**
 * **The home's plan as the round holds it: storeys, cells and room names, and nothing else** (O2).
 *
 * [HousePlan] is what the host painted, strokes and all; [HouseMap] is what the host registered,
 * cards and all. This is the narrow thing the rules are handed at arming, so that the house can
 * say which rooms touch which (for O4's plausible error) without ever holding a host's gestures or
 * a single card.
 *
 * ### The type is the field list (rule 3)
 *
 * Storeys in plan order, each with a name, a bounding box and one cell per square; rooms in plan
 * order as a name and a kind. **No card, no terminal, no meeting card, no station, no active
 * marker, no seat** — there is no member here that could hold a [MarkerId], and
 * `HomePlanViewTest` asserts that on the constructors' parameter lists. Which card is in which
 * room is the answer key to every work order (`Destination.kt`); it cannot be expressed in this
 * type, so it cannot leak through it.
 *
 * Cells rather than strokes, as the plan view in `fddd6d0` chose: strokes are the host's gestures,
 * and a cell grid is what any reader of the plan needs.
 *
 * ### ⚠️ It reaches NO client, and it is deliberately not `@Serializable` or `@ClientFacing`
 *
 * D-186 sends the plan to no client, and whether that changes is the owner's question Q1 in the
 * occupancy spec (D-186 against M11/M12). So this type is **not wire-serialisable**: the redaction
 * lint permits `@Serializable` only on `@ClientFacing` types, and marking it client-facing is the
 * ruling Q1 has not made. No `RoundMessage` carries it and no `Effect` names it, so the emit
 * schema has nothing to permit. The unit that answers Q1 adds the marker, the message and the row
 * together, and until then the fail-closed state is the only one this file can express.
 *
 * Recorded and replayed, though (rule 4): it rides `Event.RoundArmed.plan`, and the recording
 * writes it out in full, so a replay knows the same adjacency the evening did.
 */
data class HomePlanView(
    val storeys: List<StoreyView>,
    val rooms: List<RoomView>,
) {
    init {
        require(rooms.map { it.name }.distinct().size == rooms.size) {
            "two rooms share a name; the plan refuses that at painting, so this is not a plan"
        }
        for (storey in storeys) {
            for (cell in storey.cells) {
                require(cell == StoreyView.UNPAINTED || cell in rooms.indices) {
                    "storey '${storey.name}' holds a cell of room $cell, and the plan has ${rooms.size} rooms"
                }
            }
        }
    }

    /** The room called [name], by its index in [rooms], or -1 for a name this plan does not hold. */
    fun indexOf(name: String): Int = rooms.indexOfFirst { it.name == name }

    /**
     * **The rooms sharing an edge with each room, on the same storey**, by index, ascending.
     *
     * [Floor.neighboursOf]'s rule read off the cells: four-neighbourhood, never diagonal, and never
     * between storeys (no vertical connection is inferred anywhere, stairs included). Computed once
     * per plan, which is once per round, because the house asks it on every capture (rule 7).
     */
    val adjacency: List<List<Int>> by lazy {
        val touches = List(rooms.size) { mutableListOf<Int>() }
        fun touch(a: Int, b: Int) {
            if (a == StoreyView.UNPAINTED || b == StoreyView.UNPAINTED || a == b) return
            if (b !in touches[a]) touches[a] += b
            if (a !in touches[b]) touches[b] += a
        }
        for (storey in storeys) {
            for (y in 0 until storey.rows) {
                for (x in 0 until storey.cols) {
                    val here = storey.cellAt(x, y)
                    if (x + 1 < storey.cols) touch(here, storey.cellAt(x + 1, y))
                    if (y + 1 < storey.rows) touch(here, storey.cellAt(x, y + 1))
                }
            }
        }
        touches.map { it.sorted() }
    }

    /** Whether rooms [a] and [b] share an edge on one storey. False for any index the plan lacks. */
    fun adjacent(a: Int, b: Int): Boolean = a in rooms.indices && b in adjacency[a]

    companion object {
        /** No storeys and no rooms: a round armed without a home, which knows no adjacency. */
        val EMPTY = HomePlanView(emptyList(), emptyList())
    }
}

/**
 * **One storey: its name and its squares, row-major inside its bounding box.**
 *
 * [cells] holds one entry per square of a [cols] by [rows] box, row-major: the index of the room
 * painted there in [HomePlanView.rooms], or [UNPAINTED]. The box is the storey's own painted
 * extent, so a host who painted from the middle of the hall outwards gets the same view as one who
 * started in a corner; where on the editor's unbounded grid it was painted is a gesture, and it is
 * not kept.
 */
data class StoreyView(
    val name: String,
    val cols: Int,
    val rows: Int,
    val cells: List<Int>,
) {
    init {
        require(name.isNotEmpty()) { "a storey with no name" }
        require(cols >= 0 && rows >= 0) { "a storey of ${cols}x$rows" }
        require(cells.size == cols * rows) { "storey '$name' is ${cols}x$rows and holds ${cells.size} cells" }
    }

    /** The room index at column [x], row [y], or [UNPAINTED]. */
    fun cellAt(x: Int, y: Int): Int = cells[y * cols + x]

    companion object {
        /** A square nobody painted. */
        const val UNPAINTED: Int = -1
    }
}

/** **One painted area: a name and whether it is a room or stairs.** Its storey is in its cells. */
data class RoomView(val name: String, val kind: RoomKind)

/**
 * **The plan, narrowed for the round** — the one way a [HomePlanView] is built from a home.
 *
 * Storeys in plan order, rooms in plan order (storey by storey), every storey cropped to the box
 * its rooms cover. A storey with nothing painted is kept, as a 0 by 0 box: the host added it, and
 * a view that dropped it would renumber nothing but would say the house has fewer storeys than the
 * host made.
 */
fun HousePlan.toView(): HomePlanView {
    val rooms = rooms
    val views = rooms.map { RoomView(it.name, it.kind) }
    val storeys = floors.map { floor ->
        val painted = floor.rooms.flatMap { room -> room.cells.map { it to rooms.indexOf(room) } }
        if (painted.isEmpty()) {
            StoreyView(floor.name, 0, 0, emptyList())
        } else {
            val minX = painted.minOf { it.first.x }
            val maxX = painted.maxOf { it.first.x }
            val minY = painted.minOf { it.first.y }
            val maxY = painted.maxOf { it.first.y }
            val cols = maxX - minX + 1
            val rows = maxY - minY + 1
            val cells = MutableList(cols * rows) { StoreyView.UNPAINTED }
            for ((cell, index) in painted) cells[(cell.y - minY) * cols + (cell.x - minX)] = index
            StoreyView(floor.name, cols, rows, cells)
        }
    }
    return HomePlanView(storeys, views)
}
