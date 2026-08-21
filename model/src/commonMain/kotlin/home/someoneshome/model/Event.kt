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
        /** A List, not a Set. Hash order varies, and this is recorded input. */
        val insiders: List<Seat>,
    ) : Event

    /**
     * A card was read. **This is what arms a Subroutine, and the only thing that does** (D-110).
     *
     * It emits nothing to anybody, in either outcome — a scan that found work for you and a scan
     * that did not look identical from outside this event, which is D-124's ruling held one layer
     * down from the copy that implements it.
     */
    data class MarkerScanned(override val at: Tick, val actor: Seat, val marker: MarkerId) : Event

    /**
     * **An entry handed over at a marker — a report of what was entered, never a claim that it was
     * right** (D-109).
     *
     * This replaced `SubroutineCompleted`, and the rename is the ruling. A client asserting a
     * *completion* is a client grading its own work, which is the one thing the whole Subroutine
     * treatment is built to keep off the phone: the entry types in `ui` are physically incapable
     * of holding the answer, so the phone could not honestly send that event even if it wanted to.
     * The house holds what was asked, receives what was entered, and concludes.
     *
     * **Abandonment is not here and must not be added** (D-111). Walking away means the entry is
     * never sent: the house grades only what arrives, holds no partial state, and therefore has no
     * abandonment count that could become a behavioural channel separating a real Subroutine from
     * a fake. The presence plane reports *performing at room X* and that is a different report with
     * a single consumer (D-136).
     */
    data class SubroutineReturned(
        override val at: Tick,
        val actor: Seat,
        val marker: MarkerId,
        /** A List, not a Set. Order is the answer in half of the six, and this is recorded input. */
        val entered: List<Int>,
    ) : Event

    /** Armed, not fired. The cooldown starts here — a botched stalk costs a full cooldown. */
    data class RevokeArmed(override val at: Tick, val actor: Seat) : Event

    /** Phone touched to phone. Physical, so it is reported, never requested. */
    data class ContactMade(override val at: Tick, val actor: Seat, val target: Seat) : Event

    /**
     * **A meeting was called, and how** (D-121).
     *
     * Two ways and no third: the caller scanned the meeting card, or somebody reported a Revoked
     * player. [MeetingTrigger] carries which, because the admission gate has to tell them apart —
     * the card is inert during an Egress and the report is not (D-133).
     *
     * The caller's check-in is not a second event. Their scan *is* their check-in and they are
     * counted from the instant the meeting exists.
     */
    data class MeetingCalled(
        override val at: Tick,
        val caller: Seat,
        val trigger: MeetingTrigger,
    ) : Event

    /** I'M HERE. One phone, standing at the meeting area. The gate is the house's (D-104). */
    data class MeetingCheckedIn(override val at: Tick, val seat: Seat) : Event

    /** READY TO VOTE. One hand up; only a **unanimous** one ends the talk early. */
    data class ReadyToVoteDeclared(override val at: Tick, val seat: Seat) : Event

    /**
     * A finger landed on a name, or on Skip.
     *
     * **Every selection tap transmits live** (D-117) — the stream is not an optimisation, it
     * exists so players outside the system can watch the vote happen, which is most of what makes
     * the out's meeting screen worth looking at (D-134).
     */
    data class VoteSelected(override val at: Tick, val voter: Seat, val target: Seat?) : Event

    /**
     * READY: the current selection becomes the vote, **irrevocably** (D-117).
     *
     * **It carries no target, and that is the ruling rather than a saving.** The button is a
     * readiness signal that converts whatever is already selected; a target here would let a
     * client lock a vote it never transmitted, and would make the live selection stream — the
     * whole reason the out have anything to watch — optional decoration.
     *
     * This and [VoteSelected] together replaced a single `VoteCast(voter, target)`, which was the
     * *changeable until the clock ends* model D-117 superseded.
     */
    data class VoteLocked(override val at: Tick, val voter: Seat) : Event

    /** The discussion clock ran out. The house's clock, sampled at the edge like every other. */
    data class DiscussionClosed(override val at: Tick) : Event

    /**
     * The buzzer. **Whatever is selected when the clock ends locks itself** (D-117), and a seat
     * that selected nothing at all for the whole window is a Skip (D-075, narrowed).
     */
    data class VoteWindowClosed(override val at: Tick) : Event

    /**
     * The LIGHTS OUT countdown reached its halfway mark.
     *
     * A separate fact from the countdown ending because two different things happen: here the
     * Restrained takeover reaches the losing seat — *so they do not walk away when the countdown
     * ends* (D-102) — and only at zero does everybody else go back to the round.
     */
    data class TallyHalfwayReached(override val at: Tick) : Event

    /** Lights out. The meeting is over and the round resumes. */
    data class MeetingClosed(override val at: Tick) : Event
}
