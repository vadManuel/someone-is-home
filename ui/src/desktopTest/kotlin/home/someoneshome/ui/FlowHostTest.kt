package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [FlowHost] actually driving, rather than a table that says it would.
 *
 * `FlowTest` proves the auto-advance rules are well-formed — no dead ends, no orphans, no stalls.
 * That is a claim about a map. This is the claim about the car: put the host in a composition, let
 * the clock run, and watch the screens change on their own.
 *
 * The distinction has already cost this project twice. A `LaunchedEffect` keyed on the wrong thing
 * restarts its delay every recomposition and never fires; keyed on nothing at all it fires once
 * and then the screen sits there for the rest of the round. Neither is visible in the table and
 * neither fails to compile — the only symptom is a phone in a dark house that does not move, and
 * by then eight people are standing in it.
 *
 * **The clock is the test's, not the wall's.** `mainClock` drives the composition and the
 * coroutines inside it, so a ninety-second discussion is checked in microseconds and the test
 * never waits on real time.
 */
class FlowHostTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSelfTestFallsThroughOnItsOwn() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Boot))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        val rule = Flow.autoAdvance.getValue(ScreenId.Boot)
        mainClock.advanceTimeBy(rule.afterMillis - 100L)
        assertEquals(ScreenId.Boot, model.state.screen, "boot fell through early")

        mainClock.advanceTimeBy(200L)
        assertEquals(rule.to, model.state.screen, "boot never fell through")
    }

    /**
     * The whole meeting, unattended: ring, walk in, notices, talk, vote, result, lights out.
     *
     * Every step is one the house takes, and each one has to hand off to the next. A chain that
     * stops in the middle is the failure this exists for — the room would be standing there
     * looking at a screen that has stopped being true, with no control on it.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theHouseWalksTheWholeMeetingWithoutBeingTouched() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Call))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        val walked = mutableListOf(model.state.screen)
        repeat(6) {
            val rule = Flow.autoAdvance.getValue(model.state.screen)
            mainClock.advanceTimeBy(rule.afterMillis + 100L)
            walked += model.state.screen
        }
        assertEquals(
            listOf(
                ScreenId.Call, ScreenId.Assemble, ScreenId.Notice, ScreenId.Discussion,
                ScreenId.Vote, ScreenId.Tally, ScreenId.Home,
            ),
            walked,
        )
    }

    /**
     * A tap during a window cancels the window it was waiting on.
     *
     * The scan's ten seconds are a safety device — the light dies and the phone goes back where it
     * was, so nobody stands in a dark room holding a lit screen at a wall by accident. A player
     * who leaves before then must not be dragged somewhere ten seconds later by a timer that was
     * still running, which is precisely what a stale coroutine does.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun leavingAScreenEarlyCancelsWhatItWasWaitingFor() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Scan))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        mainClock.advanceTimeBy(1_000)
        model.go(ScreenId.Work)
        mainClock.advanceTimeBy(1_000)
        assertEquals(ScreenId.Work, model.state.screen)

        // Past the end of the scan window it never finished. Work owes no advance, so nothing
        // may move.
        mainClock.advanceTimeBy(Flow.autoAdvance.getValue(ScreenId.Scan).afterMillis + 1_000L)
        assertEquals(ScreenId.Work, model.state.screen, "a spent scan window moved the phone anyway")
    }
}
