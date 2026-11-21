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
                DevicePermission.Camera,
                DevicePermission.Bluetooth,
            ),
            asked,
            "the walk did not stop at the refusal, so a host answering NO to one prompt is handed " +
                "the rest of them anyway",
        )
        assertEquals(listOf(DevicePermission.Bluetooth), model.missing.take(1), "the refused row is not first")
    }

    /** Every row granted, and the walk visits every row and leaves the gate open. */
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
     * **D-309: NEARBY DEVICES at ASKED keeps the phone at PERMISSIONS**, named in the gate's
     * sentence like any missing row.
     *
     * ASKED is a probe that neither ran nor was refused within two seconds (D-303). A phone seated
     * on it could not be measured all round (D-308), and the owner's ruling is that every request
     * is required to play.
     */
    @Test
    fun nearbyDevicesReadingAskedKeepsThePhoneAtPermissions() {
        val (model, _) = seam(
            answers = DevicePermission.entries.associateWith { Grant.Granted } +
                mapOf(DevicePermission.NearbyDevices to Grant.Asked),
        )
        model.refresh()

        val why = assertNotNull(
            model.gateRefusalNow(),
            "NEARBY DEVICES reads ASKED and the phone was seated anyway",
        )
        assertTrue(DevicePermission.NearbyDevices.label in why, "the refusal does not name NEARBY DEVICES: '$why'")
        assertEquals(
            listOf(DevicePermission.NearbyDevices), model.missing,
            "the screen and the gate disagree about a phone whose NEARBY DEVICES reads ASKED",
        )
        assertTrue(!model.complete, "NEARBY DEVICES reads ASKED and the screen offers CONTINUE")
    }

    /**
     * **A probe again that answers GRANTED lets the phone continue**, from a tap on the row and
     * from REQUEST WHAT IS MISSING, so a phone whose first probe timed out need not be reinstalled.
     */
    @Test
    fun aProbeAgainThatAnswersGrantedLetsThePhoneContinue() {
        for (viaTheButton in listOf(false, true)) {
            val standing = (
                DevicePermission.entries.associateWith { Grant.Granted } +
                    mapOf(DevicePermission.NearbyDevices to Grant.Asked)
                ).toMutableMap()
            val asked = mutableListOf<DevicePermission>()
            val model = PermsModel(
                read = { standing[it] ?: Grant.Unasked },
                // The iOS seam's rule: a standing ASKED on NEARBY DEVICES probes again.
                ask = { permission, then ->
                    asked += permission
                    standing[permission] = Grant.Granted
                    then(Grant.Granted)
                },
            )
            model.refresh()
            assertTrue(!model.complete, "the arrangement is wrong: ASKED already opened the gate")

            if (viaTheButton) model.requestMissing() else model.request(DevicePermission.NearbyDevices)

            assertEquals(listOf(DevicePermission.NearbyDevices), asked, "NEARBY DEVICES was not probed again")
            assertTrue(model.complete, "the probe answered GRANTED and the phone still cannot continue")
            assertNull(model.gateRefusalNow(), "the probe answered GRANTED and the lobby still refuses")
        }
    }

    /** **A probe again that still says ASKED stops the walk**, once; it does not probe forever. */
    @Test
    fun aProbeAgainThatStillSaysAskedStopsTheWalk() {
        val (model, asked) = seam(
            answers = DevicePermission.entries.associateWith { Grant.Granted } +
                mapOf(DevicePermission.NearbyDevices to Grant.Asked),
        )
        model.refresh()
        model.requestMissing()
        assertEquals(listOf(DevicePermission.NearbyDevices), asked, "the walk probed NEARBY DEVICES more than once")
        assertTrue(!model.complete, "a second ASKED opened the gate")
    }

    /**
     * **Every row closes the gate on anything but GRANTED** (D-309), Unasked, Asked and Refused
     * alike, and names only itself.
     */
    @Test
    fun everyRowClosesItOnAnythingButGranted() {
        for (short in DevicePermission.entries) {
            for (standing in listOf(Grant.Unasked, Grant.Asked, Grant.Refused)) {
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
    }

    /** **There is no LOCAL NETWORK row** (D-309): iOS raises that prompt itself, at JOIN A HOME. */
    @Test
    fun thereIsNoLocalNetworkRow() {
        assertEquals(
            listOf("CAMERA", "BLUETOOTH", "NEARBY DEVICES", "MOTION & FITNESS", "NOTIFICATIONS"),
            DevicePermission.entries.map { it.label },
            "the walk's rows changed",
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

    /**
     * **A fake OS whose Bluetooth answers late** (D-314), the iOS seam's shape. Asking for Bluetooth
     * builds a central manager whose state is not known yet, so the first answer is the standing TAP
     * TO GRANT; the real one (here NOT ON THIS PHONE, a simulator's) arrives a moment later from the
     * delegate. [late] holds that callback for the test to fire, and [arrive] puts the late answer
     * where a read of the OS sees it. NEARBY DEVICES answers [nearby] at once.
     */
    private class LateBluetooth(val nearby: Grant = Grant.Granted) {
        val asked = mutableListOf<DevicePermission>()
        val standing = (
            DevicePermission.entries.associateWith { Grant.Granted } +
                mapOf(DevicePermission.Bluetooth to Grant.Unasked, DevicePermission.NearbyDevices to Grant.Unasked)
            ).toMutableMap()
        var late: ((Grant) -> Unit)? = null
        val model = PermsModel(
            read = { standing[it] ?: Grant.Unasked },
            ask = { permission, then ->
                asked += permission
                when (permission) {
                    DevicePermission.Bluetooth -> {
                        late = then
                        then(Grant.Unasked)
                    }
                    DevicePermission.NearbyDevices -> {
                        standing[permission] = nearby
                        then(nearby)
                    }
                    else -> then(standing.getValue(permission))
                }
            },
        )

        fun arrive(grant: Grant) {
            standing[DevicePermission.Bluetooth] = grant
        }
    }

    /**
     * **A late answer moves the walk on, without a second press** (D-314). The overnight benches
     * stopped at BLUETOOTH: it answered TAP TO GRANT, the walk stopped, the delegate said NOT ON THIS
     * PHONE a moment later and nothing asked NEARBY DEVICES. On a phone the same happens when the
     * person taps Allow. Heard here through a read of the OS, as the screen hears it on a refresh.
     */
    @Test
    fun aLateAnswerHeardOnAReadMovesTheWalkOn() {
        val os = LateBluetooth()
        os.model.refresh()
        os.model.requestMissing()
        assertEquals(listOf(DevicePermission.Bluetooth), os.asked, "the arrangement is wrong")

        os.arrive(Grant.Unavailable)
        os.model.refresh()

        assertEquals(
            listOf(DevicePermission.Bluetooth, DevicePermission.NearbyDevices), os.asked,
            "Bluetooth answered late and the walk never went on to NEARBY DEVICES",
        )
        assertTrue(os.model.complete, "every row answered and the gate is still shut")
    }

    /**
     * **The same late answer through the prompt's own callback moves the walk on once** (D-314). The
     * answer can come more than once, so the second must not start a second walk, which would probe
     * NEARBY DEVICES again after an ASKED (D-309: one probe per press).
     */
    @Test
    fun aLateAnswerOnThePromptsCallbackMovesTheWalkOnOnce() {
        val os = LateBluetooth(nearby = Grant.Asked)
        os.model.refresh()
        os.model.requestMissing()
        val late = assertNotNull(os.late, "the arrangement is wrong: Bluetooth was not asked")

        os.arrive(Grant.Unavailable)
        late(Grant.Unavailable)
        late(Grant.Unavailable)

        assertEquals(
            listOf(DevicePermission.Bluetooth, DevicePermission.NearbyDevices), os.asked,
            "the late answer did not move the walk on exactly once",
        )
        assertEquals(listOf(DevicePermission.NearbyDevices), os.model.missing, "NEARBY DEVICES at ASKED opened the gate")
        assertTrue(!os.model.walking, "the walk still says it is waiting after NEARBY DEVICES answered ASKED")
    }

    /**
     * **A late answer after the walk has stopped does not restart it** (D-314). One press is one walk;
     * a row that answers after a refusal ended the walk is the row's, and the rest wait for a press.
     */
    @Test
    fun aLateAnswerAfterTheWalkStoppedDoesNotRestartIt() {
        val os = LateBluetooth()
        os.standing[DevicePermission.Camera] = Grant.Refused
        os.model.refresh()
        os.model.requestMissing()
        assertEquals(listOf(DevicePermission.Camera), os.asked, "the walk did not stop at the refused CAMERA")

        os.arrive(Grant.Unavailable)
        os.model.refresh()

        assertEquals(listOf(DevicePermission.Camera), os.asked, "a read after the walk stopped started it again")
    }
}
