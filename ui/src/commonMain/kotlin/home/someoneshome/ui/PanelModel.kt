package home.someoneshome.ui

import androidx.compose.ui.graphics.Color

/**
 * Every screen the device can be showing.
 *
 * **This enum is the design's screen list, not a navigation graph.** It says what exists; it says
 * nothing about what may follow what, because in play the house decides that and the device is
 * told. The walkable ordering in the gallery is a review convenience.
 */
enum class ScreenId {
    // Cold start — light-field, because the house lights are still on.
    Boot, Perms, Join,

    // Host setup — once per house, in the light.
    Maps, Editor, RoomEdit, MarkerSheet, NoTerminal, Floors, SaveName, HomeDetail, Delete, Lobby,

    // Arming — the host turns the lights off and the house does the rest.
    Secret, Armed, Notify, Reveal, RevealThread,

    // The springboard — identical for both roles.
    Home, Page2, Lock,

    // Work.
    Work, Scan, Sub, SubBright, Files, Notes, TermNo, TermLive, Timelapse,

    // The house's hands.
    Banner, EgressWidget,

    // The room.
    Calling, Call, Found, Assemble, Notice, Discussion, Vote, Tally,

    // Out.
    Revoked, Ghost2, GhostMeeting, Ghost3, Disconnect, Settings, WinInsiders, WinResidents,
}

/** The Revoke ability's three visual states. Both roles render all three; only one role acts. */
enum class RevokeState { Ready, Armed, Cooldown }

/**
 * Everything the device needs in order to draw itself.
 *
 * **Deliberately flat and inert.** No behaviour, no derivation from rules — `ui` cannot see
 * `core`, and a screen that could ask the rules a question is a screen that could leak the
 * answer. Every field here arrives already decided at the effect boundary.
 */
data class PanelState(
    val screen: ScreenId = ScreenId.Home,
    val role: PanelRole = PanelRole.Resident,
    val revoke: RevokeState = RevokeState.Ready,
    val markersOn: Boolean = false,
    val hasTerminal: Boolean = true,
    /** Randomises the message backlog's *count and mix*, so inbox density can never imply a role. */
    val inboxSeed: Int = 3,
    val noteSeed: Int = 0,
)

/**
 * How the status bar reads, which is a property of where the round is rather than of the screen.
 *
 * [Hidden] is not "no status bar drawn by accident" — the lantern and the walk-in screen suppress
 * it on purpose, because both are lit fields whose entire job is to emit light evenly.
 */
enum class PanelMode { Pre, Live, Ghost, End, Hidden }

/**
 * The design's `renderVals()`, ported whole.
 *
 * Kept as one derivation rather than scattered through the screens for the reason the source
 * gives: the same rule has to hold on thirty screens at once, and a rule applied thirty times by
 * hand is a rule that holds twenty-nine times. Role-dependent values in particular live *here*,
 * where they can be read side by side and checked against each other.
 */
class PanelVals(val state: PanelState) {

    val screen: ScreenId get() = state.screen
    val insider: Boolean get() = state.role == PanelRole.Insider
    val resident: Boolean get() = !insider

    // ---- Status bar ------------------------------------------------------------------------

    val mode: PanelMode = when (state.screen) {
        ScreenId.Lock, ScreenId.Ghost2 -> PanelMode.Hidden
        ScreenId.Boot, ScreenId.Join, ScreenId.Maps, ScreenId.Editor, ScreenId.SaveName,
        ScreenId.HomeDetail, ScreenId.Delete, ScreenId.Secret, ScreenId.RoomEdit,
        ScreenId.MarkerSheet, ScreenId.Floors, ScreenId.NoTerminal, ScreenId.Perms,
        ScreenId.Lobby, ScreenId.WinResidents,
        -> PanelMode.Pre

        ScreenId.WinInsiders -> PanelMode.End
        ScreenId.Revoked, ScreenId.Ghost3, ScreenId.GhostMeeting -> PanelMode.Ghost
        else -> PanelMode.Live
    }

