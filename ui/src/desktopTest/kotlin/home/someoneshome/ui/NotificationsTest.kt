package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-105, read off the screens: what survives a notification, and what does not.**
 *
 * The read concept is deleted, and the three kinds differ only in what is still there afterwards.
 * Messages holds every text; the Egress widget holds the countdown; **a house notice is shown once
 * and held nowhere at all.** [NotificationKind.heldBy] says so in a field so that it can be
 * checked, and this is where it is checked — against the whole game rather than against the two
 * or three screens somebody remembered to look at.
 *
 * "Stored nowhere" cannot be proved by looking at the model that does not store it. It has to be
 * proved by looking *everywhere else*: every screen in the game, in both roles, with the notice's
 * own words. A notice that leaked into the inbox, into Notes, into a thread, or onto a springboard
 * tile would be found here and named — and it would be found the same way whether it got there
 * through a list somebody added or through a string somebody pasted.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationsTest {

    /**
     * Every screen in the game, both roles, and the screens on which [words] can be read.
     *
     * Substring rather than exact, because a preview line is the beginning of a message and a
     * banner is the whole of one. The point is the words being *readable* somewhere, not a node
     * whose text is equal to them.
     */
    private fun screensShowing(words: String): Set<ScreenId> {
        val found = linkedSetOf<ScreenId>()
        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(PanelState(role = role).arrivingAt(id))
                        }
                    }
                    if (onAllNodes(hasText(words, substring = true)).fetchSemanticsNodes().isNotEmpty()) {
                        found += id
                    }
                }
            }
        }
        return found
    }

    /**
     * **The house notice is on exactly one screen, and there is no second place it survives.**
     *
     * The failure this exists for is not a crash and not a leak — it is a phone that remembers the
     * evening better than the people arguing about it. A notice is read aloud into a room where
     * everyone got the same one at the same moment; a copy still sitting in a thread an hour later
     * turns a shared moment into a document one player can quote and another cannot.
     *
     * It is also the shape a "notifications" feature grows by default. Any inbox, any history, any
     * list-of-what-arrived catches this text on its way past, and nothing about that is visible in
     * a diff of the file that added the list.
     */
    @Test
    fun theHouseNoticeIsHeldNowhereButTheMeetingItArrivesAt() {
        val body = Notifications.notice.body
        assertEquals(
            setOf(ScreenId.Notice), screensShowing(body),
            "a house notice is shown once and stored nowhere — these screens still have it",
        )
        assertEquals(
            null, NotificationKind.Notice.heldBy,
            "the notice named somewhere it persists; then the words above must be there too",
        )
    }

    /**
     * **The two that do persist are on the screen their kind names.**
     *
     * The converse of the test above, and it is what stops that one from being satisfied by
     * deleting things. A rule that only ever says *fewer screens* is satisfied by an app with no
     * screens on it; this one fails if the text banner stops surviving into Messages, which would
     * be a message the player was shown once and can never look at again.
     */
    @Test
    fun everyKindThatPersistsIsOnTheScreenItNames() {
        val text = Notifications.text
        assertTrue(
            NotificationKind.Text.heldBy!! in screensShowing(text.body),
            "the house's text is not in Messages; a banner is the only time it was ever readable",
        )
    }

    /**
     * **The banner and the widget name the same two rooms.**
     *
     * Containment needs two people at two separate markers and nobody may speak, so the pair of
     * room names *is* the coordination. The alert says them once and then goes; the widget it
     * leaves behind is where anyone who swiped it away goes to read them again. Two copies of that
     * pair drifting apart would send two silent people to different ends of a dark house, and the
     * mistake would look like a typo.
     */
    @Test
    fun theEgressAlertAndTheWidgetSendPeopleToTheSamePlaces() {
        for (node in Notifications.EGRESS_NODES) {
            assertTrue(
                node in Notifications.egress.detail.orEmpty(),
                "the Egress alert does not name $node",
            )
            val shown = screensShowing(node)
            assertTrue(
                ScreenId.Banner in shown && NotificationKind.Egress.heldBy!! in shown,
                "$node is named on $shown — the alert and the widget it survives into must agree",
            )
        }
    }

    /**
     * **Nothing anywhere counts what has and has not been looked at (D-105).**
     *
     * No badges, no NEW tags, no marks. The concept is deleted rather than merely unused, and the
     * difference matters: an unused concept is one product decision away from being a dot on a
     * tile, and a dot on a tile in this game is a difference between two phones held in the same
     * dark room.
     *
     * The one count the design does keep is the backlog header — *N MESSAGES* — which counts what
     * is there rather than what has not been attended to, and reads identically for both roles on
     * the day it arrives and an hour later.
     */
    @Test
    fun noSurfaceCountsWhatHasBeenLookedAt() {
        val marks = listOf("UNREAD", "UNOPENED", "UNSEEN")
        for (id in ScreenId.entries) {
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(PanelState(role = role).arrivingAt(id))
                        }
                    }
                    for (mark in marks) {
                        onAllNodesWithText(mark, substring = true, ignoreCase = true)
                            .assertCountEquals(0)
                    }
                    // Exact, not substring: MAP A NEW HOME is a button and not a tag.
                    onAllNodesWithText("NEW").assertCountEquals(0)
                }
            }
        }
    }
}
