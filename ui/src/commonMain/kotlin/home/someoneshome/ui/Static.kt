package home.someoneshome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag

/**
 * **STATIC — what a jammed monitoring surface looks like** (D-186, `gdd.md:447-495`).
 *
 * *Animated noise, with the real data faintly beneath it — virtually impossible to parse rather
 * than merely degraded.* The surface underneath is drawn **normally and completely**, and this is
 * laid over it: the counts are there, they are correct, and a reader cannot get them out.
 *
 * ### Why the real data stays underneath rather than being replaced
 *
 * A blanked view is a broken view, and D-186 is explicit that Static must be **distinguishable from
 * a disconnection** — a jammed Terminal does not read as a broken one. ONE READER AT A TIME
 * already means *somebody else is at the station* (D-199), and a third failure picture that
 * resembled either would turn the ability's one guaranteed consequence — the reader knowing
 * precisely what they were prevented from seeing — into a bug report. Leaving the true render beneath is also what makes the jam legible as
 * an attack rather than as a fault: you can see there is something there.
 *
 * ### Rule 5: this is authored light, and the device invents none of it
 *
 * *No local animation, tween or easing the core did not emit.* The field is a pure function of the
 * seed the house sent and a frame number — [Static.field] — so the same jam draws the same frames
 * on six phones, and a replay of the round draws them again. **Nothing here reads a wall clock, a
 * random source, or anything about this phone.** The counter advances on the compositor's own beat
 * and does nothing but count, which is the latitude the hold bar's fill already has.
 *
 * [frame] is how a screenshot renders a fixed field instead of whatever the compositor happened to
 * be on; passing one **stops the loop from running at all**, so a render test is a still picture
 * rather than a race with a clock. In play it is null and the frame loop drives it.
 *
 * ### The allocation budget
 *
 * One `Canvas`, [Static.COLS] × [Static.ROWS] rects a step, and one `IntArray` per step — the
 * plan's own arrangement and for its reason (~0.5 MB/s whole-app, measured on hardware in story
 * 1.7). A cell grid rather than per-pixel noise is what makes that affordable, and it is also what
 * it should look like: a monitoring readout failing, not television.
 */
@Composable
fun StaticNoise(jam: StaticJam, modifier: Modifier = Modifier, frame: Int? = null) {
    var ticking by remember(jam) { mutableStateOf(0) }
    // **`rememberHold`'s frame loop, and it is BOUNDED by the jam's own window** — the house's
    // seconds, not a number this file invented. An unbounded loop would be an animation that never
    // settles, which on a phone is a battery and on a Compose test is a hang. It ends where the
    // window ends, so the veil's last frame and the panel field's own expiry (`FlowHost`) agree by
    // arithmetic rather than by two timers happening to line up.
    LaunchedEffect(jam, frame) {
        if (frame != null) return@LaunchedEffect
        val window = jam.seconds * 1_000L
        var last = withFrameMillis { it }
        var spent = 0L
        while (spent < window) {
            val now = withFrameMillis { it }
            spent += now - last
            last = now
            // Quantised, so the field is redrawn a few times a second rather than on every frame:
            // noise at 120 Hz is a grey wash, and what the design asks for is a readout fighting to
            // be read. The step is a look, and it is the one number here the house did not send.
            ticking = (spent / STEP_MILLIS).toInt()
        }
    }
    val cells = Static.field(jam.seed, frame ?: ticking)
    Box(modifier.testTag(STATIC_VEIL)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width / Static.COLS
            val h = size.height / Static.ROWS
            for (i in cells.indices) {
                drawRect(
                    color = ink(cells[i]),
                    topLeft = Offset((i % Static.COLS) * w, (i / Static.COLS) * h),
                    size = Size(w, h),
                )
            }
        }
    }
}

/** The noise field, and the two numbers that shape it. */
object Static {

    /** Cells across. A readout's grain, not television's — see [StaticNoise]'s allocation note. */
    const val COLS: Int = 24

    /** Cells down. Roughly square cells on a phone-shaped surface. */
    const val ROWS: Int = 40

    /** Intensity levels, including the transparent one at zero. */
    const val LEVELS: Int = 6

    /**
     * **The noise field: [COLS] × [ROWS] intensities in `0 until LEVELS`, from a seed and a frame.**
     *
     * Pure, total, and the only place the picture is decided. It takes no `Random`, reads no clock
     * and knows nothing about this device — which is what lets a test assert the three properties
     * the render rests on without drawing anything: **the same jam and frame give the same field**,
     * two jams with different seeds differ, and successive frames of one jam differ.
     *
     * The mix is a plain integer hash written out here rather than taken from a platform source,
     * because what it has to be is **the same on every target**: a field that differed between
     * Kotlin/Native and the JVM would make the noise a fact about which phone you were holding.
     */
    fun field(seed: Long, frame: Int, cols: Int = COLS, rows: Int = ROWS): IntArray {
        val out = IntArray(cols * rows)
        for (i in out.indices) {
            var h = seed xor (frame.toLong() * -0x61c8864680b583ebL)
            h = (h + i * 0x632BE59BD9B4E019L) * -0x40a7b892e31b1a47L
            h = h xor (h ushr 31)
            out[i] = ((h ushr 17) and 0xFFFF).toInt() % LEVELS
        }
        return out
    }
}

/**
 * A cell's intensity as ink, from transparent to the panel's own brightest.
 *
 * **Level zero is fully transparent** and that is the whole reason the data underneath is *faintly*
 * readable rather than gone: about one cell in six is a hole, the holes move every step, and what a
 * reader gets through them is glimpses of a number they cannot hold still long enough to add up.
 * The rest climbs to [Amber.Bright] — the panel's own ink, because a jam is the house's monitoring
 * failing and not a second colour arriving from somewhere.
 */
private fun ink(level: Int): Color =
    if (level == 0) {
        Color.Transparent
    } else {
        lerp(JAM_VEIL_FLOOR, Amber.Bright, (level - 1).toFloat() / (Static.LEVELS - 2))
    }

/**
 * **The veil's lowest lit level, and it is a light level rather than an ink** (D-208).
 *
 * `#241806` is the value D-202 retired from the panel's ramp as a line that barely reached the eye.
 * Here it is not a line: it is the dimmest step of the light a jammed surface emits, and the owner
 * ruled that the light does not change by a single bit. So it keeps its exact value under its own
 * name, outside the ramp, and nothing else draws with it.
 */
internal val JAM_VEIL_FLOOR: Color = Color(0xFF241806)

/** How long one field stands before the next is drawn. A look, and the house did not send it. */
private const val STEP_MILLIS: Long = 90L

/** The tag a test finds the veil by, so *is this surface jammed* is one question with one answer. */
const val STATIC_VEIL: String = "static-veil"