    val isPre: Boolean get() = mode == PanelMode.Pre
    val statusVisible: Boolean get() = mode != PanelMode.Hidden

    val ink: Color = if (isPre) Amber.BoneInk else Amber.Bright
    val dim: Color = if (isPre) Amber.BoneDim else Amber.Dim
    val edge: Color = if (isPre) Amber.BoneEdge else Amber.Edge

    val carrier: String = when {
        state.screen == ScreenId.WinResidents -> "SOMEONE'S HOME"
        isPre -> ""
        mode == PanelMode.Ghost -> "UNREGISTERED"
        else -> "SOMEONE'S HOME"
    }

    /** Once the round starts the house owns the network: no bars on any live screen. */
    val signalOn: Color = if (isPre) Amber.BoneInk else Amber.Edge
    val signalOff: Color = when {
        isPre && state.screen != ScreenId.WinResidents -> Amber.BoneEdge
        isPre -> Amber.BoneInk
        else -> Amber.Edge
    }

    /**
     * The perimeter is armed for every in-game screen, *including a revoked phone*.
     *
     * A glyph rather than a word, so the fact is not repeated in text on thirty screens — and so
     * a revoked player's device says exactly what everyone else's says.
     */
    val armedGlyph: Boolean =
        mode == PanelMode.Live || mode == PanelMode.Ghost || state.screen == ScreenId.WinInsiders

    /** The same iris, drained. An unarmed perimeter, drawn like the dead bars, not as a new symbol. */
    val disarmedGlyph: Boolean = state.screen == ScreenId.WinResidents

    val lockChip: Boolean = state.screen == ScreenId.Files || state.screen == ScreenId.Lock

    val clock: String = when {
        state.screen == ScreenId.WinResidents -> "21:38"
        isPre -> "20:41"
        mode == PanelMode.Ghost -> "21:31"
        mode == PanelMode.End -> "21:38"
        else -> "21:07"
    }

    /**
     * Anything that arrives unasked buzzes: banners, calls, the meeting transitions, revocation,
     * and the round ending. Nothing the player *chose* to open does.
     */
    val buzzes: Boolean = state.screen in setOf(
        ScreenId.Armed, ScreenId.Notify, ScreenId.Banner, ScreenId.Call, ScreenId.Found,
        ScreenId.Assemble, ScreenId.Notice, ScreenId.Tally, ScreenId.Revoked,
        ScreenId.WinInsiders, ScreenId.WinResidents,
    )

    // ---- Messages ---------------------------------------------------------------------------

    /**
     * Leftovers that queued while the phone had no signal.
     *
     * The count *and* the mix are randomised per player, so inbox density can never imply a role.
     * A fixed backlog would make "how many unread" a channel, and it would be one nobody thought
     * of as a channel.
     */
    private val backlog = listOf(
        Triple("MUM", "20:12", "did you get there ok? text me when you"),
        Triple("DELIVERY", "20:19", "Your parcel could not be left with a neigh"),
        Triple("FLAT CHAT", "20:31", "Tomas: running late, start without me"),
        Triple("VOICEMAIL", "20:44", "Missed call. No message left."),
        Triple("BANK", "20:47", "A payment of 12.40 was authorised on your"),
        Triple("UNKNOWN", "20:52", "sorry wrong number"),
        Triple("PRIYA", "20:55", "im outside, which door"),
    )

    data class InboxRow(
        val from: String,
        val at: String,
        val preview: String,
        val edge: Color,
        val fromInk: Color,
        val ink: Color,
        val house: Boolean = false,
    )

    val inbox: List<InboxRow> = run {
        val seed = state.inboxSeed
        val keep = 3 + (seed % 4)
        val start = seed % 3
        val rest = backlog.drop(start).take(keep).map { (from, at, preview) ->
            InboxRow(from, at, preview, Amber.Edge, Amber.Faint, Amber.Dim)
        }
        // Identical sender, time and preview for both roles. The thread must be OPENED to read —
        // the row itself cannot be a tell to anyone glancing at a neighbour's screen.
        listOf(
            InboxRow(
                "HOUSE", "21:02", "Regarding this evening. Please read.",
                Amber.Dim, Amber.Bright, Amber.Bright, house = true,
            )
        ) + rest
    }

