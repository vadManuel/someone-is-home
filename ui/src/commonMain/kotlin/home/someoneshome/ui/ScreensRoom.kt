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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * The room — where the phones get out of the way.
 *
 * The meeting is designed under one constraint: **minimal phone interaction for anything social.**
 * A clock and one control during discussion; names and Skip during the vote; a result that shows
 * attribution and nothing else. The app supplies constraints; the players supply the accusations.
 */

/** You called it. Every phone in the building rings, and you wait with everyone else. */
@Composable
fun CallingScreen() {
    Column(
        Modifier.fillMaxSize().padding(12.u),
        verticalArrangement = Arrangement.spacedBy(7.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(
            "CALLING",
            size = 6.5, color = Amber.Dim.copy(alpha = ringPulse()), tracking = 0.24,
        )
        Label(
            "HOUSE\nMEETING",
            size = 17.0, color = Amber.Bright, tracking = 0.12, lineHeight = 1.5,
            align = TextAlign.Center,
        )
        Label(
            "CALLING ALL\nRESIDENTS",
            size = 6.5, color = Amber.Dim, tracking = 0.14, lineHeight = 2.0,
            align = TextAlign.Center,
        )
        Row(
            Modifier.padding(top = 10.u),
            horizontalArrangement = Arrangement.spacedBy(4.u),
        ) {
            repeat(4) { Box(Modifier.size(8.u).background(Amber.Bright)) }
            repeat(2) { Box(Modifier.size(8.u).border(1.u, Amber.Faint)) }
        }
        Label("4 OF 6 ANSWERED", size = 6.0, color = Amber.Dim, tracking = 0.12)
        Label(
            "WAITING FOR THE REST",
            modifier = Modifier.padding(top = 12.u),
            size = 6.5, color = Amber.Dim, tracking = 0.16,
        )
    }
}

/**
 * An incoming call **with the caller named**.
 *
 * Naming the caller is deliberate: the meeting has an author, and the room can ask them why. It
 * is one of the few facts the app volunteers, and it is one everybody gets equally.
 */
@Composable
fun CallScreen() {
    val go = navigator()
    IncomingCall(caller = "PRIYA", reason = "HOUSE MEETING") { go(ScreenId.Assemble) }
}

/**
 * The same call, different header.
 *
 * **Same screen, deliberately.** Finding a revoked player and calling a meeting produce identical
 * device behaviour, so nobody can tell from a neighbour's phone which one happened.
 */
@Composable
fun FoundScreen() {
    val go = navigator()
    IncomingCall(caller = "ELLIOT", reason = "REVOKED\nRESIDENT FOUND") { go(ScreenId.Assemble) }
}

@Composable
private fun IncomingCall(caller: String, reason: String, onAnswer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(10.u),
        verticalArrangement = Arrangement.spacedBy(6.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(
            "INCOMING",
            size = 6.5, color = Amber.Dim.copy(alpha = ringPulse()), tracking = 0.24,
        )
        Label(caller, size = 22.0, color = Amber.Bright, tracking = 0.12)
        Label(
            reason,
            size = 8.0, color = Amber.Mid, tracking = 0.16, lineHeight = 1.8,
            align = TextAlign.Center,
        )
        Box(
            Modifier.padding(top = 16.u).border(1.u, Amber.Bright).tap(onAnswer)
                .padding(horizontal = 26.u, vertical = 11.u)
        ) {
            Label("ANSWER", size = 9.0, color = Amber.Bright, tracking = 0.22)
        }
    }
}

/**
 * Answered: lamps up, nobody speaks yet.
 *
 * The instruction not to speak until everyone has arrived is a *social* rule the app states and
 * never enforces — physical conduct is contract, never software.
 */
@Composable
fun AssembleScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(14.u),
        verticalArrangement = Arrangement.spacedBy(10.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label("CALL ANSWERED . PRIYA", size = 6.5, color = Amber.Dim, tracking = 0.22)
        Label(
            "WALK TO THE\nMEETING AREA",
            size = 17.0, color = Amber.Bright, tracking = 0.1, lineHeight = 1.5,
            align = TextAlign.Center,
        )
        Box(Modifier.width(30.u).height(1.u).background(Amber.Faint))
        Label(
            "DO NOT SPEAK UNTIL\nEVERYONE HAS ARRIVED.",
            size = 7.0, color = Amber.Dim, tracking = 0.1, lineHeight = 2.1,
            align = TextAlign.Center,
        )
        Box(
            // The design's own handler here is a dangling reference (`goNotices`, which its
            // renderVals never defines), so the button does nothing in the prototype. Its device
            // shell auto-advances assemble -> notice, which is plainly the intent.
            Modifier.padding(top = 10.u).border(1.u, Amber.Bright)
                .tap { go(ScreenId.Notice) }
                .padding(horizontal = 22.u, vertical = 11.u)
        ) {
            Label("I AM HERE", size = 8.5, color = Amber.Bright, tracking = 0.2)
        }
        Label("4 OF 6 CHECKED IN", size = 6.0, color = Amber.Faint, tracking = 0.12)
    }
}

/**
 * The house's notices — **once, at the top of the meeting, then gone.**
 *
 * This is rule 6 in its constructive form: a dead radio made a living player invisible, and the
 * house announces the gap rather than letting it look like evidence. It reports an *interval of
 * missing data*, never a person's behaviour.
 */
