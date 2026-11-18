package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Observation
import home.someoneshome.model.Placement
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick

/*
 * **The spectator map's input: D-136's placements, built by the house from what it already holds**
 * (O4 of the occupancy spec).
 *
 * The host has the plan (`GameState.plan`, O2), knows where every performer is (the presence plane)
 * and is handed one sighting upload a second by every living phone (D-212). D-212 itself says these
 * uploads are the spectator map's input. Until this file they reached the contact resolver alone.
 *
 * ### Two halves, as the contacts have
 *
 * [resolvePlacements] sits above the rules, beside `resolveContacts`, for its reason: turning a
 * token back into a seat needs the token book, which `reduce` has never held. It mints one
 * [Event.Sensed] for an upload whose sender is a sensor and nothing for any other upload. The rules
 * ([sensed]) then write a [Placement.Inferred] for every seat it heard and capture one
 * [Observation] of the sensor's room, its error rolled once and stored.
 *
 * ### Nothing here reaches a phone, and nothing here reads a role
 *
 * [sensed] returns no effect on any path, so the emit schema is asked nothing and the allowlist has
 * nothing to permit: **the house knows more and says exactly what it said before.** The functions'
 * inputs carry no role and no armed state, and the error is keyed on the capture's own number under
 * [SeedPurpose.Occupancy], so a role-swapped round mints and rolls a byte-identical stream. An
 * Insider's fake performance opens the presence plane exactly as a real one does (rule 8), so an
 * Insider is a sensor exactly as often.
 *
 * ### The unreliability is kept on purpose
 *
 * Bleed into the next room at the RSSI floor, holes where nobody performs, placements that expire,
 * and an injected error on every count: D-136 says *a map the out can read perfectly turns the
 * couch into an oracle*, and misreading each other is this game's material. **None of it is to be
 * tuned away.** Every number is provisional and named in [SensingGate] for playtest.
 */

/**
 * **The spectator map's tunables, in one place** (O4). ⚠️ **Every one is provisional**: the
 * occupancy spec's Q5 and `gdd.md` A-7/A-8 leave them to playtest, and only the RSSI floor needs a
 * phone before it means anything (O8). The placement lifetimes are `PlacementTiming`'s, in `model`,
 * because `GameState.placementOf` reads them.
 */
object SensingGate {

    /**
     * ⚠️ **Provisional, −75 dBm: the room-presence floor.** Looser than the contact floor (−40,
     * D-233) on purpose: this is *in the same room*, not *touching*. `gdd.md:791`: *BLE RSSI is a
     * proximity gate, never a map*, and the next room's bleed through a wall is accepted (D-136).
     * Uncalibrated: nothing has measured a room on two phones yet.
     */
    const val ROOM_RSSI_FLOOR: Int = -75

    /**
     * ⚠️ **Provisional, 3 s: the sweep on scan** (`gdd.md:776`). A seat is a sensor while its window
     * is open, and for this long after the house last placed it at a card, window or none.
     */
    const val SCAN_SWEEP: Long = 3L * Balance.TICKS_PER_SECOND

    /**
     * ⚠️ **Provisional, 15 %: a missed occupant**, out of a thousand. Applied only to a room with
     * somebody in it, so the error leans toward under-counting occupied rooms (F-007, `gdd.md:992`).
     * Load-bearing: it is what gives a Revoked phone's invisibility deniability (`gdd.md:489`).
     */
    const val MISSED_PER_MILLE: Int = 150

    /**
     * ⚠️ **Provisional, 5 %: a phantom occupant**, out of a thousand. Only where a room on the same
     * storey that shares an edge with this one has somebody in it: *a phantom in an adjacent room
     * reads as radio physics* (`gdd.md:491`). Never in an isolated room, so never random.
     */
    const val PHANTOM_PER_MILLE: Int = 50

    /** ⚠️ **Provisional, ±5 s: the age jitter**, rolled once per capture (`gdd.md:484`). */
    const val AGE_JITTER: Long = 5L * Balance.TICKS_PER_SECOND

    /** Whether [rssi] is a room-range reading. Zero and above is no reading at all (CoreBluetooth's 127). */
    fun inRoom(rssi: Int): Boolean = rssi in ROOM_RSSI_FLOOR..ContactGate.RSSI_CEILING
}

/**
 * **One upload, judged as the spectator map's input: a [Event.Sensed] if its sender is a sensor,
 * or null** (D-136, D-212).
 *
 * [book] is the contact resolver's token book for this arrival (`ContactMemory.book`), so a token
 * is turned back into a seat once per window and by lookup, never by HMAC per slot. [at] is the
 * arrival's tick, the same one the contacts were judged on.
 *
 * **A sensor is a living seat the house has placed at a card this shift** that is performing (a
 * window open or a Subroutine armed, D-136) or within [SensingGate.SCAN_SWEEP] of the scan or close
 * that put it there (`gdd.md:776`). Nothing is sensed while a meeting runs, before arming or after
 * the end, from a seat that is out (D-155; and an out phone uploads filler anyway, R8), or at a card
 * the house's own map cannot put in a room.
 *
 * **Heard** is every living seat of the round, other than the sensor, whose token the upload carried
 * at [SensingGate.ROOM_RSSI_FLOOR] or stronger, once each, in seat order. Filler and strangers
 * resolve to nobody, as they do for contacts.
 *
 * **Allocation** (rule 7): an upload from a seat that is not a sensor costs one presence lookup and
 * allocates nothing; a sensor's costs one small list and the event.
 */
