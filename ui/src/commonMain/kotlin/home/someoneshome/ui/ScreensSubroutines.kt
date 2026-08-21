package home.someoneshome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

/**
 * **The Subroutines themselves — six of the design's ten, with their interaction.**
 *
 * The screens in this file echo input and nothing else. A Subroutine's pattern arrives as an
 * Effect, the screen displays it, captures taps and echoes them locally, and what the player
 * entered goes back as an Intent the *server* verifies (D-042). Nothing here says whether an
 * answer was right, because nothing here can: every entry in [SubroutineEntry]'s family holds the
 * player's own input and has no field for the answer to sit in.
 *
 * ### The fake is the same screen, and that is the only way it stays a fake
 *
 * Every Subroutine ships with its fake in the same change — real UI, real progress, real
 * completion, writing nothing (rule 8). Nothing in this file takes a role, reads
 * [PanelState.role], or branches on one, so an Insider's Handshake *is* a Resident's Handshake:
 * one screen, one code path, nothing to keep in step and nothing to get wrong at four in the
 * morning six months from now. `SubroutineParityTest` renders both roles through the identical
 * input and compares the pixels.
 *
 * ### What is here, and what is the house's
 *
 * Three of the six have a presentation or a channel this port cannot build, and pretending
 * otherwise would be worse than leaving it out:
 *
 * - **Replay's dots flash in order.** A timed emission of light is the lamp changing, and the lamp
 *   is a pure function of state the core emitted (rule 5). The flash is therefore an Effect, and
 *   the loop is frozen. Built: the entry.
 * - **Handshake's rhythm is buzzed.** Haptics are the house's and this build has no phone attached
 *   to feel them on. Built: the return.
 * - **Short's asked-for count arrives from the house** and is a fixture here, as every number in
 *   this port is. The *gesture* is whole: several fingers, two seconds, and a hand-over.
 * - **Parity Check, Jam and Signal Trace have no timed presentation at all**, which is why those
 *   three are whole: the grid, the rings and the wiring are on the screen, the work is what you do
 *   to them, and the only thing the house owns is the verdict.
 */

/**
 * **Handshake — near-black, haptic, maximum concealment. The design says build it first.**
 *
 * You return a rhythm by feel. Nothing on this screen needs to be seen, so almost nothing is lit,
 * which makes it the safest Subroutine to be caught doing in a corridor — and the blindest, since
 * a near-black screen tells you nothing about the room you are standing in.
 *
 * ### The cells are uniform, and that is the point of the whole Subroutine
 *
 * The ported fixture drew its beats at differing widths, which read as *long beat, short beat* —
 * the rhythm itself, on the screen, in a Subroutine whose one instruction is **by feel**. A player
 * who can see the pattern is not doing the haptic Subroutine, they are doing a bright one with the
 * brightness turned down. So the cells here are identical to one another and carry only **how many
 * beats have gone back**, which is the player's own input and the only thing a screen may echo.
 *
 * The count is drawn as cells rather than as a numeral, per the bench's own rule — *comparing
 * quantities is perception; adding numbers is computation* — which also happens to be the darker
 * of the two, and on this screen that is not a coincidence.
 */
@Composable
fun HandshakeScreen() {
    val entry = LocalSubroutine.current.handshake
    val actions = LocalActions.current
    SubroutineFrame(Subroutine.Handshake, ink = Amber.Dim, mark = Amber.Faint) {
        Column(
            Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge).padding(10.u),
            verticalArrangement = Arrangement.spacedBy(12.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(
                "RETURN THE RHYTHM\nBY FEEL",
                size = 6.5, color = Amber.Faint, tracking = 0.14, lineHeight = 2.0,
                align = TextAlign.Center,
            )
            SequenceCells(entry, lit = Amber.Dim, unlit = Amber.Edge)
            HandedOverLine(entry.handedOver, Amber.Faint)
        }

        MotionBudgetRow(Amber.Faint, Amber.Dim)

        // The whole input surface of this Subroutine: one target, as large as the screen can make
        // it, because it is pressed by somebody who is not looking at it. It goes inert once the
        // rhythm has gone back -- a control that accepts taps it discards is worse than one that
        // does not move, and this is the one screen where the player has no way to see that it did.
        PanelButton(
            "TAP",
            border = if (entry.handedOver) Amber.Edge else Amber.Faint,
            ink = if (entry.handedOver) Amber.Faint else Amber.Dim,
            size = 16.0, tracking = 0.2, verticalPadding = 22.u,
            onClick = if (entry.handedOver) null else {
                { actions.tapSubroutine(Subroutine.Handshake, entry.entered.size) }
            },
        )

        StopNow(Amber.Edge, Amber.Faint)
    }
}

