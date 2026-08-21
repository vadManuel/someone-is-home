package home.someoneshome.core

import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SEATS = (0 until 8).map { Seat(it) }
private val INSIDERS = listOf(Seat(1), Seat(5))

private fun armed(): GameState =
    reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 42L, seats = SEATS, insiders = INSIDERS)).state

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
            Event.RevokeArmed(Tick(1), Seat(1)),
            Event.ContactMade(Tick(2), Seat(1), Seat(3)),
            Event.SubroutineReturned(Tick(3), Seat(2), home.someoneshome.model.MarkerId("m1"), listOf(1, 2)),
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
            Event.RevokeArmed(Tick(1), Seat(1)),
            Event.ContactMade(Tick(2), Seat(1), Seat(3)),
        ))

        // A target already revoked. Same actor, same ticks.
        val pre = run(base, listOf(
            Event.RevokeArmed(Tick(0), Seat(5)),
            Event.ContactMade(Tick(0), Seat(5), Seat(3)),
        )).first
        val (_, dead) = run(pre, listOf(
            Event.RevokeArmed(Tick(1), Seat(1)),
            Event.ContactMade(Tick(2), Seat(1), Seat(3)),
        ))

        assertEquals(live.toString(), dead.toString(),
            "the effect stream differs, so the absence of an effect reveals the target was already revoked")
    }

    @Test
    fun `contacting without arming still spends the ability visibly`() {
        val (_, unarmed) = run(armed(), listOf(Event.ContactMade(Tick(2), Seat(1), Seat(3))))
        assertEquals(
            listOf(Effect.AbilityFired(Seat(1), cooldownStarted = true)).toString(),
            unarmed.toString(),
        )
    }

    @Test
    fun `a Resident contacting produces the same effect as an Insider contacting`() {
        val base = armed()
        val (_, insider) = run(base, listOf(
            Event.RevokeArmed(Tick(1), Seat(1)),
            Event.ContactMade(Tick(2), Seat(1), Seat(3)),
        ))
        val (_, resident) = run(base, listOf(
            Event.RevokeArmed(Tick(1), Seat(2)),
            Event.ContactMade(Tick(2), Seat(2), Seat(3)),
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
