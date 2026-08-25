package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test

/**
 * **THIS ROOM lists what is in this room, reserved cards included** (E-PAY-5).
 *
 * The row under the viewfinder is the one line on the registration screen whose whole job is *what
 * is in here now*, and it drew ordinary markers only. So a host scanned the T card, watched the
 * readout confirm TERMINAL . HALL, looked down at the row that lists the room's contents, and saw
 * nothing — with no way to tell that from a scan that had failed.
 *
 * The reason it happened is worth keeping visible: the reserved cards are **not** in
 * `HouseMap.registrations` and never may be (D-120, D-121 — `HouseMap.of` refuses a build where one
 * of them is registered as an ordinary marker), so `markersIn` cannot reach them by construction.
 * They come off `terminal` and `meeting`, and they are drawn with their own tokens rather than as
 * marker glyphs, because neither is ever an ordinary marker and a letter inside a shape outline is
 * exactly what a host reads as one.
 */
@OptIn(ExperimentalTestApi::class)
class ThisRoomTest {

    /** The registration screen, with the host standing in one named room of the fixture. */
    private fun scanningIn(
        room: String,
        block: androidx.compose.ui.test.SemanticsNodeInteractionsProvider.() -> Unit,
    ) = runDesktopComposeUiTest(width = 300, height = 650) {
        val editor = HomeEditorModel.bungalow()
        editor.open(room)
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(PanelState(screen = ScreenId.ScanMarker), editor = editor)
            }
        }
        block()
    }

    /**
     * **The terminal is in the hall, and the hall's row says so.**
     *
     * The fixture's HALL holds the T card and no ordinary markers, so this is the case that failed
     * hardest: the row was completely empty in a room that holds the single most important card in
     * the home.
     */
    @Test
    fun theRoomHoldingTheTerminalDrawsTheTerminalsOwnMark() = scanningIn("HALL") {
        onAllNodesWithText("T").assertCountEquals(1)
        onAllNodesWithText("U").assertCountEquals(0)
    }

    /**
     * **The meeting card is in LIVING, beside an ordinary marker**, which is the fixture's own
     * deliberate arrangement: D-121 reserves a shape rather than a room, and a room where the
     * meeting card was alone would let this row be written as though it always is.
     */
    @Test
    fun theRoomHoldingTheMeetingCardDrawsTheMeetingCardsOwnMark() = scanningIn("LIVING") {
        onAllNodesWithText("U").assertCountEquals(1)
        onAllNodesWithText("T").assertCountEquals(0)
    }

    /**
     * **A room holding neither draws neither.** The other direction, and the one that would fail if
     * the tokens were drawn unconditionally — which would tell a host the T card is in every room
     * they walk into.
     */
    @Test
    fun aRoomWithNoReservedCardDrawsNeitherMark() = scanningIn("GARAGE") {
        onAllNodesWithText("T").assertCountEquals(0)
        onAllNodesWithText("U").assertCountEquals(0)
    }
}
