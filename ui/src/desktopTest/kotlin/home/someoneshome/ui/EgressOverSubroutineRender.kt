package home.someoneshome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
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
 * **The Egress alert over a Subroutine, drawn, and the Subroutine the same after it as if it had
 * never come** (the owner's E1 ruling, D-297).
 *
 * Driven through [FlowHost], so the ten seconds are the real timer and the Subroutine's own clock is
 * the real composition's. Writes `egress-over-<kind>-<role>-up.png` and `-cleared.png` to
 * `build/screens` and `/tmp/someones-home-d297/` to be looked at, and checks what pixels can:
 *
 * - an Insider's alert over the fake and a Resident's over the work are the same pixels, up and
 *   cleared, for every kind;
 * - once the alert has cleared itself the screen is the same pixels as a phone whose alert came
 *   down the instant it arrived, at the same moment: nothing was recreated and no clock restarted
 *   (the Interrupt's sweep is moving, which is what makes that a claim about a clock).
 */
@OptIn(ExperimentalTestApi::class)
class EgressOverSubroutineRender {

    private val out = File("build/screens").apply { mkdirs() }
    private val look = File("/tmp/someones-home-d297").apply { mkdirs() }
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

    private fun atWork(role: PanelRole, kind: SubroutineKind): FlowModel {
        val phone = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            subroutines = SubroutineModel(),
        )
        phone.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(
                    OrderLine.Known(
                        index = 0,
                        work = AssignedWork.Subroutine(kind),
                        done = false,
                        at = listOf(Destination(study, shape = "diamond", room = "STUDY")),
                    ),
                ),
            ),
        )
        phone.scanning(study)
        phone.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(0, kind, SubroutineModel.benchParameters(kind))))
        phone.actions().beginSubroutine()
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

    /**
     * **How much of the light is left below the banner**, as the sum of every channel of every pixel
     * from [below] to the foot, over the same sum on the undimmed frame. The dim is `alpha` over the
     * panel (D-119), so this is [NOTIFIED_DIM] wherever there is any light to dim.
     */
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

    /** Today's Egress banner over the springboard, and the springboard: the dim E1 must match. */
    private fun bannerOverHome(): Pair<BufferedImage, BufferedImage> {
        var banner: BufferedImage? = null
        var home: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            val model = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true))
            host(model)
            model.houseSaid(egressOpened())
            mainClock.advanceTimeByFrame()
            assertEquals(ScreenId.Banner, model.state.screen)
            banner = shot()
            model.actions().dismissNotification()
            mainClock.advanceTimeByFrame()
            home = shot()
        }
        return banner!! to home!!
    }

    /**
     * How far a measured dim may sit from [NOTIFIED_DIM]: the sum is taken after the glass's bands
     * and after every channel is rounded to a byte, and the banner over Home measures 0.350. A dim
     * that was not applied measures 1.0.
     */
    private val DIM_SLACK = 0.05

    /** When the work opened, when the Egress arrives, and when both phones are photographed. */
    private val arrives = 1_000L
    private val photographed = arrives + HEAVY_HOLD + 500L

    /** The alert held its ten seconds: photographed up, one frame after it arrived, and at the end. */
    private fun held(role: PanelRole, kind: SubroutineKind): Pair<BufferedImage, BufferedImage> {
        var up: BufferedImage? = null
        var cleared: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            val model = atWork(role, kind)
            host(model)
            mainClock.advanceTimeBy(arrives)
            model.houseSaid(egressOpened())
            mainClock.advanceTimeByFrame()
            assertTrue(model.state.egressAlertUp, "$role/$kind: the alert is not up")
            up = shot()
            mainClock.advanceTimeBy(HEAVY_HOLD - 200L)
            assertTrue(model.state.egressAlertUp, "$role/$kind: the alert came down before its ten seconds")
            mainClock.advanceTimeBy(photographed - arrives - HEAVY_HOLD + 200L - 16L)
            assertTrue(!model.state.egressAlertUp, "$role/$kind: the alert outlived its ten seconds")
            assertEquals(Subroutine.of(kind).screen, model.state.screen, "$role/$kind: the phone left the work")
            cleared = shot()
        }
        return up!! to cleared!!
    }

    /**
     * The control: the same phone, the same Egress, the alert down in the frame it arrived, and the
     * clock moved in the same steps as [held]'s so the two are photographed at one frame time.
     * [after] photographs it that many milliseconds after the arrival's frame instead: 0 is the
     * moment [held] photographs the alert up.
     */
    private fun control(role: PanelRole, kind: SubroutineKind, after: Long? = null): BufferedImage {
        var image: BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            val model = atWork(role, kind)
            host(model)
            mainClock.advanceTimeBy(arrives)
            model.houseSaid(egressOpened())
            model.egressAlertLapsed()
            mainClock.advanceTimeByFrame()
            if (after != null) {
                if (after > 0) mainClock.advanceTimeBy(after)
            } else {
                mainClock.advanceTimeBy(HEAVY_HOLD - 200L)
                mainClock.advanceTimeBy(photographed - arrives - HEAVY_HOLD + 200L - 16L)
            }
            image = shot()
        }
        return image!!
    }

    @Test
    fun renderTheAlertOverEverySubroutineAndTheWorkAfterIt() {
        val faults = mutableListOf<String>()
        val reference = bannerOverHome().let { (banner, home) -> lightLeft(banner, home) }
        if (kotlin.math.abs(reference - NOTIFIED_DIM) > DIM_SLACK) faults += "the banner over Home is not at NOTIFIED_DIM: $reference"
        // Memory Dump has no screen yet (D-322, STOPPED).
        for (kind in SubroutineKind.entries - SubroutineKind.MemoryDump) {
            val name = kind.name.lowercase()
            val (residentUp, residentCleared) = held(PanelRole.Resident, kind)
            val (insiderUp, insiderCleared) = held(PanelRole.Insider, kind)
            save("egress-over-$name-resident-up", residentUp)
            save("egress-over-$name-resident-cleared", residentCleared)
            save("egress-over-$name-insider-up", insiderUp)
            save("egress-over-$name-insider-cleared", insiderCleared)
            if (!same(residentUp, insiderUp)) faults += "$kind: the alert over an Insider's fake is not the Resident's"
            if (!same(residentCleared, insiderCleared)) faults += "$kind: the work after the alert differs by role"
            if (same(residentUp, residentCleared)) faults += "$kind: the alert drew nothing"
            // Against the same work at the same moment with no alert, because some of these screens
            // (Replay's playback) are a different picture ten seconds later.
            val left = lightLeft(residentUp, control(PanelRole.Resident, kind, after = 0L))
            println("D-297  $kind: light left below the alert ${"%.3f".format(left)} (the banner over Home ${"%.3f".format(reference)})")
            if (!left.isNaN() && kotlin.math.abs(left - NOTIFIED_DIM) > DIM_SLACK) {
                faults += "$kind: the alert over the work leaves ${"%.3f".format(left)} of the light, the banner over Home ${"%.3f".format(reference)}"
            }
            val never = control(PanelRole.Resident, kind)
            save("egress-over-$name-resident-control", never)
            if (!same(residentCleared, never)) {
                faults += "$kind: after the alert the screen is not what it would have been without it"
            }
        }
        // The Interrupt's sweep moves on the screen's own clock, so the comparison above is about a
        // clock only if the picture changes between the arrival and the photograph.
        val early = control(PanelRole.Resident, SubroutineKind.Interrupt, after = 500L)
        val late = control(PanelRole.Resident, SubroutineKind.Interrupt)
        if (same(early, late)) faults += "the Interrupt's sweep did not move, so the clock comparison proves nothing"
        faults.forEach { println("D-297  $it") }
        assertEquals(emptyList(), faults)
    }

    /** **The swipe, with a finger**, over the work: the alert goes and the phone is on the work. */
    @Test
    fun aSwipeUpTakesTheAlertOffTheWork() {
        for (role in PanelRole.entries) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                val model = atWork(role, SubroutineKind.Jam)
                host(model)
                model.houseSaid(egressOpened())
                mainClock.advanceTimeByFrame()
                onNode(hasText("EGRESS ATTEMPT IN PROGRESS", substring = true)).performTouchInput {
                    down(center)
                    repeat(10) { moveBy(Offset(0f, -40f)) }
                    up()
                }
                mainClock.advanceTimeByFrame()
                assertTrue(!model.state.egressAlertUp, "$role: the swipe did not take the alert down")
                assertEquals(ScreenId.SubJam, model.state.screen, "$role: the swipe took the phone off the Jam")
            }
        }
    }
}
