package home.someoneshome.platform

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The deck that stands in for a camera.**
 *
 * There is no real [CardScanner] yet and there cannot be one here: a capture session needs a lens,
 * and the simulator has none. What is testable — and what actually protects the flow above — is
 * that the fake hands over the **same nine characters a camera would have**, so the decoder is
 * exercised rather than bypassed and the flow is never handed a card that was never encoded.
 */
class CardScannerTest {

    private fun read(payload: String) =
        assertIs<CardPayload.Result.Read>(CardPayload.decode(payload)).card

    private fun collecting(scanner: SeededCardScanner, times: Int): List<String> {
        val seen = mutableListOf<String>()
        scanner.start { seen += it }
        repeat(times) { scanner.present() }
        return seen
    }

    /** Payloads, not cards. A fake that handed over decoded fields would prove nothing. */
    @Test
    fun `the deck hands over payloads a real camera could have produced`() {
        val payloads = collecting(SeededCardScanner(), SeededCardScanner.DECK.size)
        assertEquals(SeededCardScanner.DECK.size, payloads.size)
        for (payload in payloads) {
            assertEquals(CardPayload.LENGTH, payload.length, payload)
            assertTrue(payload.all { it in MarkerShapes.ALPHABET }, payload)
        }
        assertEquals(SeededCardScanner.DECK, payloads.map { read(it) })
    }

    /**
     * **The deck is a tour of the refusals**, and that is the whole reason it is written down
     * rather than generated: a deck of pleasant cards can only ever demonstrate the outcome
     * nobody needed to check.
     */
    @Test
    fun `the deck carries a duplicate shape and two cards marked T`() {
        val deck = SeededCardScanner.DECK
        val ordinary = deck.filterNot { it.isTerminal }
        assertEquals(
            2, deck.count { it.isTerminal },
            "one T card places the terminal; the second is the refusal that needs a screen",
        )
        assertTrue(
            ordinary.map { it.shape.id }.distinct().size < ordinary.size,
            "no two cards share a shape, so D-086's refusal cannot be reached by scanning",
        )
        assertEquals(deck.size, deck.map { it.id }.distinct().size, "two cards share a printed id")
    }

    /**
     * Running off the end and starting again is not a shortcut.
     *
     * The second lap presents ids the map already holds, which is how a host correcting themselves
     * mid-walk moves a card from one room to another — the one outcome a deck that ran out could
     * never reach.
     */
    @Test
    fun `the deck cycles so the second lap is a card already registered`() {
        val payloads = collecting(SeededCardScanner(), SeededCardScanner.DECK.size + 2)
        assertEquals(payloads[0], payloads[SeededCardScanner.DECK.size])
        assertEquals(payloads[1], payloads[SeededCardScanner.DECK.size + 1])
    }

    /** A screen opened, left and opened again must not register every card twice. */
    @Test
    fun `starting twice replaces the listener rather than adding one`() {
        val scanner = SeededCardScanner()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        scanner.start { first += it }
        scanner.start { second += it }
        scanner.present()
        assertEquals(emptyList(), first)
        assertEquals(1, second.size)
    }

    @Test
    fun `nothing is delivered after stop and stopping twice is not an error`() {
        val scanner = SeededCardScanner()
        val seen = mutableListOf<String>()
        scanner.start { seen += it }
        scanner.stop()
        scanner.stop()
        scanner.present()
        assertEquals(emptyList(), seen)
    }

    /** The chip that fires the deck names the card first, so a scan can be aimed rather than hoped. */
    @Test
    fun `peek names the card the next present will deliver`() {
        val scanner = SeededCardScanner()
        val seen = mutableListOf<String>()
        scanner.start { seen += it }
        repeat(3) {
            val expected = scanner.peek
            scanner.present()
            assertEquals(expected, read(seen.last()))
        }
    }

    /** A deck of one is legal and is what a hand-seeded test uses. */
    @Test
    fun `a deck can be seeded with cards of the callers own`() {
        val one = MarkerCard(CardPayload.VERSION, MarkerShapes.require("bowtie"), MarkerId("MINE-01"))
        assertEquals(listOf(one, one), collecting(SeededCardScanner(listOf(one)), 2).map { read(it) })
    }
}
