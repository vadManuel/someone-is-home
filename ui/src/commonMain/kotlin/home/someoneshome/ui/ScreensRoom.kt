package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

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
 * **The vote control's two faces.** Named, so that settling the copy is one edit and not six.
 *
 * D-117 named READY as the rename candidate for LOCK IN and left the wording as a build-time call;
 * **D-141 then retired the tap altogether** — *the pre-lock control **is** the hold, and there is no
 * READY tap on the ballot any more*. READY TO VOTE survives on the discussion screen, where it is a
 * readiness signal rather than a commitment.
 *
 * So the pre-lock face says what the finger has to do, in the same words the Delete screen taught
 * with the lights on.
 */
private const val VOTE_HOLD = "HOLD TO CAST"

/** The irrevocable half. Not *YOU ARE READY*, which is the discussion's button on another screen. */
private const val VOTE_CAST = "VOTE CAST"

/**
 * **The meeting's band: the one solid block on the notice, the discussion and the vote** (D-202).
 * Every lamp in the house has just gone to full white, so a filled block costs nothing here; it is
 * the screen's name, and the eye finds it before anything else.
 */
@Composable
private fun MeetingBand(title: String) {
    Box(Modifier.fillMaxWidth().background(Ramp.Ink).padding(14.dp)) {
        Words(title, size = 26, color = Amber.Black, lineHeight = 1.0)
    }
}

/** You called it. Every phone in the building rings, and you wait with everyone else. */
@Composable
fun CallingScreen() {
    val counts = LocalMeeting.current.counts
    KitScreen {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Steady (D-200, D-093). It pulsed: a local animation the core never emitted, on a
            // phone the whole house is ringing at once.
            Tag("CALLING", Ramp.Mid, tracking = 0.2)
            Words("HOUSE\nMEETING", size = 72, lineHeight = 0.85, align = TextAlign.Center)
            // The squares and the line below them are one count drawn twice, so they are read from
            // one number. Written separately they are a picture that can disagree with its caption.
            //
            // **Nothing at all where the house has said nothing** (D-247). No house sends a count of
            // answered rings, so on a housed phone this is the headline alone.
            val answered = counts.answered
            val seats = counts.seats
            if (answered != null && seats != null) {
                Row(
                    Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(answered) { Box(Modifier.size(18.dp).background(Ramp.Ink)) }
                    repeat(seats - answered) { Box(Modifier.size(18.dp).border(1.dp, Ramp.Line)) }
                }
                Words("${counts.ofSeats(answered)} ANSWERED", size = 24, lineHeight = 1.0)
            }
        }
    }
}

/**
 * An incoming call **with the caller named**.
 *
 * Naming the caller is deliberate: the meeting has an author, and the room can ask them why. It
 * is one of the few facts the app volunteers, and it is one everybody gets equally.
 */
@Composable
fun CallScreen(vals: PanelVals) {
    val go = navigator()
    IncomingCall(caller = vals.callerOr("PRIYA"), reason = "HOUSE MEETING") { go(ScreenId.Assemble) }
}

/**
 * The same call, different header.
 *
 * **Same screen, deliberately.** Finding a revoked player and calling a meeting produce identical
 * device behaviour, so nobody can tell from a neighbour's phone which one happened.
 */
@Composable
fun FoundScreen(vals: PanelVals) {
    val go = navigator()
    IncomingCall(caller = vals.callerOr("ELLIOT"), reason = "REVOKED\nRESIDENT FOUND") { go(ScreenId.Assemble) }
}

/**
 * **The two incoming calls share one layout** (D-202): the caller large, the reason under it, and
 * ANSWER as the one solid block at the foot. Only the words change.
 */
@Composable
private fun IncomingCall(caller: String, reason: String, onAnswer: () -> Unit) {
    KitScreen {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Steady (D-200, D-093). It pulsed: a local animation the core never emitted, on a
            // phone the whole house is ringing at once.
            Tag("INCOMING", Ramp.Mid, tracking = 0.2)
            Words(caller, size = 88, lineHeight = 0.85, align = TextAlign.Center)
            Words(reason, size = 30, color = Ramp.Mid, lineHeight = 1.0, align = TextAlign.Center)
        }
        KitPrimary("ANSWER", Palette.DARK, size = 36, height = 72.dp, onClick = onAnswer)
    }
}

/**
 * Answered: lamps up, nobody speaks yet.
 *
 * The instruction not to speak yet is a *social* rule the app states and never enforces — physical
 * conduct is contract, never software.
 */
@Composable
fun AssembleScreen() = WalkIn("WALK TO THE\nMEETING AREA", "DO NOT SPEAK YET.")

