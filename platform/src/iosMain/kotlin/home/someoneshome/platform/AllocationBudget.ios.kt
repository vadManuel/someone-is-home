package home.someoneshome.platform

import kotlin.native.runtime.GC

/**
 * Derived from GC epoch deltas: bytes allocated between collections, summed.
 *
 * Kotlin/Native exposes no allocation counter, so this reconstructs one. It is only as current
 * as the last collection — which is adequate for a rate budget sampled over seconds, and useless
 * for anything finer. The spike hit exactly that limit trying to measure per-blackout cost.
 */
@OptIn(ExperimentalStdlibApi::class)
actual fun allocatedBytesSinceStart(): Long {
    val info = GC.lastGCInfo ?: return 0L
    return info.memoryUsageAfter["heap"]?.totalObjectsSizeBytes ?: 0L
}
