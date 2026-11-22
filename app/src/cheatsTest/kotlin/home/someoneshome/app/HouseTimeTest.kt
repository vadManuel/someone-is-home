package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Effect
import home.someoneshome.model.Intent
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.RadioCause
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.SightingUpload
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The house's clock is the driver's, however many phones are talking to it** (V4 of the second
 * review, D-269).
 *
 * Every arrival used to be stamped `tick++`, one tick on top of the driver's twenty a second. At
 * eighteen seats the uploads alone are eighteen a second, so the house's clock ran nearly twice as
 * fast as the room's: cooldowns ended early, the contact window's 2.5 s was about 1.3 s, and an
 * Egress deadline the driver judged on its own clock was stamped on this one.
 *
 * The real [RoundAuthority], armed with eighteen seats in the bench's home. The driver is stood in
 * for exactly as `tickHouse` drives it: twenty steps a second, through [RoundAuthority.advanceTo].
 * Each second every seat uploads once, on its own seed, and half of them press something the rules
 * refuse; one phone reports a radio fault.
 */
class HouseTimeTest {

    private val seats = List(18) { Seat(it) }
    private val window = ContactWindow.containing(1_763_400_000_000L)

    private fun inputs() = ArmingInputs(
        seats = seats,
        names = seats.map { SeatNamed(it, "SEAT${it.index}") },
        oneLines = seats.map { OneLineHeld(it, "line ${it.index}") },
        chosenInsiders = null,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
        discussionSeconds = Balance.DISCUSSION_SECONDS,
        egressTimerSeconds = Balance.EGRESS_TIMER_SECONDS,
        revokeCooldownSeconds = Balance.REVOKE_COOLDOWN_SECONDS,
    )

    /**
     * **GUARD (V4, D-269): eighteen seats' arrivals for ten seconds move the house's clock ten seconds,
     * no more**, and every arrival is stamped with the tick the driver last set.
     */
    @Test
    fun eighteenSeatsArrivalsDoNotRunTheHouseClockFast() = runBlocking<Unit> {
        val pushed = mutableListOf<Effect>()
        val said = mutableListOf<String>()
        val house = RoundAuthority(
            inputs = ::inputs,
            emit = { effect, _ -> pushed += effect },
            contactWindow = { window },
            note = { said += it },
        )
        assertTrue(house.arm(SEED), "eighteen seats never armed: $said")
        val seeds = pushed.filterIsInstance<Effect.ContactSeedIssued>().associate { it.seat.index to it.seed }
        assertEquals(seats.size, seeds.size, "not every seat was given a seed")

        val start = house.clock
        var uploads = 0L
        for (second in 1..SECONDS) {
            // The driver's second: twenty steps.
            repeat(Balance.TICKS_PER_SECOND.toInt()) { house.advanceTo(Tick(house.clock + 1)) }
            val driverAt = house.clock
            for (seat in seats) {
                house.sighted(seat, SightingUpload.pack(seeds.getValue(seat.index), window, uploads, emptyList()))
                if (seat.index % 2 == 0) house.heard(Intent.CheckIn(seat))
            }
            uploads++
            if (second == 3L) house.radioFaulted(seats.last(), RadioCause.Person, "0123456789abcdef")
            assertEquals(driverAt, house.clock, "second $second: the arrivals moved the house's clock past the driver's")
        }

        val ran = house.clock - start
        println("V4 HOUSE CLOCK — ${SECONDS}S OF DRIVER, ${seats.size} SEATS · RAN $ran TICKS (${Balance.TICKS_PER_SECOND * SECONDS} EXPECTED)")
        assertEquals(Balance.TICKS_PER_SECOND * SECONDS, ran, "the house's clock ran $ran ticks in $SECONDS seconds of driver")
        // The fault the house put to the rules was stamped with the driver's tick in its second.
        val fault = house.state.unreachable
        assertTrue(fault.isNotEmpty(), "the radio fault never reached the rules, so its stamp was not measured")
    }

    private companion object {
        const val SEED = 20261117L
        const val SECONDS = 10L
    }
}
