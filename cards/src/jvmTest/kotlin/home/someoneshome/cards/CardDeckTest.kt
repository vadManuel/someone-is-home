package home.someoneshome.cards

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerShapes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **The deck, before any of it is on paper.**
 *
 * A printed card is the one artifact in this project that cannot be fixed in the morning, so the
 * questions here are the ones whose wrong answers are only discoverable in somebody's hallway: is
 * every shape there, is it there once, and can a card that cannot be printed get as far as a
 * printer.
 */
class CardDeckTest {

    private val deck = CardDeck.forRun("QK7M")

    /**
     * **Every registrable shape exactly once, plus the two reserved.**
     *
     * Forty-two ordinary markers and the two cards whose shapes are spoken for (D-120, D-121). Said
     * as three separate assertions because the three ways to get it wrong are different: a shape
     * left out is a marker nobody can place, a shape printed twice is two live cards with one name
     * — which is the collision D-086 refuses at registration and cannot refuse on paper — and a
     * reserved shape printed as an ordinary card is a home with two terminals.
     */
    @Test
    fun everyShapeInTheRosterIsOnTheSheetExactlyOnce() {
        assertEquals(MarkerShapes.all.size, deck.cards.size, "the sheet is not the whole roster")
        assertEquals(
            MarkerShapes.all.map { it.id }.toSet(),
            deck.cards.map { it.shape.id }.toSet(),
            "a shape in the roster has no card, or a card carries a shape the roster does not have",
        )
        assertEquals(
            deck.cards.size, deck.cards.map { it.shape.id }.distinct().size,
            "two cards on one sheet carry the same shape, which is two markers with one name",
        )
        assertEquals(1, deck.cards.count { it.isTerminal }, "one home, one terminal")
        assertEquals(1, deck.cards.count { it.isMeeting }, "one home, one meeting card")
        assertEquals(
            MarkerShapes.registrable.size, deck.cards.count { it.isOrdinary },
            "the ordinary count drifted from the roster's own registrable count",
        )
    }

    /** The two a host has to find are the two at the top of the first page. */
    @Test
    fun theReservedPairIsPrintedFirst() {
        assertTrue(deck.cards[0].isTerminal, "the T card is not the first card on the sheet")
        assertTrue(deck.cards[1].isMeeting, "the meeting card is not the second card on the sheet")
    }

    /**
     * **Every id is unique, seven characters, and inside the printable alphabet.**
     *
     * The alphabet is QR's alphanumeric set minus SPACE, and straying outside it does not merely
     * look wrong — it pushes the encoder into byte mode and the symbol grows past Version 1. That
     * would be one card on the sheet with smaller modules than the other forty-three, which is a
     * card that scans worse in a dark room for a reason nobody would ever go looking for.
     */
    @Test
    fun everyIdIsPrintableAndNoTwoAreTheSame() {
        assertEquals(deck.cards.size, deck.cards.map { it.id.value }.distinct().size)
        for (card in deck.cards) {
            assertEquals(CardPayload.ID_LENGTH, card.id.value.length, card.id.value)
            assertTrue(
                card.id.value.all { it in MarkerShapes.ALPHABET },
                "id '${card.id.value}' leaves the printable alphabet",
            )
            assertTrue(card.id.value.startsWith(deck.run), "a card is not in its own run")
        }
    }

    /**
     * **A reprint is a different set of ids, and that is the whole reason the id exists.**
     *
     * D-069: a host who mislays a card and prints a replacement creates two physical cards showing
     * the same shape, and the old one found behind a shelf a year later would report a player as
     * standing in whichever room the new one was registered to — *inside the injected error the
     * Terminal already carries on purpose*, which is where a bug goes to be undetectable in play.
     * The id makes the stale card recognisable as a card nobody registered. It can only do that if
     * the replacement's id is not the same one.
     */
    @Test
    fun twoPrintingsDoNotHandOutTheSameIds() {
        val first = CardDeck.random(Random(1))
        val second = CardDeck.random(Random(2))
        assertTrue(first.run != second.run, "two runs drew the same tag from a 44-character alphabet")
        assertTrue(
            first.cards.map { it.id.value }.intersect(second.cards.map { it.id.value }.toSet()).isEmpty(),
            "a reprint handed out ids the earlier printing already used, so a card found behind a " +
                "shelf next year is indistinguishable from the card that replaced it",
        )
    }

    /** Naming a run reprints it exactly — the one case where repeating an id is right. */
    @Test
    fun namingARunReprintsThatRunAndNotAnother() {
        assertEquals(deck.cards, CardDeck.forRun("QK7M").cards)
    }

    /**
     * **A run tag it could not print is refused at the keyboard.**
     *
     * `CardPayload.encode` would refuse it too, forty-four cards later. This refuses it while
     * somebody is still standing there, with the reason attached.
     */
    @Test
    fun aRunTagOutsideTheAlphabetIsRefused() {
        // Lower case: the alphabet is upper case only, and a printer would happily print it.
        assertFailsWith<IllegalArgumentException> { CardDeck.forRun("qk7m") }
        // A space: excluded from the alphabet precisely because it is ambiguous in print.
        assertFailsWith<IllegalArgumentException> { CardDeck.forRun("QK M") }
        // The wrong length, in both directions.
        assertFailsWith<IllegalArgumentException> { CardDeck.forRun("QK7") }
        assertFailsWith<IllegalArgumentException> { CardDeck.forRun("QK7MM") }
    }

    /** A random run is always printable, or the default path is the one that breaks. */
    @Test
    fun aRandomRunIsAlwaysOneThatCanBePrinted() {
        repeat(200) { seed ->
            val random = CardDeck.random(Random(seed))
            assertTrue(
                random.run.all { it in MarkerShapes.ALPHABET },
                "a generated run tag '${random.run}' cannot be printed",
            )
            // The expensive half: every card of it actually encodes.
            random.cards.forEach { random.payloadOf(it) }
        }
    }
}
