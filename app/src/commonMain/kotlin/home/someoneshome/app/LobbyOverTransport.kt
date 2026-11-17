package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.GameState
import home.someoneshome.model.Intent
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.RadioCause
import home.someoneshome.model.SavedHome
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportRefusal
import home.someoneshome.platform.clearOwedReports
import home.someoneshome.platform.clearSeatToken
import home.someoneshome.platform.loadOwedReports
import home.someoneshome.platform.loadSeatToken
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.saveHostAddress
import home.someoneshome.platform.saveOwedReports
import home.someoneshome.platform.saveSeatToken
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.transport.AdvertiseFailure
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.EffectWire
import home.someoneshome.platform.transport.HostAdvertiser
import home.someoneshome.platform.transport.HostBrowser
import home.someoneshome.platform.transport.IntentDesk
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    /**
     * **The reports this phone owes the house, as last kept, or null** (D-245). Beside the token,
     * because a report owed at a leave has to outlive the process the way the seat does: iOS ends
     * an app whose Camera or Motion grant is withdrawn. Text the round half wrote; see
     * `ContactUploader`. Nothing by default, for a store that keeps no reports.
     */
    fun owedReports(): String? = null

    /** Keep them. **Throws** when the write did not happen, as [keep] does. */
    fun keepOwedReports(text: String) = Unit

    /** Forget them: the house has every one, or the round is over. */
    fun forgetOwedReports() = Unit
}

/** [SeatStore] on the phone's own filesystem: story 0.8's two stores, and the owed reports, behind one door. */
object DeviceSeat : SeatStore {
    override fun stored(): String? = loadSeatToken()
    override fun keep(token: String, hostAddress: String) {
        saveSeatToken(token)
        saveHostAddress(hostAddress)
    }
    override fun forget() = clearSeatToken()
    override fun owedReports(): String? = loadOwedReports()
    override fun keepOwedReports(text: String) = saveOwedReports(text)
    override fun forgetOwedReports() = clearOwedReports()
}

/**
 * **A [SeatStore] that lives exactly as long as this object** — what a round half keeps its owed
 * reports in when its root names no store. A process ending loses it, which is the behaviour before
 * D-245; [DeviceSeat] is what a phone's root passes.
 */
class MemorySeat : SeatStore {
    private var token: String? = null
    private var owed: String? = null
    override fun stored(): String? = token
    override fun keep(token: String, hostAddress: String) {
        this.token = token
    }
    override fun forget() {
        token = null
    }
    override fun owedReports(): String? = owed
    override fun keepOwedReports(text: String) {
        owed = text
    }
    override fun forgetOwedReports() {
        owed = null
    }
}

/**
 * **What puts a home on the air, and hands back the way to take it off again** (H2, D-225).
 *
 * A seam for [SeatStore]'s reason: the behaviour the owner ruled on is what the house does when
 * its phone leaves the foreground and comes back — off the air, then on it again under the same
 * name and port — and a test cannot count advertisements against a Bonjour stack it does not have.
 * [DeviceAdvertising] is what every production construction gets.
 *
 * The callbacks are [HostAdvertiser]'s, unchanged: exactly one of [onAir] and [onFailed], and
 * neither at the call.
 */
fun interface Advertising {
    fun advertise(
        name: String,
        port: Int,
        onEvent: (String) -> Unit,
        onAir: () -> Unit,
        onFailed: (AdvertiseFailure) -> Unit,
    ): () -> Unit
}

/** [Advertising] over Bonjour: story 0.8's [HostAdvertiser], started on the caller's (main) thread. */
val DeviceAdvertising: Advertising = Advertising { name, port, onEvent, onAir, onFailed ->
    val advertiser = HostAdvertiser(name, port, onEvent, onAir, onFailed)
    advertiser.start()
    advertiser::stop
}

/**
 * **How many times a house coming back tries its own port before saying it cannot**, and how long
 * it waits between tries (H2, D-225).
 *
 * The port is reopened with address reuse, so the first try is expected to land; the rest are for
 * a server whose last connections are still closing. A device-side patience like
 * [ADVERTISE_PATIENCE_MILLIS]: no rule reads it and it stamps into no recording.
 */
private const val REOPEN_TRIES: Int = 10
private const val REOPEN_PAUSE_MILLIS: Long = 500

/**
 * **The round's client half, handed this phone's own socket** — the seam between the lobby's
 * connection and [RoundOverTransport].
 *
 * It exists because there is exactly **one** socket per phone and the lobby owns it. A round that
 * opened a second connection would be a second seat as far as [SeatLedger] is concerned, and the
 * phone would be attributed two ways at once; a round that could not reach the first one could not
 * receive a push at all. So the lobby hands its socket over rather than the round going and
 * getting one.
 *
 * **Null in every production construction**, exactly as [LobbyHouse.onIntent] is empty in every
 * production construction, and for the same reason: nothing arms a round yet, so there is nothing
 * for a push to be about. What fills it today is the rehearsal bench, which is the first caller
 * this seam has ever had — see `RoundCheat`.
 *
 * The three methods are the three moments the round's client half cares about and no others:
 * a connection exists, a frame arrived on it, and this phone has (re-)taken its seat.
 */
interface RoundSeam {

    /**
     * A client is up: here is the way up it. Called again on every reconnect, because the old
     * `send` closes over a socket that is gone.
     */
    fun linked(send: suspend (TransportFrame) -> Unit)

    /**
     * A frame arrived on this phone's connection. **The whole frame, not the decoded body** — the
     * round's inbox reads a sequence number off it, and a decoder in between would be a second
     * opinion about what arrived.
     */
    fun arrived(frame: TransportFrame)

    /**
     * This phone is seated, on this connection. D-097's re-assertion rides this: a phone that
     * dropped and came back has missed an unknown amount and has to ask.
     */
    fun seated()

    /**
     * **A new round: this phone holds nothing of the last one** (D-157).
     *
     * The sequence a client is at belongs to one round. Carried into the next it would make that
     * round's opening pushes look like frames this phone already held, and the screen would sit on
     * the lobby while the house talked past it. The host's own half of this already existed
     * (`LobbyHouse.roundEnded`); this is the client's, reached when the host's push arrives.
     */
    fun roundEnded()

    /**
     * **This phone hung up: it left, or the host closed the house** (T4, D-221).
     *
     * The phone holds nothing of the round it walked out of, so by default this is [roundEnded]:
     * the radio is silenced and the screen is let sleep. Before T4 a phone that left mid-round kept
     * its radio on, and with the screen held awake it would have kept the screen on too.
     */
    fun left() = roundEnded()

    /**
     * **This phone left the foreground** (D-187, D-229). On every phone, host and guest alike; the
     * round half marks the onset if it is in a round. Nothing by default, for a seam with no round.
     */
    fun leftTheForeground() = Unit

