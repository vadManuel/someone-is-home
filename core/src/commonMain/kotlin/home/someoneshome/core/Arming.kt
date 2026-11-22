package home.someoneshome.core

import home.someoneshome.model.AssignedWork
import home.someoneshome.model.Balance
import home.someoneshome.model.ChainLink
import home.someoneshome.model.ContactSeed
import home.someoneshome.model.Destination
import home.someoneshome.model.Event
import home.someoneshome.model.Hmac
import home.someoneshome.model.HomePlanView
import home.someoneshome.model.InsiderBand
import home.someoneshome.model.MarkerId
import home.someoneshome.model.OrderEntry
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.SubroutineStructure
import home.someoneshome.model.Tick
import home.someoneshome.model.WorkOrder

/*
 * **What the house draws when the lights go out.**
 *
 * Four draws -- the Insider count and who holds it, the Array Wipe's three stations, the round's
 * active marker set, and every seat's work order -- and all four come off the arming event's seed.
 * Nothing here reads a clock, a platform, or a random source the recording does not hold: a round
 * that drew differently on replay would take the one debugging instrument eight phones in a dark
 * house have and make it lie.
 *
 * ### Why one of them is drawn here and three are drawn inside `reduce`
 *
 * `insidersFor` is called ABOVE the rules and its result is written into
 * `Event.RoundArmed.insiders`, so the draw is a recorded input. The other three are re-derived
 * from the same seed every time the event is reduced, so they are recorded only as consequences.
 *
 * The asymmetry is deliberate and has one reason: **the differential harness works by rewriting
 * the arming event's Insider list.** A count drawn inside the rules could not be exchanged between
 * two seats, so the harness would lose the only handle it has on role -- and the harness is one of
 * the three independent instruments the leak surfaces are checked with. Stations and work orders
 * are role-independent by construction, so nothing needs to rewrite them.
 */

/**
 * **LIGHTS OUT, as the one event that starts a round** (D-128, D-103, D-122).
 *
 * The host presses the button, the house draws the count, and this is the fact that comes out. It
 * is the only place a `RoundArmed` should be built outside a test: everything the round is made of
 * is either on it or derived from its seed, so a second construction site would be a second set of
 * rules about what a round is.
 *
 * **The party floor is enforced here, loudly, and that is deliberate.** Rule 6 keeps errors silent
 * to a player *mid-round*; this is pre-arm, on the host's own phone, in a lit room, with the whole
 * party standing there — and a round that armed four people would be an evening nobody could win
 * discovered forty minutes in. The lobby's own gate (`LobbyDesk.readyToArm`) is what stops this
 * ever being reached; the check here is what stops a second caller getting it wrong quietly.
 *
 * Balance values lock at this moment and stamp into the recording. No mid-round edits.
 */
fun armingFor(
    at: Tick,
    seed: Long,
    seats: List<Seat>,
    chosenInsiders: Int?,
    markers: List<Destination>,
    /**
     * The host's ballot length, off the lobby's own settings row (D-117). Defaulted to the
     * design's forty-five so a caller exercising something else does not have to invent a ballot.
     */
    voteWindowSeconds: Int = Balance.VOTE_WINDOW_SECONDS,
    /**
     * What each seat asked to be called, off the desk (D-115). Defaulted empty like the lines:
     * a round armed to exercise a meeting does not have to invent six people to hold it with.
     */
    names: List<SeatNamed> = emptyList(),
    /**
     * The home's T card, off its map (D-199). Defaulted null for [names]' reason: a round armed to
     * exercise something else has no station, and every scan in it is work.
     */
    terminal: MarkerId? = null,
    /** The home's plan, narrowed (O2). Defaulted empty for [names]' reason. */
    plan: HomePlanView = HomePlanView.EMPTY,
    /** The T card's room, the house's copy (O2). Defaulted null for [terminal]'s reason. */
    terminalRoom: String? = null,
    /**
     * The seat the host's own phone holds (D-289). Kept only if it is one of [seats]; defaulted null,
     * which names no out seat on the notice (R7, D-242).
     */
    host: Seat? = null,
    /**
     * The host's DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN rows, in seconds, off the lobby's
     * desk (D-317). Defaulted to their `Balance` numbers for [voteWindowSeconds]' reason.
     */
    discussionSeconds: Int = Balance.DISCUSSION_SECONDS,
    egressTimerSeconds: Int = Balance.EGRESS_TIMER_SECONDS,
    revokeCooldownSeconds: Int = Balance.REVOKE_COOLDOWN_SECONDS,
): Event.RoundArmed {
    require(seats.size >= Balance.MINIMUM_SEATS) {
        "a round cannot be armed for ${seats.size} seats: the minimum party is " +
            "${Balance.MINIMUM_SEATS} (D-128), and below it one wrong Restrain reaches parity"
    }
    return Event.RoundArmed(
        at = at,
        seed = seed,
        seats = seats.sortedBy { it.index },
        insiders = insidersFor(seed, seats, chosenInsiders),
        // The setting travels beside the draw, because the work order is sized off the setting and
        // never off the draw (D-129). Two fields rather than one is the whole of that guarantee.
        chosenInsiders = InsiderBand.clamp(seats.size, chosenInsiders),
        markers = markers,
        voteWindowSeconds = voteWindowSeconds,
        names = names,
        terminal = terminal,
        plan = plan,
        terminalRoom = terminalRoom,
        host = host?.takeIf { h -> seats.any { it.index == h.index } },
        discussionSeconds = discussionSeconds,
        egressTimerSeconds = egressTimerSeconds,
        revokeCooldownSeconds = revokeCooldownSeconds,
    )
}

