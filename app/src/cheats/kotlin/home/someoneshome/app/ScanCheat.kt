package home.someoneshome.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import home.someoneshome.model.CardPayload
import home.someoneshome.model.Haptic
import home.someoneshome.platform.CameraCardScanner
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.ScanUse
import home.someoneshome.platform.ScanWindow
import home.someoneshome.ui.Amber
import home.someoneshome.ui.Ramp
import home.someoneshome.ui.Label
import home.someoneshome.ui.LocalPanelInsets
import home.someoneshome.ui.PanelVals
import home.someoneshome.ui.tap
import home.someoneshome.ui.u
import kotlinx.coroutines.delay

/**
 * **The camera bench: one capture session, and everything it says written down.**
 *
 * A camera is the second output in this app that a screenshot cannot show. `run-app.sh` can say
 * the app is running; a render harness can say the scan screen lays out; **only a piece of paper
 * held up to a lens proves a scan.** So this surface does what the haptic bench does — it puts the
 * halves of the verification within reach: the session's own lines go to the device log, the same
 * lines go on the screen for whoever is holding the phone, and a card read buzzes.
 *
 * ### One camera, two surfaces
 *
 * The scanner is held here and lent out. The bench's own view starts it with a listener that only
 * writes down what it saw; the panel's `ScanMarker` screen starts it with one that feeds the real
 * registration flow. `start` replaces the listener rather than adding one, so the second caller is
 * the only caller — which is the seam's documented behaviour rather than an arrangement invented
 * here, and it is why there is never a second capture session competing for the same lens.
 *
 * ### The buzz on a read is the bench standing in for the house
 *
 * `HapticCheat` carries the same caveat and it applies unchanged: **there is no authority attached
 * to this build**, so nothing here is `Rules.kt` deciding that a scan confirms. What the buzz shows
 * is that the two pieces of hardware D1 and D2 built meet — a symbol resolved by the camera reaches
 * the motor on the same phone, which is the whole of *you will feel it catch* below the effect
 * boundary. Which effect a real scan produces is `core`'s, and `core` tests it.
 *
 * ### Two shapes, because there are two scans in this game
 *
 * [scan] is D-159's: **one window, one read**, opened by a tap and closed by the read or by the
 * ten seconds running out. That is the in-round scan, and it is what [ScanWindow] is.
 *
 * [watch] is the host-setup scan, which is a different thing and must not grow into the other one
 * (E-D2-3): the host walks the house with the viewfinder up, and the screen's own instruction is
 * KEEP SCANNING TO ADD MORE. It stays continuous. What it does **not** do any more is buzz on
 * every delivery — a camera cannot tell a new card from the same card still in frame, so the
 * first version of this bench vibrated at the frame rate for as long as somebody held a card up.
 */
class ScanCheat(private val haptics: Haptics) {

    /**
     * The frame counter is on, and this is the one build it is ever on in.
     *
     * A metadata output speaks when it sees a symbol and is silent otherwise, so a phone on a desk
     * at three in the morning produces the same log whether the pipeline is delivering pixels or
     * has quietly stopped. The counter is what makes those two distinguishable in a room where
     * nobody is holding a card.
     */
    val camera = CameraCardScanner(use = ScanUse.InRound, log = ::note, countFrames = true)

    /**
     * **The in-round scan, as one object**: the lens, the motor and D-159's window.
     *
     * Held rather than built per scan, so the bench can ask whether a window is still open — and
     * so the expiry the screen drives lands on the same window the tap opened.
     */
    val window = ScanWindow(camera, haptics)

    /** Newest first, and mirrored to stdout so the same line is in the device log. */
    val log = mutableStateListOf<String>()

    /** One row of the bench: what to tap, and what it does. */
    class Row(val label: String, val detail: String, val fire: () -> Unit)

    val rows: List<Row> = listOf(
        Row("SCAN", "D-159's window — one read, one buzz, then it closes itself") { scan { } },
        Row("GIVE UP", "the ten seconds, run out — two buzzes and the lens goes back") { expire() },
        Row("WATCH", "the HOST-SETUP shape: continuous, one buzz per new card") { watch { } },
        Row("STOP", "close it — the lens is the most expensive thing on the phone") { release() },
    )

    /**
     * **Tap SCAN.** One window: the first symbol resolved is the read, it buzzes once, and the
     * window closes itself. Everything after it in the same window is dropped, including the same
     * card still sitting in the frame.
     *
     * The buzz is [ScanWindow]'s, not this file's. That is the point — the bench proves the ruled
     * shape rather than a re-implementation of it that happens to agree today.
     */
    fun scan(onAcquired: (String) -> Unit) {
        note("SCAN — window open, ${PanelVals.SCAN_WINDOW_MILLIS}ms")
        window.open { payload ->
            describe(payload)
            note("caught — window closed; tap SCAN again for another")
            onAcquired(payload)
        }
    }

    /** The ten seconds ran out. Two vibrates, and the lens goes back. */
    fun expire() {
        if (!window.isOpen) {
            note("nothing to give up on — no window is open")
            return
        }
        window.expire()
        note("GAVE UP — two buzzes, nothing spent, tap SCAN again")
    }