@Composable
fun NoticeScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Label("HOUSE MEETING . NOTICES", size = 7.0, color = Amber.Dim, tracking = 0.16)
        InfoBox(border = Amber.Bright, padding = 8.u, gap = 5.u) {
            Label("NOTICE 1 OF 1", size = 6.0, color = Amber.Dim, tracking = 0.14)
            Label(
                "Resident MARCUS was unreachable 21:04–21:07. Occupancy data for this interval " +
                    "is incomplete.",
                size = 8.5, color = Amber.Bright, lineHeight = 1.7,
            )
        }
        Box(Modifier.weight(1f))
        PanelButton(
            "DISMISS",
            border = Amber.Dim, ink = Amber.Bright,
            size = 7.5, verticalPadding = 9.u,
            onClick = { go(ScreenId.Discussion) },
        )
    }
}

/**
 * Discussion: **a clock and one control.**
 *
 * Ninety seconds, and unanimous READY skips ahead. Nothing else is on screen because everything
 * that matters is happening in the room.
 */
@Composable
fun DiscussionScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Label("HOUSE MEETING . DISCUSSION", size = 7.0, color = Amber.Dim, tracking = 0.16)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Readout("1:04", size = 62.0, color = Amber.Bright, lineHeight = 0.9)
            Label("REMAINING", size = 6.5, color = Amber.Dim, tracking = 0.2)
        }
        Row(Modifier.fillMaxWidth().height(5.u), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            repeat(3) { Box(Modifier.weight(1f).fillMaxHeight().background(Amber.Bright)) }
            repeat(3) { Box(Modifier.weight(1f).fillMaxHeight().background(Amber.Edge)) }
        }
        Label(
            "3 OF 6 READY . UNANIMOUS SKIPS AHEAD",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Faint, tracking = 0.12, align = TextAlign.Center,
        )
        PanelButton(
            "READY TO VOTE",
            border = Amber.Dim, ink = Amber.Bright,
            tracking = 0.18, verticalPadding = 11.u,
            onClick = { go(ScreenId.Vote) },
        )
    }
}

/**
 * The vote. **You see how many have voted, never what.**
 *
 * Changeable until the clock ends, and **not voting is an abstention** rather than a Skip — which
 * matters, because ties resolve to Skip and an abstention is therefore not the same act.
 */
@Composable
fun VoteScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Label("RESTRAIN . VOTE", size = 7.0, color = Amber.Dim, tracking = 0.14)
            Readout("0:38", size = 13.0, color = Amber.Bright)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
            VoteRow("PRIYA")
            VoteRow("MARCUS", held = true, note = "YOUR VOTE")
            VoteRow("DANI")
            VoteRow("ROSE")
            VoteRow("TOMAS")
            Row(
                Modifier.fillMaxWidth().dashedBorder(Amber.Faint).padding(7.u),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("SKIP", size = 8.0, color = Amber.Dim)
                Label("RESTRAIN NOBODY", size = 6.0, color = Amber.Dim)
            }
        }

        Label(
            "4 OF 6 VOTED . NOT VOTING COUNTS AS A SKIP",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Faint, tracking = 0.1, align = TextAlign.Center,
        )
        PanelButton(
            "LOCK IN",
            border = Amber.Dim, ink = Amber.Bright,
            tracking = 0.18, onClick = { go(ScreenId.Tally) },
        )
    }
}

@Composable
private fun VoteRow(name: String, held: Boolean = false, note: String? = null) {
    Row(
        Modifier.fillMaxWidth()
            .border(1.u, if (held) Amber.Bright else Amber.Edge)
            .background(if (held) Amber.Edge else Color.Transparent)
            .padding(7.u),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(name, size = 8.0, color = if (held) Amber.Bright else Amber.Mid)
        if (note != null) Label(note, size = 6.0, color = Amber.Dim)
    }
}

/**
 * The result. **Attribution shown, alignment never.**
 *
 * Who voted for whom is public, because the app supplies constraints and the players supply the
 * accusations. What the restrained player *was* is not shown, and never will be — a correct
 * Restrain and a catastrophic one look exactly alike.
 */
@Composable
fun TallyScreen() {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Label("RESTRAIN . RESULT", size = 7.0, color = Amber.Dim, tracking = 0.16)

        TallyRow("MARCUS", 3, border = Amber.Bright, nameInk = Amber.Bright)
        TallyRow("SKIP", 2)
        TallyRow("PRIYA", 1)

        Box(Modifier.weight(1f))

        Box(
            Modifier.fillMaxWidth().background(Amber.Bright).padding(8.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                "MARCUS WAS RESTRAINED",
                size = 8.0, color = Amber.Black, tracking = 0.14, align = TextAlign.Center,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
            Row(Modifier.fillMaxWidth().height(6.u), horizontalArrangement = Arrangement.spacedBy(1.u)) {
                Box(Modifier.weight(9f).fillMaxHeight().background(Amber.Dim))
                Box(Modifier.weight(6f).fillMaxHeight().background(Amber.Edge))
            }
            Label(
                "LIGHTS OUT IN 9",
                modifier = Modifier.fillMaxWidth(),
                size = 6.5, color = Amber.Dim, tracking = 0.14, align = TextAlign.Center,
            )
        }
    }
}

/**
 * A count and nothing else.
 *
 * **The living never learn who cast what.** Attribution used to publish here; it does not, and
 * that is what makes the outside-the-system view an actual privilege rather than a preview. It
 * also means your own vote stays yours, which is a different game from one where it does not.
 */
@Composable
private fun TallyRow(
    name: String,
    count: Int,
    border: Color = Amber.Edge,
    nameInk: Color = Amber.Mid,
) {
    Row(
        Modifier.fillMaxWidth().border(1.u, border).padding(7.u),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Label(name, size = 9.0, color = nameInk)
        Readout("$count", size = 17.0, color = nameInk, lineHeight = 1.0)
    }
}
