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

    private class Phone(role: PanelRole, var now: Long? = 86_409_500L) {
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
        val stamps = listOf(86_408_020L, 86_409_480L, 86_411_010L, 86_412_495L)
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
        // Stamped 0, which is no pulse of the grid, so each tap is a miss on its own and goes up as
        // an attempt for the house to fail (D-298); no pip lights and the screen does not move.
        val phone = Phone(PanelRole.Resident, now = null)
        phone.model.houseSaid(egressOpened())
        phone.scan(west)
        repeat(2) { phone.model.actions().tapPulse() }
        assertEquals(List<IntentMessage>(2) { IntentMessage.ReturnSyncPulse(listOf(NO_HOUSE_CLOCK)) }, phone.sent)
        assertEquals(0, PanelVals(phone.model.state).pulseTaps, "a tap on no pulse lit a pip")
        assertEquals(ScreenId.EgressWidget, phone.model.state.screen, "the phone moved on a tap it could not stamp")
    }

    // ---- Four in a row (the owner's O3), the lockout (O4) and the felt pulse (O2), D-298 --------

    private fun pulse(n: Long): Long = pulseFrom + n * Balance.SYNC_PULSE_INTERVAL_MILLIS

    private fun Phone.tapAt(at: Long) {
        now = at
        model.actions().tapPulse()
    }

    private fun atWest(role: PanelRole = PanelRole.Resident) = Phone(role).apply {
        model.houseSaid(egressOpened())
        scan(west)
    }

    /**
     * **The pips are the run, and a miss empties them** (O3): three hits light three; a fourth tap
     * off the beat empties them and sends the run with the tap that broke it, for the house to fail.
     * A second tap on one pulse is a miss the same way.
     */
    @Test
    fun `a tap off the beat empties the pips and sends the broken run`() {
        val phone = atWest()
        val hits = listOf(pulse(10) + 30, pulse(11) - 250, pulse(12) + 399)
        for ((i, at) in hits.withIndex()) {
            phone.tapAt(at)
            assertEquals(i + 1, PanelVals(phone.model.state).pulseTaps, "hit ${i + 1} did not light its pip")
        }
        assertTrue(phone.sent.isEmpty(), "a request went up before the run broke")
        val off = pulse(13) - Balance.SYNC_PULSE_WINDOW_MILLIS - 1
        phone.tapAt(off)
        assertEquals(0, PanelVals(phone.model.state).pulseTaps, "a tap off the beat left the pips lit")
        assertEquals(listOf<IntentMessage>(IntentMessage.ReturnSyncPulse(hits + off)), phone.sent)

        val twice = atWest()
        twice.tapAt(pulse(10))
        twice.tapAt(pulse(10) + 200)
        assertEquals(0, PanelVals(twice.model.state).pulseTaps, "two taps on one pulse stood as a run of two")
        assertEquals(listOf<IntentMessage>(IntentMessage.ReturnSyncPulse(listOf(pulse(10), pulse(10) + 200))), twice.sent)
    }

    /**
     * **A pulse gone by untapped is a miss** (O3, *a miss starts the count again*): two hits, then
     * the next pulse's window closes with no tap; the second hand empties the pips and sends the two.
     * Inside the window nothing moves; with no run under way, a pulse going by sends nothing.
     */
    @Test
    fun `a pulse going by untapped empties the pips at the next second and sends the run`() {
        val phone = atWest()
        phone.tapAt(pulse(20))
        phone.tapAt(pulse(21))
        phone.now = pulse(22) + Balance.SYNC_PULSE_WINDOW_MILLIS
        phone.model.secondPassed()
        assertEquals(2, PanelVals(phone.model.state).pulseTaps, "the run broke while the pulse's window was still open")
        phone.now = pulse(22) + Balance.SYNC_PULSE_WINDOW_MILLIS + 1
        phone.model.secondPassed()
        assertEquals(0, PanelVals(phone.model.state).pulseTaps, "a pulse gone by untapped left the pips lit")
        assertEquals(listOf<IntentMessage>(IntentMessage.ReturnSyncPulse(listOf(pulse(20), pulse(21)))), phone.sent)

        phone.now = pulse(40)
        repeat(3) { phone.model.secondPassed() }
        assertEquals(1, phone.sent.size, "a pulse going by with no run under way sent something: ${phone.sent}")
    }

    /**
     * **Three hits, a miss, then four hits** (O3): the miss sends the three; the four after it go up
     * as one run of four, the second request, and the only one the house can call four in a row.
     */
    @Test
    fun `three hits a miss and four hits send the broken run and then the four`() {
        val phone = atWest()
        listOf(0L, 1L, 2L).forEach { phone.tapAt(pulse(it)) }
        phone.now = pulse(3) + Balance.SYNC_PULSE_WINDOW_MILLIS + 1
        phone.model.secondPassed()
        listOf(4L, 5L, 6L, 7L).forEach { phone.tapAt(pulse(it)) }
        assertEquals(
            listOf<IntentMessage>(
                IntentMessage.ReturnSyncPulse(listOf(0L, 1L, 2L).map(::pulse)),
                IntentMessage.ReturnSyncPulse(listOf(4L, 5L, 6L, 7L).map(::pulse)),
            ),
            phone.sent,
        )
    }

    /**
     * **The failed answer starts the 3 s lockout on this phone** (O4): the pips empty, a tap in it
     * lights nothing and sends nothing, the felt pulse waits (`pulseFelt`, null), and OFF THE BEAT is
     * the only line, no WAIT. After the 3 s the control counts again and the pulse is felt again. A
     * good answer starts nothing.
     */
    @Test
    fun `a failed answer holds the control and the felt pulse for the lockout and no longer`() {
        val phone = atWest()
        assertEquals(pulseFrom, phone.model.pulseFelt(), "a live control did not feel the pulse")
        phone.tapAt(pulse(10))
        phone.model.houseSaid(RoundMessage.SyncPulseAnswered(held = false))
        assertEquals(0, PanelVals(phone.model.state).pulseTaps, "the failed answer left the pips lit")
        assertEquals(false, phone.model.state.beatHeld)
        for (second in 0 until 3) {
            assertTrue(phone.model.lockingOut(), "the lockout ended after $second s")
            assertNull(phone.model.pulseFelt(), "the felt pulse cut into the lockout at $second s")
            phone.tapAt(pulse(12L + second))
            assertEquals(0, PanelVals(phone.model.state).pulseTaps, "a tap in the lockout lit a pip")
            phone.model.secondPassed()
        }
        assertTrue(phone.sent.isEmpty(), "a tap in the lockout sent something: ${phone.sent}")
        assertFalse(phone.model.lockingOut(), "the lockout outlived its 3 s")
        assertEquals(pulseFrom, phone.model.pulseFelt(), "the felt pulse did not come back after the lockout")
        phone.tapAt(pulse(16))
        assertEquals(1, PanelVals(phone.model.state).pulseTaps, "the count did not start again after the lockout")

        val good = atWest()
        good.model.houseSaid(RoundMessage.SyncPulseAnswered(held = true))
        assertFalse(good.model.lockingOut(), "a good answer started a lockout")
        assertEquals(pulseFrom, good.model.pulseFelt())
    }

    /** **O2: only a phone with a live control feels the pulse**: away from a node, paused, contained. */
    @Test
    fun `only a phone whose control is live feels the pulse`() {
        val away = Phone(PanelRole.Resident).apply { model.houseSaid(egressOpened()); scan(elsewhere) }
        assertNull(away.model.pulseFelt(), "a phone away from the nodes felt the pulse")
        val none = Phone(PanelRole.Resident)
        assertNull(none.model.pulseFelt(), "a phone with no Egress felt the pulse")
        val paused = atWest().apply {
            model.houseSaid(RoundMessage.EgressHeld(remaining = 1_000, running = false, haptic = Haptic.Short))
        }
        assertNull(paused.model.pulseFelt(), "a paused Egress was felt")
        val contained = atWest().apply { model.houseSaid(RoundMessage.EgressContained(Haptic.Short)) }
        assertNull(contained.model.pulseFelt(), "a contained Egress was felt")
    }

    /** **The Insider's phone and the Resident's are the same control**, tap for tap, for every step above. */
    @Test
    fun `an Insider's run and a Resident's send the same requests and hold the same pips`() {
        val walked = PanelRole.entries.map { role ->
            val phone = atWest(role)
            val pips = mutableListOf<Int>()
            listOf(pulse(0), pulse(1), pulse(2) + 500, pulse(4), pulse(5)).forEach {
                phone.tapAt(it); pips += PanelVals(phone.model.state).pulseTaps
            }
            phone.model.houseSaid(RoundMessage.SyncPulseAnswered(held = false))
            phone.tapAt(pulse(6)); pips += PanelVals(phone.model.state).pulseTaps
            repeat(3) { phone.model.secondPassed() }
            listOf(8L, 9L, 10L, 11L).forEach { phone.tapAt(pulse(it)); pips += PanelVals(phone.model.state).pulseTaps }
            phone.sent.toList() to pips
        }
        assertEquals(walked[0], walked[1], "an Insider's control is not a Resident's")
        assertEquals(listOf(1, 2, 0, 1, 2, 0, 1, 2, 3, 0), walked[0].second)
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
