package home.someoneshome.core

import home.someoneshome.model.CellRect
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.PaintResult
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Room
import home.someoneshome.model.RoomKind
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.Timelapse
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.OccupancyWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.toView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **D-319: the Terminal's timelapse, in the rules and in the beat.**
 *
 * The owner's 2026-11-21 rulings on D-316's five stops: the cooldown is the station's and the next
 * reader inherits it; frames are bare counts, a frame every 2 s of real time; a meeting ends a
 * playback, is not an early cancel, and after it the cooldown equals the new history; frames switch,
 * nothing tweens; Static's seconds play back as noise; watching blinds you to the present; only the
 * reader is sent the frames. And `gdd.md`'s table: 2:00 at 6×, once, oldest to newest, the cooldown
 * from playback end and equal to the history, an early cancel cut linearly by the fraction unwatched,
 * the floor at the round's start or the last meeting's end.
 *
 * The house's own pipeline throughout ([OccupancyBeatTest]'s home: ROOM 0 to ROOM 4 on GROUND,
 * STAIRS, ROOM 5 on UPPER; the T card in ROOM 0). Seat 2 reads. 20 ticks a second, so a frame is
 * every 40 ticks and a frame plays for 40/6 of a tick.
 */
class TimelapseTest {

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

    private fun effectsOf(state: GameState, event: Event) =
        (admit(state, event) as Admission.Admitted).reduction.effects

    private fun anchor(state: GameState, seat: Seat): MarkerId = state.workOrderFor(seat)!!.entries.first().anchor!!

    private fun roomOf(state: GameState, seat: Seat): String = state.destinationOf(anchor(state, seat))!!.room

    private var uploads = 0L

    private fun sensed(state: GameState, at: Long, sensor: Seat, vararg heard: Pair<Int, Int>): GameState {
        val open = admitted(state, Event.MarkerScanned(Tick(at), sensor, anchor(state, sensor)))
        val slots = heard.map { (s, _) -> Sighting(ContactTokens.of(contactSeedFor(seed, Seat(s)), w), -20, w) }
        val packed = SightingUpload.pack(contactSeedFor(seed, sensor), w, uploads++, slots)
        val upload = ContactUpload(sensor, SightingUpload.unpack(packed)!!, heard.map { (s, cm) -> ContactDistance(Seat(s), cm) })
        resolveContacts(ContactMemory.EMPTY, Tick(at + 1), w, listOf(upload), open)
        return admitted(open, assertNotNull(resolvePlacements(Tick(at + 1), upload, open), "seat ${sensor.index} is not a sensor"))
    }

    /** Seat 2 holds the station from 100; seat 0 performs at 110 and measures seats 4 and 5 beside it. */
    private fun reading(state: GameState = armed()): GameState {
        val held = admitted(state, Event.MarkerScanned(Tick(100), reader, station))
        assertEquals(reader, held.readerHolding())
        return sensed(held, 110, Seat(0), 4 to 100, 5 to 100)
    }

    /** **Time passing**: an event that changes nothing (seat 7 leaving a station it never held), at [at]. */
    private fun passing(state: GameState, at: Long): GameState = admitted(state, Event.TerminalLeft(Tick(at), Seat(7)))

    private fun played(state: GameState, at: Long, seat: Seat = reader): GameState = admitted(state, Event.TimelapsePlayed(Tick(at), seat))

    /** A meeting from [at] to lights out, every living seat voting Skip. */
    private fun throughAMeeting(state: GameState, at: Long): GameState {
        var s = admitted(state, Event.MeetingCalled(Tick(at), Seat(5), MeetingTrigger.MeetingCard))
        for (seat in seats) s = admitted(s, Event.MeetingCheckedIn(Tick(at + 1), seat))
        if (s.meeting?.phase == MeetingPhase.Discussion) s = admitted(s, Event.DiscussionClosed(Tick(at + 2)))
        for (seat in s.livingSeats) {
            s = admitted(s, Event.VoteSelected(Tick(at + 3), seat, null))
            s = admitted(s, Event.VoteLocked(Tick(at + 3), seat))
        }
        if (s.meeting?.phase == MeetingPhase.Vote) s = admitted(s, Event.VoteWindowClosed(Tick(at + 4)))
        s = admitted(s, Event.TallyHalfwayReached(Tick(at + 5)))
        s = admitted(s, Event.MeetingClosed(Tick(at + 6)))
        assertNull(s.meeting, "the fixture's meeting never closed")
        return s
    }

