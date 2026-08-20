package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
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
            // Residents, NOT seats. Insiders have no assigned subroutines and no action an
            // Insider takes ever advances the meter (gdd.md:382), so counting their seats sets a
            // bar the Residents cannot reach — at 8 seats and 2 Insiders, 56 against 42
            // completable. That is not an unrefined placeholder, it is a win condition that can
            // never be met. F-005's proposed resolution, and the operand it names.
            systemIntegrity = (seats.size - insiders.size) * SUBROUTINES_PER_RESIDENT,
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

    is Event.SubroutineCompleted -> Reduction(
        state.copy(systemIntegrity = (state.systemIntegrity - 1).coerceAtLeast(0)),
        listOf(Effect.SubroutineProgressed(remaining = (state.systemIntegrity - 1).coerceAtLeast(0))),
    )

    is Event.MarkerScanned -> Reduction(state, emptyList())
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

private fun sameSeat(a: Seat, b: Seat) = a.index == b.index

/**
 * Still a placeholder — F-005 has a *proposed* resolution, not a settled one, and the 7 is the
 * part that is unrefined. The operand beside it is not: see the arming branch.
 *
 * F-005's other half is NOT built here. Orphaned subroutines — from a revoked player or a
 * collapsed chain — are meant to be silently auto-satisfied so the bar stays winnable, and
 * nothing does that yet. Without it the bar is reachable in arithmetic and not in play.
 */
private const val SUBROUTINES_PER_RESIDENT = 7

private const val LAMP_DIM = 1
