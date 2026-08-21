package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
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
 */
fun admit(state: GameState, event: Event): Admission = when {
    event is Event.RoundArmed -> Admission.Admitted(reduce(state, event))
    state.ended -> Admission.Refused(RefusalReason.RoundAlreadyEnded)
    !state.armed -> Admission.Refused(RefusalReason.RoundNotArmed)
    else -> Admission.Admitted(reduce(state, event))
}