/**
 * **Walking in, on both halves of the check-in gate** (D-104): the living's [AssembleScreen] and
 * the out player's [Ghost2Screen] are one layout, a headline, a rule, one line of instruction and
 * [CheckIn] at the foot. Only the words change, so across a room the two cannot be told apart.
 */
@Composable
internal fun WalkIn(headline: String, instruction: String) {
    KitScreen {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = 60.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Words(headline, size = 60, lineHeight = 0.85, align = TextAlign.Center)
            Box(Modifier.size(width = 48.dp, height = 2.dp).background(Ramp.Line))
            KitCaption(instruction, Palette.DARK, size = 26, center = true)
        }
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
    val counts = meeting.counts

    // Spent rather than gone. A control that vanished when pressed would take the one thing on the
    // screen the player just aimed at away from under their thumb, in the dark. Pressed, it drops
    // from the solid block to an outline and loses its press.
    Box(
        Modifier.fillMaxWidth().height(64.dp)
            .then(if (here) Modifier else Modifier.background(Ramp.Ink))
            .border(2.dp, if (here) Ramp.Line else Ramp.Ink)
            .then(if (here) Modifier else Modifier.tap(actions.checkIn)),
        contentAlignment = Alignment.Center,
    ) {
        Words(
            if (here) "YOU ARE HERE" else "I AM HERE",
            size = 32, color = if (here) Ramp.Mid else Amber.Black, tracking = 0.1, lineHeight = 1.0,
        )
    }
    // The house's count, and it does NOT include your press. It moves when the house says so.
    // Nothing until the house has counted this meeting's gate (D-247).
    counts.ofSeats(counts.present)?.let {
        KitCaption("$it CHECKED IN", Palette.DARK, Modifier.fillMaxWidth(), center = true)
    }
}

/**
 * The house's notices — **once, at the top of the meeting, then gone.**
 *
 * This is rule 6 in its constructive form: a failed radio made a living player invisible, and the
 * house announces the gap rather than letting it look like evidence. It reports an *interval of
 * missing data*, never a person's behaviour.
 *
 * **The words are [Notifications.notice]'s, and this screen is the only place they are drawn.**
 * That is D-105's *stored nowhere* made structural rather than promised: a notice belongs to the
 * meeting it arrives at, so there is no thread holding it, no list it lands in, and nothing on any
 * other screen that could still be showing it an hour later. `NotificationsTest` renders every
 * screen in the game in both roles and fails the moment a second one has these words on it.
 *
 * ### It is swiped away, and there is no DISMISS button anywhere in the game
 *
 * **The swipe is the acknowledgment** (D-119), and it is the whole vocabulary: up on a banner, left
 * under the clock, and up on this — the notice is a banner that arrived on a screen of its own.
 * A DISMISS button here was the last button dismissal left, and it was the wrong shape twice over.
 * D-105 deleted read state, so a control that *acknowledged* would be the one place in the app that
 * claimed to know what a player had looked at; and a second way to put a notification away is a
 * second thing to learn in the dark, on the screen where nobody is looking at their phone anyway.
 *
 * It walks to the discussion because a notice sits between the check-in gate and the talk, and the
 * house's own nine seconds ([Flow.autoAdvance]) walks there too. Swiping is *going first*, never
 * going somewhere else. **The gesture is no longer spelled out under the card** (D-202, screens
 * say little): the same swipe has already put a banner away, and the house clears this one anyway.
 */
@Composable
fun NoticeScreen(vals: PanelVals) {
    val actions = LocalActions.current
    // The banner's distance, measured exactly as the banner measures it, because
    // the hand does not know which screen it is on.
    val threshold = with(LocalDensity.current) { SWIPE_DISMISS.toPx() }
    // Where the finger has taken it, never below zero — the same tracking the banner does.
    var lifted by remember { mutableStateOf(0f) }

    KitScreen(padding = MEETING_PADDING, gap = 16.dp) {
        MeetingBand("HOUSE MEETING . NOTICES")
        // **Nobody evaded, so nothing is drawn: no line and no box** (D-223). The band says where
        // the room is; a sentence here would be the house speaking when it has nothing to say.
        val body = vals.notice
        if (body != null) Column(
            // Read at draw time rather than at composition: a drag is sixty frames a second and
            // the whole app has an allocation budget of about 0.5 MB/s.
            Modifier.offset { IntOffset(0, lifted.toInt()) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (-lifted >= threshold) actions.dismissNotice() else lifted = 0f },
                        onDragCancel = { lifted = 0f },
                    ) { _, delta -> lifted = (lifted + delta).coerceAtMost(0f) }
                }
                .fillMaxWidth().border(1.dp, Ramp.Line)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Words(body, size = 28, lineHeight = 1.08)
        }
    }
}

