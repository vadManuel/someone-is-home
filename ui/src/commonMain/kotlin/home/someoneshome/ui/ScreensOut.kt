package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Out, and the end of the round.
 *
 * **Nothing is ever confirmed.** Not when someone is revoked in the dark, not when the group
 * restrains someone at a meeting, not at any point until the round is over — and then the reveal
 * happens in the room, with the lights on, rather than on a screen.
 *
 * The sequencing here is the design. A revoked player sits in the dark with a flat device; they
 * stand and walk in when a meeting is called; only after that meeting ends does the outside view
 * appear. By then the room already knows they are out, so there is never a window where someone
 * outside the system knows something the living do not.
 */

/**
 * Access revoked. **A concealment screen, so it never flares** (D-077).
 *
 * Nothing on it is brighter than `#C08A2E`, because the alternative — a bright screen at the moment
 * of revocation — would light the revoked player's position for the whole house and hand the
 * attacker away. **The headline recedes on purpose** (D-202 names it as the one text in `#8A5C12`):
 * what the revoked player needs to read is what to do, not what happened.
 */
@Composable
fun RevokedScreen() = OutNotice(
    heading = "ACCESS\nREVOKED",
    instruction = "STAY WHERE YOU ARE.\nDO NOT SPEAK.",
    palette = Palette.CONCEAL,
    headingSize = 56,
    headingInk = Ramp.Line,
    rule = 1.dp,
)

/**
 * Restrained — **the same notice, on the dark palette.**
 *
 * It arrives mid-result, at a meeting, with every lamp in the house already up and the vote
 * public. There is nothing left to conceal, so the screen stops whispering: the headline and the
 * instruction are full ink.
 *
 * That step *is* the difference between the two ways out. A revoked player is told in the dark,
 * quietly, because the light would give them away; a restrained player is told in a lit room in
 * front of the people who did it.
 */
@Composable
fun RestrainedScreen() = OutNotice(
    heading = "YOU WERE\nRESTRAINED",
    instruction = "STAY AT THE MEETING AREA.\nDO NOT SPEAK.",
    palette = Palette.DARK,
    headingSize = 64,
    headingInk = Ramp.Ink,
    rule = 2.dp,
)

/**
 * **The two ways out share one layout** (D-202): a headline, a short rule and what to do. Only the
 * words and the palette change. Held in one place so the two cannot drift apart unseen — nobody
 * compares two screens that are never on the same device at the same time.
 */
@Composable
private fun OutNotice(
    heading: String,
    instruction: String,
    palette: Palette,
    headingSize: Int,
    headingInk: Color,
    rule: Dp,
) = Mockup {
    KitScreen {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Words(heading, size = headingSize, color = headingInk, lineHeight = 0.85, align = TextAlign.Center)
            Box(Modifier.size(width = 48.dp, height = rule).background(palette.line))
            Words(instruction, size = 30, color = palette.ink, lineHeight = 1.1, align = TextAlign.Center)
        }
    }
}

/**
 * Stand and walk in.
 *
 * **The living's walk-in, word for word but for the words** ([WalkIn]): the same headline, rule,
 * instruction and [CheckIn] block. A newly revoked player crosses a room the house has just lit,
 * among people who have not yet been told, so the screen in their hand must look exactly like the
 * one in everybody else's.
 *
 * The instruction is absolute in a way no living player's is — *do not speak.* Being out is a
 * complete loss of channel, not a reduced one.
 */
@Composable
fun Ghost2Screen() = WalkIn("STAND\nAND WALK IN", "DO NOT SPEAK.")

