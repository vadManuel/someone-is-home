package home.someoneshome.platform.transport

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The host's lobby table: the counts it publishes, the setting it clamps, and the text it holds
 * that must never come back out of it in any direction but the house's own.
 */
class LobbyDeskTest {

    private val elliot = Seat(0)
    private val priya = Seat(1)
    private val marcus = Seat(2)

    private fun deskOfThree() = LobbyDesk().apply {
        seated(elliot); seated(priya); seated(marcus)
    }

    @Test
    fun `an empty lobby stands at nothing and is not ready`() {
        val desk = LobbyDesk()
        assertEquals(LobbyBody.Standing(joined = 0, linesIn = 0, insiders = null), desk.standing())
        assertFalse(desk.everyLineIn(), "a lobby with nobody in it offered LIGHTS OUT")
    }

    @Test
    fun `the counts follow the seats and the lines`() {
        val desk = deskOfThree()
        assertEquals(3, desk.standing().joined)
        assertEquals(0, desk.standing().linesIn)
        desk.handedOver(elliot, "i still have priya's spare key")
        desk.handedOver(priya, "i read the group chat i was removed from")
        assertEquals(2, desk.standing().linesIn)
    }

    /** A player who re-typed theirs meant the second one. */
    @Test
    fun `handing over twice replaces the line rather than counting twice`() {
        val desk = deskOfThree()
        desk.handedOver(elliot, "first")
        desk.handedOver(elliot, "second")
        assertEquals(1, desk.standing().linesIn)
        assertEquals("second", desk.lineOf(elliot))
    }

    /**
     * A line filed under nobody is a player who will be told at arming that they never handed one
     * over. Pre-arm, in the light, on the host's own phone: fail loud.
     */
    @Test
    fun `a line from a seat that is not in the lobby is refused loudly`() {
        val desk = LobbyDesk()
        assertFailsWith<IllegalArgumentException> { desk.handedOver(elliot, "anything") }
    }

    /**
     * **`linesIn` can never exceed `joined`.** The LIGHTS OUT gate is a comparison of those two
     * numbers, and a leaver whose line stayed behind would hold a lobby at "4 of 3" forever.
     */
    @Test
    fun `a seat that leaves takes its line with it`() {
        val desk = deskOfThree()
        desk.handedOver(elliot, "i still have priya's spare key")
        desk.handedOver(priya, "something else")
        desk.left(elliot)
        assertEquals(LobbyBody.Standing(joined = 2, linesIn = 1, insiders = null), desk.standing())
        assertNull(desk.lineOf(elliot), "the desk kept the line of somebody who walked out")
    }

    @Test
    fun `the gate closes only when everybody here has handed one over`() {
        val desk = deskOfThree()
        desk.handedOver(elliot, "a")
        desk.handedOver(priya, "b")
        assertFalse(desk.everyLineIn(), "two of three was enough to offer LIGHTS OUT")
        desk.handedOver(marcus, "c")
        assertTrue(desk.everyLineIn())
        // Somebody else walks in and the gate opens again — they owe a line too.
        desk.seated(Seat(3))
        assertFalse(desk.everyLineIn(), "a late arrival did not reopen the gate")
    }

    // ---- D-103 ---------------------------------------------------------------------------

    @Test
    fun `the setting starts UNKNOWN`() {
        assertNull(LobbyDesk().insiders, "the Insider count defaulted to a number")
        assertNull(deskOfThree().standing().insiders)
    }

    @Test
    fun `a hand-picked count is clamped into the band`() {
        val desk = LobbyDesk().apply { repeat(6) { seated(Seat(it)) } }
        assertEquals(1..2, desk.band())
        desk.setInsiders(5)
        assertEquals(2, desk.insiders, "the maximum edge did not clamp the setting")
        desk.setInsiders(0)
        assertEquals(1, desk.insiders, "the minimum edge did not clamp the setting")
        desk.setInsiders(null)
        assertNull(desk.insiders, "UNKNOWN was turned into a number by the clamp")
    }

    /**
     * **The band moves with the seat count, and the setting moves with the band.**
     *
     * A host who picked 3 in a twelve-seat home and then watched four people leave must not keep
     * a 3 the band no longer allows — the count is locked at arming, and arming would lock a
     * number the envelope was written to exclude.
     */
    @Test
    fun `the setting is re-clamped when the lobby shrinks and grows`() {
        val desk = LobbyDesk().apply { repeat(12) { seated(Seat(it)) } }
        desk.setInsiders(3)
        assertEquals(3, desk.insiders)
        repeat(4) { desk.left(Seat(11 - it)) }
        assertEquals(1..2, desk.band(), "eight seats should be a 1-2 band")
        assertEquals(2, desk.insiders, "the setting kept a count the band no longer allows")
        repeat(8) { desk.seated(Seat(20 + it)) }
        assertEquals(2, desk.insiders, "growing the lobby moved a setting that was already lawful")
    }

    // ---- What goes back down the wire ------------------------------------------------------

    /**
     * **The lines are the house's, and the standing cannot carry one.**
     *
     * Checked against the encoded form rather than the object, because the encoded form is what
     * actually leaves: a field that existed but was skipped by the serializer would pass an
     * equality check and still be on the wire, and the reverse — a field that encodes without
     * appearing in `equals` — is exactly the sort of thing nobody looks for.
     */
    @Test
    fun `the standing a full desk publishes quotes nobody`() {
        val desk = deskOfThree()
        val secrets = listOf(
            "i still have priya's spare key",
            "i read the group chat i was removed from",
            "i have never watched the film we all say is our favourite",
        )
        desk.handedOver(elliot, secrets[0])
        desk.handedOver(priya, secrets[1])
        desk.handedOver(marcus, secrets[2])
        desk.setInsiders(1)

        val onTheWire = LobbyWire.encode(desk.standing())
        for (secret in secrets) {
            assertFalse(secret in onTheWire, "a one line went back down the wire: $onTheWire")
        }
        // And not by fragments either — the first word of a line is enough to identify it in a
        // room of six people who all know each other.
        for (word in secrets.flatMap { it.split(" ") }.filter { it.length > 4 }) {
            assertFalse(word in onTheWire, "'$word' from a one line reached the wire: $onTheWire")
        }
        assertEquals(LobbyBody.Standing(joined = 3, linesIn = 3, insiders = 1), desk.standing())
    }

    /** Deleted when the round ends. The seats stay; who was in the round is not a secret. */
    @Test
    fun `the round ending drops every line and keeps the lobby`() {
        val desk = deskOfThree()
        desk.handedOver(elliot, "i still have priya's spare key")
        desk.handedOver(priya, "b")
        desk.roundEnded()
        assertNull(desk.lineOf(elliot), "a line survived the round it was handed over for")
        assertNull(desk.lineOf(priya))
        assertEquals(LobbyBody.Standing(joined = 3, linesIn = 0, insiders = null), desk.standing())
    }
}
