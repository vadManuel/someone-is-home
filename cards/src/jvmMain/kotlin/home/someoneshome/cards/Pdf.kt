package home.someoneshome.cards

import home.someoneshome.model.MarkerShapes
import java.io.ByteArrayOutputStream

/**
 * **A PDF, written by hand, because the alternative is a printing library.**
 *
 * What this file has to produce is four pages of filled rectangles, filled paths and single-line
 * labels in one standard font. That is a couple of hundred lines of a format that has not changed
 * since 1993 — against a dependency whose transitive tail would sit in a repo that currently has
 * exactly one third-party library on the host side and none at all in the app.
 *
 * It writes **uncompressed** streams. A compressed PDF is smaller and unreadable; this one can be
 * opened in a text editor when somebody asks why a card printed the way it did, which is a property
 * worth more than four hundred kilobytes on an artifact printed once an evening.
 *
 * ### Coordinates are PDF's, and the roster's are not
 *
 * PDF's origin is the bottom left with y increasing upward. A [MarkerShape] path is a 16x16 box
 * with **y increasing downward**, which is the SVG convention the roster was traced in. Nothing
 * here flips it silently: [PdfPage.glyph] is the one place that conversion happens and it says so.
 */
class PdfDocument(private val width: Double, private val height: Double) {

    private val pages = mutableListOf<String>()

    /** One page, drawn by the block, appended to the document. */
    fun page(draw: PdfPage.() -> Unit) {
        pages += PdfPage().apply(draw).content()
    }

