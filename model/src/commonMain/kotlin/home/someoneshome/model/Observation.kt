package home.someoneshome.model

/**
 * Redaction, demonstrated on the type it matters most for.
 *
 * [Observation] is authority-side and carries `trueCount`. [ObservationView] is what a client
 * receives and is **physically incapable** of carrying it — the field does not exist, so no
 * future change can accidentally populate it and no reviewer has to notice a comment.
 *
 * A comment saying "authority only" is not a boundary. The type must *be* the field list.
 */
class Observation(
    val room: MarkerId,
    val trueCount: Int,
    /**
     * The error is rolled ONCE, here, at capture, and stored.
     *
     * Re-rolling it at render time would make a room's count flicker between bands while nobody
     * moved, and a flicker nobody authored is a signal nobody authored.
     */
    val reportedCount: Int,
    val staleness: Int,
)

/**
 * What a client may know about a room: a possibly-wrong count and how old it is.
 *
 * Marked [ClientFacing] because it has been checked against what a Resident is permitted to
 * learn. It cannot express ground truth, so no schema entry can leak one through it.
 *
 * **The constructor is `internal`, and this is not a `data class`.** Public construction let
 * anyone write `ObservationView(room, obs.trueCount, staleness)` — the type cannot *name* ground
 * truth but could still *carry* it, which made [toView] a convention rather than a boundary, and
 * rule 3 exists to reject exactly that. `data` is dropped because a generated `copy()` keeps
 * public visibility and would reopen the same hole.
 *
 * **Residual risk, stated:** code inside `model` can still pass the wrong Int. Narrowing at a
 * boundary cannot do better than reduce the surface to one reviewed module.
 */
@ClientFacing
@kotlinx.serialization.Serializable
class ObservationView internal constructor(
    val room: String,
    val reportedCount: Int,
    val staleness: Int,
) {
    override fun toString(): String =
        "ObservationView(room=$room, reportedCount=$reportedCount, staleness=$staleness)"
}

/** The only bridge from authority to client. Narrowing, never nulling. */
fun Observation.toView(): ObservationView =
    ObservationView(room = room.value, reportedCount = reportedCount, staleness = staleness)
