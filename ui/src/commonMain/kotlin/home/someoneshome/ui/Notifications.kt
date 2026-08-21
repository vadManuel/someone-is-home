package home.someoneshome.ui

import androidx.compose.ui.unit.Dp

/**
 * **Everything that pops, and the one gesture that takes it away (D-105).**
 *
 * The read concept is deleted. There is no unopened count, no NEW tag, no mark that a message has
 * been looked at — anywhere, on any surface. What exists instead is a *notification*: something
 * arrives over whatever you were doing, the panel behind it drops to [NOTIFIED_DIM], and **swipe
 * up dismisses it. That is the whole gesture vocabulary.**
 *
 * ### The interesting question is not what a notification looks like — it is what survives it
 *
 * A notification is a moment. What matters afterwards is whether the *thing it was about* still
 * exists somewhere the player can go back to, and the three kinds answer that differently:
 * Messages holds every text; the Egress widget holds the countdown; **a house notice is held
 * nowhere at all.** That is [NotificationKind.heldBy], and it is a field rather than a paragraph
 * because a paragraph cannot be tested and this is.
 *
 * ### This is `:ui` data and it is a fixture
 *
 * Nothing here decides a game question and nothing here may. In play the house sends a
 * notification to **everybody at once** — the dim is world-observable light, so a banner addressed
 * to fewer than all players would be a beacon pointing at whoever got it (see [PanelFrame]) — and
 * these three are what the port draws while no house is attached. They carry no role, and the
 * `when` that picks one reads only the screen.
 */
enum class NotificationKind(
    /**
     * **Where the thing survives once the banner is gone, or `null` for nowhere.**
     *
     * `null` is the whole point of the type, not a missing value: a house notice is shown once and
     * stored nowhere, which is why the enum has to be able to say so out loud. `NotificationsTest`
     * renders every screen in the game and checks each kind against this — that the two persisting
     * ones really are on the screen they name, and that the notice's words are on exactly one
     * screen and no other.
     */
    val heldBy: ScreenId?,
) {

    /** A text from the house. **Messages holds every text**, so the thread is still there. */
    Text(heldBy = ScreenId.Reveal),

    /** The Egress alert. **The widget holds the countdown**, which is the part still running. */
    Egress(heldBy = ScreenId.EgressWidget),

    /**
     * A house notice — the administrative register admitting what the system could not see
     * (D-035). **The popup from a phantom app: shown once, held nowhere.**
     *
     * Nothing is lost by that. A notice is read aloud into a room where everyone got the same one
     * at the same moment, and it is *about* the meeting it arrives at; a phone that could produce
     * it again an hour later would be a phone with a better memory of the evening than the people
     * arguing in it.
     */
    Notice(heldBy = null),
}

/**
 * One notification, as the panel draws it.
 *
 * Flat and immutable, and there is deliberately **nowhere on it to record that it was seen**. Not
 * a `dismissed` flag, not a timestamp, not a per-player mark: a field like that is how the read
 * concept comes back, and it comes back as a badge on a tile three months later.
 */
data class Notification(
    val kind: NotificationKind,
    /** The line the player reads. */
    val body: String,
    /**
     * The app it appears to have come from. HOUSE on all three — the phantom OS has one sender,
     * and a second name would be a second thing to reason about in the dark.
     */
    val from: String = "HOUSE",
    /** When, as the banner prints it. */
    val at: String = "NOW",
    /** A second, smaller line under the body, or none. */
    val detail: String? = null,
    /**
     * How large [body] is set on a banner.
     *
     * A headline and a sentence want different sizes and the design sets them differently; this
     * is that difference, and nothing else.
     */
    val bodySize: Double = 9.0,
    /**
     * Where a tap opens it, or `null` when there is nothing to open.
     *
     * A different `null` from [NotificationKind.heldBy]'s: this one says the notification is not a
     * door, that one says nothing is behind the door. A notice is both, which is a coincidence
     * rather than a rule — an alert that opened a screen but left nothing behind would be
     * perfectly expressible here.
     */
    val opens: ScreenId? = null,
)

