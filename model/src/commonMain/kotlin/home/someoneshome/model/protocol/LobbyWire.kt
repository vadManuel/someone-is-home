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
 * There are three bodies and each one travels one way only:
 *
 * - [LobbyBody.Handover] goes **client → host**. It carries the one line a player typed. That
 *   text is the player's own, it is theirs to hand over, and the house is the only thing that
 *   ever reads it.
 * - [LobbyBody.Naming] goes **client → host**. It carries what to call this phone's owner.
 * - [LobbyBody.Standing] goes **host → client**. It carries the counts, the host's setting, and
 *   the names.
 *
 * A single body with a nullable `line` would have been the obvious shape and it is exactly the
 * shape rule 3 forbids: a nulled field still exists, and someone makes it non-null later for an
 * unrelated reason. Two upward bodies rather than one widened one keeps that property where it
 * pays: **[LobbyBody.Standing] carries names because D-115 says it does, and it still has nowhere
 * to put a one line** — not a field that is currently blank, no field at all.
 *
 * ### The names arrived by a ruling, in a diff that says so (D-115)
 *
 * This type shipped as three integers, which was the overnight charter's caution while E6-1 was
 * open. D-115 closed it: **the design's lobby shows names**, the host learns them, and clients
 * receive them where the interaction needs them. Names are **round-scoped** — they arrive for the
 * round and they are gone when it ends — and they are held in memory by the host's desk exactly
 * as the one lines are.
 *
 * What the widening does *not* open is occupancy. Knowing who is in the game is not knowing who is
 * in a room, and nothing here touches the latter. Nor does it open the lines: a name says who is
 * standing in the hall, which six people standing in the hall can already see.
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
     * **Client → host. What to call the person holding this phone.**
     *
     * Sent on every seating rather than once: a phone that dropped and resumed comes back to the
     * seat its token owns, and a house that had forgotten the name would list an empty chip for
     * somebody standing in the room. Re-announcing is the cheaper half of that pair.
     *
     * Separate from [Handover] because the two arrive at different moments — a name on the way in,
     * a line whenever its author has thought of one — and a body that carried both would have to
     * be sent half-empty, which is the nullable-field shape rule 3 forbids.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("naming")
    data class Naming(val name: String) : LobbyBody() {
        companion object {
            /**
             * **The one place the name's length lives.**
             *
             * The field is one line on a 300-unit panel and the design's own names are six
             * characters. Past this a name stops being readable across a dark hall, which is the
             * only thing a name in this game is for. Bounded on the *host's* side as well as the
             * typist's: a client is free to send whatever it likes, and a lobby is not the place
             * to find out that one of them did.
             */
            const val LIMIT: Int = 14
        }
    }

    /**
     * **Host → client. How the lobby stands: the counts, the setting, and who is here.**
     *
     * [joined] is how many seats are held, [linesIn] how many of them have handed their line
     * over, and [insiders] the host's setting — **null is UNKNOWN**, which is D-103's default:
     * the house draws the count at arming, locks it, and tells no one until the round ends. A
     * chosen number is public, because the host chose it in a lit room in front of everybody.
     *
     * [names] is one entry per seat in the order the seats were taken, **empty for a phone that
     * has not said what to call its owner**. One entry per seat rather than a list of the names it
     * happens to have, so that `names.size` is `joined` and the list cannot quietly under-report
     * a lobby back to a screen that is counting it.
     *
     * ### [names] and [linesIn] must never be drawn against each other
     *
     * The order here is arrival order and [linesIn] is a count. Pairing the two positionally —
     * the nth name against the nth filled mark — asserts *who* has handed a line over, which is a
     * thing the house never said and this body cannot say. The count is a count; the names are a
     * set of people. See `LobbyScreen`, where that separation is drawn.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("standing")
    data class Standing(
        val joined: Int,
        val linesIn: Int,
        val names: List<String> = emptyList(),
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
