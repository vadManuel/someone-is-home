package home.someoneshome.model

/*
 * **The ten questions, as numbers — and this file is the only place their shape is written down.**
 *
 * A Subroutine instance is drawn by the house at scan time and rendered by the phone that scanned:
 * the parameters ride the scan, the client draws the picture from them, the tap comes back, and the
 * house grades it (D-139, D-140). That arrangement has one failure mode and it is the expensive
 * one: **the two ends disagreeing about what the numbers mean.** A client that read the band's
 * width from a fixture of its own would reject taps the player watched land inside the band, and
 * nothing in either module would look wrong.
 *
 * So the readers live here, in `model`, where the house and the phone both see them. `core` draws
 * against the same lengths and the same bounds it refuses outside of; `ui` renders from the reader's
 * output and never from a second copy of a number. **It is impossible to deal an instance a client
 * would refuse** — and the way that is checked is that the house's draw is fed to these readers a
 * few thousand times over (`SubroutineDrawTest`).
 *
 * ### What is NOT here, and must never be
 *
 * **No answer, and nothing one `min()` or one subtraction away from an answer.** These types hold
 * the *question*: the grid as drawn, the columns as dealt, the band as it sits on the bar. Which
 * cell breaks the pattern, which group was bigger, how many dots are owed and whether a tap landed
 * inside the band are all derived in `core`, which `ui` cannot see — and that is the whole reason
 * the derivations are there rather than beside the shapes they read. See `Subroutines.kt` in `core`.
 */
/**
 * **The parity grid: a regular pattern with exactly one cell breaking it, and no memory of which.**
 *
 * The design's Parity Check is *a grid of filled/empty cells; tap the one breaking the pattern* —
 * singular. The pattern is a checkerboard, which is the only regular arrangement that survives the
 * input vocabulary: at four luminance steps of amber, with no hue discrimination, what is left is
 * position, shape, count, size, order and presence/absence, and a checkerboard is presence and
 * position and nothing else.
 *
 * ### The generator forgets the answer, and that is the whole design of this object
 *
 * [of] returns the cells and nothing else. The index it flipped is a local, discarded on return,
 * so the screen drawing this grid **cannot** mark the odd cell however carelessly it is later
 * edited — the same discipline as `Observation` versus `ObservationView`: the client-facing type
 * is not the authority's type with a field hidden, it is a type that never had the field. A
 * comment saying "do not draw the answer" is not a boundary.
 *
 * The answer is not *secret* — it is on screen, in plain sight, and finding it is the work. What
 * must stay off the device is the **judgement**: whether the cell this player touched was that
 * one. See `SequenceEntry` for why that line is where it is.
 *
 * Deterministic from [seed] with no randomness at draw time (rule 4): the same seed is the same
 * grid on every render, on every replay, on every phone.
 */
object ParityGrid {

    const val COLUMNS = 6
    const val ROWS = 6
    const val SIZE = COLUMNS * ROWS

    /** How many numbers a Parity Check instance is: the grid's seed, and nothing else. */
    const val LENGTH: Int = 1

    /** The checkerboard this grid is a corruption of. True is a filled cell. */
    fun clean(at: Int): Boolean = (at / COLUMNS + at % COLUMNS) % 2 == 0

    /**
     * The grid as drawn: [clean], with the cell at `seed mod SIZE` inverted.
     *
     * The corner cells are as valid a hiding place as the middle ones and are not excluded. A
     * generator that avoided the edges would teach players where never to look, which is a
     * difficulty setting nobody chose.
     *
     * There is no seed this cannot draw, so the only refusal is the length — and it is here rather
     * than nowhere because a reader per kind is what makes *every* instance the house deals
     * something a client has agreed to.
     */
    fun of(parameters: List<Int>): List<Boolean> {
        if (parameters.size != LENGTH) {
            throw MalformedSubroutineParameters(
                "PARITY CHECK is $LENGTH number — the grid's seed — and this is " +
                    "${parameters.size}: $parameters",
            )
        }
        val odd = parameters[0].mod(SIZE)
        return List(SIZE) { at -> if (at == odd) !clean(at) else clean(at) }
    }
}

/**
 * **The Signal Trace graph: a lattice, a route that reaches across it, and some branches that do
 * not — and, like [ParityGrid], it forgets the route before it returns.**
 *
 * The design's Signal Trace *ships with BFS-generated graphs, so the optimum is computed rather
 * than authored, and difficulty tunes by decoy count*. [of] builds a walk from source to sink to
 * guarantee the graph is connected at all, hangs [DECOYS] branches off it, and then **returns only
 * the nodes and the edges.** The walk it used is a local; it is not the shortest route in general,
 * because a decoy touching two distant points of it makes a shorter one — which is exactly why the
 * optimum has to be computed. Nothing that computes it is on the phone.
 *
 * ### Why a lattice
 *
 * Node layout is not a detail the design left out by accident, it is the whole legibility question:
 * at four luminance steps of amber, with no hue, a graph is read by **position** and nothing else,
 * and edges that cross look like nodes that do not exist. A lattice with edges only between
 * orthogonally adjacent cells cannot produce a crossing, cannot produce two nodes close enough to
 * be one, and puts every node on a grid a thumb can find in the dark without looking. A prettier
 * force-directed layout would be a different Subroutine in a lit room.
 *
 * [source] and [sink] are returned because they are the *question* — both are drawn, and a route
 * with no visible ends is not a route. The answer is which way across.
 *
 * Deterministic from [seed] with no randomness at draw time (rule 4).
 */
object SignalGraph {

    /** How wide the lattice is, in columns. Four is the visual decision — see the note in [of]. */
    const val COLUMNS = 4

    /** How tall, in rows. */
    const val ROWS = 3

    /**
     * **A placeholder, and specifically not a tuning value.**
     *
     * The design says difficulty tunes by decoy count, which makes the real number balance, which
     * is out of scope here in the way every balance number is. Three is a neutral fixture that
     * fills a four-by-three lattice enough to make the route non-obvious and not so much that
     * every cell is occupied. Nothing locks it at arming and nobody has played with it.
     */
    const val DECOYS = 3

    /** The row the signal enters and leaves on: the middle one, so a route can bend either way. */
    const val ENTRY_ROW = 1

    /** Where a node sits on the lattice. Not a position on screen — the screen owns that. */
    data class Node(val column: Int, val row: Int)

    /**
     * A drawable graph: nodes, the pairs of node indices joined by an edge, and the two ends.
     *
     * **There is no route in here.** A screen holding this cannot draw the answer however
     * carelessly it is later edited, for the same reason [ParityGrid]'s cells cannot.
     */
    class Wiring(
        val nodes: List<Node>,
        val edges: List<Pair<Int, Int>>,
        val source: Int,
        val sink: Int,
    ) {
        /** The nodes joined to [at]. Adjacency is drawn, so asking for it reveals nothing. */
        fun joinedTo(at: Int): List<Int> =
            edges.mapNotNull { (a, b) -> if (a == at) b else if (b == at) a else null }
    }

    private fun adjacent(a: Node, b: Node): Boolean {
        val steps = kotlin.math.abs(a.column - b.column) + kotlin.math.abs(a.row - b.row)
        return steps == 1
    }

