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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import home.someoneshome.model.Balance

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
    val threshold = with(LocalDensity.current) { SWIPE_DISMISS.toPx() }
    // Where the finger has taken it, in pixels, never below zero. Keyed on the notification so a
    // second one arriving does not inherit half a swipe made at the first.
    var lifted by remember(notification) { mutableStateOf(0f) }
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
fun WorkScreen(vals: PanelVals) {
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
            row.light?.let { WorkMark(it, meta) }
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
fun ScanScreen(vals: PanelVals) {
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
fun ScanCaughtScreen(vals: PanelVals) {
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
            //
            // **The room on its own line, over the count and the mark** (D-256, H10). One line,
            // `<ROOM> . SUBROUTINE n OF 8` with the mark beside it, fitted the design's GARAGE and
            // nothing a host actually types: on the Simulators SCULLERY pushed the mark off the
            // panel's edge and FRONT ROOM lost it altogether. Two lines fit any room, and a room
            // too long for one line wraps under itself rather than under the mark.
            //
            // The room is dropped rather than blanked where the house named none — an empty
            // destination is lawful (`OrderLine.Known.at`) and a line holding no room reads as a
            // rendering fault rather than as a room nobody sent.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                current?.room?.let {
                    Words(
                        it, Modifier.fillMaxWidth(),
                        size = 26, color = Ramp.Mid, lineHeight = 1.0, align = TextAlign.Center,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Words(
                        current?.let { "SUBROUTINE ${it.index} OF ${it.total}" } ?: PanelVals.NOTHING_OPEN,
                        Modifier.weight(1f, fill = false),
                        size = 26, color = Ramp.Mid, lineHeight = 1.0, align = TextAlign.Center,
                    )
                    // The fourth surface, and the one D-106 does not name. It is here because BEGIN
                    // and NOT THIS ONE are both live below: this is the last moment the choice is
                    // still a choice. Flagged for review. Absent where there is no Subroutine.
                    current?.let { WorkMark(it.light, Ramp.Mid) }
                }
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
 * from across a room — and one of them (NOTHING FOR YOU HERE) answers a card holding no work for
 * you, for either role (D-109, D-124).
 * Sharing the body makes that identity structural instead of a coincidence someone later
 * "improves" away.
 *
 * **The words are the one thing that may differ, and D-124 requires that they do**: the host needs
 * the unregistered case loud during setup and the player needs the other case unremarkable during
 * a round, so the two must sound different. What may not differ is everything around them.
 */
@Composable
private fun ScanRefusal(headline: String) {
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
fun FilesScreen(vals: PanelVals) {
    val p = Palette.DARK
    // **This phone's carry, or an empty FILES** (D-257): `1 ITEM . DISK 01 . RACK` was drawn on
    // every phone in every round, carrying or not. Nothing writes a carry yet (E7), so on a housed
    // phone this is the empty folder until the circuit is built. The DEGRADED condition went with
    // the fixture: a carry is an item and a destination, and nothing says what state it is in.
    val filed = vals.filed
    KitScreen(gap = 14.dp) {
        KitHeader("FILES", p, back = ScreenId.Home, meta = if (filed != null) "1 ITEM" else "0 ITEMS")
        filed?.let {
            Column(
                Modifier.fillMaxWidth().border(2.dp, p.ink).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Words(it.item, size = 40, color = p.ink, lineHeight = 0.85)
                Words("IN HAND\nDESTINATION . ${it.to}", size = 22, color = p.mid, lineHeight = 1.1)
            }
            KitCaption("NOTHING ELSE OPENS UNTIL IT IS DELIVERED", p, size = 24)
        }
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
    val actions = LocalActions.current
    TerminalView("TERMINAL . LIVE", ScreenId.Home, vals.terminalMap, vals) {
        // **Reading is an uncompletable task, so the motion budget applies** (`gdd.md:476`, D-199).
        // Standing still, glowing, at a place everybody knows: move too much and the view closes,
        // and *the exposure is the limit* rather than a timer.
        KitBudgetRow()
        // **The station's one gated resource** (D-319). Ready, it asks the house for the playback;
        // cooling down or with no history yet, it is dimmed in the dim ink, takes no press, and says
        // how long: `TIMELAPSE . READY IN m:ss` (the owner's words). The cooldown is the station's,
        // so a reader who just arrived can find it cooling from somebody else's playback.
        val p = Palette.DARK
        KitSecondary(
            vals.timelapseLabel, p, ink = if (vals.timelapseReady) p.ink else p.dim,
            onClick = if (vals.timelapseReady) actions.playTimelapse else null,
        )
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
 * **Built** (D-319): the house keeps the station's history, a frame of bare counts every two
 * seconds, and sends its reader the playback three frames a beat, each shown for a third of a
 * second ([PanelVals.timelapseMap]). A frame taken while the house was jammed carries nothing but
 * that, and plays as Static's noise over the plan with no counts under it ([PanelVals.lapseNoise]).
 * A jam that lands *now* veils this screen as it veils the live view: both are monitoring surfaces.
 * The harness still draws the fixture's playback, with no house.
 */
@Composable
fun TimelapseScreen(vals: PanelVals) {
    val go = navigator()
    TerminalView("TERMINAL . ${vals.timelapseAt}", ScreenId.TermLive, vals.timelapseMap, vals, played = vals.lapseNoise) {
        // How far through the history the playback is: the house's frames (D-319), the fixture's in
        // the harness.
        Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, Ramp.Faint)) {
            Box(Modifier.fillMaxWidth(vals.timelapsePlayed).fillMaxHeight().background(Ramp.Mid))
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
    map: LiveMap?,
    vals: PanelVals,
    /** A playback frame the house was jammed for, as its noise and frame (D-319); the playback's only. */
    played: Pair<StaticJam, Int>? = null,
    foot: @Composable ColumnScope.() -> Unit,
) {
    val p = Palette.DARK
    KitScreen(gap = 10.dp) {
        KitHeader(title, p, back = back)
        // The plan's floors go with the plan: the bungalow's GROUND and UPPER in the harness, the
        // home's own storeys on a housed phone (D-257, D-316).
        if (map != null) FloorStrip(p, map.storeys, map.storey, map.pick())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (map != null) KitLivePlan(map, Modifier.fillMaxSize()) else NoPlan(Modifier.fillMaxSize())
            // A playback frame the house was jammed for (D-319), then a jam landing now (D-186).
            played?.let { (jam, frame) -> StaticNoise(jam, Modifier.fillMaxSize(), frame) }
            vals.staticJam?.let { StaticNoise(it, Modifier.fillMaxSize()) }
        }
        foot()
    }
}

/**
 * **Where a plan goes on a phone that was sent none** (D-257): the frame and a dash, nothing else.
 *
 * No phone is sent the home's plan and no message carries an occupancy count (see
 * [PanelVals.terminalCounts]), so the Terminal and the couch draw the space the plan will take and
 * [PanelVals.NO_READING] in it, the honest-empty every other reading on a housed phone uses. Not a
 * sentence about why: the reader is standing at a station in the dark (screens say little).
 */
@Composable
internal fun NoPlan(modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, Ramp.Faint), contentAlignment = Alignment.Center) {
        Words(PanelVals.NO_READING, size = 44, color = Ramp.Dim, lineHeight = 1.0)
    }
}

/**
 * GROUND / UPPER (`kit.py`'s `floor_strip`): the floor drawn sits on the room fill. [storeys] are
 * the plan's own, in plan order, one cell each (the bungalow's two in the harness, the home's on a
 * housed phone, D-316), and [shown] is the one the plan below draws.
 *
 * **A control on a home of more than one storey** (D-319, the owner's ruling: *there's only one
 * terminal in the entire home, so you must be able to see all floors by tapping the strip to
 * switch*): a tap on a storey's cell draws that storey, through [onPick]. Its look does not change;
 * the storey drawn is the one on the room fill, as before. Null [onPick] is the reading it was:
 * the fixture's bungalow, and a home of one storey.
 */
@Composable
internal fun FloorStrip(p: Palette, storeys: List<String>, shown: Int, onPick: ((Int) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(44.dp).border(1.dp, p.line)) {
        storeys.forEachIndexed { i, name ->
            var cell = Modifier.weight(1f).fillMaxHeight()
            if (onPick != null) cell = cell.tap { onPick(i) }
            if (i == shown) cell = cell.background(Ramp.Fill)
            if (i < storeys.lastIndex) cell = cell.edgeLine(PanelSide.End, p.line, 1.dp)
            Box(cell, contentAlignment = Alignment.Center) {
                Words(name, size = 24, color = if (i == shown) p.ink else p.dim, lineHeight = 1.0)
            }
        }
    }
}

/**
 * The motion budget as the mockup draws it: a reading between two hairlines, not a bar. On every
 * Subroutine screen (44 tall there) and on the Terminal's live view and timelapse, because reading
 * the map is an uncompletable task (`gdd.md:476`, D-199).
 *
 * It calibrates on entry against that player's own hand steadiness, and it is the **one** thing in
 * the game a client adjudicates (D-043). None of that exists yet: there is no motion on a desktop
 * render, so this states the condition and does not draw a draining bar. **A bar that moved here
 * would be moving on nothing**, which on the one client-adjudicated value in the game is the worst
 * place to put a decorative animation.
 */
@Composable
internal fun KitBudgetRow(p: Palette = Palette.DARK, height: Dp = 40.dp) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = height)
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
    val actions = LocalActions.current
    // **At a node the whole widget is the tap target** (D-295): one target as large as the widget,
    // pressed by somebody who is not looking at it, and not one pixel taller than the widget is
    // without it, so the app grid below keeps every row on the smallest panel (`PulseControlRender`).
    KitLegendBox(
        "EGRESS . ${vals.egressType}", Ramp.Ink, Ramp.Ink, edgeWidth = 2.dp,
        modifier = if (vals.pulseLive) Modifier.tap { actions.tapPulse() } else Modifier,
    ) {
        // **HELD replaces the clock rather than sitting beside it** (`Effect.EgressHeld`, D-133). A
        // paused countdown drawn as a still number is indistinguishable from one the phone has
        // stopped receiving, so the pause dims the clock and the bar together.
        if (vals.egressHeld) {
            Words("HELD", size = 76, color = Ramp.Dim, lineHeight = 0.8)
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Words(vals.egressLeftText, Modifier.weight(1f), size = 76, lineHeight = 0.8)
            if (vals.pulseLive) PulseControl(vals.pulseTaps)
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

/**
 * **The tap control after the node scan** (D-295, the owner's Q2 ruling, option B).
 *
 * Drawn beside the countdown, on a phone whose person scanned one of the nodes while the Egress
 * runs, and nowhere else: the same control for an Insider and a Resident, because which card is a
 * node is public and the role is not read. The whole widget is the target (see [EgressWidget]);
 * this is its face: Handshake's word (GDD:371: *both players tap*) and under it four pips, input
 * echo, one per tap toward the next four. Beside the clock rather than under the rooms, because the
 * springboard does not scroll and on an iPhone 16 Pro there is no height to spare below the widget.
 *
 * **The pulse is felt, not drawn** (rule 5): nothing here moves with the beat. The phone buzzes the
 * house's grid through its measured offset (`PulseBuzzer`); the thumb answers it; the house grades
 * it and says ON or OFF THE BEAT in the row above.
 */
@Composable
private fun PulseControl(taps: Int) {
    Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Words("TAP", size = 40, tracking = 0.2, lineHeight = 1.0)
        KitMeter(Balance.SYNC_PULSE_BEATS, taps, Ramp.Ink, Ramp.Faint)
    }
}

/**
 * **The floor strip's press, for a map that can switch storeys** (D-319), or null for one that
 * cannot: the bungalow's, and a home of one storey.
 */
@Composable
internal fun LiveMap.pick(): ((Int) -> Unit)? = if (switchable) LocalActions.current.showStorey else null
