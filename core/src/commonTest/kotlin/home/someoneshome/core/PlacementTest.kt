package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.CellRect
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.PaintResult
import home.someoneshome.model.Placement
import home.someoneshome.model.PlacementTiming
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Room
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.toView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **O4: the house builds D-136's placements from what it already holds** (the occupancy spec).
 *
 * Every test below runs the real pipeline: a packed upload, the contact resolver, [resolvePlacements],
 * and the admission gate. A sensor is a seat that scanned a card of its own work order (the presence
 * plane says its window is open); heard seats are the UWB distances its upload carries (D-301), the
 * only thing that places a seat in every round (D-308).
 *
 * The home: ten rooms, two cards each (`placed`), on a plan of two storeys. GROUND holds ROOM 0 to
 * ROOM 4 in a row, each touching the next; UPPER holds ROOM 5 to ROOM 8 in a row and ROOM 9 alone,
 * touching nothing. The T card is in ROOM 0.
 */
class PlacementTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val cards = (0 until 20).map { MarkerId("m$it") }
    private val station = MarkerId("T")
    private val w = ContactWindow(9_000L)
    private val seed = 20261117L

    private val plan: HousePlan = run {
        var p = HousePlan.EMPTY.withFloor("GROUND").withFloor("UPPER")
        fun paint(floor: String, name: String, x: Int) {
            p = (p.paint(floor, PlanRoom(Room(name), listOf(CellRect(x, 0, 2, 2)))) as PaintResult.Painted).plan
        }
        for (i in 0..4) paint("GROUND", "ROOM $i", 2 * i)
        for (i in 5..8) paint("UPPER", "ROOM $i", 2 * (i - 5))
        paint("UPPER", "ROOM 9", 20)
        p
    }

    private fun armed(insiders: List<Seat> = listOf(Seat(1))): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = seed, seats = seats, insiders = insiders, markers = placed(cards),
            terminal = station, plan = plan.toView(), terminalRoom = "ROOM 0",
        ),
    ).state

    /** The card that opens [seat]'s first Subroutine, so a scan of it opens a window. */
    private fun anchor(state: GameState, seat: Seat): MarkerId = state.workOrderFor(seat)!!.entries.first().anchor!!

    private fun roomOf(state: GameState, card: MarkerId): String = state.destinationOf(card)!!.room

    /** The first seat after [after] whose anchor is in none of [rooms], so two rooms are two rooms. */
    private fun seatOutside(state: GameState, rooms: Set<String>, after: Seat = Seat(0)): Seat =
        seats.first { it.index > after.index && roomOf(state, anchor(state, it)) !in rooms }

    private fun admitted(state: GameState, event: Event): GameState =
        (admit(state, event) as? home.someoneshome.core.Admission.Admitted)?.reduction?.state
            ?: error("refused: $event -> ${admit(state, event)}")

    /** [seat] scans its own anchor at [at]: the window opens there. */
    private fun perform(state: GameState, seat: Seat, at: Long): GameState =
        admitted(state, Event.MarkerScanned(Tick(at), seat, anchor(state, seat))).also {
            assertTrue(it.presenceFor(seat)!!.open, "seat ${seat.index}'s anchor opened no window")
        }

    private var uploadCount = 0L

    /**
     * [sensor]'s upload, measuring each `seat to cm`, put through the house's own pipeline. Its
     * Bluetooth slots hear every one of them at −20 dBm too, which places nobody (D-308).
     */
    private fun sensedBy(state: GameState, at: Long, sensor: Seat, vararg heard: Pair<Int, Int>): Event.Sensed? {
        val slots = heard.map { (seat, _) -> Sighting(ContactTokens.of(contactSeedFor(seed, Seat(seat)), w), -20, w) }
        val packed = SightingUpload.pack(contactSeedFor(seed, sensor), w, uploadCount++, slots)
        val upload = ContactUpload(sensor, SightingUpload.unpack(packed)!!, heard.map { (seat, cm) -> ContactDistance(Seat(seat), cm) })
        resolveContacts(ContactMemory.EMPTY, Tick(at), w, listOf(upload), state)
        return resolvePlacements(Tick(at), upload, state)
    }

    private fun sense(state: GameState, at: Long, sensor: Seat, vararg heard: Pair<Int, Int>): GameState =
        admitted(state, assertNotNull(sensedBy(state, at, sensor, *heard), "seat ${sensor.index} is not a sensor"))

    /** **GUARD: a performer at a card measures seat 4 at 1 m, and seat 4 is placed, Inferred, in that room.** */
    @Test
    fun `a performer's upload places the seats it heard in its room`() {
        val state = perform(armed(), Seat(0), at = 100)
        val room = roomOf(state, anchor(state, Seat(0)))

        val sensed = assertNotNull(sensedBy(state, 110, Seat(0), 4 to 100, 5 to 300))
        assertEquals(Event.Sensed(Tick(110), Seat(0), room, listOf(Seat(4))), sensed, "3 m is past the room's reach; 1 m is in it")

        val after = admitted(state, sensed)
        assertEquals(Placement.Inferred(Seat(4), room, Tick(110)), after.placementOf(Seat(4), Tick(111)))
        assertEquals(room, after.placedRoomOf(Seat(4), Tick(111)))
        assertNull(after.placementOf(Seat(5), Tick(111)), "a seat measured past the room's reach was placed")
        // The sensor's room is observed, its own performer and the heard seat both counted.
        val seen = assertNotNull(after.observationOf(room))
        assertEquals(2, seen.trueCount)
        // And nothing at all is sent for it, to anybody.
        assertEquals(emptyList(), (admit(state, sensed) as Admission.Admitted).reduction.effects)
        // The presence plane is untouched: a sighting is never written into it (D-136).
        assertNull(after.presenceFor(Seat(4)), "a sighting was written into the presence plane")
    }

    /**
     * **GUARD (D-136): a sighting never moves a seat whose window is open, in both orders.** Seat 2 is
     * mid-Subroutine at its own card; seat 0, performing elsewhere, hears it through a wall.
     */
    @Test
    fun `a sighting never relocates a performer whatever the order`() {
        val base = perform(armed(), Seat(0), at = 100)
        val here = roomOf(base, anchor(base, Seat(0)))
        val performer = seatOutside(base, setOf(here))
        val there = roomOf(base, anchor(base, performer))

        // Scan first, then heard.
        val scanFirst = sense(perform(base, performer, at = 105), 110, Seat(0), performer.index to 80)
        // Heard first, then scan.
        val heardFirst = perform(sense(base, 102, Seat(0), performer.index to 80), performer, at = 105)
            .let { sense(it, 110, Seat(0), performer.index to 80) }

        for ((label, state) in listOf("scan, then heard" to scanFirst, "heard, then scan" to heardFirst)) {
            assertEquals(here, state.sightingOf(performer)?.room, "$label: the sighting was not even recorded")
            for (t in listOf(111L, 111L + PlacementTiming.EXPIRY * 4)) {
                val placed = state.placementOf(performer, Tick(t))
                assertIs<Placement.Known>(placed, "$label, at $t: an inference relocated a performer")
                assertEquals(there, state.placedRoomOf(performer, Tick(t)), "$label, at $t")
            }
        }
    }

    /** **GUARD (D-155): a Revoked or Restrained seat is never placed or counted, even measured at 5 cm.** */
    @Test
    fun `an out seat is never placed even measured at touch reach`() {
        val base = perform(armed(), Seat(0), at = 100)
        val room = roomOf(base, anchor(base, Seat(0)))
        val out = base.copy(revoked = listOf(Seat(3)), restrained = listOf(Seat(5)))

        val sensed = assertNotNull(sensedBy(out, 110, Seat(0), 3 to 5, 5 to 5, 4 to 100))
        assertEquals(listOf(Seat(4)), sensed.heard, "an out seat was heard into a room")
        // And the rules refuse to place one even if a Sensed names it (a forged or replayed row).
        val forged = admitted(out, Event.Sensed(Tick(110), Seat(0), room, listOf(Seat(3), Seat(4), Seat(5))))
        assertNull(forged.placementOf(Seat(3), Tick(111)))
        assertNull(forged.placementOf(Seat(5), Tick(111)))
        assertEquals(2, forged.observationOf(room)!!.trueCount, "an out seat was counted")
    }

    /** **GUARD (R8, D-155): an out seat's upload places nobody**, window open or not. */
    @Test
    fun `an out seat senses nothing`() {
        val base = perform(armed(), Seat(0), at = 100)
        assertNotNull(sensedBy(base, 110, Seat(0), 4 to 80), "the living performer should sense")
        assertNull(sensedBy(base.copy(revoked = listOf(Seat(0))), 110, Seat(0), 4 to 80), "a Revoked seat sensed")
        assertNull(sensedBy(base.copy(restrained = listOf(Seat(0))), 110, Seat(0), 4 to 80), "a Restrained seat sensed")
        // And a living seat that never scanned anything is no sensor.
        assertNull(sensedBy(base, 110, Seat(6), 4 to 80), "a seat placed nowhere sensed")
    }

    /** **GUARD: nothing is sensed or placed at a meeting, and the map is wiped when it ends.** */
    @Test
    fun `nothing is placed at a meeting and the map is wiped at its end`() {
        val before = sense(perform(armed(), Seat(0), at = 100), 110, Seat(0), 4 to 80)
        assertNotNull(before.placementOf(Seat(4), Tick(111)))
        assertTrue(before.observations.isNotEmpty())

        val meeting = before.copy(meeting = Meeting.called(Seat(6), MeetingTrigger.MeetingCard))
        assertNull(sensedBy(meeting, 120, Seat(0), 4 to 80), "a Sensed was minted at a meeting")
        assertEquals(
            RefusalReason.MeetingAlreadyRunning,
            (admit(meeting, Event.Sensed(Tick(120), Seat(0), "ROOM 0", listOf(Seat(4)))) as Admission.Refused).reason,
        )
        for (seat in seats) assertNull(meeting.placementOf(seat, Tick(121)), "seat ${seat.index} placed at a meeting")

        val after = reduce(meeting, Event.MeetingClosed(Tick(400))).state
        assertEquals(Tick(400), after.shiftStartedAt)
        assertEquals(emptyList(), after.sightings, "the sightings outlived the meeting")
        assertEquals(emptyList(), after.observations, "the observations outlived the meeting")
        for (seat in seats) assertNull(after.placementOf(seat, Tick(401)), "seat ${seat.index} kept a pre-meeting place")
        // And a window left open across the meeting is not a sensor after it.
        assertNull(sensedBy(after, 410, Seat(0), 4 to 80), "a pre-meeting window sensed after the meeting")
    }

    /** **GUARD: sightings and closed knowledge expire; a check-in holds for ten seconds.** */
    @Test
    fun `placements expire and a check-in holds`() {
        val sensor = seatOutside(armed(), setOf("ROOM 0"), after = Seat(-1))
        check(sensor.index != 4) { "the fixture's sensor is the seat it hears" }
        val start = perform(armed(), sensor, at = 100)
        val heard = sense(start, 110, sensor, 4 to 80)
        assertNotNull(heard.placementOf(Seat(4), Tick(110 + PlacementTiming.EXPIRY - 1)))
        assertNull(heard.placementOf(Seat(4), Tick(110 + PlacementTiming.EXPIRY)), "a sighting never expired")

        // Seat 4 checks in at a card that opens nothing for it (the T card): knowledge, no window.
        val checked = admitted(heard, Event.MarkerScanned(Tick(200), Seat(4), station))
        assertEquals("ROOM 0", checked.placedRoomOf(Seat(4), Tick(201)), "the T card's room did not resolve")
        // Inside the hold, a sighting elsewhere does not move it.
        val elsewhere = roomOf(start, anchor(start, sensor))
        val rescan = perform(checked, sensor, at = 202)
        val held = sense(rescan, 205, sensor, 4 to 80)
        assertEquals("ROOM 0", held.placedRoomOf(Seat(4), Tick(206)), "a sighting relocated a check-in inside its hold")
        // Past the hold, the newer sighting wins.
        assertEquals(elsewhere, held.placedRoomOf(Seat(4), Tick(200 + PlacementTiming.KNOWN_HOLD)))
        // And the closed check-in itself expires.
        assertNull(checked.placedRoomOf(Seat(4), Tick(200 + PlacementTiming.EXPIRY)), "a check-in never expired")
    }

    /**
     * **GUARD (the spec's finding): `placementOf` counts work in progress, and only work in
     * progress lasts.** The old function placed a seat at the last card it ever read for the rest of
     * the round, open or shut; it could not tell a seat mid-Subroutine from one that left ten
     * minutes ago.
     */
    @Test
    fun `placementOf counts work in progress and lets finished work expire`() {
        val working = perform(armed(), Seat(0), at = 100)
        val card = anchor(working, Seat(0))
        val tenMinutes = 100L + 600L * Balance.TICKS_PER_SECOND
        assertEquals(
            Placement.Known(Seat(0), card), working.placementOf(Seat(0), Tick(tenMinutes)),
            "a seat ten minutes into an open Subroutine was not placed at it",
        )
        // A newer sighting elsewhere still does not move it.
        val other = perform(working, Seat(2), at = tenMinutes - 20)
        val moved = sense(other, tenMinutes - 10, Seat(2), 0 to 80)
        assertEquals(Placement.Known(Seat(0), card), moved.placementOf(Seat(0), Tick(tenMinutes)))

        // The window closes: the place stands for the expiry and then the seat is unplaced.
        val stopped = admitted(working, Event.PerformanceEnded(Tick(200), Seat(0)))
        assertEquals(Placement.Known(Seat(0), card), stopped.placementOf(Seat(0), Tick(200 + PlacementTiming.EXPIRY - 1)))
        assertNull(stopped.placementOf(Seat(0), Tick(200 + PlacementTiming.EXPIRY)), "finished work stayed placed")
    }

    /**
     * **GUARD (rule 4): a capture's error is rolled once, stored, and never rolled again.** A later
     * capture elsewhere leaves it alone; the same events from scratch capture the same values; asked
     * twice, it answers the same band.
     */
    @Test
    fun `a capture's error is rolled once and stored`() {
        val second = armed().let { seatOutside(it, setOf(roomOf(it, anchor(it, Seat(0))))) }
        fun walk(): GameState {
            val a = perform(perform(armed(), Seat(0), at = 100), second, at = 101)
            val first = sense(a, 110, Seat(0), 4 to 80, 5 to 90)
            return sense(first, 130, second, 6 to 80)
        }
        val one = walk()
        val two = walk()
        val room = roomOf(one, anchor(one, Seat(0)))
        val kept = assertNotNull(one.observationOf(room))
        assertEquals(1L, kept.id, "the first capture was re-captured by the second")
        assertEquals(Tick(110), kept.seenAt)
        assertEquals(2, one.observations.size)
        assertEquals(
            one.observations.map { listOf(it.id, it.room, it.seenAt, it.trueCount, it.reportedCount, it.ageJitter) },
            two.observations.map { listOf(it.id, it.room, it.seenAt, it.trueCount, it.reportedCount, it.ageJitter) },
            "the same events captured different values",
        )
        assertEquals(kept.stalenessAt(Tick(500)), kept.stalenessAt(Tick(500)))
        assertTrue(kept.ageJitter in -SensingGate.AGE_JITTER..SensingGate.AGE_JITTER)
    }

    /**
     * **GUARD (`gdd.md:487-493`): the error is plausible, not random.** Over three thousand captures
     * a room with people in it is under-counted at about the missed rate and never more than by one;
     * a phantom appears only in a room beside an occupied one; an isolated empty room never errs.
     */
    @Test
    fun `the error leans to missing occupants and only bleeds into the next room`() {
        val base = perform(armed(), Seat(0), at = 100)
        val room = roomOf(base, anchor(base, Seat(0)))
        val occupied = admitted(base, Event.Sensed(Tick(110), Seat(0), room, listOf(Seat(4), Seat(5))))
        val index = occupied.plan.indexOf(room)
        val beside = occupied.plan.rooms[occupied.plan.adjacency[index].first()].name
        val alone = "ROOM 9"
        check(occupied.plan.adjacency[occupied.plan.indexOf(alone)].isEmpty()) { "ROOM 9 is not isolated" }

        val n = 3_000
        var missed = 0
        var phantomsBeside = 0
        for (i in 0 until n) {
            val at = occupied.copy(nextObservation = 1_000L + i)
            val inRoom = capture(at, room, Tick(111))
            assertEquals(3, inRoom.trueCount)
            assertTrue(inRoom.reportedCount in 2..4, "an error larger than one occupant: ${inRoom.reportedCount}")
            if (inRoom.reportedCount < inRoom.trueCount) missed++
            val next = capture(at, beside, Tick(111))
            assertEquals(0, next.trueCount)
            assertTrue(next.reportedCount in 0..1, "a room beside an occupied one went negative or doubled")
            if (next.reportedCount == 1) phantomsBeside++
            val far = capture(at, alone, Tick(111))
            assertEquals(0, far.reportedCount, "a phantom in a room that touches nobody: capture ${1_000 + i}")
        }
        println("O4 ERROR — $n CAPTURES · MISSED IN AN OCCUPIED ROOM $missed · PHANTOM BESIDE IT $phantomsBeside")
        assertTrue(missed in (n * 0.10).toInt()..(n * 0.20).toInt(), "missed $missed of $n, the rate is 15 %")
        assertTrue(phantomsBeside in (n * 0.025).toInt()..(n * 0.075).toInt(), "phantoms $phantomsBeside of $n, the rate is 5 %")
    }

    /** **GUARD: the sensing stream and the error do not move with a role.** */
    @Test
    fun `a role swap senses and rolls byte-identically`() {
        fun walk(insiders: List<Seat>): Pair<List<Event.Sensed?>, GameState> {
            val a = perform(perform(armed(insiders), Seat(0), at = 100), Seat(1), at = 101)
            uploadCount = 0
            val e1 = sensedBy(a, 110, Seat(0), 1 to 80, 4 to 100)
            val b = admitted(a, e1!!)
            val e2 = sensedBy(b, 120, Seat(1), 0 to 80, 3 to 140)
            return listOf(e1, e2) to admitted(b, e2!!)
        }
        val (events, state) = walk(listOf(Seat(1)))
        for (other in listOf(listOf(Seat(0)), listOf(Seat(4)), listOf(Seat(0), Seat(1)))) {
            val (otherEvents, otherState) = walk(other)
            assertEquals(events, otherEvents, "insiders $other: the Sensed stream moved with a role")
            assertEquals(
                state.observations.map { listOf(it.room, it.trueCount, it.reportedCount, it.ageJitter) },
                otherState.observations.map { listOf(it.room, it.trueCount, it.reportedCount, it.ageJitter) },
                "insiders $other: the error moved with a role",
            )
            assertEquals(state.sightings, otherState.sightings)
        }
    }

    /**
     * **GUARD (D-301, D-308): a performer places the seats it measured within 1.5 m, and its
     * Bluetooth places nobody.** Seat 4 at 150 cm is placed; seat 5 at 151 cm is not; seat 6, heard at
     * −40 dBm and never measured, is not.
     */
    @Test
    fun `a performer places what it measured within the room's reach and Bluetooth places nobody`() {
        val state = perform(armed(), Seat(0), at = 100)
        val room = roomOf(state, anchor(state, Seat(0)))
        val slots = listOf(Sighting(ContactTokens.of(contactSeedFor(seed, Seat(6)), w), -40, w))
        val upload = ContactUpload(
            Seat(0),
            SightingUpload.unpack(SightingUpload.pack(contactSeedFor(seed, Seat(0)), w, uploadCount++, slots))!!,
            listOf(ContactDistance(Seat(5), 151), ContactDistance(Seat(4), 150)),
        )
        resolveContacts(ContactMemory.EMPTY, Tick(110), w, listOf(upload), state)
        val sensed = assertNotNull(resolvePlacements(Tick(110), upload, state))
        assertEquals(Event.Sensed(Tick(110), Seat(0), room, listOf(Seat(4))), sensed, "the room's reach by distance is not 1.5 m, or Bluetooth placed somebody")
        // The deliberate error is the same capture: an observation with its error rolled once.
        val after = admitted(state, sensed)
        assertEquals(room, after.placedRoomOf(Seat(4), Tick(111)))
        assertNotNull(after.observationOf(room))
    }
}
