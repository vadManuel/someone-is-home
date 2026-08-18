package home.someoneshome.harness

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
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
        is Event.SubroutineCompleted ->
            row("SubroutineCompleted", event.at, "actor" to num(event.actor.index), "marker" to text(event.marker.value))
        is Event.RevokeArmed ->
            row("RevokeArmed", event.at, "actor" to num(event.actor.index))
        is Event.ContactMade ->
            row("ContactMade", event.at, "actor" to num(event.actor.index), "target" to num(event.target.index))
        is Event.MeetingCalled ->
            row("MeetingCalled", event.at, "caller" to num(event.caller.index))
        is Event.VoteCast ->
            row("VoteCast", event.at, "voter" to num(event.voter.index), "target" to seatOrNone(event.target))
        is Event.MeetingClosed ->
            row("MeetingClosed", event.at)
    }

    fun render(effect: Effect): String = when (effect) {
        is Effect.LampSet ->
            "LampSet|seat=${effect.seat.index}|luminance=${effect.luminance}"
        is Effect.AbilityFired ->
            "AbilityFired|actor=${effect.actor.index}|cooldownStarted=${effect.cooldownStarted}"
        is Effect.SubroutineProgressed ->
            "SubroutineProgressed|remaining=${effect.remaining}"
        is Effect.MessageDelivered ->
            "MessageDelivered|seat=${effect.seat.index}|body=${text(effect.body)}"
        is Effect.MeetingOpened ->
            "MeetingOpened|caller=${effect.caller.index}"
        is Effect.MeetingResolved ->
            "MeetingResolved|restrained=${seatOrNone(effect.restrained)}|attribution=" +
                effect.attribution.joinToString(",") { "${it.first.index}>${seatOrNone(it.second)}" }
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
     * State rows close that hole. They never go to a client — this is authority-side debugging
     * tooling, and `GameState` still has no wire encoding.
     */
    fun render(state: GameState): String = buildString {
        append("armed=").append(state.armed)
        append("|seats=").append(state.seats.joinToString(",") { num(it.index) })
        append("|insiders=").append(state.insiderSeats.joinToString(",") { num(it.index) })
        append("|revoked=").append(state.revoked.joinToString(",") { num(it.index) })
        append("|armedRevoke=").append(state.cooldownArmed.joinToString(",") { num(it.index) })
        append("|integrity=").append(num(state.systemIntegrity))
        append("|nextEntity=").append(num(state.nextEntity))
        append("|seed=").append(num(state.seed))
    }
}
