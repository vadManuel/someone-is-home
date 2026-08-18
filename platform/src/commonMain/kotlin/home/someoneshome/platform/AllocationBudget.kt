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

    /**
     * Design target. About 3x below the cliff's known LOWER bound, not 6x below the cliff — the
     * cliff itself is unlocated.
     */
    const val TARGET_MEGABYTES_PER_SECOND: Double = 0.5

    /**
     * Highest rate at which the late-blackout rate was still indistinguishable from noise.
     *
     * Not "the rate where misses start". FINDINGS explicitly rejects that reading: the 0.99 MB/s
     * run recorded 3 late in 10 000 and the 1.46 MB/s run recorded 0, and 3-versus-0 is not a
     * real difference. Naming a constant after the lower figure would quietly harden a
     * statistical non-result into an engineering fact.
     */
    const val OBSERVED_INDISTINGUISHABLE_FROM_NOISE_TO: Double = 1.46

    /** The cliff is bracketed, not located. Clean below the low end, failing at the high end. */
    const val CLIFF_LOWER_BOUND: Double = 1.46
    const val CLIFF_UPPER_BOUND: Double = 3.00

    /** Rate at which 0.36% of blackouts missed a frame, every one with a collection in window. */
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
