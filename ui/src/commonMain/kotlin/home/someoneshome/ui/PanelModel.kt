package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.HomeReview
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.OrderLine
import home.someoneshome.model.RoomKind
import home.someoneshome.model.Seat
import home.someoneshome.model.WinRoute

import androidx.compose.ui.graphics.Color

/**
 * Every screen the device can be showing.
 *
 * **A screen list, not a navigation graph.** It says what exists; it says nothing about what may
 * follow what, because in play the house decides that and the device is told. The ordering here
 * follows the design's own grouping, which is a review convenience only.
 */
enum class ScreenId {
    // Cold start — light-field, because the house lights are still on. `NameDrop` is the one
    // setting the OS will not let the app read or change (D-248): asked once, in the light.
    Boot, Perms, NameDrop, Join,

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
    //
    // `ArmedNotify` is PERIMETER ARMED with the house's opening text over it (D-118, D-254): the
    // one message every seat gets at arming walks a seated phone to ARMED and lands the heavy
    // banner on it in the same frame, so the house dims at one moment on every phone. Its own id
    // for `Notify`'s reason below: the dim is a property of a drawn panel and every sweep walks
    // `ScreenId.entries`.
    Secret, Armed, ArmedNotify, Notify, Reveal, RevealThread,

    // The springboard — identical for both roles.
    //
    // `Quiet` and `LockNotify` are the last two the same way `Notify` and `Banner` are: a screen
    // with a notification on top of it. They are separate ids because the two things a
    // notification does — dim or not, banner or under the clock — are properties of a *drawn
    // panel*, and every sweep in this module walks `ScreenId.entries`. A quiet banner hiding
    // behind a flag on `Notify` would be a presentation none of them ever looked at.
    Home, Page2, Lock, Quiet, LockNotify,

    // **Tier 2's two target lists** (the owner's 2026-08-22 targeting ruling). The one place in the
    // game a control opens a view rather than arming in place — D-142 settled that arming happens on
    // page 2 *in place*, and it settled it about the Revoke and the Egress, which have no target to
    // pick: contact is the Revoke's confirmation and the house draws the Egress's nodes. Surge and
    // Isolate pick from a LIST, and **the glow of reading the list is the deliberate price** of
    // using them.
    //
    // Two ids rather than one screen with a mode, for the reason every sweep in this module walks
    // `ScreenId.entries`: a picker hiding behind a field on a shared screen is a picker none of them
    // ever looked at, and these two draw different things — names against shapes-and-rooms.
    //
    // **There used to be a third and D-186 deleted it.** Static has no target of any kind, so its
    // tile fires in place under the same hold and opens nothing; the screen that drew the home's
    // plan went with the ability, and with it the largest disclosure this app ever made.
    //
    // **A Resident can never be on either**, and that is capability rather than a route: their
    // tier-2 tiles take no pointer input at all (D-142), so there is no hold to run and no view to
    // be caught on.
    SurgeTargets, IsolateTargets,

