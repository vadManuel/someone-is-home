package home.someoneshome.platform

/**
 * **The OS's own settings page for this app, opened from inside it** (D-173, and the owner's
 * 2026-08-24 ruling that the hosting refusal gain a control that opens it).
 *
 * One door and one verb, for [Speaker]'s reason. A home that will not go on the air is almost
 * always a Local Network grant the host has not given, and the fix is two taps away in a place the
 * app cannot reach and the host cannot be told to find in the dark — except that this is the one
 * moment in the evening when nobody is in the dark. **Host setup happens in a lit room with the
 * party standing in front of the host**, which is why a control is allowed here at all: nothing on
 * this seam may ever be reachable once a round is armed.
 *
 * ### It opens a page and cannot read one
 *
 * There is no `isGranted`, no callback and no result. The app does not learn whether the host
 * granted anything — it finds out the way it found out the first time, by advertising again and
 * seeing whether the home goes up. A seam that could report the permission would be a second
 * source of truth about a fact the radio already answers, and the two would disagree on the phone
 * where it mattered.
 */
interface SystemSettings {

    /**
     * Send the host to this app's page in the OS settings.
     *
     * **Nothing is promised about coming back.** The host leaves the app, changes a switch and
     * returns; the screen they left is the screen they return to, because the refusal lives on
     * [home.someoneshome.model] state rather than on anything this call disturbs.
     */
    fun open()
}

/** This device's route to its own settings page. */
expect fun deviceSystemSettings(): SystemSettings

/**
 * **A door that opens nothing and says so where only a developer reads** — [SilentSpeaker]'s shape.
 *
 * The honest form of *there is no OS here*: the call goes through, the control is exercised, and no
 * page opens. [opened] is what a test reads, because *the host asked for the settings page* is
 * checkable without an OS and is the whole of what this layer can be checked for.
 */
class NoSystemSettings(private val note: (String) -> Unit = {}) : SystemSettings {

    /** How many times the host was sent to the settings page. Test-facing; nothing in play reads it. */
    var opened: Int = 0
        private set

    override fun open() {
        opened++
        note("SETTINGS — PAGE REQUESTED, NO OS TO OPEN IT")
    }
}
