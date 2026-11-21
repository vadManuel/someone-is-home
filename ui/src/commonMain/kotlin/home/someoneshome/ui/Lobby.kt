package home.someoneshome.ui

import home.someoneshome.model.Balance
import home.someoneshome.model.InsiderBand
import home.someoneshome.model.SavedHome
import home.someoneshome.model.protocol.LobbyBody

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **A home advertising itself on the local network.**
 *
 * Discovery is local only — no account, no internet — and the name is whatever the host called
 * their home. It is on the air for the whole network before any permission this app controls, so
 * it must never carry anything a round produced: no roles, no seats, no state. This type could
 * not carry one if it wanted to.
 */
data class NearbyHome(val name: String, val address: String, val port: Int)

/**
 * **The radio, as far as `ui` is allowed to know about it.**
 *
 * `ui` cannot see `platform` — `app` is the one module that sees both — so mDNS arrives here as
 * two methods and no detail, exactly as the phone's filesystem arrives as [HomeStore]. What is
 * behind it on a real phone is a `HostBrowser`; what is behind it in every test and every render
 * is [MemoryHomeFinder].
 */
interface HomeFinder {
    /**
     * Begin looking. [onFound] fires once per home found, on whatever thread found it.
     *
     * [onRefused] fires when the OS will not let the phone look at all (D-309): on iOS, a browse
     * refused because Local Network is off. The search is over when it fires; [stop] and [start]
     * again to look again.
     */
    fun start(onFound: (NearbyHome) -> Unit, onRefused: () -> Unit = {})
    fun stop()
}

/**
 * **What JOIN A HOME says under NETWORKS NEARBY while no home is listed** (D-309, the owner's words,
 * 2026-11-20). Set by [LobbyModel] from what the radio said; the screen only draws it.
 */
enum class Listening(
    /** The caption the screen draws, exactly the owner's words. */
    val caption: String,
) {
    /** Still looking, and nothing has answered yet. */
    Looking("LISTENING FOR A HOME ON THIS NETWORK"),

    /**
     * Looked for [LobbyModel.NO_HOME_AFTER_MILLIS] and nothing answered. The phone keeps looking,
     * and a home that answers later is listed as usual.
     */
    NothingFound("NO HOME ON THIS NETWORK"),

    /** The OS refused the search: Local Network is off. OPEN SETTINGS is drawn under it. */
    Off("LOCAL NETWORK IS OFF"),
}

/**
 * **Where this phone's own resident name is kept between launches** (the owner's E-PAY-6 ruling:
 * *it should be saved*).
 *
 * `ui` cannot see `platform`, so the phone's filesystem arrives here as two functions and no
 * detail — [HomeStore]'s shape exactly, and a separate type rather than a second use of that one so
 * that no caller can hand the homes file to the name and find out later.
 *
 * ### It reverses a written rule, and the reversal is narrower than the rule was
 *
 * *The name is never persisted — it is re-sent from what this phone still has in memory, or not at
 * all* was true of `LobbyOverTransport` and of `LobbyDesk`, and it stays true of both. **What the
 * host holds about other people is still round-scoped and still written nowhere**: the desk drops
 * every name when the round ends (D-115), and nothing on the wire has changed. What is stored here
 * is one string — *what the owner of this phone calls themselves* — on that phone, by that person,
 * about themselves. It is not somebody else's name, it is not a seat, and it is not a line.
 *
 * The cost of not having it was an evening: the owner force-quit the app to fix their own name and
 * lost the round with it (E-PAY-7). Re-typing a name in a dark hallway is the failure this closes.
 *
 * **A failed write does not throw and must not.** A phone that could not save its name still plays
 * perfectly; the whole consequence is that it asks again next time. That is the opposite of the
 * seat token, where a silent failure is a phone that cannot resume — see `saveSeatToken`, whose
 * KDoc argues the other side of the same question.
 */
interface NameStore {
    fun read(): String?
    fun write(text: String)
}

/**
 * The store the screens get when nobody wired a real one: this session, and nothing after it.
 *
 * Deliberately not a no-op, for [MemoryHomeStore]'s reason — a store that dropped writes would let
 * a test claim a name survived while proving nothing about surviving.
 */
class MemoryNameStore(private var text: String? = null) : NameStore {
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
}

/**
 * **Why HOST WITH THIS HOME was refused: the home never went on the air.**
 *
 * `ui` cannot see the radio and has no business knowing what Bonjour returned, so what crosses
 * this seam is the distinction a *host* can act on — rename the home, or go and look at the
 * network. The words are [LobbyModel.hostHome]'s, one layer up, where every other refusal on
 * these screens is written.
 *
 * It exists because the failure used to reach nobody (E-D3-2): a second house called THE BUNGALOW
 * was refused by the OS, never published, and its host waited in a lobby that no phone on the
 * network could see, with no reason given on any screen.
 */
enum class HostRefusal {

    /** A home of this name is already on the air here. Two households, one name — or one host twice. */
    NameAlreadyOnTheAir,

    /** The advertisement was refused for some other reason, so nobody can find this home. */
    CouldNotGoOnTheAir,
}

/**
 * **The wire, as far as `ui` is allowed to know about it.**
 *
 * Three verbs and one shape coming back. Note what is *not* here: there is no way to ask this
 * interface for anybody's one line. [LobbyBody.Standing] is what comes back, it carries the counts,
 * the setting and the names (D-115), and there is no second reply and no second question. A screen
 * that wanted a line would have to be given a way to ask for one, and that is a decision about
 * what a client receives — not something a lobby screen quietly acquires.
 */
interface LobbyLink {

