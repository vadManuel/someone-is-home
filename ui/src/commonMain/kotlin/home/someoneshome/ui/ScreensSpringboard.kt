package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The springboard — **identical for both roles.**
 *
 * Two pages, same icons, same positions, same page-dot count. An extra page would be a tell from
 * across a room, so the difference is only ever *what happens on tap*. A Resident who taps the
 * ability gets a plausible nothing.
 *
 * The governing principle for the whole device shows up here most clearly: *every rule the player
 * would have to remember becomes a state the device is in.* Files holds one item. The map opens
 * only at the station, because only a scan of the T card opens it (D-199). Messages cannot reply.
 * None of those are enforced by telling anyone off.
 */

/**
 * Page 1: the meter, the next Subroutine, the app grid.
 *
 * **The meter is frozen between meetings**, and that freeze is load-bearing — it is what lets an
 * Insider run Subroutines that write nothing without noticing the number fail to move.
 */
@Composable
fun HomeScreen(vals: PanelVals, widget: @Composable (PanelVals) -> Unit = { IntegrityWidget(it) }) {
    val go = navigator()
    // The swipe covers the page above the dock, so a swipe across the dock is not a page turn.
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().pageSwipe(PageTurn.Forward) { go(ScreenId.Page2) },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The one slot the house can take away. Everything below it is unchanged.
            widget(vals)

            // **THE TWO WIDGETS ARE ONE HEIGHT** (E-PAY-6). `IntrinsicSize.Min` on the row and
            // `fillMaxHeight` on both, rather than a height typed on each: a fixed number would be
            // wrong, silently, the first time a Subroutine name wraps.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // **CARRYING takes the widget's place, because carrying blocks the list** (D-168).
                val carried = vals.carrying
                if (carried != null) {
                    CarryingWidget(carried, Modifier.weight(1f).fillMaxHeight()) { go(ScreenId.Files) }
                } else Column(
                    Modifier.weight(1f).fillMaxHeight().border(2.dp, Ramp.Ink).tap { go(ScreenId.Work) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // ⚠️ NEXT is the queue D-114 replaced with a menu, surviving as a heading.
                    // Flagged at PanelVals.nextUp, not defended.
                    Tag("NEXT SUBROUTINE", Ramp.Mid)
                    Words(vals.nextUp.name, size = 40, lineHeight = 0.8)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The marker is named by its SHAPE, the token printed on the card the player
                        // is walking towards. Both halves are absent together on a line the house dealt
                        // with no home behind it. See OrderRow.Named.room.
                        vals.nextUp.room?.let {
                            Words(it, Modifier.testTag(ORDER_DESTINATION), size = 22, color = Ramp.Mid, lineHeight = 1.0)
                        }
                        vals.nextUp.marker?.let {
                            MarkerGlyph(it, 10.dp, Ramp.Mid, Modifier.testTag(ORDER_DESTINATION))
                        }
                        Box(Modifier.weight(1f))
                        // D-106's springboard surface: a mark inside the line the widget already has.
                        vals.nextUp.light?.let {
                            LightMark(it, Ramp.Mid, cell = 7.dp, height = 14.dp, gap = 2.dp, outline = true)
                        }
                    }
                }
                Column(
                    Modifier.width(122.dp).fillMaxHeight().border(1.dp, Ramp.Line).tap { go(ScreenId.Work) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Tag("COMPLETED", Ramp.Dim)
                    // Both numbers counted off the order the house sent. ASSIGNED is its length, the
                    // same for every seat and both roles by construction (D-129).
                    Words("${vals.nextUp.done}", size = 40, lineHeight = 0.8)
                    Words("OF ${vals.nextUp.total} ASSIGNED", size = 18, color = Ramp.Dim, lineHeight = 1.0)
                }
            }

            // **THE PHONE TILE IS GONE** (D-121: meetings are called only by scanning the meeting
            // card) **and so is TERMINAL** (D-199: the map opens only by scanning the T card). Their
            // slots stand empty, so the grid keeps its shape, and the shape is part of the parity every
            // phone on this screen is holding.
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppTile("FILES", APP_FILES, Modifier.weight(1f)) { go(ScreenId.Files) }
                    AppTile("NOTES", APP_NOTES, Modifier.weight(1f)) { go(ScreenId.Notes) }
                    AppTile("MESSAGES", APP_MESSAGES, Modifier.weight(1f)) { go(ScreenId.Reveal) }
                    Box(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppTile("SETTINGS", APP_SETTINGS, Modifier.weight(1f)) { go(ScreenId.Settings) }
                    // Camera is inert, not merely dim. See AppTile.
                    AppTile("CAMERA", APP_CAMERA, Modifier.weight(1f), onClick = null)
                    Box(Modifier.weight(1f))
                    Box(Modifier.weight(1f))
                }
            }

            Box(Modifier.weight(1f))
            PageBar(onPage = 0)
        }
        Dock()
    }
}

