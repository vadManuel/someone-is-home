package home.someoneshome.app

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.loadSeatToken
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.saveHostAddress
import home.someoneshome.platform.saveSeatToken
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.HostAdvertiser
import home.someoneshome.platform.transport.HostBrowser
import home.someoneshome.platform.transport.LobbyDesk
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.HomeFinder
import home.someoneshome.ui.LobbyLink
import home.someoneshome.ui.NearbyHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * **The lobby's two seams, joined to the radio.**
 *
 * `ui` cannot see `platform` and `platform` cannot see `ui`, so this is the one place mDNS and the
 * websocket can be introduced to the screens — the same reason [SavedHomesDocument] exists for the
 * filesystem. Everything above it is `ui`'s [HomeFinder] and [LobbyLink], which know nothing about
 * Ktor, and everything below is story 0.8's machinery, which knows nothing about a screen.
 *
 * ### None of this is the loop
 *
 * No `Event` is minted here, no `Effect` leaves here, no schema row is consulted and nothing is
 * redacted, because before arming there is no round for anything to be redacted out of. What
 * crosses the wire is three shapes: a name and a line going up, the standing coming down. The
 * standing carries the names back (D-115) and has nowhere at all to put a line.
 */

/** How many seats the ledger opens. Not a design cap — see [LobbyHouse]. */
private const val LOBBY_SEATS: Int = 16

/**
 * `ui`'s [HomeFinder], over mDNS.
 *
 * Must be built and started from the main thread: the iOS `HostBrowser` schedules on the main run
 * loop, and its callbacks arrive there. That is also where the screen wants them.
 */
class NearbyHomes(private val onEvent: (String) -> Unit = {}) : HomeFinder {

    private var browser: HostBrowser? = null

    override fun start(onFound: (NearbyHome) -> Unit) {
        if (browser != null) return
        browser = HostBrowser(
            onFound = { name, address, port -> onFound(NearbyHome(name, address, port)) },
            onEvent = onEvent,
        ).also { it.start() }
    }

    override fun stop() {
        browser?.stop()
        browser = null
    }
}

/**
 * **The house, on the host's phone: a ledger, a desk, a server and an advertisement.**
 *
 * [LOBBY_SEATS] is the ledger's list length and not a statement about how many people may play —
 * design intent is *virtually unlimited*, engineering posture *designed for 6–10, no hard cap
 * built*. D-103's band is computed from the seats actually taken, so the pool being larger than
 * the party changes nothing about the setting the host is offered.
 *
 * The port is the OS's choice, not a constant: the advertisement carries whichever one came up,
 * and nobody types an address in a dark house (D-094).
 */
class LobbyHouse(
    private val scope: CoroutineScope,
    private val onEvent: (String) -> Unit = {},
) {

    private val desk = LobbyDesk()
    private var host: TransportHost? = null
    private var advertiser: HostAdvertiser? = null

    /** True once the server is up. */
    var port: Int? = null
        private set

    /**
     * Bring the house up under [homeName] and call [onUp] with where it landed.
     *
     * The name goes on the air for the whole network before any permission this app controls, so
     * it carries the home's name and nothing a round produced — no roles, no seats, no state.
     */
    fun start(homeName: String, onUp: (NearbyHome) -> Unit) {
        if (host != null) return
        val ledger = SeatLedger(
            List(LOBBY_SEATS) { Seat(it) },
            // The app root is where real randomness belongs (the ledger's own KDoc): tokens never
            // enter a recording, and the determinism rules are about what does.
            mint = { SeatToken(Random.nextLong().toString(16) + Random.nextLong().toString(16)) },
        )
        val server = TransportHost(
            ledger,
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = ::onFrame,
            onSeated = { desk.seated(it); publish() },
            onLost = { desk.left(it); publish() },
        )
        host = server
        scope.launch {
            val bound = server.start(port = 0)
            port = bound
            advertiser = HostAdvertiser(homeName, bound, onEvent = onEvent).also { it.start() }
            onEvent("HOSTING '$homeName' ON :$bound")
            // Loopback, deliberately: the host is a player and joins its own house on the same
            // path everybody else uses.
            onUp(NearbyHome(homeName, "127.0.0.1", bound))
        }
    }

    /** The host's Insider setting. The desk clamps it into D-103's band; the wire carries it back. */
    fun setInsiders(chosen: Int?) {
        desk.setInsiders(chosen)
        publish()
    }

    /** **Deleted when the round ends** — the host's half of the promise. */
    fun roundEnded() {
        desk.roundEnded()
        publish()
    }

    fun stop() {
        advertiser?.stop()
        advertiser = null
        scope.launch { host?.stop() }
        host = null
        port = null
    }

    /**
     * A frame from a seat.
     *
     * Anything that is not a lobby body is left alone: a `Carry` may hold all sorts of things, and
     * a lobby that treated an unrecognised body as an error would take a player's connection down
     * over a message meant for somebody else.
     */
    private fun onFrame(seat: Seat, frame: TransportFrame) {
        val body = (frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull) ?: return
        when (body) {
            is LobbyBody.Handover -> {
                desk.handedOver(seat, body.line)
                // Deliberately not logged, not counted per player, and not echoed. The only trace
                // a line leaves is the count going up by one.
                publish()
            }
            // The name does go back down, to every phone, which is the whole of D-115 — and it
            // goes down as part of the standing, never as an echo of the body that brought it.
            is LobbyBody.Naming -> {
                desk.named(seat, body.name)
                publish()
            }
            // The standing is the host's to send, never to receive. A client that sent one is
            // saying nothing this house is obliged to hear.
            is LobbyBody.Standing -> Unit
        }
    }

    private fun publish() {
        val server = host ?: return
        val body = TransportFrame.Carry(LobbyWire.encode(desk.standing()))
        scope.launch {
            for (seat in server.connectedSeats()) {
                // A phone that died between the seat list and the send is not an error worth
                // taking the broadcast down for; its own reconnect brings it back up to date.
                runCatching { server.send(seat, body) }
            }
        }
    }
}

