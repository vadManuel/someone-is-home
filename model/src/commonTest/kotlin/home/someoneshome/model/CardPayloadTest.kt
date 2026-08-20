package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** D-069. Nine characters, printed on paper, scanned back months later by torchlight. */
class CardPayloadTest {

    private fun card(shapeId: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shapeId), MarkerId(id))

    /** Every shape in the roster round-trips, which is the whole roster on paper. */
    @Test
    fun `every shape round-trips through a payload`() {
        for (shape in MarkerShapes.all) {
            val original = MarkerCard(CardPayload.VERSION, shape, MarkerId("ABC1234"))
            val result = CardPayload.decode(CardPayload.encode(original))
            assertIs<CardPayload.Result.Read>(result, shape.id)
            assertEquals(original, result.card, shape.id)
        }
    }

    /** Nine characters, with one to spare against the measured Version 1 / level H ceiling. */
    @Test
    fun `a payload is nine characters and fits the smallest symbol`() {
        val payload = CardPayload.encode(card("circle", "0000001"))
        assertEquals(CardPayload.LENGTH, payload.length)
        assertTrue(CardPayload.LENGTH < CardPayload.QR_VERSION_1_H_CAPACITY)
    }

    /**
     * Every character stays in the alphabet, which is what keeps the symbol in alphanumeric mode.
     * Straying out does not look wrong — it silently grows the symbol past Version 1.
     */
    @Test
    fun `a payload never leaves the alphabet`() {
        for (shape in MarkerShapes.all) {
            val id = MarkerId(MarkerShapes.ALPHABET.takeLast(7))
            val payload = CardPayload.encode(MarkerCard(CardPayload.VERSION, shape, id))
            assertTrue(payload.all { it in MarkerShapes.ALPHABET }, payload)
        }
    }

    /**
     * **The id is what makes a lost card recognisable.** Two cards showing the same shape are two
     * different cards, and the payload must say so — otherwise the one found behind a shelf
     * reports a player into whichever room the replacement was registered to.
     */
    @Test
    fun `two cards with the same shape are different cards`() {
        val original = card("diamond", "AAAAAAA")
        val replacement = card("diamond", "BBBBBBB")
        assertTrue(CardPayload.encode(original) != CardPayload.encode(replacement))
        val a = CardPayload.decode(CardPayload.encode(original))
        val b = CardPayload.decode(CardPayload.encode(replacement))
        assertIs<CardPayload.Result.Read>(a)
        assertIs<CardPayload.Result.Read>(b)
        assertEquals(a.card.shape, b.card.shape)
        assertTrue(a.card.id != b.card.id)
    }

    /** A rotation-sensitive pair survives, which is what THIS SIDE UP on the card earns (D-070). */
    @Test
    fun `the rotation-sensitive pairs are distinct payloads`() {
        for ((up, down) in listOf("semicircle_up" to "semicircle_down", "arrow_up" to "arrow_down")) {
            assertTrue(
                CardPayload.encode(card(up, "0000000")) != CardPayload.encode(card(down, "0000000")),
                "$up and $down encode identically",
            )
        }
    }

    // ---- rejection. Each of these is a fact about a piece of paper (D-071). ----

    @Test
    fun `a payload of the wrong length is rejected`() {
        for (bad in listOf("", "1", "10ABC123", "10ABC12345")) {
            val result = CardPayload.decode(bad)
            assertIs<CardPayload.Result.Rejected>(result, "'$bad'")
            assertEquals(CardRejection.WrongLength, result.why)
        }
    }

    @Test
    fun `a character outside the alphabet is rejected`() {
        val result = CardPayload.decode("1?ABC1234".take(9))
        assertIs<CardPayload.Result.Rejected>(result)
        assertEquals(CardRejection.NotInAlphabet, result.why)
    }

    /** Cards outlive builds, so a build that does not know a version has to say so. */
    @Test
    fun `an unknown version is rejected`() {
        val valid = CardPayload.encode(card("circle", "ABC1234"))
        val future = "Z" + valid.substring(1)
        val result = CardPayload.decode(future)
        assertIs<CardPayload.Result.Rejected>(result)
        assertEquals(CardRejection.UnknownVersion, result.why)
    }

    /**
     * A shape character in the alphabet but past the roster maps to nothing.
     *
     * The roster fills the alphabet exactly today, so this case is unreachable by construction —
     * asserted rather than skipped silently, because the day a shape is retired it becomes
     * reachable and this test would otherwise have quietly stopped meaning anything.
     */
    @Test
    fun `a character with no shape is rejected`() {
        val beyond = MarkerShapes.ALPHABET.getOrNull(MarkerShapes.all.size)
        if (beyond == null) {
            assertEquals(MarkerShapes.ALPHABET.length, MarkerShapes.all.size)
            return
        }
        val result = CardPayload.decode("1" + beyond + "ABC1234")
        assertIs<CardPayload.Result.Rejected>(result)
        assertEquals(CardRejection.UnknownShape, result.why)
    }

    /** Encoding is ours, so a bad input is a programming error and says so loudly. */
    @Test
    fun `encoding refuses an id that would grow the symbol`() {
        assertFailsWith<IllegalArgumentException> { CardPayload.encode(card("circle", "short")) }
        assertFailsWith<IllegalArgumentException> {
            CardPayload.encode(
                MarkerCard(CardPayload.VERSION, MarkerShapes.require("circle"), MarkerId("abc1234")),
            )
        }
    }

    /** Decoding never throws, whatever a camera hands it. */
    @Test
    fun `decoding survives arbitrary input`() {
        for (seed in 1..500) {
            val junk = (0 until (seed % 14))
                .map { MarkerShapes.ALPHABET[(seed * it + 7) % 44] }
                .joinToString("")
            CardPayload.decode(junk)
            CardPayload.decode(junk + "?")
        }
    }
}
