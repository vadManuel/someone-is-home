package home.someoneshome.platform.transport

import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retry delay while still a stranger — [ClientSession] only backs off once a seat is held. */
const val PRESEAT_RETRY_MILLIS: Long = 500

/**
 * How often a seated client asks its [ClockSync] whether probes are due (C1). Not a probe cadence:
 * there is one burst per connection, at its seating, and the uploads do the rest (D-295). Only how
 * late a due burst, or an abandoned one's end, may be noticed.
 */
const val PROBE_TICK_MILLIS: Long = 250

/**
 * D1's phone half: one websocket to the host, reopened for as long as [ClientSession] says so.
 *
 * All policy is the session's: what the opening frame is (Hello once, ever; the stored token
 * after), when the next attempt happens (G3's bounded backoff), and when to stop (a refusal is
 * terminal). This class connects, hands frames both ways, and reports what happened — it decides
 * nothing, which is what keeps the G1 property testable without a socket in sight.
 *
 * ### Delivery is at-most-once at this layer, and that is deliberate
 *
 * A frame handed to [send] while the socket is failing may be lost with it. Retransmission belongs
 * to the ack protocol above ([AckLedger]'s broadcast → ack → commit), not to a transport queue
 * that would happily replay a stale intent into a changed round.
 *
 * ### The house's clock rides the same socket (C1, ARCH D5)
 *
 * Every seated connection sends one burst of [TransportFrame.TimeProbe]s at its seating and feeds
 * each [TransportFrame.TimeMark] the host answers with to the estimator, **before and instead of**
 * [onFrame]: a mark is this layer's, and nothing above it has a use for one. **From then on the
 * estimate is refreshed every second by the round's own uploads** ([uploadAnswered], D-295): the
 * round half above stamps each upload's probe and hands each answer's two house readings down
 * here. [hostNowMillis] is the result, readable from any thread.
 *
 * **The schedule reads nothing but this connection's own clock.** This class knows no seat and no
 * role, and every phone, the host's own included, runs this same loop from its own seating — so
 * an Insider's phone and a Resident's put the same probe frames on the air at the same offsets
 * from their connect (rule 1, D-263). The seating burst goes straight onto the socket and never
 * into D-263's request slot, where a probe would wait up to a second for a beat. **The upload's
 * own probe does not wait** (D-295): it is its own field, stamped when the upload is made, so its
 * round trip is the network's and nothing else's.
 *
 * Callbacks fire on the client engine's threads.
 */
