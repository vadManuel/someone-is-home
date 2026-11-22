package home.someoneshome.ui

import home.someoneshome.model.Cell
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.MarkerId
import home.someoneshome.model.RoomKind

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * What a screen may do when tapped.
 *
 * Navigation, plus the handful of toggles the design exposes. **No screen calls into the rules**
 * — `ui` cannot see `core`. In play these become Intents posted to the authority, which may
 * refuse them, and the screen learns the outcome by being told what to draw next rather than by
 * reading a return value.
 */
class PanelActions(
    val nav: (ScreenId) -> Unit = {},
    /**
     * **Step back — the back arrow and the left-edge swipe, which are one action with two
     * surfaces** (the owner's E-PAY-6 batch).
     *
     * It is an action rather than a `goes(...)` because **a back arrow cannot name where it goes**.
     * The editor is reached from the maps list and from a saved home's detail; the marker sheet from
     * a room and from a scan. Every chevron in this app used to name one parent apiece, so half of
     * them walked a host somewhere they had not been. What back means is *the screen before this
     * one*, which is a fact about the trail and not about the screen — [FlowModel.back] is the only
     * thing that holds it.
     *
     * ### It publishes no edge, and [BackChevron] carries a fallback so that stays true
     *
     * `ScreenGraphTest` reads the graph off `nav` calls, and a trail-driven back makes none — so
     * every screen that had a chevron would have quietly lost its declared edge from [ScreenGraph],
     * and the graph would have shrunk to agree with itself. The chevron therefore falls back to the
     * screen's declared parent when [canGoBack] is false, which is exactly the state a fresh render
     * is in: the transcription is unchanged, the test still fails on a retargeted control, and on a
     * real phone the trail wins every time.
     *
     * ### [canGoBack] is asked, not assumed, because a control that does nothing is broken
     *
     * The three screens with no parent to name — the maps list, the join screen, the permissions
     * walk — draw **no chevron at all** until something is behind them. `PanelButton` and `RowButton`
     * already refuse to publish a press they cannot answer, and this is the same rule one layer up.
     */
    val canGoBack: () -> Boolean = { false },
    val back: () -> Unit = {},
    /**
     * **The two ways out of a lobby, and they are different acts by different people** (E-PAY-6:
     * *no lobby exit/terminate*; nobody is ever trapped in a lobby again).
     *
     * [leaveLobby] is one phone standing up: the socket goes down, the desk drops that seat, and
     * the counts on five other screens fall by one because the house recounted — not because
     * anything predicted it. It is every phone's, the host's included, and on the host's it also
     * takes the house down, because a host who walks out is not still running a party.
     *
     * [terminateHouse] is the host closing the house on everybody. It is **host-only and the screen
     * draws it nowhere else**, the same restriction the two settings rows carry: a client's copy of
     * it would be one guest ending the evening for the room. Every other phone learns by a push
     * (`LobbyBody.HouseClosed`), never by noticing its counts stopped moving.
     *
     * Both are two-second holds (D-141) rather than taps. Neither can be taken back — a leave costs
     * the seat and the line, and a terminate costs everybody's — and both are pressed by somebody
     * holding a phone in a room of people talking at them.
     */
    val leaveLobby: () -> Unit = {},
    val terminateHouse: () -> Unit = {},
    /**
     * **The two things page 2 can arm, and both take a two-second hold** (D-141, D-142).
     *
     * They are actions rather than a `go(...)` on the tile for the reason [saveHome] and
     * [lightsOut] are: neither is reachable by a synthetic click. **Neither walks anywhere** (D-231):
     * both send a request up and arm in place, and the Egress alert is the house's push.
     *
     * **Neither is attached on a Resident's phone at all.** Not attached-and-ignored — the tile
     * takes no pointer input whatever, so there is nothing to press and nothing to time (D-142).
     * What a Resident's page 2 is, is a page that looks exactly like an Insider's and answers
     * nothing.
     */
    val armRevoke: () -> Unit = {},
    val armEgress: () -> Unit = {},
    /**
     * **One tap on the Egress widget at a node: the Sync Pulse** (D-295). Stamped on the house's
     * clock and kept as a pip; every fourth goes up as one request. Attached only while the control
     * is live, on any phone whose person scanned a node, whatever their role.
     */
    val tapPulse: () -> Unit = {},
    /**
     * **Tier 2 firing, from a row of a list the house sent** (the 2026-08-22 targeting ruling).
     *
     * These are the only two actions in this class that carry a **target**, and both carry it in
     * the narrowest form the picker can express: a row **position** for the roster, and a card
     * **id** for the markers.
     *
     * ### Neither takes a `Seat`, and that is the same discipline `MeetingRequest` keeps
     *
     * *There is no longer any function in `ui` that turns a [Seat] into an `Intent`.* An `Int`
     * here is an index into the list this phone was drawn with, and turning it into a seat is the
     * actions layer's lookup against the same roster the house sent — exactly what
     * `MeetingRequest.asMessage(names)` does for a ballot, and for the same reason. The seat that
     * ends up on the wire is **content** — who the ability is aimed at — and the actor is still
     * stamped at the host's desk from the connection the bytes arrived on.
     *
     * ### They are actions rather than a target named on the screen, for [openRoomAt]'s reason
     *
     * A screen cannot name what it does not know: which seat a row is depends on a list that
     * arrived from the house, and which card a row is depends on the same. And neither publishes a
     * navigation for `ScreenGraphTest` to read — firing walks back to page 2, which is declared in
     * [Flow.viaActions] with the rest of the gestures.
     *
     * **None of the three is reachable on a Resident's phone.** Not by a check inside them: the
     * tier-2 tiles take no pointer input at all (D-142), so a Resident never opens a picker and
     * never has a row to tap. The same shape [armRevoke] has, one tier down.
     *
     * [fireStatic] is the third and it **takes nothing at all** (D-186). Static has no target of
     * any kind, so there is no row to tap and no picker to open: the tile's own two-second hold
     * calls it. It is the same rule taken to its end — the screen cannot name what it does not
     * know, and here there is nothing to name.
     */
    val fireSurge: (Int) -> Unit = {},
    val fireIsolate: (String) -> Unit = {},
    val fireStatic: () -> Unit = {},
    /**
     * **TIMELAPSE and the floor strip** (D-319). The first asks the house for a playback and walks
     * nowhere until the house's frames arrive; the second is input echo, choosing the storey drawn.
     */
    val playTimelapse: () -> Unit = {},
    val showStorey: (Int) -> Unit = {},
    val toggleMarkers: () -> Unit = {},
    val toggleTorch: () -> Unit = {},
    val pickRoomType: (RoomKind) -> Unit = {},
    val confirmStairs: () -> Unit = {},
    /** A tap on the plan. Opens the room under it, or does nothing where there is no room. */
    val openRoomAt: (Cell) -> Unit = {},
    /** The plan's own controls: a name, a preset chip, a storey, a room the host is done with. */
    val nameRoom: (String) -> Unit = {},
    val deleteRoom: () -> Unit = {},
    val openFloor: (String) -> Unit = {},
    val addFloor: () -> Unit = {},
    /**
     * Registration.
     *
     * The scan itself is not here: a card arriving is the camera's event and not a tap, so it
     * enters through [FlowModel.cardScanned] rather than through anything a screen can do. These
     * are the taps that *answer* a scan — moving a reserved card to the room the host is standing
     * in, taking one out of the house altogether, and pulling one marker back off the sheet. The
     * terminal and the meeting card get one pair each rather than one pair with a parameter,
     * because a screen naming which card it means cannot be given the wrong one.
     */
    val moveTerminal: () -> Unit = {},
    val removeTerminal: () -> Unit = {},
    val moveMeeting: () -> Unit = {},
    val removeMeeting: () -> Unit = {},
    val forgetMarker: (MarkerId) -> Unit = {},
    /**
     * The saved homes.
     *
     * Every one of these is a side effect beside a navigation the screen names itself — a tapped
     * row opens *that* home and then goes to the detail screen, which is where every row goes.
     * The two that are not are [saveHome] and [deleteHome]: where a save lands depends on whether
     * it was refused, and a delete is a two-second hold rather than a tap, so both are declared in
     * [Flow.viaActions] and walked by a test.
     */
    val openSavedHome: (String) -> Unit = {},
    val mapNewHome: () -> Unit = {},
    val editOpenHome: () -> Unit = {},
    val nameHome: (String) -> Unit = {},
    val saveHome: () -> Unit = {},
    val deleteHome: () -> Unit = {},
    /**
     * The lobby.
     *
     * [nameResident] and [typeLine] are typing; [attachToHome] is a tap on a network row, which
     * navigates as well because attaching to a home is going into its lobby. [handOverLine] is
     * not, for the reason [saveHome] is not: it can be refused, and a refused hand-over stays on
     * the screen with the reason on it rather than walking away from a promise the house never
     * received. It is declared in [Flow.viaActions] and walked by a test.
     *
     * [cycleInsiders] is the host's own setting and goes to the house rather than to the screen;
     * what comes back is what the row draws. [cycleVoteWindow] is the host's too and does *not* —
     * it moves a number this phone holds and nothing else reads, because what a client may send
     * is a decision about the protocol rather than about a settings row (see
     * [LobbyModel.cycleVoteWindow]). [lightsOut] is **presentation only** — see
     * [FlowModel.lightsOut].
     */
    /**
     * **The Perms screen, asking the OS for real** (D-161).
     *
     * [requestGrant] is one row tapped; [requestMissingGrants] is the primary control, which walks
     * the rows still standing between this phone and a seat. Neither navigates, and that is the
     * whole correction: the button they replace called nothing and went to the join screen anyway.
     *
     * They are not in [ScreenGraph] and not in [Flow.viaActions] because they walk no edge —
     * CONTINUE is a separate control and appears only once the gate is open.
     */
    val requestGrant: (DevicePermission) -> Unit = {},
    val requestMissingGrants: () -> Unit = {},
    /**
     * **CONTINUE, once the gate is open** (D-311): `FlowModel.permissionsDone`, which takes a hosting
     * phone back to its own lobby and every other phone to JOIN A HOME. Null where no flow is behind
     * the screen, and then CONTINUE goes to JOIN A HOME as it always did, which is the edge
     * [ScreenGraph] reads off the screen; the hosting phone's edge is in [Flow.viaActions].
     */
    val permissionsDone: (() -> Unit)? = null,
    val nameResident: (String) -> Unit = {},
    val hostHome: (String) -> Unit = {},
    /**
     * **OPEN SETTINGS, under the refusal a host can still fix** (the owner's 2026-08-24 ruling).
     *
     * It navigates nowhere in this app and is therefore in neither [ScreenGraph] nor
     * [Flow.viaActions]: it leaves for the OS entirely, and the screen the host comes back to is
     * the one they left. Drawn only where [LobbyModel.offersTheGrant] — a duplicate home name has
     * its fix on the same screen and is not offered this.
     */
    val openSystemSettings: () -> Unit = {},
    val attachToHome: (NearbyHome) -> Unit = {},
    val typeLine: (String) -> Unit = {},
    val handOverLine: () -> Unit = {},
    val cycleInsiders: () -> Unit = {},
    val cycleVoteWindow: () -> Unit = {},
    /** The host's three timed rows (D-317): [cycleVoteWindow]'s shape. See [LobbyModel.cycleDiscussion]. */
    val cycleDiscussion: () -> Unit = {},
    val cycleEgressTimer: () -> Unit = {},
    val cycleRevokeCooldown: () -> Unit = {},
    val lightsOut: () -> Unit = {},
    /**
     * **NEW ROUND — the host's control on the two ending screens** (D-157). See
     * [FlowModel.newRound].
     *
     * Beside [lightsOut] because it is the same kind of thing seen from the other end of the
     * evening: the host acts in front of the whole party, and the house moves everybody. On a
     * phone that is not hosting, the ending screens do not attach it.
     */
    val newRound: () -> Unit = {},
    /**
     * **The meeting, and the four controls that report one phone and move nothing.**
     *
     * None of these navigates, and that is the whole point of them being here rather than being a
     * `go(...)` on a button: what happens after a check-in, a READY, or a vote depends on every
     * phone in the house, and no phone can count phones. They are declared in neither
     * [ScreenGraph] nor [Flow.viaActions] because they walk no edge at all — the meeting's
     * transitions are the house's, and they are now in [Flow.housePushes] rather than standing in
     * as timers.
     *
     * **They do reach the house**, which is what changed: each one publishes a [MeetingRequest],
     * seat-free, and the connection attaches the seat (see [MeetingModel.send]).
     *
     * [chooseVote] and [readyToVote] are two steps rather than one because the design's vote screen
     * shows *how many have voted, never what*: having voted is a state, distinct from having a
     * finger on a row. **After [readyToVote] the selection cannot be changed** (D-117), which
     * reverses what these two comments used to say about it.
     */
    val checkIn: () -> Unit = {},
    val sayReady: () -> Unit = {},
    val chooseVote: (VoteChoice) -> Unit = {},
    val readyToVote: () -> Unit = {},
    /**
     * **The Subroutines: one control that navigates and two that only ever echo.**
     *
     * [beginSubroutine] is here rather than on the caught-scan screen because BEGIN opens
     * whichever Subroutine the scanned card holds, and a screen cannot name a target that depends
     * on a piece of paper. It is declared in [Flow.viaActions] with the other decisions the
     * actions layer owns.
     *
     * [tapSubroutine] and [handOverSubroutine] navigate nowhere and decide nothing. A tap lights
     * what you touched — your own input, the one thing a screen may draw without asking — and the
     * entry goes to the house as an Intent that the *house* verifies (D-042). **Neither of them
     * takes a role**, and nothing they reach holds one: an Insider's Subroutine is a fake in the
     * ledger and identical everywhere a player can see it, which is rule 8 built rather than
     * promised.
     */
    val beginSubroutine: () -> Unit = {},
    val tapSubroutine: (Subroutine, Int) -> Unit = { _, _ -> },
    val handOverSubroutine: (Subroutine) -> Unit = {},
    /**
     * **Drift tapped before the buzz** (D-165): the entry goes as a wrong answer and is spent. The
     * screen calls it because the screen is what knows the house has not asked yet. No role.
     */
    val handOverEarly: () -> Unit = {},
    /**
     * **Something arriving, swiped away — up on a banner, left under the clock.**
     *
     * Not a tap and not a navigation the notification can name: what a dismissal leaves you
     * looking at is whatever it arrived over, which the notification does not know. It is declared
     * in [Flow.viaActions] beside the other gestures no synthetic click can reach, and walked by a
     * test that really drags a finger.
     */
    val dismissNotification: () -> Unit = {},
    /**
     * **The meeting's house notice, swiped up off the screen it arrived on** (D-105, D-119).
     *
     * Separate from [dismissNotification] because a notice is not one of the arrivals that map to
     * a screen they landed over — it *is* a screen, shown once at the top of the meeting and held
     * nowhere afterwards. Where it goes is the discussion, which is also where the house's own
     * nine seconds takes it; swiping is going first.
     *
     * It is an action rather than a `go(...)` on the notice for the reason the swipes above are:
     * a drag publishes no click action, so the edge is declared in [Flow.viaActions] and walked by
     * a test rather than read off the screen.
     */
    val dismissNotice: () -> Unit = {},
    /**
     * **STOP NOW — the performance window closing, reported** (D-110, D-111, E-P2-5 closed).
     *
     * It is an action rather than the plain `go(Work)` the button used to be, because it is now two
     * things: a navigation the player asked for, and **a report the house needs**. The presence
     * plane's one consumer is an expiry (D-136), and a window that never shut leaves a placement
     * standing in a room the player left.
     *
     * **It still says nothing about the work.** No reason, no marker, no progress — see
     * `Event.PerformanceEnded`, where both omissions are the ruling rather than a saving: an
     * abandonment record is the behavioural channel D-111 split the two planes to close.
     */
    val stopSubroutine: () -> Unit = {},
    /**
     * **One of the lock screen's stored notifications, swiped off the list.**
     *
     * Separate from [dismissNotification] because it is a different question: that one is *the*
     * thing arriving now and there is only ever one, this one names which of several standing
     * notifications the finger was on. It moves no screen at all — the player is still looking at
     * their lock screen, with one fewer thing on it.
     *
     * **What it does is remove, not mark.** There is nowhere to record that a notification was
     * swiped and nothing that could be asked about it afterwards; see [NotificationsModel].
     */
    val dismissStanding: (Notification) -> Unit = {},
)

