package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.Haptic
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.IdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **T4 through the production funnel: the idle timer is off from arming until the round ends or
 * the phone hangs up** (the owner's 2026-11-15 ruling, D-221).
 *
 * Pushes arrive at a real [RoundOverTransport] encoded as the house encodes them, and the idle
 * timer at the far end records what it was told.
 */
class ScreenAwakeTest {

    private class Timer : IdleTimer {
        override var disabled: Boolean = false
    }

    private val seed = ContactSeed("0123456789abcdef0123456789abcdef")

    private suspend fun CoroutineScope.pushes(round: RoundOverTransport, vararg messages: RoundMessage) {
        val draining = launch { round.run() }
        messages.forEach { message ->
            val seq = round.held + 1
            round.frameArrived(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message))))
            withTimeout(5_000) { while (round.held < seq) delay(1) }
        }
        draining.cancel()
    }

    private fun CoroutineScope.transport(timer: Timer) = RoundOverTransport(
        this, FlowModel(PanelState(screen = ScreenId.Home)), buzzer = HouseBuzzer(SilentHaptics),
        send = { }, radio = NoRadio, wake = ScreenWake(timer),
    )

    @Test
    fun armingHoldsTheScreenAwakeAndTheRoundsEndLetsItSleep() = runBlocking {
        val timer = Timer()
        val round = transport(timer)
        assertFalse(timer.disabled, "the screen was held awake in the lobby, before arming")
        pushes(round, RoundMessage.ContactSeedIssued(seed))
        assertTrue(timer.disabled, "armed, and the phone can still lock itself")
        pushes(round, RoundMessage.RoundEnded(Winner.Residents, WinRoute.InsidersRestrained, Haptic.Long, integrity = 60))
        assertFalse(timer.disabled, "the round ended and the screen is still held awake")
    }

    @Test
    fun aPhoneThatHangsUpMidRoundLetsTheScreenSleep() = runBlocking {
        val timer = Timer()
        val round = transport(timer)
        pushes(round, RoundMessage.ContactSeedIssued(seed))
        assertTrue(timer.disabled, "the fixture never held the screen awake")
        // What `LobbyOverTransport.hangUp` reaches through `RoundSeam.left`.
        round.roundEnded()
        assertFalse(timer.disabled, "a phone that left the round kept its screen awake")
    }
}
