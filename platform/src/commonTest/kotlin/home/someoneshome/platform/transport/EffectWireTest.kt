package home.someoneshome.platform.transport

import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.TransportFrame
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The effect wire: who is written to, in what order, and what happens when a phone is not there.**
 *
 * No socket. What is under test is the decision about *whether a byte leaves at all*, which is the
 * allowlist's, and putting a network under it would only add a way for a green run to mean
 * something else. The socket's own half is `EffectPushTest`, over loopback, in `app`.
 */
class EffectWireTest {

    private val seats = (0..3).map { Seat(it) }

    /**
     * Everything the house wrote, in the order it wrote it, per seat — and which phones are not
     * there, which [TransportHost.send] reports by throwing.
     */
    private class Sockets {
        val written = mutableListOf<Pair<Seat, RoundBody.Push>>()
        val gone = mutableSetOf<Int>()
        val wire: suspend (Seat, TransportFrame) -> Unit = { seat, frame ->
            if (seat.index in gone) error("no connection holds $seat — the frame was not sent")
            val body = (frame as TransportFrame.Carry).body
            written += seat to (RoundWire.decodeOrNull(body) as RoundBody.Push)
        }

        fun streamOf(seat: Seat): List<RoundBody.Push> =
            written.filter { it.first.index == seat.index }.map { it.second }
    }

    private fun round(insiders: List<Seat>, revoked: List<Seat> = emptyList()): GameState =
        GameState.armedRound(seed = 7L, seats = seats, insiders = insiders, systemIntegrity = 42)
            .copy(revoked = revoked)

    // ---- 1 · The allowlist is the only authority -------------------------------------------------

    /**
     * **A kind the schema denies cannot be sent, and it is not a check — it is the shape of the
     * loop** (rule 2, D-111).
     *
     * `PresenceChanged` has no row, so [EmitSchema.deliveries] returns nothing for it, so no
     * sequence is spent and nothing is written. The injection for this one replaces
     * `EmitSchema.deliveries(...)` with `state.seats`, which is what somebody reaches for when a
     * message "should obviously go to everyone" — and it is the change that would put the presence
     * plane on four phones.
     */
    @Test
    fun aKindTheSchemaDeniesReachesNobodyAndSpendsNoSequence() = runBlocking {
        val sockets = Sockets()
        val wire = EffectWire(sockets.wire)
        val state = round(insiders = listOf(Seat(1)))

        wire.emit(Effect.PresenceChanged(Seat(2), MarkerId("M-1"), open = true), state)
        wire.emit(Effect.EgressSucceeded(Haptic.Short), state)

        assertEquals(emptyList(), sockets.written, "a kind with no allowlist row was written")
        for (seat in seats) {
            assertEquals(0L, wire.sequenceOf(seat), "$seat spent a sequence on a denied kind")
        }
    }

    /**
     * The narrow row, from the other side. `AbilityFired` is permitted to `Insider/Live` and to no
     * other class, so a house with one Insider writes it exactly once.
     */
    @Test
    fun aRowThatNamesOneClassReachesOnePhone() = runBlocking {
        val sockets = Sockets()
        val wire = EffectWire(sockets.wire)
        val state = round(insiders = listOf(Seat(1)))

        wire.emit(Effect.AbilityFired(Seat(1), cooldownStarted = true), state)

        assertEquals(listOf(Seat(1)), sockets.written.map { it.first })
        assertEquals(
            RoundMessage.AbilityFired(cooldownStarted = true), sockets.written.single().second.message,
        )
    }

    // ---- 2 · The differential, over the wire ------------------------------------------------------

    /**
     * **The role-swap discipline applied to delivery: exchange two seats' roles and the two
     * streams exchange with them, byte for byte.**
     *
     * The differential harness runs a seeded round twice with a role swapped and diffs the client
     * transcripts. This is the same question asked of the pipeline rather than of the rules: if the
     * wire is right, *what seat 1 was sent when it was the Insider* is exactly *what seat 2 is sent
     * when it is*, and nothing about which index holds the role changes a byte.
     *
     * It is the shape a leak would break in both directions. A row narrowed to one role puts a
     * message on one phone and not the other; a row widened puts it on both; either way one of the
     * two comparisons below stops holding.
     */
    @Test
    fun exchangingTheRoleExchangesTheTwoStreamsAndNothingElse() = runBlocking {
        fun run(insiderSeat: Seat): Map<Int, List<RoundMessage>> {
            val sockets = Sockets()
            val wire = EffectWire(sockets.wire)
            val state = round(insiders = listOf(insiderSeat))
            runBlocking {
                // Every seat does the same things, so nothing but the role differs between runs.
                for (seat in seats) {
                    wire.emit(Effect.AbilityFired(seat, cooldownStarted = true), state)
                    wire.emit(Effect.SubroutineGraded(seat, accepted = true), state)
                    wire.emit(Effect.ScanAnswered(seat, opened = null), state)
                }
                wire.emit(Effect.SystemIntegrityBatched(percent = 87), state)
            }
            return seats.associate { it.index to sockets.streamOf(it).map { push -> push.message } }
        }

        val one = run(Seat(1))
        val two = run(Seat(2))

        assertEquals(
            one[1], two[2],
            "the seat holding the role was sent something different depending on which seat it was",
        )
        assertEquals(one[2], two[1], "the same, with the exchange the other way round")
        for (untouched in listOf(0, 3)) {
            assertEquals(
                one[untouched], two[untouched],
                "seat $untouched's stream moved because two OTHER seats exchanged roles",
            )
        }

        // And the exchange is not vacuous: the two streams really do differ.
        assertTrue(
            one[1] != one[2],
            "the Insider and the Resident were sent identical streams, so the test above proves nothing",
        )
        assertTrue(
            one.getValue(1).any { it is RoundMessage.AbilityFired },
            "the Insider's own ability never reached them",
        )
        assertTrue(
            one.getValue(2).none { it is RoundMessage.AbilityFired },
            "an ability nobody has reached a Resident's phone",
        )
    }

