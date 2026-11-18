package home.someoneshome.core

import home.someoneshome.model.Balance
import home.someoneshome.model.EgressType
import home.someoneshome.model.Event
import home.someoneshome.model.Floor
import home.someoneshome.model.GameState
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerId
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick

/*
 * **What an Egress is, in a round.**
 *
 * Two halves, and they sit on opposite sides of `reduce`, exactly as a scan's do.
 *
 * `egressFor` is ABOVE the rules, beside `routeScan` and `armingFor`: choosing the two nodes needs
 * the home's SHAPE -- which rooms touch which -- and the rules have never held house geography and
 * must not start. Its answer rides `Event.EgressFired` and lands in the recording as an input.
 *
 * `pulseNear` and `onThePulse` are INSIDE them: the pulse schedule is arithmetic on the anchor the
 * Egress was fired with -- a host-clock millisecond read at the edge and carried on the event (C2)
 * -- so it replays from the recording without anybody having to record a schedule or read a clock.
 */

/**
 * **The house picks the type and the two nodes, at fire time** (`gdd.md:349`, F-001 as ratified).
 *
 * ### The nodes: two ordinary registered markers in non-adjacent rooms
 *
 * F-001's proposed resolution, ratified: **do not add a setup step.** The candidates are the
 * round's own active set (D-123) — cards this round is already using — so the Egress lands in a
 * house the players are already walking, and it lands somewhere different every time, which a pair
 * designated during setup never could.
 *
 * ### Non-adjacency and the small-home degrade are ONE rule, not a rule and an exception
 *
 * The pair chosen is **the two markers whose rooms are farthest apart** in the adjacency graph, and
 * *non-adjacent* is simply a distance of two or more. In an ordinary home that picks a
 * non-adjacent pair by construction; in a home too small to have one it picks the farthest thing
 * available, which is the owner's ruling — *small homes are lawful; never fail to fire* — obtained
 * without a second code path that only runs in a house nobody tested in.
 *
 * Rooms on different floors are treated as **maximally far**, which is both true and correct:
 * adjacency never crosses a floor, and two storeys is as far apart as this house gets.
 *
 * ### It can decline, and only for a reason no lawful home has
 *
 * Null when the round cannot supply **two distinct** markers. That is not the small-home degrade —
 * D-127 floors a reviewable home at eight markers — it is a round armed against an empty or
 * one-card home, and firing there would start an Egress that is *physically impossible to contain*
 * while looking exactly like one that is not. The Residents would lose to a shape they could not
 * see. The Insider's phone shows what it shows for every fire: cooldown state, and nothing else
 * (`gdd.md:396`).
 *
 * ### Seeded off the round and the moment
 *
 * Never `Random`. The draw is keyed one-way off the round's seed and indexed by the tick (D-214),
 * so two Egresses in one round draw different pairs and a replay draws what it drew before — and
 * because the answer rides the event, the recording holds it whether or not this function is ever
 * called again.
 *
 * ### The pulse is anchored on the host's clock, handed in (C2)
 *
 * [anchorMillis] is the host's monotonic clock in milliseconds at the moment of the draw -- the
 * clock the host answers every phone's probe with (C1) -- and the first pulse lands
 * [Balance.SYNC_PULSE_LEAD_MILLIS] after it. **Handed in, because the rules read no clock**; the
 * caller reads it at the edge, and the event carries the result so a replay never reads one at all.
 * Not [at]: the house's ticks drift from wall time, and a haptic pulse in two hands cannot.
 */
fun egressFor(
    at: Tick,
    actor: Seat,
    state: GameState,
    map: HouseMap,
    plan: HousePlan,
    anchorMillis: Long,
): Event.EgressFired? {
    val candidates = state.activeMarkers.distinctBy { it.value }
    if (candidates.size < 2) return null

    val draw = Draw(state.seed, SeedPurpose.EgressNodes, at.step)
    val shuffled = candidates.shuffledBy(draw)
    val hops = hopsBetween(plan)

    var best: Pair<MarkerId, MarkerId>? = null
    var bestDistance = -1
    for (i in shuffled.indices) {
        for (j in i + 1 until shuffled.size) {
            val distance = separation(shuffled[i], shuffled[j], map, hops)
            if (distance > bestDistance) {
                bestDistance = distance
                best = shuffled[i] to shuffled[j]
            }
        }
    }
    val pair = best ?: return null

    // Drawn AFTER the nodes and off the same stream, so the label cannot be read off the geography
    // and the geography cannot be read off the label -- they are one draw with an order, not two
    // that a reader could correlate.
    val type = if (draw.next(2) == 0) EgressType.Beacon else EgressType.Tether
    return Event.EgressFired(
        at, actor, type, listOf(pair.first, pair.second),
        pulseFrom = anchorMillis + Balance.SYNC_PULSE_LEAD_MILLIS,
    )
}

/**
 * How far apart two markers are, in rooms.
 *
 * [UNREACHABLE] for two markers on different floors or in rooms with no path between them, which
 * ranks above every finite distance — two storeys apart is the farthest this house goes. Zero for
 * two markers in the same room, which ranks below everything and is what the degrade falls back to
 * in a one-room home.
 *
 * A marker whose card the map has forgotten is treated as its own island rather than dropped: it is
 * still a place a player can be sent, and dropping it here would silently shrink the candidate set
 * for a reason the caller could not see.
 */
private fun separation(
    a: MarkerId,
    b: MarkerId,
    map: HouseMap,
    hops: Map<String, Map<String, Int>>,
): Int {
    val roomA = map.registrationOf(a)?.room?.name ?: return UNREACHABLE
    val roomB = map.registrationOf(b)?.room?.name ?: return UNREACHABLE
    if (roomA == roomB) return 0
    return hops[roomA]?.get(roomB) ?: UNREACHABLE
}

