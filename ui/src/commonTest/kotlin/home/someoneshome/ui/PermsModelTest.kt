package home.someoneshome.ui

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The Perms screen, asking for real.**
 *
 * Every row on that screen was a drawing: three said GRANTED, two said PENDING, none of them had
 * ever reached an OS. The owner's finding was that the app's page in Settings had no toggles under
 * it — there was nothing to toggle, because nothing had ever been requested — and that the one
 * button, `REQUEST MOTION ACCESS`, *just sends me to the main screen*.
 *
 * What is under test is the half a headless target can hold: **that a tap becomes a request**, that
 * the row draws what came back rather than what it was drawn with, and that nothing navigates.
 */
class PermsModelTest {

    /** A model over an arranged OS, plus the list of what it was asked for. */
    private fun seam(
        answers: Map<DevicePermission, Grant> = emptyMap(),
        gives: (DevicePermission) -> Grant = { Grant.Granted },
    ): Pair<PermsModel, MutableList<DevicePermission>> {
        val asked = mutableListOf<DevicePermission>()
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
        return model to asked
    }

    /** **A tap on a row raises that row's prompt**, and the row then says what came back. */
    @Test
    fun tappingARowAsksTheOsForThatOneThing() {
        val (model, asked) = seam()

        model.request(DevicePermission.Motion)

        assertEquals(
            listOf(DevicePermission.Motion), asked,
            "the row was tapped and no request left the app — which is the bug this screen shipped " +
                "with, on all five rows at once",
        )
        assertEquals(Grant.Granted, model.grant(DevicePermission.Motion), "the row did not move")
    }

    /**
     * **A row nobody has asked about says so**, rather than saying GRANTED.
     *
     * The screen's five rows were three GRANTEDs and two PENDINGs written into the source. A model
     * that opened on anything but *nothing has happened yet* would reproduce that exactly.
     */
    @Test
    fun aFreshModelClaimsNothingAboutAnyRow() {
        val (model, _) = seam()
        for (permission in DevicePermission.entries) {
            assertEquals(
                Grant.Unasked, model.grant(permission),
                "${permission.label} claims an answer nobody has been asked for",
            )
        }
        assertTrue(!model.complete, "a phone with no answers behind it says its setup is done")
    }

    /** The primary control walks the missing rows, one prompt at a time, and stops at a refusal. */
    @Test
    fun requestingWhatIsMissingWalksTheRowsAndStopsWhereItIsTurnedDown() {
        val (model, asked) = seam(gives = { if (it == DevicePermission.Bluetooth) Grant.Refused else Grant.Granted })

        model.requestMissing()

        assertEquals(
            listOf(
                DevicePermission.LocalNetwork,
                DevicePermission.Camera,
                DevicePermission.Bluetooth,
            ),
            asked,
            "the walk did not stop at the refusal, so a host answering NO to one prompt is handed " +
                "the rest of them anyway",
        )
        assertEquals(listOf(DevicePermission.Bluetooth), model.missing.take(1), "the refused row is not first")
    }

    /** Every row granted, and the walk visits all five and leaves the gate open. */
    @Test
    fun requestingWhatIsMissingOpensTheGateWhenEveryPromptIsAnswered() {
        val (model, asked) = seam()

        model.requestMissing()

        assertEquals(DevicePermission.entries.toList(), asked, "the walk skipped a row")
        assertTrue(model.complete, "every prompt was granted and the gate is still shut")
        assertNull(model.gateRefusal(), "a complete phone is still being told it cannot take a seat")
    }

    /**
     * **The refusal names the rows.** *Setup is incomplete* is a sentence nobody can act on; the
     * rows to go back and tap are what makes it recoverable in ninety seconds.
     */
    @Test
    fun theGateRefusalNamesEveryRowThatIsStillMissing() {
        val (model, _) = seam(
            answers = DevicePermission.entries.associateWith { Grant.Granted } +
                mapOf(DevicePermission.Camera to Grant.Refused),
        )
        model.refresh()

        val why = assertNotNull(model.gateRefusal(), "an incomplete phone was given no reason")
        assertTrue(DevicePermission.Camera.label in why, "the refusal does not name CAMERA: '$why'")
        assertTrue(
            DevicePermission.Motion.label !in why,
            "the refusal names a row that is already granted: '$why'",
        )
    }

    /**
     * **Coming back from Settings moves the rows**, which is the one thing a walk out of the app
     * has to be followed by. Nothing else would move them: iOS reports no change to a running app.
     */
    @Test
    fun refreshingReadsEveryRowOffTheOsAgain() {
        val standing = DevicePermission.entries.associateWith { Grant.Unasked }.toMutableMap()
        val model = PermsModel(read = { standing[it] ?: Grant.Unasked })
        model.refresh()
        assertTrue(!model.complete, "a phone with no answers behind it opened the gate")

        DevicePermission.entries.forEach { standing[it] = Grant.Granted }
        model.refresh()

        assertTrue(
            model.complete,
            "the host granted everything in Settings, came back, and the screen still says the " +
                "rows are missing",
        )
    }
}
