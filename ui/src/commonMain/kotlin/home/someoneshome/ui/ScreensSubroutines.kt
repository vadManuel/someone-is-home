package home.someoneshome.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import home.someoneshome.model.DotColumns
import home.someoneshome.model.DriftPath
import home.someoneshome.model.InterruptSweep
import home.someoneshome.model.ParityGrid
import home.someoneshome.model.SignalGraph

/**
 * **The Subroutines themselves — all ten of the design's, with their interaction.**
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
 * [PanelState.role], or branches on one — **including [SniffScreen], whose whole palette turns on
 * one question and that question is *has this phone answered yet***, so an Insider's Handshake *is*
 * a Resident's Handshake:
 * one screen, one code path, nothing to keep in step and nothing to get wrong at four in the
 * morning six months from now. `SubroutineParityTest` renders both roles through the identical
 * input and compares the pixels.
 *
 * ### What is here, and what is the house's
 *
 * **Every question on these screens is the house's** (D-238): the scan's `SubroutineInstance`
 * carries its parameters, `FlowModel.beginSubroutine` deals them into [SubroutineModel], and each
 * screen draws from the model's slot for its kind. The companion's fixtures are what a phone with
 * no house draws, which is the render harness and nothing else. Three of the ten put their question
 * in a channel a desktop render cannot show:
 *
 * - **Replay's dots flash in order**, played by [ReplayScreen] off the house's pattern and the
 *   screen's own clock — D-139's arrangement, with no Effect on the wire.
 * - **Handshake's rhythm is buzzed**, by `HouseBuzzer` off the same instance. Built: the return,
 *   with as many beats as the house dealt, each tap timed on this phone's monotonic clock and the
 *   gaps graded by the house (D-296).
 * - **Short's asked-for count is the house's**, drawn as the ASKS row. The *gesture* is whole:
 *   several fingers, two seconds, and a hand-over.
 * - **Sniff's two groups are buzzed**, for Handshake's reason and with Handshake's consequence: on
 *   a build with no phone attached the question never arrives, and the screen is black until
 *   somebody taps it. Built: the answer, and the darkness it is given in.
 * - **Drift's *now* is buzzed**, which is Handshake's gap again — the pulse is a step in
 *   [DriftPath.script] and nothing on a bench feels it. Built: the motion it interrupts, and the
 *   answer.
 * - **Parity Check, Jam, Signal Trace and Deallocate have no timed presentation at all**, which is
 *   why those four are whole: the grid, the rings, the wiring and the columns are on the screen,
 *   the work is what you do to them, and the only thing the house owns is the verdict.
 *
 * ### None of them says what to do, and that is D-188 rather than an omission
 *
 * Every screen in this file used to carry its instruction across the top — *TAP TO CATCH THE SWEEP
 * INSIDE THE BAND*, *RETURN THE RHYTHM BY FEEL*. **All nine are gone.** The instruction is read once,
 * at the caught scan, above BEGIN, where the scan has already lit the player up and the cost is
 * already paid; a line drawn here is lit pixels for the whole duration of a task rated on how little
 * it may glow, which makes a dark Subroutine less dark and a bright one longer. The words live on
 * [Subroutine.briefing], which is total over the roster so that a screen cannot be added without one.
 *
 * **A misread costs the attempt and a rescan** (D-110). That is the intended severity: there is one
 * recovery path in this game and skimming in the dark uses it, exactly as fumbling does.
 *
 * ### Two of them move, and the motion is arithmetic the house authored
 *
 * Interrupt's sweep and Drift's dot are the only things in this module that move on their own, and
 * neither is an animation in the sense rule 5 forbids: **the house sends the parameters at scan
 * time and the client renders the position from them and the milliseconds since the screen opened**
 * (D-139, D-140). No tween, no easing, no Effect on the wire, no schema row — and the same instance
 * on the same clock draws the same frame on every phone and on every replay. See [screenMillis] for
 * where the milliseconds come from and why they are not a wall clock.
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
fun HandshakeScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.handshake
    val actions = LocalActions.current
    val p = Palette.CONCEAL
    SubroutineFrame(Subroutine.Handshake, p, entry.handedOver, verdict) {
        BeatCells(entry)

        // The whole input surface of this Subroutine: one target, as large as the body can make
        // it, because it is pressed by somebody who is not looking at it. It goes inert once the
        // rhythm has gone back -- a control that accepts taps it discards is worse than one that
        // does not move, and this is the one screen where the player has no way to see that it did.
        // Inert, it loses its outline: the word stays, because D-202 has no dimmer text to give it.
        Box(
            Modifier.fillMaxWidth().height(180.dp)
                .then(if (entry.handedOver) Modifier else Modifier.border(1.dp, p.faint))
                .then(
                    if (entry.handedOver) Modifier
                    else Modifier.tap { actions.tapSubroutine(Subroutine.Handshake, entry.taps) }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Words("TAP", size = 40, color = p.ink, tracking = 0.2, lineHeight = 1.0)
        }
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
 * ### The flash is the house's pattern, played by the screen from the scan (D-238)
 *
 * The dots flash in the order [SubroutineModel.pattern] holds, once, from the moment the screen
 * opens: D-139's arrangement, one Subroutine along — **the house sends the question at scan time
 * and the client renders it deterministically** from the parameters and [screenMillis], with no
 * Effect on the wire and no schema row. Until D-238 nothing flashed at all and the screen drew the
 * port's four dots, so a player stood at a card the house had dealt a pattern at, shown no pattern,
 * and was graded against it.
 *
 * **The schedule is a placeholder and playtest owns it** — [REPLAY_LEAD_MILLIS], [REPLAY_LIT_MILLIS]
 * and [REPLAY_STEP_MILLIS]. It is the same for every phone and both roles, because it is a function
 * of the pattern and the screen's own clock and of nothing else. Taps land throughout: refusing one
 * during the playback would be the phone holding an opinion about when the work may start.
 *
 * Two columns rather than a row: a row implies a reading order, and the Subroutine is *order*, so
 * the layout must not answer it for free. Dots are drawn at [DOT] — a deliberate over-size for a
 * target pressed one-handed, in the dark, by somebody watching a doorway.
 */
