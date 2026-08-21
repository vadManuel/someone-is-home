package home.someoneshome.harness

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat

/**
 * Canonical rendering of events and effects.
 *
 * **Explicit, not `toString()`.** A data class's generated `toString` is stable within a
 * compiler version and guaranteed by nothing across one. A recording is a debugging artifact
 * that has to outlive the build that produced it, so "byte-identical replay" cannot rest on a
 * format the language is free to change.
 *
 * **Both `when`s are exhaustive over sealed types, deliberately.** Adding an [Effect] without
 * adding its line here is a COMPILE ERROR, not an effect that silently records as nothing. Same
 * discipline as the redaction schema: the failure mode must be "my thing didn't build", never
 * "my thing vanished from the recording".
 */
object Transcript {

    fun render(event: Event): String = when (event) {
        is Event.RoundArmed ->
            row("RoundArmed", event.at, "seed" to num(event.seed),
                "seats" to event.seats.joinToString(",") { num(it.index) },
                "insiders" to event.insiders.joinToString(",") { num(it.index) })
        is Event.MarkerScanned ->
            row("MarkerScanned", event.at, "actor" to num(event.actor.index), "marker" to text(event.marker.value))
        is Event.SubroutineReturned ->
            row("SubroutineReturned", event.at, "actor" to num(event.actor.index),
                "marker" to text(event.marker.value),
                "entered" to event.entered.joinToString(",") { num(it) })
        is Event.RevokeArmed ->
            row("RevokeArmed", event.at, "actor" to num(event.actor.index))
        is Event.ContactMade ->
            row("ContactMade", event.at, "actor" to num(event.actor.index), "target" to num(event.target.index))
        is Event.MeetingCalled ->
            row("MeetingCalled", event.at, "caller" to num(event.caller.index),
                "trigger" to trigger(event.trigger))
        is Event.MeetingCheckedIn ->
            row("MeetingCheckedIn", event.at, "seat" to num(event.seat.index))
        is Event.ReadyToVoteDeclared ->
            row("ReadyToVoteDeclared", event.at, "seat" to num(event.seat.index))
        is Event.VoteSelected ->
            row("VoteSelected", event.at, "voter" to num(event.voter.index),
                "target" to seatOrNone(event.target))
        is Event.VoteLocked ->
            row("VoteLocked", event.at, "voter" to num(event.voter.index))
        is Event.DiscussionClosed ->
            row("DiscussionClosed", event.at)
        is Event.VoteWindowClosed ->
            row("VoteWindowClosed", event.at)
        is Event.TallyHalfwayReached ->
            row("TallyHalfwayReached", event.at)
        is Event.MeetingClosed ->
            row("MeetingClosed", event.at)
    }

    /**
     * How a meeting was called. Two forms, and the reported seat is part of the fact.
     *
     * `card` and `report:3` rather than a bare seat or a bare word: the discriminator is on the
     * page, so a recording cannot be read as the wrong trigger by a parser that guessed from the
     * shape of the value.
     */
    private fun trigger(trigger: MeetingTrigger): String = when (trigger) {
        is MeetingTrigger.MeetingCard -> "card"
        is MeetingTrigger.RevokeReported -> "report:${num(trigger.reported.index)}"
    }

    /**
     * A refusal by the admission gate (D-066).
     *
     * Carries the event that was refused as well as the reason, because "something was refused at
     * tick 40" and "a ContactMade was refused at tick 40" lead to different places — and the
     * whole point of recording a refusal is that the recording must not disagree with reality
     * about what reached the rules.
     */
    fun render(index: Int, event: Event, reason: RefusalReason): String =
        "Refused|index=${num(index)}|at=${num(event.at.step)}|" +
            "event=${render(event).substringBefore('|')}|reason=$reason"

    fun render(effect: Effect): String = when (effect) {
        is Effect.LampSet ->
            "LampSet|seat=${effect.seat.index}|luminance=${effect.luminance}"
        is Effect.AbilityFired ->
            "AbilityFired|actor=${effect.actor.index}|cooldownStarted=${effect.cooldownStarted}"
        is Effect.SubroutineGraded ->
            "SubroutineGraded|seat=${effect.seat.index}|accepted=${effect.accepted}"
        is Effect.SubroutineProgressed ->
            "SubroutineProgressed|remaining=${effect.remaining}"
        is Effect.MessageDelivered ->
            "MessageDelivered|seat=${effect.seat.index}|body=${text(effect.body)}"
        is Effect.MeetingOpened ->
            "MeetingOpened|caller=${effect.caller.index}|trigger=${trigger(effect.trigger)}" +
                "|haptic=${effect.haptic}"
        is Effect.StandAndWalkIn ->
            "StandAndWalkIn|seat=${effect.seat.index}|haptic=${effect.haptic}"
        is Effect.CheckInProgressed ->
            "CheckInProgressed|present=${effect.present}|expected=${effect.expected}"
        is Effect.MeetingPhaseOpened ->
            "MeetingPhaseOpened|phase=${effect.phase}|haptic=${effect.haptic}"
        is Effect.ReadyProgressed ->
            "ReadyProgressed|ready=${effect.ready}|expected=${effect.expected}"
        is Effect.VoteHeld ->
            "VoteHeld|seat=${effect.seat.index}|selection=${seatOrNone(effect.selection)}" +
                "|locked=${effect.locked}"
        is Effect.VoteSelectionShown ->
            "VoteSelectionShown|voter=${effect.voter.index}|selection=${seatOrNone(effect.selection)}"
        is Effect.VoteProgressed ->
            "VoteProgressed|locked=${effect.locked}|expected=${effect.expected}"
        is Effect.MeetingResult ->
            "MeetingResult|restrained=${seatOrNone(effect.restrained)}|haptic=${effect.haptic}"
        is Effect.MeetingResolved ->
            "MeetingResolved|restrained=${seatOrNone(effect.restrained)}|attribution=" +
                effect.attribution.joinToString(",") { "${it.first.index}>${seatOrNone(it.second)}" }
        is Effect.RestrainedTakeover ->
            "RestrainedTakeover|seat=${effect.seat.index}|haptic=${effect.haptic}"
        is Effect.MeetingEnded ->
            "MeetingEnded|haptic=${effect.haptic}"
    }

