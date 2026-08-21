package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Story 0.10d. Fixtures the rules produced, not fixtures somebody imagined. */
class FixturesTest {

    private val marks = listOf(
        Marks.ARMED, Marks.FIRST_PROGRESS, Marks.FIRST_REVOCATION, Marks.FIRST_MEETING,
    )

    private fun taken() = snapshots(GameState.EMPTY, round(), marks)

    @Test
    fun `every mark finds its moment and they come back in order asked`() {
        val found = taken()
        assertEquals(marks.map { it.label }, found.map { it.label })
    }

    /** A mark fires on the EDGE, so it lands on the first occurrence and not on every later one. */
    @Test
    fun `a mark lands on the first occurrence`() {
        val found = taken().associateBy { it.label }
        val armed = found.getValue("armed")
        assertEquals(0, armed.afterEvent, "arming is the first event of the round")
        assertTrue(armed.state.armed)

        val firstRevocation = found.getValue("first-revocation")
        assertEquals(1, firstRevocation.state.revoked.size, "landed after the first revocation")
    }

    /**
     * **The property that makes this worth having.** A snapshot is derived, not remembered:
     * rebuilding it from the recording reproduces it exactly, so it cannot drift away from the
     * rules while continuing to certify them.
     */
    @Test
    fun `every snapshot regenerates from its recording`() {
        for (snapshot in taken()) {
            assertEquals(
                Transcript.render(snapshot.state),
                Transcript.render(regenerate(GameState.EMPTY, round(), snapshot)),
                "${snapshot.label} could not be rebuilt from its own recording",
            )
        }
    }

    /** They come out of a Recording too, which is what a fixture will actually be built from. */
    @Test
    fun `snapshots can be taken from a parsed recording`() {
        val text = record(GameState.EMPTY, round()).second.toText()
        val fromText = snapshots(RecordingText.parse(text), marks)
        assertEquals(
            taken().map { Transcript.render(it.state) },
            fromText.map { Transcript.render(it.state) },
        )
    }

    /**
     * **A fixture set that silently comes back short is the failure this project keeps having.**
     * A test asserting against a dropped fixture passes for the one reason that means nothing.
     */
    @Test
    fun `a mark that matches nothing is fatal`() {
        val impossible = Mark("nobody-is-ever-revoked-twice") { before, after, _ ->
            after.revoked.size > before.revoked.size + 5
        }
        val failure = assertFailsWith<UnmatchedMarks> {
            snapshots(GameState.EMPTY, round(), listOf(Marks.ARMED, impossible))
        }
        assertEquals(listOf("nobody-is-ever-revoked-twice"), failure.labels)
    }

    /** A parameterised mark that can be pushed past what the round reaches, and is caught. */
    @Test
    fun `a threshold the round never reaches is fatal`() {
        assertTrue(snapshots(GameState.EMPTY, round(), listOf(Marks.integrityAtOrBelow(30))).isNotEmpty())
        assertFailsWith<UnmatchedMarks> {
            snapshots(GameState.EMPTY, round(), listOf(Marks.integrityAtOrBelow(0)))
        }
    }

    /** Two marks sharing a label would silently keep one. Refused at the door. */
    @Test
    fun `duplicate labels are refused`() {
        assertFailsWith<IllegalArgumentException> {
            snapshots(GameState.EMPTY, round(), listOf(Marks.ARMED, Marks.ARMED))
        }
    }

    @Test
    fun `an empty mark list is refused`() {
        assertFailsWith<IllegalArgumentException> { snapshots(GameState.EMPTY, round(), emptyList()) }
    }

    /**
     * An event the admission gate turned away never reaches a predicate. A snapshot taken at a
     * refused event would describe a moment the rules never saw.
     */
    @Test
    fun `a refused event cannot produce a snapshot`() {
        val events = listOf(Event.MeetingCalled(Tick(0), Seat(2), MeetingTrigger.MeetingCard)) + round()
        val found = snapshots(GameState.EMPTY, events, listOf(Marks.FIRST_MEETING))
        assertTrue(found[0].afterEvent > 0, "snapshot taken at the refused pre-arm meeting")
        assertTrue(found[0].state.armed)
    }

    /**
     * The index is into the ORIGINAL event list, including refused events — otherwise
     * [regenerate] would rebuild from a different prefix than the one that was walked.
     */
    @Test
    fun `the index survives refused events for regeneration`() {
        val events = listOf(Event.MeetingCalled(Tick(0), Seat(2), MeetingTrigger.MeetingCard)) + round()
        for (snapshot in snapshots(GameState.EMPTY, events, marks)) {
            assertEquals(
                Transcript.render(snapshot.state),
                Transcript.render(regenerate(GameState.EMPTY, events, snapshot)),
                "${snapshot.label} rebuilt from the wrong prefix",
            )
        }
    }
}
