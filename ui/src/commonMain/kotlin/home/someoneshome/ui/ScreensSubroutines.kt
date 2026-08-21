package home.someoneshome.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

/**
 * **The Subroutines themselves — three of the design's ten, with their interaction.**
 *
 * The screens in this file echo input and nothing else. A Subroutine's pattern arrives as an
 * Effect, the screen displays it, captures taps and echoes them locally, and what the player
 * entered goes back as an Intent the *server* verifies (D-042). Nothing here says whether an
 * answer was right, because nothing here can: [SequenceEntry] and [ChoiceEntry] hold the player's
 * own input and have no field for the answer to sit in.
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
 * Two of the three have a presentation phase this port cannot build, and pretending otherwise
 * would be worse than leaving it out:
 *
 * - **Replay's dots flash in order.** A timed emission of light is the lamp changing, and the lamp
 *   is a pure function of state the core emitted (rule 5). The flash is therefore an Effect, and
 *   the loop is frozen. Built: the entry.
 * - **Handshake's rhythm is buzzed.** Haptics are the house's and this build has no phone attached
 *   to feel them on. Built: the return.
 * - **Parity Check has no timed presentation at all**, which is why it is the one of the three
 *   that is whole: the grid is on screen, the work is finding the odd cell in it, and the only
 *   thing the house owns is the verdict.
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

// ---- The furniture every Subroutine screen shares ------------------------------------------

/** How big a Replay dot is. Named because it is a decision about a finger, not a layout number. */
private val DOT: Dp = 44.u

/** What the chosen parity cell tags itself with, so a guard counts marks rather than pixels. */
const val PARITY_MARK: String = "subroutine-mark"

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
