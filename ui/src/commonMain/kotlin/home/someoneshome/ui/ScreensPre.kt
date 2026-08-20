package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * The light-field screens: cold start and host setup.
 *
 * All of these run **while the house lights are still on**, which is the only reason they are
 * allowed to be a lit field at all. Nothing here has to survive a dark room, so the bone LCD and
 * its dark-on-light inversion are safe — and the moment the perimeter arms, the device never
 * shows a screen like this again.
 */

/** The design's standard pre-game page: eight units of padding, a six-unit rhythm. */
@Composable
private fun PrePage(
    gap: Int = 6,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(gap.u),
        content = content,
    )
}

/** A section heading, and optionally a value pushed to the far right. */
@Composable
private fun PreHeading(
    title: String,
    trailing: String? = null,
    back: ScreenId? = null,
    tracking: Double = 0.16,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            Box(
                Modifier
                    .goes(back)
                    .padding(start = 0.u, end = 9.u, top = 7.u, bottom = 7.u)
            ) {
                Label("‹", size = 8.0, color = Amber.BoneDim)
            }
        }
        Label(title, size = 7.0, color = Amber.BoneDim, tracking = tracking)
        if (trailing != null) {
            Label(
                trailing,
                modifier = Modifier.weight(1f),
                size = if (back != null) 7.0 else 6.0,
                color = Amber.BoneFaint,
                tracking = 0.1,
                align = TextAlign.End,
            )
        }
    }
}

/** A bone-LCD row: label left, value right. The pre-game equivalent of a settings line. */
@Composable
private fun PreRow(
    label: String,
    value: String,
    border: Color = Amber.BonePale,
    labelInk: Color = Amber.BoneDeep,
    valueInk: Color = Amber.BoneInk,
    size: Double = 7.0,
    verticalPadding: androidx.compose.ui.unit.Dp = 6.u,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
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

/** The commit button: filled slate. The only saturated control the interface has. */
@Composable
private fun SlateButton(
    text: String,
    onClick: () -> Unit,
    tracking: Double = 0.16,
    size: Double = 8.0,
    verticalPadding: androidx.compose.ui.unit.Dp = 10.u,
    modifier: Modifier = Modifier,
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

// ---------------------------------------------------------------------------------------------
// COLD START
// ---------------------------------------------------------------------------------------------

/**
 * Self-test on power-up. Falls through on its own.
 *
 * It reports `PERIMETER . . . IDLE` and `UPLINK . . . CONNECTED` — both true right now, and both
 * about to stop being true. The screen is doing exposition that the player will only understand
 * in retrospect, which is the correct amount of exposition for this game.
 */
@Composable
fun BootScreen() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.u, vertical = 12.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Label(
            "SOMEONE'S HOME",
            modifier = Modifier.padding(bottom = 8.u),
            size = 9.0,
            color = Amber.BoneInk,
            tracking = 0.2,
        )
        listOf(
            "MEM 640K . . . . . . . . . OK",
            "REGISTRY . . . . . 0 ENTRIES",
            "PERIMETER . . . . . . . IDLE",
            "LAMP ALLOCATION . . GRANTED",
        ).forEach { Label(it, size = 7.5, color = Amber.BoneDim, lineHeight = 1.9) }
        Label("UPLINK . . . . . CONNECTED", size = 7.5, color = Amber.BoneDeep, lineHeight = 1.9)

        Box(Modifier.weight(1f))

        Label("UPTIME 4331D 02:14", size = 7.5, color = Amber.BoneFaint, lineHeight = 1.9)
        Row(
            Modifier.fillMaxWidth().padding(top = 7.u).height(5.u),
            horizontalArrangement = Arrangement.spacedBy(1.u),
        ) {
            Box(Modifier.weight(7f).fillMaxHeight().background(Amber.BoneDim))
            Box(Modifier.weight(3f).fillMaxHeight().background(Amber.BonePale))
        }
        Label(
            "STARTING",
            modifier = Modifier.fillMaxWidth().padding(top = 5.u),
            size = 6.0,
            color = Amber.BoneFaint,
            tracking = 0.14,
            align = TextAlign.Center,
        )
    }
}

