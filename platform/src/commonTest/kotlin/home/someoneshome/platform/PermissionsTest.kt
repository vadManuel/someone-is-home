package home.someoneshome.platform

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The gate D-161 rules, on this side of the seam.**
 *
 * The prompts themselves need a phone — a modal is the OS's own window and no target
 * `./gradlew check` runs has one. What needs no phone is the arithmetic the whole ruling turns on:
 * *which grants leave a phone unable to do the job*, and whether an unasked row counts as one.
 * That is where the bug lives, because the tempting default is the permissive one.
 */
class PermissionsTest {

    /**
     * **A phone nobody has asked anything is refused.** The single most important line here.
     *
     * `Grant.Unasked` is the state of every row on a cold launch, and the failure direction has to
     * be *the host is asked again* rather than *the party walks into the dark behind a phone that
     * cannot scan*. E-D2-1: **a refusal at setup is recoverable in ninety seconds; the same refusal
     * at arming costs the round.**
     */
    @Test
    fun aPhoneThatHasBeenAskedNothingCannotTakeASeat() {
        val perms = NoPermissions()
        assertEquals(
            DevicePermission.entries.toList(), perms.missing(),
            "a phone with no answers behind it was let through the gate",
        )
        assertFalse(Grant.Unasked.opensTheGate, "an unasked permission opens the gate")
        assertFalse(Grant.Refused.opensTheGate, "a refused permission opens the gate")
    }

    /** Everything granted, and the gate is open. Or the test above would prove nothing useful. */
    @Test
    fun aPhoneWithEveryGrantIsSeated() {
        val perms = NoPermissions()
        DevicePermission.entries.forEach { perms.says(it, Grant.Granted) }
        assertEquals(emptyList(), perms.missing(), "a fully granted phone was refused a seat")
    }

    /**
     * **One row short is short.** Each row, on its own, closes the gate, at every answer short of
     * GRANTED (D-309: every request is required to play).
     *
     * Swept rather than asserted for one permission, because the failure this stands against is a
     * gate that checks four of them: it would pass any single-row test written against the row
     * somebody remembered.
     */
    @Test
    fun everySinglePermissionCanCloseTheGateOnItsOwn() {
        for (short in DevicePermission.entries) {
            for (worst in listOf(Grant.Unasked, Grant.Asked, Grant.Refused)) {
                val perms = NoPermissions()
                DevicePermission.entries.forEach {
                    perms.says(it, if (it == short) worst else Grant.Granted)
                }
                assertEquals(
                    listOf(short), perms.missing(),
                    "a phone whose ${short.label} reads $worst was seated anyway — the gate is not " +
                        "reading the whole set, and D-161's whole argument is that it must",
                )
            }
        }
    }

    /**
     * **ASKED is not an answer, and a phone missing a sensor is not refused** (D-309).
     *
     * ASKED is a NEARBY DEVICES probe that heard nothing clear (D-303); seated on it, a phone could
     * not be measured all round (D-308). NOT ON THIS PHONE is hardware, not consent.
     */
    @Test
    fun askedClosesTheGateAndAMissingSensorDoesNot() {
        assertFalse(
            Grant.Asked.opensTheGate,
            "ASKED opens the gate — a NEARBY DEVICES probe with no answer seats a phone that cannot " +
                "be measured all round",
        )
        assertTrue(
            Grant.Unavailable.opensTheGate,
            "a phone missing a sensor is refused a seat — a gate that waits for hardware to grow",
        )
    }

    /** The prompt is really raised, and the answer really comes back through the callback. */
    @Test
    fun requestingRaisesThePromptAndReportsWhatCameBack() {
        val answers = mutableListOf<Grant>()
        val perms = NoPermissions(onRequest = { Grant.Granted })
        perms.request(DevicePermission.Camera) { answers += it }

        assertEquals(listOf(DevicePermission.Camera), perms.asked, "no prompt was raised at all")
        assertEquals(listOf(Grant.Granted), answers, "the answer never reached the caller")
        assertEquals(
            Grant.Granted, perms.grant(DevicePermission.Camera),
            "the row was not updated by its own answer, so the screen would still say TAP TO GRANT",
        )
    }

    /**
     * **Asking again for something already refused raises nothing and changes nothing.**
     *
     * iOS asks once, ever. A seam that reported a fresh GRANTED because a second call went through
     * would be the app telling a host their camera works on the way into a dark house.
     */
    @Test
    fun askingTwiceForARefusedPermissionDoesNotTurnItIntoAGrant() {
        val perms = NoPermissions(onRequest = { Grant.Granted })
        perms.says(DevicePermission.Motion, Grant.Refused)
        perms.request(DevicePermission.Motion) { }
        assertEquals(
            Grant.Refused, perms.grant(DevicePermission.Motion),
            "a second ask laundered a refusal into a grant",
        )
    }

    /**
     * **GUARD (D-310): a grant reading switched off is one bit a row**, and only a refusal sets it:
     * nothing refused reads zero, and a missing sensor is hardware, not a grant switched off.
     */
    @Test
    fun theGrantsSwitchedOffAreOneBitARow() {
        val perms = NoPermissions()
        DevicePermission.entries.forEach { perms.says(it, Grant.Granted) }
        assertEquals(0, perms.refusedGrants(), "a phone with every grant read a grant switched off")
        perms.says(DevicePermission.Camera, Grant.Refused)
        perms.says(DevicePermission.Notifications, Grant.Refused)
        assertEquals(
            (1 shl DevicePermission.Camera.ordinal) or (1 shl DevicePermission.Notifications.ordinal),
            perms.refusedGrants(),
        )
        perms.says(DevicePermission.Camera, Grant.Unavailable)
        assertEquals(1 shl DevicePermission.Notifications.ordinal, perms.refusedGrants(), "hardware missing read as a grant switched off")
    }

    /**
     * **GUARD (D-311): the Bluetooth radio turned off reads as the BLUETOOTH row refused**, for the
     * rows, the gate and the round alike, and back on reads granted again. A row not yet answered
     * stays owed as it was, and the other rows are untouched.
     */
    @Test
    fun theBluetoothRadioTurnedOffReadsAsTheRowRefused() {
        val perms = NoPermissions()
        DevicePermission.entries.forEach { perms.says(it, Grant.Granted) }
        perms.radioOff = true
        assertEquals(Grant.Refused, perms.standing(DevicePermission.Bluetooth), "the radio turned off did not read refused")
        assertEquals(Grant.Granted, perms.grant(DevicePermission.Bluetooth), "the grant itself was rewritten")
        assertEquals(listOf(DevicePermission.Bluetooth), perms.missing(), "the gate let a phone with its radio off through")
        assertEquals(1 shl DevicePermission.Bluetooth.ordinal, perms.refusedGrants(), "the round did not read the radio off")
        var answered: Grant? = null
        perms.requestStanding(DevicePermission.Bluetooth) { answered = it }
        assertEquals(Grant.Refused, answered, "a tap on the row while the radio is off answered granted")
        perms.radioOff = false
        assertTrue(perms.missing().isEmpty(), "the radio back on still closed the gate")
        assertEquals(0, perms.refusedGrants())
        val fresh = NoPermissions().also { it.radioOff = true }
        assertEquals(Grant.Unasked, fresh.standing(DevicePermission.Bluetooth), "an unasked row was read as refused")
        assertFalse(DevicePermission.entries.filter { it != DevicePermission.Bluetooth }.any { perms.standing(it) != Grant.Granted })
    }
}
