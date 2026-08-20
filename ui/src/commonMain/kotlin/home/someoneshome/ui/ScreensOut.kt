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
    val go = navigator()
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
        Box(
            Modifier.padding(top = 10.u).border(1.u, Amber.Bright)
                .tap { go(ScreenId.GhostMeeting) }
                .padding(horizontal = 22.u, vertical = 12.u)
        ) {
            Label("I AM HERE", size = 8.5, color = Amber.Bright, tracking = 0.2)
        }
        Label("4 OF 6 CHECKED IN", size = 6.0, color = Amber.Faint, tracking = 0.12)
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
                Readout("0:24", size = 22.0, color = Amber.Bright, lineHeight = 1.0)
            }
            SegmentBar(
                total = PanelVals.VOTE_SEGMENTS,
                lit = vals.meetingLit,
                litColor = Amber.Bright,
                unlitColor = Amber.Edge,
                height = 5.u,
            )
            Label(
                "YOU HAVE NO VOICE AND NO VOTE.",
                size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.8,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("VOTES CAST", size = 6.5, color = Amber.Dim, tracking = 0.14)
            Label("3 OF 5", size = 6.5, color = Amber.Faint, tracking = 0.14)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
            vals.ballots.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().border(1.u, b.edge)
                        .padding(horizontal = 7.u, vertical = 6.u),
                    horizontalArrangement = Arrangement.spacedBy(6.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(b.by, modifier = Modifier.weight(1f), size = 8.5, color = b.ink, tracking = 0.06)
                    Label(b.arrow, size = 6.0, color = Amber.Faint, tracking = 0.1)
                    Label(b.forWhom, size = 8.5, color = b.forInk, tracking = 0.06)
                }
            }
        }

        Label(
            "THE DEACTIVATED SEE EVERY VOTE AND WHO CAST IT.\nTHE LIVING SEE ONLY THE TALLY.",
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
                Readout("${vals.outsideLit}/32", size = 15.0, color = Amber.Bright, lineHeight = 1.0)
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

        SettingRow("DO NOT DISTURB", "OFF", Amber.Mid, Amber.Dim)
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
        if (vals.insider) {
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
                Label(
                    "Thank you for your cooperation.",
                    size = 9.0, color = Amber.Bright, lineHeight = 1.5,
                )
            }
        }

        Column(
            Modifier.weight(1f).padding(horizontal = 8.u, vertical = 9.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            Label("THE INSIDERS WERE", size = 6.5, color = Amber.Dim, tracking = 0.16)
            Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
                NamedInsider("DANI")
                NamedInsider("TOMAS")
            }
            SummaryRow("EGRESS COMPLETE", "21:38 . BEACON")
            SummaryRow("SYSTEM INTEGRITY", "14 / 32")
        }
    }
}

@Composable
private fun NamedInsider(name: String) {
    Row(
        Modifier.fillMaxWidth().border(1.u, Amber.Bright).padding(7.u),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(name, size = 10.0, color = Amber.Bright)
        // The blackmail publishes: everyone learns who, and why.
        Label("BLACKMAILED", size = 6.0, color = Amber.Dim)
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
 */
@Composable
fun WinResidentsScreen() {
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

        Column(
            Modifier.weight(1f).padding(horizontal = 8.u, vertical = 9.u),
            verticalArrangement = Arrangement.spacedBy(6.u),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("THE RESIDENTS", size = 6.5, color = Amber.BoneDim, tracking = 0.16)
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
            BoneSummaryRow("SYSTEM INTEGRITY", "0 / 32")
            BoneSummaryRow("EGRESSES CONTAINED", "2")

            Box(Modifier.weight(1f))

            // Named without their alignment ever having been confirmed in play. The blackmail
            // publishes here, so everyone learns who and why at the same moment.
            BoneSummaryRow("WORKING FOR THE HOUSE", "DANI . TOMAS", nameInk = Amber.BoneFaint)
        }
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
