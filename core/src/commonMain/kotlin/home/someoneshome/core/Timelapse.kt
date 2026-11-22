package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Playback
import home.someoneshome.model.RoomKind
import home.someoneshome.model.Tick
import home.someoneshome.model.Timelapse
import home.someoneshome.model.TimelapseFrame
import home.someoneshome.model.toView

/*
 * **The station's timelapse, in the rules** (D-319; the owner's 2026-11-21 rulings and `gdd.md`'s
 * timelapse table). See `model`'s [Timelapse] for what each ruling says; this is where they act.
 *
 * Three pieces, and every one is a pure function of the events the recording holds:
 *
 * 1. [sampled], before every event: the history's marks that have passed, each taken from the state
 *    as it stood at the mark. The rules read no clock, so a mark is taken at the first event past
 *    it, from the state no later event has touched yet: the same frames, from the same events, in
 *    any replay.
 * 2. [timelapsePlayed] and [timelapseStopped], the reader's two requests. **Neither emits anything,
 *    whatever it decides** (rule 1): what the reader is shown is the beat, sealed into the upload's
 *    answer at one width on every phone, so there is no effect whose presence could say a playback
 *    began.
 * 3. [settled], after every event: the ways a playback ends that are not the reader's STOP. It
 *    played out; a meeting was called (*not an early cancel*, the owner's ruling); or the reader no
 *    longer holds the station, any of D-203's other three ways, which is an early cancel. Written
 *    once, after every handler, for [GameState.reader]'s reason: *a rule written into both would be a
 *    rule the next way out is missing.* And a new shift (a meeting's end) wipes the history, moves
 *    the floor, and clears the cooldown to the new, empty history (`cooldown == history duration`).
 */

/** **The history's marks that have passed before [at]**, taken from [state] as it stands (D-319). */
internal fun sampled(state: GameState, at: Tick): GameState {
    val held = state.timelapse
    if (!state.armed || state.ended || state.meeting != null || held.nextFrameAt >= at) return state
    // A quiet house can go a long time between events; only the last [Timelapse.FRAMES] marks can
    // be kept, and between two events the state does not move, so the skipped ones were the same.
    var mark = held.nextFrameAt.step
    val due = (at.step - 1 - mark) / Timelapse.FRAME_EVERY + 1
    if (due > Timelapse.FRAMES) mark += (due - Timelapse.FRAMES) * Timelapse.FRAME_EVERY
    val frames = ArrayList<TimelapseFrame>(Timelapse.FRAMES + 1)
    frames.addAll(held.history)
    while (mark < at.step) {
        frames += frameAt(state, Tick(mark))
        mark += Timelapse.FRAME_EVERY
    }
    val kept = if (frames.size > Timelapse.FRAMES) frames.subList(frames.size - Timelapse.FRAMES, frames.size).toList() else frames
    return state.copy(timelapse = held.copy(history = kept, nextFrameAt = Tick(mark)))
}

/**
 * **One frame: the bare counts the reader would have been shown at [at], or the jam** (D-319).
 *
 * Per room in plan order, the room's latest capture this shift through `Observation.toView`, the
 * one bridge that cannot carry a true count, so a frame holds the house's error exactly as the live
 * view did (D-272). Stairs and a room nobody has sensed are 0 (`gdd.md:485`, `:524`). **No band**:
 * the owner ruled frames bare. **Jammed** (D-186), the frame holds nothing but the jam: *a player
 * who watches it back learns the window and nothing inside it*.
 */
internal fun frameAt(state: GameState, at: Tick): TimelapseFrame {
    val plan = state.plan
    val jammed = state.staticRunning(at)
    val counts = ArrayList<Int>(plan.rooms.size)
    for (room in plan.rooms) {
        val held = if (jammed || room.kind == RoomKind.Stairs) null else state.observationOf(room.name)
        counts += held?.takeIf { it.seenAt >= state.shiftStartedAt && it.seenAt <= at }?.toView(at)?.reportedCount ?: 0
    }
    return TimelapseFrame(at, counts, jammed)
}

/**
 * **TIMELAPSE pressed** (D-319). A playback begins when the actor holds the station, nothing is
 * playing, the station's cooldown has run, and there is history to play; otherwise nothing changes.
 *
 * The cooldown is the station's and starts **at playback end**, equal to the history played
 * (`cooldown == history duration`), so it is written now as the playback's end plus its length; a
 * STOP, a lost lock or a meeting rewrites it (below). Emits nothing either way.
 */
internal fun timelapsePlayed(state: GameState, event: Event.TimelapsePlayed): Reduction<GameState, Effect> {
    val held = state.timelapse
    val history = held.history
    val starts = state.meeting == null &&
        state.readerHolding()?.index == event.actor.index &&
        held.playingAt(event.at) == null &&
        held.readyAt <= event.at &&
        history.isNotEmpty()
    val playback = Playback(event.actor, event.at, history)
    val next = if (starts) {
        state.copy(timelapse = held.copy(playback = playback, readyAt = playback.endsAt + historyLength(history.size)))
    } else {
        state
    }
    return Reduction(next, emptyList())
}

/**
 * **STOP THE PLAYBACK** (D-319). An early cancel: the cooldown, which would have been the history's
 * length from the playback's end, is cut **linearly by the fraction unwatched** and runs from now.
 * From anybody else, or after the playback has played out, nothing changes. Emits nothing either way.
 */
internal fun timelapseStopped(state: GameState, event: Event.TimelapseStopped): Reduction<GameState, Effect> {
    val playing = state.timelapse.playingAt(event.at)
    val mine = playing != null && playing.reader.index == event.actor.index
    val next = if (mine) cancelled(state, event.at) else state
    return Reduction(next, emptyList())
}

/**
 * **The ways a playback ends that are not STOP, and a new shift's floor** (D-319), after every event.
 *
 * - A new shift (the arming, or a meeting's end): the history is wiped and the floor moves to it,
 *   and the cooldown is cleared, because it equals the new history, which is none.
 * - Played out: the playback is dropped; the cooldown it wrote at its start stands.
 * - A meeting called: the playback ends and it is **not an early cancel** (the owner's ruling). The
 *   meeting's end clears the cooldown anyway.
 * - The reader no longer holds the station (left, out, or the connection gone; D-203): an early
 *   cancel at this event, as STOP is.
 */
internal fun settled(before: GameState, after: GameState, at: Tick): GameState {
    if (after.armed && after.shiftStartedAt != before.shiftStartedAt) {
        return after.copy(timelapse = Timelapse.startingAt(after.shiftStartedAt))
    }
    val playing = after.timelapse.playback ?: return after
    return when {
        at >= playing.endsAt -> after.copy(timelapse = after.timelapse.copy(playback = null))
        after.meeting != null -> after.copy(timelapse = after.timelapse.copy(playback = null))
        after.readerHolding()?.index != playing.reader.index -> cancelled(after, at)
        else -> after
    }
}

/** **An early cancel at [at]**: the cooldown is the history's length times the fraction watched, from now. */
private fun cancelled(state: GameState, at: Tick): GameState {
    val held = state.timelapse
    val playing = held.playback ?: return state
    val total = playing.endsAt.step - playing.startedAt.step
    val watched = (at.step - playing.startedAt.step).coerceIn(0, total)
    val cooldown = if (total == 0L) 0L else historyLength(playing.frames.size) * watched / total
    return state.copy(timelapse = held.copy(playback = null, readyAt = Tick(at.step + cooldown)))
}

/** **The history's length in ticks**: a frame every two seconds. The cooldown, by the standing rule. */
private fun historyLength(frames: Int): Long = frames * Timelapse.FRAME_EVERY
