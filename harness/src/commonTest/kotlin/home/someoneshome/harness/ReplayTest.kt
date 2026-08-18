package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SEATS = (0 until 8).map { Seat(it) }
private val INSIDERS = listOf(Seat(1), Seat(5))

/** A round with every event kind in it, long enough that ordering mistakes have room to show. */
private fun round(): List<Event> = buildList {
    add(Event.RoundArmed(Tick(0), seed = 20260818L, seats = SEATS, insiders = INSIDERS))
    var t = 1L
    repeat(40) { i ->
        val seat = SEATS[i % SEATS.size]
        add(Event.MarkerScanned(Tick(t++), seat, MarkerId("m${i % 7}")))
        add(Event.SubroutineCompleted(Tick(t++), seat, MarkerId("m${i % 7}")))
        if (i % 9 == 0) add(Event.RevokeArmed(Tick(t++), Seat(1)))
        if (i % 9 == 4) add(Event.ContactMade(Tick(t++), Seat(1), SEATS[(i + 3) % SEATS.size]))
        if (i % 17 == 16) {
            add(Event.MeetingCalled(Tick(t++), seat))
            SEATS.forEach { v -> add(Event.VoteCast(Tick(t++), v, if (v.index % 3 == 0) null else Seat(1))) }
            add(Event.MeetingClosed(Tick(t++)))
        }
    }
}

class RecordingTest {

    @Test
    fun `a round replays byte-identically`() {
        val (_, recording) = record(GameState.EMPTY, round())
        assertEquals(ReplayResult.Identical, replay(GameState.EMPTY, recording))
    }

    @Test
    fun `replaying twice gives the same answer`() {
        val (_, recording) = record(GameState.EMPTY, round())
        assertEquals(replay(GameState.EMPTY, recording), replay(GameState.EMPTY, recording))
    }

    @Test
    fun `recording captures every event and emits a non-trivial transcript`() {
        val events = round()
        val (_, recording) = record(GameState.EMPTY, events)
        assertEquals(events.size, recording.events.size)
        assertTrue(recording.effectTranscript.size > 40, "a round this long must emit effects")
    }

    @Test
    fun `the recording text is stable across identical runs`() {
        val a = record(GameState.EMPTY, round()).second.toText()
        val b = record(GameState.EMPTY, round()).second.toText()
        assertEquals(a, b)
    }

    /**
     * THE TEST THAT MAKES THE OTHERS MEAN SOMETHING.
     *
     * `replay` returning Identical proves nothing unless it can return Diverged. This corrupts a
     * recording by one character and requires the mismatch to be found, at the right index, with
     * both sides reported — the difference between "the round did not replay" and a bug report.
     */
    @Test
    fun `replay detects a divergence and says where`() {
        val (_, recording) = record(GameState.EMPTY, round())
        val corrupted = Recording(
            events = recording.events,
            effectTranscript = recording.effectTranscript.toMutableList().also {
                it[7] = it[7].replace("cooldownStarted=true", "cooldownStarted=false")
                    .replace("luminance=1", "luminance=2")
            },
        )
        val result = replay(GameState.EMPTY, corrupted)
        assertTrue(result is ReplayResult.Diverged, "a corrupted recording must not replay clean")
        assertEquals(7, result.index)
        assertTrue(result.expected != result.actual)
    }

    @Test
    fun `replay detects a truncated recording rather than passing on the shorter prefix`() {
        val (_, recording) = record(GameState.EMPTY, round())
        val short = Recording(recording.events, recording.effectTranscript.dropLast(1))
        val result = replay(GameState.EMPTY, short)
        assertTrue(result is ReplayResult.Diverged)
        assertEquals(recording.effectTranscript.size - 1, result.index)
    }

    /**
     * Order changes the SIMULATION but not the STREAM, and both halves matter.
     *
     * Arming then contacting revokes; contacting then arming does not, because the ability was
     * not armed when the contact landed. So the state must differ — otherwise event order is not
     * being respected and replay is meaningless.
     *
     * But the emitted effects must be IDENTICAL, because a client that could tell the two apart
     * would know whether the contact landed, which is rule 1 exactly.
     */
    @Test
    fun `order changes the state but never the visible effect stream`() {
        val armedFirst = listOf(
            Event.RoundArmed(Tick(0), 1L, SEATS, INSIDERS),
            Event.RevokeArmed(Tick(1), Seat(1)),
            Event.ContactMade(Tick(2), Seat(1), Seat(3)),
        )
        val armedAfter = listOf(
            Event.RoundArmed(Tick(0), 1L, SEATS, INSIDERS),
            Event.ContactMade(Tick(1), Seat(1), Seat(3)),
            Event.RevokeArmed(Tick(2), Seat(1)),
        )

        val (stateA, recordingA) = record(GameState.EMPTY, armedFirst)
        val (stateB, recordingB) = record(GameState.EMPTY, armedAfter)

        assertEquals(listOf(3), stateA.revoked.map { it.index }, "arming before contact must land")
        assertEquals(emptyList(), stateB.revoked.map { it.index }, "contact before arming must not")

        assertEquals(
            recordingA.effectTranscript,
            recordingB.effectTranscript,
            "the effect stream reveals whether the contact landed",
        )
    }
}
