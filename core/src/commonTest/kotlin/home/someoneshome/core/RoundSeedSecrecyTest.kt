package home.someoneshome.core

import home.someoneshome.model.Cooldown
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.InsiderAbility
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **Nothing a phone holds leads back to the round's seed** (D-214).
 *
 * The round's seed is what [insidersFor] draws the Insiders from, so a phone that recovered it would
 * know who they are. None of the three leak instruments sees this class of leak. The differential
 * harness holds the seed fixed and swaps roles, so a value that encodes the seed is byte-identical in
 * both runs. The schema allowlist checks which kinds reach which phones, never what a value can be
 * solved for. Radio sniffing sees the radio, not the socket. So it is asserted here, against the
 * derivation itself.
 *
 * Injecting the bug: put `staticSeed` back to `seed * 0x5DEECE66D + tick * 0x9E3779B9`, or start
 * [Draw] from `roundSeed * constant + domain`. Either is affine in the round's seed.
 */
class RoundSeedSecrecyTest {

    private val seats = (0 until 6).map { Seat(it) }
    private val active = listOf(MarkerId("hall-card"), MarkerId("garage-card"))

    /** A round armed the way the house arms one: the Insiders drawn from [seed], the pool ready. */
    private fun armed(seed: Long): GameState = GameState.armedRound(
        seed = seed,
        seats = seats,
        insiders = insidersFor(seed, seats, chosen = null),
        systemIntegrity = 30,
        activeMarkers = active,
        destinations = placed(active),
        cooldowns = seats.map { Cooldown(it, InsiderAbility.Access, Tick(0)) },
    )

    /** The seed every living phone is sent when an Insider fires Static at [at] in round [seed]. */
    private fun jamSeed(seed: Long, at: Tick): Long {
        val state = armed(seed)
        val actor = state.insiderSeats.first()
        val admission = admit(state, Event.StaticFired(at, actor))
        val effects = (admission as? Admission.Admitted)?.reduction?.effects
            ?: fail("the Static at $at was refused, so there is no jam to read")
        return effects.filterIsInstance<Effect.StaticOpened>().single().seed
    }

    /**
     * **The attack, run exactly as a modded phone would run it.**
     *
     * The phone holds two things the house sent it: the jam's seed, which every living phone gets,
     * and its own contact seed, which is an exact check on any guess at the round's seed. It guesses
     * the firing tick across a window it can bound off its own clock, solves the jam's seed for the
     * round's seed under each guess, and keeps the guess whose contact seed matches its own. Before
     * D-214 the solution was one multiplication by a modular inverse, and it found the round at the
     * true tick.
     */
    @Test
    fun `a phone cannot run the jam seed back to the round seed`() {
        val seed = 20260823L
        val fired = Tick(10)
        val me = Seat(0)
        val jam = jamSeed(seed, fired)
        val mine = contactSeedFor(seed, me)

        val inverse = inverseOf(OLD_SEED_MULTIPLIER)
        for (guess in 0L..1_000L) {
            val candidate = (jam - guess * OLD_TICK_MULTIPLIER) * inverse
            if (contactSeedFor(candidate, me) == mine) {
                fail(
                    "seat ${me.index} solved the jam's seed for the round's seed at tick $guess " +
                        "and redrew the Insiders as " +
                        insidersFor(candidate, seats, chosen = null).map { it.index },
                )
            }
        }
    }

    /**
     * **No seed the house derives is affine in the round's seed.**
     *
     * The structural half of the attack above, and the wider one: anything of the form
     * `seed * a + b` modulo 2⁶⁴ satisfies `f(x + d) - f(x) == f(y + d) - f(y)` for every `x`, `y`
     * and `d`, whatever the constants, so it catches a new multiplier as well as the old one. An
     * HMAC fails it with overwhelming probability. Asserted for every purpose's stream origin, and
     * for the jam's seed as the rules actually emit it.
     */
    @Test
    fun `no seed the house derives is affine in the round seed`() {
        val x = 20260823L
        val y = 7L
        val d = 1_000_003L

        for (purpose in SeedPurpose.entries) {
            for (index in listOf(0L, 3L)) {
                fun f(seed: Long) = Draw(seed, purpose, index).origin
                assertNotEquals(
                    f(x + d) - f(x), f(y + d) - f(y),
                    "the $purpose stream (index $index) starts from an affine function of the " +
                        "round's seed, so its outputs can be solved back to it",
                )
            }
        }

        fun jam(seed: Long) = jamSeed(seed, Tick(10))
        assertNotEquals(
            jam(x + d) - jam(x), jam(y + d) - jam(y),
            "the jam's seed is an affine function of the round's seed, and every living phone holds it",
        )
    }

    /**
     * **Every purpose reads a stream of its own** (D-214).
     *
     * Distinct labels, and none a prefix of another, so that no two purposes can hash the same
     * message. A repeated label would make the station draw and the Insider draw one stream again.
     */
    @Test
    fun `every purpose has a label of its own`() {
        val labels = SeedPurpose.entries.map { it.label } + "someone-is-home/contact-seed/1"
        assertEquals(labels.size, labels.toSet().size, "two purposes share a label: $labels")
        for (a in labels) for (b in labels) {
            if (a != b) assertTrue(!b.startsWith(a), "'$a' is a prefix of '$b'")
        }
        val origins = SeedPurpose.entries.map { Draw(20260823L, it).origin }
        assertEquals(origins.size, origins.toSet().size, "two purposes start from the same state")
    }

    /** The jam replays: the same round and the same tick send every phone the same seed (rule 4). */
    @Test
    fun `the jam seed still replays and still moves with the tick`() {
        assertEquals(jamSeed(20260823L, Tick(10)), jamSeed(20260823L, Tick(10)))
        assertNotEquals(jamSeed(20260823L, Tick(10)), jamSeed(20260823L, Tick(11)))
    }

    /** Newton's iteration for the inverse of an odd number modulo 2⁶⁴. */
    private fun inverseOf(odd: Long): Long {
        var x = odd
        repeat(6) { x *= 2 - odd * x }
        check(x * odd == 1L) { "no inverse" }
        return x
    }

    private companion object {
        /** The pre-D-214 jam seed was `roundSeed * this + tick * [OLD_TICK_MULTIPLIER]`. */
        const val OLD_SEED_MULTIPLIER = 0x5DEECE66DL
        const val OLD_TICK_MULTIPLIER = 0x9E3779B9L
    }
}
