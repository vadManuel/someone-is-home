package home.someoneshome.harness

import home.someoneshome.model.Balance
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The fixture round actually holds an Egress, and it goes all the way through.**
 *
 * A fixture that *contains* an Egress event list but never gets one admitted is the failure this
 * guards: the cooldown refuses the fire, the meeting refuses the pause, the beats grade false, and
 * every replay and differential assertion downstream goes on passing over a round in which the
 * whole system never ran. The same trap D-132's cooldown already sprang on the Revoke half of this
 * fixture once.
 */
class EgressFixtureTest {

    @Test
    fun `the fixture round fires and pauses and resumes and contains an Egress`() {
        val (_, recording) = record(GameState.EMPTY, round())
        val effects = recording.effectTranscript

        assertTrue(
            effects.any { it.startsWith("EgressOpened|") },
            "the fixture's Egress never started; the shared cooldown refused it",
        )
        assertTrue(
            effects.any { it.startsWith("EgressHeld|") && it.contains("running=false") },
            "the report meeting did not pause the countdown",
        )
        assertTrue(
            effects.any { it.startsWith("EgressHeld|") && it.contains("running=true") },
            "the countdown never resumed after the meeting",
        )
        assertEquals(
            1, effects.count { it.startsWith("EgressContained|") },
            "the fixture's pair did not contain the Egress they were sent to contain",
        )
        assertTrue(
            effects.none { it.startsWith("EgressSucceeded|") },
            "the fixture's Egress both contained and expired",
        )
    }

    /**
     * **The pause moved the deadline by the length of the meeting and by nothing else** (D-133).
     *
     * Read off the recorded state rows rather than off the effects, because that is the half a
     * reset would get wrong invisibly: `deadline = now + EGRESS_TIMER` produces exactly the same
     * effect stream as `deadline += meeting`, and only the state row can tell them apart.
     */
    @Test
    fun `the recorded deadline moves by the meeting and not by a reset`() {
        val events = round()
        val (_, recording) = record(GameState.EMPTY, events)

        val fired = events.filterIsInstance<Event.EgressFired>().single()
        val called = events.indexOfFirst { it is Event.EgressFired }.let { at ->
            events.drop(at).filterIsInstance<Event.MeetingCalled>().first()
        }
        val closed = events.dropWhile { it !is Event.EgressFired }
            .filterIsInstance<Event.MeetingClosed>().first()

        val paused = recording.stateTranscript.mapNotNull { deadlineOf(it) }.distinct()
        val expected = fired.at.step + Balance.EGRESS_TIMER
        assertTrue(expected in paused, "the Egress never held its opening deadline")
        assertTrue(
            expected + (closed.at.step - called.at.step) in paused,
            "the deadline did not move by exactly the length of the meeting; it moved to $paused",
        )
    }

    /** The deadline out of a recorded state row, or null for a row with no Egress on it. */
    private fun deadlineOf(row: String): Long? {
        val egress = row.split('|').firstOrNull { it.startsWith("egress=") }?.removePrefix("egress=")
        if (egress == null || egress == "none") return null
        return egress.split(':').firstOrNull { it.startsWith("deadline=") }
            ?.removePrefix("deadline=")?.toLongOrNull()
    }

    /**
     * **The pause mark and the held offers are on the state row, and nothing else can hold them.**
     *
     * Neither reaches a client. `EgressHeld` carries a *remaining* count and never the mark it was
     * computed from, and a held offer is who is standing at which node with a live beat — presence
     * data by another name, which the allowlist gives no row to at all. So a state row is the only
     * artifact either appears in, and without them a build whose timer silently kept running
     * through a meeting, or whose pairs never formed, would replay clean forever.
     *
     * Asserted as *the field is on the row and moves*, because replay compares this build against
     * itself: a field dropped from the renderer is dropped from both sides and diverges nothing.
     */
    @Test
    fun `the state row carries the pause mark and the held offers`() {
        val rows = record(GameState.EMPTY, round()).second.stateTranscript
        assertTrue(
            rows.any { it.contains(":paused=") && !it.contains(":paused=none") },
            "no state row records the moment the countdown stopped; a build that kept running " +
                "through a meeting replays as correct",
        )
        assertTrue(
            rows.any { row -> egressField(row, "offers")?.isNotEmpty() == true },
            "no state row records a held Sync Pulse offer; a build whose pairs never formed " +
                "replays as correct",
        )
        assertTrue(
            rows.any { it.contains("|egressReady=") },
            "the shared Egress cooldown is not on the state row",
        )
    }

    /** One colon-separated field out of a recorded `egress=` value, or null if there is no Egress. */
    private fun egressField(row: String, name: String): String? {
        val egress = row.split('|').firstOrNull { it.startsWith("egress=") }?.removePrefix("egress=")
        if (egress == null || egress == "none") return null
        return egress.split(':').firstOrNull { it.startsWith("$name=") }?.removePrefix("$name=")
    }
}
