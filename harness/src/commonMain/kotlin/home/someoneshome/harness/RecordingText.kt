package home.someoneshome.harness

import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick

/** A recording that could not be read. Never recovered from — a half-read recording is worse. */
class MalformedRecording(val line: Int, val detail: String) :
    IllegalArgumentException("recording line $line: $detail")

/**
 * Reading a [Recording] back from its text form.
 *
 * ### Why this had to exist
 *
 * `toText()` shipped with stories 0.3 and 0.4 and **nothing could read it**. A recording that
 * cannot cross a process boundary is not a debugging instrument — it is a string a live process
 * prints about itself, and the live process is exactly the one that is not available when eight
 * phones in a dark house have gone wrong. It is also the premise of host crash recovery (story
 * 0.10): relaunch and resume from the local recording.
 *
 * ### Strict, and loud
 *
 * Every failure throws [MalformedRecording] naming the line. Nothing is skipped, defaulted or
 * best-efforted. A parser that quietly drops a row it does not recognise reproduces the invisible
 * drop this project keeps designing against — and it would do it to the one artifact kept
 * specifically to find out what happened.
 *
 * ### The version is checked, not tolerated
 *
 * The header carries a format version and a mismatch is fatal. The state row's field list has
 * already changed once (`ended` was added), and a parser that read an old recording under a new
 * schema would produce a state that never existed and replay it confidently.
 */
object RecordingText {

    fun parse(text: String): Recording {
        val lines = text.lines().filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw MalformedRecording(0, "empty")
        if (lines[0] != Recording.HEADER) {
            throw MalformedRecording(
                1,
                "expected header '${Recording.HEADER}', got '${lines[0]}'. A recording written by " +
                    "another format version cannot be read under this one — the state row's field " +
                    "list has already changed once.",
            )
        }

        var initial: String? = null
        val events = mutableListOf<Event>()
        val refusals = mutableListOf<String>()
        val effects = mutableListOf<String>()
        val states = mutableListOf<String>()

        for ((i, line) in lines.withIndex().drop(1)) {
            val number = i + 1
            if (line.length < 2 || line[1] != ' ') {
                throw MalformedRecording(number, "expected '<tag> <row>', got '$line'")
            }
            val row = line.substring(2)
            when (line[0]) {
                'I' -> {
                    if (initial != null) throw MalformedRecording(number, "a second initial state row")
                    initial = row
                }
                'E' -> events += parseEvent(row, number)
                'X' -> refusals += row
                'F' -> effects += row
                'S' -> states += row
                else -> throw MalformedRecording(
                    number,
                    "unknown row tag '${line[0]}'. Refusing rather than skipping: a parser that " +
                        "drops rows it does not recognise silently shortens the recording.",
                )
            }
        }

        val start = initial ?: throw MalformedRecording(lines.size, "no initial state row")
        return Recording(start, events, effects, states, refusals)
    }