/**
 * **Every room's distance to every other, in hops, derived from cell neighbours** (E4.9).
 *
 * *Adjacency is now known for free, from grid cell neighbours* (`gdd.md:864`) — so this is a
 * breadth-first walk over [PlanRoom.neighbours][HousePlan] and not a line of geometry. Two rooms
 * sharing an edge are one hop apart; two that both touch a landing but not each other are two, and
 * two is what F-001 means by **non-adjacent**.
 *
 * **Stairs are transit and are not filtered out.** No card can be registered into a staircase
 * (D-099), so no node is ever *in* one — but two rooms joined only by a landing are genuinely two
 * hops apart, and a walk that refused to cross one would call them unreachable and rank them above
 * a pair on separate floors.
 *
 * Ordered throughout. `LinkedHashMap` and sorted keys, because this feeds a maximum with ties, and
 * a tie broken in hash order is a different Egress on the second run of the same recording.
 */
private fun hopsBetween(plan: HousePlan): Map<String, Map<String, Int>> {
    val out = LinkedHashMap<String, Map<String, Int>>()
    for (floor in plan.floors) {
        for (from in floor.rooms.sortedBy { it.name }) out[from.name] = walkFrom(from, floor)
    }
    return out
}

/** One breadth-first walk, in room order. Rooms it never reaches simply have no entry. */
private fun walkFrom(from: PlanRoom, floor: Floor): Map<String, Int> {
    val seen = LinkedHashMap<String, Int>()
    seen[from.name] = 0
    var frontier = listOf(from)
    var distance = 0
    while (frontier.isNotEmpty()) {
        distance++
        val next = mutableListOf<PlanRoom>()
        for (room in frontier) {
            for (neighbour in floor.neighboursOf(room)) {
                if (seen.containsKey(neighbour.name)) continue
                seen[neighbour.name] = distance
                next += neighbour
            }
        }
        frontier = next.sortedBy { it.name }
    }
    return seen
}

/**
 * **The Sync Pulse schedule: house-wide, and anchored on the host's clock** (`gdd.md:355`, C2).
 *
 * *Both phones pulse haptically in unison off a house-scheduled timestamp.* One schedule for the
 * whole house rather than one per pair is what makes **unlimited participants forming concurrent
 * pairs** work at all: two people who reach the nodes a minute apart from two other people are
 * still tapping the same pulses, so any pair that forms is already in time with any other.
 *
 * It is arithmetic on [pulseFrom] -- milliseconds of the host's monotonic clock, carried on
 * `Event.EgressFired` -- and nothing else: no state, no schedule to record, and a replay grades the
 * same taps the same way. Taps are on the same timeline, as each phone measured it (C1).
 *
 * **Not affected by a pause.** A meeting stops the *countdown* (D-133); the pulse goes on being a
 * property of when the house caught fire. Nobody can tap during a meeting anyway -- the admission
 * gate refuses it -- and a schedule that shifted under a pause would put the pulse somewhere no
 * phone could compute from what it was told.
 */
internal fun pulseNear(pulseFrom: Long, tap: Long): Long {
    val offset = tap - pulseFrom
    val interval = Balance.SYNC_PULSE_INTERVAL_MILLIS
    // Integer division rounding to nearest, negatives included. `(offset + interval/2) / interval`
    // truncates toward zero, so a tap before the first pulse would round the wrong way and a player
    // who was early would be graded against a pulse that never happened.
    return if (offset >= 0) {
        (offset + interval / 2) / interval
    } else {
        -((-offset + interval / 2) / interval)
    }
}

/** When pulse [n] lands, in the host's monotonic milliseconds. */
internal fun pulseAt(pulseFrom: Long, n: Long): Long =
    pulseFrom + n * Balance.SYNC_PULSE_INTERVAL_MILLIS

/**
 * **Did this phone hit the pulse four times?** (`gdd.md:355`, A-11.)
 *
 * Three conditions and no more. There are exactly [Balance.SYNC_PULSE_BEATS] taps; each lands
 * within [Balance.SYNC_PULSE_WINDOW_MILLIS] of a scheduled pulse; and no two of them are the *same*
 * pulse -- four taps inside one pulse's window is a player hammering the screen, not a player
 * keeping time. **Whether the four must be consecutive is the owner's** (the containment spec's
 * O3); this grades any four distinct pulses, as it always has.
 *
 * **The window is generous and that is the architecture's own ruling**, not a tolerance somebody
 * picked: *twitch timing is forbidden, and a generous window absorbs device skew*
 * (game-architecture.md:249).
 *
 * Its early returns are an internal grader's, not a client-visible path: what the phone is told is
 * built once in `pulseReturned`, whatever this says (rule 1). Integer arithmetic throughout (rule 4).
 */
internal fun onThePulse(pulseFrom: Long, taps: List<Long>): Boolean {
    if (taps.size != Balance.SYNC_PULSE_BEATS) return false
    val hit = mutableListOf<Long>()
    for (tap in taps) {
        val pulse = pulseNear(pulseFrom, tap)
        val distance = tap - pulseAt(pulseFrom, pulse)
        if (distance > Balance.SYNC_PULSE_WINDOW_MILLIS || distance < -Balance.SYNC_PULSE_WINDOW_MILLIS) {
            return false
        }
        if (hit.contains(pulse)) return false
        hit += pulse
    }
    return true
}

/** Two storeys apart, or no path at all — ranked above every finite distance. See [separation]. */
private const val UNREACHABLE = Int.MAX_VALUE
