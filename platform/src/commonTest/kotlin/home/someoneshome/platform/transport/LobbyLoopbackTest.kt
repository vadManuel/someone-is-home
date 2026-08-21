package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.monotonicNanos
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * **The lobby over an actual socket** — two phones, one host, loopback interface.
 *
 * [TransportLoopbackTest] proves the machinery survives Ktor. This proves the lobby that rides on
 * it: a line typed on one phone reaches the host inside an opaque `Carry`, the host files it, and
 * what comes back to *both* phones is counts. No BLE, no torch, no lamp — nothing the Simulator
 * licence forbids certifying, and nothing here needs a phone at all.
 *
 * The property worth a socket rather than a unit test: **the exchange is asymmetric by
 * construction.** Up goes text, down comes arithmetic, and the two directions are different types,
 * so a client cannot be handed somebody else's line by a host that filled in the wrong field.
 *
 * Everything crossing a thread here crosses on a [Channel]. The desk is the host's and lives on
 * Ktor's threads; polling it from the test thread would be reading a `HashMap` somebody else is
 * writing, which is a flake nobody would ever reproduce.
 */
class LobbyLoopbackTest {

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    private fun standingIn(frame: TransportFrame): LobbyBody.Standing? =
        (frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull) as? LobbyBody.Standing

    @Test
    fun twoPhonesHandOverAndBothSeeCountsOnly() = runBlocking {
        var mintN = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${mintN++}") })
        val desk = LobbyDesk()
        val seatedSeats = Channel<Seat>(Channel.UNLIMITED)
        val lostSeats = Channel<Seat>(Channel.UNLIMITED)
        val filed = Channel<Seat>(Channel.UNLIMITED)

        val host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { seat, frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull)
                if (body is LobbyBody.Handover) {
                    desk.handedOver(seat, body.line)
                    filed.trySend(seat)
                }
            },
            onSeated = { desk.seated(it); seatedSeats.trySend(it) },
            onLost = { desk.left(it); lostSeats.trySend(it) },
        )

        // The host's whole lobby duty, in the two lines an app would have: file what arrived,
        // then tell everybody how it stands.
        suspend fun publish() {
            val body = TransportFrame.Carry(LobbyWire.encode(desk.standing()))
            for (seat in host.connectedSeats()) host.send(seat, body)
        }

        val port = host.start(0)
        try {
            withTimeout(30_000) {
                val elliotSecret = "i still have priya's spare key"
                val priyaSecret = "i read the group chat i was removed from"

                val elliotSaw = Channel<LobbyBody.Standing>(Channel.UNLIMITED)
                val priyaSaw = Channel<LobbyBody.Standing>(Channel.UNLIMITED)

                val elliotSession = ClientSession()
                val elliot = TransportClient(
                    elliotSession, "127.0.0.1", port, ::nowMillis,
                    onFrame = { standingIn(it)?.let(elliotSaw::trySend) },
                )
                val elliotJob = launch { elliot.run() }
                val elliotSeat = seatedSeats.receive()

                val priyaSession = ClientSession()
                val priya = TransportClient(
                    priyaSession, "127.0.0.1", port, ::nowMillis,
                    onFrame = { standingIn(it)?.let(priyaSaw::trySend) },
                )
                val priyaJob = launch { priya.run() }
                val priyaSeat = seatedSeats.receive()

                // --- both here, neither has handed anything over -------------------------
                publish()
                assertEquals(LobbyBody.Standing(joined = 2, linesIn = 0), elliotSaw.receive())
                assertEquals(LobbyBody.Standing(joined = 2, linesIn = 0), priyaSaw.receive())

                // --- one line goes up, inside a Carry the frame layer cannot read ---------
                elliot.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Handover(elliotSecret))))
                assertEquals(elliotSeat, filed.receive(), "the host filed the line under another seat")
                publish()
                assertEquals(LobbyBody.Standing(joined = 2, linesIn = 1), elliotSaw.receive())
                assertEquals(LobbyBody.Standing(joined = 2, linesIn = 1), priyaSaw.receive())

                priya.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Handover(priyaSecret))))
                assertEquals(priyaSeat, filed.receive())
                publish()
                assertEquals(LobbyBody.Standing(joined = 2, linesIn = 2), elliotSaw.receive())
                assertEquals(LobbyBody.Standing(joined = 2, linesIn = 2), priyaSaw.receive())

                // --- the house has both, and neither phone has the other's ----------------
                assertNull(elliotSaw.tryReceive().getOrNull(), "a phone was sent more than the standing")
                assertNull(priyaSaw.tryReceive().getOrNull())

                // --- a phone leaving takes its line with it -------------------------------
                priyaJob.cancelAndJoin()
                assertEquals(priyaSeat, lostSeats.receive())
                publish()
                assertEquals(LobbyBody.Standing(joined = 1, linesIn = 1), elliotSaw.receive())

                elliotJob.cancelAndJoin()
                assertEquals(0, host.malformedCount(), "something on the wire was not a frame")
            }
        } finally {
            host.stop()
        }
    }

    /**
     * The downward body is not the upward one, and no host can turn it into the upward one by
     * setting a field. Stated here as well as in `LobbyWireTest` because this is the file
     * somebody reads when they are about to add "just the names" to the lobby.
     */
    @Test
    fun theStandingHasNowhereToPutALine() {
        val text = LobbyWire.encode(LobbyBody.Standing(joined = 8, linesIn = 8, insiders = 2))
        assertFalse("\"line\":" in text, "the standing grew a text field: $text")
        assertFalse("\"name" in text, "the standing grew a name: $text")
    }
}
