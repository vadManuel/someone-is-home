package home.someoneshome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
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
 * **The Egress alert over the caught scan and the scan, drawn, and each screen the same after it as
 * if it had never come** (the owner's 2026-11-19 ruling on D-297's questions, D-299).
 *
 * Driven through [FlowHost], so the ten seconds and the scan's own window are the real timers.
 * Writes `egress-over-<screen>-<role>-up.png` and `-cleared.png` to `build/screens` and
 * `/tmp/someones-home-d299/` to be looked at, and checks what pixels can:
 *
 * - an Insider's phone and a Resident's are the same pixels, up and cleared, on both screens;
 * - the light left below the banner is [NOTIFIED_DIM], as over the springboard and the work;
 * - once the alert is down the screen is the same pixels as a phone whose alert came down the
 *   instant it arrived, at the same moment: on the scan that is the window's bar, which moves, so
 *   it is a claim that the window was not restarted;
 * - the scan's window still ends on its own ten seconds with the alert up, and lands on the
 *   springboard with nothing over it.
 *
 * **There is no camera picture to cover.** The in-round scan has no preview (D-159, and
 * `CameraCardScanner`: `ScanUse.InRound` builds no `AVCaptureVideoPreviewLayer`); the lit screen is
 * the light the card is read by. So what the dim covers is the lamp, and it is the same `alpha`
 * over the same Compose panel as everywhere else.
 */
@OptIn(ExperimentalTestApi::class)
class EgressOverScanRender {

    private val out = File("build/screens").apply { mkdirs() }
    private val look = File("/tmp/someones-home-d299").apply { mkdirs() }
    private val study = MarkerId("card-study")

