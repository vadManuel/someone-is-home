package home.someoneshome.platform

/**
 * **Whether this phone can measure distance over Ultra Wideband** (D-292).
 *
 * The owner's ruling is UWB phones only, with no Bluetooth fallback: a phone that cannot range is
 * told at launch that it cannot play. This is the one question that gate asks, and it is asked
 * once, by the app's root, before anything is built. Nothing asks it again during a round.
 *
 * **Total.** It answers true or false and never throws: anything missing or refused reads as false,
 * which is the gate's closed side.
 *
 * On a phone it is Nearby Interaction's own capability check,
 * `NISession.deviceCapabilities.supportsPreciseDistanceMeasurement` (iOS 16+). The simulator build
 * answers true. The simulator has no UWB, and no Bluetooth, torch, camera or haptics either, and
 * nothing about the game is signed off there; it stays the layout and wire rehearsal it already is
 * (`run-app.sh`, `rehearse.sh`, `e2e.sh`) rather than becoming a second launch gate. A device build
 * cannot carry that answer: it is compiled into the simulator's source set and nowhere else.
 */
expect fun uwbSupported(): Boolean
