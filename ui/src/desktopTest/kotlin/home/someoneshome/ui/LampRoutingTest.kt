package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Haptic
import home.someoneshome.model.Sound
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The two effects that reach the light, and the five seconds one of them owns** (D-182, rule 7).
 *
 * `LampSet` is the level the house holds every phone at; `Surged` is the slam. **Neither passes
 * through [PanelState]**, which is what this file is really about: a `copy` is a recomposition, and
 * the whole mitigation for losing the anonymous revoke is that the lamp goes dark in the same frame as
 * phone contact. So the assertions below read [LampState] directly and check that the panel stayed
 * exactly where it was — `RoutingTest` holds the other half, that `LampSet` moves no field.
 *
 * **The clock is the test's.** D-182's five seconds are checked in microseconds, phase by phase, on
 * `mainClock`.
 */
@OptIn(ExperimentalTestApi::class)
class LampRoutingTest {

    /**
     * **The house sets the level and the lock screen is what draws it.**
     *
     * *Locked IS the lamp, and the house sets the level, not you.* Asserted as a value on
     * [LampState] rather than as pixels, because what the ground is *made of* is one `drawRect` and
     * what matters is that a level arriving reaches it at all — the pixels are `ScreenshotTest`'s.
     *
     * **`UNSET` is not black**, and the second half of this test is why that distinction is worth a
     * constant: a lamp nobody has set and a lamp the house set to nothing are opposite claims, and
     * collapsing them would make a bench's lock screen open dark.
     */
    @Test
    fun theHouseSetsTheLampAndNothingOnThePanelMoves() {
        val model = FlowModel(PanelState(screen = ScreenId.Lock))
        val before = model.state

        assertEquals(
            LampState.UNSET, model.lamp.authorised.intValue,
            "a phone no house has spoken to is already holding a level",
        )

        model.houseSaid(RoundMessage.LampSet(luminance = 1))
        assertEquals(
            lampArgb(1), model.lamp.authorised.intValue,
            "the house set the lamp and the lamp did not move",
        )
        assertNotEquals(
            LampState.UNSET, model.lamp.authorised.intValue,
            "a level the house really sent is indistinguishable from one it never sent",
        )
        assertEquals(
            before, model.state,
            "the lamp went through a state field — rule 7: that frame is an un-anonymised revoke",
        )

        // A blackout is a level like any other, and it is an OPAQUE colour rather than the absence
        // of one. This is the value the whole guarantee is about.
        model.houseSaid(RoundMessage.LampSet(luminance = 0))
        assertEquals(0xFF000000.toInt(), model.lamp.authorised.intValue, "black is not opaque black")
        assertEquals(before, model.state, "a blackout wrote a state field")
    }

