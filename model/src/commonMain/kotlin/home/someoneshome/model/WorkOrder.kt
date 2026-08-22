package home.someoneshome.model

/**
 * **Which Subroutine, and nothing else about it** (D-112).
 *
 * The light signature is *not* here, and that is the ruling rather than an omission. In v1 the
 * light level is fixed per Subroutine kind, **the client holds the roster**, and the house sends
 * only which one — so no field arrives, no effect grows a member, and the redaction schema gains
 * no row. Per-assignment variety is explicitly deferred to v2. It is the wire that costs
 * something, and nothing in the design needs that wire yet.
 *
 * The names are the roster's own (`gdd.md:563`–`:588`, as superseded by revision 31). All ten are
 * specified; six are built. A kind whose screen does not exist yet is still a lawful thing for the
 * house to assign — the roster is the design's, not the build's — and `ui` is where the two are
 * reconciled.
 */
enum class SubroutineKind {
    Replay,
    Interrupt,
    ParityCheck,
    Sniff,
    Deallocate,
    Drift,
    Short,
    SignalTrace,
    Jam,
    Handshake,
}

/**
 * **One line of a work order, authority-side: what was assigned, where, what unblocks it, and the
 * answer.**
 *
 * Carries [expected], so it is ground truth of the strongest kind and has no wire encoding and no
 * `copy()` — the same posture [OpenSubroutine] holds, for the same reason. What a client receives
 * is [OrderLine], which is physically incapable of holding an answer key.
 *
 * ### [blockedBy] holds earlier indices only, and that is what keeps the bar reachable
 *
 * Every dependency points backwards down the order, so the graph is acyclic **by construction**
 * rather than by a check somebody remembers to run. A cycle here would be an order that can never
 * be finished, on a meter that has to be finishable for the Residents to have a win condition at
 * all (D-131) — and it would be invisible until a playtest stalled.
 */
class OrderEntry(
    /** Position in the order, and this entry's identity within it. Stable for the round. */
    val index: Int,
    val subroutine: SubroutineKind,
    /**
     * The card this entry is anchored at. **A card is a place, not a container** (D-123): the
     * house resolves `(seat, card)` to *that player's* current Subroutine, so one card can be a
     * station and two players' anchors at the same time.
     */
    val marker: MarkerId,
    /** What the house will grade against, in [OpenSubroutine]'s one canonical shape. */
    val expected: List<Int>,
    /** Earlier entries that must be done first. Empty for an entry that is actionable at once. */
    val blockedBy: List<Int>,
    val done: Boolean,
) {
    fun completed(): OrderEntry = OrderEntry(index, subroutine, marker, expected, blockedBy, true)
}

/**
 * **A seat's whole work order — a menu, not a queue** (D-114).
 *
 * The player chooses among whatever is currently actionable and sequencing emerges from the
 * dependency graph rather than from an imposed order. That is what makes D-106's always-visible
 * light signature a *decision surface*: with two actionable Subroutines and one of them bright,
 * the player is choosing how visible to be for the next ninety seconds, in a house where being
 * seen is the entire risk.
 *
 * **Every order is [Balance.orderSize] long, for every seat and both roles.** An Insider's is a
 * fake drawn from the same rule at the same length — role-independent by construction (D-129), so
 * there is no length to compare and nothing to divide the hidden count out of.
 */
class WorkOrder(val seat: Seat, val entries: List<OrderEntry>) {

    /** Done, or blocked by something that is not. The player can act on everything else. */
    fun isActionable(entry: OrderEntry): Boolean =
        !entry.done && entry.blockedBy.all { i -> entries.getOrNull(i)?.done == true }

    /**
     * What a scan of [marker] opens for this seat, or null for D-124's NOTHING FOR YOU HERE.
     *
     * The **first** actionable entry at that card, in order, which is what makes a self-chain walk
     * itself: an order deeper than the home's marker count visits some cards twice, and the second
     * visit is blocked by the first until the first is done (D-123).
     */
    fun openAt(marker: MarkerId): OrderEntry? =
        entries.firstOrNull { it.marker.value == marker.value && isActionable(it) }

    fun withCompleted(index: Int): WorkOrder =
        WorkOrder(seat, entries.map { if (it.index == index) it.completed() else it })

    /**
     * **The order as its own player's phone sees it** — narrowing, never nulling (rule 3).
     *
     * Same length as the authority order, always: a blocked entry is drawn as a **known unknown**
     * rather than dropped, because an order that shortened while work was blocked would make its
     * own length a tell about what the house had drawn (D-114).
     */
    fun asLines(): List<OrderLine> = entries.map { entry ->
        if (isActionable(entry) || entry.done) {
            OrderLine.Known(entry.index, entry.subroutine, entry.done)
        } else {
            OrderLine.Blocked(entry.index)
        }
    }
}

/**
 * **One line of a work order as the player holding it may see it** (D-114).
 *
 * Two cases and no third, and the split is the redaction. A blocked entry's line is a different
 * *type* rather than the same type with its name blanked out, so nothing downstream can put a name
 * on it by populating a field: **the type is the field list** (rule 3). A nulled `subroutine`
 * would have been the same disclosure one refactor away.
 *
 * Neither case carries the card, and that is deliberate. Where the work is, is what walking the
 * house in the dark is for; the house answers a scan and never publishes a map of the round.
 */
@ClientFacing
sealed interface OrderLine {
    val index: Int

    /**
     * Actionable, or already done. Carries the kind, which is all D-112 sends: the client looks
     * the name and the light signature up in its own roster (D-106 — the signature shows on the
     * work-order list, the springboard widget and the Subroutine screen alike).
     */
    data class Known(
        override val index: Int,
        val subroutine: SubroutineKind,
        val done: Boolean,
    ) : OrderLine

    /**
     * **A known unknown**: something is there, and that is the whole of what the player learns —
     * not its name, not its signature — until it unblocks (D-114).
     *
     * Not absent, which would shorten the order and make its length a tell. Not spelled out, which
     * would hand the player a route they have not earned yet.
     */
    data class Blocked(override val index: Int) : OrderLine
}
