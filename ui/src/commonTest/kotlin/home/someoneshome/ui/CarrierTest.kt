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
        assertEquals("REVOKED", carrier(ScreenId.Ghost2, OutBy.Revoked))
        assertEquals("RESTRAINED", carrier(ScreenId.Ghost2, OutBy.Restrained))
    }

    /** Nothing in play names either word, whatever is carried alongside. */
    @Test
    fun aPlayerStillInPlayIsNeverLabelled() {
        for (screen in listOf(ScreenId.Home, ScreenId.Page2, ScreenId.Work, ScreenId.Discussion)) {
            assertEquals("SOMEONE'S HOME", carrier(screen), "$screen while in play")
            assertEquals("SOMEONE'S HOME", carrier(screen, OutBy.Revoked), "$screen, stale outBy")
        }
    }

    @Test
    fun beforeArmingTheCarrierIsBlank() {
        assertEquals("", carrier(ScreenId.Lobby))
        assertEquals("", carrier(ScreenId.Editor))
    }
}
