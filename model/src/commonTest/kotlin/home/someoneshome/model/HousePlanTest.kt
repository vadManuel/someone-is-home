package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun painted(
    name: String,
    kind: RoomKind = RoomKind.Room,
    vararg strokes: CellRect,
) = PlanRoom(Room(name, kind), strokes.toList())

/**
 * The house the whole file paints, drawn once so the shapes are readable:
 *
 * ```
 * GROUND            UPSTAIRS
 *   x 01234           x 01234
 * y0  KKKHH         y0  BB
 * y1  KKKHH         y1  BB
 * y2  K  HH
 * y3  KPfHH
 * y4     S
 * ```
 *
 * KITCHEN is L-shaped, PANTRY touches only the foot of the L, and STAIRWELL hangs off the HALL.
 */
private fun house(): HousePlan {
    var plan = HousePlan.EMPTY.withFloor("GROUND").withFloor("UPSTAIRS")
    for ((floor, room) in listOf(
        "GROUND" to painted("KITCHEN", RoomKind.Room, CellRect(0, 0, 3, 2), CellRect(0, 2, 1, 2)),
        "GROUND" to painted("HALL", RoomKind.Room, CellRect(3, 0, 2, 4)),
        "GROUND" to painted("PANTRY", RoomKind.Room, CellRect(1, 3, 1, 1)),
        "GROUND" to painted("STAIRWELL", RoomKind.Stairs, CellRect(3, 4, 1, 1)),
        "UPSTAIRS" to painted("BED 1", RoomKind.Room, CellRect(0, 0, 2, 2)),
    )) {
        plan = (plan.paint(floor, room) as PaintResult.Painted).plan
    }
    return plan
}

/** Stories 4.1–4.3. The house the host drew, which is the board. */
class HousePlanTest {

    @Test
    fun `a setup walk paints every room onto its floor in order`() {
        val plan = house()
        assertEquals(listOf("GROUND", "UPSTAIRS"), plan.floors.map { it.name })
        assertEquals(
            listOf("KITCHEN", "HALL", "PANTRY", "STAIRWELL"),
            plan.floorNamed("GROUND")!!.rooms.map { it.name },
        )
        assertEquals(listOf("BED 1"), plan.floorNamed("UPSTAIRS")!!.rooms.map { it.name })
        assertEquals(RoomKind.Stairs, plan.roomNamed("STAIRWELL")!!.kind)
    }

    /**
     * **E4's acceptance criterion, and the grid's whole reason for existing.** Rectangles cannot
     * express an L, and real houses are full of them.
     */
    @Test
    fun `an L-shaped room is expressible`() {
        val kitchen = house().roomNamed("KITCHEN")!!
        assertEquals(
            listOf(
                Cell(0, 0), Cell(1, 0), Cell(2, 0),
                Cell(0, 1), Cell(1, 1), Cell(2, 1),
                Cell(0, 2), Cell(0, 3),
            ),
            kitchen.cells,
        )
        assertTrue(kitchen.covers(Cell(0, 3)), "the foot of the L is not in the room")
        assertFalse(kitchen.covers(Cell(1, 3)), "the L filled in its own notch")
    }

    @Test
    fun `a floor says which room holds a cell`() {
        val ground = house().floorNamed("GROUND")!!
        assertEquals("KITCHEN", ground.roomAt(Cell(2, 1))?.name)
        assertEquals("PANTRY", ground.roomAt(Cell(1, 3))?.name)
        assertNull(ground.roomAt(Cell(2, 3)), "the notch of the L belongs to nobody")
    }

    /** A host correcting a drag they got wrong repaints rather than duplicating. */
    @Test
    fun `repainting a room replaces its cells`() {
        val plan = house()
        val result = plan.paint("GROUND", painted("PANTRY", RoomKind.Room, CellRect(1, 3, 1, 2)))
        assertIs<PaintResult.Painted>(result)
        assertEquals(4, result.plan.floorNamed("GROUND")!!.rooms.size, "repainting duplicated it")
        assertEquals(listOf(Cell(1, 3), Cell(1, 4)), result.plan.roomNamed("PANTRY")!!.cells)
    }

