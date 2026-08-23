package home.someoneshome.model

/**
 * **Which side took the round.**
 *
 * Plural, and deliberately not [Role]. A role is one player's alignment and it is the thing the
 * app spends the whole evening refusing to state; a winner is a side, it is stated exactly once,
 * and it is stated to everybody. Two facts of two different kinds, so two types — a `Role` used as
 * a winner would put the alignment word on a field that is broadcast, one rename away from
 * somebody reading a player's row out of it.
 */
@ClientFacing
@kotlinx.serialization.Serializable
enum class Winner { Residents, Insiders }

/**
 * **How the round ended — D-131's four routes, and there is no fifth.**
 *
 * Public without exception. Every one of them is something the whole house watched happen: the
 * meter arriving at nothing, the room running out of people to restrain, the room running out of
 * plain Residents, a countdown reaching zero with the doors still open. Naming the route on the
 * ending push therefore discloses nothing the reveal on the same screen does not disclose more
 * loudly.
 */
@ClientFacing
@kotlinx.serialization.Serializable
enum class WinRoute {
    /**
     * **SystemIntegrity reached zero, and the Residents win immediately** (`gdd.md:203`, D-131).
     *
     * The one exception to the meter being frozen between meetings. Everything else the meter does
     * waits for a house meeting to be batched and shown; this does not wait for anything, because
     * a group that finished the work and then stood in the dark for six minutes waiting to be told
     * so has been robbed of the moment it earned.
     */
    SystemIntegrityCleared,

    /**
     * **No Insider is left in the round** — D-131's *Restraining every Insider*.
     *
     * Written as *nobody is left* rather than as *everybody was Restrained* because the win check
     * counts **living** Insiders, never initial ones (`gdd.md:213`) — and because the two are the
     * same set anyway: a Revoke needs a living Insider to fire it and [Winner] is not something a
     * seat can spend on itself, so Revokes alone can never take the last one off the board.
     */
    InsidersRestrained,

    /**
     * **Living plain Residents fell to the number of living Insiders** (D-131).
     *
     * *The vote veto made formal.* At parity the Insiders can always block a Restrain, so the
     * round is decided whether or not anybody says so — this is the rule stating it rather than
     * leaving the Residents to discover it over three meetings that cannot go anywhere.
     */
    Parity,

    /**
     * **An Egress ran its clock out uncontained, and the Insiders win outright** (D-131).
     *
     * The one route that is a *fact* rather than a predicate over state: once the Egress is
     * cleared, nothing left in [GameState] can tell an expiry from a containment. See
     * `Rules.outcomeOf`, which is why it reads the event.
     */
    EgressUncontained,
}

/**
 * The round is over, and this is the whole of what that means.
 *
 * Held on [GameState] rather than a boolean, for the reason `egressRunning` derives from `egress`:
 * a flag beside a reason is two fields that can disagree about the same round, and the one that
 * decides what every client may receive ([RoundState.Ended]) would be the one with no reason on it.
 */
data class Outcome(val winner: Winner, val by: WinRoute)

/**
 * **One player's one line, on the house's desk, for the length of one round** (D-116).
 *
 * The thing a player would rather not explain, typed under two promises printed on the screen where
 * it is typed: *seen by the house only*, and *deleted when the round ends*. This is the authority's
 * copy of it; `platform`'s `LobbyDesk` holds the transport's, and `ui`'s `OneLine` holds the
 * author's own.
 *
 * ### It is ground truth, and it never renders
 *
 * [GameState] is not `@Serializable` and never will be, so this cannot reach a wire by the ordinary
 * route. The route it *could* have reached is the recording — the one artefact of this game that
 * outlives the evening — and `Transcript` renders these as a **count and never as text** for
 * exactly that reason. A recording is physically incapable of holding a one line.
 *
 * ### Keyed on the seat, never on the name
 *
 * A client names its owner and never its seat; attribution is the seat the ledger granted. Same
 * rule `LobbyDesk` keeps, one layer up.
 */
data class OneLineHeld(val seat: Seat, val text: String)

/**
 * **One Insider, named, with the line they were coerced with** (`gdd.md:1063`).
 *
 * The single type in this codebase that pairs a seat with an alignment, and it exists as its own
 * type so that it can be found: grep it and you have found every place the app is capable of
 * saying who was working for the house. There is one, it is [Effect.InsidersRevealed], and it is
 * permitted to no client class that exists before the round ends.
 *
 * *Never shown* (`gdd.md:213`) governs everything up to the reveal. This is the reveal.
 */
@ClientFacing
@kotlinx.serialization.Serializable
data class InsiderNamed(
    val seat: Seat,
    /**
     * **What to call them, resolved from the desk** (D-115).
     *
     * The reveal is read out loud in a lit room, and *seat 3* is not a person. The names live with
     * the transport — `LobbyDesk` holds what each phone asked to be called — and they ride the
     * arming event into the round exactly as the one lines do, which is what lets the rules put a
     * name on this without the rules ever having held a lobby.
     *
     * **It discloses nothing new.** Every phone in the lobby was already sent every name, in
     * arrival order, on `LobbyBody.Standing`; what this pairs with a name is the alignment, and
     * that is the disclosure, and it is the one this effect exists to make.
     *
     * Blank for a seat that never said what to call it — the fail-closed direction for text, and
     * the same blank a replayed round produces, because a recording holds seats and no names.
     */
    val name: String,
    val line: String,
)

/**
 * **What one seat asked to be called, for the length of one round** (D-115).
 *
 * Round-scoped like the one lines and dropped by the same transition: `GameState.endRound` empties
 * both, and `LobbyDesk.roundEnded` empties the transport's copies. Two copies, two wipes, neither
 * trusting the other.
 *
 * **Not the same promise a one line carries and held to the same standard anyway.** A name is
 * something six people standing in a hall can already see; a line is not. What they share is that
 * neither survives the round, so neither is written into a recording — `Transcript` renders seats
 * and no names, and a replayed reveal publishes blanks.
 *
 * Keyed on the seat, never on the name itself: a client names its owner and never its seat, so
 * attribution is the seat the ledger granted.
 */
data class SeatNamed(val seat: Seat, val name: String)