val LocalActions: ProvidableCompositionLocal<PanelActions> =
    staticCompositionLocalOf { PanelActions() }

/**
 * The navigate function, captured for use inside non-composable click lambdas.
 *
 * `LocalActions.current` can only be read during composition, so a screen grabs it once at the
 * top and closes over it. Every screen in this module opens with `val go = navigator()`.
 */
@Composable
fun navigator(): (ScreenId) -> Unit = LocalActions.current.nav

/**
 * A tap target with no visual feedback of its own.
 *
 * **Deliberately no indication.** A ripple or a highlight is a change in lit pixel area the
 * rules did not author. On the springboard it would be worse than that: a Resident tapping the
 * ability must get exactly what an Insider gets, and a control that *looked* different when
 * pressed would answer the question the button exists to refuse. Echo of your own input is
 * allowed, but it has to be designed, not inherited from a theme.
 */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Navigate to [id] on tap. The overwhelmingly common case. */
@Composable
fun Modifier.goes(id: ScreenId): Modifier {
    val actions = LocalActions.current
    return tap { actions.nav(id) }
}

/**
 * **The left-edge swipe — the other surface of [PanelActions.back], on the frame rather than on a
 * screen.**
 *
 * The owner asked for both idioms and this is the half that cannot live on a screen: the gesture
 * starts in whatever happens to be drawn at the left edge — a room panel, a list row, a map cell —
 * and thirty screens each catching their own drag would be thirty chances to disagree about what a
 * swipe is. So it goes on [Screen]'s root box, once, and every browsing surface inherits it.
 *
 * ### Absent, not disabled, on the house's screens
 *
 * With [enabled] false this returns the modifier untouched: **no pointer input node at all**, which
 * is the same discipline [rememberHold] keeps for a Resident's page 2. A gesture recogniser that
 * ran and then declined would be a drag that eats a frame's worth of input on the screens where a
 * dropped tap is a vote, and it would put a thing on the pointer path of every screen in the dark.
 * [Flow.backable] is what decides, and it is a set of screens rather than a runtime question.
 *
 * ### Why a start zone and a distance, and why both are units rather than points
 *
 * [EDGE_SWIPE_ZONE] is where the finger has to land — iOS's own interactive-pop gesture is an edge
 * gesture, and a swipe that worked from the middle of the screen would fire on every horizontal
 * drag anybody ever makes across a list. [EDGE_SWIPE_TRAVEL] is how far it has to go, so that a
 * fumble in a lit hallway is not a navigation. Both are design units, so they scale with the panel
 * the way [TAP_TARGET] does rather than being a point count that is right on one phone.
 *
 * **It cannot steal a tap.** `detectHorizontalDragGestures` consumes nothing until touch slop is
 * exceeded, so a press that never moves reaches whatever is under it — and a child that has already
 * consumed the drag (a slider, a plan being painted) cancels this before it starts.
 */
