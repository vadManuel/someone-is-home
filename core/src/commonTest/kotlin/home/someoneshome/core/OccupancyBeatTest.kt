package home.someoneshome.core

import home.someoneshome.model.CellRect
import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.PaintResult
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Room
import home.someoneshome.model.RoomKind
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.OccupancyWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.toView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **D-316: what a phone may draw of the map this beat** (`occupancyBeat`).
 *
 * The owner's 2026-11-21 ruling: *the Terminal and the couch must show the home's map with room
 * counts.* Every test runs the house's own pipeline (a packed upload, the resolver,
 * `resolvePlacements`, the gate) and then asks the beat for each seat, as the house does once per
 * upload. The home: ROOM 0 to ROOM 4 in a row on GROUND, each touching the next, STAIRS beside ROOM
 * 4, and ROOM 5 alone on UPPER. The T card is in ROOM 0. Seat 2 reads the Terminal.
 */
class OccupancyBeatTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val cards = (0 until 12).map { MarkerId("m$it") }
    private val station = MarkerId("T")
    private val w = ContactWindow(9_000L)
    private val seed = 20261121L
    private val reader = Seat(2)

    private val plan: HousePlan = run {
        var p = HousePlan.EMPTY.withFloor("GROUND").withFloor("UPPER")
        fun paint(floor: String, room: Room, x: Int) {
            p = (p.paint(floor, PlanRoom(room, listOf(CellRect(x, 0, 2, 2)))) as PaintResult.Painted).plan
        }
        for (i in 0..4) paint("GROUND", Room("ROOM $i"), 2 * i)
        paint("GROUND", Room("STAIRS", RoomKind.Stairs), 10)
        paint("UPPER", Room("ROOM 5"), 0)
        p
    }

    private fun armed(insiders: List<Seat> = listOf(Seat(1))): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(
            Tick(0), seed = seed, seats = seats, insiders = insiders, markers = placed(cards),
            terminal = station, plan = plan.toView(), terminalRoom = "ROOM 0",
        ),
    ).state

    private fun admitted(state: GameState, event: Event): GameState =
        (admit(state, event) as? Admission.Admitted)?.reduction?.state ?: error("refused: $event -> ${admit(state, event)}")

    private fun anchor(state: GameState, seat: Seat): MarkerId = state.workOrderFor(seat)!!.entries.first().anchor!!

    private fun roomOf(state: GameState, seat: Seat): String = state.destinationOf(anchor(state, seat))!!.room

    private var uploads = 0L

    /** [sensor] performs at its anchor at [at] and its upload measures each `seat to cm`. */
    private fun sensed(state: GameState, at: Long, sensor: Seat, vararg heard: Pair<Int, Int>): GameState {
        val open = admitted(state, Event.MarkerScanned(Tick(at), sensor, anchor(state, sensor)))
        val slots = heard.map { (s, _) -> Sighting(ContactTokens.of(contactSeedFor(seed, Seat(s)), w), -20, w) }
        val packed = SightingUpload.pack(contactSeedFor(seed, sensor), w, uploads++, slots)
        val upload = ContactUpload(sensor, SightingUpload.unpack(packed)!!, heard.map { (s, cm) -> ContactDistance(Seat(s), cm) })
        resolveContacts(ContactMemory.EMPTY, Tick(at + 1), w, listOf(upload), open)
        return admitted(open, assertNotNull(resolvePlacements(Tick(at + 1), upload, open), "seat ${sensor.index} is not a sensor"))
    }

    /** Seat 2 scans the T card and holds the station; seat 0 performs and measures seats 4 and 5 beside it. */
    private fun reading(state: GameState = armed(), at: Long = 100): GameState {
        val held = admitted(state, Event.MarkerScanned(Tick(at), reader, station))
        assertEquals(reader, held.readerHolding(), "seat 2 does not hold the station")
        return sensed(held, at + 10, Seat(0), 4 to 100, 5 to 100)
    }

    private fun beats(state: GameState, at: Long): List<OccupancyBeat> = seats.map { occupancyBeat(state, it, Tick(at)) }

    /** **GUARD: only the seat holding the station is sent counts. Every other living phone is sent nothing.** */
    @Test
    fun `only the reader is sent the Terminal's counts`() {
        val state = reading()
        val all = beats(state, 120)
        assertIs<OccupancyBeat.Terminal>(all[reader.index], "the reader was sent no counts")
        for (seat in seats.filter { it.index != reader.index }) {
            assertEquals(OccupancyBeat.Nothing, all[seat.index], "seat ${seat.index}, not the reader, was sent ${all[seat.index]}")
        }
    }

    /** **The reader's counts are the house's captures: the error rolled once and stored, and the band from the stored jitter.** */
    @Test
    fun `the reader's counts are the stored capture's reported count and band`() {
        val state = reading()
        val room = roomOf(state, Seat(0))
        val index = state.plan.indexOf(room)
        val capture = assertNotNull(state.observationOf(room))
        for (at in listOf(120L, 120L + 20 * 20, 120L + 60 * 20)) {
            val beat = assertIs<OccupancyBeat.Terminal>(occupancyBeat(state, reader, Tick(at)))
            assertEquals(capture.reportedCount, beat.counts[index], "the count is not the capture's reported count at $at")
            assertEquals(capture.stalenessAt(Tick(at)), beat.bands[index], "the band is not the capture's at $at")
            assertEquals(capture.toView(Tick(at)).reportedCount, beat.counts[index])
        }
        // All three bands are reached by the one capture as it ages: fresh, stale, cold.
        val bands = listOf(120L, 120L + 25 * 20, 120L + 60 * 20).map {
            (occupancyBeat(state, reader, Tick(it)) as OccupancyBeat.Terminal).bands[index]
        }
        assertEquals(listOf(OccupancyWire.FRESH, OccupancyWire.STALE, OccupancyWire.COLD), bands)
        // Asked twice, the same moment answers the same: nothing is re-rolled.
        assertEquals(occupancyBeat(state, reader, Tick(300)), occupancyBeat(state, reader, Tick(300)))

        val beat = occupancyBeat(state, reader, Tick(120)) as OccupancyBeat.Terminal
        // A room nobody sensed in is a hole: 0, cold. The station's room is the outline.
        val hole = (0 until state.plan.rooms.size).first { it != index && state.plan.rooms[it].kind == RoomKind.Room }
        assertEquals(0, beat.counts[hole])
        assertEquals(OccupancyWire.COLD, beat.bands[hole])
        assertEquals(state.plan.indexOf("ROOM 0"), beat.here)
        assertEquals(state.plan.rooms.size, beat.counts.size, "one count per room of the plan")
    }

    /** **The error is the house's, so over many captures the reader's counts are not the truth.** */
    @Test
    fun `the reader's counts carry the injected error`() {
        var state = admitted(armed(), Event.MarkerScanned(Tick(100), reader, station))
        var wrong = 0
        val room = roomOf(state, Seat(0))
        val index = state.plan.indexOf(room)
        for (i in 0 until 200) {
            state = sensed(state, 200L + i * 4, Seat(0), 4 to 100)
            val capture = state.observationOf(room)!!
            val beat = occupancyBeat(state, reader, Tick(202L + i * 4)) as OccupancyBeat.Terminal
            assertEquals(capture.reportedCount, beat.counts[index])
            if (beat.counts[index] != capture.trueCount) wrong++
        }
        assertTrue(wrong in 10..60, "the reader saw the truth $wrong times wrong in 200; the 15 % miss is not there")
    }

    /** **A Revoked seat is in no Terminal count, and the couch sees the truth, live.** */
    @Test
    fun `a Revoked seat is left out of the Terminal and the couch is shown true occupancy`() {
        // Seat 4 was Revoked in an earlier shift and is on the couch; seat 6 is newly Revoked.
        val out = reading(armed().copy(revoked = listOf(Seat(4), Seat(6)), newlyRevoked = listOf(Seat(6))))
        val room = roomOf(out, Seat(0))
        val index = out.plan.indexOf(room)
        val capture = assertNotNull(out.observationOf(room))
        val placedThen = seats.filter { out.placedRoomOf(it, capture.seenAt) == room }.map { it.index }
        assertTrue(4 !in placedThen && 6 !in placedThen, "a Revoked seat was placed: $placedThen")
        assertEquals(placedThen.size, capture.trueCount, "the capture counted a seat the house had not placed")
        assertTrue(capture.trueCount >= 2, "seats 0 and 5 are not in the capture")

        val couch = assertIs<OccupancyBeat.Outside>(occupancyBeat(out, Seat(4), Tick(120)), "the couch was not shown true occupancy")
        val truth = out.plan.rooms.map { r -> seats.count { out.placedRoomOf(it, Tick(120)) == r.name } }
        assertEquals(truth, couch.counts, "the couch's counts are not the house's placements")
        // Seats 0 and 5 where seat 0 works, and the reader at the station's check-in: never 4 or 6.
        assertEquals(3, couch.counts.sum(), "a Revoked seat was counted, or a living one missed")
        assertEquals(OccupancyBeat.Nothing, occupancyBeat(out, Seat(6), Tick(120)), "a newly Revoked phone was shown the map")
        // The truth is live: seat 5's sighting expires and the reader's check-in lapses; the couch follows.
        val later = 120L + 50 * 20
        val after = assertIs<OccupancyBeat.Outside>(occupancyBeat(out, Seat(4), Tick(later)))
        assertEquals(1, after.counts.sum(), "the couch held a placement past its expiry")
        // A Restrained seat is on the couch the same way.
        val restrained = out.copy(restrained = listOf(Seat(7)))
        assertIs<OccupancyBeat.Outside>(occupancyBeat(restrained, Seat(7), Tick(120)))
    }

    /** **Releasing the station stops the counts, whichever of D-203's ways it comes free.** */
    @Test
    fun `releasing the station stops the counts`() {
        val state = reading()
        assertIs<OccupancyBeat.Terminal>(occupancyBeat(state, reader, Tick(120)))
        val left = admitted(state, Event.TerminalLeft(Tick(121), reader))
        assertEquals(OccupancyBeat.Nothing, occupancyBeat(left, reader, Tick(122)), "the reader left and was still sent counts")
        val called = admitted(state, Event.MeetingCalled(Tick(121), Seat(5), MeetingTrigger.MeetingCard))
        assertEquals(seats.map { OccupancyBeat.Nothing }, beats(called, 122), "a phone was shown the map at a meeting")
        val revoked = state.copy(revoked = listOf(reader), newlyRevoked = listOf(reader))
        assertEquals(OccupancyBeat.Nothing, occupancyBeat(revoked, reader, Tick(122)), "a reader put out was still sent counts")
    }

    /** **Static changes no count** (D-186): tracking is untouched, so the jam is the phone's to draw over them. */
    @Test
    fun `Static leaves the reader's counts and the couch's alone`() {
        val state = reading(armed().copy(revoked = listOf(Seat(4))))
        // The window as the rules hold it once a Static lands (its firing and cooldown are `StaticTest`'s).
        val jammed = state.copy(staticUntil = Tick(121 + 10 * 20))
        assertTrue(jammed.staticRunning(Tick(122)), "the jam did not start")
        assertEquals(occupancyBeat(state, reader, Tick(122)), occupancyBeat(jammed, reader, Tick(122)))
        assertEquals(occupancyBeat(state, Seat(4), Tick(122)), occupancyBeat(jammed, Seat(4), Tick(122)))
    }

    /** **Role parity: an Insider reader and a Resident reader get the same beat of the same state, and the same sealed bytes.** */
    @Test
    fun `an Insider reader and a Resident reader are sent the same counts`() {
        val asResident = reading(armed(insiders = listOf(Seat(1))))
        val asInsider = reading(armed(insiders = listOf(reader)))
        val a = occupancyBeat(asResident, reader, Tick(130))
        val b = occupancyBeat(asInsider, reader, Tick(130))
        assertIs<OccupancyBeat.Terminal>(a)
        assertEquals(a, b, "the reader's counts moved with its role")
        val key: ContactSeed = contactSeedFor(seed, reader)
        val nonce = ByteArray(8) { it.toByte() }
        assertEquals(OccupancyWire.pack(key, nonce, a), OccupancyWire.pack(key, nonce, b))
        // And the field is one width whatever it holds: a reading, the couch's, nothing.
        val widths = listOf(a, OccupancyBeat.outside(listOf(1, 2)), OccupancyBeat.Nothing).map { OccupancyWire.pack(key, nonce, it).length }
        assertEquals(listOf(OccupancyWire.CHARS, OccupancyWire.CHARS, OccupancyWire.CHARS), widths)
        assertNotEquals(OccupancyWire.pack(key, nonce, a), OccupancyWire.pack(key, nonce, OccupancyBeat.Nothing))
    }
}
