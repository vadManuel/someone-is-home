package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

/**
 * Host setup: mapping a house, registering its markers, and locking the round's dials.
 *
 * Once per house, in the light. Roughly fifteen minutes of walking, which is why story 0.7 makes
 * the result survive a reinstall — losing it costs an evening.
 */

// ---------------------------------------------------------------------------------------------
// Saved homes and the plan
// ---------------------------------------------------------------------------------------------

/** Saved homes. Fifteen minutes of walking, kept forever. */
@Composable
fun MapsScreen() {
    val go = navigator()
    PrePage {
        PreHeading("SAVED HOMES", tracking = 0.18)
        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            SavedHome("THE BUNGALOW", "2FL . 11RM", Amber.BoneFaint, Amber.BoneInk, Amber.BoneDim) {
                go(ScreenId.HomeDetail)
            }
            SavedHome("MUM & DAD'S", "2FL . 9RM", Amber.BonePale, Amber.BoneDeep, Amber.BoneFaint) {
                go(ScreenId.HomeDetail)
            }
            SavedHome("THE LAKE PLACE", "1FL . 6RM", Amber.BonePale, Amber.BoneDeep, Amber.BoneFaint) {
                go(ScreenId.HomeDetail)
            }
        }
        PushDown()
        PanelButton(
            "MAP A NEW HOME",
            border = Amber.BoneDim, ink = Amber.BoneInk,
            onClick = { go(ScreenId.Editor) },
        )
    }
}

@Composable
private fun SavedHome(
    name: String, meta: String, border: Color, ink: Color, metaInk: Color, onClick: () -> Unit,
) {
    RowButton(border = border, onClick = onClick) {
        Label(name, size = 8.0, color = ink)
        Label(meta, size = 6.0, color = metaInk)
    }
}

/**
 * The plan editor. **Drag two corners; the shape becomes a room.**
 *
 * A grid rather than free rectangles, and the reasons are all about what the grid *removes*:
 * adjacency falls out of cell neighbours so nothing downstream needs geometry, L-shaped rooms
 * just work because real houses have them, and there are no resize handles, overlap rules or
 * snapping to build. Simpler to author and simpler to implement at once.
 */
