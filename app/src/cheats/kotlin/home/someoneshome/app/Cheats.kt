package home.someoneshome.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import home.someoneshome.platform.SeededCardScanner
import home.someoneshome.platform.clearSeatToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import home.someoneshome.platform.deviceHaptics
import home.someoneshome.platform.launchSwitch
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.ui.Amber
import home.someoneshome.ui.FlowHost
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.Label
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.LocalPanelInsets
import home.someoneshome.ui.PanelRole
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.arrivingAt
import home.someoneshome.ui.tap
import home.someoneshome.ui.u

/**
 * The cheat surfaces: compiled into playtest and debug, ABSENT from release.
 *
 * Absent as in not in the compilation — release selects a source set that does not contain this
 * file, so a release build that could show a cheat is a build that did not compile. That is the
 * fail-closed direction: the failure mode is "the picker didn't appear on my playtest phone"
 * (noticed in thirty seconds), never "the picker appeared in a real round".
 *
 * ### The first cheat is a screen picker
 *
 * The app opens on [ScreenId.Boot] and walks itself from there: [FlowHost] drives the ported
 * flows, so the self-test falls through, a call rings out into the meeting, and the meeting runs
 * to lights out with nobody touching the phone. There is still no transport and no loop, which is
 * expected rather than a regression — the house is not deciding any of this, a local table is,
 * and it goes away the day the house can push.
 *
 * The picker is for the screens no walk reaches (`Flow.housePushed` — a text arriving, a scan
 * being answered, a revocation) plus the one thing no tap can set, the role. Jump anywhere from
 * the list, then walk the ported tap targets from there. None of this is game logic and none of
 * it survives into release; it is the desktop preview's job, on the hardware the desktop cannot
 * fake.
 *
 * ### The marker is permanent, and tapping it opens the picker
 *
 * A steady chip naming the variant, drawn over every screen. It is deliberately always there —
 * a playtest build must never be mistakable for release across a dark room. Steady is what
 * makes it lawful against the light discipline: constant light carries no signal, changes with
 * nothing, and is identical on every phone regardless of role.
 */
private enum class CheatView { Panel, Picker, Transport, Haptics, Camera }

