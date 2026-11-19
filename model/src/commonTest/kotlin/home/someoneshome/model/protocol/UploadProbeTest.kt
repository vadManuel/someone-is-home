package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Hmac
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **The house's clock rides every upload at one width, and comes back at one width** (D-295).
 *
 * The probe up and the two readings down are fixed-width hex, so neither frame's length moves with
 * the house's uptime, the probe drawn, or anything the phone did: an observer of the Wi-Fi sees the
 * same upload and the same answer from every seat, out seats included (D-217).
 */
class UploadProbeTest {

    private val seed = ContactSeed.of(Hmac.sha256(byteArrayOf(5), "test".encodeToByteArray()))
    private val window = ContactWindow(117_000L)

    private val readings = listOf(0L, 1L, 999L, 86_400_000L, 1L shl 40, Long.MAX_VALUE, -1L, -3_000L, Long.MIN_VALUE)

    @Test
    fun `a reading of the house's clock is sixteen hex characters and reads back as itself`() {
        for (millis in readings) {
            val hex = UploadProbe.hexOf(millis)
            assertTrue(UploadProbe.isProbe(hex), "$millis wrote $hex")
            assertEquals(millis, UploadProbe.millisOf(hex), "$millis did not read back from $hex")
        }
        assertEquals(UploadProbe.NONE, UploadProbe.hexOf(0L))
    }

    @Test
    fun `the upload is one length whatever probe it carries and the probe survives the wire`() {
        val probes = listOf(UploadProbe.NONE, "ffffffffffffffff", "0123456789abcdef", "9f00000000000001")
        val frames = probes.map { probe ->
            val body = SightingUpload.pack(seed, window, 41, emptyList(), probe = probe)
            val text = TransportWire.encode(TransportFrame.Carry(RoundWire.encode(body)))
            val back = RoundWire.decodeOrNull(RoundWire.encode(body)) as RoundBody.Sighted
            assertEquals(probe, back.probe, "the probe did not survive the wire")
            assertEquals(body.slots, back.slots)
            text.encodeToByteArray().size
        }
        assertEquals(1, frames.toSet().size, "the upload's length follows its probe: $frames")
    }

    @Test
    fun `a probe that is not sixteen lowercase hex characters is not packed`() {
        for (bad in listOf("", "0123", "0123456789ABCDEF", "0123456789abcdefg", "zzzzzzzzzzzzzzzz")) {
            assertFailsWith<IllegalArgumentException>("packed the probe '$bad'") {
                SightingUpload.pack(seed, window, 41, emptyList(), probe = bad)
            }
        }
    }

    @Test
    fun `the answer is one length at every moment of the house's clock and carries both readings back`() {
        val lengths = readings.flatMap { heard ->
            listOf(UploadProbe.NONE, "fedcba9876543210").map { probe ->
                val answer = RoundBody.UploadHeard.of("0123456789abcdef", probe, heard, heard + 17)
                val back = RoundWire.decodeOrNull(RoundWire.encode(answer)) as RoundBody.UploadHeard
                assertEquals(heard, back.heardAtMillis)
                assertEquals(heard + 17, back.answeredAtMillis)
                assertEquals(probe, back.probe)
                TransportWire.encode(TransportFrame.Carry(RoundWire.encode(answer))).encodeToByteArray().size
            }
        }
        assertEquals(1, lengths.toSet().size, "the answer's length follows the house's clock or the probe: ${lengths.toSet()}")
    }

    @Test
    fun `an answer whose probe or readings are not sixteen hex characters does not construct or decode`() {
        val id = "0123456789abcdef"
        assertFailsWith<IllegalArgumentException> { RoundBody.UploadHeard(id, "123", UploadProbe.NONE, UploadProbe.NONE) }
        assertFailsWith<IllegalArgumentException> { RoundBody.UploadHeard(id, UploadProbe.NONE, "1000", UploadProbe.NONE) }
        assertFailsWith<IllegalArgumentException> { RoundBody.UploadHeard(id, UploadProbe.NONE, UploadProbe.NONE, "-1") }
        val decimal = RoundWire.encode(RoundBody.UploadHeard.of(id, UploadProbe.NONE, 5L, 6L))
            .replace(UploadProbe.hexOf(5L), "5")
        assertEquals(null, RoundWire.decodeOrNull(decimal), "an answer with a decimal reading decoded")
    }
}
