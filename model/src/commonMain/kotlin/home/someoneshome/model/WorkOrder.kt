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
@ClientFacing
@kotlinx.serialization.Serializable
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
 * **One line of a work order, authority-side: what was assigned, where, and what unblocks it.**
 *
 * Ground truth, with no wire encoding and no `copy()` — the same posture [OpenSubroutine] holds,
 * for the same reason. What a client receives is [OrderLine], which carries the destination and is
 * physically incapable of holding the answer key or the dependency graph.
 *
 * ### The answer is NOT here, and that is E-L3-2's ruling
 *
 * An entry is an **assignment**: this Subroutine, at this card, behind this dependency. The
 * *question* — band position, speed, phase, path, the hidden duration — and the answer it grades
 * against are drawn by the **scan** and re-drawn by every re-scan (D-139, D-140), and they live
 * on [OpenSubroutine] for exactly as long as one instance lasts. An answer key stapled to the
 * assignment would make a retry a second run at a picture the player has already memorised, which
 * is the thing both rulings exist to prevent.
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
    /**
     * What this entry is: one of the roster's ten, or the Array Wipe circuit. See [AssignedWork],
     * which carries why a circuit is a second case rather than an eleventh [SubroutineKind].
     */
    val work: AssignedWork,
    /**
     * **The cards this entry is done at, in draw order** — one for a Subroutine, the circuit's
     * three stations for an Array Wipe (D-122).
     *
     * **A card is a place, not a container** (D-123): the house resolves `(seat, card)` to *that
     * player's* current Subroutine, so one card can be a station and two players' anchors at the
     * same time.
     *
     * A list rather than a card plus a separate station list, because two collections holding the
     * same kind of fact are two collections a build can de-synchronise — and the one that went
     * stale would be the one telling somebody which room to walk to in the dark.
     */
    val markers: List<MarkerId>,
    /** Earlier entries that must be done first. Empty for an entry that is actionable at once. */
    val blockedBy: List<Int>,
    val done: Boolean,
) {
    /** Where this entry begins. Null only for an entry with nowhere to be, which no draw makes. */
    val anchor: MarkerId? get() = markers.firstOrNull()

    fun isAt(marker: MarkerId): Boolean = markers.any { it.value == marker.value }

    fun completed(): OrderEntry = OrderEntry(index, work, markers, blockedBy, true)
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
     *
     * ### A circuit line is stepped over rather than returned, and it is worth saying why
     *
     * [AssignedWork.ArrayWipe] has no [AssignedWork.opensAs], because the carried state a circuit
     * leg is graded against is not built (F-014, E7). Skipping it means a later Subroutine at the
     * same station still opens, which is the better of the two wrong answers: the alternative is a
     * circuit line standing in front of real work and turning a card into a blind alley.
     *
     * **It is unreachable today** — the draw deals no circuit — and it is the reason the format
     * being able to carry one is not the same as the round being able to contain one. Recorded
     * here rather than left for somebody to find at the station.
     */
    fun openAt(marker: MarkerId): OrderEntry? =
        entries.firstOrNull { it.isAt(marker) && it.work.opensAs != null && isActionable(it) }

    fun withCompleted(index: Int): WorkOrder =
        WorkOrder(seat, entries.map { if (it.index == index) it.completed() else it })

    /**
     * **The order as its own player's phone sees it** — narrowing, never nulling (rule 3).
     *
     * Same length as the authority order, always: a blocked entry is drawn as a **known unknown**
     * rather than dropped, because an order that shortened while work was blocked would make its
     * own length a tell about what the house had drawn (D-114).
     *
     * ### [geography] is the home, and what leaves here is one seat's rows of it
     *
     * The whole marker → room table is passed in and **only the cards this order actually names
     * come out**. That is the narrowing, and it is the one authorised by revision 34: a Resident's
     * own destinations must be nameable, and the home's full geography on every phone is not.
     * Doing the selection here rather than at a call site means every producer of a client order —
     * arming, and every hand-over — narrows by the same rule rather than by two copies of it.
     *
     * **A card with no row in [geography] contributes no destination**, so an order drawn in a
     * home the caller did not describe comes out with nothing in the destination column rather
     * than with a room name somebody invented. Fail-closed, and visible on every row at once.
     *
     * **A blocked line still carries nothing**, geography included. The blackout is structural: an
     * entry whose *name* is withheld but whose *room* is published names the person upstream of
     * you by where they are standing (D-146 made dependencies cross-player), so the room would
     * hand back exactly what the type was split to withhold.
     */
    fun asLines(geography: List<Destination>): List<OrderLine> = entries.map { entry ->
        if (isActionable(entry) || entry.done) {
            OrderLine.Known(
                index = entry.index,
                work = entry.work,
                done = entry.done,
                at = entry.markers.mapNotNull { m ->
                    geography.firstOrNull { it.marker.value == m.value }
                },
            )
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
 * **A known line carries where it is and a blocked one carries nothing at all**, and the asymmetry
 * is the whole of the type. A destination on a blocked line would publish the room another
 * player's work is in — dependencies are cross-player (D-146) — so the blackout has to cover the
 * place as well as the name, and it does so by there being no field.
 */
@ClientFacing
@kotlinx.serialization.Serializable
sealed interface OrderLine {
    val index: Int

    /**
     * Actionable, or already done. Carries the kind, which is all D-112 sends: the client looks
     * the name and the light signature up in its own roster (D-106 — the signature shows on the
     * work-order list, the springboard widget and the Subroutine screen alike).
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    data class Known(
        override val index: Int,
        val work: AssignedWork,
        val done: Boolean,
        /**
         * **Where this line is: the room and the glyph, which is what a player navigates by.**
         *
         * One destination for a Subroutine and three for the Array Wipe circuit, in draw order
         * (D-122) — so the list is the shape of the work rather than a nullable field that happens
         * to be filled twice.
         *
         * **Empty is lawful and means the house said nothing**, which is what a round armed with
         * no geography produces. A screen draws no destination there rather than inventing one:
         * a room name beside work the house chose, in a dark house, is a lie a player has no way
         * to check.
         *
         * Only this seat's own order's rooms are ever in here. See [WorkOrder.asLines].
         */
        val at: List<Destination>,
    ) : OrderLine

    /**
     * **A known unknown**: something is there, and that is the whole of what the player learns —
     * not its name, not its signature, **not where it is** — until it unblocks (D-114).
     *
     * Not absent, which would shorten the order and make its length a tell. Not spelled out, which
     * would hand the player a route they have not earned yet.
     *
     * **One field, and it must never gain a second.** A room here would be the leak in its most
     * plausible costume: it names no Subroutine, so it reads as harmless, and it points at the
     * card the person upstream of you is standing at.
     */
    @ClientFacing
    @kotlinx.serialization.Serializable
    data class Blocked(override val index: Int) : OrderLine
}
