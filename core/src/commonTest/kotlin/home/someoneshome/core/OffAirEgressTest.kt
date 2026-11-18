package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.Effect
import home.someoneshome.model.Egress
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * **The Egress pauses while the house is off the air** (the owner's 2026-11-18 ruling, Q4 option A;
 * D-281), at the rules: [Event.OffAirEnded] moves a running Egress's deadline by the gap, tells
 * every phone the number again, and touches nothing else.
 */
class OffAirEgressTest {

    private val seats = (0 until 5).map { Seat(it) }
    private val second = Balance.TICKS_PER_SECOND

    private fun armed(): GameState =
        reduce(GameState.EMPTY, Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1)))).state

    private fun burning(firedAt: Long): GameState = armed().copy(
        egress = Egress.fired(
            Tick(firedAt), EgressType.Beacon, listOf(MarkerId("A"), MarkerId("B")), Balance.EGRESS_TIMER,
            pulseFrom = 86_402_000L,
        ),
    )

    private fun admitted(state: GameState, event: Event) =
        assertIs<Admission.Admitted>(admit(state, event), "the house's return was refused").reduction

    /** Fired at 0, off the air at 100 s with 20 s left, back at 400 s: still 20 s left. */
    @Test
    fun `a running Egress is held for the whole gap and every phone is told the number again`() {
        val state = burning(0)
        val back = admitted(state, Event.OffAirEnded(Tick(400 * second), since = Tick(100 * second)))

        val egress = back.state.egress!!
        assertEquals(
            Tick(Balance.EGRESS_TIMER + 300 * second), egress.deadline,
            "the deadline did not move by exactly the gap the house was off the air",
        )
        assertEquals(null, egress.pausedAt, "the Egress came back paused")
        assertEquals(
            listOf<Effect>(Effect.EgressHeld(20 * second, running = true, haptic = Haptic.Short)),
            back.effects,
            "every phone was not told the Egress's number again on the house's return",
        )
        // The per-seat cooldowns keep wall time (D-230); the Egress's own moves with it (D-282).
        assertEquals(state.cooldowns.map { it.readyAt }, back.state.cooldowns.map { it.readyAt })
        assertEquals(state.egressReadyAt + 300 * second, back.state.egressReadyAt, "the Egress cooldown did not move by the gap")
    }

    /** A gap that began before the fire counts only from the fire. */
    @Test
    fun `a gap that began before the Egress counts only from its fire`() {
        val state = burning(50 * second)
        val back = admitted(state, Event.OffAirEnded(Tick(60 * second), since = Tick(10 * second))).state
        assertEquals(Tick(50 * second + Balance.EGRESS_TIMER + 10 * second), back.egress!!.deadline)
    }

    /** One held by a meeting stays held, and with no Egress nothing moves and nothing is said. */
    @Test
    fun `a paused Egress and a quiet house are left alone`() {
        val paused = burning(0).let { it.copy(egress = it.egress!!.pausedAt(Tick(30 * second))) }
        val held = admitted(paused, Event.OffAirEnded(Tick(400 * second), since = Tick(100 * second)))
        assertEquals(paused.egress!!.deadline, held.state.egress!!.deadline, "a meeting's pause was moved by the gap")
        assertEquals(Tick(30 * second), held.state.egress!!.pausedAt, "the gap resumed an Egress a meeting holds")
        assertEquals(emptyList(), held.effects)

        val quiet = admitted(armed(), Event.OffAirEnded(Tick(400 * second), since = Tick(100 * second)))
        assertEquals(emptyList(), quiet.effects, "a return with no Egress running said something")
    }
}
