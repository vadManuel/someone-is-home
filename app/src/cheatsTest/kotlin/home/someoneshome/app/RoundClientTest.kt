package home.someoneshome.app

import home.someoneshome.model.Haptic
import home.someoneshome.model.InsiderNamed
import home.someoneshome.model.Seat
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.platform.RecordingHaptics
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
 * **What a phone writes down about what it was told — and the two things it must never write.**
 *
 * [RoundClient] exists so a device console can be evidence: five phones on a real network is where
 * *this reached every seat* and *this reached one seat* are actually different, and from a Mac an
 * absent push and an absent connection look identical. That makes the log load-bearing, and a
 * load-bearing log is a new place for the game's two secrets to end up.
 *
 * The reveal is where they meet. It is the one push in the round that legitimately carries names
 * **and** one lines (D-116) — so a logger that prints what it receives would write every player's
 * confession into a file on a Mac, on the one frame in the game where doing so looks correct.
 */
class RoundClientTest {

    /** Deliberately distinctive, so a sweep for them cannot pass by matching something ordinary. */
    private val name = "PRIYA"
    private val line = "i still have marcus spare key and i used it"

    private class Bench(val scope: CoroutineScope) {
        val said = mutableListOf<String>()
        val watch = LineWatch { said += it }
        val flow = FlowModel()
        /** The session's motor, recorded — what this seat's pushes actually did to the hand. */
        val motor = RecordingHaptics()
        val client = RoundClient(scope, flow, watch, HouseBuzzer(motor), home.someoneshome.platform.NoRadio) { said += it }

