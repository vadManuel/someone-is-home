package home.someoneshome.platform

/**
 * **A phone: the Nearby Interaction pool, in every round** (D-303, D-308). There is no switch and no
 * Bluetooth round: every phone ranges, and a phone that cannot (NEARBY DEVICES refused, no support,
 * the session limit) says capacity 0 and is paired with nobody.
 */
actual fun nearbyRangingRadio(log: (String) -> Unit): RangingRadio = nearbyInteractionRadio(log)
