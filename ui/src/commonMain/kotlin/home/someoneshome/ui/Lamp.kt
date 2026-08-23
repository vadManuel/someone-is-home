package home.someoneshome.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

/**
 * The lamp — the full-screen light every player sees by, and the only one the game modulates.
 *
 * **The state is read inside the draw lambda, not in composition.** That makes a change
 * invalidate draw only, skipping composition and layout entirely. Read it in composition instead
 * and every blackout costs a recomposition. Story 1.7 measured this path at 10.59 ms worst case
 * against an 8.335 ms frame across 10 000 trials — one late draw, with no collection in its
 * window. See spike-stackgate/FINDINGS.md; do not quote a number from memory here.
 *
 * `MutableIntState`, not `MutableState<Color>`: Color is a value class over ULong and would box
 * on every single write.
 *
 * **No error path may touch the lamp.** A screen that blanks because something threw is
 * indistinguishable from a revocation.
 */
@Composable
fun Lamp(argb: MutableIntState, onDrawn: (Int) -> Unit = {}) {
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            val value = argb.intValue
            drawRect(Color(value))
            onDrawn(value)
        }
    )
}

/**
 * **What the house has said about this phone's light, and it is not on [PanelState]** (rule 7).
 *
 * Two `Effect`s reach the lamp — `LampSet`, the level every phone is set to, and `Surged`, the slam
 * — and neither may pass through a state field. A `PanelState.copy` is a recomposition, and the
 * whole mitigation for losing the anonymous revoke is that the lamp goes dark in the **same frame** as
 * phone contact; an extra frame there is not a dropped frame, it is an un-anonymised revoke. So
 * these are `MutableIntState`s written straight by `FlowModel.houseSaid` and read only inside a
 * draw lambda, and nothing that watches them recomposes.
 *
 * **Rule 5 holds either way**: every value written here was carried on an effect the rules
 * constructed. There is no tween, no easing and no default — a level nobody sent is [UNSET], which
 * is a fourth thing and not a guess at a fifth.
 */
class LampState {

    /**
     * **The house's last authorised luminance, as a packed ARGB** ([Effect.LampSet], `LAMP_DIM`).
     *
     * [UNSET] means the house has said nothing, which on a phone with no house attached is always —
     * and the screens fall back to what the port drew rather than to black. A default of *dark*
     * would be a bench asserting a lamp the rules never set, on the one surface whose whole job is
     * to emit light.
     */
    val authorised: MutableIntState = mutableIntStateOf(UNSET)

    /**
     * **The Surge's slam, and it is the only thing in the game that draws over every screen.**
     *
     * [UNSET] for all but five seconds of a round. While it holds a value, [Lamp] paints the whole
     * panel at it — full for a second and a half, then black for one — and the values are the two
     * the **rules** chose and carried on `Effect.Surged`, never two this module picked.
     *
     * Separate from [authorised] rather than overwriting it, because the design says what happens
     * afterwards: *the phone returns to whatever `LampSet` it was last sent.* A slam that had
     * written over the authorised level would have nothing to return to.
     */
    val slam: MutableIntState = mutableIntStateOf(UNSET)

    /** The house set this phone's lamp. Written straight through; nothing recomposes. */
    fun houseSet(luminance: Int) {
        authorised.intValue = lampArgb(luminance)
    }

    /** One phase of a Surge. [UNSET] hands the screen back to whatever was under it. */
    fun slamTo(luminance: Int?) {
        slam.intValue = luminance?.let { lampArgb(it) } ?: UNSET
    }

    companion object {
        /**
         * **Nothing has been said**, drawn as fully transparent — so an unset [slam] costs one
         * `drawRect` of nothing rather than a branch in composition, and an unset [authorised]
         * leaves the screen underneath it exactly as the port drew it.
         *
         * Zero and not black: `0xFF000000` is a real, opaque, authorised blackout, and the two must
         * never be the same value. A lamp the house set to nothing and a lamp nobody has set are
         * opposite claims.
         */
        const val UNSET: Int = 0
    }
}

/**
 * **A luminance the rules chose (0–100), as the colour the panel emits at it.**
 *
 * The one place that conversion happens. `Effect.LampSet` and `Effect.Surged` both carry a number
 * on a scale `core` owns — `LAMP_DIM` is 1, the Surge's peak is 100, its black is 0 — and the
 * amber panel is what a phone in this game is made of, so the light it emits is [Amber.Bright]
 * scaled toward black. At 100 it is the panel's full ink; at 0 it is opaque black, which is a
 * *drawn* blackout and not an absent one.
 *
 * **Scaled and never faded.** This is a pure function of one number, called with numbers that
 * arrive on effects, so there is no value between two that anything here can invent (rule 5).
 */
fun lampArgb(luminance: Int): Int {
    val scale = luminance.coerceIn(0, 100) / 100f
    val lit = Color(
        red = Amber.Bright.red * scale,
        green = Amber.Bright.green * scale,
        blue = Amber.Bright.blue * scale,
        alpha = 1f,
    )
    return (lit.alpha * 255).toInt().shl(24) or
        (lit.red * 255).toInt().shl(16) or
        (lit.green * 255).toInt().shl(8) or
        (lit.blue * 255).toInt()
}
