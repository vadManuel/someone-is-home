package home.someoneshome.platform

import platform.NearbyInteraction.NISession

/**
 * **A phone: Nearby Interaction's capability check** (D-292).
 *
 * `deviceCapabilities` is iOS 16 and later, and the deployment target is 18, so the class and the
 * property are always there. The call is still wrapped: anything thrown on the way reads as false,
 * the gate's closed side, rather than as a crash at launch.
 */
actual fun uwbSupported(): Boolean =
    try {
        NISession.deviceCapabilities.supportsPreciseDistanceMeasurement
    } catch (_: Throwable) {
        false
    }
