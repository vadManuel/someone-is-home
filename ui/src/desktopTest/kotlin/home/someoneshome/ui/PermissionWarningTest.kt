package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The owner's warning on the notice, and THE HOUSE IS CLOSED on every phone** (the owner's
 * 2026-11-20 rulings and words, D-310).
 *
 * The phone's half: the two sentences are appended to the notice when the house says a seat it
 * names switched a permission off, in the owner's words exactly, and never otherwise; and the close
 * walks every phone out of the round to the owner's two lines, a guest to JOIN A HOME and the host
 * to its own lobby, the same words whatever the phone's role and whether it is out.
 */
@OptIn(ExperimentalTestApi::class)
class PermissionWarningTest {

    // **The owner's words, approved verbatim** (2026-11-21, D-311).
    private val one = "Resident MIA switched off a permission this game needs. " +
        "If anyone switches one off again, the house closes and everyone starts over."
    private val two = "Residents MIA and JO switched off a permission this game needs. " +
        "If anyone switches one off again, the house closes and everyone starts over."
    private val three = "Residents MIA, JO and DANI switched off a permission this game needs. " +
        "If anyone switches one off again, the house closes and everyone starts over."
    private val samLeft = "Resident SAM was unreachable this shift. Occupancy data for this interval is incomplete."
    private val closedLines = "THE HOUSE IS CLOSED\nA permission was switched off again. Everyone starts over."

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

    /** A housed phone holding the notice the house sent: [left] only left the app, [switchedOff] switched something off. */
    private fun housed(left: List<Seat>, switchedOff: List<Seat>, insider: Boolean = false): PanelState {
        val model = FlowModel(PanelState(houseAttached = true))
        model.houseSaid(RoundMessage.RoleRevealed(insider = insider))
        model.houseSaid(RoundMessage.RosterIssued(listOf("ROSE", "MIA", "JO", "DANI", "SAM")))
        model.houseSaid(RoundMessage.NoticeIssued(left, switchedOff = switchedOff))
        return model.state
    }

    /**
     * **GUARD: a seat that switched something off is named in the owner's permission sentence**
     * (D-311), one name, two or three, joined with commas and then *and*, and drawn so on the notices
     * screen.
     */
    @Test
    fun aSwitchOffIsNamedInTheOwnersPermissionSentence() {
        assertEquals(one, noticeBody(emptyList(), switchedOff = listOf("MIA")))
        assertEquals(two, noticeBody(emptyList(), switchedOff = listOf("MIA", "JO")))
        assertEquals(three, noticeBody(emptyList(), switchedOff = listOf("MIA", "JO", "DANI")))
        assertEquals(one, PanelVals(housed(emptyList(), listOf(Seat(1)))).notice)
        val texts = drawn(housed(emptyList(), listOf(Seat(1), Seat(2))))
        assertTrue(texts.any { it == two }, "the notices screen did not draw the permission sentence in the owner's words: $texts")
    }

    /**
     * **GUARD: the orderings** (D-311). Only leavers: today's sentence, unchanged, and no warning.
     * Both: the leavers' sentence first, then the permission sentence. A seat the house put on the
     * permission list alone (it both left and switched off) is named once, there.
     */
    @Test
    fun theNoticeOrderings() {
        // Only leavers: exactly today's notice.
        val leftOnly = PanelVals(housed(listOf(Seat(4)), emptyList())).notice
        assertEquals(samLeft, leftOnly)
        val texts = drawn(housed(listOf(Seat(4)), emptyList()))
        assertTrue(texts.none { "permission" in it.lowercase() || "starts over" in it }, "a leave drew the warning: $texts")
        // Both: the leaver first, then the switchers.
        val both = PanelVals(housed(listOf(Seat(4)), listOf(Seat(1), Seat(2)))).notice
        assertEquals("$samLeft $two", both)
        assertTrue(drawn(housed(listOf(Seat(4)), listOf(Seat(1), Seat(2)))).any { it == "$samLeft $two" }, "the notices screen drew the two sentences otherwise")
        // A seat that did both is on the permission list alone, and is named once.
        val did = PanelVals(housed(emptyList(), listOf(Seat(1)))).notice!!
        assertEquals(1, Regex("MIA").findAll(did).count(), "a seat that both left and switched off was named twice: $did")
        // And an empty notice draws nothing.
        assertNull(noticeBody(emptyList(), emptyList()))
        assertNull(PanelVals(housed(emptyList(), emptyList())).notice)
    }

