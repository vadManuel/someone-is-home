package home.someoneshome.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-105 and D-119, read off the screens: what survives a notification, and what does not.**
 *
 * The read concept is deleted, and the kinds differ in two things — whether they dim the house,
 * which `NotificationInputTest` measures in pixels, and what is still there afterwards, which is
 * here. Messages holds every text; the Egress widget holds the countdown; the lock screen holds
 * the quiet ones until they are swiped; **a house notice is shown once and held nowhere at all.**
 * [NotificationKind.heldBy] and [NotificationKind.stored] say so in fields so that they can be
 * checked, and this is where they are checked — against the whole game rather than against the two
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
     * a diff of the file that added the list. **The lock screen is now exactly such a list**, which
     * is the reason this test matters more after this unit than it did before it.
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
     * **Every stored notification really is standing under the clock, and nothing else is.**
     *
     * D-119's storage ruling has exactly one implementation — the lock screen list — so the field
     * and the screen have to be checked against each other or the field is a claim about nothing.
     * Both directions matter and they fail differently: a stored kind missing from the lock screen
     * is a fact about the world a player can never find again, and an unstored kind appearing
     * there is either an Egress that outlived its containment or a meeting notice the phone
     * remembers better than the room does.
     */
    @Test
    fun whatStandsUnderTheClockIsExactlyWhatTheDesignSaidIsStored() {
        for (notification in Notifications.all) {
            val shown = screensShowing(notification.body)
            if (notification.kind.stored) {
                assertTrue(
                    ScreenId.Lock in shown,
                    "${notification.kind} is stored and is not on the lock screen — it is on " +
                        "$shown, and a player who was not holding the phone never saw it at all",
                )
            } else {
                assertTrue(
                    ScreenId.Lock !in shown,
                    "${notification.kind} is not stored and is standing on the lock screen anyway",
                )
            }
        }
    }

    /**
     * **The two texts survive into Messages.**
     *
     * The converse of the notice test, and it is what stops that one from being satisfied by
     * deleting things. A rule that only ever says *fewer screens* is satisfied by an app with no
     * screens on it; this one fails if a text stops surviving into Messages, which would be a
     * message the player was shown once and can never look at again.
     */
    @Test
    fun everyKindThatPersistsIsOnTheScreenItNames() {
        for (notification in listOf(Notifications.opening, Notifications.text)) {
            assertTrue(
                notification.kind.heldBy!! in screensShowing(notification.body),
                "the ${notification.kind} text is not in Messages; the banner would have been " +
                    "the only time it was ever readable",
            )
        }
    }

    /**
     * **An unblocked Subroutine is on its banner and its lock screen row, and nowhere else.**
     *
     * The kind whose `heldBy` cannot be checked by looking for words: what survives is the *fact*,
     * as a row on the Subroutines list that no longer says WAITING UPSTREAM, and the sentence
     * itself is not written there. So the claim that can be checked is the other one — that the
     * sentence has not quietly become an entry in a log somewhere, which is the direction this
     * feature grows.
     */
    @Test
    fun anUnblockedSubroutineIsAnnouncedTwiceAndFiledNowhere() {
        assertEquals(
            setOf(ScreenId.Quiet, ScreenId.Lock, ScreenId.LockNotify),
            screensShowing(Notifications.unblocked.body),
            "the quiet banner, the lock screen and the lock screen with something arriving over " +
                "it are the only places this sentence exists",
        )
        assertEquals(
            ScreenId.Work, NotificationKind.Unblocked.heldBy,
            "what survives an unblocked Subroutine is the Subroutine, on the list of them",
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
     * **And they agree on the pair the HOUSE drew, not on the pair the port drew.**
     *
     * The nodes stopped being a constant the moment F-001 was ratified: two ordinary markers in
     * non-adjacent rooms, chosen at fire time, different every Egress. So the test above — which
     * holds both surfaces to `Notifications.EGRESS_NODES` — now proves only that two fixtures
     * agree with each other. This is the same property against a pushed pair, which is the one
     * that can actually be wrong in play.
     *
     * A build where the widget still read the constant passes the test above and fails this one,
     * and it fails it in the way that matters: two silent people sent to two different ends of a
     * dark house.
     */
    @Test
    fun theEgressSurfacesNameThePairTheHouseSent() {
        val sent = listOf("CELLAR", "BOX ROOM")
        for (id in listOf(ScreenId.Banner, NotificationKind.Egress.heldBy!!)) {
            for (node in sent) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(
                                PanelState(egressType = "TETHER", egressNodes = sent)
                                    .arrivingAt(id),
                            )
                        }
                    }
                    assertTrue(
                        onAllNodes(hasText(node, substring = true)).fetchSemanticsNodes().isNotEmpty(),
                        "$id did not name $node — it is still reading the port's drawn pair",
                    )
                    assertTrue(
                        onAllNodes(hasText(Notifications.EGRESS_NODES.first(), substring = true))
                            .fetchSemanticsNodes().isEmpty(),
                        "$id named the port's fixture room while the house had sent somewhere else",
                    )
                }
            }
        }
    }

    /**
     * **Beacon or Tether is the house's word too.**
     *
     * They are mechanically identical in v1, so the widget hard-coding one of them would look
     * right in every screenshot and be wrong in half of all Egresses — contradicting, on the one
     * surface that persists, the alert that woke the whole house up.
     */
    @Test
    fun theWidgetNamesTheTypeTheHouseDrew() {
        runDesktopComposeUiTest(width = 600, height = 1300) {
            setContent {
                DeviceCanvas(insets = PanelInsets()) {
                    Screen(
                        PanelState(egressType = "TETHER", egressNodes = listOf("A", "B"))
                            .arrivingAt(ScreenId.EgressWidget),
                    )
                }
            }
            assertTrue(
                onAllNodes(hasText("TETHER", substring = true)).fetchSemanticsNodes().isNotEmpty(),
                "the containment widget did not name the Egress the house actually started",
            )
            assertTrue(
                onAllNodes(hasText("BEACON", substring = true)).fetchSemanticsNodes().isEmpty(),
                "the containment widget is hard-coded to one kind of Egress",
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
     *
     * ### The NEW check was an exact match, and an unread badge walked straight past it
     *
     * `onAllNodesWithText("NEW")` finds a node whose text *is* NEW and nothing else, which was
     * written that way so MAP A NEW HOME would not trip it. The house thread's newest message was
     * stamped **`21:02 . NEW`** for as long as that screen has existed: a badge in the plainest
     * form there is, on the one message in the game whose words differ by role, and the guard
     * whose entire job was to find it could not see it because it shared a node with a timestamp.
     * It was found by writing the same tag onto a lock screen row and watching this test stay
     * green.
     *
     * So the word is matched **inside** the text now, and the one legitimate use is excused by
     * what follows it: a NEW *home* is a thing the host makes. Everything else — `NEW`, `3 NEW`,
     * `. NEW`, `NEW MESSAGES` — is a claim that this phone knows what its owner has looked at.
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
                    val tagged = onAllNodes(
                        SemanticsMatcher("a NEW tag") { node ->
                            node.config.getOrNull(SemanticsProperties.Text)
                                .orEmpty()
                                .any { NEW_TAG.containsMatchIn(it.text) }
                        },
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes()
                    assertTrue(
                        tagged.isEmpty(),
                        "$id/$role carries a NEW tag: " + tagged.joinToString(", ") { node ->
                            node.config.getOrNull(SemanticsProperties.Text).orEmpty()
                                .joinToString(" ") { it.text }
                        },
                    )
                }
            }
        }
    }

    private companion object {
        /** NEW as a word, anywhere in a node's text, except in front of the home a host makes. */
        val NEW_TAG = Regex("""\bNEW\b(?!\s+HOME)""")
    }

    /**
     * **There is nowhere to record that a notification was seen, and that is held by the types.**
     *
     * The sweep above catches a mark that reached a screen. This catches the field it would be
     * drawn from, which is a year earlier and is where the argument is actually lost: a `seen`
     * flag, a `readAt`, a `dismissedAt` — each one arrives for a reason that sounds administrative
     * and none of them is, because a phone that knows which player has looked at what is a phone
     * that can put a dot on a tile in a dark room where two people are comparing screens.
     *
     * Both surfaces are named in full rather than counted. A rule that says *no more than seven
     * fields* is satisfied by swapping one out, and the swap is exactly what a read flag looks
     * like when it arrives.
     *
     * Java reflection rather than `kotlin.reflect`: this module does not depend on `kotlin-reflect`
     * and a guard is not worth a dependency that ships.
     */
    @Test
    fun theTypesCannotRecordThatAnythingWasLookedAt() {
        assertEquals(
            listOf("kind", "body", "from", "at", "detail", "bodySize", "opens"),
            Notification::class.java.declaredFields.map { it.name }.filterNot { it.contains('$') },
            "a field arrived on Notification — if it can say when or whether, D-105 is gone",
        )
        assertEquals(
            listOf("dismiss", "getStanding"),
            NotificationsModel::class.java.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) }
                .map { it.name }
                .filterNot { it.contains('$') }
                .distinct()
                .sorted(),
            "the model can now answer a question other than what is standing and what to remove",
        )
    }
}