    /**
     * Run the house on this phone, under this home, and report where it came up.
     *
     * The caller then [join]s it like any other phone. That is not ceremony: the host is a player
     * too, and a host that talked to its own lobby through a side door would be a second code
     * path nobody exercises — with the counts on the host's screen coming from somewhere the
     * other five phones' counts do not.
     *
     * **[onUp] fires when the home is on the air, and [onRefused] when it could not go on it.**
     * Exactly one of them, and neither at the call — a house is up when the rest of the network
     * can find it, not when a socket has bound. The alternative is what shipped and what E-D3-2
     * is: a lobby that opens, fills with nobody and never explains itself.
     *
     * ### It takes the [SavedHome] and not its name (E-PAY-3)
     *
     * Only the name goes on the air, and `ui` still learns nothing about what is done with the
     * rest: what crosses here is *which house this phone is hosting*, and the house on the other
     * side of this seam is the one that arms the round in it. It used to take a name, and the
     * consequence was a round played in a fixture — five rooms the host does not have, ten cards
     * they never printed — on every phone, every time. **A home is not a label**, and this is the
     * one call where that distinction was being thrown away.
     */
    fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit = {})

    /**
     * Attach to a home under [name]. [onStanding] fires every time the house says how the lobby
     * stands.
     *
     * **The name is a parameter of joining rather than a verb of its own**, because there is no
     * moment in this game where a phone is in a lobby and has not said what to call its owner. It
     * rides up again on every re-seating, so a resume comes back to a house that can still list
     * the person holding the phone; it is never written to this device on either side.
     */
    fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit)

    /**
     * Hand the one line to the house. **The only authorised exit from this phone for that text.**
     */
    fun handOver(line: String)

    /**
     * The host's Insider-count setting; null is UNKNOWN. On a phone that is not hosting this does
     * nothing, because the setting is not that phone's — and the lobby does not offer the control
     * there either, so nothing legitimate calls it.
     */
    fun setInsiders(chosen: Int?)

    /**
     * **The host's ballot length, in seconds** (D-117). [setInsiders]' shape and its restriction:
     * on a phone that is not hosting this does nothing, and the lobby offers the control nowhere
     * else, so nothing legitimate calls it there.
     *
     * It exists because the setting had nowhere to go. The VOTING row moved a number `LobbyModel`
     * held and the round could not read — and the round is armed off the desk, so the desk is where
     * it has to land. **Nothing about it goes down the wire**: the clients learn the window when
     * the ballot opens, on the push that opens it.
     */
    fun setVoteWindow(seconds: Int)

    /**
     * **The round ended: the house drops every line it is holding, and the connection stays up**
     * (D-116, D-157).
     *
     * Separate from [leave] and that separation is the ruling. Both wipe the desk; only one of
     * them takes the house down — and D-157 keeps the home, the seats and the settings across a
     * NEW ROUND, so an evening whose second round began by tearing down the lobby would be five
     * people re-typing a lobby code in the dark to play again.
     */
    fun roundEnded()

    /**
     * **This phone stands up and walks out.** The connection goes down; the house recounts.
     *
     * The seat and the line drop because the *desk* drops them — `LobbyDesk.left` has always done
     * exactly that on a lost connection, and it publishes a new standing to everybody still in the
     * room. Nothing here predicts the new counts and nothing may: a client that decremented its own
     * `joined` would be a phone asserting a lobby.
     *
     * On the host's phone it takes the house down with it, because a host who has left is not still
     * running a party.
     */
    fun leave()

    /**
     * **The host closes the house on everybody** (E-PAY-6). [leave]'s sibling, and a different act.
     *
     * [leave] is one person standing up and the other five carry on. This is the party being over:
     * the server stops, the advertisement comes off the air, and every other phone is *told*
     * (`LobbyBody.HouseClosed`) rather than left to notice that its counts stopped moving. Before
     * this existed a client in a lobby had no exit of any kind and a host had no way to end one —
     * the trap the owner reported.
     *
     * **On a phone that is not hosting this does nothing**, the shape [setInsiders] and
     * [setVoteWindow] already have, and the lobby offers the control nowhere else — so nothing
     * legitimate calls it there.
     */
    fun terminate()

    /**
     * **This phone holds a seat in a round it has not been told is over** (D-311, the overnight
     * review's finding 2): the round half kept a record of it beside the seat token (`SeatStore`'s
     * owed reports), which only a round writes and its ending forgets. A phone iOS ended for a grant
     * switched off mid-round relaunches holding one, and goes back to that seat rather than being
     * held at PERMISSIONS. False by default, for a link with no round behind it.
     */
    fun holdsARoundSeat(): Boolean = false
}

/**
 * The finder every test and every render gets: a fixed list, handed over at once.
 *
 * Deliberately not a no-op. A finder that found nothing would leave the one screen whose job is
 * to show what is nearby with nothing on it, and every rendering test would then be looking at a
 * screen the app never shows.
 */
class MemoryHomeFinder(private val homes: List<NearbyHome> = emptyList()) : HomeFinder {
    override fun start(onFound: (NearbyHome) -> Unit, onRefused: () -> Unit) = homes.forEach(onFound)
    override fun stop() = Unit
}

/**
 * **A finder a test drives by hand** (D-309): it finds and refuses only when told, so a test can
 * walk JOIN A HOME through LISTENING, NO HOME and LOCAL NETWORK IS OFF and back.
 */
class ScriptedHomeFinder : HomeFinder {
    private var found: (NearbyHome) -> Unit = {}
    private var refused: () -> Unit = {}

    /** How many times looking began, a restart included. Test-facing. */
    var starts: Int = 0
        private set

    override fun start(onFound: (NearbyHome) -> Unit, onRefused: () -> Unit) {
        starts++
        found = onFound
        refused = onRefused
    }

    override fun stop() {
        found = {}
        refused = {}
    }

    /** A home answered. */
    fun answers(home: NearbyHome) = found(home)

    /** The OS refused the search. */
    fun refuses() = refused()
}

/**
 * The link every test and every render gets: **a host desk, in memory, on this phone.**
 *
 * It answers the way the real host answers — it clamps the Insider setting into D-103's band and
 * publishes a standing — because a fixture that accepted anything would let the lobby's control
 * be built wrong and still look right. The band arithmetic is not reimplemented here; it is
 * [InsiderBand]'s, which is the one place that number lives.
 */
