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
    val nextEntity: Long,
    val seed: Long,
) {
    fun roleOf(seat: Seat): Role =
        if (insiderSeats.any { it.index == seat.index }) Role.Insider else Role.Resident

    fun isRevoked(seat: Seat): Boolean = revoked.any { it.index == seat.index }

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
        nextEntity: Long = this.nextEntity,
        seed: Long = this.seed,
    ) = GameState(armed, ended, seats, insiderSeats, revoked, cooldownArmed, systemIntegrity, nextEntity, seed)

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
        ) = GameState(
            armed = true,
            ended = false,
            seats = seats,
            insiderSeats = insiders,
            revoked = emptyList(),
            cooldownArmed = emptyList(),
            systemIntegrity = systemIntegrity,
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
            nextEntity = 1L,
            seed = 0L,
        )
    }
}
