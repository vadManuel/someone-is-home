package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineInstance
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **`you're up` stands over the screen it lands on** (D-201, D-322, D-324; D-327, provisional:
 * D-319's choice for MONITORING JAMMED, made the rule for every quiet text the round sends).
 *
 * The overnight review of 2026-11-22 found it walking a busy phone to page 1: a Subroutine lost its
 * attempt (D-110: STOP NOW is its only exit), the Terminal sent `LeaveTerminal` and freed the station
 * (D-203 lists four ways, and a text is not one), and the scan, the caught scan and Memory Dump lost
 * their screens. Each case here is walked to its screen as a phone walks, for both roles, and the
 * text lands: the screen is kept, nothing is sent, and the banner is drawn over the screen it
 * landed on. Page 1, page 2 and the lock screen keep the landing they always had.
 */
class YoureUpStandsOverTest {

    private val study = MarkerId("card-study")
    private val hall = MarkerId("card-hall")

    private class Phone(val model: FlowModel, val sent: MutableList<IntentMessage>)

    private fun phone(role: PanelRole, screen: ScreenId = ScreenId.Home): Phone {
        val sent = mutableListOf<IntentMessage>()
        val model = FlowModel(
            PanelState(screen = screen, role = role, houseAttached = true),
            send = { sent += it },
            subroutines = SubroutineModel(send = { sent += it }),
        )
        return Phone(model, sent)
    }

    private fun Phone.dealt(kind: SubroutineKind) {
        val at = if (kind == SubroutineKind.MemoryDump) {
            listOf(Destination(study, shape = "diamond", room = "STUDY"), Destination(hall, shape = "circle", room = "HALL"))
        } else {
            listOf(Destination(study, shape = "diamond", room = "STUDY"))
        }
        model.houseSaid(RoundMessage.WorkOrderIssued(listOf(OrderLine.Known(0, AssignedWork.Subroutine(kind), false, at), OrderLine.Blocked(1))))
        model.scanning(study)
        model.houseSaid(RoundMessage.ScanAnswered(SubroutineInstance(0, kind, SubroutineModel.benchParameters(kind))))
    }

    /** The text lands; the screen, what was sent and the motion budget are as they were, and the banner is drawn. */
    private fun Phone.youreUpKeeps(screen: ScreenId, what: String, message: RoundMessage = RoundMessage.WorkUnblocked(Haptic.Short)) {
        assertEquals(screen, model.state.screen, "$what: the arrangement is wrong")
        val measuring = model.motion.running
        sent.clear()
        model.houseSaid(message)
        assertEquals(emptyList(), sent, "$what: the text sent something up")
        assertEquals(screen, model.state.screen, "$what: the text walked the player off the screen")
        assertEquals(measuring, model.motion.running, "$what: the text moved the motion budget")
        val arrival = assertNotNull(PanelVals(model.state).arrival, "$what: no banner is drawn over the screen")
        assertEquals(screen, arrival.under, "$what: the banner is drawn over another screen")
        assertEquals(Presentation.Banner, arrival.presentation, "$what: the text is not the banner")
        assertTrue(!arrival.notification.kind.heavy, "$what: a quiet text dims the house (D-118)")
        if (message is RoundMessage.WorkUnblocked) {
            assertEquals("you're up", arrival.notification.body, "$what: the banner is not `you're up`")
            assertEquals("NUMBER WITHHELD", arrival.notification.from, "$what: the banner's sender moved (D-201)")
        }
    }

    @Test
    fun `you're up over the live view keeps the reader at the station`() {
        for (role in PanelRole.entries) {
            val p = phone(role)
            p.model.houseSaid(RoundMessage.TerminalAnswered(reading = true))
            p.youreUpKeeps(ScreenId.TermLive, "$role on the live view")
        }
    }

    @Test
    fun `you're up over a timelapse keeps the playback`() {
        for (role in PanelRole.entries) {
            val p = phone(role, ScreenId.Timelapse)
            p.youreUpKeeps(ScreenId.Timelapse, "$role on the timelapse")
        }
    }

    @Test
    fun `you're up over a Subroutine keeps the work on screen`() {
        for (role in PanelRole.entries) {
            for (kind in listOf(SubroutineKind.Jam, SubroutineKind.Handshake, SubroutineKind.Sniff)) {
                val p = phone(role)
                p.dealt(kind)
                p.model.actions().beginSubroutine()
                val screen = p.model.state.screen
                assertNotNull(Subroutine.on(screen), "$role $kind: BEGIN opened no Subroutine")
                p.youreUpKeeps(screen, "$role on $kind")
            }
        }
    }

    @Test
    fun `you're up over Memory Dump's playback keeps it`() {
        for (role in PanelRole.entries) {
            val p = phone(role)
            p.dealt(SubroutineKind.MemoryDump)
            p.model.actions().beginSubroutine()
            p.youreUpKeeps(ScreenId.SubMemory, "$role on Memory Dump")
        }
    }

    @Test
    fun `you're up over the caught scan keeps BEGIN`() {
        for (role in PanelRole.entries) {
            val p = phone(role)
            p.dealt(SubroutineKind.Jam)
            p.youreUpKeeps(ScreenId.ScanCaught, "$role on the caught scan")
            p.model.actions().beginSubroutine()
            assertEquals(ScreenId.SubJam, p.model.state.screen, "$role: BEGIN under the banner did not open the work the card dealt")
            assertNull(p.model.state.quietUp, "$role: the banner outlived the screen it stood over")
        }
    }

    @Test
    fun `you're up over the scan keeps the camera`() {
        for (role in PanelRole.entries) {
            val p = phone(role, ScreenId.Scan)
            p.youreUpKeeps(ScreenId.Scan, "$role on the scan")
        }
    }

    @Test
    fun `a later house text over a Subroutine stands over it too`() {
        for (role in PanelRole.entries) {
            val p = phone(role)
            p.dealt(SubroutineKind.Jam)
            p.model.actions().beginSubroutine()
            p.youreUpKeeps(ScreenId.SubJam, "$role, a house text on Jam", RoundMessage.MessageDelivered("THE STUDY IS QUIET"))
            assertEquals("THE STUDY IS QUIET", PanelVals(p.model.state).arrival?.notification?.body)
        }
    }

    @Test
    fun `the swipe takes the banner down and leaves the work where it was`() {
        for (role in PanelRole.entries) {
            val p = phone(role)
            p.dealt(SubroutineKind.Jam)
            p.model.actions().beginSubroutine()
            p.youreUpKeeps(ScreenId.SubJam, "$role on Jam")
            p.model.dismissNotification()
            assertEquals(ScreenId.SubJam, p.model.state.screen, "$role: the swipe walked the player off the work")
            assertNull(p.model.state.quietUp, "$role: the swipe left the banner up")
            assertEquals(emptyList(), p.sent, "$role: the swipe sent something up")
        }
    }

    @Test
    fun `page 1 and page 2 keep the banner over page 1 and the lock screen keeps it under the clock`() {
        for (role in PanelRole.entries) {
            for (page in listOf(ScreenId.Home, ScreenId.Page2)) {
                val p = phone(role, page)
                p.model.houseSaid(RoundMessage.WorkUnblocked(Haptic.Short))
                assertEquals(ScreenId.Quiet, p.model.state.screen, "$role on $page: the banner over page 1 moved")
                assertEquals("you're up", PanelVals(p.model.state).arrival?.notification?.body)
            }
            val locked = phone(role, ScreenId.Lock)
            locked.model.houseSaid(RoundMessage.WorkUnblocked(Haptic.Short))
            assertEquals(ScreenId.LockNotify, locked.model.state.screen, "$role: the lock screen no longer takes it under the clock")
        }
    }

    @Test
    fun `a screen the house is driving still waits`() {
        for (role in PanelRole.entries) {
            for (screen in listOf(ScreenId.Discussion, ScreenId.Vote, ScreenId.Report)) {
                val p = phone(role, screen)
                p.model.houseSaid(RoundMessage.WorkUnblocked(Haptic.Short))
                assertEquals(screen, p.model.state.screen, "$role: `you're up` moved a phone on $screen")
                assertNull(p.model.state.quietUp, "$role: `you're up` was drawn over $screen")
            }
        }
    }
}
