package home.someoneshome.ui

import home.someoneshome.model.CardRejection
import home.someoneshome.model.HomeReview
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerShape
import home.someoneshome.model.RoomKind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Host setup: mapping a house, registering its markers, and locking the round's dials.
 *
 * Once per house, in the light. Roughly fifteen minutes of walking, which is why story 0.7 makes
 * the result survive a reinstall — losing it costs an evening.
 *
 * **Drawn as the mockup draws it** (D-202): every screen here is `s_setup.py`'s, in mockup pixels,
 * on the bone palette. The plan, the room panel's chips and the marker shapes are
 * this file's own; the rest is the kit.
 */

/** The bone palette every screen in this file is drawn in. */
private val P = Palette.BONE

// ---------------------------------------------------------------------------------------------
// Saved homes and the plan
// ---------------------------------------------------------------------------------------------

/**
 * Saved homes. Fifteen minutes of walking, kept forever — **and read off the phone, not written
 * down here.**
 *
 * The counts beside each name are counted off the plan that was stored, so a home cannot say two
 * floors in this list and draw three in the editor. Newest first, which is the only ordering claim
 * the list makes; every row is drawn alike, as the mockup draws them.
 */
@Composable
fun MapsScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val homes = LocalHomes.current
    KitScreen {
        KitHeader("SAVED HOMES", P, trail = true)
        when {
            // The file is there and this build cannot read it. Said here, where the host is
            // looking for their houses, and nothing is written over them until somebody decides
            // what to do about it.
            homes.unreadable -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KitBody("THESE COULD NOT BE READ", P, size = 26)
                KitCaption(
                    "Something on this phone is holding homes in a form this build does not " +
                        "know. Nothing has been changed and nothing will be written over them.",
                    P,
                )
            }

            homes.isEmpty -> KitCaption("No homes yet. Map one and it stays on this phone.", P)

            else -> KitRules(P) {
                homes.homes.forEach { home ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).edgeLine(PanelSide.Bottom, P.line, 1.dp)
                            .tap {
                                actions.openSavedHome(home.name)
                                go(ScreenId.HomeDetail)
                            }
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Words(
                                home.name, size = 28, color = P.ink, lineHeight = 1.0,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Words(
                                counted(home.floorCount, "FLOOR") + " . " + counted(home.roomCount, "ROOM"),
                                size = 22, color = P.dim, lineHeight = 1.0,
                            )
                        }
                        Chevron()
                    }
                }
            }
        }
        KitSpacer()
        // Nothing is open after this, so the save at the end of the walk adds a home rather than
        // replacing whichever one the host last looked at.
        KitPrimary("MAP A NEW HOME", P) {
            actions.mapNewHome()
            go(ScreenId.Editor)
        }
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
 * The grid draws whatever the host has painted, and the counts under the name are counted rather
 * than written down. What was a picture of one bungalow is now a picture of any house.
 */
@Composable
fun EditorScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    val editor = LocalEditor.current
    KitScreen(gap = 10.dp) {
        // The mockup pulls the counts up under the title by 12 of the column's 10.
        Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
            // The home under edit, not a fixture's name.
            KitHeader(editor.name, P, back = ScreenId.Maps, trail = true)
            KitCaption(
                "${editor.roomsOn(editor.floorName)} ROOMS . ${editor.markersOn(editor.floorName)} MARKERS",
                P, size = 20,
            )
        }

        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            editor.plan.floors.forEach { storey ->
                ToggleBlock(storey.name, held = storey.name == editor.floorName, Modifier.weight(1f)) {
                    actions.openFloor(storey.name)
                }
            }
            Box(
                Modifier.width(56.dp).height(56.dp).border(1.dp, P.ink)
                    .semantics(mergeDescendants = true) { contentDescription = "FLOORS" }
                    .goes(ScreenId.Floors),
                contentAlignment = Alignment.Center,
            ) { PanelIcon(PLUS, size = 18.dp, color = P.ink, strokeWidth = 2.5f, viewBox = 24f) }
        }

        Box(Modifier.fillMaxWidth().weight(1f).border(1.dp, P.line)) {
            EditorSurface(editor, actions.openRoomAt, Modifier.fillMaxSize())
        }

        // The hint, or the refusal that replaces it — in ink, so the host knows which of the two
        // they are reading without having to read it to find out.
        Words(
            editor.refusal ?: "Drag two corners to add a room. Tap a room to edit it.",
            size = 20, color = if (editor.refusal != null) P.ink else P.dim, lineHeight = 1.15,
        )

        val review = editor.review
        // The gate (D-127). It still goes somewhere when the home is short — to the screen that
        // names every missing thing at once, because a button that goes quiet teaches nothing about
        // why, and this one is the last thing between a host and an evening spent finding out the
        // hard way.
        KitBlock(
            vals.saveLabel(review), edge = vals.saveEdge(review), fill = vals.saveFill(review),
            ink = vals.saveInk(review), size = 24, tracking = 0.02,
        ) { go(if (review.passes) ScreenId.SaveName else ScreenId.ReviewNeeds) }
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
 *
 * **The header draws the arrow from the trail alone.** Both screens are reached from more than one
 * place (the plan, a scan, a warning) and the trail is the only thing that knows which, so no
 * fallback is named; `BackNavigationTest` fails a browsing surface that has a trail and no arrow.
 */
