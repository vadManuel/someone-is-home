package home.someoneshome.ui

import home.someoneshome.model.CardPayload
import home.someoneshome.model.CardRejection
import home.someoneshome.model.Cell
import home.someoneshome.model.CellRect
import home.someoneshome.model.Floor
import home.someoneshome.model.HomeReview
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShape
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.PaintResult
import home.someoneshome.model.RegisterResult
import home.someoneshome.model.Registration
import home.someoneshome.model.Room
import home.someoneshome.model.RoomKind
import home.someoneshome.model.SavedHome
import home.someoneshome.model.PlanRoom as PaintedRoom

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor

/**
 * **The home editor's state, and the only mutable thing in `:ui` that is not chrome.**
 *
 * The host paints a plan; this holds it. It sits *beside* [PanelState] for the same reason
 * [FlowModel]'s trail does: `PanelState` is flat and inert, every field of it already decided at
 * the effect boundary, and a plan under a finger is neither flat nor decided.
 *
 * ### It answers no game question, and it never will
 *
 * A [HousePlan] is host-side setup data, drawn in the light, weeks before anybody plays. Nothing
 * here is read during a round: the plan is saved, and in play the authority sends whatever
 * narrower view a screen needs. That is what makes an editor with real mutable state lawful in a
 * module that cannot see `core` — there is no rule here to leak, because there is no rule here.
 *
 * ### The refusals are the model's, not this layer's
 *
 * [HousePlan.paint] refuses politely — overlapping cells, a name already used, a storey that is
 * not there — and [refusal] is where the answer is put so the editor can say it **while the
 * finger is still on the screen**. This class adds no rules of its own; every `no` in it is one
 * of the model's, translated into a line of screen copy. A second opinion about what is legal is
 * a second opinion that will one day disagree.
 *
 * ### The cards are real cards
 *
 * [map] is a [HouseMap]: printed [MarkerCard]s with the ids on them, bound to rooms, and the two
 * reserved cards — the one marked T and the meeting card. It used to be a fixture — room name to a
 * list of shapes — and every rule that depended on it was real while the data under it was not.
 * What the rules were always about is unchanged: **stairs hold nothing** (D-099), so [setKind] to
 * stairs unregisters the room's cards as part of the change rather than in a flow that remembers
 * to, and [review] is counted off the map rather than off a flag somebody sets.
 *
 * **Every refusal a scan can produce is the map's**, translated here into a line of screen copy
 * exactly as [HousePlan.paint]'s are. This class still holds no rule of its own.
 */
