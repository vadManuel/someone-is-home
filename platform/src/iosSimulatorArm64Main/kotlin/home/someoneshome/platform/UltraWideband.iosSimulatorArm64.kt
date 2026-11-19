package home.someoneshome.platform

/**
 * **The simulator: true, so it stays the layout and wire rehearsal it is** (D-292).
 *
 * Nearby Interaction on the simulator reports no UWB, and an honest answer would put the launch
 * gate in front of `run-app.sh`, `rehearse.sh` and `e2e.sh`. The simulator signs nothing about the
 * game off (no UWB, Bluetooth, torch, camera or haptics), so a gate there protects no player. This
 * file is in the simulator's source set only: no build that installs on a phone contains it.
 */
actual fun uwbSupported(): Boolean = true
