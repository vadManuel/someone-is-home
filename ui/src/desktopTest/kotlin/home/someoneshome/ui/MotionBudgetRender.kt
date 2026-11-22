package home.someoneshome.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-321, rendered: the motion budget row full, part spent and nearly gone, on a Subroutine and at
 * the station, and black on Sniff before its answer.**
 *
 * Driven through [FlowHost] on a housed phone at 600×1300 with an iPhone 16 Pro's insets; the bar
 * is moved by real samples through [FlowModel.motionSampled], the sensor's own sink. Writes
 * `d321-<screen>-<state>-<role>.png` to `build/screens`, and checks what pixels can: the row's lower
 * hairline is lit no brighter than the screen's word ink where the budget is left and no brighter
 * than its hairline ink where it is spent, black on a black screen,
 * HOLDING never changes, and an Insider's phone is the Resident's pixels at every step.
 */
@OptIn(ExperimentalTestApi::class)
class MotionBudgetRender {

    private val out = File("build/screens").apply { mkdirs() }

    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

    private val study = MarkerId("card-study")

    private fun DesktopComposeUiTest.shot(): BufferedImage = onRoot().captureToImage().toAwtImage()

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    private fun open(model: FlowModel, kind: SubroutineKind) {
        model.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(OrderLine.Known(0, AssignedWork.Subroutine(kind), false, listOf(Destination(study, "diamond", "STUDY")))),
            ),
        )
        model.scanning(study)
        model.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(0, kind, SubroutineModel.benchParameters(kind))))
        model.actions().beginSubroutine()
    }

    /** What one screen looked like at each step, and the row's lower hairline sampled at each. */
    private class Steps(val shots: List<BufferedImage>, val bars: List<Float>, val line: List<Pair<Int, Int>>, val holding: List<Boolean>)

    /**
     * A housed phone of [role] put on its screen by [place], then: full, after calibration and a
     * step of walking, and walked to the edge of the allowance.
     */
    private fun steps(role: PanelRole, name: String, place: (FlowModel) -> Unit): Steps {
        val shots = mutableListOf<BufferedImage>()
        val bars = mutableListOf<Float>()
        val line = mutableListOf<Pair<Int, Int>>()
        val holding = mutableListOf<Boolean>()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            mainClock.autoAdvance = false
            val model = FlowModel(
                PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
                subroutines = SubroutineModel(send = {}),
                send = {},
            )
            setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
            place(model)
            val screen = model.state.screen
            fun take(state: String) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
                assertEquals(screen, model.state.screen, "$role $name $state: the phone left its screen")
                val image = shot()
                ImageIO.write(image, "png", File(out, "d321-$name-$state-${role.name.lowercase()}.png"))
                shots += image
                bars += model.motionBar.left.floatValue
                val row = onNodeWithTag(MOTION_BUDGET_ROW).fetchSemanticsNode().boundsInRoot
                // The 1-unit hairline is 600 / 360 pixels tall and lands on fractional pixels, so it is
                // read as the reddest pixel in a short column across it: its ink where it covers a
                // pixel row whole, a blend with the ground where it does not.
                fun reddest(fx: Float): Int {
                    val x = (row.left + row.width * fx).toInt()
                    return (row.bottom.toInt() - 4..row.bottom.toInt() + 1).maxOf { (image.getRGB(x, it) shr 16) and 0xff }
                }
                line += reddest(0.1f) to reddest(0.9f)
                holding += onAllNodes(hasText("HOLDING")).fetchSemanticsNodes().size == 1
            }
            take("full")
            repeat(MotionBudget.CALIBRATION_SAMPLES) { model.motionSampled(0.004, 0.006, 0.003) }
            // Walk until about half is left, then until about a tenth is.
            var n = 0
            while (model.motion.left > 0.5 && n++ < 5_000) model.motionSampled(0.12, 0.15, 0.05)
            take("half")
            n = 0
            while (model.motion.left > 0.08 && n++ < 5_000) model.motionSampled(0.12, 0.15, 0.05)
            take("edge")
        }
        return Steps(shots, bars, line, holding)
    }

    /**
     * Full: lit at both ends. Half: lit at the start, spent at the end. Edge: spent at both. Lit is
     * brighter than the hairline ink could draw and no brighter than the word ink; spent is no
     * brighter than the hairline ink.
     */
    private fun checkRow(name: String, s: Steps, dim: Int, faint: Int) {
        assertEquals(1f, s.bars[0], "$name: the bar did not open full")
        assertTrue(s.bars[1] in 0.4f..0.6f, "$name: the half step drew ${s.bars[1]}")
        assertTrue(s.bars[2] in 0.01f..0.15f, "$name: the edge step drew ${s.bars[2]}")
        val dimRed = (dim shr 16) and 0xff
        val faintRed = (faint shr 16) and 0xff
        fun lit(r: Int) = r in faintRed + 10..dimRed
        fun spent(r: Int) = r <= faintRed
        assertTrue(lit(s.line[0].first) && lit(s.line[0].second), "$name full: not lit end to end ${s.line[0]} (dim $dimRed, faint $faintRed)")
        assertTrue(lit(s.line[1].first) && spent(s.line[1].second), "$name half: not lit then spent ${s.line[1]}")
        assertTrue(spent(s.line[2].first) && spent(s.line[2].second), "$name edge: not spent past a tenth ${s.line[2]}")
        assertTrue(s.holding.all { it }, "$name: HOLDING went from the row")
    }

    @Test
    fun theRowOnASubroutineDrainsAsTheBudgetDoes() {
        val shots = PanelRole.entries.map { role -> steps(role, "jam", { open(it, SubroutineKind.Jam) }) }
        // Jam is a medium screen: its words and its hairlines are the medium palette's.
        checkRow("jam", shots[0], Palette.MEDIUM.dim.toArgb(), Palette.MEDIUM.faint.toArgb())
        for (i in shots[0].shots.indices) {
            assertTrue(same(shots[0].shots[i], shots[1].shots[i]), "jam step $i: an Insider's phone is not a Resident's pixels")
        }
    }

    @Test
    fun theRowAtTheStationDrainsAsTheBudgetDoes() {
        val shots = PanelRole.entries.map { role ->
            steps(role, "terminal", { it.houseSaid(RoundMessage.TerminalAnswered(reading = true)) })
        }
        checkRow("terminal", shots[0], Palette.DARK.dim.toArgb(), Palette.DARK.faint.toArgb())
        for (i in shots[0].shots.indices) {
            assertTrue(same(shots[0].shots[i], shots[1].shots[i]), "terminal step $i: an Insider's phone is not a Resident's pixels")
        }
    }

    @Test
    fun theRowOnAnUnansweredSniffIsBlack() {
        val s = steps(PanelRole.Resident, "sniff", { open(it, SubroutineKind.Sniff) })
        for (i in s.line.indices) assertEquals(0 to 0, s.line[i], "sniff step $i: the bar emitted light on a screen that emits none")
    }
}
