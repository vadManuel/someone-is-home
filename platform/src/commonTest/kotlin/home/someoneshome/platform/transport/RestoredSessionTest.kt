package home.someoneshome.platform.transport

import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RestoredSessionTest {

    @Test
    fun aRestoredSessionNeverSaysHello() {
        // The relaunch half of G1: a killed app comes back with the token it stored, and its
        // first word is Resume. A restored session that said Hello would take A seat — the
        // attribution hole rebuilt on the path nobody exercises, this time by a crash.
        val session = ClientSession(restored = SeatToken("tk-stored"))
        val resume = assertIs<TransportFrame.Resume>(session.opening())
        assertEquals(SeatToken("tk-stored"), resume.token)
        session.onLost(nowMillis = 1_000)
        assertIs<TransportFrame.Resume>(session.opening(), "a loss made the restored session a stranger")
    }

    @Test
    fun aRestoredSessionRetriesImmediatelyThenBacksOff() {
        val session = ClientSession(restored = SeatToken("tk-stored"), backoff = ReconnectBackoff(500, 4_000))
        val held = assertIs<ClientSession.Phase.Rejoining>(session.phase)
        assertEquals(0, held.nextAttemptAtMillis, "a fresh relaunch does not wait to try")
        session.onLost(nowMillis = 1_000)
        val retry = assertIs<ClientSession.Phase.Rejoining>(session.phase)
        assertEquals(1, retry.attempt)
        assertEquals(1_500, retry.nextAttemptAtMillis)
    }
}
