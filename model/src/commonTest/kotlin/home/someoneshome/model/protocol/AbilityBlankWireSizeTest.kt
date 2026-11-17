package home.someoneshome.model.protocol

import home.someoneshome.model.ClientClass
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * **The bystanders' blank is the actor's answer's length on the wire, and reaches every living seat
 * but the actor's** (D-263).
 *
 * Measured as [IsolateWireSizeTest] measures the Isolate pair: pushed, encoded, carried, encoded
 * again, through the real `RoundWire` and `TransportWire`, at every sequence number a round reaches.
 * The socket-level half is `UplinkFrameTest` and `IsolateFrameSizeTest` in `app`.
 */
class AbilityBlankWireSizeTest {

    private fun frame(seq: Long, message: RoundMessage): String =
        TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message))))

    private val seqs = listOf(1L, 9L, 10L, 99L, 100L, 4_321L, 1_000_000L)

    @Test
    fun `the blank and both answers are one length on the wire`() {
        for (seq in seqs) {
            val started = frame(seq, RoundWire.project(Effect.AbilityFired(Seat(1), cooldownStarted = true)))
            val running = frame(seq, RoundWire.project(Effect.AbilityFired(Seat(1), cooldownStarted = false)))
            val blank = frame(seq, RoundWire.project(Effect.AbilityBlanked(Seat(2))))
            assertEquals(started.length, blank.length, "at seq $seq the blank is not the answer's length:\n$started\n$blank")
            assertEquals(running.length, blank.length, "at seq $seq the blank is not the answer's length:\n$running\n$blank")
            if (seq == 1L) println("D-263 ABILITY ANSWER/BLANK BYTES AT SEQ 1 — ${started.length}/${blank.length}")
        }
    }

    @Test
    fun `the blank carries nothing and anything else does not decode`() {
        val back = (RoundWire.decodeOrNull(RoundWire.encode(RoundBody.Push(1, RoundMessage.AbilityBlanked.Padded))) as RoundBody.Push).message
        assertEquals(RoundMessage.AbilityBlanked.Padded, back)
        assertEquals(EmitSchema.ABILITY_BLANKED, back.kind)
        assertFailsWith<IllegalArgumentException> { RoundMessage.AbilityBlanked(1) }
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"AbilityBlanked","paddingDigits":7}}"""))
        assertNull(RoundWire.decodeOrNull("""{"type":"push","seq":1,"message":{"type":"AbilityBlanked","paddingDigits":10}}"""))
    }

    @Test
    fun `the blank's row is both living classes and nothing else`() {
        assertEquals(
            setOf(ClientClass(Role.Resident, RoundState.Live), ClientClass(Role.Insider, RoundState.Live)),
            EmitSchema.classesFor(EmitSchema.ABILITY_BLANKED).toSet(),
        )
        assertEquals(emptyList(), EmitSchema.classesFor(MessageKind("AbilityBlank")), "a misspelt kind reached somebody")
    }

    /** The actor's answer and every other seat's blank: one frame per living seat, none to the out. */
    @Test
    fun `each living seat is sent exactly one frame and the out none`() {
        val seats = (0..5).map { Seat(it) }
        val state = GameState.armedRound(seed = 7L, seats = seats, insiders = listOf(Seat(1), Seat(4)), systemIntegrity = 42)
            .copy(revoked = listOf(Seat(3)))
        val actor = Seat(1)
        val sent = seats.map { if (it == actor) Effect.AbilityFired(actor, cooldownStarted = true) else Effect.AbilityBlanked(it) }
            .flatMap { EmitSchema.deliveries(it, state) }
            .groupBy({ it.seat.index }, { it.kind })
        assertEquals(
            mapOf(
                0 to listOf(EmitSchema.ABILITY_BLANKED),
                1 to listOf(EmitSchema.ABILITY_FIRED),
                2 to listOf(EmitSchema.ABILITY_BLANKED),
                4 to listOf(EmitSchema.ABILITY_BLANKED),
                5 to listOf(EmitSchema.ABILITY_BLANKED),
            ),
            sent,
            "the ability frames per seat follow the role in count, or reach a seat that is out",
        )
    }
}
