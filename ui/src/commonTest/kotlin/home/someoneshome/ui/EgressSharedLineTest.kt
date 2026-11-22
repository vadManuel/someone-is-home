package home.someoneshome.ui

import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Page 2's EGRESS line says the clock is shared only when another Insider shares it** (the owner's
 * 2026-11-21 ruling, D-320: *SHARED WITH THE OTHER INSIDER* hidden when the round has one Insider,
 * shown with two or more; D-151's line).
 *
 * The count is read from two public lobby facts, the party's size and the host's INSIDERS setting,
 * and from nothing the house sent about the draw ([egressSharedBy]). A Resident's page 2 does not
 * move in any of these rounds (D-286: page 2 may differ by role in content, never in structure).
 */
class EgressSharedLineTest {

    private fun page2(insider: Boolean, seats: Int?, chosen: Int?): PanelVals = PanelVals(
        PanelState(
            screen = ScreenId.Page2,
            role = if (insider) PanelRole.Insider else PanelRole.Resident,
            seats = seats,
            insidersChosen = chosen,
        ),
    )

    /** **GUARD: one Insider, and the line is gone; its slot stays, so the tile keeps its height.** */
    @Test
    fun theLineIsHiddenWhenTheRoundHasOneInsider() {
        // Three to six seats hold one Insider whatever the setting (D-103's amendment), and a host
        // who chose one where the band allows one chose one.
        for ((seats, chosen) in listOf(3 to null, 5 to null, 6 to null, 5 to 1, 6 to 3, 8 to 1, 11 to 1)) {
            val vals = page2(insider = true, seats = seats, chosen = chosen)
            assertEquals(
                "", vals.secondSub,
                "one Insider at $seats seats (setting ${chosen ?: "UNKNOWN"}), and page 2 still says the Egress is shared",
            )
            assertEquals("EGRESS", vals.secondName)
        }
    }

    /** **GUARD: two or more Insiders, and the line is there.** */
    @Test
    fun theLineIsShownWhenTheRoundHasTwoOrMoreInsiders() {
        // The host chose two or three, or UNKNOWN in a band that starts at two (twelve seats on); a
        // host's one at twelve seats is clamped to the band's two, as the house draws it.
        for ((seats, chosen) in listOf(8 to 2, 10 to 3, 12 to null, 18 to null, 12 to 2, 12 to 1)) {
            assertEquals(
                "SHARED WITH THE OTHER INSIDER", page2(insider = true, seats = seats, chosen = chosen).secondSub,
                "two or more Insiders at $seats seats (setting ${chosen ?: "UNKNOWN"}), and page 2 hid the shared clock",
            )
        }
    }

    /**
     * **Not settled, and left as it was: UNKNOWN in a band that holds one and more** (seven to eleven
     * seats). No phone knows the count there, so D-320 stopped on it for the owner; the line stays.
     */
    @Test
    fun anUnsettledCountLeavesTheLineAsItWas() {
        for (seats in 7..11) {
            assertTrue(egressSharedBy(seats, chosen = null), "UNKNOWN at $seats seats changed the line on a count nobody holds")
        }
        assertTrue(egressSharedBy(seats = null, chosen = null), "a phone no house has spoken to lost the port's line")
    }

    /** **A Resident's page 2 is unaffected**, in every round above. */
    @Test
    fun aResidentsPageTwoDoesNotMove() {
        val rounds = listOf(3 to null, 5 to 1, 8 to 1, 8 to 2, 8 to null, 12 to null, null to null)
        val drawn = rounds.map { (seats, chosen) ->
            page2(insider = false, seats = seats, chosen = chosen).let { listOf(it.secondName, it.secondSub, it.secondInk) }
        }
        assertEquals(1, drawn.distinct().size, "a Resident's page 2 moved with the Insider count: $drawn")
        assertEquals(listOf("SUBSYS", "ALL SYSTEMS RESPONDING"), drawn.first().take(2))
    }

    /**
     * **The phone copies the host's setting at arming, beside the roster** (D-320): the lobby's
     * standing is what it reads, and the roster's size is the party.
     */
    @Test
    fun theSettingIsCopiedFromTheLobbyAtArming() {
        fun armedIn(chosen: Int?, names: Int): FlowModel {
            val link = MemoryLobbyLink(joined = names, linesIn = 0)
            val lobby = LobbyModel(MemoryHomeFinder(listOf(NearbyHome("THE BUNGALOW", "192.168.1.24", 47747))), link, hosting = true)
            lobby.look()
            lobby.nameResident("ELLIOT")
            lobby.attachTo(lobby.nearby.first())
            lobby.cycleInsidersTo(chosen)
            return FlowModel(PanelState(screen = ScreenId.Page2), lobby = lobby).apply {
                houseSaid(RoundMessage.RoleRevealed(insider = true))
                houseSaid(RoundMessage.RosterIssued(List(names) { "SEAT$it" }))
            }
        }
        val one = armedIn(chosen = 1, names = 8)
        assertEquals(1, one.state.insidersChosen)
        assertEquals("", PanelVals(one.state).secondSub, "the host chose one Insider of eight and the line stayed")
        val two = armedIn(chosen = 2, names = 8)
        assertEquals("SHARED WITH THE OTHER INSIDER", PanelVals(two.state).secondSub)
        val five = armedIn(chosen = null, names = 5)
        assertEquals("", PanelVals(five.state).secondSub, "five seats hold one Insider and the line stayed")
    }

    /** Press the host's INSIDERS row until it reads [chosen] (null is UNKNOWN). */
    private fun LobbyModel.cycleInsidersTo(chosen: Int?) {
        repeat(8) {
            if (standing.insiders == chosen) return
            cycleInsiders()
        }
        assertEquals(chosen, standing.insiders, "the INSIDERS row never reached $chosen")
    }
}
