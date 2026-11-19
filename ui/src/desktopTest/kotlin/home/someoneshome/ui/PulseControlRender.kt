package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **The tap control after the node scan, drawn, and the springboard under it still whole** (D-295).
 *
 * Writes PNGs of the Egress widget at a node (fresh, two pips, after ON THE BEAT, after OFF THE BEAT
 * with one pip, an Insider's), through a run of four in a row, a miss and the lockout's reset (D-298),
 * and on a phone not at a node, to `build/screens/pulse-*.png` and to `/tmp/someones-home-d298/`, so
 * they can be looked at. Checks two things a render can:
 *
 * - an Insider's control and a Resident's are the same pixels;
 * - **the app grid is whole under every state of the widget**: every app label is laid out at its
 *   full height and sits above the dock, on a phone with no hardware insets and on the iPhone 16
 *   Pro's measured ones (`DeviceLayoutTest`). The first build's control pushed SETTINGS and CAMERA
 *   off the grid whenever the beat line showed, and nothing failed.
 */
class PulseControlRender {

    private val out = File("build/screens").apply { mkdirs() }
    private val look = File("/tmp/someones-home-d298").apply { mkdirs() }

    /** No hardware insets, and an iPhone 16 Pro's, as `DeviceLayoutTest` measured them. */
    private val phones = listOf(
        "flat" to PanelInsets(),
        "16pro" to PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp),
    )

    private val gridLabels = listOf("FILES", "NOTES", "MESSAGES", "SETTINGS", "CAMERA")

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    private fun render(name: String, model: FlowModel, insets: PanelInsets = PanelInsets(), save: Boolean = true): Pair<BufferedImage, List<String>> {
        var image: BufferedImage? = null
        val faults = mutableListOf<String>()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(600.dp, 1300.dp)) {
                        DeviceCanvas(insets = insets) {
                            Screen(model.state, model.actions(), model.editor, model.homes, model.lobby, model.meeting)
                        }
                    }
                }
            }
            image = onRoot().captureToImage().toAwtImage()
            fun node(text: String): SemanticsNodeInteraction = onNodeWithText(text, useUnmergedTree = true)
            val dockTop = node("SUBROUTINES").fetchSemanticsNode().boundsInRoot.top
            val full = node("FILES").fetchSemanticsNode().boundsInRoot.height
            for (label in gridLabels) {
                val at = node(label).fetchSemanticsNode().boundsInRoot
                if (at.height < full - 1f) faults += "$name: $label is ${at.height} px tall, its row's label is $full"
                if (at.bottom > dockTop) faults += "$name: $label ends at ${at.bottom}, under the dock at $dockTop"
            }
        }
        val png = image!!
        if (save) {
            ImageIO.write(png, "png", File(out, "$name.png"))
            ImageIO.write(png, "png", File(look, "$name.png"))
        }
        return png to faults
    }

    private fun egressOpened() = RoundMessage.EgressOpened(
        type = EgressType.Beacon,
        nodes = listOf(
            Destination(MarkerId("west-card"), "circle", "UTILITY"),
            Destination(MarkerId("east-card"), "square", "LANDING"),
        ),
        remaining = 2_400,
        haptic = Haptic.Long,
        pulseFrom = 86_402_000L,
    )

    private fun atANode(role: PanelRole, taps: Int = 0, answer: Boolean? = null): FlowModel {
        // Pulse 4 of the grid, so each tap a pulse later is a run (D-298).
        var now = 86_406_500L
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            houseNow = { now },
        )
        model.houseSaid(egressOpened())
        model.scanning(MarkerId("east-card"))
        model.houseSaid(RoundMessage.ScanAnswered(null))
        if (answer != null) model.houseSaid(RoundMessage.SyncPulseAnswered(held = answer))
        repeat(taps) {
            now += 1_500
            model.actions().tapPulse()
        }
        return model
    }

    /**
     * **A run, a miss and the reset** (the owner's O3 and O4, D-298), step by step on one phone:
     * three pulses in a row ([RunStep.Three]); a fourth tap off the beat, which empties the pips and
     * sends the run ([RunStep.Missed]); the house's failed answer, which starts the 3 s lockout, with
     * OFF THE BEAT and nothing more ([RunStep.Reset]); a tap inside the lockout, which lights nothing
     * ([RunStep.Locked]); and, the 3 s gone, a tap on the beat that starts the count again
     * ([RunStep.Again]). And a run of two whose next pulse goes by untapped ([RunStep.Passed]).
     */
    private enum class RunStep { Three, Missed, Reset, Locked, Again, Passed }

    private fun through(step: RunStep): FlowModel {
        val pulseFrom = 86_402_000L
        var now = pulseFrom + 4 * 1_500L
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = PanelRole.Resident, houseAttached = true),
            houseNow = { now },
        )
        model.houseSaid(egressOpened())
        model.scanning(MarkerId("east-card"))
        model.houseSaid(RoundMessage.ScanAnswered(null))
        fun tap(at: Long) {
            now = at
            model.actions().tapPulse()
        }
        if (step == RunStep.Passed) {
            tap(pulseFrom + 4 * 1_500L)
            tap(pulseFrom + 5 * 1_500L)
            now = pulseFrom + 6 * 1_500L + 401
            model.secondPassed()
            return model
        }
        tap(pulseFrom + 4 * 1_500L)
        tap(pulseFrom + 5 * 1_500L + 120)
        tap(pulseFrom + 6 * 1_500L - 200)
        if (step == RunStep.Three) return model
        tap(pulseFrom + 6 * 1_500L + 750)
        if (step == RunStep.Missed) return model
        model.houseSaid(RoundMessage.SyncPulseAnswered(held = false))
        if (step == RunStep.Reset) return model
        tap(pulseFrom + 8 * 1_500L)
        if (step == RunStep.Locked) return model
        repeat(3) { model.secondPassed() }
        tap(pulseFrom + 10 * 1_500L)
        return model
    }

    /** The widget on a phone not at a node: no control, with or without a beat line from an earlier return. */
    private fun away(answer: Boolean? = null): FlowModel {
        val model = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true))
        model.houseSaid(egressOpened())
        if (answer != null) model.houseSaid(RoundMessage.SyncPulseAnswered(held = answer))
        model.push(ScreenId.EgressWidget)
        return model
    }

    @Test
    fun renderTheTapControlAfterTheNodeScan() {
        val states = listOf(
            "pulse-fresh" to { atANode(PanelRole.Resident) },
            "pulse-two-taps" to { atANode(PanelRole.Resident, taps = 2) },
            "pulse-on-the-beat" to { atANode(PanelRole.Resident, answer = true) },
            "pulse-off-the-beat" to { atANode(PanelRole.Resident, answer = false, taps = 1) },
            "pulse-fresh-insider" to { atANode(PanelRole.Insider) },
            "pulse-run-three" to { through(RunStep.Three) },
            "pulse-run-missed" to { through(RunStep.Missed) },
            "pulse-run-reset" to { through(RunStep.Reset) },
            "pulse-run-locked" to { through(RunStep.Locked) },
            "pulse-run-again" to { through(RunStep.Again) },
            "pulse-run-pulse-passed" to { through(RunStep.Passed) },
            "pulse-not-at-a-node" to { away() },
            "pulse-not-at-a-node-with-beat" to { away(answer = false) },
            "pulse-baseline-home" to { FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true)) },
        )
        val faults = mutableListOf<String>()
        val flat = mutableMapOf<String, BufferedImage>()
        for ((phone, insets) in phones) {
            for ((name, model) in states) {
                val file = if (phone == "flat") name else "$name-$phone"
                val (png, found) = render(file, model(), insets)
                if (phone == "flat") flat[name] = png
                faults += found
            }
        }
        faults.forEach { println("GRID  $it") }
        assertTrue(faults.isEmpty(), "the app grid is clipped under the Egress widget:\n  " + faults.joinToString("\n  "))
        assertTrue(same(flat.getValue("pulse-fresh"), flat.getValue("pulse-fresh-insider")), "an Insider's tap control is not a Resident's, pixel for pixel")
        assertTrue(!same(flat.getValue("pulse-fresh"), flat.getValue("pulse-not-at-a-node")), "the control drew nothing a phone away from a node does not")
    }

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }
}
