package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Work — what Residents actually do, and what an Insider imitates.
 *
 * Every Subroutine here is a *real* Subroutine for both roles: same screen, same duration, same
 * completion. The only difference is whether the completion writes, and it never shows on screen.
 * That is what makes rule 8's "every subroutine ships with its fake" cheap — there is no separate
 * fake to build or forget.
 */

/**
 * **Every element that says where a piece of assigned work is** — the room name and the marker
 * glyph, on the work-order list and on the springboard widget alike.
 *
 * One tag across both surfaces because the thing being counted is one fact: *does this build tell
 * a player where their work is?* The answer is yes on every **known** line and no on every blocked
 * one, and `WorkOrderTest` holds both directions — a blocked row that grew a destination fails,
 * and a known row that lost one fails too.
 */
const val ORDER_DESTINATION: String = "order-destination"

/** The banner a notification arrives as, for tests that measure where it lies. */
const val NOTIFICATION_BANNER: String = "notification-banner"

/**
 * **A banner, not a takeover — and one of them costs the house its light (D-105, D-118).**
 *
 * Drawn over the springboard rather than instead of it, because you cannot scan a marker through
 * a modal and the player has to be able to see that their phone still works.
 *
 * ### Two treatments, and the difference is emitted light rather than taste
 *
 * A **heavy** banner is a bright amber block on a panel dropped to [NOTIFIED_DIM]: the two
 * notifications D-118 admits to the light vocabulary, arriving on every phone in the house at
 * once. A **quiet** banner is drawn in the panel's own ink on the panel's own black, over a panel
 * that did not change at all — *"no dim, no brightness spike, nothing world-observable at all"*.
 * A quiet notification lit up an amber block would be the broadcast without the meaning: the room
 * would see a light change and learn nothing from it, and the next real one would mean less.
 *
 * So [NotificationKind.heavy] is read here for colour and read again in [Screen] for the dim, and
 * neither of those is a style flag.
 *
 * ### The swipe is a real gesture, not a control shaped like one
 *
 * The banner tracks the finger the whole way up and springs back if it lifts short of
 * [SWIPE_DISMISS]. That is echo of your own input — the one thing `ui` is allowed to draw without
 * asking anybody — and it is what makes the gesture discoverable in a dark room: you learn the
 * banner moves before you learn that it goes.
 *
 * It only ever moves **up**. Down is not a shorter swipe up, it is a different gesture; a banner
 * that could be dragged down would be teaching a vocabulary that does not exist. It cannot be
 * pushed above the status row either — that row is the one thing on screen that stays put while
 * something arrives, and [PanelFrame] clips it there rather than trusting this to stop.
 *
 * **A heavy banner also goes on its own after [HEAVY_HOLD], and a quiet one never does.** That is
 * not here: it is a transition of the whole panel and it lives in [Flow.autoAdvance] with the rest
 * of the house's timing. Here is only what a finger does.
 *
 * ### A tap still opens it, where there is something to open
 *
 * Both gestures live on the same surface and neither eats the other: a drag consumes nothing until
 * it passes touch slop, and a tap that has moved past slop is no longer a tap. **Which of the two
 * modifiers is written first turns out not to matter** — that was checked by swapping them and
 * watching all the gesture tests stay green — so the order here is the readable one and not a
 * load-bearing incantation. What is load-bearing is that a test really drags a finger, because
 * a banner that cannot be dismissed looks exactly like one that can.
 *
 * [Notification.opens] is null on a notification that is not a door, and then there is nothing to
 * reach — the banner still swipes away.
 */
