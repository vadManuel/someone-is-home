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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * Out, and the end of the round.
 *
 * **Nothing is ever confirmed.** Not when someone is revoked in the dark, not when the group
 * restrains someone at a meeting, not at any point until the round is over — and then the reveal
 * happens in the room, with the lights on, rather than on a screen.
 *
 * The sequencing here is the design. A revoked player sits in the dark with a dead device; they
 * stand and walk in when a meeting is called; only after that meeting ends does the outside view
 * appear. By then the room already knows they are out, so there is never a window where someone
 * outside the system knows something the living do not.
 */

/**
 * Access revoked. **Dark, so it never flares.**
 *
 * The dimmest ink in the palette, because the alternative — a bright screen at the moment of
 * revocation — would light the revoked player's position for the whole house and hand the
 * attacker away. It says only what has been withdrawn, and what to do.
 */
@Composable
fun RevokedScreen() {
    OutNotice(
        heading = "ACCESS\nREVOKED",
        withdrawn = "PERMISSIONS WITHDRAWN\nLAMP ALLOCATION WITHDRAWN\nREGISTRY ENTRY CLEARED",
        instruction = "STAY WHERE YOU ARE.\nDO NOT MOVE. DO NOT SPEAK.\n" +
            "WALK IN ONLY WHEN A\nMEETING IS CALLED.",
        footer = "NOTHING FURTHER IS REQUIRED OF YOU",
        step = OutStep.Dark,
    )
}

/**
 * Restrained — the same notice, **one luminance step brighter, everywhere.**
 *
 * It arrives mid-result, at a meeting, with every lamp in the house already up and the vote
 * public. There is nothing left to conceal, so the screen stops whispering: heading and
 * instruction go to full intensity, and every other element rises exactly one step with them.
 *
 * That step *is* the difference between the two ways out. A revoked player is told in the dark,
 * quietly, because the light would give them away; a restrained player is told in a lit room in
 * front of the people who did it.
 */
@Composable
fun RestrainedScreen() {
    OutNotice(
        heading = "YOU WERE\nRESTRAINED",
        withdrawn = "THE VOTE WENT AGAINST YOU\nREGISTRY ENTRY CLEARED",
        instruction = "STAY AT THE MEETING AREA.\nYOU HAVE NO VOICE.\n" +
            "DO NOT SPEAK AT ALL,\nNOT EVEN IN A MEETING.",
        footer = "THE OTHERS ARE STILL READING THE RESULT",
        step = OutStep.Lit,
    )
}

/**
 * Where on the four-step scale an out-notice sits.
 *
 * The two screens are the same structure at two intensities, and holding the ramp in one place is
 * what keeps them exactly one step apart. Written as two independent screens they would drift the
 * first time either was touched, and the drift would be invisible — nobody compares two screens
 * that are never on the same device at the same time.
 */
private enum class OutStep(
    val quiet: Color,
    val loud: Color,
    val rule: Color,
    val footer: Color,
) {
    /** Told in the dark. Every element at the bottom of the scale. */
    Dark(quiet = Amber.Faint, loud = Amber.Dim, rule = Amber.Edge, footer = Amber.Edge),

    /** Told in a lit room. Every element one step up. */
    Lit(quiet = Amber.Dim, loud = Amber.Bright, rule = Amber.Faint, footer = Amber.Faint),
}