class HomeEditorModel(
    plan: HousePlan,
    floorName: String,
    map: HouseMap = HouseMap.EMPTY,
    name: String = "",
) {

    var plan: HousePlan by mutableStateOf(plan)
        private set

    /**
     * The home under edit, by name.
     *
     * The editor holds a whole home rather than only its geometry — the plan, what is registered
     * in it, and what the host calls it — because those are saved together and lost together. The
     * heading reads this, the save screen types into it, and [asSavedHome] is the whole thing
     * handed over in one piece.
     */
    var name: String by mutableStateOf(name)
        private set

    /** The storey the host has open. Floors are unordered, so this is a name and not an index. */
    var floorName: String by mutableStateOf(floorName)
        private set

    /**
     * The room the panel is about.
     *
     * **Null only when the plan holds no rooms at all.** The room panel always has a subject:
     * opening it with nothing held would draw a name field for a room that does not exist, and
     * the host would type into it. Painting one holds it, deleting one holds another.
     */
    var held: String? by mutableStateOf(plan.floorNamed(floorName)?.rooms?.firstOrNull()?.name)
        private set

    /** The rectangle under the finger, live. Null except during a drag. */
    var drag: CellRect? by mutableStateOf(null)
        private set

    /**
     * The last refusal, in the host's words.
     *
     * Cleared by the next thing the host does, so it describes the gesture they just made rather
     * than accumulating. Rule 6 — errors silent to the player — is about a player mid-round; a
     * host painting a house in a lit room is owed the reason.
     */
    var refusal: String? by mutableStateOf(null)
        private set

    /** What the host has scanned: cards bound to rooms, and the one card marked T. */
    var map: HouseMap by mutableStateOf(map)
        private set

    /**
     * What the last scan did, so the scan screen can show it.
     *
     * Held here rather than on [PanelState] because it is editing state and `PanelState` is flat
     * and inert.
     *
     * **Cleared when the host opens a different room**, and not when the scan screen is left. A
     * readout is about a card *in a room*: carrying it into the next room would tell the host a
     * card had just been read somewhere they have only walked into, while dropping it every time
     * they step out to the marker sheet and back would erase the confirmation they went to look at.
     */
    var lastScan: ScanOutcome? by mutableStateOf(null)
        private set

    private var anchor: Cell? = null

    /**
     * The room the current drag started inside, if it started inside one.
     *
     * **This is how an L-shaped room gets made.** A room is a list of strokes and not one rect —
     * that is the design's own second argument for a grid, because real houses have L-shaped
     * rooms and rectangles cannot express them — so the editor needs a gesture that adds a
     * stroke to a room that already exists. Starting the drag *inside* a room is that gesture,
     * and it is unambiguous: a drag from bare grid can only be a new room, and a drag from
     * inside a room can only be that room growing, since a second room there would overlap it.
     */
    private var growing: String? = null

    // ---- What the screens read ----------------------------------------------------------------

    val floor: Floor? get() = plan.floorNamed(floorName)

    val rooms: List<PaintedRoom> get() = floor?.rooms.orEmpty()

    val heldRoom: PaintedRoom? get() = held?.let { plan.roomNamed(it) }

    /** The held room's type, or the type a room gets when there is no room: an ordinary one. */
    val heldKind: RoomKind get() = heldRoom?.kind ?: RoomKind.Room

    val heldName: String get() = heldRoom?.name.orEmpty()

    val heldMarkers: List<MarkerShape> get() = markersIn(heldName)

    /** The cards in a room, as shapes — a shape is a marker's whole name to everyone but the app. */
    fun markersIn(room: String): List<MarkerShape> = map.inRoomNamed(room).map { it.card.shape }

    fun cardsIn(room: String): List<Registration> = map.inRoomNamed(room)

    /**
     * Whether a room holds anything a type change would take away.
     *
     * The one question the stairs warning turns on. An empty room becoming stairs costs nothing
     * and must not be interrogated about it; an occupied one costs every card in it.
     */
    fun holdsAnything(room: String): Boolean = map.holdsAnything(room)

    /** The one room holding the T card, by name. Every screen about the terminal asks for this. */
    val terminal: String? get() = map.terminal?.room?.name

    /** The one room holding the meeting card, by name. Where meetings are called from (D-121). */
    val meeting: String? get() = map.meeting?.room?.name

    /** **No terminal, no playable home.** Part of the REVIEW gate, and a fact rather than a flag. */
    val hasTerminal: Boolean get() = map.terminal != null

    /** **No meeting card, nowhere to call a meeting.** The gate's second requirement (D-127). */
    val hasMeeting: Boolean get() = map.meeting != null

    /**
     * **The REVIEW HOME gate, asked of the home rather than kept as a flag** (D-127).
     *
     * Every requirement is counted off the map the host has been building, on every read. A cached
     * verdict is a verdict that can be right about a home the host has since changed, and the one
     * change it would be wrong about is the one they made to fix it.
     */
    val review: HomeReview
        get() = HomeReview.of(markerCount, hasTerminal = hasTerminal, hasMeeting = hasMeeting)

    val floorCount: Int get() = plan.floors.size

    /** Rooms, and stairs are not rooms — see [HousePlan.roomCount]. */
    val roomCount: Int get() = plan.roomCount
    val markerCount: Int get() = map.registrations.size

    fun roomsOn(floor: String): Int = plan.floorNamed(floor)?.roomCount ?: 0

    fun markersOn(floor: String): Int =
        plan.floorNamed(floor)?.rooms?.sumOf { markersIn(it.name).size } ?: 0

    /** The room covering a cell on the open storey, or none — most of a grid is not a room. */
    fun roomAt(cell: Cell): PaintedRoom? = floor?.roomAt(cell)

    // ---- Painting -----------------------------------------------------------------------------

    /**
     * The finger went down. The anchor is one corner; the other one follows it.
     *
     * **Two corners, not a brush.** The design's own instruction on the screen is DRAG TWO
     * CORNERS TO ADD A ROOM, and a stroke is rectangular because a drag is — the room is the
     * union of strokes, which is what lets an L-shaped kitchen be one room with one name.
     */
    fun dragFrom(cell: Cell) {
        anchor = cell
        growing = roomAt(cell)?.name
        drag = spanning(cell, cell)
        refusal = null
    }

    fun dragTo(cell: Cell) {
        val from = anchor ?: return
        drag = spanning(from, cell)
    }

    /**
     * The finger came up. The rectangle becomes a room — a new one, or another stroke of the one
     * the drag started in — or the model says why it cannot.
     *
     * Returns the room's name, or null if it was refused. The caller uses that to decide nothing;
     * [refusal] carries the reason and the editor prints it.
     */
    fun dropDrag(): String? {
        val rect = drag ?: return null
        val grown = growing?.let { plan.roomNamed(it) }
        drag = null
        anchor = null
        growing = null
        val storey = floor ?: run { refusal = noStorey(floorName); return null }
        val room =
            if (grown != null) PaintedRoom(grown.room, grown.strokes + rect)
            else PaintedRoom(Room(freeRoomName()), listOf(rect))
        val painted = plan.paint(storey.name, room)
        return if (accept(painted)) {
            held = room.name
            room.name
        } else {
            null
        }
    }

    /** The finger left the screen without finishing. Nothing was drawn and nothing is refused. */
    fun cancelDrag() {
        drag = null
        anchor = null
        growing = null
    }

    /**
     * Whether the rectangle under the finger lands on cells **another** room already holds.
     *
     * The room being grown is not another room, and a stroke that overlaps the one it is being
     * added to is ordinary — cells are counted once. Colouring that as a collision would tell a
     * host their L-shaped kitchen was illegal at the exact moment it became L-shaped.
     */
    val dragBlocked: Boolean
        get() {
            val rect = drag ?: return false
            return rooms.any { room ->
                room.name != growing && room.strokes.any { it overlaps rect }
            }
        }

    // ---- The room panel ------------------------------------------------------------------------

    /** Open a room in the panel. The plan is what says which rooms there are. */
    fun open(name: String) {
        if (plan.roomNamed(name) == null) return
        if (name != held) lastScan = null
        held = name
        refusal = null
    }

    /**
     * Name the held room — the field, and the preset chips, which are the same act.
     *
     * A rename is a forget and a repaint, because a room's name is its identity everywhere else:
     * `Room` is a name and a kind, and it is what a card is registered to. The cards and the T
     * card come across with it, or a rename would silently unregister a room's contents.
     */
    fun renameHeld(to: String) {
        val room = heldRoom ?: return
        val name = to.trim().uppercase()
        if (name == room.name) return
        if (name.isEmpty()) {
            refusal = "A ROOM NEEDS A NAME"
            return
        }
        if (plan.roomNamed(name) != null) {
            refusal = alreadyCalled(name)
            return
        }
        val storey = plan.floorOf(room)?.name ?: run { refusal = noStorey(floorName); return }
        val without = plan.forget(room.name)
        val painted = without.paint(storey, PaintedRoom(Room(name, room.kind), room.strokes))
        if (accept(painted)) {
            map = map.renamedRoom(room.name, Room(name, room.kind))
            held = name
        }
    }

    /**
     * ROOM or STAIRS.
     *
     * **Stairs hold nothing, and that is enforced here rather than asked for.** D-099 makes a
     * registration into stairs impossible to construct; the editor's half of the same rule is
     * that the cards in a room stop being registered *as part of* the room becoming stairs. A
     * flow that changed the type and then remembered to clear the cards is a flow that will one
     * day not remember.
     *
     * The host is warned first — see [FlowModel.pickRoomType] — but only when there is something
     * to warn about. This is where it actually happens, whichever route reached it.
     */
    fun setKind(kind: RoomKind) {
        val room = heldRoom ?: return
        if (room.kind == kind) return
        val storey = plan.floorOf(room)?.name ?: run { refusal = noStorey(floorName); return }
        val painted = plan.paint(storey, PaintedRoom(Room(room.name, kind), room.strokes))
        if (accept(painted) && kind == RoomKind.Stairs) unregisterAll(room.name)
    }

    /** Forget the held room, its cards with it, and hold whatever is left on this storey. */
    fun deleteHeld() {
        val room = heldRoom ?: return
        plan = plan.forget(room.name)
        unregisterAll(room.name)
        held = floor?.rooms?.firstOrNull()?.name
        refusal = null
    }

    // ---- Registration --------------------------------------------------------------------------

    /**
     * **A card was read. Offer it to the room the host has open.**
     *
     * Every outcome the map defines is answered here, and the `when` is exhaustive so that a new
     * one cannot arrive as silence. That is the shape rule 1 is about, one layer up from the loop:
     * a scan that produced nothing on screen is indistinguishable from a scan that never happened,
     * and the host walks away believing a card is registered.
     *
     * Returns the map's own answer so the flow layer can decide where the host goes — the one
     * refusal that is a screen rather than a line is the terminal already being somewhere else.
     * **Null is not one of the map's answers**: it means there was no room to offer the card to,
     * which the map was never asked about. The screen still says so.
     */
    fun register(card: MarkerCard): RegisterResult? {
        val room = heldRoom
        if (room == null) {
            lastScan = ScanOutcome.Refused(card, "OPEN A ROOM FIRST")
            return null
        }
        val result = map.register(card, room.room)
        lastScan = when (result) {
            is RegisterResult.Registered -> {
                map = result.map
                ScanOutcome.Landed(card, room.name, from = null)
            }

            is RegisterResult.Moved -> {
                map = result.map
                ScanOutcome.Landed(card, room.name, from = result.from.name)
            }

            // D-086, settled at revision 18. Two live cards may never share a shape: the shape is
            // the marker's whole name, so a player told to go to the circle would have two places
            // to stand — and the wrong-room reports that follow are indistinguishable from the
            // error the Terminal injects on purpose. The host is holding the card, in the light,
            // with 43 other shapes to choose from.
            is RegisterResult.ShapeAlreadyRegistered -> ScanOutcome.Refused(
                card,
                "THAT SHAPE IS ALREADY IN ${result.to.room.name}",
            )

            // Unreachable through the screens — the marker sheet is only offered for a room — and
            // answered anyway. A room can become stairs from the room panel while this room is the
            // one the scan screen is holding, and the absent refusal would be the leak.
            is RegisterResult.StairsHoldNothing ->
                ScanOutcome.Refused(card, "${result.room.name} IS STAIRS. STAIRS HOLD NOTHING")

            // The two refusals that are screens: the host has to be told where the card already is
            // and offered the move, because they are about to go and find it.
            is RegisterResult.TerminalTaken -> ScanOutcome.Refused(
                card,
                "THE TERMINAL IS IN ${result.at.room.name}",
            )

            is RegisterResult.MeetingTaken -> ScanOutcome.Refused(
                card,
                "THE MEETING CARD IS IN ${result.at.room.name}",
            )
        }
        return result
    }

    /**
     * MOVE THE TERMINAL TO THIS ROOM: the answer to [RegisterResult.TerminalTaken].
     *
     * **The card is the one that was just scanned**, read back off [lastScan], rather than the one
     * already placed: the host is holding it, and moving the terminal is done by putting that card
     * down in this room. The old one becomes a piece of paper in a room that no longer has a
     * terminal, which is what the screen warned it would.
     *
     * Does nothing when no scan is outstanding. Nothing on any screen can reach that — the button
     * only exists on the screen a scan put the host on — and it is the honest answer rather than a
     * terminal placed with a card nobody read.
     */
    fun moveTerminal() {
        val card = lastScan?.card ?: return
        val room = heldRoom ?: return
        val result = map.moveTerminal(card, room.room)
        when (result) {
            is RegisterResult.Registered -> {
                map = result.map
                lastScan = ScanOutcome.Landed(card, room.name, from = null)
            }

            is RegisterResult.Moved -> {
                map = result.map
                lastScan = ScanOutcome.Landed(card, room.name, from = result.from.name)
            }

            else -> lastScan = ScanOutcome.Refused(card, "${room.name} CANNOT HOLD IT")
        }
    }

    /**
     * MOVE THE MEETING CARD TO THIS ROOM: the answer to [RegisterResult.MeetingTaken].
     *
     * The terminal's move, in every respect — see [moveTerminal] for why the card comes off
     * [lastScan] rather than off the map, and why doing nothing is the honest answer when no scan
     * is outstanding.
     */
    fun moveMeeting() {
        val card = lastScan?.card ?: return
        val room = heldRoom ?: return
        when (val result = map.moveMeeting(card, room.room)) {
            is RegisterResult.Registered -> {
                map = result.map
                lastScan = ScanOutcome.Landed(card, room.name, from = null)
            }

            is RegisterResult.Moved -> {
                map = result.map
                lastScan = ScanOutcome.Landed(card, room.name, from = result.from.name)
            }

            else -> lastScan = ScanOutcome.Refused(card, "${room.name} CANNOT HOLD IT")
        }
    }

    /** REMOVE IT: the T card belongs to no room, and this home cannot be saved until one does. */
    fun removeTerminal() {
        map = map.forgetTerminal()
    }

    /** REMOVE IT: the meeting card belongs to no room, and there is nowhere to call a meeting. */
    fun removeMeeting() {
        map = map.forgetMeeting()
    }

    /** A tap on a marker in the sheet: that card is no longer registered anywhere. */
    fun forgetMarker(id: MarkerId) {
        map = map.forget(id)
    }

    /** A symbol resolved and was not a card this build can read. */
    fun refuseScan(why: CardRejection) {
        lastScan = ScanOutcome.Unreadable(why)
    }

    // ---- Floors --------------------------------------------------------------------------------

    /**
     * Add a storey and open it.
     *
     * **Additive and unordered.** The new one is named for how many there are and not for where
     * it sits, because there is no where: floors carry no number, nothing connects them, and the
     * app renders what was drawn. The host renames it.
     */
    fun addFloor(): String {
        val name = freeFloorName()
        plan = plan.withFloor(name)
        floorName = name
        held = null
        refusal = null
        return name
    }

    /** Switch storeys. The panel's subject goes with it — a room belongs to one floor. */
    fun openFloor(name: String) {
        if (plan.floorNamed(name) == null) return
        floorName = name
        lastScan = null
        held = plan.floorNamed(name)?.rooms?.firstOrNull()?.name
        drag = null
        anchor = null
        refusal = null
    }

    // ---- The home the editor is holding ---------------------------------------------------------

    /**
     * Type over the home's name. Uppercased like a room's, and for the same reason: it is one
     * name in one interface, and a house called `the bungalow` in a list of shouted ones reads as
     * a different kind of thing.
     */
    fun nameHome(to: String) {
        name = to.uppercase()
    }

    /**
     * Open a stored home for editing — **the plan, the cards and the terminal together.**
     *
     * A home is one thing. Loading the geometry and leaving the previous home's cards behind
     * would register this house's rooms with the last one's markers, and the only person who
     * would ever know is the host standing in a room with nothing in it.
     */
    fun load(home: SavedHome) {
        plan = home.plan
        map = home.map
        lastScan = null
        name = home.name
        floorName = home.plan.floors.firstOrNull()?.name ?: GROUND
        held = floor?.rooms?.firstOrNull()?.name
        drag = null
        anchor = null
        growing = null
        refusal = null
    }

    /**
     * MAP A NEW HOME: an empty grid with one storey on it, under a provisional name.
     *
     * One floor rather than none, because a plan with no storey has nowhere to paint and the
     * host's first drag would be refused for a reason that is not their fault.
     */
    fun startNewHome(name: String) {
        plan = HousePlan.EMPTY.withFloor(GROUND)
        map = HouseMap.EMPTY
        lastScan = null
        this.name = name
        floorName = GROUND
        held = null
        drag = null
        anchor = null
        growing = null
        refusal = null
    }

    /**
     * The home as it would be stored.
     *
     * [SavedHome] refuses a home with no name and refuses cards in a room that is not there or is
     * stairs — the same last-ditch guarantee [HousePlan.of] is behind [paint]. Nothing this
     * editor can do reaches those, which is the point of asking it here rather than trusting it.
     */
    fun asSavedHome(named: String = name): SavedHome =
        SavedHome(name = named, plan = plan, map = map)

    // ---- Internals -----------------------------------------------------------------------------

    /**
     * Take the plan if it was painted; take the reason in the host's words if it was not.
     *
     * **One place, so nothing is swallowed.** Every refusal the model can return is turned into
     * a line here — a `when` with an `else` that shrugs is how a host comes to believe the app
     * ignored them.
     */
    private fun accept(result: PaintResult): Boolean = when (result) {
        is PaintResult.Painted -> {
            plan = result.plan
            refusal = null
            true
        }

        is PaintResult.CellsAlreadyPainted -> {
            refusal = "THAT CROSSES ${result.by.name}"
            false
        }

        is PaintResult.NameAlreadyUsed -> {
            refusal = alreadyCalled(result.by.name)
            false
        }

        is PaintResult.NoSuchFloor -> {
            refusal = noStorey(result.name)
            false
        }
    }

    private fun unregisterAll(room: String) {
        map = map.forgetIn(room)
    }

    /**
     * A provisional name, and it is provisional on purpose.
     *
     * A painted room has to have a name because a name is what a card is registered to, and it
     * has to be unique across the whole plan for the same reason. ROOM 2 is obviously nobody's
     * word for a room, which is what sends the host to the panel to say what it really is.
     */
    private fun freeRoomName(): String = generateSequence(1) { it + 1 }
        .map { "ROOM $it" }
        .first { plan.roomNamed(it) == null }

    private fun freeFloorName(): String = generateSequence(plan.floors.size + 1) { it + 1 }
        .map { "FLOOR $it" }
        .first { plan.floorNamed(it) == null }

    private fun alreadyCalled(name: String) = "A ROOM IS ALREADY CALLED $name"

    private fun noStorey(name: String) = "NO STOREY CALLED $name"

    companion object {

        /**
         * The editor's viewport, in cells.
         *
         * **A window, not a bound.** [Cell] coordinates are unbounded and negative ones are
         * ordinary — a host who starts painting in the middle of the hall and works outwards must
         * not hit an origin — so this is how much of the grid a phone shows at once, and panning
         * it is a later story. It is the design's own ten-by-twelve grid.
         */
        const val COLS = 10
        const val ROWS = 12

        /**
         * The preset names the room panel offers as chips.
         *
         * Five, because five is what fits on one row at chip size, and these five because they
         * are the rooms every house has. Everything else is typed.
         */
        val PRESETS: List<String> = listOf("KITCHEN", "LIVING", "GARAGE", "BATH 1", "BED 1")

        /**
         * The bungalow — **the same rooms the live map draws, converted rather than restated.**
         *
         * [Plan]'s KDoc makes the promise this keeps: one source of truth for every screen that
         * draws a plan, because a room sitting in one place in the editor and another on the map
         * would be a bug nobody could see from inside the game. So the ground floor is read out
         * of [Plan.rooms] and turned into strokes, rather than typed a second time.
         *
         * The upper storey has no counterpart on the live map — the map is drawn for the room a
         * player is standing in — so it is written here, and it is what the host-setup screens
         * have always claimed: five rooms and four cards.
         */
        fun bungalow(): HomeEditorModel {
            val ground = Floor(
                GROUND,
                Plan.rooms.map { rect ->
                    PaintedRoom(
                        Room(rect.name, if (rect.transit) RoomKind.Stairs else RoomKind.Room),
                        listOf(
                            CellRect(
                                x = rect.c0,
                                y = rect.r0,
                                width = rect.c1 - rect.c0 + 1,
                                height = rect.r1 - rect.r0 + 1,
                            )
                        ),
                    )
                },
            )
            val upper = Floor(
                UPPER,
                listOf(
                    painted("BED 1", RoomKind.Room, 0, 0, 4, 4),
                    painted("BED 2", RoomKind.Room, 5, 0, 5, 4),
                    painted("LANDING", RoomKind.Room, 0, 5, 6, 2),
                    painted("TOP OF STAIRS", RoomKind.Stairs, 6, 5, 4, 2),
                    painted("BATH 1", RoomKind.Room, 0, 8, 10, 3),
                ),
            )
            return HomeEditorModel(
                name = BUNGALOW,
                plan = HousePlan.of(listOf(ground, upper)),
                floorName = GROUND,
                map = bungalowMap(),
            )
        }

        /**
         * The nine ordinary cards the save screen has always counted, plus the two reserved ones.
         *
         * Five on the ground floor and four upstairs; the T card in the hall and the meeting card
         * in LIVING. The shapes come off the real roster because a marker's shape is its whole
         * identity to everyone who is not the app, and the ids are seven readable characters — a
         * fixture whose ids are noise is a fixture nobody can follow on a screenshot.
         *
         * **GARAGE holding two cards is deliberate**: a room with more than one is what the marker
         * sheet was drawn for, and a fixture where every room held exactly one would never show it.
         * They carry different shapes, because two live cards may never share one (D-086).
         *
         * **LIVING holds an ordinary marker as well as the meeting card, and that is deliberate
         * too.** D-121 reserves a shape, not a room, and a fixture where the meeting area held
         * nothing else would let a screen be written as though it did.
         *
         * Nine markers is one over D-127's floor of eight, so the fixture passes REVIEW — and
         * HOSTS UP TO 6, which is the party the design's own lobby is drawn with.
         */
        private fun bungalowMap(): HouseMap {
            val cards = listOf(
                "KITCHEN" to "triangle_up",
                "LIVING" to "square",
                "STUDY" to "diamond",
                "GARAGE" to "ring",
                "GARAGE" to "star",
                "BED 1" to "crescent",
                "BED 2" to "wide_rect",
                "LANDING" to "circle",
                "BATH 1" to "triangle_down",
            )
            val registrations = cards.mapIndexed { i, (room, shape) ->
                Registration(fixtureCard(shape, i + 1), Room(room))
            }
            return HouseMap.of(
                registrations,
                terminal = Registration(fixtureCard(MarkerShapes.TERMINAL.id, 0), Room("HALL")),
                meeting = Registration(fixtureCard(MarkerShapes.MEETING.id, 10), Room("LIVING")),
            )
        }

        /**
         * A card with a readable id: `HOME-00` through `HOME-10`, seven characters.
         *
         * The hyphen rather than a space because [MarkerShapes.ALPHABET] is QR's alphanumeric set
         * **minus SPACE** — a space is ambiguous in print, and one here would push the encoder out
         * of alphanumeric mode and grow the printed symbol past Version 1.
         */
        private fun fixtureCard(shape: String, number: Int) = MarkerCard(
            version = CardPayload.VERSION,
            shape = MarkerShapes.require(shape),
            id = MarkerId("HOME-" + number.toString().padStart(2, '0')),
        )

        const val GROUND = "GROUND"
        const val UPPER = "UPPER"

        /** The fixture's own name, which is also the first row of [SavedHomesModel.sample]. */
        const val BUNGALOW = "THE BUNGALOW"

        private fun painted(name: String, kind: RoomKind, x: Int, y: Int, w: Int, h: Int) =
            PaintedRoom(Room(name, kind), listOf(CellRect(x, y, w, h)))
    }
}

