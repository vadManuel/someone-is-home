package home.someoneshome.app

import home.someoneshome.model.CellRect
import home.someoneshome.model.Destination
import home.someoneshome.model.Floor
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Registration
import home.someoneshome.model.Room

/**
 * **A home for the rehearsal to run in, drawn in code because no phone in this run has walked one.**
 *
 * ### It is a stand-in, and it is named as one
 *
 * A real home arrives from the host's own filesystem: a `SavedHome` the host painted on the grid
 * and registered cards into, card by card, with the camera. The rehearsal needs a home on five
 * devices at once and a person walking each of them, and there is no person — so this is the third
 * thing the bench supplies that a finger would have, after a resident's name and a one line.
 *
 * **Nothing here is a balance value and no rule reads it.** What it supplies is geography: which
 * cards exist, which room each is in, and which rooms touch. All three are inputs the *host* holds
 * in production and would have drawn on a grid.
 *
 * ### The shape is chosen so that three things the round needs are true
 *
 * - **Ten ordinary markers**, which clears D-127's floor of eight and gives `Balance.activeMarkers`
 *   something to choose a subset of at five and six seats.
 * - **Two cards to a room**, exactly as `core`'s own fixture does and for the reason written there:
 *   a home with one room per card would never once produce two lines of one work order pointing at
 *   the same room name, which is the case a screen most easily draws wrong.
 * - **A corridor, not a clump.** The five rooms are painted in a row, so the adjacency graph is a
 *   path and the two ends are four rooms apart. `egressFor` picks *the two markers whose rooms are
 *   farthest apart*, and a home where everything touches everything would let a pair be chosen that
 *   two people could contain without walking — which would make the Egress leg of this rehearsal
 *   prove nothing about the mechanic it is named after.
 *
 * The two reserved cards are placed as well, because a house with nowhere to meet cannot run a
 * meeting and a house with no Terminal is a home the review screen refuses.
 */
object BenchHome {

    /** What the house is called on the air. The same name every earlier device run used. */
    const val NAME: String = "THE BUNGALOW"

    /**
     * Five rooms in a row, one cell each, painted left to right.
     *
     * One cell per room is enough for adjacency to be a path and is deliberately not a claim about
     * what a real home looks like — the grid painter makes rooms out of many strokes and this makes
     * them out of one. What is under rehearsal is the round, not the painter.
     */
    private val ROOMS: List<String> = listOf("HALL", "KITCHEN", "LOUNGE", "STUDY", "CELLAR")

    /**
     * The plan: one storey, the five rooms side by side.
     *
     * `Floor.neighboursOf` reads cell neighbours, so painting them at x = 0..4 on one row is what
     * makes HALL and CELLAR four apart and everything in between a single step. No geometry is
     * written anywhere; the row is the graph.
     */
    val plan: HousePlan = HousePlan.of(
        listOf(
            Floor(
                name = "GROUND",
                rooms = ROOMS.mapIndexed { i, name ->
                    PlanRoom(Room(name), listOf(CellRect(x = i, y = 0, width = 1, height = 1)))
                },
            )
        )
    )

    /** The ten ordinary cards, two to a room, each carrying a distinct shape off the real roster. */
    private val ordinary: List<Registration> = List(10) { i ->
        Registration(
            card = MarkerCard(
                version = 1,
                shape = MarkerShapes.registrable[i],
                id = MarkerId("BENCH-" + ('A' + i)),
            ),
            room = Room(ROOMS[i / 2]),
        )
    }

    /**
     * The map: ten ordinary cards, the Terminal in the HALL and the meeting card in the LOUNGE.
     *
     * The meeting card sits in the middle room rather than at an end, which is the only judgement
     * in this object that is about play rather than about arithmetic: a meeting point at the end of
     * a corridor makes every walk-in the longest one there is.
     */
    val map: HouseMap = HouseMap.of(
        registrations = ordinary,
        terminal = Registration(
            MarkerCard(1, MarkerShapes.TERMINAL, MarkerId("BENCH-T")), Room(ROOMS.first()),
        ),
        meeting = Registration(
            MarkerCard(1, MarkerShapes.MEETING, MarkerId("BENCH-U")), Room(ROOMS[2]),
        ),
    )

    /**
     * What the arming event takes: a marker, the shape printed on it, and the room it is in.
     *
     * Built off [map] rather than written out again, so a card that moved room in one place cannot
     * still be in the old one here. The reserved two are **not** in it — `Event.RoundArmed.markers`
     * is the ordinary set the work orders are drawn from, and the Terminal and the meeting card are
     * places rather than work.
     */
    val destinations: List<Destination> = ordinary.map {
        Destination(marker = it.card.id, shape = it.card.shape.id, room = it.room.name)
    }

    /** The card a seat should scan to call a meeting. A place, not a line of anybody's order. */
    val meetingCard: MarkerCard = requireNotNull(map.meeting).card
}
