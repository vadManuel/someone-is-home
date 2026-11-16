package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val SEATS = (0 until 8).map { Seat(it) }
private val INSIDERS = listOf(Seat(1), Seat(5))

private fun armed(): GameState =
    reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 42L, seats = SEATS, insiders = INSIDERS)).state

/**
 * **The first tick at which an Insider ability is off its opening cooldown** (D-132).
 *
 * Every cooldown starts the round already running at half its normal duration, so the round opens
 * with a guaranteed stretch of peace. Every event below is fed at or after this moment — a test
 * that armed a Revoke inside the opening stretch would arm nothing, revoke nobody, and go on
 * passing every assertion about the *shape* of what came back, which is precisely the differential
 * this file exists to keep honest.
 */
private val READY = Balance.REVOKE_COOLDOWN / 2

private fun run(start: GameState, events: List<Event>): Pair<GameState, List<Effect>> {
    var state = start
    val effects = mutableListOf<Effect>()
    for (e in events) {
        val r = reduce(state, e)
        state = r.state
        effects += r.effects
    }
    return state to effects
}

class DeterminismTest {

    @Test
    fun `the same events produce identical effects every time`() {
        val events = listOf(
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
            Event.SubroutineReturned(Tick(READY + 3), Seat(2), home.someoneshome.model.MarkerId("m1"), listOf(1, 2)),
        )
        val (s1, e1) = run(armed(), events)
        val (s2, e2) = run(armed(), events)
        assertEquals(e1.toString(), e2.toString())
        assertEquals(s1.revoked.map { it.index }, s2.revoked.map { it.index })
    }

    @Test
    fun `minted ids are seeded and monotonic - never random`() {
        val (a, sA) = armed().mintId()
        val (b, _) = sA.mintId()
        val (a2, _) = armed().mintId()
        assertEquals(a.value, a2.value, "same state must mint the same id on replay")
        assertTrue(b.value > a.value)
    }

    @Test
    fun `seats are held in a stable order regardless of input order`() {
        val shuffled = SEATS.reversed()
        val s = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 42L, seats = shuffled, insiders = INSIDERS),
        ).state
        assertEquals((0 until 8).toList(), s.seats.map { it.index })
    }
}

/**
 * The leak tests.
 *
 * These are differential: they compare the EFFECT STREAM between a case that should succeed and
 * a case that should not, and require the two to be indistinguishable. A test that merely
 * asserted "firing at a revoked target does not revoke them again" would pass on the version of
 * this code that leaks.
 */
class AbsentEffectIsTheLeakTest {

    @Test
    fun `contacting a revoked target is indistinguishable from contacting a live one`() {
        val base = armed()

        // A live target.
        val (_, live) = run(base, listOf(
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
        ))

        // A target already revoked. Same actor, same ticks.
        val pre = run(base, listOf(
            Event.RevokeArmed(Tick(READY), Seat(5)),
            Event.ContactMade(Tick(READY), Seat(5), Seat(3)),
        )).first
        // The positive control, and this file has needed one since D-132 gave the Revoke a
        // cooldown: without it, an arming that silently did nothing would leave the "already
        // revoked" case never revoked, and the differential below would compare two identical
        // live targets and pass for the one reason that means nothing.
        assertTrue(pre.isRevoked(Seat(3)), "the fixture never actually revoked anybody")
        val (_, defunct) = run(pre, listOf(
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
        ))

        assertEquals(live.toString(), defunct.toString(),
            "the effect stream differs, so the absence of an effect reveals the target was already revoked")
    }

