package home.someoneshome.harness

/**
 * A recorded round, replayable byte-identically.
 *
 * **Recordings hold complete authority state** — roles, real positions, real Egress progress.
 * They are debugging artifacts and are gitignored; never attach one to an issue.
 */
data class Recording(val seed: Long, val events: List<String>)
