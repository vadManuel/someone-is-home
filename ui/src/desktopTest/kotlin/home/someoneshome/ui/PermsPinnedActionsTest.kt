package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **The PERMISSIONS screen's buttons stay on the glass in its tallest state** (D-305).
 *
 * One row refused, the rest owed and Local Network's caption showing put OPEN SETTINGS and REQUEST
 * WHAT IS MISSING below the edge at 720×1520 (D-303's `perms-tallest.png`), where nothing could
 * tap them. The owner's ruling is a scroll with the buttons pinned under it, and this holds the
 * pinning: each button's whole box inside the screen.
 *
 * Measured from each node's unclipped position and size rather than its bounds in the root, which
 * a scrolling parent clips: a button scrolled out of sight would read as a box of no height at the
 * edge and look inside.
 */
@OptIn(ExperimentalTestApi::class)
class PermsPinnedActionsTest {

    @Test
    fun theButtonsAreOnTheScreenInTheTallestState() = runDesktopComposeUiTest(width = 720, height = 1520) {
        // PermsWalkRender's tallest state, on its canvas: the mockup's camera band above.
        val os = DevicePermission.entries.associateWith { Grant.Unasked }.toMutableMap().apply {
            this[DevicePermission.LocalNetwork] = Grant.Asked
            this[DevicePermission.NearbyDevices] = Grant.Refused
        }
        val perms = PermsModel(read = { os.getValue(it) }).also { it.refresh() }
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                DeviceCanvas(insets = PanelInsets(top = 36.dp)) {
                    FlowHost(FlowModel(PanelState(screen = ScreenId.Perms), perms = perms))
                }
            }
        }
        waitForIdle()
        val screen = onRoot().fetchSemanticsNode().size.height

        // The state really is too tall: the caption ends below the scrolling part's bottom edge.
        // Without that this test would pass on a screen that never needed the scroll.
        val scroll = onNodeWithTag(PERMS_SCROLL).fetchSemanticsNode()
        val caption = onNodeWithText("iOS never reports", substring = true).fetchSemanticsNode()
        assertTrue(
            caption.bottom() > scroll.bottom(),
            "the tallest state fits without scrolling (caption ends at ${caption.bottom()}, the " +
                "scrolling part at ${scroll.bottom()}), so it no longer tests the pinning",
        )

        for (button in listOf("OPEN SETTINGS", "REQUEST WHAT IS MISSING")) {
            val node = onNodeWithText(button).node()
            assertTrue(
                node.positionInRoot.y >= 0f && node.bottom() <= screen,
                "$button runs off the screen in the tallest state: it spans " +
                    "${node.positionInRoot.y}..${node.bottom()} on a screen $screen tall",
            )
        }
    }

    private fun SemanticsNodeInteraction.node(): SemanticsNode = fetchSemanticsNode()

    /** The node's bottom edge in the root, unclipped. */
    private fun SemanticsNode.bottom(): Float = positionInRoot.y + size.height
}
