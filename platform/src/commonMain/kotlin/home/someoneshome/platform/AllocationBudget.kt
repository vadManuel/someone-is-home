package home.someoneshome.platform

/**
 * The permanent allocation guard (story 1.7c, decision D-063).
 *
 * **Measured on hardware, not assumed.** At the noise floor up to 1.46 MB/s across 30 000
 * blackouts; at 3.00 MB/s, 0.36% of blackouts miss their frame — and a blackout that misses its
 * frame un-anonymises a revoke.
 *
 * **This deliberately measures the WHOLE APP, not the blackout path.** The original mitigation
 * was a no-allocation assertion on the blackout path, and the spike showed it would have stayed
 * green through every failure actually observed: the allocation that drives these collections is
 * on the BLE, motion, effect and recording threads. Every thread spends from one budget.
 */
object AllocationBudget {

    /** Design target. ~6x below the measured cliff, so this is a budget, not a knife edge. */
    const val TARGET_MEGABYTES_PER_SECOND: Double = 0.5

    /** Lowest rate at which GC-linked late blackouts were observed. */
    const val OBSERVED_FIRST_MISSES_AT: Double = 0.99

    /** Rate at which 0.36% of blackouts missed a frame. */
    const val OBSERVED_FAILING_AT: Double = 3.00
}

/**
 * Bytes allocated since process start, derived from the collector's own accounting.
 *
 * Reading this ALLOCATES, so it must never be called on the blackout path — sample it from a
 * monitor thread. That is not a theoretical caution: it is how the spike's GC probe had to be
 * built, because an instrument that allocates becomes a cause of the pause it is measuring.
 */
expect fun allocatedBytesSinceStart(): Long
