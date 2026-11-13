package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset

/**
 * Arming, and the lantern.
 *
 * The host turns the lights off; everything after that is the house answering rather than the
 * host announcing. From here the device is dark-field for the rest of the round, with exactly one
 * exception — the lantern, which is a lit field because being a lamp is its whole job.
 */

/**
 * The one line the house can hold over you, handed over **before the lights go out**.
 *
 * Seen by the house only, deleted when the round ends — both stated on screen, because the player
 * is being asked for something real and the promise has to be legible before they type it. The
 * Insider's blackmail text later quotes this line back; that is the entire mechanism.
 */
@Composable
fun SecretScreen() {
    val actions = LocalActions.current
    val lobby = LocalLobby.current
    PrePage(gap = 7) {
        // **The page name, with the way back beside it** (E-PAY-6). It was a bare `Label` drawing
        // the same thing [PreHeading] draws, which is how this screen came to be the one trap in
        // setup: `ScreenGraph.exitsOf(Secret)` is empty, HAND IT OVER refuses a blank line and stays
        // put, and a player who opened it with nothing to say had no control on the screen at all.
        // `Lobby` is the fallback rather than the destination — see [BackChevron].
        PreHeading("BEFORE THE LIGHTS GO OUT", back = ScreenId.Lobby)

        InfoBox(border = Amber.BoneInk, gap = 4.u) {
            Label(
                "SOMETHING YOU WOULD RATHER NOT EXPLAIN",
                size = 9.0, color = Amber.BoneInk, tracking = 0.04, lineHeight = 1.5,
            )
            Label(
                "One line, and make it real. A thing you did, owe, broke, or never told them. " +
                    "If the house picks you tonight, this is what it will hold over you.",
                size = 7.5, color = Amber.BoneDeep, lineHeight = 1.8,
            )
        }

        Column(Modifier.fillMaxWidth().weight(1f).border(1.u, Amber.BoneEdge).padding(7.u)) {
            Label(
                "YOUR LINE",
                modifier = Modifier.padding(bottom = 5.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.14,
            )
            // Real typing, in lower case. `transform` is identity here rather than the field's
            // usual shout: a house name is a label and a confession is a sentence, and the one
            // screen that asks somebody for something true must not restyle it as they type.
            // **The field fills the panel it is drawn in** (E-PAY-6). It was one line pinned to the
            // top of a box four fifths of a screen tall, and a tap anywhere in the rest of it hit
            // nothing — on the one screen in this game where somebody is being asked for something
            // they would rather not say. The glyphs still sit at the top; what changed is what the
            // finger may aim at. See [ReadoutField].
            ReadoutField(
                value = lobby.line.text,
                onValueChange = actions.typeLine,
                modifier = Modifier.fillMaxWidth().weight(1f),
                size = 17.0, color = Amber.BoneInk,
                hint = "one line",
                transform = { it },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            // The two promises, in the host's own words. They are on this screen because the
            // player is being asked for something real on the strength of them, and both are
            // kept below: the host holds the line in memory and writes it nowhere, and the round
            // ending drops it on both phones.
            PreRow("SEEN BY", "THE HOUSE ONLY", border = Amber.BoneSoft, size = 6.5, verticalPadding = 5.u)
            PreRow("DELETED", "WHEN THE ROUND ENDS", border = Amber.BoneSoft, size = 6.5, verticalPadding = 5.u)
        }

        // A refused hand-over stays on this screen with the reason under the button. Nothing
        // about this is mid-round: the lights are still on and the player is owed the reason.
        lobby.refusal?.let {
            PreNote(it, color = Amber.BoneDim, size = 6.5, lineHeight = 1.0, align = TextAlign.Center)
        }

        SlateButton(
            if (lobby.line.handedOver) "HAND IT OVER AGAIN" else "HAND IT OVER",
            actions.handOverLine,
            tracking = 0.18, verticalPadding = 11.u,
        )
    }
}

/**
 * The perimeter arms — **the house answering, not the host announcing**.
 *
 * The lights have just gone out, so this is the first dark-field screen anyone sees, and it says
 * only what is true for everyone. Six residents accounted for; no roles, no assignments, nothing
 * that differs by device.
 */
@Composable
fun ArmedScreen() = Mockup {
    val go = navigator()
    KitScreen {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Words("PERIMETER\nARMED", size = 72, lineHeight = 0.85, tracking = 0.04, align = TextAlign.Center)
            Box(Modifier.size(width = 48.dp, height = 2.dp).background(Ramp.Line))
            Words(
                "6 RESIDENTS\nACCOUNTED FOR", size = 26, color = Ramp.Dim, lineHeight = 1.1,
                align = TextAlign.Center,
            )
            // Outline only: the lights have just gone out, and nothing here may flare.
            KitSecondary(
                "CONTINUE", Palette.DARK.copy(ink = Ramp.Mid),
                Modifier.padding(top = 20.dp).width(200.dp),
            ) { go(ScreenId.Home) }
        }
    }
}

/**
 * The backlog, delivered the moment signal returned.
 *
 * **Randomised leftovers, and the house row looks the same either way.** The count and the mix
 * differ per player so inbox density can never imply a role, and the house's own row carries an
 * identical sender, time and preview for both roles — the thread has to be *opened* to read.
 */
@Composable
fun RevealScreen(vals: PanelVals) = Mockup {
    val go = navigator()
    KitScreen(gap = 8.dp) {
        KitHeader("MESSAGES", Palette.DARK, back = ScreenId.Home, meta = "${vals.inboxCount} MESSAGES")
        // One row per conversation, on hairlines (D-202). D-105: no read state, so no unread count
        // and no NEW tag.
        Column(Modifier.weight(1f).fillMaxWidth().edgeLine(PanelSide.Top, Ramp.Faint, 1.dp)) {
            vals.inbox.forEach { row ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).edgeLine(PanelSide.Bottom, Ramp.Faint, 1.dp)
                        .tap { go(ScreenId.RevealThread) }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Tag(row.from, row.fromInk)
                        Tag(row.at, Ramp.Dim)
                    }
                    Words(
                        row.preview, size = 22, color = row.ink, lineHeight = 1.0,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        ReplyUnavailable()
    }
}

/**
 * The house thread, opened. **Only the newest message differs by role.**
 *
 * Everything above it is shared word for word, which is what makes the last one deniable: a
 * Resident who glimpsed a neighbour's screen would see the same three messages they have.
 *
 * **All four of the house's texts are here** (D-200), the 21:01 line included: the house is one
 * conversation and one row in Messages, so this is the only place its texts are read in order.
 *
 * The house never addresses a Resident directly. It is intimate only with the people it owns.
 */
@Composable
fun RevealThreadScreen(vals: PanelVals) = Mockup {
    KitScreen {
        KitHeader("HOUSE", Palette.DARK, back = ScreenId.Reveal, meta = "${vals.houseThread.size} MESSAGES")
        // Every text drawn alike, oldest first (D-202). THE NEWEST WAS ONCE STAMPED "NEW" AND THE
        // TAG HAD TO GO (D-105): an unread badge on the one message whose text differs by role,
        // claiming the phone knows what this player has looked at, which nothing in the app does.
        // It is last in the thread, and that is all that marks it.
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vals.houseThread.forEach { (at, text) ->
                Column(
                    Modifier.padding(end = 28.dp).fillMaxWidth().border(1.dp, Ramp.Faint)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Tag(at, Ramp.Dim)
                    Words(text, size = 24, color = Ramp.Mid, lineHeight = 1.1)
                }
            }
        }
        ReplyUnavailable()
    }
}

/**
 * Messages receives but cannot send, and says so as a **standing condition** rather than as a
 * refusal when you try.
 *
 * That is the safe shape for saying no: a strip that is always on screen carries no information
 * about the moment you acted. It is also §5.1's enforced silence, rendered — the Insiders cannot
 * reply to the house, and nobody can message anybody.
 */
@Composable
private fun ReplyUnavailable() {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).dashedBorder(Ramp.Faint),
        contentAlignment = Alignment.Center,
    ) {
        Words("REPLY UNAVAILABLE", size = 22, color = Ramp.Dim, lineHeight = 1.0, align = TextAlign.Center)
    }
}