@Composable
private fun OutNotice(
    heading: String,
    withdrawn: String,
    instruction: String,
    footer: String,
    step: OutStep,
) {
    Column(
        Modifier.fillMaxSize().background(Amber.Black).padding(horizontal = 16.u, vertical = 22.u),
        verticalArrangement = Arrangement.spacedBy(11.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label("RESIDENT STATUS", size = 6.0, color = step.quiet, tracking = 0.24)
        Label(
            heading,
            size = 17.0, color = step.loud, tracking = 0.1, lineHeight = 1.5,
            align = TextAlign.Center,
        )
        Box(Modifier.width(26.u).height(1.u).background(step.rule))
        Label(
            withdrawn,
            size = 6.5, color = step.quiet, tracking = 0.1, lineHeight = 2.2,
            align = TextAlign.Center,
        )
        Box(
            Modifier.padding(top = 6.u).border(1.u, step.rule)
                .padding(horizontal = 9.u, vertical = 8.u)
        ) {
            Label(
                instruction,
                size = 7.0, color = step.loud, tracking = 0.06, lineHeight = 2.1,
                align = TextAlign.Center,
            )
        }
        Label(
            footer,
            modifier = Modifier.padding(top = 2.u),
            size = 6.0, color = step.footer, tracking = 0.14,
        )
    }
}

/**
 * Stand and walk in.
 *
 * **Dark, and it used to be lit.** The earlier version reasoned that a meeting means every lamp
 * is up, so being bright was camouflage. It is not: a restrained player has no lamp allocation,
 * so a bright screen would mark them out as they crossed a room full of people who do.
 *
 * The instruction is absolute in a way no living player's is — *do not speak at all, not even in
 * the meeting.* Being out is a complete loss of channel, not a reduced one.
 */
@Composable
fun Ghost2Screen() {
    Column(
        Modifier.fillMaxSize().padding(14.u),
        verticalArrangement = Arrangement.spacedBy(10.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(
            "STAND\nAND WALK IN",
            size = 19.0, color = Amber.Bright, tracking = 0.1, lineHeight = 1.45,
            align = TextAlign.Center,
        )
        Box(Modifier.width(30.u).height(1.u).background(Amber.Faint))
        Label(
            "YOU HAVE NO VOICE NOW.\nDO NOT SPEAK AT ALL,\nNOT EVEN IN THE MEETING.",
            size = 7.0, color = Amber.Dim, tracking = 0.1, lineHeight = 2.1,
            align = TextAlign.Center,
        )
        // The same gate as the living's, and the same control drawing it: D-104 counts the out
        // players too, so a ghost whose phone has not checked in holds up the whole room's talk.
        CheckIn()
    }
}

/**
 * The meeting, watched from outside. **Every vote and who cast it.**
 *
 * The living get a count; someone outside the system gets the whole ballot. That asymmetry is
 * safe only because of when it arrives — the room already knows who is out — and because there is
 * **no channel**: they cannot act, speak, or message each other. Several people watch the same
 * truth side by side, unable to share one thought about it.
 */
@Composable
fun GhostMeetingScreen(vals: PanelVals) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Label("MEETING IN PROGRESS", size = 7.0, color = Amber.Dim, tracking = 0.16)

        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.Faint).padding(horizontal = 7.u, vertical = 8.u),
            verticalArrangement = Arrangement.spacedBy(5.u),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Label("VOTING ENDS IN", size = 6.0, color = Amber.Dim, tracking = 0.13)
                Readout(vals.countdown.text, size = 22.0, color = Amber.Bright, lineHeight = 1.0)
            }
            SegmentBar(
                total = PanelVals.VOTE_SEGMENTS,
                lit = vals.countdown.litOf(PanelVals.VOTE_SEGMENTS),
                litColor = Amber.Bright,
                unlitColor = Amber.Edge,
                height = 5.u,
            )
            Label(
                "YOU HAVE NO VOICE AND NO VOTE.",
                size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.8,
            )
        }

        // Counted off the rows below rather than written beside them. It was a literal that
        // happened to agree with them, which is the state two numbers for one fact start in.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("VOTES CAST", size = 6.5, color = Amber.Dim, tracking = 0.14)
            Label(
                "${OutsideView.cast} OF ${OutsideView.ballots.size}",
                size = 6.5, color = Amber.Faint, tracking = 0.14,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
            // A ballot that has not been cast yet is a null target, not the words STILL DECIDING
            // stored in a fixture — so a row cannot be styled as decided while reading as undecided,
            // and the count above cannot be computed off a string.
            OutsideView.ballots.forEach { ballot ->
                val decided = ballot.forWhom != null
                Row(
                    Modifier.fillMaxWidth().border(1.u, if (decided) Amber.Faint else Amber.Edge)
                        .padding(horizontal = 7.u, vertical = 6.u),
                    horizontalArrangement = Arrangement.spacedBy(6.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(
                        ballot.by,
                        modifier = Modifier.weight(1f),
                        size = 8.5, color = if (decided) Amber.Bright else Amber.Dim, tracking = 0.06,
                    )
                    if (decided) {
                        Label(OutsideView.CAST_FOR, size = 6.0, color = Amber.Faint, tracking = 0.1)
                    }
                    Label(
                        ballot.forWhom ?: "STILL DECIDING",
                        size = 8.5, color = if (decided) Amber.Bright else Amber.Faint, tracking = 0.06,
                    )
                }
            }
        }

        Label(
            "THE REVOKED SEE EVERY VOTE AND WHO CAST IT.\nTHE LIVING SEE ONLY THE TALLY.",
            modifier = Modifier.fillMaxWidth(),
            size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.8,
            align = TextAlign.Center,
        )
    }
}

