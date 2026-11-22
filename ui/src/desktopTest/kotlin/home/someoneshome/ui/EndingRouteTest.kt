package home.someoneshome.ui

import home.someoneshome.model.Balance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Haptic
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Each ending says how the round was won, from the route the house sent** (D-261).
 *
 * The Insiders' ending said THE HOUSE REACHED THE INTERNET and EGRESS COMPLETE on every Insider win,
 * so a parity win at the tally (the 4-seat simulator hunt, seed 20261150) was drawn as an Egress.
 * Each of D-131's four routes is fed to a housed phone as the house sends it, in both roles, and
 * the screen it walks to must draw that route's line and no other route's. The unhoused render
 * harness keeps the mockup's two endings.
 */
@OptIn(ExperimentalTestApi::class)
class EndingRouteTest {

    private val now = WallTime(hour = 23, minute = 46, weekday = 2, day = 17, month = 11)

    private fun winnerOf(by: WinRoute): Winner = when (by) {
        WinRoute.SystemIntegrityCleared, WinRoute.InsidersRestrained -> Winner.Residents
        WinRoute.Parity, WinRoute.EgressUncontained -> Winner.Insiders
    }

    /** A housed phone in a round, which the house then ends by [by]. */
    private fun endedBy(by: WinRoute, insider: Boolean): FlowModel =
        FlowModel(PanelState(screen = ScreenId.Home), homes = SavedHomesModel(), wallClock = { now }).also { phone ->
            phone.houseAttached(true)
            phone.houseSaid(RoundMessage.RosterIssued(listOf("ALBA", "BRUNO", "CYRUS", "DEVI")))
            phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, Balance.REVOKE_COOLDOWN))
            phone.houseSaid(RoundMessage.RoleRevealed(insider = insider, line = if (insider) "the cake" else ""))
            phone.houseSaid(RoundMessage.RoundEnded(winnerOf(by), by, Haptic.Short, integrity = 60))
        }

    /** Every piece of text [phone] draws where it stands, drawn the way `FlowHost` draws it. */
    private fun drawn(phone: FlowModel, state: PanelState = phone.state): List<String> {
        var texts: List<String> = emptyList()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        state, phone.actions(), phone.editor, phone.homes, phone.lobby,
                        phone.meeting, phone.subroutines, phone.notifications, perms = phone.perms, lamp = phone.lamp,
                    )
                }
            }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        }
        return texts
    }

    /** **GUARD: every route draws its own line on a housed phone, in both roles, and no other route's.** */
    @Test
    fun eachRouteDrawsItsOwnEnding() {
        val lines = WinRoute.entries.associateWith(PanelVals::endingLine)
        assertEquals(WinRoute.entries.size, lines.values.toSet().size, "two routes share one line: $lines")
        for (by in WinRoute.entries) for (insider in listOf(false, true)) {
            val phone = endedBy(by, insider)
            val who = "$by (${if (insider) "Insider" else "Resident"})"
            assertEquals(Flow.endings.getValue(winnerOf(by)).to, phone.state.screen, "$who walked to the wrong ending")
            assertEquals(by, phone.state.endedBy)
            val texts = drawn(phone)
            assertTrue(lines.getValue(by) in texts, "$who did not draw '${lines.getValue(by)}': $texts")
            for ((other, line) in lines) if (other != by) {
                assertTrue(line !in texts, "$who drew $other's line '$line': $texts")
            }
            val egress = "EGRESS COMPLETE" in texts
            assertEquals(by == WinRoute.EgressUncontained, egress, "$who drew EGRESS COMPLETE = $egress: $texts")
        }
    }

    /** **GUARD: the parity win the hunt found is not an Egress win.** The bug, by name. */
    @Test
    fun aParityWinIsNotAnEgress() {
        val texts = drawn(endedBy(WinRoute.Parity, insider = false))
        assertTrue("INSIDERS WIN" in texts && "TOO FEW RESIDENTS LEFT" in texts, "the parity ending is not drawn: $texts")
        assertTrue(texts.none { "INTERNET" in it || "EGRESS" in it }, "a parity win was drawn as an Egress: $texts")
    }

    /** The route goes with the round: a new round, or the panel between rounds, holds none. */
    @Test
    fun theRouteIsThisRounds() {
        val phone = endedBy(WinRoute.Parity, insider = true)
        phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short, Balance.REVOKE_COOLDOWN))
        assertNull(phone.state.endedBy, "a new round kept the last one's route")
        assertNull(endedBy(WinRoute.Parity, insider = false).state.betweenRounds().endedBy, "NEW ROUND kept the route")
        // A housed ending the house gave no route draws neither design line.
        val unsaid = PanelVals(PanelState(screen = ScreenId.WinInsiders, houseAttached = true))
        assertEquals("", unsaid.insidersEndingLine)
        assertEquals(false, unsaid.egressCompleted)
        assertEquals("", PanelVals(PanelState(screen = ScreenId.WinResidents, houseAttached = true)).residentsEndingLine)
    }

    /** **GUARD: the unhoused render harness still draws the mockup's two endings.** */
    @Test
    fun anUnhousedPhoneDrawsTheMockup() {
        for (role in PanelRole.entries) {
            val insiders = FlowModel(PanelState(screen = ScreenId.WinInsiders, role = role))
            val dark = drawn(insiders)
            assertTrue("THE HOUSE REACHED THE INTERNET" in dark, "the harness lost the Insiders' line: $dark")
            assertTrue("EGRESS COMPLETE" in dark, "the harness lost EGRESS COMPLETE: $dark")
            val residents = FlowModel(PanelState(screen = ScreenId.WinResidents, role = role))
            val light = drawn(residents)
            assertTrue("PERIMETER DISARMED . YOU CAN LEAVE" in light, "the harness lost the Residents' line: $light")
        }
    }
}
