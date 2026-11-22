package home.someoneshome.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.toView
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **`you're up` drawn over a Subroutine and over the Terminal, for both roles** (D-327).
 *
 * Driven through [FlowHost], as a phone draws it. Writes `d327-youre-up-over-<screen>-<role>.png` to
 * `build/screens` to be looked at, and checks what pixels and the tree can: the banner is drawn, the
 * screen under it is still the work, nothing was sent, and a Resident's frame is an Insider's.
 */
@OptIn(ExperimentalTestApi::class)
class YoureUpOverWorkRender {

    private val out = File("build/screens").apply { mkdirs() }
    private val study = MarkerId("card-study")

    /** An iPhone 16 Pro's measured insets (`DeviceLayoutTest`). */
    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

    private val sent = mutableListOf<IntentMessage>()

    private fun phone(role: PanelRole): FlowModel = FlowModel(
        PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
        send = { sent += it },
        subroutines = SubroutineModel(send = { sent += it }),
    )

    private fun atJam(role: PanelRole): FlowModel {
        val model = phone(role)
        val kind = SubroutineKind.Jam
        model.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(OrderLine.Known(0, AssignedWork.Subroutine(kind), false, listOf(Destination(study, shape = "diamond", room = "STUDY"))), OrderLine.Blocked(1)),
            ),
        )
        model.scanning(study)
        model.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(0, kind, SubroutineModel.benchParameters(kind))))
        model.actions().beginSubroutine()
        return model
    }

    private fun atTerminal(role: PanelRole): FlowModel = phone(role).also {
        it.houseSaid(RoundMessage.PlanIssued(SavedHomesModel.sample().homes[1].plan.toView()))
        it.houseSaid(RoundMessage.TerminalAnswered(reading = true))
    }

    private fun DesktopComposeUiTest.drawn(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun shoot(name: String, model: FlowModel, screen: ScreenId): BufferedImage {
        var png: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            mainClock.autoAdvance = false
            setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
            mainClock.advanceTimeByFrame()
            assertEquals(screen, model.state.screen, "$name: the arrangement is wrong")
            sent.clear()
            model.houseSaid(RoundMessage.WorkUnblocked(Haptic.Short))
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(500)
            assertEquals(screen, model.state.screen, "$name: `you're up` walked the phone off the screen")
            assertEquals(emptyList(), sent, "$name: `you're up` sent something up")
            assertTrue(drawn("you're up"), "$name: no banner drawn")
            assertTrue(drawn("NUMBER WITHHELD"), "$name: the banner's sender is not drawn")
            png = onRoot().captureToImage().toAwtImage()
        }
        ImageIO.write(png!!, "png", File(out, "$name.png"))
        return png!!
    }

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    @Test
    fun youreUpOverASubroutineForBothRoles() {
        val resident = shoot("d327-youre-up-over-jam-resident", atJam(PanelRole.Resident), ScreenId.SubJam)
        val insider = shoot("d327-youre-up-over-jam-insider", atJam(PanelRole.Insider), ScreenId.SubJam)
        assertTrue(same(resident, insider), "`you're up` over Jam differs by role")
    }

    @Test
    fun youreUpOverTheTerminalForBothRoles() {
        val resident = shoot("d327-youre-up-over-terminal-resident", atTerminal(PanelRole.Resident), ScreenId.TermLive)
        val insider = shoot("d327-youre-up-over-terminal-insider", atTerminal(PanelRole.Insider), ScreenId.TermLive)
        assertTrue(same(resident, insider), "`you're up` over the Terminal differs by role")
    }
}