    /** How many numbers a Signal Trace instance is: the lattice's seed, and nothing else. */
    const val LENGTH: Int = 1

    /**
     * The graph as drawn, from the one number the house sends.
     *
     * The only refusal is the length, for [ParityGrid.of]'s reason — every seed lays out a graph
     * this screen can draw, and **whether that graph has one shortest route through it is the
     * house's problem**, settled where the instance is drawn rather than here. A client asked to
     * refuse an ambiguous lattice would be a client computing the optimum, which is the one thing
     * this object exists not to do.
     */
    fun of(parameters: List<Int>): Wiring {
        if (parameters.size != LENGTH) {
            throw MalformedSubroutineParameters(
                "SIGNAL TRACE is $LENGTH number — the lattice's seed — and this is " +
                    "${parameters.size}: $parameters",
            )
        }
        val seed = parameters[0]
        // A walk from source to sink, one lattice step at a time: up or down inside a column,
        // then across to the next. It exists to guarantee the sink is reachable at all, and it is
        // thrown away at the end of this function.
        // One base-ROWS digit of the seed per middle column, so consecutive seeds bend differently.
        var digits = seed
        val turns = List(COLUMNS) { column ->
            if (column == 0 || column == COLUMNS - 1) {
                ENTRY_ROW
            } else {
                val row = digits.mod(ROWS)
                digits /= ROWS
                row
            }
        }
        val walked = LinkedHashSet<Node>()
        walked += Node(0, turns[0])
        for (column in 0 until COLUMNS - 1) {
            val from = turns[column]
            val to = turns[column + 1]
            val step = if (to >= from) 1 else -1
            var row = from
            while (row != to) {
                row += step
                walked += Node(column, row)
            }
            walked += Node(column + 1, to)
        }

        // Decoys hang off the walk rather than floating: a node with no edge is a dot drawn in
        // the middle of nothing, which reads as a rendering fault rather than as a blind alley.
        val candidates = buildList {
            for (column in 0 until COLUMNS) {
                for (row in 0 until ROWS) {
                    val cell = Node(column, row)
                    if (cell !in walked && walked.any { adjacent(it, cell) }) add(cell)
                }
            }
        }
        val chosen = LinkedHashSet(walked)
        if (candidates.isNotEmpty()) {
            var at = seed.mod(candidates.size)
            var tried = 0
            while (chosen.size < walked.size + DECOYS && tried < candidates.size) {
                chosen += candidates[at]
                at = (at + 1).mod(candidates.size)
                tried++
            }
        }

        val nodes = chosen.sortedWith(compareBy({ it.column }, { it.row }))
        val edges = buildList {
            for (a in nodes.indices) {
                for (b in a + 1 until nodes.size) {
                    if (adjacent(nodes[a], nodes[b])) add(a to b)
                }
            }
        }
        return Wiring(
            nodes = nodes,
            edges = edges,
            source = nodes.indexOf(Node(0, ENTRY_ROW)),
            sink = nodes.indexOf(Node(COLUMNS - 1, ENTRY_ROW)),
        )
    }
}

/**
 * **A scan parameter this client cannot read as the question it is supposed to be.**
 *
 * The house draws an instance's parameters at scan time and the client renders from them (D-139's
 * pattern, and the plumbing L4 built). This is what happens when what arrives is not a question:
 * two Sniff groups of the same size, a column of no dots, a list of the wrong length.
 *
 * ### Loud, and at the READ rather than at the draw
 *
 * The house map's format is the precedent and the reasoning is the same one (`HouseMapText` —
 * *strict, and loud*): a reader that quietly skipped what it did not understand hands back
 * something that looks complete and is not, and the fault surfaces as a player standing in the
 * dark being asked an impossible question. So nothing is defaulted, nothing is coerced, and every
 * failure names the fault.
 *
 * **It is thrown where the parameters are read, and never from a draw.** Rule 6 is that errors are
 * silent to the player, and rule 5 is that no error path may touch the lamp: a screen that threw
 * mid-composition would blank, and a phone that blanks in a dark house is indistinguishable from a
 * revocation. Today the read happens once, at class-load, against the port's own fixtures — so a
 * malformed one fails the build. **When the house's push exists this belongs at the effect
 * boundary, with the rest of the arrival**, and the worklog says so.
 */
class MalformedSubroutineParameters(val detail: String) :
    IllegalArgumentException("the house asked something this client cannot draw: $detail")

/**
 * **Sniff's question: two groups of buzzes with a pause between them, and one of them is bigger.**
 *
 * D-137 supersedes `gdd.md:568`. The phone buzzes **two haptic groups separated by a pause** and
 * the player answers **which group was bigger** — pure perception, no arithmetic to carry, no
 * numeral on screen, and nothing for the player to count *to*. It is the roster's only short dark
 * (`gdd.md:580`), the quick one a Resident can take without becoming a beacon.
 *
 * ### It holds the question and not the answer, and that is not the same sentence twice
 *
 * *Which group was bigger* is a fact about these two numbers, so this type **can** be asked it —
 * and must never be. It is the [ParityGrid] situation rather than the `SequenceEntry` one: the
 * answer is not secret (a player who felt both groups knows it) and it is the *judgement* that
 * stays on the house. What keeps the judgement off the phone is that `ChoiceEntry` holds the
 * player's answer, this holds the question, and **nothing in `ui` is given both** — no screen, no
 * model, no function takes a `SniffGroups` and a `ChoiceEntry` and compares them. There is no
 * `wasRight` here and there must never be one.
 *
 * ### Equal groups are refused, loudly (D-137)
 *
 * *The answer must exist and must be unique — a tie is a coin flip the house would then grade, and
 * D-109 grades entries on their merits or not at all.* So a tie is not clamped, nudged or answered
 * arbitrarily; it is a malformed question and it is said so. **The stand-in draw in `core` can
 * still produce one** — it draws three numbers out of one range with no idea what they mean
 * (`Scanning.kt`, E-L4-3) — which is exactly why this refusal has work to do rather than being a
 * defensive flourish.
 *
 * ### Every buzz in the script is the same length, and the length is not this file's
 *
 * A script whose pulses varied would be a rhythm, and D-137 makes Sniff a magnitude judgment
 * precisely so there is no pattern to hold: what the player counts is *how many*, never *how they
 * went*. The pulse itself is [HouseBuzz.SHORT_MILLIS] — **there is one short buzz in this app**,
 * not one per Subroutine, and [HapticStep] now lives beside it in `model` because `platform` sees
 * `model` and never `ui` (E-S1-2, closed by the device unit exactly as the flag predicted).
 */
