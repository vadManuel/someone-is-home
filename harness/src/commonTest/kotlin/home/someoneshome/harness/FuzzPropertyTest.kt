package home.someoneshome.harness

import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Story 0.10c. Properties that must hold for **every** round, asserted over rounds nobody wrote.
 *
 * Every failure names its seed, because a fuzz failure you cannot reproduce is a rumour.
 */
class FuzzPropertyTest {

    private val SEEDS = 1L..150L

    /** Nothing throws. The absurd sequences are the ones that find the crash. */
    @Test
    fun `the rules survive every round`() {
        for (seed in SEEDS) {
            val events = fuzzRound(seed)
            try {
                record(GameState.EMPTY, events)
            } catch (t: Throwable) {
                throw AssertionError("seed $seed threw ${t::class.simpleName}: ${t.message}", t)
            }
        }
    }

    /** E0's acceptance criterion, over rounds nobody designed. */
    @Test
    fun `every round replays byte-identically`() {
        for (seed in SEEDS) {
            val events = fuzzRound(seed)
            val (_, recording) = record(GameState.EMPTY, events)
            assertEquals(ReplayResult.Identical, replay(GameState.EMPTY, recording), "seed $seed")
        }
    }

    /** The recording survives a process boundary for every round, not just the fixture one. */
    @Test
    fun `every recording round-trips through its text form`() {
        for (seed in SEEDS) {
            val recording = record(GameState.EMPTY, fuzzRound(seed)).second
            val reparsed = RecordingText.parse(recording.toText())
            assertEquals(recording.toText(), reparsed.toText(), "seed $seed")
            assertEquals(recording.events, reparsed.events, "seed $seed")
        }
    }

    /** Host crash recovery reaches the round that was being played, for every round. */
    @Test
    fun `every round resumes to the state it was in`() {
        for (seed in SEEDS) {
            val (live, recording) = record(GameState.EMPTY, fuzzRound(seed))
            val result = resumeFromText(recording.toText())
            assertIs<ResumeResult.Resumed>(result, "seed $seed")
            assertEquals(Transcript.render(live), Transcript.render(result.state), "seed $seed")
        }
    }

    /**
     * **D-079 under random input.** A seat that is never revoked and stays seated is a living
     * player for the whole round, and must never receive the live progress count or the vote
     * attribution list — no matter what sequence of events produced the round.
     */
    @Test
    fun `a player who is never out never receives an out-only message`() {
        // Coverage, asserted at the end. Without it this passes vacuously if no round ever
        // revokes anyone or ever emits an out-only message -- which is the shape of every
        // instrument this project has caught reporting a confident pass while measuring nothing.
        var livingSeatsChecked = 0
        var outOnlyDelivered = 0

        for (seed in SEEDS) {
            val events = fuzzRound(seed)
            val (finalState, _) = record(GameState.EMPTY, events)
            val everOut = everOut(events)
            val transcripts = recordPerClient(GameState.EMPTY, events)

            for (client in transcripts.perClient) {
                outOnlyDelivered += client.lines.count { it.startsWith("SubroutineProgressed") }
                if (client.seat.index in everOut) continue
                if (finalState.seats.none { it.index == client.seat.index }) continue
                livingSeatsChecked++
                val forbidden = client.lines.filter { line ->
                    OUT_ONLY.any { line.startsWith(it) }
                }
                assertEquals(
                    emptyList(), forbidden,
                    "seed $seed: living seat ${client.seat.index} received ${forbidden.take(2)}",
                )
            }
        }

        assertTrue(livingSeatsChecked > 100, "only $livingSeatsChecked living seats were checked")
        assertTrue(
            outOnlyDelivered > 0,
            "no out-only message reached anyone in ${SEEDS.count()} rounds, so this property " +
                "held because the message never fired, not because the allowlist works",
        )
    }

    /** Addressing holds under random input: nobody ever sees another seat's lamp. */
    @Test
    fun `no client ever receives another seat's lamp`() {
        for (seed in SEEDS) {
            for (client in recordPerClient(GameState.EMPTY, fuzzRound(seed)).perClient) {
                val foreign = client.lines
                    .filter { it.startsWith("LampSet") }
                    .filterNot { it.startsWith("LampSet|seat=${client.seat.index}|") }
                assertEquals(emptyList(), foreign, "seed $seed: seat ${client.seat.index}")
            }
        }
    }

    /** Every delivered line's kind has a row in the allowlist. Fail-closed, over random rounds. */
    @Test
    fun `no client ever receives a kind with no allowlist row`() {
        val known = EmitSchema.knownKinds().map { it.name }.toSet()
        for (seed in SEEDS) {
            for (client in recordPerClient(GameState.EMPTY, fuzzRound(seed)).perClient) {
                val unlisted = client.lines.map { it.substringBefore('|') }.filterNot { it in known }
                assertEquals(emptyList(), unlisted.distinct(), "seed $seed")
            }
        }
    }

