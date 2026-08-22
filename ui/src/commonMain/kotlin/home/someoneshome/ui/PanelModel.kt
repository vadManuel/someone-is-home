package home.someoneshome.ui

import home.someoneshome.model.HomeReview
import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.RoomKind

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

    // Host setup — once per house, in the light. The two reserved cards each have the same pair
    // of screens, because each raises the same two questions: this home already has one, and
    // are you sure you want it to have none.
    Maps, Editor, RoomEdit, StairsWarn, MarkerSheet, ScanMarker,
    TermTaken, TermRemove, MeetTaken, MeetRemove,
    // Was `NoTerminal`, when a missing terminal was the only thing that could hold a home back.
    // D-127 made the gate three requirements, and a screen named after one of them would be a
    // screen that tells a host about the terminal and says nothing about the other two.
    ReviewNeeds, Floors, SaveName, HomeDetail, Delete, Lobby,

    // Arming — the host turns the lights off and the house does the rest.
    Secret, Armed, Notify, Reveal, RevealThread,

    // The springboard — identical for both roles.
    //
    // `Quiet` and `LockNotify` are the last two the same way `Notify` and `Banner` are: a screen
    // with a notification on top of it. They are separate ids because the two things a
    // notification does — dim or not, banner or under the clock — are properties of a *drawn
    // panel*, and every sweep in this module walks `ScreenId.entries`. A quiet banner hiding
    // behind a flag on `Notify` would be a presentation none of them ever looked at.
    Home, Page2, Lock, Quiet, LockNotify,

    // Work. The Subroutine screens are named for the Subroutine and not for how much light it
    // makes: the port's two were `Sub` and `SubBright`, which is the light ladder used as an
    // identity, and it stops describing anything the moment a third Subroutine is built. One id
    // per Subroutine is also what keeps the sweeps honest — every guard in this module walks
    // `ScreenId.entries`, so a Subroutine hiding behind a field on a shared screen would be a
    // Subroutine none of them ever looked at.
    Work, Scan, ScanCaught, ScanBad, ScanUnknown,
    SubHandshake, SubReplay, SubParity, SubShort, SubTrace, SubJam,
    Files, Notes, TermNo, TermLive, Timelapse,

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
 * **The house's answer to an entry this phone handed over (D-109).**
 *
 * Two cases and no third, because the effect that carries it has one boolean and no room for one.
 * There is no `Pending` member: *waiting* is the absence of a verdict, which is what a nullable
 * field already says, and a third enum case would be a state the device could reach on its own.
 *
 * **Byte-identical for both roles, on the same schedule.** The house grades every entry for real —
 * an Insider's fake is real work, graded honestly, counting for nothing — so nothing here is
 * generated, delayed or suppressed for one role. That is not a promise this file can keep on its
 * own; it is kept by there being no role in the file, and checked in `SubroutineParityTest`.
 */
