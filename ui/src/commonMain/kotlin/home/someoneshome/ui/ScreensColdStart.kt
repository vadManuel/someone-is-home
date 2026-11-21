package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant

/**
 * Cold start: the screens before anyone has joined anything, and the launch gate before them.
 *
 * Light-field, because the house lights are still on.
 */

/** **The launch gate's headline** (D-292), in the owner's approved words. */
const val NO_UWB_HEADLINE: String = "THIS PHONE CAN'T PLAY"

/** **The launch gate's one line** (D-292), in the owner's approved words. */
const val NO_UWB_BODY: String = "Someone's Home needs Ultra Wideband: iPhone 11 or later, not iPhone SE."

/**
 * **A phone without Ultra Wideband, told at launch that it cannot play** (D-292).
 *
 * UWB phones only, with no Bluetooth fallback: contact, the report and presence are measured over
 * Nearby Interaction, and a phone that cannot range would be a seat the house cannot see. So the
 * phone is told, before the self-test and instead of everything after it, and the reason is its
 * hardware rather than anything its player did.
 *
 * **There is no way forward and nothing to tap.** No button, no back, no fall-through: nothing a
 * finger does here leads anywhere, because nothing could. The app roots choose this screen once,
 * at launch, and never come back to the question.
 *
 * Light-field and drawn like the self-test it replaces: the headline in the self-test's title
 * type, and the one line under it at reading size. The words are the owner's, verbatim; the line
 * breaks are the layout's.
 */
@Composable
fun NoUwbScreen() {
    val p = Palette.BONE
    KitScreen(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp), gap = 16.dp) {
        Words(NO_UWB_HEADLINE, Modifier.padding(top = 16.dp), size = 48, color = p.ink, lineHeight = 0.9)
        KitBody(NO_UWB_BODY, p, Modifier.padding(top = 8.dp))
    }
}

/**
 * Self-test on power-up. Falls through on its own.
 *
 * It reports `PERIMETER . . . IDLE` and `UPLINK . . . CONNECTED` — both true at this moment, and
 * both about to stop being true. The screen does exposition the player will only understand in
 * retrospect, which is the correct amount of exposition for this game.
 *
 * ### The bar is the self-test, not a picture of one
 *
 * It was two `weight`s, 7 against 3, so it stood at seventy percent for the whole 2.4 seconds and
 * then the screen went — which is the owner's *the loading screen's bar literally doesn't move*.
 * The first screen in the game was a still photograph of a phone starting up, on the one surface
 * whose entire job is to say *something is happening, wait*.
 *
 * It now draws [PanelVals.window], which is the same 2.4 seconds the fall-through in
 * [Flow.autoAdvance] is counting — one number, two readers — so the bar arrives full in the frame
 * the screen is replaced and cannot promise a wait that is already over. A phone with no window at
 * all draws an empty track rather than a fixture: nothing has happened yet, and that is what nothing
 * looks like.
 */
@Composable
fun BootScreen(vals: PanelVals) {
    val p = Palette.BONE
    KitScreen(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp), gap = 16.dp) {
        Words("SOMEONE'S HOME", Modifier.padding(top = 16.dp), size = 48, color = p.ink, lineHeight = 0.9)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "MEM 640K" to "OK",
                "REGISTRY" to "0 ENTRIES",
                "PERIMETER" to "IDLE",
                "LAMP ALLOCATION" to "GRANTED",
                "UPLINK" to "CONNECTED",
            ).forEach { (key, value) -> BootRow(key, value, p) }
        }
        KitSpacer()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KitCaption("UPTIME 4331D 02:14", p)
            // Twenty segments, lit as far as the window has run. A segment is a step, never a fade.
            KitMeter(
                total = BOOT_CELLS,
                lit = ((vals.window?.fraction ?: 0f) * BOOT_CELLS).toInt().coerceIn(0, BOOT_CELLS),
                ink = p.ink, edge = p.line,
                modifier = Modifier.testTag(BOOT_BAR), height = 10.dp,
            )
            Tag("STARTING", p.dim, Modifier.fillMaxWidth().then(SILK_LINE), align = TextAlign.Center)
        }
    }
}

/**
 * **A Silkscreen label's line box, 12 tall as the mockup's `line-height: 1` makes it.** The face's
 * own ascent and descent come to about 16, which pushed everything under a label 4 units down.
 */