@Composable
fun ReplayScreen(verdict: SubroutineVerdict? = null) {
    val model = LocalSubroutine.current
    val entry = model.replay
    val order = model.pattern.order
    val actions = LocalActions.current
    val p = Palette.DARK
    val elapsed = screenMillis(
        running = !entry.handedOver, instance = model.instance, until = replayPlayedMillis(order),
    )
    // The dot the house's pattern has lit at this moment, or none. A sequence handed over has
    // nothing left to show, so the playback stops with it rather than freezing a dot lit.
    val flashing = if (entry.handedOver) null else replayFlashing(order, elapsed)
    SubroutineFrame(Subroutine.Replay, p, entry.handedOver, verdict) {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            for (pair in (0 until entry.length).chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                    for (at in pair) {
                        val touched = entry.holds(at) || at == flashing
                        Box(
                            Modifier.size(DOT)
                                .background(if (touched) p.ink else Color.Transparent, CircleShape)
                                .border(2.dp, p.ink, CircleShape)
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(entry.length) { at ->
                Cell(28.dp, 12.dp, edge = p.ink, fill = if (at < entry.entered.size) p.ink else null)
            }
        }
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
fun ParityCheckScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.parity
    val actions = LocalActions.current
    // The house's grid (D-238): the cell the house grades is the one that breaks THIS pattern.
    val cells = LocalSubroutine.current.grid
    val p = Palette.DARK
    val live = entry.choice != null && !entry.locked
    SubroutineFrame(
        Subroutine.ParityCheck, p, entry.gone, verdict,
        submit = {
            SubmitButton(if (entry.locked) "SUBMITTED" else "SUBMIT", p, live) {
                actions.handOverSubroutine(Subroutine.ParityCheck)
            }
        },
    ) {
        // Square, and as large as the body allows: the mockup's `aspect-ratio: 1` at full width,
        // shrunk to the body's height on a panel too short to take it.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Column(Modifier.size(side), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in cells.indices.chunked(ParityGrid.COLUMNS)) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (at in row) {
                            val held = entry.holds(at)
                            val lit = cells[at]
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (lit) p.ink else Ramp.Fill)
                                    .border(if (held) 3.dp else 1.dp, if (held || lit) p.ink else p.faint)
                                    // On a lit cell a ring in ink is ink on ink. A black line
                                    // inside it is what lets the ring read there too.
                                    .then(if (held && lit) Modifier.padding(3.dp).border(3.dp, Amber.Black) else Modifier)
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
        }
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
fun ShortScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.short
    val actions = LocalActions.current
    val p = Palette.CONCEAL
    // The house's ask (D-238), off the model and never off the entry -- see [HoldEntry].
    val asks = LocalSubroutine.current.hold.fingers
    val down = entry.handedOver ?: entry.fingers
    // Both rows keep the same number of slots, so their cells line up and the comparison is a
    // length against a length. A hand is five; more fingers than that widen both rows together
    // rather than drawing fewer than were sent.
    val slots = maxOf(MOST_FINGERS, asks, down)
    SubroutineFrame(
        Subroutine.Short, p, entry.handedOver != null, verdict,
        bodyModifier = holdSurface(
            live = entry.handedOver == null,
            onFingers = { actions.tapSubroutine(Subroutine.Short, it) },
            onHeld = { actions.handOverSubroutine(Subroutine.Short) },
        ),
    ) {
        CountRow("ASKS", asks, slots, p)
        CountRow("DOWN", down, slots, p)
        Box(Modifier.fillMaxWidth().height(180.dp).border(1.dp, p.faint))
    }
}

/**
 * **Jam — two rings, and you walk one of them onto the other.**
 *
 * The design's *convergence* Subroutine: *tap +/− until two shapes overlap. Slow, forgiving,
 * satisfying.* Which two shapes and how far a tap moves are both left open, so both are decided
 * here and flagged.
 *
 * ### Two rings sitting apart along one row, as the mockup draws them
 *
 * The briefing says *TWO RINGS WILL SIT APART* (D-194), and the mockup draws exactly that: two
 * rings of one size side by side, the player's in the screen's ink and the fixed one in the line
 * value. **This replaced two concentric rings converging in size**, the port's own earlier answer
 * to a question the design had left open; the redesign answered it. The pair moves along one axis
 * only, so there is still no direction to get confused about in the dark, and the last press puts
 * one ring exactly on the other and two things become one thing.
 *
 * **Neither the screen nor the entry knows how far apart they are.** [ScalarEntry] holds the net
 * steps pressed and nothing else; the gap is a thing the eye measures.
 */
@Composable
fun JamScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.jam
    val actions = LocalActions.current
    // Where the house opened the player's ring, in steps. Drawn from the number that will be
    // graded, so the press that puts one ring exactly on the other is the press the house is
    // waiting for -- the band's arrangement, one Subroutine along (E-S2-1).
    val rings = LocalSubroutine.current.rings
    val live = entry.handedOver == null
    val p = Palette.MEDIUM
    SubroutineFrame(
        Subroutine.Jam, p, entry.gone, verdict,
        // Live with nothing pressed: an inert SUBMIT would be the phone saying the opening
        // position is not the answer, and it is not the phone's to know. See ScalarEntry.
        submit = {
            SubmitButton(if (entry.gone) "SUBMITTED" else "SUBMIT", p, live) {
                actions.handOverSubroutine(Subroutine.Jam)
            }
        },
    ) {
        JamRow(rings.opening, entry.offset, p)

        // Two targets, as large as the row can make them, because they are pressed many times by
        // somebody who is not looking down. The glyphs are shapes rather than words: a minus is a
        // bar and a plus is a cross, and neither has to be read.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((glyph, by) in listOf("-" to -1, "+" to 1)) {
                Box(
                    Modifier.weight(1f).height(56.dp).border(1.dp, if (live) p.line else p.faint)
                        .then(if (live) Modifier.tap { actions.tapSubroutine(Subroutine.Jam, by) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Words(glyph, size = 36, color = if (live) p.ink else p.dim, lineHeight = 1.0)
                }
            }
        }
    }
}

/**
 * **Signal Trace — tap node to node, from one marked end to the other.**
 *
 * **The two ends are drawn alike**, as the mockup draws them: filled, and nothing else is. The house
 * accepts the route traced from either one (D-211), so neither end may look like the start; a
 * screen that marked a source would be asking a question the house no longer grades.
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
 * ### The route is drawn as tapped, and the wiring never lights
 *
 * The mockup echoes a route as a heavier line from node to node. Here that line is drawn between
 * **consecutive taps, whether or not the wiring joins them**, so it is the player's input and
 * nothing else: a leg drawn only along real wiring would be the screen confirming that two nodes
 * are joined, which is the tell this note used to forbid by drawing no legs at all. The wiring stays
 * at one value throughout, and a walked node's outline goes to full ink, so a first tap -- which has
 * no leg yet -- still echoes.
 */
@Composable
fun SignalTraceScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.trace
    val actions = LocalActions.current
    val live = entry.handedOver == null
    // The house's wiring (D-238): the route the house grades is the shortest one across THIS lattice.
    val wiring = LocalSubroutine.current.wiring
    val p = Palette.MEDIUM
    SubroutineFrame(
        Subroutine.SignalTrace, p, entry.gone, verdict,
        // Inert with an empty route, which is the refusal LOCK IN makes on an empty ballot: a
        // control that sends nothing and looks like it sent something is a phone lying about what
        // the house holds. Nothing about *which* route, which is why Jam's is live from the start.
        submit = {
            SubmitButton(if (entry.gone) "SUBMITTED" else "SUBMIT", p, live && entry.touched) {
                actions.handOverSubroutine(Subroutine.SignalTrace)
            }
        },
    ) {
        Box(Modifier.size(GRAPH_WIDTH, GRAPH_HEIGHT)) {
            Canvas(Modifier.matchParentSize()) {
                // The wiring, at one value throughout.
                for ((a, b) in wiring.edges) {
                    drawLine(
                        p.line, nodeCentre(wiring.nodes[a]), nodeCentre(wiring.nodes[b]),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                // The route, as tapped: one leg per consecutive pair, whether or not the wiring
                // joins them. It is the player's input and nothing else -- see the note above.
                for ((a, b) in entry.entered.zipWithNext()) {
                    drawLine(
                        p.ink, nodeCentre(wiring.nodes[a]), nodeCentre(wiring.nodes[b]),
                        strokeWidth = 4.dp.toPx(),
                    )
                }
            }
            for (at in wiring.nodes.indices) {
                val node = wiring.nodes[at]
                val walked = entry.holds(at)
                // The target is a full tap target; the node drawn in it is the mockup's 24.
                Box(
                    Modifier
                        .offset(
                            x = NODE_INSET + COLUMN_STEP * node.column - NODE_TARGET / 2,
                            y = NODE_INSET + ROW_STEP * node.row - NODE_TARGET / 2,
                        )
                        .size(NODE_TARGET)
                        .then(
                            if (live) Modifier.tap { actions.tapSubroutine(Subroutine.SignalTrace, at) }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Both ends filled, alike: the house grades the route from either (D-211).
                    val end = at == wiring.source || at == wiring.sink
                    Box(
                        Modifier.size(NODE)
                            .background(if (end) p.ink else Amber.Black)
                            .border(if (walked) 3.dp else 2.dp, if (walked) Ramp.Ink else p.ink),
                    )
                }
            }
        }
    }
}

/**
 * **Sniff — the screen that emits nothing, and the answer gesture that had to be invented for it.**
 *
 * D-137 supersedes `gdd.md:568`. The phone buzzes **two haptic groups separated by a pause** and
 * the player answers **which group was bigger**: pure perception, no numeral, no count to hold, no
 * rhythm. It is the roster's only *short dark* — the quick one a Resident can take without becoming
 * a beacon — and the ruling is unambiguous about what that costs: **the screen is fully dark until
 * the answer.**
 *
 * ### ⚠️ THE TREATMENT — the whole screen is drawn in black, and every control is where it always is
 *
 * *"Fully dark"* is taken literally. The layout below is built in full from the first frame — the
 * header, the two halves, the return slot, the motion row, SUBMIT, STOP NOW, all in their usual
 * places — and **every colour on it is [Amber.Black] until the player has answered.** Nothing is
 * hidden, nothing is added later, and **nothing moves at the moment it lights**: the difference
 * between the dark screen and the lit one is a palette and not a layout.
 *
 * That matters more than it sounds. A screen that *grew* its controls when the answer arrived would
 * be a change in lit area on the frame the player is most likely to be looking away from — and on
 * this Subroutine, which exists to keep somebody invisible, the arrival of light is the one thing a
 * person across the room can read.
 *
 * **The answer gesture is the two halves of the panel.** The left half is the group that buzzed
 * first, the right half is the one that buzzed second, and the tap target is half a phone: no
 * precision, no boundary to find, nothing to look at. Left-to-right is reading order and it is also
 * the order the two groups arrived in, which is the only mapping a player can be expected to hold
 * while feeling for a doorway. **A mark cannot be made on the darkest screen in the game, so the
 * screen answers instead**: the first tap is what lights it, and what it lights is which side the
 * finger landed on.
 *
 * **The answer is not sent by the tap.** A single choice takes SUBMIT — the vote's shape, for the
 * vote's reason — and here it earns it twice over: the tap is made blind, on a target the size of a
 * hand, by somebody who cannot see what they hit. So the tap moves the mark, the screen lights, and
 * the player confirms what it says. Tapping the other half moves it back, exactly as
 * [ParityCheckScreen]'s mark moves.
 *
 * ### STOP NOW is drawn in black and is live from the first frame
 *
 * Somebody walked in. The player is heads-down and blind, and the control is in the place it is on
 * every other Subroutine screen, with the same words, doing the same thing — it simply cannot be
 * seen. **In the dark that is what STOP NOW already was**: the reason it never changes is that it
 * is pressed by muscle memory rather than read. An invisible live control is the strongest form of
 * this file's rule, not an exception to it. Flagged, and it is the piece of this screen most worth
 * a person's judgement in an actual dark room.
 *
 * ### What the house owns, and what a phone with no house shows
 *
 * **The buzzing is the house's** — haptics are, and this build has no phone attached to feel them
 * on — so on a desktop render this screen is black and stays black until something taps it. That is
 * the honest picture of a Subroutine whose entire question arrives through a channel this build
 * does not have, and it is the same gap [HandshakeScreen] has.
 *
 * **There is no instruction on this screen and there cannot be one.** *Which group was bigger* is a
 * sentence made of light, and the ruling forbids light until the answer. The instruction lives on
 * the screen before this one — and D-188 has since made that true of the other nine as well, so
 * this screen is no longer the exception, it is the one that showed where the line belonged.
 */
@Composable
fun SniffScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.sniff
    val actions = LocalActions.current

    // The one thing that decides every colour below. A verdict lights the screen too, even with
    // nothing chosen: a screen that could not show the house's answer would be the device
    // withholding it, which is the opposite of what D-109 gave the house a verdict for.
    val lit = entry.choice != null || verdict != null
    // The concealment palette once lit, whose brightest value is `#C08A2E` -- the same ceiling
    // HANDSHAKE has. The dark rung is a claim about how much light a Subroutine makes a player
    // emit (D-106), and a screen that went dark for the perception and then flared for the answer
    // would be making that claim about half of itself. Before that, every role is black.
    val p = if (lit) Palette.CONCEAL else UNLIT
    val live = entry.choice != null && !entry.locked

    SubroutineFrame(
        Subroutine.Sniff, p, entry.gone, verdict,
        // Refused with nothing chosen, which is [ChoiceEntry]'s refusal and the one LOCK IN makes
        // on an empty ballot: refusing to send NOTHING is honest. It is drawn in its place from
        // the first frame either way -- the layout must not move under a thumb, least of all one
        // that cannot see.
        submit = {
            SubmitButton(if (entry.locked) "SUBMITTED" else "SUBMIT", p, live) {
                actions.handOverSubroutine(Subroutine.Sniff)
            }
        },
    ) {
        // The two halves. The held one wears the heavier outline, in the screen's top value: the
        // echo, and its only claim is *this is the side you touched*.
        Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((at, side) in listOf(SubroutineModel.SNIFF_FIRST to "LEFT", SubroutineModel.SNIFF_SECOND to "RIGHT")) {
                val held = entry.holds(at)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .border(if (held) 2.dp else 1.dp, if (held) p.ink else p.faint)
                        .then(
                            if (entry.handedOver != null) Modifier
                            else Modifier.tap { actions.tapSubroutine(Subroutine.Sniff, at) }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Words(side, size = 28, color = p.ink, lineHeight = 1.0)
                }
            }
        }
    }
}

/**
 * **Deallocate — columns of dots, and a tap takes one off the column you touched.**
 *
 * D-138 supersedes `gdd.md:569`, which did not say what a tap does. **A tap removes one dot from
 * the tapped column, and evening out means bringing every column down to the shortest** —
 * deallocating what was over-allocated. *The verb is the fiction, and the fiction was the answer
 * all along.* So the answer is unique, the work is countable, and nothing on the screen is a
 * numeral: the columns carry the arithmetic (`gdd.md:588`).
 *
 * Bright for its whole duration, which is the point of having bright ones — *a bright Subroutine
 * makes you a beacon for its whole duration*, and it is what makes standing lit at a marker
 * ordinary rather than a choice worth reading.
 *
 * ### A column can be taken below level, and the screen watches it happen
 *
 * *Over-taps are the player's to make.* A column that stopped at level would be the phone holding
 * an opinion about the answer, and D-125 is explicit: **clamp only what players cannot perceive**,
 * and column heights are the one thing here a player can. So every tap on a column that still has a
 * dot lands, the column goes past its neighbours, and the house rejects a wrong final state on
 * hand-over (D-109, D-110).
 *
 * ### ⚠️ The one refusal, and it is the picture's rather than the answer's
 *
 * **A column with no dots left is not a target.** That is not the guard rule 1 forbids: the refusal
 * at *level* would be the screen saying *that would be wrong*, while the refusal at *empty* is that
 * there is nothing under the finger to take. The alternative is worse than a verdict — an entry
 * quietly counting removals the picture cannot show would send the house a number the player was
 * never shown, and grade them on it. What is drawn and what is sent stay the same fact.
 *
 * ### The removed dots are gone rather than dimmed
 *
 * A ghost where a dot used to be would leave the *dealt* height on screen for the whole Subroutine,
 * and the dealt heights are where the level came from — the eye would then be comparing wells
 * instead of dots and the work would be over at a glance. What a column is now is the only thing it
 * shows, which is also the only thing the house is going to grade.
 *
 * **SUBMIT is live from the first frame**, for Jam's reason: an inert one would be the phone saying
 * the distribution you were dealt is not already level, which it has no way of knowing.
 */
@Composable
fun DeallocateScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.deallocate
    val actions = LocalActions.current
    // The house's columns (D-238): the level the house grades is the shortest of THESE.
    val columns = LocalSubroutine.current.columns
    val live = entry.handedOver == null
    val p = Palette.DARK
    SubroutineFrame(
        Subroutine.Deallocate, p, entry.gone, verdict,
        // Live from the first frame: see Jam's note, and ScalarEntry's.
        submit = {
            SubmitButton(if (entry.gone) "SUBMITTED" else "SUBMIT", p, live) {
                actions.handOverSubroutine(Subroutine.Deallocate)
            }
        },
    ) {
        // **The field is a fixed height and the body is not.** A column area that stretched with
        // the panel would draw the same blocks against a different amount of nothing on every
        // handset, and *how tall is that column* is the entire question. The columns stand on its
        // foot, so they are heights off one line.
        Row(Modifier.height(DEAL_FIELD), horizontalArrangement = Arrangement.spacedBy(DEAL_STRIP_GAP)) {
            for (at in columns.indices) {
                // What is left of this column: what it was dealt, less what this phone has taken
                // off it. Floored at nothing in the same expression that decides whether it is a
                // target -- see the note above about the two staying one fact.
                val height = (columns[at] - entry.taken(at)).coerceAtLeast(0)
                Box(
                    // The strip is the target and the block is drawn centred in it, so the press
                    // meets D-205's floor while the picture stays the mockup's 40 × 30.
                    Modifier.width(DEAL_COLUMN_WIDTH).fillMaxHeight().testTag(DEAL_COLUMN)
                        .then(
                            if (live && height > 0) {
                                Modifier.tap { actions.tapSubroutine(Subroutine.Deallocate, at) }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(DEAL_GAP)) {
                        repeat(height) {
                            Box(Modifier.size(DEAL_BLOCK_WIDTH, DEAL_BLOCK_HEIGHT).background(p.ink))
                        }
                    }
                }
            }
        }
    }
}

/**
 * **Interrupt — a sweep bouncing along a bar, and you tap to catch it inside the band.**
 *
 * D-139 supersedes `gdd.md:566` in both respects. **The sweep ping-pongs** rather than wrapping,
 * because a wrap has a defunct return stroke to wait through and a discontinuity at the edge for a
 * player to time against instead of the band. And **it runs forever**: there is no timeout on this
 * screen, no clock counting anything down, and nothing that ends the Subroutine but the player's
 * own tap or STOP NOW.
 *
 * ### Hesitation is taxed by the room rather than by a fail window
 *
 * The screen is MEDIUM and lit and the player is standing at the marker where D-110 keeps them, so
 * every extra pass is another second of being visible to whoever walks in. That is what lets the
 * sweep be slow and the band generous (`gdd.md:595`) without the Subroutine becoming free: the
 * pressure is in the corridor, not in the phone.
 *
 * ### The whole panel is the target, because the eye is on the bar
 *
 * A button under the bar would put the finger somewhere the eye is not, on the one Subroutine where
 * *when* is the entire answer. So the body is the control — a target the size of the screen, pressed
 * without looking away — and the bar's outline is what says it is live: the line value while it
 * is, the hairline value once the catch has gone.
 *
 * **The tap is the entry and there is no SUBMIT.** A confirmation step would be asking the player to
 * ratify a moment that is already seconds in the past, so the entry goes on the tap that makes it
 * — the sequence's rule, held by the type rather than remembered by this screen. What the frozen
 * sweep then shows is where it was caught, which is this phone echoing its own input and the only
 * thing it has to say.
 *
 * **Nothing here compares the catch with the band.** Both are on the screen, and reading them is the
 * player's — the same arrangement Jam's two rings and Deallocate's columns have. See
 * [InterruptSweep] for why the judgement stays with the house even though the picture cannot.
 */
@Composable
fun InterruptScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.interrupt
    val actions = LocalActions.current
    // The band's width came with its position (E-S2-1): what is drawn here is what the house will
    // grade against, because they are the same number and it arrived on the same scan.
    val sweep = LocalSubroutine.current.sweep
    val live = !entry.handedOver
    val elapsed = screenMillis(running = live, instance = LocalSubroutine.current.instance)
    // Where it is, or where it was caught. One expression, because they are the same picture: the
    // bar carries a mark at a step of the span and what put it there is not the bar's business.
    val at = entry.entered.firstOrNull() ?: sweep.at(elapsed)
    val p = Palette.MEDIUM

    SubroutineFrame(
        Subroutine.Interrupt, p, entry.handedOver, verdict,
        // The whole body is the target, not just the bar drawn in it: see the note above.
        bodyModifier = if (live) {
            Modifier.tap { actions.tapSubroutine(Subroutine.Interrupt, sweep.at(elapsed)) }
        } else {
            Modifier
        },
    ) {
        SweepBar(sweep, at, caught = entry.handedOver, p)
    }
}

