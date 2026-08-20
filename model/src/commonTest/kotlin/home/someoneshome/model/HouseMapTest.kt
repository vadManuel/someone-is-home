package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Story 0.7. Fifteen minutes of walking a dark house, which must not evaporate. */
class HouseMapTest {

    private fun card(shapeId: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shapeId), MarkerId(id))

    private fun setupWalk(): HouseMap {
        var map = HouseMap.EMPTY
        for ((shape, room, id) in listOf(
            Triple("diamond", "HALL", "AAAAAAA"),
            Triple("ring", "GARAGE", "BBBBBBB"),
            Triple("cross", "BED 2", "CCCCCCC"),
            Triple("arrow_right", "STUDY", "DDDDDDD"),
        )) {
            map = (map.register(card(shape, id), Room(room)) as RegisterResult.Registered).map
        }
        return map
    }

    @Test
    fun `a setup walk registers every card to its room`() {
        val map = setupWalk()
        assertEquals(4, map.registrations.size)
        assertEquals(listOf("HALL", "GARAGE", "BED 2", "STUDY"), map.rooms.map { it.name })
        assertEquals(Room("GARAGE"), map.registrationOf(MarkerId("BBBBBBB"))?.room)
    }

    /** Registration order is preserved, because it is what gets written down. */
    @Test
    fun `the map keeps registration order`() {
        assertEquals(
            listOf("AAAAAAA", "BBBBBBB", "CCCCCCC", "DDDDDDD"),
            setupWalk().registrations.map { it.card.id.value },
        )
    }

    /** A host correcting themselves mid-walk moves the card rather than duplicating it. */
    @Test
    fun `re-registering the same card moves it`() {
        val map = setupWalk()
        val result = map.register(card("ring", "BBBBBBB"), Room("CELLAR"))
        assertIs<RegisterResult.Moved>(result)
        assertEquals(Room("GARAGE"), result.from)
        assertEquals(4, result.map.registrations.size, "moving a card duplicated it")
        assertEquals(Room("CELLAR"), result.map.registrationOf(MarkerId("BBBBBBB"))?.room)
    }

    /**
     * **The judgement call, asserted so it is visible rather than assumed.** A second card
     * carrying an already-registered shape is refused: the shape is the marker's whole name, and
     * two markers with one name give a player told to go to the diamond two places to stand.
     */
    @Test
    fun `a second card with an already-registered shape is refused`() {
        val result = setupWalk().register(card("diamond", "ZZZZZZZ"), Room("CELLAR"))
        assertIs<RegisterResult.ShapeAlreadyRegistered>(result)
        assertEquals(MarkerId("AAAAAAA"), result.to.card.id)
        assertEquals(Room("HALL"), result.to.room)
    }

    /**
     * **D-069's reason for the id, end to end.** The replacement is a different card, so the
     * mislaid original stays unregistered instead of reporting a player into the new room.
     */
    @Test
    fun `a reprinted card does not inherit the lost one's registration`() {
        val map = setupWalk()
        val lost = card("diamond", "AAAAAAA")
        val reprint = card("diamond", "EEEEEEE")

        // The host tears up the record of the lost card, then registers the replacement.
        val after = (map.forget(lost.id).register(reprint, Room("CELLAR")) as RegisterResult.Registered).map

        assertNull(after.registrationOf(lost.id), "the mislaid card is still registered")
        assertEquals(Room("CELLAR"), after.registrationOf(reprint.id)?.room)
        assertEquals(
            4, after.registrations.size,
            "the house should still have four markers, not five",
        )
    }

    @Test
    fun `a room lists the cards registered in it`() {
        val map = (setupWalk().register(card("star", "FFFFFFF"), Room("HALL")) as RegisterResult.Registered).map
        assertEquals(
            listOf("AAAAAAA", "FFFFFFF"),
            map.inRoom(Room("HALL")).map { it.card.id.value },
        )
    }

    // ---- persistence ----

    /** The whole point: the setup walk survives being written down and read back. */
    @Test
    fun `a map round-trips through its stored form`() {
        val map = setupWalk()
        val reread = HouseMapText.read(HouseMapText.write(map))
        assertEquals(map.registrations, reread.registrations)
        assertEquals(HouseMapText.write(map), HouseMapText.write(reread))
    }

    /** What is stored is what is printed, so the file cannot disagree with the paper. */
    @Test
    fun `the stored form holds the printed payload`() {
        val text = HouseMapText.write(setupWalk())
        val payload = CardPayload.encode(card("diamond", "AAAAAAA"))
        assertTrue(text.contains("R $payload|HALL"), text)
    }

    /** A room name is typed by a host and can contain anything, including a separator. */
    @Test
    fun `a room name containing separators survives`() {
        val awkward = Room("KITCHEN|GARAGE\\NOOK\nBACK")
        val map = (HouseMap.EMPTY.register(card("circle", "0000001"), awkward) as RegisterResult.Registered).map
        val reread = HouseMapText.read(HouseMapText.write(map))
        assertEquals(1, reread.registrations.size, "an embedded newline forged a row")
        assertEquals(awkward, reread.registrations[0].room)
    }

    @Test
    fun `an empty map round-trips`() {
        assertEquals(emptyList(), HouseMapText.read(HouseMapText.write(HouseMap.EMPTY)).registrations)
    }

    @Test
    fun `a map from another format version is refused`() {
        val text = HouseMapText.write(setupWalk()).replaceFirst(HouseMapText.HEADER, "someone-is-home/house-map/0")
        assertFailsWith<MalformedHouseMap> { HouseMapText.read(text) }
    }

    /** A map one registration short is a marker nobody knows is missing. */
    @Test
    fun `an unknown row is refused rather than skipped`() {
        assertFailsWith<MalformedHouseMap> { HouseMapText.read(HouseMapText.write(setupWalk()) + "X junk\n") }
    }

    @Test
    fun `a row with no room is refused`() {
        val text = HouseMapText.HEADER + "\nR " + CardPayload.encode(card("circle", "0000001")) + "\n"
        assertFailsWith<MalformedHouseMap> { HouseMapText.read(text) }
    }

    @Test
    fun `a row with an empty room name is refused`() {
        val text = HouseMapText.HEADER + "\nR " + CardPayload.encode(card("circle", "0000001")) + "|\n"
        assertFailsWith<MalformedHouseMap> { HouseMapText.read(text) }
    }

    @Test
    fun `a corrupted card payload is refused`() {
        val text = HouseMapText.write(setupWalk()).replaceFirst("R 1", "R Z")
        val failure = assertFailsWith<MalformedHouseMap> { HouseMapText.read(text) }
        assertTrue(failure.message!!.contains("UnknownVersion"), failure.message!!)
    }

    /** The same card twice in a file is a file that cannot say which room it is in. */
    @Test
    fun `a duplicated card is refused`() {
        val row = "R " + CardPayload.encode(card("circle", "0000001")) + "|HALL"
        assertFailsWith<MalformedHouseMap> {
            HouseMapText.read(HouseMapText.HEADER + "\n" + row + "\n" + row + "\n")
        }
    }

    @Test
    fun `an empty file is refused rather than read as an empty house`() {
        assertFailsWith<MalformedHouseMap> { HouseMapText.read("") }
    }

    /** The failure names the line, because "malformed" alone sends nobody anywhere. */
    @Test
    fun `a failure names the line it failed on`() {
        val failure = assertFailsWith<MalformedHouseMap> {
            HouseMapText.read(HouseMapText.write(setupWalk()) + "X junk\n")
        }
        assertEquals(6, failure.line, "four registrations plus a header, then the junk row")
    }
}