        /** One push, as it would really arrive: encoded, wrapped, and off a socket. */
        fun push(seq: Long, message: RoundMessage) = client.round.frameArrived(
            TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message)))
        )
    }

    private fun bench(block: suspend (Bench) -> Unit) = runBlocking {
        val job = Job()
        try {
            block(Bench(CoroutineScope(coroutineContext + job)))
        } finally {
            job.cancel()
        }
    }

    /**
     * **The reveal is counted and never quoted.**
     *
     * Both halves are asserted, and the second is what makes the first mean anything: the log has
     * to *say the reveal arrived* — otherwise a client that silently dropped the most important
     * push in the round would pass this test — and it has to contain neither the name nor the line
     * that were really on it.
     */
    @Test
    fun theRevealIsCountedAndNeitherTheNameNorTheLineIsPrinted() = bench { b ->
        b.push(1, RoundMessage.RoundEnded(Winner.Residents, WinRoute.SystemIntegrityCleared, Haptic.Short))
        b.push(2, RoundMessage.InsidersRevealed(listOf(InsiderNamed(Seat(3), name, line))))
        delay(SETTLE)

        val log = b.said.joinToString("\n")
        assertTrue(log.contains("THE REVEAL"), "the reveal reached this phone and the log is silent")
        assertTrue(log.contains("1 INSIDER(S) NAMED"), "the reveal was not counted: $log")
        assertTrue(log.contains("1 WITH A LINE"), "the log does not say a line was published: $log")
        assertFalse(log.contains(name), "the log printed the name on the reveal")
        assertFalse(log.contains(line), "the log printed a one line: it is now a file on a Mac")
    }

    /**
     * **Every push is tallied by kind, and the tally is what a five-seat log is read for.**
     *
     * *Did the arming reach all five* and *did the verdict reach only one* are the same question
     * asked of five logs at once, and the answer is a count per kind on each of them.
     */
    @Test
    fun everyPushIsTalliedByKindAndTheHeldSequenceMovesWithIt() = bench { b ->
        b.push(1, RoundMessage.OpeningMessage(Haptic.Short))
        b.push(2, RoundMessage.CheckInProgressed(present = 2, expected = 5))
        b.push(3, RoundMessage.CheckInProgressed(present = 3, expected = 5))
        delay(SETTLE)

        val verdict = b.client.verdict()
        assertTrue(verdict.contains("OpeningMessage=1"), "the opening message was not tallied: $verdict")
        assertTrue(verdict.contains("CheckInProgressed=2"), "the gate was not tallied twice: $verdict")
        assertTrue(verdict.contains("HELD 3"), "this phone did not advance to push 3: $verdict")
    }

    /**
     * **A hole is noticed, is loud to the authority, and says nothing to the player.**
     *
     * P1's rule at the client end, checked through the object that actually owns the inbox: push 3
     * arriving over a missing 2 is dropped rather than applied, because a screen drawn from a
     * newer push with older fields standing underneath it is a phone that is *partly* up to date
     * and cannot say which part.
     */
    @Test
    fun aHoleIsNoticedAndTheScreenIsNotWrittenOverIt() = bench { b ->
        b.push(1, RoundMessage.OpeningMessage(Haptic.Short))
        b.push(3, RoundMessage.CheckInProgressed(present = 3, expected = 5))
        delay(SETTLE)

        assertEquals(1L, b.client.round.held, "a push arriving over a hole was applied anyway")
        assertTrue(
            b.said.any { it.contains("A FRAME WAS MISSED") },
            "the phone noticed nothing: ${b.said}",
        )
    }

    /**
     * **The way up survives being handed over before the round exists.**
     *
     * [DeferredRoundSeam] is how the construction cycle E-P2-3 names is stepped around, and the one
     * decision in it is that `linked` is *buffered* rather than dropped. On a phone hosting its own
     * house the client connects during construction, which is before the round half exists — and a
     * seam that forgot the send would leave that phone able to receive everything and press
     * nothing, with no symptom on either screen.
     */
    @Test
    fun theWayUpSurvivesBeingHandedOverBeforeTheRoundExists() = runBlocking<Unit> {
        val seam = DeferredRoundSeam()
        val sent = mutableListOf<TransportFrame>()

        // The lobby connects first, exactly as it does on a host's own phone.
        seam.linked { frame -> sent += frame }

        val late = object : RoundSeam {
            var up: (suspend (TransportFrame) -> Unit)? = null
            override fun linked(send: suspend (TransportFrame) -> Unit) { up = send }
            override fun arrived(frame: TransportFrame) = Unit
            override fun seated() = Unit
            override fun roundEnded() = Unit
        }
        seam.becomes(late)

        val up = late.up
        assertTrue(up != null, "the round was handed no way up: every control press would vanish")
        up.invoke(TransportFrame.Carry("anything"))
        assertEquals(1, sent.size, "the buffered send did not reach the socket the lobby opened")
    }

    /**
     * **The Egress tile goes up as its own frame, and it is empty.**
     *
     * R1 made the finger a fourth body member rather than an [home.someoneshome.model.protocol.IntentMessage],
     * and the argument is `factOf`'s: a request carrying the type and the nodes would let a client
     * **choose its own Egress**, and one carrying nothing would need an arm of `factOf` that
     * composes a fact from a second source. So it keeps its own frame and the desk answers it at
     * its own door.
     *
     * Both halves are asserted. The frame has to *be* a [RoundBody.Fire] — a press that went up as
     * an `Act` would be answered by the wrong door — and it has to carry **nothing but its dedup
     * sequence**, because a member with a field is a member somebody can put a target on later.
     */
    @Test
    fun theEgressTileGoesUpAsItsOwnEmptyFrame() = bench { b ->
        val sent = mutableListOf<TransportFrame>()
        b.client.linked { frame -> sent += frame }
        b.client.fireEgress()
        delay(SETTLE)

        val carried = sent.filterIsInstance<TransportFrame.Carry>().map { it.body }
        val bodies = carried.mapNotNull { RoundWire.decodeOrNull(it) }
        assertTrue(
            bodies.any { it is RoundBody.Fire },
            "the Egress tile press never reached the socket: $bodies",
        )
        assertTrue(
            bodies.none { it is RoundBody.Act },
            "the press went up as a request as well: the house would answer it at two doors",
        )
        // A `data class Fire(val seq: Long)` and nothing else. Read off the ENCODING rather than
        // off the declaration, because what a client can put on the wire is the encoding.
        val encoded = carried.first { RoundWire.decodeOrNull(it) is RoundBody.Fire }
        assertFalse(
            encoded.contains("node") || encoded.contains("egress") || encoded.contains("Tether"),
            "the finger carried a draw the house is supposed to make: $encoded",
        )
    }

    /**
     * **Being linked is what *a house is attached* means, and the panel has to be told.**
     *
     * The seam under the whole fiction purge, and it had no test because nothing before N2 read the
     * fact. `LobbyOverTransport` calls `linked` on every connect — on the **host over loopback**, it
     * runs the house and then joins it like everybody else, and on **every client** over the
     * network — so this is the one moment a phone acquires a house, and the one place the answer
     * can be published from.
     *
     * Until it was published, whether the app was allowed to invent things was decided by launch
     * switches instead: the bench's fake house, the presentation timers and every screen fixture
     * each asked *how was this process started* and none of them asked *is there a house*. The
     * owner played three meetings that were not happening.
     *
     * **Both directions.** A phone that has never been linked must report no house, or the port's
     * fixtures and the bench's stand-in are gone from every desktop render and every unattended
     * walk of the flow.
     */
    @Test
    fun beingLinkedToAHouseTellsThePanelSo() = bench { it ->
        assertFalse(
            it.flow.state.houseAttached,
            "a phone that has never been linked claimed a house, so the port lost its fixtures",
        )

        it.client.linked { }

        assertTrue(
            it.flow.state.houseAttached,
            "this phone has a way up to a house and the panel was never told — " +
                "every stand-in in the app is still switched on",
        )
    }

    private companion object {
        /** Long enough for the queue to drain on the caller's dispatcher. */
        const val SETTLE = 300L
    }
}
