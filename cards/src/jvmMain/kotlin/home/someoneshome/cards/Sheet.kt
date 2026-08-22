package home.someoneshome.cards

import java.io.File

/**
 * **Print the deck.**
 *
 * ```
 * ./gradlew :cards:sheet                 a fresh run tag, and therefore fresh printed ids
 * ./gradlew :cards:sheet -Prun=QK7M      that exact run again, for the one card the dog ate
 * ```
 *
 * The file is named after the run, so two printings never overwrite each other on the way to the
 * printer — which matters more than it looks: two decks with the same filename and different ids is
 * the exact confusion the run tag exists to prevent, reproduced in the downloads folder.
 *
 * **The PDF is not committed.** It is generated, printed and thrown away; what is committed is the
 * thing that generates it, because the roster it draws from will outlive any particular sheet.
 */
fun main(args: Array<String>) {
    val into = File(args.getOrElse(0) { "build/deck" })
    val deck = args.getOrNull(1)?.let { CardDeck.forRun(it.uppercase()) } ?: CardDeck.random()

    into.mkdirs()
    val file = File(into, "someone-is-home-cards-${deck.run}.pdf")
    file.writeBytes(CardSheet.render(deck))

    // The manifest beside the PDF: one line per card, `payload page x y width height`, in points.
    //
    // It is what `verify-cards.sh` reads the sheet against — it crops each cell and asks Apple's
    // decoder what is in it. Both the geometry here and the geometry the pages were drawn with come
    // out of `CardSheet.slotOf`, so the crop is the rectangle the generator says it drew into
    // rather than a second copy of the layout that could agree with a sheet that is wrong.
    File(into, "someone-is-home-cards-${deck.run}.cards.txt").writeText(
        deck.cards.mapIndexed { index, card ->
            val slot = CardSheet.slotOf(index)
            "${deck.payloadOf(card)} ${slot.page} ${slot.x} ${slot.y} ${slot.width} ${slot.height}"
        }.joinToString("\n") + "\n"
    )

    println("run ${deck.run} — ${deck.cards.size} cards on ${CardSheet.pageCount(deck.cards.size)} pages")
    println("  ${file.absolutePath}")
    println("  ${deck.cards.first().id.value} .. ${deck.cards.last().id.value}")
    println("Print at 100%. Do not let the printer scale to fit — a shrunk symbol is a shrunk module.")
}