/**
 * The lantern, and the phone's own idiom for something arriving (D-118, D-119).
 *
 * **Locked IS the lamp**, and the house sets the level, not you. The one inverted-field screen in
 * the game: amber ground, black glyphs, filling the panel. That is not a style choice — this
 * screen's purpose is to emit light, so it is the only place where maximising lit pixel area is
 * correct. It carries its own status row in black rather than showing the real one, which is why
 * [PanelVals.mode] suppresses the chrome here.
 *
 * ### Notifications land under the clock, which is where a phone puts them
 *
 * Not as a banner over the top: this screen is what a player sees when they pick the phone up
 * without unlocking it, and everything on it is already arranged around the clock. What is
 * standing here is exactly the quiet notifications nobody has swiped yet ([NotificationsModel]) —
 * *this is where a quiet notification is stored*, and the swipe is the only acknowledgment there
 * is. **Swipe left**, not up: the one other control on this screen is SLIDE TO OPEN, and a
 * vertical flick on a locked phone belongs to something else on every real one.
 *
 * ### The heavy dim is the ground going down, not the glyphs going faint
 *
 * On a dark-field panel the dim is `alpha` over the whole thing. Here it cannot be: the amber
 * field *is* the emitted light, and fading black ink over a bright ground would leave the room
 * exactly as lit as before while looking dimmer to the person holding it — a light change that
 * shows up in a screenshot and not in the house. So the ground itself drops to [NOTIFIED_DIM] and
 * the ink stays black, and the arriving notification is the one thing still at full amber.
 */
