package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **The host's DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN are stamped into the recording, and the
 * round replays on them** (D-037, the owner's 2026-11-21 ruling, D-317; recording 48).
 *
 * *Balance values lock at arming and are stamped into the recording.* Armed here at numbers the
 * defaults are not, so a recording that dropped one and a parser that defaulted it would replay a
 * different round, and the replay check says so.
 */
class LobbySettingsRecordingTest {

    private fun withSettings(): List<Event> = round().map {
        if (it is Event.RoundArmed) it.copy(discussionSeconds = 60, egressTimerSeconds = 60, revokeCooldownSeconds = 30) else it
    }

    /** **GUARD (rule 4): each is on the arming row and the state rows, survives the text form, and replays.** */
    @Test
    fun `each setting is stamped into the recording and replays exactly`() {
        val events = withSettings()
        val (end, recording) = record(GameState.EMPTY, events)
        assertEquals(60, end.discussionSeconds)
        assertEquals(60, end.egressTimerSeconds)
        assertEquals(30, end.revokeCooldownSeconds)

        val text = recording.toText()
        assertTrue(text.startsWith("someone-is-home/recording/53\n"), "the format was not bumped: ${text.lineSequence().first()}")
        val armingLine = text.lines().first { it.startsWith("E RoundArmed") }
        for (field in listOf("|discussion=60", "|egressTimer=60", "|revokeCooldown=30")) {
            assertTrue(field in armingLine, "the arming row does not stamp $field: $armingLine")
            assertTrue(recording.stateTranscript.all { field in it }, "a state row does not carry $field")
        }

        val parsed = RecordingText.parse(text)
        val armed = parsed.events.first() as Event.RoundArmed
        assertEquals(listOf(60, 60, 30), listOf(armed.discussionSeconds, armed.egressTimerSeconds, armed.revokeCooldownSeconds))
        assertEquals(events.drop(1), parsed.events.drop(1), "an event did not survive the text form")
        val replayed = replay(GameState.EMPTY, parsed)
        assertTrue(replayed is ReplayResult.Identical, "the recorded round did not replay: $replayed")
    }

    /** **GUARD: the parser defaults nothing; an arming row missing any one of the three is fatal** (a version 46 row). */
    @Test
    fun `an arming row without one of the settings is fatal`() {
        val text = record(GameState.EMPTY, withSettings()).second.toText()
        val armingLine = text.lines().first { it.startsWith("E RoundArmed") }
        for (field in listOf("discussion", "egressTimer", "revokeCooldown")) {
            val broken = armingLine.replace(Regex("""\|$field=[^|]*"""), "")
            check(broken != armingLine) { "the injection changed nothing for $field" }
            assertFailsWith<MalformedRecording>("an arming row without $field was read") {
                RecordingText.parse(text.replace(armingLine, broken))
            }
        }
    }

    /**
     * **GUARD (rule 2): the settings put no role on any client's wire.** They reach every phone and
     * are the same number on every phone, so a role exchange under them leaves nothing unexplained.
     */
    @Test
    fun `the settings add nothing unexplained to a role exchange's diff`() {
        // **The meter subtracted, as `DifferentialTest` subtracts it** (D-109's ruled asymmetry). Since
        // D-322 the fixture walks circuits and Memory Dumps as well as Subroutines, so the two exchanged
        // seats bank at different moments and the meter moves on different lines; what is asked here is
        // whether anything ELSE follows the role, and `DifferentialTest` owns the meter's own reach.
        val with = differentialOnRoleExchange(GameState.EMPTY, withSettings(), Seat(1), Seat(3), ignoring = METER_ASYMMETRY)
        assertEquals(emptyList(), with.unexplained, "a setting leaked a role: $with")
    }

    /** **GUARD (rule 2): every seat is sent the same locked cooldown**, at LIGHTS OUT and at every meeting's end. */
    @Test
    fun `every seat is sent the same locked cooldown`() {
        val clients = recordPerClient(GameState.EMPTY, withSettings())
        val pushes = SEATS.map { seat ->
            clients.linesFor(seat).filter { it.startsWith("OpeningMessage|") || it.startsWith("MeetingEnded|") }
                .map { it.substringAfter("|revokeCooldown=") }
        }
        assertTrue(pushes.first().isNotEmpty(), "no seat was sent the cooldown at all")
        assertTrue(pushes.first().all { it == "600" }, "a 30 s cooldown was sent as ${pushes.first()}")
        assertEquals(1, pushes.distinct().size, "two seats were sent different cooldowns: $pushes")
    }
}
