package home.someoneshome.platform

/**
 * **The simulator: no UWB radio** (D-308). The simulator has no Nearby Interaction ranging, so it
 * holds [NoRanging]: its uploads report capacity 0 at full width and the house pairs it with
 * nobody. `run-app.sh`, `rehearse.sh`, `e2e.sh` and the sim hunt play everything that does not read
 * a distance; nothing about UWB, Bluetooth, the torch, the camera or haptics is signed off here.
 * This file is in the simulator's source set only: no build that installs on a phone contains it.
 */
@Suppress("UNUSED_PARAMETER")
actual fun nearbyRangingRadio(log: (String) -> Unit): RangingRadio = NoRanging
