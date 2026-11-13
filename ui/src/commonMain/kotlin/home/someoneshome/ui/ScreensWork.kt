package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
fun WorkScreen(vals: PanelVals) {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("SUBROUTINES", size = 7.0, color = Amber.Dim, tracking = 0.16)
            // Counted off the rows the house sent, both halves. The total is the ORDER's length
            // and never a settings number: an order that shortened while work was blocked would
            // make its own length a tell, so the length drawn here has to be the length received.
            Label(
                "${vals.nextUp.done} OF ${vals.nextUp.total} DONE",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Bright, tracking = 0.16, align = TextAlign.End,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
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

        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
            verticalArrangement = Arrangement.spacedBy(5.u),
        ) {
            Label(
                "A SUBROUTINE OPENS ONLY AT ITS MARKER.\nSCAN TO BEGIN.",
                size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.9,
            )
            LightKey()
        }
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
    // width while only its lit cells are drawn, so with the mark first the space after it varies
    // with the value: BRIGHT ends up welded to its own word while DARK floats free of one, and the
    // eye starts binding each mark to the word on its RIGHT -- reading the row as LIGHT = one
    // cell. Word first puts the constant gap where the binding is and the varying gap between
    // pairs, where extra space costs nothing.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("LIGHT", size = 6.0, color = Amber.Dim, tracking = 0.14)
        LightSignature.entries.forEach { signature ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(signature.name.uppercase(), size = 6.0, color = Amber.Faint, tracking = 0.1)
                LightMark(signature, Amber.Mid, sample = true)
            }
        }
    }
}

/**
 * **One named row of the order — and the only kind of row that names anything.**
 *
 * It takes an [OrderRow.Named] whole rather than a name and a signature separately, which is what
 * makes the pair impossible to mismatch here: the row arrived with its light already resolved off
 * the roster, so a screen cannot promise BRIGHT beside a Subroutine the roster rates DARK.
 *
 * There is deliberately **no overload taking a [Blocked][OrderRow.Blocked] row**. Blocked work is
 * drawn by [WorkLocked], which takes nothing at all.
 */
@Composable
private fun WorkRow(
    row: OrderRow.Named,
    current: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val name = row.label
    val light = row.light
    val done = row.done
    val ink = when {
        current -> Amber.Bright
        done -> Amber.Faint
        else -> Amber.Mid
    }
    val meta = if (current) Amber.Bright else if (done) Amber.Faint else Amber.Dim

    Row(
        Modifier.fillMaxWidth()
            .border(1.u, if (current) Amber.Bright else Amber.Edge)
            .background(if (current) Amber.Edge else Color.Transparent)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 7.u, vertical = if (current) 7.u else 6.u),
        horizontalArrangement = Arrangement.spacedBy(6.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Done is a filled box, current is an outlined one at full intensity, pending is outlined
        // and dim. Three states in one glyph rather than three different marks.
        if (done) {
            Box(Modifier.size(9.u).background(Amber.Bright))
        } else {
            Box(Modifier.size(9.u).border(1.u, if (current) Amber.Bright else Amber.Dim))
        }
        Label(
            name,
            modifier = Modifier.weight(1f),
            size = if (current) 8.5 else 7.5,
            color = ink,
            tracking = if (current) 0.06 else 0.0,
            // Struck through as well as dimmed. Dim alone is ambiguous on this screen, where
            // "faint" is also how a locked row reads.
            decoration = if (done) TextDecoration.LineThrough else null,
        )
        // **The destination pair, drawn only where there is one.** A known line carries its room
        // and its glyph off the wire; a line the house dealt in a round armed with no home carries
        // neither, and the column is simply absent rather than filled with a room this phone made
        // up. See OrderRow.Named.room.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.room?.let {
                Label(it, modifier = Modifier.testTag(ORDER_DESTINATION), size = 6.0, color = meta)
            }
            row.marker?.let {
                MarkerGlyph(it, if (current) 11.u else 10.u, meta, Modifier.testTag(ORDER_DESTINATION))
            }
        }
        // Its own column, past the destination pair, because it is read DOWN the list rather than
        // across a row: choosing a dark route means comparing this row's light with the next
        // one's, and the only column that stays aligned when the names differ in length is the
        // last one. It takes the row's own intensity, so a completed row's light fades with the
        // rest of it rather than staying at full step and shouting about work already done.
        LightMark(light, meta)
    }
}

/**
 * A blocked chain step. Names nothing — not the Subroutine, not who would free it.
 *
 * **And therefore no light mark either.** The signature is a property of a particular Subroutine,
 * so drawing one here would describe the very thing this row exists not to describe. Nothing on
 * the ladder is zero cells, so the empty column reads as *nothing is claimed* rather than as a
 * dark Subroutine.
 */
