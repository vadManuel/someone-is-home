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
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportClient
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.Amber
import home.someoneshome.ui.Label
import home.someoneshome.ui.LocalPanelInsets
import home.someoneshome.ui.PanelButton
import home.someoneshome.ui.u
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The cheat's fixed port. Not part of the wire contract — mDNS will advertise the real one. */
const val CHEAT_TRANSPORT_PORT: Int = 47747

/**
 * The two-phone evening's driver — a cheat surface over the 0.8 machinery, nothing more.
 *
 * One phone taps HOST; the other types the host's IP (Settings → Wi-Fi on the host phone shows
 * it — mDNS replaces the typing later) and taps JOIN. PING sends an opaque Carry each way; DROP
 * kills the socket the way a dying phone would; JOIN again resumes **as that seat**, which is
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

    private var ledger: SeatLedger? = null
    private var host: TransportHost? = null
    private var session: ClientSession? = null
    private var client: TransportClient? = null
    private var clientJob: Job? = null
    private var pings = 0

    init {
        // The 0.8 property on relaunch: a killed app reads its token back and resumes as THAT
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

    private fun note(line: String) {
        log.add(0, line)
        while (log.size > 12) log.removeAt(log.size - 1)
    }

    fun startHost() {
        if (host != null) return
        val seats = SeatLedger(
            List(8) { Seat(it) },
            // Real randomness belongs to the app root (the ledger's own KDoc); a cheat's tokens
            // never outlive the process, so Random is exactly right here and nowhere else.
            mint = { SeatToken(Random.nextLong().toString(16) + Random.nextLong().toString(16)) },
        )
        ledger = seats
        val h = TransportHost(
            seats,
            onFrame = { seat, frame -> note("HOST < S${seat.index} ${frame.brief()}") },
            onSeated = { note("HOST SEATED S${it.index}") },
            onLost = { note("HOST LOST S${it.index}") },
        )
        host = h
        scope.launch {
            val port = h.start(CHEAT_TRANSPORT_PORT)
            hostingPort = port
            note("HOSTING ON :$port")
        }
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
            s, address, CHEAT_TRANSPORT_PORT, ::nowMillis,
            onFrame = { note("ME < ${it.brief()}") },
            onPhase = { phase ->
                clientPhase = phase.brief()
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
        note("JOINING $address")
    }

    fun drop() {
        clientJob?.cancel()
        note("SOCKET DROPPED")
    }

    /** Discard the stored token and become a stranger. The one deliberate way to lose a seat. */
    fun forget() {
        clientJob?.cancel()
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
            .padding(top = insets.top + 6.u, bottom = insets.bottom + 18.u)
            .padding(horizontal = 14.u)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.u),
    ) {
        Label("${BuildVariant.MARKER} — TRANSPORT", size = 8.0, color = Amber.Bright, tracking = 0.16)

        Label("HOST", size = 6.5, color = Amber.Dim, tracking = 0.2)
        Label(
            cheat.hostingPort?.let { "HOSTING ON :$it${if (cheat.locked) " · LOCKED" else ""}" } ?: "NOT HOSTING",
            size = 7.0, color = Amber.Mid,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.u)) {
            Box(Modifier.weight(1f)) { PanelButton("HOST", ink = Amber.Bright, onClick = cheat::startHost) }
            Box(Modifier.weight(1f)) { PanelButton("LOCK", ink = Amber.Bright, onClick = cheat::lockRound) }
        }

        Label("CLIENT", size = 6.5, color = Amber.Dim, tracking = 0.2)
        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.Faint).padding(horizontal = 6.u, vertical = 4.u),
            horizontalArrangement = Arrangement.spacedBy(6.u),
        ) {
            Label("IP", size = 6.5, color = Amber.Dim)
            BasicTextField(
                value = cheat.address,
                onValueChange = { cheat.address = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Amber.Bright, fontSize = 8.sp),
                cursorBrush = SolidColor(Amber.Bright),
                singleLine = true,
            )
        }
        Label(cheat.clientPhase, size = 7.0, color = Amber.Mid)
        Row(horizontalArrangement = Arrangement.spacedBy(4.u)) {
            Box(Modifier.weight(1f)) { PanelButton("JOIN", ink = Amber.Bright, onClick = cheat::join) }
            Box(Modifier.weight(1f)) { PanelButton("PING", ink = Amber.Bright, onClick = cheat::ping) }
            Box(Modifier.weight(1f)) { PanelButton("DROP", ink = Amber.Bright, onClick = cheat::drop) }
            Box(Modifier.weight(1f)) { PanelButton("FORGET", ink = Amber.Bright, onClick = cheat::forget) }
        }

        Label("LOG", size = 6.5, color = Amber.Dim, tracking = 0.2)
        for (line in cheat.log) {
            Label(line, size = 6.5, color = Amber.Dim)
        }
    }
}
