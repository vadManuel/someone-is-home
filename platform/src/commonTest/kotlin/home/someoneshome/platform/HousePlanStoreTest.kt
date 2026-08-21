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
 * Like [HouseMapStoreTest] this deliberately does **not** assert that a plan survives reinstall.
 * Nothing running inside the app can observe that; it is a property of device backup, and claiming
 * it here would be an instrument reporting a pass about something it cannot see.
 */
class HousePlanStoreTest {

    @BeforeTest fun start() = clearHousePlan()
    @AfterTest fun finish() = clearHousePlan()

    @Test
    fun `nothing painted reads as nothing`() {
        assertNull(loadHousePlan())
    }

    @Test
    fun `a saved plan reads back byte for byte`() {
        val text = "someone-is-home/house-plan/1\nF GROUND\nR room|0,0,3,2;0,2,1,2|KITCHEN\n"
        saveHousePlan(text)
        assertEquals(text, loadHousePlan())
    }

    /** A host painting a different house overwrites rather than appending. */
    @Test
    fun `saving twice keeps only the second`() {
        saveHousePlan("first")
        saveHousePlan("second")
        assertEquals("second", loadHousePlan())
    }

    @Test
    fun `clearing forgets the plan`() {
        saveHousePlan("something")
        clearHousePlan()
        assertNull(loadHousePlan())
    }

    @Test
    fun `clearing nothing is harmless`() {
        clearHousePlan()
        clearHousePlan()
        assertNull(loadHousePlan())
    }

    /** Room names are typed by a host on a phone, so the file holds whatever they typed. */
    @Test
    fun `a plan containing awkward text survives`() {
        val text = "someone-is-home/house-plan/1\nF ÉTAGE 1\nR stairs|0,0,1,1|CAGE D'ESCALIER 🕯\\n\n"
        saveHousePlan(text)
        assertEquals(text, loadHousePlan())
    }

    @Test
    fun `a large plan survives`() {
        val text = buildString {
            appendLine("someone-is-home/house-plan/1")
            appendLine("F GROUND")
            repeat(44) { appendLine("R room|$it,0,1,1|ROOM $it") }
        }
        saveHousePlan(text)
        assertEquals(text, loadHousePlan())
        assertTrue(loadHousePlan()!!.lines().size > 44)
    }

    /**
     * **The two halves of the setup walk are stored separately and do not overwrite each other.**
     *
     * This is the reason for a second file rather than a second section, and it is the failure
     * that would be silent: a host repaints a wall, the plan saves, and forty registrations are
     * gone with nothing on screen to say so until the next evening.
     */
    @Test
    fun `saving a plan leaves the map alone and the reverse`() {
        val map = "someone-is-home/house-map/1\nR 10AAAAAAA|HALL\n"
        val plan = "someone-is-home/house-plan/1\nF GROUND\nR room|0,0,1,1|HALL\n"
        try {
            saveHouseMap(map)
            saveHousePlan(plan)
            assertEquals(map, loadHouseMap(), "painting the house destroyed the registrations")
            assertEquals(plan, loadHousePlan())

            clearHousePlan()
            assertEquals(map, loadHouseMap(), "forgetting the plan forgot the registrations too")
        } finally {
            clearHouseMap()
        }
    }
}
