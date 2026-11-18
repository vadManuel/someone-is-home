package home.someoneshome.platform.transport

import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **D-276: a frame's text read from its bytes is the text Ktor's `readText` reads.**
 *
 * Valid UTF-8 has one decoding, so the strict decode must agree with `readText` on every frame a
 * phone or the house writes, multi-byte characters included; bytes that are not valid UTF-8 are
 * handed to `readText` itself, so they get its answer whatever that is.
 */
class FrameTextTest {

    @Test
    fun `a frame's text is the text readText reads`() {
        val texts = listOf(
            "", "x", "{\"type\":\"carry\",\"body\":\"{\\\"type\\\":\\\"uploadHeard\\\"}\"}",
            "ÉLODIE · TOMÁS · 李 · 😀 · \u007f", "0".repeat(1_808),
        )
        for (text in texts) assertEquals(Frame.Text(text).readText(), Frame.Text(text).text(), text)
        val invalid = listOf(
            byteArrayOf(0xff.toByte()),
            byteArrayOf('a'.code.toByte(), 0xc3.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xe2.toByte(), 0x28, 0xa1.toByte()),
        )
        for (bytes in invalid) {
            val readText = runCatching { Frame.Text(true, bytes).readText() }
            val text = runCatching { Frame.Text(true, bytes).text() }
            assertEquals(readText.getOrNull(), text.getOrNull(), bytes.joinToString())
            assertEquals(readText.exceptionOrNull()?.let { it::class }, text.exceptionOrNull()?.let { it::class })
        }
    }
}