/**
 * Permissions, asked **at startup, in the light — never at arming**.
 *
 * Every input this game has is a permission: camera, Bluetooth, motion. A system prompt appearing
 * mid-round would be an unauthored screen state at the exact moment screen state is evidence, so
 * the whole set is collected while nothing is at stake and never asked for again.
 */
@Composable
fun PermsScreen() {
    val go = navigator()
    PrePage {
        PreHeading("PERMISSIONS")
        InfoBox(border = Amber.BoneDim) {
            Label(
                "Grant these now, while the lights are on. Nothing will be asked of you again " +
                    "once the perimeter is armed.",
                size = 8.0,
                color = Amber.BoneInk,
                lineHeight = 1.6,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            PreRow("LOCAL NETWORK", "GRANTED", size = 7.5)
            PreRow("CAMERA", "GRANTED", size = 7.5)
            PreRow("BLUETOOTH", "GRANTED", size = 7.5)
            PreRow(
                "MOTION & FITNESS", "PENDING",
                border = Amber.BoneDim, labelInk = Amber.BoneInk, valueInk = Amber.BoneDim,
                size = 7.5,
            )
            PreRow(
                "NOTIFICATIONS", "PENDING",
                labelInk = Amber.BoneFaint, valueInk = Amber.BoneFaint, size = 7.5,
            )
        }
        Box(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
            Label(
                "A SYSTEM PROMPT WILL APPEAR.\nIT IS NOT PART OF THE HOUSE.",
                size = 6.0,
                color = Amber.BoneFaint,
                tracking = 0.1,
                lineHeight = 1.9,
            )
            SlateButton("REQUEST MOTION ACCESS", { go(ScreenId.Join) })
        }
    }
}

/**
 * Your name, then the networks nearby.
 *
 * **Discovery is local only. No account, no internet.** That is a design promise and a technical
 * one at once — the house cannot reach the internet either, and the Residents are holding that
 * containment line.
 */
@Composable
fun JoinScreen() {
    val go = navigator()
    PrePage {
        InfoBox(border = Amber.BoneFaint, padding = 0.u) {
            Column(Modifier.padding(horizontal = 7.u, vertical = 5.u)) {
                Label(
                    "RESIDENT",
                    modifier = Modifier.padding(bottom = 3.u),
                    size = 6.0, color = Amber.BoneDim, tracking = 0.14,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Readout("ELLIOT", size = 19.0, color = Amber.BoneInk, tracking = 0.08)
                    Caret(Amber.BoneInk, size = 19.0)
                }
            }
        }
        Label("NETWORKS NEARBY", size = 7.0, color = Amber.BoneDim, tracking = 0.18)
        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            NetworkRow("THE BUNGALOW", "///", Amber.BoneFaint, Amber.BoneInk, Amber.BoneDim)
            NetworkRow("MUM & DAD'S", "//", Amber.BonePale, Amber.BoneDeep, Amber.BoneFaint)
            NetworkRow("FLAT 6", "/", Amber.BonePale, Amber.BoneDeep, Amber.BoneFaint)
        }
        Box(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
            PanelButton(
                "HOST A HOME INSTEAD",
                border = Amber.BoneFaint, ink = Amber.BoneDim,
                size = 7.5, tracking = 0.16, verticalPadding = 9.u,
                onClick = { go(ScreenId.Maps) },
            )
            Label(
                "DISCOVERY IS LOCAL ONLY\nNO ACCOUNT . NO INTERNET",
                modifier = Modifier.fillMaxWidth(),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.12, lineHeight = 1.9,
                align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NetworkRow(name: String, bars: String, border: Color, ink: Color, barInk: Color) {
    val actions = LocalActions.current
    RowButton(border = border, onClick = { actions.nav(ScreenId.Lobby) }) {
        Label(name, size = 8.0, color = ink, tracking = 0.08)
        Label(bars, size = 8.0, color = barInk, tracking = 0.08)
    }
}

// ---------------------------------------------------------------------------------------------
// HOST SETUP — once per house, in the light
// ---------------------------------------------------------------------------------------------

/**
 * Saved homes. Fifteen minutes of walking, kept forever.
 *
 * Map persistence is a story of its own (0.7) precisely because of that number: the setup walk is
 * real physical work, and a home that evaporated on reinstall would cost an evening.
 */
@Composable
fun MapsScreen() {
    val go = navigator()
    PrePage {
        PreHeading("SAVED HOMES", tracking = 0.18)
        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            SavedHomeRow("THE BUNGALOW", "2FL . 11RM", Amber.BoneFaint, Amber.BoneInk, Amber.BoneDim)
            SavedHomeRow("MUM & DAD'S", "2FL . 9RM", Amber.BonePale, Amber.BoneDeep, Amber.BoneFaint)
            SavedHomeRow("THE LAKE PLACE", "1FL . 6RM", Amber.BonePale, Amber.BoneDeep, Amber.BoneFaint)
        }
        Box(Modifier.weight(1f))
        PanelButton(
            "MAP A NEW HOME",
            border = Amber.BoneDim, ink = Amber.BoneInk,
            onClick = { go(ScreenId.Editor) },
        )
    }
}

@Composable
private fun SavedHomeRow(name: String, meta: String, border: Color, ink: Color, metaInk: Color) {
    val go = navigator()
    RowButton(border = border, onClick = { go(ScreenId.HomeDetail) }) {
        Label(name, size = 8.0, color = ink)
        Label(meta, size = 6.0, color = metaInk)
    }
}

/**
 * The plan editor. **Drag two corners; the shape becomes a room.**
 *
 * A grid rather than free rectangles, and the reasons are all about what the grid *removes*:
 * adjacency falls out of cell neighbours so no geometry is needed anywhere downstream, L-shaped
 * rooms just work because real houses have them, and there are no resize handles, overlap rules
 * or snapping to build. Simpler to author and simpler to implement at the same time.
 */
@Composable
fun EditorScreen(vals: PanelVals) {
    val go = navigator()
    PrePage {
        PreHeading("THE BUNGALOW", trailing = "6 ROOMS . 5 MARKERS", back = ScreenId.Maps, tracking = 0.14)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            Box(
                Modifier.weight(1f).border(1.u, Amber.BoneInk).padding(vertical = 4.u),
                contentAlignment = Alignment.Center,
            ) { Label("GROUND", size = 6.5, color = Amber.BoneInk, tracking = 0.1) }
            Box(
                Modifier.weight(1f).border(1.u, Amber.BonePale).goes(ScreenId.Floors)
                    .padding(vertical = 4.u),
                contentAlignment = Alignment.Center,
            ) { Label("UPPER", size = 6.5, color = Amber.BoneDim, tracking = 0.1) }
            Box(
                Modifier.width(26.u).fillMaxHeight().border(1.u, Amber.BonePale)
                    .goes(ScreenId.Floors).padding(vertical = 4.u),
                contentAlignment = Alignment.Center,
            ) { Label("+", size = 8.0, color = Amber.BoneDim) }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).border(1.u, Amber.BonePale)
                .goes(ScreenId.RoomEdit)
        ) {
            EditorPlan(Plan.editorCells(), Modifier.fillMaxSize())
            EditorLabels(Modifier.fillMaxSize(), chipInk = Amber.SlateFill)
        }

        InfoBox(border = Amber.BonePale, padding = 0.u) {
            Label(
                "DRAG TWO CORNERS TO ADD A ROOM.\nTAP A ROOM TO NAME IT OR ADD MARKERS.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.1, lineHeight = 1.8,
            )
        }

        PanelButton(
            vals.saveLabel,
            border = vals.saveEdge, fill = vals.saveFill, ink = vals.saveInk,
            onClick = { go(ScreenId.SaveName) },
        )
    }
}

/**
 * The save is **blocked**, and the screen says where a terminal belongs rather than only that one
 * is missing.
 *
 * *"Put it somewhere awkward"* is the load-bearing line. Standing at the terminal costs a Resident
 * time and puts them alone in the dark — that trade is what the whole map is built on, and a
 * terminal by the front door quietly deletes it.
 */
@Composable
fun NoTerminalScreen() {
    val go = navigator()
    PrePage(gap = 7) {
        PreHeading("THIS HOME NEEDS A TERMINAL", tracking = 0.14)
        InfoBox(border = Amber.BoneInk, padding = 8.u, gap = 5.u) {
            Label(
                "ONE MARKER, ANYWHERE, TAGGED TERMINAL",
                size = 9.0, color = Amber.BoneInk, tracking = 0.04, lineHeight = 1.5,
            )
            Label(
                "It is the only place in the house where residents can see who is where. " +
                    "Everywhere else their map reads NO SIGNAL.",
                size = 7.5, color = Amber.BoneDeep, lineHeight = 1.75,
            )
        }
        InfoBox(border = Amber.BonePale, gap = 5.u) {
            Label("PUT IT SOMEWHERE AWKWARD", size = 6.5, color = Amber.BoneDim, tracking = 0.12)
            Label(
                "A back room, a basement, the far end of the house — somewhere nobody passes by " +
                    "accident. Standing at it costs a resident time and puts them alone in the " +
                    "dark, which is the trade the whole map is built on.",
                size = 7.0, color = Amber.BoneDim, lineHeight = 1.8,
            )
        }
        InfoBox(border = Amber.BonePale, padding = 0.u) {
            Label(
                "NOT IN A STAIRS OR PASSAGE ROOM.\nONE PER HOME. YOU CAN MOVE IT LATER.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.1, lineHeight = 1.9,
            )
        }
        Box(Modifier.weight(1f))
        SlateButton("PLACE THE TERMINAL", { go(ScreenId.Editor) }, verticalPadding = 11.u)
    }
}

/** The plan above, a panel below. The panel is where a room gets its name, type and markers. */
@Composable
private fun HeldPlanPage(
    title: String,
    trailing: String,
    panel: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(8.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(title, size = 7.0, color = Amber.BoneDim, tracking = 0.14)
                Label(trailing, size = 7.0, color = Amber.BoneFaint, tracking = 0.14)
            }
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(Plan.COLS.toFloat() / Plan.ROWS)
                    .align(Alignment.CenterHorizontally)
                    .border(1.u, Amber.BonePale)
            ) {
                EditorPlan(
                    Plan.editorCells(Plan.rooms.first { it.name == Plan.HERE }),
                    Modifier.fillMaxSize(),
                )
                EditorLabels(Modifier.fillMaxSize(), held = Plan.HERE)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Amber.Bone)
                .edgeLine(PanelSide.Top, Amber.BoneInk, 2.u)
                .padding(8.u),
            verticalArrangement = Arrangement.spacedBy(5.u),
            content = panel,
        )
    }
}