/**
 * **Drift — a dot crosses behind cover, and the phone buzzes to ask where it is.**
 *
 * D-140 supersedes `gdd.md:570`. The dot drifts at constant velocity along a straight path and
 * hides; **at a house-chosen instant a short haptic pulse says *now***, and the player taps where
 * the dot is at that moment. The hidden duration is the difficulty and it is invisible — the player
 * cannot see how far they will have to carry the dot in their head before being asked.
 *
 * ### The lane is the target, so there is nothing to aim at but *how far along*
 *
 * The path is straight and drawn, so where the dot is *vertically* is not a question anybody is
 * asking — and a tap taxed for missing a line the screen already shows would be grading a blind
 * one-handed finger on something the question never contained. So **the strip is the control, the
 * full height of the body**, and the entry is the step of the lane the finger landed on. That is
 * this screen's one presentation decision and it is flagged: it makes the hit radius a distance
 * along the path.
 *
 * ### A tap before the buzz is handed over, graded REJECTED, and the attempt is gone (D-165)
 *
 * D-165 supersedes E-S2-3, which echoed the early tap and sent nothing. That made an early tap a
 * free probe of the house's NOW — tap, learn you were early, tap again — on the one Subroutine whose
 * whole content is *when*. The game already answers *did my tap land*, and the answer is a grade
 * (D-109): an early tap is an entry and a wrong one. So the mark stays where the finger landed, the
 * entry goes as [ChoiceEntry.handOverEarly] — holding no step, so the house cannot accept it
 * whatever the dot was doing — and `RETURNED . WAITING` says it went. One attempt per scan (D-110):
 * to try again the player walks back and re-scans. **Both roles, the same way**: nothing here reads
 * one.
 *
 * ### The motion stops when the entry goes, and it shows exactly what it was showing
 *
 * Freezing rather than running on, because a dot that kept drifting after the hand-over would
 * emerge from cover at a known speed and hand back the position the player was asked for. Frozen,
 * nothing appears that the player was not already looking at.
 *
 * **The buzzing is the house's** — haptics are, and this build has no phone attached to feel them
 * on — so on a desktop render the pulse is a moment in [DriftPath.script] rather than something that
 * happens. That is the same gap [HandshakeScreen] and [SniffScreen] have.
 */
