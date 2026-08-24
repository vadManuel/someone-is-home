package home.someoneshome.platform

import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * **iOS: `UIApplicationOpenSettingsURLString`, which is the only page the OS will let an app open.**
 *
 * It lands on *this app's* row rather than on the Local Network list, because iOS does not expose a
 * deep link to a particular grant and has not for any version this project targets. That is enough:
 * the app's own page is where its permissions are, the refusal above the control has already named
 * which one to look for (`CHECK LOCAL NETWORK PERMISSION`), and a host reading both is one scroll
 * away rather than lost in Settings.
 *
 * **A URL the OS refuses opens nothing and reports to the log** — rule 6 in the one place it is
 * about a host rather than a player: this is the lit room, the failure is a control that did not
 * work, and the host still has the sentence above it. The `canOpenURL` check is not decoration;
 * `NSURL` is nullable in the Kotlin binding and a forced unwrap here would take down the app on the
 * screen whose whole job is to explain a failure.
 */
actual fun deviceSystemSettings(): SystemSettings = UiKitSystemSettings()

private class UiKitSystemSettings : SystemSettings {

    override fun open() {
        val page = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        val app = UIApplication.sharedApplication
        if (page == null || !app.canOpenURL(page)) {
            NSLog("%@", "SETTINGS — THE OS REFUSED ITS OWN SETTINGS URL")
            return
        }
        app.openURL(page, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
