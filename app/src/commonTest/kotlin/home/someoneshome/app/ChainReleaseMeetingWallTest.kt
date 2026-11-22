package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.core.reduce
import home.someoneshome.model.Balance
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.RefusalReason
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A release minted before a meeting and gated after it is refused, and lands once the meeting is
 * over** (D-322, D-327; the overnight review of 2026-11-22).
 *
 * `TickDriver.pumped` reads the state without the lock, the clock mints the release off that
 * snapshot, and each due event is gated under its own lock. A phone's meeting card gated in between
 * is not in the snapshot, so the clock's own hold (`state.meeting != null`) does not see it. The gate
 * is the second wall, as it is for the Egress expiry.
 */
class ChainReleaseMeetingWallTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val home = (0 until 12).map { Destination(MarkerId("c$it"), shape = "diamond", room = "ROOM $it") }
    private val second = 1_000_000_000L

    private fun started(): GameState {
        val armed = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 20261121L, seats = seats, insiders = listOf(Seat(1)), markers = home),
        ).state
        val first = armed.links.first()
        return armed.withLinks(armed.links.map { if (it === first) it.startedAt(Tick(0)) else it })
    }

    /** Pump [seconds] off [state] and return the clock with the first release it minted, if any. */
    private fun pump(state: GameState, seconds: Int, from: HouseClock = HouseClock.started()): Pair<HouseClock, Event.ChainUnlocked?> {
        var clock = from
        var release: Event.ChainUnlocked? = null
        repeat(seconds * Balance.TICKS_PER_SECOND.toInt()) {
            val turned = clock.turned(second / Balance.TICKS_PER_SECOND, state)
            clock = turned.next
            if (release == null) release = turned.due.filterIsInstance<Event.ChainUnlocked>().firstOrNull()
        }
        return clock to release
    }

    @Test
    fun `a release minted before a meeting and gated after it is refused`() {
        val started = started()
        val link = started.links.first()
        val seconds = (link.delay / Balance.TICKS_PER_SECOND).toInt() + 1

        // The clock's snapshot, taken before the meeting: the release falls due.
        val (clock, minted) = pump(started, seconds)
        val due = minted ?: error("nothing fell due")

        // A phone's meeting card is gated first (another coroutine, under the same lock).
        val inMeeting = started.copy(meeting = Meeting.called(Seat(0), MeetingTrigger.MeetingCard))

        val admitted = admit(inMeeting, due)
        val effects = (admitted as? Admission.Admitted)?.reduction?.effects.orEmpty()
        assertTrue(
            admitted !is Admission.Admitted,
            "a release reached the rules during a meeting and was admitted: ${effects.map { it::class.simpleName }}" +
                " (WorkUnblocked buzzes ${(effects.firstOrNull { it is Effect.WorkUnblocked } as? Effect.WorkUnblocked)?.haptic})",
        )
        assertEquals(Admission.Refused(RefusalReason.MeetingAlreadyRunning), admitted, "refused for another reason")
        assertTrue(inMeeting.workOrderFor(link.down)!!.entries[link.downEntry].held, "the refused release moved the line")

        // The meeting is over: the clock mints the same release again on its next step, and the gate
        // admits it.
        val (_, again) = pump(started, 1, clock)
        val after = again ?: error("the release was not minted again once the meeting was over")
        assertEquals(due.seat to due.entry, after.seat to after.entry)
        val landed = admit(started, after)
        assertTrue(landed is Admission.Admitted, "the release was refused once the meeting was over: $landed")
        assertTrue(
            landed.reduction.effects.any { it is Effect.WorkUnblocked },
            "the release after the meeting sent no `you're up`",
        )
        assertTrue(!landed.reduction.state.workOrderFor(link.down)!!.entries[link.downEntry].held, "the line is still held")
    }
}
