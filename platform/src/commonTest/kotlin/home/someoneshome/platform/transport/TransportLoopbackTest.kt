package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportRefusal
import home.someoneshome.platform.monotonicNanos
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Story 0.8 over an actual socket — host and client in one process, loopback interface.
 *
 * This is transport plumbing, not the game: no BLE, no torch, no lamp, nothing the Simulator
 * licence forbids certifying. What it proves is that the machinery's properties survive Ktor:
 * a stranger is seated, frames arrive attributed, a dead socket rejoins as THAT seat, and a
 * locked round refuses strangers with a frame rather than a silent close. The two-phone evening
 * proves the same story across real hardware and a real network; this proves the code the
 * evening will run.
 */
class TransportLoopbackTest {

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    @Test
    fun joinCarryDropAndResumeAsThatSeat() = runBlocking {
        var mintN = 0
        val ledger = SeatLedger(List(2) { Seat(it) }, mint = { SeatToken("tk-${mintN++}") })
        val hostFrames = Channel<Pair<Seat, TransportFrame>>(Channel.UNLIMITED)
        val seatedSeats = Channel<Seat>(Channel.UNLIMITED)
        val lostSeats = Channel<Seat>(Channel.UNLIMITED)
        val host = TransportHost(
            ledger,
            onFrame = { seat, frame -> hostFrames.trySend(seat to frame) },
            onSeated = { seatedSeats.trySend(it) },
            onLost = { lostSeats.trySend(it) },
        )
        val port = host.start(0)
        try {
            withTimeout(30_000) {
                // --- a stranger says Hello and is seated ---------------------------------
                val sessionA = ClientSession()
                val aFrames = Channel<TransportFrame>(Channel.UNLIMITED)
                val clientA = TransportClient(
                    sessionA, "127.0.0.1", port, ::nowMillis,
                    onFrame = { aFrames.trySend(it) },
                )
                val jobA = launch { clientA.run() }
                val seatA = seatedSeats.receive()

                // --- frames flow both ways, attributed ------------------------------------
                clientA.send(TransportFrame.Carry("from A"))
                assertEquals(seatA to TransportFrame.Carry("from A"), hostFrames.receive())
                host.send(seatA, TransportFrame.Carry("to A"))
                assertEquals(TransportFrame.Carry("to A"), aFrames.receive())

                // --- arming: strangers are refused with a frame, terminally ---------------
                ledger.lock()
                val sessionB = ClientSession()
                val bPhases = Channel<ClientSession.Phase>(Channel.UNLIMITED)
                val clientB = TransportClient(
                    sessionB, "127.0.0.1", port, ::nowMillis,
                    onFrame = {}, onPhase = { bPhases.trySend(it) },
                )
                val jobB = launch { clientB.run() }
                val dismissed = assertIs<ClientSession.Phase.Dismissed>(bPhases.receive())
                assertEquals(TransportRefusal.RoundLocked, dismissed.reason)
                jobB.join() // run() ends by itself: a refusal is terminal

                // --- the phone dies mid-round ---------------------------------------------
                jobA.cancelAndJoin()
                assertEquals(seatA, lostSeats.receive())

                // --- and comes back as THAT seat, not a seat ------------------------------
                val aFramesAgain = Channel<TransportFrame>(Channel.UNLIMITED)
                val clientAAgain = TransportClient(
                    sessionA, "127.0.0.1", port, ::nowMillis,
                    onFrame = { aFramesAgain.trySend(it) },
                )
                val jobAAgain = launch { clientAAgain.run() }
                assertEquals(seatA, seatedSeats.receive(), "the resume was seated as a different seat")
                assertIs<TransportFrame.Resume>(sessionA.opening(), "the rejoin did not present the stored token")
                host.send(seatA, TransportFrame.Carry("after resume"))
                assertEquals(TransportFrame.Carry("after resume"), aFramesAgain.receive())
                jobAAgain.cancelAndJoin()

                assertEquals(0, host.malformedCount(), "something on the wire was not a frame")
            }
        } finally {
            host.stop()
        }
    }
}
