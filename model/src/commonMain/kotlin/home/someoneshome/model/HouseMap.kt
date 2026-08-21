package home.someoneshome.model

/**
 * What a mapped space is. The complete list — the design deleted "passage" on purpose (D-098):
 * the Insider's route between rooms is Override, and Override must never be drawable on a map.
 */
enum class RoomKind { Room, Stairs }

/** A room in the house, named by the host during the setup walk. */
data class Room(val name: String, val kind: RoomKind = RoomKind.Room)

/**
 * One card, bound to one room. No card is meaningful until it has one of these (D-069).
 *
 * **A registration into stairs cannot exist** (D-099). Stairs hold nothing — that is what makes
 * the stairwell invisible to the Terminal and therefore the natural hiding place, and it holds
 * by construction rather than by a check some flow remembers to run: [HouseMap.register] refuses
 * politely first, and this `require` is the last-ditch guarantee for every other constructor
 * path, [HouseMap.of] included. Loud is correct here — this is host-side setup, in the light.
 */
data class Registration(val card: MarkerCard, val room: Room) {
    init {
        require(room.kind != RoomKind.Stairs) {
            "a card cannot be registered into stairs ('" + room.name + "') — stairs hold nothing"
        }
    }
}

/** What happened when a card was offered to the map. */
sealed interface RegisterResult {
    data class Registered(val map: HouseMap) : RegisterResult

    /** The same id, now in a different room. The host moved a card; that is allowed. */
    data class Moved(val map: HouseMap, val from: Room) : RegisterResult

    /**
     * The room is stairs, and stairs hold nothing (D-099).
     *
     * Refused with a distinct kind so the scan flow can tell the host while the card is still in
     * their hand — the same shape every other refusal in this project takes.
     */
    data class StairsHoldNothing(val room: Room) : RegisterResult

    /**
     * A different card already carries this shape.
     *
     * **Refused — settled, D-086 (revision 18).** The shape is the marker's whole name —
     * `MARKER 07` is gone from every screen (D-069) — so two registered cards showing the same
     * shape give the house two markers with one name, and a player told to go to the diamond has
     * two places to stand. The wrong-room reports that follow are indistinguishable from the
     * error the Terminal injects on purpose, which makes the failure silent and permanent — the
     * exact class this project is organised around refusing. The refusal lands on the host in
     * the light, during setup, with 44 shapes to choose from; the alternative's only benefit,
     * data honesty, is already had because the map is keyed on the id either way.
     */
    data class ShapeAlreadyRegistered(val to: Registration) : RegisterResult

    /**
     * The card marked T, and this home already has its terminal in another room.
     *
     * **One home, one terminal.** A second gives the house two places to be found, and standing at
     * the terminal alone in the dark is the trade the whole map is built on. Refused rather than
     * added, and the refusal names the room the existing one is in, because the host is about to
     * go and get it — or decide to move it, which is [HouseMap.moveTerminal].
     *
     * Scanning the T card in the room it is already in is not this: nothing is at stake and
     * nothing is asked.
     */
    data class TerminalTaken(val at: Registration) : RegisterResult
}

/**
 * **Story 0.7 — the house map. The setup walk, which must not evaporate.**
 *
 * Fifteen minutes of a host walking a dark house registering cards to rooms. It has to survive
 * the round ending, the evening ending, and the app being reinstalled.
 *
 * ### Keyed on the card id, never on the shape
 *
 * D-069's whole argument. A host who mislays a card and prints a replacement creates two physical
 * cards showing the same shape; keyed on shape, the old one found later behind a shelf reports a
 * player into whichever room the replacement went to, and that wrong count lands inside the
 * injected error the Terminal already carries on purpose. Keyed on the id, the stale card is
 * simply a card nobody registered, and says so.
 *
 * ### The terminal is in here, not beside it
 *
 * A home has exactly one terminal and the T card is what places it. It is a [Registration] like
 * every other card and it sits in [terminal] rather than in [registrations], because it is never
 * an ordinary marker: nobody is sent to find it as a subroutine, and it is the one place in the
 * house a Resident stands alone in the dark on purpose.
 *
 * ### Ordered, because it is recorded
 *
 * Registrations are a list in registration order, not a map. Iteration order of a hashed
 * collection varies, and this serialises into something a later build has to read back and
 * compare.
 *
 * ### Not `@Serializable`
 *
 * This is authority-side setup data. It reaches clients as whatever narrower view the screens
 * need, constructed for the purpose — never by handing over the map and trusting the reader.
 */
