package home.someoneshome.app

import androidx.compose.runtime.Composable

/**
 * The debug build: the development default, and the only variant with debug surfaces compiled
 * in. None exist yet — the flag is pinned now so that when the first one lands it lands behind
 * a value the playtest build already excludes, rather than behind a check somebody remembers
 * to add.
 */
object BuildVariant {
    const val NAME: String = "debug"
    const val RECORDING_ON: Boolean = true
    const val CHEATS_ON: Boolean = true
    const val DEBUG_SURFACES_ON: Boolean = true
    const val MARKER: String = "DEBUG"
}

@Composable
fun VariantRoot() {
    CheatRoot()
}