@Composable
fun LockScreen(vals: PanelVals) {
    val go = navigator()
    val actions = LocalActions.current
    val lamp = LocalLamp.current
    val arrival = Notifications.arrivals[vals.screen]
        ?.takeIf { it.presentation == Presentation.UnderTheClock }
    val heavy = arrival?.notification?.kind?.heavy == true
    val ink = Amber.Black
    Column(
        Modifier.fillMaxSize()
            // **THE GROUND IS THE LAMP, AND THE HOUSE SETS ITS LEVEL** (`Effect.LampSet`). Read
            // inside the draw lambda and never in composition: a level arriving invalidates draw
            // only, which is the same path a blackout takes, and rule 7 is that an extra frame there
            // is not a dropped frame but an un-anonymised revoke. `background()` would have made
            // every luminance change a recomposition of the one screen whose whole job is to emit.
            //
            // **`UNSET` means the house has said nothing** and the port's own full amber stands in,
            // which is [egressType]'s arrangement one surface over: the fallback is a fixture rather
            // than a second source of truth, and a lock screen that opened black on a bench would be
            // this module asserting a lamp the rules never set.
            //
            // A STEP AND NOT A FADE, on the screen where the fade would be most tempting: one value
            // per draw, with nothing animating between two of them.
            .drawBehind {
                val set = lamp.authorised.intValue
                val ground = if (set == LampState.UNSET) Amber.Bright else Color(set)
                drawRect(if (heavy) ground.copy(alpha = NOTIFIED_DIM) else ground)
            }
    ) {
        Mockup { LanternFace(arrival, heavy, ink, go, actions) }
    }
}

/**
 * The lantern's face, in mockup pixels (D-202): the inverted strip, the clock, what is standing
 * under it, and SLIDE TO OPEN at the foot. The ground behind is the lamp's, drawn by [LockScreen].
 */