/**
 * The meeting, watched from outside. **Every vote and who cast it, live** (D-134, D-117, D-075).
 *
 * The living get a count; someone outside the system gets the whole ballot. That asymmetry is
 * safe only because of when it arrives — the room already knows who is out — and because there is
 * **no channel**: they cannot act, speak, or message each other. Several people watch the same
 * truth side by side, unable to share one thought about it.
 *
 * ### What arrives here, and what is drawn from it
 *
 * The rows are [PanelVals.ballots], which the house fills one selection at a time
 * (`Effect.VoteSelectionShown`, permitted to the out classes and to nobody else). A row with no
 * target yet is a **null**, not the words STILL DECIDING sitting in a fixture — so a row cannot be
 * styled as decided while reading as undecided, and the count above it cannot be computed off a
 * string. The port's five stand in while there is no house.
 *
 * **The clock is the phase's** ([PanelVals.meetingClockLabel]), set into the top edge of its box.
 * This screen does not move while a meeting runs four phases underneath it, so it is the one
 * surface in the game that has to be told which clock it is showing.
 *
 * ### The one thing this screen does not draw is a readiness control
 *
 * A ghost cannot speak in the discussion, so there is nothing of theirs to be ready with (D-147),
 * and `ReadyProgressed` is denied to the out classes. **Presence is everybody's; readiness is the
 * living's** — which is why [Ghost2Screen] above draws the check-in gate and this screen does not.
 */
@Composable
fun GhostMeetingScreen(vals: PanelVals) = Mockup {
    KitScreen {
        KitHeader("MEETING IN PROGRESS", Palette.DARK)

        KitLegendBox(vals.meetingClockLabel, Ramp.Mid, Ramp.Line) {
            // **No window, no clock, and the bar goes with it.** The check-in gate closes on the
            // last player walking in rather than on a timer (D-104), so there is nothing for a bar
            // to be a fraction of -- and an empty bar under a `0` reads as a clock that has run
            // out, on the one screen whose reader has nothing to do but read it.
            if (vals.hasMeetingClock) {
                Words(vals.countdown.text, size = 64, lineHeight = 0.8)
                KitMeter(
                    PanelVals.VOTE_SEGMENTS, vals.countdown.litOf(PanelVals.VOTE_SEGMENTS),
                    Ramp.Ink, Ramp.Faint, height = 10.dp,
                )
            }
        }

        // Counted off the rows below rather than written beside them. It was a literal that
        // happened to agree with them, which is the state two numbers for one fact start in.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Words("VOTES CAST", Modifier.weight(1f), size = 22, color = Ramp.Dim, lineHeight = 1.0)
            Words("${vals.votesCast} OF ${vals.ballots.size}", size = 22, color = Ramp.Dim, lineHeight = 1.0)
        }

        KitRules(Palette.DARK) {
            vals.ballots.forEach { ballot ->
                val decided = ballot.forWhom != null
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .edgeLine(PanelSide.Bottom, Ramp.Faint, 1.dp).padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Words(
                        ballot.by, Modifier.weight(1f),
                        size = 28, color = if (decided) Ramp.Ink else Ramp.Mid, lineHeight = 1.0,
                    )
                    if (decided) Words(OutsideView.CAST_FOR, size = 22, color = Ramp.Dim, lineHeight = 1.0)
                    Words(
                        ballot.forWhom ?: "STILL DECIDING",
                        size = if (decided) 28 else 22, color = if (decided) Ramp.Ink else Ramp.Dim,
                        lineHeight = 1.0,
                    )
                }
            }
        }
    }
}

