package home.someoneshome.app

import home.someoneshome.model.CellRect
import home.someoneshome.model.Floor
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Registration
import home.someoneshome.model.Room
import home.someoneshome.model.SavedHome

/**
 * **The fifteen-minute walk, which a Mac has no lens for — written onto a phone's own disk.**
 *
 * A host's home is fifteen minutes of walking a real house in the light, painting rooms on a grid
 * and scanning a printed card in each. Five simulator seats have no camera and no person, so a
 * rehearsal that needed a stored home before it could host had exactly two options: fake the store,
 * or write a real home into the real one. This is the second. [LobbyCheat] hands it to
 * `SavedHomesModel.save`, which is the call SAVE HOME makes, which writes through
 * `SavedHomesDocument` to the file the app reads back at boot. **Nothing about the store is stood
 * in for** — what is stood in for is the camera.
 *
 * ### ⚠️ It is deliberately NOT [BenchHome], and that difference is the instrument
 *
 * The round used to be armed in `BenchHome` no matter which home was hosted, and every rehearsal
 * this project has ever run was green over that (E-PAY-3). A rehearsal that seeded `BenchHome` and
 * then checked the work orders would be asking a question whose two answers are identical: HALL and
 * `BENCH-A` on an order line would mean *the hosted home reached the round* and *the hosted home
 * was thrown away and the fixture used* equally well.
 *
 * So this home shares nothing with the bench's. Different name, different rooms, different card
 * ids — `SEEDS-A` where the fixture has `BENCH-A`. **A `BENCH-*` id or a room called HALL on a work
 * order in a seeded run is the defect, by name, in the log.**
 *
 * ### The shape is the bench's, and for the bench's reasons
 *
 * Ten ordinary markers (clears D-127's floor of eight), two to a room (so one order can name one
 * room twice, which is what a screen most easily draws wrong), and five rooms **in a row** so the
 * adjacency graph is a path — `egressFor` picks the two markers whose rooms are farthest apart, and
 * a home where everything touches everything would let a pair be chosen that two people could
 * contain without walking. Both reserved cards are placed, because a home with no Terminal or no
 * meeting card is one D-127 refuses to host.
 */
object SeededHome {

    /**
     * Five rooms in a row, one cell each. Ordinary house words, and **none of the bench's five**.
     *
     * One cell per room is not a claim about what a real home looks like: the grid painter makes
     * rooms out of many strokes and this makes them out of one. What is under rehearsal is the
     * round, not the painter.
     */
    private val ROOMS: List<String> = listOf(
        "FRONT ROOM", "SCULLERY", "BACK HALL", "WORKSHOP", "SUN ROOM",
    )

    /**
     * Ten ordinary cards, two to a room, each carrying a distinct shape off the real roster.
     *
     * Seven characters out of the printable alphabet, because that is what `CardPayload` encodes
     * and therefore what a card in somebody's hand could actually carry.
     */
    private val ordinary: List<Registration> = List(10) { i ->
        Registration(
            card = MarkerCard(
                version = 1,
                shape = MarkerShapes.registrable[i],
                id = MarkerId("SEEDS-" + ('A' + i)),
            ),
            room = Room(ROOMS[i / 2]),
        )
    }

    private val map: HouseMap = HouseMap.of(
        registrations = ordinary,
        terminal = Registration(
            MarkerCard(1, MarkerShapes.TERMINAL, MarkerId("SEEDS-T")), Room(ROOMS.first()),
        ),
        // The middle room rather than an end: a meeting point at the end of a corridor makes every
        // walk-in the longest one there is.
        meeting = Registration(
            MarkerCard(1, MarkerShapes.MEETING, MarkerId("SEEDS-U")), Room(ROOMS[2]),
        ),
    )

    /**
     * The home, under whatever name the run is hosting.
     *
     * The name is a parameter because it is the one thing the launch really does say — a run names
     * the house it is putting on the air, and two rehearsals on one network under one name is the
     * refusal E-D3-2 is about. Everything else is the same house every time, which is what makes
     * two runs comparable.
     */
    fun named(name: String): SavedHome = SavedHome(name = name, plan = PLAN, map = map)

    private val PLAN: HousePlan = HousePlan.of(
        listOf(
            Floor(
                name = "GROUND",
                rooms = ROOMS.mapIndexed { i, room ->
                    PlanRoom(Room(room), listOf(CellRect(x = i, y = 0, width = 1, height = 1)))
                },
            )
        )
    )
}
