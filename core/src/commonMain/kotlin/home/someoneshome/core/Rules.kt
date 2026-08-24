package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Ballot
import home.someoneshome.model.Cooldown
import home.someoneshome.model.Effect
import home.someoneshome.model.Egress
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.Isolation
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.Outcome
import home.someoneshome.model.Presence
import home.someoneshome.model.PulseOffer
import home.someoneshome.model.Role
import home.someoneshome.model.Tick
import home.someoneshome.model.Seat
import home.someoneshome.model.Sound
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.tallyOf

/**
 * The simulation core. Pure, synchronous, deterministic (story 0.1).
 *
 * Ordered timestamped events in; new state plus an ordered list of effects out. No clock read,
 * no coroutine, no platform type — the boundary check fails the build if any of those reach
 * this module.
 *
 * That constraint is not tidiness. It is what lets a 25-minute eight-player round replay
 * byte-identically from its recording, and lets the rules be exercised headless in milliseconds
 * instead of with eight phones in a dark room once a month.
 */
fun reduce(state: GameState, event: Event): Reduction<GameState, Effect> =
    ending(event, dispatch(state, event))

private fun dispatch(state: GameState, event: Event): Reduction<GameState, Effect> = when (event) {

    is Event.RoundArmed -> armed(event)

    is Event.RevokeArmed -> revokeArmed(state, event)

    is Event.SurgeFired -> surgeFired(state, event)
    is Event.IsolateFired -> isolateFired(state, event)
    is Event.StaticFired -> staticFired(state, event)

    is Event.ContactMade -> contact(state, event)

    is Event.SubroutineReturned -> returned(state, event)

    is Event.MarkerScanned -> scanned(state, event)

    is Event.PerformanceEnded -> performanceEnded(state, event)

    is Event.MeetingCalled -> meetingCalled(state, event)
    is Event.MeetingCheckedIn -> checkedIn(state, event)
    is Event.ReadyToVoteDeclared -> readyDeclared(state, event)
    is Event.DiscussionClosed -> withMeeting(state) { openVote(state, it) }
    is Event.VoteSelected -> voteSelected(state, event)
    is Event.VoteLocked -> voteLocked(state, event)
    is Event.VoteWindowClosed -> withMeeting(state) { readTheBallot(state, it) }
    is Event.TallyHalfwayReached -> withMeeting(state) { takeover(state, it) }
    is Event.MeetingClosed -> withMeeting(state) { lightsOut(state, event) }

    is Event.EgressFired -> egressFired(state, event)
    is Event.SyncPulseReturned -> pulseReturned(state, event)
    is Event.EgressExpired -> withEgress(state) { egressExpired(state) }
}

// =================================================================================================
// The ending.
//
// One wrapper around every handler above, rather than a check inside four of them. The round can
// end on a Subroutine returning, on a takeover at a meeting, on a Sync Pulse pairing and on a
// countdown reaching zero — and it will one day be able to end on something nobody has written
// yet. A check written into each of those is a check the fifth one is missing, and the symptom of
// missing it is a round that carries on after it is over, in a dark house, with nobody able to say
// so out loud.
// =================================================================================================

/**
 * **Did that end the round — and if it did, say so to everybody.**
 *
 * ### Why this is a wrapper and not a branch in four handlers
 *
 * See the block above. The stronger half of the argument is that the wrapper cannot be forgotten:
 * a new handler is inside it the moment it is added to [dispatch], and a new **win route** is one
 * clause in [outcomeOf] rather than an edit to whichever handlers happen to reach it.
 *
 * ### Rule 1, and why an early return is allowed to live here
 *
 * `?: return base` is rule 1's forbidden shape, so it is worth being explicit about why this is
 * not an instance of it. **The branch is on the single most public fact in the game.** A round
 * that ended takes over every screen in the house, at the same moment, with the lights coming on;
 * a round that did not end emits nothing here and every phone carries on exactly as it was. There
 * is no audience for whom the absence of these effects is news — the presence of them is what is
 * news, to everybody at once. This is [takeover]'s exception in a wider form: a branch on
 * something the room already knows.
 *
 * ### The effects are built from the state BEFORE `endRound`, deliberately
 *
 * `GameState.endRound` empties the one-line desk, and [endingEffects] is the last thing in the
 * app entitled to read it. Building from `base.state` and applying the wipe to the result puts
 * D-116's deletion exactly one instruction after the final read, which is what *deleted when the
 * round ends* means when the publish **is** the round ending.
 */
private fun ending(
    event: Event,
    base: Reduction<GameState, Effect>,
): Reduction<GameState, Effect> {
    val outcome = outcomeOf(base.state, event) ?: return base
    return Reduction(
        base.state.endRound(outcome),
        base.effects + endingEffects(base.state, outcome),
    )
}

/**
 * **D-131's four routes, in one place, in the order they are checked.**
 *
 * ```
 * (a) SystemIntegrity reaches 0                     Residents, immediately
 * (b) no Insider is left in the round               Residents — unless an Egress is running
 * (c) living plain Residents <= living Insiders     Insiders  — the vote veto made formal
 * (d) an Egress ran its clock out                   Insiders  — outright
 * ```
 *
 * ### (a) is the one exception to the frozen meter
 *
 * The meter is batched and frozen between meetings (`gdd.md:1002`) and this does not wait for one:
 * *Residents win immediately — no meeting required.* A group that finished the work and then stood
 * in a dark house for six minutes waiting to be told so has been robbed of the moment they earned.
 *
 * ### (b) is written as *nobody left*, and it is checked before (c)
 *
 * D-131 says *Restraining every Insider*; the check counts **living** Insiders, because
 * `gdd.md:213` says it does and because the two are the same set — a Revoke needs a living Insider
 * to fire it and cannot be spent on its own seat, so Revokes alone can never take the last one off
 * the board. Before (c) because with no Insiders left parity reads `plainResidents <= 0`, which is
 * only true in a round where nobody is left at all; checking it second would let a degenerate
 * state answer with the wrong winner.
 *
 * **`insiderSeats.isNotEmpty()` is a guard against arithmetic, not against a game.** A round drawn
 * with no Insider at all is not a round the Residents have won, it is a round nobody armed
 * properly — `InsiderBand` makes it unreachable in play, and the clause is here so that a state
 * built by hand to exercise something else cannot end itself before the rule under test runs.
 *
 * ### (b) yields to a running Egress, which is the whole of D-131's exception
 *
 * *A running Egress outlives its Insiders and must still be stopped.* The house does not stop what
 * it was told to start, so Restraining the last Insider during one does not end the round: the
 * clause simply does not fire, the round continues, and the Egress decides it — containment clears
 * the flag and this route fires on the very next transition, expiry takes (d).
 *
 * ### (d) is a fact and not a predicate, which is why this function reads the event
 *
 * Once the Egress is cleared there is nothing left in [GameState] that can tell an expiry from a
 * containment, and giving the state a second field to remember it by would be a flag beside
 * `egress` that a build could de-synchronise. The gate has already refused [Event.EgressExpired]
 * unless an Egress was actually running and unpaused, so by the time it is seen here the fact is
 * established. **It is checked first**, ahead of the meter and ahead of parity: an Egress that ran
 * out is over before anything else is counted.
 */
internal fun outcomeOf(state: GameState, event: Event): Outcome? = when {
    // A round that is already over cannot end twice, and a round that never started cannot end at
    // all. Neither is reachable through the admission gate -- it refuses both -- but `reduce` is
    // callable directly and this function is the one place the answer should live.
    state.ended || !state.armed -> null

    event is Event.EgressExpired -> Outcome(Winner.Insiders, WinRoute.EgressUncontained)

    state.systemIntegrity == 0 -> Outcome(Winner.Residents, WinRoute.SystemIntegrityCleared)

    state.insiderSeats.isNotEmpty() && state.livingInsiders.isEmpty() && !state.egressRunning ->
        Outcome(Winner.Residents, WinRoute.InsidersRestrained)

    state.livingPlainResidents.size <= state.livingInsiders.size ->
        Outcome(Winner.Insiders, WinRoute.Parity)

    else -> null
}

/**
 * **The reveal, in the order the design puts it in** (`gdd.md:1051`).
 *
 * *The house speaks last. Lights up, and the Insiders stand. The blackmail publishes.* The room
 * does the middle one and the app does the other two, which is why there are three effects here
 * and not four — nothing on any screen announces that the Insiders should stand up.
 *
 * ### The order the effects are emitted in is not the order the room experiences
 *
 * The ending push goes first because it is what moves every screen; the reveal and the sign-off
 * are content that lands on the screen it moved them to. Grouped by kind rather than interleaved
 * per seat, exactly as arming's lamps are, so a seat going missing is a gap in a run of identical
 * lines rather than a subtle reordering.
 *
 * ### The publish reads every Insider's line and no Resident's
 *
 * The house never uses a Resident's (D-116). A Resident's line is typed, handed over, held for the
 * round, and dropped without ever having been read — which is what makes the promise worth
 * anything to the people who were never picked. The list is built by mapping
 * [GameState.insiderSeats], so a Resident's line is not filtered out of it: it was never in it.
 *
 * **Every Insider is named, living or not.** The reveal is about who was working for the house,
 * and somebody Restrained at the first meeting was working for it just as much as the two who were
 * still walking around at the end. `insiderSeats` is the draw and not the survivors, deliberately.
 *
 * ### One haptic, on the push, and nothing else here buzzes
 *
 * D-156: the underlying message count never drives the buzz count. The house has three things to
 * say and the room feels one of them.
 */
private fun endingEffects(state: GameState, outcome: Outcome): List<Effect> {
    // **Both texts are read here and both are dropped one instruction later** (D-115, D-116).
    // `endRound` empties the lines and the names in the same transition that applies this, so the
    // last read of either and its deletion are adjacent. A name is blank for a phone that never
    // said what to call its owner, which is the fail-closed direction for text: a visible nothing
    // rather than an invisible somebody else's.
    val revealed = Effect.InsidersRevealed(
        state.insiderSeats.sortedBy { it.index }.map {
            InsiderNamed(
                seat = it,
                name = state.nameOf(it).orEmpty(),
                line = state.lineOf(it).orEmpty(),
            )
        },
    )
    return state.seats.map {
        Effect.RoundEnded(
            seat = it,
            winner = outcome.winner,
            by = outcome.by,
            // D-135's set is closed at five and this is not one of them. See Effect.RoundEnded.
            haptic = Haptic.Short,
        )
    } + revealed + state.insiderSeats.sortedBy { it.index }.map {
        Effect.HouseSignedOff(seat = it, body = signOff(outcome.winner))
    }
}

