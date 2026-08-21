package home.someoneshome.core

import home.someoneshome.model.Ballot
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.OpenSubroutine
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
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
fun reduce(state: GameState, event: Event): Reduction<GameState, Effect> = when (event) {

    // Arming CONSTRUCTS the round rather than copying whatever was there. Carrying `revoked` or
    // `cooldownArmed` across an arming means a round can begin with a player already revoked and
    // nothing announcing it — and it is what let a recording replay from a different starting
    // state and still be certified identical.
    is Event.RoundArmed -> {
        val seats = event.seats.sortedBy { it.index }
        val insiders = seats.filter { s -> event.insiders.any { it.index == s.index } }
        Reduction(
        GameState.armedRound(
            seed = event.seed,
            seats = seats,
            insiders = insiders,
            // D-130: the total scales with SEATS, and the coefficient is playtest's.
            systemIntegrity = seats.size * METER_PER_SEAT,
            openSubroutines = seats.map { seat ->
                OpenSubroutine(seat, expected = openingEntry(event.seed, seat), armedAt = null)
            },
        ),
        // Every seat is lit identically at arming. Any per-role difference here would be a tell
        // delivered at the exact moment everyone is still standing together.
        seats.map { Effect.LampSet(it, LAMP_DIM) },
    )
    }

    is Event.RevokeArmed -> Reduction(
        state.copy(cooldownArmed = (state.cooldownArmed + event.actor).sortedBy { it.index }),
        // Silent and invisible: arming changes nothing on any screen. The effect exists so the
        // recording holds the fact, not so a client renders it.
        emptyList(),
    )

    is Event.ContactMade -> contact(state, event)

    is Event.SubroutineReturned -> returned(state, event)

    // A scan arms whatever that seat has open, and emits nothing to anybody (D-110).
    //
    // Deliberately silent in BOTH outcomes. A scan that found work and a scan that found none
    // must be indistinguishable from outside this branch, because "the house has nothing for you
    // at this card" is a sentence the client composes from the absence of work rather than a
    // thing the rules announce -- D-124's NOTHING FOR YOU HERE, held one layer below the copy.
    is Event.MarkerScanned -> Reduction(
        state.openSubroutineFor(event.actor)
            ?.let { state.withOpenSubroutine(it.armedAt(event.marker)) }
            ?: state,
        emptyList(),
    )
    is Event.MeetingCalled -> meetingCalled(state, event)
    is Event.MeetingCheckedIn -> checkedIn(state, event)
    is Event.ReadyToVoteDeclared -> readyDeclared(state, event)
    is Event.DiscussionClosed -> withMeeting(state) { openVote(state, it) }
    is Event.VoteSelected -> voteSelected(state, event)
    is Event.VoteLocked -> voteLocked(state, event)
    is Event.VoteWindowClosed -> withMeeting(state) { readTheBallot(state, it) }
    is Event.TallyHalfwayReached -> withMeeting(state) { takeover(state, it) }
    is Event.MeetingClosed -> withMeeting(state) { lightsOut(state) }
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
 */
private fun returned(
    state: GameState,
    event: Event.SubroutineReturned,
): Reduction<GameState, Effect> {
    val open = state.openSubroutineFor(event.actor)
    val accepted = open != null && open.accepts(event.marker, event.entered)

    // The Insider's fake is graded identically and banks nothing. This is the ONLY place in the
    // rules where a role changes an outcome, and it changes state rather than shape.
    val banks = accepted && state.roleOf(event.actor) == Role.Resident
    val remaining = (state.systemIntegrity - 1).coerceAtLeast(0)

    val spent = if (open == null) state else state.withOpenSubroutine(open.spent())
    val next = if (banks) spent.copy(systemIntegrity = remaining) else spent

    // Constructed once, outside every branch above. Deliberately not inside them.
    val verdict = Effect.SubroutineGraded(seat = event.actor, accepted = accepted)
    return Reduction(
        next,
        if (banks) listOf(verdict, Effect.SubroutineProgressed(remaining)) else listOf(verdict),
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
    return Reduction(
        state.copy(meeting = meeting),
        listOf(ring) + walkIn + gateCount(state, meeting),
    )
}

/**
 * **I'M HERE, and the gate that closes on it** (D-104).
 *
 * The talk does not start until every living player *and* every out player has checked in at the
 * meeting area. The accepted risk was named when it was decided: a player whose phone has died
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
            listOf(counted, Effect.MeetingPhaseOpened(MeetingPhase.Discussion, Haptic.Short)),
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
            Effect.MeetingPhaseOpened(MeetingPhase.Vote, Haptic.Short),
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
            Effect.MeetingPhaseOpened(MeetingPhase.Tally, Haptic.Short),
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
private fun lightsOut(state: GameState): Reduction<GameState, Effect> = Reduction(
    state.copy(meeting = null, newlyRevoked = emptyList()),
    listOf(Effect.MeetingEnded(Haptic.Long)),
)

/** How many seated players are standing at the meeting area. Phantom seats count for nobody. */
private fun present(state: GameState, meeting: Meeting): Int =
    state.seats.count { meeting.hasCheckedIn(it) }

/** D-104's gate as the two numbers a screen draws it with, and never as a list of names. */
private fun gateCount(state: GameState, meeting: Meeting): Effect =
    Effect.CheckInProgressed(present(state, meeting), state.seats.size)

/**
 * **D-130 — the meter total scales with SEATS, and this coefficient is playtest's.**
 *
 * `M = seats × METER_PER_SEAT`. The shape is the ruling; the number is not, and naming them apart
 * is the whole point of D-130 — `7 × residents` was a coefficient with the scaling already implied
 * inside it, so moving one meant re-deriving the other.
 *
 * **It retires the Resident operand, and the reason is not tidiness.** Under D-103 the Insider
 * count can be hidden — drawn at arming, locked, told to nobody — and a total of
 * `(seats − insiders) × K` *is* that count, recoverable by anyone who ever sees an absolute meter
 * value. The display rule (percentage only, never `28/42`) closes the panel; this closes the
 * arithmetic behind it.
 *
 * **Reachability now rests on D-129 rather than on this operand.** Each Resident is given
 * `K = ⌈M ÷ (seats − bandMax)⌉ + slack` Subroutines, computed from public lobby facts alone, so
 * the actual Residents can always complete at least `M`. Five per seat puts `K` at 7 for an
 * eight-seat home, which is where the old placeholder sat — deliberately, so that the number
 * moving is a decision somebody makes rather than a side effect of this change.
 *
 * **`K` itself is NOT built here.** Work-order size is drawn at arming, which is L3's, and `slack`
 * is the balance knob D-129 names. See the L3 boundary note in the worklog.
 *
 * F-005's other half is still not built: orphaned Subroutines — from a revoked player or a
 * collapsed chain — are meant to be silently auto-satisfied so the bar stays winnable.
 */
private const val METER_PER_SEAT = 5

/**
 * **The spine's stand-in for a drawn Subroutine, and L3 deletes it.**
 *
 * A seat's opening entry, derived from the round seed and the seat index — seeded and recorded,
 * never `Uuid.random()` (rule 4), so a round replays to the same questions and therefore to the
 * same verdicts.
 *
 * **It does not read `Role`, and that is load-bearing rather than incidental.** An Insider's fake
 * is drawn by the same rule and sized by the same rule (D-129 — order length is role-independent
 * on both axes), so the differential harness swapping two seats changes not one question asked of
 * anybody. A derivation that took the role would put the answer key itself on the role axis.
 *
 * Two elements over four values is a fixture, not a difficulty: the real questions are the six
 * built Subroutines' own shapes — a returned rhythm, a chosen cell, a finger count, a signed
 * offset, a walked route — and the roster that says which one a card holds is L3's.
 */
private fun openingEntry(seed: Long, seat: Seat): List<Int> {
    var x = seed + seat.index.toLong() * SEAT_STRIDE
    return List(SPINE_ENTRY_LENGTH) {
        x = x * 6364136223846793005L + 1442695040888963407L
        ((x ushr 33) % SPINE_ENTRY_VALUES).toInt()
    }
}

private const val SPINE_ENTRY_LENGTH = 2
private const val SPINE_ENTRY_VALUES = 4L
private const val SEAT_STRIDE = -0x61c8864680b583ebL

private const val LAMP_DIM = 1
