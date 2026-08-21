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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        Label("BEFORE THE LIGHTS GO OUT", size = 7.0, color = Amber.BoneDim, tracking = 0.16)

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
            ReadoutField(
                value = lobby.line.text,
                onValueChange = actions.typeLine,
                modifier = Modifier.fillMaxWidth(),
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
fun ArmedScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.u),
        verticalArrangement = Arrangement.spacedBy(10.u, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(
            "PERIMETER\nARMED",
            size = 18.0, color = Amber.Bright, tracking = 0.14, lineHeight = 1.5,
            align = TextAlign.Center,
        )
        Box(Modifier.width(40.u).height(1.u).background(Amber.Dim))
        Label(
            "6 RESIDENTS\nACCOUNTED FOR",
            size = 7.0, color = Amber.Dim, tracking = 0.14, lineHeight = 2.0,
            align = TextAlign.Center,
        )
        Box(
            Modifier.padding(top = 12.u).border(1.u, Amber.Faint)
                .tap { go(ScreenId.Home) }
                .padding(horizontal = 20.u, vertical = 8.u)
        ) {
            Label("CONTINUE", size = 7.5, color = Amber.Dim, tracking = 0.18)
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
fun RevealScreen(vals: PanelVals) {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("MESSAGES", size = 7.0, color = Amber.Dim, tracking = 0.14)
            Label(
                "${vals.inbox.size} MESSAGES",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Faint, tracking = 0.14, align = TextAlign.End,
            )
        }
        Label(
            "DELIVERED WHEN SIGNAL RETURNED . 21:00",
            size = 5.5, color = Amber.Faint, tracking = 0.08,
        )

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.u),
        ) {
            vals.inbox.forEach { row ->
                Column(
                    Modifier.fillMaxWidth().border(1.u, row.edge)
                        .tap { go(ScreenId.RevealThread) }
                        .padding(horizontal = 7.u, vertical = 5.u),
                    verticalArrangement = Arrangement.spacedBy(2.u),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label(row.from, size = 6.5, color = row.fromInk, tracking = 0.1)
                        Label(row.at, size = 6.5, color = Amber.Faint, tracking = 0.1)
                    }
                    Label(
                        row.preview,
                        size = 7.0, color = row.ink, lineHeight = 1.5,
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
 * Resident who glimpsed a neighbour's screen would see the same two messages they have.
 *
 * The house never addresses a Resident directly. It is intimate only with the people it owns.
 */
@Composable
fun RevealThreadScreen(vals: PanelVals) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Reveal, Amber.Dim)
            Label("HOUSE", size = 7.0, color = Amber.Dim, tracking = 0.14)
            Label(
                "3 MESSAGES",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Faint, tracking = 0.14, align = TextAlign.End,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.u)) {
            ThreadMessage("20:58", "Occupancy nominal. Access reviewed and retained.")
            ThreadMessage(
                "20:59",
                "Seven subroutines assigned. Begin at your convenience.",
            )
            // THE STAMP SAID "21:02 . NEW" AND THE TAG HAD TO GO (D-105). It is the design's
            // own wording and it is an unread badge in the plainest form there is — on the one
            // message in the game whose text differs by role, which is where a mark drawing the
            // eye is least wanted. Nothing is lost: the message is last in the thread, it is the
            // only one drawn at full intensity, and the time is right there. What the tag added
            // was the claim that the phone knows what this player has and has not looked at, and
            // there is nothing anywhere in the app that knows that.
            ThreadMessage(
                "21:02", vals.houseLine,
                border = Amber.Bright, stampInk = Amber.Dim, bodyInk = Amber.Bright,
            )
        }

        ReplyUnavailable()
    }
}

