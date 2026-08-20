package home.someoneshome.harness

import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Story 0.6. Seeded round, run twice, one role swapped, transcripts diffed. */
class DifferentialTest {

    /**
     * **The instrument checks itself first.**
     *
     * Four instruments in this project have reported a confident pass while measuring nothing.
     * A swap that did not change the round would make every assertion below vacuous, so the first
     * thing asserted is that the two runs are genuinely different rounds.
     */
    @Test
    fun `the swap actually changes the round`() {
        val baseline = round()
        val variant = withRoleSwapped(baseline, Seat(1))
        assertNotEquals(baseline, variant, "withRoleSwapped returned the same events")

        val armedA = baseline.filterIsInstance<Event.RoundArmed>().single()
        val armedB = variant.filterIsInstance<Event.RoundArmed>().single()
        assertEquals(listOf(1, 5), armedA.insiders.map { it.index })
        assertEquals(listOf(5), armedB.insiders.map { it.index })

        val stateA = record(GameState.EMPTY, baseline).first
        val stateB = record(GameState.EMPTY, variant).first
        assertNotEquals(
            Transcript.render(stateA),
            Transcript.render(stateB),
            "the two runs ended in the same authority state; the harness is measuring nothing",
        )
    }

    /** The swap touches only the arming event — nothing else in the round is rewritten. */
    @Test
    fun `the swap rewrites only the arming event`() {
        val baseline = round()
        val variant = withRoleSwapped(baseline, Seat(1))
        assertEquals(baseline.size, variant.size)
        val differing = baseline.indices.filter { baseline[it] != variant[it] }
        assertEquals(listOf(0), differing)
    }

    /** Exchanging two seats keeps the Insider count fixed. */
    @Test
    fun `an exchange preserves the number of Insiders`() {
        val armed = withRolesExchanged(round(), Seat(1), Seat(3))
            .filterIsInstance<Event.RoundArmed>().single()
        assertEquals(listOf(3, 5), armed.insiders.map { it.index })
    }

    /**
     * Exchanging two seats that already hold the same role changes nothing, and the count is
     * preserved because neither moved. Toggling each in turn instead flips both off.
     */
    @Test
    fun `exchanging two seats of the same role changes no roles`() {
        val armed = withRolesExchanged(round(), Seat(1), Seat(5))
            .filterIsInstance<Event.RoundArmed>().single()
        assertEquals(listOf(1, 5), armed.insiders.map { it.index })
    }

    /**
     * **A swap that changed nothing must be refused, not reported clean.**
     *
     * Every verdict this file produces is a claim about two DIFFERENT runs. A swap that leaves
     * the round identical makes `unexplained` empty for the one reason that means nothing, and
     * the report is indistinguishable from a real pass. Three ways in: no arming event in the
     * list, a seat that is not seated, and an exchange between two seats of the same role.
     */
    @Test
    fun `a swap with no arming event to rewrite is refused`() {
        val noArming = round().filterNot { it is Event.RoundArmed }
        assertFailsWith<IllegalArgumentException> {
            differentialOnRoleSwap(GameState.EMPTY, noArming, Seat(1))
        }
    }

    @Test
    fun `swapping a seat that is not seated is refused`() {
        assertFailsWith<IllegalArgumentException> {
            differentialOnRoleSwap(GameState.EMPTY, round(), Seat(99))
        }
    }

    @Test
    fun `exchanging two seats of the same role is refused`() {
        assertFailsWith<IllegalArgumentException> {
            differentialOnRoleExchange(GameState.EMPTY, round(), Seat(1), Seat(5))
        }
    }

    /**
     * A seat present in one run and absent from the other is a divergence in its own right, at
     * index -1, rather than something inferred from its lines. Driven through [diff] directly
     * because a role swap cannot produce it — the seat list is the same event in both runs.
     */
    @Test
    fun `a seat present in only one run is reported as a divergence`() {
        val baseline = round()
        val shortSeated = baseline.map { e ->
            if (e !is Event.RoundArmed) e else e.copy(seats = e.seats.filterNot { it.index == 6 })
        }
        val result = diff(GameState.EMPTY, baseline, shortSeated, emptyList())
        assertTrue(
            result.divergences.any { it.seat.index == 6 && it.index == -1 },
            "seat 6 vanished between runs and no presence divergence reported it",
        )
    }

    /**
     * The acceptance criterion: zero unexplained divergence.
     *
     * The swapped seat's own transcript is expected to differ and does — seat 1 is the acting
     * Insider, and AbilityFired is permitted to Insider/Live and to no other class, so as a
     * Resident seat 1 stops receiving it. That divergence is at the swapped seat, which is the
     * one place a role is allowed to show.
     */
    @Test
    fun `a role swap leaks to nobody else`() {
        val result = differentialOnRoleSwap(GameState.EMPTY, round(), Seat(1))
        assertEquals(emptyList(), result.unexplained, "leaked: $result")
        assertFalse(result.leaked)
        assertTrue(
            result.divergences.isNotEmpty(),
            "not one line differed anywhere, including at the swapped seat — the diff is not " +
                "reading the transcripts",
        )
        assertTrue(result.divergences.all { it.seat.index == 1 })
    }

    /** The count-preserving form, on two seats that both act during the round. */
    @Test
    fun `a role exchange leaks to nobody else`() {
        val result = differentialOnRoleExchange(GameState.EMPTY, round(), Seat(1), Seat(3))
        assertEquals(emptyList(), result.unexplained, "leaked: $result")
    }

    /**
     * Swapping a seat that acts in no way at all still produces two real runs, and still nothing
     * escapes. Guards against a diff that only ever compares the busy seats.
     */
    @Test
    fun `swapping a passive seat leaks to nobody`() {
        val result = differentialOnRoleSwap(GameState.EMPTY, round(), Seat(6))
        assertEquals(emptyList(), result.unexplained, "leaked: $result")
    }

    /**
     * **Stated because a green result here is currently weak evidence.**
     *
     * `reduce` never reads a role, so no effect can depend on one and this harness has nothing to
     * find. The assertion below is what makes that claim checkable rather than a comment: the
     * moment a rule branches on role, it fails and this test needs rewriting — which is exactly
     * the moment the harness above starts being worth running.
     */
    @Test
    fun `role is still inert in the rules`() {
        val baseline = effectsOf(GameState.EMPTY, round()).map { Transcript.render(it) }
        val variant = effectsOf(GameState.EMPTY, withRoleSwapped(round(), Seat(1)))
            .map { Transcript.render(it) }
        assertEquals(
            baseline, variant,
            "a rule now branches on role. The differential harness has become load-bearing; " +
                "rewrite this test rather than deleting it.",
        )
    }
}