internal val SILK_LINE: Modifier = Modifier.height(12.dp)

/** The self-test bar's segments, as the mockup's `bootCells` counts them. */
private const val BOOT_CELLS = 20

/** One self-test line: the check in dim, a dotted leader, and the answer in ink. */
@Composable
private fun BootRow(key: String, value: String, p: Palette) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Words(key, size = 24, color = p.dim, lineHeight = 1.0)
        Box(Modifier.weight(1f).padding(bottom = 8.dp).height(2.dp).dottedLine(p.line))
        Words(value, size = 24, color = p.ink, lineHeight = 1.0)
    }
}

/** The mockup's `2px dotted` rule: square dots their own width apart. */
private fun Modifier.dottedLine(color: Color): Modifier = drawBehind {
    val dot = size.height
    repeat(dotsAcross(size.width, dot)) { i -> drawRect(color, Offset(i * dot * 2, 0f), Size(dot, dot)) }
}

/**
 * **How many dots a dotted rule [width] long holds, which is always a finite number.**
 *
 * The first version stepped along the rule by the dot's own size until it ran out of width, and a
 * row squeezed to no height by a short panel has dots of no size: the step was zero, the loop never
 * ended, and every pass recorded another rectangle into the frame's picture. A test worker grew to
 * some 80 GB of native memory before it was stopped. A count, worked out once, cannot do that.
 */
internal fun dotsAcross(width: Float, dot: Float): Int =
    if (!(width.isFinite() && dot.isFinite()) || dot <= 0f || width < dot) 0
    else (((width - dot) / (dot * 2)).toInt() + 1).coerceAtMost(MAX_DOTS)

/** No rule on a phone holds this many dots; one that claims to is a measuring fault. */
private const val MAX_DOTS = 10_000

/**
 * Permissions, asked **at startup, in the light — never at arming** (D-161).
 *
 * Every input this game has is a permission: camera, Bluetooth, nearby devices, motion,
 * notifications. Local Network is not asked here (D-309): iOS raises it by itself when the phone
 * first browses for a home or advertises one. A system prompt appearing mid-round would be an unauthored screen state at the
 * exact moment screen state is evidence, so the whole set is collected while nothing is at stake
 * and nothing is asked for again.
 *
 * ### Every row on this screen used to be a drawing, and that is what shipped
 *
 * Three rows said GRANTED and two said PENDING, and **not one of them had ever reached an OS**. The
 * app's page in Settings had no toggles under it because nothing had ever been requested; the
 * camera prompt then appeared in the one place the ruling forbids, mid-scan, in a dark corridor.
 * And the button under it — `REQUEST MOTION ACCESS` — requested nothing and navigated to the join
 * screen unconditionally, which the owner reported as *request motion access just sends me to the
 * main screen.* **That fall-through is gone**: the primary control asks for what is missing, and
 * only becomes a way forward once the phone can really do the job.
 *
 * ### Tapping a row is asking for that one thing
 *
 * Rows are controls rather than readings, which [PreRow] distinguishes by whether it is given an
 * `onClick` at all. A row that has been refused keeps its tap — asking again raises no prompt (iOS
 * asks once, ever) but re-reads the answer, which is what a host who has just come back from
 * Settings needs to happen.
 */
