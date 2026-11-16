import ContactDerivation
import XCTest

/// The same values `core/src/commonTest/.../OnePhoneVectorsTest.kt` pins in Kotlin, and that
/// Python's `hmac` gives. If these fail, the Mac advertises tokens the house cannot resolve.
final class VectorTests: XCTestCase {

    private let launcherSeed: Int64 = 20261116

    private func seed(_ round: Int64, _ seat: Int32) -> String {
        ContactDerivation.hex(ContactDerivation.seatSeed(roundSeed: round, seat: seat))
    }

    private func token(_ round: Int64, _ seat: Int32, _ window: Int64) -> String {
        ContactDerivation.hex(ContactDerivation.token(seed: ContactDerivation.seatSeed(roundSeed: round, seat: seat), window: window))
    }

    /// `ContactSeedTest`'s two older vectors, which predate this tool.
    func testTheAppsOwnSeedVectors() {
        XCTAssertEqual(seed(20260823, 0), "7609365cd8eef1caf0cfcd6e645f1dd9")
        XCTAssertEqual(seed(20260823, 1), "b8369a0a447dda23d6d1453fa114c237")
    }

    func testTheLaunchersSeatSeeds() {
        XCTAssertEqual(seed(launcherSeed, 0), "7995882527fa09a3ce75db487404b23a")
        XCTAssertEqual(seed(launcherSeed, 1), "130f00039e6f9efdc337d82ba3bf7b59")
        XCTAssertEqual(seed(launcherSeed, 2), "fc274ca75d79c8f78e2e496afa36a637")
    }

    func testTheLaunchersTokens() {
        XCTAssertEqual(token(launcherSeed, 1, 0), "8c714648964924c65201ff4a48e23f37")
        XCTAssertEqual(token(launcherSeed, 1, 1), "bb3b6d5dcbbc59c87787b1959b3a982a")
        XCTAssertEqual(token(launcherSeed, 1, 117_888_000), "16dd923b17fd4239b701611561bfb7a2")
        XCTAssertEqual(token(launcherSeed, 0, 117_888_000), "0aa2b4e3ff3c4b96c60062568d758520")
    }

    func testANegativeRoundSeed() {
        XCTAssertEqual(seed(-20261116, 1), "d9c79a2954673537a3a6a6bb392f3010")
        XCTAssertEqual(token(-20261116, 1, 117_888_000), "d929b7cf1bc04ef329dd518e9970508d")
    }

    func testTheWindowIsAFloorDivision() {
        XCTAssertEqual(ContactDerivation.window(containingMillis: 0), 0)
        XCTAssertEqual(ContactDerivation.window(containingMillis: 14_999), 0)
        XCTAssertEqual(ContactDerivation.window(containingMillis: 15_000), 1)
        XCTAssertEqual(ContactDerivation.window(containingMillis: -1), -1)
        XCTAssertEqual(ContactDerivation.window(containingMillis: 1_768_320_000_000), 117_888_000)
    }
}
