package home.someoneshome.ui

import home.someoneshome.model.CardPayload
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Winner
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
        // REVIEW HOME reaches BOTH: the plan is reviewable once the home passes D-127's gate,
        // and until then the same button goes to the screen that names what it is short of. The
        // plan itself is not in here — a tap on it lands on whichever room is under it, which
        // is an actions-layer decision (see Flow.viaActions).
        ScreenId.Editor -> setOf(
            ScreenId.Maps, ScreenId.Floors, ScreenId.SaveName, ScreenId.ReviewNeeds,
        )
        ScreenId.RoomEdit -> setOf(ScreenId.MarkerSheet, ScreenId.Editor)
        ScreenId.StairsWarn -> setOf(ScreenId.RoomEdit, ScreenId.MarkerSheet)
        ScreenId.MarkerSheet -> setOf(
            ScreenId.TermRemove, ScreenId.MeetRemove, ScreenId.ScanMarker, ScreenId.RoomEdit,
        )
        ScreenId.ScanMarker -> setOf(ScreenId.MarkerSheet)
        ScreenId.TermTaken, ScreenId.MeetTaken -> setOf(ScreenId.ScanMarker)
        ScreenId.TermRemove, ScreenId.MeetRemove -> setOf(ScreenId.MarkerSheet)
        // OPEN A ROOM goes back to the plan, not into the room panel: the host has to choose
        // WHICH room each missing card goes in, and the plan is the only screen that can ask.
        ScreenId.ReviewNeeds -> setOf(ScreenId.Editor)
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
        // The page dots and the dock, and nothing else. **The two ability tiles are holds** (D-141)
        // and publish no click action, so the Insider's egress edge is not readable off this screen
        // and lives in [Flow.viaActions] with the other gestures. A Resident's tiles are not
        // controls at all (D-142) — no pointer input, nothing to fire, nothing to time.
        ScreenId.Page2 -> setOf(ScreenId.Home, ScreenId.Work, ScreenId.Scan, ScreenId.Lock)
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
        //
        // **SNIFF's is drawn in black until the answer has been given, and it is still this
        // one.** A screen whose exit moved because it is unlit would be a different screen at
        // the moment the player most needs the one they already know; the control keeps its
        // words, its place and its destination, and only its colour changes. See [SniffScreen].
        ScreenId.SubHandshake, ScreenId.SubReplay, ScreenId.SubParity,
        ScreenId.SubShort, ScreenId.SubTrace, ScreenId.SubJam,
        ScreenId.SubSniff, ScreenId.SubDeallocate,
        // **INTERRUPT and DRIFT have a running clock behind them and it is still this one edge.**
        // Neither screen ever walks off on its own: D-139's sweep has no timeout to walk off at,
        // and D-140's buzz asks a question rather than ending one. The only way out of either is
        // the player pressing STOP NOW or the house answering what they sent.
        ScreenId.SubInterrupt, ScreenId.SubDrift -> setOf(ScreenId.Work)
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
        // A notice is swiped away, not dismissed by a button (D-105, D-119) — so the walk to the
        // discussion is a drag and lives in [Flow.viaActions]. The house's own nine seconds goes
        // to the same place, which is what makes swiping *going first* rather than going somewhere.
        ScreenId.Notice -> emptySet()
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

        // **The endings are no longer terminals** (D-157) — but NEW ROUND is not in here, for
        // LIGHTS OUT's reason exactly: whether the screen publishes it as a control at all depends
        // on whether this phone is running the house, and that is not a fact a screen graph can
        // hold. It is in [Flow.viaActions], where the other host-only control already is.
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

/**
 * **A transition the AUTHORITY makes, and the message that carries it.**
 *
 * Beside [AutoAdvance] because it is the other half of the same question — *what moves this screen
 * on* — and the whole difference between them is who does the moving.
 *
 * [on] is a [MessageKind] rather than a sentence, so a row names a message that either exists in
 * the emit schema or does not: `FlowTest` checks every one against `EmitSchema.knownKinds()`, and
 * a push waiting on a kind nobody emits fails the build instead of stranding a player on a screen
 * forever. It is the strongest thing this module can say about the authority without importing it.
 */
