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
    data class CastVote(val actor: Seat, val target: Seat?) : Intent
    data class DeclareReadyToVote(val actor: Seat) : Intent
}
