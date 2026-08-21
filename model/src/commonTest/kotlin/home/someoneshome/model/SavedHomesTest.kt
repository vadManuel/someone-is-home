package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun painted(name: String, kind: RoomKind = RoomKind.Room, x: Int, y: Int) =
    PlanRoom(Room(name, kind), listOf(CellRect(x, y, 2, 2)))

/**
 * A house with somewhere to put things: two storeys, a stairwell that can hold nothing, and a
 * hall that holds the terminal.
 */
private fun plan(): HousePlan = HousePlan.of(
    listOf(
        Floor("GROUND", listOf(painted("KITCHEN", x = 0, y = 0), painted("HALL", x = 2, y = 0))),
        Floor(
            "UPPER",
            listOf(
                painted("BED 1", x = 0, y = 0),
                painted("TOP OF STAIRS", RoomKind.Stairs, x = 2, y = 0),
            ),
        ),
    )
)

private fun shapes(vararg ids: String) = ids.map { MarkerShapes[it]!! }

/** A printed card. The ids are readable so a failure names something a person can look for. */
private fun card(shape: String, id: String) =
    MarkerCard(CardPayload.VERSION, MarkerShapes.require(shape), MarkerId(id))

private fun payload(shape: String, id: String) = CardPayload.encode(card(shape, id))

private fun registered(shape: String, id: String, room: String, kind: RoomKind = RoomKind.Room) =
    Registration(card(shape, id), Room(room, kind))

/** Three ordinary cards and the T card in the hall — what a short setup walk leaves behind. */
private fun map(): HouseMap = HouseMap.of(
    listOf(
        registered("triangle_up", "CARD-01", "KITCHEN"),
        registered("square", "CARD-02", "KITCHEN"),
        registered("ring", "CARD-03", "BED 1"),
    ),
    registered("t_shape", "CARD-0T", "HALL"),
)

private fun home(name: String = "THE BUNGALOW") = SavedHome(name, plan(), map())

/**
 * **What a host owns, and the file it survives in.**
 *
 * The list is the only thing on the phone that fifteen minutes of walking a real house turns
 * into, so the failure this file is written against is not a crash: it is a list that comes back
 * one home, one room or one card short, on the evening eight people are already in the hall.
 */
class SavedHomesTest {

    // ---- The home itself --------------------------------------------------------------------

    @Test
    fun `a home counts its own floors and rooms and cards`() {
        val bungalow = home()
        assertEquals(2, bungalow.floorCount)
        assertEquals(4, bungalow.roomCount)
        assertEquals(3, bungalow.markerCount)
        assertEquals(shapes("ring"), bungalow.markersIn("BED 1"))
        assertEquals(emptyList(), bungalow.markersIn("HALL"), "the terminal is not a marker")
        assertEquals("HALL", bungalow.terminal)
    }

