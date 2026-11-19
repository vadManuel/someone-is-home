package home.someoneshome.model.protocol

import home.someoneshome.model.ArmedTouch
import home.someoneshome.model.ClientClass
import home.someoneshome.model.Effect
import home.someoneshome.model.EmitSchema
import home.someoneshome.model.GameState
import home.someoneshome.model.Haptic
import home.someoneshome.model.MessageKind
import home.someoneshome.model.Role
import home.someoneshome.model.RoundState
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    /**
     * **S1 (D-219): the Revoked target's one frame is the same length too.** The touch that puts a
     * phone out sends it `RevokeLanded` in place of an answer; at 95 bytes against 120 it marked the
     * phone that had just been Revoked.
     */
    @Test
    fun `the Revoked target's frame is the touch's one length on the wire`() {
        for (seq in listOf(1L, 9L, 10L, 99L, 100L, 4_321L, 1_000_000L)) {
            val armed = frame(seq, RoundWire.project(Effect.AbilityFired(Seat(2), cooldownStarted = true)))
            for (haptic in listOf(Haptic.Long, null)) {
                val landed = frame(seq, RoundWire.project(Effect.RevokeLanded(Seat(3), haptic)))
                assertEquals(
                    armed.length, landed.length,
                    "at seq $seq an armed touch is ${armed.length} bytes and the Revoked target's ($haptic) ${landed.length}:\n$armed\n$landed",
                )
            }
        }
        val projected = RoundWire.project(Effect.RevokeLanded(Seat(4), haptic = null))
        assertEquals(RoundMessage.RevokeLanded.Silent, projected)
        assertEquals(EmitSchema.REVOKE_LANDED, projected.kind)
    }

    /**
     * **D-290: every answer a touch can put on a phone is the one length**, whatever the armed answer
     * says and whether the landing buzzes: an armed phone whose Revoke landed, one whose arm was spent
     * on nobody, an unarmed phone, the phone just Revoked and a phone already out. The digit that
     * says which is the only difference, and it is one byte in every case.
     */
    @Test
    fun `every answer a touch can send is one length on the wire whatever it says`() {
        val answers = listOf<Effect>(
            Effect.AbilityFired(Seat(2), cooldownStarted = true, touch = ArmedTouch.Landed),
            Effect.AbilityFired(Seat(2), cooldownStarted = true, touch = ArmedTouch.Spent),
            Effect.AbilityFired(Seat(2), cooldownStarted = true),
            Effect.AbilityFired(Seat(2), cooldownStarted = false),
            Effect.ContactAnswered(Seat(2)),
            Effect.RevokeLanded(Seat(2), Haptic.Long),
            Effect.RevokeLanded(Seat(2), haptic = null),
        )
        for (seq in listOf(1L, 9L, 10L, 99L, 100L, 4_321L, 1_000_000L)) {
            val lengths = answers.associateWith { frame(seq, RoundWire.project(it)).length }
            assertEquals(1, lengths.values.distinct().size, "at seq $seq the touch's answers differ in length: $lengths")
        }
    }

    /** What the digits say, after the socket's own encoder: the phone reads what the house wrote. */
    @Test
    fun `the touch's digit survives the wire and says what the house wrote`() {
        fun back(effect: Effect): RoundMessage =
            (RoundWire.decodeOrNull(RoundWire.encode(RoundBody.Push(7L, RoundWire.project(effect)))) as RoundBody.Push).message
        for (touch in ArmedTouch.entries) {
            val message = back(Effect.AbilityFired(Seat(2), cooldownStarted = true, touch = touch)) as RoundMessage.AbilityFired
            assertEquals(touch, message.touch, "an armed answer came off the wire saying something else")
            assertEquals(touch.haptic, message.haptic)
            assertTrue(message.cooldownStarted)
        }
        assertEquals(ArmedTouch.None, (back(Effect.AbilityFired(Seat(2), cooldownStarted = false)) as RoundMessage.AbilityFired).touch)
        assertEquals(Haptic.Long, (back(Effect.RevokeLanded(Seat(2), Haptic.Long)) as RoundMessage.RevokeLanded).haptic)
        assertEquals(null, (back(Effect.RevokeLanded(Seat(2), haptic = null)) as RoundMessage.RevokeLanded).haptic)
        assertFailsWith<IllegalArgumentException>("a landing that buzzes Short") { Effect.RevokeLanded(Seat(2), Haptic.Short) }
        assertFailsWith<IllegalArgumentException>("a fifth digit") { RoundMessage.AbilityFired(4) }
        assertFailsWith<IllegalArgumentException>("a third landing digit") { RoundMessage.RevokeLanded(2) }
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
