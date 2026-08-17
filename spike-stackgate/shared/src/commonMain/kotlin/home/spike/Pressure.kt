package home.spike

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Manufactured allocation pressure — the thing that makes 1.7b a test rather than a formality.
 *
 * A GC pause only happens when there is garbage to collect. A minimal spike allocates almost
 * nothing, never triggers a collection inside the test window, returns a clean result, and
 * would have you conclude Kotlin/Native GC is a non-issue. The real app allocates constantly,
 * so the spike has to as well — and it has to do it ON OTHER THREADS while the blackout fires,
 * because that is the shape of the real thing: BLE callbacks, motion samples, effect objects
 * and recording writes are not on the UI thread.
 *
 * The profile is modelled on what the real app will actually do, not on whatever allocates
 * fastest. A flood of identical short-lived garbage is easy and produces only young
 * collections; each loop keeps a retained ring so objects survive a cycle and force the major
 * collections that produce the long pauses.
 */
@OptIn(ExperimentalAtomicApi::class)
object Pressure {

    private class MotionSample(
        val timestampNanos: Long,
        val ax: Double, val ay: Double, val az: Double,
        val gx: Double, val gy: Double, val gz: Double,
    )

    private class BleAdvert(
        val deviceId: String,
        val rssi: Int,
        val payload: ByteArray,
        val timestampNanos: Long,
    )

    private class EffectEnvelope(
        val seq: Long,
        val kind: String,
        val targets: List<Int>,
        val payload: String,
    )

    /**
     * Per-loop retained ring. Deliberately NOT shared between loops: a shared ring is a data
     * race across worker threads, and a torn write in the instrument is exactly the kind of
     * thing that would later be mistaken for a finding.
     */
    private class RetainRing(size: Int) {
        private val slots = arrayOfNulls<Any>(size)
        private var cursor = 0
        fun retain(o: Any) {
            slots[cursor] = o
            cursor = (cursor + 1) % slots.size
        }
        fun clear() {
            for (i in slots.indices) slots[i] = null
            cursor = 0
        }
    }

    private var scope: CoroutineScope? = null

    /** Self-reported estimate. The authoritative number comes from GcProbe heap deltas. */
    val estimatedBytes = AtomicLong(0L)

    var level: PressureLevel = PressureLevel.OFF
        private set

    var cpuOnly: Boolean = false
        private set

    /** Preallocated sinks for the CPU-only mode, so it does the work without making garbage. */
    private val sinkDoubles = DoubleArray(4096)
    private val sinkBytes = ByteArray(4096)

    private var startNanos = 0L
    private var elapsedAtStopNanos = 0L
    private val rings = Array(4) { RetainRing(512) }

    fun start(level: PressureLevel, cpuOnly: Boolean = false) {
        stop()
        this.level = level
        this.cpuOnly = cpuOnly
        estimatedBytes.store(0L)
        elapsedAtStopNanos = 0L
        startNanos = nowNanos()
        if (level == PressureLevel.OFF) return

        val s = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = s
        val scale = level.scale
        if (cpuOnly) {
            // Same thread count, same loop counts, same arithmetic and memory traffic —
            // written into preallocated buffers instead of fresh objects.
            s.launch { cpuLoop(scale, 8, 10) }
            s.launch { cpuLoop(scale, 6, 33) }
            s.launch { cpuLoop(scale, 4, 100) }
            s.launch { cpuLoop(scale, 2, 100) }
        } else {
            s.launch { motionLoop(scale, rings[0]) }
            s.launch { bleLoop(scale, rings[1]) }
            s.launch { effectLoop(scale, rings[2]) }
            s.launch { recordingLoop(scale, rings[3]) }
        }
    }

    fun stop() {
        if (startNanos != 0L && elapsedAtStopNanos == 0L) {
            elapsedAtStopNanos = nowNanos() - startNanos
        }
        scope?.cancel()
        scope = null
        for (r in rings) r.clear()
    }

