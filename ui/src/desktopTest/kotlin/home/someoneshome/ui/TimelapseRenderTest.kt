package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.toView
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-319, rendered: the floor strip switching storeys, a timelapse frame, the dimmed READY IN
 * button and MONITORING JAMMED over page 1.**
 *
 * The home is `SavedHomesModel.sample()`'s second, MUM & DAD'S: KITCHEN, LIVING, HALL, UTILITY and
 * GARAGE on GROUND, the station in UTILITY; BED 1, BED 2, BATH 1 and LANDING on UPPER. Every storey's
 * counts are in the beat (D-316), so the strip only chooses which is drawn. The taps are real taps
 * on the rendered strip. Writes `build/screens/d319-*.png` at 600 by 1300, and compares a Resident's
 * pixels with an Insider's.
 */
@OptIn(ExperimentalTestApi::class)
class TimelapseRenderTest {

    private val out = File("build/screens").apply { mkdirs() }

    private val home: HomePlanView = SavedHomesModel.sample().homes[1].plan.toView()

    private fun room(name: String) = home.indexOf(name)

    private val counts = mapOf("KITCHEN" to 2, "UTILITY" to 1, "BED 1" to 1, "BATH 1" to 3)

    /** The reader's beat: the counts above, fresh on GROUND and stale upstairs, every other room 0 cold. */
    private fun reading(readyIn: Int = 0, history: Int = 60): OccupancyBeat.Terminal = openedReading(
        room("UTILITY"),
        home.rooms.map { r -> counts[r.name]?.let { it to (if (r.name.startsWith("B")) 1 else 0) } ?: (0 to 2) },
        readyIn, history,
    )

    private val couch = OccupancyBeat.outside(home.rooms.map { counts[it.name] ?: 0 })

    private fun frame(f: Int): List<Int> = home.rooms.map { r -> if (r.name == "KITCHEN") f % 3 else counts[r.name] ?: 0 }

    private fun lapse(first: Int, jammed: List<Boolean> = listOf(false, false, false)): OccupancyBeat.Lapse =
        OccupancyBeat.lapse(room("UTILITY"), 60, first, List(jammed.size) { frame(first + it) }, jammed)

    private val sent = mutableListOf<IntentMessage>()

    private fun phone(screen: ScreenId, role: PanelRole = PanelRole.Resident, out: OutBy? = null): FlowModel {
        val model = FlowModel(PanelState(screen = screen, houseAttached = true, role = role, outBy = out), send = { sent += it })
        model.houseSaid(RoundMessage.PlanIssued(home))
        return model
    }