    /**
     * **The round-state axis, over the wire**: the meter's raw count is the out's and the living
     * are told nothing (D-134, `gdd.md:1014`).
     *
     * The second axis matters here for the reason D-079 gives — keyed on role alone, the entry that
     * serves an out Resident serves a living one.
     */
    @Test
    fun theLiveLedgerCountReachesTheOutAndNoLivingPhone() = runBlocking {
        val sockets = Sockets()
        val wire = EffectWire(sockets.wire)
        val state = round(insiders = listOf(Seat(1)), revoked = listOf(Seat(3)))

        wire.emit(Effect.SubroutineProgressed(remaining = 41), state)

        assertEquals(listOf(Seat(3)), sockets.written.map { it.first }, "the live count left the couch")
    }

    // ---- 3 · Order, and loss ----------------------------------------------------------------------

    /** Per seat, from 1, with no gaps of its own. Two seats hold entirely different numbers. */
    @Test
    fun everySeatIsNumberedInItsOwnStream() = runBlocking {
        val sockets = Sockets()
        val wire = EffectWire(sockets.wire)
        val state = round(insiders = listOf(Seat(1)))

        wire.emit(Effect.SystemIntegrityBatched(87), state)      // every living seat
        wire.emit(Effect.SubroutineGraded(Seat(2), true), state) // seat 2 alone
        wire.emit(Effect.SystemIntegrityBatched(85), state)      // every living seat again

        assertEquals(listOf(1L, 2L), sockets.streamOf(Seat(0)).map { it.seq })
        assertEquals(listOf(1L, 2L, 3L), sockets.streamOf(Seat(2)).map { it.seq })
        assertEquals(
            listOf(
                RoundMessage.SystemIntegrityBatched(87),
                RoundMessage.SubroutineGraded(true),
                RoundMessage.SystemIntegrityBatched(85),
            ),
            sockets.streamOf(Seat(2)).map { it.message },
            "seat 2's own order is not the order the house said things in",
        )
    }

    /**
     * **A phone that is not there keeps its place and collects when it asks** (rule 6, D-097).
     *
     * The write fails, the authority is told at once, and the seat's sequence is spent anyway — so
     * the message is a delay rather than a hole, and the re-assertion is what closes it. The
     * alternative, skipping the number, would make the client's own gap detector see a hole that
     * never existed and ask forever.
     */
    @Test
    fun aSeatThatCouldNotBeReachedKeepsItsPlaceAndCollectsOnAsking() = runBlocking {
        val sockets = Sockets()
        val unreachable = mutableListOf<Seat>()
        val wire = EffectWire(sockets.wire, onUnreachable = { unreachable += it })
        val state = round(insiders = listOf(Seat(1)))

        sockets.gone += 2
        wire.emit(Effect.SystemIntegrityBatched(87), state)
        wire.emit(Effect.SystemIntegrityBatched(85), state)

        assertEquals(listOf(Seat(2), Seat(2)), unreachable, "a failed write reached nobody's attention")
        assertEquals(emptyList(), sockets.streamOf(Seat(2)), "the closed socket was written to after all")
        assertEquals(2L, wire.sequenceOf(Seat(2)), "the absent seat lost its place in its own stream")
        // One seat's failed radio is one seat's problem: the rest of the house was written to as usual.
        assertEquals(listOf(1L, 2L), sockets.streamOf(Seat(0)).map { it.seq })

        // The phone comes back holding nothing of this round, and asks.
        sockets.gone -= 2
        wire.reassert(Seat(2), held = 0)

        assertEquals(
            listOf(1L, 2L), sockets.streamOf(Seat(2)).map { it.seq },
            "what the house could not deliver was never delivered at all",
        )
        assertEquals(
            listOf(RoundMessage.SystemIntegrityBatched(87), RoundMessage.SystemIntegrityBatched(85)),
            sockets.streamOf(Seat(2)).map { it.message },
            "the phone was caught up with something other than what it missed",
        )
    }

