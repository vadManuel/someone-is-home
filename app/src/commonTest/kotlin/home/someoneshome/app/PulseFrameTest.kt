package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.core.reduce
import home.someoneshome.model.Balance
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
import home.someoneshome.platform.monotonicMillis
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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Over a real socket, the Sync Pulse's grid reaches every phone as one host-clock number that
 * every phone, clock wrong or not, puts at one moment of real time; and every return is answered on
 * every living phone with one frame of one length, whoever tapped and whatever the house made of
 * it** (C1, C2; D-258's and D-263's patterns).
 *
 * [IsolateFrameSizeTest]'s method: a real [TransportHost] answering probes with the house's clock
 * ([monotonicMillis], what `RoundAuthority` anchors the pulse on), five real [TransportClient]s over
 * loopback whose own clocks are **wrong by +3 s, −2 s, 0, +1 s and −0.5 s**, the real [EffectWire],
 * the rules' own arming and admission, and what each socket received re-encoded by `TransportWire`.
 * Seat 3 is Revoked, so the out are seen to get no pulse frame.
 *
 * Played twice with seats 1 and 2 exchanging roles, the same seats doing the same things: seat 0
 * (a Resident both times) holds a beat at one node; seat 2 returns from nowhere near a node and
 * misses; seat 1 (an Insider, then a Resident) returns at the other node and contains it. Each seat's
 * frame lengths at each moment must be the same in both runs, and within a run every living seat's.
 */
class PulseFrameTest {

    private val seats = (0..5).map { Seat(it) }
    private val skews = listOf(3_000L, -2_000L, 0L, 1_000L, -500L, 250L)

    private val markers = listOf("KITCHEN", "HALL", "STUDY", "LANDING", "GARAGE", "PORCH")
        .mapIndexed { i, room -> Destination(MarkerId("card-$i"), MarkerShapes.all[i + 1].id, room) }

    @Test
    fun theGridIsOneMomentOnEveryPhoneAndEveryReturnIsOneFrameOfOneLengthOnEveryLivingPhone() = runBlocking {
        val asDrawn = playOverLoopback(insiders = listOf(Seat(1), Seat(4)))
        val exchanged = playOverLoopback(insiders = listOf(Seat(2), Seat(4)))

        for (run in listOf(asDrawn, exchanged)) {
            // ---- The grid: one number, decoded on every phone, the out included ----------------
            println("C2 GRID — insiders ${run.insiders}: pulseFrom ${run.pulseFrom}, decoded ${run.decodedGrid}")
            assertEquals(seats.map { it.index }.toSet(), run.decodedGrid.keys, "a phone was not told the grid")
            assertEquals(setOf(run.pulseFrom), run.decodedGrid.values.toSet(), "the phones were told different grids")

            // ---- Where each phone would put pulse 0, in real time, through its own skewed clock --
            println("C2 PULSE 0 ON EACH PHONE, MS FROM THE HOUSE'S — ${run.pulseZeroError}")
            for ((seat, error) in run.pulseZeroError) {
                assertTrue(
                    abs(error) <= 20,
                    "seat $seat (clock ${skews[seat]} ms off) would feel pulse 0 $error ms from the house's moment",
                )
            }

            // ---- Probes: the same count from every phone, Insider or Resident -------------------
            println("C1 PROBES PER SEAT — insiders ${run.insiders}: ${run.probes}")
            assertEquals(1, run.probes.values.toSet().size, "the phones sent different numbers of probes: ${run.probes}")

            // ---- Every return, one frame of one length on every living phone ---------------------
            val living = (seats.map { it.index } - 3).toSet()
            for ((moment, got) in run.returns) {
                println("C2 FRAMES AT $moment — insiders ${run.insiders}: ${got.mapValues { it.value.kinds }} ${got.mapValues { it.value.netLengths }}")
                for (seat in living) {
                    assertEquals(1, got.getValue(seat).pulse.size, "$moment: seat $seat got ${got.getValue(seat).kinds}")
                }
                assertTrue(got.getValue(3).pulse.isEmpty(), "$moment: the Revoked seat was sent a pulse frame: ${got.getValue(3).kinds}")
                val lengths = living.associateWith { got.getValue(it).netLengths }
                assertEquals(1, lengths.values.toSet().size, "$moment: the living phones were sent different frames: $lengths")
            }
            val pulseLengths = run.returns.values.flatMap { got -> living.map { got.getValue(it).pulseNetLength } }.toSet()
            assertEquals(1, pulseLengths.size, "held, missed, contained and blank are not one length: $pulseLengths")
            assertEquals(listOf(true, false), run.answers, "the house did not answer held then missed: ${run.answers}")
            assertTrue(run.contained, "the pair at the two nodes did not contain")
        }

        // ---- The roles exchanged: each seat's frames are the same --------------------------------
        for ((moment, got) in asDrawn.returns) {
            val other = exchanged.returns.getValue(moment)
            for (seat in seats) {
                assertEquals(
                    got.getValue(seat.index).netLengths, other.getValue(seat.index).netLengths,
                    "$moment: seat ${seat.index}'s frames follow its role: ${got.getValue(seat.index).kinds} / ${other.getValue(seat.index).kinds}",
                )
            }
        }
    }

    /** What one socket received for one moment of the round. */
    private class Got(val frames: List<String>) {
        val messages: List<RoundMessage> = frames.map(::messageOf)
        val kinds: List<String> get() = messages.map { it.kind.name }
        val netLengths: List<Int> get() = frames.map { it.length - seqOf(it).toString().length }
        val pulse: List<Int> = messages.indices.filter {
            messages[it] is RoundMessage.SyncPulseAnswered || messages[it] is RoundMessage.SyncPulseBlanked
        }
        val pulseNetLength: Int get() = netLengths[pulse.single()]
    }

