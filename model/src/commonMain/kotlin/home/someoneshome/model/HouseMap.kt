package home.someoneshome.model

/** A room in the house, named by the host during the setup walk. */
data class Room(val name: String)

/** One card, bound to one room. No card is meaningful until it has one of these (D-069). */
data class Registration(val card: MarkerCard, val room: Room)

/** What happened when a card was offered to the map. */
sealed interface RegisterResult {
    data class Registered(val map: HouseMap) : RegisterResult

    /** The same id, now in a different room. The host moved a card; that is allowed. */
    data class Moved(val map: HouseMap, val from: Room) : RegisterResult

    /**
     * A different card already carries this shape.
     *
     * **Refused, and this is a judgement call rather than a decided one.** The shape is the
     * marker's whole name — `MARKER 07` is gone from every screen (D-069) — so two registered
     * cards showing the same shape give the house two markers with one name, and a player told
     * to go to the diamond has two places to stand. Refusing is the fail-closed direction: the
     * host sees it during the setup walk, when a card can still be reprinted, rather than a
     * player discovering it in the dark.
     *
     * The alternative — allow it, and let the id disambiguate internally — keeps the data honest
     * and pushes an ambiguity onto people who cannot see ids. **Raised rather than settled.**
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
