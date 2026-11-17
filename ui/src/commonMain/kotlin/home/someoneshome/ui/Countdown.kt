package home.someoneshome.ui

import home.someoneshome.model.Balance
import home.someoneshome.model.MeetingPhase
import kotlin.math.roundToInt

/**
 * A clock on a screen: **how much of a window is left, and how long the window was.**
 *
 * ### It is display, and it decides nothing
 *
 * Nothing here ticks: a [Countdown] is a value that is drawn and replaced by the next one. In play
 * the house owns every clock in this game: it decides when the talk starts, when the ballot is read
 * and when the lights go out, and it pushes the screen. **What a phone does with the push is count
 * the house's window down from it** (D-243, D-239's arrangement for page 2): `FlowModel.secondPassed`
 * once a second, from the push that opened the phase, so six phones show six readings of one
 * deadline, each a little late, and none of them moves anything when it reaches zero.
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
     * IN 0:24` on the ghost meeting are `m:ss`; `LIGHTS OUT IN 9` on the fifteen-second result and
     * `6S LEFT` on the ten-second scan are bare.
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
     *
     * **A moment, never a fraction.** The couch's `0:24` is the design's own hand position and it
     * is kept; what moved under D-163 is the window it is a fraction *of*, so the bar beneath it
     * now draws sixteen segments of thirty where it drew twelve. That is the picture being redrawn
     * honestly rather than a second number being maintained to preserve it — a `0:24` re-cut to
     * `0:18` to hold the old bar would have been the drawing artifact recreated one field over.
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
     * **The vote's 45 is host-changeable in lobby settings** (D-117) and the lobby draws a control
     * for it (`LobbyModel.cycleVoteWindow`). That setting reaches the round now: it rides the
     * arming event, the rules lock it, and it arrives on `Effect.MeetingPhaseOpened` when the
     * ballot opens — so what is written here is the **default**, replaced by the house's number
     * wherever there is one, exactly as [DRAWN_AT] is replaced by the house's moment.
     *
     * It reads `Balance.VOTE_WINDOW_SECONDS` rather than a literal, because the rules arm rounds
     * against that same constant and two copies of forty-five is how this table drifted before.
     */
    private val MEETING_WINDOWS: Map<ScreenId, Int> = mapOf(
        // `1:04 REMAINING` on the design's own discussion screen, of ninety. **It reads
        // `Balance.DISCUSSION_SECONDS` rather than a literal now**, for the reason the vote's row
        // below it already did: the tick driver ends the talk against that constant, so a literal
        // here would be a bar draining against one number while the transition fired off another.
        ScreenId.Discussion to Balance.DISCUSSION_SECONDS,
        // gdd.md:412 and :1006. Unanimous READY closes it early, which shortens the wait and
        // never the window — a clock that redrew itself shorter would be the phone predicting it.
        ScreenId.Vote to Balance.VOTE_WINDOW_SECONDS,
        // LIGHTS OUT IN 9, over a bar with 6 of 15 spent. The Restrained takeover lands at the
        // halfway mark of this one (D-102), which is the house's business and not the bar's — and
        // the house now really counts it, so this reads the constant the driver counts against
        // rather than the literal the drawn screen was measured from.
        ScreenId.Tally to Balance.TALLY_SECONDS,
        // VOTING ENDS IN 0:24, from outside the system -- and it is the SAME ballot the row above
        // counts, so it reads the same constant (D-163). It carried a 60 of its own until then.
        ScreenId.GhostMeeting to Balance.VOTE_WINDOW_SECONDS,
    )

    /** The screens that draw a clock. Nothing else has one, whatever the house sends. */
    val screens: Set<ScreenId> get() = DRAWN_AT.keys

    /**
     * How long this screen's clock runs, in seconds.
     *
     * The timer that ends the screen wins where there is one, so the scan's bar and the moment its
     * light goes dark still cannot disagree. Everything else is the house's window.
     *
     * [voteWindow] is the ballot's length as the house sent it, and it replaces the default on the
     * **two** screens that count a ballot down — the living's [ScreenId.Vote] and the couch's
     * [ScreenId.GhostMeeting]. Both, because they are one clock in one room: the couch can see a
     * living player's phone, and two surfaces draining the same ballot over different windows is a
     * defect somebody notices from across a lit room. Where the house has said nothing the two
     * keep their own drawn numbers, which is what reproduces the design's screens.
     */
    fun windowOf(screen: ScreenId, voteWindow: Int? = null): Int? {
        val timed = Flow.autoAdvance[screen]?.let { it.afterMillis / 1000 }
        if (timed != null) return timed
        if (voteWindow != null && (screen == ScreenId.Vote || screen == ScreenId.GhostMeeting)) {
            return voteWindow
        }
        return MEETING_WINDOWS[screen]
    }

    /**
     * **How long the house runs [phase] for, in seconds, or null where it runs no clock** (D-243).
     *
     * The push that opens a phase starts [PanelState.secondsLeft] here: the talk's ninety, the
     * ballot the house sent (its default where it sent none), the tally's fifteen. They are the
     * numbers `HouseClock` closes each phase on, read from the same `Balance` constants, so the
     * phone's reading and the house's deadline cannot be two numbers. [MeetingPhase.CheckIn] has
     * none: the gate closes on a count of phones, not on a clock (D-104).
     */
    fun windowOfPhase(phase: MeetingPhase, voteWindow: Int? = null): Int? = when (phase) {
        MeetingPhase.CheckIn -> null
        MeetingPhase.Discussion -> windowOf(ScreenId.Discussion)
        MeetingPhase.Vote -> windowOf(ScreenId.Vote, voteWindow)
        MeetingPhase.Tally -> windowOf(ScreenId.Tally)
    }

    /**
     * The clock [screen] is showing, or null where it has none.
     *
     * [said] is the house's number and wins whenever there is one; the fixture's drawn moment
     * stands in while there is not. Either way it is clamped into the window, because a phone
     * showing more time left than the window holds would be promising a delay the house will not
     * honour — and the clamp is what makes a shortened ballot honest: a host who moved the setting
     * to 30 gets a clock that starts at 30 rather than one that starts at 45 and jumps.
     */
    fun on(screen: ScreenId, said: Int? = null, window: Int? = null): Countdown? {
        val drawn = DRAWN_AT[screen] ?: return null
        val runs = windowOf(screen, window) ?: return null
        return Countdown((said ?: drawn).coerceIn(0, runs), runs)
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
     * ### One clock, one number — the two rows agree at rest as well as in play (D-163)
     *
     * `GhostMeeting` carried a window of 60 while [ScreenId.Vote] carried the design's 45, and the
     * ghost's 60 was kept because it reproduced a drawn frame: `VOTING ENDS IN 0:24` over twelve
     * lit segments of thirty. **The 60 was a drawing artifact and the ruling is to redraw the
     * picture**, so both rows now read `Balance.VOTE_WINDOW_SECONDS` and the same `0:24` sits over
     * sixteen segments instead of twelve.
     *
     * The argument is D-145's: the out were given a live vote precisely so they watch the event
     * the living are in, and two windows means the couch's bar empties at a different rate from the
     * bar in the hallway — twelve feet away, in a lit room, for no reason a player could ever be
     * told. **In play they could already not disagree**, because [window] is the ballot length the
     * house sent and it replaces both rows at once; what is gone now is the fixture that disagreed
     * with itself on a bench.
     */
    fun onGhostMeeting(
        phase: MeetingPhase,
        said: Int? = null,
        window: Int? = null,
    ): Countdown? = when (phase) {
        MeetingPhase.CheckIn -> null
        MeetingPhase.Discussion -> on(ScreenId.Discussion, said, window)
        MeetingPhase.Vote -> on(ScreenId.GhostMeeting, said, window)
        MeetingPhase.Tally -> on(ScreenId.Tally, said, window)
    }

    /** What the couch's clock is counting down, in the phase's own words. */
    fun ghostClockLabel(phase: MeetingPhase): String = when (phase) {
        MeetingPhase.CheckIn -> "WAITING FOR THE HOUSE"
        MeetingPhase.Discussion -> "DISCUSSION ENDS IN"
        MeetingPhase.Vote -> "VOTING ENDS IN"
        MeetingPhase.Tally -> "LIGHTS OUT IN"
    }
}
