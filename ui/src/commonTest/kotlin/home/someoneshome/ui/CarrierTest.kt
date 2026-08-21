package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The status bar's carrier, which is the one place the device says what happened to you.
 *
 * Worth a test rather than a glance because the rule is **cause-driven, not screen-driven**, and
 * the obvious implementation is the wrong one. Both routes out — revoked in the dark, restrained
 * by the room — converge on the same later screens, so deriving the word from the current screen
 * silently labels half the players with the other one's fate. `Revoke` and `Restrain` are exactly
 * the pair the project vocabulary says must never be collapsed.
 */
class CarrierTest {

    private fun carrier(screen: ScreenId, outBy: OutBy? = null) =
        PanelVals(PanelState(screen = screen, outBy = outBy)).carrier

    /** The screens reached by BOTH routes. This is where screen-derived logic goes wrong. */
    private val shared = listOf(ScreenId.Ghost3, ScreenId.GhostMeeting)

    @Test
    fun theSharedOutScreensFollowTheCauseNotTheScreen() {
        for (screen in shared) {
            assertEquals("REVOKED", carrier(screen, OutBy.Revoked), "$screen after a revoke")
            assertEquals("RESTRAINED", carrier(screen, OutBy.Restrained), "$screen after a restrain")
        }
    }

    @Test
    fun theRevokedAndWalkInScreensAlsoFollowTheCause() {
        assertEquals("REVOKED", carrier(ScreenId.Revoked, OutBy.Revoked))
        assertEquals("RESTRAINED", carrier(ScreenId.Restrained, OutBy.Restrained))
        assertEquals("REVOKED", carrier(ScreenId.Ghost2, OutBy.Revoked))
        assertEquals("RESTRAINED", carrier(ScreenId.Ghost2, OutBy.Restrained))
    }

    /** Nothing in play names either word, whatever is carried alongside. */
    @Test
    fun aPlayerStillInPlayIsNeverLabelled() {
        for (screen in listOf(ScreenId.Home, ScreenId.Page2, ScreenId.Work, ScreenId.Discussion)) {
            assertEquals("", carrier(screen), "$screen while in play")
            assertEquals("", carrier(screen, OutBy.Revoked), "$screen, stale outBy")
        }
    }

    /**
     * **The slot is empty on every screen that has nothing to report** — including the two that
     * used to be special-cased into carrying the game's name.
     *
     * Worth its own test because the emptiness is the feature. The bar carried SOMEONE'S HOME on
     * every in-play screen and on the Residents' win, which trained the eye to skip the one slot
     * that later has to deliver REVOKED or RESTRAINED. A screen quietly reacquiring a title would
     * put that back without anybody noticing it had.
     */
    @Test
    fun theCarrierIsBlankWhereverThereIsNothingToSay() {
        for (screen in listOf(
            ScreenId.Home, ScreenId.Lobby, ScreenId.Editor, ScreenId.Armed,
            ScreenId.Disconnect, ScreenId.WinResidents, ScreenId.WinInsiders,
        )) {
            assertEquals("", carrier(screen), "$screen has nothing to report")
        }
    }

    /**
     * With no cause recorded the bar must claim **neither**.
     *
     * Guessing would be wrong for about half the players, and wrong in precisely the way the
     * vocabulary forbids — calling a physical act by the room "system power lent by the house",
     * or the reverse.
     */
    @Test
    fun withoutACauseItNamesNeither() {
        for (screen in shared + listOf(ScreenId.Revoked, ScreenId.Restrained, ScreenId.Ghost2)) {
            val c = carrier(screen, outBy = null)
            assertEquals("UNREGISTERED", c, "$screen with no recorded cause")
        }
    }

}
