package home.someoneshome.model.protocol

import home.someoneshome.model.RadioCause
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-276: each decoder turns the other family's bodies away by their leading type, and answers
 * exactly what it answered before.**
 *
 * `LobbyWire.decodeOrNull` and `RoundWire.decodeOrNull` used to learn that a body was the other
 * family's by throwing. They now read the leading `"type"` first ([ForeignBody]). That is only
 * allowed if it changes no answer, so every answer here is compared with the plain decode, over
 * both families' bodies and over the texts the shortcut must leave to the full decode.
 */
class ForeignBodyTest {

    private val lobby: List<LobbyBody> = listOf(
        LobbyBody.Handover("i still have priya's spare key"),
        LobbyBody.Naming("ELLIOT"),
        LobbyBody.Standing(joined = 6, linesIn = 4, names = listOf("ROSE", "TOMAS"), insiders = 2),
        LobbyBody.RoundEnded,
        LobbyBody.HouseClosed,
        LobbyBody.NameTaken,
    )

    private val round: List<RoundBody> = listOf(
        RoundBody.Push(7, RoundMessage.AbilityBlanked.Padded, round = 2),
        RoundBody.Push(8, RoundMessage.MeetingCalling),
        RoundBody.Gap(41, 2),
        RoundBody.Act(3, IntentMessage.ArmRevoke),
        RoundBody.Fire(4),
        RoundBody.Sighted("0".repeat(SightingUpload.BODY_CHARS), SightingUpload.EMPTY_ASK),
        RoundBody.UploadHeard("0123456789abcdef"),
        RoundBody.FaultHeard("0123456789abcdef"),
        RoundBody.RadioFaulted(RadioCause.Person, "0".repeat(RoundBody.RadioFaulted.ONSET_CHARS)),
    )

    /** Texts the shortcut must not decide: every one goes to the full decode, whatever it says. */
    private val odd: List<String> = listOf(
        "", "ping 3", "{}", "{\"type\":", "{\"type\":\"", "{\"type\":\"standing",
        """{ "type":"standing","joined":1,"linesIn":0}""",
        """{"joined":1,"type":"standing","linesIn":0}""",
        """{"joined":1,"type":"push","seq":1}""",
        """{"type":"standing","joined":1,"linesIn":0}""",
        """{"type":"st\"anding","joined":1,"linesIn":0}""",
        """{"type":"seated","token":"tk-7"}""",
        """{"type":"","x":1}""",
        """{"type":"uploadHeard","id":"0123456789abcdef"}""",
        """{"type":"uploadHeard","id":"short"}""",
        """{"type":"roundEnded"}""",
    )

    private fun plainLobby(text: String): LobbyBody? = try {
        Json.decodeFromString(LobbyBody.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun plainRound(text: String): RoundBody? = try {
        Json.decodeFromString(RoundBody.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }

    @Test
    fun `both decoders answer exactly what the plain decode answers`() {
        val texts = lobby.map(LobbyWire::encode) + round.map(RoundWire::encode) + odd
        for (text in texts) {
            assertEquals(plainLobby(text), LobbyWire.decodeOrNull(text), "LobbyWire on $text")
            assertEquals(plainRound(text), RoundWire.decodeOrNull(text), "RoundWire on $text")
        }
        // And the shortcut is taken where it should be: each family's own bodies are not foreign
        // to it, and every one of the other family's is.
        val lobbyShortcut = ForeignBody(LobbyBody.serializer().descriptor)
        val roundShortcut = ForeignBody(RoundBody.serializer().descriptor)
        for (body in lobby) {
            val text = LobbyWire.encode(body)
            assertTrue(!lobbyShortcut.isForeign(text) && roundShortcut.isForeign(text), text)
        }
        for (body in round) {
            val text = RoundWire.encode(body)
            assertTrue(!roundShortcut.isForeign(text) && lobbyShortcut.isForeign(text), text)
        }
    }
}