@Composable
private fun HeldPlanPage(
    title: String,
    trailing: String,
    gap: Dp,
    panel: @Composable ColumnScope.() -> Unit,
) {
    val editor = LocalEditor.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KitHeader(title, P, meta = trailing, trail = true)
            Box(Modifier.fillMaxWidth().weight(1f).border(1.dp, P.line)) {
                // Read-only here on purpose: this is the plan the host already knows, with the
                // room under discussion lit. Painting happens on the editor, where the whole grid
                // is under the finger and nothing is being decided about one room.
                EditorPlan(editor.editorCells(), Modifier.fillMaxSize())
                EditorLabels(
                    editor.rooms,
                    Modifier.fillMaxSize(),
                    held = editor.held,
                    markers = { editor.markersIn(it).size },
                    terminal = editor.terminal,
                    meeting = editor.meeting,
                    size = 17,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Amber.Bone)
                .edgeLine(PanelSide.Top, P.ink, 2.dp)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
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
    HeldPlanPage(editor.heldName, "ROOM PANEL", gap = 10.dp) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HomeEditorModel.PRESETS.forEach { preset ->
                val held = preset == editor.heldName
                Box(
                    Modifier.height(40.dp).border(1.dp, if (held) P.ink else P.line)
                        .background(if (held) P.ink else Color.Transparent)
                        .tap { actions.nameRoom(preset) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Words(preset, size = 22, color = if (held) Amber.Bone else P.dim, lineHeight = 1.0) }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(46.dp).edgeLine(PanelSide.Bottom, P.ink, 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tag("ROOM NAME", P.dim, SILK_LINE)
            Words(
                editor.heldName, Modifier.weight(1f, fill = false), size = 30, color = P.ink,
                lineHeight = 1.0, tracking = 0.06, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.size(width = 12.dp, height = 24.dp).background(P.ink))
        }

        // The refusal a rename can produce, said here rather than on the plan the host is not
        // looking at. A preset chip for a name another room already holds is the likely one.
        editor.refusal?.let { KitBody(it, P, size = 20) }

        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleBlock("ROOM", editor.heldKind == RoomKind.Room, Modifier.weight(1f)) {
                actions.pickRoomType(RoomKind.Room)
            }
            ToggleBlock("STAIRS", editor.heldKind == RoomKind.Stairs, Modifier.weight(1f)) {
                actions.pickRoomType(RoomKind.Stairs)
            }
        }

        if (editor.heldKind == RoomKind.Room) {
            Row(
                Modifier.fillMaxWidth().height(56.dp)
                    .edgeLine(PanelSide.Top, P.line, 1.dp).edgeLine(PanelSide.Bottom, P.line, 1.dp)
                    .goes(ScreenId.MarkerSheet).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Words("MARKERS", Modifier.weight(1f), size = 24, color = P.ink, lineHeight = 1.0)
                editor.heldMarkers.forEach { Shape(it, 20.dp, P.ink) }
                Chevron()
            }
        } else {
            // Stairs are somewhere people walk through, so nothing can live in them. The row
            // stays in place and goes quiet rather than vanishing: a control that disappears
            // teaches nothing about why. It is structural rather than drawn — a registration
            // into stairs cannot be constructed (D-099), and the editor gave the cards up as
            // part of the type change rather than afterwards.
            Column(
                Modifier.fillMaxWidth()
                    .edgeLine(PanelSide.Top, P.line, 1.dp).edgeLine(PanelSide.Bottom, P.line, 1.dp)
                    .padding(horizontal = 2.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Words("MARKERS", Modifier.weight(1f), size = 24, color = P.dim, lineHeight = 1.0)
                    Words("NONE HERE", size = 24, color = P.dim, lineHeight = 1.0)
                }
                KitCaption("Nothing can be registered in stairs.", P, size = 20)
            }
        }

        KitPrimary("DONE", P, size = 28, height = 56.dp) { go(ScreenId.Editor) }
        Underlined(
            deleteRoomLabel(
                editor.heldMarkers.size,
                terminal = editor.terminal == editor.heldName,
                meeting = editor.meeting == editor.heldName,
            ),
            Modifier.offset(y = (-6).dp),
        ) {
            actions.deleteRoom()
            go(ScreenId.Editor)
        }
    }
}

/**
 * What deleting this room takes with it, named rather than counted at the host afterwards.
 *
 * The design's line is DELETE ROOM AND ITS 2 MARKERS, written for a room that had two. A room
 * with none must not be told it is losing none, and a room holding a reserved card is losing
 * something the home cannot be hosted without — so each one gets said out loud, by name.
 */
private fun deleteRoomLabel(markers: Int, terminal: Boolean, meeting: Boolean): String {
    val cards = when (markers) {
        0 -> null
        1 -> "ITS 1 MARKER"
        else -> "ITS $markers MARKERS"
    }
    val parts = listOfNotNull(
        cards,
        if (terminal) "ITS TERMINAL" else null,
        if (meeting) "ITS MEETING CARD" else null,
    )
    return if (parts.isEmpty()) "DELETE ROOM"
    else "DELETE ROOM AND " + parts.joinToString(" AND ")
}

/**
 * Turning an occupied room into stairs is destructive, and the host is told what it costs
 * **before** it happens.
 *
 * The cards are shown, not counted — the host is about to go and find these specific pieces of
 * paper, so a number would be the wrong thing to give them.
 *
 * **UNREGISTER AND CONTINUE is a two-second hold** (D-141): it discards a registration the host
 * climbed the stairs to make, and there is no way back to it except walking up there again with
 * the cards in hand. The safe control is the tap and the lit block, which is the Delete screen's
 * inversion and the same argument.
 */
@Composable
fun StairsWarnScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    val editor = LocalEditor.current
    KitScreen(gap = 14.dp) {
        KitHeader("${editor.heldName} BECOMES STAIRS", P, back = ScreenId.RoomEdit, trail = true)
        KitLabel("THESE WOULD BE UNREGISTERED", P, SILK_LINE)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.heldMarkers.forEach {
                Box(Modifier.size(56.dp).border(1.dp, P.ink), contentAlignment = Alignment.Center) {
                    Shape(it, 28.dp, P.ink)
                }
            }
            // Only if this room is actually the one holding it. The port drew the T on every
            // room, which told a host they were about to lose a terminal that was two rooms away
            // and perfectly safe.
            if (editor.terminal == editor.heldName) TokenBox("T")
            if (editor.meeting == editor.heldName) TokenBox("U")
        }
        KitBody(
            "These cards will belong to no room. Scan them again in a real room, or their " +
                "subroutines are gone.",
            P,
        )
        KitSpacer()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KitPrimary("MOVE THEM FIRST", P) { go(ScreenId.MarkerSheet) }
            KitHold("UNREGISTER AND CONTINUE", P, actions.confirmStairs, height = 56.dp, size = 24)
        }
    }
}