@Composable
fun Modifier.edgeSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    val zone = with(LocalDensity.current) { EDGE_SWIPE_ZONE.toPx() }
    val travel = with(LocalDensity.current) { EDGE_SWIPE_TRAVEL.toPx() }
    return pointerInput(enabled) {
        var startedAtTheEdge = false
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { at ->
                startedAtTheEdge = at.x <= zone
                travelled = 0f
            },
            onDragEnd = {
                // Read once and reset before calling out: `onBack` navigates, and a gesture that
                // left its own state set would fire again on the next drag that went nowhere.
                val go = startedAtTheEdge && travelled >= travel
                startedAtTheEdge = false
                travelled = 0f
                if (go) onBack()
            },
            onDragCancel = {
                startedAtTheEdge = false
                travelled = 0f
            },
            onHorizontalDrag = { _, delta -> travelled += delta },
        )
    }
}

/**
 * How near the left edge a back swipe has to start. 14.4 units is about 15pt on the narrowest
 * phone in range — wide enough to catch a thumb reaching round the side of a case, narrow enough
 * that it is not simply *the left third of every screen*. (Twelve units of the old 300-wide canvas,
 * carried to 360 unchanged in size.)
 */
val EDGE_SWIPE_ZONE: Dp = 14.4.u

/**
 * How far right it has to travel. A quarter of the canvas: far enough that it is unmistakably a
 * gesture rather than a slipped finger, short enough to complete one-handed in the dark.
 */
