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
import kotlin.test.assertTrue

/**
 * **Every touch puts one frame of one length on each phone that touched** (R1, D-216).
 *
 * An armed Insider is answered `AbilityFired(cooldownStarted = true)` and everybody else
 * `ContactAnswered`. A reader of the Wi-Fi sees the frame, not the content's meaning, so the two
 * must be the same number of bytes **as they leave the socket**: pushed, encoded, carried, encoded
 * again. Measured through the real `RoundWire` and `TransportWire`, at every sequence number a
 * round reaches, so a rename of either kind or either field fails here rather than on the air.
 */
class ContactAnswerSizeTest {

    private fun frame(seq: Long, message: RoundMessage): String =
        TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message))))

    @Test
    fun `an armed answer and an unarmed answer are one length on the wire`() {
        for (seq in listOf(1L, 9L, 10L, 99L, 100L, 4_321L, 1_000_000L)) {
            val armed = frame(seq, RoundWire.project(Effect.AbilityFired(Seat(2), cooldownStarted = true)))
            val unarmed = frame(seq, RoundWire.project(Effect.ContactAnswered(Seat(2))))
            assertEquals(
                armed.length, unarmed.length,
                "at seq $seq an armed touch is ${armed.length} bytes and an unarmed one ${unarmed.length}:\n$armed\n$unarmed",
            )
        }
    }

    /** The pad is one constant: a field whose width could vary is a field somebody can measure. */
    @Test
    fun `the answer always carries the one pad`() {
        val projected = RoundWire.project(Effect.ContactAnswered(Seat(5)))
        assertEquals(RoundMessage.ContactAnswered(RoundMessage.ContactAnswered.PAD), projected)
        assertEquals(EmitSchema.CONTACT_ANSWERED, projected.kind)
    }

    /**
     * **Both living classes, and nothing else.** Narrowed to Residents the Insiders become the
     * phones a touch answers with nothing; narrowed to Insiders it is D-213's open item 1 again.
     */
    @Test
    fun `the answer's row is both living classes`() {
        assertEquals(
            listOf(ClientClass(Role.Resident, RoundState.Live), ClientClass(Role.Insider, RoundState.Live)),
            EmitSchema.classesFor(EmitSchema.CONTACT_ANSWERED),
            "the touch answer's row moved: the frame count would follow the role",
        )
    }

    /** Addressed to the actor's own socket, for a Resident and an Insider alike, and to nobody out. */
    @Test
    fun `the answer reaches the actor's own phone only`() {
        val seats = (0..5).map { Seat(it) }
        val state = GameState.armedRound(seed = 7L, seats = seats, insiders = listOf(Seat(1)), systemIntegrity = 42)
        for (seat in seats) {
            assertEquals(
                listOf(seat),
                EmitSchema.deliveries(Effect.ContactAnswered(seat), state).map { it.seat },
                "seat ${seat.index}'s answer did not go to exactly its own phone",
            )
        }
        assertEquals(
            emptyList(),
            EmitSchema.deliveries(Effect.ContactAnswered(Seat(3)), state.copy(revoked = listOf(Seat(3)))),
            "a touch was answered on a phone that is out",
        )
    }

    /** Rule 2 on this kind: the row is what lets it out, and a kind spelled otherwise reaches nobody. */
    @Test
    fun `the answer with no row would ship to nobody`() {
        assertTrue(EmitSchema.CONTACT_ANSWERED in EmitSchema.knownKinds(), "the touch answer has no row")
        assertEquals(emptyList(), EmitSchema.classesFor(MessageKind("ContactAnswer")))
    }
}
