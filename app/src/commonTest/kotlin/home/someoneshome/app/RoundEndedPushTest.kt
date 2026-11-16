package home.someoneshome.app

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.LobbyDesk
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **NEW ROUND returns every seat — E-R2-10, and E-P3-5 before it.**
 *
 * L6 made NEW ROUND the actions layer's control, drawn only where this phone is the host, which is
 * right. What followed is that `LobbyLink.roundEnded()` is a call into the desk sitting beside the
 * host, and on a client the desk is not there: the host walked back to the lobby and **four phones
 * stayed on the ending until somebody handed them back.** Two rehearsals measured it, the second
 * one with a number on it — `THE LINES AFTER THE WIPE — 4 OF 5`.
 *
 * *What is missing is a push, not a button*, and `LobbyBody.RoundEnded` is the push. This is the
 * client's half of it: a phone that receives it drops what it holds of the round and walks back to
 * the lobby, having been told nothing else.
 *
 * ### It drives a real socket and a real [LobbyOverTransport]
 *
 * `StaleSeatTest`'s rig, and for its reason: the thing under test is what a phone does with a frame
 * that arrived, so a fixture standing in for the frame would be asserting the behaviour one layer
 * below where it has to hold. The **host** here is hand-rolled rather than a second
 * [LobbyOverTransport], because bringing the real house up needs the radio — that is E-P2-3's
 * missing seam and it is not this test's to build. What that leaves uncovered is named at the
 * bottom of this file rather than left to be discovered.
 */
class RoundEndedPushTest {

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    /** What the round's client half is asked to do, recorded rather than performed. */
    private class Seam : RoundSeam {
        var ended = 0
        override fun linked(send: suspend (TransportFrame) -> Unit) = Unit
        override fun arrived(frame: TransportFrame) = Unit
        override fun seated() = Unit
        override fun roundEnded() {
            ended++
        }
        var left = 0
        override fun left() {
            left++
        }
    }

