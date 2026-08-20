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
}