class SniffGroups private constructor(
    /** How many buzzes are in the first group. */
    val first: Int,
    /** How many are in the second. Never equal to [first]. */
    val second: Int,
    /** How long the phone is still between the two groups. The thing that makes them two. */
    val gapMillis: Int,
) {

    /**
     * The pattern, as the device unit will play it: the first group, the pause, the second group.
     *
     * Ends on a [HapticStep.Buzz] rather than on a trailing rest — a script that finished with
     * silence would be a script whose end the player cannot feel, and *when the question is over*
     * is the moment they are expected to answer.
     */
    val script: List<HapticStep> = buildList {
        repeat(first) {
            if (isNotEmpty()) add(HapticStep.Rest(PULSE_GAP_MILLIS))
            add(HapticStep.Buzz(PULSE_MILLIS))
        }
        add(HapticStep.Rest(gapMillis))
        repeat(second) {
            add(HapticStep.Buzz(PULSE_MILLIS))
            if (it < second - 1) add(HapticStep.Rest(PULSE_GAP_MILLIS))
        }
    }

    companion object {

        /**
         * **One buzz, and the same one every time — the app's one short buzz, not a copy of it.**
         *
         * It was a literal `90` here, beside `HouseBuzz.SHORT_MILLIS`'s `90`, two constants for
         * one fact agreeing by hand. That is the arrangement D-106 already lost once, and the
         * failure mode here is the one D-102 is about: a Subroutine pulse that drifted a few
         * milliseconds from the house's short buzz would make *which kind of buzz was that* a
         * question a room could start answering.
         */
        const val PULSE_MILLIS: Int = HouseBuzz.SHORT_MILLIS

        /** The silence between two buzzes of the same group. Even, so the group carries no rhythm. */
        const val PULSE_GAP_MILLIS: Int = 220

        /** How many numbers a Sniff instance is: two group sizes and the pause between them. */
        const val LENGTH: Int = 3

        /**
         * **The most buzzes a group may hold.**
         *
         * Not the difficulty knob — *playtest owns the gap between the two group sizes* (D-137)
         * and the gap is what makes the comparison easy or hard. This is the ceiling on the
         * *duration*: seven short buzzes and their gaps is a little over two seconds of one group,
         * and the roster's only short dark has to stay short, because the whole of what it is for
         * is being takeable without becoming a beacon.
         */
        const val MOST_BUZZES: Int = 7

        /**
         * The pause arrives in **tenths of a second**, so the one duration on the wire is a small
         * integer like every other parameter. Nothing on the wire is ever milliseconds.
         */
        const val GAP_TENTHS: Int = 100

        /**
         * Read a scan's parameters as a Sniff question, or refuse them by name.
         *
         * Four ways to be malformed and only one of them is D-137's: a list of the wrong length, a
         * group with no buzzes in it, no pause between the groups — and **two groups of the same
         * size**, which is the one the ruling is about.
         */
        fun of(parameters: List<Int>): SniffGroups {
            if (parameters.size != LENGTH) {
                throw MalformedSubroutineParameters(
                    "SNIFF is $LENGTH numbers — two group sizes and the pause — and this is " +
                        "${parameters.size}: $parameters",
                )
            }
            val (first, second, gapTenths) = parameters
            if (first < 1 || second < 1) {
                throw MalformedSubroutineParameters(
                    "SNIFF was given a group of $first and a group of $second, and a group with " +
                        "no buzzes in it is not a group",
                )
            }
            if (first > MOST_BUZZES || second > MOST_BUZZES) {
                throw MalformedSubroutineParameters(
                    "SNIFF was given groups of $first and $second and a group holds at most " +
                        "$MOST_BUZZES — the roster's only short dark stops being short",
                )
            }
            if (first == second) {
                throw MalformedSubroutineParameters(
                    "SNIFF was given two groups of $first. D-137: equal groups never occur — the " +
                        "answer must exist and must be unique, and a tie is a coin flip the house " +
                        "would then grade",
                )
            }
            if (gapTenths < 1) {
                throw MalformedSubroutineParameters(
                    "SNIFF was given a pause of $gapTenths tenths of a second, and two groups " +
                        "with no pause between them are one group",
                )
            }
            return SniffGroups(first, second, gapTenths * GAP_TENTHS)
        }
    }
}

/**
 * **Deallocate's question: the columns as they were dealt, and no opinion about what is owed.**
 *
 * D-138 supersedes `gdd.md:569`. A tap removes one dot from the tapped column and evening out
 * means bringing every column **down to the shortest** — so the answer is unique, the work is
 * countable, and *the verb is the fiction.* The columns carry the arithmetic and nothing on the
 * screen is a numeral (`gdd.md:588`).
 *
 * ### [of] returns the heights and computes nothing from them
 *
 * There is deliberately **no `level`, no `excess` and no `owed` in this file.** Every one of them
 * is one line and every one of them is the answer: the required entry is the sum of each column's
 * excess over the shortest, so a helper that returned the shortest column would put the answer key
 * on the phone, one `min()` from a screen that could then tell a player when to stop. The answer
 * is not secret — the columns are drawn and reading them is the work — but the *judgement* is the
 * house's, exactly as it is for [ParityGrid]'s odd cell.
 *
 * ### The refusals are the panel's capacity and nothing more
 *
 * A column that will not fit on the panel is not a harder Subroutine, it is a **different question
 * from the one the house asked**: dots clipped off the top of a column change the height a player
 * reads, and they would then answer the picture correctly and be graded wrong. So a distribution
 * that cannot be drawn is refused by name rather than truncated.
 *
 * **Equal columns are NOT refused.** A distribution that arrives already level asks for no
 * removals, which is a trivial instance rather than a malformed one — and refusing it would be
 * this client deciding how much work a piece of work has to contain, which is balance and is not
 * its to decide. `gdd.md:569` says *unequal*; D-138 does not, and the difference is left where the
 * ruling left it.
 */
object DotColumns {

    /**
     * **The panel's capacity, not a difficulty setting.**
     *
     * A column strip has to be at least `TAP_TARGET` wide (D-205: 44pt on the narrowest phone,
     * 42.24 units of the 360 canvas) — it is pressed one-handed, in the dark, by somebody watching
     * a doorway — and six strips with the gaps between them stand 312.24 units wide in a 320-unit
     * body, where a seventh would not fit. [MOST_DOTS] is the same question vertically: seven
     * blocks and their gaps stand 246 units tall in the screen's 260-unit field.
     */
    const val LEAST_COLUMNS: Int = 2
    const val MOST_COLUMNS: Int = 6
    const val MOST_DOTS: Int = 7

    /** Read a scan's parameters as a column distribution, or refuse them by name. */
    fun of(parameters: List<Int>): List<Int> {
        if (parameters.size !in LEAST_COLUMNS..MOST_COLUMNS) {
            throw MalformedSubroutineParameters(
                "DEALLOCATE was dealt ${parameters.size} column(s); this panel draws " +
                    "$LEAST_COLUMNS to $MOST_COLUMNS, and one column is already level",
            )
        }
        val short = parameters.withIndex().firstOrNull { it.value < 1 }
        if (short != null) {
            throw MalformedSubroutineParameters(
                "DEALLOCATE was dealt ${short.value} dot(s) in column ${short.index}, and a " +
                    "column with no dots in it is a column that is not there: $parameters",
            )
        }
        val tall = parameters.withIndex().firstOrNull { it.value > MOST_DOTS }
        if (tall != null) {
            throw MalformedSubroutineParameters(
                "DEALLOCATE was dealt ${tall.value} dots in column ${tall.index} and this panel " +
                    "draws $MOST_DOTS — a clipped column is a different question from the one " +
                    "the house asked: $parameters",
            )
        }
        return parameters
    }
}

