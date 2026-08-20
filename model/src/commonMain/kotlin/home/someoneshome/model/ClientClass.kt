package home.someoneshome.model

/**
 * Where a seat stands relative to the round. **The second axis of the client taxonomy.**
 *
 * Round-state is publicly observable — everyone in the house can see whether the lights are on,
 * and D-068 is what makes it safe to tell a client it was refused for a round-state reason. It is
 * therefore never itself a leak. What it changes is *what a client may be told*, and it changes
 * it independently of [Role].
 */
enum class RoundState {
    /** No round exists. D-067: nothing in-game runs in the lobby, including the contact radio. */
    PreArm,

    /** In the round, in the system, playing. */
    Live,

    /**
     * Revoked or restrained: in the building, outside the system.
     *
     * **The reason the taxonomy needs two axes.** A player who is out sees the real progress bars
     * and true occupancy, which no living player of *either* role may see (gdd.md:1014). Keyed on
     * [Role] alone, the same entry that lets an out Resident see them lets a living Resident see
     * them too — and that is a leak an allowlist was built to prevent, shipped by the allowlist.
     */
    Out,

    /** The round is over. */
    Ended,
}

/**
 * The unit the emit allowlist is keyed on: **role AND round-state, never role alone.**
 *
 * Eight classes. Not every one is reachable in a given round, and the unreachable ones are still
 * enumerated so that a permission set can say "not this one" about them explicitly.
 */
data class ClientClass(val role: Role, val roundState: RoundState) {
    override fun toString(): String = "$role/$roundState"

    companion object {
        /** All eight, ordered. Ordered because an unordered set of classes renders unstably. */
        val ALL: List<ClientClass> =
            RoundState.entries.flatMap { phase -> Role.entries.map { ClientClass(it, phase) } }
    }
}

/**
 * Which round-state a seat is in.
 *
 * Total, and deliberately ordered so that the *narrowest* condition wins last. Pre-arm dominates
 * because before arming there is no round for anyone to be out of; ended dominates being out
 * because once the round is over every client is in the same after-state.
 *
 * A seat that is not seated at all comes back [RoundState.PreArm] — it has no round. That is the
 * fail-closed direction: [EmitSchema] permits the pre-arm classes nothing.
 */
fun GameState.roundStateOf(seat: Seat): RoundState = when {
    !armed -> RoundState.PreArm
    seats.none { it.index == seat.index } -> RoundState.PreArm
    ended -> RoundState.Ended
    isRevoked(seat) -> RoundState.Out
    else -> RoundState.Live
}

/** The class a seat's client belongs to right now. Both axes, read off authority state. */
fun GameState.clientClassOf(seat: Seat): ClientClass =
    ClientClass(role = roleOf(seat), roundState = roundStateOf(seat))
