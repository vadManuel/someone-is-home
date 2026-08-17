package home.spike

import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.native.runtime.GC
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.writeToFile
import platform.QuartzCore.CACurrentMediaTime
import platform.QuartzCore.CADisplayLink
import platform.QuartzCore.CAFrameRateRangeMake
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.system.getTimeNanos

/**
 * One clock for everything.
 *
 * CACurrentMediaTime is the same base CADisplayLink reports its vsync timestamps on, so
 * trigger, draw and vsync times are directly comparable with no conversion. Two clocks is a
 * way to invent a result — a constant offset between them reads exactly like latency.
 *
 * No allocation: this is called on the blackout path.
 */
actual fun nowNanos(): Long = (CACurrentMediaTime() * 1_000_000_000.0).toLong()

/**
 * The base GCInfo's timestamps are on.
 *
 * Deprecated in favour of TimeSource, but TimeSource cannot hand back a raw absolute value on
 * this base, and a raw absolute is exactly what is needed to place a GC pause on the same
 * timeline as a vsync gap. The alternative — assuming the two bases coincide — is the kind of
 * assumption that turns into a fabricated correlation.
 */
@Suppress("DEPRECATION_ERROR")
actual fun runtimeNanos(): Long = getTimeNanos()

actual fun scheduleOnUiThread(delayNanos: Long, block: () -> Unit) {
    dispatch_after(dispatch_time(0uL, delayNanos), dispatch_get_main_queue()) { block() }
}

// ---------------------------------------------------------------------------------------
// Vsync
// ---------------------------------------------------------------------------------------

private const val STALL_CAPACITY = 8192
private const val INTERVAL_BUCKETS = 5000       // 0.01 ms per bucket, up to 50 ms
private const val INTERVAL_BUCKET_NANOS = 10_000L

private class DisplayLinkTarget : NSObject() {
    @ObjCAction
    fun onTick(sender: CADisplayLink) {
        Vsync.tick((sender.timestamp * 1_000_000_000.0).toLong())
    }
}