@Composable
fun EditorScreen(vals: PanelVals) {
    val go = navigator()
    PrePage {
        PreHeading(
            "THE BUNGALOW", trailing = "6 ROOMS . 5 MARKERS",
            back = ScreenId.Maps, tracking = 0.14, trailingSize = 6.0,
        )

        Row(Modifier.fillMaxWidth().height(19.u), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            FloorTab("GROUND", held = true, modifier = Modifier.weight(1f))
            FloorTab("UPPER", held = false, modifier = Modifier.weight(1f)) { go(ScreenId.Floors) }
            FloorTab("+", held = false, modifier = Modifier.width(26.u)) { go(ScreenId.Floors) }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).border(1.u, Amber.BonePale).goes(ScreenId.RoomEdit)
        ) {
            EditorPlan(Plan.editorCells(), Modifier.fillMaxSize())
            EditorLabels(Modifier.fillMaxSize())
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

@Composable
private fun FloorTab(
    text: String,
    held: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxHeight()
            .border(1.u, if (held) Amber.BoneInk else Amber.BonePale)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Label(
            text,
            size = if (text == "+") 8.0 else 6.5,
            color = if (held) Amber.BoneInk else Amber.BoneDim,
            tracking = 0.1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The room panel and its markers
// ---------------------------------------------------------------------------------------------

/**
 * The plan above, a panel below. The panel is where a room gets its name, its type and its
 * markers.
 *
 * The plan keeps the held room lit and drops nothing else, so the room under discussion is the
 * only thing that has changed about a picture the host already knows.
 */
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
                Modifier.weight(1f).aspectRatio(Plan.COLS.toFloat() / Plan.ROWS)
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
            Modifier.fillMaxWidth().background(Amber.Bone)
                .edgeLine(PanelSide.Top, Amber.BoneInk, 2.u)
                .padding(8.u),
            verticalArrangement = Arrangement.spacedBy(5.u),
            content = panel,
        )
    }
}

/** Name chips, type, markers, and a delete that names what it takes with it. */
@Composable
fun RoomEditScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    HeldPlanPage("GARAGE", "ROOM PANEL") {
        Row(horizontalArrangement = Arrangement.spacedBy(3.u)) {
            NameChip("KITCHEN", false)
            NameChip("LIVING", false)
            NameChip("GARAGE", true)
            NameChip("BATH 1", false)
        }

        Column(Modifier.fillMaxWidth().border(1.u, Amber.BoneInk).padding(horizontal = 7.u, vertical = 5.u)) {
            Label(
                "ROOM NAME",
                modifier = Modifier.padding(bottom = 3.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.12,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Readout("GARAGE", size = 17.0, color = Amber.BoneInk, tracking = 0.06)
                Caret(Amber.BoneInk)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            TypeChip("ROOM", RoomType.Room, vals, Modifier.weight(1f)) {
                actions.pickRoomType(RoomType.Room)
            }
            TypeChip("STAIRS", RoomType.Stairs, vals, Modifier.weight(1f)) {
                actions.pickRoomType(RoomType.Stairs)
            }
        }

        if (vals.state.roomType == RoomType.Room) {
            RowButton(
                border = Amber.BoneInk, verticalPadding = 6.u,
                onClick = { go(ScreenId.MarkerSheet) },
            ) {
                Label("MARKERS", size = 7.0, color = Amber.BoneInk, tracking = 0.02)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    vals.roomMarkers.forEach { MarkerGlyph(it, 11.u, Amber.BoneInk) }
                    Label("›", size = 7.0, color = Amber.BoneInk)
                }
            }
        } else {
            // Stairs are somewhere people walk through, so nothing can live in them. The row
            // stays in place and goes quiet rather than vanishing: a control that disappears
            // teaches nothing about why.
            Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
                Row(
                    Modifier.fillMaxWidth().border(1.u, Color(0xFFB8B0A0))
                        .padding(horizontal = 7.u, vertical = 6.u),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Label("MARKERS", size = 7.0, color = Color(0xFF8A8271), tracking = 0.02)
                    Label("NONE HERE", size = 7.0, color = Color(0xFF8A8271))
                }
                Label(
                    "STAIRS ARE SOMEWHERE PEOPLE WALK THROUGH.\nNOTHING CAN BE REGISTERED IN THEM.",
                    size = 6.0, color = Amber.BoneFaint, tracking = 0.06, lineHeight = 1.8,
                )
            }
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
private fun NameChip(name: String, held: Boolean) {
    Box(
        Modifier.border(1.u, if (held) Amber.BoneInk else Amber.BonePale)
            .background(if (held) Amber.BoneInk else Color.Transparent)
            .padding(horizontal = 5.u, vertical = 3.u)
    ) {
        Label(name, size = 6.5, tracking = 0.06, color = if (held) Amber.Bone else Amber.BoneDim)
    }
}

@Composable
private fun TypeChip(
    text: String,
    type: RoomType,
    vals: PanelVals,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.border(1.u, vals.typeEdge(type)).background(vals.typeFill(type))
            .tap(onClick).padding(vertical = 6.u),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, size = 6.5, tracking = 0.08, color = vals.typeInk(type))
    }
}

/**
 * Turning an occupied room into stairs is destructive, and the host is told what it costs
 * **before** it happens.
 *
 * The cards are shown, not counted — the host is about to go and find these specific pieces of
 * paper, so a number would be the wrong thing to give them.
 */
@Composable
fun PassageWarnScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    PrePage(gap = 7) {
        PreHeading("GARAGE BECOMES STAIRS", back = ScreenId.RoomEdit, tracking = 0.14)

        InfoBox(border = Amber.BoneInk, padding = 8.u, gap = 6.u) {
            Label(
                "THESE WOULD BE UNREGISTERED",
                size = 8.5, color = Amber.BoneInk, tracking = 0.04, lineHeight = 1.5,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                vals.roomMarkers.forEach {
                    Box(Modifier.border(1.u, Amber.BoneInk).padding(4.u)) {
                        MarkerGlyph(it, 13.u, Amber.BoneInk)
                    }
                }
                TerminalToken(21.u, Amber.BoneInk)
            }
        }

        InfoBox(border = Amber.BonePale) {
            Label(
                "Stairs hold nothing, so these cards will belong to no room at all. Carry them " +
                    "into a real room and scan them again there, or the subroutines that used " +
                    "them are gone from this home.",
                size = 7.0, color = Amber.BoneDim, lineHeight = 1.85,
            )
        }

        PushDown()
        Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
            SlateButton("MOVE THEM FIRST", { go(ScreenId.MarkerSheet) }, verticalPadding = 11.u)
            PanelButton(
                "UNREGISTER AND CONTINUE",
                border = Amber.BonePale, ink = Amber.BoneDim, tracking = 0.14,
                onClick = { actions.confirmPassage() },
            )
        }
    }
}

/**
 * The markers registered in one room — **shapes, not names**.
 *
 * A marker's shape is its whole identity to everyone who is not the app. The id is never shown;
 * the host tells two cards apart by looking at them, which is also how a player finds one in the
 * dark.
 */
