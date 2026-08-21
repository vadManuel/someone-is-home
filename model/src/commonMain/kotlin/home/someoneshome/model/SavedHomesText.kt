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
 * ### The plan's rows are the plan's, and the map's are the map's
 *
 * A home's plan is written by [HousePlanText] and its cards by [HouseMapText], headers and all, in
 * the middle of this file, and read back by them. Restating either grammar here would be a second
 * description of the same layout, and the one that drifted would be the one nobody had a test
 * looking at. It also keeps the three version numbers answering their own questions:
 * `house-plan/1` is how a plan is laid out, `house-map/3` is how registered cards are, and
 * `saved-homes/3` is how a list of homes is.
 *
 * **Both embedded formats use an `R` row and they do not mean the same thing** — a painted room in
 * one, a registered card in the other. A row belongs to whichever section is open, and a section
 * opens at its own header, which is why the headers are written out rather than stripped.
 *
 * A refusal from inside a plan or a map is re-thrown against the line it really occupies in *this*
 * file, because a host is looking at one file and a line number that counts from somewhere else is
 * a line number that sends them to the wrong row.
 *
 * ### Strict, and loud
 *
 * Every failure throws [MalformedSavedHomes] naming the line. **Nothing is skipped.** A list that
 * comes back one home short is worse than one that fails to load: the host does not re-walk their
 * house, and the missing home only surfaces on the night they went looking for it.
 *
 * ### Only two characters ever need escaping
 *
 * A home name is typed by a host on a phone and can hold anything. There is no pipe-escaping on
 * this format's own row because `H` takes the whole rest of the line. That leaves the backslash
 * and the newline, which would forge a row rather than a field. The embedded formats escape their
 * own room names, which is the one place a pipe matters.
 */
object SavedHomesText {

    /**
     * Version 3: the home has a meeting card.
     *
     * Version 1 stored a room's contents as a list of shape ids and the terminal as a room name,
     * because registration had no camera behind it and there were no printed ids to write down.
     * A v1 file describes markers that cannot be scanned, and there is nothing honest to turn one
     * into — a fabricated id is a card the host does not have.
     *
     * Version 2 fixed that and knew one reserved card. **A v2 home has no meeting card**, and D-127
     * makes one a condition of hosting, so a v2 file read under this version would come back as a
     * home that passes nothing — silently short of the one card the host has never printed. There
     * is no honest way to invent it either: the meeting card is a place in a real house and only
     * the host knows where.
     *
     * **Both are refused rather than migrated, and each refusal names what changed.** Loud, at the
     * moment the file is opened, in the light.
     */
    const val HEADER: String = "someone-is-home/saved-homes/3"

    /** What the older headers say, so a refusal can name what changed rather than shrug. */
    private const val HEADER_V1 = "someone-is-home/saved-homes/1"
    private const val HEADER_V2 = "someone-is-home/saved-homes/2"

    private const val HOME_ROW = "H "

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
            append(HouseMapText.write(home.map))
        }
    }

    fun read(text: String): List<SavedHome> {
        val lines = text.lines().filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw MalformedSavedHomes(0, "empty")
        if (lines[0] != HEADER) {
            val was = lines[0]
            val detail = when (was) {
                HEADER_V1 ->
                    "these homes were saved before markers carried the id printed on the card. " +
                        "There is no honest way to read them under '$HEADER' — an invented id " +
                        "is a card the host does not have — so they are refused rather than " +
                        "half-read."

                HEADER_V2 ->
                    "these homes were saved before the meeting card. There is no honest way to " +
                        "read them under '$HEADER' — where a meeting is called is a place in a " +
                        "real house and only the host knows it — so they are refused rather " +
                        "than read back as homes that cannot be hosted."

                else ->
                    "expected header '$HEADER', got '$was'. Homes written by another format " +
                        "version cannot be read under this one."
            }
            throw MalformedSavedHomes(1, detail)
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

                line == HousePlanText.HEADER -> open.orRefuse(number, "a plan").openPlan(number, line)

                line == HouseMapText.HEADER -> open.orRefuse(number, "cards").openMap(number, line)

                // Everything else belongs to whichever section is open. Both embedded formats
                // spell a row `R `, so which one this is cannot be read off the row — only off
                // the header above it.
                else -> open.orRefuse(number, "a row").section(number, line)
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
        val map = mutableListOf<Pair<Int, String>>()

        /** The section rows are currently landing in, or none before the first header. */
        private var section: MutableList<Pair<Int, String>>? = null

        fun openPlan(number: Int, header: String) {
            if (plan.isNotEmpty()) {
                throw MalformedSavedHomes(number, "'$name' has two plans. A home is one house.")
            }
            section = plan
            plan += number to header
        }

        fun openMap(number: Int, header: String) {
            if (map.isNotEmpty()) {
                throw MalformedSavedHomes(
                    number,
                    "'$name' lists its cards twice. Refusing rather than keeping one list: the " +
                        "other one's cards are cards the host registered and would never be told " +
                        "about.",
                )
            }
            section = map
            map += number to header
        }

        fun section(number: Int, line: String) {
            val open = section ?: throw MalformedSavedHomes(
                number,
                "row '$line' belongs to no section. A plan row and a card row are both spelled " +
                    "'R' and are told apart only by the header above them.",
            )
            open += number to line
        }

        fun build(): SavedHome {
            if (plan.isEmpty()) {
                throw MalformedSavedHomes(
                    line,
                    "'$name' has no plan. A home is the house somebody walked; without one there " +
                        "is nothing to host in.",
                )
            }
            // The plan is read before the card list is even looked for, so that a file which is
            // both malformed and truncated is refused for the row a host could go and fix rather
            // than for the section that is missing behind it.
            val house = readPlan()
            if (map.isEmpty()) {
                throw MalformedSavedHomes(
                    line,
                    "'$name' has no card list. A home with nothing registered writes an empty " +
                        "one; a home missing it altogether is a file that lost rows.",
                )
            }
            val cards = readMap()
            for (registration in cards.registrations) {
                requirePainted(house, registration, "cards are registered in")
            }
            cards.terminal?.let { requirePainted(house, it, "the terminal is in") }
            cards.meeting?.let { requirePainted(house, it, "the meeting card is in") }
            return SavedHome(name = name, plan = house, map = cards)
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

        /** The map's own reader, re-numbered the same way and for the same reason. */
        private fun readMap(): HouseMap = try {
            HouseMapText.read(map.joinToString("\n") { it.second })
        } catch (e: MalformedHouseMap) {
            throw MalformedSavedHomes(map.getOrNull(e.line - 1)?.first ?: line, e.detail)
        }

        /**
         * A room a card names has to be a room in this home, and it cannot be stairs.
         *
         * The map holds room names and the plan holds what a room *is*, so this is the one place
         * the two are made to agree. The line reported is the card's own row, because that is the
         * row the host would have to change.
         */
        private fun requirePainted(plan: HousePlan, registration: Registration, what: String) {
            val room = registration.room.name
            val row = map.firstOrNull { it.second.contains(CardPayload.encode(registration.card)) }
                ?.first ?: line
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
