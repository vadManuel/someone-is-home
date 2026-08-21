package home.someoneshome.model

/**
 * Authority state. Holds ground truth, and therefore must never be `@Serializable`.
 *
 * **Ordered collections only.** `Map` and `Set` iteration order varies with hashing, and a
 * different iteration order produces a different effect order, which produces a recording that
 * does not replay. Seats are held as a sorted list and looked up by index.
 */
class GameState private constructor(
    val armed: Boolean,
    /**
     * The round is over.
     *
     * **Nothing writes this yet, and that is stated rather than hidden.** The round-end condition
     * is F-005 (the SystemIntegrity denominator) and is open, so no [Event] sets it. The field
     * exists because the client taxonomy has four round-states and [RoundState.Ended] is one of
     * them: an allowlist that cannot name the ended classes cannot deny them anything either.
     * Reachable today only through [endRound], whose sole caller in the repo is
     * `model/src/commonTest/.../EmitSchemaTest.kt`. The harness does NOT exercise the ended
     * classes — said plainly, because in this codebase these comments are the record of what is
     * and is not covered.
     */
    val ended: Boolean,
    val seats: List<Seat>,
    val insiderSeats: List<Seat>,
    val revoked: List<Seat>,
    /**
     * **Revoked and not yet through a meeting.**
     *
     * D-134 splits the out in two and the split is a fact about *when*, not about who: a newly
     * Revoked player gets STAND AND WALK IN with a long haptic (D-135), while the couch —
     * everybody Revoked or Restrained at an earlier meeting — is called in with an ordinary buzz.
     * Cleared when a meeting closes, because after that meeting nobody in it is new.
     */
    val newlyRevoked: List<Seat>,
    /**
     * **Restrained: held by the room, then deauthorised by the house** (`gdd.md:1009`).
     *
     * **A second list, never a second use of [revoked].** Rule 9 forbids collapsing the two: a
     * Revoke is system power the house lends an Insider, a Restrain is a physical act the house
     * cannot prevent, and a state that stored them together would make the distinction a
     * convention somebody maintains rather than a fact the type carries. This closes the gap
     * [RoundState.Out] documented as live — *"nothing in GameState stores a restrained player"* —
     * and it closes it the way that comment said it had to be closed.
     */
    val restrained: List<Seat>,
    val cooldownArmed: List<Seat>,
    val systemIntegrity: Int,
    /**
     * **What each seat currently has open, and what the house will grade it against** (D-109).
     *
     * A List in seat order, for the reason every other collection here is one: two seats' entries
     * graded in hash order emit their verdicts in hash order, and a recording that does not replay
     * is the only debugging instrument this game has, broken.
     *
     * **One row per seat, and the row is the spine L3 fills in.** See [OpenSubroutine] for what is
     * provisional about it. Nothing outside the rules ever reads this: it carries the answer.
     */
    val openSubroutines: List<OpenSubroutine>,
    /**
     * The meeting in progress, or null. **The whole lifecycle lives in here** — see [Meeting].
     *
     * Never reaches a client as itself: it carries every seat's live selection, which is the one
     * thing at a meeting only a player outside the system may read (D-075, D-117).
     */
    val meeting: Meeting?,
    /**
     * **An Egress is running. THE STUB — nothing writes this yet, said plainly.**
     *
     * The Egress itself is not this unit's and is not built: no node designation (F-001), no
     * countdown, no completion. The flag exists because D-133 is a rule about *meetings* that
     * happens to be phrased about the Egress — the meeting card is inert for the duration and the
     * Revoke report is not — and a meeting engine that could not express that rule would have the
     * gap hidden inside it rather than named.
     *
     * Same posture [ended] shipped with: reachable only through [withEgress], whose callers in
     * this repo are tests. **Two things are owed when the Egress lands:** something must set it,
     * and D-133's *"a reported Revoke pauses the Egress timer — it never resets it"* needs a timer
     * to pause. Neither is here.
     */
    val egressRunning: Boolean,
    val nextEntity: Long,
    val seed: Long,
) {
    fun roleOf(seat: Seat): Role =
        if (insiderSeats.any { it.index == seat.index }) Role.Insider else Role.Resident

    fun isRevoked(seat: Seat): Boolean = revoked.any { it.index == seat.index }

    fun isRestrained(seat: Seat): Boolean = restrained.any { it.index == seat.index }

    /** In the round: seated, and neither Revoked nor Restrained. Ordered, like everything here. */
    val livingSeats: List<Seat> get() = seats.filterNot { isRevoked(it) || isRestrained(it) }

    /** In the building, outside the system. The complement of [livingSeats], in seat order. */
    val outSeats: List<Seat> get() = seats.filter { isRevoked(it) || isRestrained(it) }

    /** The only writer of [egressRunning]. Separate from [copy] so the stub stays greppable. */
    fun withEgress(running: Boolean): GameState = copy(egressRunning = running)

    /** What this seat has open, or null for a seat the house has assigned nothing. */
    fun openSubroutineFor(seat: Seat): OpenSubroutine? =
        openSubroutines.firstOrNull { it.seat.index == seat.index }

    /**
     * Replace one seat's open Subroutine, leaving every other row and the list's order alone.
     *
     * A seat with no row is left with no row rather than being given one: an assignment nobody
     * made is work nobody can do, and inventing one here would hide a missing draw behind a scan.
     */
    fun withOpenSubroutine(replacement: OpenSubroutine): GameState = copy(
        openSubroutines = openSubroutines.map {
            if (it.seat.index == replacement.seat.index) replacement else it
        },
    )

    /** Seeded, recorded, monotonic. Never `Uuid.random()` — replay would mint different ids. */
    fun mintId(): Pair<EntityId, GameState> =
        EntityId(nextEntity) to copy(nextEntity = nextEntity + 1)

    /** The only writer of [ended]. Separate from [copy] so the transition is greppable. */
    fun endRound(): GameState = copy(ended = true)

    fun copy(
        armed: Boolean = this.armed,
        ended: Boolean = this.ended,
        seats: List<Seat> = this.seats,
        insiderSeats: List<Seat> = this.insiderSeats,
        revoked: List<Seat> = this.revoked,
        newlyRevoked: List<Seat> = this.newlyRevoked,
        restrained: List<Seat> = this.restrained,
        cooldownArmed: List<Seat> = this.cooldownArmed,
        systemIntegrity: Int = this.systemIntegrity,
        openSubroutines: List<OpenSubroutine> = this.openSubroutines,
        meeting: Meeting? = this.meeting,
        egressRunning: Boolean = this.egressRunning,
        nextEntity: Long = this.nextEntity,
        seed: Long = this.seed,
    ) = GameState(
        armed, ended, seats, insiderSeats, revoked, newlyRevoked, restrained, cooldownArmed,
        systemIntegrity, openSubroutines, meeting, egressRunning, nextEntity, seed,
    )

    companion object {
        /**
         * A freshly armed round. Every field is set explicitly.
         *
         * Deliberately not a `copy()` of the previous state: arming is the clean start of a
         * round, and anything carried across it is invisible to everyone.
         */
        fun armedRound(
            seed: Long,
            seats: List<Seat>,
            insiders: List<Seat>,
            systemIntegrity: Int,
            /**
             * The work order, drawn by the rules and stored here.
             *
             * Defaulted empty so a caller that only cares about the meter — every test that
             * builds an armed state by hand — does not have to invent one. An empty ledger is the
             * fail-closed direction: no seat has anything open, so every entry grades false.
             */
            openSubroutines: List<OpenSubroutine> = emptyList(),
        ) = GameState(
            armed = true,
            ended = false,
            seats = seats,
            insiderSeats = insiders,
            revoked = emptyList(),
            newlyRevoked = emptyList(),
            restrained = emptyList(),
            cooldownArmed = emptyList(),
            systemIntegrity = systemIntegrity,
            openSubroutines = openSubroutines,
            meeting = null,
            egressRunning = false,
            nextEntity = 1L,
            seed = seed,
        )

        val EMPTY = GameState(
            armed = false,
            ended = false,
            seats = emptyList(),
            insiderSeats = emptyList(),
            revoked = emptyList(),
            newlyRevoked = emptyList(),
            restrained = emptyList(),
            cooldownArmed = emptyList(),
            systemIntegrity = 0,
            openSubroutines = emptyList(),
            meeting = null,
            egressRunning = false,
            nextEntity = 1L,
            seed = 0L,
        )
    }
}
