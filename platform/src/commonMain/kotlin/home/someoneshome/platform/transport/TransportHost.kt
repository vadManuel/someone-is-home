package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportRefusal
import home.someoneshome.model.protocol.TransportWire
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The one websocket route. Part of the wire contract, beside [TransportWire]'s JSON. */
const val TRANSPORT_PATH: String = "/round"

/**
 * D1's host half: the embedded Ktor server that turns websockets into seats.
 *
 * The thinnest wiring that can carry the machinery: every admission decision is [SeatLedger]'s,
 * every frame is [TransportWire]'s, and this class only holds sockets. A connection's first frame
 * must be [TransportFrame.Hello] or [TransportFrame.Resume]; the ledger answers; a refusal is
 * *sent* — a distinct kind, never a silent close — and then the connection ends. A seated
 * connection is pumped until it fails, and every inbound frame arrives at [onFrame] already
 * attributed to the seat the ledger granted, which is the only attribution that exists: nothing a
 * client writes in a frame can name a seat.
 *
 * A malformed message is dropped, counted in [malformedCount], and the connection lives on — an
 * I/O oddity must never take a player's link down with it (silent to the player, loud to the
 * authority; the count is the authority's to read until transport failure events exist).
 *
 * Callbacks fire on Ktor's worker threads, **one per connection and several at once**: a caller
 * that keeps state off them must confine that state itself (the lobby's desk, D-227). The internal
 * table is mutex-guarded; [SeatLedger] is only ever touched under the same mutex, and so is
 * [onLost], which therefore must not block.
 */
class TransportHost(
    private val ledger: SeatLedger,
    /** The authority's monotonic clock, read at the edge — [TransportFrame.TimeMark] carries it. */
    private val nowMillis: () -> Long,
    private val onFrame: (Seat, TransportFrame) -> Unit,
    private val onSeated: (Seat) -> Unit = {},
    private val onLost: (Seat) -> Unit = {},
    /**
     * **A connection that was turned away, and why.**
     *
     * The authority's half of rule 6, and it is here because it was missing: on 2026-08-21 a
     * phone presented a token to a house that had never issued it, the ledger refused it
     * correctly (D-087), and the refusal reached no log, no screen and no host — silent to the
     * player *and* to the authority, which is rule 6 exactly inverted (E-D3-1).
     *
     * **It carries the reason and never the token.** A refused resume is worth recording; the
     * token that was presented is the one piece of the exchange that identifies a seat, and a
     * host's log is not where it belongs.
     */
    private val onRefused: (TransportRefusal) -> Unit = {},
) {

    private class Link(val id: Long, val ws: DefaultWebSocketServerSession)

    private val gate = Mutex()
    private val links = HashMap<Seat, Link>()
    private var nextLinkId = 0L
    private var malformed = 0
    private var server: EmbeddedServer<*, *>? = null

    /**
     * Binds and returns the actual port — pass 0 to let the OS pick one (tests do).
     *
     * **Startable again after [stop], on the same port, with the same ledger** (H2, D-225). That
     * is how a host that left the foreground comes back: iOS does not keep a suspended app's
     * listening socket, every phone in the house holds this port and its own seat token, and a
     * server reopened here with the ledger it had answers each of them with *that* seat. So the
     * port is bound with address reuse — the server closed the connections, so they sit in
     * TIME_WAIT on this port — and a bind that fails leaves this object stopped rather than
     * half-started, so the caller can try again.
     *
     * ### A named port is tried before the engine is given it
     *
     * Ktor's engine binds on a coroutine of its own, and a refused bind there is an **uncaught**
     * exception on its IO dispatcher — which on Kotlin/Native ends the process. Seen in the test
     * written for this (`HostReopenTest`, with address reuse switched off): `EADDRINUSE`, and the
     * test binary failed rather than failing. On the host's phone that would be the house crashing
     * at the moment it was trying to recover. So a named port is bound once here, with the
     * engine's options, where a refusal is an ordinary exception the caller can catch and retry,
     * and only then handed over. Port 0 is the OS's choice and cannot be refused this way.
     */
    suspend fun start(port: Int): Int {
        check(server == null) { "the host is already running" }
        if (port != 0) portAccepts(port)
        val wanted = port
        val s = embeddedServer(
            CIO,
            configure = {
                connector {
                    this.port = wanted
                    host = "0.0.0.0"
                }
                reuseAddress = true
            },
        ) {
            install(WebSockets)
            routing {
                webSocket(TRANSPORT_PATH) { attend(this) }
            }
        }
        try {
            s.start(wait = false)
            val bound = s.engine.resolvedConnectors().first().port
            server = s
            return bound
        } catch (failed: Throwable) {
            runCatching { s.stop(gracePeriodMillis = 0, timeoutMillis = 250) }
            throw failed
        }
    }

    /** Binds [port] the way the engine will and lets it go; throws where the engine would crash. */
    private suspend fun portAccepts(port: Int) {
        SelectorManager(Dispatchers.IO).use { selector ->
            aSocket(selector).tcp().bind("0.0.0.0", port) { reuseAddress = true }.close()
        }
    }

    suspend fun stop() {
        server?.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        server = null
    }

    /**
     * Host → one seat. Throws if no connection holds the seat — the caller is the future
     * authority glue and must know a phone is unreachable, not have the frame vanish; a defunct
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
                onRefused(admission.reason)
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
        // A resume raced the old socket's end: the token owns the seat, so the newer
        // connection wins and the stale one is closed without ceremony (outside the lock —
        // closing suspends).
        replaced?.ws?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "resumed elsewhere"))

        ws.send(Frame.Text(TransportWire.encode(TransportFrame.Seated(seated.token))))
        onSeated(seat)
        try {
            while (true) {
                val frame = ws.nextFrameOrNull() ?: break
                // Clock probes are answered HERE, below the app: the reply must carry the time
                // the probe was seen, not the time some handler got around to it — every
                // millisecond of detour becomes rtt, and rtt is the noise floor of D5's offset.
                if (frame is TransportFrame.TimeProbe) {
                    ws.send(Frame.Text(TransportWire.encode(TransportFrame.TimeMark(frame.probe, nowMillis()))))
                    continue
                }
                onFrame(seat, frame)
            }
        } finally {
            // **[onLost] is called under the lock**, so a seat's loss is always reported before a
            // later resume of the same seat is: that resume's `put` and its [onSeated] both come
            // after this lock is released. Called after the lock, a loss could reach the caller
            // second — and a lobby told *seated, then lost* empties a chair somebody is sitting
            // in, and refuses the name that seat sends next.
            //
            // **And never cancelled.** A server stopping cancels every connection at once, and a
            // cancelled coroutine that has to wait for the lock is thrown out of `withLock` before
            // it gets it: that seat's link stayed in the table and its loss was never reported, so
            // the house went on believing in a seat whose phone had gone (D-227).
            withContext(NonCancellable) {
                gate.withLock {
                    if (links[seat]?.id == link.id) {
                        links.remove(seat)
                        onLost(seat)
                    }
                    // Otherwise already replaced by a resume; the seat was never without a link.
                }
            }
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
