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
import home.someoneshome.ui.Amber
import home.someoneshome.ui.Label
import home.someoneshome.ui.LocalPanelInsets
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
    val camera = CameraCardScanner(log = ::note, countFrames = true)

    /** Newest first, and mirrored to stdout so the same line is in the device log. */
    val log = mutableStateListOf<String>()

    /** One row of the bench: what to tap, and what it does. */
    class Row(val label: String, val detail: String, val fire: () -> Unit)

    val rows: List<Row> = listOf(
        Row("START", "open the session — front camera, no preview") { watch { } },
        Row("STOP", "close it — the lens is the most expensive thing on the phone") { release() },
    )

    /**
     * Point the camera at cards and pass every symbol on, after saying what it was.
     *
     * The payload is passed **verbatim** to [onPayload] — this method reads it, describes it and
     * changes nothing, because deciding what a card is belongs to `CardPayload` and a bench that
     * tidied a payload would be certifying a scanner that does not exist.
     */
    fun watch(onPayload: (String) -> Unit) {
        note("watching")
        camera.start { payload ->
            describe(payload)
            haptics.buzz(Haptic.Short)
            onPayload(payload)
        }
    }

    /** Give the lens back. Called when the screen goes away, and by the STOP row. */
    fun release() {
        camera.stop()
        note("released")
    }

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
            .padding(top = insets.top + 6.u, bottom = insets.bottom + 18.u)
            .padding(horizontal = 14.u)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Label("${BuildVariant.MARKER} — CAMERA", size = 8.0, color = Amber.Bright, tracking = 0.16)
        Label("ONLY PAPER PROVES A SCAN", size = 6.0, color = Amber.Dim, tracking = 0.2)
        Label(
            "The front camera is reading. Hold a printed card up to the TOP of the phone — the " +
                "lens is on the same side as this screen.",
            size = 5.5,
            color = Amber.Dim,
        )
        for (row in cheat.rows) {
            Column(
                Modifier.fillMaxWidth().border(1.u, Amber.Faint).tap(row.fire)
                    .padding(horizontal = 6.u, vertical = 3.u),
                verticalArrangement = Arrangement.spacedBy(1.u),
            ) {
                Label(row.label, size = 7.0, color = Amber.Bright, tracking = 0.08)
                Label(row.detail, size = 5.5, color = Amber.Dim)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.u)) {
            Label("LOG", size = 6.5, color = Amber.Dim, tracking = 0.2)
        }
        for (line in cheat.log) {
            Label(line, size = 6.0, color = Amber.Dim)
        }
    }
}
