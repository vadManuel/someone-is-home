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
import home.someoneshome.model.CardPayload
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.protocol.IntentMessage
import home.someoneshome.platform.SeededCardScanner
import home.someoneshome.platform.clearSeatToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import home.someoneshome.platform.deviceHaptics
import home.someoneshome.platform.deviceSystemSettings
import home.someoneshome.platform.launchSwitch
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.ui.Amber
import home.someoneshome.ui.FlowHost
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.Label
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.MeetingModel
import home.someoneshome.ui.LocalPanelInsets
import home.someoneshome.ui.PanelRole
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.SavedHomesModel
import home.someoneshome.ui.SubroutineModel
import home.someoneshome.ui.ScreenId
import home.someoneshome.ui.arrivingAt
import home.someoneshome.ui.asMessage
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
    // **The round's way UP, promised to the panel before it exists — E-P2-3's cycle, untied the way
    // the two holders above already untie theirs.**
    //
    // `MeetingModel(send = …)` and `SubroutineModel(send = …)` want the round transport; the
    // transport wants a `FlowModel`; the `FlowModel` wants the models. Something has to be built
    // before the thing it needs, and this is the smallest something: the models are handed a
    // lambda that reads this holder, and the holder is filled the line the round client is built.
    // It is `rules`' idiom exactly and it is an **ordering fix and nothing else** — no seat is
    // named here, no request is composed here, and nothing here decides what any of them means.
    //
    // **Null until the round client exists**, which is every moment before LIGHTS OUT. A control
    // pressed then reaches nobody, which is the honest state of a phone with no round attached and
    // is what these two seams did on every phone in every round until now.
    val press = remember { mutableStateOf<RoundClient?>(null) }
    // **And the panel, promised to the link before it exists**, which is the same knot in the
    // third direction: the link is built first because the lobby needs it, and the two things the
    // link has to reach when the house ends a round are a screen and a typed line, both of which
    // live on the panel. Filled on the line below the panel is built.
    val panel = remember { mutableStateOf<FlowModel?>(null) }
    // ONE motor for the whole session, lent to the round, both benches and the scan window:
    // CoreHaptics takes tens of milliseconds to start an engine, and two of them on one phone is
    // two engines competing for one Taptic Engine.
    //
    // **It is built up here, above the panel, because the round needs it and so does the panel.**
    // It used to be built two hundred lines down beside the benches, which is precisely how long
    // the game went without a buzz in it: the only things that could see it were benches.
    val haptics = remember { deviceHaptics() }
    // **THE MOTOR, JOINED TO THE ROUND — E-D1-1 CLOSED.** Every push this seat is sent goes through
    // `RoundOverTransport`, which now cannot be built without one of these; the panel reports its
    // own arrivals to it so the ring can be answered and a Subroutine's question can be buzzed at
    // the moment its work opens. Before this line, `HouseBuzzer` had no caller outside the bench
    // and no push in a real round reached the Taptic Engine — which is why the owner's Handshake
    // "does not vibrate at all".
    val buzzer = remember { HouseBuzzer(haptics) { line -> println("[haptic] $line") } }
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
            // **The host said the round is over** (D-157, E-R2-10). The transport has already reset
            // this phone's inbox by the time this runs; what is left is the screen answer, which is
            // the panel's. `newRound` walks this phone back to the lobby and wipes the line it
            // typed — the same two things the host's own NEW ROUND does, reached by a push instead
            // of by a button that only one phone in the house draws.
            onRoundEnded = { panel.value?.newRound() },
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
            // **And the way out to the OS's own settings page** (the owner's 2026-08-24 ruling).
            // The refusal that names the Local Network grant now carries a control that opens it,
            // and this is the one line where that control reaches an OS: `ui` cannot see
            // `platform`, so the app hands the seam down the same way it hands down the radio.
            lobby = LobbyModel(
                finder = NearbyHomes(onEvent = radio),
                link = link,
                settings = deviceSystemSettings()::open,
            ),
            // **The four meeting controls, finally reaching the house** (E-P2-3). I AM HERE, READY
            // TO VOTE, a row under a thumb and READY were built, drawn and pressed for two whole
            // rehearsals with `send` defaulted to `{}` — so the check-in gate was closed by the
            // bench pressing intents directly and never by the control a player actually touches.
            //
            // **`asMessage` and the ballot the screen was drawn with**, which is where a name
            // becomes a seat (D-115). It reads `meeting.names` rather than a list assembled here,
            // so the seat a vote resolves to is a position in the list the house sent — and an
            // unresolvable row is a Skip, never a vote cast at somebody nearby.
            meeting = MeetingModel(send = { request ->
                // The ballot the screen was drawn with, read live off the model this lambda is
                // being handed to — the house writes it on `RosterIssued` and nothing else does.
                press.value?.act(request.asMessage(panel.value?.meeting?.names.orEmpty()))
            }),
            // The Subroutine's own upward seam, the same shape and the same holder. It publishes an
            // `IntentMessage` directly, which cannot name an actor at all.
            subroutines = SubroutineModel(send = { intent -> press.value?.act(intent) }),
            // Page 2's two Tier-2 fires. `IntentMessage` again, and the seats that ride it are
            // CONTENT — who the Surge is aimed at, off the roster the house drew and published to
            // this phone — which is the same rule the ballot's target travels under.
            send = { intent -> press.value?.act(intent) },
            // **THE HOST'S THUMB, ARMING A REAL ROUND — E-PAY-1 / E-D3-3's trigger half, closed.**
            //
            // This is the line the game did not have. `FlowModel.lightsOut` walked the host's own
            // screen to ARMED and told nobody; `RoundAuthority.arm` had one caller in the tree and
            // it was a bench script behind `SOMEONES_HOME_ROUND_HOUSE`. So a phone a person
            // launched from the springboard armed nothing, with no symptom until the hold did
            // nothing — which is exactly how the owner lost an evening after force-quitting the
            // app to fix their name and losing the switch with it (E-PAY-7).
            //
            // **No launch switch is read on this path.** `armingCordon` scans `app/commonMain` and
            // fails the build on one, and the seed below draws for real when nobody named one.
            //
            // `rules.value` is filled the line the authority is built, a few lines down — this
            // lambda is not called until a thumb reaches a live button, which is a whole lobby
            // later. The two guards are `hosting` and the desk's own `readyToArm`, and neither is
            // this file's opinion: the first is the lobby's answer and the second is the gate the
            // button itself is drawn from.
            onLightsOut = {
                val house = rules.value
                scope.launch {
                    if (house == null) {
                        println("[house] LIGHTS OUT — NO RULES ON THIS PHONE YET")
                        return@launch
                    }
                    armAtLightsOut(
                        house = house,
                        // Read off the panel holder for `meeting.names`' reason one field up: this
                        // lambda is handed to the model being built on this line, so it cannot
                        // name it directly. `hosting` is the lobby's own answer, asked at the
                        // instant of the press.
                        hosting = panel.value?.lobby?.hosting == true,
                        seed = RoundCheat.seed(),
                    ) { line -> println("[house] $line"); transport.note(line) }
                }
            },
            // **WHERE THIS PHONE IS, TOLD TO THE MOTOR.** `ui` cannot see `platform`, so the two
            // things a screen legitimately settles about a vibration are reported out to here: a
            // ring stops when the player walks off it (D-160's *until answered*), and Handshake's
            // rhythm, Sniff's groups and Drift's *now* are buzzed when their work opens. No haptic
            // is chosen on this line and none may be — see `HouseBuzzer.showing`.
            onScreen = buzzer::showing,
        ).also { panel.value = it }
    }
    // The deck that stands in for a camera, kept now that there IS a camera. It is a tour of the
    // refusals in a fixed order — two cards that share a shape, two cards marked T, two meeting
    // cards — and no stack of paper in the house is guaranteed to contain that walk. Held above
    // the view switch so dropping into the picker and back does not deal from the top again.
    val scanner = remember { SeededCardScanner() }
    LaunchedEffect(scanner) { scanner.start(flow::cardScanned) }
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
        RoundClient(scope, flow, watch, buzzer) { line -> println("[round] $line"); transport.note(line) }
            // Both knots tied in one place: the lobby gets the seam it was promised, and the four
            // meeting controls, the Subroutine's hand-over and page 2's two fires get the way up
            // they were promised (E-P2-3). Before this line every one of them reached nobody.
            .also { seam.becomes(it); press.value = it }
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
        RoundCheat(roundClient, flow, authority, isHost = { flow.lobby.hosting }, ledger = ledger) { line ->
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
        // **Nothing here arms anything any more, and that is the unit.** Arming is production's —
        // it hangs off `onLightsOut` where the `FlowModel` is built, reached by a thumb on a live
        // button and by nothing else. What is left is the script, which now waits for the house
        // the host's own press armed rather than starting one of its own.
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
            //
            // **Read off the panel's own state rather than off how this process was launched.**
            // `RoundClient.linked` writes it the moment this phone has a way up to a house, which
            // is the only thing that ever made the sentence above true or false.
            CheatView.Panel -> FlowHost(
                flow,
                standingInForTheHouse = standsInForTheHouse(flow.state.houseAttached),
            )
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
        // `Flow.housePushed` says so. A client that walked itself to `ScanCaught` would be the
        // device deciding which Subroutine a card holds, so nothing here moves a screen: the read
        // goes UP as an intent and the house answers it.
        //
        // ⚠️ **THIS CALLBACK WAS EMPTY UNTIL THE PAYOFF RUN, AND THE COMMENT SAID WHY.** It was
        // written when no house was attached (E-D3-3), so a read in a round had nowhere to go and
        // the window ended at the buzz. Everything downstream has been built since — C3 routed
        // `ScanAnswered` (`Flow.kt:1624`), R1 minted the wire members — and the one missing link
        // was the finger. This is that link, and it is enablement of behaviour already ruled three
        // times: revision 34's scan flow (tap SCAN, the window, AUTO-OPEN on acquire — the
        // auto-open IS the read becoming the intent), D-124's resolution ((seat, card) → that
        // seat's Subroutine, NOTHING FOR YOU HERE otherwise), and D-121 (a meeting is called ONLY
        // by physically scanning the meeting card).
        //
        // ⚠️ **THE INTENT SPLIT IS CLIENT-SIDE AND THAT IS THE SEAM TO REPLACE.** `CallMeeting` and
        // `BeginSubroutine` are two wire members, so somebody has to decide which a card is. The
        // HOUSE is the one that should: `routeScan` (`core/Scanning.kt:87`) decides it against the
        // home's map, and a client has no map — Static deleted `Effect.HomePlanOffered` and the
        // plan now reaches nobody. So this trusts **the paper over the map**, reading the shape
        // printed on the card, which is strictly weaker than the house's answer and **fails in the
        // harmless direction**: an unregistered meeting-shaped card produces a `MeetingCalled` the
        // rules refuse, and a real client cannot construct a wrong seat in any case because
        // `IntentDesk` stamps the seat off the connection. **A production build should send ONE raw
        // read and let the house route it** — one wire member, the house's judgment. That is E5's,
        // and this split is the seam it replaces.
        if (view == CheatView.Panel && flow.state.screen == ScreenId.Scan) {
            DisposableEffect(scan) {
                scan.scan { payload ->
                    when (val intent = inRoundScan(payload)) {
                        // Paper this game did not print. Nothing goes up: D-071 makes an unreadable
                        // card a fact about a piece of paper rather than a statement about a
                        // player, and the house is not asked about paper. The lens already buzzed.
                        null -> println("[round] SCAN — NOT THIS GAME'S PAPER")

                        // ⚠️ **THE PHONE REMEMBERS ITS OWN READ, AND N1 IS WHERE IT STARTED TO.**
                        // The house's answer names the entry, the kind and the parameters and
                        // carries no card — `SubroutineInstance` says so on its own type — so if
                        // the reader does not keep the id, nothing on this phone ever has it, and
                        // BEGIN hands `SubroutineModel.posted` a null marker that refuses the send.
                        // That was the whole of the hand-over outage: a scan went up from here, a
                        // row came down, and the id in between was dropped on the floor.
                        //
                        // Written beside the send rather than inside `inRoundScan` deliberately.
                        // That function is a pure decode and the shape test E5 deletes; this is the
                        // client keeping a fact about its own act, and it has to outlive the split.
                        is IntentMessage.BeginSubroutine -> {
                            println("[round] SCAN — ${nameOfScan(intent)} · ASKING THE HOUSE")
                            flow.scanning(intent.marker)
                            press.value?.act(intent)
                        }

                        // The meeting card opens no work and files no entry, so there is nothing
                        // here for a hand-over to be attributed to (D-121).
                        else -> {
                            println("[round] SCAN — ${nameOfScan(intent)} · ASKING THE HOUSE")
                            press.value?.act(intent)
                        }
                    }
                }
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
 * **A card read in a round, turned into the request it is** — and the seam a real build replaces.
 *
 * Pulled out of the callback that calls it so that it can be tested and injected against. The
 * callback is inside a `@Composable`, where a rule about what a scan means would be a rule nothing
 * could check; here it is a pure function from a payload to an intent, and
 * [ScanIntentTest] sweeps the whole shape roster through it.
 *
 * ### It is ruled behaviour, not a decision taken here
 *
 * Decision-log revision 34's scan flow (tap SCAN, the ten-second window, **auto-open on acquire** —
 * the auto-open *is* the read becoming the request), D-124's resolution (a `(seat, card)` pair
 * resolves to that seat's Subroutine, and to NOTHING FOR YOU HERE otherwise), and **D-121**: a
 * meeting is called only by physically scanning the meeting card.
 *
 * ### ⚠️ The split is client-side, and that is exactly what E5 should delete
 *
 * `CallMeeting` and `BeginSubroutine` are two wire members, so something must decide which a card
 * is. **The house is what should decide it** — `routeScan` (`core/Scanning.kt`) answers it against
 * the home's map, and a client has no map: Static deleted `Effect.HomePlanOffered` and the plan
 * now reaches nobody. So this reads **the shape printed on the paper** instead, which is strictly
 * weaker than the map's answer.
 *
 * **It fails in the harmless direction, and that is the whole argument for it standing in.** A
 * meeting-shaped card the home never registered produces a `MeetingCalled` the rules refuse; a
 * marker this seat has no work at produces the `ScanAnswered` that opens nothing, which is D-124
 * working. Nothing here can name a seat: `IntentDesk` stamps that off the connection, so the worst
 * a wrong answer costs is one refused frame.
 *
 * **A production build should send one raw read and let the house route it** — one wire member,
 * the house's judgment, no shape test on a phone. Returns null for paper this game did not print.
 */
/**
 * **Whether the bench's fake house may drive this phone's screens** — `FlowHost`'s
 * `standingInForTheHouse`, decided here rather than inside the composable.
 *
 * The stand-in fires whatever push the current screen is waiting for after `HOUSE_STAND_IN_MS`, so
 * that a ported flow with no house attached is walkable instead of stopping at the check-in screen
 * forever. Its own KDoc states the rule this function exists to keep: **it must never be true in a
 * real round.** Two things moving one screen race on every meeting, and a screen that advanced
 * before the gate closed looks exactly like a gate that closed early — on the one surface where
 * D-104's *no player short* is what a person is reading.
 *
 * ### ⚠️ It read one switch and there are two ways to be in a real round
 *
 * The expression was `!round.armed`, and `armed` is [RoundCheat.ROUND_SWITCH] — *this process is
 * playing the scripted evening*. The payoff run then added a **second** way to have a real round,
 * [RoundCheat.ARM_SWITCH], for a host phone that arms and then leaves the evening to the people in
 * it; this line was never taught about it. So the owner's two phones ran a real house all evening
 * with the fake one also driving their screens, and the vote beat records what that looked like:
 * **ASSEMBLE to NOTICE in 4.0 seconds** — `HOUSE_STAND_IN_MS` exactly, on a screen with no
 * `autoAdvance` entry — a check-in gate that never closed because nobody was left on the screen
 * long enough to press I AM HERE, and three meetings that were fiction.
 *
 * ### It is a function so that it can be tested and injected against
 *
 * `inRoundScan` below was pulled out of the composable for this reason and the reason holds here:
 * inside a `@Composable`, a rule about when the fake house may drive is a rule nothing can check,
 * and this one has already been wrong once in production with no test able to see it.
 *
 * ### ⚠️ AND THE TWO SWITCHES ARE GONE, WHICH IS THE GAP THE ORed VERSION FLAGGED ON ITSELF
 *
 * That version took `playsAScriptedRound` and `armsTheHouse` and answered `neither`. It fixed the
 * owner's evening and it wrote down its own residual: *both inputs are launch switches, so a phone
 * launched with neither — one a person taps through the lobby and joins to a real house with their
 * finger — still gets the stand-in.* **That phone is the product.** Every phone in every real
 * evening after the benches are gone is launched with no switches at all, so the ORed answer was
 * right about two rehearsal arrangements and wrong about the game.
 *
 * The parameter is now the question itself: **is a house attached** — [PanelState.houseAttached],
 * written by `RoundClient.linked`, which is the seam a phone actually acquires a house through and
 * is called on the host over loopback and on every client over the network. A third way into a
 * round arrives already answered, because it will arrive through the same seam; there is nothing
 * left here for anyone to forget to teach.
 *
 * **It is now the second of two guards rather than the only one**, and deliberately. `FlowHost`
 * asks the same question itself and overrules both of its drivers with it, so a caller that got
 * this wrong could no longer produce the owner's meeting. This one stays because a bench asserting
 * a house it does not have should be wrong here too, and because one honest expression at the call
 * site is worth more than a comment saying the composable will catch it.
 */
internal fun standsInForTheHouse(houseAttached: Boolean): Boolean = !houseAttached

internal fun inRoundScan(payload: String): IntentMessage? =
    when (val read = CardPayload.decode(payload)) {
        is CardPayload.Result.Rejected -> null
        // The reserved `u_shape` and nothing else. The Terminal's `t_shape` is deliberately NOT a
        // second case: `routeScan` calls the Terminal **work** — it is a place you scan to work at
        // — so it travels as an ordinary marker exactly like the ten.
        is CardPayload.Result.Read ->
            if (read.card.shape == MarkerShapes.MEETING) IntentMessage.CallMeeting
            else IntentMessage.BeginSubroutine(read.card.id)
    }

/** What to call a scan in the log. The marker, or the one card that is not a marker at all. */
private fun nameOfScan(intent: IntentMessage): String = when (intent) {
    is IntentMessage.BeginSubroutine -> intent.marker.value
    IntentMessage.CallMeeting -> "THE MEETING CARD"
    else -> "A SCAN"
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
