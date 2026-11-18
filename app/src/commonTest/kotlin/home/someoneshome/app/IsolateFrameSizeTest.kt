package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.core.reduce
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **At arming and when an Egress starts, every living phone is sent the same number of frames of
 * the same lengths, whatever its role** (D-258; R1's principle, D-216, D-219, D-249).
 *
 * [TouchFrameSizeTest]'s method: a real [TransportHost], five real [TransportClient]s over
 * loopback, the real [EffectWire], the rules' own arming and admission, and what each socket
 * received re-encoded by `TransportWire`, which is what the host wrote.
 *
 * The home's rooms are named with characters JSON escapes and characters UTF-8 takes several
 * bytes for, so an Isolate list whose width followed its content would show it.
 *
 * - **Arming, twice, with seats 1 and 2 exchanging roles.** Each seat's sequence of frame lengths
 *   is the same in both runs: nothing in the burst follows the role, in count or in size. Within
 *   a run every seat gets the same number of frames, and the Isolate frame (the offer on an
 *   Insider's, the blank on a Resident's) is one length at one place in every stream.
 * - **An Egress starting.** Every phone gets the same frames, byte for byte net of the sequence
 *   number's digits, the other Insider's and the actor's included: the actor's first is its own
 *   `AbilityFired`, and everybody else's is a blank of its length (D-263).
 * - **The actor's two answers, `true` and `false`.** A second fire into the running cooldown is
 *   told `false`, and that frame is the length of the first fire's `true` (the digit, D-258).
 *
 * The Egress frames are compared net of the sequence number's digits: that is D-216's open item, a
 * counter that grows with what a seat has been sent, and only fixed-size frames close it (S2).
 */
class IsolateFrameSizeTest {

    private val seats = (0..4).map { Seat(it) }

    /** Six cards in rooms whose names escape, or take several bytes, or both. */
    private val markers = listOf(
        "KITCHEN", "KID'S \"DEN\"", "BATH\\ROOM", "ÉTAGE ü 鍵", "HALL\n2", "LANDING\t\u0001",
    ).mapIndexed { i, room -> Destination(MarkerId("card-$i"), MarkerShapes.all[i + 1].id, room) }

    @Test
    fun everyLivingPhoneGetsOneIsolateFrameOfOneLengthAtArmingAndAtAnEgress() = runBlocking {
        val asDrawn = playOverLoopback(insiders = listOf(Seat(1), Seat(4)), actor = Seat(1))
        val exchanged = playOverLoopback(insiders = listOf(Seat(2), Seat(4)), actor = Seat(2))

        // ---- Arming: the whole burst, compared with the roles exchanged ------------------------
        println("D-258 ARMING FRAME BYTES, INSIDERS 1,4 — ${asDrawn.arming.mapValues { it.value.lengths }}")
        println("D-258 ARMING FRAME BYTES, INSIDERS 2,4 — ${exchanged.arming.mapValues { it.value.lengths }}")
        for (seat in seats) {
            assertEquals(
                asDrawn.arming.getValue(seat.index).lengths, exchanged.arming.getValue(seat.index).lengths,
                "seat ${seat.index}'s arming frames follow its role: " +
                    "as an ${if (seat.index == 1) "Insider" else if (seat.index == 2) "Resident" else "unchanged role"} " +
                    "${asDrawn.arming.getValue(seat.index).kinds} / exchanged ${exchanged.arming.getValue(seat.index).kinds}",
            )
        }
        for (run in listOf(asDrawn, exchanged)) {
            val counts = run.arming.mapValues { it.value.frames.size }
            assertEquals(1, counts.values.toSet().size, "the arming burst's frame count follows the seat: $counts")
            val isolate = run.arming.mapValues { (_, got) -> got.isolate() }
            for ((seat, at) in isolate) {
                val (index, message) = at
                if (seat in run.insiders) {
                    assertTrue(message is RoundMessage.IsolateTargetsOffered, "Insider $seat was not offered: $message")
                    assertEquals(run.state.activeMarkers.size, message.targets.size, "Insider $seat's list lost cards")
                } else {
                    assertTrue(message is RoundMessage.IsolateTargetsBlanked, "Resident $seat was not sent the blank: $message")
                }
                assertEquals(isolate.values.first().first, index, "seat $seat's Isolate frame is at another place in its stream")
            }
            val lengths = run.arming.mapValues { (_, got) -> got.frames[got.isolate().first].length }
            println("D-258 ISOLATE FRAME BYTES AT ARMING — $lengths")
            assertEquals(1, lengths.values.toSet().size, "the Isolate frame's length follows the role: $lengths")
        }

        // ---- An Egress starting ----------------------------------------------------------------
        for (run in listOf(asDrawn, exchanged)) {
            println("D-258 EGRESS FRAME BYTES, ACTOR ${run.actor.index} — ${run.egress.mapValues { it.value.lengths }}")
            val actor = run.egress.getValue(run.actor.index)
            assertTrue(
                actor.messages.first() is RoundMessage.AbilityFired,
                "the actor's first frame was not its own answer: ${actor.kinds}",
            )
            // Every phone but the actor's: the same frames, byte for byte, Insider or Resident.
            val others = run.egress.filterKeys { it != run.actor.index }
            val bystander = others.values.first()
            for ((seat, got) in others) {
                assertEquals(
                    bystander.lengths, got.lengths,
                    "at an Egress, seat $seat (${if (seat in run.insiders) "Insider" else "Resident"}) was sent other " +
                        "frames than seat ${others.keys.first()}: ${got.kinds} against ${bystander.kinds}",
                )
            }
            assertTrue(
                others.keys.any { it in run.insiders } && others.keys.any { it !in run.insiders },
                "the bystanders were all one role, so this compared nothing",
            )
            // The actor: its own answer where every other phone has its blank, then the same frames
            // (D-263). Before D-263 the answer was one frame more on this socket alone. Net of the
            // sequence number's digits, for D-216's item: the counter follows what a seat was sent.
            for ((seat, got) in others) {
                assertTrue(got.messages.first() is RoundMessage.AbilityBlanked, "seat $seat's first frame was not the blank: ${got.kinds}")
            }
            assertEquals(
                bystander.netLengths, actor.netLengths,
                "at an Egress the actor was sent other frames than everybody else: ${actor.kinds} against ${bystander.kinds}",
            )
            for ((seat, got) in run.egress) {
                val message = got.messages[got.isolate().first]
                assertTrue(
                    if (seat in run.insiders) message is RoundMessage.IsolateTargetsOffered
                    else message is RoundMessage.IsolateTargetsBlanked,
                    "seat $seat's Isolate frame at the Egress was $message",
                )
            }
            // The two nodes left the Insiders' list, and the blank shrank with it.
            val relisted = run.egress.getValue(run.insiders.last()).messages
                .filterIsInstance<RoundMessage.IsolateTargetsOffered>().single()
            assertEquals(run.state.activeMarkers.size - 2, relisted.targets.size, "the nodes stayed on the list")

            // ---- The actor's two answers ----------------------------------------------------------
            val (startedFrame, startedSeq) = run.started
            val (runningFrame, runningSeq) = run.intoRunning
            // Net of the sequence number's digits (seq $startedSeq against $runningSeq): the counter
            // is D-216's item, and what is measured here is the answer.
            val started = startedFrame.length - startedSeq.toString().length
            val running = runningFrame.length - runningSeq.toString().length
            println(
                "D-258 ABILITY FIRED BYTES — started ${startedFrame.length} at seq $startedSeq, into a running " +
                    "cooldown ${runningFrame.length} at seq $runningSeq",
            )
            assertEquals(
                started, running,
                "whether the Egress request took is readable off its answer's size:\n$startedFrame\n$runningFrame",
            )
        }
    }

    /** What one socket received for one moment of the round. */
    private class Got(val frames: List<String>) {
        val messages: List<RoundMessage> = frames.map(::messageOf)
        val lengths: List<Int> get() = frames.map { it.length }

        /** Each frame's length less its sequence number's digits: what the message itself costs. */
        val netLengths: List<Int> get() = frames.map { it.length - seqOf(it).toString().length }
        val kinds: List<String> get() = messages.map { it.kind.name }

        /** The Isolate frame: where it is in this stream, and what it carried. */
        fun isolate(): Pair<Int, RoundMessage> = messages.withIndex()
            .single { it.value is RoundMessage.IsolateTargetsOffered || it.value is RoundMessage.IsolateTargetsBlanked }
            .let { it.index to it.value }
    }

    private class Run(
        val insiders: List<Int>,
        val actor: Seat,
        val state: GameState,
        val arming: Map<Int, Got>,
        val egress: Map<Int, Got>,
        val started: Pair<String, Long>,
        val intoRunning: Pair<String, Long>,
    )

    /** A fresh house and five fresh phones: arm, start an Egress, fire again into its cooldown. */
    private suspend fun playOverLoopback(insiders: List<Seat>, actor: Seat): Run {
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
            return coroutineScope {
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

                suspend fun drain(): Map<Int, Got> {
                    delay(SETTLE_MILLIS)
                    return seats.associate { s ->
                        s.index to Got(generateSequence { received.getValue(s.index).tryReceive().getOrNull() }.toList())
                    }
                }

                val armed = reduce(
                    GameState.EMPTY,
                    Event.RoundArmed(Tick(0), seed = 42L, seats = seats, insiders = insiders, markers = markers),
                )
                var state = armed.state
                for (effect in armed.effects) wire.emit(effect, state)
                val arming = drain()
                check(state.activeMarkers.size >= 3) { "the fixture lit ${state.activeMarkers.size} cards; nothing to relist" }

                suspend fun fire(at: Tick): Map<Int, Got> {
                    val nodes = listOf(state.activeMarkers.first(), state.activeMarkers.last())
                    val admission = admit(state, Event.EgressFired(at, actor, EgressType.Beacon, nodes, pulseFrom = 86_402_000L))
                    check(admission is Admission.Admitted) { "the fixture's Egress was refused: $admission" }
                    state = admission.reduction.state
                    for (effect in admission.reduction.effects) wire.emit(effect, state)
                    return drain()
                }

                val ready = state.egressReadyAt
                val egress = fire(ready)
                check(state.egress != null) { "the fixture's Egress never started, so it measured nothing" }
                val again = fire(ready + 1)
                val startedAnswer = egress.getValue(actor.index).frames.first()
                val runningAnswer = again.getValue(actor.index).frames.single()
                // A fire into the running cooldown: the actor's answer, and one blank of its length on
                // every other phone (D-263), so the refusal is one frame on every socket.
                for (seat in seats) {
                    if (seat == actor) continue
                    val blank = again.getValue(seat.index).frames.single()
                    assertEquals(RoundMessage.AbilityBlanked.Padded, messageOf(blank), "seat ${seat.index} was not sent the blank")
                    assertEquals(
                        runningAnswer.length - seqOf(runningAnswer).toString().length,
                        blank.length - seqOf(blank).toString().length,
                        "seat ${seat.index}'s blank is not the actor's answer's length",
                    )
                }
                assertEquals(RoundMessage.AbilityFired(cooldownStarted = true), messageOf(startedAnswer))
                assertEquals(RoundMessage.AbilityFired(cooldownStarted = false), messageOf(runningAnswer))

                jobs.forEach { it.cancel() }
                Run(
                    insiders = insiders.map { it.index },
                    actor = actor,
                    state = armed.state,
                    arming = arming,
                    egress = egress,
                    started = startedAnswer to seqOf(startedAnswer),
                    intoRunning = runningAnswer to seqOf(runningAnswer),
                )
            }
        } finally {
            jobs.forEach { it.cancel() }
            host.stop()
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 400L

        fun pushOf(frame: String): RoundBody.Push =
            RoundWire.decodeOrNull((TransportWire.decodeOrNull(frame) as TransportFrame.Carry).body) as RoundBody.Push

        fun messageOf(frame: String): RoundMessage = pushOf(frame).message

        fun seqOf(frame: String): Long = pushOf(frame).seq
    }
}
