package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The back arrow, read off the screens rather than reviewed against them** — `ScreenGraphTest`'s
 * method applied to the one control whose important property is that it is *missing*.
 *
 * The owner's finding was *no back navigation anywhere*, and the fix has two halves that fail in
 * opposite directions. Too little and a player is stranded on a screen they opened themselves —
 * which in a dark house means asking somebody out loud, in a game played in silence. Too much and a
 * phone can step backwards off a screen the house put it on, asserting a state the round has already
 * replaced. **Only one of those two is caught by using the app**, so both are asserted here.
 *
 * ### Every screen is rendered twice, and the two renders are two different questions
 *
 * With `canGoBack` **true** the phone has a trail behind it, which is the state a real one is in
 * almost always. That render answers *does the arrow appear where it should* and, far more
 * importantly, *does it stay away from the house's screens even when there is somewhere to go*. A
 * test that only rendered the empty-trail case would pass on a build where every screen in the
 * meeting had an arrow, because none of them declares a fallback.
 *
 * With `canGoBack` **false** — a freshly pushed screen, nothing behind it — the arrow may only
 * appear where the screen names a parent of its own. That render answers *is the control ever drawn
 * with nowhere to go*, which is the fault `PanelButton` and `RowButton` already refuse: a control
 * that answers a press by doing nothing is indistinguishable from one that is broken.
 */
class BackNavigationTest {

    /**
     * **No screen the house is driving draws a back arrow, with or without a trail.**
     *
     * Checked by rendering rather than by reading a table — a table is a claim about the screens and
     * this is the screens. Wiring a chevron into a meeting screen by hand would leave every set in
     * [Flow] saying the right thing and fail here.
     *
     * ### The set is the house's screens, and NOT [Flow.backable]'s complement
     *
     * That was the first shape of this test and it was wrong in a way worth recording. `backable` is
     * the set the **left-edge swipe** is enabled on and where a trail-driven back is offered
     * systemically, and it is deliberately narrow — the light-field screens, where a horizontal drag
     * cannot collide with a Subroutine's own gestures (D-139's sweep is a drag) and where the
     * owner's complaint was. Several in-game screens are neither backable nor house-driven and carry
     * a chevron of their own perfectly legitimately: MESSAGES opens a thread and the thread has a
     * way back. **What must never happen is an arrow on a screen the house put you on**, and that is
     * what this asserts.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theArrowIsAbsentOnEveryScreenTheHouseIsDriving() {
        val wrong = mutableListOf<String>()
        for (id in Flow.houseDriving + Flow.housePushed) {
            for (trailed in listOf(false, true)) {
                if (drawsTheArrow(id, trailed)) {
                    wrong += "$id draws a back arrow (trail=$trailed) and the house put you there"
                }
            }
        }
        assertEquals(
            emptyList(), wrong.toList(),
            "a screen the house is driving offers a way backwards:" +
                wrong.joinToString("") { "\n  $it" },
        )
    }

    /**
     * **Every browsing surface draws one once there is a trail** — the half the owner actually
     * reported.
     *
     * `Flow.backable` is derived rather than typed out, so a screen can join it without anybody
     * touching a screen file. This is what catches the case where it did and the arrow did not
     * follow: a light-field screen whose page name is a bare `Label` instead of a [PreHeading] is
     * in the set, looks right, and has no way out. That is exactly what the one-line screen was.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyBrowsingSurfaceDrawsOneOnceSomethingIsBehindIt() {
        val missing = Flow.backable.filterNot { drawsTheArrow(it, trailed = true) }
        assertEquals(
            emptyList(), missing,
            "a browsing surface with a trail behind it has no way back on it. If its page name is " +
                "a bare Label rather than a PreHeading, that is the bug — see BackChevron",
        )
    }

    /**
     * **With nothing behind it, an arrow appears only where the screen names its own parent.**
     *
     * The fallback is what keeps [ScreenGraph] a transcription (see [BackChevron]), so it has to be
     * a real declared edge and not a shrug. Which screens carry one is not asserted — that is the
     * graph's business and `ScreenGraphTest`'s — but *an arrow with no trail and no fallback* is a
     * control pointing nowhere, and there must be none.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anArrowWithNoTrailAlwaysHasADeclaredParentBehindIt() {
        for (id in Flow.backable) {
            if (!drawsTheArrow(id, trailed = false)) continue
            val asked = linkedSetOf<ScreenId>()
            render(id, trailed = false, nav = { asked += it }) {
                val arrows = onAllNodes(hasTestTag(BACK_CHEVRON))
                repeat(arrows.fetchSemanticsNodes().size) { i ->
                    arrows[i].performSemanticsAction(SemanticsActions.OnClick)
                }
            }
            assertTrue(
                asked.isNotEmpty(),
                "$id draws a back arrow with no trail behind it and no parent to fall back to — " +
                    "it is a control that answers a press by doing nothing",
            )
            assertTrue(
                asked.all { it in ScreenGraph.exitsOf(id) },
                "$id's back arrow falls back to $asked, which the screen graph does not claim: " +
                    "${ScreenGraph.exitsOf(id)}",
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun drawsTheArrow(id: ScreenId, trailed: Boolean): Boolean {
        var found = false
        // Both roles, because the springboard's parity means a control drawn for one and not the
        // other is the single thing this app must never have — and a back arrow that appeared only
        // on an Insider's screen would be visible from across a dark room.
        for (role in PanelRole.entries) {
            render(id, trailed, role = role) {
                if (onAllNodes(hasTestTag(BACK_CHEVRON)).fetchSemanticsNodes().isNotEmpty()) {
                    found = true
                }
            }
        }
        return found
    }

    @OptIn(ExperimentalTestApi::class)
    private fun render(
        id: ScreenId,
        trailed: Boolean,
        role: PanelRole = PanelRole.Resident,
        nav: (ScreenId) -> Unit = {},
        read: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) {
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        PanelState(role = role).arrivingAt(id),
                        PanelActions(nav = nav, canGoBack = { trailed }),
                    )
                }
            }
            read()
        }
    }
}
