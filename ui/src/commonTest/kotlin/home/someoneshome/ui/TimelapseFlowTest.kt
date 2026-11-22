package home.someoneshome.ui

import home.someoneshome.model.ClientClass
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.Role
import home.someoneshome.model.RoomKind
import home.someoneshome.model.RoomView
import home.someoneshome.model.RoundState
import home.someoneshome.model.StoreyView
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.OccupancyWire
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A reading as a phone opens it** (D-316, D-319): the field's plain bytes, [rooms] as count and
 * band per room in plan order, the outline [here], and the station's readiness.
 */
internal fun openedReading(here: Int, rooms: List<Pair<Int, Int>>, readyIn: Int = 0, history: Int = 60): OccupancyBeat.Terminal {
    val plain = ByteArray(OccupancyWire.PLAIN_BYTES)
    plain[0] = OccupancyWire.KIND_TERMINAL.toByte()
    plain[1] = (here + 1).toByte()
    plain[2] = rooms.size.toByte()
    plain[3] = readyIn.toByte()
    plain[4] = history.toByte()
    rooms.forEachIndexed { i, (count, band) -> plain[OccupancyWire.HEADER_BYTES + i] = (count or (band shl 6)).toByte() }
    return OccupancyWire.read(plain) as OccupancyBeat.Terminal
}

/**
 * **D-319 on a housed phone: the floor strip, the timelapse and MONITORING JAMMED.**
 *
 * The phone decides nothing about the timelapse: TIMELAPSE asks, the playback opens on the house's
 * first frames, the frames switch a third of a second apart, and STOP (or any way off the playback)
 * says so once. The strip is input echo. MONITORING JAMMED rides the jam every living phone is sent.
 */
class TimelapseFlowTest {

    /** Two storeys: GROUND holds HALL (the station) and KITCHEN, UPPER holds BED 1 and STAIRS. */
    private val plan = HomePlanView(
        listOf(
            StoreyView("GROUND", 2, 1, listOf(0, 1)),
            StoreyView("UPPER", 2, 1, listOf(2, 3)),
        ),
        listOf(RoomView("HALL", RoomKind.Room), RoomView("KITCHEN", RoomKind.Room), RoomView("BED 1", RoomKind.Room), RoomView("STAIRS", RoomKind.Stairs)),
    )

    private val sent = mutableListOf<IntentMessage>()

    private fun phone(screen: ScreenId, role: PanelRole = PanelRole.Resident, out: OutBy? = null): FlowModel {
        val model = FlowModel(PanelState(screen = screen, houseAttached = true, role = role, outBy = out), send = { sent += it })
        model.houseSaid(RoundMessage.PlanIssued(plan))
        return model
    }

    private fun reading(readyIn: Int = 0, history: Int = 60): OccupancyBeat.Terminal =
        openedReading(0, listOf(1 to 0, 2 to 1, 3 to 0, 0 to 2), readyIn, history)

    private fun lapse(first: Int, jammed: List<Boolean> = listOf(false, false, false)): OccupancyBeat.Lapse = OccupancyBeat.lapse(
        0, 60, first, List(jammed.size) { f -> listOf(first + f, 0, 1, 0) }, jammed,
    )

    // ---- The floor strip ------------------------------------------------------------------------

    @Test
    fun `the strip switches the Terminal's storey and the couch's and each opens on its own`() {
        val reader = phone(ScreenId.TermLive)
        reader.occupancySaid(reading())
        assertEquals(0, PanelVals(reader.state).terminalMap!!.storey, "the Terminal did not open on the station's storey")
        assertTrue(PanelVals(reader.state).terminalMap!!.switchable)
        reader.showStorey(1)
        val upper = PanelVals(reader.state).terminalMap!!
        assertEquals(1, upper.storey, "the strip did not switch the Terminal to UPPER")
        assertTrue(upper.rooms[2].drawn && !upper.rooms[0].drawn, "UPPER drew GROUND's rooms")
        assertEquals(3, upper.rooms[2].count, "UPPER's count is not the house's")
        assertEquals(emptyList(), sent, "a tap on the strip went up")
        // Kept into the playback and back; dropped off the station, so the next visit opens on its own.
        reader.go(ScreenId.Timelapse)
        assertEquals(1, reader.state.storeyShown)
        reader.go(ScreenId.TermLive)
        assertEquals(1, reader.state.storeyShown)
        reader.go(ScreenId.Home)
        assertNull(reader.state.storeyShown)

        val couch = phone(ScreenId.Ghost3, out = OutBy.Revoked)
        couch.occupancySaid(OccupancyBeat.outside(listOf(1, 2, 3, 0)))
        assertEquals(0, PanelVals(couch.state).trueMap!!.storey, "the couch did not open on the first storey")
        couch.showStorey(1)
        assertEquals(1, PanelVals(couch.state).trueMap!!.storey, "the strip did not switch the couch to UPPER")
        assertEquals(3, PanelVals(couch.state).trueMap!!.rooms[2].count)
    }

