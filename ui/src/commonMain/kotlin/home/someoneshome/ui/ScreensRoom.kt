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
 * a tally and nothing else. The app supplies constraints; the players supply the accusations.
 *
 * ### Every control on these screens echoes one phone, and none of them moves the meeting
 *
 * I AM HERE, READY TO VOTE and READY look like three different buttons and are the same button:
 * *one player says they are done*. What happens next depends on **all** the phones — the check-in
 * gate closes when every living player and every out player is standing there (D-104), the talk
 * skips ahead only on a unanimous READY, the ballot is read when the window closes — and no phone
 * can count phones.
 *
 * So each of them lights its own control and the screen goes on waiting, which is exactly what the
 * player is doing. **The house moves everybody at once, and it now really does**: the meeting's
 * transitions are pushes the authority makes, listed in [Flow.housePushes] against the effect that
 * carries each one. **The counts beside these buttons do not move when you press them**: they are
 * the house's numbers and they change when the house says so.
 */

/**
 * **The vote button's two faces.** Named, so that settling the copy is one edit and not six.
 *
 * D-117 named READY as the rename candidate for LOCK IN, *for symmetry with the discussion's READY
 * TO VOTE*, and left the final wording as a build-time call. This is that call being taken, and it
 * is the kind that somebody should agree with rather than inherit.
 */
private const val VOTE_READY = "READY"

/** The irrevocable half. Not *YOU ARE READY*, which is the discussion's button on another screen. */
private const val VOTE_CAST = "VOTE CAST"

/** You called it. Every phone in the building rings, and you wait with everyone else. */
@Composable
fun CallingScreen() {
    val counts = LocalMeeting.current.counts
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
        // The dots and the line below them are one count drawn twice, so they are read from one
        // number. Written separately they are a picture that can disagree with the caption under it.
        Row(
            Modifier.padding(top = 10.u),
            horizontalArrangement = Arrangement.spacedBy(4.u),
        ) {
            repeat(counts.answered) { Box(Modifier.size(8.u).background(Amber.Bright)) }
            repeat(counts.seats - counts.answered) { Box(Modifier.size(8.u).border(1.u, Amber.Faint)) }
        }
        Label("${counts.ofSeats(counts.answered)} ANSWERED", size = 6.0, color = Amber.Dim, tracking = 0.12)
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
            Modifier.padding(top = 16.u).border(1.u, Amber.Bright).tapTarget(onAnswer)
                .padding(horizontal = 26.u, vertical = 11.u),
            contentAlignment = Alignment.Center,
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
        CheckIn()
    }
}

/**
 * **I AM HERE, and the gate it does not close.**
 *
 * D-104: the talk does not start until every living player *and* every out player has checked in
 * at the meeting area. Closing that gate means counting phones, so it is the house's — this
 * control lights your own tick and the screen goes on waiting.
 *
 * **The design's button navigated, and that was the port's, not the design's.** Its handler is a
 * dangling reference (`goNotices`, which its `renderVals` never defines) so it did nothing in the
 * prototype; the port wired it to the notices, which made one phone's press start the meeting for
 * everybody. The design's own device shell auto-advances this step, which is the house doing it.
 *
 * Drawn on both halves of the same gate — the living's [AssembleScreen] and the out player's
 * [Ghost2Screen] — from one composable, because D-104 makes them one gate and two copies of it
 * would eventually disagree about what a check-in looks like.
 */