@Composable
fun DriftScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.drift
    val actions = LocalActions.current
    val path = LocalSubroutine.current.path
    val live = entry.handedOver == null
    val elapsed = screenMillis(running = live, instance = LocalSubroutine.current.instance)
    // Whether the house has said *now*. Not a judgement: it is the clock reaching the instant the
    // scan named, the same instant for both roles, and it decides only which way a tap goes (D-165).
    val asked = elapsed >= path.askAtMillis
    // Drawn only where there is nothing in front of it. The cover is drawn over the dot as well,
    // so the last of it goes behind an edge rather than blinking out of a clear stretch of lane.
    val dot = path.at(elapsed)?.takeIf { !path.covered(it) }
    val p = Palette.MEDIUM

    SubroutineFrame(Subroutine.Drift, p, entry.gone, verdict) {
        Box(
            Modifier.fillMaxWidth().height(LANE_FIELD).border(1.dp, p.line)
                .testTag(DRIFT_LANE)
                // It publishes no click action, exactly as [holdSurface] does not: a position
                // is where the finger landed, and a synthetic click carries no position. The
                // tag is how a test finds it, and `DriftInputTest` drives real pointers at it.
                .pointerInput(live, asked) {
                    if (!live) return@pointerInput
                    detectTapGestures { offset ->
                        // The step under the finger. Clamped to the lane it is a tap inside
                        // of: an edge pixel is not a position a player can tell apart from
                        // the end of the strip, which is D-125's rule for what may be clamped.
                        val step = (offset.x * DriftPath.SPAN / size.width)
                            .toInt().coerceIn(0, DriftPath.SPAN)
                        actions.tapSubroutine(Subroutine.Drift, step)
                        // Either way the entry goes (D-165). Before the buzz it goes as the
                        // wrong answer it is; after, as the step the finger landed on.
                        if (asked) {
                            actions.handOverSubroutine(Subroutine.Drift)
                        } else {
                            actions.handOverEarly()
                        }
                    }
                },
        ) {
            DriftLane(path, dot, entry.choice, p)
        }
    }
}