/**
 * Outside the system: **both meters, and true occupancy.**
 *
 * The fiction is that deauthorisation cuts both ways — the house erased you from the registry, so
 * it can no longer see or control you, and its blackmail is worthless against someone it has
 * already deleted. You are the only person in the building outside the system.
 *
 * So you get what the whole house is denied: Resident progress *and* the real Egress number,
 * which no living player of either role can see, plus occupancy with no injected error and no
 * staleness, every count in full ink. **Still counts, never identities, and never alignments** —
 * the tools to deduce, never the answer. Nor is there any channel: the screen offers nothing to
 * press.
 *
 * ### Both numbers are percentages, and both are computed rather than written (D-103, D-153)
 *
 * D-153's rule has no exception for the one audience that can act on nothing: the denominator is
 * `(seats − insiders) × 7`, and printing it hands the reader with nothing else to do all evening
 * the Insider count by division — for this round and, since an evening is two to three rounds
 * (D-157), for every round after it. They come off [PanelVals.outsidePercent] and
 * [PanelVals.egressPercent], which are the same fraction the meters are drawn at.
 *
 * ### ⚠️ *Live* is D-134's word and it is not true of the meter yet
 *
 * No effect carries System Integrity to any client at all, so the fraction below is still the
 * port's drawn one. The Egress has a carrier (`Effect.EgressHeld`) but not the duration it is a
 * fraction of. Escalated; the shape is right and the sender is missing.
 *
 * ### The Egress widget is empty until there is an Egress
 *
 * See [GhostEgressWidget]. It is the one block on this screen that is not always saying something,
 * because it is the one whose subject is not always happening.
 */
@Composable
fun Ghost3Screen(vals: PanelVals) = Mockup {
    KitScreen(
        padding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        gap = 10.dp,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CouchMeter(
                Modifier.weight(1f), Ramp.Line, 1.dp, "SYSTEM INTEGRITY", Ramp.Mid,
                vals.outsidePercent, vals.outsideLit,
            )
            GhostEgressWidget(vals, Modifier.weight(1f))
        }
        FloorStrip(Palette.DARK)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            KitLivePlan(Plan.trueCounts, Modifier.fillMaxSize())
        }
    }
}

/**
 * One of the couch's two meters: a name, a percentage and the segments, in a box. The two sit
 * side by side at the same size whatever their edge, so an edge two units wide gives up one unit
 * of padding to it.
 */
@Composable
private fun CouchMeter(
    modifier: Modifier,
    edge: Color,
    edgeWidth: Dp,
    name: String,
    nameInk: Color,
    percent: String,
    lit: Int?,
) {
    Column(
        modifier.border(edgeWidth, edge).padding(horizontal = 13.dp - edgeWidth, vertical = 11.dp - edgeWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Words(name, size = 18, color = nameInk, lineHeight = 1.0, maxLines = 1)
        Words(percent, size = 44, lineHeight = 0.8)
        // A meter with nothing to be a fraction of keeps its height and draws nothing.
        if (lit != null) {
            KitMeter(PanelVals.METER_SEGMENTS, lit, Ramp.Mid, Ramp.Faint, height = 8.dp, gap = 1.dp)
        } else {
            Box(Modifier.fillMaxWidth().height(8.dp))
        }
    }
}

/**
 * **The real Egress number, and only while there is one** (`gdd.md:1014`).
 *
 * The couch is the one audience in the building allowed to watch this move, and it watches from a
 * single screen for the entire round — so unlike the living's widget, which is a screen the house
 * pushes and therefore cannot be wrong about whether a crisis is on, this one has to be able to be
 * **empty**. It says `EGRESS . <type>` and the live percentage, edged in full ink, while an Egress
 * runs, and it is a plain box the rest of the time: no percentage, no type, no word.
 *
 * ### `EGRESS . TRUE` is gone, and it was two meanings in one shape
 *
 * The literal here read TRUE as an *adjective* — the true Egress number. But the living's widget
 * draws `"EGRESS . ${type}"`, where the second half **is** the type: `EGRESS . BEACON`. One screen
 * apart, one shape, two grammars, and the couch is the reader with the most time to notice. It says
 * the type, which is the reading the shape already had everywhere else.
 *
 * ### The frame stays, and only the frame
 *
 * The box is drawn at its full size either way so nothing beside or below it moves when an Egress
 * fires — a layout that moved would be a second, silent announcement of the crisis, arriving before
 * the widget said anything. What changes is the edge and what is inside it.
 *
 * **Empty is not zero.** Nothing draws a meter with no lit segments here: a drained meter is a
 * statement about an Egress that is nearly contained, and *no Egress at all* is not a value on that
 * scale.
 */
@Composable
private fun GhostEgressWidget(vals: PanelVals, modifier: Modifier) {
    val running = vals.egressRunning
    // Empty strings rather than absent lines: the height comes off the same type the populated
    // widget uses, so the two states are the same box without a measurement written down twice.
    CouchMeter(
        modifier,
        edge = if (running) Ramp.Ink else Ramp.Line,
        edgeWidth = if (running) 2.dp else 1.dp,
        name = if (running) "EGRESS . ${vals.egressType}" else "",
        nameInk = Ramp.Ink,
        percent = if (running) vals.egressPercent else "",
        lit = if (running) vals.egressLit else null,
    )
}

/**
 * Reconnecting. **The lamp holds its last authorised state throughout.**
 *
 * An unauthorised lamp change is an unauthored game signal, so a phone that has lost the host
 * must not go dark, brighten, or flicker — it keeps emitting exactly what it was emitting. The
 * screen says only what the player must do: stay where they are.
 *
 * **A concealment screen, on the revoked screen's palette** (D-077, D-200): nothing on it is above
 * `#C08A2E`, because a reconnecting phone lit brighter than its neighbours is the flare D-077
 * exists to prevent. The status strip dims with it — see `PanelVals.concealed`.
 */
@Composable
fun DisconnectScreen() = Mockup {
    val p = Palette.CONCEAL
    KitScreen {
        KitHeader("CONNECTION", p)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Words("RECONNECTING", size = 52, color = p.ink, lineHeight = 0.85, align = TextAlign.Center)
            KitCaption("ATTEMPT 3 . 8S", p, size = 24, center = true)
        }
        Words(
            "STAY WHERE YOU ARE.", Modifier.fillMaxWidth(),
            size = 28, color = p.ink, lineHeight = 1.1, align = TextAlign.Center,
        )
    }
}

