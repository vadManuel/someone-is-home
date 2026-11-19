package home.someoneshome.model

/**
 * **The Sync Pulse's grid, and the one question asked of a tap on it** (`gdd.md:355`, C2, D-298).
 *
 * The arithmetic the house grades with and a phone draws its pips with, in one place: `core` grades
 * a return with [inARow], and the phone keeps its pips with [hit], so a person whose phone shows four
 * pips has handed the house four taps the house will call four in a row, by the same function. The
 * phone stamps its taps on its own estimate of the house's clock and the house grades those stamps,
 * so the two readings of one tap are the same reading.
 *
 * Milliseconds of the house's monotonic clock throughout, on `Egress.pulseFrom`; pulse `n` lands at
 * `pulseFrom + n · SYNC_PULSE_INTERVAL_MILLIS`. Integer arithmetic only (rule 4).
 */
object SyncPulseGrid {

    /** When pulse [n] lands, in the house's monotonic milliseconds. */
    fun at(pulseFrom: Long, n: Long): Long = pulseFrom + n * Balance.SYNC_PULSE_INTERVAL_MILLIS

    /**
     * **The pulse nearest [tap]**, rounding to nearest for negative offsets too: `(offset +
     * interval/2) / interval` truncates toward zero, so a tap before the first pulse would round the
     * wrong way and an early tap would be graded against a pulse that never happened.
     */
    fun near(pulseFrom: Long, tap: Long): Long {
        val offset = tap - pulseFrom
        val interval = Balance.SYNC_PULSE_INTERVAL_MILLIS
        return if (offset >= 0) (offset + interval / 2) / interval else -((-offset + interval / 2) / interval)
    }

    /**
     * **The pulse [tap] hit, or null for a miss** (D-298): within [Balance.SYNC_PULSE_WINDOW_MILLIS]
     * of a pulse, either side, inclusive, and a pulse of the grid: pulse 0 or later. The lead before
     * pulse 0 is silent on every phone (`PulseBuzzer`), so there is nothing before it to tap to.
     */
    fun hit(pulseFrom: Long, tap: Long): Long? {
        val pulse = near(pulseFrom, tap)
        val distance = tap - at(pulseFrom, pulse)
        val within = distance <= Balance.SYNC_PULSE_WINDOW_MILLIS && distance >= -Balance.SYNC_PULSE_WINDOW_MILLIS
        return if (within && pulse >= 0) pulse else null
    }

    /**
     * **Four in a row** (the owner's O3, D-298): the first of the four pulses [taps] hit, or null.
     *
     * Exactly [Balance.SYNC_PULSE_BEATS] taps, in the order they landed, each a [hit], and each on the
     * pulse after the one before: pulses `n, n+1, n+2, n+3`. A tap off the beat, a pulse skipped, two
     * taps on one pulse, a tap out of order, three taps or five: null. *A miss starts the count again*,
     * so no run with a miss inside it is four in a row, whatever else it holds.
     *
     * The early returns are an internal grader's, not a client-visible path: what a phone is told is
     * built once in `core`'s `pulseReturned`, whatever this says (rule 1).
     */
    fun inARow(pulseFrom: Long, taps: List<Long>): Long? {
        if (taps.size != Balance.SYNC_PULSE_BEATS) return null
        val first = hit(pulseFrom, taps[0]) ?: return null
        for (k in 1 until taps.size) {
            if (hit(pulseFrom, taps[k]) != first + k) return null
        }
        return first
    }
}
