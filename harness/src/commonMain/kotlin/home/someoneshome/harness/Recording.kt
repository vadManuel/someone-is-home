package home.someoneshome.harness

import home.someoneshome.core.reduce
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
) {
    fun toText(): String = buildString {
        appendLine(HEADER)
        appendLine("I $initialState")
        events.forEach { appendLine("E ${Transcript.render(it)}") }
        effectTranscript.forEach { appendLine("F $it") }
        stateTranscript.forEach { appendLine("S $it") }
    }

    companion object {
        const val HEADER = "someone-is-home/recording/3"
    }
}

/**
 * The one walk through the rules, shared by everything in this module that runs a round.
 *
 * [onStep] receives the state AFTER the event and the effects that event emitted. Every capture
 * — the recording, the effect list, the per-client transcripts — is a different observer of the
 * same walk, so no two of them can disagree about what the round did.
 */
internal inline fun drive(
    initial: GameState,
    events: List<Event>,
    onStep: (GameState, List<Effect>) -> Unit,
): GameState {
    var state = initial
    for (event in events) {
        val reduction = reduce(state, event)
        state = reduction.state
        onStep(state, reduction.effects)
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
    val state = drive(initial, events) { after, emitted ->
        emitted.forEach { effects += Transcript.render(it) }
        states += Transcript.render(after)
    }
    return state to Recording(
        initialState = Transcript.render(initial),
        events = events,
        effectTranscript = effects,
        stateTranscript = states,
    )
}

/** Every effect a round produced, in emission order. Shares the driver, so it cannot drift. */
fun effectsOf(initial: GameState, events: List<Event>): List<Effect> {
    val out = mutableListOf<Effect>()
    drive(initial, events) { _, emitted -> out += emitted }
    return out
}
