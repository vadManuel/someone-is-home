package home.someoneshome.model

/**
 * The marker vocabulary: 44 shapes, one character each.
 *
 * **A marker is a printed card carrying an id and one of these shapes.** The app never shows
 * the id — hosts and players both navigate by the shape, because a shape resolves faster than
 * two digits by lamplight and does not need to be the right way up to be recognised.
 *
 * ### Where this came from, and why it is not hand-drawn
 *
 * Generated from the `shape-encoder` project's `src/shapes.js`, which is where the set was
 * *chosen* — by measuring how confusable every pair is at small sizes, not by taste. Two facts
 * from that measurement are load-bearing here:
 *
 * - The tightest pairs are `semicircleUp`/`semicircleDown` and `arrowUp`/`arrowDown`, which are
 *   rotations of each other. They are safe **only because the printed card says THIS SIDE UP**.
 *   Remove that and the set needs trimming.
 * - `pentagon` and `hexagon` are deliberately absent. An earlier roster carried a pentagon and
 *   the legibility pass cut it; anything that reads as "circle with corners" collides with both
 *   `circle` and its neighbours.
 *
 * The 30 polygon shapes emit their own contours. The 14 defined as an implicit `inside(x, y)`
 * predicate — `circle`, `ring`, `crescent`, `pacman`, `pieWedge` and the rest — were **traced
 * from that predicate** by marching squares rather than redrawn by hand, so a change upstream
 * flows through instead of silently disagreeing. Every path was then checked by rasterising it
 * and comparing against the original: all 44 scored IoU >= 0.99, worst `crescentRight` at 0.9907.
 *
 * Coordinates are a 16x16 box, y downward, filled **even-odd** — a hole is simply a second
 * contour, which is how `ring` and the three frames work.
 *
 * ### Why this lives in `model` and not in `ui`
 *
 * A shape is a marker's **identity**, not its decoration. [id] is what a printed card encodes and
 * what a scan decodes months later, so the roster is wire data that `ui` happens to draw — and it
 * had to be in one place before map persistence could name a marker at all.
 *
 * The alternative was a second roster beside the first, which is precisely the failure D-070 was
 * written about: two things decoding to the same marker put a player in the wrong room, and that
 * wrong count lands inside the injected error the Terminal already carries on purpose. **The bug
 * would hide inside noise the design added deliberately, and would be undetectable in play.**
 *
 * Nothing here knows how to draw. [path] is a string; parsing and rendering stay in `ui`, which
 * is what keeps this side free of a graphics dependency.
 */
data class MarkerShape(
    /** The upstream name, and the wire identity. Never renumbered, never reused. */
    val id: String,
    /** The same name in Kotlin form, for reading in code. */
    val name: String,
    /** Path data in a 16x16 box, even-odd fill. */
    val path: String,
)

object MarkerShapes {

    /** Every shape lives in this box. Matches the device design's icon convention. */
    const val VIEWBOX: Float = 16f

    /**
     * The alphabet a shape encodes to, and it is not arbitrary.
     *
     * It is QR's alphanumeric character set **minus SPACE**, which is exactly 44 characters —
     * the size of the roster. That keeps a marker's payload in QR alphanumeric mode at 11 bits
     * per character pair rather than byte mode, which is what lets the whole payload sit in a
     * Version 1 symbol: 21x21 modules, the smallest QR that exists, at error-correction level H.
     * Biggest modules and strongest correction, which is what survives a dark room.
     *
     * SPACE is skipped because it is ambiguous in print.
     */
    const val ALPHABET: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ\$%*+-./:"