/**
 * **What the last scan did, in the words the viewfinder shows.**
 *
 * The scan screen is the one place in host setup where the host is looking at a card in their hand
 * and at the phone, and has to be told which of the two the app is talking about. So an outcome
 * always carries a shape: what landed, or what was turned away. A refusal with no shape on it
 * would leave a host reading THAT SHAPE IS ALREADY IN GARAGE with three cards in their hand.
 */
sealed interface ScanOutcome {

    /**
     * The card the message is about — the one in the host's hand.
     *
     * **Null on exactly one outcome**: a payload this build could not read is not a card, and
     * inventing one to fill the field would put a shape on the screen that is on nothing the host
     * is holding.
     */
    val card: MarkerCard?

    val shape: MarkerShape? get() = card?.shape

    val isTerminal: Boolean get() = card?.isTerminal == true

    val isMeeting: Boolean get() = card?.isMeeting == true

    /**
     * The card is registered here now.
     *
     * [from] is the room it was in before, when the host is correcting themselves mid-walk. Null
     * on a card that had not been registered anywhere, which is most of them.
     */
    data class Landed(override val card: MarkerCard, val room: String, val from: String?) : ScanOutcome

    /** Nothing was registered, and this is why — said out loud, because host setup is in the light. */
    data class Refused(override val card: MarkerCard, val why: String) : ScanOutcome