/**
 * **The Insider draw, clamped by the band at the draw and not only at the setting** (D-103, as
 * amended by revision 29).
 *
 * [chosen] is the host's setting, or null for UNKNOWN — the default, which is what makes hiding
 * the count possible at all. Either way the answer lands inside [InsiderBand.of]: a host cannot
 * hand-pick a count outside the band any more than UNKNOWN can land on one. **At five and six
 * seats the band has exactly one member**, so a five-seat round can never draw two Insiders, which
 * is the arithmetic D-131's parity rule forces.
 *
 * Who holds it is a seeded shuffle over the seats and nothing else. There is no weighting, no
 * memory of who was an Insider last round, and no input from anything a player did — every one of
 * those would be a pattern somebody could learn to read across an evening.
 */
fun insidersFor(seed: Long, seats: List<Seat>, chosen: Int?): List<Seat> {
    val band = InsiderBand.of(seats.size)
    if (band.isEmpty()) return emptyList()
    val count = InsiderBand.clamp(seats.size, chosen)
        ?: (band.first + Draw(seed, SeedPurpose.InsiderCount).next(band.last - band.first + 1))
    return seats.shuffledBy(Draw(seed, SeedPurpose.InsiderSeats)).take(count).sortedBy { it.index }
}

/**
 * **Spares, Rack and Disposal, drawn from the ordinary registered markers** (D-122, D-120).
 *
 * Three distinct cards, in draw order, and the host designates nothing. They reserve no shapes —
 * D-120 answered that half of E5-1 with a no — so any ordinary marker can be any station, and
 * **the circuit moves every round**: a home that remembered where the Rack was would turn it into
 * a fact players learn once and keep.
 *
 * Distinct by card rather than by position, so this function's output is distinct even where its
 * input was not. Registration already refuses a duplicate (D-086); this does not depend on it.
 */
internal fun stationsFor(seed: Long, markers: List<MarkerId>): List<MarkerId> =
    markers.distinctBy { it.value }.shuffledBy(Draw(seed, SeedPurpose.Stations)).take(Balance.STATIONS)

/**
 * **The round's active set: the stations, plus as many more markers as the party is sized for**
 * (D-123).
 *
 * *A registered marker is a slot the house may use, not work that must be done.* What is left over
 * **sits dark this round** and is drawn again next round.
 *
 * The stations are in it by definition — they are cards this round is using — and they stay
 * eligible as anchors, because *a card is a place, not a container*: one card can be a station and
 * two players' anchors at the same time.
 */
internal fun activeFor(
    seed: Long,
    markers: List<MarkerId>,
    stations: List<MarkerId>,
    seats: Int,
): List<MarkerId> {
    val all = markers.distinctBy { it.value }
    if (all.isEmpty()) return emptyList()
    val wanted = maxOf(Balance.activeMarkers(seats, all.size), stations.size)
    val rest = all.filterNot { m -> stations.any { it.value == m.value } }
    return stations + rest.shuffledBy(Draw(seed, SeedPurpose.Active)).take(wanted - stations.size)
}

