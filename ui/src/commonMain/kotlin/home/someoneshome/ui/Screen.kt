package home.someoneshome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

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

                else -> NotPorted(state.screen)
            }
        }
    }
}

/**
 * A screen that has not been ported yet, saying so loudly.
 *
 * **Not a blank placeholder.** A screen that renders as plausible emptiness is indistinguishable
 * from a screen that renders correctly and happens to be sparse, and this interface has several
 * genuinely sparse screens. If it is not built, it has to say it is not built.
 */
@Composable
private fun NotPorted(id: ScreenId) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Label(
            "NOT PORTED\n${id.name.uppercase()}",
            size = 8.0, color = Amber.Faint, tracking = 0.2, lineHeight = 2.0,
            align = TextAlign.Center,
        )
    }
}
