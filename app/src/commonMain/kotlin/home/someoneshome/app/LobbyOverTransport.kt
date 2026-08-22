package home.someoneshome.app

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportRefusal
import home.someoneshome.platform.clearSeatToken
import home.someoneshome.platform.loadSeatToken
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.saveHostAddress
import home.someoneshome.platform.saveSeatToken
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.HostAdvertiser
import home.someoneshome.platform.transport.HostBrowser
import home.someoneshome.platform.transport.LobbyDesk
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.HomeFinder
import home.someoneshome.ui.HostRefusal
import home.someoneshome.ui.LobbyLink
import home.someoneshome.ui.NearbyHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * **How long a home is given to go on the air before the silence is called a refusal.**
 *
 * Bonjour answers in well under a second on a working stack — the 2026-08-21 device logs have the
 * publish landing between two lobby lines. This is not a timeout on a slow network; it is the
 * bound on a callback that **never comes at all**, which is what a reset Local Network grant looks
 * like from inside this process (E-D3-4).
 *
 * Long enough that no working phone will ever reach it, short enough that a host is not left
 * looking at a button that did nothing. It is a device-side patience, not a balance value: no rule
 * reads it and it stamps into no recording.
 */
private const val ADVERTISE_PATIENCE_MILLIS: Long = 10_000

/**
 * **Where this phone's seat is kept between process exits**, as one seam rather than three
 * top-level calls.
 *
 * It exists so the stale-token path can be *tested* rather than argued about: the behaviour
 * E-D3-1 rules on is what a phone does when the seat it stored belongs to a house that no longer
 * exists, and a test cannot set that up against a store that only the real filesystem answers.
 * [DeviceSeat] is what every production construction gets and the default here, so nothing in the
 * app chooses a store.
 *
 * The address rides with the token because a resume needs both — WHO and WHERE (D-094, D-087).
 */
interface SeatStore {

    /** The token this phone stored last time, or null. */
    fun stored(): String?

    /** Keep both. **Throws** when the write did not happen — the caller decides what to say. */
    fun keep(token: String, hostAddress: String)

    /** Forget the token. A seat the house does not know is not worth presenting twice. */
    fun forget()
}