@Composable
private fun LanternFace(
    arrival: Arrival?,
    heavy: Boolean,
    ink: Color,
    go: (ScreenId) -> Unit,
    actions: PanelActions,
) {
    Column(
        // The ground runs to the top edge; the content starts under the safe area.
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = LocalPanelInsets.current.top, bottom = 20.dp)
    ) {
        InvertedStatusRow("SOMEONE'S HOME")

        Words(
            "21:04", Modifier.fillMaxWidth().padding(top = 52.dp),
            size = 124, color = ink, lineHeight = 0.8, align = TextAlign.Center,
        )
        Words(
            "SATURDAY 16 AUGUST", Modifier.fillMaxWidth().padding(top = 12.dp),
            size = 26, color = ink, lineHeight = 1.0, tracking = 0.06, align = TextAlign.Center,
        )

        Column(
            Modifier.padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The arriving one first, above everything that was already standing, as the one
            // solid block: black, with its words in amber, on a ground the house may just have
            // darkened (D-118).
            arrival?.let {
                LockNotification(
                    it.notification, ink, filled = true,
                    onSwipeAway = { actions.dismissNotification() },
                )
            }
            // WHAT IS STANDING IS ASKED FOR, NOT DRAWN FROM MEMORY. These used to be two literal
            // sentences on this screen and nowhere else, which is how a message ends up on the
            // lock screen saying one thing and in Messages saying another.
            //
            // The sender line drops to the line value on the full lamp and stays black on a
            // darkened one, where #5A3D0E would vanish into the ground.
            for (notification in LocalNotifications.current.standing) {
                LockNotification(
                    notification, ink, filled = false,
                    sender = if (heavy) ink else Ramp.Faint,
                    onSwipeAway = { actions.dismissStanding(notification) },
                )
            }
        }

        Box(Modifier.weight(1f))

        Box(
            Modifier.fillMaxWidth().height(64.dp).border(2.dp, ink).tap { go(ScreenId.Home) },
            contentAlignment = Alignment.Center,
        ) {
            Words("SLIDE TO OPEN >>", size = 30, color = ink, lineHeight = 1.0, tracking = 0.06)
        }
    }
}

/**
 * One notification under the clock, and the swipe that takes it away.
 *
 * **Left, and only left.** It tracks the finger the whole way and springs back short of
 * [SWIPE_DISMISS], the same distance the banner uses, because the hand does not know which screen
 * it is on. Right is not a shorter left: this screen's other gesture is SLIDE TO OPEN, which goes
 * right, and a notification that answered both would be one flick away from unlocking the phone by
 * accident in a dark hallway.
 *
 * **There is no mark on it.** Not a dot, not a tag, not a weight — nothing here distinguishes one
 * of these from another by whether it has been looked at, because there is nothing anywhere in the
 * app that knows (D-105). [filled] is the difference between arriving and standing, which is a
 * fact about *now* rather than about the player.
 */
@Composable
private fun LockNotification(
    notification: Notification,
    ink: Color,
    filled: Boolean,
    onSwipeAway: () -> Unit,
    sender: Color = Ramp.Faint,
) {
    val threshold = with(LocalDensity.current) { SWIPE_DISMISS.toPx() }
    // Keyed on the notification: the one below it moving up into this slot must not inherit half
    // a swipe made at the one that left.
    var pushed by remember(notification) { mutableStateOf(0f) }
    val words = if (filled) Ramp.Ink else ink

    Column(
        Modifier.fillMaxWidth()
            // Read at draw time rather than at composition — a drag is sixty frames a second and
            // the whole app has an allocation budget of about 0.5 MB/s.
            .offset { IntOffset(pushed.toInt(), 0) }
            .then(if (filled) Modifier.background(ink) else Modifier.border(2.dp, ink))
            .pointerInput(notification) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (-pushed >= threshold) onSwipeAway() else pushed = 0f },
                    onDragCancel = { pushed = 0f },
                ) { _, delta -> pushed = (pushed + delta).coerceAtMost(0f) }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tag("${notification.from} . ${notification.at}", if (filled) Ramp.Mid else sender)
        Words(notification.body, size = 26, color = words, lineHeight = 1.05)
        notification.detail?.let { Words(it, size = 22, color = words, lineHeight = 1.1) }
    }
}
