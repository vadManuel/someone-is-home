package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **The host's seat is a recorded input, it replays, and it puts nothing on any client's wire**
 * (D-289; recording 38).
 *
 * The house learns the seat its own phone holds and stamps it on the arming, so the notice can name
 * an out host who goes dark (D-283). It is the house's alone: the owner says everyone in the room
 * knows who the host is, and still no message or effect carries it.
 */
class HostSeatRecordingTest {

    private val host = Seat(0)

    private fun withHost(seat: Seat?): List<Event> =
        round().map { if (it is Event.RoundArmed) it.copy(host = seat) else it }

    /** **GUARD (rule 4): the seat survives the text form, the state rows hold it, and the round replays.** */
    @Test
    fun `the host's seat is recorded and replays exactly`() {
        val events = withHost(host)
        val (end, recording) = record(GameState.EMPTY, events)
        assertEquals(host, end.host, "the round did not hold the host's seat")
        val text = recording.toText()
        assertTrue(text.startsWith("someone-is-home/recording/52\n"), "the format was not bumped: ${text.lineSequence().first()}")
        val armingLine = text.lines().first { it.startsWith("E RoundArmed") }
        assertTrue("|host=0|" in armingLine, "the arming row does not carry the host: $armingLine")
        assertTrue(recording.stateTranscript.last().contains("|host=0|"), "the state row does not carry the host")

        val parsed = RecordingText.parse(text)
        // The fixture's names and lines come back blank by design (D-115, D-116), so the arming is
        // compared on its host and the round on its replay.
        assertEquals(host, (parsed.events.first() as Event.RoundArmed).host, "the host did not survive the text form")
        assertEquals(events.drop(1), parsed.events.drop(1), "an event did not survive the text form")
        assertEquals(recording.stateTranscript, parsed.stateTranscript)
        val replayed = replay(GameState.EMPTY, parsed)
        assertTrue(replayed is ReplayResult.Identical, "the recorded round did not replay: $replayed")

        // And none is none, both ways.
        val none = record(GameState.EMPTY, withHost(null)).second.toText()
        assertTrue("|host=none|" in none.lines().first { it.startsWith("E RoundArmed") })
        assertEquals(null, (RecordingText.parse(none).events.first() as Event.RoundArmed).host)
    }

    /** **GUARD: the parser defaults nothing; an arming row without the host is fatal** (a version 37 row). */
    @Test
    fun `an arming row without the host is fatal`() {
        val text = record(GameState.EMPTY, withHost(host)).second.toText()
        val armingLine = text.lines().first { it.startsWith("E RoundArmed") }
        val broken = armingLine.replace(Regex("""\|host=[^|]*"""), "")
        check(broken != armingLine) { "the injection changed nothing" }
        assertFailsWith<MalformedRecording> { RecordingText.parse(text.replace(armingLine, broken)) }
    }

    /**
     * **GUARD (rule 2): recording the host puts NOTHING on any client's wire.** Every seat's transcript,
     * built by the allowlist (`recordPerClient`), is byte-identical between the round with the host's
     * seat and the same round without it, and the authority's effects are the same rows.
     */
    @Test
    fun `no client is sent one byte for the host's seat`() {
        val with = recordPerClient(GameState.EMPTY, withHost(host))
        val without = recordPerClient(GameState.EMPTY, withHost(null))
        assertEquals(SEATS.size, with.perClient.size)
        for (seat in SEATS) {
            assertEquals(without.linesFor(seat), with.linesFor(seat), "seat ${seat.index} was sent something for the host's seat")
        }
        assertEquals(
            effectsOf(GameState.EMPTY, withHost(null)).map { Transcript.render(it) },
            effectsOf(GameState.EMPTY, withHost(host)).map { Transcript.render(it) },
            "the authority produced an effect for the host's seat",
        )
    }

    /** **GUARD (the differential harness): the host's seat adds no divergence on a role exchange.** */
    @Test
    fun `the host's seat adds nothing to a role exchange's diff`() {
        // **The meter subtracted, as `DifferentialTest` subtracts it** (D-109's ruled asymmetry). Since
        // D-322 the fixture walks circuits and Memory Dumps as well as Subroutines, so the two exchanged
        // seats bank at different moments and the meter moves on different lines; what is asked here is
        // whether anything ELSE follows the role, and `DifferentialTest` owns the meter's own reach.
        val with = differentialOnRoleExchange(GameState.EMPTY, withHost(host), Seat(1), Seat(3), ignoring = METER_ASYMMETRY)
        val without = differentialOnRoleExchange(GameState.EMPTY, withHost(null), Seat(1), Seat(3), ignoring = METER_ASYMMETRY)
        assertEquals(emptyList(), with.unexplained, "the host's seat leaked a role: $with")
        assertEquals(without.divergences.map { it.toString() }, with.divergences.map { it.toString() })
    }
}
