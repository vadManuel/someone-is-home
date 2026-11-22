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
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MemoryPattern
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Memory Dump's screens and the Array Wipe's briefing, as shipped** (D-325): the owner's approved
 * draft (`abac9b9`) built into the real screens, drawn on a housed phone driven only by the house's
 * own messages, through [FlowHost] at the draft's 720×1520 with its camera band.
 *
 * Written to `build/screens/memory-dump/<n>-<scene>-<role>.png`, the draft's numbering, to be looked
 * at beside the draft's PNGs. Every scene is drawn for a Resident and an Insider and the two must be
 * the same pixels; the words each scene must say are asserted.
 */
@OptIn(ExperimentalTestApi::class)
class MemoryDumpRender {

    private val out = File("build/screens/memory-dump").apply { mkdirs() }
    private val insets = PanelInsets(top = 36.dp)

    private fun at(card: String, shape: String, room: String) = Destination(MarkerId(card), shape, room)

    // The draft's rooms: shown at BED 2, entered at HALL.
    private val hall = at("card-hall", "diamond", "HALL")
    private val garage = at("card-garage", "arrow_right", "GARAGE")
    private val bed = at("card-bed2", "cross", "BED 2")
    private val study = at("card-study", "square_frame", "STUDY")
    private val kitchen = at("card-kitchen", "triangle_up", "KITCHEN")

    /** The draft's pattern: four places in four. */
    private val pattern = listOf(2, 0, 3, 1)

