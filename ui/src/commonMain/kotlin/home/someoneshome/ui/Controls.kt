package home.someoneshome.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

/**
 * What a screen may do when tapped.
 *
 * Navigation, plus the handful of toggles the design exposes. **No screen calls into the rules**
 * — `ui` cannot see `core`. In play these become Intents posted to the authority, which may
 * refuse them, and the screen learns the outcome by being told what to draw next rather than by
 * reading a return value.
 */
class PanelActions(
    val nav: (ScreenId) -> Unit = {},
    val stepRevoke: () -> Unit = {},
    val toggleMarkers: () -> Unit = {},
    val toggleTorch: () -> Unit = {},
    val pickRoomType: (RoomType) -> Unit = {},
    val confirmStairs: () -> Unit = {},
)

val LocalActions: ProvidableCompositionLocal<PanelActions> =
    staticCompositionLocalOf { PanelActions() }

/**
 * The navigate function, captured for use inside non-composable click lambdas.
 *
 * `LocalActions.current` can only be read during composition, so a screen grabs it once at the
 * top and closes over it. Every screen in this module opens with `val go = navigator()`.
 */
@Composable
fun navigator(): (ScreenId) -> Unit = LocalActions.current.nav

/**
 * A tap target with no visual feedback of its own.
 *
 * **Deliberately no indication.** A ripple or a highlight is a change in lit pixel area the
 * rules did not author. On the springboard it would be worse than that: a Resident tapping the
 * ability must get exactly what an Insider gets, and a control that *looked* different when
 * pressed would answer the question the button exists to refuse. Echo of your own input is
 * allowed, but it has to be designed, not inherited from a theme.
 */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Navigate to [id] on tap. The overwhelmingly common case. */
@Composable
fun Modifier.goes(id: ScreenId): Modifier {
    val actions = LocalActions.current
    return tap { actions.nav(id) }
}

/**
 * The design's primary control: a full-width bordered block with a centred label, in one of
 * three intensities — outlined, filled slate (a pre-game commit), or inverted amber (in-game
 * emphasis).
 */
@Composable
fun PanelButton(
    text: String,
    modifier: Modifier = Modifier,
    border: Color = Amber.Dim,
    fill: Color = Color.Transparent,
    ink: Color = Amber.Bright,
    size: Double = 8.0,
    tracking: Double = 0.16,
    verticalPadding: Dp = 10.u,
    onClick: () -> Unit = {},
) {
    Box(
        modifier.fillMaxWidth().border(1.u, border).background(fill).tap(onClick)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, size = size, color = ink, tracking = tracking, align = TextAlign.Center)
    }
}

/**
 * A list row: label left, value or affordance pushed right.
 *
 * Saved homes, nearby networks, settings, the message list, and every host-setup line. The
 * right-hand slot is a composable rather than a string because it carries signal strength,
 * marker shapes and unread marks — things that are drawn, not written.
 */
@Composable
fun RowButton(
    modifier: Modifier = Modifier,
    border: Color = Amber.Edge,
    fill: Color = Color.Transparent,
    horizontalPadding: Dp = 7.u,
    verticalPadding: Dp = 8.u,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().border(1.u, border).background(fill).tap(onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The blinking caret on a field that accepts typing.
 *
 * It appears on the line handed to the house, which is deleted when the round ends, and on the
 * room-name field. A caret promises the device is listening; both screens keep that promise,
 * and one of them keeps nothing else.
 *
 * **Hard on/off, never a fade** — `steps(1)` in the source. A fading caret is a luminance ramp
 * nobody authored, which is the one thing the lamp rules forbid outright.
 */
@Composable
fun Caret(color: Color, size: Double = 17.0) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
        ),
        label = "caretAlpha",
    )
    Readout("_", size = size, color = color.copy(alpha = alpha))
}

/**
 * The slow pulse on an incoming call.
 *
 * Both animations in this file sit on screens that are *demanding attention*, never on ambient
 * chrome. A pulse anywhere else would be light the rules did not emit.
 */
@Composable
fun ringPulse(): Float {
    val transition = rememberInfiniteTransition(label = "ring")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ringAlpha",
    )
    return alpha
}
