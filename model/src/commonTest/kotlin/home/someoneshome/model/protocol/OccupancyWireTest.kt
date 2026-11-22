package home.someoneshome.model.protocol

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ObservationView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * **D-316: the map's field on every upload's answer.** One width whatever it holds, sealed under the
 * receiving seat's seed, and failing closed: a field that does not open, or opens to something that
 * is not exactly one, is nothing drawn.
 */
class OccupancyWireTest {

    private val seed = ContactSeed.of(ByteArray(32) { (it * 7 + 3).toByte() })
    private val other = ContactSeed.of(ByteArray(32) { (it * 5 + 1).toByte() })
    private val nonce = ByteArray(RangingWire.NONCE_BYTES) { (it + 9).toByte() }

    private val reading = OccupancyBeat.terminal(
        here = 0,
        views = listOf(ObservationView("KITCHEN", 2, 0), null, ObservationView("HALL", 1, 1), ObservationView("STUDY", 0, 2)),
    )
    private val couch = OccupancyBeat.outside(listOf(2, 0, 3, 1))

    @Test
    fun `every kind is one width on the wire and opens to itself`() {
        for (beat in listOf(reading, couch, OccupancyBeat.Nothing)) {
            val field = OccupancyWire.pack(seed, nonce, beat)
            assertEquals(OccupancyWire.CHARS, field.length, "$beat sealed to another width")
            assertEquals(true, OccupancyWire.isField(field))
            assertEquals(beat, OccupancyWire.unpack(seed, field), "$beat did not open to itself")
        }
        // A hole reads as 0, cold; a view's band is kept.
        val opened = OccupancyWire.unpack(seed, OccupancyWire.pack(seed, nonce, reading)) as OccupancyBeat.Terminal
        assertEquals(listOf(2, 0, 1, 0), opened.counts)
        assertEquals(listOf(OccupancyWire.FRESH, OccupancyWire.COLD, OccupancyWire.STALE, OccupancyWire.COLD), opened.bands)
        assertEquals(0, opened.here)
    }

    @Test
    fun `a field opens under its own seat's seed and no other and a blank opens on no phone`() {
        val field = OccupancyWire.pack(seed, nonce, reading)
        assertNull(OccupancyWire.unpack(other, field), "another seat's phone read the reader's counts")
        assertNull(OccupancyWire.unpack(seed, OccupancyWire.blank(ByteArray(OccupancyWire.FIELD_BYTES) { 0x5a })))
        // One flipped character anywhere is nothing.
        val flipped = field.substring(0, 20) + (if (field[20] == 'A') 'B' else 'A') + field.substring(21)
        assertNull(OccupancyWire.unpack(seed, flipped))
        // Without the seed a reading and nothing are both uniform bytes of one width.
        assertNotEquals(field, OccupancyWire.pack(seed, nonce, OccupancyBeat.Nothing))
        assertEquals(field.length, OccupancyWire.pack(seed, nonce, OccupancyBeat.Nothing).length)
    }

    @Test
    fun `a field that is not exactly one reads as nothing drawn`() {
        val plain = ByteArray(OccupancyWire.PLAIN_BYTES)
        OccupancyWire.write(reading, plain)
        assertEquals(reading, OccupancyWire.read(plain))
        // A band past cold.
        plain.copyOf().also { it[3] = (3 shl 6).toByte() }.let { assertNull(OccupancyWire.read(it)) }
        // A kind nobody sends.
        plain.copyOf().also { it[0] = 3 }.let { assertNull(OccupancyWire.read(it)) }
        // A byte past the rooms.
        plain.copyOf().also { it[OccupancyWire.PLAIN_BYTES - 1] = 1 }.let { assertNull(OccupancyWire.read(it)) }
        // An outline on the couch's.
        ByteArray(OccupancyWire.PLAIN_BYTES).also { OccupancyWire.write(couch, it); it[1] = 1 }.let { assertNull(OccupancyWire.read(it)) }
        // Nothing with anything in it.
        ByteArray(OccupancyWire.PLAIN_BYTES).also { it[2] = 1 }.let { assertNull(OccupancyWire.read(it)) }
    }
}