    /**
     * The symbol resolved and was not one of ours, or was one this build cannot read.
     *
     * D-071 allows this one to be specific where the in-round scan may not: an unreadable card is
     * a fact about a piece of paper, not a statement about a player, and the host is standing in a
     * lit room holding the paper.
     */
    data class Unreadable(val why: CardRejection) : ScanOutcome {
        override val card: MarkerCard? get() = null
    }
}

/**
 * The open storey as cells, ready to draw.
 *
 * **The held room goes DARKER, and that is correct.** The design carries a comment describing the
 * opposite — selection keeps the accent while every other room drops to neutral putty — which was
 * an earlier intention its own code never implemented. Confirmed as the code, not the comment. Do
 * not "fix" this to match a comment that is not in this repo.
 *
 * [focus] is passed rather than assumed to be [HomeEditorModel.held], because the two screens
 * that draw a plan want different answers. The room panel is *about* a room and lights it; the
 * editor is about the whole storey and lights nothing — the panel always holds a room, so an
 * editor that drew the held one would open on a house with a room already picked out, which
 * reads as a selection the host did not make.
 *
 * Row-major over the viewport, so index `i` is column `i % COLS`, row `i / COLS` — the order
 * [EditorPlan] draws in.
 */
fun HomeEditorModel.editorCells(focus: String? = held): List<EditorCell> =
    planCells(floor, focus)