/**
 * **The house's last words, and there are exactly two of them** (`gdd.md:1051`).
 *
 * The register is the one it used all evening: polite, incurious, and entirely unbothered by what
 * just happened to the people it was talking to. *Unfortunate* is the whole of what it has to say
 * about losing, and the flatness is the joke.
 *
 * On [Winner] rather than on [WinRoute], because the house does not care how.
 */
private fun signOff(winner: Winner): String = when (winner) {
    Winner.Insiders -> "Thank you for your cooperation."
    Winner.Residents -> "Unfortunate."
}

/**
 * **LIGHTS OUT — the round is constructed here, and everything about it is drawn from one seed.**
 *
 * Arming CONSTRUCTS the round rather than copying whatever was there. Carrying `revoked` or
 * `cooldownArmed` across an arming means a round can begin with a player already revoked and
 * nothing announcing it — and it is what let a recording replay from a different starting state
 * and still be certified identical.
 *
 * ### What the house draws, in order
 *
 * - **The Insider count and who holds it** arrive already drawn, on the event, clamped by D-103's
 *   band. See `Arming.insidersFor` for why that one draw sits above the rules.
 * - **The three stations** — Spares, Rack, Disposal — from the ordinary registered markers, every
 *   round, held as round state and never stored with the home (D-122).
 * - **The active marker set**, sized to seats. Everything else sits dark this round (D-123).
 * - **Every seat's work order**, `Balance.orderSize` entries long — the same length for every seat
 *   and both roles, computed from **public lobby facts alone** so that order length cannot divide
 *   out the count D-103 hides (D-129).
 * - **Every Insider cooldown, already running at half** its normal duration, so the round opens
 *   with a guaranteed stretch of peace (D-132).
 *
 * ### The effects, and why they are grouped by kind
 *
 * A lamp for every seat, then a work order for every seat, then the opening message for every
 * seat. Grouped rather than interleaved per seat so that the *shape* of the arming burst is a
 * property of the round rather than of the seat list — and so that a kind going missing for one
 * seat is a gap in a run of identical lines rather than a subtle reordering.
 *
 * **Nothing here reads `Role`**, apart from recording who was drawn — **and the one line that
 * tells each phone what it is** (E-R2-2). The lamps are identical, the orders are drawn by a rule
 * that never sees a role, and the opening message is the same fact sent to everybody; any per-role
 * difference at arming that reached a phone it was not about would be a tell delivered at the exact
 * moment the whole party is still standing together in the light. The reveal is not that, and it is
 * the design's (`gdd.md:923`): one message per seat, addressed to that seat, carrying that seat's
 * own role and nothing else.
 */
private fun armed(event: Event.RoundArmed): Reduction<GameState, Effect> {
    val seats = event.seats.sortedBy { it.index }
    val insiders = seats.filter { s -> event.insiders.any { it.index == s.index } }
    // The draws take cards and never rooms. The geography rides along beside them and is consulted
    // by exactly one thing -- `asLines`, which puts a destination on a client's own order -- so no
    // draw in this file can come out differently in one house than in another.
    val cards = event.markers.map { it.marker }
    val stations = stationsFor(event.seed, cards)
    val active = activeFor(event.seed, cards, stations, seats.size)
    val size = Balance.orderSize(seats.size, event.chosenInsiders)
    val orders = seats.map { workOrderFor(event.seed, it, size, active) }

    val state = GameState.armedRound(
        seed = event.seed,
        seats = seats,
        insiders = insiders,
        systemIntegrity = Balance.meterTotal(seats.size),
        // Nothing is open until somebody scans something. The spine drew one entry per seat and
        // left it open all round, which made the meter farmable from a single assignment; work is
        // opened by a scan now and spent by an entry, and advancing the order is arming's job.
        openSubroutines = emptyList(),
        workOrders = orders,
        stations = stations,
        activeMarkers = active,
        // The home's cards, glyphs and rooms, carried across arming rather than drawn -- the rules
        // have never held geography and still derive nothing from it. It is a lookup table with one
        // reader, and the reader hands out one seat's rows of it at a time.
        destinations = event.markers,
        // D-132, and it is drawn for every seat rather than for the Insiders alone: a list whose
        // membership was the Insider list would BE the Insider list.
        // D-132, and it is drawn for every seat rather than for the Insiders alone: a list whose
        // membership was the Insider list would BE the Insider list. **Two rows per seat now** --
        // the Revoke's own clock and the Access pool Tier 2 shares (`gdd.md:306`, `:318`) -- and
        // both open at half, so the round's guaranteed stretch of peace covers the support kit as
        // well as the two time-critical abilities.
        cooldowns = openingCooldowns(seats, event.at),
        // D-132 again, and it is ONE value rather than a row per seat because the Egress cooldown
        // is house-wide -- *shared with the other Insider*. See GameState.egressReadyAt.
        egressReadyAt = event.at + Balance.EGRESS_COOLDOWN / 2,
        // The house takes custody of the lines here and gives them back once, at the ending, and
        // only the Insiders' (D-116). Carried across arming rather than drawn, because they are
        // the one thing in the round a player wrote themselves.
        oneLines = event.oneLines,
        // The desk's names, taken into custody beside the lines and dropped by the same transition
        // (D-115, D-116). The house needs them for one sentence, at the end.
        names = event.names,
        // Balance values lock at arming. The ballot's length is the host's and it is public; it is
        // read once, by `openVote`, and published on the push that opens the vote.
        voteWindowSeconds = event.voteWindowSeconds,
    )

    return Reduction(
        state,
        seats.map { Effect.LampSet(it, LAMP_DIM) } +
            orders.map { Effect.WorkOrderIssued(it.seat, it.asLines(state.destinations)) } +
            seats.map { Effect.OpeningMessage(it, Haptic.Short) } +
            // **The reveal, one per seat, behind the message above** (`gdd.md:923`, E-R2-2). The
            // opening text is the same fact sent to everybody and this is what it is a doorway to:
            // *what differs by role is what is behind the message.*
            //
            // **This is the one line in this function that reads a role**, and it is the exception
            // the header's *nothing here reads Role* is written against rather than a hole in it: a
            // per-role difference at arming is a tell only if it reaches a phone that is not the
            // one it is about, and this is addressed to that phone alone. The run itself is
            // role-independent — one for every seat, in seat order, grouped like the three above —
            // so the *shape* of the burst still says nothing, which is what the differential
            // harness reads. Read off `state` rather than off `insiders`, so there is one answer to
            // this question in the round and the recording agrees with the phone.
            seats.map { Effect.RoleRevealed(it, state.roleOf(it)) } +
            // **The Isolate picker, for every seat, narrowed to Insiders by the allowlist and not
            // by this line.** A phone cannot draw a list it was never sent, and the ruling says
            // Isolate picks from one. Grouped by kind like the three runs above it, and the same
            // length as them, which is what keeps the arming burst role-independent — see
            // [isolateOffers], where the differential harness's finding is written down.
            isolateOffers(state),
    )
}

/**
 * **A card was read: the house resolves `(seat, card)` and answers** (D-123, D-124, D-110, D-139).
 *
 * ### The answer is an effect now, and both answers are the same effect
 *
 * A scan used to emit nothing at all, on the reasoning that *the house has nothing for you here*
 * was a sentence the client composed from the absence of work. That was rule 1's forbidden shape
 * with a justification attached: **a scan that opened work would have produced a message and a
 * scan that did not would have produced silence**, so the absence was the answer, and a phone that
 * heard nothing could not tell an empty card from a failed radio. Every path through this function
 * now emits exactly one [Effect.ScanAnswered] and exactly one [Effect.PresenceChanged], both
 * constructed once, outside every branch — the same discipline [contact] holds for the Revoke.
 *
 * D-124's two vocabularies are both real and only one of them is here: NOTHING FOR YOU HERE is
 * this effect with a null, and **unregistered paper never reaches the rules at all** — see
 * [ScanRouting], which decides that above the gate and reports it to nobody (D-072).
 *
 * ### The first ACTIONABLE entry at that card, which is what walks a self-chain
 *
 * An order deeper than the active set visits some cards twice, and the second visit is blocked by
 * the first until the first is done (D-123's blocked-by-your-own-work, discovered as the player
 * completes rather than announced in advance).
 *
 * ### Every scan draws a fresh instance, and a re-scan therefore re-asks (D-139, D-140)
 *
 * The mint happens on **every** scan and not only on the ones that find work. Minting inside the
 * branch would make the id sequence itself a record of which scans found something — a fact about
 * other players' work order, sitting in the one piece of state a replay compares byte for byte.
 * The draw is cheap and the shape is what matters.
 *
 * ### The Revoked seat's gate, which is hygiene and is allowed to be
 *
 * A seat outside the system opens nothing. This is a branch on state in a client-visible path, so
 * it is worth being explicit about why it is not rule 1's forbidden shape: **the emitted shape does
 * not move.** An out seat's scan produces the same two effects with the same fields, and the
 * allowlist declines to deliver [Effect.ScanAnswered] to an out class anyway — so this is the
 * second of two independent denials rather than the only one, and neither is visible as an
 * absence. Round-state is publicly observable (D-068): everybody in the room already knows who is
 * out.
 *
 * ### A scan always relocates the player, and that is why the else branch spends
 *
 * *A performer's own scan placement is ground truth* (D-136) — the house knows where somebody is
 * for exactly one reason, which is that they read a card whose place it knows. So a scan that
 * finds nothing does not merely fail to open work: it says this player is standing somewhere else
 * now, which closes the window and spends whatever was armed at the card they walked away from.
 * You cannot be armed at a card you are not standing at, and D-111 is explicit that there is no
 * half-finished performance to come back to — *the next scan restarts.*
 */
