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
 */
@ClientFacing
@kotlinx.serialization.Serializable
data class ObservationView(
    val room: String,
    val reportedCount: Int,
    val staleness: Int,
)

/** The only bridge from authority to client. Narrowing, never nulling. */
fun Observation.toView(): ObservationView =
    ObservationView(room = room.value, reportedCount = reportedCount, staleness = staleness)
