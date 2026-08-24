package home.someoneshome.ui

import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.OrderLine
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.Balance
import home.someoneshome.model.CardPayload
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Winner
import home.someoneshome.model.Cell
import home.someoneshome.model.RegisterResult
import home.someoneshome.model.RoomKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage

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
        // HOST WITH THIS HOME is not in here: a home the radio refuses never goes on the air and
        // the host stays on this screen with the reason (E-D3-2), so where that button lands
        // depends on an answer the screen does not have (see Flow.viaActions).
        ScreenId.HomeDetail -> setOf(
            ScreenId.Maps, ScreenId.Editor, ScreenId.SaveName, ScreenId.Delete,
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
        // **The two Tier-2 pickers.** BACK is a plain tap and is the only edge either of them
        // publishes: a row FIRES, which walks back to page 2 without naming it — see
        // [Flow.viaActions], where firing is declared with the other gestures. A picker that could
        // only be left by firing would be a control that costs a cooldown to change your mind about.
        //
        // **Static is not here because it has no screen** (D-186): its tile fires in place, so the
        // third tier-2 ability publishes no edge at all.
        ScreenId.SurgeTargets, ScreenId.IsolateTargets -> setOf(ScreenId.Page2)
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
        //
        // **The edge itself is the actions layer's now, and the reason is that STOP NOW stopped
        // being only a navigation** (E-P2-5). It reports the performance window closing to the
        // house — `Intent.StopSubroutine`, which the presence plane needs in order to expire a
        // placement (D-111, D-136) — so the control publishes an action rather than a target, and
        // `ScreenGraphTest` can no longer read the destination off the screen. It is declared in
        // [Flow.viaActions] with the other edges whose screen hands over rather than names.
        ScreenId.SubHandshake, ScreenId.SubReplay, ScreenId.SubParity,
        ScreenId.SubShort, ScreenId.SubTrace, ScreenId.SubJam,
        ScreenId.SubSniff, ScreenId.SubDeallocate,
        // **INTERRUPT and DRIFT have a running clock behind them and it is still this one edge.**
        // Neither screen ever walks off on its own: D-139's sweep has no timeout to walk off at,
        // and D-140's buzz asks a question rather than ending one. The only way out of either is
        // the player pressing STOP NOW or the house answering what they sent.
        ScreenId.SubInterrupt, ScreenId.SubDrift -> emptySet()
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

/**
 * **The Surge this phone is inside — the two luminances the rules sent, and which Surge it is.**
 *
 * A key for [FlowHost]'s sequence rather than something a screen reads: [FlowModel.surge] is not
 * drawn anywhere, and the light itself lives in [LampState] where no recomposition can be between
 * it and the glass (rule 7).
 *
 * **[nth] exists so that two identical Surges are two Surges.** `Effect.Surged` carries the same
 * peak and the same black every time — they are constants in the rules — so without a counter a
 * second Surge landing during the first one's five seconds would be an equal value, would not
 * restart the `LaunchedEffect`, and the phone would come back on the first Surge's clock while
 * still holding the second one's dark.
 */
data class SurgeSlam(val peak: Int, val black: Int, val nth: Int)

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
        // pacing: ten seconds, then the light goes dark and the phone goes back where it was. Nobody
        // should be standing in a dark room holding a lit screen at a wall by accident. Derived
        // from the design's own segment count rather than restated, so the bar and the timeout
        // cannot disagree.
        //
        // **It is a window, not a held button** (D-159). The player tapped SCAN and turned the
        // lamp onto the code; the read opens the Subroutine by itself, and this row is what
        // happens when no read arrives. The phone says so with D-160's double vibrate, which is
        // the app's to play — `ui` has no motor, and this table has never been where a buzz
        // lives (D-134).
        ScreenId.Scan to AutoAdvance(
            ScreenId.Home, PanelVals.SCAN_WINDOW_MILLIS, "the scan window, two segments a second",
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

        // **The three ring screens answer the same push, and they have to** — found by routing
        // `MeetingOpened` and watching a phone strand itself.
        //
        // The ring is a presentation timer (`autoAdvance`, six seconds) and the gate is a count of
        // phones, so nothing makes them agree: in a house where everybody else is already standing
        // at the meeting card, the gate can close while one phone is still ringing. Without these
        // rows that phone missed the phase push, stood on a ring nobody was making, fell through to
        // `Assemble` on its own timer, and waited there for a gate that had already closed — which
        // is E-P3-2's *a meeting that can never leave its first phase* arriving from the other end.
        //
        // **They go straight to `Notice` rather than through `Assemble`.** Walking a phone to the
        // assembly screen after the gate has closed would show a player a check-in they no longer
        // need to make, with a count that is already full.
        ScreenId.Call to HousePush(
            ScreenId.Notice, EmitSchema.MEETING_PHASE_OPENED,
            "the gate closed while this phone was still ringing — the talk has started without it",
        ),
        ScreenId.Found to HousePush(
            ScreenId.Notice, EmitSchema.MEETING_PHASE_OPENED,
            "the same ring, the same gate, the same catch-up",
        ),
        ScreenId.Calling to HousePush(
            ScreenId.Notice, EmitSchema.MEETING_PHASE_OPENED,
            "you called it and the room filled before your own ring stopped",
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
        // HOST WITH THIS HOME, once the home is really on the air. A refused advertisement stays
        // on the detail screen with the reason under the buttons — including the RENAME row that
        // answers the refusal a host is most likely to get (E-D3-2).
        ScreenId.HomeDetail to setOf(ScreenId.Lobby),
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
        ScreenId.Page2 to setOf(
            ScreenId.Banner,
            // **Two of the three tier-2 tiles**, armed by the same two seconds of a finger (D-141)
            // and opening a list rather than arming in place — the owner's targeting rulings, and
            // the one place the design asks for a picker. *The glow of reading the list is the
            // deliberate price.*
            //
            // **The third opens nothing** (D-186). Static has no target, so its tile takes the same
            // hold and then fires, which is `Page2`'s two Tier-1 tiles' behaviour rather than its
            // two pickers'. Asymmetric in capability and never in appearance: a Resident's three
            // tiles are drawn identically and take no pointer input at all (D-142), so nothing here
            // is reachable by rendering the Resident and firing everything on screen.
            ScreenId.SurgeTargets, ScreenId.IsolateTargets,
        ),
        // Firing a Tier-2 ability, which walks back to page 2 without the row naming it: what a row
        // publishes is a target off a list the house sent, and where the phone lands afterwards is
        // the actions layer's. It is also the shape that keeps `gdd.md:398` — the walk is identical
        // whether the shot landed, so nothing about the navigation is feedback.
        ScreenId.SurgeTargets to setOf(ScreenId.Page2),
        ScreenId.IsolateTargets to setOf(ScreenId.Page2),
        // **STOP NOW, off every Subroutine screen.** It is here rather than in the graph because
        // the control publishes an action rather than a target: it reports the performance window
        // closing to the house before it navigates (`Intent.StopSubroutine`, D-111), so what a
        // screen offers is *tell the house I have stopped* and where that lands is this layer's.
        ScreenId.SubHandshake to setOf(ScreenId.Work),
        ScreenId.SubReplay to setOf(ScreenId.Work),
        ScreenId.SubParity to setOf(ScreenId.Work),
        ScreenId.SubShort to setOf(ScreenId.Work),
        ScreenId.SubTrace to setOf(ScreenId.Work),
        ScreenId.SubJam to setOf(ScreenId.Work),
        ScreenId.SubSniff to setOf(ScreenId.Work),
        ScreenId.SubDeallocate to setOf(ScreenId.Work),
        ScreenId.SubInterrupt to setOf(ScreenId.Work),
        ScreenId.SubDrift to setOf(ScreenId.Work),
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
    /**
     * **Where a Tier-2 request goes, and it does not carry a seat that says who asked**
     * (the 2026-08-22 targeting ruling).
     *
     * [MeetingModel.send]'s arrangement, one page over, and every line of that KDoc applies. The
     * meeting's four controls publish a seat-free [MeetingRequest] and this publishes an
     * [IntentMessage] directly, which is the stronger of the two shapes: `IntentMessage` **cannot**
     * name an actor at all, so there is no discipline for anything here to keep. The seat is stamped
     * at the host's desk from the connection the bytes arrived on.
     *
     * The seats that do ride up are **content** — who the Surge is aimed at, off a roster the house
     * drew and published to this phone — which is the same rule `SelectVote` carries a target under.
     *
     * Defaulted to nothing, so every render and every screenshot gets a phone whose page 2 is real
     * and whose requests reach nobody. That is the honest state of a bench: **a control that
     * predicted an outcome would be worse than one that did nothing**, and nothing on any screen in
     * this app moves because a Tier-2 ability was fired.
     */
    val send: (IntentMessage) -> Unit = {},
) {

    var state: PanelState by mutableStateOf(initial)
        private set

    /**
     * **The lamp, and it is deliberately not a field of [state]** (rule 7, D-182).
     *
     * `LampSet` and `Surged` both land here rather than on [PanelState], because a `copy` is a
     * recomposition and the lamp must go dark in the same frame as phone contact. Written straight
     * through by [houseSaid], read only inside a draw lambda. See [LampState].
     */
    val lamp: LampState = LampState()

    /**
     * **The Surge this phone is inside, or null** (D-182).
     *
     * The five seconds have to be *run* — bright, then dark, then the Boot render — and running
     * them needs a clock, which `ui` has exactly one honest source of: a `LaunchedEffect` in
     * [FlowHost], keyed on this. So this is the key rather than a copy of the light: the two
     * luminances are the ones the rules chose and they are on the effect, and [FlowHost] hands each
     * of them to [lamp] in turn. **Nothing draws this field.**
     *
     * A second Surge arriving mid-sequence restarts the sequence on the new one, because it is a
     * new key — which is the honest picture of a phone surged twice in five seconds, and is
     * `staticJam`'s arrangement one ability over.
     */
    var surge: SurgeSlam? by mutableStateOf(null)
        private set

    /**
     * Where the player came from, most recent last.
     *
     * **Capped.** A round is twenty-five minutes of tapping around a springboard, and an
     * unbounded list that grows for all of it is an allocation the whole-app budget pays for
     * (~0.5 MB/s, and the lamp must go dark in the same frame as phone contact). Nobody steps back
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
        val beginning = next.screen != state.screen && next.screen in MeetingModel.STARTS
        if (beginning) {
            meeting.meetingBegan()
            // **The couch's ballot is a meeting's, like everything else the model above clears.**
            // Both the accumulator and the drawn rows go, or the couch would open the second
            // meeting of a round looking at the first one's completed vote — which is worse than a
            // fixture, because it is a real vote that really happened to these people.
            selections.clear()
        }
        state = if (beginning) next.copy(ballots = null) else next
    }

    // ---- The Surge, five seconds of it (D-182) ---------------------------------------------------

    /**
     * **A Surge landed on this phone: the light goes to full in this call** (D-182).
     *
     * The slam is written straight to [lamp] here rather than after a frame, because the design's
     * word for it is *instantaneous and loud* and a state field would put a recomposition between
     * the house's word and the light (rule 7). What [FlowHost] then runs is the rest of the five
     * seconds — dark, the Boot render, and the phone coming back.
     *
     * **Both luminances come off the effect** and neither is chosen here. Rule 5: the rules authored
     * the light, the device performs it.
     */
    fun surged(peak: Int, black: Int) {
        lamp.slamTo(peak)
        surge = SurgeSlam(peak = peak, black = black, nth = (surge?.nth ?: 0) + 1)
    }

    /**
     * Phase two: **the light goes dark.** A second and a half after the slam, per D-182's table.
     *
     * Checked against the Surge currently running rather than assumed, exactly as [staticLapsed]
     * checks its jam: a second Surge arriving mid-sequence must not have its bright phase cut short
     * by the first one's timer.
     */
    fun surgeDarkened(slam: SurgeSlam) {
        if (surge == slam) lamp.slamTo(slam.black)
    }

    /**
     * Phase three: **the phone comes back up, and the work in your hands is gone** (D-182).
     *
     * The lamp is handed back — what is underneath is the Boot screen, drawn at whatever
     * [LampState.authorised] the house last sent, which is the design's *returns to its last
     * authorised `LampSet`* rather than to a value this module invented.
     *
     * **The open Subroutine instance is lost here**, which is the ruling's second half and not an
     * accident of navigation. `gdd.md:594`'s *interruptible — aborts cleanly with unambiguous
     * progress state* is satisfied and the state is **gone**: preserving progress across a Surge
     * would make the attack survivable by standing still, and it would need a resume path, which is
     * a second way into a Subroutine and the one thing D-110 spent a ruling closing. One attempt per
     * scan; the walk back is the cost, and a Surge buys the attacker that walk.
     *
     * The Boot render is the **cold-start one, unmodified**. A bespoke *you have been surged* screen
     * would be a caption on the event, readable by the person it happened to and by anybody glancing
     * over their shoulder — and a surged phone and a phone that genuinely crashed should be the same
     * picture. Nothing new is drawn, so there is nothing new to keep in parity.
     */
    fun surgeRebooted(slam: SurgeSlam) {
        if (surge != slam) return
        lamp.slamTo(null)
        subroutines.beganAgain()
        subroutines.opened(null)
        push(ScreenId.Boot)
    }

    /**
     * **Five seconds are up and the phone is a phone again.**
     *
     * It lands on the springboard rather than walking the cold-start line the Boot screen normally
     * falls through to (`Flow.autoAdvance`): a phone that came back from a Surge asking for
     * permissions would be a phone that lost its round, and the round is still running. **The
     * springboard is where a rescan starts**, which is what the lost instance now requires.
     *
     * ⚠️ The landing screen is the build's and not the ruling's — D-182 sizes the downtime and says
     * what it costs, and does not say which screen the phone comes back on. Flagged.
     */
    fun surgeEnded(slam: SurgeSlam) {
        if (surge != slam) return
        surge = null
        push(ScreenId.Home)
    }

    /**
     * **The Static window ran out on this phone** (D-186).
     *
     * Called by [FlowHost] after the number of seconds the house sent with the jam, and by nothing
     * else. **It is not the phone deciding the jam is over** — the house set the deadline when it
     * opened the window and this is the phone's copy of it expiring; there is no message reporting
     * the end, because a message whose arrival said *the monitoring is back* would be a second thing
     * to time and a second thing to miss.
     *
     * [jam] is checked against the one currently held rather than assumed, so a Static that arrived
     * during another's window cannot be ended by the older one's timer. A jam already cleared —
     * by a meeting call, which is D-185's — leaves this a no-op.
     */
    fun staticLapsed(jam: StaticJam) {
        if (state.staticJam == jam) state = state.copy(staticJam = null)
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
     * **SURGE, fired at the seat whose row was tapped** (`gdd.md:313`, `:342`, `:392`).
     *
     * [row] is a position in [PanelState.roster], and turning it into a `Seat` is a lookup against
     * the same list the house sent — `MeetingRequest.asMessage(names)`'s move exactly, and lawful
     * for its reason: names are round-scoped and arrive from the house (D-115), so the index a row
     * sits at *is* the seat. **The list is never sorted or filtered anywhere in this module**, which
     * is what makes that true and is written down on the field.
     *
     * ### It walks back to page 2 and reports nothing
     *
     * *No actor-side feedback on firing any ability. The attacker's device stays inert; all
     * observable effect happens on the target* (`gdd.md:398`). So there is no verdict here, no
     * confirmation line, nothing set on [PanelState], and — the part that matters — **no branch**:
     * this method does the same thing whoever was tapped, whether they are already Revoked, and
     * whether the pool was ready. What the phone's own tile draws afterwards is the house's answer
     * (`Effect.AbilityFired`), which is input echo about this phone's own clock.
     *
     * **Reached only from an Insider's tile**, and not by a check inside it: a Resident's tier-2
     * tiles take no pointer input, so a Resident never opens the picker (D-142). [stepRevoke]'s
     * shape, one tier down.
     */
    fun fireSurge(row: Int) {
        send(IntentMessage.FireSurge(target = Seat(row)))
        go(ScreenId.Page2)
    }

    /**
     * **ISOLATE, fired at the card whose row was tapped** (`gdd.md:315`, D-150).
     *
     * [card] is a printed marker id off [PanelState.isolateTargets], which is the list the house
     * offered this phone — so this reports a **choice among what the house sent** rather than a
     * claim about anything. **Nothing here checks the card against an Egress**: the node exclusion is
     * the house's, it arrives as a shorter list, and a client that re-derived it would be a device
     * deciding what an ability may be fired at.
     *
     * Walks back to page 2 and reports nothing, for [fireSurge]'s reason.
     */
    fun fireIsolate(card: String) {
        send(IntentMessage.FireIsolate(target = MarkerId(card)))
        go(ScreenId.Page2)
    }

    /**
     * **STATIC, fired in place from the tile that was held** (D-186).
     *
     * **It carries nothing, and there is nothing it could carry.** Static has no target — no seat,
     * no card, no room — so this method takes no argument, reads no state, and cannot be wrong about
     * what it asked for. It is the narrowest control in this file.
     *
     * ### It does not navigate, and that is the difference from the two pickers
     *
     * [fireSurge] and [fireIsolate] walk back to page 2 because the finger that fired them was on a
     * picker. This finger is on page 2 already, so **the screen does not move** — which is the two
     * Tier-1 tiles' behaviour (D-142) and is what makes the row's three tiles indistinguishable in
     * capability from across a dark room.
     *
     * ### Nothing is drawn afterwards and there must be nothing to draw
     *
     * [fireSurge]'s paragraph. **No branch of any kind**: this method does the same thing whether
     * the pool was ready, whether the house is already jammed, and whoever the actor is. The house's
     * answer is [RoundMessage.StaticOpened], which arrives on *every* living phone in the building
     * including this one — so what this phone shows is a bystander's view of a jam, and there is
     * nothing on it that says the thumb that started it was this one (`gdd.md:398`).
     *
     * **Reached only from an Insider's tile**, and not by a check inside it — a Resident's tier-2
     * tiles take no pointer input at all (D-142).
     */
    fun fireStatic() {
        send(IntentMessage.FireStatic)
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

    /**
     * **STOP NOW: the window shuts, the house is told, and the phone goes back to the work order.**
     *
     * The report goes first and the navigation second, which is the order that survives a phone
     * being stopped between them: a window reported shut on a phone that never reached the list is
     * recoverable, and a phone on the list with a window the house still thinks is open leaves a
     * placement standing in a room nobody is in (D-136).
     *
     * It goes to the work order rather than the springboard — you came from the list and the list
     * is where the next decision is.
     */
    fun stopSubroutine() {
        subroutines.stopped()
        go(ScreenId.Work)
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

    /**
     * **HOST WITH THIS HOME — and the lobby only opens once the home is on the air** (E-D3-2).
     *
     * Refusable, the same shape as [saveHome] and [handOverLine]: an advertisement the OS turns
     * down is a home no other phone can find, and walking into a lobby over one would be this
     * phone showing a party that cannot happen. The reason stays on the screen the host is
     * already on — where the RENAME row is, which is the fix for the refusal they will actually
     * get.
     */
    fun hostHome(homeName: String) {
        lobby.hostHome(homeName) { go(ScreenId.Lobby) }
    }

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
    fun beginSubroutine(
        subroutine: Subroutine = PanelVals.CURRENT.subroutine,
        /**
         * **Which card opened this**, so the hand-over can say so (E-P2-1).
         *
         * **Null by default, because a bench has no card.** The fixture's `CurrentSubroutine`
         * carries a `MarkerShape` — the printed symbol a player looks for — and not a card id,
         * which is the right thing for it to carry and the wrong thing to send. So a phone with no
         * house attached opens the work, takes the answer, and posts it nowhere; see
         * `SubroutineModel.posted`, which refuses a hand-over with no marker.
         *
         * What fills it is the scan: `Effect.ScanAnswered` names the instance the card opened, and
         * the scanner is what knows which card was read. That is the sender this parameter is
         * waiting for, and it is named here rather than defaulted around.
         */
        marker: MarkerId? = null,
    ) {
        val screen = subroutine.screen ?: return
        subroutines.beganAgain()
        subroutines.opened(marker)
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
     * that threw would turn a routing mistake into a flat phone in a dark house, and rule 6 is
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
        houseSaid(RoundMessage.SubroutineGraded(accepted = verdict == SubroutineVerdict.Accepted))
    }

    // ---- The house, speaking ---------------------------------------------------------------------

    /**
     * **The one consumer: everything the house pushes lands here, whichever way it arrived.**
     *
     * There is exactly one of these and there must never be a second. A message that came over a
     * socket and a message handed straight in are the same object by the time they reach this
     * method — `EffectPushTest` runs one effect sequence both ways and demands the two
     * [PanelState]s are equal — so *what a push does to a phone* cannot drift between the bench and
     * a real round. That is the whole reason [houseGraded] now goes through here rather than
     * setting a field of its own.
     *
     * ### It decides nothing
     *
     * Every branch below either copies a value the house sent onto [PanelState] or moves the screen
     * by a rule that was already written down — [Flow.housePushes], keyed on the screen that is
     * waiting, and [Flow.endings], keyed on the winner the message names. Nothing here asks a game
     * question, and nothing may: `ui` cannot see `core`, and a consumer that decided what a message
     * *meant* would be the rules living on the device in their most plausible costume.
     *
     * ### The `when` is exhaustive, and most of it does nothing yet
     *
     * A kind with no field on [PanelState] and no route in either table is a **named no-op with the
     * reason at the branch**, never a missing case and never an `else`. The fail-closed direction
     * for a client is *the screen did not change*, noticed in thirty seconds; the direction a
     * default branch fails in is *something happened and nobody knows which message did it*. The
     * branches that do nothing are the honest map of what the surfaces still owe.
     */
    fun houseSaid(message: RoundMessage) {
        when (message) {
            // **The lamp is not on [PanelState]** — it is `ui/Lamp.kt`'s, written from the effect
            // rather than copied into a field, so that nothing here can be stale at the one moment
            // the whole anonymity guarantee rests on. Routing it through a state field would put an
            // extra frame between phone contact and the light going dark, and rule 7 says that frame is
            // not a dropped frame, it is an un-anonymised revoke.
            //
            // **What reads it is the lock screen** (the owner's routing table): *locked IS the
            // lamp, and the house sets the level, not you.* `LockScreen` draws its amber ground
            // inside a draw lambda off [LampState.authorised], so a level arriving costs one draw
            // invalidation and no recomposition — which is the same path a blackout takes, on
            // purpose, because the blackout is the one that must not miss.
            is RoundMessage.LampSet -> lamp.houseSet(message.luminance)

            // Input echo already lit this phone's own tile (D-141); the house's answer confirms the
            // cooldown, and the cooldown widget has no field here yet. E3's work. **It is the only
            // thing a Tier-2 fire sends back to the actor** (`gdd.md:398`), so a branch that grew a
            // field here would be the first actor-side feedback in the game.
            is RoundMessage.AbilityFired -> Unit

            // **THE SURGE LANDING ON THIS PHONE, and it is five seconds of the phone malfunctioning**
            // (D-182: 1.5s at full, 1.0s black, 2.5s of the Boot render, no input at any point, and
            // the open Subroutine instance lost).
            //
            // **The light goes to full in this call and not a frame later.** It has no field on
            // [PanelState] and must not acquire one — `LampSet`'s argument three branches up, and
            // sharper here: a state field would put a recomposition between the house's word and
            // the glass, on the one effect whose whole point is that it is instantaneous and loud
            // (rule 7). Both luminances are the **rules'** (rule 5); nothing here picks a value and
            // nothing here fades between two.
            //
            // The rest of the sequence needs a clock, which is [FlowHost]'s — see [surged], and
            // [SurgeSlam] for why two identical Surges have to be two different keys.
            //
            // **Five seconds is the ability.** `gdd.md:342` calls the Surge a commitment rather than
            // a tool; the half that was never sized was how long the victim stays out of the game. A
            // one-second flare is a startle. Five is a person standing in the dark holding a defunct
            // rectangle while everybody in the building walks toward the light they just saw.
            //
            // ⚠️ **The sound still has no route and nothing to route TO**: `Speaker` is a stub for
            // the device pass and every actual of it is silent. The loudest half of the loudest
            // ability in the game is a decision taken with two phones in a dark room. Flagged.
            is RoundMessage.Surged -> surged(peak = message.peak, black = message.black)

            // **The Isolate picker's list, straight onto the panel.** Copied and never filtered: the
            // Egress-node exclusion is the house's (D-150) and arrives as a shorter list, and a
            // client that re-derived it would be two rules about one ability with the quieter one
            // winning the day they disagreed.
            is RoundMessage.IsolateTargetsOffered ->
                state = state.copy(isolateTargets = message.targets)

            // **STATIC: the house telling every living phone in the building that it has gone
            // blind** (D-186). Copied onto the panel whole and never filtered, for the Isolate
            // list's reason and one sharper: there is nothing on it to filter. It carries no seat,
            // so this branch cannot tell a jam this phone caused from one it did not, and neither
            // can the screen underneath it.
            //
            // **It lands the same on the firing Insider's phone as on a Resident's**, which is what
            // `gdd.md:398` asks for and is why the fire itself sets nothing. The window runs itself
            // off (`FlowHost`), which is the ring's presentation timer one message over.
            is RoundMessage.StaticOpened ->
                state = state.copy(staticJam = StaticJam(message.seconds, message.seed))

            is RoundMessage.SubroutineGraded -> state = state.copy(
                verdict = if (message.accepted) SubroutineVerdict.Accepted else SubroutineVerdict.Rejected,
            )

            // **The ledger's raw count, to the out — turned into a percentage here and drawn as
            // nothing else** (the owner's routing table; D-103, D-153).
            //
            // The count itself never reaches a screen and must not: `seats × METER_PER_SEAT` is
            // public arithmetic, so a meter drawn as `28 / 42` hands the one reader with nothing
            // else to do all evening the Insider count by division. What lands is
            // [PanelState.integrityPercent] — the same field `SystemIntegrityLive` writes, so the
            // couch's bar and the figure beside it stay one value rather than two agreeing by hand
            // (revision 21's `14 / 32`, and C2's `66%` over a bar lit to 21).
            //
            // **The arithmetic is `Balance.integrityPercent` and there is nowhere else it may
            // live.** `model` holds it, `ui` may see `model`, and the alternative — dividing here —
            // would be a second copy of D-103's rule that agreed with the first until somebody
            // moved one.
            //
            // **The denominator is the house's or there is none.** [PanelState.seats] is null until
            // `CheckInProgressed` has said how many seats the round has, and a phone that has not
            // been told **leaves the bar where it is**. That is the fail-closed direction: a meter
            // that moved against a guessed denominator would be a wrong number that looked exactly
            // like a right one, on the one screen a player who is out watches for a whole round.
            //
            // ⚠️ The seat total only arrives at the first meeting, so a phone put outside the system
            // before one has a bar that does not move until then. It is a carrier gap rather than a
            // routing one — no message before `CheckInProgressed` carries a seat count — and it is
            // flagged rather than closed by inventing one.
            is RoundMessage.SubroutineProgressed -> state.seats?.let { seats ->
                state = state.copy(
                    integrityPercent = Balance.integrityPercent(message.remaining, seats),
                )
            }

            // One field for both meter kinds, deliberately. *Whether it moves live or once a
            // meeting is not this field's business* — a phone draws whatever it was last told, and
            // that is precisely what batched-then-frozen looks like from here.
            is RoundMessage.SystemIntegrityLive -> state = state.copy(integrityPercent = message.percent)
            is RoundMessage.SystemIntegrityBatched -> state = state.copy(integrityPercent = message.percent)

            // **A later text from the house: a quiet banner, and it stands until it is swiped**
            // (D-105, D-119). Quiet because *the house talking is not the round turning over, and a
            // second dim would spend the first one's meaning* — so no dim, no buzz, and no
            // ten-second life. It goes on the lock screen because a player walking back from a
            // marker has to be able to find the thing again.
            //
            // **Stored first, then the banner.** The banner is a screen and the standing list is
            // where the thing survives; raising the banner first would leave a phone whose player
            // swiped it in the same frame with nothing anywhere to have swiped.
            is RoundMessage.MessageDelivered -> {
                notifications.houseSent(Notification(NotificationKind.Text, body = message.body))
                arrived(ScreenId.Quiet)
            }

            is RoundMessage.WorkOrderIssued -> state = state.copy(order = message.lines)

            // **THE SCAN'S ANSWER, and it lands on the caught scan rather than in a Subroutine**
            // (the owner's routing table; D-124, D-188). One flow and one route: scan → caught, with
            // the briefing on it → BEGIN → the work.
            //
            // **It deliberately does not open the Subroutine itself.** Which screen a card holds is
            // [beginSubroutine]'s answer off the roster, driven by the player's own BEGIN; a push
            // that opened the work here as well would be two routes to one Subroutine with one of
            // them untested — and it would take away the last screen a player can still say NOT
            // THIS ONE on, in a game where a scan can catch the wrong card in the dark.
            //
            // **The null is the refusal and it has its own screen** (D-124). A registered card the
            // house has nothing for *you* at is this message with nothing in it, and it is answered
            // by NOTHING FOR YOU HERE. Unregistered paper never reaches the rules at all and is
            // answered by the client's own alert — collapsing the two would let a Resident sweeping
            // cards separate *registered but not mine* from *not registered at all*.
            //
            // **The row is joined here rather than sent twice.** The house names the entry, the kind
            // and the parameters; the work order it already sent names where that entry is and
            // whether it is done. Both are this seat's own and both came from the house, and the
            // index the join is made on is the house's too — so this is a copy of two pushed facts
            // and not a derivation. An answer naming an entry no order line holds moves nothing,
            // which is fail-closed: the phone stays on the scan rather than opening a card the
            // house cannot place.
            is RoundMessage.ScanAnswered -> when (val opened = message.opened) {
                null -> push(ScreenId.ScanBad)
                else -> currentFrom(opened)?.let { current ->
                    state = state.copy(current = current)
                    push(ScreenId.ScanCaught)
                }
            }

            // **Permitted to nobody, so it cannot arrive** — the presence plane has no allowlist row
            // (D-111). Named here for the reason it is named in `EmitSchema.kindOf`: naming grants
            // nothing, and a missing branch is a compile error somebody fixes with whatever is
            // nearest.
            is RoundMessage.PresenceChanged -> Unit

            // **The house's opening text — the heavy one, and the round starting** (D-118, D-119).
            // Heavy is the ruling and not the volume: the whole house dims at the same moment, and
            // *that says exactly what it is.* It is one of exactly two arrivals permitted to do it.
            //
            // **It carries no body**, which is why nothing is stored: the opening text is the
            // house's own and the same on every phone, so `Notifications.arrivals` already holds
            // what the banner says and the message's job is to say *now*. A body on this kind would
            // be a second copy of one sentence.
            is RoundMessage.OpeningMessage -> arrived(ScreenId.Notify)

            // **What this phone is, from the house** (`gdd.md:923`, E-R2-2). One field copied onto
            // [PanelState], which is all this branch may do and all it needs to.
            //
            // **Before this, `role` was set by the cheat picker and by nothing else in the app**,
            // so every phone in a real round drew the Resident's page 2 — including the Insider's.
            // Page 2's tiles are drawn on `role` and the Isolate picker is reachable only from the
            // Insider's, so the one Insider-only list the house really did send arrived on a phone
            // with no way to open it. A whole half of the game was unreachable behind a field
            // nothing wrote.
            //
            // **It decides nothing**, like every branch here. Whether this seat is an Insider is
            // the house's to know and the house's to say; `ui` cannot see `core` and could not work
            // it out. What the field then does — identical tiles at rest, capability under the
            // thumb — is D-142's, written where the page is drawn.
            //
            // **No screen moves.** The reveal is text on a thread the player opens (`gdd.md:923`),
            // and the opening banner one branch up is what says *now*. A navigation here would be
            // this phone doing something the Resident's phone does not, in the light, with the
            // whole party standing together — which is the tell the parity discipline exists for.
            is RoundMessage.RoleRevealed -> state = state.copy(
                role = if (message.insider) PanelRole.Insider else PanelRole.Resident,
            )

            // **Who is in the round, in seat order — and it is the field four screens were reading
            // and nobody was writing** (D-115, E-R2-6, C3·4).
            //
            // [PanelState.roster] had no writer anywhere in the app. `nameOf` is a lookup against
            // it, so in a real round every lookup returned null: the ring drew the port's PRIYA,
            // the tally drew the port's MARCUS, and the Surge picker offered the port's five names
            // to a phone in a house with five other people in it. **One branch closes all of them**,
            // because they were never four gaps — they were one missing carrier read four ways.
            //
            // **The seat total rides the same list** (C3·4). `PanelState.seats` is the denominator
            // `SubroutineProgressed` turns a raw count into a percentage against, and it arrived
            // only at the first meeting on `CheckInProgressed` — so a player put outside the system
            // before one had a bar that did not move. The roster is one entry per seat, so its
            // LENGTH is that number, it is a public lobby fact, and it now arrives at arming. Written
            // in the same assignment as the roster for `CheckInProgressed`'s reason exactly: one
            // branch writes both, so they cannot drift.
            //
            // **It decides nothing and derives nothing.** The house sent this list in this order;
            // reading it is the same act as drawing it. What `ui` may not do is *assemble* one —
            // out of a lobby standing, or by sorting names it happens to hold — and nothing here
            // does. See [nameOf], where that distinction is written down.
            is RoundMessage.RosterIssued -> {
                // **The ballot is written from the same list in the same branch**, which is
                // `CheckInProgressed`'s arrangement and its reason: one writer, so the vote screen
                // and [PanelState.roster] cannot come to hold two different orderings of the room.
                // `MeetingModel.houseHolds` resolves a seat through it by index, so a second copy
                // that drifted would light a row against somebody else's ballot.
                meeting.houseNamed(message.roster)
                state = state.copy(roster = message.roster, seats = message.roster.size)
            }

            // **The ring — and this is the push that makes a round visible at all** (the owner's
            // routing table, 2026-08-23). It closes the half of E-P3-1 that mattered most: a client
            // took a hundred pushes across a complete round and stood on the lobby for all of them,
            // because this branch did nothing.
            //
            // **Found when the meeting was triggered by a report, Call otherwise.** Same layout,
            // different reason, which is what the two screens are: a house meeting cannot be
            // declined either way, and what the header says is *why the phone is ringing*. Read off
            // the trigger and off nothing else — the same split the admission gate makes (D-133),
            // asked here for a header instead of for a refusal.
            //
            // **It reaches the living only.** A seat outside the system gets `StandAndWalkIn`
            // instead (D-134) and never this, which is the allowlist's doing rather than a branch
            // here: *the ringing call is for the living.*
            //
            // **The calling seat is routed by `MeetingCalling`, four branches down** (E-R1-1). This
            // branch deliberately does not try: `caller` is a `Seat` and this phone does not know
            // which seat it is, so the comparison has one operand and always will. The caller lands
            // here with everybody else, gets its name and its jam cleared like everybody else, and
            // is then moved on by a message addressed to it alone.
            is RoundMessage.MeetingOpened -> {
                // The caller's name before the screen, so the ring is drawn with it on the first
                // frame rather than one recomposition later. D-121: the meeting has an author.
                //
                // **The jam is dropped in the same assignment** (D-185: *a meeting call ends every
                // running ability effect*). Not this phone deciding a rule — the house has already
                // cleared its own deadline, and no message reports that because there is no surface
                // left to clear: page 2 does not exist during a meeting and the Terminal is never
                // readable at one. This is the phone's copy of a window the house has ended, thrown
                // away in the frame it is told why.
                state = state.copy(caller = nameOf(message.caller), staticJam = null)
                push(
                    when (message.trigger) {
                        is MeetingTrigger.RevokeReported -> ScreenId.Found
                        is MeetingTrigger.MeetingCard -> ScreenId.Call
                    },
                )
            }

            // **You called it — the one phone in the house that gets its own screen** (E-R1-1, the
            // owner's 2026-08-23 ruling). It arrives immediately after `MeetingOpened`, so this
            // phone has already been rung, has already been given the caller's name and has already
            // dropped its jam; what this does is move it off the ring everybody else is on.
            //
            // **It is keyed on nothing and carries nothing**, which is `RestrainedTakeover`'s shape
            // and its argument: the message means *you*, and the way it means *you* is that the
            // house sent it down this socket and no other. There is nothing on it a phone could
            // read a seat number out of, so routing it cannot become a phone knowing which seat it
            // is — the thing the wire is shaped to prevent.
            //
            // `Calling` already has its row in `Flow.housePushes` (the room filled before your own
            // ring stopped) and its six-second fall-through in `autoAdvance`, so nothing else about
            // the meeting changes for this seat.
            is RoundMessage.MeetingCalling -> push(ScreenId.Calling)

            // **The other half of the same call, and it is what closes E-P3-2** (D-104, D-134).
            //
            // The gate counts *every living player and every out player*, and an out seat gets this
            // instead of a ring. With no route it never reached a screen offering a check-in, so
            // the gate stopped at 4 of 5 and every transition after it was refused
            // `WrongMeetingPhase`, on five phones, in silence — a meeting that could never leave its
            // first phase with nothing anywhere saying why.
            //
            // **The control was already there.** `Ghost2Screen` draws the living's own `CheckIn()`
            // and has since the out screens were ported; what was missing was any way to arrive on
            // it. That is worth being exact about, because the escalation reads as though a control
            // needed building and it did not.
            //
            // **Every out seat lands here, the newly Revoked and the couch alike**, and the couch
            // does not stay: `Flow.housePushes` already walks `Ghost2 → GhostMeeting` when the phase
            // opens. Sending the previously-out straight to the couch instead would be this phone
            // deciding it need not check in, which is the gate the rules are counting.
            is RoundMessage.StandAndWalkIn -> push(ScreenId.Ghost2)

            // **The gate and the hands, on the screens that were drawing fixtures of them.**
            // `4 OF 6 CHECKED IN` was a constant on a phone in a house where six people were
            // standing in the hall. The count is the house's and moves only here — see
            // `MeetingModel.counts`, whose setter is private for exactly this reason.
            //
            // **`expected` is also the round's seat total, and it is written twice on purpose.**
            // The meeting model gets it as the gate's denominator; [PanelState.seats] gets it as
            // *the number the house counts against*, which is what turns `SubroutineProgressed`'s
            // raw count into a percentage. One branch writes both, so they cannot drift — and the
            // second is nullable where the first has a fixture default, which is the difference
            // that stops a bench's six becoming a real round's denominator.
            is RoundMessage.CheckInProgressed -> {
                meeting.houseCounted(present = message.present, seats = message.expected)
                state = state.copy(seats = message.expected)
            }

            // **`expected` is the LIVING and not the seats**, which is why it is not written to the
            // same total the gate's is: readiness is the living's and presence is everybody's
            // (D-147), so a ready count of `3 OF 6` in a house where two players are out would be
            // counting a hand nobody has. The screen draws its own fraction, so what it needs is
            // the denominator the rule counted against.
            is RoundMessage.ReadyProgressed ->
                meeting.houseCounted(ready = message.ready, living = message.expected)

            is RoundMessage.MeetingPhaseOpened -> {
                state = state.copy(
                    meetingPhase = message.phase,
                    // Null on every phase but the ballot's, and null must not overwrite a window
                    // the house already gave this phone: the couch watches the whole meeting from
                    // one screen, and a vote window blanked by the tally's push would take the
                    // ballot's clock off a screen that is still drawing it.
                    voteWindowSeconds = message.voteWindowSeconds ?: state.voteWindowSeconds,
                )
                housePushed(message)
            }

            // **This seat's own ballot, re-asserted on every tap** (D-117). The lit row is input
            // echo and is already on the screen; what the house adds is the **refusal after
            // READY** — the VOTE CAST lock, which is the one half of this a phone cannot know
            // better than the house. See `MeetingModel.houseHolds`, where the seat is resolved
            // against the roster the house sent and a name the roster does not hold leaves the lit
            // row alone rather than blanking it.
            is RoundMessage.VoteHeld -> meeting.houseHolds(message.selection, message.locked)

            // **The couch's live ballot, one selection at a time** (D-117, D-134, D-075, E-R2-6).
            // *Ghosts are the only readers who see selections rather than a count*, and this is the
            // branch that lets them: it was `Unit`, so the one screen that makes being out an
            // information privilege rather than a preview drew five fixture names all round.
            //
            // **The seats resolve against the roster the house sent**, which is D-115's second-site
            // clause arriving where the interaction needs it — *the vote ballot being the obvious
            // second site.* This is [nameOf], the same lookup the ring's caller and the tally's rows
            // already make, and it is not `ui` keeping a second copy of who is in the house: the
            // roster is the house's own list, pushed to this phone, and reading it is the same act
            // as drawing it. See [nameOf], where that distinction is written out.
            //
            // **A Skip renders as SKIPPED and never as STILL DECIDING**, which is `tallyRows`' move
            // and D-075's requirement: *not voting counts as a Skip*, so the two must stay
            // distinguishable on the screen somebody is reading them off. `Ballot.forWhom` is null
            // for a row nobody has spoken for, and every row here has been spoken for — the house
            // emits this on every tap, so an arrival is always an expression.
            //
            // **In seat order, replacing rather than appending.** Every tap transmits, so a seat
            // that changes its mind must move its own row instead of growing a second one; and two
            // phones on the same couch must list the room in the same order, which a list in
            // arrival order would not be (rule 4). The roster's index is the seat, so ordering by it
            // is ordering by the house's own list.
            //
            // ⚠️ **The denominator is still short and it is flagged rather than guessed.** The
            // screen reads `votesCast OF ballots.size`, and this list holds the seats the house has
            // shown rather than every living seat — a phone that has not touched a row yet has no
            // message and therefore no line to be STILL DECIDING on. Seeding it wants the living
            // seats at ballot-open, which nothing on the couch's side of the allowlist carries.
            // Escalated; what is closed here is the rows.
            is RoundMessage.VoteSelectionShown -> {
                selections[message.voter.index] = message.selection
                state = state.copy(ballots = ballotRows())
            }

            // `N OF 4 VOTED` — locked ballots, never selections (D-117), against the living for
            // `ReadyProgressed`'s reason.
            is RoundMessage.VoteProgressed ->
                meeting.houseCounted(voted = message.locked, living = message.expected)

            // **The result, to everybody, and the tally screen stops drawing a fixture.** It had
            // MARCUS 3, SKIP 2, PRIYA 1 and MARCUS WAS RESTRAINED printed on every phone in every
            // round. A living phone gets this and nothing else: a count and an outcome (the owner's
            // 2026-08-24 ruling — see `PanelVals.votedLine`).
            //
            // **`restrained = null` is a real outcome and not an absent one** — a Skip, a tie, or a
            // vote nobody won — so it becomes a tally holding a null name rather than no tally at
            // all. A null field here would put the fixture's MARCUS back on the screen at the exact
            // moment the room restrained nobody, which is the worst single frame this screen could
            // draw.
            is RoundMessage.MeetingResult -> state = state.copy(
                tally = MeetingTally(
                    restrained = nameOf(message.restrained),
                    voted = message.voted,
                    living = message.living,
                ),
            )

            // **The couch's half: the ballot with names against it** (D-075, D-134). Permitted to
            // the out classes and to nobody else, so a living phone never reaches this branch.
            //
            // **It completes the ballot rather than counting it.** The couch has been watching this
            // list fill one selection at a time (`VoteSelectionShown`), and what the house sends
            // here is the same list finished — including the seats that never touched a row, whose
            // silence D-117 banks as a Skip and which therefore never produced a selection message
            // of their own. A player revoked mid-vote has seen only the selections shown since they
            // walked in, and this is the moment their screen holds the whole ballot.
            //
            // It replaces the map wholesale rather than merging, for `ballotRows`' own reason: the
            // house's list is authoritative and a merge would let a stale local entry outlive the
            // vote it belonged to.
            is RoundMessage.MeetingResolved -> {
                selections.clear()
                message.attribution.forEach { selections[it.voter.index] = it.selection }
                state = state.copy(ballots = ballotRows())
            }

            // **The takeover, and it is keyed on nothing** (D-102, D-134's E1-1). Like the ending,
            // it can land on whatever screen the player is standing on, so it is not a row in
            // `Flow.housePushes`; unlike the ending it names no winner, because it is addressed to
            // the one seat the room restrained and needs no answer to know that. Arriving at
            // `Restrained` is what sets [PanelState.outBy] — see [PanelState.arrivingAt], which is
            // where the Revoke/Restrain distinction is kept (rule 9).
            is RoundMessage.RestrainedTakeover -> push(ScreenId.Restrained)

            // **The Revoke landing, and the two ways out of the round are symmetric at last**
            // (D-134, E-R2-5). The branch above has existed since the meeting was routed; this one
            // did not, so a player the house had just Revoked went on being shown the round they
            // were no longer in — springboard, work order, page 2 — until somebody called a meeting.
            //
            // **Arriving at `Revoked` is what sets [PanelState.outBy]**, exactly as arriving at
            // `Restrained` does — see [PanelState.arrivingAt], which is where the Revoke/Restrain
            // distinction is kept (rule 9). That is why this is a `push` to a screen rather than a
            // field written here: the cause follows the arrival, and one place decides it.
            //
            // **Keyed on nothing, like the takeover.** It can land on whatever screen the player is
            // standing on, so it is not a row in `Flow.housePushes`; what makes it mean *you* is the
            // socket it came down, and there is nothing on the member a phone could read a seat out
            // of.
            is RoundMessage.RevokeLanded -> push(ScreenId.Revoked)

            is RoundMessage.MeetingEnded -> housePushed(message)

            // **THE HOUSE IS ON FIRE, AND THIS IS WHERE EVERY PHONE IN IT FINDS OUT** (E-R2-3;
            // `gdd.md:370-378`, D-118, D-076). The effect reached every seat and moved nothing on
            // any of them: the alert had no route and the nodes were dropped, so the loudest event
            // in the game arrived as a silently-updated field on a screen nobody was pushed to.
            //
            // **It is a DISMISSABLE alert and it is not a navigation**, which is the half the
            // escalation could not find a ruling for and the design's own table states outright:
            // *Alert: must be dismissable. You cannot scan a marker through a modal.* So nobody is
            // moved off what they were doing — `ScreenId.Banner` is the springboard with the heavy
            // notification over it, it clears itself after [HEAVY_HOLD] (`Flow.autoAdvance`) or the
            // moment it is swiped, and what it leaves behind is the widget. A push to a whole
            // Egress screen would be the app taking the camera away from a player standing at a
            // card, in the dark, during the one crisis that has to be walked to.
            //
            // **[arrived] and not [push], for the two cases it already knows about**: a phone
            // nobody has picked up gets it under the clock (`LockNotify`, D-119), and a phone
            // already on a house-driving screen keeps it — a banner raised over the tally would
            // take the room's result off the screen it is being read from.
            //
            // **The nodes land as rooms and the pair is stored before the banner.** *The widget
            // takes over and names the two nodes*, and the alert and the widget read the same two
            // strings out of [PanelState] (`Notifications.arrivalOn`) so they cannot send two people
            // who may not speak to two different places. Storing them first is `MessageDelivered`'s
            // ordering and its reason: the banner is drawn from the pair, so a banner raised before
            // the pair was written would draw the port's UTILITY and LANDING for one frame.
            //
            // ⚠️ **The countdown on the widget is still the fixture's** (E-R2-7). `remaining` is in
            // ticks, [PanelState.secondsLeft] is in seconds, and the conversion is not `ui`'s to
            // make — see the branch below and the escalation in the worklog. What this closes is
            // the alert and the two names; the number is the missing carrier's.
            is RoundMessage.EgressOpened -> {
                state = state.copy(
                    egressType = message.type.name.uppercase(),
                    // The room, which is what a player navigates a dark house by and what the
                    // design says the widget names. Copied in the house's own draw order — the pair
                    // is read positionally by nothing, but two phones listing it differently would
                    // be rule 4 arriving as *the screens disagree*.
                    egressNodes = message.nodes.map { it.room },
                    egressRunning = true,
                )
                arrived(ScreenId.Banner)
            }

            // **The countdown stopping and starting again, on the containment screen** (D-133).
            //
            // **`running` lands and `remaining` does not**, and the split is where the line already
            // was. Whether the number is moving is a fact about the house that the widget has to be
            // able to draw — a meeting called by reporting a Revoke pauses the Egress, and a
            // countdown that went on falling through a meeting the whole house had stopped for
            // would be the one screen contradicting the room. The *number* is in ticks, and
            // [PanelState.secondsLeft] is in seconds: `Balance` says outright that a screen dividing
            // by the tick rate is a screen holding an opinion about it, so the conversion is not
            // `ui`'s to make. ⚠️ Flagged, and it is E7's with the tick rate — the same gap
            // `egressPercent` has, one field over.
            is RoundMessage.EgressHeld -> state = state.copy(egressHeld = !message.running)

            // **The house's answer to one beat, and it is the same answer either way** (`gdd.md:355`,
            // rule 1). `held` is true when the house is now holding this seat's beat, waiting for
            // somebody at the other node — and false for a beat that missed, a seat serving a
            // lockout, a seat not standing at a node and a seat outside the system. One shape, one
            // kind, one message per return, with only the value differing.
            //
            // **It is copied and never interpreted.** A client that read `false` as *you are not at
            // a node* would have rebuilt the detector the single shape exists to prevent: an Insider
            // anywhere in the house could tap, read the screen, and learn what the house thinks of
            // where they are standing.
            is RoundMessage.SyncPulseAnswered -> state = state.copy(beatHeld = message.held)

            // **The Egress came down, and the couch's widget goes dim and empty with it.** The type
            // is deliberately left standing: it says which Egress the house ran, and unsetting it
            // here would make the widget's two facts one field again. What comes back down is
            // whether one is on.
            is RoundMessage.EgressContained -> state = state.copy(egressRunning = false)

            // **Permitted to nobody, so it cannot arrive** — its row is gone (D-131, D-156), and
            // what every phone gets instead is the ending carrying `WinRoute.EgressUncontained`.
            // Named for `PresenceChanged`'s reason.
            is RoundMessage.EgressSucceeded -> Unit

            is RoundMessage.RoundEnded -> Flow.endings[message.winner]?.let { push(it.to) }

            is RoundMessage.InsidersRevealed -> state = state.copy(
                revealed = message.insiders.map { RevealedInsider(it.name, it.line) },
            )

            // **The house's last word, to the people it owned.** A field of its own beside
            // [PanelState.revealed] rather than a line inside it, because they are two things that
            // share a screen: the reveal is who the Insiders were, and this is the house talking.
            //
            // **It moves no screen**, which is E-P3-6's rule pointed at a third push: an ending is
            // `RoundEnded` then `InsidersRevealed`, and anything that acts on an arrival rather
            // than on the round being over is wrong. This arrives at a phone already standing on
            // an ending and changes what is written on it.
            is RoundMessage.HouseSignedOff -> state = state.copy(signOff = message.body)
        }
    }

    /**
     * The house's transition for the screen this phone is standing on, if this message is the one
     * that screen is waiting for.
     *
     * Read off [Flow.housePushes] and never decided here. A message that no screen is waiting for
     * moves nothing, which is the fail-closed direction: a phone that stayed put is noticed, and a
     * phone that walked somewhere nobody wrote down is not.
     */
    private fun housePushed(message: RoundMessage) {
        val waiting = Flow.housePushes[state.screen] ?: return
        if (waiting.on == message.kind) push(waiting.to)
    }

    /**
     * **The caught scan's row: the scan's answer joined to the order the house already sent.**
     *
     * Both operands are pushed facts about this one seat — `Effect.ScanAnswered` names the entry,
     * the kind and the parameters; `Effect.WorkOrderIssued` names where that entry is, whether it is
     * done and how long the order is — and the index they are joined on is the house's own. So this
     * decides nothing: it is two copies read side by side, which is the only kind of derivation
     * `houseSaid` is allowed to make.
     *
     * **Null where the order does not hold the entry**, and the branch above then moves no screen.
     * That is the fail-closed direction: a phone that stayed on the scan is noticed in seconds, and
     * a caught scan drawn against an entry the house cannot place would put a player through work
     * with no room, no glyph and no position in their own list.
     *
     * **Blocked lines are not searched and cannot be.** `OrderLine.Blocked` carries an index and
     * nothing else — no name, no signature and above all no room (D-114) — so a scan answering with
     * a blocked entry finds nothing here, which is the same refusal the type already is.
     */
    private fun currentFrom(opened: SubroutineInstance): CurrentSubroutine? {
        val lines = state.order ?: return null
        val line = lines.filterIsInstance<OrderLine.Known>()
            .firstOrNull { it.index == opened.entry } ?: return null
        // The FIRST destination, for `PanelVals.order`'s reason: the Array Wipe circuit names three
        // stations in draw order (D-122) and a screen read by lamplight has room for the one you
        // are walking to. An empty list is lawful and leaves the room unnamed rather than invented.
        val at = line.at.firstOrNull()
        return CurrentSubroutine(
            subroutine = Subroutine.of(opened.subroutine),
            room = at?.room,
            marker = at?.shape?.let { MarkerShapes[it] },
            // One-based, because it is read aloud off a screen as SUBROUTINE 4 OF 7 and the house's
            // own index is a position in a list. The total is the order's LENGTH including the
            // blocked rows: an order that shortened while work was blocked would make its own
            // length a tell about what the house had drawn (D-114).
            index = opened.entry + 1,
            total = lines.size,
            done = lines.count { it is OrderLine.Known && it.done },
        )
    }

    /**
     * **Something arrived, and where it lands depends on whether anybody is holding the phone.**
     *
     * A notification on a **locked** phone goes under the clock (`LockNotify`) rather than over a
     * springboard nobody is looking at, which is D-119's own distinction and is why the port draws
     * both. Everywhere else the banner arrives over what the player was doing.
     *
     * **It does not interrupt a meeting or an ending, and that is the fail-closed direction.** A
     * banner raised over the tally would take the room's result off the screen it is being read
     * from, and one raised over an ending would put a text message over the last thing the round
     * has to say. Those screens are the house driving (`Flow.houseDriving`), so a phone standing on
     * one keeps standing on it and the notification survives wherever its kind survives — under the
     * clock if it is stored, and nowhere if it is not, which is exactly what a house notice is.
     *
     * The dim comes back on its own: `Flow.autoAdvance` walks a heavy banner off after
     * [HEAVY_HOLD], and the screen it lands on has no heavy notification on it.
     */
    private fun arrived(banner: ScreenId) {
        if (state.screen in Flow.houseDriving) return
        push(if (state.screen == ScreenId.Lock) ScreenId.LockNotify else banner)
    }

    /**
     * **A seat as the name the house sent for it** — a lookup against [PanelState.roster], and it
     * is [fireSurge]'s move read backwards.
     *
     * Lawful for [fireSurge]'s reason exactly: names are round-scoped, arrive from the house
     * (D-115), and **the roster is never sorted or filtered anywhere in this module**, so the index
     * a row sits at *is* the seat. That is written on the field and is what makes both directions
     * safe rather than one of them.
     *
     * **This is not `ui` keeping a second copy of who is in the house** — the distinction E-P1-2
     * turns on. A second copy would be this module *deriving* a roster, from a lobby standing or a
     * list it assembled; the roster is the house's own list, pushed to this phone, and reading it
     * is the same act as drawing it.
     *
     * **Null in, null out — and that is a Skip, not a failure.** `MeetingResult.restrained` is null
     * when the room restrained nobody, which is a real outcome the screen has words for. A seat the
     * roster does not hold also comes back null, and the screen says the same thing about it: the
     * fail-closed direction is *the house named nobody*, never a name picked from nearby.
     */
    private fun nameOf(seat: Seat?): String? =
        seat?.let { state.roster?.getOrNull(it.index) }

    /**
     * **The couch's live ballot, keyed by the seat that cast it** (D-117, D-134).
     *
     * Beside [PanelState] rather than in it, which is [trail]'s and [surge]'s arrangement and the
     * reason for it: [PanelState] is flat, inert and the thing a recording captures, and this is
     * bookkeeping — *what the house has said so far* — that only exists so the rows can be rebuilt
     * whole. Keyed on the **seat** and not on the name, because two players may type the same name
     * and a phone that never typed one has none at all; a map keyed on what a row happens to read
     * would silently merge two people's ballots into one.
     *
     * **Cleared when a meeting begins**, exactly as [MeetingModel]'s own state is and for its
     * reason: a round holds several meetings, and last meeting's selections standing on this one's
     * screen would be the couch watching a vote that has already happened.
     */
    private val selections = mutableMapOf<Int, Seat?>()

    /**
     * **The rows, rebuilt whole, in seat order** (rule 4).
     *
     * Rebuilt rather than edited in place so the order is a property of the house's roster rather
     * than of arrival: two people sitting beside each other on the couch must be looking at the
     * same list in the same order, and a list that grew as taps landed would be two different
     * lists on two phones within a second of each other.
     *
     * **A Skip is SKIPPED and never STILL DECIDING.** `Ballot.forWhom` is null for a row nobody has
     * spoken for, and every row in here has been spoken for — the house emits a selection on every
     * tap, so an arrival is always an expression. D-075 is why the two must not collapse: *not
     * voting counts as a Skip*, and the whole weight of inaction sits behind restraining nobody.
     *
     * A seat the roster cannot name still gets a row, because a ballot was cast; it says [UNNAMED]
     * rather than nothing, which is [nameOf]'s fail-closed direction one screen over.
     */
    private fun ballotRows(): List<Ballot> = selections.entries
        .sortedBy { it.key }
        .map { (voter, selection) ->
            Ballot(
                by = nameOf(Seat(voter))?.ifBlank { UNNAMED } ?: UNNAMED,
                forWhom = when (selection) {
                    null -> SKIPPED
                    else -> nameOf(selection)?.ifBlank { UNNAMED } ?: SKIPPED
                },
            )
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
        fireSurge = ::fireSurge,
        fireIsolate = ::fireIsolate,
        fireStatic = ::fireStatic,
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
        hostHome = ::hostHome,
        openSystemSettings = lobby::openSystemSettings,
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
        stopSubroutine = ::stopSubroutine,
        dismissStanding = ::dismissStanding,
    )

    private fun remember(screen: ScreenId) {
        trail.addLast(screen)
        if (trail.size > TRAIL_DEPTH) trail.removeFirst()
    }

    private companion object {
        const val TRAIL_DEPTH = 32

        /**
         * The tally row for a ballot that named nobody.
         *
         * A word rather than a blank, because *Skip is a candidate, not an abstention* — it
         * competes in the count, and a row with no name on it would read as a ballot the screen
         * could not resolve rather than as a decision several people made.
         */
        const val SKIPPED = "SKIP"

        /**
         * A voter the roster cannot name.
         *
         * The same word `LobbyScreen` draws for a phone that never said what to call its owner, and
         * it is here for the same reason [SKIPPED] is: a row with an empty left-hand side would
         * read as a rendering fault rather than as somebody who joined without typing a name. It is
         * also the fail-closed answer for a seat the roster does not hold at all — the row still
         * says a ballot was cast, and it names nobody rather than naming somebody nearby.
         */
        const val UNNAMED = "UNNAMED"
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
    // **THE SURGE OWNS THE PHONE FOR FIVE SECONDS** (D-182), and this is the one place in the app
    // where a screen's own timer is suppressed. The sequence pushes the phone to `Boot`, which
    // ordinarily falls through to `Perms` after 2.4 seconds — so without this the player would get
    // a hundred milliseconds of the cold-start permissions walk in the middle of a running round.
    // A malfunctioning phone is not running its own timers, which is the fiction and is also true.
    val surge = model.surge
    val pending = if (surge == null) model.pending else null
    val awaiting = if (standingInForTheHouse && surge == null) model.awaitingPush else null
    val screen = model.state.screen

    // **The rest of the Surge, on a clock, because `core` has none** (D-182). The bright phase is
    // already on the glass — `FlowModel.surged` wrote it in the frame the message arrived, which is
    // rule 7's *same frame* — and what is left is the two waits and the two things that end them.
    //
    // Keyed on the whole [SurgeSlam], so a second Surge landing mid-sequence restarts it on the new
    // one rather than letting the first one's timer end both. Each step re-checks which Surge it
    // belongs to, which is the same discipline the jam's window keeps.
    LaunchedEffect(surge) {
        if (surge != null) {
            delay(SURGE_PEAK_MS)
            model.surgeDarkened(surge)
            delay(SURGE_BLACK_MS)
            model.surgeRebooted(surge)
            delay(SURGE_BOOT_MS)
            model.surgeEnded(surge)
        }
    }

    // **The jam's own window, and it is a presentation timer rather than a clock** (D-186). The
    // house owns when the jam ENDS -- it cleared its own deadline the moment it opened one, and it
    // clears it early at a meeting call (D-185) -- and what this runs off is the length the house
    // sent with it, which is the ring's six seconds one message over. It is keyed on the jam, so a
    // second Static arriving mid-window restarts the wait on the new one rather than ending both
    // early.
    val jam = model.state.staticJam
    LaunchedEffect(jam) {
        if (jam != null) {
            delay(jam.seconds * 1_000L)
            model.staticLapsed(jam)
        }
    }

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
        model.subroutines, model.notifications, model.lamp,
    )
}

/**
 * **The Surge's three phases, and they are the owner's numbers rather than placeholders** (D-182).
 *
 * Five seconds of downtime: a second and a half at full, one black, two and a half on the
 * cold-start Boot render. They are here rather than in `Balance` for the reason the ruling gives —
 * `core` has no clock to say a duration with, so these are the platform's numbers on the platform's
 * side of the line — and they are minted from one effect the rules already emit.
 *
 * **The length IS the ability.** `gdd.md:342` calls the Surge a commitment rather than a tool; a
 * one-second flare is a startle, and five seconds is a person standing in the dark holding a defunct
 * rectangle while everybody in the building walks toward the light they just saw.
 */
const val SURGE_PEAK_MS: Long = 1_500L
const val SURGE_BLACK_MS: Long = 1_000L
const val SURGE_BOOT_MS: Long = 2_500L

/**
 * How long the bench pretends the house takes. **Not a game number and not a design one.**
 *
 * Every real one of these is a count of phones or the house's own clock — the check-in gate, the
 * discussion, the vote window, the LIGHTS OUT countdown — and none of them is four seconds. This
 * is long enough to read a screen and short enough to walk a meeting, and it exists only so that
 * somebody looking at layout on a bench can get past the check-in screen.
 */
const val HOUSE_STAND_IN_MS: Long = 4_000L
