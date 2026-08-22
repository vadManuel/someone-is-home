package home.someoneshome.ui

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Destination
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
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
    private fun at(card: String, shape: String, room: String) =
        listOf(Destination(MarkerId(card), MarkerShapes[shape]!!.id, room))

    private val dealt: List<OrderLine> = listOf(
        OrderLine.Known(
            0, AssignedWork.Subroutine(SubroutineKind.ParityCheck), done = true,
            at = at("c0", "diamond", "LANDING"),
        ),
        OrderLine.Known(
            1, AssignedWork.Subroutine(SubroutineKind.Drift), done = false,
            at = at("c1", "cross", "BOILER"),
        ),
        OrderLine.Blocked(2),
        OrderLine.Known(
            3, AssignedWork.Subroutine(SubroutineKind.SignalTrace), done = false,
            at = at("c3", "star", "PANTRY"),
        ),
        OrderLine.Blocked(4),
    )

    /**
     * **The circuit, as a line of a real order** — the mix's *1 circuit*, with D-122's three
     * stations riding as its destinations.
     *
     * Kept apart from [dealt] because it is the one line whose label and light are **this
     * client's** rather than the roster's: Array Wipe is not one of the design's ten and is rated
     * for light nowhere, so BRIGHT is the port's reading (D-113 ratified the value; nothing ruled
     * the row).
     */
    private val circuit: List<OrderLine> = listOf(
        OrderLine.Known(
            0, AssignedWork.ArrayWipe, done = false,
            at = listOf(
                Destination(MarkerId("s0"), MarkerShapes["square_frame"]!!.id, "STUDY"),
                Destination(MarkerId("s1"), MarkerShapes["ring"]!!.id, "GARAGE"),
                Destination(MarkerId("s2"), MarkerShapes["star"]!!.id, "HALL"),
            ),
        ),
        OrderLine.Known(
            1, AssignedWork.Subroutine(SubroutineKind.Replay), done = false,
            at = at("c9", "triangle_up", "BED 2"),
        ),
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
     * **The order the house dealt says where the work is — E-C2-1, closed.**
     *
     * `OrderLine.Known` carries its [Destination]s: the room the host named on their setup walk and
     * the glyph printed on the card, which are the two things a player in a dark house navigates
     * by. Before this the column went blank on every row the moment a real order arrived, and the
     * port's GARAGE was not allowed to stand in for it — a room name beside work the house chose,
     * invented by the phone, is a lie a player has no way to check.
     *
     * Both surfaces, because both draw a destination and they were the two the escalation named.
     */
    @Test
    fun anOrderFromTheHouseSaysWhereTheWorkIs() {
        for (screen in listOf(ScreenId.Work, ScreenId.Home)) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) {
                        Screen(PanelState(screen = screen, order = dealt))
                    }
                }
                assertTrue(
                    destinations() > 0,
                    "$screen drew no destination for work the house dealt — the wire carries the " +
                        "room and the glyph now, and this is the column they belong in",
                )
                // The rooms are the house's, not the port's. A screen still reading the fixture
                // would draw GARAGE, HALL and STUDY and none of the three below.
                onNodeWithText("BOILER").assertExists()
                for (fixture in listOf("BED 2", "GARAGE")) {
                    onAllNodesWithText(fixture).assertCountEquals(0)
                }
            }
        }
    }

    /**
     * **THE INJECTION: a blocked line still carries nothing, the room included** (D-114, D-146).
     *
     * The destination is the newest thing a blocked row could leak and the most plausible: it names
     * no Subroutine, so it reads as harmless — and it points at the card the person **upstream of
     * you** is standing at, because D-146 made dependencies cross-player. The blackout is
     * structural on both halves: `OrderLine.Blocked` has one integer on it and [OrderRow.Blocked]
     * has one integer on it, so there is nothing for a composable to draw even if it wanted to.
     *
     * Counted rather than asserted absent, because the screen legitimately draws three of these:
     * an order with two blocked lines out of five must show **three** destinations and not five.
     */
    @Test
    fun aBlockedLineDrawsNoDestination() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Work, order = dealt))
                }
            }
            val known = dealt.count { it is OrderLine.Known }
            // Two nodes per named row -- the room and the glyph -- and none at all for a blocked
            // one. Anything higher is a blocked row that acquired a place in the house.
            assertEquals(
                known * 2, destinations(),
                "the destination column has more entries than the order has KNOWN lines: a " +
                    "blocked entry has been given a room, which names the player upstream of you " +
                    "as surely as naming the work would (D-114, D-146)",
            )
        }

    /**
     * **The circuit draws from real data** — the mix's *1 circuit*, dealt rather than drawn.
     *
     * ARRAY WIPE was reachable only from the port's fixture: it is not one of the roster's ten, had
     * no `SubroutineKind`, and no order the house could issue was able to contain it. It is
     * `AssignedWork.ArrayWipe` now, carrying D-122's three stations as its destinations, so the row
     * the design drew is a row the house can send.
     *
     * **The row draws the FIRST station**, which is where the circuit begins: a list read by
     * lamplight has room for one room name, and three would be worse than the one you walk to
     * first. Flagged as a presentation rather than defended.
     */
    @Test
    fun aCircuitLineDrawsAsArrayWipeWithItsFirstStation() =
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(PanelState(screen = ScreenId.Work, order = circuit))
                }
            }
            onNodeWithText(PanelVals.ARRAY_WIPE).assertExists()
            onNodeWithText("STUDY").assertExists()
            // The other two stations are on the line and not on the row.
            for (later in listOf("GARAGE", "HALL")) {
                onAllNodesWithText(later).assertCountEquals(0)
            }
            // BRIGHT for the circuit, and REPLAY's own BRIGHT beside it: two rows, two marks, and
            // the circuit's is the value written down in one place rather than typed on a fixture.
            assertEquals(mapOf(LightSignature.Bright to 2), marks())
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
