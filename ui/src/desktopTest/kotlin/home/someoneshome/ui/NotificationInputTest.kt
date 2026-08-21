package home.someoneshome.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The swipe, with a real finger on the real screen (D-105).**
 *
 * *Swipe up dismisses it, and that is the whole gesture vocabulary.* Nothing about that sentence
 * is checkable by a rendering test that fires click actions, and nothing about it is checkable by
 * calling [FlowModel.dismissNotification] directly either — that proves the actions layer moves
 * the phone, not that any finger can reach it. The failure mode this is written for is a banner
 * that looks perfect, has a working dismissal behind it, and cannot be dismissed: a drag detector
 * that never fires because the tap underneath it eats the gesture, a threshold nobody can reach
 * with a thumb, a gesture that also fires downwards.
 *
 * So these drive the whole path — pointer events, touch slop, the drag detector, the actions
 * layer, the screen that comes back — against the real screens with a real [FlowModel] behind
 * them, the way `HostSetupInputTest` drives the two-second hold.
 *
 * ### The distances are read out of the running composition, never guessed
 *
 * Touch slop is the platform's and the dismiss threshold is the design's, and both are in pixels
 * of a canvas whose density this test does not set. A hardcoded "swipe by 60" is a test that
 * passes today and silently stops exercising the branch it names the first time either number
 * moves — and "stops exercising" is invisible, because it keeps passing.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationInputTest {

    /** What the composition itself says the two distances are, filled in on first composition. */
    private class Distances {
        var slop = 0f
        var dismiss = 0f

        /** Far enough that the drag is certainly a dismissal. */
        val decisive: Float get() = slop + dismiss * 2

        /** Past slop — so a real drag is happening — and short of the threshold. */
        val hesitant: Float get() = slop + dismiss / 2
    }

    private fun DesktopComposeUiTest.show(
        model: FlowModel,
        at: Distances,
        insets: PanelInsets = PanelInsets(),
    ) {
        setContent {
            DeviceCanvas(insets = insets) {
                at.slop = LocalViewConfiguration.current.touchSlop
                at.dismiss = with(LocalDensity.current) { SWIPE_DISMISS.toPx() }
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
            }
        }
    }

    /**
     * A finger on the banner, dragged [travel] pixels — negative is up — and lifted.
     *
     * In steps rather than one jump, because that is what a finger does and because a single
     * enormous move is the one gesture shape a drag detector can get right by accident.
     */
    private fun DesktopComposeUiTest.swipeBanner(body: String, travel: Float) {
        onNode(hasText(body, substring = true)).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, travel / STEPS)) }
            up()
        }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.bannerIsUp(body: String): Boolean =
        onAllNodes(hasText(body, substring = true)).fetchSemanticsNodes().isNotEmpty()

    // ---- The gesture -------------------------------------------------------------------------

    /**
     * **Swipe up, and the house's text is gone — off the panel, and off this phone.**
     *
     * What is left is the springboard the banner arrived over, which is the same screen it was
     * drawn on top of the whole time. Nothing else moves: the player is not taken into Messages,
     * not taken back to page 1 from somewhere else, and not shown a confirmation.
     */
    @Test
    fun aSwipeUpTakesTheBannerAway() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val model = FlowModel(PanelState(screen = ScreenId.Notify))
        val at = Distances()
        show(model, at)

        assertTrue(bannerIsUp(Notifications.text.body), "the banner was not up to begin with")
        swipeBanner(Notifications.text.body, -at.decisive)

        assertEquals(ScreenId.Home, model.state.screen, "the swipe did not take the banner away")
        assertTrue(!bannerIsUp(Notifications.text.body), "the banner is still drawn")
        assertEquals(
            null, Notifications.onScreen(model.state.screen),
            "the screen left behind still has a notification on it",
        )
    }

    /** The Egress alert goes the same way, because there is one gesture and not one per kind. */
    @Test
    fun theEgressAlertGoesTheSameWay() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val model = FlowModel(PanelState(screen = ScreenId.Banner))
        val at = Distances()
        show(model, at)

        swipeBanner(Notifications.egress.body, -at.decisive)
        assertEquals(ScreenId.Home, model.state.screen, "the Egress alert would not swipe away")
    }

    /**
     * **A finger that stops short leaves it exactly where it was.**
     *
     * The branch that matters most and the one easiest to lose. A dismissal that fires as soon as
     * the drag begins throws away the one thing the house said, to a player who was reading it, in
     * the dark, with no way to get it back — and it would be indistinguishable from a phone that
     * simply never showed them the banner.
     */
    @Test
    fun aFingerThatStopsShortLeavesItWhereItWas() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val model = FlowModel(PanelState(screen = ScreenId.Notify))
        val at = Distances()
        show(model, at)

        swipeBanner(Notifications.text.body, -at.hesitant)
        assertEquals(ScreenId.Notify, model.state.screen, "a short drag dismissed it anyway")
        assertTrue(bannerIsUp(Notifications.text.body), "a short drag took the banner away")
    }

    /**
     * **Down is not a shorter up.**
     *
     * One gesture is the whole vocabulary, and a banner that also answered a downward drag would
     * be teaching a second one — in a game whose entire input surface is a thumb on a dark screen,
     * where every gesture that exists has to be learned once and relied on afterwards.
     */
    @Test
    fun downIsNotAGesture() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val model = FlowModel(PanelState(screen = ScreenId.Notify))
        val at = Distances()
        show(model, at)

        swipeBanner(Notifications.text.body, at.decisive)
        assertEquals(ScreenId.Notify, model.state.screen, "dragging the banner DOWN dismissed it")
        assertTrue(bannerIsUp(Notifications.text.body), "dragging down took the banner away")
    }

    /**
     * **A tap still opens it.**
     *
     * Both gestures live on the same surface and one of them is drawn on top of the other, which
     * is exactly the arrangement that quietly eats a tap. Where a notification is a door, tapping
     * it goes through the door; the swipe is for the times it is not worth walking through.
     *
     * **The screen is written out rather than read back off [Notification.opens].** A test that
     * asserts a value equals itself passes with the value wrong — this one was written that way
     * first, and a banner retargeted from Messages to Notes went through it green (and through
     * `ScreenGraphTest` too, because everything the springboard reaches is already an exit of the
     * screen the banner is drawn on). Tapping the house's text opens **Messages**: that is the
     * design's fact and it belongs here as a second, independent copy of itself.
     */
    @Test
    fun aTapOpensItRatherThanDismissingIt() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val model = FlowModel(PanelState(screen = ScreenId.Notify))
        show(model, Distances())

        onNode(hasText(Notifications.text.body, substring = true)).performClick()
        waitForIdle()
        assertEquals(
            ScreenId.Reveal, model.state.screen,
            "a tap on the house's text did not open Messages",
        )
    }

    /** The same for the Egress alert: the tap opens the widget holding the countdown. */
    @Test
    fun aTapOnTheEgressAlertOpensTheWidget() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val model = FlowModel(PanelState(screen = ScreenId.Banner))
        show(model, Distances())

        onNode(hasText(Notifications.egress.body, substring = true)).performClick()
        waitForIdle()
        assertEquals(
            ScreenId.EgressWidget, model.state.screen,
            "a tap on the Egress alert did not open the countdown it is about",
        )
    }

    // ---- The light ---------------------------------------------------------------------------

    /**
     * **The dim goes when the banner goes.**
     *
     * [NOTIFIED_DIM] is not styling. A phone held as a lamp faces away from its owner, so the drop
     * in emitted light *is* the notification for anyone who cannot read their own screen — and it
     * is world-observable, which is why every banner has to go to everybody at once. The
     * consequence checked here is the other end of that: once the banner is gone the panel is back
     * to full, in one step, with nothing left half-dimmed.
     *
     * Measured off the pixels rather than off the flag that sets them, because the two are only
     * the same thing while [PanelFrame] keeps them the same thing.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theDimGoesWithTheBanner() = runDesktopComposeUiTest(width = 600, height = 1300) {
        val model = FlowModel(PanelState(screen = ScreenId.Notify))
        val at = Distances()
        show(model, at)

        val dimmed = meanBrightness(onRoot().captureToImage().toAwtImage())
        swipeBanner(Notifications.text.body, -at.decisive)
        val lit = meanBrightness(onRoot().captureToImage().toAwtImage())

        assertEquals(ScreenId.Home, model.state.screen, "the swipe did not land")
        assertTrue(
            lit > dimmed * 1.5,
            "the panel behind the banner was at $dimmed and came back to $lit — the dim did not " +
                "go with the banner, and the phone is still emitting a signal nobody authored",
        )
    }

    /**
     * **The status row does not move when the banner does.**
     *
     * The bar is the one thing on screen that stays put while something arrives — it is how a
     * player confirms the perimeter is still armed and the clock is still the clock, which is
     * exactly the reassurance a takeover would take away. A banner tracking a finger is content
     * moving through a region it does not own, and the only reason it stops at the boundary is
     * that [PanelFrame] clips it there.
     *
     * Held with the finger still down and the banner **part way into the band**, because that is
     * the only moment it can be wrong. Both extremes pass for the wrong reason: at rest the banner
     * has not moved, and a swipe carried far enough has taken it clean off the top of the screen
     * again. The first version of this test made the second mistake and passed with the clip
     * deleted — see the worklog.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theStatusRowDoesNotMoveWhenTheBannerDoes() = runDesktopComposeUiTest(width = 600, height = 1300) {
        // A phone with a notch, so there is a band to intrude into. The same measurements
        // DeviceLayoutTest renders every screen against.
        val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)
        val model = FlowModel(PanelState(screen = ScreenId.Notify))
        val at = Distances()
        show(model, at, insets)

        val atRest = onRoot().captureToImage().toAwtImage()
        onNode(hasText(Notifications.text.body, substring = true)).performTouchInput {
            down(center)
            // Past the threshold, so this is a real dismissal in progress, and short of the
            // banner's own height, so the banner is straddling the boundary rather than gone.
            repeat(STEPS) { moveBy(Offset(0f, -(at.slop + at.dismiss * 2) / STEPS)) }
        }
        waitForIdle()
        val midSwipe = onRoot().captureToImage().toAwtImage()

        // The band the status row occupies, in image pixels, taken from the insets rather than
        // from a number typed here.
        val band = (insets.top.value * midSwipe.width / DESIGN_WIDTH).toInt()

        // THE TEST CHECKS ITSELF FIRST. `positionInRoot` is where the banner was laid out, before
        // any clipping — so this asserts the banner really is over the status band right now, and
        // that what follows is measuring the clip rather than measuring a banner that happens to
        // be somewhere else. Without it, a swipe that carried the banner clean off the top would
        // pass while proving nothing.
        val bannerTop = onNode(hasText(Notifications.text.body, substring = true))
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            bannerTop < band,
            "the banner is at $bannerTop and the band ends at $band — it is not over the row at " +
                "all, so this test is not measuring anything",
        )
        val moved = mutableListOf<String>()
        outer@ for (y in 0 until band) {
            for (x in 0 until midSwipe.width) {
                if (atRest.getRGB(x, y) != midSwipe.getRGB(x, y)) {
                    moved += "($x,$y)"
                    if (moved.size >= 4) break@outer
                }
            }
        }
        assertTrue(
            moved.isEmpty(),
            "the banner reached into the status band while being swiped away — first at " +
                moved.joinToString(", ") + ". The bar is the one thing that stays put.",
        )
    }

    /**
     * Mean brightness of the lower half of the panel — below anything a banner covers, so what is
     * being compared is the springboard against itself, dimmed and not.
     */
    private fun meanBrightness(img: BufferedImage): Double {
        var total = 0L
        var count = 0
        for (y in img.height / 2 until img.height) {
            for (x in 0 until img.width) {
                val c = img.getRGB(x, y)
                total += ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF))
                count++
            }
        }
        return total.toDouble() / count
    }

    private companion object {
        /** Enough moves that the gesture is a drag and not a teleport. */
        const val STEPS = 8
    }
}
