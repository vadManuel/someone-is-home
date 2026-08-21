package home.someoneshome.model

/**
 * One square of a floor's grid.
 *
 * The grid is unbounded and additive in every direction — a host who starts painting in the
 * middle of the hall and works outwards must not hit an origin. Negative coordinates are
 * ordinary, [HousePlan.LIMIT] is the only bound, and it exists to stop a corrupted file rather
 * than to shape a house.
 */
data class Cell(val x: Int, val y: Int)

/**
 * One drag of the grid painter: a rectangular block of cells.
 *
 * **A room is a list of these, not one of them, and that is the whole reason the grid exists.**
 * The design chose a grid over free rectangles on three counts, and the second is that *L-shaped
 * rooms just work, because real houses have them and rectangles cannot express them*. A room
 * modelled as a single rect would quietly delete that — an L-shaped kitchen becomes two rooms
 * with two names, and a player told to go to the kitchen has two places to stand. The stroke is
 * rectangular because a drag is; the room is the union.
 *
 * The other two counts are paid here as well: adjacency falls out of cell neighbours
 * ([Floor.neighboursOf]) with no geometry, and there are no resize handles, overlap rules or
 * snapping to build.
 */
data class CellRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) {
            "a painted stroke covers no cells (${width}x${height}) — a drag that selected nothing"
        }
        require(width <= HousePlan.LIMIT && height <= HousePlan.LIMIT) {
            "a painted stroke of ${width}x${height} is past the ${HousePlan.LIMIT}-cell limit"
        }
        require(x in -HousePlan.LIMIT..HousePlan.LIMIT && y in -HousePlan.LIMIT..HousePlan.LIMIT) {
            "a painted stroke at ($x, $y) is off the grid"
        }
    }

    /** Row-major, so what comes out of a stroke is the same list every time it is asked. */
    val cells: List<Cell>
        get() = buildList {
            for (dy in 0 until height) for (dx in 0 until width) add(Cell(x + dx, y + dy))
        }

    fun contains(cell: Cell): Boolean =
        cell.x >= x && cell.x < x + width && cell.y >= y && cell.y < y + height
}

/**
 * A painted room: the [Room] the rest of the house already knows, plus where it is.
 *
 * **Composed rather than restated.** `Room` is name and [RoomKind] and is what a [Registration]
 * binds a card to; the plan adds geometry and nothing else. Keeping one `Room` type means a room
 * the host painted and a room a card is registered into cannot drift into two spellings of the
 * same place.
 */
data class PlanRoom(val room: Room, val strokes: List<CellRect>) {
    init {
        require(strokes.isNotEmpty()) { "'${room.name}' is a room with no cells" }
        require(room.name.isNotEmpty()) { "a room with no name" }
    }

    val name: String get() = room.name
    val kind: RoomKind get() = room.kind

    /** Every cell this room covers, once each, in stroke order. */
    val cells: List<Cell> get() = strokes.flatMap { it.cells }.distinct()

    fun covers(cell: Cell): Boolean = strokes.any { it.contains(cell) }
}

/**
 * One storey, named by the host.
 *
 * ### There is no floor number, on purpose
 *
 * Floors are *purely additive*: the host starts with one and adds more as they go, and the design
 * states there is **no vertical-connection logic** — the app renders what was drawn. A floor
 * carrying an index invites exactly the code that was refused, because an index is an ordering
 * and an ordering is one short step from "the stairs on floor 0 lead to floor 1". Position in
 * [HousePlan.floors] is the only order there is, and it means nothing but the order they were
 * added in.
 */
data class Floor(val name: String, val rooms: List<PlanRoom> = emptyList()) {
    init {
        require(name.isNotEmpty()) { "a floor with no name" }
    }

    fun roomAt(cell: Cell): PlanRoom? = rooms.firstOrNull { it.covers(cell) }

    fun roomNamed(name: String): PlanRoom? = rooms.firstOrNull { it.name == name }

    /** How many of this storey's painted areas are rooms. See [HousePlan.roomCount]. */
    val roomCount: Int get() = rooms.count { it.kind == RoomKind.Room }

