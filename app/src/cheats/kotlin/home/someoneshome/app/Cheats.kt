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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import home.someoneshome.platform.SeededCardScanner
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
private enum class CheatView { Panel, Picker, Transport }

@Composable
fun CheatRoot() {
    // ONE model for the whole session, held above the view switch. Dropping into the picker and
    // back must not restart the round the phone is walking, and rebuilding it here would do
    // exactly that — silently, because a screen that resets to boot looks like a screen that was
    // always on boot.
    // The homes come off the phone's own filesystem, so what this list shows survives the app
    // being killed. Held above the view switch with everything else: re-reading the file on
    // every trip through the picker would be a file read per tap, and a list rebuilt underneath
    // a host mid-setup.
    // Hoisted above the flow because the lobby is wired to it, and because the host and client
    // must outlive the view: navigating away from the transport surface mid-evening must not hang
    // up two phones.
    val scope = rememberCoroutineScope()
    val transport = remember { TransportCheat(scope) }
    val flow = remember {
        FlowModel(
            PanelState(screen = ScreenId.Boot),
            homes = SavedHomesModel(SavedHomesDocument()),
            // The real radio behind the lobby: mDNS finds the homes, a websocket carries the one
            // line up and the counts down. Discovery failures land in the rig's log, because mDNS
            // failing is otherwise completely silent — which was the whole symptom on the first
            // device run.
            lobby = LobbyModel(
                finder = NearbyHomes(onEvent = transport::note),
                link = LobbyOverTransport(scope, onEvent = transport::note),
            ),
        )
    }
    // The camera this build does not have. `SeededCardScanner` is a deck of real cards encoded to
    // real payloads; the chip below is the shutter. Held above the view switch with the rest so
    // that dropping into the picker and back does not deal the deck from the top again.
    val scanner = remember { SeededCardScanner() }
    LaunchedEffect(scanner) { scanner.start(flow::cardScanned) }
    var view by remember { mutableStateOf(CheatView.Panel) }
    Box(Modifier.fillMaxSize().background(Amber.Black)) {
        when (view) {
            CheatView.Panel -> FlowHost(flow)
            CheatView.Picker -> CheatPicker(
                flow.state,
                onPick = { flow.jump(it); view = CheatView.Panel },
                onTransport = { view = CheatView.Transport },
            )
            CheatView.Transport -> TransportCheatScreen(transport)
        }
        MarkerChip { view = if (view == CheatView.Panel) CheatView.Picker else CheatView.Panel }
        // Only where a card could actually be read. A shutter on the springboard would be a
        // control for an event that cannot happen there.
        if (view == CheatView.Panel && flow.state.screen == ScreenId.ScanMarker) {
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
private fun CheatPicker(state: PanelState, onPick: (PanelState) -> Unit, onTransport: () -> Unit) {
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
