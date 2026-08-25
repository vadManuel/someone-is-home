package home.someoneshome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.Dp

/**
 * Shared furniture for the light-field screens — cold start and host setup.
 *
 * All of these run **while the house lights are still on**, which is the only reason they may be
 * a lit field at all. Nothing here has to survive a dark room, and the moment the perimeter arms
 * the device never shows a screen like this again.
 */

/** The design's standard pre-game page: eight units of padding, a six-unit rhythm. */
@Composable
fun PrePage(gap: Int = 6, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(gap.u),
        content = content,
    )
}

/** `margin-top:auto` — the spacer that pushes everything after it to the bottom. */
@Composable
fun ColumnScope.PushDown() {
    Box(Modifier.weight(1f))
}

/**
 * **The back arrow beside the page name — one component, and the only one** (E-PAY-6).
 *
 * The owner asked for the arrow and the left-edge swipe together, and they are one action: this
 * calls [PanelActions.back], the frame's `edgeSwipeBack` calls [PanelActions.back], and there is
 * nothing a chevron can do that a swipe cannot. What it used to be was `goes(to)` — a per-screen
 * hop to one named parent, which is right exactly when a screen has one way in.
 *
 * ### [fallback] is what keeps the screen graph a transcription
 *
 * A trail-driven back makes no `nav` call, and `ScreenGraphTest` reads the graph off `nav` calls —
 * so converting the chevrons alone would have deleted a dozen edges from [ScreenGraph] and left the
 * graph agreeing with itself about a smaller game. [fallback] is the screen's own declared parent,
 * taken when [PanelActions.canGoBack] is false, which is the state of a freshly rendered screen with
 * no trail behind it. So the test reads the same edges it always did and still fails on a
 * retargeted control, and on a phone the trail — which knows the host came in from the saved home
 * rather than from the maps list — wins.
 *
 * ### With no trail and no fallback there is no chevron
 *
 * Not a dimmed one, not an inert one: absent. `PanelButton` and `RowButton` both refuse to publish
 * a press they cannot answer, on the argument that a control which does nothing is
 * indistinguishable from one that is broken, and an arrow is the most literal case of that in the
 * app — it points somewhere.
 *
 *
 * ### It is a [TAP_TARGET], and that was found by breaking it
 *
 * The chevron's own box is 24 units — the glyph plus the design's padding — which is under the
 * 36-unit floor by a third. It went unnoticed because the sweep that measures controls
 * (`MeetingInputTest`) only walks the meeting's screens, and until N5b no arrow was drawn on one.
 * Injecting a chevron onto the ballot to check `BackNavigationTest` made that sweep report it, on a
 * screen where a missed press is a vote: *Vote/Resident — "‹" is 24u, under 36u*.
 *
 * So the minimum is enforced here rather than at the call sites, for [Modifier.tapTarget]'s reason —
 * the region that answers is the region the eye is aiming at. **The visible glyph does not change
 * size**; the box around it grows to a thumb, which is the same bargain every other control in this
 * app already has and matters more here than on most: this one gets pressed in a dark hallway by
 * somebody who has just realised they are on the wrong screen.
 */
@Composable
fun BackChevron(fallback: ScreenId? = null, ink: Color = Amber.BoneDim) {
    val actions = LocalActions.current
    val trailed = actions.canGoBack()
    if (!trailed && fallback == null) return
    Box(
        Modifier
            .testTag(BACK_CHEVRON)
            // A [TAP_TARGET] square with the glyph centred in it, and **no padding on top of that**
            // — the design's 9-unit gap before the title is already inside the square, so the title
            // sits where the eye expects and the finger gets the whole 36 units.
            .size(TAP_TARGET)
            .tap { if (trailed) actions.back() else fallback?.let(actions.nav) },
        contentAlignment = Alignment.Center,
    ) {
        Label("‹", size = 8.0, color = ink)
    }
}

/**
 * **The arrow, findable by a test that has to prove it is ABSENT.**
 *
 * A tag rather than a search for the glyph, because `‹` is one character in a font and the assertion
 * that matters is the negative one: *no screen the house is driving draws this*. Matching on text
 * would make that claim about a typeface. See `BackNavigationTest`, which renders every screen in
 * the game twice — once with a trail behind it and once without — and reads this.
 */
const val BACK_CHEVRON: String = "back-chevron"

/**
 * A section heading, with the back arrow beside the page name and a value pushed to the far right.
 *
 * **[back] is the fallback rather than the destination** — see [BackChevron]. Passing it declares
 * *the parent this screen would have if nothing were behind it*, which is what [ScreenGraph] holds;
 * it does not decide where a finger goes on a phone.
 *
 * **A heading with no [back] still gets the arrow once there is a trail.** That is the systemic
 * half of the owner's finding: the maps list, the join screen and the permissions walk name no
 * parent because they have none to name, and the arrow appears on them the moment something is
 * behind them. Nothing about a page name has to be edited for that to be true.
 */
@Composable
fun PreHeading(
    title: String,
    trailing: String? = null,
    back: ScreenId? = null,
    tracking: Double = 0.16,
    trailingSize: Double = 7.0,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BackChevron(back)
        Label(title, size = 7.0, color = Amber.BoneDim, tracking = tracking)
        if (trailing != null) {
            Label(
                trailing,
                modifier = Modifier.weight(1f),
                size = trailingSize, color = Amber.BoneFaint, tracking = 0.1,
                align = TextAlign.End,
            )
        }
    }
}

/** A bone-LCD row: label left, value right. The pre-game equivalent of a settings line. */
@Composable
fun PreRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    border: Color = Amber.BonePale,
    labelInk: Color = Amber.BoneDeep,
    valueInk: Color = Amber.BoneInk,
    size: Double = 7.0,
    verticalPadding: Dp = 6.u,
    onClick: (() -> Unit)? = null,
) {
    RowButton(
        modifier = modifier,
        border = border,
        verticalPadding = verticalPadding,
        // Passed through, not defaulted to an empty lambda: a settings line nobody may change is
        // a reading, and a reading must not answer a press.
        onClick = onClick,
    ) {
        Label(label, size = size, color = labelInk)
        Label(value, size = size, color = valueInk)
    }
}

/**
 * The commit button: filled slate.
 *
 * The only saturated control the interface has, and it appears only before the perimeter arms.
 * Nothing played in the dark is ever this colour.
 */
@Composable
fun SlateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tracking: Double = 0.16,
    size: Double = 8.0,
    verticalPadding: Dp = 10.u,
) {
    PanelButton(
        text,
        modifier = modifier,
        border = Amber.Slate,
        fill = Amber.SlateFill,
        ink = Amber.SlateInk,
        size = size,
        tracking = tracking,
        verticalPadding = verticalPadding,
        onClick = onClick,
    )
}

/** A block of small caps set as a centred note, the design's usual footnote. */
@Composable
fun PreNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Amber.BoneFaint,
    size: Double = 6.0,
    tracking: Double = 0.1,
    lineHeight: Double = 1.9,
    align: TextAlign? = null,
) {
    Label(
        text,
        modifier = modifier.fillMaxWidth(),
        size = size, color = color, tracking = tracking, lineHeight = lineHeight,
        align = align,
    )
}