private fun scanned(state: GameState, event: Event.MarkerScanned): Reduction<GameState, Effect> {
    val (id, minted) = state.mintId()
    // **An Isolated card opens nothing, for anybody, and it answers exactly as an empty one does**
    // (`gdd.md:315`, and see [Isolation] for the ruling this is). The branch is on state and the
    // emitted shape does not move: the two effects below are constructed once, outside it, so a
    // scan at a dark marker produces the same `ScanAnswered` null and the same `PresenceChanged`
    // that D-124's NOTHING FOR YOU HERE already produces at a registered card holding no work for
    // you. **An Isolated marker must not announce itself as attacked**, and the way to keep that is
    // for there to be nothing in the answer that could.
    //
    // It is asked before the work order rather than after, so that an Insider's fake is dark at a
    // dark card too. A card that went offline for Residents and kept opening fakes for Insiders
    // would be a role oracle at every Isolated marker in the house.
    val offline = state.isIsolated(event.marker, event.at)
    val entry = if (state.isOut(event.actor) || offline) {
        null
    } else {
        state.workOrderFor(event.actor)?.openAt(event.marker)
    }
    // `openAt` returns only work a scan opens, so this is non-null for every entry it returns --
    // the circuit is stepped over there rather than arriving here as a kind that does not exist.
    val kind = entry?.work?.opensAs
    val instance = if (entry == null || kind == null) {
        null
    } else {
        instanceFor(state.seed, id, event.actor, entry, kind, event.marker)
    }

    val next = if (entry == null || instance == null) {
        spend(minted, event.actor)
    } else {
        minted.withOpenSubroutine(instance)
    }.withPresence(Presence(event.actor, event.marker, open = instance != null))

    // Both constructed once, outside the branch above. Deliberately not inside it.
    //
    // The instance the client is shown is the NARROWED one: it carries the parameters and cannot
    // carry the answer, because it is a different type rather than the same type with a field
    // blanked (rule 3). The answer never leaves `next`.
    val answered = Effect.ScanAnswered(
        seat = event.actor,
        opened = if (entry != null && instance != null) {
            SubroutineInstance(entry.index, instance.subroutine, instance.parameters)
        } else {
            null
        },
    )
    val placed = Effect.PresenceChanged(event.actor, event.marker, open = instance != null)
    return Reduction(next, listOf(answered, placed))
}

/**
 * **The performance window closed without an entry** — STOP NOW, or a step away from the marker
 * (D-111).
 *
 * **The work plane is told nothing and this is the whole of what the presence plane is told.** No
 * entry is graded, no partial answer is held, and nothing here distinguishes the player who pressed
 * STOP NOW from the player whose phone noticed they had walked out of the room — because an
 * abandonment record is the behavioural channel that separates a real Subroutine from a fake, and
 * D-111 closed it by refusing to keep one.
 *
 * **It spends what was armed**, for the reason a scan that finds nothing does: the window and the
 * arming are one performance, and *the next scan restarts.* An arming that survived the player
 * walking out of the room would let them hand over an entry from anywhere, which is D-110's cost
 * — standing at the marker where the house asked you to stand — refunded quietly.
 *
 * **The place is the house's own, not the phone's.** The event carries no marker: where this player
 * was, is what the house recorded when they scanned, and a report that named its own location would
 * be a phone asserting a placement rather than a window closing (D-136 — inference never overrides
 * knowledge, and this is not even inference).
 */
private fun performanceEnded(
    state: GameState,
    event: Event.PerformanceEnded,
): Reduction<GameState, Effect> {
    val where = state.presenceFor(event.actor)?.at
    val next = spend(state, event.actor)
        .withPresence(Presence(event.actor, where, open = false))
    return Reduction(next, listOf(Effect.PresenceChanged(event.actor, where, open = false)))
}

/** D-110's spend, for a seat that may have nothing open. A no-op is the fail-closed direction. */
private fun spend(state: GameState, seat: Seat): GameState =
    state.openSubroutineFor(seat)?.let { state.withOpenSubroutine(it.spent()) } ?: state

/**
 * **Armed, not fired — and it is refused while the cooldown is still running** (D-132).
 *
 * The cooldown starts here rather than at contact, which is what makes a botched stalk cost a full
 * one: an Insider who arms and then cannot get close has still spent it.
 *
 * **Silent in both outcomes, and that is what keeps rule 1 satisfied.** Arming changes nothing on
 * any screen and emits nothing to anybody whether or not the cooldown had run down, so the branch
 * below is on state alone. The player's own phone knows what it pressed and when its own cooldown
 * ends — that is input echo, not a game answer — and no other phone is told anything, because an
 * Insider whose cooldown reached the house at large would be an Insider announced by a timer.
 *
 * A seat with no cooldown row has no ability to arm. That is the fail-closed direction: a round
 * armed without cooldowns (an empty home, a hand-built state) grants nobody a Revoke rather than
 * granting everybody one.
 */
private fun revokeArmed(state: GameState, event: Event.RevokeArmed): Reduction<GameState, Effect> {
    val cooldown = state.cooldownFor(event.actor, InsiderAbility.Revoke)
    val next = if (cooldown == null || !cooldown.readyBy(event.at)) {
        state
    } else {
        state
            .copy(cooldownArmed = (state.cooldownArmed + event.actor).sortedBy { it.index })
            .withCooldown(cooldown.restartedAt(event.at, Balance.REVOKE_COOLDOWN))
    }
    return Reduction(next, emptyList())
}

// =================================================================================================
// Tier 2 — the shared Access pool.
//
// Three abilities on one clock (`gdd.md:306`), and the shape of all of them is `egressFired`'s: the actor's
// answer is constructed ONCE, outside every branch, and only the state transition moves. What
// differs from the Egress is one field, and it is deliberate — see `spendsPool`.
//
// Static is the third. It has no target seat, no target card and no target room -- what it moves
// is the house's own monitoring, for everybody at once, which is why self-targeting needed no
// expression in it either. See `staticFired`.
// =================================================================================================

/**
 * **The pool, restarted at one ability's weight** — the one writer of [InsiderAbility.Access].
 *
 * A function rather than three call sites, so that *firing any Tier-2 ability restarts the same
 * clock* is a fact the code states once. Replacing an ability on the pool (D-186) was a weight and
 * a handler and nothing here, which is what the pool being one enum member was for.
 *
 * **It writes an absolute deadline off the event's own tick**, like every other cooldown in the
 * game: a remaining count would be a fact about the moment somebody asked, and a tick replays.
 */
private fun spendPool(state: GameState, actor: Seat, at: Tick, weight: Long): GameState =
    state.withCooldown(Cooldown(actor, InsiderAbility.Access, at + weight))

/**
 * **Every seat's per-Insider clocks, at half — D-132's opening, written once because it happens
 * twice** (`gdd.md:318`, and the owner's 2026-08-23 meeting ruling).
 *
 * *Every Insider cooldown begins the round at half its normal duration*, which closes the opening-
 * Revoke problem structurally rather than by asking players not to. The ruling extends that to the
 * far side of every meeting: **a meeting hands every Insider the round's opening again.** So the
 * arithmetic is one function rather than two copies of `/ 2` that agree until somebody moves one.
 *
 * ### Both rows, for every seat, and the membership is what makes it safe
 *
 * Drawn for **every** seat rather than for the Insiders alone, exactly as arming draws it and for
 * arming's reason: a list whose membership was the Insider list would *be* the Insider list. A
 * Resident carries two cooldowns they can never spend, and that is the point.
 *
 * ### The Egress's clock is deliberately not in here
 *
 * It is house-wide rather than per-seat ([GameState.egressReadyAt]), so it is not a row this
 * function could write — and the ruling treats it differently anyway: while its Egress runs it is
 * **frozen out of resets until containment**, which is a condition on the caller and is written at
 * [lightsOut], where the condition can be read.
 */
private fun openingCooldowns(seats: List<Seat>, at: Tick): List<Cooldown> = seats.flatMap {
    listOf(
        Cooldown(it, InsiderAbility.Revoke, at + Balance.REVOKE_COOLDOWN / 2),
        Cooldown(it, InsiderAbility.Access, at + Balance.ACCESS_COOLDOWN / 2),
    )
}

/**
 * **The Surge: the target's lamp slams to full, then to black, and their phone makes the one noise
 * this game has** (`gdd.md:313`, `:342`, `:392`–`:401`).
 *
 * ### Rule 1's shape, and the branch is on the pool alone
 *
 * Every path emits exactly one [Effect.AbilityFired], constructed once, outside the branch — exactly
 * as [contact] and [egressFired] do. **Nothing here asks whether the target is Revoked, whether they
 * are an Insider, or whether they are the actor**, so `gdd.md:344`'s *targeting an already-revoked
 * player must appear to succeed on the actor's device* is kept by there being no question to answer
 * rather than by a branch that answers it identically. The panel cannot become a revoke-detector
 * because the rules never look.
 *
 * The target's own delivery is declined one layer out, by the allowlist, which permits
 * [Effect.Surged] to the two living classes and to no out class. Two independent denials, and the
 * actor sees neither.
 *
 * ### `cooldownStarted` tells the truth, and this differs from `egressFired` on purpose
 *
 * [Effect.AbilityFired] carries the flag *because* the answer is sometimes no, and the actor is the
 * only recipient of it: the row permits the kind to `Insider/Live` and the effect is addressed to
 * the actor, so nothing about it reaches a phone that did not press the button. What it says is
 * *your own pool was spent*, which is the actor's own business and is drawn on the actor's own
 * tile — input echo, not a game answer.
 *
 * **`egressFired` hardcoded `true` on the same field and now does not** (T1·8 / T2·7, closed). It
 * told a phone that fired into a running cooldown that it had spent one it did not, and the bar was
 * wrong for three minutes. All five handlers of this effect now read the flag off their own state
 * transition.
 *
 * ### Ranged, and there is no range
 *
 * ⚠️ *Ranged* is the design's word (`gdd.md:313`) and **the design gives no range mechanism** —
 * there is no distance, no room adjacency the rules may read, and no proximity hardware behind it.
 * So a Surge is house-wide valid, and *ranged* is carried by the fiction and by the physical fact
 * that firing it pulls the whole house to a place you may be standing in. Flagged, not invented: a
 * range rule guessed here would silently refuse honest shots in a house nobody measured.
 */
