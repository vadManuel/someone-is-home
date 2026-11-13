package home.someoneshome.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import home.someoneshome.model.MarkerShapes

/**
 * **Tier 2's two target lists** (`gdd.md:306`, `:313`, `:315`; the owner's 2026-08-22 targeting
 * ruling).
 *
 * *Surge and Isolate pick from a LIST — people, markers respectively. The glow of reading the list
 * is the deliberate price.*
 *
 * ### Everything on this page is light, and that is the design rather than a cost
 *
 * These are the only two screens in the game whose *existence* is the balance. Tier 1 arms in place
 * on a page the player is already on (D-142); Tier 2 makes you stop, open something, and read it, in
 * a dark house, holding the only light in it. **The rule that follows from that is: nothing here may
 * be quicker than reading.** No search, no recent-targets shortcut, no repeat-last-target. Every one
 * of those would buy back exactly what the price is.
 *
 * ### A tap fires. There is no second confirm and there must not be
 *
 * The deliberateness was paid at the tile, by two seconds of a finger (D-141), and the design has
 * one confirmation primitive rather than two: *the hold is not a confirmation dialogue — it asks for
 * no second screen, no second target, and no reading.* Stacking a confirm on the row as well would
 * ask for the same intent twice and leave the screen lit for the second half of it.
 *
 * ### Nothing on either screen reports what happened
 *
 * *No actor-side feedback on firing any ability. The attacker's device stays inert* (`gdd.md:398`).
 * A tap sends the request and walks back to page 2 — no confirmation line, no flash, no "SENT", and
 * **nothing that could differ between a shot that landed and one that did not**. A row that dimmed
 * itself after a Surge would be `gdd.md:344`'s revoke-detector rebuilt as a list.
 *
 * ### Neither screen is ever drawn for a Resident
 *
 * Not by a check on this page — there is no role branch anywhere in this file — but by there being
 * no way here. A Resident's tier-2 tiles take no pointer input at all (D-142), so the hold never
 * runs and the navigation never happens. **They are drawn identically for both roles when a test or
 * the cheat picker puts one on screen**, deliberately, so that a screenshot sweep sees the same
 * furniture either way and nothing about the drawing itself is a role.
 */

/**
 * **SURGE — the seat roster, by name** (`gdd.md:313`, `:392`).
 *
 * Every seat in the round, in seat order, including this phone's own row and including anybody
 * already out.
 *
 * ### Your own name is on the list on purpose
 *
 * *Insiders can target themselves with every ability* (`gdd.md:393`), and self-surge is the premium
 * alibi play: *you appear to be attacked in the most dramatic way available, but it burns your
 * longest cooldown and pulls the whole house to your location* (`gdd.md:342`). A roster that
 * omitted you would delete that play — and it would do worse than that, because *if only Residents
 * could ever be targeted, then being visibly targeted would be evidence of innocence, and every
 * ability would double as an alignment test.* **The row is unmarked**: nothing distinguishes it, so
 * a shoulder cannot read which one is yours.
 *
 * ### Revoked players stay on it, and this is where `gdd.md:344` is kept
 *
 * *Targeting an already-revoked player must appear to succeed on the actor's device — same
 * animation, same cooldown spent, no feedback whatsoever. Otherwise the panel becomes a
 * revoke-detector: a Insider could poll the roster and learn exactly who is out without ever finding
 * them.* This screen is that roster, and the way it keeps the rule is that **it has no way to know**:
 * `PanelState.roster` carries names and nothing else, there is no field on it that could say who is
 * out, and the house sends no such field. The type is the guarantee (rule 3).
 */
@Composable
fun SurgeTargetsScreen(vals: PanelVals) {
    val actions = LocalActions.current
    TargetList(
        title = vals.tier2A,
        // Deliberately not "PICK A TARGET" or anything that names a consequence. The house's
        // register, and a line that reads the same on a screen somebody else is looking over.
        note = "SELECT A RESIDENT",
    ) {
        vals.roster.forEachIndexed { index, name ->
            TargetRow(
                // Tagged by position, because position IS the seat: the actions layer turns a row
                // index back into a seat off the same list the house sent (D-115), exactly as the
                // ballot does. A tag naming the person would be a test asserting against a fixture.
                tag = "$TARGET_ROW$index",
                onPick = { actions.fireSurge(index) },
            ) {
                Words(name, Modifier.weight(1f), size = 28, lineHeight = 1.0)
            }
        }
    }
}