    private fun render(name: String, model: FlowModel, check: ComposeUiTest.() -> Unit = {}): ByteArray {
        var png = ByteArray(0)
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(600.dp, 1300.dp)) {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(model.state, model.actions(), model.editor, model.homes, model.lobby, perms = model.perms)
                        }
                    }
                }
            }
            check()
            waitForIdle()
            val image = onRoot().captureToImage().toAwtImage()
            ImageIO.write(image, "png", File(this@TimelapseRenderTest.out, "$name.png"))
            png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }
        return png
    }

    private fun ComposeUiTest.drawn(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theTerminalSwitchesStoreysByTheStrip() {
        val ground = phone(ScreenId.TermLive).also { it.occupancySaid(reading()) }
        render("d319-terminal-ground", ground) {
            assertTrue(drawn("UTILITY") && drawn("KITCHEN"), "the Terminal did not open on the station's storey")
            assertTrue(!drawn("BED 1"), "the Terminal drew UPPER over GROUND")
            assertTrue(drawn("TIMELAPSE . 2:00 AT 6X"))
        }
        val upper = phone(ScreenId.TermLive).also { it.occupancySaid(reading()) }
        render("d319-terminal-upper", upper) {
            onNodeWithText("UPPER").performClick()
            waitForIdle()
            assertTrue(drawn("BED 1") && drawn("BATH 1"), "a tap on UPPER did not draw UPPER")
            assertTrue(!drawn("KITCHEN"), "GROUND is still drawn after the tap")
            assertTrue(drawn("3"), "UPPER's BATH 1 count is not drawn")
        }
        assertEquals(1, upper.state.storeyShown)
        assertEquals(emptyList(), sent, "the strip sent something up")
    }

    @Test
    fun theCouchSwitchesStoreysByTheStrip() {
        val ground = phone(ScreenId.Ghost3, out = OutBy.Revoked).also { it.occupancySaid(couch) }
        render("d319-couch-ground", ground) {
            assertTrue(drawn("KITCHEN") && !drawn("BED 1"), "the couch did not open on the first storey")
        }
        val upper = phone(ScreenId.Ghost3, out = OutBy.Revoked).also { it.occupancySaid(couch) }
        render("d319-couch-upper", upper) {
            onNodeWithText("UPPER").performClick()
            waitForIdle()
            assertTrue(drawn("BED 1") && drawn("LANDING") && !drawn("KITCHEN"), "a tap on UPPER did not draw the couch's UPPER")
        }
    }

    @Test
    fun aTimelapseFrameAndAJammedOne() {
        val playing = phone(ScreenId.TermLive).also {
            it.occupancySaid(reading())
            it.playTimelapse()
            it.occupancySaid(lapse(27))
        }
        assertEquals(ScreenId.Timelapse, playing.state.screen)
        render("d319-timelapse-frame", playing) {
            assertTrue(drawn("TERMINAL . -1:06"), "the playback's place is not in the header")
            assertTrue(drawn("STOP THE PLAYBACK"))
            assertEquals(0, onAllNodesWithTag(STATIC_VEIL).fetchSemanticsNodes().size, "a clear frame was jammed")
        }
        val jammed = phone(ScreenId.TermLive).also {
            it.occupancySaid(reading())
            it.playTimelapse()
            it.occupancySaid(lapse(30, listOf(true, true, false)))
        }
        render("d319-timelapse-jammed", jammed) {
            assertEquals(1, onAllNodesWithTag(STATIC_VEIL).fetchSemanticsNodes().size, "a jammed frame did not play as noise")
            assertTrue(!drawn("2"), "a jammed frame drew a count")
        }
    }

    @Test
    fun theDimmedReadyInButtonIsTheSameForBothRoles() {
        val resident = render("d319-ready-in", phone(ScreenId.TermLive).also { it.occupancySaid(reading(readyIn = 74)) }) {
            assertTrue(drawn("TIMELAPSE . READY IN 1:14"))
            onNodeWithText("TIMELAPSE . READY IN 1:14").performClick()
        }
        assertEquals(emptyList(), sent, "the dimmed button asked the house")
        val insider = render("d319-ready-in-insider", phone(ScreenId.TermLive, PanelRole.Insider).also { it.occupancySaid(reading(readyIn = 74)) })
        assertContentEquals(resident, insider, "the dimmed button differs by role")
    }

    @Test
    fun monitoringJammedOverPageOneOnEitherRole() {
        fun jammed(role: PanelRole) = phone(ScreenId.Home, role).also { it.houseSaid(RoundMessage.StaticOpened(10, 7L)) }
        val resident = render("d319-monitoring-jammed", jammed(PanelRole.Resident)) {
            assertTrue(drawn("MONITORING JAMMED"), "no banner over page 1")
        }
        val insider = render("d319-monitoring-jammed-insider", jammed(PanelRole.Insider))
        assertContentEquals(resident, insider, "the banner over page 1 differs by role")
        val frame = render("d319-timelapse-frame-insider", phone(ScreenId.TermLive, PanelRole.Insider).also {
            it.occupancySaid(reading())
            it.playTimelapse()
            it.occupancySaid(lapse(27))
        })
        val residentFrame = render("d319-timelapse-frame-resident", phone(ScreenId.TermLive).also {
            it.occupancySaid(reading())
            it.playTimelapse()
            it.occupancySaid(lapse(27))
        })
        assertContentEquals(residentFrame, frame, "a playback frame differs by role")
    }
}
