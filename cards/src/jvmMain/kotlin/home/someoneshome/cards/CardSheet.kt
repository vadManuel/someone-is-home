package home.someoneshome.cards

import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerShapes

/**
 * **The printable sheet: forty-four cards, four pages, and one thing that cannot be patched.**
 *
 * Story 4.11. Everything else in this repository can be fixed in the morning; a printed card is in
 * somebody's hallway. So the numbers below are all written down with the reason attached, and every
 * one of them is read back by `CardSheetTest` rather than eyeballed in a viewer.
 *
 * ### Why a card is this size
 *
 * D-069 chose a nine-character payload so the symbol is **QR Version 1** — 21 modules across, the
 * smallest that exists — and then said what to do with the margin that buys: **"buy scan margin
 * with card size, not with symbol version."** A [QR_SIZE]-point symbol is 21 modules plus a
 * four-module quiet zone on each side, so a module lands at about a millimetre and a third of
 * printed ink. That is the number the room has to live with, because it is what a phone at arm's
 * length in a corridor is reading.
 *
 * ### Why the card says THIS SIDE UP
 *
 * The roster's own KDoc makes this load-bearing rather than polite: `semicircle_up` /
 * `semicircle_down` and `arrow_up` / `arrow_down` are **rotations of each other**, and the
 * legibility pass kept all four *only because the printed card says which way is up*. A sheet that
 * dropped the line would quietly collapse forty-four shapes into forty-two, and the two collisions
 * would surface as a player standing in the wrong room — inside the injected error the Terminal
 * already carries on purpose, which is where a bug goes to be undetectable.
 *
 * ### The reserved two are the first two cards
 *
 * The card marked T is the terminal (D-120) and the U card is where a meeting is called (D-121,
 * D-152). They are printed first, so the two cards the host has to find are at the top of the first
 * page, and each says what it is under its id — the only cards on the sheet that do, because they
 * are the only two whose shape is spoken for.
 */
object CardSheet {

    /** US Letter, in points. The home printer this is aimed at is in a house, not a print shop. */
    const val PAGE_WIDTH: Double = 612.0
    const val PAGE_HEIGHT: Double = 792.0

    const val MARGIN: Double = 36.0
    const val FOOTER_BAND: Double = 60.0

    const val COLUMNS: Int = 3
    const val ROWS: Int = 4

    /** Twelve to a page, so the whole roster is four pages with four slots to spare. */
    const val PER_PAGE: Int = COLUMNS * ROWS

    val CELL_WIDTH: Double = (PAGE_WIDTH - 2 * MARGIN) / COLUMNS
    val CELL_HEIGHT: Double = (PAGE_HEIGHT - MARGIN - FOOTER_BAND) / ROWS

    /**
     * The symbol's printed size, and the one measurement on this sheet that a room depends on.
     *
     * 100 points is a little under an inch and a half. Across [QrSymbol.PRINTED] modules — the 21
     * of the symbol plus its quiet zone — that is roughly 1.2 mm a module, which is comfortably
     * above the half-millimetre a phone camera needs and leaves the card small enough that
     * forty-four of them are four sheets of paper rather than eleven.
     */
    const val QR_SIZE: Double = 100.0

    /** The shape, large enough to be told apart across a dark hallway by lamplight. */
    const val GLYPH_SIZE: Double = 32.0

    /** How many pages this many cards needs. Four, and it is arithmetic rather than a constant. */
    fun pageCount(cards: Int): Int = (cards + PER_PAGE - 1) / PER_PAGE

    /** Where one card sits: which page, and the cell it occupies in PDF points. */
    data class Slot(
        val page: Int,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
    )

    /**
     * The nth card's cell, top-left slot first, reading across.
     *
     * **The only place the grid arithmetic lives.** [render] draws through it and the manifest
     * beside the PDF is written from it, so `verify-cards.sh` crops exactly the rectangle the
     * generator says it drew into rather than a rectangle a second copy of these numbers produced.
     * Two copies of a layout is how a verification comes to agree with a sheet that is wrong.
     */
    fun slotOf(index: Int): Slot {
        val slot = index % PER_PAGE
        val column = slot % COLUMNS
        val row = slot / COLUMNS
        return Slot(
            page = index / PER_PAGE + 1,
            x = MARGIN + column * CELL_WIDTH,
            // Row 0 is the top of the page, and PDF counts up from the bottom.
            y = PAGE_HEIGHT - MARGIN - (row + 1) * CELL_HEIGHT,
            width = CELL_WIDTH,
            height = CELL_HEIGHT,
        )
    }

