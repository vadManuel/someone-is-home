package home.someoneshome.cards

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import home.someoneshome.model.CardPayload

/**
 * **One printed symbol: 21 by 21 modules, and it is checked to be exactly that.**
 *
 * D-069 chose the nine-character payload so it would fit **QR Version 1 at error-correction level
 * H** — the smallest symbol that exists, at the strongest correction, which is what survives a
 * dark room. Every part of that sentence is a decision somebody can undo by accident: a tenth
 * character, a character outside the alphabet, a level dropped to Q to make room. Each one grows
 * the symbol, and the failure appears months later as *the cards do not scan as well as they used
 * to* — on paper, which cannot be patched.
 *
 * So the version is not requested and hoped for. It is **asserted after encoding**, here, while
 * somebody is still standing at a keyboard.
 *
 * ### Why the encoding is not written here
 *
 * A QR encoder is a Reed-Solomon implementation over GF(256), a mask-penalty search and a format
 * bit sequence. Hand-rolled, it would be a few hundred lines that no test in this repo could
 * meaningfully check, because the only way to know a symbol is right is to read it back with
 * something that was written independently. This project has one rule about that shape of problem
 * and it is [MarkerShapes]-shaped: *the alternative was a second roster beside the first, which is
 * precisely the failure D-070 was written about.* Two QR implementations that disagree is the same
 * bug, printed.
 *
 * `CardSheetTest` reads every symbol in the deck back with the same library's **decoder**, which is
 * a different code path, and `verify-cards.sh` reads the finished PDF with Apple's — the decoder
 * family the phone's camera actually uses.
 */
object QrSymbol {

    /** 21 modules square. Version 1, and there is no other version this deck prints. */
    const val MODULES: Int = 21

    /**
     * The quiet zone, in modules, on every side.
     *
     * Four is the specification's minimum and is not padding to taste: a symbol printed to the edge
     * of a card that is then cut out by hand is a symbol with no quiet zone at all, and a decoder
     * that cannot find the boundary does not read it slowly, it does not read it.
     */
    const val QUIET: Int = 4

    /** The symbol plus its quiet zone — what is actually laid out on the card. */
    const val PRINTED: Int = MODULES + 2 * QUIET

    /**
     * The symbol for a payload, as a grid of dark/light, origin top left.
     *
     * Takes the payload rather than the card: the only encoder is [CardPayload.encode], and a
     * function here that took a `MarkerCard` would be a second place a payload could be built.
     */
    fun modulesOf(payload: String): Array<BooleanArray> {
        require(payload.length == CardPayload.LENGTH) {
            "payload '$payload' is ${payload.length} characters, expected ${CardPayload.LENGTH}"
        }
        val code = Encoder.encode(payload, ErrorCorrectionLevel.H)
        check(code.version.versionNumber == 1) {
            "'$payload' encoded to QR version ${code.version.versionNumber}, not 1. The card was " +
                "sized for a 21x21 symbol; anything larger has smaller modules at the same card " +
                "size, and the whole margin D-069 bought is spent."
        }
        val matrix = checkNotNull(code.matrix) { "the encoder produced no matrix for '$payload'" }
        check(matrix.width == MODULES && matrix.height == MODULES) {
            "version 1 came back ${matrix.width}x${matrix.height}, not ${MODULES}x$MODULES"
        }
        return Array(MODULES) { y -> BooleanArray(MODULES) { x -> matrix.get(x, y).toInt() == 1 } }
    }
}