    /**
     * Point the camera at cards and pass every symbol on, after saying what it was.
     *
     * The payload is passed **verbatim** to [onPayload] — this method reads it, describes it and
     * changes nothing, because deciding what a card is belongs to `CardPayload` and a bench that
     * tidied a payload would be certifying a scanner that does not exist.
     *
     * **It buzzes on a new card and not on a repeat frame.** The seam says the same card arrives
     * over and over and refuses to guess which repeats mean anything; here the answer is that a
     * payload identical to the one before it is the card that has not moved, and a host holding
     * one steady must not feel the phone at the frame rate. A *different* card always buzzes, and
     * coming back to a card later — with another one in between — buzzes again, because that is
     * the host correcting themselves and it is a real event.
     */
    fun watch(onPayload: (String) -> Unit) {
        note("watching")
        last = null
        camera.start { payload ->
            val repeat = payload == last
            last = payload
            describe(payload)
            if (!repeat) haptics.buzz(Haptic.Short)
            onPayload(payload)
        }
    }

    /** Give the lens back. Called when the screen goes away, and by the STOP row. */
    fun release() {
        // Silent: a lens handed back is not the house refusing a scan, and D-160's double is the
        // house refusing a scan. `close` is the seam's word for exactly this difference.
        window.close()
        camera.stop()
        last = null
        note("released")
    }

    /** The last payload [watch] saw. Only ever compared to the next one — see [watch]. */
    private var last: String? = null

    /**
     * **Open the session, watch it for a while, then leave it armed for whoever arrives.**
     *
     * The hand a unit running overnight does not have — and unlike the haptic sweep, this one
     * cannot finish the job. A sweep can open a session and count frames; it cannot hold a printed
     * card up to the lens, and on a phone that has never been asked for the camera it cannot even
     * do that: iOS asks once, in a dialog, and a dialog is a tap.
     *
     * So it ends **watching rather than released**. Whoever picks the phone up taps the one alert
     * that is waiting, holds a card up, and the whole path runs without anybody having to find a
     * screen first. The last line says what is still missing, so a log read in the morning cannot
     * be mistaken for an end-to-end scan.
     */
    suspend fun sweep() {
        note("SWEEP — opening the session with nobody holding a card")
        watch { }
        delay(WATCH_MILLIS)
        release()
        note("SWEEP DONE — frames are not a scan; only a card held to the lens is")
        // Left armed on purpose. See above: the permission dialog is a tap, and the phone should be
        // ready for the card that follows it rather than for somebody to find the START row.
        watch { }
        note("LEFT WATCHING — tap ALLOW if the camera was never asked for, then hold a card up")
    }

    /** What the payload was, and what the decoder made of it. Neither is decided here. */
    private fun describe(payload: String) {
        val read = when (val result = CardPayload.decode(payload)) {
            is CardPayload.Result.Read ->
                "${result.card.shape.id} · ${result.card.id.value}" +
                    if (result.card.isOrdinary) "" else " (reserved)"

            is CardPayload.Result.Rejected -> "refused: ${result.why}"
        }
        note("read '$payload' → $read")
    }

    private fun note(line: String) {
        // Both places on purpose. The screen is for the person holding the phone; stdout is the
        // device log, which is the only evidence a unit with no card in its hand can produce.
        println("[scan-bench] $line")
        log.add(0, line)
        while (log.size > LOG_LINES) log.removeAt(log.size - 1)
    }

    companion object {

        /** The environment variable `devicectl` sets to make the bench drive itself once. */
        const val SWEEP_SWITCH = "SOMEONES_HOME_SCAN_SWEEP"

        /** Long enough for the session to start, settle, and count frames worth quoting. */
        private const val WATCH_MILLIS = 12_000L

        private const val LOG_LINES = 14
    }
}

/**
 * The bench, drawn — and it holds the lens for exactly as long as it is on screen.
 *
 * The `DisposableEffect` is not tidiness. A capture session left running behind a screen nobody is
 * looking at is the camera, the ISP and the battery, all held by a build whose whole purpose is to
 * be carried around a house for an evening.
 */
@Composable
fun ScanCheatScreen(cheat: ScanCheat) {
    val insets = LocalPanelInsets.current
    DisposableEffect(cheat) {
        cheat.watch { }
        onDispose { cheat.release() }
    }
    Column(
        Modifier.fillMaxSize().background(Amber.Black)
            .padding(top = insets.top + 7.2.u, bottom = insets.bottom + 21.6.u)
            .padding(horizontal = 16.8.u)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.6.u),
    ) {
        Label("${BuildVariant.MARKER} — CAMERA", size = 9.6, color = Amber.Bright, tracking = 0.16)
        Label("ONLY PAPER PROVES A SCAN", size = 7.2, color = Amber.Dim, tracking = 0.2)
        Label(
            "The front camera is reading. Turn the TOP of the phone to a printed card — the " +
                "lens is on the same side as this screen. SCAN reads once; WATCH keeps reading.",
            size = 6.6,
            color = Amber.Dim,
        )
        for (row in cheat.rows) {
            Column(
                Modifier.fillMaxWidth().border(1.u, Ramp.Faint).tap(row.fire)
                    .padding(horizontal = 7.2.u, vertical = 3.6.u),
                verticalArrangement = Arrangement.spacedBy(1.2.u),
            ) {
                Label(row.label, size = 8.4, color = Amber.Bright, tracking = 0.08)
                Label(row.detail, size = 6.6, color = Amber.Dim)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.6.u)) {
            Label("LOG", size = 7.8, color = Amber.Dim, tracking = 0.2)
        }
        for (line in cheat.log) {
            Label(line, size = 7.2, color = Amber.Dim)
        }
    }
}
