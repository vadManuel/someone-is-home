package home.someoneshome.harness

import home.someoneshome.model.GameState

/**
 * The outcome of trying to resume a round from a recording (story 0.10).
 *
 * **Resuming is allowed to fail, and failing is the safe direction.** A round that comes back
 * subtly wrong is worse than one that does not come back: eight people are standing in a dark
 * house, and a resumed round that disagrees with the one they were playing hands them a game
 * whose history nobody can appeal to.
 */
sealed interface ResumeResult {

    /**
     * The recording replayed clean and this is the authority state to carry on from.
     *
     * [eventsReplayed] and [refusalsReplayed] are reported so the caller can say how much round
     * came back, rather than presenting a resumed round as though nothing happened.
     */
    data class Resumed(
        val state: GameState,
        val eventsReplayed: Int,
        val refusalsReplayed: Int,
    ) : ResumeResult

    /** The recording did not reproduce itself. [because] is the divergence, verbatim. */
    data class Refused(val because: ReplayResult.Diverged) : ResumeResult

    /** The recording could not be read at all. */
    data class Unreadable(val because: MalformedRecording) : ResumeResult
}

/**
 * **Story 0.10 — host crash recovery. Relaunch and resume the round from the local recording.**
 *
 * Same device, same authority, no election. **Not migration** — authority is the host device and
 * there is no handover in v1.
 *
 * ### It replays before it trusts
 *
 * The recording is not merely read; it is fed back through the rules and checked against itself
 * first. A recording is a debugging artifact written by a process that then crashed, so "it
 * parsed" is a much weaker claim than "the rules, run again, produce exactly it". Only the second
 * one licences carrying on.
 *
 * ### It starts from EMPTY and rebuilds, rather than reading a state row
 *
 * The state rows are *expected output*, not input. Reconstructing authority state by parsing one
 * would create a second way to build a `GameState` — one that bypasses the rules entirely and
 * could mint a round that no sequence of events could produce. Replaying the events is slower and
 * is the only construction path that cannot invent a state.
 *
 * That also means the recorded initial-state row gets checked as a side effect: `replay` compares
 * it, so a recording that began somewhere else is refused rather than silently rebased.
 *
 * ### Refusals come back too
 *
 * The gate's refusals are replayed and compared like everything else. A resumed round that had
 * forgotten which events it turned away would start disagreeing with its own recording at the
 * first thing it did.
 */
fun resumeFromText(text: String): ResumeResult {
    val recording = try {
        RecordingText.parse(text)
    } catch (failure: MalformedRecording) {
        return ResumeResult.Unreadable(failure)
    }
    return resume(recording)
}

/** As [resumeFromText], for a recording already in hand. */
fun resume(recording: Recording): ResumeResult {
    // EMPTY, always. The recorded initial-state row is checked by `replay` rather than trusted as
    // a starting point — a recording that began elsewhere must be refused, not rebased.
    val from = GameState.EMPTY

    return when (val verdict = replay(from, recording)) {
        is ReplayResult.Diverged -> ResumeResult.Refused(verdict)
        ReplayResult.Identical -> ResumeResult.Resumed(
            state = record(from, recording.events).first,
            eventsReplayed = recording.events.size,
            refusalsReplayed = recording.refusalTranscript.size,
        )
    }
}
