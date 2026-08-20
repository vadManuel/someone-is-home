package home.someoneshome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The device, showing one screen.
 *
 * The `when` is exhaustive over [ScreenId] deliberately: adding a screen without adding its
 * branch is a COMPILE ERROR, not a screen that silently renders nothing. Same discipline as the
 * recording transcript and the redaction schema — the failure mode must be "it didn't build",
 * never "it didn't appear".
 */
@Composable
fun Screen(state: PanelState, actions: PanelActions = PanelActions()) {
    val vals = PanelVals(state)
    CompositionLocalProvider(LocalActions provides actions) {
        PanelFrame(vals) {
            when (state.screen) {
                ScreenId.Boot -> BootScreen()
                ScreenId.Perms -> PermsScreen()
                ScreenId.Join -> JoinScreen()

                ScreenId.Maps -> MapsScreen()
                ScreenId.Editor -> EditorScreen(vals)
                ScreenId.RoomEdit -> RoomEditScreen(vals)
                ScreenId.PassageWarn -> PassageWarnScreen(vals)
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
                ScreenId.Notify -> NotifyScreen()
                ScreenId.Reveal -> RevealScreen(vals)
                ScreenId.RevealThread -> RevealThreadScreen(vals)
                ScreenId.Lock -> LockScreen()

                ScreenId.Home -> HomeScreen(vals)
                ScreenId.Page2 -> Page2Screen(vals)

                ScreenId.Banner -> BannerScreen()
                ScreenId.Work -> WorkScreen()
                ScreenId.Scan -> ScanScreen()
                ScreenId.ScanCaught -> ScanCaughtScreen(vals)
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
                ScreenId.Discussion -> DiscussionScreen()
                ScreenId.Vote -> VoteScreen()
                ScreenId.Tally -> TallyScreen()

                ScreenId.Revoked -> RevokedScreen()
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

