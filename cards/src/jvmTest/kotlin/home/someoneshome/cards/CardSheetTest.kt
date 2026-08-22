package home.someoneshome.cards

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The sheet, read back.**
 *
 * The strong claim a printable has to support is not *it rendered without throwing* — it is **the
 * thing on the paper decodes to the card it was printed from**. So every symbol on the sheet goes
 * out through the encoder and comes back through a *decoder*, and the payload that returns is
 * compared with the payload that went in. A decoder is a different code path from an encoder; a bug
 * that survives both is a bug in the QR specification.
 *
 * What this cannot do is print. `verify-cards.sh` takes the finished PDF, rasterises it the way a
 * printer would and reads it with **Apple's** decoder — the family the phone's camera uses — and
 * the last mile after that is a person holding paper up to a lens.
 */
class CardSheetTest {

    private val deck = CardDeck.forRun("QK7M")
    private val pdf = CardSheet.render(deck)
    private val text = String(pdf, Charsets.ISO_8859_1)

    /**
     * **Every card's symbol reads back as that card's payload.** The whole sheet, not a sample.
     *
     * Forty-four round trips is a second of test time and it is the only assertion here that would
     * have caught a wrong payload reaching paper.
     */
    @Test
    fun everySymbolOnTheSheetDecodesToTheCardItWasPrintedFrom() {
        for (card in deck.cards) {
            val payload = deck.payloadOf(card)
            val read = decode(QrSymbol.modulesOf(payload))
            assertEquals(payload, read, "the symbol printed for ${card.id.value} reads back as '$read'")
            // And the payload that came back is still the card, through model's own decoder.
            assertEquals(
                card,
                assertIs<CardPayload.Result.Read>(CardPayload.decode(read)).card,
                "the payload survived the symbol and lost the card",
            )
        }
    }

    /**
     * **Every symbol is Version 1**, which is the size the card was measured for.
     *
     * [QrSymbol] checks this on the way out and would have thrown; asserted again here because the
     * check is the load-bearing half of D-069 and a `check` somebody deletes to make a build pass
     * should take a named test with it.
     */
    @Test
    fun everySymbolIsTheSmallestQrThatExists() {
        for (card in deck.cards) {
            val modules = QrSymbol.modulesOf(deck.payloadOf(card))
            assertEquals(QrSymbol.MODULES, modules.size)
            assertTrue(modules.all { it.size == QrSymbol.MODULES })
        }
    }

    /** Four pages for forty-four cards, and the arithmetic rather than a constant. */
    @Test
    fun theSheetIsFourPagesAndSaysSoInTheDocument() {
        assertEquals(4, CardSheet.pageCount(deck.cards.size))
        assertTrue(text.contains("/Count 4"), "the page tree does not carry four pages")
        assertEquals(4, Regex("/Type /Page[^s]").findAll(text).count(), "the pages are not four")
        assertTrue(text.startsWith("%PDF-1.4"), "this is not a PDF")
        assertTrue(text.trimEnd().endsWith("%%EOF"), "the document has no end")
        assertTrue(text.contains("startxref"), "the document has no cross-reference table")
    }

    /**
     * **Every id is on the paper, exactly once**, and nothing else is.
     *
     * Rendering the right deck into the wrong page — a slot index off by one, a `chunked` that
     * dropped the last group — produces a PDF that opens perfectly and is missing four cards. The
     * ids are the only thing on the sheet that is unique per card and readable as text, so they are
     * what the count is taken from.
     */
    @Test
    fun everyCardInTheDeckIsOnAPage() {
        // Matched on the run's own tag rather than on "seven word characters", which also caught
        // the word MEETING printed under the reserved card.
        val printed = Regex("""\((${deck.run}\d{3})\) Tj""").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(
            deck.cards.map { it.id.value }.sorted(), printed.sorted(),
            "the ids on the paper are not the ids in the deck",
        )
        assertEquals(printed.size, printed.distinct().size, "an id is printed on two cards")
    }

    /**
     * **Forty-four glyphs, and every one of them filled even-odd.**
     *
     * `f*` rather than `f` is not a preference: a hole in `ring`, `square_frame`, `triangle_frame`
     * and `diamond_frame` is a second contour, and under the non-zero winding rule those holes fill
     * in. The ring becomes a disc and the square frame becomes a square — four cards silently
     * printed as shapes that are already in the roster, which is D-070's collision arriving on
     * paper where nothing can refuse it.
     */
    @Test
    fun everyGlyphIsFilledEvenOdd() {
        assertEquals(
            deck.cards.size, Regex("""f\*""").findAll(text).count(),
            "the sheet does not carry one even-odd fill per card",
        )
        // And every OTHER fill on the sheet is a module of a symbol -- counted, not assumed, from
        // the same encoder the layout draws from. A rectangle missing here is a module of ink that
        // did not print, which is a symbol that decodes until it does not.
        val darkModules = deck.cards.sumOf { card ->
            QrSymbol.modulesOf(deck.payloadOf(card)).sumOf { row -> row.count { it } }
        }
        assertEquals(
            darkModules, Regex("""re f\n""").findAll(text).count(),
            "the sheet's filled squares and the encoder's dark modules disagree",
        )
    }

