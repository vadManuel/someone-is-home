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
import home.someoneshome.model.CircuitStation
import home.someoneshome.model.Destination
import home.someoneshome.model.DiskInHand
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The work order as the house now deals it, drawn** (D-322): the mix with an ARRAY WIPE line and a
 * MEMORY DUMP line, two blocked lines (one of them another seat's dependency), the disk in hand on
 * the springboard widget and in Files, a scan refused while carrying, and `you're up` landing.
 *
 * Every picture is a housed phone driven only by the house's own messages, through [FlowHost] at
 * 600×1300 with an iPhone 16 Pro's insets, written to `build/screens/structured-<scene>-<role>.png`
 * to be looked at. What pixels can check, it checks: the words are there, and an Insider's phone and
 * a Resident's are the same pixels.
 */
@OptIn(ExperimentalTestApi::class)
class StructuredWorkRender {

    private val out = File("build/screens").apply { mkdirs() }
    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

    private fun at(card: String, shape: String, room: String) = Destination(MarkerId(card), shape, room)

    private val hall = at("card-hall", "diamond", "HALL")
    private val garage = at("card-garage", "arrow_right", "GARAGE")
    private val bed = at("card-bed2", "cross", "BED 2")
    private val study = at("card-study", "square_frame", "STUDY")
    private val kitchen = at("card-kitchen", "triangle_up", "KITCHEN")

    /** A K = 8 order in the mix's shape: 1 circuit, 1 long, 4 medium, 2 short, two of them held. */
    private val order: List<OrderLine> = listOf(
        OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Replay), done = true, at = listOf(hall)),
        OrderLine.Known(1, AssignedWork.Subroutine(SubroutineKind.Jam), done = false, at = listOf(garage)),
        OrderLine.Known(2, AssignedWork.ArrayWipe, done = false, at = listOf(study, bed, kitchen)),
        OrderLine.Known(3, AssignedWork.MemoryDump, done = false, at = listOf(bed, hall)),
        OrderLine.Blocked(4),
        OrderLine.Known(5, AssignedWork.Subroutine(SubroutineKind.Handshake), done = false, at = listOf(kitchen)),
        OrderLine.Known(6, AssignedWork.Subroutine(SubroutineKind.Short), done = false, at = listOf(study)),
        OrderLine.Blocked(7),
    )

    private fun housed(role: PanelRole): FlowModel = FlowModel(
        PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
        subroutines = SubroutineModel(),
    ).also { it.houseSaid(RoundMessage.WorkOrderIssued(order)) }

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

    /** One scene for one role: [drive] moves the phone, [expect] are words that must be on it. */
    private fun scene(role: PanelRole, expect: List<String>, screen: ScreenId, drive: (FlowModel) -> Unit): BufferedImage {
        var image: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            val model = housed(role)
            host(model)
            drive(model)
            mainClock.advanceTimeBy(500L)
            assertEquals(screen, model.state.screen, "$role: the phone is not on $screen")
            for (word in expect) {
                assertTrue(
                    onAllNodesWithText(word, substring = true).fetchSemanticsNodes().isNotEmpty(),
                    "$role: '$word' is not on $screen",
                )
            }
            image = onRoot().captureToImage().toAwtImage()
        }
        return image!!
    }

    private fun bothRoles(name: String, expect: List<String>, screen: ScreenId, drive: (FlowModel) -> Unit) {
        val resident = scene(PanelRole.Resident, expect, screen, drive)
        val insider = scene(PanelRole.Insider, expect, screen, drive)
        ImageIO.write(resident, "png", File(out, "structured-$name-resident.png"))
        ImageIO.write(insider, "png", File(out, "structured-$name-insider.png"))
        assertTrue(same(resident, insider), "$name is drawn differently on an Insider's phone and a Resident's")
    }

    /** Spares read: the house answers with nothing opened and DISK 01 in hand. */
    private fun tookADisk(phone: FlowModel) {
        phone.go(ScreenId.Scan)
        phone.scanning(study.marker)
        phone.houseSaid(RoundMessage.ScanAnswered(null, carrying = DiskInHand(1, CircuitStation.Rack)))
    }

    @Test
    fun renderTheOrderWithItsStructuresAndItsBlockedLines() =
        bothRoles("order", listOf("ARRAY WIPE", "MEMORY DUMP", "JAM", "STUDY"), ScreenId.Work) { it.go(ScreenId.Work) }

    @Test
    fun renderTheWidgetWithADiskInHand() =
        bothRoles("carrying", listOf("CARRYING", "DISK 01", "TO RACK", "NOTHING ELSE OPENS"), ScreenId.Home) { tookADisk(it) }

    @Test
    fun renderFilesWithTheDisk() =
        bothRoles("files", listOf("DISK 01", "RACK"), ScreenId.Files) {
            tookADisk(it)
            it.go(ScreenId.Files)
        }

    @Test
    fun renderAScanRefusedWhileCarrying() =
        bothRoles("refused", listOf("NOTHING FOR"), ScreenId.ScanBad) {
            tookADisk(it)
            it.go(ScreenId.Scan)
            it.scanning(garage.marker)
            it.houseSaid(RoundMessage.ScanAnswered(null, carrying = DiskInHand(1, CircuitStation.Rack)))
        }

    @Test
    fun renderTheDamagedDiskOnItsWayToDisposal() =
        bothRoles("disposal", listOf("CARRYING", "DISK 01", "TO DISPOSAL"), ScreenId.Home) {
            tookADisk(it)
            it.go(ScreenId.Scan)
            it.scanning(bed.marker)
            it.houseSaid(RoundMessage.ScanAnswered(null, carrying = DiskInHand(1, CircuitStation.Disposal)))
        }

    @Test
    fun renderYoureUpLanding() =
        bothRoles("unblocked", listOf("you're up", "NUMBER WITHHELD"), ScreenId.Quiet) {
            it.houseSaid(
                RoundMessage.WorkOrderIssued(
                    order.map { line ->
                        if (line.index == 4) OrderLine.Known(4, AssignedWork.Subroutine(SubroutineKind.Drift), false, listOf(garage)) else line
                    },
                ),
            )
            it.houseSaid(RoundMessage.WorkUnblocked)
        }
}
