package home.someoneshome.ui

import home.someoneshome.model.CardPayload
import home.someoneshome.model.Cell
import home.someoneshome.model.RegisterResult
import home.someoneshome.model.RoomKind

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * The walkable screen graph, and the rules that walk it without being asked.
 *
 * [ScreenId] is deliberately *a screen list, not a navigation graph* — it says what exists and
 * nothing about what may follow what. This file is the other half: what a tap reaches, what the
 * house moves you to on its own, and where stepping backwards is refused.
 *
 * ### It is a transcription, and it is not the loop
 *
 * Every edge here is read off a ported screen or off a decision already taken. Nothing in this
 * file decides a game question, and nothing may: `ui` cannot see `core`, and a navigation layer
 * that could answer "did that work?" would be answering it on the device, which is the leak
 * surface the module boundary exists to delete. Screens echo input; they never predict outcomes.
 *
 * ### What the timings are, honestly
 *
 * **Every number in [Flow.autoAdvance] is a presentation placeholder.** In play the house owns
 * the clock: it decides when the ring is answered, when the talk starts, when the lights go out,
 * and it pushes the screen. This table is what a phone with no house attached does instead —
 * the playtest root's local driver, and the thing that makes the ported flows walkable end to
 * end on a device that is not yet in a round. Where the design already carries a number the
 * table borrows it rather than inventing one; where it does not, the number is made up and says
 * so. None of them is a balance value and none of them is locked at arming.
 */
object ScreenGraph {

