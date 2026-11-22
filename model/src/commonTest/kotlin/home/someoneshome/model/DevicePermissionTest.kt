package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The rows PERMISSIONS asks for, pinned** (D-309, D-326).
 *
 * The owner, 2026-11-22: *"Remove the notification permission. We don't use it."* The app posts no
 * iOS notification and has no background mode; every notification in the game is drawn in-app. So
 * the walk, the gate, the skip, the refresh and the switch-off bits all run over these four rows and
 * no other. The prompt's half of the guard is `notificationCordon` (buildSrc), which fails the build
 * if the UserNotifications framework is named again in anything that compiles into a phone.
 */
class DevicePermissionTest {

    /** **GUARD (D-326): the four rows, in the order they are drawn, and no NOTIFICATIONS row.** */
    @Test
    fun theWalkIsCameraBluetoothNearbyDevicesAndMotion() {
        assertEquals(
            listOf("CAMERA", "BLUETOOTH", "NEARBY DEVICES", "MOTION & FITNESS"),
            DevicePermission.entries.map { it.label },
            "the rows PERMISSIONS asks for changed (D-326 removed NOTIFICATIONS)",
        )
        assertTrue(
            DevicePermission.entries.none { "NOTIF" in it.label || "NOTIF" in it.name.uppercase() },
            "a notification row is back: the app posts no iOS notification (D-326)",
        )
    }

    /** With every row granted nothing is missing; one row short names that row alone. */
    @Test
    fun theGateRunsOverTheFourRows() {
        val granted = DevicePermission.entries.associateWith { Grant.Granted }
        assertEquals(emptyList(), grantsMissing(granted), "four granted rows still left something missing")
        for (row in DevicePermission.entries) {
            assertEquals(listOf(row), grantsMissing(granted + (row to Grant.Refused)), "$row refused")
        }
    }
}
