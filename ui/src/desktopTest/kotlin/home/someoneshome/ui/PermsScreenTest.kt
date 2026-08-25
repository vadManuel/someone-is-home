package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The Perms screen, rendered, with a hand on it** — the half no headless model test can hold.
 *
 * Compiling proves nothing about a screen. What is asserted here is what a host looking at the
 * glass would see and touch: that every row is on it with the OS's own answer beside it, that the
 * button under it does not say the thing that shipped, and that pressing it asks for something.
 *
 * The screen that shipped had all five rows drawn from string literals and a button reading
 * `REQUEST MOTION ACCESS` which requested nothing and navigated to the join screen regardless —
 * *request motion access just sends me to the main screen.* Both halves are asserted defunct.
 */
@OptIn(ExperimentalTestApi::class)
class PermsScreenTest {

    /** Render the Perms screen over an arranged OS, and report what it was asked for. */
    private fun perms(
        answers: Map<DevicePermission, Grant>,
        gives: (DevicePermission) -> Grant = { Grant.Granted },
        block: androidx.compose.ui.test.SemanticsNodeInteractionsProvider.(MutableList<DevicePermission>, MutableList<ScreenId>) -> Unit,
    ) = runDesktopComposeUiTest(width = 300, height = 650) {
        val asked = mutableListOf<DevicePermission>()
        val went = mutableListOf<ScreenId>()
        val standing = answers.toMutableMap()
        val model = PermsModel(
            read = { standing[it] ?: Grant.Unasked },
            ask = { permission, then ->
                asked += permission
                val answer = standing[permission]?.takeIf { it != Grant.Unasked } ?: gives(permission)
                standing[permission] = answer
                then(answer)
            },
        )
        setContent {
            DeviceCanvas(insets = PanelInsets()) {
                Screen(
                    PanelState(screen = ScreenId.Perms),
                    actions = PanelActions(
                        nav = { went += it },
                        requestGrant = model::request,
                        requestMissingGrants = model::requestMissing,
                    ),
                    perms = model,
                )
            }
        }
        block(asked, went)
    }

    /**
     * **Every permission is a row, and each says what the OS said.**
     *
     * Swept over the whole enum rather than written out, so a sixth permission added to the game
     * arrives on this screen or fails here — the alternative is a grant the app needs, asks for
     * nowhere, and first prompts for in a dark corridor, which is precisely D-161's argument.
     */
    @Test
    fun everyPermissionIsARowShowingWhatTheOsSaid() = perms(
        answers = DevicePermission.entries.associateWith { Grant.Granted } +
            mapOf(DevicePermission.Motion to Grant.Refused),
    ) { _, _ ->
        for (permission in DevicePermission.entries) {
            onAllNodesWithText(permission.label).assertCountEquals(1)
        }
        onAllNodesWithText(Grant.Granted.label).assertCountEquals(DevicePermission.entries.size - 1)
        onAllNodesWithText(Grant.Refused.label).assertCountEquals(1)
        // The refused row's fix is outside the app, and only then is the way out drawn.
        onAllNodesWithText("OPEN SETTINGS").assertCountEquals(1)
    }

    /**
     * **The button that lied is gone, and the one in its place asks.**
     *
     * Two assertions in one test because they are one claim: the control on an incomplete phone
     * requests and does not navigate. Either half alone is satisfiable by the bug — a button that
     * asks *and* walks forward is still a phone in a lobby it cannot serve.
     */
    @Test
    fun theControlOnAnIncompletePhoneAsksAndGoesNowhere() = perms(
        answers = emptyMap(),
        gives = { Grant.Refused },
    ) { asked, went ->
        onAllNodesWithText("REQUEST MOTION ACCESS").assertCountEquals(0)
        onAllNodesWithText("CONTINUE").assertCountEquals(0)
        onAllNodesWithText("REQUEST WHAT IS MISSING").assertCountEquals(1)

        onNodeWithText("REQUEST WHAT IS MISSING").performClick()

        assertEquals(
            listOf(DevicePermission.LocalNetwork), asked,
            "the primary control raised no prompt at all, which is what shipped",
        )
        assertEquals(
            emptyList(), went,
            "the control walked the host forward off an incomplete phone — the fall-through the " +
                "owner reported as 'request motion access just sends me to the main screen'",
        )
    }

    /**
     * **A row is a control**, and tapping one asks for that one thing.
     *
     * The row tapped here is the last one, so a screen that wired every row to the same permission
     * would report the first and fail.
     */
    @Test
    fun tappingARowAsksForThatRow() = perms(answers = emptyMap()) { asked, went ->
        onNodeWithText(DevicePermission.Notifications.label).performClick()

        assertEquals(
            listOf(DevicePermission.Notifications), asked,
            "a row on the permissions screen is still a drawing rather than a control",
        )
        assertTrue(went.isEmpty(), "tapping a permission row moved the phone somewhere")
    }

    /** A phone that can do the job is offered the way forward, and only then. */
    @Test
    fun aCompletePhoneIsOfferedTheWayForward() = perms(
        answers = DevicePermission.entries.associateWith { Grant.Granted },
    ) { _, went ->
        onAllNodesWithText("REQUEST WHAT IS MISSING").assertCountEquals(0)
        onAllNodesWithText("OPEN SETTINGS").assertCountEquals(0)
        onNodeWithText("CONTINUE").performClick()

        assertEquals(listOf(ScreenId.Join), went, "a complete phone could not get off the screen")
    }
}
