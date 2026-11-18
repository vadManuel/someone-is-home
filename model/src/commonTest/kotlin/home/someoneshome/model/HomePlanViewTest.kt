package home.someoneshome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * **O2: the plan, narrowed for the round.** What survives the narrowing (rooms whole, stairs as
 * stairs, adjacency as the painted plan has it) and what cannot be expressed in it is checked on
 * the JVM by `HomePlanViewFieldsTest`, which reads the field lists.
 */
class HomePlanViewTest {

    private fun HousePlan.with(floor: String, room: Room, vararg strokes: CellRect): HousePlan =
        (paint(floor, PlanRoom(room, strokes.toList())) as PaintResult.Painted).plan

    /** An L-shaped kitchen, a hall beside it, stairs in the crook, and a study upstairs over them. */
    private val plan: HousePlan = HousePlan.EMPTY.withFloor("GROUND").withFloor("UPPER")
        .with("GROUND", Room("KITCHEN"), CellRect(-2, 5, 3, 1), CellRect(-2, 6, 1, 2))
        .with("GROUND", Room("HALL"), CellRect(1, 5, 2, 3))
        .with("GROUND", Room("STAIRS", RoomKind.Stairs), CellRect(-1, 6, 2, 2))
        .with("UPPER", Room("STUDY"), CellRect(0, 0, 2, 2))

    /** **GUARD: an L-shaped room survives as one room, every one of its squares in it.** */
    @Test
    fun `an L-shaped room is one room`() {
        val view = plan.toView()
        val ground = view.storeys.first()
        assertEquals("GROUND", ground.name)
        assertEquals(5 to 3, ground.cols to ground.rows, "the box is the storey's painted extent")
        val kitchen = view.indexOf("KITCHEN")
        // Row-major, cropped to the box: the L's top bar is row 0, its leg is column 0 below it.
        assertEquals(listOf(0, 1, 2, 5, 10), ground.cells.indices.filter { ground.cells[it] == kitchen })
        assertEquals(plan.roomNamed("KITCHEN")!!.cells.size, ground.cells.count { it == kitchen })
        assertEquals(4, view.rooms.size, "the L came out as more than one room")
    }

    /** **GUARD: stairs stay stairs, and a storey's rooms are on that storey alone.** */
    @Test
    fun `stairs stay stairs and storeys stay apart`() {
        val view = plan.toView()
        assertEquals(RoomKind.Stairs, view.rooms[view.indexOf("STAIRS")].kind)
        assertEquals(RoomKind.Room, view.rooms[view.indexOf("HALL")].kind)
        val upper = view.storeys[1]
        assertEquals(listOf(view.indexOf("STUDY")), upper.cells.distinct())
        assertEquals(emptyList(), view.adjacency[view.indexOf("STUDY")], "a room touched another storey")
    }

    /** **GUARD: adjacency is the plan's own, `Floor.neighboursOf`, read off the cells.** */
    @Test
    fun `adjacency agrees with the painted plan`() {
        val view = plan.toView()
        for (floor in plan.floors) {
            for (room in floor.rooms) {
                assertEquals(
                    floor.neighboursOf(room).map { it.name }.sorted(),
                    view.adjacency[view.indexOf(room.name)].map { view.rooms[it].name }.sorted(),
                    "${room.name}'s neighbours",
                )
            }
        }
        assertEquals(true, view.adjacent(view.indexOf("KITCHEN"), view.indexOf("HALL")))
        assertEquals(false, view.adjacent(view.indexOf("KITCHEN"), view.indexOf("STUDY")))
    }

    /** A storey nothing was painted on is kept, empty; a cell naming a room the plan lacks is refused. */
    @Test
    fun `an empty storey is kept and a broken view is refused`() {
        val view = HousePlan.EMPTY.withFloor("GROUND").toView()
        assertEquals(listOf(StoreyView("GROUND", 0, 0, emptyList())), view.storeys)
        assertFailsWith<IllegalArgumentException> { HomePlanView(listOf(StoreyView("G", 1, 1, listOf(3))), emptyList()) }
        assertFailsWith<IllegalArgumentException> { StoreyView("G", 2, 2, listOf(StoreyView.UNPAINTED)) }
    }
}
