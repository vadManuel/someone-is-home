package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Story 0.10. The host crashed; the round is still happening in the house. */
class ResumeTest {

    private fun crashed() = record(GameState.EMPTY, round())

    @Test
    fun `a round resumes to the state it crashed in`() {
        val (live, recording) = crashed()
        val result = resumeFromText(recording.toText())
        assertIs<ResumeResult.Resumed>(result)
        assertEquals(
            Transcript.render(live), Transcript.render(result.state),
            "the resumed round is not the round that was being played",
        )
        assertEquals(recording.events.size, result.eventsReplayed)
    }

    /** Resuming twice from the same text gives the same round. */
    @Test
    fun `resuming is deterministic`() {
        val text = crashed().second.toText()
        val a = resumeFromText(text)
        val b = resumeFromText(text)
        assertIs<ResumeResult.Resumed>(a)
        assertIs<ResumeResult.Resumed>(b)
        assertEquals(Transcript.render(a.state), Transcript.render(b.state))
    }

    /** A resumed round is still recordable and still replays. Recovery is not a dead end. */
    @Test
    fun `a resumed round carries on and still replays`() {
        val result = resumeFromText(crashed().second.toText())
        assertIs<ResumeResult.Resumed>(result)

        val more = listOf(
            Event.RevokeArmed(Tick(9_000), Seat(1)),
            Event.ContactMade(Tick(9_001), Seat(1), Seat(4)),
        )
        val (after, continued) = record(result.state, more)
        assertEquals(ReplayResult.Identical, replay(result.state, continued))
        assertTrue(after.isRevoked(Seat(4)), "the resumed round did not carry its rules forward")
    }

    /** Refusals survive, or a resumed round disagrees with its own recording immediately. */
    @Test
    fun `refusals are replayed on resume`() {
        val events = listOf(Event.MeetingCalled(Tick(0), Seat(2))) + round()
        val result = resumeFromText(record(GameState.EMPTY, events).second.toText())
        assertIs<ResumeResult.Resumed>(result)
        assertEquals(1, result.refusalsReplayed)
    }

    /**
     * **Failing is the safe direction.** A round that comes back subtly wrong is worse than one
     * that does not come back — eight people are in a dark house and a resumed round that
     * disagrees with the one they played hands them a game with no appealable history.
     */
    @Test
    fun `a recording whose effects were tampered with is refused`() {
        val recording = crashed().second
        val text = recording.toText().replaceFirst("F AbilityFired|actor=1|cooldownStarted=true",
            "F AbilityFired|actor=1|cooldownStarted=false")
        val result = resumeFromText(text)
        assertIs<ResumeResult.Refused>(result)
        assertEquals(ReplayResult.Diverged.Kind.EFFECT, result.because.kind)
    }

    /**
     * The state rows are checked, which is what stops a resumed round being built from an
     * authority state that no sequence of events could produce.
     */
    @Test
    fun `a recording whose state rows were tampered with is refused`() {
        val recording = crashed().second
        val text = recording.toText().replace("|revoked=0,2,7|", "|revoked=|")
        val result = resumeFromText(text)
        assertIs<ResumeResult.Refused>(result)
        assertEquals(ReplayResult.Diverged.Kind.STATE, result.because.kind)
    }

    /** The gate's refusals are compared like everything else, not carried along unchecked. */
    @Test
    fun `a recording whose refusal rows were tampered with is refused`() {
        val events = listOf(Event.MeetingCalled(Tick(0), Seat(2))) + round()
        val recording = record(GameState.EMPTY, events).second
        val text = recording.toText().replace("reason=RoundNotArmed", "reason=RoundAlreadyEnded")
        val result = resumeFromText(text)
        assertIs<ResumeResult.Refused>(result)
        assertEquals(ReplayResult.Diverged.Kind.REFUSAL, result.because.kind)
    }

    /** A recording that began somewhere else is refused rather than silently rebased. */
    @Test
    fun `a recording that started from another state is refused`() {
        val recording = crashed().second
        val text = recording.toText().replaceFirst("I armed=false|", "I armed=true|")
        val result = resumeFromText(text)
        assertIs<ResumeResult.Refused>(result)
        assertEquals(ReplayResult.Diverged.Kind.INITIAL_STATE, result.because.kind)
    }

    @Test
    fun `an unreadable recording is reported as unreadable rather than as a divergence`() {
        val result = resumeFromText(crashed().second.toText() + "Q junk\n")
        assertIs<ResumeResult.Unreadable>(result)
        assertTrue(result.because.line > 1)
    }

    @Test
    fun `an empty file is unreadable rather than an empty round`() {
        assertIs<ResumeResult.Unreadable>(resumeFromText(""))
    }

    /**
     * A recording of a round that never got past arming resumes to an armed round, not to
     * nothing. The shortest real crash: the host died during setup.
     */
    @Test
    fun `a round that only got as far as arming resumes armed`() {
        val armed = listOf(Event.RoundArmed(Tick(0), 5L, SEATS, INSIDERS))
        val result = resumeFromText(record(GameState.EMPTY, armed).second.toText())
        assertIs<ResumeResult.Resumed>(result)
        assertTrue(result.state.armed)
        assertEquals(SEATS.size, result.state.seats.size)
    }
}
