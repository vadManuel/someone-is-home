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
 * **The owner's 2026-11-15 rulings R2, R3 and R5, at the rules** (D-217).
 *
 * R2: being Restrained or Revoked disarms an armed Revoke. R3: only living players can report.
 * R5: contacts are ignored during meetings. Each through `admit`, the one gate the house uses, so a
 * refusal is a recorded refusal and an admission is the rules' own answer.
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
        assertTrue(armed(s, first), "the meeting disarmed the Insider before anybody was Restrained")
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
        assertTrue(armed(s, first), "the refusal spent the armed Revoke")
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
