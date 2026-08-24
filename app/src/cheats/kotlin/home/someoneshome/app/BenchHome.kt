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
import home.someoneshome.model.SavedHome

/**
 * **A home for a TEST to run in, drawn in code because no phone in a test has walked one.**
 *
 * ⚠️ **It is not what any round is armed in any more, and that is N3.** For three units this
 * object was the round's home on every phone, hardcoded into the arming draw, because
 * `ArmingInputs` had no home field for the host's own to arrive through (E-PAY-3). It does now.
 * What is left here is a fixture: a lawful home for the tests in this source set to host, and the
 * **wrong answer** a test can name — a `BENCH-*` id on a work order in a round armed with somebody
 * else's home is the defect coming back, by name.
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
     * **The whole thing, as a home somebody could have walked** — what a test hosts.
     *
     * A [SavedHome] is what `HOST WITH THIS HOME` puts on the air and therefore what a round is
     * armed in (`ArmingInputs.home`), so a fixture that stopped at a map and a plan could not be
     * hosted at all. The name is the one every earlier device run used.
     */
    val home: SavedHome = SavedHome(name = NAME, plan = plan, map = map)

    /**
     * What the arming event takes: a marker, the shape printed on it, and the room it is in.
     *
     * `HouseMap.destinations`' answer rather than a second assembly of the same rows — which is
     * how this object came to be the *only* home any round was ever played in. The reserved two
     * are not in it and that rule is stated where the derivation is.
     */
    val destinations: List<Destination> get() = map.destinations

    /** The card a seat should scan to call a meeting. A place, not a line of anybody's order. */
    val meetingCard: MarkerCard = requireNotNull(map.meeting).card
}
