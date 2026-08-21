package home.someoneshome.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Short, driven by several fingers at once against a clock the test owns.**
 *
 * Batch 1 skipped this Subroutine rather than build it, on the grounds that *N fingers held for two
 * seconds* could not be looked at without a phone and this work may not use one. That turned out to
 * be answerable: Compose's input injector addresses pointers **by id**, so `down(0, …)` `down(1, …)`
 * `down(2, …)` puts three real pointers on the glass at once, and `mainClock` drives the two seconds
 * in virtual time exactly as it already does for HOLD TO DELETE. Nothing here is synthesised at the
 * semantics layer and nothing here is a click: the surface publishes no click action, because a
 * hold a single synthetic click could fire would not be a hold.
 *
 * **What this still does not prove** is the thing no desktop can: that three fingers arranged on a
 * 6-inch sheet of glass, in the dark, by somebody who is also watching a doorway, is a gesture a
 * person can make. That is a room test, and it is the same gap every Subroutine in this port has.
 *
 * ### The property under test is not "the hold works"
 *
 * It is that **the hold ends on the clock and never on the count**. A hold that completed when the
 * asked-for number of fingers arrived would be the phone grading the answer, and it would grade it
 * where a player could see: an Insider's fake hand would sit there while a Resident's right one
 * went. So the wrong number of fingers has to go, at the same moment, saying nothing.
 */
@OptIn(ExperimentalTestApi::class)
class ShortInputTest {

