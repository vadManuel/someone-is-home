package home.someoneshome.harness

import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The recording has to survive a process boundary, because the process that could answer
 * questions is the one that is gone.
 */
class RecordingTextTest {

    private fun recorded() = record(GameState.EMPTY, round()).second

    /** Text in, text out, byte for byte. */
    @Test
    fun `a recording round-trips through its text form`() {
        val original = recorded()
        val reparsed = RecordingText.parse(original.toText())
        assertEquals(original.toText(), reparsed.toText())
    }

    /**
     * Every event comes back as the same event, not merely as the same rendering — **with one
     * field deliberately excepted, and the exception is D-116.**
     *
     * A recording records the *seats* that handed a one line over and never the text, so the arming
     * event comes back with blanks where the confessions were. That is the promise printed on the
     * screen where a line is typed, kept in the one artefact of this game that outlives the
     * evening. Everything else about the arming — the seed, the seats, the draw, the public
     * setting, the markers — round-trips exactly, and every other event round-trips whole.
     *
     * Written as *blank the lines on both sides and compare* rather than as *skip the arming
     * event*, so the arming's other six fields stay under the guarantee: skipping it would have
     * let the seed or the draw stop surviving and nothing would have said so.
     */
    @Test
    fun `every event survives the round-trip as a typed event`() {
        val original = recorded()
        val reparsed = RecordingText.parse(original.toText())

        fun withoutTheText(events: List<Event>) = events.map { event ->
            if (event !is Event.RoundArmed) event
            else event.copy(oneLines = event.oneLines.map { it.copy(text = "") })
        }

        assertEquals(withoutTheText(original.events), reparsed.events)
        assertTrue(original.events.size > 100, "the fixture must exercise every event kind")

        // And the seats really are on the row: a recording that dropped the list entirely would
        // pass the comparison above for the wrong reason, because both sides would hold nothing.
        val armed = reparsed.events.filterIsInstance<Event.RoundArmed>().single()
        assertEquals(
            SEATS.map { it.index }, armed.oneLines.map { it.seat.index },
            "the recording forgot who handed a line over, so a replay cannot rebuild the desk",
        )
        assertTrue(
            armed.oneLines.all { it.text.isEmpty() },
            "a one line came back out of a recording: ${armed.oneLines}",
        )
    }

    /**
     * **Both Egress types survive, and so do the nodes and the taps.**
     *
     * The fixture round fires one Egress, so it can only ever prove one label round-trips — and
     * Beacon and Tether are mechanically identical, so a renderer that wrote a constant would be
     * invisible to it. This walks both, and it walks them **through a whole recording** rather than
     * through the renderer alone, because the failure that matters is a recording that replays into
     * a different evening from the one it was made on.
     */
    @Test
    fun `both Egress types and their nodes survive the round-trip`() {
        for (type in EgressType.entries) {
            val events = listOf<Event>(
                Event.RoundArmed(
                    Tick(0), seed = 5L, seats = SEATS, insiders = INSIDERS, markers = MARKERS,
                ),
                Event.EgressFired(Tick(3600), Seat(1), type, listOf(MARKERS[0], MARKERS[4])),
                Event.SyncPulseReturned(Tick(3700), Seat(2), listOf(3640L, 3670L, 3700L, 3730L)),
                Event.EgressExpired(Tick(6100)),
            )
            val reparsed = RecordingText.parse(record(GameState.EMPTY, events).second.toText())
            assertEquals(events, reparsed.events, "a $type Egress did not survive its own recording")
        }
    }