// ---- The furniture every Subroutine screen shares ------------------------------------------

/** How big a Replay dot is. Named because it is a decision about a finger, not a layout number. */
private val DOT: Dp = 72.dp

/**
 * **Replay's playback schedule: a still beat, then each dot of the pattern lit in turn** (D-238).
 *
 * Placeholders, and playtest owns them: long enough lit to be seen at arm's length in a dark room,
 * a gap between two flashes so a dot flashed twice in a row could not be mistaken for one long
 * flash (the reader refuses a repeat anyway), and a lead-in so the first flash is not spent on a
 * player still lifting the phone.
 */
internal const val REPLAY_LEAD_MILLIS: Long = 800L
internal const val REPLAY_LIT_MILLIS: Long = 600L
internal const val REPLAY_STEP_MILLIS: Long = 900L

/**
 * **The dot the pattern has lit [elapsed] milliseconds into the screen, or null** (D-238).
 *
 * A pure function of the house's pattern and the screen's own clock, and of nothing else, so the
 * same scan flashes the same dots at the same moments on every phone and on both roles. It says
 * nothing about the entry: it is the question being asked, not an answer being checked.
 */
internal fun replayFlashing(order: List<Int>, elapsed: Long): Int? {
    val into = elapsed - REPLAY_LEAD_MILLIS
    if (into < 0) return null
    val step = into / REPLAY_STEP_MILLIS
    if (step >= order.size) return null
    return if (into % REPLAY_STEP_MILLIS < REPLAY_LIT_MILLIS) order[step.toInt()] else null
}

/** How long the playback of [order] takes, after which nothing flashes again. */
internal fun replayPlayedMillis(order: List<Int>): Long = REPLAY_LEAD_MILLIS + order.size * REPLAY_STEP_MILLIS

/**
 * **Deallocate's block, the gap above it, and the field they stand in** (the mockup's 40 × 30,
 * 6 apart, in a 260 field).
 *
 * The block is not a target -- the *column* is, a strip the full height of the field. What the
 * numbers have to do instead is let [DotColumns.MOST_DOTS] blocks stand up inside the field without
 * being clipped, and [DotColumns.MOST_COLUMNS] strips sit side by side in the body. A field short of
 * what the reader lets through is a clipped column, which is a *different question* from the one
 * the house asked; `SubroutineFitTest` holds both.
 */
internal val DEAL_BLOCK_WIDTH: Dp = 40.dp
internal val DEAL_BLOCK_HEIGHT: Dp = 30.dp
internal val DEAL_GAP: Dp = 6.dp
internal val DEAL_FIELD: Dp = 260.dp

/** The space between Deallocate's drawn blocks, as the mockup draws it. */
internal val DEAL_COLUMN_GAP: Dp = 14.dp

/**
 * **The column a finger presses: [TAP_TARGET] wide, with the block centred in it** (D-205).
 *
 * The block stays the mockup's 40 and the strip it stands in is the target, so the floor is met
 * without moving a pixel of the picture. It is the constant itself rather than a copy of it, so the
 * strip and the floor cannot drift apart again (they had: the strip was the 40-unit block).
 */
internal val DEAL_COLUMN_WIDTH: Dp = TAP_TARGET

/**
 * The space between the strips: the drawn gap, less what each strip adds to its block. That keeps
 * the blocks [DEAL_COLUMN_GAP] apart on the glass, and it is what lets six strips fit the body.
 */
internal val DEAL_STRIP_GAP: Dp = DEAL_COLUMN_GAP - (DEAL_COLUMN_WIDTH - DEAL_BLOCK_WIDTH)

/** Each Deallocate strip, so `SubroutineFitTest` can measure the press rather than the constant. */
const val DEAL_COLUMN: String = "deallocate-column"

/**
 * **Jam's two rings, and the step is a placeholder** (the mockup's 140 rings, 3 units of line).
 *
 * The rings sit apart along the row and the player's walks onto the fixed one, which is what the
 * briefing says will happen (*TWO RINGS WILL SIT APART*, D-194). **Where the player's ring opens is
 * not here** -- it is `JamRings.opening`, drawn by the house with the rest of the question, and the
 * ring is drawn that many steps from the fixed one. The pair is centred on the opening, so the
 * fixed ring never moves and the start is the mockup's picture.
 *
 * The step is the **presentation fixture**: four units is a press you can see land at arm's
 * length, and at `JamRings.REACH` past the widest opening the ring still stays on the glass.
 */
