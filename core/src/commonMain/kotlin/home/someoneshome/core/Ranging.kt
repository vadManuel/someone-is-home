package home.someoneshome.core

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Sighting
import home.someoneshome.model.Tick
import home.someoneshome.model.protocol.RangingCodec
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingPlan
import home.someoneshome.model.protocol.RangingPlanSource
import home.someoneshome.model.protocol.RangingReading
import home.someoneshome.model.protocol.RangingReportView
import home.someoneshome.model.protocol.RangingSpare
import home.someoneshome.model.protocol.RangingStep
import home.someoneshome.model.protocol.RangingToken
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
 * ### What it does, once per upload ([RangingPairing.arrive])
 *
 * 1. **Ranks each seat's peers by Bluetooth**, from the 24 RSSI slots every upload already carries,
 *    resolved through the contact resolver's own token book. A pair's strength is its strongest
 *    reading, either way round, among its last [RangingGate.HEARD_KEPT] inside
 *    [RangingGate.HEARD_TICKS]: one side is enough, so a phone that is out, which advertises and
 *    never scans (D-008), is still ranked by the phones that hear it.
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
 *    keeps, start every kept pair it does not yet hold (on its own spare, against the peer's; at most
 *    [RangingWire.TOKEN_SLOTS] a second), and keep the rest.
 *
 * ### Symmetric by construction
 *
 * A pair is one record naming both seats and both spares; each phone's plan is read off the same
 * records, so if one phone of a pair is told to start it, the other is told on its own next upload,
 * with the other's token.
 *
 * ### Never a role, and out seats range like any
 *
 * [RangingPairing] is built from the round's seed and its seats, and takes nothing else, ever: no
 * role, no armed state, and **no out list either**. An out phone is paired exactly as a living one,
 * because a Revoked phone must stay measurable for the report (the UWB plan: *a Revoked phone keeps
 * ranging*), and a house that stopped pairing it would deal it no steps where every other phone gets
 * some, which the plan would show to anybody holding that phone's seed.
 *
 * ### Deterministic (rule 4), and held rather than rebuilt (rule 7)
 *
 * Every step walks seats in seat order and pairs in forming order, so a replay feeding the same
 * uploads rebuilds the same pairs, handles and plans. It is not recorded: its inputs (the uploads)
 * are not, exactly as the contact resolver's are not, and nothing it produces reaches the rules.
 * Unlike the resolver its memory is held in arrays sized at arming and written in place: it runs
 * for every seat every second, and rebuilding its lists each time cost the host more than the
 * whole-app budget allows (D-300). A token is kept once per offered key, so a phone offering the
 * same idle sessions second after second costs the house nothing new. `RangingAllocationTest`
 * guards it.
 */

/**
 * **The pairing's tolerances, named, in one place.** ⚠️ All provisional (D-300): the plan fixes the
 * shape, and phones will set the numbers.
 */
object RangingGate {

    /** How long a Bluetooth reading ranks a pair: three uploads. */
    const val HEARD_TICKS: Long = 3L * ContactGate.UPLOAD_TICKS

    /**
     * **Readings kept per pair: 8.** Both phones of a pair hear each other once a second, so three
     * seconds is six; past eight the oldest goes first.
     */
    const val HEARD_KEPT: Int = 8

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

/**
 * **One pair the house keeps, as a value**: [low] < [high], the [handle] both phones know it by, the
 * spare each phone offered for it, the pair's [ordinal] in the round's forming order, and when each
 * phone last said it holds it (its forming, until then). What [RangingPairing.pairs] reads out, for
 * the bench and the tests; the house holds the same facts in arrays.
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

/**
 * **The house's UWB pairing for one round** (U6, D-300): built at arming from the round's seed and
 * its seats, and handed every upload in arrival order.
 *
 * For each upload: [hear] each Bluetooth reading it carried (already resolved to a seat), then
 * [arrive] with its report; the uploading phone's plan for this second is then [plan], a
 * [RangingPlanSource] the codec seals from directly. **Not thread-safe**: the house calls it under
 * its one lock.
 */
class RangingPairing(private val roundSeed: Long, seats: List<Seat>) {

