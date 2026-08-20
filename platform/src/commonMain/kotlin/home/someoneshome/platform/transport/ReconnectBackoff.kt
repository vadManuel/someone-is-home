package home.someoneshome.platform.transport

/**
 * G3's bounded exponential backoff, as arithmetic.
 *
 * A phone that lost its websocket retries at growing intervals so eight reconnecting phones do
 * not hammer a host that is already struggling — and *bounded*, because the player standing in a
 * dark house is waiting on every one of these milliseconds.
 *
 * **The numbers are placeholders, F-005-style: the shape is decided, the values are not.** Base,
 * factor and cap were chosen to be sane, not measured; the two-phone verification evening is
 * where they meet reality. Nothing else in the codebase may copy them out of here.
 */
class ReconnectBackoff(
    private val baseMillis: Long = 500,
    private val capMillis: Long = 8_000,
) {
    init {
        require(baseMillis > 0 && capMillis >= baseMillis) { "backoff must grow from a positive base" }
    }

    /** Delay before retry [attempt] (1-based): base × 2^(attempt−1), capped. */
    fun delayMillis(attempt: Int): Long {
        require(attempt >= 1) { "attempts are 1-based" }
        var delay = baseMillis
        repeat(attempt - 1) {
            delay = (delay * 2).coerceAtMost(capMillis)
            if (delay == capMillis) return capMillis
        }
        return delay
    }
}
