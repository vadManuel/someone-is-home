package home.someoneshome.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
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
 * **NOT A MARKER in a round: the house's answer to paper this home never registered, drawn**
 * (D-124, the owner's 2026-11-21 ruling, D-318).
 *
 * A housed phone of each role has work open (a Jam caught at the STUDY card), leaves the caught scan
 * by NOT THIS ONE, taps SCAN again and reads a card the home never registered. The house's
 * `CardUnregistered` arrives and the phone moves to the screen it already had for this,
 * [ScreenId.ScanUnknown]. Driven through [FlowHost] at 600×1300 with an iPhone 16 Pro's insets, and
 * written to `build/screens/notamarker-in-round-<role>.png` to be looked at.
 *
 * What pixels can check, it checks: the words are NOT A MARKER and not NOTHING FOR YOU HERE; an
 * Insider's phone and a Resident's are the same pixels; and the work the phone had caught is still
 * the work it holds, because the house spent nothing.
 */
@OptIn(ExperimentalTestApi::class)
class NotAMarkerRender {

    private val out = File("build/screens").apply { mkdirs() }
    private val study = MarkerId("card-study")
    private val stray = MarkerId("ZZZZ001")

    /** An iPhone 16 Pro's measured insets (`DeviceLayoutTest`). */
    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

    private val caught = SubroutineInstance(0, SubroutineKind.Jam, SubroutineModel.benchParameters(SubroutineKind.Jam))

    /** A housed phone of [role], its Jam caught at [study], then back on the scan for another card. */
    private fun scanningAgain(role: PanelRole): FlowModel {
        val phone = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            subroutines = SubroutineModel(),
        )
        phone.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(
                    OrderLine.Known(
                        index = 0,
                        work = AssignedWork.Subroutine(SubroutineKind.Jam),
                        done = false,
                        at = listOf(Destination(study, shape = "diamond", room = "STUDY")),
                    ),
                ),
            ),
        )
        phone.go(ScreenId.Scan)
        phone.scanning(study)
        phone.houseSaid(RoundMessage.ScanAnswered(caught))
        assertEquals(ScreenId.ScanCaught, phone.state.screen, "$role: the Jam was not caught")
        phone.go(ScreenId.Work)
        phone.go(ScreenId.Scan)
        assertEquals(ScreenId.Scan, phone.state.screen, "$role: the phone is not on the scan")
        return phone
    }

    private fun DesktopComposeUiTest.host(model: FlowModel) {
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
        mainClock.advanceTimeByFrame()
    }

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    /** The phone reads [stray], the house says NOT A MARKER, and the screen it lands on is photographed. */
    private fun told(role: PanelRole): BufferedImage {
        var image: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            val model = scanningAgain(role)
            val held = model.state.current
            host(model)
            mainClock.advanceTimeBy(1_000L)
            model.scanning(stray)
            model.houseSaid(RoundMessage.CardUnregistered)
            mainClock.advanceTimeByFrame()
            assertEquals(ScreenId.ScanUnknown, model.state.screen, "$role: NOT A MARKER did not land on its screen")
            assertEquals(held, model.state.current, "$role: NOT A MARKER took away the work the phone had caught")
            onAllNodesWithText("NOT A MARKER").assertCountEquals(1)
            onAllNodesWithText("NOTHING FOR\nYOU HERE").assertCountEquals(0)
            image = onRoot().captureToImage().toAwtImage()
        }
        return image!!
    }

    @Test
    fun renderNotAMarkerInARoundForBothRoles() {
        val resident = told(PanelRole.Resident)
        val insider = told(PanelRole.Insider)
        ImageIO.write(resident, "png", File(out, "notamarker-in-round-resident.png"))
        ImageIO.write(insider, "png", File(out, "notamarker-in-round-insider.png"))
        assertTrue(same(resident, insider), "NOT A MARKER is drawn differently on an Insider's phone and a Resident's")
    }
}