@Composable
fun CheckIn() {
    val actions = LocalActions.current
    val meeting = LocalMeeting.current
    val here = meeting.checkedIn

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.u),
    ) {
        // Spent rather than gone. A control that vanished when pressed would take the one thing on
        // the screen the player just aimed at away from under their thumb, in the dark.
        val block = Modifier.padding(top = 10.u).border(1.u, if (here) Amber.Faint else Amber.Bright)
        Box(
            (if (here) block else block.tapTarget(actions.checkIn))
                .padding(horizontal = 22.u, vertical = 11.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                if (here) "YOU ARE HERE" else "I AM HERE",
                size = 8.5, color = if (here) Amber.Dim else Amber.Bright, tracking = 0.2,
            )
        }
        // The house's count, and it does NOT include your press. It moves when the house says so.
        val counts = meeting.counts
        Label(
            "${counts.ofSeats(counts.present)} CHECKED IN",
            size = 6.0, color = Amber.Faint, tracking = 0.12,
        )
    }
}

/**
 * The house's notices — **once, at the top of the meeting, then gone.**
 *
 * This is rule 6 in its constructive form: a dead radio made a living player invisible, and the
 * house announces the gap rather than letting it look like evidence. It reports an *interval of
 * missing data*, never a person's behaviour.
 *
 * **The words are [Notifications.notice]'s, and this screen is the only place they are drawn.**
 * That is D-105's *stored nowhere* made structural rather than promised: a notice belongs to the
 * meeting it arrives at, so there is no thread holding it, no list it lands in, and nothing on any
 * other screen that could still be showing it an hour later. `NotificationsTest` renders every
 * screen in the game in both roles and fails the moment a second one has these words on it.
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
                Notifications.notice.body,
                size = Notifications.notice.bodySize, color = Amber.Bright, lineHeight = 1.7,
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
 *
 * The clock is a value, not a timer — see [PanelVals.countdown]. The row of segments under it is
 * **not** the clock: it is the readiness count the line below spells out, which is why it is drawn
 * from the same number rather than from the time.
 */
@Composable
fun DiscussionScreen(vals: PanelVals) {
    val meeting = LocalMeeting.current
    val counts = meeting.counts
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
            Readout(vals.countdown.text, size = 62.0, color = Amber.Bright, lineHeight = 0.9)
            Label("REMAINING", size = 6.5, color = Amber.Dim, tracking = 0.2)
        }
        Row(Modifier.fillMaxWidth().height(5.u), horizontalArrangement = Arrangement.spacedBy(3.u)) {
            repeat(counts.ready) { Box(Modifier.weight(1f).fillMaxHeight().background(Amber.Bright)) }
            repeat(counts.seats - counts.ready) {
                Box(Modifier.weight(1f).fillMaxHeight().background(Amber.Edge))
            }
        }
        Label(
            "${counts.ofSeats(counts.ready)} READY . UNANIMOUS SKIPS AHEAD",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Faint, tracking = 0.12, align = TextAlign.Center,
        )
        // Unanimous is a count of six phones. This one says only that this phone is done.
        PanelButton(
            if (meeting.ready) "YOU ARE READY" else "READY TO VOTE",
            border = if (meeting.ready) Amber.Faint else Amber.Dim,
            ink = if (meeting.ready) Amber.Dim else Amber.Bright,
            tracking = 0.18, verticalPadding = 11.u,
            onClick = if (meeting.ready) null else LocalActions.current.sayReady,
        )
    }
}

/**
 * The vote. **You see how many have voted, never what.**
 *
 * **Not voting counts as a Skip** rather than as an abstention (D-075) — which is what the line at
 * the foot of the screen has always said. Combined with ties resolving to Skip, the whole weight of
 * inaction sits behind restraining nobody. *(The KDoc here used to claim the opposite, carried over
 * from the GDD paragraph D-075 reversed; the screen's own copy was right and the comment above it
 * was not.)*
 *
 * ### The rows echo your finger. Nothing on this screen knows the result
 *
 * Tapping a name lights that row and nothing else happens — no count moves, no other phone is
 * consulted, and the row is not a claim that the vote landed. **READY converts the selection into
 * the vote, and after it nothing can be changed** (D-117, superseding *changeable until the clock
 * ends* at `gdd.md:412` and `:1006`): the rows stop echoing, and the house refuses a later tap and
 * re-asserts what it holds. The result arrives when the house reads the ballot and pushes the
 * screen; a READY that walked to it would be this phone announcing a tally it cannot see.
 *
 * The count is of **locked** seats, never of selections — the living see how many have voted, and
 * only a player outside the system sees what anybody chose.
 */
