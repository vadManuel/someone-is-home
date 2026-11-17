package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.monotonicNanos
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue

/**
 * **Each seat's frames wait in that seat's own queue, and only there** (R12, D-244).
 *
 * `TransportHost.send` queues and returns; each connection's own writer does the socket write. Real
 * sockets on loopback; one seat's write is held back at the server's real write, standing for a
 * phone that stopped draining.
 */
class OutboundQueueTest {

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    private class Phone(val session: ClientSession, val frames: Channel<TransportFrame>, val job: Job)

    private fun CoroutineScope.phone(port: Int): Phone {
        val session = ClientSession()
        val frames = Channel<TransportFrame>(Channel.UNLIMITED)
        val client = TransportClient(session, "127.0.0.1", port, ::nowMillis, onFrame = { frames.trySend(it) })
        return Phone(session, frames, launch { client.run() })
    }

    /**
     * **GUARD (R12): a seat whose socket stops draining delays nobody, and is dropped once its queue
     * is full.** Two phones; the first's writes are held. Every frame to the second arrives at once
     * and in order, every `send` returns, and the held seat, once [CAPACITY] frames are waiting, is
     * dropped like a lost connection and `send` to it throws.
     */
    @Test
    fun aSeatThatStopsDrainingDelaysNobodyAndIsDroppedWhenItsQueueIsFull() = runBlocking {
        var minted = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${minted++}") })
        val seated = Channel<Seat>(Channel.UNLIMITED)
        val lost = Channel<Seat>(Channel.UNLIMITED)
        var held: Seat? = null
        val drain = CompletableDeferred<Unit>()
        val host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { _, _ -> },
            onSeated = { seated.trySend(it) },
            onLost = { lost.trySend(it) },
            socketWrite = { seat, write ->
                if (seat == held) drain.await()
                write()
            },
            outboundFrames = CAPACITY,
        )
        val port = host.start(0)
        try {
            withTimeout(90_000) {
                val a = phone(port)
                val seatA = seated.receive()
                val b = phone(port)
                val seatB = seated.receive()
                while (a.session.phase !is ClientSession.Phase.Seated || b.session.phase !is ClientSession.Phase.Seated) delay(10)
                held = seatA

                val start = nowMillis()
                // Interleaved, as the effect wire sends one effect to every permitted seat in turn.
                var refused = 0
                var sentB = 0
                try {
                    withTimeout(BOUND_MILLIS) {
                        repeat(FRAMES) { i ->
                            try {
                                host.send(seatA, TransportFrame.Carry("a$i"))
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: IllegalStateException) {
                                refused++
                            }
                            host.send(seatB, TransportFrame.Carry("b$i"))
                            sentB++
                        }
                    }
                } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
                    throw AssertionError("send waited on a held seat's socket: $sentB of $FRAMES frames for the other seat went out in $BOUND_MILLIS ms", late)
                }
                val queued = nowMillis() - start
                val toB = List(FRAMES) { TransportFrame.Carry("b$it") }
                val gotB = List(FRAMES) { b.frames.receive() }
                val took = nowMillis() - start
                println("R12 — $FRAMES FRAMES TO EACH OF TWO SEATS QUEUED IN ${queued} MS, THE DRAINING SEAT HELD ALL IN ${took} MS")
                assertEquals(toB, gotB, "the draining seat's frames were not all there, in the order they were sent")
                assertTrue(took < BOUND_MILLIS, "a held seat delayed the other seat's frames by ${took} ms")

                assertEquals(seatA, withTimeout(30_000) { lost.receive() }, "a seat whose queue filled was never dropped")
                // The sends past the bound threw, which is the effect wire's signal to report the
                // seat unreachable; the phone keeps its place in its stream and asks on its return.
                assertTrue(refused > 0, "a held seat's queue took $FRAMES frames and never filled")
                assertTrue(refused <= FRAMES - CAPACITY, "a held seat was dropped before its queue was full ($refused refused)")
                assertTrue(seatB in host.connectedSeats(), "the draining seat was dropped too")
                drain.complete(Unit)
                a.job.cancelAndJoin()
                b.job.cancelAndJoin()
            }
        } finally {
            drain.complete(Unit)
            host.stop()
        }
    }

    private companion object {
        const val CAPACITY = 8
        const val FRAMES = 40
        /**
         * How long the other seat may take, and it is a bound on a hang rather than a speed (D-251).
         * The held seat is released only when the test ends, so a send coupled to its socket
         * waits for ever and fails at any bound; 40 loopback frames take milliseconds. At 2 s it
         * failed once under three agents' builds.
         */
        const val BOUND_MILLIS = 20_000L
    }
}
