package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState

/**
 * A whole round, captured (story 0.3).
 *
 * Holds the events that entered the rules, the effects they emitted, **and the authority state
 * after each one**. The events are the input; the effects and the states are jointly the
 * expected output.
 *
 * **The state rows are not redundant with the effects.** Rule 1 requires the effect stream to be
 * identical whether a revoke landed or not, so for the transition that matters most, effects
 * carry no information about what actually happened. A recording that stored only effects
 * certified a build where the revoke ability did nothing as replaying correctly.
 *
 * **The initial state is recorded too**, so a recording is self-contained. Without it, replaying
 * from a different starting state produced a different outcome and still reported Identical.
 *
 * **Recordings hold complete authority state** — who is an Insider, real occupancy, real Egress
 * progress. Debugging artifacts, gitignored. Never attach one to an issue or hand one to a
 * player.
 */
class Recording(
    val initialState: String,
    val events: List<Event>,
    val effectTranscript: List<String>,
    val stateTranscript: List<String>,
    /**
     * Events the admission gate refused (D-066), in arrival order.
     *
     * **Recorded, because an unrecorded refusal is an invisible drop.** The gate exists to stop
     * the recording's effect rows and state rows disagreeing about a round that had not begun; a
     * gate that silently swallowed events would fix that disagreement by creating a different
     * one, where the recording no longer says what reached the rules at all.
     */
    val refusalTranscript: List<String>,
) {
    fun toText(): String = buildString {
        appendLine(HEADER)
        appendLine("I $initialState")
        events.forEach { appendLine("E ${Transcript.render(it)}") }
        refusalTranscript.forEach { appendLine("X $it") }
        effectTranscript.forEach { appendLine("F $it") }
        stateTranscript.forEach { appendLine("S $it") }
    }

    companion object {
        // 29: one effect a version 28 build cannot name -- `AbilityBlanked` (D-263), built for every
        // seat but the actor beside each `AbilityFired` a Surge, an Isolate, a Static or an Egress
        // answers. No event and no state row changed; the same events replay to a longer effect
        // transcript, so it is a bump for version 15's reason.
        //
        // 28: one effect a version 27 build cannot name -- `IsolateTargetsBlanked` (D-258), built
        // beside every seat's `IsolateTargetsOffered` at arming, at an Egress's start and at its
        // containment. No event and no state row changed; the same events replay to a longer effect
        // transcript, so it is a bump for version 15's reason.
        //
        // 27: one refusal reason a version 26 build cannot name -- `VoterOut` (H8, D-252) -- and the
        // rule change behind it: a selection or a READY from an out seat is refused at the gate, so
        // it no longer answers `VoteHeld` and `VoteSelectionShown`. No row changed shape.
        //
        // 26: `RadioFaulted` carries `cause` (D-223), and a row without one is fatal; the notice
        // effect is `NoticeIssued|unreachable=` where it was `RadioFaultsAnnounced|seats=`, and the
        // state column `radioFaulted` is `unreachable`, which a reset never writes (D-187).
        //
        // 25: one event and one effect a version 24 build cannot name -- `RadioFaulted` and
        // `RadioFaultsAnnounced` (T2, D-222) -- and a new state column, the radios awaiting the next
        // meeting's notice. Every meeting's gate now emits the notice, so every meeting's effect
        // transcript changes. The state row gains three columns: `reported` (S4's list, which it had
        // left out), `touches` (T1, D-220) and `radioFaulted`.
        //
        // 24: no row changed shape, and every touch reduces differently (T1, D-220). The first half
        // of a touch answers both of its phones and lands any Revoke either held; the second half,
        // at the same tick, changes nothing. So the same events replay to a different effect
        // transcript for every touch, and a Revoke held by the higher seat now lands in the lower
        // seat's half.
        //
        // 23: one refusal reason a version 22 build cannot name -- `AlreadyReported` (S4, D-219) --
        // and the rule change behind it: a second report of a Revoked seat a meeting has already
        // reported is refused, so the same events reduce differently. No row changed shape.
        //
        // 22: one refusal reason a version 21 build cannot name -- `ReporterOut` (R3, D-217) -- and
        // two rule changes that reduce the same events differently: a Revoked or Restrained seat
        // is disarmed (R2), and a contact at a meeting is refused (R5). No row changed shape.
        //
        // 21: one effect a version 20 build cannot name -- `ContactAnswered` (R1, D-216). A contact
        // from an actor who holds no armed Revoke used to answer `AbilityFired` and now answers
        // this, so the same events replayed by a version 20 build produce a different effect
        // transcript for every unarmed touch. No event and no state row changed.
        //
        // 20: no row changed shape and every round re-draws (D-214). Every stream the house draws
        // from the round's seed -- the Insider count and seats, the stations, the active set, each
        // seat's anchors and kinds, each Subroutine's question, each Egress's nodes and type -- now
        // starts from an HMAC of the seed under its own label rather than from the seed times a
        // constant, and the jam's seed on `StaticOpened` is an HMAC too. A version 19 recording
        // replayed by this build draws different work orders, questions, Egress nodes and noise from
        // the same arming row, and would report every one of them as a divergence.
        //
        // 19: one effect a version 18 build cannot name -- `ContactSeedIssued`, one per seat in the
        // arming burst (D-212). No event and no state row changed: the seed is derived from the
        // round's seed, which the arming row already holds. It is still a bump for version 15's
        // reason: the events are the same and the effect transcripts are not.
        //
        // 18: the Terminal became a station (D-199, D-203). One event a version 17 build cannot
        // construct (`TerminalLeft`) and one effect it cannot name (`TerminalAnswered`), and two
        // rows changed shape: **`RoundArmed` gained the T card's id**, without which a replay
        // answers every scan of it as NOTHING FOR YOU HERE, and **the state row gained the
        // station's card and its reader**, which reach no client and are therefore held nowhere
        // else. A version 17 arming row is missing a field this build requires.
        //
        // 17: two rows changed shape, in opposite directions, and neither could have been a
        // tolerant read.
        //
        // **`MeetingResult` gained the participation pair** -- `voted=` and `living=` beside the
        // outcome, because the owner ruled the living's result count-only (2026-08-24) and the
        // screen that used to compute those numbers off a fixture now reads them off the message.
        // Every version 16 result row holds two fields where this build writes four.
        //
        // **The state row LOST `accessPaused=`.** `GameState.accessPausedAt` is deleted: D-183
        // refuses a Tier-2 fire at the gate for the whole of a meeting and D-184 resets every
        // per-Insider clock at lights out, so between them nothing could observe the pause the mark
        // recorded. A version 16 state row carries a column this build does not write, and a
        // differential run across the boundary would report every state row as divergent -- which
        // is the condition this format's own note states for a smaller change.
        //
        // **Nothing about the round changes.** The pool behaves exactly as version 16's did,
        // because the mark was already unobservable; what is gone is the record of it.
        //
        // 16: three effects a version 15 build cannot name and one row that changed shape.
        // `RoleRevealed` was version 15's own arrival and never got its bump (found by the routing
        // day's closing sweep); `RosterIssued` and `RevokeLanded` are this unit's. A version 15
        // build cannot render any of the three, so a differential run across the boundary would
        // report a divergence that is really a format gap -- which is the condition version 15's
        // own note states in as many words for a smaller change.
        //
        // **`EgressOpened|nodes=` changed shape**, and that is the half that could not have been a
        // tolerant read: the nodes were card ids and are DESTINATIONS now -- card, glyph and room --
        // because the widget names the two nodes and a card id is not a name. Every Egress row in
        // every version 15 recording holds two bare ids where this build writes six escaped fields.
        //
        // **What version 16 does NOT gain is the names**, exactly as versions 10 and 11 did not gain
        // the lines and the names (D-115, D-116). `RosterIssued` renders its seat and the list's
        // LENGTH; a replayed round rebuilds blanks of the same length and the row comes out
        // byte-identical, so the game is reproduced without the words a third time.
        //
        // 15: one effect a version 14 build cannot name -- `MeetingCalling`, the per-seat message
        // that tells the calling phone which of the ringing phones it is (E-R1-1, ruled
        // 2026-08-23). It is the smallest bump this format has had: one row, one field, no state
        // row change and nothing removed.
        //
        // **It is still a bump, and the reason is the effect row rather than the state.** A version
        // 14 recording replays into a round where every meeting rang six identical phones and
        // nobody's screen said they had called it; the events are the same and the effect
        // transcripts are not, so a differential run across the boundary would report a divergence
        // that is really a format gap.
        //
        // 14: STATIC replaced the third Tier-2 ability outright (D-186), and this is the first
        // version bump in this format that is mostly a SUBTRACTION. Version 13's `SpoofFired` is
        // gone and `StaticFired` -- an actor and a tick, and nothing else -- is in its place; two
        // effects a version 13 build could name are gone with it (`HomePlanOffered`,
        // `PhantomPlaced`) and one it cannot name is new (`StaticOpened`).
        //
        // **The arming row lost the home's plan**, which is the largest thing this format has ever
        // stopped carrying. It existed because a picker over rooms cannot be drawn without rooms;
        // Static has no picker, so the plan crosses to no client and the rules never see one.
        //
        // The state row swapped the phantoms for one nullable deadline. It is load-bearing for the
        // isolations' reason inverted: the jam DOES reach every living phone, but it reaches them as
        // a window that has already started, so a replay that did not hold the deadline would come
        // out of the jam at the wrong moment. It carries no seat, which is D-186's anonymity kept in
        // the one artifact of this game that outlives the evening -- a recording cannot say who
        // jammed the house, because the house never knew.
        //
        // 12: Tier 2 arrived. Two events a version 11 build cannot construct (`SurgeFired`,
        // `IsolateFired`) and two effects it cannot name (`Surged`, `IsolateTargetsOffered`), and
        // the state row gained the two fields those need: the Access pool's pause mark and the
        // isolated markers. Both are load-bearing rather than decorative — `accessReady` reads the
        // pause, so it decides whether a Tier-2 ability is available at all, and the isolations
        // reach NO client, so a state row is the only artifact in the game that holds them. A
        // version 11 recording replays into a round where nothing was ever dark and every pool
        // recharged through every meeting. The cooldown list also doubled in length: every seat now
        // carries a Revoke row and an Access row (D-132, `gdd.md:306`).
        //
        // 11: the wire learned to say where, how long, and who. `RoundArmed.markers` were bare card
        // ids and are DESTINATIONS now -- card, glyph and room -- because a work order's whole
        // destination column is computed from them, and a version 10 recording replays into orders
        // that point nowhere. Beside them the arming row gained the host's ballot length, which
        // locks at arming like every other balance value, and the seats that said what to call
        // them. Two effects a version 10 build cannot construct arrive with it (the live meter and
        // the batched one), `MeetingPhaseOpened` carries the ballot's window, and a work order line
        // carries its destinations and can be a circuit rather than only one of the ten.
        //
        // **The escape covers the colon now**, which changes the bytes of every row holding a
        // marker id or a room name and is the honest reason this could not have been a tolerant
        // read of version 10. It closes E-L4-4: `:` has always been a sub-separator inside `open=`
        // and `presence=`, a marker id may contain one, and a room name is text a host typed.
        //
        // **What version 11 does NOT gain is the names** (D-115), exactly as version 10 did not
        // gain the lines. Both round-scoped texts render as seats, the parser rebuilds blanks, and
        // a replayed reveal publishes a blank name beside a blank line.
        //
        // 10: the ending. `ended` was a boolean and is the OUTCOME now -- winner and route -- so a
        // version 9 recording can say a round finished and cannot say who took it or how, which is
        // the only question anybody asks afterwards. Three effects a version 9 build cannot
        // construct arrive with it, and `RoundArmed` gained the seats that handed a one line over.
        //
        // **What version 10 deliberately does NOT gain is the lines themselves** (D-116). Both new
        // rows carry seats and no text: a recording is the one artefact of this game that outlives
        // the evening, and the promise printed on the screen where a line is typed does not survive
        // being written into one. A replayed round therefore rebuilds blanks and publishes blanks
        // at its reveal, and every row it produces is byte-identical to the row it is compared
        // against -- the game is reproduced without the words.
        //
        // 9: the Egress. Three events a version 8 build cannot construct -- and the first of them,
        // `EgressFired`, carries the two nodes and the type as INPUTS, drawn above the rules
        // against a home the recording does not hold. The state row's `egress` was a boolean and
        // is now the whole lifecycle: deadline, pause mark, held Sync Pulse offers and lockouts,
        // beside the house-wide shared cooldown. A version 8 recording says only that a house was
        // on fire, which cannot tell a contained Egress from one that ran out.
        //
        // 8: the scan answers. `MarkerScanned` stopped being silent -- it emits ScanAnswered and
        // PresenceChanged now -- and `PerformanceEnded` is a new event a version 7 build cannot
        // construct. The answer key moved off the work order and onto the open instance, where a
        // re-scan re-draws it (D-139), so `orders` lost `expected` and `open` gained the
        // parameters beside it; the state row gained the whole presence plane. A version 7
        // recording replays into a round whose questions are drawn somewhere else.
        //
        // 7: arming. `RoundArmed` gained the host's public Insider SETTING beside the hidden draw,
        // and the home's markers -- without which the stations, the active set and every work
        // order are undrawable, so a version 6 recording replays into a round with no work in it.
        // The state row gained the round's whole draw (orders with their answer keys, stations,
        // lit markers) and every Insider cooldown, and `open` gained the order line it came from.
        //
        // 6: the meeting engine. `VoteCast(voter, target)` became `VoteSelected` plus `VoteLocked`
        // -- the *changeable until the clock ends* model D-117 superseded, split into the live tap
        // and the irrevocable READY -- and six more meeting events arrived with them. The state row
        // gained the whole meeting, both out lists and the Egress flag. A version 5 recording holds
        // an event this build cannot construct, and its state rows are missing the ballots a tally
        // is computed from.
        //
        // 5: the verdict spine. `SubroutineCompleted` became `SubroutineReturned` and carries the
        // entry; the state row gained the work order.
        const val HEADER = "someone-is-home/recording/29"
    }
}

