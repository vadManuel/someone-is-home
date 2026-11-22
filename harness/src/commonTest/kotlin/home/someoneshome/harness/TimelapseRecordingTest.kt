package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The station's timelapse is recorded, and a round with playbacks in it replays** (D-319;
 * recording 49).
 *
 * The fixture round's station visits each gain the reader's TIMELAPSE as it claims and a STOP a tick
 * later, so the recording holds both requests, the state rows hold the history the rules took at each
 * two-second mark, the playback and the station's cooldown, and the replay must take the same frames
 * from the same events. What the reader was shown is `occupancyBeat` of those rows; no beat is
 * recorded.
 */
class TimelapseRecordingTest {

    /** [round] with a TIMELAPSE after every claim of the station and a STOP after the refused second scan. */
    private fun withPlaybacks(): List<Event> {
        val events = round()
        val out = ArrayList<Event>(events.size + 16)
        for (i in events.indices) {
            val event = events[i]
            out += event
            val next = events.getOrNull(i + 1)
            if (event is Event.MarkerScanned && next is Event.MarkerScanned && event.marker == STATION && next.marker == STATION) {
                out += Event.TimelapsePlayed(event.at, event.actor)
            }
            val before = events.getOrNull(i - 1)
            if (event is Event.MarkerScanned && before is Event.MarkerScanned && event.marker == STATION && before.marker == STATION) {
                out += Event.TimelapseStopped(event.at, before.actor)
            }
        }
        return out
    }

    /** **GUARD (rule 4): the requests and the station's state survive the text form, and the round replays.** */
    @Test
    fun `a round with playbacks in it is recorded and replays exactly`() {
        val events = withPlaybacks()
        val (_, recording) = record(GameState.EMPTY, events)
        val text = recording.toText()
        assertTrue(text.startsWith("someone-is-home/recording/53\n"), "the format was not bumped: ${text.lineSequence().first()}")
        assertTrue(text.lines().any { it.startsWith("E TimelapsePlayed") }, "no TIMELAPSE was recorded")
        assertTrue(text.lines().any { it.startsWith("E TimelapseStopped") }, "no STOP was recorded")
        assertTrue(recording.stateTranscript.all { "|timelapse=" in it }, "a state row does not carry the station's timelapse")
        assertTrue(recording.stateTranscript.any { "playback=none" !in it }, "no playback ever began, so nothing here was tested")
        assertTrue(recording.stateTranscript.any { ":frames=" in it && Regex(""":frames=\d""").containsMatchIn(it) }, "no frame was ever taken")

        val parsed = RecordingText.parse(text)
        assertEquals(events.drop(1), parsed.events.drop(1), "an event did not survive the text form")
        val replayed = replay(GameState.EMPTY, parsed)
        assertTrue(replayed is ReplayResult.Identical, "the recorded round did not replay: $replayed")
    }

    /** **GUARD (rule 1): the two requests emit nothing, so a role exchange under them leaves nothing unexplained.** */
    @Test
    fun `the timelapse adds nothing unexplained to a role exchange's diff`() {
        // **The meter subtracted, as `HostSeatRecordingTest` subtracts it** (D-109's ruled asymmetry;
        // integrated with D-322, whose fixture walks circuits, so the exchanged seats bank at different
        // moments). What is asked here is whether the timelapse adds anything else.
        val with = differentialOnRoleExchange(GameState.EMPTY, withPlaybacks(), Seat(1), Seat(3), ignoring = METER_ASYMMETRY)
        assertEquals(emptyList(), with.unexplained, "the timelapse leaked a role: $with")
        val (_, plain) = record(GameState.EMPTY, round())
        val (_, played) = record(GameState.EMPTY, withPlaybacks())
        assertEquals(plain.effectTranscript, played.effectTranscript, "TIMELAPSE or STOP emitted something")
    }
}
