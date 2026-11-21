package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Role
import home.someoneshome.model.Seat

/**
 * What the admission gate decided about one event.
 *
 * Two cases and no third. A refusal carries no effects and no state: refusing is not a quiet
 * version of reducing.
 */
sealed interface Admission {
    data class Admitted(val reduction: Reduction<GameState, Effect>) : Admission
    data class Refused(val reason: RefusalReason) : Admission
}

/**
 * **D-066 — the admission gate. Above the rules, never inside them.**
 *
 * The authority refuses events that arrive outside a round before [reduce] sees them.
 *
 * ### Why this is not a branch in `reduce`
 *
 * The obvious fix is banned. `if (!state.armed) return Reduction(state, emptyList())` is rule 1's
 * forbidden shape — an event that produces no effect where an effect was expected *is* the signal,
 * and the silence is the message. Putting the check here instead means [reduce] stays total, never
 * learns the flag exists, and the branch is **provably outside every client-visible path** rather
 * than merely reviewed as being outside one.
 *
 * ### Why it existed as a bug at all
 *
 * `GameState.armed` had two writers and no readers, so the rules processed every event identically
 * whether or not a round existed. Feeding `ContactMade`, `SubroutineReturned` and `MeetingCalled`
 * to `GameState.EMPTY` emitted `AbilityFired`, a verdict and `MeetingOpened`.
 *
 * **Severity, stated honestly: this is a replay bug, not a leak.** Arming *constructs* a fresh
 * state, so pre-arm effects reached clients while the state that would explain them was thrown
 * away — the recording's effect rows and state rows disagreed about a round that had not begun,
 * and the recording is the only debugging instrument this game has. That is why a refusal must be
 * **recorded**: an unrecorded refusal is an invisible drop, and the recording would then disagree
 * with reality in the other direction.
 *
 * ### `RoundArmed` is always admitted
 *
 * It is the event that creates the round. Nothing else gets in without one.
 *
 * ### The meeting's three refusals are here for the same reason the first two are
 *
 * D-133's *no meeting is called during an Egress*, D-121's one-meeting-at-a-time, and *this event
 * belongs to a phase the meeting is not at* are all conditions the whole house can see. Written
 * inside [reduce] each of them would be `if (…) return Reduction(state, emptyList())` — rule 1's
 * forbidden shape, and the one that looks most like hygiene. Written here the branch is provably
 * outside every client-visible path, and the refusal is **recorded**, which an early return never
 * is.
 *
 * **What is deliberately NOT here:** every refusal that would name one seat. *You are revoked*,
 * *you already locked your vote*, *that target is already out* — those are alignment leaks in
 * refusal clothing, and they live in the rules as an effect with the same shape as a success. See
 * [RefusalReason] itself, which draws the line and says where it is.
 *
 * ### The one branch that DOES ask who the actor is, and why it is not the exception it looks like
 *
 * *This ability is not yours to fire* names a seat and names the most alignment-revealing fact in
 * the game. It is here rather than in the rules because **no honest client can produce the intent**
 * — the same argument the Tier-2-at-a-meeting branch makes and the same one that makes it safe.
 * The three refusals above that were kept out of this file are all reachable by a real thumb on a
 * real screen; this one is reachable only by a frame nobody's phone would send. See [firedBy].
 */