fun resolvePlacements(at: Tick, book: ContactBook?, upload: ContactUpload, state: GameState): Event.Sensed? {
    if (!state.armed || state.ended || state.meeting != null || book == null) return null
    val sensor = upload.seat
    if (state.seats.none { it.index == sensor.index } || state.isOut(sensor)) return null
    val row = state.presenceFor(sensor) ?: return null
    val card = row.at ?: return null
    if (row.since < state.shiftStartedAt) return null
    val working = row.open || state.openSubroutineFor(sensor)?.armed == true
    if (!working && at.step - row.since.step > SensingGate.SCAN_SWEEP) return null
    val room = state.roomOfCard(card) ?: return null

    val heard = ArrayList<Seat>(4)
    for (sighting in upload.sightings) {
        if (!SensingGate.inRoom(sighting.rssi)) continue
        val seat = book.seatOf[sighting.token] ?: continue
        if (seat.index == sensor.index || state.isOut(seat)) continue
        if (heard.none { it.index == seat.index }) heard += seat
    }
    heard.sortBy { it.index }
    return Event.Sensed(at, sensor, room, heard)
}

/**
 * **The rules' half: place every seat heard, and observe the sensor's room** (D-136, O4).
 *
 * Every living seat in [Event.Sensed.heard] gets a [Placement.Inferred] in the sensor's room,
 * replacing whatever sighting it had: *every other placement is a sighting and may be overwritten
 * by the next one.* **The presence plane is not touched**, so a seat mid-Subroutine in the next
 * room, heard through the wall, keeps its Known placement: `GameState.placementOf` orders the two,
 * and *inference never overrides knowledge*.
 *
 * Then one [Observation] of the room is captured ([capture]).
 *
 * **It emits nothing, on every path.** That is not rule 1 bending: nothing here is a client-visible
 * path, because nothing here reaches a client at all. A seat that is out is never placed and never
 * counted (D-155), which is a filter on state rather than a branch anybody could observe.
 */
internal fun sensed(state: GameState, event: Event.Sensed): Reduction<GameState, Effect> {
    val sightings = ArrayList(state.sightings)
    for (seat in event.heard) {
        val lawful = seat.index != event.sensor.index && !state.isOut(seat) &&
            state.seats.any { it.index == seat.index }
        if (!lawful) continue
        val row = Placement.Inferred(seat, event.room, event.at)
        val held = sightings.indexOfFirst { it.seat.index == seat.index }
        if (held >= 0) sightings[held] = row else sightings += row
    }
    sightings.sortBy { it.seat.index }
    val placed = state.copy(sightings = sightings)
    return Reduction(placed.withObservation(capture(placed, event.room, event.at)), emptyList())
}

/**
 * **One room, observed at [at], its error rolled ONCE and stored** (rule 4; `gdd.md:484`, `:487-493`).
 *
 * `trueCount` is the living seats `placementOf` puts in [room] at [at]: the sensor itself, everybody
 * heard there, and anybody the house already knew was there. One stream is drawn under
 * [SeedPurpose.Occupancy], keyed on the capture's own number, and both rolls and the jitter are
 * always drawn, whatever they decide, so the stream's use never depends on the room:
 *
 * - a **missed occupant** ([SensingGate.MISSED_PER_MILLE]) takes one off a room that has somebody in
 *   it, and only such a room;
 * - otherwise a **phantom** ([SensingGate.PHANTOM_PER_MILLE]) adds one, and only where a room that
 *   shares an edge with this one on the plan has somebody in it.
 *
 * Identical rates for both roles, because nothing here can see a role (`gdd.md:493`).
 */
internal fun capture(state: GameState, room: String, at: Tick): Observation {
    val id = state.nextObservation
    val draw = Draw(state.seed, SeedPurpose.Occupancy, id)
    val missRoll = draw.next(1_000)
    val phantomRoll = draw.next(1_000)
    val jitter = draw.next((2 * SensingGate.AGE_JITTER + 1).toInt()) - SensingGate.AGE_JITTER

    val rooms = state.seats.map { state.placedRoomOf(it, at) }
    val trueCount = rooms.count { it == room }
    val index = state.plan.indexOf(room)
    val besideSomebody = index >= 0 && state.plan.adjacency[index].any { n ->
        val name = state.plan.rooms[n].name
        rooms.any { it == name }
    }
    val reported = when {
        trueCount > 0 && missRoll < SensingGate.MISSED_PER_MILLE -> trueCount - 1
        besideSomebody && phantomRoll < SensingGate.PHANTOM_PER_MILLE -> trueCount + 1
        else -> trueCount
    }
    return Observation(id, room, at, trueCount, reported, jitter)
}
