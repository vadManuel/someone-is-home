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
        // LIGHTS OUT is not in here: it is a control only while every line is in, so whether the
        // screen publishes it at all depends on a count the house sent (see Flow.viaActions).
        ScreenId.Lobby -> setOf(ScreenId.Secret)

        // Arming. HAND IT OVER is not in here either — a blank line is refused and the refusal
        // stays on this screen, same shape as SAVE HOME.
        ScreenId.Secret -> emptySet()
        ScreenId.Armed -> setOf(ScreenId.Home)

        // The springboard, and the screens that ARE the springboard with something on top.
        // `Quiet`'s banner opens the Subroutines list, which the springboard already reaches, so
        // it adds no edge of its own — the tile and the banner are two doors to one screen.
        ScreenId.Home, ScreenId.Notify, ScreenId.Quiet, ScreenId.EgressWidget -> SPRINGBOARD
        ScreenId.Banner -> SPRINGBOARD + ScreenId.EgressWidget
        ScreenId.Page2 -> setOf(
            ScreenId.Home, ScreenId.Work, ScreenId.Scan, ScreenId.Lock,
            // The Insider's egress tile. A Resident's tap on the same tile does nothing and does
            // it silently — same control, same brightness, no new view for either role to be
            // caught on. This is the only role-asymmetric edge in the graph.
            ScreenId.Banner,
        )
        // The lantern, and the lantern with something under its clock. A notification on a locked
        // phone is READ, not opened: this screen has one control and it is SLIDE TO OPEN, so a
        // card that also navigated would be an unlock nobody performed.
        ScreenId.Lock, ScreenId.LockNotify -> setOf(ScreenId.Home)

        // Work.
        ScreenId.Work -> setOf(ScreenId.Home, ScreenId.Scan)
        ScreenId.Scan -> setOf(ScreenId.Home)
        // NOT THIS ONE, and nothing else: BEGIN names no screen, because which Subroutine opens
        // is a fact about the card that was read. See Flow.viaActions.
        ScreenId.ScanCaught -> setOf(ScreenId.Work)
        // Both refusals, pixel-identical apart from their text, and identical here too. One of
        // them is Resident-only; differing exits would be the tell the shared body prevents.
        ScreenId.ScanBad, ScreenId.ScanUnknown -> setOf(ScreenId.Scan, ScreenId.Work)
        // **Every Subroutine screen has exactly one exit, and it is STOP NOW.** Handing your
        // entry over is not one: whether the work landed is the house's answer, and a screen that
        // walked away on the strength of its own last tap would be this phone announcing a
        // completion it cannot see. The three used to chain into one another — Sub to SubBright to
        // Work — which was a fixture convenience wearing a game route's clothes.
        ScreenId.SubHandshake, ScreenId.SubReplay, ScreenId.SubParity,
        ScreenId.SubShort, ScreenId.SubTrace, ScreenId.SubJam -> setOf(ScreenId.Work)
        ScreenId.Files -> setOf(ScreenId.Home)
        ScreenId.Notes -> setOf(ScreenId.Home)
        ScreenId.TermNo -> setOf(ScreenId.Home, ScreenId.TermLive)
        ScreenId.TermLive -> setOf(ScreenId.Home, ScreenId.Timelapse)
        ScreenId.Timelapse -> setOf(ScreenId.TermLive)
        ScreenId.Reveal -> setOf(ScreenId.Home, ScreenId.RevealThread)
        ScreenId.RevealThread -> setOf(ScreenId.Reveal)
        ScreenId.Settings -> setOf(ScreenId.Home)

        // The room. A line with one way forward and no way out — see Flow.houseDriving — and
        // three of its steps are not tapped at all.
        //
        // I AM HERE, READY TO VOTE and LOCK IN each report ONE PHONE, and what follows depends on
        // every phone in the house: the check-in gate closes when every living player and every
        // out player is standing there (D-104), the talk skips ahead only on a UNANIMOUS ready,
        // and the ballot is read when the window closes. A phone cannot count phones, so none of
        // those three walks anywhere. They echo, the screen goes on waiting, and the house moves
        // everybody at once — as Flow.autoAdvance already has it do.
        //
        // Answering a ring is different and stays an edge: it is a call, and answering it is
        // entirely your own phone's business.
        ScreenId.Calling -> emptySet()
        ScreenId.Call, ScreenId.Found -> setOf(ScreenId.Assemble)
        ScreenId.Assemble -> emptySet()
        ScreenId.Notice -> setOf(ScreenId.Discussion)
        ScreenId.Discussion -> emptySet()
        ScreenId.Vote -> emptySet()
        ScreenId.Tally -> emptySet()

        // Out. The two notices publish no control whatever: nothing further is required of you.
        ScreenId.Revoked, ScreenId.Restrained -> emptySet()
        // The same check-in as the living's, and it moves the same nothing.
        ScreenId.Ghost2 -> emptySet()
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

        // **THE TEN SECONDS A HEAVY NOTIFICATION LASTS (D-119).** The three rows here are the
        // three arrivals that dim the house, and the undim is this transition: the panel comes
        // back up because the screen it lands on has no heavy notification on it, not because
        // anything animates.
        //
        // They are written out rather than derived from `Notifications.arrivals` so that the
        // reader of this table sees them and so that `FlowTest` can check one copy against the
        // other. A derived row would agree with itself about a rule nobody had checked.
        //
        // **There is deliberately no row for `Quiet`.** A quiet notification sits until it is
        // swiped, and this table's fail-closed default — a screen with no row waits — is exactly
        // the behaviour it needs. Adding one would delete the only acknowledgment D-105 left.
        ScreenId.Notify to AutoAdvance(
            ScreenId.Home, HEAVY_HOLD, "the house's opening message clears itself and the light comes back",
        ),
        ScreenId.Banner to AutoAdvance(
            ScreenId.Home, HEAVY_HOLD, "the Egress alert clears itself; the countdown is on the widget",
        ),
        ScreenId.LockNotify to AutoAdvance(
            ScreenId.Lock, HEAVY_HOLD, "the same ten seconds, under the clock, on a phone nobody picked up",
        ),

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
        // place on a phone with no house attached. I AM HERE reports this phone and moves nothing,
        // so this row is the ONLY way off the screen; the design's own device shell auto-advances
        // the same step.
        ScreenId.Assemble to AutoAdvance(ScreenId.Notice, 8_000, "4 OF 6 CHECKED IN — the check-in gate closes"),
        ScreenId.Notice to AutoAdvance(ScreenId.Discussion, 9_000, "notices are shown once at the top of the meeting, then gone"),
        ScreenId.Discussion to AutoAdvance(ScreenId.Vote, 90_000, "the discussion clock; unanimous READY skips ahead"),
        // **45 seconds, which is the design's own number** (`gdd.md:412`, restated at `:1006`).
        // This row said 60 for as long as it existed and nothing in the design ever did — the
        // lobby's settings line said 60S beside it, so the two agreed with each other and with
        // nothing else. The host may now move this in the lobby, but that control is a
        // client-side echo (see [LobbyModel.cycleVoteWindow]) and this table is not wired to it:
        // when the window is really enforced it will be the house's clock, not this one's.
        ScreenId.Vote to AutoAdvance(
            ScreenId.Tally, 45_000, "the vote window closes and the ballot is read",
        ),
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
        // The three notifications nothing on this phone can produce: the house's opening text
        // over the springboard, the same text under the clock on a phone lying face down, and a
        // Subroutine coming unblocked by somebody else's work. The Egress alert is NOT here — an
        // Insider walks to it from their own page 2, which is the one role-asymmetric edge in the
        // game.
        ScreenId.Notify, ScreenId.LockNotify, ScreenId.Quiet,
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
     * `ScreenGraph` reads those straight off the screens. These do not:
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
     * - **HAND IT OVER.** A blank line is refused and the refusal stays on the screen, for the
     *   reason a refused save does: walking away would leave the player believing the house holds
     *   something it does not, with the lobby's own count agreeing with them.
     * - **LIGHTS OUT.** Gated on every line being in, and the gate is a count the *house* sent.
     *   With it closed the screen publishes no control at all, so whether this edge exists on any
     *   given render is not a fact about the screen — which is the definition of an edge that
     *   belongs here. It is also the host's alone; a client's lobby draws a line of text where
     *   the button would be.
     * - **BEGIN, on a caught scan.** Which Subroutine opens is a fact about the card that was just
     *   read, so the screen cannot name a target any more than a tap on the plan can — it is the
     *   same shape as opening the room under a finger. Every *built* Subroutine screen is listed
     *   as a place it can land, because any of them can be behind a BEGIN; a Subroutine whose
     *   interaction does not exist yet opens nothing and the phone stays where it is.
     * - **A banner, swiped up.** D-105's whole gesture vocabulary, and a drag rather than a tap,
     *   so it publishes no click action for `ScreenGraphTest` to fire. Where it lands is the
     *   screen the notification arrived over rather than anything the banner names — the banner
     *   does not know what is behind it, and in the port both arrive over the springboard.
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
        // BEGIN, opening whichever Subroutine this marker holds. Derived from the roster rather
        // than listed, so building a Subroutine cannot leave its screen an orphan.
        ScreenId.ScanCaught to Subroutine.built.mapNotNullTo(mutableSetOf()) { it.screen },
        // A line that was real, handed over; and the lights going out once every line is in.
        ScreenId.Secret to setOf(ScreenId.Lobby),
        ScreenId.Lobby to setOf(ScreenId.Armed),
        // Every notification, swiped away — up on the three banners, left under the clock. The
        // banners arrive over the springboard so all three leave it behind; the lock screen's
        // arrival leaves the lock screen. Each of these is the `under` of an entry in
        // `Notifications.arrivals`, and `FlowTest` walks the gesture on every one of them rather
        // than trusting the two lists to agree.
        ScreenId.Notify to setOf(ScreenId.Home),
        ScreenId.Banner to setOf(ScreenId.Home),
        ScreenId.Quiet to setOf(ScreenId.Home),
        ScreenId.LockNotify to setOf(ScreenId.Lock),
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
    /**
     * The lobby this phone is in.
     *
     * Held here for the reason the two above are: the player walks out of the lobby into the
     * one-line screen and back, and a lobby rebuilt on each visit would re-discover the network
     * and forget what had been typed. The app hands this one a real finder and a real link; every
     * test and every render gets the memory ones.
     */
    val lobby: LobbyModel = LobbyModel.sample(),
    /**
     * What this phone has said at the meeting it is at.
     *
     * Held here for the reason the three above are: the meeting is five screens and the player
     * walks the whole line, and a check-in that lived in a screen's own `remember` would be
     * forgotten the moment the house moved them on from the screen they made it on. It is cleared
     * when a new meeting begins — see [arrive].
     */
    val meeting: MeetingModel = MeetingModel.sample(),
    /**
     * What this phone has entered into the Subroutine it has open.
     *
     * Held here for the reason the four above are: a Subroutine is walked out of and back into —
     * the player stops because somebody came in, waits in the hall, scans again — and an entry
     * that lived in a screen's own `remember` would be gone the moment they looked away. It is
     * cleared when a Subroutine is opened afresh; see [beginSubroutine].
     */
    val subroutines: SubroutineModel = SubroutineModel.sample(),
    /**
     * What is still standing on this phone's lock screen.
     *
     * Held here for the reason the five above are, and more plainly than any of them: the lock
     * screen is walked away from constantly — it is the screen a phone is left on — and a list
     * that lived in the screen's own `remember` would put every swiped notification back the next
     * time the player picked the phone up. **It holds what is still there and nothing else**; see
     * [NotificationsModel] for the fields it deliberately does not have.
     */
    val notifications: NotificationsModel = NotificationsModel(),
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
        arrive(state.arrivingAt(to))
    }

    /**
     * The house moved you.
     *
     * Auto-advance calls this, and so will the authority's pushes when there is an authority.
     * The trail goes with it — everything on it described a round that has moved on.
     */
    fun push(to: ScreenId) {
        trail.clear()
        arrive(state.arrivingAt(to))
    }

    /**
     * Step back, if the house is not driving.
     *
     * Returns false without touching [state] when it is refused, so a caller can let the
     * platform's own gesture fall through to nothing rather than half-happen.
     */
    fun back(): Boolean {
        if (!canGoBack) return false
        arrive(state.arrivingAt(trail.removeLast()))
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
        arrive(to)
    }

    /**
     * Land on a screen, whichever way the phone got there.
     *
     * The one thing that happens on every arrival: **a new meeting starts with nothing said.** A
     * round holds several meetings and [meeting] outlives all of them, so a check-in made at the
     * first would still be lit at the second — a phone telling a player they are already standing
     * at the meeting area they have this second been called to.
     *
     * Keyed on arriving at one of [MeetingModel.STARTS] rather than on leaving the last screen,
     * because a meeting has four ways in and no screen knows which of them ended.
     */
    private fun arrive(next: PanelState) {
        if (next.screen != state.screen && next.screen in MeetingModel.STARTS) meeting.meetingBegan()
        state = next
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

    // ---- The lobby -------------------------------------------------------------------------

    /** A tap on a network row: attach to that home, and go into its lobby. */
    fun attachToHome(home: NearbyHome) {
        lobby.attachTo(home)
    }

    /**
     * HAND IT OVER.
     *
     * Refused when the line is blank, and a refusal stays on the screen with the reason — the
     * same shape as [saveHome], and for the same reason: walking away from a refused hand-over
     * would leave the player believing the house holds something it does not, with the lobby's
     * count agreeing with them.
     */
    fun handOverLine() {
        if (lobby.handOverLine()) go(ScreenId.Lobby)
    }

    /**
     * **LIGHTS OUT — presentation only, and this is the line it stops at.**
     *
     * It walks this phone to the ARMED screen and does nothing else. It does not lock the seat
     * ledger, draw the Insider count, stamp the balance values into a recording, start a clock or
     * tell another phone anything. Every one of those is arming, arming belongs to the loop, and
     * the loop is frozen — so what exists here is the transition, and the round behind it does
     * not exist yet. In play this button becomes an intent the house answers by pushing a screen
     * to everybody at once, which is why nothing about it may be decided on a device.
     */
    fun lightsOut() {
        push(ScreenId.Armed)
    }

    // ---- The meeting -------------------------------------------------------------------------

    /**
     * **The four meeting controls, and what every one of them does: light itself.**
     *
     * They are here rather than on [MeetingModel] directly for one reason — so that the place a
     * screen's tap arrives is the same place every other tap arrives, and so that this file is
     * where somebody looks to check that none of them navigates. None of them does. The gates they
     * report to are counts of every phone in the house (D-104's check-in, the unanimous READY, the
     * vote window closing), and a device that moved itself on the strength of its own button press
     * would be predicting an outcome it cannot see — which is the leak surface `ui ↛ core` exists
     * to delete, arriving through the navigation layer instead of through the rules.
     */
    fun checkIn() = meeting.checkIn()

    fun sayReady() = meeting.sayReady()

    fun chooseVote(choice: VoteChoice) = meeting.choose(choice)

    fun lockInVote() = meeting.lockIn()

    // ---- Subroutines ---------------------------------------------------------------------------

    /**
     * **BEGIN: open whichever Subroutine the marker you just scanned holds.**
     *
     * Here rather than on the screen because the screen cannot name the target — the same reason
     * a tap on the plan is here. A Subroutine with no interaction built yet opens **nothing** and
     * the phone stays on the caught scan, which is the fail-closed direction: the failure mode is
     * *it did not open*, noticed immediately, rather than *it opened somebody else's work*.
     *
     * The entries are cleared on the way in. A round has several visits to the same marker — you
     * stop because somebody walked in, you come back — and an entry left standing would hand a
     * player a sequence they part-returned ten minutes ago as though it were still live.
     *
     * **The parameter is the card's answer, and the default is the fixture's.** In play the
     * scanned marker says which Subroutine opens; on a phone with no house attached there is one
     * Subroutine and it is [PanelVals.CURRENT]. Written as a parameter rather than read straight
     * off the fixture so that the port's single-marker limitation is visible in the signature
     * instead of being a thing you discover when a second marker exists.
     */
    fun beginSubroutine(subroutine: Subroutine = PanelVals.CURRENT.subroutine) {
        val screen = subroutine.screen ?: return
        subroutines.beganAgain()
        go(screen)
    }

    /**
     * **A finger landing on a dot, a cell, or the one big button — and that is all it is.**
     *
     * It echoes. It does not compare what was entered with anything, because there is nothing on
     * this device to compare it with (see [SequenceEntry]); the sequence goes to the house as an
     * Intent and the house is what verifies it (D-042). Both roles arrive here, through the same
     * screens, into the same entries: an Insider's Subroutine is a fake in the ledger and nowhere
     * else, which is what rule 8 means by *the fake is not a backlog item*.
     *
     * A Subroutine with no entry behind it is ignored rather than crashing. Six of the ten are
     * unbuilt and none of them has a screen, so nothing can reach this with one — but a `when`
     * that threw would turn a routing mistake into a dead phone in a dark house, and rule 6 is
     * that errors are silent to the player.
     */
    fun tapSubroutine(subroutine: Subroutine, at: Int) = subroutines.tap(subroutine, at)

    /**
     * The entry goes to the house — SUBMIT, or on Short the two seconds running out.
     *
     * A sequence hands itself over when its last element goes in: there is nothing left to change,
     * so there is nothing to confirm. Everything else can be moved right up until it is sent,
     * which is the vote's shape and the vote's reason.
     *
     * The dispatch is [SubroutineModel.handOver] rather than a second `when` here, for the reason
     * given there: two copies of it is how a Subroutine gets a screen and no wiring.
     */
    fun handOverSubroutine(subroutine: Subroutine) = subroutines.handOver(subroutine)

    // ---- Notifications -----------------------------------------------------------------------

    /**
     * **Something arriving, swiped away (D-105, D-119).**
     *
     * What is left behind is the screen it arrived over — the springboard for the three banners,
     * the lock screen for the one under the clock — which is [Arrival.under] and not a constant.
     * The panel comes back up out of [NOTIFIED_DIM] because the screen it lands on has no heavy
     * notification on it, in the same step the navigation happens.
     *
     * **A quiet notification swiped here is gone from the lock screen too.** One acknowledgment,
     * one gesture: a player who dismissed the banner and then found the same sentence waiting
     * under their clock would learn that the swipe means nothing, which is the only thing D-105
     * left that means anything.
     *
     * **Nothing is written down.** Not which notification it was, not that it was dismissed, not
     * when. There is no read concept and this is the method that would grow one first: a `seen`
     * set here is three months from being a count on a tile. What survives a notification is the
     * surface that already held the thing — Messages, the Egress widget, the Subroutines list —
     * and a house notice survives nowhere at all, which is [NotificationKind.heldBy].
     *
     * A swipe on a screen with nothing on it does nothing rather than navigating somewhere, which
     * is the fail-closed direction: a gesture the panel cannot service must not move the phone.
     */
    fun dismissNotification() {
        val arrival = Notifications.arrivals[state.screen] ?: return
        notifications.dismiss(arrival.notification)
        go(arrival.under)
    }

    /**
     * **One of the lock screen's stored notifications, swiped left off the list.**
     *
     * No navigation: the player is still looking at their lock screen, with one fewer thing on it.
     * That is the whole of it, and the whole of it is the point — this is the acknowledgment
     * D-105 left in place of read state, and an acknowledgment that moved the phone would be a
     * gesture people avoid making.
     */
    fun dismissStanding(notification: Notification) = notifications.dismiss(notification)

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
        nameHome = editor::nameHome,
        saveHome = ::saveHome,
        deleteHome = ::deleteHome,
        nameResident = lobby::nameResident,
        hostHome = lobby::hostHome,
        attachToHome = ::attachToHome,
        typeLine = lobby::typeLine,
        handOverLine = ::handOverLine,
        cycleInsiders = lobby::cycleInsiders,
        cycleVoteWindow = lobby::cycleVoteWindow,
        lightsOut = ::lightsOut,
        checkIn = ::checkIn,
        sayReady = ::sayReady,
        chooseVote = ::chooseVote,
        lockInVote = ::lockInVote,
        beginSubroutine = { beginSubroutine() },
        tapSubroutine = ::tapSubroutine,
        handOverSubroutine = ::handOverSubroutine,
        dismissNotification = ::dismissNotification,
        dismissStanding = ::dismissStanding,
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
    Screen(
        model.state, model.actions(), model.editor, model.homes, model.lobby, model.meeting,
        model.subroutines, model.notifications,
    )
}