    @Test
    fun `a home with no name is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> { SavedHome("", plan()) }
        assertEquals("a home with no name", thrown.message)
    }

    /** A card registered somewhere this house does not have is a card in no room at all. */
    @Test
    fun `cards in a room that is not in the plan are refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            SavedHome("H", plan(), HouseMap.of(listOf(registered("ring", "CARD-01", "CELLAR"))))
        }
        assertTrue("'CELLAR'" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    /** D-099, at the storage boundary: stairs hold nothing, and a file cannot say otherwise. */
    @Test
    fun `cards in stairs are refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            SavedHome(
                "H",
                plan(),
                // Constructed as an ordinary room so the plan is the thing that says it is stairs
                // — `Registration` refuses the other spelling outright.
                HouseMap.of(listOf(registered("ring", "CARD-01", "TOP OF STAIRS"))),
            )
        }
        assertTrue("stairs hold nothing" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    @Test
    fun `a terminal in stairs or in no room is refused`() {
        for (room in listOf("TOP OF STAIRS", "CELLAR")) {
            assertFailsWith<IllegalArgumentException> {
                SavedHome("H", plan(), HouseMap.of(emptyList(), registered("t_shape", "CARD-0T", room)))
            }
        }
    }

    /** **The T card is never an ordinary marker.** Not a convention — the map cannot hold one. */
    @Test
    fun `the card marked T cannot be an ordinary registration`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            HouseMap.of(listOf(registered("t_shape", "CARD-0T", "KITCHEN")))
        }
        assertTrue("never is" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    @Test
    fun `a terminal that is not the card marked T is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            HouseMap.of(emptyList(), registered("ring", "CARD-01", "HALL"))
        }
        assertTrue("not the card marked T" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    @Test
    fun `renaming carries the plan and the cards and the terminal`() {
        val renamed = home().renamedTo("MUM & DAD'S")
        assertEquals("MUM & DAD'S", renamed.name)
        assertEquals(3, renamed.markerCount)
        assertEquals("HALL", renamed.terminal)
        assertEquals(home().plan.floors, renamed.plan.floors)
    }

    // ---- The format -------------------------------------------------------------------------

    @Test
    fun `a list of homes reads back as what went in`() {
        val homes = listOf(home(), home("THE LAKE PLACE"))
        assertEquals(homes, SavedHomesText.read(SavedHomesText.write(homes)))
    }

    /** The id is the whole reason a card is a card. A round trip that lost it would look fine. */
    @Test
    fun `the printed ids survive the round trip`() {
        val back = SavedHomesText.read(SavedHomesText.write(listOf(home()))).single()
        assertEquals(
            listOf("CARD-01", "CARD-02", "CARD-03"),
            back.map.registrations.map { it.card.id.value },
        )
        assertEquals("CARD-0T", back.map.terminal?.card?.id?.value)
    }

    @Test
    fun `an empty list is still a file with a header`() {
        val text = SavedHomesText.write(emptyList())
        assertEquals(SavedHomesText.HEADER + "\n", text)
        assertEquals(emptyList(), SavedHomesText.read(text))
    }

    /**
     * The plan's own rows and the map's own rows, in the middle of this file, read by their own
     * readers.
     *
     * Written down as a test because it is the whole reason there is no second copy of either
     * grammar here: the row a host would see if they opened the file is the row the format that
     * owns it wrote.
     */
    @Test
    fun `a home's plan and cards are written in their own formats`() {
        val text = SavedHomesText.write(listOf(home()))
        assertTrue(HousePlanText.HEADER in text.lines(), text)
        assertTrue(HouseMapText.HEADER in text.lines(), text)
        assertTrue("R stairs|2,0,2,2|TOP OF STAIRS" in text.lines(), text)
        assertTrue("H THE BUNGALOW" in text.lines(), text)
        assertTrue("R ${payload("ring", "CARD-03")}|BED 1" in text.lines(), text)
        assertTrue("T ${payload("t_shape", "CARD-0T")}|HALL" in text.lines(), text)
    }

    @Test
    fun `the order of the homes is the order they were written`() {
        val homes = listOf(home("C"), home("A"), home("B"))
        assertEquals(listOf("C", "A", "B"), SavedHomesText.read(SavedHomesText.write(homes)).map { it.name })
    }

    /** Typed by a host on a phone, so it holds whatever they typed. */
    @Test
    fun `awkward names survive the round trip`() {
        val awkward = "MUM & DAD'S | THE \\ ONE\nUPSTAIRS 🕯"
        val homes = listOf(home().renamedTo(awkward))
        val back = SavedHomesText.read(SavedHomesText.write(homes))
        assertEquals(awkward, back.single().name)
        assertEquals(1, back.size, "the name forged a row")
    }

    /**
     * A room name holding the separator cannot forge a card row.
     *
     * The payload goes first for exactly this reason — it is nine characters of QR alphanumeric by
     * construction — so the split is unambiguous and everything after the first pipe is the room,
     * whatever is in it.
     */
    @Test
    fun `a room name holding a separator does not forge a card row`() {
        val awkward = "KITCHEN|ring|LIVING"
        val house = HousePlan.of(listOf(Floor("GROUND", listOf(painted(awkward, x = 0, y = 0)))))
        val homes = listOf(
            SavedHome(
                "H",
                house,
                HouseMap.of(
                    listOf(registered("ring", "CARD-01", awkward)),
                    registered("t_shape", "CARD-0T", awkward),
                ),
            )
        )
        val back = SavedHomesText.read(SavedHomesText.write(homes)).single()
        assertEquals(shapes("ring"), back.markersIn(awkward))
        assertEquals(awkward, back.terminal)
    }

    // ---- What the reader refuses -------------------------------------------------------------

    private fun refusal(text: String): MalformedSavedHomes =
        assertFailsWith { SavedHomesText.read(text) }

    private fun linesOf(vararg rows: String) = (listOf(SavedHomesText.HEADER) + rows).joinToString("\n")

    /** A home with one room, ready for a card row to be appended by a test. */
    private fun oneRoom(vararg rows: String) = linesOf(
        *(arrayOf("H X", HousePlanText.HEADER, "F GROUND", "R room|0,0,2,2|KITCHEN") + rows)
    )

    @Test
    fun `an empty file is refused`() {
        assertEquals(0, refusal("").line)
    }

    @Test
    fun `another format version is refused rather than guessed at`() {
        val other = refusal("someone-is-home/saved-homes/3\nH X\n")
        assertEquals(1, other.line)
        assertTrue("cannot be read under this one" in other.detail, other.detail)
    }

    /**
     * **Version 1 is named, not shrugged at.**
     *
     * A v1 file holds shapes with no printed ids. There is nothing honest to turn one into — an
     * invented id is a card the host does not have — so the refusal says what changed rather than
     * reporting an unexpected string.
     */
    @Test
    fun `homes written before cards had ids are refused by name`() {
        val old = refusal("someone-is-home/saved-homes/1\nH X\n")
        assertEquals(1, old.line)
        assertTrue("before markers carried the id" in old.detail, old.detail)
    }

    @Test
    fun `a home with no name is refused on its own line`() {
        assertEquals(2, refusal(linesOf("H ")).line)
    }

    @Test
    fun `two homes with one name are refused on the second one's line`() {
        val text = linesOf("H X", HousePlanText.HEADER, "F GROUND", HouseMapText.HEADER, "H X")
        val thrown = refusal(text)
        assertEquals(6, thrown.line)
        assertTrue("two homes called 'X'" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a home with no plan is refused`() {
        val thrown = refusal(linesOf("H X"))
        assertEquals(2, thrown.line)
        assertTrue("has no plan" in thrown.detail, thrown.detail)
    }

    /**
     * A home that writes no card list is a file that lost rows.
     *
     * A home with nothing registered still writes the header, so its absence is never the ordinary
     * case — it is a truncated file, and a truncated file that came back as a house with no cards
     * in it is fifteen minutes of walking nobody knows is missing.
     */
    @Test
    fun `a home with no card list at all is refused`() {
        val thrown = refusal(linesOf("H X", HousePlanText.HEADER, "F GROUND", "R room|0,0,2,2|KITCHEN"))
        assertEquals(2, thrown.line)
        assertTrue("has no card list" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a plan before any home is refused`() {
        assertTrue("before any home" in refusal(linesOf(HousePlanText.HEADER)).detail)
    }

    @Test
    fun `cards before any home are refused`() {
        assertTrue("before any home" in refusal(linesOf(HouseMapText.HEADER)).detail)
    }

    /**
     * **`R` means two different things and the header above it is the only thing that says which.**
     *
     * A row arriving before either header belongs to nothing, and guessing would file a painted
     * room as a registered card or the other way round.
     */
    @Test
    fun `a row before any section is refused`() {
        val thrown = refusal(linesOf("H X", "R room|0,0,2,2|KITCHEN"))
        assertEquals(3, thrown.line)
        assertTrue("belongs to no section" in thrown.detail, thrown.detail)
    }

    @Test
    fun `an unknown row inside the card list is refused rather than skipped`() {
        val thrown = refusal(oneRoom(HouseMapText.HEADER, "Z what"))
        assertEquals(7, thrown.line)
        assertTrue("unknown row" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a second plan in one home is refused`() {
        val thrown = refusal(oneRoom(HousePlanText.HEADER))
        assertEquals(6, thrown.line)
        assertTrue("two plans" in thrown.detail, thrown.detail)
    }

    /** Keeping one of two lists would drop cards the host really registered, silently. */
    @Test
    fun `a second card list in one home is refused`() {
        val thrown = refusal(oneRoom(HouseMapText.HEADER, HouseMapText.HEADER))
        assertEquals(7, thrown.line)
        assertTrue("twice" in thrown.detail, thrown.detail)
    }

    /**
     * **A refusal from inside a plan names the line it really occupies here.**
     *
     * `HousePlanText` counts from the top of what it is handed, which is the middle of this file.
     * A host sent to line 2 for a row that is on line 5 goes looking at the wrong house.
     */
    @Test
    fun `a bad plan row is refused against its line in this file`() {
        val thrown = refusal(
            linesOf("H X", HousePlanText.HEADER, "F GROUND", "R room|nonsense|KITCHEN")
        )
        assertEquals(5, thrown.line)
        assertTrue("not a painted stroke" in thrown.detail, thrown.detail)
    }

    /** The same, for the card list. Its reader counts from its own header too. */
    @Test
    fun `a bad card row is refused against its line in this file`() {
        val thrown = refusal(oneRoom(HouseMapText.HEADER, "R nonsense|KITCHEN"))
        assertEquals(7, thrown.line)
        assertTrue("rejected" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a card row with no room is refused`() {
        val thrown = refusal(oneRoom(HouseMapText.HEADER, "R " + payload("ring", "CARD-01")))
        assertTrue("no room on this row" in thrown.detail, thrown.detail)
    }

    @Test
    fun `cards in a room the plan does not have are refused`() {
        val thrown = refusal(
            oneRoom(HouseMapText.HEADER, "R ${payload("ring", "CARD-01")}|CELLAR")
        )
        assertEquals(7, thrown.line)
        assertTrue("not a room in 'X'" in thrown.detail, thrown.detail)
    }

    @Test
    fun `cards in stairs are refused by the reader too`() {
        val thrown = refusal(
            linesOf(
                "H X", HousePlanText.HEADER, "F GROUND", "R stairs|0,0,2,2|TOP",
                HouseMapText.HEADER, "R ${payload("ring", "CARD-01")}|TOP",
            )
        )
        assertTrue("stairs hold nothing" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a terminal in a room the plan does not have is refused`() {
        val thrown = refusal(
            oneRoom(HouseMapText.HEADER, "T ${payload("t_shape", "CARD-0T")}|CELLAR")
        )
        assertTrue("not a room in 'X'" in thrown.detail, thrown.detail)
    }

    /** One home, one terminal. A second gives the house two places to be found. */
    @Test
    fun `two terminals in one home are refused`() {
        val thrown = refusal(
            linesOf(
                "H X", HousePlanText.HEADER, "F GROUND",
                "R room|0,0,2,2|KITCHEN", "R room|2,0,2,2|HALL",
                HouseMapText.HEADER,
                "T ${payload("t_shape", "CARD-0T")}|KITCHEN",
                "T ${payload("t_shape", "CARD-1T")}|HALL",
            )
        )
        assertEquals(9, thrown.line)
        assertTrue("a second terminal" in thrown.detail, thrown.detail)
    }

    /** Keyed on the id, so the same card twice is a file that disagrees with itself. */
    @Test
    fun `one card id appearing twice is refused`() {
        val row = "R ${payload("ring", "CARD-01")}|KITCHEN"
        val thrown = refusal(oneRoom(HouseMapText.HEADER, row, row))
        assertEquals(8, thrown.line)
        assertTrue("appears twice" in thrown.detail, thrown.detail)
    }

    /**
     * **The terminal's card counts as a card.**
     *
     * Found by injection: the duplicate check looked only at the ordinary rows, so a file naming
     * one printed id as both a marker and the terminal read back clean. The house would then have
     * two places that card means, which is D-069's hazard arriving through the file rather than
     * through a reprint.
     */
    @Test
    fun `a card id used by a marker and by the terminal is refused`() {
        val id = "CARD-0T"
        val thrown = refusal(
            oneRoom(
                HouseMapText.HEADER,
                "T ${payload("t_shape", id)}|KITCHEN",
                "R ${payload("ring", id)}|KITCHEN",
            )
        )
        assertEquals(8, thrown.line)
        assertTrue("appears twice" in thrown.detail, thrown.detail)
    }

    /** The T card in an ordinary row, and an ordinary card in the terminal row. Both refused. */
    @Test
    fun `the card marked T is refused in an ordinary row and the other way round`() {
        val asMarker = refusal(oneRoom(HouseMapText.HEADER, "R ${payload("t_shape", "CARD-0T")}|KITCHEN"))
        assertTrue("never is" in asMarker.detail, asMarker.detail)

        val asTerminal = refusal(oneRoom(HouseMapText.HEADER, "T ${payload("ring", "CARD-01")}|KITCHEN"))
        assertTrue("not the card marked T" in asTerminal.detail, asTerminal.detail)
    }

    @Test
    fun `a dangling escape in a name is refused`() {
        assertTrue("dangling escape" in refusal(linesOf("H X\\")).detail)
    }

    @Test
    fun `an unknown escape in a name is refused`() {
        assertTrue("unknown escape" in refusal(linesOf("H X\\q")).detail)
    }

    /** The one property that is not a guard: nothing here can be disabled to break it. */
    @Test
    fun `a home with no cards at all still reads back`() {
        val bare = SavedHome("BARE", plan())
        val back = SavedHomesText.read(SavedHomesText.write(listOf(bare))).single()
        assertEquals(emptyList(), back.map.registrations)
        assertNull(back.terminal)
        assertEquals(4, back.roomCount)
    }
}