/**
 * **Replay — 3–5 dots flash in order; tap them back. Bright for its whole duration.**
 *
 * The canonical case of the rule that saves the `ui ↛ core` edge: *lighting the dot you just
 * touched reflects your own input rather than simulating anything* (D-042). A tapped dot lights
 * and stays lit; the row of cells beneath counts how far through the sequence you are. Neither is
 * a claim that you are tapping the right dots, and there is nothing on this device that could make
 * one.
 *
 * **The flash is missing and its absence is deliberate.** The dots flashing in order is the house
 * emitting light on a schedule, which is an Effect, and the loop is frozen — so this screen is the
 * half of Replay that belongs to the phone. On a device with no house attached there is nothing to
 * remember, which is exactly what a Subroutine screen with no pattern behind it should look like.
 *
 * Two columns rather than a row: a row implies a reading order, and the Subroutine is *order*, so
 * the layout must not answer it for free. Dots are drawn at [DOT] — a deliberate over-size for a
 * target pressed one-handed, in the dark, by somebody watching a doorway.
 */
@Composable
fun ReplayScreen() {
    val entry = LocalSubroutine.current.replay
    val actions = LocalActions.current
    SubroutineFrame(Subroutine.Replay, ink = Amber.Dim, mark = Amber.Bright) {
        Label(
            "TAP THE DOTS BACK\nIN THE ORDER THEY FLASHED",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Dim, tracking = 0.12, lineHeight = 1.8,
            align = TextAlign.Center,
        )

        Column(
            Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge).padding(10.u),
            verticalArrangement = Arrangement.spacedBy(10.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (pair in (0 until entry.length).chunked(2)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (at in pair) {
                        val touched = entry.holds(at)
                        Box(
                            Modifier.size(DOT)
                                .background(
                                    if (touched) Amber.Bright else Color.Transparent, CircleShape,
                                )
                                .border(
                                    1.u, if (touched) Amber.Bright else Amber.Dim, CircleShape,
                                )
                                .then(
                                    if (entry.handedOver) Modifier
                                    else Modifier.tap {
                                        actions.tapSubroutine(Subroutine.Replay, at)
                                    }
                                )
                        )
                    }
                }
            }
            SequenceCells(entry, lit = Amber.Bright, unlit = Amber.Edge)
            HandedOverLine(entry.handedOver, Amber.Dim)
        }

        MotionBudgetRow(Amber.Dim, Amber.Bright)
        StopNow(Amber.Faint, Amber.Dim)
    }
}

/**
 * **Parity Check — a grid of filled and empty cells, and one of them breaks the pattern.**
 *
 * Lit for its whole duration, and that is the point of having it: if every Subroutine could be
 * done in the dark, choosing a dark one would itself be a choice worth reading. The bright ones
 * are what make standing lit at a marker ordinary.
 *
 * **This replaces a fixture that was playing a different game.** The ported screen said FIND EVERY
 * CORRUPTED BLOCK over a grid with seven corrupt cells and four already found, and drew the
 * unfound ones *lighter than the clean ones* — which showed the answer in a Subroutine whose whole
 * content is looking for it. The roster's Parity Check is singular: **tap the one breaking the
 * pattern.** The grid is now generated ([ParityGrid]), so the pattern is a real checkerboard with
 * exactly one real violation, and the generator forgets which cell it flipped before it returns.
 *
 * ### One answer, changeable until SUBMIT — the vote's shape, for the vote's reason
 *
 * A tap that went straight to the house would make a mis-touch final, in a game whose input
 * vocabulary is explicitly no twitch timing and no precise dragging, played standing up in an
 * unlit room. So a tap moves the mark and SUBMIT hands it over, exactly as a name and LOCK IN do
 * at a meeting. SUBMIT is drawn inert rather than hidden when there is nothing to send: the layout
 * must not move under a thumb.
 *
 * **The mark is a ring, not a fill.** Half the cells are already filled, so a mark made of fill
 * would be invisible on half the grid — and worse, would read as *this cell is filled* rather than
 * *this is the cell I chose*.
 */
