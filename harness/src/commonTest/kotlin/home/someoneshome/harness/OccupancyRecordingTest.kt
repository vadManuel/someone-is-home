package home.someoneshome.harness

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.CellRect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerId
import home.someoneshome.model.PaintResult
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Room
import home.someoneshome.model.RoomKind
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.toView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **O2 and O4 in the harness: the plan and the spectator map are recorded and replay, and they put
 * nothing new on any client's wire.**
 *
 * The round is the shared fixture's home (four rooms, two cards each, the T card beside them) with
 * a plan painted for it, an L-shaped ROOM 0 and a stairwell among them. Seat 0 and seat 2 perform;
 * each one's uploads are `Sensed` rows hearing the others.
 */
class OccupancyRecordingTest {

    private val plan: HomePlanView = run {
        var p = HousePlan.EMPTY.withFloor("GROUND").withFloor("UPPER")
        fun paint(floor: String, room: Room, vararg strokes: CellRect) {
            p = (p.paint(floor, PlanRoom(room, strokes.toList())) as PaintResult.Painted).plan
        }
        // An L: one room, two strokes.
        paint("GROUND", Room("ROOM 0"), CellRect(0, 0, 3, 1), CellRect(0, 1, 1, 2))
        paint("GROUND", Room("ROOM 1"), CellRect(3, 0, 2, 3))
        paint("GROUND", Room("STAIRS", RoomKind.Stairs), CellRect(1, 1, 2, 2))
        paint("UPPER", Room("ROOM 2"), CellRect(0, 0, 2, 2))
        paint("UPPER", Room("ROOM 3"), CellRect(2, 0, 2, 2))
        p.toView()
    }

    private val arming = Event.RoundArmed(
        Tick(0), seed = 20261117L, seats = SEATS, insiders = INSIDERS, markers = placed(MARKERS),
        terminal = STATION, plan = plan, terminalRoom = "ROOM 1",
    )

    /** The armed state, for the anchors: the card each seat's first Subroutine opens at. */
    private val armed: GameState = (admit(GameState.EMPTY, arming) as Admission.Admitted).reduction.state

    private fun anchor(seat: Int): MarkerId = armed.workOrderFor(Seat(seat))!!.entries.first().anchor!!
    private fun room(seat: Int): String = armed.destinationOf(anchor(seat))!!.room

    /** The round with the map's input in it. */
    private val sensing: List<Event> = listOf(
        arming,
        Event.MarkerScanned(Tick(100), Seat(0), anchor(0)),
        Event.MarkerScanned(Tick(101), Seat(2), anchor(2)),
        Event.Sensed(Tick(120), Seat(0), room(0), listOf(Seat(3), Seat(4))),
        Event.Sensed(Tick(121), Seat(2), room(2), emptyList()),
        Event.MarkerScanned(Tick(140), Seat(6), STATION),
        Event.Sensed(Tick(140), Seat(0), room(0), listOf(Seat(1), Seat(3))),
        Event.PerformanceEnded(Tick(160), Seat(2)),
        Event.Sensed(Tick(161), Seat(2), room(2), listOf(Seat(7))),
        Event.Sensed(Tick(200), Seat(0), room(0), listOf(Seat(4), Seat(5), Seat(6))),
    )

    /** The same round as the house played it before O2 and O4: no plan, no station room, no map. */
    private val blind: List<Event> = sensing
        .filterNot { it is Event.Sensed }
        .map { if (it is Event.RoundArmed) it.copy(plan = HomePlanView.EMPTY, terminalRoom = null) else it }

    /** **GUARD (rule 4): the plan and every `Sensed` survive the text form, and the round replays.** */
    @Test
    fun `the plan and the map's input are recorded and replay exactly`() {
        val (end, recording) = record(GameState.EMPTY, sensing)
        assertTrue(end.observations.isNotEmpty() && end.sightings.isNotEmpty(), "the round placed and observed nothing")

        val text = recording.toText()
        assertTrue(text.startsWith("someone-is-home/recording/51\n"), "the format was not bumped: ${text.lineSequence().first()}")
        val parsed = RecordingText.parse(text)
        assertEquals(sensing, parsed.events, "an event did not survive the text form")
        assertEquals(plan, (parsed.events.first() as Event.RoundArmed).plan)
        assertEquals(recording.stateTranscript, parsed.stateTranscript)

        val replayed = replay(GameState.EMPTY, parsed)
        assertTrue(replayed is ReplayResult.Identical, "the recorded round did not replay: $replayed")
        // And the state rows hold the map, the once-rolled errors included.
        assertTrue(Regex("""\|observations=\d+:ROOM 2:200:5:""").containsMatchIn(recording.stateTranscript.last()), recording.stateTranscript.last())
        assertTrue(recording.stateTranscript.last().contains("|rooms=ROOM 0:Room,ROOM 1:Room,STAIRS:Stairs"))
    }

