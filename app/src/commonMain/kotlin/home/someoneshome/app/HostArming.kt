package home.someoneshome.app

/**
 * **The host held LIGHTS OUT, and a round begins — E-PAY-1 / E-D3-3's trigger half, closed.**
 *
 * For three units nothing in this application armed a round. `FlowModel.lightsOut` walked the
 * host's own screen to ARMED and stopped — presentation, and its own KDoc said so — while
 * [RoundAuthority.arm] had exactly one caller in the whole tree, a bench script behind a launch
 * switch. Five seats were stood up to prove it: the lobby filled, the press landed, and no seat in
 * the house saw one push. Every other phone stood in the lobby forever.
 *
 * This is the line that was missing, and it is deliberately a function rather than three lines
 * inside a `LaunchedEffect`. Two rules live here — *only the host arms*, and *the press is what
 * arms* — and both of them have been wrong in production before with no test able to see it: P3
 * read *does this object have an authority* as *is this phone the host* and put five phones'
 * abstentions on one ballot, and the payoff run's stand-in gate was an expression in a composable
 * that nothing could check. A rule inside a lambda is a rule nothing can check.
 *
 * ### No launch switch reaches this, and that is enforced rather than intended
 *
 * The bench's arming switch is gone. What arms a round is a person's thumb on a live button for
 * two seconds; what tells this side about it is `FlowModel`'s own seam. `armingCordon` scans this
 * source set for any `launchSwitch`/`launchValue` read and fails the build on one, so the phone a
 * person launches from the springboard — with no environment at all — is the phone that plays.
 * That was E-PAY-7's real cost: the owner force-quit the host app to fix their name, relaunched
 * it by hand, lost `SOMEONES_HOME_ROUND_HOUSE` with it, and their hold armed nothing with no
 * symptom anywhere.
 */
suspend fun armAtLightsOut(
    house: RoundAuthority,
    /**
     * **Whether this phone is running the house** — `LobbyModel.hosting`, asked fresh.
     *
     * Deliberately not *does this phone have a [RoundAuthority]*, which is a different question
     * with the same shape: the app root builds one on every seat and it is simply inert on four of
     * them. Reading it the other way is what put five abstentions on one ballot in P3, and the
     * lobby's own answer is the one the LIGHTS OUT button itself is drawn from.
     */
    hosting: Boolean,
    /**
     * The round's seed, drawn where real randomness belongs — the app root, beside the seat
     * ledger's own token mint.
     *
     * It is a parameter rather than a draw inside here so that a test can arm a named round, which
     * is the only reason `armingFor` is deterministic at all: a round that replayed to different
     * questions next month would be a recording that no longer describes the evening it was made
     * on.
     */
    seed: Long,
    note: (String) -> Unit,
): Boolean {
    if (!hosting) {
        // Silent to a player and loud to the authority, which is rule 6 the right way up. A client
        // is drawn no such button at all — this is the second guard, for the third route nobody
        // has invented yet.
        note("LIGHTS OUT — NOT THE HOST, SO THERE IS NO HOUSE HERE TO ARM")
        return false
    }
    // **A round that has ended is not one that is running** (D-157, D-252). `armed` stays true on a
    // finished round, and this read it alone, so after NEW ROUND the host's LIGHTS OUT said the
    // house was already playing and the second round never armed. Arming constructs a fresh round
    // (`Rules.armed`), which is all a second round needs from here.
    if (house.armed && !house.ended) {
        // A second press on a round that is already running. The rules would refuse it in any
        // case; saying so here is what keeps the refusal readable on the host's own phone rather
        // than arriving as an unexplained `RefusalReason` in a log.
        note("LIGHTS OUT — THIS HOUSE IS ALREADY PLAYING A ROUND")
        return false
    }
    note("=== THE LIGHTS GO OUT — ARMING FOR A ROOM FULL OF PEOPLE ===")
    // **Refused: the host stays on the lobby**, which is drawing why under a inert button. Nothing on
    // the screen moves for a refusal (rule 6): the reason is in the log, in a lit room.
    if (!house.arm(seed)) return false
    note("ARMED — ${house.state.seats.size} SEAT(S) IN THE ROUND · THE EVENING IS THEIRS")
    // **No screen is walked from here** (D-234). It used to call the host's own walk to ARMED
    // (D-226), and that was the only walk there was: the guests' phones went from the lobby to the
    // springboard. The house's `OpeningMessage`, which `arm` has just sent every seat, the host's
    // own included over loopback, is what walks each of them to ARMED, by one path.
    return true
}
