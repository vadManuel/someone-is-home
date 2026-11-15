package home.someoneshome.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.clearSeatToken
import home.someoneshome.platform.loadHostAddress
import home.someoneshome.platform.loadSeatToken
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.saveHostAddress
import home.someoneshome.platform.saveSeatToken
import home.someoneshome.platform.transport.ClientSession
import home.someoneshome.platform.transport.ClockSync
import home.someoneshome.platform.transport.HostAdvertiser
import home.someoneshome.platform.transport.HostBrowser
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.Amber
import home.someoneshome.ui.Ramp
import home.someoneshome.ui.Label
import home.someoneshome.ui.LocalPanelInsets
import home.someoneshome.ui.PanelButton
import home.someoneshome.ui.tap
import home.someoneshome.ui.u
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The cheat's fixed port. Not part of the wire contract — mDNS will advertise the real one. */
const val CHEAT_TRANSPORT_PORT: Int = 47747

/**
 * The two-phone evening's driver — a cheat surface over the 0.8 machinery, nothing more.
 *
 * One phone taps HOST; the other types the host's IP (Settings → Wi-Fi on the host phone shows
 * it — mDNS replaces the typing later) and taps JOIN. PING sends an opaque Carry each way; DROP
 * stops the socket the way a failing phone would; JOIN again resumes **as that seat**, which is
 * the property the evening exists to watch on real hardware. LOCK arms the ledger so a FORGET +
 * JOIN demonstrates the terminal refusal. The same surface can prove itself on one device by
 * joining 127.0.0.1.
 *
 * Everything here is playtest/debug-only by the same compile-out as every other cheat, and it
 * touches no game state: seat numbers on this screen are transport bookkeeping shown to a
 * developer, not effects shown to a player.
 */
class TransportCheat(private val scope: CoroutineScope) {

    val log = mutableStateListOf<String>()
    var hostingPort by mutableStateOf<Int?>(null)
        private set
    var locked by mutableStateOf(false)
        private set
    var clientPhase by mutableStateOf("NOT JOINED")
        private set
    var address by mutableStateOf("127.0.0.1")
    var port by mutableStateOf(CHEAT_TRANSPORT_PORT)
        private set
    var clock by mutableStateOf("NO FIX")
        private set

    private var ledger: SeatLedger? = null
    private var host: TransportHost? = null
    private var advertiser: HostAdvertiser? = null
    private var browser: HostBrowser? = null
    private var session: ClientSession? = null
    private var client: TransportClient? = null
    private var clientJob: Job? = null
    private var sync: ClockSync? = null
    private var probeJob: Job? = null
    private var pings = 0

    init {
        // The 0.8 property on relaunch: a stopped app reads its token back and resumes as THAT
        // seat. The restored session never says Hello.
        loadSeatToken()?.let {
            session = ClientSession(SeatToken(it))
            clientPhase = "TOKEN HELD — JOIN TO RESUME"
        }
        // And the other half, found on the first two-phone evening: the token says WHO this
        // phone is, the address says WHERE the house is, and a resume needs both. Without this
        // the relaunched client dutifully presented its token to 127.0.0.1.
        loadHostAddress()?.let { address = it }
    }

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    /**
     * One line in the rig's log, newest first.
     *
     * Public because the lobby wiring writes here too: mDNS failing is silent, and silence was
     * the entire symptom on the first device run. A playtest phone that cannot find a house needs
     * somewhere the reason lands.
     */
    fun note(line: String) {
        log.add(0, line)
        while (log.size > 12) log.removeAt(log.size - 1)
    }

