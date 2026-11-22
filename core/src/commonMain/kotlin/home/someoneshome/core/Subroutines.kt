package home.someoneshome.core

import home.someoneshome.model.DotColumns
import home.someoneshome.model.DriftPath
import home.someoneshome.model.HandshakeRhythm
import home.someoneshome.model.HandshakeTaps
import home.someoneshome.model.InterruptSweep
import home.someoneshome.model.JamRings
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MemoryPattern
import home.someoneshome.model.OpenSubroutine
import home.someoneshome.model.ParityGrid
import home.someoneshome.model.ReplayPattern
import home.someoneshome.model.ShortHold
import home.someoneshome.model.SignalGraph
import home.someoneshome.model.SniffGroups
import home.someoneshome.model.SubroutineKind

/*
 * **The house's half of a Subroutine: what it asks, what the answer is, and what counts as right.**
 *
 * Three functions and they are one thing said three times, so they are in one file:
 *
 * - [drawParameters] deals a question of a given kind — and **it cannot deal one a client would
 *   refuse**, because every constraint it draws inside is the constraint the reader in `model`
 *   refuses outside of. That claim is not a comment: `SubroutineDrawTest` deals thousands of every
 *   kind and reads all of them through `SubroutineQuestion.read`.
 * - [answerTo] answers the question **by reading it with the client's own reader** and working out
 *   what a player who read it correctly would do. There is no second generator here — the odd cell
 *   is found in the grid the phone will draw, the shortest route is walked over the lattice the
 *   phone will draw — so the house cannot grade a picture it is not showing.
 * - [grades] compares what came back with what was asked for, **within the tolerance the question
 *   itself carries** where the answer is a position rather than a choice (D-139, D-140).
 *
 * ### Why all three are here and not in `model` beside the shapes they read
 *
 * `ui` sees `model`, and none of this may ever be reachable from a screen. A phone that could ask
 * *which cell breaks the pattern* or *was that tap inside the band* is a phone that can adjudicate,
 * and the moment a screen adjudicates, an Insider's fake and a Resident's real work stop being the
 * same picture (rule 8, D-109). `ui` cannot see `core`, so this is a boundary the build enforces
 * rather than a paragraph asking nicely.
 *
 * ### Every number in here is a placeholder and the STRUCTURE is what is owned
 *
 * Group gaps, speeds, radii, how many dots a column holds — playtest owns all of them (D-137 to
 * D-140) and nobody has played with any of them. What this file settles is the shape: that a
 * question of each kind has a size and bounds, that an answer is derived from the question rather
 * than stored beside it, and that a tolerance is a number the house drew rather than a fixture each
 * end keeps its own copy of.
 */

/**
 * **A question of [kind], drawn from the round's own stream.**
 *
 * Deterministic in [draw] and nothing else (rule 4): the same recording replays the same ten
 * questions in the same order. It does not read `Role` and has no way to — D-109 grades every entry
 * for real, for both roles, by the same rule, and a question drawn per role would put the game's
 * central secret into the answer key.
 */
