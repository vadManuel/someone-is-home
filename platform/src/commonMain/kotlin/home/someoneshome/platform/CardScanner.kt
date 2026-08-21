package home.someoneshome.platform

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes

/**
 * **A camera pointed at a printed card, as far as anything above it is allowed to know.**
 *
 * The host-setup scan is the one scan in this game that is allowed to be easy: back camera, lights
 * on, torch available, a card held still in a lit room. The in-round scan is the front camera in
 * the dark by lamplight and is a different thing entirely (E5) — this interface is not it and must
 * not grow into it.
 *
 * ### It hands over the payload, not a card
 *
 * Nine characters off a symbol, exactly as printed. Decoding them is [CardPayload]'s, in `model`,
 * because a scanner that decided what a card *is* would be a second opinion about the payload
 * format, and the two would one day disagree about a piece of paper that cannot be edited. What
 * comes out of here is what the camera saw; what it means is asked somewhere else.
 *
 * ### The same card arrives over and over
 *
 * A camera cannot tell "a new card" from "the same card, still in frame", and pretending otherwise
 * here would put that guess in the wrong place. Every resolution is delivered; the caller decides
 * what a repeat means, and for registration a repeat is a card being registered to the room it is
 * already in, which is a no-op the host cannot tell from the first scan and does not need to.
 *
 * ### There is no real implementation yet, and that is deliberate
 *
 * AVFoundation, a capture session, a permission prompt and a preview layer are hardware work on a
 * physical device — **the simulator has no camera** — so this build ships the interface, the fake,
 * and the whole of what happens *after* a card is read. The one thing that is missing is the one
 * thing a phone in a hand would provide.
 */
interface CardScanner {

    /**
     * Point at cards. Every symbol resolved arrives at [onPayload] until [stop].
     *
     * Calling it twice replaces the listener rather than adding one — a scan screen that was
     * opened, left and opened again must not register every card twice.
     */
    fun start(onPayload: (String) -> Unit)

    /** Stop reading. Safe to call when nothing was started; the screen is gone either way. */
    fun stop()
}

/**
 * **The playtest scanner: a deck of cards that arrive when something says one did.**
 *
 * A build with no camera still has to be able to walk the whole registration flow, and every
 * outcome of it, on a phone in somebody's hand. This is the deck: real [MarkerCard]s, encoded to
 * real payloads by [CardPayload.encode], so what the flow above receives is the same nine
 * characters a camera would have produced and the decoder is exercised rather than bypassed.
 *
 * **It cycles.** Running off the end and starting again is not a shortcut: the second lap presents
 * ids that are already registered, which is exactly how a host correcting themselves mid-walk
 * moves a card from one room to another — the one outcome a deck that ran out could never reach.
 *
 * The deck itself is a tour of the refusals rather than a list of pleasant cards — see [DECK].
 */
class SeededCardScanner(cards: List<MarkerCard> = DECK) : CardScanner {

    private val payloads: List<String> = cards.map { CardPayload.encode(it) }
    private var listener: ((String) -> Unit)? = null
    private var next = 0

    override fun start(onPayload: (String) -> Unit) {
        listener = onPayload
    }

    override fun stop() {
        listener = null
    }

    /**
     * A card came into frame.
     *
     * The camera's own event, made explicit so a build without one can raise it. Nothing in `ui`
     * calls this: the trigger lives in the cheat surfaces, which are absent from the compilation
     * of a release build.
     */
    fun present() {
        val onPayload = listener ?: return
        if (payloads.isEmpty()) return
        onPayload(payloads[next % payloads.size])
        next++
    }

    /** What the deck will present next, without presenting it — for a control that names it. */
    val peek: MarkerCard?
        get() = payloads.getOrNull(next % payloads.size.coerceAtLeast(1))
            ?.let { (CardPayload.decode(it) as? CardPayload.Result.Read)?.card }

    companion object {

        /**
         * **Six cards, chosen so that walking the deck walks every refusal.**
         *
         * In order: two ordinary cards that simply register; a third card whose shape one of them
         * already carries, which D-086 refuses; the card marked T, which places the terminal; a
         * *second* T card, which one home has no room for; and one more ordinary card. Then it
         * comes round again, and every id on the second lap is one the map already holds.
         *
         * The ids are the seven characters a printed card carries and are readable on purpose —
         * a playtest phone showing `SEED001` is a phone whose scan can be followed by somebody
         * standing next to it.
         *
         * **The shapes are chosen to be ones no sample home already holds.** They collided at
         * first, and the effect was that every ordinary card in the deck was turned away by D-086
         * before it could demonstrate anything: a deck whose first outcome is a refusal proves the
         * refusal and nothing else. The one collision here is between two cards of the deck's own,
         * so the refusal happens because of the walk rather than because of the fixture.
         */
        val DECK: List<MarkerCard> = listOf(
            seeded("bowtie", "SEED001"),
            seeded("lightning", "SEED002"),
            // Same shape as SEED001, different card. Two live cards may never share a shape.
            seeded("bowtie", "SEED003"),
            seeded("t_shape", "SEEDT01"),
            // A second card marked T. One home, one terminal.
            seeded("t_shape", "SEEDT02"),
            seeded("chevron", "SEED004"),
        )

        private fun seeded(shape: String, id: String) = MarkerCard(
            version = CardPayload.VERSION,
            shape = MarkerShapes.require(shape),
            id = MarkerId(id),
        )
    }
}