@Composable
fun CheatRoot() {
    // ONE model for the whole session, held above the view switch. Dropping into the picker and
    // back must not restart the round the phone is walking, and rebuilding it here would do
    // exactly that — silently, because a screen that resets to boot looks like a screen that was
    // always on boot.
    // The homes come off the phone's own filesystem, so what this list shows survives the app
    // being stopped. Held above the view switch with everything else: re-reading the file on
    // every trip through the picker would be a file read per tap, and a list rebuilt underneath
    // a host mid-setup.
    // Hoisted above the flow because the lobby is wired to it, and because the host and client
    // must outlive the view: navigating away from the transport surface mid-evening must not hang
    // up two phones.
    val scope = rememberCoroutineScope()
    val transport = remember { TransportCheat(scope) }
    // The rig's log AND the device console. On one phone being tapped the screen is enough; across
    // two phones on a real network it is not — an advertisement that never went out and a browser
    // that never answered look identical from a Mac, and only a console line separates them. It
    // carries the radio's own events and nothing a player typed.
    val radio = remember<(String) -> Unit> {
        { line -> println("[lobby] $line"); transport.note(line) }
    }
    // What comes down the socket, classified and counted. Built before the flow because the link
    // is built inside it, and the probe has to be attached before the first frame rather than
    // after — a leak detector that starts late reports a clean run for the frames it missed.
    // It gets `radio` and not `transport::note`, and the difference is not cosmetic: the first
    // version handed it the rig's log alone, so the one line in this app that shouts — a
    // client-bound frame carrying somebody's line — reached the screen of a phone nobody was
    // holding and never reached the console. The injection that proved the probe works had to be
    // read off a summary instead of off the alarm.
    val watch = remember { LineWatch(radio) }
    // FORGET, before the link below is built — see LobbyCheat.FORGET_SWITCH. The link reads the
    // stored token in its constructor, so this is the last moment it can happen and still mean
    // anything, and a `LaunchedEffect` is already too late.
    remember { if (launchSwitch(LobbyCheat.FORGET_SWITCH)) clearSeatToken() }
    // The round's client half, promised to the lobby before it exists. See DeferredRoundSeam —
    // the cycle E-P2-3 named is real and this is the bench stepping around it, not a ruling on it.
    val seam = remember { DeferredRoundSeam() }
    // The rules, promised to the house before they exist — the same knot the seam above unties,
    // in the other direction: `LobbyHouse` wants somewhere to put an arriving request, and the
    // thing that holds the state wants the house to push its answers through.
    val rules = remember { mutableStateOf<RoundAuthority?>(null) }
    // Held in a named val because the round is armed off the house behind it: `armingInputs` is
    // what LIGHTS OUT reads and `emit` is the authority's only exit to the phones. Every other
    // caller in this file still reaches it as a `LobbyLink` and cannot see either.
    val link = remember {
        LobbyOverTransport(
            scope, onEvent = radio, onInbound = watch::saw,
            // **The one door a request from another phone comes in by.** It only ever fires on the
            // phone that is hosting, because that is the only phone frames arrive at.
            onIntent = { intent -> scope.launch { rules.value?.heard(intent) } },
            // **The other door, and the Egress's finger comes in by it** (E-P2-5 / E-P3-7 closed).
            // `fireEgress` makes the house's draw against the home's shape and then mints, which is
            // two steps where `heard` is one — the reason the request has a frame of its own.
            onEgressFired = { actor -> scope.launch { rules.value?.fireEgress(actor) } },
            round = seam,
        )
    }
    val flow = remember {
        FlowModel(
            PanelState(screen = ScreenId.Boot),
            homes = SavedHomesModel(SavedHomesDocument()),
            // The real radio behind the lobby: mDNS finds the homes, a websocket carries the one
            // line up and the counts down. Discovery failures land in the rig's log, because mDNS
            // failing is otherwise completely silent — which was the whole symptom on the first
            // device run.
            lobby = LobbyModel(finder = NearbyHomes(onEvent = radio), link = link),
        )
    }
    // The deck that stands in for a camera, kept now that there IS a camera. It is a tour of the
    // refusals in a fixed order — two cards that share a shape, two cards marked T, two meeting
    // cards — and no stack of paper in the house is guaranteed to contain that walk. Held above
    // the view switch so dropping into the picker and back does not deal from the top again.
    val scanner = remember { SeededCardScanner() }
    LaunchedEffect(scanner) { scanner.start(flow::cardScanned) }
    // ONE motor for the whole session, lent to both benches: CoreHaptics takes tens of
    // milliseconds to start an engine, and two of them on one phone is two engines competing for
    // one Taptic Engine.
    val haptics = remember { deviceHaptics() }
    val bench = remember { HapticCheat(haptics) }
    // And one camera, held here for the same reason and released far more carefully: a capture
    // session holds the lens, the ISP and the battery, so it is started where a card could be read
    // and stopped the moment that screen goes away.
    val scan = remember { ScanCheat(haptics) }
    // The bench, driving itself, when the process was launched with the switch set. It is how a
    // unit with no hand on the phone gets a device log out of the one output that has no picture:
    // the app opens on the bench and every row fires once. Absent the switch this is false and
    // nothing here happens, which is every launch a person makes.
    val sweeping = remember { launchSwitch(HapticCheat.SWEEP_SWITCH) }
    val scanSweeping = remember { launchSwitch(ScanCheat.SWEEP_SWITCH) }
    // The two-phone evening's finger. It drives the real lobby on the real flow, so it opens on the
    // panel like any other launch rather than on a bench of its own — what a person watching this
    // phone sees is the app, not a rig.
    // The rig's log only: it prints its own console lines, under its own prefix.
    val evening = remember { LobbyCheat(flow, watch, transport::note) }
    // **The round, past LIGHTS OUT.** The client half is built here rather than inside the flow
    // because it needs the flow — `houseSaid` is the one consumer — and handed to the lobby's
    // socket through the seam that was promised it above.
    val roundClient = remember {
        RoundClient(scope, flow, watch) { line -> println("[round] $line"); transport.note(line) }
            .also { seam.becomes(it) }
    }
    // The rules, on this phone. Built on every seat and inert on four of them: `onIntent` above
    // only fires where frames arrive, which is the phone that is hosting. What decides whether a
    // round is ever armed is `lobby.hosting`, one screen up, exactly as LIGHTS OUT does.
    val authority = remember {
        RoundAuthority(
            inputs = { link.house.armingInputs() },
            emit = { effect, state -> link.house.emit(effect, state) },
        ) { line -> println("[house] $line"); transport.note(line) }.also { rules.value = it }
    }
    // **What this seat's screen says, at every beat** — the instrument P3 did not have. It reads
    // the same model the composition draws from and is handed to the bench so the script's own
    // beats and the screen's own changes land in one stream in one order.
    val ledger = remember {
        ScreenLedger(flow) { line -> println("[screen] $line"); transport.note(line) }
    }
    val round = remember {
        RoundCheat(roundClient, authority, isHost = { flow.lobby.hosting }, ledger = ledger) { line ->
            println("[round-bench] $line"); transport.note(line)
        }
    }
    // Every push, to the reactive half — the only channel five processes in a dark house have.
    remember { roundClient.onMessage = round::react }
    // **NEW ROUND, when the house says the round is over.** `flow.newRound` is the control the
    // ending screen draws: it calls `lobby.roundEnded()` and walks this phone back to the lobby.
    // Pressed on every seat because the host pressing it moves only the host — see the reaction
    // in RoundCheat, and the escalation it names.
    remember {
        round.onEnding = {
            flow.newRound()
            println("[round-bench] NEW ROUND — BACK ON ${flow.state.screen.name.uppercase()}")
            println(
                "[round-bench] THE LINES AFTER THE WIPE — " +
                    "${flow.lobby.standing.linesIn} OF ${flow.lobby.standing.joined} · " +
                    "THIS PHONE HANDED OVER=${flow.lobby.line.handedOver}"
            )
        }
    }
    var view by remember {
        mutableStateOf(
            when {
                sweeping -> CheatView.Haptics
                scanSweeping -> CheatView.Camera
                else -> CheatView.Panel
            }
        )
    }
    LaunchedEffect(sweeping) { if (sweeping) bench.sweep() }
    LaunchedEffect(scanSweeping) { if (scanSweeping) scan.sweep() }
    LaunchedEffect(evening) { if (evening.armed) evening.run() }
    // **The round, once the lobby has produced one.**
    //
    // Two coroutines and they are not the same job. Every seat walks the house when its own work
    // order arrives, which is a reaction to a push and runs on all five. The *script* runs on one
    // phone, and the test for which one is the screen: LIGHTS OUT is what walks the host to ARMED,
    // so a phone standing there having pressed it is the phone holding the house.
    LaunchedEffect(round) {
        if (!round.armed) return@LaunchedEffect
        launch { round.play() }
        while (flow.state.screen != ScreenId.Armed) delay(250)
        if (flow.lobby.hosting) round.theRound(RoundCheat.seed())
    }
    // **The tick driver, on the host and nowhere else** (E-P3-3). The five timed transitions are
    // the authority's own, so a client running this loop would be a phone deciding when the talk
    // was over. It is a third coroutine rather than a step inside the script above because it is
    // not a script: it runs for the whole round, under whatever the players are doing, and the
    // meeting it ends may be one nobody scripted.
    LaunchedEffect(authority) {
        val house = authority ?: return@LaunchedEffect
        while (flow.state.screen != ScreenId.Armed) delay(250)
        if (flow.lobby.hosting) {
            tickHouse(
                house,
                note = { line -> println("[house-clock] $line"); transport.note(line) },
                // **The bench's one remaining hand on the clock, and it is on the clock rather than
                // on the counter.** P3's script set the authority's tick directly to jump a
                // cooldown; with a driver running, that leaves the driver's own timestep behind and
                // every deadline is then evaluated against a clock it can never reach. So the bench
                // tells the driver that more real time has passed than really has, and the driver
                // does everything else it always does — measures, converts to whole steps, abandons
                // a backlog too large to chase, and says so out loud. See RoundCheat.skipTo.
                clockNanos = { monotonicNanos() + round.skewNanos },
            )
        }
    }
    // The screen, sampled for as long as the phone is up. On the composition's scope because
    // `PanelState` is Compose state and the main thread is the only place it may be read from.
    LaunchedEffect(ledger) { ledger.watch() }
    Box(Modifier.fillMaxSize().background(Amber.Black)) {
        when (view) {
            // `standingInForTheHouse` is the bench's stand-in for the authority: with no round
            // attached the meeting stops at the check-in screen and stays there, which is true and
            // unwalkable.
            //
            // **It is switched off the moment a real round is being played**, and that is not
            // tidiness. The stand-in walks this phone's own screen forward on a timer; a house
            // pushing the same transition would then be the second thing moving it, and the two
            // would race on every meeting — a screen that advanced before the gate closed would
            // look exactly like a gate that closed early, on the one surface where D-104's
            // *no player short* is what a person is reading.
            CheatView.Panel -> FlowHost(flow, standingInForTheHouse = !round.armed)
            CheatView.Picker -> CheatPicker(
                flow.state,
                onPick = { flow.jump(it); view = CheatView.Panel },
                onTransport = { view = CheatView.Transport },
                onHaptics = { view = CheatView.Haptics },
                onCamera = { view = CheatView.Camera },
            )
            CheatView.Transport -> TransportCheatScreen(transport)
            CheatView.Haptics -> HapticCheatScreen(bench)
            CheatView.Camera -> ScanCheatScreen(scan)
        }
        MarkerChip { view = if (view == CheatView.Panel) CheatView.Picker else CheatView.Panel }
        // **THE IN-ROUND SCAN (D-159)**, on the screen that draws its own countdown. The player
        // got here by tapping SCAN; the window is open for the ten seconds the bar is emptying,
        // and the lens is held for exactly that long.
        //
        // What a read produces is `Effect.ScanAnswered` and the house's push to `ScanCaught` —
        // `Flow.housePushed` says so, and there is no house attached to this build (E-D3-3). A
        // client that walked itself to `ScanCaught` would be the device deciding which Subroutine
        // a card holds. So the window ends here where it really ends on this build: **at the
        // buzz**, which is the half below the effect boundary and the half that is real.
        if (view == CheatView.Panel && flow.state.screen == ScreenId.Scan) {
            DisposableEffect(scan) {
                scan.scan { }
                onDispose {
                    // A window still open when this screen goes away produced no read — the
                    // countdown reached zero, or the player tapped STOP NOW. Both are the same
                    // fact, and D-160 has one word for it: two vibrates, *no*, nothing spent.
                    scan.expire()
                    scan.release()
                    scanner.start(flow::cardScanned)
                }
            }
        }
        // Only where a card could actually be read. A shutter on the springboard would be a
        // control for an event that cannot happen there.
        if (view == CheatView.Panel && flow.state.screen == ScreenId.ScanMarker) {
            // THE REAL CAMERA, on the real scan screen, and only while it is on screen. This is the
            // doctrine as wiring: the lit screen IS the scanner, so the session is bound to the
            // screen's own lifetime rather than to the app's — walk off it and the lens comes back.
            DisposableEffect(scan) {
                scan.watch(flow::cardScanned)
                onDispose {
                    scan.release()
                    // The deck gets its listener back, or the shutter below would silently stop
                    // working after the first trip through the scan screen.
                    scanner.start(flow::cardScanned)
                }
            }
            ShutterChip(scanner.peek?.id?.value.orEmpty()) { scanner.present() }
        }
    }
}