@Composable
private fun ThreadMessage(
    stamp: String,
    body: String,
    border: Color = Amber.Edge,
    stampInk: Color = Amber.Faint,
    bodyInk: Color = Amber.Dim,
) {
    Column(Modifier.fillMaxWidth().border(1.u, border).padding(6.u)) {
        Label(
            stamp,
            modifier = Modifier.padding(bottom = 3.u),
            size = 6.0, color = stampInk, tracking = 0.14,
        )
        Label(body, size = 8.0, color = bodyInk, lineHeight = 1.6)
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
        Modifier.fillMaxWidth().dashedBorder(Amber.Edge).padding(5.u),
        contentAlignment = Alignment.Center,
    ) {
        Label(
            "REPLY UNAVAILABLE . NO PERMISSION",
            size = 6.0, color = Amber.Faint, tracking = 0.1, align = TextAlign.Center,
        )
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
    val arrival = Notifications.arrivals[vals.screen]
        ?.takeIf { it.presentation == Presentation.UnderTheClock }
    val heavy = arrival?.notification?.kind?.heavy == true
    val ink = Amber.Black
    Column(
        Modifier.fillMaxSize()
            // A STEP AND NOT A FADE, on a screen where the fade would be the most tempting: this
            // is one colour value chosen per composition, with nothing animating between the two.
            .background(if (heavy) Amber.Bright.copy(alpha = NOTIFIED_DIM) else Amber.Bright)
            .padding(start = 10.u, end = 10.u, top = 10.u, bottom = 9.u)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(7.u),
                horizontalArrangement = Arrangement.spacedBy(1.u),
                verticalAlignment = Alignment.Bottom,
            ) {
                val spent = ink.copy(alpha = 0.25f)
                Block(2.u, 3.u, spent)
                Block(2.u, 5.u, spent)
                Block(2.u, 7.u, spent)
            }
            Column(
                Modifier.width(7.u),
                verticalArrangement = Arrangement.spacedBy(1.u),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Block(7.u, 1.u, ink)
                Block(5.u, 1.u, ink)
                Block(1.u, 1.u, ink)
            }
            Label(
                "SOMEONE'S HOME",
                modifier = Modifier.weight(1f),
                size = 6.5, color = ink, tracking = 0.1,
            )
            Box(
                Modifier.size(7.u).border(1.u, ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(3.u).background(ink)) }
            Row(Modifier.width(11.u).height(6.u).border(1.u, ink.copy(alpha = 0.55f)).padding(1.u)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(ink))
                Box(Modifier.weight(1f).fillMaxHeight().background(ink))
                Box(Modifier.weight(1f).fillMaxHeight())
            }
        }

        Readout(
            "21:04",
            modifier = Modifier.fillMaxWidth().padding(top = 10.u),
            size = 58.0, color = ink, tracking = 0.02, lineHeight = 1.0,
            align = TextAlign.Center,
        )
        Label(
            "SATURDAY 16 AUGUST",
            modifier = Modifier.fillMaxWidth().padding(top = 1.u),
            size = 6.5, color = ink, tracking = 0.2, align = TextAlign.Center,
        )

        Column(
            Modifier.padding(top = 12.u),
            verticalArrangement = Arrangement.spacedBy(5.u),
        ) {
            // The arriving one first, above everything that was already standing, and drawn as a
            // filled block rather than an outline: on a field the house has just darkened it is
            // the only thing left at full amber, which is the whole of what "the rest of the
            // screen dims around it" means.
            arrival?.let {
                LockNotification(
                    it.notification, ink, filled = true,
                    onSwipeAway = { actions.dismissNotification() },
                )
            }
            // WHAT IS STANDING IS ASKED FOR, NOT DRAWN FROM MEMORY. These used to be two literal
            // sentences on this screen and nowhere else, which is how a message ends up on the
            // lock screen saying one thing and in Messages saying another.
            for (notification in LocalNotifications.current.standing) {
                LockNotification(
                    notification, ink, filled = false,
                    onSwipeAway = { actions.dismissStanding(notification) },
                )
            }
        }

        Box(Modifier.weight(1f))

        Box(
            Modifier.fillMaxWidth().border(1.u, ink)
                .tap { go(ScreenId.Home) }
                .padding(vertical = 10.u),
            contentAlignment = Alignment.Center,
        ) {
            Label("SLIDE TO OPEN >>", size = 8.0, color = ink, tracking = 0.2)
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
) {
    val threshold = with(LocalDensity.current) { SWIPE_DISMISS.toPx() }
    // Keyed on the notification: the one below it moving up into this slot must not inherit half
    // a swipe made at the one that left.
    var pushed by remember(notification) { mutableStateOf(0f) }

    Column(
        Modifier.fillMaxWidth()
            // Read at draw time rather than at composition — a drag is sixty frames a second and
            // the whole app has an allocation budget of about 0.5 MB/s.
            .offset { IntOffset(pushed.toInt(), 0) }
            .then(if (filled) Modifier.background(Amber.Bright) else Modifier)
            .border(1.u, ink)
            .pointerInput(notification) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (-pushed >= threshold) onSwipeAway() else pushed = 0f },
                    onDragCancel = { pushed = 0f },
                ) { _, delta -> pushed = (pushed + delta).coerceAtMost(0f) }
            }
            .padding(horizontal = 7.u, vertical = 5.u)
    ) {
        Label(
            "${notification.from} . ${notification.at}",
            size = 6.0, color = ink, tracking = 0.14,
        )
        Label(
            notification.body,
            modifier = Modifier.padding(top = 3.u),
            size = 8.0, color = ink, lineHeight = 1.5,
        )
        notification.detail?.let {
            Label(it, modifier = Modifier.padding(top = 2.u), size = 6.5, color = ink, tracking = 0.08)
        }
    }
}
