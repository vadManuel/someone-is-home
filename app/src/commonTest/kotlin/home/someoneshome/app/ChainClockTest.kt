package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.core.reduce
import home.someoneshome.model.Balance
import home.someoneshome.model.Destination
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The house's clock releases a dependency once its delay has run out, and at no other time**
 * (D-146, `gdd.md:284`, D-322).
 *
 * [HouseClock.turned] on a clock this file made up, for [HouseClockTest]'s reason. What comes due is
 * admitted through the real gate and reduced by the real rules, so a release that the rules refused
 * would show up here as a failed admission or a second mint.
 */
class ChainClockTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val home = (0 until 12).map { Destination(MarkerId("c$it"), shape = "diamond", room = "ROOM $it") }
    private val second = 1_000_000_000L

    private fun armed(): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(Tick(0), seed = 20261121L, seats = seats, insiders = listOf(Seat(1)), markers = home),
    ).state

    /** The round with its first dependency's upstream line completed at tick zero. */
    private fun started(state: GameState): GameState {
        val first = state.links.first()
        return state.withLinks(state.links.map { if (it === first) it.startedAt(Tick(0)) else it })
    }

    private class Ran(val clock: HouseClock, val state: GameState, val minted: List<Event.ChainUnlocked>)

    /** Pump [seconds] of real time, a step at a time, putting every release that falls due to the rules. */
    private fun run(start: GameState, seconds: Int, clock: HouseClock = HouseClock.started()): Ran {
        var held = clock
        var state = start
        val minted = mutableListOf<Event.ChainUnlocked>()
        repeat(seconds * Balance.TICKS_PER_SECOND.toInt()) {
            val turned = held.turned(second / Balance.TICKS_PER_SECOND, state)
            held = turned.next
            for (event in turned.due.filterIsInstance<Event.ChainUnlocked>()) {
                minted += event
                val admitted = admit(state, event)
                assertTrue(admitted is Admission.Admitted, "the clock minted a release the gate refused: $event")
                state = admitted.reduction.state
            }
        }
        return Ran(held, state, minted)
    }

    @Test
    fun `a started dependency is released once when its delay runs out`() {
        val state = started(armed())
        val link = state.links.first()
        val seconds = (link.delay / Balance.TICKS_PER_SECOND).toInt()

        val before = run(state, seconds - 1)
        assertEquals(emptyList(), before.minted, "a dependency was released before its ${seconds}s ran out")
        val after = run(before.state, 5, before.clock)
        assertEquals(listOf(link.down to link.downEntry), after.minted.map { it.seat to it.entry })
        assertTrue(!after.state.workOrderFor(link.down)!!.entries[link.downEntry].held, "the line is still held")
    }

    @Test
    fun `a stranded dependency is never released`() {
        val ran = run(armed(), 600)
        assertEquals(emptyList(), ran.minted, "ten minutes released work nobody completed: nothing auto-satisfies (D-146)")
        assertTrue(ran.state.links.all { ran.state.workOrderFor(it.down)!!.entries[it.downEntry].held })
    }

    @Test
    fun `a release that falls due during a meeting waits for the lights to go out`() {
        val state = started(armed())
        val link = state.links.first()
        val meeting = state.copy(meeting = Meeting.called(Seat(0), MeetingTrigger.MeetingCard))
        val seconds = (link.delay / Balance.TICKS_PER_SECOND).toInt()
        val during = run(meeting, seconds + 10)
        assertEquals(emptyList(), during.minted, "`you're up` landed on a phone standing in a meeting")
        val after = run(state, 1, during.clock)
        assertEquals(1, after.minted.size, "the release did not land once the meeting was over")
    }
}
