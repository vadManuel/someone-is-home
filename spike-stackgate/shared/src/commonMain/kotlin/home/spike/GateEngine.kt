package home.spike

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

const val ARGB_AMBER: Int = 0xFFB4600A.toInt()
const val ARGB_BLACK: Int = 0xFF000000.toInt()

/** Trial flag bits. */
const val FLAG_PREWARM = 1 shl 0
const val FLAG_GC_DURING = 1 shl 1
const val FLAG_STALL_DURING = 1 shl 2

/**
 * The measurement state machine.
 *
 * Everything here runs on the UI thread: Compose on iOS composes, lays out and draws on main,
 * the CADisplayLink callback is on the main run loop, and the scripted trigger dispatches to
 * main. So no synchronisation is needed and none is present — a lock on the blackout path
 * would be one more thing that can stall the frame that decides whether a revoke is anonymous.
 */
object GateEngine {

    // ---- the lamp -----------------------------------------------------------------------
    //
    // IntState, not MutableState<Color>: Color is a value class over ULong and would box on
    // every write. This is read inside the draw lambda (see Lamp.kt), so a change invalidates
    // DRAW ONLY — composition and layout are skipped. That is the no-allocation discipline of
    // story 1.7b expressed in Compose terms, and it is the whole reason the path is this shape.
    val lampArgb = mutableIntStateOf(ARGB_AMBER)

    /** Toggled every drawn frame in camera mode so app frames are countable on 240 fps video. */
    val framePatchPhase = mutableIntStateOf(0)

    // ---- run state (observable, read by the report UI only) ------------------------------
    val running = mutableStateOf(false)
    val progress = mutableIntStateOf(0)
    val finished = mutableStateOf(false)

    var config: RunConfig = RunConfig.VOLUME
        private set

    // ---- phase --------------------------------------------------------------------------
    private const val PHASE_STOPPED = 0
    private const val PHASE_IDLE = 1
    private const val PHASE_AWAIT_DRAW = 2
    private const val PHASE_AWAIT_PRESENT = 3
    private const val PHASE_HOLD = 4

    private var phase = PHASE_STOPPED

    // ---- per-trial scratch. Plain fields, so a trial allocates nothing. -------------------
    private var tTrigger = 0L
    private var tDraw = 0L
    private var vsyncAtTrigger = 0L
    private var vsyncAtDraw = 0L
    private var tickAtTrigger = 0L
    private var tickAtDraw = 0L
    private var gcEpochAtTrigger = 0L
    private var stallsAtTrigger = 0
    private var holdUntilTick = 0L

    private var trigger: TriggerSource? = null

    // ---- results ------------------------------------------------------------------------
    var trials: TrialBuffer = TrialBuffer(1)
        private set

    private var completed = 0
    private var prewarmRemaining = 0

    var startedAtNanos = 0L
        private set
    var endedAtNanos = 0L
        private set

    // -------------------------------------------------------------------------------------

    fun start(cfg: RunConfig, source: TriggerSource) {
        config = cfg
        trials = TrialBuffer(cfg.trials + cfg.prewarmTrials)
        completed = 0
        prewarmRemaining = cfg.prewarmTrials
        trigger = source

        prepareDeviceForRun(brightness = 0.6)
        Vsync.resetStalls()
        GcProbe.reset()

        // Bracket the run with a forced collection at each end.
        //
        // Without this, ALLOC_PROBE has a false-pass mode that looks exactly like a real
        // result: pressure is OFF, the blackout path allocates little, no collection happens
        // inside the window, the epoch list is empty, and the derived allocation is reported
        // as zero bytes per trial. Zero would be read as "the discipline holds" when what it
        // actually means is "nothing was measured". The brackets guarantee at least two epochs
        // for the heap delta to be derived from.
        GcProbe.forceCollect()

        Pressure.start(cfg.pressure, cfg.cpuOnlyPressure)

        lampArgb.intValue = ARGB_AMBER
        finished.value = false
        progress.intValue = 0
        running.value = true
        startedAtNanos = nowNanos()
        phase = PHASE_IDLE

        source.start(::fire)
        source.requestNext(cfg.idle)
    }