    /** **This phone is back** (D-229). The round half tells the house once, if it left in a round. */
    fun backInTheForeground() = Unit
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
    /**
     * **A request from one phone, attributed, on its way to the rules.**
     *
     * [emit]'s mirror and the other half of the same seam: [emit] is the authority's exit to the
     * phones, and this is the phones' entrance to the authority. The [Intent] handed over here has
     * already had its seat stamped from the connection the frame arrived on — see [IntentDesk],
     * which is where that happens and the only place it can.
     *
     * **Empty in every production construction today**, exactly as [emit] has no production caller,
     * and for the same reason: there is no round for a request to change, because nothing arms one
     * (E-D3-3's remaining half). What is joined here is `core`'s `admit(state, intent, at)` — the
     * one gate, which the in-process source reaches by the same call.
     */
    private val onIntent: suspend (Intent) -> Unit = {},
    /**
     * **A phone held its Egress tile down** ([RoundBody.Fire], E-P2-5 / E-P3-7).
     *
     * [onIntent]'s sibling and deliberately a second seam rather than a member of it: what this
     * one owes the caller is a **draw** before there is a fact to admit — the Egress's type and its
     * two nodes come from the home's shape, which the rules have never held. `RoundAuthority.
     * fireEgress` is what fills it, and that is the only thing in the app that can.
     *
     * [Seat] is the connection's, stamped by [IntentDesk] exactly as an intent's actor is. A client
     * has no way to name one — see [RoundBody.Fire], which carries nothing but its sequence.
     */
    private val onEgressFired: suspend (Seat) -> Unit = {},
    /**
     * **What one phone's radio heard, on its way to the contact resolver** ([RoundBody.Sighted],
     * D-212, D-217).
     *
     * [onEgressFired]'s sibling, and a seam of its own for the same kind of reason: an upload is
     * neither a request nor a fact yet. It is what a radio heard, and the contact is the house's
     * conclusion (`resolveContacts`, D-213), drawn above the rules from a token book nothing else
     * holds. `RoundAuthority.sighted` is what fills it. The seat is the connection's, exactly as a
     * request's actor is; the upload carries none and has nowhere to put one.
     */
    private val onSighted: suspend (Seat, RoundBody.Sighted) -> Unit = { _, _ -> },
    /**
     * **A phone saying its radio went down, and why** ([RoundBody.RadioFaulted], T2, D-222; D-223).
     *
     * [onSighted]'s sibling: a fact one phone reports about itself, which `RoundAuthority.radioFaulted`
     * puts to the rules. A person's act is named at the next meeting; the phone's own failure is
     * recorded and named nowhere (D-187). The seat is the connection's; the body carries the cause
     * and the onset's random name, by which the authority hears one onset once (D-245).
     */
    private val onRadioFaulted: suspend (Seat, RoundBody.RadioFaulted) -> Unit = { _, _ -> },
    /** What puts the home on the air. [DeviceAdvertising] in every production construction. */
    private val advertising: Advertising = DeviceAdvertising,
) {

    private val desk = LobbyDesk()
    private var host: TransportHost? = null

    /** Takes the advertisement off the air, or null when nothing is on it. */
    private var advertiser: (() -> Unit)? = null

    /**
     * **The house is up but this phone has left the foreground** (H2, D-225), so the server is
     * stopped and the advertisement is off, and both come back at [foregrounded].
     */
    private var away = false

    /** The server's last stop, awaited before the port is bound again (and by a test, before it ends). */
    internal var closing: Job? = null
        private set

    /**
     * **The home this house came up under, or none because this phone is not hosting.**
     *
     * Written by [start] and cleared by [stop], so it is true exactly while there is a house — and
     * it is the whole of [ArmingInputs.home]. Held beside the server rather than passed to arming
     * separately, because *the hosted home is the round's home by definition*: two ways to say
     * which house this is would be two answers that could differ, and the one that differed would
     * be the one the work orders were drawn from.
     */
    private var hosted: SavedHome? = null

    /**
     * **The round's outbound half, on the same server the lobby is on** (D-171's successor work).
     *
     * Here rather than in a class of its own because there is one house: one ledger deciding who is
     * seated, one socket per seat, and therefore one thing that may write down them. A second
     * object holding its own sockets would be a second delivery order, and the two would be right
     * about different rounds.
     *
     * Its `send` is the server's, so the host's own phone — which joined its own house over
     * loopback like everybody else — is reached by the same call as the phone in the next room.
     */
    private val effects = EffectWire(
        send = { seat, frame ->
            val server = host ?: error("the house is not up — the frame was not sent")
            server.send(seat, frame)
        },
        // Rule 6 the right way up: the player is told nothing and the authority is told at once.
        // The seat keeps its place in the stream and collects the message when it asks, so this is
        // a report of a delay rather than of a loss.
        onUnreachable = { seat -> onEvent("A SEAT COULD NOT BE REACHED — ${seat.index}") },
    )

    /**
     * **The round's inbound half, on the same server the outbound half is on.**
     *
     * Beside [effects] for [effects]'s reason: there is one house, one ledger deciding who is
     * seated, and one socket per seat — so the thing that reads up those sockets and the thing that
     * writes down them are two halves of one desk rather than two objects with two opinions about
     * who seat four is.
     *
     * **The seat it stamps is the connection's**, which is the whole security property of the
     * upward channel and is argued in full at [IntentDesk].
     */
    private val intents = IntentDesk(
        // Rule 6 again: a phone whose frames are being lost on the way up is a player pressing
        // controls that do nothing, and neither they nor anybody else could tell from a screen.
        onSkipped = { seat, missed -> onEvent("A SEAT'S REQUESTS SKIPPED $missed — ${seat.index}") },
    )

    /**
     * **Everything that arrives from a phone and matters to the round, in one ordered queue.**
     *
     * D4 made literal at the host end, and it is not a precaution — the first build of this ran
     * each arriving request in its own `launch`, and `IntentLoopTest` caught it immediately: the
     * desk and the emit boundary both suspend, so two requests from **one phone** would interleave
     * and reach the rules in the order they finished rather than the order the player pressed them.
     * A `LockVote` overtaking its own `SelectVote` locks a ballot the couch never watched being
     * made, which is D-117's whole point undone by a scheduler.
     *
     * ### A seating rides the same queue, for the same reason
     *
     * [IntentDesk.seated] clears that seat's mark, and a seating that raced the frames arriving
     * behind it would clear the mark *after* the first request of the new connection had already
     * been judged against the old one — so the phone's first press after a reconnect would be
     * dropped as a duplicate, in silence. Ordering the two against each other is the fix, and one
     * queue is what ordering them against each other means.
     */
    private sealed interface Arrival {

        /** A phone has a new connection, from [TransportHost.onSeated]. */
        data class Seated(val seat: Seat) : Arrival

        /** A control somebody pressed. [seat] is the connection's; see [IntentDesk]. */
        data class Asked(val seat: Seat, val act: RoundBody.Act) : Arrival

        /**
         * **The Egress tile, held down.** [seat] is the connection's, exactly as [Asked]'s is.
         *
         * A separate arrival because it is answered differently and not because it arrives
         * differently: an [Asked] is translated and admitted, and this one needs the house to make
         * a draw first. See [RoundBody.Fire], where the whole argument is. It rides the same queue,
         * so one phone's Egress and one phone's check-in still reach the rules in the order the
         * player made them.
         */
        data class Fired(val seat: Seat, val fire: RoundBody.Fire) : Arrival

        /**
         * **A phone's connection dropped, from [TransportHost.onLost]** (D-203).
         *
         * A phone that is gone cannot say it stopped reading the Terminal, and the station would
         * stay held for the rest of the round. So the house says it on that seat's behalf, as
         * [Intent.LeaveTerminal] -- the request the phone would have sent -- and it rides this
         * queue so that it reaches the rules after everything the phone did before it dropped.
         * From a seat that is not reading, it changes nothing.
         */
        data class Lost(val seat: Seat) : Arrival

        /**
         * **What a phone's radio heard** ([RoundBody.Sighted], D-217). On the same queue as every
         * request, so a touch reaches the rules in the order it arrived relative to everything
         * else that phone did; launched separately it would land in the order it finished.
         */
        data class Sighted(val seat: Seat, val upload: RoundBody.Sighted) : Arrival

        /** **A phone's radio went down** ([RoundBody.RadioFaulted], T2, D-222; D-223). On the ordered queue. */
        data class RadioFaulted(val seat: Seat, val report: RoundBody.RadioFaulted) : Arrival
    }

    private val arriving = Channel<Arrival>(Channel.UNLIMITED)
    private var attending: Job? = null

    /** Whether this attempt at hosting has had its one answer yet. See [ADVERTISE_PATIENCE_MILLIS]. */
    private var answered = false

    /** True once the server is up. */
    var port: Int? = null
        private set

    /**
     * Bring the house up under [home]'s name and call [onUp] with where it landed — **or
     * [onRefused] when the home could not go on the air at all.**
     *
     * The name goes on the air for the whole network before any permission this app controls, so
     * it carries the home's name and nothing a round produced — no roles, no seats, no state.
     * **Nothing else about [home] leaves this phone**: the map and the plan are what the arming
     * draw and the Egress draw are made of, and both of those run here.
     *
     * ### It takes the home and not its name, and that is the seam E-PAY-3 was missing
     *
     * A house that knew only what it was *called* could not tell the round which house it was in,
     * so the round was played in a fixture — five rooms nobody owns, ten cards nobody printed. The
     * home arrives here because this is the one call that means *this phone is now hosting that
     * house*, and binding the two together is what makes [ArmingInputs.home] impossible to
     * disagree with.
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
        home: SavedHome,
        onUp: (NearbyHome) -> Unit,
        onRefused: (AdvertiseFailure) -> Unit = {},
    ) {
        if (host != null) return
        answered = false
        val homeName = home.name
        hosted = home
        val ledger = SeatLedger(
            List(LOBBY_SEATS) { Seat(it) },
            // The app root is where real randomness belongs (the ledger's own KDoc): tokens never
            // enter a recording, and the determinism rules are about what does. Secure, because a
            // token a phone could guess is a resume as somebody else's seat (D-215).
            mint = ::mintSeatToken,
        )
        val server = TransportHost(
            ledger,
            nowMillis = { monotonicNanos() / 1_000_000 },
            onFrame = ::onFrame,
            // The intent desk's mark is cleared on the same fact, because it is the same fact: a
            // phone on a new connection begins numbering its requests again, and a desk still
            // holding the old mark would take every one of them for a duplicate and drop it in
            // silence. See IntentDesk.seated.
            // The lobby's desk is not on the ordered queue, because a standing is a broadcast and
            // orders against nothing; the intent desk's mark rides the queue -- see Arrival, and
            // the silent failure it exists to prevent. **But the lobby's desk is never changed
            // from here either**: these run on the server's connection threads, one per socket and
            // several at once, so it is changed through [atTheDesk] (D-227).
            onSeated = { seat -> atTheDesk { desk.seated(seat); publish() }; arriving.trySend(Arrival.Seated(seat)) },
            onLost = { seat -> atTheDesk { desk.left(seat); publish() }; arriving.trySend(Arrival.Lost(seat)) },
            // The authority's record of a connection turned away — the reason and never the
            // token (E-D3-1). A refused resume is the one the host most needs: it means a phone
            // in this room is holding a seat from an evening this house never ran.
            onRefused = { reason -> onEvent("A CONNECTION WAS REFUSED — $reason") },
            socketWrite = { seat, write -> socketWrite(seat, write) },
        )
        host = server
        attending = scope.launch { attend() }
        scope.launch {
            val bound = server.start(port = 0)
            port = bound
            onEvent("HOSTING '$homeName' ON :$bound")
            advertiser = advertising.advertise(
                homeName, bound,
                onEvent = onEvent,
                // Loopback, deliberately: the host is a player and joins its own house on the
                // same path everybody else uses.
                onAir = { answer { onUp(NearbyHome(homeName, "127.0.0.1", bound)) } },
                onFailed = { why -> answer { stop(); onRefused(why) } },
            )
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

    /**
     * **One effect, to every seat the allowlist permits it to and no other** (rule 2, D-079).
     *
     * The authority's whole exit to the phones. It takes the state *after* the event, because the
     * round-state axis moves because of the event being processed — classifying against the state
     * before it hands a revoked player one last frame of living-client permissions.
     *
     * **Nothing in production calls this yet, and that is E-D3-3's remaining half.** The pipeline
     * below it is real and proven end to end; what is missing above it is a round being armed —
     * `armingFor` still has no production caller, exactly as W1 left it. The escalation's first
     * half, *there is no host→client effect push for any effect*, is closed here.
     */
    suspend fun emit(effect: Effect, state: GameState) = effects.emit(effect, state)

    /**
     * **Everything the lobby holds that a round is armed out of**, in one call rather than as a
     * set of readers on the desk.
     *
     * One method because it is one moment: `Event.RoundArmed` takes the seats, the names, the
     * lines, the host's Insider setting and the host's ballot length together, and *balance values
     * lock at arming*. A class offering five separate getters would be offering five chances to
     * read them at five different instants, which is the one thing the arming event exists to make
     * impossible.
     *
     * **The lines are in it, and this is the only reader of them outside the desk.** [LobbyDesk]'s
     * own KDoc names the house as the one permitted caller, and this is the house: the lines go
     * straight onto the arming event, live on `GameState`, and are dropped by the same transition
     * that publishes them at the reveal (D-116, L6). Nothing between here and there keeps a copy —
     * in particular this is never logged, and the bench that drives it prints a count.
     */
    fun armingInputs(): ArmingInputs {
        val named = desk.namedSeats()
        return ArmingInputs(
            seats = named.map { it.seat }.sortedBy { it.index },
            names = named,
            oneLines = named.mapNotNull { n -> desk.lineOf(n.seat)?.let { OneLineHeld(n.seat, it) } },
            chosenInsiders = desk.insiders,
            voteWindowSeconds = desk.voteWindowSeconds,
            readyToArm = desk.readyToArm(),
            // Read in the same instant as the seats and the lines, which is this method's whole
            // reason: the home is what the work orders are drawn from, and a home read a moment
            // later would be a house that could have been taken down in between.
            home = hosted,
        )
    }

    /**
     * **CLOSE THE HOUSE — the host ends the party, and every phone is told before it goes** (N5b,
     * E-PAY-6).
     *
     * [roundEnded]'s sibling in shape and its opposite in consequence. That one wipes the desk and
     * **keeps the house up**, because D-157 plays a second round in the same lobby. This one takes
     * the house down: the server stops, the advertisement comes off the air, and the desk goes with
     * the object.
     *
     * ### The announcement goes first, and the ordering is the whole of it
     *
     * [stop] closes every socket. A `HouseClosed` sent afterwards would be a frame written to
     * connections that no longer exist, and five phones would learn the house was gone by noticing
     * their counts had stopped — which is the trap this closes, arriving by a different road. So the
     * broadcast is queued while the server is still up, and the teardown follows it.
     *
     * **The lines are dropped on the way out.** They were typed on the strength of *deleted when the
     * round ends*, and a house being closed before any round started is a stronger case of the same
     * promise, not a weaker one.
     */
    fun houseClosed() {
        announce(TransportFrame.Carry(LobbyWire.encode(LobbyBody.HouseClosed)))
        desk.roundEnded()
        stop()
    }

    /** **Deleted when the round ends** — the host's half of the promise. */
    fun roundEnded() {
        desk.roundEnded()
        // **And every other phone is told**, which is what E-R2-10 / E-P3-5 was: the wipe below is
        // this desk's, the walk back to the lobby is each phone's, and nothing joined them. Sent
        // BEFORE `publish`, so a client acts on the moment and then receives the standing that
        // moment produced — the other order would hand it a lobby of zero lines it had no reason
        // to expect yet.
        announce(TransportFrame.Carry(LobbyWire.encode(LobbyBody.RoundEnded)))
        // The retained streams are a round's worth of one house's messages and outlive nothing.
        // Wiped in the same method the lines are, so what the house holds about an evening ends
        // when the evening does rather than when somebody remembers.
        scope.launch { effects.roundEnded() }
        publish()
    }

    fun stop() {
        attending?.cancel()
        attending = null
        advertiser?.invoke()
        advertiser = null
        // **Read into a local before the launch.** This used to be `scope.launch { host?.stop() }`
        // followed by `host = null`, and on a dispatcher that runs the launch later — the main
        // thread's, which is where every production call comes from — the field was already null
        // by then and the server was never stopped: CLOSE THE HOUSE took the house off the air and
        // left its port bound.
        val server = host
        closing = scope.launch { runCatching { server?.stop() } }
        host = null
        port = null
        away = false
        // The house is down, so this phone is hosting nothing — and a stale home here would let a
        // later arming draw from a house that is no longer on the air.
        hosted = null
    }

    /** True while this phone is running a house, whether or not it is in the foreground. */
    val up: Boolean get() = host != null

    /**
     * **This phone left the foreground: take the house off the air, and keep everything it knows**
     * (H2, D-225).
     *
     * iOS suspends an app that leaves the foreground, and a suspended app keeps neither its
     * listening socket nor its Bonjour advertisement. On 2026-11-16 a host that auto-locked in the
     * lobby woke with both gone and nothing that would bring them back: every seat dropped, and
     * LIGHTS OUT then reached a house with nobody in it. So the house stops them itself while it
     * still can, and the connections close cleanly — each phone learns at once that it is off the
     * line, rather than at its next write, and starts the reconnect it already has.
     *
     * **What survives is the ledger, the desk and the round.** The ledger is what knows which token
     * holds which seat, so it is the one thing a seat coming back needs; the server object keeps
     * it and is started again at [foregrounded]. Each lost connection reaches the desk and the
     * round's queue exactly as any lost connection does (`onLost`), which is existing behaviour
     * and is not changed here: in a round that is D-203's `LeaveTerminal` for each seat.
     *
     * Nothing here touches a screen, the lamp or a rule (rules 5 and 6). The host's own phone is
     * one of the seats that drops, and its lobby says so in the words it already has — see
     * `LobbyOverTransport`'s `Rejoining`.
     */
    fun backgrounded() {
        val server = host ?: return
        if (away) return
        away = true
        advertiser?.invoke()
        advertiser = null
        onEvent("THIS PHONE LEFT THE FOREGROUND — THE HOUSE IS OFF THE AIR UNTIL IT IS BACK")
        closing = scope.launch { runCatching { server.stop() } }
    }

    /**
     * **This phone is back: reopen the same port with the same ledger, and go back on the air**
     * (H2, D-225).
     *
     * The same port, because that is where every phone in the house is dialling: a client holds
     * the address and port it joined on and its seat token, and resumes by presenting the token
     * there (D-087, D-094). Each one is answered with **that** seat, because the ledger that
     * minted the token is the one listening — no lobby code, no fresh seat, no second opinion
     * about who seat four is.
     *
     * The advertisement goes back up for the phones that have not joined yet. If Bonjour refuses
     * it this time, the house stays up and says so in its log: every seated phone comes back by
     * address and needs no advertisement, so a refusal here costs new arrivals and nobody already
     * in the room. A port that cannot be reopened is the one failure that strands the room, and it
     * is said in so many words.
     */
    fun foregrounded() {
        val server = host ?: return
        if (!away) return
        away = false
        val home = hosted ?: return
        val at = port ?: return
        val stopping = closing
        scope.launch {
            stopping?.join()
            val bound = reopen(server, at)
            // Closed, left or backgrounded again while the port was being reopened: whatever this
            // coroutine just bound is not the house any more.
            if (host !== server || away) {
                if (bound != null) runCatching { server.stop() }
                return@launch
            }
            if (bound == null) {
                onEvent("COULD NOT REOPEN :$at — NO SEAT CAN COME BACK TO THIS HOUSE")
                return@launch
            }
            onEvent("BACK IN THE FOREGROUND — LISTENING AGAIN ON :$bound · SEATS COME BACK BY THEIR OWN TOKENS")
            advertiser = advertising.advertise(
                home.name, bound,
                onEvent = onEvent,
                onAir = { onEvent("BACK ON THE AIR AS '${home.name}'") },
                onFailed = { why ->
                    onEvent("NOT BACK ON THE AIR — $why · SEATED PHONES RETURN BY ADDRESS, A NEW ONE CANNOT FIND THIS HOME")
                },
            )
        }
    }

    /** The port this house had, bound again; null after [REOPEN_TRIES] refusals. */
    private suspend fun reopen(server: TransportHost, at: Int): Int? {
        repeat(REOPEN_TRIES) { attempt ->
            try {
                return server.start(at)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (refused: Throwable) {
                onEvent("REOPENING :$at — TRY ${attempt + 1} OF $REOPEN_TRIES REFUSED (${refused::class.simpleName})")
                delay(REOPEN_PAUSE_MILLIS)
            }
        }
        return null
    }

    /**
     * **The one consumer of [arriving], and the only place a request reaches the rules.**
     *
     * One coroutine, draining in order, forever. Everything it does suspends — the desk takes a
     * lock, the rules are the caller's — so the whole point is that it does them one at a time.
     *
     * **And the four seams are `suspend` and awaited, which is what makes "one at a time" true**
     * (R6, D-241). They were plain functions, and the app root's bodies launched a coroutine each
     * and returned, so this loop took the next arrival while the last was still pushing: the queue
     * was ordered and the rules were not. See [HouseDoors].
     */
    private suspend fun attend() {
        for (arrival in arriving) {
            when (arrival) {
                is Arrival.Seated -> intents.seated(arrival.seat)
                is Arrival.Asked -> when (val landing = intents.arrived(arrival.seat, arrival.act)) {
                    is IntentDesk.Landing.Put -> onIntent(landing.intent)
                    // Heard once already, which is exactly once. Silent by design: the first copy
                    // produced whatever it produced and it went down this seat's stream, and a
                    // phone still waiting for that answer has a hole in its push sequence, which
                    // its own inbox notices and asks about.
                    is IntentDesk.Landing.Duplicate -> Unit
                    // A control press cannot ask the house for a draw — the desk answers `Draw` to
                    // `RoundBody.Fire` and to nothing else, and this branch is what says so rather
                    // than an `else` that would quietly accept one if the desk ever changed.
                    is IntentDesk.Landing.Draw -> Unit
                }
                // **The Egress tile, through the same desk and the same dedup mark.** What comes
                // back names the seat and nothing else, and the draw is the authority's: it needs
                // the home's shape, which the rules have never held.
                is Arrival.Fired -> when (val landing = intents.arrived(arrival.seat, arrival.fire)) {
                    is IntentDesk.Landing.Draw -> onEgressFired(landing.actor)
                    is IntentDesk.Landing.Duplicate -> Unit
                    // A `Fire` carries no intent, so there is nothing here a `Put` could hold.
                    is IntentDesk.Landing.Put -> Unit
                }
                // **The house says the reader left, because the reader cannot** (D-203). Not through
                // the desk: there is no frame and no sequence, only a connection that closed.
                is Arrival.Lost -> onIntent(Intent.LeaveTerminal(arrival.seat))
                // **The radio, to the resolver** (D-217). Not through the desk: an upload carries
                // no sequence by design (D-212), and the resolver recognises a second delivery by
                // the slots themselves (D-213).
                is Arrival.Sighted -> onSighted(arrival.seat, arrival.upload)
                // **The radio fault, to the rules, then answered** (T2, D-222; D-245). Not through
                // the desk: the body has no sequence. A phone keeps a report until it is answered
                // and sends it again on every resume, so one onset can arrive twice, and the
                // authority hears each onset once by its name. The answer goes to this seat
                // whatever the rules made of it (rule 1), after they are done with it.
                is Arrival.RadioFaulted -> {
                    onRadioFaulted(arrival.seat, arrival.report)
                    answerFault(arrival.seat, arrival.report.onset)
                }
            }
        }
    }

    /**
     * A frame from a seat.
     *
     * Anything that is not a lobby body is left alone: a `Carry` may hold all sorts of things, and
     * a lobby that treated an unrecognised body as an error would take a player's connection down
     * over a message meant for somebody else.
     */
    private fun onFrame(seat: Seat, frame: TransportFrame) {
        val carried = (frame as? TransportFrame.Carry)?.body ?: return
        // **A seat saying what it is missing, answered by re-assertion** (D-097). Tried before the
        // lobby's bodies rather than after, because a phone catching up mid-round is the one
        // exchange on this socket that is time-critical: it is a screen that has stopped being
        // right, and every frame it waits is a frame the player is reading something stale.
        RoundWire.decodeOrNull(carried)?.let { round ->
            when (round) {
                is RoundBody.Gap -> scope.launch { effects.reassert(seat, round.held) }
                // The house's to send, never to receive. A client that pushed one is claiming to be
                // the authority, and it is told nothing at all.
                is RoundBody.Push -> Unit
                // **A control somebody pressed.** The seat handed to the desk is `seat`, which is
                // this connection's and came from the ledger; the frame contributes nothing to it
                // and has nowhere to put a rival claim.
                //
                // Launched rather than called inline for the reason the gap above is: `onFrame`
                // runs on the connection's own read coroutine, and the desk is shared across all
                // of them. Within one seat the order still holds -- that read loop is single and
                // submits in order.
                // Queued rather than launched: `onFrame` runs on this connection's own read
                // coroutine, and the desk and the emit boundary both suspend. Two launches from
                // one phone would reach the rules in the order they FINISHED. See Arrival.
                is RoundBody.Act -> arriving.trySend(Arrival.Asked(seat, round))
                // **The Egress tile.** Queued on the same channel as a control press, for the same
                // reason: two arrivals from one phone must reach the rules in the order the player
                // made them, and two launches would reach them in the order they finished.
                is RoundBody.Fire -> arriving.trySend(Arrival.Fired(seat, round))
                // **What a phone's radio heard, onto the ordered queue** (D-217). The resolver
                // judges it there; an upload the house cannot read resolves to nothing.
                is RoundBody.Sighted -> arriving.trySend(Arrival.Sighted(seat, round))
                // **A phone saying its radio went down** (T2, D-222), onto the same ordered queue.
                is RoundBody.RadioFaulted -> arriving.trySend(Arrival.RadioFaulted(seat, round))
                // The house's to send, never to receive (D-245).
                is RoundBody.FaultHeard -> Unit
            }
            return
        }
        val body = LobbyWire.decodeOrNull(carried) ?: return
        when (body) {
            // Deliberately not logged, not counted per player, and not echoed. The only trace a
            // line leaves is the count going up by one.
            is LobbyBody.Handover -> atTheDesk {
                desk.handedOver(seat, body.line)
                publish()
            }
            // The name does go back down, to every phone, which is the whole of D-115 — and it
            // goes down as part of the standing, never as an echo of the body that brought it.
            is LobbyBody.Naming -> atTheDesk {
                desk.named(seat, body.name)
                publish()
            }
            // The standing is the host's to send, never to receive. A client that sent one is
            // saying nothing this house is obliged to hear.
            is LobbyBody.Standing -> Unit
            // **Downward only, and this direction is where it would bite**: a client that could
            // end the round would be one phone wiping the desk's lines and returning five other
            // people to the lobby, mid-round, with no control anywhere that asked for it. NEW ROUND
            // is the host's (D-157) and reaches the rest as the push below.
            is LobbyBody.RoundEnded -> Unit
            // **Downward only, exactly as above, and this one would bite harder**: a client able to
            // send it would be one guest closing everybody else's house from a lobby they had just
            // walked into. CLOSE THE HOUSE is the host's control and reaches the rest as the
            // broadcast in [houseClosed].
            is LobbyBody.HouseClosed -> Unit
        }
    }

    private fun publish() = announce(TransportFrame.Carry(LobbyWire.encode(desk.standing())))

    /**
     * **"I have your report of [onset]"**, to the one seat that sent it (`RoundBody.FaultHeard`,
     * D-245). The phone stops keeping it. A seat whose connection has gone by now misses this, keeps
     * the report, and sends it again when it is back; the authority hears it once and this answers
     * it again. Queued on the seat's own connection and never waited on (R12).
     */
    private suspend fun answerFault(seat: Seat, onset: String) {
        val server = host ?: return
        try {
            server.send(seat, TransportFrame.Carry(RoundWire.encode(RoundBody.FaultHeard(onset))))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onEvent("A REPORT COULD NOT BE ANSWERED — SEAT ${seat.index} · IT IS SENT AGAIN ON ITS RETURN")
        }
    }

    /**
     * **Every change a phone makes to the desk, made on the house's own scope and nowhere else**
     * (D-227).
     *
     * The server calls [onFrame] and its seat callbacks on its connection threads — one per socket,
     * several at once — and the desk is a set and two plain maps. On 2026-11-16 both filler seats
     * resumed in the same instant after the host's lock, both sent their names, and the desk kept
     * one: the lobby read `[PHONE, SIMA, UNNAMED]` and the round's roster went out with two names
     * for three seats. Two writes into one map from two threads is a lost write, or a name filed
     * under the wrong seat.
     *
     * So the change is handed to [scope], which is the thread the host's own controls already
     * reach the desk on (the setting, NEW ROUND, arming), and every one of them is made in the order
     * the server handed it over: one connection's seating, name and line arrive in the order that
     * phone sent them, and a seat's loss is reported before its resume (`TransportHost` reports a
     * loss under its lock). The standing is built here too, so it is never one thread's view sent
     * after another's newer one.
     *
     * **A change the desk refuses is the authority's to hear, never the player's** (rule 6): a name
     * or line from a seat that is not on the desk is dropped and logged — the seat, never the text.
     */
    private fun atTheDesk(change: () -> Unit) {
        scope.launch {
            deskWatch()
            try {
                change()
            } catch (refused: IllegalArgumentException) {
                onEvent("THE DESK REFUSED A CHANGE — ${refused.message}")
            }
        }
    }

    /** Called before every change [atTheDesk] makes, where it makes it. A test's view of the confinement. */
    internal var deskWatch: () -> Unit = {}

    /**
     * **The real write of one frame to one seat's socket**, passed straight through (R12, D-244). A
     * test holds one seat's write back to stand for a phone whose socket has stopped draining. Read
     * by the server at each write, so a test sets it before or after [start].
     */
    internal var socketWrite: suspend (Seat, suspend () -> Unit) -> Unit = { _, write -> write() }

    /**
     * **End [seat]'s connection as a lost one** (`TransportHost.drop`, D-245): a test's way of
     * standing a socket that failed while its phone was suspended. The phone comes back by its token.
     */
    internal suspend fun dropConnection(seat: Seat) {
        host?.drop(seat)
    }

    /**
     * One frame, to every phone attached to this house.
     *
     * Shared by the standing and by the round's ending so there is one loop over the seats rather
     * than two that could come to differ about what *every phone* means. On a phone that is not
     * hosting there is no server and this does nothing, which is every caller's precondition
     * already.
     */
    private fun announce(body: TransportFrame) {
        val server = host ?: return
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
 * **What the lobby is holding when the lights go out**, read once so that every field is read at
 * the same instant. See [LobbyHouse.armingInputs].
 *
 * It carries [readyToArm] rather than leaving it to the caller to recompute, because the gate and
 * the inputs have to be the same reading of the same desk: a caller that asked *may I arm* and
 * then *what with* has asked two questions of a room somebody may have walked into in between.
 */
data class ArmingInputs(
    val seats: List<Seat>,
    val names: List<SeatNamed>,
    /** **Never logged, never printed, never counted per player.** See [LobbyHouse.armingInputs]. */
    val oneLines: List<OneLineHeld>,
    val chosenInsiders: Int?,
    val voteWindowSeconds: Int,
    val readyToArm: Boolean,
    /**
     * **The home this house is hosting, which IS the round's home** (E-PAY-3, the owner's ruling).
     *
     * There was no field here at all until N3, and that absence is what made a round unplayable in
     * anybody's actual house: the arming draw reached for the bench's fixture because nothing on
     * this type could have handed it the host's own. The host picked a home at HOST WITH THIS
     * HOME, the house went on the air under its name, and the map and plan behind that name are
     * this. **There is no second choice to make** — a house is hosting one home, and the round it
     * arms is played in it.
     *
     * **Null on a phone that is not hosting**, which is four phones in five and every phone before
     * a home goes on the air. [RoundAuthority.arm] refuses on null rather than substituting
     * anything: a round armed in a home nobody chose is the defect this field closes, and a
     * fallback here would be that defect with a shorter name.
     *
     * **Nothing on it crosses to a client.** The map and the plan stay host-side — Static deleted
     * `Effect.HomePlanOffered` and the ruling stands — and what reaches a phone is the
     * [home.someoneshome.model.Destination] rows of its own order's lines, which is W1/E1's
     * arrangement and carries a card, a glyph and a room name.
     */
    val home: SavedHome?,
)

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
    /**
     * **What this phone does with a request that arrived from another phone**, when this phone is
     * the host. See [LobbyHouse.onIntent] — it is empty in every production construction, because
     * the round it would change does not exist yet.
     */
    private val onIntent: suspend (Intent) -> Unit = {},
    /**
     * **What this phone does when another phone holds its Egress tile down**, when this phone is
     * the host. [LobbyHouse.onEgressFired], threaded through — see there for why it is a seam of
     * its own rather than another [Intent].
     */
    private val onEgressFired: suspend (Seat) -> Unit = {},
    /** [LobbyHouse.onSighted], threaded through: what another phone's radio heard (D-217). */
    private val onSighted: suspend (Seat, RoundBody.Sighted) -> Unit = { _, _ -> },
    /** [LobbyHouse.onRadioFaulted], threaded through: another phone's radio went down (T2, D-222; D-223). */
    private val onRadioFaulted: suspend (Seat, RoundBody.RadioFaulted) -> Unit = { _, _ -> },
    /**
     * **The round's client half, on this phone's one socket.** Null in every production
     * construction — nothing arms a round yet. See [RoundSeam].
     */
    private val round: RoundSeam? = null,
    /**
     * **What this phone does when the HOST says the round is over** (D-157, E-R2-10 / E-P3-5).
     *
     * It is a seam for [onIntent]'s reason exactly, in the other direction: what a phone does when
     * the evening turns over is a **screen** answer — walk back to the lobby, drop the line this
     * phone typed — and neither this class nor `platform` may reach a screen. So the transport
     * reports the arrival and the app root, which holds both halves, decides. Empty in a
     * construction with no panel behind it, which is every test that only wants the wire.
     *
     * **The round's own inbox is reset here rather than in the callback**, because that half is not
     * a screen answer and is not optional: a phone that kept last round's sequence would refuse the
     * next round's first push as already held.
     */
    private val onRoundEnded: () -> Unit = {},
    /**
     * **What this phone does when the HOST closes the house** (E-PAY-6).
     *
     * [onRoundEnded]'s sibling and wired identically, for the identical reason: what a phone does
     * about it is a **screen** answer — walk back to the join screen, drop the line, say why — and
     * neither this class nor `platform` may reach a screen. What differs is everything downstream.
     * `RoundEnded` keeps the home, the seats, the settings and the socket; this one has no house
     * left on the other end of the socket, which is why the hang-up happens here rather than being
     * left to the panel.
     */
    private val onHouseClosed: () -> Unit = {},
    /**
     * **The house has been out of reach for [houseGoneAfterMillis]** (H4 of the 2026-11-17 hunt,
     * D-253). [onHouseClosed]'s sibling for the house that could not say goodbye: the host's phone
     * failed, crashed or walked out of range, so no `HouseClosed` came down and this phone's socket
     * simply stopped. Its reconnect keeps trying (the token stays this seat's, G1); what this adds
     * is the screen answer, which is the app root's: [home.someoneshome.ui.ScreenId.Disconnect].
     *
     * **A guest only.** The host's own link drops when its phone locks (D-225), and the house that
     * would be missing is in the host's hand.
     */
    private val onHouseLost: () -> Unit = {},
    /** **And it is back**: this phone was seated again after [onHouseLost] fired. */
    private val onHouseBack: () -> Unit = {},
    /**
     * **How long a guest's link to the house is down before the house is called gone** (D-253,
     * provisional, owner to confirm). Long enough that a host whose phone locked for a moment and
     * came back (D-225) never puts five phones on the disconnect screen; see [HOUSE_GONE_AFTER_MILLIS].
     */
    private val houseGoneAfterMillis: Long = HOUSE_GONE_AFTER_MILLIS,
    /**
     * **The screen, kept awake while this phone hosts a lobby** (H1, D-224), and the same object
     * the round half holds at arming (D-221) — one idle timer, so one [ScreenWake]. Required for
     * the round's reason: a root that has not decided what the idle timer is does not compile. A
     * construction with no idle timer passes one over [home.someoneshome.platform.NoIdleTimer].
     */
    private val wake: ScreenWake,
    /** What puts a hosted home on the air. [DeviceAdvertising] in every production construction. */
    private val advertising: Advertising = DeviceAdvertising,
) : LobbyLink {

    /**
     * **Internal rather than private**, so that the thing which arms a round can reach the house
     * that would carry it.
     *
     * It is the same widening [LobbyCheat] already relies on everywhere else: a bench in this
     * module may reach what a screen may not, and the release variant does not compile the bench
     * at all. `emit` and `armingInputs` are what it is reached for, and both are the authority's
     * own calls rather than a screen's.
     */
    internal val house = LobbyHouse(scope, onEvent, onIntent, onEgressFired, onSighted, onRadioFaulted, advertising)
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

    /**
     * **The line this phone handed over, kept so it can be handed over again** (H2, D-225).
     *
     * [residentName]'s twin and for its reason. The desk drops a seat's line with its connection
     * (`LobbyDesk.left`), because a lost connection is also what a person leaving looks like and
     * the house cannot tell the two apart. So a phone that dropped and came back to its own seat —
     * every phone in the room, when the host's phone locks — arrives at a desk holding nothing of
     * it, while its own screen still says HANDED OVER. The name has always ridden back up on every
     * seating; the line now rides with it.
     *
     * **In memory and nowhere else, and only until the promise says it goes**: the round ends,
     * this phone leaves, or the house closes. The screen's own copy (`OneLine`) lives exactly as
     * long. It is never logged, and it goes up by the one body that has always carried it.
     */
    private var handedLine: String? = null

    init {
        // The 0.8 property on relaunch: a stopped app reads its token back and resumes as THAT
        // seat, never as *a* seat. The restored session never says Hello.
        seat.stored()?.let { session = ClientSession(SeatToken(it)) }
    }

    override fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) =
        house.start(
            home,
            // **The lobby is open, so the host's screen stays on** (H1, D-224). Held here rather
            // than at the button because a house nobody can find is not a lobby: the hold begins
            // when the home is on the air and the host is walked in.
            onUp = { nearby ->
                wake.holdWhileHosting()
                onUp(nearby)
            },
        ) { why ->
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
                // **The round sees the frame, not the body**, and sees it whatever the lobby makes
                // of it: one socket carries both, and a `Carry` the lobby cannot read is exactly
                // the case where it is the round's. Handed over before the lobby's own decode so
                // that a change to what the lobby recognises can never quietly starve the round.
                round?.arrived(frame)
                val body = carried?.let(LobbyWire::decodeOrNull)
                when (body) {
                    is LobbyBody.Standing -> onStanding(body)
                    // **The evening turned over** (D-157). The round's inbox goes first and
                    // unconditionally — it holds a sequence for a round that no longer exists, and
                    // a phone carrying it into the next one would refuse that round's opening
                    // pushes as frames it already had. Then the panel is told, which is the half
                    // this class cannot do itself.
                    //
                    // **Answering it ends nothing** (H1 of the 2026-11-17 hunt). The panel's answer
                    // is `FlowModel.newRound`, the same call the host's NEW ROUND makes, and that
                    // call comes back into [roundEnded] below. Taken there as a new end, the host's
                    // own phone, which hears its broadcast over loopback like everybody, broadcast
                    // it again, heard it again, and so on: hundreds of RoundEnded a second, every
                    // pass wiping the desk's names, until the host crashed. So [roundEnded] is told
                    // that this is the house's push being answered, and keeps only its local half.
                    //
                    // **The name goes back up from here, once**: the desk dropped every name with
                    // the lines (D-115), and this is the one moment every phone, the host's
                    // included, knows the desk has done it.
                    is LobbyBody.RoundEnded -> {
                        handedLine = null
                        round?.roundEnded()
                        answeringTheHouse = true
                        try {
                            onRoundEnded()
                        } finally {
                            answeringTheHouse = false
                        }
                        client?.let { live -> scope.launch { live.send(naming(residentName)) } }
                    }
                    // **The host closed the house** (E-PAY-6). The round's inbox goes first and
                    // unconditionally for `RoundEnded`'s reason, and then this phone hangs up: the
                    // socket on the other end is about to close anyway, and a client still trying to
                    // reconnect to a house that has come off the air would spend the rest of the
                    // evening dialling a number nobody is at. The screen answer is the app root's —
                    // this class cannot reach one.
                    is LobbyBody.HouseClosed -> {
                        round?.roundEnded()
                        hangUp()
                        onHouseClosed()
                    }
                    // Either upward body arriving at a client would be the house sending one
                    // phone's text to another, which nothing on the host side does — and if it ever
                    // did, ignoring it here is the fail-closed direction.
                    is LobbyBody.Handover, is LobbyBody.Naming, null -> Unit
                }
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
                        //
                        // **And the line, for the same reason** (H2, D-225): the desk dropped it
                        // with the connection, and the screen still says HANDED OVER. Sent after
                        // the name, on the same socket, by the same one body that always carries it.
                        client?.let { live ->
                            scope.launch {
                                live.send(naming(name))
                                handedLine?.let { live.send(handover(it)) }
                            }
                        }
                        // **On every seating, exactly as the name is re-sent on every seating**,
                        // and for the mirror of that reason: the name is what the house has to be
                        // told again, and the pushes are what this phone has to ask for again. A
                        // phone that dropped mid-round has missed an unknown amount (D-097, G3).
                        //
                        // **The house is back** (D-253), said before the round asks for what it
                        // missed, so the screen this phone was on is restored first and whatever
                        // the house re-pushes lands on top of it.
                        houseReached()
                        round?.seated()
                    }
                    is ClientSession.Phase.Dismissed -> {
                        onEvent("REFUSED BY THE HOUSE — ${phase.reason}")
                        if (mayStartOver && phase.reason == TransportRefusal.UnknownToken) {
                            startOver(home, name, onStanding)
                        }
                    }
                    // **The host's own link is down, so its standing is not the house's any more**
                    // (H2, D-226). The host's LIGHTS OUT is drawn from the standing that last came
                    // down its own socket, and a host whose phone locked held a READY standing from
                    // before the lock while the house beside it had nobody seated: the button was
                    // live, the house refused, and the screen walked to ARMED anyway. A phone that
                    // is not in its lobby has no standing to hold a stale copy of — LEAVE wipes the
                    // counts for exactly that reason — so the host is shown an empty room until the
                    // house says otherwise, which draws NEEDS N RESIDENTS under a inert button.
                    //
                    // The host only. A guest draws no button from it, and its list blinking empty
                    // on every reconnect would be a change to what a guest is shown for no gain.
                    //
                    // **On a guest, the clock on the house starts** (D-253): see [houseUnreached].
                    is ClientSession.Phase.Rejoining ->
                        if (house.up) onStanding(LobbyBody.Standing(joined = 0, linesIn = 0)) else houseUnreached()
                    is ClientSession.Phase.Joining -> Unit
                }
            },
        )
        client = c
        // The way up, handed over on every connect: the previous one closes over a socket that is
        // gone, and a round still holding it would send every control press into a defunct session.
        round?.linked { frame -> c.send(frame) }
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
        handedLine = line
        scope.launch { c.send(handover(line)) }
    }

    private fun handover(line: String): TransportFrame =
        TransportFrame.Carry(LobbyWire.encode(LobbyBody.Handover(line)))

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
     * **The name goes back up when the house's `RoundEnded` comes down**, not from here (D-115:
     * the desk drops names with the lines). Every phone hears that push, the host's over loopback,
     * so it is the one moment each of them knows the desk has been wiped; a name sent from here
     * as well raced the wipe on a guest and went up twice on the host.
     *
     * **Called while this phone is answering that push, it ends nothing** (H1 of the 2026-11-17
     * hunt, D-252). The panel answers `RoundEnded` with `FlowModel.newRound`, which calls this; a
     * host that took that call as a new end broadcast the round's end again, heard it again over
     * loopback, and looped until it crashed. The house ends a round once, when a thumb presses.
     */
    override fun roundEnded() {
        handedLine = null
        if (answeringTheHouse) return
        house.roundEnded()
        // **NEW ROUND opens the host's lobby again, so its screen is held again** (H1, D-224). The
        // round's end let it go (`ScreenWake.release` ends hosting with the round); the host is now
        // back in a lobby the house runs on this phone.
        if (house.up) wake.holdWhileHosting()
    }

    /**
     * **True only while this phone is answering the house's own `RoundEnded`** (D-252). Set and
     * cleared around the panel's answer in the frame handler, on the same call stack as the
     * [roundEnded] it guards, so it is never read from anywhere else.
     */
    private var answeringTheHouse = false

    /**
     * **This phone stands up and walks out.**
     *
     * The socket goes down and the *host's* desk does the arithmetic: `TransportHost.onLost` calls
     * `LobbyDesk.left`, which drops this seat, this seat's line and this seat's name and publishes a
     * new standing to everybody still in the room. **Nothing here tells the house anything** — a
     * connection closing is the message, and it is the one message that cannot be lost.
     *
     * On the host's own phone it takes the house down as well, because the house runs here: a host
     * who has left is not still running a party. Which makes this and [terminate] the same act from
     * a host's phone, and they are still two methods — see [terminate] for what the *other five*
     * phones get, which is the entire difference and is not visible from this one.
     */
    override fun leave() {
        hangUp()
        house.roundEnded()
        house.stop()
        // Hosting ended (H1, D-224). A guest never held for hosting, so this touches nothing there.
        wake.releaseHosting()
    }

    /**
     * **CLOSE THE HOUSE — and every other phone is told before the sockets go** (E-PAY-6).
     *
     * [leave] and this do the same three things to *this* phone. What this adds is the broadcast,
     * and the broadcast is the requirement: *no one is ever trapped in a lobby again* is a promise
     * about the five phones that did not press anything, and a host tearing the server down in
     * silence leaves them exactly where they were — on a lobby screen with counts that have simply
     * stopped moving, which is indistinguishable from a network that got slow.
     *
     * **On a phone that is not hosting [LobbyHouse] was never started**, so this reduces to a
     * hang-up. That is the same shape [setInsiders] and [setVoteWindow] already have, and the lobby
     * screen does not draw the control there either.
     */
    override fun terminate() {
        // The announcement is the house's and goes out while its sockets are still up; the order
        // matters and is argued at [LobbyHouse.houseClosed]. Then this phone's own client, which
        // joined over loopback like everybody else.
        house.houseClosed()
        hangUp()
        wake.releaseHosting()
    }

    /**
     * **This phone left the foreground** (H2, D-225; D-229). On every phone, the round half marks the
     * onset of D-187's evasion if a round is armed, the host's own seat exactly as a guest's. On the
     * host, the house then goes off the air and its server stops, keeping the ledger every seat comes
     * back through. On a guest the transport does nothing more: its own reconnect already does
     * everything a guest can (D-087, G3).
     */
    fun backgrounded() {
        round?.leftTheForeground()
        house.backgrounded()
    }

    /**
     * **This phone is back** (H2, D-225; D-229). On the host, the same port, the same ledger, on the
     * air again. On every phone, the round half owes the house one report if it left in a round; it
     * goes up its own socket on the next beat, and a socket still coming back holds it until it is
     * seated (the host's own over loopback, like everybody's).
     */
    fun foregrounded() {
        house.foregrounded()
        round?.backInTheForeground()
    }

    /**
     * **A guest's link to the house is down, or a reconnect to it was just refused** (H4 of the
     * 2026-11-17 hunt, D-253).
     *
     * The first call marks when the link went. **The house is called gone at a refused reconnect
     * made [houseGoneAfterMillis] or more after that**, never by a timer alone: the reconnect backs
     * off to eight seconds between tries, so a timer would fire on a host that came back a moment
     * after a try and is simply waiting for the next one. Only a seating clears the mark. Nothing
     * about the reconnect changes: it keeps presenting this seat's token on its bounded backoff, so
     * a host that relaunches or walks back into range still gets this seat back in its own chair.
     *
     * **Never on the host's phone.** That link drops whenever the host's own phone locks (D-225),
     * and the house it would be missing is the one in the host's hand.
     */
    private fun houseUnreached() {
        if (house.up) return
        val now = monotonicNanos() / 1_000_000
        val since = unreachedSince ?: now.also { unreachedSince = it }
        if (houseLostShown || now - since < houseGoneAfterMillis) return
        houseLostShown = true
        scope.launch {
            onEvent("THE HOUSE HAS BEEN OUT OF REACH FOR ${(now - since) / 1_000}S — STILL TRYING")
            onHouseLost()
        }
    }

    /** **Seated again**: the mark goes, and a phone that was told the house was gone is told it is back. */
    private fun houseReached() {
        unreachedSince = null
        if (!houseLostShown) return
        houseLostShown = false
        scope.launch {
            onEvent("THE HOUSE IS BACK — THIS PHONE IS IN ITS OWN CHAIR AGAIN")
            onHouseBack()
        }
    }

    /** When the link went, in monotonic milliseconds, or null while this phone is seated or has hung up. */
    private var unreachedSince: Long? = null

    /** True between [onHouseLost] and the seating that answers it with [onHouseBack]. */
    private var houseLostShown = false

    /** This phone's own end of the socket, dropped. Shared by the two exits so they cannot drift. */
    private fun hangUp() {
        // A phone that walked out is not waiting for a house: no mark, and nothing owed back.
        unreachedSince = null
        houseLostShown = false
        // The round's half first: a phone that has hung up is in no round, so its radio stops and
        // its screen may sleep (T4, D-221). Harmless when it already holds nothing.
        round?.left()
        handedLine = null
        clientJob?.cancel()
        clientJob = null
        client = null
    }
}

/**
 * **How long a guest waits on a house it cannot reach before its screen says so** (D-253,
 * provisional, owner to confirm).
 *
 * Read against the reconnect's own schedule: [home.someoneshome.platform.transport.ReconnectBackoff]
 * waits 0.5, 1, 2, 4 and 8 seconds, so the tries fall at about 0.5, 1.5, 3.5, 7.5 and 15.5 seconds
 * after the link goes, and the screen changes when the fifth is refused. A host whose phone locked
 * and came back before that try (D-225) is answered by it and walks nobody; a host whose app failed
 * is not coming back by then without a relaunch, which is what the screen tells the room.
 */
const val HOUSE_GONE_AFTER_MILLIS: Long = 15_000
