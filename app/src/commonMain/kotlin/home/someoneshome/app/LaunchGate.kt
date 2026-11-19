package home.someoneshome.app

import androidx.compose.runtime.Composable
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.Screen
import home.someoneshome.ui.ScreenId

/**
 * **What a phone does at launch: play, or be told it cannot** (D-292).
 *
 * The owner's ruling is UWB phones only, with no Bluetooth fallback. A phone without Ultra
 * Wideband is shown [ScreenId.NoUwb] before anything else and is never shown anything after it.
 */
enum class Launch {
    /** The phone cannot range. It gets the gate and nothing is built behind it. */
    Gate,

    /** The phone can range. The variant's own root runs as it always has. */
    Play,
}

/**
 * **The one decision, asked once.** `Entry` reads `uwbSupported()` when the shell builds the root
 * view controller and hands the answer to the variant's root, which routes on it here. Nothing
 * asks again during a round: a phone that launched able to range is not re-examined.
 */
fun launchAs(uwb: Boolean): Launch = if (uwb) Launch.Play else Launch.Gate

/** The gate's panel: the one screen, with nothing behind it to step back to. */
val GATE_PANEL: PanelState = PanelState(screen = ScreenId.NoUwb)

/**
 * **The gate, drawn.** Used by every variant's root, so that no root can draw a different one.
 * It is a bare [Screen] with default actions: no flow, no lobby, no radio and no house exist
 * behind it, so nothing can push the phone off it.
 */
@Composable
fun LaunchGate() {
    Screen(GATE_PANEL)
}