/** The screens that open on [MeetingBand] sit a little lower than `kit.py`'s default. */
private val MEETING_PADDING = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)

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
    KitScreen(padding = MEETING_PADDING, gap = 16.dp) {
        MeetingBand("HOUSE MEETING . DISCUSSION")
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Words(vals.countdown.text, size = 150, lineHeight = 0.75)
            Tag("REMAINING", Ramp.Mid, tracking = 0.2)
        }
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Against the living, like the caption under it: a ghost has no hand to put up (D-147).
            // Nothing until the house has counted (D-247).
            val ready = counts.ready
            val living = counts.living
            if (ready != null && living != null) {
                repeat(ready) { Box(Modifier.weight(1f).fillMaxHeight().background(Ramp.Ink)) }
                repeat(living - ready) {
                    Box(Modifier.weight(1f).fillMaxHeight().border(1.dp, Ramp.Line))
                }
            }
        }
        counts.ofLiving(counts.ready)?.let {
            KitCaption("$it READY", Palette.DARK, Modifier.fillMaxWidth(), size = 24, center = true)
        }
        // Unanimous is a count of six phones. This one says only that this phone is done.
        val ready = meeting.ready
        Box(
            Modifier.fillMaxWidth().height(64.dp).border(2.dp, if (ready) Ramp.Line else Ramp.Ink)
                .then(if (ready) Modifier else Modifier.tap(LocalActions.current.sayReady)),
            contentAlignment = Alignment.Center,
        ) {
            Words(
                if (ready) "YOU ARE READY" else "READY TO VOTE",
                size = 32, color = if (ready) Ramp.Mid else Ramp.Ink, tracking = 0.08, lineHeight = 1.0,
            )
        }
    }
}

/**
 * The vote. **You see how many have voted, never what.**
 *
 * **Not voting counts as a Skip** rather than as an abstention (D-075). Combined with ties
 * resolving to Skip, the whole weight of inaction sits behind restraining nobody. *(The KDoc here
 * used to claim the opposite, carried over from the GDD paragraph D-075 reversed.)* The line at the
 * foot used to say so too; the mockup cut it to the count, and the rule is the briefing's to teach.
 *
 * ### The rows echo your finger. Nothing on this screen knows the result
 *
 * Tapping a name lights that row and nothing else happens — no count moves, no other phone is
 * consulted, and the row is not a claim that the vote landed. **Two seconds of a finger converts
 * the selection into the vote, and after it nothing can be changed** (D-117 for the irrevocability,
 * D-141 for the gesture, together superseding *changeable until the clock ends* at `gdd.md:412` and
 * `:1006`): the rows stop echoing, and the house refuses a later tap and re-asserts what it holds.
 * The result arrives when the house reads the ballot and pushes the screen; a control that walked
 * to it would be this phone announcing a tally it cannot see.
 *
 * **The hold is a lock and not a submit**, which is what keeps the buzzer's auto-lock meaningful: a
 * player who never completes one has whatever is selected locked for them when the window closes,
 * and a player who selected nothing at all is a Skip by silence (D-075).
 *
 * The count is of **locked** seats, never of selections — the living see how many have voted, and
 * only a player outside the system sees what anybody chose.
 */
