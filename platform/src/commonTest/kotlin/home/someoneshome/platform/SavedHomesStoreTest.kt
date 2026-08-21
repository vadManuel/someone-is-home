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
 * Like [HouseMapStoreTest] and [HousePlanStoreTest] this deliberately does **not** claim a home
 * survives a reinstall. Nothing running inside the app can observe that: it is a property of
 * device backup, and an instrument reporting a pass about something it cannot see is worse than
 * no instrument. What it does prove is that a save lands, that a load reads back exactly what was
 * written, and that the three setup files leave each other alone.
 */
class SavedHomesStoreTest {

    @BeforeTest fun start() = clearSavedHomes()
    @AfterTest fun finish() = clearSavedHomes()

    @Test
    fun `a phone that has kept no home reads as nothing`() {
        assertNull(loadSavedHomes())
    }

    @Test
    fun `saved homes read back byte for byte`() {
        val text = "someone-is-home/saved-homes/1\nH THE BUNGALOW\n" +
            "someone-is-home/house-plan/1\nF GROUND\nR room|0,0,3,2|KITCHEN\nT KITCHEN\n"
        saveSavedHomes(text)
        assertEquals(text, loadSavedHomes())
    }

    /** Deleting a home rewrites the list; it does not append a shorter one. */
    @Test
    fun `saving twice keeps only the second`() {
        saveSavedHomes("first")
        saveSavedHomes("second")
        assertEquals("second", loadSavedHomes())
    }

    @Test
    fun `clearing forgets every home`() {
        saveSavedHomes("something")
        clearSavedHomes()
        assertNull(loadSavedHomes())
    }

    @Test
    fun `clearing nothing is harmless`() {
        clearSavedHomes()
        clearSavedHomes()
        assertNull(loadSavedHomes())
    }

    /** Home names are typed by a host on a phone, so the file holds whatever they typed. */
    @Test
    fun `a list containing awkward text survives`() {
        val text = "someone-is-home/saved-homes/1\nH MAMAN & PAPA'S 🕯\\n\n" +
            "someone-is-home/house-plan/1\nF ÉTAGE 1\nR stairs|0,0,1,1|CAGE D'ESCALIER\n"
        saveSavedHomes(text)
        assertEquals(text, loadSavedHomes())
    }

    /** Eight homes of eleven rooms is a host who has been doing this for a while. */
    @Test
    fun `a long list survives`() {
        val text = buildString {
            appendLine("someone-is-home/saved-homes/1")
            repeat(8) { home ->
                appendLine("H HOME $home")
                appendLine("someone-is-home/house-plan/1")
                appendLine("F GROUND")
                repeat(11) { appendLine("R room|$it,0,1,1|ROOM $home-$it") }
            }
        }
        saveSavedHomes(text)
        assertEquals(text, loadSavedHomes())
        assertTrue(loadSavedHomes()!!.lines().size > 100)
    }

    /**
     * **The three setup files are stored separately and do not overwrite each other.**
     *
     * The homes are the list; the map and the plan are the two halves of one walk. They are
     * written at different moments and lost differently, and the failure would be silent: a host
     * saves a home and forty registrations are gone with nothing on screen to say so until the
     * next evening.
     */
    @Test
    fun `saving homes leaves the map and the plan alone`() {
        val map = "someone-is-home/house-map/1\nR 10AAAAAAA|HALL\n"
        val plan = "someone-is-home/house-plan/1\nF GROUND\nR room|0,0,1,1|HALL\n"
        val homes = "someone-is-home/saved-homes/1\nH X\nsomeone-is-home/house-plan/1\nF GROUND\n"
        try {
            saveHouseMap(map)
            saveHousePlan(plan)
            saveSavedHomes(homes)
            assertEquals(map, loadHouseMap(), "keeping a home destroyed the registrations")
            assertEquals(plan, loadHousePlan(), "keeping a home destroyed the painted plan")
            assertEquals(homes, loadSavedHomes())

            clearSavedHomes()
            assertEquals(map, loadHouseMap(), "forgetting the homes forgot the registrations too")
            assertEquals(plan, loadHousePlan(), "forgetting the homes forgot the plan too")
        } finally {
            clearHouseMap()
            clearHousePlan()
        }
    }
}