fun admit(state: GameState, event: Event): Admission = when {
    event is Event.RoundArmed -> Admission.Admitted(reduce(state, event))
    state.ended -> Admission.Refused(RefusalReason.RoundAlreadyEnded)
    !state.armed -> Admission.Refused(RefusalReason.RoundNotArmed)

    // **The house checks who is firing — E-R2-1, and it is checked here for D-066's reason.**
    //
    // Nothing in `reduce` asked. `roleOf` and `isOut` appear in `Rules.kt` three times — the scan,
    // the entry grading and the Sync Pulse — and **no ability handler read either one**, so a plain
    // Resident on the couch could press the Egress tile and the house would draw one. It did, twice,
    // in a five-seat rehearsal: thirty-nine presses from five seats, thirty-nine draws. Not a leak
    // and worse than most leaks — nothing came back to the firing seat, and what they could do was
    // spend the house's cooldown or end a round they were not in.
    //
    // **Third in the chain on purpose.** After the round exists and before every reason about the
    // shape of the moment: *this ability is not yours* is true whatever phase the house is at, and a
    // forged fire during a meeting should be recorded as the forgery it is rather than as a meeting
    // being busy.
    //
    // **Above the rules rather than inside them.** Written in the handler it would be
    // `if (!insider) return Reduction(state, emptyList())` — rule 1's forbidden shape, in the file
    // rule 1 is about, on the one branch whose absence would be the loudest signal in the game. Here
    // the refusal costs nothing (no cooldown, no pool, no draw) and it is **recorded**, which an
    // early return never is (D-066).
    //
    // **It is not a client-visible path.** D-142 gives a Resident's page-2 controls no pointer input
    // at all and an out seat has no springboard to swipe, so no honest client can produce any of
    // these five intents — which is the Tier-2-at-a-meeting branch's argument below, arriving at the
    // one gate that needed it more. What this refuses is a forged or bench-built frame, and it is
    // refused, recorded, and surfaced to nobody.
    firedBy(event)?.let { actor -> state.roleOf(actor) != Role.Insider || state.isOut(actor) }
        == true -> Admission.Refused(RefusalReason.AbilityNotTheirs)

    // **R3: only a living player can report** (the owner's 2026-11-15 ruling, D-217). A report is
    // the radio's conclusion that a living phone heard a Revoked one, and a phone that is out does
    // not scan (D-008) and uploads nothing heard (R8) -- so no honest phone produces this, and it is
    // `AbilityNotTheirs`'s argument rather than a client-visible path. Above the meeting checks for
    // that one's reason too: a forged report is recorded as the forgery it is, not as a meeting
    // being busy.
    event is Event.MeetingCalled &&
        event.trigger is MeetingTrigger.RevokeReported &&
        state.isOut(event.caller) -> Admission.Refused(RefusalReason.ReporterOut)

    // **Ghosts cannot act, and calling the house in is acting** (gdd M12, `gdd.md:1052`; review r4
    // F5, D-279). R3 above refused the report from an out caller and left the meeting card's call
    // open, so a forged call from the couch summoned the house and rang every living phone with an
    // out seat's name on it. No honest phone produces it -- a phone that is out does not scan
    // (D-008) -- which is `ReporterOut`'s and `VoterOut`'s argument, so it is refused here,
    // recorded, and answered with nothing. Above the meeting checks for R3's reason: a forged call
    // is recorded as the forgery it is, not as an Egress or a meeting.
    event is Event.MeetingCalled &&
        event.trigger is MeetingTrigger.MeetingCard &&
        state.isOut(event.caller) -> Admission.Refused(RefusalReason.CallerOut)

    // **Ghosts cast nothing** (`gdd.md:417`; H8 of the 2026-11-17 hunt, D-252). An out seat has no
    // ballot, and a selection or a READY from one was answered in the rules by the same two effects
    // a refused tap gets -- one of them a live selection, which goes to every seat outside the
    // system. So a forged tap from the couch put a SKIP on every ghost's screen and VOTES CAST 5 OF
    // 5 over a ballot of four. No honest phone produces it: the couch has no ballot to tap (D-134),
    // which is `ReporterOut`'s argument, so it is refused here, recorded, and answered with nothing.
    (event is Event.VoteSelected && state.isOut(event.voter)) ||
        (event is Event.VoteLocked && state.isOut(event.voter)) ->
        Admission.Refused(RefusalReason.VoterOut)

    // **S4: a Revoked player is reported once** (the owner's 2026-11-15 ruling, D-219). A couch
    // phone keeps advertising (D-008), so a living phone held against it after its meeting is a
    // second report of a seat the whole house was already rung about. Refused at the gate, as R3
    // is, so it is recorded rather than dropped -- and, being the radio's, answered with nothing
    // (R4), so no client's stream differs from a touch that was never made. Above the meeting checks
    // so that a repeat during its own meeting is recorded as the repeat it is.
    event is Event.MeetingCalled &&
        (event.trigger as? MeetingTrigger.RevokeReported)?.let { state.wasReported(it.reported) } == true ->
        Admission.Refused(RefusalReason.AlreadyReported)

    // D-133. The meeting card is inert while the house is on fire; the Revoke report is NOT --
    // D-121's one exception, unchanged. Split on the trigger and on nothing else.
    event is Event.MeetingCalled &&
        event.trigger is MeetingTrigger.MeetingCard &&
        state.egressRunning -> Admission.Refused(RefusalReason.EgressRunning)

    // A second call would reset the meeting everybody is already standing at.
    event is Event.MeetingCalled && state.meeting != null ->
        Admission.Refused(RefusalReason.MeetingAlreadyRunning)

    // **Tier 2 is refused for the duration of a meeting — D-183, closing T1·7 / T2·8.**
    //
    // **What settles it is that there is no surface to fire from.** Everyone alive is on the meeting
    // view and everyone out is on the ghost view; springboard page 2 does not exist during a meeting
    // for any seat of any role. So no honest client can produce the intent, and this branch is
    // **not a client-visible path at all** — it is the boundary doing what it is for, against a
    // forged client. Rule 1 is about an effect a player should have seen and did not, and nobody can
    // see an effect on a screen nobody is standing on.
    //
    // That is stronger than the reading both earlier units left it on — *the pool pauses, so nothing
    // turns on the gate*. A pause makes the fire pointless; it does not make it impossible, and
    // *pointless but permitted* is the shape that gets found by whoever builds the next client and
    // wonders why the tile is missing. **The answer is now the same on both sides of the wire.**
    //
    // **The pause is gone and this branch is why it could go** (the owner's 2026-08-24 ruling). With
    // no fire admitted for the whole of a meeting, nothing can ask whether the pool is ready while
    // it would have been frozen — so `GameState.accessPausedAt` could not change any answer, and
    // state that cannot change an answer is state a later reader will one day believe.
    //
    // **It costs nothing**, which is the whole of what a refusal at the gate means: the pool is not
    // spent, because the event never reaches the handler that would spend it.
    isTierTwo(event) && state.meeting != null ->
        Admission.Refused(RefusalReason.MeetingAlreadyRunning)

    // **The Egress is blocked during meetings** (`gdd.md:386`; review r4 F1, D-279). It is not
    // Tier 2 and it is refused for Tier 2's reason, D-183's: springboard page 2, where its tile
    // lives, does not exist during a meeting for any seat of any role. Nothing blocked it before
    // this branch -- [isTierTwo]'s KDoc said `gdd.md:373` already did, and no code did. The race is
    // honest: a press rides the next upload beat (D-263), a meeting can be admitted first, and the
    // Egress then started with no pause on it, ran through the check-in, the talk and the vote, and
    // expired in front of the whole house. D-133's pause is applied by the meeting's call, so an
    // Egress that starts after the call is one the pause never reaches. Refused, recorded, and
    // symmetric for every seat and role; it costs nothing, because the shared cooldown is spent
    // only by the handler this never reaches. Below the ownership check, so a forged fire is still
    // recorded as `AbilityNotTheirs`.
    event is Event.EgressFired && state.meeting != null ->
        Admission.Refused(RefusalReason.MeetingAlreadyRunning)

    // **R5: a touch during a meeting is not a contact** (the owner's 2026-11-15 ruling, D-217).
    // Everybody alive is standing in one room, and the gesture that Revokes and reports is a touch;
    // admitted, a Revoke armed before the call could land at the meeting itself, in front of the
    // whole house, and a room full of people standing close would be a room of contacts. The
    // decision log said nothing otherwise (checked: D-121, D-133, D-183, D-185, D-213). The refusal
    // is symmetric -- every seat, every role, both halves of every touch -- so it is the D-183 shape,
    // a condition the whole house is standing in, and it is recorded, which an early return is not.
    // A report is refused already, one branch up, as any second meeting is.
    event is Event.ContactMade && state.meeting != null ->
        Admission.Refused(RefusalReason.MeetingAlreadyRunning)

    // **Nothing is sensed at a meeting** (O4), for R5's reason one gesture over: everybody alive is
    // standing in one room and the presence plane is frozen, so a room count taken there would be a
    // count of the meeting. The house mints no `Sensed` while one runs; this refuses one that
    // arrives anyway (a replay, a forged frame), recorded, symmetric across every seat and role.
    event is Event.Sensed && state.meeting != null ->
        Admission.Refused(RefusalReason.MeetingAlreadyRunning)

    // Everyone in the house is on the same phase of the same meeting, so this tells a client
    // nothing it could not read off the room (D-068).
    phaseOf(event) != null && state.meeting?.phase != phaseOf(event) ->
        Admission.Refused(RefusalReason.WrongMeetingPhase)

    // The Egress's own three, and both halves are as public as the meeting's. A countdown on every
    // widget in the building is not something a phone tells you; neither is the whole party
    // standing in one room, which is what a PAUSED Egress means (D-133). Written here for
    // `withEgress`'s sake: the rules stay total, never learn the flag exists, and the refusal is
    // RECORDED, which an early return never is.
    //
    // **And no Egress is running while a meeting is, whatever its pause mark says** (D-133; review
    // r4 F1, D-279). A meeting pauses the countdown, so under D-133 nothing can expire and nobody can
    // pulse while the house is assembled. The EgressFired branch above is what keeps an unpaused
    // Egress out of a meeting; this is the second wall, so that an expiry minted against one that
    // slipped in anyway -- a replayed recording, a future path that starts one -- cannot end the
    // round in the middle of the vote. It refuses nothing an honest round produces.
    needsEgress(event) &&
        (state.egress == null || state.egress?.pausedAt != null || state.meeting != null) ->
        Admission.Refused(RefusalReason.EgressNotRunning)

    else -> Admission.Admitted(reduce(state, event))
}

