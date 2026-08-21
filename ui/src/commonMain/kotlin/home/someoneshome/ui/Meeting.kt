package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **What this phone said at the meeting — and nothing about what the room decided.**
 *
 * Sits beside [PanelState] with [HomeEditorModel], [SavedHomesModel] and [LobbyModel], for the
 * reason they do: `PanelState` is flat, inert, and every field of it is already decided at the
 * effect boundary. A finger landing on a name is none of those things.
 *
 * ### Everything here is echo, and echo is the only optimism this app has
 *
 * Lighting the row you just tapped reflects *your own input* — it is the one thing the
 * architecture permits a screen to do without asking (project rule: "input echo is not game
 * logic"; D-097: "the only optimism the design permits is input echo"). Everything past that is
 * the house's: whether your vote was received, how many others have voted, who the room
 * restrained, and when any of it happens.
 *
 * So this class can say **"you tapped MARCUS"** and can say nothing else. It cannot tally, it
 * cannot tell you whether a vote landed, and — the part that would be quietest and worst — it
 * holds no attribution. Who voted for whom is a thing only a player outside the system ever
 * learns (D-075), and the type a living phone holds is physically incapable of carrying it. That
 * separation is [OutsideView]'s whole reason for being a different type in the same file.
 */
class MeetingModel(
    /**
     * The house's counts, as they stood when the screen was drawn.
     *
     * Every one of these arrives from the authority in play: how many phones answered the ring,
     * how many are standing at the meeting area, how many have said they are ready, how many have
     * voted. **Nothing on this phone moves them** — see [checkIn].
     */
    val counts: MeetingCounts = MeetingCounts(),
    /**
     * The names this phone may vote for.
     *
     * A fixture today, and in play it is what the house sent: the living, minus you. It is a list
     * of who *can* be restrained and says nothing about who should be.
     */
    val names: List<String> = emptyList(),
) {

    /**
     * **This phone's own check-in, and it moves nothing else.**
     *
     * D-104: the talk does not start until every living player *and* every out player has checked
     * in at the meeting area. That gate is the house's, because closing it means counting phones,
     * and one phone cannot count phones. So pressing I AM HERE lights your own tick and the screen
     * goes on waiting — which is exactly what the player is doing.
     *
     * The count beside it — *4 OF 6 CHECKED IN* — does **not** move when you press it. It is the
     * house's number and it changes when the house says so. A client that helpfully incremented it
     * would be a phone claiming the gate is one closer to closing on the strength of its own
     * button press, and it would be wrong for as long as the network took to disagree.
     */
    var checkedIn: Boolean by mutableStateOf(false)
        private set

    /**
     * **READY TO VOTE, and it moves nothing either.**
     *
     * The talk is ninety seconds and *unanimous* READY skips ahead — unanimous across the room,
     * which is again a count of phones and again not one phone's to take. So this is your own
     * hand up, and the discussion ends when the house ends it.
     */
    var ready: Boolean by mutableStateOf(false)
        private set

    /**
     * The name this phone has its finger on, or none.
     *
     * **Changeable until the clock ends**, which the design says outright — so nothing here ever
     * freezes it, including [lockIn]. And *not voting counts as a Skip* (D-075), so there is no
     * un-vote to draw: the two things a player can express are *this one* and *nobody*, and both
     * are rows on the screen.
     */
    var choice: VoteChoice? by mutableStateOf(null)
        private set

    /**
     * What was last handed to the house, which is not the same as what is selected.
     *
     * The design's vote screen shows *how many have voted, never what* — so having voted is a real
     * state, distinct from having a finger on a row, and LOCK IN is the step between them. Changing
     * the selection afterwards leaves this holding the old one until it is locked in again, which
     * is why [locked] compares the two rather than being a flag of its own: a flag would say LOCKED
     * IN over a row the house has never heard of.
     *
     * **It is an echo of what this phone sent, not of what the house received.** The count on the
     * screen — *4 OF 6 VOTED* — is the house's and does not move when you press the button.
     */
    var handedOver: VoteChoice? by mutableStateOf(null)
        private set

    /** I AM HERE. */
    fun checkIn() {
        checkedIn = true
    }

    /** READY TO VOTE. */
    fun sayReady() {
        ready = true
    }

    /** A tap on a name, or on SKIP. Re-tapping what is already held changes nothing. */
    fun choose(next: VoteChoice) {
        choice = next
    }

    /**
     * LOCK IN: hand the selection to the house.
     *
     * **Refused when nothing is selected**, rather than quietly sending nothing. Not voting is
     * already a Skip and SKIP is already a row — a button that handed over an empty vote would be
     * a third way to say the same thing, and the only one of the three with nothing on screen to
     * show for it.
     */
    fun lockIn() {
        if (choice != null) handedOver = choice
    }

    /** Whether [name]'s row is the one holding this phone's vote. */
    fun holds(name: String): Boolean = choice == VoteChoice.Named(name)

    /** Whether SKIP is the one holding it. */
    val skipping: Boolean get() = choice == VoteChoice.Skip

    /** True while the house has been handed exactly what is on the screen. */
    val locked: Boolean get() = choice != null && choice == handedOver

    /**
     * **A new meeting starts with nothing said.**
     *
     * A round has several meetings and this model outlives all of them, so a check-in made at the
     * first one would still be lit at the second — a phone telling a player they are already
     * standing at the meeting area they have just been called to.
     *
     * Called on arrival at one of [STARTS] rather than on departure from the last screen, because
     * the meeting has four ways in and only the house knows which of them ended.
     */
    fun meetingBegan() {
        checkedIn = false
        ready = false
        choice = null
        handedOver = null
    }

    companion object {

        /**
         * The four ways a meeting begins: you called it, somebody called it, somebody found a
         * revoked player, or you are out and have been told to walk in.
         */
        val STARTS: Set<ScreenId> = setOf(
            ScreenId.Calling, ScreenId.Call, ScreenId.Found, ScreenId.Ghost2,
        )

        /**
         * **The fixture: the design's own meeting, mid-vote.**
         *
         * Every render and every rendering test gets this, for the reason [LobbyModel.sample]
         * exists — a meeting with nobody at it is a screen the app never shows, and a test looking
         * at one proves nothing about the screen that ships.
         *
         * The counts and the five names are the design's, and so is the vote: its ballot is drawn
         * with MARCUS held and YOUR VOTE beside him. It is **chosen and not locked in**, which is
         * the same shape as the lobby's fixture line — typed and not handed over — and it means
         * both halves of the control are on screen to be looked at: a lit row, and a button that
         * has not yet been pressed.
         */
        fun sample(): MeetingModel = MeetingModel(
            counts = MeetingCounts(),
            names = listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS"),
        ).apply { choose(VoteChoice.Named("MARCUS")) }
    }
}

/**
 * How this phone voted: for one person, or for nobody.
 *
 * A type rather than a nullable name, because "nobody" is a choice a player makes and `null` is
 * the absence of one. **Not voting counts as a Skip** (D-075) and ties resolve to Skip, so the
 * whole weight of inaction already sits behind restraining nobody — which is precisely why the
 * two must stay distinguishable on the screen where the player is deciding between them.
 *
 * `Restrain` is the room's physical act and `Revoke` is the Insider's system power; the two are
 * never synonyms. This is a vote *towards* the first of them, and it is the only kind of vote the
 * game has.
 */
sealed interface VoteChoice {

    /** Restrain this resident. */
    data class Named(val name: String) : VoteChoice

    /** Restrain nobody. */
    data object Skip : VoteChoice
}

/**
 * The house's counts at a meeting, as the design's own lines read them out.
 *
 * Counts and a seat total, and nothing that could name anybody — the same shape the lobby's
 * standing has and for the same reason. A meeting is six people who can see each other; how many
 * of them are standing there is not a disclosure, and who they are is not the app's to say.
 *
 * **Written once, drawn twice.** [present] appears on the living's meeting screen and on the
 * out player's, because D-104 makes them the same gate; two fixtures for it would have let the
 * two halves of one gate print different numbers.
 */
data class MeetingCounts(
    val seats: Int = 6,
    /** Phones that answered the ring. */
    val answered: Int = 4,
    /** Phones standing at the meeting area — living and out alike (D-104). */
    val present: Int = 4,
    /** Players who have said they are ready to vote. Unanimous skips the talk ahead. */
    val ready: Int = 3,
    /** Players who have voted. Never *what* they voted. */
    val voted: Int = 4,
) {
    fun ofSeats(n: Int): String = "$n OF $seats"
}

/**
 * **The meeting as a player outside the system sees it: every vote and who cast it.**
 *
 * A different type from [MeetingModel], deliberately and permanently. The living get a count; a
 * player who has been revoked or restrained gets the whole ballot (D-075), and that asymmetry is
 * only safe because of when it arrives — the room already knows who is out, so there is never a
 * window in which someone outside knows something the living do not.
 *
 * Keeping it out of [MeetingModel] means a living phone's meeting state **cannot** carry
 * attribution: not by a field somebody adds later, not by a screen reading one model when it meant
 * the other. `MeetingDisclosureTest` renders every screen in the game and proves only one of them
 * ever draws these rows.
 */
object OutsideView {

    /**
     * The design's own five ballots, three cast and two still out.
     *
     * `null` is *still deciding* rather than a magic string, so the count below is arithmetic over
     * the list rather than a second number written beside it — the ghost's `VOTES CAST 3 OF 5` had
     * been a literal that happened to agree with the rows above it.
     */
    val ballots: List<Ballot> = listOf(
        Ballot("ELLIOT", "DANI"),
        Ballot("PRIYA", "DANI"),
        Ballot("DANI", "ROSE"),
        Ballot("ROSE", null),
        Ballot("TOMAS", null),
    )

    /** How many of them have been cast. Counted, never assumed. */
    val cast: Int get() = ballots.count { it.forWhom != null }

    /**
     * **The mark between a voter and the resident they voted for.**
     *
     * A constant rather than a glyph typed into a screen, because it is the one thing on any panel
     * in this game that means *attribution*, and `MeetingDisclosureTest` sweeps all 56 screens for
     * it: it must be readable on this one and on no other (D-075). A second screen drawing a
     * pairing would render, build green and play — and would quietly be a different game, because
     * *your own vote stays yours* is what makes a meeting an argument rather than an audit.
     */
    const val CAST_FOR: String = "→"
}

/**
 * One ballot, seen from outside the system: who cast it, and who for.
 *
 * [forWhom] is null while that player is still deciding. **This type never reaches a living
 * phone** — see [OutsideView].
 */
data class Ballot(val by: String, val forWhom: String?)

/**
 * The meeting the room screens draw.
 *
 * Provided by [Screen] beside [LocalLobby] and for the same reason: a test that checks in must not
 * leave the next render looking at a phone that is already standing at the meeting area.
 */
val LocalMeeting: ProvidableCompositionLocal<MeetingModel> =
    staticCompositionLocalOf { MeetingModel.sample() }