/**
 * Settings: **you can see all of what you cannot do.**
 *
 * Everything is LOCKED and visibly so, which is the standing-condition pattern again — the list
 * is identical on every device, so nothing about which rows are locked can distinguish a player.
 * END SESSION is host-only and says so.
 *
 * Drawn as the mockup draws it (D-202): a hairline list rather than boxed rows, and the one
 * boxed thing, END SESSION, at the foot.
 */
@Composable
fun SettingsScreen() = Mockup {
    val p = Palette.DARK
    KitScreen {
        KitHeader("SETTINGS", p, back = ScreenId.Home)
        KitRules(p) {
            // Off, and inert: there is no tap on this row. It is here for the device fiction.
            //
            // A WORKING ONE WOULD BREAK THE BANNER DIM. Every banner goes to everyone at once and
            // drops every lamp together; a player who had suppressed theirs would be the one phone
            // in the room that did not react, which is a beacon. They would also miss the Egress
            // alert. The mockup lifts its name to ink, a setting with a value, and keeps OFF in dim.
            KitInfo("DO NOT DISTURB", "OFF", p, valueColor = p.dim, minHeight = 56.dp, keyColor = p.ink)
            for (name in listOf("NETWORK", "PERMISSIONS", "OCCUPANCY")) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .edgeLine(PanelSide.Bottom, p.faint, 1.dp).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Words(name, Modifier.weight(1f), size = 26, color = p.dim, lineHeight = 1.0)
                    Words("LOCKED", size = 26, color = p.dim, lineHeight = 1.0)
                }
            }
            Column(
                Modifier.fillMaxWidth().edgeLine(PanelSide.Bottom, p.faint, 1.dp)
                    .padding(horizontal = 2.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Words("ABOUT", Modifier.weight(1f), size = 26, color = p.ink, lineHeight = 1.0)
                    Words("HOUSE CONTROLLER", size = 26, color = p.mid, lineHeight = 1.0)
                }
                KitCaption("UPTIME 4331D 02:14\nFIRMWARE 1.0.4 . UNSUPPORTED", p)
            }
        }
        KitSpacer()
        // Not a control on any phone, the host's included: it says whose it would be.
        Row(
            Modifier.fillMaxWidth().cssBorder(1.dp, p.line).heightIn(min = 56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Words("END SESSION", Modifier.weight(1f), size = 26, color = p.ink, lineHeight = 1.0)
            Tag("HOST ONLY", p.dim)
        }
    }
}

