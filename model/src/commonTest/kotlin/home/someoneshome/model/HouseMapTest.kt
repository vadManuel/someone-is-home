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

/**
 * **The card marked T, and the one room a home can have it in.**
 *
 * The terminal is where a Resident stands alone in the dark on purpose — exclusive access, one
 * reader at a time, highly campable by design. A home with two of them has two places to be
 * found and the trade the whole map is built on stops costing anything.
 */
class TerminalTest {

    private fun card(shapeId: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shapeId), MarkerId(id))

    private val tCard = card("t_shape", "TTTTTTT")

    private fun placed(room: String = "HALL"): HouseMap =
        (HouseMap.EMPTY.register(tCard, Room(room)) as RegisterResult.Registered).map

    /** Read off the payload, so it is a fact about the paper and not a flag somebody set. */
    @Test
    fun `the card marked T says so itself`() {
        assertTrue(tCard.isTerminal)
        assertTrue(!card("ring", "BBBBBBB").isTerminal)
        assertEquals(42, MarkerShapes.registrable.size, "the roster minus the two spoken for")
    }

    @Test
    fun `the T card places the terminal and is not an ordinary marker`() {
        val map = placed()
        assertEquals(Room("HALL"), map.terminal?.room)
        assertEquals(emptyList(), map.registrations, "the T card was filed as a marker")
        assertEquals(emptyList(), map.inRoomNamed("HALL"), "the T card was filed as a marker")
        assertTrue(map.holdsAnything("HALL"), "the hall holds the terminal and says it holds nothing")
    }

    /** Nothing is at stake, so nothing is asked. The host scanned the card where it already is. */
    @Test
    fun `scanning the T card in the room it is already in is not a question`() {
        val result = placed().register(tCard, Room("HALL"))
        assertIs<RegisterResult.Registered>(result)
        assertEquals(Room("HALL"), result.map.terminal?.room)
    }

    /** **Never a silent move.** The host is told where it is, because they have to go and get it. */
    @Test
    fun `the T card in a second room is refused and names the first`() {
        val result = placed().register(tCard, Room("GARAGE"))
        assertIs<RegisterResult.TerminalTaken>(result)
        assertEquals(Room("HALL"), result.at.room)
    }

    @Test
    fun `moving the terminal is a separate act and says where it came from`() {
        val result = placed().moveTerminal(tCard, Room("GARAGE"))
        assertIs<RegisterResult.Moved>(result)
        assertEquals(Room("HALL"), result.from)
        assertEquals(Room("GARAGE"), result.map.terminal?.room)
    }

    /** D-099 covers the T card too: the stairwell is invisible to the Terminal, not host to it. */
    @Test
    fun `the terminal cannot go into stairs whichever way it is offered`() {
        val stairs = Room("LANDING WELL", RoomKind.Stairs)
        assertIs<RegisterResult.StairsHoldNothing>(HouseMap.EMPTY.register(tCard, stairs))
        assertIs<RegisterResult.StairsHoldNothing>(placed().moveTerminal(tCard, stairs))
    }

    @Test
    fun `forgetting the T card takes the terminal with it`() {
        assertNull(placed().forget(tCard.id).terminal)
        assertNull(placed().forgetTerminal().terminal)
    }

    /** Stairs hold nothing, and that has to happen as part of the room becoming stairs. */
    @Test
    fun `forgetting a room takes its cards and its terminal`() {
        val map = (placed().register(card("ring", "BBBBBBB"), Room("HALL")) as RegisterResult.Registered).map
        val after = map.forgetIn("HALL")
        assertEquals(emptyList(), after.registrations)
        assertNull(after.terminal)
    }

    /** A room's name is its identity everywhere else, so a rename has to carry what is in it. */
    @Test
    fun `renaming a room carries its cards and its terminal`() {
        val map = (placed().register(card("ring", "BBBBBBB"), Room("HALL")) as RegisterResult.Registered).map
        val after = map.renamedRoom("HALL", Room("ENTRY"))
        assertEquals(listOf("ENTRY"), after.registrations.map { it.room.name })
        assertEquals(Room("ENTRY"), after.terminal?.room)
    }

    @Test
    fun `the terminal survives the stored form`() {
        val map = (placed().register(card("ring", "BBBBBBB"), Room("GARAGE")) as RegisterResult.Registered).map
        val reread = HouseMapText.read(HouseMapText.write(map))
        assertEquals(map.terminal, reread.terminal)
        assertEquals(map.registrations, reread.registrations)
    }
}

/**
 * **The meeting card, and the one room a home can have it in** (D-121).
 *
 * A meeting is called by physically walking to this card and scanning it — never remotely — and
 * the caller's scan is their check-in. A home with two of them is a home the house would take a
 * meeting from in two places, and half the party would walk to the wrong one in the dark.
 *
 * Everything here mirrors [TerminalTest] because the two cards are the same kind of thing, and the
 * one place they differ is asserted rather than assumed: **the meeting card reserves a shape, not
 * a room**, so ordinary markers may share the room it is in.
 */