    /**
     * **D-081 under random input, and D-109's one exception.** With the Insider count held fixed,
     * exchanging which seats hold the role must leak nothing but the meter.
     *
     * The meter is subtracted for the reason [METER_ASYMMETRY] gives, and the subtraction is
     * checked for emptiness at the end: if no round in a hundred and fifty ever diverged on the
     * meter, this property is holding because the rule under test never fired.
     */
    @Test
    fun `a role exchange leaks nothing but the meter in any round`() {
        var exercised = 0
        var meterMoved = 0
        for (seed in SEEDS) {
            val events = fuzzRound(seed)
            val armed = events.filterIsInstance<Event.RoundArmed>().first()
            val insider = armed.insiders.firstOrNull() ?: continue
            val resident = armed.seats.firstOrNull { s -> armed.insiders.none { it.index == s.index } }
                ?: continue
            val result = differentialOnRoleExchange(
                GameState.EMPTY, events, insider, resident, ignoring = METER_ASYMMETRY,
            )
            assertEquals(emptyList(), result.unexplained, "seed $seed leaked: $result")
            if (differentialOnRoleExchange(GameState.EMPTY, events, insider, resident)
                    .unexplained.isNotEmpty()
            ) {
                meterMoved++
            }
            exercised++
        }
        assertTrue(exercised > SEEDS.count() / 2, "only $exercised seeds exercised the exchange")
        assertTrue(
            meterMoved > 0,
            "no round in ${SEEDS.count()} diverged on the meter at all, so the subtraction is " +
                "removing nothing and this property is vacuous",
        )
    }

    /**
     * **THE ROLE ORACLE TEST, over rounds nobody wrote.**
     *
     * The house grades every entry for real, for both roles, in identical words (D-109). Exchange
     * which seats hold the role and **not one verdict may move** — not its value, not its position
     * in the stream, not whether it exists at all. A fake that never failed, a fake graded against
     * a rolled distribution, or a verdict simply withheld from an Insider all show up here.
     */
    @Test
    fun `a verdict never depends on who the Insiders are in any round`() {
        var verdicts = 0
        for (seed in SEEDS) {
            val events = fuzzRound(seed)
            val armed = events.filterIsInstance<Event.RoundArmed>().first()
            val insider = armed.insiders.firstOrNull() ?: continue
            val resident = armed.seats.firstOrNull { s -> armed.insiders.none { it.index == s.index } }
                ?: continue

            fun stream(list: List<Event>) = effectsOf(GameState.EMPTY, list)
                .map { Transcript.render(it) }

            // **Up to the moment either round ended** (D-131), through `untilTheRoundEnds` rather
            // than a second copy of the rule. Parity counts living plain Residents against living
            // Insiders, so an exchange genuinely ends one of these rounds and not the other — and
            // past that point a missing verdict is a round that finished, not a verdict withheld
            // from an Insider. Everything before it is compared exactly as strictly as it ever was.
            val a = stream(events)
            val b = stream(withRolesExchanged(events, insider, resident))
            fun graded(lines: List<String>, other: List<String>) =
                untilTheRoundEnds(lines, other).filter { it.startsWith("SubroutineGraded") }

            val baseline = graded(a, b)
            verdicts += baseline.size
            assertEquals(
                baseline, graded(b, a),
                "seed $seed: a verdict changed when seats ${insider.index} and " +
                    "${resident.index} traded roles — the house graded the asker, not the entry",
            )
        }
        assertTrue(verdicts > 100, "only $verdicts verdicts were compared across all rounds")
    }

    /**
     * The absurd player scores sometimes, and the grading says both things.
     *
     * Coverage for every assertion above that mentions a verdict: a fuzzer whose entries were
     * always wrong would exercise one half of one branch, and `accepted=true` — the half the meter
     * and D-109's whole asymmetry hang off — would never once be reached.
     */
    @Test
    fun `the generator produces both verdicts`() {
        var accepted = 0
        var rejected = 0
        for (seed in SEEDS) {
            for (line in effectsOf(GameState.EMPTY, fuzzRound(seed)).map { Transcript.render(it) }) {
                if (line.startsWith("SubroutineGraded")) {
                    if (line.endsWith("accepted=true")) accepted++ else rejected++
                }
            }
        }
        assertTrue(accepted > 0, "no entry was ever graded correct in ${SEEDS.count()} rounds")
        assertTrue(rejected > 0, "no entry was ever graded wrong in ${SEEDS.count()} rounds")
    }

    /** The gate holds: a round with no arming event emits nothing to anyone, whatever it contains. */
    @Test
    fun `a round that never arms reaches no client`() {
        for (seed in SEEDS) {
            val events = fuzzRound(seed, armFirst = false)
                .filterNot { it is Event.RoundArmed }
            val (_, recording) = record(GameState.EMPTY, events)
            assertEquals(emptyList(), recording.effectTranscript, "seed $seed")
            assertEquals(events.size, recording.refusalTranscript.size, "seed $seed")
            assertEquals(emptyList(), recordPerClient(GameState.EMPTY, events).perClient, "seed $seed")
        }
    }