class MemoryLobbyLink(
    joined: Int = 0,
    linesIn: Int = 0,
    /**
     * The people already in the lobby when this phone walks in, in the order they arrived. This
     * phone's own name is not among them — it is put in by [join], in the last seat, which is
     * where a desk would have put it.
     *
     * Defaulted to the design's own five rather than to nothing, for the reason [MemoryHomeFinder]
     * finds homes rather than nothing: a lobby with nobody in it is a screen the app never shows,
     * and a rendering test looking at one proves nothing about the screen that ships.
     */
    private val others: List<String> = THE_OTHERS,
) : LobbyLink {

    private var mine: String = ""
    private var standing =
        LobbyBody.Standing(joined = joined, linesIn = linesIn, names = seatNames(joined))
    private var listener: (LobbyBody.Standing) -> Unit = {}

    /** The lines this fixture has been handed, so a test can prove one arrived. Never persisted. */
    val received = mutableListOf<String>()

    /** The names it has been told, for the same reason. */
    val named = mutableListOf<String>()

    /** The homes this fixture has been asked to host, so a test can prove which one crossed. */
    val hosted = mutableListOf<SavedHome>()

    override fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) {
        // Loopback, because that is what the real one does: the house runs here and this phone
        // joins it over the local interface like everybody else. The fixture always gets on the
        // air; the refusal has its own stand-in ([RefusingLobbyLink]) rather than a flag here,
        // because every render and every screen test wants a home that went up.
        hosted += home
        onUp(NearbyHome(home.name, "127.0.0.1", 0))
    }

    override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) {
        listener = onStanding
        mine = name
        named += name
        standing = standing.copy(names = seatNames(standing.joined))
        publish()
    }

    override fun handOver(line: String) {
        received += line
        standing = standing.copy(linesIn = minOf(standing.joined, standing.linesIn + 1))
        publish()
    }

    /**
     * The desk dropped every line, so the count it publishes goes to nothing — **and the seats do
     * not** (D-157).
     *
     * That is the fixture answering the way the real desk answers rather than doing nothing, for
     * the reason this class clamps the Insider band: a stand-in that quietly kept the count would
     * let the next round's LIGHTS OUT gate open with nobody having typed anything, and the screen
     * would look right the whole time.
     *
     * [received] is deliberately **not** cleared. It is the test's window onto what crossed the
     * link, not a store the house reads — and a fixture that erased its own evidence would make
     * *the line never arrived* and *the line arrived and was dropped* the same observation.
     */
    override fun roundEnded() {
        standing = standing.copy(linesIn = 0)
        publish()
    }

    override fun setInsiders(chosen: Int?) {
        standing = standing.copy(insiders = InsiderBand.clamp(standing.joined, chosen))
        publish()
    }

    /**
     * The windows this fixture has been told, so a test can prove the setting left the screen.
     *
     * **Nothing is published**, which is the fixture answering the way the real link answers: the
     * ballot's length is not part of the standing and never goes down the wire — a client learns
     * it when the ballot opens. A stand-in that echoed it into [LobbyBody.Standing] would let a
     * screen be written against a field the protocol does not have.
     */
    val windows = mutableListOf<Int>()

    override fun setVoteWindow(seconds: Int) {
        windows += seconds
    }

    /**
     * **This phone left: the seat and the line go with it, and the desk republishes.**
     *
     * The fixture answers the way the real desk answers rather than only dropping the listener —
     * the same reason it clamps the Insider band. A stand-in that kept the counts would let the
     * leave control be built against a lobby that never recounted, and the screen would look right
     * the whole time.
     *
     * `linesIn` falls only if this phone had handed one over, which the fixture knows because it is
     * holding the receipts. It cannot fall below zero and `joined` cannot fall below zero either: a
     * second leave from a phone that has already left is a no-op, not a negative lobby.
     */
    override fun leave() {
        if (mine.isBlank() && standing.joined == 0) return
        val hadHandedOver = received.isNotEmpty()
        val joined = maxOf(0, standing.joined - 1)
        mine = ""
        standing = standing.copy(
            joined = joined,
            linesIn = minOf(joined, if (hadHandedOver) standing.linesIn - 1 else standing.linesIn),
            names = seatNames(joined),
        )
        publish()
        listener = {}
    }

    /**
     * **The host closed the house.** There is no lobby left, so there is no standing to publish.
     *
     * It does not fall through to [leave]. A leave is one seat going and a desk recounting for the
     * people still there; this is the desk itself going away, and a fixture that published a lobby
     * of five after the house came down would be modelling something that cannot happen.
     */
    override fun terminate() {
        mine = ""
        standing = standing.copy(joined = 0, linesIn = 0, names = emptyList())
        listener = {}
    }

    private fun publish() = listener(standing)

    /**
     * One entry per seat, blanks for the seats nobody has spoken for — the invariant
     * [LobbyBody.Standing] documents and the host's desk holds by building the list from its
     * seats. A fixture that published a shorter list would let a screen be written against a
     * `names` that is sometimes `joined` long and sometimes not.
     */
    private fun seatNames(joined: Int): List<String> {
        val here = (others + mine).filter { it.isNotBlank() }.take(joined)
        return here + List(joined - here.size) { "" }
    }

    private companion object {
        /** The design's own lobby, minus ELLIOT — the name the sample phone types for itself. */
        val THE_OTHERS: List<String> = listOf("PRIYA", "MARCUS", "DANI", "ROSE", "TOMAS")
    }
}

/**
 * **The link for the one case [MemoryLobbyLink] cannot show: a home the radio would not carry.**
 *
 * A separate fixture rather than a flag on the other one, because the two are asked for by
 * different tests and a link that sometimes hosts and sometimes does not is a fixture every
 * caller has to configure. Nothing else about it works — a home that never went on the air has no
 * lobby behind it, which is the whole of the ruling.
 */
class RefusingLobbyLink(private val why: HostRefusal = HostRefusal.NameAlreadyOnTheAir) : LobbyLink {
    override fun host(home: SavedHome, onUp: (NearbyHome) -> Unit, onRefused: (HostRefusal) -> Unit) =
        onRefused(why)
    override fun join(home: NearbyHome, name: String, onStanding: (LobbyBody.Standing) -> Unit) = Unit
    override fun handOver(line: String) = Unit
    override fun setInsiders(chosen: Int?) = Unit
    override fun setVoteWindow(seconds: Int) = Unit
    override fun roundEnded() = Unit
    override fun leave() = Unit
    override fun terminate() = Unit
}

/**
 * **The one line, held in memory and nowhere else.**
 *
 * A player is asked for something they would rather not explain, on the strength of two promises
 * printed on the screen where they type it: *seen by the house only*, and *deleted when the round
 * ends*. This class is the client half of keeping them.
 *
 * ### Why it is a class and not a `String` on [PanelState]
 *
 * [PanelState] is flat, inert and — the part that matters here — the thing a recording captures.
 * A line living there would ride into every transcript of every round, and transcripts are the
 * one artefact of this game that outlives the evening. So it lives beside the panel, with the
 * rest of the interactive state, and never in it.
 *
 * ### [toString] does not contain the line, on purpose
 *
 * Everything that ever accidentally leaks a value leaks it through a `toString`: a log line, a
 * crash report, a `data class` that holds this one and prints its fields for free. There is no
 * legitimate caller that wants the text interpolated into a string, so this one reports the shape
 * and not the content, and the failure mode of getting that wrong is a test.
 */
class OneLine {

    /** What has been typed. Drawn on exactly one screen; sent on exactly one path. */
    var text: String by mutableStateOf("")
        private set

    /** True once the house has it. Re-typing and handing over again replaces it. */
    var handedOver: Boolean by mutableStateOf(false)
        private set

    /** Blank is not a line. "One line, and make it real" is a requirement, not encouragement. */
    val isReal: Boolean get() = text.isNotBlank()

    fun type(next: String) {
        text = next.take(LIMIT)
    }

    fun wasHandedOver() {
        handedOver = true
    }

    /** **Deleted when the round ends.** The promise, kept on this side too. */
    fun roundEnded() {
        text = ""
        handedOver = false
    }

    override fun toString(): String = "OneLine(${text.length} typed, handedOver=$handedOver)"

    private companion object {
        /**
         * One line, and the field is one line high. Past this the text scrolls out of the box the
         * player is reading, which is a promise about legibility rather than a limit on candour.
         * The number lives in the model now, where the house and the reveal's padding read it too
         * (D-249).
         */
        const val LIMIT = LobbyBody.Handover.LIMIT
    }
}

/**
 * **The lobby, as this phone experiences it: who it can see, what it has typed, and how the house
 * says the room stands.**
 *
 * Sits beside [PanelState] with [HomeEditorModel] and [SavedHomesModel], for the reason they do:
 * `PanelState` is flat and inert and every field of it is already decided at the effect boundary,
 * and a name being typed, a list of networks filling in, and a count arriving off the wire are
 * none of those things.
 *
 * **It answers no game question and never will.** Every number on the lobby screen came off the
 * wire from the house; nothing here derives one. The single piece of arithmetic in the file is
 * [InsiderBand], which is a *setting's* envelope and is consulted by the host's own control
 * before it asks the house for anything.
 */