    @Test
    fun `a stroke landing on another room's cells is refused and names them`() {
        val result = house().paint("GROUND", painted("UTILITY", RoomKind.Room, CellRect(2, 1, 2, 1)))
        assertIs<PaintResult.CellsAlreadyPainted>(result)
        assertEquals("KITCHEN", result.by.name)
        assertEquals(listOf(Cell(2, 1)), result.cells)
    }

    /**
     * A card is registered to a `Room`, which is a name and a kind — so one name in two places is
     * a registration that cannot say which place it means.
     */
    @Test
    fun `a room name already used on another floor is refused`() {
        val result = house().paint("UPSTAIRS", painted("HALL", RoomKind.Room, CellRect(4, 4, 1, 1)))
        assertIs<PaintResult.NameAlreadyUsed>(result)
        assertEquals("HALL", result.by.name)
        assertEquals("GROUND", house().floorOf(result.by)?.name)
    }

    @Test
    fun `painting onto a storey nobody added is refused`() {
        val result = house().paint("ATTIC", painted("LOFT", RoomKind.Room, CellRect(0, 0, 1, 1)))
        assertIs<PaintResult.NoSuchFloor>(result)
        assertEquals("ATTIC", result.name)
    }

    @Test
    fun `a second floor with the same name is refused`() {
        assertFailsWith<IllegalArgumentException> { house().withFloor("GROUND") }
    }

    @Test
    fun `forgetting a room leaves the rest of the house alone`() {
        val plan = house().forget("PANTRY")
        assertNull(plan.roomNamed("PANTRY"))
        assertEquals(3, plan.floorNamed("GROUND")!!.rooms.size)
        assertEquals(1, plan.floorNamed("UPSTAIRS")!!.rooms.size)
    }

    // Floors are additive and nothing is numbered, so an empty house is a legitimate house.
    @Test
    fun `an empty plan is an empty house rather than a broken one`() {
        assertEquals(emptyList(), HousePlan.EMPTY.floors)
        assertEquals(emptyList(), HousePlan.EMPTY.rooms)
    }
}

/**
 * **Adjacency falls out of cell neighbours** — E4.9, and the first of the three counts on which
 * the design chose a grid over free rectangles. No geometry, anywhere.
 */
class PlanAdjacencyTest {

    @Test
    fun `neighbours are the rooms sharing an edge`() {
        val ground = house().floorNamed("GROUND")!!
        assertEquals(
            listOf("HALL", "PANTRY"),
            ground.neighboursOf(ground.roomNamed("KITCHEN")!!).map { it.name },
        )
        assertEquals(
            listOf("KITCHEN", "STAIRWELL"),
            ground.neighboursOf(ground.roomNamed("HALL")!!).map { it.name },
        )
    }

    /** PANTRY touches nothing but the foot of the L, which is the shape a rectangle cannot hold. */
    @Test
    fun `a room reached only along the leg of an L is still a neighbour`() {
        val ground = house().floorNamed("GROUND")!!
        assertEquals(
            listOf("KITCHEN"),
            ground.neighboursOf(ground.roomNamed("PANTRY")!!).map { it.name },
        )
    }

    /**
     * Two rooms meeting at one corner share no doorway. An eight-neighbourhood would report a
     * route through a wall, and error injection would place a player somewhere they could not
     * have walked to.
     */
    @Test
    fun `rooms touching only at a corner are not neighbours`() {
        val floor = Floor(
            "GROUND",
            listOf(
                painted("STUDY", RoomKind.Room, CellRect(0, 0, 1, 1)),
                painted("PORCH", RoomKind.Room, CellRect(1, 1, 1, 1)),
            ),
        )
        assertEquals(emptyList(), floor.neighboursOf(floor.roomNamed("STUDY")!!))
    }

