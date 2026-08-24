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
        const val HEADER = "someone-is-home/recording/16"
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
