package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportWire
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The one websocket route. Part of the wire contract, beside [TransportWire]'s JSON. */
const val TRANSPORT_PATH: String = "/round"

/**
 * D1's host half: the embedded Ktor server that turns websockets into seats.
 *
 * The thinnest wiring that can carry the machinery: every admission decision is [SeatLedger]'s,
 * every frame is [TransportWire]'s, and this class only holds sockets. A connection's first frame
 * must be [TransportFrame.Hello] or [TransportFrame.Resume]; the ledger answers; a refusal is
 * *sent* — a distinct kind, never a silent close — and then the connection ends. A seated
 * connection is pumped until it dies, and every inbound frame arrives at [onFrame] already
 * attributed to the seat the ledger granted, which is the only attribution that exists: nothing a
 * client writes in a frame can name a seat.
 *
 * A malformed message is dropped, counted in [malformedCount], and the connection lives on — an
 * I/O oddity must never take a player's link down with it (silent to the player, loud to the
 * authority; the count is the authority's to read until transport failure events exist).
 *
 * Callbacks fire on Ktor's worker threads. The internal table is mutex-guarded; [SeatLedger] is
 * only ever touched under the same mutex.
 */
class TransportHost(
    private val ledger: SeatLedger,
    private val onFrame: (Seat, TransportFrame) -> Unit,
    private val onSeated: (Seat) -> Unit = {},
    private val onLost: (Seat) -> Unit = {},
) {

    private class Link(val id: Long, val ws: DefaultWebSocketServerSession)

    private val gate = Mutex()
    private val links = HashMap<Seat, Link>()
    private var nextLinkId = 0L
    private var malformed = 0
    private var server: EmbeddedServer<*, *>? = null

    /** Binds and returns the actual port — pass 0 to let the OS pick one (tests do). */
    suspend fun start(port: Int): Int {
        check(server == null) { "the host is already running" }
        val s = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                webSocket(TRANSPORT_PATH) { attend(this) }
            }
        }
        server = s
        s.start(wait = false)
        return s.engine.resolvedConnectors().first().port
    }

    suspend fun stop() {
        server?.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        server = null
    }

    /**
     * Host → one seat. Throws if no connection holds the seat — the caller is the future
     * authority glue and must know a phone is unreachable, not have the frame vanish; a dead
     * radio making a living player silently invisible is this project's canonical worst failure.
     */
    suspend fun send(seat: Seat, frame: TransportFrame) {
        val link = gate.withLock { links[seat] }
            ?: error("no connection holds ${seat} — the frame was not sent")
        link.ws.send(Frame.Text(TransportWire.encode(frame)))
    }

    suspend fun connectedSeats(): Set<Seat> = gate.withLock { links.keys.toSet() }

    /** Messages that were not frames. Zero is the only healthy number. */
    suspend fun malformedCount(): Int = gate.withLock { malformed }

    private suspend fun attend(ws: DefaultWebSocketServerSession) {
        val opening = ws.nextFrameOrNull() ?: return
        val admission = gate.withLock {
            when (opening) {
                is TransportFrame.Hello -> ledger.join()
                is TransportFrame.Resume -> ledger.resume(opening.token)
                // Anything else before a handshake is a client speaking out of turn; no seat, no
                // conversation.
                else -> null
            }
        }
        when (admission) {
            null -> ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "handshake first"))
            is SeatLedger.Admission.Refused -> {
                ws.send(Frame.Text(TransportWire.encode(TransportFrame.Refused(admission.reason))))
                ws.close(CloseReason(CloseReason.Codes.NORMAL, "refused"))
            }
            is SeatLedger.Admission.Seated -> attendSeated(ws, admission)
        }
    }

    private suspend fun attendSeated(ws: DefaultWebSocketServerSession, seated: SeatLedger.Admission.Seated) {
        val seat = seated.seat
        val link: Link
        val replaced: Link?
        gate.withLock {
            link = Link(nextLinkId++, ws)
            replaced = links.put(seat, link)
        }
        // A resume raced the old socket's death: the token owns the seat, so the newer
        // connection wins and the stale one is closed without ceremony (outside the lock —
        // closing suspends).
        replaced?.ws?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "resumed elsewhere"))

        ws.send(Frame.Text(TransportWire.encode(TransportFrame.Seated(seated.token))))
        onSeated(seat)
        try {
            while (true) {
                val frame = ws.nextFrameOrNull() ?: break
                onFrame(seat, frame)
            }
        } finally {
            val lost = gate.withLock {
                if (links[seat]?.id == link.id) {
                    links.remove(seat)
                    true
                } else {
                    false // already replaced by a resume; the seat was never without a link
                }
            }
            if (lost) onLost(seat)
        }
    }

    /** The next decodable frame, skipping malformed messages; null when the socket is done. */
    private suspend fun DefaultWebSocketServerSession.nextFrameOrNull(): TransportFrame? {
        while (true) {
            val msg = try {
                incoming.receive()
            } catch (_: ClosedReceiveChannelException) {
                return null
            }
            val text = (msg as? Frame.Text)?.readText() ?: continue
            val frame = TransportWire.decodeOrNull(text)
            if (frame == null) {
                gate.withLock { malformed++ }
                continue
            }
            return frame
        }
    }
}
