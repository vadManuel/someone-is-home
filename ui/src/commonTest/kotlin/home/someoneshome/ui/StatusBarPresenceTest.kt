package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Every screen carries a status bar.** Not most screens — every one.
 *
 * It is how a player confirms the perimeter is still armed and what the time is, and it stays put
 * on the two screens where they have just been removed from the round: a device that stopped
 * saying so would be the app abandoning them at the moment it took everything else away.
 *
 * Worth pinning because the natural way to build a full-screen takeover is to let it cover the
 * whole panel, and the bar is the first casualty. The design's own fixture does exactly that on
 * `revoked` and `restrained` — it pins them to all four edges and paints over a bar it has
 * already computed, which is why the carrier work for those two screens would never have been
 * seen. This asserts the port does not inherit that.
 */
class StatusBarPresenceTest {

    @Test
    fun noScreenIsWithoutAStatusRow() {
        val missing = ScreenId.entries.filter { id ->
            val vals = PanelVals(PanelState(screen = id))
            !vals.statusVisible && !vals.drawsOwnStatusRow
        }
        assertTrue(missing.isEmpty(), "screens with no status row at all: $missing")
    }

    /** The inverted row belongs to the amber-field screens, and only to them. */
    @Test
    fun onlyTheAmberFieldScreensDrawTheirOwn() {
        val own = ScreenId.entries.filter { PanelVals(PanelState(screen = it)).drawsOwnStatusRow }
        assertTrue(
            own.toSet() == setOf(ScreenId.Lock, ScreenId.Scan),
            "expected only Lock and Scan to draw their own row, got $own",
        )
    }

    /** Being out does not cost you the bar. */
    @Test
    fun theOutScreensKeepTheSharedBar() {
        for (id in listOf(
            ScreenId.Revoked, ScreenId.Restrained, ScreenId.Ghost2,
            ScreenId.GhostMeeting, ScreenId.Ghost3,
        )) {
            assertTrue(PanelVals(PanelState(screen = id)).statusVisible, "$id lost the bar")
        }
    }
}