    /**
     * **D-097's re-assertion: exactly what was said to this seat, from where it says it is, and
     * nothing that belongs to anybody else.**
     */
    @Test
    fun theHouseSaysAgainOnlyWhatItSaidToThatSeat() = runBlocking {
        val sockets = Sockets()
        val wire = EffectWire(sockets.wire)
        val state = round(insiders = listOf(Seat(1)))

        wire.emit(Effect.SystemIntegrityBatched(87), state)
        wire.emit(Effect.SubroutineGraded(Seat(2), accepted = true), state)
        wire.emit(Effect.SystemIntegrityBatched(85), state)
        val first = sockets.streamOf(Seat(2))
        sockets.written.clear()

        wire.reassert(Seat(2), held = 1)

        assertEquals(
            first.drop(1), sockets.streamOf(Seat(2)),
            "the re-assertion is not what the house said in the first place",
        )
        assertEquals(
            listOf(Seat(2)), sockets.written.map { it.first }.distinct(),
            "one seat asked and another seat's phone was written to",
        )

        // A seat that already holds everything is told nothing, which is what makes asking cheap.
        sockets.written.clear()
        wire.reassert(Seat(2), held = 3)
        assertEquals(emptyList(), sockets.written, "a seat that was up to date was re-sent its round")
    }

    /** The retained streams are a round's and outlive nothing. */
    @Test
    fun theRoundEndingWipesWhatTheHouseWasHolding() = runBlocking {
        val sockets = Sockets()
        val wire = EffectWire(sockets.wire)
        val state = round(insiders = listOf(Seat(1)))

        wire.emit(Effect.SystemIntegrityBatched(87), state)
        wire.roundEnded()
        sockets.written.clear()

        wire.reassert(Seat(0), held = 0)
        assertEquals(emptyList(), sockets.written, "last round's messages are still on the house's desk")
        assertEquals(0L, wire.sequenceOf(Seat(0)), "the numbering carried over from a finished round")
    }
}

/**
 * **The client's gap detector, on its own.**
 *
 * Pure, so it is checked without a socket for [ClientSession]'s reason: what a phone decides about
 * a frame is not I/O, and a test that needed a network to ask the question would be a test of the
 * network.
 */
class RoundInboxTest {

    private fun push(seq: Long) = RoundBody.Push(seq, RoundMessage.SystemIntegrityBatched(seq.toInt()))

    @Test
    fun inOrderIsApplied() {
        val inbox = RoundInbox()
        assertEquals(RoundInbox.Landing.Apply(push(1).message), inbox.arrived(push(1)))
        assertEquals(RoundInbox.Landing.Apply(push(2).message), inbox.arrived(push(2)))
        assertEquals(2L, inbox.held)
    }

    /**
     * **A duplicate is discarded, and that is what makes a re-assertion safe to receive
     * redundantly** — D-097's own requirement for the payload, kept here rather than argued.
     */
    @Test
    fun aMessageThisPhoneAlreadyHoldsIsDiscarded() {
        val inbox = RoundInbox()
        inbox.arrived(push(1))
        inbox.arrived(push(2))
        assertEquals(RoundInbox.Landing.Duplicate, inbox.arrived(push(1)))
        assertEquals(RoundInbox.Landing.Duplicate, inbox.arrived(push(2)))
        assertEquals(2L, inbox.held, "a duplicate moved the phone's own position")
    }

    /**
     * **A hole is not filled in.** The later push is dropped rather than applied over the missing
     * one, and the phone asks from where it really is — so what a screen draws is always a
     * prefix of what the house said, never a mixture.
     */
    @Test
    fun aGapIsNotAppliedAndTheAskNamesWhatIsHeld() {
        val inbox = RoundInbox()
        inbox.arrived(push(1))
        assertEquals(RoundInbox.Landing.Gap(held = 1), inbox.arrived(push(4)))
        assertEquals(1L, inbox.held, "an out-of-order push moved the phone forward anyway")

        // The house says the rest again, in order, and the phone converges.
        assertEquals(RoundInbox.Landing.Apply(push(2).message), inbox.arrived(push(2)))
        assertEquals(RoundInbox.Landing.Apply(push(3).message), inbox.arrived(push(3)))
        assertEquals(RoundInbox.Landing.Apply(push(4).message), inbox.arrived(push(4)))
        assertEquals(4L, inbox.held)
    }

    /** A reconnect is the same request by its other trigger, and a fresh phone asks for everything. */
    @Test
    fun askingAgainNamesWhereThisPhoneIs() {
        val inbox = RoundInbox()
        assertEquals(RoundBody.Gap(held = 0), inbox.askAgain())
        inbox.arrived(push(1))
        assertEquals(RoundBody.Gap(held = 1), inbox.askAgain())
        inbox.roundEnded()
        assertEquals(RoundBody.Gap(held = 0), inbox.askAgain())
    }
}
