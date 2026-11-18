package home.someoneshome.app

import home.someoneshome.core.Admission
import home.someoneshome.core.admit
import home.someoneshome.model.Balance
import home.someoneshome.model.Egress
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The host locks during an Egress, and the Egress waits for the house** (the owner's 2026-11-18
 * ruling, Q4 option A; D-281).
 *
 * The driver's own pump ([pumped]) against a house that admits and reduces what it is given, with
 * the real [OffAirMark] the authority holds, on a clock this file made up. Before the ruling the
 * return's first pump skipped the clock past the deadline and minted `EgressExpired`: the Insiders
 * won for a host's lock (D-230).
 */
class OffAirDriverTest {

    private val second = 1_000_000_000L
    private val seats = (0 until 5).map { Seat(it) }

    private class House(var s: GameState) : HouseHandle {
        val mark = OffAirMark()
        val minted = mutableListOf<Event>()
        var tick = 0L
        override fun state(): GameState = s
        override fun advanceTo(at: Tick) {
            if (at.step > tick) tick = at.step
        }
        override suspend fun mint(event: Event) {
            minted += event
            (admit(s, event) as? Admission.Admitted)?.let { s = it.reduction.state }
        }
        override fun offAir(): Boolean = mark.off
        override fun backOnAir(at: Tick): Event.OffAirEnded? = mark.ended(at)
    }

    private fun burning(): House = House(
        home.someoneshome.core.reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1))),
        ).state.copy(
            egress = Egress.fired(
                Tick(0), EgressType.Beacon, listOf(MarkerId("A"), MarkerId("B")), Balance.EGRESS_TIMER,
                pulseFrom = 86_402_000L,
            ),
        ),
    )

    /** [seconds] of real time, a step at a time, as a running driver hands it over. */
    private suspend fun run(house: House, clock: HouseClock, seconds: Int): HouseClock {
        var held = clock
        repeat(seconds * Balance.TICKS_PER_SECOND.toInt()) {
            held = pumped(house, held, second / Balance.TICKS_PER_SECOND)
        }
        return held
    }

    private val timer = (Balance.EGRESS_TIMER / Balance.TICKS_PER_SECOND).toInt()

    /**
     * **GUARD: iOS suspends the host with the driver asleep.** Off the air 100 s into the Egress, back
     * five minutes later; the first pump after the return skips the whole gap in one step. The
     * return is minted, no expiry is, and the Egress then runs out its last 20 s and not before.
     */
    @Test
    fun aHostLockedThroughTheDeadlineDoesNotHandTheRoundToTheInsiders() = runBlocking {
        val house = burning()
        var clock = run(house, HouseClock.started(), 100)
        house.mark.went(Tick(house.tick))
        house.mark.returned()
        clock = pumped(house, clock, 300 * second)

        assertTrue(house.minted.any { it is Event.OffAirEnded }, "the house's return was not recorded")
        assertFalse(house.minted.any { it is Event.EgressExpired }, "the Egress expired on the first step after the host came back")
        assertFalse(house.s.ended, "the round ended on the host's lock")

        clock = run(house, clock, timer - 100 - 2)
        assertFalse(house.minted.any { it is Event.EgressExpired }, "the Egress expired before its own time had run")
        run(house, clock, 4)
        assertTrue(house.minted.any { it is Event.EgressExpired }, "the Egress never expired after the gap")
    }

    /** **The driver kept running through the gap** (a lock iOS did not suspend at once). */
    @Test
    fun aDriverThatRanThroughTheGapMintsNoExpiryWhileTheHouseIsOff() = runBlocking {
        val house = burning()
        var clock = run(house, HouseClock.started(), 100)
        house.mark.went(Tick(house.tick))
        clock = run(house, clock, 200)
        assertFalse(house.minted.any { it is Event.EgressExpired }, "the Egress expired while the house was off the air")

        house.mark.returned()
        clock = run(house, clock, 1)
        val back = house.minted.filterIsInstance<Event.OffAirEnded>().single()
        // The return is recorded on the first pump after it, one step past the gap's last.
        assertEquals(200L * Balance.TICKS_PER_SECOND + 1, back.at.step - back.since.step, "the gap recorded was not the gap")
        run(house, clock, timer - 100 - 3)
        assertFalse(house.minted.any { it is Event.EgressExpired }, "the Egress lost time to the gap")
    }
}
