package home.someoneshome.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** **T4: awake from arming until the round ends or the player leaves, then put back** (D-221). */
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
}