class TransportClient(
    private val session: ClientSession,
    private val hostName: String,
    private val port: Int,
    /** This phone's monotonic milliseconds. The probe schedule and the offset are both on it. */
    private val nowMillis: () -> Long,
    private val onFrame: (TransportFrame) -> Unit,
    private val onPhase: (ClientSession.Phase) -> Unit = {},
    /**
     * **Each new estimate, once, as a round of probes finishes** — for the device console and
     * nowhere else (rule 6). Called on the client engine's thread; it must not block.
     */
    private val onFix: (ClockFix) -> Unit = {},
) {

    private val outbox = Channel<TransportFrame>(Channel.BUFFERED)

    /** D5's estimator. Single-threaded by contract, so every touch of it is under [clockLock]. */
    private val clock = ClockSync()
    private val clockLock = Mutex()

    /** [clock]'s newest fix, published for readers on any thread. */
    private val published = MutableStateFlow<ClockFix?>(null)
    private val probesOut = MutableStateFlow(0)
    private val samplesIn = MutableStateFlow(0L)

    /** Hand [clock]'s fix to readers, and to [onFix] when it is a new one. Under [clockLock]. */
    private fun publish() {
        val fix = clock.fix ?: return
        if (fix == published.value) return
        published.value = fix
        onFix(fix)
    }

    suspend fun send(frame: TransportFrame) {
        outbox.send(frame)
    }

    /**
     * **Now, on the house's monotonic clock**, in milliseconds — or null before the first round of
     * probes has been answered. It never runs backward (D5's slew). Any thread.
     */
    fun hostNowMillis(): Long? = published.value?.hostNowMillis(nowMillis())

    /** The estimate in force, for a log line: offset and round trip. Null before the first fix. */
    fun clockFix(): ClockFix? = published.value

    /** How many [TransportFrame.TimeProbe] this client has put on a socket. A diagnostic. */
    val probesSent: Int get() = probesOut.value

    /** How many samples the estimate has taken: the seating burst's one, then one per answered upload (D-295). A diagnostic. */
    val clockSamples: Long get() = samplesIn.value

    /**
     * **One upload's round trip, from the round half above** (D-295): the upload left this phone
     * at [sentAtMillis] and its answer arrived at [receivedAtMillis], both on [nowMillis]'s clock;
     * the house read it at [heardAtMillis] and answered at [answeredAtMillis] on its own. Into the
     * same estimator the seating burst fed, under its lock, and published. Any thread.
     */
    suspend fun uploadAnswered(sentAtMillis: Long, heardAtMillis: Long, answeredAtMillis: Long, receivedAtMillis: Long) {
        clockLock.withLock {
            clock.onSample(sentAtMillis, heardAtMillis, answeredAtMillis, receivedAtMillis)
            samplesIn.value = clock.sampleCount
            publish()
        }
    }

    /**
     * Connect, pump, reconnect — until the session is dismissed or the caller cancels. Cancelling
     * this is how a caller hangs up; nothing else stops a client that still holds a seat.
     */
    suspend fun run() {
        val http = HttpClient(CIO) { install(WebSockets) }
        try {
            while (true) {
                when (val phase = session.phase) {
                    is ClientSession.Phase.Dismissed -> return
                    is ClientSession.Phase.Rejoining -> {
                        val wait = phase.nextAttemptAtMillis - nowMillis()
                        if (wait > 0) delay(wait)
                    }
                    else -> {}
                }
                val hadSeatBefore = session.phase !is ClientSession.Phase.Joining
                attempt(http)
                if (session.phase is ClientSession.Phase.Dismissed) return
                if (!hadSeatBefore && session.phase is ClientSession.Phase.Joining) {
                    // Still a stranger and the connect failed: the session has nothing to back
                    // off with, so pace the retry here rather than hot-looping at a defunct host.
                    delay(PRESEAT_RETRY_MILLIS)
                }
            }
        } finally {
            http.close()
        }
    }

    private suspend fun attempt(http: HttpClient) {
        try {
            http.webSocket(host = hostName, port = port, path = TRANSPORT_PATH) {
                send(Frame.Text(TransportWire.encode(session.opening())))
                when (val reply = nextFrameOrNull()) {
                    is TransportFrame.Seated -> {
                        session.onSeated(reply.token)
                        onPhase(session.phase)
                    }
                    is TransportFrame.Refused -> {
                        session.onRefused(reply.reason)
                        onPhase(session.phase)
                        return@webSocket
                    }
                    // Closed, or not a handshake reply: treat as a failed attempt.
                    else -> return@webSocket
                }
                coroutineScope {
                    val pump = launch {
                        for (frame in outbox) send(Frame.Text(TransportWire.encode(frame)))
                    }
                    // **The clock's own loop, on this connection** (C1). A new socket bursts at
                    // once and keeps the fix; nothing after (D-295: the uploads refresh it). Written straight to
                    // the socket beside the pump rather than queued behind it: Ktor's outgoing side
                    // is a channel, so two senders interleave whole frames.
                    // A round can finish inside either call (abandoned, or timed out), so each
                    // publishes what the estimator now holds.
                    clockLock.withLock {
                        clock.reconnected(nowMillis())
                        publish()
                    }
                    val probing = launch {
                        while (true) {
                            val due = clockLock.withLock {
                                clock.dueProbes(nowMillis()).also { publish() }
                            }
                            for (probe in due) {
                                send(Frame.Text(TransportWire.encode(TransportFrame.TimeProbe(probe))))
                                probesOut.value += 1
                            }
                            delay(PROBE_TICK_MILLIS)
                        }
                    }
                    try {
                        while (true) {
                            val frame = nextFrameOrNull() ?: break
                            if (frame is TransportFrame.TimeMark) {
                                // Stamped on arrival, before the lock: a wait for the lock is not
                                // round trip, and would read as offset.
                                val heardAt = nowMillis()
                                clockLock.withLock {
                                    clock.onMark(frame.probe, frame.hostMillis, heardAt)
                                    samplesIn.value = clock.sampleCount
                                    publish()
                                }
                                continue
                            }
                            onFrame(frame)
                        }
                    } finally {
                        probing.cancel()
                        pump.cancel()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // A refused connection, a mid-pump end — all one fact, recorded below.
        }
        if (session.phase !is ClientSession.Phase.Dismissed) {
            session.onLost(nowMillis())
            onPhase(session.phase)
        }
    }

    /** The next decodable frame; malformed messages are skipped, a closed socket is null. */
    private suspend fun DefaultClientWebSocketSession.nextFrameOrNull(): TransportFrame? {
        while (true) {
            val msg = try {
                incoming.receive()
            } catch (_: ClosedReceiveChannelException) {
                return null
            }
            val text = (msg as? Frame.Text)?.text() ?: continue
            return TransportWire.decodeOrNull(text) ?: continue
        }
    }
}