enum class SubroutineVerdict { Accepted, Rejected }

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
    /** Host-side only: the torch while registering markers, in the light, with the back camera. */
    val torch: Boolean = false,
    /** Null while still in play. Set once, by whichever event put the player out. */
    val outBy: OutBy? = null,
    /**
     * **What the house said about the entry this phone handed over. Null means it has not said.**
     *
     * A pushed fact, like every other field here: the device never forms one, because nothing on
     * it can — the entry types hold the player's own input and have no field for the answer to sit
     * in. `RETURNED . WAITING` is what a null renders as, and *waiting* is the honest word.
     *
     * **Cleared by walking anywhere**, including by scanning the marker again ([PanelState.arrivingAt]).
     * A verdict is about one instance of one Subroutine, and one left standing across a
     * navigation would put the last one's answer under the next one's work.
     */
    val verdict: SubroutineVerdict? = null,
    /**
     * **The clock on this screen, as the house last said it stood.**
     *
     * Seconds remaining, and nothing else — the window it is counting down is a property of the
     * screen (see [Countdowns]), so the two cannot be sent inconsistently. Null means the house
     * has said nothing, which on a phone with no house attached is always.
     *
     * **The device never advances it.** No screen in this module runs a clock; a countdown is
     * redrawn when a new value arrives and is otherwise as still as any other field here. Six
     * phones each counting down on their own would be six meetings ending at six different
     * moments, and the one thing every player in a dark house needs is to be looking at the same
     * clock.
     */
    val secondsLeft: Int? = null,
    /**
     * **Which Egress the house started, and where it has to be contained — as the house said.**
     *
     * Two pushed facts, like every other field here. The house draws two ordinary markers in
     * non-adjacent rooms at fire time and a Beacon/Tether label to go with them; the device holds
     * no opinion about either, and there is nothing on the phone that could form one — the pair is
     * a property of the home the round was armed in.
     *
     * **Null means the house has said nothing**, which on a phone with no house attached is always;
     * the port's drawn pair stands in. Exactly [secondsLeft]'s arrangement, and for the same
     * reason: the fallback is a fixture, not a second source of truth.
     *
     * **One field feeds both surfaces.** The heavy alert and the widget it leaves behind after a
     * swipe read the same two strings, so they cannot send two people who may not speak to two
     * different places.
     */
    val egressType: String? = null,
    val egressNodes: List<String>? = null,
    /**
     * **Who was working for the house, and what was held over them — as the house published it.**
     *
     * The only field on this class that carries an alignment, and it is null on every screen but
     * the two endings. It arrives once, at the end of the round, on the one push the house ever
     * sends that names anybody (`gdd.md:1063`); before that the app has spent an evening being
     * physically incapable of saying it, and a phone that held this early would be holding the
     * answer to the only question in the game.
     *
     * **Null means the house has said nothing**, which on a phone with no house attached is
     * always; the port's drawn pair stands in. Exactly [egressNodes]' arrangement.
     *
     * ### A published line on this class is not the thing D-116 protects
     *
     * `OneLine` lives beside this class rather than in it precisely because a line typed in the
     * lobby would otherwise ride into every capture of every round. This is the other side of that
     * rule and not an exception to it: **the lines here have already been read out to the entire
     * room**, by the house, as the round's last act. What the promise protects is the line of
     * somebody the house never used — and a Resident's line is never in this list, because it is
     * never in the effect that fills it.
     *
     * **Percentages, not totals, apply here too.** Nothing about the reveal changes D-103: see
     * [PanelVals.integrityPercent], which is what both ending screens draw.
     */
    val revealed: List<RevealedInsider>? = null,
    /** Randomises the backlog's *count and mix*, so inbox density can never imply a role. */
    val inboxSeed: Int = 3,
    val noteSeed: Int = 0,
)

/**
 * **One named Insider and their one line, on the ending screen.**
 *
 * Flat and inert, like everything on [PanelState]: two strings the house sent, already resolved to
 * a name. Seat-to-name resolution belongs to the transport, which is the only layer holding the
 * lobby's roster — a `ui` that mapped seats to names would be `ui` keeping a second copy of who is
 * in the house.
 */
