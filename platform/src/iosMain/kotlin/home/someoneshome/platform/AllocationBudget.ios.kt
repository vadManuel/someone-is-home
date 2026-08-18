package home.someoneshome.platform

import kotlin.native.runtime.GC

/**
 * Reconstructs an allocation counter, because Kotlin/Native does not expose one.
 *
 * Each collection reports the heap immediately before and immediately after it ran. The bytes
 * allocated between two collections is therefore `before(n) - after(n-1)`, and summing that
 * across epochs gives a cumulative total. This is the method the story 1.7 spike used to derive
 * every MB/s figure in `FINDINGS.md`.
 *
 * **Three limits, all of which under-report rather than over-report:**
 *
 * 1. **It only advances when a collection happens.** Between collections the value is stale. At
 *    low allocation rates that can be seconds — fine for a rate budget averaged over a round,
 *    useless for anything finer. The spike hit this limit trying to isolate per-blackout cost
 *    and could only produce an upper bound.
 * 2. **Epochs missed between samples are lost.** If two collections occur between polls, the
 *    first one's allocation is never counted. Poll often enough that this stays rare — the
 *    spike sampled every 4 ms.
 * 3. **Reading `GC.lastGCInfo` allocates a `GCInfo`.** Never call this on the blackout path.
 *    Sample it from a monitor thread. An instrument that allocates becomes a cause of the pause
 *    it is measuring, which is not hypothetical: it is why the spike's probe had to be built
 *    this way.
 *
 * Under-reporting matters for how the result is read. A measured rate near the budget means the
 * true rate is at least that, so the budget is breached; a measured rate far below it is weaker
 * evidence than it looks.
 *
 * Not thread-safe. Sample it from one thread.
 */
private object NativeAllocationCounter {
    private var lastEpoch = -1L
    private var lastHeapAfterBytes = 0L
    private var accumulatedBytes = 0L
    private var seenFirstEpoch = false

    /** Epochs actually observed. Zero means this counter has never advanced. */
    var epochsObserved: Long = 0L
        private set

    @OptIn(ExperimentalStdlibApi::class)
    fun sample(): Long {
        val info = GC.lastGCInfo ?: return accumulatedBytes
        if (info.epoch != lastEpoch) {
            val before = info.memoryUsageBefore["heap"]?.totalObjectsSizeBytes ?: 0L
            val after = info.memoryUsageAfter["heap"]?.totalObjectsSizeBytes ?: 0L

            // The FIRST observed epoch establishes a baseline and contributes nothing.
            //
            // Charging `before - 0` would bill the entire pre-existing live heap as "allocated
            // since start" — a large over-report, in a counter whose documentation promises it
            // only ever under-reports. A monitor built on that reports a false breach at
            // startup, gets tuned around, and the tuning then masks a real breach later.
            if (seenFirstEpoch) {
                val grown = before - lastHeapAfterBytes
                if (grown > 0) accumulatedBytes += grown
            }
            seenFirstEpoch = true
            lastHeapAfterBytes = after
            lastEpoch = info.epoch
            epochsObserved++
        }
        return accumulatedBytes
    }
}

actual fun allocatedBytesSinceStart(): Long = NativeAllocationCounter.sample()
