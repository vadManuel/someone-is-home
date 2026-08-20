package home.someoneshome.app

import androidx.compose.runtime.Composable

/**
 * The playtest build: what the monthly session runs.
 *
 * Recording on, cheats on, debug surfaces compiled out, and a permanent visible marker — the
 * epic's own definition, verbatim. The marker is the point, not decoration: it is the only
 * thing standing between "we played a round" and "we played a round on a build that could
 * record it", and a build that could be mistaken for release across a dark room is a build
 * whose recordings nobody thought to collect.
 *
 * `RECORDING_ON` has no consumer yet — the loop and transport it gates arrive with stories
 * 0.8/0.9 — but the contract is pinned now so the wiring lands against a value that already
 * has a test.
 */
object BuildVariant {
    const val NAME: String = "playtest"
    const val RECORDING_ON: Boolean = true
    const val CHEATS_ON: Boolean = true
    const val DEBUG_SURFACES_ON: Boolean = false
    const val MARKER: String = "PLAYTEST"
}

@Composable
fun VariantRoot() {
    CheatRoot()
}