@Composable
fun VoteScreen(vals: PanelVals) {
    val actions = LocalActions.current
    val meeting = LocalMeeting.current
    val counts = meeting.counts

    KitScreen(padding = MEETING_PADDING, gap = 10.dp) {
        Row(
            Modifier.fillMaxWidth().background(Ramp.Ink).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Words("RESTRAIN . VOTE", Modifier.weight(1f), size = 30, color = Amber.Black, lineHeight = 1.0)
            Words(vals.countdown.text, size = 56, color = Amber.Black, lineHeight = 0.75)
        }

        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // **A null is nobody, and nobody is not a row** (D-274). The ballot is indexed by
            // seat, so a seat not in the round is a null slot; a row for it would be a vote cast
            // at a seat that is not in the house. Skipped rather than filtered out of the list:
            // the list's index is the seat, and `asMessage` turns a name back into it. **A blank
            // is somebody** (D-277) — in the round, with no name — and is a row like any other,
            // drawn as UNNAMED and sent as the blank it is, which `asMessage` finds at its seat.
            for ((seat, name) in meeting.names.withIndex()) {
                if (name == null) continue
                VoteRow(
                    label = name.ifBlank { UNNAMED },
                    held = meeting.holds(name, seat),
                    onPick = { actions.chooseVote(VoteChoice.Named(name, seat)) },
                )
            }
            // Restraining nobody is a vote, so it is a row like the others — dashed, because it is
            // the one row that is not a person.
            VoteRow(
                label = "SKIP",
                held = meeting.skipping,
                dashed = true,
                onPick = { actions.chooseVote(VoteChoice.Skip) },
            )
        }

        KitSpacer()
        // The house's count. It does not move when you lock yours in.
        counts.ofLiving(counts.voted)?.let {
            KitCaption("$it VOTED", Palette.DARK, Modifier.fillMaxWidth(), size = 24, center = true)
        }
        // Three states, all of them facts about this phone: nothing chosen yet, something chosen
        // and not yet handed over, and a ballot that has been cast and cannot be taken back.
        // **One control through all three**, present and inert in the first and last rather than
        // absent, so the block under the player's thumb never moves and never changes height. The
        // ink says which: full while it will take a hold, dim with nothing chosen, mid once cast.
        val live = meeting.choice != null && !meeting.locked
        KitHold(
            if (meeting.locked) VOTE_CAST else VOTE_HOLD,
            Palette.DARK.copy(
                ink = when {
                    meeting.locked -> Ramp.Mid
                    live -> Ramp.Ink
                    else -> Ramp.Dim
                },
            ),
            onConfirm = actions.readyToVote,
            enabled = live,
            size = 32,
            note = false,
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
    held: Boolean,
    onPick: () -> Unit,
    dashed: Boolean = false,
) {
    val edge = if (held) Ramp.Ink else Ramp.Line
    // YOUR VOTE on the row you hold, whichever it is, and SKIP's meaning on SKIP while it is not.
    val note = if (held) "YOUR VOTE" else if (dashed) "RESTRAIN NOBODY" else null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            // The fill first: a dashed edge is drawn behind the content, and a fill drawn after it
            // would cover it.
            .then(if (held) Modifier.background(Ramp.Ink) else Modifier)
            .then(if (dashed) Modifier.dashedBorder(edge, 1.dp) else Modifier.border(1.dp, edge))
            .tap(onPick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Words(label, Modifier.weight(1f), size = 28, color = if (held) Amber.Black else Ramp.Ink, lineHeight = 1.0)
        if (note != null) {
            Words(note, size = 22, color = if (held) Amber.Black else Ramp.Dim, lineHeight = 1.0)
        }
    }
}

/**
 * The result, as a living player reads it. **A count and an outcome, and nothing finer.**
 *
 * *`6 OF 6 VOTED . MARCUS WAS RESTRAINED`* is the whole of it (the owner's 2026-08-24 ruling). The
 * screen used to carry a per-candidate tally — three rows with names and counts against them,
 * drawn from a fixture on every phone in every round, because nothing on a living phone's wire
 * could fill them. That is gone in both halves: no sender, and nothing to send to.
 *
 * ### Why a count and not a tally, when neither one attributes
 *
 * Because a per-candidate tally is *the shape of the room's thinking, published to the people still
 * in it* — who drew suspicion and how much, in a form that outlives the meeting in six people's
 * memories. The count says how much of the room took part, which everybody watched happen. **What
 * the restrained player was is not shown and never will be**: a correct Restrain and a catastrophic
 * one look exactly alike.
 *
 * A player outside the system reads something wider on their own screen — the ballot itself, with
 * names against it (D-075, `GhostMeetingScreen`) — and can act on none of it.
 */
@Composable
fun TallyScreen(vals: PanelVals) {
    KitScreen {
        KitHeader("RESTRAIN . RESULT", Palette.DARK)

        // **The empty middle is the result** (the owner's 2026-08-24 ruling). Three rows of names
        // and counts used to sit here and there is nothing to put in their place: what the living
        // are told is two facts, and a screen that filled the gap would be filling it with
        // something nobody ruled. The room is looking up from their phones by now.
        KitSpacer()

        // **The two facts as one block, and the order is the sentence.** `6 OF 6 VOTED` then
        // `MARCUS WAS RESTRAINED` — how much of the room took part, then what it decided — drawn
        // adjacent because they are read as one statement. The count is outlined and the outcome
        // is the screen's one solid block, so which the eye lands on first is not left to chance.
        Box(
            Modifier.fillMaxWidth().border(1.dp, Ramp.Line).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Words(vals.votedLine, size = 28, lineHeight = 1.0, align = TextAlign.Center)
        }
        Box(
            Modifier.fillMaxWidth().background(Ramp.Ink).padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Words(
                vals.restrainedLine,
                size = 30, color = Amber.Black, tracking = 0.02, lineHeight = 1.0, align = TextAlign.Center,
            )
        }

        // The bar drains rather than fills, and it is the same number as the line under it: one
        // value drawn twice.
        Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, Ramp.Faint)) {
            Box(Modifier.fillMaxWidth(vals.countdown.remaining).fillMaxHeight().background(Ramp.Line))
        }
        KitCaption(
            "LIGHTS OUT IN ${vals.countdown.text}", Palette.DARK, Modifier.fillMaxWidth(),
            size = 24, center = true,
        )
    }
}
