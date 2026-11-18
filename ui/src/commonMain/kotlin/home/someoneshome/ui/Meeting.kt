package home.someoneshome.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.IntentMessage

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
     * **Where a control goes when it is pressed — and it does not carry a seat.**
     *
     * *Intents are attributed by connection, never by a client naming itself. A client that could
     * name its own seat could claim another player's — the only cheat in this game that is remote,
     * undetectable, and requires no physical act.* So the screen says **what happened**, not **who
     * it happened to**, and the host's desk attaches the seat, from the connection it arrived on.
     *
     * These four used to go nowhere at all, which was correct while nothing could receive them:
     * a control that predicted an outcome would have been worse than one that did nothing. They
     * still predict nothing — every count on every meeting screen is the house's, and none of them
     * moves when a button here is pressed.
     */
    val send: (MeetingRequest) -> Unit = {},
    /**
     * The house's counts, as they stood when the screen was drawn.
     *
     * Every one of these arrives from the authority in play: how many phones answered the ring,
     * how many are standing at the meeting area, how many have said they are ready, how many have
     * voted. **Nothing on this phone moves them** — see [checkIn].
     */
    initialCounts: MeetingCounts = MeetingCounts(),
    /**
     * The names this phone may vote for, before the house has said any.
     *
     * A fixture for a phone with no house attached, exactly as [initialCounts] is. In play it is
     * replaced by the round's roster — see [names] and [houseNamed].
     */
    initialNames: List<String> = emptyList(),
) {

    /**
     * **The ballot, in SEAT ORDER, as the house issued it** (`Effect.RosterIssued`, D-115).
     *
     * It is a list of who *can* be restrained and says nothing about who should be — and its order
     * is load-bearing rather than presentational: [houseHolds] turns a seat back into a row by
     * index and `MeetingRequest.asMessage` turns a row back into a seat the same way, so **this
     * list is never sorted, filtered or de-duplicated anywhere in this module.** A shortened list
     * would move every seat after the gap and restrain the wrong person.
     *
     * **Nothing on this phone writes it**, which is [counts]' arrangement and its reason: the
     * setter is private and the only caller is [houseNamed]. It was a constructor fixture with no
     * writer at all, so in a real round the vote screen offered the port's five names to a house
     * with five other people standing in it.
     */
    var names: List<String?> = initialNames
        private set

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
    /**
     * **The house's counts, and they move when the house says so and at no other moment.**
     *
     * Observable state rather than a constructor value, because the round can finally push them:
     * `CheckInProgressed`, `ReadyProgressed` and `VoteProgressed` were three of E-P1-4's routeless
     * kinds, so every count on every meeting screen was a fixture — *4 OF 6 CHECKED IN* on a phone
     * in a house where six people were standing in the hall.
     *
     * **Nothing on this phone writes it.** [checkIn] lights this phone's own tick and leaves the
     * number alone, which is the whole of that method's KDoc and is now a property of the type: the
     * setter is private and the only caller is [houseCounted].
     */
    var counts: MeetingCounts by mutableStateOf(initialCounts)
        private set

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
     * *Not voting counts as a Skip* (D-075), so there is no un-vote to draw: the two things a
     * player can express are *this one* and *nobody*, and both are rows on the screen.
     *
     * **It stops being changeable the moment READY is pressed** — see [locked].
     */
    var choice: VoteChoice? by mutableStateOf(null)
        private set

    /**
     * **The vote has been cast and cannot be taken back** (D-117).
     *
     * This is a real flag rather than a comparison between what is selected and what was sent, and
     * the change is a **ruling and not a refactor**: the design said the vote was *changeable until
     * the clock ends* at `gdd.md:412` and `:1006`, and D-117 supersedes both. READY converts the
     * current selection into the actual vote, and after it nothing can be changed.
     *
     * **A locked phone stops echoing, and that is not the phone forming an opinion.** Echo exists
     * to reflect a player's own input; a tap the house will refuse is not input the game accepted,
     * and lighting a row that would then snap back is worse than not lighting it. This is one of
     * the few facts a phone genuinely holds about itself — it pressed the button — which is why it
     * can be honoured here without asking anybody. The house refuses the tap as well, and
     * re-asserts what it holds (`Effect.VoteHeld`), so the two agree without the screen guessing.
     */
    var locked: Boolean by mutableStateOf(false)
        private set

    /** I AM HERE. */
    fun checkIn() {
        checkedIn = true
        send(MeetingRequest.CheckIn)
    }

    /** READY TO VOTE. */
    fun sayReady() {
        ready = true
        send(MeetingRequest.ReadyToVote)
    }

    /**
     * A tap on a name, or on SKIP. Re-tapping what is already held changes nothing.
     *
     * **Ignored once the ballot is locked**, and nothing is sent — see [locked].
     */
    fun choose(next: VoteChoice) {
        if (locked) return
        choice = next
        send(MeetingRequest.Select(next))
    }

    /**
     * READY: turn the selection into the vote, irrevocably (D-117).
     *
     * **Refused when nothing is selected**, rather than quietly sending nothing. Not voting is
     * already a Skip and SKIP is already a row — a button that handed over an empty vote would be
     * a third way to say the same thing, and the only one of the three with nothing on screen to
     * show for it. The buzzer's auto-lock is what handles a player who never chose at all, and
     * that is the house's to run.
     *
     * **Refused a second time when already locked**, for the reason [locked] gives.
     */
    fun readyToVote() {
        if (choice == null || locked) return
        locked = true
        send(MeetingRequest.LockVote)
    }

    /**
     * Whether the row for [name] at [seat] is the one holding this phone's vote. **By seat when the
     * choice knows it** (D-278): two people may give one name, and only one of their rows is lit.
     */
    fun holds(name: String, seat: Int): Boolean = (choice as? VoteChoice.Named)
        ?.let { it.name == name && (it.seat ?: names.indexOf(name)) == seat } == true

    /** Whether a row named [name] holds this phone's vote, whichever seat it is at. */
    fun holds(name: String): Boolean = (choice as? VoteChoice.Named)?.name == name

    /** Whether SKIP is the one holding it. */
    val skipping: Boolean get() = choice == VoteChoice.Skip

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
        locked = false
    }

    /**
     * **A house opened a meeting: every count is unsaid until it counts** (D-247).
     *
     * Called from `FlowModel` on the house's own ring (`MeetingOpened`, `StandAndWalkIn`), which
     * arrive before the gate's first count in the same burst, so the counts a screen draws are this
     * meeting's and never the last one's or the design's. Not from [meetingBegan], which runs on a
     * screen arriving and could land after the count it would wipe.
     */
    fun houseOpenedMeeting() {
        counts = MeetingCounts.UNSAID
        counted = true
    }

    /**
     * **A house attached: drop the design's counts if no house has counted yet** (D-247). A
     * reconnect calls this too, and must not wipe a meeting in progress, so it clears only counts
     * no house has written.
     */
    fun houseAttached() {
        if (!counted) counts = MeetingCounts.UNSAID
        counted = true
    }

    /** Whether a house has written [counts] since this model was built. */
    private var counted = false

    /**
     * **The house counted, and this is the one writer of [counts].**
     *
     * Four kinds land here — the ring answered, the gate, the hands up, the ballots locked — and
     * each moves one number and leaves the other three where they were. That is deliberate rather
     * than convenient: the four are counted at four different moments by four different rules, and
     * a push that carried all of them would make every count a re-statement of every other, so a
     * kind that stopped being emitted would go on looking correct.
     *
     * [seats] is the total each of those pushes carries with it — `expected` on three of them. It
     * is the same number every time and is written every time for the same reason the counts are
     * separate: the house saying *of six* on every push is what keeps a screen from drawing a
     * fraction whose denominator came from a round that has since lost a player.
     */
    fun houseCounted(
        answered: Int? = null,
        present: Int? = null,
        ready: Int? = null,
        voted: Int? = null,
        seats: Int? = null,
        living: Int? = null,
    ) {
        counted = true
        counts = counts.copy(
            seats = seats ?: counts.seats,
            living = living ?: counts.living,
            answered = answered ?: counts.answered,
            present = present ?: counts.present,
            ready = ready ?: counts.ready,
            voted = voted ?: counts.voted,
        )
    }

    /**
     * **The house re-asserting this seat's own ballot** (`Effect.VoteHeld`, D-117).
     *
     * The lit row is input echo and is already on the screen; what the house adds is **the refusal
     * after READY**, which needs a field that says *the house holds this one* rather than *your
     * finger is here*. Emitted on every tap so that its absence can never be the message (rule 1),
     * which means this arrives constantly and agrees with the screen nearly always — and the one
     * time it does not is the time that matters.
     *
     * [selection] is a seat and [names] is what this phone may vote for, so the index is the row:
     * `asMessage`'s move in reverse, lawful for its reason — names are round-scoped, arrive from
     * the house (D-115), and **the list is never sorted or filtered in this module.** A seat the
     * list does not hold leaves the choice alone rather than blanking it, which is the fail-closed
     * direction: a phone that cleared a lit row because a roster was short would take a player's
     * vote off the screen and give them nothing to put back.
     *
     * **[locked] is taken from the house outright**, because it is the one half a phone cannot know
     * better: this is the flag that stops the controls responding, and a phone that kept its own
     * `false` after the buzzer auto-locked every ballot would go on echoing taps the house refuses.
     */
    /**
     * **The round's roster, from the house** (`Effect.RosterIssued`, D-115).
     *
     * One entry per seat, in seat order, blank where a phone never said what to call its owner —
     * the shape [names] indexes into in both directions. **Null at a seat index nobody holds**
     * (D-274, D-277): the index is the seat even across a hole, and the ballot draws no row for a
     * null. A blank is somebody — a seat in the round with no name — and keeps its row, drawn as
     * [UNNAMED]; folding the two together left an unnamed Insider beyond the vote. Written by the one branch in `FlowModel`
     * that receives it, so the ballot and `PanelState.roster` cannot come to hold different lists.
     */
    fun houseNamed(roster: List<String?>) {
        names = roster
    }

    fun houseHolds(selection: Seat?, locked: Boolean) {
        choice = when {
            selection == null -> if (this.locked || choice == VoteChoice.Skip) VoteChoice.Skip else choice
            else -> names.getOrNull(selection.index)?.let { VoteChoice.Named(it, selection.index) } ?: choice
        }
        this.locked = locked
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
            initialCounts = MeetingCounts(),
            initialNames = listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS"),
        ).apply { choose(VoteChoice.Named("MARCUS")) }
    }
}

