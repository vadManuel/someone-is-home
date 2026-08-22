package home.someoneshome.cards

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import kotlin.random.Random

/**
 * **The whole printable deck: 44 cards, and every shape in the roster exactly once.**
 *
 * Forty-two ordinary markers plus the two reserved ones — the card marked T, which is the terminal
 * (D-120), and the U card, which is where a meeting is called (D-121, D-152). The reserved pair
 * comes first so that the two cards a host needs to find are the two cards at the top of the first
 * page, rather than somewhere in a sheet of forty-four abstract marks.
 *
 * ### Every card in a run shares a tag, and a new run is a new tag
 *
 * A printed id is seven characters: **four of run and three of card**. The run tag is what makes
 * the id do the job D-069 gave it. *A host who mislays a card and prints a replacement creates two
 * physical cards showing the same shape* — and the shape is the marker's name, so keyed on shape
 * alone the old card, found behind a shelf a year later, would report a player as standing in
 * whichever room the new one was registered to. That corrupts the Terminal's per-room counts, which
 * the design deliberately fills with injected error, **so the bug would hide inside noise the
 * design added on purpose and be undetectable in play.**
 *
 * The id only saves anybody from that if the reprint's ids are *different*. So [random] is the
 * default and [forRun] is the exception: a fresh tag every time somebody prints, and an old tag
 * only when they deliberately name one — which is the single case where repeating an id is right,
 * a host reprinting the one card the dog ate.
 *
 * Four characters of the 44-character alphabet is a little over three and a half million tags,
 * which is not a cryptographic quantity and does not need to be. The question it has to answer is
 * *did these two cards come off the same printer on the same afternoon*, and the cost of a
 * collision is one host, one home, and two cards that look alike.
 */
class CardDeck private constructor(val run: String) {

    /** The reserved two, then the roster's 42, in roster order. Never sorted, never shuffled. */
    val cards: List<MarkerCard> = buildList {
        val shapes = MarkerShapes.reserved.toList() + MarkerShapes.registrable
        shapes.forEachIndexed { index, shape ->
            add(MarkerCard(CardPayload.VERSION, shape, MarkerId(run + number(index + 1))))
        }
    }

    /** What is printed under a card: its own payload, encoded by the only encoder there is. */
    fun payloadOf(card: MarkerCard): String = CardPayload.encode(card)

    /**
     * The card's number within its run, three characters, zero padded.
     *
     * Decimal rather than base-44. A host reading `QK7M013` off a card and typing it into a message
     * is reading a number, and the alphabet's `$%*+-./:` are exactly the characters that turn a
     * spoken id into an argument in a dark hallway.
     */
    private fun number(n: Int): String = n.toString().padStart(CARD_DIGITS, '0')

    companion object {

        /** Four of run, three of card. Seven, because [CardPayload.ID_LENGTH] is seven. */
        const val RUN_LENGTH: Int = 4
        const val CARD_DIGITS: Int = CardPayload.ID_LENGTH - RUN_LENGTH

        /**
         * The characters a run tag is drawn from: **the alphabet's letters and digits, no
         * punctuation.**
         *
         * A narrower set than [MarkerShapes.ALPHABET] on purpose. Every character of a payload has
         * to be in that alphabet or the symbol leaves QR's alphanumeric mode and grows past Version
         * 1 — but a tag is also read aloud and written on the back of an envelope, and `$%*+-./:`
         * are not characters anybody dictates reliably. Narrowing costs nothing: the tag is still
         * one of millions.
         */
        val RUN_ALPHABET: String = MarkerShapes.ALPHABET.filter { it.isLetterOrDigit() }

        /** A new run, and therefore a deck whose ids no earlier printing carries. */
        fun random(random: Random = Random.Default): CardDeck =
            forRun(String(CharArray(RUN_LENGTH) { RUN_ALPHABET[random.nextInt(RUN_ALPHABET.length)] }))

        /**
         * That exact run, reprinted.
         *
         * Refuses a tag it could not print rather than producing one card in a symbol that grew to
         * Version 2 — which would be a sheet where forty-three cards scan at arm's length and one
         * does not, in a dark house, months later. [CardPayload.encode] would catch it too; this
         * catches it while somebody is still standing at a keyboard.
         */
        fun forRun(run: String): CardDeck {
            require(run.length == RUN_LENGTH) {
                "run tag '$run' is ${run.length} characters, expected $RUN_LENGTH"
            }
            val stray = run.filterNot { it in MarkerShapes.ALPHABET }
            require(stray.isEmpty()) {
                "run tag '$run' carries $stray, which is outside the printable alphabet. Every " +
                    "character of a payload must be in it or the encoder leaves alphanumeric mode " +
                    "and the symbol grows past QR Version 1, which is the size the card was " +
                    "measured for."
            }
            return CardDeck(run)
        }
    }
}
