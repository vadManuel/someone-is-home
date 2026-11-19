package home.someoneshome.platform.transport

/**
 * Probes per seating burst. **A placeholder, F-005-style**: D5 fixes the estimator (minimum RTT),
 * not the sample count. Five is enough to dodge one bad scheduling hiccup on a quiet LAN.
 */
const val PROBES_PER_ROUND: Int = 5

/** A burst that has not completed after this long is abandoned; its samples still count. */
const val ROUND_TIMEOUT_MILLIS: Long = 3_000

/**
 * **How many recent samples the estimate is the minimum round trip of** (D-295).
 *
 * A sample arrives with every upload's answer, one a second, so this is the last sixteen seconds of
 * them. Long enough that a run of Wi-Fi delay (a burst of retries, a scan, a busy access point)
 * still leaves a quiet beat in the window to believe; short enough that the newest quiet sample is
 * at most sixteen seconds old, so two crystals drifting apart at 100 ppm cost under 2 ms of it.
 * The window is counted in samples, not seconds, so a phone whose answers stop keeps its last
 * estimate rather than forgetting it.
 */
const val SAMPLE_WINDOW: Int = 16

/**
 * How fast the applied offset may move toward a new estimate, in milliseconds per elapsed
 * second. **Deliberately far below 1000**: the mapped host timeline is `local + offset`, and as
 * long as the offset loses less than one millisecond per elapsed millisecond, the timeline can
 * slow but NEVER run backward — which is the whole point of slewing (D5: a backward jump could
 * skip an already-scheduled event). The value itself is a placeholder; the constraint `< 1000`
 * is not. Two clocks drifting apart even at 200 ppm move 0.2 ms a second, far inside it.
 */
const val SLEW_RATE_MILLIS_PER_SECOND: Long = 50

