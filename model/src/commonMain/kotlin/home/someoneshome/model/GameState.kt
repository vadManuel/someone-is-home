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
    val seats: List<Seat>,
    private val insiderSeats: List<Seat>,
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

    fun copy(
        armed: Boolean = this.armed,
        seats: List<Seat> = this.seats,
        insiderSeats: List<Seat> = this.insiderSeats,
        revoked: List<Seat> = this.revoked,
        cooldownArmed: List<Seat> = this.cooldownArmed,
        systemIntegrity: Int = this.systemIntegrity,
        nextEntity: Long = this.nextEntity,
        seed: Long = this.seed,
    ) = GameState(armed, seats, insiderSeats, revoked, cooldownArmed, systemIntegrity, nextEntity, seed)

    companion object {
        val EMPTY = GameState(
            armed = false,
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
