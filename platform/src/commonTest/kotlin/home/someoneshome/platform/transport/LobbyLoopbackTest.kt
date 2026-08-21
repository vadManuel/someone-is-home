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
import kotlin.test.assertTrue

/**
 * **The lobby over an actual socket** — two phones, one host, loopback interface.
 *
 * [TransportLoopbackTest] proves the machinery survives Ktor. This proves the lobby that rides on
 * it: two pieces of text go up from each phone inside opaque `Carry` bodies, the host files them,
 * and **one of the two comes back down to everybody while the other never comes back at all.** No
 * BLE, no torch, no lamp — nothing the Simulator licence forbids certifying, and nothing here
 * needs a phone at all.
 *
 * The property worth a socket rather than a unit test: **the exchange is asymmetric by
 * construction.** Two bodies go up and a third comes down, and the third is not either of them —
 * so a client cannot be handed somebody else's line by a host that filled in the wrong field.
 * D-115 widened what comes down, once and on purpose; what it widened it with is names, and this
 * test runs both texts through the same socket at the same time to say which one survives the trip.
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
    fun twoPhonesHandOverAndBothSeeNamesButNoLines() = runBlocking {
        var mintN = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${mintN++}") })
        val desk = LobbyDesk()
        val seatedSeats = Channel<Seat>(Channel.UNLIMITED)
        val lostSeats = Channel<Seat>(Channel.UNLIMITED)
        val filed = Channel<Seat>(Channel.UNLIMITED)
        val namedSeats = Channel<Seat>(Channel.UNLIMITED)

        val host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { seat, frame ->
                when (val body = (frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull)) {
                    is LobbyBody.Handover -> {
                        desk.handedOver(seat, body.line)
                        filed.trySend(seat)
                    }
                    is LobbyBody.Naming -> {
                        desk.named(seat, body.name)
                        namedSeats.trySend(seat)
                    }
                    // The standing is the host's to send, never to receive.
                    is LobbyBody.Standing, null -> Unit
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

                // --- both here, neither has said anything yet -----------------------------
                val nobody = LobbyBody.Standing(joined = 2, linesIn = 0, names = listOf("", ""))
                publish()
                assertEquals(nobody, elliotSaw.receive())
                assertEquals(nobody, priyaSaw.receive())

                // --- the names go up, and come back down to both phones -------------------
                elliot.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Naming("ELLIOT"))))
                assertEquals(elliotSeat, namedSeats.receive(), "the host filed a name under another seat")
                priya.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Naming("PRIYA"))))
                assertEquals(priyaSeat, namedSeats.receive())
                val both = listOf("ELLIOT", "PRIYA")
                publish()
                assertEquals(LobbyBody.Standing(2, 0, both), elliotSaw.receive())
                assertEquals(LobbyBody.Standing(2, 0, both), priyaSaw.receive())

                // --- one line goes up, inside a Carry the frame layer cannot read ---------
                elliot.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Handover(elliotSecret))))
                assertEquals(elliotSeat, filed.receive(), "the host filed the line under another seat")
                publish()
                assertEquals(LobbyBody.Standing(2, 1, both), elliotSaw.receive())
                assertEquals(LobbyBody.Standing(2, 1, both), priyaSaw.receive())

                priya.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Handover(priyaSecret))))
                assertEquals(priyaSeat, filed.receive())
                publish()
                // **The one that matters**: the desk now holds a name and a line for each seat,
                // it has published four times, and the only text either phone has ever been sent
                // is the two names. Asserted against the encoded frames rather than the objects,
                // because the objects are what a serializer is free to disagree with.
                assertEquals(LobbyBody.Standing(2, 2, both), elliotSaw.receive())
                assertEquals(LobbyBody.Standing(2, 2, both), priyaSaw.receive())
                for (secret in listOf(elliotSecret, priyaSecret)) {
                    for (word in secret.split(" ").filter { it.length > 4 }) {
                        assertFalse(
                            word in LobbyWire.encode(desk.standing()),
                            "'$word' from a one line reached the wire",
                        )
                    }
                }

                // --- the house has both, and neither phone has the other's ----------------
                assertNull(elliotSaw.tryReceive().getOrNull(), "a phone was sent more than the standing")
                assertNull(priyaSaw.tryReceive().getOrNull())

                // --- a phone leaving takes its line and its name with it ------------------
                priyaJob.cancelAndJoin()
                assertEquals(priyaSeat, lostSeats.receive())
                publish()
                assertEquals(
                    LobbyBody.Standing(1, 1, listOf("ELLIOT")), elliotSaw.receive(),
                    "a phone that walked out is still on the lobby screen",
                )

                // --- and coming back is coming back unnamed, until it says so again --------
                //
                // The seat waits for its token, so a resume returns to *that* seat — but the desk
                // dropped the name with the connection, which is why the name is announced on
                // every seating rather than once. Without the re-announcement the lobby lists an
                // empty chip for somebody standing in the room, and nothing on the screen would
                // explain why.
                val priyaAgain = TransportClient(
                    priyaSession, "127.0.0.1", port, ::nowMillis,
                    onFrame = { standingIn(it)?.let(priyaSaw::trySend) },
                )
                val priyaAgainJob = launch { priyaAgain.run() }
                assertEquals(priyaSeat, seatedSeats.receive(), "a resume was given a different seat")
                publish()
                assertEquals(
                    LobbyBody.Standing(2, 1, listOf("ELLIOT", "")), elliotSaw.receive(),
                    "a resumed phone came back already named",
                )
                priyaAgain.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Naming("PRIYA"))))
                assertEquals(priyaSeat, namedSeats.receive())
                publish()
                assertEquals(LobbyBody.Standing(2, 1, listOf("ELLIOT", "PRIYA")), elliotSaw.receive())

                priyaAgainJob.cancelAndJoin()
                elliotJob.cancelAndJoin()
                assertEquals(0, host.malformedCount(), "something on the wire was not a frame")
            }
        } finally {
            host.stop()
        }
    }

    /**
     * The downward body is not either upward one, and no host can turn it into one by setting a
     * field. Stated here as well as in `LobbyWireTest` because this is the file somebody reads
     * when they are about to add "just the lines" to the lobby — the names went in by a ruling
     * (D-115), and the next thing to go in has to go in the same way.
     */
    @Test
    fun theStandingHasNowhereToPutALine() {
        val text = LobbyWire.encode(
            LobbyBody.Standing(joined = 8, linesIn = 8, names = listOf("ROSE"), insiders = 2),
        )
        assertFalse("\"line\"" in text, "the standing grew a field for the one line: $text")
        assertFalse("\"lines\"" in text, "the standing grew a field for the one lines: $text")
        assertTrue("\"names\"" in text, "the standing stopped carrying the names D-115 put in it")
    }
}
