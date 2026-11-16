package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.core.reduce
import home.someoneshome.model.Balance
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
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
import kotlin.test.assertTrue

/**
 * **R1, measured on the sockets: every touch puts one frame of one length on each phone that
 * touched, whatever its role and whether or not it was armed** (D-216).
 *
 * Effect-level tests say which effect each actor is answered with; this says what a reader of the
 * Wi-Fi would see. A real [TransportHost], real [TransportClient]s over loopback, the real
 * [EffectWire], and the rules' own `admit` for both halves of every touch, exactly as the resolver
 * mints them (D-213). What each client's socket received is re-encoded with `TransportWire`, which
 * is deterministic and is what the host wrote, so a length here is a length on the air.
 *
 * Three touches, each against a live Resident:
 * - **an unarmed Insider** (seat 1) and a Resident (seat 3);
 * - **two Residents** (seats 2 and 3);
 * - **an armed Insider** (seat 1) and a Resident (seat 2), which revokes seat 2.
 *
 * The toucher's frame is the same length in all three, and so is the Resident partner's in the two
 * that revoke nobody. **The partner of the armed touch is now Revoked and is sent `RevokeLanded`, and
 * since S1 (D-219) that is one frame of the same length too**: it was 95 bytes, and on a measurable
 * Wi-Fi the short frame was the phone that had just been Revoked.
 *
 * **And since T1 (D-220) the count is one in both seat orders.** The same touches are then made with
 * the Insider seated *above* its partner (seat 1 and seat 0), unarmed and armed: before T1 the
 * lower-seated target was sent its own `ContactAnswered` and then `RevokeLanded`, two frames where
 * every other toucher got one. Every socket's count is asserted per touch, in a table, for all four
 * cases. So every phone that touched, in every touch, receives exactly one frame of one size.
 */
class TouchFrameSizeTest {

    private val seats = (0..4).map { Seat(it) }
    private val insider = Seat(1)