    /**
     * The rooms sharing an edge with this one — **derived from cell neighbours, with no geometry**.
     *
     * That is the grid's first argument for existing, and E4's acceptance criterion: adjacency is
     * queryable without a line of geometry code. Four-neighbourhood, not eight: two rooms meeting
     * at a single corner share no doorway, and a diagonal neighbour would report a route through a
     * wall.
     *
     * **Within this floor only.** No vertical connection is inferred anywhere, stairs included —
     * the stairwell is drawn, never wired.
     *
     * Returned in floor order so the answer is the same every time, which matters because what
     * consumes adjacency is seeded and replayed.
     */
    fun neighboursOf(room: PlanRoom): List<PlanRoom> {
        val touched = HashSet<Cell>()
        for (cell in room.cells) {
            touched += Cell(cell.x - 1, cell.y)
            touched += Cell(cell.x + 1, cell.y)
            touched += Cell(cell.x, cell.y - 1)
            touched += Cell(cell.x, cell.y + 1)
        }
        return rooms.filter { it.name != room.name && it.cells.any { cell -> cell in touched } }
    }
}

/** What happened when a room was offered to the plan. */
sealed interface PaintResult {
    data class Painted(val plan: HousePlan) : PaintResult

    /** No floor by that name. The host is painting onto a storey they have not added. */
    data class NoSuchFloor(val name: String) : PaintResult

    /**
     * The stroke lands on cells another room already holds.
     *
     * Refused rather than repainted: a cell belonging to two rooms is a house that cannot say
     * where a player is standing, and the wrong-room report that follows is indistinguishable
     * from the error the Terminal injects on purpose. Refused in the host's hands, in the light,
     * with the offending cells named so the painter can show them.
     */
    data class CellsAlreadyPainted(val by: PlanRoom, val cells: List<Cell>) : PaintResult

    /**
     * Another room in this house already carries this name.
     *
     * **Across the whole plan, not just this floor.** A card is registered to a `Room`, which is a
     * name and a kind — so two rooms called BATHROOM on different storeys give a registration two
     * places it could mean, and the host who painted them is the only person who would ever know.
     * The refusal costs a host one rename during setup; the alternative costs someone a round for
     * a reason that never surfaces.
     */
    data class NameAlreadyUsed(val by: PlanRoom) : PaintResult
}

/**
 * **Story 4.1–4.3 — the painted house. What the host drew, and nothing else.**
 *
 * The setup walk has two halves. [HouseMap] is the half that binds cards to rooms; this is the
 * half that says what the rooms *are* — one grid plan per storey, rooms painted as unions of
 * rectangular drags, named and tagged room or stairs.
 *
 * ### Pure data, host-side, out of the loop
 *
 * Nothing here is read during a round. It is drawn in the light, weeks before, and stored. It
 * emits no [Event], answers no game question, and — like [HouseMap] — is **not `@Serializable`**:
 * it is authority-side setup data and reaches a client only as whatever narrower view a screen
 * needs, constructed for the purpose.
 *
 * ### Ordered, because it is written down
 *
 * Floors, rooms and strokes are lists in the order the host made them. Iteration order of a
 * hashed collection varies between runs, and this serialises into something a later build reads
 * back and compares byte for byte.
 *
 * ### Loud, because this is setup
 *
 * Rule 6 keeps errors away from a *player* mid-round. A host painting a house is not mid-round:
 * [paint] refuses politely so the editor can say why while the finger is still on the screen, and
 * [of] `require`s as the last-ditch guarantee for every other path — storage included. Same shape
 * as [HouseMap.register] and [Registration]'s `init` (D-099).
 */
class HousePlan private constructor(val floors: List<Floor>) {

    val rooms: List<PlanRoom> get() = floors.flatMap { it.rooms }

    /**
     * **How many rooms this home has — which is not how many areas were painted.**
     *
     * A stairwell is painted like a room and is not one: it holds nothing, structurally (D-099),
     * and it is not the Insider's route between rooms either, because that is Override and
     * Override is never drawn on a map (D-098). The editor read `rooms.size` and told a host their
     * ground floor had `6 ROOMS`, one of which was STAIRS.
     *
     * `rooms` itself is deliberately unfiltered: every structural question — which room covers
     * this cell, which storey is this room on, is this name taken — has to see the stairwell, and
     * a collection that quietly omitted it would be a stairwell you could paint a room on top of.
     * The filtering belongs to the count, because the count is the only thing that was ever
     * claiming these were all rooms.
     */
    val roomCount: Int get() = floors.sumOf { it.roomCount }

