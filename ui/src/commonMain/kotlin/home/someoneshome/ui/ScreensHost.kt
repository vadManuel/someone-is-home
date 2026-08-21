package home.someoneshome.ui

import home.someoneshome.model.CardRejection
import home.someoneshome.model.RoomKind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Saved homes. Fifteen minutes of walking, kept forever — **and read off the phone, not written
 * down here.**
 *
 * The counts beside each name are counted off the plan that was stored, so a home cannot say two
 * floors in this list and draw three in the editor. The topmost row is the one saved most
 * recently and is the brighter of the two intensities the design uses, which is the only ordering
 * claim the list makes.
 */
@Composable
fun MapsScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val homes = LocalHomes.current
    PrePage {
        PreHeading("SAVED HOMES", tracking = 0.18)
        when {
            // The file is there and this build cannot read it. Said here, where the host is
            // looking for their houses, and nothing is written over them until somebody decides
            // what to do about it.
            homes.unreadable -> InfoBox(border = Amber.Caution, gap = 5.u) {
                Label("THESE COULD NOT BE READ", size = 8.0, color = Amber.BoneInk, tracking = 0.04)
                Label(
                    "Something on this phone is holding homes in a form this build does not " +
                        "know. Nothing has been changed and nothing will be written over them.",
                    size = 7.0, color = Amber.BoneDim, lineHeight = 1.8,
                )
            }

            homes.isEmpty -> InfoBox(border = Amber.BonePale, padding = 0.u) {
                Label(
                    "NO HOMES YET.\nMAP ONE AND IT STAYS ON THIS PHONE.",
                    modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                    size = 6.0, color = Amber.BoneDim, tracking = 0.1, lineHeight = 1.9,
                )
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
                homes.homes.forEachIndexed { index, home ->
                    val newest = index == 0
                    SavedHomeRow(
                        home.name,
                        "${home.floorCount}FL . ${home.roomCount}RM",
                        border = if (newest) Amber.BoneFaint else Amber.BonePale,
                        ink = if (newest) Amber.BoneInk else Amber.BoneDeep,
                        metaInk = if (newest) Amber.BoneDim else Amber.BoneFaint,
                    ) {
                        actions.openSavedHome(home.name)
                        go(ScreenId.HomeDetail)
                    }
                }
            }
        }
        PushDown()
        PanelButton(
            "MAP A NEW HOME",
            border = Amber.BoneDim, ink = Amber.BoneInk,
            // Nothing is open after this, so the save at the end of the walk adds a home rather
            // than replacing whichever one the host last looked at.
            onClick = {
                actions.mapNewHome()
                go(ScreenId.Editor)
            },
        )
    }
}

@Composable
private fun SavedHomeRow(
    name: String, meta: String, border: Color, ink: Color, metaInk: Color, onClick: () -> Unit,
) {
    RowButton(border = border, onClick = onClick) {
        Label(name, size = 8.0, color = ink)
        Label(meta, size = 6.0, color = metaInk)
    }
}

/** `2 FLOORS`, `1 FLOOR` — a count the host reads as a sentence rather than as a readout. */
private fun counted(n: Int, one: String, many: String = one + "S"): String =
    "$n " + if (n == 1) one else many

/**
 * The plan editor. **Drag two corners; the shape becomes a room.**
 *
 * A grid rather than free rectangles, and the reasons are all about what the grid *removes*:
 * adjacency falls out of cell neighbours so nothing downstream needs geometry, L-shaped rooms
 * just work because real houses have them, and there are no resize handles, overlap rules or
 * snapping to build. Simpler to author and simpler to implement at once.
 *
 * The grid draws whatever the host has painted, and the counts in the heading are counted rather
 * than written down. What was a picture of one bungalow is now a picture of any house.
 */
