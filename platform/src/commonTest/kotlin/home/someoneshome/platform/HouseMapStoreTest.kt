package home.someoneshome.platform

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * File storage, not game hardware — one of the few things the simulator can honestly verify.
 *
 * This deliberately does NOT assert that a map survives reinstall. Nothing running inside the app
 * can observe that; it is a property of device backup, and claiming it here would be an
 * instrument reporting a pass about something it cannot see.
 */
class HouseMapStoreTest {

    @BeforeTest fun start() = clearHouseMap()
    @AfterTest fun finish() = clearHouseMap()

    @Test
    fun `nothing stored reads as nothing`() {
        assertNull(loadHouseMap())
    }

    @Test
    fun `a saved map reads back byte for byte`() {
        val text = "someone-is-home/house-map/1\nR 10AAAAAAA|HALL\nR 11BBBBBBB|GARAGE\n"
        saveHouseMap(text)
        assertEquals(text, loadHouseMap())
    }

    /** A host setting up a different house overwrites rather than appending. */
    @Test
    fun `saving twice keeps only the second`() {
        saveHouseMap("first")
        saveHouseMap("second")
        assertEquals("second", loadHouseMap())
    }

    @Test
    fun `clearing forgets the map`() {
        saveHouseMap("something")
        clearHouseMap()
        assertNull(loadHouseMap())
    }

    /** Clearing a map that was never written is not an error. */
    @Test
    fun `clearing nothing is harmless`() {
        clearHouseMap()
        clearHouseMap()
        assertNull(loadHouseMap())
    }

    /**
     * Room names are typed by a host on a phone, so the file has to hold whatever they typed —
     * accents, emoji, newlines. UTF-8 in and UTF-8 out, unchanged.
     */
    @Test
    fun `a map containing awkward text survives`() {
        val text = "someone-is-home/house-map/1\nR 10AAAAAAA|SALLE À MANGER 🕯\\nBACK\n"
        saveHouseMap(text)
        assertEquals(text, loadHouseMap())
    }

    /** A map larger than any real house still round-trips. */
    @Test
    fun `a large map survives`() {
        val text = buildString {
            appendLine("someone-is-home/house-map/1")
            repeat(44) { appendLine("R 10AAAAA${it / 10}${it % 10}|ROOM $it") }
        }
        saveHouseMap(text)
        assertEquals(text, loadHouseMap())
        assertTrue(loadHouseMap()!!.lines().size > 44)
    }
}
