package home.someoneshome.platform

import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.model.grantsMissing

/**
 * **The OS's own permission prompts, raised from the one screen that is allowed to raise them.**
 *
 * D-161: every grant is collected at setup, in the light, before anybody sits down — and the lobby
 * will not seat a phone that cannot do the job. This is the seam that makes that a real request
 * rather than a row of drawn text: [request] puts a system modal on the glass, and [grant] reports
 * what the OS says afterwards.
 *
 * ### Reading is synchronous and asking is not, and neither of those is a choice
 *
 * A grant is drawn on a row every frame, so [grant] answers now, out of what the seam last learned.
 * Two of the five (notifications, Bluetooth) only answer through a callback, so what [grant]
 * returns for those is the last answer that came back — refreshed by [refresh] and by every
 * [request]. **A row that has never been asked reads [Grant.Unasked], which is the truth**: nothing
 * has happened yet.
 *
 * ### It never reports a grant it did not hear
 *
 * The one permission iOS will not answer at all is Local Network, so it is not a row (D-309): iOS
 * raises that prompt itself when the phone first browses or advertises. NEARBY DEVICES answers only
 * through a probe, and a probe with no clear answer says so rather than guessing — see
 * [Grant.Asked]. The rule the whole interface is built on is `SystemSettings`': a
 * seam that invented an answer would be a second source of truth about a fact the radio already
 * settles, and the two would disagree on the phone where it mattered.
 */
interface Permissions {

    /** What the OS last said about one permission. Synchronous; safe to call while drawing. */
    fun grant(of: DevicePermission): Grant

    /**
     * Ask the OS again for everything it will answer **without prompting anybody**.
     *
     * Called when the Perms screen opens and when the app comes back from Settings: a host who
     * walked out to flip a switch has to see the row change, and nothing else would move it.
     */
    fun refresh(then: () -> Unit = {})

    /**
     * **[refresh] without the Nearby Interaction probe** (D-310): what the OS answers by a read
     * alone, for a phone back in the foreground in a round. D-309 keeps every probe off a round's
     * sessions, so a round re-reads Notifications and Bluetooth this way, and a NEARBY DEVICES grant
     * switched off is heard from the ranging radio instead. [refresh] itself by default, for a seam
     * that has no probe.
     */
    fun reread(then: () -> Unit = {}) = refresh(then)

    /**
     * **Raise the prompt.** [then] carries what came back, and is called on the main thread.
     *
     * Asking for something already answered raises nothing — iOS asks once, ever — and reports the
     * standing answer instead. That is not a failure and must not be drawn as one: the row's fix
     * is the settings page, which the screen offers separately.
     *
     * **[then] may be called a second time** (D-314): an answer that is still [Grant.Unasked]
     * because the OS does not know yet (Bluetooth's manager, before its delegate speaks) is
     * followed by the real one when it arrives.
     */
    fun request(of: DevicePermission, then: (Grant) -> Unit)

    /**
     * **Whether the Bluetooth radio itself is turned off, as the OS last said** (the owner's
     * 2026-11-21 rulings, D-311): Control Centre, or airplane mode, which turns Bluetooth off. Not a
     * grant (a powered-off radio is still one the app may use), so [grant] does not say it; but the
     * game needs the radio on exactly as it needs the grant, so [standing] reads the BLUETOOTH row as
     * refused while it is off. False by default, for a seam with no radio to ask.
     */
    fun bluetoothOff(): Boolean = false
}

/** This device's own permission prompts. */
expect fun devicePermissions(): Permissions

/**
 * **What one row reads for this game** (D-311): [Permissions.grant], except that a BLUETOOTH grant
 * given while the radio itself is turned off reads [Grant.Refused]. *The game requires all these to
 * function* (the owner, 2026-11-21): a phone with Bluetooth off cannot do the job, so it is shown
 * PERMISSIONS with the row refused, the lobby lets it go, and in a round it is a switch-off. A row
 * not yet answered stays as it reads: it is owed either way, and a tap still raises its prompt.
 *
 * The one reading the gate, the rows, [missing] and [refusedGrants] all take, so the PERMISSIONS
 * screen, the lobby and the round cannot disagree about the radio.
 */
