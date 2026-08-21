package home.someoneshome.model

/**
 * **One home the host has kept: what they painted, what they registered in it, and its name.**
 *
 * [HousePlan] is what the rooms *are* and [HouseMap] is what is registered where; this is the
 * thing a host actually owns — a named house that survives the evening, sits in a list of others,
 * and can be hosted with, edited, renamed or thrown away. Fifteen minutes of walking a real house
 * in the light, kept forever, which is the whole reason story 0.7 exists.
 *
 * ### The cards here are the editor's fixture, and the format version is how they stop being one
 *
 * [markers] is room name to the shapes registered in it, and [terminal] is the one room holding
 * the T card. Registering a card is story 4.5 and there is no camera behind it yet, so what is
 * stored is *which room holds what* — the half that is already real and already load-bearing,
 * because **stairs hold nothing** (D-099) and a home with no terminal cannot be played. When 4.5
 * lands, a card is a [MarkerCard] with an id and these rows are keyed on that id instead of on the
 * shape; that is a format change, and the header carries a version number so it is one.
 *
 * ### A room with no cards writes no row, and an empty list is the same fact
 *
 * `markersIn` is how the contents of a room are asked for, so `KITCHEN to emptyList()` and no
 * KITCHEN entry at all are the same answer. The writer emits nothing for the first, which is why
 * a home carrying one comes back without it and is still equal to what went in.
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
    markers: Map<String, List<MarkerShape>> = emptyMap(),
    val terminal: String? = null,
) {

    /** Empty entries dropped, so the map is what the file holds and nothing else. */
    val markers: Map<String, List<MarkerShape>> = markers.filterValues { it.isNotEmpty() }

    init {
        require(name.isNotEmpty()) { "a home with no name" }
        for (room in this.markers.keys) requirePainted(room, "cards are registered in")
        if (terminal != null) requirePainted(terminal, "the terminal is in")
    }

    val floorCount: Int get() = plan.floors.size
    val roomCount: Int get() = plan.rooms.size
    val markerCount: Int get() = markers.values.sumOf { it.size }

    fun markersIn(room: String): List<MarkerShape> = markers[room].orEmpty()

    fun renamedTo(to: String): SavedHome = SavedHome(to, plan, markers, terminal)

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
        markers == other.markers &&
        terminal == other.terminal

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + plan.floors.hashCode()
        result = 31 * result + markers.hashCode()
        result = 31 * result + terminal.hashCode()
        return result
    }

    override fun toString(): String =
        "SavedHome($name, ${floorCount}fl, ${roomCount}rm, ${markerCount}mk, terminal=$terminal)"
}