@Composable
fun MarkerSheetScreen(vals: PanelVals) {
    val go = navigator()
    HeldPlanPage("MARKERS", "GARAGE . ${vals.roomMarkers.size}") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.u),
            horizontalArrangement = Arrangement.spacedBy(4.u),
        ) {
            vals.roomMarkers.forEach { shape ->
                Row(
                    Modifier.border(1.u, Amber.BoneInk)
                        .padding(start = 5.u, end = 4.u, top = 3.u, bottom = 3.u),
                    horizontalArrangement = Arrangement.spacedBy(4.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarkerGlyph(shape, 11.u, Amber.BoneInk)
                    Label("×", size = 8.0, color = Amber.BoneInk, tracking = 0.06)
                }
            }
        }

        PreNote(
            "TAP A MARKER TO REMOVE IT.",
            lineHeight = 1.0, align = TextAlign.Center,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            Row(
                Modifier.weight(1f).dashedBorder(Amber.BonePale)
                    .padding(horizontal = 7.u, vertical = 5.u),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("TERMINAL", size = 6.5, color = Amber.BoneDim)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TerminalToken(9.u, Amber.BoneInk, textSize = 5.0)
                    Label("IN HALL", size = 6.5, color = Amber.BoneInk)
                }
            }
            Box(
                Modifier.border(1.u, Amber.BonePale).goes(ScreenId.TermRemove)
                    .padding(horizontal = 8.u, vertical = 5.u),
                contentAlignment = Alignment.Center,
            ) {
                Label("×", size = 8.0, color = Amber.BoneDim)
            }
        }

        SlateButton("REGISTER MARKER", { go(ScreenId.ScanMarker) })
        PanelButton(
            "DONE",
            border = Amber.BonePale, ink = Amber.BoneDim, verticalPadding = 9.u,
            onClick = { go(ScreenId.RoomEdit) },
        )
    }
}

/**
 * Registration: select the room, then scan. **Back camera, in the light, torch available.**
 *
 * This is the one scan in the game that is allowed to be easy. The in-round scan happens in a
 * dark house on the front camera by lamplight; this one happens with the lights on and the torch
 * lit, which is why the two screens look nothing alike.
 */
@Composable
fun ScanMarkerScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    PrePage {
        PreHeading("REGISTER MARKER", trailing = "GARAGE", back = ScreenId.MarkerSheet, tracking = 0.14)

        Box(Modifier.fillMaxWidth().weight(1f).background(Amber.SlateDead)) {
            ViewfinderCorners()

            Label(
                "BACK CAMERA . POINT AT THE CODE",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.u).fillMaxWidth(),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.14, align = TextAlign.Center,
            )

            Box(Modifier.fillMaxSize().background(vals.torchWash))

            // What was just registered, shown as the shape rather than as a confirmation line:
            // the host has to match it against the card in their hand.
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.u),
            ) {
                vals.lastRegistered?.let { MarkerGlyph(it, 34.u, Amber.SlateFill) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.u)) {
                        Block(2.u, 8.u, Amber.SlateFill)
                        Block(2.u, 8.u, Amber.SlateFill)
                    }
                    Label(
                        "${vals.lastRegistered?.id?.uppercase() ?: ""} . ADDED",
                        size = 6.5, color = Amber.SlateFill, tracking = 0.14,
                    )
                }
            }

            Row(
                Modifier.align(Alignment.BottomEnd).padding(6.u)
                    .border(1.u, Amber.SlateFill).background(vals.torchFill)
                    .tap { actions.toggleTorch() }
                    .padding(horizontal = 7.u, vertical = 6.u),
                horizontalArrangement = Arrangement.spacedBy(4.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.u).background(vals.torchInk, CircleShape))
                Label(vals.torchLabel, size = 6.0, color = vals.torchInk, tracking = 0.1)
            }
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 19.u),
            horizontalArrangement = Arrangement.spacedBy(4.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("THIS ROOM", size = 6.0, color = Amber.BoneFaint, tracking = 0.12)
            vals.roomMarkers.forEach { MarkerGlyph(it, 12.u, Amber.BoneInk) }
        }

        PreNote("KEEP SCANNING TO ADD MORE. THE CODE CARRIES\nTHE SHAPE PRINTED ON IT.")
        SlateButton("DONE SCANNING", { go(ScreenId.MarkerSheet) })
    }
}

/**
 * The four viewfinder brackets, at the design's own 14% / 16% insets.
 *
 * Corners only. A full frame would read as a photograph of something rather than as an
 * instrument pointed at it.
 */
