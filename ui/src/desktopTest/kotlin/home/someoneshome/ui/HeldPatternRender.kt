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
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MemoryPattern
import home.someoneshome.model.OrderLine
import home.someoneshome.model.RoomKind
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The owner's 2026-11-23 rulings on the phone (D-330)**: the strip's padlock shows while a Memory
 * Dump pattern is held, as it does for a disk, by one rule for anything carried, and clears on the
 * hand-over; the source re-scanned while the pattern is held is the source's briefing again, and
 * BEGIN plays the fresh pattern; and the stairs warning's hold reads UNREGISTER AND PROCEED.
 *
 * Driven through [FlowHost] on a housed phone fed only house messages, at the mockup's 720×1520 with
 * its 36 px camera band, written to `build/screens/d330/<scene>-<role>.png` to be looked at. Every
 * scene is drawn for a Resident and an Insider and the two must be the same pixels.
 */
@OptIn(ExperimentalTestApi::class)
class HeldPatternRender {

    private val out = File("build/screens/d330").apply { mkdirs() }
    private val insets = PanelInsets(top = 36.dp)

    private fun at(card: String, shape: String, room: String) = Destination(MarkerId(card), shape, room)

    // ProceedRender's rooms: the pattern shown at BED 2 and entered at HALL.
    private val hall = at("card-hall", "diamond", "HALL")
    private val garage = at("card-garage", "arrow_right", "GARAGE")
    private val bed = at("card-bed2", "cross", "BED 2")
    private val study = at("card-study", "square_frame", "STUDY")
    private val kitchen = at("card-kitchen", "triangle_up", "KITCHEN")