/**
 * Outside the system: **both bars, and true occupancy.**
 *
 * The fiction is that deauthorisation cuts both ways — the house erased you from the registry, so
 * it can no longer see or control you, and its blackmail is worthless against someone it has
 * already deleted. You are the only person in the building outside the system.
 *
 * So you get what the whole house is denied: Resident progress *and* the real Egress number,
 * which no living player of either role can see, plus occupancy with no injected error and no
 * staleness. **Still counts, never identities, and never alignments** — the tools to deduce,
 * never the answer.
 */
@Composable
fun Ghost3Screen(vals: PanelVals) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.Faint).padding(horizontal = 7.u, vertical = 6.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Label("SYSTEM INTEGRITY", size = 6.5, color = Amber.Dim, tracking = 0.13)
                Readout("66%", size = 15.0, color = Amber.Bright, lineHeight = 1.0)
            }
            SegmentBar(
                total = PanelVals.METER_SEGMENTS, lit = vals.outsideLit,
                litColor = Amber.Mid, unlitColor = Amber.Edge, height = 6.u,
            )
        }

        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.Bright).padding(horizontal = 7.u, vertical = 6.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Label("EGRESS . TRUE", size = 6.5, color = Amber.Bright, tracking = 0.13)
                Readout("71%", size = 15.0, color = Amber.Bright, lineHeight = 1.0)
            }
            SegmentBar(
                total = PanelVals.METER_SEGMENTS, lit = vals.egressLit,
                litColor = Amber.Bright, unlitColor = Amber.Edge, height = 6.u,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            Box(
                Modifier.weight(1f).border(1.u, Amber.Dim).padding(vertical = 4.u),
                contentAlignment = Alignment.Center,
            ) { Label("GROUND", size = 6.5, color = Amber.Bright, tracking = 0.1) }
            Box(
                Modifier.weight(1f).border(1.u, Amber.Edge).padding(vertical = 4.u),
                contentAlignment = Alignment.Center,
            ) { Label("UPPER", size = 6.5, color = Amber.Dim, tracking = 0.1) }
        }

        Box(Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge)) {
            LivePlan(Plan.mapCells(), Modifier.fillMaxSize())
            PlanRoomLabels(Modifier.fillMaxSize())
            PlanCounts(Plan.trueCounts, Modifier.fillMaxSize())
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("TRUE OCCUPANCY", size = 5.5, color = Amber.Faint, tracking = 0.08)
            Label("NO CHANNEL AVAILABLE", size = 5.5, color = Amber.Faint, tracking = 0.08)
        }
    }
}

/**
 * Reconnecting. **The lamp holds its last authorised state throughout.**
 *
 * An unauthorised lamp change is an unauthored game signal, so a phone that has lost the host
 * must not go dark, brighten, or flicker — it keeps emitting exactly what it was emitting. The
 * screen says so, because the player needs to know not to move.
 */
