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
     * **The acceptance criterion, and it now runs on the exchange form.**
     *
     * Two seats trade roles, so the Insider count is identical in both runs. Every seat other
     * than the two that traded receives byte-identical bytes.
     *
     * The toggle form no longer isolates role — see below. This is why `withRolesExchanged` was
     * called the stronger variant before there was any reason to prefer it.
     */
    @Test
    fun `a role exchange leaks to nobody else`() {
        val result = differentialOnRoleExchange(GameState.EMPTY, round(), Seat(1), Seat(3))
        assertEquals(emptyList(), result.unexplained, "leaked: $result")
        assertFalse(result.leaked)
        assertTrue(
            result.divergences.isNotEmpty(),
            "not one line differed anywhere, including at the two swapped seats — the diff is " +
                "not reading the transcripts",
        )
        assertTrue(result.divergences.all { it.seat.index == 1 || it.seat.index == 3 })
    }

    /** Two seats that take no ability action all round. Guards against only ever diffing the busy ones. */
    @Test
    fun `exchanging two passive seats leaks to nobody`() {
        val result = differentialOnRoleExchange(GameState.EMPTY, round(), Seat(5), Seat(6))
        assertEquals(emptyList(), result.unexplained, "leaked: $result")
    }

    /**
     * **The toggle form is now confounded, and this records why rather than deleting it.**
     *
     * Toggling one seat changes the number of Insiders, and the SystemIntegrity denominator is
     * `7 x initial_residents` — so the two runs are playing for a different bar and every out
     * player's progress line differs. That is a world change, not a tell: the Insider count is a
     * host-set lobby option (gdd.md:655, :875), public to everyone before the round starts, and
     * a balance value that locks at arming.
     *
     * The harness cannot know that, so it reports the divergence and is right to. The judgement
     * that it is explained belongs here, in a test that names the reason.
     */
    @Test
    fun `toggling one role moves a public balance value and the diff says so`() {
        val toggled = differentialOnRoleSwap(GameState.EMPTY, round(), Seat(1))
        assertTrue(
            toggled.unexplained.isNotEmpty(),
            "toggling changed the Insider count and therefore the denominator; if this is now " +
                "clean, the denominator has stopped reading the count and the test needs revisiting",
        )
        assertTrue(
            toggled.unexplained.all { it.baseline?.startsWith("SubroutineProgressed") == true },
            "toggling diverged in something other than the progress bar: " +
                "${toggled.unexplained.take(3)}",
        )
        assertEquals(
            emptyList(),
            differentialOnRoleExchange(GameState.EMPTY, round(), Seat(1), Seat(3)).unexplained,
            "the same seat, count preserved, must be clean",
        )
    }

    /**
     * **The successor to `role is still inert in the rules`, which fired the day the denominator
     * was fixed.**
     *
     * Role is now read by exactly one rule: the SystemIntegrity denominator counts Residents. So
     * the inertness claim is dead, and this is the sharper property that replaces it — *with the
     * Insider count held fixed, no effect depends on WHICH seats hold the role.*
     *
     * That is the invariant the whole differential harness rests on. The day it fails, a rule has
     * started branching on identity rather than on population, and the harness above becomes the
     * instrument that matters rather than a formality.
     */
    @Test
    fun `with the Insider count fixed no effect depends on who the Insiders are`() {
        val baseline = effectsOf(GameState.EMPTY, round()).map { Transcript.render(it) }
        val exchanged = effectsOf(GameState.EMPTY, withRolesExchanged(round(), Seat(1), Seat(3)))
            .map { Transcript.render(it) }
        assertEquals(
            baseline, exchanged,
            "a rule now branches on which seats hold the role, not merely on how many do. " +
                "The differential harness has become load-bearing; rewrite this rather than " +
                "deleting it.",
        )
    }

    /** Toggling changes the progress values and nothing else — same kinds, same order. */
    @Test
    fun `toggling a role changes only the progress values`() {
        val baseline = effectsOf(GameState.EMPTY, round()).map { Transcript.render(it) }
        val toggled = effectsOf(GameState.EMPTY, withRoleSwapped(round(), Seat(1)))
            .map { Transcript.render(it) }
        assertEquals(
            baseline.map { it.substringBefore('|') },
            toggled.map { it.substringBefore('|') },
            "toggling a role changed the shape of the effect stream, not just a balance value",
        )
        val differing = baseline.zip(toggled).filter { it.first != it.second }
        assertTrue(differing.isNotEmpty(), "the denominator no longer reads the Insider count")
        assertTrue(
            differing.all { it.first.startsWith("SubroutineProgressed") },
            "toggling changed something other than progress: ${differing.take(3)}",
        )
    }
}
