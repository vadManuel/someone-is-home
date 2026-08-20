package home.someoneshome.platform.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReconnectBackoffTest {

    @Test
    fun doublesFromTheBaseAndStopsAtTheCap() {
        val backoff = ReconnectBackoff(baseMillis = 500, capMillis = 8_000)
        assertEquals(
            listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 8_000L, 8_000L),
            (1..7).map(backoff::delayMillis),
        )
    }

    @Test
    fun theCapHoldsForAbsurdAttemptCounts() {
        // A phone can retry for the whole 25 minutes; the delay must neither overflow nor grow.
        val backoff = ReconnectBackoff(baseMillis = 500, capMillis = 8_000)
        assertEquals(8_000, backoff.delayMillis(10_000))
    }

    @Test
    fun nonsenseConfigurationsAreRejected() {
        assertFailsWith<IllegalArgumentException> { ReconnectBackoff(baseMillis = 0) }
        assertFailsWith<IllegalArgumentException> { ReconnectBackoff(baseMillis = 500, capMillis = 100) }
        assertFailsWith<IllegalArgumentException> { ReconnectBackoff().delayMillis(0) }
    }
}
