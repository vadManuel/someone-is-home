package home.someoneshome.ui

import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes

import androidx.compose.ui.graphics.Color

/**
 * Every screen the device can be showing.
 *
 * **A screen list, not a navigation graph.** It says what exists; it says nothing about what may
 * follow what, because in play the house decides that and the device is told. The ordering here
 * follows the design's own grouping, which is a review convenience only.
 */
enum class ScreenId {
    // Cold start — light-field, because the house lights are still on.
    Boot, Perms, Join,

    // Host setup — once per house, in the light.
    Maps, Editor, RoomEdit, PassageWarn, MarkerSheet, ScanMarker, TermTaken, TermRemove,
    NoTerminal, Floors, SaveName, HomeDetail, Delete, Lobby,

    // Arming — the host turns the lights off and the house does the rest.
    Secret, Armed, Notify, Reveal, RevealThread,

    // The springboard — identical for both roles.
    Home, Page2, Lock,

    // Work.
    Work, Scan, ScanCaught, ScanBad, ScanUnknown, Sub, SubBright, Files, Notes, TermNo, TermLive, Timelapse,

    // The house's hands.
    Banner, EgressWidget,

    // The room.
    Calling, Call, Found, Assemble, Notice, Discussion, Vote, Tally,

    // Out.
    Revoked, Restrained, Ghost2, GhostMeeting, Ghost3, Disconnect, Settings, WinInsiders, WinResidents,
}

/**
 * How a player left the round.
 *
 * **Two different things, and the vocabulary forbids collapsing them.** `Revoke` is system power
 * lent by the house and spent in the dark; `Restrain` is a physical act by the room at a meeting,
 * which the house cannot prevent. Both end on the same couch and the same screens, so the screen
 * alone cannot tell them apart — this is carried from the event that caused it.
 */
enum class OutBy { Revoked, Restrained }

/** The ability's three visual states. Both roles render all three; only one role acts. */
enum class RevokeState { Ready, Armed, Cooldown }

/**
 * What a room is for.
 *
 * **Stairs hold nothing** — never counted, never carrying a Subroutine, never a timed route. So
 * turning an occupied room into one is destructive, and the host is told what it costs before it
 * happens rather than after.
 */
enum class RoomType { Room, Stairs }

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
    /** Host-side only: the torch while registering markers, in the light, with the back camera. */
    val torch: Boolean = false,
    val roomType: RoomType = RoomType.Room,
    /** Null while still in play. Set once, by whichever event put the player out. */
    val outBy: OutBy? = null,
    /**
     * The SystemIntegrity meter's segment total — display data that ARRIVES, never a value this
     * module computes (F-005, revision 19). The authority owns the denominator —
     * `(seats − insiders) × 7`, frozen at arming — and sends the panel this number the same way
     * it sends the role. The default is the ported design's 32, an artifact of an earlier
     * player count, correct only where no round exists: fixtures, previews, the screens guard.
     */
    val meterSegments: Int = PanelVals.METER_SEGMENTS,

    /** Randomises the backlog's *count and mix*, so inbox density can never imply a role. */
    val inboxSeed: Int = 3,
    val noteSeed: Int = 0,
)

/**
 * How the status bar reads — a property of where the round is, not of the individual screen.
 *
 * **The status bar is never absent.** Every screen in the game carries one, including the two
 * where the player has just been removed from the round. It is how anyone confirms the perimeter
 * is still armed and what the time is, and a device that stopped saying so would be the app
 * abandoning a player at the exact moment it took everything else away.
 *
 * [Inverted] is the one variation, and it is not an absence: the lantern and the scan fill the
 * panel with amber, so the shared bar — amber ink on black — would be invisible on them. Those
 * two draw the same row themselves in black on amber instead.
 */
enum class PanelMode {
    Pre, Live, Ghost, End,

    /** The screen paints a full amber field and draws its own status row, inverted. */
    Inverted,
}