    /**
     * **An unarmed touch is answered, and it is not answered as a spent Revoke** (R1, D-216).
     *
     * Until R1 this test asserted `AbilityFired` here, and the row ships that kind to living
     * Insiders only: an unarmed Insider's touch put a frame on their socket and a Resident's put
     * none on theirs. The answer is now `ContactAnswered`, the same for an unarmed Insider and a
     * Resident, which `ContactAnswerSizeTest` pins to `AbilityFired`'s length on the wire.
     */
    @Test
    fun `contacting without arming is answered and spends nothing`() {
        val start = armed()
        val (after, unarmed) = run(start, listOf(Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3))))
        assertEquals(
            listOf(
                Effect.ContactAnswered(Seat(1)),
                // **Constructed for a seat this contact did not revoke** (E-R2-5). That is what
                // keeps the test above true: the stream is the same two effects whether the Revoke
                // landed, hit somebody already out, was fired at the actor, or was never armed.
                Effect.RevokeLanded(Seat(3)),
            ).toString(),
            unarmed.toString(),
        )
        // **And it reaches nobody, which is the other half and the half a stream cannot show.**
        // Seat 3 is still Live, the row permits the kind to the two OUT classes only, so the
        // delivery is declined at the boundary — `Surged`'s two independent denials, inverted. A
        // widening of that row would push a living player to the Revoked screen for a Revoke that
        // never happened, and nothing in the rules would have changed.
        assertFalse(after.isRevoked(Seat(3)), "the fixture revoked somebody it should not have")
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.RevokeLanded(Seat(3)), after),
            "a Revoke that landed on nobody was delivered to somebody",
        )
    }

    /**
     * **The other side of the same row: a Revoke that DID land reaches the seat it landed on, and
     * that seat only** (D-134, E-R2-5).
     *
     * Without this the test above passes on a build where the row permits the kind to nobody at
     * all, which would be the screen never arriving — the thing E-R2-5 was raised about.
     */
    @Test
    fun `a Revoke that landed reaches the revoked seat and nobody else`() {
        val (after, _) = run(armed(), listOf(
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
        ))
        assertTrue(after.isRevoked(Seat(3)), "the fixture never actually revoked anybody")
        assertEquals(
            listOf(Seat(3)),
            EmitSchema.deliveries(Effect.RevokeLanded(Seat(3)), after).map { it.seat },
            "the Revoke landing reached the wrong phones",
        )
    }

    /**
     * **R1: every actor gets one answer, and only an armed one is told it spent a Revoke** (D-216).
     *
     * Three actors touching the same live Resident: an armed Insider, an unarmed Insider, a
     * Resident. Each is answered with exactly one effect addressed to themselves; the armed one's is
     * `AbilityFired`, the other two's is `ContactAnswered`, and neither of those two reveals which
     * role touched. Only the armed touch revokes.
     */
    @Test
    fun `every touch answers its actor once and only an armed Insider is told AbilityFired`() {
        val base = armed()
        fun answerTo(state: GameState, actor: Seat): List<Effect> =
            reduce(state, Event.ContactMade(Tick(READY + 2), actor, Seat(3))).effects
                .filter { EmitSchema.audienceOf(it, state).any { s -> s.index == actor.index } }

        val armedState = reduce(base, Event.RevokeArmed(Tick(READY + 1), Seat(1))).state
        assertEquals(listOf<Effect>(Effect.AbilityFired(Seat(1), cooldownStarted = true)), answerTo(armedState, Seat(1)))
        assertEquals(listOf<Effect>(Effect.ContactAnswered(Seat(5))), answerTo(base, Seat(5)), "an unarmed Insider")
        assertEquals(listOf<Effect>(Effect.ContactAnswered(Seat(2))), answerTo(base, Seat(2)), "a Resident")

        assertTrue(reduce(armedState, Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3))).state.isRevoked(Seat(3)))
        assertFalse(reduce(base, Event.ContactMade(Tick(READY + 2), Seat(5), Seat(3))).state.isRevoked(Seat(3)))
    }

    @Test
    fun `a Resident contacting produces the same effect as an Insider contacting`() {
        val base = armed()
        val (_, insider) = run(base, listOf(
            Event.RevokeArmed(Tick(READY + 1), Seat(1)),
            Event.ContactMade(Tick(READY + 2), Seat(1), Seat(3)),
        ))
        val (_, resident) = run(base, listOf(
            Event.RevokeArmed(Tick(READY + 1), Seat(2)),
            Event.ContactMade(Tick(READY + 2), Seat(2), Seat(3)),
        ))
        // Same shape, differing only in the acting seat — never in whether an effect exists.
        assertEquals(insider.size, resident.size)
        assertEquals(insider.map { it::class.simpleName }, resident.map { it::class.simpleName })
    }

    @Test
    fun `arming emits an identical lamp effect for every seat`() {
        val (_, effects) = run(GameState.EMPTY, listOf(
            Event.RoundArmed(Tick(0), seed = 1L, seats = SEATS, insiders = INSIDERS)))
        val lamps = effects.filterIsInstance<Effect.LampSet>()
        assertEquals(8, lamps.size)
        assertEquals(1, lamps.map { it.luminance }.distinct().size,
            "a per-role luminance difference at arming is a tell delivered while everyone is clustered")
    }
}
