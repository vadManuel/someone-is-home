package home.someoneshome.model

/**
 * **The home's shape, narrowed for a client that has to draw it and tap it** (the owner's Spoof
 * targeting ruling).
 *
 * ### Why this crosses the boundary at all, and what authorised it
 *
 * [Destination]'s own KDoc says the thing this type appears to break: *what is NOT authorised is
 * the whole map to every client; a home's full geography on every phone would be the map of the
 * round the house has never published, arriving as a convenience.* That sentence is still true and
 * this is not that. **It is not arriving as a convenience and it is not going to every client.**
 *
 * The owner's ruling is that **Spoof picks from the map** — the Insider taps a room on the home's
 * plan to place the phantom there — and *the glow of reading the map is the deliberate price.* A
 * picker over rooms cannot be built without rooms, exactly as the Isolate picker could not be built
 * without markers, and this is the same trade taken the same way: `HOME_PLAN_OFFERED` is permitted
 * to `Insider/Live` and to nothing else, so a Resident's copy is constructed and delivered to
 * nobody. Widened by one class it would hand every phone in the house the geography
 * [Effect.WorkOrderIssued] narrows to one seat's own lines.
 *
 * ### It is CELLS, not strokes, and that is the narrowing rather than a format choice
 *
 * [PlanRoom] holds `strokes` — the rectangles the host dragged out in the editor, in the order they
 * dragged them. That is an authoring history: how many gestures a room took, which corner they
 * started from, whether they went back and patched it. **A cell list is the finished floor and
 * nothing about the hand that drew it.** Two hosts whose homes look identical send identical bytes
 * here and send different `strokes`, and there is no reason a player's phone should be able to tell
 * those two hosts apart.
 *
 * ### What it is physically incapable of carrying
 *
 * No markers, no seats, no counts, no occupancy, no Egress nodes, no isolations. It is rooms, their
 * names, whether they are stairs, and which squares they cover. There is nothing inside a row to
 * narrow per recipient — [Destination]'s posture, and it is what makes the audience question the
 * only question this type raises.
 *
 * **Stairs ride it and are not a target.** *The map knows only room and stairs, and stairs hold
 * nothing* — but a plan drawn with the stairs cut out has a hole in it exactly where the reader's
 * own staircase is, which is a worse map to read in the dark than one with a hatched square. So
 * [stairs] travels and the picker declines to take a tap on it.
 */
@ClientFacing
@kotlinx.serialization.Serializable
data class HomePlanView(
    /** In the order the host created them: ground first, like the editor's own tabs. */
    val floors: List<FloorView> = emptyList(),
) {
    /** Every room on every floor, flattened — the picker draws one floor at a time and this is not it. */
    val rooms: List<RoomOutline> get() = floors.flatMap { it.rooms }

    companion object {
        /**
         * A home with no floors, which is what a round armed without one has.
         *
         * The fail-closed direction and the honest picture: an Insider who fires Spoof in a round
         * armed with no home gets a plan with nothing on it and can tap nothing. No copy explains
         * it — see the Isolate picker's empty column, and the reason there is the reason here.
         */
        val EMPTY = HomePlanView()
    }
}

/** One storey of the home: the host's name for it, and the rooms painted on it. */
@ClientFacing
@kotlinx.serialization.Serializable
data class FloorView(val name: String, val rooms: List<RoomOutline> = emptyList())

/**
 * One room, as a set of squares.
 *
 * L-shaped rooms work for free because the squares are a set rather than a rectangle, which is the
 * same property the editor's grid has and the reason the map is a grid at all: real houses have
 * L-shaped rooms and no geometry is needed to describe one.
 */
@ClientFacing
@kotlinx.serialization.Serializable
data class RoomOutline(
    /** The host's own name for the room, exactly as [Destination.room] carries it. */
    val room: String,
    /** Stairs. Never a Spoof target — see the type's KDoc. */
    val stairs: Boolean = false,
    val cells: List<PlanCell> = emptyList(),
)

/** One square of the editor's grid, in the editor's own coordinates. */
@ClientFacing
@kotlinx.serialization.Serializable
data class PlanCell(val x: Int, val y: Int)