    @Test
    fun `a frame every two seconds of the shift oldest to newest bare counts the reader would have been shown`() {
        val state = passing(reading(), 1000)
        val history = state.timelapse.history
        // Marks every 40 ticks from the shift's start, strictly before the event that took them.
        assertEquals((1..24).map { it * 40L }, history.map { it.at.step }, "the frames are not every two seconds, oldest first")
        val room = state.plan.indexOf(roomOf(state, Seat(0)))
        val capture = assertNotNull(state.observationOf(roomOf(state, Seat(0))))
        // Before the capture the room was a hole, 0; after it, the capture's REPORTED count, error and
        // all, with no band to carry (the type has none).
        assertEquals(0, history.first { it.at.step == 80L }.counts[room])
        for (frame in history.filter { it.at.step >= 120 }) {
            assertEquals(capture.reportedCount, frame.counts[room], "frame ${frame.at.step} is not the capture's reported count")
        }
        assertTrue(history.none { it.jammed })
        assertEquals(state.plan.rooms.size, history.first().counts.size, "one count per room of the plan")
        // The beat says so: 24 frames held, and the station ready.
        val beat = assertIs<OccupancyBeat.Terminal>(occupancyBeat(state, reader, Tick(1000)))
        assertEquals(24, beat.history)
        assertEquals(0, beat.readyIn)
        // Two minutes is the most: sixty frames, the oldest dropped.
        val long = passing(state, 4000)
        assertEquals(Timelapse.FRAMES, long.timelapse.history.size)
        assertEquals(4000L - 40, long.timelapse.history.last().at.step)
        assertEquals(4000L - 60 * 40, long.timelapse.history.first().at.step)
    }

    @Test
    fun `the playback plays the history oldest to newest three frames a beat and the live view is not sent`() {
        val ready = passing(reading(), 1000)
        val state = played(ready, 1000)
        val playback = assertNotNull(state.timelapse.playback, "the reader's TIMELAPSE started nothing")
        assertEquals(ready.timelapse.history, playback.frames)
        val first = assertIs<OccupancyBeat.Lapse>(occupancyBeat(state, reader, Tick(1000)), "the reader was sent the live view while playing")
        assertEquals(0, first.first)
        assertEquals(24, first.frames)
        assertEquals(playback.frames.take(3).map { it.counts }, first.counts)
        // One second later, six seconds of history on: three frames further.
        val second = assertIs<OccupancyBeat.Lapse>(occupancyBeat(state, reader, Tick(1020)))
        assertEquals(3, second.first)
        assertEquals(playback.frames.subList(3, 6).map { it.counts }, second.counts)
        // Oldest to newest, never back: the first frame a beat carries only climbs.
        val firsts = (1000L until playback.endsAt.step step 5).map { (occupancyBeat(state, reader, Tick(it)) as OccupancyBeat.Lapse).first }
        assertEquals(firsts.sorted(), firsts)
        // The last beat carries the last frame and no more; then the live view is back.
        val last = assertIs<OccupancyBeat.Lapse>(occupancyBeat(state, reader, Tick(playback.endsAt.step - 1)))
        assertEquals(23, last.first + last.counts.size - 1)
        assertEquals(1000L + 160, playback.endsAt.step, "24 frames at 6x are not 8 seconds")
        assertIs<OccupancyBeat.Terminal>(occupancyBeat(state, reader, playback.endsAt))
    }

    @Test
    fun `the cooldown is the station's runs from playback end equals the history and the next reader inherits it`() {
        val state = played(passing(reading(), 1000), 1000)
        // 24 frames: 8 s of playback, then 48 s of cooldown.
        assertEquals(1000L + 160 + 24 * 40, state.timelapse.readyAt.step)
        // The reader plays it out and leaves; seat 3 claims the station and finds it cooling.
        val left = admitted(passing(state, 1200), Event.TerminalLeft(Tick(1200), reader))
        val next = admitted(left, Event.MarkerScanned(Tick(1300), Seat(3), station))
        assertEquals(Seat(3), next.readerHolding())
        val cooling = assertIs<OccupancyBeat.Terminal>(occupancyBeat(next, Seat(3), Tick(1300)))
        assertEquals((2120 - 1300 + 19) / 20, cooling.readyIn, "the next reader did not inherit the station's cooldown")
        // TIMELAPSE pressed while it cools starts nothing; once it has run, it plays.
        val refused = played(next, 1300, Seat(3))
        assertNull(refused.timelapse.playback, "a playback began while the station was cooling")
        assertIs<OccupancyBeat.Terminal>(occupancyBeat(refused, Seat(3), Tick(1301)))
        assertEquals(0, (occupancyBeat(refused, Seat(3), Tick(2120)) as OccupancyBeat.Terminal).readyIn)
        val plays = played(refused, 2120, Seat(3))
        assertNotNull(plays.timelapse.playback)
        assertIs<OccupancyBeat.Lapse>(occupancyBeat(plays, Seat(3), Tick(2120)))
    }

