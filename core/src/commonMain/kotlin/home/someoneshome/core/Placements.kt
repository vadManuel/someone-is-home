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
 * and is handed one upload a second by every phone (D-212). D-212 itself says these uploads are the
 * spectator map's input. Since D-308 what places a seat is the UWB distance the performer's phone
 * read of it (D-301), never a Bluetooth reading.
 *
 * ### Two halves, as the contacts have
 *
 * [resolvePlacements] sits above the rules, beside `resolveContacts`, for its reason: turning a
 * ranged pair back into a seat needs the house's UWB pairing, which `reduce` has never held. It
 * mints one [Event.Sensed] for an upload whose sender is a sensor and nothing for any other upload. The rules
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
 * Bleed into the next room at the room reach, holes where nobody performs, placements that expire,
 * and an injected error on every count: D-136 says *a map the out can read perfectly turns the
 * couch into an oracle*, and misreading each other is this game's material. **None of it is to be
 * tuned away.** Every number is provisional and named in [SensingGate] for playtest.
 */

/**
 * **The spectator map's tunables, in one place** (O4). ⚠️ **Every one is provisional**: the
 * occupancy spec's Q5 and `gdd.md` A-7/A-8 leave them to playtest, and only the room reach needs a
 * phone before it means anything (O8). The placement lifetimes are `PlacementTiming`'s, in `model`,
 * because `GameState.placementOf` reads them.
 */
object SensingGate {

    /**
     * ⚠️ **Provisional (overnight), 1.5 m: the room reach by UWB** (D-301; the U6 plan's starting
     * number, unit 3). A performer places a seat in its room when its phone read that seat this
     * near, in every round (D-308). A wall does not lengthen a UWB reading the way a body does, so
     * the next room's bleed is still there (U3 accepts it), and only seats this phone is ranging can
     * be placed, which is a performer's few nearest. The deliberate error (D-272) is unchanged.
     */
    const val ROOM_REACH_CM: Int = 150

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

    /** Whether [cm] is a room-range distance (D-301). */
    fun inRoomReach(cm: Int): Boolean = cm <= ROOM_REACH_CM
}

/**
 * **One upload, judged as the spectator map's input: a [Event.Sensed] if its sender is a sensor,
 * or null** (D-136, D-212).
 *
 * [at] is the arrival's tick, the same one the contacts were judged on.
 *
 * **A sensor is a living seat the house has placed at a card this shift** that is performing (a
 * window open or a Subroutine armed, D-136) or within [SensingGate.SCAN_SWEEP] of the scan or close
 * that put it there (`gdd.md:776`). Nothing is sensed while a meeting runs, before arming or after
 * the end, from a seat that is out (D-155; and an out phone uploads filler anyway, R8), or at a card
 * the house's own map cannot put in a room.
 *
 * **Heard** is every living seat of the round, other than the sensor, that the upload measured at
 * [SensingGate.ROOM_REACH_CM] or nearer ([ContactUpload.distances], D-301), once each, in seat
 * order. The Bluetooth slots place nobody (D-308).
 *
 * **Allocation** (rule 7): an upload from a seat that is not a sensor costs one presence lookup and
 * allocates nothing; a sensor's costs one small list and the event.
 */
fun resolvePlacements(at: Tick, upload: ContactUpload, state: GameState): Event.Sensed? {
    val sensor = upload.seat
    val room = sensingRoom(state, sensor, at) ?: return null

    val heard = ArrayList<Seat>(4)
    for (distance in upload.distances) {
        if (!SensingGate.inRoomReach(distance.cm)) continue
        val seat = distance.target
        if (!couldPlace(state, sensor, seat)) continue
        if (heard.none { it.index == seat.index }) heard += seat
    }
    heard.sortBy { it.index }
    return Event.Sensed(at, sensor, room, heard)
}

/**
 * **The room [sensor] senses in at [at], or null when it is not a sensor** ([resolvePlacements]'s
 * definition, one place): the round armed and not over, no meeting running, a living seat of the
 * round, placed by the house at a card this shift, performing (a window open or a Subroutine armed)
 * or within [SensingGate.SCAN_SWEEP] of the scan or close that put it there, at a card the house's
 * own map can put in a room.
 *
 * **The house's UWB pairing asks it too** (D-306): a pair is worth ranging for the map only while
 * one of its seats is a sensor. Allocates nothing.
 */
internal fun sensingRoom(state: GameState, sensor: Seat, at: Tick): String? {
    if (!state.armed || state.ended || state.meeting != null) return null
    if (!state.isSeated(sensor) || state.isOut(sensor)) return null
    val row = state.presenceFor(sensor) ?: return null
    val card = row.at ?: return null
    if (row.since < state.shiftStartedAt) return null
    val working = row.open || state.openSubroutineFor(sensor)?.armed == true
    if (!working && at.step - row.since.step > SensingGate.SCAN_SWEEP) return null
    return state.roomOfCard(card)
}

/**
 * **Whether [sensor] may place [seat]**: another seat of the round, and living (D-155). What
 * [resolvePlacements] asks of every seat an upload heard and [sensed] asks again of every seat it
 * places, and what the house's UWB pairing asks of a sensor's peers (D-306).
 */
internal fun couldPlace(state: GameState, sensor: Seat, seat: Seat): Boolean =
    seat.index != sensor.index && !state.isOut(seat) && state.isSeated(seat)

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
        if (!couldPlace(state, event.sensor, seat)) continue
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