    /** **GUARD: the parser defaults nothing; a missing or broken plan is fatal.** */
    @Test
    fun `an arming row without its plan is fatal`() {
        val text = record(GameState.EMPTY, sensing).second.toText()
        val armingLine = text.lines().first { it.startsWith("E RoundArmed") }
        for ((label, broken) in listOf(
            "no rooms" to armingLine.replace(Regex("""\|rooms=[^|]*"""), ""),
            "no storeys" to armingLine.replace(Regex("""\|storeys=[^|]*"""), ""),
            "no terminalRoom" to armingLine.replace(Regex("""\|terminalRoom=[^|]*"""), ""),
            "a cell naming a room the plan lacks" to armingLine.replace(".3.", ".9."),
        )) {
            check(broken != armingLine) { "$label: the injection changed nothing" }
            assertFailsWith<MalformedRecording>(label) { RecordingText.parse(text.replace(armingLine, broken)) }
        }
        val sensedLine = text.lines().first { it.startsWith("E Sensed") }
        assertFailsWith<MalformedRecording> { RecordingText.parse(text.replace(sensedLine, sensedLine.replace(Regex("""\|heard=[^|]*"""), ""))) }
    }

    /**
     * **GUARD (rule 2): the plan and the map put nothing on any client's wire but the plan itself**
     * (D-316). Every seat's transcript, built by the allowlist itself (`recordPerClient`), is
     * byte-identical between the round with the plan and the map's input in it and the same round
     * without them, **except one `PlanIssued` row per seat at arming**, which is the plan reaching
     * every seat alike. The map's input (`Sensed`) still puts nothing anywhere: the counts ride the
     * upload's answer, sealed, and are no effect.
     */
    @Test
    fun `no client is sent one byte more for the plan or the map than the plan itself`() {
        val withMap = recordPerClient(GameState.EMPTY, sensing)
        val without = recordPerClient(GameState.EMPTY, blind)
        assertEquals(SEATS.size, withMap.perClient.size)
        val plan = { lines: List<String> -> lines.filter { "PlanIssued|" in it } }
        for (seat in SEATS) {
            assertEquals(
                without.linesFor(seat).filterNot { "PlanIssued|" in it },
                withMap.linesFor(seat).filterNot { "PlanIssued|" in it },
                "seat ${seat.index} was sent something new",
            )
            assertEquals(1, plan(withMap.linesFor(seat)).size, "seat ${seat.index} was not sent the plan once")
        }
        assertEquals(1, SEATS.map { plan(withMap.linesFor(it)).single().substringAfter("|storeys=") }.distinct().size, "two seats were sent two plans")
        assertEquals(
            effectsOf(GameState.EMPTY, blind).map { Transcript.render(it) }.filterNot { it.startsWith("PlanIssued|") },
            effectsOf(GameState.EMPTY, sensing).map { Transcript.render(it) }.filterNot { it.startsWith("PlanIssued|") },
            "the authority produced an effect for the map's input",
        )
        // And the comparison measured something: the two rounds' states differ.
        assertNotEquals(record(GameState.EMPTY, blind).second.stateTranscript.last(), record(GameState.EMPTY, sensing).second.stateTranscript.last())
    }

    /**
     * **GUARD (the differential harness): the map adds no divergence on a role exchange.** Seats 1
     * and 3 trade roles (an Insider and a Resident, one of them heard by a sensor); the per-client
     * diff of the round with the map is exactly the diff of the round without it.
     */
    @Test
    fun `the map adds nothing to a role exchange's diff`() {
        val withMap = differentialOnRoleExchange(GameState.EMPTY, sensing, Seat(1), Seat(3))
        val without = differentialOnRoleExchange(GameState.EMPTY, blind, Seat(1), Seat(3))
        assertEquals(emptyList(), withMap.unexplained, "the map leaked a role: $withMap")
        assertEquals(without.divergences.map { it.toString() }, withMap.divergences.map { it.toString() })
        // And the authority's own map did not move with the role either.
        val swapped = withRolesExchanged(sensing, Seat(1), Seat(3))
        val a = record(GameState.EMPTY, sensing).first
        val b = record(GameState.EMPTY, swapped).first
        assertNotEquals(a.insiderSeats, b.insiderSeats)
        assertEquals(Transcript.render(a).substringAfter("|rooms="), Transcript.render(b).substringAfter("|rooms="))
    }
}