    /**
     * Fields are `Pair<String, String>`, not `Pair<String, Any>`.
     *
     * Two reasons, both of which bit. `Any` meant values rendered through a generated
     * `toString()` — the exact thing this file's header says it refuses to rely on — so
     * `"seat" to seat` would have written `Seat(index=3)` into a recording meant to outlive the
     * build. And it let raw Strings through unescaped: a MarkerId is external input, read off a
     * house marker, and one containing a newline produced a genuine forged event row in
     * `toText()`. Typing the vararg forces every call site through [text] or [num], and [text]
     * escapes.
     */
    private fun row(name: String, at: home.someoneshome.model.Tick, vararg fields: Pair<String, String>): String =
        buildString {
            append(name).append("|at=").append(at.step)
            for ((k, v) in fields) append('|').append(k).append('=').append(v)
        }

    /** A string field. Always escaped — separators must not survive inside a value. */
    private fun text(s: String): String = escape(s)

    /** A numeric field. Cannot contain a separator, so it needs no escaping. */
    private fun num(n: Long): String = n.toString()
    private fun num(n: Int): String = n.toString()

    /**
     * Absence renders as a token, never as a sentinel integer.
     *
     * `?.index ?: -1` made a skipped vote and a vote for `Seat(-1)` render identically, so the
     * transcript stopped being injective at precisely the point where "who abstained" matters.
     */
    private fun seatOrNone(seat: Seat?): String = seat?.let { num(it.index) } ?: "none"

    /** Field separators must not survive inside a value, or a body could forge a row. */
    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")

    /**
     * A canonical rendering of authority STATE.
     *
     * This exists because comparing effect streams is structurally insufficient, and rule 1 is
     * why. Rule 1 requires the effect stream to be identical whether a revoke landed or not —
     * so for the single most important transition in the game, "the transcript matched" is the
     * weakest possible evidence. A build where the revoke ability did nothing at all replayed
     * clean against a recording made before it was broken.
     *
     * **Every field of authority state, with no exceptions for fields nothing writes yet.** A
     * field this row omits is a field that guarantee does not cover, and `ended` is the worst
     * possible omission to make: it feeds `roundStateOf`, so it decides what every client is
     * permitted to receive. It was omitted for exactly one commit.
     *
     * State rows close that hole. They never go to a client — this is authority-side debugging
     * tooling, and `GameState` still has no wire encoding.
     */
    fun render(state: GameState): String = buildString {
        append("armed=").append(state.armed)
        append("|ended=").append(state.ended)
        append("|seats=").append(state.seats.joinToString(",") { num(it.index) })
        append("|insiders=").append(state.insiderSeats.joinToString(",") { num(it.index) })
        append("|revoked=").append(state.revoked.joinToString(",") { num(it.index) })
        // Two lists, never one. A Revoke is system power the house lent; a Restrain is a physical
        // act it could not prevent and then ratified (rule 9). A state row that folded them
        // together would replay a round in which the distinction had never existed.
        append("|newlyRevoked=").append(state.newlyRevoked.joinToString(",") { num(it.index) })
        append("|restrained=").append(state.restrained.joinToString(",") { num(it.index) })
        append("|armedRevoke=").append(state.cooldownArmed.joinToString(",") { num(it.index) })
        append("|egress=").append(state.egressRunning)
        append("|integrity=").append(num(state.systemIntegrity))
        // The work order, ANSWER KEY INCLUDED. A state row is authority-side debugging and holds
        // complete ground truth by design — recordings are gitignored and never handed to a
        // player. Omitting `expected` would leave the one piece of state a verdict is computed
        // from outside the guarantee, which is the `ended` omission again in a new costume.
        append("|open=").append(
            state.openSubroutines.joinToString(",") { open ->
                "${num(open.seat.index)}:${open.armedAt?.let { text(it.value) } ?: "none"}" +
                    ":${open.expected.joinToString(".") { num(it) }}"
            }
        )
        // **The meeting, ballots and all.** Live selections are the one thing at a meeting that
        // only a player outside the system may read (D-075), which is exactly why they belong in
        // an authority-side state row: a recording that stopped short of them could not tell a
        // replayed vote from a different one that happened to reach the same result.
        append("|meeting=").append(state.meeting?.let { meeting ->
            "${meeting.caller.index}:${trigger(meeting.trigger)}:${meeting.phase}" +
                ":in=${meeting.checkedIn.joinToString(".") { num(it.index) }}" +
                ":ready=${meeting.ready.joinToString(".") { num(it.index) }}" +
                ":ballots=${meeting.ballots.joinToString(".") {
                    "${num(it.voter.index)}>${seatOrNone(it.selection)}${if (it.locked) "!" else ""}"
                }}" +
                ":pending=${seatOrNone(meeting.restrainPending)}"
        } ?: "none")
        append("|nextEntity=").append(num(state.nextEntity))
        append("|seed=").append(num(state.seed))
    }
}