    /**
     * Every event field is required and unknown fields are fatal.
     *
     * A missing field cannot be defaulted: `Seat(0)` is a real seat and `Tick(0)` is a real moment,
     * so any default here invents an event that did not happen and replays it as fact.
     */
    private fun parseEvent(row: String, line: Int): Event {
        val parts = row.split('|')
        val name = parts[0]
        val fields = LinkedHashMap<String, String>()
        for (part in parts.drop(1)) {
            val eq = part.indexOf('=')
            if (eq < 0) throw MalformedRecording(line, "field '$part' is not key=value")
            val key = part.substring(0, eq)
            if (fields.put(key, part.substring(eq + 1)) != null) {
                throw MalformedRecording(line, "duplicate field '$key'")
            }
        }

        fun req(key: String): String =
            fields[key] ?: throw MalformedRecording(line, "$name has no '$key' field")

        fun long(key: String): Long =
            req(key).toLongOrNull() ?: throw MalformedRecording(line, "$key='${req(key)}' is not a number")

        fun int(key: String): Int =
            req(key).toIntOrNull() ?: throw MalformedRecording(line, "$key='${req(key)}' is not a number")

        fun seat(key: String) = Seat(int(key))
        fun marker(key: String) = MarkerId(unescape(req(key), line))
        fun seatOrNone(key: String): Seat? = if (req(key) == "none") null else seat(key)

        /**
         * A list of plain integers. Empty value means an empty list — an entry a player handed
         * over having touched nothing is a real entry and grades like any other, so it must
         * survive the round trip as itself rather than as a missing field.
         */
        fun ints(key: String): List<Int> {
            val raw = req(key)
            if (raw.isEmpty()) return emptyList()
            return raw.split(',').map {
                it.toIntOrNull() ?: throw MalformedRecording(line, "'$it' in $key is not a number")
            }
        }

        /** A list of plain longs. Empty value means an empty list, for [ints]' reason. */
        fun longs(key: String): List<Long> {
            val raw = req(key)
            if (raw.isEmpty()) return emptyList()
            return raw.split(',').map {
                it.toLongOrNull() ?: throw MalformedRecording(line, "'$it' in $key is not a number")
            }
        }

        fun seats(key: String): List<Seat> {
            val raw = req(key)
            if (raw.isEmpty()) return emptyList()
            return raw.split(',').map {
                Seat(it.toIntOrNull() ?: throw MalformedRecording(line, "'$it' in $key is not a seat"))
            }
        }

        /** A list of markers. Each element unescaped — the comma is a separator, so it escapes. */
        fun markers(key: String): List<MarkerId> {
            val raw = req(key)
            if (raw.isEmpty()) return emptyList()
            return raw.split(',').map { MarkerId(unescape(it, line)) }
        }

        /**
         * The host's Insider setting: a number, or UNKNOWN.
         *
         * `none` rather than a sentinel integer, and fatal on anything else. UNKNOWN is a real
         * answer — *let the house decide* — and a parser that read it as a zero would replay a
         * round the host had configured, not the one they left alone.
         */
        fun chosen(key: String): Int? = if (req(key) == "none") null else int(key)

        val at = Tick(long("at"))
        val event = when (name) {
            "RoundArmed" -> Event.RoundArmed(
                at, long("seed"), seats("seats"), seats("insiders"),
                chosen("chosen"), markers("markers"),
            )
            "MarkerScanned" -> Event.MarkerScanned(at, seat("actor"), marker("marker"))
            "SubroutineReturned" ->
                Event.SubroutineReturned(at, seat("actor"), marker("marker"), ints("entered"))
            "PerformanceEnded" -> Event.PerformanceEnded(at, seat("actor"))
            "RevokeArmed" -> Event.RevokeArmed(at, seat("actor"))
            "ContactMade" -> Event.ContactMade(at, seat("actor"), seat("target"))
            "MeetingCalled" -> Event.MeetingCalled(at, seat("caller"), trigger(req("trigger"), line))
            "MeetingCheckedIn" -> Event.MeetingCheckedIn(at, seat("seat"))
            "ReadyToVoteDeclared" -> Event.ReadyToVoteDeclared(at, seat("seat"))
            "VoteSelected" -> Event.VoteSelected(at, seat("voter"), seatOrNone("target"))
            "VoteLocked" -> Event.VoteLocked(at, seat("voter"))
            "DiscussionClosed" -> Event.DiscussionClosed(at)
            "VoteWindowClosed" -> Event.VoteWindowClosed(at)
            "TallyHalfwayReached" -> Event.TallyHalfwayReached(at)
            "MeetingClosed" -> Event.MeetingClosed(at)
            "EgressFired" -> Event.EgressFired(
                at, seat("actor"), egressType(req("type"), line), markers("nodes"),
            )
            "SyncPulseReturned" -> Event.SyncPulseReturned(at, seat("actor"), longs("taps"))
            "EgressExpired" -> Event.EgressExpired(at)
            else -> throw MalformedRecording(
                line,
                "unknown event '$name'. This recording was written by a build that had an event " +
                    "this one does not.",
            )
        }

        // Unknown fields are fatal for the same reason unknown rows are: a field this build does
        // not read is a field the writing build thought was part of the event.
        val consumed = expectedFields(name)
        val extra = fields.keys - consumed
        if (extra.isNotEmpty()) throw MalformedRecording(line, "$name has unexpected field(s) $extra")
        return event
    }

