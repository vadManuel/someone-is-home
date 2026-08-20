package home.someoneshome.platform.transport

import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TRANSPORT_PROTOCOL
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportRefusal

/**
 * The client's half of story 0.8: what a phone says when it connects, and what it holds on to.
 *
 * ### The property this type exists for
 *
 * **Once a token is held, every subsequent opening frame is [TransportFrame.Resume] with that
 * token.** Never [TransportFrame.Hello] again — a rejoin that fell back to Hello would be
 * re-deriving identity from the lobby code on the one path nobody exercises, which is the exact
 * hole G1 names. There is no operation on this class that discards the token and keeps going.
 *
 * A resume the host refuses therefore ends the session ([Phase.Dismissed]) rather than retrying
 * as a stranger: presenting a token the ledger does not know means one side has lost the round,
 * and quietly taking *a* seat at that point is the failure, not the recovery.
 *
 * ### What is deliberately absent
 *
 * The lamp. G3 says the lamp holds its last authorised state throughout a reconnect — but the
 * lamp is `ui`'s and the rule is enforced where the lamp is drawn, not here. This class only
 * refuses to invent any signal a screen could echo: losing the socket changes what the session
 * *sends*, never what anything shows.
 *
 * Pure, single-threaded (D4), time handed in as monotonic milliseconds.
 */
class ClientSession(
    /**
     * The token a relaunched process read back from [home.someoneshome.platform.loadSeatToken].
     * A session restored this way NEVER says Hello — it was seated once, the seat is its
     * token's, and a fresh process is just a very long socket loss.
     */
    restored: SeatToken? = null,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
) {

    sealed interface Phase {
        /** No seat yet. The next opening frame asks for *a* seat. */
        data object Joining : Phase

        /** Connected and holding [token]'s seat. */
        data class Seated(val token: SeatToken) : Phase

        /** Socket lost; holding [token]; retry [attempt] due at [nextAttemptAtMillis]. */
        data class Rejoining(
            val token: SeatToken,
            val attempt: Int,
            val nextAttemptAtMillis: Long,
        ) : Phase

        /** The host refused this connection for good. The session is over; a human decides next. */
        data class Dismissed(val reason: TransportRefusal) : Phase
    }

    var phase: Phase =
        restored?.let { Phase.Rejoining(it, attempt = 0, nextAttemptAtMillis = 0) } ?: Phase.Joining
        private set

    /**
     * The frame that opens (or reopens) the connection. Hello exactly once, ever — the first
     * grant makes this Resume for the rest of the session's life.
     */
    fun opening(): TransportFrame = when (val p = phase) {
        is Phase.Joining -> TransportFrame.Hello(TRANSPORT_PROTOCOL)
        is Phase.Seated -> TransportFrame.Resume(p.token)
        is Phase.Rejoining -> TransportFrame.Resume(p.token)
        is Phase.Dismissed -> error("a dismissed session opens nothing — reason: ${p.reason}")
    }

    /** The host seated us. On a rejoin the token must be our own — a swapped seat is refused loudly. */
    fun onSeated(token: SeatToken) {
        val held = tokenOrNull()
        require(held == null || held == token) {
            "the host seated this connection under a different token — refusing the swapped seat"
        }
        phase = Phase.Seated(token)
    }

    /** The socket dropped. Keep the token, schedule the next attempt on the bounded backoff. */
    fun onLost(nowMillis: Long) {
        if (phase is Phase.Dismissed) return // terminal stays terminal; a dropped socket revives nothing
        val token = tokenOrNull() ?: run {
            // Lost before ever being seated: nothing is held, so joining simply starts over.
            phase = Phase.Joining
            return
        }
        val attempt = ((phase as? Phase.Rejoining)?.attempt ?: 0) + 1
        phase = Phase.Rejoining(token, attempt, nowMillis + backoff.delayMillis(attempt))
    }

    /** The host refused the opening frame. Terminal — see the class KDoc for why there is no retry-as-Hello. */
    fun onRefused(reason: TransportRefusal) {
        phase = Phase.Dismissed(reason)
    }

    private fun tokenOrNull(): SeatToken? = when (val p = phase) {
        is Phase.Seated -> p.token
        is Phase.Rejoining -> p.token
        is Phase.Joining, is Phase.Dismissed -> null
    }
}