@Composable
fun BoxScope.NotificationBanner(notification: Notification) {
    val actions = LocalActions.current
    val heavy = notification.kind.heavy
    val box = this
    Mockup {
        val threshold = with(LocalDensity.current) { SWIPE_DISMISS.toPx() }
        // Where the finger has taken it, in pixels, never below zero. Keyed on the notification so a
        // second one arriving does not inherit half a swipe made at the first.
        var lifted by remember(notification) { mutableStateOf(0f) }
        with(box) {
            Column(
                Modifier.align(Alignment.TopCenter).testTag(NOTIFICATION_BANNER)
                    // The lambda form: it reads `lifted` at draw time rather than at composition,
                    // so a finger moving up the screen does not recompose the banner every frame.
                    .offset { IntOffset(0, lifted.toInt()) }
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .fillMaxWidth()
                    // THE QUIET BANNER EMITS NOTHING THE PANEL WAS NOT ALREADY EMITTING: black
                    // ground, one line, and the same ink every other panel is written in.
                    .background(if (heavy) Ramp.Ink else Amber.Black)
                    .then(if (heavy) Modifier else Modifier.border(1.dp, Ramp.Line))
                    // The quiet banner covers the whole System Integrity widget rather than slicing
                    // it: it arrives over a page that has not dimmed, so a half-covered meter
                    // would read as a fault rather than as something behind.
                    .then(if (heavy) Modifier else Modifier.heightIn(min = 156.dp))
                    .pointerInput(notification) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (-lifted >= threshold) actions.dismissNotification() else lifted = 0f },
                            onDragCancel = { lifted = 0f },
                        ) { _, delta -> lifted = (lifted + delta).coerceAtMost(0f) }
                    }
                    .then(
                        if (notification.opens != null) {
                            Modifier.tap { actions.nav(notification.opens) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val ink = if (heavy) Amber.Black else Ramp.Ink
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Tag(notification.from, if (heavy) ink else Ramp.Mid)
                    Tag(notification.at, if (heavy) ink else Ramp.Dim)
                }
                // The mockup's three sizes: the Egress alert's headline, the house text, and a
                // player's quiet line.
                val egress = notification.detail != null
                Words(
                    notification.body, color = ink,
                    size = if (!heavy) 26 else if (egress) 32 else 28,
                    lineHeight = if (egress) 0.95 else 1.05,
                )
                notification.detail?.let { Words(it, size = 22, color = ink, lineHeight = 1.0) }
            }
        }
    }
}

/**
 * The work order — **the order the house dealt this seat, drawn** (D-114, D-129).
 *
 * **A menu, not a queue.** Every row the house has named is a row the player may choose, and the
 * sequencing that emerges comes from the dependency graph rather than from the order they are
 * printed in. One row is highlighted because the springboard has to name *something* to walk
 * towards — see [PanelVals.nextUp], where that presentation is flagged against D-114 rather than
 * defended.
 *
 * **A Subroutine opens only at its marker**, which is what turns the list into travel.
 *
 * **The blocked rows name nothing, and nothing here decides that.** [OrderRow.Blocked] has one
 * field on it, so this screen has nothing to draw even if it wanted to: not the Subroutine, not
 * its signature, and not whose completion would free it — which under D-146's cross-player
 * dependencies would name a *person* as surely as a piece of work.
 *
 * **This is where a dark route is planned (D-106).** Every named row carries its light signature
 * in a column of its own, so the whole order can be read for concealment in one glance — which is
 * the entire reason the value is knowledge held in advance rather than discovered at the marker.
 * The key at the foot of the screen is the only place the mark is spelled out; it is here rather
 * than on the springboard because this is the screen a player reads while deciding, and the
 * springboard is the one they glance at while walking.
 *
 * ### The rows carry their destination, and the blocked ones still carry nothing
 *
 * The design draws a room and a marker glyph against every row, and `OrderLine.Known` carries them
 * now — the seat's own lines' rooms and no others, which is the whole of what a client is entitled
 * to know about the home. `OrderLine.Blocked` still carries nothing at all: a room on a blocked row
 * would name the person upstream of you by where they are standing (D-146), so the blackout covers
 * the place as well as the name and it does so by there being no field to draw from.
 */
@Composable
fun WorkScreen(vals: PanelVals) = Mockup {
    val go = navigator()
    val p = Palette.DARK
    KitScreen {
        // Counted off the rows the house sent, both halves. The total is the ORDER's length and
        // never a settings number: an order that shortened while work was blocked would make its
        // own length a tell, so the length drawn here has to be the length received.
        KitHeader(
            "SUBROUTINES", p, back = ScreenId.Home,
            meta = "${vals.nextUp.done} OF ${vals.nextUp.total} DONE",
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // **The name and the light travel together or not at all.** They used to be two
            // literals per row agreeing by hand -- REPLAY beside Bright, SHORT beside Dark --
            // which is the arrangement D-106 already lost once, and the failure mode is silent: a
            // row keeps its name and quietly changes what it promises the lamp will do. Now the
            // pair is resolved once, off the roster, on the way in from the wire.
            vals.order.forEach { row ->
                when (row) {
                    is OrderRow.Named -> {
                        val current = row.index == vals.nextUp.row?.index
                        WorkRow(row, current, onClick = if (current) ({ go(ScreenId.Scan) }) else null)
                    }
                    is OrderRow.Blocked -> WorkLocked()
                }
            }
        }

        // The sentence that sat above the key -- A SUBROUTINE OPENS ONLY AT ITS MARKER. SCAN TO
        // BEGIN. -- went with the mockup's copy cut: only the next row is a target, and it opens
        // the scan, which says the same thing by doing it.
        KitSpacer()
        LightKey()
    }
}

