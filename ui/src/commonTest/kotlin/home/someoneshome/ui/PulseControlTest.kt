package home.someoneshome.ui

import home.someoneshome.model.Balance
import home.someoneshome.model.Destination
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The tap control after the node scan** (D-295, the owner's Q2 ruling, option B).
 *
 * A phone whose person scans one of the running Egress's two node cards lands on the Egress widget
 * with a live tap control, whatever the house's answer held and whatever the phone's role; every
 * tap is stamped on the house's clock as this phone measures it, the pips are input echo, and the
 * fourth tap sends the four up as one `ReturnSyncPulse`. The house grades them; the phone decides
 * nothing about the beat.
 */
class PulseControlTest {

    private val west = MarkerId("west-card")
    private val east = MarkerId("east-card")
    private val elsewhere = MarkerId("hall-card")
    private val pulseFrom = 86_402_000L

    private fun egressOpened() = RoundMessage.EgressOpened(
        type = EgressType.Beacon,
        nodes = listOf(Destination(west, "circle", "UTILITY"), Destination(east, "square", "LANDING")),
        remaining = 2_400,
        haptic = Haptic.Long,
        pulseFrom = pulseFrom,
    )

    private class Phone(role: PanelRole, var now: Long? = 86_410_000L) {
        val sent = mutableListOf<IntentMessage>()
        val model = FlowModel(
            PanelState(screen = ScreenId.Home, role = role, houseAttached = true),
            send = { sent += it },
            houseNow = { now },
        )
    }

    private fun Phone.scan(card: MarkerId) {
        model.scanning(card)
        model.houseSaid(RoundMessage.ScanAnswered(null))
    }

    @Test
    fun `a node scan during a running Egress lands on the widget with the control live and for either role`() {
        val screens = PanelRole.entries.map { role ->
            val phone = Phone(role)
            phone.model.houseSaid(egressOpened())
            phone.scan(east)
            assertEquals(ScreenId.EgressWidget, phone.model.state.screen, "$role: a node scan did not reach the control")
            assertEquals(east, phone.model.state.pulseAt)
            assertTrue(PanelVals(phone.model.state).pulseLive, "$role: the control is not live at a node")
            assertEquals(pulseFrom, phone.model.state.pulseFrom, "$role: the grid was not kept")
            phone.model.state.screen
        }
        assertEquals(1, screens.toSet().size, "the control follows the role: $screens")
    }

    @Test
    fun `any other scan and a scan before the Egress and a scan while it is held do not`() {
        val other = Phone(PanelRole.Resident).apply {
            model.houseSaid(egressOpened())
            scan(elsewhere)
        }
        assertEquals(ScreenId.ScanBad, other.model.state.screen)
        assertNull(other.model.state.pulseAt, "a card that is not a node opened the control")

        val before = Phone(PanelRole.Resident).apply { scan(east) }
        assertEquals(ScreenId.ScanBad, before.model.state.screen)
        assertNull(before.model.state.pulseAt, "a node scanned before any Egress opened the control")

        val held = Phone(PanelRole.Insider).apply {
            model.houseSaid(egressOpened())
            model.houseSaid(RoundMessage.EgressHeld(remaining = 1_000, running = false, haptic = Haptic.Short))
            scan(west)
        }
        assertEquals(ScreenId.ScanBad, held.model.state.screen)
        assertNull(held.model.state.pulseAt, "a node scanned during a pause opened the control")
    }

    @Test
    fun `four taps send the four house-clock stamps up as one request and the pips are the taps`() {
        val phone = Phone(PanelRole.Resident)
        phone.model.houseSaid(egressOpened())
        phone.scan(west)
        val stamps = listOf(86_410_020L, 86_411_480L, 86_413_010L, 86_414_495L)
        for ((i, at) in stamps.withIndex()) {
            phone.now = at
            phone.model.actions().tapPulse()
            if (i < 3) {
                assertEquals(i + 1, PanelVals(phone.model.state).pulseTaps, "tap ${i + 1} did not light its pip")
                assertTrue(phone.sent.isEmpty(), "a request went up before the fourth tap")
            }
        }
        assertEquals(listOf<IntentMessage>(IntentMessage.ReturnSyncPulse(stamps)), phone.sent)
        assertEquals(0, PanelVals(phone.model.state).pulseTaps, "the pips did not empty after the four went up")
        assertEquals(Balance.SYNC_PULSE_BEATS, stamps.size)
    }

    @Test
    fun `a tap with no estimate of the house's clock is stamped on no pulse and nothing says why`() {
        val phone = Phone(PanelRole.Resident, now = null)
        phone.model.houseSaid(egressOpened())
        phone.scan(west)
        repeat(Balance.SYNC_PULSE_BEATS) { phone.model.actions().tapPulse() }
        assertEquals(listOf<IntentMessage>(IntentMessage.ReturnSyncPulse(List(4) { NO_HOUSE_CLOCK })), phone.sent)
        assertEquals(ScreenId.EgressWidget, phone.model.state.screen, "the phone moved on a tap it could not stamp")
    }

    @Test
    fun `a pause and containment and a scan elsewhere each take the control away and a control no longer live sends nothing`() {
        val paused = Phone(PanelRole.Resident).apply {
            model.houseSaid(egressOpened()); scan(west); model.actions().tapPulse()
            model.houseSaid(RoundMessage.EgressHeld(remaining = 1_000, running = false, haptic = Haptic.Short))
        }
        assertNull(paused.model.state.pulseAt, "a meeting's pause left the control live")
        assertEquals(0, paused.model.state.pulseTaps.size)
        // The meeting is over: the house reads no scan from before it (D-279 F3), and neither does this.
        paused.model.houseSaid(RoundMessage.EgressHeld(remaining = 1_000, running = true, haptic = Haptic.Short))
        assertFalse(PanelVals(paused.model.state).pulseLive, "the control came back without a new scan")
        repeat(4) { paused.model.actions().tapPulse() }
        assertTrue(paused.sent.isEmpty(), "a control no longer live sent taps: ${paused.sent}")

        val contained = Phone(PanelRole.Insider).apply {
            model.houseSaid(egressOpened()); scan(east)
            model.houseSaid(RoundMessage.EgressContained(Haptic.Short))
        }
        assertNull(contained.model.state.pulseAt, "containment left the control live")

        val walked = Phone(PanelRole.Resident).apply {
            model.houseSaid(egressOpened()); scan(east); scan(elsewhere)
        }
        assertNull(walked.model.state.pulseAt, "a scan elsewhere left the control live at the node")
    }
}