actual object Vsync {
    private var target: DisplayLinkTarget? = null
    private var link: CADisplayLink? = null

    private var _lastNanos = 0L
    private var _tickIndex = 0L

    private val intervalHistogram = IntArray(INTERVAL_BUCKETS)
    private var intervalSamples = 0

    private val stallTicks = LongArray(STALL_CAPACITY)
    private val stallAt = LongArray(STALL_CAPACITY)
    private val stallGaps = LongArray(STALL_CAPACITY)
    private var stalls = 0
    private var stallsDropped = 0
    private var baselineNanos = 0L

    actual val lastNanos: Long get() = _lastNanos
    actual val tickIndex: Long get() = _tickIndex

    actual val nominalIntervalNanos: Long
        get() {
            val fps = UIScreen.mainScreen.maximumFramesPerSecond.toInt()
            return if (fps > 0) 1_000_000_000L / fps else 16_666_667L
        }

    actual fun start() {
        if (link != null) return
        val t = DisplayLinkTarget()
        val l = CADisplayLink.displayLinkWithTarget(t, NSSelectorFromString("onTick:"))

        // preferredFrameRateRange, NOT the deprecated preferredFramesPerSecond = 0.
        //
        // "0" is documented as "the fastest the display supports" and on a ProMotion panel it
        // delivers 60. The first VOLUME run was measured with it, and the result was an
        // instrument sampling at exactly half the rate Compose was rendering at: every trial
        // reported span 0 because our link never ticked between trigger and draw, and every
        // ordinary frame tripped the stall detector. Neither number meant anything, and both
        // looked plausible.
        val fps = UIScreen.mainScreen.maximumFramesPerSecond.toFloat()
        l.preferredFrameRateRange = CAFrameRateRangeMake(fps, fps, fps)
        l.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        target = t
        link = l
    }

    actual fun stop() {
        link?.invalidate()
        link = null
        target = null
    }

    /**
     * Called on the main run loop, once per vsync.
     *
     * Because it is on the main run loop, anything that blocks the main thread — a
     * stop-the-world GC pause included — shows up here as a skipped tick. That is what makes
     * this both the frame clock and the main-thread stall detector.
     */
    fun tick(vsyncNanos: Long) {
        val previous = _lastNanos
        _lastNanos = vsyncNanos
        _tickIndex++

        if (previous != 0L) {
            val gap = vsyncNanos - previous
            val bucket = (gap / INTERVAL_BUCKET_NANOS).toInt()
            if (bucket in 0 until INTERVAL_BUCKETS) {
                intervalHistogram[bucket]++
                intervalSamples++
            }
            // Compared against what the link ACTUALLY does, not against the display's rated
            // maximum. Using the rated maximum meant that when the link ran at half rate,
            // every ordinary frame was a "stall" — 8192 of them, saturating the buffer, while
            // the real signal was that there was no signal.
            //
            // The baseline is re-derived periodically rather than fixed once, because an LTPO
            // panel can genuinely change rate mid-run when the device warms up, and that is a
            // finding rather than something to normalise away.
            // Wait for a real sample population before trusting the median.
            //
            // Seeding it on the first tick meant the baseline was the median of ONE arbitrary
            // gap, held until tick 1024 — so if that first gap was short, every ordinary frame
            // for the next thousand ticks tripped the detector. Run 2 reported 1023 stalls, of
            // which 1022 had a gap of exactly one frame interval. The true count was 1.
            if (intervalSamples >= 240 && (_tickIndex % 1024 == 0L || baselineNanos == 0L)) {
                val median = measuredIntervalNanos()
                if (median > 0L) baselineNanos = median
            }
            val baseline = if (baselineNanos > 0L) baselineNanos else nominalIntervalNanos
            if (gap > baseline + baseline / 2) {
                if (stalls < STALL_CAPACITY) {
                    val i = stalls
                    stallTicks[i] = _tickIndex
                    stallAt[i] = vsyncNanos
                    stallGaps[i] = gap
                    stalls = i + 1
                } else {
                    // Never silently truncate: a saturated buffer reporting its capacity reads
                    // exactly like a real count.
                    stallsDropped++
                }
            }
        }

        GateEngine.onVsync(vsyncNanos, _tickIndex)
    }

    actual fun measuredIntervalNanos(): Long {
        if (intervalSamples == 0) return 0L
        val half = intervalSamples / 2
        var seen = 0
        for (b in 0 until INTERVAL_BUCKETS) {
            seen += intervalHistogram[b]
            if (seen >= half) return b * INTERVAL_BUCKET_NANOS + INTERVAL_BUCKET_NANOS / 2
        }
        return 0L
    }

    actual fun stallCount(): Int = stalls
    actual fun stallsDroppedCount(): Int = stallsDropped
    actual fun stallTickIndex(i: Int): Long = stallTicks[i]
    actual fun stallAtNanos(i: Int): Long = stallAt[i]
    actual fun stallGapNanos(i: Int): Long = stallGaps[i]

    actual fun resetStalls() {
        stalls = 0
        stallsDropped = 0
        baselineNanos = 0L
        intervalSamples = 0
        for (i in intervalHistogram.indices) intervalHistogram[i] = 0
    }
}

// ---------------------------------------------------------------------------------------
// GC probe
// ---------------------------------------------------------------------------------------

private const val GC_CAPACITY = 16384