internal fun drawParameters(kind: SubroutineKind, draw: Draw): List<Int> = when (kind) {

    // The pattern IS the question: as many numbers as there are dots, each dot once, in the order
    // they flash. Drawn as a shuffle rather than as N independent picks, because independent picks
    // repeat and a repeated dot is a pattern the screen cannot echo.
    SubroutineKind.Replay -> {
        val dots = ReplayPattern.LEAST_DOTS +
            draw.next(ReplayPattern.MOST_DOTS - ReplayPattern.LEAST_DOTS + 1)
        List(dots) { it }.shuffledBy(draw)
    }

    // The band's middle is drawn where the whole band fits on the bar, which is what keeps the
    // player from answering a picture that runs off the end. The phase starts the sweep part-way
    // through the bounce so that two scans of one card do not open on the same frame.
    SubroutineKind.Interrupt -> {
        val half = InterruptSweep.BAND_LEAST +
            draw.next(InterruptSweep.BAND_MOST - InterruptSweep.BAND_LEAST + 1)
        val at = half + draw.next(InterruptSweep.SPAN - 2 * half + 1)
        val speed = SWEEP_SLOWEST + draw.next(SWEEP_FASTEST - SWEEP_SLOWEST + 1)
        listOf(at, half, speed, draw.next(InterruptSweep.CYCLE))
    }

    SubroutineKind.ParityCheck -> listOf(draw.next(ParityGrid.SIZE))

    // D-137: equal groups never occur. The second group is drawn as the first plus a gap and then
    // put on one side or the other, so a tie is not something this draw rejects and re-rolls -- it
    // is something it cannot express. A rejection loop would be a draw whose length depends on what
    // it drew, which is a stream that stops being replayable the day the bounds change.
    SubroutineKind.Sniff -> {
        val gap = SNIFF_GAP_LEAST + draw.next(SNIFF_GAP_MOST - SNIFF_GAP_LEAST + 1)
        val smaller = SNIFF_SMALLEST + draw.next(SniffGroups.MOST_BUZZES - gap - SNIFF_SMALLEST + 1)
        val bigger = smaller + gap
        val biggerIsSecond = draw.next(2) == 1
        val pause = SNIFF_PAUSE_LEAST + draw.next(SNIFF_PAUSE_MOST - SNIFF_PAUSE_LEAST + 1)
        if (biggerIsSecond) listOf(smaller, bigger, pause) else listOf(bigger, smaller, pause)
    }

    // The columns are dealt from a floor upwards, so the shortest column is never the panel's own
    // minimum by construction -- a distribution whose level was always one dot would be a
    // Subroutine you could do without looking at the short column at all.
    SubroutineKind.Deallocate -> {
        val columns = DotColumns.LEAST_COLUMNS +
            draw.next(DotColumns.MOST_COLUMNS - DotColumns.LEAST_COLUMNS + 1)
        val floor = 1 + draw.next(DEALLOCATE_FLOOR_SPREAD)
        List(columns) { floor + draw.next(DotColumns.MOST_DOTS - floor + 1) }
    }

    // The wait is drawn short enough that the dot is still behind the first piece of cover when the
    // phone buzzes -- D-140's whole ruling -- and the reader is what says whether it was, because
    // how long the cover lasts depends on the layout this seed lays down and on the speed. So the
    // seed and the speed are drawn first and the wait is drawn INSIDE what they left room for.
    SubroutineKind.Drift -> {
        val seed = draw.next(DRIFT_SEEDS)
        val speed = DRIFT_SLOWEST + draw.next(DRIFT_FASTEST - DRIFT_SLOWEST + 1)
        val radius = DriftPath.RADIUS_LEAST +
            draw.next(DriftPath.RADIUS_MOST - DriftPath.RADIUS_LEAST + 1)
        val room = DriftPath.hiddenTenthsAvailable(seed, speed)
        listOf(seed, speed, 1 + draw.next(room), radius)
    }

    SubroutineKind.Short ->
        listOf(
            ShortHold.LEAST_FINGERS +
                draw.next(ShortHold.MOST_FINGERS - ShortHold.LEAST_FINGERS + 1),
        )

    // Only the seeds whose lattice has ONE shortest route through it. See [traceSeeds].
    SubroutineKind.SignalTrace -> listOf(traceSeeds[draw.next(traceSeeds.size)])

    // Signed, because the ring can open inside the fixed one or outside it, and a Subroutine whose
    // shape always opened the same side would be one the player could start walking before looking.
    SubroutineKind.Jam -> {
        val steps = JamRings.LEAST + draw.next(JamRings.MOST - JamRings.LEAST + 1)
        listOf(if (draw.next(2) == 1) -steps else steps)
    }

    // One number per gap, so the list is one shorter than the beat count.
    SubroutineKind.Handshake -> {
        val beats = HandshakeRhythm.LEAST_BEATS +
            draw.next(HandshakeRhythm.MOST_BEATS - HandshakeRhythm.LEAST_BEATS + 1)
        List(beats - 1) {
            HandshakeRhythm.GAP_LEAST +
                draw.next(HandshakeRhythm.GAP_MOST - HandshakeRhythm.GAP_LEAST + 1)
        }
    }

    // The source's question: the pattern itself, four or five elements (`gdd.md:646`). The scan
    // handler draws it through [patternFor] off the same stream; this branch is the same draw, so a
    // sweep of every kind through [drawParameters] reads a Memory Dump the way a phone would.
    SubroutineKind.MemoryDump -> {
        val length = MemoryPattern.LEAST + draw.next(MemoryPattern.MOST - MemoryPattern.LEAST + 1)
        listOf(MemoryPattern.SOURCE) + List(length) { it }.shuffledBy(draw)
    }
}

