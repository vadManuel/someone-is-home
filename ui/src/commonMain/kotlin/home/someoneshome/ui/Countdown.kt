package home.someoneshome.ui

import kotlin.math.roundToInt

/**
 * A clock on a screen: **how much of a window is left, and how long the window was.**
 *
 * ### It is display, and the device does not run it
 *
 * Nothing here ticks. There is no coroutine, no frame callback and no elapsed-time arithmetic —
 * a [Countdown] is a value that arrives, is drawn, and is replaced by the next one. In play the
 * house owns every clock in this game: it decides when the talk starts, when the ballot is read
 * and when the lights go out, and it pushes the screen. A phone that counted down locally would
 * be six phones counting down slightly differently in a dark house, each of them certain.
 *
 * ### The number and the bar are the same value
 *
 * Every screen that draws a clock draws it twice — as a readout and as a bar — and two numbers
 * for one fact drift the first time either is touched. [remaining] and [litOf] are both this
 * value, so a bar can no longer disagree with the figure printed beside it. The design's own
 * screens had already drifted this way once: the scan's `6S LEFT` and its twelve lit segments
 * were separate constants that happened to agree.
 */
data class Countdown(val secondsLeft: Int, val ofSeconds: Int) {

    /**
     * `m:ss` on a window of [CLOCK_FORM_SECONDS] or more, bare seconds below it.
     *
     * The form reads off the length of the window rather than off the screen, so a screen cannot
     * pick the wrong one. **The threshold is fitted to the design's four drawn clocks**, all of
     * which it has to reproduce: `1:04 REMAINING` on the ninety-second discussion and `VOTING ENDS
     * IN 0:24` on the sixty-second ghost meeting are `m:ss`; `LIGHTS OUT IN 9` on the fifteen-
     * second result and `6S LEFT` on the ten-second scan are bare.
     *
     * **It was 60, and 60 was only ever right by accident.** The vote screen is the fourth clock,
     * drawn `0:38` — and the design's vote window is 45 seconds (`gdd.md:412`). While the flow
     * table wrongly said 60 the m:ss form came out of the threshold; correcting the window to 45
     * turned the design's own `0:38` into `38` without anybody asking for it. Thirty is the round
     * number that keeps all four, and it is fitted to them rather than reasoned from anything.
     */
    val text: String
        get() =
            if (ofSeconds >= CLOCK_FORM_SECONDS) {
                "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}"
            } else {
                "$secondsLeft"
            }

    /** How much of the window is still to run, `0f..1f`. Bars drain; they do not fill. */
    val remaining: Float
        get() = if (ofSeconds <= 0) 0f else (secondsLeft.toFloat() / ofSeconds).coerceIn(0f, 1f)

    /** The same fraction as whole segments, for the design's only progress form. */
    fun litOf(segments: Int): Int = (segments * remaining).roundToInt()

    companion object {
        /** The shortest window still drawn as `m:ss`. See [text] for the four clocks it fits. */
        const val CLOCK_FORM_SECONDS: Int = 30

        /**
         * A screen with no clock on it.
         *
         * Spent rather than absent, so a screen that asks for a countdown it has no entry for
         * draws `0` — visibly wrong, noticed immediately — instead of a blank where a number
         * belongs or a plausible figure the device invented.
         */
        val NONE: Countdown = Countdown(secondsLeft = 0, ofSeconds = 0)
    }
}

/**
 * **Which screens carry a clock, and how long each clock runs.**
 *
 * ### The window is the auto-advance, not a second opinion about it
 *
 * A countdown on screen is a promise that something happens when it reaches zero, and the thing
 * that happens is [Flow.autoAdvance]'s row for that screen. Written as two numbers they drift, and
 * the drift is a phone that says nine seconds and moves after fifteen. So the window is *read off*
 * the row rather than restated here — the same argument [PanelVals.SCAN_SEGMENTS] already won for
 * the scan window, applied to every clock in the game.
 *
 * ### [DRAWN_AT] is a fixture, exactly like [PanelState.arrivingAt]'s cause
 *
 * In play the moment comes from the house, on [PanelState.secondsLeft]. A phone with no house
 * attached has no such number, and a screen with a blank where a clock belongs is not what any of
 * these screens look like — so each carries the moment the design drew it at, and the house's
 * number replaces it whenever there is one. **It decides nothing**: it is the position of a clock
 * hand, and no rule anywhere reads it.
 *
 * ### It fails closed
 *
 * A screen not named here has no clock, **even when the house sends a number for it**. The
 * failure mode is a countdown that does not appear, which somebody notices in thirty seconds —
 * never a countdown appearing on a screen nobody decided should have one.
 */
object Countdowns {

    /**
     * The moment each clock was drawn at, in seconds remaining.
     *
     * Every one of these is read off the design's own screen: `6S LEFT` over twelve lit segments
     * of twenty, `1:04 REMAINING`, `0:38`, `LIGHTS OUT IN 9`, `VOTING ENDS IN 0:24`.
     */
    private val DRAWN_AT: Map<ScreenId, Int> = mapOf(
        ScreenId.Scan to 6,
        ScreenId.Discussion to 64,
        ScreenId.Vote to 38,
        ScreenId.Tally to 9,
        ScreenId.GhostMeeting to 24,
    )

    /** The screens that draw a clock. Nothing else has one, whatever the house sends. */
    val screens: Set<ScreenId> get() = DRAWN_AT.keys

    /** How long this screen's clock runs, in seconds — the auto-advance that ends it. */
    fun windowOf(screen: ScreenId): Int? =
        Flow.autoAdvance[screen]?.let { it.afterMillis / 1000 }

    /**
     * The clock [screen] is showing, or null where it has none.
     *
     * [said] is the house's number and wins whenever there is one; the fixture's drawn moment
     * stands in while there is not. Either way it is clamped into the window, because a phone
     * showing more time left than the window holds would be promising a delay the house will not
     * honour.
     */
    fun on(screen: ScreenId, said: Int? = null): Countdown? {
        val drawn = DRAWN_AT[screen] ?: return null
        val window = windowOf(screen) ?: return null
        return Countdown((said ?: drawn).coerceIn(0, window), window)
    }
}