    /** In upstream order. The index into this list is the character index in [ALPHABET]. */
    val all: List<MarkerShape> = listOf(
        MarkerShape("circle", "circle", "M7.31 0.03L8.66 0L10.09 0.25L11.16 0.63L12.03 1.06L13.03 1.75L14.25 2.97L14.94 3.97L15.38 4.84L15.75 5.91L16 7.34L16 8.66L15.75 10.09L15.38 11.16L14.94 12.03L14.25 13.03L13.03 14.25L12.03 14.94L11.16 15.38L10.09 15.75L8.69 15.97L7.34 16L5.91 15.75L4.84 15.38L3.97 14.94L2.97 14.25L1.75 13.03L1.06 12.03L0.63 11.16L0.25 10.09L0 8.66L0 7.34L0.25 5.91L0.63 4.84L1.06 3.97L1.75 2.97L2.97 1.75L3.97 1.06L4.84 0.63L5.91 0.25L6.78 0.06L7.28 0.06Z"),
        MarkerShape("ring", "ring", "M7.31 0.03L8.66 0L10.09 0.25L11.16 0.63L12.03 1.06L13.03 1.75L14.25 2.97L14.94 3.97L15.38 4.84L15.75 5.91L16 7.34L16 8.66L15.75 10.09L15.38 11.16L14.94 12.03L14.25 13.03L13.03 14.25L12.03 14.94L11.16 15.38L10.09 15.75L8.69 15.97L7.34 16L5.91 15.75L4.84 15.38L3.97 14.94L2.97 14.25L1.75 13.03L1.06 12.03L0.63 11.16L0.25 10.09L0 8.66L0 7.34L0.25 5.91L0.63 4.84L1.06 3.97L1.75 2.97L2.97 1.75L3.97 1.06L4.84 0.63L5.91 0.25L6.78 0.06L7.28 0.06ZM7.59 3.19L6.84 3.31L5.72 3.75L4.97 4.25L4.25 4.97L3.75 5.72L3.31 6.84L3.19 7.59L3.19 8.41L3.31 9.16L3.75 10.28L4.25 11.03L4.97 11.75L5.72 12.25L6.84 12.69L7.59 12.81L8.41 12.81L9.16 12.69L10.28 12.25L11.03 11.75L11.75 11.03L12.25 10.28L12.69 9.16L12.81 8.41L12.81 7.59L12.69 6.84L12.25 5.72L11.75 4.97L11.03 4.25L10.28 3.75L9.16 3.31L8.41 3.19L7.66 3.19Z"),
        MarkerShape("crescent", "crescent", "M7.31 0.03L9.22 0.06L10.09 0.25L11.31 0.72L9.78 1.13L8.66 1.69L7.78 2.31L6.75 3.34L6.06 4.34L5.69 5.09L5.38 5.97L5.13 7.28L5.13 8.72L5.38 10.03L6.06 11.66L6.75 12.66L7.78 13.69L8.66 14.31L9.78 14.88L11.31 15.28L9.97 15.75L8.66 16L6.78 15.94L5.16 15.5L3.97 14.94L2.97 14.25L2.41 13.75L1.56 12.78L1.06 12.03L0.63 11.16L0.25 10.09L0.06 9.22L0 7.34L0.25 5.91L0.63 4.84L1.06 3.97L1.75 2.97L2.97 1.75L3.97 1.06L4.84 0.63L5.91 0.25L6.78 0.06L7.28 0.06Z"),
        MarkerShape("semicircle_up", "semicircleUp", "M7.31 8.03L8.66 8L10.09 8.25L11.16 8.63L12.03 9.06L13.03 9.75L14.25 10.97L14.94 11.97L15.5 13.16L15.94 14.78L15.97 16L8.72 16L0 15.97L0.06 14.78L0.25 13.91L0.63 12.84L1.06 11.97L1.75 10.97L2.25 10.41L3.22 9.56L3.97 9.06L4.84 8.63L6.16 8.19L7.28 8.06Z"),
        MarkerShape("semicircle_down", "semicircleDown", "M0 0.03L15.97 0L15.94 1.22L15.75 2.09L15.38 3.16L14.94 4.03L14.44 4.78L13.69 5.66L13.03 6.25L12.03 6.94L10.84 7.5L9.22 7.94L8.66 8L6.78 7.94L5.91 7.75L4.84 7.38L3.97 6.94L2.97 6.25L1.75 5.03L1.06 4.03L0.63 3.16L0.25 2.09L0.06 1.22L0 0.09Z"),
        MarkerShape("s_letter", "sLetter", "M4.06 0.03L11.91 0L12.56 0.72L13 1.47L13.06 2.28L12.81 2.91L12.41 3.31L11.91 3.56L11.34 3.63L10.72 3.44L10.13 2.91L9.88 2.47L9.53 2.13L9.03 1.81L8.47 1.63L7.53 1.63L6.97 1.81L6.47 2.13L5.88 2.84L5.63 3.53L5.69 4.72L6.13 5.53L6.47 5.88L7.28 6.31L9.16 6.5L9.84 6.69L10.91 7.19L11.53 7.63L12.38 8.47L12.81 9.09L13.31 10.16L13.5 10.84L13.63 11.84L13.56 12.84L13.31 13.84L12.81 14.91L12.38 15.53L11.94 15.97L4.09 16L3.44 15.28L3 14.53L2.94 13.72L3.19 13.09L3.59 12.69L4.09 12.44L4.66 12.38L5.28 12.56L5.88 13.09L6.13 13.53L6.47 13.88L6.97 14.19L7.53 14.38L8.47 14.38L9.03 14.19L9.53 13.88L10.13 13.16L10.38 12.47L10.31 11.28L9.88 10.47L9.53 10.13L8.72 9.69L6.84 9.5L6.16 9.31L5.09 8.81L4.47 8.38L3.63 7.53L3.19 6.91L2.69 5.84L2.5 5.16L2.38 4.16L2.44 3.16L2.69 2.16L3.19 1.09L3.63 0.47L4.03 0.06Z"),
        MarkerShape("triangle_up", "triangleUp", "M8 0L16 16L0 16Z"),
        MarkerShape("triangle_down", "triangleDown", "M0 0L16 0L8 16Z"),
        MarkerShape("square", "square", "M0 0L16 0L16 16L0 16Z"),
        MarkerShape("diamond", "diamond", "M8 0L16 8L8 16L0 8Z"),
        MarkerShape("wide_rect", "wideRect", "M0 5.33L16 5.33L16 10.67L0 10.67Z"),
        MarkerShape("narrow_rect", "narrowRect", "M5.33 0L10.67 0L10.67 16L5.33 16Z"),
        MarkerShape("trapezoid", "trapezoid", "M3.52 0L12.48 0L16 16L0 16Z"),
        MarkerShape("trapezoid_inv", "trapezoidInv", "M0 0L16 0L12.48 16L3.52 16Z"),
        MarkerShape("star", "star", "M8 0L10.26 4.89L15.61 5.53L11.65 9.19L12.7 14.47L8 11.84L3.3 14.47L4.35 9.19L0.39 5.53L5.74 4.89Z"),
        MarkerShape("cross", "cross", "M5.28 0L10.72 0L10.72 5.28L16 5.28L16 10.72L10.72 10.72L10.72 16L5.28 16L5.28 10.72L0 10.72L0 5.28L5.28 5.28Z"),
        MarkerShape("x_cross", "xCross", "M0 0.03L3.59 0L8.03 4.38L12.41 0L15.97 0L16 3.59L11.63 8.03L16 12.41L16 15.97L12.41 16L7.97 11.63L3.59 16L0.03 16L0 12.41L4.38 7.97L0 3.59L0 0.09Z"),
        MarkerShape("arrow_up", "arrowUp", "M8 0L16 7.2L11.52 7.2L11.52 16L4.48 16L4.48 7.2L0 7.2Z"),
        MarkerShape("arrow_down", "arrowDown", "M8 16L16 8.8L11.52 8.8L11.52 0L4.48 0L4.48 8.8L0 8.8Z"),
        MarkerShape("lightning", "lightning", "M9.92 0L1.92 9.28L6.72 9.28L4.8 16L14.08 6.4L8.96 6.4L12.8 0Z"),
        MarkerShape("hourglass", "hourglass", "M0 0L16 0L8 8ZM0 16L16 16L8 8Z"),
        MarkerShape("l_shape", "lShape", "M0 0L6.08 0L6.08 9.92L16 9.92L16 16L0 16Z"),
        MarkerShape("square_frame", "squareFrame", "M0 0L16 0L16 16L0 16ZM4.48 4.48L11.52 4.48L11.52 11.52L4.48 11.52Z"),
        MarkerShape("triangle_frame", "triangleFrame", "M8 0L16 16L0 16ZM8 4.05L12.96 13.97L3.04 13.97Z"),
        MarkerShape("diamond_frame", "diamondFrame", "M8 0L16 8L8 16L0 8ZM8 3.04L12.96 8L8 12.96L3.04 8Z"),
        MarkerShape("pacman", "pacman", "M7.31 0.03L9.22 0.06L10.09 0.25L11.16 0.63L12.03 1.06L13.03 1.75L14.25 2.97L14.75 3.72L14.72 3.81L8.06 7.97L8.16 8.13L14.75 12.22L14.44 12.78L13.75 13.59L12.66 14.5L12.03 14.94L11.16 15.38L10.09 15.75L9.22 15.94L7.34 16L5.91 15.75L4.84 15.38L3.97 14.94L2.97 14.25L2.41 13.75L1.75 13.03L1.06 12.03L0.5 10.84L0.06 9.22L0 7.34L0.25 5.91L0.63 4.84L1.06 3.97L1.75 2.97L2.25 2.41L2.97 1.75L3.97 1.06L4.84 0.63L5.91 0.25L6.78 0.06L7.28 0.06Z"),
        MarkerShape("pie_wedge", "pieWedge", "M0 0.03L0.97 0L2.97 0.25L4.53 0.63L5.97 1.13L8.53 2.44L9.78 3.31L11.16 4.5L12.13 5.53L13.06 6.72L13.94 8.09L14.88 10.03L15.38 11.47L15.75 13.03L15.94 14.28L16 15.97L0.03 16L0 0.09Z"),
        MarkerShape("bowtie", "bowtie", "M0 0L8 8L0 16ZM16 0L8 8L16 16Z"),
        MarkerShape("crescent_right", "crescentRight", "M7.31 0.03L9.22 0.06L10.84 0.5L12.03 1.06L13.03 1.75L13.59 2.25L14.25 2.97L14.94 3.97L15.5 5.16L15.94 6.78L16 8.66L15.75 10.09L15.38 11.16L14.94 12.03L14.25 13.03L13.03 14.25L12.03 14.94L11.16 15.38L10.09 15.75L8.66 16L7.34 16L7.28 15.94L6.78 15.94L5.91 15.75L4.69 15.28L6.22 14.88L7.34 14.31L8.22 13.69L9.25 12.66L9.94 11.66L10.31 10.91L10.63 10.03L10.88 8.72L10.88 7.28L10.63 5.97L9.94 4.34L9.25 3.34L8.22 2.31L7.34 1.69L6.22 1.13L4.69 0.72L5.91 0.25L6.78 0.06L7.28 0.06Z"),
        MarkerShape("s_mirror", "sMirror", "M4.06 0.03L11.91 0L12.56 0.72L13.19 1.84L13.56 3.16L13.56 3.78L13.63 3.84L13.56 4.84L13.19 6.16L12.81 6.91L12.38 7.53L11.28 8.56L10.16 9.19L8.84 9.56L7.53 9.63L6.84 9.88L6.13 10.47L5.81 10.97L5.63 11.53L5.63 12.47L5.81 13.03L6.13 13.53L6.84 14.13L7.53 14.38L8.47 14.38L9.16 14.13L9.88 13.53L10.13 13.09L10.53 12.69L10.84 12.5L11.59 12.38L12.09 12.5L12.41 12.69L12.81 13.09L13 13.47L13.06 14.28L12.81 14.91L12.38 15.53L11.94 15.97L4.09 16L3.44 15.28L2.81 14.16L2.44 12.84L2.44 12.22L2.38 12.16L2.44 11.16L2.81 9.84L3.19 9.09L3.63 8.47L4.72 7.44L5.84 6.81L7.16 6.44L8.47 6.38L9.16 6.13L9.88 5.53L10.19 5.03L10.38 4.47L10.38 3.53L10.19 2.97L9.88 2.47L9.16 1.88L8.47 1.63L7.53 1.63L6.84 1.88L6.13 2.47L5.88 2.91L5.47 3.31L5.16 3.5L4.41 3.63L3.91 3.5L3.59 3.31L3.19 2.91L3 2.53L2.94 1.72L3.19 1.09L3.63 0.47L4.03 0.06Z"),
        MarkerShape("l_mirror", "lMirror", "M16 0L9.92 0L9.92 9.92L0 9.92L0 16L16 16Z"),
        MarkerShape("triangle_left", "triangleLeft", "M0 8L16 0L16 16Z"),
        MarkerShape("triangle_right", "triangleRight", "M16 8L0 0L0 16Z"),
        MarkerShape("semicircle_left", "semicircleLeft", "M15.31 0.03L16 0.03L16 15.97L14.78 15.94L13.16 15.5L11.97 14.94L10.72 14L9.75 13.03L9.06 12.03L8.63 11.16L8.25 10.09L8 8.66L8 7.34L8.25 5.91L8.63 4.84L9.06 3.97L9.75 2.97L10.97 1.75L11.97 1.06L13.16 0.5L14.16 0.19L15.28 0.06Z"),
        MarkerShape("semicircle_right", "semicircleRight", "M0 0.03L0.66 0L2.09 0.25L3.16 0.63L4.03 1.06L5.03 1.75L6.25 2.97L6.94 3.97L7.38 4.84L7.75 5.91L7.94 6.78L8 7.34L7.94 9.22L7.5 10.84L6.94 12.03L6.25 13.03L5.69 13.66L4.78 14.44L4.03 14.94L3.16 15.38L2.09 15.75L1.22 15.94L0 15.97L0 0.09Z"),
        MarkerShape("arrow_left", "arrowLeft", "M0 8L7.2 0L7.2 4.48L16 4.48L16 11.52L7.2 11.52L7.2 16Z"),
        MarkerShape("arrow_right", "arrowRight", "M16 8L8.8 0L8.8 4.48L0 4.48L0 11.52L8.8 11.52L8.8 16Z"),
        MarkerShape("y_shape", "yShape", "M0.63 1.97L1.47 1.94L2.03 2.13L7.91 5.56L8.22 5.5L13.97 2.13L14.53 1.94L15.34 1.94L15.97 2.19L16 5.78L11.44 8.41L10.06 9.22L10.06 15.97L5.97 16L5.94 9.22L0 5.78L0 2.22L0.41 2L0.59 2Z"),
        MarkerShape("u_shape", "uShape", "M0 0L4 0L4 12.16L12 12.16L12 0L16 0L16 16L0 16Z"),
        MarkerShape("t_shape", "tShape", "M0 0L16 0L16 5.28L10.72 5.28L10.72 16L5.28 16L5.28 5.28L0 5.28Z"),
        MarkerShape("chevron", "chevron", "M0 12L8 4.8L16 12L16 15.68L8 8.48L0 15.68Z"),
        MarkerShape("double_vbar", "doubleVbar", "M0 0L5.44 0L5.44 16L0 16ZM10.56 0L16 0L16 16L10.56 16Z"),
        MarkerShape("double_hbar", "doubleHbar", "M0 0L16 0L16 5.44L0 5.44ZM0 10.56L16 10.56L16 16L0 16Z"),
        MarkerShape("parallelogram", "parallelogram", "M6.08 0L16 0L9.92 16L0 16Z"),
    )