@Composable
fun PermsScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val perms = LocalPerms.current
    val p = Palette.BONE
    // Read the OS when the screen arrives, and again on every return to it. A host who walked out
    // to Settings comes back here, and nothing else would move the rows.
    LaunchedEffect(perms) { perms.refresh() }
    KitScreen {
        KitHeader("PERMISSIONS", p, trail = true)
        // **The words and rows scroll; the buttons under them do not** (D-305). One row refused,
        // the rest owed and Local Network's caption showing put OPEN SETTINGS and REQUEST WHAT IS
        // MISSING below the edge, where nothing could tap them. The gap between the two parts is
        // the 10 the button column always had, so a screen that fits draws as it did.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.fillMaxWidth().weight(1f).testTag(PERMS_SCROLL)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KitBody("Grant these now, while the lights are on.", p)
                KitRules(p) {
                    DevicePermission.entries.forEach { permission ->
                        val grant = perms.grant(permission)
                        // **The gate's own rule** (D-309: every row must read GRANTED), so the
                        // screen and the gate agree about what is still owed. An open row's state is
                        // an outline; one still owed is the filled tag, which is the mockup's TAP TO
                        // GRANT, and NEARBY DEVICES at ASKED is owed.
                        val open = grant.opensTheGate
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .edgeLine(PanelSide.Bottom, p.line, 1.dp)
                                .tap { actions.requestGrant(permission) }
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Words(permission.label, Modifier.weight(1f), size = 24, color = p.ink, lineHeight = 1.0)
                            Box(
                                Modifier.border(1.dp, if (open) p.dim else p.ink)
                                    .background(if (open) Color.Transparent else p.ink)
                                    .padding(1.dp).padding(horizontal = 6.dp, vertical = 5.dp)
                            ) {
                                Tag(grant.label, if (open) p.ink else Amber.Bone, Modifier.height(12.dp))
                            }
                        }
                    }
                }
                // Inside the scroll the column's height is at least the space it was given, so this
                // still pushes the gate's words down onto the buttons when everything fits.
                KitSpacer()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // What the last tap was refused with, or what the gate is still waiting for.
                    // Never both, and never an invented sentence: a complete phone has nothing here
                    // at all.
                    (perms.refusal ?: perms.gateRefusal())?.let { KitBody(it, p, size = 22) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // **A refused row cannot be un-refused from in here** — iOS asks once, ever, and
                // the second ask raises nothing. So the control appears exactly where its fix is
                // outside the app, which is [LobbyModel.offersTheGrant]'s rule read on this screen.
                if (perms.states.values.any { it == Grant.Refused }) {
                    KitSecondary("OPEN SETTINGS", p, onClick = actions.openSystemSettings)
                }
                if (perms.complete) {
                    // **A hosting phone goes back to its own lobby** (D-311): the house stayed on the
                    // air while it was shown this screen for something switched off.
                    KitPrimary("CONTINUE", p) { actions.permissionsDone?.invoke() ?: go(ScreenId.Join) }
                } else {
                    // It asks, and it stays here. The one screen where a control that navigated
                    // without doing anything cost an evening.
                    KitPrimary("REQUEST WHAT IS MISSING", p, size = 28, onClick = actions.requestMissingGrants)
                }
            }
        }
    }
}

/** The PERMISSIONS screen's scrolling part: the line under the header, the rows and the gate's words. */
const val PERMS_SCROLL: String = "perms-scroll"

/**
 * **What the RESIDENT field answers to, from inside a Compose test and from outside the process.**
 *
 * Compose Multiplatform publishes a `testTag` as the iOS `accessibilityIdentifier`, so this one
 * constant is the handle at both altitudes of bench — the in-process one that presses the lambdas,
 * and the human-mock that presses the glass. Kebab-case, unique on its screen; the convention is
 * written out in `iosApp/SomeonesHomeUITests/HumanMock.swift`.
 */
const val RESIDENT_NAME: String = "resident-name"

/**
 * Your name, then the homes nearby.
 *
 * **Discovery is local only. No account, no internet** — a design promise and a technical one at
 * once. The house cannot reach the internet either; that containment line is what the Residents
 * are holding.
 *
 * ### The list is the radio's, and the radio is honest about what it knows
 *
 * The design's fixture drew three fixed rows with signal bars beside them. mDNS resolves a name,
 * an address and a port and reports no signal strength at all, so the bars are gone and the
 * address is in their place: it is the only true thing that tells two homes apart when both
 * households have called theirs THE BUNGALOW. A row of bars nobody measured would be an
 * invention on the screen a player uses to pick which house they are walking into.
 *
 * The name field is real typing rather than a drawn caret, and **tapping a row is what sends it**:
 * a home is chosen and the name goes up with the join, because the design's lobby lists the people
 * in it (D-115). It is typed here rather than in the lobby for that reason — there is no moment in
 * this game where a phone is in a lobby and has not said what to call its owner. See
 * [LobbyModel.residentName] and [LobbyModel.attachTo].
 */
