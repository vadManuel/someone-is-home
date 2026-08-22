package home.someoneshome.platform

import home.someoneshome.model.CardPayload
import home.someoneshome.model.CardRejection
import home.someoneshome.model.MarkerShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The camera's own code path, with the camera taken out of it.**
 *
 * Under `./gradlew check` the target is the Simulator, which has no lens — so nothing here starts a
 * session, and nothing here pretends to. What it does certify is the half of the class that is not
 * hardware: **what the scanner does with a symbol once one has been resolved**, which is where the
 * rule that matters lives. `deliver` is `internal` for exactly this, the way
 * [SeededCardScanner.present] is public for it.
 *
 * `start()` **is** called here, and on this target it takes the refusal branch: a Kotlin/Native
 * test binary is not an application bundle carrying `NSCameraUsageDescription`, so TCC answers
 * `denied` without prompting anybody. That is a useful accident — it means the branch a phone takes
 * when a host has said no in Settings is the branch every run of `./gradlew check` exercises. The
 * *granted* branch, and everything behind it, exists only where there is a lens: it is certified by
 * the device log and by a hand holding a card, and by nothing here.
 */
class CameraCardScannerTest {

    /** A scanner with a listener already on it, and the list that listener writes to. */
    private fun collecting(): Pair<CameraCardScanner, List<String>> {
        val seen = mutableListOf<String>()
        val scanner = CameraCardScanner(log = {})
        scanner.start { seen += it }
        return scanner to seen
    }

    /**
     * **Verbatim. Byte for byte, whatever came off the symbol.**
     *
     * The camera must not decide what a card is (U5's doctrine, D-069): trimming, upper-casing or
     * length-checking here would make the scanner a second opinion about the payload format, and
     * the thing the two opinions would eventually disagree about is a piece of paper nobody can
     * edit. So a payload with a space in it, one in the wrong case, and one far too long all arrive
     * unchanged — and are refused a layer up, by the decoder, which is the only thing that may.
     */
    @Test
    fun everySymbolArrivesExactlyAsItLeftTheCamera() {
        val (scanner, seen) = collecting()

        // Every one of these is a card the decoder will refuse. That is the point: the refusal is
        // CardPayload's to make, and it cannot make it if this class tidied the evidence first.
        val raw = listOf(
            "1A SEED01",            // a space, the one character the alphabet excludes
            "1aseed001",            // the wrong case
            "1ASEED001 ",           // a trailing space
            "https://example.test", // a foreign code: somebody else's QR, in frame
            "",                     // a symbol with no string value at all
            "1ASEED001",            // and one real card, so the list is not all refusals
        )
        raw.forEach(scanner::deliver)

        assertEquals(raw, seen, "the scanner changed a payload on its way past")
        for ((i, payload) in raw.withIndex()) {
            assertEquals(
                payload.length, seen[i].length,
                "payload $i arrived at a different length than it left",
            )
        }
    }

    /**
     * The refusals are reachable **because** nothing was tidied — the same list, decoded.
     *
     * Stated as its own test rather than as an assertion inside the one above, because it is a
     * different claim: not *the bytes survived* but *the bytes surviving is what lets the scan
     * surface say something true about the piece of paper in the host's hand* (D-071).
     */
    @Test
    fun aTidiedPayloadWouldHaveHiddenTheReasonItWasRefused() {
        val (scanner, seen) = collecting()
        scanner.deliver("1A SEED01")
        scanner.deliver("https://example.test")
        scanner.deliver("")

        assertEquals(
            CardRejection.NotInAlphabet,
            assertIs<CardPayload.Result.Rejected>(CardPayload.decode(seen[0])).why,
            "a space is not in the alphabet; trimming it would have turned a misread into a card",
        )
        assertEquals(
            CardRejection.WrongLength,
            assertIs<CardPayload.Result.Rejected>(CardPayload.decode(seen[1])).why,
            "somebody else's QR is not nine characters, and the screen may say so",
        )
        assertEquals(
            CardRejection.WrongLength,
            assertIs<CardPayload.Result.Rejected>(CardPayload.decode(seen[2])).why,
            "a symbol with no string value is a fact about a piece of paper, not a silence",
        )
    }