    /** Only the newest message in the house thread differs by role. Everything above it is shared. */
    val houseLine: String = if (insider) {
        "You still have Priya's spare key. Follow my instructions tonight and nobody else " +
            "needs to hear about it."
    } else {
        "I am watching you. Thank you for your cooperation."
    }

    /**
     * Notes writes to you, not the other way round.
     *
     * The Resident lines are plausible, unverifiable, and deliberately *not* context aware — a
     * line that referred to something that actually happened would be a channel from the
     * authority to one player, which is the thing this app does not have.
     */
    private val residentNotes = listOf(
        "why was rose in the garage",
        "i have not seen marcus finish anything yet",
        "priya was upstairs when the lights went",
        "dani walked past the study twice",
        "somebody was already at marker 07",
        "tomas has not been in the hall all night",
        "count was four. i only saw three of you",
        "rose scanned nothing for a long time",
    )

    val noteLine: String =
        if (insider) "i hope no one finds out"
        else residentNotes[state.noteSeed.mod(residentNotes.size)]

    // ---- The springboard's two tiles ---------------------------------------------------------

    /**
     * **Baselined to the Insider's brightness for BOTH roles.**
     *
     * A dimmer page 2 for Residents would be readable across a room as *"this one has nothing to
     * tap"*, which is the tell the whole parity discipline exists to prevent. The Resident sees
     * this page lit exactly as an Insider does; nothing here answers them.
     */
    val tileBorder: Color = Amber.Dim
    val tileInk: Color = Amber.Bright
    val tier2Ink: Color = Amber.Mid

    val abilityName: String = if (insider) "REVOKE" else "POWER"

    /**
     * The subtitle is identical in the armed and cooling states, and differs only in the resting
     * verb — TAP TO ARM against TAP TO TEST. Both are true sentences about the button in front of
     * you, and neither confirms anything.
     */
    val abilitySub: String = when (state.revoke) {
        RevokeState.Armed -> "ARMED . TOUCH THEIR PHONE"
        RevokeState.Cooldown -> "COOLING DOWN"
        RevokeState.Ready -> if (insider) "READY . TAP TO ARM" else "READY . TAP TO TEST"
    }

    val revokeBorder: Color = if (state.revoke == RevokeState.Armed) Amber.Bright else Amber.Dim
    val revokeFill: Color = if (state.revoke == RevokeState.Armed) Amber.Edge else Color.Transparent
    val revokeInk: Color = if (state.revoke == RevokeState.Cooldown) Amber.Dim else Amber.Bright
    val revokeSubInk: Color = if (state.revoke == RevokeState.Armed) Amber.Bright else Amber.Dim
    val revokeBarFraction: Float = if (state.revoke == RevokeState.Cooldown) 0.38f else 1f
    val revokeBarInk: Color = if (state.revoke == RevokeState.Cooldown) Amber.Dim else Amber.Bright

    val secondName: String = if (insider) "EGRESS" else "SUBSYS"
    val secondSub: String =
        if (insider) "SHARED WITH THE OTHER INSIDER" else "ALL SYSTEMS RESPONDING"

    val tier2A: String = if (insider) "SURGE" else "BUS"
    val tier2B: String = if (insider) "SPOOF" else "THERMAL"
    val tier2C: String = if (insider) "ISOLATE" else "CACHE"
    val tier2Note: String =
        if (insider) "SURGE, SPOOF AND ISOLATE SHARE THIS" else "BUS, THERMAL AND CACHE NOMINAL"

    // ---- Plan editor state -------------------------------------------------------------------

    /** No terminal, no playable plan: the save button says so rather than failing later. */
    val saveLabel: String =
        if (state.hasTerminal) "REVIEW HOME" else "REVIEW HOME . NEEDS A TERMINAL"
    val saveEdge: Color = if (state.hasTerminal) Amber.Slate else Amber.SlateDead
    val saveFill: Color = if (state.hasTerminal) Amber.SlateFill else Color.Transparent
    val saveInk: Color = if (state.hasTerminal) Amber.SlateInk else Amber.SlateDead

