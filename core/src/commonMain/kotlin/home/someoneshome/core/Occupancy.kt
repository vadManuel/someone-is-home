package home.someoneshome.core

import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.ObservationView
import home.someoneshome.model.RoomKind
import home.someoneshome.model.clientClassOf
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.Timelapse
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.OccupancyWire
import home.someoneshome.model.Balance
import home.someoneshome.model.toView

/*
 * **What a phone may draw of the map this beat: the Terminal's counts, true occupancy, or nothing**
 * (D-316; the owner's 2026-11-21 ruling, *the Terminal and the couch must show the home's map with
 * room counts*).
 *
 * The house answers every upload once a beat (D-263) and every answer carries one sealed occupancy
 * field of one width (`OccupancyWire`). This is what fills it, for one seat at one moment, read
 * off the round as the rules left it. It mints nothing and changes nothing, so it is not a
 * reduction and the recording holds no row for it: what a phone was shown at a tick is this
 * function of the recorded state at that tick (rule 4), which is how a replay explains it.
 *
 * ### Who gets what, and the schema is still the gate
 *
 * - **The seat holding the station's lock** (`GameState.readerHolding`, D-199, D-203) gets the
 *   Terminal's reading, if `EmitSchema` permits `TERMINAL_READING` to its class. Both living
 *   classes have the row, so an Insider reader and a Resident reader get the same beat of the same
 *   state, byte for byte: nothing here reads a role. Releasing the station, any of D-203's four
 *   ways, makes the next beat nothing.
 * - **A seat on the couch**, out and through a meeting since (never a newly Revoked phone,
 *   `gdd.md:537`; `GameState.newlyRevoked`), gets true occupancy, if `EmitSchema` permits
 *   `TRUE_OCCUPANCY` to its class. Only the out classes have the row.
 * - **Everybody else, and everybody while a meeting runs**, gets nothing. The map is never read at
 *   a meeting (`gdd.md:484`, D-203), and nobody is placed during one.
 *
 * ### What each one counts
 *
 * The reader's counts are the house's captures (D-272), each read through `Observation.toView`,
 * the one bridge that cannot carry a true count: the error rolled once and stored, and the band
 * at this moment from the stored jitter. A room nobody has sensed in this shift is 0 in the cold
 * band, because a hole and an empty room are the same fact (`gdd.md:524`). A Revoked seat is in no
 * count, because the capture counts living seats only (D-155).
 *
 * True occupancy is the house's own placements at this moment (`GameState.placedRoomOf`, D-136):
 * living seats only, no injected error and no bands (`gdd.md:546`), and with D-136's bleed, holes
 * and expiry, which the house cannot see past. Stairs are drawn and never counted (`gdd.md:485`).
 *
 * **Static changes neither** (D-186): tracking is untouched, so the reader's beat keeps carrying the
 * real counts under the jam, and the phone draws the noise over them; the couch is exempt.
 *
 * ### The timelapse (D-319)
 *
 * While the reader's playback runs, the reader is sent **its frames instead of the live view**
 * (*watching blinds you to the present*, the GDD), [OccupancyWire.LAPSE_FRAMES] a beat from the
 * frame showing now, if `EmitSchema` permits `TIMELAPSE_PLAYBACK` to its class. Otherwise a reading
 * also says when the station may next play and how much history it holds. Only the reader is sent
 * either: the playback is the reader's, and the reader holds the station.
 */
fun occupancyBeat(state: GameState, seat: Seat, at: Tick): OccupancyBeat {
    val running = state.armed && !state.ended && state.meeting == null && state.isSeated(seat)
    val clientClass = state.clientClassOf(seat)
    val reads = running && state.readerHolding()?.index == seat.index &&
        EmitSchema.permits(EmitSchema.TERMINAL_READING, clientClass)
    val onTheCouch = running && state.isOut(seat) && state.newlyRevoked.none { it.index == seat.index } &&
        EmitSchema.permits(EmitSchema.TRUE_OCCUPANCY, clientClass)
    val playing = state.timelapse.playingAt(at)?.takeIf { reads && it.reader.index == seat.index }
    return when {
        // **Watching the past blinds you to the present** (the GDD): a reader whose playback runs is
        // sent its frames and never the live view, and with no `TIMELAPSE_PLAYBACK` row it is sent
        // nothing at all, the fail-closed direction (rule 2).
        playing != null ->
            if (EmitSchema.permits(EmitSchema.TIMELAPSE_PLAYBACK, clientClass)) lapseFrames(state, playing, at) else OccupancyBeat.Nothing
        reads -> terminalReading(state, at)
        onTheCouch -> trueOccupancy(state, at)
        else -> OccupancyBeat.Nothing
    }
}

/**
 * **The reader's playback this beat** (D-319): the frame showing at [at] and the next ones, up to
 * [OccupancyWire.LAPSE_FRAMES], oldest to newest. The phone shows them a third of a second apart.
 */
internal fun lapseFrames(state: GameState, playing: home.someoneshome.model.Playback, at: Tick): OccupancyBeat.Lapse {
    val frames = playing.frames
    val first = playing.frameAt(at).coerceAtMost(frames.size - 1)
    val last = minOf(frames.size, first + OccupancyWire.LAPSE_FRAMES)
    val counts = ArrayList<List<Int>>(last - first)
    val jammed = ArrayList<Boolean>(last - first)
    for (i in first until last) {
        counts += frames[i].counts
        jammed += frames[i].jammed
    }
    val here = state.terminalRoom?.let { state.plan.indexOf(it) } ?: -1
    return OccupancyBeat.lapse(here, frames.size, first, counts, jammed)
}

/**
 * **When the station may next play, in whole seconds, and the history it holds now** (D-319). Ready
 * is the station's cooldown run out **and** history to play; while there is none, the wait is for
 * its first frame.
 */
internal fun timelapseReadiness(state: GameState, at: Tick): Pair<Int, Int> {
    val held = Timelapse.framesHeld(state.shiftStartedAt, at)
    val cooling = state.timelapse.readyAt.step - at.step
    // The first frame is the mark two seconds into the shift, held from the tick after it.
    val firstFrame = if (held > 0) 0L else state.shiftStartedAt.step + Timelapse.FRAME_EVERY + 1 - at.step
    val wait = maxOf(cooling, firstFrame, 0L)
    return Balance.wholeSeconds(wait) to held
}

/** **The reader's beat**: per room in plan order, the latest capture this shift as its view, or a hole. */
internal fun terminalReading(state: GameState, at: Tick): OccupancyBeat.Terminal {
    val plan = state.plan
    val views = ArrayList<ObservationView?>(plan.rooms.size)
    for (room in plan.rooms) {
        val held = if (room.kind == RoomKind.Stairs) null else state.observationOf(room.name)
        views += held?.takeIf { it.seenAt >= state.shiftStartedAt }?.toView(at)
    }
    val here = state.terminalRoom?.let { plan.indexOf(it) } ?: -1
    val (readyIn, history) = timelapseReadiness(state, at)
    return OccupancyBeat.terminal(here, views, readyIn, history)
}

/** **The couch's beat**: per room in plan order, the living seats the house places there now. */
internal fun trueOccupancy(state: GameState, at: Tick): OccupancyBeat.Outside {
    val plan = state.plan
    val counts = IntArray(plan.rooms.size)
    for (seat in state.seats) {
        val room = state.placedRoomOf(seat, at) ?: continue
        val index = plan.indexOf(room)
        if (index >= 0 && plan.rooms[index].kind != RoomKind.Stairs) counts[index]++
    }
    return OccupancyBeat.outside(counts.toList())
}