/**
 * The key to the light column — **the one place in the game the mark is spelled out.**
 *
 * Built by walking [LightSignature] rather than by listing three rows, so a rung added to the
 * ladder appears here without anybody remembering to add it, and the word beside each mark is the
 * rung's own name rather than a second copy of it that can disagree.
 *
 * The marks are drawn at the same size as the ones in the rows above. A key whose sample is
 * larger than the thing it explains teaches a glyph the player will never see again.
 */
@Composable
private fun LightKey() {
    // **The word comes before its mark, and that is not a preference.** A mark keeps a constant
    // width while only its lit cells are filled, so with the mark first the space after it varies
    // with the value and the eye binds each mark to the word on its RIGHT. Word first puts the
    // constant gap where the binding is.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag("LIGHT", Ramp.Dim, Modifier.weight(1f))
        LightSignature.entries.forEach { signature ->
            Words(signature.name.uppercase(), size = 18, color = Ramp.Dim, lineHeight = 1.0)
            WorkMark(signature, Ramp.Dim, sample = true)
        }
    }
}

/** The mockup's light mark: three 7 by 14 cells, the unlit ones outlined (`kit.py`'s `lightmark`). */
@Composable
private fun WorkMark(signature: LightSignature, ink: Color, sample: Boolean = false) =
    LightMark(signature, ink, sample = sample, cell = 7.dp, height = 14.dp, gap = 2.dp, outline = true)

/**
 * **One named row of the order — and the only kind of row that names anything.**
 *
 * It takes an [OrderRow.Named] whole rather than a name and a signature separately, which is what
 * makes the pair impossible to mismatch here: the row arrived with its light already resolved off
 * the roster, so a screen cannot promise BRIGHT beside a Subroutine the roster rates DARK.
 *
 * There is deliberately **no overload taking a [Blocked][OrderRow.Blocked] row**. Blocked work is
 * drawn by [WorkLocked], which takes nothing at all.
 *
 * **The current row is the screen's one solid block** (D-202): black on amber, and the only row
 * that is a target. Done rows are struck through and dimmed, with their box filled; open rows are
 * an outline in full ink.
 */
@Composable
private fun WorkRow(
    row: OrderRow.Named,
    current: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val done = row.done
    val ink = when {
        current -> Amber.Black
        done -> Ramp.Dim
        else -> Ramp.Ink
    }
    // The destination and the mark, a step below the name on an open row.
    val meta = when {
        current -> Amber.Black
        done -> Ramp.Dim
        else -> Ramp.Mid
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(
                if (current) Modifier.background(Ramp.Ink)
                else Modifier.border(1.dp, if (done) Ramp.Faint else Ramp.Line)
            )
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Done is a filled box, current an outline in black on the block, open an outline in ink.
        Box(
            Modifier.size(22.dp).then(
                when {
                    current -> Modifier.border(2.dp, Amber.Black)
                    done -> Modifier.background(Ramp.Dim)
                    else -> Modifier.border(1.dp, Ramp.Ink)
                }
            )
        )
        Words(
            row.label, Modifier.weight(1f), size = 28, color = ink, lineHeight = 1.0,
            // Struck through as well as dimmed: dim alone is how a locked row reads too.
            decoration = if (done) TextDecoration.LineThrough else null,
        )
        // **The destination pair, drawn only where there is one.** A known line carries its room
        // and its glyph off the wire; a line the house dealt in a round armed with no home carries
        // neither, and the pair is simply absent rather than filled with a room this phone made
        // up. See OrderRow.Named.room.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.room?.let {
                Words(it, Modifier.testTag(ORDER_DESTINATION), size = 22, color = meta, lineHeight = 1.0)
            }
            // The mockup's 16 box, at the 10/16 its shapes fill.
            row.marker?.let { MarkerGlyph(it, 10.dp, meta, Modifier.testTag(ORDER_DESTINATION)) }
            // Last, because it is read DOWN the list rather than across a row: choosing a dark
            // route means comparing this row's light with the next one's.
            WorkMark(row.light, meta)
        }
    }
}

/**
 * A blocked chain step. Names nothing — not the Subroutine, not who would free it.
 *
 * **And therefore no light mark either.** The signature is a property of a particular Subroutine,
 * so drawing one here would describe the very thing this row exists not to describe. Nothing on
 * the ladder is zero cells, so the empty column reads as *nothing is claimed* rather than as a
 * dark Subroutine.
 *
 * Dashed and hatched, the mockup's "not yet" (D-202), so it reads as a different kind of row
 * before a word on it is.
 */
@Composable
private fun WorkLocked() {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).hatch(Ramp.Fill).dashedBorder(Ramp.Line, 1.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).dashedBorder(Ramp.Faint, 1.dp))
        Words("LOCKED", Modifier.weight(1f), size = 28, color = Ramp.Dim, lineHeight = 1.0)
        Words("WAITING UPSTREAM", size = 22, color = Ramp.Dim, lineHeight = 1.0)
    }
}