private fun surgeFired(state: GameState, event: Event.SurgeFired): Reduction<GameState, Effect> {
    val ready = state.accessReady(event.actor, event.at)
    val next = if (!ready) {
        state
    } else {
        spendPool(state, event.actor, event.at, Balance.SURGE_COOLDOWN)
    }

    // Constructed once, outside the branch above. Deliberately not inside it: the actor's device
    // stays inert whatever happened to anybody, and what it is told is that the ability fired and
    // what its own clock now says.
    val answered = Effect.AbilityFired(actor = event.actor, cooldownStarted = ready)
    val landed = if (!ready) {
        emptyList()
    } else {
        // **Addressed to the target and never to the actor**, which is `gdd.md:398` — and when they
        // are the same seat this arrives once, as the target's, which is what makes the self-surge
        // alibi indistinguishable from being attacked (`gdd.md:342`).
        //
        // D-135's long haptic is closed at five and this is not one of them, however loud the rest
        // of it is. A ruling rather than a default.
        listOf(
            Effect.Surged(
                seat = event.target,
                peak = LAMP_FULL,
                black = LAMP_BLACK,
                sound = Sound.Surge,
                haptic = Haptic.Short,
            ),
        )
    }
    return Reduction(next, listOf(answered) + landed)
}

/**
 * **Isolate: one card goes offline, and nothing anywhere says so** (`gdd.md:315`, `:621`, D-150).
 *
 * ### The Egress nodes are refused here and are NOT charged for
 *
 * D-150 excludes the two nodes from the target **list** for the duration of an Egress, and the list
 * is what [Effect.IsolateTargetsOffered] carries. This is the other half of the same rule: the gap
 * between a list arriving on a phone and a finger landing on it is real — the Egress can fire in
 * between — so a node can still reach the rules, and when it does **the pool is not spent.**
 *
 * That is the ruling's own argument, and it is why this is not the fake success the GDD originally
 * proposed: *the old shape had Isolate fire at a node, play its animation, spend from the **Access
 * pool** and do nothing — obeying "abilities never report failure" by charging a **shared** resource
 * for an action that could not work. That is not concealment, it is a tax on a teammate's budget,
 * paid in the dark by somebody who will never learn why the pool was short later.*
 *
 * **And there is nothing left to conceal**, because the house has already named both nodes to every
 * phone in the building on [Effect.EgressOpened]. `cooldownStarted = false` here tells the actor
 * something the whole house was told outright.
 *
 * ### Rule 1 still holds, and it holds on the shape
 *
 * One [Effect.AbilityFired], constructed once, outside every branch, for every outcome: pool
 * running, node refused, or a card taken dark. **Nothing is emitted about the marker itself** — no
 * announcement to the room, nothing to the seats whose work is at that card, and no schema row for
 * one. What a player learns is that they walked to a card and the house had nothing for them there,
 * which is [Isolation]'s ruling and is flagged as a build-time call.
 *
 * ### The carrier lockout falls out, as far as anything can fall out of a circuit nobody deals
 *
 * `gdd.md:621`: *a Insider who Isolates the Disposal marker does not delay one task — they lock that
 * player out of **every** task until it clears.* The mechanism it rests on is that a scan at an
 * Isolated card opens nothing, which is [scanned]'s doing and is exercised there for a station
 * exactly as for any other card. ⚠️ **The other half is unbuildable today**: `AssignedWork.ArrayWipe`
 * has no `opensAs`, `workOrderFor` deals no circuit, and there is no carried state anywhere in the
 * app (F-014, E7). So the lockout cannot be *tested* as a lockout — what is tested is that the
 * station goes dark like anything else, which is the whole of what this layer contributes.
 *
 * ⚠️ **The Terminal cannot be Isolated and it is supposed to be able to be** (`gdd.md:315`). The
 * reserved cards do not ride the arming event — `Event.RoundArmed.markers` is the *ordinary*
 * registered markers, explicitly excluding the Terminal and the meeting card — so the rules have
 * never held the Terminal's id and there is nothing here to name. Escalated.
 */
private fun isolateFired(state: GameState, event: Event.IsolateFired): Reduction<GameState, Effect> {
    // D-150. Read off the Egress rather than off a copy of the node list, so the exclusion the list
    // draws and the refusal the rules make are one fact asked twice rather than two that agree.
    val offered = state.egress?.isNode(event.target) != true
    val takes = offered && state.accessReady(event.actor, event.at)

    val next = if (!takes) {
        state
    } else {
        spendPool(state, event.actor, event.at, Balance.ISOLATE_COOLDOWN)
            .withIsolated(Isolation(event.target, event.at + Balance.ISOLATE_DURATION))
    }

    // Constructed once, outside the branch above. Deliberately not inside it, and deliberately the
    // ONLY effect this handler produces: a card going dark is discovered by walking to it.
    return Reduction(next, listOf(Effect.AbilityFired(event.actor, cooldownStarted = takes)))
}

/**
 * **Static: the house's monitoring goes down for ten seconds, and the house says so** (D-186,
 * `gdd.md:447-495`).
 *
 * *A ten-second house-wide jam of every monitoring surface the living can read.* Underneath it
 * nothing changes: tracking keeps recording the truth, check-ins land, System Integrity moves, and
 * every surface is correct again the instant the window closes. **What is jammed is the reading, not
 * the recording**, which is why this handler writes one deadline and touches nothing else.
 *
 * ### Rule 1's shape, and the branch is on the pool alone
 *
 * One [Effect.AbilityFired], constructed once, outside every branch — [surgeFired]'s and
 * [egressFired]'s arrangement. **Nothing here asks who the actor is**, whether they are an Insider,
 * where they are standing, or whether the house is already jammed. There is no target to be wrong
 * about, so the only thing that can make a fire worthless is a clock that has not come back, and
 * the actor's phone is told the same thing either way.
 *
 * ### It is fired in place, and the picker failed with the ability it belonged to
 *
 * D-186 supersedes D-175's third answer: *the third ability picks a room from the MAP* is gone,
 * **Static has no target at all**, and the screen that drew the plan went with it. The two
 * surviving pickers are unchanged and so is the argument under them — *the glow of reading the list
 * is the deliberate price* — which this ability does not pay because it has nothing to read.
 *
 * ### A second fire EXTENDS the window and can never shorten it
 *
 * [GameState.withStatic] takes the later deadline. The pool is per-Insider, so a second Insider can
 * fire into a running jam; what they buy is a longer jam, never a shorter one. **A window that could
 * be cut short would make its length on screen a report of how many Insiders reached for it**, which
 * is the anonymity going out through the render.
 *
 * ### The seed is a fact about WHEN and never about WHO
 *
 * The noise on the client is authored light (rule 5) and has to replay, so the field it is generated
 * from rides the effect. It is minted from the round's seed and the firing tick, **and from nothing
 * else** — no actor, no seat index, no Insider count. Two Statics fired at the same tick by
 * different Insiders would produce identical bytes, which is the property the anonymity rests on and
 * is asserted directly.
 *
 * ### The announcement is the effect, and there is only one of it
 *
 * *The house shows that it was jammed. It never shows who.* [Effect.StaticOpened] carries no seat,
 * so it is one message to the whole living house — the announcement and the render in one kind. The
 * out are exempt at the allowlist (D-134's liveness privilege, D-186's ghost exemption), and no
 * house notice is raised: D-187 narrows notices to detectable deliberate evasion, and **Static earns
 * none** because it is an ability rather than an evasion.
 */
private fun staticFired(state: GameState, event: Event.StaticFired): Reduction<GameState, Effect> {
    val ready = state.accessReady(event.actor, event.at)
    val until = event.at + Balance.STATIC_DURATION

    val next = if (!ready) {
        state
    } else {
        spendPool(state, event.actor, event.at, Balance.STATIC_COOLDOWN).withStatic(until)
    }

    // Constructed once, outside the branch above. Deliberately not inside it: the actor's device is
    // told the ability fired and what its own clock now says, and nothing else -- exactly what a
    // Surge into a running pool is told.
    val answered = Effect.AbilityFired(actor = event.actor, cooldownStarted = ready)

    // **The house announcing its own blindness, to every living phone in the building.** Not
    // addressed, not narrowed, and carrying no seat -- see Effect.StaticOpened. The seed is the
    // round's own, folded with the tick the fire landed on, so a replay draws the same noise and
    // two Insiders firing at the same tick are indistinguishable.
    val jammed = if (!ready) {
        emptyList()
    } else {
        listOf(
            Effect.StaticOpened(
                seconds = (Balance.STATIC_DURATION / Balance.TICKS_PER_SECOND).toInt(),
                seed = staticSeed(state.seed, event.at),
            ),
        )
    }

    return Reduction(next, listOf(answered) + jammed)
}

/**
 * **The noise field's seed: the round's own, folded with the tick.**
 *
 * Deterministic, replayable, and **incapable of naming anybody** — there is no seat argument and
 * there must never be one. A mix rather than a sum so that two jams a few ticks apart do not draw
 * two nearly identical fields, which on a ten-second window would read as the same jam continuing.
 */
private fun staticSeed(seed: Long, at: Tick): Long = seed * 0x5DEECE66DL + at.step * 0x9E3779B9L

/**
 * **The Isolate picker — built for EVERY seat, and narrowed by the allowlist** (D-150, D-079, the
 * 2026-08-22 ruling).
 *
 * The round's active markers with their rooms, minus the two Egress nodes while an Egress is
 * running. Built in one place and called from the three transitions that can change the answer —
 * arming, an Egress starting, an Egress being contained — so that *what the list says* is one rule
 * rather than three copies of one.
 *
 * ### ⚠️ It is emitted for Residents too, and that is the whole point of this function's shape
 *
 * The first build of this filtered to Insiders here, which is the obvious thing to write and is
 * **wrong in a way that only one instrument in this project can see**. `Rules.kt`'s own claim is
 * that the *only* place a role changes an outcome is a Resident's accepted entry banking against the
 * meter — and a role-filtered emission would have made the arming burst itself role-dependent. The
 * differential harness caught exactly that: swap the roles in a seeded round, and the authority's
 * effect stream came out different in something other than the meter.
 *
 * **That is not a leak on the wire and it would still have been a serious loss.** Story 0.6's
 * differential is one of three independent leak instruments, and its whole method is that a
 * role-swapped round produces a byte-identical transcript. A rule that branches on role retires the
 * instrument for every future change as well as for this one.
 *
 * So the narrowing happens where narrowing belongs: `ISOLATE_TARGETS_OFFERED` is permitted to
 * `Insider/Live` and to nothing else, so a Resident's copy is **constructed and delivered to
 * nobody** — [Effect.PresenceChanged]'s and [Effect.EgressSucceeded]'s posture exactly, and the
 * architecture's own division of labour: the rules say what happened, the emit boundary says who may
 * hear it. Rule 2 does the work for free the moment the row is one class wide.
 *
 * **Nothing here reads `roleOf`, `isOut`, or any per-seat state**, which is what makes that true.
 * Every seat gets the same list.
 *
 * **The whole list every time, never a diff**, for [Effect.WorkOrderIssued]'s reason: a message that
 * appeared only when something had changed would make its presence the news.
 *
 * **Already-Isolated markers stay on it.** Which cards another Insider has taken dark is not this
 * Insider's to know, and an omission would report it — an argument the node exclusion does not need,
 * because the nodes are public and an isolation is not.
 */