    /**
     * **A phone that leaves tells its round half, so the radio stops and the screen may sleep**
     * (T4, D-221). Before T4 nothing reached the round on a leave: its radio stayed on, and with the
     * screen now held awake through a round, the screen would have too.
     */
    @Test
    fun aClientThatLeavesTellsItsRoundHalf() = runBlocking {
        var minted = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${minted++}") })
        val seated = Channel<Seat>(Channel.UNLIMITED)
        val host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { _, _ -> },
            onSeated = { seated.trySend(it) },
        )
        val port = host.start(0)
        val seam = Seam()
        val link = LobbyOverTransport(this, round = seam, wake = ScreenWake(NoIdleTimer))
        val model = LobbyModel(link = link)
        model.nameResident("ELLIOT")
        try {
            withTimeout(30_000) {
                model.attachTo(NearbyHome("THE BUNGALOW", "127.0.0.1", port))
                seated.receive()
            }
            assertEquals(0, seam.left, "the round half was told it left before anybody left")
            link.leave()
            assertEquals(1, seam.left, "a phone that left never told its round half: its radio and screen stay on")
        } finally {
            host.stop()
        }
    }

    @Test
    fun theHousesRoundEndedPushReturnsAClientToTheLobby() = runBlocking {
        var minted = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${minted++}") })
        val desk = LobbyDesk()
        val seated = Channel<Seat>(Channel.UNLIMITED)
        lateinit var host: TransportHost

        host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { seat, frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull)
                if (body is LobbyBody.Naming) desk.named(seat, body.name)
            },
            onSeated = { desk.seated(it); seated.trySend(it) },
            onLost = { desk.left(it) },
        )
        val port = host.start(0)

        val seam = Seam()
        var returned = 0
        val link = LobbyOverTransport(
            this,
            round = seam,
            // The screen answer, which is the panel's in the app root. Counted here rather than
            // performed, so this test says *the transport reported it* and `FlowModel.newRound` is
            // asserted where it lives.
            onRoundEnded = { returned++ },
            wake = ScreenWake(NoIdleTimer),
        )
        val model = LobbyModel(link = link)
        model.nameResident("ELLIOT")
        try {
            withTimeout(30_000) {
                model.attachTo(NearbyHome("THE BUNGALOW", "127.0.0.1", port))
                val seat = seated.receive()
                model.typeLine("I told them it was mine")
                assertTrue(model.handOverLine(), "the fixture never handed a line over")

                // **The evening turns over.** One frame, carrying nothing, down the socket this
                // phone is already on.
                host.send(seat, TransportFrame.Carry(LobbyWire.encode(LobbyBody.RoundEnded)))
            }

            // **Bounded, and it fails with a sentence rather than by hanging.** `StaleSeatTest`
            // keeps this rule and the injection that drops the client's branch is why: a bare wait
            // inside the timeout above made a build that never returns fail after thirty seconds
            // with `TimeoutCancellationException` and no words on it — a guard that hangs is a
            // guard somebody eventually reads as infrastructure. This waits, gives up, and says
            // what did not happen.
            withTimeoutOrNull(10_000) { while (returned == 0) delay(20) }
            assertEquals(1, returned, "the push arrived and the panel was never told")
            assertEquals(
                1, seam.ended,
                "the round's inbox kept last round's sequence, so the next round's first pushes " +
                    "would be refused as frames this phone already held",
            )
        } finally {
            link.leave()
            host.stop()
        }
    }

    /**
     * **The push is downward only, and a client cannot end anybody's round.**
     *
     * The host's `onFrame` names it as a body it will not act on. Asserted here at the level a
     * forged frame would actually arrive at: a client that could end the round would be one phone
     * wiping the desk's lines and returning five other people to the lobby, mid-round, with no
     * control anywhere that asked for it.
     */
    @Test
    fun aClientCannotEndTheRoundByPushingOneUpward() = runBlocking {
        var minted = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${minted++}") })
        val desk = LobbyDesk()
        val seated = Channel<Seat>(Channel.UNLIMITED)
        val acted = Channel<String>(Channel.UNLIMITED)
        lateinit var host: TransportHost

        host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            // The host half of `LobbyHouse.onFrame`, in the shape that class has it: the two
            // upward bodies are served and every downward one is declined.
            onFrame = { seat, frame ->
                when ((frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull)) {
                    is LobbyBody.Naming -> acted.trySend("naming")
                    is LobbyBody.Handover -> acted.trySend("handover")
                    is LobbyBody.Standing, is LobbyBody.RoundEnded, is LobbyBody.HouseClosed,
                    null,
                    -> Unit
                }
                if (seat.index < 0) error("unreachable")
            },
            onSeated = { desk.seated(it); seated.trySend(it) },
            onLost = { desk.left(it) },
        )
        val port = host.start(0)

        val link = LobbyOverTransport(this, wake = ScreenWake(NoIdleTimer))
        val model = LobbyModel(link = link)
        model.nameResident("ELLIOT")
        try {
            withTimeout(30_000) {
                model.attachTo(NearbyHome("THE BUNGALOW", "127.0.0.1", port))
                seated.receive()
                // The naming arrives on every seating, so it is the fixture's proof the socket is
                // carrying anything at all — without it a silent `acted` proves nothing.
                assertEquals("naming", acted.receive(), "nothing this phone said reached the house")
            }
            assertEquals(
                null, acted.tryReceive().getOrNull(),
                "the host acted on a body only it is allowed to send",
            )
        } finally {
            link.leave()
            host.stop()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ⚠️ WHAT THIS FILE DOES NOT COVER, named rather than left to be found:
//
// **The HOST's half — `LobbyHouse.roundEnded` broadcasting to every connected seat.** The host
// above is hand-rolled, so what is proved here is that a client acts on the frame, not that the
// real house sends it. Bringing `LobbyOverTransport`'s host up without the radio is E-P2-3's
// missing seam and it is the same gap `LobbyHouse.emit` and `onIntent` are already recorded as
// having. The broadcast itself shares one loop with the standing's (`announce`), which every
// evening exercises.
// ---------------------------------------------------------------------------------------------