@Composable
private fun ViewfinderCorners() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val insetX = maxWidth * 0.14f
        val insetY = maxHeight * 0.16f
        val len = 12.u
        val t = 2.u
        val c = Amber.SlateFill

        @Composable
        fun bracket(x: Dp, y: Dp, vertical: PanelSide, horizontal: PanelSide) {
            Box(
                Modifier.offset(x = x, y = y).size(len)
                    .edgeLine(vertical, c, t)
                    .edgeLine(horizontal, c, t)
            )
        }

        bracket(insetX, insetY, PanelSide.Top, PanelSide.Start)
        bracket(maxWidth - insetX - len, insetY, PanelSide.Top, PanelSide.End)
        bracket(insetX, maxHeight - insetY - len, PanelSide.Bottom, PanelSide.Start)
        bracket(maxWidth - insetX - len, maxHeight - insetY - len, PanelSide.Bottom, PanelSide.End)
    }
}

/**
 * A second terminal card, refused.
 *
 * **One terminal per home**, and the screen says where the existing one is rather than only that
 * there is one — the host has to go and find it, or decide to move it. A second would give the
 * house two places to be found, which is the whole reason the map costs a Resident time.
 */
@Composable
fun TermTakenScreen() {
    val go = navigator()
    PrePage {
        PreHeading("REGISTER MARKER", trailing = "GARAGE", back = ScreenId.ScanMarker, tracking = 0.14)

        Box(Modifier.fillMaxWidth().weight(1f).background(Amber.SlateDead)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 14.u),
                verticalArrangement = Arrangement.spacedBy(6.u, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TerminalToken(26.u, Amber.Caution, stroke = 2.u, textSize = 13.0)
                Label(
                    "ALREADY REGISTERED",
                    size = 8.0, color = Amber.Caution, tracking = 0.1, lineHeight = 1.7,
                    align = TextAlign.Center,
                )
                Label(
                    "This home has one terminal and it is in HALL. A second one would give the " +
                        "house two places to be found.",
                    size = 7.0, color = Amber.SlateFill, lineHeight = 1.8, align = TextAlign.Center,
                )
            }
        }

        SlateButton("KEEP IT IN HALL", { go(ScreenId.ScanMarker) })
        PanelButton(
            "MOVE THE TERMINAL TO GARAGE",
            border = Amber.BonePale, ink = Amber.BoneDim, verticalPadding = 9.u,
            onClick = { go(ScreenId.ScanMarker) },
        )
        PreNote(
            "MOVING IT LEAVES HALL WITH NO TERMINAL.\nTHE T CARD IS NEVER AN ORDINARY MARKER.",
            align = TextAlign.Center,
        )
    }
}

/** Demoting the terminal, and saying what it costs. */
@Composable
fun TermRemoveScreen() {
    val go = navigator()
    PrePage(gap = 7) {
        PreHeading("REMOVE THE TERMINAL", back = ScreenId.MarkerSheet, tracking = 0.14)

        InfoBox(border = Amber.BoneInk, padding = 9.u, gap = 6.u) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.u),
            ) {
                TerminalToken(24.u, Amber.BoneInk, stroke = 2.u, textSize = 12.0)
                Label("IN HALL", size = 8.0, color = Amber.BoneInk, tracking = 0.06)
            }
        }

        InfoBox(border = Amber.BonePale) {
            Label(
                "The T card belongs to no room after this, and it can only ever be a terminal. " +
                    "It is not a marker. This home cannot be saved again until some room has one.",
                size = 7.0, color = Amber.BoneDim, lineHeight = 1.85,
            )
        }

        PushDown()
        Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
            PanelButton(
                "REMOVE IT",
                border = Amber.BonePale, ink = Amber.BoneDim,
                onClick = { go(ScreenId.MarkerSheet) },
            )
            SlateButton("KEEP IT", { go(ScreenId.MarkerSheet) })
        }
    }
}

/**
 * Save is blocked, and the screen says where a terminal belongs rather than only that one is
 * missing.
 *
 * *"Put it somewhere awkward"* is the load-bearing line. Standing at the terminal costs a
 * Resident time and puts them alone in the dark; that trade is what the whole map is built on,
 * and a terminal by the front door quietly deletes it.
 */