/**
 * The design's `renderVals()`, ported whole.
 *
 * Kept as one derivation rather than scattered through the screens, for the reason the source
 * gives: the same rule has to hold on thirty screens at once, and a rule applied thirty times by
 * hand is a rule that holds twenty-nine times. Role-dependent values in particular live *here*,
 * where they can be read side by side and checked against one another.
 */
class PanelVals(val state: PanelState) {

    val screen: ScreenId get() = state.screen
    val insider: Boolean get() = state.role == PanelRole.Insider
    val resident: Boolean get() = !insider

    // ---- Status bar ---------------------------------------------------------------------

    val mode: PanelMode = when (state.screen) {
        // The only two screens that fill with amber, and therefore the only two that draw their
        // own inverted status row. Everything else uses the shared one.
        ScreenId.Lock, ScreenId.Scan -> PanelMode.Inverted

        ScreenId.Boot, ScreenId.Join, ScreenId.Maps, ScreenId.Editor, ScreenId.SaveName,
        ScreenId.HomeDetail, ScreenId.Delete, ScreenId.Secret, ScreenId.RoomEdit,
        ScreenId.MarkerSheet, ScreenId.ScanMarker, ScreenId.PassageWarn, ScreenId.TermTaken,
        ScreenId.TermRemove, ScreenId.Floors, ScreenId.NoTerminal, ScreenId.Perms,
        ScreenId.Lobby, ScreenId.WinResidents,
        -> PanelMode.Pre

        ScreenId.WinInsiders -> PanelMode.End
        ScreenId.Revoked, ScreenId.Restrained, ScreenId.Ghost3, ScreenId.GhostMeeting ->
            PanelMode.Ghost
        else -> PanelMode.Live
    }

    val isPre: Boolean get() = mode == PanelMode.Pre

    /**
     * Whether the *shared* status bar is drawn.
     *
     * False only for [PanelMode.Inverted], where the screen draws its own. It is never false
     * because a screen has no status row — see [PanelMode].
     */
    val statusVisible: Boolean get() = mode != PanelMode.Inverted

    /** True where the screen is responsible for drawing its own inverted status row. */
    val drawsOwnStatusRow: Boolean get() = mode == PanelMode.Inverted

    /**
     * The two screens whose whole job is to emit as little light as possible — **the status bar
     * included**.
     *
     * A bright bar above deliberately dim text is the same flare the screen exists to avoid, and
     * it is worse than a bright body, because chrome is the part nobody thinks to check.
     *
     * **Only these two.** The set briefly included every out-screen, and that was wrong: once you
     * are on the couch the room already knows you are out, so there is nothing left to conceal —
     * and a reconnecting phone needs to be *noticed*, not hidden.
     */
    private val concealed: Boolean = state.screen == ScreenId.Revoked || state.screen == ScreenId.Sub

    val ink: Color = when {
        isPre -> Amber.BoneInk
        concealed -> Amber.Dim
        else -> Amber.Bright
    }
    val dim: Color = when {
        isPre -> Amber.BoneDim
        concealed -> Amber.Faint
        else -> Amber.Dim
    }
    val edge: Color = if (isPre) Amber.BoneEdge else Amber.Edge