/** A reserved card in the 56 box the stairs warning draws a marker in. */
@Composable
private fun TokenBox(letter: String) {
    Box(Modifier.size(56.dp).border(1.dp, P.ink), contentAlignment = Alignment.Center) {
        KitToken(letter, P.ink, 28.dp)
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
    HeldPlanPage("MARKERS", "${editor.heldName} . ${cards.size}", gap = 8.dp) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Keyed on the card, not on the shape. Two rooms can never hold the same shape
            // (D-086) but the chip has to name a card to take one off, and the shape is what the
            // host reads — so the chip shows the shape and removes the id underneath it.
            cards.forEach { registration ->
                Row(
                    Modifier.height(52.dp).border(1.dp, P.ink)
                        .semantics(mergeDescendants = true) { contentDescription = REMOVE_MARKER }
                        .tap { actions.forgetMarker(registration.card.id) }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Shape(registration.card.shape, 24.dp, P.ink)
                    Cross()
                }
            }
        }

        // The two reserved cards, drawn the same way and for the same reason: where each one is
        // is a fact about the whole home rather than about this room, and here is where the host
        // would otherwise scan a second one.
        KitRules(P) {
            ReservedRow("T", "TERMINAL", editor.terminal, ScreenId.TermRemove, "Remove the terminal")
            ReservedRow("U", "MEETING CARD", editor.meeting, ScreenId.MeetRemove, "Remove the meeting card")
        }

        KitPrimary("REGISTER MARKER", P, size = 28, height = 60.dp) { go(ScreenId.ScanMarker) }
        KitSecondary("DONE", P) { go(ScreenId.RoomEdit) }
    }
}

/** What a marker chip on the sheet is called: its one act. */
const val REMOVE_MARKER: String = "Remove this marker"

/**
 * One reserved card on the marker sheet: what it is, where it is, and the way to take it out.
 *
 * **Present whether or not the card is placed**, saying NOT PLACED when it is not. A row that
 * appeared once the card existed would be a row a host never sees while they still need to be
 * told the card exists at all — and this sheet is the only screen that says so.
 */
