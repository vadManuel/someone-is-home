package home.someoneshome.ui

import home.someoneshome.model.MeetingPhase

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **No fixture may render on a phone with a house attached** — read off the screens, not off
 * [PanelVals].
 *
 * The port's fixtures exist so that a screen with nothing behind it shows the drawn design instead
 * of an empty rectangle, which is what makes a desktop render harness worth anything. That is
 * lawful and it stays. **What is not lawful is a fixture standing in front of a player**, and the
 * owner met the whole family of them in one evening:
 *
 * - the caught-scan screen said `HANDSHAKE . GARAGE` while the house had dealt them a **Jam**, off
 *   [PanelVals.CURRENT] — *"it seems that all tasks are the same"*, and nine of the ten Subroutines
 *   were unreachable in play because of it;
 * - the tally read `VOTED 4/6` in a **five-seat** round, off the port's six;
 * - both phones drew a vote clock all night while the house had opened no phase at all.
 *
 * Every one of those is a `?: FIXTURE` that was right about a bench and wrong about a round. The
 * failure is invisible by construction — a fixture renders exactly as confidently as a fact — so it
 * cannot be caught by looking, and this is the file that catches it instead.
 *
 * **Both directions, every test.** The unhoused port must keep drawing the fixture, because a fix
 * that stopped the fiction by stopping the harness would take every screenshot review with it.
 */
@OptIn(ExperimentalTestApi::class)
class HousedFixtureTest {

