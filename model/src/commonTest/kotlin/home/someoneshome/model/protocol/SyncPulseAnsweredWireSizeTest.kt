package home.someoneshome.model.protocol

import home.someoneshome.model.ClientClass
import home.someoneshome.model.Destination
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A Sync Pulse answer is one length on the wire whether the beat was held or not, its blank is
 * that length, and every living seat is sent exactly one of them** (C2, D-258's and D-263's
 * patterns). And the pulse grid crosses on `EgressOpened` as the same number for everyone.
 *
 * Measured as [AbilityBlankWireSizeTest] measures the ability answer: pushed, encoded, carried,
 * encoded again, through the real `RoundWire` and `TransportWire`, at every sequence number a round
 * reaches. The socket-level half is `PulseFrameTest` in `app`.
 */
class SyncPulseAnsweredWireSizeTest {

    private fun frame(seq: Long, message: RoundMessage): String =
        TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message))))

    private val seqs = listOf(1L, 9L, 10L, 99L, 100L, 4_321L, 1_000_000L)

    @Test
    fun `held and not held and the blank are one length on the wire`() {
        for (seq in seqs) {
            val held = frame(seq, RoundWire.project(Effect.SyncPulseAnswered(Seat(1), held = true)))
            val missed = frame(seq, RoundWire.project(Effect.SyncPulseAnswered(Seat(1), held = false)))
            val blank = frame(seq, RoundWire.project(Effect.SyncPulseBlanked(Seat(2))))
            assertEquals(held.length, missed.length, "at seq $seq a held beat's answer is not a missed one's length:\n$held\n$missed")
            assertEquals(held.length, blank.length, "at seq $seq the blank is not the answer's length:\n$held\n$blank")
            if (seq == 1L) {
                println("C2 SYNC PULSE ANSWER BYTES AT SEQ 1 — held ${held.length}, missed ${missed.length}, blank ${blank.length}")
            }
        }
    }

    @Test
    fun `the answer round-trips and anything but one digit does not decode`() {
        for (held in listOf(true, false)) {
            val sent = RoundMessage.SyncPulseAnswered(held)
            val back = (RoundWire.decodeOrNull(RoundWire.encode(RoundBody.Push(1, sent))) as RoundBody.Push).message
            assertEquals(sent, back)
            assertEquals(held, (back as RoundMessage.SyncPulseAnswered).held)
        }
        assertFailsWith<IllegalArgumentException> { RoundMessage.SyncPulseAnswered(2) }
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"SyncPulseAnswered","held":2}}"""))
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"SyncPulseAnswered","held":true}}"""))
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"SyncPulseAnswered","held":10}}"""))
    }

    @Test
    fun `the blank carries nothing and anything else does not decode`() {
        val back = (RoundWire.decodeOrNull(RoundWire.encode(RoundBody.Push(1, RoundMessage.SyncPulseBlanked.Padded))) as RoundBody.Push).message
        assertEquals(RoundMessage.SyncPulseBlanked.Padded, back)
        assertEquals(EmitSchema.SYNC_PULSE_BLANKED, back.kind)
        assertFailsWith<IllegalArgumentException> { RoundMessage.SyncPulseBlanked(1) }
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"SyncPulseBlanked","blank":7}}"""))
    }

    @Test
    fun `the blank's row is both living classes and nothing else`() {
        assertEquals(
            setOf(ClientClass(Role.Resident, RoundState.Live), ClientClass(Role.Insider, RoundState.Live)),
            EmitSchema.classesFor(EmitSchema.SYNC_PULSE_BLANKED).toSet(),
        )
        assertEquals(
            EmitSchema.classesFor(EmitSchema.SYNC_PULSE_ANSWERED).toSet(),
            EmitSchema.classesFor(EmitSchema.SYNC_PULSE_BLANKED).toSet(),
            "the answer and its blank reach different classes, so one of them picks out a role",
        )
        assertEquals(emptyList(), EmitSchema.classesFor(MessageKind("SyncPulseBlank")), "a misspelt kind reached somebody")
    }

    /** The tapper's answer and every other seat's blank: one frame per living seat, none to the out. */
    @Test
    fun `each living seat is sent exactly one frame and the out none`() {
        val seats = (0..5).map { Seat(it) }
        val state = GameState.armedRound(seed = 7L, seats = seats, insiders = listOf(Seat(1), Seat(4)), systemIntegrity = 42)
            .copy(revoked = listOf(Seat(3)))
        for (tapper in listOf(Seat(1), Seat(2))) {
            for (held in listOf(true, false)) {
                val sent = seats.map { if (it == tapper) Effect.SyncPulseAnswered(tapper, held) else Effect.SyncPulseBlanked(it) }
                    .flatMap { EmitSchema.deliveries(it, state) }
                    .groupBy({ it.seat.index }, { it.kind })
                assertEquals(
                    setOf(0, 1, 2, 4, 5), sent.keys,
                    "a ${if (tapper.index == 1) "Insider's" else "Resident's"} return reached another set of seats: $sent",
                )
                for ((seat, kinds) in sent) {
                    assertEquals(
                        listOf(if (seat == tapper.index) EmitSchema.SYNC_PULSE_ANSWERED else EmitSchema.SYNC_PULSE_BLANKED),
                        kinds, "seat $seat at a return by seat ${tapper.index}",
                    )
                }
            }
        }
    }

    /** The grid's anchor crosses whole, and the frame's length follows its digits and not the seat. */
    @Test
    fun `the pulse grid crosses on EgressOpened as the same number for every seat`() {
        val nodes = listOf(Destination(MarkerId("M-1"), "circle", "UTILITY"), Destination(MarkerId("M-2"), "square", "LANDING"))
        val lengths = (0..5).map { seat ->
            val effect = Effect.EgressOpened(Seat(seat), EgressType.Beacon, nodes, 2_400, Haptic.Long, pulseFrom = 86_402_317L)
            val message = RoundWire.project(effect) as RoundMessage.EgressOpened
            val back = (RoundWire.decodeOrNull(RoundWire.encode(RoundBody.Push(5, message))) as RoundBody.Push).message
            assertEquals(86_402_317L, (back as RoundMessage.EgressOpened).pulseFrom, "seat $seat's grid did not survive the wire")
            frame(5, message).length
        }
        assertEquals(1, lengths.toSet().size, "EgressOpened's length follows the seat: $lengths")
        assertTrue(
            RoundWire.encode(RoundBody.Push(5, RoundWire.project(
                Effect.EgressOpened(Seat(0), EgressType.Beacon, nodes, 2_400, Haptic.Long, pulseFrom = 86_402_317L),
            ))).contains("\"pulseFrom\":86402317"),
            "the grid is not on the wire under its own name",
        )
    }
}