/** Name chips, type, markers, and a delete that names what it takes with it. */
@Composable
fun RoomEditScreen() {
    val go = navigator()
    HeldPlanPage("GARAGE", "ROOM PANEL") {
        Row(horizontalArrangement = Arrangement.spacedBy(3.u)) {
            RoomChip("KITCHEN", false)
            RoomChip("LIVING", false)
            RoomChip("GARAGE", true)
            RoomChip("BATH 1", false)
        }
        InfoBox(border = Amber.BoneInk, padding = 0.u) {
            Column(Modifier.padding(horizontal = 7.u, vertical = 5.u)) {
                Label(
                    "ROOM NAME",
                    modifier = Modifier.padding(bottom = 3.u),
                    size = 6.0, color = Amber.BoneDim, tracking = 0.12,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Readout("GARAGE", size = 17.0, color = Amber.BoneInk, tracking = 0.06)
                    Caret(Amber.BoneInk)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            TypeChip("ROOM", true, Modifier.weight(1f))
            TypeChip("STAIRS", false, Modifier.weight(1f))
            TypeChip("PASSAGE", false, Modifier.weight(1f))
        }
        RowButton(
            border = Amber.BoneInk, verticalPadding = 6.u,
            onClick = { go(ScreenId.MarkerSheet) },
        ) {
            Label("MARKERS", size = 7.0, color = Amber.BoneInk, tracking = 0.02)
            Label("2  ›", size = 7.0, color = Amber.BoneInk, tracking = 0.02)
        }
        SlateButton("DONE", { go(ScreenId.Editor) })
        PanelButton(
            "DELETE ROOM AND ITS 2 MARKERS",
            border = Amber.BonePale, ink = Amber.BoneDim,
            onClick = { go(ScreenId.Editor) },
        )
    }
}

@Composable
private fun RoomChip(name: String, held: Boolean) {
    Box(
        Modifier
            .border(1.u, if (held) Amber.BoneInk else Amber.BonePale)
            .background(if (held) Amber.BoneInk else Color.Transparent)
            .padding(horizontal = 5.u, vertical = 3.u)
    ) {
        Label(
            name, size = 6.5, tracking = 0.06,
            color = if (held) Amber.Bone else Amber.BoneDim,
        )
    }
}

@Composable
private fun TypeChip(name: String, held: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.u, if (held) Amber.BoneInk else Amber.BonePale)
            .background(if (held) Amber.BoneInk else Color.Transparent)
            .padding(vertical = 4.u),
        contentAlignment = Alignment.Center,
    ) {
        Label(
            name, size = 6.5, tracking = 0.08,
            color = if (held) Amber.Bone else Amber.BoneDim,
        )
    }
}

