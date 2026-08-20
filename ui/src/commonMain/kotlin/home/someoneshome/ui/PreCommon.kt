package home.someoneshome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** A back chevron with the design's negative margins, so it does not shift the title. */
@Composable
fun BackChevron(to: ScreenId, ink: Color = Amber.BoneDim) {
    Box(Modifier.goes(to).padding(end = 9.u, top = 7.u, bottom = 7.u)) {
        Label("‹", size = 8.0, color = ink)
    }
}

/** A section heading, optionally with a back chevron and a value pushed to the far right. */
@Composable
fun PreHeading(
    title: String,
    trailing: String? = null,
    back: ScreenId? = null,
    tracking: Double = 0.16,
    trailingSize: Double = 7.0,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) BackChevron(back)
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
        onClick = onClick ?: {},
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
