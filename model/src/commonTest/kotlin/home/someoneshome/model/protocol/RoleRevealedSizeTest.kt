package home.someoneshome.model.protocol

import home.someoneshome.model.Effect
import home.someoneshome.model.Role
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **An Insider's reveal and a Resident's are one length on the wire, whatever the line** (D-249).
 *
 * The encoding half of `RoleRevealedFrameSizeTest`, which measures the same over real sockets:
 * projected from the effect, pushed, encoded and carried, at every sequence number a round reaches.
 */
class RoleRevealedSizeTest {

    private fun frame(seq: Long, message: RoundMessage): Int =
        TransportWire.encode(TransportFrame.Carry(RoundWire.encode(RoundBody.Push(seq, message)))).encodeToByteArray().size

    private val lines = listOf(
        "",
        "x",
        "k".repeat(LobbyBody.Handover.LIMIT),
        "鍵".repeat(LobbyBody.Handover.LIMIT),
        "🔑".repeat(LobbyBody.Handover.LIMIT / 2),
        "\"\\\n\t\u0001 said \"no\"",
        "鍵".repeat(LobbyBody.Handover.LIMIT * 2),
    )

    @Test
    fun everyRevealIsOneLengthOnTheWire() {
        for (seq in listOf(1L, 9L, 10L, 99L, 100L, 4_321L)) {
            val sizes = buildList {
                for (line in lines) {
                    add(frame(seq, RoundWire.project(Effect.RoleRevealed(Seat(1), Role.Insider, line))))
                    add(frame(seq, RoundWire.project(Effect.RoleRevealed(Seat(2), Role.Resident, line))))
                    add(frame(seq, RoundWire.project(Effect.RoleRevealed(Seat(3), Role.Resident, ""))))
                }
            }.toSet()
            assertEquals(1, sizes.size, "at seq $seq a reveal's size follows the role or the line: $sizes")
        }
    }

    @Test
    fun theWordsComeBackAsTypedAndALineTooLongIsCutAtACharacter() {
        for (line in lines.dropLast(1)) {
            val back = RoundWire.decodeOrNull(RoundWire.encode(RoundBody.Push(1, RoundMessage.RoleRevealed(true, line))))
            val message = (back as RoundBody.Push).message as RoundMessage.RoleRevealed
            assertEquals(line, message.line)
            assertTrue(message.insider)
        }
        val long = RoundMessage.RoleRevealed(true, lines.last())
        assertEquals("鍵".repeat(LobbyBody.Handover.LIMIT), long.line)
        assertEquals("", RoundMessage.RoleRevealed(false).line)
    }

    /** A reveal of any other width does not decode: it is a body the phone cannot read, not a short one. */
    @Test
    fun aRevealOfTheWrongWidthDoesNotDecode() {
        val good = RoundWire.encode(RoundBody.Push(1, RoundMessage.RoleRevealed(false)))
        assertNull(RoundWire.decodeOrNull(good.replace("\"lineHex\":\"00", "\"lineHex\":\"")))
        assertNull(RoundWire.decodeOrNull(good.replace("\"role\":0", "\"role\":2")))
    }
}