    /**
     * The document as bytes.
     *
     * The cross-reference table is byte offsets into this very array, so the objects are written
     * first and measured as they go. Getting an offset wrong produces a file that most readers
     * repair silently and one reader somewhere refuses — the worst failure mode available, so the
     * offsets are recorded rather than computed twice.
     */
    fun bytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: String) {
            offsets += out.size()
            write("${offsets.size} 0 obj\n$body\nendobj\n")
        }

        write("%PDF-1.4\n")
        // A comment line of high bytes, which is what tells a transfer program this is binary.
        out.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        // 1 catalog, 2 page tree, 3 font, then a page and a content stream per page.
        val firstPage = 4
        val kids = pages.indices.joinToString(" ") { "${firstPage + it * 2} 0 R" }
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        pages.forEachIndexed { index, content ->
            val stream = firstPage + index * 2 + 1
            obj(
                "<< /Type /Page /Parent 2 0 R " +
                    "/MediaBox [0 0 ${width.pt()} ${height.pt()}] " +
                    "/Resources << /Font << /F1 3 0 R >> >> " +
                    "/Contents $stream 0 R >>"
            )
            obj("<< /Length ${content.toByteArray(Charsets.ISO_8859_1).size} >>\nstream\n$content\nendstream")
        }

        val xref = out.size()
        write("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(it.toString().padStart(10, '0') + " 00000 n \n") }
        write("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}

/** One page's content stream, built by the drawing calls below. */
class PdfPage {

    private val out = StringBuilder()

    internal fun content(): String = out.toString()

    /** A filled rectangle. Every dark module of every symbol is one of these. */
    fun fill(x: Double, y: Double, w: Double, h: Double, grey: Double = 0.0) {
        out.append("${grey.pt()} g\n${x.pt()} ${y.pt()} ${w.pt()} ${h.pt()} re f\n")
    }

    /** A hairline rectangle — the cut line a host follows with scissors. */
    fun outline(x: Double, y: Double, w: Double, h: Double, grey: Double = 0.75, width: Double = 0.4) {
        out.append(
            "${grey.pt()} G ${width.pt()} w\n${x.pt()} ${y.pt()} ${w.pt()} ${h.pt()} re S\n"
        )
    }

    /**
     * A shape from the roster, drawn into a box of [size] with its lower-left corner at [x], [y].
     *
     * **The y flip lives here and nowhere else.** The roster's paths are a 16x16 box with y
     * downward; PDF's y goes up. Filled **even-odd** (`f*`), which is not a preference either: a
     * hole in `ring` and in the three frames is a second contour, and under the non-zero rule those
     * holes fill in — the ring becomes a disc, the square frame becomes a square, and three of the
     * forty-four shapes silently become other shapes that are already in the roster.
     */
    fun glyph(path: String, x: Double, y: Double, size: Double, grey: Double = 0.0) {
        val scale = size / VIEWBOX
        out.append("${grey.pt()} g\n")
        var i = 0
        while (i < path.length) {
            when (val c = path[i]) {
                'M', 'L' -> {
                    val (px, rest) = number(path, i + 1)
                    val (py, next) = number(path, rest)
                    out.append("${(x + px * scale).pt()} ${(y + (VIEWBOX - py) * scale).pt()} ")
                    out.append(if (c == 'M') "m\n" else "l\n")
                    i = next
                }

                'Z', 'z' -> {
                    out.append("h\n")
                    i++
                }

                else -> error("the roster path carries '$c', which is not M, L or Z: $path")
            }
        }
        out.append("f*\n")
    }

    /** One line of text, left edge at [x], baseline at [y]. */
    fun text(s: String, x: Double, y: Double, size: Double, grey: Double = 0.0) {
        out.append(
            "${grey.pt()} g\nBT /F1 ${size.pt()} Tf ${x.pt()} ${y.pt()} Td (${escape(s)}) Tj ET\n"
        )
    }

    /** The same line, centred on [cx]. Helvetica's widths are approximated; see [widthOf]. */
    fun centred(s: String, cx: Double, y: Double, size: Double, grey: Double = 0.0) {
        text(s, cx - widthOf(s, size) / 2, y, size, grey)
    }

    /**
     * A rough Helvetica advance width.
     *
     * Rough is enough and exactness would cost the font's metric tables. Everything centred on
     * these cards is a short line of capitals and digits over a card two inches wide; a point of
     * drift is invisible, and the alternative is parsing an AFM to centre the word `TERMINAL`.
     */
    private fun widthOf(s: String, size: Double): Double = s.sumOf {
        when {
            it in "IJl1 ilj." -> 0.34
            it.isDigit() -> 0.556
            it.isUpperCase() -> 0.70
            else -> 0.55
        }
    } * size

    /**
     * A string as a PDF literal, in **WinAnsi**, and it refuses what it cannot print.
     *
     * The font is declared `/WinAnsiEncoding`, which is one byte a character — so a character above
     * 255 has no byte to be. Written naively it becomes `?` on the page and nothing anywhere says
     * so: the first sheet printed `ORDINARY MARKERS ? ONE PER PLACE` because a line of copy carried
     * an em dash. A printable that silently substitutes a character is a printable that is wrong on
     * paper and right in every test, so the two dashes and the curly quotes are mapped to the bytes
     * WinAnsi actually has, and anything else stops the build.
     */
    private fun escape(s: String): String {
        val mapped = s.map { c ->
            when (c) {
                '\u2014' -> '\u0097' // em dash
                '\u2013' -> '\u0096' // en dash
                '\u2018' -> '\u0091'
                '\u2019' -> '\u0092'
                '\u201C' -> '\u0093'
                '\u201D' -> '\u0094'
                else -> c
            }
        }
        val stray = mapped.filter { it.code > 255 }
        require(stray.isEmpty()) {
            "'$s' carries $stray, which WinAnsiEncoding has no byte for. It would print as a " +
                "question mark and no test would see it."
        }
        return String(mapped.toCharArray())
            .replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }

    /**
     * Reads one number out of a path string, returning it and the index after it.
     *
     * Leading separators are skipped: the roster writes `M7.31 0.03L8.66 0L…`, so a number is
     * preceded by a command letter, a space, or nothing at all depending on where in the pair it
     * sits. A parser that did not skip them read an empty string and every card on the sheet
     * failed at once, which is at least the loud kind of wrong.
     */
    private fun number(path: String, from: Int): Pair<Double, Int> {
        var start = from
        while (start < path.length && (path[start] == ' ' || path[start] == ',')) start++
        var i = start
        while (i < path.length && (path[i].isDigit() || path[i] == '.' || path[i] == '-')) i++
        val text = path.substring(start, i)
        require(text.isNotEmpty()) { "no number at $from in: $path" }
        return text.toDouble() to i
    }

    private companion object {

        /** Every roster path lives in this box, and it is the roster's own number rather than a
         * second 16 written down beside it. */
        val VIEWBOX = MarkerShapes.VIEWBOX.toDouble()
    }
}

/**
 * A number as PDF writes it: at most three decimals, never in exponent form, never locale-dependent.
 *
 * `toString()` on a Double produces `1.0E-4` for small numbers and a comma for a decimal point in
 * half of Europe. Either one is a content stream a reader gives up on partway down the page, and
 * the symptom is a card that prints with its lower half missing.
 */
private fun Double.pt(): String {
    val rounded = Math.round(this * 1000.0) / 1000.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
    else String.format(java.util.Locale.ROOT, "%.3f", rounded).trimEnd('0').trimEnd('.')
}