/**
 * **Interrupt's question: a bar, a band on it, and a sweep that bounces along it forever.**
 *
 * D-139 supersedes `gdd.md:566` in both respects. **The sweep ping-pongs** rather than wrapping —
 * there is no defunct return stroke to wait through and no discontinuity at the edge for a player to
 * time against instead of the band — and **it runs forever until tapped or abandoned.** There is no
 * timeout anywhere in this class, and the thing that taxes hesitation is the room: the screen is
 * MEDIUM and lit, the player is standing at the marker where D-110 keeps them, and every extra pass
 * is another second of being visible to whoever walks in.
 *
 * ### [at] is arithmetic, not animation, and that is what makes a replay a replay
 *
 * The house sends the parameters at scan time and **the client renders the motion deterministically
 * from them**: no authored Effect, no motion on the wire, no schema row. So the position is a pure
 * function of the parameters and the milliseconds since the screen opened — integer arithmetic
 * throughout, no floating point to round differently on two devices, no easing curve nobody
 * authored (rule 5). Two phones handed the same instance and the same clock draw the same frame.
 *
 * ### It holds the question, and the judgement is the house's
 *
 * [bandFrom] and [bandTo] are on this class because the band is drawn, and [at] is on it because
 * the sweep is drawn. *Was the tap inside the band* is therefore one expression away — and it is
 * the [SniffGroups] situation exactly: the answer is not secret, it is on the screen and catching it
 * is the work, and what must never happen on the device is the **judgement**. **Nothing in `ui` is
 * given both a sweep position and a band to compare it with** — no `caught`, no `inside`, no
 * `wasRight`, here or anywhere else — and the only place the two meet in one expression is a test
 * that says so in its own name.
 *
 * **Every re-scan re-draws band position and phase** (D-139), which is the plumbing L4 already
 * built: the instance is minted per scan, so a retry is a fresh judgment rather than a second run at
 * a picture the player has already memorised.
 */
class InterruptSweep private constructor(
    /** Where the middle of the band sits on the bar, in steps of [SPAN]. */
    val bandAt: Int,
    /**
     * **How far the band reaches either side of its middle — the house's number, not this
     * client's** (E-S2-1, closed).
     *
     * It was a constant in `ui` while nothing graded, and it could not survive the day something
     * did: the house grades *inside the band* and the phone draws the band, so a width the two ends
     * held separately would reject taps the player watched land inside it, and neither module would
     * look wrong. It rides the scan with the position now, and the band that is drawn is the band
     * that is graded because they are one number.
     */
    val bandHalf: Int,
    /** How many steps of the bar the sweep covers in a second. */
    val speed: Int,
    /** How far into the bounce the sweep already is when the screen opens, in steps of [CYCLE]. */
    val phase: Int,
) {

    /**
     * Where the sweep is [elapsedMillis] after the screen opened, as a step of [SPAN].
     *
     * The bounce is a fold: the sweep walks a [CYCLE] twice the length of the bar, and the second
     * half of it is the first half read backwards. That is ping-pong with no branch on direction
     * and no state to accumulate — which is what makes the same millisecond always the same step,
     * however many frames the phone dropped getting there.
     */
    fun at(elapsedMillis: Long): Int {
        val travelled = elapsedMillis * speed / MILLIS_PER_SECOND
        val step = (phase + travelled).mod(CYCLE.toLong()).toInt()
        return if (step <= SPAN) step else CYCLE - step
    }

    /** The near edge of the band, in steps. Drawn; never compared with anything. */
    val bandFrom: Int get() = bandAt - bandHalf

    /** The far edge. */
    val bandTo: Int get() = bandAt + bandHalf

    companion object {

        /** How many steps long the bar is. The unit every position on this screen is in. */
        const val SPAN: Int = 100

        /** A there-and-back: the sweep's period, in steps. */
        const val CYCLE: Int = SPAN * 2

        /**
         * **The narrowest and widest half-band the bar can carry.**
         *
         * Not a difficulty setting — *playtest owns the band width* (D-139) and the house draws it
         * inside these. A half-band of nothing is a band a finger cannot land in at all, which is
         * a Subroutine with no answer rather than a hard one; a half-band of a quarter of the bar
         * is half the bar lit and the sweep is inside it most of the time, which is a Subroutine
         * with no question. Eight either side — sixteen steps of a hundred — is *generous* per
         * `gdd.md:595` and is what the port's own fixture draws.
         */
        const val BAND_LEAST: Int = 4
        const val BAND_MOST: Int = SPAN / 4

        /**
         * How many numbers an Interrupt instance is: where the band is, **how wide it is**, how
         * fast the sweep goes, and how far into the bounce it starts.
         */
        const val LENGTH: Int = 4

        /**
         * Read a scan's parameters as a sweep, or refuse them by name.
         *
         * Four ways to be malformed. A band that runs off the end of the bar is a **different
         * question from the one the house asked** — the player answers the picture, correctly, and
         * is graded against a band they were never shown, which is [DotColumns]' clipped column one
         * Subroutine along. A sweep at no speed is not a timing question at all: it sits where the
         * phase put it and every tap in the round grades the same. A phase outside the cycle is the
         * one that would be tempting to wrap silently, and must not be: the house grades a position
         * this client renders, so a parameter the client quietly reinterprets is the two of them
         * disagreeing about where the sweep started.
         */
        fun of(parameters: List<Int>): InterruptSweep {
            if (parameters.size != LENGTH) {
                throw MalformedSubroutineParameters(
                    "INTERRUPT is $LENGTH numbers — the band's position, its width, the speed and " +
                        "the phase — and this is ${parameters.size}: $parameters",
                )
            }
            val (bandAt, bandHalf, speed, phase) = parameters
            if (bandHalf !in BAND_LEAST..BAND_MOST) {
                throw MalformedSubroutineParameters(
                    "INTERRUPT was given a band reaching $bandHalf either side of its middle, and " +
                        "this bar carries $BAND_LEAST to $BAND_MOST — narrower is a band no " +
                        "finger can land in, wider is a bar the sweep is inside most of the time",
                )
            }
            if (bandAt - bandHalf < 0 || bandAt + bandHalf > SPAN) {
                throw MalformedSubroutineParameters(
                    "INTERRUPT was given a band at $bandAt reaching $bandHalf either side, and " +
                        "the bar is $SPAN steps long — a band running off the end of the bar is a " +
                        "different question from the one the house asked",
                )
            }
            if (speed < 1) {
                throw MalformedSubroutineParameters(
                    "INTERRUPT was given a speed of $speed, and a sweep that does not move is not " +
                        "a moment to catch — every tap in the round would land in the same place",
                )
            }
            if (phase !in 0 until CYCLE) {
                throw MalformedSubroutineParameters(
                    "INTERRUPT was given a phase of $phase and the bounce is $CYCLE steps long. " +
                        "It is not wrapped here: the house grades a position this client draws, " +
                        "and a parameter the client reinterprets is the two of them disagreeing " +
                        "about where the sweep started",
                )
            }
            return InterruptSweep(bandAt, bandHalf, speed, phase)
        }
    }
}

