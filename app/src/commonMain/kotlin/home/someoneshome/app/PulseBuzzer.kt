package home.someoneshome.app

import home.someoneshome.model.Balance
import home.someoneshome.model.Haptic
import home.someoneshome.platform.Haptics
import kotlinx.coroutines.delay

/**
 * **The Sync Pulse, felt: one short buzz on every pulse of the house's grid, on a phone at a node**
 * (D-295; GDD:371 *both phones pulse haptically in unison off a server-scheduled timestamp*).
 *
 * The house decides the grid (`EgressOpened.pulseFrom`, its monotonic milliseconds, the same number
 * on every phone, D-265); this phone only plays it, at the moment its own measured offset puts each
 * pulse ([houseNow], refreshed every second on the uploads). Two phones whose clocks differ and
 * drift still buzz together, because each reads the house's clock through its own estimate rather
 * than its own crystal. The offset is read again before every pulse, so a slew in between is
 * followed.
 *
 * **Only while the tap control is live** ([pulseFrom] answers null otherwise): a phone whose person
 * scanned a node of the running, unpaused Egress, of either role. Haptics are private to the hand,
 * and which hands buzz is decided by where their people stood, never by who they are. The buzz is
 * [Haptic.Short], the one short buzz the app has (D-140): no length of its own.
 *
 * One coroutine and no allocation per pulse but what the motor's own call makes (rule 7).
 */
class PulseBuzzer(
    private val haptics: Haptics,
    /** Pulse 0 of the running Egress while this phone's tap control is live; null otherwise. */
    private val pulseFrom: () -> Long?,
    /** Now on the house's clock, as this phone measures it; null before any estimate. */
    private val houseNow: () -> Long?,
) {

    /** How many pulses this phone has buzzed. For tests and the log. */
    var buzzed: Int = 0
        private set

    /** Forever, on the caller's scope: wait for the next pulse, and buzz it if the control is still live. */
    suspend fun run() {
        var last = Long.MIN_VALUE
        var lastFrom: Long? = null
        while (true) {
            val from = pulseFrom()
            val now = houseNow()
            if (from == null || now == null) {
                delay(LOOK_MILLIS)
                continue
            }
            // A new Egress is a new grid: what was buzzed on the last one says nothing about this.
            if (from != lastFrom) {
                last = Long.MIN_VALUE
                lastFrom = from
            }
            val n = nextPulse(from, now, after = last)
            val wait = pulseAt(from, n) - now
            // Look again rather than sleep long: the control can go, and the offset can slew.
            if (wait > LOOK_MILLIS) {
                delay(LOOK_MILLIS)
                continue
            }
            if (wait > 0) delay(wait)
            if (pulseFrom() == from) {
                haptics.buzz(Haptic.Short)
                buzzed++
            }
            last = n
        }
    }

    companion object {
        /** How long this waits before looking again at whether the control is live and where the next pulse is. */
        const val LOOK_MILLIS: Long = 100L

        /** When pulse [n] lands, on the house's clock. `core`'s own `pulseAt`, the same arithmetic. */
        fun pulseAt(pulseFrom: Long, n: Long): Long = pulseFrom + n * Balance.SYNC_PULSE_INTERVAL_MILLIS

        /**
         * **The first pulse at or after [now]** on the house's clock, and later than pulse [after]
         * (the last one buzzed), so a pulse is never buzzed twice. Never before pulse 0: the lead
         * (`SYNC_PULSE_LEAD_MILLIS`) is silent.
         */
        fun nextPulse(pulseFrom: Long, now: Long, after: Long = Long.MIN_VALUE): Long {
            val interval = Balance.SYNC_PULSE_INTERVAL_MILLIS
            val due = if (now <= pulseFrom) 0L else (now - pulseFrom + interval - 1) / interval
            return if (after == Long.MIN_VALUE) due else maxOf(due, after + 1)
        }
    }
}
