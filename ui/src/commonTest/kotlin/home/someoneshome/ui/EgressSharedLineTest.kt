package home.someoneshome.ui

import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Page 2's EGRESS line says the clock is shared only when another Insider shares it** (the owner's
 * 2026-11-21 ruling, D-320: *SHARED WITH THE OTHER INSIDER* hidden when the round has one Insider,
 * shown with two or more; D-151's line).
 *
 * The count is the one the house tells each Insider's phone on its role reveal (the owner's
 * 2026-11-22 ruling, D-323 B), which replaced D-320's reading of the lobby's public facts and its
 * stop at UNKNOWN in seven to eleven seats. A Resident's page 2 does not move in any of these
 * rounds (D-286: page 2 may differ by role in content, never in structure).
 * `EgressSharedCountTest` (app) walks the same through the real rules and the real draw.
 */
class EgressSharedLineTest {

    /** A phone told its role by the house, with the count the reveal carried. */
    private fun revealed(insider: Boolean, insiders: Int): FlowModel =
        FlowModel(PanelState(screen = ScreenId.Page2)).apply {
            houseSaid(RoundMessage.RoleRevealed(insider = insider, insiders = insiders))
        }

    private fun page2(phone: FlowModel): PanelVals = PanelVals(phone.state.copy(screen = ScreenId.Page2))

    /** **GUARD: one Insider, and the line is gone; its slot stays, so the tile keeps its height.** */
    @Test
    fun theLineIsHiddenWhenTheRoundHasOneInsider() {
        val vals = page2(revealed(insider = true, insiders = 1))
        assertEquals("", vals.secondSub, "one Insider, and page 2 still says the Egress is shared")
        assertEquals("EGRESS", vals.secondName)
    }

    /** **GUARD: two or more Insiders, and the line is there.** */
    @Test
    fun theLineIsShownWhenTheRoundHasTwoOrMoreInsiders() {
        for (count in 2..5) {
            assertEquals(
                "SHARED WITH THE OTHER INSIDER", page2(revealed(insider = true, insiders = count)).secondSub,
                "$count Insiders, and page 2 hid the shared clock",
            )
        }
    }

    /**
     * **The lobby no longer decides it** (D-323 B): the host's setting and the party's size are not
     * read, so UNKNOWN at seven to eleven seats, where D-320 stopped, follows the house's count like
     * every other round.
     */
    @Test
    fun theLobbyDoesNotDecideTheLine() {
        for (seats in 7..11) {
            val one = revealed(insider = true, insiders = 1).apply { houseSaid(RoundMessage.RosterIssued(List(seats) { "SEAT$it" })) }
            val two = revealed(insider = true, insiders = 2).apply { houseSaid(RoundMessage.RosterIssued(List(seats) { "SEAT$it" })) }
            assertEquals("", page2(one).secondSub, "one Insider at $seats seats (UNKNOWN), and the line stayed")
            assertEquals("SHARED WITH THE OTHER INSIDER", page2(two).secondSub, "two Insiders at $seats seats (UNKNOWN), and the line went")
        }
        assertTrue(egressSharedBy(count = null), "a phone no house has spoken to lost the port's line")
    }

    /**
     * **A Resident's page 2 is unaffected, and a Resident's phone holds no count**: the reveal pads
     * a Resident's with zeros and the constructor zeroes whatever it is handed.
     */
    @Test
    fun aResidentsPageTwoDoesNotMoveAndHoldsNoCount() {
        val drawn = (0..3).map { n ->
            val phone = revealed(insider = false, insiders = n)
            assertNull(phone.state.insiderCount, "a Resident's phone holds an Insider count")
            page2(phone).let { listOf(it.secondName, it.secondSub, it.secondInk) }
        }
        assertEquals(1, drawn.distinct().size, "a Resident's page 2 moved with the Insider count: $drawn")
        assertEquals(listOf("SUBSYS", "ALL SYSTEMS RESPONDING"), drawn.first().take(2))
        assertEquals(0, RoundMessage.RoleRevealed(insider = false, insiders = 3).insiders, "a Resident's reveal carried the count")
    }

    /** **The reveal stays one width for both roles and every count** (D-249). */
    @Test
    fun theRevealIsOneWidthForEveryRoleAndCount() {
        val widths = (listOf(false to 0) + (1..12).map { true to it }).map { (insider, n) ->
            RoundMessage.RoleRevealed(insider = insider, insiders = n).count.length
        }
        assertEquals(setOf(RoundMessage.RoleRevealed.COUNT_CHARS), widths.toSet())
    }
}
