package home.someoneshome.app

import androidx.compose.ui.window.ComposeUIViewController
import home.someoneshome.ui.DeviceCanvas
import platform.UIKit.UIViewController

/**
 * The whole iOS shell, on the Kotlin side.
 *
 * Everything the game does lives in the framework. Anything written in Swift is something no
 * boundary check covers, no lint reads, and no headless test can exercise — so the Swift side is
 * a window and a root view controller, and stops there. The spike's shell holds the same line for
 * the same reason.
 *
 * ### There is no game behind this yet, and it does not pretend otherwise
 *
 * It draws screens from fixtures. No authority, no radio, no tick loop is wired in — those
 * arrive with the transport and the loop. **What this proves is exactly one thing: that the
 * ported screens lay out on a real panel**, which nothing before it could show. `ui`'s desktop
 * preview renders on a Mac, and a Mac is not a phone: the panel is a different aspect, the
 * density is different, and the pixel fonts land on different physical pixels.
 *
 * What sits inside the canvas is the build variant's root (story 0.10b): release shows the boot
 * screen and nothing else; playtest and debug add the screen picker and the permanent variant
 * marker, compiled in for them and out of release.
 *
 * ### What it can never prove
 *
 * Run in the Simulator this shows layout and nothing else. **No BLE, no torch, no camera, no
 * haptics — which is every input this game has.** Nothing touching those may be signed off from
 * here, and the day this shell grows a lamp it stops being verifiable anywhere but on plural
 * physical devices in a dark room.
 */
fun mainViewController(): UIViewController = ComposeUIViewController {
    DeviceCanvas {
        VariantRoot()
    }
}