@Composable
fun EditorScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    val editor = LocalEditor.current
    PrePage {
        PreHeading(
            // The home under edit, not a fixture's name. A host who mapped their own house was
            // told they were editing THE BUNGALOW for as long as this screen existed.
            editor.name,
            trailing = "${editor.roomsOn(editor.floorName)} ROOMS . " +
                "${editor.markersOn(editor.floorName)} MARKERS",
            back = ScreenId.Maps, tracking = 0.14, trailingSize = 6.0,
        )

        Row(Modifier.fillMaxWidth().height(19.u), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            editor.plan.floors.forEach { storey ->
                FloorTab(
                    storey.name,
                    held = storey.name == editor.floorName,
                    modifier = Modifier.weight(1f),
                ) { actions.openFloor(storey.name) }
            }
            FloorTab("+", held = false, modifier = Modifier.width(26.u)) { go(ScreenId.Floors) }
        }

        Box(Modifier.fillMaxWidth().weight(1f).border(1.u, Amber.BonePale)) {
            EditorSurface(editor, actions.openRoomAt, Modifier.fillMaxSize())
        }

        // The hint, or the refusal that replaces it. The model refuses politely so this can be
        // said while the finger is still on the screen, and the one warning colour says which of
        // the two the host is reading without them having to read it to find out.
        InfoBox(border = if (editor.refusal != null) Amber.Caution else Amber.BonePale, padding = 0.u) {
            Label(
                editor.refusal ?: "DRAG TWO CORNERS TO ADD A ROOM.\n" +
                    "DRAG FROM INSIDE ONE TO GROW IT.\n" +
                    "TAP A ROOM TO NAME IT OR ADD MARKERS.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0,
                color = if (editor.refusal != null) Amber.BoneInk else Amber.BoneDim,
                tracking = 0.1, lineHeight = 1.8,
            )
        }

        PanelButton(
            vals.saveLabel(editor.hasTerminal),
            border = vals.saveEdge(editor.hasTerminal),
            fill = vals.saveFill(editor.hasTerminal),
            ink = vals.saveInk(editor.hasTerminal),
            // The gate. It still goes somewhere when the home has no terminal — to the screen
            // that says where a terminal belongs, because a button that goes quiet teaches
            // nothing about why, and this one is the last thing between a host and an evening
            // spent finding out the hard way.
            onClick = {
                go(if (editor.hasTerminal) ScreenId.SaveName else ScreenId.NoTerminal)
            },
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
    val editor = LocalEditor.current
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
                Modifier.weight(1f)
                    .aspectRatio(HomeEditorModel.COLS.toFloat() / HomeEditorModel.ROWS)
                    .align(Alignment.CenterHorizontally)
                    .border(1.u, Amber.BonePale)
            ) {
                // Read-only here on purpose: this is the plan the host already knows, with the
                // room under discussion lit. Painting happens on the editor, where the whole
                // grid is under the finger and nothing is being decided about one room.
                EditorPlan(editor.editorCells(), Modifier.fillMaxSize())
                EditorLabels(
                    editor.rooms,
                    Modifier.fillMaxSize(),
                    held = editor.held,
                    markers = { editor.markersIn(it).size },
                    terminal = editor.terminal,
                )
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

/**
 * Name chips, type, markers, and a delete that names what it takes with it.
 *
 * The presets are the five rooms every house has, and tapping one **is** naming the room — the
 * same act as typing it, so it goes through the same door and is refused the same way if some
 * other room already has that name.
 */
@Composable
fun RoomEditScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    val editor = LocalEditor.current
    HeldPlanPage(editor.heldName, "ROOM PANEL") {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.u),
            verticalArrangement = Arrangement.spacedBy(3.u),
        ) {
            HomeEditorModel.PRESETS.forEach { preset ->
                NameChip(preset, held = preset == editor.heldName) { actions.nameRoom(preset) }
            }
        }

        Column(Modifier.fillMaxWidth().border(1.u, Amber.BoneInk).padding(horizontal = 7.u, vertical = 5.u)) {
            Label(
                "ROOM NAME",
                modifier = Modifier.padding(bottom = 3.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.12,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Readout(editor.heldName, size = 17.0, color = Amber.BoneInk, tracking = 0.06)
                Caret(Amber.BoneInk)
            }
        }

        // The refusal a rename can produce, said here rather than on the plan the host is not
        // looking at. A preset chip for a name another room already holds is the likely one.
        editor.refusal?.let {
            Label(it, size = 6.0, color = Amber.BoneInk, tracking = 0.1, lineHeight = 1.6)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            TypeChip("ROOM", RoomKind.Room, editor.heldKind, vals, Modifier.weight(1f)) {
                actions.pickRoomType(RoomKind.Room)
            }
            TypeChip("STAIRS", RoomKind.Stairs, editor.heldKind, vals, Modifier.weight(1f)) {
                actions.pickRoomType(RoomKind.Stairs)
            }
        }

        if (editor.heldKind == RoomKind.Room) {
            RowButton(
                border = Amber.BoneInk, verticalPadding = 6.u,
                onClick = { go(ScreenId.MarkerSheet) },
            ) {
                Label("MARKERS", size = 7.0, color = Amber.BoneInk, tracking = 0.02)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    editor.heldMarkers.forEach { MarkerGlyph(it, 11.u, Amber.BoneInk) }
                    Label("›", size = 7.0, color = Amber.BoneInk)
                }
            }
        } else {
            // Stairs are somewhere people walk through, so nothing can live in them. The row
            // stays in place and goes quiet rather than vanishing: a control that disappears
            // teaches nothing about why. It is structural rather than drawn — a registration
            // into stairs cannot be constructed (D-099), and the editor gave the cards up as
            // part of the type change rather than afterwards.
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
            deleteRoomLabel(editor.heldMarkers.size, editor.terminal == editor.heldName),
            border = Amber.BonePale, ink = Amber.BoneDim,
            onClick = {
                actions.deleteRoom()
                go(ScreenId.Editor)
            },
        )
    }
}