@Composable
private fun ReservedRow(letter: String, name: String, at: String?, remove: ScreenId, removeName: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).edgeLine(PanelSide.Bottom, P.line, 1.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitToken(letter, P.ink, 26.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Words(name, size = 24, color = P.ink, lineHeight = 1.0)
            Words(" ", size = 24, color = P.ink, lineHeight = 1.0)
            Words(at?.let { "IN $it" } ?: "NOT PLACED", size = 24, color = P.dim, lineHeight = 1.0)
        }
        // The × hangs 12 into the margin, so the finger gets 52 and the glyph sits on the edge.
        Box(
            Modifier.offset(x = 12.dp).size(52.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = removeName
                    role = Role.Button
                }
                .goes(remove),
            contentAlignment = Alignment.Center,
        ) { Cross() }
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
    RegisterFrame(
        back = ScreenId.MarkerSheet,
        camera = true,
        viewfinder = {
            // What the last card did, shown as the shape rather than as a confirmation line: the
            // host has to match it against the piece of paper in their hand. Nothing is drawn
            // before the first card of this visit — an empty viewfinder is the honest state, and
            // the last session's card sitting there would read as one that had just been read.
            editor.lastScan?.let { ScanReadout(it) }
            TorchButton(vals) { actions.toggleTorch() }
        },
    ) {
        Row(
            Modifier.heightIn(min = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tag("THIS ROOM", P.dim, SILK_LINE)
            editor.heldMarkers.forEach { Shape(it, 22.dp, P.ink) }
            // **The two reserved cards, in the room they are actually in** (E-PAY-5). Drawn with
            // the tokens the rest of host setup uses for them and never as a marker glyph.
            if (editor.terminal == editor.heldName) KitToken("T", P.ink, 22.dp)
            if (editor.meeting == editor.heldName) KitToken("U", P.ink, 22.dp)
        }
        KitCaption("Keep scanning to add more.", P)
        KitPrimary("DONE SCANNING", P) { go(ScreenId.MarkerSheet) }
    }
}

/**
 * **REGISTER MARKER and its two refusals are one layout**, as the mockup draws them: the same
 * header, the same viewfinder at the same height, and a fixed action area under it with the lit
 * button always at the foot. Only what sits inside the viewfinder and inside the action area
 * changes, so a refusal arriving does not move the frame the host is looking at.
 *
 * [camera] puts the picture under the frame; only the scan itself runs it. A refusal is a decision
 * the host has to make, and nothing on it is being read.
 */
@Composable
private fun RegisterFrame(
    back: ScreenId,
    camera: Boolean,
    viewfinder: @Composable BoxScope.() -> Unit,
    action: @Composable ColumnScope.() -> Unit,
) {
    val editor = LocalEditor.current
    KitScreen {
        KitHeader("REGISTER MARKER", P, back = back, meta = editor.heldName, trail = true)
        Box(Modifier.testTag(REGISTER_VIEWFINDER).fillMaxWidth().weight(1f).background(Amber.SlateDefunct).clipToBounds()) {
            // **THE PICTURE, UNDER EVERYTHING ELSE IN THIS BOX.** See [LocalViewfinder] — the app
            // fills it and nothing here can.
            if (camera) LocalViewfinder.current(Modifier.fillMaxSize())
            Words(
                "BACK CAMERA . POINT AT THE CODE",
                Modifier.align(Alignment.TopCenter).padding(top = 14.dp).fillMaxWidth(),
                size = 20, color = Amber.SlateFill, lineHeight = 1.0, align = TextAlign.Center,
            )
            ViewfinderCorners()
            viewfinder()
        }
        Column(
            Modifier.fillMaxWidth().height(ACTION_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            content = action,
        )
    }
}

/** The viewfinder the three register screens share, for `RegisterFrameParityTest`. */
const val REGISTER_VIEWFINDER: String = "register-viewfinder"

/** The register screens' action area, fixed so the viewfinder above it never moves. */
private val ACTION_HEIGHT = 214.dp

/** The torch, at the viewfinder's foot, and its wash over the picture while it is on. */
@Composable
private fun BoxScope.TorchButton(vals: PanelVals, toggle: () -> Unit) {
    Box(Modifier.fillMaxSize().background(vals.torchWash))
    Box(
        Modifier.align(Alignment.BottomEnd).padding(10.dp).height(56.dp)
            .border(1.dp, Amber.SlateFill).background(vals.torchFill)
            .tap(toggle).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Words(vals.torchLabel, size = 22, color = vals.torchInk, lineHeight = 1.0) }
}

/**
 * **What the last card did, in the middle of the viewfinder.**
 *
 * One place for all six outcomes, because they are one thing to the host: they scanned a card and
 * the app has an answer. The shape is drawn large whichever way it went, in caution ink when the
 * card was turned away. **A refusal that named a room and not a shape would leave a host holding
 * three cards with no way to tell which one the app meant.**
 */
@Composable
private fun BoxScope.ScanReadout(outcome: ScanOutcome) {
    val refused = outcome !is ScanOutcome.Landed
    val ink = if (refused) Amber.Caution else Amber.SlateFill
    ViewfinderMiddle {
        when {
            outcome.isTerminal -> KitToken("T", ink, 44.dp)
            outcome.isMeeting -> KitToken("U", ink, 44.dp)
            else -> outcome.shape?.let { Shape(it, 56.dp, ink) }
        }
        Words(scanLine(outcome), size = 26, color = ink, lineHeight = 1.0, align = TextAlign.Center)
        // The card's printed id, quietly, and only when the card was read. It is the only place
        // in the app that ever shows one: a host looking for one specific card among nine
        // identical-looking ones has nothing else to go on (D-069).
        outcome.card?.let { Words(it.id.value, size = 18, color = ink, lineHeight = 1.0, tracking = 0.1) }
    }
}

/** The mockup's readout column: 228 wide, centred on 45% of the viewfinder's height. */
@Composable
private fun BoxScope.ViewfinderMiddle(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val centre = maxHeight * 0.45f
        Column(
            Modifier.align(Alignment.TopCenter).width(228.dp)
                .layoutCentredAt(centre),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Place this element so its vertical middle sits [y] from the top of its parent. */
private fun Modifier.layoutCentredAt(y: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        // Bounded by the viewfinder it sits in; with no bound it is simply as tall as it is.
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        layout(placeable.width, height) {
            placeable.place(0, (y.roundToPx() - placeable.height / 2).coerceAtLeast(0))
        }
    }
)

/** The outcome in the host's words. Every branch, so a new one cannot arrive as an empty line. */
private fun scanLine(outcome: ScanOutcome): String = when (outcome) {
    is ScanOutcome.Landed -> when {
        outcome.from != null -> "MOVED FROM ${outcome.from}"
        outcome.isTerminal -> "TERMINAL . ${outcome.room}"
        outcome.isMeeting -> "MEETING CARD . ${outcome.room}"
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
 * The four viewfinder brackets, at the mockup's 7% across and 11% / 20% down.
 *
 * Corners only. A full frame would read as a photograph of something rather than as an
 * instrument pointed at it.
 */
@Composable
private fun ViewfinderCorners() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val x = maxWidth * 0.07f
        val top = maxHeight * 0.11f
        val bottom = maxHeight * 0.20f
        val len = 22.dp
        val t = 3.dp
        val c = Amber.SlateFill

        @Composable
        fun bracket(left: Dp, y: Dp, vertical: PanelSide, horizontal: PanelSide) {
            Box(
                Modifier.offset(x = left, y = y).size(len)
                    .edgeLine(vertical, c, t)
                    .edgeLine(horizontal, c, t)
            )
        }

        bracket(x, top, PanelSide.Top, PanelSide.Start)
        bracket(maxWidth - x - len, top, PanelSide.Top, PanelSide.End)
        bracket(x, maxHeight - bottom - len, PanelSide.Bottom, PanelSide.Start)
        bracket(maxWidth - x - len, maxHeight - bottom - len, PanelSide.Bottom, PanelSide.End)
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
    val actions = LocalActions.current
    val editor = LocalEditor.current
    Taken(
        letter = "T", what = "terminal", at = editor.terminal.orEmpty(),
        second = "A second one would give the house two places to be found.",
        move = "MOVE THE TERMINAL TO ${editor.heldName}", onMove = actions.moveTerminal,
        note = "Moving it leaves ${editor.terminal.orEmpty()} with no terminal.",
    )
}

/**
 * A second meeting card, refused.
 *
 * **One home, one meeting card** (D-121), and the screen says where the existing one is rather
 * than only that there is one. A meeting is called by walking to this card and scanning it, so a
 * second card is a second place the house would take a meeting from, and half the party would walk
 * to the wrong one in the dark.
 */
@Composable
fun MeetTakenScreen() {
    val actions = LocalActions.current
    val editor = LocalEditor.current
    Taken(
        letter = "U", what = "meeting card", at = editor.meeting.orEmpty(),
        second = "A second one would give the house two places to be called to.",
        move = "MOVE THE MEETING CARD TO ${editor.heldName}", onMove = actions.moveMeeting,
        note = "Moving it leaves ${editor.meeting.orEmpty()} with nowhere to meet.",
    )
}

/**
 * **The two refusals, one layout** — [RegisterFrame] with the reserved card in caution ink and
 * the room it is already in lifted out of the sentence, because that room is the one thing the
 * host has to act on.
 *
 * KEEP goes back to the viewfinder and changes nothing: the card the host is holding is simply not
 * the one that places it, and there is nothing to undo because nothing happened. MOVE is the one
 * that acts, and it is the actions layer's.
 */
@Composable
private fun Taken(
    letter: String,
    what: String,
    at: String,
    second: String,
    move: String,
    onMove: () -> Unit,
    note: String,
) {
    val go = navigator()
    RegisterFrame(
        back = ScreenId.ScanMarker,
        camera = false,
        viewfinder = {
            ViewfinderMiddle {
                KitToken(letter, Amber.Caution, 44.dp)
                Words("ALREADY REGISTERED", size = 30, color = Amber.Caution, lineHeight = 1.0, align = TextAlign.Center)
                Words(
                    buildAnnotatedString {
                        append("This home has one $what and it is in ")
                        withStyle(SpanStyle(color = Amber.BoneChip)) { append(at) }
                        append(". $second")
                    },
                    size = 22, color = Amber.SlateFill, lineHeight = 1.12, align = TextAlign.Center,
                )
            }
        },
    ) {
        KitCaption(note, P, size = 20)
        KitSecondary(move, P, size = 22, onClick = onMove)
        KitPrimary("KEEP IT IN $at", P) { go(ScreenId.ScanMarker) }
    }
}

/** Demoting the terminal, and saying what it costs. */
@Composable
fun TermRemoveScreen() {
    val actions = LocalActions.current
    val editor = LocalEditor.current
    Remove(
        "REMOVE THE TERMINAL", "T", editor.terminal,
        "The T card will belong to no room. This home cannot be saved until a room has one.",
        actions.removeTerminal,
    )
}

/** Demoting the meeting card, and saying what it costs. */
@Composable
fun MeetRemoveScreen() {
    val actions = LocalActions.current
    val editor = LocalEditor.current
    Remove(
        "REMOVE THE MEETING CARD", "U", editor.meeting,
        "The meeting card will belong to no room. This home cannot be saved until a room has one.",
        actions.removeMeeting,
    )
}

/**
 * **REMOVE THE TERMINAL and REMOVE THE MEETING CARD are one layout**: a one-line title at the same
 * size, the token row, the explanation, and a fixed action area. Only the words change.
 */
@Composable
private fun Remove(title: String, letter: String, at: String?, text: String, onRemove: () -> Unit) {
    val go = navigator()
    KitScreen(gap = 16.dp) {
        KitHeader(title, P, back = ScreenId.MarkerSheet, titleSize = 28, trail = true)
        Row(
            Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitToken(letter, P.ink, 64.dp)
            Words(at?.let { "IN $it" } ?: "NOT PLACED", size = 36, color = P.ink, lineHeight = 1.0)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { KitBody(text, P) }
        Column(
            Modifier.fillMaxWidth().height(130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            KitSecondary("REMOVE IT", P, onClick = onRemove)
            KitPrimary("KEEP IT", P) { go(ScreenId.MarkerSheet) }
        }
    }
}

/**
 * **REVIEW is refused, and the screen names every reason at once** (D-126, D-127).
 *
 * D-127 made the gate three requirements — one terminal, one meeting card, eight ordinary markers
 * — and **all of them are drawn**, not the first one that fails. A host sent back for a terminal,
 * then for a meeting card, then for three more markers is a host walking their own house three
 * times over a fact the app knew on the first pass.
 *
 * **This screen is guidance about mechanics, never about houses** (D-125). Nothing here has an
 * opinion about how big a home is, how many rooms it has or what shape it is.
 */
@Composable
fun ReviewNeedsScreen(vals: PanelVals) {
    val go = navigator()
    val editor = LocalEditor.current
    val review = editor.review
    KitScreen {
        KitHeader(
            if (review.missing.size == 1) "THIS HOME NEEDS ONE MORE THING"
            else "THIS HOME NEEDS ${review.missing.size} MORE THINGS",
            P, back = ScreenId.Editor, titleSize = 28, trail = true,
        )
        // Drawn in the gate's own order, which is the order a host would go and fix them. An
        // empty list cannot reach this screen — REVIEW HOME goes to SaveName then — and if it ever
        // did, the list simply has nothing in it rather than the screen claiming otherwise.
        review.missing.forEachIndexed { i, missing -> MissingRequirement(missing, first = i == 0) }
        KitSpacer()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The capacity the home would have if the markers stopped here — guidance, never a
            // gate (D-127). Shown on the screen that is refusing, because the number the host is
            // working towards is the reason they are being asked for more markers at all.
            KitCaption(vals.capacityLine(review), P, Modifier.fillMaxWidth(), size = 20, center = true)
            // Back to the plan, not into the room panel. The host has to choose WHICH room each
            // card goes in, and the plan is the only screen that can ask that question.
            KitPrimary("OPEN A ROOM", P) { go(ScreenId.Editor) }
        }
    }
}

/**
 * One line of the gate, with its token and its instruction.
 *
 * Every branch is written out rather than derived from a string on [HomeReview.Missing], so the
 * copy stays in the layer that owns copy and a new requirement is a compile error here rather
 * than a blank box on the screen a host is stuck on.
 */
@Composable
private fun MissingRequirement(missing: HomeReview.Missing, first: Boolean) {
    when (missing) {
        HomeReview.Missing.Terminal -> Requirement(
            "SCAN THE CARD MARKED T", "Scan it in a room. That room becomes the terminal.", first,
        ) { KitToken("T", P.ink, 40.dp) }

        HomeReview.Missing.MeetingCard -> Requirement(
            "SCAN THE CARD MARKED U", "Scan it in a room. Meetings are called there.", first,
        ) { KitToken("U", P.ink, 40.dp) }

        is HomeReview.Missing.Markers -> Requirement(
            if (missing.short == 1) "ONE MORE MARKER" else "${missing.short} MORE MARKERS",
            "This home has ${missing.have} of the ${missing.need} a round needs. Scan more in its rooms.",
            first,
        ) {
            // The count, where the other two draw a token. There is no mark for "markers" that is
            // not one particular marker's shape, and a diamond here would send a host looking for
            // the diamond card.
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Words("${missing.have}/${missing.need}", size = 22, color = P.ink, lineHeight = 1.0)
            }
        }
    }
}

@Composable
private fun Requirement(title: String, body: String, first: Boolean, token: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (first) Modifier else Modifier.edgeLine(PanelSide.Top, P.line, 1.dp).padding(top = 12.dp)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        token()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KitBody(title, P, size = 26)
            KitBody(body, P, size = 22)
        }
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
 * counts-not-dots map never cashes in. The open floor is outlined.
 */
@Composable
fun FloorsScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val editor = LocalEditor.current
    val open = editor.floorName
    KitScreen {
        KitHeader("FLOORS", P, back = ScreenId.Editor, meta = "${editor.floorCount} IN THIS HOME", trail = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.plan.floors.forEach { storey ->
                val held = storey.name == open
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .border(if (held) 2.dp else 1.dp, if (held) P.ink else P.line)
                        .tap { actions.openFloor(storey.name) }
                        .padding(horizontal = if (held) 11.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Words(storey.name, Modifier.weight(1f), size = 28, color = P.ink, lineHeight = 1.0)
                    Words(
                        "${editor.roomsOn(storey.name)} ROOMS . ${editor.markersOn(storey.name)} MARKERS",
                        size = 22, color = if (held) P.ink else P.dim, lineHeight = 1.0,
                    )
                }
            }
        }
        // The new storey is added AND opened, and the host lands on it with an empty grid. Adding
        // one and staying here would leave them looking at a list, wondering whether it worked.
        Row(
            Modifier.fillMaxWidth().height(56.dp).border(1.dp, P.ink)
                .tap {
                    actions.addFloor()
                    go(ScreenId.Editor)
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelIcon(PLUS, size = 18.dp, color = P.ink, strokeWidth = 2.5f, viewBox = 24f)
            Words("ADD A FLOOR", size = 26, color = P.ink, lineHeight = 1.0)
        }
        KitSpacer()
        // Renaming and deleting a storey are the two controls here that are still inert. Both
        // need something this unit does not build — a text field for one, a two-second hold for
        // the other — so they name the open storey truthfully and take no press.
        KitRules(P) {
            InertRow("RENAME $open")
            InertRow("DELETE $open")
        }
        KitCaption(
            "Deleting $open removes ${editor.roomsOn(open)} rooms and ${editor.markersOn(open)} " +
                "markers. Hold two seconds.",
            P,
        )
    }
}

/** `kit.py`'s `navrow` with no `go`: drawn as the row is, and publishing no press. */
@Composable
private fun InertRow(text: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).edgeLine(PanelSide.Bottom, P.faint, 1.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Words(text, Modifier.weight(1f), size = 26, color = P.ink, lineHeight = 1.0)
        Chevron()
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
    KitScreen(gap = 14.dp) {
        KitHeader("SAVE HOME", P, back = ScreenId.Editor, trail = true)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KitLabel("NAME THIS HOME", P, SILK_LINE)
            KitField(P) {
                ReadoutField(editor.name, actions.nameHome, Modifier.fillMaxWidth(), size = 40.0, color = P.ink, tracking = 0.04)
            }
        }
        // What a save can refuse: an empty name, a name another home holds, and a phone that did
        // not write the file. Said here, because here is where the host is standing when it
        // happens and the alternative is a button that appears not to work.
        homes.refusal?.let { KitBody(it, P, size = 22) }
        // Counted off the plan, because this is the screen where a host checks that what they
        // walked is what the app has.
        KitRules(P) {
            KitInfo("FLOORS", "${editor.floorCount}", P)
            KitInfo("ROOMS", "${editor.roomCount}", P)
            KitInfo("MARKERS", "${editor.markerCount}", P)
            // Capacity, on the last screen before the home is kept. Guidance and never a gate
            // (D-127) — nothing on this screen refuses a save because of it.
            KitInfo("HOSTS UP TO", "${editor.review.hosts}", P)
        }
        KitSpacer()
        // The one control here that does not name where it goes: a refused save stays put, so the
        // target depends on an answer the screen does not have. Flow.viaActions carries it.
        KitPrimary("SAVE HOME", P, onClick = actions.saveHome)
    }
}

/**
 * One saved home: host with it, edit the plan, rename it — or throw it away.
 *
 * **Every number here is counted off the stored home**, including what it cost to walk. There is
 * no play count and no DUPLICATE: a number with no source that a host would nonetheless believe,
 * and a row nobody needs yet.
 *
 * **A host picks their house out of a list by its shape**, long before they read the counts, so
 * every storey is drawn. The counts stay, because they are what the plan cannot say.
 */
@Composable
fun HomeDetailScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val homes = LocalHomes.current
    val lobby = LocalLobby.current
    val home = homes.open
    KitScreen {
        KitHeader("SAVED HOME", P, back = ScreenId.Maps, titleSize = 24, trail = true)
        if (home == null) {
            // No home is open. Unreachable by walking — every route here opens one first — and
            // drawn rather than left blank because the picker can land on any screen.
            KitCaption("No home is open. Go back and choose one.", P)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Words(home.name, size = 48, color = P.ink, lineHeight = 0.9)
                Column {
                    KitCaption(
                        counted(home.floorCount, "floor", "floors") + " . " + counted(home.roomCount, "room", "rooms") +
                            " . " + counted(home.markerCount, "marker", "markers"),
                        P,
                    )
                    KitCaption("About ${walkedInWords(home.roomCount, home.markerCount)} of walking", P)
                    // Capacity, on the screen a host is looking at when they decide to host with
                    // this home. Guidance and never a gate (D-127): HOST WITH THIS HOME below is
                    // not conditioned on it.
                    KitCaption("Hosts up to ${home.review.hosts}", P)
                }
            }
        }
        if (home != null) StoreyPlans(home.plan, Modifier.weight(1f), named = true)
        else KitSpacer()
        // The house comes up on this phone under this home's name, and then this phone joins it
        // like everybody else. **The screen does not name where this goes** (E-D3-2): a home the
        // radio will not carry never reaches a lobby. The edge is in Flow.viaActions.
        KitPrimary("HOST WITH THIS HOME", P) {
            // Only when there is one. A house advertised under an empty name is a row every phone
            // on the network can see and nobody can identify.
            home?.let { actions.hostHome(it.name) }
        }
        // **The home that could not go on the air, said here, in the light** (E-D3-2). Two
        // refusal lines rather than one merged line because they belong to two different things —
        // the store refuses a save, the radio refuses an advertisement — and at most one is set.
        homes.refusal?.let { KitBody(it, P, size = 22) }
        lobby.refusal?.let { KitBody(it, P, size = 22) }
        KitRules(P) {
            // Both of these open the home in the editor first. A rename that did not would type a
            // new name over whichever house the editor happened to be holding.
            KitNavRow("EDIT THE PLAN", P, {
                actions.editOpenHome()
                go(ScreenId.Editor)
            })
            KitNavRow("RENAME", P, {
                actions.editOpenHome()
                go(ScreenId.SaveName)
            })
            // **The grant, one tap away** (the owner's 2026-08-24 ruling), and only under the
            // sentence it acts on — see [LobbyModel.offersTheGrant].
            if (lobby.offersTheGrant) KitNavRow("OPEN SETTINGS", P, actions.openSystemSettings)
        }
        Underlined("DELETE THIS HOME") { go(ScreenId.Delete) }
    }
}

/**
 * Every storey of a home, side by side — the plan as a picture rather than as counts.
 *
 * **A reading, never an editor.** Nothing here is tappable and no room is picked out. The aspect
 * ratio is held rather than stretched: a plan that filled its box would show a house the host has
 * never seen, and four storeys degrade into four narrow plans rather than into nonsense.
 */
@Composable
private fun StoreyPlans(plan: HousePlan, modifier: Modifier = Modifier, named: Boolean) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        plan.floors.forEach { storey ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (named) KitLabel(storey.name, P, SILK_LINE)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    EditorPlan(
                        planCells(storey),
                        Modifier.aspectRatio(HomeEditorModel.COLS.toFloat() / HomeEditorModel.ROWS)
                            .border(1.dp, P.line),
                    )
                }
            }
        }
    }
}