@Composable
fun ParityCheckScreen() {
    val entry = LocalSubroutine.current.parity
    val actions = LocalActions.current
    val cells = ParityGrid.of(SubroutineModel.PARITY_SEED)
    SubroutineFrame(Subroutine.ParityCheck, ink = Amber.Dim, mark = Amber.Bright) {
        Label(
            "FIND THE BLOCK THAT BREAKS THE PATTERN",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Dim, tracking = 0.12, align = TextAlign.Center,
        )

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.u),
        ) {
            for (row in cells.indices.chunked(ParityGrid.COLUMNS)) {
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.u),
                ) {
                    for (at in row) {
                        val held = entry.holds(at)
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (cells[at]) Amber.Mid else Amber.Well)
                                .border(
                                    if (held) 2.u else 1.u,
                                    if (held) Amber.Bright else Amber.Faint,
                                )
                                .then(if (held) Modifier.testTag(PARITY_MARK) else Modifier)
                                .then(
                                    if (entry.handedOver != null) Modifier
                                    else Modifier.tap {
                                        actions.tapSubroutine(Subroutine.ParityCheck, at)
                                    }
                                )
                        )
                    }
                }
            }
        }

        MotionBudgetRow(Amber.Dim, Amber.Bright)

        PanelButton(
            if (entry.locked) "SUBMITTED" else "SUBMIT",
            border = if (entry.choice == null || entry.locked) Amber.Faint else Amber.Dim,
            ink = if (entry.choice == null || entry.locked) Amber.Dim else Amber.Bright,
            tracking = 0.18, verticalPadding = 9.u,
            onClick = if (entry.choice == null || entry.locked) {
                null
            } else {
                { actions.handOverSubroutine(Subroutine.ParityCheck) }
            },
        )

        StopNow(Amber.Faint, Amber.Dim)
    }
}

/**
 * **Short — hold this many fingers until it goes. Near-black, and both hands on the glass.**
 *
 * The design's *gross motor* Subroutine and the roster's other short dark one: brief, but
 * genuinely defenceless, because for two seconds you are stationary with your hands full and a
 * phone that is showing you almost nothing about the room.
 *
 * ### The hold ends on the clock and not on your hand, and everything else follows from that
 *
 * A hold that completed when the *asked-for* number of fingers arrived would be the phone grading
 * the answer — and grading it visibly, since a wrong hand would sit there while a right one went.
 * So the two seconds run against whatever is on the glass and the entry goes with whatever was
 * there. See [HoldEntry].
 *
 * ### Two rows of cells, and no numeral on either
 *
 * ASKS is the house's instruction; DOWN is this phone's own echo of your hand. They are separate
 * rows rather than one row lit part-way for a reason that matters more here than anywhere: a
 * single row that filled exactly when you had it right would be a verdict drawn as a diagram.
 * Two lengths, side by side, is *comparing quantities is perception* — the bench's own rule, and
 * the reason there is no digit anywhere on this screen.
 *
 * ### There is no progress bar, and that is a decision rather than an omission
 *
 * Two seconds of a bar growing is a two-second ramp of light on the roster's darkest screen — the
 * one built to keep a player invisible while they work. The screen says HOLD UNTIL IT RETURNS so
 * the player knows what they are waiting for, and the thing they are waiting for is the same
 * RETURNED . WAITING line the other five use.
 */
@Composable
fun ShortScreen() {
    val entry = LocalSubroutine.current.short
    val actions = LocalActions.current
    SubroutineFrame(Subroutine.Short, ink = Amber.Dim, mark = Amber.Faint) {
        HoldSurface(
            live = entry.handedOver == null,
            onFingers = { actions.tapSubroutine(Subroutine.Short, it) },
            onHeld = { actions.handOverSubroutine(Subroutine.Short) },
            modifier = Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge).padding(10.u),
        ) {
            Label(
                "HOLD THIS MANY FINGERS\nUNTIL IT RETURNS",
                size = 6.5, color = Amber.Faint, tracking = 0.14, lineHeight = 2.0,
                align = TextAlign.Center,
            )
            CountRow("ASKS", SubroutineModel.SHORT_FINGERS, Amber.Faint, Amber.Dim)
            CountRow("DOWN", entry.handedOver ?: entry.fingers, Amber.Faint, Amber.Dim)
            HandedOverLine(entry.handedOver != null, Amber.Faint)
        }

        MotionBudgetRow(Amber.Faint, Amber.Dim)
        StopNow(Amber.Edge, Amber.Faint)
    }
}

