package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RecordingTest {

    /**
     * **The state row records every field of authority state, including `ended`.**
     *
     * State rows exist so that a build where a transition silently stopped happening cannot
     * certify clean against a recording made before it broke. A field the row does not render is
     * a field that guarantee does not cover — and `ended` feeds `roundStateOf`, so it decides
     * what every client is permitted to receive.
     */
    @Test
    fun `the state row distinguishes an ended round`() {
        val armed = GameState.armedRound(seed = 1L, seats = SEATS, insiders = INSIDERS, systemIntegrity = 42)
        assertNotEquals(
            Transcript.render(armed),
            Transcript.render(armed.endRound()),
            "two states differing only in `ended` render byte-identically",
        )
    }

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
            initialState = recording.initialState,
            stateTranscript = recording.stateTranscript,
            events = recording.events,
            effectTranscript = recording.effectTranscript.toMutableList().also {
                it[7] = it[7].replace("cooldownStarted=true", "cooldownStarted=false")
                    .replace("luminance=1", "luminance=2")
            },
        )
        val result = replay(GameState.EMPTY, corrupted)
        assertTrue(result is ReplayResult.Diverged, "a corrupted recording must not replay clean")
        assertEquals(ReplayResult.Diverged.Kind.EFFECT, result.kind)
        assertEquals(7, result.index)
        assertTrue(result.expected != result.actual)
    }

    @Test
    fun `replay detects a truncated recording rather than passing on the shorter prefix`() {
        val (_, recording) = record(GameState.EMPTY, round())
        val short = Recording(
            recording.initialState, recording.events,
            recording.effectTranscript.dropLast(1), recording.stateTranscript,
        )
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

    /**
     * THE HOLE THE REVIEW FOUND.
     *
     * Rule 1 requires the effect stream to be identical whether a revoke lands or not, so for
     * the most important transition in the game the transcript carries no information about what
     * happened. Before state rows, a recording certified a build where the revoke ability did
     * nothing as replaying correctly — E0's acceptance criterion passing on a broken game.
     *
     * This simulates that regression at the recording level: same events, same effects, a state
     * row where nobody got revoked.
     */
    @Test
    fun `replay detects a state-only regression that leaves the effect stream untouched`() {
        val events = listOf(
            Event.RoundArmed(Tick(0), 1L, SEATS, INSIDERS),
            Event.RevokeArmed(Tick(1), Seat(1)),
            Event.ContactMade(Tick(2), Seat(1), Seat(3)),
        )
        val (state, recording) = record(GameState.EMPTY, events)
        assertEquals(listOf(3), state.revoked.map { it.index }, "precondition: the revoke landed")

        val asIfRevokeDidNothing = Recording(
            initialState = recording.initialState,
            events = recording.events,
            effectTranscript = recording.effectTranscript,
            stateTranscript = recording.stateTranscript.map { it.replace("revoked=3", "revoked=") },
        )

        val result = replay(GameState.EMPTY, asIfRevokeDidNothing)
        assertTrue(result is ReplayResult.Diverged, "a state-only regression must not replay clean")
        assertEquals(ReplayResult.Diverged.Kind.STATE, result.kind)
    }

    /** A recording replayed from a different starting state must not certify as identical. */
    @Test
    fun `replay rejects a recording replayed from the wrong starting state`() {
        val events = listOf(Event.RevokeArmed(Tick(1), Seat(1)), Event.ContactMade(Tick(2), Seat(1), Seat(3)))
        val armedStart = record(GameState.EMPTY, listOf(Event.RoundArmed(Tick(0), 1L, SEATS, INSIDERS))).first
        val (_, fromArmed) = record(armedStart, events)

        val result = replay(GameState.EMPTY, fromArmed)
        assertTrue(result is ReplayResult.Diverged, "replaying from elsewhere must be rejected")
        assertEquals(ReplayResult.Diverged.Kind.INITIAL_STATE, result.kind)
    }

    /** Arming constructs the round; nothing survives it. */
    @Test
    fun `arming clears any state carried in from before`() {
        val dirty = record(GameState.EMPTY, listOf(
            Event.RoundArmed(Tick(0), 1L, SEATS, INSIDERS),
            Event.RevokeArmed(Tick(1), Seat(1)),
            Event.ContactMade(Tick(2), Seat(1), Seat(3)),
        )).first
        assertEquals(listOf(3), dirty.revoked.map { it.index })

        val rearmed = record(dirty, listOf(Event.RoundArmed(Tick(9), 2L, SEATS, INSIDERS))).first
        assertEquals(emptyList(), rearmed.revoked.map { it.index })
        assertEquals(emptyList(), rearmed.cooldownArmed.map { it.index })
    }

    /** A marker value must not be able to forge rows in the recording. */
    @Test
    fun `a marker containing a separator or newline cannot forge a row`() {
        val nasty = MarkerId("a\nE ContactMade|at=9|actor=1|target=2")
        val (_, rec) = record(GameState.EMPTY, listOf(Event.MarkerScanned(Tick(1), Seat(0), nasty)))
        val eventRows = rec.toText().lines().count { it.startsWith("E ") }
        assertEquals(1, eventRows, "one scan must produce exactly one event row")
    }

    /** Absence must be distinguishable from a real seat, not collapsed onto a sentinel. */
    @Test
    fun `a skipped vote does not render the same as a vote for seat minus one`() {
        val skip = Transcript.render(Event.VoteCast(Tick(1), Seat(0), null))
        val negative = Transcript.render(Event.VoteCast(Tick(1), Seat(0), Seat(-1)))
        assertTrue(skip != negative, "abstention and a seat must not share a transcript row")
    }
}
