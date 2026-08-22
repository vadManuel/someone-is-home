package home.someoneshome.platform

import home.someoneshome.model.Haptic
import home.someoneshome.model.HouseBuzz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **D-159's window and D-160's two words, read off a recording instead of off a dark corridor.**
 *
 * The scan is the one gesture in this game the player cannot watch themselves make: the lamp and
 * the lens are on the same face, so while a scan is happening the screen is pointed at a wall.
 * Everything the window does is therefore a claim about **what a hand feels**, and a hand feels
 * scripts — so what these tests compare is what the motor was asked to play.
 */
class ScanWindowTest {

    /** A camera whose deliveries a test can decide. Nothing here is a real lens. */
    private class Lens : CardScanner {
        var listener: ((String) -> Unit)? = null
        var starts = 0
        var stops = 0

        override fun start(onPayload: (String) -> Unit) {
            starts++
            listener = onPayload
        }

        override fun stop() {
            stops++
            listener = null
        }

        /** A symbol came into frame. Dropped when nobody is reading, exactly as a real one is. */
        fun present(payload: String) = listener?.invoke(payload)

        /**
         * **The listener, held past the [stop] that removed it** — and the tests that matter use
         * this rather than [present].
         *
         * A real capture session is not a function call. `stopRunning` is asynchronous and a frame
         * already in the pipeline is delivered *after* it, so a window that relied on the camera
         * having gone quiet would be relying on the one thing the seam explicitly refuses to
         * promise. Delivering through a captured listener is the camera doing what a camera does,
         * and it is what makes the window's own guard the thing under test.
         */
        fun deliverPastStop(): (String) -> Unit {
            val held = listener ?: error("nothing was reading")
            return held
        }
    }

    private fun rig(): Triple<Lens, RecordingHaptics, ScanWindow> {
        val lens = Lens()
        val motor = RecordingHaptics()
        return Triple(lens, motor, ScanWindow(lens, motor))
    }

    private val yes = HouseBuzz.script(Haptic.Short)
    private val no = HouseBuzz.script(Haptic.Double)

    /**
     * **THE BUZZ STORM, as the injection that catches it.**
     *
     * A camera cannot tell *a new card* from *the same card, still in frame* — the seam says so
     * and refuses to guess — so it delivers the same payload for as long as somebody holds a card
     * up. The bench that buzzed on every delivery vibrated at the frame rate.
     *
     * Delete the `listening = null` in `ScanWindow.saw` and this test fails with a count of three.
     */
    @Test
    fun theSamePayloadTwiceInOneWindowBuzzesOnceAndOpensOnce() {
        val (lens, motor, window) = rig()
        val opened = mutableListOf<String>()
        window.open { opened += it }

        // The card is held up and the frames keep coming, including past the window's own stop.
        val frames = lens.deliverPastStop()
        frames("1ASEED001")
        frames("1ASEED001")
        frames("1ASEED001")

        assertEquals(listOf("1ASEED001"), opened, "one window delivered more than one read")
        assertEquals(1, motor.count, "the phone went off ${motor.count} times for one scan")
        assertEquals(listOf(yes), motor.played, "an acquire is D-160's *yes*: one vibrate")
        assertTrue(!window.isOpen, "the window is still open after it caught something")
    }

    /**
     * **One read per window, and that means the card as well as the frame.**
     *
     * A payload-keyed dedupe would let a *second* card in the same frame ring a second buzz out of
     * one tap — one scan producing two answers, in a game where a scan is how the house learns
     * where you are. The window closing is what makes that impossible.
     */
    @Test
    fun aDifferentCardInTheSameWindowIsAlsoDropped() {
        val (lens, motor, window) = rig()
        val opened = mutableListOf<String>()
        window.open { opened += it }

        val frames = lens.deliverPastStop()
        frames("1ASEED001")
        frames("1ASEED002")

        assertEquals(listOf("1ASEED001"), opened, "a second card was read out of one window")
        assertEquals(1, motor.count, "a second card in the same window buzzed")
        assertEquals("1ASEED001", window.read)
    }

