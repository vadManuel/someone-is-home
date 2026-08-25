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
     * ⚠️ **The owner's exact sentence, as a test** (2026-08-25).
     *
     * Two phones with Local Network switched on in Settings, the row reading that the prompt had
     * been raised, and: *I tap the button and it says this phone cannot take a seat yet, local
     * network.* The gate was reading a [Grant.Refused] the seam wrote when the browse that raises
     * the prompt failed — which it does while the modal is still on the glass, before anyone has
     * answered. **A row the OS will never confirm cannot be gated on a confirmation**, so anything
     * but [Grant.Unasked] seats the phone and the real test is the advertise.
     */
    @Test
    fun theRowIosWillNeverConfirmDoesNotStandBetweenAGrantedPhoneAndASeat() {
        for (unconfirmed in listOf(Grant.Asked, Grant.Refused)) {
            val (model, _) = seam(
                answers = DevicePermission.entries.associateWith { Grant.Granted } +
                    mapOf(DevicePermission.LocalNetwork to unconfirmed),
            )
            model.refresh()

            assertNull(
                model.gateRefusalNow(),
                "a phone that granted everything is refused a seat over LOCAL NETWORK reading " +
                    "$unconfirmed — the one row iOS has no call to confirm, so this gate never opens",
            )
            assertTrue(model.complete, "the screen and the gate disagree about the same phone")
        }
    }

    /**
     * **And the exemption is one row wide.** The half that stops the fix being *open the gate*.
     *
     * Every other row is answered for real by its framework, so a refusal there is a fact and still
     * closes the gate — and the unconfirmable row still closes it when nobody has raised the prompt
     * at all, because tapping it is an action the host can actually take.
     */
    @Test
    fun everyOtherRowStillClosesIt() {
        for (short in DevicePermission.entries) {
            val standing = if (short.confirmable) Grant.Refused else Grant.Unasked
            val (model, _) = seam(
                answers = DevicePermission.entries.associateWith { Grant.Granted } +
                    mapOf(short to standing),
            )
            model.refresh()

            val why = assertNotNull(
                model.gateRefusalNow(),
                "${short.label} reads $standing and the phone was seated anyway",
            )
            assertTrue(short.label in why, "the refusal does not name ${short.label}: '$why'")
            assertEquals(
                emptyList(),
                DevicePermission.entries.filter { it != short && it.label in why },
                "the refusal names rows this phone was granted: '$why'",
            )
        }
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
