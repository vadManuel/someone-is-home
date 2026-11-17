package home.someoneshome.platform

/**
 * **What this phone's own clock says, in the phone's own time zone** (D-243).
 *
 * For display and for nothing else: the status strip's clock, the lantern's time and date, and the
 * moment a house message landed. Nothing in the rules reads it and nothing in the round is decided
 * by it, which is why it may exist at all (rule 4 keeps every wall clock out of `core`).
 *
 * Plain numbers, so `ui`, which cannot see this module, is handed them by the app root and words
 * them itself.
 */
data class LocalWallTime(
    val hour: Int,
    val minute: Int,
    /** ISO: 1 is Monday, 7 is Sunday. */
    val weekday: Int,
    val day: Int,
    /** 1 is January. */
    val month: Int,
)

/** The phone's wall clock now, in its own zone. See [LocalWallTime]. */
expect fun localWallTime(): LocalWallTime