data class HousePush(val to: ScreenId, val on: MessageKind, val why: String)

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

        // **The ring, and it is the last of the meeting's timers.** Answering a call is your own
        // phone's business, so the moment it stops ringing is a presentation number like the
        // notification hold above. Everything past it is the house's and lives in [housePushes].
        ScreenId.Calling to AutoAdvance(ScreenId.Assemble, 6_000, "WAITING FOR THE REST — the last phone answers"),
        ScreenId.Call to AutoAdvance(ScreenId.Assemble, 6_000, "the ring; a house meeting cannot be declined"),
        ScreenId.Found to AutoAdvance(ScreenId.Assemble, 6_000, "the same ring, different header"),

        // House notices are shown once at the top of the meeting and then gone. This is the one
        // step inside the meeting that really is presentation: the talk's clock started when the
        // gate closed, and how long a notice sits on screen changes nothing the house is counting.
        ScreenId.Notice to AutoAdvance(ScreenId.Discussion, 9_000, "notices are shown once at the top of the meeting, then gone"),
    )

    /**
     * **The meeting's own transitions, every one of them a push.**
     *
     * These were rows in [autoAdvance] with delays on them, which was the honest thing to do while
     * nothing could send them and was labelled as a stand-in from the day it was written. D-134's
     * E8-2 recorded all four as authority pushes *so nobody later reads the missing edges as an
     * omission*, and the rules now make them: the gate closing, the talk ending or a unanimous
     * READY arriving, the vote window closing, and the ghost walk-in.
     *
     * **Why they cannot be timers.** Every one of them is a count of phones — every living player
     * and every out player standing at the meeting area (D-104), every hand up, every ballot
     * locked — and a phone cannot count phones. A device that ran its own 90 seconds would move a
     * player on while the room was still talking, and six devices would do it at six different
     * moments.
     *
     * **The delays that are gone were not a loss of information.** Where the design carries a
     * number it still does: the vote window is 45 seconds, host-changeable in lobby settings
     * (D-117), and it is the house's clock that runs it — this table never was where that lived.
     */
    val housePushes: Map<ScreenId, HousePush> = mapOf(
        ScreenId.Assemble to HousePush(
            ScreenId.Notice, EmitSchema.MEETING_PHASE_OPENED,
            "4 OF 6 CHECKED IN — the gate closes when the LAST player walks in (D-104)",
        ),
        ScreenId.Discussion to HousePush(
            ScreenId.Vote, EmitSchema.MEETING_PHASE_OPENED,
            "the discussion clock runs out, or every hand goes up — both are the house's to see",
        ),
        ScreenId.Vote to HousePush(
            ScreenId.Tally, EmitSchema.MEETING_PHASE_OPENED,
            "the window closes and the ballot is read; unanimous READY closes it early (D-117)",
        ),
        ScreenId.Tally to HousePush(
            ScreenId.Home, EmitSchema.MEETING_ENDED,
            "LIGHTS OUT reaches zero and everybody still in the round goes back to it",
        ),

        // The same meeting from outside the system. A player who is out walks in, watches, and
        // gets the outside view only once the meeting has ended — by which time the room already
        // knows they are out, so there is never a window where they know something the living do
        // not.
        ScreenId.Ghost2 to HousePush(
            ScreenId.GhostMeeting, EmitSchema.MEETING_PHASE_OPENED,
            "4 OF 6 CHECKED IN — the same gate, from outside, because it is one gate",
        ),
        ScreenId.GhostMeeting to HousePush(
            ScreenId.Ghost3, EmitSchema.MEETING_ENDED,
            "the meeting ends and the couch gets the ballot with names against it (D-075)",
        ),

        // **The ending is deliberately NOT in here** — see [endings], which explains why it cannot
        // be. It is the one push in the game that does not care what screen you are standing on.
    )

    /**
     * **The ending: the one push that can arrive on any screen in the game** (D-131, D-157).
     *
     * ### Why it is not a row in [housePushes], and must not become one
     *
     * That table is keyed on **the screen you are standing on**, because every push in it answers
     * a screen that is explicitly waiting — the assembly gate closing, the ballot being read, the
     * couch being let in. The ending answers nothing. All four of D-131's routes can land while a
     * player is anywhere: on the springboard between meetings when the meter reaches nothing, at
     * the tally when the room Restrains the last Insider, at a node with a countdown on it, inside
     * a Subroutine, on the lock screen with the phone in a pocket. A row per screen would be
     * thirty rows saying the same thing and one screen somebody forgot.
     *
     * It would also not *fit*: `Tally` already has a row, and a second one keyed on the same
     * screen replaces the first silently.
     *
     * ### Keyed on the winner, because that is what decides the screen
     *
     * The destination is carried by the message — `Effect.RoundEnded` names the winner — and this
     * is the two-entry table that turns it into a screen. Keyed on anything else it would push
     * every phone in the house to the same ending regardless of who took the round, which is the
     * one fact the whole evening was about.
     *
     * **PERIMETER DISARMED rides the Resident entry and nothing else.** Landing on `WinResidents`
     * is what flips [PanelVals.disarmedGlyph], and the status row that has read `ARMED` for
     * twenty-five minutes changes for everybody at once (`gdd.md:203`).
     */
    val endings: Map<Winner, HousePush> = mapOf(
        Winner.Residents to HousePush(
            ScreenId.WinResidents, EmitSchema.ROUND_ENDED,
            "the meter reached nothing, or the room ran out of Insiders — the perimeter disarms",
        ),
        Winner.Insiders to HousePush(
            ScreenId.WinInsiders, EmitSchema.ROUND_ENDED,
            "parity, or an Egress that ran its clock out uncontained (D-131)",
        ),
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
        // **The meeting, past the ring.** Each of these was reachable by a timer and is now
        // reachable only by the house — see [housePushes] for which effect carries which. They are
        // named here as well because this set is the honest answer to *why can I not walk there?*,
        // and the cheat picker reads it.
        ScreenId.Notice, ScreenId.Vote, ScreenId.Tally,
        ScreenId.GhostMeeting, ScreenId.Ghost3,
        ScreenId.Disconnect,
        ScreenId.WinInsiders, ScreenId.WinResidents,
    )

    /**
     * **Host-setup screens the port cannot reach, recorded as the gap they are.**
     *
     * `ReviewNeeds` — `NoTerminal`, as it was called then — used to be here beside `TermTaken`,
     * for the same reason: `Editor`'s REVIEW HOME named `SaveName` unconditionally, so a save
     * attempted by a home that could not be hosted had nowhere to land. It is routed now — the
     * button asks the home what it is short of and goes to the explanation when the answer is not
     * "nothing".
     *
     * `TermTaken` was here too, for a gap of the same shape: `ScanMarker`'s DONE always goes to
     * `MarkerSheet`, and the refusal belongs to *scanning a second T card*, which had no scanner
     * behind it. It is routed now, and it is the actions layer's rather than a screen's — a scan
     * is not a tap, and where the host lands depends on what the map says about the card that was
     * read (see [viaActions]). `MeetTaken` arrived already routed, by the same edge.
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
     * - **The two ability tiles on page 2.** Both arm on a two-second hold (D-141), so neither
     *   publishes a click action; the egress tile is the one of them that walks anywhere. It is
     *   also the only role-asymmetric edge in the game, and the asymmetry is capability rather
     *   than appearance — a Resident's tiles are drawn identically and take no pointer input at
     *   all (D-142), which is why nothing here is reachable by rendering the Resident and firing
     *   everything on the screen.
     * - **A house notice, swiped up.** The last button dismissal in the app, retired: the swipe
     *   is the acknowledgment (D-119) and there is no read state for a control to claim (D-105).
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
        // Either reserved card, read in a room while this home already has one somewhere else.
        ScreenId.ScanMarker to setOf(ScreenId.TermTaken, ScreenId.MeetTaken),
        // BEGIN, opening whichever Subroutine this marker holds. Derived from the roster rather
        // than listed, so building a Subroutine cannot leave its screen an orphan.
        ScreenId.ScanCaught to Subroutine.built.mapNotNullTo(mutableSetOf()) { it.screen },
        // A line that was real, handed over; and the lights going out once every line is in.
        ScreenId.Secret to setOf(ScreenId.Lobby),
        ScreenId.Lobby to setOf(ScreenId.Armed),
        // The Insider's egress tile, armed by two seconds of a finger (D-141). **The only
        // role-asymmetric edge in the game**, and it is asymmetric in capability rather than in
        // appearance: a Resident's tile is drawn identically and takes no pointer input at all
        // (D-142), so there is no hold to run and no view either role can be caught on.
        ScreenId.Page2 to setOf(ScreenId.Banner),
        // A house notice, swiped up off the meeting.
        ScreenId.Notice to setOf(ScreenId.Discussion),
        // Every notification, swiped away — up on the three banners, left under the clock. The
        // banners arrive over the springboard so all three leave it behind; the lock screen's
        // arrival leaves the lock screen. Each of these is the `under` of an entry in
        // `Notifications.arrivals`, and `FlowTest` walks the gesture on every one of them rather
        // than trusting the two lists to agree.
        ScreenId.Notify to setOf(ScreenId.Home),
        ScreenId.Banner to setOf(ScreenId.Home),
        ScreenId.Quiet to setOf(ScreenId.Home),
        ScreenId.LockNotify to setOf(ScreenId.Lock),
        // **NEW ROUND, off both endings, and it is here rather than in the graph for LIGHTS OUT's
        // reason** (D-157). Whether either ending publishes it as a control at all depends on
        // whether this phone is running the house — present and inert with HOST ONLY beside it
        // otherwise — and *is this the host* is not a fact a screen graph can hold. The lobby's
        // arming control sits three lines up for exactly the same reason.
        ScreenId.WinInsiders to setOf(ScreenId.Lobby),
        ScreenId.WinResidents to setOf(ScreenId.Lobby),
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
    // The verdict is dropped on every arrival, this one included. It belongs to one instance of
    // one Subroutine: carried across a navigation it would sit under the next piece of work, and
    // carried into a re-scan it would be the phone answering an entry nobody has made yet.
    ScreenId.Revoked -> copy(screen = id, outBy = OutBy.Revoked, verdict = null)
    ScreenId.Restrained -> copy(screen = id, outBy = OutBy.Restrained, verdict = null)
    else -> copy(screen = id, verdict = null)
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

    /** What the house will do to this screen, if anything. Nothing here fires it — see [FlowHost]. */
    val awaitingPush: HousePush? get() = Flow.housePushes[state.screen]

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

    /**
     * **Arming a Revoke — two seconds of a finger on page 2** (D-141, D-142).
     *
     * Presentation only, and the line it stops at is [lightsOut]'s: it walks this phone's tile
     * through the states the design drew and tells nobody anything. Real arming is silent and
     * invisible, spends the cooldown at the moment of arming rather than at the landing, and has
     * no cancel — all three of which are the house's, because a device that decided any of them
     * would be a device deciding an outcome.
     *
     * **Reached only from an Insider's tile.** Not by a branch inside it: a Resident's tile takes
     * no pointer input at all, so this is not something a Resident's phone declines to do — it is
     * something a Resident's phone has no way to ask for.
     */
    fun stepRevoke() {
        val next = RevokeState.entries[(state.revoke.ordinal + 1) % RevokeState.entries.size]
        state = state.copy(revoke = next)
    }

    /**
     * **Arming an Egress — the other two-second hold on page 2** (D-141).
     *
     * The misfire the design calls *"a game-ending misclick [that] will happen in the dark"* is
     * the whole reason for the hold. What it reaches here is the alert every phone in the house
     * gets; in play the alert is the house's answer to an intent, not this phone's announcement.
     */
    fun armEgress() {
        go(ScreenId.Banner)
    }

    /**
     * **A house notice, swiped up** (D-105, D-119).
     *
     * The last button dismissal in the game, retired: the swipe is the acknowledgment, and there
     * is nothing anywhere in this app that records one. A notice is held nowhere afterwards, so
     * dismissing it is a navigation and no more than that.
     */
    fun dismissNotice() {
        go(ScreenId.Discussion)
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
     * **Every outcome ends somewhere the host can see.** Only two of them are screens: a reserved
     * card — the terminal's or the meeting card's — already being in another room, because that is
     * a decision and not a message. Everything else — registered, moved, a shape already taken,
     * stairs, a card this build cannot read — is said on the viewfinder the host is already looking
     * at, and the screen does not move. That is what KEEP SCANNING TO ADD MORE means: the flow does
     * not walk away between cards.
     *
     * The `when` is over the map's own answers and is exhaustive, so an outcome added later cannot
     * arrive as a screen that does not move.
     */
    fun cardScanned(payload: String) {
        when (val read = CardPayload.decode(payload)) {
            // Allowed to be specific (D-071): an unreadable card is a fact about a piece of paper,
            // not a statement about a player, and the host is standing in a lit room holding it.
            is CardPayload.Result.Rejected -> editor.refuseScan(read.why)

            is CardPayload.Result.Read -> when (editor.register(read.card)) {
                is RegisterResult.TerminalTaken -> go(ScreenId.TermTaken)
                is RegisterResult.MeetingTaken -> go(ScreenId.MeetTaken)
                // Said on the viewfinder, which the host is already looking at.
                is RegisterResult.Registered, is RegisterResult.Moved,
                is RegisterResult.ShapeAlreadyRegistered, is RegisterResult.StairsHoldNothing,
                null,
                -> Unit
            }
        }
    }

    /** MOVE THE TERMINAL TO THIS ROOM, then back to the viewfinder to carry on. */
    fun moveTerminal() {
        editor.moveTerminal()
        go(ScreenId.ScanMarker)
    }

    /** MOVE THE MEETING CARD TO THIS ROOM, then back to the viewfinder to carry on. */
    fun moveMeeting() {
        editor.moveMeeting()
        go(ScreenId.ScanMarker)
    }

    /** REMOVE IT: the T card belongs to no room, and this home cannot be saved until one does. */
    fun removeTerminal() {
        editor.removeTerminal()
        go(ScreenId.MarkerSheet)
    }

    /** REMOVE IT: the meeting card belongs to no room, and there is nowhere to call a meeting. */
    fun removeMeeting() {
        editor.removeMeeting()
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

    /**
     * **NEW ROUND — back to the lobby, with the lines gone** (D-157, D-116).
     *
     * Presentation and one deletion, and the line it stops at is [lightsOut]'s line. It walks this
     * phone to the lobby and wipes what this phone typed. It does not redraw roles, reset a
     * cooldown, clear the meter or tell another phone anything: **roles are redrawn at the next
     * arming**, which is arming's job, and every other reset falls out of `Rules.armed`
     * constructing a round rather than copying one. In play the host's tap is an intent the house
     * answers by pushing every phone to the lobby at once.
     *
     * **What survives is the home, the seats and the settings.** The home was walked once, ever,
     * and it would be absurd to walk it again at midnight; the seats are who is in the house; the
     * settings are what the host tuned. So this calls neither `attach` nor `leave` — a return to
     * the lobby that dropped the connection would be an evening that ends after one round with
     * five people re-typing a lobby code in the dark.
     *
     * **[LobbyModel.roundEnded] is the deletion and it is called here rather than trusted to the
     * ending screen**, because a promise kept by whichever composable happens to be on screen is
     * a promise that lapses the day somebody adds a second route to the lobby.
     */
    fun newRound() {
        lobby.roundEnded()
        push(ScreenId.Lobby)
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

    fun readyToVote() = meeting.readyToVote()

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

    /**
     * **The house answered (D-109). The one push that lands on a Subroutine screen.**
     *
     * A verdict is not a navigation and does not move the phone: the player is still standing at
     * the marker, and where they go next is their decision — STOP NOW, or the walk back to the
     * work order. **A rejected entry does not re-arm anything here either**, which is D-110 read as
     * a thing this method does *not* do: the controls stay inert because the entry is still handed
     * over, and the only way back to ready is [beginSubroutine], which is a fresh scan.
     *
     * Separate from [push] because it changes no screen, and separate from the entry model because
     * the entries hold this phone's own hand and this is the house's answer. In play it arrives at
     * the effect boundary like every other field of [PanelState]; until there is a house, it is the
     * seam a test drives.
     */
    fun houseGraded(verdict: SubroutineVerdict) {
        state = state.copy(verdict = verdict)
    }

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
        armEgress = ::armEgress,
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
        moveMeeting = ::moveMeeting,
        removeMeeting = ::removeMeeting,
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
        newRound = ::newRound,
        checkIn = ::checkIn,
        sayReady = ::sayReady,
        chooseVote = ::chooseVote,
        readyToVote = ::readyToVote,
        beginSubroutine = { beginSubroutine() },
        tapSubroutine = ::tapSubroutine,
        handOverSubroutine = ::handOverSubroutine,
        dismissNotification = ::dismissNotification,
        dismissNotice = ::dismissNotice,
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
 *
 * ### [standingInForTheHouse] is a debug harness and must never be true in a real round
 *
 * The meeting's own transitions are [Flow.housePushes] and there is no house attached to this
 * module yet — so on a bench the meeting stops at the check-in screen, correctly and forever. That
 * is the truth, and it is also unwalkable, which is how a ported flow stops being looked at.
 *
 * So the playtest and debug roots pass `true` and get a **stand-in for the authority**: after
 * [HOUSE_STAND_IN_MS] it fires whatever push the current screen is waiting on. It is deliberately
 * one flat number for every push rather than a schedule — a schedule would be a table of meeting
 * timings living in `ui`, which is the thing that was just deleted, and it would be believed.
 */
@Composable
fun FlowHost(
    model: FlowModel = remember { FlowModel() },
    standingInForTheHouse: Boolean = false,
) {
    val pending = model.pending
    val awaiting = if (standingInForTheHouse) model.awaitingPush else null
    val screen = model.state.screen
    LaunchedEffect(screen, pending, awaiting) {
        // Only if nothing else moved first. The keys above already restart this effect on every
        // screen change, so reaching either branch means the screen is still the one that owed it.
        if (pending != null) {
            delay(pending.afterMillis.toLong())
            if (model.state.screen == screen) model.push(pending.to)
        } else if (awaiting != null) {
            delay(HOUSE_STAND_IN_MS)
            if (model.state.screen == screen) model.push(awaiting.to)
        }
    }
    Screen(
        model.state, model.actions(), model.editor, model.homes, model.lobby, model.meeting,
        model.subroutines, model.notifications,
    )
}

/**
 * How long the bench pretends the house takes. **Not a game number and not a design one.**
 *
 * Every real one of these is a count of phones or the house's own clock — the check-in gate, the
 * discussion, the vote window, the LIGHTS OUT countdown — and none of them is four seconds. This
 * is long enough to read a screen and short enough to walk a meeting, and it exists only so that
 * somebody looking at layout on a bench can get past the check-in screen.
 */
const val HOUSE_STAND_IN_MS: Long = 4_000L
