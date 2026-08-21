package home.someoneshome.model.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lobby's two bodies, and the one property that matters about the pair.
 *
 * Round-tripping and pinned discriminators for the reason [TransportFrameTest] pins the frames'.
 * The third test is the real one: **what the host sends down cannot carry text**, so the lobby
 * cannot name anybody by filling in a field that was already there.
 */
class LobbyWireTest {

    private val everyBody: Map<String, LobbyBody> = mapOf(
        "handover" to LobbyBody.Handover("i still have priya's spare key"),
        "standing" to LobbyBody.Standing(joined = 6, linesIn = 4, insiders = 2),
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
            is LobbyBody.Standing -> "standing"
        }
        for ((wireName, body) in everyBody) assertEquals(wireName, wireNameOf(body))
        assertEquals(everyBody.size, 2, "a lobby body was added without pinning its discriminator")
    }

    /**
     * **The standing cannot quote a line or name a player, whatever it is built from.**
     *
     * Not a claim about the values a caller happens to pass — a claim about the type. Every field
     * is an integer, so the encoded form is integers, and the only way to change that is to add a
     * field in a diff that says out loud it is widening what a client receives.
     */
    @Test
    fun theStandingCarriesNoTextAtAll() {
        val text = LobbyWire.encode(LobbyBody.Standing(joined = 6, linesIn = 4, insiders = 2))
        // The discriminator is the one string on the wire; everything else must be a number.
        val quoted = Regex("\"[^\"]*\"").findAll(text).map { it.value.trim('"') }.toList()
        assertEquals(
            listOf("type", "standing", "joined", "linesIn", "insiders"), quoted,
            "the standing put something on the wire that is not a count: $text",
        )
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