internal val JAM_RING: Dp = 140.dp
internal val JAM_STEP: Dp = 4.dp
private val JAM_FIELD: Dp = 220.dp

/**
 * **Signal Trace's lattice, in mockup units.**
 *
 * [NODE] is the mockup's 24 square, drawn inside a [NODE_TARGET] of 56 -- D-202's least tap target,
 * because a node is a control pressed one-handed in the dark by somebody watching a doorway, and the
 * graph is the one screen where two targets sit close enough that a near miss lands on the wrong
 * one. The lattice is spread over the mockup's 320 × 280 so no two targets overlap.
 */
private val NODE: Dp = 24.dp
private val NODE_TARGET: Dp = 56.dp
private val GRAPH_WIDTH: Dp = 320.dp
private val GRAPH_HEIGHT: Dp = 280.dp
private val NODE_INSET: Dp = NODE_TARGET / 2
private val COLUMN_STEP: Dp = (GRAPH_WIDTH - NODE_TARGET) / (SignalGraph.COLUMNS - 1)
private val ROW_STEP: Dp = (GRAPH_HEIGHT - NODE_TARGET) / (SignalGraph.ROWS - 1)

/**
 * How many finger-cells Short's two rows keep room for, so neither row's cells move when the
 * count changes. A hand, because a hand is what is on the glass.
 */
private const val MOST_FINGERS = 5

/** Where a node's centre falls inside the graph box. The one place the lattice becomes a picture. */
private fun DrawScope.nodeCentre(node: SignalGraph.Node): Offset = Offset(
    x = (NODE_INSET + COLUMN_STEP * node.column).toPx(),
    y = (NODE_INSET + ROW_STEP * node.row).toPx(),
)

/**
 * **Interrupt's bar, and Drift's lane -- the two screens the house draws with a clock.**
 *
 * [BAR_FIELD] and [LANE_FIELD] are fixed heights for [DEAL_FIELD]'s reason: a field that stretched
 * with the panel would draw the same motion across a different amount of nothing on every handset,
 * and *how far along is it* is the entire question on both screens.
 *
 * [SWEEP] and [MARK] are wide enough to be a thing rather than a hairline at arm's length in an
 * unlit room, and narrow enough that where they are is a position rather than a region. [DRIFT_DOT]
 * is the one round thing on either screen, which is what makes it the thing that moves.
 */
private val BAR_FIELD: Dp = 200.dp
private val SWEEP: Dp = 6.dp
private val SWEEP_INSET: Dp = 12.dp
private val BAND_EDGE: Dp = 2.dp
private val LANE_FIELD: Dp = 120.dp
private val LANE_INSET: Dp = 24.dp
private val LANE_STEP: Dp = 36.dp
private val DRIFT_DOT: Dp = 10.dp
private val MARK: Dp = 6.dp

/** What Jam's rings tag themselves with, so a guard can read the fixed ring off its own pixels. */
const val JAM_RINGS: String = "subroutine-rings"

/** What Interrupt's bar tags itself with, so a guard can read the band off its own pixels. */
const val INTERRUPT_BAR: String = "subroutine-bar"

/**
 * What Short's hold surface tags itself with.
 *
 * It publishes no click action on purpose, so a test cannot find it the way it finds every other
 * control in this app -- and finding it by the words on it would be finding the label, which is not
 * the thing several fingers have to land inside.
 */
const val HOLD_SURFACE: String = "subroutine-hold"

/** A palette with nothing lit: every role black. Sniff, before the answer (D-137). */
private val UNLIT = Palette(
    ink = Amber.Black, mid = Amber.Black, dim = Amber.Black, line = Amber.Black,
    faint = Amber.Black, fill = Color.Transparent, onFill = Amber.Black, fillEdge = Amber.Black,
)

/** What the chosen parity cell tags itself with, so a guard counts marks rather than pixels. */
const val PARITY_MARK: String = "subroutine-mark"

/**
 * What Drift's lane tags itself with.
 *
 * It publishes no click action, for [HOLD_SURFACE]'s reason turned sideways: a click carries no
 * position, and the position is the whole of what this control is for.
 */
const val DRIFT_LANE: String = "subroutine-lane"

/**
 * **How long this screen has been open, in milliseconds — the only clock in the module, and it is
 * the composition's.**
 *
 * D-139 and D-140 both put the motion on the client and the parameters on the wire, so both screens
 * need to know how far into the question they are. Three things about where that number comes from:
 *
 * **It is the frame clock and never a wall clock.** `Clock.System.now()` is unreplayable by
 * construction — the harness cannot put the phone back at 21:47:03 — and a screen that read one
 * would render a different frame on every replay of the same recording, which is the second
 * promise in `project-context` broken by a drawing helper.
 *
 * **It is [withInfiniteAnimationFrameMillis] rather than `withFrameMillis`**, and that is what
 * lets D-139's *no timeout* be literal. Compose's own idiom for an animation with no end also
 * tells a test harness that this composition is never going to go idle: under a test clock that
 * auto-advances, the frames stop and the screen renders its opening position — deterministic,
 * identical for both roles, and not a hang. Under `mainClock.autoAdvance = false` the frames arrive
 * exactly where the test puts them, which is how the sweeps sample an animated screen at the same
 * instant for both roles. [holdSurface]'s clock is `withFrameMillis` because a hold *does* end.
 *
 * **It accumulates deltas rather than subtracting from a start.** The frame clock's origin is not
 * this screen's, so the first frame is zero by definition of the thing being measured, and every
 * frame after it adds what has passed since the last one.
 *
 * It freezes when [running] goes false, which is what makes an entry that has gone stop the picture
 * where it stood. See [DriftScreen] for why that matters more there than a frozen picture usually
 * would.
 *
 * ### It restarts on the INSTANCE, not on the composition (E-S2-4)
 *
 * [instance] is [SubroutineModel.instance], and it is what the zero is a zero *of*. A re-scan
 * re-draws the band's position and phase (D-139) and the drift's path and wait (D-140), so the
 * question on the glass is a new question and the clock measuring it has to be a new clock.
 *
 * Keying it on `remember` alone would have made the restart a side effect of the screen being
 * disposed — which is true today, because the only route back to a marker goes through the work
 * order — and would have gone silently wrong the first time a re-scan could re-open a Subroutine
 * the phone was already standing on. **The clock restarts because the instance changed**, which
 * is the sentence the fix has to be, and now is.
 */
@Composable
private fun screenMillis(running: Boolean, instance: Int, until: Long = Long.MAX_VALUE): Long {
    var elapsed by remember(instance) { mutableStateOf(0L) }
    LaunchedEffect(running, instance, until) {
        if (!running) return@LaunchedEffect
        var last = withInfiniteAnimationFrameMillis { it }
        // [until] is Replay's: its playback ends, and a clock left running past it would be a frame
        // every refresh for a picture that no longer changes. The two that never end pass nothing.
        while (elapsed < until) {
            val now = withInfiniteAnimationFrameMillis { it }
            elapsed += now - last
            last = now
        }
    }
    return elapsed
}

/**
 * The bar, the band on it, and the sweep at [at] (the mockup's: a 200-tall box, the band's two
 * edges dashed, the sweep a bar inset from the box's top and foot).
 *
 * Drawn in one canvas because they are one picture and share one mapping from a step of
 * [InterruptSweep.SPAN] to a place on the glass. **The band is drawn and the sweep is drawn and
 * nothing here asks whether one is inside the other** -- that is the house's, and there is no
 * expression in this function that could form the question.
 *
 * The caught mark is [Ramp.Ink] and the running sweep is the screen's ink, which is the same
 * grammar every other screen in this file uses: what the house drew is dimmer than what the player
 * did.
 */
