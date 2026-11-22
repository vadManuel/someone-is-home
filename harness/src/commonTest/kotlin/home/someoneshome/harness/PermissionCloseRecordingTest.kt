package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The warning and the close are recorded, and the round replays exactly** (D-310, D-311; recording 46).
 *
 * A round armed as the fixture arms it, a permission switched off, the meeting that warns, and a
 * second permission the house closes on. The recording holds the cause, the notice's warning, the
 * close as an event of its own, and the three state columns that say why a round ended with no
 * winner; the parser reads every one back, and a role exchange adds no divergence.
 */
class PermissionCloseRecordingTest {

    private fun evening(): List<Event> {
        val arming = round().first()
        var t = arming.at.step + 1
        fun at() = Tick(t++)
        return listOf(arming, Event.RadioFaulted(at(), Seat(3), RadioCause.Permission), Event.RadioFaulted(at(), Seat(4), RadioCause.Person)) +
            Event.MeetingCalled(at(), Seat(0), MeetingTrigger.MeetingCard) +
            SEATS.drop(1).map { Event.MeetingCheckedIn(at(), it) } +
            listOf(
                Event.RadioFaulted(at(), Seat(2), RadioCause.Permission),
                Event.HouseClosedOnPermission(at(), Seat(2)),
            )
    }

    /** **GUARD (rule 4): the warning and the close survive the text form and replay.** */
    @Test
    fun `the warning and the close are recorded and replay exactly`() {
        val events = evening()
        val (end, recording) = record(GameState.EMPTY, events)
        assertTrue(end.closed && end.ended && end.outcome == null, "the fixture never closed the house")
        val text = recording.toText()
        assertTrue(text.startsWith("someone-is-home/recording/53\n"), "the format was not bumped: ${text.lineSequence().first()}")
        assertTrue(text.lines().any { it.startsWith("E RadioFaulted") && it.contains("|cause=Permission") }, "the cause was not recorded")
        assertTrue(text.lines().any { it.startsWith("E HouseClosedOnPermission") && it.endsWith("|seat=2") }, "the close was not recorded")
        assertTrue(text.lines().any { it.startsWith("F NoticeIssued|unreachable=4|switchedOff=3") }, "the two lists of the notice were not recorded apart (D-311)")
        assertTrue(text.lines().any { it.startsWith("F HouseClosedOnPermission") }, "the close's effect was not recorded")
        assertTrue(recording.stateTranscript.last().contains("|warned=true|closed=true"), "the state row does not say why the round ended")

        val parsed = RecordingText.parse(text)
        assertEquals(events.drop(1), parsed.events.drop(1), "an event did not survive the text form")
        assertEquals(recording.stateTranscript, parsed.stateTranscript)
        val replayed = replay(GameState.EMPTY, parsed)
        assertTrue(replayed is ReplayResult.Identical, "the recorded round did not replay: $replayed")
    }

    /** **GUARD (the differential harness): the warning and the close add no divergence on a role exchange.** */
    @Test
    fun `the warning and the close add nothing to a role exchange's diff`() {
        val diff = differentialOnRoleExchange(GameState.EMPTY, evening(), Seat(1), Seat(3))
        assertEquals(emptyList(), diff.unexplained, "the warning or the close leaked a role: $diff")
    }
}
