package home.someoneshome.core

import home.someoneshome.model.ArmedTouch
import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-290: a landed Revoke takes ARMED down at once, and both phones buzz Long** (the owner's
 * 2026-11-18 ruling, on D-286's question).
 *
 * What each phone that touched is *delivered*, read off the emit boundary after each half as
 * `TouchAnsweredOnceTest` reads it: still exactly one effect per phone per touch, and now the armed
 * phone's says whether its Revoke landed, and the phone it landed on is buzzed Long. The buzz rides
 * the effect (D-134), so what is asserted here is what `HouseBuzzer` plays.
 */
class LandedRevokeTest {

    private val seats = (0 until 8).map { Seat(it) }
    private val ready = Balance.REVOKE_COOLDOWN / 2

    private fun round(vararg insiders: Int, seed: Long = 7L): GameState = reduce(
        GameState.EMPTY,
        Event.RoundArmed(Tick(0), seed = seed, seats = seats, insiders = insiders.map { Seat(it) }),
    ).state

    private fun put(state: GameState, event: Event): Pair<GameState, List<Effect>> {
        val admission = admit(state, event)
        check(admission is Admission.Admitted) { "the fixture's $event was refused: $admission" }
        return admission.reduction.state to admission.reduction.effects
    }

    private fun armed(state: GameState, vararg insiders: Int): GameState =
        insiders.fold(state) { s, i -> put(s, Event.RevokeArmed(Tick(ready), Seat(i))).first }
            .also { s -> insiders.forEach { i -> assertTrue(s.armedAt(Seat(i), Tick(ready + 1)), "the fixture never armed seat $i") } }

    /** Both halves of a touch between [a] and [b], in seat order; what each seat was delivered. */
    private fun touch(state: GameState, at: Long, a: Int, b: Int): Pair<GameState, Map<Int, List<Effect>>> {
        val halves = listOf(Seat(a) to Seat(b), Seat(b) to Seat(a)).sortedBy { it.first.index }
        var s = state
        val got = seats.associate { it.index to mutableListOf<Effect>() }
        for ((actor, target) in halves) {
            val (next, effects) = put(s, Event.ContactMade(Tick(at), actor, target))
            s = next
            for (effect in effects) for (d in EmitSchema.deliveries(effect, s)) got.getValue(d.seat.index) += effect
        }
        return s to got
    }

    private fun buzzes(effects: List<Effect>): List<Haptic?> = effects.map {
        when (it) {
            is Effect.AbilityFired -> it.haptic
            is Effect.RevokeLanded -> it.haptic
            else -> null
        }
    }

    @Test
    fun `a landed Revoke tells the armed phone it landed and buzzes both phones Long in both seat orders`() {
        for ((insider, target) in listOf(1 to 3, 4 to 2)) {
            val order = if (insider < target) "Insider below" else "Insider above"
            val (after, got) = touch(armed(round(insider), insider), ready + 1, insider, target)
            assertTrue(after.isRevoked(Seat(target)), "$order: the armed touch revoked nobody, so it measured nothing")
            assertEquals(
                listOf<Effect>(Effect.AbilityFired(Seat(insider), cooldownStarted = true, touch = ArmedTouch.Landed)),
                got.getValue(insider),
                "$order: the armed phone was not told its Revoke landed",
            )
            assertEquals(listOf<Effect>(Effect.RevokeLanded(Seat(target), Haptic.Long)), got.getValue(target), order)
            assertEquals(listOf<Haptic?>(Haptic.Long), buzzes(got.getValue(insider)), "$order: the Insider's phone did not buzz Long")
            assertEquals(listOf<Haptic?>(Haptic.Long), buzzes(got.getValue(target)), "$order: the Revoked phone did not buzz Long")
            assertTrue(after.revokeArms.isEmpty(), "$order: the house kept the arm the touch spent")
        }
    }

    /** The Revoke a Resident walks into: the armed phone is the target of the half that lands it. */
    @Test
    fun `a Revoke landed by the higher seat's arm buzzes the same two phones`() {
        val (after, got) = touch(armed(round(4), 4), ready + 1, 1, 4)
        assertTrue(after.isRevoked(Seat(1)))
        assertEquals(listOf<Effect>(Effect.AbilityFired(Seat(4), true, ArmedTouch.Landed)), got.getValue(4))
        assertEquals(listOf<Effect>(Effect.RevokeLanded(Seat(1), Haptic.Long)), got.getValue(1))
    }

    /**
     * **The owner's ruling: the Long on a landed Revoke only.** An armed touch on a seat already out
     * spends the arm (ARMED comes down) and buzzes neither phone; the couch phone is sent its silent
     * re-assertion, as before (D-156).
     */
    @Test
    fun `an armed touch on a seat already out is spent and buzzes nobody`() {
        val pre = touch(armed(round(1, 5), 5), ready + 1, 5, 3).first
        assertTrue(pre.isRevoked(Seat(3)), "the fixture never revoked seat 3")
        val armedAgain = put(pre, Event.RevokeArmed(Tick(ready + 2), Seat(1))).first
        val (after, got) = touch(armedAgain, ready + 3, 1, 3)
        assertEquals(listOf<Effect>(Effect.AbilityFired(Seat(1), true, ArmedTouch.Spent)), got.getValue(1))
        assertEquals(listOf<Effect>(Effect.RevokeLanded(Seat(3), haptic = null)), got.getValue(3))
        assertEquals(listOf<Haptic?>(null), buzzes(got.getValue(1)), "a touch that landed nothing buzzed the armed phone")
        assertEquals(listOf<Haptic?>(null), buzzes(got.getValue(3)), "a phone already out buzzed again")
        assertTrue(after.revokeArms.isEmpty(), "the spent arm survived its touch")
    }

    /** Two armed Insiders: the lower seat's lands (D-220), and the higher seat is Revoked, not "spent". */
    @Test
    fun `between two armed phones the one that landed is told so and the other is Revoked`() {
        val (after, got) = touch(armed(round(1, 4), 1, 4), ready + 1, 1, 4)
        assertTrue(after.isRevoked(Seat(4)))
        assertEquals(listOf<Effect>(Effect.AbilityFired(Seat(1), true, ArmedTouch.Landed)), got.getValue(1))
        assertEquals(listOf<Effect>(Effect.RevokeLanded(Seat(4), Haptic.Long)), got.getValue(4))
    }

    /** An unarmed touch, and a touch on a couch phone by an unarmed one: no buzz, as today. */
    @Test
    fun `a touch that revoked nobody buzzes nobody`() {
        val (_, plain) = touch(round(1), ready + 1, 1, 3)
        assertEquals(listOf<Effect>(Effect.ContactAnswered(Seat(1))), plain.getValue(1))
        assertEquals(listOf<Effect>(Effect.ContactAnswered(Seat(3))), plain.getValue(3))

        val pre = touch(armed(round(1), 1), ready + 1, 1, 3).first
        val (_, couch) = touch(pre, ready + 2, 2, 3)
        assertEquals(listOf<Effect>(Effect.ContactAnswered(Seat(2))), couch.getValue(2))
        assertEquals(listOf<Effect>(Effect.RevokeLanded(Seat(3), haptic = null)), couch.getValue(3))
    }

    /**
     * **Nothing about a third seat reaches the armed phone's answer** (the brief's STOP condition).
     * The answer is built from the touch's two phones: who else is out, who else is armed and who
     * else is an Insider change nothing in it, nor in what the Revoked phone is sent.
     */
    @Test
    fun `the armed answer reads only the touch's two phones`() {
        fun answers(state: GameState): Map<Int, List<Effect>> = touch(state, ready + 3, 1, 3).second.filterKeys { it == 1 || it == 3 }
        val plain = answers(armed(round(1), 1))
        val third = listOf(
            // A third seat out, by another Insider's landed Revoke.
            touch(armed(round(1, 5), 1, 5), ready + 1, 5, 2).first,
            // A third seat armed and not touching.
            armed(round(1, 5), 1, 5),
            // A third seat an Insider, unarmed.
            armed(round(1, 4), 1),
        )
        for ((i, state) in third.withIndex()) {
            assertEquals(plain, answers(state), "case $i: a third seat's state reached the touch's answers")
        }
    }
}