    /**
     * **No vertical-connection logic** — the app renders what was drawn. BED 1 sits on the same
     * coordinates as part of KITCHEN and is not its neighbour, nor is the STAIRWELL wired to
     * anything above it.
     */
    @Test
    fun `adjacency never crosses a floor`() {
        val plan = house()
        val upstairs = plan.floorNamed("UPSTAIRS")!!
        assertEquals(emptyList(), upstairs.neighboursOf(upstairs.roomNamed("BED 1")!!))

        val ground = plan.floorNamed("GROUND")!!
        assertTrue(
            ground.neighboursOf(ground.roomNamed("STAIRWELL")!!).none { it.name == "BED 1" },
            "the stairwell was wired to the storey above it",
        )
    }
}

/**
 * The last-ditch guarantees behind the polite refusals: every other route into a plan — storage,
 * an importer, a hand-built list — arrives through these.
 */
class HousePlanConstructionTest {

    @Test
    fun `a stroke covering no cells cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { CellRect(0, 0, 0, 3) }
        assertFailsWith<IllegalArgumentException> { CellRect(0, 0, 3, -1) }
    }

    @Test
    fun `a stroke past the grid limit cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { CellRect(0, 0, HousePlan.LIMIT + 1, 1) }
        assertFailsWith<IllegalArgumentException> { CellRect(HousePlan.LIMIT + 1, 0, 1, 1) }
    }

    @Test
    fun `a room with no cells cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { PlanRoom(Room("KITCHEN"), emptyList()) }
    }

    @Test
    fun `a nameless room or floor cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> {
            PlanRoom(Room(""), listOf(CellRect(0, 0, 1, 1)))
        }
        assertFailsWith<IllegalArgumentException> { Floor("") }
    }

    @Test
    fun `two floors with one name cannot be assembled`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HousePlan.of(listOf(Floor("GROUND"), Floor("GROUND")))
        }
        assertTrue(failure.message!!.contains("two floors called 'GROUND'"), failure.message!!)
    }

    @Test
    fun `two rooms with one name cannot be assembled`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HousePlan.of(
                listOf(
                    Floor("GROUND", listOf(painted("HALL", RoomKind.Room, CellRect(0, 0, 1, 1)))),
                    Floor("UPSTAIRS", listOf(painted("HALL", RoomKind.Room, CellRect(0, 0, 1, 1)))),
                )
            )
        }
        assertTrue(failure.message!!.contains("two rooms called 'HALL'"), failure.message!!)
    }

    @Test
    fun `two rooms holding one cell cannot be assembled`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HousePlan.of(
                listOf(
                    Floor(
                        "GROUND",
                        listOf(
                            painted("HALL", RoomKind.Room, CellRect(0, 0, 2, 2)),
                            painted("STUDY", RoomKind.Room, CellRect(1, 1, 2, 2)),
                        ),
                    )
                )
            )
        }
        assertTrue(failure.message!!.contains("both hold the cell (1, 1)"), failure.message!!)
    }

    /** The same house rebuilt from its own parts is the same house. */
    @Test
    fun `a well-formed plan assembles`() {
        assertEquals(5, HousePlan.of(house().floors).rooms.size)
    }
}

/** The plan's storage format. Strict, loud, versioned — [HouseMapText]'s doctrine, applied. */
class HousePlanTextTest {

    @Test
    fun `a painted house round-trips byte for byte`() {
        val text = HousePlanText.write(house())
        assertEquals(text, HousePlanText.write(HousePlanText.read(text)))
        assertEquals(
            HousePlanText.HEADER + "\n" +
                "F GROUND\n" +
                "R room|0,0,3,2;0,2,1,2|KITCHEN\n" +
                "R room|3,0,2,4|HALL\n" +
                "R room|1,3,1,1|PANTRY\n" +
                "R stairs|3,4,1,1|STAIRWELL\n" +
                "F UPSTAIRS\n" +
                "R room|0,0,2,2|BED 1\n",
            text,
        )
    }