/**
 * **What the house will grade [parameters] against**, in the ordered integers the player's own
 * screen deals in.
 *
 * Derived from the question every time rather than drawn beside it, which is what makes it *the
 * answer to that question*: there is no second stream to fall out of step, and a parameter list
 * that changed shape without this changing with it would not compile.
 *
 * For the two graded by tolerance this is the **centre** — the band's middle, the dot's true
 * position — and [grades] is what knows how far off it a tap may land.
 */
internal fun answerTo(kind: SubroutineKind, parameters: List<Int>): List<Int> = when (kind) {

    // Tap the dots back in the order they flashed. The pattern is the answer, which is why the
    // pattern is on the screen.
    SubroutineKind.Replay -> ReplayPattern.of(parameters).order

    SubroutineKind.Interrupt -> listOf(InterruptSweep.of(parameters).bandAt)

    // The cell that breaks the checkerboard, found in the grid the phone will draw rather than
    // recomputed from the seed. One generator, and the answer is read out of its output.
    SubroutineKind.ParityCheck -> {
        val cells = ParityGrid.of(parameters)
        listOf(cells.indices.first { cells[it] != ParityGrid.clean(it) })
    }

    // Which group was bigger, as the half of the screen that group was played on. Never a tie:
    // the reader refuses one and the draw cannot express one (D-137).
    SubroutineKind.Sniff -> {
        val groups = SniffGroups.of(parameters)
        listOf(if (groups.first > groups.second) SNIFF_FIRST else SNIFF_SECOND)
    }

    // Every column's excess over the shortest. D-138: evening out means levelling DOWN, so the
    // work is the sum of these and the entry is the removals column by column.
    SubroutineKind.Deallocate -> {
        val columns = DotColumns.of(parameters)
        val level = columns.min()
        columns.map { it - level }
    }

    // Where the dot is at the instant the phone buzzes. Behind cover by construction, which is the
    // whole of the Subroutine and the reason the reader refuses a wait that outlasts the cover.
    SubroutineKind.Drift -> {
        val path = DriftPath.of(parameters)
        listOf(path.at(path.askAtMillis.toLong()) ?: DriftPath.SPAN)
    }

    SubroutineKind.Short -> listOf(ShortHold.of(parameters).fingers)

    // The shortest route across the lattice, computed rather than authored, and unique because the
    // draw only deals seeds whose lattice has one (see [traceSeeds]). Stated from the source; its
    // reverse is accepted too (D-211), and is unique for the same reason.
    SubroutineKind.SignalTrace -> shortestRoutes(SignalGraph.of(parameters)).single()

    // What closes the gap: the opposite of the opening, because the entry is net steps pressed.
    SubroutineKind.Jam -> listOf(-JamRings.of(parameters).opening)

    // The entry that returns the dealt gaps exactly (D-296): each silence in milliseconds, padded
    // to the longest Handshake any rule can deal. [grades] is what knows how far off each may be.
    SubroutineKind.Handshake -> HandshakeTaps.ofGaps(HandshakeRhythm.of(parameters).gapsMillis)

    // The pattern shown at the source is the answer at the target. The target's own instance is
    // built from the house's held copy (`patternEntry`), never from its parameters, which carry
    // only the length.
    SubroutineKind.MemoryDump -> MemoryPattern.of(parameters).elements
}

