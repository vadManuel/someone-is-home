package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-200: three places the build had drifted from a ruling, and the ruling wins in each.**
 *
 * The reconnecting screen dims with the other concealment screens (D-077), and the house is one
 * conversation in Messages with all four of its texts in the thread. The third, the scan ring's
 * pulse, is a render fact and is checked by rendering it.
 */
class StandingAgainstTheBuildTest {

    private fun vals(screen: ScreenId, role: PanelRole = PanelRole.Resident) =
        PanelVals(PanelState(screen = screen, role = role))

    @Test
    fun `the reconnecting screen dims its chrome with the revoked screen`() {
        for (screen in listOf(ScreenId.Revoked, ScreenId.Disconnect)) {
            assertEquals(Amber.Dim, vals(screen).ink, "$screen is lit above the concealment step")
            assertEquals(Amber.Faint, vals(screen).dim, "$screen is lit above the concealment step")
        }
        assertEquals(Amber.Bright, vals(ScreenId.Home).ink, "the springboard was dimmed with them")
    }

    @Test
    fun `the house is one row in Messages`() {
        for (role in PanelRole.entries) {
            val rows = vals(ScreenId.Reveal, role).inbox.filter { it.from == "HOUSE" }
            assertEquals(1, rows.size, "$role: the house drawn as ${rows.size} conversations")
            assertEquals("21:02", rows.single().at, "the house row is not its newest text")
        }
    }

    @Test
    fun `the thread holds all four texts and the header counts messages`() {
        val resident = vals(ScreenId.RevealThread, PanelRole.Resident)
        val insider = vals(ScreenId.RevealThread, PanelRole.Insider)
        assertEquals(4, resident.houseThread.size)
        assertTrue(
            "21:01" to "Do not attempt the exterior doors." in resident.houseThread,
            "the 21:01 text is not in the thread: ${resident.houseThread}",
        )
        assertEquals(
            resident.houseThread.dropLast(1), insider.houseThread.dropLast(1),
            "the thread differs by role above its newest line",
        )
        assertEquals(resident.inbox.size - 1 + 4, resident.inboxCount)
    }
}
