package home.someoneshome.platform

import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes

/**
 * **Which of the two scans a session is**, and everything that follows from it.
 *
 * There are two scans in this game and they are opposites. Registration happens at host setup **in
 * a lit room with the party standing in front of the host**: back camera, a live picture so the
 * host can aim, a torch when the hall light is poor. The in-round scan happens in a dark house with
 * the panel held up to the marker: **front camera, no picture at all**, because the lit screen *is*
 * the light the card is read by and it is facing the paper (D-159, gdd M1).
 *
 * ### The three fields are the whole difference, and they are declared once
 *
 * A camera that read its facing off one constant and its preview off another would be two rules
 * about the same scan, and the pair would drift the first time somebody fixed half of a bug. So the
 * configuration is a table with two rows, everything downstream reads it rather than deciding for
 * itself, and `ScanUseTest` refuses a row whose facing or preview has been swapped. **That test is
 * the point of this type existing** — the bug it stands against shipped: the registration screen
 * said BACK CAMERA . POINT AT THE CODE over a session hard-coded to the front lens.
 *
 * ### It is not a settings bag and must not become one
 *
 * Nothing may be added here that a caller could want *sometimes*. Two scans, two rows, and a third
 * row would mean a third scan exists — which is a design question, not a parameter.
 */
enum class ScanUse(
    /** Which lens. The paper is on the far side of the phone at setup and on the near side in play. */
    val facing: CameraFacing,
    /** Whether a live picture is drawn. False in play, and its absence there is the design. */
    val preview: Boolean,
    /** Whether the torch may be lit. The house is dark in play and the lamp is the game's. */
    val torch: Boolean,
) {

    /** Host setup: **back camera, live viewfinder, torch available.** In the light. */
    Registration(CameraFacing.Back, preview = true, torch = true),

    /** In a round: **front camera, no preview, no torch.** D-159, and it may never gain either. */
    InRound(CameraFacing.Front, preview = false, torch = false),
}

/** Which lens. Two of them, named rather than numbered, so a swap is readable in a diff. */
enum class CameraFacing { Front, Back }

/**
 * **A camera pointed at a printed card, as far as anything above it is allowed to know.**
 *
 * One interface, two configurations — see [ScanUse], which is where the split is named. A scanner
 * carries the [use] it was built for and cannot be talked out of it: there is no method here that
 * changes a lens, because a session that could be reconfigured mid-evening is a session that ends
 * up on the wrong lens on the one screen where nobody can see the screen.
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

    /** Which of the two scans this session is. Fixed when it was built; nothing may change it. */
    val use: ScanUse

    /**
     * Point at cards. Every symbol resolved arrives at [onPayload] until [stop].
     *
     * Calling it twice replaces the listener rather than adding one — a scan screen that was
     * opened, left and opened again must not register every card twice.
     */
    fun start(onPayload: (String) -> Unit)

    /** Stop reading. Safe to call when nothing was started; the screen is gone either way. */
    fun stop()

    /**
     * **The LED beside the back lens, on or off** — the host's control at registration.
     *
     * Refused, in the log and nowhere else, on a scanner whose [ScanUse.torch] is false. That
     * refusal is not defensive tidiness: the in-round scan happens in a dark house where **light is
     * game state** (rule 5), and a torch lit behind a screen nobody is looking at would be a light
     * nobody authored, visible from three rooms away, on a phone whose owner is trying not to be
     * found. It is silent to the player and loud to the log, rule 6 exactly.
     *
     * Safe before [start] and after [stop]; a scanner with no session yet has nothing to light and
     * says so.
     */
    fun torch(lit: Boolean)
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
expect fun deviceCardScanner(use: ScanUse): CardScanner

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
class SeededCardScanner(
    cards: List<MarkerCard> = DECK,
    /**
     * Registration by default, because that is the walk this deck exists for: the tour of the
     * refusals below is a host's stack of paper, not a player's single marker in the dark.
     */
    override val use: ScanUse = ScanUse.Registration,
) : CardScanner {

    private val payloads: List<String> = cards.map { CardPayload.encode(it) }
    private var listener: ((String) -> Unit)? = null
    private var next = 0

    /** Whether the deck's imaginary torch is lit. Nothing draws it; a test reads it. */
    var torchLit: Boolean = false
        private set

    override fun start(onPayload: (String) -> Unit) {
        listener = onPayload
    }

    override fun stop() {
        listener = null
    }

    /** The same refusal the camera makes, on a deck: a scan with no torch may not light one. */
    override fun torch(lit: Boolean) {
        if (!use.torch) return
        torchLit = lit
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