private fun isolateOffers(state: GameState): List<Effect> {
    val targets = state.activeMarkers
        .filterNot { state.egress?.isNode(it) == true }
        .mapNotNull { state.destinationOf(it) }
    return state.seats.map { Effect.IsolateTargetsOffered(seat = it, targets = targets) }
}

/**
 * **The rule idiomatic code breaks.**
 *
 * There is no early return on an invalid target. Every path through this function emits exactly
 * one [Effect.AbilityFired] with `cooldownStarted = true`, and spends the cooldown, whether the
 * contact landed on a live player, an already-revoked one, a fellow Insider, or the actor
 * themselves.
 *
 * Written the obvious way — `if (target.isRevoked) return Reduction(state, emptyList())` — the
 * *absence* of the effect tells the actor their target was already revoked. Nobody has to see a
 * screen; the silence is the message. The absent effect IS the leak, and it is invisible in
 * review because the code looks like ordinary defensive programming.
 *
 * So the branch is on the STATE TRANSITION only, never on the emitted shape.
 */
private fun contact(state: GameState, event: Event.ContactMade): Reduction<GameState, Effect> {
    val armed = state.cooldownArmed.any { it.index == event.actor.index }

    val next = if (!armed || state.isRevoked(event.target) || sameSeat(event.actor, event.target)) {
        // Nothing lands, but the cooldown is still spent.
        state.copy(cooldownArmed = state.cooldownArmed.filterNot { it.index == event.actor.index })
    } else {
        state.copy(
            revoked = (state.revoked + event.target).sortedBy { it.index },
            // D-134's split: *newly* Revoked means revoked since the last meeting ended, and it
            // decides whether the next meeting rings this phone or tells it to stand and walk in.
            // Two lists rather than a timestamp, because "newer than the last meeting" is a fact
            // about meetings and comparing ticks to work it out would put the rule in two places.
            newlyRevoked = (state.newlyRevoked + event.target).sortedBy { it.index },
            cooldownArmed = state.cooldownArmed.filterNot { it.index == event.actor.index },
        )
    }

    // Identical in every case. Deliberately not inside the branch above.
    return Reduction(next, listOf(Effect.AbilityFired(actor = event.actor, cooldownStarted = true)))
}

/**
 * **D-109 — the house grades every entry for real, for both roles, in identical words.**
 *
 * The verdict is constructed once, outside every branch, and it is the same shape in every case:
 * a correct entry, a wrong one, an entry against a Subroutine that was never armed, an entry at
 * the wrong card, an entry from a seat the house has assigned nothing. **The Insider's verdict is
 * emitted, never omitted** — the absent effect is the leak, and here it would be the loudest one
 * in the game: a fake that never came back is a role oracle after a single Subroutine.
 *
 * The two alternatives D-109 rejected are both worse than they look. *Say nothing back about a
 * fake* is rule 1's forbidden shape. *Roll a plausible failure distribution for a fake* is a
 * random number generator standing where the rule should be — and it is legible to the one reader
 * the device-side guarantee never covered, somebody watching over a shoulder in the dark, who
 * learns that this player's work always works.
 *
 * ### The one asymmetry, and it lives where no player can stand
 *
 * A plain Resident's accepted entry decrements SystemIntegrity. An Insider's writes nothing
 * (`gdd.md:382`) — the fake is real work, graded honestly, counting for nothing. That is a
 * difference in **state**, never in the emitted verdict, and [Effect.SubroutineGraded] has no
 * field that could carry it.
 *
 * It is visible in the effect *stream* rather than in the verdict, because
 * [Effect.SubroutineProgressed] is emitted only when the meter actually moves. That reaches
 * players who are outside the system and nobody else, and the alternative — emitting it on every
 * return with an unchanged value — is worse: it would tell the out that *somebody returned
 * something that did not score*, which is a per-return count nobody designed. See the escalation
 * in the worklog.
 *
 * ### The entry is spent whatever it said (D-110)
 *
 * One attempt per scan. The Subroutine re-arms only when the player scans the marker again — the
 * house never re-arms it silently and the phone never re-arms it on a timer. So the spend is
 * outside the branch too, exactly as the cooldown is in [contact].
 *
 * ### A seat that is out banks nothing, and that is hygiene rather than a second verdict (E-L1-2)
 *
 * A Revoked or Restrained seat holding an instance from before it went out could hand it over and
 * move the meter. Unreachable in play — an out phone has no Subroutine screen — and reachable by
 * the fuzzer, which is exactly the kind of hole that is a bug the day a reconnect replays a queued
 * intent. It is refused here, at the rules, now that per-seat round state exists to ask.
 *
 * **It is rule 1's shape and not its forbidden one.** The three effects below are constructed once
 * and outside every branch: an out seat's return emits the same verdict, the same order and the
 * same presence as anybody else's, and the allowlist already declines to deliver
 * [Effect.SubroutineGraded] to an out class — so this is the second of two independent denials and
 * neither is visible as an absence. Round-state is publicly observable (D-068): the room already
 * knows who is out.
 */
private fun returned(
    state: GameState,
    event: Event.SubroutineReturned,
): Reduction<GameState, Effect> {
    val open = state.openSubroutineFor(event.actor)
    val accepted = open != null && !state.isOut(event.actor) && grades(open, event.marker, event.entered)

    // The Insider's fake is graded identically and banks nothing. This is the ONLY place in the
    // rules where a role changes an outcome, and it changes state rather than shape.
    val banks = accepted && state.roleOf(event.actor) == Role.Resident
    val remaining = (state.systemIntegrity - 1).coerceAtLeast(0)

    val spent = if (open == null) state else state.withOpenSubroutine(open.spent())
    // An accepted entry marks its line of the work order done, which is what unblocks whatever was
    // chained behind it (D-114, D-123). A rejected one leaves the line standing: D-110's re-scan
    // is the only way back to ready, and there is no RETRY and must never be one.
    val advanced = if (open == null || !accepted) spent else {
        spent.workOrderFor(event.actor)
            ?.let { spent.withWorkOrder(it.withCompleted(open.entry)) }
            ?: spent
    }
    val banked = if (banks) advanced.copy(systemIntegrity = remaining) else advanced

    // **The hand-over closes the performance window** (D-111). The place is the one the house
    // recorded when this seat scanned, never the marker on the event: the event's marker is a
    // client's claim about where it is standing, and `open.accepts` is already the only thing
    // entitled to an opinion about that.
    val where = state.presenceFor(event.actor)?.at
    val next = banked.withPresence(Presence(event.actor, where, open = false))

    // All three constructed once, outside every branch above. Deliberately not inside them.
    //
    // The order goes back on EVERY return, not only on the ones that changed it. Sending it only
    // when something moved would make its presence a second verdict -- one the client could read
    // without being told, arriving beside the real one -- which is rule 1's forbidden shape wearing
    // an optimisation's clothes.
    val verdict = Effect.SubroutineGraded(seat = event.actor, accepted = accepted)
    val order = Effect.WorkOrderIssued(
        seat = event.actor,
        lines = next.workOrderFor(event.actor)?.asLines(next.destinations) ?: emptyList(),
    )
    val closed = Effect.PresenceChanged(event.actor, where, open = false)
    return Reduction(
        next,
        if (banks) {
            // **Both, and only when the meter really moved.** The count is the ledger's own number
            // and the percentage is the bar; both reach players outside the system and neither
            // reaches a living one, because the living are told a number at a meeting and not
            // between them. Emitting either on a return that banked nothing would say *somebody
            // returned something that did not score*, which is a per-return count nobody designed.
            listOf(
                verdict,
                order,
                closed,
                Effect.SubroutineProgressed(remaining),
                Effect.SystemIntegrityLive(
                    Balance.integrityPercent(remaining, state.seats.size),
                ),
            )
        } else {
            listOf(verdict, order, closed)
        },
    )
}

private fun sameSeat(a: Seat, b: Seat) = a.index == b.index

// =================================================================================================
// The meeting.
//
// One lifecycle, seven events, and every transition below is the HOUSE moving everybody at once.
// Not one of them is a client-side rule and not one of them can be: closing the check-in gate means
// counting phones, ending the talk early means knowing every hand is up, and reading the ballot
// means holding every ballot. A phone cannot count phones (D-134's E8-2, which recorded all four
// of these as authority pushes so that the missing edges would not later read as an omission).
// =================================================================================================

/**
 * Run [body] against the meeting in progress.
 *
 * **Not a guard, and it must not become one.** By the time a meeting event reaches [reduce] the
 * admission gate has already refused every case where there is no meeting or the phase is wrong —
 * that is what [RefusalReason.WrongMeetingPhase] is for. This exists because the gate's knowledge
 * cannot be expressed in the type, and the alternative spelling is `?: return Reduction(state,
 * emptyList())`, which is rule 1's forbidden shape sitting in the file rule 1 is about, waiting for
 * somebody to route a real event through it.
 */
private inline fun withMeeting(
    state: GameState,
    body: (Meeting) -> Reduction<GameState, Effect>,
): Reduction<GameState, Effect> {
    val meeting = state.meeting ?: return Reduction(state, emptyList())
    return body(meeting)
}