/**
 * **Drift's question: a dot crossing behind cover, and a buzz that asks where it is.**
 *
 * D-140 supersedes `gdd.md:570`. *A dot drifts at constant velocity along a straight path, passes
 * behind occluders and hides. At a house-chosen instant a haptic pulse says* **now**, *and the
 * player taps where the dot is at that moment.* The tap position is the entry and the house grades
 * it against the true position within a hit radius.
 *
 * **The hidden duration is the difficulty and it is invisible** — the player cannot see how far
 * they will have to carry the dot in their head before being asked, which is the entire test and
 * the reason the answer cannot be read off the screen.
 *
 * ### It is [InterruptSweep]'s pattern with the timing the other way round
 *
 * Path, occluder layout and speed are **seeded per scan**, sent at scan time, rendered
 * deterministically by the client, and re-drawn on every re-scan. Interrupt is the player's timing
 * against a visible rhythm; this is the house's timing against the player's mental model. One asks
 * *when*, the other asks *where* — and both are one tap, so `gdd.md:596` holds for both.
 *
 * ### The layout is generated and the answer is not stored, exactly as [SignalGraph] does it
 *
 * [of] builds the lane and the cover from the seed and returns them. **There is no `answer` on this
 * class** — the true position at the pulse is [at] of [askAtMillis], which is one expression, and
 * it is the [InterruptSweep] situation for the third time: the client must render the motion, so
 * the client can compute the position; what must never happen here is the **judgement**. Nothing in
 * `ui` holds a `DriftPath` and a tap in the same expression outside a test.
 */
class DriftPath private constructor(
    /** Which of [LANES] heights the straight path runs at. The path, and the whole of it. */
    val lane: Int,
    /** What the dot passes behind, in steps of [SPAN], in the order it reaches them. */
    val cover: List<IntRange>,
    /** How many steps the dot drifts in a second. Constant — it is a drift, not a curve. */
    val speed: Int,
    /** When the phone says *now*, in milliseconds after the screen opened. */
    val askAtMillis: Int,
    /**
     * **How far from the dot a tap still counts, in steps of the lane — the house's tolerance,
     * carried on the question** (D-140).
     *
     * It rides the scan for [InterruptSweep.bandHalf]'s reason: one number, drawn once, so the
     * grading and the picture cannot drift apart. **Unlike the band it is not drawn** — it is
     * centred on a position the player is being asked for, so a radius on screen would be the
     * answer's own neighbourhood painted around the answer. It is read here, and refused here if it
     * is not a tolerance, so that the number the house grades with is a number this client has
     * agreed is a question.
     */
    val hitRadius: Int,
) {

    /**
     * Where the dot is [elapsedMillis] after the screen opened, or null once it has drifted off
     * the far end.
     *
     * Null rather than a clamp at [SPAN]: a dot parked on the end of the lane is a dot the player
     * would read as *still there*, and D-125's rule is to clamp only what a player cannot perceive.
     * It leaves, and what is owed is where it was when the phone buzzed.
     */
    fun at(elapsedMillis: Long): Int? {
        val step = (elapsedMillis * speed / MILLIS_PER_SECOND).toInt()
        return if (step > SPAN) null else step
    }

    /** Whether a step of the lane has something drawn in front of it. */
    fun covered(step: Int): Boolean = cover.any { step in it }

    /**
     * The pattern, as the device unit will play it: the wait, and then one short buzz.
     *
     * **One buzz and a short one.** D-135 reserves the long haptic for five named events and this
     * is not among them (D-140 says so in as many words), and it is the same short buzz Sniff's
     * groups are made of, because there is one of them in this app rather than one per Subroutine.
     */
    val script: List<HapticStep> = listOf(
        HapticStep.Rest(askAtMillis),
        HapticStep.Buzz(SniffGroups.PULSE_MILLIS),
    )

    companion object {

        /** How many steps long the lane is, end to end. [InterruptSweep.SPAN]'s unit, deliberately. */
        const val SPAN: Int = 100

        /** How many heights the straight path can run at. */
        const val LANES: Int = 3

        /** How many pieces of cover the dot passes behind. */
        const val COVER: Int = 3

        /** How many steps of lane are clear before the first piece of cover can start. */
        const val RUN_IN: Int = 22

        /** The narrowest and widest a piece of cover may be, in steps. */
        const val COVER_LEAST: Int = 10
        const val COVER_MOST: Int = 16

        /** How many clear steps are guaranteed between one piece of cover and the next. */
        const val CLEAR: Int = 4

        /**
         * How many numbers a Drift instance is: the layout's seed, the speed, the wait, and **the
         * hit radius the house will grade the tap against.**
         */
        const val LENGTH: Int = 4

        /**
         * The narrowest and widest hit radius, in steps of the lane.
         *
         * *Playtest owns the hit radius* (D-140) and the house draws it inside these. A radius of
         * nothing asks a blind one-handed finger for an exact step; a radius of a quarter of the
         * lane is four places the dot could be and all of them right.
         */
        const val RADIUS_LEAST: Int = 3
        const val RADIUS_MOST: Int = SPAN / 8

        /** How wide a slot each piece of cover is placed inside. */
        private const val SLOT: Int = (SPAN - RUN_IN) / COVER

        /**
         * **The hidden duration arrives in tenths of a second**, so nothing on the wire is ever
         * milliseconds — `SniffGroups.GAP_TENTHS`' arrangement, for its reason.
         */
        const val HIDDEN_TENTHS: Int = 100

        /**
         * Read a scan's parameters as a path, or refuse them by name.
         *
         * Four ways to be malformed, and the last is D-140's. A dot at no speed never reaches the
         * cover and never hides. A wait of nothing asks where the dot is at the instant the player
         * watched it go in, which is not a question about anything hidden. And **a wait longer than
         * the crossing puts the dot back in sight before the buzz**, which is the whole ruling
         * inverted: the answer would be on the screen, and the thing the Subroutine tests would not
         * be tested. The seed is not refused, because there is no seed this cannot draw.
         */
        fun of(parameters: List<Int>): DriftPath {
            if (parameters.size != LENGTH) {
                throw MalformedSubroutineParameters(
                    "DRIFT is $LENGTH numbers — the layout's seed, the speed, the wait and the " +
                        "hit radius — and this is ${parameters.size}: $parameters",
                )
            }
            val (seed, speed, hiddenTenths, radius) = parameters
            if (radius !in RADIUS_LEAST..RADIUS_MOST) {
                throw MalformedSubroutineParameters(
                    "DRIFT was given a hit radius of $radius and this lane grades $RADIUS_LEAST " +
                        "to $RADIUS_MOST — narrower asks a finger in the dark for an exact step, " +
                        "wider is a stretch of lane where every tap is the right one",
                )
            }
            if (speed < 1) {
                throw MalformedSubroutineParameters(
                    "DRIFT was given a speed of $speed, and a dot that does not drift never " +
                        "reaches the cover and never hides",
                )
            }
            if (hiddenTenths < 1) {
                throw MalformedSubroutineParameters(
                    "DRIFT was given a wait of $hiddenTenths tenths of a second, and a buzz at " +
                        "the instant the dot goes in asks where the player just watched it go",
                )
            }
            val cover = coverFrom(seed)
            val first = cover.first()
            val enteredAt = first.first * MILLIS_PER_SECOND / speed
            val leavesAt = (first.last + 1) * MILLIS_PER_SECOND / speed
            val askAt = enteredAt + hiddenTenths * HIDDEN_TENTHS
            if (askAt >= leavesAt) {
                throw MalformedSubroutineParameters(
                    "DRIFT was given a wait of $hiddenTenths tenths at speed $speed, and the dot " +
                        "is back in sight ${leavesAt - enteredAt}ms after it hides — a buzz the " +
                        "player can answer by looking is not the question D-140 asks",
                )
            }
            return DriftPath(
                lane = seed.mod(LANES),
                cover = cover,
                speed = speed,
                askAtMillis = askAt,
                hitRadius = radius,
            )
        }

        /**
         * **The longest wait, in tenths, that still has the dot hidden when the phone buzzes.**
         *
         * The refusal in [of] read the other way round, and it exists so that the house can draw a
         * wait it is *about* to be refused for. How long the dot spends behind the first piece of
         * cover depends on how wide this seed drew that piece and how fast the dot is going, so
         * there is no fixed ceiling to draw inside — there is only this number, and the two ends
         * agree about it because they are the same arithmetic.
         *
         * Zero would mean the dot crosses the whole first piece of cover in under a tenth of a
         * second, which the narrowest cover and the fastest dot the house draws cannot do. The
         * house's own test says so out loud rather than trusting the arithmetic here.
         */
        fun hiddenTenthsAvailable(seed: Int, speed: Int): Int {
            val first = coverFrom(seed).first()
            val entered = first.first * MILLIS_PER_SECOND / speed
            val leaves = (first.last + 1) * MILLIS_PER_SECOND / speed
            return (leaves - entered - 1) / HIDDEN_TENTHS
        }

        /**
         * The cover, drawn from the seed: one piece per slot, each somewhere inside its own slot,
         * with [CLEAR] steps of lane guaranteed before the next.
         *
         * A slot apiece rather than three free placements, because two pieces of cover that touched
         * would be one wider piece — and the dot would then hide for a stretch the layout does not
         * look like it covers. The player reads the picture; the picture has to be the question.
         */
        private fun coverFrom(seed: Int): List<IntRange> {
            var digits = seed.mod(SEED_SPREAD)
            return List(COVER) { slot ->
                val width = COVER_LEAST + digits.mod(COVER_MOST - COVER_LEAST + 1)
                digits /= COVER_MOST - COVER_LEAST + 1
                val room = SLOT - width - CLEAR
                val start = RUN_IN + slot * SLOT + digits.mod(room + 1)
                digits /= room + 1
                start until start + width
            }
        }

        /**
         * How much of the seed the layout reads, so that consecutive seeds lay out differently.
         *
         * The stand-in draw in `core` hands over small numbers today (E-L4-3), which lay the same
         * cover out repeatedly — deterministic and dull rather than wrong. It is the same
         * arrangement [SignalGraph] uses on the same kind of number, and it is the shape the real
         * per-kind draw will fill.
         */
        private const val SEED_SPREAD: Int = 100_000
    }
}

