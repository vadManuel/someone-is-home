package home.someoneshome.platform

import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * **iOS: `UIApplication.idleTimerDisabled`** (T4, D-221).
 *
 * The property stops Auto-Lock and the dim before it, and nothing more: it does not touch
 * `UIScreen.brightness`, so no level the lamp set is changed. UIKit wants it on the main thread.
 * The round applies its pushes there already; a call from anywhere else is moved there rather than
 * made from the wrong thread, and a read off the main thread reports what was last written.
 */
actual fun deviceIdleTimer(): IdleTimer = UiKitIdleTimer

private object UiKitIdleTimer : IdleTimer {
    private var written: Boolean? = null

    override var disabled: Boolean
        get() = if (NSThread.isMainThread) UIApplication.sharedApplication.idleTimerDisabled else written ?: false
        set(value) {
            written = value
            if (NSThread.isMainThread) {
                UIApplication.sharedApplication.idleTimerDisabled = value
            } else {
                dispatch_async(dispatch_get_main_queue()) { UIApplication.sharedApplication.idleTimerDisabled = value }
            }
        }
}
