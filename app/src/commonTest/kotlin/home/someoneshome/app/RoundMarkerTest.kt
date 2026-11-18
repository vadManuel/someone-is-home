package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.NoRadio
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.SilentHaptics
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelRole
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The round on every push, read by the phone's round half** (V1 of the second review, D-267).
 *
 * Over loopback the house's standing walks a phone that missed a round's end back to the lobby the
 * moment it is seated (`MissedRoundEndTest`), before any push of the next round can reach it. This
 * is the second defence, on its own: a phone holding round one's sequence that is pushed round two
 * directly. Pushes arrive at a real [RoundOverTransport] encoded as the house encodes them, over a
 * real [FlowModel].
 */
class RoundMarkerTest {

    private val roundOneSeed = ContactSeed("0123456789abcdef0123456789abcdef")
    private val roundTwoSeed = ContactSeed("fedcba9876543210fedcba9876543210")

    private class Phone(scope: CoroutineScope) {
        val flow = FlowModel(PanelState(screen = ScreenId.Lobby))
        val sent = mutableListOf<String>()
        val round = RoundOverTransport(
            scope, flow, buzzer = HouseBuzzer(SilentHaptics),
            send = { }, radio = NoRadio, wake = ScreenWake(NoIdleTimer),
            onOutbound = { sent += it },
        )

        fun push(round: Int, seq: Long, message: RoundMessage) =
            this.round.frameArrived(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message, round))))

        val gaps: List<RoundBody.Gap> get() = sent.mapNotNull { RoundWire.decodeOrNull(it) as? RoundBody.Gap }
    }

    private suspend fun until(what: String, condition: () -> Boolean) {
        try {
            withTimeout(5_000) { while (!condition()) delay(1) }
        } catch (late: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("never happened: $what", late)
        }
    }

    /**
     * **GUARD (V1, D-267): round two's pushes, numbered from 1, land on a phone holding fifty of round
     * one's.** It ends its round there (the report it owed from round one goes with it), applies
     * round two's role, roster and seed, and a late push of round one changes nothing.
     */
    @Test
    fun aLaterRoundsPushEndsTheRoundThePhoneIsStillIn() = runBlocking<Unit> {
        val phone = Phone(this)
        val draining = launch { phone.round.run() }
        try {
            phone.push(1, 1, RoundMessage.RoleRevealed(insider = false))
            phone.push(1, 2, RoundMessage.ContactSeedIssued(roundOneSeed))
            for (seq in 3L..50L) phone.push(1, seq, RoundMessage.SystemIntegrityBatched(seq.toInt()))
            until("round one's fifty pushes were applied") { phone.round.held == 50L }
            phone.round.leftTheForeground()
            assertEquals(1, phone.round.owing, "the fixture owes nothing from round one, so its end proves nothing")
            assertEquals(PanelRole.Resident, phone.flow.state.role)
            phone.flow.lobby.line.type("round one's line")

            // Round two, from 1, to a phone that never heard round one end.
            phone.push(2, 1, RoundMessage.RoleRevealed(insider = true))
            phone.push(2, 2, RoundMessage.RosterIssued(listOf("ALBA", "BRUNO", "CYRUS", "DEVI", "ENZO")))
            phone.push(2, 3, RoundMessage.ContactSeedIssued(roundTwoSeed))
            until("round two's opening was applied", ) { phone.round.held == 3L }

            assertEquals(PanelRole.Insider, phone.flow.state.role, "round two's role was dropped as a duplicate of round one's")
            assertEquals(5, phone.flow.state.roster?.size, "round two's roster was dropped")
            assertTrue(phone.round.uploading, "round two's seed was dropped")
            assertEquals(0, phone.round.owing, "a report owed from round one went into round two")
            assertEquals("", phone.flow.lobby.line.text, "round one's line is still typed")
            assertTrue(phone.gaps.isEmpty(), "the phone asked for round two from round one's place: ${phone.gaps}")

            // A late push of round one, a replay in flight when the round ended, is stale.
            phone.push(1, 51, RoundMessage.SystemIntegrityBatched(51))
            phone.push(2, 4, RoundMessage.SystemIntegrityBatched(4))
            until("round two's next push was applied") { phone.round.held == 4L }
            assertEquals(4, phone.flow.state.integrityPercent, "round one's late push was applied in round two")
        } finally {
            draining.cancel()
        }
    }

    /**
     * **GUARD (V2, D-268): a phone behind a hole asks once for that point, however many pushes follow
     * the hole before the replay arrives**, and names the round the point is in.
     */
    @Test
    fun aHoleIsAskedAboutOncePerPoint() = runBlocking<Unit> {
        val phone = Phone(this)
        val draining = launch { phone.round.run() }
        try {
            phone.push(3, 1, RoundMessage.SystemIntegrityBatched(1))
            until("the first push was applied") { phone.round.held == 1L }
            // Push 2 is lost; a burst of forty follows it.
            for (seq in 3L..42L) phone.push(3, seq, RoundMessage.SystemIntegrityBatched(seq.toInt()))
            delay(200)
            assertEquals(listOf(RoundBody.Gap(1, 3)), phone.gaps, "the phone asked more than once for one hole")
            // The replay fills it, and the next hole is a new point.
            for (seq in 2L..42L) phone.push(3, seq, RoundMessage.SystemIntegrityBatched(seq.toInt()))
            until("the replay was applied") { phone.round.held == 42L }
            phone.push(3, 44, RoundMessage.SystemIntegrityBatched(44))
            phone.push(3, 45, RoundMessage.SystemIntegrityBatched(45))
            delay(200)
            assertEquals(listOf(RoundBody.Gap(1, 3), RoundBody.Gap(42, 3)), phone.gaps)
        } finally {
            draining.cancel()
        }
    }
}