/**
 * What deleting this room takes with it, named rather than counted at the host afterwards.
 *
 * The design's line is DELETE ROOM AND ITS 2 MARKERS, written for a room that had two. A room
 * with none must not be told it is losing none, and a room holding the T card is losing the one
 * thing in the house that cannot simply be printed again — so it gets said out loud.
 */
private fun deleteRoomLabel(markers: Int, terminal: Boolean): String {
    val cards = when (markers) {
        0 -> null
        1 -> "ITS 1 MARKER"
        else -> "ITS $markers MARKERS"
    }
    val parts = listOfNotNull(cards, if (terminal) "ITS TERMINAL" else null)
    return if (parts.isEmpty()) "DELETE ROOM"
    else "DELETE ROOM AND " + parts.joinToString(" AND ")
}

@Composable
private fun NameChip(name: String, held: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.border(1.u, if (held) Amber.BoneInk else Amber.BonePale)
            .background(if (held) Amber.BoneInk else Color.Transparent)
            .tap(onClick)
            .padding(horizontal = 5.u, vertical = 3.u)
    ) {
        Label(name, size = 6.5, tracking = 0.06, color = if (held) Amber.Bone else Amber.BoneDim)
    }
}

@Composable
private fun TypeChip(
    text: String,
    type: RoomKind,
    held: RoomKind,
    vals: PanelVals,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.border(1.u, vals.typeEdge(held, type)).background(vals.typeFill(held, type))
            .tap(onClick).padding(vertical = 6.u),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, size = 6.5, tracking = 0.08, color = vals.typeInk(held, type))
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
fun StairsWarnScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    val editor = LocalEditor.current
    PrePage(gap = 7) {
        PreHeading("${editor.heldName} BECOMES STAIRS", back = ScreenId.RoomEdit, tracking = 0.14)

        InfoBox(border = Amber.BoneInk, padding = 8.u, gap = 6.u) {
            Label(
                "THESE WOULD BE UNREGISTERED",
                size = 8.5, color = Amber.BoneInk, tracking = 0.04, lineHeight = 1.5,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                editor.heldMarkers.forEach {
                    Box(Modifier.border(1.u, Amber.BoneInk).padding(4.u)) {
                        MarkerGlyph(it, 13.u, Amber.BoneInk)
                    }
                }
                // Only if this room is actually the one holding it. The port drew the T on every
                // room, which told a host they were about to lose a terminal that was two rooms
                // away and perfectly safe.
                if (editor.terminal == editor.heldName) TerminalToken(21.u, Amber.BoneInk)
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
                onClick = { actions.confirmStairs() },
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
    val actions = LocalActions.current
    val editor = LocalEditor.current
    val cards = editor.cardsIn(editor.heldName)
    HeldPlanPage("MARKERS", "${editor.heldName} . ${cards.size}") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.u),
            horizontalArrangement = Arrangement.spacedBy(4.u),
        ) {
            // Keyed on the card, not on the shape. Two rooms can never hold the same shape
            // (D-086) but the chip has to name a card to take one off, and the shape is what the
            // host reads — so the chip shows the shape and removes the id underneath it.
            cards.forEach { registration ->
                Row(
                    Modifier.border(1.u, Amber.BoneInk)
                        .tap { actions.forgetMarker(registration.card.id) }
                        .padding(start = 5.u, end = 4.u, top = 3.u, bottom = 3.u),
                    horizontalArrangement = Arrangement.spacedBy(4.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarkerGlyph(registration.card.shape, 11.u, Amber.BoneInk)
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
                    // Where the T card is, which is a fact about the whole home rather than
                    // about this room — the host is told it here because here is where they
                    // would otherwise scan a second one.
                    val at = editor.terminal
                    if (at != null) TerminalToken(9.u, Amber.BoneInk, textSize = 5.0)
                    Label(
                        if (at != null) "IN $at" else "NOT PLACED",
                        size = 6.5,
                        color = if (at != null) Amber.BoneInk else Amber.BoneDim,
                    )
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
    val editor = LocalEditor.current
    PrePage {
        PreHeading(
            "REGISTER MARKER", trailing = editor.heldName,
            back = ScreenId.MarkerSheet, tracking = 0.14,
        )

        Box(Modifier.fillMaxWidth().weight(1f).background(Amber.SlateDead)) {
            ViewfinderCorners()

            Label(
                "BACK CAMERA . POINT AT THE CODE",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.u).fillMaxWidth(),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.14, align = TextAlign.Center,
            )

            Box(Modifier.fillMaxSize().background(vals.torchWash))

            // What the last card did, shown as the shape rather than as a confirmation line: the
            // host has to match it against the piece of paper in their hand. Nothing is drawn
            // before the first card of this visit — an empty viewfinder is the honest state, and
            // the last session's card sitting there would read as one that had just been read.
            editor.lastScan?.let { ScanReadout(it) }

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
            editor.heldMarkers.forEach { MarkerGlyph(it, 12.u, Amber.BoneInk) }
        }

        PreNote("KEEP SCANNING TO ADD MORE. THE CODE CARRIES\nTHE SHAPE PRINTED ON IT.")
        SlateButton("DONE SCANNING", { go(ScreenId.MarkerSheet) })
    }
}

/**
 * **What the last card did, in the middle of the viewfinder.**
 *
 * One place for all six outcomes, because they are one thing to the host: they scanned a card and
 * the app has an answer. Splitting the refusals onto their own screens would take the viewfinder
 * away between cards, and the design's own instruction on this screen is KEEP SCANNING TO ADD
 * MORE — the host has a stack of paper in one hand and is not putting the phone down between them.
 *
 * The shape is drawn large whichever way it went, in caution ink when the card was turned away.
 * **A refusal that named a room and not a shape would leave a host holding three cards with no way
 * to tell which one the app meant.** The one outcome with no shape is a symbol that was not one of
 * our cards at all, and it says exactly that instead.
 */
@Composable
private fun BoxScope.ScanReadout(outcome: ScanOutcome) {
    val refused = outcome !is ScanOutcome.Landed
    val ink = if (refused) Amber.Caution else Amber.SlateFill
    Column(
        Modifier.align(Alignment.Center).padding(horizontal = 12.u),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        // The T card is never an ordinary marker and is never drawn as one: it is the token the
        // host-setup screens have always used for it, so the one card that is not a marker does
        // not arrive looking like the letter T happened to be printed on a marker.
        if (outcome.isTerminal) TerminalToken(30.u, ink, stroke = 2.u, textSize = 15.0)
        else outcome.shape?.let { MarkerGlyph(it, 34.u, ink) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.u)) {
                Block(2.u, 8.u, ink)
                Block(2.u, 8.u, ink)
            }
            Label(scanLine(outcome), size = 6.5, color = ink, tracking = 0.14)
        }

        // The card's printed id, quietly, and only when the card was read. It is the only place
        // in the app that ever shows one: a host looking for one specific card among nine
        // identical-looking ones has nothing else to go on, and D-069's whole argument is that the
        // id is what tells two cards carrying the same shape apart.
        outcome.card?.let {
            Label(it.id.value, size = 5.5, color = ink, tracking = 0.2)
        }
    }
}

/** The outcome in the host's words. Every branch, so a new one cannot arrive as an empty line. */
private fun scanLine(outcome: ScanOutcome): String = when (outcome) {
    is ScanOutcome.Landed -> when {
        outcome.from != null -> "MOVED FROM ${outcome.from}"
        outcome.isTerminal -> "TERMINAL . ${outcome.room}"
        else -> "${outcome.room} . ADDED"
    }

    is ScanOutcome.Refused -> outcome.why

    // D-071: an unreadable card is a fact about a piece of paper and may be said plainly.
    is ScanOutcome.Unreadable -> when (outcome.why) {
        CardRejection.WrongLength, CardRejection.NotInAlphabet -> "NOT ONE OF OUR CARDS"
        CardRejection.UnknownVersion -> "A NEWER CARD THAN THIS APP KNOWS"
        CardRejection.UnknownShape -> "A SHAPE THIS APP DOES NOT HAVE"
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
    val actions = LocalActions.current
    val editor = LocalEditor.current
    val at = editor.terminal.orEmpty()
    PrePage {
        PreHeading(
            "REGISTER MARKER", trailing = editor.heldName,
            back = ScreenId.ScanMarker, tracking = 0.14,
        )

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
                // The room name is lifted out of the sentence, because it is the one thing the
                // host has to act on -- they are about to go and find it.
                Label(
                    "This home has one terminal and it is in",
                    size = 7.0, color = Amber.SlateFill, lineHeight = 1.8, align = TextAlign.Center,
                )
                Label(at, size = 7.0, color = Amber.BoneChip, tracking = 0.1)
                Label(
                    "A second one would give the house two places to be found.",
                    size = 7.0, color = Amber.SlateFill, lineHeight = 1.8, align = TextAlign.Center,
                )
            }
        }

        // KEEP goes back to the viewfinder and changes nothing: the card the host is holding is
        // simply not the one that places the terminal, and there is nothing to undo because
        // nothing happened. MOVE is the one that acts, and it is the actions layer's — where it
        // lands is the same place either way, but what it does to the map is not.
        SlateButton("KEEP IT IN $at", { go(ScreenId.ScanMarker) })
        PanelButton(
            "MOVE THE TERMINAL TO ${editor.heldName}",
            border = Amber.BonePale, ink = Amber.BoneDim, verticalPadding = 9.u,
            onClick = actions.moveTerminal,
        )
        PreNote(
            "MOVING IT LEAVES $at WITH NO TERMINAL.\nTHE T CARD IS NEVER AN ORDINARY MARKER.",
            align = TextAlign.Center,
        )
    }
}