val EDGE_SWIPE_TRAVEL: Dp = 90.u

/**
 * **The smallest a control may be: Apple's 44pt, on the narrowest phone in range** (D-205).
 *
 * A unit is `panelWidth / 360` points, and the narrowest phone this app targets is
 * [NARROWEST_PHONE_POINTS] across, so 44pt there is `44 × 360 / 375` = 42.24 units; every wider
 * phone makes it larger. It was 36, written for the old 300-unit canvas, where it cleared 44pt;
 * D-202's 360 canvas left it at 37.5pt until the owner raised it. D-202 draws the kit's own
 * targets at 56, so this is the floor under them, not their size. `MeetingInputTest` holds a
 * meeting's controls to this same value.
 *
 * This is not a general accessibility uplift and the type sizes are untouched — the reference
 * device is a 2001 organiser and its 6-unit labels are the design (see [Label]). It is about the
 * *finger*, and specifically about this game's finger: a player standing in an unlit room, holding
 * the phone at an angle so it does not shine on anybody, forbidden to speak if they miss. A vote
 * cast on the wrong resident because a 22-unit row was a 27pt target is a game outcome decided by
 * a touch target.
 */
val TAP_TARGET: Dp = (44.0 * DESIGN_WIDTH / NARROWEST_PHONE_POINTS).u

