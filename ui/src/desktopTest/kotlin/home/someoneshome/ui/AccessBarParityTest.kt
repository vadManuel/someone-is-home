package home.someoneshome.ui

import home.someoneshome.model.Balance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import home.someoneshome.model.Haptic
import home.someoneshome.model.protocol.RoundMessage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Page 2 in pixels: the same on both roles until a fire, and the actor's own Access bar after
 * one** (D-142, D-239, D-262).
 *
 * `PublicCooldownTest` compares the values page 2 is drawn from; this compares what is drawn. Two
 * real [FlowModel]s, an Insider's and a Resident's, are sent the same pushes and counted the same
 * seconds, and page 2 is rendered from each. **The only pixels allowed to differ are the words
 * D-142 gives each role** (REVOKE / POWER, SURGE / BUS, the resting lines): every text whose string
 * differs between the two renders is masked out of both, and every other pixel, including the
 * words the two share, must be equal. A tell in this game is far more likely to be a shade than a
 * word (`SubroutineParityTest`).
 *
 * The renders land in `build/screens-access/` to be looked at.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class AccessBarParityTest {

    private val width = 360
    private val height = 760
    private val out = File("build/screens-access").apply { mkdirs() }

    private class Phone(insider: Boolean) {
        val flow = FlowModel(PanelState(screen = ScreenId.Lobby)).apply {
            houseSaid(RoundMessage.RoleRevealed(insider = insider))
        }
        fun said(message: RoundMessage) = flow.houseSaid(message)
        fun seconds(count: Int) = repeat(count) { flow.secondPassed() }
    }

    /** A render, and each text on it with where it was drawn, in drawing order. */
    private class Shot(val image: BufferedImage, val texts: List<Pair<String, Rect>>)

    private fun shot(phone: Phone, name: String): Shot {
        var shot: Shot? = null
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(phone.flow.state.copy(screen = ScreenId.Page2))
                        }
                    }
                }
            }
            val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } to node.boundsInRoot }
            shot = Shot(onRoot().captureToImage().toAwtImage(), texts)
        }
        val taken = shot ?: error("page 2 rendered nothing at all")
        ImageIO.write(taken.image, "png", File(out, "$name.png"))
        return taken
    }

    /**
     * How many pixels differ outside the words the two roles are given different strings for, and
     * the first place one does. The two renders must hold the same texts in the same order, or the
     * pages are not the same page and the count would mean nothing.
     */
    private fun differingOutsideRoleWords(a: Shot, b: Shot): Pair<Int, String?> {
        assertEquals(a.texts.size, b.texts.size, "the two roles' page 2 drew a different number of texts")
        val masks = a.texts.zip(b.texts)
            .filter { (x, y) -> x.first != y.first }
            .flatMap { (x, y) -> listOf(x.second, y.second) }
        fun masked(px: Int, py: Int) = masks.any { r ->
            px >= floor(r.left) - 1 && px <= ceil(r.right) + 1 && py >= floor(r.top) - 1 && py <= ceil(r.bottom) + 1
        }
        var count = 0
        var first: String? = null
        for (y in 0 until minOf(a.image.height, b.image.height)) {
            for (x in 0 until minOf(a.image.width, b.image.width)) {
                if (a.image.getRGB(x, y) != b.image.getRGB(x, y) && !masked(x, y)) {
                    count++
                    if (first == null) first = "($x,$y)"
                }
            }
        }
        return count to first
    }

    /**
     * **GUARD (D-142, D-239, D-262): before any fire, an Insider's page 2 and a Resident's are the
     * same pixels outside the role's own words, at every public moment; after the Insider's Surge
     * is answered, the actor's Access bar is not.**
     *
     * The second half is what keeps the first honest: an instrument that could not see a bar move
     * would pass parity on any two pages. It sees this one.
     */
    @Test
    fun page2IsTheSamePixelsOnBothRolesUntilAFireAndTheActorsBarAfter() {
        val insider = Phone(insider = true)
        val resident = Phone(insider = false)
        val both = listOf(insider, resident)
        fun at(moment: String, tag: String, step: (Phone) -> Unit) {
            both.forEach(step)
            val (count, first) = differingOutsideRoleWords(shot(insider, "$tag-insider"), shot(resident, "$tag-resident"))
            assertEquals(0, count, "page 2 differed by role at $moment, $count pixels, first at $first")
        }

        at("LIGHTS OUT", "1-lights-out") { it.said(RoundMessage.OpeningMessage(Haptic.Short, Balance.REVOKE_COOLDOWN)) }
        at("15 s in", "2-15s") { it.seconds(15) }
        at("the Access half run out", "3-access-ready") { it.seconds(PublicCooldown.Access.seconds) }

        insider.flow.fireSurge(0)
        insider.said(RoundMessage.AbilityFired(cooldownStarted = true))
        both.forEach { it.seconds(20) }
        val (count, first) = differingOutsideRoleWords(shot(insider, "4-surge-20s-insider"), shot(resident, "4-surge-20s-resident"))
        assertTrue(count > 0, "after the Surge the actor's page 2 still drew the pool as ready")
        println("after the Surge: $count pixels differ, first at $first")

        // A meeting's end puts every pool back on the public half, so the pages are one again.
        at("the meeting's end after the Surge", "5-meeting-end") { it.said(RoundMessage.MeetingEnded(Haptic.Long, Balance.REVOKE_COOLDOWN)) }
    }
}
