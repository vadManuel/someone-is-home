package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.ContactMemory
import home.someoneshome.core.ContactUpload
import home.someoneshome.core.admit
import home.someoneshome.core.resolveContacts
import home.someoneshome.core.armingFor
import home.someoneshome.core.egressFor
import home.someoneshome.model.Balance
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.SavedHome
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.wallClockMillis

/**
 * **The rules, on the host's phone, joined to both wires — E-D3-3, closed.**
 *
 * P1 built the carriage down and P2 built the carriage up, and both units ended with the same
 * sentence: *both carriages are built and proved; the thing between them is the loop's.* This is
 * that thing. It stood in `app/src/cheats/` for three units, absent from a release build and
 * described as a bench that showed the production join was *possible*; N3 moved it here, because
 * the thing between the two carriages was never a bench — it is the house, and the house has to
 * ship. **The host IS the house**, which is what D3 proved a phone can be.
 *
 * What arrives with the move is the one input it never had: [ArmingInputs.home]. The draw used to
 * reach for the bench's fixture — five rooms nobody owns, ten cards nobody printed — because
 * nothing on this side could hand it the host's own home (E-PAY-3). It can now, and a round with
 * no home behind it is refused rather than substituted for.
 *
 * ### What it is, in three lines
 *
 * A [GameState], `admit` between the two wires, and [LobbyHouse.emit] for whatever the rules
 * produce. Every request that arrives from a phone has already had its seat stamped by
 * [IntentDesk] from the connection it came in on; every effect that leaves goes through
 * `EmitSchema` before it is rendered. This object adds no policy to either — **it holds the state
 * and turns the handle.**
 *
 * ### [mint], and which of its callers are the house's own
 *
 * [mint] puts an [Event] to the rules that no phone asked for. Three of those are legitimate and
 * permanent — the arming draw, the Egress draw and the round's timed transitions are all the
 * *house's*, minted above the rules, and P2's `factOf` explains at length why they can never be
 * `Intent`s. The rest are stand-ins for hardware and for a clock that do not exist yet; they live
 * in the cheat source set, they are absent from a release compilation, and every one of them is
 * marked where it is called rather than here.
 *
 * ### There is a tick driver now, and this object is what it turns — E-P3-3 closed
 *
 * The architecture puts the driver in the app roots, *for the reason `core` cannot import
 * coroutines*, and for two units it did not exist: nothing minted `DiscussionClosed`,
 * `VoteWindowClosed`, `TallyHalfwayReached`, `MeetingClosed` or `EgressExpired` when its moment
 * arrived, so a meeting left alone stopped at whatever phase it reached. [tickHouse] is that loop
 * and this class is its [HouseHandle]: the driver reads [state], calls [advanceTo] with the
 * simulation clock, and [mint]s whatever fell due — **the same two calls the script below makes,
 * with real time behind them instead of a number somebody typed.**
 *
 * [at] stays, and stays a bench thing: a script that wants to be at a cooldown's expiry without
 * standing there for three minutes jumps to the second. A round played that way is deterministic
 * and fast, which is right for a rehearsal and wrong for a house — and no production caller
 * touches it, which the cordon lint on this source set is what keeps true.
 */
