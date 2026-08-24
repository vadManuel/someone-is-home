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

    /**
     * **The exchange that moves the round's actor with it.**
     *
     * Seat 5 is the Insider this fixture fires everything from and seat 6 is an ordinary Resident,
     * so exchanging them hands the whole kit to a different phone — and `withRolesExchanged` moves
     * the fires along with the role, because since E-R2-1 a variant that left them behind would be
     * a round in which nothing can be fired rather than the same round played by other people.
     * Every one of those moved lines lands on one of the two swapped seats.
     *
     * Its opposite number is the exchange above: with the actor at seat 5, seats 1 and 3 are the
     * pair that take no ability action all round, so the two tests together diff the busy seats and
     * the quiet ones.
     */
    @Test
    fun `exchanging the acting seat leaks nothing but the meter`() {
        val result = differentialOnRoleExchange(
            GameState.EMPTY, round(), Seat(5), Seat(6), ignoring = METER_ASYMMETRY,
        )
        assertEquals(emptyList(), result.unexplained, "leaked: $result")
    }

    /**
     * **The second subtraction is not hiding an empty diff either: the exchange really does end
     * one round and not the other** (D-131).
     *
     * `diff` compares two runs only up to the first ending in either, because past that point they
     * are two different games rather than two views of one round — see `untilTheRoundEnds`, which
     * carries the argument. That is a real narrowing of a leak instrument, so like the meter's it
     * is paired with the assertion that the thing being subtracted is *there*: if exchanging seats
     * 1 and 3 ever stops ending one of the two rounds, this fails and says so, and the narrowing
     * has quietly become a filter over two identical rounds.
     *
     * The arithmetic, written out because it is the whole reason: six of the eight seats are plain
     * Residents under the baseline draw and the fixture takes four of them out of the round.
     * Exchanging seat 1 (an Insider) with seat 3 (a Resident) moves the restrained seat from the
     * Insider side of parity to the Resident side, which is the difference between `3 > 1` and
     * `2 <= 2`.
     */
    @Test
    fun `the exchange really does end one round and not the other`() {
        val baseline = record(GameState.EMPTY, round()).first
        val variant = record(GameState.EMPTY, withRolesExchanged(round(), Seat(1), Seat(3))).first

        assertFalse(
            baseline.ended,
            "the shared fixture round now ends on its own, so both runs end and the ending " +
                "subtraction in `diff` is comparing two truncated rounds against each other",
        )
        assertTrue(
            variant.ended,
            "exchanging seats 1 and 3 no longer ends the round, so `untilTheRoundEnds` subtracts " +
                "nothing and the assertions that rely on it have gone vacuous",
        )
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

        // **Up to the moment either round ended** (D-131) — `untilTheRoundEnds`, the same rule
        // `diff` uses and not a second copy of it. Exchanging seats 1 and 3 moves the restrained
        // seat across parity, so the variant round is over at the second meeting and stops
        // producing verdicts; past that point the two runs are different games and a missing
        // verdict is a round that finished rather than a verdict withheld from an Insider.
        fun verdicts(t: ClientTranscripts, other: ClientTranscripts, seat: Seat) =
            untilTheRoundEnds(t.linesFor(seat), other.linesFor(seat))
                .filter { it.startsWith("SubroutineGraded") }

        var seen = 0
        for (seat in SEATS) {
            val a = verdicts(baseline, exchanged, seat)
            val b = verdicts(exchanged, baseline, seat)
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
            .withoutTheRuledAsymmetries()

        // Three subtractions, and they are two shapes on purpose: the meter and the role reveal are
        // KINDS, dropped from both runs so the surrounding lines stay aligned; the ending is the
        // round STOPPING, so what is compared is everything that happened while both were still
        // running. See `untilTheRoundEnds` for why the third cannot be expressed as the first.
        //
        // **The role reveal is subtracted HERE and nowhere else in this file.** It is addressed to
        // the seat it is about, so in a per-client transcript it lands on a swapped seat and is
        // already explained; this comparison is the authority's own stream, where every seat's
        // reveal is on one list. The test below measures it on its own.
        val a = stream(round())
        val b = stream(withRolesExchanged(round(), Seat(1), Seat(3)))
        assertEquals(
            untilTheRoundEnds(a, b),
            untilTheRoundEnds(b, a),
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
            baseline.withoutTheRuledAsymmetries(),
            toggled.withoutTheRuledAsymmetries(),
            "toggling a role changed something other than the meter and the reveal",
        )
    }

    /**
     * **The meter subtracted from a raw effect stream, read off the one place it is named.**
     *
     * These two tests compare authority-side effect rows rather than client transcripts, so the
     * subtraction is by rendered prefix rather than by [MessageKind] — but *which* kinds are the
     * meter is [METER_ASYMMETRY]'s decision, and it was written out here by hand as a single
     * literal until the meter grew two more kinds and neither of them was added.
     *
     * **Reading the set is not the derived-allowlist fault L2 found.** That was a test deriving
     * its expectation from `EmitSchema`, which is the thing it was testing. Nothing here tests
     * `METER_ASYMMETRY`; it names a ruled asymmetry, and these tests assert about the rules.
     */
    /**
     * **[THE_ROLE_REVEAL] is subtracted above, so it is measured here** (E-R2-2).
     *
     * The pattern [METER_ASYMMETRY] set: a subtraction has to be paired with the assertion that the
     * thing subtracted is really there, or it becomes a filter over a leak that nobody would ever
     * see again.
     *
     * Two halves, and the second is the one that matters. **It moves** — the reveal really does
     * differ between two runs whose roles were exchanged, so the subtraction is subtracting
     * something. **And it moves on the two seats that traded and on no other phone** — read off the
     * per-client transcripts rather than off the authority's stream, because the claim is about
     * what arrived somewhere, and a reveal reaching a third seat would be the round's alignment
     * published at arming.
     */
    @Test
    fun `the role reveal moves with the role and reaches one seat`() {
        val baseline = round()
        val exchanged = withRolesExchanged(baseline, Seat(1), Seat(3))

        fun reveals(events: List<Event>) = effectsOf(GameState.EMPTY, events)
            .map { Transcript.render(it) }
            .filter { line -> THE_ROLE_REVEAL.any { line.startsWith(it.name) } }

        assertTrue(reveals(baseline).isNotEmpty(), "no phone was told what it was")
        assertNotEquals(
            reveals(baseline), reveals(exchanged),
            "the reveal no longer moves when two seats trade roles, so subtracting it subtracts " +
                "nothing and the tests that subtract it have gone vacuous",
        )

        val perClient = diff(GameState.EMPTY, baseline, exchanged, listOf(Seat(1), Seat(3)))
        val elsewhere = perClient.divergences
            .filter { d -> THE_ROLE_REVEAL.any { d.baseline?.startsWith(it.name) == true } }
            .filterNot { it.seat.index == 1 || it.seat.index == 3 }
        assertEquals(
            emptyList(), elsewhere,
            "a seat that traded nothing was sent a different role: ${elsewhere.take(3)}",
        )
    }

    private fun List<String>.withoutTheRuledAsymmetries(): List<String> =
        filterNot { line -> (METER_ASYMMETRY + THE_ROLE_REVEAL).any { line.startsWith(it.name) } }
}