/** Demoting the terminal, and saying what it costs. */
@Composable
fun TermRemoveScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val editor = LocalEditor.current
    PrePage(gap = 7) {
        PreHeading("REMOVE THE TERMINAL", back = ScreenId.MarkerSheet, tracking = 0.14)

        InfoBox(border = Amber.BoneInk, padding = 9.u, gap = 6.u) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.u),
            ) {
                TerminalToken(24.u, Amber.BoneInk, stroke = 2.u, textSize = 12.0)
                Label(
                    editor.terminal?.let { "IN $it" } ?: "NOT PLACED",
                    size = 8.0, color = Amber.BoneInk, tracking = 0.06,
                )
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
                onClick = actions.removeTerminal,
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
        // Back to the plan, not into the room panel. The host has to choose WHICH room the
        // terminal goes in — "somewhere awkward" is the whole point of it — and the plan is the
        // only screen that can ask that question.
        SlateButton("OPEN A ROOM", { go(ScreenId.Editor) }, verticalPadding = 11.u)
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
    val actions = LocalActions.current
    val editor = LocalEditor.current
    val open = editor.floorName
    PrePage {
        PreHeading(
            "FLOORS", trailing = "${editor.floorCount} IN THIS HOME",
            back = ScreenId.Editor, tracking = 0.14,
        )
        editor.plan.floors.forEach { storey ->
            val held = storey.name == open
            PreRow(
                storey.name,
                "${editor.roomsOn(storey.name)} ROOMS . ${editor.markersOn(storey.name)} MARKERS",
                border = if (held) Amber.BoneInk else Amber.BonePale,
                labelInk = if (held) Amber.BoneInk else Amber.BoneDeep,
                size = 7.5, verticalPadding = 7.u,
                onClick = { actions.openFloor(storey.name) },
            )
        }
        PanelButton(
            "ADD A FLOOR",
            border = Amber.BonePale, ink = Amber.BoneDim,
            size = 7.5, tracking = 0.14, verticalPadding = 9.u,
            // The new storey is added AND opened, and the host lands on it with an empty grid.
            // Adding one and staying here would leave them looking at a list, wondering whether
            // it worked.
            onClick = {
                actions.addFloor()
                go(ScreenId.Editor)
            },
        )
        InfoBox(border = Amber.BonePale, padding = 0.u) {
            Label(
                "FLOORS ARE ADDITIVE AND UNORDERED.\nNOTHING CONNECTS THEM. THE APP\nRENDERS WHAT YOU DREW.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.1, lineHeight = 1.8,
            )
        }
        PushDown()
        // Renaming and deleting a storey are the two controls here that are still inert. Both
        // need something this unit does not build — a text field for one, a two-second hold for
        // the other — so they name the open storey truthfully and do nothing, rather than
        // naming a storey that is not open and doing nothing.
        Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
            PreRow("RENAME $open", ">", verticalPadding = 7.u)
            PreRow(
                "DELETE $open", ">",
                border = Amber.BoneInk, labelInk = Amber.BoneInk, verticalPadding = 7.u,
            )
            PreNote(
                "DELETING $open REMOVES ${editor.roomsOn(open)} ROOMS AND\n" +
                    "${editor.markersOn(open)} MARKERS. HOLD TWO SECONDS.",
                lineHeight = 1.8, align = TextAlign.Center,
            )
        }
    }
}