    private fun order(held: Boolean, done: Boolean = false): List<OrderLine> = listOf(
        OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Replay), done = true, at = listOf(hall)),
        OrderLine.Known(1, AssignedWork.Subroutine(SubroutineKind.Jam), done = false, at = listOf(garage)),
        OrderLine.Known(2, AssignedWork.ArrayWipe, done = false, at = listOf(study, bed, kitchen)),
        OrderLine.Known(3, AssignedWork.MemoryDump, done = done, at = listOf(bed, hall), pattern = if (held) 1 else 0),
        OrderLine.Blocked(4),
        OrderLine.Known(5, AssignedWork.Subroutine(SubroutineKind.Handshake), done = false, at = listOf(kitchen)),
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

    private fun DesktopComposeUiTest.drawn(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    /** The camera band and the status strip: 36 and 34 design units, doubled at 720 wide. */
    private fun strip(image: BufferedImage): BufferedImage = image.getSubimage(0, 0, image.width, (36 + 34 + 1) * 2)

    private class Frame(val image: BufferedImage, val lock: Boolean)

    private fun scene(
        role: PanelRole,
        screen: ScreenId,
        says: List<String>,
        never: List<String>,
        after: Long,
        drive: (FlowModel) -> Unit,
    ): Frame {
        var frame: Frame? = null
        val model = housed(role)
        runDesktopComposeUiTest(width = 720, height = 1520) {
            mainClock.autoAdvance = false
            setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
            mainClock.advanceTimeByFrame()
            drive(model)
            mainClock.advanceTimeByFrame()
            var left = after
            while (left > 0) {
                val step = minOf(left, 16L)
                mainClock.advanceTimeBy(step)
                left -= step
            }
            assertEquals(screen, model.state.screen, "$role: the phone is not on $screen")
            for (word in says) assertTrue(drawn(word), "$role: '$word' is not on $screen")
            for (word in never) assertTrue(!drawn(word), "$role: '$word' is on $screen")
            frame = Frame(onRoot().captureToImage().toAwtImage(), PanelVals(model.state).lockChip)
        }
        return frame!!
    }

    private val identical = mutableListOf<String>()

    /** One scene for both roles, written out, the same pixels and the same padlock on both. */
    private fun bothRoles(
        name: String,
        screen: ScreenId,
        says: List<String>,
        never: List<String> = emptyList(),
        after: Long = 500L,
        drive: (FlowModel) -> Unit,
    ): Frame {
        val resident = scene(PanelRole.Resident, screen, says, never, after, drive)
        val insider = scene(PanelRole.Insider, screen, says, never, after, drive)
        ImageIO.write(resident.image, "png", File(out, "$name-resident.png"))
        ImageIO.write(insider.image, "png", File(out, "$name-insider.png"))
        assertTrue(same(resident.image, insider.image), "$name is drawn differently on an Insider's phone and a Resident's")
        assertEquals(resident.lock, insider.lock, "$name: the padlock follows the role")
        identical += name
        return resident
    }

    // ---- The padlock: anything carried, one rule ----------------------------------------------

    @Test
    fun thePadlockShowsWhileAPatternIsHeldAsForADiskAndClearsOnTheHandOver() {
        // Nothing carried: no padlock.
        val none = bothRoles("strip-0-nothing-carried", ScreenId.Work, says = listOf("MEMORY DUMP", "BED 2")) { it.go(ScreenId.Work) }
        assertFalse(none.lock, "the padlock shows with nothing carried")

        // A pattern held (BEGIN's answer, the row TO HALL): the padlock, and Files still empty.
        val held = bothRoles("strip-1-pattern-held", ScreenId.Work, says = listOf("MEMORY DUMP", "TO HALL")) { phone ->
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
            phone.go(ScreenId.Work)
        }
        assertTrue(held.lock, "the padlock does not show while a pattern is held")

        // The same on page 1, where the strip is the same strip.
        val home = bothRoles("strip-2-pattern-held-page-1", ScreenId.Home, says = emptyList()) { phone ->
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
        }
        assertTrue(home.lock, "the padlock does not show on page 1 while a pattern is held")

        // A disk in hand, for the comparison: the same icon, ink and place.
        val disk = bothRoles("strip-3-disk-in-hand", ScreenId.Work, says = listOf("ARRAY WIPE")) { phone ->
            phone.houseSaid(RoundMessage.ScanAnswered(null, carrying = DiskInHand(1, CircuitStation.Rack)))
            phone.go(ScreenId.Work)
        }
        assertTrue(disk.lock, "the padlock does not show for a disk")
        assertTrue(same(strip(held.image), strip(disk.image)), "the held pattern's strip is not the disk's: a different icon, ink or place")
        assertFalse(same(strip(held.image), strip(none.image)), "the padlock is in the model and not on the strip")

        // Handed over at the target: the house lets the pattern go and the padlock goes with it,
        // accepted or not.
        for ((verdict, accepted) in listOf("accepted" to true, "rejected" to false)) {
            val after = bothRoles("strip-4-handed-over-$verdict", ScreenId.Work, says = listOf("MEMORY DUMP")) { phone ->
                phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
                phone.houseSaid(RoundMessage.SubroutineGraded(accepted = accepted))
                phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = false, done = accepted)))
                phone.go(ScreenId.Work)
            }
            assertFalse(after.lock, "the padlock outlived the hand-over ($verdict)")
            assertTrue(same(strip(after.image), strip(none.image)), "the strip after the hand-over ($verdict) is not the empty-handed strip")
        }
        // Files never shows the pattern: the padlock is the strip's, not Files' contents.
        bothRoles("strip-5-files-while-held", ScreenId.Files, says = emptyList(), never = listOf("PATTERN")) { phone ->
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
            phone.go(ScreenId.Files)
        }
        println("padlock (D-330): pixel-identical for both roles: ${identical.joinToString()}")
    }

    // ---- The source again, holding the pattern: the briefing, then a fresh pattern ------------------

    @Test
    fun theSourceReScannedWhileHeldIsTheBriefingAgainAndBeginPlaysTheFreshPattern() {
        val fresh = listOf(1, 3, 0, 2)
        fun rescanned(phone: FlowModel) {
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
            phone.go(ScreenId.Scan)
            phone.scanning(bed.marker)
            // The house's answer: the source's fresh pattern, and the order with nothing held.
            phone.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(3, SubroutineKind.MemoryDump, listOf(MemoryPattern.SOURCE) + fresh)))
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = false)))
        }
        val caught = bothRoles(
            "source-1-rescanned-briefing", ScreenId.ScanCaught,
            says = listOf("CAUGHT", "BED 2", "DOTS WILL FLASH IN ORDER", "BEGIN", "NOT THIS ONE"),
            never = listOf("CARRYING", "GO TO HALL", "NOTHING FOR"),
        ) { rescanned(it) }
        assertFalse(caught.lock, "the padlock outlived the pattern the re-scan let go")

        sent.clear()
        val playing = bothRoles("source-2-fresh-pattern-playing", ScreenId.SubMemory, says = listOf("MEMORY DUMP", "STOP NOW"), after = 2_000L) { phone ->
            rescanned(phone)
            phone.beginSubroutine()
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
        }
        assertTrue(sent.count { it == IntentMessage.BeginCarry } == 2, "BEGIN on the re-scanned source did not ask for the fresh pattern, once per role: $sent")
        assertTrue(playing.lock, "the padlock does not show for the fresh pattern")

        // Another card while the fresh one is held: still CARRYING . GO TO HALL (D-328).
        bothRoles("source-3-other-card-carrying", ScreenId.ScanBad, says = listOf("CARRYING", "GO TO HALL"), never = listOf("NOTHING FOR")) { phone ->
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
            phone.go(ScreenId.Scan)
            phone.scanning(garage.marker)
            phone.houseSaid(RoundMessage.ScanAnswered(null, Haptic.Double))
        }
    }

    // ---- The stairs warning: UNREGISTER AND PROCEED --------------------------------------------

    @Test
    fun theStairsWarningsHoldReadsUnregisterAndProceed() {
        val frames = PanelRole.entries.map { role ->
            var image: BufferedImage? = null
            val model = FlowModel(PanelState(screen = ScreenId.Home, role = role)).also { it.push(ScreenId.RoomEdit) }
            model.editor.open("GARAGE")
            model.pickRoomType(RoomKind.Stairs)
            assertEquals(ScreenId.StairsWarn, model.state.screen, "the warning did not come up")
            runDesktopComposeUiTest(width = 720, height = 1520) {
                setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
                waitForIdle()
                for (word in listOf("BECOMES STAIRS", "MOVE THEM FIRST", "UNREGISTER AND PROCEED")) {
                    assertTrue(drawn(word), "($role) '$word' is not on the stairs warning")
                }
                assertTrue(!drawn("CONTINUE"), "($role) CONTINUE is still on the stairs warning")
                image = onRoot().captureToImage().toAwtImage()
            }
            ImageIO.write(image!!, "png", File(out, "stairs-warning-${role.name.lowercase()}.png"))
            image!!
        }
        assertTrue(same(frames[0], frames[1]), "the stairs warning differs by role")
    }
}