fun Permissions.standing(of: DevicePermission): Grant {
    val grant = grant(of)
    return if (of == DevicePermission.Bluetooth && grant == Grant.Granted && bluetoothOff()) Grant.Refused else grant
}

/**
 * **[Permissions.request], answered in [standing]'s terms** (D-311): a BLUETOOTH prompt granted
 * while the radio is off comes back refused, as the row will read on its next refresh.
 */
fun Permissions.requestStanding(of: DevicePermission, then: (Grant) -> Unit) =
    request(of) { then(standing(of)) }

/**
 * **What is still standing between this phone and a seat**, off a live [Permissions].
 *
 * The gate D-161 rules, in one call, so that the lobby and the Perms screen cannot come to
 * different conclusions about the same phone. Empty means setup is complete. Read in [standing]'s
 * terms, so the Bluetooth radio turned off is missing too (D-311).
 */
fun Permissions.missing(): List<DevicePermission> =
    grantsMissing(DevicePermission.entries.associateWith { standing(it) })

/**
 * **The grants that read switched off now, as one number** (D-310): bit `ordinal` set for every row
 * that reads [Grant.Refused] in [standing]'s terms, so the Bluetooth radio turned off sets
 * BLUETOOTH's bit (D-311). Every row is required to play (D-309), so one refused in a round was
 * switched off after the phone was seated.
 *
 * Read once a beat by the round's uploader (`ContactUploader`), so a grant switched off mid-round
 * goes up as a permission. A number and not a list, so the read allocates nothing (rule 7). It stays
 * inside this phone: only the one fixed report body goes up.
 */
fun Permissions.refusedGrants(): Int {
    var refused = 0
    for (row in DevicePermission.entries) {
        if (standing(row) == Grant.Refused) refused = refused or (1 shl row.ordinal)
    }
    return refused
}

/**
 * **A phone with no OS behind it**, [NoSystemSettings]' shape: the call goes through, the row moves,
 * and no modal appears anywhere.
 *
 * The default answer is [Grant.Unasked] for every row, which makes a test that forgets to arrange
 * anything fail the gate rather than pass it. [asked] is what a test reads — *the host asked for
 * the camera* is checkable without an OS and is the whole of what this layer can be checked for.
 */
class NoPermissions(
    private val answers: MutableMap<DevicePermission, Grant> = mutableMapOf(),
    /** What a [request] turns an unanswered row into. A test that wants a refusal names one. */
    private val onRequest: (DevicePermission) -> Grant = { Grant.Granted },
    private val note: (String) -> Unit = {},
) : Permissions {

    /** Every permission requested, in the order it was asked for. Test-facing. */
    val asked: MutableList<DevicePermission> = mutableListOf()

    /** How many times the seam was re-read. Test-facing. */
    var refreshes: Int = 0
        private set

    override fun grant(of: DevicePermission): Grant = answers[of] ?: Grant.Unasked

    override fun refresh(then: () -> Unit) {
        refreshes++
        then()
    }

    override fun request(of: DevicePermission, then: (Grant) -> Unit) {
        asked += of
        val answer = answers[of]?.takeIf { it != Grant.Unasked } ?: onRequest(of)
        answers[of] = answer
        note("PERMS — ${of.label} ASKED, NO OS TO ASK · $answer")
        then(answer)
    }

    /** Arrange an answer without going through a prompt. Test-facing. */
    fun says(of: DevicePermission, grant: Grant) {
        answers[of] = grant
    }

    /** **The Bluetooth radio turned off, or back on** (D-311). Test-facing. */
    var radioOff: Boolean = false

    override fun bluetoothOff(): Boolean = radioOff
}
