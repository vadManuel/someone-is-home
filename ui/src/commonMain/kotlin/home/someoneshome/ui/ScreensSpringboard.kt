package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * The springboard — **identical for both roles.**
 *
 * Two pages, same icons, same positions, same page-dot count. An extra page would be a tell from
 * across a room, so the difference is only ever *what happens on tap*. A Resident who taps the
 * ability gets a plausible nothing.
 *
 * The governing principle for the whole device shows up here most clearly: *every rule the player
 * would have to remember becomes a state the device is in.* Files holds one item. Terminal reads
 * NO SIGNAL away from the station. Messages cannot reply. None of those are enforced by telling
 * anyone off.
 */

/**
 * Page 1: the meter, the next Subroutine, the app grid.
 *
 * **The meter is frozen between meetings**, and that freeze is load-bearing — it is what lets an
 * Insider run Subroutines that write nothing without noticing the number fail to move.
 */
@Composable
fun HomeScreen(vals: PanelVals) {
    val go = navigator()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 7.u).padding(top = 7.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            IntegrityWidget(vals)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.u)) {
                Column(
                    Modifier.weight(1f).border(1.u, Amber.Faint).tap { go(ScreenId.Work) }
                        .padding(horizontal = 7.u, vertical = 6.u),
                    verticalArrangement = Arrangement.spacedBy(3.u),
                ) {
                    Label("NEXT SUBROUTINE", size = 6.0, color = Amber.Dim, tracking = 0.12)
                    Label("SNIFF", size = 9.0, color = Amber.Bright, tracking = 0.06)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.u),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label("GARAGE", size = 6.5, color = Amber.Mid, tracking = 0.08)
                        // The marker is named by its SHAPE, not by a number. This is the same
                        // token printed on the card the player is walking towards.
                        MarkerShapes["triangle_up"]?.let { MarkerGlyph(it, 11.u, Amber.Mid) }
                    }
                }
                Column(
                    Modifier.width(78.u).border(1.u, Amber.Faint).tap { go(ScreenId.Work) }
                        .padding(horizontal = 7.u, vertical = 6.u),
                    verticalArrangement = Arrangement.spacedBy(3.u),
                ) {
                    Label("COMPLETED", size = 6.0, color = Amber.Dim, tracking = 0.12)
                    Readout("3", size = 26.0, color = Amber.Bright, lineHeight = 0.95)
                    Label("OF 7 ASSIGNED", size = 5.5, color = Amber.Dim, tracking = 0.08)
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.u),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.u)) {
                    SpringTile("FILES", PanelIcons.Files, Modifier.weight(1f)) { go(ScreenId.Files) }
                    SpringTile("NOTES", PanelIcons.Notes, Modifier.weight(1f)) { go(ScreenId.Notes) }
                    SpringTile("PHONE", PanelIcons.Phone, Modifier.weight(1f)) { go(ScreenId.Calling) }
                    SpringTile("MESSAGES", PanelIcons.Messages, Modifier.weight(1f)) { go(ScreenId.Reveal) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.u)) {
                    SpringTile(
                        "SETTINGS", PanelIcons.Settings, Modifier.weight(1f),
                        circles = PanelIcons.SettingsDots,
                    ) { go(ScreenId.Settings) }
                    SpringTile("TERMINAL", PanelIcons.Terminal, Modifier.weight(1f)) { go(ScreenId.TermNo) }
                    // Camera is inert, not merely dim. See SpringTile.
                    SpringTile(
                        "CAMERA", PanelIcons.Camera, Modifier.weight(1f),
                        circles = PanelIcons.CameraLens, enabled = false,
                    )
                    Box(Modifier.weight(1f))
                }
            }

            PageDots(onPage = 0) { go(ScreenId.Page2) }
        }
        Dock()
    }
}

/**
 * Page 2: the ability, the second slot, and the shared pool.
 *
 * **Both roles see this page lit exactly the same way.** The labels differ — REVOKE against
 * POWER, EGRESS against SUBSYS — but position, size and brightness do not, because a dimmer page
 * 2 would read across a dark room as *"this one has nothing to tap"*. Tapping arms in place; it
 * never opens a new view, so nobody can be caught on a screen only one role has.
 */