    /**
     * Where a screen's own controls can take you.
     *
     * **Exhaustive over [ScreenId] on purpose**, the same discipline as `Screen`'s `when`: adding
     * a screen without deciding what it reaches is a COMPILE ERROR, not a screen that quietly
     * has no way out. Transcribed by rendering each screen and firing every tap target it
     * publishes — `ScreenGraphTest` does exactly that on every build and fails if the two drift.
     *
     * The union over both roles, because parity means both roles see the same controls; the four
     * screens that differ do so in what a tap *does*, and the Insider-only egress tile is the one
     * place that shows up here as an edge one role cannot walk.
     */
    fun exitsOf(id: ScreenId): Set<ScreenId> = when (id) {
        // Cold start. Boot publishes no control at all — it falls through (see Flow.autoAdvance).
        ScreenId.Boot -> emptySet()
        ScreenId.Perms -> setOf(ScreenId.Join)
        ScreenId.Join -> setOf(ScreenId.Lobby, ScreenId.Maps)

        // Host setup, in the light.
        ScreenId.Maps -> setOf(ScreenId.HomeDetail, ScreenId.Editor)
        // REVIEW HOME reaches BOTH: the plan is reviewable once some room holds the terminal,
        // and until then the same button goes to the screen that says where one belongs. The
        // plan itself is not in here — a tap on it lands on whichever room is under it, which
        // is an actions-layer decision (see Flow.viaActions).
        ScreenId.Editor -> setOf(
            ScreenId.Maps, ScreenId.Floors, ScreenId.SaveName, ScreenId.NoTerminal,
        )
        ScreenId.RoomEdit -> setOf(ScreenId.MarkerSheet, ScreenId.Editor)
        ScreenId.StairsWarn -> setOf(ScreenId.RoomEdit, ScreenId.MarkerSheet)
        ScreenId.MarkerSheet -> setOf(ScreenId.TermRemove, ScreenId.ScanMarker, ScreenId.RoomEdit)
        ScreenId.ScanMarker -> setOf(ScreenId.MarkerSheet)
        ScreenId.TermTaken -> setOf(ScreenId.ScanMarker)
        ScreenId.TermRemove -> setOf(ScreenId.MarkerSheet)
        // OPEN A ROOM goes back to the plan, not into the room panel: the host has to choose
        // WHICH room the terminal goes in, and the plan is the only screen that can ask that.
        ScreenId.NoTerminal -> setOf(ScreenId.Editor)
        ScreenId.Floors -> setOf(ScreenId.Editor)
        // SAVE HOME is not in here: a refused save stays on this screen, so where it lands
        // depends on an answer the screen does not have (see Flow.viaActions).
        ScreenId.SaveName -> setOf(ScreenId.Editor)
        ScreenId.HomeDetail -> setOf(
            ScreenId.Maps, ScreenId.Lobby, ScreenId.Editor, ScreenId.SaveName, ScreenId.Delete,
        )
        // HOLD TO DELETE publishes no click action at all — it is a two-second hold, and a
        // control a single synthetic click could fire would not be one. Also in viaActions.
        ScreenId.Delete -> setOf(ScreenId.HomeDetail)
        ScreenId.Lobby -> setOf(ScreenId.Secret, ScreenId.Armed)

        // Arming.
        ScreenId.Secret -> setOf(ScreenId.Lobby)
        ScreenId.Armed -> setOf(ScreenId.Home)

        // The springboard, and the two screens that ARE the springboard with something on top.
        ScreenId.Home, ScreenId.Notify, ScreenId.EgressWidget -> SPRINGBOARD
        ScreenId.Banner -> SPRINGBOARD + ScreenId.EgressWidget
        ScreenId.Page2 -> setOf(
            ScreenId.Home, ScreenId.Work, ScreenId.Scan, ScreenId.Lock,
            // The Insider's egress tile. A Resident's tap on the same tile does nothing and does
            // it silently — same control, same brightness, no new view for either role to be
            // caught on. This is the only role-asymmetric edge in the graph.
            ScreenId.Banner,
        )
        ScreenId.Lock -> setOf(ScreenId.Home)

        // Work.
        ScreenId.Work -> setOf(ScreenId.Home, ScreenId.Scan)
        ScreenId.Scan -> setOf(ScreenId.Home)
        ScreenId.ScanCaught -> setOf(ScreenId.Sub, ScreenId.Work)
        // Both refusals, pixel-identical apart from their text, and identical here too. One of
        // them is Resident-only; differing exits would be the tell the shared body prevents.
        ScreenId.ScanBad, ScreenId.ScanUnknown -> setOf(ScreenId.Scan, ScreenId.Work)
        ScreenId.Sub -> setOf(ScreenId.SubBright)
        ScreenId.SubBright -> setOf(ScreenId.Work)
        ScreenId.Files -> setOf(ScreenId.Home)
        ScreenId.Notes -> setOf(ScreenId.Home)
        ScreenId.TermNo -> setOf(ScreenId.Home, ScreenId.TermLive)
        ScreenId.TermLive -> setOf(ScreenId.Home, ScreenId.Timelapse)
        ScreenId.Timelapse -> setOf(ScreenId.TermLive)
        ScreenId.Reveal -> setOf(ScreenId.Home, ScreenId.RevealThread)
        ScreenId.RevealThread -> setOf(ScreenId.Reveal)
        ScreenId.Settings -> setOf(ScreenId.Home)

        // The room. Every one of these is a line with one way forward and no way out — see
        // Flow.houseDriving.
        ScreenId.Calling -> emptySet()
        ScreenId.Call, ScreenId.Found -> setOf(ScreenId.Assemble)
        ScreenId.Assemble -> setOf(ScreenId.Notice)
        ScreenId.Notice -> setOf(ScreenId.Discussion)
        ScreenId.Discussion -> setOf(ScreenId.Vote)
        ScreenId.Vote -> setOf(ScreenId.Tally)
        ScreenId.Tally -> emptySet()

        // Out. The two notices publish no control whatever: nothing further is required of you.
        ScreenId.Revoked, ScreenId.Restrained -> emptySet()
        ScreenId.Ghost2 -> setOf(ScreenId.GhostMeeting)
        ScreenId.GhostMeeting -> emptySet()
        ScreenId.Ghost3 -> emptySet()
        ScreenId.Disconnect -> emptySet()
        ScreenId.WinInsiders, ScreenId.WinResidents -> emptySet()
    }

    /** The springboard's ten tiles and dock buttons, shared by the three screens that draw it. */
    private val SPRINGBOARD = setOf(
        ScreenId.Work, ScreenId.Files, ScreenId.Notes, ScreenId.Calling, ScreenId.Reveal,
        ScreenId.Settings, ScreenId.TermNo, ScreenId.Page2, ScreenId.Scan, ScreenId.Lock,
    )

    /** The whole graph, for the walks that need it all at once. */
    val exits: Map<ScreenId, Set<ScreenId>> = ScreenId.entries.associateWith { exitsOf(it) }
}

/**
 * A transition the house makes on its own, with no tap.
 *
 * [afterMillis] is presentation timing and nothing else — see [ScreenGraph]'s note. [why] names
 * the cue on the screen that says this will happen, so a row can never be added without somebody
 * being able to point at the design that asked for it.
 */
