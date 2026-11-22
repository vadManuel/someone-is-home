package home.someoneshome.ui

import home.someoneshome.model.Balance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.Haptic
import home.someoneshome.model.protocol.RoundMessage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Two phones' clocks, run by `FlowHost` itself, agree however often either redraws** (V4 of the
 * second review, D-269).
 *
 * Page 2's bars are public (D-239): every phone restarts them at moments every phone is told, so an
 * Insider's page and a Resident's draw the same bar at the same moment (D-142). They were counted a
 * second at a time by a `LaunchedEffect` keyed on the clocks' own values, which restarted on any
 * change to any of them: the Insider's own `AbilityFired` (sent to nobody else, D-262) restarts its
 * Access bar, and every restart threw away the part-second its Revoke bar had run. The Insider's
 * Revoke bar fell behind the Resident's at every fire, which is page 2 differing by role.
 *
 * Two real [FlowModel]s, each in its own real [FlowHost] in one composition, with the composition's
 * own clock as each phone's monotonic clock. Both are sent the same pushes at the same moments; the
 * Insider is also sent the answers to its own fires, at moments that fall between seconds.
 */
@OptIn(ExperimentalTestApi::class)
class ClockParityTest {

    /**
     * **GUARD (V4, D-269): an Insider's Revoke clock and a Resident's read the same second at every
     * moment, through four of the Insider's own fires, each answered half way through a second.**
     */
    @Test
    fun anInsidersRevokeClockKeepsTimeWithAResidentsThroughItsOwnFires() = runDesktopComposeUiTest(width = 760, height = 800) {
        mainClock.autoAdvance = false
        val clock = { mainClock.currentTime }
        val insider = FlowModel(PanelState(screen = ScreenId.Lobby), monotonicMillis = clock)
        val resident = FlowModel(PanelState(screen = ScreenId.Lobby), monotonicMillis = clock)
        insider.houseSaid(RoundMessage.RoleRevealed(insider = true))
        resident.houseSaid(RoundMessage.RoleRevealed(insider = false))
        setContent {
            Row {
                Box(Modifier.size(360.dp, 760.dp)) { DeviceCanvas(insets = PanelInsets()) { FlowHost(insider) } }
                Box(Modifier.size(360.dp, 760.dp)) { DeviceCanvas(insets = PanelInsets()) { FlowHost(resident) } }
            }
        }
        mainClock.advanceTimeBy(100)

        // LIGHTS OUT on both, at the same moment.
        listOf(insider, resident).forEach { it.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, Balance.REVOKE_COOLDOWN)) }
        val seconds = PublicCooldown.Revoke.seconds
        var worst = 0
        val readings = mutableListOf<String>()
        fun compare(at: String) {
            val a = insider.state.revokeClock?.elapsed ?: -1
            val b = resident.state.revokeClock?.elapsed ?: -1
            readings += "$at: ${a}s/${b}s"
            worst = maxOf(worst, abs(a - b))
        }

        // Four fires, each answered half way through a second: the Insider's Access bar restarts
        // and the Resident's does not.
        repeat(4) { fire ->
            mainClock.advanceTimeBy(2_500)
            insider.fireSurge(0)
            insider.houseSaid(RoundMessage.AbilityFired(cooldownStarted = true))
            compare("fire ${fire + 1}")
            repeat(3) {
                mainClock.advanceTimeBy(330)
                compare("after fire ${fire + 1}")
            }
        }
        // And on to the end of the Revoke clock.
        while ((resident.state.revokeClock?.running == true) || (insider.state.revokeClock?.running == true)) {
            mainClock.advanceTimeBy(250)
            compare("running out")
            if (mainClock.currentTime > 120_000) break
        }
        println("V4 CLOCKS — WORST GAP ${worst}s · ${readings.takeLast(6)}")
        assertEquals(0, worst, "the Insider's Revoke clock and the Resident's read different seconds: $readings")
        assertEquals(seconds, insider.state.revokeClock?.elapsed, "the Insider's Revoke clock never ran out")
        assertEquals(seconds, resident.state.revokeClock?.elapsed, "the Resident's Revoke clock never ran out")
        assertTrue(insider.state.accessClock != resident.state.accessClock, "the Insider's fires restarted nothing, so nothing was measured")
    }

    /**
     * **GUARD (V4, D-269): a clock reads the whole seconds since its push, not a count of waits**:
     * after a full Revoke clock of composition time, the bar is full exactly when its seconds have
     * passed, neither before nor a second after.
     */
    @Test
    fun aClockRunsOutWhenItsSecondsHavePassed() = runDesktopComposeUiTest(width = 360, height = 760) {
        mainClock.autoAdvance = false
        val phone = FlowModel(PanelState(screen = ScreenId.Lobby), monotonicMillis = { mainClock.currentTime })
        phone.houseSaid(RoundMessage.RoleRevealed(insider = false))
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(phone) } }
        mainClock.advanceTimeBy(100)
        phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, Balance.REVOKE_COOLDOWN))
        val seconds = PublicCooldown.Revoke.seconds
        mainClock.advanceTimeBy(seconds * 1_000L - 200)
        assertTrue(phone.state.revokeClock?.running == true, "the Revoke clock ran out before its ${seconds}s")
        mainClock.advanceTimeBy(400)
        assertEquals(false, phone.state.revokeClock?.running, "the Revoke clock was still running after its ${seconds}s")
    }
}
