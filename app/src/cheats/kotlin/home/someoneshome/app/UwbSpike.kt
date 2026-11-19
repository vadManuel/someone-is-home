@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package home.someoneshome.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import home.someoneshome.platform.launchSwitch
import home.someoneshome.platform.launchValue
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.ui.Amber
import home.someoneshome.ui.Label
import home.someoneshome.ui.LocalPanelInsets
import home.someoneshome.ui.tap
import home.someoneshome.ui.u
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.Vector128
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSKeyedArchiver
import platform.Foundation.NSKeyedUnarchiver
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.NearbyInteraction.NIAlgorithmConvergence
import platform.NearbyInteraction.NIDiscoveryToken
import platform.NearbyInteraction.NINearbyObject
import platform.NearbyInteraction.NINearbyObjectAngleNotAvailable
import platform.NearbyInteraction.NINearbyObjectDistanceNotAvailable
import platform.NearbyInteraction.NINearbyObjectRemovalReason
import platform.NearbyInteraction.NINearbyPeerConfiguration
import platform.NearbyInteraction.NISession
import platform.NearbyInteraction.NISessionDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationProtectedDataDidBecomeAvailable
import platform.UIKit.UIApplicationProtectedDataWillBecomeUnavailable
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIDevice
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.posix.uname
import platform.posix.utsname
import kotlin.math.PI
import kotlin.math.asin

/**
 * **The UWB ranging spike** (2026-11-18): a measurement tool, and nothing else.
 *
 * The owner ruled that Apple's Nearby Interaction (UWB) replaces Bluetooth for contact, report and
 * presence. Before anything is rewritten, two real iPhones answer what UWB actually gives this game:
 * the distance at a touch and at a hand-span, whether direction survives a pocket and the dark, the
 * update rate, what a lock or a trip to the background does to a session, and where NameDrop fires
 * against the UWB reading. `tools/uwb-spike/README.md` is the owner's script; `tools/uwb-spike/run.sh`
 * installs, launches and relays; `tools/uwb-spike/sample.sh` summarises a window of the logs.
 *
 * ### Cordoned the way every cheat is
 *
 * This file is in `src/cheats/`, so a release build does not compile it at all. And on a playtest
 * phone it takes the screen **only** when launched with [SWITCH] set — `CheatRoot` hands the whole
 * phone to [UwbSpikeRoot] before it builds anything else, so no lobby, radio, house or round exists
 * in the process. Launched any other way (the springboard, `round.sh`, `run-app.sh`), the phone is
 * exactly the playtest build it was before this file existed.
 *
 * ### How the two phones learn each other's token
 *
 * Through the Mac both phones are already tethered to for their consoles. Each phone prints its
 * session's archived `NIDiscoveryToken` as a `[uwb] S1 TOKEN` line; `run.sh` reads it off that
 * phone's console and copies it, with `devicectl device copy to`, into the **other** phone's app
 * container as `tmp/uwb-peer-token.txt`, which this file polls. The house's websocket was the other
 * candidate and was refused: carrying a token over it means a new wire message in `model`, which
 * means a redaction-schema entry and a lobby to stand up first, all to move forty bytes once. The
 * file relay touches no game code and needs no network at all.
 *
 * ### Three phones: one session, one token, one file per peer
 *
 * With [UwbSpike.PEERS_SWITCH] set (`run.sh` sets it when `UWB_PHONE_C` names a third phone), the
 * phone is one letter of A, B and C, and holds one live session per other letter. A token belongs to
 * the session that made it, so each live session's lines end in `peer=<letter>`; `run.sh` copies
 * the token printed on phone X's `peer=Y` line into phone Y as `tmp/uwb-peer-token-X.txt`, and
 * phone Y's session for X polls exactly that file. Idle sessions (`--sessions`, `--probe`) print
 * `peer=-`, which the relay never matches. Without the switch nothing here changes: one live
 * session, the one file, and no `peer=` on any line.
 *
 * ### Every line is `[uwb]`, and the stats script reads them
 *
 * The formats are built by the pure functions at the bottom of this file and pinned by
 * `UwbSpikeLinesTest`, because `sample.sh` parses them with grep: a line that changes shape without
 * the test failing is a window the script reports as empty.
 */
internal object UwbSpike {
    /** Present: this launch is the spike. `run.sh` sets it; nothing else does. */
    const val SWITCH = "SOMEONES_HOME_UWB_SPIKE"

    /**
     * How many sessions this phone holds (default 1). Session 1 ranges with the peer; sessions 2..N
     * are run with the same peer token purely to see whether, and with which error, the phone
     * refuses an extra one. A concurrency probe, as far as two phones can probe it.
     */
    const val SESSIONS_SWITCH = "SOMEONES_HOME_UWB_SESSIONS"

    /** This phone's letter in a three-phone run. `run.sh` sets it beside [PEERS_SWITCH]. */
    const val ME_SWITCH = "SOMEONES_HOME_UWB_ME"

