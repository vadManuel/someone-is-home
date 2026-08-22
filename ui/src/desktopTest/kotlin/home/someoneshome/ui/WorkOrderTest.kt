package home.someoneshome.ui

import home.someoneshome.model.OrderLine
import home.someoneshome.model.SubroutineKind

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The work order the house dealt, read off the two screens that draw it** (D-114, D-129, D-106).
 *
 * Until now both screens drew a fixture: seven rows typed into a composable, agreeing with nothing.
 * `Effect.WorkOrderIssued` has carried the real thing since L3 and no surface consumed it, which is
 * the shape a leak hides in — a screen that cannot be wrong because it is not connected to
 * anything, and then one day it is connected and nobody re-reads it.
 *
 * **The dangerous half of this screen is the blocked entry.** A Resident whose work is gated behind
 * somebody else's sees that *something is there* and never what — and under D-146 dependencies are
 * cross-player, so naming a blocked entry names the **person upstream** as surely as the work. The
 * blackout is structural: `OrderLine.Blocked` and [OrderRow.Blocked] each carry one integer, so
 * there is nothing on either type for a composable to draw. These tests are what stops that being
 * quietly undone by a field somebody adds for a good reason.
 */
@OptIn(ExperimentalTestApi::class)
class WorkOrderTest {

    /**
     * An order with one of everything: something done, something to do, and two known unknowns.
     *
     * The kinds are chosen so the three light rungs are all represented and all *different* from
     * the port's fixture — REPLAY is the fixture's too, but PARITY CHECK, DRIFT and SIGNAL TRACE
     * are not, so a screen still drawing the fixture fails on the names rather than passing by
     * coincidence.
     */
    private val dealt: List<OrderLine> = listOf(
        OrderLine.Known(0, SubroutineKind.ParityCheck, done = true),
        OrderLine.Known(1, SubroutineKind.Drift, done = false),
        OrderLine.Blocked(2),
        OrderLine.Known(3, SubroutineKind.SignalTrace, done = false),
        OrderLine.Blocked(4),
    )

    private fun SemanticsNodeInteractionsProvider.marks(): Map<LightSignature, Int> =
        LightSignature.entries
            .associateWith { onAllNodesWithTag(markTag(it), useUnmergedTree = true).fetchSemanticsNodes().size }
            .filterValues { it > 0 }

    private fun SemanticsNodeInteractionsProvider.destinations(): Int =
        onAllNodesWithTag(ORDER_DESTINATION, useUnmergedTree = true).fetchSemanticsNodes().size