    /**
     * **THE REPRODUCTION: a housed phone with no answered scan draws no Subroutine at all.**
     *
     * The room and the position are drawn as one joined label — `GARAGE . SUBROUTINE 4 OF 7` — so
     * every assertion here is a **substring** match. That is not a detail: the first draft of this
     * test asked for a node whose whole text was `GARAGE`, which no screen has ever drawn, and it
     * therefore passed against the fixture it was written to catch. It is the eighth silent
     * instrument this project has found and the first one caught by its own opposite half.
     */
    @Test
    fun aHousedCaughtScanNamesNothingRatherThanTheFixture() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.ScanCaught, houseAttached = true))
                }
            }
            for (fiction in listOf("GARAGE", "SUBROUTINE 4 OF 7", PanelVals.CURRENT.subroutine.briefing)) {
                assertEquals(
                    0, onAllNodes(hasText(fiction, substring = true)).fetchSemanticsNodes().size,
                    "the port's fixture rendered on a housed phone: $fiction",
                )
            }
            // ⚠️ And BEGIN is absent rather than present-and-refusing. A BEGIN with nothing behind
            // it is how the fixture got opened in the first place.
            onAllNodesWithText("BEGIN").assertCountEquals(0)
            assertTrue(
                onAllNodes(hasText(PanelVals.NOTHING_OPEN, substring = true))
                    .fetchSemanticsNodes().isNotEmpty(),
                "a housed phone with no answered scan drew neither the work nor the honest empty",
            )
        }

    /** The same screen unhoused still draws the design, which is what the fixture is for. */
    @Test
    fun anUnhousedCaughtScanStillDrawsThePortsSubroutine() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.ScanCaught, houseAttached = false))
                }
            }
            for (drawn in listOf("GARAGE", "SUBROUTINE 4 OF 7")) {
                assertTrue(
                    onAllNodes(hasText(drawn, substring = true)).fetchSemanticsNodes().isNotEmpty(),
                    "the port lost its own drawn screen — the harness went with the fiction: $drawn",
                )
            }
            onAllNodesWithText("BEGIN").assertCountEquals(1)
        }

    /**
     * **The work order draws no rows the house did not deal.**
     *
     * A work order is the list of places a player walks to in the dark. The port's seven name five
     * rooms — HALL, GARAGE, BED 2, STUDY — and on the owner's phones those were rooms in a bungalow
     * nobody lives in. An empty order says *you have not been dealt one*, which is true and which a
     * player can wait out.
     */
    @Test
    fun aHousedWorkOrderDrawsNoFixtureRows() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Work, houseAttached = true))
                }
            }
            for (fixture in listOf("REPLAY", "SHORT", "JAM", "ARRAY WIPE")) {
                onAllNodesWithText(fixture).assertCountEquals(0)
            }
        }

    /** And unhoused it still draws all seven, blocked rows and all. */
    @Test
    fun anUnhousedWorkOrderStillDrawsThePortsSeven() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Work, houseAttached = false))
                }
            }
            for (drawn in listOf("REPLAY", "SHORT", "JAM")) {
                assertTrue(
                    onAllNodes(hasText(drawn)).fetchSemanticsNodes().isNotEmpty(),
                    "$drawn is in the port's drawn order and the unhoused list lost it",
                )
            }
        }

    /**
     * **The reveal names nobody the house did not name.**
     *
     * The sharpest fixture in the game: the reveal is the one moment a name and a one line are
     * lawfully together on a phone (D-116), so a fixture here accuses two named people of having
     * been Insiders, on the screen the whole room reads at the end of the evening.
     */
    @Test
    fun aHousedEndingAccusesNobody() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            for (ending in listOf(ScreenId.WinResidents, ScreenId.WinInsiders)) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(PanelState(screen = ending, houseAttached = true))
                    }
                }
                for (named in PanelVals.REVEALED) {
                    onAllNodesWithText(named.name).assertCountEquals(0)
                }
            }
        }

    /**
     * **The meters read `-` rather than a percentage of a fiction.**
     *
     * `0%` would be a reading the house could have sent and did not; a player has no way to tell
     * one from the other on a lit rectangle in a dark room. This is the same distinction the device
     * consoles were already making — `METER -` — while the screens beside them printed `87%`.
     */
    @Test
    fun aHousedMeterReadsNothingRatherThanTheDrawnFraction() {
        val housed = PanelVals(PanelState(screen = ScreenId.Home, houseAttached = true))
        assertEquals(PanelVals.NO_READING, housed.integrityPercent, "a housed meter printed a fiction")
        assertEquals(0, housed.integrityLit, "a housed meter lit cells nobody had earned")

        val port = PanelVals(PanelState(screen = ScreenId.Home, houseAttached = false))
        assertEquals("87%", port.integrityPercent, "the port lost its drawn meter")
        assertEquals(28, port.integrityLit)
    }

    /**
     * **A housed phone with no tally publishes no verdict**, and says so in words that are not the
     * words for a room that restrained nobody.
     *
     * `NOBODY WAS RESTRAINED` is a *result* — a Skip, a tie, a vote nobody won — and it is the
     * house's answer to a meeting that happened. A room shown that sentence will act on it.
     */
    @Test
    fun aHousedResultWithNoTallyNamesNobodyAndClaimsNothing() {
        val housed = PanelVals(PanelState(screen = ScreenId.Tally, houseAttached = true))
        assertEquals(null, housed.restrained, "a housed phone named somebody off the fixture")
        assertEquals(PanelVals.NO_RESULT, housed.restrainedLine)
        assertEquals(PanelVals.NO_RESULT, housed.votedLine, "a housed tally counted ballots nobody cast")

        val port = PanelVals(PanelState(screen = ScreenId.Tally, houseAttached = false))
        assertEquals("MARCUS", port.restrained, "the port lost its drawn result")
        assertEquals("6 OF 6 VOTED", port.votedLine)
    }

    /**
     * **A housed couch watching no phase is at the gate, and the gate has no clock.**
     *
     * The honest-empty form was already written for this one and nobody had needed it here:
     * [MeetingPhase.CheckIn] gives `WAITING FOR THE HOUSE` and no countdown, because D-104's gate
     * closes on a count of phones rather than on a timer. Both of the owner's phones drew
     * `VOTING ENDS IN` all night with `PHASE -` in every logged line beneath it.
     */
    @Test
    fun aHousedCouchWithNoPhaseWaitsForTheHouseAndRunsNoClock() {
        val housed = PanelVals(PanelState(screen = ScreenId.GhostMeeting, houseAttached = true))
        assertEquals(MeetingPhase.CheckIn, housed.meetingPhase)
        assertEquals("WAITING FOR THE HOUSE", housed.meetingClockLabel)
        assertTrue(!housed.hasMeetingClock, "a housed couch ran a clock on a window nobody opened")

        val port = PanelVals(PanelState(screen = ScreenId.GhostMeeting, houseAttached = false))
        assertEquals(MeetingPhase.Vote, port.meetingPhase, "the port lost its drawn vote clock")
        assertTrue(port.hasMeetingClock)
    }

    /**
     * **The pickers offer nothing the house has not offered.**
     *
     * The Surge picker's rows *are* the wire — a row's position in the list is the seat the ability
     * is fired at (`PanelState.roster`) — so a fixture here is not a cosmetic stand-in. An Insider
     * firing at row 3 of five names the house never sent is aiming at a seat by guessing.
     */
    @Test
    fun aHousedPickerOffersNothingTheHouseDidNotSend() {
        val housed = PanelVals(PanelState(screen = ScreenId.Home, houseAttached = true))
        assertEquals(emptyList(), housed.roster, "a housed picker drew seats off the port's five")
        assertEquals(emptyList(), housed.isolateTargets)
        assertEquals(emptyList(), housed.ballots)
        assertEquals(emptyList(), housed.egressNodes, "a housed alert sent players to rooms nobody drew")

        val port = PanelVals(PanelState(screen = ScreenId.Home, houseAttached = false))
        assertEquals(PanelVals.ROSTER, port.roster, "the port lost its drawn picker")
        assertTrue(port.isolateTargets.isNotEmpty())
        assertTrue(port.ballots.isNotEmpty())
        assertTrue(port.egressNodes.isNotEmpty())
    }

    /**
     * **BEGIN opens nothing on a housed phone the house has not answered.**
     *
     * The line under two of the biggest bugs of the owner's evening, one layer below the screen:
     * `beginSubroutine`'s kind defaulted to the fixture, so every BEGIN in every round opened a
     * Handshake over whatever had actually been dealt. N1 pointed the default at the house's answer
     * and left the fixture behind it for the port; this is the same rule at the call.
     */
    @Test
    fun beginOpensNothingOnAHousedPhoneWithNothingDealt() {
        val housed = FlowModel(PanelState(screen = ScreenId.ScanCaught, houseAttached = true))
        housed.beginSubroutine()
        assertEquals(
            ScreenId.ScanCaught, housed.state.screen,
            "BEGIN opened the port's fixture on a phone the house had dealt nothing",
        )

        val port = FlowModel(PanelState(screen = ScreenId.ScanCaught, houseAttached = false))
        port.beginSubroutine()
        assertEquals(
            PanelVals.CURRENT.subroutine.screen, port.state.screen,
            "the unhoused port can no longer open its own drawn Subroutine",
        )
    }
}