    private val byId: Map<String, MarkerShape> = all.associateBy { it.id }

    /**
     * **The one shape that is never an ordinary marker: the card marked T.**
     *
     * The host-setup screens have always said *scan the card marked T* and *the T card is never an
     * ordinary marker*, and something has to make that true of a piece of paper. A card's payload
     * carries a version, a shape and an id (D-069) and nothing else, so the shape is the only field
     * that can say what kind of card this is — and `t_shape` is literally the letter T, which is
     * what is printed on the card the host is holding.
     *
     * Reserving it costs the ordinary roster one of its 44 shapes and buys the terminal an
     * identity that survives a reprint: a T card found behind a shelf a year later still says
     * terminal, and [MarkerCard.isTerminal] is a fact about the paper rather than a flag somebody
     * set. [all] keeps all 44 entries because the roster is wire data and ids are never renumbered
     * or reused — this is a shape that is spoken for, not a shape that was removed.
     *
     * **This is a provisional ruling and is written up for ratification.** It decides what is
     * printed on paper, and paper cannot be patched.
     */
    val TERMINAL: MarkerShape = requireNotNull(byId["t_shape"]) { "the roster lost t_shape" }

    /**
     * The shapes an ordinary marker card may carry — the roster minus [TERMINAL].
     *
     * What the printable sheet (story 4.11) draws from, and the answer to "how many markers can
     * one home hold" that is not off by one.
     */
    val registrable: List<MarkerShape> = all.filterNot { it.id == TERMINAL.id }