    /**
     * Once you are out, the carrier names **what happened to you** — not which screen you are on.
     *
     * `Revoke` and `Restrain` are not synonyms and must never be collapsed: one is system power
     * lent by the house, the other is a physical act the house cannot prevent. So the word follows
     * [PanelState.outBy], which is carried from the event that put you there.
     *
     * The design's fixture derives this from the current screen instead, which cannot express it —
     * a prototype has no memory of how a player left. `ghost3` and `ghostmeeting` are reached by
     * *both* routes, so screen-derived logic labels a revoked player RESTRAINED. Driving it from
     * the cause is what the shipped app needs and what the rule actually says.
     *
     * **This is public once you are out**, and that is deliberate: anyone near enough to read the
     * bar learns whether an Insider acted or the room did.
     */
    val carrier: String = when {
        state.screen == ScreenId.WinResidents -> "SOMEONE'S HOME"
        isPre -> ""
        // Blank, exactly as before arming. The carrier names the house you are attached to, and a
        // phone that has lost the host is not attached to one -- the same fact, so it reads the
        // same way rather than inventing a third word. Without this the bar claimed a healthy
        // link directly above a body saying the link was gone.
        state.screen == ScreenId.Disconnect -> ""
        mode == PanelMode.Ghost || state.screen == ScreenId.Ghost2 -> when (state.outBy) {
            OutBy.Revoked -> "REVOKED"
            OutBy.Restrained -> "RESTRAINED"
            // Missing cause: say neither. Picking one would be wrong about half the players,
            // and wrong in the specific way the vocabulary forbids -- naming a physical act by
            // the room as system power, or the reverse. UNREGISTERED asserts only that you are
            // out, which is already public by the time any of these screens is on.
            null -> "UNREGISTERED"
        }
        else -> "SOMEONE'S HOME"
    }

    /** Once the round starts the house owns the network: no bars on any live screen. */
    val signalOn: Color = if (isPre) Amber.BoneInk else Amber.Edge

    /**
     * The reception mark drops to the dead intensity while reconnecting.
     *
     * The lamp holds its last authorised state throughout — that is the rule, and it is why the
     * screen says so in words — but the *chrome* carries no such obligation, and a healthy-looking
     * link above a body announcing the link is gone is the screen contradicting itself.
     */
    val receptionInk: Color get() = if (state.screen == ScreenId.Disconnect) Amber.Edge else ink
    val signalOff: Color = when {
        isPre && state.screen != ScreenId.WinResidents -> Amber.BoneEdge
        isPre -> Amber.BoneInk
        else -> Amber.Edge
    }

    /**
     * The perimeter is armed for every in-game screen, **including a revoked phone**.
     *
     * A glyph rather than a word, so the fact is not repeated in text on thirty screens — and so
     * a revoked player's device says exactly what everyone else's device says.
     */
    val armedGlyph: Boolean =
        mode == PanelMode.Live || mode == PanelMode.Ghost || state.screen == ScreenId.WinInsiders

    /** The same iris, drained. An unarmed perimeter drawn like the dead bars, not a new symbol. */
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
     * a caught scan, and the round ending. Nothing the player *chose* to open does.
     */
    val buzzes: Boolean = state.screen in setOf(
        ScreenId.Armed, ScreenId.Notify, ScreenId.Banner, ScreenId.Call, ScreenId.Found,
        ScreenId.Assemble, ScreenId.Notice, ScreenId.Tally, ScreenId.Revoked, ScreenId.Restrained,
        ScreenId.ScanMarker, ScreenId.ScanCaught, ScreenId.ScanBad, ScreenId.ScanUnknown, ScreenId.GhostMeeting,
        ScreenId.WinInsiders, ScreenId.WinResidents,
    )

    // ---- Messages -------------------------------------------------------------------------

    /**
     * Leftovers that queued while the phone had no signal.
     *
     * The count *and* the mix are randomised per player, so inbox density can never imply a
     * role. A fixed backlog would make "how many unread" a channel, and one nobody would think
     * to look for.
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
    )

    val inbox: List<InboxRow> = run {
        val seed = state.inboxSeed
        val keep = 3 + (seed % 4)
        val rest = backlog.drop(seed % 3).take(keep).map { (from, at, preview) ->
            InboxRow(from, at, preview, Amber.Edge, Amber.Faint, Amber.Dim)
        }
        // Identical sender, time and preview for both roles: the thread must be OPENED to read.
        // The row itself cannot be a tell to anyone glancing at a neighbour's screen.
        listOf(
            InboxRow(
                "HOUSE", "21:02", "Regarding this evening. Please read.",
                Amber.Dim, Amber.Bright, Amber.Bright,
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
     * The Resident lines are plausible, unverifiable and deliberately **not context aware** — a
     * line referring to something that actually happened would be a private channel from the
     * authority to one player, which is the thing this app does not have.
     */
    private val residentNotes = listOf(
        "why was rose in the garage",
        "i have not seen marcus finish anything yet",
        "priya was upstairs when the lights went",
        "dani walked past the study twice",
        "somebody was already at the garage marker",
        "tomas has not been in the hall all night",
        "count was four. i only saw three of you",
        "rose scanned nothing for a long time",
    )