/**
 * **A meeting was called** (D-121, D-134).
 *
 * The caller is already checked in — their scan of the meeting card *is* their check-in, so
 * [Meeting.called] counts them and D-104's gate needs nothing new. The remote half of D-121, a
 * reported Revoke, arrives here identically; the two triggers differ only at the admission gate,
 * where an Egress makes the card inert and leaves the report alone (D-133).
 *
 * **The living are rung and the out are told to stand up**, which is D-134's correction of *a
 * screen with nothing on it, waiting*. Two effects rather than one with a flag: they carry
 * different things to audiences the allowlist separates on the round-state axis.
 *
 * **The long haptic is D-135's, and it is on the round-state axis and never on role.** An incoming
 * phone call is one of the five; so is STAND AND WALK IN for a **newly** Revoked player. The
 * couch — everyone who was already out before this meeting — is called in with an ordinary buzz.
 */
private fun meetingCalled(
    state: GameState,
    event: Event.MeetingCalled,
): Reduction<GameState, Effect> {
    val meeting = Meeting.called(event.caller, event.trigger)
    val ring = Effect.MeetingOpened(
        caller = event.caller,
        trigger = event.trigger,
        haptic = Haptic.Long,
    )
    // **The caller's own phone, told which of the ringing phones it is** (E-R1-1, ruled
    // 2026-08-23). The ring above carries `caller` to everybody and no client can use it, because a
    // phone is never told which seat it is; this is the house saying so by addressing one socket.
    //
    // **Emitted unconditionally and after the ring**, both of which matter. Unconditionally,
    // because every meeting has exactly one caller and a message whose presence was conditional
    // would make its absence a fact somebody could read. After, because the ring is what carries
    // the caller's NAME and clears the phone's jam -- a caller pushed to its own screen first would
    // arrive there with a header the next message was still on its way to fill.
    val calling = Effect.MeetingCalling(event.caller)
    val walkIn = state.outSeats.map { seat ->
        Effect.StandAndWalkIn(
            seat = seat,
            haptic = if (state.newlyRevoked.any { it.index == seat.index }) {
                Haptic.Long
            } else {
                Haptic.Short
            },
        )
    }
    // **D-133's third rule, and it is the one that matters.** The only meeting that can be called
    // during an Egress is a reported Revoke -- the card is inert, refused at the gate -- and that
    // meeting PAUSES the countdown. It never resets it: a reset would make the report a free
    // Egress cancellation and every Egress would end the same way, where a pause makes it a
    // decision about spending time.
    val held = state.egress?.pausedAt(event.at)
    val stopped = held?.let {
        listOf(Effect.EgressHeld(it.remainingAt(event.at), running = false, haptic = Haptic.Short))
    } ?: emptyList()

    // **The shift boundary, which is the only moment the living are told the meter**
    // (`gdd.md:192`, `:1002`). A shift runs from lights-out to the first meeting and a meeting ends
    // the shift it interrupts, so the meeting being called IS the boundary — and it is the moment
    // rather than the phase because the number belongs to the shift that just finished, not to the
    // talk that is about to start.
    //
    // Emitted unconditionally, whether or not the meter moved during that shift. A batch sent only
    // when something changed would make its presence the news: a meeting with no meter push on it
    // would tell the room that nobody banked anything all shift, which is a report on the house's
    // work nobody asked the app to make. Constructed once, outside every branch above.
    val batched = Effect.SystemIntegrityBatched(
        Balance.integrityPercent(state.systemIntegrity, state.seats.size),
    )

    // **The Access pool stops with the shift** (the Shift ruling, `GameState.accessPausedAt`). A
    // shift is the work interval between meetings and a meeting ends the shift it interrupts
    // (D-170), so the moment the party is called in is the moment Tier 2 stops recharging — and it
    // picks up where it stopped when the lights go out, which is `lightsOut`'s half.
    //
    // **A second call cannot move the mark**, for `Egress.pausedAt`'s reason: one meeting at a time
    // (`RefusalReason.MeetingAlreadyRunning`), and a pause that reset its own mark would be the one
    // shape that could silently lengthen a cooldown.
    //
    // **Nothing is emitted about it.** A cooldown reaching the house at large would be an Insider
    // announced by a timer; the actor's own tile is drawn from the actor's own input echo, and a
    // phone that walked into a meeting knows it walked into a meeting.
    val poolHeld = state.accessPausedAt ?: event.at

    return Reduction(
        state.copy(
            meeting = meeting,
            egress = held,
            accessPausedAt = poolHeld,
            // **A meeting call ends every running ability effect — D-185, closing T1·9 and T2·9.**
            //
            // **The moment is the call, not the end.** A call snaps every lamp in the house to full
            // white and puts every living player in one room, which is the moment an isolated marker
            // stops being an attack and becomes stale machinery ticking in an empty hallway. An
            // effect whose whole function is to shape movement through a dark house has nothing left
            // to do once nobody is moving through it — and one that outlived the call would land its
            // remaining seconds on the NEXT shift, bought at the price of the shift it was fired in.
            // **Nobody could tell that from a fresh fire, which is the real objection: an effect
            // that crosses a meeting is an effect with no traceable cause.**
            //
            // **Cleared rather than paused, and the Egress is the one exception.** A paused
            // isolation would come back after the meeting pointed at a house that has moved on;
            // the Egress survives because it is a win condition with a countdown the whole room is
            // looking at, and it is paused rather than cleared for D-133's reason — a report that
            // cancelled an Egress would make every Egress end the same way.
            //
            // **Nothing is emitted about either.** A card going dark is discovered by walking to
            // it (`isolateFired`), so a card coming back is discovered the same way, and there is no
            // message whose arrival could report that an ability had been running. **A jam needs no
            // message either, and that is the call rather than an omission**: page 2 does not exist
            // during a meeting for any seat of any role (D-183), the Terminal is never readable at
            // one (`gdd.md:470`), and every living phone is being pushed the meeting in the same
            // frame — so there is no surface left showing noise for a message to clear.
            isolated = emptyList(),
            staticUntil = null,
        ),
        listOf(ring, calling) + walkIn + gateCount(state, meeting) + batched + stopped,
    )
}

/**
 * **I'M HERE, and the gate that closes on it** (D-104).
 *
 * The talk does not start until every living player *and* every out player has checked in at the
 * meeting area. The accepted risk was named when it was decided: a player whose phone has failed
 * stalls the gate until they are brought back or the room resolves it socially. Presence over
 * proceed-without — *a meeting that starts while someone is still walking is a worse game than a
 * meeting that waits.*
 *
 * The count is constructed once and emitted in every case, including a second check-in from a seat
 * that is already standing there. What it says is a number and never a list: the ledger reports
 * anonymous counts and nothing finer (`gdd.md:294`).
 */
private fun checkedIn(state: GameState, event: Event.MeetingCheckedIn): Reduction<GameState, Effect> =
    withMeeting(state) { meeting ->
        val next = if (meeting.hasCheckedIn(event.seat)) {
            meeting
        } else {
            meeting.copy(checkedIn = (meeting.checkedIn + event.seat).sortedBy { it.index })
        }

        // Constructed once, outside the branch below.
        val counted = gateCount(state, next)
        val everybody = state.seats.isNotEmpty() && present(state, next) == state.seats.size
        if (!everybody) return@withMeeting Reduction(state.copy(meeting = next), listOf(counted))

        val talking = next.copy(phase = MeetingPhase.Discussion)
        Reduction(
            state.copy(meeting = talking),
            listOf(
                counted,
                Effect.MeetingPhaseOpened(
                    MeetingPhase.Discussion,
                    voteWindowSeconds = null,
                    haptic = Haptic.Short,
                ),
            ),
        )
    }

/**
 * **READY TO VOTE — one hand, and a unanimous one ends the talk.**
 *
 * Unanimous **among the living**, who are the only players holding the control: a player outside
 * the system watches the discussion and vote timers and the live vote (D-134) and has no hand to
 * put up. Counted against the same number the count is drawn against, so the bar on the screen and
 * the rule that ends the talk cannot disagree about who is in the room.
 */
private fun readyDeclared(
    state: GameState,
    event: Event.ReadyToVoteDeclared,
): Reduction<GameState, Effect> = withMeeting(state) { meeting ->
    val next = if (meeting.hasSaidReady(event.seat)) {
        meeting
    } else {
        meeting.copy(ready = (meeting.ready + event.seat).sortedBy { it.index })
    }

    val living = state.livingSeats
    val hands = living.count { next.hasSaidReady(it) }
    // Constructed once, outside the branch below.
    val counted = Effect.ReadyProgressed(hands, living.size)
    if (living.isEmpty() || hands < living.size) {
        return@withMeeting Reduction(state.copy(meeting = next), listOf(counted))
    }

    val opened = openVote(state.copy(meeting = next), next)
    Reduction(opened.state, listOf(counted) + opened.effects)
}

/**
 * The ballot opens: one row per living seat, in seat order.
 *
 * **The out are not given a row and cannot be.** Ghosts cast nothing (`gdd.md:417`) — not for
 * fairness but because a vote is a communication channel, and a player who knows who Revoked them
 * and can vote leaks that through the ballot. The tally leaks even when the vote is secret, and
 * several ghosts voting together are a beacon.
 */
private fun openVote(state: GameState, meeting: Meeting): Reduction<GameState, Effect> {
    val voting = meeting.copy(
        phase = MeetingPhase.Vote,
        ballots = state.livingSeats.map { Ballot(voter = it, selection = null, locked = false) },
    )
    return Reduction(
        state.copy(meeting = voting),
        listOf(
            // **The ballot's length rides the push that opens it** (D-117). The host set it in the
            // lobby, arming locked it, and this is the one read of it in the game — so every phone
            // in the house draws one clock from one number, and a client that ran the design's
            // default while the house ran the host's would be six phones disagreeing about when
            // the room stops voting.
            Effect.MeetingPhaseOpened(
                MeetingPhase.Vote,
                voteWindowSeconds = state.voteWindowSeconds,
                haptic = Haptic.Short,
            ),
            Effect.VoteProgressed(voting.lockedCount, voting.ballots.size),
        ),
    )
}

