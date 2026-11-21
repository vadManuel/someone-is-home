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
     * **Raise the prompt.** [then] carries what came back, and is called on the main thread.
     *
     * Asking for something already answered raises nothing — iOS asks once, ever — and reports the
     * standing answer instead. That is not a failure and must not be drawn as one: the row's fix
     * is the settings page, which the screen offers separately.
     */
    fun request(of: DevicePermission, then: (Grant) -> Unit)
}

/** This device's own permission prompts. */
expect fun devicePermissions(): Permissions

/**
 * **What is still standing between this phone and a seat**, off a live [Permissions].
 *
 * The gate D-161 rules, in one call, so that the lobby and the Perms screen cannot come to
 * different conclusions about the same phone. Empty means setup is complete.
 */
fun Permissions.missing(): List<DevicePermission> =
    grantsMissing(DevicePermission.entries.associateWith(::grant))

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
}