    @Test
    fun `a read plan is the plan that was written`() {
        val plan = HousePlanText.read(HousePlanText.write(house()))
        assertEquals(listOf("GROUND", "UPSTAIRS"), plan.floors.map { it.name })
        assertEquals(house().roomNamed("KITCHEN")!!.cells, plan.roomNamed("KITCHEN")!!.cells)
        assertEquals(RoomKind.Stairs, plan.roomNamed("STAIRWELL")!!.kind)
    }

    /** A grid that grew leftwards and upwards from where the host started painting. */
    @Test
    fun `negative coordinates survive`() {
        val plan = HousePlan.of(
            listOf(Floor("GROUND", listOf(painted("PORCH", RoomKind.Room, CellRect(-4, -2, 2, 1)))))
        )
        val back = HousePlanText.read(HousePlanText.write(plan))
        assertEquals(listOf(Cell(-4, -2), Cell(-3, -2)), back.roomNamed("PORCH")!!.cells)
    }

    /**
     * Names are typed by a host on a phone. The separator especially must not survive, or a room
     * called `KITCHEN|room|0,0,1,1` forges a row.
     */
    @Test
    fun `an awkward name survives without forging a row`() {
        val awkward = "SALLE À MANGER 🕯|room|9,9,1,1\nBACK\\"
        val plan = HousePlan.of(
            listOf(Floor("ÉTAGE\\|1", listOf(painted(awkward, RoomKind.Room, CellRect(0, 0, 1, 1)))))
        )
        val back = HousePlanText.read(HousePlanText.write(plan))
        assertEquals(listOf("ÉTAGE\\|1"), back.floors.map { it.name })
        assertEquals(awkward, back.floors[0].rooms[0].name)
        assertEquals(1, back.rooms.size, "an escaped name forged a second room")
    }

    /** A host who has added a storey and painted nothing on it yet has a real plan. */
    @Test
    fun `a header alone reads as a house with nothing painted`() {
        assertEquals(emptyList(), HousePlanText.read(HousePlanText.HEADER + "\n").floors)
        assertEquals(
            emptyList(),
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\n").floors[0].rooms,
        )
    }

    @Test
    fun `an empty file is refused rather than read as an empty house`() {
        assertFailsWith<MalformedHousePlan> { HousePlanText.read("") }
    }

    @Test
    fun `a plan written under another format version is refused`() {
        val text = HousePlanText.write(house()).replaceFirst("/1", "/2")
        val failure = assertFailsWith<MalformedHousePlan> { HousePlanText.read(text) }
        assertEquals(1, failure.line)
        assertTrue(failure.message!!.contains("expected header"), failure.message!!)
    }

