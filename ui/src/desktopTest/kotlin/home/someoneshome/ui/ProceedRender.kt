package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.CircuitStation
import home.someoneshome.model.Destination
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.DiskInHand
import home.someoneshome.model.Grant
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MemoryPattern
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The owner's 2026-11-23 rulings on the phone (D-328)**: the Subroutine's foot reads STOP NOW
 * until the house answers and PROCEED after either verdict, for both roles; Memory Dump's TO <ROOM>
 * reads PROCEED and goes to the work order; a card refused while carrying reads CARRYING . GO TO
 * <ROOM>, for a held pattern and for a disk; PERMISSIONS and PERIMETER ARMED read PROCEED; and the
 * work order of a chained and an unchained player.
 *
 * Driven through [FlowHost] on a housed phone fed only house messages, at the mockup's 720×1520 with
 * its 36 px camera band, written to `build/screens/d328/<scene>-<role>.png` to be looked at. Every
 * scene is drawn for a Resident and an Insider and the two must be the same pixels; the words each
 * scene must say, and must not, are asserted.
 */
@OptIn(ExperimentalTestApi::class)
class ProceedRender {

    private val out = File("build/screens/d328").apply { mkdirs() }
    private val insets = PanelInsets(top = 36.dp)

    private fun at(card: String, shape: String, room: String) = Destination(MarkerId(card), shape, room)

    // MemoryDumpRender's rooms: the pattern shown at BED 2 and entered at HALL; the circuit's
    // Spares in STUDY, its Rack in BED 2 and its Disposal in the KITCHEN.
    private val hall = at("card-hall", "diamond", "HALL")
    private val garage = at("card-garage", "arrow_right", "GARAGE")
    private val bed = at("card-bed2", "cross", "BED 2")
    private val study = at("card-study", "square_frame", "STUDY")
    private val kitchen = at("card-kitchen", "triangle_up", "KITCHEN")

    private val pattern = listOf(2, 0, 3, 1)

    /** The draft's order, the Memory Dump at line 3, its pattern [held] or not, [chained] or not. */
    private fun order(held: Boolean, chained: Boolean = true): List<OrderLine> = listOf(
        OrderLine.Known(0, AssignedWork.Subroutine(SubroutineKind.Replay), done = true, at = listOf(hall)),
        OrderLine.Known(1, AssignedWork.Subroutine(SubroutineKind.Jam), done = false, at = listOf(garage)),
        OrderLine.Known(2, AssignedWork.ArrayWipe, done = false, at = listOf(study, bed, kitchen)),
        OrderLine.Known(3, AssignedWork.MemoryDump, done = false, at = listOf(bed, hall), pattern = if (held) 1 else 0),
        if (chained) OrderLine.Blocked(4) else OrderLine.Known(4, AssignedWork.Subroutine(SubroutineKind.Drift), done = false, at = listOf(hall)),
        OrderLine.Known(5, AssignedWork.Subroutine(SubroutineKind.Handshake), done = false, at = listOf(kitchen)),
        OrderLine.Known(6, AssignedWork.Subroutine(SubroutineKind.Short), done = false, at = listOf(study)),
        OrderLine.Known(7, AssignedWork.Subroutine(SubroutineKind.Sniff), done = false, at = listOf(garage)),
    )

    private val sent = mutableListOf<Any>()

