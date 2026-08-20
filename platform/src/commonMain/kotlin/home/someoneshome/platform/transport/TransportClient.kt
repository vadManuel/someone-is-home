package home.someoneshome.platform.transport

import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Retry delay while still a stranger — [ClientSession] only backs off once a seat is held. */
const val PRESEAT_RETRY_MILLIS: Long = 500

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
 * A frame handed to [send] while the socket is dying may be lost with it. Retransmission belongs
 * to the ack protocol above ([AckLedger]'s broadcast → ack → commit), not to a transport queue
 * that would happily replay a stale intent into a changed round.
 *
 * Callbacks fire on the client engine's threads.
 */
class TransportClient(
    private val session: ClientSession,
    private val hostName: String,
    private val port: Int,
    private val nowMillis: () -> Long,
    private val onFrame: (TransportFrame) -> Unit,
    private val onPhase: (ClientSession.Phase) -> Unit = {},
) {

    private val outbox = Channel<TransportFrame>(Channel.BUFFERED)

    suspend fun send(frame: TransportFrame) {
        outbox.send(frame)
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
                    // off with, so pace the retry here rather than hot-looping at a dead host.
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
                    try {
                        while (true) onFrame(nextFrameOrNull() ?: break)
                    } finally {
                        pump.cancel()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // A refused connection, a mid-pump death — all one fact, recorded below.
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
            val text = (msg as? Frame.Text)?.readText() ?: continue
            return TransportWire.decodeOrNull(text) ?: continue
        }
    }
}