    private fun expectedFields(name: String): Set<String> = when (name) {
        "RoundArmed" -> setOf("at", "seed", "seats", "insiders", "chosen", "markers")
        "MarkerScanned" -> setOf("at", "actor", "marker")
        "SubroutineReturned" -> setOf("at", "actor", "marker", "entered")
        "PerformanceEnded" -> setOf("at", "actor")
        "RevokeArmed" -> setOf("at", "actor")
        "ContactMade" -> setOf("at", "actor", "target")
        "MeetingCalled" -> setOf("at", "caller", "trigger")
        "MeetingCheckedIn" -> setOf("at", "seat")
        "ReadyToVoteDeclared" -> setOf("at", "seat")
        "VoteSelected" -> setOf("at", "voter", "target")
        "VoteLocked" -> setOf("at", "voter")
        "DiscussionClosed" -> setOf("at")
        "VoteWindowClosed" -> setOf("at")
        "TallyHalfwayReached" -> setOf("at")
        "MeetingClosed" -> setOf("at")
        "EgressFired" -> setOf("at", "actor", "type", "nodes")
        "SyncPulseReturned" -> setOf("at", "actor", "taps")
        "EgressExpired" -> setOf("at")
        else -> emptySet()
    }

    /**
     * Beacon or Tether, read back. **An unrecognised label is fatal rather than defaulted.**
     *
     * The two are mechanically identical in v1, so a parser that guessed would replay a round that
     * played the same and *read* differently — the label is on the widget, in the alert, and in
     * every account anybody gives of the evening afterwards. Guessing it would make the recording
     * disagree with the one thing it exists to settle.
     */
    private fun egressType(raw: String, line: Int): EgressType =
        EgressType.entries.firstOrNull { it.toString() == raw }
            ?: throw MalformedRecording(line, "unknown Egress type '$raw'")

    /**
     * How a meeting was called, read back.
     *
     * **An unrecognised trigger is fatal rather than defaulted to the card.** The two forms differ
     * at exactly one place — an Egress makes the card inert and leaves the report alone (D-133) —
     * so a parser that guessed would replay a refusal that never happened, or fail to replay one
     * that did.
     */
    private fun trigger(raw: String, line: Int): MeetingTrigger = when {
        raw == "card" -> MeetingTrigger.MeetingCard
        raw.startsWith("report:") -> MeetingTrigger.RevokeReported(
            Seat(
                raw.removePrefix("report:").toIntOrNull()
                    ?: throw MalformedRecording(line, "'$raw' names no seat"),
            ),
        )
        else -> throw MalformedRecording(line, "unknown meeting trigger '$raw'")
    }

    /**
     * The exact inverse of `Transcript.escape`, scanned left to right.
     *
     * Left to right matters. `\\p` is a literal backslash followed by 'p', not an escaped pipe —
     * a naive `replace("\\p", "|")` run before `replace("\\\\", "\\")` would turn a marker id
     * containing a backslash into one containing a pipe, and a pipe forges a field separator.
     */
    private fun unescape(s: String, line: Int): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') { out.append(c); i++; continue }
            if (i + 1 >= s.length) throw MalformedRecording(line, "value ends in a dangling escape")
            when (val next = s[i + 1]) {
                '\\' -> out.append('\\')
                'p' -> out.append('|')
                'c' -> out.append(',')
                'n' -> out.append('\n')
                else -> throw MalformedRecording(line, "unknown escape '\\$next'")
            }
            i += 2
        }
        return out.toString()
    }
}
