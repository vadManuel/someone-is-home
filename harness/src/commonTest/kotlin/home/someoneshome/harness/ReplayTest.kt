package home.someoneshome.harness

import home.someoneshome.model.Balance
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **The first tick at which an Insider ability is off its opening cooldown** (D-132).
 *
 * Every cooldown starts the round already running at half its normal duration. Every Revoke below
 * is armed after this moment — armed inside the opening stretch of peace it would arm nothing,
 * revoke nobody, and leave every assertion about the *shape* of the effect stream passing over a
 * round in which the transition under test never happened.
 */
private val READY = Balance.REVOKE_COOLDOWN / 2

class RecordingTest {

    /**
     * A refused event is recorded, and a recording that includes refusals replays identically.
     *
     * **An unrecorded refusal is an invisible drop.** The gate exists so the recording's effect
     * rows and state rows stop disagreeing about a round that had not begun; a gate that silently
     * swallowed events would fix that by creating a different disagreement, where the recording no
     * longer says what reached the rules at all.
     */
    @Test
    fun `refusals are recorded and replay identically`() {
        val events = listOf(
            Event.MeetingCalled(Tick(0), Seat(2), MeetingTrigger.MeetingCard),
            Event.ContactMade(Tick(1), Seat(1), Seat(2)),
        ) + round()
        val (_, recording) = record(GameState.EMPTY, events)
        assertEquals(2, recording.refusalTranscript.size, recording.refusalTranscript.toString())
        assertTrue(recording.refusalTranscript[0].contains("event=MeetingCalled"))
        assertTrue(recording.refusalTranscript[0].contains("reason=RoundNotArmed"))
        assertTrue(recording.toText().contains("\nX Refused|"), "refusals are absent from the text form")
        assertEquals(ReplayResult.Identical, replay(GameState.EMPTY, recording))
    }

    /** A round that never refuses anything records no refusal rows. */
    @Test
    fun `a clean round records no refusals`() {
        assertEquals(emptyList(), record(GameState.EMPTY, round()).second.refusalTranscript)
    }

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
            refusalTranscript = recording.refusalTranscript,
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
            recording.refusalTranscript,
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
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
        )
        val armedAfter = listOf(
            Event.RoundArmed(Tick(0), 1L, SEATS, INSIDERS),
            Event.ContactMade(Tick(READY + 1), Seat(1), Seat(3)),
            Event.RevokeArmed(Tick(READY + 2), Seat(1)),
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
     * **THE SAME HOLE, ONE DRAW FURTHER ON — and this one was found by an injection coming back
     * asleep.**
     *
     * Deleting the work orders from the state row failed nothing. That is the `ended` omission in a
     * new costume: **the answer key is what a verdict is computed from**, so a state row that
     * stopped short of it would certify a build whose draw had quietly changed — different
     * Subroutines, different cards, different answers — as replaying byte-identically, because the
     * effect stream carries only a boolean per return and looks the same either way.
     *
     * So the tamper is the guard. Mark every line of every order done in the state rows, change
     * nothing else, and the replay must refuse it. On a build that does not render the orders there
     * is nothing to mark, the recording comes back unchanged, and this fails on the assertion below
     * rather than passing quietly.
     */
    @Test
    fun `replay detects a work order that was drawn differently`() {
        val markers = (0 until 8).map { MarkerId("m$it") }
        val events = listOf(Event.RoundArmed(Tick(0), 1L, SEATS, INSIDERS, null, markers))
        val (state, recording) = record(GameState.EMPTY, events)
        assertTrue(
            state.workOrders.any { it.entries.isNotEmpty() },
            "precondition: the round was armed with work in it",
        )

        val tampered = recording.stateTranscript.map { it.replace("/open", "/done") }
        assertNotEquals(
            recording.stateTranscript, tampered,
            "the state row does not carry the work orders at all, so the answer key a verdict is " +
                "computed from is outside the replay guarantee",
        )
        val result = replay(
            GameState.EMPTY,
            Recording(
                initialState = recording.initialState,
                events = recording.events,
                effectTranscript = recording.effectTranscript,
                stateTranscript = tampered,
                refusalTranscript = recording.refusalTranscript,
            ),
        )
        assertTrue(result is ReplayResult.Diverged, "a different draw replayed clean")
        assertEquals(ReplayResult.Diverged.Kind.STATE, result.kind)
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
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
        )
        val (state, recording) = record(GameState.EMPTY, events)
        assertEquals(listOf(3), state.revoked.map { it.index }, "precondition: the revoke landed")

        val asIfRevokeDidNothing = Recording(
            initialState = recording.initialState,
            events = recording.events,
            effectTranscript = recording.effectTranscript,
            stateTranscript = recording.stateTranscript.map { it.replace("revoked=3", "revoked=") },
            refusalTranscript = recording.refusalTranscript,
        )

        val result = replay(GameState.EMPTY, asIfRevokeDidNothing)
        assertTrue(result is ReplayResult.Diverged, "a state-only regression must not replay clean")
        assertEquals(ReplayResult.Diverged.Kind.STATE, result.kind)
    }

    /** A recording replayed from a different starting state must not certify as identical. */
    @Test
    fun `replay rejects a recording replayed from the wrong starting state`() {
        val events = listOf(Event.RevokeArmed(Tick(READY + 1), Seat(1)), Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)))
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
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
        )).first
        assertEquals(listOf(3), dirty.revoked.map { it.index })

        val rearmed = record(dirty, listOf(Event.RoundArmed(Tick(READY + 9), 2L, SEATS, INSIDERS))).first
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
        val skip = Transcript.render(Event.VoteSelected(Tick(1), Seat(0), null))
        val negative = Transcript.render(Event.VoteSelected(Tick(1), Seat(0), Seat(-1)))
        assertTrue(skip != negative, "abstention and a seat must not share a transcript row")
    }
}