/**
 * Scanning — **the screen is the lamp.**
 *
 * The front camera reads the card, so the lit screen has to face the marker, which means the
 * player cannot see it while it works. Everything about the screen follows from that: the
 * instruction is legible *before* you turn the phone over, confirmation is a haptic rather than a
 * visual, and it gives up on its own so nobody is left standing in a dark room holding a lit
 * screen at a wall.
 *
 * ### The gesture is a tap and a turn, and the door opens by itself (D-159)
 *
 * Press-and-hold-then-release is superseded. The player taps SCAN — the last thing they see
 * themselves do — turns the phone so the lamp faces the code, and the Subroutine opens **on
 * acquire, by itself**. That is not a convenience: the surface that would say *got it* is the
 * surface doing the reading, so auto-open is the only way a scan can end in the player's favour.
 *
 * The window is what happens when it does not. Ten seconds, and then **two vibrates** — D-160's
 * word for *no* — because a scan that runs out with the phone pointed at a wall has to say so
 * through the one channel that reaches a player whose eyes are elsewhere. Nothing is spent by it.
 *
 * A lit field, and one of only three in the game. Here that is the function, not the style.
 */
@Composable
fun ScanScreen(vals: PanelVals) = Mockup {
    val go = navigator()
    val ink = Amber.Black
    Column(
        // The ground runs to the top edge; the content starts under the safe area.
        Modifier.fillMaxSize().background(Amber.Bright)
            .padding(start = 20.dp, end = 20.dp, top = LocalPanelInsets.current.top, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InvertedStatusRow("SCANNING")

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                // **Steady, and it used to pulse** (D-200, D-093). `ringPulse()` animated this ring
                // on the full amber field: a local animation the core never emitted, on the one
                // screen that is already a lamp.
                Box(Modifier.fillMaxSize().border(3.dp, ink, CircleShape))
                Box(Modifier.size(52.dp).border(2.dp, Ramp.Faint, CircleShape))
                Box(Modifier.size(12.dp).background(ink, CircleShape))
            }
            Words(
                "TURN THE SCREEN\nTO THE MARKER",
                size = 44, color = ink, lineHeight = 0.9, align = TextAlign.Center,
            )
        }

        // The window, and only the window (D-159): twenty segments over ten seconds, two a second.
        // [PanelVals.window] is this phone's own clock over the same ten seconds the fall-through
        // counts, so the segments really do go and reach nothing in the frame the light goes dark. The
        // seconds figure beside it went with the mockup's copy cut: the bar says the same thing.
        KitMeter(
            total = PanelVals.SCAN_SEGMENTS,
            lit = vals.countdown.litOf(PanelVals.SCAN_SEGMENTS),
            ink = ink, edge = Ramp.Line,
            modifier = Modifier.testTag(SCAN_BAR),
        )

        Box(
            Modifier.fillMaxWidth().height(64.dp).border(2.dp, ink).tap { go(ScreenId.Home) },
            contentAlignment = Alignment.Center,
        ) {
            Words("STOP NOW", size = 30, color = ink, lineHeight = 1.0, tracking = 0.08)
        }
    }
}

/**
 * The scan caught. **The light goes dark the instant it does.**
 *
 * The shape is drawn large and outlined so the player can check it against the card still in
 * their hand — the app is not asserting which marker this is, it is offering a comparison. NOT
 * THIS ONE exists because a scan can catch the wrong card in the dark.
 */
