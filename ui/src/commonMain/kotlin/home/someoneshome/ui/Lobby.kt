package home.someoneshome.ui

import home.someoneshome.model.InsiderBand
import home.someoneshome.model.protocol.LobbyBody

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
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
    /** Begin looking. [onFound] fires once per home found, on whatever thread found it. */
    fun start(onFound: (NearbyHome) -> Unit)
    fun stop()
}

/**
 * **The wire, as far as `ui` is allowed to know about it.**
 *
 * Three verbs and one shape coming back. Note what is *not* here: there is no way to ask this
 * interface who else is in the lobby, because [LobbyBody.Standing] is what comes back and it is
 * counts. A screen that wanted a name would have to be given a way to ask for one, and that is a
 * decision about what a client receives — not something a lobby screen quietly acquires.
 */
interface LobbyLink {

    /**
     * Run the house on this phone, under this home's name, and report where it came up.
     *
     * The caller then [join]s it like any other phone. That is not ceremony: the host is a player
     * too, and a host that talked to its own lobby through a side door would be a second code
     * path nobody exercises — with the counts on the host's screen coming from somewhere the
     * other five phones' counts do not.
     */
    fun host(homeName: String, onUp: (NearbyHome) -> Unit)

    /** Attach to a home. [onStanding] fires every time the house says how the lobby stands. */
    fun join(home: NearbyHome, onStanding: (LobbyBody.Standing) -> Unit)

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

    fun leave()
}

/**
 * The finder every test and every render gets: a fixed list, handed over at once.
 *
 * Deliberately not a no-op. A finder that found nothing would leave the one screen whose job is
 * to show what is nearby with nothing on it, and every rendering test would then be looking at a
 * screen the app never shows.
 */
class MemoryHomeFinder(private val homes: List<NearbyHome> = emptyList()) : HomeFinder {
    override fun start(onFound: (NearbyHome) -> Unit) = homes.forEach(onFound)
    override fun stop() = Unit
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
) : LobbyLink {

    private var standing = LobbyBody.Standing(joined = joined, linesIn = linesIn)
    private var listener: (LobbyBody.Standing) -> Unit = {}

    /** The lines this fixture has been handed, so a test can prove one arrived. Never persisted. */
    val received = mutableListOf<String>()

    override fun host(homeName: String, onUp: (NearbyHome) -> Unit) {
        // Loopback, because that is what the real one does: the house runs here and this phone
        // joins it over the local interface like everybody else.
        onUp(NearbyHome(homeName, "127.0.0.1", 0))
    }

    override fun join(home: NearbyHome, onStanding: (LobbyBody.Standing) -> Unit) {
        listener = onStanding
        publish()
    }

    override fun handOver(line: String) {
        received += line
        standing = standing.copy(linesIn = minOf(standing.joined, standing.linesIn + 1))
        publish()
    }

    override fun setInsiders(chosen: Int?) {
        standing = standing.copy(insiders = InsiderBand.clamp(standing.joined, chosen))
        publish()
    }

    override fun leave() {
        listener = {}
    }

    private fun publish() = listener(standing)
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
         */
        const val LIMIT = 120
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
     * This phone's own resident name, typed on the way in.
     *
     * **It does not leave the device in this unit.** Nothing on the far side reads it yet — who is
     * in a round and what the house calls them is the loop's, and the loop is frozen — and text
     * that identifies a person, sent to a host that has no lifecycle for it, is a thing to be
     * asked about rather than started quietly. See the worklog.
     */
    var residentName: String by mutableStateOf("")
        private set

    /** The homes on the air, in the order they answered. */
    var nearby: List<NearbyHome> by mutableStateOf(emptyList())
        private set

    /** The home this phone attached to, or none. */
    var attached: NearbyHome? by mutableStateOf(null)
        private set

    val line: OneLine = OneLine()

    /**
     * **How the house says the lobby stands. Counts and a setting; never a name.**
     *
     * Starts at nothing rather than at a plausible six, because "nobody is here yet" is the true
     * state of a phone that has not attached to anything, and a lobby that opened on a number it
     * invented would be the device asserting a round.
     */
    var standing: LobbyBody.Standing by mutableStateOf(LobbyBody.Standing(joined = 0, linesIn = 0))
        private set

    /** The last refusal, in the player's own words, or none. Cleared by the next thing they do. */
    var refusal: String? by mutableStateOf(null)
        private set

    fun nameResident(next: String) {
        residentName = next.take(NAME_LIMIT)
        refusal = null
    }

    /** Begin listening for homes. Idempotent from the screen's point of view. */
    fun look() {
        finder.start { found ->
            // By address and port, not by name: two households on one network may both have
            // called their home THE BUNGALOW, and the one that answered second must not vanish.
            if (nearby.none { it.address == found.address && it.port == found.port }) {
                nearby = nearby + found
            }
        }
    }

    fun stopLooking() = finder.stop()

    /**
     * **HOST WITH THIS HOME: the house comes up here, and this phone joins it.**
     *
     * The second half is the part worth writing down. A host that read its own lobby out of its
     * own desk would have counts arriving by a route no other phone uses, and the route no other
     * phone uses is the one nobody tests.
     */
    fun hostHome(homeName: String) {
        hosting = true
        refusal = null
        link.host(homeName) { attachTo(it) }
    }

    /** A tap on a row: attach to that home and start hearing how the lobby stands. */
    fun attachTo(home: NearbyHome) {
        attached = home
        refusal = null
        link.join(home) { standing = it }
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
     * **Whether LIGHTS OUT may be offered: everybody here, everybody's line in.**
     *
     * An empty lobby is not ready — "0 of 0" is true in arithmetic and false in every other
     * sense — and this is read off the house's counts rather than off anything this phone
     * decided. The transition it gates is presentation only for now; see [PanelActions.lightsOut].
     */
    val everyLineIn: Boolean
        get() = standing.joined > 0 && standing.linesIn == standing.joined

    /** **Deleted when the round ends.** */
    fun roundEnded() {
        line.roundEnded()
        link.leave()
    }

    companion object {
        const val UNKNOWN: String = "UNKNOWN"

        /**
         * The field is one line on a 300-unit panel and the design's own name is six characters.
         * Past this it stops being readable across a dark hall, which is the only thing a name in
         * this game is for.
         */
        const val NAME_LIMIT: Int = 14

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
