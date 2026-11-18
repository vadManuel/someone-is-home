package home.someoneshome.app

import home.someoneshome.platform.launchSwitch
import home.someoneshome.platform.launchValue
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import home.someoneshome.ui.ScreenId
import kotlinx.coroutines.delay

/**
 * **The two-phone evening, driven by the switches `devicectl` can set — because nobody can tap a
 * phone from here.**
 *
 * D1's haptic bench exists because a buzz has no picture; this exists because a *second phone* has
 * no finger. Discovery, joining, a name going up and a line being handed over are four events that
 * each need a person on one device while a person is on the other, and an evening driven from a
 * Mac has neither. So the switches supply the three things a finger would have supplied — what to
 * call this resident, which home to be or look for, and what the line says — and everything after
 * that is the app's own path.
 *
 * ### It taps what a finger taps, and nothing else
 *
 * Every step below is a call the screens already make: [FlowModel.go] is the navigator,
 * `lobby.nameResident` is the field on JOIN, `lobby.hostHome` is HOST WITH THIS HOME,
 * [FlowModel.attachToHome] is a tap on a network row, [FlowModel.handOverLine] is HAND IT OVER,
 * `lobby.cycleInsiders` is the INSIDERS row and [FlowModel.lightsOut] is the slate button. There is
 * no second route into the lobby here and no state this reaches that a screen does not — which is
 * what makes the log below evidence about the app rather than about the bench.
 *
 * ### What it prints, and the one thing it will not
 *
 * The lobby's numbers are read back out of the same [FlowModel.lobby] the composition draws from,
 * so a line saying `4 JOINED · 3 LINES IN` is the text on the screen and not a second opinion about
 * it. **The one line itself is never printed** — not its text, not a prefix of it, not at any log
 * level — because a line in a device log is a line on a phone, and the whole promise is that the
 * house is the only thing that ever sees it. What is printed is that one went, and how long it was.
 *
 * A log is still not a photograph. It says what the screen was told to draw; only a person looking
 * at the phone says what the screen drew.
 */