@Composable
fun ScanCaughtScreen(vals: PanelVals) = Mockup {
    val go = navigator()
    val actions = LocalActions.current
    val p = Palette.DARK
    // ⚠️ **Null on a housed phone the house has not answered**, and every read of it below is
    // guarded rather than defaulted. It used to resolve to the port's fixture — HANDSHAKE, room
    // GARAGE — so this screen showed the owner a Subroutine and a room in a house that had dealt
    // them neither, and did it with exactly the confidence it shows a real one. What an unanswered
    // scan draws now is the shape of nothing: no glyph, no room, no briefing, and no BEGIN.
    val current = vals.current
    KitScreen {
        KitHeader("CAUGHT", p)

        KitMiddle {
            // The marker you just scanned -- the same shape the work order sent you to. Filled, as
            // every marker in the app is (see [MarkerGlyph]): the mockup's 88 box, holding the
            // shape at the 10/16 of it the mockup's shapes fill.
            current?.marker?.let {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    MarkerGlyph(it, 55.dp, Ramp.Ink)
                }
            }
            // **The flavour line that used to sit here is gone** (the owner's 2026-08-24 ruling,
            // D-196). What a player has to do is the briefing at the foot of this screen (D-188).
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The room is dropped rather than blanked where the house named none — an empty
                // destination is lawful (`OrderLine.Known.at`) and a leading separator over an
                // absent room reads as a rendering fault rather than as a room nobody sent.
                Words(
                    listOfNotNull(
                        current?.room,
                        current?.let { "SUBROUTINE ${it.index} OF ${it.total}" }
                            ?: PanelVals.NOTHING_OPEN,
                    ).joinToString(" . "),
                    size = 26, color = Ramp.Mid, lineHeight = 1.0, align = TextAlign.Center,
                )
                // The fourth surface, and the one D-106 does not name. It is here because BEGIN
                // and NOT THIS ONE are both live below: this is the last moment the choice is
                // still a choice. Flagged for review. Absent where there is no Subroutine.
                current?.let { WorkMark(it.light, Ramp.Mid) }
            }
            // The line under it -- THE CARD IN YOUR HAND MATCHES, or WAITING FOR THE HOUSE -- went
            // with the mockup's copy cut: the glyph is the comparison, and saying so is a second
            // lit line at a card.
        }

        // **The last lit surface before the work, and ALL TEN are read here now** (D-188). An
        // in-screen instruction is lit pixels for the whole duration of a task rated on how little
        // it may glow, and the scan has already paid for this screen's light.
        //
        // Above BEGIN rather than beside it, so it is read on the way to the control rather than
        // discovered under a thumb. Not nullable: [Subroutine.briefing] is total over the roster,
        // so a Subroutine cannot reach a player with nothing said about it.
        current?.let {
            Box(
                Modifier.fillMaxWidth()
                    .edgeLine(PanelSide.Top, Ramp.Faint, 1.dp)
                    .edgeLine(PanelSide.Bottom, Ramp.Faint, 1.dp)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Words(
                    it.subroutine.briefing, Modifier.fillMaxWidth(),
                    size = 24, color = p.ink, lineHeight = 1.15, align = TextAlign.Center,
                )
            }
        }

        // **BEGIN does not name a screen, and cannot.** Which Subroutine opens is a fact about
        // the card that was just read, so the target is the actions layer's. Declared in
        // Flow.viaActions, where every built Subroutine screen is listed as a place this can land.
        //
        // ⚠️ **Absent where there is no Subroutine to begin**, rather than present and refusing.
        // A BEGIN that could be pressed with nothing behind it is how nine of the ten Subroutines
        // became unreachable: the press defaulted to the port's fixture and opened a Handshake over
        // whatever the house had actually dealt. This screen's other button still goes back — the
        // player is never stuck here.
        if (current != null) {
            KitPrimary("BEGIN", p, size = 32, onClick = actions.beginSubroutine)
        }
        KitQuiet("NOT THIS ONE", p) { go(ScreenId.Work) }
    }
}

/**
 * The scan caught something that is not yours.
 *
 * **Every game-side reason lands here.** Not assigned to you and blocked upstream produce the
 * same four words, so the refusal cannot be read backwards into a fact about the marker or about
 * anybody else.
 *
 * **NOTHING FOR YOU HERE is D-124's own vocabulary** and this screen said NOT YOURS until E-L4-2
 * caught it: the ruling names the words a registered card with no work for *you* answers with, and
 * a screen that paraphrases a copy ruling is a screen the next ruling has to be re-applied to.
 *
 * The phrasing is about **you**, deliberately, in the headline and in the line under it. The
 * alternatives — "already completed", "belongs to another resident" — would each be a small
 * statement about a player who is not standing there. D-124's second half is why the *other*
 * refusal keeps its own words: collapsing the two would let a Resident sweeping cards separate
 * *registered but not mine* from *not registered at all*, which is a detector.
 */
@Composable
fun ScanBadScreen() {
    ScanRefusal(headline = "NOTHING FOR\nYOU HERE")
}

/**
 * The card is not in this home at all.
 *
 * The **one** refusal that can afford to be specific, because it is a fact about a piece of paper
 * rather than about any player: the host printed a card and never registered it. Saying so costs
 * nothing and buys the only diagnosis available from inside a dark house — otherwise a missed
 * card reads as another player's marker and the setup fault stays invisible all evening.
 *
 * ### Nothing reports this anywhere. That is the decision, not an omission.
 *
 * It would be easy to surface a count at the end of the round, or a notice at the next meeting.
 * **Do not.** Either one turns the app into an arbiter of a player's claim: someone says *"I
 * scanned a card in the garage and it wasn't a marker"*, and a system report lets the room check
 * them. Fact-checking testimony is the thing this app refuses to do everywhere else.
 *
 * Left unreported it becomes material instead. A Resident who mentions it gives up their own
 * position to be useful, which is a real cost freely paid. An Insider can claim it to explain
 * standing at a marker doing nothing. Neither can be verified, which is the point.
 *
 * **The general rule, which this is a case of: the house announces only what no player could
 * have observed.** It reports a failed radio at the next meeting because a radio failure is
 * invisible to everyone including the player it happened to, so without the announcement a
 * phantom appears that nobody designed. An unregistered card was seen by whoever scanned it.
 * Everything a player saw is theirs to report, or to lie about.
 */
