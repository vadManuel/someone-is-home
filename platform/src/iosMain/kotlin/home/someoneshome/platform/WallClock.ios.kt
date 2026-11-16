package home.someoneshome.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.timespec

/** `CLOCK_REALTIME`: the network-kept wall clock, which every phone in the house shares (D-217). */
actual fun wallClockMillis(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME.toUInt(), ts.ptr)
    ts.tv_sec * 1_000L + ts.tv_nsec / 1_000_000L
}
