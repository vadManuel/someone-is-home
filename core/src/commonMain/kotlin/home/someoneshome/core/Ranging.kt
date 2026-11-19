package home.someoneshome.core

import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingPlan
import home.someoneshome.model.protocol.RangingReport
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingStep
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.SpareKey

/*
 * **The house pairs phones for UWB: Bluetooth picks who is near, UWB measures them** (U6, D-300).
 *
 * Beside `resolveContacts` and for its reason: something above the rules has to turn what the
 * radios said into what happens next, and that needs knowledge `reduce` never holds (here, which
 * phone offered which token). It mints no `Event` and changes no state the rules hold. **No rule
 * reads its output in this unit**: contact, the report and the spectator map still read RSSI, so
 * nothing in play changes. What it decides is only which phones are told to range which.
 *
 * ### What it does, once per upload
 *
 * 1. **Ranks each seat's peers by Bluetooth**, from the 24 RSSI slots every upload already carries,
 *    resolved through the contact resolver's own token book. A pair's strength is its strongest
 *    reading, either way round, inside [RangingGate.HEARD_TICKS]: one side is enough, so a phone that
 *    is out, which advertises and never scans (D-008), is still ranked by the phones that hear it.
 * 2. **Keeps the pairs that are still candidates** (hysteresis): a pair stays while each phone is
 *    among the other's strongest *capacity + [RangingGate.KEEP_MARGIN]* peers, both phones keep
 *    saying they hold it ([RangingGate.HOLD_GRACE_TICKS]), and neither phone's degree passes its
 *    capacity.
 * 3. **Forms new pairs greedily, strongest first**, each only between two phones that are each among
 *    the other's strongest *capacity* peers, both under their capacity and both with a spare token
 *    the house has not dealt. Entry is stricter than staying, so a pair does not flap at the edge.
 * 4. **Deals each new pair a handle drawn from the round's seed** under its own label, indexed by
 *    how many pairs the round has formed ([SeedPurpose.RangingHandle]). Never from a seat: no
 *    phone can tell from a handle who it is ranging, and a pair formed again gets a new one.
 * 5. **Plans the uploading phone's second**: stop every pair it holds that the house no longer
 *    keeps, start every kept pair it does not yet hold (on its own spare, against the peer's), and
 *    keep the rest.
 *
 * ### Symmetric by construction
 *
 * A pair is one value naming both seats and both spares; each phone's plan is read off the same
 * list, so if one phone of a pair is told to start it, the other is told on its own next upload,
 * with the other's token.
 *
 * ### Never a role, and out seats range like any
 *
 * [RangingHouse]'s field list is the guarantee: the round's seed and its seats. No role, no armed
 * state, and **no out list either**: an out phone is paired exactly as a living one, because a
 * Revoked phone must stay measurable for the report (the UWB plan: *a Revoked phone keeps
 * ranging*), and a house that stopped pairing it would deal it no steps where every other phone
 * gets some, which the plan's step count would show to anybody holding that phone's seed.
 *
 * ### Deterministic (rule 4)
 *
 * A pure function of `(memory, arrival, state)`, every list built in seat or formation order, so a
 * replay feeding the same uploads rebuilds the same pairs, handles and plans. It is not recorded:
 * its inputs (the uploads) are not, exactly as the contact resolver's are not, and nothing it
 * produces reaches the rules.
 */

/**
 * **The pairing's tolerances, named, in one place.** ⚠️ All provisional (D-300): the plan fixes the
 * shape, and phones will set the numbers.
 */
object RangingGate {

    /** How long a Bluetooth reading ranks a pair: three uploads. */
    const val HEARD_TICKS: Long = 3L * ContactGate.UPLOAD_TICKS

    /**
     * **How far down a phone's ranking a pair may slip and still be kept: two places past its
     * capacity.** Entry asks for the top *capacity*; staying asks for the top *capacity + 2*.
     */
    const val KEEP_MARGIN: Int = 2