    // Work. The Subroutine screens are named for the Subroutine and not for how much light it
    // makes: the port's two were `Sub` and `SubBright`, which is the light ladder used as an
    // identity, and it stops describing anything the moment a third Subroutine is built. One id
    // per Subroutine is also what keeps the sweeps honest — every guard in this module walks
    // `ScreenId.entries`, so a Subroutine hiding behind a field on a shared screen would be a
    // Subroutine none of them ever looked at.
    // `TermBusy` is the third scan refusal (D-199): the T card, while somebody else is reading.
    Work, Scan, ScanCaught, ScanBad, ScanUnknown, TermBusy,
    SubHandshake, SubReplay, SubParity, SubShort, SubTrace, SubJam, SubSniff, SubDeallocate,
    SubInterrupt, SubDrift,
    // **No `TermNo`.** NO SIGNAL is deleted with the tile that reached it (D-199): the live view
    // opens because the house answered a scan of the T card, and from nowhere else.
    Files, Notes, TermLive, Timelapse,

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
 * **A Static jam, as this phone was told about it** (D-186, `Effect.StaticOpened`).
 *
 * ### It cannot say who, and that is the whole of the type
 *
 * There is no seat here and there must never be one. The message it is built from carries none —
 * the house does not keep who jammed it — so a screen that wanted to draw *your* jam differently
 * from somebody else's would find nothing to branch on. **Every living phone in the building holds
 * an identical value of this**, including the one whose thumb started it.
 *
 * ### [seed] is why the noise is authored rather than invented
 *
 * Rule 5: light is game state, and no animation the rules did not emit. So the field the noise is
 * generated from comes down the wire with the jam, the same on every phone and the same again on
 * every replay — see `StaticNoise`, where the seed and a frame number are the only two inputs.
 *
 * [seconds] is the window, from the house, in the form `EgressOpened`'s `remaining` uses: a tick is
 * the house's own clock and means nothing here.
 */
data class StaticJam(val seconds: Int, val seed: Long)

/**
 * Everything the device needs in order to draw itself.
 *
 * **Deliberately flat and inert.** No behaviour, no derivation from rules — `ui` cannot see
 * `core`, and a screen that could ask the rules a question is a screen that could leak the
 * answer. Every field here arrives already decided at the effect boundary.
 */
/**
 * **One carried thing and where it goes** (D-168): `DISK 01`, `RACK`. Both are words the widget
 * prints, and neither is decided here -- see [PanelState.carrying].
 */
data class Carried(val item: String, val to: String)

data class PanelState(
    val screen: ScreenId = ScreenId.Home,
    /**
     * **Whether a real house is attached to this phone. When it is, NOTHING FAKE MAY RENDER OR
     * FIRE** — no stand-in push, no presentation timer, no fixture.
     *
     * ### It is the one field on this class the house does not push, and it has to be
     *
     * Every other field here is a fact the house sent. This is the fact that decides whether the
     * *absence* of those facts may be papered over, so it cannot itself arrive down the wire: a
     * phone learns it has a house by having one, not by being told. `app` writes it — see
     * [FlowModel.houseAttached] — because `app` is the one module that can see both a socket and a
     * screen.
     *
     * ### What it is derived from, and why not from a switch
     *
     * **A live way up to a house**, which host and client reach identically: the host runs the
     * house and then joins it over loopback like everybody else (`LobbyOverTransport`), so
     * `RoundSeam.linked` is called on every phone in the round and on no phone outside one.
     *
     * It was two launch switches before, ORed, and the pair is why the owner's evening was
     * unreadable: the arming switch added during the payoff run was a **second** way to have a real
     * round and the line that turned the bench's fake house off had only ever been taught the
     * first. So a real house and a fake one drove the same two phones all evening. **A question
     * about how the process was started can always acquire a third answer nobody teaches it; a
     * question about whether a house is actually there cannot.**
     *
     * ### False is the port, and the port must keep working
     *
     * With no house attached the ported flows walk themselves off [Flow.autoAdvance] and the
     * fixtures render, which is what makes a screen reviewable on a desktop and a bench walkable
     * past the check-in gate. That is a standalone mode and it is legitimate — it is only a lie
     * when a house is standing right there. See [FlowHost] and [PanelVals].
     */
    val houseAttached: Boolean = false,
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
     * **The meeting phase's clock: seconds left until the house's deadline** (D-243, provisional).
     *
     * Seconds remaining, and nothing else — the window it is counting down is a property of the
     * screen (see [Countdowns]), so the two cannot be sent inconsistently. Null means no phase with
     * a deadline is open, which on a phone with no house attached is always, and the design's drawn
     * moment stands in.
     *
     * **Set by the push that opens the phase, counted down by `FlowHost`.** `MeetingPhaseOpened`
     * starts it at the window the house runs that phase for (`Balance.DISCUSSION_SECONDS`, the
     * ballot the house sent, `Balance.TALLY_SECONDS`: the same numbers `HouseClock` ends the phase
     * on), `FlowModel.secondPassed` takes a second off it a second at a time, which is D-239's
     * arrangement for page 2's clocks, and `MeetingEnded` clears it. Until D-243 nothing wrote it
     * and every meeting clock on a housed phone stood at the design's `1:04`, `0:38`, `9` or `0:24`.
     *
     * **It drives nothing.** No screen moves when it reaches zero, no light changes with it, and no
     * request rides on it: the house moves every phone at once when its own clock says so (rule 5;
     * the display is the push's echo). Counting from the push's arrival errs late by the push's
     * travel, so a phone may still read `0:01` when the house moves it, never `0:00` early.
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
     * **Whether an Egress is running right now** — opened by the house, and not yet contained.
     *
     * A separate fact from [egressType], and it has to be: the type is *which* Egress the house
     * started and it survives being read, while this is *whether one is still going* and it comes
     * back down. A surface that took a non-null type as "an Egress is on" would keep saying so for
     * the rest of the round, because nothing ever unsays a label.
     *
     * The living do not read it — their Egress is a whole screen the house pushes them to, and the
     * screen is the state. **The couch reads it**, because the couch watches one screen for the
     * entire round and the Egress widget on it has to be able to be empty. See [Ghost3Screen].
     *
     * False on a phone with no house attached, which is the honest bench: no Egress has been fired
     * in a round that has not started. Unlike [egressType] this has **no fixture behind it** — a
     * default that drew a live crisis would be a port reproducing a mockup by asserting something
     * about the house.
     */
    val egressRunning: Boolean = false,
    /**
     * **Whether the Egress countdown is stopped** (`Effect.EgressHeld`, D-133).
     *
     * A second fact beside [egressRunning] rather than a third state on it, because the two answer
     * different questions: that one is *is there a crisis*, and this is *is its clock moving*. A
     * meeting called by reporting a Revoked player is the one meeting that can happen during an
     * Egress and it **pauses** the timer — never resets it — so a widget that could not say HELD
     * would go on counting down through a meeting the whole house had stopped for, which is one
     * screen contradicting the room.
     *
     * False until the house says otherwise, which is the honest bench: a countdown nobody has
     * stopped is running.
     */
    val egressHeld: Boolean = false,
    /**
     * **The Egress countdown: what is left of it and how long it ran from** (D-243).
     *
     * From the house's own number, already on the wire: `EgressOpened.remaining` starts it (and is
     * the length it is a fraction of), `EgressHeld.remaining` resets it at each pause and each
     * restart (D-133), both in ticks and turned into whole seconds by `Balance.wholeSeconds`, which
     * rounds up so the phone never reads less than the house holds. `FlowModel.secondPassed` counts
     * it down while the house has not stopped it, and `EgressContained` clears it.
     *
     * Null with no Egress, and on a phone with no house attached, where the design's `1:42` over 22
     * of 32 segments stands in. Like [secondsLeft] it drives nothing: the house ends an Egress.
     */
    val egressLeft: Countdown? = null,
    /**
     * **Whether the house is holding this seat's beat** (`Effect.SyncPulseAnswered`, `gdd.md:355`).
     *
     * True when this phone's tap landed and the house is waiting for somebody at the other node.
     * **Copied and never interpreted**: false is the answer for a missed beat, for a lockout, for a
     * seat that is not at a node and for a seat outside the system, deliberately — one shape, one
     * kind, one message per return. A screen that turned false into *you are not at a node* would
     * have rebuilt the presence detector the single shape exists to prevent.
     *
     * Null means this phone has returned no beat the house has answered yet, which is every moment
     * of the round except the ones inside a containment.
     */
    val beatHeld: Boolean? = null,
    /**
     * **The work the scan opened, as the house answered it.**
     *
     * `Effect.ScanAnswered` carries a `SubroutineInstance` and **this is where it lands** — the
     * house names the work its own scan opened, and the roster turns the kind into the row the
     * caught scan draws. Null means the house has said nothing, and the port's drawn row stands in:
     * [egressType]'s arrangement, and for the same reason.
     *
     * **It is the seam a screen is tested through.** Three surfaces name the current Subroutine and
     * one of them — the caught scan — draws that Subroutine's briefing (D-188, [Subroutine.briefing]),
     * so a bench that could only ever render one Subroutine could only ever render one of the ten
     * sentences a player reads before the work opens.
     */
    val current: CurrentSubroutine? = null,
    /**
     * **Which card this phone last read — the id, beside the row the read produced.**
     *
     * ### Why it is here and not on [CurrentSubroutine]
     *
     * [current] is what a screen draws, and its `marker` is a [MarkerShape] — the printed symbol a
     * player looks for in the dark. That is the right thing for a drawn row to carry and the wrong
     * thing to send: `IntentMessage.HandOverEntry` wants the card's **id**. Putting an id on the
     * drawn row would give one field two jobs and put a wire value in front of a composable; a
     * sibling here keeps the drawing type drawable and the sending value sendable.
     *
     * ### Why the client holds it at all, when everything else on this class came from the house
     *
     * Because the house does not send it and deliberately cannot. `SubroutineInstance` carries no
     * marker — *where the work is, is what walking the house in the dark is for* — and
     * `Effect.PresenceChanged`, which does carry one, is permitted to nobody (D-111). **The scanner
     * is what knows which card was read**, and the scanner is on this phone. So this is not `ui`
     * deriving a fact about the house; it is this phone remembering its own act, which is the one
     * category of state a client is allowed to keep.
     *
     * ### Lineage
     *
     * F1·1 added [current] as a field with no sender; C3 routed `Effect.ScanAnswered` into it. This
     * is the third step of that same seam and the one the payoff run found missing: the row arrived
     * and the id did not, so BEGIN had nothing to hand `SubroutineModel.posted` and every entry in
     * the game was refused at the last line before the send. The bench had been carrying its own
     * copy — `RoundClient.standingAt`, written by `RoundClient.scanning` — which is how a rehearsal
     * could hand entries over all evening while no phone a person held could.
     *
     * **Null means this phone has read no card**, which on a phone with no house attached is
     * always. A hand-over then goes nowhere, which is the honest state of a bench and is asserted
     * as such — see `HandOverSeamTest`.
     */
    val scannedAt: MarkerId? = null,
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
    /**
     * **The house's last word, on the ending screen** (`Effect.HouseSignedOff`).
     *
     * It sits beside [revealed] rather than inside it because they are two different things that
     * happen to land on one screen: the reveal is who the Insiders were and what they wrote, and
     * this is the house talking — the administrative voice that has been the app's register all
     * evening, signing off to the people it owned.
     *
     * **Null means the house has said nothing** and the ending draws its own, which is every other
     * pushed field's arrangement on this type. It stays null on the two pushes that *end* the
     * round, because an ending is `RoundEnded` then `InsidersRevealed` and this is a third thing
     * that may or may not follow them (E-P3-6: anything that acts on the first push is wrong).
     */
    val signOff: String? = null,
    /**
     * **This seat's work order, exactly as `Effect.WorkOrderIssued` carries it** (D-114, D-129).
     *
     * A `List<OrderLine>` and never a list of anything richer: a blocked line is a different *type*
     * with no name on it, so a screen reading this one is **physically incapable** of drawing what
     * a blocked entry is. The blackout is the type, not a branch somebody has to remember (rule 3).
     *
     * **Null means the house has said nothing**, which on a phone with no house attached is always;
     * the port's drawn seven stand in. Exactly [egressNodes]' and [revealed]'s arrangement, and the
     * fallback is a fixture rather than a second source of truth.
     *
     * **The order is a menu, not a queue** (D-114) — it arrives whole, every line the same length
     * of order for every seat and both roles, and the player picks. It is re-sent on every return
     * rather than only when something changed, because presence would otherwise be a second
     * verdict; nothing here has to know that, but nothing here may assume the opposite either.
     */
    val order: List<OrderLine>? = null,
    /**
     * **The live ballot, for the couch and nobody else** (D-117, D-134, D-075).
     *
     * What `Effect.VoteSelectionShown` builds up, one selection at a time, with seats resolved
     * against [roster] on the way in — `FlowModel.nameOf`, the same lookup [caller] and [tally]
     * make, and the same one `fireSurge` makes in the other direction.
     *
     * **That is not `ui` keeping a second copy of who is in the house**, which is the distinction
     * this KDoc used to draw on the wrong side of. A second copy would be this module *deriving* a
     * roster — out of a lobby standing, or by sorting names it happens to hold. [roster] is the
     * house's own list, in the house's own order, pushed to this phone by `Effect.RosterIssued`;
     * reading it is the same act as drawing it.
     *
     * **Ghosts are the only readers who see selections rather than a count.** This field must
     * never be read by a living screen, and `MeetingDisclosureTest` renders every screen in the
     * game with it populated to prove that none of them does.
     *
     * Null means the house has said nothing; [OutsideView]'s drawn five stand in.
     */
    val ballots: List<Ballot>? = null,
    /**
     * **The result, as the house read it** (`Effect.MeetingResult`, and the owner's 2026-08-24
     * ruling).
     *
     * The tally screen drew a fixture — MARCUS 3, SKIP 2, PRIYA 1, and MARCUS WAS RESTRAINED — on
     * every phone in every round, which was honest while nothing could send it and is not any more.
     * **One kind lands here now**: the count and the outcome, which every phone in the house gets
     * equally. The couch's extra is not a wider version of this field, it is a different field —
     * `MeetingResolved`'s attribution completes [ballots], where the one screen permitted to draw
     * it already reads it (D-075).
     *
     * **Null means the house has said nothing** and the drawn fixture stands in, which is
     * [egressNodes]' arrangement and every other pushed field's on this type.
     */
    val tally: MeetingTally? = null,
    /**
     * **Who called this meeting** (`Effect.MeetingOpened`, D-121).
     *
     * *Naming the caller is deliberate: the meeting has an author, and the room can ask them why.
     * It is one of the few facts the app volunteers, and it is one everybody gets equally.* The
     * ring screens drew PRIYA and ELLIOT on every phone in every round until this field existed.
     *
     * A name rather than a seat, resolved against [roster] on the way in — `FlowModel.nameOf`, and
     * the same lookup `fireSurge` makes in the other direction.
     *
     * **Null means the house has said nothing** and the port's names stand in, which is every other
     * pushed field's arrangement here.
     */
    val caller: String? = null,
    /**
     * **Which phase of the meeting the house has opened** (`Effect.MeetingPhaseOpened`).
     *
     * A pushed fact, like everything else here. The living walk a screen per phase and never need
     * to be told which one they are on; the couch watches the whole meeting from **one** screen
     * (D-134), so it is the only surface that has to be told — and a couch counting down the vote
     * window while the room is still talking would be the one screen in the game showing a clock
     * that is not running.
     *
     * Null means the house has said nothing, and the port's drawn moment stands in.
     */
    val meetingPhase: MeetingPhase? = null,
    /**
     * **How long the ballot runs, as the house said when it opened the vote**
     * (`Effect.MeetingPhaseOpened.voteWindowSeconds`, D-117).
     *
     * The host set it in the lobby and arming locked it. It is the window rather than the moment —
     * [secondsLeft] is the hand position — and it replaces the design's own forty-five in
     * [Countdowns] wherever a house has said one, on the living's vote screen and on the couch's
     * alike, so that the two surfaces cannot count the same ballot down over different windows.
     *
     * Null means the house has said nothing, and the design's number stands in.
     */
    val voteWindowSeconds: Int? = null,
    /**
     * **System Integrity, as a percentage the house pushed** (`Effect.SystemIntegrityLive`,
     * `Effect.SystemIntegrityBatched`).
     *
     * A percentage and never a count, because a count plus the public seat arithmetic is the
     * Insider number D-103 hides (D-130). There is nothing on this class a total could be put in
     * and there must never be: [PanelVals.integrityLit] draws the bar **from this**, so the figure
     * and the segments beside it are one value rather than two that agree by hand — which is the
     * exact fault revision 21 found in `14 / 32` and C2 found again in Ghost3's `66%`.
     *
     * **Whether it moves live or once a meeting is not this field's business.** The two effects
     * that fill it differ in audience and timing and carry the same shape; a phone draws whatever
     * it was last told, which is precisely what *batched, then frozen* looks like from here.
     *
     * Null means the house has said nothing, and the screen's own drawn fraction stands in.
     */
    val integrityPercent: Int? = null,
    /**
     * **How many seats the round has, as the house counted them** — and **nothing draws it.**
     *
     * The one number on this class that exists to be a *denominator* and never a display. It
     * arrives on `Effect.CheckInProgressed`, which is the only message in the game that carries the
     * seat total (`expected`, and D-104's gate counts every living player and every out player), and
     * the one thing that reads it is the conversion of `Effect.SubroutineProgressed`'s raw count
     * into the percentage the couch's meter is drawn from — see `FlowModel.houseSaid`.
     *
     * ### It is not a copy of `MeetingCounts.seats`, and the difference is the whole point
     *
     * That one has a **fixture default of six**, because a meeting screen with no denominator is a
     * screen that cannot be drawn on a bench. This one is null until the house has spoken, so a
     * phone that has never been told a seat total cannot compute a percentage against a number
     * somebody typed into a fixture — it leaves the bar where it is instead, which is the
     * fail-closed direction. Both are written from the same branch, so they cannot drift.
     *
     * **A total may never be printed** (D-103, D-153). `seats × METER_PER_SEAT` is public
     * arithmetic, so a meter drawn as `28 / 42` hands back the Insider count by division, in a game
     * played two to three rounds an evening (D-157). `MeterDisclosureTest` reads the panels; this
     * field is fed to `Balance.integrityPercent` and to nothing else.
     */
    val seats: Int? = null,
    /**
     * **Every seat in the round, by the name its owner asked for, in seat order** (D-115).
     *
     * What the Surge picker draws. **Seat order is load-bearing and not a presentation choice**: the
     * picker turns a tapped row back into a `Seat` by its position in this list, exactly as the
     * ballot does (`MeetingRequest.asMessage`), so a list that was sorted, filtered or de-duplicated
     * would fire the ability at somebody else.
     *
     * **Every seat, including this one and including the out.** Self-targeting is required
     * (`gdd.md:393`) so the actor's own row has to be on it; the Revoked stay on it because a roster
     * that dropped them would be the revoke-detector `gdd.md:344` exists to prevent, delivered by a
     * list instead of by an animation.
     *
     * **It arrives at arming, on `Effect.RosterIssued`, and everything that names anybody reads
     * it** (D-115). It had no writer at all until then: the ring's caller, the tally's rows, the
     * couch's live ballot and this picker all resolve a seat through it, so in a real round all
     * four drew the port's five names at a meeting of six real people.
     *
     * Null means the house has said nothing, which on a phone with no house attached is always;
     * [PanelVals.roster] resolves the port's five. [egressNodes]' arrangement exactly.
     */
    val roster: List<String>? = null,
    /**
     * **The seats the house notice names at this meeting, in seat order** (`Effect.NoticeIssued`,
     * D-187, D-223).
     *
     * Deliberate evasion only: today a radio switched off or its permission withdrawn mid-round.
     * Arrives at every meeting as the check-in gate closes, **empty when nobody evaded, and an empty
     * one draws nothing** (the owner, 2026-11-15). Null means the house has said nothing, which on a
     * phone with no house attached is always; [PanelVals.notice] resolves the drawn fixture there.
     */
    val notice: List<Seat>? = null,
    /**
     * **The markers this phone may Isolate, as the house last offered them**
     * (`Effect.IsolateTargetsOffered`, D-150).
     *
     * Shape and room per row, which is what a player in a dark house picks by. **Null means the
     * house has said nothing** and [PanelVals.isolateTargets] resolves the port's drawn set;
     * [egressNodes]' arrangement, and the fallback is a fixture rather than a second source of
     * truth.
     *
     * **This phone does no filtering of it whatever.** The Egress-node exclusion is the *house's*
     * (D-150) and arrives as a shorter list; a client that removed rows of its own would be a device
     * deciding what an ability may be fired at, and the day the two rules disagreed the quieter one
     * would win.
     */
    val isolateTargets: List<Destination>? = null,
    /**
     * **The Static jam the house last announced, or null when the monitoring is clear** (D-186,
     * `Effect.StaticOpened`).
     *
     * **It carries no seat and it never can**, which is the ability: the message that sets it is
     * house-wide and identical on every living phone, so a screen reading this cannot tell a jam
     * this phone caused from one it did not. That is what makes the Insider who fired it look
     * exactly like everybody else.
     *
     * Null is the resting state and there is no fixture behind it — unlike [isolateTargets], whose
     * null resolves to the port's drawn set. **A jam has no drawn state to fall back to**: a bench
     * that showed noise with no house attached would be a screenshot of the exception rather than
     * of the screen.
     *
     * Cleared two ways and neither of them is a rule this phone invented: by its own window running
     * out (`FlowHost`, the ring's presentation timer one message over) and by a meeting opening,
     * which is D-185's *a meeting call ends every running ability effect* arriving as the frame that
     * says so.
     */
    val staticJam: StaticJam? = null,
    /**
     * **Page 2's three clocks, as every phone was told they restarted** (D-239). See
     * [PublicCooldown]. Null means no house has armed a round on this phone, and the port's drawn
     * bars stand in.
     *
     * Set by pushes every seat gets (LIGHTS OUT, a meeting's end, an Egress opening), advanced by
     * `FlowHost` once a second, and never by anything a seat did, with one exception: an Insider's
     * clocks and a Resident's are the same three values at the same moment until this phone fires a
     * Tier-2 ability, when the house's answer to that fire restarts [accessClock] from the ability's
     * weight (`PublicCooldown.fired`, D-262). The next meeting's end puts it back on the public half.
     */
    val revokeClock: PublicCooldown? = null,
    val accessClock: PublicCooldown? = null,
    val egressClock: PublicCooldown? = null,
    /**
     * **What this phone is carrying, or nothing** (D-168).
     *
     * *Carrying blocks every other entry*, so the state and the list it blocks are one surface: the
     * work-order widget turns into the carry while this is set, and says on its face that nothing
     * else opens until the thing is delivered. The player does not have to hold the rule in their
     * head in a dark room.
     *
     * ⚠️ **Nothing writes it yet.** Carrying is the Array Wipe circuit (Spares, Rack, Disposal), and
     * the circuit is unbuilt: `AssignedWork.ArrayWipe` has no `opensAs` and there is no carried
     * state anywhere in the rules (F-014, E7). Until E7 lands, the widget is reachable by the cheat
     * picker and by tests. One field, one shape, so E7 fills it from a house message and nothing
     * on the surface changes.
     */
    val carrying: Carried? = null,
    /**
     * **This phone's own wall clock, as it last read it** (D-243). Written by `FlowModel` from the
     * clock the app root hands it, at construction and once a minute changes, **housed or not**
     * (D-255); read by the status strip and the lantern. Not a fact the house sent, and nothing but
     * those two draws it. Null where no clock was handed down, which is every render: that null is
     * how [PanelVals.renderHarness] tells the harness from a real phone with no house yet.
     */
    val wallTime: WallTime? = null,
    /**
     * **When the house's opening text landed, on this phone's clock** (D-243). The house thread's
     * opening rows and the role's line arrive with LIGHTS OUT (`OpeningMessage`, `RoleRevealed`),
     * so they carry this time. Null until then.
     */
    val openedAt: String? = null,
    /**
     * **Every later text from the house, in the order it landed, each with its own arrival time**
     * (`MessageDelivered`, D-243). The thread behind the house's row. Empty until one lands.
     */
    val houseTexts: List<HouseText> = emptyList(),
    /** **When the ending landed, on this phone's clock** (`RoundEnded`, D-243). Null until then. */
    val endedAt: String? = null,
    /**
     * **How the round was won, as the house said it** (`RoundEnded.by`, D-131's four routes, D-261).
     * The endings name the route from this. Null until a round ends, and reset with the round.
     */
    val endedBy: WinRoute? = null,
    /**
     * **The one line this phone handed over, as the house quotes it back to an Insider** (D-116,
     * D-101, D-247). `RoleRevealed.line`: set on an Insider's phone, never on a Resident's, and
     * dropped when the round ends, so the promise *deleted when the round ends* holds on this side
     * too. A [HeldLine] rather than a String so that this class's `toString` — which a log line or a
     * ledger can print — reports a length and never the words ([OneLine]'s rule).
     */
    val heldOver: HeldLine? = null,
    /**
     * **How many Egresses the house has told this phone were contained this round**
     * (`EgressContained`, D-247). Zero from LIGHTS OUT, one more per push; null until a house has
     * opened a round, which is what the Residents' ending draws as nothing.
     */
    val egressesContained: Int? = null,
    /**
     * **The Residents, by name, once the house has said who the Insiders were** (D-247). The roster
     * less the seats `InsidersRevealed` named, in seat order, blanks left out. Null until the
     * ending; what the Residents' ending lists instead of the port's four.
     */
    val residents: List<String>? = null,
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
 * **A one line held on the panel, which never prints its words** (D-247).
 *
 * [OneLine]'s `toString` rule applied to [PanelState.heldOver]: [PanelState] is a data class, so
 * anything that prints it prints its fields, and a line in a log is a line kept.
 */
data class HeldLine(val text: String) {
    override fun toString(): String = "HeldLine(${text.length} chars)"
}

/**
 * **What the room decided, and how many of it voted — the whole of a living player's result**
 * (D-075, D-117, and the owner's 2026-08-24 ruling that the living's result is count-only).
 *
 * `6 OF 6 VOTED . MARCUS WAS RESTRAINED`, and nothing finer. **This type cannot carry a
 * per-candidate total** and that is the redaction rather than a rule the screen keeps: the rows it
 * used to hold are gone, so no composable, no `when` branch and no later refactor can put a count
 * beside a candidate's name on a screen a living player stands on (rule 3). What the couch gets
 * instead is strictly more and arrives elsewhere — the ballot itself, with names against it, on
 * [PanelState.ballots].
 *
 * [restrained] is null when the room restrained nobody: a Skip, a tie, or a vote nobody won. That
 * is a real outcome and not an absent one, which is why it is a nullable name rather than an empty
 * string — `NOBODY WAS RESTRAINED` is a sentence the screen has to be able to say.
 *
 * [voted] and [living] are the house's own pair off `Effect.MeetingResult` — the same two numbers
 * the vote screen counted up to, never recomputed here from anything this phone holds.
 */
data class MeetingTally(
    val restrained: String?,
    val voted: Int,
    val living: Int,
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
class PanelVals(
    val state: PanelState,
    /**
     * **How far into its own window this screen is, or null on the screens that have none.**
     *
     * The one value on this class that the *device* produces rather than the house, and it is
     * allowed to because there is nothing outside the phone that could produce it: a self-test
     * running and a camera being open are facts about this rectangle and no other. [LocalWindows]
     * is the closed list, [localWindow] is the clock, and [Screen] is the only caller.
     *
     * Null everywhere else, including on every meeting screen — those windows are the house's and
     * are drawn from [PanelState.secondsLeft], which nothing on the device advances.
     */
    val window: LocalWindow? = null,
) {

    val screen: ScreenId get() = state.screen
    val insider: Boolean get() = state.role == PanelRole.Insider
    val resident: Boolean get() = !insider

    /**
     * **The house's value, the port's fixture, or honest emptiness — and never the middle one with
     * a house attached** ([PanelState.houseAttached]).
     *
     * Every fallback on this class was written as `pushed ?: FIXTURE` and every one of them was
     * right about the bench and wrong about a round. The fixtures exist so that a screen rendered
     * with nothing behind it shows the drawn design rather than an empty rectangle — that is what
     * makes a desktop render harness and a screenshot review worth anything, and it stays.
     *
     * **What it may not do is stand in front of a player.** The owner met this: their caught-scan
     * screen said `HANDSHAKE . GARAGE` in a house with no garage, in a round where the house had
     * dealt them a Jam, because [CURRENT] filled a silence that was real. A fixture rendered on a
     * housed phone is not a placeholder, it is the app asserting something about the house — and it
     * is indistinguishable, on the glass, from the house having said it.
     *
     * So each call site names three things instead of two: what the house sent, what the port draws,
     * and **what nothing looks like**. The third is the new one and it is the honest one: an empty
     * list, an absent row, a `NOTHING`-shaped line. A player who is shown nothing has been told the
     * truth and can act on it; a player who is shown a fixture cannot tell and has no way to find
     * out.
     */
    private fun <T> housed(pushed: T?, fixture: T, nothing: T): T =
        pushed ?: if (state.houseAttached) nothing else fixture

    // ---- Status bar ---------------------------------------------------------------------

    val mode: PanelMode = when (state.screen) {
        // The only screens that fill with amber, and therefore the only ones that draw their
        // own inverted status row. Everything else uses the shared one. `LockNotify` is the lock
        // screen with something arriving on it, so it is the lantern too -- darkened, but still
        // the lantern, and still drawing its own row.
        ScreenId.Lock, ScreenId.LockNotify, ScreenId.Scan -> PanelMode.Inverted

        in PRE -> PanelMode.Pre

        ScreenId.WinInsiders -> PanelMode.End
        ScreenId.Revoked, ScreenId.Restrained, ScreenId.Ghost3, ScreenId.GhostMeeting ->
            PanelMode.Ghost
        else -> PanelMode.Live
    }

    val isPre: Boolean get() = mode == PanelMode.Pre

    /**
     * **Whether this screen puts text on a ground that emits.**
     *
     * The question the scan-line glass asks, and the only thing it varies on — see [ScanLines].
     * The band is a *multiply*: over black it takes 32% of nothing and is invisible, which is the
     * field the design's mockup was drawn over. Over a ground that is already lit it takes 32% of
     * everything, ink and ground alike, and the pair it leaves behind is closer together than the
     * pair it found. That is the owner's *"barely legible"*, and it happens on three kinds of
     * ground rather than one:
     *
     * - **bone** — every pre-arming setup surface and the Residents' ending ([isPre]);
     * - **the lantern's amber** — the two full amber fields, black glyphs on `Amber.Bright`
     *   ([PanelMode.Inverted]);
     * - **an amber block on an otherwise dark screen** — [AMBER_BLOCKS] below, where the block is
     *   the one sentence on the screen that matters.
     *
     * **A function of [PanelState.screen] and nothing else** — not the role, not the round, not the
     * cause a player is out by. That is what keeps the varying strength inside rule 5: two phones
     * showing the same screen carry the same glass, and a difference nobody can produce is a
     * difference nobody can read off somebody else's phone.
     */
    val litGround: Boolean get() = isPre || mode == PanelMode.Inverted || screen in AMBER_BLOCKS

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
     * The screens whose whole job is to emit as little light as possible — **the status bar
     * included**.
     *
     * A bright bar above deliberately dim text is the same flare the screen exists to avoid, and
     * it is worse than a bright body, because chrome is the part nobody thinks to check.
     *
     * **The revoked screen, the dark Subroutines, and the reconnecting screen.** The set briefly
     * included every out-screen, and that was wrong: once you are on the couch the room already
     * knows you are out, so there is nothing left to conceal.
     *
     * **The reconnecting screen was narrowed out, and D-200 put it back.** The argument for leaving
     * it bright was that a reconnecting phone needs to be *noticed*; that narrowing was never
     * ruled, D-077 names the disconnect screen among the concealment screens, and a reconnecting
     * phone lit brighter than its neighbours is exactly the flare D-077 exists to prevent.
     *
     * **Asked of the roster rather than named.** It used to name `Sub` — the one dark Subroutine
     * screen the port had — so a second dark Subroutine would have shipped with a bright status
     * bar above a screen built to emit nothing, and the fault would live in a file nobody edits
     * when adding a Subroutine. The question *is* the light signature, so it is asked of the
     * light signature.
     */
    private val concealed: Boolean = state.screen == ScreenId.Revoked ||
        state.screen == ScreenId.Disconnect ||
        Subroutine.on(state.screen)?.light == LightSignature.Dark

    val ink: Color = when {
        isPre -> Amber.BoneInk
        concealed -> Amber.Dim
        else -> Amber.Bright
    }
    val dim: Color = when {
        isPre -> Amber.BoneDim
        concealed -> Ramp.Faint
        else -> Amber.Dim
    }
    val edge: Color = if (isPre) Amber.BoneEdge else Ramp.Faint

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

    /**
     * **The strip's ink: the brightest thing on it** (D-204). Bone ink before arming, and on a
     * concealment screen (D-077) `#C08A2E` like everything else there.
     */
    val stripInk: Color = when {
        isPre -> Amber.BoneInk
        concealed -> Ramp.Dim
        else -> Ramp.Ink
    }

    /** The carrier's ink, and the drained perimeter chip's. */
    val stripDim: Color = if (isPre) Amber.BoneDim else Ramp.Dim

    /** The strip's hairline, and the drained chip's core. */
    val stripEdge: Color = if (isPre) Amber.BoneEdge else Ramp.Faint

    /** Once the round starts the house owns the network: no bars on any live screen. */
    val signalOn: Color = if (isPre) Amber.BoneInk else Ramp.Faint

    /** The third bar: unlit on the bone screens, until the Residents' ending gives it back. */
    val signalOff: Color = when {
        isPre && state.screen != ScreenId.WinResidents -> Amber.BoneEdge
        isPre -> Amber.BoneInk
        else -> Ramp.Faint
    }

    /**
     * The perimeter is armed for every in-game screen, **including a revoked phone**.
     *
     * A glyph rather than a word, so the fact is not repeated in text on thirty screens — and so
     * a revoked player's device says exactly what everyone else's device says.
     */
    val armedGlyph: Boolean =
        mode == PanelMode.Live || mode == PanelMode.Ghost || state.screen == ScreenId.WinInsiders

    /** The same iris, drained. An unarmed perimeter drawn like the defunct bars, not a new symbol. */
    val disarmedGlyph: Boolean = state.screen == ScreenId.WinResidents

    /**
     * The padlock: on Files and while carrying (D-168, D-204), the two places nothing else opens.
     * The lantern draws its own strip and carries none.
     */
    val lockChip: Boolean = state.screen == ScreenId.Files || state.carrying != null

    /**
     * **The render harness, and only the render harness: no house attached and no device clock**
     * (D-255).
     *
     * Until D-255 nothing told the harness apart from **a real phone that has no house yet** (the
     * join screen on first launch, the lobby before a seat, the setup walk): both were simply
     * `houseAttached = false`, so a real phone drew the design's `20:41` until a house attached.
     * The difference is a fact about the process, and the app root states it by handing down the
     * phone's own clock (`FlowModel`'s `wallClock`), which `FlowModel` reads at construction and
     * `FlowHost` every second after, housed or not. A render or a test that hands none down is the
     * harness; anything with a clock is a phone.
     */
    val renderHarness: Boolean = !state.houseAttached && state.wallTime == null

    /**
     * **The strip's clock: the phone's own, on any real phone, housed or not** (D-243, D-255), and
     * the design's drawn times in the [renderHarness] alone, so it still draws the mockup. A dash on
     * a housed phone that was handed no clock, never a drawn time in front of a player.
     */
    val clock: String = when {
        state.wallTime != null -> state.wallTime.clock
        !renderHarness -> NO_READING
        state.screen == ScreenId.WinResidents -> "21:38"
        isPre -> "20:41"
        mode == PanelMode.Ghost -> "21:31"
        mode == PanelMode.End -> "21:38"
        else -> "21:07"
    }

    /** The lantern's large time (D-243, D-255): [clock]'s source, at the lantern's size. */
    val lockClock: String = state.wallTime?.clock ?: if (renderHarness) "21:04" else NO_READING

    /** The lantern's date line (D-243, D-255): the phone's own date on any real phone. */
    val lockDate: String = state.wallTime?.date ?: if (renderHarness) "SATURDAY 16 AUGUST" else NO_READING

    /** When the ending landed on this phone (D-243), as the endings print it beside the house. */
    val endedAt: String = housed(state.endedAt, fixture = "21:38", nothing = NO_READING)

    /**
     * **PERIMETER ARMED's count: every seat in the round, as the house counted them** (D-247).
     * Everyone is a Resident, so the roster's length is the number. The port's six on a phone with
     * no house; on a housed phone not yet told a seat total, the line without a number.
     */
    val accountedFor: String = when {
        !state.houseAttached -> "6 RESIDENTS\nACCOUNTED FOR"
        state.seats != null -> "${state.seats} RESIDENTS\nACCOUNTED FOR"
        else -> "ACCOUNTED FOR"
    }

    /**
     * **The Residents' ending: who the Residents were** (D-247) — the roster less the Insiders the
     * house named, from [PanelState.residents]. The port's four with no house; nothing on a housed
     * phone not yet told. The house does not say who is still standing, so neither does this.
     */
    val residentNames: List<String> = housed(state.residents, ENDING_RESIDENTS, nothing = emptyList())

    /**
     * The port's `4 OF 6` beside them, and **nothing on a housed phone** (D-247): the house sends no
     * count of who is still standing, and a phone that worked one out would be guessing.
     */
    val residentsStanding: String? = if (state.houseAttached) null else "4 OF 6"

    /** **How many Egresses were contained** (D-247), as the house's pushes counted them. */
    val egressesContained: String =
        housed(state.egressesContained?.toString(), fixture = "2", nothing = NO_READING)

    /**
     * **The reconnecting screen's attempt line**: the port's `ATTEMPT 3 . 8S`, and nothing on a
     * housed phone (D-247). Nothing on this phone counts reconnect attempts, so there is no real
     * number to draw.
     */
    val reconnectLine: String? = if (state.houseAttached) null else "ATTEMPT 3 . 8S"

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
        val fromInk: Color,
        val ink: Color,
    )

    val inbox: List<InboxRow> = run {
        val seed = state.inboxSeed
        val keep = 3 + (seed % 4)
        // **No backlog on a housed phone** (D-247, provisional, owner to confirm). It was meant as a
        // per-player disguise, but nothing ever set [PanelState.inboxSeed], so every phone in the
        // house showed the same seven invented senders at the same invented times — texts nobody
        // sent, from people who are not in the room, which D-156 already refuses as fake
        // deliveries. One row, the house's, is identical on every phone, so density says nothing.
        val rest = if (state.houseAttached) emptyList() else backlog.drop(seed % 3).take(keep).map { (from, at, preview) ->
            InboxRow(from, at, preview, Ramp.Dim, Ramp.Mid)
        }
        // Identical sender, time and preview for both roles: the thread must be OPENED to read.
        // The row itself cannot be a tell to anyone glancing at a neighbour's screen.
        //
        // THE PREVIEW IS THE BANNER'S OWN WORDS, not a second copy of them. These rows are where
        // the two texts SURVIVE their notification -- both kinds name Messages as what holds them
        // -- so the two saying different things would make that claim false while both looked
        // right.
        //
        // **THE HOUSE IS ONE ROW, BECAUSE IT IS ONE CONVERSATION** (D-200). Messages lists one row
        // per sender, and two HOUSE rows at 21:02 and 21:01 read as two different numbers. The
        // row is the newest time and the opening banner's words; the thread behind it holds all
        // four texts, the 21:01 line included. D-105's reason for drawing two rows identically
        // (the older one dimmed would read as a read mark) does not arise with one.
        listOf(
            InboxRow(
                // The thread's newest time. Not a read of [houseThread]: this list is built at
                // construction, before [houseLine] -- which the thread holds -- has been assigned.
                // On a housed phone it is the newest arrival's own time (D-243).
                Notifications.opening.from,
                if (state.houseAttached) {
                    state.houseTexts.lastOrNull()?.at ?: state.openedAt ?: NO_READING
                } else {
                    "21:02"
                },
                Notifications.opening.body,
                Ramp.Ink, Ramp.Ink,
            ),
        ) + rest
    }

    /**
     * **What the Messages header counts: messages, not rows** (D-200, D-105). The house's row
     * stands for every text in its thread.
     */
    val inboxCount: Int get() = inbox.size - 1 + houseThread.size

    /**
     * **The house thread, oldest first: time, then text** (D-200). Four texts, and only the newest
     * differs by role. The 21:01 line is the later text's own words, so the thread and the lock
     * screen cannot say two different things.
     *
     * **On a phone with a house attached every row carries the time it landed** (D-243): the two
     * opening rows and the role's line came with LIGHTS OUT and carry [PanelState.openedAt], and
     * each later text the house sent follows, at its own arrival. The design's 21:01 line is not
     * among them, because no house sent it. The rows' words are unchanged; only their times and
     * the stand-in text are.
     */
    val houseThread: List<Pair<String, String>>
        get() = if (state.houseAttached) {
            val opened = state.openedAt ?: NO_READING
            listOf(
                opened to OPENING_THREAD[0],
                opened to OPENING_THREAD[1],
                opened to houseLine,
            ) + state.houseTexts.map { it.at to it.body }
        } else {
            listOf(
                "20:58" to OPENING_THREAD[0],
                "20:59" to OPENING_THREAD[1],
                Notifications.text.at to Notifications.text.body,
                "21:02" to houseLine,
            )
        }

    /**
     * Only the newest message in the house thread differs by role. Everything above it is shared.
     *
     * **On a housed Insider's phone it quotes the line that phone handed over** (D-101, D-116,
     * D-247): *I know you said "…".* The port's *Priya's spare key* is the render harness's, and on
     * a housed phone it was the house holding a stranger's secret over every Insider in every
     * round. An Insider the house quoted nothing to (no line handed over, or a replay) gets the
     * instruction alone, never somebody else's line.
     */
    val houseLine: String = when {
        !insider -> "I am watching you. Thank you for your cooperation."
        !state.houseAttached -> "You still have Priya's spare key. $HOUSE_INSTRUCTION"
        else -> state.heldOver?.text?.takeIf { it.isNotBlank() }
            ?.let { "I know you said \"$it\". $HOUSE_INSTRUCTION" } ?: HOUSE_INSTRUCTION
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

    /**
     * **A housed Resident's note names nobody and no room** (D-247). The port's lines name five
     * people who are not in the house and rooms it may not have, and [PanelState.noteSeed] is never
     * set, so every Resident in every round was handed *why was rose in the garage*. Picking a real
     * player instead would be every Resident's phone accusing the same person, which is the app
     * authoring a read the room is meant to make. ⚠️ The words are provisional, owner to confirm.
     */
    val noteLine: String = when {
        insider -> "i hope no one finds out"
        state.houseAttached -> HOUSED_NOTE
        else -> residentNotes[state.noteSeed.mod(residentNotes.size)]
    }

    // ---- The springboard's two tiles --------------------------------------------------------

    /**
     * **Baselined to the Insider's brightness for BOTH roles.**
     *
     * A dimmer page 2 for Residents would read across a room as *"this one has nothing to tap"*,
     * which is the tell the whole parity discipline exists to prevent. The Resident sees this
     * page lit exactly as an Insider does; nothing here answers them.
     */
    val tileBorder: Color = Ramp.Line
    val tileInk: Color = Ramp.Ink
    val tier2Ink: Color = Ramp.Mid

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
    /**
     * **The state the REVOKE tile is drawn in** ([revokeShown], D-239): ARMED if this phone held it,
     * COOLING DOWN while the public clock runs, READY after. Read by every line below, so the
     * tile's words, ink and bar cannot disagree about which state it is in.
     */
    val revoke: RevokeState = state.revokeShown

    val abilitySub: String = when (revoke) {
        RevokeState.Armed -> "ARMED . TOUCH THEIR PHONE"
        RevokeState.Cooldown -> "COOLING DOWN"
        RevokeState.Ready -> if (insider) "READY . HOLD TO ARM" else "READY . ALL NOMINAL"
    }

    val revokeBorder: Color = if (revoke == RevokeState.Armed) Ramp.Ink else Ramp.Line
    val revokeFill: Color = if (revoke == RevokeState.Armed) Ramp.Fill else Color.Transparent
    val revokeInk: Color = if (revoke == RevokeState.Cooldown) Ramp.Dim else Ramp.Ink
    val revokeSubInk: Color = if (revoke == RevokeState.Armed) Ramp.Ink else Ramp.Dim

    /**
     * **The three bars are the house's public clocks now, not the mockup's 0.38, 0.64 and 0.82**
     * (D-239). Each is [PublicCooldown.fraction], restarted by a push every seat gets, so both roles
     * draw the same length at the same moment. The drawn values stay as the port's fixture for a
     * phone no house has armed, which is what the render harness shows.
     */
    val revokeBar: Float = if (revoke == RevokeState.Cooldown) state.revokeClock?.fraction ?: 0.38f else 1f
    val revokeBarInk: Color = if (revoke == RevokeState.Cooldown) Ramp.Line else Ramp.Ink

    val secondName: String = if (insider) "EGRESS" else "SUBSYS"
    val secondSub: String =
        if (insider) "SHARED WITH THE OTHER INSIDER" else "ALL SYSTEMS RESPONDING"
    val secondBar: Float = state.egressClock?.fraction ?: 0.64f
    val secondBarInk: Color = if (state.egressClock?.running == false) Ramp.Ink else Ramp.Line

    val tier2A: String = if (insider) "SURGE" else "BUS"
    // **STATIC took SPOOF's slot and the Resident's cover word did not move** (D-186). THERMAL is
    // what a Resident's middle tile has always said and there is no reason for it to change: the
    // cover words describe a phone's subsystems, not the abilities behind them, and a Resident who
    // had played before would notice the one that did.
    val tier2B: String = if (insider) "STATIC" else "THERMAL"
    val tier2C: String = if (insider) "ISOLATE" else "CACHE"
    /**
     * The Access pool: its public half (D-239), or, after this phone's own Tier-2 fire was answered
     * with a spent pool, the actor's own restart (D-262). The same on both roles until a fire.
     */
    val tier2Bar: Float = state.accessClock?.fraction ?: 0.82f
    val tier2BarInk: Color = Ramp.Mid
    /**
     * **The one line under the three tier-2 tiles, and it is the same length for both roles.**
     *
     * D-142 asks that a Resident's copy be *true, and not shorter, dimmer or otherwise
     * distinguishable*: same slot, same brightness, same word count. The Insider's says what the
     * three share; the Resident's says nothing about what the tiles do under a thumb, because for
     * that player they do nothing.
     *
     * **There used to be a second line**, HOLD TO USE THESE THREE against WITHIN NOMINAL
     * PARAMETERS. The redesign cut it (D-202: screens say little): the hold is taught by the tile,
     * whose bar fills under a thumb, and an instruction that only one role's copy could carry was a
     * line the parity had to work to keep level.
     */
    val tier2Note: String =
        if (insider) "SURGE, STATIC AND ISOLATE SHARE THIS" else "BUS, THERMAL AND CACHE NOMINAL"

    /**
     * **The seat roster the Surge picker draws** (`gdd.md:392`, D-115).
     *
     * The house's when there is one and the port's five when there is not — [egressNodes]'
     * arrangement exactly, derived once here so the picker and anything else that reads it resolve
     * the same list rather than each keeping its own.
     *
     * **Order is the wire**, not a presentation: a row's position in this list is the seat the
     * ability is fired at. See [PanelState.roster].
     */
    val roster: List<String> = housed(state.roster, ROSTER, nothing = emptyList())

    /**
     * **What the notices screen says: the house notice's words, or nothing at all** (D-035, D-187,
     * D-223).
     *
     * Three cases, [housed]'s three. A phone with no house attached and no notice pushed draws the
     * design's fixture, so the render harness still shows the screen as drawn. **A notice naming
     * nobody draws nothing**: no *data is complete* line (the owner, 2026-11-15), because the empty
     * notice arrives at every meeting and a sentence on it would be the house speaking when nobody
     * evaded. A housed phone that was pushed nothing draws nothing too, never the fixture: the
     * fixture names MARCUS, and on a housed phone that would be the house naming a player nobody
     * reported. A notice naming somebody draws [noticeBody].
     */
    val notice: String? = when {
        state.notice != null -> noticeBody(state.notice.map { roster.getOrNull(it.index).orEmpty() })
        state.houseAttached -> null
        else -> Notifications.notice.body
    }

    /**
     * **The markers the Isolate picker draws** (`gdd.md:315`, D-150).
     *
     * The house's when there is one, the port's when there is not. **Nothing here filters it** — the
     * Egress-node exclusion is the house's and arrives as a shorter list.
     */
    val isolateTargets: List<Destination> =
        housed(state.isolateTargets, ISOLATE_TARGETS, nothing = emptyList())

    /**
     * **Whether the house's monitoring is jammed right now, and the field the noise is drawn from**
     * (D-186).
     *
     * The house's or nothing — there is no fixture, for [PanelState.staticJam]'s reason. Passed
     * whole rather than as a boolean and a long, so a surface cannot draw noise without the seed
     * that makes it deterministic.
     */
    val staticJam: StaticJam? = state.staticJam

    /** What this phone is carrying, if anything (D-168). See [PanelState.carrying]. */
    val carrying: Carried? = state.carrying

    /**
     * **What FILES holds: this phone's carry, and nothing on a housed phone that carries nothing**
     * (D-257). The screen said `1 ITEM . DISK 01 . RACK` on every phone in every round, carrying or
     * not; the port's carry stands in only with no house attached.
     */
    val filed: Carried? = housed(state.carrying, FILED, nothing = null)

    /**
     * **The Terminal's live plan, its playback and the couch's: the port's bungalow and its counts,
     * or nothing** (D-257, unit 4d).
     *
     * [Plan] is the design's fixed bungalow (KITCHEN, LIVING, HALL, STAIRS, STUDY, GARAGE) with
     * drawn counts, and on a housed phone it put rooms the home does not have, holding people
     * nobody counted, on the Terminal and on the couch. **No phone is sent the home's plan** (D-186:
     * *the home plan returns to crossing to NO client*) **and no house message carries an
     * occupancy count** (the spectator map is unbuilt, D-136; the Terminal's observations have no
     * producer), so a housed phone draws neither: an empty frame where the plan goes, the same on
     * every phone. Null is that empty frame.
     */
    val terminalCounts: List<Plan.RoomCount>? = housed(null, Plan.terminalCounts, nothing = null)

    /** The playback's, for [terminalCounts]' reason. */
    val timelapseCounts: List<Plan.RoomCount>? = housed(null, Plan.timelapseCounts, nothing = null)

    /** The couch's true occupancy, for [terminalCounts]' reason. */
    val trueCounts: List<Plan.RoomCount>? = housed(null, Plan.trueCounts, nothing = null)

    /**
     * **How far back the playback is, and how much of it has played** — the port's `-1:12` and
     * 44%, or a dash and an empty bar (D-257). Nothing on any phone knows which second of a playback
     * it shows, because no playback history exists (E8, `TimelapseScreen`).
     */
    val timelapseAt: String = housed(null, "-1:12", nothing = NO_READING)

    /** See [timelapseAt]. */
    val timelapsePlayed: Float = housed(null, 0.44f, nothing = 0f)

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
        if (review.passes) Amber.Slate else Amber.SlateDefunct

    fun saveFill(review: HomeReview): Color =
        if (review.passes) Amber.SlateFill else Color.Transparent

    fun saveInk(review: HomeReview): Color =
        if (review.passes) Amber.SlateInk else Amber.SlateDefunct

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
     * **One name and no second sentence** (the owner's 2026-08-24 ruling). `HANDSHAKE` is the kind
     * of Subroutine and it is what all three surfaces say; the flavour line that used to sit under
     * it on the caught scan is deleted, and what a player does is [Subroutine.briefing] (D-188).
     *
     * **The scan's own answer where there is one, and the port's drawn row where there is not** —
     * [PanelState.current], and [egressNodes]' arrangement exactly. What the fixture buys is that a
     * screen drawing this can be rendered against a Subroutine other than the one fixture, which is
     * the difference between a surface that is tested and one that is looked at.
     *
     * ### ⚠️ NULL WITH A HOUSE ATTACHED, AND THE OWNER IS WHY
     *
     * This was a flat `?: CURRENT`, and [CURRENT] is `HANDSHAKE`, room `GARAGE`. On a housed phone
     * that is not a placeholder — it is the app telling a player, in a dark house, that the house
     * dealt them work it did not deal them, in a room they may not have. The owner read
     * `WORK JAM` on one screen and opened a Handshake on the next, and the two biggest bugs of that
     * evening were both this fixture standing in front of a real round.
     *
     * So with a house attached an unanswered scan resolves to **null**, and null is drawn as
     * nothing — the same treatment [room] and [marker] already get one type down, for the same
     * stated reason: *a screen draws no room rather than inventing one.* The port keeps the fixture
     * and keeps its screenshots.
     */
    val current: CurrentSubroutine? get() = housed(state.current, CURRENT, nothing = null)

    // ---- The work order --------------------------------------------------------------------

    /**
     * **The order the house dealt this seat, as rows a screen can draw** (D-114, D-106).
     *
     * One mapping, in one place, for the two surfaces that draw the order — the list and the
     * springboard widget. Written twice they would drift, and the drift would be silent: the
     * widget naming one Subroutine while the list highlighted another is a player walking to the
     * wrong room in the dark.
     *
     * **The blackout is carried, not applied.** `OrderLine.Blocked` has no kind on it, so
     * [OrderRow.Blocked] has no name on it, and the branch below is a translation rather than a
     * decision — there is nothing here that *could* name a blocked entry, which is what makes
     * D-114's known unknown a property of the types rather than of this function's discipline.
     *
     * **The port's seven stand in while the house has said nothing** ([ORDER]), so every screenshot
     * and every screen review sees the drawn design rather than an empty list.
     *
     * ⚠️ **And not once a house is attached**, where an unissued order draws no rows at all and
     * [nextUp] says `NOTHING OPEN`. A work order is the list of places a player walks to in the
     * dark; seven fictional rows would send them to five rooms the house never named. An empty
     * order is a phone that has not been dealt one yet, which is a true thing to say and a thing a
     * player can wait out.
     *
     * ### The destination is the house's now, and the row draws the first of them
     *
     * A known line carries its [Destination]s — the room and the glyph a player navigates by. A
     * Subroutine has one; the Array Wipe circuit has three, in D-122's draw order, and the row
     * draws the **first**, which is where the circuit begins. Naming three rooms on one line of a
     * list read by lamplight would be worse than naming the one you walk to first, and the widget
     * that names it has room for one.
     *
     * **A line with no destinations draws none.** That is a round armed without a home, and a blank
     * column is the honest answer: a room name beside work the house chose, invented by the phone,
     * is a lie a player in the dark has no way to check.
     */
    val order: List<OrderRow> = state.order?.map { line ->
        when (line) {
            is OrderLine.Known -> {
                val at = line.at.firstOrNull()
                val room = at?.room
                val glyph = at?.let { MarkerShapes[it.shape] }
                when (val work = line.work) {
                    is AssignedWork.Subroutine -> Subroutine.of(work.kind).let { roster ->
                        OrderRow.Named(
                            line.index, roster.label, roster.light, line.done, room, glyph,
                        )
                    }
                    // **The circuit, drawn from real data at last.** Its label and its light are
                    // this client's — the design rates ten Subroutines and rates the circuit
                    // nowhere, so BRIGHT is still the port's reading (D-113 ratified the value,
                    // nothing ruled the row) and it is written down in one place rather than
                    // hand-typed into a fixture.
                    AssignedWork.ArrayWipe -> OrderRow.Named(
                        line.index, ARRAY_WIPE, ARRAY_WIPE_LIGHT, line.done, room, glyph,
                    )
                }
            }
            is OrderLine.Blocked -> OrderRow.Blocked(line.index)
        }
    }.let { housed(it, ORDER, nothing = emptyList()) }

    /**
     * **The first line of the order this seat can act on** — what the springboard names and what
     * the list highlights.
     *
     * ### ⚠️ Naming one at all is in tension with D-114, and it is flagged rather than resolved
     *
     * *The work order is a menu, not a queue.* The house designates nothing; the player chooses
     * among whatever is actionable, and that choice — which is really a choice about how much
     * light to make for the next ninety seconds — is the whole point of D-106's always-visible
     * signature. A widget headed NEXT SUBROUTINE is the queue the ruling replaced, surviving as a
     * presentation. It is left standing here because changing it is a copy and layout decision
     * nobody has taken, and because a springboard with nothing in that slot is a worse answer than
     * a first-actionable one. **Recorded as a flag, not as a reading.**
     */
    val nextUp: NextSubroutine = order.let { rows ->
        val named = rows.filterIsInstance<OrderRow.Named>()
        val next = named.firstOrNull { !it.done } ?: named.lastOrNull()
        NextSubroutine(
            row = next,
            index = (next?.index ?: 0) + 1,
            total = rows.size,
            done = named.count { it.done },
        )
    }

    // ---- Clocks -------------------------------------------------------------------------------

    /**
     * The countdown this screen is showing, or [Countdown.NONE] where it has none.
     *
     * **Display, not a clock.** The value comes from [PanelState.secondsLeft], which the house's
     * phase push starts and `FlowHost` counts down (D-243), and from the design's drawn moment on a
     * phone with no house attached. A housed phone with no phase clock draws a spent one rather
     * than the drawing's moment. The window it counts down is [Countdowns]'.
     */
    /**
     * **Which phase of the meeting the house has opened, as the couch's one screen needs it**
     * (D-134, `Effect.MeetingPhaseOpened`).
     *
     * Null with **no** house attached resolves to the vote, which is the phase the design drew the
     * outside view in (`VOTING ENDS IN 0:24`).
     *
     * ⚠️ **Null WITH a house attached resolves to [MeetingPhase.CheckIn]**, and that is not a
     * fixture — it is the truth, and the truth already draws itself. The house opens every phase by
     * pushing it; a phone that has been pushed none is at the gate, whatever screen it is standing
     * on. [Countdowns.onGhostMeeting] gives `CheckIn` **no clock at all** (D-104: the gate closes on
     * a count of phones, not on a timer) and [Countdowns.ghostClockLabel] calls it
     * `WAITING FOR THE HOUSE`, which is the honest-empty form written out in the couch's own words
     * long before anybody needed it here.
     *
     * The evening this fixes: `PHASE -` on every logged line on both phones, all night, while both
     * of them drew a vote clock counting down. **The couch was watching a ballot that did not
     * exist**, and the one screen a player who is out looks at for a whole round was the screen
     * lying hardest.
     *
     * **It never advances anything.** Like every clock value on this class it is a hand position
     * the house pushed; what it decides here is which window is being counted and what the row
     * above it is called.
     */
    val meetingPhase: MeetingPhase =
        housed(state.meetingPhase, MeetingPhase.Vote, nothing = MeetingPhase.CheckIn)

    /**
     * The house's phase clock, or, on a housed phone that has none, a spent one: never the design's
     * drawn moment in front of a player (D-243).
     */
    private val said: Int? = state.secondsLeft ?: 0.takeIf { state.houseAttached }

    val countdown: Countdown = when (state.screen) {
        // The one screen whose clock changes meaning under it: the couch watches every phase of a
        // meeting without moving, so its window is the phase's and not the screen's (D-134).
        ScreenId.GhostMeeting ->
            Countdowns.onGhostMeeting(meetingPhase, said, state.voteWindowSeconds)
        // **A window this phone opened is counted by this phone, and it wins here** ([window]).
        //
        // The scan is the only clock in the game of that kind, and it is the one the owner found
        // frozen: `6S LEFT` over twelve lit segments, forever, because the fixture moment was the
        // only number that ever reached this line. The house never sends a `secondsLeft` for it and
        // could not — nothing outside the phone knows the camera is open — so a stale figure left
        // over from a meeting screen is the only thing `state.secondsLeft` could contribute, which
        // is exactly why the local reading is asked for first rather than as a fallback.
        else -> Countdowns.on(
            state.screen,
            window?.secondsLeft ?: said,
            state.voteWindowSeconds,
        )
    } ?: Countdown.NONE

    /** What the couch's clock is counting down, in the phase's own words. */
    val meetingClockLabel: String = Countdowns.ghostClockLabel(meetingPhase)

    /**
     * **Whether the couch's phase has a clock at all.**
     *
     * False through [MeetingPhase.CheckIn] alone: D-104's gate closes when the last player walks
     * in, and a countdown drawn against no window would be the phone predicting a moment only the
     * house can see. The label stays — the couch is told what it is waiting for — and the readout
     * and the bar go, because a `0` over an empty bar reads as a clock that has *run out*.
     */
    val hasMeetingClock: Boolean =
        Countdowns.onGhostMeeting(meetingPhase, window = state.voteWindowSeconds) != null

    // ---- Meters ---------------------------------------------------------------------------

    /**
     * System Integrity, frozen between meetings: 28 of [METER_SEGMENTS] cells lit, which is the
     * design's 88% drawn.
     *
     * **A bar position, not a score.** The cells are display resolution (D-103, revision 21) and
     * the fraction they stand for is the only form the meter reaches a panel in.
     */
    val integrityLit: Int = state.integrityPercent?.let { percent ->
        // **The house's number drives the bar, and the bar drives nothing.** The percentage is
        // what arrives (D-103 — never a count, never a total), so the segments are computed from
        // it rather than the other way round, and the figure printed beside the bar is the same
        // value the bar is drawn at by construction. Rounded, so a meter with any work left on it
        // cannot light zero cells and a full one cannot light all but one.
        (percent * METER_SEGMENTS + 50) / 100
    } ?: when {
        // ⚠️ **An unlit bar where a house is attached and has not said.** System Integrity is how
        // close the Residents are to winning, and it is the one number the whole room plans around.
        // A phone drawing 28 of 32 off a fixture tells five people the job is nearly done; drawing
        // it at an ending tells them how the evening went. Neither is something this phone knows.
        // Empty, and the readout beside it says NOTHING rather than a percentage of a fiction.
        state.houseAttached -> 0
        // **The port's drawn fractions, for a phone with no house attached.** The two endings carry
        // the round's final reading and the port's figures are the design's own: the Residents took
        // it by clearing the meter, and the Insiders took it with this much still standing.
        state.screen == ScreenId.WinResidents -> 0
        state.screen == ScreenId.WinInsiders -> 14
        else -> 28
    }

    /**
     * **The meter as a percentage, and the only form it may ever be drawn in** (D-103, D-153).
     *
     * **The house's own number where there is one, and the bar's fraction where there is not.** A
     * percentage is what crosses the boundary (`Effect.SystemIntegrityLive`), so it is printed as
     * it arrived rather than round-tripped through the segments — [integrityLit] is a rounding of
     * *this*, which is what keeps the two from disagreeing while leaving the truth on the side that
     * has it. Two hand-maintained facts about one meter is how `SYSTEM INTEGRITY 14 / 32` came to
     * sit under a bar drawn at a different fraction, with a denominator that was a fossil of a
     * count F-005 had already corrected.
     *
     * ⚠️ **The springboard drew `88%` as a literal and now draws this**, which moves the design's
     * own screen to `87%`: the fixture lights 28 of 32 cells, which is 87.5, and the floor is what
     * every other meter on this class already prints. It is the same correction C2 made to Ghost3's
     * `66%` over a bar lit to 21 — recorded rather than absorbed, because it is a visible change to
     * a drawn screen.
     *
     * **The endings are not the safe place for a real number, and that is D-153's whole argument.**
     * The round is over, nothing can be acted on, and it is still not safe: the app is played two
     * to three rounds in an evening (D-157), so a denominator printed at the end of round one is a
     * denominator carried into round two — where it divides out the Insider count for every round
     * that follows. One ending screen would retroactively unhide a number D-103 spent a whole
     * revision hiding.
     */
    val integrityPercent: String = housed(
        state.integrityPercent?.let { "$it%" },
        fixture = "${integrityLit * 100 / METER_SEGMENTS}%",
        // `0%` would be a reading — the Residents have done nothing — and this is the absence of
        // one. The difference is the whole of D-103's care about this number.
        nothing = NO_READING,
    )

    /**
     * **The Egress countdown, which replaces the meter in place and takes the only number back.**
     *
     * The house's: [PanelState.egressLeft], started by `EgressOpened.remaining` and reset by each
     * `EgressHeld.remaining` (D-243). The bar drains with the clock beside it, one value. The
     * design's `1:42` over 22 of 32 segments stands in only on a phone with no house; a housed phone
     * the house has told no Egress draws an empty bar and a dash.
     */
    val egressLit: Int = state.egressLeft?.litOf(METER_SEGMENTS) ?: if (state.houseAttached) 0 else 22

    /** The countdown's readout, [egressLit]'s own value in `m:ss`. See there. */
    val egressLeftText: String = state.egressLeft?.text ?: if (state.houseAttached) NO_READING else "1:42"

    /**
     * **The Egress's type and its two nodes, from the house when there is one.**
     *
     * The port's drawn pair when there is not — [PanelState.secondsLeft]'s arrangement exactly.
     * Derived here, in the one place role- and round-dependent values are read side by side, so
     * that the alert and the widget resolve the *same* fallback rather than each keeping its own.
     */
    // ⚠️ The nodes are two rooms the house drew in the home the round was armed in, and the alert
    // sends people to them. On a housed phone that has been told neither, the port's pair would
    // send two players who cannot speak to each other to two rooms nobody chose.
    val egressType: String = housed(state.egressType, Notifications.EGRESS_TYPE, nothing = "")
    val egressNodes: List<String> =
        housed(state.egressNodes, Notifications.EGRESS_NODES, nothing = emptyList())

    /**
     * The Insiders' ending's Egress line: when the ending landed and which Egress the house ran
     * (D-243). `21:38 . BEACON` with no house; a part the house did not say is left out.
     */
    val egressEndedLine: String = listOf(endedAt, egressType).filter { it.isNotEmpty() }.joinToString(" . ")

    /**
     * **The Insiders' ending's line: how they won** (D-261). The house's route on a housed phone;
     * the mockup's Egress win with no house; nothing on a housed phone not told a route.
     */
    val insidersEndingLine: String =
        housed(state.endedBy?.let(::endingLine), endingLine(WinRoute.EgressUncontained), nothing = "")

    /** **The Residents' ending's line: how they won** (D-261). [insidersEndingLine]'s arrangement. */
    val residentsEndingLine: String =
        housed(state.endedBy?.let(::endingLine), endingLine(WinRoute.SystemIntegrityCleared), nothing = "")

    /**
     * **Whether the Insiders' ending draws EGRESS COMPLETE** (D-261): only when an Egress ran out,
     * and always in the render harness, which draws the mockup's Egress win.
     */
    val egressCompleted: Boolean = housed(state.endedBy?.let { it == WinRoute.EgressUncontained }, true, nothing = false)

    /**
     * **Whether the couch's Egress widget has anything to say** ([PanelState.egressRunning]).
     *
     * Read off the state and **not off the screen**, which is the distinction the carrier had to be
     * corrected on: `Ghost3` is the one surface a player who is out sits in front of for the whole
     * round, so the screen cannot say whether a crisis is running — it is the same screen either
     * way. The living need no equivalent, because their Egress *is* a screen.
     *
     * No fixture behind it. Everything else on this class stands in the port's drawn value while no
     * house has spoken; a widget that defaulted to *running* would be a bench asserting a crisis.
     */
    val egressRunning: Boolean = state.egressRunning

    /** Whether the house has stopped the Egress clock (D-133). See [PanelState.egressHeld]. */
    val egressHeld: Boolean = state.egressHeld

    /** This phone's own last beat, as the house answered it. See [PanelState.beatHeld]. */
    val beatHeld: Boolean? = state.beatHeld

    /**
     * **What is standing over this screen, with the Egress alert naming this round's rooms.**
     *
     * The single door onto [Notifications.arrivals] for everything that draws one. A composable
     * reaching into that table directly would get the port's fixture pair, and the day the house
     * drew a different one the banner and the widget would disagree — silently, in the dark, to two
     * people who cannot speak to each other.
     */
    //
    // **On a housed phone the quiet banner is the house's newest text** (D-247). `Quiet` is where a
    // house text lands, and the static table's entry for it is the design's `you're up` from
    // NUMBER WITHHELD at 21:03, so every text the house sent arrived under somebody else's words
    // and a drawn time. Nothing yet sent draws no banner.
    val arrival: Arrival? = when {
        state.houseAttached && state.screen == ScreenId.Quiet -> state.houseTexts.lastOrNull()
            ?.let { Arrival(Notifications.houseText(it), under = ScreenId.Home) }
        else -> Notifications.arrivalOn(state.screen, egressType, egressNodes)
    }

    /**
     * **THE INSIDERS WERE, with the blackmail published beside each name** — from the house when
     * there is one, and the port's pair when there is not.
     *
     * [PanelState.revealed]'s arrangement exactly, for [egressNodes]' reason: derived once, here,
     * so that the two ending screens resolve the *same* list rather than each keeping its own. The
     * Residents' ending and the Insiders' ending name the same people, and a fixture reached
     * directly by one of them would agree with itself and with nothing else.
     */
    val revealed: List<RevealedInsider> = housed(
        state.revealed, REVEALED,
        // ⚠️ The most consequential empty on this class. The reveal is the only moment a name and a
        // one line are lawfully together on a phone (D-116), so a fixture here does not merely
        // invent a fact — it accuses two named people of having been Insiders, on the screen the
        // whole room reads at the end of the evening. Nothing is the only safe nothing.
        nothing = emptyList(),
    )

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

    /**
     * **Resident progress as the couch sees it — the same meter [integrityLit] draws, live.**
     *
     * Two bars on this screen and only one of them has a sender: this is System Integrity, which
     * `Effect.SystemIntegrityLive` now carries to the classes outside the system, and it is
     * therefore [integrityLit]'s own value rather than a second fixture beside it. Written as its
     * own field only because the screen draws two bars and the other one is the Egress.
     *
     * The port's 21 of 32 stands in while no house has spoken.
     */
    val outsideLit: Int = housed(state.integrityPercent?.let { integrityLit }, fixture = 21, nothing = 0)

    /**
     * **`6 OF 6 VOTED` — the living's whole result besides the name** (the owner's 2026-08-24
     * ruling, which closed E-R2-9 / E-R1-2).
     *
     * The escalation this replaces was real and it was a leak in the drawn direction: the result
     * screen carried three rows with names and counts against them — MARCUS 3, SKIP 2, PRIYA 1 —
     * on **every phone in every round**, from a fixture, because `Effect.MeetingResult` had nothing
     * that could fill them. Either the effect was a field short or the screen showed the living
     * more than it should, and the owner ruled the second: **a per-candidate tally is the shape of
     * the room's thinking published to the people still in it.** The rows are gone from the type,
     * from the screen and from the wire's client half; what is here is the participation count the
     * vote screen was already counting up to.
     *
     * **The house's pair where there is one, and the port's six where there is not** —
     * [egressNodes]' arrangement, and the fixture is the design's own six-seat house so the drawn
     * screen and a real round read the same sentence.
     */
    val votedLine: String = housed(
        state.tally?.let { "${it.voted} OF ${it.living} VOTED" },
        fixture = "6 OF 6 VOTED",
        // A count of ballots nobody cast, in a round whose seats the phone does not know. The
        // owner's tally screen read `VOTED 4/6` in a FIVE-seat round, off this fixture, while the
        // house was running no vote at all.
        nothing = NO_RESULT,
    )

    /**
     * **Who the room restrained, as the house read the ballot** — or nobody.
     *
     * On the same message as [votedLine]'s pair (`Effect.MeetingResult`), and the house's word
     * whenever there has been one.
     *
     * **Nobody is an outcome and not an absence.** A Skip, a tie, or a vote nobody won all resolve
     * to no name, and the screen has a sentence for it — `gdd.md:413`'s *ties resolve to Skip*, and
     * D-075's *the whole weight of inaction sits behind restraining nobody.* A screen that fell
     * back to the fixture's MARCUS on that outcome would name a person the room voted not to.
     */
    val restrained: String? =
        if (state.tally != null) state.tally.restrained
        else "MARCUS".takeUnless { state.houseAttached }

    /**
     * `MARCUS WAS RESTRAINED`, or the sentence for a room that restrained nobody.
     *
     * ⚠️ **Those two are not the only cases and the difference matters more here than anywhere else
     * on this class.** `NOBODY WAS RESTRAINED` is a *result* — a Skip, a tie, a vote nobody won —
     * and it is the house's answer to a meeting that happened. A housed phone with no tally has not
     * had that answer; it has had no meeting result at all, and saying `NOBODY WAS RESTRAINED`
     * would publish a verdict on a vote the house never counted, to a room that would then act on
     * it. So the third case says [NO_RESULT] and says nothing about anybody.
     */
    val restrainedLine: String = when {
        restrained != null -> "$restrained WAS RESTRAINED"
        state.tally != null || !state.houseAttached -> "NOBODY WAS RESTRAINED"
        else -> NO_RESULT
    }

    /**
     * **Who called the meeting, with the port's own name standing in** (D-121).
     *
     * [drawn] is the name on the design's frame for whichever ring screen is asking, so the two
     * keep their separate fixtures — the port drew PRIYA on the call and ELLIOT on the report, and
     * a single fallback here would silently redraw one of the two frames the screenshots are read
     * against. **A housed phone the house named nobody to draws no name** (D-247): the port's
     * PRIYA on a ring in a house with no Priya is the app naming the meeting's author.
     */
    fun callerOr(drawn: String): String = state.caller ?: if (state.houseAttached) "" else drawn

    /**
     * **Resident progress, live, and still only ever a percentage** (D-103, D-134, D-153).
     *
     * The couch is the one audience allowed to watch this move while the round runs — and D-153's
     * rule does not soften for them by one digit. The denominator is `(seats − insiders) × 7`, so
     * a total printed here hands the one reader with nothing else to do all evening the Insider
     * count by division, and the app is played two to three rounds in an evening (D-157).
     *
     * **The house's own number where there is one, and the bar's fraction where there is not** —
     * [integrityPercent]'s arrangement exactly, and it took a second correction to get here. The
     * screen first drew a literal `66%` over a bar lit to 21 of 32, which is 65: two hand-maintained
     * facts about one meter, disagreeing, in the one place the meter is watched closely. Computing
     * it from [outsideLit] fixed that and left a quieter version of the same fault — **the figure
     * was a round trip through thirty-two segments**, so a house that said 70% got a couch printing
     * 68%, and every value that happened to land on a segment boundary hid it. The number the house
     * sent is the fact; the bar is a rendering of it, and a rendering may not be the source.
     *
     * ### It really is live now, which is what E-C2-2 was open about
     *
     * `Effect.SystemIntegrityLive` carries the meter to the two classes outside the system and to
     * nobody else, on every movement — so *live* is true of this bar on the one screen D-134
     * promises it live, and the living are told the same number once a shift instead. What still
     * has no sender is the bar below it; see [egressPercent].
     */
    val outsidePercent: String = housed(
        state.integrityPercent?.let { "$it%" },
        fixture = "${outsideLit * 100 / METER_SEGMENTS}%",
        nothing = NO_READING,
    )

    /**
     * **The real Egress number, which no living player of either role may see** (`gdd.md:1014`).
     *
     * [egressLit]'s own fraction rather than a second literal beside it — [outsidePercent]'s fault,
     * caught on the same screen in the same pass: `71%` was drawn over a bar lit to 22 of 32, which
     * is 68.
     *
     * **The house's own fraction now** (D-243): what is left of [PanelState.egressLeft] over the
     * length `EgressOpened.remaining` gave it, so the duration is the one the house fired with and
     * no lobby setting has to be guessed. The port's fraction stands in only with no house, and a
     * housed phone told no Egress prints the dash.
     */
    val egressPercent: String = when {
        state.egressLeft != null -> "${(state.egressLeft.remaining * 100).toInt()}%"
        state.houseAttached -> NO_READING
        else -> "${egressLit * 100 / METER_SEGMENTS}%"
    }

    /**
     * **The live ballot the couch reads, from the house when there is one** (D-117, D-134, D-075).
     *
     * [egressNodes]' arrangement exactly: the house's list wins, the port's five stand in, and the
     * fallback is a fixture rather than a second source of truth.
     *
     * **Read by one screen in the game and guarded as such.** `MeetingDisclosureTest` renders every
     * screen in both roles with this populated and asserts the attribution mark reaches only
     * `GhostMeeting` — in both directions, so denying the couch fails as loudly as widening it.
     */
    val ballots: List<Ballot> = housed(state.ballots, OutsideView.ballots, nothing = emptyList())

    /**
     * How many of [ballots] have been cast. **Counted off the rows, never written beside them.**
     *
     * It was a literal that happened to agree with the fixture, which is the state two numbers for
     * one fact always start in.
     */
    val votesCast: Int = ballots.count { it.forWhom != null }

    companion object {

        /** The house thread's two opening rows, the same words housed or not (see [houseThread]). */
        private val OPENING_THREAD = listOf(
            "Occupancy nominal. Access reviewed and retained.",
            "Seven subroutines assigned. Begin at your convenience.",
        )

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
        /**
         * **What the Array Wipe circuit is called, and what it does to the lamp.**
         *
         * Here rather than on the [Subroutine] roster because the circuit is not one of the ten:
         * a roster row for it would be this client inventing a light value the design never rated,
         * under cover of transcribing one. It is written down once, read by the fixture and by the
         * real mapping alike, so a house-dealt circuit and the drawn design cannot disagree about
         * either — which is precisely how the two were allowed to drift while one of them was the
         * only one that existed.
         *
         * BRIGHT is this port's reading. D-113 ratified the value; nothing ruled the row.
         */
        /**
         * **The port's five, standing in for a roster the house sends** (D-115).
         *
         * A fixture for [CURRENT]'s reason: a picker rendered from an empty list is a screen the app
         * never shows, and a test looking at one proves nothing about the screen that ships. The
         * same five the meeting's ballot is drawn with, deliberately — two fixtures naming different
         * houses would make a screenshot pass read as a house that had changed between screens.
         *
         * In play `Effect.IsolateTargetsOffered`'s sibling on the roster wire sends the real thing
         * and this is not consulted.
         */
        val ROSTER: List<String> = listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS")

        /**
         * **The port's drawn markers, standing in for the list the house offers** (D-150).
         *
         * [ROSTER]'s reason. Rooms and glyphs from the same set the work-order fixture uses, so the
         * two screens describe one house.
         */
        val ISOLATE_TARGETS: List<Destination> = listOf(
            Destination(MarkerId("card-hall"), "diamond", "HALL"),
            Destination(MarkerId("card-garage"), "arrow_right", "GARAGE"),
            Destination(MarkerId("card-bed2"), "cross", "BED 2"),
            Destination(MarkerId("card-study"), "square_frame", "STUDY"),
        )

        const val ARRAY_WIPE = "ARRAY WIPE"
        val ARRAY_WIPE_LIGHT: LightSignature = LightSignature.Bright

        val CURRENT: CurrentSubroutine = CurrentSubroutine(
            subroutine = Subroutine.Handshake,
            room = "GARAGE",
            marker = MarkerShapes["triangle_up"],
            index = 4,
            total = 7,
            done = 3,
        )

        /**
         * **The port's drawn work order — five named rows and two known unknowns** (D-114).
         *
         * A fixture, for [CURRENT]'s reason: a screen rendered from an empty list is a screen the
         * app never shows, and a test looking at one proves nothing about the screen that ships.
         * In play `Effect.WorkOrderIssued` sends the real thing and this is not consulted.
         *
         * **The two blocked rows are why the totals here read seven and the light marks read
         * five.** A blocked step names nothing, so its light is part of nothing — and the length
         * still says seven, because an order that shortened while work was blocked would make its
         * own length a tell about what the house had drawn.
         *
         * ### ARRAY WIPE is here and the house can now say it
         *
         * The circuit still is not one of the roster's ten and still has no `SubroutineKind` — it
         * is `AssignedWork.ArrayWipe`, a second case of what a line may be, carrying the three
         * stations D-122 draws as its destinations. So the row above is reachable from a real
         * order rather than only from this fixture.
         *
         * **What is still unruled is whether the house should ever deal one.** The design's mix is
         * *1 circuit, 1 long, 3 medium, 2 short* and none of that is built: length is D-129's `K`
         * and the kinds are drawn flat, so nothing puts a circuit in an order. Its BRIGHT remains
         * this port's reading (D-113 ratified the value; nothing ruled the row).
         */
        val ORDER: List<OrderRow> = listOf(
            row(0, Subroutine.Replay, "HALL", "diamond", done = true),
            row(1, Subroutine.Short, "GARAGE", "arrow_right", done = true),
            row(2, Subroutine.Jam, "BED 2", "cross", done = true),
            row(3, CURRENT.subroutine, CURRENT.room, CURRENT.marker, done = false),
            // The design's fixture used a hexagon; the vetted roster has none, because the
            // legibility pass cut everything reading as "circle with corners". The first
            // substitution was `trapezoid`, which at 10 units read as the same amber wedge as the
            // triangle two rows up -- exactly the confusion the roster exists to avoid. A frame is
            // topologically distinct from every solid shape in this list.
            OrderRow.Named(
                4, ARRAY_WIPE, ARRAY_WIPE_LIGHT, done = false,
                room = "STUDY", marker = MarkerShapes["square_frame"],
            ),
            OrderRow.Blocked(5),
            OrderRow.Blocked(6),
        )

        /** One fixture row, with the name and the light taken off the roster together. */
        private fun row(
            index: Int,
            subroutine: Subroutine,
            room: String,
            shape: String,
            done: Boolean,
        ): OrderRow.Named = row(index, subroutine, room, MarkerShapes[shape], done)

        private fun row(
            index: Int,
            subroutine: Subroutine,
            room: String?,
            marker: MarkerShape?,
            done: Boolean,
        ): OrderRow.Named =
            OrderRow.Named(index, subroutine.label, subroutine.light, done, room, marker)

        /**
         * **What the springboard says when the order holds nothing a player could start.**
         *
         * Unreachable by construction today — `OrderEntry.blockedBy` holds earlier indices only,
         * so the first line of any order is always actionable until it is done — and written down
         * anyway, because the alternative to a constant is a blank slot on the one screen a player
         * navigates a dark house by, and a blank slot is indistinguishable from a phone that has
         * stopped working. **Copy nobody has ruled on**; it is deliberately not D-124's NOTHING
         * FOR YOU HERE, which is a scan's answer about a card and not an order's about a round.
         */
        const val NOTHING_OPEN = "NOTHING OPEN"

        /** The port's carry on FILES, for a phone with no house attached ([filed], D-257). */
        val FILED: Carried = Carried(item = "DISK 01", to = "RACK")

        /**
         * The screens that arrive unasked, and therefore buzz.
         *
         * **A constant, not a literal rebuilt per screen.** It used to be a `setOf(...)` inside
         * [buzzes], so every construction of a `PanelVals` — every recomposition of every screen —
         * allocated a seventeen-element set to ask one membership question. The whole app has an
         * allocation budget of about 0.5 MB/s, and the lamp has to fail in the same frame as phone
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
        /**
         * **The light-field screens: everything drawn while the house lights are still on**, plus
         * the one ending that turns them back on.
         *
         * Written down here rather than left as a `when` branch for [BUZZING]'s reason exactly —
         * it is now read by two things instead of one, and two hand-kept copies of a set of screens
         * are two copies that agree on the day they are written. [mode] answers *how does this
         * screen look* from it, and [Flow.backable] answers *may a finger step back off it*, which
         * is the same question about the same fact: **before arming, nothing behind you has stopped
         * being true.**
         *
         * `WinResidents` is in it because the perimeter disarms and the lights come up, which is
         * what `Pre` means on this class. It is emphatically **not** backable, and it is not
         * excluded here — [Flow.backable] subtracts it along with everything else the house pushed,
         * so the exclusion is stated once, where the reason for it lives.
         */
        val PRE: Set<ScreenId> = setOf(
            ScreenId.Boot, ScreenId.Join, ScreenId.Maps, ScreenId.Editor, ScreenId.SaveName,
            ScreenId.HomeDetail, ScreenId.Delete, ScreenId.Secret, ScreenId.RoomEdit,
            ScreenId.MarkerSheet, ScreenId.ScanMarker, ScreenId.StairsWarn, ScreenId.TermTaken,
            ScreenId.TermRemove, ScreenId.MeetTaken, ScreenId.MeetRemove,
            ScreenId.Floors, ScreenId.ReviewNeeds, ScreenId.Perms, ScreenId.NameDrop,
            ScreenId.Lobby, ScreenId.WinResidents,
        )

        /**
         * **The dark screens that carry an amber block with black text in it**, which is a lit
         * ground on a panel that is otherwise black.
         *
         * `Tally` prints `MARCUS WAS RESTRAINED` in the design's inverted block and `WinInsiders`
         * puts its whole ROUND OVER / INSIDERS WIN header in one. `Notify` and `Banner` are the
         * springboard with a **heavy notification** on it — D-118's two kinds, the Egress and the
         * house's opening text — and that banner is an amber card with black type, three lines of
         * it. None of the four is `Pre` and none is `Inverted`, so none is caught by the two
         * questions above; all four put their single most important sentence on exactly the ground
         * the full band is worst over.
         *
         * **`Notify` and `Banner` were found by the guard rather than by the mining**, which is the
         * argument for the guard being what it is: `ScanLinesTest` asks the *render* whether a
         * screen has words standing on a ground that emits, and it named two the sweep of the
         * palette had missed. A banner is also not a per-player difference — the two heavy kinds
         * arrive on every phone at once — so a softer band while one is up is not something one
         * player can have and another cannot.
         *
         * ⚠️ **The strength is per screen, so these two get the softer band over their dark parts
         * as well**, where its only visible effect is the texture it lays over amber-on-black
         * content. That is a real cost and it is the one paid on purpose: the glass is one brush
         * over the whole panel (rule 7, and the reason it is the *surface* rather than any screen's
         * decoration), so the alternative is a second brush per lit region and a glass that knows
         * what is under it. A slightly finer texture on two screens is the cheaper of the two.
         *
         * **`Work` and `ScanCaught` joined with the restyle** (D-202): the work order's current
         * row and the caught scan's BEGIN are each the screen's one solid block, full width, with
         * black words on it, and the guard named both the moment they were drawn. **So did the
         * meeting's seven**: ANSWER on the two calls, I AM HERE on both walk-ins, and the band the
         * notice, the discussion and the vote open on. Every lamp in the house is white by then.
         *
         * Written down here rather than left as a `when` branch for [BUZZING]'s reason exactly: it
         * is the kind of set a new screen joins, and a new amber block on a screen that is not in
         * here ships with the band that made the owner file the report.
         */
        val AMBER_BLOCKS: Set<ScreenId> = setOf(
            ScreenId.Tally, ScreenId.WinInsiders, ScreenId.Notify, ScreenId.ArmedNotify, ScreenId.Banner,
            ScreenId.Work, ScreenId.ScanCaught,
            ScreenId.Call, ScreenId.Found, ScreenId.Assemble, ScreenId.Ghost2,
            ScreenId.Notice, ScreenId.Discussion, ScreenId.Vote,
        )

        val BUZZING: Set<ScreenId> = setOf(
            ScreenId.Armed, ScreenId.ArmedNotify, ScreenId.Notify, ScreenId.Banner, ScreenId.LockNotify,
            ScreenId.Call, ScreenId.Found,
            ScreenId.Assemble, ScreenId.Notice, ScreenId.Tally, ScreenId.Revoked,
            ScreenId.Restrained, ScreenId.ScanMarker, ScreenId.ScanCaught, ScreenId.ScanBad,
            ScreenId.ScanUnknown, ScreenId.TermBusy, ScreenId.GhostMeeting, ScreenId.WinInsiders,
            ScreenId.WinResidents,
        )

        /**
         * **What a housed phone prints where a number would be a fiction** — the honest-empty form
         * for every meter and clock on this class.
         *
         * A dash and not a zero, and the distinction is the whole reason it exists: `0%` is a
         * reading the house could have sent and did not, and a player has no way to tell one from
         * the other on a lit rectangle in a dark room. This is the same word the device consoles
         * printed all through the payoff run — `METER -`, `PHASE -`, `TALLY -` — where the ledger
         * was being honest about the absence the screens beside it were papering over.
         *
         * Only ever reached with a house attached. The unhoused port keeps its fixtures and its
         * screenshots; see [PanelVals.housed].
         */
        const val NO_READING = "-"

        /** What the house tells every Insider to do, after the quote (D-101, D-247). */
        const val HOUSE_INSTRUCTION = "Follow my instructions tonight and nobody else needs to hear about it."

        /** A housed Resident's note: no name, no room, no count (D-247, provisional). */
        const val HOUSED_NOTE = "somebody was already at the marker"

        /**
         * **The ending's line for each of D-131's four routes** (D-261), one place for all four.
         *
         * The meter's and the Egress's are the design's (`gdd.md:205`, the mockup's two endings).
         * **Parity's and Restraining every Insider's are provisional, owner to confirm**: no document
         * gives them words, so these are the shortest that say what the whole room watched happen.
         */
        fun endingLine(by: WinRoute): String = when (by) {
            WinRoute.SystemIntegrityCleared -> "PERIMETER DISARMED . YOU CAN LEAVE"
            WinRoute.InsidersRestrained -> "INSIDERS RESTRAINED . YOU CAN LEAVE"
            WinRoute.Parity -> "TOO FEW RESIDENTS LEFT"
            WinRoute.EgressUncontained -> "THE HOUSE REACHED THE INTERNET"
        }

        /** The Residents' ending's names, as the port drew them. The render harness's only. */
        val ENDING_RESIDENTS: List<String> = listOf("ELLIOT", "PRIYA", "MARCUS", "ROSE")

        /**
         * **What a housed phone says where a meeting result would be** — [NO_READING]'s sibling,
         * in a sentence rather than a figure, because the rows it replaces are sentences.
         *
         * `NOBODY WAS RESTRAINED` is a **result** and it is not this: the room voted and restrained
         * nobody. This is the house not having answered, and the two must never render alike — the
         * first is something the room may act on and the second is something it must wait out.
         */
        const val NO_RESULT = "NO RESULT YET"

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
         * initial seven), then the light goes dark and the phone goes back to where it was: nobody
         * should be standing in a dark room holding a lit screen at a wall by accident.
         *
         * **What the ten seconds are counting down to changed in revision 34 and the number did
         * not.** D-159 supersedes press-and-hold-then-release: the window now bounds an
         * *unattended* read — tap SCAN, turn the lamp onto the code, and the Subroutine opens on
         * acquire by itself — rather than a held button. The ten seconds were always the design's
         * final number and are untouched by that correction.
         *
         * **How many of those segments are lit is not a constant here.** It used to be — a `12`
         * sitting beside a hardcoded `6S LEFT`, two numbers for one fact, agreeing by hand. Both
         * now come off [PanelVals.countdown].
         */
        const val SCAN_SEGMENTS = 20

        /** Two segments a second. The bar's tick, and the only place the rate is written down. */
        const val SCAN_SEGMENT_MILLIS = 500

        /**
         * **The scan window, in milliseconds — the ten seconds D-159 rules, derived and not
         * restated.**
         *
         * Everything that has an opinion about when the window ends reads this: the flow's
         * auto-advance off the scan screen, and the app's `ScanWindow`, which produces D-160's
         * double vibrate at the same instant. A second literal would be a bar that empties at a
         * moment the phone does not agree is the moment.
         */
        const val SCAN_WINDOW_MILLIS = SCAN_SEGMENTS * SCAN_SEGMENT_MILLIS

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
        // Fresh, stale and cold are Ink, Mid and Dim (D-209): the three values text may use.
        mapOf(
            "KITCHEN" to (2 to Ramp.Ink),
            "LIVING" to (4 to Ramp.Mid),
            "HALL" to (1 to Ramp.Dim),
            "STUDY" to (0 to Ramp.Dim),
            "GARAGE" to (1 to Ramp.Ink),
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
            "KITCHEN" to (3 to Ramp.Dim),
            "LIVING" to (1 to Ramp.Dim),
            "HALL" to (2 to Ramp.Dim),
            "STUDY" to (0 to Ramp.Dim),
            "GARAGE" to (0 to Ramp.Dim),
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
 * **Built from `Effect.ScanAnswered` and the work order the house already sent** — see
 * `FlowModel.houseSaid`, where the scan's answer names a line of that order and the two are joined
 * by the index the house put on both. The port's own row stands in while no house has spoken.
 *
 * ### The flavour line is gone (the owner's 2026-08-24 ruling)
 *
 * There used to be an `instruction` here — *RESYNC THE KEEPALIVE*, a fake-technical sentence naming
 * what this instance of the kind asked for. Nothing on the wire ever carried it, so it was the
 * port's own words on every phone in every round or it was absent, and the owner deleted the slot
 * rather than commission a carrier for it: **what a player has to do is [Subroutine.briefing] and
 * is never null** (D-188), and a second sentence above it competes with the one that is true.
 * Marked in the log as a **v2 revisit** — the register is worth having and it is not worth a wire
 * field and ten strings before the game is played.
 */
data class CurrentSubroutine(
    /** Which of the design's ten. The name and the light signature are both its. */
    val subroutine: Subroutine,
    /**
     * Which room the card is in, off the order line's own destination.
     *
     * Nullable for `OrderLine.Known.at`'s reason, stated one type over: **an empty destination list
     * is lawful and means the house said nothing**, and a screen draws no room rather than inventing
     * one — a room name beside work the house chose, in a dark house, is a lie with no way to check
     * it.
     */
    val room: String?,
    val marker: MarkerShape?,
    val index: Int,
    val total: Int,
    val done: Int,
    /**
     * **The question the house dealt at this scan** — `SubroutineInstance.parameters`, carried from
     * the arrival to BEGIN so the two are read in one act (D-238).
     *
     * Null on the port's fixture, and only there: a phone with no house draws the bench's own
     * question. On a housed phone a null here is a scan the house never answered, and BEGIN opens
     * nothing over it (`FlowModel.beginSubroutine`).
     */
    val parameters: List<Int>? = null,
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

/**
 * **One row of the work order as a screen draws it** — [OrderLine]'s two cases, resolved.
 *
 * Two cases and no third, because the wire has two and a screen may not invent a state the house
 * cannot be in. The split is the redaction carried forward rather than re-applied: a [Blocked] row
 * is a **different type** with no name and no signature on it, so no composable, no `when` branch
 * and no later refactor can put either on one. A `Named` with its fields blanked out would have
 * been the same disclosure one careless `copy()` away (rule 3, D-114).
 *
 * **The label and the light travel together as a pair**, rather than as a [Subroutine] roster row,
 * for one reason the fixture makes plain: ARRAY WIPE is a circuit and not one of the roster's ten,
 * so it has no [SubroutineKind] and the house can never issue it as an order line — but the port's
 * drawn screen carries it. Anything the house sends is looked up in the roster on the way in
 * ([PanelVals.order]), so a real row's pair is always the roster's own and never a second copy.
 */
sealed interface OrderRow {

    /** Position in the order, and this row's identity within it. Stable for the round. */
    val index: Int

    /**
     * Actionable, or already done — the house named it, and the client looked up what it does to
     * the lamp (D-112: the kind crosses the wire, the signature never does).
     */
    data class Named(
        override val index: Int,
        val label: String,
        val light: LightSignature,
        val done: Boolean,
        /**
         * **Where this line is: the house's own room name and the glyph printed on the card.**
         *
         * Off `OrderLine.Known.at`, which carries a seat's own lines' destinations and nothing else
         * — knowing where your own next Subroutine is, is the game; knowing where every card in the
         * house is, is a map the house has never published.
         *
         * The **first** destination, where there is more than one: the Array Wipe circuit names
         * three stations in draw order (D-122) and a list read by lamplight has room for the one
         * you walk to first. The glyph is looked up in this client's own roster from the shape id
         * the house sent, which is D-112's arrangement applied to a marker.
         *
         * **Null draws nothing, and it is never filled from a fixture.** A round armed without a
         * home produces lines with no destinations, and the honest column there is an empty one:
         * a room name beside work the house chose, invented by the phone, is a lie a player in the
         * dark has no way to check. [PanelVals.ORDER]'s rows carry the port's drawn values, and
         * that is a fixture standing in for a house rather than a value mixed into one.
         */
        val room: String? = null,
        val marker: MarkerShape? = null,
    ) : OrderRow

    /**
     * **A known unknown, and the type is the whole of the blackout** (D-114).
     *
     * Something is there, and that is all the player learns — not its name, not its signature —
     * until it unblocks. Not absent, which would shorten the order and make its own length a tell
     * about what the house had drawn. Not spelled out, which would hand the player a route they
     * have not earned, and would say something about a **different player's** work: a cross-player
     * dependency names the person upstream of you as surely as it names the Subroutine (D-146).
     *
     * **It has one field and must never gain a second.** `SubroutineRosterTest`'s bijection makes
     * a kind resolvable to a name in one call, so a `kind` added here for any reason at all —
     * sorting, telemetry, a nicer placeholder — is the leak, fully formed, the same afternoon.
     */
    data class Blocked(override val index: Int) : OrderRow
}

/**
 * **What the springboard names and what the work order highlights.**
 *
 * One derivation for both surfaces ([PanelVals.nextUp]), because they were two hand-kept fixtures
 * and hand-kept fixtures drift.
 */
data class NextSubroutine(
    /** The first actionable row, or null if the order holds nothing a player could start. */
    val row: OrderRow.Named?,
    /** Human position in the order — `SUBROUTINE 4 OF 7`, so one-based. */
    val index: Int,
    val total: Int,
    val done: Int,
) {
    /** The name to draw, or [PanelVals.NOTHING_OPEN] where the order holds nothing startable. */
    val name: String get() = row?.label ?: PanelVals.NOTHING_OPEN

    /** Null draws no mark: nothing is being named, so nothing is rated. */
    val light: LightSignature? get() = row?.light

    /** The destination, where the port drew one — see [OrderRow.Named.room]. */
    val room: String? get() = row?.room
    val marker: MarkerShape? get() = row?.marker
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
                room.transit -> Ramp.Faint
                mine -> Amber.Deep
                else -> null
            },
        )
    }

/**
 * **The house notice's words for the players it names, or null for none** (D-035, D-187, D-223).
 *
 * The one place those words are decided. **An empty list is null**: a meeting where nobody evaded
 * draws nothing (the owner, 2026-11-15).
 *
 * ⚠️ **A non-empty list is null too, until the owner rules the copy** (D-223). The existing notice
 * (`Notifications.notice`, D-035's sentence, which D-161 says a permission flip is reported in)
 * carries a clock interval, *21:04–21:07*, and the house keeps no clock to fill one: every time on
 * every screen is a fixture. So the sentence cannot be reused unchanged, and a new one is the
 * owner's. Proposed, in its voice and shape: *Resident MARCUS was unreachable this shift. Occupancy
 * data for this interval is incomplete.* When it is ruled, this function is the whole change.
 */
fun noticeBody(names: List<String>): String? = when {
    names.isEmpty() -> NOTHING
    else -> "${residents(names)} unreachable this shift. Occupancy data for this interval is incomplete."
}

/** A meeting where nobody evaded: no words, and no box to put them in. */
private val NOTHING: String? = null

/** The owner's words for the named (D-223, 2026-11-15): one sentence, however many were named. */
private fun residents(names: List<String>): String = when (names.size) {
    1 -> "Resident ${names[0]} was"
    else -> "Residents ${names.dropLast(1).joinToString(", ")} and ${names.last()} were"
}