/**
 * **What a meeting control asks the house for — with no seat on it.**
 *
 * The four controls at a meeting are the first ones in this app that reach the authority rather
 * than only the screen, and the shape they reach it in is the whole point of this type.
 *
 * `Intent` carries an `actor: Seat` and this does not, deliberately: *intents are attributed by
 * connection, never by a client naming itself* — a client that could name its own seat could claim
 * another player's, which is the only cheat in this game that is remote, undetectable and requires
 * no physical act. A screen that could construct `Intent.CheckIn(Seat(4))` is a screen that could
 * check somebody else in. So the screen says what happened, and the seat goes on at the connection
 * that already knows it.
 *
 * **That is now structural rather than conventional.** [asMessage] hands this to an
 * `IntentMessage`, which is the wire type and has no field a seat could ride in at all — so the
 * discipline `ui` was keeping by agreement is kept by the shape of the frame, all the way to the
 * host's desk. Nothing in this module takes a [Seat] and returns an `Intent` any more.
 */
sealed interface MeetingRequest {

    /** I AM HERE (D-104). */
    data object CheckIn : MeetingRequest

    /** READY TO VOTE. */
    data object ReadyToVote : MeetingRequest

    /** A finger on a row. Transmitted live so the couch can watch the vote happen (D-117). */
    data class Select(val choice: VoteChoice) : MeetingRequest

