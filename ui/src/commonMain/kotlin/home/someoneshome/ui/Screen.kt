package home.someoneshome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxScope

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
) {
    val vals = PanelVals(state)
    CompositionLocalProvider(
        LocalActions provides actions,
        LocalEditor provides editor,
        LocalHomes provides homes,
        LocalLobby provides lobby,
        LocalMeeting provides meeting,
    ) {
        PanelFrame(vals, overlay = bannerFor(state.screen)) {
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
                ScreenId.NoTerminal -> NoTerminalScreen()
                ScreenId.Floors -> FloorsScreen()
                ScreenId.SaveName -> SaveNameScreen()
                ScreenId.HomeDetail -> HomeDetailScreen()
                ScreenId.Delete -> DeleteScreen()
                ScreenId.Lobby -> LobbyScreen()

                ScreenId.Secret -> SecretScreen()
                ScreenId.Armed -> ArmedScreen()
                // Both banner screens draw the springboard they interrupted, dimmed by the
                // frame, and hand the banner itself up as an overlay so it stays at full
                // intensity. "A banner, not a takeover" is only true if what was underneath is
                // still visible and still recognisable.
                ScreenId.Notify -> HomeScreen(vals)
                ScreenId.Reveal -> RevealScreen(vals)
                ScreenId.RevealThread -> RevealThreadScreen(vals)
                ScreenId.Lock -> LockScreen()

                ScreenId.Home -> HomeScreen(vals)
                ScreenId.Page2 -> Page2Screen(vals)

                ScreenId.Banner -> HomeScreen(vals)
                ScreenId.Work -> WorkScreen(vals)
                ScreenId.Scan -> ScanScreen(vals)
                ScreenId.ScanCaught -> ScanCaughtScreen(vals)
                ScreenId.ScanBad -> ScanBadScreen()
                ScreenId.ScanUnknown -> ScanUnknownScreen()
                ScreenId.Sub -> SubScreen()
                ScreenId.SubBright -> SubBrightScreen()
                ScreenId.Files -> FilesScreen()
                ScreenId.Notes -> NotesScreen(vals)
                ScreenId.TermNo -> TermNoScreen()
                ScreenId.TermLive -> TermLiveScreen()
                ScreenId.Timelapse -> TimelapseScreen()
                ScreenId.EgressWidget -> EgressWidgetScreen(vals)

                ScreenId.Calling -> CallingScreen()
                ScreenId.Call -> CallScreen()
                ScreenId.Found -> FoundScreen()
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
                ScreenId.WinResidents -> WinResidentsScreen()
            }
        }
    }
}

/**
 * The full-intensity thing on top, for the two screens that have one.
 *
 * Returning non-null is what tells [PanelFrame] to dim the panel behind it, so "this screen has a
 * banner" and "this screen is dimmed" cannot drift apart.
 *
 * **Which notification, and whether there is one at all, is [Notifications]' answer, not this
 * function's.** The banner is one composable taking one piece of data — there is no
 * per-notification composable to forget to write, and adding a kind cannot produce a banner drawn
 * differently from its two siblings.
 */
@Composable
private fun bannerFor(screen: ScreenId): (@Composable BoxScope.() -> Unit)? {
    val notification = Notifications.onScreen(screen) ?: return null
    return { NotificationBanner(notification) }
}

