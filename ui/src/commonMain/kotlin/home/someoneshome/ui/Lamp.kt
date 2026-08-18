package home.someoneshome.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
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