/**
 * Markers in a room — **a list, not a placement.**
 *
 * There are no cells anywhere in this sheet. A marker belongs to a room and nothing finer: the
 * house picks which marker a Subroutine opens at, so a host who could position one precisely
 * would be authoring information the game deliberately does not carry.
 */
@Composable
fun MarkerSheetScreen() {
    val go = navigator()
    HeldPlanPage("MARKERS", "GARAGE . 2") {
        Row(
            Modifier.fillMaxWidth().height(63.u),
            horizontalArrangement = Arrangement.spacedBy(3.u),
        ) {
            MarkerChip("M03 ×")
            MarkerChip("M04 ×")
            Box(
                Modifier
                    .dashedBorder(Amber.Slate)
                    .padding(horizontal = 6.u, vertical = 4.u)
            ) { Label("+ ADD", size = 6.5, color = Amber.Slate, tracking = 0.06) }
        }
        Label(
            "TAP A MARKER TO REMOVE IT.",
            modifier = Modifier.fillMaxWidth(),
            size = 6.0, color = Amber.BoneFaint, tracking = 0.1, align = TextAlign.Center,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .dashedBorder(Amber.BonePale)
                .padding(horizontal = 7.u, vertical = 5.u),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("TERMINAL", size = 6.5, color = Amber.BoneDim)
            Label("IN HALL", size = 6.5, color = Amber.BoneInk)
        }
        Label(
            "THE HOUSE PICKS WHICH MARKER\nA SUBROUTINE OPENS AT.",
            modifier = Modifier.fillMaxWidth(),
            size = 6.0, color = Amber.BoneFaint, tracking = 0.1, lineHeight = 1.8,
            align = TextAlign.Center,
        )
        SlateButton("DONE", { go(ScreenId.RoomEdit) })
    }
}

