package home.someoneshome.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **T4: awake from arming until the round ends or the player leaves, then put back** (D-221) —
 * **and H1: the host awake from its lobby opening until hosting ends** (D-224).
 */
class ScreenWakeTest {

    private class Timer(var value: Boolean = false) : IdleTimer {
        val writes = mutableListOf<Boolean>()
        override var disabled: Boolean
            get() = value
            set(v) {
                writes += v
                value = v
            }
    }

    @Test
    fun heldTheIdleTimerIsOffAndReleasedItIsBackOn() {
        val timer = Timer()
        val wake = ScreenWake(timer)
        wake.hold()
        assertTrue(timer.value, "held, the phone can still lock itself")
        assertTrue(wake.holding)
        wake.release()
        assertFalse(timer.value, "released, the phone stays awake after the round")
        assertFalse(wake.holding)
    }

    @Test
    fun aSecondHoldDoesNotForgetWhatTheTimerWasBefore() {
        val timer = Timer(value = false)
        val wake = ScreenWake(timer)
        wake.hold()
        wake.hold()
        wake.release()
        assertFalse(timer.value, "a resume's second hold remembered the game's own setting as the owner's")
    }

    @Test
    fun aTimerTheOwnerHadAlreadyDisabledIsLeftDisabled() {
        val timer = Timer(value = true)
        val wake = ScreenWake(timer)
        wake.hold()
        wake.release()
        assertTrue(timer.value, "the release switched off a setting the round never owned")
    }

    @Test
    fun aReleaseWithNothingHeldTouchesNothing() {
        val timer = Timer(value = true)
        ScreenWake(timer).release()
        assertEquals(emptyList(), timer.writes, "a release with nothing held wrote the idle timer")
    }

    // ---- H1: the host's lobby (D-224) -------------------------------------------------------------

    @Test
    fun theHostsLobbyHoldsTheScreenAndHostingsEndPutsItBack() {
        val timer = Timer()
        val wake = ScreenWake(timer)
        wake.holdWhileHosting()
        assertTrue(timer.value, "the host's lobby is open and the phone can still lock itself")
        assertTrue(wake.holdingForHosting)
        wake.releaseHosting()
        assertFalse(timer.value, "hosting ended and the screen is still held awake")
    }

    /** **Arming takes over the host's hold with no instant where the timer is back on.** */
    @Test
    fun armingFromALobbyNeverLetsTheTimerBackOn() {
        val timer = Timer()
        val wake = ScreenWake(timer)
        wake.holdWhileHosting()
        wake.hold()
        assertEquals(listOf(true), timer.writes, "the timer flickered back on between the lobby and the round")
        assertFalse(wake.holdingForHosting, "the round's hold did not take over")
    }

    /** **Hosting ends when the round does** (the owner's H1): the round's release ends both. */
    @Test
    fun theRoundsEndEndsHostingToo() {
        val timer = Timer()
        val wake = ScreenWake(timer)
        wake.holdWhileHosting()
        wake.hold()
        wake.release()
        assertFalse(timer.value, "the round ended and the host's screen is still held awake")
        assertFalse(wake.holding)
    }

    /**
     * **A late round release leaves a re-opened lobby alone.** NEW ROUND holds for the lobby again,
     * and the house's `RoundEnded` broadcast then reaches the host's own round half, which releases.
     */
    @Test
    fun aLateRoundReleaseDoesNotEndTheLobbyNewRoundOpened() {
        val timer = Timer()
        val wake = ScreenWake(timer)
        wake.hold()
        wake.release()
        wake.holdWhileHosting()
        wake.release()
        assertTrue(timer.value, "the round half's late release let the host's re-opened lobby lock")
        assertTrue(wake.holdingForHosting)
    }

    /** **A host leaving mid-round has its round half release first; hosting's release is not the round's.** */
    @Test
    fun aHostingReleaseLeavesARoundsHoldAlone() {
        val timer = Timer()
        val wake = ScreenWake(timer)
        wake.hold()
        wake.releaseHosting()
        assertTrue(timer.value, "ending hosting released a round's hold")
    }

    @Test
    fun aHostWhoseOwnerHadDisabledTheTimerIsLeftThatWay() {
        val timer = Timer(value = true)
        val wake = ScreenWake(timer)
        wake.holdWhileHosting()
        wake.hold()
        wake.release()
        assertTrue(timer.value, "hosting and a round switched off a setting neither owned")
    }
}
