package home.someoneshome.ui

import home.someoneshome.model.Haptic
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The Terminal is a station you scan, and leaving it is the reading ending** (D-199, D-203).
 *
 * Two halves of one phone's part in the lock. The house's answer to a scan of the T card moves the
 * phone and decides nothing; every way off the station's two screens tells the house, once, and
 * moving between them tells it nothing.
 */
class TerminalStationTest {

    private class Wire {
        val sent = mutableListOf<IntentMessage>()
        val leaves: Int get() = sent.count { it == IntentMessage.LeaveTerminal }
    }

    private fun phone(screen: ScreenId, wire: Wire = Wire()): FlowModel =
        FlowModel(PanelState(screen = screen), send = { wire.sent += it })

    @Test
    fun `the house saying you are reading opens the live view`() {
        val model = phone(ScreenId.Scan)
        model.houseSaid(RoundMessage.TerminalAnswered(reading = true))
        assertEquals(ScreenId.TermLive, model.state.screen)
    }

    @Test
    fun `the house saying somebody else is reading is the third scan refusal`() {
        val model = phone(ScreenId.Scan)
        model.houseSaid(RoundMessage.TerminalAnswered(reading = false))
        assertEquals(ScreenId.TermBusy, model.state.screen)
    }

    /**
     * **Every way off the station says so, exactly once.** BACK to the springboard, and the house
     * pushing a meeting over the timelapse, are the two kinds of exit: one the player's and one the
     * house's. Both pass through `FlowModel.arrive`, which is the point.
     */
    @Test
    fun `leaving the station by any route sends one leave`() {
        val tapped = Wire()
        phone(ScreenId.TermLive, tapped).go(ScreenId.Home)
        assertEquals(listOf<IntentMessage>(IntentMessage.LeaveTerminal), tapped.sent)

        val pushed = Wire()
        val model = phone(ScreenId.TermLive, pushed)
        model.go(ScreenId.Timelapse)
        model.houseSaid(RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Long))
        assertEquals(1, pushed.leaves, "a meeting pushed over the timelapse did not free the station")
    }

    @Test
    fun `moving between the live view and the timelapse is still reading`() {
        val wire = Wire()
        val model = phone(ScreenId.TermLive, wire)
        model.go(ScreenId.Timelapse)
        model.go(ScreenId.TermLive)
        assertEquals(0, wire.leaves, "the reader was let go for watching the playback")
    }

    @Test
    fun `a phone nowhere near the station never says it left`() {
        val wire = Wire()
        val model = phone(ScreenId.Home, wire)
        model.go(ScreenId.Work)
        model.go(ScreenId.Scan)
        model.houseSaid(RoundMessage.TerminalAnswered(reading = false))
        assertEquals(0, wire.leaves)
    }
}
