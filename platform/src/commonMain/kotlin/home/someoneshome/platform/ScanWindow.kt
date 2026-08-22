package home.someoneshome.platform

import home.someoneshome.model.Haptic

/**
 * **One scan, from the tap to the read or the giving up** (D-159).
 *
 * The gesture is *tap SCAN → turn the phone so the lamp faces the code → the Subroutine opens on
 * acquire, by itself.* Press-and-hold-then-release is superseded, and the reason is geometry
 * rather than taste: **the front camera is on the same face as the lamp**, so the light that lets
 * a phone read a printed card in a dark house is on the side pointed at the marker, and the player
 * cannot see their own screen while it works. A gesture that asked them to decide when the read
 * succeeded would be asking them to judge evidence facing a wall three inches from their hand.
 *
 * So the window decides, and it says which way it went through the one channel that reaches a
 * player whose eyes are elsewhere (D-160):
 *
 * - **acquire → one vibrate**, *yes*, and the window closes itself;
 * - **the window running out → two vibrates**, *no*, and the window closes itself.
 *
 * Nothing is spent by running out. Tap again.
 *
 * ### ONE read per window, and that is what makes the bench survivable
 *
 * A camera cannot tell *a new card* from *the same card, still in frame* — [CardScanner] says so
 * at the seam and refuses to guess — so a surface that buzzed on every delivery buzzes at the
 * frame rate for as long as somebody holds a card up. That is what the camera bench did.
 *
 * **The fix is not a dedupe on the payload, it is the window.** The first delivery closes it, so
 * every later delivery lands on a closed window and is dropped — the same card, a different card,
 * a foreign code, all of them. A payload-keyed dedupe would let a second card in the frame ring a
 * second buzz out of one tap, which is one scan producing two answers.
 *
 * ### Across windows a repeat is meaningful, and must stay that way
 *
 * Scanning the same marker again in a *new* window is not a duplicate — it is a player putting
 * themselves at a known place at a known time, which is the alibi the house's presence map is made
 * of (D-136). Every [open] starts a clean window, and this class remembers nothing across them.
 *
 * ### It holds no clock
 *
 * Ten seconds is the design's number and it is drawn on the screen — the countdown bar, its
 * segments and its figure all come off one value in `ui`. A second copy of that number in here
 * would be two clocks for one window, disagreeing eventually. **The surface that draws the
 * countdown is the surface that calls [expire].**
 */
class ScanWindow(
    private val scanner: CardScanner,
    private val haptics: Haptics,
) {

    private var listening: ((String) -> Unit)? = null
    private var caught: String? = null

    /** True between [open] and whichever of the three endings arrives first. */
    val isOpen: Boolean get() = listening != null

    /** What this window read, or null if it has not read anything (yet, or at all). */
    val read: String? get() = caught

    /**
     * **The player tapped SCAN.** The lens opens and the ten seconds start.
     *
     * Opening while a window is already open replaces it rather than nesting: the second tap is
     * the player asking again, and a window that survived it would be counting down to a refusal
     * for a scan nobody is still waiting on.
     */
    fun open(onAcquired: (String) -> Unit) {
        caught = null
        listening = onAcquired
        scanner.start(::saw)
    }

    /**
     * A symbol resolved. **Delivered verbatim** — deciding what it means belongs to `CardPayload`,
     * and a window that tidied a payload would be certifying a scanner that does not exist.
     *
     * The window closes *before* the handler runs, so a delivery that arrives while the handler is
     * still on the stack — the same card, still in frame — finds nothing to land on.
     */
    private fun saw(payload: String) {
        val onAcquired = listening ?: return
        listening = null
        caught = payload
        scanner.stop()
        haptics.buzz(Haptic.Short)
        onAcquired(payload)
    }

    /**
     * **The window ran out.** Two vibrates, the lens goes back, and the phone goes back with it.
     *
     * Idempotent, and silent on a window that already caught something: a countdown that reaches
     * zero a frame after the read landed must not turn a *yes* into a *no*.
     */
    fun expire() {
        if (listening == null) return
        listening = null
        scanner.stop()
        haptics.buzz(Haptic.Double)
    }

    /**
     * **The window was closed without an answer either way** — the screen went somewhere else, the
     * app went to the background, the phone was locked.
     *
     * Silent, and that is the difference from [expire]: the double vibrate is the house answering
     * a scan, and there is no scan left to answer.
     */
    fun close() {
        if (listening == null) return
        listening = null
        scanner.stop()
    }
}
