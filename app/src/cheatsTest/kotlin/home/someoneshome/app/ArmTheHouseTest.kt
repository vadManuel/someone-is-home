package home.someoneshome.app

import home.someoneshome.model.Effect
import home.someoneshome.model.GameState
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.ui.FlowModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **LIGHTS OUT arming a real round with nobody scripted afterwards** — [RoundCheat.armTheHouse].
 *
 * ### Why this method exists at all
 *
 * ⚠️ **Nothing in the application arms a round.** `FlowModel.lightsOut`'s entire body is
 * `push(ScreenId.Armed)` — presentation, and its own KDoc says so — and [RoundAuthority.arm] has
 * exactly one other caller in the tree, [RoundCheat.theRound], which is the bench script. So on a
 * phone a person launches, holding LIGHTS OUT walks the host's own screen to ARMED and arms
 * nothing: every other seat stands in the lobby forever. That was proved on five seats before this
 * method was written, and it is **E-D3-3's trigger half**, which fell off the tracked list at
 * revision ~34 when P3 closed the push half and the escalation was retired with it.
 *
 * This method is **test enablement standing in for a production path that does not exist**. The
 * engineering item — the host's own LIGHTS OUT arming the house — is still open.
 *
 * ### What is actually under test
 *
 * That it arms, and **that it does nothing else**. The distinguishing property against
 * [RoundCheat.theRound] is not that it is shorter; it is that no act of the round follows it and
 * the house's clock is never pushed. A version of this that quietly ran a shift would look correct
 * in every log until somebody wondered why the room was being played for them.
 */
class ArmTheHouseTest {

    private val seats = List(5) { Seat(it) }

    private fun inputs(ready: Boolean = true) = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = ready,
    )

    private class Bench(scope: CoroutineScope, inputs: ArmingInputs) {
        val pushed = mutableListOf<Effect>()
        val said = mutableListOf<String>()
        val house = RoundAuthority(
            inputs = { inputs },
            emit = { effect: Effect, _: GameState -> pushed += effect },
            note = { said += it },
        )
        val flow = FlowModel()
        val client = RoundClient(scope, flow, LineWatch {}) { said += it }
        val round = RoundCheat(client, flow, house, isHost = { true }, ledger = null) { said += it }
    }

    /**
     * **A child [Job] that is cancelled afterwards, and not `runBlocking`'s own scope** —
     * `RoundClientTest.bench`'s idiom, borrowed for the reason it was written.
     *
     * ⚠️ **The first version of this file handed the bench `runBlocking`'s scope directly and every
     * test in it HUNG.** [RoundClient] keeps a coroutine for the life of the round, so `runBlocking`
     * waited on a child that is never meant to finish — a build sitting at
     * `:app:iosSimulatorArm64Test` doing nothing, with no failure and nothing to read. That is the
     * sixth member of the silent-instrument family this run keeps finding, after A4's, L3's, D3's
     * two and Restore's, and it is exactly what `StaleSeatTest`'s KDoc warns about: **a guard that
     * hangs is a guard that will one day be stopped by a CI timeout and read as infrastructure.**
     */
    private fun bench(ready: Boolean = true, block: suspend (Bench) -> Unit) = runBlocking {
        val job = Job()
        try {
            block(Bench(CoroutineScope(coroutineContext + job), inputs(ready)))
        } finally {
            job.cancel()
        }
    }

    /** Arming is real: the house ends up holding a round with every seat in it. */
    @Test
    fun theHouseIsArmedAndEverySeatIsInTheRound() = bench { bench ->
        bench.round.armTheHouse(RoundCheat.SEED)

        assertTrue(bench.house.armed, "LIGHTS OUT armed no round at all")
        assertEquals(
            seats, bench.house.state.seats,
            "the round was armed with a different set of seats than the lobby held",
        )
        assertEquals(
            seats, bench.pushed.filterIsInstance<Effect.OpeningMessage>().map { it.seat },
            "the opening message did not reach every seat exactly once",
        )
    }

    /**
     * **And then it stops.** No contact, no report, no meeting, no ability, no ending — every one
     * of which [RoundCheat.theRound] mints. The evening after this belongs to the people holding
     * the phones.
     */
    @Test
    fun nothingIsPlayedForTheRoomAfterTheLightsGoOut() = bench { bench ->
        bench.round.armTheHouse(RoundCheat.SEED)

        val acted = bench.pushed.filter {
            it is Effect.AbilityFired || it is Effect.MeetingOpened ||
                it is Effect.StaticOpened || it is Effect.EgressOpened ||
                it is Effect.RoundEnded
        }
        assertEquals(
            emptyList(), acted,
            "arming played the round for the room: it minted ${acted.map { it::class.simpleName }}",
        )
    }

    /**
     * **The house's clock is left on real time**, which is the other half of *nobody is scripted*.
     *
     * `theRound` moves the driver's view of real time through [RoundCheat.skewNanos] so a rehearsal
     * does not stand in the dark for a ninety-second cooldown five times an evening. An evening with
     * people in it must run at their pace, and a skew would end a meeting they were still standing
     * in — which is the mistake Rehearsal 2 recorded when it pushed the clock through a live crisis.
     */
    @Test
    fun theHouseClockIsLeftOnRealTime() = bench { bench ->
        bench.round.armTheHouse(RoundCheat.SEED)

        assertEquals(
            0L, bench.round.skewNanos,
            "arming pushed the house's clock ahead of the room it is being played in",
        )
    }

    /**
     * A lobby that is short is refused, out loud, on the host's own phone in a lit room — which is
     * where `armingFor` says a refusal belongs. The gate is `LobbyDesk.readyToArm`, the same one
     * the button reads.
     */
    @Test
    fun aLobbyThatIsNotReadyArmsNothingAndSaysSo() = bench(ready = false) { bench ->
        bench.round.armTheHouse(RoundCheat.SEED)

        assertFalse(bench.house.armed, "a lobby that was not ready armed a round anyway")
        assertTrue(
            bench.said.any { it.contains("WILL NOT ARM") },
            "the refusal never reached the host's own phone: ${bench.said}",
        )
    }
}
