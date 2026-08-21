package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The meeting's controls, driven with a real finger against the real screens.**
 *
 * `MeetingTest` holds the model's properties and `ScreenGraphTest` fires every click action; both
 * would pass on a vote screen whose rows are not wired to anything, whose selection never moves,
 * or whose targets are too small to hit in the dark. These press the screens.
 *
 * Two of them matter more than the rest. The first is that a press **echoes and nothing else
 * happens** — the screen stays where it is and the house's counts do not move. The second is that
 * these screens do not run a clock: a countdown is the single most tempting thing here to
 * implement locally, and a phone that counted down on its own would be right for about a second
 * and then quietly wrong for the rest of the meeting.
 */
@OptIn(ExperimentalTestApi::class)
class MeetingInputTest {

    private fun modelOn(screen: ScreenId): FlowModel =
        FlowModel(PanelState(screen = ScreenId.Home)).also { it.push(screen) }

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(
                    model.state, model.actions(), model.editor, model.homes, model.lobby,
                    model.meeting,
                )
            }
        }
        mainClock.autoAdvance = false
    }

    /** The same, with [FlowHost] behind it, so anything that runs on a clock really runs. */
    private fun DesktopComposeUiTest.drive(model: FlowModel) {
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }
        mainClock.autoAdvance = false
    }

    private fun DesktopComposeUiTest.says(text: String): Boolean =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    // ---- The vote ------------------------------------------------------------------------------

    /**
     * **The row you touch is the row that lights, and only that row.**
     *
     * Echo of your own input is the one piece of optimism this app permits (D-097). It is also the
     * only thing on this screen that responds at all, so a selection that failed to move would
     * read as a phone that had stopped.
     */
    @Test
    fun theRowYouTapIsTheOneThatLights() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = modelOn(ScreenId.Vote)
        show(model)

        // The fixture arrives on MARCUS, which is what the design drew.
        assertTrue(model.meeting.holds("MARCUS"))

        onNodeWithText("DANI").performClick()
        mainClock.advanceTimeBy(50)
        assertTrue(model.meeting.holds("DANI"), "a tap on a name did not reach the ballot")
        assertFalse(model.meeting.holds("MARCUS"), "the old vote stayed lit beside the new one")

        // And the screen says so: exactly one row carries the mark.
        assertEquals(
            1, onAllNodes(hasText("YOUR VOTE", substring = true)).fetchSemanticsNodes().size,
            "the ballot marks more than one row as this phone's vote",
        )
    }

    /** Changeable until the clock ends, which means a second tap really moves it. */
    @Test
    fun theVoteMovesAsManyTimesAsThePlayerLikes() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = modelOn(ScreenId.Vote)
            show(model)

            for (name in listOf("ROSE", "TOMAS", "PRIYA", "DANI")) {
                onNodeWithText(name).performClick()
                mainClock.advanceTimeBy(50)
                assertTrue(model.meeting.holds(name), "the ballot stuck on an earlier vote")
            }
        }

    /** SKIP is a row like the others: restraining nobody is a vote, not the absence of one. */
    @Test
    fun skipIsAVoteAndNotAnEmptyOne() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = modelOn(ScreenId.Vote)
        show(model)

        onNodeWithText("SKIP").performClick()
        mainClock.advanceTimeBy(50)
        assertTrue(model.meeting.skipping, "SKIP is drawn as a row and wired to nothing")
        assertFalse(model.meeting.holds("MARCUS"))
    }

    /**
     * **LOCK IN hands the vote over and the screen stays exactly where it is.**
     *
     * The one that would have been easiest to get wrong and hardest to notice: the result screen
     * exists, the button is called LOCK IN, and walking to it on a press produces a phone showing
     * one player a tally the house has not read.
     */
    @Test
    fun lockingInHandsItOverAndGoesNowhere() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = modelOn(ScreenId.Vote)
        show(model)

        onNodeWithText("LOCK IN").performClick()
        mainClock.advanceTimeBy(50)

        assertEquals(VoteChoice.Named("MARCUS"), model.meeting.handedOver)
        assertEquals(ScreenId.Vote, model.state.screen, "one phone's press walked to the result")
        onNodeWithText("LOCKED IN").assertExists()
        // The house's count is the house's. It did not move because this phone pressed a button.
        onNodeWithText("4 OF 6 VOTED", substring = true).assertExists()
    }

    /**
     * The button is **present and inert** with nothing chosen, rather than absent and then
     * appearing — a control that materialised under a thumb about to press it is worse than one
     * that is visibly not ready.
     */
    @Test
    fun lockInIsPresentAndInertWithNothingChosen() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(PanelState(screen = ScreenId.Home), meeting = MeetingModel.sample())
            // A meeting begins with nothing said, which is the state this asserts against.
            model.push(ScreenId.Call)
            model.push(ScreenId.Vote)
            show(model)

            onNodeWithText("LOCK IN").assertExists()
            assertEquals(
                0,
                onAllNodes(hasClickAction() and hasText("LOCK IN", substring = true))
                    .fetchSemanticsNodes().size,
                "an empty vote could be handed over",
            )
        }

    /** Changing the vote after locking it in stops the button claiming the house has this one. */
    @Test
    fun movingTheVoteAfterLockingItInSaysSoOnTheButton() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = modelOn(ScreenId.Vote)
            show(model)

            onNodeWithText("LOCK IN").performClick()
            mainClock.advanceTimeBy(50)
            assertTrue(says("LOCKED IN"))

            onNodeWithText("ROSE").performClick()
            mainClock.advanceTimeBy(50)
            assertFalse(
                says("LOCKED IN"),
                "the button said the house held ROSE, and the house has never heard of ROSE",
            )
            onNodeWithText("LOCK IN").assertExists()
        }

    // ---- Checking in ----------------------------------------------------------------------------

    /**
     * **I AM HERE lights your own tick, the count does not move, and the meeting does not start.**
     *
     * D-104: the talk waits for every living player *and* every out player. One phone cannot know
     * that has happened, so one phone must not act as though it has.
     */
    @Test
    fun checkingInEchoesAndTheGateStaysWhereItWas() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            for (screen in listOf(ScreenId.Assemble, ScreenId.Ghost2)) {
                val model = modelOn(screen)
                show(model)

                onNodeWithText("4 OF 6 CHECKED IN").assertExists()
                onNodeWithText("I AM HERE").performClick()
                mainClock.advanceTimeBy(50)

                assertTrue(model.meeting.checkedIn, "$screen: the check-in did not reach the model")
                assertEquals(screen, model.state.screen, "$screen: one phone's press started the talk")
                onNodeWithText("YOU ARE HERE").assertExists()
                // The house's number, unmoved. It counts phones; this one counted itself. The
                // negative is asserted first so a phone that helpfully incremented the gate is
                // named as such rather than reported as a missing node.
                assertFalse(says("5 OF 6 CHECKED IN"), "$screen: the phone advanced the house's gate")
                onNodeWithText("4 OF 6 CHECKED IN").assertExists()
            }
        }

    /** Once you are there you are there: the spent control keeps its place and loses its press. */
    @Test
    fun aSpentCheckInIsStillOnScreenAndNoLongerAControl() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = modelOn(ScreenId.Assemble)
            show(model)

            onNodeWithText("I AM HERE").performClick()
            mainClock.advanceTimeBy(50)

            assertEquals(
                0, onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
                "a checked-in phone still offers a control, and it can only lie",
            )
        }

    /** READY TO VOTE is the same shape: your hand up, and a talk that ends when the house says. */
    @Test
    fun sayingYouAreReadyEchoesAndSkipsNothingAhead() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = modelOn(ScreenId.Discussion)
            show(model)

            onNodeWithText("READY TO VOTE").performClick()
            mainClock.advanceTimeBy(50)

            assertTrue(model.meeting.ready)
            assertEquals(ScreenId.Discussion, model.state.screen, "one READY skipped the talk ahead")
            onNodeWithText("YOU ARE READY").assertExists()
            onNodeWithText("3 OF 6 READY", substring = true).assertExists()
        }

    // ---- The clocks ------------------------------------------------------------------------------

    /**
     * **No screen in this game runs a clock.**
     *
     * With [FlowHost] behind them and the test clock advanced most of a minute, every countdown
     * reads exactly what it read on arrival. A local tick would be invisible in review, would look
     * right on one phone, and would put six phones in a dark house on six different clocks.
     */
    @Test
    fun noCountdownAdvancesOnTheDevice() {
        val held = listOf(
            ScreenId.Vote to "0:38",
            ScreenId.Discussion to "1:04",
            ScreenId.GhostMeeting to "0:24",
            ScreenId.Tally to "LIGHTS OUT IN 9",
        )
        for ((screen, figure) in held) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                val model = modelOn(screen)
                drive(model)

                onNodeWithText(figure, substring = true).assertExists()
                // Short of the window, so the house's own fall-through has not fired either.
                mainClock.advanceTimeBy(6_000)
                assertEquals(screen, model.state.screen, "$screen moved before its window closed")
                assertTrue(says(figure), "$screen ran its own clock: $figure was redrawn as something else")
            }
        }
    }

    /** And when the house sends a number, the screen shows the house's number. */
    @Test
    fun theFigureOnScreenIsTheOneTheHouseSent() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = FlowModel(PanelState(screen = ScreenId.Vote, secondsLeft = 7))
            show(model)

            onNodeWithText("0:07").assertExists()
            assertFalse(says("0:38"), "the fixture's moment outlived the house's")
        }

    // ---- Tap targets ------------------------------------------------------------------------------

    /**
     * **Every control at a meeting is big enough to hit in the dark.**
     *
     * The measurement is real pixels off the rendered tree, converted back to design units, so it
     * cannot pass by agreeing with a constant. [TAP_TARGET] is 36 units, which clears Apple's 44pt
     * on the narrowest phone this app targets.
     *
     * This is the screen where it matters most and the reason is not comfort: a finger landing one
     * row off restrains the wrong resident, in an unlit room, under a rule that forbids saying so
     * out loud. The rest of the app is measured here too and **reported rather than asserted** —
     * the springboard and the host-setup screens were drawn to a different brief and bringing them
     * up is a design pass, not an overnight edit.
     */
    @Test
    fun everyControlAtAMeetingIsBigEnoughToHitInTheDark() {
        val theMeeting = setOf(
            ScreenId.Calling, ScreenId.Call, ScreenId.Found, ScreenId.Assemble, ScreenId.Notice,
            ScreenId.Discussion, ScreenId.Vote, ScreenId.Tally,
            ScreenId.Ghost2, ScreenId.GhostMeeting,
        )
        val small = mutableListOf<String>()
        val elsewhere = mutableListOf<String>()

        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 300, height = 650) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(PanelState(role = role).arrivingAt(id))
                        }
                    }
                    val perUnit = onRoot().fetchSemanticsNode().size.width / DESIGN_WIDTH
                    for (node in onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
                        val units = node.size.height / perUnit
                        if (units >= TAP_TARGET.value) continue
                        val what = node.config.getOrNull(SemanticsProperties.Text)
                            ?.joinToString(" ") { it.text }?.take(28) ?: "(unlabelled)"
                        val line = "$id/${role.name} — \"$what\" is ${units.toInt()}u, " +
                            "under ${TAP_TARGET.value.toInt()}u"
                        if (id in theMeeting) small += line else elsewhere += line
                    }
                }
            }
        }

        elsewhere.distinct().forEach { println("TAP (reported, not asserted)  $it") }
        small.forEach { println("TAP  $it") }
        assertEquals(
            emptyList(), small.toList(),
            "controls at a meeting that a thumb cannot reliably hit:" +
                small.joinToString("") { "\n  $it" },
        )
    }
}
