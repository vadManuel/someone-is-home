import ContactDerivation
import XCTest

/// The same values `core/src/commonTest/.../OnePhoneVectorsTest.kt` pins in Kotlin, and that
/// Python's `hmac` gives. If these fail, the Mac advertises tokens the house cannot resolve.
final class VectorTests: XCTestCase {

    /// The launcher's seed (`round.sh`). 20261150 deals seat 0 the Insider role at three and at
    /// four seats; 20261116, the first one, Revoked the phone itself at four.
    private let launcherSeed: Int64 = 20261150

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
        XCTAssertEqual(seed(launcherSeed, 0), "f79b6f331bf6d28bbdbeddd74aaa18a2")
        XCTAssertEqual(seed(launcherSeed, 1), "aed69e6fc764d486ab051d7fdc760ad4")
        XCTAssertEqual(seed(launcherSeed, 2), "4f06e585f8ab672c59d4a161b987199f")
        XCTAssertEqual(seed(launcherSeed, 3), "8f12a2ae4efbc0960d06c55f87d91269")
    }

    func testTheLaunchersTokens() {
        XCTAssertEqual(token(launcherSeed, 1, 0), "f7b01e60a5f1b47206a3c79d535a4b69")
        XCTAssertEqual(token(launcherSeed, 1, 1), "33cec58d2d012e756c02b98c965fab5f")
        XCTAssertEqual(token(launcherSeed, 1, 117_888_000), "615f52c14cb48655c67bbefca08afd6d")
        XCTAssertEqual(token(launcherSeed, 0, 117_888_000), "c8246bc64c3355d2ffce2b55a82f6380")
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