@Composable
private fun SweepBar(sweep: InterruptSweep, at: Int, caught: Boolean, p: Palette) {
    Canvas(
        Modifier.fillMaxWidth().height(BAR_FIELD).testTag(INTERRUPT_BAR)
            .border(1.dp, if (caught) p.faint else p.line)
    ) {
        fun x(step: Int): Float = size.width * step / InterruptSweep.SPAN
        val edge = BAND_EDGE.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(edge * 2, edge * 2))
        for (side in listOf(x(sweep.bandFrom) + edge / 2, x(sweep.bandTo) - edge / 2)) {
            drawLine(p.ink, Offset(side, 0f), Offset(side, size.height), edge, pathEffect = dashes)
        }
        val width = SWEEP.toPx()
        val inset = SWEEP_INSET.toPx()
        drawRect(
            if (caught) Ramp.Ink else p.ink,
            Offset(x(at) - width / 2, inset),
            Size(width, size.height - inset * 2),
        )
    }
}

/**
 * The straight path, what stands in front of it, the dot where it can be seen, and the player's
 * mark.
 *
 * **The cover is drawn after the dot**, so the dot passes behind an edge rather than blinking out
 * in a clear stretch of lane -- and it is drawn at all only where nothing stands in front of it, so
 * a dot half-way under an edge is not a dot whose position can be read off the part still showing.
 * The cover is the stairs hatch, the full height of the lane, between two line-value edges.
 *
 * **The path is a hairline.** The mockup draws no path at all; it is kept because the lane is the
 * target and only *how far along* is asked, and a drawn path is what says the height is not.
 *
 * The mark spans the whole field rather than sitting on the lane: it is the one thing here the
 * player put on the screen, it has to be findable without looking for it, and a tick the height of
 * the field cannot be mistaken for something that drifted there.
 */
@Composable
private fun DriftLane(path: DriftPath, dot: Int?, mark: Int?, p: Palette) {
    // **No inset, because the finger and the picture have to be measuring the same rectangle.**
    // The tap arrives as an offset into the surface this fills, and the step it becomes is that
    // offset over this width -- so a canvas narrower than its own touch surface would put the mark
    // somewhere other than where the finger landed and send the house the other one. What is drawn
    // and what is sent stay one fact.
    Canvas(Modifier.fillMaxSize()) {
        fun x(step: Int): Float = size.width * step / DriftPath.SPAN
        val lane = (LANE_INSET + LANE_STEP * path.lane).toPx()
        val rule = 1.dp.toPx()
        drawRect(p.faint, Offset(0f, lane - rule / 2), Size(size.width, rule))
        if (dot != null) {
            drawCircle(p.ink, DRIFT_DOT.toPx() / 2, Offset(x(dot), lane))
        }
        for (cover in path.cover) {
            val from = x(cover.first)
            val to = x(cover.last + 1)
            drawHatch(Offset(from, 0f), Size(to - from, size.height), Ramp.Fill, band = 6.dp.toPx())
            drawRect(p.line, Offset(from, 0f), Size(rule, size.height))
            drawRect(p.line, Offset(to - rule, 0f), Size(rule, size.height))
        }
        if (mark != null) {
            val width = MARK.toPx()
            drawRect(Ramp.Ink, Offset(x(mark) - width / 2, 0f), Size(width, size.height))
        }
    }
}

/**
 * **Every Subroutine screen, one layout** (`s_work.py`'s `sub`): the name and what it will do to
 * the lamp, the work in the middle, the return line, the motion budget, SUBMIT where there is one,
 * and STOP NOW.
 *
 * **One fixed slot for the light mark, on every Subroutine screen** (D-106). It is the same place
 * on the near-black one and on the lit ones, which is what makes it a thing a player recognises
 * rather than reads -- HANDSHAKE shows one cell and PARITY CHECK three, in the same corner, at the
 * same size.
 *
 * The name comes off the roster rather than out of a string, so a screen cannot be titled with a
 * Subroutine whose light it is not showing. [p] is the whole of a screen's light: the palette is the
 * Subroutine's rung (bright, medium, or concealment for the dark ones, D-077), so nothing on the
 * screen is brighter than its rung allows.
 *
 * [bodyModifier] is for the two screens whose body *is* the control -- Interrupt's tap and Short's
 * hold.
 */
@Composable
private fun SubroutineFrame(
    subroutine: Subroutine,
    p: Palette,
    handedOver: Boolean,
    verdict: SubroutineVerdict?,
    bodyModifier: Modifier = Modifier,
    submit: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    KitScreen(gap = 10.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Words(subroutine.label, Modifier.weight(1f), size = 32, color = p.ink, lineHeight = 1.0)
            LightMark(subroutine.light, p.ink, cell = 7.dp, height = 14.dp, gap = 2.dp, outline = true)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().then(bodyModifier),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = body,
        )
        ReturnLine(handedOver, verdict, p.dim)
        KitBudgetRow(p, height = 44.dp)
        submit?.invoke()
        StopNow(p)
    }
}

/** A cell: an outline, filled when it counts. The meter's grammar at a Subroutine's size. */
@Composable
private fun Cell(width: Dp, height: Dp, edge: Color, fill: Color?) {
    Box(
        Modifier.size(width, height).border(1.dp, edge)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
    )
}

/**
 * **Handshake's beats gone back, as cells -- uniform, because the rhythm is felt, not seen.**
 *
 * One cell per beat the house asked for, lit up to what this phone has entered. The ported fixture
 * drew its beats at differing widths, which read as *long beat, short beat* -- the rhythm itself, on
 * the screen, in a Subroutine whose one instruction is **by feel**. So the cells are identical and
 * carry only *how many* beats have gone back, which is the player's own input.
 *
 * **It counts and it does not judge.** A cell lights because a finger landed, not because the
 * finger landed at the right moment -- see [RhythmEntry], which holds when each one landed and no
 * gap to compare it with (D-296).
 */
@Composable
private fun BeatCells(entry: RhythmEntry) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(entry.length) { at ->
            val lit = at < entry.taps
            Cell(30.dp, 14.dp, edge = if (lit) Ramp.Line else Ramp.Faint, fill = if (lit) Ramp.Line else null)
        }
    }
}

/**
 * A named quantity, drawn as cells -- the row grammar with the numeral taken out.
 *
 * *Comparing quantities is perception; adding numbers is computation*, so a count that a player
 * has to hold against another count is drawn as a length rather than written as a digit. Used on
 * Short for the two counts that matter, one above the other, so the comparison is a glance: ASKS
 * is the house's instruction, DOWN this phone's echo of the hand. Two rows rather than one lit
 * part-way, because a single row that filled exactly when you had it right would be a verdict
 * drawn as a diagram.
 */
@Composable
private fun CountRow(label: String, count: Int, slots: Int, p: Palette) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A fixed slot for the word, so the two rows' cells start at the same place.
        Tag(label, p.ink, Modifier.width(64.dp))
        repeat(slots) { at ->
            Box(
                Modifier.weight(1f).height(22.dp).border(1.dp, p.faint)
                    .then(if (at < count) Modifier.background(Ramp.Line) else Modifier)
            )
        }
    }
}

