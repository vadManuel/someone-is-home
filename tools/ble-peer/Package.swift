// swift-tools-version:6.0
//
// The Mac's BLE peer for the one-phone radio check. A dev tool: it is not part of the app's build,
// and nothing in the Gradle or Xcode projects refers to it. See tools/one-phone/README.md.

import Foundation
import PackageDescription

// A command-line tool that touches CoreBluetooth needs a usage string, and a bare binary has no
// bundle to carry one, so the Info.plist is linked into the binary's __TEXT,__info_plist section.
let plist = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("Info.plist").path

let package = Package(
    name: "ble-peer",
    platforms: [.macOS(.v13)],
    targets: [
        // The seed and token derivation, pure, so the tests can pin it without a radio.
        .target(name: "ContactDerivation"),
        .executableTarget(
            name: "ble-peer",
            dependencies: ["ContactDerivation"],
            linkerSettings: [
                .unsafeFlags(["-Xlinker", "-sectcreate", "-Xlinker", "__TEXT", "-Xlinker", "__info_plist", "-Xlinker", plist]),
            ]
        ),
        .testTarget(name: "ContactDerivationTests", dependencies: ["ContactDerivation"]),
    ],
    swiftLanguageModes: [.v5]
)