/** The narrowest phone in range, in points: the width [TAP_TARGET]'s 44pt is measured on. */
const val NARROWEST_PHONE_POINTS: Float = 375f

/**
 * A tap target that is never shorter than [TAP_TARGET], whatever is drawn inside it.
 *
 * The minimum and the click sit on the same node, so the region that answers is the region with
 * the outline around it. A control whose touch area is larger than its visible edge steals presses
 * from its neighbour; one whose touch area is smaller teaches people to aim at the wrong place.
 */
@Composable
fun Modifier.tapTarget(onClick: () -> Unit): Modifier = heightIn(min = TAP_TARGET).tap(onClick)

/**
 * The design's primary control: a full-width bordered block with a centred label, in one of
 * three intensities — outlined, filled slate (a pre-game commit), or inverted amber (in-game
 * emphasis).
 *
 * **A button with no handler is not a control**, and it publishes no click action — the same fault
 * [RowButton] had and the same fix. It used to default to an empty lambda, so every decorative
 * block in the game answered a press by doing nothing, which is indistinguishable from one that is
 * broken. That matters most where a control is deliberately *present and inert*: the lobby's gated
 * LIGHTS OUT, and a readiness button that has already been pressed. Those have to keep their shape
 * so the layout does not move under a thumb, and must not keep their press.
 *
 * **Two tests hold it true**, each on a control that is deliberately present and inert: the Jam
 * route with nothing entered, and the discussion's READY TO VOTE once it has been pressed.
 * Injection-verified together — making the null branch tap anyway fails both by name.
 *
 * *(It used to be three, and the lobby's gated LIGHTS OUT was the sharpest of them. That control is
 * a two-second hold now (D-141) and expresses the same refusal by taking no pointer input at all —
 * see [HoldToConfirm]'s `enabled`. The ballot's LOCK IN went the same way. The argument is
 * unchanged wherever a tap survives: a control that answers a press by doing nothing is
 * indistinguishable from one that is broken.)*
 *
 * Note what is *not* guarded and does not need to be: the `null` **default**. Every call site in
 * the app passes `onClick` explicitly, so changing the default back changes no screen. The
 * behaviour that matters is the branch below, and that is what the three tests read.
 */
