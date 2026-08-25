package home.someoneshome.app

import home.someoneshome.model.DevicePermission
import home.someoneshome.platform.CameraCardScanner
import home.someoneshome.platform.Permissions
import home.someoneshome.platform.ScanUse
import home.someoneshome.platform.deviceCardScanner
import home.someoneshome.platform.missing
import kotlinx.coroutines.delay

/**
 * **Host setup, driven by nobody, so a device run leaves evidence a screenshot cannot.**
 *
 * Two of this unit's three outputs have no picture. A permission prompt is the **OS's own window**
 * — no render harness draws it, and `devicectl` has no screenshot command anyway. A capture
 * session's *configuration* is invisible even to a person holding the phone: front and back look
 * identical in a log unless something says which, and the bug this unit closes is exactly that
 * confusion, on a screen that read BACK CAMERA over a front lens.
 *
 * So this walks the setup surface once and writes down what happened at each step. What it can
 * prove: **that a request was really made and what came back**, and **which lens each of the two
 * scans opens, with or without a preview layer, with or without a torch**. What it cannot prove —
 * and says so in its own last line — is that a card was read, because that needs a hand and a piece
 * of paper, and that half is the owner's.
 *
 * ### The camera goes first, deliberately
 *
 * iOS shows one system modal at a time and queues the rest behind it. A sweep that asked for five
 * permissions before opening a session would leave a stack of dialogs over a phone nobody is
 * holding, and the capture session's own lines would never be reached in the same run. So the two
 * sessions are opened first — the camera grant on a phone that has ever scanned is already given —
 * and the prompts come afterwards, where a queue of them costs nothing.
 *
 * ### It is a cheat and release does not compile it
 *
 * Behind [SWEEP_SWITCH], read at launch, absent on every launch a person makes. `armingCordon`'s
 * rule holds: nothing a round depends on arrives this way, and nothing here arms, seats or scans
 * anything a round would read.
 */
class SetupSweep(
    private val permissions: Permissions,
    private val note: (String) -> Unit = { println("[setup-sweep] $it") },
) {

    suspend fun run() {
        note("SWEEP — host setup, with nobody holding the phone")
        sessions()
        prompts()
        note(
            "SWEEP DONE — a configured session is not a scan. Only a card held to the lens is, " +
                "and only an eye on the glass can say the picture is really there.",
        )
    }

    /**
     * **The two scans, opened one after the other, each saying what it built.**
     *
     * The registration session is released before the in-round one opens: there is one back lens
     * and one front lens on this phone, and two sessions competing for hardware would produce a
     * log about contention rather than about configuration.
     */
    private suspend fun sessions() {
        note("--- REGISTRATION: back camera, preview, torch ---")
        val registration = deviceCardScanner(ScanUse.Registration)
        describe(registration.use)
        registration.start { payload -> note("registration read '$payload'") }
        delay(SETTLE_MILLIS)
        report("registration", registration)

        // The torch, both ways, with a pause long enough for a person in the room to see it. The
        // seam's own log line is the evidence; a lit LED is the owner's check.
        note("torch ON")
        registration.torch(true)
        delay(TORCH_MILLIS)
        note("torch OFF")
        registration.torch(false)
        registration.stop()
        delay(SETTLE_MILLIS)

        note("--- IN ROUND: front camera, no preview, no torch (D-159) ---")
        val inRound = deviceCardScanner(ScanUse.InRound)
        describe(inRound.use)
        inRound.start { payload -> note("in-round read '$payload'") }
        delay(SETTLE_MILLIS)
        report("in round", inRound)
        // **Asked for on purpose, and it must be refused by name.** A dark house is the one place a
        // light nobody authored is a signal nobody authored (rule 5), so the refusal is the claim.
        note("asking the in-round scan for its torch — this must be REFUSED")
        inRound.torch(true)
        inRound.stop()
    }

    /** What the configuration says, before any hardware is asked whether it agrees. */
    private fun describe(use: ScanUse) {
        note("configured as $use — facing=${use.facing}, preview=${use.preview}, torch=${use.torch}")
    }

    /**
     * What the session actually built, read off the camera rather than off the configuration.
     *
     * The two could disagree, and the whole point of the log is that a disagreement is visible: a
     * scanner that says `preview=true` and has no layer is a viewfinder nobody would see.
     */
    private fun report(which: String, scanner: home.someoneshome.platform.CardScanner) {
        val camera = scanner as? CameraCardScanner
        if (camera == null) {
            note("$which — this build has no camera behind the seam")
            return
        }
        note("$which — previewLayer attached: ${camera.previewLayer != null}, torchLit=${camera.torchLit}")
    }

    /**
     * **Every permission, asked for real**, with the state before and after each one.
     *
     * The before/after pair is what makes a line evidence rather than decoration. A row that was
     * already granted logs `Granted → Granted` and raises nothing, which is iOS asking once ever;
     * a row that was never asked logs `Unasked → …` and is a modal appearing on the glass. **The
     * screen that shipped could produce neither line, because it never asked anybody anything.**
     */
    private suspend fun prompts() {
        note("--- PERMISSIONS ---")
        note("before: " + DevicePermission.entries.joinToString(" · ") { "${it.label}=${permissions.grant(it)}" })
        for (permission in DevicePermission.entries) {
            val before = permissions.grant(permission)
            note("requesting ${permission.label} (standing at $before)")
            permissions.request(permission) { after ->
                note("${permission.label}: $before → $after")
            }
            // Between prompts, so a modal that is waiting for a thumb does not have the next one
            // stacked on top of it before this line is even written.
            delay(PROMPT_MILLIS)
        }
        note("after: " + DevicePermission.entries.joinToString(" · ") { "${it.label}=${permissions.grant(it)}" })
        val short = permissions.missing()
        note(
            if (short.isEmpty()) "GATE OPEN — this phone may host and may take a seat (D-161)"
            else "GATE SHUT — still missing: " + short.joinToString(" · ") { it.label },
        )
    }

    companion object {

        /** The environment variable `devicectl` sets to make the setup surface drive itself once. */
        const val SWEEP_SWITCH = "SOMEONES_HOME_SETUP_SWEEP"

        /** Long enough for a capture session to configure and start on a phone. */
        private const val SETTLE_MILLIS = 4_000L

        /** Long enough for an LED to be seen by somebody standing next to the phone. */
        private const val TORCH_MILLIS = 3_000L

        /** Long enough that two modals do not race each other onto the glass. */
        private const val PROMPT_MILLIS = 2_000L
    }
}
