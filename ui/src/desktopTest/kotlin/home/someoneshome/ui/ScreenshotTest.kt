package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
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
            // Every line in and still short of D-128's party floor: the one state where the host
            // has done everything asked of them and the button is still dead. What needed a
            // person's eye is whether the line above it reads as an instruction or as a fault.
            "lobby-short" to lobbyOf(joined = 4, linesIn = 4, hosting = true),
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

    /**
     * **The three states of the return line, on all six Subroutines — the frames the sweep above
     * renders only one of.**
     *
     * Naming a [ScreenId] gets you a Subroutine mid-work: nothing has gone to the house, so the
     * line is empty. The two states that needed a person's eye are the ones the loop just built —
     * `RETURNED . WAITING`, and each of the house's two answers under it — and what has to be
     * checked there is not the words but **the light**: whether a longer line at the same
     * intensity reads as the same slot on the near-black screens as on the lit ones, and whether
     * anything above it moved when the answer arrived.
     *
     * Rendered handed-over, so the frame is the real one: the controls inert, the entry spent, and
     * the only thing that changed between the three shots being the one line. A viewer, not an
     * assertion — `SubroutineParityTest` is where the assertions are.
     */
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderEverySubroutinesVerdict() {
        var written = 0
        for (subroutine in Subroutine.built) {
            for (verdict in listOf(null) + SubroutineVerdict.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    val model = SubroutineModel()
                    repeat(SubroutineModel.HANDSHAKE_BEATS) { model.tap(subroutine, it) }
                    model.handOver(subroutine)
                    setContent {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            DeviceCanvas {
                                Screen(
                                    PanelState(screen = subroutine.screen!!, verdict = verdict),
                                    subroutines = model,
                                )
                            }
                        }
                    }
                    val tail = verdict?.name?.lowercase() ?: "waiting"
                    val name = "${subroutine.screen!!.name.lowercase()}-$tail.png"
                    ImageIO.write(
                        onRoot().captureToImage().toAwtImage(), "png", File(out, name),
                    )
                    written++
                }
            }
        }
        println("wrote $written verdict frames to ${out.absolutePath}")
    }

    /**
     * **A banner on its way out — the other state no [ScreenId] can name.**
     *
     * `Notify` is the springboard with a banner at rest on it, and that is as far as a screen id
     * goes. The gesture in between is a position, and a position is not a screen: how far the
     * banner has travelled, how much of it has gone under the status row, and what the panel
     * behind it looks like at [NOTIFIED_DIM] while it goes.
     *
     * Three frames, rendered against a phone with a notch so the clipping boundary is where the
     * hardware really puts it. A viewer, not an assertion — `NotificationInputTest` is where the
     * assertions are.
     */
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderABannerBeingSwipedAway() {
        val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)
        // At rest, a third of the way up, and far enough that it is going.
        val travel = listOf("banner-rest" to 0f, "banner-swiping" to 40f, "banner-going" to 90f)
        for ((name, lift) in travel) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                val model = FlowModel(PanelState(screen = ScreenId.Notify))
                setContent {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        DeviceCanvas(insets = insets) {
                            Screen(model.state, model.actions(), model.editor, model.homes, model.lobby)
                        }
                    }
                }
                if (lift > 0f) {
                    onNode(hasText(Notifications.opening.body, substring = true)).performTouchInput {
                        down(center)
                        repeat(6) { moveBy(Offset(0f, -lift / 6)) }
                    }
                    waitForIdle()
                }
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"),
                )
            }
        }
        println("wrote ${travel.size} banner frames to ${out.absolutePath}")
    }

    /**
     * **A notification going sideways off the lock screen — the other state no [ScreenId] names.**
     *
     * The lock screen's gesture is a swipe *left*, and its notifications are a list rather than a
     * single banner, which makes two things worth a person's eye and invisible to any sweep: what
     * a half-swiped row looks like against the amber field it is leaving, and whether the rows
     * below it hold their place while the one above them goes.
     *
     * Rendered on the arriving-notification screen so the darkened field is in shot too — the
     * whole point of that dim is that the bright card is the only thing left, and a screenshot is
     * the only way to check that claim about light.
     */
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderALockScreenNotificationBeingSwipedAway() {
        val insets = PanelInsets(top = 45.dp, bottom = 25.dp, side = 12.dp)
        val travel = listOf("lock-rest" to 0f, "lock-swiping" to 40f, "lock-going" to 110f)
        for ((name, push) in travel) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                val model = FlowModel(PanelState(screen = ScreenId.LockNotify))
                setContent {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        DeviceCanvas(insets = insets) {
                            Screen(
                                model.state, model.actions(), model.editor, model.homes,
                                model.lobby, notifications = model.notifications,
                            )
                        }
                    }
                }
                if (push > 0f) {
                    onNode(hasText(Notifications.opening.body, substring = true)).performTouchInput {
                        down(center)
                        repeat(6) { moveBy(Offset(-push / 6, 0f)) }
                    }
                    waitForIdle()
                }
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"),
                )
            }
        }
        println("wrote ${travel.size} lock screen frames to ${out.absolutePath}")
    }

    /**
     * **The meeting's echo states, which are the other half of four screens.**
     *
     * The sweep above renders every screen from a [ScreenId], and for these four that is only the
     * state a player arrives in. What a press *looks* like — a spent check-in, a hand up, a ballot
     * with nothing on it, a vote the house is holding — is a fact about the [MeetingModel] beside
     * the panel, so none of it has a screen id and none of it is in the sweep.
     *
     * These are the frames worth a person's eye: every one of them is a control that has kept its
     * place and lost its press, and "present and inert" is a thing you can only really check by
     * looking at it. A viewer, not an assertion — `MeetingInputTest` is where the assertions are.
     */
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderTheMeetingsEchoStates() {
        val frames: List<Triple<String, ScreenId, MeetingModel.() -> Unit>> = listOf(
            Triple("meeting-checked-in", ScreenId.Assemble) { checkIn() },
            Triple("meeting-checked-in-out", ScreenId.Ghost2) { checkIn() },
            Triple("meeting-ready", ScreenId.Discussion) { sayReady() },
            // A ballot nobody has touched: READY present, and visibly not a control.
            Triple("meeting-vote-untouched", ScreenId.Vote) { meetingBegan() },
            Triple("meeting-vote-skip", ScreenId.Vote) { choose(VoteChoice.Skip) },
            Triple("meeting-vote-locked", ScreenId.Vote) { readyToVote() },
        )
        for ((name, screen, set) in frames) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                val meeting = MeetingModel.sample().apply(set)
                setContent {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        DeviceCanvas {
                            Screen(PanelState().arrivingAt(screen), meeting = meeting)
                        }
                    }
                }
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"),
                )
            }
        }
        println("wrote ${frames.size} meeting frames to ${out.absolutePath}")
    }

    /**
     * **The Subroutines' echo states, which are most of what a Subroutine screen ever is.**
     *
     * The sweep above renders each of the six from its [ScreenId] and gets the fixture's
     * part-way-through state, which is one frame out of a set where every frame is the interesting
     * one. What a Subroutine looks like when nobody has touched it — a screen with no echo at all,
     * the thing a player actually walks onto — and what it looks like once the entry has gone to
     * the house are facts about the [SubroutineModel] beside the panel, so neither has a screen id.
     *
     * The handed-over frames are the ones worth a person's eye. Every control on them has kept its
     * place and lost its press, RETURNED . WAITING has appeared in a slot that was already there,
     * and *nothing says whether it worked* — which is easy to assert and much harder to be sure
     * reads as deliberate rather than as a screen that failed to update. A viewer, not an assertion
     * — `SubroutineParityTest` is where the assertions are.
     */
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun renderTheSubroutinesEchoStates() {
        var written = 0
        for (subroutine in Subroutine.built) {
            val slug = subroutine.label.lowercase().replace(' ', '-')
            val frames = listOf<Pair<String, SubroutineModel.() -> Unit>>(
                "untouched" to { },
                "handed-over" to {
                    // Enough taps to finish the longest of the six, then one past it: an entry
                    // that has gone takes nothing more, so the extra is drawn as a no-op on
                    // purpose rather than being carefully avoided. Then the hand-over, which four
                    // of the six need and the two sequences have already done for themselves.
                    repeat(SubroutineModel.HANDSHAKE_BEATS + 1) { at ->
                        tap(
                            subroutine,
                            when (subroutine) {
                                Subroutine.Replay -> at % SubroutineModel.REPLAY_DOTS
                                Subroutine.Jam -> -1
                                Subroutine.Short -> SubroutineModel.SHORT_FINGERS
                                Subroutine.SignalTrace -> at.mod(
                                    SignalGraph.of(SubroutineModel.TRACE_SEED).nodes.size,
                                )
                                else -> at
                            },
                        )
                    }
                    handOver(subroutine)
                },
            )
            for ((state, set) in frames) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    val model = SubroutineModel().apply(set)
                    setContent {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            DeviceCanvas {
                                Screen(
                                    PanelState(screen = subroutine.screen!!),
                                    subroutines = model,
                                )
                            }
                        }
                    }
                    ImageIO.write(
                        onRoot().captureToImage().toAwtImage(), "png",
                        File(out, "$slug-$state.png"),
                    )
                    written++
                }
            }
        }
        println("wrote $written Subroutine frames to ${out.absolutePath}")
    }

    private fun lobbyOf(joined: Int, linesIn: Int, hosting: Boolean): LobbyModel {
        val home = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)
        val model = LobbyModel(
            MemoryHomeFinder(listOf(home)),
            MemoryLobbyLink(joined = joined, linesIn = linesIn),
            hosting = hosting,
        )
        model.look()
        // Named before attaching, because attaching is what carries the name up (D-115) — a
        // screenshot of a lobby with an unnamed seat in it would be a picture of the odd case.
        model.nameResident("ELLIOT")
        model.attachTo(home)
        return model
    }
}
