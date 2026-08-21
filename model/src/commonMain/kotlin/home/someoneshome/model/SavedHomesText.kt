package home.someoneshome.model

/** Stored homes that could not be read. Never recovered from — a half-read list is a lost house. */
class MalformedSavedHomes(val line: Int, val detail: String) :
    IllegalArgumentException("saved homes line $line: $detail")

/**
 * **The saved homes' storage format: every home this phone holds, in one file.**
 *
 * Same doctrine as [HouseMapText] and [HousePlanText] and for the same reasons — this outlives the
 * build that wrote it, a host paints a house in March and plays in it in June, and a format the
 * language or a library is free to change is a format that silently loses the setup walk on the
 * evening eight people are already standing in the hall.
 *
 * ### The plan's rows are the plan's, not a second copy of them
 *
 * A home's plan is written by [HousePlanText], header and all, in the middle of this file, and
 * read back by it. Restating the grammar here would be a second description of the same layout,
 * and the one that drifted would be the one nobody had a test looking at. It also keeps the two
 * version numbers answering their own questions: `house-plan/1` is how a plan is laid out,
 * `saved-homes/1` is how a list of homes is.
 *
 * A refusal from inside a plan is re-thrown against the line it really occupies in *this* file,
 * because a host is looking at one file and a line number that counts from somewhere else is a
 * line number that sends them to the wrong row.
 *
 * ### Strict, and loud
 *
 * Every failure throws [MalformedSavedHomes] naming the line. **Nothing is skipped.** A list that
 * comes back one home short is worse than one that fails to load: the host does not re-walk their
 * house, and the missing home only surfaces on the night they went looking for it.
 *
 * ### Only two characters ever need escaping
 *
 * A home name and a room name are typed by a host on a phone and can hold anything. There is no
 * pipe-escaping here because no row ends in a field that could be confused for another: `H` and
 * `T` take the whole rest of the line, and `M` puts the shapes first — they are lowercase words
 * by construction — and splits at the first pipe, so the name after it survives intact whatever
 * is in it. That leaves the backslash and the newline, which would forge a row rather than a
 * field.
 */
object SavedHomesText {

    const val HEADER: String = "someone-is-home/saved-homes/1"

    private const val HOME_ROW = "H "
    private const val MARKERS_ROW = "M "
    private const val TERMINAL_ROW = "T "

    private const val SEPARATOR = '|'
    private const val SHAPE_SEPARATOR = ';'

    /** The rows a plan is made of, so a plan row can be told from one of this format's own. */
    private val PLAN_ROWS = listOf("F ", "R ")

    /**
     * One home after another, in the order the list holds them — which is the order the host sees.
     *
     * The name first, then the plan, then what is registered in it. Cards after the rooms rather
     * than before, so that by the time a room is named as holding something, the room exists.
     */
    fun write(homes: List<SavedHome>): String = buildString {
        appendLine(HEADER)
        for (home in homes) {
            append(HOME_ROW)
            appendLine(escape(home.name))
            append(HousePlanText.write(home.plan))
            for ((room, shapes) in home.markers) {
                if (shapes.isEmpty()) continue
                append(MARKERS_ROW)
                append(shapes.joinToString(SHAPE_SEPARATOR.toString()) { it.id })
                append(SEPARATOR)
                appendLine(escape(room))
            }
            home.terminal?.let {
                append(TERMINAL_ROW)
                appendLine(escape(it))
            }
        }
    }

