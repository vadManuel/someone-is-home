package home.someoneshome.harness

import home.someoneshome.model.Effect
import home.someoneshome.model.Event

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
            row("RoundArmed", event.at, "seed" to event.seed,
                "seats" to event.seats.joinToString(",") { it.index.toString() },
                "insiders" to event.insiders.joinToString(",") { it.index.toString() })
        is Event.MarkerScanned ->
            row("MarkerScanned", event.at, "actor" to event.actor.index, "marker" to event.marker.value)
        is Event.SubroutineCompleted ->
            row("SubroutineCompleted", event.at, "actor" to event.actor.index, "marker" to event.marker.value)
        is Event.RevokeArmed ->
            row("RevokeArmed", event.at, "actor" to event.actor.index)
        is Event.ContactMade ->
            row("ContactMade", event.at, "actor" to event.actor.index, "target" to event.target.index)
        is Event.MeetingCalled ->
            row("MeetingCalled", event.at, "caller" to event.caller.index)
        is Event.VoteCast ->
            row("VoteCast", event.at, "voter" to event.voter.index, "target" to (event.target?.index ?: -1))
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
            "MessageDelivered|seat=${effect.seat.index}|body=${escape(effect.body)}"
        is Effect.MeetingOpened ->
            "MeetingOpened|caller=${effect.caller.index}"
        is Effect.MeetingResolved ->
            "MeetingResolved|restrained=${effect.restrained?.index ?: -1}|attribution=" +
                effect.attribution.joinToString(",") { "${it.first.index}>${it.second?.index ?: -1}" }
    }

    private fun row(name: String, at: home.someoneshome.model.Tick, vararg fields: Pair<String, Any>): String =
        buildString {
            append(name).append("|at=").append(at.step)
            for ((k, v) in fields) append('|').append(k).append('=').append(v)
        }

    /** Field separators must not survive inside a value, or a body could forge a row. */
    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")
}