    /** An iPhone 16 Pro's measured insets (`DeviceLayoutTest`). */
    private val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)

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

    /** A housed phone of [role] with a Jam dealt at [study], on [screen]. */
    private fun on(role: PanelRole, screen: ScreenId): FlowModel {
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
        if (screen == ScreenId.ScanCaught) {
            phone.scanning(study)
            phone.houseSaid(
                RoundMessage.ScanAnswered(SubroutineInstance(0, SubroutineKind.Jam, SubroutineModel.benchParameters(SubroutineKind.Jam))),
            )
        }
        assertEquals(screen, phone.state.screen)
        return phone
    }

    private fun DesktopComposeUiTest.host(model: FlowModel) {
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = insets) { FlowHost(model) } }
        mainClock.advanceTimeByFrame()
    }

    private fun DesktopComposeUiTest.shot(): BufferedImage = onRoot().captureToImage().toAwtImage()

    private fun save(name: String, png: BufferedImage) {
        ImageIO.write(png, "png", File(out, "$name.png"))
        ImageIO.write(png, "png", File(look, "$name.png"))
    }

    private fun same(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    /** [EgressOverSubroutineRender]'s measure: every channel below [below], dimmed over lit. */
    private fun lightLeft(dimmed: BufferedImage, lit: BufferedImage, below: Int = 420): Double {
        fun sum(image: BufferedImage): Long {
            var total = 0L
            for (y in below until image.height) for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                total += (rgb shr 16 and 0xff) + (rgb shr 8 and 0xff) + (rgb and 0xff)
            }
            return total
        }
        val base = sum(lit)
        return if (base == 0L) Double.NaN else sum(dimmed).toDouble() / base
    }

    /** [EgressOverSubroutineRender]'s slack: the banner over Home measures 0.350. */
    private val DIM_SLACK = 0.05

    /** When the screen opened (0), when the Egress arrives, and when the alert is taken down. */
    private val arrives = 1_000L

    /**
     * How long after the arrival the cleared picture is taken. The caught scan has no window of its
     * own, so it is cleared by its ten seconds; the scan's window ends before those ten seconds
     * could (it opened first and is no longer), so it is cleared by the swipe, two seconds in.
     */
    private fun clearsAfter(screen: ScreenId): Long = if (screen == ScreenId.Scan) 2_000L else HEAVY_HOLD + 500L

    /** Up, one frame after the arrival; cleared, [clearsAfter] later, by the ten seconds or the swipe. */
    private fun held(role: PanelRole, screen: ScreenId): Pair<BufferedImage, BufferedImage> {
        var up: BufferedImage? = null
        var cleared: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            val model = on(role, screen)
            host(model)
            mainClock.advanceTimeBy(arrives)
            model.houseSaid(egressOpened())
            mainClock.advanceTimeByFrame()
            assertTrue(model.state.egressAlertUp, "$role/$screen: the alert is not up")
            assertEquals(screen, model.state.screen, "$role/$screen: the Egress moved the phone")
            up = shot()
            if (screen == ScreenId.Scan) {
                mainClock.advanceTimeBy(clearsAfter(screen) - 16L)
                assertTrue(model.state.egressAlertUp, "$role/$screen: the alert came down by itself")
                model.actions().dismissNotification()
                mainClock.advanceTimeByFrame()
            } else {
                mainClock.advanceTimeBy(HEAVY_HOLD - 200L)
                assertTrue(model.state.egressAlertUp, "$role/$screen: the alert came down before its ten seconds")
                mainClock.advanceTimeBy(clearsAfter(screen) - HEAVY_HOLD + 200L)
            }
            assertTrue(!model.state.egressAlertUp, "$role/$screen: the alert outlived its clearing")
            assertEquals(screen, model.state.screen, "$role/$screen: the phone left the screen")
            cleared = shot()
        }
        return up!! to cleared!!
    }

    /** The same phone with the alert down in the frame it arrived, photographed [after] the arrival. */
    private fun control(role: PanelRole, screen: ScreenId, after: Long): BufferedImage {
        var image: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            val model = on(role, screen)
            host(model)
            mainClock.advanceTimeBy(arrives)
            model.houseSaid(egressOpened())
            model.egressAlertLapsed()
            mainClock.advanceTimeByFrame()
            if (after > 0) mainClock.advanceTimeBy(after)
            image = shot()
        }
        return image!!
    }

    @Test
    fun renderTheAlertOverTheScanAndTheCaughtScanAndEachScreenAfterIt() {
        val faults = mutableListOf<String>()
        for (screen in listOf(ScreenId.Scan, ScreenId.ScanCaught)) {
            val name = if (screen == ScreenId.Scan) "scan" else "scancaught"
            val (residentUp, residentCleared) = held(PanelRole.Resident, screen)
            val (insiderUp, insiderCleared) = held(PanelRole.Insider, screen)
            save("egress-over-$name-resident-up", residentUp)
            save("egress-over-$name-resident-cleared", residentCleared)
            save("egress-over-$name-insider-up", insiderUp)
            save("egress-over-$name-insider-cleared", insiderCleared)
            if (!same(residentUp, insiderUp)) faults += "$screen: the alert over an Insider's phone is not the Resident's"
            if (!same(residentCleared, insiderCleared)) faults += "$screen: the screen after the alert differs by role"
            if (same(residentUp, residentCleared)) faults += "$screen: the alert drew nothing"
            val left = lightLeft(residentUp, control(PanelRole.Resident, screen, after = 0L))
            println("D-299  $screen: light left below the alert ${"%.3f".format(left)}")
            if (left.isNaN() || kotlin.math.abs(left - NOTIFIED_DIM) > DIM_SLACK) {
                faults += "$screen: the alert leaves ${"%.3f".format(left)} of the light"
            }
            // The control is photographed where [held] photographs the cleared screen: one frame
            // after the arrival, then the rest of the way.
            val never = control(PanelRole.Resident, screen, after = clearsAfter(screen))
            save("egress-over-$name-resident-control", never)
            if (!same(residentCleared, never)) faults += "$screen: after the alert the screen is not what it would have been without it"
        }
        // The scan's bar moves on its window, so the comparison above is about the window only if
        // the picture changes between the arrival and the photograph.
        if (same(control(PanelRole.Resident, ScreenId.Scan, after = 0L), control(PanelRole.Resident, ScreenId.Scan, after = clearsAfter(ScreenId.Scan)))) {
            faults += "the scan's bar did not move, so the window comparison proves nothing"
        }
        faults.forEach { println("D-299  $it") }
        assertEquals(emptyList(), faults)
    }

    /**
     * **D-159's window is the scan's, and the alert does not stretch it**: opened at 0, the alert at
     * one second, and the phone is on the springboard with nothing over it when the ten seconds are
     * up, as it would have been without the Egress.
     */
    @Test
    fun theScansWindowEndsOnItsOwnTenSecondsUnderTheAlert() {
        for (role in PanelRole.entries) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                val model = on(role, ScreenId.Scan)
                host(model)
                mainClock.advanceTimeBy(arrives)
                model.houseSaid(egressOpened())
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeBy(PanelVals.SCAN_WINDOW_MILLIS - arrives - 300L)
                assertEquals(ScreenId.Scan, model.state.screen, "$role: the scan closed early under the alert")
                assertTrue(model.state.egressAlertUp, "$role: the alert came down before the window ended")
                mainClock.advanceTimeBy(400L)
                assertEquals(ScreenId.Home, model.state.screen, "$role: the scan's window did not end on its ten seconds")
                assertTrue(!model.state.egressAlertUp, "$role: the alert outlived the scan it stood over")
                mainClock.advanceTimeByFrame()
                if (role == PanelRole.Resident) save("egress-over-scan-resident-window-ended", shot())
            }
        }
    }

    /** **The swipe, with a finger**, over both screens: the alert goes and the screen stays. */
    @Test
    fun aSwipeUpTakesTheAlertOffTheScanAndTheCaughtScan() {
        for (screen in listOf(ScreenId.Scan, ScreenId.ScanCaught)) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    val model = on(role, screen)
                    host(model)
                    model.houseSaid(egressOpened())
                    mainClock.advanceTimeByFrame()
                    onNode(hasText("EGRESS ATTEMPT IN PROGRESS", substring = true)).performTouchInput {
                        down(center)
                        repeat(10) { moveBy(Offset(0f, -40f)) }
                        up()
                    }
                    mainClock.advanceTimeByFrame()
                    assertTrue(!model.state.egressAlertUp, "$role: the swipe did not take the alert off the $screen")
                    assertEquals(screen, model.state.screen, "$role: the swipe took the phone off the $screen")
                }
            }
        }
    }

    /** **The tap, with a finger**, opens the Egress widget, as it does over the springboard. */
    @Test
    fun aTapOnTheAlertOpensTheWidget() {
        for (screen in listOf(ScreenId.Scan, ScreenId.ScanCaught)) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    val model = on(role, screen)
                    host(model)
                    model.houseSaid(egressOpened())
                    mainClock.advanceTimeByFrame()
                    onNode(hasText("EGRESS ATTEMPT IN PROGRESS", substring = true)).performTouchInput { click(center) }
                    mainClock.advanceTimeByFrame()
                    assertEquals(ScreenId.EgressWidget, model.state.screen, "$role: the tap over the $screen did not open the widget")
                    assertTrue(!model.state.egressAlertUp, "$role: the alert followed the phone to the widget")
                }
            }
        }
    }
}
