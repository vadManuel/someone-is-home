package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.Grant

/**
 * Cold start: the three screens before anyone has joined anything.
 *
 * Light-field, because the house lights are still on.
 */

/**
 * Self-test on power-up. Falls through on its own.
 *
 * It reports `PERIMETER . . . IDLE` and `UPLINK . . . CONNECTED` — both true at this moment, and
 * both about to stop being true. The screen does exposition the player will only understand in
 * retrospect, which is the correct amount of exposition for this game.
 */
@Composable
fun BootScreen() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.u, vertical = 12.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Label(
            "SOMEONE'S HOME",
            modifier = Modifier.padding(bottom = 8.u),
            size = 9.0, color = Amber.BoneInk, tracking = 0.2,
        )
        listOf(
            "MEM 640K . . . . . . . . . OK",
            "REGISTRY . . . . . 0 ENTRIES",
            "PERIMETER . . . . . . . IDLE",
            "LAMP ALLOCATION . . GRANTED",
        ).forEach { Label(it, size = 7.5, color = Amber.BoneDim, lineHeight = 1.9) }
        Label("UPLINK . . . . . CONNECTED", size = 7.5, color = Amber.BoneDeep, lineHeight = 1.9)

        Box(Modifier.weight(1f))

        Label("UPTIME 4331D 02:14", size = 7.5, color = Amber.BoneFaint, lineHeight = 1.9)
        Row(
            Modifier.fillMaxWidth().padding(top = 7.u).height(5.u),
            horizontalArrangement = Arrangement.spacedBy(1.u),
        ) {
            Box(Modifier.weight(7f).fillMaxHeight().background(Amber.BoneDim))
            Box(Modifier.weight(3f).fillMaxHeight().background(Amber.BonePale))
        }
        PreNote(
            "STARTING",
            modifier = Modifier.padding(top = 5.u),
            tracking = 0.14, lineHeight = 1.0, align = TextAlign.Center,
        )
    }
}

/**
 * Permissions, asked **at startup, in the light — never at arming** (D-161).
 *
 * Every input this game has is a permission: camera, local network, Bluetooth, motion,
 * notifications. A system prompt appearing mid-round would be an unauthored screen state at the
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
    // Read the OS when the screen arrives, and again on every return to it. A host who walked out
    // to Settings comes back here, and nothing else would move the rows.
    LaunchedEffect(perms) { perms.refresh() }
    PrePage {
        PreHeading("PERMISSIONS")
        InfoBox(border = Amber.BoneDim) {
            Label(
                "Grant these now, while the lights are on. Nothing will be asked of you again " +
                    "once the perimeter is armed.",
                size = 8.0, color = Amber.BoneInk, lineHeight = 1.6,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            DevicePermission.entries.forEach { permission ->
                val grant = perms.grant(permission)
                PreRow(
                    permission.label,
                    grant.label,
                    border = if (grant.opensTheGate) Amber.BonePale else Amber.BoneDim,
                    labelInk = if (grant.opensTheGate) Amber.BoneDeep else Amber.BoneInk,
                    valueInk = if (grant == Grant.Refused) Amber.Caution else Amber.BoneInk,
                    size = 7.5,
                    onClick = { actions.requestGrant(permission) },
                )
            }
        }
        PushDown()
        Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
            // What the last tap was refused with, or what the gate is still waiting for. Never
            // both, and never an invented sentence: a complete phone has nothing here at all.
            (perms.refusal ?: perms.gateRefusal())?.let {
                PreNote(it, color = Amber.Caution, align = TextAlign.Center)
            }
            PreNote("A SYSTEM PROMPT WILL APPEAR.\nIT IS NOT PART OF THE HOUSE.")
            // **A refused row cannot be un-refused from in here** — iOS asks once, ever, and the
            // second ask raises nothing. So the control appears exactly where its fix is outside
            // the app, which is [LobbyModel.offersTheGrant]'s rule read on this screen: a row
            // nobody has been asked for yet has its fix right here and is offered nothing.
            if (perms.states.values.any { it == Grant.Refused }) {
                PanelButton(
                    "OPEN SETTINGS",
                    border = Amber.BoneFaint, ink = Amber.BoneDim,
                    size = 7.5, verticalPadding = 9.u,
                    onClick = actions.openSystemSettings,
                )
            }
            if (perms.complete) {
                SlateButton("CONTINUE", { go(ScreenId.Join) })
            } else {
                // It asks, and it stays here. The one screen where a control that navigated
                // without doing anything cost an evening.
                SlateButton("REQUEST WHAT IS MISSING", actions.requestMissingGrants)
            }
        }
    }
}

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
    // Listening starts when the screen does. Nothing before this point has any business having
    // the radio on: permissions are granted one screen back, in the light.
    LaunchedEffect(lobby) { lobby.look() }
    PrePage {
        Column(
            Modifier.fillMaxWidth().border(1.u, Amber.BoneFaint)
                .padding(horizontal = 7.u, vertical = 5.u)
        ) {
            Label(
                "RESIDENT",
                modifier = Modifier.padding(bottom = 3.u),
                size = 6.0, color = Amber.BoneDim, tracking = 0.14,
            )
            ReadoutField(
                value = lobby.residentName,
                onValueChange = actions.nameResident,
                modifier = Modifier.fillMaxWidth(),
                size = 19.0, color = Amber.BoneInk, tracking = 0.08,
                hint = "YOUR NAME",
            )
        }

        Label("NETWORKS NEARBY", size = 7.0, color = Amber.BoneDim, tracking = 0.18)

        Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
            if (lobby.nearby.isEmpty()) {
                // Not an error and not a dialog: a house that has not been started yet looks
                // exactly like this, and the phone says what it is doing rather than what went
                // wrong.
                PreNote("LISTENING FOR A HOME ON THIS NETWORK", tracking = 0.12)
            }
            lobby.nearby.forEachIndexed { index, home ->
                // The first one answered is drawn at full intensity, as the design's strongest
                // signal was. It is the one most likely to be the house you are standing in.
                val strongest = index == 0
                NetworkRow(
                    home.name,
                    home.address,
                    if (strongest) Amber.BoneFaint else Amber.BonePale,
                    if (strongest) Amber.BoneInk else Amber.BoneDeep,
                    if (strongest) Amber.BoneDim else Amber.BoneFaint,
                ) {
                    actions.attachToHome(home)
                    go(ScreenId.Lobby)
                }
            }
        }

        PushDown()
        Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
            PanelButton(
                "HOST A HOME INSTEAD",
                border = Amber.BoneFaint, ink = Amber.BoneDim,
                size = 7.5, verticalPadding = 9.u,
                onClick = { go(ScreenId.Maps) },
            )
            PreNote(
                "DISCOVERY IS LOCAL ONLY\nNO ACCOUNT . NO INTERNET",
                tracking = 0.12, align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NetworkRow(
    name: String,
    where: String,
    border: Color,
    ink: Color,
    whereInk: Color,
    onClick: () -> Unit,
) {
    RowButton(border = border, onClick = onClick) {
        Label(name, size = 8.0, color = ink, tracking = 0.08, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Label(where, size = 6.0, color = whereInk, tracking = 0.08)
    }
}
