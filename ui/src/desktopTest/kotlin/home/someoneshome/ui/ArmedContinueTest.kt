package home.someoneshome.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.Haptic
import home.someoneshome.model.protocol.RoundMessage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **PERIMETER ARMED's CONTINUE raises the house's opening banner, with its dim, on that phone, at
 * its own tap** (D-293, the owner's Q6 on D-234; D-118, D-119, D-102).
 *
 * D-254 landed the opening banner over ARMED in the frame ARMED did, one dim for the whole house at
 * one moment. The owner chose the tap instead, knowing the room sees separate dims. So the opening
 * push lands plain ARMED, undimmed, on every phone, and CONTINUE walks that phone to the opening text
 * over the springboard ([ScreenId.Notify]): dimmed, cleared by a swipe or D-119's ten seconds. The
 * tap is a walk on this phone and nothing else: every way this model has of reaching the house is
 * captured here and must stay empty.
 *
 * Every phone runs the same path whatever its role (D-142): the frames are compared to the pixel, an
 * Insider's against a Resident's, before the tap and after it.
 */
@OptIn(ExperimentalTestApi::class)
class ArmedContinueTest {

    private val names = listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")
    private val now = WallTime(hour = 23, minute = 46, weekday = 2, day = 17, month = 11)

    /** Everything a [FlowModel] can send outward, in the order it was sent. */
    private class Outbound {
        val said = mutableListOf<String>()
    }

    /** A seated phone with a house attached, standing on [at], every way out of it recorded. */
    private fun seated(at: ScreenId, insider: Boolean, out: Outbound = Outbound()): FlowModel =
        FlowModel(
            PanelState(screen = at),
            meeting = MeetingModel(send = { out.said += "meeting $it" }),
            subroutines = SubroutineModel(send = { out.said += "subroutine $it" }),
            send = { out.said += "intent $it" },
            fireEgress = { out.said += "egress" },
            onTorch = { out.said += "torch $it" },
            onLightsOut = { out.said += "lights out" },
            wallClock = { now },
        ).also { phone ->
            phone.houseAttached(true)
            armingBurst(phone, insider)
        }

    /** The arming burst, in the house's own order: the opening, the roster, the role. */
    private fun armingBurst(phone: FlowModel, insider: Boolean) {
        phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short))
        phone.houseSaid(RoundMessage.RosterIssued(names))
        phone.houseSaid(RoundMessage.RoleRevealed(insider = insider, line = if (insider) "a line" else ""))
    }

    private class Drawn(val texts: List<String>, val pixels: IntArray, val image: java.awt.image.BufferedImage)

    /**
     * What [phone] draws, the way `FlowHost` draws it; with [tap], CONTINUE is tapped on the drawn
     * screen first and what is captured is the frame after it.
     */
    private fun drawn(phone: FlowModel, tap: Boolean = false): Drawn {
        var texts: List<String> = emptyList()
        var image: java.awt.image.BufferedImage? = null
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        phone.state, phone.actions(), phone.editor, phone.homes, phone.lobby,
                        phone.meeting, phone.subroutines, phone.notifications, perms = phone.perms, lamp = phone.lamp,
                    )
                }
            }
            if (tap) {
                onNodeWithText("CONTINUE").performClick()
                waitForIdle()
            }
            texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            image = onRoot().captureToImage().toAwtImage()
        }
        val img = checkNotNull(image)
        return Drawn(texts, img.getRGB(0, 0, img.width, img.height, null, 0, img.width), img)
    }

    private val phones = listOf(
        Triple("Resident in the lobby", ScreenId.Lobby, false),
        Triple("Insider in the lobby", ScreenId.Lobby, true),
        Triple("Resident on the one-line screen", ScreenId.Secret, false),
        Triple("Insider on the one-line screen", ScreenId.Secret, true),
    )

    /**
     * **GUARD: the opening push lands plain ARMED on every seated phone, undimmed, with no banner,
     * and every phone draws the same frame.** The banner is no longer the push's to raise.
     */
    @Test
    fun theOpeningPushLandsPlainArmedOnEveryPhone() {
        val frames = phones.map { (who, at, insider) ->
            val phone = seated(at, insider)
            assertEquals(ScreenId.Armed, phone.state.screen, "the opening push did not land ARMED: $who")
            assertNull(PanelVals(phone.state).arrival, "a banner landed with ARMED on $who's phone, before any tap")
            assertFalse(Notifications.dims(phone.state.screen), "$who's phone dimmed at the push, before any tap")
            val frame = drawn(phone)
            assertTrue(frame.texts.any { "PERIMETER" in it }, "$who's phone drew no PERIMETER ARMED: ${frame.texts}")
            assertTrue("CONTINUE" in frame.texts, "$who's PERIMETER ARMED has no CONTINUE: ${frame.texts}")
            assertFalse(Notifications.opening.body in frame.texts, "$who's phone drew the opening text before its tap")
            who to frame
        }
        assertSameFrame(frames, "ARMED")
    }

    /**
     * **GUARD: CONTINUE, tapped on the drawn screen, raises the opening banner and its dim on this
     * phone, sends nothing, and draws the same frame on every phone whatever its role.**
     */
    @Test
    fun continueRaisesTheOpeningBannerAndItsDimOnThisPhoneAndSendsNothing() {
        val frames = phones.map { (who, at, insider) ->
            val out = Outbound()
            val phone = seated(at, insider, out)
            val frame = drawn(phone, tap = true)
            assertEquals(
                ScreenId.Notify, phone.state.screen,
                "CONTINUE did not raise the opening banner on $who's phone",
            )
            assertEquals(
                NotificationKind.Opening, PanelVals(phone.state).arrival?.notification?.kind,
                "the banner CONTINUE raised on $who's phone is not the house's opening text",
            )
            assertTrue(Notifications.dims(phone.state.screen), "the opening banner did not dim $who's phone (D-118)")
            assertTrue(Notifications.opening.body in frame.texts, "$who's phone drew no opening banner: ${frame.texts}")
            assertEquals(emptyList(), out.said, "$who's CONTINUE sent something outward")
            who to frame
        }
        assertSameFrame(frames, "after CONTINUE")
    }

    /**
     * **GUARD: the banner CONTINUE raised clears to the springboard**, by the swipe or by D-119's ten
     * seconds, on a housed phone too, and the light comes back up with it. Nothing walks back to
     * ARMED, so a phone raises the banner once.
     */
    @Test
    fun theBannerClearsToTheSpringboard() {
        val swiped = seated(ScreenId.Lobby, insider = false)
        swiped.go(ScreenGraph.exitsOf(ScreenId.Armed).single())
        swiped.dismissNotification()
        assertEquals(ScreenId.Home, swiped.state.screen, "the swipe did not leave the springboard")
        assertFalse(Notifications.dims(swiped.state.screen), "the springboard stayed dimmed after the swipe")

        val timed = Flow.autoAdvance.getValue(ScreenId.Notify)
        assertEquals(ScreenId.Home, timed.to, "the banner's ten seconds do not go to the springboard")
        assertEquals(HEAVY_HOLD, timed.afterMillis, "the opening banner is not D-119's ten seconds")
        assertTrue(Flow.runsItsOwnTimer(swiped.state.arrivingAt(ScreenId.Notify)), "a housed phone never clears the banner on its own")

        val reachesArmed = ScreenId.entries.filter { from ->
            ScreenId.Armed in ScreenGraph.exitsOf(from) + Flow.viaActions[from].orEmpty() + setOfNotNull(Flow.autoAdvance[from]?.to)
        }
        assertEquals(listOf(ScreenId.Lobby), reachesArmed, "something other than the lobby's LIGHTS OUT walks to ARMED")
    }

    /**
     * **A second opening moves nothing**, on ARMED or under the banner: a duplicate on the wire
     * neither raises the banner early nor raises it twice. And **a relaunched phone**, a fresh
     * process seated again on its lobby and replayed the round from its first push (D-268), stands
     * on ARMED again, undimmed, with CONTINUE to tap, as any phone does at arming.
     */
    @Test
    fun aSecondOpeningMovesNothingAndARelaunchedPhoneStandsOnArmedAgain() {
        for (insider in listOf(false, true)) {
            val phone = seated(ScreenId.Lobby, insider)
            phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short))
            assertEquals(ScreenId.Armed, phone.state.screen, "a second opening moved a phone on ARMED (insider=$insider)")
            assertFalse(Notifications.dims(phone.state.screen), "a second opening dimmed ARMED (insider=$insider)")

            phone.go(ScreenGraph.exitsOf(ScreenId.Armed).single())
            phone.houseSaid(RoundMessage.OpeningMessage(Haptic.Short))
            assertEquals(ScreenId.Notify, phone.state.screen, "a second opening moved a phone under its banner (insider=$insider)")

            val relaunched = seated(ScreenId.Lobby, insider)
            assertEquals(ScreenId.Armed, relaunched.state.screen, "the relaunched phone's replay did not stand it on ARMED")
            assertFalse(Notifications.dims(relaunched.state.screen), "the relaunched phone dimmed before its tap")
        }
    }

    /**
     * **NEW ROUND, and the second round's ARMED is the first's** (D-157): a phone that tapped through
     * the first round's banner is walked to the lobby, the second opening lands it on plain ARMED,
     * and its CONTINUE raises the banner and the dim again. Nothing from the first tap is carried.
     */
    @Test
    fun aSecondRoundsContinueRaisesTheBannerAgain() {
        for (insider in listOf(false, true)) {
            val phone = seated(ScreenId.Lobby, insider)
            phone.go(ScreenGraph.exitsOf(ScreenId.Armed).single())
            phone.dismissNotification()
            phone.push(ScreenId.WinResidents)
            phone.newRound()
            assertEquals(ScreenId.Lobby, phone.state.screen, "NEW ROUND did not walk the phone to the lobby")
            armingBurst(phone, insider)
            assertEquals(ScreenId.Armed, phone.state.screen, "the second round's opening did not land ARMED (insider=$insider)")
            assertFalse(Notifications.dims(phone.state.screen), "the second round's ARMED dimmed before the tap")
            phone.go(ScreenGraph.exitsOf(ScreenId.Armed).single())
            assertEquals(ScreenId.Notify, phone.state.screen, "the second round's CONTINUE raised no banner (insider=$insider)")
            assertTrue(Notifications.dims(phone.state.screen), "the second round's banner did not dim")
        }
    }

    /**
     * **A viewer, not an assertion**: PERIMETER ARMED with CONTINUE, and the frame just after the
     * tap, written to `build/screens/d293-*.png` to be looked at.
     */
    @Test
    fun renderArmedAndTheFrameAfterContinue() {
        val out = File("build/screens").apply { mkdirs() }
        ImageIO.write(drawn(seated(ScreenId.Lobby, insider = false)).image, "png", File(out, "d293-armed-continue.png"))
        ImageIO.write(drawn(seated(ScreenId.Lobby, insider = false), tap = true).image, "png", File(out, "d293-after-continue.png"))
    }

    private fun assertSameFrame(frames: List<Pair<String, Drawn>>, what: String) {
        val (first, reference) = frames.first()
        for ((who, frame) in frames.drop(1)) {
            assertEquals(reference.texts, frame.texts, "$who's $what frame says something $first's does not")
            assertTrue(reference.pixels.contentEquals(frame.pixels), "$who's $what frame differs from $first's by a pixel (D-142)")
        }
    }
}