    /** The draft's order (K = 8, two held lines), the Memory Dump at line 3, its pattern [held] or not. */
    private fun order(held: Boolean): List<OrderLine> = listOf(
        OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Replay), done = true, at = listOf(hall)),
        OrderLine.Known(1, AssignedWork.Subroutine(SubroutineKind.Jam), done = false, at = listOf(garage)),
        OrderLine.Known(2, AssignedWork.ArrayWipe, done = false, at = listOf(study, bed, kitchen)),
        OrderLine.Known(3, AssignedWork.MemoryDump, done = false, at = listOf(bed, hall), pattern = if (held) 1 else 0),
        OrderLine.Blocked(4),
        OrderLine.Known(5, AssignedWork.Subroutine(SubroutineKind.Handshake), done = false, at = listOf(kitchen)),
        OrderLine.Known(6, AssignedWork.Subroutine(SubroutineKind.Short), done = false, at = listOf(study)),
        OrderLine.Blocked(7),
    )

    private val sent = mutableListOf<Any>()

    private fun housed(role: PanelRole): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            subroutines = SubroutineModel(send = { sent += it }),
            send = { sent += it },
        )
        model.houseSaid(RoundMessage.WorkOrderIssued(order(held = false)))
        return model
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

    /** One scene for one role: [drive] moves the phone, then the clock runs [after] ms. */
    private fun scene(role: PanelRole, expect: List<String>, screen: ScreenId, after: Long, drive: (FlowModel) -> Unit): BufferedImage {
        var image: BufferedImage? = null
        runDesktopComposeUiTest(width = 720, height = 1520) {
            val model = housed(role)
            host(model)
            drive(model)
            mainClock.advanceTimeByFrame()
            var left = after
            while (left > 0) {
                val step = minOf(left, 16L)
                mainClock.advanceTimeBy(step)
                left -= step
            }
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

    private val identical = mutableListOf<String>()

    private fun bothRoles(name: String, expect: List<String>, screen: ScreenId, after: Long = 500L, drive: (FlowModel) -> Unit) {
        val resident = scene(PanelRole.Resident, expect, screen, after, drive)
        val insider = scene(PanelRole.Insider, expect, screen, after, drive)
        ImageIO.write(resident, "png", File(out, "$name-resident.png"))
        ImageIO.write(insider, "png", File(out, "$name-insider.png"))
        assertTrue(same(resident, insider), "$name is drawn differently on an Insider's phone and a Resident's")
        identical += name
    }

    // ---- The two scans, as the house answers them ---------------------------------------------

    private fun caughtTheSource(phone: FlowModel) {
        phone.go(ScreenId.Scan)
        phone.scanning(bed.marker)
        phone.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(3, SubroutineKind.MemoryDump, listOf(MemoryPattern.SOURCE) + pattern)))
    }

    private fun caughtTheTarget(phone: FlowModel) {
        phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
        phone.go(ScreenId.Scan)
        phone.scanning(hall.marker)
        phone.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(3, SubroutineKind.MemoryDump, listOf(MemoryPattern.TARGET, 4))))
    }

    private fun entered(phone: FlowModel, taps: List<Int>) {
        caughtTheTarget(phone)
        phone.beginSubroutine()
        for (t in taps) phone.tapSubroutine(Subroutine.MemoryDump, t)
    }

    /** Mid-way through the first pass: the second dot of the pattern is lit. */
    private val atSecond = REPLAY_LEAD_MILLIS + REPLAY_STEP_MILLIS + 100L

    @Test
    fun theScheduleIsReplaysPlayedTwice() {
        assertEquals(8_800L, memoryDumpShownMillis(pattern), "the source does not show the pattern for 8.8 s")
        assertNull(memoryDumpFlashing(pattern, 799L), "a dot lit in the 800 ms still")
        assertEquals(pattern[0], memoryDumpFlashing(pattern, 800L))
        assertNull(memoryDumpFlashing(pattern, 800L + 600L), "a dot is lit longer than 600 ms")
        assertEquals(pattern[1], memoryDumpFlashing(pattern, atSecond))
        assertEquals(pattern[1], memoryDumpFlashing(pattern, 4_400L + atSecond), "the second pass did not flash the same order")
        assertEquals(pattern[3], memoryDumpFlashing(pattern, 4_400L + 800L + 3 * 900L))
        assertNull(memoryDumpFlashing(pattern, 8_800L), "a dot lit after the playback")
    }

    @Test
    fun renderEveryScreenForBothRoles() {
        bothRoles("1-brief-source", listOf("CAUGHT", "BED 2", "SUBROUTINE 4 OF 8", "DOTS WILL FLASH IN ORDER.", "CARRY THE ORDER IN YOUR HEAD.", "BEGIN", "NOT THIS ONE"), ScreenId.ScanCaught) {
            caughtTheSource(it)
        }
        bothRoles("2-pattern", listOf("MEMORY DUMP", "STOP NOW"), ScreenId.SubMemory, after = atSecond) {
            caughtTheSource(it)
            it.beginSubroutine()
        }
        bothRoles("3-carry", listOf("MEMORY DUMP", "TO HALL", PROCEED), ScreenId.SubMemory, after = 9_000L) {
            caughtTheSource(it)
            it.beginSubroutine()
        }
        bothRoles("4-brief-target", listOf("HALL", "THE DOTS WILL NOT FLASH.", "TAP THEM BACK FROM MEMORY.", "BEGIN"), ScreenId.ScanCaught) {
            caughtTheTarget(it)
        }
        bothRoles("5-entry", listOf("MEMORY DUMP", "STOP NOW"), ScreenId.SubMemory) { entered(it, listOf(2, 0)) }
        bothRoles("6-returned", listOf("RETURNED . WAITING", "STOP NOW"), ScreenId.SubMemory) { entered(it, pattern) }
        bothRoles("7-accepted", listOf(HOUSE_WEAKER, PROCEED), ScreenId.SubMemory) {
            entered(it, pattern)
            it.houseSaid(RoundMessage.SubroutineGraded(accepted = true))
        }
        bothRoles("8-mismatch", listOf("REJECTED . BACK TO BED 2", PROCEED), ScreenId.SubMemory) {
            entered(it, listOf(2, 3, 0, 1))
            it.houseSaid(RoundMessage.SubroutineGraded(accepted = false))
        }
        bothRoles("9-order-row", listOf("MEMORY DUMP", "TO HALL", "LOCKED", "WAITING UPSTREAM"), ScreenId.Work) {
            it.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
            it.go(ScreenId.Work)
        }
        bothRoles("9b-order-row-before-source", listOf("MEMORY DUMP", "BED 2", "LOCKED", "WAITING UPSTREAM"), ScreenId.Work) {
            it.go(ScreenId.Work)
        }
        bothRoles("10-array-wipe-brief", listOf("STUDY", "SUBROUTINE 3 OF 8", "CARRY ONE DISK AT A TIME.", "NOTHING ELSE OPENS UNTIL IT IS DELIVERED.", "BEGIN", "NOT THIS ONE"), ScreenId.ScanCaught) {
            it.go(ScreenId.Scan)
            it.scanning(study.marker)
            it.houseSaid(RoundMessage.ScanAnswered(null, circuit = true))
        }
        println("MEMORY DUMP (D-325): pixel-identical for both roles: ${identical.joinToString()}")
    }
}
