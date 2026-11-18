package home.someoneshome.ui

import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineQuestion
import home.someoneshome.model.MalformedSubroutineParameters
import home.someoneshome.model.OrderLine
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.Balance
import home.someoneshome.model.CardPayload
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
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
        // CONTINUE goes wherever [NameDropModel.pastPerms] says (the join screen on a rendered,
        // set-up phone), and the NAMEDROP row is the one way back to that step (D-248).
        ScreenId.Perms -> setOf(ScreenId.Join, ScreenId.NameDrop)
        // DONE. The step is said to be finished and the phone goes on to join.
        ScreenId.NameDrop -> setOf(ScreenId.Join)
        // **Tapping a network row is not in here** (E-PAY-6). Attaching can be refused — D-161's
        // setup gate, and now a phone that has not said what to call its owner — and a refusal stays
        // on this screen with the reason on it. The row used to navigate unconditionally beside the
        // action, so a refused attach walked into a lobby this phone was not in and the counts sat
        // at nothing. Same shape as HOST WITH THIS HOME; see [Flow.viaActions].
        //
        // The back arrow always reaches the PERMISSIONS screen, trail or no trail (D-248): D-235's
        // skip means a set-up phone arrives here with nothing behind it, and that screen is where
        // the NameDrop step is reached again.
        ScreenId.Join -> setOf(ScreenId.Maps, ScreenId.Perms)

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
        //
        // **The back arrow is, and it is the whole edge this screen has** (E-PAY-6). Until N5b the
        // set was empty and HAND IT OVER refused a blank line in place, so a player who opened the
        // one-line screen with nothing to say was on a screen with no control that moved them. The
        // arrow's target here is the *fallback* the graph transcribes — on a phone the trail decides
        // — but the lobby is the only screen this one is ever reached from, so the two agree.
        ScreenId.Secret -> setOf(ScreenId.Lobby)
        ScreenId.Armed -> setOf(ScreenId.Home)
        // The opening banner over ARMED (D-254): CONTINUE is still under the thumb, and a tap on the
        // banner opens what it says, the reveal, as it does over the springboard. The swipe that
        // clears it is a gesture, declared in [Flow.viaActions].
        ScreenId.ArmedNotify -> setOf(ScreenId.Home, ScreenId.Reveal)

        // The springboard, and the screens that ARE the springboard with something on top.
        // `Quiet`'s banner opens the Subroutines list, which the springboard already reaches, so
        // it adds no edge of its own — the tile and the banner are two doors to one screen.
        ScreenId.Home, ScreenId.Notify, ScreenId.Quiet, ScreenId.EgressWidget -> SPRINGBOARD
        ScreenId.Banner -> SPRINGBOARD + ScreenId.EgressWidget
        // The page dots and the dock, and nothing else. **The two ability tiles are holds** (D-141)
        // and publish no click action, so the Insider's egress edge is not readable off this screen
        // and lives in [Flow.viaActions] with the other gestures. A Resident's tiles are not
        // controls at all (D-142) — no pointer input, nothing to fire, nothing to time.
        // The dock, and nothing else: page 1 is a swipe away and lives in [Flow.viaActions].
        ScreenId.Page2 -> setOf(ScreenId.Work, ScreenId.Scan, ScreenId.Lock)
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
        // All three refusals, pixel-identical apart from their text, and identical here too. One
        // of them is Resident-only; differing exits would be the tell the shared body prevents.
        ScreenId.ScanBad, ScreenId.ScanUnknown, ScreenId.TermBusy ->
            setOf(ScreenId.Scan, ScreenId.Work)
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
        // **The live view is opened by the house and left by the player** (D-199). See
        // [Flow.houseOpened]: nothing walks to it except its own timelapse coming back.
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
        // **`Calling` is not in here, and its absence is D-121** (N5b). The PHONE tile went straight
        // to the caller's ring, which made a house meeting callable from a chair — the one thing
        // that ruling forbids outright. The tile is deleted rather than retargeted and `Calling`
        // has moved to [Flow.housePushed], where `MeetingCalling` is the only thing that reaches it.
        ScreenId.Work, ScreenId.Files, ScreenId.Notes, ScreenId.Reveal,
        // No TERMINAL tile (D-199). The map opens by scanning the T card, so it is not here.
        // **No Page2 either**: the page bar is not a control (D-202), and page 2 is a swipe away,
        // declared in [Flow.viaActions].
        ScreenId.Settings, ScreenId.Scan, ScreenId.Lock,
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
     *
     * ### ⚠️ THIS TABLE IS THE UNHOUSED PORT'S, AND ONLY THE UNHOUSED PORT'S
     *
     * Every number in it is a presentation placeholder, which this file has said from the first
     * day — and for just as long, nothing stopped the placeholders running on a phone with a real
     * house attached. They did, all evening, in front of the owner: `Notice → Discussion` after
     * nine seconds walked two phones into a discussion the house had not opened, and a player sat
     * through a talk, a ballot and a tally that were not happening.
     *
     * **[FlowHost] is where the cordon is**, because that is the one place these rows are ever
     * fired from: with [PanelState.houseAttached] the table is not consulted at all. What is left
     * here is what it always claimed to be — the thing that makes a ported flow walkable on a
     * desktop and on a phone with no round attached, and nothing a player in a dark house will
     * ever meet.
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
        ScreenId.ArmedNotify to AutoAdvance(
            ScreenId.Armed, HEAVY_HOLD, "the same opening message, over PERIMETER ARMED, clears itself (D-254)",
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
     * **The rows in [autoAdvance] that are this phone's own, and therefore run with a house
     * attached.**
     *
     * ### Why a subset, and not the whole table or none of it
     *
     * [FlowHost] switches the whole of [autoAdvance] off the moment a house is attached, and for
     * most of it that is exactly right: the ring's six seconds and the notice's nine describe
     * moments the **house** decides, and a phone running them locally is the fictional meeting
     * `HousedPhoneTest` reproduces. But the table also holds windows nothing outside this rectangle
     * can see, and switching those off did not make the phone honest — it made it **stop**.
     *
     * Three of them, each found by rendering rather than by reading:
     *
     * - **[ScreenId.Scan] — D-159's safety window.** Ten seconds, then two vibrates and the light
     *   fails, *so nobody is left standing in a dark room holding a lit screen at a wall.* With a
     *   house attached it never fired, which removed the safety device in the only situation it was
     *   written for: a real round, in a real dark house. The house cannot supply it — nothing
     *   outside the phone knows the camera is open.
     * - **[ScreenId.Notify], [ScreenId.Banner], [ScreenId.LockNotify] — D-119's ten seconds.** A
     *   heavy notification dims the house and this transition is the undim. There is no
     *   [housePushes] row for any of the three, so on a housed phone the banner stood over the
     *   springboard and the panel stayed dimmed **for the rest of the round**.
     * - **[ScreenId.Boot] — the self-test.** Mostly moot, because the one way a housed phone renders
     *   it is inside a Surge, where [FlowHost] suppresses every timer anyway (D-182). It is named
     *   for completeness: if the cold start ever runs with a house attached it must still fall
     *   through, and the alternative is a phone parked on a self-test forever.
     *
     * ### It is a list rather than a rule
     *
     * There is no property of an [AutoAdvance] that says *this one is the phone's own* — the honest
     * version of that question is a person deciding, per row, whether anything outside the phone
     * could ever push it. So this is written out, and a row added to [autoAdvance] is **house-owned
     * by default**: the failure mode is a screen that waits for a push, which somebody notices, and
     * never a housed phone quietly telling itself a story, which is what the owner's evening was.
     *
     * [LocalWindows] names the two of these that also draw a clock. The sets are deliberately not
     * the same one: a window can be the phone's own without anything on screen counting it.
     */
    val ownTimers: Set<ScreenId> = setOf(
        ScreenId.Boot,
        ScreenId.Scan,
        ScreenId.Notify,
        ScreenId.ArmedNotify,
        ScreenId.Banner,
        ScreenId.LockNotify,
    )

    /**
     * **The rows in [autoAdvance] that are the phone's own only once the house has opened a phase**
     * (D-250, provisional, owner to confirm).
     *
     * The notice's nine seconds is presentation (*shown once at the top of the meeting, then gone*),
     * but it is not in [ownTimers]: on the owner's evening it walked a phone parked on a fake meeting
     * into a discussion no house had opened (`HousedPhoneTest`). Left off both lists, a housed phone
     * that reached the notice had no way off it but a swipe, and with NOBODY NAMED there is no card
     * to swipe: the simulator hunt found living seats on HOUSE MEETING · NOTICES from the gate's
     * close to the next LIGHTS OUT (H2).
     *
     * So it runs on a housed phone **once the house has opened the talk**, which is the push that
     * walked the phone onto the notice in the first place. The phase is read off this phone's own
     * copy of the house's push ([PanelState.meetingPhase]); a housed phone with no phase open still
     * stays where it is.
     */
    val ownOncePhaseOpened: Map<ScreenId, MeetingPhase> = mapOf(
        ScreenId.Notice to MeetingPhase.Discussion,
    )

    /**
     * **Whether [autoAdvance]'s row for the screen [state] stands on may fire.** Unhoused, all of
     * them; housed, [ownTimers], and [ownOncePhaseOpened] once its phase is open.
     */
    fun runsItsOwnTimer(state: PanelState): Boolean =
        !state.houseAttached ||
            state.screen in ownTimers ||
            (ownOncePhaseOpened[state.screen]?.let { it == state.meetingPhase } ?: false)

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
        // **The notice, when the talk ends before it does** (D-250, H2). The push that walked the
        // phone here opened the talk, so the next phase push a phone on the notice can hear is the
        // ballot's: every other hand went up while this one was still reading. Without the row the
        // phone stayed on the notice through the ballot and the tally.
        ScreenId.Notice to HousePush(
            ScreenId.Vote, EmitSchema.MEETING_PHASE_OPENED,
            "every hand went up while this phone was still on the notice; the ballot is open",
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
     * **Where a seated phone stands when the lights go out**: the lobby, and the one-line screen it
     * opens. The house's opening push walks a phone on either to [ScreenId.Armed] (D-234), with the
     * opening banner over it ([ScreenId.ArmedNotify], D-254).
     */
    val seatedBeforeArming: Set<ScreenId> = setOf(ScreenId.Lobby, ScreenId.Secret)

    /**
     * **Where that push lands a seated phone: PERIMETER ARMED, under the opening banner and once it
     * has cleared** (D-234, D-254). Anything that waits for a phone to have armed asks this set
     * rather than one id: the banner stands for [HEAVY_HOLD], so a wait on plain ARMED alone would
     * start ten seconds late, or never, if a thumb takes CONTINUE from under the banner.
     */
    val armedOn: Set<ScreenId> = setOf(ScreenId.ArmedNotify, ScreenId.Armed)

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
        // Subroutine coming unblocked by somebody else's work. **And the Egress alert** (D-231): it
        // used to be walked to from the Insider's own page 2, the one role-asymmetric edge in the
        // game, and a hold that walked there told the house nothing. It is `EgressOpened`'s now, on
        // every phone alike, the actor's included.
        ScreenId.Notify, ScreenId.LockNotify, ScreenId.Quiet, ScreenId.Banner,
        // The same opening text over PERIMETER ARMED, where a seated phone stands when it lands
        // (D-254).
        ScreenId.ArmedNotify,
        ScreenId.Call, ScreenId.Found,
        // **The caller's own ring, and it is here because D-121 says a meeting is a WALK** (N5b).
        // The springboard drew a PHONE tile that went straight to this screen, which is the remote
        // call D-121 spent its closing paragraph refusing — *a button that summons the whole house
        // from a chair*. The tile is deleted; what reaches this screen now is
        // `RoundMessage.MeetingCalling`, sent to the one seat that scanned the meeting card
        // (D-191), and nothing on any phone can walk here.
        ScreenId.Calling,
        ScreenId.ScanCaught, ScreenId.ScanBad, ScreenId.ScanUnknown, ScreenId.TermBusy,
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
     * **Screens only the house can open, which the player then uses like any app** (D-199).
     *
     * The live view is the one. It used to be walked to, through a TERMINAL tile and a NO SIGNAL
     * screen whose RETRY always worked; now it opens because the house answered a scan of the T
     * card with *you are reading*, and from nowhere else. That makes it house-pushed in the sense
     * [housePushed] is about -- **no tap anywhere in the app may put a phone on it** -- and unlike
     * every screen in that set in one respect: once it is open, it is the player's. It has a way
     * home, and its timelapse comes back to it.
     *
     * So it is a set of its own rather than a member of [housePushed], and the difference is
     * exactly the two things that set forbids: a back arrow to a declared parent, and a child
     * screen walking back into it. `FlowTest.nothingWalksIntoAHouseOpenedViewFromOutsideIt` holds
     * the narrower rule that replaces them -- the only screens that walk here are ones reachable
     * from nowhere else.
     */
    val houseOpened: Set<ScreenId> = setOf(ScreenId.TermLive)

    /**
     * **The station's screens: while a phone is on one of these, it is the reader** (D-203).
     *
     * Leaving the set for any screen outside it is the phone stopping reading, and [FlowModel]
     * says so to the house from the one place every navigation passes through. Moving between the
     * two is still reading.
     */
    val atTheStation: Set<ScreenId> = setOf(ScreenId.TermLive, ScreenId.Timelapse)

    /**
     * **The browsing surfaces: the screens a finger may step backwards off.**
     *
     * The owner's finding, in their own words: *no back navigation anywhere — swipe right from the
     * left edge, or tap the back arrow next to the page name.* Both existed as ideas and neither
     * reached a thumb. [FlowModel.back] had exactly one caller in the repository and it was a test;
     * the chevrons that were drawn were per-screen `goes(...)` targets that named one parent each,
     * so a host who reached the editor from a saved home stepped back into the maps list.
     *
     * ### It is derived and not typed out, which is the whole safety of it
     *
     * [PanelVals.PRE] minus [houseDriving] minus [housePushed] minus the lobby. **Every subtraction
     * is a rule rather than an exception**, so a screen added to any of those three tables leaves
     * this set on its own, and nobody has to remember a fourth place:
     *
     * - **[houseDriving]** is D-102's haptic doctrine read backwards — a screen that arrived unasked
     *   is one the house put you on. `Boot` is in it because nothing is behind a self-test.
     * - **[housePushed]** is the set nothing walks to, and it carries `WinResidents`: the perimeter
     *   disarming turns the lights back on, so the ending is a light-field screen, and stepping back
     *   off it would be a phone re-entering a round that is over.
     * - **The lobby is subtracted by name, and it is the only named exclusion in this file.** Not
     *   because the house is driving — the lights are on and nothing has been decided — but because
     *   a back arrow there would be a control that *appears* to leave and does not: the socket stays
     *   up, the seat stays held, and five other phones go on counting a person who is looking at the
     *   join screen. Leaving a lobby is a thing that has to be **said to the house**, so it is a
     *   control that says it (`LobbyModel.leaveLobby`) and never a gesture that walks away from one.
     *
     * ### What it is NOT, and must never become
     *
     * **A way onto the house's screens.** Everything the perimeter arms over is absent from
     * [PanelVals.PRE] by construction, and `FlowTest` asserts the emptiness of the intersection
     * directly rather than trusting the derivation to stay this shape. On every one of those
     * screens *nothing behind you is still true* — the round armed, a meeting was called, the vote
     * happened, your access was revoked — and a device asserting a state the house has already
     * replaced is the fault this whole table exists to make impossible.
     */
    val backable: Set<ScreenId> =
        PanelVals.PRE - houseDriving - housePushed - ScreenId.Lobby

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
        // A tap on a network row, once this phone may really take a seat. Refusable on two counts —
        // D-161's setup gate, and a phone that has not named its owner (E-PAY-6) — and both
        // refusals stay on the join screen with the reason on it, exactly as a refused
        // advertisement stays on the detail screen.
        ScreenId.Join to setOf(ScreenId.Lobby),
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
        // LIGHTS OUT, and **the way out of the lobby** (E-PAY-6). Both are two-second holds and
        // neither publishes a click action, so both are declared here. `Join` is where a client who
        // left and a host who closed the house both land — see `FlowModel.leaveLobby` and
        // `FlowModel.terminateHouse`, and `FlowModel.houseClosed` for the phones that were told.
        ScreenId.Lobby to setOf(ScreenId.Armed, ScreenId.Join),
        // **LEAVE THIS HOUSE, off the disconnect screen** (D-260): the lobby's own way out, for a
        // guest whose house has been out of reach long enough to be shown it. A hold, like the
        // lobby's, so it is declared here.
        ScreenId.Disconnect to setOf(ScreenId.Join),
        // Page 2's gestures. **The Egress tile is no longer one of them** (D-231): its hold sends
        // the request and stays put, and the alert arrives by the house's push on every phone, so
        // `Banner` is in [housePushed]. A Resident's tiles are drawn identically and take no pointer
        // input at all (D-142), so there is no hold to run and no view either role can be caught on.
        ScreenId.Page2 to setOf(
            // **The swipe back to page 1** (`gdd.md:561`), the same for both roles. A gesture
            // publishes no click action, so the edge is declared here.
            ScreenId.Home,
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
        //
        // **And the page swipe to page 2** (`gdd.md:561`) off every screen that draws page 1. The
        // banners sit over page 1, so its body is still under the thumb. `SpringboardSwipeTest`
        // performs the swipe on each of them.
        ScreenId.Home to setOf(ScreenId.Page2),
        ScreenId.EgressWidget to setOf(ScreenId.Page2),
        ScreenId.Notify to setOf(ScreenId.Home, ScreenId.Page2),
        // The opening banner over ARMED, swiped up: ARMED is left standing (D-254). No page swipe,
        // because there is no springboard under it.
        ScreenId.ArmedNotify to setOf(ScreenId.Armed),
        ScreenId.Banner to setOf(ScreenId.Home, ScreenId.Page2),
        ScreenId.Quiet to setOf(ScreenId.Home, ScreenId.Page2),
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
 * **The panel between two rounds: what the evening keeps, and nothing the round wrote** (D-259).
 *
 * A round is lobby to victory (D-170), and every field the house pushed during one is about that
 * one: who this phone was, whether it was put out and how, its work, the meter, the clocks, the
 * meeting, the thread, the ending. After NEW ROUND the second round's PERIMETER ARMED drew the first
 * round's OUT REVOKED, METER 96% and work order, because `newRound` walked the screen and copied
 * everything else across.
 *
 * **Built fresh, not copied with a list of fields to blank.** The kept fields are named here and
 * every other field takes its default, so a field added to [PanelState] later is reset at the
 * boundary unless somebody says it belongs to the evening. That is the fail-closed direction: a
 * lobby field wrongly reset shows itself at once, and a round field wrongly kept is a leak from one
 * round into the next that nobody would see.
 *
 * Kept, because none of it is the round's:
 * - [PanelState.screen] — where the phone stands; the caller moves it.
 * - [PanelState.houseAttached] — a fact about the socket, which outlives the round (D-157).
 * - [PanelState.markersOn], [PanelState.torch] — the host's marker registration, done in the light.
 * - [PanelState.wallTime] — the phone's own clock (D-255).
 * - [PanelState.inboxSeed], [PanelState.noteSeed] — the phone's own fictional backlog.
 */
fun PanelState.betweenRounds(): PanelState = PanelState(
    screen = screen,
    houseAttached = houseAttached,
    markersOn = markersOn,
    torch = torch,
    wallTime = wallTime,
    inboxSeed = inboxSeed,
    noteSeed = noteSeed,
)

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
     *
     * Null means *choose for me*, and [HomeEditorModel.opening] does (D-270): the empty home on a
     * real phone, the bungalow in the render harness alone. See the [editor] property.
     */
    editor: HomeEditorModel? = null,
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
     * **What the OS has said about this phone** (D-161), and the gate the lobby stands behind.
     *
     * Held here rather than in the Perms screen's own `remember` for [notifications]' reason and a
     * harder one: the answer has to outlive the screen. `hostHome` and `attachToHome` both consult
     * it, and neither of them is anywhere near the screen that asked.
     *
     * Defaulted to a phone that has been asked nothing, which is what a cold launch is — and which
     * closes the gate. A default that opened it would mean every test, every screenshot and every
     * bench walked into a lobby through a gate that was never shut, and the one thing this gate has
     * to survive is exactly that: it is a gate a build is tempted to have open.
     */
    val perms: PermsModel = PermsModel(),
    /**
     * **Whether this install has been told NameDrop is off** (D-248), held here for [perms]' reason:
     * [fallThrough] asks it, nowhere near the screen that sets it.
     *
     * Defaulted to a phone that has not, which is a fresh install: the default fails toward
     * showing the step, never toward a phone that skipped it.
     */
    val nameDrop: NameDropModel = NameDropModel(),
    /**
     * **The torch beside the back lens, reached at last** (E-PAY-5).
     *
     * [onScreen]'s shape and its boundary: the LED is `platform`'s and `ui` may not see `platform`,
     * so what goes out of this line is *the host asked for the torch* and the app root is where
     * that becomes an `AVCaptureDevice`. Until this existed, `toggleTorch` moved a colour value in
     * `Amber` and nothing else — the owner's finding was that the button reaches no hardware.
     *
     * Defaulted to nothing, which is every render and every screenshot: the wash over the
     * viewfinder still appears, because that wash is the screen saying what it believes, and the
     * belief is true on a phone and harmless on a Mac.
     */
    private val onTorch: (Boolean) -> Unit = {},
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
     * this app moves because a Tier-2 ability was fired, until the house answers the fire (the
     * actor's own Access bar, D-262).
     */
    val send: (IntentMessage) -> Unit = {},
    /**
     * **The Egress tile, held: where the request goes** (D-141, D-231).
     *
     * [send]'s sibling and not a member of it, because the Egress is not an [IntentMessage]: the
     * house has to draw the type and the two nodes before there is a fact to admit, so the request
     * travels as its own frame (`RoundBody.Fire`) and carries nothing. The app root points this at
     * the round transport's `fireEgress`.
     *
     * Until D-231 there was no such seam and [armEgress] walked this phone to the alert itself and
     * told nobody, so an Egress could not be fired from a phone at all. Defaulted to nothing, which
     * is every render and every test: a page 2 whose hold reaches nobody.
     */
    private val fireEgress: () -> Unit = {},
    /**
     * **The host held LIGHTS OUT down for two seconds and the gate was open** (E-PAY-1 / E-D3-3's
     * trigger half).
     *
     * The one act in this game that starts a round, as a seam, because the thing it starts is not
     * `ui`'s: arming draws the Insider count, the stations, the active set and every seat's work
     * order, and `ui` may not see `core` at all. So what this layer owes the app is the *fact that
     * the host did it*, and the app root is where that becomes a round.
     *
     * ### It is the ACT and deliberately not the screen
     *
     * An app that armed when the host's phone arrived on [ScreenId.Armed] would arm on a route
     * that never consulted the gate — the cheat picker walks every `ScreenId` and `Armed` is one
     * of them, which the payoff run recorded as *a jump to ARMED is indistinguishable from a gate
     * that failed*. This fires from [lightsOut] and nowhere else, so it is reachable only the way
     * a person reaches it: a live button, held. `HoldToConfirm(enabled = false)` takes no pointer
     * input at all (D-141), so a short lobby cannot even begin the hold.
     *
     * ### The screen moves when the house says it armed, and not before (D-226)
     *
     * It is handed `armed`, and walks this phone to [ScreenId.Armed] only when that is called.
     * Until 2026-11-16 the screen moved on the press and the house was told after, so a press the
     * house refused still walked the host to ARMED: a host whose phone had locked in the lobby held
     * a READY standing from before the lock, the house beside it had nobody seated and said `WILL
     * NOT ARM — 0 SEAT(S)`, and the screen said ARMED over a room with no round in it. A refused
     * press now leaves the host on the lobby, which is still drawing why.
     *
     * **Defaulted to arming at once**, which is every render, every screenshot and the desktop
     * preview — no house is behind those, so nothing could refuse, and the walk is what it always
     * was. A client's lobby draws no such button, and a host with no house behind it arms nobody.
     * `FlowModel` decides none of that: it reports the press and follows the answer.
     *
     * **With a house behind it the app root does not call `armed` at all** (D-234). The house's
     * `OpeningMessage`, sent to every seat at arming, is what walks every phone to ARMED, the
     * host's over its own loopback socket (see [roundOpened]), so the host and its guests reach the
     * screen by one path. A refused press still sends nothing and still moves nothing.
     */
    private val onLightsOut: (armed: () -> Unit) -> Unit = { armed -> armed() },
    /**
     * **This phone landed on a screen** — reported, never interpreted.
     *
     * [onLightsOut]'s shape and its argument, for the other output `ui` cannot reach: the motor is
     * `platform`'s and `ui` may not see `platform`, so the two things a *screen* legitimately
     * settles about a vibration have to be reported outward and settled in the app root.
     *
     * They are exactly two, and both are about where a player's attention is rather than about what
     * the house said. **A ringing phone stops ringing when it is answered** (D-160), and answering
     * is walking off the ring — the six seconds running out, or `MeetingCalling` moving the caller
     * to their own screen; nothing else in the app knows that happened. **A Subroutine whose whole
     * question is a rhythm buzzes it when its work opens** — Handshake, Sniff and Drift — and the
     * moment they are owed it is the moment they arrive on the screen that takes the answer.
     *
     * ### ⚠️ It is not a buzzing table and this layer must never make it one
     *
     * D-134: *the buzz rides the effect rather than living in a table in `ui`*, because a client-side
     * lookup that could say *"…and if it was you, buzz differently"* is the device deciding a game
     * answer. Nothing here chooses a [home.someoneshome.model.Haptic], and nothing here may: what
     * goes out of this line is a [ScreenId] and nothing else, and `PanelVals.BUZZING` — the set of
     * screens that arrive unasked — stays what it has always been, a description of the design that
     * [houseDriving] is derived from and that no motor reads.
     *
     * **Fired on a change of screen and not on every arrival**, so that a `copy` which lands on the
     * screen the phone is already on cannot re-buzz a question the player is halfway through.
     *
     * Defaulted to nothing: every render, every screenshot and every test gets a phone with no
     * motor behind it, which is what a desktop harness is.
     */
    private val onScreen: (ScreenId) -> Unit = {},
    /**
     * **This phone's own wall clock** (D-243), for the status strip, the lantern and the time each
     * house message landed, and for nothing else. [onTorch]'s shape: the clock is `platform`'s and
     * `ui` may not see `platform`, so the app root hands it down.
     *
     * Defaulted to no clock at all, which is every render, every screenshot and every test that
     * does not hand one down: a housed phone without one prints a dash where the time goes, and a
     * phone with no house draws the design's times whatever this says.
     */
    private val wallClock: () -> WallTime? = { null },
    /**
     * **This phone's monotonic clock, in milliseconds** (V4, D-269): what every clock the house
     * starts here is stamped with when its push arrives, and read against. [wallClock]'s shape, and
     * for its reason: `platform` holds the clock and `ui` may not see it.
     *
     * Null is the model's own clock, which only [secondPassed] moves: every render, and every test
     * that stands in for `FlowHost`'s second hand.
     */
    private val monotonicMillis: (() -> Long)? = null,
) {

    var state: PanelState by mutableStateOf(initial)
        private set

    /**
     * **What this phone's last fire spends the Access pool by, until the house answers it** (D-262).
     *
     * Set on every Tier-2 fire this phone sends ([fireSurge], [fireStatic], [fireIsolate]) to that
     * ability's weight, and to null by an Egress fire, whose answer restarts no Access bar. Read
     * once, by [ownFireAnswered], and cleared there whatever the answer said.
     *
     * `AbilityFired` does not name the ability (D-239 (b)), so this is how the phone knows which
     * request an answer is for: its own last one. A hold takes two seconds and the answer comes
     * back well inside that, so at most one fire is ever in flight. ⚠️ **The limit:** an armed
     * Revoke's contact is answered with the same `AbilityFired(true)`, and one that lands inside the
     * round trip of a Tier-2 fire is read as that fire's answer; the Access bar then restarts a round
     * trip early, or, if the fire fell into a running pool, restarts when it should not have (late,
     * the safe direction). A wire answer that named the ability would close it.
     */
    private var awaitingFire: Long? = null

    // A phone built already housed draws no design count at its first meeting either (D-247).
    // And a real phone shows its own time from its first frame, housed or not (D-255): the join
    // screen on first launch is drawn before any house exists.
    init {
        if (initial.houseAttached) meeting.houseAttached()
        wallClockRead()
    }

    /**
     * **The plan the host is painting** — the one handed in, or, with none, the one
     * [HomeEditorModel.opening] chooses (D-270).
     *
     * Declared after `init` on purpose: the choice asks [PanelVals.isRenderHarness] of [state], and
     * the phone's clock is only on [state] once `init` has read it. A real phone (one the app
     * handed a clock) therefore opens on **one empty storey, no rooms and no cards**, whichever
     * route reaches the editor; the design's bungalow is left to the harness, so its pictures still
     * compare to the mockup.
     */
    val editor: HomeEditorModel = editor ?: HomeEditorModel.opening(PanelVals.isRenderHarness(state))

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

    /**
     * **Where a fall-through lands, asked when it fires** — [AutoAdvance.to], except for the
     * permissions screen (D-235).
     *
     * The self-test falls through to the Perms screen, which asks for the whole D-161 set in the
     * light. A phone the OS already grants every row has nothing to be asked, so it goes straight
     * past to the join screen. The question is [PermsModel.missingNow], the fresh read the lobby's
     * gate asks, never the screen's cached rows; it is asked when the timer fires rather than when
     * the self-test is drawn, because two rows answer through a callback that lands after launch.
     * **It fails toward the screen**: a row the OS has not answered yet is missing, so a phone that
     * cannot be sure is shown the screen, and a missing grant always is.
     *
     * **Skipped past to [NameDropModel.pastPerms], never to the join screen by name** (D-248): a
     * fresh install's next step is NameDrop, and the skip must not walk it past that.
     */
    fun fallThrough(rule: AutoAdvance): ScreenId =
        if (rule.to == ScreenId.Perms && perms.missingNow().isEmpty()) nameDrop.pastPerms else rule.to

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
     *
     * ⚠️ **[PanelState.houseAttached] is the one field it may not replace**, and the reason is the
     * evening this was found in. The picker builds a fresh [PanelState] to jump to, which carries
     * the field's default — so a jump from a phone with a real house attached landed on a screen
     * that believed it had none, and the stand-ins came straight back on for exactly the screen the
     * player had just chosen to look at. **The owner's jump to the check-in screen is how all three
     * fictional meetings started.**
     *
     * Whether a house is attached is a fact about the socket, not about the panel, so no debug
     * control may set it either way: a picker that could switch it on would be a bench claiming a
     * house, and one that could switch it off is what this was.
     */
    fun jump(to: PanelState) {
        trail.clear()
        arrive(to.copy(houseAttached = state.houseAttached))
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
        val moved = next.screen != state.screen
        // **Leaving the station, by any route, is the reading ending** (D-203). BACK, the dock, a
        // meeting being pushed, a Surge rebooting the phone, the motion budget closing the view:
        // every one of them arrives here, so the report is made here and nowhere else, and a new
        // way off the live view cannot forget to make it. The actor and nothing else.
        val leftTheStation = moved && state.screen in Flow.atTheStation &&
            next.screen !in Flow.atTheStation
        val beginning = moved && next.screen in MeetingModel.STARTS
        if (beginning) {
            meeting.meetingBegan()
            // **The couch's ballot is a meeting's, like everything else the model above clears.**
            // Both the accumulator and the drawn rows go, or the couch would open the second
            // meeting of a round looking at the first one's completed vote — which is worse than a
            // fixture, because it is a real vote that really happened to these people.
            selections.clear()
        }
        state = if (beginning) next.copy(ballots = null) else next
        // **After the state, so that whatever the app root does about it reads the screen this
        // phone is actually on.** The two things it does — stopping a ring, buzzing a Subroutine's
        // question — are both answers to *where the player is now*, and a report made before the
        // move would be answering about where they were.
        if (moved) onScreen(next.screen)
        if (leftTheStation) send(IntentMessage.LeaveTerminal)
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
     * **Arming a Revoke — two seconds of a finger on page 2** (D-141, D-142, D-231).
     *
     * **The request goes to the house, every time, the same way** ([IntentMessage.ArmRevoke]). Until
     * D-231 this method was presentation only: it stepped the tile through READY, ARMED and COOLING
     * DOWN and told nobody, so the house never armed and every touch on two real phones was
     * answered as an ordinary `ContactAnswered`. The bench hid it by minting `RevokeArmed` itself.
     *
     * Whether the arm takes is the house's: it spends the cooldown at the moment of arming and
     * refuses an arm into a running clock, **silently in both outcomes** (D-009, D-142, D-216), so
     * there is no answer to follow. The tile's ARMED is therefore input echo, the design's own
     * reading of it (D-216: *their phone already knows because they held the tile down*).
     *
     * **The echo waits for the public clock** (D-239). Until D-239 it was set on every hold, and a
     * hold into the opening half-cooldown (D-132) or the half after a meeting (D-184) said ARMED
     * over a house that had refused it: on two real iPhones the owner held 2.5 s early, read ARMED,
     * touched, and nothing happened (D-237). [PanelState.revokeClock] restarts at moments every
     * phone is told, so the branch below reads public time and nothing else: not the role, not
     * the house's answer (there is none), and not the request, which goes up the same way on every
     * hold (rule 1).
     *
     * **Reached only from an Insider's tile.** Not by a branch inside it: a Resident's tile takes
     * no pointer input at all, so this is not something a Resident's phone declines to do — it is
     * something a Resident's phone has no way to ask for.
     */
    fun armRevoke() {
        send(IntentMessage.ArmRevoke)
        if (state.revokeClock?.running != true) state = state.copy(revoke = RevokeState.Armed)
    }

    /**
     * **Every clock the house started on this phone, read against the moment its push arrived**
     * (D-239, D-243; V4, D-269). Called by `FlowHost` at each whole second of a running clock. It
     * counts from the push; see [PublicCooldown].
     *
     * Page 2's three public clocks fill; the meeting phase's clock ([PanelState.secondsLeft]) and
     * the Egress's ([PanelState.egressLeft]) drain, the Egress's only while the house has not held
     * it (D-133). Each stops at its end and none of them moves a screen or a light when it gets
     * there: the house does that, by a push, on its own clock (rule 5).
     *
     * ### A deadline, not a count of ticks (V4, D-269)
     *
     * Until D-269 each clock was a count of `FlowHost`'s one-second waits, and the wait restarted on
     * any change to any clock: a push that restarted one clock threw away the part-second every
     * other clock had run, and every wait ran a little long. An Insider's own `AbilityFired` restarts
     * its Access bar and no other phone's, so the Insider's Revoke bar fell behind a Resident's at
     * every fire, which is page 2 differing by role (D-142). Now each clock is stamped, at the push
     * that started it, with this phone's monotonic time ([clocksStamped]), and a reading is the
     * whole seconds since then. Nothing accumulates: two phones sent the same pushes agree to within
     * the difference in when those pushes reached them, however often either redraws.
     */
    fun clocksRead() {
        clocksStamped()
        val now = monotonicNow()
        val s = state
        fun elapsed(at: Long?): Int = if (at == null) 0 else ((now - at).coerceAtLeast(0) / 1_000L).toInt()
        val next = s.copy(
            revokeClock = s.revokeClock?.at(elapsed(stamps.revoke)),
            accessClock = s.accessClock?.at(elapsed(stamps.access)),
            egressClock = s.egressClock?.at(elapsed(stamps.egress)),
            secondsLeft = s.secondsLeft?.let { (stamps.phaseFrom - elapsed(stamps.phase)).coerceAtLeast(0) },
            egressLeft = s.egressLeft?.let { left ->
                if (s.egressHeld) left else left.copy(secondsLeft = (stamps.egressLeftFrom - elapsed(stamps.egressLeft)).coerceAtLeast(0))
            },
        )
        if (next != s) {
            state = next
            stamps.seen(next)
        }
    }

    /**
     * **One second later, on a phone whose clock is the model's own** (no `monotonicMillis` handed
     * down: every test that stands in for `FlowHost`, and every render). The model's clock moves a
     * second and the clocks are read against it, by the same arithmetic a phone uses.
     */
    fun secondPassed() {
        check(monotonicMillis == null) { "this phone reads its own clock; a second cannot be handed to it" }
        ownMillis += 1_000L
        clocksRead()
    }

    /**
     * **How long until a running clock next shows a different whole second**, in milliseconds, or
     * null when none is running. `FlowHost` waits this long between [clocksRead]s, so a clock's
     * number turns over at its own second rather than at some other clock's.
     */
    fun untilNextSecond(): Long? {
        clocksStamped()
        val now = monotonicNow()
        val s = state
        val running = listOfNotNull(
            stamps.revoke.takeIf { s.revokeClock?.running == true },
            stamps.access.takeIf { s.accessClock?.running == true },
            stamps.egress.takeIf { s.egressClock?.running == true },
            stamps.phase.takeIf { (s.secondsLeft ?: 0) > 0 },
            stamps.egressLeft.takeIf { !s.egressHeld && (s.egressLeft?.secondsLeft ?: 0) > 0 },
        )
        return running.minOfOrNull { at -> 1_000L - (now - at).coerceAtLeast(0) % 1_000L }
    }

    /** Whether this phone reads a monotonic clock of its own (a phone), or the model's (tests). */
    val readsItsOwnClock: Boolean get() = monotonicMillis != null

    /** The model's own clock, for a phone handed none: moved only by [secondPassed]. */
    private var ownMillis = 0L

    private fun monotonicNow(): Long = monotonicMillis?.invoke() ?: ownMillis

    /**
     * **When each clock last started, on this phone's monotonic clock** (V4, D-269), and what it
     * started from. Not on [PanelState]: it is when this phone heard a push, which no screen draws
     * and no other phone shares. Each is found by [clocksStamped] from the value a push wrote.
     */
    private class ClockStamps {
        var revoke: Long? = null
        var access: Long? = null
        var egress: Long? = null
        var phase: Long? = null
        var phaseFrom: Int = 0
        var egressLeft: Long? = null
        var egressLeftFrom: Int = 0

        /** The values last stamped or last read; a value other than these is a push's restart. */
        var seenRevoke: PublicCooldown? = null
        var seenAccess: PublicCooldown? = null
        var seenEgress: PublicCooldown? = null
        var seenSeconds: Int? = null
        var seenPhase: MeetingPhase? = null
        var seenEgressLeft: Countdown? = null

        fun seen(s: PanelState) {
            seenRevoke = s.revokeClock
            seenAccess = s.accessClock
            seenEgress = s.egressClock
            seenSeconds = s.secondsLeft
            seenPhase = s.meetingPhase
            seenEgressLeft = s.egressLeft
        }
    }

    private val stamps = ClockStamps()

    /**
     * **Stamp every clock a push has just (re)started with now** (V4, D-269). A page 2 clock is a new
     * value object whenever a push restarts it (never the one [clocksRead] last wrote), the meeting's
     * clock restarts with its phase, and the Egress's countdown is a new value at its fire, each pause
     * and each restart. Found by comparing with what was last seen, so no push's branch has to
     * remember to stamp anything.
     */
    private fun clocksStamped() {
        val s = state
        val now = monotonicNow()
        if (s.revokeClock !== stamps.seenRevoke) stamps.revoke = s.revokeClock?.let { now - it.elapsed * 1_000L }
        if (s.accessClock !== stamps.seenAccess) stamps.access = s.accessClock?.let { now - it.elapsed * 1_000L }
        if (s.egressClock !== stamps.seenEgress) stamps.egress = s.egressClock?.let { now - it.elapsed * 1_000L }
        if (s.secondsLeft != stamps.seenSeconds || s.meetingPhase != stamps.seenPhase) {
            stamps.phase = s.secondsLeft?.let { now }
            stamps.phaseFrom = s.secondsLeft ?: 0
        }
        if (s.egressLeft !== stamps.seenEgressLeft) {
            stamps.egressLeft = s.egressLeft?.let { now }
            stamps.egressLeftFrom = s.egressLeft?.secondsLeft ?: 0
        }
        stamps.seen(s)
    }

    /** Whether any clock [secondPassed] counts still has a second to count. */
    val ticking: Boolean
        get() = state.revokeClock?.running == true ||
            state.accessClock?.running == true ||
            state.egressClock?.running == true ||
            (state.secondsLeft ?: 0) > 0 ||
            (!state.egressHeld && (state.egressLeft?.secondsLeft ?: 0) > 0)

    /**
     * **Read the phone's wall clock onto the panel** (D-243). Called at construction, by `FlowHost`
     * once a second **housed or not** (D-255), and when a house attaches; the panel changes only
     * when the minute does, so a second that changes nothing redraws nothing. False when no clock
     * was handed down, which is every render and test that did not ask for one, and then nothing is
     * read again.
     */
    fun wallClockRead(): Boolean {
        val now = wallClock() ?: return false
        if (now != state.wallTime) state = state.copy(wallTime = now)
        return true
    }

    /** The time a house message landed, on this phone's clock, or null with no clock (D-243). */
    private fun landedAt(): String? = wallClock()?.also { now ->
        if (now != state.wallTime) state = state.copy(wallTime = now)
    }?.clock

    /**
     * **A shift began: the per-seat clocks back to half on every phone** (D-132 at LIGHTS OUT,
     * D-184 at each meeting's end, which starts the next shift). The house-wide Egress clock goes
     * back with them, except at a meeting's end while an Egress is running, which D-184 freezes out
     * of the reset. LIGHTS OUT has no running Egress to freeze, whatever an earlier round left here.
     */
    private fun shiftBegan(opening: Boolean = false) {
        val frozen = !opening && state.egressRunning
        // The house resets the pool to half here whatever the actor last fired (D-184), so an
        // answer still owed from before belongs to no bar.
        awaitingFire = null
        state = state.copy(
            // A new round holds no arm, so an earlier round's echo does not survive into it. A
            // meeting's end keeps it: the house keeps the arm across a meeting.
            revoke = if (opening) RevokeState.Ready else state.revoke,
            revokeClock = PublicCooldown.Revoke,
            accessClock = PublicCooldown.Access,
            egressClock = if (frozen) state.egressClock else PublicCooldown.EgressOpening,
        )
    }

    /**
     * **Arming an Egress — the other two-second hold on page 2** (D-141, D-231).
     *
     * The misfire the design calls *"a game-ending misclick [that] will happen in the dark"* is
     * the whole reason for the hold. **The request goes up and the screen does not move.** The alert
     * is the house's answer (`RoundMessage.EgressOpened`, which every phone in the house gets, this
     * one included), so this phone reaches [ScreenId.Banner] the way every other phone does, by the
     * push. Until D-231 it walked there itself and sent nothing: no Egress could be fired from a
     * phone, and a refused one would have shown the alert on the actor's phone alone.
     */
    fun armEgress() {
        awaitingFire = null
        fireEgress()
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
     * tiles take no pointer input, so a Resident never opens the picker (D-142). [armRevoke]'s
     * shape, one tier down.
     */
    fun fireSurge(row: Int) {
        awaitingFire = Balance.SURGE_COOLDOWN
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
        awaitingFire = Balance.ISOLATE_COOLDOWN
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
     * ### Nothing is drawn afterwards but the actor's own Access bar
     *
     * [fireSurge]'s paragraph. **No branch of any kind**: this method does the same thing whether
     * the pool was ready, whether the house is already jammed, and whoever the actor is. The house's
     * answer is [RoundMessage.StaticOpened], which arrives on *every* living phone in the building
     * including this one — so what this phone shows of the jam is a bystander's view of it
     * (`gdd.md:398`). The one thing that is this phone's own is the Access bar restarting from the
     * house's answer to its own hold, as after a Surge ([ownFireAnswered], D-262): a cooldown, not a
     * light, a sound or a screen, and visible only to somebody reading this page 2 beside another.
     *
     * **Reached only from an Insider's tile**, and not by a check inside it — a Resident's tier-2
     * tiles take no pointer input at all (D-142).
     */
    fun fireStatic() {
        awaitingFire = Balance.STATIC_COOLDOWN
        send(IntentMessage.FireStatic)
    }

    /**
     * **The house's answer to this phone's own fire: the Access bar restarts if the pool was spent**
     * (D-262, review #2's V3).
     *
     * Before this the answer was dropped, so after a Surge, Static or Isolate the bar read the
     * public clock, full, while the house's pool cooled for two minutes or more, and a hold in that
     * time fired into a running pool and nothing happened: the silent no-op D-237 found on REVOKE,
     * one tier down. The bar now draws what the house wrote.
     *
     * **Input echo, not game logic, and the answer goes to the actor alone.** The schema sends
     * `AbilityFired` to the seat that fired and to nobody else, a Resident's page 2 takes no input
     * (D-142) so no Resident is ever answered, and `core` never branches on the target (rule 1), so
     * a Surge at somebody already Revoked restarts this bar exactly as one at anybody else does
     * (`gdd.md:357`: *same cooldown spent* on the actor's device). Only the actor's own tile moves,
     * and only in answer to the actor's own hold; until a phone fires, both roles' page 2 stay the
     * same at every public moment (D-239).
     *
     * **A fire into a running pool restarts nothing**: the house spent nothing (`cooldownStarted =
     * false`), so the bar keeps counting. **An answer with no fire of this phone's in flight moves
     * nothing either**: that is an armed Revoke's contact, and the REVOKE tile's ARMED is D-239's
     * open question, untouched here.
     */
    private fun ownFireAnswered(cooldownStarted: Boolean) {
        val weight = awaitingFire
        awaitingFire = null
        if (cooldownStarted && weight != null) state = state.copy(accessClock = PublicCooldown.fired(weight))
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

    /**
     * **The torch, on the registration scan** — the screen's belief and the LED, in that order.
     *
     * The state moves first so the wash and the label are what the host pressed, and the seam is
     * told second. That order is deliberate: [onTorch] reaches a capture device that may be locked
     * by something else for a moment, and a control that waited for hardware before drawing itself
     * would be a button that feels broken in a lit room. **The screen says what was asked for; the
     * log says what the lens did** (rule 6).
     *
     * Nothing on the in-round scan calls this. `ScanUse.InRound` sets `torch = false`, so the seam
     * refuses by name even if something one day did — see [home.someoneshome.model] and the
     * scanner seam's own KDoc for why a light nobody authored is the thing being guarded against.
     */
    fun toggleTorch() {
        state = state.copy(torch = !state.torch)
        onTorch(state.torch)
    }

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
        // **D-161's gate, and it is checked before the radio rather than after it.** A home that
        // went on the air from a phone that cannot scan is a party already assembled around a
        // device that will fail in the dark, and the host would find out at the first marker.
        if (!setupIsComplete()) return
        // **The home itself and not its name from here down** (E-PAY-3). The row that publishes
        // this action holds a `SavedHome` and hands over its name, so the lookup is here rather
        // than at the button: what the screens address a home by is what a host reads on it, and
        // the list this resolves against is the same one the row was drawn from.
        val home = homes.homes.firstOrNull { it.name == homeName }
        if (home == null) {
            // A name with no home behind it is not a house that failed to go on the air, so it is
            // not `lobby.refusal`'s: nothing was advertised and nothing was refused. It is the
            // list disagreeing with the row, which is the host's own screen and the host's own
            // words — SAVE HOME's refusal path exactly.
            homes.refuse("THERE IS NO HOME CALLED $homeName ON THIS PHONE")
            return
        }
        lobby.hostHome(home) { go(ScreenId.Lobby) }
    }

    /**
     * A tap on a network row: attach to that home, and go into its lobby — **if this phone may.**
     *
     * ⚠️ **The navigation is here now, and that is the fix rather than a tidy-up** (E-PAY-6). The
     * join screen used to call this and then `go(Lobby)` on the next line, unconditionally — so
     * every refusal below walked the player into a lobby they had not joined, with the reason
     * printed on the screen they had just left and a count of nothing on the screen they arrived
     * at. It is `hostHome`'s shape now: **the refusable action owns the navigation, and a refusal
     * stays put.**
     */
    fun attachToHome(home: NearbyHome) {
        // The same gate the host stands behind, on the other side of the party. D-161 is about a
        // seat rather than about hosting: *the lobby will not seat a device that cannot do the
        // job, and the host is never handed a party that will fail in the dark.*
        if (!setupIsComplete()) return
        // And the name, which is the second thing a seat needs and the one the room will need at a
        // meeting. `attachTo` writes its own refusal; this only reads whether it went.
        lobby.attachTo(home)
        if (lobby.attached == home) go(ScreenId.Lobby)
    }

    /**
     * **LEAVE THIS HOUSE — and the phone goes back to the join screen** (E-PAY-6).
     *
     * A push rather than a `go`, and that is the whole of the navigation decision: the trail behind
     * this phone runs back through a lobby it is no longer in, and a back arrow that walked into it
     * would draw a house that is not listening. `push` clears the trail, which is what it is for.
     *
     * Everything else is [LobbyModel.leaveLobby]'s — the socket, the line, the counts. Nothing here
     * predicts what the house will publish to the people still in the room.
     */
    fun leaveLobby() {
        lobby.leaveLobby()
        // A phone that walked out holds no round (D-259): the lobby's own LEAVE, and the disconnect
        // screen's, which can be pressed in the middle of one.
        roundForgotten()
        push(ScreenId.Join)
    }

    /**
     * **TERMINATE — the host closes the house, and lands where a leaver lands** (E-PAY-6).
     *
     * The host's own phone gets no different treatment from the clients', which is the point: what
     * a terminate produces is a network with no house on it and six phones on the join screen. The
     * host reaches that by pressing the control and everybody else by [houseClosed] below, and the
     * two roads end in the same room.
     *
     * Refused on a phone that is not hosting one layer down, and the control is not drawn there.
     */
    fun terminateHouse() {
        lobby.terminateHouse()
        roundForgotten()
        push(ScreenId.Join)
    }

    /**
     * **The host closed the house: the client's half, reached by a push** (E-PAY-6).
     *
     * `newRound`'s sibling and wired the same way — the transport reports the arrival, the app root
     * calls this, and neither `platform` nor the transport ever reaches a screen. What differs is
     * everything after: `RoundEnded` keeps the home, the seats, the settings and the socket and
     * walks this phone to the *lobby*, and this one has no lobby to walk to.
     *
     * The honest line is [LobbyModel.HOUSE_CLOSED] and it is **flagged as proposed copy**, at the
     * constant. It is put on the join screen rather than on a dialog for rule 6's reason read in a
     * lit room: nothing pops up, nothing has to be dismissed, and what the player sees is the screen
     * they would have been on with a sentence saying why they are on it.
     */
    fun houseClosed() {
        lobby.houseClosed()
        roundForgotten()
        push(ScreenId.Join)
    }

    /**
     * **D-161's gate, asked once and answered in the host's own words** (E-PAY-5).
     *
     * False leaves a refusal on the lobby, naming the rows — *the whole Perms set, camera
     * included*, and which rows are missing is the only part a person can act on. The screen does
     * not move: a refusal that walked forward would be the fall-through this unit exists to stop,
     * one screen along.
     *
     * It reads [PermsModel] rather than an OS, and that is the boundary working: what a grant *is*
     * comes from `platform` through the seam the app wired, and what it *means for a seat* is
     * ruled here, where the lobby can see it.
     *
     * ⚠️ **[PermsModel.gateRefusalNow] and not `gateRefusal`, and the difference is a night's
     * worth of unplayable builds** (N6). `gateRefusal` reads the Perms screen's display map, which
     * opens empty and is filled by exactly one `LaunchedEffect` — so this asked *what did that
     * screen last draw* on every path, including the two benches that never compose it, and
     * answered a fully granted 13 Pro with all five rows. **A gate whose input is another screen's
     * side effect is a gate that is silent about phones that took a different road.** The ruling
     * is that the gate reads the OS at decision time; the sentence is unchanged and so is the
     * boundary.
     */
    private fun setupIsComplete(): Boolean {
        val why = perms.gateRefusalNow() ?: return true
        lobby.refuse(why)
        return false
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
     * **LIGHTS OUT — the transition, and the one report this layer owes the house.**
     *
     * ⚠️ **Its entire body used to be `push(ScreenId.Armed)`, and that was E-PAY-1.** Presentation
     * only was the right shape while there was no loop to tell: it did not lock the seat ledger,
     * draw the Insider count, stamp the balance values into a recording or tell another phone
     * anything, because every one of those is arming and nothing in the app could arm. It stayed
     * that shape after the loop arrived, and the cost was measured on five seats — a full lobby, a
     * press that landed, and no round anywhere in the house. Every other phone stood in the lobby
     * forever.
     *
     * So [onLightsOut] is told, and the screen moves when the house armed — never on the press
     * alone, which walked a refused host to ARMED (D-226). With a house attached the walk is the
     * house's opening push, on every phone alike (D-234, [roundOpened]). The division is unchanged:
     * **nothing about the round is decided here.** What the host did is a fact; what it means is
     * the house's, one module up, where `core` can be seen.
     *
     * The gate is not here either and never was. `LightsOut(ready = lobby.readyToArm)` draws a
     * `HoldToConfirm(enabled = ready)`, and a disabled one takes no pointer input at all (D-141) —
     * so a short lobby cannot begin the hold, let alone finish it. The house checks the same gate
     * again on arrival, in a lit room, out loud.
     */
    fun lightsOut() {
        onLightsOut { push(ScreenId.Armed) }
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
     *
     * **And the panel forgets the round** (D-259, [roundForgotten]). `Rules.armed` constructs the
     * next round in the house, but nothing constructed the phone's: the second round's ARMED drew
     * the first round's OUT, meter and work order. Called on every phone, the host's by its button
     * and every phone's again by the house's push, and the second call finds nothing to forget.
     */
    fun newRound() {
        lobby.roundEnded()
        roundForgotten()
        push(ScreenId.Lobby)
    }

    /**
     * **A later round's push reached a phone still in an earlier one** (V1, D-267): the house ended a
     * round while this phone was off the air, and the push that says so is sent once. [newRound]'s
     * every reset on this phone, and the walk to the lobby, so the new round's opening push walks it
     * to ARMED as it does every seated phone (D-234). **It tells the house nothing**: the line this
     * phone typed goes from here only ([LobbyModel.line]), never through the link, because the house
     * ended that round long ago and a link told it again would end the one now running (D-252).
     */
    fun houseBeganAnotherRound() {
        lobby.line.roundEnded()
        roundForgotten()
        push(ScreenId.Lobby)
    }

    /**
     * **Everything this phone holds about the round, gone; everything it holds about the evening,
     * kept** (D-259). The round boundary, on the panel side.
     *
     * [PanelState.betweenRounds] is the panel's half and names what it keeps. The rest is the
     * models beside it that hold a round's worth of state:
     * - the couch's ballot ([selections]) and the meeting's own echo and counts ([meeting]): unsaid,
     *   and on a housed phone the last roster's names go too, until the next `RosterIssued`;
     * - the Subroutine in hand ([subroutines]): nothing entered, no card standing;
     * - the house's texts still on the lock screen ([notifications]): each one this round's thread
     *   holds is taken off, by its own value, so a fixture on an unhoused bench is left alone.
     *
     * **Not the lamp, and not a Surge in flight.** The lamp is the house's (rule 5) and the next
     * round's arming sets it; a Surge runs itself out in five seconds, and clearing it mid-sequence
     * would leave its slam painted with nothing left to lift it.
     */
    private fun roundForgotten() {
        selections.clear()
        meeting.meetingBegan()
        if (state.houseAttached) {
            meeting.houseOpenedMeeting()
            meeting.houseNamed(emptyList())
        }
        subroutines.beganAgain()
        subroutines.opened(null)
        state.houseTexts.forEach { notifications.dismiss(Notifications.houseText(it)) }
        state = state.betweenRounds()
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

    // ---- Is there a house ----------------------------------------------------------------------

    /**
     * **A house is attached to this phone, or is not** ([PanelState.houseAttached]).
     *
     * The one honest question, asked once and answered from real state, that decides every
     * stand-in behaviour in the app at the same time: the presentation timers in [Flow.autoAdvance],
     * the bench's fake house in [FlowHost], and every fixture [PanelVals] falls back to. **They were
     * three independent decisions and that is why the fiction survived three separate attempts to
     * remove it** — each one switched off the stand-in it knew about and left the other two running.
     *
     * Called by `app` on the seam that actually knows: `RoundSeam.linked`, which fires on the host
     * over loopback and on every client over the network, and on nothing that is not in a round.
     *
     * **It moves no screen and clears no field.** A phone that has just acquired a house is standing
     * exactly where it was; what changes is only that from this moment the phone will not invent
     * anything, and will wait for the house instead. That is the whole of it, and it is deliberately
     * not a reset: a reset here would wipe a round in progress every time a socket reconnected.
     */
    fun houseAttached(attached: Boolean) {
        state = state.copy(houseAttached = attached)
        if (attached) {
            // The strip's clock is the phone's own from this moment, not from FlowHost's next second.
            wallClockRead()
            // **And the lock screen holds nothing the house did not send** (D-243). The design's
            // two standing texts (`21:01`, `21:03`) are the stand-ins a phone with no house shows;
            // on a housed phone they were two fake times under the clock all evening. Each is
            // removed by its own value (words, sender and time), so a reconnect, which calls this
            // again, clears only a real text identical to a stand-in to the minute.
            Notifications.stored.forEach(notifications::dismiss)
            // **And the meeting counts are the house's from here** (D-247): the design's
            // `4 OF 6 ANSWERED` was on every housed caller's screen. A reconnect keeps a count the
            // house has already written.
            meeting.houseAttached()
        }
    }

    // ---- Subroutines ---------------------------------------------------------------------------

    /**
     * **A card was just read, and this is the phone remembering which one** ([PanelState.scannedAt]).
     *
     * Called by the scan flow at the moment the read goes **up**, not when the answer comes back
     * down: the house's answer names the entry, the kind and the parameters and carries no marker
     * at all — `SubroutineInstance` says so on its own type, and the effect that does carry one is
     * permitted to nobody (D-111). So the only moment this fact exists on a phone is the moment the
     * lens resolves the symbol, and this is where it lands.
     *
     * **It records and nothing else.** No screen moves, because which Subroutine a card holds is
     * the house's answer and not the reader's — a client that walked itself to the caught scan on
     * its own read would be the device deciding what a card is. The read still goes up as an intent
     * and the house still answers it; this is the half of that act the phone keeps.
     *
     * **A second read replaces the first**, which is the physical fact: a player who scanned again
     * is standing at a different card, and D-136 is explicit that a hand-over names *which card
     * opened the work* rather than where anybody is now. What keeps those two in step is that BEGIN
     * reads this and [PanelState.current] together, in one act, one tap after the read that wrote
     * both.
     */
    fun scanning(marker: MarkerId) {
        state = state.copy(scannedAt = marker)
    }

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
     * **Both parameters default to what the last scan left on [state], and the fixture stands in
     * where no scan has happened.** In play the scanned card says which Subroutine opens and which
     * id the entry is filed against; on a phone with no house attached there is one Subroutine, it
     * is [PanelVals.CURRENT], and there is no card at all.
     *
     * ⚠️ **The defaults are the fix, and their absence was the outage.** They were the fixture and
     * `null`, and `viaActions` wired the button as `beginSubroutine = { beginSubroutine() }` — so
     * every BEGIN in every round opened Handshake whatever the house had dealt, and every entry was
     * refused by `SubroutineModel.posted` for want of a marker. Nine of the ten Subroutines were
     * unreachable and the return half of the core loop had never run. The parameters stay
     * parameters — `SubroutineTest` and the render harness both drive them explicitly — but what
     * they fall back to is now **this seat's own last read** rather than the port's furniture.
     *
     * The pair is read in one act, from two fields written by one scan: [PanelState.current] by the
     * house's answer and [PanelState.scannedAt] by the read that asked for it. Reading them apart —
     * a kind off one scan and an id off another — is the failure this method is positioned to make
     * impossible, and it is why the marker is not looked up from anywhere else.
     */
    fun beginSubroutine(
        /**
         * ⚠️ **The fixture is the unhoused port's alone.** N1 pointed this default at the house's
         * own answer and left `PanelVals.CURRENT` behind it for the port, which is right — and on a
         * housed phone whose house has not answered, that fallback is the same fiction one layer
         * down from the screen. `PanelVals.current` is null in exactly that case and the caught-scan
         * screen draws no BEGIN over it; this is the same rule where the call is made, so a press
         * arriving from anywhere else cannot open a Subroutine the house never dealt.
         *
         * **A housed phone with nothing open begins nothing**, and the body below already knows how
         * to do that: an unresolvable target returns without moving the screen.
         */
        subroutine: Subroutine? = state.current?.subroutine
            ?: PanelVals.CURRENT.subroutine.takeUnless { state.houseAttached },
        /**
         * **Which card opened this**, so the hand-over can say so (E-P2-1).
         *
         * **The last card this phone read, and null where it has read none.** The fixture's
         * `CurrentSubroutine` carries a `MarkerShape` — the printed symbol a player looks for — and
         * not a card id, which is the right thing for it to carry and the wrong thing to send, so
         * the id lives beside the row instead: see [PanelState.scannedAt] and [scanning].
         *
         * **A phone with no house attached still opens the work, takes the answer, and posts it
         * nowhere**, because it has scanned nothing and this is null; see `SubroutineModel.posted`,
         * which refuses a hand-over with no marker. That is the bench's honest state and it is
         * pinned by a test — it was also, for one whole build, the *product's* state.
         */
        marker: MarkerId? = state.scannedAt,
        /**
         * **The question the house dealt at this scan** (D-238) — off the same row as the kind, so
         * a kind from one scan is never drawn with a question from another.
         *
         * **Null opens nothing on a housed phone.** The screens draw from what [SubroutineModel.deal]
         * writes, and a housed phone that opened work without dealing it would draw the bench's
         * fixture in front of a player and hand the house an answer to a question it never asked —
         * which is exactly what every phone in every round did until D-238. On a phone with no
         * house, null is the port's own question and the fixture is what it draws.
         */
        parameters: List<Int>? = state.current?.takeIf { it.subroutine == subroutine }?.parameters,
    ) {
        val screen = subroutine?.screen ?: return
        if (parameters == null && state.houseAttached) return
        if (parameters != null) {
            // Read at the arrival already (see [currentFrom]); a refusal here is a hand-made row,
            // and it opens nothing for the same reason.
            try {
                subroutines.deal(subroutine.kind, parameters)
            } catch (_: MalformedSubroutineParameters) {
                return
            }
        }
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
        said(message)
        // Every clock this push started is stamped with the moment it arrived (V4, D-269).
        clocksStamped()
    }

    private fun said(message: RoundMessage) {
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

            // **The house's answer to this phone's own fire, and to nobody else's** (D-262). It is the
            // only thing a Tier-2 fire sends back to the actor (`gdd.md:398`), and what it moves is
            // the actor's own Access bar: *same cooldown spent* (`gdd.md:357`), never a light, a
            // sound, a screen or anything about the target. See [ownFireAnswered].
            is RoundMessage.AbilityFired -> ownFireAnswered(message.cooldownStarted)

            // **A touch, answered, and the screen does not move** (R1, D-216). Every touch puts one
            // of these, or an armed Insider's `AbilityFired`, on each phone that touched, so that the
            // frame count never follows the role. A phone that showed it would be a phone announcing
            // that it had been touched; this branch is a named no-op and stays one.
            is RoundMessage.ContactAnswered -> Unit

            // **Somebody else's ability, answered** (D-263). Every living phone but the actor's is
            // sent one where the actor is sent `AbilityFired`, so that the frame count never says who
            // asked. It carries nothing, and a phone that moved on it would be announcing that an
            // ability had fired somewhere: a named no-op, and it stays one.
            is RoundMessage.AbilityBlanked -> Unit
            // **Somebody else's Sync Pulse, answered** (C2). AbilityBlanked's reason one system
            // over: every living phone but the tapper's is sent one, so the frame count never says
            // who is at a node with their taps in. A named no-op, and it stays one.
            is RoundMessage.SyncPulseBlanked -> Unit

            // **The house notice** (D-187, D-223). It arrives on every phone at every meeting, just
            // before the push that opens the notices, and it is what the notices screen draws: the
            // seats it names, or none, and none draws nothing. It moves no screen; the phase push
            // beside it does. The words for a named seat are `noticeBody`'s, and the owner's.
            is RoundMessage.NoticeIssued -> state = state.copy(notice = message.unreachable)

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

            // **A Resident's frame of the offer's size, and the screen does not move** (D-258).
            // It exists so that every living phone gets one frame where an Insider's gets the
            // offer. It carries padding and no list, and this branch is a named no-op: a phone that
            // did anything with it would be a phone announcing it is not an Insider's.
            is RoundMessage.IsolateTargetsBlanked -> Unit

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
            //
            // **Stamped with the moment it landed** (D-243): the lock screen keeps it for as long as
            // nobody swipes it, so NOW would be a lie a minute later, and the house thread keeps it
            // for good, in arrival order.
            is RoundMessage.MessageDelivered -> {
                val text = HouseText(landedAt() ?: PanelVals.NO_READING, message.body)
                state = state.copy(houseTexts = state.houseTexts + text)
                notifications.houseSent(Notifications.houseText(text))
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
            // **The T card's answer: the live view, or ONE READER AT A TIME** (D-199). Both are
            // the house's word about this phone's own scan, and the phone moves on it and decides
            // nothing. The station screen is not a Subroutine and has no BEGIN: reading starts the
            // moment the house says this phone is the reader.
            is RoundMessage.TerminalAnswered ->
                push(if (message.reading) ScreenId.TermLive else ScreenId.TermBusy)

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

            // **The radio's key, and the screen is not where it goes** (D-212). It belongs to the
            // contact radio, which is `platform`'s and is not built yet (unit 4); routing it is unit
            // 3's. Nothing on [PanelState] may hold it -- a seed a screen could draw is a seed a
            // screenshot could carry -- so this branch is a named no-op and stays one.
            is RoundMessage.ContactSeedIssued -> Unit

            // **The house's opening text — the heavy one, and the round starting** (D-118, D-119).
            // Heavy is the ruling and not the volume: the whole house dims at the same moment, and
            // *that says exactly what it is.* It is one of exactly two arrivals permitted to do it.
            //
            // **It carries no body**, which is why nothing is stored: the opening text is the
            // house's own and the same on every phone, so `Notifications.arrivals` already holds
            // what the banner says and the message's job is to say *now*. A body on this kind would
            // be a second copy of one sentence.
            //
            // **And it is what walks every phone in the lobby to PERIMETER ARMED** (D-234), with
            // this banner over it on every phone (D-254). See [roundOpened].
            //
            // **And it is the first shift beginning, on every phone at once** (D-132, D-239): page
            // 2's clocks start here, at half, the same on an Insider's phone and a Resident's.
            //
            // **And it is when the house thread's opening rows landed** (D-243), on this phone's clock.
            is RoundMessage.OpeningMessage -> {
                shiftBegan(opening = true)
                val at = landedAt()
                // A new round holds no last round's Egress count, quoted line or Residents (D-247).
                state = state.copy(
                    openedAt = at, houseTexts = emptyList(), endedAt = null, endedBy = null,
                    egressesContained = 0, heldOver = null, residents = null,
                )
                roundOpened()
            }

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
            //
            // **And an Insider's carries the line this phone handed over** (D-116, D-101, D-247),
            // which the house thread quotes back. Held only on an Insider's phone and only if the
            // house sent words; a Resident's message carries none.
            is RoundMessage.RoleRevealed -> state = state.copy(
                role = if (message.insider) PanelRole.Insider else PanelRole.Resident,
                heldOver = message.line.takeIf { message.insider && it.isNotBlank() }?.let(::HeldLine),
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
            // before one had a bar that did not move. The roster is one slot per seat, so its
            // non-null slots are that number, it is a public lobby fact, and it now arrives at arming. Written
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
                //
                // **A null slot is a seat index nobody holds, and it becomes a blank here** (D-274).
                // The list is indexed by seat, so a round armed without seat 2 sends a null at 2;
                // every reader below already treats a blank as nobody — no ballot row, no Surge row,
                // no Resident on the ending — and keeps the index, which is the seat. The seat total
                // is the non-null slots, and never the length: with a hole in the numbering the
                // length is the highest seat plus one, not the number of people in the round.
                // Null stays null on this phone (D-277): nobody at that index, which no screen
                // offers, and never the same thing as a blank, which is somebody with no name.
                val roster = message.roster
                meeting.houseNamed(roster)
                state = state.copy(roster = roster, seats = roster.count { it != null })
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
                // **A new meeting's counts start unsaid** (D-247), before the gate's first count,
                // which follows in the same burst. Never the last meeting's, never the design's.
                if (state.houseAttached) meeting.houseOpenedMeeting()
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
            is RoundMessage.StandAndWalkIn -> {
                // The out seat's ring, and its counts start unsaid for the same reason (D-247).
                if (state.houseAttached) meeting.houseOpenedMeeting()
                push(ScreenId.Ghost2)
            }

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
                    // **The phase's clock starts now, at the window the house runs it for** (D-243).
                    // Null at the gate, which runs on a count and not a clock (D-104).
                    secondsLeft = Countdowns.windowOfPhase(
                        message.phase, message.voteWindowSeconds ?: state.voteWindowSeconds,
                    ),
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

            // The meeting ends the shift it interrupted and starts the next (D-170), and every
            // clock goes back to half on every phone (D-184, D-239).
            is RoundMessage.MeetingEnded -> {
                shiftBegan()
                // The meeting's last clock goes with it (D-243).
                state = state.copy(secondsLeft = null)
                housePushed(message)
            }

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
            // **And the countdown on the widget is the house's** (E-R2-7, closed by D-243):
            // `remaining`, in ticks, through `Balance.wholeSeconds`, the conversion `model` names
            // so that `ui` holds no opinion about the tick rate (D-239).
            is RoundMessage.EgressOpened -> {
                state = state.copy(
                    egressType = message.type.name.uppercase(),
                    // The room, which is what a player navigates a dark house by and what the
                    // design says the widget names. Copied in the house's own draw order — the pair
                    // is read positionally by nothing, but two phones listing it differently would
                    // be rule 4 arriving as *the screens disagree*.
                    egressNodes = message.nodes.map { it.room },
                    egressRunning = true,
                    // The house-wide clock restarts at full on the fire (D-151), and every phone is
                    // told it fired by this push, so the bar restarts on every phone (D-239).
                    egressClock = PublicCooldown.EgressFired,
                    // **The countdown, from the house's own number** (D-243): what it fired with is
                    // both what is left and what the bar is a fraction of.
                    egressLeft = Balance.wholeSeconds(message.remaining).let { Countdown(it, it) },
                    egressHeld = false,
                )
                arrived(ScreenId.Banner)
            }

            // **The countdown stopping and starting again, on the containment screen** (D-133).
            //
            // **Both halves land** (D-243). Whether the number is moving is a fact about the house
            // the widget has to draw: a meeting called by reporting a Revoke pauses the Egress, and
            // a countdown that went on falling through a meeting the whole house had stopped for
            // would be the one screen contradicting the room. The number itself resets the phone's
            // count at every pause and restart, through `Balance.wholeSeconds` (D-239), which
            // rounds up so the phone never reads less than the house holds. The length stays the
            // one the Egress fired with; a phone that missed the fire falls back to the house's
            // timer.
            is RoundMessage.EgressHeld -> state = state.copy(
                egressHeld = !message.running,
                egressLeft = Balance.wholeSeconds(message.remaining).let { left ->
                    Countdown(left, state.egressLeft?.ofSeconds ?: maxOf(left, Balance.wholeSeconds(Balance.EGRESS_TIMER)))
                },
            )

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
            // Counted for the Residents' ending (D-247): every Egress the house says was contained.
            is RoundMessage.EgressContained -> state = state.copy(
                egressRunning = false, egressLeft = null,
                egressesContained = (state.egressesContained ?: 0) + 1,
            )

            // **Permitted to nobody, so it cannot arrive** — its row is gone (D-131, D-156), and
            // what every phone gets instead is the ending carrying `WinRoute.EgressUncontained`.
            // Named for `PresenceChanged`'s reason.
            is RoundMessage.EgressSucceeded -> Unit

            // Stamped before the push, so the ending is drawn with its time from its first frame (D-243).
            is RoundMessage.RoundEnded -> {
                val at = landedAt()
                // **The quoted line goes when the round ends** (D-116's promise, kept on this side).
                state = state.copy(endedAt = at, endedBy = message.by, heldOver = null)
                Flow.endings[message.winner]?.let { push(it.to) }
            }

            is RoundMessage.InsidersRevealed -> state = state.copy(
                revealed = message.insiders.map { RevealedInsider(it.name, it.line) },
                // **Who the Residents were: the roster less the seats just named** (D-247), in seat
                // order, blanks left out. By seat and never by name, so two players who gave one
                // name cannot both be counted as Insiders.
                residents = state.roster?.let { roster ->
                    val insiders = message.insiders.map { it.seat.index }.toSet()
                    roster.withIndex().mapNotNull { (seat, name) ->
                        name?.takeIf { seat !in insiders && it.isNotBlank() }
                    }
                },
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
        // **The question is read here, at the arrival, and a refusal opens nothing** (D-238; the
        // boundary `MalformedSubroutineParameters` names). The house cannot deal a question this
        // client cannot draw — `SubroutineDrawTest` reads thousands of its draws through these
        // readers — so this is the second of two refusals, and its direction is the one that
        // matters: the phone stays on the scan rather than opening work over a question it could
        // not draw (rule 6: silent to the player, and the house's own log holds what it dealt).
        try {
            SubroutineQuestion.read(opened.subroutine, opened.parameters)
        } catch (_: MalformedSubroutineParameters) {
            return null
        }
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
            parameters = opened.parameters,
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
     * **The house armed, and every phone still standing in the lobby goes to PERIMETER ARMED**
     * (D-234).
     *
     * Found on two real iPhones on 2026-11-16: after LIGHTS OUT the host's phone said PERIMETER
     * ARMED and the guests' did not. The host walked itself there on the house's yes (D-226, a call
     * on the host's own phone), and nothing the house sent walked anybody else: `OpeningMessage`
     * raised the opening banner, so a guest went from the lobby straight to the springboard, and
     * the host, already on ARMED, never saw that banner at all (ARMED is the house's screen, and a
     * banner does not land on one). Two phones, two different first frames of the round.
     *
     * Now the one message every seat is sent once, at arming, is what walks every seat there, the
     * host's included over its own loopback socket, and the host no longer walks itself. One path,
     * the same frame on every phone (rule 1). The two screens are the lobby and the one-line screen
     * it opens, which is where a seated phone stands when the lights go out. Anywhere else (a phone
     * the house is catching up after a gap) the message is what it always was, the banner.
     *
     * ### And the banner lands with ARMED, on every phone (D-254, restoring D-118)
     *
     * D-234 walked the seated phone to plain ARMED, and ARMED is a screen the house drives, so the
     * opening banner, one of D-118's only two heavy notifications, was drawn at arming on no phone
     * at all: one of the two events allowed to dim the house had quietly stopped dimming it.
     * D-118 ties the dim to the opening text's arrival (*a dim during the opening message is the
     * round starting*) and D-119 has every phone dim and undim on the same ten seconds, so the
     * banner lands over ARMED in the frame ARMED does: [ScreenId.ArmedNotify], the opening text
     * over PERIMETER ARMED, which a swipe or [HEAVY_HOLD] takes back to ARMED. One message, one
     * screen, the same frame on every phone, whatever its role (D-142's parity).
     */
    private fun roundOpened() {
        if (state.screen in Flow.seatedBeforeArming) push(ScreenId.ArmedNotify) else arrived(ScreenId.Notify)
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
        // The banner the panel drew, which on a housed phone is the house's own text (D-247).
        val arrival = PanelVals(state).arrival ?: return
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
        // **The back arrow and the left-edge swipe, at last reaching the thing that was built for
        // them** (E-PAY-6). `back` and `canGoBack` have existed on this class since the flow was
        // ported and had exactly one caller between them, in a test — the owner's *no back
        // navigation anywhere* is that gap, seen from the glass.
        canGoBack = { canGoBack },
        back = { back() },
        leaveLobby = ::leaveLobby,
        terminateHouse = ::terminateHouse,
        armRevoke = ::armRevoke,
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
        requestGrant = perms::request,
        requestMissingGrants = perms::requestMissing,
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
 *
 * ### ⚠️ And that paragraph was a rule nothing enforced, so [PanelState.houseAttached] enforces it
 *
 * *Must never be true in a real round* was written here from the first day and was true of nobody:
 * the flag was set from a launch switch, a second way to have a real round was added later, and
 * two of the owner's phones then ran a fake house beside a real one for an entire evening. **A
 * caveat in a KDoc is not a guard.**
 *
 * So the caller no longer decides this alone. [PanelState.houseAttached] is asked first and
 * overrules both drivers: with a house attached, neither the stand-in **nor** the presentation
 * timers in [Flow.autoAdvance] may fire, whatever [standingInForTheHouse] says. The two were
 * separately gated before — one behind this parameter and one behind nothing at all — which is why
 * the fiction survived being switched off.
 *
 * **A phone with a house attached moves on exactly two things: a push, and the player's thumb.**
 * Everything else it does is waiting, and waiting is honest.
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
    // **THE ONE QUESTION, ASKED ONCE, ABOVE BOTH DRIVERS** ([PanelState.houseAttached]).
    //
    // With a house attached this composable moves nothing. Both locals below go null, so neither
    // branch of the effect can be reached, and the only things left that can change the screen are
    // the two that are entitled to: a push from the house, and the player's own thumb.
    //
    // ⚠️ **It has to sit above BOTH of them, and that is the whole finding.** `standingInForTheHouse`
    // gated the stand-in alone, so switching it off left `pending` — the production
    // [Flow.autoAdvance] table — still walking a housed phone from the notice into a discussion the
    // house had not opened. Two stand-ins, gated in two places, one of them not gated at all: the
    // owner's meeting ran `ASSEMBLE → NOTICE` on the bench's four seconds and `NOTICE → DISCUSSION`
    // on production's nine, and no single switch could ever have stopped both.
    val housed = model.state.houseAttached
    val screen = model.state.screen
    // **Except for the windows that are the phone's own** ([Flow.ownTimers]). Switching the table
    // off wholesale did not only stop the fiction, it stopped the scan's safety window and the
    // ten seconds a heavy notification lasts — neither of which any house can push, and both of
    // which left a housed phone stuck rather than honest. The Surge still suppresses everything.
    // And the notice's nine seconds once the house has opened the talk ([Flow.ownOncePhaseOpened]).
    val runsItsOwn = Flow.runsItsOwnTimer(model.state)
    val pending = if (surge == null && runsItsOwn) model.pending else null
    val awaiting = if (standingInForTheHouse && surge == null && !housed) model.awaitingPush else null

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

    // **Every clock the house started, read against the moment its push arrived** (D-239: page 2's
    // three; D-243: the meeting phase's and the Egress's; V4, D-269). One loop for the life of the
    // model, keyed on nothing that changes: it was keyed on the clocks' values, so every second and
    // every push relaunched it, a push that restarted one clock threw away every other clock's part
    // second, and the waits ran long. Each read is the whole seconds since that clock's push
    // ([FlowModel.clocksRead]), and the loop wakes at the next whole second of whichever running
    // clock turns over first. Nothing here moves a screen or the lamp.
    //
    // A model handed no clock of its own (a render, a bench with no house) is moved a second at a
    // time instead, which is the same arithmetic on the model's own clock.
    LaunchedEffect(model) {
        while (true) {
            if (model.readsItsOwnClock) {
                model.clocksRead()
                delay((model.untilNextSecond() ?: IDLE_CLOCK_MILLIS).coerceIn(MIN_CLOCK_MILLIS, IDLE_CLOCK_MILLIS))
            } else {
                delay(1_000L)
                if (model.ticking) model.secondPassed()
            }
        }
    }

    // **The phone's own wall clock, on any real phone, housed or not** (D-243, D-255). Read once a
    // second; the panel changes only when the minute does. Display only: nothing in the round reads
    // it. A model handed no clock (the render harness) stops at the first read.
    LaunchedEffect(model) {
        while (model.wallClockRead()) delay(1_000L)
    }

    LaunchedEffect(screen, pending, awaiting) {
        // Only if nothing else moved first. The keys above already restart this effect on every
        // screen change, so reaching either branch means the screen is still the one that owed it.
        if (pending != null) {
            delay(pending.afterMillis.toLong())
            // Where it lands is asked now, not when the effect started: see `fallThrough` (D-235).
            if (model.state.screen == screen) model.push(model.fallThrough(pending))
        } else if (awaiting != null) {
            delay(HOUSE_STAND_IN_MS)
            if (model.state.screen == screen) model.push(awaiting.to)
        }
    }
    Screen(
        model.state, model.actions(), model.editor, model.homes, model.lobby, model.meeting,
        model.subroutines, model.notifications,
        // Named from here on, because the two after the notifications are not in the order a
        // reader would guess and a positional argument that lands one slot over is a screen drawn
        // with the wrong model and no compile error to say so.
        perms = model.perms,
        nameDrop = model.nameDrop,
        lamp = model.lamp,
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

/**
 * **How often `FlowHost` looks at the clocks when none of them is running**, and the longest it
 * ever waits (V4, D-269): a push that starts one is read within this. A quarter second, so a bar
 * that starts is drawn moving inside the second it started in.
 */
private const val IDLE_CLOCK_MILLIS: Long = 250L

/** The shortest wait between two reads, so a clock that turns over now is not read twice in a frame. */
private const val MIN_CLOCK_MILLIS: Long = 16L
