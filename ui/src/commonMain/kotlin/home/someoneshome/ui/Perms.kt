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

    /**
     * What is still standing between this phone and a seat, in row order — **as the screen has it.**
     *
     * [states] and therefore empty until something refreshed it. That is right for the rows, which
     * must not assert a fact nobody read, and it is wrong for a gate; see [missingNow].
     */
    val missing: List<DevicePermission> get() = grantsMissing(states)

    /** **D-161's gate, as the screen has it.** True when the rows this screen drew are all open. */
    val complete: Boolean get() = missing.isEmpty()

    /**
     * ⚠️ **What the OS says right now** — [missing]'s sibling, and the one a gate is allowed to ask.
     *
     * ### The false refusal this exists to stop
     *
     * [states] opens empty on purpose and `refresh()`'s one caller is the Perms screen's own
     * `LaunchedEffect`. So every path into a lobby that did not *compose that screen* asked the
     * gate about a map nobody had filled — and on the 13 Pro, with three of five rows granted at
     * the OS, the answer was the whole five-row sentence:
     *
     * ```
     * [lobby-bench] NOT HOSTING — THIS PHONE CANNOT TAKE A SEAT YET
     *               LOCAL NETWORK · CAMERA · BLUETOOTH · MOTION & FITNESS · NOTIFICATIONS
     * ```
     *
     * **That refusal was false, and a false refusal is worse than an open gate**: it names rows
     * the host has already granted, so the one thing it offers them to do is the one thing that
     * cannot help. Both benches take that path — `go(ScreenId.Join)` at launch, early enough to
     * pre-empt Boot's first frame — and no round could be armed on hardware or on a simulator for
     * three commits.
     *
     * ### The ruling: the gate reads the OS at decision time
     *
     * A fresh read, through the same [read] seam the rows use, taken when `hostHome` and
     * `attachToHome` ask the question — **never a screen's cached map**. The OS answers these
     * queries synchronously (`Permissions.grant` is documented as safe to call while drawing), so
     * there is no cost to asking at the moment it matters, and asking then is the only way to be
     * sure the answer is about this phone rather than about which screens happen to have composed.
     *
     * **It deliberately does not write [states].** The KDoc on that field argues that a row must
     * not open on a guess, and that argument governs what a *screen asserts* — it says nothing
     * about what a gate may know. Keeping the two apart means this can be honest without the Perms
     * screen becoming a screen that reports facts nobody asked it to read.
     *
     * ⚠️ Flagged for the owner's morning review: the placement was a design call with three
     * answers — the app root, a read at gate time, or seeding at construction — and this is the
     * second.
     */
    fun missingNow(): List<DevicePermission> =
        grantsMissing(DevicePermission.entries.associateWith(read))

    /**
     * **What reads switched off right now** (D-311): the rows the OS reads [Grant.Refused], the
     * Bluetooth radio turned off among them (the app reads it so). [missingNow]'s question for a
     * phone already seated: it passed the gate with every row GRANTED, so anything it lacks now was
     * switched off, and reads refused. A row nobody has answered is not a switch-off; it is only
     * ever seen on a phone with no OS behind it, which this never lets go.
     */
    fun switchedOffNow(): List<DevicePermission> =
        DevicePermission.entries.filter { read(it) == Grant.Refused }

    /**
     * Read every row off the OS. Called when the screen opens and when the app comes back to it.
     *
     * The synchronous read lands first and the callback lands again afterwards, because two of the
     * five only answer late — so a row can move twice on one refresh and both of those are true.
     */
    fun refresh() {
        states = DevicePermission.entries.associateWith(read)
        walkOnIfOpened()
        reread {
            states = DevicePermission.entries.associateWith(read)
            walkOnIfOpened()
        }
    }

    /**
     * **The rows as the OS reads them now, with nothing asked again** (D-311): what a phone the
     * lobby let go is shown on arrival, the row that was switched off reading REFUSED. A read alone,
     * so no Nearby Interaction probe starts here (D-309); the screen's own [refresh] on arrival is
     * the one it always ran.
     */
    fun readNow() {
        states = DevicePermission.entries.associateWith(read)
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
     *
     * ### An answer that arrives late still starts the next (D-314)
     *
     * Bluetooth's first answer is TAP TO GRANT: the manager that raises the prompt does not know
     * its state yet, and the real answer comes a moment later from its delegate, when the person
     * taps Allow (on a simulator, NOT ON THIS PHONE). So the walk keeps the row it asked in
     * [awaiting] until that row has a real answer, heard on the prompt's callback (which may speak
     * more than once) or on a read of the OS ([refresh]), and only then goes on or stops.
     */
    fun requestMissing() {
        refusal = null
        val next = missing.firstOrNull()
        awaiting = next
        if (next != null) ask(next) { grant -> answered(next, grant) }
    }

    /**
     * The row the walk asked and has not had a real answer for yet, or none (D-314). Only this row
     * moves the walk on, and only once: whatever it answers, the walk stops waiting on it.
     */
    private var awaiting: DevicePermission? = null

    /** **Whether REQUEST WHAT IS MISSING is still waiting to hear a row it asked** (D-314). */
    val walking: Boolean get() = awaiting != null

    /**
     * One answer on the prompt's callback. The row that answered is no longer the head of `missing`
     * whichever way it went — granted removes it, refused or ASKED stops the walk, and none loops.
     * An ASKED NEARBY DEVICES is still the head of `missing`, so the next press probes it again
     * (D-309) rather than this walk probing it forever. TAP TO GRANT is no answer yet, so the walk
     * keeps waiting on that row. An answer for a row the walk is no longer waiting on moves only
     * the row.
     */
    private fun answered(row: DevicePermission, grant: Grant) {
        states = states + (row to grant)
        if (awaiting == row && grant != Grant.Unasked) {
            awaiting = null
            if (grant.opensTheGate) requestMissing()
        }
    }

    /**
     * A read of the OS found the row the walk is waiting on open: go on (D-314). A read that finds
     * it anything else changes nothing, because a probe still in flight reads ASKED and its own
     * answer is the one the walk waits for.
     */
    private fun walkOnIfOpened() {
        val row = awaiting
        if (row != null && grant(row).opensTheGate) {
            awaiting = null
            requestMissing()
        }
    }

    /**
     * The gate, said to whoever is standing at it — or null when there is nothing to say.
     *
     * **Names the rows.** *Setup is incomplete* tells a host nothing they can act on; the rows they
     * have to go back and tap are the whole of what makes the refusal recoverable in ninety
     * seconds rather than at arming.
     */
    fun gateRefusal(): String? = refusalOver(missing)

    /**
     * **The same sentence, over the rows the OS is refusing right now** — the lobby's gate.
     *
     * [gateRefusal]'s sibling and the only difference is [missingNow] in place of [missing]. The
     * words are shared rather than written twice on purpose: a host who is refused at the lobby
     * and then walks back to the Perms screen has to read the same sentence in both places, or
     * the two surfaces are two opinions about one phone.
     */
    fun gateRefusalNow(): String? = refusalOver(missingNow())

    private fun refusalOver(short: List<DevicePermission>): String? {
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
