package home.someoneshome.model

/**
 * A fact. Past tense, already happened, and the only thing the rules consume.
 *
 * Events are what the recording stores and what replay feeds back. Every one carries the [Tick]
 * it occurred at, sampled at the edge — the rules never ask what time it is.
 *
 * **This set is deliberately partial.** It covers the spine the round trace walks; several
 * systems it names are still blocked on open design decisions (Egress node designation F-001,
 * the SystemIntegrity denominator F-005, carry state across a meeting F-014). Inventing events
 * for those here would encode a guess as a decision.
 */
sealed interface Event {
    val at: Tick

    /** Balance values lock here and stamp into the recording. No mid-round edits. */
    data class RoundArmed(
        override val at: Tick,
        val seed: Long,
        val seats: List<Seat>,
        val insiders: Set<Seat>,
    ) : Event

    data class MarkerScanned(override val at: Tick, val actor: Seat, val marker: MarkerId) : Event
    data class SubroutineCompleted(override val at: Tick, val actor: Seat, val marker: MarkerId) : Event

    /** Armed, not fired. The cooldown starts here — a botched stalk costs a full cooldown. */
    data class RevokeArmed(override val at: Tick, val actor: Seat) : Event

    /** Phone touched to phone. Physical, so it is reported, never requested. */
    data class ContactMade(override val at: Tick, val actor: Seat, val target: Seat) : Event

    data class MeetingCalled(override val at: Tick, val caller: Seat) : Event
    data class VoteCast(override val at: Tick, val voter: Seat, val target: Seat?) : Event
    data class MeetingClosed(override val at: Tick) : Event
}