@OptIn(ExperimentalStdlibApi::class)
actual object GcProbe {
    private var scope: CoroutineScope? = null

    private var _lastEpoch = 0L
    actual val lastEpoch: Long get() = _lastEpoch

    private val epochs = LongArray(GC_CAPACITY)
    private val pauseStart = LongArray(GC_CAPACITY)
    private val pauseEnd = LongArray(GC_CAPACITY)
    private val stw = LongArray(GC_CAPACITY)
    private val heapBefore = LongArray(GC_CAPACITY)
    private val heapAfter = LongArray(GC_CAPACITY)
    private var events = 0

    actual fun start() {
        if (scope != null) return
        val s = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = s
        s.launch {
            // Polled off-thread on purpose: reading GC.lastGCInfo ALLOCATES a GCInfo, so
            // sampling it from the blackout path would make the instrument a cause of the
            // pause it is trying to observe.
            while (isActive) {
                sample()
                delay(4)
            }
        }
    }

    actual fun stop() {
        scope?.cancel()
        scope = null
    }

    actual fun reset() {
        events = 0
        _lastEpoch = 0L
    }

    actual fun forceCollect() {
        GC.collect()
    }

    private fun sample() {
        val info = GC.lastGCInfo ?: return
        if (info.epoch == _lastEpoch) return
        if (events < GC_CAPACITY) {
            val i = events
            epochs[i] = info.epoch

            // Two stop-the-world pauses per cycle: one around the start of marking, one around
            // the end. Summing them is the time the main thread was actually frozen — the
            // stretch between them is concurrent and does not freeze the frame.
            //
            // The second pause is nullable: this info can be published with the cycle still in
            // flight. Treated as zero rather than skipped, because dropping those rows would
            // quietly bias the pause distribution towards the short ones.
            val firstStart = info.firstPauseStartTimeNs
            val first = info.firstPauseEndTimeNs - firstStart
            val secondStart = info.secondPauseStartTimeNs
            val secondEnd = info.secondPauseEndTimeNs
            val second = if (secondStart != null && secondEnd != null) secondEnd - secondStart else 0L
            stw[i] = (if (first > 0) first else 0L) + (if (second > 0) second else 0L)

            pauseStart[i] = runtimeToVsyncBase(firstStart)
            pauseEnd[i] = runtimeToVsyncBase(secondEnd ?: info.firstPauseEndTimeNs)
            heapBefore[i] = info.memoryUsageBefore["heap"]?.totalObjectsSizeBytes ?: 0L
            heapAfter[i] = info.memoryUsageAfter["heap"]?.totalObjectsSizeBytes ?: 0L
            events = i + 1
        }
        // Published last, so a reader that sees a new epoch also sees its row.
        _lastEpoch = info.epoch
    }

    /**
     * GCInfo times are on Kotlin/Native's monotonic base; everything else here is on
     * CACurrentMediaTime. The offset is measured once so GC pauses can be placed on the same
     * timeline as the vsync gaps rather than eyeballed against them.
     */
    private val baseOffsetNanos: Long = nowNanos() - runtimeNanos()

    private fun runtimeToVsyncBase(runtimeNs: Long): Long = runtimeNs + baseOffsetNanos

    actual fun eventCount(): Int = events
    actual fun eventEpoch(i: Int): Long = epochs[i]
    actual fun eventPauseStartNanos(i: Int): Long = pauseStart[i]
    actual fun eventPauseEndNanos(i: Int): Long = pauseEnd[i]
    actual fun eventStwNanos(i: Int): Long = stw[i]
    actual fun eventHeapBeforeBytes(i: Int): Long = heapBefore[i]
    actual fun eventHeapAfterBytes(i: Int): Long = heapAfter[i]

    actual fun allocatedBytesAcrossEpochs(): Long {
        var total = 0L
        for (i in 1 until events) {
            val grew = heapBefore[i] - heapAfter[i - 1]
            if (grew > 0) total += grew
        }
        return total
    }
}

// ---------------------------------------------------------------------------------------
// Device
// ---------------------------------------------------------------------------------------

private var savedBrightness = -1.0

actual fun prepareDeviceForRun(brightness: Double) {
    if (savedBrightness < 0) savedBrightness = UIScreen.mainScreen.brightness
    // Pinned so the camera pass has a stable exposure and so auto-brightness cannot move the
    // amber level between runs.
    UIScreen.mainScreen.brightness = brightness
    UIApplication.sharedApplication.idleTimerDisabled = true
    Vsync.start()
    GcProbe.start()
}

actual fun restoreDeviceAfterRun() {
    // GcProbe is NOT stopped here — the closing bracket collection has just been requested and
    // the poller has to still be alive to record it. The report path stops it once it has.
    UIApplication.sharedApplication.idleTimerDisabled = false
    if (savedBrightness >= 0) UIScreen.mainScreen.brightness = savedBrightness
    // Vsync is deliberately left running: it costs one no-op callback per frame and keeps the
    // stall log continuous across the report screen.
}

actual fun deviceDescription(): String {
    val d = UIDevice.currentDevice
    val fps = UIScreen.mainScreen.maximumFramesPerSecond
    return "${d.name} · ${d.systemName} ${d.systemVersion} · ${fps} Hz max"
}

actual fun writeResults(fileName: String, contents: String): String {
    val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val dir = dirs.firstOrNull() as? String ?: return "(no documents dir)"
    val path = "$dir/$fileName"
    @Suppress("CAST_NEVER_SUCCEEDS")
    (contents as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
    return path
}
