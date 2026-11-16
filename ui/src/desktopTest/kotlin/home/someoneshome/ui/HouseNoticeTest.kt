package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The notices screen draws the house notice, and a meeting where nobody evaded draws nothing**
 * (D-187, D-223; the owner, 2026-11-15).
 *
 * The house sends the notice at every meeting, empty when nobody evaded (rule 1), so the empty one
 * is the common case and its screen is the one a room sees most. It must carry no sentence at all:
 * no *data is complete* line, and above all not the design's fixture, which names MARCUS and on a
 * housed phone would be the house naming a player nobody reported.
 */
@OptIn(ExperimentalTestApi::class)
class HouseNoticeTest {

    /** Every piece of text the Notice screen drew for [state]. */
    private fun drawn(state: PanelState): List<String> {
        var texts: List<String> = emptyList()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(state.arrivingAt(ScreenId.Notice)) } }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        }
        return texts
    }

    /** What a sentence about the notice would have to contain, in any wording the house has used. */
    private fun aNotice(text: String): Boolean =
        listOf("unreachable", "occupancy", "complete", "notice:").any { it in text.lowercase() }

    /** A housed phone, at a meeting the house has opened with this notice. */
    private fun housed(notice: List<Seat>): PanelState {
        val model = FlowModel(PanelState(houseAttached = true))
        model.houseSaid(RoundMessage.RosterIssued(listOf("ROSE", "ELLIOT", "PRIYA", "DANI")))
        model.houseSaid(RoundMessage.NoticeIssued(notice))
        return model.state
    }

    /** **GUARD: a meeting with no evasion draws nothing**: no line, no box, no fixture. */
    @Test
    fun aMeetingWhereNobodyEvadedDrawsNothing() {
        val state = housed(emptyList())
        assertEquals(emptyList(), state.notice, "the house's empty notice never reached the phone's state")
        assertEquals(null, PanelVals(state).notice, "an empty notice resolved to words")
        val texts = drawn(state)
        assertTrue(texts.any { "NOTICES" in it }, "the screen did not draw at all: $texts")
        assertEquals(
            emptyList(), texts.filter(::aNotice),
            "a meeting where nobody evaded drew a notice sentence",
        )
    }

    /** **GUARD: a named player is named in the owner's words** (D-223), and nobody else is. */
    @Test
    fun aNamedPlayerIsNamedInTheOwnersWords() {
        val texts = drawn(housed(listOf(Seat(2))))
        assertTrue(
            texts.any { it == "Resident PRIYA was unreachable this shift. Occupancy data for this interval is incomplete." },
            "the notice did not name PRIYA in the owner's words: $texts",
        )
        assertTrue(texts.none { "ROSE" in it || "ELLIOT" in it || "DANI" in it }, "the notice named a player nobody reported: $texts")
    }

    /** Two named in one meeting read as one sentence. */
    @Test
    fun twoNamedReadAsOneSentence() {
        assertEquals(
            "Residents ROSE and DANI were unreachable this shift. Occupancy data for this interval is incomplete.",
            noticeBody(listOf("ROSE", "DANI")),
        )
        assertEquals(
            "Residents ROSE, ELLIOT and DANI were unreachable this shift. Occupancy data for this interval is incomplete.",
            noticeBody(listOf("ROSE", "ELLIOT", "DANI")),
        )
    }

    /** A housed phone the house has told nothing yet is not shown the design's MARCUS either. */
    @Test
    fun aHousedPhoneIsNeverShownTheFixture() {
        val texts = drawn(PanelState(houseAttached = true))
        assertEquals(emptyList(), texts.filter(::aNotice), "a housed phone drew the fixture's notice")
    }

    /** And the render harness still sees the screen as the design drew it. */
    @Test
    fun aPhoneWithNoHouseStillDrawsTheDesignsNotice() {
        val texts = drawn(PanelState())
        assertTrue(texts.any { Notifications.notice.body in it }, "the fixture notice is gone from the render harness: $texts")
    }
}
