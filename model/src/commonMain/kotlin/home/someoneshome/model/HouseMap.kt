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
class HouseMap private constructor(val registrations: List<Registration>) {

    val rooms: List<Room> get() = registrations.map { it.room }.distinct()

    fun registrationOf(id: MarkerId): Registration? =
        registrations.firstOrNull { it.card.id == id }

    /** Every registered card in a room, in registration order. */
    fun inRoom(room: Room): List<Registration> = registrations.filter { it.room == room }

    /**
     * Register a scanned card to a room.
     *
     * Re-registering the same id moves it, which is a host correcting themselves mid-walk. A
     * different id carrying an already-registered shape is refused — see
     * [RegisterResult.ShapeAlreadyRegistered].
     */
    fun register(card: MarkerCard, room: Room): RegisterResult {
        if (room.kind == RoomKind.Stairs) return RegisterResult.StairsHoldNothing(room)
        val existingShape = registrations.firstOrNull {
            it.card.shape.id == card.shape.id && it.card.id != card.id
        }
        if (existingShape != null) return RegisterResult.ShapeAlreadyRegistered(existingShape)

        val existing = registrationOf(card.id)
        val without = registrations.filterNot { it.card.id == card.id }
        val next = HouseMap(without + Registration(card, room))
        return if (existing == null) RegisterResult.Registered(next)
        else RegisterResult.Moved(next, existing.room)
    }

    /** Forget a card. The host tore one up, or a room went out of play. */
    fun forget(id: MarkerId): HouseMap = HouseMap(registrations.filterNot { it.card.id == id })

    companion object {
        val EMPTY = HouseMap(emptyList())

        /** Rebuild from storage. Order is preserved because order is what was written. */
        fun of(registrations: List<Registration>): HouseMap = HouseMap(registrations.toList())
    }
}