    fun startHost() {
        if (host != null) return
        val seats = SeatLedger(
            List(8) { Seat(it) },
            // Real randomness belongs to the app root (the ledger's own KDoc). The secure mint even
            // here, where tokens never outlive the process: `seedSourceCordon` admits no
            // `kotlin.random` anywhere in this module, so there is no second source to reach for.
            mint = ::mintSeatToken,
        )
        ledger = seats
        val h = TransportHost(
            seats,
            nowMillis = ::nowMillis,
            onFrame = { seat, frame -> note("HOST < S${seat.index} ${frame.brief()}") },
            onSeated = { note("HOST SEATED S${it.index}") },
            onLost = { note("HOST LOST S${it.index}") },
        )
        host = h
        scope.launch {
            val bound = h.start(CHEAT_TRANSPORT_PORT)
            hostingPort = bound
            // D-094 closed properly: the host says where it is, nobody types an IP. The
            // instance name carries nothing about any round — it is on the air for the whole
            // LAN before any permission this app controls.
            advertiser = HostAdvertiser("Someone's Home", bound, onEvent = ::note).also { it.start() }
            note("HOSTING ON :$bound")
        }
    }

    /** Browse for an advertised host; the first one found fills the address and port. */
    fun find() {
        if (browser != null) return
        note("LOOKING FOR A HOST…")
        browser = HostBrowser(
            onFound = { name, foundAddress, foundPort ->
                address = foundAddress
                port = foundPort
                note("FOUND '$name' $foundAddress:$foundPort")
                browser?.stop()
                browser = null
            },
            onEvent = ::note,
        ).also { it.start() }
    }

    fun lockRound() {
        val seats = ledger ?: return
        seats.lock()
        locked = true
        note("LEDGER LOCKED")
    }

    fun join() {
        if (clientJob?.isActive == true) return
        val s = session ?: ClientSession().also { session = it }
        val c = TransportClient(
            s, address, port, ::nowMillis,
            onFrame = { frame ->
                // Clock marks feed the estimator and the display, not the log — five arrive
                // every thirty seconds and would scroll everything else away.
                if (frame is TransportFrame.TimeMark) {
                    val now = nowMillis()
                    sync?.let { est ->
                        est.onMark(frame.probe, frame.hostMillis, now)
                        val offset = est.appliedOffsetMillis(now)
                        val rtt = est.lastRttMillis
                        if (offset != null && rtt != null) clock = "OFFSET ${offset}MS · RTT ${rtt}MS"
                    }
                } else {
                    note("ME < ${frame.brief()}")
                }
            },
            onPhase = { phase ->
                clientPhase = phase.brief()
                // The log is the record; the phase line is only the present tense. A seating
                // that never entered the log was invisible the moment anything else happened —
                // Vadmanuel noticed on the first successful discovery run.
                if (phase is ClientSession.Phase.Seated || phase is ClientSession.Phase.Dismissed) {
                    note("ME: ${phase.brief()}")
                }
                // The token is durable from the moment of seating, or the log says it is not.
                // This call was missing once: an edit silently matched nothing, the build stayed
                // green (an unused import compiles), and the 13 Pro relaunched as a stranger —
                // D-084's failure shape, caught only because a phone was watched.
                if (phase is ClientSession.Phase.Seated) {
                    try {
                        saveSeatToken(phase.token.value)
                        saveHostAddress(address)
                    } catch (notSaved: IllegalStateException) {
                        note("TOKEN NOT SAVED — NO RESUME AFTER A CRASH")
                    }
                }
            },
        )
        client = c
        clientJob = scope.launch { c.run() }
        // D5's discipline rides the same socket: a burst of probes at join, another every 30 s,
        // the minimum-RTT sample believed, corrections slewed. The loop just asks the estimator
        // what is due and puts it on the wire.
        val est = sync ?: ClockSync().also { sync = it }
        probeJob?.cancel()
        probeJob = scope.launch {
            while (true) {
                if (session?.phase is ClientSession.Phase.Seated) {
                    est.dueProbes(nowMillis()).forEach { c.send(TransportFrame.TimeProbe(it)) }
                }
                kotlinx.coroutines.delay(500)
            }
        }
        note("JOINING $address")
    }

    fun drop() {
        clientJob?.cancel()
        note("SOCKET DROPPED")
    }

    /** Discard the stored token and become a stranger. The one deliberate way to lose a seat. */
    fun forget() {
        clientJob?.cancel()
        probeJob?.cancel()
        sync = null
        clock = "NO FIX"
        session = null
        client = null
        clientPhase = "NOT JOINED"
        clearSeatToken()
        note("TOKEN FORGOTTEN")
    }

