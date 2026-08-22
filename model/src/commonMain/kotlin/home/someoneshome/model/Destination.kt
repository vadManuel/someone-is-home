package home.someoneshome.model

/**
 * **Where a piece of work is: the card, the shape printed on it, and the room it sits in.**
 *
 * The two things a player navigates a dark house by are the room's name and the glyph on the card
 * (`gdd.md`'s own work-order row draws both). Neither existed on the wire before this: `OrderLine`
 * carried no card and no room, so a real order arrived with the destination column empty on every
 * row at once — E-C2-1, closed here.
 *
 * ### It carries the shape's ROSTER ID and never the shape itself
 *
 * [MarkerShape] holds path data — hundreds of characters of contour per glyph — and the client
 * already holds all 44 of them ([MarkerShapes]). So the house sends *which* shape and the client
 * looks it up, which is D-112's arrangement for the light signature applied one type over: the
 * wire is what costs something, and the roster is not a thing two sides can disagree about because
 * a printed card decodes to exactly one entry in it.
 *
 * ### It is physically incapable of saying what the work is, or whose it is
 *
 * A card, a glyph and a room name. No seat, no [SubroutineKind], no answer, no dependency — so a
 * destination cannot become a place another player's work is described from, whichever collection
 * it ends up in. **That is what makes it safe to hold the whole home's geography authority-side
 * and hand out rows of it per seat** ([GameState.destinations], [WorkOrder.asLines]): the
 * narrowing is *which rows*, and there is nothing inside a row to narrow.
 *
 * **What is NOT authorised is the whole map to every client.** A client receives the destinations
 * of its own order's lines and nothing else; a home's full geography on every phone would be the
 * map of the round the house has never published, arriving as a convenience.
 */
@ClientFacing
data class Destination(
    val marker: MarkerId,
    /**
     * The shape's roster id — `MarkerShapes[shape]` on the client. See the KDoc above for why the
     * id travels and the glyph does not.
     */
    val shape: String,
    /**
     * The host's own name for the room, exactly as they typed it on the setup walk.
     *
     * Host-authored text, and the one piece of house geography that crosses the boundary. It is
     * not a secret — the room is a room in a house everybody is standing in — but it is also not
     * something the rules ever had, which is why it arrives as a recorded input on the arming
     * event rather than being derived from anything (E-L4-1's mechanism, taken).
     */
    val room: String,
)

/**
 * **What one line of a work order IS — one of the roster's ten, or the Array Wipe circuit.**
 *
 * A sealed pair rather than a [SubroutineKind] with an eleventh member, because Array Wipe is not
 * one of the ten and never was: the design rates ten Subroutines for light (`gdd.md:565`) and
 * describes Array Wipe separately as a **circuit** (`gdd.md:611`), three markers and a carried
 * state. An eleventh enum member would have put a circuit everywhere a Subroutine is expected —
 * the light roster, the parameter draw, the grading — and every one of those would have had to
 * grow a special case for it.
 *
 * ### The circuit is representable and is not yet dealt
 *
 * The design's assigned mix is *1 circuit, 1 long, 3 medium, 2 short* (`gdd.md:265`), and none of
 * that mix is built: order length is D-129's `K` and the kinds are drawn flat. So the **format**
 * can carry a circuit — which is what lets the client's ARRAY WIPE row stop being a fixture — and
 * the draw does not put one in an order. Dealing one needs the carry-state machine (F-014, E7),
 * which is not this unit's.
 *
 * See [WorkOrder.openAt] for the one consequence of that, stated where it bites.
 */
@ClientFacing
sealed interface AssignedWork {

    /**
     * The kind a scan opens, or **null for work no scan opens yet**.
     *
     * A property rather than an `is` check at each call site, so that the day the circuit becomes
     * scannable there is one place that changes rather than three that have to be found.
     */
    val opensAs: SubroutineKind?

    /**
     * One of the roster's ten. The kind is all the wire carries — the label and the light
     * signature are looked up client-side (D-112).
     */
    data class Subroutine(val kind: SubroutineKind) : AssignedWork {
        override val opensAs: SubroutineKind get() = kind
    }

    /**
     * **The Array Wipe circuit** (`gdd.md:611`, D-122).
     *
     * Three stations — Spares, Rack, Disposal — drawn by the house every round and never stored
     * with the home. It carries no stations of its own: an entry's markers are the entry's
     * ([OrderEntry.markers]), so a circuit line and a Subroutine line are the same shape with a
     * different number of places in it, and there is no second list to disagree with the draw.
     *
     * `opensAs` is null: the carry-state machine that makes a circuit leg scannable is not built.
     */
    data object ArrayWipe : AssignedWork {
        override val opensAs: SubroutineKind? get() = null
    }
}
