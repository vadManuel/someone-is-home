package home.someoneshome.ui

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * **M2's motion budget: an accumulator, not a classifier** (`gdd.md` M2, D-043, D-110, D-203, D-321).
 *
 * *A player must not scan and then walk away, crouch, or leave the spot.* What is ruled, and built
 * exactly:
 *
 * - **The source** is `CMDeviceMotion.userAcceleration`, gravity removed, as its **magnitude**; the
 *   rotation rate is never read, which is *strict on translation, lenient on rotation* at the source.
 * - **The rate** is 100 Hz, so one sample is [SECONDS_PER_SAMPLE].
 * - **Calibration** is the first ~0.5 s on entry, [CALIBRATION_SAMPLES] samples, measuring that
 *   player's own hand: the mean magnitude over them sets this player's limit, so a steadier hand
 *   gets a stricter one. *Fixed thresholds feel unfair to half the players.*
 * - **It accumulates**: every sample over the limit spends what it is over by, and nothing earns it
 *   back. Still is free; still never refills.
 * - **It scales with the tier** ([MotionAllowance]): a short Subroutine is strict, a circuit leg is not.
 * - **Spent, the Subroutine fails and must be rescanned** (M2, D-110: *the walk back is the cost*),
 *   and at the station the view closes (`gdd.md:492`, D-203). That is [FlowModel.motionSampled]'s.
 * - **It is the one thing a client adjudicates** (D-043), so it is computed here, on the phone, and
 *   the house hears only what STOP NOW already says.
 *
 * ### What is not ruled, and is here only as [MotionAllowance]'s provisional numbers
 *
 * The limit's multiple of the calibrated mean and its floor and ceiling, and every tier's allowance.
 * See [MotionAllowance]. **M2's other bound — too little motion also fails — is STOPPED** (D-321):
 * its floor and window are unruled and are the one number here that fails an honest player if it is
 * wrong, so nothing below judges a phone too still.
 *
 * ### Rule 7
 *
 * Primitives only, and [sample] allocates nothing: a hundred calls a second, for every second
 * anybody stands at a card. `MotionAllocationTest` holds it to zero bytes.
 */
class MotionBudget {

    private var calibrated = 0
    private var steadySum = 0.0
    private var limit = 0.0
    private var spent = 0.0
    private var allowed = 0.0

    /** True from [begin] to [end]: a screen the budget applies to is open. */
    var running: Boolean = false
        private set

    /** True once this opening's allowance is gone. */
    val gone: Boolean get() = running && spent >= allowed

    /** How much is left, 1 to 0. Full while calibrating, and full when nothing is running. */
    val left: Double
        get() = if (!running || allowed <= 0.0) 1.0 else (1.0 - spent / allowed).coerceIn(0.0, 1.0)

    /** A screen the budget applies to opened: calibrate afresh, and allow [allowance] g·s. */
    fun begin(allowance: Double) {
        running = true
        calibrated = 0
        steadySum = 0.0
        limit = 0.0
        spent = 0.0
        allowed = allowance
    }

    /** It closed. Samples after this are nothing. */
    fun end() {
        running = false
    }

    /**
     * One sample, in g. True when this sample spent the last of the allowance, and only then — the
     * caller closes the screen once.
     */
    fun sample(x: Double, y: Double, z: Double): Boolean {
        if (!running || spent >= allowed) return false
        val magnitude = sqrt(x * x + y * y + z * z)
        if (calibrated < CALIBRATION_SAMPLES) {
            steadySum += magnitude
            calibrated++
            if (calibrated == CALIBRATION_SAMPLES) {
                limit = (steadySum / CALIBRATION_SAMPLES * MotionAllowance.STEADY_MULTIPLE)
                    .coerceIn(MotionAllowance.LIMIT_FLOOR_G, MotionAllowance.LIMIT_CEILING_G)
            }
            return false
        }
        val over = magnitude - limit
        if (over > 0.0) spent += over * SECONDS_PER_SAMPLE
        return spent >= allowed
    }