data class AutoAdvance(val to: ScreenId, val afterMillis: Int, val why: String)

object Flow {

    /**
     * Screens that move on by themselves.
     *
     * **A rule belongs here only when the screen itself says it will move on** — a fall-through, a
     * ring, a countdown, a bar draining. Anything waiting on a game event instead (a meeting being
     * called, a card being scanned, the round ending) is a push from the house and is deliberately
     * absent: see [housePushed].
     *
     * A screen with no row simply waits, which is the fail-closed direction — a new screen that
     * nobody wrote a rule for sits still, and "it never advanced" is noticed in thirty seconds.
     */
    val autoAdvance: Map<ScreenId, AutoAdvance> = mapOf(
        ScreenId.Boot to AutoAdvance(ScreenId.Perms, 2_400, "the self-test says STARTING and falls through on its own"),

        // The scan's countdown, and the ONE number here that is a safety device rather than
        // pacing: ten seconds, then the light dies and the phone goes back where it was. Nobody
        // should be standing in a dark room holding a lit screen at a wall by accident. Derived
        // from the design's own segment count rather than restated, so the bar and the timeout
        // cannot disagree.
        ScreenId.Scan to AutoAdvance(
            ScreenId.Home, PanelVals.SCAN_SEGMENTS * 500, "the scan window, two segments a second",
        ),

        // The meeting: call, walk in, notices, talk, vote, result, lights out. One line, no
        // branches, and each step is the house moving everybody at once.
        ScreenId.Calling to AutoAdvance(ScreenId.Assemble, 6_000, "WAITING FOR THE REST — the last phone answers"),
        ScreenId.Call to AutoAdvance(ScreenId.Assemble, 6_000, "the ring; a house meeting cannot be declined"),
        ScreenId.Found to AutoAdvance(ScreenId.Assemble, 6_000, "the same ring, different header"),
        // D-104: the talk does not start until every living player AND every out player has
        // checked in. That gate is the house's — it counts phones — and this delay stands in its
        // place on a phone with no house attached. The design's own device shell auto-advances
        // this step, which is why the I AM HERE button in the port hands its target to nobody.
        ScreenId.Assemble to AutoAdvance(ScreenId.Notice, 8_000, "4 OF 6 CHECKED IN — the check-in gate closes"),
        ScreenId.Notice to AutoAdvance(ScreenId.Discussion, 9_000, "notices are shown once at the top of the meeting, then gone"),
        ScreenId.Discussion to AutoAdvance(ScreenId.Vote, 90_000, "the discussion clock; unanimous READY skips ahead"),
        ScreenId.Vote to AutoAdvance(ScreenId.Tally, 60_000, "the vote window closes and the ballot is read"),
        ScreenId.Tally to AutoAdvance(ScreenId.Home, 15_000, "LIGHTS OUT IN 9, over a bar with 6 spent"),

        // The same meeting from outside the system. A player who is out walks in, watches, and
        // gets the outside view only once the meeting has ended — by which time the room already
        // knows they are out, so there is never a window where they know something the living do
        // not.
        ScreenId.Ghost2 to AutoAdvance(ScreenId.GhostMeeting, 8_000, "4 OF 6 CHECKED IN — the same gate, from outside"),
        ScreenId.GhostMeeting to AutoAdvance(ScreenId.Ghost3, 60_000, "VOTING ENDS IN 0:24 — the meeting ends"),
    )