@Composable
private fun WorkLocked() {
    Row(
        Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
        horizontalArrangement = Arrangement.spacedBy(6.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.u).border(1.u, Amber.Faint))
        Label("LOCKED", modifier = Modifier.weight(1f), size = 7.5, color = Amber.Faint)
        Label("WAITING UPSTREAM", size = 6.0, color = Amber.Faint)
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
fun ScanCaughtScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    // ⚠️ **Null on a housed phone the house has not answered**, and every read of it below is
    // guarded rather than defaulted. It used to resolve to the port's fixture — HANDSHAKE, room
    // GARAGE — so this screen showed the owner a Subroutine and a room in a house that had dealt
    // them neither, and did it with exactly the confidence it shows a real one. What an unanswered
    // scan draws now is the shape of nothing: no glyph, no room, no briefing, and no BEGIN.
    val current = vals.current
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Label("CAUGHT", size = 7.0, color = Amber.Dim, tracking = 0.16)

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The marker you just scanned -- the same shape the work order sent you to, not the
            // host-setup fixture this used to read by mistake.
            current?.marker?.let { MarkerGlyph(it, 52.u, Amber.Bright) }
            // **The flavour line that used to sit here is gone** (the owner's 2026-08-24 ruling).
            // It was a fake-technical sentence — RESYNC THE KEEPALIVE — that nothing on the wire
            // ever carried, so it was the port's own words printed over whatever Subroutine a real
            // scan had actually opened. What a player has to do is the briefing at the foot of this
            // screen (D-188), which is total over the roster and cannot be absent; a second, larger
            // sentence above it competed with the one that is true.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The room is dropped rather than blanked where the house named none — an empty
                // destination is lawful (`OrderLine.Known.at`) and a leading separator over an
                // absent room reads as a rendering fault rather than as a room nobody sent.
                Label(
                    listOfNotNull(
                        current?.room,
                        current?.let { "SUBROUTINE ${it.index} OF ${it.total}" }
                            ?: PanelVals.NOTHING_OPEN,
                    ).joinToString(" . "),
                    size = 7.0, color = Amber.Dim, tracking = 0.12,
                )
                // The fourth surface, and the one D-106 does not name. It is here because BEGIN
                // and NOT THIS ONE are both live below: this is the last moment the choice is
                // still a choice, and a route planned on the work order is re-decided here by
                // anyone who walked past someone on the way. Flagged for review.
                //
                // A light signature is a promise about how much a screen will glow for the next
                // ninety seconds, which is the one thing a player plans a walk around. Absent where
                // there is no Subroutine to make it about.
                current?.let { LightMark(it.light, Amber.Dim) }
            }
            Label(
                if (current != null) "THE CARD IN YOUR HAND MATCHES" else "WAITING FOR THE HOUSE",
                size = 6.0, color = Amber.Faint, tracking = 0.1,
            )
        }

        // **The last lit surface before the work, and ALL TEN are read here now** (D-188). It began
        // as Sniff's exception — its own screen is black until the answer (D-137), so the sentence
        // telling a player what to do there would be black ink on black glass — and the ruling
        // inverted it: an in-screen instruction is lit pixels for the whole duration of a task
        // rated on how little it may glow, and the scan has already paid for this screen's light.
        //
        // Above BEGIN rather than beside it, so it is read on the way to the control rather than
        // discovered under a thumb — and dim, because this screen is lit and several of the ten
        // that follow it are not.
        //
        // Not nullable any more, which is the point: [Subroutine.briefing] is total over the
        // roster, so a Subroutine cannot reach a player with nothing said about it.
        current?.let {
            Label(
                it.subroutine.briefing,
                modifier = Modifier.fillMaxWidth(),
                size = 6.5, color = Amber.Dim, tracking = 0.12, lineHeight = 1.8,
                align = TextAlign.Center,
            )
        }

        // **BEGIN does not name a screen, and cannot.** Which Subroutine opens is a fact about
        // the card that was just read, so the target is the actions layer's — the same shape as a
        // tap on the plan, which opens whichever room is under the finger. Declared in
        // Flow.viaActions, where every built Subroutine screen is listed as a place this can land.
        //
        // A Subroutine with no interaction built yet opens nothing and the phone stays here,
        // which is the fail-closed direction: routing an unbuilt Subroutine to the nearest built
        // one would put a player through the wrong work and read as a routing bug rather than as
        // a missing screen.
        // ⚠️ **Absent where there is no Subroutine to begin**, rather than present and refusing.
        // A BEGIN that could be pressed with nothing behind it is how nine of the ten Subroutines
        // became unreachable: the press defaulted to the port's fixture and opened a Handshake over
        // whatever the house had actually dealt. A control that is not drawn cannot do that, and
        // this screen's other button still goes back — the player is never stuck here.
        if (current != null) {
            PanelButton(
                "BEGIN",
                border = Amber.Bright, ink = Amber.Bright,
                size = 9.0, tracking = 0.18, verticalPadding = 13.u,
                onClick = actions.beginSubroutine,
            )
        }
        PanelButton(
            "NOT THIS ONE",
            border = Amber.Faint, ink = Amber.Dim,
            size = 7.0, verticalPadding = 9.u,
            onClick = { go(ScreenId.Work) },
        )
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
 * Carrying is a device state rather than a rule the player has to remember. The refusals are
 * listed as standing conditions — UNAVAILABLE, on screen, before you try — which is the safe way
 * to say no.
 */
