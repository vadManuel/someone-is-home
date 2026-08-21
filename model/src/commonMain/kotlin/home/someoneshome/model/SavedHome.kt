package home.someoneshome.model

/**
 * **One home the host has kept: what they painted, what they registered in it, and its name.**
 *
 * [HousePlan] is what the rooms *are* and [HouseMap] is what is registered where; this is the
 * thing a host actually owns — a named house that survives the evening, sits in a list of others,
 * and can be hosted with, edited, renamed or thrown away. Fifteen minutes of walking a real house
 * in the light, kept forever, which is the whole reason story 0.7 exists.
 *
 * ### The cards are real cards now
 *
 * [map] is what the host scanned: a [MarkerCard] with a printed id, bound to a room, and the one
 * card marked T that says which room is the terminal. The rows used to be shapes with no ids
 * because there was no camera behind them; the format's own note said that when registration
 * landed the rows would be keyed on the card instead, and that it would be a version change. It
 * is, and the header says so.
 *
 * The two rules the plan and the map have to agree about are checked here rather than assumed:
 * every room named by a card is a room in the plan, and **stairs hold nothing** (D-099).
 *
 * ### Equality is the written form, deliberately
 *
 * [HousePlan] is a class with no `equals`, so a `data class` here would compare two identical
 * plans as different and every round-trip test would be asserting object identity while looking
 * like it asserted content. The comparison is spelled out below and is exactly what the file
 * holds: the name, the floors, the cards and the terminal.
 */
class SavedHome(
    val name: String,
    val plan: HousePlan,
    val map: HouseMap = HouseMap.EMPTY,
) {

    init {
        require(name.isNotEmpty()) { "a home with no name" }
        for (registration in map.registrations) {
            requirePainted(registration.room.name, "cards are registered in")
        }
        map.terminal?.let { requirePainted(it.room.name, "the terminal is in") }
    }

    val floorCount: Int get() = plan.floors.size
    val roomCount: Int get() = plan.rooms.size
    val markerCount: Int get() = map.registrations.size

    /** The one room holding the T card, by name — what every screen about the terminal asks for. */
    val terminal: String? get() = map.terminal?.room?.name

    fun markersIn(room: String): List<MarkerShape> =
        map.inRoomNamed(room).map { it.card.shape }

    fun renamedTo(to: String): SavedHome = SavedHome(to, plan, map)

    /**
     * A room named by a card or by the terminal has to be a room in this home, and it cannot be
     * stairs.
     *
     * The same last-ditch guarantee [Registration]'s `init` is, in the same words: a registration
     * into stairs cannot exist, because stairs holding nothing is what makes the stairwell
     * invisible to the Terminal. Loud, because this is host-side setup in the light.
     */
    private fun requirePainted(room: String, what: String) {
        val painted = plan.roomNamed(room)
        require(painted != null) { "'$name' says $what '$room', which is not a room in it" }
        require(painted.kind != RoomKind.Stairs) {
            "'$name' says $what '$room', which is stairs — stairs hold nothing"
        }
    }

    override fun equals(other: Any?): Boolean = other is SavedHome &&
        name == other.name &&
        plan.floors == other.plan.floors &&
        map.registrations == other.map.registrations &&
        map.terminal == other.map.terminal

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + plan.floors.hashCode()
        result = 31 * result + map.registrations.hashCode()
        result = 31 * result + map.terminal.hashCode()
        return result
    }

    override fun toString(): String =
        "SavedHome($name, ${floorCount}fl, ${roomCount}rm, ${markerCount}mk, terminal=$terminal)"
}
