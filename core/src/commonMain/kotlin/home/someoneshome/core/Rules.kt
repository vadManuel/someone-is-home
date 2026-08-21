package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.OpenSubroutine
import home.someoneshome.model.Role
import home.someoneshome.model.Seat

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
    is Event.MeetingCalled -> Reduction(state, listOf(Effect.MeetingOpened(event.caller)))
    is Event.VoteCast -> Reduction(state, emptyList())
    is Event.MeetingClosed -> Reduction(state, emptyList())
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