class HouseMap private constructor(
    val registrations: List<Registration>,
    /**
     * Where the card marked T is, or nowhere yet.
     *
     * **Held here rather than beside the map**, because "one home, one terminal" is a statement
     * about what is registered in a house and this is the type that holds that. A terminal kept in
     * a second field somewhere else is a second field that can disagree with this one about which
     * rooms hold something — and the room it disagrees about would be the one a Resident is
     * standing in, in the dark, being told nothing opens there.
     *
     * It is a [Registration] and not a room name because the T card is a card: it has an id, it
     * can be lost and reprinted, and D-069's whole argument is that paper is what the file has to
     * describe.
     */
    val terminal: Registration?,
) {

    /** Every room something is registered in, the terminal's included, in registration order. */
    val rooms: List<Room> get() = (registrations.map { it.room } + listOfNotNull(terminal?.room)).distinct()

    fun registrationOf(id: MarkerId): Registration? =
        registrations.firstOrNull { it.card.id == id }

    /** Every registered card in a room, in registration order. The terminal is not one of them. */
    fun inRoom(room: Room): List<Registration> = registrations.filter { it.room == room }

    /**
     * Every registered card in a room **named** this, for callers that hold a name and not a
     * [Room].
     *
     * Almost every caller is one: a name is what a card is registered to, what the plan keys a
     * painted room on, and what a screen has in its hand. [inRoom] compares whole `Room` values,
     * which is right for a caller holding the room it means and wrong for one holding only a name.
     *
     * **Today the two agree, and it is worth writing down why rather than trusting the name.**
     * `RoomKind` has exactly two values (D-098 deleted the third), a registration into stairs
     * cannot be constructed (D-099), and so every room in this map is an ordinary one and matches
     * `Room(name)` exactly. Injecting `inRoom(Room(name))` in here breaks no test, and that is the
     * honest state of it — this is the spelling that stays correct if a third kind ever arrives,
     * not a guard that is holding something up now.
     */
    fun inRoomNamed(name: String): List<Registration> = registrations.filter { it.room.name == name }

    /** Whether this room holds anything a type change would take away, the terminal included. */
    fun holdsAnything(name: String): Boolean =
        inRoomNamed(name).isNotEmpty() || terminal?.room?.name == name

    /**
     * Register a scanned card to a room.
     *
     * Re-registering the same id moves it, which is a host correcting themselves mid-walk. A
     * different id carrying an already-registered shape is refused — see
     * [RegisterResult.ShapeAlreadyRegistered]. **The card marked T goes to the terminal and never
     * into [registrations]**, whichever room it is offered to.
     */
    fun register(card: MarkerCard, room: Room): RegisterResult {
        if (room.kind == RoomKind.Stairs) return RegisterResult.StairsHoldNothing(room)
        if (card.isTerminal) return registerTerminal(card, room)

        val existingShape = registrations.firstOrNull {
            it.card.shape.id == card.shape.id && it.card.id != card.id
        }
        if (existingShape != null) return RegisterResult.ShapeAlreadyRegistered(existingShape)

        val existing = registrationOf(card.id)
        val without = registrations.filterNot { it.card.id == card.id }
        val next = HouseMap(without + Registration(card, room), terminal)
        return if (existing == null) RegisterResult.Registered(next)
        else RegisterResult.Moved(next, existing.room)
    }

    /**
     * MOVE THE TERMINAL TO THIS ROOM: the answer to [RegisterResult.TerminalTaken].
     *
     * Separate from [register] because it is a separate act. The host was told what the move costs
     * — the old room is left with no terminal — and said yes; a [register] that quietly moved it
     * would be that question never asked.
     */
    fun moveTerminal(card: MarkerCard, room: Room): RegisterResult {
        if (room.kind == RoomKind.Stairs) return RegisterResult.StairsHoldNothing(room)
        val from = terminal?.room
        val next = HouseMap(registrations, Registration(card, room))
        return if (from == null) RegisterResult.Registered(next)
        else RegisterResult.Moved(next, from)
    }

    /** Forget a card. The host tore one up, or a room went out of play. The T card counts. */
    fun forget(id: MarkerId): HouseMap = HouseMap(
        registrations.filterNot { it.card.id == id },
        terminal?.takeIf { it.card.id != id },
    )

    /** REMOVE THE TERMINAL: the T card belongs to no room, and this home cannot be saved again. */
    fun forgetTerminal(): HouseMap = HouseMap(registrations, null)

    /**
     * Everything in a room stops being registered, the terminal with it.
     *
     * **The other half of D-099.** A room becoming stairs holds nothing afterwards, and that has
     * to happen *as part of* the change rather than in a step that follows it — a flow that
     * retyped the room and then remembered to clear it is a flow that will one day not remember,
     * and the map would keep pointing players at a staircase.
     */
    fun forgetIn(name: String): HouseMap = HouseMap(
        registrations.filterNot { it.room.name == name },
        terminal?.takeIf { it.room.name != name },
    )

    /**
     * The same cards, in a room that has been renamed.
     *
     * A room's name is its identity everywhere else — a card is registered to a [Room], and a
     * [Room] is a name and a kind — so a rename that did not come through here would silently
     * unregister the room's contents. Same for a retype: the kind is carried so the map does not
     * hold a stale opinion about what the plan says a room is.
     */
    fun renamedRoom(from: String, to: Room): HouseMap = HouseMap(
        registrations.map { if (it.room.name == from) Registration(it.card, to) else it },
        terminal?.let { if (it.room.name == from) Registration(it.card, to) else it },
    )

    /**
     * The T card, offered to a room.
     *
     * Three answers and no fourth: nowhere yet, so it lands; already this room, so nothing is at
     * stake and nothing is asked; already somewhere else, so the host is told where and asked.
     * **Never a silent move** — see [RegisterResult.TerminalTaken].
     */
    private fun registerTerminal(card: MarkerCard, room: Room): RegisterResult {
        val at = terminal
        return when {
            at == null -> RegisterResult.Registered(HouseMap(registrations, Registration(card, room)))
            at.room.name == room.name -> RegisterResult.Registered(
                HouseMap(registrations, Registration(card, room))
            )
            else -> RegisterResult.TerminalTaken(at)
        }
    }

    companion object {
        val EMPTY = HouseMap(emptyList(), null)

        /** Rebuild from storage. Order is preserved because order is what was written. */
        fun of(registrations: List<Registration>, terminal: Registration? = null): HouseMap {
            require(terminal == null || terminal.card.isTerminal) {
                "'" + terminal!!.card.id.value + "' is the terminal but is not the card marked T"
            }
            require(registrations.none { it.card.isTerminal }) {
                "the card marked T is registered as an ordinary marker — it never is"
            }
            return HouseMap(registrations.toList(), terminal)
        }
    }
}
