package home.someoneshome.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * **The clocks this phone runs for itself, and the one place they are counted.**
 *
 * Every moving thing in this app belongs to one of two owners, and the whole of the motion audit is
 * that distinction:
 *
 * - **House-fed.** The meeting's four windows and the System Integrity meter. The house decides when
 *   the talk ends and pushes every phone at once; a device that counted those down locally would be
 *   six phones counting slightly differently in a dark house, each of them certain. [Countdowns]
 *   says this outright and it stands: *nothing on the device advances* [PanelState.secondsLeft].
 * - **Client-owned.** A window this phone opened, that this phone closes, that nothing outside the
 *   phone can see. There are two, and both are on [Flow.autoAdvance] already: the self-test's fall
 *   through and D-159's ten-second scan window.
 *
 * **The second kind had no clock at all**, which is what the owner reported twice in one sentence —
 * *the loading screen's bar literally doesn't move* and *the scanning timeout doesn't countdown nor
 * does its countdown bar move*. Both screens drew a hand position and nothing ever moved the hand:
 * Boot's bar was a fixed 7:3 split of two `weight`s, and the scan drew [Countdowns]' fixture moment
 * of six seconds forever, over the twelve lit segments that go with it.
 *
 * ### The window is read off the timer, never restated
 *
 * [Countdowns] already promised this — *where the thing that happens is a timer this phone runs, the
 * window is read off that timer rather than restated; written as two numbers they drift, and the
 * drift is a phone that says nine seconds and moves after fifteen.* [millisOf] is that promise kept:
 * the length of the window comes out of the same [Flow.autoAdvance] row that fires when it ends, so
 * the bar cannot drain against one number while the light goes dark on another.
 *
 * ### It fails closed
 *
 * A screen not named in [screens] runs **no local clock**, whatever [Flow.autoAdvance] says about
 * it. The failure mode is a bar that does not move — which is what the owner found in thirty seconds
 * — and never a screen quietly counting down a window somebody else owns.
 */
object LocalWindows {

    /**
     * **The two screens whose clock is their own phone's business.**
     *
     * Named rather than derived from [Flow.autoAdvance], because most of that table is *not* this:
     * the ring's six seconds and the notice's nine are superseded by the house's own pushes the
     * moment a house is attached, and a screen that counted them down would be drawing a promise
     * the house is going to break. These two are different in kind — nothing outside this phone
     * knows the self-test is running or that the camera is open, so nothing outside this phone
     * could ever push them along.
     */
    val screens: Set<ScreenId> = setOf(
        // The self-test, 2.4 seconds, falling through to the permissions walk on its own. It is
        // also what a phone renders through the last two and a half seconds of a Surge (D-182),
        // where a progress bar climbing is exactly the picture: a rectangle rebooting in somebody's
        // hand while the room walks toward the light they just saw.
        ScreenId.Boot,
        // D-159's safety window. Ten seconds, then two vibrates and the light goes dark, so nobody is
        // left standing in a dark room holding a lit screen at a wall. The player cannot see this
        // screen while it works — the lit glass is facing the marker — so the countdown is not for
        // them to read; it is for the bar to be honest to whoever picks the phone up.
        ScreenId.Scan,
    )

    /**
     * How long [screen]'s own window runs, in milliseconds, or null where it has none.
     *
     * Read straight off the row that fires when it ends. A screen in [screens] with no
     * [Flow.autoAdvance] row gets null rather than a made-up length — a clock counting down to
     * nothing is the fault this whole file exists to close, and inventing a window here would
     * recreate it one field over.
     */
    fun millisOf(screen: ScreenId): Int? =
        if (screen in screens) Flow.autoAdvance[screen]?.afterMillis else null
}

/**
 * **What the self-test's progress bar tags itself with**, so the guard measures the bar rather than
 * the screen.
 *
 * A whole-frame pixel diff would go green on this screen for the wrong reason — the `STARTING` note
 * and the boot lines are static, but antialiasing anywhere would satisfy *something changed*. The
 * tags below are how `MotionTest` asks the narrower question: **did THIS rectangle move.** Both live
 * here rather than beside their screens because they exist for one instrument and are meaningless
 * apart from it.
 */
