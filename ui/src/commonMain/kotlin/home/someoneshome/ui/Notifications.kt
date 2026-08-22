package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * **Everything that pops, and what it costs the house (D-105, D-118, D-119).**
 *
 * The read concept is deleted. There is no unopened count, no NEW tag, no mark that a message has
 * been looked at — anywhere, on any surface. What exists instead is a *notification*, and three
 * separate rulings decide what one does:
 *
 * ### 1. The dim is a two-member vocabulary, not a notification style (D-118)
 *
 * **Exactly two events dim the house**: the [Egress][NotificationKind.Egress] and the house's
 * [opening text message][NotificationKind.Opening]. Those two arrive with a bright banner and the
 * screen dropping to [NOTIFIED_DIM]. **Every other notification is quiet** — no dim, no brightness
 * spike, nothing world-observable at all, which is why quiet banners are drawn dark and do not
 * buzz (see [PanelVals.BUZZING]).
 *
 * That is the whole reason [NotificationKind.heavy] exists as a field. A light change that means
 * one specific thing is a signal; a light change that happens twenty times a round is noise, paid
 * for out of the readability the entire game is built on. An Egress is audible anyway, and a dim
 * during the opening message is the round starting — the house already knows about both.
 *
 * ### 2. What survives the banner (D-105)
 *
 * A notification is a moment. What matters afterwards is whether the *thing it was about* still
 * exists somewhere the player can go back to: Messages holds every text, the Egress widget holds
 * the countdown, the Subroutines list holds an unblocked Subroutine, and **a house notice is held
 * nowhere at all.** That is [NotificationKind.heldBy], a field rather than a paragraph because a
 * paragraph cannot be tested and this is.
 *
 * ### 3. What is still on the lock screen (D-119)
 *
 * **Quiet notifications are stored**; they sit under the clock until they are swiped, and the
 * swipe is the acknowledgment. The two heavy ones are not stored — they clear themselves after
 * [HEAVY_HOLD] and the thing they were about is in its own home — and a house notice at a meeting
 * is stored nowhere at all. That is [NotificationKind.stored], and [NotificationsModel] is the
 * list it produces.
 *
 * ### This is `:ui` data and it is a fixture
 *
 * Nothing here decides a game question and nothing here may. In play the house sends a heavy
 * notification to **everybody at once** — the dim is world-observable light, so a dimming banner
 * addressed to fewer than all players would be a beacon pointing at whoever got it (see
 * [PanelFrame]) — and these five are what the port draws while no house is attached. They carry no
 * role, and the map that picks one reads only the screen.
 */
enum class NotificationKind(
    /**
     * **Whether this is one of the two that dim the house (D-118).**
     *
     * Exactly two are true and the design named both. Heavy means: a bright banner, the panel
     * behind it at [NOTIFIED_DIM], a buzz, and a ten-second life whether anybody touches it or
     * not. Quiet means none of those — the banner is dark, the panel is untouched, and it waits
     * for a finger however long that takes.
     */
    val heavy: Boolean,
    /**
     * **Whether it stands on the lock screen until it is swiped (D-119).**
     *
     * The lock screen under the clock is where a quiet notification lives, because a player
     * walking back from a marker has to be able to find the thing again and a quiet banner they
     * were not holding the phone for is a thing they never saw. **The swipe is the only
     * acknowledgment there is** — D-105 deleted read state, so a stored notification that cleared
     * itself would leave nothing behind at all.
     */
    val stored: Boolean,
    /**
     * **Where the thing survives once the banner is gone, or `null` for nowhere.**
     *
     * `null` is the whole point of the type, not a missing value: a house notice is shown once and
     * stored nowhere, which is why the enum has to be able to say so out loud. It is also where a
     * tap on the banner goes — see [Notification.opens], and the invariant in `FlowTest` that ties
     * the two together.
     */
    val heldBy: ScreenId?,
) {

    /**
     * The house's opening text message. **Heavy**: this one is the round starting, so the whole
     * house dimming at the same moment says exactly what it is. **Messages holds every text.**
     */
    Opening(heavy = true, stored = false, heldBy = ScreenId.Reveal),

    /**
     * The Egress alert. **Heavy**, and the cheapest of the two to justify: an Egress is audible
     * across the house before anybody looks at a phone. **The widget holds the countdown**, which
     * is the part still running, and an Egress is never a stored notification.
     */
    Egress(heavy = true, stored = false, heldBy = ScreenId.EgressWidget),

    /**
     * Any later text from the house. **Quiet** — the house talking is not the round turning over,
     * and a second dim would spend the first one's meaning. Messages holds it, and it stands on
     * the lock screen until swiped.
     */
    Text(heavy = false, stored = true, heldBy = ScreenId.Reveal),

    /**
     * A Subroutine that was WAITING UPSTREAM and is not any more — D-119's own example of the kind
     * of thing a quiet notification is for. **Quiet and stored**: it is a fact about the world
     * that has to still be there when the player next looks at the phone, and the Subroutines list
     * is where the fact itself lives.
     */
    Unblocked(heavy = false, stored = true, heldBy = ScreenId.Work),

    /**
     * A house notice — the administrative register admitting what the system could not see
     * (D-035). **Quiet, and the one thing shown once and held nowhere.**
     *
     * Nothing is lost by that. A notice is read aloud into a room where everyone got the same one
     * at the same moment, and it is *about* the meeting it arrives at; a phone that could produce
     * it again an hour later would be a phone with a better memory of the evening than the people
     * arguing in it.
     */
    Notice(heavy = false, stored = false, heldBy = null),
}

