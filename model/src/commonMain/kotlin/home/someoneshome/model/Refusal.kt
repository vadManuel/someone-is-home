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
 *
 * **[AbilityNotTheirs] is the one exception and it is argued at the member, not waved past here.**
 * It is admissible because no honest client can produce the intent it refuses, and because D-097
 * deleted the refusal message the paragraph above is a warning about. The entry condition is
 * unchanged for every reason that *can* meet a player: a refusal about a seat still needs to be a
 * fact the room is already standing in.
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
     * Egress cancellation and every Egress would end the same way. **The pause is built** — see
     * [Egress.pausedAt] and [Egress.resumedAt].
     */
    EgressRunning,

    /**
     * **This event belongs to an Egress, and there is no Egress to be had** — including one that
     * is *paused* while the party stands in a meeting.
     *
     * The mirror of [WrongMeetingPhase] and admissible for the same reason: both halves are
     * something everyone in the house is already looking at. An Egress holds a countdown on every
     * widget in the building and dims every panel when it starts (D-118), so *the house is not on
     * fire* is not news; and a paused Egress means the whole party is standing in one room, which
     * is the most publicly observable state this game has.
     *
     * **It names no seat, and it must never start.** *You are not at a node*, *you are serving a
     * lockout*, *you are Revoked* are the refusals that suggest themselves next, and every one of
     * them is a claim about one player that would be readable from the outside. Those live in the
     * rules and come back as [Effect.SyncPulseAnswered] — the same shape a good beat gets, with a
     * different value in it.
     */
    EgressNotRunning,

    /**
     * A meeting is already in progress.
     *
     * Publicly observable in the most literal way this enum will ever get: the whole house is
     * standing in one room. Without it, a second call would reset the meeting everyone is already
     * at — and the fuzzer calls meetings inside meetings on purpose.
     */
    MeetingAlreadyRunning,

    /**
     * **The seat that asked to fire does not hold the ability** — it is not an Insider, or it is
     * no longer in the round (E-R2-1).
     *
     * ### It is the one reason here a player could not have read off the room, and that is lawful
     *
     * Every other member of this enum clears the entry condition above by being publicly
     * observable. This one does the opposite: *who is an Insider* is the single fact the whole app
     * exists to keep off every screen. It is admissible for the reason the Tier-2-at-a-meeting
     * branch is — **no honest client can produce the intent, so this is not a client-visible path
     * at all.** A Resident's page-2 controls take no pointer input from the first millisecond
     * (D-142) and an out seat has no springboard, so the only thing that reaches this gate is a
     * forged or bench-built frame, and a forged frame has no player behind it to leak to.
     *
     * ### And the channel that would have carried it does not exist
     *
     * D-068's warning — *the identical code reporting a mid-round refusal is an alignment leak
     * written by someone tidying up error handling* — was a warning about a **refusal message**.
     * D-097 deleted that message: a refused event is answered by re-pushing that seat's own
     * authorized view, and no [Effect] and no wire kind can carry a reason down. So this enum is
     * read by the authority and by the recording, and by nothing that has a socket.
     *
     * **The two halves are one reason on purpose.** *Not an Insider* and *out of the round* are
     * both *this ability is not yours to fire*, and splitting them would put the difference between
     * a Resident and a Revoked Insider into the recording as two distinct rows — a distinction the
     * transcript has no reason to hold and the one a reader of a leaked recording would look for
     * first.
     */
    AbilityNotTheirs,

    /**
     * The event belongs to a phase the meeting is not at — including there being no meeting.
     *
     * **Everyone is on the same phase**, which is what makes this admissible under D-068: one
     * room, one clock, and a player who taps a ballot that is not open learns only what the person
     * next to them is also looking at.
     *
     * **This was declared the boundary of the enum, and [EgressNotRunning] was tested against
     * that line rather than waved past it.** It clears the entry condition for the same reason
     * this one does — a countdown on every widget is not something a phone tells you — and it
     * names no seat. The line itself has not moved: a refusal that named *why this seat in
     * particular* — you are revoked, you already locked, you are not at a node — would be an
     * alignment leak written by someone tidying up error handling. Those refusals live in the
     * rules and come back as an effect of the same shape as a success (rule 1); the two here never
     * reach a rule at all.
     */
    WrongMeetingPhase,
}