/**
 * **The seat firing an ability, or null for an event that is not one being fired** (E-R2-1).
 *
 * **Exhaustive over [Event] on purpose**, for [phaseOf]'s reason and more sharply: a new event does
 * not compile until somebody decides whether it is an ability being fired, and the compile error is
 * the prompt. The alternative is a sixth ability added later that any socket in the house can fire,
 * discovered the way the first five were — by watching a Revoked Resident set the building alight.
 *
 * ### The five, and they are the whole of the Insider's kit
 *
 * The Revoke's arming (Tier 1) and the four Tier-2-and-up fires. Named by the seat that asked
 * rather than by the tier, because what is being checked is *whose ability this is* and the tiers
 * differ in cooldown rather than in ownership.
 *
 * ### [Event.ContactMade] is deliberately not one of them
 *
 * It is a physical act reported by hardware — `factOf` has no intent that makes one, and a phone
 * touched to a phone is a fact the house is told rather than a request it may refuse. It needs no
 * gate here because it can no longer do anything: a contact revokes only from a seat holding an
 * armed Revoke, and the only way into `revokeArms` is [Event.RevokeArmed], which this function
 * names. **Gating it would also be the wrong shape** — a report refused above the rules is a report
 * the recording never holds, and D-066's whole reason for recording refusals is that an unrecorded
 * drop is an invisible one.
 *
 * ### The Sync Pulse is not one either, and that is the same distinction
 *
 * [Event.SyncPulseReturned] is a Resident's four taps at a node during someone else's crisis — the
 * containment loop, not an ability. It is already gated on there being a running Egress, and its
 * every failure comes back as one shape for all five ways to miss.
 */
