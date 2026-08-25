package home.someoneshome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/**
 * The device, showing one screen.
 *
 * The `when` is exhaustive over [ScreenId] deliberately: adding a screen without adding its
 * branch is a COMPILE ERROR, not a screen that silently renders nothing. Same discipline as the
 * recording transcript and the redaction schema — the failure mode must be "it didn't build",
 * never "it didn't appear".
 */
@Composable
fun Screen(
    state: PanelState,
    actions: PanelActions = PanelActions(),
    /**
     * The plan the host-setup screens draw.
     *
     * Defaulted and `remember`ed rather than read from a global: every test that renders a screen
     * then gets its own, so a test that fires every control on the editor cannot leave a
     * half-deleted house behind for the next one. In the app [FlowHost] passes the one the round
     * is being set up with.
     */
    editor: HomeEditorModel = remember { HomeEditorModel.bungalow() },
    /**
     * The homes this phone holds.
     *
     * Defaulted the same way and for the same reason: a test that deletes a home must not leave
     * the next one with a shorter list than it expected. The default is the design's own three,
     * with the bungalow open — which is the state every host-setup screen was drawn in.
     */
    homes: SavedHomesModel = remember { SavedHomesModel.sample() },
    /**
     * The lobby this phone is in.
     *
     * Defaulted and `remember`ed for the reason the two above are: a test that hands a one line
     * over must not leave the next render looking at a lobby already one line further on. The
     * default is the design's own — three networks nearby, six seats, four lines in — because a
     * lobby with nobody in it is a screen the app never shows.
     */
    lobby: LobbyModel = remember { LobbyModel.sample() },
    /**
     * What this phone has said at the meeting it is at.
     *
     * Defaulted and `remember`ed for the reason the three above are: a test that checks in or
     * votes must not leave the next render looking at a phone that has already done both. The
     * default is the design's own meeting — counts from the house and five names to vote for —
     * because a meeting with nobody at it is a screen the app never shows.
     */
    meeting: MeetingModel = remember { MeetingModel.sample() },
    /**
     * What this phone has entered into the Subroutine it has open.
     *
     * Defaulted and `remember`ed for the reason the four above are: a test that taps four dots
     * must not leave the next render looking at a phone that has already handed its sequence over.
     * The default is part-way through each of the three, because a Subroutine nobody has touched
     * is a screen with no echo on it — and the echo is the entire content of these screens.
     */
    subroutines: SubroutineModel = remember { SubroutineModel.sample() },
    /**
     * What is still standing on this phone's lock screen.
     *
     * Defaulted and `remember`ed for the reason the five above are: a test that swipes a
     * notification away must not leave the next render looking at a phone with one fewer on it.
     * The default is the design's own two, because a lock screen is the one surface in this app
     * that is *supposed* to have yesterday's news on it.
     */
    notifications: NotificationsModel = remember { NotificationsModel() },
    /**
     * **What the OS has said about this phone** (D-161).
     *
     * Defaulted to a phone whose setup is done, for the reason the six above are defaulted to a
     * furnished state: every screen after this one is a screen a host reaches *through* the gate,
     * so a fixture that failed it would draw the rest of the app in a state no player ever sees. In
     * the app [FlowHost] passes the one wired to `devicePermissions()`.
     */
    perms: PermsModel = remember { PermsModel.sample() },
    /**
     * **The lamp — the light the house set, and the Surge's slam over the top of it.**
     *
     * Defaulted and `remember`ed for the reason the six above are, and it is the one of the seven
     * that is deliberately **not** part of [PanelState]: a lamp change must invalidate draw and
     * nothing else (rule 7), so it is two `MutableIntState`s read inside draw lambdas. See
     * [LampState]. In the app [FlowHost] passes the one the round is writing to.
     */
    lamp: LampState = remember { LampState() },
) {
    val vals = PanelVals(state)
    CompositionLocalProvider(
        LocalLamp provides lamp,
        LocalActions provides actions,
        LocalEditor provides editor,
        LocalHomes provides homes,
        LocalLobby provides lobby,
        LocalMeeting provides meeting,
        LocalSubroutine provides subroutines,
        LocalNotifications provides notifications,
        LocalPerms provides perms,
    ) {
        // THE FRAME DIMS FOR A HEAVY BANNER AND FOR NOTHING ELSE. `dims` is the house-wide
        // question — is one of D-118's two events on this screen — and the answer is yes on the
        // lock screen too; but there the dim cannot be `alpha` over the panel, because the amber
        // field IS the emitted light and the notification has to stay at full intensity while
        // everything around it drops. [LockScreen] implements the same ruling on its own ground.
        // Dimming here as well would darken the one bright thing and dim the house twice.
        val arrival = vals.arrival
        // **THE LAMP IS OVER EVERYTHING AND IS ALWAYS DRAWN** (rule 5, rule 7, D-182). Unset it is
        // fully transparent, so this costs one `drawRect` of nothing on every screen in the game
        // and no branch in composition; during a Surge it is the whole screen at the luminance the
        // rules sent. Being unconditional is the point — a lamp that appeared and disappeared from
        // the tree would be a recomposition at the one moment there must not be one.
        Box(Modifier.fillMaxSize()) {
        PanelFrame(
            vals,
            overlay = bannerFor(vals),
            dimmed = Notifications.dims(state.screen) &&
                arrival?.presentation == Presentation.Banner,
        ) {
            when (state.screen) {
                ScreenId.Boot -> BootScreen()
                ScreenId.Perms -> PermsScreen()
                ScreenId.Join -> JoinScreen()

                ScreenId.Maps -> MapsScreen()
                ScreenId.Editor -> EditorScreen(vals)
                ScreenId.RoomEdit -> RoomEditScreen(vals)
                ScreenId.StairsWarn -> StairsWarnScreen(vals)
                ScreenId.MarkerSheet -> MarkerSheetScreen(vals)
                ScreenId.ScanMarker -> ScanMarkerScreen(vals)
                ScreenId.TermTaken -> TermTakenScreen()
                ScreenId.TermRemove -> TermRemoveScreen()
                ScreenId.MeetTaken -> MeetTakenScreen()
                ScreenId.MeetRemove -> MeetRemoveScreen()
                ScreenId.ReviewNeeds -> ReviewNeedsScreen(vals)
                ScreenId.Floors -> FloorsScreen()
                ScreenId.SaveName -> SaveNameScreen()
                ScreenId.HomeDetail -> HomeDetailScreen()
                ScreenId.Delete -> DeleteScreen()
                ScreenId.Lobby -> LobbyScreen(vals)

                ScreenId.Secret -> SecretScreen()
                ScreenId.Armed -> ArmedScreen()
                // Every banner screen draws the springboard it interrupted and hands the banner
                // itself up as an overlay. "A banner, not a takeover" is only true if what was
                // underneath is still visible and still recognisable — which is also why only the
                // two heavy ones dim it (D-118), and why the third looks like the panel it is on.
                ScreenId.Notify -> HomeScreen(vals)
                ScreenId.Reveal -> RevealScreen(vals)
                ScreenId.RevealThread -> RevealThreadScreen(vals)
                // The lantern, and the lantern with something arriving under its clock. One
                // composable: a lock screen that drew its notifications differently depending on
                // whether one was landing would be two lock screens.
                ScreenId.Lock, ScreenId.LockNotify -> LockScreen(vals)

                ScreenId.Home -> HomeScreen(vals)
                ScreenId.Page2 -> Page2Screen(vals)
                ScreenId.SurgeTargets -> SurgeTargetsScreen(vals)
                ScreenId.IsolateTargets -> IsolateTargetsScreen(vals)
                ScreenId.Quiet -> HomeScreen(vals)

                ScreenId.Banner -> HomeScreen(vals)
                ScreenId.Work -> WorkScreen(vals)
                ScreenId.Scan -> ScanScreen(vals)
                ScreenId.ScanCaught -> ScanCaughtScreen(vals)
                ScreenId.ScanBad -> ScanBadScreen()
                ScreenId.ScanUnknown -> ScanUnknownScreen()
                // The verdict, and DELIBERATELY NOT `vals`. Every other screen that needs
                // anything derived takes the whole `PanelVals`, which carries `insider` -- and
                // handing that to a Subroutine screen would make "no role reaches this file" a
                // convention rather than a fact about what the six functions can see. One
                // nullable field is the only thing the house says to a Subroutine.
                ScreenId.SubHandshake -> HandshakeScreen(state.verdict)
                ScreenId.SubReplay -> ReplayScreen(state.verdict)
                ScreenId.SubParity -> ParityCheckScreen(state.verdict)
                ScreenId.SubShort -> ShortScreen(state.verdict)
                ScreenId.SubTrace -> SignalTraceScreen(state.verdict)
                ScreenId.SubJam -> JamScreen(state.verdict)
                ScreenId.SubSniff -> SniffScreen(state.verdict)
                ScreenId.SubDeallocate -> DeallocateScreen(state.verdict)
                ScreenId.SubInterrupt -> InterruptScreen(state.verdict)
                ScreenId.SubDrift -> DriftScreen(state.verdict)
                ScreenId.Files -> FilesScreen()
                ScreenId.Notes -> NotesScreen(vals)
                ScreenId.TermNo -> TermNoScreen()
                ScreenId.TermLive -> TermLiveScreen(vals)
                ScreenId.Timelapse -> TimelapseScreen(vals)
                ScreenId.EgressWidget -> EgressWidgetScreen(vals)

                ScreenId.Calling -> CallingScreen()
                ScreenId.Call -> CallScreen(vals)
                ScreenId.Found -> FoundScreen(vals)
                ScreenId.Assemble -> AssembleScreen()
                ScreenId.Notice -> NoticeScreen()
                ScreenId.Discussion -> DiscussionScreen(vals)
                ScreenId.Vote -> VoteScreen(vals)
                ScreenId.Tally -> TallyScreen(vals)

                ScreenId.Revoked -> RevokedScreen()
                ScreenId.Restrained -> RestrainedScreen()
                ScreenId.Ghost2 -> Ghost2Screen()
                ScreenId.GhostMeeting -> GhostMeetingScreen(vals)
                ScreenId.Ghost3 -> Ghost3Screen(vals)
                ScreenId.Disconnect -> DisconnectScreen()
                ScreenId.Settings -> SettingsScreen()
                ScreenId.WinInsiders -> WinInsidersScreen(vals)
                ScreenId.WinResidents -> WinResidentsScreen(vals)
            }
        }
            Lamp(lamp.slam)
        }
    }
}