    fun stop() {
        if (phase == PHASE_STOPPED) return
        endedAtNanos = nowNanos()
        phase = PHASE_STOPPED
        trigger?.stop()
        trigger = null
        Pressure.stop()
        // Closing bracket. The poller is deliberately still running so it can observe this
        // epoch; it is stopped by the report path once it has.
        GcProbe.forceCollect()
        lampArgb.intValue = ARGB_AMBER
        running.value = false
        finished.value = true
        restoreDeviceAfterRun()
    }

    /**
     * THE BLACKOUT PATH.
     *
     * Never early-returns into a different shape: a rejected trigger is a no-op on an idle
     * lamp, not an alternative code path. Nothing here allocates, and nothing here logs.
     * Everything between the timestamp and the state write is a plain field load.
     */
    fun fire(triggerNanos: Long) {
        if (phase != PHASE_IDLE) return

        tTrigger = triggerNanos
        vsyncAtTrigger = Vsync.lastNanos
        tickAtTrigger = Vsync.tickIndex
        gcEpochAtTrigger = GcProbe.lastEpoch
        stallsAtTrigger = Vsync.stallCount()
        tDraw = 0L
        phase = PHASE_AWAIT_DRAW

        lampArgb.intValue = ARGB_BLACK
    }

    /**
     * Called from inside the draw lambda, on every draw. Stamps the first frame that actually
     * put black on the surface — not the frame Compose *decided* to, which is the number a
     * naive spike reports.
     */
    fun onLampDrawn(argb: Int) {
        if (phase != PHASE_AWAIT_DRAW || argb != ARGB_BLACK) return
        tDraw = nowNanos()
        vsyncAtDraw = Vsync.lastNanos
        tickAtDraw = Vsync.tickIndex
        phase = PHASE_AWAIT_PRESENT
    }

    /** Called from the CADisplayLink callback, once per vsync. */
    fun onVsync(vsyncNanos: Long, tickIndex: Long) {
        when (phase) {
            PHASE_AWAIT_PRESENT -> {
                if (vsyncNanos > tDraw) {
                    record(vsyncNanos)
                    holdUntilTick = tickIndex + config.holdFrames
                    phase = PHASE_HOLD
                }
            }

            PHASE_HOLD -> {
                if (tickIndex >= holdUntilTick) {
                    lampArgb.intValue = ARGB_AMBER
                    phase = PHASE_IDLE
                    onTrialFinished()
                }
            }
        }
    }

    private fun record(presentNanos: Long) {
        val isPrewarm = prewarmRemaining > 0
        var flags = 0
        if (isPrewarm) flags = flags or FLAG_PREWARM
        if (GcProbe.lastEpoch != gcEpochAtTrigger) flags = flags or FLAG_GC_DURING
        if (Vsync.stallCount() != stallsAtTrigger) flags = flags or FLAG_STALL_DURING

        trials.add(
            triggerNanos = tTrigger,
            drawNanos = tDraw,
            presentNanos = presentNanos,
            vsyncAtTriggerNanos = vsyncAtTrigger,
            vsyncAtDrawNanos = vsyncAtDraw,
            tickAtTrigger = tickAtTrigger,
            tickAtDraw = tickAtDraw,
            gcEpoch = gcEpochAtTrigger,
            flags = flags,
        )
    }

    private fun onTrialFinished() {
        if (prewarmRemaining > 0) {
            prewarmRemaining--
        } else {
            completed++
            // A recomposition, so it is done between trials and never mid-trial. Short runs
            // update every trial: the camera pass needs the index legible on video to line a
            // video segment up with its row, and 30 recompositions cost nothing.
            val every = if (config.trials <= 500) 1 else 250
            if (completed % every == 0) progress.intValue = completed
        }

        if (completed >= config.trials) {
            progress.intValue = completed
            stop()
            return
        }
        trigger?.requestNext(config.idle)
    }

    /** True while a trial is in flight; the tap trigger uses it to ignore double taps. */
    fun isIdle(): Boolean = phase == PHASE_IDLE

    fun isPrewarming(): Boolean = prewarmRemaining > 0

    fun completedTrials(): Int = completed
}