private fun firedBy(event: Event): Seat? = when (event) {
    is Event.RevokeArmed -> event.actor
    is Event.SurgeFired -> event.actor
    is Event.IsolateFired -> event.actor
    is Event.StaticFired -> event.actor
    is Event.EgressFired -> event.actor

    is Event.RoundArmed, is Event.MarkerScanned, is Event.SubroutineReturned,
    is Event.PerformanceEnded, is Event.ContactMade, is Event.RadioFaulted, is Event.MeetingCalled,
    is Event.MeetingCheckedIn, is Event.ReadyToVoteDeclared, is Event.DiscussionClosed,
    is Event.VoteSelected, is Event.VoteLocked, is Event.VoteWindowClosed,
    is Event.TallyHalfwayReached, is Event.MeetingClosed,
    is Event.SyncPulseReturned, is Event.EgressExpired, is Event.TerminalLeft,
    // The radio's conclusion, like ContactMade: a fact the house is told, never an ability.
    is Event.Sensed,
    // The house's own fact about its own air, never an ability.
    is Event.OffAirEnded,
    // The house's own fact about a permission switched off again (D-310), never an ability.
    is Event.HouseClosedOnPermission,
    -> null
}

/**
 * Which phase of a meeting an event belongs to, or null for an event that is not part of one.
 *
 * **Exhaustive over [Event] on purpose.** A new event does not compile until somebody decides
 * whether it is a meeting event, which is the same discipline the emit schema's `kindOf` uses: the
 * compile error is the prompt, and the alternative is a meeting event that silently belongs to
 * every phase.
 */