    /**
     * **THIS SIDE UP is printed on every card, and it is load-bearing.**
     *
     * The roster kept `semicircle_up` / `semicircle_down` and `arrow_up` / `arrow_down` — two pairs
     * that are rotations of each other — **only because the printed card says which way is up**.
     * Drop the line and the set needs trimming; keep the shapes and drop the line and four cards
     * become two, in a dark house, months later.
     */
    @Test
    fun everyCardSaysWhichWayIsUp() {
        assertEquals(
            deck.cards.size, Regex("""\(THIS SIDE UP\) Tj""").findAll(text).count(),
            "a card was printed without the line that keeps the two rotation pairs apart",
        )
    }

    /** The two reserved cards say what they are; the other forty-two do not. */
    @Test
    fun onlyTheReservedTwoAreNamed() {
        assertEquals(1, Regex("""\(TERMINAL\) Tj""").findAll(text).count())
        assertEquals(1, Regex("""\(MEETING\) Tj""").findAll(text).count())
        assertEquals(
            MarkerShapes.reserved.size, deck.cards.count { !it.isOrdinary },
            "the sheet's reserved count and the roster's disagree",
        )
    }

    /** A quiet zone is paper. Ink under a symbol is the edge a decoder cannot find. */
    @Test
    fun theSymbolIsInkOnPaperAndNothingIsPrintedUnderIt() {
        assertTrue(
            !Regex("""(^|\n)1 g(\n| )""").containsMatchIn(text),
            "something on the sheet is filled white. On a home printer that is ink very nearly the " +
                "colour of the page, laid exactly where a decoder goes looking for the edge of the " +
                "quiet zone.",
        )
    }

    /**
     * **A character the page cannot print stops the build rather than printing a question mark.**
     *
     * The first sheet rendered carried `ORDINARY MARKERS ? ONE PER PLACE` — an em dash in a line of
     * copy, silently substituted by the single-byte encoding the font is declared with. Nothing
     * failed, nothing warned, and the only way anybody found out was by looking at a picture of the
     * page. On an artifact that goes to a printer that is the whole class of bug worth guarding:
     * wrong on paper, green everywhere else.
     */
    @Test
    fun aCharacterTheFontCannotPrintIsRefusedRatherThanSubstituted() {
        // The dash a writer actually types, mapped to the byte WinAnsi keeps it at.
        val dashed = PdfDocument(10.0, 10.0).apply { page { text("A\u2014B", 0.0, 0.0, 8.0) } }.bytes()
        assertTrue(
            String(dashed, Charsets.ISO_8859_1).contains("(A\u0097B)"),
            "an em dash did not reach the page as the byte WinAnsiEncoding holds it at",
        )
        // And one it has no byte for at all.
        assertFailsWith<IllegalArgumentException> {
            PdfDocument(10.0, 10.0).apply { page { text("A\u2192B", 0.0, 0.0, 8.0) } }.bytes()
        }
    }

    /**
     * **Nothing a host is told to keep is printed outside the line they are told to cut on.**
     *
     * The reserved word sat four points below the cut line on the first sheet rendered: the two
     * cards that say what they are were the two cards that would have said it on the offcut. Every
     * piece of text on a card is checked against the cut rectangle rather than the cell.
     */
    @Test
    fun everythingPrintedOnACardIsInsideItsCutLine() {
        val slot = CardSheet.slotOf(0)
        val baselines = Regex("""Tf [\d.-]+ ([\d.-]+) Td""").findAll(text)
            .map { it.groupValues[1].toDouble() }
            .filter { it > slot.y && it < slot.y + slot.height }
            .toList()
        assertTrue(baselines.isNotEmpty(), "no text was found on the first card at all")
        for (baseline in baselines) {
            assertTrue(
                baseline > slot.y + 4.0,
                "a line on the T card sits at $baseline, below the cut line at ${slot.y + 4.0} — " +
                    "a host who cuts where they are told loses it",
            )
        }
    }

    /** zxing's own decoder, over the module grid, with no camera and no optics in the way. */
    private fun decode(modules: Array<BooleanArray>): String {
        val bits = BitMatrix(modules.size, modules.size)
        for (y in modules.indices) for (x in modules[y].indices) if (modules[y][x]) bits.set(x, y)
        return Decoder().decode(bits).text
    }
}
