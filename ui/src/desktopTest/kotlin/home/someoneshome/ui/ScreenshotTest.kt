package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders every screen to a PNG so they can be looked at.
 *
 * **This is the instrument that makes the rest of the port honest.** Layout in Compose fails at
 * runtime, silently: a `weight` inside a wrap-content parent, an `aspectRatio` fighting a fill, a
 * plan grid measuring to zero. None of it is visible to the compiler, and a screen that renders
 * as an empty black rectangle looks exactly like a screen that is meant to be sparse.
 *
 * Both roles are rendered for every screen, side by side, because parity is the property most
 * easily broken and least easily noticed — and it only exists in comparison.
 *
 * Output lands in `ui/build/screens/`. Not an assertion and not a regression test: there is no
 * golden image to diff against, because there is no prior correct version. It is a viewer.
 */
class ScreenshotTest {

    private val out = File("build/screens").apply { mkdirs() }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderEveryScreen() {
        val w = 300
        val h = 400
        var written = 0

        for (id in ScreenId.entries) {
            // The out-screens are meaningless without a cause: the status bar names what
            // happened to you, and there is no sensible default. Ghost2/Ghost3/GhostMeeting are
            // reached by both routes -- rendered here as revoked, which is the route the design
            // fixture cannot express.
            val outBy = when (id) {
                ScreenId.Restrained -> OutBy.Restrained
                ScreenId.Revoked, ScreenId.Ghost2, ScreenId.Ghost3, ScreenId.GhostMeeting ->
                    OutBy.Revoked
                else -> null
            }
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = w * 2, height = h * 2) {
                    setContent {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            Box(Modifier.size((w * 2).dp, (h * 2).dp)) {
                                DeviceCanvas {
                                    Screen(PanelState(screen = id, role = role, outBy = outBy))
                                }
                            }
                        }
                    }
                    val img = onRoot().captureToImage().toAwtImage()
                    val name = "${id.name.lowercase()}-${role.name.lowercase()}.png"
                    ImageIO.write(img, "png", File(out, name))
                    written++
                }
            }
        }
        println("wrote $written screenshots to ${out.absolutePath}")
    }

    /**
     * **The lobby's other three states, which the loop above cannot reach.**
     *
     * Every screen in this game is drawn from a [PanelState] and can therefore be rendered by
     * naming a [ScreenId] — except this one. The lobby draws counts that arrive off the wire and a
     * control only the host has, so *which* lobby you are looking at is a fact about the
     * [LobbyModel] beside the panel, not about the screen id. Three of the four states it can be
     * in are consequently invisible to the sweep above:
     *
     * - **empty** — attached to a house nobody has joined, which is what a host sees for the first
     *   thirty seconds every single evening;
     * - **client** — no settings control and no LIGHTS OUT, because both are the host's;
     * - **ready** — every line in, the gate open, the one moment the commit button is live.
     *
     * A viewer, not an assertion, exactly as above.
     */
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderTheLobbysOtherStates() {
        val states = listOf(
            "lobby-empty" to lobbyOf(joined = 0, linesIn = 0, hosting = true),
            "lobby-client" to lobbyOf(joined = 6, linesIn = 4, hosting = false),
            "lobby-ready" to lobbyOf(joined = 6, linesIn = 6, hosting = true),
        )
        for ((name, lobby) in states) {
            runDesktopComposeUiTest(width = 600, height = 800) {
                setContent {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Box(Modifier.size(600.dp, 800.dp)) {
                            DeviceCanvas {
                                Screen(PanelState(screen = ScreenId.Lobby), lobby = lobby)
                            }
                        }
                    }
                }
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"),
                )
            }
        }
        println("wrote ${states.size} lobby states to ${out.absolutePath}")
    }

    private fun lobbyOf(joined: Int, linesIn: Int, hosting: Boolean): LobbyModel {
        val home = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)
        val model = LobbyModel(
            MemoryHomeFinder(listOf(home)),
            MemoryLobbyLink(joined = joined, linesIn = linesIn),
            hosting = hosting,
        )
        model.look()
        model.attachTo(home)
        return model
    }
}
