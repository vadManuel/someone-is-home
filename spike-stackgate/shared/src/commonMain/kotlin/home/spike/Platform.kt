package home.spike

/**
 * Monotonic clock. Every timestamp in this spike is on one base — the same one CADisplayLink
 * reports its vsync timestamps on — so trigger, draw and vsync times are directly comparable
 * without conversion. Comparing two clocks would be a way to invent a result.
 *
 * Must not allocate: it is called on the blackout path.
 */
expect fun nowNanos(): Long

/** Kotlin/Native's own monotonic base, used only to place GC events on the vsync timeline. */
expect fun runtimeNanos(): Long

/** Schedules [block] on the UI thread after [delayNanos]. Used by the scripted trigger. */
expect fun scheduleOnUiThread(delayNanos: Long, block: () -> Unit)

/**
 * Vsync timeline, from a CADisplayLink on the main run loop.
 *
 * The callback runs on the main run loop, so anything that stalls the main thread — a
 * stop-the-world GC pause included — shows up as a gap between ticks. That makes this both the
 * frame clock and the main-thread stall detector.
 */
expect object Vsync {
    /** Timestamp of the most recent vsync. Plain field read, safe on the blackout path. */
    val lastNanos: Long

    /** Monotonically increasing tick count. */
    val tickIndex: Long

    /** Nominal interval from the display's max refresh rate. */
    val nominalIntervalNanos: Long

    /** Median observed interval for the run. Populated at report time. */
    fun measuredIntervalNanos(): Long

    fun start()
    fun stop()

    /** Gaps longer than 1.5x nominal: (tickIndex, vsyncNanos, gapNanos). */
    fun stallCount(): Int
    /** Stalls seen after the buffer filled. Non-zero means stallCount is a floor, not a count. */
    fun stallsDroppedCount(): Int
    fun stallTickIndex(i: Int): Long
    fun stallAtNanos(i: Int): Long
    fun stallGapNanos(i: Int): Long
    fun resetStalls()
}

/**
 * GC epoch sampler.
 *
 * Reading `GC.lastGCInfo` allocates a GCInfo, so it is polled from a background thread and
 * never from the blackout path. Storing it here is what lets a latency spike be attributed to
 * a collection rather than guessed at.
 */
expect object GcProbe {
    /** Latest epoch seen. Plain field read, safe on the blackout path. */
    val lastEpoch: Long

    fun start()
    fun stop()
    fun reset()

    fun forceCollect()

    fun eventCount(): Int
    fun eventEpoch(i: Int): Long

    /**
     * Kotlin/Native's collector stops the world TWICE per cycle — once around the start of
     * marking and once around the end. The window below spans the whole collection, from the
     * first pause's start to the second pause's end; [eventStwNanos] is the part of it during
     * which the main thread was actually frozen, which is the number that matters here.
     */
    fun eventPauseStartNanos(i: Int): Long
    fun eventPauseEndNanos(i: Int): Long
    fun eventStwNanos(i: Int): Long

    fun eventHeapBeforeBytes(i: Int): Long
    fun eventHeapAfterBytes(i: Int): Long

    /**
     * Bytes allocated across all sampled epochs, derived as
     * sum(heapBefore[i] - heapAfter[i-1]). This is what makes ALLOC_PROBE a measurement
     * rather than an assertion about code that looks allocation-free.
     */
    fun allocatedBytesAcrossEpochs(): Long
}

/** Writes the run's CSV and summary somewhere retrievable off-device. Report time only. */
expect fun writeResults(fileName: String, contents: String): String

/** Pins screen brightness and defeats the idle timer, so camera exposure and runs are stable. */
expect fun prepareDeviceForRun(brightness: Double)

expect fun restoreDeviceAfterRun()

expect fun deviceDescription(): String

/** Launch arguments, so the harness can drive a run without touching the screen. */
expect fun launchArguments(): List<String>

/** Ends the process so the harness can detect completion by the app disappearing. */
expect fun exitProcess(code: Int)

/**
 * Thermal state at report time.
 *
 * A full matrix is ~2 hours of continuous rendering and the phone gets hot. An LTPO panel
 * throttles and the CPU clocks down, so a run late in a sequence is not comparable to one at
 * the start unless this is recorded. A silent thermal confound would read as a dose-response.
 */
expect fun thermalStateName(): String