    val noteLine: String =
        if (insider) "i hope no one finds out"
        else residentNotes[state.noteSeed.mod(residentNotes.size)]

    // ---- The springboard's two tiles --------------------------------------------------------

    /**
     * **Baselined to the Insider's brightness for BOTH roles.**
     *
     * A dimmer page 2 for Residents would read across a room as *"this one has nothing to tap"*,
     * which is the tell the whole parity discipline exists to prevent. The Resident sees this
     * page lit exactly as an Insider does; nothing here answers them.
     */
    val tileBorder: Color = Amber.Dim
    val tileInk: Color = Amber.Bright
    val tier2Ink: Color = Amber.Mid

    val abilityName: String = if (insider) "REVOKE" else "POWER"

    /**
     * Identical in the armed and cooling states, differing only in the resting verb — TAP TO ARM
     * against TAP TO TEST. Both are true sentences about the button in front of you, and neither
     * confirms anything.
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
    val revokeBar: Float = if (state.revoke == RevokeState.Cooldown) 0.38f else 1f
    val revokeBarInk: Color = if (state.revoke == RevokeState.Cooldown) Amber.Dim else Amber.Bright

    val secondName: String = if (insider) "EGRESS" else "SUBSYS"
    val secondSub: String =
        if (insider) "SHARED WITH THE OTHER INSIDER" else "ALL SYSTEMS RESPONDING"
    val secondBar: Float = 0.64f
    val secondBarInk: Color = Amber.Dim

    val tier2A: String = if (insider) "SURGE" else "BUS"
    val tier2B: String = if (insider) "SPOOF" else "THERMAL"
    val tier2C: String = if (insider) "ISOLATE" else "CACHE"
    val tier2Bar: Float = 0.82f
    val tier2BarInk: Color = Amber.Mid
    val tier2Note: String =
        if (insider) "SURGE, SPOOF AND ISOLATE SHARE THIS" else "BUS, THERMAL AND CACHE NOMINAL"

    // ---- Host setup -------------------------------------------------------------------------

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
    val markerChipInk: Color = if (state.markersOn) Amber.BoneInk else Amber.BoneFaint
    val markerChipFill: Color = if (state.markersOn) Amber.BoneChip else Amber.BoneChipOff
    val markerChipEdge: Color = if (state.markersOn) Amber.BoneInk else Amber.BonePutty

    val editorHint: String = if (state.markersOn) {
        "TAP ANYWHERE TO DROP A MARKER.\nTAP A MARKER TO RENAME OR REMOVE IT."
    } else {
        "DRAG TWO CORNERS TO ADD A ROOM.\nMARKERS IGNORE YOUR TAPS WHILE OFF."
    }

    /** Room-type chips. Selection is inverted-dark on bone, the light field's emphasis. */
    fun typeEdge(t: RoomType): Color = if (state.roomType == t) Amber.BoneInk else Amber.BonePale
    fun typeFill(t: RoomType): Color = if (state.roomType == t) Amber.BoneInk else Color.Transparent
    fun typeInk(t: RoomType): Color = if (state.roomType == t) Amber.Bone else Amber.BoneDim

    val torchLabel: String = if (state.torch) "TORCH ON" else "TORCH OFF"
    val torchFill: Color = if (state.torch) Amber.SlateFill else Color.Transparent
    val torchInk: Color = if (state.torch) Amber.BoneInk else Amber.SlateFill
    val torchWash: Color = if (state.torch) Amber.TorchWash else Color.Transparent

