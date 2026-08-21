package home.someoneshome.ui

import home.someoneshome.model.Cell
import home.someoneshome.model.MarkerId
import home.someoneshome.model.RoomKind

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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
    val pickRoomType: (RoomKind) -> Unit = {},
    val confirmStairs: () -> Unit = {},
    /** A tap on the plan. Opens the room under it, or does nothing where there is no room. */
    val openRoomAt: (Cell) -> Unit = {},
    /** The plan's own controls: a name, a preset chip, a storey, a room the host is done with. */
    val nameRoom: (String) -> Unit = {},
    val deleteRoom: () -> Unit = {},
    val openFloor: (String) -> Unit = {},
    val addFloor: () -> Unit = {},
    /**
     * Registration.
     *
     * The scan itself is not here: a card arriving is the camera's event and not a tap, so it
     * enters through [FlowModel.cardScanned] rather than through anything a screen can do. These
     * three are the taps that *answer* a scan — moving the terminal to the room the host is
     * standing in, taking it out of the house altogether, and pulling one card back off the sheet.
     */
    val moveTerminal: () -> Unit = {},
    val removeTerminal: () -> Unit = {},
    val forgetMarker: (MarkerId) -> Unit = {},
    /**
     * The saved homes.
     *
     * Every one of these is a side effect beside a navigation the screen names itself — a tapped
     * row opens *that* home and then goes to the detail screen, which is where every row goes.
     * The two that are not are [saveHome] and [deleteHome]: where a save lands depends on whether
     * it was refused, and a delete is a two-second hold rather than a tap, so both are declared in
     * [Flow.viaActions] and walked by a test.
     */
    val openSavedHome: (String) -> Unit = {},
    val mapNewHome: () -> Unit = {},
    val editOpenHome: () -> Unit = {},
    val duplicateHome: () -> Unit = {},
    val nameHome: (String) -> Unit = {},
    val saveHome: () -> Unit = {},
    val deleteHome: () -> Unit = {},
    /**
     * The lobby.
     *
     * [nameResident] and [typeLine] are typing; [attachToHome] is a tap on a network row, which
     * navigates as well because attaching to a home is going into its lobby. [handOverLine] is
     * not, for the reason [saveHome] is not: it can be refused, and a refused hand-over stays on
     * the screen with the reason on it rather than walking away from a promise the house never
     * received. It is declared in [Flow.viaActions] and walked by a test.
     *
     * [cycleInsiders] is the host's own setting and goes to the house rather than to the screen;
     * what comes back is what the row draws. [lightsOut] is **presentation only** — see
     * [FlowModel.lightsOut].
     */
    val nameResident: (String) -> Unit = {},
    val hostHome: (String) -> Unit = {},
    val attachToHome: (NearbyHome) -> Unit = {},
    val typeLine: (String) -> Unit = {},
    val handOverLine: () -> Unit = {},
    val cycleInsiders: () -> Unit = {},
    val lightsOut: () -> Unit = {},
    /**
     * **The meeting, and the four controls that report one phone and move nothing.**
     *
     * None of these navigates, and that is the whole point of them being here rather than being a
     * `go(...)` on a button: what happens after a check-in, a READY, or a vote depends on every
     * phone in the house, and no phone can count phones. They are declared in neither
     * [ScreenGraph] nor [Flow.viaActions] because they walk no edge at all — the meeting's
     * transitions are the house's, and [Flow.autoAdvance] stands in for it until there is one.
     *
     * [chooseVote] and [lockInVote] are two steps rather than one because the design's vote screen
     * shows *how many have voted, never what*: having voted is a state, distinct from having a
     * finger on a row, and the vote stays changeable after it either way.
     */
    val checkIn: () -> Unit = {},
    val sayReady: () -> Unit = {},
    val chooseVote: (VoteChoice) -> Unit = {},
    val lockInVote: () -> Unit = {},
    /**
     * **The Subroutines: one control that navigates and two that only ever echo.**
     *
     * [beginSubroutine] is here rather than on the caught-scan screen because BEGIN opens
     * whichever Subroutine the scanned card holds, and a screen cannot name a target that depends
     * on a piece of paper. It is declared in [Flow.viaActions] with the other decisions the
     * actions layer owns.
     *
     * [tapSubroutine] and [handOverSubroutine] navigate nowhere and decide nothing. A tap lights
     * what you touched — your own input, the one thing a screen may draw without asking — and the
     * entry goes to the house as an Intent that the *house* verifies (D-042). **Neither of them
     * takes a role**, and nothing they reach holds one: an Insider's Subroutine is a fake in the
     * ledger and identical everywhere a player can see it, which is rule 8 built rather than
     * promised.
     */
    val beginSubroutine: () -> Unit = {},
    val tapSubroutine: (Subroutine, Int) -> Unit = { _, _ -> },
    val handOverSubroutine: (Subroutine) -> Unit = {},
    /**
     * **A banner, swiped up.**
     *
     * Not a tap and not a navigation the banner can name: what a dismissal leaves you looking at
     * is whatever the notification arrived over, which the banner does not know. It is declared in
     * [Flow.viaActions] beside the other gestures no synthetic click can reach, and walked by a
     * test that really drags a finger.
     */
    val dismissNotification: () -> Unit = {},
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
 * **The smallest a control may be: 36 design units.**
 *
 * The canvas is [DESIGN_WIDTH] units wide and [DeviceCanvas] scales it to the panel, so a design
 * unit is `panelWidth / 300` points. Apple's minimum is 44pt square, and the narrowest phone this
 * app targets is 375 points across — a scale of 1.25, at which 44pt is 35.2 units. Rounded up:
 * **36 units clears 44pt on every phone in range, and clears it by more on the wider ones.**
 *
 * This is not a general accessibility uplift and the type sizes are untouched — the reference
 * device is a 2001 organiser and its 6-unit labels are the design (see [Label]). It is about the
 * *finger*, and specifically about this game's finger: a player standing in an unlit room, holding
 * the phone at an angle so it does not shine on anybody, forbidden to speak if they miss. A vote
 * cast on the wrong resident because a 22-unit row was a 27pt target is a game outcome decided by
 * a touch target.
 */
