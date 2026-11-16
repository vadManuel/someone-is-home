package home.someoneshome.platform

/**
 * **The phone's idle timer: the one switch that decides whether the OS dims and locks a phone
 * nobody is touching** (T4 of the owner's 2026-11-15 rulings, D-221).
 *
 * One property, so the decisions in [ScreenWake] can be tested off a phone. On iOS it is
 * `UIApplication.idleTimerDisabled`, and like every UIKit property it is read and written on the
 * main thread, which is where the round's pushes are applied.
 */
interface IdleTimer {
    var disabled: Boolean
}

/**
 * **The screen stays awake from arming until the round ends or the player leaves, and then the
 * idle timer is put back as it was** (T4, D-221) — **and on the host, from the moment its lobby
 * opens** (H1, D-224).
 *
 * Without it a phone left alone during a round dims and then locks, and iOS suspends a locked app
 * within seconds: its radio stops advertising and scanning and its socket goes quiet (D-218 item 3).
 * Disabling the idle timer stops Auto-Lock **and nothing else**. It sets no brightness and draws
 * nothing: the lamp is still a pure function of state (rule 5). What it removes is the OS's own dim
 * before the lock, a fade nobody in the game authored and the one light change a phone made on its
 * own. D-118's two dims are the app's and are untouched.
 *
 * No background modes are added (T4). A player who presses the side button or leaves the app still
 * suspends it; that is a person's act, and D-187's notice is the house's answer to it.
 *
 * ### Two holds, one switch
 *
 * The round's ([hold], [release]) is D-221's and every phone takes it at arming. The host's
 * ([holdWhileHosting], [releaseHosting]) is H1's: the house runs on the host's phone, so a host
 * that sets the phone down in the lobby and lets it lock takes every seat's socket down with it.
 * There is one idle timer, so the two are one state with three values, not two counters:
 *
 * - **A round's hold takes over a hosting hold.** The timer stays off across arming, with no
 *   instant where it is back on between the two.
 * - **A round's release ends both**: hosting ends when the round does (the owner's H1). The host
 *   walking back into its lobby at NEW ROUND is the lobby opening again, and holds again.
 * - **A round's release leaves a hosting hold alone.** The round half is told the round is over
 *   when the house's `RoundEnded` broadcast comes back to the host's own phone, which is after the
 *   host's NEW ROUND has already re-held for the lobby; that late release must not end it.
 * - **A hosting release leaves a round's hold alone.** A host leaving mid-round has its round half
 *   release first, through `RoundSeam.left`.
 *
 * ### Restored, not switched off
 *
 * The first hold remembers what the timer was and the last release puts that back, so a phone
 * whose owner had disabled it for some other reason is left that way. Everything is idempotent: a
 * second hold does not overwrite the remembered value with the game's own `true`, and a release
 * with nothing of its kind held touches nothing.
 */
class ScreenWake(private val timer: IdleTimer) {
    private enum class For { Hosting, Round }

    private var holder: For? = null
    private var before: Boolean? = null

    /** Whether anything is holding the screen awake. */
    val holding: Boolean get() = holder != null

    /** Whether the host's lobby, and not a round, is what holds it. */
    val holdingForHosting: Boolean get() = holder == For.Hosting

    /** Keep the screen awake for a round. Called at arming; again on a resume, harmlessly. */
    fun hold() {
        if (holder == null) keepAwake()
        holder = For.Round
    }

    /** Put the idle timer back. Called when the round ends or the player leaves; a hosting hold is left alone. */
    fun release() {
        if (holder == For.Round) putBack()
    }

    /** Keep the host's screen awake while its lobby is open (H1). A round already holding keeps holding. */
    fun holdWhileHosting() {
        if (holder == null) {
            keepAwake()
            holder = For.Hosting
        }
    }

    /** Hosting ended: the house closed, or the host left. A round's hold is the round's to release. */
    fun releaseHosting() {
        if (holder == For.Hosting) putBack()
    }

    private fun keepAwake() {
        before = timer.disabled
        timer.disabled = true
    }

    private fun putBack() {
        val was = before ?: false
        holder = null
        before = null
        timer.disabled = was
    }
}

/** **No idle timer at all.** What a test or a bench holds when the screen is not the question. */
object NoIdleTimer : IdleTimer {
    override var disabled: Boolean
        get() = false
        set(_) = Unit
}

/** This phone's idle timer. `UIApplication.idleTimerDisabled` on iOS. */
expect fun deviceIdleTimer(): IdleTimer