@Composable
fun NoTerminalScreen() {
    val go = navigator()
    PrePage(gap = 7) {
        PreHeading("THIS HOME NEEDS A TERMINAL", tracking = 0.14)

        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.BoneInk).padding(8.u),
            horizontalArrangement = Arrangement.spacedBy(9.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalToken(30.u, Amber.BoneInk, stroke = 2.u, textSize = 15.0)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.u),
            ) {
                Label(
                    "SCAN THE CARD MARKED T",
                    size = 8.5, color = Amber.BoneInk, tracking = 0.04, lineHeight = 1.5,
                )
                Label(
                    "Open a room, register a marker, and scan the T card there. That room " +
                        "becomes the terminal.",
                    size = 7.0, color = Amber.BoneDeep, lineHeight = 1.75,
                )
            }
        }

        InfoBox(border = Amber.BonePale, gap = 5.u) {
            Label("PUT IT SOMEWHERE AWKWARD", size = 6.5, color = Amber.BoneDim, tracking = 0.12)
            Label(
                "A back room, a basement, the far end of the house. Somewhere nobody passes by " +
                    "accident. It is the only place residents can see who is where, so standing " +
                    "at it costs them time and puts them alone in the dark.",
                size = 7.0, color = Amber.BoneDim, lineHeight = 1.8,
            )
        }

        PushDown()
        SlateButton("OPEN A ROOM", { go(ScreenId.RoomEdit) }, verticalPadding = 11.u)
    }
}

// ---------------------------------------------------------------------------------------------
// Floors, saving, and the lobby
// ---------------------------------------------------------------------------------------------

/**
 * Floors are **additive and unordered, and nothing connects them**.
 *
 * No vertical-connection logic exists, by decision: the app renders what was drawn. A stairwell
 * that "led" somewhere would be geometry the game then has to reason about, for a benefit the
 * counts-not-dots map never cashes in.
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
        PushDown()
        Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
            PreRow("RENAME UPPER", ">", verticalPadding = 7.u)
            PreRow(
                "DELETE UPPER", ">",
                border = Amber.BoneInk, labelInk = Amber.BoneInk, verticalPadding = 7.u,
            )
            PreNote(
                "DELETING UPPER REMOVES 5 ROOMS AND\n4 MARKERS. HOLD TWO SECONDS.",
                lineHeight = 1.8, align = TextAlign.Center,
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
            Row(verticalAlignment = Alignment.Bottom) {
                Readout("THE BUNGALOW", size = 21.0, color = Amber.BoneInk, tracking = 0.06)
                Caret(Amber.BoneInk, size = 21.0)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            PreRow("FLOORS", "2")
            PreRow("ROOMS", "11")
            PreRow("MARKERS", "9")
        }
        InfoBox(border = Amber.BonePale, padding = 0.u) {
            Label(
                "A NICKNAME, NOT AN ADDRESS.\nNOTHING LEAVES THIS DEVICE.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.1, lineHeight = 1.9,
            )
        }
        PushDown()
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
            RowButton(border = Amber.Slate, fill = Amber.SlateFill, onClick = { go(ScreenId.Lobby) }) {
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
        PushDown()
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
 * The destructive control is outlined and the safe one is filled — inverted from habit, on
 * purpose. Fifteen minutes of walking a house is the thing being protected, so the brighter
 * button should be the one that protects it.
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
        PushDown()
        Column(verticalArrangement = Arrangement.spacedBy(7.u)) {
            Row(Modifier.fillMaxWidth().height(6.u), horizontalArrangement = Arrangement.spacedBy(1.u)) {
                Box(Modifier.weight(3f).fillMaxHeight().background(Amber.BoneDim))
                Box(Modifier.weight(7f).fillMaxHeight().background(Amber.BonePale))
            }
            PreNote(
                "KEEP HOLDING . 0.6S OF 2.0S",
                tracking = 0.12, lineHeight = 1.0, align = TextAlign.Center,
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
 * Balance values cannot be edited mid-round: a round whose numbers changed under it is a round
 * that cannot be replayed, and replay is the only debugging this game has.
 */
@Composable
fun LobbyScreen() {
    val go = navigator()
    PrePage {
        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.BoneFaint)
                .padding(horizontal = 7.u, vertical = 6.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("THE BUNGALOW", size = 7.0, color = Amber.BoneDim, tracking = 0.12)
                Label("6 JOINED", size = 7.0, color = Amber.BoneInk, tracking = 0.12)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.u)) {
                SeatChip("ELLIOT", Amber.BoneInk)
                SeatChip("PRIYA", Amber.BoneDeep)
                SeatChip("MARCUS", Amber.BoneDeep)
                SeatChip("DANI", Amber.BoneDeep)
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
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
        PreNote(
            "4 OF 6 HANDED THEIRS OVER",
            color = Amber.BoneDim, size = 5.5, lineHeight = 1.0, align = TextAlign.Center,
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