/**
 * Insiders win — **named, at last.**
 *
 * The only moment the app ever states an alignment, and it comes after the round is over. The
 * house texts the Insiders alone, in the same customer-service register it used all evening: it
 * is intimate only with the people it owns, right to the end.
 *
 * ### What arrives here, and what is drawn from it
 *
 * This screen is a **house push** ([Flow.housePushes]) and not somewhere a phone walks. Three
 * effects land together: the ending itself, the reveal that fills [PanelVals.revealed], and the
 * sign-off that fills [PanelVals.signOff] on two phones and nobody else's.
 *
 * ### The meter is a percentage, here as everywhere (D-153)
 *
 * The row under the names used to read `SYSTEM INTEGRITY 14 / 32` and that was a defect twice
 * over: the denominator divides out the Insider count, and the `32` was a fossil of a count F-005
 * had already corrected. The endings look like the safe place for a real number — the round is
 * over, nothing can be acted on — and they are not, because the app is played two to three rounds
 * in an evening (D-157) and a denominator printed at the end of round one is a denominator
 * carried into round two.
 *
 * ### The two endings are one set of parts in two palettes
 *
 * [EndingBanner], [HouseSignOff], [Revealed] and [NewRound] are drawn once and take the palette,
 * so the dark ending and the lights-on one cannot disagree about a shape. Only the words, the
 * palette and the order of the middle differ, which is how the mockup draws them.
 */
@Composable
fun WinInsidersScreen(vals: PanelVals) = Mockup {
    val p = Palette.DARK
    KitScreen(padding = ENDING_PADDING, gap = 10.dp) {
        EndingBanner("INSIDERS WIN", "THE HOUSE REACHED THE INTERNET", p, lineSize = 24)
        // The house speaks last, and only to the people it owned.
        vals.signOff?.let { HouseSignOff(it, p) }
        Revealed("THE INSIDERS WERE", vals.revealed, p, labelInk = p.mid)
        KitRules(p) {
            KitInfo("EGRESS COMPLETE", "21:38 . BEACON", p, valueColor = p.mid, minHeight = 40.dp)
            KitInfo("SYSTEM INTEGRITY", vals.integrityPercent, p, valueColor = p.mid, minHeight = 40.dp)
        }
        NewRound(p)
    }
}

/**
 * Residents win — **back to the light.**
 *
 * The only screen that returns to the bone field mid-round-end, and the palette is doing the
 * work: the perimeter is disarmed, the lights are on, the device is an organiser again. Slate
 * rather than amber for the banner, because slate has only ever meant "before the round".
 *
 * **PERIMETER DISARMED is not written twice.** The status bar carries it off
 * [PanelVals.disarmedGlyph], which is true on this screen and on no other — so the moment the
 * house pushes this ending, the row that has read `ARMED` for twenty-five minutes changes for
 * everybody at once (`gdd.md:203`). The banner's own line says the same thing to the eye that is
 * looking at the middle of the screen rather than the top of it.
 *
 * **The reveal is the same reveal** the Insiders' ending draws, off the same [PanelVals.revealed],
 * and the house's message card is the same card: it has something to say when it loses too, and
 * the register does not improve. What differs between the two screens is the palette and the
 * headline — the disclosure is identical, because it is one disclosure to one room.
 */