/**
 * **One seat's work order: [size] entries in the design's mix, anchored round-robin through a
 * shuffled active set** (D-129, D-123, D-114, D-322).
 *
 * ### The size is public, the mix is the size's, and the order is per seat
 *
 * [size] is [Balance.orderSize], computed from public lobby facts alone, so **every order in the
 * round is the same length** — the Insider's fake included, which is what makes length
 * role-independent by construction rather than by a rule somebody keeps applying. **The mix is
 * [Balance.orderMix] of that size** (the owner's 2026-11-21 ruling: *scale the proportions*), so
 * every order also holds the same number of each structure. What is per seat is which short and
 * medium kinds were drawn, where in the order each structure sits, and the anchors — so that two
 * players do not walk the same route in the same order, which would turn congestion from content
 * into a queue.
 *
 * ### What each structure is dealt
 *
 * - **short, medium** — one of that structure's kinds ([SubroutineKind.dealtAs]), at one card;
 * - **long** — a Memory Dump at **two different cards**, the source and then the target
 *   (`gdd.md:640`: *a different, specified marker*), the next two anchors in the walk;
 * - **circuit** — the Array Wipe at the round's three [stations], in D-122's draw order (Spares,
 *   Rack, Disposal). Every circuit in the round shares the one Rack (`gdd.md:672`).
 *
 * **A home too small for a structure deals a short in its place**: a circuit needs three stations
 * and a Memory Dump two distinct cards, and a home with fewer cannot hold them. D-128's minimum
 * home is eight markers, so this is unreachable in a home that passed review; it is the fail-closed
 * answer for a hand-built round, and the length still says [size].
 *
 * ### Round-robin is what makes a self-chain happen, and only when it has to
 *
 * Anchors are handed out by walking the shuffled markers in order and wrapping. An order no deeper
 * than the active set therefore gets **distinct** anchors and is a flat menu; an order deeper than
 * the active set wraps, and the second visit to a card is blocked by the first (D-123's
 * blocked-by-your-own-work, discovered as the player completes rather than announced in advance).
 * An entry at several cards is blocked by the most recent earlier entry at each of them.
 *
 * **The dependency always points backwards**, so an order can always be finished — see
 * [OrderEntry.blockedBy]. The cross-player holds are added after, by [linksFor].
 *
 * ### `Role` does not appear in this function, and that is load-bearing
 *
 * An Insider's fake is drawn by this rule, at this length, from this seed. A derivation that took
 * the role would put the answer key itself on the role axis, and the differential harness would
 * start reporting the game's central secret as a divergence it could not explain.
 */
internal fun workOrderFor(
    seed: Long,
    seat: Seat,
    size: Int,
    active: List<MarkerId>,
    stations: List<MarkerId> = emptyList(),
): WorkOrder {
    if (active.isEmpty() || size <= 0) return WorkOrder(seat, emptyList())
    val anchors = active.shuffledBy(Draw(seed, SeedPurpose.Anchors, seat.index.toLong()))
    val kinds = Draw(seed, SeedPurpose.Kinds, seat.index.toLong())
    val structures = structuresFor(size, circuit = stations.size >= Balance.STATIONS, long = anchors.size >= 2)
        .shuffledBy(Draw(seed, SeedPurpose.Structures, seat.index.toLong()))
    val entries = mutableListOf<OrderEntry>()
    var walk = 0
    fun nextAnchor(): MarkerId = anchors[walk++ % anchors.size]
    for ((index, structure) in structures.withIndex()) {
        val (work, markers) = when (structure) {
            SubroutineStructure.Circuit -> AssignedWork.ArrayWipe to stations.take(Balance.STATIONS)
            SubroutineStructure.Long -> AssignedWork.MemoryDump to listOf(nextAnchor(), nextAnchor())
            SubroutineStructure.Short, SubroutineStructure.Medium -> {
                val dealt = SubroutineKind.dealtAs(structure)
                AssignedWork.Subroutine(dealt[kinds.next(dealt.size)]) to listOf(nextAnchor())
            }
        }
        // The most recent earlier entry at each of this entry's cards, and nothing else (D-123).
        val chain = markers.mapNotNull { m -> entries.lastOrNull { it.isAt(m) }?.index }.distinct().sorted()
        entries += OrderEntry(
            index = index,
            work = work,
            markers = markers,
            blockedBy = chain,
            done = false,
        )
    }
    return WorkOrder(seat, entries)
}

/**
 * **The structures of one order, in mix order, before the per-seat shuffle** (D-322).
 *
 * [Balance.orderMix]'s counts, with a circuit or a long the home cannot hold dealt as a short
 * instead (see [workOrderFor]). Always [size] long.
 */