    val markerState: String = if (state.markersOn) "ON" else "OFF"
    val markerEdge: Color = if (state.markersOn) Amber.Slate else Amber.BonePale
    val markerFill: Color = if (state.markersOn) Amber.SlateFill else Color.Transparent
    val markerInk: Color = if (state.markersOn) Amber.SlateInk else Amber.SlateMute
    val markerChipInk: Color = if (state.markersOn) Amber.BoneMute else Amber.BoneFaint
    val markerChipFill: Color = if (state.markersOn) Amber.BoneChip else Amber.BoneChipOff
    val markerChipEdge: Color = if (state.markersOn) Amber.BoneMute else Amber.BonePutty

    val editorHint: String = if (state.markersOn) {
        "TAP ANYWHERE TO DROP A MARKER.\nTAP A MARKER TO RENAME OR REMOVE IT."
    } else {
        "DRAG TWO CORNERS TO ADD A ROOM.\nMARKERS IGNORE YOUR TAPS WHILE OFF."
    }

    // ---- Meeting ------------------------------------------------------------------------------

    /**
     * Being out is an information privilege: a player outside the system sees who voted for whom,
     * while the living only ever get a count.
     *
     * Safe only because of the sequencing — by the time this is visible, the room already knows
     * who is out, so there is never a window where someone out knows something the living do not.
     */
    data class BallotRow(
        val by: String,
        val forWhom: String,
        val arrow: String,
        val edge: Color,
        val ink: Color,
        val forInk: Color,
    )

    val ballots: List<BallotRow> = listOf(
        BallotRow("ELLIOT", "DANI", "→", Amber.Faint, Amber.Bright, Amber.Bright),
        BallotRow("PRIYA", "DANI", "→", Amber.Faint, Amber.Bright, Amber.Bright),
        BallotRow("DANI", "ROSE", "→", Amber.Faint, Amber.Bright, Amber.Bright),
        BallotRow("ROSE", "STILL DECIDING", "", Amber.Edge, Amber.Dim, Amber.Faint),
        BallotRow("TOMAS", "STILL DECIDING", "", Amber.Edge, Amber.Dim, Amber.Faint),
    )
}

/**
 * The bungalow's ground floor — six rooms on a ten-by-twelve grid.
 *
 * **One source of truth for every screen that draws a plan.** The editor, the live map, the
 * timelapse and the outside-the-system view all read these rects, so a room cannot be in one
 * place on the editor and another on the map. The design made that a rule and it is worth
 * keeping: two grids that agree by coincidence stop agreeing the first time one is edited.
 */
data class PlanRoom(
    val name: String,
    val r0: Int,
    val r1: Int,
    val c0: Int,
    val c1: Int,
    /** Stairs. Never counted, never carrying a Subroutine, never a timed route. */
    val transit: Boolean = false,
)

object Plan {
    const val COLS = 10
    const val ROWS = 12

    val rooms = listOf(
        PlanRoom("KITCHEN", 0, 2, 0, 3),
        PlanRoom("LIVING", 0, 2, 5, 9),
        PlanRoom("HALL", 4, 5, 0, 5),
        PlanRoom("STAIRS", 4, 5, 6, 9, transit = true),
        PlanRoom("STUDY", 7, 10, 0, 3),
        PlanRoom("GARAGE", 7, 9, 5, 9),
    )

    /** The room the reader is standing in, which is the only anchor a count-based map can offer. */
    const val HERE = "GARAGE"

    fun roomAt(row: Int, col: Int): PlanRoom? =
        rooms.firstOrNull { row >= it.r0 && row <= it.r1 && col >= it.c0 && col <= it.c1 }

    /**
     * Counts per room, never dots.
     *
     * A dot implies a trackable individual and no such thing exists in this system. You learn
     * that four people were in the living room, never which four — and a numeral says exactly
     * that and nothing more.
     */
    data class RoomCount(val room: PlanRoom, val count: Int, val ink: Color)

