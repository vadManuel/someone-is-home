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
 * It deliberately does **not** claim a home survives a reinstall. Nothing running inside the app
 * can observe that: it is a property of device backup, and an instrument reporting a pass about
 * something it cannot see is worse than no instrument. What it does prove is that a save lands and
 * that a load reads back exactly what was written, byte for byte, including the text a host
 * actually types into a phone.
 *
 * It used to prove a third thing — that the three setup files left each other alone — and that
 * test went with the two stores it was about. A home carries its plan and its registrations
 * inside itself, so there is one file now and nothing left for it to collide with.
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
     * **A home's plan and its registrations survive inside the home, not beside it.**
     *
     * This is what replaced the three-file separation test. The failure it guards is the same one
     * and it is still silent: a host keeps a home, forty registrations go, and nothing on screen
     * says so until the next evening. What changed is where they could go — into the same write,
     * rather than into a neighbouring file somebody overwrote.
     */
    @Test
    fun `a home keeps its plan and its registrations in one write`() {
        val homes = "someone-is-home/saved-homes/1\nH THE BUNGALOW\n" +
            "someone-is-home/house-plan/1\nF GROUND\nR room|0,0,3,2|HALL\nT HALL\n" +
            "someone-is-home/house-map/1\nR 10AAAAAAA|HALL\n"
        saveSavedHomes(homes)
        assertEquals(homes, loadSavedHomes(), "a home came back without everything it went in with")
    }
}