    fun read(text: String): List<SavedHome> {
        val lines = text.lines().filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw MalformedSavedHomes(0, "empty")
        if (lines[0] != HEADER) {
            throw MalformedSavedHomes(
                1,
                "expected header '$HEADER', got '${lines[0]}'. Homes written by another format " +
                    "version cannot be read under this one.",
            )
        }

        val homes = mutableListOf<SavedHome>()
        var open: OpenHome? = null

        for ((i, line) in lines.withIndex().drop(1)) {
            val number = i + 1
            when {
                line.startsWith(HOME_ROW) -> {
                    val name = unescape(line.substring(HOME_ROW.length), number)
                    if (name.isEmpty()) throw MalformedSavedHomes(number, "a home with no name")
                    if (name == open?.name || homes.any { it.name == name }) {
                        throw MalformedSavedHomes(number, "two homes called '$name'")
                    }
                    open?.let { homes += it.build() }
                    open = OpenHome(name, number)
                }

                line == HousePlanText.HEADER || PLAN_ROWS.any { line.startsWith(it) } ->
                    open.orRefuse(number, "a plan").plan += number to line

                line.startsWith(MARKERS_ROW) -> {
                    val row = line.substring(MARKERS_ROW.length)
                    val split = row.indexOf(SEPARATOR)
                    if (split < 0) throw MalformedSavedHomes(number, "no room on this row")
                    val room = unescape(row.substring(split + 1), number)
                    val shapes = row.substring(0, split).split(SHAPE_SEPARATOR).map { id ->
                        MarkerShapes[id] ?: throw MalformedSavedHomes(
                            number,
                            "'$id' is not a marker shape. The roster is 44 shapes and a card " +
                                "carrying anything else is a card nobody can be sent to.",
                        )
                    }
                    val home = open.orRefuse(number, "cards")
                    if (home.markers.any { it.second == room }) {
                        throw MalformedSavedHomes(
                            number,
                            "'${home.name}' lists the cards in '$room' twice. Refusing rather " +
                                "than keeping one row: the other row's cards are cards the host " +
                                "registered and would never be told about.",
                        )
                    }
                    home.markers += Triple(number, room, shapes)
                }

                line.startsWith(TERMINAL_ROW) -> {
                    val home = open.orRefuse(number, "a terminal")
                    if (home.terminal != null) {
                        throw MalformedSavedHomes(
                            number,
                            "'${home.name}' has two terminals. One home, one terminal — a second " +
                                "gives the house two places to be found.",
                        )
                    }
                    home.terminal = number to unescape(line.substring(TERMINAL_ROW.length), number)
                }

                else -> throw MalformedSavedHomes(
                    number,
                    "unknown row '$line'. Refusing rather than skipping: a list that comes back " +
                        "one home short is fifteen minutes of walking nobody knows is missing.",
                )
            }
        }
        open?.let { homes += it.build() }
        return homes
    }

    /** A row that belongs to a home, with no home open, names nothing. */
    private fun OpenHome?.orRefuse(line: Int, what: String): OpenHome = this ?: throw
        MalformedSavedHomes(line, "$what before any home. Every plan belongs to one.")

    /**
     * A home being read: its rows, and the line each of them came from.
     *
     * The line numbers are carried rather than recomputed because a refusal names one, and the
     * only honest number is the row's own position in the file the host is looking at.
     */
    private class OpenHome(val name: String, val line: Int) {
        val plan = mutableListOf<Pair<Int, String>>()
        val markers = mutableListOf<Triple<Int, String, List<MarkerShape>>>()
        var terminal: Pair<Int, String>? = null

        fun build(): SavedHome {
            if (plan.isEmpty()) {
                throw MalformedSavedHomes(
                    line,
                    "'$name' has no plan. A home is the house somebody walked; without one there " +
                        "is nothing to host in.",
                )
            }
            val house = readPlan()
            for ((row, room, _) in markers) requirePainted(house, room, row, "cards are registered in")
            terminal?.let { (row, room) -> requirePainted(house, room, row, "the terminal is in") }
            return SavedHome(
                name = name,
                plan = house,
                markers = markers.associate { (_, room, shapes) -> room to shapes },
                terminal = terminal?.second,
            )
        }

        /**
         * The plan's own reader, against the plan's own rows — and its refusal, re-numbered.
         *
         * [HousePlanText] counts lines from the top of what it was handed, which here is the
         * middle of a longer file. The row it objected to is the one it counted to, so the line
         * reported is that row's real position.
         */
        private fun readPlan(): HousePlan = try {
            HousePlanText.read(plan.joinToString("\n") { it.second })
        } catch (e: MalformedHousePlan) {
            throw MalformedSavedHomes(plan.getOrNull(e.line - 1)?.first ?: line, e.detail)
        }

        private fun requirePainted(plan: HousePlan, room: String, row: Int, what: String) {
            val painted = plan.roomNamed(room)
                ?: throw MalformedSavedHomes(row, "$what '$room', which is not a room in '$name'")
            if (painted.kind == RoomKind.Stairs) {
                throw MalformedSavedHomes(
                    row,
                    "$what '$room', which is stairs — stairs hold nothing (D-099)",
                )
            }
        }
    }

    /**
     * Backslash and newline only — see the class note on why no pipe is escaped here.
     *
     * A name holding a newline would otherwise become two rows, and the second would be refused as
     * an unknown row on a file that was perfectly good when it was written.
     */
    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\n", "\\n")

    /** Left to right, so `\\n` is a backslash followed by n and not an escaped newline. */
    private fun unescape(s: String, line: Int): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') { out.append(c); i++; continue }
            if (i + 1 >= s.length) throw MalformedSavedHomes(line, "a name ends in a dangling escape")
            when (val next = s[i + 1]) {
                '\\' -> out.append('\\')
                'n' -> out.append('\n')
                else -> throw MalformedSavedHomes(line, "unknown escape '\\$next' in a name")
            }
            i += 2
        }
        return out.toString()
    }
}