@Composable
private fun MarkerChip(text: String) {
    Box(Modifier.border(1.u, Amber.BoneInk).padding(horizontal = 6.u, vertical = 4.u)) {
        Label(text, size = 6.5, color = Amber.BoneInk, tracking = 0.06)
    }
}

/**
 * Floors are **additive and unordered, and nothing connects them.**
 *
 * No vertical-connection logic exists, by decision: the app renders what was drawn. A stairwell
 * that "led" somewhere would be geometry the game would then have to reason about, for a benefit
 * the counts-not-dots map never cashes in.
 */
@Composable
fun FloorsScreen() {
    val go = navigator()
    PrePage {
        PreHeading("FLOORS", trailing = "2 IN THIS HOME", back = ScreenId.Editor, tracking = 0.14)
        PreRow("GROUND", "6 ROOMS . 5 MARKERS", size = 7.5, verticalPadding = 7.u)
        PreRow(
            "UPPER", "5 ROOMS . 4 MARKERS",
            border = Amber.BoneInk, labelInk = Amber.BoneInk, size = 7.5, verticalPadding = 7.u,
        )
        PanelButton(
            "ADD A FLOOR",
            border = Amber.BonePale, ink = Amber.BoneDim,
            size = 7.5, tracking = 0.14, verticalPadding = 9.u,
            onClick = { go(ScreenId.Editor) },
        )
        InfoBox(border = Amber.BonePale, padding = 0.u) {
            Label(
                "FLOORS ARE ADDITIVE AND UNORDERED.\nNOTHING CONNECTS THEM. THE APP\nRENDERS WHAT YOU DREW.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.1, lineHeight = 1.8,
            )
        }
        Box(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
            PreRow("RENAME UPPER", ">", verticalPadding = 7.u)
            PreRow("DELETE UPPER", ">", border = Amber.BoneInk, labelInk = Amber.BoneInk, verticalPadding = 7.u)
            Label(
                "DELETING UPPER REMOVES 5 ROOMS AND\n4 MARKERS. HOLD TWO SECONDS.",
                modifier = Modifier.fillMaxWidth(),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.1, lineHeight = 1.8,
                align = TextAlign.Center,
            )
        }
    }
}

