package home.someoneshome.platform

/**
 * Where the seat token lives between process exits — the "stored" in *"resume presents the
 * stored seat token"* (story 0.8).
 *
 * A phone that crashes mid-round relaunches with empty memory. Without this file it is a
 * stranger, and a stranger cannot be re-seated without rebuilding the attribution hole G1 names
 * — so the token is written at seating and the relaunched app constructs its session from it.
 * Text in, text out: this layer stores a string and does not know what a token is, the same
 * doctrine as [saveSavedHomes].
 *
 * ### A failed save THROWS, same as the homes store, and for a sharper reason
 *
 * A token that silently failed to persist is a phone that plays the whole round normally and
 * becomes unresumable at the exact moment resuming matters — the failure ships invisibly and is
 * discovered standing in a dark hallway. The *caller* decides how to surface it (rule 6: silent
 * to the player, loud to the authority); this layer's job is only to refuse to pretend.
 *
 * Cleared when a session ends for good: a token for a round that no longer exists is a resume
 * refusal waiting to confuse somebody.
 */
expect fun saveSeatToken(text: String)

/** The stored token, or null if none. Never a partial read. */
expect fun loadSeatToken(): String?

/** Forget the stored token. The seat, if any, stays the ledger's business. */
expect fun clearSeatToken()

/** A save that did not happen. The phone would not survive a relaunch; do not pretend it would. */
class SeatTokenNotSaved(val path: String) : IllegalStateException(
    "the seat token was not written to $path. This phone cannot resume after a crash; " +
        "do not report the seating as durable."
)

/**
 * **The reports this phone owes the house, kept beside the seat token** (D-229's recommendation,
 * built in D-245).
 *
 * A phone that leaves the foreground mid-round owes the house one report (D-187), sent when it is
 * back. iOS **ends** an app whose Camera or Motion grant is withdrawn in Settings, and going to
 * Settings is leaving the foreground, so a report held only in memory failed with the process and the
 * relaunch resumed its seat owing nothing. So it is written here at the leave, read back by the
 * relaunched process, and cleared when the house answers that it has it. Text in, text out, the
 * same doctrine as the token: this layer does not know what a report is.
 *
 * A failed save **throws**, as the token's does, and the caller says so in its own log.
 */
expect fun saveOwedReports(text: String)

/** The owed reports as last kept, or null if none. Never a partial read. */
expect fun loadOwedReports(): String?

/** Forget the owed reports: the house has them, or the round they belong to is over. */
expect fun clearOwedReports()

/** A save that did not happen: a report owed now would not survive the process ending. */
class OwedReportsNotSaved(val path: String) : IllegalStateException(
    "the owed reports were not written to $path. A report owed now fails with this process."
)