/**
 * **Replay's question: which dots flash, and in what order.**
 *
 * The design's Replay is *3–5 dots flash in order; tap them back*. The pattern **is** the question
 * and it is not a secret: it is played on the screen and remembering it is the work. What stays
 * with the house is whether what came back was it.
 *
 * ### No dot flashes twice, and that is a refusal rather than a taste
 *
 * A tapped dot lights and stays lit (`ReplayScreen` in `ui` — the echo is per-element and there is
 * nothing to un-light), so a pattern that visited the same dot twice could not be echoed
 * distinguishably: the player would tap it, watch it already be lit, and have no way to say *again*.
 * It would be a question with an answer the screen cannot express, which is [SniffGroups]' tie one
 * Subroutine along.
 */
class ReplayPattern private constructor(
    /** The dots, in the order they flash. Also, and unavoidably, the order they are tapped back. */
    val order: List<Int>,
) {

    companion object {

        /** The roster's own range. `gdd.md:565` says 3–5 and this is that, unedited. */
        const val LEAST_DOTS: Int = 3
        const val MOST_DOTS: Int = 5

        /**
         * Read a scan's parameters as a pattern, or refuse them by name.
         *
         * **A Replay instance is as many numbers as it has dots**, which is the one kind whose
         * length is the question rather than a fixture — the screen draws one dot per element,
         * so the count of parameters is the count of dots and there is nothing else to say.
         */
        fun of(parameters: List<Int>): ReplayPattern {
            if (parameters.size !in LEAST_DOTS..MOST_DOTS) {
                throw MalformedSubroutineParameters(
                    "REPLAY flashes $LEAST_DOTS to $MOST_DOTS dots and this is " +
                        "${parameters.size}: $parameters",
                )
            }
            val stray = parameters.firstOrNull { it !in parameters.indices }
            if (stray != null) {
                throw MalformedSubroutineParameters(
                    "REPLAY was told to flash dot $stray and this screen draws " +
                        "${parameters.size} of them: $parameters",
                )
            }
            if (parameters.toSet().size != parameters.size) {
                throw MalformedSubroutineParameters(
                    "REPLAY was given a pattern that flashes the same dot twice, and a tapped dot " +
                        "stays lit — the player has no way to say *again*: $parameters",
                )
            }
            return ReplayPattern(parameters)
        }
    }
}

/**
 * **Handshake's question: a rhythm, as the silences between its beats.**
 *
 * The design's Handshake is *return the rhythm by feel* — the phone buzzes a pattern and the player
 * taps it back, on the roster's other dark Medium. The question is the **gaps**: a rhythm is what
 * the silences do, and the buzzes are all one length for [SniffGroups]' reason (there is one short
 * buzz in this app, not one per Subroutine).
 *
 * ### What comes back is the gaps, and each is graded to within [TOLERANCE_PERCENT] (D-296)
 *
 * The owner's Q7 ruling, option C: *each gap between taps within ±40 % of the dealt gap.* The phone
 * times its own taps on its own monotonic clock and sends the silences between them as a
 * [HandshakeTaps] entry, one length for every rhythm, every role and every outcome; the house reads
 * them back with the same object and grades each against the gap it dealt. Until D-296 the entry
 * was the ordinal of each tap and a rhythm returned at any tempo passed (D-238).
 */