/**
 * D5's clock discipline, as arithmetic — the client half.
 *
 * ### Where the samples come from (D-295)
 *
 * **Every upload's answer**, once a second for as long as the phone is in a round (`onSample`):
 * the phone sent a probe at its own `sentAt`, the house read it at its own `heardAt` and answered at
 * its own `answeredAt`, and the answer arrived at the phone's `receivedAt`. That is the owner's
 * ruling of 2026-11-18: the offset is **refreshed every second on the uploads every phone already
 * sends, not measured once at join**, because two crystals drift apart over a 30-minute round.
 *
 * And **one burst of [PROBES_PER_ROUND] numbered probes at each seating** (`dueProbes`/`onMark`),
 * so a phone in the lobby, before any upload, already has an estimate. There is no other burst:
 * until D-295 there was one every 30 s, and the uploads replace it.
 *
 * ### The estimate: the minimum round trip of the last [SAMPLE_WINDOW] samples
 *
 * Per sample, `rtt = (receivedAt − sentAt) − (answeredAt − heardAt)` (the house's own time with the
 * upload is not network) and `offset = ((heardAt − sentAt) + (answeredAt − receivedAt)) / 2`. **The
 * sample with the smallest round trip in the window wins** — not the mean, because a delayed packet
 * lies about the offset by up to half its delay, and the least-delayed packet is the least able to
 * lie. A seating burst's best sample enters the window as one sample. Ties go to the newer.
 *
 * ### First fix sets, every later fix slews
 *
 * Before the first sample there is no host timeline and nothing scheduled against it, so the first
 * estimate is applied whole. From then on the applied offset MOVES toward each new estimate at
 * [SLEW_RATE_MILLIS_PER_SECOND] — [hostNowMillis] can slow down, it can hurry, it can never run
 * backward.
 *
 * The client's own readings never leave the device: probes carry a number, not a time.
 *
 * Pure, single-threaded (D4), time handed in as monotonic milliseconds. No allocation per sample
 * but the new [ClockFix] when the estimate moves.
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

    /** Whether this connection has had its seating burst. Cleared by [reconnected]. */
    private var burstSent = false

    /** The window: round trip and offset per sample, a ring of [SAMPLE_WINDOW]. */
    private val rtts = LongArray(SAMPLE_WINDOW)
    private val offsets = LongArray(SAMPLE_WINDOW)
    private var samples = 0

    /** How many samples this estimator has taken, ever. A diagnostic. */
    var sampleCount: Long = 0
        private set

    /**
     * **The estimate in force, as one immutable value**, or null before the first fix.
     *
     * A value rather than three fields so that a reader on another thread can be handed the whole
     * of it at once: [TransportClient] publishes it after every sample and reads the house's clock
     * off it from any thread, without ever touching this object, which stays single-threaded.
     */
    var fix: ClockFix? = null
        private set

    /** Probe ids to put on the wire right now: one burst per connection, at its seating; nothing after. */
    fun dueProbes(nowMillis: Long): List<Long> {
        val current = round
        if (current != null) {
            if (nowMillis < current.startedAtMillis + ROUND_TIMEOUT_MILLIS) return emptyList()
            finishRound(current, nowMillis) // abandoned, but its samples still count
        }
        if (burstSent) return emptyList()
        burstSent = true
        val fresh = Round(nowMillis)
        round = fresh
        return List(PROBES_PER_ROUND) { nextProbe++ }.onEach { fresh.sentAt[it] = nowMillis }
    }

    /** The host's answer to a probe. Unknown probe ids are counted, not crashed on. */
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

    /**
     * **One upload's round trip** (D-295): sent at [sentAtMillis] on this phone, read by the house at
     * [heardAtMillis] and answered at [answeredAtMillis] on its clock, and the answer here at
     * [receivedAtMillis]. A sample whose round trip comes out negative is impossible on two
     * monotonic clocks and is counted as stray, never believed.
     */
    fun onSample(sentAtMillis: Long, heardAtMillis: Long, answeredAtMillis: Long, receivedAtMillis: Long) {
        val rtt = (receivedAtMillis - sentAtMillis) - (answeredAtMillis - heardAtMillis)
        if (rtt < 0 || answeredAtMillis < heardAtMillis) {
            strayMarks++
            return
        }
        val offset = ((heardAtMillis - sentAtMillis) + (answeredAtMillis - receivedAtMillis)).floorDiv(2L)
        take(rtt, offset, receivedAtMillis)
    }

    var strayMarks: Int = 0
        private set

    /** The RTT behind the current estimate, for eyes on a screen. Null before the first fix. */
    var lastRttMillis: Long? = null
        private set

    private fun finishRound(current: Round, nowMillis: Long) {
        round = null
        if (current.bestRttMillis == Long.MAX_VALUE) return // nothing came back at all
        take(current.bestRttMillis, current.bestOffsetMillis, nowMillis)
    }

    /** One sample into the window, and the window's minimum round trip as the estimate. */
    private fun take(rtt: Long, offset: Long, nowMillis: Long) {
        sampleCount++
        val slot = samples % SAMPLE_WINDOW
        rtts[slot] = rtt
        offsets[slot] = offset
        samples++
        val held = minOf(samples, SAMPLE_WINDOW)
        // The newest first, so a tie goes to the newer sample: the older one has drifted further.
        var best = slot
        for (back in 1 until held) {
            val i = (slot - back + SAMPLE_WINDOW) % SAMPLE_WINDOW
            if (rtts[i] < rtts[best]) best = i
        }
        adopt(offsets[best], rtts[best], nowMillis)
    }

    private fun adopt(target: Long, rtt: Long, nowMillis: Long) {
        lastRttMillis = rtt
        val current = fix
        if (current != null && current.targetOffsetMillis == target && current.rttMillis == rtt) return
        // The first fix is applied whole; every later one slews from wherever the applied offset
        // has got to, toward the new estimate.
        val base = current?.offsetAt(nowMillis) ?: target
        fix = ClockFix(
            baseOffsetMillis = base,
            targetOffsetMillis = target,
            slewFromMillis = nowMillis,
            rttMillis = rtt,
        )
    }

    /**
     * **A new connection: the burst in flight is abandoned, the next [dueProbes] bursts, and the
     * fix is kept** (C1).
     *
     * Marks for probes sent on the old socket will never come, so a round waiting for them would
     * only sit out its [ROUND_TIMEOUT_MILLIS]; and a phone that has just reconnected is exactly the
     * phone whose estimate most wants refreshing. The fix and the window stay: the house's clock did
     * not change because a socket did.
     */
    fun reconnected(nowMillis: Long) {
        round?.let { finishRound(it, nowMillis) }
        burstSent = false
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