/**
 * The plan is drawn; now name it. A nickname, not an address — nothing leaves this device.
 *
 * **The field is real and the name it holds is the home's.** It is also the rename screen: with a
 * home open, saving under a different name moves that home, its plan and its cards across rather
 * than leaving a second copy behind under the old one.
 */
@Composable
fun SaveNameScreen() {
    val actions = LocalActions.current
    val editor = LocalEditor.current
    val homes = LocalHomes.current
    PrePage(gap = 7) {
        PreHeading("SAVE HOME", back = ScreenId.Editor)
        InfoBox(border = Amber.BoneDim) {
            Label(
                "NAME THIS HOME",
                modifier = Modifier.padding(bottom = 4.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.14,
            )
            ReadoutField(
                editor.name,
                actions.nameHome,
                modifier = Modifier.fillMaxWidth(),
                size = 21.0,
            )
        }
        // What a save can refuse: an empty name, a name another home holds, and a phone that did
        // not write the file. Said here, because here is where the host is standing when it
        // happens and the alternative is a button that appears not to work.
        homes.refusal?.let {
            Label(it, size = 6.5, color = Amber.BoneInk, tracking = 0.1, lineHeight = 1.6)
        }
        // Counted off the plan, because this is the screen where a host checks that what they
        // walked is what the app has. A number typed in here would agree with the house on the
        // day it was written and never again.
        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            PreRow("FLOORS", "${editor.floorCount}")
            PreRow("ROOMS", "${editor.roomCount}")
            PreRow("MARKERS", "${editor.markerCount}")
        }
        InfoBox(border = Amber.BonePale, padding = 0.u) {
            Label(
                "A NICKNAME, NOT AN ADDRESS.\nNOTHING LEAVES THIS DEVICE.",
                modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                size = 6.0, color = Amber.BoneFaint, tracking = 0.1, lineHeight = 1.9,
            )
        }
        PushDown()
        // The one control here that does not name where it goes: a refused save stays put, so the
        // target depends on an answer the screen does not have. Flow.viaActions carries it.
        SlateButton("SAVE HOME", actions.saveHome, tracking = 0.18, verticalPadding = 11.u)
    }
}

