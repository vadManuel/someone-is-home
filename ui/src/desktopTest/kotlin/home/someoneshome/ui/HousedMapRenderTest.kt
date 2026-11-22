package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.RoomKind
import home.someoneshome.model.RoomView
import home.someoneshome.model.StoreyView
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.OccupancyWire
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.toView
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-316: the Terminal and the couch on a housed phone, drawn from what the house sent.**
 *
 * The plan arrives as `RoundMessage.PlanIssued` and the counts as the opened field of an upload's
 * answer (`OccupancyWire.read`), through `FlowModel.houseSaid` and `FlowModel.occupancySaid`, the
 * two doors a phone has. The bench home is `SavedHomesModel.sample()`'s first, the home
 * `TerminalCountsWiringTest` plays its round in over real sockets; the reader's counts below are the
 * ones that test's reader was sent (`D-316 READER` in its output: HALL 1 fresh, STUDY 2 fresh, every
 * other room 0 cold, the station in HALL), and the couch's are the house's placements at that moment
 * (the two in STUDY, the reader at the station). Writes `build/screens/d316-*.png` at 600 by 1300.
 */
@OptIn(ExperimentalTestApi::class)
class HousedMapRenderTest {

    private val out = File("build/screens").apply { mkdirs() }

    private val bench: HomePlanView = SavedHomesModel.sample().homes.first().plan.toView()

    /** The field's plain bytes, as a phone opens them: [kind], the outline, then one byte per room. */
    private fun opened(kind: Int, here: Int, rooms: List<Pair<Int, Int>>): OccupancyBeat {
        val plain = ByteArray(OccupancyWire.PLAIN_BYTES)
        plain[0] = kind.toByte()
        plain[1] = (here + 1).toByte()
        plain[2] = rooms.size.toByte()
        rooms.forEachIndexed { i, (count, band) -> plain[OccupancyWire.HEADER_BYTES + i] = (count or (band shl 6)).toByte() }
        return requireNotNull(OccupancyWire.read(plain)) { "the field did not read" }
    }

    private fun room(name: String) = bench.indexOf(name)

    /** The bench reader's beat: HALL 1 fresh, STUDY 2 fresh, the rest 0 cold, the station in HALL. */
    private val reading = opened(
        OccupancyWire.KIND_TERMINAL, room("HALL"),
        bench.rooms.indices.map { i ->
            when (i) {
                room("HALL") -> 1 to OccupancyWire.FRESH
                room("STUDY") -> 2 to OccupancyWire.FRESH
                else -> 0 to OccupancyWire.COLD
            }
        },
    )

    /** The couch at the same moment: the house's placements, no band. */
    private val couch = opened(
        OccupancyWire.KIND_OUTSIDE, -1,
        bench.rooms.indices.map { i -> (if (i == room("STUDY")) 2 else if (i == room("HALL")) 1 else 0) to 0 },
    )

    private fun phone(plan: HomePlanView, beat: OccupancyBeat, screen: ScreenId, out: Boolean = false, jam: StaticJam? = null): FlowModel {
        val model = FlowModel(PanelState(houseAttached = true, outBy = if (out) OutBy.Revoked else null, staticJam = jam))
        model.houseSaid(RoundMessage.PlanIssued(plan))
        model.occupancySaid(beat)
        model.push(screen)
        return model
    }

