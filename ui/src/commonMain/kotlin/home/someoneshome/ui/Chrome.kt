package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

/**
 * The status bar — the one piece of chrome on screen for nearly the whole round.
 *
 * It carries the perimeter as a **glyph**, so the fact is stated once rather than written out in
 * words on thirty screens, and so a revoked player's device says exactly what everyone else's
 * says. The signal bars go dead the moment the round starts, because from then on the house owns
 * the network — a fiction and a rule at the same time.
 *
 * **Nothing here is role-dependent.** If a value in this function ever needs to consult the role,
 * that is the bug, not the feature.
 */
@Composable
fun StatusBar(vals: PanelVals) {
    Row(
        Modifier.fillMaxWidth().height(17.u)
            .edgeLine(PanelSide.Bottom, vals.edge)
            .padding(horizontal = 6.u),
        horizontalArrangement = Arrangement.spacedBy(5.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalBars(on = vals.signalOn, off = vals.signalOff)
        ReceptionGlyph(vals.receptionInk)

        Label(
            vals.carrier,
            modifier = Modifier.weight(1f),
            size = 7.0, color = vals.dim, tracking = 0.06,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )

        // The iris follows the panel's ink rather than being pinned to full intensity. The
        // design hardcodes #FFC759 here, which left it the single brightest element on the very
        // screens that dim everything else -- and a 7-unit lit ring is exactly the kind of thing
        // that carries across a dark room when the text does not.
        if (vals.armedGlyph) PerimeterGlyph(ring = vals.ink, core = vals.ink)
        if (vals.disarmedGlyph) PerimeterGlyph(ring = vals.dim, core = vals.edge)

        if (vals.lockChip) {
            Box(Modifier.border(1.u, Amber.Dim).padding(horizontal = 2.u)) {
                Label("LOCK", size = 7.0, color = Amber.Bright, tracking = 0.06)
            }
        }

        Readout(vals.clock, size = 11.0, color = vals.ink)
        Battery(ink = vals.ink, edge = vals.dim)
    }
}

/** Three bars, and after arming all three are spent. The house is the network now. */
@Composable
private fun SignalBars(on: Color, off: Color) {
    Row(
        Modifier.height(7.u),
        horizontalArrangement = Arrangement.spacedBy(1.u),
        verticalAlignment = Alignment.Bottom,
    ) {
        Block(2.u, 3.u, on)
        Block(2.u, 5.u, on)
        Block(2.u, 7.u, off)
    }
}

/** The inverted-triangle reception mark, built from three rules the way the panel would. */
@Composable
private fun ReceptionGlyph(ink: Color) {
    Column(
        Modifier.width(7.u),
        verticalArrangement = Arrangement.spacedBy(1.u),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Block(7.u, 1.u, ink)
        Block(5.u, 1.u, ink)
        Block(1.u, 1.u, ink)
    }
}

/**
 * The perimeter iris: a lit ring with a lit core when armed, the same iris drained when not.
 *
 * One symbol in two intensities rather than two symbols, so "the perimeter came down" reads as a
 * state change on something the player has been looking at all evening.
 */
@Composable
private fun PerimeterGlyph(ring: Color, core: Color) {
    Box(Modifier.size(7.u).border(1.u, ring, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(3.u).background(core))
    }
}

@Composable
private fun Battery(ink: Color, edge: Color) {
    Row(Modifier.width(11.u).height(6.u).border(1.u, edge).padding(1.u)) {
        Box(Modifier.weight(1f).fillMaxHeight().background(ink))
        Box(Modifier.weight(1f).fillMaxHeight().background(ink))
        Box(Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * The device screen: ground, status bar, and the content area every screen fills.
 *
 * The bone ground is painted *behind* everything rather than as the column's background, because
 * it is a property of the device being unarmed rather than of any one screen's layout — the same
 * reason the design paints it as an absolutely-positioned fill.
 *
 * ### The dim, and why it is not styling
 *
 * When [overlay] is present the whole panel — **status bar included** — drops to
 * [NOTIFIED_DIM], and the overlay draws at full intensity on top. Two jobs at once:
 *
 * 1. It makes the banner the only bright thing, so it is noticed.
 * 2. **It is a notification channel that works when the screen cannot be seen at all.** A phone
 *    held as a lamp faces away from its owner. They cannot read a banner, but they can see the
 *    light in the room drop. The buzz says something arrived; the dim confirms it.
 *
 * Which makes this a **lamp change**, and lamp changes are game state (project rule 5). Two
 * consequences that outlive this file:
 *
 * - The dim has to arrive as an authored effect from the rules, not as a UI transition, and it
 *   has to be a step rather than a fade. A ramp nobody authored is a signal nobody authored.
 * - **Every banner must go to everyone at once.** A lamp dimming is world-observable, so a
 *   notification addressed to fewer than all players is a beacon pointing at whoever got it. If
 *   a per-player notification is ever wanted, it cannot use this.
 */
@Composable
fun PanelFrame(
    vals: PanelVals,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Amber.Black)) {
        Box(
            Modifier.fillMaxSize()
                .then(if (overlay != null) Modifier.alpha(NOTIFIED_DIM) else Modifier)
        ) {
            if (vals.isPre) Box(Modifier.fillMaxSize().background(Amber.Bone))

            Column(Modifier.fillMaxSize()) {
                if (vals.statusVisible) StatusBar(vals)
                Column(Modifier.fillMaxWidth().weight(1f), content = content)
            }
        }
        if (overlay != null) {
            // Below the status bar, not over it. The bar is the one thing that stays put when
            // something arrives -- it is how the player confirms the perimeter and the clock are
            // still what they were, which is exactly the reassurance a takeover would remove.
            Box(
                Modifier.fillMaxSize()
                    .padding(top = if (vals.statusVisible) 17.u else 0.u)
            ) {
                overlay(this)
            }
        }
    }
}

/**
 * How far the panel drops when something arrives unasked.
 *
 * Deep enough to read as a light change across a room, shallow enough that the screen underneath
 * is still legible — the player must be able to see *what they were doing* behind the banner, or
 * it is a takeover after all.
 */
const val NOTIFIED_DIM: Float = 0.32f
