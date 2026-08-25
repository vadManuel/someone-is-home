package home.someoneshome.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import home.someoneshome.model.Haptic
import home.someoneshome.platform.CardScanner
import home.someoneshome.platform.Haptics
import home.someoneshome.platform.ScanUse
import home.someoneshome.platform.deviceCardScanner

/**
 * **The host-setup scan, as one object: the back lens, the picture, the torch and the buzz.**
 *
 * ### It is production and lives outside the cheat sources on purpose
 *
 * The registration camera was reached through `ScanCheat` — a bench — which meant the one scanner
 * a release build would ever need was in the source set release does not compile, and that the
 * screen's real camera was whichever one the bench happened to hold. It held the in-round one:
 * **front lens, no picture**, under a label reading BACK CAMERA . POINT AT THE CODE. This is that
 * seam pulled down to where the game is, so the two scans are two objects with two configurations
 * rather than one bench lending its lens to both.
 *
 * ### One scanner, and it is [ScanUse.Registration] by construction
 *
 * There is no parameter and there must not be one. A caller that could choose would be a caller
 * that could choose wrong, and the whole of E-PAY-5's camera half is a caller having chosen wrong
 * once already. The in-round scan builds its own — see `ScanCheat`, and E5, which owns it.
 *
 * ### The buzz is a host's confirmation and it does not fire at the frame rate
 *
 * A camera cannot tell *a new card* from *the same card, still in frame* — [CardScanner] says so
 * at the seam and refuses to guess. Here the answer is that a payload identical to the one before
 * it is the card that has not moved, and a host holding one steady must not feel the phone thirty
 * times a second. A *different* card always buzzes, and coming back to a card later — with another
 * one in between — buzzes again, because that is the host correcting themselves and it is a real
 * event. **Nothing here decides what a scan means**: `HomeEditorModel` does, one layer up.
 */
class MarkerRegistration(
    private val haptics: Haptics,
    private val note: (String) -> Unit = { println("[registration] $it") },
) {

    /** The back camera, with a preview and a torch, and it can be nothing else. */
    val scanner: CardScanner = deviceCardScanner(ScanUse.Registration)

    /**
     * The live picture, for the screen's slot.
     *
     * Built once and handed to `LocalViewfinder`. On a target with no lens it is a composable that
     * draws nothing, which is the honest hole the slot's own KDoc argues for.
     */
    val viewfinder: @Composable (Modifier) -> Unit = viewfinderFor(scanner)

    private var last: String? = null

    /**
     * **Open the lens for as long as the registration screen is up**, and pass every symbol on.
     *
     * The payload goes to [onPayload] **verbatim** — this class reads it for the buzz and changes
     * nothing, because deciding what a card is belongs to `CardPayload` and a scanner that tidied a
     * payload would be a second opinion about a piece of paper nobody can edit.
     */
    fun watch(onPayload: (String) -> Unit) {
        last = null
        note("WATCHING — ${scanner.use}")
        scanner.start { payload ->
            val repeat = payload == last
            last = payload
            note("read '$payload' (${payload.length} chars)${if (repeat) " · same card, still in frame" else ""}")
            if (!repeat) haptics.buzz(Haptic.Short)
            onPayload(payload)
        }
    }

    /**
     * **Give the lens back**, and the LED with it.
     *
     * A capture session holds the camera, the ISP and the battery; the torch holds a light. Host
     * setup ends by walking to a lobby and then into a dark house, so a session left running here
     * is a lit phone in somebody's pocket at lights-out.
     */
    fun release() {
        torch(false)
        scanner.stop()
        last = null
        note("released")
    }

    /** The host's torch button, reaching the LED. Refused by the seam on any other scan. */
    fun torch(lit: Boolean) {
        scanner.torch(lit)
    }
}

/**
 * **A `CardScanner`'s live picture as something a Compose tree can hold**, or nothing.
 *
 * `expect` rather than a composable in `commonMain` because a preview layer is an AVFoundation
 * type inside a `UIView`, and putting a `UIView` in a Compose tree is iOS interop that only the
 * iOS source set may name. What comes back has the exact shape `LocalViewfinder` takes, so the app
 * hands it straight down and `ui` never learns what is inside it.
 */
expect fun viewfinderFor(scanner: CardScanner): @Composable (Modifier) -> Unit