/**
 * **The lamp, reachable from a screen without being handed down through every one of them.**
 *
 * `static` for the same reason the other seven locals are: it is set once per [Screen] and never
 * varies within a composition, so nothing that reads it takes a recomposition dependency on the
 * provider. That matters more here than anywhere else — the whole point of [LampState] is that a
 * light change costs a draw and nothing above it.
 *
 * The default is a fresh one, so a composable rendered outside [Screen] draws at whatever its own
 * screen decided rather than crashing. A lamp nobody has set is [LampState.UNSET], which the
 * surfaces read as *the house has said nothing* and never as *the house said dark*.
 */
val LocalLamp: ProvidableCompositionLocal<LampState> = staticCompositionLocalOf { LampState() }

/**
 * The thing on top, for the screens that have one.
 *
 * **This no longer decides the dim.** It used to: returning non-null was what dropped the panel to
 * [NOTIFIED_DIM], on the argument that the two could then never drift apart. D-118 ruled that they
 * are not the same fact — the dim belongs to two named events and a banner belongs to any
 * notification at all — so the panel is told both, separately, and [Notifications.dims] is the one
 * place the second is answered.
 *
 * **The lock screen's arrivals are not here.** A notification landing on the lantern goes *under
 * the clock*, inside [LockScreen], because that screen is already an arrangement around a clock
 * and a banner across the top of it would be a second idiom on the one screen that has no room for
 * one. [Presentation] is where that split is written down.
 *
 * **Which notification, and whether there is one at all, is [Notifications]' answer, not this
 * function's.** The banner is one composable taking one piece of data — there is no
 * per-notification composable to forget to write, and adding a kind cannot produce a banner drawn
 * differently from its siblings.
 */
@Composable
private fun bannerFor(vals: PanelVals): (@Composable BoxScope.() -> Unit)? {
    // `vals.arrival` and not `Notifications.arrivals[screen]`: the Egress alert's rooms are round
    // data, and the widget behind it reads them off the same field. See PanelVals.arrival.
    val arrival = vals.arrival ?: return null
    if (arrival.presentation != Presentation.Banner) return null
    return { NotificationBanner(arrival.notification) }
}

