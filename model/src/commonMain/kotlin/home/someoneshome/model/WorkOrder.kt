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
 * specified and all ten are built. A kind whose screen does not exist yet is still a lawful thing for the
 * house to assign — the roster is the design's, not the build's — and `ui` is where the two are
 * reconciled.
 */
@ClientFacing
@kotlinx.serialization.Serializable
enum class SubroutineKind(
    /**
     * **Which of M4's structures this kind populates** (`gdd.md:256`, the roster table at
     * `gdd.md:600`). The roster's own column, carried into the type so that the mix draw
     * ([Balance.orderMix]) and the table cannot disagree: six of the ten are short and four are
     * medium, as the roster rates them, and D-137 to D-140 rewrote four of the ten without moving
     * a single one between structures.
     *
     * The circuit is not a kind (see [AssignedWork.ArrayWipe]), so no kind is [SubroutineStructure.Circuit].
     */
    val structure: SubroutineStructure,
) {
    Replay(SubroutineStructure.Short),
    Interrupt(SubroutineStructure.Short),
    ParityCheck(SubroutineStructure.Short),
    Sniff(SubroutineStructure.Short),
    Deallocate(SubroutineStructure.Short),
    Drift(SubroutineStructure.Medium),
    Short(SubroutineStructure.Short),
    SignalTrace(SubroutineStructure.Medium),
    Jam(SubroutineStructure.Medium),
    Handshake(SubroutineStructure.Medium),

    /**
     * **Memory Dump, the long structure's one Subroutine** (`gdd.md:640`, D-322).
     *
     * Two stages at two different markers: the source shows a pattern of four or five elements,
     * and the target, a different card the order names, takes it back from memory. Only ever dealt
     * as [AssignedWork.MemoryDump], whose two cards are the source and the target; never as
     * [AssignedWork.Subroutine], which names one card.
     *
     * ⚠️ **Its two screens, its briefing and its light signature are STOPPED for the owner**
     * (D-113: *Memory Dump is rated when it is built*; D-188, D-194: every Subroutine is briefed at
     * the card, and no line exists for this one). The house deals it, grades it and carries its
     * pattern; no phone can open it yet.
     */
    MemoryDump(SubroutineStructure.Long),
    ;

    companion object {
        /** The kinds the house may deal into [structure], in roster order. */
        fun dealtAs(structure: SubroutineStructure): List<SubroutineKind> =
            entries.filter { it.structure == structure }
    }
}

/**
 * **M4's four structures: how a Subroutine is scheduled and where it sits in space**
 * (`gdd.md:256`).
 *
 * Short and medium are one marker each; **long** is two stages at two markers (Memory Dump);
 * **circuit** is three markers and one carried state (the Array Wipe, D-164). The work-order mix
 * is written in these ([Balance.orderMix]), never in kinds.
 */
enum class SubroutineStructure { Short, Medium, Long, Circuit }

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
    /**
     * **Held by another seat's work** (D-146, D-322): true from arming until the house releases it,
     * which it does a random 20–120 s after the upstream entry is completed (`gdd.md:284`), and
     * never otherwise. **Nothing auto-satisfies, ever** — a Revoke that takes the upstream seat out
     * leaves this true for the rest of the round, and the entry stays a blocked line (D-114).
     *
     * Which seat holds it is not here and is never on any type a client can see: the link lives on
     * `GameState.links`, authority-side.
     */
    val held: Boolean = false,
    /**
     * **How many damaged disks this Array Wipe has degaussed** (`gdd.md:660`). Zero for every other
     * work; the circuit is done at [Balance.ARRAY_WIPE_DISKS].
     */
    val wiped: Int = 0,
) {
    /** Where this entry begins. Null only for an entry with nowhere to be, which no draw makes. */
    val anchor: MarkerId? get() = markers.firstOrNull()

    fun isAt(marker: MarkerId): Boolean = markers.any { it.value == marker.value }

    fun completed(): OrderEntry = OrderEntry(index, work, markers, blockedBy, true, held, wiped)

    /** Another seat's dependency, released (D-146). Only the house's unlock calls this. */
    fun released(): OrderEntry = OrderEntry(index, work, markers, blockedBy, done, false, wiped)

    /** One more damaged disk degaussed at Disposal. */
    fun wipedOne(): OrderEntry = OrderEntry(index, work, markers, blockedBy, done, held, wiped + 1)
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

    /**
     * Done, or blocked by something that is not — an earlier entry of this order, or another seat's
     * work holding it ([OrderEntry.held]). The player can act on everything else.
     */
    fun isActionable(entry: OrderEntry): Boolean =
        !entry.done && !entry.held && entry.blockedBy.all { i -> entries.getOrNull(i)?.done == true }

    /**
     * **The first actionable entry that BEGINS at [marker]**, for a seat carrying nothing, or null
     * for D-124's NOTHING FOR YOU HERE.
     *
     * The **first** actionable entry at that card, in order, which is what makes a self-chain walk
     * itself: an order deeper than the home's marker count visits some cards twice, and the second
     * visit is blocked by the first until the first is done (D-123).
     *
     * ### Where each kind of work begins (D-322)
     *
     * Every [AssignedWork] begins at its first card: a Subroutine at its one card, a Memory Dump at
     * its **source** (the pattern), an Array Wipe at **Spares** (the first good disk). A Memory
     * Dump's *target* and the circuit's Rack and Disposal are steps that depend on what the seat is
     * carrying, which this type does not hold, so the rules resolve those (`core`'s scan
     * resolution) and ask this for the empty-handed case only.
     */
    fun openAt(marker: MarkerId): OrderEntry? =
        entries.firstOrNull { it.anchor?.value == marker.value && isActionable(it) }

    fun withCompleted(index: Int): WorkOrder =
        WorkOrder(seat, entries.map { if (it.index == index) it.completed() else it })

    /** [index]'s cross-player hold released (D-146). */
    fun withReleased(index: Int): WorkOrder =
        WorkOrder(seat, entries.map { if (it.index == index) it.released() else it })

    /** One disk more degaussed on the circuit at [index]. */
    fun withWiped(index: Int): WorkOrder =
        WorkOrder(seat, entries.map { if (it.index == index) it.wipedOne() else it })

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
    fun asLines(geography: List<Destination>, patterns: List<Int> = emptyList()): List<OrderLine> = entries.map { entry ->
        if (isActionable(entry) || entry.done) {
            OrderLine.Known(
                index = entry.index,
                work = entry.work,
                done = entry.done,
                at = entry.markers.mapNotNull { m ->
                    geography.firstOrNull { it.marker.value == m.value }
                },
                // [patterns] is the house's list of the Memory Dump lines whose pattern it holds for
                // this seat (D-325); every other line is 0, on the wire all the same.
                pattern = if (entry.work == AssignedWork.MemoryDump && entry.index in patterns) 1 else 0,
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
        /**
         * **1 while the house holds this Memory Dump line's pattern, 0 otherwise** (D-325), so the
         * row reads TO <ROOM> with the target's glyph between BEGIN at the source and the hand-over
         * at the target. Never the pattern itself, which is in no client type (F-015).
         *
         * **One digit on every known line, always encoded**, so an order is the same width whether
         * or not a pattern is held and whatever the line is: the smallest same-width addition. It
         * rides `WorkOrderIssued`, whose schema row (this seat, the living classes) is unchanged.
         */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        @kotlinx.serialization.EncodeDefault
        val pattern: Int = 0,
    ) : OrderLine {
        init {
            require(pattern == 0 || pattern == 1) { "a line's pattern digit is 0 or 1, never $pattern" }
        }
    }

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