    /**
     * The letters of the phones this one ranges with, comma-separated (`B,C` on phone A). Each gets
     * its own live session and its own token file. Absent: the two-phone run, exactly as before.
     */
    const val PEERS_SWITCH = "SOMEONES_HOME_UWB_PEERS"

    /**
     * Present: once the live sessions have a peer, open one more idle session every
     * [PROBE_EVERY_MS] until iOS refuses one or [PROBE_CAP] sessions are held, then print `LIMIT`.
     */
    const val PROBE_SWITCH = "SOMEONES_HOME_UWB_PROBE"

    /**
     * The opt-in session options, one switch each. Kotlin/Native's `platform.NearbyInteraction`
     * (Kotlin 2.4.10, iOS 26.5 SDK) gives `NINearbyPeerConfiguration` exactly two settable options,
     * `cameraAssistanceEnabled` (iOS 16) and `extendedDistanceMeasurementEnabled` (iOS 17), and
     * nothing that names a rate. The third is the session's `delegateQueue`: readings are handed
     * over on a queue of the spike's own instead of the main thread, which is busy drawing.
     */
    const val CAMERA_ASSIST_SWITCH = "SOMEONES_HOME_UWB_CAMERA_ASSIST"
    const val EXTENDED_DISTANCE_SWITCH = "SOMEONES_HOME_UWB_EXTENDED_DISTANCE"
    const val DELEGATE_QUEUE_SWITCH = "SOMEONES_HOME_UWB_DELEGATE_QUEUE"

    /** The file `run.sh` copies the other phone's token into, under the app container's `tmp/`. */
    const val PEER_TOKEN_FILE = "uwb-peer-token.txt"

    /** Restarts after an invalidation, per session, before the spike stops trying and says so. */
    const val RESTARTS = 20

    const val PROBE_EVERY_MS = 2_000L
    const val PROBE_CAP = 20

    val launched: Boolean get() = launchSwitch(SWITCH)

    fun sessions(): Int = launchValue(SESSIONS_SWITCH)?.toIntOrNull()?.coerceIn(1, 16) ?: 1

    fun me(): String? = launchValue(ME_SWITCH)?.trim()?.takeIf { it.length == 1 && it[0] in 'A'..'Z' }

    fun peers(): List<String> = parsePeers(launchValue(PEERS_SWITCH), me())

    val probe: Boolean get() = launchSwitch(PROBE_SWITCH)

    fun options(): UwbOptions = UwbOptions(
        cameraAssistance = launchSwitch(CAMERA_ASSIST_SWITCH),
        extendedDistance = launchSwitch(EXTENDED_DISTANCE_SWITCH),
        delegateQueue = launchSwitch(DELEGATE_QUEUE_SWITCH),
    )
}

/** One `[uwb]` line to the device console, which `run.sh` keeps as `phone.log` / `phoneB.log`. */
private fun say(line: String) = println("[uwb] $line")

private fun nowMs(): Double = monotonicNanos() / 1_000_000.0

// ---------------------------------------------------------------------------------------------
// The screen

/** What the screen shows of one live session, and what its tally counts. */
private class LiveView(val peer: String?) {
    var shown by mutableStateOf(UwbReading.NONE)
    var state by mutableStateOf("STARTING")
    var updates by mutableIntStateOf(0)
    var rate by mutableStateOf(0.0)
}

/** The token the idle sessions run with: the first peer token the relay delivered. */
private class IdleToken {
    var token: NIDiscoveryToken? = null
}

