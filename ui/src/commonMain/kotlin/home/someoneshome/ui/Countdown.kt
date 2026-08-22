package home.someoneshome.ui

import home.someoneshome.model.MeetingPhase
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
 * ### Where a window comes from, and why there are now two answers
 *
 * A countdown on screen is a promise that something happens when it reaches zero. **Where the thing
 * that happens is a timer this phone runs, the window is read off that timer** rather than restated
 * — written as two numbers they drift, and the drift is a phone that says nine seconds and moves
 * after fifteen. That is [Flow.autoAdvance], and after the meeting's transitions became house
 * pushes the only clock still in it is the scan's.
 *
 * **Where the thing that happens is the house moving everybody at once, the window is the design's
 * own number** and is listed in [MEETING_WINDOWS]. It cannot be derived from anything on this phone,
 * because nothing on this phone ends a meeting phase — and it is not a loss: the discussion's ninety
 * seconds and the vote's forty-five (`gdd.md:412`, `:1006`, D-117) were always the design's, and the
 * flow table was borrowing them rather than owning them.
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

    /**
     * **The meeting's four windows, which the house runs and this phone only draws.**
     *
     * Each is the design's own number rather than a presentation choice, which is why they are
     * written here with a citation apiece instead of being inferred from a timer. A meeting phase
     * ends when every phone in the house says so or when the house's clock says so, and neither is
     * a thing this table could compute.
     *
     * **The vote's 45 is host-changeable in lobby settings** (D-117) and the lobby already draws a
     * control for it (`LobbyModel.cycleVoteWindow`). That control still reaches nothing — the wire
     * does not carry the setting — so this is the default, and when the setting is really sent the
     * house's number will arrive with the phase and replace it, exactly as [DRAWN_AT] is replaced.
     */
    private val MEETING_WINDOWS: Map<ScreenId, Int> = mapOf(
        // `1:04 REMAINING` on the design's own discussion screen, of ninety.
        ScreenId.Discussion to 90,
        // gdd.md:412 and :1006. Unanimous READY closes it early, which shortens the wait and
        // never the window — a clock that redrew itself shorter would be the phone predicting it.
        ScreenId.Vote to 45,
        // LIGHTS OUT IN 9, over a bar with 6 of 15 spent. The Restrained takeover lands at the
        // halfway mark of this one (D-102), which is the house's business and not the bar's.
        ScreenId.Tally to 15,
        // VOTING ENDS IN 0:24, from outside the system.
        ScreenId.GhostMeeting to 60,
    )

    /** The screens that draw a clock. Nothing else has one, whatever the house sends. */
    val screens: Set<ScreenId> get() = DRAWN_AT.keys

    /**
     * How long this screen's clock runs, in seconds.
     *
     * The timer that ends the screen wins where there is one, so the scan's bar and the moment its
     * light dies still cannot disagree. Everything else is the house's window.
     */
    fun windowOf(screen: ScreenId): Int? =
        Flow.autoAdvance[screen]?.let { it.afterMillis / 1000 } ?: MEETING_WINDOWS[screen]

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

    /**
     * **The couch's clock, looked up by phase rather than by screen** (D-134).
     *
     * The living walk a screen per phase and never have to be told which one they are on. A player
     * who is out watches the **whole meeting from one screen**, so `GhostMeeting` is the only
     * surface in the game whose clock changes meaning under it — and D-134 puts *the discussion and
     * vote timers* on the couch, plural. Before this it ran one clock labelled VOTING ENDS IN for
     * every phase, which during the talk was a countdown to a window that had not opened.
     *
     * Null during [MeetingPhase.CheckIn]: the gate closes when the last player walks in, not when a
     * clock runs out (D-104), so there is no window for a bar to be a fraction of.
     *
     * ### ⚠️ The vote reads the ghost's own row, and the two rows disagree
     *
     * `GhostMeeting` carries a window of 60 while [ScreenId.Vote] carries the design's 45 — two
     * numbers for one clock, in a lit room where the couch can see a living player's phone. It is
     * **left alone deliberately**: the ghost's 60 is what reproduces the design's drawn picture,
     * `VOTING ENDS IN 0:24` over twelve lit segments of thirty, and moving it to 45 redraws that
     * bar at sixteen. Reconciling them is a visible screen change and wants a ruling; flagged.
     */
    fun onGhostMeeting(phase: MeetingPhase, said: Int? = null): Countdown? = when (phase) {
        MeetingPhase.CheckIn -> null
        MeetingPhase.Discussion -> on(ScreenId.Discussion, said)
        MeetingPhase.Vote -> on(ScreenId.GhostMeeting, said)
        MeetingPhase.Tally -> on(ScreenId.Tally, said)
    }

    /** What the couch's clock is counting down, in the phase's own words. */
    fun ghostClockLabel(phase: MeetingPhase): String = when (phase) {
        MeetingPhase.CheckIn -> "WAITING FOR THE HOUSE"
        MeetingPhase.Discussion -> "DISCUSSION ENDS IN"
        MeetingPhase.Vote -> "VOTING ENDS IN"
        MeetingPhase.Tally -> "LIGHTS OUT IN"
    }
}
