package home.someoneshome.platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * **iOS: the two UIApplication notifications, on the main queue** (H2, D-225).
 *
 * A lock with the app in front posts `DidEnterBackground`; an unlock posts `WillEnterForeground`.
 * Pulling down Control Centre or a notification only resigns active, which suspends nothing and is
 * deliberately not watched. Callbacks arrive on the main queue, which is where the lobby's house
 * and its Bonjour advertiser live.
 */
actual fun deviceForeground(): Foreground = UiKitForeground

private object UiKitForeground : Foreground {
    override fun watch(onBackground: () -> Unit, onForeground: () -> Unit): () -> Unit {
        val centre = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        val away = centre.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ ->
            onBackground()
        }
        val back = centre.addObserverForName(UIApplicationWillEnterForegroundNotification, null, queue) { _ ->
            onForeground()
        }
        return {
            centre.removeObserver(away)
            centre.removeObserver(back)
        }
    }
}