    /**
     * **The Surge's five seconds, phase by phase, on the test clock** (D-182).
     *
     * 1.5s at full, 1.0s black, 2.5s of the cold-start Boot render, no input at any point. The
     * luminances are the ones the message carried and never values this module chose (rule 5), so
     * they are asserted against the message rather than against a constant here.
     *
     * **The Boot render is the cold-start one, unmodified**, which is why the third phase is a
     * screen and not a fourth colour: a surged phone and a phone that genuinely crashed have to be
     * the same picture, so that a Surge cannot be read off a shoulder.
     */
    @Test
    fun theSurgeRunsBrightThenDarkThenBootAndComesBack() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Work))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        model.houseSaid(RoundMessage.Surged(peak = 100, black = 0, sound = Sound.Surge, haptic = Haptic.Short))
        assertEquals(
            lampArgb(100), model.lamp.slam.intValue,
            "the slam waited for a frame — it has to be on the glass in the frame it arrived",
        )

        mainClock.advanceTimeBy(SURGE_PEAK_MS - 100L)
        assertEquals(lampArgb(100), model.lamp.slam.intValue, "the bright phase ended early")

        mainClock.advanceTimeBy(200L)
        assertEquals(lampArgb(0), model.lamp.slam.intValue, "the light never failed")
        assertEquals(ScreenId.Work, model.state.screen, "the phone rebooted during the dark phase")

        mainClock.advanceTimeBy(SURGE_BLACK_MS)
        assertEquals(ScreenId.Boot, model.state.screen, "the phone never came back up")
        assertEquals(
            LampState.UNSET, model.lamp.slam.intValue,
            "the slam outlived the reboot and painted over the Boot render",
        )

        // 200 rather than 100: the clock is already a tenth of a second past the reboot, because
        // the two assertions above each stepped over their own boundary.
        mainClock.advanceTimeBy(SURGE_BOOT_MS - 200L)
        assertEquals(ScreenId.Boot, model.state.screen, "the Boot render was cut short")

        mainClock.advanceTimeBy(200L)
        assertEquals(ScreenId.Home, model.state.screen, "the phone never finished coming back")
        assertNull(model.surge, "the Surge is still running after its five seconds")

        // **The three numbers, written out, and not derived from the constants under test.**
        // Everything above walks the sequence in terms of `SURGE_PEAK_MS` and its siblings, so it
        // passes just as happily on a build where the bright and dark phases have swapped lengths —
        // a list derived from the thing it is checking agrees with itself. These are D-182's own
        // figures, typed, so moving one is a diff that says what it is moving.
        assertEquals(1_500L, SURGE_PEAK_MS, "the full-bright phase is no longer the owner's 1.5s")
        assertEquals(1_000L, SURGE_BLACK_MS, "the dark phase is no longer the owner's 1.0s")
        assertEquals(2_500L, SURGE_BOOT_MS, "the Boot render is no longer the owner's 2.5s")
        assertEquals(
            5_000L, SURGE_PEAK_MS + SURGE_BLACK_MS + SURGE_BOOT_MS,
            "the downtime stopped being the owner's five seconds",
        )
    }

    /**
     * **The Boot screen does not walk the cold-start line while a Surge is running.**
     *
     * `Flow.autoAdvance` sends `Boot` to `Perms` after 2.4 seconds and the Surge holds it for 2.5,
     * so without the suppression the player would get a hundred milliseconds of the permissions
     * walk in the middle of a running round. A malfunctioning phone is not running its own timers,
     * which is the fiction and is also what the code has to do.
     */
    @Test
    fun aSurgedPhoneDoesNotFallThroughTheColdStart() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Work))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        model.houseSaid(RoundMessage.Surged(peak = 100, black = 0, sound = Sound.Surge, haptic = Haptic.Short))

        // **Stepped rather than jumped, because the fall-through is transient.** `Boot` walks to
        // `Perms` after 2.4s and the Surge holds it for 2.5, so a build that left the screen's own
        // timer running lands on `Perms` for a hundred milliseconds and then comes back to `Home`
        // anyway — invisible to any assertion that only reads the end of the sequence, and very
        // visible to a player. So every screen the phone stands on is collected.
        val stood = mutableListOf(model.state.screen)
        repeat(52) {
            mainClock.advanceTimeBy(100L)
            stood += model.state.screen
        }
        assertEquals(
            emptyList(), stood.filter { it == ScreenId.Perms },
            "a surged phone walked into the cold-start permissions screen mid-round: $stood",
        )
        assertEquals(
            ScreenId.Home, model.state.screen,
            "the surged phone did not come back to the round it is still in",
        )
        assertTrue(
            Flow.autoAdvance.getValue(ScreenId.Boot).to == ScreenId.Perms,
            "the Boot row stopped being the cold-start one and this test stopped meaning anything",
        )
    }

    /**
     * **The work in your hands is gone** (D-182), and it is gone rather than paused.
     *
     * `gdd.md:594`'s *interruptible — aborts cleanly with unambiguous progress state* is satisfied
     * and the state is **gone**: preserving progress would make the attack survivable by standing
     * still, and it would need a resume path, which is a second way into a Subroutine and the one
     * thing D-110 spent a ruling closing. One attempt per scan; the walk back is the cost.
     */
    @Test
    fun aSurgeEatsTheSubroutineInYourHands() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.SubReplay))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        model.beginSubroutine(Subroutine.Replay)
        model.tapSubroutine(Subroutine.Replay, 0)
        model.tapSubroutine(Subroutine.Replay, 2)
        assertTrue(model.subroutines.replay.touched, "the fixture entered nothing to lose")

        model.houseSaid(RoundMessage.Surged(peak = 100, black = 0, sound = Sound.Surge, haptic = Haptic.Short))
        mainClock.advanceTimeBy(SURGE_PEAK_MS + SURGE_BLACK_MS + 100L)

        assertEquals(
            false, model.subroutines.replay.touched,
            "a Surge left the open Subroutine standing — it is survivable by not moving",
        )
        assertNull(
            model.subroutines.standingAt,
            "the phone still thinks it is standing at the card it was surged away from",
        )
    }

    /**
     * **Two identical Surges are two Surges.**
     *
     * `Effect.Surged` carries the same peak and the same black every time — they are constants in
     * the rules — so a key that was only the two luminances would make a second Surge landing
     * mid-window an equal value: the sequence would not restart, and the phone would come back on
     * the first Surge's clock while still holding the second one's dark.
     */
    @Test
    fun aSecondSurgeMidWindowRestartsTheSequenceRatherThanInheritingIt() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Work))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        val surge = RoundMessage.Surged(peak = 100, black = 0, sound = Sound.Surge, haptic = Haptic.Short)
        model.houseSaid(surge)
        mainClock.advanceTimeBy(SURGE_PEAK_MS + SURGE_BLACK_MS - 100L)

        // A second one, a tenth of a second before the first would have rebooted the phone.
        model.houseSaid(surge)
        assertEquals(lampArgb(100), model.lamp.slam.intValue, "the second Surge did not slam")

        mainClock.advanceTimeBy(200L)
        assertEquals(
            lampArgb(100), model.lamp.slam.intValue,
            "the first Surge's timer cut the second one's bright phase short",
        )
        assertEquals(ScreenId.Work, model.state.screen, "the first Surge's timer rebooted the phone")

        mainClock.advanceTimeBy(SURGE_PEAK_MS + SURGE_BLACK_MS + SURGE_BOOT_MS)
        assertEquals(ScreenId.Home, model.state.screen, "the second Surge never finished")
    }

    /**
     * **The caught scan is the one flow, end to end** (D-124, D-188).
     *
     * The house answers a scan, the card comes up with its briefing on it, and BEGIN opens the work
     * — rendered rather than asserted as fields, because the briefing being *on that screen* is the
     * whole of D-188 and the screen is where it either is or is not.
     */
    @Test
    fun theScansAnswerOpensTheCardWithItsBriefingAndBeginOpensTheWork() = runDesktopComposeUiTest {
        val model = FlowModel(PanelState(screen = ScreenId.Scan))
        mainClock.autoAdvance = false
        setContent { DeviceCanvas(insets = PanelInsets()) { FlowHost(model) } }

        model.houseSaid(
            RoundMessage.WorkOrderIssued(
                listOf(
                    home.someoneshome.model.OrderLine.Known(
                        index = 0,
                        work = home.someoneshome.model.AssignedWork.Subroutine(SubroutineKind.Interrupt),
                        done = false,
                        at = listOf(
                            home.someoneshome.model.Destination(
                                home.someoneshome.model.MarkerId("card-hall"),
                                shape = "diamond",
                                room = "HALL",
                            ),
                        ),
                    ),
                ),
            ),
        )
        model.houseSaid(
            RoundMessage.ScanAnswered(
                home.someoneshome.model.SubroutineInstance(
                    entry = 0, subroutine = SubroutineKind.Interrupt, parameters = listOf(4),
                ),
            ),
        )
        mainClock.advanceTimeBy(100L)

        assertEquals(ScreenId.ScanCaught, model.state.screen)
        onAllNodesWithText(Subroutine.Interrupt.briefing).assertCountEquals(1)
        onAllNodesWithText("HALL . SUBROUTINE 1 OF 1").assertCountEquals(1)

        model.beginSubroutine(Subroutine.Interrupt)
        mainClock.advanceTimeBy(100L)
        assertEquals(
            ScreenId.SubInterrupt, model.state.screen,
            "BEGIN did not open the work the card the house answered for holds",
        )
        onAllNodesWithText(Subroutine.Interrupt.briefing).assertCountEquals(0)
    }
}
