package home.someoneshome.model

/**
 * Why the admission gate refused an event (D-066).
 *
 * **Every reason here is publicly observable, and that is the entry condition, not a coincidence.**
 * D-068 permits telling a client it was refused only when the reason is something everyone in the
 * house can already see — whether the lights are on. Round-state qualifies.
 *
 * **This does not survive generalisation, and the generalisation will look like good consistency.**
 * The moment a mid-round refusal is added here — you are revoked, that target is already revoked,
 * your cooldown is running — the identical code becomes an alignment leak, written by someone
 * tidying up error handling. A reason belongs in this enum only if a player standing in the dark
 * could have worked it out without their phone.
 */
enum class RefusalReason {
    /** No round exists yet. D-067: nothing in-game runs in the lobby, including the contact radio. */
    RoundNotArmed,

    /** The round is over. The perimeter is disarmed and everyone can see it. */
    RoundAlreadyEnded,

    /**
     * **An Egress is running, and the meeting card is inert for the duration** (D-133).
     *
     * *A house on fire is not a house that debates.* This passes the entry condition above without
     * straining it: an Egress is audible, it dims every panel in the house (D-118), and it holds a
     * countdown on a widget every player can see. Nobody learns anything from this refusal that
     * they were not already standing in.
     *
     * **Reporting a Revoked player is NOT refused here** — D-121's one exception, unchanged, and
     * D-133 is explicit that the report still triggers a meeting from anywhere. That meeting
     * *pauses* the Egress timer and never resets it, because a reset would make the report a free
     * Egress cancellation and every Egress would end the same way. **The pause is not built** —
     * see the Egress note on [GameState.egressRunning].
     */
    EgressRunning,

    /**
     * A meeting is already in progress.
     *
     * Publicly observable in the most literal way this enum will ever get: the whole house is
     * standing in one room. Without it, a second call would reset the meeting everyone is already
     * at — and the fuzzer calls meetings inside meetings on purpose.
     */
    MeetingAlreadyRunning,

    /**
     * The event belongs to a phase the meeting is not at — including there being no meeting.
     *
     * **Everyone is on the same phase**, which is what makes this admissible under D-068: one
     * room, one clock, and a player who taps a ballot that is not open learns only what the person
     * next to them is also looking at.
     *
     * **This is the boundary of the enum and the next reason will probably be over it.** A refusal
     * that named *why this seat in particular* — you are revoked, you already locked, that target
     * is already out — would be an alignment leak written by someone tidying up error handling.
     * Those refusals live in the rules and come back as an effect of the same shape as a success
     * (rule 1); this one never reaches a rule at all.
     */
    WrongMeetingPhase,
}