/**
 * Hold two seconds to delete. **Keep is the lit button.**
 *
 * The destructive control is outlined and the safe one is filled — inverted from habit, on
 * purpose. Fifteen minutes of walking a house is the thing being protected, so the brighter
 * button should be the one that protects it. **And the house is drawn**: the host sees the rooms
 * they walked while their finger is on a control that will take them away.
 */
@Composable
fun DeleteScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val home = LocalHomes.current.open
    KitScreen {
        KitHeader("DELETE HOME", P, back = ScreenId.HomeDetail, trail = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Words(home?.name ?: "NO HOME IS OPEN", size = 40, color = P.ink, lineHeight = 0.95)
            // What is about to be lost, counted off the home rather than described. The minutes
            // are the argument: the plan can be painted again, and the evening cannot.
            KitBody(
                if (home == null) "Nothing is open, so nothing here would be deleted."
                else counted(home.floorCount, "floor", "floors") + ", " + counted(home.roomCount, "room", "rooms") +
                    " and " + counted(home.markerCount, "marker", "markers") + ". About " +
                    walkedInWords(home.roomCount, home.markerCount) + " of walking this home.",
                P,
            )
        }
        KitCaption("This cannot be undone and nothing is backed up off this device.", P)
        if (home != null) StoreyPlans(home.plan, Modifier.weight(1f), named = false)
        else KitSpacer()
        KitHold("HOLD TO DELETE", P, actions.deleteHome)
        KitPrimary("KEEP THIS HOME", P) { go(ScreenId.HomeDetail) }
    }
}