    @Test
    fun `an unknown row is refused rather than skipped`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.write(house()) + "X junk\n")
        }
        assertEquals(9, failure.line, "two floor rows and five rooms after a header, then the junk")
        assertTrue(failure.message!!.contains("Refusing rather than skipping"), failure.message!!)
    }

    @Test
    fun `a room before any floor is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nR room|0,0,1,1|KITCHEN\n")
        }
        assertTrue(failure.message!!.contains("a room before any floor"), failure.message!!)
    }

    @Test
    fun `a room row with the wrong number of fields is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room|0,0,1,1\n")
        }
        assertTrue(failure.message!!.contains("kind|cells|name"), failure.message!!)
    }

    /**
     * The map knows room and stairs and nothing else (D-098). A file offering a third kind is a
     * file written against a house this one does not have.
     */
    @Test
    fun `an unknown room kind is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR corridor|0,0,1,1|KITCHEN\n")
        }
        assertTrue(failure.message!!.contains("unknown room kind 'corridor'"), failure.message!!)
    }

    @Test
    fun `a stroke that is not four numbers is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room|0,0,1|KITCHEN\n")
        }
        assertTrue(failure.message!!.contains("four numbers are needed"), failure.message!!)
    }

    @Test
    fun `a stroke with something that is not a number is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room|0,0,1,x|KITCHEN\n")
        }
        assertTrue(failure.message!!.contains("'x' in '0,0,1,x'"), failure.message!!)
    }

    @Test
    fun `a stroke covering no cells is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room|0,0,1,0|KITCHEN\n")
        }
        assertTrue(failure.message!!.contains("covers no cells"), failure.message!!)
    }

    /** A stroke two million cells wide is a corrupted file, and expanding it is how a phone dies. */
    @Test
    fun `a stroke past the grid limit is refused before it becomes cells`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room|0,0,2000000,1|KITCHEN\n")
        }
        assertTrue(failure.message!!.contains("past the ${HousePlan.LIMIT}-cell limit"), failure.message!!)
    }

    @Test
    fun `a room with no cells at all is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room||KITCHEN\n")
        }
        assertTrue(failure.message!!.contains("a room with no cells"), failure.message!!)
    }

    @Test
    fun `a nameless room is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room|0,0,1,1|\n")
        }
        assertTrue(failure.message!!.contains("a room with no name"), failure.message!!)
    }

    /**
     * **The message is asserted, not just the refusal.** A nameless floor was once refused as
     * *two floors called ''* — the empty name collided with the sentinel the reader used for "no
     * storey open yet", so the duplicate check answered first. The right answer for the wrong
     * reason, and the reason is the half a host reads.
     */
    @Test
    fun `a nameless floor is refused and says so`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF \n")
        }
        assertEquals(2, failure.line)
        assertTrue(failure.message!!.contains("a floor with no name"), failure.message!!)
    }

    @Test
    fun `two floors with one name are refused on the second one's line`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nF GROUND\n")
        }
        assertEquals(3, failure.line)
        assertTrue(failure.message!!.contains("two floors called 'GROUND'"), failure.message!!)
    }

    @Test
    fun `two rooms with one name are refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(
                HousePlanText.HEADER + "\nF GROUND\nR room|0,0,1,1|HALL\n" +
                    "F UPSTAIRS\nR room|4,4,1,1|HALL\n"
            )
        }
        assertEquals(5, failure.line)
        assertTrue(failure.message!!.contains("two rooms called 'HALL'"), failure.message!!)
    }

    @Test
    fun `two rooms holding one cell are refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(
                HousePlanText.HEADER + "\nF GROUND\nR room|0,0,2,2|HALL\nR room|1,1,2,2|STUDY\n"
            )
        }
        assertEquals(4, failure.line)
        assertTrue(failure.message!!.contains("both hold the cell (1, 1)"), failure.message!!)
    }

    @Test
    fun `a dangling escape is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROUND\nR room|0,0,1,1|KITCHEN\\\n")
        }
        assertTrue(failure.message!!.contains("dangling escape"), failure.message!!)
    }

    @Test
    fun `an unknown escape is refused`() {
        val failure = assertFailsWith<MalformedHousePlan> {
            HousePlanText.read(HousePlanText.HEADER + "\nF GROU\\qND\n")
        }
        assertTrue(failure.message!!.contains("unknown escape"), failure.message!!)
    }

    /** A house larger than any real one still round-trips. */
    @Test
    fun `a large plan survives`() {
        var plan = HousePlan.EMPTY
        for (floor in 0 until 4) {
            plan = plan.withFloor("FLOOR $floor")
            for (room in 0 until 20) {
                val painted = painted("ROOM $floor-$room", RoomKind.Room, CellRect(room, floor, 1, 1))
                plan = (plan.paint("FLOOR $floor", painted) as PaintResult.Painted).plan
            }
        }
        val text = HousePlanText.write(plan)
        assertEquals(80, HousePlanText.read(text).rooms.size)
        assertEquals(text, HousePlanText.write(HousePlanText.read(text)))
    }
}
