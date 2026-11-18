package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The owner's 2026-11-15 rulings R2, R3 and R5, and S4, at the rules** (D-217, D-219).
 *
 * R2: being Restrained or Revoked disarms an armed Revoke. R3: only living players can report.
 * R5: contacts are ignored during meetings. S4 (D-219): a Revoked player is reported once. Each
 * through `admit`, the one gate the house uses, so a refusal is a recorded refusal and an
 * admission is the rules' own answer.
 */
class ContactRulingsTest {

    // Eight, so that two Revokes leave the Residents short of parity and the round still running.
    private val seats = (0 until 8).map { Seat(it) }
    private val first = Seat(1)
    private val second = Seat(4)
    private val ready = Balance.REVOKE_COOLDOWN / 2

    private fun round(): GameState =
        reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = listOf(first, second))).state

    private fun put(state: GameState, event: Event): GameState {
        val admission = admit(state, event)
        assertIs<Admission.Admitted>(admission, "the fixture's $event was refused: $admission")
        return admission.reduction.state
    }

    private fun armed(state: GameState, seat: Seat): Boolean = state.cooldownArmed.any { it.index == seat.index }

    // ---- R2 ----------------------------------------------------------------------------------------

    @Test
    fun `R2 an armed Insider who is Revoked is disarmed and their touch lands nothing`() {
        var s = round()
        s = put(s, Event.RevokeArmed(Tick(ready), first))
        s = put(s, Event.RevokeArmed(Tick(ready), second))
        assertTrue(armed(s, first) && armed(s, second), "the fixture armed nobody")

        // The second Insider Revokes the first, who is holding an armed Revoke.
        s = put(s, Event.ContactMade(Tick(ready + 1), second, first))
        assertTrue(s.isRevoked(first), "the fixture revoked nobody")
        assertFalse(armed(s, first), "R2: a Revoked seat kept its armed Revoke")

        // A forged or late touch from the Revoked seat lands nothing.
        s = put(s, Event.ContactMade(Tick(ready + 2), first, Seat(2)))
        assertFalse(s.isRevoked(Seat(2)), "R2: a Revoked seat's armed Revoke landed from the couch")
    }

    @Test
    fun `R2 an armed Insider who is Restrained is disarmed at the takeover`() {
        var s = round()
        s = put(s, Event.RevokeArmed(Tick(ready), first))
        s = put(s, Event.MeetingCalled(Tick(ready + 1), Seat(0), MeetingTrigger.MeetingCard))
        for (seat in seats) s = put(s, Event.MeetingCheckedIn(Tick(ready + 2), seat))
        if (s.meeting?.phase == home.someoneshome.model.MeetingPhase.Discussion) {
            s = put(s, Event.DiscussionClosed(Tick(ready + 3)))
        }
        for (seat in s.livingSeats) {
            s = put(s, Event.VoteSelected(Tick(ready + 4), seat, first))
            s = put(s, Event.VoteLocked(Tick(ready + 4), seat))
        }
        if (s.meeting?.phase == home.someoneshome.model.MeetingPhase.Vote) {
            s = put(s, Event.VoteWindowClosed(Tick(ready + 5)))
        }
        // **The call has already disarmed them** (the owner's 2026-11-18 ruling: an armed Revoke ends
        // at a meeting call). Until then the arm survived the meeting and R2's line at the takeover
        // was what took it; it is still there, as the second of two walls.
        assertFalse(armed(s, first), "a meeting call left an armed Revoke standing (2026-11-18)")
        s = put(s, Event.TallyHalfwayReached(Tick(ready + 6)))
        assertTrue(s.isRestrained(first), "the fixture restrained nobody")
        assertFalse(armed(s, first), "R2: a Restrained seat kept its armed Revoke")
    }

    // ---- R3 ----------------------------------------------------------------------------------------

    @Test
    fun `R3 a report whose caller is out is refused and calls nothing`() {
        var s = round()
        s = put(s, Event.RevokeArmed(Tick(ready), first))
        s = put(s, Event.ContactMade(Tick(ready + 1), first, Seat(2)))
        s = put(s, Event.RevokeArmed(Tick(ready + 1), second))
        s = put(s, Event.ContactMade(Tick(ready + 2), second, Seat(3)))
        assertTrue(s.isRevoked(Seat(2)) && s.isRevoked(Seat(3)), "the fixture revoked nobody")
        assertFalse(s.ended, "the fixture ended the round, so every refusal below would be that one")

        val forged = admit(s, Event.MeetingCalled(Tick(ready + 3), Seat(2), MeetingTrigger.RevokeReported(Seat(3))))
        assertEquals(Admission.Refused(RefusalReason.ReporterOut), forged, "R3: an out seat reported")

        // A living caller is admitted, which is the positive control.
        val honest = admit(s, Event.MeetingCalled(Tick(ready + 3), Seat(0), MeetingTrigger.RevokeReported(Seat(3))))
        assertIs<Admission.Admitted>(honest, "a living report was refused")
    }

    // ---- S4 ----------------------------------------------------------------------------------------

    /** A meeting from [call] to lights out, every living seat voting Skip so nobody is Restrained. */
    private fun throughAMeeting(state: GameState, call: Event.MeetingCalled): GameState {
        val at = call.at.step
        var s = put(state, call)
        for (seat in seats) s = put(s, Event.MeetingCheckedIn(Tick(at + 1), seat))
        if (s.meeting?.phase == home.someoneshome.model.MeetingPhase.Discussion) s = put(s, Event.DiscussionClosed(Tick(at + 2)))
        for (seat in s.livingSeats) {
            s = put(s, Event.VoteSelected(Tick(at + 3), seat, null))
            s = put(s, Event.VoteLocked(Tick(at + 3), seat))
        }
        if (s.meeting?.phase == home.someoneshome.model.MeetingPhase.Vote) s = put(s, Event.VoteWindowClosed(Tick(at + 4)))
        s = put(s, Event.TallyHalfwayReached(Tick(at + 5)))
        s = put(s, Event.MeetingClosed(Tick(at + 6)))
        assertEquals(null, s.meeting, "the fixture's meeting never closed")
        assertFalse(s.ended, "the fixture ended the round, so every refusal below would be that one")
        return s
    }

    @Test
    fun `S4 a Revoked player already reported is refused a second report and a fresh one is not`() {
        var s = round()
        s = put(s, Event.RevokeArmed(Tick(ready), first))
        s = put(s, Event.ContactMade(Tick(ready + 1), first, Seat(2)))
        assertTrue(s.isRevoked(Seat(2)), "the fixture revoked nobody")

        // The first report calls a meeting, and the seat is reported from then on.
        s = throughAMeeting(s, Event.MeetingCalled(Tick(ready + 2), Seat(0), MeetingTrigger.RevokeReported(Seat(2))))
        assertTrue(s.wasReported(Seat(2)), "the meeting did not record whom it reported")

        // A second report of the same couch phone, by any living seat, is refused and calls nothing.
        for (caller in listOf(Seat(0), Seat(3), first)) {
            assertEquals(
                Admission.Refused(RefusalReason.AlreadyReported),
                admit(s, Event.MeetingCalled(Tick(ready + 20), caller, MeetingTrigger.RevokeReported(Seat(2)))),
                "S4: seat ${caller.index} reported seat 2 again",
            )
        }

        // The positive control: a Revoked seat nobody has reported yet is still reported. Armed past
        // the half-cooldown the meeting reset (D-184).
        s = put(s, Event.RevokeArmed(Tick(3 * ready), second))
        s = put(s, Event.ContactMade(Tick(3 * ready + 1), second, Seat(5)))
        assertTrue(s.isRevoked(Seat(5)), "the fixture's second Revoke landed nothing")
        assertFalse(s.wasReported(Seat(5)), "a seat nobody reported is on the reported list")
        assertIs<Admission.Admitted>(
            admit(s, Event.MeetingCalled(Tick(3 * ready + 2), Seat(0), MeetingTrigger.RevokeReported(Seat(5)))),
            "a first report of a different Revoked seat was refused",
        )
    }

    /** A meeting-card call reports nobody, so a seat out before it can still be reported after it. */
    @Test
    fun `S4 a meeting nobody reported a seat at leaves that seat reportable`() {
        var s = round()
        s = put(s, Event.RevokeArmed(Tick(ready), first))
        s = put(s, Event.ContactMade(Tick(ready + 1), first, Seat(2)))
        s = throughAMeeting(s, Event.MeetingCalled(Tick(ready + 2), Seat(0), MeetingTrigger.MeetingCard))
        assertEquals(emptyList(), s.reported, "a meeting-card call reported somebody")
        assertIs<Admission.Admitted>(
            admit(s, Event.MeetingCalled(Tick(ready + 20), Seat(3), MeetingTrigger.RevokeReported(Seat(2)))),
            "a seat never reported was refused as already reported",
        )
    }

    // ---- R5 ----------------------------------------------------------------------------------------

    @Test
    fun `R5 a touch during a meeting is refused for every pair and spends nothing`() {
        var s = round()
        s = put(s, Event.RevokeArmed(Tick(ready), first))
        s = put(s, Event.MeetingCalled(Tick(ready + 1), Seat(0), MeetingTrigger.MeetingCard))
        for ((a, b) in listOf(first to Seat(2), Seat(2) to first, Seat(2) to Seat(3), second to Seat(0))) {
            assertEquals(
                Admission.Refused(RefusalReason.MeetingAlreadyRunning),
                admit(s, Event.ContactMade(Tick(ready + 2), a, b)),
                "R5: a touch between ${a.index} and ${b.index} was admitted at a meeting",
            )
        }
        // A refusal has no state to spend anything with. The arm itself is gone already: a meeting
        // call ends every armed Revoke (the owner's 2026-11-18 ruling, D-280), which until then
        // survived the meeting and was what this line checked.
        assertFalse(armed(s, first), "a meeting call left an armed Revoke standing (2026-11-18)")
    }

    /** Outside a meeting the same touch is admitted, which is what makes the refusal above R5's. */
    @Test
    fun `R5 the same touch after lights out is a contact again`() {
        var s = round()
        s = put(s, Event.RevokeArmed(Tick(ready), first))
        s = put(s, Event.ContactMade(Tick(ready + 1), first, Seat(2)))
        assertTrue(s.isRevoked(Seat(2)), "outside a meeting an armed touch should land")
    }
}
