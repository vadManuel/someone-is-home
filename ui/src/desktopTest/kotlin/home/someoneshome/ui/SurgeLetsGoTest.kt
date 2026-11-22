package home.someoneshome.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Haptic
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **D-323 E: a Surge lets go of every hold already under way, and closes the camera, at the slam**
 * (the owner's 2026-11-22 ruling, *cancel both*).
 *
 * [SurgeTakesNoInputTest] holds new fingers off the glass and lets go of the card; what is checked
 * here is a gesture that began **before** the slam and is still going when it lands: a Revoke or
 * an Egress arm held on page 2 (real pointers, through [FlowHost], which hands the Surge to the
 * screen), the report circle (the house's hold, ended by the phone leaving the main view), and the
 * camera's scan window (closed by walking off the scan screen, which is what plays its *no*).
 * Each hold is first shown firing without a Surge, so a hold that had stopped working altogether
 * could not pass.
 */
@OptIn(ExperimentalTestApi::class)
class SurgeLetsGoTest {

    private val surge = RoundMessage.Surged(peak = 100, black = 0, haptic = Haptic.Short)

    /** A full hold, with frames to spare. */
    private val enough = HOLD_MILLIS + 200L

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }
        mainClock.advanceTimeByFrame()
    }

    private fun insiderOnPage2(sent: MutableList<IntentMessage>, fired: () -> Unit = {}): FlowModel =
        FlowModel(PanelState(screen = ScreenId.Home, role = PanelRole.Insider), send = { sent += it }, fireEgress = fired)
            .also { it.push(ScreenId.Page2) }

    @Test
    fun revokeArmsOnAFullHoldWithNoSurge() = runDesktopComposeUiTest(width = 300, height = 650) {
        val sent = mutableListOf<IntentMessage>()
        val phone = insiderOnPage2(sent)
        show(phone)
        onNodeWithText("REVOKE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertEquals(listOf<IntentMessage>(IntentMessage.ArmRevoke), sent.toList(), "the control: a full hold did not arm")
    }

    /** **GUARD: a Revoke arm half held when the Surge slams is let go and never arms.** */
    @Test
    fun aRevokeArmUnderWayIsLetGoAtTheSlam() = runDesktopComposeUiTest(width = 300, height = 650) {
        val sent = mutableListOf<IntentMessage>()
        val phone = insiderOnPage2(sent)
        show(phone)
        val before = phone.state.revoke
        onNodeWithText("REVOKE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(HOLD_MILLIS / 2L)
        phone.houseSaid(surge)
        // The finger stays down well past where the two seconds would have run out.
        mainClock.advanceTimeBy(enough)
        assertTrue(sent.none { it is IntentMessage.ArmRevoke }, "a Revoke arm held through the slam armed: $sent")
        assertEquals(before, phone.state.revoke, "the tile echoed ARMED for a hold the Surge let go")
    }

    @Test
    fun egressFiresOnAFullHoldWithNoSurge() = runDesktopComposeUiTest(width = 300, height = 650) {
        var fired = 0
        val phone = insiderOnPage2(mutableListOf()) { fired++ }
        show(phone)
        onNodeWithText("EGRESS").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertEquals(1, fired, "the control: a full hold did not fire the Egress")
    }

    /** **GUARD: an Egress arm half held when the Surge slams is let go and never fires.** */
    @Test
    fun anEgressArmUnderWayIsLetGoAtTheSlam() = runDesktopComposeUiTest(width = 300, height = 650) {
        var fired = 0
        val phone = insiderOnPage2(mutableListOf()) { fired++ }
        show(phone)
        onNodeWithText("EGRESS").performTouchInput { down(center) }
        mainClock.advanceTimeBy(HOLD_MILLIS / 2L)
        phone.houseSaid(surge)
        mainClock.advanceTimeBy(enough)
        assertEquals(0, fired, "an Egress arm held through the slam fired")
    }

    /**
     * **GUARD: the report circle is let go at the slam.** The report screen is left in the slam's
     * own call, the phone says it is off the main view (so the house ends the hold on the next
     * upload, D-307's path), a slot the house sent before it heard opens nothing, and the phone is
     * on the main view again once the Surge is over.
     */
    @Test
    fun theReportCircleIsLetGoAtTheSlam() = runDesktopComposeUiTest(width = 300, height = 650) {
        val phone = FlowModel(PanelState(screen = ScreenId.Home))
        show(phone)
        phone.reportSaid(ReportSlot.Pending(600))
        assertEquals(ScreenId.Report, phone.state.screen, "the fixture never opened the report circle")
        assertTrue(phone.onMainView)

        phone.houseSaid(surge)
        assertEquals(ScreenId.Home, phone.state.screen, "the report circle outlived the slam")
        assertFalse(phone.onMainView, "a surged phone still told the house it could report")
        assertEquals(0f, phone.report.fill.floatValue, "the circle kept its fill through the slam")

        phone.reportSaid(ReportSlot.Pending(900))
        assertTrue(phone.state.screen != ScreenId.Report, "a slot sent before the house heard reopened the circle")
        mainClock.advanceTimeBy(SURGE_PEAK_MS + SURGE_BLACK_MS + SURGE_BOOT_MS + 200L)
        assertEquals(ScreenId.Home, phone.state.screen)
        assertTrue(phone.onMainView, "the phone never came back to the main view after the Surge")
    }

    /**
     * **GUARD: the camera's scan window closes at the slam**, not at the reboot. The scan screen is
     * left in the slam's own call; leaving it is what closes the window and plays D-160's *no* in
     * the app (the in-round scan's `onDispose`), so the double buzz is at the slam.
     */
    @Test
    fun theScanWindowClosesAtTheSlam() = runDesktopComposeUiTest(width = 300, height = 650) {
        val phone = FlowModel(PanelState(screen = ScreenId.Home)).also { it.push(ScreenId.Scan) }
        show(phone)
        assertEquals(ScreenId.Scan, phone.state.screen)
        phone.houseSaid(surge)
        assertEquals(ScreenId.Home, phone.state.screen, "the camera's window stayed open past the slam")
        mainClock.advanceTimeBy(SURGE_PEAK_MS / 2L)
        assertTrue(phone.state.screen != ScreenId.Scan, "the scan screen came back during the Surge")
    }
}