/**
 * One saved home: host with it, edit the plan, rename it — or throw it away.
 *
 * **Every number here is counted off the stored home**, including what it cost to walk. The port
 * also carried PLAYED 3 TIMES and LAST PLAYED 12 AUGUST, and both are gone rather than
 * approximated: nothing in this app has ever hosted a round, so a play count would be a number
 * with no source that a host would nonetheless believe. The line that replaced them is the one
 * fact the plan really does know.
 *
 * **DUPLICATE is gone too**, and the whole of it went with it — the row, the action, the model's
 * `duplicateOpen` and the free-name arithmetic that produced `THE BUNGALOW 2`. It worked; nobody
 * needs it yet. Left in place it would be a fourth row a host has to read past on the way to the
 * three that do something, on a screen that is already mostly air.
 */
@Composable
fun HomeDetailScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val homes = LocalHomes.current
    val home = homes.open
    PrePage {
        PreHeading("SAVED HOME", back = ScreenId.Maps)
        if (home == null) {
            // No home is open. Unreachable by walking — every route here opens one first — and
            // drawn rather than left blank because the picker can land on any screen.
            InfoBox(border = Amber.BonePale, padding = 0.u) {
                Label(
                    "NO HOME IS OPEN.\nGO BACK AND CHOOSE ONE.",
                    modifier = Modifier.padding(horizontal = 7.u, vertical = 6.u),
                    size = 6.0, color = Amber.BoneDim, tracking = 0.1, lineHeight = 1.9,
                )
            }
        } else {
            InfoBox(border = Amber.BoneDim, gap = 4.u) {
                Label(home.name, size = 11.0, color = Amber.BoneInk, tracking = 0.06)
                Label(
                    counted(home.floorCount, "FLOOR") + " . " +
                        counted(home.roomCount, "ROOM") + " . " +
                        counted(home.markerCount, "MARKER") + "\nABOUT " +
                        walkedInWords(home.roomCount, home.markerCount).uppercase() +
                        " OF WALKING",
                    size = 6.0, color = Amber.BoneDim, tracking = 0.1, lineHeight = 1.9,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            // The house comes up on this phone under this home's name, and then this phone joins
            // it like everybody else. Both halves are the actions layer's; the screen still names
            // where it goes, because it goes there either way.
            RowButton(
                border = Amber.Slate, fill = Amber.SlateFill,
                onClick = {
                    // Only when there is one. With no home open this screen already says so, and
                    // a house advertised under an empty name is a row every phone on the network
                    // can see and nobody can identify.
                    home?.let { actions.hostHome(it.name) }
                    go(ScreenId.Lobby)
                },
            ) {
                Label("HOST WITH THIS HOME", size = 8.0, color = Amber.SlateInk)
                Label(">", size = 8.0, color = Amber.BoneDim)
            }
            // Both of these open the home in the editor first. A rename that did not would type a
            // new name over whichever house the editor happened to be holding.
            RowButton(
                border = Amber.BoneFaint,
                onClick = {
                    actions.editOpenHome()
                    go(ScreenId.Editor)
                },
            ) {
                Label("EDIT THE PLAN", size = 8.0, color = Amber.BoneDeep)
                Label(">", size = 8.0, color = Amber.BoneFaint)
            }
            RowButton(
                border = Amber.BoneFaint,
                onClick = {
                    actions.editOpenHome()
                    go(ScreenId.SaveName)
                },
            ) {
                Label("RENAME", size = 8.0, color = Amber.BoneDeep)
                Label(">", size = 8.0, color = Amber.BoneFaint)
            }
        }
        homes.refusal?.let {
            Label(it, size = 6.5, color = Amber.BoneInk, tracking = 0.1, lineHeight = 1.6)
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
    val actions = LocalActions.current
    val home = LocalHomes.current.open
    PrePage(gap = 7) {
        PreHeading("DELETE HOME", back = ScreenId.HomeDetail)
        InfoBox(border = Amber.BoneDim, padding = 8.u, gap = 5.u) {
            Label(home?.name ?: "NO HOME IS OPEN", size = 10.0, color = Amber.BoneInk, tracking = 0.06)
            // What is about to be lost, counted off the home rather than described. The minutes
            // are the argument: the plan can be painted again, and the evening cannot.
            Label(
                if (home == null) "Nothing is open, so nothing here would be deleted."
                else counted(home.floorCount, "floor").lowercase() + ", " +
                    counted(home.roomCount, "room").lowercase() + " and " +
                    counted(home.markerCount, "marker").lowercase() + ". About " +
                    walkedInWords(home.roomCount, home.markerCount) + " of walking this home.",
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
            // The destructive control is a two-second hold and is outlined; the safe one is a
            // single tap and is the lit button. Inverted from habit, on purpose: what is being
            // protected is fifteen minutes of walking a real house, so the brighter button is
            // the one that protects it.
            HoldToConfirm(
                "HOLD TO DELETE",
                restingNote = "HOLD THE BUTTON FOR TWO SECONDS.",
                onConfirm = actions.deleteHome,
            )
            SlateButton("KEEP THIS HOME", { go(ScreenId.HomeDetail) }, verticalPadding = 11.u)
        }
    }
}

/**
 * **Counts and settings. Nobody's name appears on this screen.**
 *
 * Every dial the host owns, plus the two numbers the house publishes: how many are here, and how
 * many have handed their line over. **Locks at arming**, and stamps into the recording — balance
 * values cannot be edited mid-round, because a round whose numbers changed under it is a round
 * that cannot be replayed, and replay is the only debugging this game has.
 *
 * ### There are no names here, and their absence is not an omission
 *
 * The design's lobby shows counts, and the model behind this screen is
 * [home.someoneshome.model.protocol.LobbyBody.Standing] — three integers, incapable of naming
 * anybody. The seat chips this screen used to draw were a fixture, and reinstating them would
 * mean widening what a client receives, which is not a call a lobby screen makes.
 *
 * The presence strip below is the same two integers drawn rather than written. It carries no
 * identity and structurally cannot: the marks are filled by arithmetic — `linesIn` of them — not
 * by seat, because the seat is a thing this phone was never told.
 *
 * ### The Insider count is the host's, and the band clamps it (D-103)
 *
 * Default UNKNOWN: the house draws the count at arming, locks it, and tells no one until the
 * round ends. What makes hiding affordable is that SystemIntegrity reaches a panel only as a
 * percentage. On a phone that is not hosting the row is a reading, not a control.
 *
 * ### The vote window is the host's too, and it is the only live row that does not travel
 *
 * The two are drawn the same and behave differently one layer down: INSIDERS is sent, clamped and
 * echoed back, while VOTING moves a number this phone holds alone. The difference is deliberate —
 * what a client may put on the wire is a protocol decision — and it is invisible here on purpose,
 * because a host should not have to know which of their settings has reached the house yet.
 */
@Composable
fun LobbyScreen() {
    val actions = LocalActions.current
    val lobby = LocalLobby.current
    val standing = lobby.standing
    PrePage {
        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.BoneFaint)
                .padding(horizontal = 7.u, vertical = 6.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // The home this phone is in: the one it attached to over the air, or — on the
                // host's own device, which attached to nothing — the one they chose on the way
                // in. Otherwise this screen names a different house from the one they just
                // tapped HOST WITH THIS HOME on.
                Label(
                    lobby.attached?.name ?: LocalHomes.current.open?.name ?: HomeEditorModel.BUNGALOW,
                    size = 7.0, color = Amber.BoneDim, tracking = 0.12,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Label(
                    "${standing.joined} JOINED",
                    size = 7.0, color = Amber.BoneInk, tracking = 0.12,
                )
            }
            PresenceStrip(joined = standing.joined, linesIn = standing.linesIn)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
            // The one setting this unit wires. UNKNOWN is the default and a real answer, not an
            // unset value — see D-103. Only the host may move it, so only the host gets a tap.
            PreRow(
                "INSIDERS", lobby.insidersLabel,
                border = if (lobby.hosting) Amber.BoneDim else Amber.BonePale,
                labelInk = if (lobby.hosting) Amber.BoneInk else Amber.BoneDeep,
                verticalPadding = 5.u,
                onClick = if (lobby.hosting) actions.cycleInsiders else null,
            )
            // The rest are the design's own numbers, in the design's own order. Two of them are
            // live and the others are not yet wired to anything that could change them; playtest
            // owns those, as it owns the 7.
            PreRow(
                "INSIDERS KNOW EACH OTHER", "ON",
                border = Amber.BoneDim, labelInk = Amber.BoneInk, verticalPadding = 5.u,
            )
            PreRow("SUBROUTINES EACH", "7", verticalPadding = 5.u)
            PreRow("DISCUSSION", "90S", verticalPadding = 5.u)
            // The second row a host may touch, and it stays where the design put it rather than
            // moving up beside the other live one. It reads 45S because that is the design's
            // number (gdd.md:412) — it said 60S, agreeing with a flow table that had invented
            // the same wrong number. Unlike INSIDERS the tap goes no further than this phone:
            // see LobbyModel.cycleVoteWindow for why a settings row does not get to widen what a
            // client sends.
            PreRow(
                "VOTING", lobby.voteWindowLabel,
                border = if (lobby.hosting) Amber.BoneDim else Amber.BonePale,
                labelInk = if (lobby.hosting) Amber.BoneInk else Amber.BoneDeep,
                verticalPadding = 5.u,
                onClick = if (lobby.hosting) actions.cycleVoteWindow else null,
            )
            PreRow("EGRESS TIMER", "120S", verticalPadding = 5.u)
            PreRow("REVOKE COOLDOWN", "60S", verticalPadding = 5.u)
        }

        RowButton(border = Amber.BoneInk, onClick = { actions.nav(ScreenId.Secret) }) {
            Label("YOUR ONE LINE", size = 7.5, color = Amber.BoneInk)
            Label(
                if (lobby.line.handedOver) "HANDED OVER" else "REQUIRED",
                size = 7.5, color = Amber.BoneDim,
            )
        }
        // Not drawn while nobody is here: "0 OF 0 HANDED THEIRS OVER" is arithmetic rather than
        // information, and the strip above has already said the same thing in words.
        if (standing.joined > 0) {
            PreNote(
                "${standing.linesIn} OF ${standing.joined} HANDED THEIRS OVER",
                color = Amber.BoneDim, size = 5.5, lineHeight = 1.0, align = TextAlign.Center,
            )
        }
        LightsOut(hosting = lobby.hosting, ready = lobby.everyLineIn, onArm = actions.lightsOut)
    }
}

/**
 * The two counts, drawn: one mark per seat, filled once as many lines are in.
 *
 * It replaces the fixture's row of names and does the job the names were doing — making the
 * number legible at a glance rather than only readable — while naming nobody. **The fill order
 * is arithmetic, not identity**: this phone was told two integers and could not order the marks
 * by seat if it wanted to.
 */
@Composable
private fun PresenceStrip(joined: Int, linesIn: Int) {
    if (joined <= 0) {
        PreNote("NOBODY HAS JOINED YET", color = Amber.BoneFaint, size = 6.0, lineHeight = 1.0)
        return
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        repeat(joined) { index ->
            val handed = index < linesIn
            Box(
                Modifier.size(width = 12.u, height = 9.u)
                    .border(1.u, if (handed) Amber.BoneInk else Amber.BonePale)
                    .background(if (handed) Amber.BoneSoft else Color.Transparent),
            )
        }
    }
}

/**
 * **LIGHTS OUT, gated on every line being in — and it is the host's control alone.**
 *
 * The host turns the lights off; everything after that is the house answering rather than the
 * host announcing, so a client gets the same fact without a button that would do nothing. Until
 * the gate closes the control is present and inert rather than absent: a button that appeared
 * when the last line arrived would move the layout under a host's thumb at the exact moment they
 * are about to press it.
 *
 * **What this button does is presentation only.** It walks the phone to the ARMED screen; it does
 * not arm a round, lock a ledger, draw an Insider count or start a clock. Real arming is the
 * loop's, and the loop is frozen.
 */
@Composable
private fun LightsOut(hosting: Boolean, ready: Boolean, onArm: () -> Unit) {
    when {
        !hosting -> PreNote(
            if (ready) "WAITING FOR THE HOST" else "WAITING FOR EVERYONE'S LINE",
            color = Amber.BoneDim, size = 7.0, lineHeight = 1.0, align = TextAlign.Center,
        )
        ready -> SlateButton(
            "LIGHTS OUT", onArm, tracking = 0.2, size = 9.0, verticalPadding = 11.u,
        )
        // The same block, the same size, no tap target. Written out rather than reusing
        // [PanelButton]: that one always publishes a click action, and a control that answers a
        // press by doing nothing is indistinguishable from one that is broken — on the screen
        // where a host is waiting to find out why the evening has not started.
        else -> Box(
            Modifier.fillMaxWidth().border(1.u, Amber.BonePale).padding(vertical = 11.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                "LIGHTS OUT",
                size = 9.0, color = Amber.BoneFaint, tracking = 0.2, align = TextAlign.Center,
            )
        }
    }
}