    @Test
    fun everyTouchPutsOneFrameOfOneLengthOnEachTouchingPhone() = runBlocking {
        var state: GameState = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = listOf(insider)),
        ).state
        var tick = Balance.REVOKE_COOLDOWN / 2 + 1

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
            // One phone per seat, joined in seat order so the ledger hands seat i to phone i.
            for (seat in seats) {
                val sink = received.getValue(seat.index)
                val client = TransportClient(
                    ClientSession(), "127.0.0.1", port,
                    nowMillis = { monotonicNanos() / 1_000_000 },
                    onFrame = { frame ->
                        if (frame is TransportFrame.Carry && RoundWire.decodeOrNull(frame.body) is RoundBody.Push) {
                            sink.trySend(TransportWire.encode(frame))
                        }
                    },
                )
                jobs += launch { client.run() }
                assertEquals(seat, withTimeout(30_000) { seated.receive() })
            }

            suspend fun put(event: Event) {
                val admission = admit(state, event)
                check(admission is Admission.Admitted) { "the fixture's $event was refused: $admission" }
                state = admission.reduction.state
                for (effect in admission.reduction.effects) wire.emit(effect, state)
            }

            /** Both halves of a touch, in seat order as the resolver mints them; then what each socket got. */
            suspend fun touch(a: Seat, b: Seat): Map<Int, List<String>> {
                val (low, high) = if (a.index < b.index) a to b else b to a
                put(Event.ContactMade(Tick(tick), low, high))
                put(Event.ContactMade(Tick(tick), high, low))
                tick++
                // Every frame of a touch is written before `put` returns; loopback delivers it soon
                // after. Settle, then drain.
                delay(SETTLE_MILLIS)
                return seats.associate { s ->
                    s.index to generateSequence { received.getValue(s.index).tryReceive().getOrNull() }.toList()
                }
            }

            val unarmed = touch(insider, Seat(3))
            val residents = touch(Seat(2), Seat(3))
            put(Event.RevokeArmed(Tick(tick++), insider))
            assertTrue(state.cooldownArmed.any { it.index == insider.index }, "the fixture never armed the Insider")
            val armed = touch(insider, Seat(2))
            assertTrue(state.isRevoked(Seat(2)), "the armed touch revoked nobody, so it measured nothing")

            val unarmedInsider = unarmed.getValue(1)
            val unarmedPartner = unarmed.getValue(3)
            val residentA = residents.getValue(2)
            val residentB = residents.getValue(3)
            val armedInsider = armed.getValue(1)
            val revoked = armed.getValue(2)

            for ((who, frames) in listOf(
                "an unarmed Insider" to unarmedInsider,
                "the unarmed Insider's Resident partner" to unarmedPartner,
                "a Resident touching a Resident" to residentA,
                "the other Resident" to residentB,
                "an armed Insider" to armedInsider,
                "the armed Insider's Revoked target" to revoked,
            )) {
                assertEquals(1, frames.size, "$who's socket got ${frames.size} frames for one touch: $frames")
            }

            val lengths = mapOf(
                "unarmed Insider" to unarmedInsider.single().length,
                "unarmed Insider's partner" to unarmedPartner.single().length,
                "Resident" to residentA.single().length,
                "Resident partner" to residentB.single().length,
                "armed Insider" to armedInsider.single().length,
                "Revoked target" to revoked.single().length,
            )
            assertEquals(
                1, lengths.values.toSet().size,
                "a touch's frame length follows the role or the arm: $lengths",
            )
            println("R1 FRAME BYTES — $lengths")

            // Content: the armed one is the spent Revoke, the rest are the padded answer.
            fun messageOf(frame: String): RoundMessage? = (TransportWire.decodeOrNull(frame) as? TransportFrame.Carry)
                ?.let { (RoundWire.decodeOrNull(it.body) as? RoundBody.Push)?.message }
            assertEquals(RoundMessage.AbilityFired(cooldownStarted = true), messageOf(armedInsider.single()))
            for (frame in unarmedInsider + unarmedPartner + residentA + residentB) {
                assertEquals(RoundMessage.ContactAnswered(RoundMessage.ContactAnswered.PAD), messageOf(frame))
            }

            // Nobody who did not touch was sent anything by any of the three.
            for (map in listOf(unarmed, residents, armed)) {
                for (bystander in listOf(0, 4)) {
                    assertEquals(emptyList(), map.getValue(bystander), "seat $bystander did not touch and was sent a frame")
                }
            }

            // The Revoked partner's one frame is the Revoke landing, padded to the touch's size (S1).
            assertEquals(listOf<RoundMessage?>(RoundMessage.RevokeLanded.Padded), revoked.map(::messageOf))
            println("S1 REVOKED PARTNER — ${revoked.single().length} BYTES (RevokeLanded, padded)")

            // **Both seat orders, armed and unarmed: one frame per touching socket** (T1, D-220).
            // Until T1, when the target sat below the Insider the resolver's first half was the
            // target's own: still living and unarmed, it was answered `ContactAnswered`, and the
            // Insider's half then sent `RevokeLanded`. Now the first half answers the whole touch.
            val unarmedAbove = touch(insider, Seat(0))
            assertTrue(state.revoked.none { it.index == 0 }, "the unarmed touch revoked seat 0")
            tick += Balance.REVOKE_COOLDOWN + 1
            put(Event.RevokeArmed(Tick(tick++), insider))
            assertTrue(state.cooldownArmed.any { it.index == insider.index }, "the fixture never re-armed the Insider")
            val armedAbove = touch(insider, Seat(0))
            assertTrue(state.isRevoked(Seat(0)), "the second armed touch revoked nobody, so it measured nothing")

            // The table: every socket's frame count, per touch. Touchers 1, bystanders 0.
            val cases = listOf(
                Triple("unarmed, Insider below its partner", unarmed, setOf(1, 3)),
                Triple("unarmed, Insider above its partner", unarmedAbove, setOf(0, 1)),
                Triple("armed, Insider below its target", armed, setOf(1, 2)),
                Triple("armed, Insider above its target", armedAbove, setOf(0, 1)),
            )
            val counts = cases.associate { (label, got, _) -> label to got.mapValues { it.value.size } }
            println("T1 FRAMES PER SOCKET — $counts")
            for ((label, got, touchers) in cases) {
                for ((seat, frames) in got) {
                    assertEquals(
                        if (seat in touchers) 1 else 0, frames.size,
                        "$label: seat $seat's socket got ${frames.size} frames for one touch: ${frames.map(::messageOf)}",
                    )
                }
            }
            assertEquals(
                listOf<RoundMessage?>(RoundMessage.RevokeLanded.Padded), armedAbove.getValue(0).map(::messageOf),
                "the lower-seated target was not sent exactly its Revoke landing",
            )
            assertEquals(
                listOf<RoundMessage?>(RoundMessage.AbilityFired(cooldownStarted = true)), armedAbove.getValue(1).map(::messageOf),
                "the higher-seated armed Insider was not sent exactly its spent Revoke",
            )
            for (frame in unarmedAbove.getValue(0) + unarmedAbove.getValue(1)) {
                assertEquals(RoundMessage.ContactAnswered(RoundMessage.ContactAnswered.PAD), messageOf(frame))
            }
            val allLengths = cases.flatMap { (_, got, _) -> got.values.flatten().map { it.length } }.toSet()
            assertEquals(setOf(lengths.values.first()), allLengths, "a touch frame in one seat order is another size: $allLengths")
            println("T1 LOWER-SEATED TARGET — ${armedAbove.getValue(0).size} FRAME OF ${armedAbove.getValue(0).map { it.length }} BYTES")
        } finally {
            jobs.forEach { it.cancel() }
            host.stop()
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 400L
    }
}