/**
 * The same cells for a storey nobody is editing.
 *
 * The editor is not the only screen that wants to draw a plan: a host choosing between four saved
 * homes recognises theirs by its shape long before they read `2 FLOORS . 9 ROOMS`, and the screen
 * that asks them to destroy one should show what is about to go. Those screens hold a [SavedHome]
 * and no editor, so the colouring lives here rather than on the model, and [HomeEditorModel] is
 * the caller that happens to have an open storey.
 */
fun planCells(storey: Floor?, focus: String? = null): List<EditorCell> =
    List(HomeEditorModel.ROWS * HomeEditorModel.COLS) { i ->
        val room = storey?.roomAt(Cell(x = i % HomeEditorModel.COLS, y = i / HomeEditorModel.COLS))
        when {
            room == null -> EditorCell(Amber.BonePutty, null)
            room.name == focus -> EditorCell(Amber.SlateFocus, Amber.SlateFocusFill)
            // Slate underneath, pale stripes on top: the source gradient is 3 units of BoneHatch
            // against 1 of Slate, so the SLATE is the gap colour. Leaving the fill null let the
            // bone ground show through and the stairs went pale.
            room.kind == RoomKind.Stairs -> EditorCell(Amber.Slate, Amber.Slate, hatch = true)
            else -> EditorCell(Amber.Slate, Amber.SlateFill)
        }
    }

