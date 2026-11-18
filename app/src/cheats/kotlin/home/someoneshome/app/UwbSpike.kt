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

    /** The file `run.sh` copies the other phone's token into, under the app container's `tmp/`. */
    const val PEER_TOKEN_FILE = "uwb-peer-token.txt"

    /** Restarts after an invalidation, per session, before the spike stops trying and says so. */
    const val RESTARTS = 20

    val launched: Boolean get() = launchSwitch(SWITCH)

    fun sessions(): Int = launchValue(SESSIONS_SWITCH)?.toIntOrNull()?.coerceIn(1, 16) ?: 1
}

/** One `[uwb]` line to the device console, which `run.sh` keeps as `phone.log` / `phoneB.log`. */
private fun say(line: String) = println("[uwb] $line")

private fun nowMs(): Double = monotonicNanos() / 1_000_000.0

// ---------------------------------------------------------------------------------------------
// The screen

@Composable
internal fun UwbSpikeRoot() {
    val count = remember { UwbSpike.sessions() }
    val model = remember { modelIdentifier() }
    var shown by remember { mutableStateOf(UwbReading.NONE) }
    var state by remember { mutableStateOf("STARTING") }
    var updates by remember { mutableIntStateOf(0) }
    var rate by remember { mutableStateOf(0.0) }
    var marks by remember { mutableIntStateOf(0) }
    val sessions = remember {
        List(count) { i ->
            UwbPeerSession(
                index = i + 1,
                onReading = { r -> if (i == 0) { shown = r; updates++ } },
                onState = { s -> if (i == 0) state = s },
            )
        }
    }

    DisposableEffect(Unit) {
        // The measurements, before anything runs: what this phone is and what it says it can do.
        say(deviceLine(model, UIDevice.currentDevice.systemName, UIDevice.currentDevice.systemVersion))
        say(capabilityLine(capabilities()))
        say("SESSIONS $count · polling ${peerTokenPath()} t=${fmt(nowMs(), 3)}")
        // A token left by an earlier launch is a session that no longer exists.
        NSFileManager.defaultManager.removeItemAtPath(peerTokenPath(), null)
        // A test phone that dims and locks on its own ends the session the step was measuring.
        UIApplication.sharedApplication.idleTimerDisabled = true
        val stopWatching = watchApp()
        sessions.first().start()
        onDispose {
            stopWatching()
            sessions.forEach { it.stop() }
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }

    // The relay's file, polled. A new token is applied to session 1 and then, if the probe asked for
    // more, to each extra session in turn.
    LaunchedEffect(Unit) {
        var applied: String? = null
        while (true) {
            val text = readPeerToken()
            if (text != null && text != applied) {
                val token = unarchiveToken(text)
                if (token == null) {
                    say("S1 PEER TOKEN UNREADABLE chars=${text.length} t=${fmt(nowMs(), 3)}")
                } else {
                    say("S1 PEER TOKEN READ chars=${text.length} t=${fmt(nowMs(), 3)}")
                    sessions.forEach { s ->
                        if (!s.started) s.start()
                        s.peerToken(token)
                    }
                }
                applied = text
            }
            delay(500)
        }
    }

    // The rate on screen, and a tally line every ten seconds for a reader with no script.
    LaunchedEffect(Unit) {
        var last = updates
        var lastAt = nowMs()
        var tallyFrom = updates
        var tallyAt = lastAt
        while (true) {
            delay(1_000)
            val now = nowMs()
            rate = (updates - last) * 1000.0 / (now - lastAt)
            last = updates
            lastAt = now
            if (now - tallyAt >= 10_000) {
                val n = updates - tallyFrom
                say("S1 TALLY t=${fmt(now, 3)} updates=$n per_s=${fmt(n * 1000.0 / (now - tallyAt), 1)} state=$state")
                tallyFrom = updates
                tallyAt = now
            }
        }
    }

    val insets = LocalPanelInsets.current
    Box(Modifier.fillMaxSize().background(Amber.Black)) {
        Column(
            Modifier.fillMaxWidth().padding(top = insets.top + 8.u, start = insets.side + 8.u, end = insets.side + 8.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            Label("UWB SPIKE - $model", size = 7.0, color = Amber.Dim)
            Label(state, size = 7.0, color = Amber.Dim)
            // ASCII only: the pixel face is not known to carry a dash or a degree sign.
            Label(shown.distance?.let { "${fmt(it.toDouble(), 2)} M" } ?: "NO READING", size = 28.0, color = Amber.Mid)
            Label(
                shown.azimuthDegrees?.let { "DIR ${fmt(it.toDouble(), 0)} DEG" } ?: "DIR NONE",
                size = 10.0,
                color = Amber.Mid,
            )
            Label("${fmt(rate, 1)} / S - $updates UPDATES", size = 7.0, color = Amber.Dim)
            Label("MARKS $marks", size = 7.0, color = Amber.Dim)
        }
        // One control: MARK, which stamps a numbered line into the log at the moment it is pressed.
        Box(
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = insets.bottom + 16.u)
                .border(1.u, Amber.Dim)
                .tap {
                    marks++
                    say("MARK $marks t=${fmt(nowMs(), 3)} d=${shown.distance?.let { fmt(it.toDouble(), 3) } ?: "nil"}")
                }
                .padding(horizontal = 24.u, vertical = 10.u),
        ) {
            Label("MARK", size = 10.0, color = Amber.Mid)
        }
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
 */
private class UwbPeerSession(
    val index: Int,
    private val onReading: (UwbReading) -> Unit,
    private val onState: (String) -> Unit,
) {
    private var session: NISession? = null
    private var peer: NIDiscoveryToken? = null
    private var generation = 0
    private var restarts = 0
    private val listener = Listener(this)

    var started = false
        private set

    fun start() {
        started = true
        val s = NISession()
        s.delegate = listener
        session = s
        generation++
        val token = s.discoveryToken?.let(::archiveToken)
        say("S$index TOKEN ${token ?: "nil"} gen=$generation t=${fmt(nowMs(), 3)}")
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
        say("S$index RUN why=$why gen=$generation t=${fmt(nowMs(), 3)}")
        onState("RUNNING")
        s.runWithConfiguration(NINearbyPeerConfiguration(peerToken = token))
    }

    private fun current(s: NISession, what: String): Boolean {
        // `==`, not `===`: the session a delegate callback hands back is a fresh Kotlin wrapper
        // around the same Objective-C object, so reference identity said every callback was stale
        // and dropped every reading (first two-phone run, 2026-11-18). `==` is `isEqual:`, which
        // for an NISession is the object's own identity.
        if (s == session) return true
        say("S$index STALE $what (an older session) t=${fmt(nowMs(), 3)}")
        return false
    }

    fun updated(s: NISession, objects: List<*>) {
        if (!current(s, "UPDATE")) return
        val t = nowMs()
        for (o in objects) {
            val r = readingOf(o as NINearbyObject)
            say(updateLine(index, t, r))
            onReading(r)
        }
    }

    fun removed(s: NISession, reason: NINearbyObjectRemovalReason) {
        if (!current(s, "REMOVED")) return
        say("S$index REMOVED reason=${reason.name} t=${fmt(nowMs(), 3)}")
        onState("REMOVED (${reason.name}) - RUNNING AGAIN")
        // The header: "To retry interacting with the peer, issue a new call to -runWithConfiguration:."
        peer?.let { run(it, "after-removed") }
    }

    fun suspended(s: NISession) {
        if (!current(s, "SUSPENDED")) return
        say("S$index SUSPENDED t=${fmt(nowMs(), 3)}")
        onState("SUSPENDED")
    }

    fun suspensionEnded(s: NISession) {
        if (!current(s, "SUSPENSION-ENDED")) return
        say("S$index SUSPENSION-ENDED t=${fmt(nowMs(), 3)}")
        // The header: "A suspended session may be run again only after -sessionSuspensionEnded: has
        // been called. Restart a session by issuing a new call to -runWithConfiguration:."
        peer?.let { run(it, "suspension-ended") }
    }

    fun startedRunning(s: NISession) {
        if (!current(s, "STARTED")) return
        say("S$index STARTED t=${fmt(nowMs(), 3)}")
    }

    fun converged(s: NISession, convergence: NIAlgorithmConvergence) {
        if (!current(s, "CONVERGENCE")) return
        val reasons = convergence.reasons.joinToString(",") { it.toString() }.ifEmpty { "-" }
        say("S$index CONVERGENCE status=${convergence.status.name} reasons=$reasons t=${fmt(nowMs(), 3)}")
    }

    fun invalidated(s: NISession, error: NSError) {
        if (!current(s, "INVALIDATED")) return
        val code = error.code
        say(
            "S$index INVALIDATED code=$code (${errorName(code)}) gen=$generation t=${fmt(nowMs(), 3)} " +
                "msg=${error.localizedDescription}",
        )
        session = null
        // These three will not change on a retry, and a probe session refused for the limit is the
        // answer the probe was asking for.
        val final = code == ERR_USER_DID_NOT_ALLOW || code == ERR_UNSUPPORTED_PLATFORM ||
            code == ERR_SESSIONS_LIMIT || restarts >= UwbSpike.RESTARTS
        if (final) {
            say("S$index NOT RESTARTING after ${errorName(code)} (restarts so far: $restarts)")
            onState("INVALIDATED: ${errorName(code)}")
            return
        }
        restarts++
        onState("INVALIDATED: ${errorName(code)} - NEW SESSION")
        start()
    }

    private class Listener(private val owner: UwbPeerSession) : NSObject(), NISessionDelegateProtocol {
        @ObjCSignatureOverride
        override fun session(session: NISession, didUpdateNearbyObjects: List<*>) =
            owner.updated(session, didUpdateNearbyObjects)

        override fun session(
            session: NISession,
            didRemoveNearbyObjects: List<*>,
            withReason: NINearbyObjectRemovalReason,
        ) = owner.removed(session, withReason)

        override fun sessionWasSuspended(session: NISession) = owner.suspended(session)

        override fun sessionSuspensionEnded(session: NISession) = owner.suspensionEnded(session)

        override fun session(session: NISession, didInvalidateWithError: NSError) =
            owner.invalidated(session, didInvalidateWithError)

        override fun sessionDidStartRunning(session: NISession) = owner.startedRunning(session)

        override fun session(
            session: NISession,
            didUpdateAlgorithmConvergence: NIAlgorithmConvergence,
            forObject: NINearbyObject?,
        ) = owner.converged(session, didUpdateAlgorithmConvergence)
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

private fun peerTokenPath(): String = NSTemporaryDirectory().trimEnd('/') + "/" + UwbSpike.PEER_TOKEN_FILE

private fun readPeerToken(): String? =
    NSString.stringWithContentsOfFile(peerTokenPath(), NSUTF8StringEncoding, null)?.let(::cleanToken)

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

internal fun updateLine(session: Int, tMs: Double, r: UwbReading): String {
    val d = r.distance?.let { fmt(it.toDouble(), 3) } ?: "nil"
    val dir = r.direction?.let { (x, y, z) -> "${fmt(x.toDouble(), 3)},${fmt(y.toDouble(), 3)},${fmt(z.toDouble(), 3)}" } ?: "nil"
    val az = r.azimuthDegrees?.let { fmt(it.toDouble(), 1) } ?: "nil"
    val h = r.horizontalDegrees?.let { fmt(it.toDouble(), 1) } ?: "nil"
    return "S$session UPDATE t=${fmt(tMs, 3)} d=$d dir=$dir az=$az hang=$h vert=${r.vertical}"
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