/**
 * **The shutter: the one thing a build with no camera cannot do for itself.**
 *
 * Everything downstream of a card being read is real — the payload is decoded by `model`, offered
 * to the real `HouseMap`, and every refusal it can produce is the map's own. What is faked is the
 * camera resolving a symbol, and this is that event with a finger on it.
 *
 * It names the card it is about to present, because a scan whose outcome you cannot predict is a
 * scan you cannot use to check anything. **Compiled out of release** with the rest of this file,
 * which is the fail-closed direction: the failure mode is "the shutter is missing on my playtest
 * phone", never "a card registered itself in a real round".
 */
@Composable
private fun BoxScope.ShutterChip(next: String, onTap: () -> Unit) {
    val insets = LocalPanelInsets.current
    Box(
        Modifier.align(Alignment.BottomStart)
            .padding(start = 6.u, bottom = insets.bottom + 2.u)
            .background(Amber.Black)
            .border(1.u, Amber.Dim)
            .tap(onTap)
            .padding(horizontal = 5.u, vertical = 1.5.u),
    ) {
        Label("SCAN $next", size = 5.5, color = Amber.Dim, tracking = 0.18)
    }
}

/** Every screen, as a tappable list, plus the one toggle a screen cannot express: the role. */
@Composable
private fun CheatPicker(
    state: PanelState,
    onPick: (PanelState) -> Unit,
    onTransport: () -> Unit,
    onHaptics: () -> Unit,
    onCamera: () -> Unit,
) {
    var draft by remember(state) { mutableStateOf(state) }
    val insets = LocalPanelInsets.current
    Column(
        Modifier.fillMaxSize().background(Amber.Black)
            .padding(top = insets.top + 6.u, bottom = insets.bottom + 18.u)
            .padding(horizontal = 14.u)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Label("${BuildVariant.MARKER} — PICK A SCREEN", size = 8.0, color = Amber.Bright, tracking = 0.16)
        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.Faint)
                .tap {
                    val other = if (draft.role == PanelRole.Resident) PanelRole.Insider else PanelRole.Resident
                    draft = draft.copy(role = other)
                }
                .padding(horizontal = 6.u, vertical = 4.u),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("ROLE", size = 6.5, color = Amber.Dim)
            Label(draft.role.name.uppercase(), size = 6.5, color = Amber.Bright)
        }
        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.Faint).tap(onTransport)
                .padding(horizontal = 6.u, vertical = 4.u),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("TRANSPORT", size = 6.5, color = Amber.Dim)
            Label("0.8 RIG", size = 6.5, color = Amber.Bright)
        }
        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.Faint).tap(onHaptics)
                .padding(horizontal = 6.u, vertical = 4.u),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("HAPTICS", size = 6.5, color = Amber.Dim)
            Label("D-135 BENCH", size = 6.5, color = Amber.Bright)
        }
        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.Faint).tap(onCamera)
                .padding(horizontal = 6.u, vertical = 4.u),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("CAMERA", size = 6.5, color = Amber.Dim)
            Label("FRONT, NO PREVIEW", size = 6.5, color = Amber.Bright)
        }
        for (id in ScreenId.entries) {
            val current = id == draft.screen
            Label(
                id.name.uppercase(),
                // `arrivingAt` rather than a bare copy: the carrier on the two out screens reads
                // the CAUSE, not the screen, so a picker that landed there with none would render
                // the UNREGISTERED fallback and look like a bug in the chrome.
                Modifier.fillMaxWidth().tap { onPick(draft.arrivingAt(id)) }.padding(vertical = 1.5.u),
                size = 7.0,
                color = if (current) Amber.Bright else Amber.Dim,
                tracking = 0.08,
            )
        }
    }
}

