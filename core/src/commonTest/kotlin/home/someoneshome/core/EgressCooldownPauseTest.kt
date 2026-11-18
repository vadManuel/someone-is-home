package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Egress
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The Egress cooldown is pushed back by the pause, so it always outlives its Egress** (the owner's
 * 2026-11-18 ruling, decision pack 9f option a; D-282).
 *
 * The pack's own example: fired at 0 s, so the next is ready at 180 s; a report meeting runs from
 * 10 s to 190 s; the Egress resumes with 110 s left and is contained at 250 s. Before the ruling a
 * second Egress could be fired at 251 s.
 */
class EgressCooldownPauseTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val insider = Seat(1)
    private val second = Balance.TICKS_PER_SECOND
    private val nodes = listOf(MarkerId("A"), MarkerId("B"))

    private fun burningAtAMeeting(): GameState {
        val armed = reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(insider))).state
        val fired = Egress.fired(Tick(0), EgressType.Beacon, nodes, Balance.EGRESS_TIMER, pulseFrom = 86_402_000L)
        return armed.copy(
            egress = fired.pausedAt(Tick(10 * second)),
            egressReadyAt = Tick(Balance.EGRESS_COOLDOWN),
            meeting = Meeting.called(Seat(0), MeetingTrigger.MeetingCard).copy(phase = MeetingPhase.Tally),
        )
    }

    private fun closed(state: GameState, at: Long): GameState =
        assertIs<Admission.Admitted>(admit(state, Event.MeetingClosed(Tick(at))), "the meeting's close was refused").reduction.state

    @Test
    fun `a meeting during an Egress pushes the Egress cooldown back by its length`() {
        val after = closed(burningAtAMeeting(), 190 * second)
        assertEquals(Tick(Balance.EGRESS_TIMER + 180 * second), after.egress!!.deadline, "the fixture's pause did not move the deadline")
        assertEquals(
            Tick(Balance.EGRESS_COOLDOWN + 180 * second), after.egressReadyAt,
            "the Egress cooldown kept wall time while its Egress was held",
        )
        assertTrue(after.egressReadyAt > after.egress!!.deadline, "the cooldown no longer outlives its own Egress")
    }

    @Test
    fun `the meeting does not fund the next Egress`() {
        // Contained at 250 s: the Egress is gone, and a second is pressed a second later.
        val contained = closed(burningAtAMeeting(), 190 * second).copy(egress = null)
        val again = admit(contained, Event.EgressFired(Tick(251 * second), insider, EgressType.Tether, nodes, 0L))
        val reduced = assertIs<Admission.Admitted>(again).reduction.state
        assertNull(reduced.egress, "a second Egress fired the moment the first was contained: the meeting funded it")
    }
}