class HandshakeRhythm private constructor(
    /** The silence before each beat after the first, in milliseconds, in order. */
    val gapsMillis: List<Int>,
) {

    /** How many taps the player owes. One per buzz. */
    val beats: Int get() = gapsMillis.size + 1

    /** The pattern, as the device unit will play it: a buzz, then each gap and the beat after it. */
    val script: List<HapticStep> = buildList {
        add(HapticStep.Buzz(SniffGroups.PULSE_MILLIS))
        gapsMillis.forEach { gap ->
            add(HapticStep.Rest(gap))
            add(HapticStep.Buzz(SniffGroups.PULSE_MILLIS))
        }
    }

    companion object {

        /**
         * How many beats a rhythm runs to.
         *
         * The design rates it nowhere. Three is the fewest that is a rhythm rather than a pair of
         * taps; six is where a pattern held by feel alone stops being one thing and becomes two
         * halves the player is rehearsing separately. Placeholders, both — playtest owns them.
         */
        const val LEAST_BEATS: Int = 3
        const val MOST_BEATS: Int = 6

        /** The gaps arrive in tenths of a second, [SniffGroups.GAP_TENTHS]' arrangement. */
        const val GAP_TENTHS: Int = 100

        /** The shortest and longest silence between two beats, in tenths. */
        const val GAP_LEAST: Int = 2
        const val GAP_MOST: Int = 9

        /**
         * **How far a returned gap may sit from the dealt one, as a percentage of the dealt one**
         * (the owner's Q7 ruling, option C, D-296): ±40 %, and exactly 40 % is within.
         *
         * Here beside the question for [InterruptSweep.bandHalf]'s reason: the house grades with
         * the number the question carries, not with a copy of its own. It is a rule every phone
         * plays under, the same for every role, so a screen that could read it learns nothing.
         */
        const val TOLERANCE_PERCENT: Int = 40

        /**
         * Read a scan's parameters as a rhythm, or refuse them by name.
         *
         * **A Handshake instance is one number per gap**, so it is [ReplayPattern]'s arrangement:
         * the length of the list is part of the question. A gap of nothing is two beats that are
         * one beat, and a gap the wrong side of a second is a pause the player reads as the pattern
         * having ended.
         */
        fun of(parameters: List<Int>): HandshakeRhythm {
            if (parameters.size + 1 !in LEAST_BEATS..MOST_BEATS) {
                throw MalformedSubroutineParameters(
                    "HANDSHAKE runs to $LEAST_BEATS–$MOST_BEATS beats and this is " +
                        "${parameters.size + 1}: $parameters",
                )
            }
            val bad = parameters.firstOrNull { it !in GAP_LEAST..GAP_MOST }
            if (bad != null) {
                throw MalformedSubroutineParameters(
                    "HANDSHAKE was given a gap of $bad tenths of a second and a beat falls " +
                        "$GAP_LEAST to $GAP_MOST — shorter is two beats that are one, longer is a " +
                        "pattern the player reads as over: $parameters",
                )
            }
            return HandshakeRhythm(parameters.map { it * GAP_TENTHS })
        }
    }
}

/**
 * **Handshake's entry: the silences between the player's taps, at one length whatever was tapped**
 * (the owner's Q7 ruling, D-296).
 *
 * The phone stamps each tap on its own monotonic clock and [of] turns the stamps into the entry
 * `HandOverEntry.entered` carries. The house reads it back with [gapsIn] and grades each gap
 * against the one it dealt (`grades` in `core`). Both ends use this object, so they cannot disagree
 * about what a number in the entry means — [SubroutineQuestion]'s reason, one step along.
 *
 * ### One length and one width, for every role and every outcome
 *
 * Encryption hides what a frame says and not how long it is (D-263). So an entry is always
 * [GAPS] numbers, enough for the longest Handshake any rule can deal ([HandshakeRhythm.MOST_BEATS]
 * beats), and every number is five digits: [FLOOR] plus the gap in milliseconds, with [NONE] where
 * a shorter rhythm has no gap. A three-beat entry and a six-beat one, a rhythm returned perfectly
 * and one tapped at random, a Resident's and an Insider's fake: the same [GAPS] five-digit numbers,
 * the same bytes long. A gap longer than [LONGEST] is written as [LONGEST]; any gap that long is
 * already several times past the tolerance of the longest gap the house deals, so the cap moves
 * no verdict.
 *
 * ### Nothing here grades
 *
 * This is an encoding, and it holds no dealt gap and no tolerance test. Whether a gap is near the
 * one asked for is decided in `core`, which `ui` cannot see.
 */
object HandshakeTaps {

    /** How many gaps every entry carries: those of the longest Handshake any rule can deal. */
    const val GAPS: Int = HandshakeRhythm.MOST_BEATS - 1

    /** What every number in an entry starts from, so that each is five digits long. */
    const val FLOOR: Int = 10_000

    /** The longest gap an entry can say, in milliseconds. A longer one is written as this. */
    const val LONGEST: Int = 9_998

    /** A gap the entry has no tap for: the padding after a rhythm shorter than the longest. */
    const val NONE: Int = FLOOR + LONGEST + 1

    /**
     * **The entry for taps stamped at [tapMillis]**, in the order they landed, on one monotonic
     * clock. The gaps between neighbours, padded with [NONE] to [GAPS]. No tap and one tap are
     * entries with no gap at all. The screen hands a rhythm over on its last owed beat, so it never
     * has more than [HandshakeRhythm.MOST_BEATS] stamps; more than that is not a Handshake, and is
     * written as no gap at all, which every rhythm refuses — never cut down to one that would pass.
     */
    fun of(tapMillis: List<Long>): List<Int> =
        if (tapMillis.size > GAPS + 1) {
            ofGaps(emptyList())
        } else {
            ofGaps(tapMillis.zipWithNext { a, b -> (b - a).coerceIn(0L, LONGEST.toLong()).toInt() })
        }

    /** **The entry that returns exactly [gapsMillis]**: what the house expects for a dealt rhythm. */
    fun ofGaps(gapsMillis: List<Int>): List<Int> =
        List(GAPS) { at -> gapsMillis.getOrNull(at)?.let { FLOOR + it.coerceIn(0, LONGEST) } ?: NONE }

    /**
     * **The gaps in [entered], or null when it is not an entry at all**: the wrong length, or a
     * number that is neither a gap nor [NONE]. A null in the list is a [NONE]: no tap there.
     */
    fun gapsIn(entered: List<Int>): List<Int?>? {
        if (entered.size != GAPS) return null
        if (entered.any { it != NONE && it !in FLOOR..FLOOR + LONGEST }) return null
        return entered.map { if (it == NONE) null else it - FLOOR }
    }
}

/**
 * **Short's question: how many fingers to hold.**
 *
 * The design's Short is *hold N fingers on the screen for two seconds* — the roster's other short
 * dark, and the one Subroutine that commits both hands to the glass. The whole question is N, it is
 * drawn on the screen in words, and the hold ends on the clock rather than on the count (see
 * `HoldEntry` in `ui`, which is told nothing at all).
 */
class ShortHold private constructor(
    /** How many fingers the house is asking for. */
    val fingers: Int,
) {

    companion object {

        /** How many numbers a Short instance is: the count, and nothing else. */
        const val LENGTH: Int = 1

        /**
         * One hand's worth, and at least two.
         *
         * One finger is a press rather than a pose, and it is the number a phone in a pocket
         * produces by accident; five is a hand. Placeholders — the design says *N* and never says
         * what N is.
         */
        const val LEAST_FINGERS: Int = 2
        const val MOST_FINGERS: Int = 5

        /** Read a scan's parameters as a hold, or refuse them by name. */
        fun of(parameters: List<Int>): ShortHold {
            if (parameters.size != LENGTH) {
                throw MalformedSubroutineParameters(
                    "SHORT is $LENGTH number — how many fingers — and this is " +
                        "${parameters.size}: $parameters",
                )
            }
            val fingers = parameters[0]
            if (fingers !in LEAST_FINGERS..MOST_FINGERS) {
                throw MalformedSubroutineParameters(
                    "SHORT was told to ask for $fingers fingers and a hand holds " +
                        "$LEAST_FINGERS to $MOST_FINGERS — one is a press, and six is not a hand",
                )
            }
            return ShortHold(fingers)
        }
    }
}