internal fun structuresFor(size: Int, circuit: Boolean, long: Boolean): List<SubroutineStructure> {
    val mix = Balance.orderMix(size)
    return Balance.ORDER_MIX_WEIGHTS.flatMap { (structure, _) ->
        val held = when (structure) {
            SubroutineStructure.Circuit -> circuit
            SubroutineStructure.Long -> long
            SubroutineStructure.Short, SubroutineStructure.Medium -> true
        }
        List(mix.getValue(structure)) { if (held) structure else SubroutineStructure.Short }
    }
}

/**
 * **The round's cross-player dependencies: one seeded cycle over the seats** (D-146, D-322).
 *
 * *Work-order dependencies are cross-player*: one seat's entry holds another seat's until it is
 * completed, and then a random 20–120 s later (`gdd.md:284`). The draw is one cycle: the seats are
 * shuffled, and each seat's **upstream** entry holds the **downstream** entry of the seat after it,
 * the last seat's holding the first's. So **every seat is upstream of exactly one seat and
 * downstream of exactly one**, a dependency always joins two different seats, and nobody appears
 * twice in a chain (`gdd.md:272`).
 *
 * ### ⚠️ The shape is the build's, and provisional (D-322)
 *
 * The design rules that dependencies exist, that they are cross-player, how long the unlock waits
 * and that nothing rescues them. It does not say how many per order or how long a chain is, and
 * the one bound it gives — `L ≤ living Insiders + 1` (`gdd.md:273`) — is computed from the hidden
 * draw. A cycle of pairs is the smallest shape that is cross-player for every seat: each chain is
 * two links long, inside that bound for every lawful round (the band's least is one).
 *
 * ### Chains are drawn over every seat, Insiders included, and that is rule 1 rather than a choice
 *
 * `gdd.md:271` says chains are composed entirely of Residents. **The build cannot draw that**
 * without reading the role, and the role read here would reach every downstream seat: a Resident
 * whose dependency was redrawn because a seat turned out to be an Insider is a Resident told
 * something about that seat, and the differential harness reads exactly that as a leak. An
 * Insider's fake is graded for real (D-109), so an Insider upstream unlocks a downstream seat by
 * completing their entry, or sits on it — *either the player upstream is gone, or they are sitting
 * on it* (D-146). Flagged for the owner in D-322.
 *
 * ### Which entries
 *
 * Per seat, two distinct entries drawn from the seed: the **earlier** is the seat's upstream entry
 * and the **later** its downstream one. Upstream before downstream is what keeps the whole round
 * acyclic: an upstream entry depends only on earlier entries of its own order (self-chains point
 * backwards), so it never waits, however indirectly, on the line it is holding elsewhere.
 *
 * A round with fewer than two orders of two entries has no cycle to draw and gets none.
 */
internal fun linksFor(seed: Long, orders: List<WorkOrder>): Pair<List<WorkOrder>, List<ChainLink>> {
    val eligible = orders.filter { it.entries.size >= 2 }
    if (eligible.size < 2) return orders to emptyList()
    val cycle = eligible.shuffledBy(Draw(seed, SeedPurpose.Links))
    val picks = cycle.map { order ->
        val draw = Draw(seed, SeedPurpose.LinkEntries, order.seat.index.toLong())
        val size = order.entries.size
        val first = draw.next(size)
        val second = draw.next(size - 1).let { if (it >= first) it + 1 else it }
        minOf(first, second) to maxOf(first, second)
    }
    val links = cycle.indices.map { k ->
        val up = cycle[k]
        val down = cycle[(k + 1) % cycle.size]
        val delay = Draw(seed, SeedPurpose.UnlockDelay, down.seat.index.toLong()).let { draw ->
            val seconds = Balance.UNLOCK_DELAY_LEAST_SECONDS +
                draw.next(Balance.UNLOCK_DELAY_MOST_SECONDS - Balance.UNLOCK_DELAY_LEAST_SECONDS + 1)
            seconds * Balance.TICKS_PER_SECOND
        }
        ChainLink(
            up = up.seat,
            upEntry = picks[k].first,
            down = down.seat,
            downEntry = picks[(k + 1) % cycle.size].second,
            delay = delay,
            dueAt = null,
        )
    }.sortedBy { it.down.index }
    val held = orders.map { order ->
        val holds = links.filter { it.down.index == order.seat.index }.map { it.downEntry }
        if (holds.isEmpty()) {
            order
        } else {
            WorkOrder(
                order.seat,
                order.entries.map { e ->
                    if (e.index in holds) OrderEntry(e.index, e.work, e.markers, e.blockedBy, e.done, held = true) else e
                },
            )
        }
    }
    return held to links
}