class LobbyCheat(
    private val flow: FlowModel,
    private val watch: LineWatch,
    private val onScreen: (String) -> Unit,
) {

    private val lobby get() = flow.lobby

    /** True when this process was launched to run an evening rather than to be tapped. */
    val armed: Boolean = launchValue(HOST_SWITCH) != null || launchValue(JOIN_SWITCH) != null

    /**
     * **The whole evening, once**, from the switches this process was launched with.
     *
     * It never gives up and never throws: a phone that cannot find a home sits on JOIN saying so,
     * which is the same thing it does for a person, and the log says how long it has been saying
     * it. A bench that exited on a timeout would turn *the network was slow* into *the app failed*.
     */
    suspend fun run() {
        val name = launchValue(NAME_SWITCH).orEmpty()
        val hosting = launchValue(HOST_SWITCH)
        note("EVENING — ${if (hosting != null) "HOSTING '$hosting'" else "JOINING"} AS '$name'")
        if (launchSwitch(FORGET_SWITCH)) note("TOKEN WAS FORGOTTEN BEFORE THE LINK WAS BUILT")

        // The name is typed on JOIN, one screen before it leaves the phone, which is the order the
        // screens are in. Doing it here rather than after attaching matters: `attachTo` is what
        // carries it up (D-115), so a name typed afterwards would never be sent.
        lobby.nameResident(name)
        flow.go(ScreenId.Join)
        // The same call JoinScreen makes when it is composed. Made here as well because a bench
        // must not depend on a composition having happened — and it is idempotent on both sides.
        lobby.look()

        // The fifth thing a finger would have supplied. See [walkSetup].
        walkSetup()

        var joining: NearbyHome? = null
        if (hosting != null) {
            if (!seedTheHome(hosting)) return
            // HOST WITH THIS HOME, through the action the button calls — which now walks to the
            // lobby only once the home is really on the air, and stays put with a reason when it
            // is not (E-D3-2). The bench reports whichever happened rather than deciding: a run
            // that printed HOSTING over a home the radio refused would be the bench inventing the
            // one fact this ruling exists to surface.
            flow.hostHome(hosting)
            if (!waitForTheAir(hosting)) return
        } else {
            val wanted = launchValue(JOIN_SWITCH).orEmpty()
            val home = waitForHome(wanted)
            joining = home
            note("ATTACHING TO '${home.name}' AT ${home.address}:${home.port}")
            flow.attachToHome(home)
            flow.go(ScreenId.Lobby)
        }

        // Seated, or still trying. `joined` moving off zero is the house counting this phone, and
        // it is the first thing on this path that could not have happened locally.
        awaitSeat(joining)
        awaitTheNextRound()

        handOverLine()
        cycleInsiders()
        watch(armed = lightsOut())
    }

    /**
     * **The fifth thing a finger would have supplied: a host tapping through setup, in the light.**
     *
     * [seedTheHome]'s shape exactly, and for the same reason. A round is played on a phone whose
     * owner stood on the permissions screen weeks ago and answered five system modals; a process
     * launched by `simctl` has nobody to answer them. So the tap is what is stood in for — **and
     * only the tap.** What this calls is `PermsModel.requestMissing`, which is REQUEST WHAT IS
     * MISSING's own call, which reaches the real seam and raises the OS's real prompts. A bench
     * that arranged the rows directly would prove nothing about the path a grant actually travels,
     * and would put the gate back where only one caller can see it.
     *
     * ### It reports and it does not decide
     *
     * No refusal is raised here and the evening is not ended here. `hostHome` and `attachToHome`
     * each ask the gate themselves a moment later, off a fresh OS read, and whatever they say is
     * the result. Two gates in one path would be two opinions about one phone.
     *
     * ### What it waits for, and why the wait is bounded
     *
     * Three of the five answer through a callback and two of those can be a modal standing on the
     * glass with nobody in the room — a simulator has no thumb for `Would Like to Send You
     * Notifications`. So the wait ends on the clock as well as on the answer, and what is printed
     * is the OS's own reading either way. **A row still missing here is a row the log names**, and
     * the refusal that follows will name it again.
     */
    private suspend fun walkSetup() {
        val perms = flow.perms
        if (perms.missingNow().isEmpty()) {
            note("SETUP IS ALREADY DONE ON THIS PHONE — EVERY GRANT THE EVENING NEEDS IS GIVEN")
            return
        }
        note(
            "SETUP — TAPPING REQUEST WHAT IS MISSING FOR " +
                perms.missingNow().joinToString(" · ") { it.label } +
                " · THE MODALS A HOST WOULD HAVE ANSWERED"
        )
        perms.requestMissing()
        var waited = 0L
        while (waited < SETUP_MILLIS && perms.missingNow().isNotEmpty()) {
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        val short = perms.missingNow()
        note(
            if (short.isEmpty()) "SETUP DONE — THE OS ANSWERED EVERY ROW · ${waited / 1000}S"
            else "SETUP INCOMPLETE AFTER ${waited / 1000}S — STILL MISSING " +
                short.joinToString(" · ") { it.label }
        )
    }

    /**
     * **The fourth thing a finger would have supplied: a home on this phone's disk.**
     *
     * A round is armed in the home the host is hosting (E-PAY-3), so a phone with nothing stored
     * has nothing to host and there is no evening. On a real phone the host walked their house
     * with a camera weeks ago; a simulator has no lens and no person, so the walk is what is stood
     * in for — and **only the walk**. What this calls is `SavedHomesModel.save`, which is SAVE
     * HOME's own call, which writes through the real document to the real file. A run that faked
     * the store instead would prove nothing about the path the host's home actually travels.
     *
     * ### A home already on this phone is never overwritten
     *
     * This fires only when the list has nothing under that name. On the 13 Pro that matters: the
     * owner's own MP RESIDENCE was walked on hardware with real cards, and a bench that wrote over
     * it would delete fifteen minutes of somebody's evening to make room for a fixture. Where a
     * home is already there, the run hosts **theirs**, which is the better evidence anyway.
     *
     * A refused save ends the evening here, on the screen a person would be looking at, rather
     * than falling through to a hosting attempt that can only be refused for a second reason.
     */
    private fun seedTheHome(name: String): Boolean {
        flow.homes.homes.firstOrNull { it.name == name }?.let {
            note(
                "'$name' IS ALREADY ON THIS PHONE — ${it.roomCount} ROOM(S), " +
                    "${it.markerCount} MARKER(S) · HOSTING WHAT IS STORED"
            )
            return true
        }
        // MAP A NEW HOME first, so the save adds rather than replacing whatever was last open.
        flow.homes.closeHome()
        val home = SeededHome.named(name)
        if (!flow.homes.save(home)) {
            note("THE WALK WAS NOT SAVED — ${flow.homes.refusal}")
            return false
        }
        note(
            "SEEDED '$name' ONTO THIS PHONE — ${home.roomCount} ROOM(S), " +
                "${home.markerCount} MARKER(S) · THE CAMERA WALK, WHICH A SIMULATOR CANNOT DO"
        )
        return true
    }

    /**
     * **Whether this phone's own home went on the air**, which is now the thing that decides
     * whether there is a lobby at all (E-D3-2).
     *
     * The screen is the answer: `hostHome` walks to the lobby when the advertisement is published
     * and stays on the saved-home screen with `lobby.refusal` set when it is refused. So this
     * watches both and prints whichever arrived. **A refusal ends the evening here**, on the
     * screen a person would be looking at, rather than falling through to a seat wait that would
     * print NOT SEATED YET for as long as the phone is up and say nothing about why.
     */
    private suspend fun waitForTheAir(homeName: String): Boolean {
        var waited = 0L
        while (flow.state.screen != ScreenId.Lobby) {
            lobby.refusal?.let {
                note("NOT HOSTING — $it · ON ${flow.state.screen.name.uppercase()}")
                return false
            }
            if (waited > 0 && waited % PATIENCE_MILLIS == 0L) {
                note("'$homeName' HAS NOT GONE ON THE AIR YET — ${waited / 1000}S")
            }
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        note("HOSTING '$homeName' — ON THE AIR · WAITING FOR THIS PHONE'S OWN SEAT")
        return true
    }

    /**
     * The homes on the air, as they answer, until one of them is the one being looked for.
     *
     * [wanted] is a substring and an empty one matches the first home found — two households on one
     * network may both have called theirs THE BUNGALOW, and an evening driven from a Mac has to be
     * able to say which. The list itself is the lobby's own, deduped by address and port by it.
     */
    private suspend fun waitForHome(wanted: String): NearbyHome {
        var seen = 0
        var waited = 0L
        while (true) {
            if (lobby.nearby.size > seen) {
                lobby.nearby.drop(seen).forEach { note("SAW '${it.name}' AT ${it.address}:${it.port}") }
                seen = lobby.nearby.size
            }
            lobby.nearby.firstOrNull { wanted.isEmpty() || it.name.contains(wanted, ignoreCase = true) }
                ?.let { return it }
            if (waited > 0 && waited % PATIENCE_MILLIS == 0L) {
                note("STILL LOOKING${if (wanted.isEmpty()) "" else " FOR '$wanted'"} — ${waited / 1000}S")
            }
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
    }

    /**
     * The house counting this phone. Until then the lobby says NOBODY HAS JOINED YET, truthfully.
     *
     * **Refused at the door while a round runs** (D-287), the phone stands on JOIN saying ROUND IN
     * PROGRESS, and the bench taps the home again every [PATIENCE_MILLIS], as a person told *you
     * join the next one* would look up now and then; the round's ending opens the door.
     */
    private suspend fun awaitSeat(home: NearbyHome?) {
        var waited = 0L
        while (lobby.standing.joined == 0) {
            if (waited > 0 && waited % PATIENCE_MILLIS == 0L) {
                val refused = lobby.refusal
                if (home != null && lobby.attached == null && refused?.startsWith(LobbyModel.ROUND_IN_PROGRESS) == true) {
                    note("REFUSED AT THE DOOR — ${refused.replace('\n', ' ')} · TAPPING THE HOME AGAIN")
                    flow.attachToHome(home)
                    flow.go(ScreenId.Lobby)
                } else {
                    note("NOT SEATED YET — ${waited / 1000}S${refused?.let { " · ${it.replace('\n', ' ')}" }.orEmpty()}")
                }
            }
            delay(STEP_MILLIS)
            waited += STEP_MILLIS
        }
        note("SEATED — ${standing()}")
    }

    /**
     * **Seated while a round this phone is not in runs** (D-273): its screen offers no line, so the
     * bench types none until the house's NEW ROUND brings it into the next lobby, as a person would.
     */
    private suspend fun awaitTheNextRound() {
        if (!lobby.waitingForNextRound) return
        note("A ROUND IS RUNNING WITHOUT THIS PHONE — ${standing()} · WAITING FOR THE NEXT ONE")
        while (lobby.waitingForNextRound) delay(STEP_MILLIS)
        note("THE NEXT ROUND'S LOBBY — ${standing()}")
    }

    /**
     * YOUR ONE LINE, then HAND IT OVER — through the screen that asks for it.
     *
     * The length is printed and the text is not. That is not squeamishness about a bench: the two
     * failures worth telling apart here are *nothing was typed* and *something was typed and did not
     * arrive*, and a count of characters separates them without putting anybody's confession in a
     * file on a Mac.
     */
    private suspend fun handOverLine() {
        val line = launchValue(LINE_SWITCH) ?: return
        // Told to the probe before it is typed, so a body arriving between the two is still checked.
        watch.mine(line)
        flow.go(ScreenId.Secret)
        lobby.typeLine(line)
        note("ONE LINE TYPED — ${lobby.line.text.length} CHAR(S), NOT PRINTED HERE")
        flow.handOverLine()
        // The refusal a blank line gets is the screen's own, and it is a pass when a blank was what
        // was sent: the bench reports what the screen says rather than deciding it was wrong.
        lobby.refusal?.let { note("REFUSED — $it") }
        note("HANDED OVER=${lobby.line.handedOver} · ON ${flow.state.screen.name.uppercase()}")
        // The count coming back is the house's answer, and it arrives around the socket.
        delay(SETTLE_MILLIS)
        note("AFTER THE HANDOVER — ${standing()}")
    }

    /**
     * The INSIDERS row, tapped as many times as asked, with the band printed beside each answer.
     *
     * Every number logged is the one that came *back* from the house. The bench predicts nothing:
     * the point of the row is that the desk clamps it, so a bench that printed what it asked for
     * would be printing the one number that is not evidence.
     */
    private suspend fun cycleInsiders() {
        val taps = launchValue(INSIDERS_SWITCH)?.toIntOrNull() ?: return
        note("INSIDERS — $taps TAP(S), BAND ${band()}")
        repeat(taps) {
            lobby.cycleInsiders()
            delay(SETTLE_MILLIS)
            note("INSIDERS = ${lobby.insidersLabel} · BAND ${band()} · HOSTING=${lobby.hosting}")
        }
    }

    /**
     * **LIGHTS OUT, including — especially — when the house refuses it.**
     *
     * A refusal is the result, not a failure to get one: with fewer than the minimum seated the
     * screen draws the reason under a inert button, and that is the gate working. So the bench asks
     * the model the same two questions the screen asks it and prints both answers, and presses only
     * if the button would have been live. **Pressing a button the screen does not offer would be
     * the bench inventing a tap no finger could have made**, and the log would then say a two-seat
     * party armed a round.
     *
     * Called again from [watch] every time the room changes, which is a host standing with a thumb
     * over the button waiting for the last person to walk in — not a retry loop. Returns whether
     * the lights went out, so the watch stops asking once they have.
     */
    private fun lightsOut(): Boolean {
        if (!launchSwitch(LIGHTS_OUT_SWITCH)) return false
        val waiting = lobby.waitingFor()
        note("LIGHTS OUT — READY=${lobby.readyToArm} · ${waiting?.let { "REFUSED: $it" } ?: "NO REFUSAL"}")
        if (!lobby.hosting) {
            // A client is told WAITING FOR THE HOST and given no button at all.
            note("NOT THE HOST — NO BUTTON ON THIS PHONE")
            return false
        }
        if (!lobby.readyToArm) {
            note("THE BUTTON IS INERT — THIS PHONE STAYS ON ${flow.state.screen.name.uppercase()}")
            return false
        }
        // A host waiting for the whole party rather than the minimum: the button is live at three,
        // and a thumb that pressed it then would arm a round the fourth phone never sat down in.
        val party = (if (roundsPlayed > 0) launchValue(SEATS_AGAIN_SWITCH) else null)?.toIntOrNull()
            ?: launchValue(SEATS_SWITCH)?.toIntOrNull()
        if (party != null && lobby.standing.joined < party) {
            note("THE BUTTON IS LIVE — WAITING FOR $party SEATED, ${lobby.standing.joined} SO FAR")
            return false
        }
        flow.lightsOut()
        note("ARMED THIS PHONE ONLY — ON ${flow.state.screen.name.uppercase()}")
        return true
    }

    /**
     * The lobby, out loud, for as long as the phone is up.
     *
     * The evening's other half happens on somebody else's device: a fifth phone joining, a line
     * arriving, the host moving the setting. None of it produces an event on this phone except a
     * standing coming down the socket, so this is what turns the far side into something the log
     * on this side can be read for.
     *
     * Only changes are printed. A line per second for twenty minutes is a log nobody reads, and the
     * whole question being asked of this one is *what moved, and when*.
     */
    /** Rounds this phone has played and come back to the lobby from. See [SEATS_AGAIN_SWITCH]. */
    private var roundsPlayed = 0

    private suspend fun watch(armed: Boolean) {
        var lit = armed
        var last = ""
        // **A second round, by the same fingers** ([AGAIN_SWITCH], D-252). Set when this phone is
        // anywhere but the lobby's two screens, which after the handover is only ever a round.
        var played = false
        val again = launchSwitch(AGAIN_SWITCH)
        while (true) {
            if (flow.state.screen != ScreenId.Lobby && flow.state.screen != ScreenId.Secret) played = true
            // NEW ROUND brought this phone back with its line wiped, as promised. A person types it
            // again, and the host's thumb goes back over LIGHTS OUT.
            if (again && played && flow.state.screen == ScreenId.Lobby && !lobby.line.handedOver) {
                played = false
                roundsPlayed++
                note("BACK IN THE LOBBY AFTER A ROUND — HANDING THE LINE OVER AGAIN")
                handOverLine()
                lit = false
            }
            val now = "${standing()} · ${lobby.waitingFor() ?: "READY"} · ${flow.state.screen.name.uppercase()}"
            if (now != last) {
                note(now)
                // Beside every change, not once at the end: this bench has no end — the app runs
                // until somebody stops it — so a verdict printed after the loop is a verdict that
                // never gets printed.
                note(watch.verdict())
                last = now
                if (!lit && lobby.readyToArm) lit = lightsOut()
            }
            delay(WATCH_MILLIS)
        }
    }

    /**
     * The four things the lobby screen prints about the room, in one line.
     *
     * Read out of the model the composition reads, so this is the screen's own text rather than a
     * report about it — see the class KDoc. The names are in it because the house sends them to
     * every phone (D-115) and the strip draws them; the lines are a count and stay one.
     */
    private fun standing(): String {
        val s = lobby.standing
        val who = s.names.map { it.ifBlank { "UNNAMED" } }
        // The waiting phone's own header (D-273): the round's count, said as the round's.
        if (s.waitingForNextRound) return "${s.joined} IN THIS ROUND · ROUND IN PROGRESS · $who"
        return "${s.joined} JOINED · ${s.linesIn} OF ${s.joined} HANDED THEIRS OVER · " +
            "INSIDERS ${lobby.insidersLabel} · SUBROUTINES EACH ${lobby.orderSizeLabel} · $who"
    }

    private fun band(): String = lobby.band.let { if (it.isEmpty()) "EMPTY" else "${it.first}..${it.last}" }

    /**
     * Both places, for [HapticCheat]'s reason: the screen is for whoever is holding the phone, and
     * stdout is the device console, which is the only evidence a unit with no hand on a phone can
     * produce. The rig's log is where the lobby's own discovery events already land.
     */
    private fun note(line: String) {
        println("[lobby-bench] $line")
        onScreen(line)
    }

    companion object {

        /** Host under this home's name. The name goes on the air; nothing else does. */
        const val HOST_SWITCH = "SOMEONES_HOME_LOBBY_HOST"

        /** Join the first home found whose name contains this. Empty matches the first to answer. */
        const val JOIN_SWITCH = "SOMEONES_HOME_LOBBY_JOIN"

        /** What to call the resident holding this phone. Empty is a seat nobody has spoken for. */
        const val NAME_SWITCH = "SOMEONES_HOME_LOBBY_NAME"

        /** The one line. Absent means this phone hands nothing over, which is a state worth having. */
        const val LINE_SWITCH = "SOMEONES_HOME_LOBBY_LINE"

        /** How many times to tap the INSIDERS row. Host only; on a client the row is a reading. */
        const val INSIDERS_SWITCH = "SOMEONES_HOME_LOBBY_INSIDERS"

        /**
         * **After NEW ROUND, hand the line over again and, on the host, press LIGHTS OUT again**
         * (D-252). How a hunt shows that a second round arms; absent, the bench plays one round.
         */
        const val AGAIN_SWITCH = "SOMEONES_HOME_LOBBY_AGAIN"

        /** Ask for LIGHTS OUT at the end — and report the refusal when the house has one. */
        const val LIGHTS_OUT_SWITCH = "SOMEONES_HOME_LOBBY_LIGHTS_OUT"

        /**
         * With [LIGHTS_OUT_SWITCH]: press it only once this many are seated. Absent, the bench
         * presses as soon as the button is live, which is at `Balance.MINIMUM_SEATS`.
         */
        const val SEATS_SWITCH = "SOMEONES_HOME_LOBBY_SEATS"

        /**
         * With [AGAIN_SWITCH]: [SEATS_SWITCH] for every round after the first. How a hunt arms a
         * second round without a guest it stopped at the first one's ending (V1, D-267).
         */
        const val SEATS_AGAIN_SWITCH = "SOMEONES_HOME_LOBBY_SEATS_AGAIN"

        /**
         * **Drop the stored seat token before anything is built. The rig's FORGET, as a switch.**
         *
         * Acted on in `CheatRoot` and not here, and that ordering is the point:
         * [LobbyOverTransport] reads the stored token **in its constructor**, so a clear that runs
         * from a `LaunchedEffect` happens one composition too late — the token on disk is gone and
         * the session in memory still presents it. The first version of this switch did exactly
         * that and reported `TOKEN FORGOTTEN` over a phone that then failed to seat.
         *
         * **It came from a bug and outlived it.** E-D3-1 — a phone presenting a stored token to a
         * house that never issued it, refused in silence on every surface there is — is ruled and
         * fixed: the phone now drops the stale token and joins fresh by itself, invisibly, so a
         * second evening needs no switch and no uninstall. What this is still good for is the
         * *other* half of that: it makes a run start as a phone that has never played, which is
         * the only way to tell a fresh join from a resume in a log where both end up seated.
         */
        const val FORGET_SWITCH = "SOMEONES_HOME_LOBBY_FORGET"

        /** How often the waits look again. Fast enough to catch an arrival, idle enough to ignore. */
        private const val STEP_MILLIS = 500L

        /** How long a wait goes before it says out loud that it is still waiting. */
        private const val PATIENCE_MILLIS = 5_000L

        /**
         * How long setup is given before the evening goes on without it.
         *
         * Long enough for five serial modals to be raised and answered by an OS that is going to
         * answer them, short enough that a modal nobody can dismiss does not hold the whole run —
         * the honest failure there is a named row in the log and a refusal at the gate, not a
         * process that never gets to either.
         */
        private const val SETUP_MILLIS = 15_000L

        /** Long enough for an answer to come back around the socket before it is read. */
        private const val SETTLE_MILLIS = 750L

        private const val WATCH_MILLIS = 1_000L
    }
}
