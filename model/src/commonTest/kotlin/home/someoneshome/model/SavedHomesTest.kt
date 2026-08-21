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

private fun home(name: String = "THE BUNGALOW") = SavedHome(
    name = name,
    plan = plan(),
    markers = mapOf("KITCHEN" to shapes("triangle_up", "square"), "BED 1" to shapes("ring")),
    terminal = "HALL",
)

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
        assertEquals(emptyList(), bungalow.markersIn("HALL"))
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
            SavedHome("H", plan(), markers = mapOf("CELLAR" to shapes("ring")))
        }
        assertTrue("'CELLAR'" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    /** D-099, at the storage boundary: stairs hold nothing, and a file cannot say otherwise. */
    @Test
    fun `cards in stairs are refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            SavedHome("H", plan(), markers = mapOf("TOP OF STAIRS" to shapes("ring")))
        }
        assertTrue("stairs hold nothing" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    @Test
    fun `a terminal in stairs or in no room is refused`() {
        assertFailsWith<IllegalArgumentException> { SavedHome("H", plan(), terminal = "TOP OF STAIRS") }
        assertFailsWith<IllegalArgumentException> { SavedHome("H", plan(), terminal = "CELLAR") }
    }

    /** A room holding nothing and a room with no entry are the same fact, so they compare equal. */
    @Test
    fun `an empty card list is the same home as no entry at all`() {
        val withEmpty = SavedHome("H", plan(), markers = mapOf("KITCHEN" to emptyList()))
        assertEquals(SavedHome("H", plan()), withEmpty)
        assertEquals(emptyMap(), withEmpty.markers)
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

    @Test
    fun `an empty list is still a file with a header`() {
        val text = SavedHomesText.write(emptyList())
        assertEquals(SavedHomesText.HEADER + "\n", text)
        assertEquals(emptyList(), SavedHomesText.read(text))
    }

    /**
     * The plan's own rows, in the middle of this file, read by the plan's own reader.
     *
     * Written down as a test because it is the whole reason there is no second copy of the plan
     * grammar here: the row a host would see if they opened the file is the row `HousePlanText`
     * wrote.
     */
    @Test
    fun `a home's plan is written in the plan's own format`() {
        val text = SavedHomesText.write(listOf(home()))
        assertTrue(HousePlanText.HEADER in text.lines(), text)
        assertTrue("R stairs|2,0,2,2|TOP OF STAIRS" in text.lines(), text)
        assertTrue("H THE BUNGALOW" in text.lines(), text)
        assertTrue("T HALL" in text.lines(), text)
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
     * The shapes go first for exactly this reason — they are lowercase words by construction, so
     * the split is unambiguous and everything after the first pipe is the room, whatever is in it.
     */
    @Test
    fun `a room name holding a separator does not forge a card row`() {
        val awkward = "KITCHEN|ring|LIVING"
        val house = HousePlan.of(listOf(Floor("GROUND", listOf(painted(awkward, x = 0, y = 0)))))
        val homes = listOf(SavedHome("H", house, mapOf(awkward to shapes("ring")), terminal = awkward))
        val back = SavedHomesText.read(SavedHomesText.write(homes)).single()
        assertEquals(shapes("ring"), back.markersIn(awkward))
        assertEquals(awkward, back.terminal)
    }

    // ---- What the reader refuses -------------------------------------------------------------

    private fun refusal(text: String): MalformedSavedHomes =
        assertFailsWith { SavedHomesText.read(text) }

    private fun linesOf(vararg rows: String) = (listOf(SavedHomesText.HEADER) + rows).joinToString("\n")

    @Test
    fun `an empty file is refused`() {
        assertEquals(0, refusal("").line)
    }

    @Test
    fun `another format version is refused rather than guessed at`() {
        val other = refusal("someone-is-home/saved-homes/2\nH X\n")
        assertEquals(1, other.line)
        assertTrue("cannot be read under this one" in other.detail, other.detail)
    }

    @Test
    fun `a home with no name is refused on its own line`() {
        assertEquals(2, refusal(linesOf("H ")).line)
    }

    @Test
    fun `two homes with one name are refused on the second one's line`() {
        val text = linesOf("H X", HousePlanText.HEADER, "F GROUND", "H X")
        val thrown = refusal(text)
        assertEquals(5, thrown.line)
        assertTrue("two homes called 'X'" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a home with no plan is refused`() {
        val thrown = refusal(linesOf("H X"))
        assertEquals(2, thrown.line)
        assertTrue("has no plan" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a plan before any home is refused`() {
        assertTrue("before any home" in refusal(linesOf("F GROUND")).detail)
    }

    @Test
    fun `cards before any home are refused`() {
        assertTrue("before any home" in refusal(linesOf("M ring|KITCHEN")).detail)
    }

    @Test
    fun `an unknown row is refused rather than skipped`() {
        val thrown = refusal(linesOf("H X", HousePlanText.HEADER, "F GROUND", "Z what"))
        assertEquals(5, thrown.line)
        assertTrue("unknown row" in thrown.detail, thrown.detail)
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

    @Test
    fun `a shape the roster does not carry is refused`() {
        val thrown = refusal(
            linesOf(
                "H X", HousePlanText.HEADER, "F GROUND", "R room|0,0,2,2|KITCHEN",
                "M octagon|KITCHEN",
            )
        )
        assertEquals(6, thrown.line)
        assertTrue("'octagon' is not a marker shape" in thrown.detail, thrown.detail)
    }

    @Test
    fun `cards in a room the plan does not have are refused`() {
        val thrown = refusal(
            linesOf(
                "H X", HousePlanText.HEADER, "F GROUND", "R room|0,0,2,2|KITCHEN",
                "M ring|CELLAR",
            )
        )
        assertEquals(6, thrown.line)
        assertTrue("not a room in 'X'" in thrown.detail, thrown.detail)
    }

    @Test
    fun `cards in stairs are refused by the reader too`() {
        val thrown = refusal(
            linesOf(
                "H X", HousePlanText.HEADER, "F GROUND", "R stairs|0,0,2,2|TOP",
                "M ring|TOP",
            )
        )
        assertTrue("stairs hold nothing" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a terminal in a room the plan does not have is refused`() {
        val thrown = refusal(
            linesOf("H X", HousePlanText.HEADER, "F GROUND", "R room|0,0,2,2|KITCHEN", "T CELLAR")
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
                "T KITCHEN", "T HALL",
            )
        )
        assertEquals(8, thrown.line)
        assertTrue("has two terminals" in thrown.detail, thrown.detail)
    }

    /** Keeping one of two rows would drop cards the host really registered, silently. */
    @Test
    fun `two card rows for one room are refused`() {
        val thrown = refusal(
            linesOf(
                "H X", HousePlanText.HEADER, "F GROUND", "R room|0,0,2,2|KITCHEN",
                "M ring|KITCHEN", "M square|KITCHEN",
            )
        )
        assertEquals(7, thrown.line)
        assertTrue("twice" in thrown.detail, thrown.detail)
    }

    @Test
    fun `a card row with no room is refused`() {
        val thrown = refusal(
            linesOf("H X", HousePlanText.HEADER, "F GROUND", "R room|0,0,2,2|KITCHEN", "M ring")
        )
        assertTrue("no room on this row" in thrown.detail, thrown.detail)
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
        assertEquals(emptyMap(), back.markers)
        assertNull(back.terminal)
        assertEquals(4, back.roomCount)
    }
}
