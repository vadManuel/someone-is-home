package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant
import home.someoneshome.model.grantsMissing

/**
 * **What the OS has actually said about this phone**, and the one screen that may ask it.
 *
 * ### It asks for real, and until this existed it asked for nothing at all
 *
 * The Perms screen drew five rows and a button. Three rows said GRANTED, two said PENDING, and
 * **not one of them was a fact**: nothing on that screen ever reached an OS, so Settings showed the
 * app with no toggles under it — there was nothing to toggle, because nothing had ever been
 * requested. The button, `REQUEST MOTION ACCESS`, navigated to the join screen and requested
 * nothing; the owner's words for it were *request motion access just sends me to the main screen*.
 * The camera prompt then appeared where D-161 says it must never appear: mid-scan.
 *
 * Every row here is a real request now, and every value drawn is what came back.
 *
 * ### `ui` cannot see `platform`, so the OS arrives as two lambdas
 *
 * [LobbyModel]'s settings seam exactly, and for the same boundary. [read] answers what the OS
 * currently says and [ask] raises the prompt; the app hands down `devicePermissions()`. **Both are
 * defaulted to nothing**, so a bench, a screenshot and a rendering test all draw the screen, tap
 * every row, and no modal appears anywhere — with the rows staying at [Grant.Unasked], which is the
 * honest state of a phone with no OS behind it and the state that keeps the gate shut.
 *
 * ### The gate is the whole point, not the rows
 *
 * D-161: *a player cannot join a lobby until device setup is complete.* A warmed permission that
 * was declined leaves a phone that looks like it is working and is not — E-D2-1's finding, that
 * *Don't Allow* leaves the scan screen lit, instructive, counting down and registering nothing.
 * **A refusal at setup is recoverable in ninety seconds; the same refusal at arming costs the
 * round.** So [complete] is asked before a phone hosts and before it joins, and both refuse.
 */
class PermsModel(
    private val read: (DevicePermission) -> Grant = { Grant.Unasked },
    private val ask: (DevicePermission, (Grant) -> Unit) -> Unit = { _, _ -> },
    /**
     * Ask the OS again for everything it will answer without prompting.
     *
     * The one caller is [refresh], and the reason it exists is the walk to Settings: a host who
     * left to flip a switch comes back to rows that have to move, and nothing else would move them.
     */
    private val reread: (() -> Unit) -> Unit = { it() },
) {

    /**
     * What each row says, seeded at nothing.
     *
     * Not seeded from [read] at construction: the model is built where the app starts, which on a
     * cold launch is before anything has asked an OS anything, and a row that opened on a guess
     * would be the first screen of the evening asserting a fact about the phone.
     */
    var states: Map<DevicePermission, Grant> by mutableStateOf(emptyMap())
        private set

    /**
     * What the last tap on this screen was refused with, or none.
     *
     * Cleared by the next request, because a host who has just granted something is a host whose
     * refusal is out of date. The one screen that draws it is the Perms screen's own gate line.
     */
    var refusal: String? by mutableStateOf(null)
        private set

    /** One row's answer. [Grant.Unasked] for a row nobody has asked about. */
    fun grant(of: DevicePermission): Grant = states[of] ?: Grant.Unasked

    /** What is still standing between this phone and a seat, in row order. */
    val missing: List<DevicePermission> get() = grantsMissing(states)

    /** **D-161's gate.** True when this phone may host or join. */
    val complete: Boolean get() = missing.isEmpty()

    /**
     * Read every row off the OS. Called when the screen opens and when the app comes back to it.
     *
     * The synchronous read lands first and the callback lands again afterwards, because two of the
     * five only answer late — so a row can move twice on one refresh and both of those are true.
     */
    fun refresh() {
        states = DevicePermission.entries.associateWith(read)
        reread { states = DevicePermission.entries.associateWith(read) }
    }

    /**
     * **Tap a row: raise the prompt.**
     *
     * The answer replaces the row whenever it arrives — the modal is on screen for as long as
     * somebody takes to read it, and the composition is still here when they answer.
     */
    fun request(of: DevicePermission) {
        refusal = null
        ask(of) { grant -> states = states + (of to grant) }
    }

    /**
     * **Ask for everything that is still missing**, one prompt after another.
     *
     * Serial rather than all at once, and that is not politeness: iOS shows one system modal at a
     * time and queues the rest behind it, so five simultaneous requests are five dialogs a host
     * dismisses without reading. Each answer starts the next.
     */
    fun requestMissing() {
        refusal = null
        val next = missing.firstOrNull() ?: return
        ask(next) { grant ->
            states = states + (next to grant)
            // The row that was just answered is no longer the head of `missing` whichever way it
            // went — granted removes it, refused stops the walk, and neither loops.
            if (grant.opensTheGate) requestMissing()
        }
    }

    /**
     * The gate, said to whoever is standing at it — or null when there is nothing to say.
     *
     * **Names the rows.** *Setup is incomplete* tells a host nothing they can act on; the rows they
     * have to go back and tap are the whole of what makes the refusal recoverable in ninety
     * seconds rather than at arming.
     */
    fun gateRefusal(): String? {
        val short = missing
        if (short.isEmpty()) return null
        return "THIS PHONE CANNOT TAKE A SEAT YET\n" + short.joinToString(" · ") { it.label }
    }

    /** Record a refusal against the screen. The one caller is the Perms screen's own gate. */
    fun refuse(why: String) {
        refusal = why
    }

    companion object {

        /**
         * **A phone whose setup is done**, for a screenshot and for a rendering test.
         *
         * Every row granted, so the fixture draws the screen a host sees on their second launch.
         * It is a fixture and nothing in the app builds one: the app's own model reads an OS.
         */
        fun sample(): PermsModel = PermsModel(read = { Grant.Granted }).also { it.refresh() }
    }
}

/** The permission rows, for the one screen that draws them. */
val LocalPerms: ProvidableCompositionLocal<PermsModel> = staticCompositionLocalOf { PermsModel() }