@Composable
fun DisconnectScreen() {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(8.u),
    ) {
        Label("CONNECTION", size = 7.0, color = Amber.Dim, tracking = 0.16)
        Column(
            Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge),
            verticalArrangement = Arrangement.spacedBy(12.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.height(16.u),
                horizontalArrangement = Arrangement.spacedBy(3.u),
                verticalAlignment = Alignment.Bottom,
            ) {
                Block(4.u, 5.u, Amber.Faint)
                Block(4.u, 9.u, Amber.Edge)
                Block(4.u, 13.u, Amber.Edge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label(
                    "RECONNECTING",
                    size = 9.0, color = Amber.Mid, tracking = 0.14, lineHeight = 1.9,
                    align = TextAlign.Center,
                )
                Label("ATTEMPT 3 . 8S", size = 6.5, color = Amber.Dim, tracking = 0.14)
            }
        }
        Box(
            Modifier.fillMaxWidth().border(1.u, Amber.Dim).padding(7.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                "YOUR LAMP IS HOLDING ITS LAST STATE.\nSTAY WHERE YOU ARE.",
                size = 6.5, color = Amber.Mid, tracking = 0.1, lineHeight = 1.9,
                align = TextAlign.Center,
            )
        }
    }
}

/**
 * Settings: **you can see all of what you cannot do.**
 *
 * Everything is LOCKED and visibly so, which is the standing-condition pattern again — the list
 * is identical on every device, so nothing about which rows are locked can distinguish a player.
 * END SESSION is host-only and says so.
 */
@Composable
fun SettingsScreen() {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("SETTINGS", size = 7.0, color = Amber.Dim, tracking = 0.16)
        }

        // Off, and inert. It is here for the device fiction and nothing else.
        //
        // A WORKING ONE WOULD BREAK THE BANNER DIM. Every banner goes to everyone at once and
        // drops every lamp together; a player who had suppressed theirs would be the one phone
        // in the room that did not react, which is a beacon. They would also miss the Egress
        // alert. So it renders at the same intensity as the locked rows -- present, legible,
        // and visibly not a control.
        SettingRow("DO NOT DISTURB", "OFF", Amber.Faint, Amber.Faint)
        SettingRow("NETWORK", "LOCKED", Amber.Faint, Amber.Faint)
        SettingRow("PERMISSIONS", "LOCKED", Amber.Faint, Amber.Faint)
        SettingRow("OCCUPANCY", "LOCKED", Amber.Faint, Amber.Faint)

        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(7.u),
            verticalArrangement = Arrangement.spacedBy(4.u),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("ABOUT", size = 7.5, color = Amber.Mid)
                Label("HOUSE CONTROLLER", size = 7.5, color = Amber.Dim)
            }
            Label(
                "UPTIME 4331D 02:14\nFIRMWARE 1.0.4 . UNSUPPORTED",
                size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.9,
            )
        }

        Box(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.Dim).padding(8.u),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("END SESSION", size = 7.5, color = Amber.Bright)
            Label("HOST ONLY", size = 7.5, color = Amber.Dim)
        }
    }
}