@Composable
fun VoteScreen(vals: PanelVals) {
    val actions = LocalActions.current
    val meeting = LocalMeeting.current
    val counts = meeting.counts

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
            Readout(vals.countdown.text, size = 13.0, color = Amber.Bright)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
            for (name in meeting.names) {
                val held = meeting.holds(name)
                VoteRow(
                    label = name,
                    note = if (held) "YOUR VOTE" else null,
                    held = held,
                    onPick = { actions.chooseVote(VoteChoice.Named(name)) },
                )
            }
            // Restraining nobody is a vote, so it is a row like the others — dashed, because it is
            // the one row that is not a person.
            VoteRow(
                label = "SKIP",
                note = "RESTRAIN NOBODY",
                held = meeting.skipping,
                dashed = true,
                onPick = { actions.chooseVote(VoteChoice.Skip) },
            )
        }

        // The house's count. It does not move when you lock yours in.
        Label(
            "${counts.ofSeats(counts.voted)} VOTED . NOT VOTING COUNTS AS A SKIP",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Faint, tracking = 0.1, align = TextAlign.Center,
        )
        // Three states, all of them facts about this phone: nothing chosen yet, something chosen
        // and not yet handed over, and a ballot that has been cast and cannot be taken back.
        // Present and inert in the first and last rather than absent, so the row under the
        // player's thumb never moves.
        PanelButton(
            if (meeting.locked) VOTE_CAST else VOTE_READY,
            border = if (meeting.choice == null || meeting.locked) Amber.Faint else Amber.Dim,
            ink = if (meeting.choice == null || meeting.locked) Amber.Dim else Amber.Bright,
            tracking = 0.18,
            onClick = if (meeting.choice == null || meeting.locked) null else actions.readyToVote,
        )
    }
}

/**
 * One row of the ballot: a name, or SKIP.
 *
 * **A tap target first and a list row second.** At the design's own padding these were about 22
 * units tall — roughly 27 points on a phone — and this is the screen where a finger landing one
 * row off restrains the wrong resident, in the dark, in silence, with no way to say so.
 * [TAP_TARGET] is the floor; the ink and the border are the design's.
 */
@Composable
private fun VoteRow(
    label: String,
    note: String?,
    held: Boolean,
    onPick: () -> Unit,
    dashed: Boolean = false,
) {
    val edged =
        if (dashed) Modifier.dashedBorder(if (held) Amber.Bright else Amber.Faint)
        else Modifier.border(1.u, if (held) Amber.Bright else Amber.Edge)
    Row(
        Modifier.fillMaxWidth()
            .then(edged)
            .background(if (held) Amber.Edge else Color.Transparent)
            .tapTarget(onPick)
            .padding(horizontal = 7.u, vertical = 6.u),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, size = 8.0, color = if (held) Amber.Bright else Amber.Mid)
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
fun TallyScreen(vals: PanelVals) {
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

        // The bar drains rather than fills, and it is the same number as the line under it — two
        // weights of 9 and 6 out of a fifteen-second window, now one value drawn twice. A weight
        // of zero still draws its own hairline, so each half appears only while it has width.
        Column(verticalArrangement = Arrangement.spacedBy(5.u)) {
            val left = vals.countdown.remaining
            Row(Modifier.fillMaxWidth().height(6.u), horizontalArrangement = Arrangement.spacedBy(1.u)) {
                if (left > 0f) Box(Modifier.weight(left).fillMaxHeight().background(Amber.Dim))
                if (left < 1f) Box(Modifier.weight(1f - left).fillMaxHeight().background(Amber.Edge))
            }
            Label(
                "LIGHTS OUT IN ${vals.countdown.text}",
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