/**
 * A springboard app: a 56 square with its glyph, and the name beneath (D-202).
 *
 * **The inert tile is not a disabled tile that happens to look dim.** Camera has a dashed edge, the
 * hatch for a ground and no tap target at all, because an app that responded and then refused
 * would be a channel: the refusal itself carries information about what the device thinks you may
 * do.
 */
@Composable
private fun AppTile(label: String, path: String, modifier: Modifier, onClick: (() -> Unit)?) {
    val live = onClick != null
    Column(
        modifier.then(if (onClick != null) Modifier.tap(onClick) else Modifier)
            .padding(top = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).then(
                if (live) Modifier.background(Amber.Black).border(1.dp, Ramp.Line)
                else Modifier.hatch(Ramp.Fill).dashedBorder(Ramp.Line)
            ),
            contentAlignment = Alignment.Center,
        ) {
            PanelIcon(
                listOf(path), size = 28.dp, color = if (live) Ramp.Ink else Ramp.Line,
                strokeWidth = 2.5f, viewBox = 28f,
            )
        }
        Words(label, size = 20, color = if (live) Ramp.Mid else Ramp.Dim, lineHeight = 1.0)
    }
}

/** The mockup's app glyphs, on its 28-unit grid. */
private const val APP_FILES = "M4 8h8l2 3h10v12H4z"
private const val APP_NOTES = "M7 4h14v20H7z M11 10h6 M11 14h6 M11 18h4"
private const val APP_MESSAGES = "M4 6h20v13H12l-5 4v-4H4z"
private const val APP_SETTINGS = "M4 9h3 M13 9h11 M4 19h11 M21 19h3 M7 6h6v6H7z M15 16h6v6h-6z"
private const val APP_CAMERA = "M4 9h20v14H4z M10 9l2-3h4l2 3 M11 13h6v6h-6z"

/**
 * Page 2: the ability, the second slot, and the shared pool.
 *
 * **Both roles see this page lit exactly the same way.** The labels differ — REVOKE against
 * POWER, EGRESS against SUBSYS — but position, size and brightness do not, because a dimmer page
 * 2 would read across a dark room as *"this one has nothing to tap"*. Arming happens in place; it
 * never opens a new view, so nobody can be caught on a screen only one role has (D-142, ratifying
 * what was built and superseding the GDD's Status panel summoned by a corner long-press).
 *
 * ### Identical at rest, inert under the thumb
 *
 * The two halves of D-142, and they are not the same claim. *Identical at rest* is the parity
 * above. *Inert under the thumb* is that a *Resident's* tiles take *no pointer input at all* — no
 * press, no hold that runs and then declines, nothing from the first millisecond. A hold that
 * filled and then refused would be a self-test: press it, watch it, learn your own role. And it
 * would be worse than useless besides, because a bar filling in a dark house is world-observable
 * to whoever is standing behind the shoulder. **The resting tell the parity was built against must
 * not be answered by opening a behavioural one.**
 *
 * **Arming is a two-second hold** (D-141) on both tiles, because an accidental arm spends a full
 * cooldown with no cancel, and because the Egress is the misclick the design calls *game-ending*.
 */
