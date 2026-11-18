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

/*
 * **There is no in-app allocation counter, on purpose** (D-276).
 *
 * `allocatedBytesSinceStart()` used to be declared here, reconstructed from the collector's
 * heap-before minus heap-after-the-last-collection. Under the allocator the app ships with, that
 * figure moves in the allocator's pages (64 to 256 KiB), not in objects, and misses blocks reused
 * between collections: one kilobyte of arrays read as 1.31 MB, and the same work read anywhere from
 * 52 to 437 KB/s. It had no caller, and a figure that wrong would be believed by the first one.
 * Kotlin/Native offers nothing honest in its place for the shipping allocator, so nothing replaces it
 * here.
 *
 * **Where the number comes from instead** (D-276):
 * - **A whole-app rate, per seat, on a round:** a measurement build of the app (objects from
 *   malloc, collections only when asked, one forced every second and the heap's own byte count read
 *   either side), validated within 2% by a known allocation, run on every seat of a hunt round
 *   (`tools/sim-hunt/hunt.sh --all-env`). It changes the allocator, so it is never the shipping app.
 * - **Per operation, as a regression guard:** the JVM's exact per-thread counter
 *   (`UploadAllocationTest` and its kind), with ceilings proven red on the code before D-276.
 */