    /** The generator has to actually vary, or every property above is one round asserted 150 times. */
    @Test
    fun `the generator produces distinct rounds and reproduces them`() {
        val texts = SEEDS.map { record(GameState.EMPTY, fuzzRound(it)).second.toText() }
        assertEquals(SEEDS.count(), texts.distinct().size, "seeds collided")
        assertEquals(texts.first(), record(GameState.EMPTY, fuzzRound(SEEDS.first)).second.toText())
    }

    /** And it has to actually emit the absurd cases, or it is a reasonable player after all. */
    @Test
    fun `the generator emits out-of-range seats and self-contact`() {
        var outOfRange = 0
        var selfContact = 0
        for (seed in SEEDS) {
            for (event in fuzzRound(seed)) {
                if (event is Event.ContactMade) {
                    if (event.actor.index == event.target.index) selfContact++
                    if (event.actor.index !in 0..7 || event.target.index !in 0..7) outOfRange++
                }
            }
        }
        assertTrue(outOfRange > 0, "no out-of-range seat in ${SEEDS.count()} rounds")
        assertTrue(selfContact > 0, "no self-contact in ${SEEDS.count()} rounds")
    }

    /**
     * **The kinds only a player outside the system may receive — WRITTEN OUT, not derived.**
     *
     * This list was briefly computed from `EmitSchema` itself, and the injection that was supposed
     * to prove it caught nothing: widening a row to a living class removes that kind from the
     * derived list, so the property went on passing over a leak it was pointed straight at. **A
     * test derived from the thing it is testing agrees with itself.** That is the whole reason
     * the schema allowlist is described as *independently required, not a second opinion* — and
     * a harness that re-derives it is a second opinion after all.
     *
     * So the three are restated here as a decision, and [theOutOnlyKindsAreExactlyTheOnesNamed]
     * is the other half: it fails when a fourth out-only kind appears and nobody adds it, which is
     * the failure a literal list would otherwise have.
     */
    private val OUT_ONLY: List<String> = listOf(
        // The live SystemIntegrity decrement. A continuous rate signal nobody living may read.
        "SubroutineProgressed",
        // Who voted for whom (D-075). The living get a count and the outcome, never the ballot.
        "MeetingResolved",
        // Every selection tap, live (D-117, D-134). The couch is the only reader of selections,
        // and this is the largest single disclosure in the table -- widened by one class it hands
        // the room its own thinking in real time, to the people still in it.
        "VoteSelectionShown",
        // The two the completeness check below found when it was first written, which is what it
        // is for. Both are ADDRESSED to one seat as well as permitted only to the out classes, so
        // a living player was never going to receive one by accident -- but "never by accident" is
        // the argument this project keeps refusing, and the row is what actually denies them.
        //
        // STAND AND WALK IN: what a player outside the system gets instead of a ringing call.
        "StandAndWalkIn",
        // The Restrained takeover, at the halfway mark, to the losing seat (D-102, D-134's E1-1).
        "RestrainedTakeover",
    )

    /**
     * The list above is complete against the allowlist.
     *
     * Not a derivation of the property, a check ON it: the property must name every kind the table
     * gives to the out and to nobody living, so a fourth one cannot appear un-covered.
     */
    @Test
    fun `the out-only kinds are exactly the ones named`() {
        val fromTheTable = EmitSchema.knownKinds()
            .filter { kind ->
                val classes = EmitSchema.classesFor(kind)
                classes.isNotEmpty() && classes.all { it.roundState == RoundState.Out }
            }
            .map { it.name }
        assertEquals(
            OUT_ONLY.sorted(), fromTheTable.sorted(),
            "an out-only kind is not covered by the fuzz property, or one named here has been " +
                "widened to a living class -- either way somebody should say which they meant",
        )
    }

    private fun everOut(events: List<Event>): Set<Int> {
        val seen = mutableSetOf<Int>()
        var state = GameState.EMPTY
        drive(GameState.EMPTY, events) { _, after, _ ->
            after.revoked.forEach { seen += it.index }
            // **Both lists, and this one was added the day a Restrain first reached state.** It
            // read `revoked` alone for as long as nothing stored a Restrain, and the two are never
            // interchangeable (rule 9) -- so a seat the room restrained looked living to this
            // helper while the allowlist, correctly, was treating it as out.
            after.restrained.forEach { seen += it.index }
            state = after
        }
        // A re-arm wipes both lists, so anyone put out before it is living again afterwards and
        // this set is deliberately the union across the whole round: it is used to EXCLUDE seats
        // from a strict assertion, so over-including is the safe direction.
        return seen
    }
}