@Composable
fun PanelButton(
    text: String,
    modifier: Modifier = Modifier,
    border: Color = Amber.Dim,
    fill: Color = Color.Transparent,
    ink: Color = Amber.Bright,
    size: Double = 8.0,
    tracking: Double = 0.16,
    verticalPadding: Dp = 10.u,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth().heightIn(min = TAP_TARGET).border(1.u, border).background(fill)
    Box(
        (if (onClick != null) base.tap(onClick) else base).padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, size = size, color = ink, tracking = tracking, align = TextAlign.Center)
    }
}

/**
 * A list row: label left, value or affordance pushed right.
 *
 * Saved homes, nearby homes, settings, the message list, and every host-setup line. The
 * right-hand slot is a composable rather than a string because it carries an address, marker
 * shapes and unread marks — things that are drawn, not written.
 *
 * **A row with no handler is not a control**, and it does not publish a click action. It used to:
 * the parameter defaulted to an empty lambda, so every settings line in the game was a tap target
 * that did nothing. That is a lie told by a row — worst on the lobby, where the same settings are
 * the host's to change and everybody else's to read, and where the difference between the two has
 * to be visible in what the screen does when you press it.
 */
@Composable
fun RowButton(
    modifier: Modifier = Modifier,
    border: Color = Ramp.Faint,
    fill: Color = Color.Transparent,
    horizontalPadding: Dp = 7.u,
    verticalPadding: Dp = 8.u,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val base = modifier.fillMaxWidth().border(1.u, border).background(fill)
    Row(
        (if (onClick != null) base.tap(onClick) else base)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The blinking caret on a field that accepts typing.
 *
 * It appears on the line handed to the house, which is deleted when the round ends, and on the
 * room-name field. A caret promises the device is listening; both screens keep that promise,
 * and one of them keeps nothing else.
 *
 * **Hard on/off, never a fade** — `steps(1)` in the source. A fading caret is a luminance ramp
 * nobody authored, which is the one thing the lamp rules forbid outright.
 */
@Composable
fun Caret(color: Color, size: Double = 17.0) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
        ),
        label = "caretAlpha",
    )
    Readout("_", size = size, color = color.copy(alpha = alpha))
}

/**
 * **The one field in this interface a host actually types into.**
 *
 * A [Readout] with a real keyboard behind it. Everywhere else the caret is drawn by [Caret] as a
 * promise that the device is listening; here the promise is kept, and the caret is the field's own
 * so there is exactly one of them and it sits where the next character will go.
 *
 * The text is passed through [transform] on the way in rather than on the way out — a home named
 * in lower case would come back shouted at the moment it was saved, and a field that rewrites what
 * you typed after you stop looking at it is a field nobody trusts.
 *
 * [hint] is drawn in the field's own face while it is empty, and it is not decoration. The design
 * drew a blinking [Caret] in every field to promise the device was listening; a real field shows
 * its caret only once it is focused, so an empty one with nothing in it reads as a box rather than
 * as somewhere to type. That is a fair mistake to make on the two screens in this game where
 * somebody is being asked for something.
 *
 * ### The whole box takes the tap, not the one line of glyphs inside it (E-PAY-6)
 *
 * The owner's finding was on the one-line screen: a bordered panel most of a screen tall, with a
 * text field one line high pinned at the top of it, and a tap anywhere in the remaining four fifths
 * hit nothing. It reads as a broken field rather than as a miss, because the thing being aimed at
 * *is* the box — nobody aims at a caret.
 *
 * So the outer [Box] carries a tap that focuses the field, and the fix is here rather than at the
 * two call sites: a screen that had to remember to wire its own focus requester is a screen that
 * will forget. A caller wanting the larger target gives this a taller [modifier]; one that does not
 * is unchanged, because the box is the field's own height and the tap lands where it always did.
 */
