package home.someoneshome.app

import home.someoneshome.platform.Foreground
import home.someoneshome.platform.Permissions
import home.someoneshome.ui.FlowModel

/**
 * **What lets a seated phone go from the lobby, on events and never on a clock** (D-311, D-326).
 *
 * D-311 read the OS once a second for the lobby's check, because the Bluetooth radio turned off in
 * Control Centre suspends nothing and composes nothing. The owner, 2026-11-22: drop the poll. Two
 * events reach a running app instead, and both are wired here:
 *
 * - **The Bluetooth radio's state changing** (Control Centre, airplane mode), as the permission
 *   seam's own manager hears it ([Permissions.watchTheRadio]): the lobby's check runs on it.
 * - **A return to the foreground**: every Settings change means leaving the app. The OS is read
 *   again, never probed ([Permissions.reread], D-310: a round's uploader reads it beat by beat), and
 *   then [FlowModel.cameBackToTheForeground] runs the lobby's check and, in the lobby alone, probes
 *   NEARBY DEVICES again.
 *
 * The rest of the triggers are the flow's own and need no wiring: NEW ROUND, a later round's push
 * and the host after a close run the check on arrival; a phone iOS ended for Camera or Motion is met
 * by the gate at its cold start; and in a round a refused ranging session reports itself.
 *
 * [flow] is asked at each event, so a root that builds the flow after this is wired is answered by
 * whatever flow exists then. Returns the way to stop both watches.
 */
fun watchTheLobby(permissions: Permissions, foreground: Foreground, flow: () -> FlowModel?): () -> Unit {
    val radio = permissions.watchTheRadio { flow()?.permissionsChecked() }
    val back = foreground.watch(
        onBackground = {},
        onForeground = { permissions.reread { flow()?.cameBackToTheForeground() } },
    )
    return {
        radio()
        back()
    }
}