    companion object {
        /** M2's rate, 100 Hz. */
        const val SAMPLES_PER_SECOND: Int = 100

        const val SECONDS_PER_SAMPLE: Double = 1.0 / SAMPLES_PER_SECOND

        /** M2's *first ~0.5 s on entry*, at 100 Hz. */
        const val CALIBRATION_SAMPLES: Int = SAMPLES_PER_SECOND / 2
    }
}

/**
 * **PROVISIONAL — every number in this object is unruled** (D-321), and each is one named
 * constant so the owner's numbers replace them without a second change.
 *
 * M2 rules the shape (*budget scales with tier; a 5 s short subroutine can be strict; a 60 s circuit
 * leg cannot*) and no number. These are this build's guesses, sized from what an iPhone's
 * `userAcceleration` reads in a hand (roughly 0.005 to 0.02 g of tremor and breath) and while
 * walking (roughly 0.1 to 0.3 g a step), and **untested on a phone**: the phone session's D-321 line
 * is where they are first felt.
 *
 * An allowance is g·s: the acceleration over the limit, summed over the seconds it lasted. At about
 * 0.1 g over the limit while walking, [SHORT] is two seconds or so of walking away and [CIRCUIT]
 * eight.
 *
 * **They are the phone's constants, not [home.someoneshome.model.Balance]'s**: the house never
 * computes the budget (D-043), so nothing about it is recorded, and the recording does not change.
 * Making them lobby or arming values would put them in `Balance` and bump the recording.
 */
object MotionAllowance {

    /** The limit is this many times the calibrated mean. */
    const val STEADY_MULTIPLE: Double = 3.0

    /** Never stricter than this, however steady the hand: a braced phone is not held to its noise. */
    const val LIMIT_FLOOR_G: Double = 0.03

    /** Never looser than this, however much the first half second moved: scanning and walking off. */
    const val LIMIT_CEILING_G: Double = 0.08

    const val SHORT: Double = 0.2
    const val MEDIUM: Double = 0.35
    const val LONG: Double = 0.6
    const val CIRCUIT: Double = 0.8

    /** The station: reading is an uncompletable Subroutine (`gdd.md:492`), allowed a medium's. */
    const val STATION: Double = MEDIUM

    /** The allowance for [tier]. */
    fun of(tier: SubroutineTier): Double = when (tier) {
        SubroutineTier.Short -> SHORT
        SubroutineTier.Medium -> MEDIUM
        SubroutineTier.Long -> LONG
        SubroutineTier.Circuit -> CIRCUIT
    }

    /** The allowance on [screen]: its Subroutine's tier, the station's, or none (0). */
    fun on(screen: ScreenId): Double = Subroutine.on(screen)?.let { of(it.tier) }
        ?: if (screen in Flow.atTheStation) STATION else 0.0
}

/**
 * **The budget as it is drawn: one float, read only inside the row's draw lambda** (D-321).
 *
 * [ReportCircle]'s arrangement: a change invalidates the row's draw and nothing else (rule 7). It is
 * stepped ([STEPS]) rather than written every sample, so a hundred samples a second become at most
 * [STEPS] writes an opening, and a still hand writes nothing at all.
 */
class MotionBar {
    val left: MutableFloatState = mutableFloatStateOf(1f)

    /** Shows [fraction], rounded up to a step so the bar is only empty when the budget is. */
    fun show(fraction: Double) {
        val stepped = (ceil(fraction * STEPS) / STEPS).toFloat()
        if (left.floatValue != stepped) left.floatValue = stepped
    }

    companion object {
        const val STEPS: Int = 64
    }
}

/** The motion budget row's test tag. */
const val MOTION_BUDGET_ROW: String = "motion-budget-row"

/** The motion bar the rows draw. Provided by [Screen]; every render that passes none gets a full one. */
val LocalMotionBar: ProvidableCompositionLocal<MotionBar> = staticCompositionLocalOf { MotionBar() }