    /** The round's seats, in seat order: slot `i` is the `i`th. */
    private val seatsInOrder: List<Seat> = seats.sortedBy { it.index }
    private val n = seatsInOrder.size
    private val slotOfIndex = IntArray((seatsInOrder.maxOfOrNull { it.index } ?: -1) + 1) { -1 }.also { slots ->
        seatsInOrder.forEachIndexed { slot, seat -> slots[seat.index] = slot }
    }

    private fun slotOf(seat: Int): Int = if (seat in slotOfIndex.indices) slotOfIndex[seat] else -1

    /** Whether [seat] is one of the round's. */
    fun holds(seat: Seat): Boolean = slotOf(seat.index) >= 0

    private val maxPeers = RangingWire.MAX_PEERS
    private val tokenSlots = RangingWire.TOKEN_SLOTS

    // ---- Bluetooth: the last readings of each unordered pair --------------------------------------
    private val heardRssi = IntArray(n * n * RangingGate.HEARD_KEPT)
    private val heardAt = LongArray(n * n * RangingGate.HEARD_KEPT) { NEVER }
    private val heardNext = IntArray(n * n)

    /** This upload's readings, strongest per peer, until [arrive] takes them. */
    private val hearing = IntArray(n) { NONE }

    // ---- Each seat's last report -----------------------------------------------------------------
    private val capacity = IntArray(n)
    private val reportAt = LongArray(n) { NEVER }
    private val spareKey = LongArray(n * tokenSlots)
    private val spareToken = arrayOfNulls<RangingToken>(n * tokenSlots)
    private val spareCount = IntArray(n)
    private val heldHandle = LongArray(n * maxPeers)
    private val heldCm = IntArray(n * maxPeers)
    private val heldReadings = IntArray(n * maxPeers)
    private val heldCount = IntArray(n)
    private val newKey = LongArray(tokenSlots)
    private val newToken = arrayOfNulls<RangingToken>(tokenSlots)

    // ---- Spare keys already dealt, per seat: a ring ----------------------------------------------
    private val dealt = LongArray(n * RangingGate.DEALT_KEPT)
    private val dealtCount = IntArray(n)
    private val dealtNext = IntArray(n)

    // ---- The pairs, in forming order -------------------------------------------------------------
    private val most = n * maxPeers / 2 + 1
    private val pLow = IntArray(most)
    private val pHigh = IntArray(most)
    private val pHandle = LongArray(most)
    private val pLowKey = LongArray(most)
    private val pHighKey = LongArray(most)
    private val pLowToken = arrayOfNulls<RangingToken>(most)
    private val pHighToken = arrayOfNulls<RangingToken>(most)
    private val pOrdinal = LongArray(most)
    private val pLowHeld = LongArray(most)
    private val pHighHeld = LongArray(most)
    private var pairCount = 0
    private var formed = 0L

    // ---- Scratch, held -----------------------------------------------------------------------------
    private val strength = IntArray(n * n)
    private val degree = IntArray(n)
    private val order = IntArray(most)
    private val keep = BooleanArray(most)
    private val candidates = IntArray(n * n)
    private val stopScratch = LongArray(maxPeers)

    // ---- The plan for the seat that just uploaded --------------------------------------------------
    private val stepKind = IntArray(maxPeers)
    private val stepHandle = LongArray(maxPeers)
    private val stepOwn = LongArray(maxPeers)
    private val stepPeer = arrayOfNulls<RangingToken>(maxPeers)
    private var stepCount = 0

    /** **The plan for the seat that last [arrive]d**: read it, or seal it, before the next arrival. */
    val plan: RangingPlanSource = object : RangingPlanSource {
        override val stepCount: Int get() = this@RangingPairing.stepCount
        override fun stepKind(i: Int): Int = this@RangingPairing.stepKind[i]
        override fun stepHandle(i: Int): Long = this@RangingPairing.stepHandle[i]
        override fun stepOwn(i: Int): Long = this@RangingPairing.stepOwn[i]
        override fun stepPeer(i: Int): RangingToken? = this@RangingPairing.stepPeer[i]
    }

    /** **This upload heard [seat] at [rssi]**: one Bluetooth slot, resolved. Before [arrive]. */
    fun hear(seat: Int, rssi: Int) {
        val p = slotOf(seat)
        if (p < 0 || rssi > ContactGate.RSSI_CEILING) return
        if (hearing[p] == NONE || rssi > hearing[p]) hearing[p] = rssi
    }