@Composable
fun WinResidentsScreen(vals: PanelVals) = Mockup {
    val p = Palette.BONE
    KitScreen(padding = ENDING_PADDING, gap = 8.dp) {
        EndingBanner("RESIDENTS WIN", "PERIMETER DISARMED . YOU CAN LEAVE", p, lineSize = 20)
        // The house speaks last here too, and to the same two people. Nothing about a Resident win
        // makes it apologetic -- but the lights are on, so it says it in bone, like everything
        // else on this screen.
        vals.signOff?.let { HouseSignOff(it, p) }
        Row(Modifier.fillMaxWidth()) {
            Words("THE RESIDENTS", Modifier.weight(1f), size = 22, color = p.dim, lineHeight = 1.0)
            // Who is still standing, which everybody in the room can already see. A count of
            // seats is not a count of Insiders and divides nothing out -- see
            // PanelVals.integrityPercent for the number that would.
            Words("4 OF 6", size = 22, color = p.dim, lineHeight = 1.0)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ELLIOT" to "PRIYA", "MARCUS" to "ROSE").forEach { (a, b) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SurvivorChip(a, p, Modifier.weight(1f))
                    SurvivorChip(b, p, Modifier.weight(1f))
                }
            }
        }
        KitRules(p) {
            KitInfo("SYSTEM INTEGRITY", vals.integrityPercent, p, minHeight = 36.dp)
            KitInfo("EGRESSES CONTAINED", "2", p, minHeight = 36.dp)
        }
        // Named without their alignment ever having been confirmed in play, and the blackmail
        // publishes with them: everyone learns who AND why, at the same moment, on the winning
        // side's screen as much as on the losing side's.
        Revealed("WORKING FOR THE HOUSE", vals.revealed, p, labelInk = p.dim)
        NewRound(p)
    }
}

/** Both endings open a little lower than `kit.py`'s default, as the meeting band does. */
private val ENDING_PADDING = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)

/**
 * **ROUND OVER and who won, as the palette's filled block.** Amber with black words on the dark
 * ending (which is why `WinInsiders` is in [PanelVals.AMBER_BLOCKS]), slate on the lights-on one.
 */
@Composable
private fun EndingBanner(headline: String, line: String, p: Palette, lineSize: Int) {
    Column(
        Modifier.fillMaxWidth().background(p.fill).cssBorder(1.dp, p.fillEdge).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tag("ROUND OVER", p.onFill, LABEL_BOX, tracking = 0.2)
        Words(headline, size = 52, color = p.onFill, lineHeight = 0.85, align = TextAlign.Center)
        Words(line, size = lineSize, color = p.onFill, lineHeight = 1.0, align = TextAlign.Center)
    }
}

/**
 * **The house's message card, on both endings, on two phones.**
 *
 * One card for both endings — the house says something either way (`gdd.md:1051`) and a second
 * hand-written copy of this block would be the two screens disagreeing about its shape.
 *
 * **It signs as HOUSE** (D-201): the same sender as the thread, because there is one house and it
 * has one name. NUMBER WITHHELD is a player's voice, the one that says `you're up`, and the house
 * signing off under it would make the house two people.
 *
 * ### The palette is a parameter, because one of the two endings comes back to the light
 *
 * `Unfortunate.` was once **the one amber element on a sage screen**, and the reason nothing caught
 * it is that it reaches only an Insider's phone: a two-role layout sweep compares geometry and this
 * is a palette. Passing the palette in keeps the *shape* shared, which is what the lift was for.
 */
@Composable
private fun HouseSignOff(body: String, p: Palette) {
    Column(
        Modifier.fillMaxWidth().cssBorder(1.dp, if (p.bone) p.ink else p.line)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Tag("HOUSE", if (p.bone) p.dim else p.mid, Modifier.weight(1f).then(LABEL_BOX))
            Tag("21:38", p.dim, LABEL_BOX)
        }
        Words(body, size = 26, color = p.ink, lineHeight = 1.05)
    }
}