private fun phaseOf(event: Event): MeetingPhase? = when (event) {
    is Event.RoundArmed, is Event.MarkerScanned, is Event.SubroutineReturned,
    is Event.RevokeArmed, is Event.ContactMade, is Event.RadioFaulted, is Event.MeetingCalled -> null

    // **Not meeting events, and they are refused at a meeting by [isTierTwo] rather than by a phase
    // here.** T1·7 / T2·8 was unruled and is ruled now: Tier 2 may not be fired at a meeting at all.
    // The refusal is not expressible as a phase — *no phase* is what it means, and a member of
    // [MeetingPhase] returned here would say the opposite, that there is one phase these belong to.
    is Event.SurgeFired, is Event.IsolateFired, is Event.StaticFired -> null

    // Not meeting events. The Egress has its own gates above: the fire is refused for the whole of
    // a meeting, and the other two are refused while a meeting is holding the countdown -- which is
    // the same answer by a shorter route, and the one that stays true if the meeting's phases are
    // ever reshaped.
    is Event.EgressFired, is Event.SyncPulseReturned, is Event.EgressExpired -> null

    // Not a meeting event, and deliberately not refused during one either. A window that was open
    // when the house rang closes wherever the player is standing; a gate that declined the report
    // would leave the presence plane holding a window that never shut, and the one consumer it has
    // is an expiry (D-136).
    is Event.PerformanceEnded -> null

    // Not a meeting event, and not refused during one, for [Event.PerformanceEnded]'s reason: the
    // live view closes wherever the reader is when the house rings, and a phone whose connection
    // drops at a meeting has dropped all the same. The call has already freed the station, so at a
    // meeting this changes nothing -- which is the harmless direction.
    is Event.TerminalLeft -> null

    // Not a meeting event. Refused at a meeting outright, above, rather than by a phase here.
    is Event.Sensed -> null

    // Not a meeting event, and not refused at one: a host can lock during a meeting, and the
    // meeting's own pause already holds the Egress then, so the rules move nothing.
    is Event.OffAirEnded -> null

    // Not a meeting event, and not refused at one (D-310): a permission switched off during a
    // meeting, after the warning, closes the house as it would during a shift.
    is Event.HouseClosedOnPermission -> null

    is Event.MeetingCheckedIn -> MeetingPhase.CheckIn
    is Event.ReadyToVoteDeclared, is Event.DiscussionClosed -> MeetingPhase.Discussion
    is Event.VoteSelected, is Event.VoteLocked, is Event.VoteWindowClosed -> MeetingPhase.Vote
    is Event.TallyHalfwayReached, is Event.MeetingClosed -> MeetingPhase.Tally
}

