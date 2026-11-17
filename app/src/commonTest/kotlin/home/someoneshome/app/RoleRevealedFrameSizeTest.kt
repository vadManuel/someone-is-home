package home.someoneshome.app

import home.someoneshome.core.reduce
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.EffectWire
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Every seat's reveal is one frame of one length on the Wi-Fi, whatever its role and whatever
 * its line** (D-249).
 *
 * Each seat is sent exactly one `RoleRevealed`, at the same place in the arming burst, so its size
 * is a reading of the role. [TouchFrameSizeTest]'s method: a real [TransportHost], real
 * [TransportClient]s over loopback, the real [EffectWire], and the rules' own arming, with what
 * each socket received re-encoded by `TransportWire` (deterministic, and what the host wrote).
 *
 * Five seats, seat 1 the Insider, armed once per line: blank, one character, the longest line the
 * lobby takes ([LobbyBody.Handover.LIMIT] characters), the longest in bytes (three-byte characters),
 * one JSON escapes, and one past the limit. Every Resident hands over a line too, so a Resident
 * frame that carried its owner's words would be a different length as well as a leak.
 */
class RoleRevealedFrameSizeTest {

    private val seats = (0..4).map { Seat(it) }
    private val insider = Seat(1)

    private val lines = mapOf(
        "blank" to "",
        "one character" to "x",
        "the longest line" to "k".repeat(LobbyBody.Handover.LIMIT),
        "the longest in bytes" to "鍵".repeat(LobbyBody.Handover.LIMIT),
        "one JSON escapes" to "\"\\\n\t\u0001 said \"no\"",
        "past the limit" to "鍵".repeat(LobbyBody.Handover.LIMIT * 2),
    )

    @Test
    fun anInsidersRevealAndAResidentsAreOneLengthOnTheWire() = runBlocking {
        val lengths = mutableMapOf<String, Map<String, Int>>()
        for ((label, line) in lines) {
            val frames = armOverLoopback(line)
            val insiderFrame = frames.getValue(insider.index)
            val decoded = message(insiderFrame)
            assertEquals(true, decoded.insider, "$label: seat 1's reveal did not say Insider")
            assertEquals(
                RoundMessage.RoleRevealed(true, line).line, decoded.line,
                "$label: the Insider's own words did not survive the socket",
            )
            if (line.encodeToByteArray().size <= RoundMessage.RoleRevealed.LINE_BYTES) {
                assertEquals(line, decoded.line, "$label: the Insider's line came back changed")
            }
            for ((seat, frame) in frames) {
                if (seat == insider.index) continue
                val resident = message(frame)
                assertEquals(false, resident.insider, "$label: seat $seat's reveal said Insider")
                assertEquals("", resident.line, "$label: seat $seat (Resident) was sent words")
            }
            lengths[label] = frames.entries.associate { (seat, frame) ->
                (if (seat == insider.index) "Insider" else "Resident $seat") to frame.encodeToByteArray().size
            }
        }
        println("D-249 ROLE REVEALED FRAME BYTES — $lengths")
        val every = lengths.values.flatMap { it.values }.toSet()
        assertEquals(
            1, every.size,
            "a reveal's frame length follows the role or the line: $lengths",
        )
    }

    private fun message(frame: String): RoundMessage.RoleRevealed {
        val carry = TransportWire.decodeOrNull(frame) as TransportFrame.Carry
        return (RoundWire.decodeOrNull(carry.body) as RoundBody.Push).message as RoundMessage.RoleRevealed
    }

    /** One round armed over a fresh house and five fresh phones; each seat's one reveal frame. */
    private suspend fun armOverLoopback(insiderLine: String): Map<Int, String> {
        val received = seats.associate { it.index to Channel<String>(Channel.UNLIMITED) }
        val seated = Channel<Seat>(Channel.UNLIMITED)
        var minted = 0
        val host = TransportHost(
            SeatLedger(seats, mint = { SeatToken("tk-${minted++}") }),
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = { _, _ -> },
            onSeated = { seated.trySend(it) },
        )
        val wire = EffectWire(send = { seat, frame -> host.send(seat, frame) })
        val port = host.start(0)
        val jobs = mutableListOf<Job>()
        try {
            return kotlinx.coroutines.coroutineScope {
                for (seat in seats) {
                    val sink = received.getValue(seat.index)
                    val client = TransportClient(
                        ClientSession(), "127.0.0.1", port,
                        nowMillis = { monotonicNanos() / 1_000_000 },
                        onFrame = { frame ->
                            val push = (frame as? TransportFrame.Carry)?.let { RoundWire.decodeOrNull(it.body) as? RoundBody.Push }
                            if (push?.message is RoundMessage.RoleRevealed) sink.trySend(TransportWire.encode(frame))
                        },
                    )
                    jobs += launch { client.run() }
                    assertEquals(seat, withTimeout(30_000) { seated.receive() })
                }
                val armed = reduce(
                    GameState.EMPTY,
                    Event.RoundArmed(
                        Tick(0), seed = 42L, seats = seats, insiders = listOf(insider),
                        oneLines = seats.map { OneLineHeld(it, if (it == insider) insiderLine else "resident ${it.index}'s own secret") },
                    ),
                )
                for (effect in armed.effects) wire.emit(effect, armed.state)
                val frames = seats.associate { s ->
                    s.index to withTimeout(30_000) { received.getValue(s.index).receive() }
                }
                // Exactly one each: a second reveal would be a count that follows something.
                delay(SETTLE_MILLIS)
                for (s in seats) {
                    assertEquals(null, received.getValue(s.index).tryReceive().getOrNull(), "seat ${s.index} got two reveals")
                }
                jobs.forEach { it.cancel() }
                frames
            }
        } finally {
            jobs.forEach { it.cancel() }
            host.stop()
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
    }
}
