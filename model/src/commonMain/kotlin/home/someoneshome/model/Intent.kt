package home.someoneshome.model

/**
 * A request from a client. Imperative mood, and **may be refused**.
 *
 * Kept rigorously separate from [Event] (project-context rule 10). `ArmRevoke` is a request that
 * may be denied; `PlayerRevoked` is a fact that happened. Name them interchangeably and you will
 * eventually store a request in the recording and replay something that never occurred.
 *
 * Intents are attributed **by connection**, never by a client naming itself. A client that could
 * name its own seat could claim another player's — the only cheat in this game that is remote,
 * undetectable, and requires no physical act.
 */
sealed interface Intent {
    data class ArmRevoke(val actor: Seat) : Intent
    data class BeginSubroutine(val actor: Seat, val marker: MarkerId) : Intent
    data class CallMeeting(val actor: Seat) : Intent

    /** I'M HERE. Reports one phone; the gate that closes on it is the house's (D-104). */
    data class CheckIn(val actor: Seat) : Intent

    /** READY TO VOTE. One hand up. Only a unanimous one ends the talk early. */
    data class DeclareReadyToVote(val actor: Seat) : Intent

    /** A finger on a name, or on Skip. Transmitted live so the couch can watch (D-117). */
    data class SelectVote(val actor: Seat, val target: Seat?) : Intent

    /**
     * READY: turn the current selection into the vote, irrevocably (D-117).
     *
     * **Carries no target**, exactly as [Event.VoteLocked] does not — a target here would let a
     * client lock a vote it never transmitted. This replaced `CastVote(actor, target)`, which was
     * the *changeable until the clock ends* model the design has since superseded.
     */
    data class LockVote(val actor: Seat) : Intent
}
