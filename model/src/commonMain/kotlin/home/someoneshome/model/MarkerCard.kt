package home.someoneshome.model

/**
 * What is physically printed on one marker card (D-069).
 *
 * A card is anonymous stock until it is registered. It carries a [shape], which is the marker's
 * **name** — the app never shows the id, because a shape resolves faster than two digits by
 * lamplight and does not need to be the right way up.
 *
 * ### The id exists because paper is lost
 *
 * A shape alone was considered and is not enough. A host who mislays a card and prints a
 * replacement creates two physical cards showing the same shape. Keyed on shape, the old one —
 * found later behind a shelf — would report a player as standing in whichever room the *new* one
 * was registered to. **That corrupts the Terminal's per-room counts, which the design deliberately
 * fills with injected error, so the bug would hide inside noise the design added on purpose and
 * be undetectable in play.**
 *
 * The [id] is what makes the stale card recognisable as a card nobody registered.
 */
data class MarkerCard(val version: Int, val shape: MarkerShape, val id: MarkerId)

/** Why a scanned payload could not become a card. Every case is a fact about a piece of paper. */
enum class CardRejection {
    /** Not nine characters. Almost certainly not one of our cards. */
    WrongLength,

    /** A character outside the printable alphabet — a misread, or a different symbology. */
    NotInAlphabet,

    /** A version this build does not know. Cards outlive builds; builds must say so. */
    UnknownVersion,

    /** The shape character maps to no shape in the roster. */
    UnknownShape,
}

/**
 * The card payload: **version + shape + id, nine characters** (D-069).
 *
 * Nine fits **QR Version 1 at error-correction level H** — 21x21 modules, the smallest symbol
 * that exists, strongest correction. Capacity was verified empirically at ten characters, so
 * there is one character of headroom and no more. Micro QR was rejected: one finder pattern
 * instead of three and much weaker correction, in a room where blur is the operating condition.
 * **Buy scan margin with card size, not with symbol version.**
 *
 * Every character comes from [MarkerShapes.ALPHABET], which is QR's alphanumeric set minus SPACE.
 * That keeps the payload in alphanumeric mode at 11 bits per character pair rather than byte
 * mode, which is the whole reason it fits in a Version 1 symbol at all. Straying outside the
 * alphabet does not merely look wrong — it silently pushes the encoder into byte mode and the
 * symbol grows.
 */
object CardPayload {

    const val LENGTH: Int = 9
    const val ID_LENGTH: Int = 7

    /** The only version this build writes. A card carries it so a later build can refuse. */
    const val VERSION: Int = 1

    /** Ten characters is the measured Version 1 / level H ceiling. Nine leaves one spare. */
    const val QR_VERSION_1_H_CAPACITY: Int = 10

    private val ALPHABET = MarkerShapes.ALPHABET

    /**
     * Render a card to its printed payload.
     *
     * Throws rather than returning null: every input here is one we constructed, so a failure is
     * a programming error and not a misread card.
     */
    fun encode(card: MarkerCard): String {
        val versionChar = ALPHABET.getOrNull(card.version)
            ?: throw IllegalArgumentException("version ${card.version} has no character")
        val shapeIndex = MarkerShapes.all.indexOfFirst { it.id == card.shape.id }
        require(shapeIndex >= 0) { "shape '${card.shape.id}' is not in the roster" }
        require(card.id.value.length == ID_LENGTH) {
            "id '${card.id.value}' is ${card.id.value.length} characters, expected $ID_LENGTH"
        }
        require(card.id.value.all { it in ALPHABET }) {
            "id '${card.id.value}' leaves the alphabet, which forces the encoder out of " +
                "alphanumeric mode and grows the symbol past Version 1"
        }
        val payload = "$versionChar${MarkerShapes.encode(shapeIndex)}${card.id.value}"
        check(payload.length == LENGTH) { "payload is ${payload.length}, expected $LENGTH" }
        return payload
    }

    /**
     * Read a scanned payload.
     *
     * Returns null with a [CardRejection] rather than throwing. **This one is allowed to be
     * specific** (D-071): an unreadable card is a fact about a piece of paper, not a statement
     * about a player, so the screen may say so. Every other refusal in the scan path produces one
     * indistinguishable message.
     */
    fun decode(payload: String): Result {
        if (payload.length != LENGTH) return Result.Rejected(CardRejection.WrongLength)
        if (payload.any { it !in ALPHABET }) return Result.Rejected(CardRejection.NotInAlphabet)

        val version = ALPHABET.indexOf(payload[0])
        if (version != VERSION) return Result.Rejected(CardRejection.UnknownVersion)

        val shape = MarkerShapes.decode(payload[1])
            ?: return Result.Rejected(CardRejection.UnknownShape)

        return Result.Read(MarkerCard(version, shape, MarkerId(payload.substring(2))))
    }

    sealed interface Result {
        data class Read(val card: MarkerCard) : Result
        data class Rejected(val why: CardRejection) : Result
    }
}
