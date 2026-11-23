package home.someoneshome.model.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lobby's nine bodies, and the one property that matters about the set.
 *
 * Round-tripping and pinned discriminators for the reason [TransportFrameTest] pins the frames'.
 * The third test is the real one: **what the host sends down carries names and nothing else that
 * is text** (D-115), so the lobby cannot quote a line by filling in a field that was already there.
 */
class LobbyWireTest {

    private val everyBody: Map<String, LobbyBody> = mapOf(
        "handover" to LobbyBody.Handover("i still have priya's spare key"),
        "naming" to LobbyBody.Naming("ELLIOT"),
        "standing" to LobbyBody.Standing(
            joined = 6, linesIn = 4, names = listOf("ROSE", "TOMAS"), insiders = 2,
        ),
        "roundEnded" to LobbyBody.RoundEnded,
        // **The host closing the house** (E-PAY-6). Its own discriminator rather than a flag on
        // `roundEnded`, and pinned here for the reason every other one is: the two mean opposite
        // things about whether there is still a house on the other end of the socket, and a client
        // that read them as one kind could not tell a second round from a party that ended.
        "houseClosed" to LobbyBody.HouseClosed,
        // **A refused name** (D-288): downward, to one phone, carrying nothing.
        "nameTaken" to LobbyBody.NameTaken,
        // **A lobby let-go asks the house first** (D-329): upward, carrying nothing, and the two
        // answers downward, carrying nothing.
        "letGo" to LobbyBody.LetGo,
        "released" to LobbyBody.Released,
        "seatKept" to LobbyBody.SeatKept,
    )

    @Test
    fun everyBodyRoundTripsThroughJson() {
        for ((_, body) in everyBody) {
            val text = LobbyWire.encode(body)
            assertEquals(body, LobbyWire.decodeOrNull(text), "round trip changed the body: $text")
        }
    }

    @Test
    fun theDiscriminatorsAreFrozen() {
        for ((wireName, body) in everyBody) {
            val text = LobbyWire.encode(body)
            assertTrue(
                "\"type\":\"$wireName\"" in text,
                "${body::class.simpleName} no longer says '$wireName' on the wire: $text",
            )
        }
    }

    @Test
    fun theBodyListIsExhaustive() {
        // Exhaustive over the sealed type, so a body added without a row above is a COMPILE
        // error here rather than a body that ships with an unpinned discriminator.
        fun wireNameOf(body: LobbyBody): String = when (body) {
            is LobbyBody.Handover -> "handover"
            is LobbyBody.Naming -> "naming"
            is LobbyBody.Standing -> "standing"
            is LobbyBody.RoundEnded -> "roundEnded"
            is LobbyBody.HouseClosed -> "houseClosed"
            is LobbyBody.NameTaken -> "nameTaken"
            is LobbyBody.LetGo -> "letGo"
            is LobbyBody.Released -> "released"
            is LobbyBody.SeatKept -> "seatKept"
        }
        for ((wireName, body) in everyBody) assertEquals(wireName, wireNameOf(body))
        assertEquals(everyBody.size, 9, "a lobby body was added without pinning its discriminator")
    }

    /**
     * **The let-go is one body from every phone, and its two answers are one width** (D-329). The
     * request carries nothing, so the same bytes go up whichever grant is off and whoever holds the
     * phone; and the house's two answers are the same number of bytes, so the length of what comes
     * down never says which it was. Pinned whole, byte for byte.
     */
    @Test
    fun theLetGoCarriesNothingAndItsAnswersAreOneWidth() {
        assertEquals("""{"type":"letGo"}""", LobbyWire.encode(LobbyBody.LetGo), "the let-go carries something")
        val released = LobbyWire.encode(LobbyBody.Released)
        val kept = LobbyWire.encode(LobbyBody.SeatKept)
        assertEquals("""{"type":"released"}""", released)
        assertEquals("""{"type":"seatKept"}""", kept)
        assertEquals(released.length, kept.length, "the house's two answers to a let-go are not one width")
    }

    /**
     * **The standing carries the names and nothing else that is text.**
     *
     * The whole encoded form is pinned, string by string, rather than swept for the values a
     * caller happened to pass. D-115 widened this type once, deliberately; the next field that
     * appears here — a line, a seat, a role, anything — changes this list and fails this test,
     * which is the property the two-type split was built to keep after the widening as well as
     * before it.
     */
    @Test
    fun theStandingCarriesNamesAndNothingElseThatIsText() {
        val text = LobbyWire.encode(
            LobbyBody.Standing(joined = 6, linesIn = 4, names = listOf("ROSE", "TOMAS"), insiders = 2),
        )
        val quoted = Regex("\"[^\"]*\"").findAll(text).map { it.value.trim('"') }.toList()
        assertEquals(
            listOf("type", "standing", "joined", "linesIn", "names", "ROSE", "TOMAS", "insiders"),
            quoted,
            "the standing put something on the wire that is neither a count nor a name: $text",
        )
    }

    /** A lobby that has heard nothing names nobody, and says so as an empty list. */
    @Test
    fun aStandingWithNoNamesIsTheDefault() {
        val standing = LobbyBody.Standing(joined = 0, linesIn = 0)
        assertEquals(emptyList(), standing.names, "the standing invented a name for an empty lobby")
        assertEquals(standing, LobbyWire.decodeOrNull(LobbyWire.encode(standing)))
    }

    /** UNKNOWN is the default and survives the wire as itself, never as a number. */
    @Test
    fun unknownIsCarriedAsUnknown() {
        val standing = LobbyBody.Standing(joined = 6, linesIn = 6)
        assertNull(standing.insiders, "the default Insider setting is UNKNOWN, not a count")
        val back = LobbyWire.decodeOrNull(LobbyWire.encode(standing))
        assertEquals(standing, back)
    }

    /** A `Carry` holds all sorts of things. Anything that is not a lobby body is simply not one. */
    @Test
    fun anythingElseDecodesToNothing() {
        assertNull(LobbyWire.decodeOrNull("ping 3"))
        assertNull(LobbyWire.decodeOrNull(""))
        assertNull(LobbyWire.decodeOrNull("""{"type":"seated","token":"tk-7"}"""))
    }
}