val TAP_TARGET: Dp = 36.u

/**
 * A tap target that is never shorter than [TAP_TARGET], whatever is drawn inside it.
 *
 * The minimum and the click sit on the same node, so the region that answers is the region with
 * the outline around it. A control whose touch area is larger than its visible edge steals presses
 * from its neighbour; one whose touch area is smaller teaches people to aim at the wrong place.
 */
@Composable
fun Modifier.tapTarget(onClick: () -> Unit): Modifier = heightIn(min = TAP_TARGET).tap(onClick)

/**
 * The design's primary control: a full-width bordered block with a centred label, in one of
 * three intensities — outlined, filled slate (a pre-game commit), or inverted amber (in-game
 * emphasis).
 *
 * **A button with no handler is not a control**, and it publishes no click action — the same fault
 * [RowButton] had and the same fix. It used to default to an empty lambda, so every decorative
 * block in the game answered a press by doing nothing, which is indistinguishable from one that is
 * broken. That matters most where a control is deliberately *present and inert*: the lobby's gated
 * LIGHTS OUT, and a readiness button that has already been pressed. Those have to keep their shape
 * so the layout does not move under a thumb, and must not keep their press.
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
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth().heightIn(min = TAP_TARGET).border(1.u, border).background(fill)
    Box(
        (if (onClick != null) base.tap(onClick) else base).padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, size = size, color = ink, tracking = tracking, align = TextAlign.Center)
    }
}

/**
 * A list row: label left, value or affordance pushed right.
 *
 * Saved homes, nearby homes, settings, the message list, and every host-setup line. The
 * right-hand slot is a composable rather than a string because it carries an address, marker
 * shapes and unread marks — things that are drawn, not written.
 *
 * **A row with no handler is not a control**, and it does not publish a click action. It used to:
 * the parameter defaulted to an empty lambda, so every settings line in the game was a tap target
 * that did nothing. That is a lie told by a row — worst on the lobby, where the same settings are
 * the host's to change and everybody else's to read, and where the difference between the two has
 * to be visible in what the screen does when you press it.
 */
