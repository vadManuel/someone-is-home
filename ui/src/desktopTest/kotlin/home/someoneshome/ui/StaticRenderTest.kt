package home.someoneshome.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **STATIC on the surfaces that exist, and off the ones the ruling exempts** (D-186).
 *
 * *House-wide to the living. Ghosts exempt.* The allowlist is what stops a jam ever reaching a
 * ghost's phone (`EmitSchemaTest.static reaches both living classes and neither out class`), and
 * this is the other half of that guarantee: **even handed a jam, the couch's map does not draw one.**
 * Two independent denials, exactly as the presence plane has, because a row written by mistake is a
 * different mistake from a screen that decided to render it.
 *
 * ### Which surfaces are covered, and which are not built yet
 *
 * E8 — the Terminal — is design-complete and build-pending, so what exists to jam is the live view
 * (`TermLive`) and the Timelapse playback, both drawn over fixture counts. Those two get the render.
 * **The rest of the monitoring surfaces get it as they are built**, and this file is where the sweep
 * to catch them should go.
 */
@OptIn(ExperimentalTestApi::class)
class StaticRenderTest {

    private val jam = StaticJam(seconds = 10, seed = 20260823L)

    private fun at(screen: ScreenId, jammed: Boolean, out: Boolean = false) = PanelState(
        screen = screen,
        outBy = if (out) OutBy.Revoked else null,
        staticJam = if (jammed) jam else null,
    )

    private fun veils(state: PanelState): Int {
        var found = 0
        runDesktopComposeUiTest(width = 300, height = 650) {
            setContent { DeviceCanvas(insets = PanelInsets()) { Screen(state) } }
            // Counted rather than asserted present, so the same call answers both directions and
            // neither is a differently-shaped assertion from the other.
            found = onAllNodesWithTag(STATIC_VEIL).fetchSemanticsNodes().size
        }
        return found
    }

    /**
     * **The Terminal's live view drowns while the house is jammed, and is clean when it is not.**
     *
     * Both directions in one test, because either alone passes on a build that is wrong the other
     * way: a surface that always draws noise satisfies *a jam is visible*, and one that never draws
     * it satisfies *an unjammed Terminal is readable*.
     *
     * **The plan, the labels and the counts are still drawn underneath** — that is `StaticNoise`'s
     * whole arrangement and the reason a jammed Terminal does not read as a broken one. It is not
     * asserted here in pixels; what is asserted is that the veil is a layer over the surface rather
     * than a replacement for it, which is what the node count says.
     */
    @Test
    fun theTerminalsLiveViewIsJammedOnlyWhileTheHouseIs() {
        assertEquals(1, veils(at(ScreenId.TermLive, jammed = true)), "the live view drew no noise")
        assertEquals(
            0, veils(at(ScreenId.TermLive, jammed = false)),
            "the live view drew noise with no jam running",
        )
    }

    /**
     * **A Timelapse crossing the window renders as static** (D-186 — *~1.7s of noise at 6×*).
     *
     * ⚠️ **Over a fixture playback**, because that is what this screen is: there is no recorded
     * history behind the numbers yet and nothing on this phone knows which second of a playback it
     * is showing. So what is asserted is that a playback watched during a jam is noise — the right
     * picture for a reason narrower than the ruling's — and the seconds-crossing arithmetic belongs
     * to whoever builds the real playback. Written down rather than left to be discovered.
     */
    @Test
    fun theTimelapseIsJammedOnlyWhileTheHouseIs() {
        assertEquals(1, veils(at(ScreenId.Timelapse, jammed = true)), "the playback drew no noise")
        assertEquals(
            0, veils(at(ScreenId.Timelapse, jammed = false)),
            "the playback drew noise with no jam running",
        )
    }

