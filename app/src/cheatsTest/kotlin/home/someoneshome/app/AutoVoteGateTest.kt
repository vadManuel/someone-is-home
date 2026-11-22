package home.someoneshome.app

import home.someoneshome.model.Haptic
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.platform.launchSwitch
import home.someoneshome.ui.FlowModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **A phone a person holds never votes by itself** (D-232, D-237).
 *
 * The bug: `CheatRoot` wires `RoundCheat.react` to every phone's pushes, and `react` answered the
 * ballot opening with `SelectVote(caller)` whatever the phone was launched with. So every playtest
 * phone a person launched from the springboard voted for the meeting's caller the instant the vote
 * opened, before its owner touched anything.
 *
 * Wired the way `CheatRoot` wires it, exactly: `roundClient.onMessage = round::react`, and the
 * pushes arrive as encoded frames off a socket. What goes up is read off the phone's own way up.
 */
class AutoVoteGateTest {

    private class Seat1(scope: CoroutineScope, armed: Boolean?) {
        val up = mutableListOf<IntentMessage>()
        val flow = FlowModel()
        val client = RoundClient(
            scope, flow, LineWatch {}, HouseBuzzer(SilentHaptics), NoRadio, ScreenWake(NoIdleTimer),
        ) {}.also { client ->
            client.linked { frame ->
                val body = (frame as? TransportFrame.Carry)?.body ?: return@linked
                (RoundWire.decodeOrNull(body) as? RoundBody.Act)?.let { up += it.intent }
            }
        }

        /** `null` is the launch environment's own answer, which is what `CheatRoot` reads. */
        val round = if (armed == null) {
            RoundCheat(client, flow, authority = null, isHost = { false }) {}
        } else {
            RoundCheat(client, flow, authority = null, isHost = { false }, armed = armed) {}
        }.also { client.onMessage = it::react }

        fun push(seq: Long, message: RoundMessage) = client.round.frameArrived(
            TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message)))
        )

        /** A meeting called by seat 2, through to the ballot opening. */
        suspend fun theBallotOpens() {
            push(1, RoundMessage.MeetingOpened(Seat(2), MeetingTrigger.MeetingCard, Haptic.Short))
            push(2, RoundMessage.MeetingPhaseOpened(MeetingPhase.Vote, voteWindowSeconds = 30, discussionSeconds = null, haptic = Haptic.Short))
            delay(SETTLE_MILLIS)
        }
    }

    private fun bench(armed: Boolean?, block: suspend (Seat1) -> Unit) = runBlocking {
        val job = Job()
        try {
            block(Seat1(CoroutineScope(coroutineContext + job), armed))
        } finally {
            job.cancel()
        }
    }

    /** **GUARD: launched without the switch, the phone casts no vote when the ballot opens.** */
    @Test
    fun aPhoneLaunchedWithoutTheRoundSwitchCastsNoVote() = bench(armed = null) { seat ->
        assertFalse(launchSwitch(RoundCheat.ROUND_SWITCH), "this test process was launched with the round switch")
        assertFalse(seat.round.armed, "a phone launched without the switch reads as a bench seat")
        seat.theBallotOpens()
        assertTrue(
            seat.up.none { it is IntentMessage.SelectVote || it is IntentMessage.LockVote },
            "a phone nobody touched voted by itself: ${seat.up}",
        )
    }

    /**
     * **The control: a bench seat still votes**, so the guard above can see a vote when there is one.
     * Without it, a test that read the wrong way up would pass the guard for ever.
     */
    @Test
    fun aBenchSeatStillVotesForTheCaller() = bench(armed = true) { seat ->
        seat.theBallotOpens()
        assertEquals(listOf<IntentMessage>(IntentMessage.SelectVote(Seat(2))), seat.up.filterIsInstance<IntentMessage.SelectVote>(), "the bench seat did not vote")
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
    }
}
