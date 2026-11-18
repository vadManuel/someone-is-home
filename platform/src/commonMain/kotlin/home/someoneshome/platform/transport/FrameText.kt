package home.someoneshome.platform.transport

import io.ktor.websocket.Frame
import io.ktor.websocket.readText

/**
 * **A text frame's text, decoded straight from its bytes** (rule 7, D-276).
 *
 * Ktor's `readText` copies the payload into a fresh I/O buffer (a whole segment) and decodes it
 * through a growing builder: on the host that was a few kilobytes for every frame every phone sent,
 * one upload a second each. The bytes are UTF-8, and valid UTF-8 has exactly one decoding, so the
 * standard library's strict decode gives the same string from the same bytes with one allocation.
 *
 * **Anything that is not plainly that goes to `readText` as before**, and gets whatever answer it
 * always got: a fragment (which `readText` refuses) and bytes that are not valid UTF-8.
 */
internal fun Frame.Text.text(): String {
    if (!fin) return readText()
    return try {
        data.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        readText()
    }
}