@Composable
fun ScanUnknownScreen() {
    ScanRefusal(headline = "NOT A MARKER")
}

/**
 * **The T card, while somebody else is reading** (D-199, `gdd.md:472`).
 *
 * *The station locks on any viewing. Others may stand here; only one person reads.* The third
 * refusal on the scan path, drawn by the same body as the other two so that, across a room, it is
 * the same screen with different words.
 *
 * **It is the one refusal that says something about another player, and what it says is already
 * in front of the scanner**: a person standing lit at a fixed place everybody knows, reading. The
 * house answers it to either role on the same terms, because either role may hold the station.
 *
 * ⚠️ **ONE READER AT A TIME is new copy and awaits the owner's eye** (D-199).
 */
@Composable
fun TermBusyScreen() {
    ScanRefusal(headline = "ONE READER\nAT A TIME")
}

/**
 * All three refusals, drawn by one function.
 *
 * **The three screens are identical apart from their two lines of text, and that must stay true.**
 * If they had different icons or different shapes, an onlooker could tell which refusal you got
 * from across a room — and one of them (NOTHING FOR YOU HERE) is reachable only by a Resident.
 * Sharing the body makes that identity structural instead of a coincidence someone later
 * "improves" away.
 *
 * **The words are the one thing that may differ, and D-124 requires that they do**: the host needs
 * the unregistered case loud during setup and the player needs the other case unremarkable during
 * a round, so the two must sound different. What may not differ is everything around them.
 */
@Composable
private fun ScanRefusal(headline: String) = Mockup {
    val go = navigator()
    val p = Palette.DARK
    // **The headline and the two ways out, and nothing else** (Step 3, screens say little). The
    // line of explanation under each headline is cut: the buzz has said *no*, the headline says
    // which no, and a sentence read in the dark at a card is a player standing lit for longer.
    KitScreen {
        KitHeader("SCAN", p)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PanelIcon(
                REFUSED, size = 72.dp, color = Ramp.Mid, strokeWidth = 3f,
                circles = listOf(Triple(32f, 32f, 26f)), viewBox = 64f,
            )
            Words(headline, size = 52, color = p.ink, lineHeight = 0.85, align = TextAlign.Center)
        }
        KitSecondary(
            "SCAN ANOTHER", p, size = 30, height = 64.dp, edge = p.ink, edgeWidth = 2.dp,
        ) { go(ScreenId.Scan) }
        KitQuiet("SEE MY SUBROUTINES", p) { go(ScreenId.Work) }
    }
}

/** The refusal mark, on the mockup's 64 grid: a ring and its bar. */
private val REFUSED = listOf("M14 50L50 14")

/**
 * Files: **one item, and every other app refuses.**
 *
 * Carrying is a device state rather than a rule the player has to remember: the padlock in the
 * strip (D-204) and the one line under the item say it, in the words the carrying widget uses.
 * The list of UNAVAILABLE apps and the STORAGE IN USE box went with the mockup's copy cut: a list
 * of refusals is lit rows saying what the line already says.
 *
 * ⚠️ **NOTHING ELSE OPENS UNTIL IT IS DELIVERED is new copy and awaits the owner's eye** (D-200).
 */
@Composable
fun FilesScreen() = Mockup {
    val p = Palette.DARK
    KitScreen(gap = 14.dp) {
        KitHeader("FILES", p, back = ScreenId.Home, meta = "1 ITEM")
        Column(
            Modifier.fillMaxWidth().border(2.dp, p.ink).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Words("DISK 01", size = 40, color = p.ink, lineHeight = 0.85)
            Words("DEGRADED . IN HAND\nDESTINATION . RACK", size = 22, color = p.mid, lineHeight = 1.1)
        }
        KitCaption("NOTHING ELSE OPENS UNTIL IT IS DELIVERED", p, size = 24)
    }
}

/**
 * Notes: **read only. It writes to you, not the other way round.**
 *
 * The line is plausible, unverifiable and deliberately not context aware. *These are not your
 * notes* is the whole joke and the whole mechanic: the device hands you an accusation you did not
 * make and cannot check. READ ONLY . NO INPUT PERMITTED went with the mockup's copy cut: there is
 * nothing on the screen to type into, which says it.
 */