    /**
     * The one Subroutine the player is being sent to, shared by every screen that mentions it.
     *
     * Held in one place because it appears on three: the springboard names it, the work order
     * highlights its row, and the scan confirms it on arrival. Written separately they drifted —
     * two screens showed a triangle and the third a ring, for the same marker, because the third
     * was reading the host-setup fixture by mistake.
     *
     * **[name] and [instruction] are different things and both are kept.** `SNIFF` is the kind of
     * Subroutine; *purge the media cache* is what this instance of it asks for. The work order
     * lists kinds, and the scan tells you the specific task once you are standing at the marker.
     */
    val current: CurrentSubroutine = CurrentSubroutine(
        name = "SNIFF",
        instruction = "PURGE THE\nMEDIA CACHE",
        room = "GARAGE",
        marker = MarkerShapes["triangle_up"],
        index = 4,
        total = 7,
        done = 3,
    )

    /**
     * The markers registered in the room the host is holding, and the shape most recently added.
     *
     * Fixture data. The real list comes from the authority, and the *shapes* come from
     * [MarkerShapes] — a marker's shape is its whole name here, so this is the one place the
     * design's own 24-shape sample must not be used.
     */
    val roomMarkers: List<MarkerShape> =
        listOfNotNull(MarkerShapes["triangle_up"], MarkerShapes["ring"])
    val lastRegistered: MarkerShape? = MarkerShapes["ring"]

    // ---- Meeting ------------------------------------------------------------------------------

    /**
     * Being out is an information privilege: someone outside the system sees who voted for whom,
     * while the living only ever get a count.
     *
     * Safe only because of the sequencing — by the time this is visible the room already knows
     * who is out, so there is never a window where someone outside knows something the living
     * do not.
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

    // ---- Meters ---------------------------------------------------------------------------

    /** System Integrity, frozen between meetings. 28 of 32. */
    val integrityLit: Int = 28

    /** The Egress countdown, which replaces the meter in place and takes the only number back. */
    val egressLit: Int = 22

    /** Both bars, seen only from outside the system. */
    val outsideLit: Int = 21

    /** 24 seconds left of a 60-second vote window. */
    val meetingLit: Int = 12

    /** The meter total in force — [PanelState.meterSegments], never the fixture constant. */
    val meterSegments: Int get() = state.meterSegments

    companion object {
        /**
         * **A fixture default, not a game value** — the ported design drew 32 segments, an
         * artifact of an earlier player count (F-005). In play the total arrives in
         * [PanelState.meterSegments] from the authority. Nothing that renders a live round may
         * read this constant.
         */
        const val METER_SEGMENTS = 32

        /** The vote clock is its own bar: 30 segments, not the meter's 32. */
        const val VOTE_SEGMENTS = 30

        /**
         * The scan's own countdown, and it is a safety device rather than a progress bar.
         *
         * Seven seconds, then the light dies and the phone goes back to where it was: nobody
         * should be standing in a dark room holding a lit screen at a wall by accident.
         */
        const val SCAN_SEGMENTS = 20
        const val SCAN_LIT = 12
    }
}

/**
 * The bungalow's ground floor — six rooms on a ten-by-twelve grid.
 *
 * **One source of truth for every screen that draws a plan.** The editor, the live map, the
 * timelapse and the outside view all read these rects, so a room cannot sit in one place on the
 * editor and another on the map. Two grids that agree by coincidence stop agreeing the first
 * time one is edited, and a map that disagreed with the house the host walked would be a bug
 * nobody could see from inside the game.
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

    /** The room the reader is standing in — the only anchor a count-based map can offer. */
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

    /**
     * **Every room carries a number, always.** Zero and no-reading are the same fact — the system
     * cannot tell "nobody was in the study" from "nothing reported the study", and it must not
     * imply that it can. A blank room would read as the second, so there are no blanks.
     *
     * The timelapse fixture originally omitted STUDY, which drew exactly that distinction by
     * accident.
     */
    val timelapseCounts = counts(
        mapOf(
            "KITCHEN" to (3 to Amber.Dim),
            "LIVING" to (1 to Amber.Dim),
            "HALL" to (2 to Amber.Dim),
            "STUDY" to (0 to Amber.Dim),
            "GARAGE" to (0 to Amber.Dim),
        )
    )

    /** True occupancy: live, no injected error, no staleness bands. Still counts, never names. */
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
 * The Subroutine a player is currently sent to.
 *
 * A fixture today. In play it arrives at the effect boundary like everything else.
 */
