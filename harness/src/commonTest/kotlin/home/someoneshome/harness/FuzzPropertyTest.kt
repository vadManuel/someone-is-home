package home.someoneshome.harness

import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
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
            val everRevoked = everRevoked(events)
            val transcripts = recordPerClient(GameState.EMPTY, events)

            for (client in transcripts.perClient) {
                outOnlyDelivered += client.lines.count { it.startsWith("SubroutineProgressed") }
                if (client.seat.index in everRevoked) continue
                if (finalState.seats.none { it.index == client.seat.index }) continue
                livingSeatsChecked++
                val forbidden = client.lines.filter {
                    it.startsWith("SubroutineProgressed") || it.startsWith("MeetingResolved")
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
     * **D-081 under random input.** With the Insider count held fixed, exchanging which seats
     * hold the role must leak to nobody else.
     */
    @Test
    fun `a role exchange leaks to nobody else in any round`() {
        var exercised = 0
        for (seed in SEEDS) {
            val events = fuzzRound(seed)
            val armed = events.filterIsInstance<Event.RoundArmed>().first()
            val insider = armed.insiders.firstOrNull() ?: continue
            val resident = armed.seats.firstOrNull { s -> armed.insiders.none { it.index == s.index } }
                ?: continue
            val result = differentialOnRoleExchange(GameState.EMPTY, events, insider, resident)
            assertEquals(emptyList(), result.unexplained, "seed $seed leaked: $result")
            exercised++
        }
        assertTrue(exercised > SEEDS.count() / 2, "only $exercised seeds exercised the exchange")
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

    private fun everRevoked(events: List<Event>): Set<Int> {
        val seen = mutableSetOf<Int>()
        var state = GameState.EMPTY
        drive(GameState.EMPTY, events) { _, after, _ ->
            after.revoked.forEach { seen += it.index }
            state = after
        }
        // A re-arm wipes `revoked`, so anyone revoked before it is living again afterwards and
        // this set is deliberately the union across the whole round: it is used to EXCLUDE seats
        // from a strict assertion, so over-including is the safe direction.
        return seen
    }
}