    // ---- The timelapse ---------------------------------------------------------------------------

    @Test
    fun `TIMELAPSE asks the house and the playback opens on the house's frames`() {
        val model = phone(ScreenId.TermLive)
        model.occupancySaid(reading())
        assertTrue(PanelVals(model.state).timelapseReady)
        assertEquals("TIMELAPSE . 2:00 AT 6X", PanelVals(model.state).timelapseLabel)
        model.playTimelapse()
        assertEquals(listOf<IntentMessage>(IntentMessage.PlayTimelapse), sent)
        assertEquals(ScreenId.TermLive, model.state.screen, "the phone walked into a playback the house had not started")
        // A beat answered before the request arrived changes nothing.
        model.occupancySaid(reading())
        assertEquals(ScreenId.TermLive, model.state.screen)
        // The house's first frames open the playback, and the live view is gone with them.
        model.occupancySaid(lapse(0))
        assertEquals(ScreenId.Timelapse, model.state.screen)
        assertNull(model.state.terminalBeat, "the live view was kept while the past played")
        val vals = PanelVals(model.state)
        assertEquals("-2:00", vals.timelapseAt)
        assertEquals(0, vals.timelapseMap!!.rooms[0].count)
        // The next frames switch in, a third of a second apart; nothing in between.
        val beat = model.state.lapseBeat!!
        model.lapseStepped(beat, 1)
        assertEquals(1, PanelVals(model.state).timelapseMap!!.rooms[0].count)
        assertEquals("-1:58", PanelVals(model.state).timelapseAt)
        model.lapseStepped(beat, 2)
        assertEquals(2, PanelVals(model.state).timelapseMap!!.rooms[0].count)
        // A stale step from a beat already replaced moves nothing.
        model.occupancySaid(lapse(3))
        model.lapseStepped(beat, 2)
        assertEquals(0, model.state.lapseShown)
        assertEquals(3, PanelVals(model.state).timelapseMap!!.rooms[0].count)
        assertEquals(4f / 60, PanelVals(model.state).timelapsePlayed)
    }

    @Test
    fun `the house ending the playback returns to the live view and STOP says so once`() {
        val ended = phone(ScreenId.TermLive)
        ended.occupancySaid(reading())
        ended.playTimelapse()
        ended.occupancySaid(lapse(57))
        sent.clear()
        ended.occupancySaid(reading(readyIn = 120))
        assertEquals(ScreenId.TermLive, ended.state.screen, "the playback did not end with the house's")
        assertEquals(emptyList(), sent, "the house's own end was answered with a STOP")

        val stopped = phone(ScreenId.TermLive)
        stopped.occupancySaid(reading())
        stopped.playTimelapse()
        stopped.occupancySaid(lapse(9))
        sent.clear()
        stopped.go(ScreenId.TermLive)
        assertEquals(ScreenId.TermLive, stopped.state.screen)
        assertEquals(listOf<IntentMessage>(IntentMessage.StopTimelapse), sent, "STOP did not say so, once")
        // A beat still carrying the playback opens nothing: this phone did not ask again.
        stopped.occupancySaid(lapse(12))
        assertEquals(ScreenId.TermLive, stopped.state.screen, "a stale playback beat walked the phone back in")
        // BACK on the live view is the live view's, not a walk into a playback that is over.
        stopped.occupancySaid(reading(readyIn = 30))
        stopped.back()
        assertTrue(stopped.state.screen != ScreenId.Timelapse, "BACK walked into the playback")
    }

