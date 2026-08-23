package home.someoneshome.platform.transport

import home.someoneshome.model.Intent
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.model.protocol.RoundBody
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The desk: who said it, and how many times.**
 *
 * [EffectWire]'s mirror. That class's guard is that a kind the schema denies spends no sequence and
 * writes no byte; this one's is that a frame contributes nothing to who is asking, and that a frame
 * delivered twice reaches the rules once.
 */
class IntentDeskTest {

    private fun act(seq: Long, intent: IntentMessage = IntentMessage.CheckIn) =
        RoundBody.Act(seq = seq, intent = intent)

    // ---- 1 · The seat is the connection's ----------------------------------------------------------

    /**
     * **One frame, two connections, two different requests — and nothing in the frame chose.**
     *
     * The property the whole upward channel exists for, at the layer that enforces it. A screen
     * that could construct `Intent.CheckIn(Seat(4))` is a screen that could check somebody else in;
     * a *desk* that read a seat out of a frame would be that same cheat with a longer wire.
     */
    @Test
    fun theSeatComesOffTheConnectionAndTheFrameContributesNothing() = runBlocking<Unit> {
        val desk = IntentDesk()
        val frame = act(1)

        val fromFour = desk.arrived(Seat(4), frame)
        val fromOne = desk.arrived(Seat(1), frame)

        assertIs<IntentDesk.Landing.Put>(fromFour)
        assertIs<IntentDesk.Landing.Put>(fromOne)
        assertEquals(Intent.CheckIn(Seat(4)), fromFour.intent)
        assertEquals(Intent.CheckIn(Seat(1)), fromOne.intent)
    }

    /**
     * **Each seat's numbering is its own, so one phone's traffic cannot silence another's.**
     *
     * A single shared counter would be the quiet version of this bug: seat 1 asking for ten things
     * would put seat 4's next nine requests below the mark, and every control on that phone would
     * stop working with nothing anywhere to say why.
     */
    @Test
    fun oneSeatsNumberingSaysNothingAboutAnothers() = runBlocking<Unit> {
        val desk = IntentDesk()
        repeat(9) { assertIs<IntentDesk.Landing.Put>(desk.arrived(Seat(1), act(it + 1L))) }
        assertIs<IntentDesk.Landing.Put>(
            desk.arrived(Seat(4), act(1)),
            "seat 4's first request was judged against seat 1's numbering",
        )
    }

    // ---- 2 · Once is exactly once ------------------------------------------------------------------

    /**
     * **A request delivered twice is put to the house once.**
     *
     * `ArmRevoke` applied twice is two cooldowns spent on one press; `CheckIn` applied twice is a
     * phone counted twice against a gate that closes on a number (D-104). Neither is a leak and
     * both would be blamed on the rules.
     */
    @Test
    fun aRequestDeliveredTwiceIsPutToTheHouseOnce() = runBlocking<Unit> {
        val desk = IntentDesk()
        val frame = act(1, IntentMessage.ArmRevoke)

        assertIs<IntentDesk.Landing.Put>(desk.arrived(Seat(2), frame))
        assertIs<IntentDesk.Landing.Duplicate>(
            desk.arrived(Seat(2), frame),
            "the same request reached the rules twice",
        )
        assertIs<IntentDesk.Landing.Duplicate>(desk.arrived(Seat(2), frame))
    }

    /** An older number is a duplicate too — a re-ordered pair must not un-hear what came first. */
    @Test
    fun aNumberBelowTheMarkIsADuplicateAndNotAFreshRequest() = runBlocking<Unit> {
        val desk = IntentDesk()
        assertIs<IntentDesk.Landing.Put>(desk.arrived(Seat(2), act(5)))
        assertIs<IntentDesk.Landing.Duplicate>(desk.arrived(Seat(2), act(4)))
        assertIs<IntentDesk.Landing.Duplicate>(desk.arrived(Seat(2), act(1)))
        assertIs<IntentDesk.Landing.Put>(desk.arrived(Seat(2), act(6)))
    }

