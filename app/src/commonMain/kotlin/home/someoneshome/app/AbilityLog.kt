package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.Intent
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick

/**
 * **What the house did with one ability request, in one line, for the developer console only.**
 *
 * Found on two real iPhones (2026-11-16): the Insider held REVOKE, the tile echoed ARMED, and every
 * touch after it was answered as a plain touch. The house said nothing about the arm, so nobody could
 * tell an arm that never arrived from an arm that arrived inside the opening half-cooldown (D-132)
 * and was refused in silence. Rule 6: silent to the player, **loud to the authority**. This is the
 * loud half, and nothing else changes: every effect, every frame and every screen is what it was.
 *
 * ### Where the line goes, and what it may not carry
 *
 * The house's console and nowhere else. **Never a screen and never a frame**: the app root hands
 * [RoundAuthority] a sink that prints to stdout, and not the rig's on-screen log, because a line about
 * an ability on a screen is a screen that changed for a reason the house did not author.
 *
 * **The seat is not named unless the caller says the console may name it.** Only an Insider's page 2
 * takes a hold (D-142), so *seat n armed a Revoke* names an Insider, and the arming note already keeps
 * that fact out of every log because a device console is a file. The app root names the seat only
 * when the round's seed was handed in at launch: then the Mac already holds the seed, from which
 * `insidersFor` redraws every Insider, and the seat in the line tells it nothing new. Otherwise the
 * line says `A SEAT`.
 */
internal enum class AbilityRequest(val label: String) {
    Revoke("REVOKE ARM"),
    Egress("EGRESS FIRE"),
    Surge("SURGE FIRE"),
    Isolate("ISOLATE FIRE"),
    Static("STATIC FIRE"),
}

/** The ability an intent asks for and who asked, or null for an intent that is not one. */
internal fun abilityRequestOf(intent: Intent): Pair<AbilityRequest, Seat>? = when (intent) {
    is Intent.ArmRevoke -> AbilityRequest.Revoke to intent.actor
    is Intent.FireSurge -> AbilityRequest.Surge to intent.actor
    is Intent.FireIsolate -> AbilityRequest.Isolate to intent.actor
    is Intent.FireStatic -> AbilityRequest.Static to intent.actor
    else -> null
}

/**
 * **The line**: `REVOKE ARM — SEAT 0 · ARMED`, `· COOLING DOWN (READY IN 3S)`, `· REFUSED <reason>`.
 *
 * Read off the gate's answer and the state on both sides of it, never off anything the request
 * claimed. [before] is the state the request was judged against and [at] the tick it was judged at.
 */
internal fun abilityLine(
    request: AbilityRequest,
    actor: Seat,
    at: Tick,
    before: GameState,
    admission: Admission,
    namesSeat: Boolean,
): String {
    val who = if (namesSeat) "SEAT ${actor.index}" else "A SEAT"
    return "${request.label} — $who · ${outcome(request, actor, at, before, admission)}"
}

/** The same line for a request the house declined before it reached the gate (no Egress drawn). */
internal fun abilityLineDeclined(request: AbilityRequest, actor: Seat, why: String, namesSeat: Boolean): String {
    val who = if (namesSeat) "SEAT ${actor.index}" else "A SEAT"
    return "${request.label} — $who · REFUSED $why"
}

private fun outcome(
    request: AbilityRequest,
    actor: Seat,
    at: Tick,
    before: GameState,
    admission: Admission,
): String {
    val admitted = when (admission) {
        is Admission.Refused -> return "REFUSED ${admission.reason}"
        is Admission.Admitted -> admission.reduction
    }
    if (request == AbilityRequest.Revoke) {
        // The arm is silent in both outcomes (D-009, D-142), so the answer is in the state alone.
        val wasArmed = before.cooldownArmed.any { it.index == actor.index }
        val isArmed = admitted.state.cooldownArmed.any { it.index == actor.index }
        val clock = before.cooldownFor(actor, InsiderAbility.Revoke)
        return when {
            isArmed && !wasArmed -> "ARMED"
            clock == null -> "REFUSED NO REVOKE CLOCK FOR THIS SEAT"
            else -> coolingDown(clock.readyAt, at) + if (wasArmed) " · ALREADY ARMED" else ""
        }
    }
    // Every other ability answers its actor with exactly one `AbilityFired` (rule 1), and the flag on
    // it is the truth about whether the actor's clock started (T1·8).
    val fired = admitted.effects.filterIsInstance<Effect.AbilityFired>()
        .firstOrNull { it.actor.index == actor.index }
        ?: return "ANSWERED WITH NO AbilityFired"
    if (fired.cooldownStarted) return "FIRED"
    val readyAt = when (request) {
        AbilityRequest.Egress -> before.egressReadyAt
        else -> before.cooldownFor(actor, InsiderAbility.Access)?.readyAt
    }
    return when {
        readyAt == null -> "NOT FIRED · NO CLOCK FOR THIS SEAT"
        readyAt.step > at.step -> coolingDown(readyAt, at)
        else -> "NOT FIRED · THE CLOCK WAS READY"
    }
}

/** Whole seconds, rounded up, so a clock one tick short never reads READY IN 0S. */
private fun coolingDown(readyAt: Tick, at: Tick): String {
    val ticks = (readyAt.step - at.step).coerceAtLeast(0L)
    val seconds = (ticks + Balance.TICKS_PER_SECOND - 1) / Balance.TICKS_PER_SECOND
    return "COOLING DOWN (READY IN ${seconds}S)"
}