@Composable
fun Page2Screen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    // The swipe covers the page above the dock, so a swipe across the dock is not a page turn.
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().pageSwipe(PageTurn.Back) { go(ScreenId.Home) },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AbilityTile(
                name = vals.abilityName,
                sub = vals.abilitySub,
                border = vals.revokeBorder,
                fill = vals.revokeFill,
                nameInk = vals.revokeInk,
                subInk = vals.revokeSubInk,
                bar = vals.revokeBar,
                barInk = vals.revokeBarInk,
                // A Resident's tile is not a control at all — see the KDoc above. The tile is real,
                // the cooldown bar is real, and neither answers the question.
                armable = vals.insider,
                onArm = actions.armRevoke,
            )
            AbilityTile(
                name = vals.secondName,
                sub = vals.secondSub,
                border = vals.tileBorder,
                fill = Color.Transparent,
                nameInk = vals.tileInk,
                subInk = Ramp.Dim,
                bar = vals.secondBar,
                barInk = vals.secondBarInk,
                armable = vals.insider,
                onArm = actions.armEgress,
            )

            // **Tier 2, on one shared bar** (`gdd.md:306`). Three tiles, one Access pool, and the bar
            // under them is the pool rather than any one ability's clock.
            //
            // **All three take the same two-second hold, and what it does differs**: two open a list,
            // and STATIC fires in place (D-186). A Resident's three are all inert, identically, and take
            // no pointer input at all (D-142).
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tier2Tile(vals.tier2A, vals, Modifier.weight(1f), armable = vals.insider) {
                        go(ScreenId.SurgeTargets)
                    }
                    // **STATIC, and the hold FIRES rather than opening anything** (D-186). Nothing is
                    // drawn afterwards and nothing may be: the house's answer is the jam every living
                    // phone gets, and it reaches this one as a bystander's (`gdd.md:398`).
                    Tier2Tile(vals.tier2B, vals, Modifier.weight(1f), armable = vals.insider) {
                        actions.fireStatic()
                    }
                    Tier2Tile(vals.tier2C, vals, Modifier.weight(1f), armable = vals.insider) {
                        go(ScreenId.IsolateTargets)
                    }
                }
                PoolBar(vals.tier2Bar, vals.tier2BarInk)
                // One line, and the same length for both roles (D-142). The mockup cut the second one.
                Words(vals.tier2Note, size = 22, color = Ramp.Dim, lineHeight = 1.1)
            }

            Box(Modifier.weight(1f))
            PageBar(onPage = 1)
        }
        Dock()
    }
}

/** A cooldown or pool bar: an 8-tall hairline box, filled to [fraction] (D-202). */
@Composable
private fun PoolBar(fraction: Float, ink: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, Ramp.Faint)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(ink))
    }
}

/**
 * One of the two arming tiles.
 *
 * **The hold draws nothing until a finger is on it**, which is what keeps the two roles' page 2
 * identical at rest while only one of them can be held: the progress line is drawn from the first
 * frame of a real hold and from no other state, so a Resident — whose tile takes no pointer input —
 * never has one, and neither role has one until somebody puts a thumb down.
 *
 * It is a hairline along the foot of the tile rather than the design's six-unit block. The tile
 * already carries a [FillBar] that means something the house said, and a second bar of the same
 * weight would be two progress meters arguing.
 */
@Composable
private fun AbilityTile(
    name: String,
    sub: String,
    border: Color,
    fill: Color,
    nameInk: Color,
    subInk: Color,
    bar: Float,
    barInk: Color,
    armable: Boolean,
    onArm: () -> Unit,
) {
    val hold = rememberHold(onArm, enabled = armable)
    Box(Modifier.fillMaxWidth().border(2.dp, border).background(fill).then(hold.surface)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Words(name, size = 52, color = nameInk, lineHeight = 0.8, tracking = 0.04)
            Words(sub, size = 22, color = subInk, lineHeight = 1.0)
            Box(Modifier.padding(top = 4.dp)) { PoolBar(bar, barInk) }
        }
        if (hold.holding) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(hold.fraction).height(3.dp)
                    .background(Ramp.Ink)
            )
        }
    }
}

