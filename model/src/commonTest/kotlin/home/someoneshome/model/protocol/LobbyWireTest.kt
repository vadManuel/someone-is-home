package home.someoneshome.model.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lobby's three bodies, and the one property that matters about the set.
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
        }
        for ((wireName, body) in everyBody) assertEquals(wireName, wireNameOf(body))
        assertEquals(everyBody.size, 3, "a lobby body was added without pinning its discriminator")
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