/**
 * **One seat's contact seed: HMAC-SHA-256 of the round's seed, keyed, over the seat index** (D-212).
 *
 * Seeded and recorded rather than random (rule 4): the round's seed is on `Event.RoundArmed`, so a
 * replay issues every phone the seed it was issued that evening, and the house can re-derive any
 * seat's seed whenever it needs to invert a token — nothing extra is held on `GameState`.
 *
 * **One-way, and that is why it is an HMAC rather than a [Draw].** [Draw] is a linear generator:
 * a phone handed raw outputs of it could run it backwards to the round's seed, and the round's seed
 * is what [insidersFor] draws the Insiders from. A phone holding its own seed learns nothing about
 * the round's seed, another seat's seed, or anybody's role.
 *
 * **Nothing about the seat but its index is an input.** No role, no armed state: every seat's seed
 * is drawn by the same rule, so a role-swapped round issues identical seeds.
 */
fun contactSeedFor(roundSeed: Long, seat: Seat): ContactSeed {
    val message = CONTACT_SEED_LABEL.encodeToByteArray() +
        ByteArray(4) { i -> (seat.index ushr (24 - 8 * i)).toByte() }
    return ContactSeed.of(Hmac.sha256(roundKey(roundSeed), message))
}

private const val CONTACT_SEED_LABEL = "someone-is-home/contact-seed/1"

/**
 * **Everything the house derives from the round's seed, each under a label of its own** (D-214).
 *
 * The round's seed is the one value the Insiders are drawn from, so **nothing derived from it may
 * lead back to it**. Every derivation goes through [oneWaySeed], an HMAC keyed by the round's seed
 * over the purpose's label and an index, and the label is what keeps two purposes from reading one
 * stream: moving the station draw cannot move the Insider draw, and a phone that somehow recovered
 * the state of one stream holds an HMAC output, not the key it was made under.
 *
 * The labels are versioned. Changing one changes every round drawn under it, which is a recording
 * format bump, not a refactor.
 *
 * [contactSeedFor] is one more derivation and predates this list (D-212). It keeps its own
 * label and its four-byte seat index so that every contact token already pinned stays pinned.
 */
internal enum class SeedPurpose(val label: String) {
    /** The Insider count, when the host left it UNKNOWN. Never reaches a phone. */
    InsiderCount("someone-is-home/draw/insider-count/1"),

    /** Who holds it. Never reaches a phone. */
    InsiderSeats("someone-is-home/draw/insider-seats/1"),

    /** The Array Wipe's three stations. Reach phones through the work orders. */
    Stations("someone-is-home/draw/stations/1"),

    /** The round's active markers. Reach the Insiders' picker and every work order. */
    Active("someone-is-home/draw/active/1"),

    /** One seat's anchors, indexed by seat. Reach that seat's work order. */
    Anchors("someone-is-home/draw/anchors/1"),

    /**
     * One seat's short and medium kinds, indexed by seat. Reach that seat's work order. Version 2
     * since the mix (D-322): the stream is read once per short or medium entry, from that
     * structure's kinds, where version 1 was read once per entry from all ten.
     */
    Kinds("someone-is-home/draw/kinds/2"),

    /** Where each structure sits in one seat's order, indexed by seat (D-322). Reaches that seat. */
    Structures("someone-is-home/draw/structures/1"),

    /** The cycle of cross-player dependencies over the seats (D-146, D-322). Never reaches a phone. */
    Links("someone-is-home/draw/links/1"),

    /**
     * One seat's upstream and downstream entries, indexed by seat (D-322). Reaches the seat only as
     * which of its own lines is blocked, and which one somebody is waiting on.
     */
    LinkEntries("someone-is-home/draw/link-entries/1"),

    /** One dependency's unlock delay, indexed by its downstream seat (`gdd.md:284`). Never sent. */
    UnlockDelay("someone-is-home/draw/unlock-delay/1"),

    /** One opened Subroutine's question, indexed by its entity. Reaches the scanning phone. */
    Instance("someone-is-home/draw/instance/1"),

    /** An Egress's two nodes and its type, indexed by the firing tick. Reach every phone. */
    EgressNodes("someone-is-home/draw/egress-nodes/1"),