@Composable
fun FilesScreen() {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("FILES", size = 7.0, color = Amber.Dim, tracking = 0.16)
            Label(
                "1 ITEM",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Bright, tracking = 0.16, align = TextAlign.End,
            )
        }

        InfoBox(border = Amber.Bright, gap = 4.u) {
            Label("DISK 01", size = 9.0, color = Amber.Bright, tracking = 0.06)
            Label(
                "DEGRADED . IN HAND\nDESTINATION . RACK",
                size = 6.5, color = Amber.Dim, tracking = 0.1, lineHeight = 1.8,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            listOf("SUBROUTINES", "NOTES", "ARCHIVE").forEach { name ->
                Row(
                    Modifier.fillMaxWidth().border(1.u, Amber.Edge)
                        .padding(horizontal = 7.u, vertical = 6.u),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Label(name, size = 7.0, color = Amber.Faint)
                    Label("UNAVAILABLE", size = 7.0, color = Amber.Faint)
                }
            }
        }

        Box(Modifier.weight(1f))

        Box(
            Modifier.fillMaxWidth().border(1.u, Amber.Dim).padding(7.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                "STORAGE IN USE\nHANDS FULL UNTIL DELIVERED",
                size = 6.5, color = Amber.Mid, tracking = 0.1, lineHeight = 1.9,
                align = TextAlign.Center,
            )
        }
    }
}

/**
 * Notes: **read only. It writes to you, not the other way round.**
 *
 * The line is plausible, unverifiable and deliberately not context aware. *These are not your
 * notes* is the whole joke and the whole mechanic: the device hands you an accusation you did not
 * make and cannot check.
 */
@Composable
fun NotesScreen(vals: PanelVals) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("NOTES", size = 7.0, color = Amber.Dim, tracking = 0.16)
            Label(
                "UNTITLED",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Faint, tracking = 0.16, align = TextAlign.End,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge).padding(9.u)) {
            Readout(vals.noteLine, size = 18.0, color = Amber.Bright, lineHeight = 1.45)
        }

        Box(
            Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                "READ ONLY . NO INPUT PERMITTED\nTHESE ARE NOT YOUR NOTES",
                size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.9,
                align = TextAlign.Center,
            )
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
 */
@Composable
fun TermLiveScreen(vals: PanelVals) {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("TERMINAL . LIVE", size = 7.0, color = Amber.Dim, tracking = 0.14)
        }
        FloorStrip()
        Box(Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge)) {
            LivePlan(Plan.mapCells(), Modifier.fillMaxSize())
            PlanRoomLabels(Modifier.fillMaxSize())
            PlanCounts(Plan.terminalCounts, Modifier.fillMaxSize())
            vals.staticJam?.let { StaticNoise(it, Modifier.fillMaxSize()) }
        }
        Label("YOUR ROOM OUTLINED", size = 5.5, color = Amber.Faint, tracking = 0.08)
        // **Reading is an uncompletable task, so the motion budget applies** (`gdd.md:476`, D-199).
        // Standing still, glowing, at a place everybody knows: move too much and the view closes,
        // and *the exposure is the limit* rather than a timer.
        MotionBudgetRow(Amber.Dim, Amber.Bright)
        PanelButton(
            "TIMELAPSE . 2:00 AT 6X",
            border = Amber.Dim, ink = Amber.Bright,
            size = 7.5, tracking = 0.14, verticalPadding = 8.u,
            onClick = { go(ScreenId.Timelapse) },
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
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.TermLive, Amber.Dim)
            Label("TERMINAL . -1:12", size = 7.0, color = Amber.Dim, tracking = 0.14)
        }
        FloorStrip()
        Box(Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge)) {
            LivePlan(Plan.mapCells(), Modifier.fillMaxSize())
            PlanRoomLabels(Modifier.fillMaxSize())
            PlanCounts(Plan.timelapseCounts, Modifier.fillMaxSize())
            vals.staticJam?.let { StaticNoise(it, Modifier.fillMaxSize()) }
        }
        Row(Modifier.fillMaxWidth().height(6.u), horizontalArrangement = Arrangement.spacedBy(1.u)) {
            Box(Modifier.weight(44f).fillMaxHeight().background(Amber.Bright))
            Box(Modifier.weight(56f).fillMaxHeight().background(Amber.Edge))
        }
        // The same budget as the live view: watching the playback is still standing at the
        // station reading (D-199).
        MotionBudgetRow(Amber.Dim, Amber.Bright)
        // Playback can be abandoned. It plays once either way, so stopping early is a real
        // decision rather than a convenience.
        PanelButton(
            "STOP THE PLAYBACK",
            border = Amber.Edge, ink = Amber.Dim,
            size = 7.0, tracking = 0.14, verticalPadding = 8.u,
            onClick = { go(ScreenId.TermLive) },
        )
    }
}

@Composable
private fun FloorStrip() {
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
