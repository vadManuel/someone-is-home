package home.someoneshome.harness

import home.someoneshome.model.GameState

/**
 * The result of feeding a recording back through the rules (story 0.4).
 *
 * Divergence names WHAT diverged as well as where, because "effect 4 113 differs" and "the state
 * after event 12 differs" are different bugs and lead to different places.
 */
sealed interface ReplayResult {
    data object Identical : ReplayResult

    data class Diverged(
        val kind: Kind,
        val index: Int,
        val expected: String?,
        val actual: String?,
    ) : ReplayResult {
        enum class Kind { INITIAL_STATE, EFFECT, STATE, REFUSAL }

        override fun toString(): String = when (kind) {
            Kind.INITIAL_STATE ->
                "recording started from a different state: recorded $expected, replayed from $actual"
            Kind.EFFECT ->
                "diverged at effect $index: recording had ${expected ?: "<end>"}, replay produced ${actual ?: "<end>"}"
            Kind.STATE ->
                "diverged in state after event $index: recording had ${expected ?: "<end>"}, replay produced ${actual ?: "<end>"}"
            Kind.REFUSAL ->
                "diverged at refusal $index: recording had ${expected ?: "<end>"}, replay produced ${actual ?: "<end>"}"
        }
    }
}

/**
 * Replays a recording and compares it to what was captured.
 *
 * E0's acceptance criterion, and the only debugging tool this game can have: eight phones in a
 * dark house under enforced silence cannot be inspected any other way, and players who lie by
 * design cannot be asked what happened.
 *
 * Compares three things, in the order a failure is most usefully reported:
 *
 * 1. **The starting state**, because a recording replayed from elsewhere produced a different
 *    outcome and still certified as identical.
 * 2. **The effect stream**, which is what clients would have seen.
 * 3. **Every refusal by the admission gate** (D-066), which is the only record that an event
 *    arrived at all and was turned away. Without these rows a refused event and an event that
 *    never happened are the same recording.
 * 4. **The authority state after every event**, which is what the effect stream is REQUIRED to
 *    hide. Rule 1 makes effects invariant across a landed and a refused revoke, so without state
 *    rows a build with the revoke ability entirely disabled replays clean.
 */
fun replay(initial: GameState, recording: Recording): ReplayResult {
    val startedFrom = Transcript.render(initial)
    if (startedFrom != recording.initialState) {
        return ReplayResult.Diverged(
            ReplayResult.Diverged.Kind.INITIAL_STATE, -1, recording.initialState, startedFrom,
        )
    }

    val (_, fresh) = record(initial, recording.events)

    compare(recording.effectTranscript, fresh.effectTranscript, ReplayResult.Diverged.Kind.EFFECT)
        ?.let { return it }
    compare(recording.stateTranscript, fresh.stateTranscript, ReplayResult.Diverged.Kind.STATE)
        ?.let { return it }
    compare(recording.refusalTranscript, fresh.refusalTranscript, ReplayResult.Diverged.Kind.REFUSAL)
        ?.let { return it }

    return ReplayResult.Identical
}

/** Walks to the longer length, so a truncation is reported where it starts, not at the end. */
private fun compare(
    expected: List<String>,
    actual: List<String>,
    kind: ReplayResult.Diverged.Kind,
): ReplayResult.Diverged? {
    for (i in 0 until maxOf(expected.size, actual.size)) {
        val e = expected.getOrNull(i)
        val a = actual.getOrNull(i)
        if (e != a) return ReplayResult.Diverged(kind, i, e, a)
    }
    return null
}