    /** **GUARD (rule 1, parity): the notice draws the same words on an Insider's phone and a Resident's.** */
    @Test
    fun theNoticeIsTheSameOnEveryRole() {
        for ((left, switched) in listOf(listOf(Seat(4)) to emptyList(), emptyList<Seat>() to listOf(Seat(1)), listOf(Seat(4)) to listOf(Seat(1), Seat(2)))) {
            val resident = drawn(housed(left, switched, insider = false))
            val insider = drawn(housed(left, switched, insider = true))
            assertEquals(resident, insider, "the notice drew differently by role for $left / $switched")
        }
    }

    /** The permission list is reset between rounds with the notice it rode. */
    @Test
    fun theWarningIsForgottenBetweenRounds() {
        val state = housed(emptyList(), listOf(Seat(1)))
        assertEquals(listOf(Seat(1)), state.noticeSwitchedOff)
        assertTrue(state.betweenRounds().noticeSwitchedOff.isEmpty())
    }

    // ---- The close -------------------------------------------------------------------------------

    private fun aHome() = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    /** A guest in a lobby, walked in as a real one is, then into a round on [screen], an Insider or not. */
    private fun aGuestInARound(insider: Boolean, screen: ScreenId): FlowModel {
        val model = FlowModel(
            PanelState(screen = ScreenId.Join),
            lobby = LobbyModel(finder = MemoryHomeFinder(listOf(aHome()))),
            perms = PermsModel.sample(),
        )
        model.lobby.nameResident("ROSE")
        model.lobby.look()
        model.attachToHome(model.lobby.nearby.first())
        assertEquals(ScreenId.Lobby, model.state.screen, "the fixture never reached a lobby")
        model.houseSaid(RoundMessage.RoleRevealed(insider = insider))
        model.push(screen)
        return model
    }

    /**
     * **GUARD: the close walks a guest to JOIN A HOME with the owner's two lines**, out of the
     * round and the lobby, with nothing behind it, the same words whatever its role and whether it
     * is out.
     */
    @Test
    fun theCloseWalksEveryGuestToJoinAHomeWithTheTwoLines() {
        val cases = listOf(
            false to ScreenId.Home,
            true to ScreenId.Page2,
            false to ScreenId.Ghost2,
            true to ScreenId.Vote,
        )
        val landed = cases.map { (insider, screen) ->
            val model = aGuestInARound(insider, screen)
            model.houseSaid(RoundMessage.HouseClosedOnPermission)
            assertEquals(ScreenId.Join, model.state.screen, "a guest (insider=$insider) on $screen was not walked to JOIN A HOME")
            assertFalse(model.canGoBack, "a closed guest could step back into the round")
            assertNull(model.lobby.attached, "a closed guest was still attached to the house")
            assertEquals(PanelRole.Resident, model.state.role, "a closed guest kept its round's role")
            model.lobby.refusal
        }
        assertEquals(List(cases.size) { closedLines }, landed, "the two lines were not the same on every guest")
        val drawn = onScreen(aGuestInARound(insider = true, screen = ScreenId.Home).also { it.houseSaid(RoundMessage.HouseClosedOnPermission) })
        assertTrue(drawn.any { it == closedLines }, "JOIN A HOME did not draw the two lines: $drawn")
    }

    /** **GUARD: the host goes back to its lobby, still hosting, with the same two lines.** */
    @Test
    fun theCloseWalksTheHostBackToItsLobbyWithTheTwoLines() {
        for (insider in listOf(false, true)) {
            val model = FlowModel(PanelState(screen = ScreenId.Home), lobby = LobbyModel.sample())
            assertTrue(model.lobby.hosting)
            model.houseSaid(RoundMessage.RoleRevealed(insider = insider))
            model.houseSaid(RoundMessage.HouseClosedOnPermission)
            assertEquals(ScreenId.Lobby, model.state.screen, "the host (insider=$insider) was not walked back to its lobby")
            assertTrue(model.lobby.hosting, "the close took the house off the air")
            assertEquals(closedLines, model.lobby.refusal, "the host was not shown the two lines")
            assertEquals(PanelRole.Resident, model.state.role, "the host kept its round's role")
            assertTrue(onScreen(model).any { it == closedLines }, "the host's lobby did not draw the two lines: ${onScreen(model)}")
        }
    }

    /** Every piece of text the screen [model] stands on drew, with its lobby. */
    private fun onScreen(model: FlowModel): List<String> {
        var texts: List<String> = emptyList()
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
                }
            }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        }
        return texts
    }
}
