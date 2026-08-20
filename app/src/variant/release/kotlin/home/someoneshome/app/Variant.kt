package home.someoneshome.app

import androidx.compose.runtime.Composable
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.Screen
import home.someoneshome.ui.ScreenId

/**
 * The release build: the one that goes to a real round, and the one that carries nothing else.
 *
 * No recording, no cheats, no marker — and none of that is a flag being false. The cheat
 * sources are not in this compilation at all, so reaching for them from here is an unresolved
 * reference, not a code path an audit has to prove dead. `BuildVariantTest` pins these
 * constants; verify-guards.sh proves the compile-out by injecting a cheat reference and
 * watching the release build refuse it.
 */
object BuildVariant {
    const val NAME: String = "release"
    const val RECORDING_ON: Boolean = false
    const val CHEATS_ON: Boolean = false
    const val DEBUG_SURFACES_ON: Boolean = false
    const val MARKER: String = ""
}

/**
 * What the app shows: the device, at boot. Nothing can advance it yet — the transport and the
 * loop do not exist — and this root does not pretend otherwise.
 */
@Composable
fun VariantRoot() {
    Screen(PanelState(screen = ScreenId.Boot))
}