    /**
     * **[seat]'s upload, arriving at [at]**: the readings [hear]d for it, and its ranging [report]
     * (null when the field did not read). [taken] is false for a transport's second delivery or a
     * seat not of the round: nothing it carried is taken, and the plan is read off what the house
     * holds. Afterwards [plan] is this seat's.
     */
    fun arrive(at: Tick, seat: Seat, report: RangingReportView?, taken: Boolean) {
        val now = at.step
        val s = slotOf(seat.index)
        if (taken && s >= 0) {
            for (p in 0 until n) {
                val rssi = hearing[p]
                if (rssi == NONE || p == s) continue
                val pair = if (s < p) s * n + p else p * n + s
                val k = pair * RangingGate.HEARD_KEPT + heardNext[pair]
                heardRssi[k] = rssi
                heardAt[k] = now
                heardNext[pair] = (heardNext[pair] + 1) % RangingGate.HEARD_KEPT
            }
            if (report != null) take(s, now, report)
        }
        hearing.fill(NONE)

        strengths(now)
        keepPairs(now)
        formPairs(now)
        planFor(s)
    }

    /** [report] becomes seat [s]'s, and every pair it lists is held at [now]. */
    private fun take(s: Int, now: Long, report: RangingReportView) {
        val spares = minOf(report.spareCount, tokenSlots)
        for (i in 0 until spares) {
            val key = report.spareKey(i)
            var token: RangingToken? = null
            // A key offered before keeps the token taken then, so an idle session costs one copy.
            for (j in 0 until spareCount[s]) {
                val old = spareToken[s * tokenSlots + j]
                if (spareKey[s * tokenSlots + j] == key && old != null && report.spareTokenIs(i, old)) {
                    token = old
                    break
                }
            }
            newKey[i] = key
            newToken[i] = token ?: report.spareToken(i)
        }
        for (i in 0 until tokenSlots) {
            spareKey[s * tokenSlots + i] = if (i < spares) newKey[i] else 0L
            spareToken[s * tokenSlots + i] = if (i < spares) newToken[i] else null
            newToken[i] = null
        }
        spareCount[s] = spares
        val readings = minOf(report.readingCount, maxPeers)
        for (i in 0 until readings) {
            heldHandle[s * maxPeers + i] = report.readingHandle(i)
            heldReadings[s * maxPeers + i] = report.readingReadings(i)
            heldCm[s * maxPeers + i] = report.readingCm(i)
        }
        heldCount[s] = readings
        capacity[s] = report.capacity.coerceIn(0, maxPeers)
        reportAt[s] = now
        for (q in 0 until pairCount) {
            if (!holdsHandle(s, pHandle[q])) continue
            if (pLow[q] == s) pLowHeld[q] = now
            if (pHigh[q] == s) pHighHeld[q] = now
        }
    }

    private fun holdsHandle(s: Int, handle: Long): Boolean {
        for (i in 0 until heldCount[s]) if (heldHandle[s * maxPeers + i] == handle) return true
        return false
    }

    /** Every pair's strength now: its strongest reading inside the window, or [NONE]. */
    private fun strengths(now: Long) {
        for (a in 0 until n) {
            strength[a * n + a] = NONE
            for (b in a + 1 until n) {
                val pair = a * n + b
                var best = NONE
                for (k in pair * RangingGate.HEARD_KEPT until (pair + 1) * RangingGate.HEARD_KEPT) {
                    val t = heardAt[k]
                    if (t == NEVER || now - t > RangingGate.HEARD_TICKS) continue
                    if (best == NONE || heardRssi[k] > best) best = heardRssi[k]
                }
                strength[pair] = best
                strength[b * n + a] = best
            }
        }
    }

    private fun capacityOf(s: Int, now: Long): Int =
        if (reportAt[s] != NEVER && now - reportAt[s] <= RangingGate.REPORT_TICKS) capacity[s] else 0

    /** Whether [b] is among [a]'s strongest [depth] peers: ranked by strength, then by seat. */
    private fun within(a: Int, b: Int, depth: Int): Boolean {
        val sab = strength[a * n + b]
        if (sab == NONE || depth <= 0) return false
        var ahead = 0
        for (c in 0 until n) {
            if (c == a || c == b) continue
            val sac = strength[a * n + c]
            if (sac == NONE) continue
            if (sac > sab || (sac == sab && c < b)) {
                ahead++
                if (ahead >= depth) return false
            }
        }
        return true
    }