/**
 * **Who is here, the counts, and the settings.**
 *
 * Every dial the host owns, plus the two numbers the house publishes: how many are here, and how
 * many have handed their line over. **Locks at arming**, and stamps into the recording — balance
 * values cannot be edited mid-round, because a round whose numbers changed under it is a round
 * that cannot be replayed, and replay is the only debugging this game has.
 *
 * ### The names arrived by a ruling, and the presence strip went with them (D-115)
 *
 * The host learns names and every phone receives them, so the list is drawn off the wire. **The
 * count of lines handed over stays a count and stays away from the names**: drawn one above the
 * other, the eye would pair the nth mark with the nth name and read *who* has handed a line over,
 * which the house never said.
 *
 * ### The Insider count and the vote window are the host's, and the rest are readings
 *
 * INSIDERS (D-103) is sent, clamped and echoed back; VOTING moves a number this phone holds alone
 * (D-117). On a phone that is not hosting both are readings. ⚠️ **The other four rows are readings
 * on every phone, and the GDD says they should be controls** (`gdd.md:911`) — a known gap: each is
 * a `Balance` constant `core` reads directly, and a row cycling a number `Rules` never consults is
 * exactly the fault D-117 closed. Wiring one means carrying it on `ArmingInputs` and
 * `Event.RoundArmed` into `GameState`, the shape `voteWindowSeconds` already took.
 */
