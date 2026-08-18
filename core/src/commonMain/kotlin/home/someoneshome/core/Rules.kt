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
    is Event.RoundArmed -> Reduction(
        GameState.armedRound(
            seed = event.seed,
            seats = event.seats.sortedBy { it.index },
            insiders = event.seats.filter { s -> event.insiders.any { it.index == s.index } }
                .sortedBy { it.index },
            systemIntegrity = event.seats.size * SUBROUTINES_PER_RESIDENT,
        ),
        // Every seat is lit identically at arming. Any per-role difference here would be a tell
        // delivered at the exact moment everyone is still standing together.
        event.seats.sortedBy { it.index }.map { Effect.LampSet(it, LAMP_DIM) },
    )

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

/** Placeholder until F-005 resolves the SystemIntegrity denominator against the count assigned. */
private const val SUBROUTINES_PER_RESIDENT = 7

private const val LAMP_DIM = 1
