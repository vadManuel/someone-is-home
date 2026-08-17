package home.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFDDDDDD))
private val monoDim = mono.copy(color = Color(0xFF888888))

@Composable
fun SpikeApp() {
    var screen by remember { mutableStateOf(Screen.CONFIG) }
    var report by remember { mutableStateOf<String?>(null) }
    var savedPaths by remember { mutableStateOf<List<String>>(emptyList()) }

    val running by GateEngine.running
    val finished by GateEngine.finished

    LaunchedEffect(finished) {
        if (finished && screen == Screen.RUN) {
            // Let the GC poller observe the closing bracket collection before summarising,
            // then stop it. Summarising first would drop the last epoch and understate the
            // allocation total.
            kotlinx.coroutines.delay(250)
            GcProbe.stop()

            val summary = Report.summarise()
            report = Report.text(summary)
            savedPaths = listOf(
                writeResults("trials-${summary.label}.csv", Report.csv()),
                writeResults("gc-${summary.label}.csv", Report.gcCsv()),
                writeResults("stalls-${summary.label}.csv", Report.stallCsv()),
                writeResults("summary-${summary.label}.txt", report!!),
            )
            screen = Screen.REPORT
        }
    }

    when (screen) {
        Screen.CONFIG -> ConfigScreen(onStart = { cfg ->
            report = null
            savedPaths = emptyList()
            screen = Screen.RUN
            startRun(cfg)
        })

        Screen.RUN -> RunScreen(onAbort = {
            GateEngine.stop()
        })

        Screen.REPORT -> ReportScreen(
            text = report.orEmpty(),
            paths = savedPaths,
            onBack = { screen = Screen.CONFIG },
        )
    }
}

private enum class Screen { CONFIG, RUN, REPORT }

private var activeTapSource: TapTriggerSource? = null

private fun startRun(cfg: RunConfig) {
    val source: TriggerSource = when (cfg.trigger) {
        TriggerKind.TAP -> TapTriggerSource().also { activeTapSource = it }
        TriggerKind.SCRIPTED -> { activeTapSource = null; ScriptedTriggerSource() }
    }
    GateEngine.start(cfg, source)
}

// -----------------------------------------------------------------------------------------

@Composable
private fun ConfigScreen(onStart: (RunConfig) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101010))
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Spacer(Modifier.size(40.dp))
        BasicText("STACK GATE — story 1.7", style = mono.copy(fontSize = 16.sp))
        BasicText(deviceDescription(), style = monoDim)
        BasicText(
            "Can Compose Multiplatform blank the lamp in the same frame as a trigger?",
            style = monoDim,
        )
        Spacer(Modifier.size(24.dp))

        RunConfig.all.forEach { cfg ->
            Column(
                Modifier.fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(Color(0xFF1E1E1E))
                    .clickable { onStart(cfg) }
                    .padding(14.dp)
            ) {
                BasicText(cfg.label, style = mono.copy(fontSize = 15.sp))
                BasicText(
                    "n=${cfg.trials}  pressure=${cfg.pressure}  idle=${cfg.idle}  " +
                        "prewarm=${cfg.prewarmTrials}  trigger=${cfg.trigger}",
                    style = monoDim,
                )
                BasicText(cfg.note, style = monoDim)
            }
        }

        Spacer(Modifier.size(24.dp))
        BasicText(
            "VOLUME is the gate. Run VOLUME_CONTROL too — if the two tails match, the " +
                "pressure generator is a no-op and the GC half was never tested.",
            style = monoDim,
        )
        BasicText(
            "CAMERA calibrates the instrument against photons. Neither layer answers the " +
                "gate alone.",
            style = monoDim,
        )
        Spacer(Modifier.size(40.dp))
    }
}

// -----------------------------------------------------------------------------------------

@Composable
private fun RunScreen(onAbort: () -> Unit) {
    val cfg = GateEngine.config
    val progress by GateEngine.progress

    // Camera mode only: a corner patch that flips every drawn frame, so app frames are
    // countable on 240 fps video and a dropped frame is visible rather than inferred. It
    // forces a redraw every frame, which keeps the renderer permanently warm — that is a real
    // difference from the other modes, and it is why it is off everywhere else.
    if (cfg.framePatch) {
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { GateEngine.framePatchPhase.intValue++ }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {

        Lamp(showFramePatch = cfg.framePatch)

        if (cfg.trigger == TriggerKind.TAP) {
            // Initial pass: take the press as early in the dispatch as Compose allows. Anything
            // that consumes it downstream must not be able to move the trigger's timestamp.
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                activeTapSource?.onTap()
                            }
                        }
                    }
                }
            )
        }

        // Deliberately sparse: progress updates every 250 trials, never mid-trial.
        Column(Modifier.align(Alignment.TopStart).padding(top = 60.dp, start = 16.dp)) {
            BasicText(cfg.label, style = monoDim)
            BasicText("$progress / ${cfg.trials}", style = monoDim)
            if (cfg.trigger == TriggerKind.TAP) {
                BasicText("tap anywhere — ${GateEngine.completedTrials()} done", style = monoDim)
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(24.dp)
                .background(Color(0xFF303030)).clickable { onAbort() }.padding(16.dp)
        ) {
            BasicText("STOP", style = mono)
        }
    }
}

/**
 * THE BLACKOUT DRAW PATH.
 *
 * The state is read INSIDE the draw lambda, not in composition. That means a change invalidates
 * draw only — composition and layout are skipped entirely, which is the whole point. Read it in
 * composition instead and every blackout costs a recomposition, which is where the allocation
 * that feeds the GC pause comes from.
 *
 * `mutableIntStateOf` rather than `MutableState<Color>`: Color is a value class over ULong and
 * would box on every single write.
 */
@Composable
private fun Lamp(showFramePatch: Boolean) {
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            val argb = GateEngine.lampArgb.intValue
            drawRect(Color(argb))
            GateEngine.onLampDrawn(argb)

            if (showFramePatch) {
                val phase = GateEngine.framePatchPhase.intValue
                val on = (phase and 1) == 1
                drawRect(
                    color = if (on) Color.White else Color(0xFF202020),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - 140f, 140f),
                    size = androidx.compose.ui.geometry.Size(120f, 120f),
                )
            }
        }
    )
}

// -----------------------------------------------------------------------------------------

@Composable
private fun ReportScreen(text: String, paths: List<String>, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101010))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Spacer(Modifier.size(50.dp))
        BasicText(text, style = mono.copy(fontSize = 11.sp))
        Spacer(Modifier.size(16.dp))
        paths.forEach { BasicText(it, style = monoDim.copy(fontSize = 9.sp)) }
        Spacer(Modifier.size(24.dp))
        Box(
            Modifier.background(Color(0xFF303030)).clickable { onBack() }.padding(16.dp)
        ) {
            BasicText("BACK", style = mono)
        }
        Spacer(Modifier.size(40.dp))
    }
}
