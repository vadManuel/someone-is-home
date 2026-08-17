import UIKit
import SpikeKit

/// The entire iOS shell. Everything measured lives in the Kotlin framework — if any of the
/// blackout path were written here, the spike would be measuring Swift and reporting it as an
/// answer about Compose Multiplatform.
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = Entry_iosKt.spikeViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