/**
 * **Was this the entry the house asked for** — equality where the answer is discrete, and the
 * question's own tolerance where it is a position (E-L4-3).
 *
 * Seven of the ten are a choice, a count, a sequence or a final state, and for those *right* is
 * exactly right: there is no near-miss in tapping the wrong cell. Signal Trace's route is exactly
 * right **in either direction** (D-211): the screen marks two ends alike and the briefing says
 * *from one to the other*, so the house's route and its reverse are the same answer, and nothing
 * else is. Two of them are a **place on a
 * moving picture**, and D-139 and D-140 both rule a tolerance rather than a point — *inside the
 * band*, *within the hit radius* — because a blind one-handed tap at a bouncing sweep is not a
 * claim about an integer. **Handshake is a rhythm returned by feel**, and the owner's Q7 ruling
 * grades each gap between its taps to within ±40 % of the gap dealt (D-296).
 *
 * **The tolerance is read out of the question**, never out of a constant here: the band the house
 * grades is the band the phone drew, because they are the same number (E-S2-1, closed). A copy of
 * it in this file would be the same fault one module further along.
 *
 * Exhaustive over the roster on purpose. An eleventh Subroutine cannot quietly inherit equality
 * from an `else` — it stops the build until somebody says how it is graded.
 */
internal fun grades(open: OpenSubroutine, marker: MarkerId, entered: List<Int>): Boolean {
    if (!open.standingAt(marker)) return false
    return when (open.subroutine) {
        SubroutineKind.Replay,
        SubroutineKind.ParityCheck,
        SubroutineKind.Sniff,
        SubroutineKind.Deallocate,
        SubroutineKind.Short,
        SubroutineKind.Jam,
        -> open.expected == entered

        // **All-or-nothing** (`gdd.md:648`): the whole pattern, in order, or a mismatch. No partial
        // credit, so guessing and keeping the hits banks nothing.
        SubroutineKind.MemoryDump -> open.expected == entered

        // D-211: the route traced from either end. Both ends are drawn alike, so which one the
        // player started from is not part of the question; the route is.
        SubroutineKind.SignalTrace ->
            open.expected == entered || open.expected.asReversed() == entered

        SubroutineKind.Interrupt ->
            within(entered, open.expected, InterruptSweep.of(open.parameters).bandHalf)

        SubroutineKind.Drift ->
            within(entered, open.expected, DriftPath.of(open.parameters).hitRadius)

        // D-296, the owner's Q7 ruling: every gap within the question's own tolerance of its own
        // dealt gap. One function, for every role: the Insider's fake comes through here too.
        SubroutineKind.Handshake ->
            inRhythm(entered, open.expected, HandshakeRhythm.TOLERANCE_PERCENT)
    }
}

/**
 * **Every gap returned within [percent] of the gap dealt, and a tap for exactly the gaps dealt**
 * (the owner's Q7 ruling, option C, D-296).
 *
 * [expected] and [entered] are both `HandshakeTaps` entries, read by the one reader the phone
 * wrote them with. Each dealt gap needs a returned gap no further from it than [percent] of it,
 * exactly [percent] included, in whole milliseconds and integer arithmetic (rule 4). A gap where
 * the rhythm dealt none, which is an extra tap, and no gap where it dealt one, which is a missing
 * tap, are both a miss: there is nothing to compare the one with and nothing to grade in the
 * other. An entry that is not an entry at all is a miss, as [within] says of a wrong shape.
 */
private fun inRhythm(entered: List<Int>, expected: List<Int>, percent: Int): Boolean {
    val dealt = HandshakeTaps.gapsIn(expected) ?: return false
    val tapped = HandshakeTaps.gapsIn(entered) ?: return false
    return dealt.indices.all { at ->
        val asked = dealt[at]
        val gap = tapped[at]
        when {
            asked == null -> gap == null
            gap == null -> false
            else -> {
                val off = gap - asked
                (if (off < 0) -off else off) * PERCENT <= percent * asked
            }
        }
    }
}

/** A whole, as a percentage. */
private const val PERCENT = 100

/**
 * One number, no further from the one asked for than [tolerance].
 *
 * A list rather than an `Int` because that is what comes off the wire, and an entry of the wrong
 * shape is wrong rather than an exception: a client that sent two numbers for a single tap has
 * failed to answer, and the house says so in the same words it says every other refusal in.
 */