@Composable
fun LobbyScreen(vals: PanelVals) {
    val actions = LocalActions.current
    val lobby = LocalLobby.current
    val standing = lobby.standing
    // The hosted home, and only on the phone hosting it. A client has never been sent this home's
    // marker count and must not draw a number it would be guessing at.
    val hosted = if (lobby.hosting) LocalHomes.current.open else null
    KitScreen(gap = 8.dp) {
        // The home this phone is in: the one it attached to over the air, or — on the host's own
        // device, which attached to nothing — the one they chose on the way in.
        KitHeader(
            lobby.attached?.name ?: LocalHomes.current.open?.name ?: HomeEditorModel.BUNGALOW,
            P, meta = "${standing.joined} JOINED",
        )
        ResidentStrip(lobby.residents, lobby::isMine)
        // Capacity guidance, and it is guidance (D-127) — LIGHTS OUT below is not conditioned on
        // it. Absent entirely when the party fits.
        hosted?.let { home ->
            vals.crowdedLine(standing.joined, home.review)?.let { KitCaption(it, P, size = 20) }
        }
        KitRules(P) {
            // UNKNOWN is the default and a real answer, not an unset value — see D-103. Only the
            // host may move it, so only the host gets a tap.
            LobbyRow("INSIDERS", lobby.insidersLabel, if (lobby.hosting) actions.cycleInsiders else null)
            LobbyRow("INSIDERS KNOW EACH OTHER", "ON")
            // **Not a literal** — D-129's `K`, asked of the balance rules for the party actually
            // standing here. See LobbyModel.orderSizeLabel.
            LobbyRow("SUBROUTINES EACH", lobby.orderSizeLabel)
            LobbyRow("DISCUSSION", "90S")
            // The tap goes no further than this phone: see LobbyModel.cycleVoteWindow.
            LobbyRow("VOTING", lobby.voteWindowLabel, if (lobby.hosting) actions.cycleVoteWindow else null)
            LobbyRow("EGRESS TIMER", "120S")
            LobbyRow("REVOKE COOLDOWN", "60S")
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).border(1.dp, P.ink)
                .tap { actions.nav(ScreenId.Secret) }.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Words("YOUR ONE LINE", Modifier.weight(1f), size = 26, color = P.ink, lineHeight = 1.0)
            val done = lobby.line.handedOver
            Box(
                Modifier.border(1.dp, if (done) P.dim else P.ink).background(if (done) Color.Transparent else P.ink)
                    .padding(1.dp).padding(horizontal = 6.dp, vertical = 5.dp)
            ) { Tag(if (done) "HANDED OVER" else "REQUIRED", if (done) P.ink else Amber.Bone, Modifier.height(12.dp)) }
        }
        // Not drawn while nobody is here: "0 of 0" is arithmetic rather than information. It stays
        // a count and stays down here, away from the names.
        if (standing.joined > 0) {
            KitCaption("${standing.linesIn} of ${standing.joined} handed theirs over", P, Modifier.fillMaxWidth(), center = true)
        }
        KitSpacer()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LightsOut(
                hosting = lobby.hosting,
                ready = lobby.readyToArm,
                waitingFor = lobby.waitingFor(),
                onArm = actions.lightsOut,
            )
            LobbyExit(hosting = lobby.hosting, actions = actions)
        }
    }
}

/** One of the lobby's settings: a key and its value on a hairline, a press only where it is live. */
@Composable
private fun LobbyRow(key: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).edgeLine(PanelSide.Bottom, P.line, 1.dp)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Words(key, Modifier.weight(1f), size = 20, color = P.dim, lineHeight = 1.0)
        Words(value, size = 22, color = P.ink, lineHeight = 1.0)
    }
}

