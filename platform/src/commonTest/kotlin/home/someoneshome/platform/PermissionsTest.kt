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
     * **One row short is short.** Each of the five, on its own, closes the gate.
     *
     * Swept rather than asserted for one permission, because the failure this stands against is a
     * gate that checks four of them: it would pass any single-row test written against the row
     * somebody remembered.
     */
    @Test
    fun everySinglePermissionCanCloseTheGateOnItsOwn() {
        for (short in DevicePermission.entries) {
            val perms = NoPermissions()
            DevicePermission.entries.forEach {
                perms.says(it, if (it == short) Grant.Refused else Grant.Granted)
            }
            assertEquals(
                listOf(short), perms.missing(),
                "a phone missing ${short.label} was seated anyway — the gate is not reading the " +
                    "whole set, and D-161's whole argument is that it must",
            )
        }
    }

    /**
     * **Local Network stops at REQUESTED and that is enough to be seated.**
     *
     * iOS exposes no read-back for this grant, so the honest state after raising the prompt is
     * *asked* — see [Grant.Asked]. Blocking a seat on it would be a gate nobody can ever open on
     * the one permission the platform will not answer; the real answer arrives when the home goes
     * on the air, where the hosting refusal already names the cause and offers the settings page.
     */
    @Test
    fun aPermissionTheOsWillNotReportOpensTheGateOnceItHasBeenAsked() {
        assertTrue(
            Grant.Asked.opensTheGate,
            "REQUESTED closes the gate — which on Local Network is a gate that can never open, " +
                "because iOS has no call that reports that grant",
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
}
