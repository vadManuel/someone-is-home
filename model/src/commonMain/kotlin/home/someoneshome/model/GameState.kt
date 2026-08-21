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
    val nextEntity: Long,
    val seed: Long,
) {
    fun roleOf(seat: Seat): Role =
        if (insiderSeats.any { it.index == seat.index }) Role.Insider else Role.Resident

    fun isRevoked(seat: Seat): Boolean = revoked.any { it.index == seat.index }

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
        cooldownArmed: List<Seat> = this.cooldownArmed,
        systemIntegrity: Int = this.systemIntegrity,
        openSubroutines: List<OpenSubroutine> = this.openSubroutines,
        nextEntity: Long = this.nextEntity,
        seed: Long = this.seed,
    ) = GameState(
        armed, ended, seats, insiderSeats, revoked, cooldownArmed, systemIntegrity,
        openSubroutines, nextEntity, seed,
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
            cooldownArmed = emptyList(),
            systemIntegrity = systemIntegrity,
            openSubroutines = openSubroutines,
            nextEntity = 1L,
            seed = seed,
        )

        val EMPTY = GameState(
            armed = false,
            ended = false,
            seats = emptyList(),
            insiderSeats = emptyList(),
            revoked = emptyList(),
            cooldownArmed = emptyList(),
            systemIntegrity = 0,
            openSubroutines = emptyList(),
            nextEntity = 1L,
            seed = 0L,
        )
    }
}