    @Test
    fun `an early cancel cuts the cooldown linearly by the fraction unwatched`() {
        val state = played(passing(reading(), 1000), 1000)
        // A quarter watched (40 of 160 ticks): a quarter of the 960-tick cooldown, from now.
        val stopped = admitted(state, Event.TimelapseStopped(Tick(1040), reader))
        assertNull(stopped.timelapse.playback)
        assertEquals(1040L + 960 / 4, stopped.timelapse.readyAt.step)
        assertIs<OccupancyBeat.Terminal>(occupancyBeat(stopped, reader, Tick(1041)), "the live view did not come back after STOP")
        // Somebody else's STOP is nothing: the playback plays on and the cooldown stands.
        val other = admitted(state, Event.TimelapseStopped(Tick(1040), Seat(3)))
        assertEquals(state.timelapse.playback, other.timelapse.playback)
        assertEquals(state.timelapse.readyAt, other.timelapse.readyAt)
        // Leaving the station mid-playback is an early cancel too: half watched, half the cooldown.
        val walked = admitted(state, Event.TerminalLeft(Tick(1080), reader))
        assertNull(walked.timelapse.playback)
        assertEquals(1080L + 960 / 2, walked.timelapse.readyAt.step)
        // A STOP after it played out changes nothing: the full cooldown from the end stands.
        val late = admitted(state, Event.TimelapseStopped(Tick(1200), reader))
        assertEquals(1000L + 160 + 960, late.timelapse.readyAt.step)
    }

    @Test
    fun `a meeting ends the playback is not an early cancel and after it the floor and the cooldown are the new tiny history`() {
        val state = played(passing(reading(), 1000), 1000)
        val called = admitted(state, Event.MeetingCalled(Tick(1040), Seat(5), MeetingTrigger.MeetingCard))
        assertNull(called.timelapse.playback, "the playback outlived the meeting's call")
        assertEquals(state.timelapse.readyAt, called.timelapse.readyAt, "the meeting cut the cooldown as an early cancel would")
        assertEquals(OccupancyBeat.Nothing, occupancyBeat(called, reader, Tick(1041)))
        // Lights out: the history is wiped, the floor is the meeting's end, and the cooldown is cleared.
        val after = throughAMeeting(state, 1040)
        val end = after.shiftStartedAt.step
        assertEquals(1046L, end)
        assertEquals(emptyList(), after.timelapse.history, "history from before the meeting survived it")
        assertEquals(end, after.timelapse.readyAt.step, "the cooldown outlived the meeting")
        // The reader claims again: no history yet, so it waits for the first frame, two seconds in.
        val back = admitted(after, Event.MarkerScanned(Tick(1050), reader, station))
        val waiting = assertIs<OccupancyBeat.Terminal>(occupancyBeat(back, reader, Tick(1050)))
        assertEquals(0, waiting.history)
        assertEquals(2, waiting.readyIn)
        assertNull(played(back, 1050).timelapse.playback, "a playback began with no history")
        // Two seconds on, one frame and the station ready; it plays that one frame, from after the floor.
        val one = played(back, 1090)
        val playback = assertNotNull(one.timelapse.playback)
        assertEquals(listOf(end + 40), playback.frames.map { it.at.step })
        assertTrue(playback.frames.all { it.at.step > end }, "a frame from before the floor played")
        // The standing rule: cooldown == history, two seconds of it.
        assertEquals(playback.endsAt.step + 40, one.timelapse.readyAt.step)
    }