    fun floorNamed(name: String): Floor? = floors.firstOrNull { it.name == name }

    fun roomNamed(name: String): PlanRoom? = rooms.firstOrNull { it.name == name }

    /** The floor a room was painted on. Nothing else knows; a room does not carry its storey. */
    fun floorOf(room: PlanRoom): Floor? = floors.firstOrNull { it.roomNamed(room.name) != null }

    /** Add a storey. Additive is the whole model — nothing is fixed and nothing is numbered. */
    fun withFloor(name: String): HousePlan {
        require(floorNamed(name) == null) { "a floor called '$name' is already in this house" }
        return HousePlan(floors + Floor(name))
    }

    /**
     * Paint a room onto a floor.
     *
     * Repainting a room the plan already holds under the same name **replaces its cells**, which
     * is a host correcting a drag they got wrong — the same allowance [HouseMap.register] makes
     * for a card re-registered into a different room. It must be repainted onto the floor it is
     * already on; a room does not move between storeys, it is deleted and drawn again.
     */
    fun paint(floorName: String, room: PlanRoom): PaintResult {
        val floor = floorNamed(floorName) ?: return PaintResult.NoSuchFloor(floorName)

        val elsewhere = rooms.firstOrNull { it.name == room.name }
        if (elsewhere != null && floor.roomNamed(room.name) == null) {
            return PaintResult.NameAlreadyUsed(elsewhere)
        }

        val others = floor.rooms.filterNot { it.name == room.name }
        for (other in others) {
            val shared = room.cells.filter { other.covers(it) }
            if (shared.isNotEmpty()) return PaintResult.CellsAlreadyPainted(other, shared)
        }

        val nextRooms = if (floor.roomNamed(room.name) == null) floor.rooms + room
        else floor.rooms.map { if (it.name == room.name) room else it }
        return PaintResult.Painted(
            HousePlan(floors.map { if (it.name == floor.name) Floor(it.name, nextRooms) else it })
        )
    }

    /** Forget a room. The host repainted a wall, or a room went out of play. */
    fun forget(name: String): HousePlan =
        HousePlan(floors.map { Floor(it.name, it.rooms.filterNot { room -> room.name == name }) })

    companion object {
        val EMPTY = HousePlan(emptyList())

        /**
         * A sanity bound on the grid, not a statement about houses.
         *
         * A file claiming a stroke 2 000 000 cells wide is a corrupted file, and expanding it to
         * cells before finding that out is how a host's phone runs out of memory during setup.
         * 256 is roughly six times the widest plan anyone would paint by hand.
         */
        const val LIMIT: Int = 256

        /**
         * Rebuild a plan. Order is preserved, because order is what was written.
         *
         * The `require`s here are the last-ditch guarantee behind [paint]'s polite refusals — the
         * reader, the editor and any future importer all arrive through this door.
         */
        fun of(floors: List<Floor>): HousePlan {
            conflictIn(floors)?.let { throw IllegalArgumentException(it) }
            return HousePlan(floors.toList())
        }

        /**
         * The first thing wrong with a set of floors, said in words, or null.
         *
         * Shared so [of] and [HousePlanText] refuse the same things — the reader checks row by
         * row because it can name a line, and this catches every other way a plan is assembled.
         */
        internal fun conflictIn(floors: List<Floor>): String? {
            val floorNames = mutableSetOf<String>()
            val roomNames = mutableSetOf<String>()
            for (floor in floors) {
                if (!floorNames.add(floor.name)) return "two floors called '${floor.name}'"
                val owner = HashMap<Cell, PlanRoom>()
                for (room in floor.rooms) {
                    if (!roomNames.add(room.name)) return "two rooms called '${room.name}'"
                    for (cell in room.cells) {
                        val already = owner.put(cell, room)
                        if (already != null) {
                            return "'${room.name}' and '${already.name}' both hold the cell " +
                                "(${cell.x}, ${cell.y}) on '${floor.name}'"
                        }
                    }
                }
            }
            return null
        }
    }
}
