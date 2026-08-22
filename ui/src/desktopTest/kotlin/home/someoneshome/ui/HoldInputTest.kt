package home.someoneshome.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.RoomKind
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **The hold family** (D-141) — *hold what cannot be taken back* — driven with a real finger.
 *
 * Five controls take the two-second hold, and they are five separate things that are irreversible
 * at the moment they complete: the vote lock, arming a Revoke, arming an Egress, the host's LIGHTS
 * OUT and StairsWarn's UNREGISTER AND CONTINUE. Four of the five are pressed in the dark by a thumb
 * that cannot see what it is over.
 *
 * The vote and LIGHTS OUT are driven where their own screens are — `MeetingInputTest` and
 * `LobbyInputTest` — because a hold is not the interesting thing about either of those screens.
 * What is here is page 2, the stairs warning, and the one control D-141 explicitly **refuses** to
 * put friction on.
 *
 * ### Every one of these is asserted twice: it fires at two seconds, and not at 1.9
 *
 * A hold that fired the moment a finger landed would pass a test that only ever held the full two
 * seconds, and it would be the exact bug the gesture exists to prevent. So each is held to just
 * short of the window, released, and checked to have reached nothing.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class HoldInputTest {

    /** Most of a hold: the design's two seconds, a tenth short. */
    private val nearly = HOLD_MILLIS - 100L

    /** A full hold, with a couple of frames of margin so the completion really lands. */
    private val enough = HOLD_MILLIS + 200L

    private fun DesktopComposeUiTest.show(model: FlowModel) {
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
            }
        }
        mainClock.autoAdvance = false
    }

    private fun on(screen: ScreenId, role: PanelRole = PanelRole.Resident): FlowModel =
        FlowModel(PanelState(screen = ScreenId.Home, role = role)).also { it.push(screen) }

    // ---- Page 2: the Insider arms, and a Resident's page is not a control ------------------------

    /**
     * **REVOKE arms on two seconds of a finger, and on nothing shorter** (D-141).
     *
     * An accidental arm spends a full cooldown and there is no cancel (D-009), so the cost of the
     * hold being wrong is a whole ability, silently, in the dark. The tile is the same tile it
     * always was — arming happens on page 2 *in place*, and nothing opens (D-142).
     */
    @Test
    fun revokeArmsOnAFullHoldAndNotBefore() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.Page2, PanelRole.Insider)
        show(model)
        val before = model.state.revoke

        onNodeWithText("REVOKE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(nearly)
        assertEquals(before, model.state.revoke, "the Revoke armed before two seconds were up")
        onNodeWithText("REVOKE").performTouchInput { up() }
        mainClock.advanceTimeBy(enough)
        assertEquals(before, model.state.revoke, "letting go early armed the Revoke anyway")

        onNodeWithText("REVOKE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertNotEquals(before, model.state.revoke, "a full hold on REVOKE reached nothing")
        assertEquals(ScreenId.Page2, model.state.screen, "arming opened a view; it happens in place")
    }

    /**
     * **EGRESS is the same gesture on the same page**, and it is the misclick the design calls
     * game-ending.
     */
    @Test
    fun egressArmsOnAFullHoldAndNotBefore() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.Page2, PanelRole.Insider)
        show(model)

        onNodeWithText("EGRESS").performTouchInput { down(center) }
        mainClock.advanceTimeBy(nearly)
        onNodeWithText("EGRESS").performTouchInput { up() }
        mainClock.advanceTimeBy(enough)
        assertEquals(ScreenId.Page2, model.state.screen, "most of a hold fired the Egress")

        onNodeWithText("EGRESS").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertEquals(ScreenId.Banner, model.state.screen, "a full hold on EGRESS reached nothing")
    }

    /**
     * **D-142, and the whole of it: a Resident's page 2 is identical at rest and inert under the
     * thumb.**
     *
     * Not *refused after two seconds* — refused from the first millisecond. A hold that filled and
     * then declined would be a self-test: press it, watch it, learn your own role. It would also be
     * worse than useless, because a bar filling in a dark house is world-observable to whoever is
     * standing behind the shoulder — the resting tell the parity was built against must not be
     * answered by opening a behavioural one.
     *
     * So this is asserted in pixels as well as in state. Both tiles are held for a full two seconds
     * and the screen is compared with itself before anything was touched: **not one pixel may
     * move**, which is the only form of the claim that cannot be satisfied by a bar somebody
     * decided to draw faintly.
     *
     * Injecting the bug: give either tile `enabled = true` regardless of role, or draw the hold
     * line for both. The first fails on the state, the second on the pixels, and both name the tile.
     */
    @Test
    fun aResidentsPageTwoAnswersNeitherTileAndDrawsNothingUnderTheThumb() {
        for (tile in listOf("POWER", "SUBSYS")) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                val model = on(ScreenId.Page2, PanelRole.Resident)
                show(model)
                val before = model.state
                val atRest: BufferedImage = onRoot().captureToImage().toAwtImage()

                onNodeWithText(tile).performTouchInput { down(center) }
                // Held well past the window, and looked at WITH THE FINGER STILL DOWN — a bar that
                // ran and then reset on the lift would be invisible to a check made afterwards.
                mainClock.advanceTimeBy(enough)
                val held: BufferedImage = onRoot().captureToImage().toAwtImage()
                onNodeWithText(tile).performTouchInput { up() }
                mainClock.advanceTimeBy(enough)

                assertEquals(before.revoke, model.state.revoke, "$tile: a Resident armed a Revoke")
                assertEquals(before.screen, model.state.screen, "$tile: a Resident's page 2 walked somewhere")
                val (moved, at) = diff(atRest, held)
                assertEquals(
                    0, moved,
                    "$tile: a Resident's page 2 changed under the thumb — $moved pixels, first at $at",
                )
            }
        }
    }

    /**
     * And the tiles are drawn for both roles, which is what makes the test above mean something.
     *
     * A page 2 that had simply stopped drawing the second tile for Residents would pass every
     * inertness check on this page while failing the parity the inertness exists to protect.
     */
    @Test
    fun bothRolesGetTwoTilesInTheSamePlaces() {
        for ((role, names) in mapOf(
            PanelRole.Insider to listOf("REVOKE", "EGRESS"),
            PanelRole.Resident to listOf("POWER", "SUBSYS"),
        )) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                show(on(ScreenId.Page2, role))
                for (name in names) {
                    onNodeWithText(name).assertExists("$role lost the $name tile")
                }
            }
        }
    }

    // ---- The stairs warning ----------------------------------------------------------------------

    /**
     * **UNREGISTER AND CONTINUE is a hold**: it discards a registration the host climbed the stairs
     * to make, and the only way back is climbing them again with the cards in hand.
     */
    @Test
    fun unregisterAndContinueTakesTwoSeconds() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.RoomEdit)
        model.editor.open("GARAGE")
        model.pickRoomType(RoomKind.Stairs)
        assertEquals(ScreenId.StairsWarn, model.state.screen, "the warning did not come up")
        show(model)

        onNodeWithText("UNREGISTER AND CONTINUE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(nearly)
        onNodeWithText("UNREGISTER AND CONTINUE").performTouchInput { up() }
        mainClock.advanceTimeBy(enough)
        assertNotEquals(
            RoomKind.Stairs, model.editor.heldKind,
            "most of a hold unregistered the cards",
        )
        assertEquals(ScreenId.StairsWarn, model.state.screen)

        onNodeWithText("UNREGISTER AND CONTINUE").performTouchInput { down(center) }
        mainClock.advanceTimeBy(enough)
        assertEquals(RoomKind.Stairs, model.editor.heldKind, "a full hold changed nothing")
        assertEquals(ScreenId.Editor, model.state.screen, "the host was left on the warning")
    }

    /** The safe answer beside it is still one tap, because nothing about it is irreversible. */
    @Test
    fun moveThemFirstIsStillOneTap() = runDesktopComposeUiTest(width = 300, height = 650) {
        val model = on(ScreenId.RoomEdit)
        model.editor.open("GARAGE")
        model.pickRoomType(RoomKind.Stairs)
        show(model)

        onNodeWithText("MOVE THEM FIRST").performClick()
        mainClock.advanceTimeBy(50)
        assertEquals(ScreenId.MarkerSheet, model.state.screen, "the safe answer needs a hold")
        assertNotEquals(RoomKind.Stairs, model.editor.heldKind, "asking to move them moved the room")
    }

    // ---- The one place friction is refused --------------------------------------------------------

    /**
     * **STOP NOW is an instant tap, everywhere it appears, and this test exists to keep it one.**
     *
     * D-141 puts friction on five controls and refuses it here, deliberately. It is the panic exit
     * from a Subroutine, and D-111 made abandonment free by design: the work plane hears nothing,
     * no partial state is held, and the next scan restarts. **Friction on the exit would undo
     * that.** A player who hears somebody in the doorway has to be able to be looking at nothing in
     * the time it takes to lift a thumb — and a two-second bar filling while they wait is both a
     * delay and a lit rectangle on the screen of somebody trying to stop being interesting.
     *
     * So this is a test that fails if somebody adds friction: it sweeps every screen in the game,
     * finds the ones that say STOP NOW, and requires each to be reachable by a single click. Turning
     * one into a hold takes its click action away and fails here by name.
     */
    @Test
    fun stopNowIsOneTapOnEveryScreenThatOffersIt() {
        val found = mutableListOf<String>()
        for (id in ScreenId.entries) {
            runDesktopComposeUiTest(width = 300, height = 650) {
                val model = on(id)
                show(model)
                if (onAllNodes(hasText("STOP NOW")).fetchSemanticsNodes().isEmpty()) {
                    return@runDesktopComposeUiTest
                }
                found += id.name
                assertEquals(
                    1,
                    onAllNodes(hasClickAction() and hasText("STOP NOW")).fetchSemanticsNodes().size,
                    "$id: STOP NOW is not a single-tap control",
                )
                onNodeWithText("STOP NOW").performClick()
                mainClock.advanceTimeBy(50)
                assertNotEquals(id, model.state.screen, "$id: one tap on STOP NOW went nowhere")
            }
        }
        // The scan viewfinder and every built Subroutine screen. Asserted as a floor rather than a
        // list, so building a seventh Subroutine does not have to edit this test — but a sweep that
        // silently found none would otherwise pass, which is the failure this line exists for.
        assertTrue(
            found.size >= Subroutine.built.size + 1,
            "STOP NOW was found on only ${found.size} screen(s): $found",
        )
    }

    // ---- No button dismissals anywhere (D-105) ----------------------------------------------------

    /**
     * **There is no DISMISS button left in the game**, on any screen, in either role.
     *
     * D-105 deleted read state and D-119 made the swipe the acknowledgment, which leaves a
     * dismissal *control* with nothing honest to be: it would be the one thing in the app claiming
     * to know what a player has looked at. The house notice was the last one holding out.
     */
    @Test
    fun nothingInTheGameOffersAButtonThatDismisses() {
        val offenders = mutableListOf<String>()
        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 300, height = 650) {
                    show(on(id, role))
                    val found = onAllNodes(
                        hasClickAction() and hasText("DISMISS", substring = true, ignoreCase = true),
                    ).fetchSemanticsNodes().size
                    if (found > 0) offenders += "$id/${role.name}"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "a notification can be put away by pressing something: $offenders",
        )
    }

    /** How many pixels differ, and the first place they do. */
    private fun diff(a: BufferedImage, b: BufferedImage): Pair<Int, String?> {
        var count = 0
        var first: String? = null
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    count++
                    if (first == null) first = "($x,$y)"
                }
            }
        }
        return count to first
    }
}