data class RevealedInsider(val name: String, val line: String)

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
        // The only screens that fill with amber, and therefore the only ones that draw their
        // own inverted status row. Everything else uses the shared one. `LockNotify` is the lock
        // screen with something arriving on it, so it is the lantern too -- darkened, but still
        // the lantern, and still drawing its own row.
        ScreenId.Lock, ScreenId.LockNotify, ScreenId.Scan -> PanelMode.Inverted

        ScreenId.Boot, ScreenId.Join, ScreenId.Maps, ScreenId.Editor, ScreenId.SaveName,
        ScreenId.HomeDetail, ScreenId.Delete, ScreenId.Secret, ScreenId.RoomEdit,
        ScreenId.MarkerSheet, ScreenId.ScanMarker, ScreenId.StairsWarn, ScreenId.TermTaken,
        ScreenId.TermRemove, ScreenId.MeetTaken, ScreenId.MeetRemove,
        ScreenId.Floors, ScreenId.ReviewNeeds, ScreenId.Perms,
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
     *
     * **Asked of the roster rather than named.** It used to name `Sub` — the one dark Subroutine
     * screen the port had — so a second dark Subroutine would have shipped with a bright status
     * bar above a screen built to emit nothing, and the fault would live in a file nobody edits
     * when adding a Subroutine. The question *is* the light signature, so it is asked of the
     * light signature.
     */
    private val concealed: Boolean = state.screen == ScreenId.Revoked ||
        Subroutine.on(state.screen)?.light == LightSignature.Dark

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
     * **The carrier says one thing and only one: what happened to you, once something has.**
     *
     * It used to carry the game's name on every in-play screen, which is what a phone's network
     * name looks like — and that is exactly why it went. A word sitting in the status bar for the
     * whole evening is a word nobody reads by the second round, so the one moment the slot has
     * something to say was arriving in a place the eye had already learned to skip. Blank until
     * there is a fact, and the fact appears against nothing.
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
        // Blank before arming, blank in play, and blank on a phone that has lost the host. The
        // last of those was a rule of its own once, back when everything else in play said
        // SOMEONE'S HOME and a disconnected phone claiming a healthy link contradicted the body
        // of its own screen. Now it is simply the ordinary case.
        isPre || state.screen == ScreenId.Disconnect -> ""
        mode == PanelMode.Ghost || state.screen == ScreenId.Ghost2 -> when (state.outBy) {
            OutBy.Revoked -> "REVOKED"
            OutBy.Restrained -> "RESTRAINED"
            // Missing cause: say neither. Picking one would be wrong about half the players,
            // and wrong in the specific way the vocabulary forbids -- naming a physical act by
            // the room as system power, or the reverse. UNREGISTERED asserts only that you are
            // out, which is already public by the time any of these screens is on.
            null -> "UNREGISTERED"
        }
        else -> ""
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
    val buzzes: Boolean get() = state.screen in BUZZING

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
        //
        // THE PREVIEW IS THE BANNER'S OWN WORDS, not a second copy of them. These rows are where
        // the two texts SURVIVE their notification -- both kinds name Messages as what holds them
        // -- so the two saying different things would make that claim false while both looked
        // right.
        //
        // BOTH HOUSE ROWS ARE DRAWN THE SAME. The older one dimmed would be indistinguishable
        // from a read mark, which is the one thing D-105 removes from every surface in the game:
        // the eye reads "bright above, faint below" as attended-to, and nobody would ever call it
        // a feature, which is exactly why it would survive review.
        listOf(
            InboxRow(
                Notifications.opening.from, "21:02", Notifications.opening.body,
                Amber.Dim, Amber.Bright, Amber.Bright,
            ),
            InboxRow(
                Notifications.text.from, Notifications.text.at, Notifications.text.body,
                Amber.Dim, Amber.Bright, Amber.Bright,
            ),
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
     * Identical in the armed and cooling states, differing only in the resting line.
     *
     * **Neither of the two says TAP any more** (D-141, D-142). The Insider's is an instruction and
     * the instruction changed — arming is a two-second hold — and a false instruction on this
     * control costs a whole cooldown with no cancel. The Resident's could not stay TAP TO TEST at
     * all: it invited a press on a tile that now takes no pointer input whatever, and a line
     * asking for a gesture that produces nothing is the one kind of copy a dark house punishes.
     *
     * D-142 asks that the Resident's line be *true, and not shorter, dimmer or otherwise
     * distinguishable*. Same brightness, same slot, same nineteen characters, and it says nothing
     * about what the tile does when touched — because for that player it does nothing, and a
     * sentence about a control is exactly what a Resident is not entitled to.
     */
    val abilitySub: String = when (state.revoke) {
        RevokeState.Armed -> "ARMED . TOUCH THEIR PHONE"
        RevokeState.Cooldown -> "COOLING DOWN"
        RevokeState.Ready -> if (insider) "READY . HOLD TO ARM" else "READY . ALL NOMINAL"
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

    /**
     * The REVIEW HOME button, saying what the home is still short of rather than failing later.
     *
     * **Asked of the map rather than of a flag** (D-127). What this home has registered is a fact
     * about what the host scanned, and it lives in [HomeEditorModel] with the rest of the plan; a
     * second copy of it on [PanelState] is a second copy that would one day disagree with the home
     * it describes.
     *
     * The label names **how many** things are outstanding rather than which, because there can be
     * three and a button is one line. The screen behind it names them all. The button still
     * *works* when it is short — it goes to that screen — because a control that goes quiet
     * teaches nothing about why.
     */
    fun saveLabel(review: HomeReview): String = when (review.missing.size) {
        0 -> "REVIEW HOME"
        1 -> "REVIEW HOME . 1 THING MISSING"
        else -> "REVIEW HOME . ${review.missing.size} THINGS MISSING"
    }

    fun saveEdge(review: HomeReview): Color =
        if (review.passes) Amber.Slate else Amber.SlateDead

    fun saveFill(review: HomeReview): Color =
        if (review.passes) Amber.SlateFill else Color.Transparent

    fun saveInk(review: HomeReview): Color =
        if (review.passes) Amber.SlateInk else Amber.SlateDead

    /**
     * **HOSTS UP TO N** — capacity as guidance, never as a gate (D-127).
     *
     * Drawn wherever a host is looking at a whole home: at REVIEW, on the save screen, and on the
     * home's own screen. `N = markers − 3`, the three being the Array Wipe circuit's stations
     * (D-122), and a home short of the floor says so instead of advertising a number it cannot
     * honour yet.
     */
    fun capacityLine(review: HomeReview): String =
        if (review.hosts == 0) "HOSTS UP TO NOBODY YET"
        else "HOSTS UP TO ${review.hosts}"

    /**
     * **The lobby's guidance line, or none — and it never blocks anything** (D-127).
     *
     * Null when the party fits, which is the common case and draws nothing. When more people have
     * joined than the home has capacity for, the host is told what that means rather than told no:
     * a card is a place and not a container (D-123), so the round is armed either way and some
     * markers are simply visited twice. **The honest thing to tell a host with nine markers and
     * ten players is that it will be crowded, not that it is forbidden.**
     *
     * D-125 is why this is a line and not a clamp: how many people are standing in the hall is a
     * thing everybody there can see, and clamping a visible fact is condescension.
     */
    fun crowdedLine(joined: Int, review: HomeReview): String? =
        if (joined <= review.hosts) null
        else "$joined HERE, AND THIS HOME IS SIZED FOR ${review.hosts}. " +
            "IT WILL BE CROWDED — SOME MARKERS GET VISITED TWICE."

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

    /**
     * Room-type chips. Selection is inverted-dark on bone, the light field's emphasis.
     *
     * The held room's own [RoomKind] decides which chip is lit — the plan is the only place a
     * room's type is written down, and a chip reading from anywhere else is a chip that can be
     * lit for a room that is not that type.
     */
    fun typeEdge(held: RoomKind, t: RoomKind): Color =
        if (held == t) Amber.BoneInk else Amber.BonePale

    fun typeFill(held: RoomKind, t: RoomKind): Color =
        if (held == t) Amber.BoneInk else Color.Transparent

    fun typeInk(held: RoomKind, t: RoomKind): Color = if (held == t) Amber.Bone else Amber.BoneDim

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
     * **[CurrentSubroutine.subroutine] and [CurrentSubroutine.instruction] are different things
     * and both are kept.** `HANDSHAKE` is the kind of Subroutine; *resync the keepalive* is what
     * this instance of it asks for. The work order lists kinds, and the scan tells you the
     * specific work once you are standing at the marker.
     */
    val current: CurrentSubroutine get() = CURRENT

    // ---- Clocks -------------------------------------------------------------------------------

    /**
     * The countdown this screen is showing, or [Countdown.NONE] where it has none.
     *
     * **Display, not a clock.** The value comes from [PanelState.secondsLeft] when the house has
     * sent one and from the design's drawn moment when it has not; nothing on the device advances
     * it either way. The window it counts down is [Countdowns]', which reads it off the
     * auto-advance that fires when the clock runs out — so the number on screen and the moment
     * the phone moves cannot drift apart.
     */
    val countdown: Countdown = Countdowns.on(state.screen, state.secondsLeft) ?: Countdown.NONE

    // ---- Meters ---------------------------------------------------------------------------

    /**
     * System Integrity, frozen between meetings: 28 of [METER_SEGMENTS] cells lit, which is the
     * design's 88% drawn.
     *
     * **A bar position, not a score.** The cells are display resolution (D-103, revision 21) and
     * the fraction they stand for is the only form the meter reaches a panel in.
     */
    val integrityLit: Int = when (state.screen) {
        // **The two endings carry the round's final reading**, and the port's figures are the
        // design's own: the Residents took it by clearing the meter, and the Insiders took it with
        // this much still standing. Screen-dependent rather than pushed because nothing pushes the
        // meter yet — the springboard's bar is a fixture too — and a `PanelState.integrity` added
        // here would be a field with no sender, which is the shape this class already refuses.
        ScreenId.WinResidents -> 0
        ScreenId.WinInsiders -> 14
        else -> 28
    }

    /**
     * **The meter as a percentage, and the only form it may ever be drawn in** (D-103, D-153).
     *
     * Computed from [integrityLit] against [METER_SEGMENTS] so the bar and the number cannot
     * disagree — two hand-maintained facts about one meter is how `SYSTEM INTEGRITY 14 / 32` came
     * to sit under a bar drawn at a different fraction, with a denominator that was a fossil of a
     * count F-005 had already corrected.
     *
     * **The endings are not the safe place for a real number, and that is D-153's whole argument.**
     * The round is over, nothing can be acted on, and it is still not safe: the app is played two
     * to three rounds in an evening (D-157), so a denominator printed at the end of round one is a
     * denominator carried into round two — where it divides out the Insider count for every round
     * that follows. One ending screen would retroactively unhide a number D-103 spent a whole
     * revision hiding.
     */
    val integrityPercent: String = "${integrityLit * 100 / METER_SEGMENTS}%"

    /** The Egress countdown, which replaces the meter in place and takes the only number back. */
    val egressLit: Int = 22

    /**
     * **The Egress's type and its two nodes, from the house when there is one.**
     *
     * The port's drawn pair when there is not — [PanelState.secondsLeft]'s arrangement exactly.
     * Derived here, in the one place role- and round-dependent values are read side by side, so
     * that the alert and the widget resolve the *same* fallback rather than each keeping its own.
     */
    val egressType: String = state.egressType ?: Notifications.EGRESS_TYPE
    val egressNodes: List<String> = state.egressNodes ?: Notifications.EGRESS_NODES

    /**
     * **What is standing over this screen, with the Egress alert naming this round's rooms.**
     *
     * The single door onto [Notifications.arrivals] for everything that draws one. A composable
     * reaching into that table directly would get the port's fixture pair, and the day the house
     * drew a different one the banner and the widget would disagree — silently, in the dark, to two
     * people who cannot speak to each other.
     */
    val arrival: Arrival? = Notifications.arrivalOn(state.screen, egressType, egressNodes)

    /**
     * **THE INSIDERS WERE, with the blackmail published beside each name** — from the house when
     * there is one, and the port's pair when there is not.
     *
     * [PanelState.revealed]'s arrangement exactly, for [egressNodes]' reason: derived once, here,
     * so that the two ending screens resolve the *same* list rather than each keeping its own. The
     * Residents' ending and the Insiders' ending name the same people, and a fixture reached
     * directly by one of them would agree with itself and with nothing else.
     */
    val revealed: List<RevealedInsider> = state.revealed ?: REVEALED

    /**
     * **The house's last words, and it says them only to the people it owned** (`gdd.md:1051`).
     *
     * Which of the two depends on who won, and *who won* is what the screen is: there is one
     * ending screen per winner. Drawn on both of them, behind [insider], because the house has
     * something to say either way and its tone does not improve when it loses.
     *
     * Null on a Resident's phone, which is what the message card branches on. Not a secret by the
     * time it is drawn — [revealed] is on the same screen — but it is not theirs to read either.
     */
    val signOff: String? = when {
        !insider -> null
        state.screen == ScreenId.WinInsiders -> "Thank you for your cooperation."
        state.screen == ScreenId.WinResidents -> "Unfortunate."
        else -> null
    }

    /** Both bars, seen only from outside the system. */
    val outsideLit: Int = 21

    companion object {

        /**
         * **The Subroutine this phone is being sent to — one value, read by four screens and by
         * the actions layer.**
         *
         * A `companion` constant rather than a per-instance field because `FlowModel` needs it
         * too: BEGIN on the caught-scan screen opens whichever Subroutine the scanned marker
         * holds, and that is a navigation decision the actions layer takes. Two copies of the
         * fixture — one for the screens and one for the router — would send a player to a screen
         * for a Subroutine other than the one they are looking at, which is the exact fault the
         * shared fixture was introduced to stop.
         *
         * **HANDSHAKE, and not the SNIFF that used to be here.** The port's fixture named SNIFF
         * while carrying the instruction *purge the media cache*, and the roster's Sniff is haptic
         * counting — a name and a piece of work that were never the same Subroutine. Handshake is
         * the design's own *build this one first*, keeps the DARK signature the four surfaces
         * already show, and is one of the three with an interaction behind it, so BEGIN now
         * reaches the screen it names.
         */
        val CURRENT: CurrentSubroutine = CurrentSubroutine(
            subroutine = Subroutine.Handshake,
            instruction = "RESYNC THE\nKEEPALIVE",
            room = "GARAGE",
            marker = MarkerShapes["triangle_up"],
            index = 4,
            total = 7,
            done = 3,
        )

        /**
         * The screens that arrive unasked, and therefore buzz.
         *
         * **A constant, not a literal rebuilt per screen.** It used to be a `setOf(...)` inside
         * [buzzes], so every construction of a `PanelVals` — every recomposition of every screen —
         * allocated a seventeen-element set to ask one membership question. The whole app has an
         * allocation budget of about 0.5 MB/s, and the lamp has to die in the same frame as phone
         * contact; a per-frame allocation to answer a constant is the wrong direction on both.
         *
         * It is also the one place the design's buzzing set is written down, which is what lets
         * [Flow.houseDriving] be derived from it rather than kept in step with it by hand.
         *
         * ### `Quiet` is not in here, and that is D-118 applied to the hand rather than the eye
         *
         * A quiet notification is *"no dim, no brightness spike, nothing world-observable at
         * all"* — and in a house where nobody may speak, a buzz is about as world-observable as a
         * phone gets. Six pockets buzzing at once is the same broadcast the dim would have been,
         * arriving through a different sense. So the two heavy kinds buzz on every phone, on the
         * banner (`Notify`, `Banner`) and under the clock (`LockNotify`) alike, and a quiet one
         * waits to be found.
         */
        val BUZZING: Set<ScreenId> = setOf(
            ScreenId.Armed, ScreenId.Notify, ScreenId.Banner, ScreenId.LockNotify,
            ScreenId.Call, ScreenId.Found,
            ScreenId.Assemble, ScreenId.Notice, ScreenId.Tally, ScreenId.Revoked,
            ScreenId.Restrained, ScreenId.ScanMarker, ScreenId.ScanCaught, ScreenId.ScanBad,
            ScreenId.ScanUnknown, ScreenId.GhostMeeting, ScreenId.WinInsiders,
            ScreenId.WinResidents,
        )

        /**
         * The meter bar's DISPLAY RESOLUTION — how many cells the percentage is quantised into,
         * a fact about pixels and nothing else (D-103, revision 21). SystemIntegrity reaches a
         * panel only as a percentage: the denominator `(seats − insiders) × 7` would disclose
         * the Insider count by division, and under D-103 that count can be hidden. No screen may
         * ever print an absolute meter total; MeterDisclosureTest reads the panels to prove it.
         */
        const val METER_SEGMENTS = 32

        /** The vote clock is its own bar: 30 segments, not the meter's 32. */
        const val VOTE_SEGMENTS = 30

        /**
         * The scan's own countdown, and it is a safety device rather than a progress bar.
         *
         * Ten seconds (20 segments, two per second — the design lengthened it from an
         * initial seven), then the light dies and the phone goes back to where it was: nobody
         * should be standing in a dark room holding a lit screen at a wall by accident.
         *
         * **How many of those segments are lit is not a constant here.** It used to be — a `12`
         * sitting beside a hardcoded `6S LEFT`, two numbers for one fact, agreeing by hand. Both
         * now come off [PanelVals.countdown].
         */
        const val SCAN_SEGMENTS = 20

        /**
         * **The port's reveal: the design's two Insiders, with what the house had on them.**
         *
         * A fixture, for [CURRENT]'s reason — a screen rendered with an empty list is a screen the
         * app never shows, and a test looking at one proves nothing about the screen that ships.
         * In play the house sends the real thing and this is not consulted.
         *
         * **The lines are the point and they are deliberately small.** *The petty, mundane thing
         * each Insider was coerced with* is the design's own phrasing, and a fixture holding
         * anything lurid would quietly teach every future writer of this screen that the game is
         * about secrets rather than about how little it takes.
         */
        val REVEALED: List<RevealedInsider> = listOf(
            RevealedInsider("DANI", "I still have your spare key."),
            RevealedInsider("TOMAS", "You never cancelled the second account."),
        )
    }
}

/**
 * The bungalow's ground floor — six rooms on a ten-by-twelve grid.
 *
 * **One source of truth for every screen that draws a plan.** The editor, the live map, the
 * timelapse and the outside view all read these rects, so a room cannot sit in one place on the
 * editor and another on the map. Two grids that agree by coincidence stop agreeing the first
 * time one is edited, and a map that disagreed with the house the host walked would be a bug
 * nobody could see from inside the game. The editor keeps that promise by *converting* these
 * into strokes — [HomeEditorModel.bungalow] — rather than by typing the same rooms out again.
 *
 * **A rect on the live map, not a painted room.** `model.PlanRect` is the painted one: a `Room`
 * and the strokes it is the union of, which is what an L-shaped kitchen needs and what a single
 * rect cannot express. This is the fixture the in-round screens draw, where every room happens
 * to be one rectangle.
 */
data class PlanRect(
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
        PlanRect("KITCHEN", 0, 2, 0, 3),
        PlanRect("LIVING", 0, 2, 5, 9),
        PlanRect("HALL", 4, 5, 0, 5),
        PlanRect("STAIRS", 4, 5, 6, 9, transit = true),
        PlanRect("STUDY", 7, 10, 0, 3),
        PlanRect("GARAGE", 7, 9, 5, 9),
    )

    /** The room the reader is standing in — the only anchor a count-based map can offer. */
    const val HERE = "GARAGE"

    fun roomAt(row: Int, col: Int): PlanRect? =
        rooms.firstOrNull { row >= it.r0 && row <= it.r1 && col >= it.c0 && col <= it.c1 }

    /**
     * Counts per room, never dots.
     *
     * A dot implies a trackable individual and no such thing exists in this system. You learn
     * that four people were in the living room, never which four — and a numeral says exactly
     * that and nothing more.
     */
    data class RoomCount(val room: PlanRect, val count: Int, val ink: Color)

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
 * **How much light a Subroutine will make this phone emit — known before you walk to it (D-106).**
 *
 * The screen is the lamp, so this is a risk axis independent of duration: a bright Subroutine
 * makes you a beacon for its whole length, a dark one hides you and blinds you to the room, and a
 * six-second bright one and a thirty-second dark one are different decisions rather than two
 * points on "how long". D-106 settles that a player holds this knowledge *in advance* — on the
 * work order, on the springboard widget and on the Subroutine screen itself — because a Resident
 * planning a dark route can only do it from the list.
 *
 * **Three rungs, and the lowest is not off.** The design's roster spreads ten Subroutines 3
 * bright / 4 medium / 3 dark, and even the darkest is *near-black* rather than black: this device
 * has no off, and a phone that stopped emitting would be a player who had been revoked. So
 * [LightMark] lights one cell of three at [Dark] and never zero — nothing on the ladder is
 * darkness, and an absent mark can therefore never be misread as a dark Subroutine.
 *
 * [rung] is the position on that ladder, which is also how many cells the mark lights. It is not
 * a brightness value and nothing may treat it as one: the actual luminance a Subroutine screen
 * draws at is a property of the screen, and in play it is the lamp's, which `ui` does not decide.
 */
enum class LightSignature(val rung: Int) { Dark(1), Medium(2), Bright(3) }

/**
 * The Subroutine a player is currently sent to.
 *
 * A fixture today. In play it arrives at the effect boundary like everything else.
 */
data class CurrentSubroutine(
    /** Which of the design's ten. The name and the light signature are both its. */
    val subroutine: Subroutine,
    /** What this instance asks for, shown once you are at the marker. */
    val instruction: String,
    val room: String,
    val marker: MarkerShape?,
    val index: Int,
    val total: Int,
    val done: Int,
) {
    /**
     * The name and the light, taken off the roster rather than stored beside it.
     *
     * They were two fields here, set by hand at the one construction site, which meant a fixture
     * could name HANDSHAKE and promise BRIGHT and nothing in the type would object. A Subroutine's
     * signature is a property of the Subroutine — so it is read from the Subroutine, on all four
     * surfaces at once.
     */
    val name: String get() = subroutine.label
    val light: LightSignature get() = subroutine.light
}

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

/** The live grid, drawn from the fixture the in-round screens share. */
fun Plan.mapCells(): List<MapCell> =
    List(ROWS * COLS) { i ->
        val row = i / COLS
        val col = i % COLS
        val room = roomAt(row, col)
        val mine = room != null && room.name == HERE

        fun edgeTo(other: PlanRect?): Color? =
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

