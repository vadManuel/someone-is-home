package home.someoneshome.platform

import home.someoneshome.platform.motion.sh_motion_available
import home.someoneshome.platform.motion.sh_motion_start
import home.someoneshome.platform.motion.sh_motion_stop
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction

/** **A phone: Core Motion's device motion at 100 Hz** (M2, D-321). */
actual fun deviceMotion(note: (String) -> Unit): MotionSensor = CoreMotionSensor(note)

/**
 * **Core Motion, read in C, delivered as three doubles** (`motion.def`).
 *
 * `CMDeviceMotion.userAcceleration` (gravity removed, `gdd.md` M2) at [MOTION_HZ], on the main
 * queue. The struct is read on the C side and its fields cross as arguments to one
 * [staticCFunction], so a sample allocates nothing on the Kotlin heap: no wrapper for the motion
 * object, no `CValue` for the struct, no boxed double (rule 7).
 *
 * **One [StableRef] to this object, made once and never disposed.** The OS's queue can deliver a
 * sample after [stop]; a ref disposed at stop would be read by that sample. The sensor lives for the
 * app's life, so the ref does too, and a late sample finds [sink] null and goes nowhere.
 */
private class CoreMotionSensor(private val note: (String) -> Unit) : MotionSensor {

    private var sink: MotionSink? = null

    private val self: COpaquePointer = StableRef.create(this).asCPointer()

    fun deliver(x: Double, y: Double, z: Double) {
        sink?.sample(x, y, z)
    }

    override fun start(sink: MotionSink) {
        this.sink = sink
        val running = try {
            sh_motion_start(1.0 / MOTION_HZ, SINK, self) == 1
        } catch (_: Throwable) {
            false
        }
        // Said once a start, never per sample, and to the console only (rule 6).
        if (!running) note("MOTION — no device motion on this phone (available ${sh_motion_available()})")
    }

    override fun stop() {
        sink = null
        try {
            sh_motion_stop()
        } catch (_: Throwable) {
            // Nothing to do: a sensor that would not stop delivers to a null sink.
        }
    }
}

/** The one C function the shim calls, a hundred times a second. Captures nothing. */
private val SINK = staticCFunction { context: COpaquePointer?, x: Double, y: Double, z: Double ->
    context?.asStableRef<CoreMotionSensor>()?.get()?.deliver(x, y, z)
    Unit
}