/**
 * Jam's two rings: the fixed one in the line value, the player's in the screen's ink, along one
 * row. At an offset that cancels the opening the player's ring is drawn exactly on the fixed one,
 * so the two read as one -- `SubroutineFitTest` holds that.
 */
@Composable
private fun JamRow(opening: Int, offset: Int, p: Palette) {
    Canvas(Modifier.fillMaxWidth().height(JAM_FIELD).testTag(JAM_RINGS)) {
        val step = JAM_STEP.toPx()
        val radius = (JAM_RING.toPx() - 3.dp.toPx()) / 2
        val stroke = Stroke(3.dp.toPx())
        val fixed = Offset(size.width / 2 - step * opening / 2f, size.height / 2)
        val player = fixed + Offset(step * (opening + offset), 0f)
        drawCircle(p.line, radius, fixed, style = stroke)
        drawCircle(p.ink, radius, player, style = stroke)
    }
}

/**
 * **The surface Short is held with: the whole body, counting the fingers on it.**
 *
 * Two pieces of state that look like one and are not. `down` is how many pointers the *glass*
 * currently reports, and it exists to drive the clock; the entry beside it is the *echo*, and it
 * is what gets drawn. They are the same number in a player's hand, and they are deliberately not
 * the same variable: the clock must only ever be started by a finger, so a fixture that puts a
 * count into the entry -- every render, every parity comparison -- moves the cells on the screen and
 * starts nothing.
 *
 * The hold restarts whenever the number of fingers changes, because *hold N fingers for two
 * seconds* is a hold of an unchanging hand: fingers arriving one at a time each begin it again,
 * which is what the gesture is.
 *
 * ### The hold ends on the clock and not on your hand
 *
 * A hold that completed when the *asked-for* number of fingers arrived would be the phone grading
 * the answer -- and grading it visibly, since a wrong hand would sit there while a right one went.
 * So the two seconds run against whatever is on the glass and the entry goes with whatever was
 * there. See [HoldEntry].
 *
 * **It publishes no click action**, exactly as [HoldToConfirm] does not -- a hold a single
 * synthetic click could fire would not be a hold -- and it walks no edge, so there is nothing for
 * `ScreenGraphTest` to miss. `ShortInputTest` drives real pointers, several at once, against a
 * clock it controls.
 */
@Composable
private fun holdSurface(live: Boolean, onFingers: (Int) -> Unit, onHeld: () -> Unit): Modifier {
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

    return Modifier.testTag(HOLD_SURFACE).pointerInput(Unit) {
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
    }
}

/**
 * SUBMIT, on the Subroutines whose answer can still change -- and SUBMITTED once it cannot
 * (`s_work.py`'s 60-tall outline in the screen's ink).
 *
 * Drawn inert rather than hidden when there is nothing to send: the layout must not move under a
 * thumb. Inert, its edge drops to the line value and its words to the dim one. Whether it is live
 * at all is the caller's, because the reasons differ and the difference is the interesting part --
 * see the notes at the call sites.
 */
@Composable
private fun SubmitButton(label: String, p: Palette, enabled: Boolean, onClick: () -> Unit) {
    KitSecondary(
        label, if (enabled) p else p.copy(ink = p.dim),
        size = 28, height = 60.dp,
        edge = if (enabled) p.ink else p.line, edgeWidth = 2.dp,
        onClick = if (enabled) onClick else null,
    )
}

/**
 * **The one line that says the phone is no longer waiting for you -- and then what the house said.**
 *
 * Three states in one fixed slot: empty while the player is still working, `RETURNED . WAITING`
 * once the entry has gone, and the house's verdict once it arrives. A fixed slot whether or not
 * there is anything in it, so nothing above it moves at the moment of completion or at the moment
 * of the answer -- a layout that jumps is a change in lit area nobody authored, on the two frames
 * the player is most likely to be looking away.
 *
 * *Waiting* is the honest word for the middle state and the only one available: whether the entry
 * was right is the house's answer, and the device has no opinion to offer in the meantime.
 *
 * ### The copy, and the register it is in (D-109)
 *
 * **Success is a damage report, and the house is not on your side.** *THE HOUSE GROWS WEAKER* is
 * the decision log's own candidate and the family it names; the house narrates its own decline
 * rather than congratulating anybody, and *"the house has it"* is ruled out explicitly. The final
 * wording is a build-time call and this is a draft; **the register is not a draft.**
 *
 * **Failure is a rejection plus an instruction to re-scan in place**, and the instruction is load-
 * bearing rather than helpful: D-110 makes the walk back to the marker the price of a wrong
 * answer, so the line has to say where the player must stand. It says nothing about *what* was
 * wrong -- a mismatch reports only that it was a mismatch (`gdd.md:608`).
 *
 * **Neither line offers a retry, and no control on any of these screens re-arms.** The only way
 * back to ready is a fresh scan of the marker (D-110).
 *
 * ### Identical for both roles, which is the whole of D-109
 *
 * There is no role in this file and none in this function. `SubroutineParityTest` renders both
 * roles in all three states and compares the pixels.
 */
@Composable
private fun ReturnLine(handedOver: Boolean, verdict: SubroutineVerdict?, color: Color) {
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        val line = when {
            verdict == SubroutineVerdict.Accepted -> HOUSE_WEAKER
            verdict == SubroutineVerdict.Rejected -> RESCAN
            handedOver -> "RETURNED . WAITING"
            else -> null
        }
        if (line != null) Words(line, size = 22, color = color, lineHeight = 1.0)
    }
}

/**
 * **Drafted copy, flagged. The register is ruled (D-109); these exact words are not.**
 *
 * Named constants rather than literals at ten call sites, so the two lines cannot drift into ten
 * variants of themselves -- and so that whoever settles the wording changes it in one place.
 */
const val HOUSE_WEAKER: String = "THE HOUSE GROWS WEAKER"
const val RESCAN: String = "REJECTED . RESCAN THE MARKER"

/**
 * **STOP NOW -- the same words, in the same place, on every Subroutine screen.**
 *
 * Somebody walked in. The player is heads-down, lit or blind, both hands occupied, and needs to be
 * out of this screen without reading it: so the control is constant across all ten, keeps its
 * words whether or not the entry has gone back, and sits at the foot where the scan's own STOP NOW
 * sits. In the dark that is muscle memory, and muscle memory is the only interface that works when
 * you are not looking at the phone. On Sniff before the answer it is drawn in black and is live
 * all the same: in the dark that is what it already was.
 *
 * It goes to the work order rather than the springboard -- you came from the list and the list is
 * where the next decision is -- which is where NOT THIS ONE goes from the screen before this one.
 *
 * **It is not a claim about the Subroutine, and it never became one.** It reports to the house now
 * (`Intent.StopSubroutine`) -- the window this phone opened has shut, which the presence plane needs
 * in order to expire a placement (D-111, D-136) -- and what it reports is **the actor and nothing
 * else**: no marker, no reason, no progress.
 *
 * **It reports even when nothing was entered.** A phone that only told the house about abandoned
 * work it had made progress on would make the report itself a signal, which is the statistic
 * separating a real Subroutine from a fake.
 */
@Composable
private fun StopNow(p: Palette) {
    val actions = LocalActions.current
    KitQuiet("STOP NOW", p.copy(mid = p.dim), onClick = actions.stopSubroutine)
}