    /**
     * **A second window re-arms**, and it has to: scanning the same marker again is not a
     * duplicate, it is a player putting themselves at a known place at a known time. That is the
     * alibi the house's presence map is made of (D-136).
     */
    @Test
    fun aSecondWindowReadsTheSameCardAgain() {
        val (lens, motor, window) = rig()
        val opened = mutableListOf<String>()

        window.open { opened += it }
        lens.present("1ASEED001")
        window.open { opened += it }
        lens.present("1ASEED001")

        assertEquals(listOf("1ASEED001", "1ASEED001"), opened, "the second window refused a repeat")
        assertEquals(listOf(yes, yes), motor.played, "the second window's read did not buzz")
        assertEquals(2, lens.starts, "the second window did not open the lens")
    }

    /**
     * **THE INJECTION FOR THE REFUSAL.** Expiry produces *exactly* the double and closes.
     *
     * Point `expire` at `Haptic.Short` and this fails naming both patterns; delete the `if
     * (listening == null) return` guard and `expiryAfterAReadDoesNotTurnAYesIntoANo` fails instead.
     */
    @Test
    fun expiryProducesExactlyTheDoubleAndCloses() {
        val (lens, motor, window) = rig()
        window.open { }

        window.expire()

        assertEquals(listOf(no), motor.played, "the window ran out and said something other than *no*")
        assertEquals(1, motor.count, "expiry buzzed ${motor.count} times")
        assertTrue(!window.isOpen, "an expired window is still open")
        assertEquals(1, lens.stops, "the lens was not handed back when the window ran out")
        assertNull(window.read, "an expired window claims to have read something")

        // Nothing is spent by a timeout. The lens is free and the next tap opens a real window.
        window.expire()
        assertEquals(1, motor.count, "expiring twice buzzed twice")
    }

    /**
     * **A countdown that reaches zero a frame after the read landed must not turn a *yes* into a
     * *no*.** The two answers are one vibration apart and the player is not looking at the screen.
     */
    @Test
    fun expiryAfterAReadDoesNotTurnAYesIntoANo() {
        val (lens, motor, window) = rig()
        window.open { }
        lens.present("1ASEED001")

        window.expire()

        assertEquals(listOf(yes), motor.played, "a caught scan was told *no* by its own countdown")
    }

    /**
     * **Closing is silent, and that is the whole difference from expiring.** The double vibrate is
     * the house answering a scan; a window closed because the app went to the background has no
     * scan left to answer.
     */
    @Test
    fun closingAWindowSaysNothingAndStillHandsTheLensBack() {
        val (lens, motor, window) = rig()
        window.open { }

        window.close()

        assertEquals(emptyList(), motor.played, "closing a window buzzed")
        assertTrue(!window.isOpen)
        assertEquals(1, lens.stops, "a closed window kept the lens")
    }

    /**
     * **A delivery to a window nobody opened reaches nothing.** The lens can outlive a window by a
     * frame or two, and a scan that answered after the player walked away would be a Subroutine
     * opening in a pocket.
     */
    @Test
    fun aPayloadWithNoWindowOpenIsNotARead() {
        val (lens, motor, window) = rig()
        val opened = mutableListOf<String>()
        window.open { opened += it }
        val frames = lens.deliverPastStop()
        window.close()

        frames("1ASEED001")

        assertEquals(emptyList(), opened)
        assertEquals(emptyList(), motor.played)
    }

    /**
     * **Tapping SCAN again while a window is open replaces it rather than nesting one inside it.**
     *
     * The second tap is the player asking again — a window that survived it would be counting down
     * to a refusal for a scan nobody is still waiting on, and the phone would say *no* to a scan
     * that had already been answered.
     */
    @Test
    fun openingTwiceReplacesTheWindowRatherThanStackingIt() {
        val (lens, motor, window) = rig()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()

        window.open { first += it }
        window.open { second += it }
        lens.present("1ASEED001")

        assertEquals(emptyList(), first, "the replaced window was still listening")
        assertEquals(listOf("1ASEED001"), second)
        assertEquals(1, motor.count, "two windows over one read buzzed twice")
    }

    /**
     * **The payload arrives verbatim.** What a card *is* belongs to `CardPayload`, and a window
     * that trimmed one would turn a misread into a card — `1A SEED01` trimmed is nine characters
     * that look like ours. The refusals live downstream and need the bytes to reach them.
     */
    @Test
    fun theWindowChangesNothingAboutWhatTheCameraSaw() {
        val (lens, _, window) = rig()
        var seen: String? = null
        window.open { seen = it }

        lens.present(" 1a seed01 ")

        assertEquals(" 1a seed01 ", seen, "the window tidied a payload on its way through")
    }
}
