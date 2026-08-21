package home.someoneshome.model

/** A stored plan that could not be read. Never recovered from — a half-read plan is a wrong house. */
class MalformedHousePlan(val line: Int, val detail: String) :
    IllegalArgumentException("house plan line $line: $detail")

/**
 * The house plan's storage format. Same doctrine as [HouseMapText], for the same reasons.
 *
 * ### Why a text format rather than a serialiser
 *
 * This outlives the build that wrote it. A host paints their house in March and plays in it in
 * June, across app updates. A format the language or a library is free to change is a format that
 * silently loses the setup walk — and loses it at the start of an evening, with eight people
 * already there. The stored form is also the **export/share** form, so handing a house to another
 * host is handing them the file.
 *
 * ### Strict, and loud
 *
 * Every failure throws [MalformedHousePlan] naming the line. **Nothing is skipped.** A plan that
 * comes back one room short is worse than one that fails to load: the host does not re-walk their
 * house, and the missing room only surfaces when a player is standing in a place the app has
 * never heard of.
 *
 * ### The reader refuses what the model refuses
 *
 * Duplicate names and overlapping cells are checked here, row by row, because a reader can name
 * the line a host has to look at. [HousePlan.of] checks the same things without a line number, as
 * the last-ditch guarantee for every other way a plan gets built.
 *
 * ### The version is checked, not tolerated
 *
 * A plan written by another format version cannot be read under this one. Guessing at a layout
 * change is how a wall ends up somewhere nobody drew it.
 */
object HousePlanText {

    const val HEADER: String = "someone-is-home/house-plan/1"

    private const val SEPARATOR = '|'
    private const val ESCAPED_SEPARATOR = "\\p"
    private const val STROKE_SEPARATOR = ';'
    private const val EXTENT_SEPARATOR = ','

    private const val FLOOR_ROW = "F "
    private const val ROOM_ROW = "R "

    private const val ROOM_TOKEN = "room"
    private const val STAIRS_TOKEN = "stairs"

    /**
     * A floor row, then its rooms, in the order the host painted them.
     *
     * Rooms are written under the floor they sit on rather than carrying a floor name of their
     * own, because a room that names its own storey can name one that is not in the file — and
     * the reader would then have to decide what to do with it. It cannot arise if it cannot be
     * written.
     */
    fun write(plan: HousePlan): String = buildString {
        appendLine(HEADER)
        for (floor in plan.floors) {
            append(FLOOR_ROW)
            appendLine(escape(floor.name))
            for (room in floor.rooms) {
                append(ROOM_ROW)
                append(token(room.kind))
                append(SEPARATOR)
                append(strokes(room.strokes))
                append(SEPARATOR)
                appendLine(escape(room.name))
            }
        }
    }

    fun read(text: String): HousePlan {
        val lines = text.lines().filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw MalformedHousePlan(0, "empty")
        if (lines[0] != HEADER) {
            throw MalformedHousePlan(
                1,
                "expected header '$HEADER', got '${lines[0]}'. A plan written by another format " +
                    "version cannot be read under this one.",
            )
        }

        val floors = mutableListOf<Floor>()

        // The storey being read, name and rooms together in one nullable, rather than a name and
        // a list kept in step by hand. The first version held the name in a `var` starting at "",
        // and that sentinel is *also* what a nameless floor row parses to — so `F ` was refused
        // as "two floors called ''" by the duplicate check before the empty-name check could
        // speak. Right answer, wrong reason, and the wrong reason is what a host would read.
        var open: Pair<String, MutableList<PlanRoom>>? = null

        for ((i, line) in lines.withIndex().drop(1)) {
            val number = i + 1
            when {
                line.startsWith(FLOOR_ROW) -> {
                    val name = unescape(line.substring(FLOOR_ROW.length), number)
                    if (name.isEmpty()) throw MalformedHousePlan(number, "a floor with no name")
                    // The open storey as well as the flushed ones: it has not been added to
                    // `floors` yet, so checking only that list lets a file naming the same floor
                    // twice in a row through to HousePlan.of, which refuses it without a line
                    // number a host can look at.
                    if (name == open?.first || floors.any { it.name == name }) {
                        throw MalformedHousePlan(number, "two floors called '$name'")
                    }
                    open?.let { floors += Floor(it.first, it.second.toList()) }
                    open = name to mutableListOf()
                }

                line.startsWith(ROOM_ROW) -> {
                    val (floorName, rooms) = open
                        ?: throw MalformedHousePlan(
                            number,
                            "a room before any floor. Every room is painted on a storey; one " +
                                "that is not says nothing about where it is.",
                        )
                    rooms += room(line.substring(ROOM_ROW.length), number, floors, rooms, floorName)
                }

                else -> throw MalformedHousePlan(
                    number,
                    "unknown row '$line'. Refusing rather than skipping: a plan that comes back " +
                        "one room short is a room nobody knows is missing.",
                )
            }
        }
        open?.let { floors += Floor(it.first, it.second.toList()) }
        return HousePlan.of(floors)
    }