@Composable
fun ReadoutField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Double = 17.0,
    color: Color = Amber.BoneInk,
    tracking: Double = 0.06,
    hint: String = "",
    /**
     * **What the field is called, for anything that cannot see the label printed above it.**
     *
     * The panel draws the field's name as a separate `Label` in the row above — which the eye
     * reads as one thing and every non-visual reader receives as two, the second of them nameless.
     * Naming it here is the honest fix and not a test hook: a text field whose only identity is
     * its position on the page is unusable by a screen reader, and was equally unreachable to the
     * human-mock, which is how the gap was found.
     */
    label: String = "",
    /** The field's handle, for the human-mock. Kebab-case, unique on its screen. */
    tag: String = "",
    transform: (String) -> String = { it.uppercase() },
    /**
     * Whether the words wrap onto a second line. They are one line either way — a caller that wraps
     * keeps newlines out in [transform] — and this is only whether a long one is still on screen.
     */
    singleLine: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    Box(modifier.tap { focus.requestFocus() }) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            Readout(hint, size = size, color = Amber.BoneFaint, tracking = tracking)
        }
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(transform(it)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                .then(if (tag.isEmpty()) Modifier else Modifier.testTag(tag))
                .then(
                    if (label.isEmpty()) Modifier
                    else Modifier.semantics { contentDescription = label }
                ),
            textStyle = TextStyle(
                fontFamily = PanelType.readout,
                fontSize = size.sp,
                color = color,
                letterSpacing = tracking.em,
            ),
            singleLine = singleLine,
            cursorBrush = SolidColor(color),
        )
    }
}

/**
 * **The two-second hold as a gesture, with nothing drawn** (D-141).
 *
 * *Hold what cannot be taken back.* Five controls in this app take it — the vote lock, arming a
 * Revoke, arming an Egress, the host's LIGHTS OUT and StairsWarn's UNREGISTER AND CONTINUE — and
 * they do not all look alike: three are the design's full-width bar-and-note block ([HoldToConfirm])
 * and two are springboard tiles that must go on looking exactly as they looked. So the *clock* is
 * here and the furniture is the caller's, which is what stops the five drifting into five slightly
 * different ideas of two seconds.
 *
 * The progress is driven by frames while the finger is down and **resets the moment it lifts** — a
 * hold is not a total. A mis-tap in a pocket, a fumble in the dark or a phone handed to a child
 * reaches nothing.
 *
 * ### [enabled] is refusal at the surface, not refusal at the end
 *
 * With it false there is **no pointer input at all**: no press, no progress that runs and then
 * declines, nothing from the first millisecond. That is D-142 for a Resident's page 2, and it is
 * the whole of the ruling — a hold that filled for two seconds and then refused would be a
 * self-test, and a bar filling in a dark house is world-observable to whoever is behind the
 * shoulder. The safe control is the one with nothing behind it to probe.
 *
 * ### It publishes no click action, and that is the point
 *
 * `ScreenGraphTest` reads the whole screen graph off click *semantics* actions, which is how every
 * other control in this app is checked. A hold has none — a control that could be fired by one
 * synthetic click would not be a hold — so the edges these walk are declared in [Flow.viaActions]
 * and proved by tests that really hold a finger down for two seconds.
 */
@Composable
fun rememberHold(
    onConfirm: () -> Unit,
    enabled: Boolean = true,
    millis: Int = HOLD_MILLIS,
): HoldState {
    var holding by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }

    LaunchedEffect(holding) {
        if (!holding) {
            elapsed = 0
            return@LaunchedEffect
        }
        var last = withFrameMillis { it }
        var spent = 0L
        while (spent < millis) {
            val now = withFrameMillis { it }
            spent += now - last
            last = now
            elapsed = spent.coerceAtMost(millis.toLong()).toInt()
        }
        holding = false
        onConfirm()
    }

    val surface =
        if (!enabled) Modifier
        else Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    holding = true
                    tryAwaitRelease()
                    holding = false
                },
            )
        }
    return HoldState(holding, elapsed, millis, surface)
}