    /** A real card still reads, or the two tests above would be proving nothing useful. */
    @Test
    fun aPrintedPayloadStillDecodesToTheCardItWasPrintedFrom() {
        val (scanner, seen) = collecting()
        val card = SeededCardScanner.DECK.first()
        scanner.deliver(CardPayload.encode(card))
        assertEquals(card, assertIs<CardPayload.Result.Read>(CardPayload.decode(seen.single())).card)
        assertTrue(card.shape.id in MarkerShapes.all.map { it.id })
    }

    /** A screen opened, left and opened again must not register every card twice. */
    @Test
    fun startingTwiceReplacesTheListenerRatherThanAddingOne() {
        val scanner = CameraCardScanner(log = {})
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        scanner.start { first += it }
        scanner.start { second += it }
        scanner.deliver("1ASEED001")
        assertEquals(emptyList(), first)
        assertEquals(listOf("1ASEED001"), second)
    }

    /**
     * **A card resolved after the screen went away registers nothing.**
     *
     * The camera does not stop the instant `stop` is called — a frame already in flight arrives
     * afterwards. Delivering it would register a marker to the room the host has just walked out
     * of, and the host would have no way to know: the screen they are looking at is a different
     * one.
     */
    @Test
    fun nothingIsDeliveredAfterStopAndStoppingTwiceIsNotAnError() {
        val seen = mutableListOf<String>()
        val log = mutableListOf<String>()
        val scanner = CameraCardScanner(log = log::add)
        scanner.start { seen += it }
        scanner.stop()
        scanner.stop()
        scanner.deliver("1ASEED001")
        assertEquals(emptyList(), seen)
        assertTrue(
            log.any { it.contains("nobody was told") },
            "a card resolved after stop vanished without a word to the log: $log",
        )
    }

    /**
     * **A screen that left while the prompt was up does not come back to a running camera.**
     *
     * iOS asks for the camera once ever and the answer arrives whenever the person answers it —
     * after reading a sentence, thinking about it, possibly walking into another room. `stop` lands
     * first more often than not, and the permission callback that fires afterwards would open a
     * capture session behind a screen nobody is looking at. It was doing exactly that on the first
     * device run: the sweep asked, released twelve seconds later, and the grant it was still
     * waiting for would have started a session with no listener on it.
     *
     * The session itself cannot be reached from here — there is no lens. What is asserted is the
     * flag the permission callback consults, which is the whole of the decision.
     */
    @Test
    fun aScreenThatLeftWhileThePromptWasUpDoesNotStartACamera() {
        val scanner = CameraCardScanner(log = {})
        assertTrue(!scanner.wantsToRun, "a scanner nobody started wants the camera")
        scanner.start { }
        assertTrue(scanner.wantsToRun, "a started scanner does not want the camera")
        scanner.stop()
        assertTrue(
            !scanner.wantsToRun,
            "a stopped scanner still wants the camera, so a grant arriving now opens a session " +
                "behind a screen the host has walked away from",
        )
    }

    /**
     * **Silent, and never a throw.** Building one on a target with no camera is an ordinary Tuesday.
     *
     * A scanner that threw would take the screen down with it, and rule 5 is unambiguous about what
     * a screen that blanks in a dark house looks like to everyone watching it: a revocation.
     */
    @Test
    fun buildingOneOnATargetWithNoLensIsNotAnError() {
        val log = mutableListOf<String>()
        val seen = mutableListOf<String>()
        val scanner = CameraCardScanner(log = log::add)
        scanner.start { seen += it }
        scanner.stop()
        assertTrue(deviceCardScanner() is CameraCardScanner, "the iOS actual stopped being the camera")
        assertEquals(emptyList(), seen, "a target with no lens delivered a card")
        assertTrue(
            log.isNotEmpty() && log.all { it.startsWith("[scan]") },
            "a target that will never read a card said nothing to the log, so a phone whose host " +
                "refused the camera looks exactly like one that is working: $log",
        )
    }
}
