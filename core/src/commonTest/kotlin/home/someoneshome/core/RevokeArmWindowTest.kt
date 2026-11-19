package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **An armed Revoke lasts 20 s, or until a meeting is called, whichever comes first; the lapse is
 * silent** (the owner's 2026-11-18 ruling, Q1/Q2 option C with window option a; D-280).
 *
 * Through `admit`, the one gate the house uses. Eight seats and two Insiders, so a Revoke leaves
 * the round running and the second Insider is there to show that the call clears every seat.
 */
class RevokeArmWindowTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val first = Seat(1)
    private val second = Seat(4)
    private val ready = Balance.REVOKE_COOLDOWN / 2

    private fun round(): GameState =
        reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = listOf(first, second))).state

    private fun put(state: GameState, event: Event): Admission.Admitted {
        val admission = admit(state, event)
        assertIs<Admission.Admitted>(admission, "the fixture's $event was refused: $admission")
        return admission
    }

    @Test
    fun `the window is the owner's twenty seconds`() {
        assertEquals(20L * Balance.TICKS_PER_SECOND, Balance.REVOKE_ARM_WINDOW)
    }

    /** The last tick inside the window lands; the tick the window closes on is an unarmed touch. */
    @Test
    fun `a touch inside the window lands and a touch at its close is an unarmed touch`() {
        val armed = put(round(), Event.RevokeArmed(Tick(ready), first)).reduction.state
        val close = ready + Balance.REVOKE_ARM_WINDOW

        val inside = put(armed, Event.ContactMade(Tick(close - 1), first, Seat(2)))
        assertTrue(inside.reduction.state.isRevoked(Seat(2)), "a touch inside the window landed nothing")

        val after = put(armed, Event.ContactMade(Tick(close), first, Seat(2)))
        assertFalse(after.reduction.state.isRevoked(Seat(2)), "an arm landed after its 20 s window closed")
        // The Insider is answered exactly as a Resident's touch is: the lapse told nobody anything.
        assertEquals(
            listOf(
                Effect.ContactAnswered(first),
                Effect.RevokeLanded(first, haptic = null),
                Effect.ContactAnswered(Seat(2)),
                Effect.RevokeLanded(Seat(2), haptic = null),
            ),
            after.reduction.effects,
            "a touch after the window was answered as anything but an unarmed touch",
        )
        // The cooldown spent at the arm still runs its full sixty seconds: a botched stalk costs one.
        assertEquals(
            Tick(ready + Balance.REVOKE_COOLDOWN),
            after.reduction.state.cooldownFor(first, home.someoneshome.model.InsiderAbility.Revoke)!!.readyAt,
        )
    }

    /** Every seat's arm ends at the call, and the call's effects are the same armed or not. */
    @Test
    fun `a meeting call ends every armed Revoke in silence`() {
        var s = put(round(), Event.RevokeArmed(Tick(ready), first)).reduction.state
        s = put(s, Event.RevokeArmed(Tick(ready), second)).reduction.state
        assertEquals(listOf(first, second), s.cooldownArmed, "the fixture armed nobody")

        val call = Event.MeetingCalled(Tick(ready + 1), Seat(0), MeetingTrigger.MeetingCard)
        val called = put(s, call)
        assertEquals(emptyList(), called.reduction.state.cooldownArmed, "a meeting call left an armed Revoke standing")
        assertFalse(called.reduction.state.armedAt(first, Tick(ready + 1)), "the house still treats a seat as armed after the call")

        // Silent: the same call against the same round with nobody armed says exactly the same things.
        val unarmed = put(round(), call)
        assertEquals(unarmed.reduction.effects, called.reduction.effects, "clearing the arms at the call emitted something")
    }

    /** The arm cleared at the call cannot land after the meeting, inside what was its window. */
    @Test
    fun `an arm from before a meeting lands nothing after it`() {
        var s = put(round(), Event.RevokeArmed(Tick(ready), first)).reduction.state
        val at = ready + 1
        s = put(s, Event.MeetingCalled(Tick(at), Seat(0), MeetingTrigger.MeetingCard)).reduction.state
        for (seat in seats) s = put(s, Event.MeetingCheckedIn(Tick(at + 1), seat)).reduction.state
        if (s.meeting?.phase == home.someoneshome.model.MeetingPhase.Discussion) s = put(s, Event.DiscussionClosed(Tick(at + 2))).reduction.state
        for (seat in s.livingSeats) {
            s = put(s, Event.VoteSelected(Tick(at + 3), seat, null)).reduction.state
            s = put(s, Event.VoteLocked(Tick(at + 3), seat)).reduction.state
        }
        if (s.meeting?.phase == home.someoneshome.model.MeetingPhase.Vote) s = put(s, Event.VoteWindowClosed(Tick(at + 4))).reduction.state
        s = put(s, Event.MeetingClosed(Tick(at + 6))).reduction.state
        assertEquals(null, s.meeting, "the fixture's meeting never closed")

        // Still inside the 20 s the arm opened, but past the call that ended it.
        val touch = put(s, Event.ContactMade(Tick(at + 7), first, Seat(2)))
        assertFalse(touch.reduction.state.isRevoked(Seat(2)), "an arm survived a meeting call and landed after it")
    }
}