/**
 * **Jam — two rings, and you walk one of them onto the other.**
 *
 * The design's *convergence* Subroutine: *tap +/− until two shapes overlap. Slow, forgiving,
 * satisfying.* Which two shapes and how far a tap moves are both left open, so both are decided
 * here and flagged.
 *
 * ### Two rings converging in size, rather than two shapes sliding together
 *
 * The input vocabulary at four luminance steps of amber is position, shape, count, size, order,
 * presence and slow motion. Two concentric rings closing on one another use **size alone**, with
 * both shapes sharing a centre — so there is nothing to track across the screen, nothing that goes
 * off the edge, and no direction to get confused about in the dark. It is also the version that
 * has the moment the design asks for: the last press puts one ring exactly on the other and two
 * things become one thing.
 *
 * The fixed ring is drawn thicker and dimmer, the player's ring thinner and brighter, so at the
 * moment they coincide the bright one sits inside the dim one's stroke and reads as a single
 * ring with a halo rather than as one ring that ate the other.
 *
 * **Neither the screen nor the entry knows how far apart they are.** [ScalarEntry] holds the net
 * steps pressed and nothing else; the gap is a thing the eye measures.
 */
@Composable
fun JamScreen() {
    val entry = LocalSubroutine.current.jam
    val actions = LocalActions.current
    val live = entry.handedOver == null
    SubroutineFrame(Subroutine.Jam, ink = Amber.Dim, mark = Amber.Mid) {
        Label(
            "TAP UNTIL THE RINGS SIT ON ONE ANOTHER",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Dim, tracking = 0.12, align = TextAlign.Center,
        )

        Box(
            Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(JAM_TARGET).border(2.u, Amber.Dim, CircleShape))
            Box(
                Modifier
                    .size(JAM_OPENING + JAM_STEP * entry.offset)
                    .border(1.u, Amber.Mid, CircleShape),
            )
        }

        // Two targets, as large as the row can make them, because they are pressed many times by
        // somebody who is not looking down. The glyphs are shapes rather than words: at the
        // dimmest step a minus is a bar and a plus is a cross, and neither has to be read.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.u)) {
            for ((glyph, by) in listOf("-" to -1, "+" to 1)) {
                Box(Modifier.weight(1f)) {
                    PanelButton(
                        glyph,
                        border = if (live) Amber.Dim else Amber.Edge,
                        ink = if (live) Amber.Mid else Amber.Faint,
                        size = 14.0, tracking = 0.0, verticalPadding = 11.u,
                        onClick = if (live) {
                            { actions.tapSubroutine(Subroutine.Jam, by) }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        MotionBudgetRow(Amber.Dim, Amber.Mid)
        // Live with nothing pressed: an inert SUBMIT would be the phone saying the opening
        // position is not the answer, and it is not the phone's to know. See ScalarEntry.
        SubmitButton(Subroutine.Jam, sent = entry.gone, enabled = live)
        StopNow(Amber.Faint, Amber.Dim)
    }
}

/**
 * **Signal Trace — tap node to node, from the disc to the target.**
 *
 * The design's *pathfinding* Subroutine, shipping with generated graphs so that the optimum is
 * computed rather than authored ([SignalGraph]).
 *
 * ### The screen accepts a move the graph does not have, and that is rule 1
 *
 * Tapping a node that is not joined to the one you are standing on lands, lights, and goes into
 * the route. Refusing it would be a screen telling a player their move was illegal — the house's
 * answer, arriving from the phone, on the only Subroutine where the device would otherwise have an
 * opinion about how the work is going. *Never early-return on invalid: the absent effect is the
 * leak.* The route goes back and the house decides whether it was a route.
 *
 * ### Nodes light; edges never do
 *
 * A walked *edge* lighting up would be the screen confirming that two nodes really are joined,
 * which is the same tell wearing a line instead of a word. So the wiring is drawn at one intensity
 * throughout and only the nodes you touched change — which is your own input and nothing else.
 */
@Composable
fun SignalTraceScreen() {
    val entry = LocalSubroutine.current.trace
    val actions = LocalActions.current
    val live = entry.handedOver == null
    val wiring = SignalGraph.of(SubroutineModel.TRACE_SEED)
    SubroutineFrame(Subroutine.SignalTrace, ink = Amber.Dim, mark = Amber.Mid) {
        Label(
            "TAP NODE TO NODE\nFROM THE DISC TO THE TARGET",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Dim, tracking = 0.12, lineHeight = 1.8,
            align = TextAlign.Center,
        )

        Box(
            Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(GRAPH_WIDTH, GRAPH_HEIGHT)) {
                // The wiring, behind the nodes and at one intensity: see the note above about why
                // a walked edge must not light.
                Canvas(Modifier.matchParentSize()) {
                    for ((a, b) in wiring.edges) {
                        drawLine(
                            color = Amber.Faint,
                            start = nodeCentre(wiring.nodes[a]),
                            end = nodeCentre(wiring.nodes[b]),
                            strokeWidth = 2.u.toPx(),
                        )
                    }
                }
                for (at in wiring.nodes.indices) {
                    val node = wiring.nodes[at]
                    val walked = entry.holds(at)
                    Box(
                        Modifier
                            .offset(
                                x = NODE_INSET + COLUMN_STEP * node.column - NODE / 2,
                                y = NODE_INSET + ROW_STEP * node.row - NODE / 2,
                            )
                            .size(NODE)
                            .background(
                                if (at == wiring.source) Amber.Dim else Color.Transparent,
                                CircleShape,
                            )
                            .border(
                                if (walked) 3.u else 1.u,
                                if (walked) Amber.Bright else Amber.Dim,
                                CircleShape,
                            )
                            .then(
                                if (live) {
                                    Modifier.tap {
                                        actions.tapSubroutine(Subroutine.SignalTrace, at)
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The sink wears a second ring inside the first: a target, and the only
                        // node that is neither filled nor plain. Direction has to be visible or
                        // "from the disc to the target" is two instructions with one picture.
                        if (at == wiring.sink) {
                            Box(Modifier.size(NODE / 3).background(Amber.Dim, CircleShape))
                        }
                    }
                }
            }
        }

        MotionBudgetRow(Amber.Dim, Amber.Mid)
        // Inert with an empty route, which is the refusal LOCK IN makes on an empty ballot: a
        // control that sends nothing and looks like it sent something is a phone lying about what
        // the house holds. Nothing about *which* route, which is why Jam's is live from the start.
        SubmitButton(Subroutine.SignalTrace, sent = entry.gone, enabled = live && entry.touched)
        StopNow(Amber.Faint, Amber.Dim)
    }
}

// ---- The furniture every Subroutine screen shares ------------------------------------------

/** How big a Replay dot is. Named because it is a decision about a finger, not a layout number. */
private val DOT: Dp = 44.u

/**
 * **Jam's three sizes, and the step is a placeholder.**
 *
 * [JAM_TARGET] is the fixed ring. [JAM_OPENING] is where the house opens the player's ring — well
 * clear of the target, so the Subroutine starts with something visibly to do. [JAM_STEP] is how
 * much one press moves it, and is the **presentation fixture** of this screen in the way
 * `HANDSHAKE_BEATS` is of Handshake's: it decides how many presses the work takes, which is
 * difficulty, which is balance, which nobody has played with. Six units is a step you can see
 * land at arm's length in an unlit room, and the opening is a whole number of them from the
 * target so the rings can be brought exactly together.
 */
private val JAM_TARGET: Dp = 84.u
private val JAM_STEP: Dp = 6.u
private val JAM_OPENING: Dp = 150.u

/**
 * **Signal Trace's lattice, in design units.**
 *
 * [NODE] is 36 units because that is [TAP_TARGET] — a node is a control pressed one-handed in the
 * dark by somebody watching a doorway, and the graph is the one screen in this game where two
 * targets sit close enough that a near miss lands on the wrong one. Everything else is derived
 * from it: the lattice is as wide as the panel's body allows and as tall as leaves the instruction
 * and the two buttons their room.
 */
/**
 * How many finger-cells Short's two rows keep room for, so neither row's cells move when the
 * count changes. A hand, because a hand is what is on the glass.
 */
private const val MOST_FINGERS = 5

private val NODE: Dp = 36.u
private val COLUMN_STEP: Dp = 74.u
private val ROW_STEP: Dp = 70.u
private val NODE_INSET: Dp = NODE / 2
private val GRAPH_WIDTH: Dp = NODE + COLUMN_STEP * (SignalGraph.COLUMNS - 1)
private val GRAPH_HEIGHT: Dp = NODE + ROW_STEP * (SignalGraph.ROWS - 1)

/** Where a node's centre falls inside the graph box. The one place the lattice becomes a picture. */
private fun DrawScope.nodeCentre(node: SignalGraph.Node): Offset = Offset(
    x = (NODE_INSET + COLUMN_STEP * node.column).toPx(),
    y = (NODE_INSET + ROW_STEP * node.row).toPx(),
)

/** What the chosen parity cell tags itself with, so a guard counts marks rather than pixels. */
const val PARITY_MARK: String = "subroutine-mark"

/**
 * What Short's hold surface tags itself with.
 *
 * It publishes no click action on purpose, so a test cannot find it the way it finds every other
 * control in this app — and finding it by the words on it would be finding the label, which is not
 * the thing several fingers have to land inside.
 */
const val HOLD_SURFACE: String = "subroutine-hold"

/**
 * The header every Subroutine screen wears: its name, and what it will do to the lamp.
 *
 * **One fixed slot for the light mark, on every Subroutine screen** (D-106). It is the same place
 * on the near-black one and on the lit ones, which is what makes it a thing a player recognises
 * rather than reads — HANDSHAKE shows one faint cell and PARITY CHECK three bright ones, in the
 * same corner, at the same size.
 *
 * The name comes off the roster rather than out of a string, so a screen cannot be titled with a
 * Subroutine whose light it is not showing.
 */
@Composable
private fun SubroutineFrame(
    subroutine: Subroutine,
    ink: Color,
    mark: Color,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(subroutine.label, size = 7.0, color = ink, tracking = 0.14)
            LightMark(subroutine.light, mark, cell = 5.u, height = 8.u)
        }
        body()
    }
}

/**
 * How much of the sequence has gone back, as cells.
 *
 * One cell per element the house asked for, lit up to what this phone has entered — the same
 * grammar as [LightMark] one row above it, and the bench's own rule applied: *comparing quantities
 * is perception; adding numbers is computation*, so any Subroutine showing a numeral is suspect.
 * The ported fixtures both printed one (`3 OF 5 RETURNED`, `FOUND 4 OF 7`) beside a display that
 * was already carrying the same fact.
 *
 * **It counts and it does not judge.** A cell lights because a finger landed, not because the
 * finger landed in the right place — see [SequenceEntry].
 */
@Composable
private fun SequenceCells(entry: SequenceEntry, lit: Color, unlit: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(entry.length) { at ->
            Block(11.u, 8.u, if (at < entry.entered.size) lit else unlit)
        }
    }
}

/**
 * A named quantity, drawn as cells — the row grammar with the numeral taken out.
 *
 * *Comparing quantities is perception; adding numbers is computation*, so a count that a player
 * has to hold against another count is drawn as a length rather than written as a digit. Used on
 * Short for the two counts that matter, one above the other, so the comparison is a glance.
 */
@Composable
private fun CountRow(label: String, count: Int, ink: Color, lit: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A fixed slot for the word, so the two rows' cells start at the same place and the
        // comparison is a length against a length rather than two rows of ragged left edges.
        Label(label, modifier = Modifier.width(26.u), size = 6.0, color = ink, tracking = 0.12)
        Row(
            Modifier.width(11.u * MOST_FINGERS + 4.u * (MOST_FINGERS - 1)).height(9.u),
            horizontalArrangement = Arrangement.spacedBy(4.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { Block(11.u, 8.u, lit) }
        }
    }
}

/**
 * **The surface Short is held with: the panel itself, counting the fingers on it.**
 *
 * Two pieces of state that look like one and are not. [down] is how many pointers the *glass*
 * currently reports, and it exists to drive the clock; the entry beside it is the *echo*, and it
 * is what gets drawn. They are the same number in a player's hand, and they are deliberately not
 * the same variable: the clock must only ever be started by a finger, so a fixture that puts a
 * count into the entry — every render, every parity comparison — moves the cells on the screen and
 * starts nothing.
 *
 * The hold restarts whenever the number of fingers changes, because *hold N fingers for two
 * seconds* is a hold of an unchanging hand: fingers arriving one at a time each begin it again,
 * which is what the gesture is.
 *
 * **It publishes no click action**, exactly as [HoldToConfirm] does not — a hold a single
 * synthetic click could fire would not be a hold — and it walks no edge, so there is nothing for
 * `ScreenGraphTest` to miss. `ShortInputTest` drives real pointers, several at once, against a
 * clock it controls.
 */
@Composable
private fun HoldSurface(
    live: Boolean,
    onFingers: (Int) -> Unit,
    onHeld: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var down by remember { mutableStateOf(0) }

    LaunchedEffect(down, live) {
        if (down == 0 || !live) return@LaunchedEffect
        var last = withFrameMillis { it }
        var spent = 0L
        while (spent < HOLD_MILLIS) {
            val now = withFrameMillis { it }
            spent += now - last
            last = now
        }
        onHeld()
    }

    Column(
        modifier.testTag(HOLD_SURFACE).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (pressed != down) {
                        down = pressed
                        onFingers(pressed)
                    }
                }
            }
        },
        verticalArrangement = Arrangement.spacedBy(12.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = body,
    )
}