    /** READY. Irrevocable, and it carries no target: it converts whatever was last selected. */
    data object LockVote : MeetingRequest
}

/**
 * **The control, as the frame that carries it — and the seat does not go on here either.**
 *
 * This replaced `asIntent(seat, names)`, and losing the parameter is the point rather than a
 * tidy-up. That function was `ui`'s promise not to name a seat, kept by nobody passing it a wrong
 * one; this one **cannot** name a seat, because `IntentMessage` has no field for one. The stamp
 * happens at the host's desk, from the connection the bytes arrived on, and there is no longer any
 * function in this module that turns a [Seat] into an `Intent`.
 *
 * **Exhaustive on purpose**: a fifth control at a meeting does not compile until somebody decides
 * what it asks the house for, which is the same discipline `EmitSchema.kindOf` and
 * `ScreenGraph.exitsOf` use. A `when` with an `else` here would let a new control quietly become a
 * request nobody named.
 *
 * [names] is the ballot the screen was drawn with, because a vote row carries a name and the wire
 * carries a seat — names are round-scoped and arrive from the house (D-115), so turning one back
 * into a seat is a lookup against the same list the house sent. A name that is not on it resolves
 * to **Skip**, which is the fail-closed direction: not voting counts as a Skip already (D-075), so
 * an unresolvable row can only ever cost a vote and never cast one at somebody.
 *
 * That seat is *content* and not attribution — it is who the vote is for, which the house publishes
 * live to the couch (D-117) — and the two are kept apart by the wire type having room for the first
 * and none for the second.
 */
