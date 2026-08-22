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
 * ### The real one is a camera, and it lives on the far side of an expect
 *
 * AVFoundation, a capture session and a permission prompt are hardware work on a physical device —
 * **the simulator has no camera** — so [deviceCardScanner] returns something that cannot be
 * exercised by `./gradlew check` on any target this repo builds. What check *can* certify is
 * everything on this side of the seam: the fake below, the decoder in `model`, and the whole of
 * what happens after a card is read.
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
 * **The camera this phone actually has**, or something silent on a target with none.
 *
 * Built where a scan screen is, and stopped when it goes away: a capture session holds the camera
 * and the power that goes with it, and the evening runs on batteries in a dark house. It is not
 * held for the life of the app the way the haptic engine is, because a motor costs milliseconds to
 * start and a camera costs the camera.
 *
 * A target with no lens — the Simulator, and any future desktop harness — returns a scanner that
 * starts nothing and delivers nothing, rather than one that refuses loudly. Rule 6, and rule 5
 * behind it: a screen that blanked because the camera threw is a screen that went dark in a room
 * where dark means something.
 */
expect fun deviceCardScanner(): CardScanner

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
         * **Eight cards, chosen so that walking the deck walks every refusal.**
         *
         * In order: two ordinary cards that simply register; a third card whose shape one of them
         * already carries, which D-086 refuses; the card marked T, which places the terminal; a
         * *second* T card, which one home has no room for; the meeting card, which places the
         * meeting area; a *second* meeting card, which one home has no room for either; and one
         * more ordinary card. Then it comes round again, and every id on the second lap is one the
         * map already holds.
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
         *
         * The two reserved shapes are exempt from that rule and must not be swapped for ones the
         * fixture lacks: `t_shape` and `u_shape` are what is printed on the paper (D-120, D-121),
         * so a deck carrying anything else there would demonstrate an ordinary marker.
         */
        val DECK: List<MarkerCard> = listOf(
            seeded("bowtie", "SEED001"),
            seeded("lightning", "SEED002"),
            // Same shape as SEED001, different card. Two live cards may never share a shape.
            seeded("bowtie", "SEED003"),
            seeded(MarkerShapes.TERMINAL.id, "SEEDT01"),
            // A second card marked T. One home, one terminal.
            seeded(MarkerShapes.TERMINAL.id, "SEEDT02"),
            seeded(MarkerShapes.MEETING.id, "SEEDU01"),
            // A second meeting card. One home, one meeting card.
            seeded(MarkerShapes.MEETING.id, "SEEDU02"),
            seeded("chevron", "SEED004"),
        )

        private fun seeded(shape: String, id: String) = MarkerCard(
            version = CardPayload.VERSION,
            shape = MarkerShapes.require(shape),
            id = MarkerId(id),
        )
    }
}