    private class Run(
        val insiders: List<Int>,
        val pulseFrom: Long,
        val decodedGrid: Map<Int, Long>,
        val pulseZeroError: Map<Int, Long>,
        val probes: Map<Int, Int>,
        val returns: Map<String, Map<Int, Got>>,
        val answers: List<Boolean>,
        val contained: Boolean,
    )

    private suspend fun playOverLoopback(insiders: List<Seat>): Run {
        val received = seats.associate { it.index to Channel<String>(Channel.UNLIMITED) }
        val seated = Channel<Seat>(Channel.UNLIMITED)
        var minted = 0
        val host = TransportHost(
            SeatLedger(seats, mint = { SeatToken("tk-${minted++}") }),
            nowMillis = ::monotonicMillis,
            onFrame = { _, _ -> },
            onSeated = { seated.trySend(it) },
        )
        val wire = EffectWire(send = { seat, frame -> host.send(seat, frame) })
        val port = host.start(0)
        val jobs = mutableListOf<Job>()
        try {
            return coroutineScope {
                val clients = seats.map { seat ->
                    val sink = received.getValue(seat.index)
                    val skew = skews[seat.index]
                    val client = TransportClient(
                        ClientSession(), "127.0.0.1", port,
                        nowMillis = { monotonicMillis() + skew },
                        onFrame = { frame ->
                            if (frame is TransportFrame.Carry && RoundWire.decodeOrNull(frame.body) is RoundBody.Push) {
                                sink.trySend(TransportWire.encode(frame))
                            }
                        },
                    )
                    jobs += launch { client.run() }
                    assertEquals(seat, withTimeout(30_000) { seated.receive() })
                    client
                }
                withTimeout(10_000) { while (clients.any { it.hostNowMillis() == null }) delay(10) }

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
                // Seat 3 is out: it must be sent no pulse frame.
                var state = armed.state.copy(revoked = listOf(Seat(3)))
                for (effect in armed.effects) wire.emit(effect, state)
                drain()

                suspend fun put(event: Event): Map<Int, Got> {
                    val admission = admit(state, event)
                    check(admission is Admission.Admitted) { "the fixture's $event was refused: $admission" }
                    state = admission.reduction.state
                    for (effect in admission.reduction.effects) wire.emit(effect, state)
                    return drain()
                }

                // ---- The Egress, anchored as `RoundAuthority` anchors it: the house's own clock -----
                val firedAt = state.egressReadyAt
                val nodes = listOf(state.activeMarkers.first(), state.activeMarkers.last())
                val pulseFrom = monotonicMillis() + Balance.SYNC_PULSE_LEAD_MILLIS
                val opened = put(Event.EgressFired(firedAt, Seat(4), EgressType.Beacon, nodes, pulseFrom))
                check(state.egress != null) { "the fixture's Egress never started, so it measured nothing" }
                val decodedGrid = opened.mapValues { (_, got) ->
                    got.messages.filterIsInstance<RoundMessage.EgressOpened>().single().pulseFrom
                }
                // Where each phone puts pulse 0 in real time: its own clock reading that maps to the
                // grid, less its skew. The house's moment is `pulseFrom` itself.
                val pulseZeroError = clients.withIndex().associate { (i, client) ->
                    val fix = client.clockFix()!!
                    val localNow = monotonicMillis() + skews[i]
                    val localPulse = pulseFrom - fix.offsetAt(localNow)
                    i to (localPulse - skews[i]) - pulseFrom
                }

                // ---- Three returns: held, missed, contained -----------------------------------------
                var t = firedAt.step + 1
                put(Event.MarkerScanned(Tick(t++), Seat(0), nodes[0]))
                put(Event.MarkerScanned(Tick(t++), Seat(1), nodes[1]))
                put(Event.MarkerScanned(Tick(t++), Seat(2), state.activeMarkers[1]))
                // The taps are on the host's grid: pulse n at pulseFrom + n · interval.
                val onGrid = (0 until Balance.SYNC_PULSE_BEATS).map { pulseFrom + it * Balance.SYNC_PULSE_INTERVAL_MILLIS }
                t = maxOf(t, firedAt.step + Balance.SYNC_PULSE_LEAD + Balance.SYNC_PULSE_BEATS * Balance.SYNC_PULSE_INTERVAL)
                val held = put(Event.SyncPulseReturned(Tick(t++), Seat(0), onGrid))
                val missed = put(Event.SyncPulseReturned(Tick(t++), Seat(2), onGrid))
                val containing = put(Event.SyncPulseReturned(Tick(t++), Seat(1), onGrid))

                val answers = listOf(held to Seat(0), missed to Seat(2)).map { (got, seat) ->
                    got.getValue(seat.index).messages.filterIsInstance<RoundMessage.SyncPulseAnswered>().single().held
                }
                val contained = state.egress == null &&
                    containing.getValue(0).messages.any { it is RoundMessage.EgressContained }
                val probes = clients.withIndex().associate { (i, c) -> i to c.probesSent }

                jobs.forEach { it.cancel() }
                Run(
                    insiders = insiders.map { it.index },
                    pulseFrom = pulseFrom,
                    decodedGrid = decodedGrid,
                    pulseZeroError = pulseZeroError,
                    probes = probes,
                    returns = mapOf("A HELD BEAT" to held, "A MISS" to missed, "CONTAINMENT" to containing),
                    answers = answers,
                    contained = contained,
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