private fun within(entered: List<Int>, expected: List<Int>, tolerance: Int): Boolean {
    val tap = entered.singleOrNull() ?: return false
    val asked = expected.singleOrNull() ?: return false
    val off = tap - asked
    return (if (off < 0) -off else off) <= tolerance
}

/**
 * **Every route of the shortest length from source to sink**, over the lattice as drawn.
 *
 * Breadth-first, so the first depth at which the sink appears is the shortest length and everything
 * found at that depth is a route of it. It returns **all** of them rather than one, because the
 * count is the question the draw asks: a lattice with two shortest routes is a lattice where a
 * player can be right and be graded wrong, and that is D-137's tie in another Subroutine's clothes.
 */
internal fun shortestRoutes(wiring: SignalGraph.Wiring): List<List<Int>> {
    var live = listOf(listOf(wiring.source))
    repeat(wiring.nodes.size) {
        val arrived = live.filter { it.last() == wiring.sink }
        if (arrived.isNotEmpty()) return arrived
        live = live.flatMap { route ->
            wiring.joinedTo(route.last())
                .filter { it !in route }
                .sorted()
                .map { route + it }
        }
        if (live.isEmpty()) return emptyList()
    }
    return emptyList()
}

/**
 * **The Signal Trace seeds the house is allowed to deal: the ones whose lattice has exactly one
 * shortest route.**
 *
 * D-137's principle, one Subroutine along: *the answer must exist and must be unique.* The lattice
 * is drawn from the seed and the decoys hang off the walk, so some seeds produce two routes of the
 * same length — and grading equality against one of them would fail a player who walked the other
 * and was right. The client cannot refuse those (it would have to compute the optimum, which is the
 * one thing `SignalGraph` exists not to do), so the house never deals them.
 *
 * Sifted once, at class-load, over a fixed span of seeds — the same arrangement the readers use,
 * and for the same reason: a malformed roster fails the build rather than a player.
 */
internal val traceSeeds: List<Int> =
    (0 until TRACE_SEED_SPAN).filter { shortestRoutes(SignalGraph.of(listOf(it))).size == 1 }

/** How many Signal Trace seeds are sifted. Enough that the sifted list is not a short cycle. */
private const val TRACE_SEED_SPAN = 512

/** How many layout seeds Drift draws between. [TRACE_SEED_SPAN]'s reasoning, one Subroutine along. */
private const val DRIFT_SEEDS = 512

/** Sniff's two halves, as the screen lays them out: first buzzed on the left, second on the right. */
private const val SNIFF_FIRST = 0
private const val SNIFF_SECOND = 1

/**
 * **How far apart the two Sniff groups are drawn.**
 *
 * *Playtest owns the gap between the two group sizes; it is the difficulty knob and the only one*
 * (D-137). One apart is the hardest comparison the Subroutine has and three is close to free.
 */
private const val SNIFF_GAP_LEAST = 1
private const val SNIFF_GAP_MOST = 3

/** The smallest a group gets. One buzz against two is a question about whether there was a group. */
private const val SNIFF_SMALLEST = 2

/** The pause between the groups, in tenths. Long enough to be a silence, short enough to be one. */
private const val SNIFF_PAUSE_LEAST = 6
private const val SNIFF_PAUSE_MOST = 10

/** How far above the panel's floor a Deallocate distribution's shortest column may start. */
private const val DEALLOCATE_FLOOR_SPREAD = 3

/**
 * **How fast Interrupt's sweep crosses the bar, in steps a second.**
 *
 * *Playtest owns the speed* (D-139) and *slow* is the only thing the design says (`gdd.md:595`).
 * Fourteen steps of a hundred is seven seconds end to end, which is the port's own fixture; the
 * range around it is deliberately narrow, because the width of the band is the other half of the
 * same difficulty and drawing both wide would make two instances of one Subroutine feel like two
 * Subroutines.
 */
private const val SWEEP_SLOWEST = 11
private const val SWEEP_FASTEST = 17

/** How fast Drift's dot crosses the lane, in steps a second. *Playtest owns the speed* (D-140). */
private const val DRIFT_SLOWEST = 10
private const val DRIFT_FASTEST = 15