@Composable
fun NotesScreen(vals: PanelVals) = Mockup {
    val p = Palette.DARK
    KitScreen(gap = 14.dp) {
        KitHeader("NOTES", p, back = ScreenId.Home, meta = "UNTITLED")
        Box(Modifier.fillMaxWidth().edgeLine(PanelSide.Top, p.faint, 1.dp).padding(top = 18.dp)) {
            Words(vals.noteLine, size = 34, color = p.ink, lineHeight = 1.1)
        }
        KitSpacer()
        KitCaption("THESE ARE NOT YOUR NOTES", p, Modifier.fillMaxWidth(), center = true)
    }
}

/**
 * Counts per room, three staleness bands, your own room outlined. Never dots.
 *
 * **STATIC lands here** (D-186). The plan, the labels and the counts are drawn exactly as they
 * always are and the noise goes over them — see `StaticNoise`, where *the real data faintly
 * beneath* is argued. Nothing about the surface changes underneath: the counts are still the
 * house's, still correct, and they are correct on screen again the instant the window closes.
 *
 * **The jam is read off the panel and never asked for**, so this screen has no way to be jammed by
 * anything except the house having said so, and no way to tell whose Static it was.
 *
 * YOUR ROOM OUTLINED went with the mockup's copy cut: the outline is the only two-unit edge on
 * the plan and the only room name in full ink, which says it.
 */
@Composable
fun TermLiveScreen(vals: PanelVals) {
    val go = navigator()
    TerminalView("TERMINAL . LIVE", ScreenId.Home, Plan.terminalCounts, vals) {
        // **Reading is an uncompletable task, so the motion budget applies** (`gdd.md:476`, D-199).
        // Standing still, glowing, at a place everybody knows: move too much and the view closes,
        // and *the exposure is the limit* rather than a timer.
        KitBudgetRow()
        KitSecondary("TIMELAPSE . 2:00 AT 6X", Palette.DARK) { go(ScreenId.Timelapse) }
    }
}

/**
 * Timelapse: two minutes at 6×, **plays once**.
 *
 * The gated resource. The live view is free and limited only by the motion budget; this one you
 * spend, which is what makes standing at the terminal a decision rather than a habit.
 *
 * ### A playback crossing a jam renders those seconds as static (D-186)
 *
 * *~1.7s of noise at 6×* — ten seconds compressed. **It is a forensic trace and it is meant to be
 * one**: a scar in the playback that says *something was hidden here, at this time*, without saying
 * what or by whom. A player who watches it back learns the window and nothing inside it.
 *
 * ⚠️ **What is drawn here is a jam over a FIXTURE playback**, because that is what this screen is:
 * E8 is design-complete and build-pending, there is no recorded history behind the numbers, and
 * nothing on this phone yet knows which second of a playback it is showing. So the noise appears
 * while the house says the monitoring is jammed, which is the right picture for the wrong reason —
 * and the seconds-crossing-the-window arithmetic belongs to whoever builds the real playback. It is
 * `PlanCounts`' own position exactly: the fixture is the screen until the history exists.
 */
@Composable
fun TimelapseScreen(vals: PanelVals) {
    val go = navigator()
    TerminalView("TERMINAL . -1:12", ScreenId.TermLive, Plan.timelapseCounts, vals) {
        // How far through the two minutes the playback is. A fixture, like the counts.
        Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, Ramp.Faint)) {
            Box(Modifier.fillMaxWidth(0.44f).fillMaxHeight().background(Ramp.Mid))
        }
        // The same budget as the live view: watching the playback is still standing at the
        // station reading (D-199).
        KitBudgetRow()
        // Playback can be abandoned. It plays once either way, so stopping early is a real
        // decision rather than a convenience.
        KitSecondary("STOP THE PLAYBACK", Palette.DARK) { go(ScreenId.TermLive) }
    }
}

/**
 * **The live view and the playback share one layout** (D-202, variants of one screen): the
 * header, the floor strip, the plan, and a foot that differs. Only the words and the foot change,
 * so across a room the two cannot be told apart.
 */
@Composable
private fun TerminalView(
    title: String,
    back: ScreenId,
    counts: List<Plan.RoomCount>,
    vals: PanelVals,
    foot: @Composable ColumnScope.() -> Unit,
) = Mockup {
    val p = Palette.DARK
    KitScreen(gap = 10.dp) {
        KitHeader(title, p, back = back)
        FloorStrip(p)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            KitLivePlan(counts, Modifier.fillMaxSize())
            vals.staticJam?.let { StaticNoise(it, Modifier.fillMaxSize()) }
        }
        foot()
    }
}

