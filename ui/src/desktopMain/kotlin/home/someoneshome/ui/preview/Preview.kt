package home.someoneshome.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import home.someoneshome.ui.DeviceCanvas
import home.someoneshome.ui.PanelRole
import home.someoneshome.ui.PanelState
import home.someoneshome.ui.Screen
import home.someoneshome.ui.ScreenId

/**
 * A window that draws the device, so screens can be seen while they are written.
 *
 * **A development instrument, not part of the game.** It lives in the `desktop` target, which no
 * shipped variant builds. Its whole job is to turn "it compiles" into "I looked at it", because
 * every interesting failure in this port — a weight fighting an aspect ratio, a scaled density
 * getting type size wrong, a plan grid collapsing to nothing — is invisible to the compiler and
 * obvious on screen.
 *
 * Renders both roles side by side, always. Parity is the property most easily broken and least
 * easily noticed, and it is only visible in comparison.
 *
 *   ./gradlew :ui:run -Dscreen=perms
 */
fun main() = application {
    val name = System.getProperty("screen") ?: "boot"
    val id = ScreenId.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: error("no screen named '$name'. one of: ${ScreenId.entries.joinToString { it.name }}")

    Window(onCloseRequest = ::exitApplication, title = "Someone's Home — $id") {
        Row(
            Modifier.fillMaxSize().background(Color(0xFF070503)).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (role in PanelRole.entries) {
                Box(Modifier.size(600.dp, 800.dp).background(Color.Black)) {
                    DeviceCanvas { Screen(PanelState(screen = id, role = role)) }
                }
            }
        }
    }
}
