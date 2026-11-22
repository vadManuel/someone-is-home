package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.PanelVals
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Each Insider's phone is told the round's Insider count at arming, and page 2's EGRESS line
 * follows it** (the owner's 2026-11-22 ruling, D-323 B: *just because the phone knows doesn't mean
 * the player itself does … we can share any info with player phones*).
 *
 * Real: the rules and their draw, `EmitSchema`'s allowlist, `RoundWire.project` and the frame's
 * encoding, `FlowModel`. What stands in is the socket, as in [StructuredWorkBenchTest]: every
 * projected message is encoded, decoded and handed to the addressed seat's model.
 *
 * The case D-320 stopped on is the point: **UNKNOWN at seven to eleven seats**, where the band
 * holds one and two and no lobby fact settles which. Swept over seeds until both have been drawn,
 * the line is hidden on the one Insider's page 2 and shown on both of two, and no Resident's moves.
 */
class EgressSharedCountTest {

    private class Bench(val seats: List<Seat>, chosen: Int?) {
        val phones: Map<Seat, FlowModel> = seats.associateWith { FlowModel(PanelState(screen = ScreenId.Boot)) }
        val reveals: MutableMap<Seat, String> = mutableMapOf()

        val house: RoundAuthority = RoundAuthority(
            inputs = {
                ArmingInputs(
                    seats = seats,
                    names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
                    oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
                    chosenInsiders = chosen,
                    voteWindowSeconds = 45,
                    readyToArm = true,
                    home = BenchHome.home,
                    discussionSeconds = Balance.DISCUSSION_SECONDS,
                    egressTimerSeconds = Balance.EGRESS_TIMER_SECONDS,
                    revokeCooldownSeconds = Balance.REVOKE_COOLDOWN_SECONDS,
                )
            },
            emit = { effect, state -> emit(effect, state) },
            note = {},
        )

        private fun emit(effect: Effect, state: GameState) {
            val message = RoundWire.project(effect)
            for (delivery in EmitSchema.deliveries(effect, state)) {
                val frame = RoundWire.encode(home.someoneshome.model.protocol.RoundBody.Push(1, message))
                if (message is RoundMessage.RoleRevealed) reveals[delivery.seat] = frame
                val heard = (RoundWire.decodeOrNull(frame) as home.someoneshome.model.protocol.RoundBody.Push).message
                phones[delivery.seat]?.houseSaid(heard)
            }
        }

        fun line(seat: Seat): String = PanelVals(phones.getValue(seat).state.copy(screen = ScreenId.Page2)).secondSub
    }

    /** Arm a round of [party] seats with the host's INSIDERS at [chosen] and check every page 2. */
    private fun round(party: Int, chosen: Int?, seed: Long): Int = runBlocking {
        val bench = Bench(List(party) { Seat(it) }, chosen)
        assertTrue(bench.house.arm(seed), "the bench never armed a round of $party")
        val state = bench.house.state
        val insiders = bench.seats.filter { state.roleOf(it) == Role.Insider }
        val count = insiders.size
        val expected = if (count >= 2) "SHARED WITH THE OTHER INSIDER" else ""
        for (seat in bench.seats) {
            val said = if (seat in insiders) expected else "ALL SYSTEMS RESPONDING"
            assertEquals(
                said, bench.line(seat),
                "$party seats (INSIDERS ${chosen ?: "UNKNOWN"}, seed $seed, $count drawn): seat ${seat.index}'s page 2",
            )
            val told = bench.phones.getValue(seat).state.insiderCount
            assertEquals(if (seat in insiders) count else null, told, "seat ${seat.index} holds the wrong count")
        }
        // **One length on the wire for every seat and both roles** (D-249).
        assertEquals(1, bench.reveals.values.map { it.length }.distinct().size, "the reveals are not one length: ${bench.reveals.mapValues { it.value.length }}")
        count
    }

    /** **GUARD: one Insider hides the line, at three to six seats where the band is one.** */
    @Test
    fun oneInsiderHidesTheLine() {
        for (party in 3..6) assertEquals(1, round(party, chosen = null, seed = 20261122L))
        assertEquals(1, round(8, chosen = 1, seed = 20261122L), "the host's one at eight seats")
    }

    /** **GUARD: two Insiders show the line**, the host's two at eight seats and UNKNOWN at twelve. */
    @Test
    fun twoInsidersShowTheLine() {
        assertEquals(2, round(8, chosen = 2, seed = 20261122L))
        assertTrue(round(12, chosen = null, seed = 20261122L) >= 2, "twelve seats drew fewer than two")
    }

    /**
     * **GUARD: UNKNOWN at seven to eleven seats, where D-320 stopped**: the count is the house's
     * draw and each Insider's phone follows it, one or two. The sweep must see both.
     */
    @Test
    fun unknownAtSevenToElevenSeatsFollowsTheDraw() {
        for (party in 7..11) {
            val seen = mutableSetOf<Int>()
            var seed = 1L
            while (!(1 in seen && 2 in seen) && seed <= 200L) seen += round(party, chosen = null, seed = seed++)
            assertTrue(1 in seen && 2 in seen, "$party seats UNKNOWN drew only $seen over ${seed - 1} seeds")
        }
    }
}