class LobbyModel(
    private val finder: HomeFinder = MemoryHomeFinder(),
    private val link: LobbyLink = MemoryLobbyLink(),
    hosting: Boolean = false,
    /**
     * **The way out to the OS's own settings page for this app** (the owner's 2026-08-24 ruling).
     *
     * A lambda for `MeetingModel(send = …)`'s reason — this module cannot see `platform`, and what
     * goes up is not a request with content in it but a verb with none. The app hands it
     * `deviceSystemSettings()::open`; **defaulted to nothing, so a bench, a screenshot and a
     * rendering test all draw the control and none of them leaves the app.**
     *
     * It is reachable from two places, both in a lit room before any round exists: the control
     * under the hosting refusal ([openSystemSettings]) and the one under LOCAL NETWORK IS OFF on
     * JOIN A HOME (D-309).
     */
    private val settings: () -> Unit = {},
    /**
     * **Where this phone's own name lives between launches** (the owner's E-PAY-6 ruling). See
     * [NameStore], which argues why storing this one string does not reopen anything D-115 closed.
     *
     * Defaulted to memory for [HomeStore]'s reason: every test, every render and the desktop
     * preview get a store that really holds a value for the session and nothing after it, so a test
     * about persistence is testing a store rather than a stub.
     */
    private val names: NameStore = MemoryNameStore(),
) {

    /**
     * Whether this phone is the one running the house.
     *
     * The lobby is one screen and both ends see it. What differs is not the information — it is
     * counts on both — but who may touch the settings, because they are the host's. A client
     * drawing a live control for somebody else's setting would be a control that lies.
     *
     * Set by [hostHome] rather than fixed at construction: one app has to be able to be either,
     * and which one it is depends on a button the host presses fifteen minutes in.
     */
    var hosting: Boolean by mutableStateOf(hosting)
        private set

    /**
     * This phone's own resident name, typed on the way in — **and read back off this phone at the
     * next launch** (the owner's E-PAY-6 ruling: *it should be saved*).
     *
     * **It leaves the device at [attachTo], and only there** (D-115). The host learns it because a
     * house that cannot say who is being Restrained cannot hold a meeting, and every other phone
     * learns it because the design's lobby lists the people in it. It is round-scoped on the
     * *house's* side and dropped there when the round ends; what is new is that the phone whose
     * owner typed it does not forget it.
     *
     * The KDoc here used to end *written to no store on either*, and the half of that sentence
     * about the host is unchanged. See [NameStore] for why the other half moved.
     *
     * Typed before attaching, so a change afterwards moves nothing: there is one screen with this
     * field on it and it is the one before the lobby.
     */
    var residentName: String by mutableStateOf(names.read()?.take(NAME_LIMIT).orEmpty())
        private set

    /**
     * **Whether this phone has said what to call its owner** — the gate on hosting and on joining
     * (E-PAY-6: *name not persisted and not required before host/join*).
     *
     * It used to be neither. `attachTo`'s own KDoc said so out loud — *a player who typed nothing
     * joins under a blank, which the house lists as an unnamed seat rather than refusing: the lobby
     * is a lit room and the fix is to say something, not a dialog* — and that argument is still
     * right about **dialogs** and was wrong about **controls**. A seat with no name on it is a
     * person the room cannot Restrain by name, at a meeting where naming somebody is the entire
     * mechanic; and the phone that has to fix it is the one that already walked away from the field.
     *
     * ### The refusal is the control being inert, not a sentence somebody has to dismiss
     *
     * Both gated controls are drawn, in place, unpressable, with the reason on the row — the shape
     * LIGHTS OUT already has while a lobby is short (D-141: no pointer input at all, so there is
     * nothing to press and nothing to time). Nothing pops up and nothing has to be acknowledged.
     */
    val named: Boolean get() = residentName.isNotBlank()

    /**
     * **Which rows in the lobby are this phone's owner** (the owner's E-PAY-6 ruling: *my name
     * should be highlighted*).
     *
     * A client-side string match against what this phone typed, and **that is the whole of the
     * mechanism** — the house is not asked and could not be. `Effect.MeetingCalling` carries no
     * field for exactly this reason (D-191): *a phone in this game does not know its own seat*, and
     * teaching it one would make *is this me?* answerable about every seat-carrying disclosure in
     * the game, for one highlight. The boundary stays where E-R1-1 put it.
     *
     * ### Two ROSEs are both lit, and that is the honest answer rather than a limitation
     *
     * `ResidentStrip`'s KDoc used to refuse the highlight outright, on the argument that *two people
     * in a house may well both be a ROSE, and a screen that pointed at the wrong one would be worse
     * than a screen that points at none.* That argument holds against picking **one** row and is
     * what this avoids: every row matching the typed name is marked, so the screen says *one of
     * these is you* and never *that one is you*. It cannot point at the wrong person, because it
     * does not point at a person — it points at a name, which is the only thing this phone knows.
     *
     * Blank is never a match. An unnamed seat is somebody who has not typed, and marking every one
     * of them as *you* would be the highlight saying nothing on the screen it is most needed.
     */
    fun isMine(name: String): Boolean = named && name == residentName

    /** The homes on the air, in the order they answered. */
    var nearby: List<NearbyHome> by mutableStateOf(emptyList())
        private set

    /**
     * **What the screen says while no home is listed** (D-309). [Listening.Off] from the radio's
     * refusal; [Listening.NothingFound] when a look ran [NO_HOME_AFTER_MILLIS] unrefused and found
     * nothing; [Listening.Looking] otherwise.
     */
    var listening: Listening by mutableStateOf(Listening.Looking)
        private set

    /**
     * **Which look this is**: counted up each time looking begins, a restart included, so the
     * screen's timer for [NO_HOME_AFTER_MILLIS] restarts with it and a timer from an earlier look
     * changes nothing ([lookedFor]).
     */
    var looks: Int by mutableIntStateOf(0)
        private set

    /**
     * ⚠️ **[looks] and [listening] as plain fields, which is what [look] and [lookedFor] read.**
     * [look] is called inside a composition by fixtures that build a lobby in `setContent`; a read
     * of either state there subscribed that composition to it, so the screen's own [look] made it
     * build a new lobby, which looked again, for ever (`EndingScreensTest` hung on JOIN A HOME).
     * The two states are only written here; only the screen reads them.
     */
    private var lookCount = 0
    private var off = false

    /** The OS refused the current look. Reset when looking begins again. */
    private var refusedThisLook = false

    private fun hear(next: Listening) {
        off = next == Listening.Off
        listening = next
    }

    /** The home this phone attached to, or none. */
    var attached: NearbyHome? by mutableStateOf(null)
        private set

    val line: OneLine = OneLine()

    /**
     * **How the house says the lobby stands. Counts, a setting and the names; never a line.**
     *
     * Starts at nothing rather than at a plausible six, because "nobody is here yet" is the true
     * state of a phone that has not attached to anything, and a lobby that opened on a number it
     * invented — or on a name it invented — would be the device asserting a round.
     */
    var standing: LobbyBody.Standing by mutableStateOf(LobbyBody.Standing(joined = 0, linesIn = 0))
        private set

    /**
     * **The residents in this lobby, in the order the house seated them.**
     *
     * Blank for a phone that has not said what to call its owner — one entry per seat, so this is
     * `joined` long and stays that way. Nothing is filtered out here: a seat with no name is a
     * person standing in the hall who has not typed, and dropping them would make the list
     * disagree with the count printed above it.
     */
    val residents: List<String> get() = standing.names

    /**
     * **A round is running in this house and this phone is not in it** (D-273): it was seated by its
     * token after LIGHTS OUT (D-225), and joins at the next NEW ROUND. The house's word, off the
     * standing, never this phone's guess; while it holds, [standing] counts the running round.
     */
    val waitingForNextRound: Boolean get() = standing.waitingForNextRound

    /**
     * **This phone holds a seat in a running round** (D-311, the overnight review's finding 2), as its
     * link keeps it ([LobbyLink.holdsARoundSeat]). Asked fresh: a round's ending forgets it.
     */
    val holdsARoundSeat: Boolean get() = link.holdsARoundSeat()

    /** The last refusal, in the player's own words, or none. Cleared by the next thing they do. */
    var refusal: String? by mutableStateOf(null)
        private set

    /**
     * **Whether the refusal on screen is the one a host can still fix from here** (D-173, and the
     * owner's 2026-08-24 ruling that this refusal gain a control opening the settings page).
     *
     * Read off the refusal's own cause line rather than off a second field set beside it, and that
     * is the point: **the control and the sentence it acts on cannot come apart.** A boolean would
     * have to be cleared everywhere `refusal` is, and the failure of that is silent in both
     * directions — a host told to CHECK LOCAL NETWORK PERMISSION with nothing to press, or a
     * duplicate home name offering to open the OS settings for a problem that is one RENAME away on
     * the same screen.
     *
     * The other refusal is deliberately not offered it. Its fix is on this screen.
     */
    val offersTheGrant: Boolean get() = refusal?.contains(NOT_ON_THE_AIR_CAUSE) == true

    /**
     * **Send the host to this app's page in the OS settings, so they can grant and try again.**
     *
     * The one caller is the control under the hosting refusal. It answers nothing and reports
     * nothing back: whether the grant was given is a question the radio answers on the next HOST
     * WITH THIS HOME, and a second way of knowing would be a second thing to be wrong.
     */
    fun openSystemSettings() {
        settings()
    }

    /**
     * **Refused before the radio was ever asked** — D-161's setup gate, said here.
     *
     * The one caller is [FlowModel.setupIsComplete]. It writes the same field the hosting refusal
     * writes, because to a host standing on this screen there is one place refusals appear and
     * this is a refusal: they pressed a control and the party did not happen. What differs is the
     * cause, and the sentence names it.
     *
     * It is deliberately **not** offered [openSystemSettings]. The rows this refusal names are
     * fixed on the Perms screen, which is one back and has a control per row — [offersTheGrant] is
     * for the refusal whose fix is genuinely outside the app, and widening it here would send a
     * host to iOS Settings for a prompt they have simply not been shown yet.
     */
    fun refuse(why: String) {
        refusal = why
    }

    /**
     * Typing in the RESIDENT field — **and the keystroke is what persists it**.
     *
     * Written through on every character rather than at some commit point, because there is no
     * commit: the field has no button under it, and the next thing the player does is tap a network
     * row. A name saved at *join* would be lost by the phone that never got that far, which is the
     * phone this was asked for (E-PAY-7 — the app force-quit to fix a name).
     *
     * **A failed write is swallowed here and only here.** [NameStore] says why: a phone that could
     * not save its name plays the round perfectly and asks again next time, so surfacing it would
     * be a refusal on a screen where nothing was refused. That is not the homes store's bargain and
     * must not be confused with it — a save that did not happen is loud there (D-087) because a
     * house that vanished is a house somebody walked for fifteen minutes.
     */
    fun nameResident(next: String) {
        residentName = next.take(NAME_LIMIT)
        runCatching { names.write(residentName) }
        refusal = null
    }

    /**
     * Begin listening for homes. Idempotent from the screen's point of view.
     *
     * **LOCAL NETWORK IS OFF holds across a restart** (D-309) rather than flickering back to
     * LISTENING: it clears when a home answers, or when this look runs [NO_HOME_AFTER_MILLIS]
     * without being refused ([lookedFor]). A phone still refused is refused again at once.
     */
    fun look() {
        lookCount += 1
        looks = lookCount
        refusedThisLook = false
        if (!off) hear(Listening.Looking)
        finder.start(
            onFound = { found ->
                // By address and port, not by name: two households on one network may both have
                // called their home THE BUNGALOW, and the one that answered second must not vanish.
                if (nearby.none { it.address == found.address && it.port == found.port }) {
                    nearby = nearby + found
                }
                // A home answered, so the search is running: the radio is not refused.
                hear(Listening.Looking)
            },
            onRefused = {
                refusedThisLook = true
                hear(Listening.Off)
            },
        )
    }

    /**
     * **Look again from the start** (D-309): the app is back in the foreground, so someone who
     * switched Local Network on in Settings sees JOIN A HOME recover. The count restarts.
     */
    fun lookAgain() {
        finder.stop()
        look()
    }

    /**
     * **The look numbered [look] has run [NO_HOME_AFTER_MILLIS]** on the phone's clock (the screen's
     * timer). If it is still the current look and was not refused, nothing listed reads NO HOME ON
     * THIS NETWORK, and a LOCAL NETWORK IS OFF from an earlier look clears.
     */
    fun lookedFor(look: Int) {
        val current = look == lookCount && !refusedThisLook
        if (current) hear(if (nearby.isEmpty()) Listening.NothingFound else Listening.Looking)
    }

    fun stopLooking() = finder.stop()

    /**
     * **HOST WITH THIS HOME: the house comes up here, and this phone joins it — if it went on the
     * air at all.**
     *
     * The second half is the part worth writing down. A host that read its own lobby out of its
     * own desk would have counts arriving by a route no other phone uses, and the route no other
     * phone uses is the one nobody tests.
     *
     * ### Nothing here happens until the radio says the home is up (E-D3-2)
     *
     * [hosting] used to be set on the way in and the caller walked to the lobby on the same tap.
     * Both were the phone asserting a house that might not exist: an advertisement the OS refuses
     * is a home no other phone can see, and the host sat in a one-seat lobby with no reason given.
     * So this is a **refusable action** now, the shape [handOverLine] and SAVE HOME already have —
     * [onAir] is where the navigation goes, and a refusal stays on the screen the host is on with
     * [refusal] naming what happened. The failure a host can fix is named as such: a duplicate
     * home name is one RENAME away, and the rename row is on that same screen.
     *
     * ### The other refusal names its fixable cause too, and that is a ruling
     *
     * *The refusal should name the fixable cause rather than only the symptom.* The symptom line
     * is unchanged and stays first — it is true whatever went wrong — and [NOT_ON_THE_AIR_CAUSE]
     * is added under it. Both lines are the owner's as written (confirmed 2026-08-24), and the
     * refusal now carries a control that opens the page the second one names — see
     * [openSystemSettings].
     */
    fun hostHome(home: SavedHome, onAir: () -> Unit = {}) {
        // **A nameless host is refused before the radio is asked** (E-PAY-6), which is where D-161's
        // setup gate already stands and for the same reason: a house that goes on the air under a
        // host the room cannot name is a party assembled around a phone that will have to be fixed
        // later. The refusal stays on this screen, which is the shape every other refusal here has.
        if (!named) {
            refusal = NEEDS_A_NAME
            return
        }
        refusal = null
        link.host(
            home,
            onUp = {
                hosting = true
                attachTo(it)
                onAir()
            },
            onRefused = { why ->
                // Not hosting, because there is no house: whatever came up locally is not on the
                // air, and a phone drawing the host's controls over it would be offering settings
                // for a lobby nobody can join.
                hosting = false
                refusal = when (why) {
                    HostRefusal.NameAlreadyOnTheAir ->
                        "ANOTHER HOME HERE IS ALREADY CALLED ${home.name}"

                    HostRefusal.CouldNotGoOnTheAir -> "$NOT_ON_THE_AIR\n$NOT_ON_THE_AIR_CAUSE"
                }
            },
        )
    }

    /**
     * A tap on a row: attach to that home under this phone's name, and start hearing how the lobby
     * stands.
     *
     * **This is where the name leaves the phone** (D-115), and it is the only place it does.
     *
     * ### A player who typed nothing is refused, which reverses half of what this KDoc said
     *
     * It read: *a player who typed nothing joins under a blank, which the house lists as an unnamed
     * seat rather than refusing: the lobby is a lit room and the fix is to say something, not a
     * dialog.* **The clause about dialogs stands and nothing here is one** — the refusal is the row
     * going inert with a reason on it, in place, on the screen the field is on. What was wrong is
     * the rest: the fix being *to say something* requires the player to still be looking at the
     * field, and this call is the moment they stop. An unnamed seat is a person the room cannot
     * Restrain by name at a meeting whose whole mechanic is naming somebody, and the phone that
     * has to fix it has already walked into the lobby (E-PAY-6).
     *
     * The desk still tolerates a blank on its side and must go on doing so — `LobbyBody.Standing`
     * documents *empty for a phone that has not said what to call its owner*, and a host is not
     * entitled to assume every client enforces anything.
     */
    fun attachTo(home: NearbyHome) {
        if (!named) {
            refusal = NEEDS_A_NAME
            return
        }
        attached = home
        refusal = null
        // **A phone holding no house holds no standing** (D-288): the house's next standing can be
        // in flight behind the refusal that walked this phone out, and must not fill the counts back
        // in under JOIN.
        link.join(home, residentName) { if (attached != null) standing = it }
    }

    fun typeLine(next: String) {
        line.type(next)
        refusal = null
    }

    /**
     * HAND IT OVER.
     *
     * Returns whether it went, so the caller can stay put on a refusal — a screen that walked
     * away from a blank line would leave the player believing the house had something it does
     * not, and the count on the lobby would agree with them.
     */
    fun handOverLine(): Boolean {
        if (!line.isReal) {
            refusal = "ONE LINE, AND MAKE IT REAL"
            return false
        }
        link.handOver(line.text)
        line.wasHandedOver()
        refusal = null
        return true
    }

    // ---- The host's settings ------------------------------------------------------------

    /** The band the Insider setting may move in, for the seats currently in the lobby (D-103). */
    val band: IntRange get() = InsiderBand.of(standing.joined)

    /** UNKNOWN, or the count the host chose. The word is the design's, not a placeholder. */
    val insidersLabel: String get() = standing.insiders?.toString() ?: UNKNOWN

    /**
     * **SUBROUTINES EACH — `K`, asked of [Balance] rather than typed on the row** (D-129).
     *
     * The design drew a `7` and the port kept it, which was right by coincidence and only at six
     * seats: `K = ⌈M ÷ worstCasePlainResidents⌉ + slack` moves with the party and with the host's
     * Insider setting, so a literal is a number that silently stops describing the round the
     * moment a seventh person walks in. It is the same length for every seat and for both roles by
     * construction — the Insider's fake order is drawn from this same rule — so there is nothing
     * role-dependent to hide here.
     *
     * **Computed from public lobby facts alone**, which is the part that has to stay true: seats
     * and the host's *setting*, never the draw. Sizing it against the draw would let order length
     * divide out the count D-103 spent a revision hiding.
     *
     * With nobody seated there is no round to size, and the row says so rather than printing the
     * `1` the arithmetic floor produces for an empty house.
     */
    val orderSizeLabel: String
        get() =
            if (standing.joined == 0) "NOT YET"
            else Balance.orderSize(standing.joined, standing.insiders).toString()

    /**
     * The Insider-count control: **UNKNOWN, then every count in the band, then UNKNOWN again.**
     *
     * A cycle rather than a stepper because UNKNOWN is not below one or above the maximum — it is
     * a different kind of answer, and a control that made the host step *through* it to get from
     * two back to one would be a control that keeps setting it by accident.
     *
     * Nothing is predicted here: the choice goes to the house, the house clamps it, and the
     * number on screen is the one that came back. On a phone that is not hosting this does
     * nothing, and the screen does not draw it as a control.
     */
    fun cycleInsiders() {
        if (!hosting) return
        val band = band
        val next = when {
            band.isEmpty() -> null
            standing.insiders == null -> band.first
            standing.insiders!! >= band.last -> null
            else -> standing.insiders!! + 1
        }
        link.setInsiders(next)
    }

    /**
     * **The vote window, in seconds. The host's to set; this phone's to echo.**
     *
     * The design makes it a lobby setting and gives 45 as the default (`gdd.md:412`, restated at
     * `:1006`). What it does not give is a *range*, so [VOTE_WINDOWS] is this control's shape
     * rather than the design's number, and it is playtest's to rule on — the same standing this
     * screen's `7` and `120S` have.
     *
     * ### It reaches the desk, and it goes no further than the host's own phone
     *
     * It reaches [LobbyLink] now, exactly as [cycleInsiders] does, and lands on the desk a round is
     * armed from — which is what closes the gap where the row moved a number nothing read. **It
     * does not go down the wire in either direction.** The Insider count travels because the house
     * clamps it and every phone draws the clamped value; a ballot length needs neither, and the
     * clients learn it when the ballot opens, on the push that opens it.
     *
     * The value shown is therefore this phone's own rather than one that came back from the house,
     * which is the one asymmetry with the row above it. That is honest here and would not be there:
     * nothing clamps a ballot length (D-125 — it is a number the whole room watches run out), so
     * there is no second opinion for the screen to be waiting on.
     */
    var voteWindowSeconds: Int by mutableStateOf(VOTE_WINDOWS.first())
        private set

    /** The row's right-hand value, in the design's own form: `45S`. */
    val voteWindowLabel: String get() = "${voteWindowSeconds}S"

    /**
     * The vote-window control: the windows in order, then back to the first.
     *
     * A cycle rather than a stepper for the reason [cycleInsiders] is one — it is the presentation
     * family the lobby already has — though the argument is weaker here, because unlike UNKNOWN
     * none of these is a different *kind* of answer. On a phone that is not hosting this does
     * nothing, and the screen does not draw it as a control.
     */
    fun cycleVoteWindow() {
        if (!hosting) return
        val at = VOTE_WINDOWS.indexOf(voteWindowSeconds)
        voteWindowSeconds = VOTE_WINDOWS[(at + 1) % VOTE_WINDOWS.size]
        link.setVoteWindow(voteWindowSeconds)
    }

    /**
     * **Whether LIGHTS OUT may be offered: enough people, and everybody's line in.**
     *
     * Two conditions, and they fail differently on purpose — see [waitingFor], which is what the
     * screen says while one of them is open. Both are read off the house's counts rather than off
     * anything this phone decided.
     *
     * **The party floor is a gate rather than guidance**, which is the one place this screen
     * departs from D-125's *guide, don't gate*. The sorting rule is what makes it lawful: clamp
     * what players cannot perceive, guide what they can. A host can see how many people are
     * standing in their hall — that is why HOSTS UP TO N never blocks — but *this party is too
     * small for the vote to mean anything* (D-128) is a balance consequence nobody in the hall can
     * check, and it is the same kind of fact as the Insider band.
     *
     * An empty lobby is not ready twice over: "0 of 0" is true in arithmetic and false in every
     * other sense, and zero is below the floor anyway.
     */
    val readyToArm: Boolean
        get() = enoughSeated && standing.joined > 0 && standing.linesIn == standing.joined

    /** D-128's floor, read off the house's own count of who is seated. */
    val enoughSeated: Boolean get() = standing.joined >= Balance.MINIMUM_SEATS

    /**
     * **What the screen says while the gate is open**, or null once it is closed.
     *
     * The party floor is named first, because it is the one a host can do something about that
     * does not involve waiting for somebody else to finish typing — and because a lobby three
     * people short that said WAITING FOR EVERYONE'S LINE would send the host looking for a phone
     * that is not the problem.
     */
    fun waitingFor(): String? = when {
        !enoughSeated -> "NEEDS ${Balance.MINIMUM_SEATS} RESIDENTS"
        standing.linesIn < standing.joined -> "WAITING FOR EVERYONE'S LINE"
        else -> null
    }

    /**
     * **Deleted when the round ends**, on this phone and on the house's desk, and the lobby stays
     * up (D-116, D-157).
     *
     * It used to call `link.leave()`, which kept the deletion promise by taking the whole house
     * down with it — correct while the app had no way to play a second round and wrong the moment
     * NEW ROUND existed. The seats, the home and the settings survive; the text does not.
     */
    fun roundEnded() {
        line.roundEnded()
        link.roundEnded()
    }

    // ---- The ways out of a lobby ---------------------------------------------------------

    /**
     * **LEAVE — this phone stands up and walks out** (E-PAY-6: *no one is ever trapped in a lobby
     * again*).
     *
     * Everything this phone was holding about the lobby goes, and none of it is guessed at: the
     * counts are wiped rather than decremented, because a lobby this phone is no longer in has no
     * standing for it to hold a stale copy of. The house does its own arithmetic and publishes it
     * to the people still there.
     *
     * **The line goes too.** It was typed on the strength of *deleted when the round ends*, and
     * leaving before the round starts is a stronger version of the same promise — the desk drops it
     * on the lost connection (`LobbyDesk.left`) and this drops this phone's copy, independently, the
     * way `roundEnded` already drops both ends separately.
     *
     * **The name does not.** It is this phone's own and it is now stored (see [NameStore]); a
     * player who left the wrong house should not have to type themselves in again.
     */
    fun leaveLobby() {
        link.leave()
        line.roundEnded()
        attached = null
        hosting = false
        standing = LobbyBody.Standing(joined = 0, linesIn = 0)
        refusal = null
    }

    /**
     * **TERMINATE — the host closes the house on everybody** (E-PAY-6).
     *
     * Refused on a phone that is not hosting, the shape [cycleInsiders] and [cycleVoteWindow]
     * already have, and the screen does not draw the control there either — so this is the second
     * lock on a door that has one.
     *
     * It ends with this phone in the same place a leaver is: nothing attached, nothing hosted, no
     * counts. The other phones are told by the house (`LobbyBody.HouseClosed`) and answer it at
     * [houseClosed] below, which is the same landing reached by a different road.
     */
    fun terminateHouse() {
        if (!hosting) return
        link.terminate()
        line.roundEnded()
        attached = null
        hosting = false
        standing = LobbyBody.Standing(joined = 0, linesIn = 0)
        refusal = null
    }

    /**
     * **The house closed under this phone: the client's half of a TERMINATE.**
     *
     * Reached by a push and never by a control on this screen — there is no control, because the
     * host is the one who closed it. What a client owes the player is *an honest line*, and the
     * line is [HOUSE_CLOSED].
     *
     * It lands the same state [leaveLobby] does and then puts the reason on it, which is the one
     * difference between walking out and being shown out. The caller navigates — see
     * `FlowModel.houseClosed` — because where a phone goes is the flow's business and what it is
     * holding is this class's.
     */
    fun houseClosed() {
        leaveLobby()
        refusal = HOUSE_CLOSED
    }

    /**
     * **The house turned this phone away at JOIN: a round is under way and it is not in it** (D-287,
     * the owner's ruling 9c).
     *
     * [houseClosed]'s shape: the state a leaver is in, with the reason on it, on the join screen the
     * flow walks it to. The words are the waiting screen's, unchanged (the owner, 2026-11-18: *keep
     * today's*), in the join screen's one refusal slot rather than on a lobby the phone would stand
     * on, lit, for the whole round. Typing or tapping clears it, as every refusal here is cleared,
     * and tapping the home again after the round is the way in.
     */
    fun roundInProgress() {
        leaveLobby()
        refusal = "$ROUND_IN_PROGRESS\n$YOU_JOIN_THE_NEXT_ONE"
    }

    /**
     * **The house refused this phone's name: another seat here already has it** (D-288, the owner's
     * ruling 9d). The person types another, and the RESIDENT field is on JOIN, so this lands where
     * [roundInProgress] lands: nothing attached, the name still in the field to be changed, and
     * [NAME_TAKEN] in the refusal slot under it. The next keystroke clears it ([nameResident]), and
     * tapping the home walks back in under the new name.
     */
    fun nameTaken() {
        leaveLobby()
        refusal = NAME_TAKEN
    }

    companion object {
        const val UNKNOWN: String = "UNKNOWN"

        /**
         * **A round is running and this phone is not in it** (D-273's words; the owner kept them for
         * the JOIN refusal, 2026-11-18). Drawn on the join screen when the house refuses this phone
         * at the door (D-287), and by the waiting lobby of a phone seated outside a round.
         */
        const val ROUND_IN_PROGRESS: String = "ROUND IN PROGRESS"

        /** The second line under [ROUND_IN_PROGRESS], and under WAITING FOR THE HOST (D-273, D-274). */
        const val YOU_JOIN_THE_NEXT_ONE: String = "YOU JOIN THE NEXT ONE"

        /**
         * **Another seat in this lobby already has the name this phone sent** (D-288). The owner's
         * approved words, 2026-11-18, with no second line: the field it is about is right above it.
         */
        const val NAME_TAKEN: String = "NAME TAKEN"

        /**
         * ⚠️ **PROPOSED COPY, NOT RATIFIED.** The owner asked for *an honest line* and did not write
         * one; this is the draft, flagged here rather than shipped quietly, in the register the rest
         * of the refusals on this screen are written in.
         *
         * It says what happened and does not editorialise about why. The host is standing in the
         * room — a client's screen is the wrong place to explain a decision the person who made it
         * can explain out loud, and a sentence guessing at their reason would be the app inventing
         * one. *Closed* rather than *ended*: the round-ending line is `LobbyBody.RoundEnded`'s and
         * means something else entirely, and the two must not read alike.
         *
         * Nothing about the wire depends on these words — `LobbyBody.HouseClosed` carries no
         * field — so ratifying different ones is an edit to this constant and to nothing else.
         */
        const val HOUSE_CLOSED: String = "THE HOST CLOSED THE HOUSE"

        /**
         * **What the two gated controls say while this phone has no name** (E-PAY-6).
         *
         * On the row rather than in a dialog, and the same sentence on both, because it is the same
         * missing fact: see [named]. It names the field the player has to go and fill rather than
         * only the refusal, for the reason `NOT_ON_THE_AIR_CAUSE` names a permission — a refusal a
         * person can act on is worth more than one that is merely true.
         */
        const val NEEDS_A_NAME: String = "TYPE YOUR NAME FIRST"

        /**
         * **The symptom: this home never went up, whatever the reason** (D-173).
         *
         * Ruled, and honest in a way the line under it cannot be: the advertisement did not
         * succeed, and that is the whole of what this phone knows for certain. It stays first and
         * it stays unchanged.
         */
        const val NOT_ON_THE_AIR: String = "THIS HOME DID NOT GO ON THE AIR"

        /**
         * **The fixable cause, named rather than only the symptom** (the owner's 2026-08-22
         * ruling).
         *
         * ### The wording is confirmed (the owner's 2026-08-24 ruling)
         *
         * The 2026-08-22 ruling was the *shape* — symptom, then the cause a person can act on — and
         * it did not dictate the words; these were drafted within the register and carried a flag
         * through two revisions. The owner kept both lines as written and added the control that
         * opens the page this one names, so what was a draft is the refusal.
         *
         * ### It points rather than asserts, and that is not hedging
         *
         * With the Local Network grant gone, iOS publishes nothing and **reports nothing** — no
         * error, no prompt, no callback of any kind (E-D3-4). So `CouldNotGoOnTheAir` covers both
         * a refusal the OS gave a reason for and ten seconds of silence it gave nothing for, and
         * **this phone genuinely cannot know which happened.** A line asserting the permission was
         * the cause would be the app stating a fact it does not have, on a screen a host acts on.
         * *Check* is the strongest verb the evidence supports, and it is enough: the host is
         * holding the phone in a lit room with the party in front of them, and two taps from a
         * person cleared exactly this on the night it was first seen.
         *
         * **LOCAL NETWORK is iOS's own name for the grant**, the heading on the switch in Settings,
         * so the host is sent to words they will find there rather than to a term this screen
         * invented. (The setup walk listed it too, until D-309 took that row away.)
         */
        const val NOT_ON_THE_AIR_CAUSE: String = "CHECK LOCAL NETWORK PERMISSION"

        /**
         * **How long JOIN A HOME looks before it says NO HOME ON THIS NETWORK**: about ten seconds
         * (D-309). ⚠️ Provisional, the owner's *about 10 s*. Counted on the phone's clock from when
         * looking began, and again from every restart.
         */
        const val NO_HOME_AFTER_MILLIS: Long = 10_000L

        /**
         * **The windows the host may choose between, first one being the design's default.**
         *
         * 45 is `gdd.md:412` and is not negotiable here. The other three are the control's range,
         * which the design never states, chosen to bracket the default rather than to say
         * anything: half a minute for a house that already knows who it suspects, ninety seconds
         * for one that changes its mind. **Playtest owns these three**, the way it owns the
         * subroutine count on the row above.
         *
         * Ordered, and read by index — the list *is* the cycle.
         */
        val VOTE_WINDOWS: List<Int> = listOf(45, 60, 90, 30)

        /**
         * The bound on a name, which is [LobbyBody.Naming.LIMIT] and not a second opinion about
         * it. The host applies the same number to whatever arrives, so a field that let more be
         * typed would silently truncate on somebody else's screen.
         */
        const val NAME_LIMIT: Int = LobbyBody.Naming.LIMIT

        /**
         * **The fixture: the design's three networks, and a lobby with six in it and four lines
         * handed over.**
         *
         * Every screen render and every rendering test gets this, for the reason
         * `SavedHomesModel.sample()` exists — a lobby with nothing in it is a screen the app never
         * shows, and a test looking at one proves nothing about the screen that ships.
         *
         * It is *hosting* and it is *attached*, which is not a contradiction: the host's phone
         * runs the house and then joins it over loopback like everybody else. The host is a
         * player too, and a host that talked to its own lobby through a side door would be a
         * second code path nobody exercises.
         */
        fun sample(): LobbyModel {
            val model = LobbyModel(
                finder = MemoryHomeFinder(
                    listOf(
                        NearbyHome("THE BUNGALOW", "192.168.1.24", 47747),
                        NearbyHome("MUM & DAD'S", "192.168.1.31", 47747),
                        NearbyHome("FLAT 6", "192.168.1.9", 47747),
                    ),
                ),
                link = MemoryLobbyLink(joined = 6, linesIn = 4),
                hosting = true,
            )
            model.look()
            // Named before attaching, because that is the order the screens are in and because
            // the name is what attaching carries up. A sample that skipped it would draw a lobby
            // with an unnamed seat in it and teach the wrong thing about the common case.
            model.nameResident("ELLIOT")
            model.attachTo(model.nearby.first())
            // The design's own line, so the screen that asks for one can be looked at with
            // something in it. It is typed and NOT handed over: the lobby row reads REQUIRED,
            // which is the state the design drew.
            model.typeLine("i still have priya's spare key")
            return model
        }
    }
}

/**
 * The lobby the cold-start and arming screens draw.
 *
 * Provided by [Screen] beside [LocalHomes] and for the same reason: a test that hands a line over
 * must not leave the next render looking at a lobby that is already one line further on.
 */
val LocalLobby: ProvidableCompositionLocal<LobbyModel> =
    staticCompositionLocalOf { LobbyModel.sample() }