/**
 * SUBMIT, on the Subroutines whose answer can still change — and SUBMITTED once it cannot.
 *
 * Drawn inert rather than hidden when there is nothing to send: the layout must not move under a
 * thumb. Whether it is live at all is the caller's, because the reasons differ and the difference
 * is the interesting part — see the notes at the two call sites.
 */
@Composable
private fun SubmitButton(subroutine: Subroutine, sent: Boolean, enabled: Boolean) {
    val actions = LocalActions.current
    PanelButton(
        if (sent) "SUBMITTED" else "SUBMIT",
        border = if (enabled) Amber.Dim else Amber.Faint,
        ink = if (enabled) Amber.Bright else Amber.Dim,
        tracking = 0.18, verticalPadding = 9.u,
        onClick = if (enabled) {
            { actions.handOverSubroutine(subroutine) }
        } else {
            null
        },
    )
}

/**
 * **The one line that says the phone is no longer waiting for you.**
 *
 * A fixed slot whether or not there is anything in it, so the cells above do not move when the
 * last element goes in — a layout that jumps at the moment of completion is a change in lit area
 * that nobody authored, on the frame where the player is most likely to be looking away.
 *
 * *Waiting* is the honest word and the only one available: whether the sequence was right is the
 * house's answer, it has not arrived, and the device has no opinion to offer in the meantime.
 */