/**
 * **The Insiders, named, each with what was held over them printed underneath.**
 *
 * The row used to say `BLACKMAILED` beside the name, which is the fact without the content — and
 * the content is the half that matters. *Everyone learns who **and why** — the petty, mundane
 * thing each Insider was coerced with.* The line is the reveal; the label was a placeholder for it.
 */
@Composable
private fun Revealed(heading: String, insiders: List<RevealedInsider>, p: Palette, labelInk: Color) {
    Tag(heading, labelInk, LABEL_BOX)
    for (who in insiders) {
        Column(
            Modifier.fillMaxWidth().cssBorder(2.dp, p.ink).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Words(who.name, size = 28, color = p.ink, lineHeight = 1.0)
            Words(who.line, size = 20, color = p.dim, lineHeight = 1.05)
        }
    }
}

/**
 * **NEW ROUND — the host's control, on both endings, and the app's only way to have a second one**
 * (D-157, D-177).
 *
 * *Two to three rounds in an evening* is the design's own expectation, and until this existed the
 * app had no way to meet it: both ending screens were graph terminals, and the evening finished
 * wherever the round did.
 *
 * ### Host-only, and no hold
 *
 * It starts a round in front of the whole party, so it is the host's to press. It takes no
 * two-second hold (D-177): D-141's argument is about a control **pressed in the dark**, and this is
 * pressed in the light, with everyone watching.
 *
 * ### It returns everybody, and the house is what returns them
 *
 * The host's tap goes to the house and the house pushes every phone back to the lobby — the same
 * arrangement LIGHTS OUT has, and for the same reason: a device that walked itself to the lobby on
 * its own button press would be one phone leaving a round the other seven were still in. What
 * survives is the home, the seats and the settings; what does not is every one line, dropped at
 * the moment the round ended and dropped again here (D-116, D-157).
 *
 * On a phone that is not hosting it is present and inert with `HOST ONLY` beside it, exactly as
 * `END SESSION` is on the settings screen — a control that appeared only on one phone would be
 * a layout that moves under a thumb, and one that vanished would be a host wondering where it went.
 * The mockup draws only the host's face; the other phones' words drop to dim, since they press
 * nothing.
 */
@Composable
private fun NewRound(p: Palette) {
    val actions = LocalActions.current
    val hosting = LocalLobby.current.hosting
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).border(1.dp, if (p.bone) p.ink else p.line)
            .then(if (hosting) Modifier.tapTarget(actions.newRound) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Words("NEW ROUND", Modifier.weight(1f), size = 26, color = if (hosting) p.ink else p.dim, lineHeight = 1.0)
        Tag(if (hosting) "EVERYONE RETURNS" else "HOST ONLY", p.dim)
    }
}

/** One of the Residents still standing, on the lights-on ending. */
@Composable
private fun SurvivorChip(name: String, p: Palette, modifier: Modifier = Modifier) {
    Box(
        modifier.cssBorder(1.dp, p.ink).heightIn(min = 34.dp).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Words(name, size = 24, color = p.ink, lineHeight = 1.0)
    }
}

/**
 * **A border the way CSS draws one on a `div`: outside the content box, adding to its size.**
 * Compose's `border` paints inside the bounds it is given, so a mockup box with a 2px edge came up
 * 4 units short and everything under it rode up the panel. (The mockup's `<button>`s are
 * border-box, so NEW ROUND keeps the plain border.)
 */
private fun Modifier.cssBorder(width: Dp, color: Color): Modifier = border(width, color).padding(width)

/**
 * **A Silkscreen label's line box, 12 tall as the mockup's `line-height: 1` makes it.** The font's
 * own ascent and descent come to about 16, and [Tag] lets them stand, so each label in a stack ran
 * the column 4 units long and pushed the Residents' NEW ROUND into the foot padding.
 */
private val LABEL_BOX = Modifier.height(12.dp)
