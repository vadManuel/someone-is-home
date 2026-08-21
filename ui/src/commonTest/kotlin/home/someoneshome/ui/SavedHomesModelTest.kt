package home.someoneshome.ui

import home.someoneshome.model.SavedHome
import home.someoneshome.model.SavedHomesText

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A store that counts what it was asked to do, because *"nothing was written"* is the claim half
 * of this file is making and an assertion about the list alone cannot see it.
 */
private class RecordingStore(private var text: String? = null) : HomeStore {
    var writes = 0
        private set

    override fun read(): String? = text

    override fun write(text: String) {
        writes++
        this.text = text
    }
}

/** A phone whose filesystem is full. The failure D-087 is written about, one layer up. */
private class DeadStore(private val text: String? = null) : HomeStore {
    override fun read(): String? = text
    override fun write(text: String): Unit = throw IllegalStateException("no space on device")
}

private fun bungalow(name: String = "THE BUNGALOW"): SavedHome =
    HomeEditorModel.bungalow().asSavedHome(name)

/**
 * **The list of homes, and the file underneath it.**
 *
 * The thing being protected is fifteen minutes of a host walking a real house in the light. Every
 * test here is about one of the two ways that is lost: a save that did not land while the screen
 * said it did, or a write that went over homes this build could not read.
 */
class SavedHomesModelTest {

    @Test
    fun `a phone that has kept nothing shows nothing`() {
        val homes = SavedHomesModel(MemoryHomeStore())
        assertTrue(homes.isEmpty)
        assertNull(homes.open)
        assertFalse(homes.unreadable)
    }

    @Test
    fun `a saved home is on the list and in the store`() {
        val store = RecordingStore()
        val homes = SavedHomesModel(store)

        assertTrue(homes.save(bungalow()))
        assertEquals(listOf("THE BUNGALOW"), homes.homes.map { it.name })
        assertEquals("THE BUNGALOW", homes.openName)
        assertEquals(1, store.writes)
    }

    /**
     * **The relaunch, in miniature.**
     *
     * A second model over the same store is what a killed and reopened app is: nothing in memory,
     * a file on disk, and a list that has to come back out of it. The real proof is a phone, and
     * this is the one that fails on the build.
     */
    @Test
    fun `a second model over the same store sees the same homes`() {
        val store = RecordingStore()
        SavedHomesModel(store).save(bungalow())

        val reopened = SavedHomesModel(store)
        assertEquals(listOf("THE BUNGALOW"), reopened.homes.map { it.name })
        assertEquals(bungalow(), reopened.homes.single())
        assertEquals(9, reopened.homes.single().markerCount, "the cards did not come back")
        assertEquals("HALL", reopened.homes.single().terminal, "the terminal did not come back")
    }

    @Test
    fun `the newest home is at the top of the list`() {
        val homes = SavedHomesModel(MemoryHomeStore())
        homes.save(bungalow("FIRST"))
        homes.closeHome()
        homes.save(bungalow("SECOND"))
        assertEquals(listOf("SECOND", "FIRST"), homes.homes.map { it.name })
    }

    /** Editing a home you already have does not leave you with two of it. */
    @Test
    fun `saving an open home replaces it`() {
        val homes = SavedHomesModel(MemoryHomeStore())
        homes.save(bungalow())
        homes.save(bungalow())
        assertEquals(1, homes.homes.size)
    }

    /**
     * A rename is a move, not a copy — and the plan and the cards go with it, because a home is
     * one thing.
     */
    @Test
    fun `saving an open home under a new name renames it`() {
        val homes = SavedHomesModel(MemoryHomeStore())
        homes.save(bungalow())
        assertTrue(homes.save(bungalow().renamedTo("MUM & DAD'S")))

        assertEquals(listOf("MUM & DAD'S"), homes.homes.map { it.name })
        assertEquals("MUM & DAD'S", homes.openName)
        assertEquals(9, homes.open!!.markerCount)
        assertEquals(11, homes.open!!.roomCount)
    }

    @Test
    fun `a name another home already holds is refused and nothing is written`() {
        val store = RecordingStore()
        val homes = SavedHomesModel(store)
        homes.save(bungalow("THE LAKE PLACE"))
        homes.closeHome()

        val writesBefore = store.writes
        assertFalse(homes.save(bungalow("THE LAKE PLACE")))
        assertEquals("A HOME IS ALREADY CALLED THE LAKE PLACE", homes.refusal)
        assertEquals(1, homes.homes.size)
        assertEquals(writesBefore, store.writes, "a refused save wrote to the phone anyway")
    }