/**
 * **A selection tap, and the refusal that must not be a silence** (D-117, rule 1).
 *
 * Every tap transmits live, because the stream is what a player outside the system watches happen.
 * After READY the ballot is locked and a later tap from that seat is **refused** — and the whole
 * weight of this function is in what refused means here. It does **not** mean nothing is emitted:
 * the house re-asserts, sending back the selection it actually holds, and the two effects below
 * are constructed once, outside every branch, exactly as the cooldown is in [contact].
 *
 * Written the tempting way — *if the ballot is locked, return no effects* — the player is left
 * looking at a row they lit themselves that the house has never heard of, and the absence of the
 * answer is the answer.
 *
 * **A seat with no ballot is handled by the same shape rather than by a branch**: it produces the
 * same two effects with a null selection, is addressed only to itself, and the allowlist declines
 * it if that seat is outside the system.
 */
private fun voteSelected(state: GameState, event: Event.VoteSelected): Reduction<GameState, Effect> =
    withMeeting(state) { meeting ->
        val ballot = meeting.ballotFor(event.voter)
        val takes = ballot != null && !ballot.locked
        val held = if (takes) event.target else ballot?.selection

        val next = if (ballot != null && takes) {
            meeting.withBallot(ballot.copy(selection = event.target))
        } else {
            meeting
        }

        // Both constructed once, outside the branch above. Deliberately not inside it.
        Reduction(
            state.copy(meeting = next),
            listOf(
                Effect.VoteHeld(event.voter, held, locked = ballot?.locked == true),
                Effect.VoteSelectionShown(event.voter, held),
            ),
        )
    }

/**
 * **READY: the selection becomes the vote, irrevocably** (D-117).
 *
 * It converts what is already selected and carries no target of its own, which is what makes the
 * live stream load-bearing rather than decorative. A second press changes nothing and still comes
 * back with the same two effects — same shape, same numbers.
 *
 * **Unanimous readiness closes the window early**, symmetric with the discussion's unanimous READY
 * TO VOTE: E8-1's second question, answered by its first — if the button is a readiness signal, it
 * behaves like the other readiness signal.
 */
private fun voteLocked(state: GameState, event: Event.VoteLocked): Reduction<GameState, Effect> =
    withMeeting(state) { meeting ->
        val ballot = meeting.ballotFor(event.voter)
        val next = if (ballot != null && !ballot.locked) {
            meeting.withBallot(ballot.copy(locked = true))
        } else {
            meeting
        }

        // Constructed once, outside the branch below.
        val answered = listOf(
            Effect.VoteHeld(
                seat = event.voter,
                selection = next.ballotFor(event.voter)?.selection,
                locked = next.ballotFor(event.voter)?.locked == true,
            ),
            Effect.VoteProgressed(next.lockedCount, next.ballots.size),
        )
        if (next.ballots.isEmpty() || next.lockedCount < next.ballots.size) {
            return@withMeeting Reduction(state.copy(meeting = next), answered)
        }

        val read = readTheBallot(state.copy(meeting = next), next)
        Reduction(read.state, answered + read.effects)
    }

/**
 * **The buzzer: auto-lock, then the tally** (D-117, D-075 as narrowed, `gdd.md:413`).
 *
 * *Whatever is selected when the clock ends locks itself.* A seat that selected nothing at all for
 * the whole window locks a null, which is a **Skip** — that is what D-117 narrowed D-075 to:
 * skip-by-silence now applies to a player who never chose, rather than to anyone who did not press
 * a button.
 *
 * **The same function serves the early close**, where every ballot is already locked and the lock
 * below is a no-op. One path rather than two means a window that closed early and a window that
 * ran out cannot produce different results from the same ballots.
 *
 * Most votes is Restrained and ties resolve to Skip — see [tallyOf], which holds the rule and the
 * reason the count is ordered.
 */
private fun readTheBallot(state: GameState, meeting: Meeting): Reduction<GameState, Effect> {
    val banked = meeting.copy(ballots = meeting.ballots.map { it.copy(locked = true) })
    val restrained = tallyOf(banked.ballots)
    val finished = banked.copy(phase = MeetingPhase.Tally, restrainPending = restrained)

    return Reduction(
        state.copy(meeting = finished),
        listOf(
            Effect.MeetingPhaseOpened(
                MeetingPhase.Tally,
                voteWindowSeconds = null,
                haptic = Haptic.Short,
            ),
            Effect.VoteProgressed(finished.lockedCount, finished.ballots.size),
            // The outcome to everyone; the ballot with names against it to the out and nobody
            // else (D-075). Two kinds, because they are two disclosures.
            Effect.MeetingResult(restrained, Haptic.Short),
            Effect.MeetingResolved(
                restrained = restrained,
                attribution = finished.ballots.map { it.voter to it.selection },
            ),
        ),
    )
}

/**
 * **The takeover, at the halfway mark, to the losing seat and nobody else** (D-102, D-134's E1-1).
 *
 * *So they do not walk away when the countdown ends.* This is also the moment the seat actually
 * leaves the round: the group holds them, and the house deauthorises them moments later
 * (`gdd.md:1009`). Doing it at the buzzer instead would put an out client class on a phone whose
 * owner is still looking at the living's result screen, and the attribution list meant for the
 * couch would land there.
 *
 * **The one place a meeting effect can be absent, and it is safe for a stated reason.** When the
 * room Restrained nobody there is no seat to address, and the fact that nobody was Restrained has
 * already gone to every client on [Effect.MeetingResult]. The branch is on something already
 * published, which is the only kind of branch rule 1 permits in a client-visible path.
 */
private fun takeover(state: GameState, meeting: Meeting): Reduction<GameState, Effect> {
    val losing = meeting.restrainPending
        ?: return Reduction(state, emptyList())

    val held = if (state.isRestrained(losing)) {
        state.restrained
    } else {
        (state.restrained + losing).sortedBy { it.index }
    }
    return Reduction(
        state.copy(restrained = held),
        listOf(Effect.RestrainedTakeover(losing, Haptic.Long)),
    )
}

/**
 * **Lights out.** The meeting is over and the round resumes, with D-135's fifth long haptic.
 *
 * Clearing [GameState.newlyRevoked] is what makes D-134's split mean *since the last meeting*:
 * everybody who walked into this one is on the couch from here, and the next meeting rings them
 * the way it rings the rest of the couch.
 */
private fun lightsOut(state: GameState, event: Event.MeetingClosed): Reduction<GameState, Effect> {
    // The other half of D-133. The deadline moves forward by the length of the meeting and by
    // nothing else, so the remaining time across the meeting is EXACTLY what it was when the party
    // was called in -- see Egress.resumedAt, where that arithmetic lives.
    val running = state.egress?.resumedAt(event.at)
    val restarted = running?.let {
        listOf(Effect.EgressHeld(it.remainingAt(event.at), running = true, haptic = Haptic.Short))
    } ?: emptyList()

    // **A meeting hands every Insider the round's opening again — D-184**, which chooses at last
    // between `gdd.md:373`'s *pauses or resets*. It resets, to half.
    // Both per-seat clocks go back to half their normal duration off this tick, which is D-132's
    // arming state arriving a second time -- `openingCooldowns`, so the two are one arithmetic
    // rather than two that agree until somebody moves one.
    //
    // **This SUPERSEDES the Shift ruling's resume arithmetic and does not sit beside it.** What was
    // here moved every Access deadline forward by the length of the meeting so the remaining time
    // across it was unchanged; a reset to half overwrites that answer outright, so keeping both
    // would be two rules about one clock with the quieter one winning nothing. The **pause** is
    // untouched and still does its own job -- it is what `accessReady` reads *during* the meeting,
    // where a pool that carried on charging while the party stood in one room would hand a seat a
    // Tier-2 ability for walking into a room. What is gone is only the resume.
    val reopened = openingCooldowns(state.seats, event.at)

    // **The Egress's clock is house-wide and is frozen out of this while its Egress runs**, until
    // containment. A reset that reached it mid-crisis would hand the Insiders a second Egress to
    // fire on top of the one the Residents are still crossing the house to contain -- and D-131
    // already makes a running Egress outlive its Insiders, so the two together would put the round
    // beyond the Residents on a rule about cooldowns. With no Egress running it resets like the
    // rest, because then it is an ordinary cooldown and the ruling says every cooldown.
    val egressReopened = if (running != null) state.egressReadyAt else event.at + Balance.EGRESS_COOLDOWN / 2

    return Reduction(
        state.copy(
            meeting = null,
            newlyRevoked = emptyList(),
            egress = running,
            cooldowns = reopened,
            egressReadyAt = egressReopened,
            accessPausedAt = null,
        ),
        listOf(Effect.MeetingEnded(Haptic.Long)) + restarted,
    )
}

/** How many seated players are standing at the meeting area. An empty seat counts for nobody. */
private fun present(state: GameState, meeting: Meeting): Int =
    state.seats.count { meeting.hasCheckedIn(it) }

/** D-104's gate as the two numbers a screen draws it with, and never as a list of names. */
private fun gateCount(state: GameState, meeting: Meeting): Effect =
    Effect.CheckInProgressed(present(state, meeting), state.seats.size)

// =================================================================================================
// The Egress.
//
// Three events, and the shape of the whole system is the shape of `contact`'s: the actor's effect
// is constructed ONCE, outside every branch, and only the state transition moves. The house
// catching fire is world-observable and reaches everybody identically; what the person who fired
// it gets is a cooldown answer and nothing else (gdd.md:396).
// =================================================================================================

/**
 * Run [body] against the Egress in progress.
 *
 * **Not a guard, and it must not become one** — see [withMeeting], which this mirrors exactly. By
 * the time an Egress event reaches [reduce] the admission gate has already refused every case where
 * there is no Egress or it is paused; that is what [RefusalReason.EgressNotRunning] is for. This
 * exists because the gate's knowledge cannot be expressed in the type, and the alternative spelling
 * is rule 1's forbidden shape sitting in the file rule 1 is about.
 */
private inline fun withEgress(
    state: GameState,
    body: (Egress) -> Reduction<GameState, Effect>,
): Reduction<GameState, Effect> {
    val egress = state.egress ?: return Reduction(state, emptyList())
    return body(egress)
}

