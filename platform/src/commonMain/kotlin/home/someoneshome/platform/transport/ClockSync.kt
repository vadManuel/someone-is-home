package home.someoneshome.platform.transport

/** D5's cadence: re-sync every 30 s. */
const val RESYNC_MILLIS: Long = 30_000

/**
 * Probes per estimation round. **A placeholder, F-005-style** — D5 fixes the estimator (minimum
 * RTT), not the sample count. Five is enough to dodge one bad scheduling hiccup on a quiet LAN;
 * the eight-phone rig is where the number meets reality.
 */
const val PROBES_PER_ROUND: Int = 5

/** A round that has not completed after this long is abandoned; its samples still count. */
const val ROUND_TIMEOUT_MILLIS: Long = 3_000

/**
 * How fast the applied offset may move toward a new estimate, in milliseconds per elapsed
 * second. **Deliberately far below 1000**: the mapped host timeline is `local + offset`, and as
 * long as the offset loses less than one millisecond per elapsed millisecond, the timeline can
 * slow but NEVER run backward — which is the whole point of slewing (D5: a backward jump could
 * skip an already-scheduled event). The value itself is a placeholder; the constraint `< 1000`
 * is not.
 */
const val SLEW_RATE_MILLIS_PER_SECOND: Long = 50

/**
 * D5's clock discipline, as arithmetic — the client half.
 *
 * The client sends numbered probes; the host answers each with its own monotonic reading; the
 * client computes `offset = hostMillis + rtt/2 − receivedAt` per sample and **the minimum-RTT
 * sample of the round wins** — not the mean, because a delayed packet lies about the offset in
 * proportion to its delay, and the least-delayed packet is the least able to lie.
 *
 * ### First fix sets, every later fix slews
 *
 * Before the first round completes there is no host timeline and nothing scheduled against it,
 * so the first estimate is applied whole. From then on the applied offset MOVES toward each new
 * estimate at [SLEW_RATE_MILLIS_PER_SECOND] — [hostNowMillis] can slow down, it can hurry, it
 * can never run backward.
 *
 * The client's own readings never leave the device: probes carry a number, not a time.
 *
 * Pure, single-threaded (D4), time handed in as monotonic milliseconds.
 */
class ClockSync {

    private class Round(val startedAtMillis: Long) {
        val sentAt = LinkedHashMap<Long, Long>()
        var bestRttMillis: Long = Long.MAX_VALUE
        var bestOffsetMillis: Long = 0
        var answered = 0
    }

    private var nextProbe: Long = 0
    private var round: Round? = null
    private var lastRoundStartedMillis: Long? = null

    /**
     * **The estimate in force, as one immutable value**, or null before the first fix.
     *
     * A value rather than three fields so that a reader on another thread can be handed the whole
     * of it at once: [TransportClient] publishes it after every mark and reads the house's clock
     * off it from any thread, without ever touching this object, which stays single-threaded.
     */
    var fix: ClockFix? = null
        private set

    /** Probe ids to put on the wire right now. A burst at each round's start; nothing between. */
    fun dueProbes(nowMillis: Long): List<Long> {
        val current = round
        if (current != null) {
            if (nowMillis < current.startedAtMillis + ROUND_TIMEOUT_MILLIS) return emptyList()
            finishRound(current, nowMillis) // abandoned, but its samples still count
        }
        val last = lastRoundStartedMillis
        if (last != null && nowMillis < last + RESYNC_MILLIS) return emptyList()
        val fresh = Round(nowMillis)
        round = fresh
        lastRoundStartedMillis = nowMillis
        return List(PROBES_PER_ROUND) { nextProbe++ }.onEach { fresh.sentAt[it] = nowMillis }
    }

    /** The host's answer. Unknown probe ids are counted, not crashed on. */
    fun onMark(probe: Long, hostMillis: Long, nowMillis: Long) {
        val current = round ?: run { strayMarks++; return }
        val sent = current.sentAt.remove(probe) ?: run { strayMarks++; return }
        val rtt = nowMillis - sent
        if (rtt < current.bestRttMillis) {
            current.bestRttMillis = rtt
            current.bestOffsetMillis = hostMillis + rtt / 2 - nowMillis
        }
        current.answered++
        if (current.answered == PROBES_PER_ROUND) finishRound(current, nowMillis)
    }

    var strayMarks: Int = 0
        private set

    /** The RTT behind the current estimate, for eyes on a screen. Null before the first fix. */
    var lastRttMillis: Long? = null
        private set

    private fun finishRound(current: Round, nowMillis: Long) {
        round = null
        if (current.bestRttMillis == Long.MAX_VALUE) return // nothing came back at all
        lastRttMillis = current.bestRttMillis
        // The first fix is applied whole; every later one slews from wherever the applied offset
        // has got to, toward the new estimate.
        val base = fix?.offsetAt(nowMillis) ?: current.bestOffsetMillis
        fix = ClockFix(
            baseOffsetMillis = base,
            targetOffsetMillis = current.bestOffsetMillis,
            slewFromMillis = nowMillis,
            rttMillis = current.bestRttMillis,
        )
    }

    /**
     * **A new connection: the round in flight is abandoned, and the fix is kept** (C1).
     *
     * Marks for probes sent on the old socket will never come, so a round waiting for them would
     * only sit out its [ROUND_TIMEOUT_MILLIS]; and a phone that has just reconnected is exactly the
     * phone whose estimate most wants refreshing. So the next [dueProbes] starts a round at once.
     * The fix stays: the house's clock did not change because a socket did. Anything the abandoned
     * round heard still counts, as it does for a round that timed out.
     */
    fun reconnected(nowMillis: Long) {
        round?.let { finishRound(it, nowMillis) }
        lastRoundStartedMillis = null
    }

    /** The offset in force at [nowMillis] — base moved toward target at the bounded rate. */
    fun appliedOffsetMillis(nowMillis: Long): Long? = fix?.offsetAt(nowMillis)

    /** Local time mapped onto the authority's timeline. Null until the first fix. */
    fun hostNowMillis(nowMillis: Long): Long? = fix?.hostNowMillis(nowMillis)
}

/**
 * **One estimate of the house's clock: where the applied offset slews from, to, since when.**
 *
 * Immutable, so it can be read on any thread. [offsetAt] is the slew as arithmetic: the base,
 * moved toward the target at [SLEW_RATE_MILLIS_PER_SECOND] and stopping there — so the mapped
 * timeline can slow or hurry, and never runs backward.
 */
data class ClockFix(
    val baseOffsetMillis: Long,
    val targetOffsetMillis: Long,
    val slewFromMillis: Long,
    /** The round trip behind the newest estimate. For a log line, never for a screen. */
    val rttMillis: Long,
) {
    /** The offset in force at [nowMillis], local monotonic milliseconds. */
    fun offsetAt(nowMillis: Long): Long {
        val travelled = (nowMillis - slewFromMillis) * SLEW_RATE_MILLIS_PER_SECOND / 1_000
        val distance = targetOffsetMillis - baseOffsetMillis
        return when {
            distance >= 0 -> baseOffsetMillis + minOf(travelled, distance)
            else -> baseOffsetMillis - minOf(travelled, -distance)
        }
    }

    /** [nowMillis] on this phone, as the house's monotonic milliseconds. */
    fun hostNowMillis(nowMillis: Long): Long = nowMillis + offsetAt(nowMillis)
}