    /**
     * **A skipped number is put through and reported, never refused.**
     *
     * Refusing it would be rule 1's forbidden shape wearing transport clothing: the player pressed
     * a control, the house did nothing, and the silence is the answer. So the request lands and the
     * *authority* is told the numbering skipped — rule 6, exactly as a seat the house could not
     * reach is reported on the way down.
     */
    @Test
    fun aSkippedNumberIsPutThroughAndReportedRatherThanRefused() = runBlocking<Unit> {
        val skips = mutableListOf<Pair<Seat, Long>>()
        val desk = IntentDesk(onSkipped = { seat, missed -> skips += seat to missed })

        assertIs<IntentDesk.Landing.Put>(desk.arrived(Seat(3), act(1)))
        assertIs<IntentDesk.Landing.Put>(
            desk.arrived(Seat(3), act(4)),
            "a request was dropped because two before it went missing",
        )
        assertEquals(listOf(Seat(3) to 2L), skips, "the authority was not told what went missing")

        // And the ones that did not skip say nothing at all.
        assertIs<IntentDesk.Landing.Put>(desk.arrived(Seat(3), act(5)))
        assertEquals(1, skips.size, "an unbroken run was reported as a skip")
    }

    // ---- 3 · The window is the connection ----------------------------------------------------------

    /**
     * **A phone that came back is heard, and this is the failure the class would otherwise have.**
     *
     * A stopped app reads its token back and resumes as *that* seat (story 0.8) — with a fresh
     * [IntentOutbox] counting from 1. A desk still holding the old mark would take every request
     * that phone ever made again for a duplicate: not a leak, but a player pressing controls that
     * reach a house which has already decided it has heard them, forever, in silence.
     *
     * The second half is the point of the first: **without the reset the same sequence goes quiet**,
     * so this test cannot pass by the reset doing nothing.
     */
    @Test
    fun aReseatedPhoneIsHeardAgainAndWithoutTheResetItWouldNotBe() = runBlocking<Unit> {
        val seat = Seat(2)

        val forgetful = IntentDesk()
        repeat(6) { forgetful.arrived(seat, act(it + 1L)) }
        forgetful.seated(seat)
        assertIs<IntentDesk.Landing.Put>(
            forgetful.arrived(seat, act(1)),
            "a phone that resumed its seat was ignored from its first press onwards",
        )

        // The same run without the re-seating: every one of them silently dropped.
        val stubborn = IntentDesk()
        repeat(6) { stubborn.arrived(seat, act(it + 1L)) }
        assertIs<IntentDesk.Landing.Duplicate>(
            stubborn.arrived(seat, act(1)),
            "the mark was not doing anything, so clearing it proved nothing",
        )
    }

    /** Clearing one seat's mark leaves every other seat's exactly where it was. */
    @Test
    fun aReseatingIsOneSeatsAndNobodyElses() = runBlocking<Unit> {
        val desk = IntentDesk()
        desk.arrived(Seat(1), act(3))
        desk.arrived(Seat(2), act(3))
        desk.seated(Seat(1))

        assertIs<IntentDesk.Landing.Put>(desk.arrived(Seat(1), act(1)))
        assertIs<IntentDesk.Landing.Duplicate>(
            desk.arrived(Seat(2), act(1)),
            "one phone reconnecting reset another phone's numbering",
        )
    }

    // ---- 4 · The client's half ---------------------------------------------------------------------

    /** The outbox numbers from 1, never repeats, and starts over with the connection. */
    @Test
    fun theOutboxNumbersFromOneAndStartsOverWithTheConnection() {
        val outbox = IntentOutbox()
        assertEquals(0, outbox.sent, "a phone that has asked for nothing was already counting")

        val first = outbox.ask(IntentMessage.CheckIn)
        val second = outbox.ask(IntentMessage.LockVote)
        assertEquals(1L, first.seq)
        assertEquals(2L, second.seq)
        assertEquals(2L, outbox.sent)

        outbox.reseated()
        assertEquals(
            1L, outbox.ask(IntentMessage.CheckIn).seq,
            "a new connection carried on the old connection's numbering",
        )
    }

    /**
     * **The two ends agree about the window, which is the property that makes either of them safe.**
     *
     * The outbox restarting and the desk forgetting are the same fact seen from two phones, and a
     * build where only one of them happened is the build where a resumed player's controls do
     * nothing. Driven end to end here rather than asserted twice separately.
     */
    @Test
    fun theOutboxAndTheDeskResetOnTheSameFact() = runBlocking<Unit> {
        val seat = Seat(0)
        val desk = IntentDesk()
        val outbox = IntentOutbox()

        repeat(4) { assertIs<IntentDesk.Landing.Put>(desk.arrived(seat, outbox.ask(IntentMessage.CheckIn))) }

        // The socket failed and came back: the host seats the phone, the phone learns it is seated.
        desk.seated(seat)
        outbox.reseated()

        val after = (1..3).map { desk.arrived(seat, outbox.ask(IntentMessage.CheckIn)) }
        assertTrue(
            after.all { it is IntentDesk.Landing.Put },
            "a phone that reconnected was silently ignored: $after",
        )
    }
}
