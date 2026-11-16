package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **T1: every phone that touched is delivered exactly one effect per touch, whatever the seat order
 * and whether or not a Revoke lands** (the owner's 2026-11-15 ruling, D-220).
 *
 * Both halves go through `admit`, in the resolver's seat order, and what counts is what the emit
 * boundary actually delivers, read off the state after each half. Before T1 a target seated below
 * its Insider was delivered two: its own `ContactAnswered`, then `RevokeLanded`.
 */
class TouchAnsweredOnceTest {

    private val seats = (0 until 6).map { Seat(it) }

    private fun round(insider: Seat): GameState =
        reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 7L, seats = seats, insiders = listOf(insider))).state

    private val ready = Balance.REVOKE_COOLDOWN / 2 + 1

    /** Both halves of every touch in [pairs], at one tick and in seat order; what each seat was delivered. */
    private fun touch(state: GameState, at: Long, vararg pairs: Pair<Int, Int>): Pair<GameState, Map<Int, List<Effect>>> {
        val halves = pairs.flatMap { (a, b) -> listOf(Seat(a) to Seat(b), Seat(b) to Seat(a)) }
            .sortedWith(compareBy<Pair<Seat, Seat>> { it.first.index }.thenBy { it.second.index })
        var s = state
        val got = seats.associate { it.index to mutableListOf<Effect>() }
        for ((actor, target) in halves) {
            val admission = admit(s, Event.ContactMade(Tick(at), actor, target))
            check(admission is Admission.Admitted) { "the fixture's half $actor -> $target was refused: $admission" }
            s = admission.reduction.state
            for (effect in admission.reduction.effects) {
                for (d in EmitSchema.deliveries(effect, s)) got.getValue(d.seat.index) += effect
            }
        }
        return s to got
    }

    private fun armedAt(state: GameState, insider: Seat): GameState {
        val admission = admit(state, Event.RevokeArmed(Tick(ready), insider))
        check(admission is Admission.Admitted) { "the fixture could not arm: $admission" }
        val s = admission.reduction.state
        assertTrue(s.cooldownArmed.any { it.index == insider.index }, "the fixture never armed the Insider")
        return s
    }

    @Test
    fun `a landed Revoke's target is delivered RevokeLanded and nothing else in both seat orders`() {
        for ((insider, target) in listOf(Seat(1) to Seat(3), Seat(4) to Seat(2))) {
            val order = if (insider.index < target.index) "Insider below" else "Insider above"
            val (after, got) = touch(armedAt(round(insider), insider), ready + 1L, insider.index to target.index)
            assertTrue(after.isRevoked(target), "$order: the armed touch revoked nobody, so it measured nothing")
            assertEquals(
                listOf<Effect>(Effect.RevokeLanded(target)), got.getValue(target.index),
                "$order: the Revoked target was delivered something other than exactly its Revoke landing",
            )
            assertEquals(
                listOf<Effect>(Effect.AbilityFired(insider, cooldownStarted = true)), got.getValue(insider.index),
                "$order: the armed Insider was delivered something other than exactly its spent Revoke",
            )
            for (bystander in seats.filter { it != insider && it != target }) {
                assertEquals(emptyList(), got.getValue(bystander.index), "$order: seat ${bystander.index} did not touch")
            }
        }
    }

    @Test
    fun `an unarmed touch delivers one ContactAnswered to each phone in both seat orders`() {
        for ((insider, other) in listOf(Seat(1) to Seat(3), Seat(4) to Seat(2))) {
            val order = if (insider.index < other.index) "Insider below" else "Insider above"
            val (after, got) = touch(round(insider), ready + 1L, insider.index to other.index)
            assertTrue(after.revoked.isEmpty(), "$order: an unarmed touch revoked somebody")
            for (seat in listOf(insider, other)) {
                assertEquals(
                    listOf<Effect>(Effect.ContactAnswered(seat)), got.getValue(seat.index),
                    "$order: seat ${seat.index} was not delivered exactly one answer",
                )
            }
        }
    }

    /**
     * **A huddle of three is three touches, and each phone is delivered one effect per touch it was
     * in.** The halves of one touch are not adjacent here (seat order across pairs), which is what
     * the touch memory is for.
     */
    @Test
    fun `a huddle around an armed Insider delivers one effect per touch to every phone`() {
        val insider = Seat(2)
        val (after, got) = touch(armedAt(round(insider), insider), ready + 1L, 0 to 1, 0 to 2, 1 to 2)
        assertEquals(listOf(0), after.revoked.map { it.index }, "the huddle's Revoke landed somewhere unexpected")
        for (seat in listOf(0, 1, 2)) {
            assertEquals(2, got.getValue(seat).size, "seat $seat was in two touches and was delivered ${got.getValue(seat)}")
        }
        assertEquals(listOf<Effect>(Effect.ContactAnswered(Seat(0)), Effect.RevokeLanded(Seat(0))), got.getValue(0))
    }

    /** The touch memory is the current tick's: the same pair touching at the next tick is a new touch. */
    @Test
    fun `the same pair at a later tick is answered again`() {
        val start = round(Seat(1))
        val (once, first) = touch(start, ready + 1L, 1 to 3)
        val (_, second) = touch(once, ready + 2L, 1 to 3)
        assertEquals(first, second, "a second touch of the same pair was not answered as one")
    }
}