    @Test
    fun `Static's seconds play back as noise and nothing inside them about 1_7 s of a playback`() {
        val reading = reading()
        val jammed = admitted(passing(reading, 1000), Event.StaticFired(Tick(1000), Seat(1)))
        assertEquals(Tick(1200), jammed.staticUntil, "the fixture's Static did not land")
        val state = passing(jammed, 1400)
        val room = state.plan.indexOf(roomOf(state, Seat(0)))
        val noise = state.timelapse.history.filter { it.jammed }
        assertEquals(listOf(1000L, 1040, 1080, 1120, 1160), noise.map { it.at.step }, "the jam's ten seconds are not its five frames")
        assertTrue(noise.all { f -> f.counts.all { it == 0 } }, "a jammed frame held what was inside the jam")
        val capture = assertNotNull(state.observationOf(roomOf(state, Seat(0))))
        assertEquals(capture.reportedCount, state.timelapse.history.first { it.at.step == 1200L }.counts[room], "the frame after the jam is not clear")
        // At 6x the five jammed frames play for 5 x 40 / 6 ticks: 1.67 s.
        assertEquals(1.67, (noise.size * Timelapse.FRAME_EVERY / 6.0 / 20.0), 0.01)
        // The reader is sent them as jammed frames, counts 0.
        val playing = played(state, 1400)
        val beats = (1400L until playing.timelapse.playback!!.endsAt.step).map { occupancyBeat(playing, reader, Tick(it)) as OccupancyBeat.Lapse }
        val sent = beats.flatMap { b -> b.jammed.indices.map { (b.first + it) to (b.jammed[it] to b.counts[it]) } }.toMap()
        val jammedIndices = sent.filterValues { it.first }.keys.sorted()
        assertEquals(5, jammedIndices.size)
        assertTrue(sent.filterValues { it.first }.values.all { (_, counts) -> counts.all { it == 0 } })
    }

    /** **GUARD: only the reader is sent the frames.** Every other living phone is sent nothing, the couch its own. */
    @Test
    fun `only the reader is sent the timelapse's frames`() {
        val state = played(passing(reading(armed().copy(revoked = listOf(Seat(4)))), 1000), 1000)
        for (at in listOf(1000L, 1050, 1100)) {
            assertIs<OccupancyBeat.Lapse>(occupancyBeat(state, reader, Tick(at)), "the reader was sent no frames at $at")
            for (seat in seats.filter { it.index != reader.index }) {
                val beat = occupancyBeat(state, seat, Tick(at))
                if (seat.index == 4) {
                    assertIs<OccupancyBeat.Outside>(beat, "the couch was sent something other than true occupancy")
                } else {
                    assertEquals(OccupancyBeat.Nothing, beat, "seat ${seat.index}, not the reader, was sent $beat at $at")
                }
            }
        }
        // Every kind the field holds is one width: frames, a reading, the couch's, nothing.
        val key = contactSeedFor(seed, reader)
        val nonce = ByteArray(8) { it.toByte() }
        val widths = listOf(
            occupancyBeat(state, reader, Tick(1000)), occupancyBeat(state, reader, Tick(2000)),
            occupancyBeat(state, Seat(4), Tick(1000)), occupancyBeat(state, Seat(3), Tick(1000)),
        ).map { OccupancyWire.pack(key, nonce, it).length }
        assertEquals(List(4) { OccupancyWire.CHARS }, widths)
    }

    @Test
    fun `an Insider reader and a Resident reader get the same history the same playback and the same sealed bytes`() {
        val asResident = played(passing(reading(armed(insiders = listOf(Seat(1)))), 1000), 1000)
        val asInsider = played(passing(reading(armed(insiders = listOf(reader))), 1000), 1000)
        assertEquals(asResident.timelapse, asInsider.timelapse, "the station's timelapse moved with its reader's role")
        val key = contactSeedFor(seed, reader)
        val nonce = ByteArray(8) { it.toByte() }
        for (at in listOf(999L, 1000, 1030, 1159, 1160, 1500)) {
            val a = occupancyBeat(asResident, reader, Tick(at))
            val b = occupancyBeat(asInsider, reader, Tick(at))
            assertEquals(a, b, "the beat at $at moved with the reader's role")
            assertEquals(OccupancyWire.pack(key, nonce, a), OccupancyWire.pack(key, nonce, b))
        }
    }

    /** **Rule 1: the two requests emit nothing, whether they start, refuse, stop or do nothing.** */
    @Test
    fun `TIMELAPSE and STOP emit nothing whatever they decide`() {
        val state = passing(reading(), 1000)
        assertEquals(emptyList(), effectsOf(state, Event.TimelapsePlayed(Tick(1000), reader)), "a playback that began emitted")
        assertEquals(emptyList(), effectsOf(state, Event.TimelapsePlayed(Tick(1000), Seat(3))), "a refused playback emitted")
        val playing = played(state, 1000)
        assertEquals(emptyList(), effectsOf(playing, Event.TimelapsePlayed(Tick(1010), reader)), "a second press emitted")
        assertEquals(emptyList(), effectsOf(playing, Event.TimelapseStopped(Tick(1010), reader)), "a STOP emitted")
        assertEquals(emptyList(), effectsOf(playing, Event.TimelapseStopped(Tick(1010), Seat(3))), "somebody else's STOP emitted")
    }
}