/** The plan is drawn; now name it. A nickname, not an address — nothing leaves this device. */
@Composable
fun SaveNameScreen() {
    val go = navigator()
    PrePage(gap = 7) {
        PreHeading("SAVE HOME", back = ScreenId.Editor)
        InfoBox(border = Amber.BoneDim) {
            Label(
                "NAME THIS HOME",
                modifier = Modifier.padding(bottom = 4.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.14,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Readout("THE BUNGALOW", size = 21.0, color = Amber.BoneInk, tracking = 0.06)
                Caret(Amber.BoneInk, size = 21.0)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            PreRow("FLOORS", "2", labelInk = Amber.BoneDeep)
            PreRow("ROOMS", "11", labelInk = Amber.BoneDeep)
            PreRow("MARKERS", "9", labelInk = Amber.BoneDeep)
        }
        InfoBox(border = Amber.BonePale, padding = 0.u) {
            Label(
                "A NICKNAME, NOT AN ADDRESS.\nNOTHING LEAVES THIS DEVICE.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.1, lineHeight = 1.9,
            )
        }
        Box(Modifier.weight(1f))
        SlateButton("SAVE HOME", { go(ScreenId.HomeDetail) }, tracking = 0.18, verticalPadding = 11.u)
    }
}

/** Host with it, edit the plan, rename, duplicate. */
@Composable
fun HomeDetailScreen() {
    val go = navigator()
    PrePage {
        PreHeading("SAVED HOME", trailing = "PLAYED 3 TIMES", back = ScreenId.Maps)
        InfoBox(border = Amber.BoneDim, gap = 4.u) {
            Label("THE BUNGALOW", size = 11.0, color = Amber.BoneInk, tracking = 0.06)
            Label(
                "2 FLOORS . 11 ROOMS . 9 MARKERS\nLAST PLAYED 12 AUGUST",
                size = 6.0, color = Amber.BoneDim, tracking = 0.1, lineHeight = 1.9,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            RowButton(
                border = Amber.Slate, fill = Amber.SlateFill,
                onClick = { go(ScreenId.Lobby) },
            ) {
                Label("HOST WITH THIS HOME", size = 8.0, color = Amber.SlateInk)
                Label(">", size = 8.0, color = Amber.BoneDim)
            }
            RowButton(border = Amber.BoneFaint, onClick = { go(ScreenId.Editor) }) {
                Label("EDIT THE PLAN", size = 8.0, color = Amber.BoneDeep)
                Label(">", size = 8.0, color = Amber.BoneFaint)
            }
            RowButton(border = Amber.BoneFaint, onClick = { go(ScreenId.SaveName) }) {
                Label("RENAME", size = 8.0, color = Amber.BoneDeep)
                Label(">", size = 8.0, color = Amber.BoneFaint)
            }
            RowButton(border = Amber.BonePale) {
                Label("DUPLICATE", size = 8.0, color = Amber.BoneDim)
                Label(">", size = 8.0, color = Amber.BoneFaint)
            }
        }
        Box(Modifier.weight(1f))
        PanelButton(
            "DELETE THIS HOME",
            border = Amber.BoneFaint, ink = Amber.BoneDim,
            size = 7.5, tracking = 0.14, verticalPadding = 9.u,
            onClick = { go(ScreenId.Delete) },
        )
    }
}

/**
 * Hold two seconds to delete. **Keep is the lit button.**
 *
 * The destructive control is the outlined one and the safe control is filled — inverted from the
 * usual habit, on purpose. Fifteen minutes of walking a house is the thing being protected, and
 * the brighter button should be the one that protects it.
 */
@Composable
fun DeleteScreen() {
    val go = navigator()
    PrePage(gap = 7) {
        PreHeading("DELETE HOME", back = ScreenId.HomeDetail)
        InfoBox(border = Amber.BoneDim, padding = 8.u, gap = 5.u) {
            Label("THE BUNGALOW", size = 10.0, color = Amber.BoneInk, tracking = 0.06)
            Label(
                "2 floors, 11 rooms and 9 markers. About fifteen minutes of walking this home.",
                size = 7.5, color = Amber.BoneDeep, lineHeight = 1.7,
            )
        }
        InfoBox(border = Amber.BonePale) {
            Label(
                "This cannot be undone and nothing is backed up off this device.",
                size = 7.0, color = Amber.BoneDim, tracking = 0.06, lineHeight = 1.9,
            )
        }
        Box(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(7.u)) {
            Row(
                Modifier.fillMaxWidth().height(6.u),
                horizontalArrangement = Arrangement.spacedBy(1.u),
            ) {
                Box(Modifier.weight(3f).fillMaxHeight().background(Amber.BoneDim))
                Box(Modifier.weight(7f).fillMaxHeight().background(Amber.BonePale))
            }
            Label(
                "KEEP HOLDING . 0.6S OF 2.0S",
                modifier = Modifier.fillMaxWidth(),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.12, align = TextAlign.Center,
            )
            PanelButton(
                "HOLD TO DELETE",
                border = Amber.BoneFaint, ink = Amber.BoneDim, verticalPadding = 13.u,
                onClick = { go(ScreenId.Maps) },
            )
            SlateButton("KEEP THIS HOME", { go(ScreenId.HomeDetail) }, verticalPadding = 11.u)
        }
    }
}

/**
 * Every dial the host owns. **Locks at arming**, and stamps into the recording.
 *
 * Balance values cannot be edited mid-round — a round whose numbers changed under it is a round
 * that cannot be replayed, and replay is the only debugging this game has.
 */
@Composable
fun LobbyScreen() {
    val go = navigator()
    PrePage {
        InfoBox(border = Amber.BoneFaint, padding = 0.u, gap = 4.u) {
            Column(Modifier.padding(horizontal = 7.u, vertical = 6.u)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("THE BUNGALOW", size = 7.0, color = Amber.BoneDim, tracking = 0.12)
                    Label("6 JOINED", size = 7.0, color = Amber.BoneInk, tracking = 0.12)
                }
                Row(
                    Modifier.padding(top = 4.u),
                    horizontalArrangement = Arrangement.spacedBy(3.u),
                ) {
                    SeatChip("ELLIOT", Amber.BoneInk)
                    SeatChip("PRIYA", Amber.BoneDeep)
                    SeatChip("MARCUS", Amber.BoneDeep)
                    SeatChip("DANI", Amber.BoneDeep)
                }
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.u),
        ) {
            PreRow("INSIDERS", "2", verticalPadding = 5.u)
            PreRow(
                "INSIDERS KNOW EACH OTHER", "ON",
                border = Amber.BoneDim, labelInk = Amber.BoneInk, verticalPadding = 5.u,
            )
            PreRow("SUBROUTINES EACH", "7", verticalPadding = 5.u)
            PreRow("DISCUSSION", "90S", verticalPadding = 5.u)
            PreRow("VOTING", "60S", verticalPadding = 5.u)
            PreRow("EGRESS TIMER", "120S", verticalPadding = 5.u)
            PreRow("REVOKE COOLDOWN", "60S", verticalPadding = 5.u)
        }
        RowButton(border = Amber.BoneInk, onClick = { go(ScreenId.Secret) }) {
            Label("YOUR ONE LINE", size = 7.5, color = Amber.BoneInk)
            Label("REQUIRED", size = 7.5, color = Amber.BoneDim)
        }
        Label(
            "4 OF 6 HANDED THEIRS OVER",
            modifier = Modifier.fillMaxWidth(),
            size = 5.5, color = Amber.BoneDim, tracking = 0.1, align = TextAlign.Center,
        )
        SlateButton("LIGHTS OUT", { go(ScreenId.Armed) }, tracking = 0.2, size = 9.0, verticalPadding = 11.u)
    }
}

@Composable
private fun SeatChip(name: String, ink: Color) {
    Box(Modifier.border(1.u, Amber.BonePale).padding(horizontal = 4.u, vertical = 2.u)) {
        Label(name, size = 6.5, color = ink)
    }
}

/**
 * The one line the house can hold over you, handed over **before the lights go out**.
 *
 * Seen by the house only, and deleted when the round ends — both stated on screen, because the
 * player is being asked for something real and the promise has to be legible before they type it.
 * The Insider's blackmail text later quotes this line back; that is the entire mechanism.
 */
@Composable
fun SecretScreen() {
    val go = navigator()
    PrePage(gap = 7) {
        Label("BEFORE THE LIGHTS GO OUT", size = 7.0, color = Amber.BoneDim, tracking = 0.16)
        InfoBox(border = Amber.BoneInk, gap = 4.u) {
            Label(
                "SOMETHING YOU WOULD RATHER NOT EXPLAIN",
                size = 9.0, color = Amber.BoneInk, tracking = 0.04, lineHeight = 1.5,
            )
            Label(
                "One line, and make it real. A thing you did, owe, broke, or never told them. " +
                    "If the house picks you tonight, this is what it will hold over you.",
                size = 7.5, color = Amber.BoneDeep, lineHeight = 1.8,
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).border(1.u, Amber.BoneEdge).padding(7.u),
        ) {
            Label(
                "YOUR LINE",
                modifier = Modifier.padding(bottom = 5.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.14,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Readout("i still have priya's spare key", size = 17.0, color = Amber.BoneInk, lineHeight = 1.35)
                Caret(Amber.BoneInk)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            PreRow("SEEN BY", "THE HOUSE ONLY", border = Amber.BoneSoft, size = 6.5, verticalPadding = 5.u)
            PreRow("DELETED", "WHEN THE ROUND ENDS", border = Amber.BoneSoft, size = 6.5, verticalPadding = 5.u)
        }
        SlateButton("HAND IT OVER", { go(ScreenId.Lobby) }, tracking = 0.18, verticalPadding = 11.u)
    }
}