    private fun pairStrength(q: Int): Int = strength[pLow[q] * n + pHigh[q]]

    /** Keep: strongest first, then oldest; the kept stay in forming order. */
    private fun keepPairs(now: Long) {
        for (q in 0 until pairCount) order[q] = q
        for (i in 1 until pairCount) {
            val moving = order[i]
            var j = i - 1
            while (j >= 0 && keptBefore(moving, order[j])) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = moving
        }
        degree.fill(0)
        for (i in 0 until pairCount) {
            val q = order[i]
            keep[q] = false
            val low = pLow[q]
            val high = pHigh[q]
            if (now - pLowHeld[q] > RangingGate.HOLD_GRACE_TICKS || now - pHighHeld[q] > RangingGate.HOLD_GRACE_TICKS) continue
            if (!within(low, high, capacityOf(low, now) + RangingGate.KEEP_MARGIN)) continue
            if (!within(high, low, capacityOf(high, now) + RangingGate.KEEP_MARGIN)) continue
            if (degree[low] >= capacityOf(low, now) || degree[high] >= capacityOf(high, now)) continue
            keep[q] = true
            degree[low]++
            degree[high]++
        }
        var w = 0
        for (q in 0 until pairCount) {
            if (!keep[q]) continue
            if (w != q) movePair(q, w)
            w++
        }
        for (q in w until pairCount) {
            pLowToken[q] = null
            pHighToken[q] = null
        }
        pairCount = w
    }

    /** Whether pair [a] is judged before pair [b]: stronger, or as strong and formed earlier. */
    private fun keptBefore(a: Int, b: Int): Boolean {
        val sa = pairStrength(a)
        val sb = pairStrength(b)
        if (sa != sb) return sa > sb
        return pOrdinal[a] < pOrdinal[b]
    }

    private fun movePair(from: Int, to: Int) {
        pLow[to] = pLow[from]; pHigh[to] = pHigh[from]; pHandle[to] = pHandle[from]
        pLowKey[to] = pLowKey[from]; pHighKey[to] = pHighKey[from]
        pLowToken[to] = pLowToken[from]; pHighToken[to] = pHighToken[from]
        pOrdinal[to] = pOrdinal[from]; pLowHeld[to] = pLowHeld[from]; pHighHeld[to] = pHighHeld[from]
    }

    private fun paired(a: Int, b: Int): Boolean {
        for (q in 0 until pairCount) if (pLow[q] == a && pHigh[q] == b) return true
        return false
    }

    /** Form: strongest first, then by seat; each spare dealt once. */
    private fun formPairs(now: Long) {
        var count = 0
        for (a in 0 until n) for (b in a + 1 until n) {
            if (strength[a * n + b] != NONE && !paired(a, b)) candidates[count++] = a * n + b
        }
        for (i in 1 until count) {
            val moving = candidates[i]
            var j = i - 1
            while (j >= 0 && formedBefore(moving, candidates[j])) {
                candidates[j + 1] = candidates[j]
                j--
            }
            candidates[j + 1] = moving
        }
        for (i in 0 until count) {
            val a = candidates[i] / n
            val b = candidates[i] % n
            val capA = capacityOf(a, now)
            val capB = capacityOf(b, now)
            if (degree[a] >= capA || degree[b] >= capB) continue
            if (!within(a, b, capA) || !within(b, a, capB)) continue
            val sa = spareOf(a, now)
            if (sa < 0) continue
            val sb = spareOf(b, now)
            if (sb < 0) continue
            if (pairCount >= most) break
            var handle: Long
            do {
                formed++
                handle = rangingHandleFor(roundSeed, formed).bits
            } while (hasHandle(handle))
            deal(a, spareKey[a * tokenSlots + sa])
            deal(b, spareKey[b * tokenSlots + sb])
            val q = pairCount++
            pLow[q] = a; pHigh[q] = b; pHandle[q] = handle
            pLowKey[q] = spareKey[a * tokenSlots + sa]; pHighKey[q] = spareKey[b * tokenSlots + sb]
            pLowToken[q] = spareToken[a * tokenSlots + sa]; pHighToken[q] = spareToken[b * tokenSlots + sb]
            pOrdinal[q] = formed; pLowHeld[q] = now; pHighHeld[q] = now
            degree[a]++
            degree[b]++
        }
    }

