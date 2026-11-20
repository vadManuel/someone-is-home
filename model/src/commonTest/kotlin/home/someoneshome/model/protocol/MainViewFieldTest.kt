package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **D-307: the main view field is one character in every upload, and it fails closed.**
 *
 * Measured on the encoded frame through the real `RoundWire` and `TransportWire`: an upload that says
 * the main view and one that says anything else are the same number of bytes, so a reader of the
 * Wi-Fi measuring frames learns nothing from the field's presence, and the cadence is the upload's.
 */
class MainViewFieldTest {

    private fun frame(view: String): Int {
        val seed = ContactSeed.of(ByteArray(32) { it.toByte() })
        val body = SightingUpload.pack(seed, ContactWindow(7), 3, emptyList(), view = view)
        return TransportWire.encode(TransportFrame.Carry(RoundWire.encode(body))).encodeToByteArray().size
    }

    @Test
    fun `an upload on the main view and one off it are one length`() {
        assertEquals(frame(MainViewField.OFF), frame(MainViewField.ON), "the main view field moved the upload's length")
        assertEquals(MainViewField.CHARS, MainViewField.ON.length)
        assertEquals(MainViewField.CHARS, MainViewField.OFF.length)
    }

    @Test
    fun `the field survives the wire and only exactly ON reads as the main view`() {
        val seed = ContactSeed.of(ByteArray(32) { it.toByte() })
        for (on in listOf(true, false)) {
            val sent = SightingUpload.pack(seed, ContactWindow(7), 3, emptyList(), view = MainViewField.of(on))
            val back = RoundWire.decodeOrNull(RoundWire.encode(sent)) as RoundBody.Sighted
            assertEquals(on, MainViewField.reads(back.view), "the main view did not survive the wire")
        }
        for (bad in listOf("", "2", "11", "1 ", "true", "O")) assertFalse(MainViewField.reads(bad), "'$bad' read as the main view")
        assertTrue(MainViewField.reads(MainViewField.ON))
    }

    @Test
    fun `a caller that says nothing sends off the main view and a packer refuses a bad field`() {
        val seed = ContactSeed.of(ByteArray(32) { it.toByte() })
        assertEquals(MainViewField.OFF, SightingUpload.pack(seed, ContactWindow(7), 3, emptyList()).view)
        assertFailsWith<IllegalArgumentException> { SightingUpload.pack(seed, ContactWindow(7), 3, emptyList(), view = "2") }
    }
}