@Composable
internal fun UwbSpikeRoot() {
    val me = remember { UwbSpike.me() }
    val peers = remember { UwbSpike.peers() }
    val options = remember { UwbSpike.options() }
    val probe = remember { UwbSpike.probe }
    val plan = remember { sessionPlan(peers, UwbSpike.sessions()) }
    val model = remember { modelIdentifier() }
    val os = remember { "${UIDevice.currentDevice.systemName}-${UIDevice.currentDevice.systemVersion}" }
    var marks by remember { mutableIntStateOf(0) }
    var probeState by remember { mutableStateOf<String?>(null) }
    // The live sessions come first in the plan, so session n's view is views[n - 1].
    val views = remember { plan.filter { it.live }.map { LiveView(it.tag) } }
    val sessions = remember {
        plan.map { slot ->
            val view = if (slot.live) views[slot.index - 1] else null
            UwbPeerSession(
                index = slot.index,
                tag = slot.tag,
                options = options,
                onReading = { r -> if (view != null) { view.shown = r; view.updates++ } },
                onState = { s -> if (view != null) view.state = s },
            )
        }
    }
    val live = remember { sessions.filterIndexed { i, _ -> plan[i].live } }
    // The probe's sessions, opened one at a time after the launch.
    val probed = remember { mutableListOf<UwbPeerSession>() }
    val idle = remember { IdleToken() }

    DisposableEffect(Unit) {
        // The measurements, before anything runs: what this phone is and what it says it can do.
        say(deviceLine(model, UIDevice.currentDevice.systemName, UIDevice.currentDevice.systemVersion))
        say(capabilityLine(capabilities()))
        if (peers.isEmpty()) {
            say("SESSIONS ${plan.size} · polling ${peerTokenPath(null)} t=${fmt(nowMs(), 3)}")
        } else {
            say(peersLine(me, peers))
            say("SESSIONS ${plan.size} · polling ${peers.joinToString(" ") { peerTokenPath(it) }} t=${fmt(nowMs(), 3)}")
        }
        say(configLine(options, probe))
        // A token left by an earlier launch is a session that no longer exists.
        val files: List<String?> = if (peers.isEmpty()) listOf(null) else peers
        files.forEach { NSFileManager.defaultManager.removeItemAtPath(peerTokenPath(it), null) }
        // A test phone that dims and locks on its own ends the session the step was measuring.
        UIApplication.sharedApplication.idleTimerDisabled = true
        val stopWatching = watchApp()
        // Two phones: session 1 prints its token now and the rest wait for the peer's. Three: every
        // live session prints its token now, because each peer needs its own.
        if (peers.isEmpty()) sessions.first().start() else live.forEach { it.start() }
        onDispose {
            stopWatching()
            (sessions + probed).forEach { it.stop() }
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }

    // The relay's files, polled.
    LaunchedEffect(Unit) {
        if (peers.isEmpty()) {
            // Two phones: a new token is applied to session 1 and then, if the probe asked for
            // more, to each extra session in turn.
            var applied: String? = null
            while (true) {
                val text = readPeerToken(null)
                if (text != null && text != applied) {
                    val token = unarchiveToken(text)
                    say(peerTokenLine(1, token != null, text.length, nowMs(), null))
                    if (token != null) {
                        idle.token = token
                        sessions.forEach { s ->
                            if (!s.started) s.start()
                            s.peerToken(token)
                        }
                        probed.forEach { it.peerToken(token) }
                    }
                    applied = text
                }
                delay(500)
            }
        } else {
            // Three phones: each live session reads its own peer's file. The idle sessions run with
            // whichever peer's token came first, and follow that peer's replacements.
            val applied = HashMap<String, String>()
            var idleFrom: String? = null
            while (true) {
                peers.forEachIndexed { i, peer ->
                    val text = readPeerToken(peer)
                    if (text != null && text != applied[peer]) {
                        val s = sessions[i]
                        val token = unarchiveToken(text)
                        say(peerTokenLine(s.index, token != null, text.length, nowMs(), peer))
                        if (token != null) {
                            s.peerToken(token)
                            if (idleFrom == null) idleFrom = peer
                            if (idleFrom == peer) {
                                idle.token = token
                                (sessions.drop(peers.size) + probed).forEach { o ->
                                    if (!o.started) o.start()
                                    o.peerToken(token)
                                }
                            }
                        }
                        applied[peer] = text
                    }
                }
                delay(500)
            }
        }
    }

    // The rate on screen, and a tally line per live session every ten seconds for a reader with no
    // script.
    LaunchedEffect(Unit) {
        val last = IntArray(views.size) { views[it].updates }
        var lastAt = nowMs()
        val tallyFrom = IntArray(views.size) { views[it].updates }
        var tallyAt = lastAt
        while (true) {
            delay(1_000)
            val now = nowMs()
            views.forEachIndexed { i, v ->
                v.rate = (v.updates - last[i]) * 1000.0 / (now - lastAt)
                last[i] = v.updates
            }
            lastAt = now
            if (now - tallyAt >= 10_000) {
                views.forEachIndexed { i, v ->
                    val n = v.updates - tallyFrom[i]
                    say(tallyLine(i + 1, now, n, n * 1000.0 / (now - tallyAt), v.state, v.peer))
                    tallyFrom[i] = v.updates
                }
                tallyAt = now
            }
        }
    }

    // The limit probe: one more idle session every two seconds until iOS refuses one.
    if (probe) {
        LaunchedEffect(Unit) {
            while (idle.token == null) delay(500)
            // The live sessions settle with their peers first, so the probe starts from a phone
            // that is already ranging.
            delay(UwbSpike.PROBE_EVERY_MS)
            val idleTag = if (peers.isEmpty()) null else IDLE_TAG
            fun held() = (sessions + probed).count { it.alive }
            fun liveNow() = live.count { it.alive }
            say("PROBE START live=${liveNow()} idle=${held() - liveNow()} t=${fmt(nowMs(), 3)}")
            val stop = probeUntilRefused(
                held = ::held,
                refused = { (sessions + probed).firstNotNullOfOrNull { it.refusedFor } },
                open = {
                    val s = UwbPeerSession(plan.size + probed.size + 1, idleTag, options, {}, {})
                    probed += s
                    probeState = "PROBE - ${held() + 1} SESSIONS"
                    s.start()
                    idle.token?.let(s::peerToken)
                },
            )
            say(limitLine(liveNow(), held() - liveNow(), model, os, stop, nowMs()))
            probeState = "LIMIT - ${held()} SESSIONS - $stop"
        }
    }

    val insets = LocalPanelInsets.current
    Box(Modifier.fillMaxSize().background(Amber.Black)) {
        Column(
            Modifier.fillMaxWidth().padding(top = insets.top + 8.u, start = insets.side + 8.u, end = insets.side + 8.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            Label("UWB SPIKE - $model" + (me?.let { " - $it" } ?: ""), size = 7.0, color = Amber.Dim)
            if (peers.isEmpty()) {
                val v = views.first()
                Label(v.state, size = 7.0, color = Amber.Dim)
                // ASCII only: the pixel face is not known to carry a dash or a degree sign.
                Label(v.shown.distance?.let { "${fmt(it.toDouble(), 2)} M" } ?: "NO READING", size = 28.0, color = Amber.Mid)
                Label(
                    v.shown.azimuthDegrees?.let { "DIR ${fmt(it.toDouble(), 0)} DEG" } ?: "DIR NONE",
                    size = 10.0,
                    color = Amber.Mid,
                )
                Label("${fmt(v.rate, 1)} / S - ${v.updates} UPDATES", size = 7.0, color = Amber.Dim)
            } else {
                // One block per peer: its letter and distance, then its rate and state.
                views.forEach { v ->
                    Label(
                        "${v.peer} " + (v.shown.distance?.let { "${fmt(it.toDouble(), 2)} M" } ?: "NO READING"),
                        size = 16.0,
                        color = Amber.Mid,
                    )
                    Label("${fmt(v.rate, 1)} / S - ${v.updates} UPDATES - ${v.state}", size = 7.0, color = Amber.Dim)
                }
            }
            probeState?.let { Label(it, size = 7.0, color = Amber.Dim) }
            Label("MARKS $marks", size = 7.0, color = Amber.Dim)
        }
        // One control: MARK, which stamps a numbered line into the log at the moment it is pressed.
        Box(
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = insets.bottom + 16.u)
                .border(1.u, Amber.Dim)
                .tap {
                    marks++
                    say(markLine(marks, nowMs(), views.map { it.peer to it.shown.distance }))
                }
                .padding(horizontal = 24.u, vertical = 10.u),
        ) {
            Label("MARK", size = 10.0, color = Amber.Mid)
        }
    }
}

/**
 * Opens one session at a time, [UwbSpike.PROBE_EVERY_MS] apart, and returns why it stopped: the name
 * of the error iOS refused a session with, or `cap-20` when it never refused (the limit is then at
 * least that many).
 */
private suspend fun probeUntilRefused(held: () -> Int, refused: () -> String?, open: () -> Unit): String {
    while (true) {
        refused()?.let { return it }
        if (held() >= UwbSpike.PROBE_CAP) return "cap-${UwbSpike.PROBE_CAP}"
        open()
        delay(UwbSpike.PROBE_EVERY_MS)
    }
}

/** What happens to the app, beside what happens to the session — so a suspension has a cause. */
private fun watchApp(): () -> Unit {
    val centre = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue
    val names = listOf(
        UIApplicationWillResignActiveNotification to "RESIGN-ACTIVE",
        UIApplicationDidEnterBackgroundNotification to "BACKGROUND",
        UIApplicationWillEnterForegroundNotification to "FOREGROUND",
        UIApplicationDidBecomeActiveNotification to "ACTIVE",
        // Posted only on a phone with a passcode: the lock itself, as near as iOS reports it.
        UIApplicationProtectedDataWillBecomeUnavailable to "LOCKING",
        UIApplicationProtectedDataDidBecomeAvailable to "UNLOCKED",
    )
    val observers = names.map { (name, word) ->
        centre.addObserverForName(name, null, queue) { _ -> say("APP $word t=${fmt(nowMs(), 3)}") }
    }
    return { observers.forEach { centre.removeObserver(it) } }
}

// ---------------------------------------------------------------------------------------------
// One NISession, held for one peer

/** One reading, already unpacked from the sentinels Nearby Interaction uses for "not available". */
internal data class UwbReading(
    val distance: Float?,
    val direction: Triple<Float, Float, Float>?,
    val azimuthDegrees: Float?,
    val horizontalDegrees: Float?,
    val vertical: String,
) {
    companion object {
        val NONE = UwbReading(null, null, null, null, "UNKNOWN")
    }
}

/**
 * **One session per peer.** A `NINearbyPeerConfiguration` names exactly one peer token, so a phone
 * in a round of N holds N-1 of these, each with its own discovery token to hand out. This holds one
 * and keeps it running: a suspension that ends is run again with the same configuration, a peer
 * that times out is run again, and an invalidated session is replaced (a new token, which the relay
 * carries across) up to [UwbSpike.RESTARTS] times.
 *
 * [tag] is what every line of this session ends in, as ` peer=<tag>`: the peer's letter, or `-` for
 * an idle session, in a three-phone run. In the two-phone run it is null and the lines are exactly
 * what they always were.
 */
private class UwbPeerSession(
    val index: Int,
    private val tag: String?,
    private val options: UwbOptions,
    private val onReading: (UwbReading) -> Unit,
    private val onState: (String) -> Unit,
) {
    private var session: NISession? = null
    private var peer: NIDiscoveryToken? = null
    private var generation = 0
    private var restarts = 0
    private val listener = Listener(this, options.delegateQueue)
    private val suffix = peerSuffix(tag)

    /** The spike's own queue for this session's callbacks, when [UwbOptions.delegateQueue] asks. */
    // `dispatch_queue_t` is already nullable in Kotlin/Native (`NSObject?`).
    private val queue: dispatch_queue_t =
        if (options.delegateQueue) dispatch_queue_create("home.someoneshome.uwb.S$index", null) else null

    var started = false
        private set

    /** Holding a session now: false once one has ended for good. */
    val alive: Boolean get() = session != null

    /** The name of the limit error iOS ended this session with, if it did: the probe's answer. */
    var refusedFor: String? = null
        private set

    fun start() {
        started = true
        val s = NISession()
        if (queue != null) s.delegateQueue = queue
        s.delegate = listener
        session = s
        generation++
        val token = s.discoveryToken?.let(::archiveToken)
        say(tokenLine(index, token, generation, nowMs(), tag))
        onState(if (peer == null) "WAITING FOR THE OTHER PHONE'S TOKEN" else "RUNNING")
        peer?.let { run(it, "new-session") }
    }

    fun peerToken(token: NIDiscoveryToken) {
        peer = token
        run(token, "peer-token")
    }

    fun stop() {
        session?.invalidate()
        session = null
    }

    private fun run(token: NIDiscoveryToken, why: String) {
        val s = session ?: return
        say("S$index RUN why=$why gen=$generation t=${fmt(nowMs(), 3)}$suffix")
        onState("RUNNING")
        val configuration = NINearbyPeerConfiguration(peerToken = token)
        if (options.cameraAssistance) configuration.cameraAssistanceEnabled = true
        if (options.extendedDistance) configuration.extendedDistanceMeasurementEnabled = true
        if (options.cameraAssistance || options.extendedDistance) {
            // Read back from the configuration itself: what was asked for is not proof it was set.
            say(
                "S$index CONFIGURED camera_assistance=${configuration.cameraAssistanceEnabled} " +
                    "extended_distance=${configuration.extendedDistanceMeasurementEnabled}$suffix",
            )
        }
        s.runWithConfiguration(configuration)
    }

    private fun current(s: NISession, what: String): Boolean {
        // `==`, not `===`: the session a delegate callback hands back is a fresh Kotlin wrapper
        // around the same Objective-C object, so reference identity said every callback was stale
        // and dropped every reading (first two-phone run, 2026-11-18). `==` is `isEqual:`, which
        // for an NISession is the object's own identity.
        if (s == session) return true
        say("S$index STALE $what (an older session) t=${fmt(nowMs(), 3)}$suffix")
        return false
    }

    /** [t] is when the callback arrived, taken before any hop to the main thread. */
    fun updated(s: NISession, t: Double, readings: List<UwbReading>) {
        if (!current(s, "UPDATE")) return
        for (r in readings) {
            say(updateLine(index, t, r, tag))
            onReading(r)
        }
    }

    fun removed(s: NISession, reason: NINearbyObjectRemovalReason) {
        if (!current(s, "REMOVED")) return
        say("S$index REMOVED reason=${reason.name} t=${fmt(nowMs(), 3)}$suffix")
        onState("REMOVED (${reason.name}) - RUNNING AGAIN")
        // The header: "To retry interacting with the peer, issue a new call to -runWithConfiguration:."
        peer?.let { run(it, "after-removed") }
    }

    fun suspended(s: NISession) {
        if (!current(s, "SUSPENDED")) return
        say("S$index SUSPENDED t=${fmt(nowMs(), 3)}$suffix")
        onState("SUSPENDED")
    }

    fun suspensionEnded(s: NISession) {
        if (!current(s, "SUSPENSION-ENDED")) return
        say("S$index SUSPENSION-ENDED t=${fmt(nowMs(), 3)}$suffix")
        // The header: "A suspended session may be run again only after -sessionSuspensionEnded: has
        // been called. Restart a session by issuing a new call to -runWithConfiguration:."
        peer?.let { run(it, "suspension-ended") }
    }

    fun startedRunning(s: NISession) {
        if (!current(s, "STARTED")) return
        say("S$index STARTED t=${fmt(nowMs(), 3)}$suffix")
    }

    fun converged(s: NISession, convergence: NIAlgorithmConvergence) {
        if (!current(s, "CONVERGENCE")) return
        val reasons = convergence.reasons.joinToString(",") { it.toString() }.ifEmpty { "-" }
        say("S$index CONVERGENCE status=${convergence.status.name} reasons=$reasons t=${fmt(nowMs(), 3)}$suffix")
    }

    fun invalidated(s: NISession, error: NSError) {
        if (!current(s, "INVALIDATED")) return
        val code = error.code
        say(
            "S$index INVALIDATED code=$code (${errorName(code)}) gen=$generation t=${fmt(nowMs(), 3)} " +
                "msg=${error.localizedDescription}$suffix",
        )
        session = null
        val limit = code == ERR_SESSIONS_LIMIT || code == ERR_EXTENDED_SESSIONS_LIMIT
        if (limit) refusedFor = errorName(code)
        // These will not change on a retry, and a probe session refused for the limit is the answer
        // the probe was asking for.
        val final = code == ERR_USER_DID_NOT_ALLOW || code == ERR_UNSUPPORTED_PLATFORM ||
            limit || restarts >= UwbSpike.RESTARTS
        if (final) {
            say("S$index NOT RESTARTING after ${errorName(code)} (restarts so far: $restarts)$suffix")
            onState("INVALIDATED: ${errorName(code)}")
            return
        }
        restarts++
        onState("INVALIDATED: ${errorName(code)} - NEW SESSION")
        start()
    }

    /**
     * The delegate. On the main thread by default, where everything is handled at once. With
     * [queued], the callbacks arrive on the session's own queue: the time and the readings are taken
     * there, and the rest hops to the main thread, which owns the session and the screen.
     */
    private class Listener(private val owner: UwbPeerSession, private val queued: Boolean) : NSObject(), NISessionDelegateProtocol {
        private fun onMain(block: () -> Unit) {
            if (queued) dispatch_async(dispatch_get_main_queue(), block) else block()
        }

        @ObjCSignatureOverride
        override fun session(session: NISession, didUpdateNearbyObjects: List<*>) {
            val t = nowMs()
            val readings = didUpdateNearbyObjects.map { readingOf(it as NINearbyObject) }
            onMain { owner.updated(session, t, readings) }
        }

        override fun session(
            session: NISession,
            didRemoveNearbyObjects: List<*>,
            withReason: NINearbyObjectRemovalReason,
        ) = onMain { owner.removed(session, withReason) }

        override fun sessionWasSuspended(session: NISession) = onMain { owner.suspended(session) }

        override fun sessionSuspensionEnded(session: NISession) = onMain { owner.suspensionEnded(session) }

        override fun session(session: NISession, didInvalidateWithError: NSError) =
            onMain { owner.invalidated(session, didInvalidateWithError) }

        override fun sessionDidStartRunning(session: NISession) = onMain { owner.startedRunning(session) }

        override fun session(
            session: NISession,
            didUpdateAlgorithmConvergence: NIAlgorithmConvergence,
            forObject: NINearbyObject?,
        ) = onMain { owner.converged(session, didUpdateAlgorithmConvergence) }
    }
}

// ---------------------------------------------------------------------------------------------
// Nearby Interaction, unpacked

private fun readingOf(o: NINearbyObject): UwbReading {
    val d = o.distance
    val distance = if (d.isNaN() || d.toRawBits() == NINearbyObjectDistanceNotAvailable.toRawBits()) null else d
    val direction = vectorOrNull(o.direction)
    val h = o.horizontalAngle
    val horizontal = if (h.isNaN() || h.toRawBits() == NINearbyObjectAngleNotAvailable.toRawBits()) null else h
    return UwbReading(
        distance = distance,
        direction = direction,
        // Apple's sample derives azimuth from the unit vector as asin(x).
        azimuthDegrees = direction?.let { (asin(it.first.toDouble().coerceIn(-1.0, 1.0)) * 180.0 / PI).toFloat() },
        horizontalDegrees = horizontal?.let { (it * 180.0 / PI).toFloat() },
        vertical = verticalName(o.verticalDirectionEstimate),
    )
}

/**
 * The direction, or null when the update carried none.
 *
 * `NINearbyObjectDirectionNotAvailable` is the header's sentinel, but Kotlin/Native refuses to
 * import it (a `simd_float3` global). A real direction is a **unit vector**, so anything that is not
 * one, NaN or not, is "no direction" — which holds whatever the sentinel's value turns out to be.
 */
private fun vectorOrNull(v: Vector128): Triple<Float, Float, Float>? {
    val x = v.getFloatAt(0)
    val y = v.getFloatAt(1)
    val z = v.getFloatAt(2)
    return if (isUnitVector(x, y, z)) Triple(x, y, z) else null
}

internal fun isUnitVector(x: Float, y: Float, z: Float): Boolean {
    if (x.isNaN() || y.isNaN() || z.isNaN()) return false
    val length = kotlin.math.sqrt((x * x + y * y + z * z).toDouble())
    return kotlin.math.abs(length - 1.0) < 0.05
}

private fun verticalName(v: Long): String = when (v) {
    1L -> "SAME"
    2L -> "ABOVE"
    3L -> "BELOW"
    4L -> "ABOVE-OR-BELOW"
    else -> "UNKNOWN"
}

private const val ERR_UNSUPPORTED_PLATFORM = -5889L
private const val ERR_USER_DID_NOT_ALLOW = -5884L
private const val ERR_SESSIONS_LIMIT = -5885L
private const val ERR_EXTENDED_SESSIONS_LIMIT = -5880L

/** NIError.h's codes, named, so the log reads without the header open beside it. */
internal fun errorName(code: Long): String = when (code) {
    -5889L -> "UnsupportedPlatform"
    -5888L -> "InvalidConfiguration"
    -5887L -> "SessionFailed"
    -5886L -> "ResourceUsageTimeout"
    -5885L -> "ActiveSessionsLimitExceeded"
    -5884L -> "UserDidNotAllow"
    -5883L -> "InvalidARConfiguration"
    -5882L -> "AccessoryPeerDeviceUnavailable"
    -5881L -> "IncompatiblePeerDevice"
    -5880L -> "ActiveExtendedDistanceSessionsLimitExceeded"
    else -> "Unknown"
}

// `isSupported` is deprecated since iOS 16 in favour of `deviceCapabilities`; it is logged beside
// them because the brief asked for both, and a disagreement between the two is itself a finding.
@Suppress("DEPRECATION")
private fun capabilities(): UwbCapabilities {
    val c = NISession.deviceCapabilities
    return UwbCapabilities(
        preciseDistance = c.supportsPreciseDistanceMeasurement,
        direction = c.supportsDirectionMeasurement,
        cameraAssistance = c.supportsCameraAssistance,
        extendedDistance = c.supportsExtendedDistanceMeasurement,
        isSupported = NISession.supported,
    )
}

/** `iPhone14,2` and the like: `utsname.machine`, which is what names the hardware. */
private fun modelIdentifier(): String = memScoped {
    val u = alloc<utsname>()
    uname(u.ptr)
    u.machine.toKString()
}

private fun archiveToken(token: NIDiscoveryToken): String? =
    NSKeyedArchiver.archivedDataWithRootObject(token, requiringSecureCoding = true, error = null)
        ?.base64EncodedStringWithOptions(0u)

private fun unarchiveToken(text: String): NIDiscoveryToken? {
    val data = NSData.create(base64EncodedString = text, options = 0u) ?: return null
    return NSKeyedUnarchiver.unarchivedObjectOfClass(NIDiscoveryToken, fromData = data, error = null) as? NIDiscoveryToken
}

/** The file the relay copies [peer]'s token into; null is the two-phone run's one file. */
private fun peerTokenPath(peer: String?): String = NSTemporaryDirectory().trimEnd('/') + "/" + peerTokenFile(peer)

private fun readPeerToken(peer: String?): String? =
    NSString.stringWithContentsOfFile(peerTokenPath(peer), NSUTF8StringEncoding, null)?.let(::cleanToken)

// ---------------------------------------------------------------------------------------------
// The lines, pure, so UwbSpikeLinesTest can pin the shapes sample.sh greps for

internal data class UwbCapabilities(
    val preciseDistance: Boolean,
    val direction: Boolean,
    val cameraAssistance: Boolean,
    val extendedDistance: Boolean,
    val isSupported: Boolean,
)

internal fun deviceLine(model: String, system: String, version: String): String =
    "DEVICE model=$model os=$system-$version"

internal fun capabilityLine(c: UwbCapabilities): String =
    "CAPABILITY precise_distance=${c.preciseDistance} direction=${c.direction} " +
        "camera_assistance=${c.cameraAssistance} extended_distance=${c.extendedDistance} " +
        "is_supported=${c.isSupported}"

internal fun updateLine(session: Int, tMs: Double, r: UwbReading, tag: String? = null): String {
    val d = r.distance?.let { fmt(it.toDouble(), 3) } ?: "nil"
    val dir = r.direction?.let { (x, y, z) -> "${fmt(x.toDouble(), 3)},${fmt(y.toDouble(), 3)},${fmt(z.toDouble(), 3)}" } ?: "nil"
    val az = r.azimuthDegrees?.let { fmt(it.toDouble(), 1) } ?: "nil"
    val h = r.horizontalDegrees?.let { fmt(it.toDouble(), 1) } ?: "nil"
    return "S$session UPDATE t=${fmt(tMs, 3)} d=$d dir=$dir az=$az hang=$h vert=${r.vertical}${peerSuffix(tag)}"
}

/** What a session's lines end in: nothing in the two-phone run, ` peer=<letter or ->` with three. */
internal fun peerSuffix(tag: String?): String = tag?.let { " peer=$it" } ?: ""

/** The tag of a session that ranges with nobody: never a letter, so the relay never carries its token. */
internal const val IDLE_TAG = "-"

/** `run.sh` reads the token after `TOKEN` and, with three phones, the letter after `peer=`. */
internal fun tokenLine(session: Int, token: String?, generation: Int, tMs: Double, tag: String?): String =
    "S$session TOKEN ${token ?: "nil"} gen=$generation t=${fmt(tMs, 3)}${peerSuffix(tag)}"

/** `run.sh` waits for `PEER TOKEN READ` to say the relay arrived. */
internal fun peerTokenLine(session: Int, readable: Boolean, chars: Int, tMs: Double, peer: String?): String =
    "S$session PEER TOKEN ${if (readable) "READ" else "UNREADABLE"} chars=$chars t=${fmt(tMs, 3)}${peerSuffix(peer)}"

internal fun tallyLine(session: Int, tMs: Double, updates: Int, perSecond: Double, state: String, tag: String?): String =
    "S$session TALLY t=${fmt(tMs, 3)} updates=$updates per_s=${fmt(perSecond, 1)} state=$state${peerSuffix(tag)}"

/** One `d=` in the two-phone run, as it always was; one `d_<letter>=` per peer with three. */
internal fun markLine(n: Int, tMs: Double, readings: List<Pair<String?, Float?>>): String {
    fun d(v: Float?) = v?.let { fmt(it.toDouble(), 3) } ?: "nil"
    val single = readings.singleOrNull()
    val fields = if (single != null && single.first == null) {
        "d=${d(single.second)}"
    } else {
        readings.joinToString(" ") { (peer, v) -> "d_$peer=${d(v)}" }
    }
    return "MARK $n t=${fmt(tMs, 3)} $fields"
}

/** The limit probe's answer. `stop` is the error that refused a session, or `cap-20`. */
internal fun limitLine(live: Int, idle: Int, model: String, os: String, stop: String, tMs: Double): String =
    "LIMIT live=$live idle=$idle total=${live + idle} model=$model os=$os stop=$stop t=${fmt(tMs, 3)}"

/** The opt-in session options, as `run.sh` passed them. */
internal data class UwbOptions(
    val cameraAssistance: Boolean = false,
    val extendedDistance: Boolean = false,
    val delegateQueue: Boolean = false,
)

internal fun configLine(o: UwbOptions, probe: Boolean): String =
    "CONFIG camera_assistance=${o.cameraAssistance} extended_distance=${o.extendedDistance} " +
        "delegate_queue=${o.delegateQueue} probe=$probe"

/** Which phone this is and which it ranges with, so a log read later names its own phone. */
internal fun peersLine(me: String?, peers: List<String>): String =
    "PEERS me=${me ?: "?"} peers=${peers.joinToString(",")}"

/** The relay's file for [peer]'s token, under the app container's `tmp/`. */
internal fun peerTokenFile(peer: String?): String =
    if (peer == null) UwbSpike.PEER_TOKEN_FILE else "uwb-peer-token-$peer.txt"

/**
 * `B,C` to `[B, C]`: single capital letters, each once, never this phone's own. Anything else is
 * dropped rather than guessed at, and nothing left means the two-phone run.
 */
internal fun parsePeers(text: String?, me: String?): List<String> =
    text.orEmpty().split(',').map { it.trim() }
        .filter { it.length == 1 && it[0] in 'A'..'Z' && it != me }
        .distinct()

/** One session the phone holds: a live one ranges with a peer, an idle one only counts against the limit. */
internal data class SessionSlot(val index: Int, val live: Boolean, val tag: String?)

/**
 * The sessions a phone opens at launch. Two phones: [sessions] of them, session 1 live, as before.
 * Three: one live session per peer first, then idle ones up to [sessions] in all.
 */
internal fun sessionPlan(peers: List<String>, sessions: Int): List<SessionSlot> =
    if (peers.isEmpty()) {
        List(sessions) { i -> SessionSlot(i + 1, live = i == 0, tag = null) }
    } else {
        List(maxOf(sessions, peers.size)) { i ->
            if (i < peers.size) {
                SessionSlot(i + 1, live = true, tag = peers[i])
            } else {
                SessionSlot(i + 1, live = false, tag = IDLE_TAG)
            }
        }
    }

/** The relay writes one base64 line; anything that is not base64 is dropped rather than guessed at. */
internal fun cleanToken(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    if (!t.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) return null
    return t
}

/** Fixed decimals with no locale and no exponent, for a log a shell script parses. */
internal fun fmt(v: Double, decimals: Int): String {
    if (v.isNaN()) return "nan"
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val negative = v < 0
    val scaled = kotlin.math.round(kotlin.math.abs(v) * scale).toLong()
    val whole = scaled / scale
    val frac = scaled % scale
    val sign = if (negative && scaled != 0L) "-" else ""
    return if (decimals == 0) "$sign$whole" else "$sign$whole.${frac.toString().padStart(decimals, '0')}"
}
