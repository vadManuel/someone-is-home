package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.monotonicNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **A seat's loss is always reported, and always before a later resume of the same seat** (D-227, D-246).
 *
 * `TransportHost` reports [TransportHost]'s `onLost` under its lock, and takes that lock
 * uncancellably. Two properties, one test each. `96a4c2e` built both and could not make either fail
 * by breaking it, because on loopback the window each closes is microseconds wide. Here the window
 * is held open on purpose: the caller's `onLost` takes [SLOW_LOSS_MILLIS] (a thread descheduled at
 * the wrong moment, or a slow desk), and what may or may not get past it while it runs is the test.
 *
 * Real sockets on loopback, real clients and sessions.
 */
class LossOrderTest {

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    /** Blocks this thread, as a slow callback does: the lock it is called under stays held. */
    private fun spin(millis: Long) {
        val until = nowMillis() + millis
        while (nowMillis() < until) Unit
    }

    private class Phone(val session: ClientSession, val job: Job)

    private fun CoroutineScope.phone(port: Int, session: ClientSession = ClientSession()): Phone {
        val client = TransportClient(session, "127.0.0.1", port, ::nowMillis, onFrame = { })
        return Phone(session, launch { client.run() })
    }

    private suspend fun seatedOf(phone: Phone): SeatToken {
        while (phone.session.phase !is ClientSession.Phase.Seated) delay(5)
        return assertIs<ClientSession.Phase.Seated>(phone.session.phase).token
    }

    /**
     * **GUARD (D-227): a loss is reported before the resume that follows it.** A phone drops, and
     * while the house is still reporting the loss (slowly), the phone comes back by its token. The
     * caller must hear *lost* before *seated again*: told the other way round, a lobby empties a
     * chair somebody is sitting in and refuses the name that seat sends next.
     */
    @Test
    fun aLossIsReportedBeforeTheResumeThatFollowsIt() = runBlocking {
        var minted = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${minted++}") })
        val heard = Channel<String>(Channel.UNLIMITED)
        val losing = Channel<Seat>(Channel.UNLIMITED)
        val host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { _, _ -> },
            onSeated = { heard.trySend("seated ${it.index}") },
            onLost = { seat ->
                losing.trySend(seat)
                spin(SLOW_LOSS_MILLIS)
                heard.trySend("lost ${seat.index}")
            },
        )
        val port = host.start(0)
        try {
            withTimeout(30_000) {
                val first = phone(port)
                val token = seatedOf(first)
                assertEquals("seated 0", heard.receive())

                // The phone's socket fails; the house begins reporting the loss.
                first.job.cancelAndJoin()
                assertEquals(Seat(0), losing.receive(), "the house never began reporting the loss")
                // While it is still reporting, the phone comes back by its token.
                val back = phone(port, ClientSession(restored = token))
                assertEquals(token, seatedOf(back), "the phone came back holding somebody else's token")

                val order = listOf(heard.receive(), heard.receive())
                assertEquals(
                    listOf("lost 0", "seated 0"), order,
                    "the house said the seat was seated again before it said the seat was lost",
                )
                back.job.cancelAndJoin()
            }
        } finally {
            host.stop()
        }
    }

    /**
     * **GUARD (D-227): every connection a stopping server cancels reports its loss.** Four phones
     * seated; the first loss reported is slow, so the other three connections are waiting for the
     * lock when the server cancels them. Each must still report, or the house goes on believing in
     * seats whose phones have gone (the D-225 lock-and-unlock path).
     */
    @Test
    fun everyConnectionAStoppingServerCancelsReportsItsLoss() = runBlocking {
        var minted = 0
        val ledger = SeatLedger(List(6) { Seat(it) }, mint = { SeatToken("tk-${minted++}") })
        val lost = Channel<Seat>(Channel.UNLIMITED)
        // Read and written under the lock, where `onLost` is called.
        var losses = 0
        val host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { _, _ -> },
            onLost = { seat ->
                if (++losses == 1) spin(SLOW_LOSS_MILLIS)
                lost.trySend(seat)
            },
        )
        val port = host.start(0)
        val phones = mutableListOf<Phone>()
        try {
            withTimeout(30_000) {
                repeat(SEATS) { phones += phone(port).also { seatedOf(it) } }
                assertEquals(SEATS, host.connectedSeats().size, "not every phone was seated")

                host.stop()
                val reported = mutableSetOf<Seat>()
                try {
                    withTimeout(SLOW_LOSS_MILLIS * (SEATS + 1) + 3_000) { while (reported.size < SEATS) reported += lost.receive() }
                } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
                    throw AssertionError("a stopping server reported ${reported.size} of $SEATS losses: ${reported.map { it.index }}", late)
                }
                assertEquals((0 until SEATS).map { Seat(it) }.toSet(), reported, "a seat's loss went unreported")
                assertTrue(host.connectedSeats().isEmpty(), "a seat whose phone has gone is still in the table: ${host.connectedSeats()}")
                // The phones are this block's children, and it would wait on their reconnecting.
                phones.forEach { it.job.cancelAndJoin() }
            }
        } finally {
            phones.forEach { it.job.cancelAndJoin() }
            host.stop()
        }
    }

    private companion object {
        const val SLOW_LOSS_MILLIS = 400L
        const val SEATS = 4
    }
}