/** The rectangle two corners span, in either order — a drag runs whichever way the finger went. */
fun spanning(a: Cell, b: Cell): CellRect = CellRect(
    x = minOf(a.x, b.x),
    y = minOf(a.y, b.y),
    width = maxOf(a.x, b.x) - minOf(a.x, b.x) + 1,
    height = maxOf(a.y, b.y) - minOf(a.y, b.y) + 1,
)

/**
 * Whether two strokes share a cell, **without expanding either into cells**.
 *
 * This is asked on every frame of a drag, and `CellRect.cells` builds a list. Sixty of those a
 * second against a whole-app budget of about half a megabyte a second is the wrong direction for
 * an answer four comparisons give.
 */
infix fun CellRect.overlaps(other: CellRect): Boolean =
    x < other.x + other.width && other.x < x + width &&
        y < other.y + other.height && other.y < y + height

/**
 * The editor the host-setup screens draw.
 *
 * Provided by [Screen], so a screen reads the plan the same way it reads its actions, and a test
 * that renders one screen gets its own editor rather than sharing a global one that the previous
 * test painted over.
 */
val LocalEditor: ProvidableCompositionLocal<HomeEditorModel> =
    staticCompositionLocalOf { HomeEditorModel.bungalow() }

/**
 * **The grid under the finger: drag two corners to add a room, tap a room to open it.**
 *
 * The two gestures sit in separate `pointerInput` blocks on purpose. A drag has to wait out touch
 * slop before it is a drag, and a tap has to wait for the finger to come up without having moved;
 * one detector cannot be waiting for both, and combining them by hand is how a slow, deliberate
 * drag from one corner of a room to the other ends up registering as a tap on the corner.
 *
 * ### The anchor is where the finger WENT DOWN, and getting that wrong loses a cell
 *
 * The drag is written out by hand rather than with `detectDragGestures` for one reason:
 * `detectDragGestures` reports the position at which the gesture *became* a drag — after touch
 * slop has been travelled — so the first corner arrives already a cell or two along the drag.
 * The host draws a four-cell room and gets a three-cell one, off by exactly the corner they
 * aimed at, and if that corner was inside a room the drag creates a new room beside it instead
 * of growing it. `awaitFirstDown` is the only position that is the corner the host chose.
 *
 * `EditorSurfaceTest` caught this; nothing else could have. The model's own tests are told which
 * cells the finger crossed, so they were being told the truth about a lie.
 *
 * ### Why the tap navigates through the actions layer rather than naming a screen
 *
 * Every other control in this app says where it goes, and `ScreenGraphTest` reads those straight
 * off the screens. This one cannot: where a tap lands depends on **which room is under it**, and
 * on most of the grid there is no room and it lands nowhere. So the edge is declared in
 * [Flow.viaActions] and walked by a test, the same footnote the room-type chip already carries.
 */
