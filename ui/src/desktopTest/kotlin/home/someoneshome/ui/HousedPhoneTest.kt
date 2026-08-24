package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **With a house attached, nothing fake may move this phone.**
 *
 * The reproduction of the owner's three fictional meetings, kept as a test so that the evening
 * cannot happen twice.
 *
 * ### What happened, and why no existing test could see it
 *
 * Two real phones sat beside a real house all evening with the bench's fake house also driving
 * them. Every meeting ran itself: `ASSEMBLE → NOTICE → DISCUSSION → VOTE → TALLY` in **21.1
 * seconds**, a discussion whose real length is ninety, with `PHASE -` on every logged line because
 * the house had opened no phase at all. The player could not vote, and a ballot cast into it would
 * have gone nowhere.
 *
 * `ASSEMBLE → NOTICE` took **4.0 seconds**, which is [HOUSE_STAND_IN_MS] exactly, on a screen with
 * no [Flow.autoAdvance] row — so the fake house was firing the gate's own push. And `NOTICE →
 * DISCUSSION` took nine, which is a **production** `autoAdvance` row and had nothing to do with the
 * bench at all. **Two independent stand-ins, one symptom**, which is why switching either off alone
 * left a phone still telling a story.
 *
 * [FlowHostTest] could not catch it because every test there is either housed or unhoused by
 * implication and never both at once: the fixture that walks the whole meeting *wants* the stand-in,
 * and the one that waits at the gate simply omits it. Neither ever asked the question this file
 * asks — **what does a phone do when a real house is attached AND a stand-in is switched on** —
 * which is the exact state two phones were in for an entire evening.
 *
 * ### The pair, in both directions
 *
 * Housed: parked forever, until the house pushes or the player taps. Unhoused: the port walks
 * itself exactly as it always has. The second half is not a formality — the standalone mode is what
 * makes a screen reviewable on a desktop and a bench walkable past the check-in gate, and a fix that
 * stopped the fiction by stopping the port would have been reported as a success.
 */
class HousedPhoneTest {

    /** Long enough to cover the stand-in, the notice hold, and a ninety-second discussion. */
    private val thirtySeconds = 30_000L

    /**
     * **THE REPRODUCTION.** A housed phone parked on the check-in screen stays on it.
     *
     * `standingInForTheHouse` is `true` here on purpose — that is not a mistake in the test, it is
     * the state the owner's phones were actually in. The bench's switch said *stand in for the
     * house*; a house was standing right there. The whole finding is that the second fact has to
     * win, and it must win **without** anyone remembering to turn the first one off, because the
     * evening proved that nobody does.
     *
     * D-104 is what is at stake: the gate closes when the last player walks in, so a phone that
     * moves itself on after four seconds is one player at the talk while five are still walking —
     * and, worse, it is indistinguishable on screen from a gate that closed early.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aHousedPhoneParkedOnAssembleNeverWalksItselfOff() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true))
        mainClock.autoAdvance = false
        setContent {
            DeviceCanvas(insets = PanelInsets()) { FlowHost(model, standingInForTheHouse = true) }
        }

        // **The picker jump the owner made, built exactly as the picker builds it** — a fresh
        // `PanelState`, which carries `houseAttached = false` by default. That default is the
        // second half of the defect: the jump itself used to tell the phone it had no house, on
        // the one screen where the fake house then had a push waiting to fire.
        model.jump(PanelState(screen = ScreenId.Assemble))
        mainClock.advanceTimeBy(thirtySeconds)

        assertEquals(
            ScreenId.Assemble, model.state.screen,
            "a house was attached and something else moved the phone anyway — " +
                "this is the owner's fictional meeting, reproduced",
        )
    }

    /**
     * **The other stand-in, and it is production code rather than the bench.**
     *
     * `Notice → Discussion` after nine seconds is a real [Flow.autoAdvance] row. It fired on the
     * owner's phones for the same reason the four-second one did, and it would still have fired
     * with the bench's fake house switched off — which is what makes it the half of E-PAY-8 that
     * survives the vote beat's one-line cause.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aHousedPhoneOnNoticeNeverWalksItselfIntoTheDiscussion() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Notice, houseAttached = true))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        mainClock.advanceTimeBy(thirtySeconds)

        assertEquals(
            ScreenId.Notice, model.state.screen,
            "the notice hold walked a housed phone into a discussion the house had not opened",
        )
    }

    /**
     * The whole meeting, housed, with both stand-ins available and neither permitted to fire.
     *
     * The owner's twenty-one seconds, run again with a house attached: the phone must be on the
     * screen it started on at the end of it, every time, from every beat of the meeting.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyMeetingScreenParksAHousedPhoneUntilTheHouseSpeaks() = runDesktopComposeUiTest {
        for (screen in listOf(
            ScreenId.Call, ScreenId.Calling, ScreenId.Found, ScreenId.Assemble,
            ScreenId.Notice, ScreenId.Discussion, ScreenId.Vote, ScreenId.Tally,
        )) {
            val model = FlowModel(PanelState(screen = screen, houseAttached = true))
            mainClock.autoAdvance = false
            setContent {
                DeviceCanvas(insets = PanelInsets()) { FlowHost(model, standingInForTheHouse = true) }
            }

            mainClock.advanceTimeBy(thirtySeconds)

            assertEquals(
                screen, model.state.screen,
                "a housed phone left ${screen.name} on its own — the house never said so",
            )
        }
    }

    /**
     * **Parked is not frozen.** The house still moves a housed phone, in the same frame it speaks.
     *
     * The fix's own failure mode, and the reason this test is here rather than assumed: a phone that
     * ignored the stand-ins by ignoring everything would pass every assertion above and be a much
     * worse bug than the one being fixed.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aHousedPhoneStillMovesWhenTheHousePushes() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Assemble, houseAttached = true))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        mainClock.advanceTimeBy(thirtySeconds)
        assertEquals(ScreenId.Assemble, model.state.screen, "it moved before the house said anything")

        model.push(ScreenId.Notice)
        mainClock.advanceTimeBy(100L)
        assertEquals(
            ScreenId.Notice, model.state.screen,
            "the house pushed a housed phone and the phone did not move",
        )
    }

    /** And the player's own thumb still works, which is the other half of *parked is not frozen*. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aHousedPhoneStillMovesWhenThePlayerTaps() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Home, houseAttached = true))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        model.go(ScreenId.Work)
        mainClock.advanceTimeBy(100L)
        assertEquals(ScreenId.Work, model.state.screen, "a tap on a housed phone reached nothing")
    }

    /**
     * **The unhoused port still walks itself, and it has to.**
     *
     * The standalone mode is the whole reason [Flow.autoAdvance] and the bench's fake house exist:
     * a desktop render harness and a phone with no round attached both need the ported flows to be
     * walkable end to end. This is the direction a fix could quietly destroy while every other test
     * on the page went green.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theUnhousedPortStillWalksTheWholeMeetingByItself() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Call, houseAttached = false))
        mainClock.autoAdvance = false
        setContent {
            DeviceCanvas(insets = PanelInsets()) { FlowHost(model, standingInForTheHouse = true) }
        }

        val walked = mutableListOf(model.state.screen)
        repeat(6) {
            val at = model.state.screen
            val wait = Flow.autoAdvance[at]?.afterMillis?.toLong() ?: HOUSE_STAND_IN_MS
            mainClock.advanceTimeBy(wait + 100L)
            walked += model.state.screen
        }
        assertEquals(
            listOf(
                ScreenId.Call, ScreenId.Assemble, ScreenId.Notice, ScreenId.Discussion,
                ScreenId.Vote, ScreenId.Tally, ScreenId.Home,
            ),
            walked,
            "the standalone port stopped walking itself — the fix took the bench down with the fiction",
        )
    }

    /** And its presentation timers, which are the port's alone and must survive with it. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theUnhousedPortStillFallsThroughItsOwnTimers() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Boot, houseAttached = false))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        val rule = Flow.autoAdvance.getValue(ScreenId.Boot)
        mainClock.advanceTimeBy(rule.afterMillis + 100L)
        assertEquals(rule.to, model.state.screen, "the port's own fall-through stopped firing")
    }
}