    /**
     * **The house is driving.** Stepping backwards off these is refused.
     *
     * Not a styling choice and not a convenience: on every screen here, *nothing behind you is
     * still true*. The perimeter armed; a meeting was called; the vote happened; your access was
     * revoked. Walking back into the screen you were on before would be the device asserting a
     * state the house has already replaced — and on the out screens it would be worse, because
     * the phone would be pretending you were still in the round.
     *
     * ### The set is not invented — it is D-102's haptic doctrine read the other way round
     *
     * *A screen that arrives unasked buzzes; a screen you tapped into does not.* A screen that
     * arrived unasked is one the house put you on, so the buzzing screens are house-driving,
     * with **one exception the doctrine itself names**: the host's registration scan buzzes
     * because the phone is against a card with the display angled away, not because it arrived
     * unasked. `FlowTest` asserts exactly that relationship, so the two sets cannot drift.
     *
     * The additions below buzz on nobody's phone and are still house-driving, each for its own
     * reason, named at the entry.
     */
    val houseDriving: Set<ScreenId> = buildSet {
        // Everything that arrives unasked, minus the one screen D-102 excepts.
        addAll(PanelVals.BUZZING - ScreenId.ScanMarker)

        // Nothing is behind the self-test: the app has just started.
        add(ScreenId.Boot)
        // You called the meeting. You are now waiting with everybody else, and the house has the
        // room — the same position as everyone who answered, which is the point.
        add(ScreenId.Calling)
        // Mid-meeting. The talk and the vote are the room's, on the room's clock.
        add(ScreenId.Discussion)
        add(ScreenId.Vote)
        // Out, and walking in. A revoked player has no round to step back into.
        add(ScreenId.Ghost2)
        add(ScreenId.Ghost3)
        // The house is gone. Every screen behind this one describes a phone that is still
        // attached to one.
        add(ScreenId.Disconnect)
    }

    /**
     * The screens no tap and no fall-through reaches: **in play, the house puts you there.**
     *
     * Written down rather than left implicit, because this list is the honest answer to "why can
     * I not get to that screen by walking?" — and because it is what the cheat picker exists for.
     * A text arriving, a call ringing, a scan being answered, a revocation, the round ending: all
     * of them are the authority pushing a screen, and none of them can be a client-side rule.
     */
    val housePushed: Set<ScreenId> = setOf(
        ScreenId.Notify,
        ScreenId.Call, ScreenId.Found,
        ScreenId.ScanCaught, ScreenId.ScanBad, ScreenId.ScanUnknown,
        ScreenId.Revoked, ScreenId.Restrained, ScreenId.Ghost2,
        ScreenId.Disconnect,
        ScreenId.WinInsiders, ScreenId.WinResidents,
    )

    /**
     * **Host-setup screens the port cannot reach, recorded as the gap they are.**
     *
     * `NoTerminal` used to be here beside `TermTaken`, for the same reason: `Editor`'s REVIEW
     * HOME named `SaveName` unconditionally, so a save attempted with no terminal anywhere had
     * nowhere to land. It is routed now — the button asks the plan whether any room holds the T
     * card and goes to the explanation when none does.
     *
     * `TermTaken` was here too, for a gap of the same shape: `ScanMarker`'s DONE always goes to
     * `MarkerSheet`, and the refusal belongs to *scanning a second T card*, which had no scanner
     * behind it. It is routed now, and it is the actions layer's rather than a screen's — a scan
     * is not a tap, and where the host lands depends on what the map says about the card that was
     * read (see [viaActions]).
     *
     * **The list is empty, and that is the state to keep it in.** `FlowTest` fails if it grows
     * without anyone saying so: a screen nothing reaches is a screen drawn for nobody.
     */
    val unrouted: Set<ScreenId> = emptySet()

    /**
     * **The edges the actions layer owns**, because the screen hands over a decision instead of
     * naming a target.
     *
     * Almost every control in the port says where it goes — `goes(Editor)`, `go(Home)` — and
     * `ScreenGraph` reads those straight off the screens. Six do not:
     *
     * - **The plan itself.** A tap on the grid opens the room under it, and on most of a grid
     *   there is no room, so it opens nothing. A screen cannot name that target because the
     *   target is a position.
     * - **The room-type chips.** They call `pickRoomType`, and where that lands depends on
     *   whether the host is about to destroy something: turning an *occupied* room into stairs
     *   unregisters every card in it, because **stairs hold nothing**, and the host is told what
     *   that costs before it happens. An empty room changes type in place with nothing to warn
     *   about, and goes nowhere.
     * - **SAVE HOME.** A save can be refused — an empty name, a name another home holds, a phone
     *   that did not write the file — and a refused save stays where it is with the reason on
     *   screen. A button that navigated away from a refusal would leave the host looking at a
     *   list their house is not in.
     * - **HOLD TO DELETE.** Two seconds of a finger, not a tap, so it publishes no click action
     *   for `ScreenGraphTest` to read.
     * - **A card being read.** Not a tap at all — the camera raises it — and where it lands
     *   depends on what the map says about the card: five of the six outcomes are a line on the
     *   viewfinder and no movement, and the sixth is the terminal already being in another room,
     *   which is a decision the host has to make and therefore a screen.
     *
     * Without these, five screens would read as orphans — drawn and reachable by nothing — while
     * in fact being one gesture away. Written down here rather than smuggled into [ScreenGraph],
     * where they would be claims about controls that do not exist.
     */
    val viaActions: Map<ScreenId, Set<ScreenId>> = mapOf(
        // A tap on the plan, landing on a room.
        ScreenId.Editor to setOf(ScreenId.RoomEdit),
        // The STAIRS chip on an occupied room, and the confirmation that answers it.
        ScreenId.RoomEdit to setOf(ScreenId.StairsWarn),
        ScreenId.StairsWarn to setOf(ScreenId.Editor),
        // A save that landed, and a home the host held a finger on for two seconds.
        ScreenId.SaveName to setOf(ScreenId.HomeDetail),
        ScreenId.Delete to setOf(ScreenId.Maps),
        // The T card, read in a room while the terminal is in another one.
        ScreenId.ScanMarker to setOf(ScreenId.TermTaken),
    )
}