class RoundAuthority(
    /**
     * What the lobby is holding, read at the instant it is asked. [LobbyHouse.armingInputs] in
     * production; a fixture in a test.
     *
     * **A function rather than the house itself**, and the reason is E-P2-2: `LobbyHouse.start`
     * waits on an advertisement no test has a radio for, so a class that took one could only ever
     * be checked by playing an evening. Both units before this one rebuilt the house's whole
     * arrangement in a rig for the same reason; taking the two seams instead means this object is
     * the one thing in the chain that does *not* need mirroring.
     */
    private val inputs: () -> ArmingInputs,
    /** The authority's only exit to the phones. [LobbyHouse.emit] in production. */
    private val emit: suspend (Effect, GameState) -> Unit,
    /**
     * **The house's contact window, now** (D-217): the wall clock, which every phone cuts its own
     * window from too. A test hands a fixed window; nothing in the rules reads a clock. Before
     * [note] so that the app root's trailing lambda stays the log.
     */
    private val contactWindow: () -> ContactWindow = { ContactWindow.containing(wallClockMillis()) },
    private val note: (String) -> Unit,
) : HouseHandle {

    var state: GameState = GameState.EMPTY
        private set

    /** [HouseHandle.state]. The round as it stands, for the tick driver to read deadlines off. */
    override fun state(): GameState = state

    /** Every effect the rules produced, in order — the authority's own record. */
    val emitted: MutableList<Effect> = mutableListOf()

    /** Every refusal, in order. D-066: an unrecorded refusal is an invisible drop. */
    val refusals: MutableList<RefusalReason> = mutableListOf()

    /** Every request that reached the rules, attributed. For the log and for the assertions. */
    val heard: MutableList<Intent> = mutableListOf()

    /**
     * The house's clock, in ticks, sampled where a request arrives.
     *
     * It only ever moves forward: two events reduced at one tick are two events the recording
     * cannot order, and the recording is the only debugging instrument this game has.
     */
    private var tick: Long = 0L

    /**
     * **The home this round is being played in**, taken off the lobby at [arm] and held for the
     * round.
     *
     * Read by [fireEgress], which needs the house's *shape* rather than its cards. Null before the
     * lights go out, which is the honest state of a phone with no round on it — and the reason
     * [fireEgress] can decline rather than reaching for a home that was never chosen.
     */
    private var home: SavedHome? = null

    /** What the contact resolver remembers between arrivals (D-213). A value, carried here. */
    private var contactMemory: ContactMemory = ContactMemory.EMPTY

    /**
     * **Every contact event the rules refused, in order** (R4, D-217). Also in [refusals], with the
     * rest; kept apart so that the one property that matters here can be asserted on its own: a
     * refused contact is recorded and **nothing is pushed for it, to anybody, ever**.
     */
    val contactRefusals: MutableList<Pair<Event, RefusalReason>> = mutableListOf()

    val armed: Boolean get() = state.armed
    val ended: Boolean get() = state.ended

    /** Where the clock stands, in whole seconds, for a log line that a person can read. */
    val second: Long get() = tick / Balance.TICKS_PER_SECOND

    /**
     * Where the clock stands, in ticks — readable, and writable only through [advanceTo].
     *
     * A script that wants to be past a cooldown compares against this and pushes the **driver**
     * until the house arrives, rather than setting the counter itself. See `RoundCheat.skipTo`, in
     * the cheat source set: a clock the bench sets and a clock the driver runs are two clocks, and
     * the deadline the driver evaluates is its own.
     */
    val clock: Long get() = tick

    /**
     * **Move the house's clock to T+[seconds], and never backwards.**
     *
     * The bench's shortcut: a named second, for a script that wants to be at a cooldown's expiry
     * without waiting for it. [advanceTo] is the general form and the one the tick driver uses.
     */
    fun at(seconds: Long) {
        advanceTo(Tick(seconds * Balance.TICKS_PER_SECOND))
    }

    /**
     * **Move the house's clock to [at], and never backwards** — [HouseHandle.advanceTo].
     *
     * This is where the tick driver stands, and the class KDoc's escalation is closed: the clock is
     * no longer advanced only by hand. What the driver does is exactly this and then [mint], which
     * is the same two calls the bench script makes — the difference is that one of them is
     * counting real time and the other is jumping to a number somebody typed.
     *
     * **Forward only**, for the reason on [tick]: two events reduced at one tick are two events the
     * recording cannot order, and a clock that could go back would put a transition before the
     * request that caused it.
     */
    override fun advanceTo(at: Tick) {
        if (at.step > tick) tick = at.step
    }

    /**
     * **LIGHTS OUT, for real: the draw, the arming event, and the first pushes of the round.**
     *
     * Everything it arms with comes off [LobbyHouse.armingInputs] — the seats the ledger admitted,
     * what each of them asked to be called, the lines they handed over, the host's Insider setting,
     * the host's ballot length **and the home the house is hosting** — all read in one instant,
     * because balance values lock here.
     *
     * ### The markers are the hosted home's, and there is no other home to be in
     *
     * `armingFor` has always taken a marker set; what filled it for three units was a fixture, and
     * the consequence was the largest thing this project had open: the owner mapped their real
     * house with a real camera, hosted it, held LIGHTS OUT, and the home was **discarded** —
     * work orders naming rooms they do not have, every card on their own walls opening nothing
     * (E-PAY-3). The socket is the same one; what goes into it is now [ArmingInputs.home]'s own
     * cards, and [egressFor] draws against that home's map and plan for the same reason.
     *
     * ### The home is kept, because the round is played in it after this line
     *
     * [home] is written here and read by [fireEgress], rather than re-read off the lobby at the
     * moment a crisis is drawn. A house taken down and brought up again mid-round would otherwise
     * put an Egress in a different building from the work orders — and *balance values lock at
     * arming* is the same rule, one field over.
     *
     * ### Two refusals, both out loud
     *
     * A lobby that is short, and **a phone that is hosting nothing**. Both return false and say
     * why rather than arming anyway. The gate is `LobbyDesk.readyToArm` — the same one the button
     * reads — and this is pre-arm, on the host's own phone, in a lit room, which is where
     * `armingFor` says a refusal belongs out loud. Neither is a fallback: a round armed in a home
     * nobody chose is the defect this method just stopped having.
     */
    suspend fun arm(seed: Long): Boolean {
        val inputs = inputs()
        if (!inputs.readyToArm) {
            note("WILL NOT ARM — ${inputs.seats.size} SEAT(S), ${inputs.oneLines.size} LINE(S) IN")
            return false
        }
        val hosted = inputs.home
        if (hosted == null) {
            note("WILL NOT ARM — THIS PHONE IS HOSTING NO HOME, AND A ROUND IS PLAYED IN ONE")
            return false
        }
        val markers = hosted.map.destinations
        val arming = armingFor(
            at = Tick(tick),
            seed = seed,
            seats = inputs.seats,
            chosenInsiders = inputs.chosenInsiders,
            markers = markers,
            voteWindowSeconds = inputs.voteWindowSeconds,
            names = inputs.names,
            // The station's card, so that a scan of it opens the map (D-199). The id alone: the
            // room the host put it in is the host's to say aloud.
            terminal = hosted.map.terminal?.card?.id,
        ).copy(oneLines = inputs.oneLines)
        home = hosted
        // A new round's radios start from nothing: last round's halves and debounces are about
        // seats and tokens that no longer exist.
        contactMemory = ContactMemory.EMPTY

        // The count and never who holds it. Which seats were drawn is the one fact on this event
        // that must not reach a log on a Mac -- a device console is a file, and a file naming the
        // Insiders is the whole game given away for every run after it.
        //
        // The home's NAME is in it and its geography is not. A name is what is already on the air
        // for the whole network; which card is in which room is the answer key to every work order
        // in the round.
        //
        // **And never the seed** (S3 of the owner's 2026-11-15 rulings, D-219). Every secret in the
        // round is an HMAC under it (D-214): the Insider draw, every contact seed and so every
        // token on the air. A log line carrying it is the round's answer key in a file.
        // `ContactRadioBenchTest` plays a round through this and fails on the seed in any encoding.
        note(
            "ARMING — ${inputs.seats.size} SEAT(S) · " +
                "INSIDERS ${arming.insiders.size} (SETTING ${inputs.chosenInsiders ?: "UNKNOWN"}) · " +
                "IN '${hosted.name}' · ${markers.size} MARKER(S) · ${inputs.oneLines.size} LINE(S)"
        )
        mint(arming)
        return armed
    }

    /**
     * **A request from a phone, attributed, put to the one gate** — [LobbyHouse.onIntent]'s body.
     *
     * The seat on [intent] came off the connection and nothing here may second-guess it. `admit`
     * with three arguments is `admit(state, factOf(intent, at))` and nothing else, so this door and
     * the [mint] door below reach the same gate.
     */
    suspend fun heard(intent: Intent) {
        heard += intent
        val before = state
        when (val admission = admit(state, intent, Tick(tick++))) {
            is Admission.Admitted -> {
                state = admission.reduction.state
                push(admission.reduction.effects)
            }
            is Admission.Refused -> {
                refusals += admission.reason
                // Loud to the authority, silent to the player: nothing is pushed, and the phone's
                // screen is corrected by whatever the house said last. D-066, rule 6.
                note("REFUSED — ${admission.reason} · ${intent::class.simpleName}")
            }
        }
        if (before.systemIntegrity != state.systemIntegrity) {
            note("SYSTEM INTEGRITY — ${before.systemIntegrity} -> ${state.systemIntegrity}")
        }
    }

    /**
     * **An event the house itself mints, put to the same gate.**
     *
     * Legitimate and permanent for the three draws that are the house's own — arming above, the
     * Egress below, and the round's timed transitions. A **stand-in** for everything else, and
     * every stand-in is marked where it is called rather than here, because a caller reading this
     * method should not have to come and look.
     */
    override suspend fun mint(event: Event) {
        val before = state
        when (val admission = admit(state, event)) {
            is Admission.Admitted -> {
                state = admission.reduction.state
                push(admission.reduction.effects)
            }
            is Admission.Refused -> {
                refusals += admission.reason
                note("REFUSED — ${admission.reason} · ${event::class.simpleName}")
            }
        }
        if (before.systemIntegrity != state.systemIntegrity) {
            note("SYSTEM INTEGRITY — ${before.systemIntegrity} -> ${state.systemIntegrity}")
        }
        if (!before.ended && state.ended) {
            note("THE ROUND ENDED — ${state.outcome?.winner} BY ${state.outcome?.by}")
        }
    }

    /**
     * **What one phone's radio heard, judged, and whatever it amounts to put to the rules** (D-217).
     *
     * The arrival queue's `Sighted` lands here. The upload is unpacked (an upload that is not one
     * resolves to nothing, and nothing about it reaches a phone), handed to `resolveContacts` with
     * this house's memory, its clock and its window, and every event the resolver mints is admitted
     * in the order it minted them: the gesture is the resolver's, the outcome is the rules'.
     *
     * ### R4: a refused contact is never answered (D-217)
     *
     * No phone asked for a contact or a report; the house concluded it. So when the rules refuse one
     * (a report from a seat that is out, a second meeting, a touch during a meeting) the refusal is
     * recorded and noted and **nothing goes down any socket** — not D-097's re-assertion and not
     * anything else. D-097 answers a phone that asked for something stale; nobody here asked. This
     * is a separate door from [heard] for exactly that reason: whatever [heard] one day does about
     * a refused request, it cannot reach a contact.
     */
    suspend fun sighted(seat: Seat, upload: RoundBody.Sighted) {
        val slots = SightingUpload.unpack(upload)
        if (slots == null) {
            note("AN UPLOAD THAT IS NOT ONE — SEAT ${seat.index}")
            return
        }
        val resolution = resolveContacts(
            contactMemory, Tick(tick++), contactWindow(), listOf(ContactUpload(seat, slots)), state,
        )
        contactMemory = resolution.memory
        for (event in resolution.events) concluded(event)
    }

    /**
     * **A phone's radio went down** (T2, D-222). Loud here, in the house's log, and put to the rules,
     * which hold it for the next meeting's notice. Admitted or refused, nothing goes down any socket
     * now: the notice is the meeting's, to everybody at once. A report that arrives before arming or
     * after the round is refused by the gate and recorded, like any.
     */
    suspend fun radioFaulted(seat: Seat) {
        note("RADIO FAULT — SEAT ${seat.index} · THE HOUSE ANNOUNCES IT AT THE NEXT MEETING")
        concluded(Event.RadioFaulted(Tick(tick++), seat))
    }

    /** One event the resolver minted, admitted; refused, it is recorded and answered with nothing. */
    private suspend fun concluded(event: Event) {
        val before = state
        when (val admission = admit(state, event)) {
            is Admission.Admitted -> {
                state = admission.reduction.state
                push(admission.reduction.effects)
            }
            is Admission.Refused -> {
                refusals += admission.reason
                contactRefusals += event to admission.reason
                note("REFUSED — ${admission.reason} · ${event::class.simpleName} (THE RADIO'S, NOT ANSWERED)")
            }
        }
        if (!before.ended && state.ended) {
            note("THE ROUND ENDED — ${state.outcome?.winner} BY ${state.outcome?.by}")
        }
    }

    /** The clock, for an event the caller is minting itself. Always the next tick, never a repeat. */
    fun now(): Tick = Tick(tick++)

    /**
     * **The Egress draw, which sits above the rules for `routeScan`'s reason.**
     *
     * Choosing two markers in non-adjacent rooms needs the home's *shape*, and the rules have
     * never held house geography. Its answer rides `Event.EgressFired` and lands in the recording
     * as an input, exactly as the Insider draw does. This is a real house call, not a stand-in —
     * what is a stand-in is the finger that asked for it.
     *
     * **The shape is [home]'s, which is the shape somebody walked.** It used to be the bench's
     * corridor, so the two ends of a crisis were two rooms of a bungalow nobody lives in — one of
     * them a CELLAR the owner does not own. A round with no home has no geography to draw against
     * and declines, which cannot happen after [arm] and is not worth being able to happen.
     */
    suspend fun fireEgress(actor: Seat): Boolean {
        val here = home
        if (here == null) {
            note("NO EGRESS — THERE IS NO HOME TO DRAW ONE IN")
            return false
        }
        val fired = egressFor(now(), actor, state, here.map, here.plan)
        if (fired == null) {
            note("NO EGRESS — THE HOUSE DECLINED TO DRAW ONE")
            return false
        }
        note("EGRESS DRAWN — ${fired.type} AT ${fired.nodes.joinToString { it.value }}")
        mint(fired)
        return state.egressRunning
    }

    /**
     * **Everything the rules produced, out to the phones the schema permits** — one at a time, in
     * the order the rules produced them.
     *
     * `EffectWire.emit` takes the state **after** the event, because the round-state axis moves
     * because of the event being processed: classifying against the state before it hands a seat
     * that has just gone out one last frame of living permissions.
     */
    private suspend fun push(effects: List<Effect>) {
        for (effect in effects) {
            emitted += effect
            emit(effect, state)
        }
    }
}