class MeetingTest {

    private fun card(shapeId: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shapeId), MarkerId(id))

    private val uCard = card("u_shape", "UUUUUUU")

    private fun placed(room: String = "LIVING"): HouseMap =
        (HouseMap.EMPTY.register(uCard, Room(room)) as RegisterResult.Registered).map

    /** Read off the payload, so it is a fact about the paper and not a flag somebody set. */
    @Test
    fun `the meeting card says so itself`() {
        assertTrue(uCard.isMeeting)
        assertTrue(!uCard.isTerminal)
        assertTrue(!card("ring", "BBBBBBB").isMeeting)
        assertTrue(card("ring", "BBBBBBB").isOrdinary)
        assertEquals(2, MarkerShapes.reserved.size, "T and the meeting card, and nothing else")
        assertTrue(MarkerShapes.MEETING !in MarkerShapes.registrable)
    }

    @Test
    fun `the meeting card places the meeting area and is not an ordinary marker`() {
        val map = placed()
        assertEquals(Room("LIVING"), map.meeting?.room)
        assertEquals(emptyList(), map.registrations, "the meeting card was filed as a marker")
        assertEquals(emptyList(), map.inRoomNamed("LIVING"), "the meeting card was filed as a marker")
        assertTrue(map.holdsAnything("LIVING"), "LIVING holds it and says it holds nothing")
    }

    /**
     * **A shape is reserved, a room is not** (D-121) — the one way this differs from the terminal.
     *
     * The meeting area is a room people already gather in, so ordinary markers being registered
     * there is the expected case rather than an edge one. A map that excluded the room would
     * quietly cost the host a card in the busiest room of the house.
     */
    @Test
    fun `ordinary markers may share the meeting card's room`() {
        val map = placed()
        val result = map.register(card("ring", "BBBBBBB"), Room("LIVING"))
        assertIs<RegisterResult.Registered>(result)
        assertEquals(Room("LIVING"), result.map.meeting?.room)
        assertEquals(listOf(MarkerId("BBBBBBB")), result.map.inRoomNamed("LIVING").map { it.card.id })
    }

    /** Nothing is at stake, so nothing is asked. The host scanned the card where it already is. */
    @Test
    fun `scanning the meeting card in the room it is already in is not a question`() {
        val result = placed().register(uCard, Room("LIVING"))
        assertIs<RegisterResult.Registered>(result)
        assertEquals(Room("LIVING"), result.map.meeting?.room)
    }

    /** **Never a silent move.** The host is told where it is, because they have to go and get it. */
    @Test
    fun `the meeting card in a second room is refused and names the first`() {
        val result = placed().register(uCard, Room("KITCHEN"))
        assertIs<RegisterResult.MeetingTaken>(result)
        assertEquals(Room("LIVING"), result.at.room)
    }

    @Test
    fun `moving the meeting card is a separate act and says where it came from`() {
        val result = placed().moveMeeting(uCard, Room("KITCHEN"))
        assertIs<RegisterResult.Moved>(result)
        assertEquals(Room("LIVING"), result.from)
        assertEquals(Room("KITCHEN"), result.map.meeting?.room)
    }

    /** D-099 covers it too: stairs hold nothing, and nobody holds a meeting on the stairs. */
    @Test
    fun `the meeting card cannot go into stairs whichever way it is offered`() {
        val stairs = Room("LANDING WELL", RoomKind.Stairs)
        assertIs<RegisterResult.StairsHoldNothing>(HouseMap.EMPTY.register(uCard, stairs))
        assertIs<RegisterResult.StairsHoldNothing>(placed().moveMeeting(uCard, stairs))
    }

    @Test
    fun `forgetting the meeting card takes the meeting area with it`() {
        assertNull(placed().forget(uCard.id).meeting)
        assertNull(placed().forgetMeeting().meeting)
    }

    @Test
    fun `forgetting a room takes its cards and its meeting card`() {
        val map = (placed().register(card("ring", "BBBBBBB"), Room("LIVING")) as RegisterResult.Registered).map
        val after = map.forgetIn("LIVING")
        assertEquals(emptyList(), after.registrations)
        assertNull(after.meeting)
    }

    @Test
    fun `renaming a room carries its cards and its meeting card`() {
        val map = (placed().register(card("ring", "BBBBBBB"), Room("LIVING")) as RegisterResult.Registered).map
        val after = map.renamedRoom("LIVING", Room("LOUNGE"))
        assertEquals(listOf("LOUNGE"), after.registrations.map { it.room.name })
        assertEquals(Room("LOUNGE"), after.meeting?.room)
    }

    /**
     * The two reserved cards are independent, and each stays where it is while the other moves.
     *
     * Held as one test because the failure it is written against is a shared field: an
     * implementation that kept "the reserved card" in one place would pass every test above and
     * lose the terminal the moment the meeting card was placed.
     */
    @Test
    fun `the terminal and the meeting card do not disturb each other`() {
        val tCard = card("t_shape", "TTTTTTT")
        val both = (placed().register(tCard, Room("CELLAR")) as RegisterResult.Registered).map
        assertEquals(Room("LIVING"), both.meeting?.room)
        assertEquals(Room("CELLAR"), both.terminal?.room)

        val movedMeeting = (both.moveMeeting(uCard, Room("KITCHEN")) as RegisterResult.Moved).map
        assertEquals(Room("CELLAR"), movedMeeting.terminal?.room, "moving one lost the other")

        // The other direction, and it is not symmetry for its own sake: found asleep. Every test
        // above walked the meeting card, so a `moveTerminal` that rebuilt the map without carrying
        // the meeting field through deleted the meeting card and nothing failed. A host would meet
        // that by moving their terminal and finding the house had nowhere to hold a meeting.
        val movedTerminal = (both.moveTerminal(tCard, Room("GARAGE")) as RegisterResult.Moved).map
        assertEquals(Room("LIVING"), movedTerminal.meeting?.room, "moving the terminal lost it")

        assertEquals(Room("KITCHEN"), movedMeeting.forgetTerminal().meeting?.room)
        assertEquals(Room("CELLAR"), movedMeeting.forgetMeeting().terminal?.room)
    }

    @Test
    fun `the meeting card survives the stored form`() {
        val map = (placed().register(card("ring", "BBBBBBB"), Room("GARAGE")) as RegisterResult.Registered).map
        val reread = HouseMapText.read(HouseMapText.write(map))
        assertEquals(map.meeting, reread.meeting)
        assertEquals(map.registrations, reread.registrations)
    }

    /** The reader refuses what the writer can never produce, because a file can be edited. */
    @Test
    fun `a stored meeting row that is not the U card is refused`() {
        val ordinary = CardPayload.encode(card("ring", "BBBBBBB"))
        val failure = assertFailsWith<MalformedHouseMap> {
            HouseMapText.read(HouseMapText.HEADER + "\nM $ordinary|LIVING\n")
        }
        assertTrue("is not the card marked U" in failure.detail, failure.detail)
    }

    @Test
    fun `the U card stored as an ordinary marker is refused`() {
        val payload = CardPayload.encode(uCard)
        val failure = assertFailsWith<MalformedHouseMap> {
            HouseMapText.read(HouseMapText.HEADER + "\nR $payload|LIVING\n")
        }
        assertTrue("registered as an ordinary marker" in failure.detail, failure.detail)
        assertFailsWith<IllegalArgumentException> {
            HouseMap.of(listOf(Registration(uCard, Room("LIVING"))))
        }
    }

    @Test
    fun `a second stored meeting row is refused`() {
        val payload = CardPayload.encode(uCard)
        val second = CardPayload.encode(card("u_shape", "UUUUUU2"))
        val failure = assertFailsWith<MalformedHouseMap> {
            HouseMapText.read("${HouseMapText.HEADER}\nM $payload|LIVING\nM $second|KITCHEN\n")
        }
        assertTrue("a second meeting card" in failure.detail, failure.detail)
    }

    /**
     * D-069's hazard, arriving through the file rather than through a reprint.
     *
     * One printed id named as a marker and as the meeting card reads back clean otherwise, and the
     * card it collides with is a real piece of paper somebody is holding.
     */
    @Test
    fun `a card id used by a marker and by the meeting card is refused`() {
        val marker = CardPayload.encode(card("ring", "SHARED1"))
        val same = CardPayload.encode(card("u_shape", "SHARED1"))
        val failure = assertFailsWith<MalformedHouseMap> {
            HouseMapText.read("${HouseMapText.HEADER}\nR $marker|GARAGE\nM $same|LIVING\n")
        }
        assertTrue("appears twice" in failure.detail, failure.detail)
    }
}

class StairsHoldNothingTest {

    private fun card(shapeId: String, id: String) =
        MarkerCard(CardPayload.VERSION, MarkerShapes.require(shapeId), MarkerId(id))

    @Test
    fun registeringIntoStairsIsRefusedWithItsOwnKind() {
        val stairs = Room("LANDING WELL", RoomKind.Stairs)
        val result = HouseMap.EMPTY.register(card("diamond", "AAAAAAA"), stairs)
        assertIs<RegisterResult.StairsHoldNothing>(result)
        assertEquals(stairs, result.room)
    }

    @Test
    fun aStairsRegistrationCannotEvenBeConstructed() {
        // The last-ditch guarantee behind the polite refusal: HouseMap.of() rebuilds from
        // storage, and a hand-built list must not be able to smuggle a card into stairs.
        assertFailsWith<IllegalArgumentException> {
            Registration(card("ring", "BBBBBBB"), Room("LANDING WELL", RoomKind.Stairs))
        }
    }

    @Test
    fun ordinaryRoomsStillRegister() {
        val result = HouseMap.EMPTY.register(card("cross", "CCCCCCC"), Room("GARAGE"))
        assertIs<RegisterResult.Registered>(result)
    }
}