    private fun room(
        row: String,
        number: Int,
        floors: List<Floor>,
        siblings: List<PlanRoom>,
        floorName: String,
    ): PlanRoom {
        val fields = row.split(SEPARATOR)
        if (fields.size != 3) {
            throw MalformedHousePlan(
                number,
                "a room row is kind|cells|name, and this one has ${fields.size} field(s)",
            )
        }

        val kind = when (fields[0]) {
            ROOM_TOKEN -> RoomKind.Room
            STAIRS_TOKEN -> RoomKind.Stairs
            else -> throw MalformedHousePlan(
                number,
                "unknown room kind '${fields[0]}'. The map knows '$ROOM_TOKEN' and " +
                    "'$STAIRS_TOKEN' and nothing else (D-098).",
            )
        }

        val name = unescape(fields[2], number)
        if (name.isEmpty()) throw MalformedHousePlan(number, "a room with no name")
        val already = floors.flatMap { it.rooms }.plus(siblings).firstOrNull { it.name == name }
        if (already != null) throw MalformedHousePlan(number, "two rooms called '$name'")

        val painted = PlanRoom(Room(name, kind), strokes(fields[1], number, name))
        for (other in siblings) {
            val shared = painted.cells.filter { other.covers(it) }
            if (shared.isNotEmpty()) {
                val cell = shared.first()
                throw MalformedHousePlan(
                    number,
                    "'$name' and '${other.name}' both hold the cell (${cell.x}, ${cell.y}) on " +
                        "'$floorName'",
                )
            }
        }
        return painted
    }

    private fun strokes(strokes: List<CellRect>): String =
        strokes.joinToString(STROKE_SEPARATOR.toString()) {
            "${it.x}$EXTENT_SEPARATOR${it.y}$EXTENT_SEPARATOR${it.width}$EXTENT_SEPARATOR${it.height}"
        }

    private fun strokes(field: String, number: Int, name: String): List<CellRect> {
        if (field.isEmpty()) throw MalformedHousePlan(number, "'$name' is a room with no cells")
        return field.split(STROKE_SEPARATOR).map { stroke ->
            val extents = stroke.split(EXTENT_SEPARATOR)
            if (extents.size != 4) {
                throw MalformedHousePlan(
                    number,
                    "'$stroke' is not a painted stroke — four numbers are needed, x,y,width," +
                        "height, and this has ${extents.size}",
                )
            }
            val numbers = extents.map {
                it.toIntOrNull()
                    ?: throw MalformedHousePlan(number, "'$it' in '$stroke' is not a whole number")
            }
            val (x, y, width, height) = numbers
            if (width <= 0 || height <= 0) {
                throw MalformedHousePlan(
                    number,
                    "the stroke '$stroke' covers no cells (${width}x${height})",
                )
            }
            if (width > HousePlan.LIMIT || height > HousePlan.LIMIT ||
                x !in -HousePlan.LIMIT..HousePlan.LIMIT || y !in -HousePlan.LIMIT..HousePlan.LIMIT
            ) {
                throw MalformedHousePlan(
                    number,
                    "the stroke '$stroke' is past the ${HousePlan.LIMIT}-cell limit. A house is " +
                        "not this big; a corrupted file is.",
                )
            }
            CellRect(x, y, width, height)
        }
    }

    private fun token(kind: RoomKind): String = when (kind) {
        RoomKind.Room -> ROOM_TOKEN
        RoomKind.Stairs -> STAIRS_TOKEN
    }

    /**
     * A room name is typed by a host on a phone and can contain anything.
     *
     * The separator must not survive inside one, or a room called `KITCHEN|room|0,0,1,1` forges a
     * row — the same hazard [HouseMapText] handles the same way. Kind tokens and strokes never
     * need escaping: they are a fixed word and four integers by construction.
     */
    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace(SEPARATOR.toString(), ESCAPED_SEPARATOR).replace("\n", "\\n")

    /** Left to right, so `\\p` is a backslash followed by p and not an escaped separator. */
    private fun unescape(s: String, line: Int): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') { out.append(c); i++; continue }
            if (i + 1 >= s.length) throw MalformedHousePlan(line, "a name ends in a dangling escape")
            when (val next = s[i + 1]) {
                '\\' -> out.append('\\')
                'p' -> out.append(SEPARATOR)
                'n' -> out.append('\n')
                else -> throw MalformedHousePlan(line, "unknown escape '\\$next' in a name")
            }
            i += 2
        }
        return out.toString()
    }
}
