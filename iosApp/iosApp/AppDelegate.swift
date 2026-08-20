import UIKit
import SomeoneIsHomeKit

/// The entire iOS shell.
///
/// A window and a root view controller, and nothing else. Everything the game does lives in the
/// Kotlin framework — anything written here is code no boundary check covers, no lint reads, and
/// no headless test can exercise, in a project whose bugs are silent leaks rather than crashes.
/// The stack-gate spike's shell holds the same line for the same reason.
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = Entry_iosKt.mainViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
