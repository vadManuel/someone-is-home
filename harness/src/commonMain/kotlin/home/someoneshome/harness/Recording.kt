package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState

/**
 * A whole round, captured (story 0.3).
 *
 * Holds the events that entered the rules, the effects they emitted, **and the authority state
 * after each one**. The events are the input; the effects and the states are jointly the
 * expected output.
 *
 * **The state rows are not redundant with the effects.** Rule 1 requires the effect stream to be
 * identical whether a revoke landed or not, so for the transition that matters most, effects
 * carry no information about what actually happened. A recording that stored only effects
 * certified a build where the revoke ability did nothing as replaying correctly.
 *
 * **The initial state is recorded too**, so a recording is self-contained. Without it, replaying
 * from a different starting state produced a different outcome and still reported Identical.
 *
 * **Recordings hold complete authority state** — who is an Insider, real occupancy, real Egress
 * progress. Debugging artifacts, gitignored. Never attach one to an issue or hand one to a
 * player.
 */
class Recording(
    val initialState: String,
    val events: List<Event>,
    val effectTranscript: List<String>,
    val stateTranscript: List<String>,
    /**
     * Events the admission gate refused (D-066), in arrival order.
     *
     * **Recorded, because an unrecorded refusal is an invisible drop.** The gate exists to stop
     * the recording's effect rows and state rows disagreeing about a round that had not begun; a
     * gate that silently swallowed events would fix that disagreement by creating a different
     * one, where the recording no longer says what reached the rules at all.
     */
    val refusalTranscript: List<String>,
) {
    fun toText(): String = buildString {
        appendLine(HEADER)
        appendLine("I $initialState")
        events.forEach { appendLine("E ${Transcript.render(it)}") }
        refusalTranscript.forEach { appendLine("X $it") }
        effectTranscript.forEach { appendLine("F $it") }
        stateTranscript.forEach { appendLine("S $it") }
    }

    companion object {
        // 6: the meeting engine. `VoteCast(voter, target)` became `VoteSelected` plus `VoteLocked`
        // -- the *changeable until the clock ends* model D-117 superseded, split into the live tap
        // and the irrevocable READY -- and six more meeting events arrived with them. The state row
        // gained the whole meeting, both out lists and the Egress flag. A version 5 recording holds
        // an event this build cannot construct, and its state rows are missing the ballots a tally
        // is computed from.
        //
        // 5: the verdict spine. `SubroutineCompleted` became `SubroutineReturned` and carries the
        // entry; the state row gained the work order.
        const val HEADER = "someone-is-home/recording/6"
    }
}

/**
 * The one walk through the authority, shared by everything in this module that runs a round.
 *
 * **Goes through [admit], not [home.someoneshome.core.reduce].** The admission gate is part of
 * the authority, so a harness that called the rules directly would be exercising a path no real
 * client can reach — and would have gone on certifying pre-arm effects as correct.
 *
 * [onStep] receives the event's index, the state AFTER the event, and the effects it emitted.
 * [onRefusal] receives the index, the event, and why the gate refused it. **Both carry the index
 * into the original event list, not a count of how many got through** — a refused event still
 * occupies a position, and a snapshot that named a different one could not be regenerated. Every capture —
 * the recording, the effect list, the per-client transcripts — is a different observer of the
 * same walk, so no two of them can disagree about what the round did.
 */
internal inline fun drive(
    initial: GameState,
    events: List<Event>,
    // Before `onStep` so that `onStep` stays the trailing lambda: a caller that only cares about
    // effects reads as `drive(initial, events) { after, emitted -> ... }` and cannot accidentally
    // bind its lambda to the refusal hook instead.
    onRefusal: (Int, Event, home.someoneshome.model.RefusalReason) -> Unit = { _, _, _ -> },
    onStep: (Int, GameState, List<Effect>) -> Unit,
): GameState {
    var state = initial
    events.forEachIndexed { index, event ->
        when (val admission = admit(state, event)) {
            is Admission.Admitted -> {
                state = admission.reduction.state
                onStep(index, state, admission.reduction.effects)
            }
            // No state change and no effects. Refusing is not a quiet version of reducing.
            is Admission.Refused -> onRefusal(index, event, admission.reason)
        }
    }
    return state
}

/**
 * Runs a round and captures it.
 *
 * Nothing here decides anything: it drives [reduce] and writes down what happened. A rule in
 * this function would mean the recording was of the harness rather than of the game.
 */
fun record(initial: GameState, events: List<Event>): Pair<GameState, Recording> {
    val effects = mutableListOf<String>()
    val states = mutableListOf<String>()
    val refusals = mutableListOf<String>()
    val state = drive(
        initial, events,
        onRefusal = { index, event, reason -> refusals += Transcript.render(index, event, reason) },
    ) { _, after, emitted ->
        emitted.forEach { effects += Transcript.render(it) }
        states += Transcript.render(after)
    }
    return state to Recording(
        initialState = Transcript.render(initial),
        events = events,
        effectTranscript = effects,
        stateTranscript = states,
        refusalTranscript = refusals,
    )
}

/** Every effect a round produced, in emission order. Shares the driver, so it cannot drift. */
fun effectsOf(initial: GameState, events: List<Event>): List<Effect> {
    val out = mutableListOf<Effect>()
    drive(initial, events) { _, _, emitted -> out += emitted }
    return out
}
