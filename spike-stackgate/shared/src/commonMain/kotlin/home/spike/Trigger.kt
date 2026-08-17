package home.spike

/**
 * Pluggable trigger. The gate measures *trigger -> pixels dark*; what produced the trigger is
 * a separate question with a separate test.
 */
interface TriggerSource {
    val name: String
    fun start(onFire: (Long) -> Unit)
    fun stop()
    /** Ask for the next trigger. A manual source ignores this and waits for the human. */
    fun requestNext(idle: IdleProfile)
}

/**
 * A screen tap. The decided trigger for this spike, and the one used for the camera pass —
 * finger contact is visible on 240 fps video, which is what gives the video a trigger
 * reference to count frames from.
 */
class TapTriggerSource : TriggerSource {
    override val name = "tap"
    private var sink: ((Long) -> Unit)? = null

    override fun start(onFire: (Long) -> Unit) {
        sink = onFire
    }

    override fun stop() {
        sink = null
    }

    override fun requestNext(idle: IdleProfile) = Unit

    /** Called from the Compose pointer handler on the Initial pass, as early as it can be. */
    fun onTap() {
        sink?.invoke(nowNanos())
    }
}

/**
 * Fires on a schedule, because nobody taps ten thousand times.
 *
 * The delay is randomised with sub-frame resolution ON PURPOSE. A trigger on a fixed period
 * locks to one phase of the frame clock and samples one slice of the distribution — which
 * would look clean and mean nothing. Uniform phase across the frame interval is what makes the
 * latency distribution honest.
 */
class ScriptedTriggerSource(seed: Long = 0x5EED_1_7L) : TriggerSource {
    override val name = "scripted"
    private var sink: ((Long) -> Unit)? = null
    private var live = false

    // Plain LCG. Deterministic, allocation-free, and enough for phase decorrelation.
    private var rngState = seed

    private fun nextUniform(): Double {
        rngState = rngState * 6364136223846793005L + 1442695040888963407L
        return ((rngState ushr 11).toDouble() / (1L shl 53).toDouble())
    }

    override fun start(onFire: (Long) -> Unit) {
        sink = onFire
        live = true
    }

    override fun stop() {
        live = false
        sink = null
    }

    override fun requestNext(idle: IdleProfile) {
        if (!live) return
        val spanMillis = (idle.maxMillis - idle.minMillis).toDouble()
        val delayMillis = idle.minMillis + nextUniform() * spanMillis
        val delayNanos = (delayMillis * 1_000_000.0).toLong()
        scheduleOnUiThread(delayNanos) {
            // The stamp is taken here, inside the block — not when the block was scheduled —
            // so dispatch latency is outside the measured window. What is under test is the
            // renderer, not the queue.
            if (live) sink?.invoke(nowNanos())
        }
    }
}