/**
 * The fixture's stand-in for a cause that, in play, arrives with the state.
 *
 * Once a player is out, the status carrier names **what happened to them** rather than which
 * screen they are on — `Revoke` is system power lent by the house, `Restrain` is a physical act
 * by the room, and the two must never be collapsed. That word follows [PanelState.outBy], which
 * the authority sets on the event that put the player there.
 *
 * A phone with no house attached has no such event, so walking onto one of those screens here
 * would render the UNREGISTERED fallback and read as a bug in the chrome rather than as a
 * missing round. This carries the cause the only way a fixture can. **It is not a rule and it
 * decides nothing**: in play [PanelState] arrives whole and this is never consulted.
 */
fun PanelState.arrivingAt(id: ScreenId): PanelState = when (id) {
    ScreenId.Revoked -> copy(screen = id, outBy = OutBy.Revoked)
    ScreenId.Restrained -> copy(screen = id, outBy = OutBy.Restrained)
    else -> copy(screen = id)
}

/**
 * The thing that walks the graph: [PanelState] plus the little that is needed to move it.
 *
 * **[PanelState] stays flat and inert; this sits beside it.** The trail is not game state and is
 * never read by a screen — it exists so that a player who opened Files can shut it again, and for
 * nothing else. In play the house pushes state and this drives nothing; it is what a phone does
 * while no house is attached.
 */