/**
 * `ui`'s [LobbyLink], over the websocket — and over [LobbyHouse] when this phone is the host.
 *
 * One object rather than two, because the lobby screen is one screen: the host's setting goes to
 * the desk sitting next to it and the answer comes back around the socket, so the number the host
 * reads arrived exactly the way the number everybody else reads did.
 */
class LobbyOverTransport(
    private val scope: CoroutineScope,
    private val onEvent: (String) -> Unit = {},
) : LobbyLink {

    private val house = LobbyHouse(scope, onEvent)
    private var session: ClientSession? = null
    private var client: TransportClient? = null
    private var clientJob: Job? = null

    /**
     * What this phone last said to call its owner, kept so it can be said again.
     *
     * **In memory and nowhere else**, exactly like the one line it sits beside. The desk drops
     * names at the end of every round (D-115) and the protocol's answer has always been that the
     * name rides back up on every re-seating; NEW ROUND is a third occasion for the same re-send,
     * and it needs the same thing to re-send.
     */
    private var residentName: String = ""

    init {
        // The 0.8 property on relaunch: a killed app reads its token back and resumes as THAT
        // seat, never as *a* seat. The restored session never says Hello.
        loadSeatToken()?.let { session = ClientSession(SeatToken(it)) }
    }

    override fun host(homeName: String, onUp: (NearbyHome) -> Unit) = house.start(homeName, onUp)

    override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) {
        residentName = name
        if (clientJob?.isActive == true) return
        val s = session ?: ClientSession().also { session = it }
        val c = TransportClient(
            s, home.address, home.port,
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = { frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull)
                // Only the standing is acted on. Either upward body arriving at a client would be
                // the house sending one phone's text to another, which nothing on the host side
                // does — and if it ever did, ignoring it here is the fail-closed direction.
                if (body is LobbyBody.Standing) onStanding(body)
            },
            onPhase = { phase ->
                if (phase is ClientSession.Phase.Seated) {
                    // The token says WHO this phone is and the address says WHERE the house is;
                    // a resume needs both. A failed write is reported, never swallowed — a phone
                    // that cannot store its token cannot come back from a crash, and the host is
                    // the only one who can be told (D-087, D-084).
                    try {
                        saveSeatToken(phase.token.value)
                        saveHostAddress(home.address)
                    } catch (notSaved: IllegalStateException) {
                        onEvent("TOKEN NOT SAVED — NO RESUME AFTER A CRASH")
                    }
                    // On every seating, not only the first: a phone that dropped and came back to
                    // the seat its token owns arrives at a desk that dropped its name with it, and
                    // a lobby listing an empty chip for somebody standing in the room is a fault
                    // nobody could diagnose from the screen. The name is never persisted — it is
                    // re-sent from what this phone still has in memory, or not at all.
                    client?.let { c -> scope.launch { c.send(naming(name)) } }
                }
            },
        )
        client = c
        clientJob = scope.launch { c.run() }
    }

    /**
     * **The one line's only exit from this phone**, as an opaque body the frame layer cannot read.
     *
     * Nothing keeps a copy on the way out. It is not logged — `onEvent` is where every other event
     * on this path goes, and a line in a log is a line on a phone.
     */
    override fun handOver(line: String) {
        val c = client ?: return
        scope.launch { c.send(TransportFrame.Carry(LobbyWire.encode(LobbyBody.Handover(line)))) }
    }

    private fun naming(name: String): TransportFrame =
        TransportFrame.Carry(LobbyWire.encode(LobbyBody.Naming(name)))

    /** The host's setting. On a phone that is not hosting, [LobbyHouse] was never started. */
    override fun setInsiders(chosen: Int?) = house.setInsiders(chosen)

    /**
     * **The round ended: the desk drops every line, and this phone stays in the lobby** (D-116,
     * D-157).
     *
     * On a phone that is not hosting the desk is not here, so the first call does nothing — the
     * same shape [setInsiders] has, and for the same reason: the desk belongs to the host and a
     * client that had one would be a second copy of the lobby.
     *
     * **The name goes back up afterwards**, because the desk drops names with the lines (D-115 —
     * they are text, held for one round like everything else on it). That is the same re-send a
     * re-seating does, for the same reason: a lobby listing an empty chip for somebody standing in
     * the room is a fault nobody could diagnose from the screen. It is re-sent from what this
     * phone still has in memory; it is never persisted.
     */
    override fun roundEnded() {
        house.roundEnded()
        val c = client ?: return
        scope.launch { c.send(naming(residentName)) }
    }

    override fun leave() {
        clientJob?.cancel()
        clientJob = null
        client = null
        house.roundEnded()
        house.stop()
    }
}