/**
 * The one walk through the authority, shared by everything in this module that runs a round.
 *
 * **Goes through [admit], not [home.someoneshome.core.reduce].** The admission gate is part of
 * the authority, so a harness that called the rules directly would be exercising a path no real
 * client can reach — and would have gone on certifying pre-arm effects as correct.
 *
 * [onStep] receives the event's index, the state AFTER the event, and the effects it emitted.
 * [onRefusal] receives the index, the event, and why the gate refused it. **Both carry the index
 * into the original event list, not a count of how many got through** — a refused event still
 * occupies a position, and a snapshot that named a different one could not be regenerated. Every capture —
 * the recording, the effect list, the per-client transcripts — is a different observer of the
 * same walk, so no two of them can disagree about what the round did.
 */
internal inline fun drive(
    initial: GameState,
    events: List<Event>,
    // Before `onStep` so that `onStep` stays the trailing lambda: a caller that only cares about
    // effects reads as `drive(initial, events) { after, emitted -> ... }` and cannot accidentally
    // bind its lambda to the refusal hook instead.
    onRefusal: (Int, Event, home.someoneshome.model.RefusalReason) -> Unit = { _, _, _ -> },
    onStep: (Int, GameState, List<Effect>) -> Unit,
): GameState {
    var state = initial
    events.forEachIndexed { index, event ->
        when (val admission = admit(state, event)) {
            is Admission.Admitted -> {
                state = admission.reduction.state
                onStep(index, state, admission.reduction.effects)
            }
            // No state change and no effects. Refusing is not a quiet version of reducing.
            is Admission.Refused -> onRefusal(index, event, admission.reason)
        }
    }
    return state
}

/**
 * Runs a round and captures it.
 *
 * Nothing here decides anything: it drives [reduce] and writes down what happened. A rule in
 * this function would mean the recording was of the harness rather than of the game.
 */
fun record(initial: GameState, events: List<Event>): Pair<GameState, Recording> {
    val effects = mutableListOf<String>()
    val states = mutableListOf<String>()
    val refusals = mutableListOf<String>()
    val state = drive(
        initial, events,
        onRefusal = { index, event, reason -> refusals += Transcript.render(index, event, reason) },
    ) { _, after, emitted ->
        emitted.forEach { effects += Transcript.render(it) }
        states += Transcript.render(after)
    }
    return state to Recording(
        initialState = Transcript.render(initial),
        events = events,
        effectTranscript = effects,
        stateTranscript = states,
        refusalTranscript = refusals,
    )
}

/** Every effect a round produced, in emission order. Shares the driver, so it cannot drift. */
fun effectsOf(initial: GameState, events: List<Event>): List<Effect> {
    val out = mutableListOf<Effect>()
    drive(initial, events) { _, _, emitted -> out += emitted }
    return out
}
