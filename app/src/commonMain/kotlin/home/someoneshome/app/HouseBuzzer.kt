package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.Haptic
import home.someoneshome.platform.Haptics

/**
 * **Every buzz in the game goes through here, and there is nowhere else to get one.**
 *
 * An effect arrives on this phone; if it carries a [Haptic], the motor plays that kind. That is
 * the whole class, and its shortness is the point — D-102's *identical for every player* survives
 * exactly as long as there is one place that turns an arrival into a vibration.
 *
 * ### It is not `PanelVals.buzzes`, and the two must never be joined
 *
 * `ui` already holds a set of screens that buzz, and wiring *that* to the motor is the obvious
 * shortcut and the wrong one. D-134's rule: **the buzz rides the effect rather than living in a
 * table in `ui`** — a client-side lookup that could say *"…and if it was you, buzz differently"*
 * is the device deciding a game answer. `PanelVals.BUZZING` exists so the flow can derive which
 * screens the house drives (`Flow.houseDriving`) and so the render harness can draw a screen
 * knowing it arrived unasked. It is a description of the design, not a source of vibrations.
 *
 * ### It never asks whether the effect was *meant* for this phone
 *
 * There is no filter here and there must not be one. What reaches a client is `EmitSchema`'s
 * decision, made once, on the authority, and a second opinion on the device is the shape rule 1
 * spends its length arguing against: an effect this phone declined to buzz for is an effect whose
 * absence is a fact about this phone's role. Everything delivered buzzes; delivery is decided
 * upstream and fails closed.
 *
 * ### The `when` is exhaustive on purpose
 *
 * A new `Effect` does not compile until somebody has written down whether it buzzes. That is the
 * same fail-closed direction as the schema: the failure is *"my effect doesn't buzz"*, found the
 * first time anyone runs it, never *"my effect buzzes on one role's phone"*, found never.
 */
class HouseBuzzer(private val haptics: Haptics) {

    /**
     * An effect landed on this phone. Buzz if it carries a kind, and stay silent if it does not.
     *
     * **No branch on role, seat, content or which effect it was** — [Effect.haptic] is the only
     * question asked, and the answer is a two-valued enum the house put there.
     */
    fun heard(effect: Effect) {
        haptics.buzz(effect.haptic() ?: return)
    }
}

/**
 * **The kind of buzz an effect carries, or null for the ones that arrive quietly.**
 *
 * The value is read off the effect and never computed: the house constructed it in the same
 * reduction that constructed the push (D-134), so there is nothing to decide here. Twelve kinds
 * carry one; the rest are counts, progress and content that update a screen already on the phone.
 *
 * ### D-135's five, and where they are
 *
 * The long haptic is closed to the Egress ([Effect.EgressOpened]), an incoming phone call
 * ([Effect.MeetingOpened]), STAND AND WALK IN for a newly Revoked player
 * ([Effect.StandAndWalkIn]), the Restrained takeover ([Effect.RestrainedTakeover]), and the end of
 * the LIGHTS OUT countdown ([Effect.MeetingEnded]). **Not one of them is decided here** — each of
 * those effects carries `Haptic.Long` because `Rules.kt` put it there, and `StandAndWalkIn` is the
 * proof that this is the right layer for it: the same effect is Long for a newly Revoked player
 * and Short for the couch, which is a fact about round-state that only the authority holds.
 */
internal fun Effect.haptic(): Haptic? = when (this) {
    // ---- The twelve that buzz ----------------------------------------------------------------
    is Effect.OpeningMessage -> haptic
    is Effect.MeetingOpened -> haptic
    is Effect.StandAndWalkIn -> haptic
    is Effect.MeetingPhaseOpened -> haptic
    is Effect.MeetingResult -> haptic
    is Effect.RestrainedTakeover -> haptic
    is Effect.MeetingEnded -> haptic
    is Effect.EgressOpened -> haptic
    is Effect.EgressHeld -> haptic
    is Effect.EgressContained -> haptic
    // Reaches no client at all — its row is gone from EmitSchema. It is listed anyway because
    // this function answers *what does a delivered effect do*, never *is it delivered*. The two
    // questions have one owner each and it is not the same owner.
    is Effect.EgressSucceeded -> haptic
    is Effect.RoundEnded -> haptic

    // ---- The rest arrive quietly, each for its own reason -------------------------------------
    // The lamp is a pure function of state (rule 5) and a buzz on every luminance change would be
    // a phone that vibrates through a whole round.
    is Effect.LampSet -> null
    // Input echo. The phone that fired it is the phone in the hand that pressed the control.
    is Effect.AbilityFired -> null
    // A verdict on work the player is standing over, on a screen they are already looking at.
    is Effect.SubroutineGraded -> null
    is Effect.SubroutineProgressed -> null
    // D-156: whatever the house has to say arrives as ONE delivery with ONE buzz, and the buzz is
    // OpeningMessage's. The underlying message count never drives the buzz count — that is the
    // exact leak F-003 found, where an Insider's phone went off four times and a Resident's once.
    is Effect.MessageDelivered -> null
    is Effect.HouseSignedOff -> null
    // The work order lands with the opening delivery, which has already buzzed.
    is Effect.WorkOrderIssued -> null
    // The answer to a scan the player is holding the phone up to do.
    is Effect.ScanAnswered -> null
    // The map moving. Every phone in the house would buzz every time anybody walked anywhere.
    is Effect.PresenceChanged -> null
    // Counts ticking up on a screen the player is watching tick up.
    is Effect.CheckInProgressed -> null
    is Effect.ReadyProgressed -> null
    is Effect.VoteProgressed -> null
    // Their own selection coming back, and the couch's live view of everyone's.
    is Effect.VoteHeld -> null
    is Effect.VoteSelectionShown -> null
    // The attribution, on the screen MeetingResult already buzzed for.
    is Effect.MeetingResolved -> null
    // The house's answer to one beat of the Sync Pulse, at a terminal, in a hand.
    is Effect.SyncPulseAnswered -> null
    // The reveal, on the ending screen RoundEnded already buzzed for. One ending, one buzz.
    is Effect.InsidersRevealed -> null
}
