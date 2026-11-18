package home.someoneshome.model

/**
 * Redaction, demonstrated on the type it matters most for.
 *
 * [Observation] is authority-side and carries `trueCount`. [ObservationView] is what a client
 * receives and is **physically incapable** of carrying it — the field does not exist, so no
 * future change can accidentally populate it and no reviewer has to notice a comment.
 *
 * A comment saying "authority only" is not a boundary. The type must *be* the field list.
 *
 * ### Captured by the house now, and sent to nobody (O4)
 *
 * The rules capture one of these for the sensor's room on every `Event.Sensed` and keep the latest
 * per room in `GameState.observations`. **No effect carries one**, so nothing here reaches a phone;
 * the Terminal's view of them is the occupancy spec's O6, which waits on the owner.
 */
class Observation(
    /** The capture's own number in the round, and the only key its error is rolled under. */
    val id: Long,
    /** A room of the round's plan, by name (rooms are keyed by name: `HousePlan.paint`). */
    val room: String,
    /** When the house captured it: the sensor's upload, on the house's clock. */
    val seenAt: Tick,
    /** The living seats the house had placed in [room] at [seenAt]. Never leaves the authority. */
    val trueCount: Int,
    /**
     * The error is rolled ONCE, here, at capture, and stored.
     *
     * Re-rolling it at render time would make a room's count flicker between bands while nobody
     * moved, and a flicker nobody authored is a signal nobody authored.
     */
    val reportedCount: Int,
    /**
     * **Signed jitter on the age, in ticks, rolled once at capture and stored** (`gdd.md:484`), so
     * band edges cannot be used to read exact scan timing and a count does not flicker between
     * bands at render time.
     */
    val ageJitter: Long,
) {
    /** How old this reading looks at [now]: the real age plus the stored jitter. Rolls nothing. */
    fun apparentAge(now: Tick): Long = now.step - seenAt.step + ageJitter

    /**
     * **Fresh (0), stale (1) or cold (2) at [now]** (`gdd.md:483`), a pure function of the stored
     * capture and the moment asked, so the same reading asked twice answers the same band.
     */
    fun stalenessAt(now: Tick): Int = when {
        apparentAge(now) < FRESH_FOR -> 0
        apparentAge(now) < STALE_FOR -> 1
        else -> 2
    }

    companion object {
        /** ⚠️ **Provisional, 15 s** (occupancy spec Q5; `gdd.md` A-7). Fresh below this age. */
        const val FRESH_FOR: Long = 15L * Balance.TICKS_PER_SECOND

        /** ⚠️ **Provisional, 45 s.** Stale below this age, cold at and past it. */
        const val STALE_FOR: Long = 45L * Balance.TICKS_PER_SECOND
    }
}

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
 *
 * **No message carries it yet.** Its shape is the occupancy spec's O6 to settle.
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

/** The only bridge from authority to client. Narrowing, never nulling. Banded at [now]. */
fun Observation.toView(now: Tick): ObservationView =
    ObservationView(room = room, reportedCount = reportedCount, staleness = stalenessAt(now))