/**
 * The permanent variant marker: a black chip in the panel's own ink, above the home indicator.
 *
 * Black-filled so it reads on the two full-amber screens as well as the dark ones, and bordered so
 * it reads on black.
 *
 * ### It lives in the home indicator's band, which is the only strip on the panel that is empty
 *
 * It used to sit *above* that band, centred — `insets.bottom + 2.u` — which put it over whatever
 * the screen had drawn at the bottom. On most screens that was a button's edge. On two it was
 * worse: it hid the springboard's middle dock label outright, and on the lobby it landed
 * mid-sentence on WAITING FOR EVERYONE'S LINE. "It sits over whatever the screen put there" was
 * written down as the cost of being unmistakable, and it turned out to be the cost of not reading
 * one word of a screen this build exists to look at.
 *
 * The home indicator's band is reserved by the hardware and `DeviceLayoutTest` asserts the app
 * puts nothing in it, so a chip there covers nothing — and it is held to the **start** rather than
 * centred, because the one thing that IS drawn in that band is the system's own pill, in the
 * middle. Cheat chrome and the operating system are now beside each other rather than on top of
 * one another, which is the correct relationship between them.
 *
 * On a phone with no bottom inset the band is zero and the chip is simply in the corner. That is
 * the desktop preview and nothing this game is played on.
 */
@Composable
private fun BoxScope.MarkerChip(onTap: () -> Unit) {
    val insets = LocalPanelInsets.current
    Box(
        Modifier.align(Alignment.BottomStart)
            .padding(bottom = 2.u, start = insets.side + 2.u)
            .background(Amber.Black)
            .border(1.u, Amber.Dim)
            .tap(onTap)
            .padding(horizontal = 5.u, vertical = 1.5.u),
    ) {
        Label(BuildVariant.MARKER, size = 5.5, color = Amber.Dim, tracking = 0.24)
    }
}