/**
 * **ISOLATE — the active markers, by shape and room** (`gdd.md:315`, D-150).
 *
 * The glyph and the room, which is what a player in a dark house navigates by, and which is the
 * same pair the work order's destination column draws — so a card named here and a card named there
 * are recognisably one card.
 *
 * ### The list is the house's and this screen filters nothing
 *
 * **The two Egress nodes are absent for the duration of an Egress** (D-150, F-002 amended), and they
 * are absent because the house did not send them. A client that removed rows of its own would be a
 * device deciding what an ability may be fired at, and the day the two rules disagreed the quieter
 * one would win. What this screen does with a shorter list is draw a shorter list.
 *
 * ### Nothing marks a marker that is already dark
 *
 * Which cards another Insider has taken offline is not this Insider's to know — the house sends no
 * such field and there is nothing here that could draw one. An Isolate fired at an
 * already-Isolated card is lawful and simply restarts it.
 */
@Composable
fun IsolateTargetsScreen(vals: PanelVals) {
    val actions = LocalActions.current
    TargetList(
        title = vals.tier2C,
        note = "SELECT A MARKER",
    ) {
        vals.isolateTargets.forEach { destination ->
            TargetRow(
                // The card id, which is what the request carries — a row keyed on the room would
                // fire at whichever card happened to be listed first in a room with two.
                tag = "$TARGET_ROW${destination.marker.value}",
                onPick = { actions.fireIsolate(destination.marker.value) },
            ) {
                // **A glyph this client's roster does not hold draws nothing**, and the row still
                // draws: the work order's `at?.let` exactly, and the same fail-closed direction. A
                // room with no shape beside it is a visible gap, where a substituted shape would
                // send somebody to the wrong card in the dark.
                MarkerShapes[destination.shape]?.let { MarkerGlyph(it, 14.dp, Ramp.Ink) }
                Words(destination.room, Modifier.weight(1f), size = 28, lineHeight = 1.0)
            }
        }
    }
}

/**
 * The shared furniture, so the two pickers cannot drift into two different screens.
 *
 * **It scrolls**, because a house can hold more markers than a phone can show and a list that
 * silently stopped at the bottom of the screen would be an ability that could not be fired at the
 * far end of the house. **There is no empty-state copy**: an empty list draws an empty column, which
 * is the honest picture of a round armed with no home, and a sentence explaining it would be a
 * sentence about the house's state on a screen that has no business holding an opinion about one.
 */
@Composable
private fun TargetList(title: String, note: String, rows: @Composable () -> Unit) {
    KitScreen {
        // The way out is the header's arrow, a plain tap back to page 2. A picker that could only
        // be left by firing would be a control that costs a cooldown to change your mind about.
        KitHeader(title, Palette.DARK, back = ScreenId.Page2, meta = note)
        Column(
            Modifier.fillMaxWidth().weight(1f).edgeLine(PanelSide.Top, Ramp.Faint, 1.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            rows()
        }
    }
}

/** One row on a hairline. A tap fires; see this file's header for why there is no second confirm. */
@Composable
private fun TargetRow(tag: String, onPick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).edgeLine(PanelSide.Bottom, Ramp.Faint, 1.dp)
            .tap(onPick).padding(horizontal = 2.dp).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The prefix every target row is tagged with, so a test can find one without knowing a name.
 *
 * Beside `HOLD_BLOCK` and for its reason: a tag typed into a test is a tag that stops matching the
 * day somebody renames a fixture, and what these tests are about is *which seat did the tap ask
 * for*, which is a position rather than a person.
 */
const val TARGET_ROW: String = "target-row-"