@Composable
private fun HandedOverLine(handedOver: Boolean, color: Color) {
    Box(Modifier.height(9.u), contentAlignment = Alignment.Center) {
        if (handedOver) {
            Label("RETURNED . WAITING", size = 6.0, color = color, tracking = 0.12)
        }
    }
}

/**
 * The motion budget, as a readout rather than as a bar.
 *
 * It calibrates on entry to a Subroutine against that player's own hand steadiness, and it is the
 * **one** thing in the game a client adjudicates (D-043) — 100 Hz cannot round-trip and the
 * failure has to be immediate. None of that exists yet: there is no motion on a desktop render and
 * no phone attached, so this states the condition and does not draw a draining bar. **A bar that
 * moved here would be moving on nothing**, which on the one client-adjudicated value in the game
 * is the worst place to put a decorative animation.
 */
@Composable
private fun MotionBudgetRow(ink: Color, value: Color) {
    Row(
        Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label("MOTION BUDGET", size = 6.0, color = ink, tracking = 0.1)
        Label("HOLDING", size = 6.0, color = value, tracking = 0.1)
    }
}

/**
 * **STOP NOW — the same words, in the same place, on every Subroutine screen.**
 *
 * Somebody walked in. The player is heads-down, lit or blind, both hands occupied, and needs to be
 * out of this screen without reading it: so the control is constant across all three, keeps its
 * words whether or not the entry has gone back, and sits where the scan's own STOP NOW sits. In
 * the dark that is muscle memory, and muscle memory is the only interface that works when you are
 * not looking at the phone.
 *
 * It goes to the work order rather than the springboard — you came from the list and the list is
 * where the next decision is — which is where NOT THIS ONE goes from the screen before this one.
 *
 * **It is not a claim about the Subroutine.** Walking away from work is your own phone's business;
 * whether anything was banked is the house's, and this button neither asks nor says.
 */
@Composable
private fun StopNow(border: Color, ink: Color) {
    val go = navigator()
    PanelButton(
        "STOP NOW",
        border = border, ink = ink,
        size = 7.0, verticalPadding = 9.u,
        onClick = { go(ScreenId.Work) },
    )
}