/**
 * How far a hold has got, and where to put the finger.
 *
 * [surface] is the pointer input, handed to the caller rather than wrapped around it: the design's
 * block puts it on the button and not on the bar above it, and a springboard tile puts it on the
 * whole tile. **It is [Modifier] and nothing else when the hold is disabled**, which is what makes
 * an inert control genuinely inert rather than merely unanswered.
 */
class HoldState(
    /** True from the moment the finger lands, which is before there is any progress to draw. */
    val holding: Boolean,
    val elapsed: Int,
    val millis: Int,
    val surface: Modifier,
) {
    /** 0 at rest, 1 the frame it completes. */
    val fraction: Float get() = elapsed.toFloat() / millis
}

/**
 * **Hold for two seconds. The bar is the hold, not an animation of one.**
 *
 * The design's own control for the one irreversible thing in host setup, and now for the four
 * others D-141 named: it is the same block wherever it appears, so a host who has deleted a home
 * already knows what the ballot and the lobby are asking of them.
 *
 * The colours are parameters because three of the five holds sit on a **dark-field** screen and two
 * on the bone LCD, and the defaults are the bone ones so a light-field caller writes none of them.
 * Nothing else about the block varies: same bar, same note, same height, same words while the
 * finger is down.
 *
 * **With [enabled] false it is present and inert** rather than absent — a control that materialised
 * under a thumb about to press it is worse than one that is visibly not ready — and the note is
 * then the caller's chance to say why.
 */
@Composable
fun HoldToConfirm(
    label: String,
    restingNote: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    millis: Int = HOLD_MILLIS,
    enabled: Boolean = true,
    spent: Color = Amber.BoneDim,
    track: Color = Amber.BonePale,
    border: Color = Amber.BoneFaint,
    fill: Color = Color.Transparent,
    ink: Color = Amber.BoneDim,
    noteInk: Color = Amber.BoneFaint,
) {
    val hold = rememberHold(onConfirm, enabled, millis)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.u)) {
        // Two blocks rather than a fraction of one: the bar has to read at six units tall on a
        // 300-unit canvas, and a weight of zero is a box that still draws its own hairline.
        Row(Modifier.fillMaxWidth().height(6.u), horizontalArrangement = Arrangement.spacedBy(1.u)) {
            val done = hold.fraction
            if (done > 0f) Box(Modifier.weight(done).fillMaxHeight().background(spent))
            if (done < 1f) Box(Modifier.weight(1f - done).fillMaxHeight().background(track))
        }
        PreNote(
            if (hold.holding) "KEEP HOLDING . ${seconds(hold.elapsed)}S OF ${seconds(millis)}S"
            else restingNote,
            color = noteInk,
            tracking = 0.12, lineHeight = 1.0, align = TextAlign.Center,
        )
        // [TAP_TARGET] for the same reason every other control has it, and one more: the design's
        // own padding leaves this block at 34 units, and a hold that cannot be *started* reliably
        // in the dark is a hold nobody finishes. It is the floor rather than the height, so the
        // design's padding still decides on any screen where the label wraps.
        Box(
            Modifier.fillMaxWidth().heightIn(min = TAP_TARGET).testTag(HOLD_BLOCK)
                .border(1.u, border).background(fill)
                .then(hold.surface)
                .padding(vertical = 13.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(label, size = 8.0, color = ink, tracking = 0.16, align = TextAlign.Center)
        }
    }
}

/** Tenths, which is the resolution the design's own note is written at. */
private fun seconds(millis: Int): String = "${millis / 1000}.${millis % 1000 / 100}"

/** Two seconds, the design's number, and the only place it is written down. */
const val HOLD_MILLIS: Int = 2_000

/**
 * **The block a finger goes on, findable by a test that cannot look for a click action.**
 *
 * The tap-target sweep in `MeetingInputTest` reads every control on every screen off click
 * semantics, and a hold has none — so retiring the ballot's tap would have quietly taken the
 * meeting's most important control out of the one measurement that exists to keep it hittable in
 * the dark. A test that stops checking is worse than one that fails.
 *
 * Beside `HOLD_SURFACE`, which is the same idea for the Subroutine that counts fingers.
 */
const val HOLD_BLOCK: String = "hold-block"

/**
 * **The words under every hold in the game, so the gesture is taught once.**
 *
 * A player learns what a bar under a button means on the Delete screen with the lights on, and
 * meets the same sentence four more times in the dark. Five separate wordings would be five
 * separate things to work out at the moment each of them cannot be taken back.
 */
const val HOLD_NOTE: String = "HOLD THE BUTTON FOR TWO SECONDS."