/**
 * One notification, as the panel draws it.
 *
 * Flat and immutable, and there is deliberately **nowhere on it to record that it was seen**. Not
 * a `dismissed` flag, not a timestamp, not a per-player mark: a field like that is how the read
 * concept comes back, and it comes back as a badge on a tile three months later. `NotificationsTest`
 * reads the fields off this class and fails on a sixth one, because the argument above is exactly
 * the argument that will sound weak to whoever is adding the sixth one.
 */
data class Notification(
    val kind: NotificationKind,
    /** The line the player reads. */
    val body: String,
    /**
     * The app it appears to have come from — HOUSE, or the withheld number the design gives the
     * system when it is talking about work rather than about the evening.
     */
    val from: String = "HOUSE",
    /**
     * When, as the banner and the lock screen both print it.
     *
     * NOW on the two heavy ones, because a heavy notification is only ever read while it is
     * arriving. A stored one carries the time it came in: it can sit under the clock for twenty
     * minutes, and NOW would be a lie by then.
     */
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
 * **How a notification is presented, which is also which way it is swiped away.**
 *
 * Two presentations, because the phone has two: over whatever you were doing, and on the lock
 * screen. They are not styling variants of each other — the gesture differs, the dim differs, and
 * on the lock screen the notification is the one bright thing on a darkened field rather than a
 * bright block on a darkened panel.
 */
enum class Presentation {
    /** A banner over the panel. **Swipe up.** */
    Banner,

    /**
     * Under the large clock, the phone's own idiom (D-118). **Swipe left.**
     *
     * A different direction from the banner's on purpose: the lock screen's own control is SLIDE
     * TO OPEN, which is a horizontal gesture across the bottom of the screen, and a vertical flick
     * on a locked phone is what everybody's real phone uses for something else entirely.
     */
    UnderTheClock,
}

/**
 * **A notification arriving, and what is underneath it.**
 *
 * [under] is where a dismissal leaves the player, and it is the arrival's fact rather than the
 * banner's: the banner does not know what it is drawn on top of. In the port every in-game banner
 * arrives over the springboard and the lock screen's arrives over the lock screen.
 */
data class Arrival(
    val notification: Notification,
    val under: ScreenId,
    val presentation: Presentation = Presentation.Banner,
)

/**
 * The five the port draws, and the one place each one's words are written down.
 *
 * **Single-sourced on purpose.** Most of these appear twice — on the banner and on the surface
 * that holds it afterwards — and two copies of a sentence agree on the day they are written and
 * never again. The banner naming one pair of rooms while the widget named another would send two
 * people who may not speak to two different places.
 */
object Notifications {

    /**
     * **The two nodes containment needs, as the port drew them.**
     *
     * Egress needs two people at two separate markers and nobody may speak, so the device saying
     * *where* is the only coordination available.
     *
     * **This is the fallback, not the source.** In play the house sends the pair it drew at fire
     * time — two ordinary markers in non-adjacent rooms, different every Egress — and it arrives on
     * `PanelState.egressNodes`. These two rooms are what a phone with no house attached draws, the
     * same way `PanelState.secondsLeft` falls back to the design's drawn moment. Both surfaces read
     * the *same* value whichever it is, which is the property that matters: two copies of the pair
     * would send two people who may not speak to two different places.
     */
    val EGRESS_NODES: List<String> = listOf("UTILITY", "LANDING")

    /** The type the port drew. The house picks Beacon or Tether at fire time; this is the stand-in. */
    const val EGRESS_TYPE: String = "BEACON"

    /**
     * The house's opening text, arriving over page 1. Everyone gets one, at the same moment, and
     * the whole house dims for it.
     *
     * [body] is also the preview on the Messages row, because it is the same message — see
     * [PanelVals.inbox]. Identical sender, time and preview for both roles: the thread must be
     * OPENED to read, and only its newest line differs.
     */
    val opening: Notification = Notification(
        kind = NotificationKind.Opening,
        body = "Regarding this evening. Please read.",
        bodySize = 8.0,
        opens = ScreenId.Reveal,
    )

    /**
     * **The Egress alert, built from the pair the house drew.**
     *
     * A function rather than a value, because the pair is not a constant any more: it is chosen at
     * fire time and lands on `PanelState`. This is the *only* place the alert's words are written,
     * and [EgressWidgetScreen]'s widget draws its rooms from the same `PanelVals` field — so the
     * banner and the surface that holds it after a swipe cannot name different places.
     */
    fun egressFor(type: String, nodes: List<String>): Notification = Notification(
        kind = NotificationKind.Egress,
        body = "EGRESS ATTEMPT IN PROGRESS",
        detail = "CONTAIN AT ${nodes.joinToString(" AND ")}",
        opens = ScreenId.EgressWidget,
    )

    /** The alert as the port drew it, for [all], [arrivals] and every test that wants one. */
    val egress: Notification = egressFor(EGRESS_TYPE, EGRESS_NODES)

    /**
     * A later text from the house, standing on the lock screen. The design's own second lock
     * message, and the reason it is a notification rather than a drawn line: it is in Messages
     * too, and two copies of a sentence drift.
     */
    val text: Notification = Notification(
        kind = NotificationKind.Text,
        body = "Do not attempt the exterior doors.",
        at = "21:01",
        bodySize = 8.0,
        opens = ScreenId.Reveal,
    )

    /**
     * A Subroutine coming unblocked — the design's first lock message, and D-119's example of the
     * kind quiet storage exists for.
     *
     * **NUMBER WITHHELD, not HOUSE.** The design gives the system a second sender for the messages
     * that are about work rather than about the evening, and it is the better of the two names for
     * a phone in a dark room: the house talks to you, the work just appears.
     */
    val unblocked: Notification = Notification(
        kind = NotificationKind.Unblocked,
        body = "A subroutine is available to you.",
        from = "NUMBER WITHHELD",
        at = "21:03",
        bodySize = 8.0,
        opens = ScreenId.Work,
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

    /** All five, for the tests that check a property of every kind at once. */
    val all: List<Notification> = listOf(opening, egress, text, unblocked, notice)

    /**
     * **What is standing on the lock screen when the round starts, newest first.**
     *
     * Asked of [NotificationKind.stored] rather than listed, so a kind cannot be given the storage
     * ruling and then quietly left off the only screen that implements it.
     */
    val stored: List<Notification> = all.filter { it.kind.stored }.sortedByDescending { it.at }

    /**
     * **What is up over each screen, and what is underneath it.**
     *
     * The port expresses "a notification is showing" as a screen id: `Notify`, `Banner` and
     * `Quiet` are the springboard with something on top of it, and `LockNotify` is the lock screen
     * with something on top of it — which is why each draws the same screen [under] it. In play
     * the house pushes a notification and the screen underneath is wherever the player was, so
     * this map is the fixture's stand-in for a message that has not been built yet. The loop is
     * frozen, so it stays a fixture.
     */
    val arrivals: Map<ScreenId, Arrival> = mapOf(
        ScreenId.Notify to Arrival(opening, under = ScreenId.Home),
        ScreenId.Banner to Arrival(egress, under = ScreenId.Home),
        ScreenId.Quiet to Arrival(unblocked, under = ScreenId.Home),
        ScreenId.LockNotify to Arrival(
            opening, under = ScreenId.Lock, presentation = Presentation.UnderTheClock,
        ),
    )

    /** What is up over [screen], if anything. */
    fun onScreen(screen: ScreenId): Notification? = arrivals[screen]?.notification

    /**
     * **What is up over [screen], with the Egress alert naming the pair the house actually drew.**
     *
     * [arrivals] is a static table and has to be: it is the port's fixture for a push that does not
     * exist yet. But the Egress alert is the one entry in it whose *words* are round data, so this
     * rebuilds that one and passes every other through untouched.
     *
     * **Every drawing site goes through here rather than through [arrivals] directly**, which is
     * what makes *the alert and the widget cannot disagree* a property of the code rather than a
     * habit two composables keep.
     */
    fun arrivalOn(screen: ScreenId, type: String, nodes: List<String>): Arrival? {
        val arrival = arrivals[screen] ?: return null
        if (arrival.notification.kind != NotificationKind.Egress) return arrival
        return arrival.copy(notification = egressFor(type, nodes))
    }

    /**
     * **Whether the house is dimmed while [screen] is showing (D-118).**
     *
     * The one place the dim is decided, so that "a banner is up" and "the light dropped" are
     * *deliberately* different questions with one answer each. They used to be the same question,
     * which is the reading D-118 corrected.
     */
    fun dims(screen: ScreenId): Boolean = onScreen(screen)?.kind?.heavy == true
}

/**
 * **How long a heavy banner lasts if nobody touches it (D-119).**
 *
 * Ten seconds, and it is the house's number rather than a taste call: the two heavy notifications
 * dim every phone in the building, so the light has to come back on its own for the player who is
 * holding their phone as a lamp, facing away, with both hands busy at a marker. Undim happens on
 * personal dismissal or at this expiry, whichever comes first.
 *
 * **Quiet notifications have no equivalent and must never grow one.** They sit until swiped —
 * that is the whole of what D-105 left in place of read state.
 */
const val HEAVY_HOLD: Int = 10_000

/**
 * **How far a notification has to be pushed before it goes.**
 *
 * Longer than a brush and shorter than a deliberate throw. The whole game is played in the dark
 * with one thumb, and both failure directions are real: a dismiss that fires on contact throws
 * away the one thing the house said, and a dismiss that needs a full swipe is a player standing
 * in a dark room fighting their phone.
 *
 * One distance for both directions — up on a banner, left on the lock screen. Two numbers would be
 * two things to learn in the dark, and the hand does not know which screen it is on.
 *
 * The notification moves with the finger the whole way — echo of your own input, which is allowed
 * — and springs back if the finger lifts short of this.
 */
val SWIPE_DISMISS: Dp = 14.u

/**
 * **What is still on the lock screen: the quiet notifications nobody has swiped yet.**
 *
 * The narrowest model in the module, and narrow on purpose. It can say what is standing and it can
 * remove one. **It cannot say whether anything was ever here**, when it arrived, whether it was
 * looked at, or how many of them there have been — there is no field for any of that and no method
 * that would answer it, which is what D-105 means by the read concept being *deleted* rather than
 * unused. `NotificationsTest` reads this class's public surface and fails on a third member.
 *
 * Held beside [PanelState] rather than in it, like every other model in this module: the panel
 * stays flat and inert, and in play the house pushes what is standing.
 */
class NotificationsModel(standing: List<Notification> = Notifications.stored) {

    /** What has arrived and not yet been swiped away, newest first. */
    var standing: List<Notification> by mutableStateOf(standing)
        private set

    /**
     * **Swiped away, and that is the end of it.**
     *
     * Removed rather than marked. A marked-as-dismissed notification is a read flag with a
     * different name on it, and it is one product decision away from being a dot on a tile.
     */
    fun dismiss(notification: Notification) {
        standing = standing - notification
    }
}

/**
 * Provided by [Screen] beside [LocalMeeting] and for the same reason: a test that swipes a
 * notification off the lock screen must not leave the next render looking at a phone with one
 * fewer notification on it than it expected.
 */
val LocalNotifications: ProvidableCompositionLocal<NotificationsModel> =
    staticCompositionLocalOf { NotificationsModel() }