    private fun housed(role: PanelRole, chained: Boolean = true): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            subroutines = SubroutineModel(send = { sent += it }),
            send = { sent += it },
        )
        model.houseSaid(RoundMessage.WorkOrderIssued(order(held = false, chained = chained)))
        return model
    }

    private fun DesktopComposeUiTest.drawn(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    /**
     * One scene for one role, through [FlowHost]: [drive] moves the phone, the clock runs [after]
     * ms, the words are checked, and [then] may tap the drawn screen after the picture is taken.
     */
    private fun scene(
        role: PanelRole,
        screen: ScreenId,
        says: List<String>,
        never: List<String>,
        after: Long,
        model: FlowModel,
        drive: (FlowModel) -> Unit,
        then: (DesktopComposeUiTest.(FlowModel) -> Unit)?,
    ): BufferedImage {
        var image: BufferedImage? = null
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
            image = onRoot().captureToImage().toAwtImage()
            then?.invoke(this, model)
        }
        return image!!
    }

    private val identical = mutableListOf<String>()

    private fun bothRoles(
        name: String,
        screen: ScreenId,
        says: List<String>,
        never: List<String> = emptyList(),
        after: Long = 500L,
        chained: Boolean = true,
        then: (DesktopComposeUiTest.(FlowModel) -> Unit)? = null,
        drive: (FlowModel) -> Unit,
    ) {
        val resident = scene(PanelRole.Resident, screen, says, never, after, housed(PanelRole.Resident, chained), drive, then)
        val insider = scene(PanelRole.Insider, screen, says, never, after, housed(PanelRole.Insider, chained), drive, then)
        ImageIO.write(resident, "png", File(out, "$name-resident.png"))
        ImageIO.write(insider, "png", File(out, "$name-insider.png"))
        assertTrue(same(resident, insider), "$name is drawn differently on an Insider's phone and a Resident's")
        identical += name
    }

    // ---- A Subroutine, before and after its verdict ---------------------------------------------

    private fun atJam(phone: FlowModel) {
        phone.go(ScreenId.Scan)
        phone.scanning(garage.marker)
        phone.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(1, SubroutineKind.Jam, SubroutineModel.benchParameters(SubroutineKind.Jam))))
        phone.beginSubroutine()
    }

    /** PROCEED, tapped on the drawn screen: the work order, and the window reported shut. */
    private val proceedToTheOrder: DesktopComposeUiTest.(FlowModel) -> Unit = { phone ->
        sent.clear()
        assertEquals(1, onAllNodes(hasClickAction() and hasText(PROCEED)).fetchSemanticsNodes().size, "PROCEED is not one tap")
        onAllNodes(hasClickAction() and hasText(PROCEED))[0].performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(ScreenId.Work, phone.state.screen, "PROCEED did not go to the work order")
        assertEquals(listOf<Any>(IntentMessage.StopSubroutine), sent, "PROCEED did not report the window shut, and only that")
    }

    @Test
    fun aSubroutineSaysStopNowUntilTheHouseAnswersAndProceedAfterEitherVerdict() {
        bothRoles("jam-1-working", ScreenId.SubJam, says = listOf("JAM", "STOP NOW"), never = listOf(PROCEED)) { atJam(it) }
        bothRoles("jam-2-returned", ScreenId.SubJam, says = listOf("RETURNED . WAITING", "STOP NOW"), never = listOf(PROCEED)) {
            atJam(it)
            it.handOverSubroutine(Subroutine.Jam)
        }
        bothRoles("jam-3-accepted", ScreenId.SubJam, says = listOf(HOUSE_WEAKER, PROCEED), never = listOf("STOP NOW"), then = proceedToTheOrder) {
            atJam(it)
            it.handOverSubroutine(Subroutine.Jam)
            it.houseSaid(RoundMessage.SubroutineGraded(accepted = true))
        }
        bothRoles("jam-4-rejected", ScreenId.SubJam, says = listOf(RESCAN, PROCEED), never = listOf("STOP NOW"), then = proceedToTheOrder) {
            atJam(it)
            it.handOverSubroutine(Subroutine.Jam)
            it.houseSaid(RoundMessage.SubroutineGraded(accepted = false))
        }
        println("PROCEED (D-328): pixel-identical for both roles: ${identical.joinToString()}")
    }

    /**
     * **Every Subroutine's foot, in every state, for both roles** (D-328): STOP NOW working and
     * waiting, PROCEED after ACCEPTED and after REJECTED, never both, and the two roles the same
     * pixels. Through [Screen] with the house's verdict, as `SubroutineParityTest` draws them.
     */
    @Test
    fun everySubroutineSaysProceedAfterEitherVerdictAndStopNowBeforeForBothRoles() {
        val wrong = mutableListOf<String>()
        for (subroutine in Subroutine.built) {
            for ((state, verdict) in listOf("working" to null, "returned" to null, "accepted" to SubroutineVerdict.Accepted, "rejected" to SubroutineVerdict.Rejected)) {
                val frames = PanelRole.entries.map { role ->
                    var image: BufferedImage? = null
                    runDesktopComposeUiTest(width = 300, height = 650) {
                        val model = SubroutineModel()
                        if (state != "working") model.handOver(subroutine)
                        setContent {
                            Box(Modifier.fillMaxSize()) {
                                DeviceCanvas(insets = PanelInsets()) {
                                    Screen(PanelState(screen = subroutine.screen!!, role = role, verdict = verdict), subroutines = model)
                                }
                            }
                        }
                        val expected = if (verdict == null) "STOP NOW" else PROCEED
                        val other = if (verdict == null) PROCEED else "STOP NOW"
                        if (onAllNodes(hasText(expected)).fetchSemanticsNodes().size != 1) wrong += "${subroutine.label} $state ($role): no $expected"
                        if (onAllNodes(hasText(other)).fetchSemanticsNodes().isNotEmpty()) wrong += "${subroutine.label} $state ($role): $other is drawn"
                        image = onRoot().captureToImage().toAwtImage()
                    }
                    image!!
                }
                if (!same(frames[0], frames[1])) wrong += "${subroutine.label} $state differs by role"
            }
        }
        assertTrue(Subroutine.built.size >= 11, "the sweep reached ${Subroutine.built.size} Subroutines")
        assertEquals(emptyList(), wrong)
    }

    // ---- Memory Dump: TO HALL, with PROCEED, to the work order -----------------------------------

    private fun caughtTheSource(phone: FlowModel) {
        phone.go(ScreenId.Scan)
        phone.scanning(bed.marker)
        phone.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(3, SubroutineKind.MemoryDump, listOf(MemoryPattern.SOURCE) + pattern)))
        phone.beginSubroutine()
        // The house's answer to BEGIN: the order, the row now TO HALL (D-325).
        phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
    }

    @Test
    fun theToRoomScreenSaysProceedAndGoesToTheWorkOrder() {
        // While the pattern plays it is STOP NOW: the house has not finished saying anything yet.
        bothRoles("memory-dump-1-playing", ScreenId.SubMemory, says = listOf("MEMORY DUMP", "STOP NOW"), never = listOf(PROCEED, "TO HALL"), after = 2_000L) {
            caughtTheSource(it)
        }
        // After it, TO HALL counts as after the verdict (D-328): PROCEED, which goes to the order.
        bothRoles(
            "memory-dump-2-to-hall", ScreenId.SubMemory, says = listOf("MEMORY DUMP", "TO HALL", PROCEED), never = listOf("STOP NOW"), after = 9_000L,
            then = { phone ->
                proceedToTheOrder(phone)
                mainClock.advanceTimeBy(100L)
                assertTrue(drawn("MEMORY DUMP") && drawn("TO HALL"), "the work order's row does not read MEMORY DUMP · TO HALL")
                assertTrue(drawn("LOCKED"), "the chained player's work order lost its lock")
            },
        ) { caughtTheSource(it) }
        // Walking away from it (the motion budget, D-321) leaves by the same action and goes to the
        // work order: `MotionBudgetTest`'s Memory Dump case, unchanged by D-328.
    }

    // ---- A card refused while carrying: CARRYING . GO TO <ROOM> -------------------------------

    @Test
    fun aCardRefusedWhileCarryingSaysWhereToGo() {
        // A held pattern: the target's room.
        bothRoles("carrying-pattern-go-to-hall", ScreenId.ScanBad, says = listOf("CARRYING", "GO TO HALL", "SCAN ANOTHER"), never = listOf("NOTHING FOR")) { phone ->
            phone.houseSaid(RoundMessage.WorkOrderIssued(order(held = true)))
            phone.go(ScreenId.Scan)
            phone.scanning(garage.marker)
            phone.houseSaid(RoundMessage.ScanAnswered(null, Haptic.Double))
        }
        // A disk in hand, good: the Rack's room. Damaged: Disposal's.
        for ((to, room) in listOf(CircuitStation.Rack to "BED 2", CircuitStation.Disposal to "KITCHEN")) {
            bothRoles("carrying-disk-to-${to.name.lowercase()}", ScreenId.ScanBad, says = listOf("CARRYING", "GO TO $room"), never = listOf("NOTHING FOR")) { phone ->
                phone.go(ScreenId.Scan)
                phone.scanning(study.marker)
                phone.houseSaid(RoundMessage.ScanAnswered(null, carrying = DiskInHand(1, to)))
                phone.go(ScreenId.Scan)
                phone.scanning(garage.marker)
                phone.houseSaid(RoundMessage.ScanAnswered(null, Haptic.Double, carrying = DiskInHand(1, to)))
            }
        }
        // Carrying nothing, the same card is NOTHING FOR YOU HERE, as before.
        bothRoles("carrying-nothing", ScreenId.ScanBad, says = listOf("NOTHING FOR"), never = listOf("CARRYING")) { phone ->
            phone.go(ScreenId.Scan)
            phone.scanning(garage.marker)
            phone.houseSaid(RoundMessage.ScanAnswered(null, Haptic.Double))
        }
    }

    // ---- PERMISSIONS and PERIMETER ARMED -------------------------------------------------------

    private fun still(name: String, model: () -> FlowModel, says: List<String>, never: List<String>) {
        val frames = PanelRole.entries.map { role ->
            var image: BufferedImage? = null
            runDesktopComposeUiTest(width = 720, height = 1520) {
                setContent { DeviceCanvas(insets = insets) { FlowHost(model()) } }
                waitForIdle()
                for (word in says) assertTrue(drawn(word), "$name ($role): '$word' is not drawn")
                for (word in never) assertTrue(!drawn(word), "$name ($role): '$word' is drawn")
                image = onRoot().captureToImage().toAwtImage()
            }
            ImageIO.write(image!!, "png", File(out, "$name-${role.name.lowercase()}.png"))
            image!!
        }
        assertTrue(same(frames[0], frames[1]), "$name differs by role")
    }

    @Test
    fun permissionsAllGrantedAndPerimeterArmedSayProceed() {
        still(
            "permissions-all-granted",
            {
                val perms = PermsModel(read = { Grant.Granted }).also { it.refresh() }
                FlowModel(PanelState(screen = ScreenId.Perms), perms = perms)
            },
            says = listOf("PERMISSIONS", PROCEED) + DevicePermission.entries.map { it.label },
            never = listOf("CONTINUE", "REQUEST WHAT IS MISSING"),
        )
        still(
            "perimeter-armed",
            { FlowModel(PanelState(screen = ScreenId.Armed)) },
            says = listOf("PERIMETER", PROCEED),
            never = listOf("CONTINUE"),
        )
    }

    // ---- The work order of a chained and an unchained player ------------------------------------

    @Test
    fun theWorkOrderOfAChainedAndAnUnchainedPlayer() {
        bothRoles("order-chained", ScreenId.Work, says = listOf("SUBROUTINES", "LOCKED", "WAITING UPSTREAM", "JAM"), chained = true) { it.go(ScreenId.Work) }
        bothRoles("order-unchained", ScreenId.Work, says = listOf("SUBROUTINES", "DRIFT", "JAM"), never = listOf("LOCKED", "WAITING UPSTREAM"), chained = false) { it.go(ScreenId.Work) }
        assertEquals(Balance.chainedSeats(6), 2, "the table moved under the render")
    }
}