    private fun formedBefore(x: Int, y: Int): Boolean {
        val sx = strength[x]
        val sy = strength[y]
        if (sx != sy) return sx > sy
        return x < y
    }

    private fun hasHandle(handle: Long): Boolean {
        for (q in 0 until pairCount) if (pHandle[q] == handle) return true
        return false
    }

    /** The slot of [s]'s undealt spare with the lowest key (unsigned), or -1. */
    private fun spareOf(s: Int, now: Long): Int {
        if (reportAt[s] == NEVER || now - reportAt[s] > RangingGate.REPORT_TICKS) return -1
        var best = -1
        for (i in 0 until spareCount[s]) {
            val key = spareKey[s * tokenSlots + i]
            if (wasDealt(s, key)) continue
            if (best < 0 || key.toULong() < spareKey[s * tokenSlots + best].toULong()) best = i
        }
        return best
    }

    private fun wasDealt(s: Int, key: Long): Boolean {
        for (i in 0 until dealtCount[s]) if (dealt[s * RangingGate.DEALT_KEPT + i] == key) return true
        return false
    }

    private fun deal(s: Int, key: Long) {
        dealt[s * RangingGate.DEALT_KEPT + dealtNext[s]] = key
        dealtNext[s] = (dealtNext[s] + 1) % RangingGate.DEALT_KEPT
        if (dealtCount[s] < RangingGate.DEALT_KEPT) dealtCount[s]++
    }

    /**
     * **Seat [s]'s steps this second**: stop what it holds that the house no longer keeps, start what
     * the house keeps that it does not hold, keep the rest; in that order, at most
     * [RangingWire.MAX_PEERS], and at most [RangingWire.TOKEN_SLOTS] starts. A phone stops before it
     * starts, so a full phone frees a session first; a start cut off goes out on the next answer.
     */
    private fun planFor(s: Int) {
        stepCount = 0
        for (i in 0 until maxPeers) stepPeer[i] = null
        if (s < 0) return
        // Stops: held handles no pair of this seat keeps, in handle order (unsigned).
        var stops = 0
        for (i in 0 until heldCount[s]) {
            val h = heldHandle[s * maxPeers + i]
            if (!keeps(s, h)) stopScratch[stops++] = h
        }
        for (i in 1 until stops) {
            val moving = stopScratch[i]
            var j = i - 1
            while (j >= 0 && stopScratch[j].toULong() > moving.toULong()) {
                stopScratch[j + 1] = stopScratch[j]
                j--
            }
            stopScratch[j + 1] = moving
        }
        for (i in 0 until stops) add(RangingWire.STEP_STOP, stopScratch[i], 0L, null)
        var starts = 0
        for (q in 0 until pairCount) {
            if (pLow[q] != s && pHigh[q] != s) continue
            if (holdsHandle(s, pHandle[q]) || starts >= tokenSlots) continue
            val low = pLow[q] == s
            add(RangingWire.STEP_START, pHandle[q], if (low) pLowKey[q] else pHighKey[q], if (low) pHighToken[q] else pLowToken[q])
            starts++
        }
        for (q in 0 until pairCount) {
            if (pLow[q] != s && pHigh[q] != s) continue
            if (holdsHandle(s, pHandle[q])) add(RangingWire.STEP_KEEP, pHandle[q], 0L, null)
        }
    }

    private fun keeps(s: Int, handle: Long): Boolean {
        for (q in 0 until pairCount) if (pHandle[q] == handle && (pLow[q] == s || pHigh[q] == s)) return true
        return false
    }

    private fun add(kind: Int, handle: Long, own: Long, peer: RangingToken?) {
        if (stepCount >= maxPeers) return
        stepKind[stepCount] = kind
        stepHandle[stepCount] = handle
        stepOwn[stepCount] = own
        stepPeer[stepCount] = peer
        stepCount++
    }

    // ---- Read out as values, for the bench and the tests (allocates) ------------------------------

    /** Every pair the house keeps, in forming order. */
    fun pairs(): List<RangingPair> = List(pairCount) { q ->
        RangingPair(
            low = seatsInOrder[pLow[q]],
            high = seatsInOrder[pHigh[q]],
            handle = RangingHandle(pHandle[q]),
            lowSpare = RangingSpare(SpareKey(pLowKey[q]), pLowToken[q]!!),
            highSpare = RangingSpare(SpareKey(pHighKey[q]), pHighToken[q]!!),
            ordinal = pOrdinal[q],
            lowHeldAt = Tick(pLowHeld[q]),
            highHeldAt = Tick(pHighHeld[q]),
        )
    }