@Composable
fun Page2Screen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 7.u).padding(top = 7.u),
            verticalArrangement = Arrangement.spacedBy(7.u),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.u)) {
                AbilityTile(
                    modifier = Modifier.weight(1f),
                    name = vals.abilityName,
                    sub = vals.abilitySub,
                    border = vals.revokeBorder,
                    fill = vals.revokeFill,
                    nameInk = vals.revokeInk,
                    subInk = vals.revokeSubInk,
                    bar = vals.revokeBar,
                    barInk = vals.revokeBarInk,
                    // A Resident's tap does nothing, and does it silently. The button is real,
                    // the cooldown bar is real, and neither answers the question.
                    onClick = { if (vals.insider) actions.stepRevoke() },
                )
                AbilityTile(
                    modifier = Modifier.weight(1f),
                    name = vals.secondName,
                    sub = vals.secondSub,
                    border = vals.tileBorder,
                    fill = Color.Transparent,
                    nameInk = vals.tileInk,
                    subInk = Amber.Dim,
                    bar = vals.secondBar,
                    barInk = vals.secondBarInk,
                    onClick = { if (vals.insider) go(ScreenId.Banner) },
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.u)) {
                        listOf(vals.tier2A, vals.tier2B, vals.tier2C).forEach { name ->
                            Box(
                                Modifier.weight(1f).border(1.u, Amber.Edge).padding(vertical = 13.u),
                                contentAlignment = Alignment.Center,
                            ) {
                                Label(name, size = 6.5, color = vals.tier2Ink, tracking = 0.1)
                            }
                        }
                    }
                    FillBar(vals.tier2Bar, vals.tier2BarInk)
                    Label(
                        vals.tier2Note,
                        modifier = Modifier.fillMaxWidth(),
                        size = 5.5, color = Amber.Faint, tracking = 0.1, align = TextAlign.Center,
                    )
                }
            }

            PageDots(onPage = 1) { go(ScreenId.Home) }
        }
        Dock()
    }
}

@Composable
private fun AbilityTile(
    modifier: Modifier,
    name: String,
    sub: String,
    border: Color,
    fill: Color,
    nameInk: Color,
    subInk: Color,
    bar: Float,
    barInk: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().border(1.u, border).background(fill).tap(onClick)
            .padding(horizontal = 10.u, vertical = 8.u),
        verticalArrangement = Arrangement.spacedBy(6.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(name, size = 13.0, color = nameInk, tracking = 0.18)
        Label(sub, size = 6.0, color = subInk, tracking = 0.12)
        FillBar(bar, barInk)
    }
}

/**
 * Two dots, always. The count is part of the parity: an extra page for one role would be visible
 * from across a room without anyone reading a word.
 */
@Composable
private fun ColumnScope.PageDots(onPage: Int, onOther: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.u, bottom = 5.u),
        horizontalArrangement = Arrangement.spacedBy(5.u, Alignment.CenterHorizontally),
    ) {
        repeat(2) { i ->
            Box(
                Modifier.size(5.u)
                    .background(if (i == onPage) Amber.Bright else Amber.Faint)
                    .then(if (i == onPage) Modifier else Modifier.tap(onOther))
            )
        }
    }
}

/**
 * The dock: work, scan, lantern. Present on both pages, identical for both roles.
 *
 * SLEEP raising the lantern rather than blanking the screen is the whole conceit — the device
 * has no "off", because a phone that went dark would be a player who stopped emitting light.
 */
@Composable
private fun Dock() {
    val go = navigator()
    Row(
        Modifier.fillMaxWidth().edgeLine(PanelSide.Top, Amber.Faint).background(Amber.Edge),
    ) {
        DockButton("SUBROUTINES", PanelIcons.Work, Modifier.weight(1f), divider = true) { go(ScreenId.Work) }
        DockButton("SCAN", PanelIcons.Scan, Modifier.weight(1f), divider = true) { go(ScreenId.Scan) }
        DockButton("LOCK SCREEN", PanelIcons.Lock, Modifier.weight(1f)) { go(ScreenId.Lock) }
    }
}

@Composable
private fun DockButton(
    label: String,
    paths: List<String>,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .then(if (divider) Modifier.edgeLine(PanelSide.End, Amber.Faint) else Modifier)
            .tap(onClick)
            .padding(top = 7.u, bottom = 8.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelIcon(paths, size = 15.u, color = Amber.Bright, strokeWidth = 1.5f)
        Label(label, size = 5.5, color = Amber.Bright, tracking = 0.1)
    }
}

/**
 * System Integrity: 28 of 32, and **frozen until the next house meeting**.
 *
 * Thirty-two segments rather than a percentage, because comparing quantities is perception and
 * adding numbers is computation — and because the Egress countdown replaces this widget in place,
 * taking the Residents' only number away without anything changing size.
 */
@Composable
private fun IntegrityWidget(vals: PanelVals) {
    Column(
        Modifier.fillMaxWidth().border(1.u, Amber.Faint).padding(horizontal = 7.u, vertical = 6.u),
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Label("SYSTEM INTEGRITY", size = 6.5, color = Amber.Dim, tracking = 0.14)
            Row(verticalAlignment = Alignment.Bottom) {
                Readout("${vals.integrityLit}", size = 19.0, color = Amber.Bright, lineHeight = 1.0)
                Readout("/${PanelVals.METER_SEGMENTS}", size = 11.0, color = Amber.Dim)
            }
        }
        SegmentBar(
            total = PanelVals.METER_SEGMENTS,
            lit = vals.integrityLit,
            litColor = Amber.Bright,
            unlitColor = Amber.Edge,
        )
        Label("FROZEN UNTIL NEXT HOUSE MEETING", size = 6.0, color = Amber.Faint, tracking = 0.1)
    }
}