    /**
     * **How long a pair may go without one of its phones saying it holds it: three uploads.** A new
     * pair starts the count at its forming, so a Start lost on the way is sent again for three
     * seconds before the pair is let go and formed afresh.
     */
    const val HOLD_GRACE_TICKS: Long = 3L * ContactGate.UPLOAD_TICKS

    /** How long a phone's last report stands for it: three uploads. Older, it can hold nothing. */
    const val REPORT_TICKS: Long = 3L * ContactGate.UPLOAD_TICKS

    /** Spare keys remembered as dealt per seat, so a stale upload's offer is not dealt twice. */
    const val DEALT_KEPT: Int = 4 * RangingWire.MAX_PEERS
}

/** Bluetooth heard the pair [low]–[high] (low < high) at [rssi] in the upload that arrived at [at]. */
data class RangingHeard(val low: Seat, val high: Seat, val rssi: Int, val at: Tick)

/** [seat]'s last readable ranging report, taken at [at]. */
data class RangingSeatReport(val seat: Seat, val report: RangingReport, val at: Tick)

/**
 * **One pair the house keeps**: [low] < [high], the [handle] both phones know it by, the spare each
 * phone offered for it, the pair's [ordinal] in the round's forming order, and when each phone last
 * said it holds it (its forming, until then).
 */
data class RangingPair(
    val low: Seat,
    val high: Seat,
    val handle: RangingHandle,
    val lowSpare: RangingSpare,
    val highSpare: RangingSpare,
    val ordinal: Long,
    val lowHeldAt: Tick,
    val highHeldAt: Tick,
) {
    fun has(seat: Seat): Boolean = low.index == seat.index || high.index == seat.index
}

/** [key], offered by [seat], has been dealt to a pair. */
data class RangingDealt(val seat: Seat, val key: SpareKey)

/** **What the pairing remembers between arrivals**, as values (rule 4). */
data class RangingMemory(
    val heard: List<RangingHeard>,
    val reports: List<RangingSeatReport>,
    val pairs: List<RangingPair>,
    val dealt: List<RangingDealt>,
    /** Pairs formed this round: the handle draw's index. */
    val formed: Long,
) {
    companion object {
        val EMPTY = RangingMemory(emptyList(), emptyList(), emptyList(), emptyList(), 0L)
    }
}

/** What one arrival produced: the memory to carry, and the uploading phone's plan for this second. */
data class RangingResolution(val memory: RangingMemory, val plan: RangingPlan)

/**
 * **The whole of what the pairing may know about the round**: its seed and its seats. No role, no
 * armed state, no out list.
 */
internal class RangingHouse private constructor(val roundSeed: Long, val seats: List<Seat>) {
    fun holds(seat: Seat): Boolean = seats.any { it.index == seat.index }

    companion object {
        fun of(state: GameState) = RangingHouse(state.seed, state.seats.sortedBy { it.index })
    }
}

/**
 * **One upload, for the pairing: [seat]'s, arriving at [at].**
 *
 * [heard] is every Bluetooth slot it carried, already resolved to the seat that advertised it and
 * its RSSI (the caller resolves through the contact book, `ContactBook.seatOf`), and [report] is its
 * ranging field opened under the seat's seed, or null when it did not read. [taken] is false for a
 * transport's second delivery or a seat not of the round: nothing it carries is taken again, and the
 * plan is read off what the house already holds.
 */