@Composable
fun JoinScreen() {
    val go = navigator()
    val actions = LocalActions.current
    val lobby = LocalLobby.current
    val p = Palette.BONE
    // Listening starts when the screen does. Nothing before this point has any business having
    // the radio on: permissions are granted one screen back, in the light.
    LaunchedEffect(lobby) { lobby.look() }
    // **The look's clock** (D-309): NO HOME ON THIS NETWORK after about ten seconds of a look that
    // found nothing. Keyed on the look, so a restart (back from Settings) restarts the count; the
    // decision is the model's ([LobbyModel.lookedFor]), the screen only keeps the time.
    val look = lobby.looks
    LaunchedEffect(lobby, look) {
        if (look > 0) {
            delay(LobbyModel.NO_HOME_AFTER_MILLIS)
            lobby.lookedFor(look)
        }
    }
    KitScreen {
        // **The page name, and the way back to the permissions walk beside it** (E-PAY-6). CONTINUE
        // is a real `go` from `Perms`, so the trail is often there. **And PERMISSIONS is named as
        // the fallback** (D-248, kept by D-303): since D-235 a set-up phone skips that screen and
        // arrives here with nothing behind it, and that screen is the way back to the permission walk.
        KitHeader("JOIN A HOME", p, back = ScreenId.Perms, trail = true)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KitLabel("RESIDENT", p, SILK_LINE)
            KitField(p) {
                ReadoutField(
                    value = lobby.residentName,
                    onValueChange = actions.nameResident,
                    modifier = Modifier.fillMaxWidth(),
                    size = 40.0, color = p.ink, tracking = 0.06,
                    hint = "YOUR NAME",
                    label = "RESIDENT",
                    tag = RESIDENT_NAME,
                )
            }
        }
        KitLabel("NETWORKS NEARBY", p, Modifier.padding(top = 8.dp).then(SILK_LINE))
        val off = lobby.listening == Listening.Off
        KitRules(p) {
            if (lobby.nearby.isEmpty() || off) {
                // Not an error and not a dialog: a house that has not been started yet looks
                // exactly like this, and the phone says what it is doing rather than what went
                // wrong. Three captions, the owner's words (D-309): still looking, looked and
                // found nothing, and the OS refusing the search.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).edgeLine(PanelSide.Bottom, p.line, 1.dp)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { KitCaption(lobby.listening.caption, p) }
            }
            lobby.nearby.forEach { home ->
                NetworkRow(
                    home.name, home.address, p,
                    // **The row no longer navigates beside the action** (E-PAY-6). It used to do
                    // both on the same tap, so a refused attach — a phone short a permission, and
                    // now a phone with no name on it — walked into a lobby it was not in. Where
                    // this lands is `FlowModel.attachToHome`'s, declared in [Flow.viaActions].
                    onClick = { actions.attachToHome(home) },
                )
            }
        }
        // **Local Network is off: the way to it, under the words** (D-309). PERMISSIONS' own control,
        // same copy and style, and the same verb.
        if (off) KitSecondary("OPEN SETTINGS", p, onClick = actions.openSystemSettings)
        KitSpacer()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // **What the last tap was refused with** — a permission still missing, or no name typed
            // (E-PAY-6). In full ink and at reading size, because this is often the reason the
            // screen changed **under** the player: a house that closed arrives by a push
            // (`LobbyModel.HOUSE_CLOSED`).
            lobby.refusal?.let { KitBody(it, p, Modifier.fillMaxWidth(), size = 22, align = TextAlign.Center) }
            KitSecondary("HOST A HOME INSTEAD", p) { go(ScreenId.Maps) }
        }
    }
}

/**
 * **The mockup's text input: a 60-tall field on a 2-unit rule in ink.** The field itself is the
 * caller's, so each keeps its own handle and its own name for a screen reader.
 */
@Composable
internal fun KitField(p: Palette, height: androidx.compose.ui.unit.Dp = 60.dp, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(height).edgeLine(PanelSide.Bottom, p.ink, 2.dp).padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** One home on the network: its name, and the address that tells two BUNGALOWs apart. */
@Composable
private fun NetworkRow(name: String, where: String, p: Palette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).edgeLine(PanelSide.Bottom, p.line, 1.dp)
            .tap(onClick).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Words(
            name, Modifier.weight(1f), size = 26, color = p.ink, lineHeight = 1.0,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Words(where, size = 20, color = p.dim, lineHeight = 1.0)
    }
}