    fun ping() {
        val c = client ?: return
        val n = ++pings
        scope.launch {
            c.send(TransportFrame.Carry("ping $n"))
            note("ME > ping $n")
        }
    }
}

private fun TransportFrame.brief(): String = when (this) {
    is TransportFrame.Hello -> "HELLO"
    is TransportFrame.Resume -> "RESUME"
    is TransportFrame.Seated -> "SEATED"
    is TransportFrame.Refused -> "REFUSED ${reason.name.uppercase()}"
    is TransportFrame.Proposed -> "PROPOSED $proposal"
    is TransportFrame.Ack -> "ACK $proposal"
    is TransportFrame.Commit -> "COMMIT $proposal"
    is TransportFrame.TimeProbe -> "PROBE $probe"
    is TransportFrame.TimeMark -> "MARK $probe"
    is TransportFrame.Carry -> "CARRY '$body'"
}

private fun ClientSession.Phase.brief(): String = when (this) {
    is ClientSession.Phase.Joining -> "JOINING"
    is ClientSession.Phase.Seated -> "SEATED"
    is ClientSession.Phase.Rejoining -> "REJOINING (TRY $attempt)"
    is ClientSession.Phase.Dismissed -> "DISMISSED ${reason.name.uppercase()}"
}

@Composable
fun TransportCheatScreen(cheat: TransportCheat) {
    val insets = LocalPanelInsets.current
    Column(
        Modifier.fillMaxSize().background(Amber.Black)
            .padding(top = insets.top + 7.2.u, bottom = insets.bottom + 21.6.u)
            .padding(horizontal = 16.8.u)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.8.u),
    ) {
        Label("${BuildVariant.MARKER} — TRANSPORT", size = 9.6, color = Amber.Bright, tracking = 0.16)

        Label("HOST", size = 7.8, color = Amber.Dim, tracking = 0.2)
        Label(
            cheat.hostingPort?.let { "HOSTING ON :$it${if (cheat.locked) " · LOCKED" else ""}" } ?: "NOT HOSTING",
            size = 8.4, color = Amber.Mid,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.8.u)) {
            Box(Modifier.weight(1f)) { RigButton("HOST", cheat::startHost) }
            Box(Modifier.weight(1f)) { RigButton("LOCK", cheat::lockRound) }
        }

        Label("CLIENT", size = 7.8, color = Amber.Dim, tracking = 0.2)
        Row(
            Modifier.fillMaxWidth().border(1.u, Ramp.Faint).padding(horizontal = 7.2.u, vertical = 4.8.u),
            horizontalArrangement = Arrangement.spacedBy(7.2.u),
        ) {
            Label("IP", size = 7.8, color = Amber.Dim)
            BasicTextField(
                value = cheat.address,
                onValueChange = { cheat.address = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Amber.Bright, fontSize = 9.6.sp),
                cursorBrush = SolidColor(Amber.Bright),
                singleLine = true,
            )
            Label("FIND", Modifier.tap(cheat::find), size = 7.8, color = Amber.Bright)
        }
        Label(cheat.clientPhase, size = 8.4, color = Amber.Mid)
        Label("CLOCK ${cheat.clock}", size = 7.8, color = Amber.Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(4.8.u)) {
            Box(Modifier.weight(1f)) { RigButton("JOIN", cheat::join) }
            Box(Modifier.weight(1f)) { RigButton("PING", cheat::ping) }
            Box(Modifier.weight(1f)) { RigButton("DROP", cheat::drop) }
            Box(Modifier.weight(1f)) { RigButton("FORGET", cheat::forget) }
        }

        Label("LOG", size = 7.8, color = Amber.Dim, tracking = 0.2)
        for (line in cheat.log) {
            Label(line, size = 7.8, color = Amber.Dim)
        }
    }
}

/** The rig's buttons, drawn at their old size on the 360 canvas: the 300 canvas's 8 and 10, × 1.2. */
@Composable
private fun RigButton(text: String, onClick: () -> Unit) =
    PanelButton(text, size = 9.6, verticalPadding = 12.u, ink = Amber.Bright, onClick = onClick)