/**
 * The three the port draws, and the one place each one's words are written down.
 *
 * **Single-sourced on purpose.** Every one of these appears twice — on the banner and on the
 * surface that holds it afterwards — and two copies of a sentence agree on the day they are
 * written and never again. The banner naming one pair of rooms while the widget named another
 * would send two people who may not speak to two different places.
 */
object Notifications {

    /**
     * **The two nodes containment needs, written once.**
     *
     * Egress needs two people at two separate markers and nobody may speak, so the device saying
     * *where* is the only coordination available. The banner names them and so does the widget it
     * leaves behind; a test holds them to the same pair.
     */
    val EGRESS_NODES: List<String> = listOf("UTILITY", "LANDING")

    /**
     * The house's text, arriving over page 1. Everyone gets one, at the same moment.
     *
     * [body] is also the preview on the Messages row, because it is the same message — see
     * [PanelVals.inbox]. Identical sender, time and preview for both roles: the thread must be
     * OPENED to read, and only its newest line differs.
     */
    val text: Notification = Notification(
        kind = NotificationKind.Text,
        body = "Regarding this evening. Please read.",
        bodySize = 8.0,
        opens = ScreenId.Reveal,
    )

    /** The Egress alert. Names both nodes, because nobody may speak. */
    val egress: Notification = Notification(
        kind = NotificationKind.Egress,
        body = "EGRESS ATTEMPT IN PROGRESS",
        detail = "CONTAIN AT ${EGRESS_NODES.joinToString(" AND ")}",
        opens = ScreenId.EgressWidget,
    )

    /**
     * The house notice (D-035): the system admitting, badly, what it could not see.
     *
     * **Resident, not occupant.** The design's own wording says occupant; the vocabulary is
     * closed and this is the word the rest of the app uses for a player.
     *
     * Its surface today is the meeting's own notices screen rather than a banner, which is what
     * the design drew — see the worklog. What is settled and held here is the part D-105 settled:
     * these words live in exactly one place and survive nowhere.
     */
    val notice: Notification = Notification(
        kind = NotificationKind.Notice,
        body = "Resident MARCUS was unreachable 21:04–21:07. Occupancy data for this interval " +
            "is incomplete.",
        bodySize = 8.5,
    )

    /** All three, for the tests that check a property of every kind at once. */
    val all: List<Notification> = listOf(text, egress, notice)

    /**
     * **What is up over [screen], if anything.**
     *
     * The port expresses "a notification is showing" as a screen id: `Notify` and `Banner` are the
     * springboard with something on top of it, which is why both draw [HomeScreen] underneath. In
     * play the house pushes a notification and the screen underneath is wherever the player was —
     * so this `when` is the fixture's stand-in for a message that has not been built yet, and the
     * loop is frozen, so it stays a fixture.
     *
     * Returning non-null is also what dims the panel: [Screen] hands the result to [PanelFrame],
     * so "a notification is showing" and "the panel is dimmed" cannot drift apart.
     */
    fun onScreen(screen: ScreenId): Notification? = when (screen) {
        ScreenId.Notify -> text
        ScreenId.Banner -> egress
        else -> null
    }
}

/**
 * **How far up the banner has to be pushed before it goes.**
 *
 * Longer than a brush and shorter than a deliberate throw. The whole game is played in the dark
 * with one thumb, and both failure directions are real: a dismiss that fires on contact throws
 * away the one thing the house said, and a dismiss that needs a full swipe is a player standing
 * in a dark room fighting their phone.
 *
 * The banner moves with the finger the whole way — echo of your own input, which is allowed — and
 * springs back if the finger lifts short of this.
 */
val SWIPE_DISMISS: Dp = 14.u