@Composable
private fun SettingRow(name: String, value: String, nameInk: Color, valueInk: Color) {
    Row(
        Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(7.u),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label(name, size = 7.5, color = nameInk)
        Label(value, size = 7.5, color = valueInk)
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
 */
@Composable
fun WinInsidersScreen(vals: PanelVals) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Amber.Bright)
                .padding(start = 10.u, end = 10.u, top = 14.u, bottom = 12.u),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("ROUND OVER", size = 6.5, color = Amber.Black, tracking = 0.24)
            Label(
                "INSIDERS\nWIN",
                modifier = Modifier.padding(top = 6.u),
                size = 21.0, color = Amber.Black, tracking = 0.1, lineHeight = 1.4,
                align = TextAlign.Center,
            )
            Label(
                "THE HOUSE REACHED THE INTERNET",
                modifier = Modifier.padding(top = 7.u),
                size = 7.0, color = Amber.Black, tracking = 0.1,
            )
        }

        // The house speaks last, and only to the people it owned.
        vals.signOff?.let { HouseSignOff(it) }

        Column(
            Modifier.weight(1f).padding(horizontal = 8.u, vertical = 9.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            Label("THE INSIDERS WERE", size = 6.5, color = Amber.Dim, tracking = 0.16)
            Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
                vals.revealed.forEach { NamedInsider(it) }
            }
            SummaryRow("EGRESS COMPLETE", "21:38 . BEACON")
            SummaryRow("SYSTEM INTEGRITY", vals.integrityPercent)

            Box(Modifier.weight(1f))

            NewRound(Amber.Bright, Amber.Dim)
        }
    }
}

/**
 * **The house's message card, on both endings, on two phones.**
 *
 * Lifted out of the Insiders' ending so the Residents' ending can draw the identical card — the
 * house says something either way (`gdd.md:1051`) and a second hand-written copy of this block
 * would be the two screens disagreeing about its shape.
 *
 * Its number is withheld, as it has been all evening. That is the whole characterisation.
 */
@Composable
private fun HouseSignOff(body: String) {
    Column(
        Modifier.padding(horizontal = 8.u).padding(top = 7.u)
            .fillMaxWidth().border(1.u, Amber.Dim)
            .padding(horizontal = 7.u, vertical = 6.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("NUMBER WITHHELD", size = 5.5, color = Amber.Dim, tracking = 0.12)
            Label("21:38", size = 5.5, color = Amber.Faint, tracking = 0.12)
        }
        Label(body, size = 9.0, color = Amber.Bright, lineHeight = 1.5)
    }
}

/**
 * One Insider, named, **with what was held over them printed underneath**.
 *
 * The row used to say `BLACKMAILED` beside the name, which is the fact without the content — and
 * the content is the half that matters. *Everyone learns who **and why** — the petty, mundane
 * thing each Insider was coerced with.* The line is the reveal; the label was a placeholder for it.
 */
@Composable
private fun NamedInsider(who: RevealedInsider) {
    Column(
        Modifier.fillMaxWidth().border(1.u, Amber.Bright).padding(7.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Label(who.name, size = 10.0, color = Amber.Bright)
        Label(who.line, size = 6.5, color = Amber.Dim, lineHeight = 1.5)
    }
}

@Composable
private fun SummaryRow(name: String, value: String) {
    Row(
        Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label(name, size = 6.5, color = Amber.Dim)
        Label(value, size = 6.5, color = Amber.Mid)
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
fun WinResidentsScreen(vals: PanelVals) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Amber.SlateFill)
                .edgeLine(PanelSide.Bottom, Amber.Slate)
                .padding(start = 10.u, end = 10.u, top = 14.u, bottom = 12.u),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("ROUND OVER", size = 6.5, color = Amber.SlateInk, tracking = 0.24)
            Label(
                "RESIDENTS\nWIN",
                modifier = Modifier.padding(top = 6.u),
                size = 21.0, color = Amber.SlateInk, tracking = 0.1, lineHeight = 1.4,
                align = TextAlign.Center,
            )
            Label(
                "PERIMETER DISARMED . YOU CAN LEAVE",
                modifier = Modifier.padding(top = 7.u),
                size = 7.0, color = Amber.SlateInk, tracking = 0.1,
            )
        }

        // The house speaks last here too, and to the same two people. Nothing about a Resident win
        // makes it apologetic.
        vals.signOff?.let { HouseSignOff(it) }

        Column(
            Modifier.weight(1f).padding(horizontal = 8.u, vertical = 9.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("THE RESIDENTS", size = 6.5, color = Amber.BoneDim, tracking = 0.16)
                // Who is still standing, which everybody in the room can already see. A count of
                // seats is not a count of Insiders and divides nothing out -- see
                // PanelVals.integrityPercent for the number that would.
                Label("4 OF 6", size = 6.5, color = Amber.BoneFaint, tracking = 0.16)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
                listOf("ELLIOT" to "PRIYA", "MARCUS" to "ROSE").forEach { (a, b) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.u)) {
                        SurvivorChip(a, Modifier.weight(1f))
                        SurvivorChip(b, Modifier.weight(1f))
                    }
                }
            }
            BoneSummaryRow("SYSTEM INTEGRITY", vals.integrityPercent)
            BoneSummaryRow("EGRESSES CONTAINED", "2")

            Box(Modifier.weight(1f))

            // Named without their alignment ever having been confirmed in play, and the blackmail
            // publishes with them: everyone learns who AND why, at the same moment, on the winning
            // side's screen as much as on the losing side's. This used to be a summary row reading
            // `WORKING FOR THE HOUSE  DANI . TOMAS` -- the names without the reason, which is the
            // half that turns a result into a person.
            Label("WORKING FOR THE HOUSE", size = 6.5, color = Amber.BoneFaint, tracking = 0.16)
            Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
                vals.revealed.forEach { BoneNamedInsider(it) }
            }

            NewRound(Amber.BoneInk, Amber.BoneDim, border = Amber.BonePale)
        }
    }
}

