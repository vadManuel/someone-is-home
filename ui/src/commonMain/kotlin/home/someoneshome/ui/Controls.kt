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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
 * What a screen is allowed to do when tapped.
 *
 * Navigation only, plus the two prototype toggles the design exposes. **No screen calls into the
 * rules**, because `ui` cannot see `core` — in play these become Intents posted to the authority,
 * which may refuse them, and the screen finds out by being told what to draw next rather than by
 * reading a return value.
 */
class PanelActions(
    val nav: (ScreenId) -> Unit = {},
    val stepRevoke: () -> Unit = {},
    val toggleMarkers: () -> Unit = {},
)

val LocalActions: ProvidableCompositionLocal<PanelActions> = staticCompositionLocalOf { PanelActions() }

/**
 * A tap target with no visual feedback of its own.
 *
 * **Deliberately no indication.** A ripple or a highlight would be a change in lit pixel area
 * that the rules did not author, and on the springboard it would be worse than that: a Resident
 * tapping Revoke must get exactly what an Insider gets, and a control that *looked* different
 * when pressed would answer the question the button exists to refuse. Echo of your own input is
 * fine (project rule), but it has to be designed, not inherited from a theme.
 */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * The navigate function, captured for use inside non-composable click lambdas.
 *
 * `LocalActions.current` can only be read during composition, so a screen grabs it once at the
 * top and closes over it. Every screen in this module opens with `val go = navigator()`.
 */
@Composable
fun navigator(): (ScreenId) -> Unit = LocalActions.current.nav

/** Navigate to [id] on tap. The overwhelmingly common case. */
@Composable
fun Modifier.goes(id: ScreenId): Modifier {
    val actions = LocalActions.current
    return tap { actions.nav(id) }
}

/**
 * The design's primary control: a full-width bordered block with a centred label.
 *
 * Every "do the thing" button in the interface is this shape, in one of three intensities —
 * outlined, filled slate (pre-game commit), or inverted amber (in-game emphasis).
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
        modifier
            .fillMaxWidth()
            .border(1.u, border)
            .background(fill)
            .tap(onClick)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, size = size, color = ink, tracking = tracking, align = TextAlign.Center)
    }
}

/**
 * A list row: a label on the left, a value or affordance pushed to the right.
 *
 * Used for saved homes, nearby networks, settings and the message list. The right-hand slot is
 * a composable rather than a string because it carries signal strength, room counts and unread
 * marks — all of which are drawn, not written.
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
        modifier
            .fillMaxWidth()
            .border(1.u, border)
            .background(fill)
            .tap(onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A bordered block of explanatory text. The design's only container. */
@Composable
fun InfoBox(
    modifier: Modifier = Modifier,
    border: Color = Amber.Edge,
    fill: Color = Color.Transparent,
    padding: Dp = 7.u,
    gap: Dp = 0.u,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().border(1.u, border).background(fill).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * The blinking caret on a field that accepts typing.
 *
 * It appears on Notes, which **never saves**, and on the one line handed to the house, which is
 * deleted when the round ends. A caret is a promise that the device is listening; both screens
 * keep the promise and neither keeps the text.
 */
@Composable
fun Caret(color: Color, size: Double = 17.0) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // steps(1) in the source: a hard on/off, never a fade. A fading caret would be a
            // luminance ramp nobody authored, which is the one thing the lamp rules forbid.
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
 * The slow pulse on an incoming call — the design's only other animation.
 *
 * Both animations in this file are on screens that are *demanding attention*, never on ambient
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
