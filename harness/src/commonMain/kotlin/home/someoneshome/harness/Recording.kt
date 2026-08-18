package home.someoneshome.harness

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.core.reduce

/**
 * A whole round, captured (story 0.3).
 *
 * Holds every event that entered the rules, in order, and every effect they emitted. The events
 * are the *input* and are what replay feeds back; the effects are the *expected output* and are
 * what replay is checked against.
 *
 * **Recordings hold complete authority state** — who is an Insider, real occupancy, real Egress
 * progress. They are debugging artifacts and are gitignored. Never attach one to an issue, and
 * never hand one to a player.
 */
class Recording(
    val events: List<Event>,
    val effectTranscript: List<String>,
) {
    /** The recording as stable text, one row per line. Events first, then a transcript marker. */
    fun toText(): String = buildString {
        appendLine(HEADER)
        events.forEach { appendLine("E ${Transcript.render(it)}") }
        effectTranscript.forEach { appendLine("F $it") }
    }

    companion object {
        const val HEADER = "someone-is-home/recording/1"
    }
}

/**
 * Runs a round and captures it.
 *
 * Nothing here decides anything: it drives [reduce] and writes down what happened. If this
 * function had a rule in it, the recording would be of the harness rather than of the game.
 */
fun record(initial: GameState, events: List<Event>): Pair<GameState, Recording> {
    var state = initial
    val transcript = mutableListOf<String>()
    for (event in events) {
        val reduction = reduce(state, event)
        state = reduction.state
        reduction.effects.forEach { transcript += Transcript.render(it) }
    }
    return state to Recording(events = events, effectTranscript = transcript)
}

/** Every effect a round produced, flattened in emission order. */
fun effectsOf(initial: GameState, events: List<Event>): List<Effect> {
    var state = initial
    val out = mutableListOf<Effect>()
    for (event in events) {
        val reduction = reduce(state, event)
        state = reduction.state
        out += reduction.effects
    }
    return out
}
