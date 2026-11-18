package home.someoneshome.app

/**
 * **Where each of this phone's log lines goes: the device console, and which of them also go to the
 * rig's log that the TRANSPORT bench draws on this phone's own screen.**
 *
 * ⚠️ **Until D-237 every `[house]` and `[round]` line went to both**, and the TRANSPORT view is two
 * taps from the PLAYTEST chip that is drawn on every screen of every playtest phone. So a player's own
 * role (`WHAT THIS PHONE IS — INSIDER`), what they pressed (`PRESSED — ArmRevoke`) and what it did
 * (`ABILITY FIRED`) were readable off their screen by anyone holding it, and the host's screen carried
 * the house's lines too. A screen that says something the house did not author breaks rules 5 and 6,
 * and this one said the one thing the game never says.
 *
 * **The rule, in one place so it can be tested:** everything about a round (the house, the round
 * client, the bench script, the screen ledger and the tick driver) goes to the **console only**. The
 * rig's log keeps only the lobby's transport lines, which are role-neutral: connects, refusals,
 * advertisements and frame counts. `ConsoleLinesTest` plays a round with an Insider through these and
 * fails on a role word, an ability name or the seed reaching the rig.
 */
internal class ConsoleLines(
    /** The rig's on-screen log (`TransportCheat.note`). */
    private val rig: (String) -> Unit,
    /** The device console, which `round.sh` keeps as `phone.log`. */
    private val console: (String) -> Unit = ::println,
) {
    /** The lobby's own transport events and the frame probe. Role-neutral; console and rig. */
    fun lobby(line: String) {
        console("[lobby] $line")
        rig(line)
    }

    /** The rules on this phone, including what became of each ability request. Console only. */
    fun house(line: String) = console("[house] $line")

    /** This phone's half of the round: every push it was sent and every control it pressed. */
    fun round(line: String) = console("[round] $line")

    /** The bench script, which names seats and acts. Console only. */
    fun bench(line: String) = console("[round-bench] $line")

    /** The screen ledger, which prints this phone's role on every line. Console only. */
    fun screen(line: String) = console("[screen] $line")

    /** The house's tick driver. Console only, with the house it drives. */
    fun clock(line: String) = console("[house-clock] $line")

    /** This phone's estimate of the house's clock, at each round of probes (C1). Console only. */
    fun offset(line: String) = console("[clock] $line")
}