/**
 * **Jam's question: how far apart the two rings start.**
 *
 * The design's Jam is *tap +/− until two shapes overlap; slow, forgiving, satisfying*. The house
 * opens the player's ring [opening] steps away from the fixed one — signed, because the ring can
 * open inside or outside — and closing that gap is the work. `ScalarEntry` in `ui` holds the net
 * steps pressed and **not** the gap, so nothing on the phone can subtract one from the other.
 */
class JamRings private constructor(
    /** How far the player's ring starts from the fixed one, in steps, signed. Never zero. */
    val opening: Int,
) {

    companion object {

        /** How many numbers a Jam instance is: the opening gap, and nothing else. */
        const val LENGTH: Int = 1

        /**
         * **How far the ring may travel from where the house opened it, in steps.**
         *
         * A stop, so the shape cannot be driven off the screen, deliberately far outside anything
         * a player would aim at — a limit that bit near the target would be the device fencing the
         * answer. It is here rather than in `ui` for the reason the band's width is: the house
         * cannot open a ring further out than the player is able to walk it back.
         */
        const val REACH: Int = 16

        /** The smallest and largest opening gap. Playtest owns both; the reach bounds the larger. */
        const val LEAST: Int = 3
        const val MOST: Int = REACH - 4

        /** Read a scan's parameters as an opening, or refuse them by name. */
        fun of(parameters: List<Int>): JamRings {
            if (parameters.size != LENGTH) {
                throw MalformedSubroutineParameters(
                    "JAM is $LENGTH number — how far apart the rings open — and this is " +
                        "${parameters.size}: $parameters",
                )
            }
            val opening = parameters[0]
            val steps = if (opening < 0) -opening else opening
            if (steps !in LEAST..MOST) {
                throw MalformedSubroutineParameters(
                    "JAM was told to open the rings $opening steps apart and this screen opens " +
                        "$LEAST to $MOST either way — closer is two rings that already sit on one " +
                        "another, further is a walk the reach of $REACH cannot finish",
                )
            }
            return JamRings(opening)
        }
    }
}

/**
 * **Every question the house can ask, read by the client that will be asked it.**
 *
 * Total over the roster and deliberately exhaustive: an eleventh [SubroutineKind] stops this
 * compiling in the same change that adds the row, rather than dealing instances nothing has ever
 * read. It is the shape rule 8 asks for, turned on the parameters instead of on the screens.
 *
 * **This is the oracle the house's draw is tested against** — `SubroutineDrawTest` in `core` deals
 * thousands of instances of all ten kinds and reads every one of them through here, so *it is
 * impossible to deal an instance a client would refuse* is a thing that can fail rather than a
 * thing that was intended.
 */
object SubroutineQuestion {

    /** Read [parameters] as a question of [kind], or throw [MalformedSubroutineParameters]. */
    fun read(kind: SubroutineKind, parameters: List<Int>) {
        when (kind) {
            SubroutineKind.Replay -> ReplayPattern.of(parameters)
            SubroutineKind.Interrupt -> InterruptSweep.of(parameters)
            SubroutineKind.ParityCheck -> ParityGrid.of(parameters)
            SubroutineKind.Sniff -> SniffGroups.of(parameters)
            SubroutineKind.Deallocate -> DotColumns.of(parameters)
            SubroutineKind.Drift -> DriftPath.of(parameters)
            SubroutineKind.Short -> ShortHold.of(parameters)
            SubroutineKind.SignalTrace -> SignalGraph.of(parameters)
            SubroutineKind.Jam -> JamRings.of(parameters)
            SubroutineKind.Handshake -> HandshakeRhythm.of(parameters)
            SubroutineKind.MemoryDump -> MemoryPattern.of(parameters)
        }
    }
}

/**
 * **Memory Dump's two questions: the pattern at the source, and how long it was at the target**
 * (`gdd.md:640`, D-322).
 *
 * One reader for both stages, because they are one Subroutine and the first number says which:
 *
 * - **at the source** `[SOURCE, e₁ … eₙ]` — the pattern itself, four or five elements
 *   (`gdd.md:646`: *four elements, maybe five*). It is shown and then it is gone: the phone may not
 *   keep it (*only your head is off-network*) and Files never shows it (F-015);
 * - **at the target** `[TARGET, n]` — how many elements to enter, and nothing else. The answer is
 *   the house's copy of the pattern, held between the two cards (`GameState.carries`), and it never
 *   leaves the house again.
 *
 * ### ⚠️ What an element is, is STOPPED for the owner (D-322)
 *
 * The design says how many and never says of what. Until the owner rules, an element is **a dot,
 * drawn the way [ReplayPattern]'s are** — `n` distinct indices into `n` positions, in the order
 * they are shown — because Replay is the roster's one ruled *show a sequence, take it back* and its
 * reader already refuses what its screen could not echo. It is a placeholder for the house's draw
 * and grade only; **no phone draws either stage yet.**
 *
 * All-or-nothing (`gdd.md:648`): the grade is equality, and a mismatch says *mismatch* and never
 * which elements were wrong.
 */
class MemoryPattern private constructor(
    /** True at the source, where the pattern is shown; false at the target, where it is entered. */
    val shown: Boolean,
    /** The pattern, at the source; empty at the target, where it is in the player's head. */
    val elements: List<Int>,
    /** How many elements the pattern has, at either stage. */
    val length: Int,
) {
    companion object {
        /** The first parameter at the source. */
        const val SOURCE: Int = 0

        /** The first parameter at the target. */
        const val TARGET: Int = 1

        /**
         * `gdd.md:646`: *four elements, maybe five.* **Fixed at four** (the owner approved the
         * draft as drawn, 2026-11-22, D-325): Replay's dots in a 2×2 grid, each place once.
         */
        const val LENGTH: Int = 4

        fun of(parameters: List<Int>): MemoryPattern {
            val stage = parameters.firstOrNull()
            return when (stage) {
                SOURCE -> {
                    val elements = parameters.drop(1)
                    if (elements.size != LENGTH) {
                        throw MalformedSubroutineParameters(
                            "MEMORY DUMP shows $LENGTH elements and this is ${elements.size}: $parameters",
                        )
                    }
                    if (elements.any { it !in elements.indices } || elements.toSet().size != elements.size) {
                        throw MalformedSubroutineParameters(
                            "MEMORY DUMP was given a pattern that is not ${elements.size} distinct " +
                                "places in ${elements.size}: $parameters",
                        )
                    }
                    MemoryPattern(shown = true, elements = elements, length = elements.size)
                }
                TARGET -> {
                    val length = parameters.getOrNull(1)
                    if (parameters.size != 2 || length != LENGTH) {
                        throw MalformedSubroutineParameters(
                            "MEMORY DUMP's target asks for $LENGTH elements and this is $parameters",
                        )
                    }
                    MemoryPattern(shown = false, elements = emptyList(), length = LENGTH)
                }
                else -> throw MalformedSubroutineParameters(
                    "MEMORY DUMP's first number says which card this is, $SOURCE or $TARGET: $parameters",
                )
            }
        }
    }
}

/** One second, in milliseconds. Named because two motions divide by it and neither is a duration. */
private const val MILLIS_PER_SECOND: Int = 1_000