    /**
     * A Static's noise field, indexed by the firing tick. **Rides `Effect.StaticOpened` to every
     * living phone as a raw value**, which is why this purpose existed before the others were moved
     * behind the HMAC: it was the round's seed times an odd constant plus the tick times another,
     * and anybody who guessed the tick could divide it back out.
     */
    StaticNoise("someone-is-home/static-noise/1"),

    /**
     * One captured [home.someoneshome.model.Observation]'s error and age jitter, indexed by the
     * capture's own number (O4). Keyed on the capture and **never on a seat or a role**, so a
     * role-swapped round rolls byte-identical errors. Reaches no phone today (O6 waits on the owner).
     */
    Occupancy("someone-is-home/draw/occupancy/1"),

    /**
     * A UWB pair's handle, indexed by how many pairs the round has formed (U6, D-300). Keyed on that
     * count and **never on a seat or a role**, so a phone handed one cannot tell whom it ranges, and a
     * role-swapped round deals byte-identical handles. Reaches the two phones of the pair, sealed.
     */
    RangingHandle("someone-is-home/draw/ranging-handle/1"),
}

/**
 * **A 64-bit seed for [purpose], one-way from [roundSeed]** (D-214).
 *
 * HMAC-SHA-256 keyed by the round's seed, over the purpose's label and [index] as eight big-endian
 * bytes, and the first eight bytes of the digest. Deterministic and replayable (rule 4): the round's
 * seed is on `Event.RoundArmed`, so a replay derives every value again and nothing new is recorded.
 *
 * **Never a sum or a product of the round's seed.** Anything linear in it is invertible modulo 2⁶⁴,
 * and `RoundSeedSecrecyTest` asserts that no derived seed is even affine in it.
 */
internal fun oneWaySeed(roundSeed: Long, purpose: SeedPurpose, index: Long = 0L): Long {
    val message = purpose.label.encodeToByteArray() + ByteArray(8) { i -> (index ushr (56 - 8 * i)).toByte() }
    val digest = Hmac.sha256(roundKey(roundSeed), message)
    var out = 0L
    for (i in 0 until 8) out = (out shl 8) or (digest[i].toLong() and 0xff)
    return out
}

/** The round's seed as the HMAC key: eight big-endian bytes. [contactSeedFor]'s since D-212. */
private fun roundKey(roundSeed: Long): ByteArray = ByteArray(8) { i -> (roundSeed ushr (56 - 8 * i)).toByte() }

/**
 * A deterministic stream, written out rather than borrowed.
 *
 * **Not `kotlin.random.Random`**, whose algorithm is stable within a language version and
 * guaranteed by nothing across one — and a round that replayed to different questions next month
 * would be a recording that no longer describes the evening it was made on. Same reasoning the
 * harness's own generator is written out for.
 *
 * ### It starts from [oneWaySeed] and from nothing else (D-214)
 *
 * The generator itself is linear, and its outputs reach phones: a work order is a shuffle, a
 * Subroutine's question is a run of draws. Until D-214 it started from the round's seed times a
 * constant plus the domain, so every stream in the round was an affine function of one secret and
 * a phone collecting enough of its own outputs could search its way back to the Insider draw. Now
 * each stream starts from an HMAC output under its own [SeedPurpose], so the most a phone can
 * recover is that one stream's state, and that leads nowhere. **There is no constructor that takes
 * a raw state**, and that is the guarantee rather than a convention.
 */
internal class Draw(roundSeed: Long, purpose: SeedPurpose, index: Long = 0L) {

    /** Where this stream starts. Read by `RoundSeedSecrecyTest` and by nothing else. */
    internal val origin: Long = oneWaySeed(roundSeed, purpose, index)

    private var state: Long = origin

    fun next(bound: Int): Int {
        require(bound > 0) { "a draw with no options is not a draw" }
        state = state * MULTIPLIER + INCREMENT
        return ((state ushr 33) % bound).toInt()
    }
}

/** Deterministic shuffle. `List.shuffled()` reaches for the platform's default random source. */
internal fun <T> List<T>.shuffledBy(draw: Draw): List<T> {
    val out = toMutableList()
    for (i in out.indices.reversed()) {
        val j = draw.next(i + 1)
        val tmp = out[i]; out[i] = out[j]; out[j] = tmp
    }
    return out
}

private const val MULTIPLIER = 6364136223846793005L
private const val INCREMENT = 1442695040888963407L
