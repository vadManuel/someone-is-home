package home.someoneshome.model

/**
 * **One step of a haptic pattern — data on this side of the boundary, a vibration on the other.**
 *
 * Shaped for the thing that plays it: alternating durations, no intensities, nothing to interpret
 * — the vocabulary every platform vibration API already has. A script is a `List<HapticStep>` and
 * nothing richer, because a step that carried an intensity would be a step that could differ
 * between two phones without either of their scripts differing in length, and D-102's rule is
 * about what a room can feel rather than about what a pattern is called.
 *
 * ### It lives here rather than in `ui`, and that is E-S1-2 closed
 *
 * `SniffGroups` built the first one of these, in `ui`, with a flag on it: `platform` sees `model`
 * and never `ui`, so a script the platform layer consumes directly has to be a `model` type. It is
 * pure data with no role and no answer in it, so the move was the file move that flag predicted.
 * It is deliberately **not** `@Serializable`: nothing puts a script on the wire — a Subroutine's
 * parameters travel and the client builds the script from them.
 */
sealed interface HapticStep {

    /** The motor is on for [millis]. */
    data class Buzz(val millis: Int) : HapticStep

    /** The motor is off for [millis]. */
    data class Rest(val millis: Int) : HapticStep
}

/**
 * **The two buzzes this app has, defined once each, and there is no third.**
 *
 * D-102: *the buzz is identical for every player — same pattern, same duration, including when an
 * Insider's own Revoke lands — or it is an audible tell in a silent house.* D-135 closes which
 * buzz: every event buzzes, and the long one is reserved for five — the Egress, an incoming phone
 * call, STAND AND WALK IN for a newly Revoked player, the Restrained takeover, and the end of the
 * LIGHTS OUT countdown.
 *
 * ### The doctrine is enforced by there being nowhere else to get a pattern from
 *
 * A rule that says *"always pass the same duration"* is a rule every call site can break by
 * accident, and the symptom — one screen buzzing a little longer than the others — is exactly the
 * kind of thing nobody notices from across a dark room until it has already told them something.
 * So the durations are private, [script] is the only way out of a [Haptic], and the platform seam
 * above this takes a [Haptic] rather than a number. **A call site cannot ask for a buzz of its
 * own length, because there is no parameter for one.**
 *
 * Adding a member to [Haptic] is a decision and not a parameter (D-135 says so in as many words);
 * `when` here is exhaustive, so it is also a compile error until somebody writes the pattern down.
 */
object HouseBuzz {

    /**
     * **The short buzz, and it is the only short buzz in the app.**
     *
     * `SniffGroups.PULSE_MILLIS` reads this rather than carrying its own copy: D-140 records that
     * Drift's now-pulse *"is the same short buzz Sniff's groups are made of, because there is one
     * of them in this app rather than one per Subroutine"*, and two constants agreeing by hand is
     * the arrangement that sentence exists to refuse.
     */
    const val SHORT_MILLIS: Int = 90

    /**
     * **The long buzz.** Long enough to be unmistakably not the short one through a pocket, which
     * is the entire job: D-135's five are the events a player must not miss, and a duration a
     * hurried thumb could confuse with the ordinary buzz would collapse the distinction the
     * closed set is built on. Playtest owns the number, as it owns the 7.
     */
    const val LONG_MILLIS: Int = 750

    private val SHORT: List<HapticStep> = listOf(HapticStep.Buzz(SHORT_MILLIS))
    private val LONG: List<HapticStep> = listOf(HapticStep.Buzz(LONG_MILLIS))

    /**
     * The pattern for a kind. **The same list every time**, held rather than rebuilt — the whole
     * app has an allocation budget of about 0.5 MB/s and a buzz arrives on the effect thread,
     * which is one of the threads that budget is actually spent on.
     */
    fun script(haptic: Haptic): List<HapticStep> = when (haptic) {
        Haptic.Short -> SHORT
        Haptic.Long -> LONG
    }

    /** Every pattern an event is allowed to produce. What a leak test compares against. */
    val ALL: List<List<HapticStep>> = Haptic.entries.map(::script)
}