/**
 * One of the three tier-2 tiles.
 *
 * [AbilityTile]'s discipline at a smaller size, and every line of that KDoc applies: **the hold
 * draws nothing until a finger is on it**, so both roles' tiles are identical at rest and only one
 * of them can be held. A Resident's takes no pointer input at all (D-142), so there is no progress
 * to run and then decline — the self-test that would be.
 *
 * ### What completing the hold does is open a list, and that is the ruling
 *
 * The two Tier-1 tiles arm in place and nothing opens (D-142). These two open a picker, because
 * Surge and Isolate have a target and the owner ruled on 2026-08-22 that the target comes off a
 * **list** — people for one, markers for the other. *The glow of reading the list is the deliberate
 * price*: Tier 2 is the deliberate tier, it is never used while running, and being harder to reach
 * is a feature (`gdd.md:306`).
 *
 * **The hold is still what pays for the deliberateness the list cannot** (D-141): the picker is
 * reachable, so a stray thumb that opened it would be a lit rectangle in a dark house at a moment
 * nobody chose. Two seconds is what an accident cannot supply.
 */
@Composable
private fun Tier2Tile(
    name: String,
    vals: PanelVals,
    modifier: Modifier,
    armable: Boolean,
    onArm: () -> Unit,
) {
    val hold = rememberHold(onArm, enabled = armable)
    Box(
        modifier.height(56.dp).border(1.dp, Ramp.Line).then(hold.surface),
        contentAlignment = Alignment.Center,
    ) {
        Words(name, size = 24, color = vals.tier2Ink, lineHeight = 1.0, tracking = 0.04)
        if (hold.holding) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(hold.fraction).height(3.dp)
                    .background(Ramp.Ink)
            )
        }
    }
}

/**
 * **Which page is showing: a two-segment bar, and not a control** (D-202, `gdd.md:561`).
 *
 * Two segments, always. The count is part of the parity: an extra page for one role would be
 * visible from across a room without anyone reading a word.
 *
 * **It takes no input.** It used to be two dots, the unlit one a tap target five units square,
 * which is a control nobody can find in the dark. Paging is the horizontal swipe the GDD's action
 * table names ([pageSwipe]), and the bar only says where you are.
 */
@Composable
private fun ColumnScope.PageBar(onPage: Int) {
    Box(Modifier.align(Alignment.CenterHorizontally).height(28.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.size(width = 56.dp, height = 4.dp).background(Ramp.Faint)) {
            repeat(2) { i ->
                Box(Modifier.weight(1f).fillMaxHeight().then(
                    if (i == onPage) Modifier.background(Ramp.Ink) else Modifier
                ))
            }
        }
    }
}

/** Which way a page swipe turns: towards page 2, or back to page 1. */
private enum class PageTurn { Forward, Back }

/**
 * **The horizontal swipe between the two pages** (`gdd.md:561`): *standard, muscle-memory, and
 * identical for both roles.*
 *
 * Right to left turns [PageTurn.Forward] to page 2; left to right turns [PageTurn.Back] to page 1.
 * It goes on the page body above the dock, so a swipe across the dock is not a page turn.
 *
 * **It cannot steal a tap**, for [edgeSwipeBack]'s reason: `detectHorizontalDragGestures` consumes
 * nothing until touch slop is exceeded, so a press on a tile that never moves still reaches it. A
 * swipe that starts on an Insider's arming tile is the hold's to keep, which is the right way round
 * for the one control in the game that must not misfire.
 *
 * It is not the edge swipe the GDD rejects (`gdd.md:566`): it starts anywhere on the page, and
 * [edgeSwipeBack] is absent on every in-round screen.
 */
@Composable
private fun Modifier.pageSwipe(turn: PageTurn, onTurn: () -> Unit): Modifier {
    val travel = with(LocalDensity.current) { PAGE_SWIPE_TRAVEL.toPx() }
    return pointerInput(turn) {
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                // Read once and reset before calling out, for edgeSwipeBack's reason.
                val go = when (turn) {
                    PageTurn.Forward -> travelled <= -travel
                    PageTurn.Back -> travelled >= travel
                }
                travelled = 0f
                if (go) onTurn()
            },
            onDragCancel = { travelled = 0f },
            onHorizontalDrag = { _, delta -> travelled += delta },
        )
    }
}

/**
 * How far a page swipe has to travel: forty units, the mockup's 48px at its 360 width. Far enough
 * to be a gesture rather than a slipped finger, short enough for a thumb in the dark.
 */