    private fun render(name: String, model: FlowModel, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {}) =
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
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(this@HousedMapRenderTest.out, "$name.png"))
            check()
        }

    @Test
    fun theTerminalDrawsTheHousesPlanAndTheReadersCounts() {
        val reader = phone(bench, reading, ScreenId.TermLive)
        val map = assertNotNullMap(reader.state.let { PanelVals(it).terminalMap })
        assertEquals(room("HALL"), map.here, "the outline is not the station's room")
        assertEquals(listOf("GROUND", "UPPER"), map.storeys)
        assertEquals(0, map.storey, "the Terminal does not draw the station's storey")
        assertEquals(2, map.rooms[room("STUDY")].count)
        assertEquals(Ramp.Ink, map.rooms[room("STUDY")].ink, "a fresh count is not in ink")
        assertEquals(Ramp.Dim, map.rooms[room("KITCHEN")].ink, "a cold count is not dim")
        assertEquals(null, map.rooms[room("STAIRS")].count, "the stairs were counted")
        render("d316-terminal-live", reader) {
            assertTrue(onAllNodesWithText("STUDY").fetchSemanticsNodes().isNotEmpty(), "the live view drew no STUDY")
            assertTrue(onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty(), "the live view drew no STUDY count")
        }
    }

    @Test
    fun theTerminalIsJammedOverTheRealCounts() {
        render("d316-terminal-jammed", phone(bench, reading, ScreenId.TermLive, jam = StaticJam(10, 20261121L))) {
            assertEquals(1, onAllNodesWithTag(STATIC_VEIL).fetchSemanticsNodes().size, "the jam drew no noise over the real counts")
            assertTrue(onAllNodesWithText("STUDY").fetchSemanticsNodes().isNotEmpty(), "the plan is not still drawn under the jam")
        }
    }

    @Test
    fun theCouchDrawsTrueOccupancyCleanEvenUnderAJam() {
        val ghost = phone(bench, couch, ScreenId.Ghost3, out = true, jam = StaticJam(10, 20261121L))
        val map = assertNotNullMap(PanelVals(ghost.state).trueMap)
        assertEquals(-1, map.here, "the couch outlined a room")
        assertEquals(Amber.Bright, map.rooms[room("STUDY")].ink)
        render("d316-couch", ghost) {
            assertEquals(0, onAllNodesWithTag(STATIC_VEIL).fetchSemanticsNodes().size, "the couch went to noise")
            assertTrue(onAllNodesWithText("STUDY").fetchSemanticsNodes().isNotEmpty(), "the couch drew no plan")
        }
    }

    /** **A home that is not the bungalow**: one storey, an L-shaped room, stairs. Its count sits in its own cells. */
    @Test
    fun aHomeThatIsNotTheBungalowIsDrawnAsItIs() {
        // 6 by 5: PANTRY an L (the left column and the bottom row), HALLWAY the rest of the top, STAIRS two cells.
        val cells = listOf(
            0, 1, 1, 1, 2, 2,
            0, 1, 1, 1, -1, -1,
            0, 1, 1, 1, -1, -1,
            0, -1, -1, -1, -1, -1,
            0, 0, 0, 0, 0, 0,
        )
        val plan = HomePlanView(
            listOf(StoreyView("ONLY FLOOR", 6, 5, cells)),
            listOf(RoomView("PANTRY", RoomKind.Room), RoomView("HALLWAY", RoomKind.Room), RoomView("STAIRS", RoomKind.Stairs)),
        )
        val beat = opened(OccupancyWire.KIND_TERMINAL, 1, listOf(3 to OccupancyWire.STALE, 1 to OccupancyWire.FRESH, 0 to OccupancyWire.COLD))
        val model = phone(plan, beat, ScreenId.TermLive)
        val map = assertNotNullMap(PanelVals(model.state).terminalMap)
        val pantry = map.rooms[0]
        assertEquals(0, map.roomAt(pantry.centreRow.toInt(), pantry.centreCol.toInt()), "the L's count sits outside the L")
        assertEquals(listOf("ONLY FLOOR"), map.storeys)
        render("d316-terminal-other-home", model) {
            assertTrue(onAllNodesWithText("PANTRY").fetchSemanticsNodes().isNotEmpty())
            assertEquals(0, onAllNodesWithText("KITCHEN").fetchSemanticsNodes().size, "the bungalow was drawn over a home that is not it")
        }
    }

    private fun assertNotNullMap(map: LiveMap?): LiveMap = requireNotNull(map) { "a housed phone holding a plan and a beat drew no map" }
}
