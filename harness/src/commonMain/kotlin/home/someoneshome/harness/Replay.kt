package home.someoneshome.harness

import home.someoneshome.model.GameState

/**
 * The result of feeding a recording back through the rules (story 0.4).
 *
 * Divergence is reported with the index and both sides, because "the round did not replay" is
 * useless and "effect 4,113 was AbilityFired(cooldownStarted=false) where the recording says
 * true" is a bug report.
 */
sealed interface ReplayResult {
    data object Identical : ReplayResult

    data class Diverged(
        val index: Int,
        val expected: String?,
        val actual: String?,
    ) : ReplayResult {
        override fun toString(): String =
            "diverged at effect $index: recording had ${expected ?: "<end>"}, replay produced ${actual ?: "<end>"}"
    }
}

/**
 * Replays a recording and compares it to what was captured.
 *
 * This is E0's acceptance criterion in one function: a 25-minute eight-player round must come
 * back byte-identically. It is also the only debugging tool this game can have — eight phones in
 * a dark house with enforced silence cannot be inspected any other way, and players who lie by
 * design cannot be asked what happened.
 *
 * Compares the full transcript rather than stopping at the first mismatch count, so a divergence
 * that changes length is reported at the point it started rather than at the end.
 */
fun replay(initial: GameState, recording: Recording): ReplayResult {
    val (_, fresh) = record(initial, recording.events)
    val expected = recording.effectTranscript
    val actual = fresh.effectTranscript

    val longest = maxOf(expected.size, actual.size)
    for (i in 0 until longest) {
        val e = expected.getOrNull(i)
        val a = actual.getOrNull(i)
        if (e != a) return ReplayResult.Diverged(i, e, a)
    }
    return ReplayResult.Identical
}