/** [NamedInsider] in the bone palette, for the ending that comes back to the light. */
@Composable
private fun BoneNamedInsider(who: RevealedInsider) {
    Column(
        Modifier.fillMaxWidth().border(1.u, Amber.BoneInk).padding(7.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Label(who.name, size = 10.0, color = Amber.BoneInk)
        Label(who.line, size = 6.5, color = Amber.BoneDim, lineHeight = 1.5)
    }
}

/**
 * **NEW ROUND — the host's control, on both endings, and the app's only way to have a second one**
 * (D-157).
 *
 * *Two to three rounds in an evening* is the design's own expectation, and until this existed the
 * app had no way to meet it: both ending screens were graph terminals, and the evening finished
 * wherever the round did.
 *
 * ### Host-only, for LIGHTS OUT's reason and not quite in LIGHTS OUT's way
 *
 * It starts a round in front of the whole party, so it is the host's to press. Whether it takes
 * D-141's two-second hold is a build-time call and this does not take one: D-141's argument is
 * about a control **pressed in the dark**, where a thumb finds a tile by feel and a mis-tap cannot
 * be taken back. This is pressed in the light, with everyone watching, on the one screen in the
 * game where the lights are on and nobody is hiding their display. *(The label and the hold are
 * both recorded as copy-pending in revision 33; this is the shape, not the last word on either.)*
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
 */
@Composable
private fun NewRound(ink: Color, subInk: Color, border: Color = Amber.Dim) {
    val actions = LocalActions.current
    val hosting = LocalLobby.current.hosting
    Row(
        Modifier.fillMaxWidth().border(1.u, border)
            .then(if (hosting) Modifier.tapTarget(actions.newRound) else Modifier)
            .padding(8.u),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label("NEW ROUND", size = 7.5, color = if (hosting) ink else subInk)
        Label(if (hosting) "EVERYONE RETURNS" else "HOST ONLY", size = 7.5, color = subInk)
    }
}

@Composable
private fun SurvivorChip(name: String, modifier: Modifier = Modifier) {
    Box(modifier.border(1.u, Amber.BoneInk).padding(horizontal = 7.u, vertical = 6.u)) {
        Label(name, size = 9.0, color = Amber.BoneInk)
    }
}

@Composable
private fun BoneSummaryRow(name: String, value: String, nameInk: Color = Amber.BoneDim) {
    Row(
        Modifier.fillMaxWidth().border(1.u, Amber.BonePale).padding(horizontal = 7.u, vertical = 6.u),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label(name, size = 6.5, color = nameInk)
        Label(value, size = 6.5, color = Amber.BoneInk)
    }
}