/**
 * **A Insider fired an Egress** (`gdd.md:349`, D-131, D-132, F-001).
 *
 * ### Rule 1's shape, and the branch is on the shared cooldown alone
 *
 * Every path through this function emits exactly one [Effect.AbilityFired] with
 * `cooldownStarted = true`, constructed once, outside the branch — exactly as [contact] does. What
 * differs is whether the house caught fire, and *that* is world-observable: an Egress is audible,
 * it dims every panel in the building (D-118), and it puts a countdown on every widget. There is
 * nothing here for an absence to say that the room is not already saying louder.
 *
 * ### The cooldown is one clock for the whole house
 *
 * Firing puts **every** Insider on it — the panel's own *SHARED WITH THE OTHER INSIDER* line, as
 * state — so a second Insider cannot follow the first. It began the round already running at half
 * (D-132), which is why the opening minutes cannot hold one, and no effect carries it: the actor's
 * phone knows what it pressed, and every phone in the house learns the shared clock restarted from
 * [Effect.EgressOpened], which they all receive.
 *
 * ### A second Egress cannot start on top of a first
 *
 * Not because it is refused — that would be a refusal about a running Egress, which is the gate's
 * job and is already there for the meeting card — but because the cooldown outlives the timer by
 * construction. The check is written anyway, and it is on state that is on every widget.
 *
 * ### The nodes arrive already chosen
 *
 * They ride the event, drawn above the rules by `egressFor` against a home the rules have never
 * held. What is checked here is the one thing the rules can check without geography: that the pair
 * is **two distinct markers**. A collapsed pair would be an Egress physically impossible to contain
 * that looked exactly like one that was not, and the Residents would lose to a shape they could
 * never see.
 */
private fun egressFired(state: GameState, event: Event.EgressFired): Reduction<GameState, Effect> {
    val ready = event.at >= state.egressReadyAt && state.egress == null
    val started = if (!ready) {
        null
    } else {
        Egress.fired(event.at, event.type, event.nodes, Balance.EGRESS_TIMER)
            .takeIf { it.namesTwoNodes }
    }

    val next = if (started == null) {
        state
    } else {
        state.copy(egress = started, egressReadyAt = event.at + Balance.EGRESS_COOLDOWN)
    }

    // Constructed once, outside the branch above. Deliberately not inside it: the actor's device
    // stays inert whatever happened, and what it is told is that the ability fired and what its own
    // clock now says -- the same two facts a botched Revoke stalk is told.
    //
    // **It said `true` unconditionally and that was a lie** (T1·8 / T2·7, flagged twice under the
    // freeze and fixed now that the freeze is lifted for it). `next` restarts `egressReadyAt` when
    // and only when `started != null`, so an Egress fired into a running cooldown told that phone it
    // had spent one it did not -- and the actor's own bar, which is drawn from this flag, was wrong
    // for three minutes.
    //
    // **Reading the flag off the state transition rather than off `ready` is the point.** They are
    // not the same condition: a pair the house drew collapsed into one room is `ready` and starts
    // nothing, and a flag that said `ready` would tell the phone about a draw rather than about its
    // own clock. This is `surgeFired`'s arrangement — the actor is the only recipient, the row
    // permits the kind to `Insider/Live`, and what it says is *your own clock*, which is input echo
    // and not a game answer.
    val answered = Effect.AbilityFired(actor = event.actor, cooldownStarted = started != null)
    val alarm = started?.let { egress ->
        // Per seat, once each, because this is one of D-118's two dimming events and a dim that
        // reached fewer than everybody would be a beacon (D-076). Grouped as one run rather than
        // interleaved, exactly as arming's lamps are, so a seat going missing is a gap in a run of
        // identical lines.
        state.seats.map {
            Effect.EgressOpened(
                seat = it,
                type = egress.type,
                nodes = egress.nodes,
                remaining = egress.remainingAt(event.at),
                // D-135's first of five. The Egress is named at the head of that closed set.
                haptic = Haptic.Long,
            )
        }
    } ?: emptyList()

    // **The two nodes leave every Insider's Isolate list at the instant they are named to the
    // house** (D-150). Built from `next`, so the exclusion is read off the Egress that just started
    // rather than off the one that was not running a line ago — and emitted only when an Egress
    // actually started, because a list re-sent on a fire that did nothing would be a message whose
    // presence reported the state of the shared cooldown to a phone that was told it another way.
    val relisted = if (started == null) emptyList() else isolateOffers(next)

    return Reduction(next, listOf(answered) + alarm + relisted)
}

/**
 * **One participant's four taps, graded — and the answer has one shape** (`gdd.md:355`, rule 1).
 *
 * ### Where the player is, is the house's own fact
 *
 * The event carries no node. The house reads it off the presence plane, which is what it recorded
 * when this seat **scanned** the node's card — *a performer's own scan is knowledge* (D-136). A
 * phone naming its own node would be the cheapest cheat in the game: sit on the couch, claim the
 * Landing, contain an Egress from a chair. [returned] reads the place the same way and for the same
 * reason.
 *
 * ### Five ways to fail and one shape for all of them
 *
 * Outside the system, not standing at a node, standing at a node but serving a lockout, standing at
 * a node with a beat that missed, standing at a node with a good beat and nobody at the other one:
 * every one of them produces [Effect.SyncPulseAnswered] addressed to this seat, with `held` saying
 * only whether the house is now waiting on their behalf. Written the tempting way — say nothing
 * unless the beat was good — the absence would be readable, and what it would report is a claim
 * about the presence plane delivered to a living phone.
 *
 * ### Containment says nothing about who contained it
 *
 * *Nobody learns anything about anybody* (`gdd.md:987`). [Effect.EgressContained] carries no seats
 * and no node; the pair that did it get exactly what the rest of the house gets, one tick after
 * they get their own `held = true`.
 *
 * ### A missed beat costs a lockout and a locked seat is not re-graded
 *
 * The lockout is `gdd.md:359`'s 2–3 seconds and its whole job is that stalling at a node is
 * expensive. A seat already serving one is not graded again and its lockout is **not extended** —
 * an extension would turn a mistimed retry at the moment of expiry into a permanent exclusion, and
 * the thing being priced is spam, which a lockout that simply does nothing already prices.
 */
private fun pulseReturned(
    state: GameState,
    event: Event.SyncPulseReturned,
): Reduction<GameState, Effect> = withEgress(state) { egress ->
    val standing = state.presenceFor(event.actor)?.at
    val atNode = !state.isOut(event.actor) &&
        standing != null &&
        egress.isNode(standing)
    val locked = egress.lockedAt(event.actor, event.at)
    val good = atNode && !locked && onTheBeat(egress.firedAt, event.taps)

    val partner = if (!good || standing == null) {
        null
    } else {
        egress.partnerFor(event.actor, standing, event.at, Balance.SYNC_PULSE_PAIR_WINDOW)
    }

    val next = when {
        // Contained. The Egress is over and there is nothing left to hold offers for.
        partner != null -> state.copy(egress = null)
        // A good beat with nobody at the other node yet: the house holds it and waits.
        good && standing != null ->
            state.copy(
                egress = egress.holding(
                    PulseOffer(event.actor, standing, event.at),
                    Balance.SYNC_PULSE_PAIR_WINDOW,
                ),
            )
        // A beat that missed, from somebody actually standing at a node. That is what a lockout is
        // for. A seat that was never at a node has attempted nothing and pays nothing.
        atNode && !locked ->
            state.copy(
                egress = egress.lockingOut(event.actor, event.at + Balance.SYNC_PULSE_LOCKOUT),
            )
        else -> state
    }

    // Constructed once, outside the branch above. Deliberately not inside it.
    val answered = Effect.SyncPulseAnswered(seat = event.actor, held = good && partner == null)
    val contained = if (partner == null) {
        emptyList()
    } else {
        // No seats, no node, no attribution of any kind. The house says it stopped.
        //
        // **And the two nodes come back onto every Insider's Isolate list**, which is the other end
        // of D-150's exclusion: it lasts *for the duration of an Egress* and this is the duration
        // ending. Built from `next`, where the Egress has already been cleared, so the list is drawn
        // against the house as it now is. The other way an Egress ends is expiry, which ends the
        // round — there is nobody left to offer a list to.
        listOf(Effect.EgressContained(Haptic.Short)) + isolateOffers(next)
    }
    Reduction(next, listOf(answered) + contained)
}

/**
 * **Uncontained: the timer ran out and the Insiders win outright** (`gdd.md:361`, D-131).
 *
 * *A running Egress outlives its Insiders and must still be stopped* — so nothing here asks whether
 * any Insider is left in the round. Restraining the last one during an Egress does not end it: the
 * house does not stop what it was told to start.
 *
 * **The Egress is cleared here and the round is ended one layer up**, by [ending], on
 * `WinRoute.EgressUncontained`. That split is not tidiness: clearing the Egress is what takes the
 * countdown off every widget in the building, and it is the reason [outcomeOf] has to read the
 * event rather than the state — after this line there is nothing left in [GameState] that can tell
 * an expiry from a containment.
 */
private fun egressExpired(state: GameState): Reduction<GameState, Effect> = Reduction(
    state.copy(egress = null),
    listOf(Effect.EgressSucceeded(Haptic.Long)),
)

/**
 * The luminance every phone is set to at arming. Identical for every seat, deliberately.
 *
 * F-005's other half is still not built: orphaned Subroutines — from a revoked player, or from a
 * chain whose blocker can no longer be completed — are meant to be silently auto-satisfied so the
 * bar stays winnable. The meter's own arithmetic lives in [Balance] now, where the lobby and the
 * reachability tests can read the same numbers the rules do.
 */
private const val LAMP_DIM = 1

/**
 * **The Surge's two luminances — full, then black** (`gdd.md:313`).
 *
 * Here rather than in [Balance] because they are not balance: they are the light the rules author
 * for one gesture, and [LAMP_DIM] is the only other value in the game that is the same kind of
 * thing. Rule 5 is why they exist at all — *light is game state, and no local animation the core did
 * not emit* — so the slam is two numbers this file chose, carried on the effect, and the device
 * performs those and returns to whatever [Effect.LampSet] it was last sent.
 *
 * ⚠️ **How long each half lasts is unruled and is not here**, because `core` has no clock to say it
 * with. It is the platform's, and it is flagged.
 */
private const val LAMP_FULL = 100
private const val LAMP_BLACK = 0