private val PAGE_SWIPE_TRAVEL: Dp = 40.u

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
        Modifier.fillMaxWidth().edgeLine(PanelSide.Top, Ramp.Faint, 1.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DockButton("SUBROUTINES", DOCK_WORK, Modifier.weight(1f)) { go(ScreenId.Work) }
        // SCAN is the one solid block on the springboard (D-202): the thing a player does most.
        DockButton("SCAN", DOCK_SCAN, Modifier.weight(1f), lit = true) { go(ScreenId.Scan) }
        DockButton("LOCK SCREEN", DOCK_LOCK, Modifier.weight(1f)) { go(ScreenId.Lock) }
    }
}

@Composable
private fun DockButton(
    label: String,
    path: String,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = if (lit) Amber.Black else Ramp.Ink
    Column(
        modifier.height(64.dp).then(if (lit) Modifier.background(Ramp.Ink) else Modifier).tap(onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelIcon(listOf(path), size = 24.dp, color = ink, strokeWidth = 2.5f, viewBox = 28f)
        Words(
            label, size = if (lit) 24 else 20, color = ink, lineHeight = 1.0,
            tracking = if (lit) 0.08 else 0.0, maxLines = 1,
        )
    }
}

/** The mockup's dock glyphs, on its 28-unit grid. */
private const val DOCK_WORK = "M5 5h18v18H5z M9 11h10 M9 16h6"
private const val DOCK_SCAN = "M4 10V4h6 M18 4h6v6 M24 18v6h-6 M10 24H4v-6 M9 14h10"
private const val DOCK_LOCK = "M7 13h14v11H7z M10 13V7h8v6"

/**
 * System Integrity: **a percentage, quantised into a bar**, and frozen until the next house
 * meeting.
 *
 * The KDoc here used to say *thirty-two segments rather than a percentage*, which was the design's
 * own argument — comparing quantities is perception and adding numbers is computation — and it
 * stopped being what this widget does. It draws both: the number, and [PanelVals.METER_SEGMENTS]
 * cells of bar under it.
 *
 * **The percentage is not a presentation choice, it is the disclosure boundary** (D-103, revision
 * 21). SystemIntegrity's real denominator is `(seats − insiders) × 7`, so a screen printing an
 * absolute total would hand over the Insider count by division — and D-103 lets a host hide that
 * count. The 32 is display resolution and a fact about pixels; `MeterDisclosureTest` reads the
 * panels to prove no screen ever prints the total itself.
 *
 * The bar earns its place anyway, for the design's second reason: the Egress countdown replaces
 * this widget in place, taking the Residents' only number away without anything changing size.
 */
@Composable
fun IntegrityWidget(vals: PanelVals) {
    KitLegendBox("SYSTEM INTEGRITY", Ramp.Mid, Ramp.Line) {
        // **Off [PanelVals.integrityPercent], not a literal.** A literal that happens to be right
        // is invisible until the house pushes the meter.
        Words(vals.integrityPercent, size = 76, lineHeight = 0.8)
        KitMeter(PanelVals.METER_SEGMENTS, vals.integrityLit, Ramp.Mid, Ramp.Faint)
        Words("Frozen until next house meeting", size = 22, color = Ramp.Dim, lineHeight = 1.0)
    }
}

/**
 * **The work-order widget while this phone is carrying** (D-168).
 *
 * The same box in the same slot as NEXT SUBROUTINE, so the springboard keeps its shape; only the
 * words change, and the last line is the rule the carry imposes.
 *
 * ⚠️ **NOTHING ELSE OPENS UNTIL IT IS DELIVERED is new copy and awaits the owner's eye** (D-200).
 */
@Composable
private fun CarryingWidget(carried: Carried, modifier: Modifier, open: () -> Unit) {
    Column(
        modifier.border(2.dp, Ramp.Ink).tap(open).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Tag("CARRYING", Ramp.Mid)
        Words(carried.item, size = 40, lineHeight = 0.8)
        Words("TO ${carried.to}", size = 22, color = Ramp.Mid, lineHeight = 1.0)
        Words("NOTHING ELSE OPENS UNTIL IT IS DELIVERED", size = 18, color = Ramp.Dim, lineHeight = 1.05)
    }
}