    fun elapsedNanos(): Long =
        if (elapsedAtStopNanos != 0L) elapsedAtStopNanos
        else if (startNanos != 0L) nowNanos() - startNanos
        else 0L

    /** Estimated allocation rate in MB/s, so the label is backed by a number. */
    fun estimatedMegabytesPerSecond(): Double {
        val secs = elapsedNanos() / 1e9
        if (secs <= 0.0) return 0.0
        return (estimatedBytes.load() / (1024.0 * 1024.0)) / secs
    }

    /** The confound control's worker: identical shape to the allocating loops, zero garbage. */
    private suspend fun CoroutineScope.cpuLoop(scale: Double, base: Int, periodMillis: Long) {
        val n = (base * scale).toInt().coerceAtLeast(1)
        var acc = 0.0
        while (isActive) {
            for (i in 0 until n) {
                val k = i and 4095
                acc += sinkDoubles[k] * 1.000001 + 0.1
                sinkDoubles[k] = acc
                sinkBytes[k] = (acc.toInt() and 0xFF).toByte()
            }
            delay(periodMillis)
        }
    }

    /** 100 Hz, 8 samples per tick — the motion budget's real sampling rate. */
    private suspend fun CoroutineScope.motionLoop(scale: Double, ring: RetainRing) {
        val n = (8 * scale).toInt().coerceAtLeast(1)
        while (isActive) {
            for (i in 0 until n) {
                val s = MotionSample(nowNanos(), 0.1, 0.2, 9.8, 0.01, 0.02, 0.03)
                estimatedBytes.addAndFetch(72L)
                if (i == 0) ring.retain(s)
            }
            delay(10)
        }
    }

    /** ~30 Hz adverts, each a byte payload plus a wrapper plus a string id. */
    private suspend fun CoroutineScope.bleLoop(scale: Double, ring: RetainRing) {
        val n = (6 * scale).toInt().coerceAtLeast(1)
        while (isActive) {
            for (i in 0 until n) {
                val advert = BleAdvert(
                    deviceId = "seat-" + (i % 8),
                    rssi = -40 - i,
                    payload = ByteArray(31),
                    timestampNanos = nowNanos(),
                )
                estimatedBytes.addAndFetch(120L)
                if (i == 0) ring.retain(advert)
            }
            delay(33)
        }
    }

    /** Effect objects: immutable data carrying lists, the shape the real core emits. */
    private suspend fun CoroutineScope.effectLoop(scale: Double, ring: RetainRing) {
        val n = (4 * scale).toInt().coerceAtLeast(1)
        var seq = 0L
        while (isActive) {
            for (i in 0 until n) {
                val e = EffectEnvelope(
                    seq = seq++,
                    kind = "AbilityFired",
                    targets = listOf(i, i + 1, i + 2),
                    payload = "seat=$i;cooldown=true;at=" + nowNanos(),
                )
                estimatedBytes.addAndFetch(260L)
                ring.retain(e)
            }
            delay(100)
        }
    }

    /** Recording writes: medium-lifetime strings, the classic promotion source. */
    private suspend fun CoroutineScope.recordingLoop(scale: Double, ring: RetainRing) {
        val n = (2 * scale).toInt().coerceAtLeast(1)
        var seq = 0L
        while (isActive) {
            for (i in 0 until n) {
                val sb = StringBuilder(560)
                sb.append("{\"seq\":").append(seq++).append(",\"t\":").append(nowNanos()).append(",\"e\":[")
                for (k in 0 until 12) {
                    sb.append("{\"seat\":").append(k)
                        .append(",\"kind\":\"ObservationCaptured\",\"band\":\"near\"},")
                }
                sb.append("]}")
                val line = sb.toString()
                estimatedBytes.addAndFetch((line.length * 2 + 1_200).toLong())
                ring.retain(line)
            }
            delay(100)
        }
    }
}