@Composable
fun EditorSurface(
    editor: HomeEditorModel,
    onOpenRoom: (Cell) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .pointerInput(editor) {
                awaitEachGesture {
                    // `requireUnconsumed = false` because the tap detector below is the inner
                    // node and consumes the down before this one is reached. Waiting for an
                    // unconsumed down here means waiting forever, and the whole painting gesture
                    // silently does nothing.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val past = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture

                    editor.dragFrom(cellAt(down.position, size))
                    editor.dragTo(cellAt(past.position, size))
                    val finished = drag(past.id) { editor.dragTo(cellAt(it.position, size)) }
                    if (finished) editor.dropDrag() else editor.cancelDrag()
                }
            }
            .pointerInput(editor) {
                detectTapGestures { onOpenRoom(cellAt(it, size)) }
            }
    ) {
        EditorPlan(
            editor.editorCells(focus = null),
            Modifier.fillMaxSize(),
            preview = editor.drag,
            blocked = editor.dragBlocked,
        )
        EditorLabels(
            editor.rooms,
            Modifier.fillMaxSize(),
            markers = { editor.markersIn(it).size },
            terminal = editor.terminal,
            meeting = editor.meeting,
        )
    }
}

/**
 * Where on the grid a finger is, clamped to the viewport.
 *
 * Clamped rather than refused: a drag that runs off the edge of the plan should stop at the edge,
 * not stop tracking — a rectangle that freezes halfway through a drag reads as the app having
 * stopped listening. `floor` rather than a truncating cast, because truncation folds the two
 * cells either side of zero into one.
 */
private fun cellAt(at: Offset, size: IntSize): Cell = Cell(
    x = floor(at.x / (size.width.toFloat() / HomeEditorModel.COLS)).toInt()
        .coerceIn(0, HomeEditorModel.COLS - 1),
    y = floor(at.y / (size.height.toFloat() / HomeEditorModel.ROWS)).toInt()
        .coerceIn(0, HomeEditorModel.ROWS - 1),
)