fun pairForRanging(
    memory: RangingMemory,
    at: Tick,
    seat: Seat,
    heard: List<Pair<Seat, Int>>,
    report: RangingReport?,
    taken: Boolean,
    state: GameState,
): RangingResolution {
    val house = RangingHouse.of(state)
    fun fresh(t: Tick, span: Long) = at.step - t.step <= span

    // 1. What this upload says: its Bluetooth readings, and its report.
    val heardNow = ArrayList<RangingHeard>(memory.heard.size + heard.size)
    memory.heard.filterTo(heardNow) { fresh(it.at, RangingGate.HEARD_TICKS) && house.holds(it.low) && house.holds(it.high) }
    var reports = memory.reports.filter { house.holds(it.seat) }
    var pairs = memory.pairs.filter { house.holds(it.low) && house.holds(it.high) }
    if (taken && house.holds(seat)) {
        heard
            .filter { (peer, rssi) -> rssi <= ContactGate.RSSI_CEILING && peer.index != seat.index && house.holds(peer) }
            .groupBy { it.first.index }
            .entries
            .sortedBy { it.key }
            .forEach { (_, readings) ->
                val peer = readings.first().first
                val rssi = readings.maxOf { it.second }
                heardNow += if (seat.index < peer.index) RangingHeard(seat, peer, rssi, at) else RangingHeard(peer, seat, rssi, at)
            }
        if (report != null) {
            reports = (reports.filter { it.seat.index != seat.index } + RangingSeatReport(seat, report, at)).sortedBy { it.seat.index }
            val held = report.readings.map { it.handle }
            pairs = pairs.map { p ->
                when {
                    p.handle !in held -> p
                    p.low.index == seat.index -> p.copy(lowHeldAt = at)
                    p.high.index == seat.index -> p.copy(highHeldAt = at)
                    else -> p
                }
            }
        }
    }
    heardNow.sortWith(compareBy<RangingHeard> { it.low.index }.thenBy { it.high.index }.thenBy { it.at.step })

    // 2. The ranking: each seat's peers, strongest first, then by seat.
    fun reportOf(s: Seat) = reports.firstOrNull { it.seat.index == s.index }
    fun capacityOf(s: Seat) = reportOf(s)?.takeIf { fresh(it.at, RangingGate.REPORT_TICKS) }?.report?.capacity ?: 0
    val strength = HashMap<Long, Int>()
    for (h in heardNow) {
        val k = pairKey(h.low, h.high)
        strength[k] = maxOf(strength[k] ?: Int.MIN_VALUE, h.rssi)
    }
    fun strengthOf(a: Seat, b: Seat): Int? = strength[if (a.index < b.index) pairKey(a, b) else pairKey(b, a)]
    val ranking = house.seats.associate { s ->
        s.index to house.seats
            .filter { it.index != s.index }
            .mapNotNull { peer -> strengthOf(s, peer)?.let { peer to it } }
            .sortedWith(compareByDescending<Pair<Seat, Int>> { it.second }.thenBy { it.first.index })
            .map { it.first.index }
    }
    fun within(a: Seat, b: Seat, depth: Int): Boolean = ranking.getValue(a.index).take(depth).contains(b.index)

    // 3. Keep: strongest first, then oldest.
    val degree = HashMap<Int, Int>()
    fun degreeOf(s: Seat) = degree[s.index] ?: 0
    val kept = ArrayList<RangingPair>()
    for (p in pairs.sortedWith(compareByDescending<RangingPair> { strengthOf(it.low, it.high) ?: Int.MIN_VALUE }.thenBy { it.ordinal })) {
        if (!fresh(p.lowHeldAt, RangingGate.HOLD_GRACE_TICKS) || !fresh(p.highHeldAt, RangingGate.HOLD_GRACE_TICKS)) continue
        if (!within(p.low, p.high, capacityOf(p.low) + RangingGate.KEEP_MARGIN)) continue
        if (!within(p.high, p.low, capacityOf(p.high) + RangingGate.KEEP_MARGIN)) continue
        if (degreeOf(p.low) >= capacityOf(p.low) || degreeOf(p.high) >= capacityOf(p.high)) continue
        kept += p
        degree[p.low.index] = degreeOf(p.low) + 1
        degree[p.high.index] = degreeOf(p.high) + 1
    }

    // 4. Form: strongest first, then by seat; each spare dealt once.
    val dealt = ArrayList(memory.dealt.filter { house.holds(it.seat) })
    fun spareOf(s: Seat): RangingSpare? {
        val r = reportOf(s)?.takeIf { fresh(it.at, RangingGate.REPORT_TICKS) } ?: return null
        return r.report.spares
            .filter { spare -> dealt.none { it.seat.index == s.index && it.key == spare.key } }
            .minByOrNull { it.key.hex }
    }
    var formed = memory.formed
    val candidates = strength.keys
        .map { k -> Seat((k ushr 32).toInt()) to Seat(k.toInt()) }
        .filter { (a, b) -> house.holds(a) && house.holds(b) && kept.none { it.low.index == a.index && it.high.index == b.index } }
        .sortedWith(compareByDescending<Pair<Seat, Seat>> { strengthOf(it.first, it.second) }.thenBy { it.first.index }.thenBy { it.second.index })
    for ((a, b) in candidates) {
        if (degreeOf(a) >= capacityOf(a) || degreeOf(b) >= capacityOf(b)) continue
        if (!within(a, b, capacityOf(a)) || !within(b, a, capacityOf(b))) continue
        val sa = spareOf(a) ?: continue
        val sb = spareOf(b) ?: continue
        var handle: RangingHandle
        do {
            formed++
            handle = rangingHandleFor(house.roundSeed, formed)
        } while (kept.any { it.handle == handle })
        dealt += RangingDealt(a, sa.key)
        dealt += RangingDealt(b, sb.key)
        kept += RangingPair(a, b, handle, sa, sb, formed, at, at)
        degree[a.index] = degreeOf(a) + 1
        degree[b.index] = degreeOf(b) + 1
    }
    val dealtKept = dealt.groupBy { it.seat.index }.entries.sortedBy { it.key }.map { it.value }.flatMap { it.takeLast(RangingGate.DEALT_KEPT) }

    // 5. This phone's plan.
    val plan = if (!house.holds(seat)) RangingPlan.EMPTY else planFor(seat, kept, reportOf(seat)?.report)

    return RangingResolution(
        memory = RangingMemory(
            heard = heardNow,
            reports = reports,
            pairs = kept.sortedBy { it.ordinal },
            dealt = dealtKept,
            formed = formed,
        ),
        plan = plan,
    )
}

