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
     * **The acceptance criterion, and D-109 moved one line of it.**
     *
     * Two seats trade roles, so the Insider count is identical in both runs. Every seat other than
     * the two that traded receives byte-identical bytes — **apart from the meter**, which is the
     * one thing in the game that genuinely depends on *which* seats hold the role: a plain
     * Resident's accepted entry banks and an Insider's does not (`gdd.md:382`, D-109).
     *
     * The meter is subtracted here and measured on its own in the two tests below, so nothing is
     * being waved past. See [METER_ASYMMETRY] for why subtracting rather than loosening.
     */
    @Test
    fun `a role exchange leaks nothing but the meter`() {
        val result = differentialOnRoleExchange(
            GameState.EMPTY, round(), Seat(1), Seat(3), ignoring = METER_ASYMMETRY,
        )
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
    fun `exchanging two passive seats leaks nothing but the meter`() {
        val result = differentialOnRoleExchange(
            GameState.EMPTY, round(), Seat(5), Seat(6), ignoring = METER_ASYMMETRY,
        )
        assertEquals(emptyList(), result.unexplained, "leaked: $result")
    }

    /**
     * **The subtraction is not hiding an empty diff: without it, the meter really does diverge.**
     *
     * Every filtered assertion in this file would pass vacuously if exchanging two roles had
     * stopped changing anything at all — which is the shape of every instrument this project has
     * caught reporting a confident pass while measuring nothing. So the unfiltered exchange is run
     * too, and it must be dirty.
     */
    @Test
    fun `without the subtraction the exchange is visibly dirty`() {
        val raw = differentialOnRoleExchange(GameState.EMPTY, round(), Seat(5), Seat(6))
        assertTrue(
            raw.unexplained.isNotEmpty(),
            "the meter no longer depends on who the Insiders are, so METER_ASYMMETRY is " +
                "subtracting nothing and every test that uses it has gone vacuous",
        )
    }

    /**
     * **D-109's asymmetry reaches players who are outside the system, and NO living player.**
     *
     * The complement of the subtraction, and the property that decides whether the asymmetry is
     * survivable at all. The meter moving differently is a fact about the aggregate bar; the day
     * it reaches a phone still in the round, it becomes a per-player statement about who banked —
     * and the living see SystemIntegrity only as a frozen percentage at meetings (D-103).
     */
    @Test
    fun `the meter asymmetry reaches no living player`() {
        val baseline = round()
        val exchanged = withRolesExchanged(baseline, Seat(5), Seat(6))
        val out = record(GameState.EMPTY, baseline).first.revoked.map { it.index }.toSet()
        assertTrue(out.isNotEmpty(), "nobody is out in this round, so this proves nothing")

        val raw = diff(GameState.EMPTY, baseline, exchanged, listOf(Seat(5), Seat(6)))
        val living = raw.unexplained.filterNot { it.seat.index in out }
        assertEquals(
            emptyList(), living,
            "a seat that stayed in the round saw the meter asymmetry: ${living.take(3)}",
        )
    }

    /**
     * **THE ROLE ORACLE TEST. The same entry, graded the same way, answered in the same words.**
     *
     * The successor to *"role is still inert in the rules"* and to *"no effect depends on who the
     * Insiders are"*, both of which D-109 retired by giving the rules their first real role branch.
     * This is the property that replaces them, and it is the one the whole verdict model rests on:
     * **every seat's verdicts are byte-identical when two seats trade roles**, including the two
     * that traded.
     *
     * The fixture hands over the entries the house asked for, so the round is full of accepted
     * verdicts, and seats 1 and 3 answer identically in both runs. A fake that never failed, a fake
     * that failed on a rolled distribution, a verdict withheld from an Insider, a verdict that
     * arrived a beat later — every one of them shows up here as a differing or missing line.
     */
    @Test
    fun `a verdict never depends on who the Insiders are`() {
        val baseline = recordPerClient(GameState.EMPTY, round())
        val exchanged = recordPerClient(GameState.EMPTY, withRolesExchanged(round(), Seat(1), Seat(3)))

        fun verdicts(t: ClientTranscripts, seat: Seat) =
            t.linesFor(seat).filter { it.startsWith("SubroutineGraded") }

        var seen = 0
        for (seat in SEATS) {
            val a = verdicts(baseline, seat)
            val b = verdicts(exchanged, seat)
            seen += a.size
            assertEquals(
                a, b,
                "seat ${seat.index}'s verdicts changed when two seats traded roles — the house " +
                    "graded the asker rather than the entry",
            )
        }
        assertTrue(seen > 0, "no verdict reached anybody, so this held because nothing fired")
    }

    /**
     * The same claim one layer lower: the effect stream itself, meter subtracted.
     *
     * Transcripts are what a phone receives; this is what the rules emitted. Both are asserted
     * because the emit boundary sits between them, and a leak that the allowlist happens to be
     * hiding today is still a rule branching on identity.
     */
    @Test
    fun `with the Insider count fixed only the meter depends on who the Insiders are`() {
        fun stream(events: List<Event>) = effectsOf(GameState.EMPTY, events)
            .map { Transcript.render(it) }
            .filterNot { it.startsWith("SubroutineProgressed") }

        assertEquals(
            stream(round()),
            stream(withRolesExchanged(round(), Seat(1), Seat(3))),
            "a rule now branches on which seats hold the role in something other than the meter. " +
                "The differential harness has become load-bearing; rewrite this rather than " +
                "deleting it.",
        )
    }

    /**
     * **Toggling changes two public balance values now, and the diff still says so.**
     *
     * Toggling one seat changes the number of Insiders, which moves both the meter's trajectory
     * (one fewer or one more seat banks) and — before D-130 — the total itself. The total is now a
     * function of seats alone, so what is left is the trajectory: still a world change rather than
     * a tell, still something the harness cannot know, and still a judgement that belongs here in
     * a test that names the reason.
     */
    @Test
    fun `toggling one role moves a public balance value and the diff says so`() {
        val toggled = differentialOnRoleSwap(GameState.EMPTY, round(), Seat(1))
        assertTrue(
            toggled.unexplained.isNotEmpty(),
            "toggling changed the Insider count and therefore how many seats bank; if this is " +
                "now clean, the meter has stopped reading the roles and the test needs revisiting",
        )
        assertEquals(
            emptyList(),
            differentialOnRoleSwap(
                GameState.EMPTY, round(), Seat(1), ignoring = METER_ASYMMETRY,
            ).unexplained,
            "toggling diverged in something other than the meter",
        )
    }

    /** Toggling changes the meter and nothing else — same kinds, same order, once it is taken out. */
    @Test
    fun `toggling a role changes only the meter`() {
        fun stream(events: List<Event>) = effectsOf(GameState.EMPTY, events)
            .map { Transcript.render(it) }

        val baseline = stream(round())
        val toggled = stream(withRoleSwapped(round(), Seat(1)))
        assertNotEquals(baseline, toggled, "the meter no longer reads the Insider count")
        assertEquals(
            baseline.filterNot { it.startsWith("SubroutineProgressed") },
            toggled.filterNot { it.startsWith("SubroutineProgressed") },
            "toggling a role changed something other than the meter",
        )
    }
}
