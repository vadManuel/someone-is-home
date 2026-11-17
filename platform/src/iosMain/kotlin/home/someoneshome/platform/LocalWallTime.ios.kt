package home.someoneshome.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.localtime_r
import platform.posix.time
import platform.posix.time_tVar
import platform.posix.tm

/**
 * `localtime_r` over `time()`: the wall clock in the zone the phone is set to, as its own status
 * bar would show it. `tm_wday` counts from Sunday and `tm_mon` from zero; both are turned into
 * [LocalWallTime]'s forms here, once.
 */
actual fun localWallTime(): LocalWallTime = memScoped {
    val now = alloc<time_tVar>()
    now.value = time(null)
    val parts = alloc<tm>()
    localtime_r(now.ptr, parts.ptr)
    LocalWallTime(
        hour = parts.tm_hour,
        minute = parts.tm_min,
        weekday = if (parts.tm_wday == 0) 7 else parts.tm_wday,
        day = parts.tm_mday,
        month = parts.tm_mon + 1,
    )
}
