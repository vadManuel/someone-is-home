package home.someoneshome.platform.transport

import home.someoneshome.model.Seat

/** D5's numbers, named once. The protocol is broadcast → ack → commit. */
const val ACK_TIMEOUT_MILLIS: Long = 2_000
const val COMMIT_LEAD_MILLIS: Long = 500

/**
 * The host's bookkeeping for one round of broadcast → ack → commit — pure, time passed in.
 *
 * D5: scheduled events commit at `last_ack + 500 ms`, and **a client that never acks must not
 * stall the caller** — at 2 s the host proceeds without the missing client, which flips on
 * reconnect. This class holds exactly that arithmetic and no I/O, so the property can be tested
 * in milliseconds and the websocket wiring stays thin.
 *
 * Time is monotonic milliseconds handed in by the caller ([home.someoneshome.platform.MonotonicClock]
 * at the wiring layer, plain numbers in tests). Nothing here reads a clock.
 *
 * Not thread-safe: D4 confines it to the transport's own dispatcher.
 */
class AckLedger {

    sealed interface Standing {
        /** Acks outstanding, deadline not reached. */
        data class Waiting(val missing: Set<Seat>) : Standing

        /**
         * Commit. [missing] is empty when everyone acked in time; otherwise it names the seats
         * the host proceeded without — the reconnect path owes each of them the flip.
         */
        data class Ready(val commitAtMillis: Long, val missing: Set<Seat>) : Standing
    }

    private class Proposal(
        val expected: Set<Seat>,
        val proposedAtMillis: Long,
    ) {
        val acked = LinkedHashSet<Seat>()
        val late = LinkedHashSet<Seat>()
        var lastAckMillis: Long = proposedAtMillis
        var ready: Standing.Ready? = null
    }

    private val proposals = LinkedHashMap<Long, Proposal>()

    fun propose(proposal: Long, expected: Set<Seat>, nowMillis: Long) {
        require(proposal !in proposals) { "proposal $proposal already exists" }
        proposals[proposal] = Proposal(expected, nowMillis)
    }

    /**
     * Records an ack. An ack from a seat that was never expected is recorded as late rather than
     * ignored — a seat acking a proposal not addressed to it is a fact worth surfacing, and
     * silently dropping it is how instrument number seven gets written.
     */
    fun ack(proposal: Long, seat: Seat, nowMillis: Long) {
        val p = requireNotNull(proposals[proposal]) { "ack for unknown proposal $proposal" }
        if (p.ready != null || seat !in p.expected) {
            p.late.add(seat)
            return
        }
        p.acked.add(seat)
        p.lastAckMillis = nowMillis
    }

    /**
     * Where the proposal stands at [nowMillis].
     *
     * The first poll that finds it committable fixes [Standing.Ready.commitAtMillis] permanently —
     * everyone acked: `last_ack + 500`; deadline passed with seats missing: `now + 500`, counted
     * from when the host noticed, because the lead time exists to get the commit scheduled *ahead*
     * of every phone and a stale deadline could already be in the past. Polling again never moves
     * a commit that has been given out: a commit time that drifts after phones heard it is two
     * houses disagreeing about when one thing happens.
     */
    fun standing(proposal: Long, nowMillis: Long): Standing {
        val p = requireNotNull(proposals[proposal]) { "standing of unknown proposal $proposal" }
        p.ready?.let { return it }
        val missing = p.expected - p.acked
        val fixed = when {
            missing.isEmpty() -> Standing.Ready(p.lastAckMillis + COMMIT_LEAD_MILLIS, emptySet())
            nowMillis >= p.proposedAtMillis + ACK_TIMEOUT_MILLIS ->
                Standing.Ready(nowMillis + COMMIT_LEAD_MILLIS, missing)
            else -> return Standing.Waiting(missing)
        }
        p.ready = fixed
        return fixed
    }

    /** Acks that arrived after the commit was fixed, or from unexpected seats. Flip-on-reconnect reads this. */
    fun lateAcks(proposal: Long): Set<Seat> =
        requireNotNull(proposals[proposal]) { "late acks of unknown proposal $proposal" }.late
}