/**
 * GROUND / UPPER: **a reading, not a control** (`kit.py`'s `floor_strip`). The floor you are on
 * sits on the room fill; the shipped strip takes no taps.
 */
@Composable
private fun FloorStrip(p: Palette) {
    Row(Modifier.fillMaxWidth().height(44.dp).border(1.dp, p.line)) {
        Box(
            Modifier.weight(1f).fillMaxHeight().background(Ramp.Fill)
                .edgeLine(PanelSide.End, p.line, 1.dp),
            contentAlignment = Alignment.Center,
        ) { Words("GROUND", size = 24, color = p.ink, lineHeight = 1.0) }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Words("UPPER", size = 24, color = p.dim, lineHeight = 1.0)
        }
    }
}

/**
 * The motion budget as the mockup draws it: a reading between two hairlines, not a bar. See
 * [MotionBudgetRow] for why nothing here drains -- a bar that moved on a desktop would be moving
 * on nothing.
 */
@Composable
internal fun KitBudgetRow(p: Palette = Palette.DARK) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .edgeLine(PanelSide.Top, p.faint, 1.dp).edgeLine(PanelSide.Bottom, p.faint, 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Words("MOTION BUDGET", Modifier.weight(1f), size = 22, color = p.dim, lineHeight = 1.0)
        Words("HOLDING", size = 22, color = p.dim, lineHeight = 1.0)
    }
}

/**
 * Containment: **the house takes the only number back.**
 *
 * This is page 1 with **one widget swapped** — not a screen of its own. The Egress countdown
 * occupies the System Integrity widget's slot, at the same size, in the same place, and
 * everything below it carries on working: the next Subroutine, the app grid, the dock. That is
 * the whole point. The Residents lose their only measure of progress at exactly the moment they
 * most want it, and nothing else about the phone changes to soften it.
 *
 * The design's fixture draws placeholder rectangles below the widget because it only needed to
 * show the widget. Drawing the real springboard is what the swap actually means.
 */
@Composable
fun EgressWidgetScreen(vals: PanelVals) {
    HomeScreen(vals) { EgressWidget(it) }
}

/**
 * The countdown, built to the same measurements as the meter it replaces.
 *
 * It names both nodes because containment needs two people at two separate markers and nobody may
 * speak — the device saying where is the only coordination available.
 *
 * **This is where the Egress notification survives being swiped away** —
 * [NotificationKind.Egress]'s `heldBy` — so it names the same two rooms the banner did, out of
 * [PanelVals.egressNodes] rather than out of a second string. Two copies of that pair would send
 * two people who may not speak to two different places, and the mistake would look like a typo in
 * a diff.
 *
 * **The type is the house's too.** Beacon and Tether are mechanically identical in v1 and the
 * house picks between them at fire time, so a widget that always said BEACON would be the one
 * surface in the building contradicting the alert that woke everybody up.
 */
@Composable
private fun EgressWidget(vals: PanelVals) {
    KitLegendBox("EGRESS . ${vals.egressType}", Ramp.Ink, Ramp.Ink, edgeWidth = 2.dp) {
        // **HELD replaces the clock rather than sitting beside it** (`Effect.EgressHeld`, D-133). A
        // paused countdown drawn as a still number is indistinguishable from one the phone has
        // stopped receiving, so the pause dims the clock and the bar together.
        if (vals.egressHeld) {
            Words("HELD", size = 76, color = Ramp.Dim, lineHeight = 0.8)
        } else {
            Words("1:42", size = 76, lineHeight = 0.8)
        }
        KitMeter(
            PanelVals.METER_SEGMENTS, vals.egressLit,
            if (vals.egressHeld) Ramp.Dim else Ramp.Mid, Ramp.Faint,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Words("CONTAIN AT", size = 22, color = Ramp.Dim, lineHeight = 1.0)
            Words(vals.egressNodes.joinToString(" . "), size = 22, lineHeight = 1.0)
        }
        // **The house's answer to this phone's own beat, and it says nothing about anybody else**
        // (`Effect.SyncPulseAnswered`, `gdd.md:355`). ON THE BEAT is *the house is holding your
        // tap*; OFF THE BEAT is every other answer there is, because the effect is one shape with
        // one boolean. Drawn only once this phone has returned a beat.
        vals.beatHeld?.let { held ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Words("YOUR BEAT", size = 22, color = Ramp.Dim, lineHeight = 1.0)
                Words(
                    if (held) "ON THE BEAT" else "OFF THE BEAT",
                    size = 22, color = if (held) Ramp.Ink else Ramp.Dim, lineHeight = 1.0,
                )
            }
        }
    }
}