    /**
     * **⚠️ THE GHOSTS ARE EXEMPT, AND THE SCREEN IS THE SECOND DENIAL** (D-186, M12, D-134).
     *
     * The couch's map draws **true occupancy** and it stays clean through a jam. A ghost is outside
     * the house's registry, a jam is the house's own monitoring degrading, and a ghost's occupancy
     * is not the house's monitoring — so this is the liveness privilege stated once more, and it
     * costs nothing because a ghost with a clean map cannot tell anybody.
     *
     * **Handed a jam it has no lawful way to receive**, on purpose. The allowlist already refuses
     * the kind to both out classes; this asserts that a build which widened that row would still not
     * put noise on a ghost's map. Neither denial subsumes the other, and the failure this catches is
     * somebody laying the veil over `LivePlan` itself — where all three maps would inherit it in one
     * line.
     */
    @Test
    fun aGhostsMapStaysCleanEvenWhenHandedAJam() {
        assertEquals(
            0, veils(at(ScreenId.Ghost3, jammed = true, out = true)),
            "a ghost's true occupancy went to noise — the exemption is gone",
        )
        assertEquals(
            0, veils(at(ScreenId.Ghost3, jammed = false, out = true)),
            "a ghost's map drew noise with no jam at all",
        )
    }

    /**
     * **The noise is a pure function of the seed and the frame** (rule 5 — light is game state).
     *
     * *No local animation, tween or easing the core did not emit.* Asserted on the field rather than
     * on pixels, because the field is where the claim lives: the same jam draws the same picture on
     * six phones and again on every replay, and a build that reached for a random source would fail
     * the first line here without anything having to be rendered.
     *
     * Three claims and each is a different failure: **determinism** (a seed and a frame decide the
     * picture), **separation** (two jams do not draw the same picture), and **motion** (successive
     * frames of one jam differ, so the noise animates rather than sitting still).
     */
    @Test
    fun theNoiseFieldIsDeterministicFromTheSeed() {
        val a = Static.field(jam.seed, frame = 3)
        assertTrue(
            a.toList() == Static.field(jam.seed, frame = 3).toList(),
            "the same seed and frame drew two different fields — the noise is not authored",
        )
        assertTrue(
            a.toList() != Static.field(jam.seed + 1, frame = 3).toList(),
            "two jams draw the same field, so the seed decides nothing",
        )
        assertTrue(
            a.toList() != Static.field(jam.seed, frame = 4).toList(),
            "successive frames are identical, so the noise does not animate",
        )
        assertEquals(
            Static.COLS * Static.ROWS, a.size,
            "the field is not one intensity per cell",
        )
        assertTrue(
            a.all { it in 0 until Static.LEVELS },
            "an intensity fell outside the ladder, so some cell draws an undefined ink",
        )
        // **The transparent level really occurs**, which is what makes the data faintly readable
        // underneath rather than gone. A ladder whose floor never came up would be an opaque veil,
        // and an opaque veil is a blanked view — the one thing D-186 says Static must not resemble.
        assertTrue(a.any { it == 0 }, "no cell is transparent: the real data is buried, not jammed")
    }

    /**
     * **A veil handed the same frame twice renders the same pixels, and two frames do not.**
     *
     * The render's own half of the determinism claim. The field being pure is worth nothing if the
     * composable mixes anything else into it — a clock read, a per-device size, a random ink — so
     * this compares what actually reached the screen rather than what the function returned.
     *
     * The second half is what makes it more than a tautology: a veil that drew the same pixels for
     * every frame would satisfy the first assertion and would be a still image, which is not noise.
     */
    @Test
    fun theSameFrameRendersTheSamePixelsAndTwoFramesDoNot() {
        fun shot(frame: Int): BufferedImage {
            var image: BufferedImage? = null
            runDesktopComposeUiTest(width = 120, height = 200) {
                setContent { StaticNoise(jam, Modifier.fillMaxSize(), frame = frame) }
                onAllNodesWithTag(STATIC_VEIL).assertCountEquals(1)
                image = onRoot().captureToImage().toAwtImage()
            }
            return image ?: error("the veil rendered nothing")
        }

        assertEquals(0, differing(shot(7), shot(7)), "the same frame drew two different veils")
        assertTrue(differing(shot(7), shot(8)) > 0, "two frames drew the same veil")
    }

    /** How many pixels differ between two shots of the same size. */
    private fun differing(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) count++
            }
        }
        return count
    }
}