/**
 * **The way out of a lobby — one control, two acts** (E-PAY-6).
 *
 * A client leaves and the party carries on without them. A host cannot: the house runs on their
 * phone, so a host who walks out takes the server and the advertisement with them whatever the
 * button is called. So the label says which act this is, and there is exactly one of them on any
 * phone. **A two-second hold** (D-141): it costs a seat and a line, or everybody's.
 *
 * **No note under it** (the owner, 2026-11-16; D-235). It used to name the consequence; the label
 * already says which act it is, and screens say little. A guest's label is LEAVE THIS HOUSE, the
 * owner's words, beside the host's CLOSE THE HOUSE.
 */
@Composable
private fun LobbyExit(hosting: Boolean, actions: PanelActions) {
    KitHold(
        if (hosting) "CLOSE THE HOUSE" else "LEAVE THIS HOUSE",
        P,
        if (hosting) actions.terminateHouse else actions.leaveLobby,
        height = 44.dp, size = 22, note = false,
    )
}

/**
 * **The residents in this lobby, one chip each, in the order the house seated them.**
 *
 * All alike, on purpose. The chip carries the name and nothing else, because every other thing a
 * chip could say about a person is something the house did not send.
 *
 * **Every chip matching the name this phone typed is marked** (the owner's ruling), by its edge
 * coming up to a 2-unit rule in ink. With one ROSE that is you; with two it says *one of these is
 * you*, which is true, and never *that one is you*, which would be a guess (see [LobbyModel.isMine],
 * and D-191 for why a phone is not told which seat it is).
 */
@Composable
private fun ResidentStrip(residents: List<String>, isMine: (String) -> Boolean) {
    if (residents.isEmpty()) {
        KitCaption("NOBODY HAS JOINED YET", P, size = 20)
        return
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (name in residents) {
            val mine = isMine(name)
            Box(
                Modifier.border(if (mine) 2.dp else 1.dp, if (mine) P.ink else P.line)
                    .padding(if (mine) 2.dp else 1.dp)
                    .padding(horizontal = if (mine) 7.dp else 8.dp, vertical = if (mine) 3.dp else 4.dp)
            ) {
                Words(
                    name.ifBlank { "UNNAMED" }, size = 20, color = if (name.isBlank()) P.dim else P.ink,
                    lineHeight = 1.0, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * **LIGHTS OUT, gated on the party and on every line being in — and it is the host's control
 * alone.**
 *
 * Until the gate closes the control is present and inert rather than absent: a control that
 * appeared when the last line arrived would move the layout under a host's thumb. **One block
 * through both states**, so the thing under the thumb never moves; the gate is the hold refusing
 * to start, drawn dashed as the mockup draws a control that is not live yet. It is a two-second
 * hold (D-141), and [waitingFor] says which condition is open on every phone.
 *
 * **No helper text under it** (the owner, 2026-11-16; D-235): HOLD THE BUTTON FOR TWO SECONDS and
 * KEEP HOLDING are gone, because the bar running under the thumb already says both. What stays is
 * the inert button's reason, NEEDS n RESIDENTS or WAITING FOR EVERYONE'S LINE, which is not help with
 * the gesture but the answer to *why will it not start* (D-226). Its line is kept when the gate
 * opens, empty, so the block under the host's thumb does not move at the moment it goes live.
 */
@Composable
private fun LightsOut(hosting: Boolean, ready: Boolean, waitingFor: String?, onArm: () -> Unit) {
    if (!hosting) {
        KitCaption(waitingFor ?: "WAITING FOR THE HOST", P, Modifier.fillMaxWidth(), size = 20, center = true)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KitHold(
            "LIGHTS OUT", P, onArm,
            enabled = ready, lit = ready, inert = !ready,
            height = 56.dp, size = 30, tracking = 0.1, note = false,
        )
        // A space rather than nothing when the gate is open: the line keeps its height.
        KitCaption(waitingFor ?: " ", P, Modifier.fillMaxWidth(), size = 20, center = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Parts
// ---------------------------------------------------------------------------------------------

/** The mockup's plus, on its 24 grid. */
private val PLUS = listOf("M12 4v16M4 12h16")

/** The mockup's cross, on its 24 grid. */
private val CROSS = listOf("M5 5l14 14M19 5L5 19")

/** The mockup's right chevron, trailing a row that goes somewhere. */
private val CHEVRON = listOf("M9 4l8 8-8 8")

@Composable
private fun Chevron() = PanelIcon(CHEVRON, size = 18.dp, color = P.ink, strokeWidth = 2.5f, viewBox = 24f)

@Composable
private fun Cross() = PanelIcon(CROSS, size = 16.dp, color = P.ink, strokeWidth = 2.5f, viewBox = 24f)

/**
 * A marker's shape in the mockup's [box]. Filled, as every marker in the app is (see
 * [MarkerGlyph]), at the 10/16 of its box the mockup's shapes fill.
 */
@Composable
private fun Shape(shape: MarkerShape, box: Dp, ink: Color) {
    Box(Modifier.size(box), contentAlignment = Alignment.Center) { MarkerGlyph(shape, box * 10f / 16f, ink) }
}

/**
 * A 56-tall block in ink with a selected state: the floor tabs and the room-type chips. Selected is
 * the ink block with bone words; the other is the ink outline.
 */
@Composable
private fun ToggleBlock(text: String, held: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxSize().border(1.dp, P.ink).background(if (held) P.ink else Color.Transparent).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Words(text, size = 24, color = if (held) Amber.Bone else P.ink, lineHeight = 1.0, tracking = 0.06) }
}

/** A 64-tall block in whatever three colours the caller's state gives it. */
@Composable
private fun KitBlock(
    text: String,
    edge: Color,
    fill: Color,
    ink: Color,
    size: Int,
    tracking: Double,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(64.dp).border(1.dp, edge).background(fill).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Words(text, size = size, color = ink, lineHeight = 1.0, tracking = tracking, align = TextAlign.Center) }
}

/** `kit.py`'s `quiet`, underlined: the destructive way out that is not the lit block. */
@Composable
private fun Underlined(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().height(56.dp).tap(onClick), contentAlignment = Alignment.Center) {
        Words(
            text, size = 24, color = P.dim, lineHeight = 1.0, tracking = 0.04,
            align = TextAlign.Center, decoration = TextDecoration.Underline,
        )
    }
}