    /**
     * **The list draws the order the house sent, and not the one the port drew.**
     *
     * Both halves matter. The names that arrived are on screen; the fixture's are not, which is
     * what makes this a wiring test rather than a rendering test — a screen still reading [ORDER]
     * would show REPLAY, SHORT, JAM and ARRAY WIPE and fail on all four.
     */
    @Test
    fun theListDrawsTheOrderTheHouseDealt() = runDesktopComposeUiTest(width = 600, height = 1300) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(PanelState(screen = ScreenId.Work, order = dealt))
            }
        }
        for (name in listOf("PARITY CHECK", "DRIFT", "SIGNAL TRACE")) {
            assertTrue(
                onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty(),
                "$name is in the order the house dealt and is not on the list",
            )
        }
        for (fixture in listOf("SHORT", "JAM", "ARRAY WIPE")) {
            onAllNodesWithText(fixture).assertCountEquals(0)
        }
        // Counted off the rows, both halves: one of five done, and five is the length received.
        onNodeWithText("1 OF 5 DONE").assertExists()
    }

    /**
     * **THE INJECTION: a blocked entry names nothing and rates nothing** (D-114, D-146).
     *
     * *Not absent, which would shorten the order and make its length a tell. Not spelled out, which
     * would hand the player a route they have not earned.* This renders an order whose blocked
     * lines sit between named ones and asserts three things at once: the length is the length the
     * house sent, the light marks number exactly the named rows, and the blocked rows say only what
     * every blocked row in the game says.
     *
     * **The light count is the sharp end.** A signature is a property of a particular Subroutine,
     * so a mark on a blocked row would describe the very thing the row exists not to describe — and
     * it would do it silently, in a column the eye reads *down* rather than across, while the name
     * beside it still read LOCKED. Nothing on the ladder is zero cells, so an absent mark can never
     * be misread as a dark Subroutine.
     *
     * Verified by injection: drawing a name and a `LightMark` on [WorkLocked] fails here by name.
     */
    @Test
    fun aBlockedEntryNamesNothingAndRatesNothing() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Work, order = dealt))
                }
            }
            // Two blocked rows, and both say the one thing a blocked row is allowed to say.
            onAllNodesWithText("LOCKED").assertCountEquals(2)
            onAllNodesWithText("WAITING UPSTREAM").assertCountEquals(2)

            // Three named rows, three marks, and every one of them the roster's own value for the
            // Subroutine on that row: PARITY CHECK bright, DRIFT and SIGNAL TRACE medium.
            assertEquals(
                mapOf(LightSignature.Bright to 1, LightSignature.Medium to 2),
                marks(),
                "the light column does not match the rows the house named — a blocked entry that " +
                    "draws a signature has described the Subroutine it exists to hide (D-114)",
            )

            // The whole roster, swept: nothing a blocked line could have been is on this screen.
            val named = setOf("PARITY CHECK", "DRIFT", "SIGNAL TRACE")
            for (subroutine in Subroutine.entries) {
                if (subroutine.label in named) continue
                onAllNodesWithText(subroutine.label).assertCountEquals(0)
            }
        }

    /**
     * **The converse, because a blackout is satisfied by a screen with nothing on it.**
     *
     * The same two rows really are drawn, in their own places in the order, and the order really is
     * as long as the one the house sent. An order that dropped its blocked lines would render green
     * against every assertion above.
     */
    @Test
    fun theOrderIsAsLongAsTheOneTheHouseSent() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Work, order = dealt))
                }
            }
            assertEquals(
                dealt.size,
                PanelVals(PanelState(screen = ScreenId.Work, order = dealt)).order.size,
                "the drawn order is not the length the house sent — length is not a channel, in " +
                    "either direction (D-114, D-129)",
            )
            onNodeWithText("1 OF ${dealt.size} DONE").assertExists()
        }

    /**
     * **⚠️ THE ESCALATION, HELD AS A GUARD: nothing on the wire says where an entry is.**
     *
     * `OrderLine` carries no card and no room, in either case, deliberately — *the house answers a
     * scan and never publishes a map of the round.* So the moment a real order arrives, the list's
     * destination column and the springboard widget's destination line both go, on every row at
     * once, and the player is told what their work is and how bright it is and nothing about where.
     *
     * The design draws a room and a marker glyph on both surfaces. **This test asserts the hole
     * rather than the design**, which is the honest state of the build: it fails the day somebody
     * quietly fills the column back in from a fixture — which would be a screen that lies in the
     * dark, to a player with no way to check — and it fails the day the wire really carries a card,
     * at which point the test that has to change says exactly why.
     */
    @Test
    fun anOrderFromTheHouseSaysNothingAboutWhereTheWorkIs() {
        for (screen in listOf(ScreenId.Work, ScreenId.Home)) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(PanelState(screen = screen, order = dealt))
                    }
                }
                assertEquals(
                    0, destinations(),
                    "$screen drew a destination for work the house dealt. No effect carries the " +
                        "card, so anything drawn there came from a fixture and is a lie in the dark",
                )
            }
            // And the port's own seven still carry theirs, so the assertion above is about the
            // wire rather than about a column somebody deleted.
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = screen)) }
                }
                assertTrue(
                    destinations() > 0,
                    "$screen draws no destination even from the port's fixture — the column was " +
                        "removed rather than left waiting for a sender",
                )
            }
        }
    }

    /**
     * **The springboard and the list name the same Subroutine** (D-106, D-114).
     *
     * They were two hand-kept fixtures, and hand-kept fixtures drift: the design's own port had
     * three surfaces showing a triangle and a fourth showing a ring for the same marker. Both now
     * read [PanelVals.nextUp], so the widget a player glances at while walking and the list they
     * read while deciding cannot send them to two different pieces of work.
     *
     * DRIFT is the first line of [dealt] that is neither done nor blocked, and *first actionable*
     * is the whole of the rule — which is a presentation D-114 makes questionable and which is
     * flagged where it is derived rather than defended here.
     */
    @Test
    fun theWidgetNamesTheFirstActionableLineOfTheSameOrder() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Home, order = dealt))
                }
            }
            onNodeWithText("DRIFT").assertExists()
            // The counts are the order's, not a settings row's.
            onNodeWithText("1").assertExists()
            onNodeWithText("OF 5 ASSIGNED").assertExists()
            // D-106's springboard mark, and it is DRIFT's own rung rather than the fixture's dark.
            assertEquals(mapOf(LightSignature.Medium to 1), marks())
        }

    /**
     * **Both roles are dealt the same shape of order and drawn the same screen** (D-129, rule 8).
     *
     * An Insider's order is a fake drawn by the same rule at the same length, and every entry on it
     * is real work with a real screen that writes nothing. So the same order rendered in both roles
     * has to produce the same words — a list that differed by so much as a row count would be an
     * alignment tell on the screen a player opens most often.
     */
    @Test
    fun theOrderScreenIsTheSameScreenInBothRoles() {
        // **Collected into a variable declared out here, and that is not a style choice.**
        // `runDesktopComposeUiTest` returns Unit, so the first version of this test mapped over
        // the roles, got a `List<Unit>` back and compared `Unit` to `Unit` — green, forever,
        // over any difference at all. Same class of fault as the harness's derived allowlist:
        // a test that agrees with itself.
        val drawn = mutableListOf<List<String>>()
        for (role in PanelRole.entries) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(PanelState(screen = ScreenId.Work, role = role, order = dealt))
                    }
                }
                drawn += onAllNodes(hasText("", substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .flatMap { node ->
                        node.config.asSequence()
                            .filter { it.key.name == "Text" }
                            .flatMap { entry -> (entry.value as? List<*> ?: emptyList<Any>()).asSequence() }
                            .map { it.toString() }
                            .toList()
                    }
            }
        }
        assertEquals(2, drawn.size)
        assertTrue(
            drawn[0].isNotEmpty(),
            "no text was read off the work order at all; this compares two empty lists and proves " +
                "nothing",
        )
        assertTrue(
            drawn[0].any { "PARITY CHECK" in it },
            "the text read off the screen does not contain the order that was dealt; the reader " +
                "is looking at the wrong nodes and would agree with itself about anything",
        )
        assertEquals(
            drawn[0], drawn[1],
            "the work order reads differently for the two roles — an Insider's order is drawn by " +
                "the same rule at the same length and there is nothing here to differ about (D-129)",
        )
    }
}