    /** A label this build does not know is fatal, never quietly read as the other one. */
    @Test
    fun `an unknown Egress type is refused`() {
        val text = recorded().toText().replace("|type=Beacon|", "|type=Semaphore|")
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    /**
     * **The point of the whole exercise.** A recording read from text replays clean against the
     * rules, which is what makes it a debugging instrument rather than a log.
     */
    @Test
    fun `a recording read from text replays identically`() {
        val text = recorded().toText()
        assertEquals(ReplayResult.Identical, replay(GameState.EMPTY, RecordingText.parse(text)))
    }

    /** Refusal rows survive too, or a resumed round would disagree about what reached the rules. */
    @Test
    fun `refusal rows survive the round-trip`() {
        val withRefusals = record(
            GameState.EMPTY,
            listOf(Event.MeetingCalled(Tick(0), Seat(2), MeetingTrigger.MeetingCard)) + round(),
        ).second
        val reparsed = RecordingText.parse(withRefusals.toText())
        assertEquals(withRefusals.refusalTranscript, reparsed.refusalTranscript)
        assertEquals(1, reparsed.refusalTranscript.size)
        assertEquals(ReplayResult.Identical, replay(GameState.EMPTY, reparsed))
    }

    /**
     * A marker id is external input, read off a piece of paper, and the escaping exists so a
     * hostile one cannot forge a row. The inverse has to be exact.
     */
    @Test
    fun `a marker id containing separators survives intact`() {
        val nasty = MarkerId("a|b\\c\nd\\pe\\\\f")
        val events = listOf(
            Event.RoundArmed(Tick(0), 1L, listOf(Seat(0)), emptyList()),
            Event.MarkerScanned(Tick(1), Seat(0), nasty),
        )
        val reparsed = RecordingText.parse(record(GameState.EMPTY, events).second.toText())
        assertEquals(nasty, (reparsed.events[1] as Event.MarkerScanned).marker)
        assertEquals(2, reparsed.events.size, "an embedded newline forged a row")
    }

    /** A skipped vote and a vote for a real seat must not collapse into each other. */
    @Test
    fun `an abstained vote survives as an abstained vote`() {
        val events = listOf(
            Event.RoundArmed(Tick(0), 1L, listOf(Seat(0), Seat(1)), emptyList()),
            Event.VoteSelected(Tick(1), Seat(0), null),
            Event.VoteSelected(Tick(2), Seat(1), Seat(0)),
        )
        val reparsed = RecordingText.parse(record(GameState.EMPTY, events).second.toText())
        assertEquals(null, (reparsed.events[1] as Event.VoteSelected).target)
        assertEquals(Seat(0), (reparsed.events[2] as Event.VoteSelected).target)
    }

    /** An empty insider list is a real state, not a missing field. */
    @Test
    fun `empty seat lists survive`() {
        val events = listOf(Event.RoundArmed(Tick(0), 1L, listOf(Seat(0)), emptyList()))
        val reparsed = RecordingText.parse(record(GameState.EMPTY, events).second.toText())
        assertEquals(emptyList(), (reparsed.events[0] as Event.RoundArmed).insiders)
    }

    // ---- everything below is about refusing, loudly ----

    @Test
    fun `a recording from another format version is refused`() {
        val text = recorded().toText().replaceFirst(Recording.HEADER, "someone-is-home/recording/1")
        val failure = assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
        assertTrue(failure.message!!.contains("recording/1"), failure.message!!)
    }

    @Test
    fun `an unknown row tag is refused rather than skipped`() {
        val text = recorded().toText() + "Q something\n"
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    @Test
    fun `an unknown event kind is refused`() {
        val text = recorded().toText().replaceFirst("E RoundArmed|", "E RoundDisarmed|")
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    /** A missing field cannot be defaulted: Seat(0) is a real seat and Tick(0) a real moment. */
    @Test
    fun `a missing field is refused rather than defaulted`() {
        val text = recorded().toText().replaceFirst(Regex("""\|actor=\d+"""), "")
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    /** A field this build does not read is one the writing build thought was part of the event. */
    @Test
    fun `an unexpected field is refused`() {
        val text = recorded().toText().replaceFirst("E MeetingClosed|at=", "E MeetingClosed|extra=1|at=")
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    @Test
    fun `a non-numeric field is refused`() {
        val text = recorded().toText().replaceFirst(Regex("""E RevokeArmed\|at=\d+"""), "E RevokeArmed|at=soon")
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    @Test
    fun `a recording with no initial state row is refused`() {
        val text = recorded().toText().lines().filterNot { it.startsWith("I ") }.joinToString("\n")
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    @Test
    fun `an empty document is refused`() {
        assertFailsWith<MalformedRecording> { RecordingText.parse("") }
    }

    /**
     * Built by concatenation rather than by `replaceFirst`, which treats a backslash in the
     * replacement as an escape and silently produces a well-formed document instead.
     */
    @Test
    fun `a dangling escape is refused`() {
        val backslash = '\\'
        val text = Recording.HEADER + "\nI armed=false\n" +
            "E MarkerScanned|at=0|actor=0|marker=m0" + backslash + "\n"
        val failure = assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
        assertTrue(failure.message!!.contains("dangling escape"), failure.message!!)
    }

    /** An escape this build does not know is a value it cannot reconstruct. */
    @Test
    fun `an unknown escape is refused`() {
        val text = Recording.HEADER + "\nI armed=false\n" +
            "E MarkerScanned|at=0|actor=0|marker=m" + '\\' + "q\n"
        assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
    }

    /** The failure names the line, because "malformed" alone sends nobody anywhere. */
    @Test
    fun `a failure names the line it failed on`() {
        val text = recorded().toText() + "Q something\n"
        val failure = assertFailsWith<MalformedRecording> { RecordingText.parse(text) }
        assertTrue(failure.line > 1, "line ${failure.line}")
        assertTrue(failure.message!!.contains("line ${failure.line}"), failure.message!!)
    }
}