    @Test
    fun `while the station cannot play the button is dimmed and says READY IN`() {
        val cooling = phone(ScreenId.TermLive)
        cooling.occupancySaid(reading(readyIn = 74, history = 60))
        assertFalse(PanelVals(cooling.state).timelapseReady)
        assertEquals("TIMELAPSE . READY IN 1:14", PanelVals(cooling.state).timelapseLabel)
        val empty = phone(ScreenId.TermLive)
        empty.occupancySaid(reading(readyIn = 2, history = 0))
        assertEquals("TIMELAPSE . READY IN 0:02", PanelVals(empty.state).timelapseLabel)
        // A short history plays as itself: the cooldown and the label both scale to it.
        val short = phone(ScreenId.TermLive)
        short.occupancySaid(reading(readyIn = 0, history = 17))
        assertEquals("TIMELAPSE . 0:34 AT 6X", PanelVals(short.state).timelapseLabel)
        // Before the house has said anything: dimmed, and a dash.
        val silent = phone(ScreenId.TermLive)
        assertFalse(PanelVals(silent.state).timelapseReady)
        assertEquals("TIMELAPSE . READY IN -", PanelVals(silent.state).timelapseLabel)
        // The harness's port, with no house, is the design's.
        assertEquals("TIMELAPSE . 2:00 AT 6X", PanelVals(PanelState(screen = ScreenId.TermLive)).timelapseLabel)
    }

    @Test
    fun `a jammed frame plays as noise with no counts under it`() {
        val model = phone(ScreenId.TermLive)
        model.occupancySaid(reading())
        model.playTimelapse()
        model.occupancySaid(lapse(30, listOf(false, true, false)))
        assertNull(PanelVals(model.state).lapseNoise)
        model.lapseStepped(model.state.lapseBeat!!, 1)
        val vals = PanelVals(model.state)
        assertTrue(vals.lapseJammed)
        assertEquals(31, assertNotNull(vals.lapseNoise).second, "the noise is not the frame's own")
        assertTrue(vals.timelapseMap!!.rooms.all { it.count == null }, "a jammed frame drew counts")
    }

    // ---- MONITORING JAMMED -----------------------------------------------------------------------

    @Test
    fun `MONITORING JAMMED lands quietly over the screen on every living phone of either role`() {
        for (role in listOf(PanelRole.Resident, PanelRole.Insider)) {
            for (screen in listOf(ScreenId.Home, ScreenId.TermLive, ScreenId.Page2)) {
                val model = phone(screen, role)
                model.houseSaid(RoundMessage.StaticOpened(10, 7L))
                assertEquals(screen, model.state.screen, "the banner walked a ${role.name} off $screen")
                val arrival = assertNotNull(PanelVals(model.state).arrival, "no banner over $screen")
                assertEquals("MONITORING JAMMED", arrival.notification.body)
                assertEquals(NotificationKind.Text, arrival.notification.kind, "it is not the house's quiet text")
                assertFalse(Notifications.dims(arrival), "the banner dimmed the house")
                assertNotNull(model.state.staticJam, "the jam itself changed")
                assertEquals("MONITORING JAMMED", model.state.houseTexts.last().body)
                // Swiped, it comes down and the screen stays.
                model.dismissNotification()
                assertNull(PanelVals(model.state).arrival)
                assertEquals(screen, model.state.screen)
            }
        }
        // The reader is not let go of the station by it.
        assertEquals(emptyList(), sent.filter { it == IntentMessage.LeaveTerminal })
    }

    @Test
    fun `MONITORING JAMMED never reaches an out phone`() {
        // The house sends the jam to the two living classes and no out one (D-186: ghosts exempt).
        for (role in listOf(Role.Resident, Role.Insider)) {
            assertTrue(EmitSchema.permits(EmitSchema.STATIC_OPENED, ClientClass(role, RoundState.Live)))
            assertFalse(EmitSchema.permits(EmitSchema.STATIC_OPENED, ClientClass(role, RoundState.Out)), "the jam reaches an out $role")
        }
        // And an out phone handed one anyway raises nothing.
        val couch = phone(ScreenId.Ghost3, out = OutBy.Revoked)
        couch.houseSaid(RoundMessage.StaticOpened(10, 7L))
        assertNull(couch.state.quietUp)
        assertTrue(couch.state.houseTexts.none { it.body == "MONITORING JAMMED" })
    }

    @Test
    fun `the floor strip the playback and the button read the same for an Insider and a Resident`() {
        fun drive(role: PanelRole): List<Any?> {
            sent.clear()
            val model = phone(ScreenId.TermLive, role)
            model.occupancySaid(reading(readyIn = 41, history = 60))
            val cooling = PanelVals(model.state).timelapseLabel
            model.occupancySaid(reading())
            model.showStorey(1)
            model.playTimelapse()
            model.occupancySaid(lapse(6, listOf(false, true, false)))
            model.lapseStepped(model.state.lapseBeat!!, 1)
            val v = PanelVals(model.state)
            return listOf(cooling, v.timelapseLabel, v.timelapseMap, v.timelapseAt, v.timelapsePlayed, v.lapseNoise, model.state.screen, sent.toList())
        }
        assertEquals(drive(PanelRole.Resident), drive(PanelRole.Insider))
    }
}
