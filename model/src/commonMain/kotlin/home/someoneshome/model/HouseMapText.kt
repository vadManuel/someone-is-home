package home.someoneshome.model

/** A stored map that could not be read. Never recovered from — a half-read map is a wrong house. */
class MalformedHouseMap(val line: Int, val detail: String) :
    IllegalArgumentException("house map line $line: $detail")

/**
 * The house map's storage format.
 *
 * ### Why a text format rather than a serialiser
 *
 * Same reason [MarkerShapes] is generated data with a test on it: this outlives the build that
 * wrote it. A host sets a house up in March and plays in it in June, across app updates. A
 * format the language or a library is free to change is a format that silently loses fifteen
 * minutes of walking around in the dark — and loses it at the start of an evening, with eight
 * people already there.
 *
 * ### Strict, and loud
 *
 * Every failure throws [MalformedHouseMap] naming the line. Nothing is skipped. A map that comes
 * back one registration short is worse than one that fails to load: the host does not recount
 * their cards, and the missing marker only surfaces when a player stands at it and the app says
 * nothing of theirs opens there.
 *
 * ### The version is checked, not tolerated
 *
 * A card's payload carries its own version, and so does the file. They are different questions:
 * one is what is printed on paper, the other is how this file is laid out.
 */
object HouseMapText {

    /**
     * Version 2 carries the terminal.
     *
     * Version 1 had no `T` row, because the terminal was a room name held somewhere else. A v1
     * reader handed a v2 file would refuse the row it has never heard of, which is the correct
     * failure and the reason this is a version and not a quiet addition.
     */
    const val HEADER: String = "someone-is-home/house-map/2"

    private const val REGISTRATION_ROW = "R "
    private const val TERMINAL_ROW = "T "

    private const val SEPARATOR = '|'
    private const val ESCAPED_SEPARATOR = "\\p"

    /**
     * One line per registration: the printed payload, then the room. The terminal last, as `T`.
     *
     * The payload is stored rather than the decoded fields, so what is written is exactly what is
     * printed on the card. A file holding decoded fields could disagree with the paper, and the
     * paper is the thing that cannot be edited.
     */
    fun write(map: HouseMap): String = buildString {
        appendLine(HEADER)
        for (registration in map.registrations) row(REGISTRATION_ROW, registration)
        map.terminal?.let { row(TERMINAL_ROW, it) }
    }

    private fun StringBuilder.row(kind: String, registration: Registration) {
        append(kind)
        append(CardPayload.encode(registration.card))
        append(SEPARATOR)
        appendLine(escape(registration.room.name))
    }

    fun read(text: String): HouseMap {
        val lines = text.lines().filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw MalformedHouseMap(0, "empty")
        if (lines[0] != HEADER) {
            throw MalformedHouseMap(
                1,
                "expected header '$HEADER', got '${lines[0]}'. A map written by another format " +
                    "version cannot be read under this one.",
            )
        }

        val registrations = mutableListOf<Registration>()
        var terminal: Registration? = null
        for ((i, line) in lines.withIndex().drop(1)) {
            val number = i + 1
            val kind = when {
                line.startsWith(REGISTRATION_ROW) -> REGISTRATION_ROW
                line.startsWith(TERMINAL_ROW) -> TERMINAL_ROW
                else -> throw MalformedHouseMap(
                    number,
                    "unknown row '$line'. Refusing rather than skipping: a map that comes back " +
                        "one registration short is a marker nobody knows is missing.",
                )
            }
            val row = line.substring(kind.length)
            val split = row.indexOf(SEPARATOR)
            if (split < 0) throw MalformedHouseMap(number, "no room on this row")

            val payload = row.substring(0, split)
            val room = Room(unescape(row.substring(split + 1), number))
            if (room.name.isEmpty()) throw MalformedHouseMap(number, "a room with no name")

            val registration = when (val result = CardPayload.decode(payload)) {
                is CardPayload.Result.Rejected ->
                    throw MalformedHouseMap(number, "card payload '$payload' rejected: ${result.why}")
                is CardPayload.Result.Read -> {
                    val taken = registrations.any { it.card.id == result.card.id } ||
                        terminal?.card?.id == result.card.id
                    if (taken) {
                        throw MalformedHouseMap(number, "card ${result.card.id.value} appears twice")
                    }
                    Registration(result.card, room)
                }
            }

            if (kind == TERMINAL_ROW) {
                if (terminal != null) {
                    throw MalformedHouseMap(
                        number,
                        "a second terminal. One home, one terminal — a second gives the house " +
                            "two places to be found.",
                    )
                }
                if (!registration.card.isTerminal) {
                    throw MalformedHouseMap(
                        number,
                        "'$payload' is the terminal row but is not the card marked T",
                    )
                }
                terminal = registration
            } else {
                if (registration.card.isTerminal) {
                    throw MalformedHouseMap(
                        number,
                        "'$payload' is the card marked T, registered as an ordinary marker — it " +
                            "never is",
                    )
                }
                registrations += registration
            }
        }
        return HouseMap.of(registrations, terminal)
    }

    /**
     * A room name is typed by a host on a phone and can contain anything.
     *
     * The separator must not survive inside one, or a room called `KITCHEN|GARAGE` forges a row —
     * the same hazard [MarkerId] has in a recording, and handled the same way. A payload never
     * needs escaping: it is nine characters of QR alphanumeric by construction.
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
            if (i + 1 >= s.length) throw MalformedHouseMap(line, "a room name ends in a dangling escape")
            when (val next = s[i + 1]) {
                '\\' -> out.append('\\')
                'p' -> out.append(SEPARATOR)
                'n' -> out.append('\n')
                else -> throw MalformedHouseMap(line, "unknown escape '\\$next' in a room name")
            }
            i += 2
        }
        return out.toString()
    }
}
