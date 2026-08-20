package home.someoneshome.platform.transport

import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportRefusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ClientSessionTest {

    @Test
    fun aFreshSessionSaysHelloExactlyOnce() {
        val session = ClientSession()
        assertIs<TransportFrame.Hello>(session.opening())
        session.onSeated(SeatToken("tk-0"))
        val resume = assertIs<TransportFrame.Resume>(session.opening())
        assertEquals(SeatToken("tk-0"), resume.token)
    }

    @Test
    fun everyOpeningAfterALossPresentsTheStoredToken() {
        // G1's reconnect half: re-deriving identity from the lobby code on resume — sending
        // Hello again — rebuilds the attribution hole in the one path nobody exercises. Across
        // repeated losses the session presents the same stored token, every time.
        val session = ClientSession()
        session.onSeated(SeatToken("tk-0"))
        repeat(4) { loss ->
            session.onLost(nowMillis = 10_000L * (loss + 1))
            val resume = assertIs<TransportFrame.Resume>(
                session.opening(),
                "loss ${loss + 1} produced a non-Resume opening frame",
            )
            assertEquals(SeatToken("tk-0"), resume.token)
            session.onSeated(SeatToken("tk-0"))
        }
    }

    @Test
    fun retriesFollowTheBoundedBackoff() {
        val backoff = ReconnectBackoff(baseMillis = 500, capMillis = 4_000)
        val session = ClientSession(backoff)
        session.onSeated(SeatToken("tk-0"))
        val delays = mutableListOf<Long>()
        var now = 100_000L
        repeat(6) {
            session.onLost(now)
            val phase = assertIs<ClientSession.Phase.Rejoining>(session.phase)
            assertEquals(it + 1, phase.attempt, "attempts count consecutive failures")
            delays.add(phase.nextAttemptAtMillis - now)
            now = phase.nextAttemptAtMillis
        }
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 4_000L, 4_000L), delays)
    }

    @Test
    fun aSuccessfulRejoinResetsTheBackoff() {
        val session = ClientSession(ReconnectBackoff(baseMillis = 500, capMillis = 4_000))
        session.onSeated(SeatToken("tk-0"))
        session.onLost(nowMillis = 1_000)
        session.onLost(nowMillis = 2_000)
        session.onSeated(SeatToken("tk-0"))
        session.onLost(nowMillis = 9_000)
        val phase = assertIs<ClientSession.Phase.Rejoining>(session.phase)
        assertEquals(1, phase.attempt)
        assertEquals(9_500, phase.nextAttemptAtMillis)
    }

    @Test
    fun aRefusedSessionIsOverAndNeverFallsBackToHello() {
        val session = ClientSession()
        session.onSeated(SeatToken("tk-0"))
        session.onLost(nowMillis = 1_000)
        session.onRefused(TransportRefusal.UnknownToken)
        assertIs<ClientSession.Phase.Dismissed>(session.phase)
        assertFailsWith<IllegalStateException> { session.opening() }
        session.onLost(nowMillis = 2_000)
        assertIs<ClientSession.Phase.Dismissed>(session.phase, "a dropped socket revived a dismissed session")
    }

    @Test
    fun aSwappedSeatIsRefusedLoud() {
        val session = ClientSession()
        session.onSeated(SeatToken("tk-0"))
        session.onLost(nowMillis = 1_000)
        assertFailsWith<IllegalArgumentException> { session.onSeated(SeatToken("tk-9")) }
    }

    @Test
    fun aLossBeforeAnySeatStartsOverCleanly() {
        // Nothing is held yet, so there is nothing to protect: Hello again is correct here and
        // only here.
        val session = ClientSession()
        session.onLost(nowMillis = 1_000)
        assertIs<TransportFrame.Hello>(session.opening())
    }
}
