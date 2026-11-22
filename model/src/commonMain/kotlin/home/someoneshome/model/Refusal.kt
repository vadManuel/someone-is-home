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
     * **A report whose caller is out of the round** (R3 of the owner's 2026-11-15 rulings, D-217).
     *
     * *Only living players can report.* Admissible for [AbilityNotTheirs]'s reason and no other: no
     * honest phone can produce it. A report is the house's conclusion that a living phone's radio
     * heard a Revoked one; a phone that is out does not scan (D-008) and uploads nothing heard (R8),
     * so the only thing that reaches this is a forged upload or a bench-built event. It is read by
     * the authority and the recording, and by nothing that has a socket (D-097).
     */
    ReporterOut,

    /**
     * **A vote from a seat that is out of the round** (H8 of the 2026-11-17 hunt, D-252).
     *
     * *Ghosts cast nothing* (`gdd.md:417`), and the ballot has always had no row for them. What it
     * had was an answer: a selection tap from a seat with no ballot came back as a live selection
     * of nothing, and live selections go to every seat outside the system, so the couch counted a
     * ghost's SKIP and read VOTES CAST 5 OF 5 on a ballot of four. Admissible for [ReporterOut]'s
     * reason and no other: the couch has no ballot to tap (D-134), so no honest phone produces it.
     * It is read by the authority and the recording, and by nothing that has a socket (D-097).
     */
    VoterOut,

    /**
     * **A meeting called from the meeting card by a seat that is out of the round** (review r4 F5,
     * D-279).
     *
     * *Ghosts cannot act* (gdd M12, `gdd.md:1052`). [ReporterOut] refused the report from the couch
     * and left the card's call open, so a forged call summoned the house and rang every living
     * phone with an out seat's name on it. Admissible for [ReporterOut]'s reason and no other: a
     * phone that is out does not scan (D-008), so no honest phone produces it. Its own reason rather
     * than [ReporterOut]'s because a card call is not a report, and the recording should say which
     * forgery it held. Read by the authority and the recording, and by nothing that has a socket
     * (D-097).
     */
    CallerOut,

    /**
     * **A report of a Revoked player a meeting has already been called to report** (S4 of the
     * owner's 2026-11-15 rulings, D-219).
     *
     * *Repeat reports of the same Revoked player are refused.* A couch phone still advertises
     * (D-008), so after its meeting any living phone held against it would call another. This
     * clears the entry condition above the ordinary way: the meeting that reported this seat rang
     * every phone in the house with its name on it (D-121), so whether it has been reported is
     * something the whole room already stood in. It is read by the authority and the recording,
     * and, like every refusal of the radio's, it is answered with nothing (R4, D-217).
     */
    AlreadyReported,

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

    /**
     * **A [Event.ChainUnlocked] for a dependency that is not due** (D-146, D-322): no link holds that
     * line, its upstream entry has not been completed, its delay has not run out, or it was already
     * released.
     *
     * The event is the house's own and no phone can request it, so this refusal answers nobody: it
     * is the gate keeping a clock that fired twice, or early, from releasing work the rules never
     * scheduled. It names nothing a phone could learn, because no phone is told.
     */
    ChainNotDue,
}
