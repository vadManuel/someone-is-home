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
     * **Only the revoked half is implemented, and that is a live gap rather than a nuance.**
     * Nothing in [GameState] stores a restrained player — `Effect.MeetingResolved` carries one
     * and no state receives it — so a restrained seat still classifies [Live] and keeps every
     * living-class permission while `ui` renders it the outside-the-system screen. Restrain
     * cannot borrow the revoked list to fix this: rule 9 forbids collapsing the two, one being
     * system power lent by the house and the other a physical act it cannot prevent. Closing it
     * means a second list and a second clause in [roundStateOf].
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
 * Total, and the order is load-bearing. A seat that is not seated has no round at all. **[Ended]
 * outranks `!armed`**, because the round ending disarms the perimeter — checked the other way, a
 * finished round classifies every seat pre-arm, the pre-arm classes are permitted nothing, and
 * every phone in the house goes dark at the moment of winning. That presents as a missing effect,
 * not as a precedence bug. [Ended] also outranks [Out]: once the round is over every client is in
 * the same after-state.
 *
 * A seat that is not seated at all comes back [RoundState.PreArm] — it has no round. That is the
 * fail-closed direction: [EmitSchema] permits the pre-arm classes nothing.
 */
fun GameState.roundStateOf(seat: Seat): RoundState = when {
    seats.none { it.index == seat.index } -> RoundState.PreArm
    ended -> RoundState.Ended
    !armed -> RoundState.PreArm
    isRevoked(seat) -> RoundState.Out
    else -> RoundState.Live
}

/** The class a seat's client belongs to right now. Both axes, read off authority state. */
fun GameState.clientClassOf(seat: Seat): ClientClass =
    ClientClass(role = roleOf(seat), roundState = roundStateOf(seat))