    /** The screen, wired to a model the test can then interrogate, with the clock stopped. */
    private fun DesktopComposeUiTest.show(
        model: SubroutineModel,
        role: PanelRole = PanelRole.Resident,
    ) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(
                    PanelState(screen = ScreenId.SubShort, role = role),
                    PanelActions(
                        tapSubroutine = model::tap,
                        handOverSubroutine = model::handOver,
                    ),
                    subroutines = model,
                )
            }
        }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(16)
    }

    /**
     * One more finger on the glass, at its own place on it.
     *
     * Spread across the surface rather than stacked, because that is where a hand puts them and
     * because two pointers at one position is a case the injector is entitled to treat as one.
     */
    private fun DesktopComposeUiTest.putDown(finger: Int) {
        onNodeWithTag(HOLD_SURFACE).performTouchInput {
            down(finger, percentOffset(0.2f + 0.15f * finger, 0.55f))
        }
        mainClock.advanceTimeBy(16)
    }

    private fun DesktopComposeUiTest.lift(finger: Int) {
        onNodeWithTag(HOLD_SURFACE).performTouchInput { up(finger) }
        mainClock.advanceTimeBy(16)
    }

    /** Long enough that a hold begun at the last change has certainly finished. */
    private fun DesktopComposeUiTest.waitOutTheHold() =
        mainClock.advanceTimeBy(HOLD_MILLIS + 200L)

    /**
     * **Three fingers, two seconds, and the entry goes as three.**
     *
     * The whole gesture through the real stack: pointers, the surface, the actions layer, the
     * entry. The count is checked before the hold completes as well as after, because the echo is
     * what the screen draws and an entry that only learned the count at the end would be a screen
     * showing nothing for two seconds.
     */
    @Test
    fun threeFingersHeldForTwoSecondsGoAsThree() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = SubroutineModel()
            show(model)

            repeat(3) { putDown(it) }
            assertEquals(3, model.short.fingers, "the surface did not see three pointers")
            assertNull(model.short.handedOver, "it went before the two seconds were up")

            waitOutTheHold()
            assertEquals(3, model.short.handedOver, "the hold did not go, or went as something else")
        }

    /**
     * **A hand that is still arriving has not begun holding.**
     *
     * *Hold N fingers for two seconds* is two seconds of an unchanging hand, so every finger that
     * lands starts it again — which is what the gesture is, and also the only reading under which
     * putting three fingers down one after another does not complete the moment the first one has
     * been there long enough.
     */
    @Test
    fun aFingerArrivingLateStartsTheTwoSecondsAgain() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = SubroutineModel()
            show(model)

            repeat(3) { putDown(it) }
            mainClock.advanceTimeBy(HOLD_MILLIS - 400L)
            assertNull(model.short.handedOver, "it went early")

            putDown(3)
            mainClock.advanceTimeBy(HOLD_MILLIS - 400L)
            assertNull(
                model.short.handedOver,
                "the fourth finger did not restart the hold — the two seconds ran from the first " +
                    "hand rather than from the one that was actually being held",
            )

            mainClock.advanceTimeBy(600L)
            assertEquals(4, model.short.handedOver, "the restarted hold never finished")
        }

    /**
     * **The wrong number of fingers goes at the same moment, and the screen says nothing about it.**
     *
     * This is the test the Subroutine exists behind. One finger where the house asked for three is
     * a fake's hand, a fumble in the dark, or a Resident who miscounted — and all three have to
     * look identical, in both roles, on a screen with no opinion. Injecting the bug it exists for
     * means giving `HoldEntry` the asked-for count and refusing the hand-over unless it matches.
     */
    @Test
    fun theWrongNumberOfFingersGoesJustTheSameAndInBothRoles() {
        for (role in PanelRole.entries) {
            for (fingers in listOf(1, SubroutineModel.SHORT_FINGERS, 5)) {
                runDesktopComposeUiTest(width = 300, height = 650) {
                    val model = SubroutineModel()
                    show(model, role)
                    repeat(fingers) { putDown(it) }
                    waitOutTheHold()
                    assertEquals(
                        fingers, model.short.handedOver,
                        "$role held $fingers finger(s) and the phone treated it differently from " +
                            "the asked-for ${SubroutineModel.SHORT_FINGERS}",
                    )
                    for (verdict in listOf("CORRECT", "WRONG", "TRY AGAIN", "COMPLETE", "FAILED")) {
                        assertTrue(
                            onAllNodes(hasText(verdict, substring = true))
                                .fetchSemanticsNodes().isEmpty(),
                            "$role held $fingers finger(s) and the screen said \"$verdict\"",
                        )
                    }
                }
            }
        }
    }

    /**
     * **Letting go early sends nothing, and leaves nothing behind.**
     *
     * Somebody walked in. The player's hand comes off the glass at 1.6 seconds and the entry has
     * to be as it was — the design's third constraint is that a Subroutine is interruptible, and an
     * interruption that quietly banked most of a hold would be the phone reporting work that was
     * abandoned.
     */
    @Test
    fun aHandLiftedBeforeTwoSecondsSendsNothing() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = SubroutineModel()
            show(model)

            repeat(3) { putDown(it) }
            mainClock.advanceTimeBy(HOLD_MILLIS - 400L)
            repeat(3) { lift(it) }
            assertEquals(0, model.short.fingers, "the glass still thinks a hand is on it")

            waitOutTheHold()
            assertNull(model.short.handedOver, "an abandoned hold went to the house anyway")
        }

    /**
     * **A hold that has gone takes nothing more, including a hand coming off it.**
     *
     * The echo after the hand-over is *what this phone sent*, which is the honest thing for it to
     * be showing while the house has not answered. A count draining back to nothing as the player
     * relaxes would read as the entry being withdrawn — on the one screen where the player cannot
     * check, because nothing on it ever says what the house did with it.
     */
    @Test
    fun theEchoHoldsWhatWentAfterTheHandComesOff() =
        runDesktopComposeUiTest(width = 300, height = 650) {
            val model = SubroutineModel()
            show(model)

            repeat(2) { putDown(it) }
            waitOutTheHold()
            assertEquals(2, model.short.handedOver)

            repeat(2) { lift(it) }
            assertEquals(2, model.short.fingers, "the echo drained after the entry had gone")

            putDown(0)
            waitOutTheHold()
            assertEquals(2, model.short.handedOver, "a second hold went on top of the first")
        }
}