class FlowModel(
    initial: PanelState = PanelState(screen = ScreenId.Boot),
    /**
     * The plan the host is painting.
     *
     * Held here rather than inside a screen because the host walks out of the editor and back in
     * — into the room panel, the marker sheet, the floors list — and a plan that lived in a
     * screen's own `remember` would be repainted from scratch every time they did. Fifteen
     * minutes of walking a house is the thing being protected.
     */
    val editor: HomeEditorModel = HomeEditorModel.bungalow(),
    /**
     * The homes this phone holds.
     *
     * Held here for the reason the editor is: the host walks out of the list into the editor and
     * back, and a list rebuilt from the file on every visit would be a file read on every tap.
     * The app hands this one a real store; every test and every render gets the memory one.
     */
    val homes: SavedHomesModel = SavedHomesModel.sample(),
) {

    var state: PanelState by mutableStateOf(initial)
        private set

    /**
     * Where the player came from, most recent last.
     *
     * **Capped.** A round is twenty-five minutes of tapping around a springboard, and an
     * unbounded list that grows for all of it is an allocation the whole-app budget pays for
     * (~0.5 MB/s, and the lamp must die in the same frame as phone contact). Nobody steps back
     * thirty-two screens; the oldest is dropped.
     */
    private val trail = ArrayDeque<ScreenId>()

    /** What this screen will do on its own, if anything. */
    val pending: AutoAdvance? get() = Flow.autoAdvance[state.screen]

    /** True when [back] would move. False on the house's screens and at the start of the trail. */
    val canGoBack: Boolean get() = state.screen !in Flow.houseDriving && trail.isNotEmpty()

    /**
     * A tap the player made.
     *
     * Leaving a house-driving screen **clears the trail** rather than extending it: the meeting
     * you just walked out of is not somewhere the phone may walk back into.
     */
    fun go(to: ScreenId) {
        if (state.screen in Flow.houseDriving) trail.clear() else remember(state.screen)
        state = state.arrivingAt(to)
    }

    /**
     * The house moved you.
     *
     * Auto-advance calls this, and so will the authority's pushes when there is an authority.
     * The trail goes with it — everything on it described a round that has moved on.
     */
    fun push(to: ScreenId) {
        trail.clear()
        state = state.arrivingAt(to)
    }

    /**
     * Step back, if the house is not driving.
     *
     * Returns false without touching [state] when it is refused, so a caller can let the
     * platform's own gesture fall through to nothing rather than half-happen.
     */
    fun back(): Boolean {
        if (!canGoBack) return false
        state = state.arrivingAt(trail.removeLast())
        return true
    }

    /**
     * Replace the whole panel — the cheat picker's jump, and nothing else.
     *
     * Takes a [PanelState] rather than a [ScreenId] because the picker also sets the role, which
     * no tap can.
     */
    fun jump(to: PanelState) {
        trail.clear()
        state = to
    }

    fun stepRevoke() {
        val next = RevokeState.entries[(state.revoke.ordinal + 1) % RevokeState.entries.size]
        state = state.copy(revoke = next)
    }

    fun toggleMarkers() { state = state.copy(markersOn = !state.markersOn) }

    fun toggleTorch() { state = state.copy(torch = !state.torch) }

    /**
     * A tap on the plan: open the room under the finger.
     *
     * **Nothing happens where there is no room**, and that is the whole reason this edge is the
     * actions layer's rather than the screen's — a screen cannot name a target that depends on
     * where you touched it. Most of a grid is not a room; a tap there is the host aiming at
     * something they have not painted yet.
     */
    fun openRoomAt(cell: Cell) {
        val room = editor.roomAt(cell) ?: return
        editor.open(room.name)
        go(ScreenId.RoomEdit)
    }

    /**
     * The room-type chips, and the place the design hands navigation to the actions layer
     * instead of naming a target on the screen.
     *
     * Turning an **occupied** room into stairs is destructive — stairs hold nothing, so every
     * card registered in that room would belong to no room at all — and the host is told what it
     * costs *before* it happens rather than after. That is why `RoomEdit`'s STAIRS chip calls
     * this rather than `go`, and why the warning screen would otherwise be unreachable.
     *
     * **The type does not change until the warning is answered.** Setting it first and asking
     * afterwards would mean MOVE THEM FIRST returns the host to a room that is already stairs,
     * which is the destructive edit happening before the question about it.
     *
     * **An empty room changes in place, with nothing to warn about.** The port could not express
     * that — its held room always held markers — so the warning was unconditional and a host
     * tagging a bare stairwell was interrogated about cards that were never there. The plan
     * knows now, so the question is only asked when there is an answer worth having.
     */
    fun pickRoomType(kind: RoomKind) {
        val room = editor.heldRoom ?: return
        when {
            kind == room.kind -> Unit
            kind == RoomKind.Stairs && editor.holdsAnything(room.name) ->
                go(ScreenId.StairsWarn)
            else -> editor.setKind(kind)
        }
    }

    /** UNREGISTER AND CONTINUE: the cards are given up and the room becomes stairs. */
    fun confirmStairs() {
        editor.setKind(RoomKind.Stairs)
        go(ScreenId.Editor)
    }

    // ---- Registration ---------------------------------------------------------------------------

    /**
     * **A card was read: nine characters off a symbol, exactly as printed.**
     *
     * This is not a tap and is not in [PanelActions]. A card arriving is the camera's event — in a
     * real build the scanner calls this, in a build without one the playtest deck does — and a
     * screen that could raise it would be a screen claiming to have seen a piece of paper.
     *
     * **Every outcome ends somewhere the host can see.** Only one of them is a screen: the terminal
     * already being in another room, because that is a decision and not a message. Everything else
     * — registered, moved, a shape already taken, stairs, a card this build cannot read — is said
     * on the viewfinder the host is already looking at, and the screen does not move. That is what
     * KEEP SCANNING TO ADD MORE means: the flow does not walk away between cards.
     */
    fun cardScanned(payload: String) {
        when (val read = CardPayload.decode(payload)) {
            // Allowed to be specific (D-071): an unreadable card is a fact about a piece of paper,
            // not a statement about a player, and the host is standing in a lit room holding it.
            is CardPayload.Result.Rejected -> editor.refuseScan(read.why)

            is CardPayload.Result.Read ->
                if (editor.register(read.card) is RegisterResult.TerminalTaken) {
                    go(ScreenId.TermTaken)
                }
        }
    }

    /** MOVE THE TERMINAL TO THIS ROOM, then back to the viewfinder to carry on. */
    fun moveTerminal() {
        editor.moveTerminal()
        go(ScreenId.ScanMarker)
    }

    /** REMOVE IT: the T card belongs to no room, and this home cannot be saved until one does. */
    fun removeTerminal() {
        editor.removeTerminal()
        go(ScreenId.MarkerSheet)
    }

    // ---- The saved homes -------------------------------------------------------------------

    /**
     * A row in the list: that home is now the open one.
     *
     * The editor is **not** loaded here. Opening a home is looking at it, and a host who taps a
     * row and backs out again has not asked for the house they were painting to be thrown away.
     * EDIT THE PLAN and RENAME are the two controls that say they will replace it, and they do.
     */
    fun openSavedHome(name: String) {
        homes.openHome(name)
    }

    /** MAP A NEW HOME: nothing open, an empty grid, one storey, a provisional name. */
    fun mapNewHome() {
        homes.closeHome()
        editor.startNewHome(homes.freeName())
    }

    /** EDIT THE PLAN and RENAME: the open home goes into the editor, whole. */
    fun editOpenHome() {
        homes.open?.let { editor.load(it) }
    }

    /**
     * SAVE HOME.
     *
     * The blank name is refused here rather than in the model, because it is the only refusal the
     * screen's own field can produce and [SavedHome] cannot be built to carry it — a home with no
     * name is not a home that failed to save, it is not a home.
     */
    fun saveHome() {
        val name = editor.name.trim()
        if (name.isEmpty()) {
            homes.refuse("A HOME NEEDS A NAME")
            return
        }
        if (homes.save(editor.asSavedHome(name))) go(ScreenId.HomeDetail)
    }

    /** Two seconds of a finger, and fifteen minutes of walking is gone. */
    fun deleteHome() {
        if (homes.deleteOpen()) go(ScreenId.Maps)
    }

    /** A copy of the open home, opened, on the screen the host is already on. */
    fun duplicateHome() {
        if (homes.duplicateOpen()) editor.load(homes.open ?: return)
    }

    /** Every action a screen can take, wired to this model. */
    fun actions(): PanelActions = PanelActions(
        nav = ::go,
        stepRevoke = ::stepRevoke,
        toggleMarkers = ::toggleMarkers,
        toggleTorch = ::toggleTorch,
        pickRoomType = ::pickRoomType,
        confirmStairs = ::confirmStairs,
        openRoomAt = ::openRoomAt,
        nameRoom = editor::renameHeld,
        deleteRoom = editor::deleteHeld,
        openFloor = editor::openFloor,
        addFloor = { editor.addFloor() },
        moveTerminal = ::moveTerminal,
        removeTerminal = ::removeTerminal,
        forgetMarker = editor::forgetMarker,
        openSavedHome = ::openSavedHome,
        mapNewHome = ::mapNewHome,
        editOpenHome = ::editOpenHome,
        duplicateHome = ::duplicateHome,
        nameHome = editor::nameHome,
        saveHome = ::saveHome,
        deleteHome = ::deleteHome,
    )

    private fun remember(screen: ScreenId) {
        trail.addLast(screen)
        if (trail.size > TRAIL_DEPTH) trail.removeFirst()
    }

    private companion object {
        const val TRAIL_DEPTH = 32
    }
}

/**
 * The device, driving itself.
 *
 * [Screen] draws one panel and knows nothing about what follows it. This is the piece that moves
 * it: taps go through [FlowModel], and the screens that say they will move on do so.
 *
 * **The delay is a local clock, and it is temporary.** When the transport carries a round, the
 * house decides every one of these transitions and pushes the result; the auto-advance table
 * becomes the house's schedule and this coroutine goes away. Until then it is the only thing that
 * makes the ported flows walkable end to end.
 */
@Composable
fun FlowHost(model: FlowModel = remember { FlowModel() }) {
    val pending = model.pending
    val screen = model.state.screen
    LaunchedEffect(screen, pending) {
        if (pending != null) {
            delay(pending.afterMillis.toLong())
            // Only if nothing else moved first. The key above already restarts this effect on
            // every screen change, so reaching here means the screen is still the one that owed
            // the advance.
            if (model.state.screen == screen) model.push(pending.to)
        }
    }
    Screen(model.state, model.actions(), model.editor, model.homes)
}