/**
 * **A seat the roster cannot name**, as every screen draws it.
 *
 * The same word `LobbyScreen` draws for a phone that never said what to call its owner: a row with
 * an empty left-hand side would read as a rendering fault rather than as somebody who joined without
 * typing a name. It is also the fail-closed answer for a seat the roster does not hold at all — a
 * couch row still says a ballot was cast, and it names nobody rather than naming somebody nearby.
 */
internal const val UNNAMED = "UNNAMED"

fun MeetingRequest.asMessage(names: List<String?>): IntentMessage = when (this) {
    is MeetingRequest.CheckIn -> IntentMessage.CheckIn
    is MeetingRequest.ReadyToVote -> IntentMessage.DeclareReadyToVote
    is MeetingRequest.LockVote -> IntentMessage.LockVote
    is MeetingRequest.Select -> {
        val picked = choice
        val target = when (picked) {
            is VoteChoice.Skip -> null
            // **The row's own seat, when it carries one and the roster still names it there**
            // (D-278): two people who gave one name are two rows, and `indexOf` would send both
            // to the first. Only a choice that knows no seat falls back to the name.
            is VoteChoice.Named -> picked.seat?.takeIf { names.getOrNull(it) == picked.name }?.let { Seat(it) }
                ?: names.indexOf(picked.name).takeIf { it >= 0 }?.let { Seat(it) }
        }
        IntentMessage.SelectVote(target = target)
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

    /**
     * Restrain this resident: the row's name, and the seat it was drawn at (D-278). The seat is
     * what goes to the house, because a name need not be unique in a room; a choice built from a
     * name alone (a fixture, a test) reaches the first seat that bears it.
     */
    data class Named(val name: String, val seat: Int? = null) : VoteChoice

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
    val seats: Int? = 6,
    /**
     * **The living, which is the other denominator and not a second copy of [seats].**
     *
     * Three of the four counts below are fractions of different things, and the house says which
     * every time it sends one: presence is *every living player and every out player* (D-104), and
     * readiness and the ballot are the living's alone — a ghost has no hand to put up (D-147) and
     * casts nothing (`gdd.md:417`). Drawn against [seats] they would read `3 OF 6 READY` in a house
     * where two people are out and four hands are all the hands there are, which is a screen
     * telling a room it is waiting on somebody who is sitting on the couch.
     *
     * It defaults to [seats] so a fixture with nobody out draws what it always drew.
     */
    val living: Int? = 6,
    /**
     * Phones that answered the ring. **No house sends this** (D-247): the ring is answered on the
     * phone and the house hears nothing until a check-in, so on a housed phone it stays null and
     * the caller's screen draws no count at all.
     */
    val answered: Int? = 4,
    /** Phones standing at the meeting area — living and out alike (D-104). */
    val present: Int? = 4,
    /** Players who have said they are ready to vote. Unanimous skips the talk ahead. */
    val ready: Int? = 3,
    /** Players who have voted. Never *what* they voted. */
    val voted: Int? = 4,
) {
    /** `4 OF 6` — against every seat in the round. The ring and the check-in gate. Null if either is unsaid. */
    fun ofSeats(n: Int?): String? = if (n != null && seats != null) "$n OF $seats" else null

    /** `3 OF 4` — against the living. Readiness and the ballot. See [living]. Null if either is unsaid. */
    fun ofLiving(n: Int?): String? = if (n != null && living != null) "$n OF $living" else null

    companion object {
        /**
         * **A meeting the house has said nothing about yet** (D-247). Every count null, so every
         * screen draws no number until a push fills it — never the design's six, four and three,
         * which on a housed phone were `4 OF 6 ANSWERED` at every meeting of every round.
         */
        val UNSAID: MeetingCounts = MeetingCounts(null, null, null, null, null, null)
    }
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
     * `null` is *still deciding* rather than a magic string. The ghost screen drew a `VOTES CAST
     * 3 OF 5` over them until the owner removed it (decision pack 9a, D-285); the rows are all of it.
     */
    val ballots: List<Ballot> = listOf(
        Ballot("ELLIOT", "DANI"),
        Ballot("PRIYA", "DANI"),
        Ballot("DANI", "ROSE"),
        Ballot("ROSE", null),
        Ballot("TOMAS", null),
    )

    /**
     * **The mark between a voter and the resident they voted for.**
     *
     * A constant rather than a glyph typed into a screen, because it is the one thing on any panel
     * in this game that means *attribution*, and `MeetingDisclosureTest` sweeps every screen in the game for
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
