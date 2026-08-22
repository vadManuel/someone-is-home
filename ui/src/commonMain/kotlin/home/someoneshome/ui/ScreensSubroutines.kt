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
 * **The Subroutines themselves — eight of the design's ten, with their interaction.**
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
 * - **Sniff's two groups are buzzed**, for Handshake's reason and with Handshake's consequence: on
 *   a build with no phone attached the question never arrives, and the screen is black until
 *   somebody taps it. Built: the answer, and the darkness it is given in.
 * - **Parity Check, Jam, Signal Trace and Deallocate have no timed presentation at all**, which is
 *   why those four are whole: the grid, the rings, the wiring and the columns are on the screen,
 *   the work is what you do to them, and the only thing the house owns is the verdict.
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
            ReturnLine(entry.handedOver, verdict, Amber.Faint)
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
fun ReplayScreen(verdict: SubroutineVerdict? = null) {
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
            ReturnLine(entry.handedOver, verdict, Amber.Dim)
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
fun ParityCheckScreen(verdict: SubroutineVerdict? = null) {
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

        ReturnLine(entry.gone, verdict, Amber.Dim)
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
fun ShortScreen(verdict: SubroutineVerdict? = null) {
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
            ReturnLine(entry.handedOver != null, verdict, Amber.Faint)
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
fun JamScreen(verdict: SubroutineVerdict? = null) {
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

        ReturnLine(entry.gone, verdict, Amber.Dim)
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
fun SignalTraceScreen(verdict: SubroutineVerdict? = null) {
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

        ReturnLine(entry.gone, verdict, Amber.Dim)
        MotionBudgetRow(Amber.Dim, Amber.Mid)
        // Inert with an empty route, which is the refusal LOCK IN makes on an empty ballot: a
        // control that sends nothing and looks like it sent something is a phone lying about what
        // the house holds. Nothing about *which* route, which is why Jam's is live from the start.
        SubmitButton(Subroutine.SignalTrace, sent = entry.gone, enabled = live && entry.touched)
        StopNow(Amber.Faint, Amber.Dim)
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
 * sentence made of light, and the ruling forbids light until the answer. The instruction has to live
 * on the screen before this one, and that is recorded as owed rather than quietly solved here.
 */
@Composable
fun SniffScreen(verdict: SubroutineVerdict? = null) {
    val entry = LocalSubroutine.current.sniff
    val actions = LocalActions.current

    // The one thing that decides every colour below. A verdict lights the screen too, even with
    // nothing chosen: a screen that could not show the house's answer would be the device
    // withholding it, which is the opposite of what D-109 gave the house a verdict for.
    val lit = entry.choice != null || verdict != null
    val ink = if (lit) Amber.Dim else Amber.Black
    val mark = if (lit) Amber.Faint else Amber.Black
    val edge = if (lit) Amber.Edge else Amber.Black
    // The brightest thing this screen ever draws, and it is [Amber.Dim] — the same ceiling
    // HANDSHAKE has. The dark rung is a claim about how much light a Subroutine makes a player
    // emit (D-106), and a screen that went dark for the perception and then flared for the answer
    // would be making that claim about half of itself.
    val chosen = if (lit) Amber.Dim else Amber.Black

    SubroutineFrame(Subroutine.Sniff, ink = ink, mark = mark) {
        // The whole body, split down the middle. No divider between them: a line drawn on a dark
        // screen is light, and the two halves are told apart by the phone's own edges.
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.u),
        ) {
            for (at in listOf(SubroutineModel.SNIFF_FIRST, SubroutineModel.SNIFF_SECOND)) {
                val held = entry.holds(at)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .border(if (held) 2.u else 1.u, if (held) chosen else edge)
                        .then(
                            if (entry.handedOver != null) Modifier
                            else Modifier.tap { actions.tapSubroutine(Subroutine.Sniff, at) }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // The echo, and its only claim: this is the side you touched. Drawn as a block
                    // in the same grammar as every other count on these screens, and drawn on one
                    // side only -- an unheld half shows nothing at all, so there is no pair of
                    // marks for the eye to compare and no second thing to read.
                    if (held) Block(26.u, 26.u, chosen)
                }
            }
        }

        ReturnLine(entry.gone, verdict, ink)
        MotionBudgetRow(ink, mark, border = edge)

        // Refused with nothing chosen, which is [ChoiceEntry]'s refusal and the one LOCK IN makes
        // on an empty ballot: refusing to send NOTHING is honest. It is drawn in its place from
        // the first frame either way -- the layout must not move under a thumb, least of all one
        // that cannot see.
        PanelButton(
            if (entry.locked) "SUBMITTED" else "SUBMIT",
            border = if (entry.choice == null || entry.locked) mark else ink,
            ink = if (entry.choice == null || entry.locked) ink else chosen,
            tracking = 0.18, verticalPadding = 9.u,
            onClick = if (entry.choice == null || entry.locked) {
                null
            } else {
                { actions.handOverSubroutine(Subroutine.Sniff) }
            },
        )

        StopNow(edge, ink)
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
    val columns = SubroutineModel.DEALLOCATE
    val live = entry.handedOver == null
    SubroutineFrame(Subroutine.Deallocate, ink = Amber.Dim, mark = Amber.Bright) {
        Label(
            "TAP A COLUMN TO TAKE A DOT OFF IT\nBRING THEM DOWN TO THE SHORTEST",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Dim, tracking = 0.12, lineHeight = 1.8,
            align = TextAlign.Center,
        )

        // **The field is a fixed height and the body is not.** A column area that stretched with
        // the panel would draw the same five dots against a different amount of nothing on every
        // handset, and *how tall is that column* is the entire question — so the field is exactly
        // as tall as the tallest distribution this panel accepts, centred in whatever room the
        // body has. It is also what guarantees a full column is never clipped: the space is
        // reserved whether or not anything is standing in it.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(8.u)) {
                Row(
                    Modifier.height(DEAL_FIELD).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.u),
                ) {
                    for (at in columns.indices) {
                        // What is left of this column: what it was dealt, less what this phone
                        // has taken off it. Floored at nothing in the same expression that
                        // decides whether it is a target — see the note above about the two
                        // staying one fact.
                        val height = (columns[at] - entry.taken(at)).coerceAtLeast(0)
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .then(
                                    if (live && height > 0) {
                                        Modifier.tap {
                                            actions.tapSubroutine(Subroutine.Deallocate, at)
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(DEAL_GAP),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                repeat(height) {
                                    Box(
                                        Modifier.size(DEAL_DOT)
                                            .background(Amber.Bright, CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }
                // The floor they all stand on. Without it the columns are lengths hanging in a
                // box and the comparison is a judgement about where each one ends; with it they
                // are heights off one line, which is the comparison the Subroutine is made of.
                Hairline(Amber.Faint, Modifier.padding(top = 6.u))
            }
        }

        ReturnLine(entry.gone, verdict, Amber.Dim)
        MotionBudgetRow(Amber.Dim, Amber.Bright)
        // Live from the first frame: see Jam's note, and ScalarEntry's.
        SubmitButton(Subroutine.Deallocate, sent = entry.gone, enabled = live)
        StopNow(Amber.Faint, Amber.Dim)
    }
}

// ---- The furniture every Subroutine screen shares ------------------------------------------

/** How big a Replay dot is. Named because it is a decision about a finger, not a layout number. */
private val DOT: Dp = 44.u

/**
 * **Deallocate's dot, and the gap above it.**
 *
 * Smaller than [DOT] because a dot here is not a target — the *column* is, at a strip the full
 * height of the body, which is a far larger thing to hit than any single dot could be. What these
 * two have to do instead is let [DotColumns.MOST_DOTS] of them stand up inside the body without
 * being clipped: seven at 16 units with 3 between them is 130, against roughly 195 of body.
 */
private val DEAL_DOT: Dp = 16.u
private val DEAL_GAP: Dp = 3.u

/**
 * How tall the field the columns stand in is: the tallest distribution the panel accepts, exactly.
 *
 * Derived rather than typed, so the two numbers above and [DotColumns.MOST_DOTS] cannot disagree —
 * a field short of what the reader lets through is a clipped column, which is a *different
 * question* from the one the house asked.
 */
private val DEAL_FIELD: Dp =
    DEAL_DOT * DotColumns.MOST_DOTS + DEAL_GAP * (DotColumns.MOST_DOTS - 1)

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
 * **The one line that says the phone is no longer waiting for you — and then what the house said.**
 *
 * Three states in one fixed slot: empty while the player is still working, `RETURNED . WAITING`
 * once the entry has gone, and the house's verdict once it arrives. A fixed slot whether or not
 * there is anything in it, so nothing above it moves at the moment of completion or at the moment
 * of the answer — a layout that jumps is a change in lit area nobody authored, on the two frames
 * the player is most likely to be looking away.
 *
 * *Waiting* is the honest word for the middle state and the only one available: whether the entry
 * was right is the house's answer, and the device has no opinion to offer in the meantime. It is
 * the state every one of these screens sat in permanently before the loop had a verdict in it.
 *
 * ### The copy, and the register it is in (D-109)
 *
 * **Success is a damage report, and the house is not on your side.** *THE HOUSE GROWS WEAKER* is
 * the decision log's own candidate and the family it names; the house narrates its own decline
 * rather than congratulating anybody, and *"the house has it"* is ruled out explicitly — the house
 * must never be made to sound like an ally. The final wording is a build-time call and this is a
 * draft; **the register is not a draft.**
 *
 * **Failure is a rejection plus an instruction to re-scan in place**, and the instruction is load-
 * bearing rather than helpful: D-110 makes the walk back to the marker the price of a wrong
 * answer, so the line has to say where the player must stand. It says nothing about *what* was
 * wrong — a mismatch reports only that it was a mismatch (`gdd.md:608`).
 *
 * **Neither line offers a retry, and no control on any of these screens re-arms.** The entry has
 * gone, so every control is already inert, and it stays inert: the only way back to ready is a
 * fresh scan of the marker. A RETRY button here would be the phone re-arming work on its own
 * schedule, which D-110 rules out in the same breath.
 *
 * ### Identical for both roles, which is the whole of D-109
 *
 * There is no role in this file and none in this function. An Insider's fake is graded honestly
 * and answered in these exact words, on this exact schedule; the only difference is a number in
 * the authority's ledger that no screen can read. `SubroutineParityTest` renders both roles in all
 * three states and compares the pixels.
 */
@Composable
private fun ReturnLine(handedOver: Boolean, verdict: SubroutineVerdict?, color: Color) {
    // Full width and centred, not wrap-content. Three of the six place this at the root of the
    // screen's column, where wrapping put the line hard against the left edge while the other
    // three -- inside a centred column -- had it in the middle. The same sentence in two places
    // depending on which Subroutine you are doing is the opposite of the muscle memory the rest
    // of this file is built around.
    Box(Modifier.fillMaxWidth().height(9.u), contentAlignment = Alignment.Center) {
        val line = when {
            verdict == SubroutineVerdict.Accepted -> HOUSE_WEAKER
            verdict == SubroutineVerdict.Rejected -> RESCAN
            handedOver -> "RETURNED . WAITING"
            else -> null
        }
        if (line != null) Label(line, size = 6.0, color = color, tracking = 0.12)
    }
}

/**
 * **Drafted copy, flagged. The register is ruled (D-109); these exact words are not.**
 *
 * Named constants rather than literals at six call sites, so the two lines cannot drift into six
 * variants of themselves — and so that whoever settles the wording changes it in one place.
 */
const val HOUSE_WEAKER: String = "THE HOUSE GROWS WEAKER"
const val RESCAN: String = "REJECTED . RESCAN THE MARKER"

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
private fun MotionBudgetRow(
    ink: Color,
    value: Color,
    /**
     * The outline, which is [Amber.Edge] on seven of the eight and **black on Sniff until the
     * answer** — a hairline is still emitted light, and D-137 does not carve an exception for
     * chrome. It is a parameter rather than a `when` on the screen for the reason every colour on
     * these screens is one: the caller knows what it is drawing on and this row does not.
     */
    border: Color = Amber.Edge,
) {
    Row(
        Modifier.fillMaxWidth().border(1.u, border).padding(horizontal = 7.u, vertical = 6.u),
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
