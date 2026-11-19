package home.someoneshome.harness

import home.someoneshome.model.ContactSource
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **The round's contact source is a recorded input, it replays, and it puts nothing on any client's
 * wire** (D-301; recording 43).
 *
 * The house chooses at arming whether the round's rules read a touch, a report and the spectator map
 * from Bluetooth RSSI or from UWB distance, from whether every phone can range, and stamps it on the
 * arming. A replay must read what the evening read. No phone is told it, whatever its role.
 */
class ContactSourceRecordingTest {

    private fun reading(source: ContactSource): List<Event> =
        round().map { if (it is Event.RoundArmed) it.copy(contactSource = source) else it }

    /** **GUARD (rule 4): the source survives the text form, the state rows hold it, and the round replays.** */
    @Test
    fun `the contact source is recorded and replays exactly`() {
        for ((source, word) in listOf(ContactSource.Uwb to "uwb", ContactSource.Rssi to "rssi")) {
            val events = reading(source)
            val (end, recording) = record(GameState.EMPTY, events)
            assertEquals(source, end.contactSource, "the round did not hold its contact source")
            val text = recording.toText()
            assertTrue(text.startsWith("someone-is-home/recording/43\n"), "the format was not bumped: ${text.lineSequence().first()}")
            val armingLine = text.lines().first { it.startsWith("E RoundArmed") }
            assertTrue(armingLine.endsWith("|contacts=$word"), "the arming row does not carry the source: $armingLine")
            assertTrue(recording.stateTranscript.last().contains("|contacts=$word|"), "the state row does not carry the source")

            val parsed = RecordingText.parse(text)
            assertEquals(source, (parsed.events.first() as Event.RoundArmed).contactSource, "the source did not survive the text form")
            assertEquals(events.drop(1), parsed.events.drop(1), "an event did not survive the text form")
            assertEquals(recording.stateTranscript, parsed.stateTranscript)
            val replayed = replay(GameState.EMPTY, parsed)
            assertTrue(replayed is ReplayResult.Identical, "the recorded round did not replay: $replayed")
        }
    }

    /** **GUARD: the parser defaults nothing; an arming row without the source, or with another word, is fatal** (a version 42 row). */
    @Test
    fun `an arming row without the source is fatal`() {
        val text = record(GameState.EMPTY, reading(ContactSource.Uwb)).second.toText()
        val armingLine = text.lines().first { it.startsWith("E RoundArmed") }
        val missing = armingLine.replace(Regex("""\|contacts=[^|]*"""), "")
        check(missing != armingLine) { "the injection changed nothing" }
        assertFailsWith<MalformedRecording> { RecordingText.parse(text.replace(armingLine, missing)) }
        val other = armingLine.replace("|contacts=uwb", "|contacts=bluetooth")
        check(other != armingLine) { "the injection changed nothing" }
        assertFailsWith<MalformedRecording> { RecordingText.parse(text.replace(armingLine, other)) }
    }

    /**
     * **GUARD (rule 2): the source puts NOTHING on any client's wire.** Every seat's transcript, built by
     * the allowlist (`recordPerClient`), is byte-identical between the round that reads distance and
     * the same round that reads Bluetooth, and the authority's effects are the same rows. (The fixture
     * round mints no contact; what a contact does under either source is the resolver's tests'.)
     */
    @Test
    fun `no client is sent one byte for the contact source`() {
        val uwb = recordPerClient(GameState.EMPTY, reading(ContactSource.Uwb))
        val rssi = recordPerClient(GameState.EMPTY, reading(ContactSource.Rssi))
        assertEquals(SEATS.size, uwb.perClient.size)
        for (seat in SEATS) {
            assertEquals(rssi.linesFor(seat), uwb.linesFor(seat), "seat ${seat.index} was sent something for the contact source")
        }
        assertEquals(
            effectsOf(GameState.EMPTY, reading(ContactSource.Rssi)).map { Transcript.render(it) },
            effectsOf(GameState.EMPTY, reading(ContactSource.Uwb)).map { Transcript.render(it) },
            "the authority produced an effect for the contact source",
        )
    }

    /** **GUARD (the differential harness): the source adds no divergence on a role exchange.** */
    @Test
    fun `the contact source adds nothing to a role exchange's diff`() {
        val uwb = differentialOnRoleExchange(GameState.EMPTY, reading(ContactSource.Uwb), Seat(1), Seat(3))
        val rssi = differentialOnRoleExchange(GameState.EMPTY, reading(ContactSource.Rssi), Seat(1), Seat(3))
        assertEquals(emptyList(), uwb.unexplained, "the contact source leaked a role: $uwb")
        assertEquals(rssi.divergences.map { it.toString() }, uwb.divergences.map { it.toString() })
    }
}