const val BOOT_BAR: String = "boot-bar"

/** What the scan window's twenty segments tag themselves with. See [BOOT_BAR]. */
const val SCAN_BAR: String = "scan-bar"

/**
 * How far into its own window a screen is.
 *
 * Two readings of one number, for the two shapes the design draws a window in: [fraction] for a bar
 * that fills as something progresses, [secondsLeft] for a clock that counts down to something
 * happening. They are the same value — the scan's readout and the twenty segments under it cannot
 * disagree, because there is nothing here for them to disagree about.
 */
class LocalWindow(val elapsedMillis: Long, val ofMillis: Int) {

    /** How much of the window has run, `0f..1f`. Progress fills; it does not drain. */
    val fraction: Float
        get() = if (ofMillis <= 0) 1f else (elapsedMillis.toFloat() / ofMillis).coerceIn(0f, 1f)

    /**
     * How much is left, in whole seconds, rounded **up**.
     *
     * Up, so a ten-second window reads `10` for its whole first second and reaches `0` only when it
     * is genuinely spent. Rounding down would show `9` the instant the screen opened and `0` for the
     * last full second of a window still running — a clock that lies at both ends.
     */
    val secondsLeft: Int
        get() {
            val left = (ofMillis - elapsedMillis).coerceAtLeast(0L)
            return ((left + 999L) / 1000L).toInt()
        }
}

/**
 * **The clock behind [LocalWindows], driven by the composition and by nothing else.**
 *
 * S2's idiom, stated for a window rather than for a Subroutine — see `screenMillis` in
 * `ScreensSubroutines.kt`, which this deliberately mirrors:
 *
 * **It is the frame clock and never a wall clock.** `Clock.System.now()` is unreplayable by
 * construction; a screen that read one would draw a different frame on every replay of the same
 * recording, which is the second promise in `project-context` broken by a drawing helper.
 *
 * **It is [withInfiniteAnimationFrameMillis] rather than `withFrameMillis`**, and that is what makes
 * a still render still. Under a test clock that auto-advances, Compose's own
 * [androidx.compose.animation.core.InfiniteAnimationPolicy] stops the frames, so a screenshot sweep
 * and the layout guard both render the screen's **opening position** — an empty progress bar, a full
 * scan window — rather than racing an unattended clock to whatever it reached. Under
 * `mainClock.autoAdvance = false` the frames arrive exactly where the test puts them, which is how
 * `MotionTest` can assert that the picture changes and then that it stops. In the app there is no
 * such policy installed and this is the ordinary frame clock.
 *
 * **It accumulates deltas rather than subtracting from a start**, because the frame clock's origin
 * is not this screen's: the first frame is zero by definition of the thing being measured.
 *
 * **It is bounded by the window**, like `StaticNoise`'s veil and unlike the two Subroutines that run
 * forever. A window that ends stops asking for frames, so the composition goes idle, which is both
 * the battery answer and the reason a test that waits for idle does not hang.
 *
 * The clock restarts on the **screen**, which is what the zero is a zero of. Walking off the scan
 * and back onto it is a new ten seconds, because it is a new scan.
 */
@Composable
fun localWindow(screen: ScreenId): LocalWindow? {
    val ofMillis = LocalWindows.millisOf(screen)
    var elapsed by remember(screen) { mutableStateOf(0L) }
    LaunchedEffect(screen, ofMillis) {
        if (ofMillis == null) return@LaunchedEffect
        var last = withInfiniteAnimationFrameMillis { it }
        while (elapsed < ofMillis) {
            val now = withInfiniteAnimationFrameMillis { it }
            elapsed = (elapsed + (now - last)).coerceAtMost(ofMillis.toLong())
            last = now
        }
    }
    return ofMillis?.let { LocalWindow(elapsed, it) }
}