/**
 * **Whether this event is a Tier-2 firing** — the owner's 2026-08-23 ruling, as a set.
 *
 * **Exhaustive over [Event] on purpose**, for [phaseOf]'s reason: a new event does not compile until
 * somebody decides whether it is Tier 2, and the compile error is the prompt. The alternative is an
 * ability added later that is quietly firable from the assembly because nobody thought about it.
 *
 * It is named for the tier rather than listing three events at the call site because that is what
 * the ruling is about — *Tier-2 fires are refused during meetings* — and because a fourth Tier-2
 * ability should join the rule by being added here rather than by somebody remembering to.
 *
 * **[Event.RevokeArmed] is deliberately not one of them.** Tier 1 arms in place (D-142) and the
 * ruling names Tier 2; a Revoke armed at a meeting is a botched stalk that costs its own cooldown,
 * which is the ordinary case rather than a rule about meetings. [Event.EgressFired] is not one
 * either, because it is not Tier 2 — **but it is refused during a meeting all the same**, by its own
 * branch in [admit] (`gdd.md:386`, *blocked during meetings*; D-279). This KDoc used to say
 * `gdd.md:373` blocked it already. Nothing did, and an Egress pressed a beat before a meeting was
 * called started unpaused inside it (review r4 F1).
 */
private fun isTierTwo(event: Event): Boolean = when (event) {
    is Event.SurgeFired, is Event.IsolateFired, is Event.StaticFired -> true

    is Event.RoundArmed, is Event.MarkerScanned, is Event.SubroutineReturned,
    is Event.PerformanceEnded, is Event.RevokeArmed, is Event.ContactMade, is Event.RadioFaulted,
    is Event.MeetingCalled, is Event.MeetingCheckedIn, is Event.ReadyToVoteDeclared,
    is Event.DiscussionClosed, is Event.VoteSelected, is Event.VoteLocked,
    is Event.VoteWindowClosed, is Event.TallyHalfwayReached, is Event.MeetingClosed,
    is Event.EgressFired, is Event.SyncPulseReturned, is Event.EgressExpired,
    is Event.TerminalLeft, is Event.Sensed, is Event.OffAirEnded, is Event.HouseClosedOnPermission,
    -> false
}

/**
 * Whether this event needs a **running, unpaused** Egress to mean anything.
 *
 * **Exhaustive over [Event] on purpose**, for [phaseOf]'s reason: a new event does not compile
 * until somebody decides whether it belongs to an Egress, and the compile error is the prompt. The
 * alternative is an Egress event that silently belongs to every state of the house.
 *
 * [Event.EgressFired] is deliberately **not** one of them. It is the event that *starts* one, and a
 * gate that required an Egress to fire an Egress would refuse the only thing that can create one —
 * `RoundArmed` sits outside this file's first check for exactly the same reason.
 */
private fun needsEgress(event: Event): Boolean = when (event) {
    is Event.SyncPulseReturned, is Event.EgressExpired -> true

    is Event.RoundArmed, is Event.MarkerScanned, is Event.SubroutineReturned,
    is Event.PerformanceEnded, is Event.RevokeArmed, is Event.ContactMade, is Event.RadioFaulted,
    is Event.MeetingCalled, is Event.MeetingCheckedIn, is Event.ReadyToVoteDeclared,
    is Event.DiscussionClosed, is Event.VoteSelected, is Event.VoteLocked,
    is Event.VoteWindowClosed, is Event.TallyHalfwayReached, is Event.MeetingClosed,
    is Event.EgressFired,
    // **Isolate READS the Egress and does not need one.** D-150's node exclusion is a rule about
    // what the target list offers and what the rules do with a stale one -- so an Isolate fired
    // while no Egress is running is the ordinary case, not an event out of place. A gate here would
    // refuse every Isolate in every quiet round.
    is Event.SurgeFired, is Event.IsolateFired,
    // **Static does not read the Egress at all.** It has no node exclusion to honour -- D-150 is
    // about a list of markers and a Static names no marker, no room and no seat -- so there is
    // nothing here for it to need, and a gate would refuse every Static in every quiet round.
    is Event.StaticFired,
    is Event.TerminalLeft,
    // The map is sensed whether or not the house is on fire.
    is Event.Sensed,
    // **The house's return reads the Egress and does not need one.** A lock with no Egress running
    // is the ordinary case, and the rules then move nothing.
    is Event.OffAirEnded,
    // **The close reads no Egress** (D-310): a running one ends with the round.
    is Event.HouseClosedOnPermission,
    -> false
}