    /** The whole deck, laid out. */
    fun render(deck: CardDeck): ByteArray {
        val doc = PdfDocument(PAGE_WIDTH, PAGE_HEIGHT)
        val pages = pageCount(deck.cards.size)
        deck.cards.chunked(PER_PAGE).forEachIndexed { pageIndex, page ->
            doc.page {
                page.forEachIndexed { slot, card -> card(deck, card, slotOf(pageIndex * PER_PAGE + slot)) }
                footer(deck, pageIndex + 1, pages)
            }
        }
        return doc.bytes()
    }

    /** One card, in the cell [slotOf] put it in. */
    private fun PdfPage.card(deck: CardDeck, card: MarkerCard, slot: Slot) {
        val x = slot.x
        val y = slot.y
        val centre = x + CELL_WIDTH / 2

        // The cut line. Grey rather than black: a host cutting along it leaves a hairline on the
        // card, and a black hairline beside a quiet zone is a mark a decoder has to reason about.
        outline(x + CUT_INSET, y + CUT_INSET, CELL_WIDTH - 2 * CUT_INSET, CELL_HEIGHT - 2 * CUT_INSET)

        // Which way is up, and it is at the top because that is the only place it means anything.
        centred(THIS_SIDE_UP, centre, y + CELL_HEIGHT - 10.0, 5.5, grey = 0.45)

        glyph(card.shape.path, centre - GLYPH_SIZE / 2, y + CELL_HEIGHT - 46.0, GLYPH_SIZE)

        symbol(deck.payloadOf(card), centre - QR_SIZE / 2, y + 26.0, QR_SIZE)

        // EVERYTHING BELOW THE CUT LINE IS SCISSORS. The reserved word first sat at y+3, four
        // points below the line a host is told to cut along -- so the two cards on the sheet that
        // say what they are were the two cards that would have said it on the offcut.
        centred(card.id.value, centre, y + 17.0, 8.0)
        val reserved = when {
            card.isTerminal -> "TERMINAL"
            card.isMeeting -> "MEETING"
            else -> null
        }
        if (reserved != null) centred(reserved, centre, y + 8.5, 5.5, grey = 0.45)
    }

    /**
     * The symbol itself: one filled square per dark module, inside its quiet zone.
     *
     * **No white is drawn.** The quiet zone is paper — a white rectangle under the symbol would be
     * ink on a home printer, and ink that is *nearly* the colour of the page is exactly the surface
     * that makes a decoder hunt for an edge that is not there.
     */
    private fun PdfPage.symbol(payload: String, x: Double, y: Double, size: Double) {
        val module = size / QrSymbol.PRINTED
        val modules = QrSymbol.modulesOf(payload)
        for (row in modules.indices) {
            for (column in modules[row].indices) {
                if (!modules[row][column]) continue
                fill(
                    x + (QrSymbol.QUIET + column) * module,
                    // The matrix's first row is the top of the symbol; PDF's y counts up.
                    y + size - (QrSymbol.QUIET + row + 1) * module,
                    module,
                    module,
                )
            }
        }
    }

    /**
     * The line under the grid — and on the first page, the two sentences a host needs before they
     * pick up scissors.
     *
     * The run tag is in it because a host holding two decks from two evenings has no other way to
     * tell them apart: the ids differ, but the tag is the four characters that say *these forty-four
     * belong together*.
     */
    private fun PdfPage.footer(deck: CardDeck, page: Int, pages: Int) {
        val centre = PAGE_WIDTH / 2
        if (page == 1) {
            centred(FIRST_PAGE_NOTE, centre, 40.0, 7.0, grey = 0.3)
        }
        centred(
            "SOMEONE'S HOME  ·  MARKER CARDS  ·  RUN ${deck.run}  ·  " +
                "PAGE $page OF $pages  ·  ${MarkerShapes.all.size} SHAPES, EACH ONE ONCE",
            centre,
            26.0,
            7.0,
            grey = 0.45,
        )
    }

    /**
     * Load-bearing text, not decoration — see the class KDoc. Four shapes in the roster are two
     * pairs of rotations and the sentence is the only thing keeping them apart.
     */
    private const val THIS_SIDE_UP = "THIS SIDE UP"

    private const val FIRST_PAGE_NOTE =
        "THE T CARD IS THE TERMINAL. THE U CARD IS WHERE A MEETING IS CALLED. " +
            "THE OTHER 42 ARE ORDINARY MARKERS: ONE PER PLACE, ANY PLACE YOU LIKE."

    /** The cut line sits a little inside the cell, so two neighbouring cards never share a line. */
    private const val CUT_INSET = 4.0
}
