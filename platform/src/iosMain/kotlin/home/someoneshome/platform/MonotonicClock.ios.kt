package home.someoneshome.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime
import platform.posix.timespec

/**
 * `CLOCK_MONOTONIC`, which does not tick while the device is asleep.
 *
 * That is the correct choice here and it is worth naming, because the alternative sounds better.
 * `CLOCK_MONOTONIC_RAW` and the uptime clocks keep counting through suspension, so a phone that
 * spent ten minutes in a pocket would return a ten-minute backlog to the tick source. The
 * simulation did not happen during those ten minutes and there is nothing to catch up on.
 *
 * `FixedTimestep` bounds that case anyway rather than trusting this — a locked phone, an incoming
 * call and a backgrounded app are all normal in a 25-minute round, and the backlog bound exists
 * because at least one of them will happen every session.
 */
actual fun monotonicNanos(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_MONOTONIC.toUInt(), ts.ptr)
    ts.tv_sec * 1_000_000_000L + ts.tv_nsec
}
