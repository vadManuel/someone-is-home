package home.someoneshome.platform

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant

/**
 * **Where one motion sample goes: three numbers, in g, gravity removed** (M2, D-321).
 *
 * `gdd.md` M2 names the source, `CMDeviceMotion.userAcceleration`, and the rate, 100 Hz. Gravity is
 * already out of it, and rotation is never in it: the rotation rate is a separate field this seam
 * does not carry, which is how M2's *strict on translation, lenient on rotation* holds at the source.
 *
 * **A `fun interface` with primitive parameters, and that is rule 7 rather than style.** A
 * `(Double, Double, Double) -> Unit` is a generic `Function3` and boxes all three numbers on every
 * call: three heap objects a sample, three hundred a second. This one passes them as doubles.
 */
fun interface MotionSink {
    fun sample(x: Double, y: Double, z: Double)
}

/**
 * **The phone's own motion, while something is measuring it** (M2, D-043, D-321).
 *
 * Started when a screen the motion budget applies to opens and stopped when it closes, so the
 * sensor runs only while someone is standing at a card or at the station. [start] while running
 * replaces the sink and starts nothing twice; [stop] while stopped does nothing. **Samples may
 * still arrive for a moment after [stop]** on a real phone (the OS's queue drains), and a sink must
 * read them as nothing, which the motion budget does by not running.
 *
 * Never throws and never reports to the player (rule 6): a sensor that cannot start delivers
 * nothing, and a budget that hears nothing holds.
 */
interface MotionSensor {
    fun start(sink: MotionSink)
    fun stop()
}

/** M2's sample rate: **100 Hz** (`gdd.md` M2, the performance table). */
const val MOTION_HZ: Int = 100

/**
 * **This build's motion: Core Motion on a phone, a stand-in on the simulator** (D-321).
 *
 * [note] is the console's, never a screen's (rule 6).
 */
expect fun deviceMotion(note: (String) -> Unit = {}): MotionSensor

/**
 * **The Motion & Fitness permission, kept by the sensor** (D-161, D-321).
 *
 * The sensor starts only on a phone whose MOTION & FITNESS row stands GRANTED, asked at every
 * [start], because a grant can be switched off in Settings between one card and the next (D-310).
 * Anything else — refused, never asked, asked and unanswered, not on this phone — starts nothing,
 * so no sample is taken and the motion budget holds. The refusal itself is the permission walk's
 * to handle: warned, then named at the meeting (D-310); **this seam adds no report and no copy.**
 *
 * A refusal that lands while the sensor is running stops it at the next [start] rather than in the
 * middle of a card: the OS keeps delivering device motion either way, so what the row protects is
 * the game's own reading, and it is read on the next opening.
 */
class PermittedMotion(
    private val sensor: MotionSensor,
    private val granted: () -> Boolean,
) : MotionSensor {

    override fun start(sink: MotionSink) {
        if (granted()) sensor.start(sink) else sensor.stop()
    }

    override fun stop() = sensor.stop()

    companion object {
        /** The gate read off [permissions]: MOTION & FITNESS standing GRANTED and nothing else. */
        fun of(sensor: MotionSensor, permissions: Permissions): PermittedMotion =
            PermittedMotion(sensor) { permissions.standing(DevicePermission.Motion) == Grant.Granted }
    }
}

/**
 * **A motion sensor a test drives by hand** (D-321): the desktop's and the tests' sensor.
 *
 * [feed] delivers one sample to whatever sink is running, and nothing while stopped, as a phone's
 * sensor does. Test-facing.
 */
class DrivenMotion : MotionSensor {

    private var sink: MotionSink? = null

    /** How many times [start] reached this sensor. Test-facing. */
    var starts: Int = 0
        private set

    val running: Boolean get() = sink != null

    override fun start(sink: MotionSink) {
        starts++
        this.sink = sink
    }

    override fun stop() {
        sink = null
    }

    fun feed(x: Double, y: Double, z: Double) {
        sink?.sample(x, y, z)
    }
}
