package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **A round armed without seat 2: the roster holds a null at index 2, and nothing offers it**
 * (D-115, D-274, D-277).
 *
 * The roster is read by seat index, so a seat that is not in the round is a null slot rather than
 * a missing one. A null is nobody: the ballot draws no row for it and the Surge picker draws no
 * row for it, so a finger can never send a vote or a Surge to a seat that is not in the house. The
 * rows that are drawn turn back into the seat at their own index, past the gap.
 *
 * **A blank is somebody** (D-277): a seat in the round whose phone never said a name. It keeps its
 * row, drawn as UNNAMED, and that row reaches its own seat even with a hole in front of it — the
 * review that found D-274 folding the two together saw an unnamed Insider nobody could vote out.
 *
 * The hunt this closes: a second round armed with seats 0, 1, 3, 4 and 5, and the house restrained
 * HERON while every screen said FINCH.
 */
@OptIn(ExperimentalTestApi::class)
class RosterGapTest {

    /** Seat 2 is not in this round. */
    private val gapped = listOf("PRIYA", "MARCUS", null, "DANI")

    /** Seat 1 is not in this round; seat 2 is, and never said a name. The hole comes first. */
    private val unnamedPastAHole = listOf("PRIYA", null, "", "DANI")

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby, model.meeting)
            }
        }
    }

    /** A clickable row whose only words are none at all: a row for nobody. */
    private val aBlankRow = hasClickAction() and SemanticsMatcher("draws no words") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.all { it.text.isBlank() } &&
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.isNotEmpty()
    }

    @Test
    fun theBallotOffersNoRowForTheMissingSeatAndDaniIsSeatThree() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val sent = mutableListOf<IntentMessage>()
            // The app's own wiring (`Cheats.kt`): a control becomes a frame against the ballot the
            // screen was drawn with, which the house wrote.
            lateinit var meeting: MeetingModel
            meeting = MeetingModel(send = { sent += it.asMessage(meeting.names) })
            val model = FlowModel(PanelState(screen = ScreenId.Home), meeting = meeting)
            model.houseSaid(RoundMessage.RosterIssued(gapped))
            model.push(ScreenId.Vote)
            show(model)

            assertEquals(
                0, onAllNodes(aBlankRow).fetchSemanticsNodes().size,
                "the ballot drew a row for the seat that is not in the round",
            )
            onNodeWithText("DANI").performClick()
            waitForIdle()
            assertEquals(
                listOf<IntentMessage>(IntentMessage.SelectVote(Seat(3))),
                sent.filterIsInstance<IntentMessage.SelectVote>(),
                "DANI's row asked for somebody other than seat 3",
            )
        }

    /**
     * **The wire's hole is a null, and the phone counts seats, not slots** (D-274). Three people are
     * in this round; the list is four long because DANI sits at seat 3. The couch's ballot names
     * each voter by their own seat, past the hole.
     */
    @Test
    fun aNullSlotIsNobodyAndTheSeatCountIsThePeopleInTheRound() {
        val couch = FlowModel(PanelState(screen = ScreenId.GhostMeeting))
        couch.houseSaid(RoundMessage.RosterIssued(gapped))
        assertEquals(gapped, couch.state.roster, "the roster was not kept by seat index")
        assertEquals(3, couch.state.seats, "the seat total counted the hole as somebody")
        assertEquals(gapped, couch.meeting.names, "the ballot holds a different list from the roster")

        couch.houseSaid(RoundMessage.VoteSelectionShown(voter = Seat(3), selection = Seat(1)))
        couch.houseSaid(RoundMessage.VoteSelectionShown(voter = Seat(1), selection = Seat(3)))
        assertEquals(
            listOf(Ballot("MARCUS", "DANI"), Ballot("DANI", "MARCUS")),
            couch.state.ballots,
            "the couch named a voter or a choice by somebody else's seat",
        )
    }

    @Test
    fun theSurgePickerOffersNoRowForTheMissingSeatAndDaniIsSeatThree() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val sent = mutableListOf<IntentMessage>()
            val model = FlowModel(
                PanelState(screen = ScreenId.Home, role = PanelRole.Insider, roster = gapped),
                send = { sent += it },
            ).also { it.push(ScreenId.SurgeTargets) }
            show(model)

            onNodeWithTag("${TARGET_ROW}2").assertDoesNotExist()
            onNodeWithTag("${TARGET_ROW}3").assert(hasText("DANI"))
            onNodeWithTag("${TARGET_ROW}3").performClick()
            waitForIdle()
            assertEquals(
                listOf<IntentMessage>(IntentMessage.FireSurge(Seat(3))), sent.toList(),
                "DANI's row fired at somebody other than seat 3",
            )
        }

    @Test
    fun anUnnamedSeatPastAHoleIsOnTheBallotAndItsRowIsItsOwnSeat() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val sent = mutableListOf<IntentMessage>()
            lateinit var meeting: MeetingModel
            meeting = MeetingModel(send = { sent += it.asMessage(meeting.names) })
            val model = FlowModel(PanelState(screen = ScreenId.Home), meeting = meeting)
            model.houseSaid(RoundMessage.RosterIssued(unnamedPastAHole))
            model.push(ScreenId.Vote)
            show(model)

            assertEquals(
                0, onAllNodes(aBlankRow).fetchSemanticsNodes().size,
                "the ballot drew a row with no words on it",
            )
            onNodeWithText(UNNAMED).performClick()
            waitForIdle()
            assertEquals(
                listOf<IntentMessage>(IntentMessage.SelectVote(Seat(2))),
                sent.filterIsInstance<IntentMessage.SelectVote>(),
                "the unnamed seat's row asked for somebody other than seat 2",
            )
        }

    @Test
    fun anUnnamedSeatPastAHoleIsOnTheSurgePickerAndItsRowIsItsOwnSeat() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val sent = mutableListOf<IntentMessage>()
            val model = FlowModel(
                PanelState(screen = ScreenId.Home, role = PanelRole.Insider, roster = unnamedPastAHole),
                send = { sent += it },
            ).also { it.push(ScreenId.SurgeTargets) }
            show(model)

            onNodeWithTag("${TARGET_ROW}1").assertDoesNotExist()
            onNodeWithTag("${TARGET_ROW}2").assert(hasText(UNNAMED))
            onNodeWithTag("${TARGET_ROW}2").performClick()
            waitForIdle()
            assertEquals(
                listOf<IntentMessage>(IntentMessage.FireSurge(Seat(2))), sent.toList(),
                "the unnamed seat's row fired at somebody other than seat 2",
            )
        }

    /**
     * **Two people who gave one name are two rows, and each reaches its own seat** (D-278). The
     * desk does not make names unique, and the ballot once turned a row back into a seat by name,
     * so a vote for the second SAM restrained the first.
     */
    @Test
    fun twoPeopleWithOneNameAreTwoRowsAndEachIsItsOwnSeat() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val sent = mutableListOf<IntentMessage>()
            lateinit var meeting: MeetingModel
            meeting = MeetingModel(send = { sent += it.asMessage(meeting.names) })
            val model = FlowModel(PanelState(screen = ScreenId.Home), meeting = meeting)
            model.houseSaid(RoundMessage.RosterIssued(listOf("SAM", "PRIYA", "SAM", "DANI")))
            model.push(ScreenId.Vote)
            show(model)

            val sams = onAllNodes(hasText("SAM") and hasClickAction())
            assertEquals(2, sams.fetchSemanticsNodes().size, "the ballot did not draw both SAMs")
            sams[1].performClick()
            waitForIdle()
            assertEquals(
                listOf<IntentMessage>(IntentMessage.SelectVote(Seat(2))),
                sent.filterIsInstance<IntentMessage.SelectVote>(),
                "the second SAM's row asked for somebody other than seat 2",
            )
            assertTrue(meeting.holds("SAM", 2), "the second SAM's row is not the one lit")
            assertFalse(meeting.holds("SAM", 0), "the first SAM's row is lit for the second SAM's vote")
        }
}
