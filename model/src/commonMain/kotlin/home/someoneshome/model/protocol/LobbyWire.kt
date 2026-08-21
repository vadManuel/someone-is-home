package home.someoneshome.model.protocol

import home.someoneshome.model.ClientFacing
import kotlinx.serialization.json.Json

/**
 * **What the lobby says, in the opaque body of a [TransportFrame.Carry].**
 *
 * The lobby happens *before* the round exists: nothing here is an Event, nothing here is an
 * Effect, and nothing here passes the emit boundary, because there is no `GameState` yet for
 * anything to be redacted out of. It is pre-arm bookkeeping riding the channel story 0.8 already
 * built, which is why it needs no [TRANSPORT_PROTOCOL] bump — the frame layer carries it and
 * still cannot see what it is carrying.
 *
 * ### The direction is the type, and that is the whole guard
 *
 * There are two bodies and each one travels one way only:
 *
 * - [LobbyBody.Handover] goes **client → host**. It carries the one line a player typed. That
 *   text is the player's own, it is theirs to hand over, and the house is the only thing that
 *   ever reads it.
 * - [LobbyBody.Standing] goes **host → client**. It carries three integers.
 *
 * A single body with a nullable `line` would have been the obvious shape and it is exactly the
 * shape rule 3 forbids: a nulled field still exists, and someone makes it non-null later for an
 * unrelated reason. **[LobbyBody.Standing] is physically incapable of naming anyone or quoting
 * anything** — it has no `String` at all — so "the lobby leaked a name" is not a bug that can be
 * introduced by filling in a field. It would take adding one, in a diff that says so.
 *
 * The design's lobby shows counts. Names are deliberately absent and their absence is not an
 * oversight to be tidied up later: what a client may receive is not a call this layer makes.
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed class LobbyBody {

    /**
     * **Client → host. The one line the house can hold over you.**
     *
     * Seen by the house only, deleted when the round ends — both promised on the screen where it
     * is typed, and both kept above this layer: the host holds it in memory and writes it
     * nowhere, and nothing sends it back down. This body is the single authorised exit from the
     * phone that typed it.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("handover")
    data class Handover(val line: String) : LobbyBody()

    /**
     * **Host → client. How the lobby stands, as counts and a setting.**
     *
     * [joined] is how many seats are held, [linesIn] how many of them have handed their line
     * over, and [insiders] the host's setting — **null is UNKNOWN**, which is D-103's default:
     * the house draws the count at arming, locks it, and tells no one until the round ends. A
     * chosen number is public, because the host chose it in a lit room in front of everybody.
     *
     * Three integers and no text. See the class KDoc: that is the point of the type, not an
     * accident of the current requirements.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("standing")
    data class Standing(
        val joined: Int,
        val linesIn: Int,
        val insiders: Int? = null,
    ) : LobbyBody()
}

/**
 * The one encoder/decoder for [LobbyBody], for the reason [TransportWire] is the one for frames:
 * what the JSON looks like has exactly one home, and its round-trip test lives beside it.
 */
object LobbyWire {

    private val json = Json

    fun encode(body: LobbyBody): String = json.encodeToString(LobbyBody.serializer(), body)

    /**
     * Null for anything that is not a lobby body — including every other thing a `Carry` may
     * hold. A body that does not decode must become a fact the shell can report, never an
     * exception on an I/O thread taking a player's connection down with it.
     */
    fun decodeOrNull(text: String): LobbyBody? = try {
        json.decodeFromString(LobbyBody.serializer(), text)
    } catch (_: IllegalArgumentException) {
        null
    }
}
