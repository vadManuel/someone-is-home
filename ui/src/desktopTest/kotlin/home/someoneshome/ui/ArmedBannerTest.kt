package home.someoneshome.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Haptic
import home.someoneshome.model.protocol.RoundMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The house's opening text lands over PERIMETER ARMED, on every phone, in the frame ARMED does**
 * (D-118, D-254).
 *
 * D-234 walked every seated phone to plain ARMED on the opening push, and ARMED is a screen the
 * house drives, so the opening banner, one of D-118's only two heavy notifications, was drawn at
 * arming on no phone at all. This holds it back on every phone, whatever its role (D-142's parity),
 * with the dim D-118 ties to it, and with D-119's way off it: a swipe or ten seconds, back to ARMED.
 */
@OptIn(ExperimentalTestApi::class)
class ArmedBannerTest {

    private val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")
    private val now = WallTime(hour = 23, minute = 46, weekday = 2, day = 17, month = 11)

    /** A seated phone with a house attached, standing on [at] when the lights go out. */
    private fun seated(at: ScreenId, insider: Boolean): FlowModel =
        FlowModel(PanelState(screen = at), wallClock = { now }).also { phone ->
            phone.houseAttached(true)
            // The arming burst, in the house's own order: the opening, the roster, the role.
            phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short))
            phone.houseSaid(RoundMessage.RosterIssued(names))
            phone.houseSaid(RoundMessage.RoleRevealed(insider = insider, line = if (insider) "a line" else ""))
        }

    private class Drawn(val texts: List<String>, val pixels: IntArray)

    /** Every text and every pixel [phone] draws, the way `FlowHost` draws it. */
    private fun drawn(phone: FlowModel): Drawn {
        var texts: List<String> = emptyList()
        var pixels = IntArray(0)
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        phone.state, phone.actions(), phone.editor, phone.homes, phone.lobby,
                        phone.meeting, phone.subroutines, phone.notifications, perms = phone.perms, lamp = phone.lamp,
                    )
                }
            }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            val img = onRoot().captureToImage().toAwtImage()
            pixels = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        }
        return Drawn(texts, pixels)
    }

    /**
     * **GUARD: every seated phone, in either role and from either seated screen, lands on ARMED with
     * the opening banner over it, dimmed, and draws the same frame as every other phone.**
     */
    @Test
    fun theOpeningBannerLandsOverArmedOnEveryPhone() {
        val phones = listOf(
            "Resident in the lobby" to seated(ScreenId.Lobby, insider = false),
            "Insider in the lobby" to seated(ScreenId.Lobby, insider = true),
            "Resident on the one-line screen" to seated(ScreenId.Secret, insider = false),
            "Insider on the one-line screen" to seated(ScreenId.Secret, insider = true),
        )
        val frames = phones.map { (who, phone) ->
            assertEquals(ScreenId.ArmedNotify, phone.state.screen, "the opening push did not land the banner over ARMED: $who")
            val vals = PanelVals(phone.state)
            assertEquals(NotificationKind.Opening, vals.arrival?.notification?.kind, "no opening banner on $who's phone")
            assertTrue(Notifications.dims(phone.state.screen), "the opening text did not dim $who's phone (D-118)")
            val frame = drawn(phone)
            assertTrue(Notifications.opening.body in frame.texts, "$who's phone drew no opening banner: ${frame.texts}")
            assertTrue(frame.texts.any { "PERIMETER" in it }, "$who's phone drew no PERIMETER ARMED under it: ${frame.texts}")
            who to frame
        }
        val (first, reference) = frames.first()
        for ((who, frame) in frames.drop(1)) {
            assertEquals(reference.texts, frame.texts, "$who's arming frame says something $first's does not")
            assertTrue(reference.pixels.contentEquals(frame.pixels), "$who's arming frame differs from $first's by a pixel (D-142)")
        }
    }

    /** **GUARD: the banner leaves ARMED standing**, by the swipe or by D-119's ten seconds. */
    @Test
    fun theBannerClearsBackToArmed() {
        val swiped = seated(ScreenId.Lobby, insider = false)
        swiped.dismissNotification()
        assertEquals(ScreenId.Armed, swiped.state.screen, "the swipe did not leave ARMED standing")
        assertTrue(!Notifications.dims(swiped.state.screen), "ARMED stayed dimmed after the swipe")

        val timed = Flow.autoAdvance.getValue(ScreenId.ArmedNotify)
        assertEquals(ScreenId.Armed, timed.to, "the banner's ten seconds do not go back to ARMED")
        assertEquals(HEAVY_HOLD, timed.afterMillis, "the banner over ARMED is not D-119's ten seconds")
        assertTrue(Flow.runsItsOwnTimer(swiped.state.arrivingAt(ScreenId.ArmedNotify)), "a housed phone never clears the banner on its own")
        assertTrue(ScreenId.ArmedNotify in Flow.armedOn, "a wait for ARMED would not see the banner over it")
    }
}