    init {
        require(ALPHABET.length == 44) { "alphabet is ${ALPHABET.length}, expected 44" }
        require(all.size <= ALPHABET.length) {
            "${all.size} shapes exceeds the ${ALPHABET.length}-character alphabet"
        }
        require(all.map { it.id }.toSet().size == all.size) { "duplicate shape id" }
    }

    operator fun get(id: String): MarkerShape? = byId[id]

    /**
     * Lookup that refuses to return nothing.
     *
     * [get] returning null is right for a scan — an unregistered or misread card is a fact about
     * a piece of paper (D-071). It is wrong everywhere the id is a constant written by us: a typo
     * then yields a marker with no shape, and a shape is a marker's whole name. Where the caller
     * wraps the result in `listOfNotNull`, the typo does not even leave a gap — the list is
     * simply one shorter, and nobody counts it.
     */
    fun require(id: String): MarkerShape = byId[id]
        ?: throw IllegalArgumentException(
            "no shape '$id'. The roster is fixed at ${all.size} and ids are never renumbered or " +
                "reused, so this is a typo rather than a missing shape."
        )

    /** Shape index to its single character. */
    fun encode(index: Int): Char {
        require(index in all.indices) { "no shape at index $index" }
        return ALPHABET[index]
    }

    /** A single character back to its shape, or null if the character maps to no shape. */
    fun decode(code: Char): MarkerShape? {
        val i = ALPHABET.indexOf(code)
        return if (i < 0 || i >= all.size) null else all[i]
    }
}