/** [SeatStore] on the phone's own filesystem: story 0.8's two stores, behind one door. */
object DeviceSeat : SeatStore {
    override fun stored(): String? = loadSeatToken()
    override fun keep(token: String, hostAddress: String) {
        saveSeatToken(token)
        saveHostAddress(hostAddress)
    }
    override fun forget() = clearSeatToken()
}

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

    /** Whether this attempt at hosting has had its one answer yet. See [ADVERTISE_PATIENCE_MILLIS]. */
    private var answered = false

    /** True once the server is up. */
    var port: Int? = null
        private set

    /**
     * Bring the house up under [homeName] and call [onUp] with where it landed — **or [onRefused]
     * when the home could not go on the air at all.**
     *
     * The name goes on the air for the whole network before any permission this app controls, so
     * it carries the home's name and nothing a round produced — no roles, no seats, no state.
     *
     * ### The server binding is not the house being up (E-D3-2)
     *
     * [onUp] used to fire the moment the socket bound, which is one line above where the OS gets
     * to say whether anybody will ever find this home. When the advertisement was refused — a
     * second THE BUNGALOW on one network, on 2026-08-21 — the host went into a lobby that no
     * phone could join and nothing anywhere said so. So the two callbacks are the advertiser's
     * two ends, and a refusal **takes the server back down**: a house nobody can find is not a
     * house, and leaving the port bound would make the next attempt under a better name fail to
     * start for a second, unrelated reason.
     *
     * ### Silence is the third end, and it is the one that was seen in the wild
     *
     * The OS does not always refuse. With the Local Network grant reset, iOS publishes **nothing
     * and says nothing** — no error, no prompt, no callback of any kind (E-D3-4, and the very
     * first device run before it). Waiting on two callbacks that may both never come would turn
     * this button into a control that does nothing when pressed, which is a worse failure than
     * the one being fixed. So [ADVERTISE_PATIENCE_MILLIS] later, an unanswered advertisement is
     * refused as one — exactly one of the three outcomes reaches the host, always.
     */
    fun start(
        homeName: String,
        onUp: (NearbyHome) -> Unit,
        onRefused: (AdvertiseFailure) -> Unit = {},
    ) {
        if (host != null) return
        answered = false
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
            // The authority's record of a connection turned away — the reason and never the
            // token (E-D3-1). A refused resume is the one the host most needs: it means a phone
            // in this room is holding a seat from an evening this house never ran.
            onRefused = { reason -> onEvent("A CONNECTION WAS REFUSED — $reason") },
        )
        host = server
        scope.launch {
            val bound = server.start(port = 0)
            port = bound
            onEvent("HOSTING '$homeName' ON :$bound")
            advertiser = HostAdvertiser(
                homeName, bound,
                onEvent = onEvent,
                // Loopback, deliberately: the host is a player and joins its own house on the
                // same path everybody else uses.
                onAir = { answer { onUp(NearbyHome(homeName, "127.0.0.1", bound)) } },
                onFailed = { why -> answer { stop(); onRefused(why) } },
            ).also { it.start() }
            delay(ADVERTISE_PATIENCE_MILLIS)
            // Hung up while waiting: there is nothing left to refuse.
            if (host == null) return@launch
            answer {
                onEvent("NO ANSWER FROM THE RADIO IN ${ADVERTISE_PATIENCE_MILLIS / 1000}S")
                stop()
                onRefused(AdvertiseFailure.Refused)
            }
        }
    }

    /** The first of the three outcomes wins; the other two are a house answering twice. */
    private fun answer(what: () -> Unit) {
        if (answered) return
        answered = true
        what()
    }

    /** The host's Insider setting. The desk clamps it into D-103's band; the wire carries it back. */
    fun setInsiders(chosen: Int?) {
        desk.setInsiders(chosen)
        publish()
    }

    /**
     * **The host's ballot length, to the desk — and deliberately not to the standing** (D-117).
     *
     * [setInsiders] publishes because the Insider count is part of what every phone in the lobby is
     * shown: the desk clamps it into D-103's band and the clamped value has to come back, or the
     * host reads a setting the house is not holding. **The ballot's length is not on that surface at
     * all.** It reaches the round by the arming event, and it reaches the clients on the push that
     * opens the vote — so publishing it here would widen the wire for a number already travelling
     * by a better road, and would put it on six phones a whole round before anybody may act on it.
     *
     * The absence of `publish()` is therefore the decision, not an omission, which is why it is
     * written down beside the one call that has it. See `LobbyDesk.voteWindowSeconds`.
     */
    fun setVoteWindow(seconds: Int) = desk.setVoteWindow(seconds)

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
                // A phone that failed between the seat list and the send is not an error worth
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
    /**
     * **Every body that arrives at this client, before anything is done with it.**
     *
     * Empty by default and empty in every production construction — this exists so that *nothing
     * carrying a line ever comes down* can be checked on a phone rather than argued from types.
     * [LobbyWire] makes the claim structurally, and the structural claim is the real guarantee; but
     * two phones on a real network is where a wrong turn in the host's broadcast would show, and
     * from a Mac an absent leak and an absent connection look the same.
     *
     * It is handed the body and not the decoded shape on purpose: a decoder that dropped an
     * unrecognised body on the floor would hide exactly the frame worth seeing. **Whoever takes
     * this must not print it** — the cheat that does takes a verdict off it and prints that.
     */
    private val onInbound: (String) -> Unit = {},
    /**
     * Where the stored seat lives. [DeviceSeat] in every production construction; an in-memory
     * one is how the stale-token path is tested without a filesystem underneath it.
     */
    private val seat: SeatStore = DeviceSeat,
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
        // The 0.8 property on relaunch: a stopped app reads its token back and resumes as THAT
        // seat, never as *a* seat. The restored session never says Hello.
        seat.stored()?.let { session = ClientSession(SeatToken(it)) }
    }

    override fun host(homeName: String, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) =
        house.start(homeName, onUp) { why ->
            // `app` is the one module that sees both sides, so the translation from what the
            // radio said to what a host can act on happens here — `ui` never learns that Bonjour
            // exists, and `platform` never learns that there is a screen.
            onRefused(
                when (why) {
                    AdvertiseFailure.NameAlreadyOnTheAir -> HostRefusal.NameAlreadyOnTheAir
                    AdvertiseFailure.Refused -> HostRefusal.CouldNotGoOnTheAir
                }
            )
        }

    override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) {
        residentName = name
        if (clientJob?.isActive == true) return
        connect(home, name, onStanding, session ?: ClientSession(), mayStartOver = true)
    }

    /**
     * One client, run on [s], until it is dismissed or the caller hangs up.
     *
     * [mayStartOver] is the fuse on the automatic rejoin below: the second attempt is a stranger
     * saying Hello, and a Hello can never be refused for holding an unknown token, so this cannot
     * recur — but a recursion whose bound is an argument about somebody else's protocol is a
     * recursion, and one that only ever runs once is not.
     */
    private fun connect(
        home: NearbyHome,
        name: String,
        onStanding: (LobbyBody.Standing) -> Unit,
        s: ClientSession,
        mayStartOver: Boolean,
    ) {
        session = s
        val c = TransportClient(
            s, home.address, home.port,
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = { frame ->
                val carried = (frame as? TransportFrame.Carry)?.body
                carried?.let(onInbound)
                val body = carried?.let(LobbyWire::decodeOrNull)
                // Only the standing is acted on. Either upward body arriving at a client would be
                // the house sending one phone's text to another, which nothing on the host side
                // does — and if it ever did, ignoring it here is the fail-closed direction.
                if (body is LobbyBody.Standing) onStanding(body)
            },
            onPhase = { phase ->
                when (phase) {
                    is ClientSession.Phase.Seated -> {
                        // The token says WHO this phone is and the address says WHERE the house
                        // is; a resume needs both. A failed write is reported, never swallowed —
                        // a phone that cannot store its token cannot come back from a crash, and
                        // the host is the only one who can be told (D-087, D-084).
                        try {
                            seat.keep(phase.token.value, home.address)
                        } catch (notSaved: IllegalStateException) {
                            onEvent("TOKEN NOT SAVED — NO RESUME AFTER A CRASH")
                        }
                        // On every seating, not only the first: a phone that dropped and came back
                        // to the seat its token owns arrives at a desk that dropped its name with
                        // it, and a lobby listing an empty chip for somebody standing in the room
                        // is a fault nobody could diagnose from the screen. The name is never
                        // persisted — it is re-sent from what this phone still has in memory, or
                        // not at all.
                        client?.let { live -> scope.launch { live.send(naming(name)) } }
                    }
                    is ClientSession.Phase.Dismissed -> {
                        onEvent("REFUSED BY THE HOUSE — ${phase.reason}")
                        if (mayStartOver && phase.reason == TransportRefusal.UnknownToken) {
                            startOver(home, name, onStanding)
                        }
                    }
                    is ClientSession.Phase.Joining, is ClientSession.Phase.Rejoining -> Unit
                }
            },
        )
        client = c
        clientJob = scope.launch { c.run() }
    }

    /**
     * **The stored seat belonged to a house this one is not: drop it and walk in as a stranger.**
     *
     * The refusal is right and stays right — [ClientSession] presents *that* seat or nothing, and
     * a house that never issued the token must turn it away (D-087). What was wrong was the
     * silence after it (E-D3-1): the phone sat on NOT SEATED YET forever, the host saw a party
     * that never arrived, and neither screen said one word. It happens on the second evening of
     * every household that plays twice.
     *
     * **The ruling is that the player is shown nothing at all**, so this is not a message, a
     * refusal or a state the eye can catch — the token goes, a fresh session says Hello, and the
     * lobby fills the way it does for a phone that had never played. The record is a log line on
     * this phone and the refusal the house recorded at its own end; between them the two sides
     * can say what happened, and the person in the dark hallway has nothing to read.
     *
     * Only [TransportRefusal.UnknownToken]. A locked round and a full house are refusals a fresh
     * Hello would collect again — starting over on those would be a phone hammering a door it has
     * been told is shut.
     */
    private fun startOver(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) {
        seat.forget()
        onEvent("THE STORED SEAT IS NOT THIS HOUSE'S — JOINING FRESH")
        val dismissed = clientJob
        scope.launch {
            // The dismissed client returns from `run` immediately after this phase; joining it is
            // what keeps two clients off one seam rather than a delay somebody would have to
            // justify.
            dismissed?.join()
            // Hung up while the refusal was in flight: `leave` drops the job, and a rejoin after
            // that would be this phone walking back into a lobby its owner has left.
            if (clientJob !== dismissed) return@launch
            connect(home, name, onStanding, ClientSession(), mayStartOver = false)
        }
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

    /** The host's ballot length, to the desk beside this phone. See [LobbyHouse.setVoteWindow]. */
    override fun setVoteWindow(seconds: Int) = house.setVoteWindow(seconds)

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