    @Test
    fun `deleting the open home takes it off the phone`() {
        val store = RecordingStore()
        val homes = SavedHomesModel(store)
        homes.save(bungalow())

        assertTrue(homes.deleteOpen())
        assertTrue(homes.isEmpty)
        assertNull(homes.openName)
        assertEquals(emptyList(), SavedHomesModel(store).homes, "it came back after the relaunch")
    }

    @Test
    fun `deleting with nothing open deletes nothing`() {
        val homes = SavedHomesModel(MemoryHomeStore())
        homes.save(bungalow())
        homes.closeHome()
        assertFalse(homes.deleteOpen())
        assertEquals(1, homes.homes.size)
    }

    /**
     * **A save that did not land does not update the list.**
     *
     * The list on screen is the list on the phone, or it is a host being told their house is kept
     * when it is not — which is the whole of D-087, arriving one layer above the store.
     */
    @Test
    fun `a phone that cannot write is said so and the list does not move`() {
        val homes = SavedHomesModel(DeadStore())
        assertFalse(homes.save(bungalow()))
        assertEquals("THIS PHONE DID NOT SAVE IT", homes.refusal)
        assertTrue(homes.isEmpty, "the list showed a home the phone does not have")
        assertNull(homes.openName)
    }

    /**
     * **Homes this build cannot read are never written over.**
     *
     * Coming up empty and saving on top would delete every house on the phone to make room for
     * one, silently, on the evening the host went looking for them.
     */
    @Test
    fun `a file that cannot be read blocks every write`() {
        val store = RecordingStore("someone-is-home/saved-homes/2\nH FROM THE FUTURE\n")
        val homes = SavedHomesModel(store)

        assertTrue(homes.unreadable)
        assertTrue(homes.isEmpty)
        assertNotNull(homes.refusal, "it came up empty and said nothing")

        assertFalse(homes.save(bungalow()))
        assertFalse(homes.deleteOpen())
        assertEquals(0, store.writes, "something was written over homes that could not be read")
    }

    @Test
    fun `opening a home the phone does not have opens nothing`() {
        val homes = SavedHomesModel(MemoryHomeStore())
        homes.save(bungalow())
        homes.openHome("A HOUSE NOBODY WALKED")
        assertEquals("THE BUNGALOW", homes.openName)
    }

    @Test
    fun `a new home is offered an obviously provisional name`() {
        val homes = SavedHomesModel(MemoryHomeStore())
        assertEquals("HOME 1", homes.freeName())
        homes.save(bungalow("HOME 1"))
        assertEquals("HOME 2", homes.freeName())
    }

    // ---- The sample the screens are drawn against ---------------------------------------------

    @Test
    fun `the sample is the design's three homes with the bungalow open`() {
        val homes = SavedHomesModel.sample()
        assertEquals(
            listOf("THE BUNGALOW", "MUM & DAD'S", "THE LAKE PLACE"), homes.homes.map { it.name },
        )
        assertEquals("THE BUNGALOW", homes.openName)
    }

    /** The counts the design wrote on that screen, now counted rather than written down. */
    @Test
    fun `the sample bungalow is the editor's bungalow`() {
        val bungalow = SavedHomesModel.sample().open!!
        assertEquals(2, bungalow.floorCount)
        assertEquals(11, bungalow.roomCount)
        assertEquals(9, bungalow.markerCount)
        assertEquals(HomeEditorModel.bungalow().asSavedHome(), bungalow)
    }

    @Test
    fun `the other two sample homes carry the counts the design gave them`() {
        val homes = SavedHomesModel.sample().homes
        assertEquals(2 to 9, homes[1].floorCount to homes[1].roomCount)
        assertEquals(1 to 6, homes[2].floorCount to homes[2].roomCount)
    }

    // ---- What a home cost to walk -------------------------------------------------------------

    /**
     * The design's own sentence, against the design's own home: *"About fifteen minutes of
     * walking this home"* for eleven rooms and nine markers.
     */
    @Test
    fun `the bungalow cost about fifteen minutes`() {
        assertEquals(15, walkMinutes(rooms = 11, markers = 9))
        assertEquals("fifteen minutes", walkedInWords(rooms = 11, markers = 9))
    }

    @Test
    fun `a home is never worth less than five minutes and the words carry the unit`() {
        assertEquals(5, walkMinutes(rooms = 1, markers = 0))
        assertEquals("five minutes", walkedInWords(rooms = 1, markers = 0))
        assertEquals("an hour", walkedInWords(rooms = 40, markers = 40))
    }

    /** Written into the file rather than derived at read time, so it cannot drift from the plan. */
    @Test
    fun `the stored text is the format's and not this layer's`() {
        val store = RecordingStore()
        SavedHomesModel(store).save(bungalow())
        val text = assertNotNull(store.read())
        assertEquals(listOf(bungalow()), SavedHomesText.read(text))
    }
}
