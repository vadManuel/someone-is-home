package home.someoneshome.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **D-106: the light signature shows everywhere — so "everywhere" has to be a checked property.**
 *
 * How much light a Subroutine makes a phone emit is knowledge a player holds *in advance*, which
 * is what lets a Resident plan a dark route. The design already lost it once: the sentence form
 * was removed from the springboard widget for how it read, and the fact went with it. A treatment
 * can be redrawn; a surface that quietly stops carrying the value cannot be noticed, because the
 * absent mark looks exactly like a Subroutine nobody has rated.
 *
 * So these read the marks off the rendered screens rather than off the model. Every surface that
 * names a Subroutine is named here with the count it must show, and every other screen in the
 * game is asserted to show none — a sweep, so a screen that starts naming a Subroutine without
 * its light fails here rather than shipping.
 *
 * **Both roles, every screen.** The light signature is a property of the work, not of who is
 * doing it: a signature that differed by role would be a brightness channel carrying alignment,
 * which is the one thing the whole panel is built to refuse. Counting per role makes that
 * structural rather than a thing nobody thought to try.
 */
@OptIn(ExperimentalTestApi::class)
class LightSignatureTest {

    /**
     * What each screen must say about light, in marks.
     *
     * The five work-order rows are the design's own roster values — Replay bright, Short dark,
     * Jam medium, Sniff dark (the one short dark) — plus ARRAY WIPE, whose signature this port
     * chose and which is flagged for review. The two LOCKED rows are the reason the totals are
     * five and not seven: a blocked step names nothing, and its light is part of nothing.
     */
    private val expected: Map<ScreenId, Map<LightSignature, Int>> = mapOf(
        ScreenId.Work to mapOf(
            LightSignature.Bright to 2,
            LightSignature.Medium to 1,
            LightSignature.Dark to 2,
        ),
        // The springboard's NEXT SUBROUTINE widget, on all five screens that draw page 1: the
        // page itself, the three that put a notification over it, and the one where the Egress
        // countdown occupies the meter's slot. The house taking the only number away must not
        // take the light with it — and this list has grown twice by being caught here rather than
        // by being reasoned about, most recently when a quiet banner became its own screen.
        ScreenId.Home to mapOf(LightSignature.Dark to 1),
        ScreenId.Notify to mapOf(LightSignature.Dark to 1),
        ScreenId.Banner to mapOf(LightSignature.Dark to 1),
        ScreenId.Quiet to mapOf(LightSignature.Dark to 1),
        ScreenId.EgressWidget to mapOf(LightSignature.Dark to 1),
        // The last moment before BEGIN, where NOT THIS ONE is still live.
        ScreenId.ScanCaught to mapOf(LightSignature.Dark to 1),
        // One row per built Subroutine, each showing its own rung in the same fixed slot: the
        // header's mark is what makes the ladder a thing a player recognises rather than reads.
        // HANDSHAKE is one faint cell and the two bright ones are three.
        ScreenId.SubHandshake to mapOf(LightSignature.Dark to 1),
        ScreenId.SubReplay to mapOf(LightSignature.Bright to 1),
        ScreenId.SubParity to mapOf(LightSignature.Bright to 1),
        ScreenId.SubShort to mapOf(LightSignature.Dark to 1),
        ScreenId.SubTrace to mapOf(LightSignature.Medium to 1),
        ScreenId.SubJam to mapOf(LightSignature.Medium to 1),
        ScreenId.SubDeallocate to mapOf(LightSignature.Bright to 1),
        // **SNIFF carries its mark in the same slot as the other seven, and on this render it is
        // drawn in black** — the screen emits nothing until the player has answered (D-137), and
        // that includes its own header. The mark is present, in its place, at the size it is
        // everywhere else; what changes is one colour. A screen that dropped the mark while dark
        // would be a surface that stops carrying the value, which is the exact regression the
        // sentence form's removal caused once already and the reason this sweep exists.
        ScreenId.SubSniff to mapOf(LightSignature.Dark to 1),
    )

    private fun SemanticsNodeInteractionsProvider.marks(): Map<LightSignature, Int> =
        LightSignature.entries
            .associateWith { onAllNodesWithTag(markTag(it), useUnmergedTree = true).fetchSemanticsNodes().size }
            .filterValues { it > 0 }

    @Test
    fun everySurfaceThatNamesASubroutineCarriesItsLightAndNoOtherScreenDoes() {
        val wrong = mutableListOf<String>()

        for (id in ScreenId.entries) {
            val outBy = when (id) {
                ScreenId.Restrained -> OutBy.Restrained
                ScreenId.Revoked, ScreenId.Ghost2, ScreenId.Ghost3, ScreenId.GhostMeeting ->
                    OutBy.Revoked
                else -> null
            }
            for (role in PanelRole.entries) {
                runDesktopComposeUiTest(width = 600, height = 1300) {
                    setContent {
                        DeviceCanvas(insets = PanelInsets()) {
                            Screen(PanelState(screen = id, role = role, outBy = outBy))
                        }
                    }
                    val found = marks()
                    val want = expected[id] ?: emptyMap()
                    if (found != want) {
                        wrong += "$id/$role — shows $found, D-106 says $want"
                    }
                }
            }
        }

        wrong.forEach { println("LIGHT  $it") }
        assertTrue(
            wrong.isEmpty(),
            "${wrong.size} surface(s) disagree with D-106 about the light signature:" +
                wrong.joinToString("") { "\n  $it" },
        )
    }

    /**
     * **The key teaches the whole ladder, and it is the only place a mark is spelled out.**
     *
     * One sample per rung, so adding a rung without adding it to the key fails here — and the key
     * lives on the work order alone, because a legend repeated on every screen that carries the
     * mark is a legend nobody stops needing.
     */
    @Test
    fun theKeyOnTheWorkOrderSpellsOutEveryRungAndAppearsNowhereElse() {
        for (id in listOf(ScreenId.Work, ScreenId.Home, ScreenId.SubHandshake, ScreenId.SubParity)) {
            runDesktopComposeUiTest(width = 600, height = 1300) {
                setContent {
                    DeviceCanvas(insets = PanelInsets()) { Screen(PanelState(screen = id)) }
                }
                val samples = LightSignature.entries.associateWith {
                    onAllNodesWithTag(sampleTag(it), useUnmergedTree = true).fetchSemanticsNodes().size
                }
                val want = if (id == ScreenId.Work) 1 else 0
                assertEquals(
                    LightSignature.entries.associateWith { want },
                    samples,
                    "$id draws the wrong key — the work order carries one sample per rung and " +
                        "nothing else carries any",
                )
            }
        }
    }

    /**
     * The ladder itself: three rungs, one cell each, and the darkest is one cell rather than none.
     *
     * Zero would make an *absent* mark a value, and then a locked row — which names nothing on
     * purpose — would be claiming its Subroutine is dark. It would also be untrue: this device
     * has no off, and a phone emitting nothing is a phone whose player has been revoked.
     */
    @Test
    fun theDarkestRungStillLightsACell() {
        assertEquals(listOf(1, 2, 3), LightSignature.entries.map { it.rung })
        assertEquals(
            LightSignature.entries.size,
            LightSignature.entries.maxOf { it.rung },
            "the mark draws one cell per rung, so the top rung must light all of them",
        )
    }
}
