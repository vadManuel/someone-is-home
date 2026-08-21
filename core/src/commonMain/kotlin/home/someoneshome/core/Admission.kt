package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason

/**
 * What the admission gate decided about one event.
 *
 * Two cases and no third. A refusal carries no effects and no state: refusing is not a quiet
 * version of reducing.
 */
sealed interface Admission {
    data class Admitted(val reduction: Reduction<GameState, Effect>) : Admission
    data class Refused(val reason: RefusalReason) : Admission
}

/**
 * **D-066 — the admission gate. Above the rules, never inside them.**
 *
 * The authority refuses events that arrive outside a round before [reduce] sees them.
 *
 * ### Why this is not a branch in `reduce`
 *
 * The obvious fix is banned. `if (!state.armed) return Reduction(state, emptyList())` is rule 1's
 * forbidden shape — an event that produces no effect where an effect was expected *is* the signal,
 * and the silence is the message. Putting the check here instead means [reduce] stays total, never
 * learns the flag exists, and the branch is **provably outside every client-visible path** rather
 * than merely reviewed as being outside one.
 *
 * ### Why it existed as a bug at all
 *
 * `GameState.armed` had two writers and no readers, so the rules processed every event identically
 * whether or not a round existed. Feeding `ContactMade`, `SubroutineReturned` and `MeetingCalled`
 * to `GameState.EMPTY` emitted `AbilityFired`, a verdict and `MeetingOpened`.
 *
 * **Severity, stated honestly: this is a replay bug, not a leak.** Arming *constructs* a fresh
 * state, so pre-arm effects reached clients while the state that would explain them was thrown
 * away — the recording's effect rows and state rows disagreed about a round that had not begun,
 * and the recording is the only debugging instrument this game has. That is why a refusal must be
 * **recorded**: an unrecorded refusal is an invisible drop, and the recording would then disagree
 * with reality in the other direction.
 *
 * ### `RoundArmed` is always admitted
 *
 * It is the event that creates the round. Nothing else gets in without one.
 *
 * ### The meeting's three refusals are here for the same reason the first two are
 *
 * D-133's *no meeting is called during an Egress*, D-121's one-meeting-at-a-time, and *this event
 * belongs to a phase the meeting is not at* are all conditions the whole house can see. Written
 * inside [reduce] each of them would be `if (…) return Reduction(state, emptyList())` — rule 1's
 * forbidden shape, and the one that looks most like hygiene. Written here the branch is provably
 * outside every client-visible path, and the refusal is **recorded**, which an early return never
 * is.
 *
 * **What is deliberately NOT here:** every refusal that would name one seat. *You are revoked*,
 * *you already locked your vote*, *that target is already out* — those are alignment leaks in
 * refusal clothing, and they live in the rules as an effect with the same shape as a success. See
 * [RefusalReason] itself, which draws the line and says where it is.
 */
fun admit(state: GameState, event: Event): Admission = when {
    event is Event.RoundArmed -> Admission.Admitted(reduce(state, event))
    state.ended -> Admission.Refused(RefusalReason.RoundAlreadyEnded)
    !state.armed -> Admission.Refused(RefusalReason.RoundNotArmed)

    // D-133. The meeting card is inert while the house is on fire; the Revoke report is NOT --
    // D-121's one exception, unchanged. Split on the trigger and on nothing else.
    event is Event.MeetingCalled &&
        event.trigger is MeetingTrigger.MeetingCard &&
        state.egressRunning -> Admission.Refused(RefusalReason.EgressRunning)

    // A second call would reset the meeting everybody is already standing at.
    event is Event.MeetingCalled && state.meeting != null ->
        Admission.Refused(RefusalReason.MeetingAlreadyRunning)

    // Everyone in the house is on the same phase of the same meeting, so this tells a client
    // nothing it could not read off the room (D-068).
    phaseOf(event) != null && state.meeting?.phase != phaseOf(event) ->
        Admission.Refused(RefusalReason.WrongMeetingPhase)

    else -> Admission.Admitted(reduce(state, event))
}

/**
 * Which phase of a meeting an event belongs to, or null for an event that is not part of one.
 *
 * **Exhaustive over [Event] on purpose.** A new event does not compile until somebody decides
 * whether it is a meeting event, which is the same discipline the emit schema's `kindOf` uses: the
 * compile error is the prompt, and the alternative is a meeting event that silently belongs to
 * every phase.
 */
private fun phaseOf(event: Event): MeetingPhase? = when (event) {
    is Event.RoundArmed, is Event.MarkerScanned, is Event.SubroutineReturned,
    is Event.RevokeArmed, is Event.ContactMade, is Event.MeetingCalled -> null

    is Event.MeetingCheckedIn -> MeetingPhase.CheckIn
    is Event.ReadyToVoteDeclared, is Event.DiscussionClosed -> MeetingPhase.Discussion
    is Event.VoteSelected, is Event.VoteLocked, is Event.VoteWindowClosed -> MeetingPhase.Vote
    is Event.TallyHalfwayReached, is Event.MeetingClosed -> MeetingPhase.Tally
}