@Composable
fun RowButton(
    modifier: Modifier = Modifier,
    border: Color = Amber.Edge,
    fill: Color = Color.Transparent,
    horizontalPadding: Dp = 7.u,
    verticalPadding: Dp = 8.u,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val base = modifier.fillMaxWidth().border(1.u, border).background(fill)
    Row(
        (if (onClick != null) base.tap(onClick) else base)
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
 * **The one field in this interface a host actually types into.**
 *
 * A [Readout] with a real keyboard behind it. Everywhere else the caret is drawn by [Caret] as a
 * promise that the device is listening; here the promise is kept, and the caret is the field's own
 * so there is exactly one of them and it sits where the next character will go.
 *
 * The text is passed through [transform] on the way in rather than on the way out — a home named
 * in lower case would come back shouted at the moment it was saved, and a field that rewrites what
 * you typed after you stop looking at it is a field nobody trusts.
 *
 * [hint] is drawn in the field's own face while it is empty, and it is not decoration. The design
 * drew a blinking [Caret] in every field to promise the device was listening; a real field shows
 * its caret only once it is focused, so an empty one with nothing in it reads as a box rather than
 * as somewhere to type. That is a fair mistake to make on the two screens in this game where
 * somebody is being asked for something.
 */
@Composable
fun ReadoutField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Double = 17.0,
    color: Color = Amber.BoneInk,
    tracking: Double = 0.06,
    hint: String = "",
    transform: (String) -> String = { it.uppercase() },
) {
    Box(modifier) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            Readout(hint, size = size, color = Amber.BoneFaint, tracking = tracking)
        }
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(transform(it)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontFamily = PanelType.readout,
                fontSize = size.sp,
                color = color,
                letterSpacing = tracking.em,
            ),
            singleLine = true,
            cursorBrush = SolidColor(color),
        )
    }
}

/**
 * **Hold for two seconds. The bar is the hold, not an animation of one.**
 *
 * The design's own control for the one irreversible thing in host setup. A tap cannot reach it:
 * the progress is driven by frames while the finger is down and resets the moment it lifts, so a
 * mis-tap in a pocket, a fumble in the dark, or a child holding the phone does not delete fifteen
 * minutes of somebody's evening.
 *
 * ### It publishes no click action, and that is the point
 *
 * `ScreenGraphTest` reads the whole screen graph off click *semantics* actions, which is how every
 * other control in this app is checked. A hold has none — a control that could be fired by one
 * synthetic click would not be a hold — so the edge it walks is declared in [Flow.viaActions] and
 * proved by a test that really holds a finger down for two seconds.
 */
@Composable
fun HoldToConfirm(
    label: String,
    restingNote: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    millis: Int = HOLD_MILLIS,
) {
    var holding by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }

    LaunchedEffect(holding) {
        if (!holding) {
            elapsed = 0
            return@LaunchedEffect
        }
        var last = withFrameMillis { it }
        var spent = 0L
        while (spent < millis) {
            val now = withFrameMillis { it }
            spent += now - last
            last = now
            elapsed = spent.coerceAtMost(millis.toLong()).toInt()
        }
        holding = false
        onConfirm()
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.u)) {
        // Two blocks rather than a fraction of one: the bar has to read at six units tall on a
        // 300-unit canvas, and a weight of zero is a box that still draws its own hairline.
        Row(Modifier.fillMaxWidth().height(6.u), horizontalArrangement = Arrangement.spacedBy(1.u)) {
            val done = elapsed.toFloat() / millis
            if (done > 0f) Box(Modifier.weight(done).fillMaxHeight().background(Amber.BoneDim))
            if (done < 1f) Box(Modifier.weight(1f - done).fillMaxHeight().background(Amber.BonePale))
        }
        PreNote(
            if (holding) "KEEP HOLDING . ${seconds(elapsed)}S OF ${seconds(millis)}S"
            else restingNote,
            tracking = 0.12, lineHeight = 1.0, align = TextAlign.Center,
        )
        Box(
            Modifier.fillMaxWidth().border(1.u, Amber.BoneFaint)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                }
                .padding(vertical = 13.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(label, size = 8.0, color = Amber.BoneDim, tracking = 0.16, align = TextAlign.Center)
        }
    }
}

/** Tenths, which is the resolution the design's own note is written at. */
private fun seconds(millis: Int): String = "${millis / 1000}.${millis % 1000 / 100}"

/** Two seconds, the design's number, and the only place it is written down. */
const val HOLD_MILLIS: Int = 2_000

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