/**
 * **[seat]'s steps this second**: stop what it holds that the house no longer keeps, start what the
 * house keeps that it does not hold, keep the rest; in that order, at most
 * [RangingWire.MAX_PEERS]. A phone stops before it starts, so a full phone frees a session first;
 * a start cut off by the width goes out on the next upload's answer.
 */
private fun planFor(seat: Seat, pairs: List<RangingPair>, report: RangingReport?): RangingPlan {
    val held = report?.readings?.map { it.handle }.orEmpty()
    val mine = pairs.filter { it.has(seat) }.sortedBy { it.ordinal }
    val stops = held.filter { h -> mine.none { it.handle == h } }.sortedBy { it.hex }.map { RangingStep.Stop(it) }
    val starts = mine.filter { it.handle !in held }.map { p ->
        val (own, peer) = if (p.low.index == seat.index) p.lowSpare to p.highSpare else p.highSpare to p.lowSpare
        RangingStep.Start(p.handle, own.key, peer.token)
    }
    val keeps = mine.filter { it.handle in held }.map { RangingStep.Keep(it.handle) }
    return RangingPlan((stops + starts + keeps).take(RangingWire.MAX_PEERS))
}

private fun pairKey(low: Seat, high: Seat): Long = (low.index.toLong() shl 32) or (high.index.toLong() and 0xffffffffL)

/**
 * **The [formed]th pair's handle in the round of [roundSeed]**: [oneWaySeed] under
 * [SeedPurpose.RangingHandle], as eight bytes. No seat is an input.
 */
internal fun rangingHandleFor(roundSeed: Long, formed: Long): RangingHandle {
    val v = oneWaySeed(roundSeed, SeedPurpose.RangingHandle, formed)
    val hex = StringBuilder(RangingWire.HANDLE_BYTES * 2)
    for (i in 0 until RangingWire.HANDLE_BYTES) {
        val b = ((v ushr (56 - 8 * i)) and 0xff).toInt()
        hex.append(HANDLE_HEX[b ushr 4]).append(HANDLE_HEX[b and 0xf])
    }
    return RangingHandle(hex.toString())
}

private const val HANDLE_HEX = "0123456789abcdef"