    private fun counts(spec: Map<String, Pair<Int, Color>>): List<RoomCount> =
        rooms.mapNotNull { room -> spec[room.name]?.let { RoomCount(room, it.first, it.second) } }

    /** Live view: three staleness bands, error injected both ways, your own room outlined. */
    val terminalCounts = counts(
        mapOf(
            "KITCHEN" to (2 to Amber.Bright),
            "LIVING" to (4 to Amber.Dim),
            "HALL" to (1 to Amber.Faint),
            "STUDY" to (0 to Amber.Edge),
            "GARAGE" to (1 to Amber.Bright),
        )
    )

    val timelapseCounts = counts(
        mapOf(
            "KITCHEN" to (3 to Amber.Dim),
            "LIVING" to (1 to Amber.Dim),
            "HALL" to (2 to Amber.Dim),
            "GARAGE" to (0 to Amber.Dim),
        )
    )

    /** True occupancy: live, no injected error, no staleness bands. Still counts, never identities. */
    val trueCounts = counts(
        mapOf(
            "KITCHEN" to (2 to Amber.Bright),
            "LIVING" to (3 to Amber.Bright),
            "HALL" to (0 to Amber.Bright),
            "STUDY" to (1 to Amber.Bright),
            "GARAGE" to (1 to Amber.Bright),
        )
    )
}

/**
 * One cell of the plan editor's grid.
 *
 * The editor draws a filled cell per grid square; the live map draws only the *edges* where a
 * room boundary falls. That is not two styles of the same thing — the editor is about shapes you
 * are building, and the map is about a room you might be standing in.
 */
data class EditorCell(val border: Color, val fill: Color?, val hatch: Boolean = false)

/**
 * One cell of a live plan. Borders are per-side, because an outline only exists where a cell's
 * neighbour belongs to a different room.
 *
 * **Adjacency falls out of cell neighbours**, which is the whole reason the map is a grid rather
 * than free rectangles: no geometry, no overlap rules, no snapping, and L-shaped rooms work for
 * free because real houses have them.
 */
data class MapCell(
    val top: Color?,
    val end: Color?,
    val bottom: Color?,
    val start: Color?,
    val fill: Color?,
)

/** The editor grid, with one room optionally held as the current selection. */
fun Plan.editorCells(focus: PlanRoom? = null): List<EditorCell> =
    List(ROWS * COLS) { i ->
        val row = i / COLS
        val col = i % COLS
        val room = roomAt(row, col)
        val held = focus != null && room != null && room.name == focus.name
        when {
            held -> EditorCell(Amber.SlateFocus, Amber.SlateFocusFill)
            room == null -> EditorCell(Amber.BonePutty, null)
            room.transit -> EditorCell(Amber.Slate, null, hatch = true)
            else -> EditorCell(Amber.Slate, Amber.SlateFill)
        }
    }

/**
 * The live grid. Reads the same rects as the editor, deliberately.
 *
 * Two grids that agree by coincidence stop agreeing the first time one is edited, and a map that
 * disagreed with the plan the host walked would be a bug nobody could see from inside the game.
 */
fun Plan.mapCells(): List<MapCell> =
    List(ROWS * COLS) { i ->
        val row = i / COLS
        val col = i % COLS
        val room = roomAt(row, col)
        val mine = room != null && room.name == HERE

        fun edgeTo(other: PlanRoom?): Color? =
            if (other !== room) (if (mine) Amber.Bright else if (room != null) Amber.Dim else Amber.Deep)
            else null

        MapCell(
            top = edgeTo(roomAt(row - 1, col)),
            end = edgeTo(roomAt(row, col + 1)),
            bottom = edgeTo(roomAt(row + 1, col)),
            start = edgeTo(roomAt(row, col - 1)),
            fill = when {
                room == null -> null
                room.transit -> Amber.Edge
                mine -> Amber.Deep
                else -> null
            },
        )
    }
