package home.someoneshome.core

import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.ObservationView
import home.someoneshome.model.RoomKind
import home.someoneshome.model.clientClassOf
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.OccupancyBeat
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
 */
fun occupancyBeat(state: GameState, seat: Seat, at: Tick): OccupancyBeat {
    val running = state.armed && !state.ended && state.meeting == null && state.isSeated(seat)
    val clientClass = state.clientClassOf(seat)
    val reads = running && state.readerHolding()?.index == seat.index &&
        EmitSchema.permits(EmitSchema.TERMINAL_READING, clientClass)
    val onTheCouch = running && state.isOut(seat) && state.newlyRevoked.none { it.index == seat.index } &&
        EmitSchema.permits(EmitSchema.TRUE_OCCUPANCY, clientClass)
    return when {
        reads -> terminalReading(state, at)
        onTheCouch -> trueOccupancy(state, at)
        else -> OccupancyBeat.Nothing
    }
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
    return OccupancyBeat.terminal(here, views)
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