data class CurrentSubroutine(
    /** The kind of Subroutine, as the work order lists it. */
    val name: String,
    /** What this instance asks for, shown once you are at the marker. */
    val instruction: String,
    val room: String,
    val marker: MarkerShape?,
    val index: Int,
    val total: Int,
    val done: Int,
)

/** One cell of the plan editor's grid. */
data class EditorCell(val border: Color, val fill: Color?, val hatch: Boolean = false)

/**
 * One cell of a live plan. Borders are per-side, because an outline exists only where a cell's
 * neighbour belongs to a different room.
 *
 * **Adjacency falls out of cell neighbours**, which is why the map is a grid rather than free
 * rectangles: no geometry, no overlap rules, no snapping, and L-shaped rooms work for free
 * because real houses have them.
 */
data class MapCell(
    val top: Color?,
    val end: Color?,
    val bottom: Color?,
    val start: Color?,
    val fill: Color?,
)

/**
 * The editor grid, with one room optionally held as the current selection.
 *
 * **The held room goes DARKER, and that is correct.** The design carries a comment at the call
 * site describing the opposite — selection keeps the accent while every other room drops to
 * neutral putty — which was an earlier intention that the code never implemented. Confirmed as
 * the code, not the comment. Do not "fix" this to match a comment that is not in this repo.
 */
fun Plan.editorCells(focus: PlanRoom? = null): List<EditorCell> =
    List(ROWS * COLS) { i ->
        val room = roomAt(i / COLS, i % COLS)
        val held = focus != null && room != null && room.name == focus.name
        when {
            held -> EditorCell(Amber.SlateFocus, Amber.SlateFocusFill)
            room == null -> EditorCell(Amber.BonePutty, null)
            // Slate underneath, pale stripes on top: the source gradient is 3 units of
            // BoneHatch against 1 of Slate, so the SLATE is the gap colour. Leaving the
            // fill null let the bone ground show through and the stairs went pale.
            room.transit -> EditorCell(Amber.Slate, Amber.Slate, hatch = true)
            else -> EditorCell(Amber.Slate, Amber.SlateFill)
        }
    }

/** The live grid. Reads the same rects as the editor, deliberately. */
fun Plan.mapCells(): List<MapCell> =
    List(ROWS * COLS) { i ->
        val row = i / COLS
        val col = i % COLS
        val room = roomAt(row, col)
        val mine = room != null && room.name == HERE

        fun edgeTo(other: PlanRoom?): Color? =
            if (other !== room) {
                if (mine) Amber.Bright else if (room != null) Amber.Dim else Amber.Deep
            } else null

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

/**
 * The parity-check grid: 36 cells, seven corrupt, four of them found so far.
 *
 * A found cell inverts to full amber; a corrupt one sits at edge intensity; the rest are wells.
 */
object Parity {
    private val corrupt = setOf(2, 7, 11, 16, 19, 24, 33)
    private val found = setOf(2, 11, 19, 33)

    data class Cell(val fill: Color, val border: Color)

    val cells: List<Cell> = List(36) { i ->
        Cell(
            fill = when {
                i in found -> Amber.Bright
                i in corrupt -> Amber.Edge
                else -> Amber.Well
            },
            border = if (i in found) Amber.Bright else Amber.Faint,
        )
    }
}