    /** The plan for the seat that last arrived, as a value. */
    fun planned(): RangingPlan = RangingPlan(
        List(stepCount) { i ->
            val handle = RangingHandle(stepHandle[i])
            when (stepKind[i]) {
                RangingWire.STEP_START -> RangingStep.Start(handle, SpareKey(stepOwn[i]), stepPeer[i]!!)
                RangingWire.STEP_KEEP -> RangingStep.Keep(handle)
                else -> RangingStep.Stop(handle)
            }
        },
    )

    /** What [seat] last said it ranges, with the distances, or nothing for a seat not of the round. */
    fun readingsOf(seat: Seat): List<RangingReading> {
        val s = slotOf(seat.index)
        if (s < 0) return emptyList()
        return List(heldCount[s]) { i ->
            val k = s * maxPeers + i
            RangingReading(RangingHandle(heldHandle[k]), if (heldReadings[k] == 0) null else heldCm[k], heldReadings[k])
        }
    }

    /** How many pairs the round has formed. */
    val pairsFormed: Long get() = formed

    private companion object {
        const val NONE = Int.MIN_VALUE
        const val NEVER = Long.MIN_VALUE
    }
}

/**
 * **The [formed]th pair's handle in the round of [roundSeed]**: [oneWaySeed] under
 * [SeedPurpose.RangingHandle], as eight bytes. No seat is an input.
 */
internal fun rangingHandleFor(roundSeed: Long, formed: Long): RangingHandle =
    RangingHandle(oneWaySeed(roundSeed, SeedPurpose.RangingHandle, formed))

/**
 * **The ranging desk for the round [state] arms**: built from its seed and its seats, and from
 * nothing else in it, so no role, armed state or out list can reach the pairing (D-300). The one
 * place a desk is made from a round.
 */
fun rangingDeskFor(state: GameState): RangingDesk = RangingDesk(state.seed, state.seats)

/**
 * **The house's whole ranging path for one round, per upload** (U6, D-300): the upload's Bluetooth
 * slots resolved to seats, its report opened under the seat's seed, the pairing told, and the seat's
 * plan sealed under the same seed. Built at arming; one per house, called under its lock.
 *
 * It holds everything it reuses (rule 7): the [RangingPairing], one [RangingCodec], each seat's seed
 * (derived once here), and the contact book's tokens keyed by their text, rebuilt only when the
 * book is (once a window), so resolving a slot allocates nothing. On a steady second the one thing
 * an upload costs is its plan's string. `RangingAllocationTest` measures it.
 */
class RangingDesk(roundSeed: Long, seats: List<Seat>) {
    val pairing = RangingPairing(roundSeed, seats)
    private val codec = RangingCodec()
    private val seeds = arrayOfNulls<ContactSeed>((seats.maxOfOrNull { it.index } ?: -1) + 1).also { seeds ->
        for (seat in seats) seeds[seat.index] = contactSeedFor(roundSeed, seat)
    }
    private var book: ContactBook? = null
    private val tokenSeats = HashMap<String, Int>()

    /**
     * **[seat]'s upload, through the pairing, and its plan sealed with [nonce]**: [sightings] are
     * every Bluetooth slot it carried, resolved through [book] (the contact resolver's, for this
     * window); [field] is its ranging report. Null for a seat that is not the round's, which has no
     * seed here.
     */
    fun answer(at: Tick, seat: Seat, sightings: List<Sighting>, book: ContactBook?, field: String, taken: Boolean, nonce: ByteArray): String? {
        val seed = seeds.getOrNull(seat.index) ?: return null
        if (book !== this.book) {
            this.book = book
            tokenSeats.clear()
            book?.seatOf?.forEach { (token, owner) -> tokenSeats[token.hex] = owner.index }
        }
        for (i in sightings.indices) {
            val s = sightings[i]
            val owner = tokenSeats[s.token.hex] ?: continue
            pairing.hear(owner, s.rssi)
        }
        val report: RangingReportView? = if (codec.openReport(seed, field)) codec else null
        pairing.arrive(at, seat, report, taken)
        return codec.packPlan(seed, nonce, pairing.plan)
    }
}
